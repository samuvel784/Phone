/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.util

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat

/**
 * The result of checking whether an incoming call can be presented over the lock screen.
 *
 * This deliberately separates notification delivery, channel importance and the Android 14+
 * full-screen-intent permission. An app cannot override a user's channel choice, and it must not
 * claim that a full-screen intent was attached when the OS refused permission.
 */
data class IncomingCallAccess(
    val fullScreenPermissionSupported: Boolean,
    val fullScreenIntentAllowed: Boolean,
    val fullScreenPermissionCheckFailed: Boolean,
    val notificationsEnabled: Boolean,
    val channelCheckSupported: Boolean,
    val channelExists: Boolean,
    val channelImportance: Int
) {
    val channelSupportsHeadsUp: Boolean
        get() = channelCheckSupported && channelExists && channelImportance >= NotificationManager.IMPORTANCE_HIGH

    /**
     * True only when the notification can actually carry a full-screen intent. On Android 14+
     * this requires both the OS permission and notifications to be enabled. Before Android 14 the
     * legacy notification path is used.
     */
    val canAttachFullScreenIntent: Boolean
        get() = notificationsEnabled &&
                !fullScreenPermissionCheckFailed &&
                (!fullScreenPermissionSupported || fullScreenIntentAllowed)

    /** A user-controlled Android 14+ full-screen-intent permission action is appropriate. */
    val needsFullScreenSettings: Boolean
        get() = fullScreenPermissionSupported &&
                (!fullScreenIntentAllowed || fullScreenPermissionCheckFailed)

    /** Notification delivery or the incoming channel is limiting the heads-up fallback. */
    val needsNotificationSettings: Boolean
        get() = !notificationsEnabled ||
                (channelCheckSupported && (!channelExists || !channelSupportsHeadsUp))

    val hasActionableIssue: Boolean
        get() = needsFullScreenSettings || needsNotificationSettings
}

object IncomingCallAccessReader {

    fun read(context: Context, channelId: String): IncomingCallAccess {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        val fullScreenSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        var fullScreenAllowed = true
        var checkFailed = false

        if (fullScreenSupported) {
            if (notificationManager == null) {
                fullScreenAllowed = false
                checkFailed = true
            } else {
                try {
                    fullScreenAllowed = notificationManager.canUseFullScreenIntent()
                } catch (e: Exception) {
                    // Fail closed: never attach a full-screen intent when the OS check itself
                    // failed, but keep the diagnostic so the fallback is explainable.
                    fullScreenAllowed = false
                    checkFailed = true
                    android.util.Log.w(
                        "Nova Dialer Notifications",
                        "canUseFullScreenIntent() failed: ${e.javaClass.simpleName}: ${e.message}"
                    )
                }
            }
        }

        val channelCheckSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        val channel = if (channelCheckSupported) {
            runCatching { notificationManager?.getNotificationChannel(channelId) }.getOrNull()
        } else {
            null
        }
        val channelImportance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channel?.importance ?: NotificationManager.IMPORTANCE_NONE
        } else {
            NotificationManager.IMPORTANCE_NONE
        }

        return IncomingCallAccess(
            fullScreenPermissionSupported = fullScreenSupported,
            fullScreenIntentAllowed = fullScreenAllowed,
            fullScreenPermissionCheckFailed = checkFailed,
            notificationsEnabled = runCatching {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            }.getOrDefault(false),
            channelCheckSupported = channelCheckSupported,
            channelExists = channel != null,
            channelImportance = channelImportance
        )
    }

    /** Pure construction path used by unit tests and by [read] after the platform queries. */
    fun evaluate(
        sdkInt: Int,
        notificationsEnabled: Boolean,
        channelExists: Boolean,
        channelImportance: Int,
        fullScreenIntentAllowed: Boolean,
        fullScreenPermissionCheckFailed: Boolean = false
    ): IncomingCallAccess {
        val supported = sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        return IncomingCallAccess(
            fullScreenPermissionSupported = supported,
            fullScreenIntentAllowed = if (supported) fullScreenIntentAllowed else true,
            fullScreenPermissionCheckFailed = supported && fullScreenPermissionCheckFailed,
            notificationsEnabled = notificationsEnabled,
            channelCheckSupported = sdkInt >= Build.VERSION_CODES.O,
            channelExists = channelExists,
            channelImportance = channelImportance
        )
    }
}
