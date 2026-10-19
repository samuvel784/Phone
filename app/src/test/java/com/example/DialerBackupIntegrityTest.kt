package com.example

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.BackupRestoreManager
import com.example.model.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DialerBackupIntegrityTest {

    private lateinit var context: Application

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun cleanup() = runBlocking {
        // Robolectric can share the in-memory Room singleton across test classes in the same
        // sandbox. Leave no restored rows behind that could make later tests order-dependent.
        AppDatabase.getDatabase(context).clearAllTables()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun testBackupRequiresPassword() = runBlocking {
        val failure = runCatching {
            BackupRestoreManager.exportBackup(context, password = "")
        }.exceptionOrNull()
        assertTrue(
            "A blank password must not produce a plaintext backup",
            failure is IllegalArgumentException
        )
    }

    @Test
    fun testEncryptedBackupAndRestoreIntegrity() = runBlocking {
        val db = AppDatabase.getDatabase(context)
        val dao = db.dialerDao()

        // 1. Reset state
        db.clearAllTables()
        shadowOf(Looper.getMainLooper()).idle()

        // 2. Insert secure dummy details, including contacts and call history that the
        // old backup format silently omitted.
        dao.insertBlockedNumber(BlockedNumber("555999888"))
        dao.insertSpeedDial(SpeedDial(3, "555777666", "Confidential Partner"))
        dao.insertSetting(AppSetting("encryption_flag", "true"))
        dao.insertContacts(
            listOf(
                Contact(
                    id = 101L,
                    rawContactId = 101L,
                    contactId = 101L,
                    number = "555999888",
                    name = "Confidential Partner",
                    label = "Mobile",
                    avatarText = "CP",
                    avatarBgValue = 0L,
                    avatarTextColorValue = 0L
                )
            )
        )
        dao.insertCallLogs(
            listOf(
                CallRecord(
                    id = 202,
                    name = "Confidential Partner",
                    number = "555999888",
                    label = "Mobile",
                    timestamp = "2026-10-19 10:00",
                    type = CallType.INCOMING,
                    avatarText = "CP",
                    avatarBgValue = 0L,
                    avatarTextColorValue = 0L,
                    duration = 42L,
                    hasVoicemail = false,
                    timestampMs = 1_760_000_000_000L
                )
            )
        )
        shadowOf(Looper.getMainLooper()).idle()

        // 3. Export encrypted backup JSON representation
        val securePassword = "ChiefSecurityAuditorPassword2026"
        val encryptedPayload = BackupRestoreManager.exportBackup(context, password = securePassword)
        assertNotNull(encryptedPayload)

        // Ensure payload is encrypted (does not expose plain-text fields/numbers)
        assertFalse(encryptedPayload.contains("555999888"))
        assertFalse(encryptedPayload.contains("Confidential Partner"))
        assertFalse(encryptedPayload.contains("encryption_flag"))

        // 4. Wipe database
        db.clearAllTables()
        shadowOf(Looper.getMainLooper()).idle()

        // 5. Attempt restore with INCORRECT decryption key (should return false and block restore)
        val incorrectRestoreResult = BackupRestoreManager.importBackup(context, encryptedPayload, password = "WrongDecryptionPassword")
        assertFalse(incorrectRestoreResult)

        // Ensure database remains pristine and uncompromised
        assertTrue(dao.getBlockedNumbersList().isEmpty())
        assertTrue(dao.getSpeedDialList().isEmpty())

        // 6. Restore with the CORRECT decryption key
        val correctRestoreResult = BackupRestoreManager.importBackup(context, encryptedPayload, password = securePassword)
        assertTrue(correctRestoreResult)
        shadowOf(Looper.getMainLooper()).idle()

        // Assert full recovered state matches
        val recoveredBlocked = dao.getBlockedNumbersList()
        assertEquals("555999888", recoveredBlocked[0].number)

        val recoveredSpeed = dao.getSpeedDialList()
        assertEquals("Confidential Partner", recoveredSpeed[0].name)

        val recoveredContacts = dao.getAllContactsList()
        assertTrue(recoveredContacts.any { it.number == "555999888" && it.name == "Confidential Partner" })

        val recoveredCallHistory = dao.getAllCallHistoryList()
        assertTrue(recoveredCallHistory.any { it.number == "555999888" && it.duration == 42L })
    }
}
