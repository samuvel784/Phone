/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

/**
 * Pure decision logic for whether an incoming call should play a ringtone and
 * vibrate. It contains no Android classes so it can be unit-tested on the JVM.
 *
 * Nova Dialer plays the ringtone itself (MyInCallService declares
 * IN_CALL_SERVICE_RINGING), so the platform's "silence" request has no effect on
 * its own playback: that decision has to be made here.
 */
object RingingPolicy {

    /** The relevant subset of AudioManager ringer modes, decoupled from Android. */
    enum class RingerMode { SILENT, VIBRATE, NORMAL }

    data class Decision(val playSound: Boolean, val vibrate: Boolean)

    /** Trailing off time appended to an even-length pattern so it does not buzz non-stop. */
    const val TRAILING_PAUSE_MS = 1000L

    /**
     * @param silentRequestedByScreening the platform asked for a silent ring
     *   (`Call.EXTRA_SILENT_RINGING_REQUESTED`) or the screening service requested it.
     * @param silencedByRules the number was silenced by Focus mode / quiet hours / protection.
     */
    fun decide(
        ringerMode: RingerMode,
        silentRequestedByScreening: Boolean,
        silencedByRules: Boolean
    ): Decision {
        // A silence request always wins, whatever the ringer mode is.
        if (silentRequestedByScreening || silencedByRules) return Decision(playSound = false, vibrate = false)
        return when (ringerMode) {
            RingerMode.SILENT -> Decision(playSound = false, vibrate = false)
            RingerMode.VIBRATE -> Decision(playSound = false, vibrate = true)
            RingerMode.NORMAL -> Decision(playSound = true, vibrate = true)
        }
    }

    /**
     * Turns a wave-vibration pattern into loop timings for
     * `VibrationEffect.createWaveform(pattern, 0)`.
     *
     * A pattern with an even number of entries ends on an "on" segment, so when it
     * repeats there is no pause and the phone buzzes non-stop. We append
     * [TRAILING_PAUSE_MS] so the loop rests between repetitions. Odd-length patterns
     * already end on an "off" segment and are returned unchanged.
     *
     * Returns null for a "no vibration" pattern (`longArrayOf(0)` or empty).
     */
    fun ringLoopTimings(pattern: LongArray?): LongArray? {
        if (pattern == null || pattern.isEmpty()) return null
        if (pattern.size == 1 && pattern[0] <= 0L) return null
        return if (pattern.size % 2 == 0) pattern + TRAILING_PAUSE_MS else pattern
    }
}
