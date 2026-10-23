/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AppResetTest {

    @Test
    fun `reset preservation of exported files`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cacheDir = context.cacheDir
        val vcfFile = File(cacheDir, "test_contacts.vcf").apply {
            writeText("BEGIN:VCARD\nFN:Test\nEND:VCARD")
        }
        val backupFile = File(cacheDir, "backup_2026.backup").apply {
            writeText("NOVABACKUP1...")
        }
        val junkFile = File(cacheDir, "temp_junk.tmp").apply {
            writeText("junk")
        }

        assertTrue(vcfFile.exists())
        assertTrue(backupFile.exists())
        assertTrue(junkFile.exists())
    }
}
