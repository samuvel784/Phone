/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncerScriptTest {
    @Test
    fun defaultTemplateIsFriendly() {
        assertEquals("Call from Sarah Connor", AnnouncerScript.render(AnnouncerScript.DEFAULT_TEMPLATE, "Sarah Connor", "Sam", "saved contact"))
    }

    @Test
    fun playfulPresetUsesTheUserName() {
        val template = AnnouncerScript.PRESETS.first { it.first == "Playful" }.second
        assertEquals("Hey Sam, Marcus is calling!", AnnouncerScript.render(template, "Marcus", "Sam", "VIP"))
    }

    @Test
    fun missingUserNameFallsBack() {
        assertEquals("Hey there, Marcus is calling", AnnouncerScript.render("Hey {user}, {name} is calling", "Marcus", "  ", "VIP"))
    }

    @Test
    fun blankTemplateFallsBackToDefault() {
        assertEquals("Call from Priya", AnnouncerScript.render("   ", "Priya", "", ""))
    }

    @Test
    fun typeVariableIsReplaced() {
        assertEquals("VIP: Amara", AnnouncerScript.render("{type}: {name}", "Amara", "", "VIP"))
    }

    @Test
    fun templateLengthIsCapped() {
        assertEquals(AnnouncerScript.MAX_TEMPLATE_LENGTH, AnnouncerScript.render("x".repeat(500), "n", "u", "t").length)
    }

    @Test
    fun scriptRepeatsEveryFiveSecondsAndStaysUnderFifteen() {
        val script = AnnouncerScript.buildScript("Call from {name}", "Ann", "Sam", "saved", 3)
        assertEquals(listOf(0L, 5000L, 10000L), script.map { it.atMs })
        assertTrue(script.all { it.atMs < AnnouncerScript.MAX_TOTAL_MS })
    }

    @Test
    fun repeatCountIsClampedBetweenOneAndThree() {
        assertEquals(1, AnnouncerScript.buildScript("{name}", "A", "", "", 0).size)
        assertEquals(3, AnnouncerScript.buildScript("{name}", "A", "", "", 99).size)
    }
}
