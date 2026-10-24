/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.Contact
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ContactShareUtilsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `shareAsVCard generates collision-safe file with flags`() {
        val contact = Contact(
            id = 1L,
            rawContactId = 1L,
            contactId = 1L,
            name = "Test User",
            number = "+14155550100",
            label = "Mobile",
            favorite = false,
            avatarText = "TU",
            avatarBgValue = 0L,
            avatarTextColorValue = 0L
        )

        val shadowApp = shadowOf(context as android.app.Application)
        shadowApp.clearNextStartedActivities()

        ContactShareUtils.shareAsVCard(context, contact)

        val exportsDir = File(context.cacheDir, "exports")
        assertTrue("Exports directory must exist", exportsDir.exists())

        val files = exportsDir.listFiles() ?: emptyArray()
        assertTrue("At least one export file must be generated", files.isNotEmpty())
        val generatedFile = files.first()
        assertTrue("Filename must contain sanitized name", generatedFile.name.contains("Test_User"))
        assertTrue("VCard file must not be empty", generatedFile.length() > 0L)

        val startedIntent = shadowApp.nextStartedActivity
        assertNotNull("Share chooser intent must be started", startedIntent)
    }

    @Test
    fun `shareAsText launches share intent cleanly`() {
        val contact = Contact(
            id = 2L,
            rawContactId = 2L,
            contactId = 2L,
            name = "Text Share User",
            number = "+14155550200",
            label = "Mobile",
            favorite = false,
            avatarText = "TS",
            avatarBgValue = 0L,
            avatarTextColorValue = 0L
        )

        val shadowApp = shadowOf(context as android.app.Application)
        shadowApp.clearNextStartedActivities()

        ContactShareUtils.shareAsText(context, contact)

        val startedIntent = shadowApp.nextStartedActivity
        assertNotNull("Share chooser intent must be started", startedIntent)
    }
}
