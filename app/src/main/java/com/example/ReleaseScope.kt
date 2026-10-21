/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example

/**
 * Compile-time scope switches for the simplified v1 release.
 *
 * The deferred implementations are retained for data compatibility and possible future work,
 * but they must not be reachable through an ordinary user flow in this build. Keeping the
 * switches here makes that boundary explicit instead of relying on a missing navigation entry.
 */
object ReleaseScope {

    /** Call recording is deferred. The release declares no RECORD_AUDIO permission. */
    val CALL_RECORDING_ENABLED = false

    /** Callback reminders are deferred. ReminderAlarmReceiver is not registered in this build. */
    val CALLBACK_REMINDERS_ENABLED = false

    /** The duplicate-contact scanner is deferred; no shipped screen consumes its result. */
    val DUPLICATE_FINDER_ENABLED = false

    /** Spam labelling belongs to the deferred offline spam/screening feature. */
    val SPAM_LABELS_ENABLED = false
}
