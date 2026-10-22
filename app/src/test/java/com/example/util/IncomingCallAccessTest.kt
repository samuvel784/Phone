/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The incoming-call UI must fail closed and must not pretend that the OS blocked a full-screen
 * intent differently from an intent that was never attached.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IncomingCallAccessTest {

    private fun access(
        notificationsEnabled: Boolean = true,
        channelExists: Boolean = true,
        channelImportance: Int = NotificationManager.IMPORTANCE_HIGH,
        fullScreenAllowed: Boolean = true,
        checkFailed: Boolean = false
    ) = IncomingCallAccessReader.evaluate(
        sdkInt = 34,
        notificationsEnabled = notificationsEnabled,
        channelExists = channelExists,
        channelImportance = channelImportance,
        fullScreenIntentAllowed = fullScreenAllowed,
        fullScreenPermissionCheckFailed = checkFailed
    )

    @Test
    fun grantedFullScreenPermissionAndHighChannelCanAttach() {
        val result = access()

        assertTrue(result.canAttachFullScreenIntent)
        assertFalse(result.needsFullScreenSettings)
        assertFalse(result.needsNotificationSettings)
        assertFalse(result.hasActionableIssue)
    }

    @Test
    fun deniedFullScreenPermissionPreventsAttachmentAndOffersSettings() {
        val result = access(fullScreenAllowed = false)

        assertFalse("Never report a successful FSI when Android refused it", result.canAttachFullScreenIntent)
        assertTrue(result.needsFullScreenSettings)
        assertTrue(result.hasActionableIssue)
    }

    @Test
    fun permissionCheckFailureFailsClosedAndRemainsVisible() {
        val result = access(fullScreenAllowed = false, checkFailed = true)

        assertFalse(result.canAttachFullScreenIntent)
        assertTrue(result.needsFullScreenSettings)
        assertTrue(result.hasActionableIssue)
    }

    @Test
    fun disabledNotificationsPreventAttachmentAndOfferNotificationSettings() {
        val result = access(notificationsEnabled = false)

        assertFalse(result.canAttachFullScreenIntent)
        assertTrue(result.needsNotificationSettings)
        assertTrue(result.hasActionableIssue)
    }

    @Test
    fun loweredIncomingChannelImportanceIsReportedWithoutOverridingIt() {
        val result = access(channelImportance = NotificationManager.IMPORTANCE_DEFAULT)

        assertFalse(result.channelSupportsHeadsUp)
        assertTrue(result.needsNotificationSettings)
        assertTrue("The user's channel choice does not remove FSI permission", result.canAttachFullScreenIntent)
    }

    @Test
    fun missingIncomingChannelIsAnActionableNotificationIssue() {
        val result = access(channelExists = false, channelImportance = NotificationManager.IMPORTANCE_NONE)

        assertFalse(result.channelSupportsHeadsUp)
        assertTrue(result.needsNotificationSettings)
    }

    @Test
    fun preAndroid14UsesLegacyNotificationPathWithoutFullScreenSettings() {
        val result = IncomingCallAccessReader.evaluate(
            sdkInt = 33,
            notificationsEnabled = true,
            channelExists = true,
            channelImportance = NotificationManager.IMPORTANCE_HIGH,
            fullScreenIntentAllowed = false
        )

        assertFalse(result.fullScreenPermissionSupported)
        assertTrue(result.canAttachFullScreenIntent)
        assertFalse(result.needsFullScreenSettings)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun readerQueriesTheActualIncomingChannelWithoutChangingIt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "test_incoming_call_channel"
        manager.createNotificationChannel(
            NotificationChannel(
                channelId,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            )
        )

        val result = IncomingCallAccessReader.read(context, channelId)

        assertTrue(result.channelExists)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, result.channelImportance)
        assertTrue(result.channelSupportsHeadsUp)
        assertEquals(
            NotificationManager.IMPORTANCE_HIGH,
            manager.getNotificationChannel(channelId).importance
        )
    }
}
