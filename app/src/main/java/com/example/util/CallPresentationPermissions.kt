/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.util

import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * The three honest states this app can report for an incoming-call presentation setting.
 *
 * [UNABLE_TO_VERIFY] is deliberately not treated as "enabled". It is used when Android does not
 * expose a public API for the setting, or when the platform query itself failed.
 */
enum class CallPermissionStatus {
    ENABLED,
    DISABLED,
    UNABLE_TO_VERIFY
}

/**
 * Combined status of the two permissions required for the incoming-call experience:
 *
 * 1. Lock-screen display: on Android 14+ this is the full-screen-intent special access. On older
 *    versions the verifiable prerequisites are notification delivery and the incoming channel.
 * 2. Background pop-up windows: a manufacturer-specific permission. Android has no public API to
 *    read it, so it stays [CallPermissionStatus.UNABLE_TO_VERIFY].
 */
data class CallPresentationPermissions(
    val lockScreenStatus: CallPermissionStatus,
    val backgroundPopupStatus: CallPermissionStatus,
    val backgroundPopupSettingAvailable: Boolean,
    val fullScreenPermissionSupported: Boolean,
    val fullScreenIntentAllowed: Boolean,
    val fullScreenPermissionCheckFailed: Boolean,
    val notificationsEnabled: Boolean,
    val channelCheckSupported: Boolean,
    val channelExists: Boolean,
    val channelImportance: Int
) {
    /** A definite or unknown lock-screen problem is worth surfacing to the user. */
    val needsLockScreenAttention: Boolean
        get() = lockScreenStatus != CallPermissionStatus.ENABLED

    /**
     * Only ask for the manufacturer-specific pop-up page on devices where such a page is likely
     * to exist. On stock Android the setting does not apply, so the reminder would be noise.
     */
    val needsBackgroundPopupAttention: Boolean
        get() = backgroundPopupSettingAvailable

    val needsAttention: Boolean
        get() = needsLockScreenAttention || needsBackgroundPopupAttention
}

object CallPresentationPermissionsReader {

    fun read(context: Context, channelId: String): CallPresentationPermissions {
        val access = IncomingCallAccessReader.read(context, channelId)
        val acknowledged = CallPermissionAcknowledgement.isAcknowledged(context)
        return evaluate(
            sdkInt = Build.VERSION.SDK_INT,
            notificationsEnabled = access.notificationsEnabled,
            channelExists = access.channelExists,
            channelImportance = access.channelImportance,
            fullScreenIntentAllowed = access.fullScreenIntentAllowed,
            fullScreenPermissionCheckFailed = access.fullScreenPermissionCheckFailed,
            backgroundPopupSettingAvailable =
                CallScreenPermissionHelper.isBackgroundPopupSettingLikely(context),
            backgroundPopupAcknowledged = acknowledged
        )
    }

    /**
     * Pure status calculation used by the reader and by unit tests. Definite notification problems
     * are reported as [CallPermissionStatus.DISABLED] even when the full-screen query also failed,
     * because that is the actionable condition the user must fix first.
     */
    fun evaluate(
        sdkInt: Int,
        notificationsEnabled: Boolean,
        channelExists: Boolean,
        channelImportance: Int,
        fullScreenIntentAllowed: Boolean,
        fullScreenPermissionCheckFailed: Boolean = false,
        backgroundPopupSettingAvailable: Boolean = false,
        backgroundPopupAcknowledged: Boolean = false
    ): CallPresentationPermissions {
        val fullScreenSupported = sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        val channelCheckSupported = sdkInt >= Build.VERSION_CODES.O
        val channelNeedsAttention = channelCheckSupported &&
                (!channelExists || channelImportance < NotificationManager.IMPORTANCE_HIGH)

        val lockScreenStatus = when {
            !notificationsEnabled -> CallPermissionStatus.DISABLED
            channelNeedsAttention -> CallPermissionStatus.DISABLED
            fullScreenPermissionCheckFailed -> CallPermissionStatus.UNABLE_TO_VERIFY
            fullScreenSupported && !fullScreenIntentAllowed -> CallPermissionStatus.DISABLED
            else -> CallPermissionStatus.ENABLED
        }

        val backgroundPopupStatus = if (backgroundPopupAcknowledged) {
            CallPermissionStatus.ENABLED
        } else {
            CallPermissionStatus.UNABLE_TO_VERIFY
        }

        return CallPresentationPermissions(
            lockScreenStatus = lockScreenStatus,
            backgroundPopupStatus = backgroundPopupStatus,
            backgroundPopupSettingAvailable = backgroundPopupSettingAvailable,
            fullScreenPermissionSupported = fullScreenSupported,
            fullScreenIntentAllowed = if (fullScreenSupported) fullScreenIntentAllowed else true,
            fullScreenPermissionCheckFailed = fullScreenSupported && fullScreenPermissionCheckFailed,
            notificationsEnabled = notificationsEnabled,
            channelCheckSupported = channelCheckSupported,
            channelExists = channelExists,
            channelImportance = channelImportance
        )
    }
}
