/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Android has no public API for reading (or enabling) a manufacturer's
 * "Display pop-up windows while running in the background" setting. The OS status therefore stays
 * [CallPermissionStatus.UNABLE_TO_VERIFY]; this store only records the user's own confirmation
 * that they enabled the setting manually.
 *
 * The acknowledgement is intentionally kept separate from [CallPresentationPermissions] so the
 * diagnostics screen can never present it as an OS-reported value, and it is persisted in the
 * existing preference file so it survives process death and app upgrades.
 */
object CallPermissionAcknowledgement {

    const val PREFERENCES_NAME = "dialer_prefs"
    const val KEY_BACKGROUND_POPUP = "background_popup_user_acknowledged_v1"

    fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isAcknowledged(preferences: SharedPreferences): Boolean =
        preferences.getBoolean(KEY_BACKGROUND_POPUP, false)

    fun isAcknowledged(context: Context): Boolean =
        isAcknowledged(preferences(context))

    fun setAcknowledged(context: Context, acknowledged: Boolean) {
        preferences(context).edit { putBoolean(KEY_BACKGROUND_POPUP, acknowledged) }
    }
}

/**
 * Whether the automatic reminder should still be shown.
 *
 * The reminder stays visible while a verifiable lock-screen problem exists, and while the
 * manufacturer pop-up setting applies but the user has not confirmed enabling it. An
 * acknowledgement never turns the OEM status into [CallPermissionStatus.ENABLED]; it only
 * suppresses the reminder.
 */
fun CallPresentationPermissions.needsReminder(backgroundPopupAcknowledged: Boolean): Boolean {
    return needsLockScreenAttention ||
        (needsBackgroundPopupAttention && !backgroundPopupAcknowledged)
}
