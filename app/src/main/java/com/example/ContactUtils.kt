/*
 * Copyright (C) 2026 MovStore
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import com.example.data.AppDatabase
import com.example.model.AppSetting
import com.example.model.Contact
import com.example.util.PhoneNumberNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Result of resolving a number against the currently saved contacts. */
sealed interface ContactLookup {
    /** Exactly one saved contact owns this number. */
    data class Unique(val contact: Contact) : ContactLookup

    /** More than one saved contact owns the same number: never guess between them. */
    data object Ambiguous : ContactLookup

    /** No saved contact owns this number. */
    data object NotSaved : ContactLookup
}

/**
 * In-memory index of the contacts that are currently saved on the device.
 *
 * Identity is based on the stable contact id (or raw contact id), never on a display name or on
 * a shared phone number. Two numbers that belong to the same contact are one identity; the same
 * number attached to two different contacts is ambiguous and resolves to nothing.
 */
object ContactCache {
    private val numberIndex = ConcurrentHashMap<String, ConcurrentHashMap<String, Contact>>()
    private val cnapMap = ConcurrentHashMap<String, String>()

    @Volatile
    private var region: String = PhoneNumberNormalizer.defaultRegion

    fun init(
        contacts: List<Contact>,
        settings: List<AppSetting>,
        regionIso: String? = null
    ) {
        if (!regionIso.isNullOrBlank()) {
            PhoneNumberNormalizer.setRegion(regionIso)
        } else {
            // Callers without a context (tests, early init) must not inherit a region that a
            // previous operation happened to set. Production always passes the SIM region.
            PhoneNumberNormalizer.resetToLocale()
        }
        region = PhoneNumberNormalizer.defaultRegion

        val nextIndex = HashMap<String, MutableMap<String, Contact>>()
        for (contact in contacts) {
            val identity = identityOf(contact)
            for (labeledNum in contact.getAllNumbers()) {
                val key = PhoneNumberNormalizer.key(labeledNum.number, region)
                if (key.isEmpty()) continue
                nextIndex.getOrPut(key) { LinkedHashMap() }[identity] = contact
            }
        }

        numberIndex.clear()
        for ((key, byIdentity) in nextIndex) {
            numberIndex[key] = ConcurrentHashMap(byIdentity)
        }
        initCnapFromSettings(settings)
    }

    fun initCnapFromSettings(settings: List<AppSetting>) {
        cnapMap.clear()
        val prefix = "cnap_"

        for (setting in settings) {
            if (setting.key.startsWith(prefix)) {
                val key = PhoneNumberNormalizer.key(setting.key.substring(prefix.length), region)
                if (key.isNotEmpty() && setting.value.isNotBlank()) {
                    cnapMap[key] = setting.value
                }
            }
        }
    }

    fun putCnapName(number: String, cnapName: String) {
        val key = PhoneNumberNormalizer.key(number, region)
        if (key.isEmpty() || cnapName.isBlank()) return
        cnapMap[key] = cnapName
    }

    /** Full resolution, including the ambiguous case. */
    fun lookup(number: String): ContactLookup {
        val key = PhoneNumberNormalizer.key(number, region)
        if (key.isEmpty()) return ContactLookup.NotSaved

        val owners = numberIndex[key] ?: return ContactLookup.NotSaved
        if (owners.isEmpty()) return ContactLookup.NotSaved
        if (owners.size > 1) return ContactLookup.Ambiguous
        return ContactLookup.Unique(owners.values.first())
    }

    /** Current saved contact for this number, or null when unknown/ambiguous. */
    fun getContact(number: String): Contact? = (lookup(number) as? ContactLookup.Unique)?.contact

    fun getCnapName(number: String): String? {
        val key = PhoneNumberNormalizer.key(number, region)
        if (key.isEmpty()) return null
        return cnapMap[key]
    }

    internal fun identityOf(contact: Contact): String = when {
        contact.contactId > 0L -> "c:${contact.contactId}"
        contact.rawContactId > 0L -> "r:${contact.rawContactId}"
        contact.id > 0L -> "i:${contact.id}"
        else -> "n:${contact.name.trim().lowercase(Locale.ROOT)}|${normKey(contact.number)}"
    }

    private fun normKey(number: String): String = PhoneNumberNormalizer.key(number, region)

    /** Region used by this cache; exposed for tests and provider lookups. */
    fun region(): String = region
}

/**
 * Resolves a name for [number] from the currently saved contacts.
 *
 * Returns a name only when the whole lookup proves one unique contact identity. When the cache is
 * empty or ambiguous the Contacts Provider is consulted by contact id; a provider answer with
 * more than one contact id is treated as ambiguous. Anything else returns null so callers can
 * display the number instead of a wrong or stale name.
 */
fun getContactNameFromNumber(context: Context, number: String): String? {
    if (number.isBlank()) return null

    when (val cached = ContactCache.lookup(number)) {
        is ContactLookup.Unique -> return cached.contact.name.takeIf { it.isNotBlank() }
        // The provider may have newer data than the last sync; fall through and verify there.
        ContactLookup.Ambiguous, ContactLookup.NotSaved -> Unit
    }

    return queryUniqueProviderName(context, number)
}

private fun queryUniqueProviderName(context: Context, number: String): String? {
    val safeContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try { context.createAttributionContext("default") } catch (_: Exception) { context }
    } else {
        context
    }
    val region = PhoneNumberNormalizer.regionFor(safeContext)
    val variants = PhoneNumberNormalizer.lookupVariants(number, region)
    if (variants.isEmpty()) return null

    val namesByContactId = LinkedHashMap<Long, String>()
    for (variant in variants) {
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(variant)
            )
            safeContext.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.PhoneLookup.CONTACT_ID,
                    ContactsContract.PhoneLookup.DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor -> collectUniqueMatches(cursor, namesByContactId) }
                ?: continue
        } catch (_: SecurityException) {
            // Permission denied: never fall back to a cached or carrier-provided name.
            return null
        } catch (_: Exception) {
            // Try the next representation; a malformed variant must not crash the dialer.
        }
    }

    if (namesByContactId.size != 1) return null
    return namesByContactId.values.first().takeIf { it.isNotBlank() }
}

private fun collectUniqueMatches(
    cursor: android.database.Cursor,
    into: MutableMap<Long, String>
) {
    val idIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.CONTACT_ID)
    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
    // Without a stable contact id uniqueness cannot be proven, so the answer is unusable.
    if (idIndex < 0 || nameIndex < 0) return

    while (cursor.moveToNext()) {
        val contactId = cursor.getLong(idIndex)
        val name = cursor.getString(nameIndex)?.trim().orEmpty()
        if (contactId > 0L && name.isNotEmpty()) {
            into.putIfAbsent(contactId, name)
        }
    }
}

suspend fun getSavedCnapName(context: Context, number: String): String? {
    if (number.isBlank()) return null
    ContactCache.getCnapName(number)?.let { return it }

    return withContext(Dispatchers.IO) {
        try {
            val clean = number.filter { it.isDigit() }
            val normalized = PhoneNumberNormalizer.key(number)
            val db = AppDatabase.getDatabase(context)
            val direct = db.dialerDao().getSetting("cnap_$clean")
                ?: normalized.takeIf { it != clean }?.let { db.dialerDao().getSetting("cnap_$it") }
            if (!direct.isNullOrBlank()) {
                ContactCache.putCnapName(number, direct)
                return@withContext direct
            }
            val settings = db.dialerDao().getAllSettingsList()
            ContactCache.initCnapFromSettings(settings)
            ContactCache.getCnapName(number)
        } catch (_: Exception) {
            null
        }
    }
}
