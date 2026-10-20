/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.util

/**
 * Helpers for keeping personal information out of logcat.
 *
 * Phone numbers are not secret in the same way a password is, but a full number in a log line
 * identifies a person and links them to a specific call, so it is personal data. Release builds
 * already strip `android.util.Log.d/i/v` via R8 (`proguard-rules.pro`), but that is only a second
 * line of defence: the value must not be logged in the first place, because debug and
 * profile/benchmark builds keep logs.
 *
 * The mask keeps only the last few digits so a developer can still correlate two log lines for the
 * *same* call without learning who the call was with.
 */
object LogRedaction {

    /** Returned when there is no usable number to show. */
    const val NONE = "<none>"

    /** Returned when the value is too short to keep any digits safely. */
    const val SHORT = "***"

    /**
     * Masks a phone number (or any free-form dial string) for logging.
     *
     * - `null`, blank, or whitespace -> [NONE]
     * - up to 4 characters of dial material -> [SHORT] (nothing useful is revealed)
     * - otherwise -> `***` plus at most the last 4 characters, preserving formatting characters
     *   (spaces, `+`, `-`, parentheses) only in the kept suffix.
     *
     * The result never contains the full input, so it is safe to place in a log message.
     */
    fun redactPhone(number: String?): String {
        val raw = number?.trim().orEmpty()
        if (raw.isEmpty()) return NONE

        // Keep a readable suffix: the last 4 non-separator characters, preserving the exact tail
        // so "*** 4567" style output still looks like a number fragment.
        val tailLength = 4
        if (raw.length <= tailLength) return SHORT

        val tail = raw.takeLast(tailLength)
        // If the tail is entirely separators there is nothing identifying to keep.
        if (tail.none { it.isLetterOrDigit() }) return SHORT
        return "***$tail"
    }

    /** True when [number] would not survive [redactPhone] intact (used by tests/asserts). */
    fun containsFullNumber(logMessage: String?, number: String?): Boolean {
        val raw = number?.trim().orEmpty()
        return raw.isNotEmpty() && logMessage?.contains(raw) == true
    }
}
