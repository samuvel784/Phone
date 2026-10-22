/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.util

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.example.MyInCallService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CallPresentationPermissionsTest {

    private lateinit var context: Application

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun evaluate(
        sdkInt: Int = 34,
        notificationsEnabled: Boolean = true,
        channelExists: Boolean = true,
        channelImportance: Int = NotificationManager.IMPORTANCE_HIGH,
        fullScreenAllowed: Boolean = true,
        checkFailed: Boolean = false,
        backgroundPopupAvailable: Boolean = false
    ) = CallPresentationPermissionsReader.evaluate(
        sdkInt = sdkInt,
        notificationsEnabled = notificationsEnabled,
        channelExists = channelExists,
        channelImportance = channelImportance,
        fullScreenIntentAllowed = fullScreenAllowed,
        fullScreenPermissionCheckFailed = checkFailed,
        backgroundPopupSettingAvailable = backgroundPopupAvailable
    )

    @Test
    fun android14GrantedAndHighChannelIsEnabled() {
        val result = evaluate()

        assertEquals(CallPermissionStatus.ENABLED, result.lockScreenStatus)
        assertFalse(result.needsLockScreenAttention)
        assertFalse(result.needsAttention)
    }

    @Test
    fun android14DeniedFullScreenIsDisabled() {
        val result = evaluate(fullScreenAllowed = false)

        assertEquals(CallPermissionStatus.DISABLED, result.lockScreenStatus)
        assertTrue(result.needsLockScreenAttention)
        assertTrue(result.needsAttention)
    }

    @Test
    fun failedFullScreenQueryIsUnableToVerifyNotDisabled() {
        val result = evaluate(fullScreenAllowed = false, checkFailed = true)

        assertEquals(CallPermissionStatus.UNABLE_TO_VERIFY, result.lockScreenStatus)
        assertTrue(result.needsLockScreenAttention)
    }

    @Test
    fun disabledNotificationsAreDisabledEvenOnAndroid14() {
        val result = evaluate(notificationsEnabled = false)

        assertEquals(CallPermissionStatus.DISABLED, result.lockScreenStatus)
    }

    @Test
    fun loweredChannelImportanceIsDisabled() {
        val result = evaluate(channelImportance = NotificationManager.IMPORTANCE_DEFAULT)

        assertEquals(CallPermissionStatus.DISABLED, result.lockScreenStatus)
    }

    @Test
    fun missingChannelIsDisabled() {
        val result = evaluate(
            channelExists = false,
            channelImportance = NotificationManager.IMPORTANCE_NONE
        )

        assertEquals(CallPermissionStatus.DISABLED, result.lockScreenStatus)
    }

    @Test
    fun preAndroid14UsesNotificationPrerequisitesWithoutFullScreenApi() {
        val result = evaluate(sdkInt = 33, fullScreenAllowed = false)

        assertEquals(CallPermissionStatus.ENABLED, result.lockScreenStatus)
        assertFalse(result.fullScreenPermissionSupported)
        assertTrue(result.fullScreenIntentAllowed)
    }

    @Test
    fun backgroundPopupIsAlwaysUnableToVerify() {
        val result = evaluate(backgroundPopupAvailable = true)

        assertEquals(CallPermissionStatus.UNABLE_TO_VERIFY, result.backgroundPopupStatus)
    }

    @Test
    fun backgroundPopupOnlyRequiresAttentionWhenSuchASettingIsLikely() {
        val stockDevice = evaluate(backgroundPopupAvailable = false)
        assertFalse(stockDevice.needsBackgroundPopupAttention)
        assertFalse(stockDevice.needsAttention)

        val oemDevice = evaluate(backgroundPopupAvailable = true)
        assertTrue(oemDevice.needsBackgroundPopupAttention)
        assertTrue(oemDevice.needsAttention)
    }

    @Test
    fun fullScreenIntentIntentTargetsThePackageOnAndroid14() {
        val intent = CallScreenPermissionHelper.fullScreenIntentSettingsIntent(context)

        assertNotNull(intent)
        assertEquals(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, intent?.action)
        assertEquals("package:${context.packageName}", intent?.data.toString())
    }

    @Test
    @Config(sdk = [33])
    fun fullScreenIntentIntentIsNullBeforeAndroid14() {
        assertNull(CallScreenPermissionHelper.fullScreenIntentSettingsIntent(context))
    }

    @Test
    fun backgroundPopupCandidatesAreOnlyCreatedForTheXiaomiFamily() {
        assertTrue(
            CallScreenPermissionHelper.backgroundPopupSettingsCandidates(context, "Xiaomi").isNotEmpty()
        )
        assertTrue(
            CallScreenPermissionHelper.backgroundPopupSettingsCandidates(context, "Redmi").isNotEmpty()
        )
        assertTrue(
            CallScreenPermissionHelper.backgroundPopupSettingsCandidates(context, "POCO").isNotEmpty()
        )
        assertTrue(
            CallScreenPermissionHelper.backgroundPopupSettingsCandidates(context, "Google").isEmpty()
        )
    }

    @Test
    fun backgroundPopupCandidateCarriesThePackageAndOemAction() {
        val candidate = CallScreenPermissionHelper
            .backgroundPopupSettingsCandidates(context, "Xiaomi")
            .first()

        assertEquals("miui.intent.action.APP_PERM_EDITOR", candidate.action)
        assertEquals("com.miui.securitycenter", candidate.component?.packageName)
        assertEquals(
            "com.miui.permcenter.permissions.PermissionsEditorActivity",
            candidate.component?.className
        )
        assertEquals(context.packageName, candidate.getStringExtra("extra_pkgname"))
        assertEquals(context.packageName, candidate.getStringExtra("packageName"))
    }

    @Test
    fun backgroundPopupAvailabilityUsesTheManufacturer() {
        assertTrue(
            CallScreenPermissionHelper.isBackgroundPopupSettingLikely(context, "Xiaomi")
        )
        assertFalse(
            CallScreenPermissionHelper.isBackgroundPopupSettingLikely(context, "Google")
        )
    }

    @Test
    fun readerDoesNotClaimBackgroundPopupAccess() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "presentation_test_channel"
        manager.createNotificationChannel(
            NotificationChannel(
                channelId,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            )
        )

        val result = CallPresentationPermissionsReader.read(context, channelId)

        assertEquals(CallPermissionStatus.UNABLE_TO_VERIFY, result.backgroundPopupStatus)
        assertEquals(Build.VERSION.SDK_INT >= 34, result.fullScreenPermissionSupported)
    }

    @Test
    fun lockScreenConfigureOpensNotificationSettingsWhenNotificationsAreDisabled() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(manager).setNotificationsEnabled(false)
        val shadowApp = shadowOf(context)
        shadowApp.clearNextStartedActivities()

        val result = CallScreenPermissionHelper.openLockScreenSettings(context)

        assertTrue(result.opened)
        val started = shadowApp.nextStartedActivity
        assertNotNull(started)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, started?.action)
    }

    @Test
    fun lockScreenConfigureOpensChannelSettingsWhenChannelIsLowered() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(manager).setNotificationsEnabled(true)
        manager.deleteNotificationChannel(MyInCallService.CHANNEL_INCOMING)
        manager.createNotificationChannel(
            NotificationChannel(
                MyInCallService.CHANNEL_INCOMING,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        val shadowApp = shadowOf(context)
        shadowApp.clearNextStartedActivities()

        val result = CallScreenPermissionHelper.openLockScreenSettings(context)

        assertTrue(result.opened)
        val started = shadowApp.nextStartedActivity
        assertNotNull(started)
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, started?.action)
        assertEquals(
            MyInCallService.CHANNEL_INCOMING,
            started?.getStringExtra(Settings.EXTRA_CHANNEL_ID)
        )
    }

    @Test
    fun lockScreenConfigureOpensFullScreenPageWhenOtherChecksPass() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(manager).setNotificationsEnabled(true)
        manager.deleteNotificationChannel(MyInCallService.CHANNEL_INCOMING)
        manager.createNotificationChannel(
            NotificationChannel(
                MyInCallService.CHANNEL_INCOMING,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        val shadowApp = shadowOf(context)
        shadowApp.clearNextStartedActivities()

        val result = CallScreenPermissionHelper.openLockScreenSettings(context)

        assertTrue(result.opened)
        val started = shadowApp.nextStartedActivity
        assertNotNull(started)
        assertEquals(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, started?.action)
        assertEquals("package:${context.packageName}", started?.data.toString())
    }

    @Test
    @Config(sdk = [33])
    fun lockScreenConfigureUsesChannelOnPreAndroid14() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(manager).setNotificationsEnabled(true)
        manager.deleteNotificationChannel(MyInCallService.CHANNEL_INCOMING)
        manager.createNotificationChannel(
            NotificationChannel(
                MyInCallService.CHANNEL_INCOMING,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            )
        )
        val shadowApp = shadowOf(context)
        shadowApp.clearNextStartedActivities()

        val result = CallScreenPermissionHelper.openLockScreenSettings(context)

        assertTrue(result.opened)
        val started = shadowApp.nextStartedActivity
        assertNotNull(started)
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, started?.action)
    }
}
