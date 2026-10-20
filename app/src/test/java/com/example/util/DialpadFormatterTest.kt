/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DialpadFormatterTest {

    @Test
    fun groupsTenDigitNumberInFives() {
        assertEquals("98765 43210", DialpadFormatter.format("9876543210"))
    }

    @Test
    fun separatesKnownCountryCode() {
        assertEquals("+91 98765 43210", DialpadFormatter.format("+919876543210"))
        assertEquals("+1 41555 50100", DialpadFormatter.format("+14155550100"))
        assertEquals("+44 79111 23456", DialpadFormatter.format("+447911123456"))
        assertEquals("+7 91234 56789", DialpadFormatter.format("+79123456789"))
    }

    @Test
    fun incompletePrefixIsNotSplitYet() {
        assertEquals("+", DialpadFormatter.format("+"))
        assertEquals("+9", DialpadFormatter.format("+9"))
        assertEquals("+91", DialpadFormatter.format("+91"))
        assertEquals("+91 9", DialpadFormatter.format("+919"))
    }

    @Test
    fun unknownPrefixGroupsAllDigits() {
        assertEquals("+99912 34567 8", DialpadFormatter.format("+99912345678"))
    }

    @Test
    fun serviceCodesAreShownUnchanged() {
        assertEquals("*123#", DialpadFormatter.format("*123#"))
        assertEquals("#100#", DialpadFormatter.format("#100#"))
    }

    @Test
    fun sanitizeKeepsLeadingPlusOnly() {
        assertEquals("+919876543210", DialpadFormatter.raw("+91 98765 43210"))
        assertEquals("9876", DialpadFormatter.raw("98+76"))
        assertEquals("98*76#", DialpadFormatter.raw("98*76#"))
    }

    @Test
    fun capsAtThirtyDigits() {
        val input = "1".repeat(35)
        val raw = DialpadFormatter.raw(input)
        assertEquals(DialpadFormatter.MAX_DIGITS, raw.length)
        assertEquals("11111 11111 11111 11111 11111 11111", DialpadFormatter.format(input))
    }

    @Test
    fun resolvesCountryCodeLongestFirst() {
        assertEquals("91", DialpadFormatter.countryCode("919876543210"))
        assertEquals("1", DialpadFormatter.countryCode("14155550100"))
        assertEquals("212", DialpadFormatter.countryCode("212600000000"))
        assertEquals("1", DialpadFormatter.countryCode("123456"))
        assertEquals("", DialpadFormatter.countryCode("999123"))
    }
}
