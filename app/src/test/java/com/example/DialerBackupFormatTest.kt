/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example

import android.app.Application
import android.os.Looper
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.BackupRestoreManager
import com.example.data.BackupRestoreManager.BackupError
import com.example.data.BackupRestoreManager.BackupKind
import com.example.data.BackupRestoreManager.ImportResult
import com.example.model.BlockedNumber
import com.example.model.CallNote
import com.example.model.CallReminder
import com.example.model.Contact
import com.example.model.QuickResponse
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Exercises the documented backup container without depending on the private crypto helpers:
 * the tests re-implement the same PBKDF2 + AES-GCM layout so the file format itself is pinned.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DialerBackupFormatTest {

    private lateinit var context: Application
    private val password = "CorrectHorseBattery2026"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun cleanup() = runBlocking {
        AppDatabase.getDatabase(context).clearAllTables()
        shadowOf(Looper.getMainLooper()).idle()
    }

    // ---------------------------------------------------------------------------------------------
    // Format
    // ---------------------------------------------------------------------------------------------

    @Test
    fun exportWritesRecognisableVersionedContainerAndNoPlaintext() = runBlocking {
        val dao = AppDatabase.getDatabase(context).dialerDao()
        dao.insertBlockedNumber(BlockedNumber("555999888"))

        val payload = BackupRestoreManager.exportBackup(context, password)

        assertTrue("must start with the container marker", payload.startsWith("NOVABACKUP1\n"))
        assertFalse("plaintext number must not appear", payload.contains("555999888"))
        val body = payload.substringAfter('\n')
        assertTrue("body must be Base64", body.isNotEmpty() && body.none { it.isWhitespace() })
        assertEquals(BackupKind.ENCRYPTED, BackupRestoreManager.classify(payload))
    }

    @Test
    fun classifyRecognisesEverySupportedShape() {
        assertEquals(BackupKind.UNKNOWN, BackupRestoreManager.classify(""))
        assertEquals(BackupKind.UNKNOWN, BackupRestoreManager.classify("hello world"))
        assertEquals(BackupKind.LEGACY_PLAINTEXT_JSON, BackupRestoreManager.classify("{\"version\":3}"))
        assertEquals(BackupKind.LEGACY_PLAINTEXT_JSON, BackupRestoreManager.classify("[1,2,3]"))
        assertEquals(BackupKind.ENCRYPTED, BackupRestoreManager.classify("NOVABACKUP1\nAAAA"))
        assertEquals(BackupKind.LEGACY_ENCRYPTED, BackupRestoreManager.classify("A".repeat(64)))
    }

    // ---------------------------------------------------------------------------------------------
    // Authentication / rejection
    // ---------------------------------------------------------------------------------------------

    @Test
    fun wrongPasswordIsRejectedAndExistingDataIsPreserved() = runBlocking {
        val dao = AppDatabase.getDatabase(context).dialerDao()
        dao.insertBlockedNumber(BlockedNumber("111222333"))
        dao.insertContacts(listOf(contact(1L, "Keep me", "111222333")))

        val payload = BackupRestoreManager.exportBackup(context, password)
        val result = BackupRestoreManager.importBackupResult(context, payload, "DefinitelyWrong999")

        val failure = result as? ImportResult.Failure
        assertNotNull(result.toString(), failure)
        assertEquals(BackupError.WRONG_PASSWORD_OR_DAMAGED, failure!!.error)

        assertEquals(1, dao.getBlockedNumbersList().size)
        assertTrue(dao.getAllContactsList().any { it.name == "Keep me" })
    }

    @Test
    fun tamperedCiphertextIsRejected() = runBlocking {
        val payload = BackupRestoreManager.exportBackup(context, password)
        val header = payload.substringBefore('\n')
        val body = payload.substringAfter('\n')

        val index = body.length / 2
        val replacement = if (body[index] == 'A') 'B' else 'A'
        val tamperedBody = body.substring(0, index) + replacement + body.substring(index + 1)

        val result = BackupRestoreManager.importBackupResult(context, "$header\n$tamperedBody", password)
        assertTrue("tampered payload must fail authentication", result is ImportResult.Failure)
    }

    @Test
    fun tamperedHeaderIsRejectedAsUnsupported() = runBlocking {
        val payload = BackupRestoreManager.exportBackup(context, password)
        val retagged = payload.replaceFirst("NOVABACKUP1", "NOVABACKUP2")

        val result = BackupRestoreManager.importBackupResult(context, retagged, password)
        val failure = result as? ImportResult.Failure
        assertNotNull(failure)
        assertEquals(BackupError.UNSUPPORTED_VERSION, failure!!.error)
    }

    @Test
    fun truncatedFileIsRejected() = runBlocking {
        val result = BackupRestoreManager.importBackupResult(context, "NOVABACKUP1\nAAAA", password)
        assertTrue(result is ImportResult.Failure)
    }

    @Test
    fun emptyFileIsRejected() = runBlocking {
        val result = BackupRestoreManager.importBackupResult(context, "   ", password)
        val failure = result as? ImportResult.Failure
        assertNotNull(failure)
        assertEquals(BackupError.EMPTY_FILE, failure!!.error)
    }

    @Test
    fun unrecognisedFileIsRejected() = runBlocking {
        val result = BackupRestoreManager.importBackupResult(context, "this is not a backup", password)
        val failure = result as? ImportResult.Failure
        assertNotNull(failure)
        assertEquals(BackupError.NOT_A_BACKUP, failure!!.error)
    }

    @Test
    fun encryptedBackupWithoutPasswordIsRejectedBeforeDecryption() = runBlocking {
        val payload = BackupRestoreManager.exportBackup(context, password)
        val result = BackupRestoreManager.importBackupResult(context, payload, "")
        val failure = result as? ImportResult.Failure
        assertNotNull(failure)
        assertEquals(BackupError.PASSWORD_REQUIRED, failure!!.error)
    }

    @Test
    fun unknownFuturePayloadVersionIsRejected() = runBlocking {
        val json = """{"format":"novadial.backup","version":999,"contacts":[]}"""
        val payload = encryptForTest(json, password)

        val result = BackupRestoreManager.importBackupResult(context, payload, password)
        val failure = result as? ImportResult.Failure
        assertNotNull(failure)
        assertEquals(BackupError.UNSUPPORTED_VERSION, failure!!.error)
    }

    @Test
    fun foreignJsonFormatIsRejected() = runBlocking {
        val json = """{"format":"some.other.app","version":4,"contacts":[]}"""
        val payload = encryptForTest(json, password)

        val result = BackupRestoreManager.importBackupResult(context, payload, password)
        val failure = result as? ImportResult.Failure
        assertNotNull(failure)
        assertEquals(BackupError.INVALID_PAYLOAD, failure!!.error)
    }

    // ---------------------------------------------------------------------------------------------
    // Atomicity
    // ---------------------------------------------------------------------------------------------

    @Test
    fun malformedRecordAbortsTheWholeImport() = runBlocking {
        val dao = AppDatabase.getDatabase(context).dialerDao()
        dao.insertContacts(listOf(contact(7L, "Existing contact", "555000111")))

        // `blocked_numbers` is valid, but the speed-dial entry has a non-numeric key. Parsing must
        // fail before ANY row is written, so the blocked number must not appear.
        val json = """
            {
              "format": "novadial.backup",
              "version": 4,
              "blocked_numbers": ["999000999"],
              "speed_dial": [{"key": "not-a-number", "number": "1", "name": "x"}]
            }
        """.trimIndent()
        val payload = encryptForTest(json, password)

        val result = BackupRestoreManager.importBackupResult(context, payload, password)
        assertTrue(result is ImportResult.Failure)
        assertFalse(
            "a failed import must not leave partial rows",
            dao.getBlockedNumbersList().any { it.number == "999000999" }
        )
        assertTrue(dao.getAllContactsList().any { it.name == "Existing contact" })
    }

    @Test
    fun repeatedImportDoesNotDuplicateKeyedRows() = runBlocking {
        val dao = AppDatabase.getDatabase(context).dialerDao()
        dao.insertContacts(listOf(contact(42L, "Repeatable", "555123456")))
        dao.insertBlockedNumber(BlockedNumber("555123456"))

        val payload = BackupRestoreManager.exportBackup(context, password)
        AppDatabase.getDatabase(context).clearAllTables()

        assertTrue(BackupRestoreManager.importBackupResult(context, payload, password) is ImportResult.Success)
        assertTrue(BackupRestoreManager.importBackupResult(context, payload, password) is ImportResult.Success)

        assertEquals(1, dao.getAllContactsList().count { it.id == 42L })
        assertEquals(1, dao.getBlockedNumbersList().count { it.number == "555123456" })
    }

    @Test
    fun duplicateContactIdsAreRejectedInsteadOfSilentlyOverwriting() = runBlocking {
        val dao = AppDatabase.getDatabase(context).dialerDao()
        val json = """
            {
              "format": "novadial.backup",
              "version": 4,
              "contacts": [
                {"id": 7, "number": "111", "name": "First"},
                {"id": 7, "number": "222", "name": "Second"}
              ],
              "blocked_numbers": ["999000111"]
            }
        """.trimIndent()
        val payload = encryptForTest(json, password)

        val result = BackupRestoreManager.importBackupResult(context, payload, password)
        assertTrue(result is ImportResult.Failure)
        assertEquals(
            BackupRestoreManager.BackupError.INVALID_PAYLOAD,
            (result as ImportResult.Failure).error
        )
        assertFalse("no contact may be written", dao.getAllContactsList().any { it.id == 7L })
        assertFalse(
            "the whole import is aborted, not partially applied",
            dao.getBlockedNumbersList().any { it.number == "999000111" }
        )
    }

    @Test
    fun duplicateCallIdsAreRejectedInsteadOfSilentlyOverwriting() = runBlocking {
        val dao = AppDatabase.getDatabase(context).dialerDao()
        val json = """
            {
              "format": "novadial.backup",
              "version": 4,
              "call_history": [
                {"id": 3, "number": "111"},
                {"id": 3, "number": "222"}
              ],
              "blocked_numbers": ["999000222"]
            }
        """.trimIndent()
        val payload = encryptForTest(json, password)

        val result = BackupRestoreManager.importBackupResult(context, payload, password)
        assertTrue(result is ImportResult.Failure)
        assertFalse("no call may be written", dao.getAllCallHistoryList().any { it.id == 3 })
        assertFalse(dao.getBlockedNumbersList().any { it.number == "999000222" })
    }

    @Test
    fun repeatedImportDoesNotDuplicateGeneratedIdEntities() = runBlocking {
        val dao = AppDatabase.getDatabase(context).dialerDao()
        dao.insertQuickResponse(QuickResponse(message = "Can't talk right now"))
        dao.insertCallNote(
            CallNote(number = "555111222", note = "Renew passport", lastUpdated = 1_700_000_000_000L)
        )
        dao.insertReminder(
            CallReminder(
                number = "555111222",
                name = "Alex",
                reminderTime = 1_700_000_100_000L,
                isCompleted = false,
                note = "Call back"
            )
        )

        val payload = BackupRestoreManager.exportBackup(context, password)
        AppDatabase.getDatabase(context).clearAllTables()

        repeat(3) {
            assertTrue(
                BackupRestoreManager.importBackupResult(context, payload, password) is ImportResult.Success
            )
        }

        assertEquals(1, dao.getQuickResponsesList().size)
        assertEquals(1, dao.getAllCallNotesList().size)
        assertEquals(1, dao.getAllRemindersList().size)
    }

    @Test
    fun distinctCallNotesAreNotCollapsed() = runBlocking {
        val dao = AppDatabase.getDatabase(context).dialerDao()
        // Same text saved at two different moments, plus a different text. All three are real user
        // records and must survive dedupe (which keys on the exact save, not the text alone).
        dao.insertCallNote(CallNote(number = "555000111", note = "Same text", lastUpdated = 1_000L))
        dao.insertCallNote(CallNote(number = "555000111", note = "Same text", lastUpdated = 2_000L))
        dao.insertCallNote(CallNote(number = "555000111", note = "Other text", lastUpdated = 3_000L))

        val payload = BackupRestoreManager.exportBackup(context, password)
        AppDatabase.getDatabase(context).clearAllTables()
        repeat(2) {
            assertTrue(BackupRestoreManager.importBackupResult(context, payload, password) is ImportResult.Success)
        }

        val restored = dao.getAllCallNotesList()
        assertEquals(3, restored.size)
        assertEquals(1, restored.count { it.note == "Same text" && it.lastUpdated == 1_000L })
        assertEquals(1, restored.count { it.note == "Same text" && it.lastUpdated == 2_000L })
        assertEquals(1, restored.count { it.note == "Other text" })
    }

    @Test
    fun completedReminderStateSurvivesBackupAndRestore() = runBlocking {
        val dao = AppDatabase.getDatabase(context).dialerDao()
        dao.insertReminder(
            CallReminder(
                number = "555333444",
                name = "Done",
                reminderTime = 1_600_000_000_000L,
                isCompleted = true,
                note = "Already handled"
            )
        )

        val payload = BackupRestoreManager.exportBackup(context, password)
        AppDatabase.getDatabase(context).clearAllTables()
        assertTrue(BackupRestoreManager.importBackupResult(context, payload, password) is ImportResult.Success)

        val restored = dao.getAllRemindersList().single()
        assertTrue("a finished reminder must not come back as active", restored.isCompleted)
    }

    @Test
    fun reimportDoesNotResurrectACompletedReminder() = runBlocking {
        val dao = AppDatabase.getDatabase(context).dialerDao()
        dao.insertReminder(
            CallReminder(
                number = "555666777",
                name = "Later",
                reminderTime = 1_650_000_000_000L,
                isCompleted = false,
                note = "Pending"
            )
        )

        val payload = BackupRestoreManager.exportBackup(context, password)

        // The user completes the reminder after exporting, then restores the same file again.
        val active = dao.getAllRemindersList().single()
        dao.updateReminder(active.copy(isCompleted = true))
        assertTrue(BackupRestoreManager.importBackupResult(context, payload, password) is ImportResult.Success)

        val after = dao.getAllRemindersList()
        assertEquals("re-import must not duplicate the reminder", 1, after.size)
        assertTrue("re-import must not reset completion", after.single().isCompleted)
    }

    // ---------------------------------------------------------------------------------------------
    // Backward compatibility
    // ---------------------------------------------------------------------------------------------

    @Test
    fun legacyEncryptedBackupWithoutHeaderStillRestores() = runBlocking {
        val json = """{"version":3,"blocked_numbers":["123456789"]}"""
        val payload = encryptForTest(json, password, withHeader = false)

        assertEquals(BackupKind.LEGACY_ENCRYPTED, BackupRestoreManager.classify(payload))
        val result = BackupRestoreManager.importBackupResult(context, payload, password)
        assertTrue(result is ImportResult.Success)
        assertTrue(
            AppDatabase.getDatabase(context).dialerDao().getBlockedNumbersList()
                .any { it.number == "123456789" }
        )
    }

    @Test
    fun legacyPlaintextJsonIsDetectedAndRestoresOnlyThroughTheImportCall() = runBlocking {
        val json = """{"version":3,"blocked_numbers":["987654321"]}"""
        assertTrue(BackupRestoreManager.isLegacyPlaintextJson(json))

        val result = BackupRestoreManager.importBackupResult(context, json, "")
        assertTrue(result is ImportResult.Success)
        assertTrue(
            AppDatabase.getDatabase(context).dialerDao().getBlockedNumbersList()
                .any { it.number == "987654321" }
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private fun contact(id: Long, name: String, number: String) = Contact(
        id = id,
        rawContactId = id,
        contactId = id,
        number = number,
        name = name,
        label = "Mobile",
        avatarText = name.take(2).uppercase(),
        avatarBgValue = 0L,
        avatarTextColorValue = 0L
    )

    /**
     * Produces a container in exactly the documented layout:
     * `[NOVABACKUP1\n]Base64(salt[16] || iv[12] || AES-256-GCM ciphertext)`.
     */
    private fun encryptForTest(
        plaintext: String,
        password: String,
        iterations: Int = 250_000,
        withHeader: Boolean = true
    ): String {
        val random = SecureRandom()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }

        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        val header = "NOVABACKUP1"
        if (withHeader) cipher.updateAAD(header.toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(salt.size + iv.size + ciphertext.size)
        System.arraycopy(salt, 0, combined, 0, salt.size)
        System.arraycopy(iv, 0, combined, salt.size, iv.size)
        System.arraycopy(ciphertext, 0, combined, salt.size + iv.size, ciphertext.size)

        val body = Base64.encodeToString(combined, Base64.NO_WRAP)
        return if (withHeader) "$header\n$body" else body
    }
}
