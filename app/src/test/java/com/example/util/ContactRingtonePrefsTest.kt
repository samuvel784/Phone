/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Runs under Robolectric because number identity uses the platform E.164 formatter. A plain JVM
 * test would hit the unmocked android.jar stub and silently fall back to raw digits, which would
 * make these assertions meaningless.
 */
@RunWith(RobolectricTestRunner::class)
class ContactRingtonePrefsTest {

    @Before
    fun useIndianRegion() {
        PhoneNumberNormalizer.setRegion("IN")
    }

    @Test
    fun matchesFullIdentityAcrossNationalAndInternationalForms() {
        assertTrue(ContactRingtonePrefs.numbersMatch("+919876543210", "9876543210"))
        assertTrue(ContactRingtonePrefs.numbersMatch("09876543210", "+91 98765 43210"))
        assertTrue(ContactRingtonePrefs.numbersMatch("9876543210", "9876543210"))
    }

    @Test
    fun doesNotMatchNumbersThatOnlyShareTheFinalTenDigits() {
        // Same suffix, different subscriber. No tail length may create a match.
        assertFalse(ContactRingtonePrefs.numbersMatch("+14155550100", "+4414155550100"))
        assertFalse(ContactRingtonePrefs.numbersMatch("9876543210", "9876500000"))
    }

    @Test
    fun doesNotMatchDifferentNumbers() {
        assertFalse(ContactRingtonePrefs.numbersMatch("9876543210", "9876500000"))
        assertFalse(ContactRingtonePrefs.numbersMatch("", "9876543210"))
        assertFalse(ContactRingtonePrefs.numbersMatch("123", "456"))
    }

    @Test
    fun doesNotMatchNumbersThatOnlyShareAShortSuffix() {
        // Same 10-digit tail shape but a different subscriber/country.
        assertFalse(ContactRingtonePrefs.numbersMatch("+14155551234", "+442075551234"))
        // A 7-digit local number must not inherit a longer number's assignment by suffix.
        assertFalse(ContactRingtonePrefs.numbersMatch("5551234", "987655551234"))
    }

    @Test
    fun patternTimingsCoverEveryNamedPattern() {
        assertNull(ContactRingtonePrefs.patternTimings(ContactRingtonePrefs.PATTERN_DEFAULT))
        assertArrayEquals(longArrayOf(0), ContactRingtonePrefs.patternTimings(ContactRingtonePrefs.PATTERN_NONE))
        assertArrayEquals(longArrayOf(0, 100), ContactRingtonePrefs.patternTimings(ContactRingtonePrefs.VIBRATION_SHORT))
    }
}
