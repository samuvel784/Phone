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

import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.room.withTransaction
import com.example.data.AppDatabase
import com.example.model.*
import com.example.ui.theme.*
import com.example.util.CallScreenPermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.abs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.content.edit

class DialerRepository(rawContext: Context) {
    val context: Context = rawContext.applicationContext
    val db: AppDatabase = AppDatabase.getDatabase(context)
    val dao = db.dialerDao()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val contactCacheLock = Mutex()

    init {
        repositoryScope.launch {
            try {
                dao.getAllContactsFlow().collect {
                    // Re-read inside the lock instead of using the flow's payload: an emission
                    // can be in flight while a sync commits, and an older payload applied last
                    // would restore stale names. Querying under the same lock as the explicit
                    // refresh means the last writer always applies a fresh snapshot.
                    refreshContactCacheFromDatabase()
                }
            } catch (_: Exception) {}
        }
    }

    // --- Paging & Flows ---

    fun getFavoriteContacts(): Flow<List<Contact>> = dao.getFavoriteContacts()

    fun getAllContactsFlow(): Flow<List<Contact>> = dao.getAllContactsFlow()

    fun getAllCallHistoryFlow(): Flow<List<CallRecord>> = dao.getAllCallHistoryFlow()

    // --- Sync Logic ---

    private var contactsObserver: ContentObserver? = null
    private var callLogObserver: ContentObserver? = null
    private var lastSyncTimestamp = 0L

    fun startObservingChanges(onChanged: () -> Unit) {
        try {
            if (contactsObserver == null) {
                val observer = changeObserver(onChanged)
                context.contentResolver.registerContentObserver(
                    ContactsContract.Contacts.CONTENT_URI,
                    true,
                    observer
                )
                contactsObserver = observer
            }

            // Call-log access is allowed only while Nova Dialer holds the default Phone role.
            // Register the observer only then; a denied/revoked role must not leave a live
            // call-log subscription behind.
            if (CallScreenPermissionHelper.isDefaultDialer(context)) {
                if (callLogObserver == null) {
                    val observer = changeObserver(onChanged)
                    context.contentResolver.registerContentObserver(
                        CallLog.Calls.CONTENT_URI,
                        true,
                        observer
                    )
                    callLogObserver = observer
                }
            } else {
                callLogObserver?.let { observer ->
                    try { context.contentResolver.unregisterContentObserver(observer) } catch (_: Exception) {}
                    callLogObserver = null
                }
            }
        } catch (_: SecurityException) {}
    }

    private fun changeObserver(onChanged: () -> Unit): ContentObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val now = System.currentTimeMillis()
                if (now - lastSyncTimestamp > 1500) {
                    lastSyncTimestamp = now
                    onChanged()
                }
            }
        }

    fun stopObservingChanges() {
        contactsObserver?.let { observer ->
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (_: Exception) {}
            contactsObserver = null
        }
        callLogObserver?.let { observer ->
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (_: Exception) {}
            callLogObserver = null
        }
    }

    suspend fun syncContacts(force: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            val prefs = context.getSharedPreferences("dialer_prefs", Context.MODE_PRIVATE)
            var systemContactsCount = 0
            var maxTimestamp = 0L
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP),
                null, null, "${ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP} DESC"
            )?.use { cursor ->
                systemContactsCount = cursor.count
                val tsCol = cursor.getColumnIndex(ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP)
                if (tsCol != -1 && cursor.moveToFirst()) {
                    maxTimestamp = cursor.getLong(tsCol)
                }
            }

            val localCount = dao.getContactsCount()
            val lastSyncedCount = prefs.getInt("last_synced_contacts_count", -1)
            val lastSyncedTimestamp = prefs.getLong("last_synced_contacts_timestamp", -1L)

            if (!force && localCount > 0 && systemContactsCount == lastSyncedCount && maxTimestamp == lastSyncedTimestamp) {
                return@withContext
            }

            val systemContacts = fetchSystemContacts()

            // ATOMIC TRANSACTION: Eliminates UI screen flashing blank during sync
            db.withTransaction {
                dao.clearContacts()
                if (systemContacts.isNotEmpty()) {
                    dao.insertContacts(systemContacts)
                }
            }

            // Refresh the identity cache synchronously. The Room flow collector is asynchronous,
            // so a call-log rebuild started right after this sync could otherwise resolve names
            // from the previous contact snapshot (stale names in Recents).
            refreshContactCacheFromDatabase()

            prefs.edit {
                putInt("last_synced_contacts_count", systemContactsCount)
                putLong("last_synced_contacts_timestamp", maxTimestamp)
            }
        } catch (_: Exception) {
            try {
                val systemContacts = fetchSystemContacts()
                if (systemContacts.isNotEmpty()) {
                    db.withTransaction {
                        dao.clearContacts()
                        dao.insertContacts(systemContacts)
                    }
                    refreshContactCacheFromDatabase()
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Rebuilds [ContactCache] from the committed Room rows. Kept as a single entry point so the
     * background flow collector, the sync pipeline and the tests all observe the same snapshot.
     */
    internal suspend fun refreshContactCacheFromDatabase() {
        try {
            contactCacheLock.withLock {
                ContactCache.init(dao.getAllContactsList(), dao.getAllSettingsList())
            }
        } catch (_: Exception) {
            // A closed/recovering database must not crash name resolution; the next sync retries.
        }
    }

    /**
     * Re-resolves names of locally stored call records after a contact add/rename/delete.
     *
     * The system CallLog is only readable while Nova Dialer is the default phone handler, so
     * local records (outgoing calls placed from this app) are refreshed here directly. Records
     * that no longer resolve to a contact and still carry [previousName] are reset to their
     * number instead of keeping a stale name.
     */
    internal suspend fun refreshLocalCallHistoryIdentity(previousName: String? = null) {
        try {
            val updates = ArrayList<CallRecord>()
            for (record in dao.getAllCallHistoryList()) {
                val contact = if (record.number.isNotBlank()) {
                    ContactCache.getContact(record.number)
                } else null

                val updated = when {
                    contact != null && contact.name.isNotBlank() -> record.copy(
                        name = contact.name,
                        label = contact.label,
                        avatarText = getInitials(contact.name),
                        avatarBgValue = contact.avatarBgValue,
                        avatarTextColorValue = contact.avatarTextColorValue,
                        photoUri = contact.photoUri,
                        isVerified = false
                    )
                    previousName != null && record.name == previousName -> record.copy(
                        name = record.number.ifBlank { "Unknown" },
                        label = "",
                        avatarText = getInitials(record.number),
                        photoUri = ""
                    )
                    else -> record
                }
                if (updated != record) updates.add(updated)
            }
            if (updates.isNotEmpty()) dao.updateCallRecords(updates)
        } catch (_: Exception) {}
    }

    suspend fun syncCallLogs(force: Boolean = false) = withContext(Dispatchers.IO) {
        // Google Play permits READ_CALL_LOG/WRITE_CALL_LOG only for the default Phone
        // handler (or an explicit exception). Never touch the system CallLog otherwise.
        if (!CallScreenPermissionHelper.isDefaultDialer(context)) return@withContext
        try {
            val prefs = context.getSharedPreferences("dialer_prefs", Context.MODE_PRIVATE)
            var systemCount = 0
            var systemMaxId = 0
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID),
                null, null, "${CallLog.Calls._ID} DESC"
            )?.use { cursor ->
                systemCount = cursor.count
                if (cursor.moveToFirst()) {
                    val idCol = cursor.getColumnIndex(CallLog.Calls._ID)
                    if (idCol != -1) {
                        systemMaxId = cursor.getInt(idCol)
                    }
                }
            }

            val localCount = dao.getCallLogCount()
            val lastSyncedMaxId = prefs.getInt("last_synced_call_log_max_id", -1)
            val lastSyncedCount = prefs.getInt("last_synced_call_log_count", -1)
            val currentLang = Locale.getDefault().language
            val lastSyncedLang = prefs.getString("last_synced_locale_lang", "")
            val localeChanged = currentLang != lastSyncedLang

            if (!force && localCount > 0 && localCount == systemCount && systemMaxId == lastSyncedMaxId && systemCount == lastSyncedCount && !localeChanged) {
                return@withContext
            }

            val systemLogs = fetchSystemCallLogs()

            // ATOMIC TRANSACTION: Eliminates call log UI flash
            db.withTransaction {
                if (systemLogs.isNotEmpty()) {
                    dao.clearCallLogs()
                    dao.insertCallLogs(systemLogs)
                } else if (systemCount == 0 && lastSyncedCount > 0) {
                    dao.clearCallLogs()
                }
            }

            prefs.edit {
                putInt("last_synced_call_log_max_id", systemMaxId)
                putInt("last_synced_call_log_count", systemCount)
                putString("last_synced_locale_lang", currentLang)
            }
        } catch (_: Exception) {
            try {
                val systemLogs = fetchSystemCallLogs()
                if (systemLogs.isNotEmpty()) {
                    db.withTransaction {
                        dao.clearCallLogs()
                        dao.insertCallLogs(systemLogs)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // --- Actions ---

    suspend fun addContactWithDetails(
        name: String,
        numbers: List<LabeledNumber>,
        emails: List<LabeledEmail> = emptyList(),
        addresses: List<LabeledAddress> = emptyList(),
        accountName: String = "",
        accountType: String = ""
    ) {
        val ops = arrayListOf<ContentProviderOperation>()
        val rawInsert = ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
        if (accountName.isNotBlank() && accountType.isNotBlank() && accountName != "Phone") {
            rawInsert.withValue(ContactsContract.RawContacts.ACCOUNT_NAME, accountName)
            rawInsert.withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType)
        } else {
            rawInsert.withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
            rawInsert.withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
        }
        ops.add(rawInsert.build())
        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build()
        )

        for (numItem in numbers) {
            if (numItem.number.isBlank()) continue
            val phoneType = when (numItem.label.lowercase(Locale.ROOT)) {
                "work" -> Phone.TYPE_WORK
                "home" -> Phone.TYPE_HOME
                "other" -> Phone.TYPE_OTHER
                "main" -> Phone.TYPE_MAIN
                "mobile" -> Phone.TYPE_MOBILE
                else -> Phone.TYPE_CUSTOM
            }
            val builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, numItem.number)
                .withValue(Phone.TYPE, phoneType)
            if (phoneType == Phone.TYPE_CUSTOM) {
                builder.withValue(Phone.LABEL, numItem.label)
            }
            if (numItem.isPrimary) {
                builder.withValue(Phone.IS_PRIMARY, 1)
            }
            ops.add(builder.build())
        }

        for (emailItem in emails) {
            if (emailItem.email.isBlank()) continue
            val emailType = when (emailItem.label.lowercase(Locale.ROOT)) {
                "work" -> ContactsContract.CommonDataKinds.Email.TYPE_WORK
                "other" -> ContactsContract.CommonDataKinds.Email.TYPE_OTHER
                else -> ContactsContract.CommonDataKinds.Email.TYPE_HOME
            }
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, emailItem.email)
                    .withValue(ContactsContract.CommonDataKinds.Email.TYPE, emailType)
                    .build()
            )
        }

        for (addrItem in addresses) {
            if (addrItem.address.isBlank()) continue
            val addrType = when (addrItem.label.lowercase(Locale.ROOT)) {
                "work" -> ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK
                "other" -> ContactsContract.CommonDataKinds.StructuredPostal.TYPE_OTHER
                else -> ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME
            }
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, addrItem.address)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.TYPE, addrType)
                    .build()
            )
        }

        try { context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops) } catch (_: Exception) {}
        syncContacts(true)
        refreshLocalCallHistoryIdentity()
        syncCallLogs(force = true)
    }

    suspend fun updateContactWithDetails(
        contact: Contact,
        name: String,
        numbers: List<LabeledNumber>,
        emails: List<LabeledEmail> = emptyList(),
        addresses: List<LabeledAddress> = emptyList()
    ) {
        val previousName = contact.name
        val rawId = contact.rawContactId
        if (rawId <= 0L) {
            addContactWithDetails(name, numbers, emails, addresses, contact.accountName, contact.accountType)
            return
        }

        val ops = arrayListOf<ContentProviderOperation>()
        ops.add(
            ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                .withSelection("${ContactsContract.Data.RAW_CONTACT_ID} = ?", arrayOf(rawId.toString()))
                .build()
        )

        ops.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build()
        )

        for (numItem in numbers) {
            if (numItem.number.isBlank()) continue
            val phoneType = when (numItem.label.lowercase(Locale.ROOT)) {
                "work" -> Phone.TYPE_WORK
                "home" -> Phone.TYPE_HOME
                "other" -> Phone.TYPE_OTHER
                "main" -> Phone.TYPE_MAIN
                "mobile" -> Phone.TYPE_MOBILE
                else -> Phone.TYPE_CUSTOM
            }
            val builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                .withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, numItem.number)
                .withValue(Phone.TYPE, phoneType)
            if (phoneType == Phone.TYPE_CUSTOM) {
                builder.withValue(Phone.LABEL, numItem.label)
            }
            if (numItem.isPrimary) {
                builder.withValue(Phone.IS_PRIMARY, 1)
            }
            ops.add(builder.build())
        }

        for (emailItem in emails) {
            if (emailItem.email.isBlank()) continue
            val emailType = when (emailItem.label.lowercase(Locale.ROOT)) {
                "work" -> ContactsContract.CommonDataKinds.Email.TYPE_WORK
                "other" -> ContactsContract.CommonDataKinds.Email.TYPE_OTHER
                else -> ContactsContract.CommonDataKinds.Email.TYPE_HOME
            }
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, emailItem.email)
                    .withValue(ContactsContract.CommonDataKinds.Email.TYPE, emailType)
                    .build()
            )
        }

        for (addrItem in addresses) {
            if (addrItem.address.isBlank()) continue
            val addrType = when (addrItem.label.lowercase(Locale.ROOT)) {
                "work" -> ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK
                "other" -> ContactsContract.CommonDataKinds.StructuredPostal.TYPE_OTHER
                else -> ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME
            }
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                    .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, addrItem.address)
                    .withValue(ContactsContract.CommonDataKinds.StructuredPostal.TYPE, addrType)
                    .build()
            )
        }

        try { context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops) } catch (_: Exception) {}
        syncContacts(true)
        refreshLocalCallHistoryIdentity(previousName)
        syncCallLogs(force = true)
    }

    suspend fun deleteContact(contact: Contact) = withContext(Dispatchers.IO) {
        val previousName = contact.name
        try {
            if (contact.rawContactId > 0) {
                context.contentResolver.delete(
                    ContactsContract.RawContacts.CONTENT_URI,
                    "${ContactsContract.RawContacts._ID} = ?",
                    arrayOf(contact.rawContactId.toString())
                )
            } else {
                getContactIdFromNumber(contact.number)?.let { id ->
                    context.contentResolver.delete(
                        ContactsContract.RawContacts.CONTENT_URI,
                        "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                        arrayOf(id)
                    )
                }
            }
        } catch (_: Exception) {}
        dao.deleteContact(contact)
        // Force a full refresh: the provider count/timestamp heuristic can miss a rename or a
        // deletion within the same second.
        syncContacts(true)
        refreshLocalCallHistoryIdentity(previousName)
        syncCallLogs(force = true)
    }

    suspend fun deleteCallLog(id: Int) = withContext(Dispatchers.IO) {
        // Read the row before deleting it: the Room primary key only mirrors a system
        // CallLog _ID for rows imported from the provider. A restored or legacy row can
        // reuse the same integer, so the provider delete must prove it is the same call.
        val record = dao.getCallLogById(id)
        dao.deleteCallLog(id)
        if (record == null || !CallScreenPermissionHelper.isDefaultDialer(context)) return@withContext
        try {
            context.contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls._ID} = ? AND ${CallLog.Calls.NUMBER} = ? AND ${CallLog.Calls.DATE} = ?",
                arrayOf(record.id.toString(), record.number, record.timestampMs.toString())
            )
        } catch (_: Exception) {}
    }

    /** Removes every local row for [number] in one statement, then one provider delete. */
    suspend fun deleteCallLogsByNumber(number: String) = withContext(Dispatchers.IO) {
        dao.deleteCallLogsByNumber(number)
        if (!CallScreenPermissionHelper.isDefaultDialer(context)) return@withContext
        try {
            context.contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls.NUMBER} = ?",
                arrayOf(number)
            )
        } catch (_: Exception) {}
    }

    suspend fun clearAllCallLogs() = withContext(Dispatchers.IO) {
        dao.clearCallLogs()
        if (!CallScreenPermissionHelper.isDefaultDialer(context)) return@withContext
        try {
            context.contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
        } catch (_: Exception) {}
    }

    suspend fun getCallHistoryByNumber(number: String): List<CallRecord> = dao.getCallHistoryByNumber(number)

    suspend fun toggleFavorite(number: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        try {
            getContactIdFromNumber(number)?.let { contactId ->
                val values = ContentValues().apply { put(ContactsContract.Contacts.STARRED, if (isFavorite) 1 else 0) }
                context.contentResolver.update(
                    ContactsContract.Contacts.CONTENT_URI,
                    values,
                    "${ContactsContract.Contacts._ID} = ?",
                    arrayOf(contactId)
                )
            }
        } catch (_: Exception) {}
        dao.getContactByNumber(number)?.let { dao.updateContact(it.copy(favorite = isFavorite)) }
    }

    private fun getContactIdFromNumber(number: String): String? {
        if (number.isBlank()) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup._ID),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getString(0)
            }

            val phoneUri = Phone.CONTENT_URI
            val cleanedNumber = number.filter { it.isDigit() }
            val selection = "${Phone.NUMBER} = ? OR ${Phone.NORMALIZED_NUMBER} = ? OR REPLACE(REPLACE(REPLACE(REPLACE(${Phone.NUMBER}, ' ', ''), '-', ''), '(', ''), ')', '') = ?"
            val selectionArgs = arrayOf(number, number, cleanedNumber)
            context.contentResolver.query(
                phoneUri,
                arrayOf(Phone.CONTACT_ID),
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getString(0)
            }

            // No suffix-only fallback: matching the last 7 digits can delete/star the wrong
            // contact when two subscribers share a national tail. PhoneLookup above already
            // handles country-code and formatting variants.
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Stores a call that the platform did not add to the system CallLog (diagnostics/tests).
     *
     * This is deliberately local-only: it never inserts into [CallLog.Calls], so a manual entry
     * cannot duplicate or overwrite a real provider record. Production call history comes from
     * the Telecom-driven sync.
     */
    suspend fun insertLocalCallRecord(
        name: String,
        number: String,
        type: CallType,
        durationSeconds: Long,
        simSlot: Int = 1
    ) = withContext(Dispatchers.IO) {
        try {
            val timestampMs = System.currentTimeMillis()
            val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
            val colors = listOf(AvatarBlue to AvatarBlueText, AvatarOrange to AvatarOrangeText, AvatarGreen to AvatarGreenText)
            val pair = colors[abs(name.hashCode()) % colors.size]
            dao.insertCallLogs(
                listOf(
                    CallRecord(
                        name = name,
                        number = number,
                        label = "Mobile",
                        timestamp = sdf.format(Date(timestampMs)),
                        type = type,
                        avatarText = getInitials(name),
                        avatarBgValue = pair.first.value.toLong(),
                        avatarTextColorValue = pair.second.value.toLong(),
                        duration = durationSeconds,
                        hasVoicemail = false,
                        timestampMs = timestampMs,
                        simSlot = simSlot
                    )
                )
            )
        } catch (_: Exception) {}
    }

    // --- Call Notes ---
    suspend fun updateCallRecordingNote(id: Int, note: String) = dao.updateCallRecordingNote(id, note)
    fun getCallNotesForNumberFlow(number: String): Flow<List<CallNote>> = dao.getCallNotesForNumberFlow(number)
    fun getAllCallNotes(): Flow<List<CallNote>> = dao.getAllCallNotesFlow()
    suspend fun saveCallNote(callNote: CallNote) = dao.insertCallNote(callNote)
    suspend fun deleteCallNoteById(id: Long) = dao.deleteCallNoteById(id)
}
