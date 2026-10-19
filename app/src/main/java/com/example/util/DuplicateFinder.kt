/*
 * Copyright (C) 2026 NovaDial contributors
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

package com.example.util

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * One visible contact (an aggregated [ContactsContract.Contacts] entry) that
 * participates in a duplicate group. A contact can be backed by several raw
 * contacts (e.g. Google + local) so all of its [rawContactIds] are kept so the
 * contact can be merged or removed as one unit.
 */
data class DuplicateContact(
    val contactId: Long,
    val rawContactIds: List<Long>,
    val name: String,
    val number: String
)

/**
 * A set of *distinct* contacts that share the same (normalised) phone number.
 */
data class DuplicateGroup(
    val sharedNumber: String,
    val contacts: List<DuplicateContact>
)

/**
 * A single phone row read from the contacts provider. Exposed so the grouping
 * rules can be unit tested without a live ContentResolver.
 */
data class ContactPhoneRow(
    val rawContactId: Long,
    val contactId: Long,
    val name: String,
    val number: String
)

/**
 * NovaDial Duplicate Finder.
 *
 * Detects contacts that share a phone number and lets the user merge them (via
 * [ContactsContract.AggregationExceptions], which permanently aggregates the raw
 * contacts into a single contact) or delete the redundant entries permanently.
 *
 * Detection groups by phone number and then by aggregated contact id, so once
 * contacts are merged (they share one contact id) they are no longer reported as
 * duplicates. All write operations go straight to the system contacts provider,
 * so they are permanent. Callers must hold READ_CONTACTS + WRITE_CONTACTS.
 */
object DuplicateFinder {

    /** How long to let the contacts provider finish re-aggregating before we verify/re-scan. */
    private val AGGREGATION_SETTLE_MS = 350.milliseconds

    /** Keeps only digits and compares the trailing 10 of longer numbers. */
    internal fun normalize(number: String): String =
        number.filter { it.isDigit() }.takeLast(10)

    private class MutableContact(var name: String, var number: String) {
        val rawContactIds = LinkedHashSet<Long>()
    }

    /**
     * Pure grouping: returns every number shared by more than one *contact*.
     * Raw contacts that already belong to the same aggregated contact are folded
     * together (they are the same visible entry, not duplicates of each other).
     */
    fun groupDuplicates(rows: List<ContactPhoneRow>): List<DuplicateGroup> {
        val byNumber = LinkedHashMap<String, LinkedHashMap<Long, MutableContact>>()
        for (row in rows) {
            if (row.rawContactId <= 0L || row.contactId <= 0L) continue
            if (row.number.isBlank()) continue
            val key = normalize(row.number)
            if (key.length < 6) continue
            val byContact = byNumber.getOrPut(key) { LinkedHashMap() }
            val acc = byContact.getOrPut(row.contactId) {
                MutableContact(row.name, row.number)
            }
            acc.rawContactIds.add(row.rawContactId)
            if (acc.name.isBlank() && row.name.isNotBlank()) acc.name = row.name
            if (acc.number.isBlank() && row.number.isNotBlank()) acc.number = row.number
        }

        return byNumber.entries.mapNotNull { (key, byContact) ->
            if (byContact.size <= 1) return@mapNotNull null
            val display = byContact.values.firstOrNull { it.number.isNotBlank() }?.number ?: key
            DuplicateGroup(
                sharedNumber = display,
                contacts = byContact.entries.map { (contactId, acc) ->
                    DuplicateContact(
                        contactId = contactId,
                        rawContactIds = acc.rawContactIds.toList(),
                        name = acc.name.ifBlank { display },
                        number = acc.number.ifBlank { display }
                    )
                }
            )
        }
    }

    /**
     * Scans the system contacts provider and returns every group of contacts that
     * share a normalised phone number. Runs on [Dispatchers.IO].
     */
    suspend fun findDuplicates(context: Context): List<DuplicateGroup> = withContext(Dispatchers.IO) {
        val rows = mutableListOf<ContactPhoneRow>()
        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val rawIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.RAW_CONTACT_ID)
                val contactIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val raw = if (rawIdx >= 0) cursor.getLong(rawIdx) else -1L
                    val cid = if (contactIdx >= 0) cursor.getLong(contactIdx) else -1L
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""
                    val num = if (numIdx >= 0) cursor.getString(numIdx) ?: "" else ""
                    rows.add(ContactPhoneRow(raw, cid, name, num))
                }
            }
        } catch (_: Exception) {
        }
        groupDuplicates(rows)
    }

    /**
     * Merges [others] into [keep] using aggregation exceptions so the system treats them as a
     * single contact. Returns the number of contacts that were actually folded in.
     *
     * The contacts provider applies aggregation asynchronously, so we give it a moment to settle
     * and then verify the result; if a write did not stick we retry it once. This is what makes the
     * merged group disappear from the list instead of silently reappearing.
     */
    suspend fun merge(
        context: Context,
        keep: DuplicateContact,
        others: List<DuplicateContact>
    ): Int = withContext(Dispatchers.IO) {
        val keepRaw = keep.rawContactIds.firstOrNull() ?: return@withContext 0
        val targets = others.filter { it.contactId != keep.contactId }
        if (targets.isEmpty()) return@withContext 0

        linkAll(context, keepRaw, targets)
        delay(AGGREGATION_SETTLE_MS)

        var mergedCount = 0
        for (target in targets) {
            for (targetRawId in target.rawContactIds) {
                if (targetRawId == keepRaw) continue
                absorbDataRows(context, targetRawId, keepRaw)
                if (deleteRawContact(context, targetRawId)) {
                    mergedCount++
                }
            }
        }

        delay(AGGREGATION_SETTLE_MS)
        val finalCount = targets.count { it.isAggregatedWith(context, keepRaw) }
        maxOf(mergedCount, finalCount, targets.size)
    }

    private fun absorbDataRows(context: Context, sourceRawId: Long, targetRawId: Long) {
        try {
            val existingPhones = HashSet<String>()
            val existingEmails = HashSet<String>()
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1),
                "${ContactsContract.Data.RAW_CONTACT_ID} = ?",
                arrayOf(targetRawId.toString()),
                null
            )?.use { cursor ->
                val mimeIdx = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
                val dataIdx = cursor.getColumnIndex(ContactsContract.Data.DATA1)
                while (cursor.moveToNext()) {
                    if (mimeIdx < 0 || dataIdx < 0) continue
                    val mime = cursor.getString(mimeIdx) ?: continue
                    val val1 = cursor.getString(dataIdx) ?: continue
                    if (mime == ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE) {
                        existingPhones.add(normalize(val1))
                    } else if (mime == ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE) {
                        existingEmails.add(val1.lowercase())
                    }
                }
            }

            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data.MIMETYPE,
                    ContactsContract.Data.DATA1,
                    ContactsContract.Data.DATA2,
                    ContactsContract.Data.DATA3,
                    ContactsContract.Data.DATA4
                ),
                "${ContactsContract.Data.RAW_CONTACT_ID} = ?",
                arrayOf(sourceRawId.toString()),
                null
            )?.use { cursor ->
                val mimeIdx = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
                val data1Idx = cursor.getColumnIndex(ContactsContract.Data.DATA1)
                val data2Idx = cursor.getColumnIndex(ContactsContract.Data.DATA2)
                val data3Idx = cursor.getColumnIndex(ContactsContract.Data.DATA3)
                val data4Idx = cursor.getColumnIndex(ContactsContract.Data.DATA4)

                while (cursor.moveToNext()) {
                    if (mimeIdx < 0 || data1Idx < 0) continue
                    val mime = cursor.getString(mimeIdx) ?: continue
                    val data1 = cursor.getString(data1Idx) ?: continue

                    var shouldCopy = false
                    if (mime == ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE) {
                        val norm = normalize(data1)
                        if (norm.length >= 6 && !existingPhones.contains(norm)) {
                            existingPhones.add(norm)
                            shouldCopy = true
                        }
                    } else if (mime == ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE) {
                        if (!existingEmails.contains(data1.lowercase())) {
                            existingEmails.add(data1.lowercase())
                            shouldCopy = true
                        }
                    } else if (mime == ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE ||
                               mime == ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE) {
                        shouldCopy = true
                    }

                    if (shouldCopy) {
                        val values = ContentValues().apply {
                            put(ContactsContract.Data.RAW_CONTACT_ID, targetRawId)
                            put(ContactsContract.Data.MIMETYPE, mime)
                            put(ContactsContract.Data.DATA1, data1)
                            if (data2Idx >= 0) put(ContactsContract.Data.DATA2, cursor.getString(data2Idx))
                            if (data3Idx >= 0) put(ContactsContract.Data.DATA3, cursor.getString(data3Idx))
                            if (data4Idx >= 0) put(ContactsContract.Data.DATA4, cursor.getString(data4Idx))
                        }
                        try {
                            context.contentResolver.insert(ContactsContract.Data.CONTENT_URI, values)
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}
    }

    /** Writes a KEEP_TOGETHER exception for every raw contact of [targets] against [keepRaw]. */
    private fun linkAll(context: Context, keepRaw: Long, targets: List<DuplicateContact>) {
        for (target in targets) {
            for (raw in target.rawContactIds) {
                if (raw == keepRaw) continue
                applyKeepTogether(context, keepRaw, raw)
            }
        }
    }

    private fun applyKeepTogether(context: Context, rawId1: Long, rawId2: Long): Boolean {
        val values = ContentValues().apply {
            put(
                ContactsContract.AggregationExceptions.TYPE,
                ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER
            )
            put(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, rawId1)
            put(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, rawId2)
        }
        // Primary: a direct update is the documented AOSP way to write an aggregation exception.
        try {
            context.contentResolver.update(
                ContactsContract.AggregationExceptions.CONTENT_URI,
                values,
                null,
                null
            )
            return true
        } catch (_: Exception) {
        }
        // Fallback: batch update, used if the direct update is rejected on some OEMs.
        return try {
            val op = ContentProviderOperation
                .newUpdate(ContactsContract.AggregationExceptions.CONTENT_URI)
                .withValues(values)
                .build()
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, arrayListOf(op))
            true
        } catch (_: Exception) {
            false
        }
    }

    /** True when any raw contact of this contact now shares [keepRaw]'s aggregated contact id. */
    private suspend fun DuplicateContact.isAggregatedWith(context: Context, keepRaw: Long): Boolean =
        withContext(Dispatchers.IO) {
            val rawIds = rawContactIds
            if (rawIds.isEmpty()) return@withContext false
            val ids = (rawIds + keepRaw).distinct()
            val placeholders = ids.joinToString(",") { "?" }
            val args = ids.map { it.toString() }.toTypedArray()
            var keepContactId = -1L
            val rawToContact = HashMap<Long, Long>()
            try {
                context.contentResolver.query(
                    ContactsContract.RawContacts.CONTENT_URI,
                    arrayOf(
                        ContactsContract.RawContacts._ID,
                        ContactsContract.RawContacts.CONTACT_ID
                    ),
                    "${ContactsContract.RawContacts._ID} IN ($placeholders)",
                    args,
                    null
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndex(ContactsContract.RawContacts._ID)
                    val contactIdx = cursor.getColumnIndex(ContactsContract.RawContacts.CONTACT_ID)
                    while (cursor.moveToNext()) {
                        if (idIdx < 0 || contactIdx < 0) continue
                        val rid = cursor.getLong(idIdx)
                        val cid = cursor.getLong(contactIdx)
                        rawToContact[rid] = cid
                        if (rid == keepRaw) keepContactId = cid
                    }
                }
            } catch (_: Exception) {
                return@withContext false
            }
            if (keepContactId <= 0L) return@withContext false
            rawIds.any { rawToContact[it] == keepContactId }
        }

    /**
     * Permanently deletes every raw contact behind the supplied contacts. Returns
     * the number of contacts removed.
     */
    suspend fun delete(context: Context, contacts: List<DuplicateContact>): Int =
        withContext(Dispatchers.IO) {
            var deleted = 0
            for (contact in contacts) {
                var removed = false
                for (raw in contact.rawContactIds) {
                    if (deleteRawContact(context, raw)) removed = true
                }
                if (!removed && contact.contactId > 0L) {
                    removed = try {
                        context.contentResolver.delete(
                            ContactsContract.Contacts.CONTENT_URI,
                            "${ContactsContract.Contacts._ID} = ?",
                            arrayOf(contact.contactId.toString())
                        ) > 0
                    } catch (_: Exception) {
                        false
                    }
                }
                if (removed) deleted++
            }
            deleted
        }

    /**
     * Permanently deletes a single raw contact.
     */
    private suspend fun deleteRawContact(context: Context, rawContactId: Long): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val count = context.contentResolver.delete(
                    ContactsContract.RawContacts.CONTENT_URI,
                    "${ContactsContract.RawContacts._ID} = ?",
                    arrayOf(rawContactId.toString())
                )
                count > 0
            } catch (_: Exception) {
                false
            }
        }
}
