/*
 * Copyright (C) 2026 NovaDial contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import android.telecom.Call
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exhaustive tests for the Telecom-driven multi-call policy.
 *
 * The rules encoded in [MultiCallStateMachine] are what stop the in-call UI from ever offering a
 * Swap/Merge that the platform or the carrier cannot perform. Because the machine is pure, every
 * state combination can be checked on the JVM without real (unconstructable) Telecom [Call]s.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiCallStateMachineTest {

    private val holdCaps = Call.Details.CAPABILITY_HOLD
    private val supportHoldCaps = Call.Details.CAPABILITY_SUPPORT_HOLD
    private val mergeCaps = Call.Details.CAPABILITY_MERGE_CONFERENCE
    private val swapConferenceCaps = Call.Details.CAPABILITY_SWAP_CONFERENCE
    private val manageConferenceCaps = Call.Details.CAPABILITY_MANAGE_CONFERENCE

    private fun call(
        id: Long,
        state: Int,
        capabilities: Int = 0,
        properties: Int = 0,
        conferenceableIds: List<Long> = emptyList(),
        isConferenceHost: Boolean = false,
        isConferenceParticipant: Boolean = false
    ) = CallSnapshot(
        id = id,
        state = state,
        capabilities = capabilities,
        properties = properties,
        conferenceableIds = conferenceableIds,
        isConferenceHost = isConferenceHost,
        isConferenceParticipant = isConferenceParticipant
    )

    @Test
    fun idleWhenThereAreNoCalls() {
        val state = MultiCallStateMachine.evaluate(emptyList())
        assertEquals(MultiCallPhase.IDLE, state.phase)
        assertEquals(0, state.liveCallCount)
        assertNull(state.primary)
        assertFalse(state.canAddCall)
        assertFalse(state.canSwap)
        assertFalse(state.canMerge)
    }

    @Test
    fun disconnectedCallsAreIgnored() {
        val state = MultiCallStateMachine.evaluate(
            listOf(call(1, Call.STATE_DISCONNECTED, capabilities = holdCaps))
        )
        assertEquals(MultiCallPhase.IDLE, state.phase)
        assertEquals(0, state.liveCallCount)
    }

    @Test
    fun singleActiveCallOffersHoldAndAddCall() {
        val state = MultiCallStateMachine.evaluate(
            listOf(call(1, Call.STATE_ACTIVE, capabilities = holdCaps))
        )
        assertEquals(MultiCallPhase.SINGLE_ACTIVE, state.phase)
        assertTrue(state.canHoldPrimary)
        assertFalse(state.canUnholdPrimary)
        assertTrue(state.canAddCall)
        assertFalse(state.canSwap)
        assertFalse(state.canMerge)
    }

    @Test
    fun activeCallWithoutHoldCapabilityCannotBeHeld() {
        val state = MultiCallStateMachine.evaluate(listOf(call(1, Call.STATE_ACTIVE)))
        assertEquals(MultiCallPhase.SINGLE_ACTIVE, state.phase)
        assertFalse(state.canHoldPrimary)
    }

    @Test
    fun singleHeldCallOffersUnholdAndAddCall() {
        val state = MultiCallStateMachine.evaluate(
            listOf(call(1, Call.STATE_HOLDING, capabilities = supportHoldCaps))
        )
        assertEquals(MultiCallPhase.SINGLE_HELD, state.phase)
        assertTrue(state.canUnholdPrimary)
        assertFalse(state.canHoldPrimary)
        assertTrue(state.canAddCall)
    }

    @Test
    fun singleRingingCallNeverOffersAddCall() {
        val state = MultiCallStateMachine.evaluate(listOf(call(1, Call.STATE_RINGING)))
        assertEquals(MultiCallPhase.SINGLE_INCOMING, state.phase)
        assertFalse(state.canAddCall)
    }

    @Test
    fun secondOutgoingCallIsAddCallInProgress() {
        val state = MultiCallStateMachine.evaluate(
            listOf(
                call(1, Call.STATE_ACTIVE, capabilities = holdCaps),
                call(2, Call.STATE_DIALING)
            )
        )
        assertEquals(MultiCallPhase.ADD_CALL_IN_PROGRESS, state.phase)
        assertTrue(state.hasSecondCallInProgress)
        assertEquals(2, state.liveCallCount)
        assertFalse("A second call is already being placed", state.canAddCall)
        // No merge/swap while a second call is still connecting — nothing to swap yet.
        assertFalse(state.canSwap)
        assertFalse(state.canMerge)
    }

    @Test
    fun twoCleanCallsSupportSwapWhenBothCanBeHeld() {
        val state = MultiCallStateMachine.evaluate(
            listOf(
                call(1, Call.STATE_ACTIVE, capabilities = holdCaps),
                call(2, Call.STATE_HOLDING, capabilities = holdCaps)
            ),
            preferredPrimaryId = 1
        )
        assertEquals(MultiCallPhase.TWO_CALLS, state.phase)
        assertTrue(state.canSwap)
        assertEquals(1L, state.primary?.id)
        assertEquals(2L, state.secondary?.id)
        assertTrue(state.supportsPerCallEnd)
    }

    @Test
    fun swapIsBlockedWhenAHeldCallCannotBeUnheld() {
        val state = MultiCallStateMachine.evaluate(
            listOf(
                call(1, Call.STATE_ACTIVE, capabilities = holdCaps),
                // No hold/support-hold capability: swapping is not offered.
                call(2, Call.STATE_HOLDING, capabilities = 0)
            )
        )
        assertFalse(state.canSwap)
    }

    @Test
    fun mergeRequiresCapabilityAndConferenceableRelationship() {
        val state = MultiCallStateMachine.evaluate(
            listOf(
                call(1, Call.STATE_ACTIVE, capabilities = holdCaps or mergeCaps, conferenceableIds = listOf(2L)),
                call(2, Call.STATE_HOLDING, capabilities = holdCaps)
            )
        )
        assertTrue("capability + conferenceable => merge allowed", state.canMerge)
    }

    @Test
    fun mergeIsBlockedWhenConferenceableListExcludesTheOtherCall() {
        val state = MultiCallStateMachine.evaluate(
            listOf(
                // Advertises merge but Telecom explicitly named a different (non-live) candidate.
                call(1, Call.STATE_ACTIVE, capabilities = holdCaps or mergeCaps, conferenceableIds = listOf(99L)),
                call(2, Call.STATE_HOLDING, capabilities = holdCaps)
            )
        )
        assertFalse("A non-matching conferenceable set must block merge", state.canMerge)
    }

    @Test
    fun emptyConferenceableListIsAcceptedWhenCapabilityPresent() {
        val state = MultiCallStateMachine.evaluate(
            listOf(
                call(1, Call.STATE_ACTIVE, capabilities = holdCaps or mergeCaps),
                call(2, Call.STATE_HOLDING, capabilities = holdCaps)
            )
        )
        assertTrue("Capability with no published candidate list is mergeable", state.canMerge)
    }

    @Test
    fun mergeIsOfferedFromTheHeldSideToo() {
        val state = MultiCallStateMachine.evaluate(
            listOf(
                call(1, Call.STATE_ACTIVE, capabilities = holdCaps),
                call(2, Call.STATE_HOLDING, capabilities = holdCaps or mergeCaps, conferenceableIds = listOf(1L))
            )
        )
        assertTrue(state.canMerge)
    }

    @Test
    fun mergeAndSwapAreNeverOfferedForASingleCall() {
        val state = MultiCallStateMachine.evaluate(
            listOf(call(1, Call.STATE_ACTIVE, capabilities = holdCaps or mergeCaps, conferenceableIds = emptyList()))
        )
        assertFalse(state.canMerge)
        assertFalse(state.canSwap)
    }

    @Test
    fun conferenceHostProducesConferencePhase() {
        val state = MultiCallStateMachine.evaluate(
            listOf(
                call(
                    1,
                    Call.STATE_ACTIVE,
                    capabilities = holdCaps or manageConferenceCaps,
                    properties = Call.Details.PROPERTY_CONFERENCE,
                    isConferenceHost = true
                ),
                call(2, Call.STATE_ACTIVE, isConferenceParticipant = true)
            )
        )
        assertEquals(MultiCallPhase.CONFERENCE, state.phase)
        assertTrue(state.isConference)
        assertTrue(state.canManageConference)
        assertEquals(1L, state.conference?.id)
        assertFalse("Merging is not offered once a conference exists", state.canMerge)
    }

    @Test
    fun waitingCallIsReported() {
        val state = MultiCallStateMachine.evaluate(
            listOf(
                call(1, Call.STATE_ACTIVE, capabilities = holdCaps),
                call(2, Call.STATE_RINGING)
            ),
            waitingCallId = 2L
        )
        assertTrue(state.hasWaitingCall)
    }

    @Test
    fun ringingCallWithoutWaitingIdIsNotReportedAsWaiting() {
        val state = MultiCallStateMachine.evaluate(
            listOf(
                call(1, Call.STATE_ACTIVE, capabilities = holdCaps),
                call(2, Call.STATE_RINGING)
            ),
            waitingCallId = null
        )
        assertFalse(state.hasWaitingCall)
    }

    @Test
    fun preferredPrimaryIsHonoured() {
        val state = MultiCallStateMachine.evaluate(
            listOf(
                call(1, Call.STATE_ACTIVE, capabilities = holdCaps),
                call(2, Call.STATE_HOLDING, capabilities = holdCaps)
            ),
            preferredPrimaryId = 2
        )
        assertEquals(2L, state.primary?.id)
        assertEquals(1L, state.secondary?.id)
    }

    @Test
    fun conferenceIsNotTreatedAsASwappablePair() {
        val state = MultiCallStateMachine.evaluate(
            listOf(
                call(1, Call.STATE_ACTIVE, capabilities = holdCaps, isConferenceHost = true),
                call(2, Call.STATE_HOLDING, capabilities = holdCaps)
            )
        )
        assertFalse("A conference host is not a plain active call", state.canSwap)
    }
}
