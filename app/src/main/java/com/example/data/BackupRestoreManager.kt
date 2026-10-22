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

package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.room.withTransaction
import com.example.DialerRepository
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Reader
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupRestoreManager {

    // =====================================================================================
    // Backup container format
    //
    // Exported files are a small, documented text container:
    //
    //     NOVABACKUP1\n
    //     <Base64( salt[16] || iv[12] || AES-256-GCM ciphertext )>
    //
    // The first line is a human- and machine-recognisable marker. It is also fed to AES-GCM as
    // Additional Authenticated Data, so the marker cannot be swapped or edited without the
    // decryption failing the authentication tag. Older backups (no header, raw Base64) are still
    // accepted for backward compatibility.
    // =====================================================================================

    /** Marker at the start of the current encrypted container. */
    const val ENVELOPE_MAGIC = "NOVABACKUP"

    /** Version of the outer container. Bump only for structural file-format changes. */
    const val ENVELOPE_VERSION = 1

    /** Value written to the payload `format` field, so a foreign JSON file can be rejected. */
    const val PAYLOAD_FORMAT = "novadial.backup"

    /** Version of the encrypted JSON payload. Bump when the payload shape changes. */
    const val PAYLOAD_VERSION = 4

    /** Rejects absurdly large imports before any parsing or crypto work happens. */
    private const val MAX_BACKUP_CHARS = 20_000_000
    private const val MAX_CONTACTS_VCF_CHARS = 5_000_000
    private const val MAX_TEXT_LIST_CHARS = 2_000_000

    /**
     * Per-format ceilings enforced while reading. A stream is stopped as soon as one character
     * beyond the limit is observed, before a large allocation or parse can occur.
     */
    enum class TextImportKind(val maxChars: Int) {
        BACKUP(MAX_BACKUP_CHARS),
        CONTACTS_VCF(MAX_CONTACTS_VCF_CHARS),
        TEXT_LIST(MAX_TEXT_LIST_CHARS)
    }

    sealed interface TextReadResult {
        data class Success(val text: String) : TextReadResult
        data object Empty : TextReadResult
        data object TooLarge : TextReadResult
        data object Failure : TextReadResult
    }

    /** Minimum accepted backup password length (also enforced by the UI). */
    const val MIN_PASSWORD_LENGTH = 8

    /**
     * What a file looks like before we touch any application data.
     *
     * The UI uses this to decide whether to ask for a password (encrypted) or to show an explicit
     * "this file is not encrypted" warning (legacy plaintext JSON).
     */
    enum class BackupKind {
        /** Current encrypted container (has the [ENVELOPE_MAGIC] header). */
        ENCRYPTED,

        /** Older encrypted backup: raw Base64 with no header. Still decrypted for compatibility. */
        LEGACY_ENCRYPTED,

        /** Legacy plaintext JSON. Must never be imported without an explicit user warning. */
        LEGACY_PLAINTEXT_JSON,

        /** Not recognisable as any supported backup. */
        UNKNOWN
    }

    /** Classifies an imported file without decrypting or modifying anything. */
    fun classify(rawData: String): BackupKind {
        val trimmed = rawData.trim()
        if (trimmed.isEmpty()) return BackupKind.UNKNOWN
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return BackupKind.LEGACY_PLAINTEXT_JSON
        if (trimmed.startsWith(ENVELOPE_MAGIC)) return BackupKind.ENCRYPTED

        // Older encrypted backups are a single Base64 blob with no marker. Treat anything that
        // looks like a long Base64 string as legacy-encrypted so the user is asked for a password
        // rather than being told the file is invalid.
        val compact = trimmed.filterNot { it.isWhitespace() }
        if (compact.length >= 40 && compact.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }) {
            return BackupKind.LEGACY_ENCRYPTED
        }
        return BackupKind.UNKNOWN
    }

    /** True when [rawData] is a legacy (unencrypted) JSON backup. */
    fun isLegacyPlaintextJson(rawData: String): Boolean =
        classify(rawData) == BackupKind.LEGACY_PLAINTEXT_JSON

    /** Distinct, user-explainable reasons an import can fail. */
    enum class BackupError {
        EMPTY_FILE,
        TOO_LARGE,
        NOT_A_BACKUP,
        PASSWORD_REQUIRED,
        WRONG_PASSWORD_OR_DAMAGED,
        UNSUPPORTED_VERSION,
        INVALID_PAYLOAD
    }

    /** Outcome of an import. [Success] carries per-category restored row counts. */
    sealed interface ImportResult {
        data class Success(val restored: Map<String, Int>) : ImportResult
        data class Failure(val error: BackupError) : ImportResult
    }

    /** Internal signal for a structurally wrong container (wrong marker / unsupported version). */
    private class BackupFormatException(val error: BackupError) : Exception(error.name)

    suspend fun writeTextToUri(context: Context, uri: Uri, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os, Charsets.UTF_8).use { it.write(content); it.flush() }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun readTextFromUri(
        context: Context,
        uri: Uri,
        kind: TextImportKind
    ): TextReadResult = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                    readTextFromReader(reader, kind.maxChars)
                }
            } ?: TextReadResult.Failure
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            TextReadResult.Failure
        }
    }

    /**
     * Bounded reader used by every text import. It intentionally never calls readText() or
     * allocates a buffer sized from the provider's reported length.
     */
    internal fun readTextFromReader(reader: Reader, maxChars: Int): TextReadResult {
        require(maxChars > 0) { "maxChars must be positive" }
        return try {
            val output = StringBuilder(minOf(maxChars, 8192))
            val buffer = CharArray(8192)
            while (true) {
                val remaining = maxChars - output.length
                val toRead = if (remaining > 0) minOf(buffer.size, remaining) else 1
                val read = reader.read(buffer, 0, toRead)
                if (read < 0) break
                if (read == 0) continue
                if (output.length + read > maxChars) return TextReadResult.TooLarge
                output.append(buffer, 0, read)
                if (output.length == maxChars) {
                    // Exactly at the limit is allowed; one additional character is not.
                    if (reader.read() >= 0) return TextReadResult.TooLarge
                    break
                }
            }
            if (output.isEmpty()) TextReadResult.Empty else TextReadResult.Success(output.toString())
        } catch (_: Exception) {
            TextReadResult.Failure
        }
    }

    suspend fun exportBlockedNumbers(context: Context): String = withContext(Dispatchers.IO) {
        try {
            val list = AppDatabase.getDatabase(context).dialerDao().getBlockedNumbersList()
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            buildString {
                append("# Dialer Blocked Numbers Export\n# Exported: $timestamp\n# Format: One phone number per line\n\n")
                list.forEach { if (it.number.isNotBlank()) append(it.number.trim()).append("\n") }
            }
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun importBlockedNumbers(context: Context, rawData: String): Int = withContext(Dispatchers.IO) {
        try {
            val dao = AppDatabase.getDatabase(context).dialerDao()
            var count = 0
            val trimmed = rawData.trim()

            if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
                try {
                    val array = if (trimmed.startsWith("[")) {
                        JSONArray(trimmed)
                    } else {
                        JSONObject(trimmed).optJSONArray("blocked_numbers") ?: JSONArray()
                    }
                    for (i in 0 until array.length()) {
                        val num = array.optString(i, "").trim()
                        if (num.isNotBlank()) {
                            dao.insertBlockedNumber(BlockedNumber(number = num))
                            count++
                        }
                    }
                } catch (_: Exception) {}
            }

            if (count == 0) {
                trimmed.lineSequence()
                    .map { it.trim().replace("\"", "") }
                    .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("//") }
                    .map { if (it.contains(",")) it.split(",")[0].trim() else it }
                    .filter { it.length >= 2 }
                    .forEach {
                        dao.insertBlockedNumber(BlockedNumber(number = it))
                        count++
                    }
            }
            count
        } catch (_: Exception) {
            0
        }
    }

    suspend fun exportBackup(context: Context, password: String = ""): String = withContext(Dispatchers.IO) {
        // A backup contains phone numbers, names, notes and call history. Never write it in
        // plaintext: require a real password so the file is encrypted at rest and in transit
        // (for example when the user saves it to cloud storage).
        require(password.length >= MIN_PASSWORD_LENGTH) {
            "A backup password of at least $MIN_PASSWORD_LENGTH characters is required."
        }

        val dao = AppDatabase.getDatabase(context).dialerDao()

        val json = JSONObject().apply {
            put("format", PAYLOAD_FORMAT)
            put("version", PAYLOAD_VERSION)
            put("timestamp", System.currentTimeMillis())

            put("contacts", JSONArray().apply {
                dao.getAllContactsList().forEach { contact ->
                    put(JSONObject().apply {
                        put("id", contact.id)
                        put("rawContactId", contact.rawContactId)
                        put("contactId", contact.contactId)
                        put("number", contact.number)
                        put("name", contact.name)
                        put("label", contact.label)
                        put("favorite", contact.favorite)
                        put("avatarText", contact.avatarText)
                        put("avatarBgValue", contact.avatarBgValue)
                        put("avatarTextColorValue", contact.avatarTextColorValue)
                        put("t9Mapping", contact.t9Mapping)
                        put("email", contact.email)
                        put("photoUri", contact.photoUri)
                        put("accountName", contact.accountName)
                        put("accountType", contact.accountType)
                        put("numbers", JSONArray().apply {
                            contact.numbers.forEach { number ->
                                put(JSONObject().apply {
                                    put("number", number.number)
                                    put("label", number.label)
                                    put("isPrimary", number.isPrimary)
                                })
                            }
                        })
                        put("emails", JSONArray().apply {
                            contact.emails.forEach { email ->
                                put(JSONObject().apply {
                                    put("email", email.email)
                                    put("label", email.label)
                                })
                            }
                        })
                        put("addresses", JSONArray().apply {
                            contact.addresses.forEach { address ->
                                put(JSONObject().apply {
                                    put("address", address.address)
                                    put("label", address.label)
                                })
                            }
                        })
                        put("note", contact.note)
                    })
                }
            })

            put("call_history", JSONArray().apply {
                dao.getAllCallHistoryList().forEach { call ->
                    put(JSONObject().apply {
                        put("id", call.id)
                        put("name", call.name)
                        put("number", call.number)
                        put("label", call.label)
                        put("timestamp", call.timestamp)
                        put("type", call.type.name)
                        put("avatarText", call.avatarText)
                        put("avatarBgValue", call.avatarBgValue)
                        put("avatarTextColorValue", call.avatarTextColorValue)
                        put("duration", call.duration)
                        put("hasVoicemail", call.hasVoicemail)
                        put("photoUri", call.photoUri)
                        put("timestampMs", call.timestampMs)
                        put("isVerified", call.isVerified)
                        put("simSlot", call.simSlot)
                    })
                }
            })

            put("blocked_numbers", JSONArray().apply {
                dao.getBlockedNumbersList().forEach { put(it.number) }
            })

            put("speed_dial", JSONArray().apply {
                dao.getSpeedDialList().forEach {
                    put(JSONObject().apply {
                        put("key", it.key)
                        put("number", it.number)
                        put("name", it.name)
                    })
                }
            })

            put("quick_responses", JSONArray().apply {
                dao.getQuickResponsesList().forEach { put(it.message) }
            })

            put("app_settings", JSONArray().apply {
                dao.getAllSettingsList().forEach {
                    put(JSONObject().apply {
                        put("key", it.key)
                        put("value", it.value)
                    })
                }
            })

            put("call_notes", JSONArray().apply {
                dao.getAllCallNotesList().forEach {
                    put(JSONObject().apply {
                        put("number", it.number)
                        put("note", it.note)
                        put("lastUpdated", it.lastUpdated)
                    })
                }
            })

            // COMPLETE RESTORATION: Included missing entities
            put("spam_numbers", JSONArray().apply {
                dao.getAllSpamNumbersList().forEach { put(it.number) }
            })

            put("call_reminders", JSONArray().apply {
                dao.getAllRemindersList().forEach {
                    put(JSONObject().apply {
                        put("number", it.number)
                        put("contactName", it.name)
                        put("reminderTime", it.reminderTime)
                        // Restoring the completion flag avoids resurrecting finished reminders as
                        // active ones. Older readers simply ignore this additive field.
                        put("isCompleted", it.isCompleted)
                        put("note", it.note)
                    })
                }
            })
        }

        val jsonString = json.toString(2)
        val header = "$ENVELOPE_MAGIC$ENVELOPE_VERSION"
        // The header is authenticated (AAD) so a tampered marker fails the GCM tag check.
        val payload = encryptData(jsonString, password, header.toByteArray(Charsets.UTF_8))
        "$header\n$payload"
    }

    suspend fun importBackup(context: Context, rawData: String, password: String = ""): Boolean =
        importBackupResult(context, rawData, password) is ImportResult.Success

    /**
     * Imports a backup with strict validation and atomic application.
     *
     * Order of operations, chosen so that a bad file can never half-modify the database:
     *  1. size guard, then classify the container (encrypted / legacy plaintext / legacy encrypted);
     *  2. decrypt, which authenticates the whole payload with the AES-GCM tag (a wrong password or
     *     a damaged byte both fail here and leave the database untouched);
     *  3. validate the payload `format`/`version` header;
     *  4. parse every record into memory (any malformed record, or a repeated primary key, aborts
     *     the import);
     *  5. apply all rows inside a single Room transaction.
     *
     * Restore **merges** with existing data. Rows with a stable identity are written with
     * `OnConflictStrategy.REPLACE` (contacts, call history, blocked/spam numbers, speed dials,
     * settings), so the backup copy wins for the same key and unbacked-up rows are kept. Three
     * tables use generated ids, so they are merged by an exact-identity check instead:
     *  - quick responses by their exact message text,
     *  - call notes by (number, note, lastUpdated),
     *  - reminders by (number, reminderTime, note), never downgrading a completed reminder.
     * Importing the same file repeatedly therefore does not create duplicates. Call history from a
     * hypothetical id-less legacy file (never produced by this app) is the one remaining case where
     * content-based dedupe would be guesswork, so those rows are preserved as-is.
     */
    suspend fun importBackupResult(
        context: Context,
        rawData: String,
        password: String = ""
    ): ImportResult = withContext(Dispatchers.IO) {
        if (rawData.length > MAX_BACKUP_CHARS) return@withContext ImportResult.Failure(BackupError.TOO_LARGE)

        val trimmed = rawData.trim()
        if (trimmed.isEmpty()) return@withContext ImportResult.Failure(BackupError.EMPTY_FILE)

        // 1-2. Decode and authenticate the container before anything else.
        val jsonString: String = try {
            when (classify(trimmed)) {
                BackupKind.LEGACY_PLAINTEXT_JSON -> trimmed
                BackupKind.ENCRYPTED -> {
                    if (password.isBlank()) return@withContext ImportResult.Failure(BackupError.PASSWORD_REQUIRED)
                    decryptEnvelope(trimmed, password)
                }
                BackupKind.LEGACY_ENCRYPTED -> {
                    if (password.isBlank()) return@withContext ImportResult.Failure(BackupError.PASSWORD_REQUIRED)
                    decryptData(trimmed, password)
                }
                BackupKind.UNKNOWN -> return@withContext ImportResult.Failure(BackupError.NOT_A_BACKUP)
            }
        } catch (e: BackupFormatException) {
            return@withContext ImportResult.Failure(e.error)
        } catch (_: Exception) {
            // AES-GCM cannot tell a wrong password from a damaged file; both fail the tag check.
            return@withContext ImportResult.Failure(BackupError.WRONG_PASSWORD_OR_DAMAGED)
        }

        // 3. Validate the payload header.
        val json = try {
            JSONObject(jsonString)
        } catch (_: Exception) {
            return@withContext ImportResult.Failure(BackupError.INVALID_PAYLOAD)
        }
        val version = json.optInt("version", -1)
        if (version < 0) return@withContext ImportResult.Failure(BackupError.INVALID_PAYLOAD)
        if (version > PAYLOAD_VERSION) return@withContext ImportResult.Failure(BackupError.UNSUPPORTED_VERSION)
        val format = json.optString("format", "")
        if (format.isNotEmpty() && format != PAYLOAD_FORMAT) {
            return@withContext ImportResult.Failure(BackupError.INVALID_PAYLOAD)
        }

        // 4. Parse the whole payload in memory. A malformed record must abort before any write.
        val parsed = try {
            parsePayload(json)
        } catch (_: Exception) {
            return@withContext ImportResult.Failure(BackupError.INVALID_PAYLOAD)
        }

        // 5. Commit atomically.
        val db = AppDatabase.getDatabase(context)
        val dao = db.dialerDao()
        try {
            db.withTransaction {
                if (parsed.contacts.isNotEmpty()) dao.insertContacts(parsed.contacts)
                if (parsed.calls.isNotEmpty()) dao.insertCallLogs(parsed.calls)
                parsed.blocked.forEach { dao.insertBlockedNumber(it) }
                parsed.speedDial.forEach { dao.insertSpeedDial(it) }
                parsed.quickResponses.forEach { response ->
                    if (dao.countQuickResponseWithMessage(response.message) == 0) {
                        dao.insertQuickResponse(response)
                    }
                }
                parsed.settings.forEach { dao.insertSetting(it) }
                parsed.notes.forEach { note ->
                    if (dao.countCallNoteExact(note.number, note.note, note.lastUpdated) == 0) {
                        dao.insertCallNote(note)
                    }
                }
                parsed.spam.forEach { dao.insertSpamNumber(it) }
                parsed.reminders.forEach { reminder ->
                    if (dao.countReminderExact(reminder.number, reminder.reminderTime, reminder.note) == 0) {
                        dao.insertReminder(reminder)
                    }
                }
            }
        } catch (_: Exception) {
            return@withContext ImportResult.Failure(BackupError.INVALID_PAYLOAD)
        }

        ImportResult.Success(
            mapOf(
                "contacts" to parsed.contacts.size,
                "call_history" to parsed.calls.size,
                "blocked_numbers" to parsed.blocked.size,
                "speed_dial" to parsed.speedDial.size,
                "quick_responses" to parsed.quickResponses.size,
                "app_settings" to parsed.settings.size,
                "call_notes" to parsed.notes.size,
                "spam_numbers" to parsed.spam.size,
                "call_reminders" to parsed.reminders.size
            )
        )
    }

    /** Fully-parsed backup payload. Built in memory so a partial file never reaches the database. */
    private class ParsedBackup(
        val contacts: List<Contact>,
        val calls: List<CallRecord>,
        val blocked: List<BlockedNumber>,
        val speedDial: List<SpeedDial>,
        val quickResponses: List<QuickResponse>,
        val settings: List<AppSetting>,
        val notes: List<CallNote>,
        val spam: List<SpamNumber>,
        val reminders: List<CallReminder>
    )

    /** Parses every supported section of the payload. Any missing required field throws. */
    private fun parsePayload(json: JSONObject): ParsedBackup {
        val contacts = mutableListOf<Contact>()
        json.optJSONArray("contacts")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val numbers = mutableListOf<LabeledNumber>()
                obj.optJSONArray("numbers")?.let { numberArray ->
                    for (n in 0 until numberArray.length()) {
                        val numberObj = numberArray.getJSONObject(n)
                        numbers.add(
                            LabeledNumber(
                                number = numberObj.optString("number", ""),
                                label = numberObj.optString("label", "Mobile"),
                                isPrimary = numberObj.optBoolean("isPrimary", false)
                            )
                        )
                    }
                }
                val emails = mutableListOf<LabeledEmail>()
                obj.optJSONArray("emails")?.let { emailArray ->
                    for (e in 0 until emailArray.length()) {
                        val emailObj = emailArray.getJSONObject(e)
                        emails.add(
                            LabeledEmail(
                                email = emailObj.optString("email", ""),
                                label = emailObj.optString("label", "Home")
                            )
                        )
                    }
                }
                val addresses = mutableListOf<LabeledAddress>()
                obj.optJSONArray("addresses")?.let { addressArray ->
                    for (a in 0 until addressArray.length()) {
                        val addressObj = addressArray.getJSONObject(a)
                        addresses.add(
                            LabeledAddress(
                                address = addressObj.optString("address", ""),
                                label = addressObj.optString("label", "Home")
                            )
                        )
                    }
                }
                contacts.add(
                    Contact(
                        id = obj.optLong("id", 0L),
                        rawContactId = obj.optLong("rawContactId", 0L),
                        contactId = obj.optLong("contactId", 0L),
                        number = obj.optString("number", ""),
                        name = obj.optString("name", ""),
                        label = obj.optString("label", ""),
                        favorite = obj.optBoolean("favorite", false),
                        avatarText = obj.optString("avatarText", ""),
                        avatarBgValue = obj.optLong("avatarBgValue", 0L),
                        avatarTextColorValue = obj.optLong("avatarTextColorValue", 0L),
                        t9Mapping = obj.optString("t9Mapping", ""),
                        email = obj.optString("email", ""),
                        photoUri = obj.optString("photoUri", ""),
                        accountName = obj.optString("accountName", ""),
                        accountType = obj.optString("accountType", ""),
                        numbers = numbers,
                        emails = emails,
                        addresses = addresses,
                        note = obj.optString("note", "")
                    )
                )
            }
        }

        // Contacts are identified by their primary key. A payload that repeats an id would make
        // REPLACE silently overwrite one contact with another, so reject it instead of losing data.
        if (contacts.map { it.id }.toSet().size != contacts.size) {
            throw IllegalArgumentException("Duplicate contact ids in backup")
        }

        val calls = mutableListOf<CallRecord>()
        json.optJSONArray("call_history")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val type = try {
                    CallType.valueOf(obj.optString("type", CallType.INCOMING.name))
                } catch (_: Exception) {
                    CallType.INCOMING
                }
                calls.add(
                    CallRecord(
                        id = obj.optInt("id", 0),
                        name = obj.optString("name", ""),
                        number = obj.optString("number", ""),
                        label = obj.optString("label", ""),
                        timestamp = obj.optString("timestamp", ""),
                        type = type,
                        avatarText = obj.optString("avatarText", ""),
                        avatarBgValue = obj.optLong("avatarBgValue", 0L),
                        avatarTextColorValue = obj.optLong("avatarTextColorValue", 0L),
                        duration = obj.optLong("duration", 0L),
                        hasVoicemail = obj.optBoolean("hasVoicemail", false),
                        photoUri = obj.optString("photoUri", ""),
                        timestampMs = obj.optLong("timestampMs", 0L),
                        isVerified = obj.optBoolean("isVerified", false),
                        simSlot = obj.optInt("simSlot", 1)
                    )
                )
            }
        }

        // Same reasoning for call history: two records with one id cannot both survive a REPLACE.
        if (calls.map { it.id }.toSet().size != calls.size) {
            throw IllegalArgumentException("Duplicate call ids in backup")
        }

        val blocked = mutableListOf<BlockedNumber>()
        json.optJSONArray("blocked_numbers")?.let { arr ->
            for (i in 0 until arr.length()) blocked.add(BlockedNumber(arr.getString(i)))
        }

        val speedDial = mutableListOf<SpeedDial>()
        json.optJSONArray("speed_dial")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                speedDial.add(SpeedDial(obj.getInt("key"), obj.getString("number"), obj.getString("name")))
            }
        }

        val quickResponses = mutableListOf<QuickResponse>()
        json.optJSONArray("quick_responses")?.let { arr ->
            for (i in 0 until arr.length()) quickResponses.add(QuickResponse(message = arr.getString(i)))
        }

        val settings = mutableListOf<AppSetting>()
        json.optJSONArray("app_settings")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                settings.add(AppSetting(obj.getString("key"), obj.getString("value")))
            }
        }

        val notes = mutableListOf<CallNote>()
        json.optJSONArray("call_notes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                notes.add(
                    CallNote(
                        number = obj.getString("number"),
                        note = obj.getString("note"),
                        lastUpdated = obj.optLong("lastUpdated", System.currentTimeMillis())
                    )
                )
            }
        }

        val spam = mutableListOf<SpamNumber>()
        json.optJSONArray("spam_numbers")?.let { arr ->
            for (i in 0 until arr.length()) spam.add(SpamNumber(arr.getString(i)))
        }

        val reminders = mutableListOf<CallReminder>()
        json.optJSONArray("call_reminders")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                reminders.add(
                    CallReminder(
                        number = obj.getString("number"),
                        name = obj.optString("contactName", ""),
                        reminderTime = obj.getLong("reminderTime"),
                        // Absent in backups written before this field was added.
                        isCompleted = obj.optBoolean("isCompleted", false),
                        note = obj.optString("note", "")
                    )
                )
            }
        }

        return ParsedBackup(contacts, calls, blocked, speedDial, quickResponses, settings, notes, spam, reminders)
    }

    suspend fun exportContactsToVcf(context: Context): String = withContext(Dispatchers.IO) {
        try {
            val contacts = AppDatabase.getDatabase(context).dialerDao().getAllContactsList()
            buildString {
                contacts.forEach { contact ->
                    append("BEGIN:VCARD\r\nVERSION:3.0\r\n")
                    append("FN:").append(contact.name).append("\r\n")
                    contact.getAllNumbers().forEach { num ->
                        val type = when (num.label.lowercase(Locale.ROOT)) {
                            "work" -> "WORK"; "home" -> "HOME"; "mobile" -> "CELL"; else -> "VOICE"
                        }
                        append("TEL;TYPE=").append(type).append(":").append(num.number).append("\r\n")
                    }
                    contact.getAllEmails().forEach { eml ->
                        val type = if (eml.label.lowercase(Locale.ROOT) == "work") "WORK" else "HOME"
                        append("EMAIL;TYPE=").append(type).append(":").append(eml.email).append("\r\n")
                    }
                    contact.getAllAddresses().forEach { adr ->
                        val type = if (adr.label.lowercase(Locale.ROOT) == "work") "WORK" else "HOME"
                        append("ADR;TYPE=").append(type).append(":;;").append(adr.address.replace(";", " ")).append(";;;;\r\n")
                    }
                    append("END:VCARD\r\n")
                }
            }
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun importContactsFromVcf(context: Context, vcfContent: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // CRITICAL FIX: Unfold lines according to RFC 2426/6350 before parsing
            val unfolded = vcfContent.replace(Regex("\r?\n[ \t]"), "")
            val lines = unfolded.split(Regex("\\r?\\n"))

            var currentName = ""
            val currentNumbers = mutableListOf<LabeledNumber>()
            val currentEmails = mutableListOf<LabeledEmail>()
            val currentAddresses = mutableListOf<LabeledAddress>()
            val repo = DialerRepository(context)
            var count = 0

            for (line in lines) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("BEGIN:VCARD", ignoreCase = true) -> {
                        currentName = ""
                        currentNumbers.clear()
                        currentEmails.clear()
                        currentAddresses.clear()
                    }
                    trimmed.startsWith("FN", ignoreCase = true) -> {
                        val index = trimmed.indexOf(":")
                        if (index != -1) currentName = decodeVcfField(trimmed.substring(0, index), trimmed.substring(index + 1))
                    }
                    trimmed.startsWith("N", ignoreCase = true) && !trimmed.startsWith("NOTE", ignoreCase = true) -> {
                        if (currentName.isBlank()) {
                            val index = trimmed.indexOf(":")
                            if (index != -1) {
                                val decoded = decodeVcfField(trimmed.substring(0, index), trimmed.substring(index + 1))
                                currentName = decoded.split(";").filter { it.isNotBlank() }.reversed().joinToString(" ")
                            }
                        }
                    }
                    trimmed.startsWith("TEL", ignoreCase = true) -> {
                        val index = trimmed.indexOf(":")
                        if (index != -1) {
                            val rawVal = decodeVcfField(trimmed.substring(0, index), trimmed.substring(index + 1))
                            val tag = trimmed.substring(0, index).uppercase(Locale.ROOT)
                            val label = when {
                                tag.contains("WORK") -> "Work"
                                tag.contains("HOME") -> "Home"
                                else -> "Mobile"
                            }
                            if (rawVal.isNotBlank()) {
                                currentNumbers.add(LabeledNumber(number = rawVal, label = label, isPrimary = currentNumbers.isEmpty()))
                            }
                        }
                    }
                    trimmed.startsWith("EMAIL", ignoreCase = true) -> {
                        val index = trimmed.indexOf(":")
                        if (index != -1) {
                            val rawVal = decodeVcfField(trimmed.substring(0, index), trimmed.substring(index + 1))
                            val tag = trimmed.substring(0, index).uppercase(Locale.ROOT)
                            val label = if (tag.contains("WORK")) "Work" else "Home"
                            if (rawVal.isNotBlank()) currentEmails.add(LabeledEmail(email = rawVal, label = label))
                        }
                    }
                    trimmed.startsWith("ADR", ignoreCase = true) -> {
                        val index = trimmed.indexOf(":")
                        if (index != -1) {
                            val rawVal = decodeVcfField(trimmed.substring(0, index), trimmed.substring(index + 1))
                            val cleaned = rawVal.split(";").filter { it.isNotBlank() }.joinToString(", ")
                            val tag = trimmed.substring(0, index).uppercase(Locale.ROOT)
                            val label = if (tag.contains("WORK")) "Work" else "Home"
                            if (cleaned.isNotBlank()) currentAddresses.add(LabeledAddress(address = cleaned, label = label))
                        }
                    }
                    trimmed.startsWith("END:VCARD", ignoreCase = true) -> {
                        if (currentNumbers.isNotEmpty()) {
                            val nameToUse = currentName.ifBlank { currentNumbers.first().number }
                            repo.addContactWithDetails(
                                name = nameToUse,
                                numbers = currentNumbers.toList(),
                                emails = currentEmails.toList(),
                                addresses = currentAddresses.toList()
                            )
                            count++
                        }
                    }
                }
            }
            count > 0
        } catch (_: Exception) {
            false
        }
    }

    private fun decodeVcfField(tag: String, rawValue: String): String {
        var value = rawValue
        if (tag.contains("QUOTED-PRINTABLE", ignoreCase = true)) {
            value = decodeQuotedPrintable(value)
        }
        return cleanVcfValue(value)
    }

    private fun decodeQuotedPrintable(input: String): String {
        return try {
            val bytes = ByteArrayOutputStream()
            var i = 0
            while (i < input.length) {
                val c = input[i]
                if (c == '=' && i + 2 < input.length) {
                    val hex = input.substring(i + 1, i + 3)
                    val b = hex.toIntOrNull(16)
                    if (b != null) {
                        bytes.write(b)
                        i += 3
                        continue
                    }
                }
                bytes.write(c.code)
                i++
            }
            bytes.toString("UTF-8")
        } catch (_: Exception) {
            input
        }
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, 256)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
            chars.fill('\u0000') // Zeroize password in memory immediately
        }
    }

    private fun encryptData(plainText: String, password: String, aad: ByteArray? = null): String {
        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }

        val key = deriveKey(password, salt, 250000)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        // Binding the container header as AAD means a tampered marker fails authentication.
        if (aad != null) cipher.updateAAD(aad)

        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(salt.size + iv.size + encrypted.size)

        System.arraycopy(salt, 0, combined, 0, salt.size)
        System.arraycopy(iv, 0, combined, salt.size, iv.size)
        System.arraycopy(encrypted, 0, combined, salt.size + iv.size, encrypted.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Parses the `NOVABACKUP<version>` container header and decrypts the Base64 body, using the
     * exact header string as AEAD associated data.
     */
    private fun decryptEnvelope(text: String, password: String): String {
        val newline = text.indexOf('\n')
        val header = (if (newline >= 0) text.substring(0, newline) else text).trim()
        val body = if (newline >= 0) text.substring(newline + 1).trim() else ""
        val expected = "$ENVELOPE_MAGIC$ENVELOPE_VERSION"
        if (header != expected) throw BackupFormatException(BackupError.UNSUPPORTED_VERSION)
        if (body.isEmpty()) throw BackupFormatException(BackupError.NOT_A_BACKUP)
        return decryptData(body, password, header.toByteArray(Charsets.UTF_8))
    }

    private fun decryptData(cipherText: String, password: String, aad: ByteArray? = null): String {
        val combined = Base64.decode(cipherText, Base64.DEFAULT)
        if (combined.size < 28) throw IllegalArgumentException("Corrupt backup payload")

        val salt = ByteArray(16)
        val iv = ByteArray(12)
        val encrypted = ByteArray(combined.size - 28)

        System.arraycopy(combined, 0, salt, 0, 16)
        System.arraycopy(combined, 16, iv, 0, 12)
        System.arraycopy(combined, 28, encrypted, 0, encrypted.size)

        return try {
            val key = deriveKey(password, salt, 250000)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            if (aad != null) cipher.updateAAD(aad)
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            // The current container always uses 250k iterations; only pre-header backups may have
            // used the older 10k iteration count, so never silently weaken a modern backup.
            if (aad != null) throw e
            val legacyKey = deriveKey(password, salt, 10000)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, legacyKey, GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }
    }

    private fun cleanVcfValue(value: String): String = value.trim()
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\n", "\n")
        .replace("\\\\", "\\")
}