/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
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

@RunWith(RobolectricTestRunner::class)
class AppResetTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `resetApp preserves exported backup files and vCards while removing temp junk`() = runBlocking {
        val cacheDir = context.cacheDir
        val filesDir = context.filesDir

        val vcfFile = File(cacheDir, "exported_contacts.vcf").apply {
            writeText("BEGIN:VCARD\nFN:Test Contact\nEND:VCARD")
        }
        val backupFile = File(filesDir, "user_backup.backup").apply {
            writeText("NOVABACKUP1_TEST_PAYLOAD")
        }
        val tempJunkFile = File(cacheDir, "temp_cache.tmp").apply {
            writeText("disposable cache data")
        }

        assertTrue(vcfFile.exists())
        assertTrue(backupFile.exists())
        assertTrue(tempJunkFile.exists())

        // Populate SharedPreferences to verify prefs reset
        val prefs = context.getSharedPreferences("dialer_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("test_pref_key", "test_pref_value").commit()
        assertEquals("test_pref_value", prefs.getString("test_pref_key", null))

        // Perform App Reset
        val result = AppResetManager.resetApp(context)
        assertTrue(result)

        // Assert exported files SURVIVE
        assertTrue("vCard file (.vcf) must survive app reset", vcfFile.exists())
        assertEquals("BEGIN:VCARD\nFN:Test Contact\nEND:VCARD", vcfFile.readText())

        assertTrue("Backup file (.backup) must survive app reset", backupFile.exists())
        assertEquals("NOVABACKUP1_TEST_PAYLOAD", backupFile.readText())

        // Assert disposable cache is removed
        assertFalse("Disposable temp cache file must be deleted during app reset", tempJunkFile.exists())

        // Assert SharedPreferences cleared
        assertNull(prefs.getString("test_pref_key", null))

        // Assert Database Key material is cleared
        assertFalse(DatabaseKeyManager.hasPersistedKey(context))
    }

    @Test
    fun `resetApp archives the database and recovery artifacts before clearing the key`() = runBlocking {
        AppDatabase.closeInstance()
        val dbFile = context.getDatabasePath(AppDatabase.DATABASE_NAME)
        dbFile.parentFile?.mkdirs()
        dbFile.writeText("encrypted-user-data")
        File("${dbFile.absolutePath}-wal").writeText("pending-wal")
        val unreadableBackup = File(dbFile.parentFile, "${dbFile.name}_unreadable_bak.db").apply {
            writeText("recoverable-user-data")
        }

        val result = AppResetManager.resetApp(context)
        assertTrue(result)

        // Active database and sidecars are gone...
        assertFalse("Active database must not remain after a reset", dbFile.exists())
        assertFalse("WAL sidecar must not remain after a reset", File("${dbFile.absolutePath}-wal").exists())

        // ...but the bytes are archived, not destroyed.
        val archived = File(dbFile.parentFile, "recovery_archive")
            .walkTopDown()
            .filter { it.isFile }
            .toList()
        assertTrue(
            "The active database must be preserved in recovery_archive",
            archived.any { it.name == dbFile.name && it.readText() == "encrypted-user-data" }
        )
        assertTrue(
            "Recovery artifacts must be preserved in recovery_archive",
            archived.any { it.name == unreadableBackup.name && it.readText() == "recoverable-user-data" }
        )

        assertFalse(DatabaseKeyManager.hasPersistedKey(context))
    }
}
