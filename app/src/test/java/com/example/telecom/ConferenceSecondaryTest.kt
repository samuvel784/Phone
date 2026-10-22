/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import android.telecom.Call
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A conference participant is not a second independent call. It must never produce the
 * pause/held secondary card that previously appeared after Merge.
 */
class ConferenceSecondaryTest {

    private fun call(
        id: Long,
        state: Int,
        properties: Int = 0,
        isConferenceHost: Boolean = false,
        isConferenceParticipant: Boolean = false
    ) = CallSnapshot(
        id = id,
        state = state,
        capabilities = 0,
        properties = properties,
        isConferenceHost = isConferenceHost,
        isConferenceParticipant = isConferenceParticipant
    )

    @Test
    fun conferenceChildrenAreNotReportedAsSecondaryHeldCall() {
        val state = MultiCallStateMachine.evaluate(
            listOf(
                call(
                    id = 1,
                    state = Call.STATE_ACTIVE,
                    properties = Call.Details.PROPERTY_CONFERENCE,
                    isConferenceHost = true
                ),
                call(
                    id = 2,
                    state = Call.STATE_HOLDING,
                    isConferenceParticipant = true
                )
            )
        )

        assertEquals(MultiCallPhase.CONFERENCE, state.phase)
        assertNull(state.secondary)
    }

    @Test
    fun genuinelyIndependentHeldCallStillGetsASecondaryCard() {
        val state = MultiCallStateMachine.evaluate(
            listOf(
                call(
                    id = 1,
                    state = Call.STATE_ACTIVE,
                    properties = Call.Details.PROPERTY_CONFERENCE,
                    isConferenceHost = true
                ),
                call(
                    id = 2,
                    state = Call.STATE_HOLDING,
                    isConferenceParticipant = true
                ),
                call(id = 3, state = Call.STATE_HOLDING)
            )
        )

        assertEquals(MultiCallPhase.CONFERENCE, state.phase)
        assertEquals(3L, state.secondary?.id)
    }
}
