/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRedactionTest {

    @Test
    fun nullNumberBecomesNone() {
        assertEquals(LogRedaction.NONE, LogRedaction.redactPhone(null))
    }

    @Test
    fun blankAndWhitespaceNumbersBecomeNone() {
        assertEquals(LogRedaction.NONE, LogRedaction.redactPhone(""))
        assertEquals(LogRedaction.NONE, LogRedaction.redactPhone("   "))
    }

    @Test
    fun veryShortNumbersRevealNothing() {
        assertEquals(LogRedaction.SHORT, LogRedaction.redactPhone("1"))
        assertEquals(LogRedaction.SHORT, LogRedaction.redactPhone("12"))
        assertEquals(LogRedaction.SHORT, LogRedaction.redactPhone("1234"))
    }

    @Test
    fun internationalNumberKeepsOnlyTheLastFourCharacters() {
        val redacted = LogRedaction.redactPhone("+15551234567")
        assertEquals("***4567", redacted)
        assertFalse("full number must not survive", redacted.contains("+15551234567"))
        assertFalse("leading digits must not survive", redacted.contains("15551234"))
    }

    @Test
    fun unusuallyFormattedNumberNeverContainsTheFullInput() {
        val input = "+1 (555) 123-4567"
        val redacted = LogRedaction.redactPhone(input)
        assertFalse("full formatted input must not survive", redacted.contains(input))
        assertTrue("a short tail is kept for correlation", redacted.endsWith("4567"))
        assertTrue(redacted.startsWith("***"))
    }

    @Test
    fun separatorOnlyTailRevealsNothing() {
        assertEquals(LogRedaction.SHORT, LogRedaction.redactPhone("1234----"))
    }

    @Test
    fun formattedLogMessageDoesNotContainTheFullNumber() {
        val number = "+15551234567"
        val message = "event=onCallAdded offset=12ms id=7 call=${LogRedaction.redactPhone(number)} incoming=true"
        assertFalse(LogRedaction.containsFullNumber(message, number))
        assertTrue(message.contains("***4567"))
    }

    @Test
    fun shortNumberLogMessageDoesNotContainTheInput() {
        val number = "1234"
        val message = "call=${LogRedaction.redactPhone(number)}"
        assertFalse(LogRedaction.containsFullNumber(message, number))
    }
}
