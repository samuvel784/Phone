/*
 * Copyright (C) 2026 NovaDial contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.ui

import com.example.model.CallRecording
import com.example.model.SpamNumber
import com.example.ui.components.RecentsLookup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentsLookupTest {
    private fun rec(id: Int, number: String, name: String) =
        CallRecording(id = id, number = number, name = name, timestamp = "t", duration = 10, filePath = "/f$id.m4a")

    private val lookup = RecentsLookup(
        recordings = listOf(
            rec(1, "+91 98765 43210", "Amma"),
            rec(2, "9876543210", "Amma"),
            rec(3, "044-2345678", "Office"),
            rec(4, "", "Ghost")
        ),
        spamNumbers = listOf(SpamNumber("+14155550100", "Scam"), SpamNumber("+14155550100", "Duplicate")),
        physicalSimCount = 2
    )

    @Test fun sameNumberWithAndWithoutCountryCodeMatches() =
        assertEquals(listOf(1, 2), lookup.recordingsFor("09876543210", "").map { it.id }.sorted())

    @Test fun nameAloneFindsTheContactsRecordings() =
        assertEquals(listOf(1, 2), lookup.recordingsFor("", "amma").map { it.id }.sorted())

    @Test fun numberAndNameTogetherDoNotDuplicate() =
        assertEquals(2, lookup.recordingsFor("+919876543210", "Amma").size)

    @Test fun landlineMatchesByLastSevenDigits() =
        assertEquals(listOf(3), lookup.recordingsFor("2345678", "").map { it.id })

    @Test fun unknownNumberAndTheNameUnknownMatchNothing() {
        assertEquals(0, lookup.recordingsFor("5550001", "Unknown").size)
        assertEquals(0, lookup.recordingsFor("", "Unknown").size)
        assertEquals(0, lookup.recordingsFor("", "").size)
    }

    @Test fun spamLookupKeepsTheFirstEntry() {
        assertEquals("Scam", lookup.spamFor("+14155550100")?.label)
        assertNull(lookup.spamFor("+14155550101"))
    }

    @Test fun simCountIsPassedThrough() = assertEquals(2, lookup.physicalSimCount)

    @Test fun emptyIndexIsSafe() = assertEquals(0, RecentsLookup.Empty.recordingsFor("123", "x").size)
}
