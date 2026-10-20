/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactRingtonePrefsTest {

    @Test
    fun matchesNumbersByLastEightDigits() {
        assertTrue(ContactRingtonePrefs.numbersMatch("+919876543210", "9876543210"))
        assertTrue(ContactRingtonePrefs.numbersMatch("09876543210", "+91 98765 43210"))
        assertTrue(ContactRingtonePrefs.numbersMatch("9876543210", "9876543210"))
    }

    @Test
    fun doesNotMatchDifferentNumbers() {
        assertFalse(ContactRingtonePrefs.numbersMatch("9876543210", "9876500000"))
        assertFalse(ContactRingtonePrefs.numbersMatch("", "9876543210"))
        assertFalse(ContactRingtonePrefs.numbersMatch("123", "456"))
    }

    @Test
    fun patternTimingsCoverEveryNamedPattern() {
        assertNull(ContactRingtonePrefs.patternTimings(ContactRingtonePrefs.PATTERN_DEFAULT))
        assertArrayEquals(longArrayOf(0), ContactRingtonePrefs.patternTimings(ContactRingtonePrefs.PATTERN_NONE))
        assertArrayEquals(longArrayOf(0, 100), ContactRingtonePrefs.patternTimings(ContactRingtonePrefs.VIBRATION_SHORT))
    }
}
