/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
}
