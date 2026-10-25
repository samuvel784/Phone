/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.DatabaseFileFamily
import com.example.data.DatabaseKeyManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class AppResetTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `resetApp deletes private database, artifacts and key but keeps document-picker exports`() =
        runBlocking {
            AppDatabase.closeInstance()
            val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
            dbFile.parentFile?.mkdirs()
            dbFile.writeText("encrypted-user-data")
            val family = DatabaseFileFamily.from(dbFile)
            File("${dbFile.absolutePath}-wal").writeText("pending-wal")
            File("${dbFile.absolutePath}-shm").writeText("pending-shm")
            family.legacyPlaintextBackup.writeText("plaintext-legacy-leftover")
            family.unreadableBackup.writeText("unreadable-artifact")
            family.swapBackup.writeText("interrupted-swap")
            val oldArchive = File(dbFile.parentFile, "recovery_archive/123").apply { mkdirs() }
            File(oldArchive, dbFile.name).writeText("old-encrypted-archive")
            // A crashed earlier reset left staged files behind; a retry must not keep them.
            val stalePending = File(dbFile.parentFile, "reset_pending/999").apply { mkdirs() }
            File(stalePending, dbFile.name).writeText("stale-staged-database")
            val straySibling = File(dbFile.parentFile, "${dbFile.name}_bak").apply {
                writeText("stale-sibling-copy")
            }

            val privateExport = File(context.filesDir, "private_export.vcf").apply {
                parentFile?.mkdirs()
                writeText("BEGIN:VCARD")
            }
            val cacheJunk = File(context.cacheDir, "temp_cache.tmp").apply { writeText("junk") }

            // A genuine document-picker export lives outside the app's private directories.
            val userChosenDir = File(
                System.getProperty("java.io.tmpdir"),
                "nova-reset-export-${UUID.randomUUID()}"
            ).apply { mkdirs() }
            val userExport = File(userChosenDir, "user_backup.backup").apply {
                writeText("NOVABACKUP1_USER_PAYLOAD")
            }

            val prefs = context.getSharedPreferences("dialer_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("test_pref_key", "test_pref_value").commit()
            val databasePrefs = context.getSharedPreferences("database_prefs", Context.MODE_PRIVATE)
            databasePrefs.edit().putString("db_pref", "value").commit()
            // Preferences a future/older build might own must also disappear; reset cannot rely on
            // a hardcoded list of "known" preference files.
            val otherPrefs = context.getSharedPreferences("nova_permission_prompts", Context.MODE_PRIVATE)
            otherPrefs.edit().putBoolean("permission_prompt_shown", true).commit()
            val otherPrefsFile = File(context.dataDir, "shared_prefs/nova_permission_prompts.xml")
            val otherPrefsWasPersisted = otherPrefsFile.exists()

            val result = AppResetManager.resetApp(context)
            assertTrue("Reset must report success: $result", result)

            // Every private database artifact is gone, including old recovery archives.
            assertFalse("Active database must not remain", dbFile.exists())
            assertFalse("WAL sidecar must not remain", File("${dbFile.absolutePath}-wal").exists())
            assertFalse("SHM sidecar must not remain", File("${dbFile.absolutePath}-shm").exists())
            assertFalse(family.legacyPlaintextBackup.exists())
            assertFalse(family.unreadableBackup.exists())
            assertFalse(family.swapBackup.exists())
            assertFalse(File(dbFile.parentFile, "recovery_archive").exists())
            assertFalse(File(dbFile.parentFile, "reset_pending").exists())
            assertFalse(stalePending.exists())
            assertFalse(straySibling.exists())

            // Key material and preferences are verified empty.
            assertFalse(DatabaseKeyManager.hasPersistedKey(context))
            assertNull(prefs.getString("test_pref_key", null))
            assertEquals(0, prefs.all.size)
            assertEquals(0, databasePrefs.all.size)
            assertEquals(0, otherPrefs.all.size)
            if (otherPrefsWasPersisted) {
                assertFalse("Preference files must be deleted, not only cleared", otherPrefsFile.exists())
            }
            assertFalse(File(context.dataDir, "shared_prefs").exists())

            // Private scratch/exports are deleted; extension alone no longer preserves anything.
            assertFalse(privateExport.exists())
            assertFalse(cacheJunk.exists())

            // The user-selected export survives untouched.
            assertTrue("Document-picker export must survive reset", userExport.exists())
            assertEquals("NOVABACKUP1_USER_PAYLOAD", userExport.readText())
            assertTrue("Test export directory should be removable", userChosenDir.deleteRecursively())
        }

    @Test
    fun `reset leaves no rows for the next database instance`() = runBlocking {
        val before = AppDatabase.getDatabase(context)
        val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
        assertTrue(AppResetManager.resetApp(context))

        // A fresh instance must be empty; in Robolectric this is an in-memory database, so the
        // real SQLCipher reopen is covered by the instrumentation suite (NOT RUN without a device).
        val after = AppDatabase.getDatabase(context)
        assertTrue(after !== before)
        assertEquals(0, after.dialerDao().getAllContactsList().size)
        assertEquals(0, after.dialerDao().getAllCallHistoryList().size)
        assertFalse(dbFile.exists())
    }

    @Test
    fun `clearKeyDetailed verifies key metadata erasure`() {
        val prefs = context.createDeviceProtectedStorageContext()
            .getSharedPreferences("dialer_secure_device_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("encrypted_db_key", "not-a-real-key")
            .putString("gcm_iv", "not-a-real-iv")
            .commit()
        assertTrue(DatabaseKeyManager.hasPersistedKey(context))

        val status = DatabaseKeyManager.clearKeyDetailed(context)
        assertTrue(status.aliasRemoved)
        assertTrue(status.preferencesCleared)
        assertTrue(status.success)
        assertFalse(DatabaseKeyManager.hasPersistedKey(context))
    }
}
