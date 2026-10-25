/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.ui

import com.example.model.CallRecord
import com.example.model.CallType
import com.example.ui.components.RecentsFilter
import com.example.ui.components.RecentsLookup
import com.example.ui.components.groupCallRecords
import com.example.ui.components.isBlockedNumber
import com.example.util.PhoneNumberNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Retained Recents behaviours only. Recording and spam indexes were removed from the shipped row,
 * so their old tests are gone; the legacy tables themselves are still covered by the backup and
 * schema tests.
 *
 * Grouping uses the platform E.164 formatter, so it must run under Robolectric; on a plain JVM the
 * android.jar stub is unmocked and the normalizer would degrade to raw digits.
 */
@RunWith(RobolectricTestRunner::class)
class RecentsLookupTest {

    private var nextId = 1

    @Before
    fun useIndianRegion() {
        // These fixtures are Indian national/international variants. The production app derives
        // the region from the SIM/network; the test pins it so the assertion is deterministic.
        PhoneNumberNormalizer.setRegion("IN")
    }

    private fun record(
        number: String,
        name: String = "",
        type: CallType = CallType.INCOMING,
        timestampMs: Long = 1_700_000_000_000L
    ) = CallRecord(
        id = nextId++,
        name = name,
        number = number,
        label = "Mobile",
        timestamp = "10:00",
        type = type,
        avatarText = "A",
        avatarBgValue = 0L,
        avatarTextColorValue = 0L,
        duration = 10L,
        hasVoicemail = false,
        timestampMs = timestampMs
    )

    @Test
    fun simCountIsPassedThrough() {
        assertEquals(2, RecentsLookup(physicalSimCount = 2).physicalSimCount)
    }

    @Test
    fun emptyLookupUsesASingleSim() {
        assertEquals(1, RecentsLookup.Empty.physicalSimCount)
    }

    @Test
    fun filterKeepsOnlyTheRequestedCallType() {
        val records = listOf(
            record("111", type = CallType.MISSED),
            record("222", type = CallType.OUTGOING),
            record("333", type = CallType.INCOMING)
        )

        assertEquals(1, groupCallRecords(records, RecentsFilter.MISSED, "").size)
        assertEquals(1, groupCallRecords(records, RecentsFilter.DIALED, "").size)
        assertEquals(1, groupCallRecords(records, RecentsFilter.RECEIVED, "").size)
        assertEquals(3, groupCallRecords(records, RecentsFilter.ALL, "").size)
    }

    @Test
    fun searchMatchesNameAndNumberIgnoringCase() {
        val records = listOf(
            record("9876543210", name = "Amma"),
            record("5550001", name = "Office")
        )

        assertEquals("Amma", groupCallRecords(records, RecentsFilter.ALL, "amma").single().primary.name)
        assertEquals("Office", groupCallRecords(records, RecentsFilter.ALL, "555").single().primary.name)
        assertTrue(groupCallRecords(records, RecentsFilter.ALL, "missing").isEmpty())
    }

    @Test
    fun sameContactCallsAreGroupedTogether() {
        val records = listOf(
            record("9876543210", name = "Amma", timestampMs = 1_700_000_000_000L),
            record("09876543210", name = "Amma", timestampMs = 1_699_000_000_000L)
        )

        val group = groupCallRecords(records, RecentsFilter.ALL, "").single()
        assertEquals(2, group.calls.size)
        assertEquals(1_700_000_000_000L, group.primary.timestampMs)
    }

    @Test
    fun differentStoredNamesOnTheSameNumberStillGroup() {
        val records = listOf(
            record("9876543210", name = "Amma"),
            record("9876543210", name = "Office")
        )

        // The stored call-time name is not part of identity; one number is one group.
        val group = groupCallRecords(records, RecentsFilter.ALL, "").single()
        assertEquals(2, group.calls.size)
    }

    @Test
    fun differentNumbersSharingATailAreNotMerged() {
        val records = listOf(
            record("+14155550100", name = "US"),
            // Same final ten digits, different country/subscriber.
            record("+4414155550100", name = "UK")
        )

        assertEquals(2, groupCallRecords(records, RecentsFilter.ALL, "").size)
    }

    @Test
    fun blockedNumberStateRequiresAnExactMatch() {
        val blocked = setOf("+14155550100")

        assertTrue(isBlockedNumber("+14155550100", blocked))
        assertFalse(isBlockedNumber("+1 415 555 0100", blocked))
        assertFalse(isBlockedNumber("+14155550101", blocked))
        assertFalse(isBlockedNumber("+14155550100", emptySet()))
    }

    @Test
    fun emptyInputProducesNoGroups() {
        assertTrue(groupCallRecords(emptyList(), RecentsFilter.ALL, "anything").isEmpty())
    }
}
