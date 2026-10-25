/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Number identity is a privacy-sensitive boundary: the wrong match shows a saved contact's name
 * on an unknown caller. These tests pin the full-identity rules, including the explicit absence of
 * suffix matching.
 */
@RunWith(RobolectricTestRunner::class)
class PhoneNumberNormalizerTest {

    @Before
    fun setUp() {
        PhoneNumberNormalizer.setRegion("IN")
    }

    @After
    fun tearDown() {
        PhoneNumberNormalizer.resetToLocale()
    }

    @Test
    fun `indian national and trunk-prefixed forms resolve to E164 digits`() {
        assertEquals("919876543210", PhoneNumberNormalizer.e164Digits("9876543210", "IN"))
        assertEquals("919876543210", PhoneNumberNormalizer.e164Digits("09876543210", "IN"))
        assertEquals("919876543210", PhoneNumberNormalizer.e164Digits("+91 98765 43210", "IN"))
    }

    @Test
    fun `us national form resolves to E164 digits`() {
        assertEquals("14155550100", PhoneNumberNormalizer.e164Digits("4155550100", "US"))
        assertEquals("14155550100", PhoneNumberNormalizer.e164Digits("+1 (415) 555-0100", "US"))
    }

    @Test
    fun `international zero-zero prefix is treated as a country code`() {
        assertEquals("442079460958", PhoneNumberNormalizer.e164Digits("00442079460958", "GB"))
    }

    @Test
    fun `national and international spellings share one identity`() {
        assertTrue(PhoneNumberNormalizer.sameNumber("09876543210", "+919876543210", "IN"))
        assertTrue(PhoneNumberNormalizer.sameNumber("9876543210", "+91 98765 43210", "IN"))
        assertEquals(
            PhoneNumberNormalizer.key("+919876543210", "IN"),
            PhoneNumberNormalizer.key("09876543210", "IN")
        )
    }

    @Test
    fun `numbers that only share a tail are never the same identity`() {
        assertFalse(PhoneNumberNormalizer.sameNumber("+14155550100", "+4414155550100"))
        assertFalse(PhoneNumberNormalizer.sameNumber("9876543210", "9876500000", "IN"))
        assertFalse(PhoneNumberNormalizer.sameNumber("5551234", "987655551234", "IN"))
    }

    @Test
    fun `empty and digitless input never matches`() {
        assertTrue(PhoneNumberNormalizer.key("").isEmpty())
        assertTrue(PhoneNumberNormalizer.key("   ").isEmpty())
        assertTrue(PhoneNumberNormalizer.key("call me").isEmpty())
        assertFalse(PhoneNumberNormalizer.sameNumber("", ""))
        assertFalse(PhoneNumberNormalizer.sameNumber("", "9876543210"))
        assertTrue(PhoneNumberNormalizer.lookupVariants("").isEmpty())
    }

    @Test
    fun `lookup variants include raw and E164 spellings without duplicates`() {
        val variants = PhoneNumberNormalizer.lookupVariants("09876543210", "IN")

        assertTrue(variants.first() == "09876543210")
        assertTrue(variants.contains("+919876543210"))
        assertTrue(variants.contains("919876543210"))
        assertEquals(variants.distinct(), variants)
    }

    @Test
    fun `region setter accepts only two-letter ISO codes`() {
        PhoneNumberNormalizer.setRegion("in")
        assertEquals("IN", PhoneNumberNormalizer.defaultRegion)

        PhoneNumberNormalizer.setRegion("USA")
        assertEquals("IN", PhoneNumberNormalizer.defaultRegion)

        PhoneNumberNormalizer.setRegion(null)
        assertEquals("IN", PhoneNumberNormalizer.defaultRegion)
    }
}
