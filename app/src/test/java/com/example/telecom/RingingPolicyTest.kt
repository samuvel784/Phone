/*
 * Copyright (C) 2026 NovaDial contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import com.example.telecom.RingingPolicy.Decision
import com.example.telecom.RingingPolicy.RingerMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RingingPolicyTest {

    // --- decide() -----------------------------------------------------------------

    @Test
    fun normalRingerRingsAndVibrates() {
        assertEquals(
            Decision(playSound = true, vibrate = true),
            RingingPolicy.decide(RingerMode.NORMAL, silentRequestedByScreening = false, silencedByRules = false)
        )
    }

    @Test
    fun vibrateRingerVibratesOnly() {
        assertEquals(
            Decision(playSound = false, vibrate = true),
            RingingPolicy.decide(RingerMode.VIBRATE, silentRequestedByScreening = false, silencedByRules = false)
        )
    }

    @Test
    fun silentRingerDoesNothing() {
        assertEquals(
            Decision(playSound = false, vibrate = false),
            RingingPolicy.decide(RingerMode.SILENT, silentRequestedByScreening = false, silencedByRules = false)
        )
    }

    @Test
    fun screeningSilenceRequestOverridesNormalRinger() {
        assertEquals(
            Decision(playSound = false, vibrate = false),
            RingingPolicy.decide(RingerMode.NORMAL, silentRequestedByScreening = true, silencedByRules = false)
        )
    }

    @Test
    fun rulesSilencingOverridesNormalRinger() {
        assertEquals(
            Decision(playSound = false, vibrate = false),
            RingingPolicy.decide(RingerMode.NORMAL, silentRequestedByScreening = false, silencedByRules = true)
        )
    }

    @Test
    fun silenceRequestBeatsVibrateRinger() {
        assertEquals(
            Decision(playSound = false, vibrate = false),
            RingingPolicy.decide(RingerMode.VIBRATE, silentRequestedByScreening = true, silencedByRules = false)
        )
    }

    // --- ringLoopTimings() --------------------------------------------------------

    @Test
    fun evenLengthPatternGetsTrailingPause() {
        assertArrayEquals(
            longArrayOf(0, 100, 1000),
            RingingPolicy.ringLoopTimings(longArrayOf(0, 100))
        )
    }

    @Test
    fun oddLengthPatternIsUnchanged() {
        assertArrayEquals(
            longArrayOf(0, 100, 1000),
            RingingPolicy.ringLoopTimings(longArrayOf(0, 100, 1000))
        )
    }

    @Test
    fun defaultPatternBecomesPausable() {
        assertArrayEquals(
            longArrayOf(0, 1000, 1000, 1000, 1000),
            RingingPolicy.ringLoopTimings(longArrayOf(0, 1000, 1000, 1000))
        )
    }

    @Test
    fun nonePatternMeansNoVibration() {
        assertNull(RingingPolicy.ringLoopTimings(longArrayOf(0)))
        assertNull(RingingPolicy.ringLoopTimings(LongArray(0)))
        assertNull(RingingPolicy.ringLoopTimings(null))
    }
}
