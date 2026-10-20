/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.util

import android.content.Context
import androidx.core.content.edit

/**
 * Local, on-device acknowledgements for the call-recording disclosures.
 *
 * Nothing here is transmitted anywhere; it only answers "has this user already been shown and
 * accepted the recording notice". The acknowledgement is deliberately *not* reset by simply
 * reopening the settings screen, so a user who declined the notice is not silently opted in later.
 */
object RecordingDisclosure {

    private const val PREFS_NAME = "recording_disclosure"
    private const val KEY_MANUAL_ACK = "manual_recording_disclosure_acknowledged"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** True once the user has accepted the one-time notice shown before the first manual recording. */
    fun isManualAcknowledged(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MANUAL_ACK, false)

    /** Records acceptance of the one-time manual-recording notice. */
    fun acknowledgeManual(context: Context) {
        prefs(context).edit { putBoolean(KEY_MANUAL_ACK, true) }
    }
}
