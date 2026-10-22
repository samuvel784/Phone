/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example

import android.telecom.Call
import com.example.telecom.CallAction
import com.example.telecom.CallActionRouter
import com.example.telecom.CallSnapshot
import com.example.telecom.IncomingPresentationPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallActionRoutingTest {

    private fun call(id: Long, state: Int) = CallSnapshot(
        id = id,
        state = state,
        capabilities = 0,
        properties = 0
    )

    @Test
    fun staleDeclineNeverFallsThroughToAnotherRingingCall() {
        val calls = listOf(
            call(1, Call.STATE_RINGING),
            call(2, Call.STATE_RINGING)
        )
        assertNull(CallActionRouter.resolve(CallAction.DECLINE, 99, calls))
        assertNull(CallActionRouter.resolve(CallAction.ANSWER, 99, calls))
        assertNull(CallActionRouter.resolve(CallAction.HANG_UP, 99, calls))
    }

    @Test
    fun staleHangUpDoesNotFallThroughToAnUnrelatedActiveCall() {
        val calls = listOf(call(1, Call.STATE_ACTIVE))
        assertNull(CallActionRouter.resolve(CallAction.HANG_UP, 99, calls))
        assertNull(CallActionRouter.resolve(CallAction.DECLINE, 99, calls))
        assertNull(CallActionRouter.resolve(CallAction.ANSWER, 99, calls))
    }

    @Test
    fun callWaitingRaceNeverAnswersTheOtherRingingCall() {
        val calls = listOf(
            call(1, Call.STATE_ACTIVE),
            call(2, Call.STATE_RINGING),
            call(3, Call.STATE_RINGING)
        )
        assertNull(CallActionRouter.resolve(CallAction.ANSWER, 2, calls.filter { it.id != 2L }))
        assertNull(CallActionRouter.resolve(CallAction.DECLINE, 2, calls.filter { it.id != 2L }))
        assertEquals(3L, CallActionRouter.resolve(CallAction.ANSWER, 3, calls))
    }

    @Test
    fun legacyHangUpIsAmbiguousWithTwoActiveCalls() {
        val calls = listOf(
            call(1, Call.STATE_ACTIVE),
            call(2, Call.STATE_HOLDING)
        )
        assertNull(CallActionRouter.resolve(CallAction.HANG_UP, CallActionRouter.NO_CALL_ID, calls))
    }

    @Test
    fun presentationClearsRingingWhenTheRingingCallEnds() {
        val before = listOf(
            call(1, Call.STATE_ACTIVE),
            call(2, Call.STATE_RINGING)
        )
        assertEquals(2L, IncomingPresentationPlanner.plan(before).waitingRingingId)

        val after = listOf(
            call(1, Call.STATE_ACTIVE),
            call(2, Call.STATE_DISCONNECTED)
        )
        val presentation = IncomingPresentationPlanner.plan(after)
        assertNull(presentation.primaryRingingId)
        assertNull(presentation.waitingRingingId)
    }

    @Test
    fun validRequestedIdWinsEvenWhenAnotherCallExists() {
        val calls = listOf(
            call(1, Call.STATE_RINGING),
            call(2, Call.STATE_RINGING)
        )
        assertEquals(2L, CallActionRouter.resolve(CallAction.ANSWER, 2, calls))
        assertEquals(1L, CallActionRouter.resolve(CallAction.DECLINE, 1, calls))
    }

    @Test
    fun requestedIdMustBeEligibleForTheAction() {
        val calls = listOf(
            call(1, Call.STATE_ACTIVE),
            call(2, Call.STATE_HOLDING)
        )
        assertNull(CallActionRouter.resolve(CallAction.ANSWER, 1, calls))
        assertNull(CallActionRouter.resolve(CallAction.DECLINE, 2, calls))
        assertEquals(1L, CallActionRouter.resolve(CallAction.HANG_UP, 1, calls))
    }

    @Test
    fun legacyIdlessActionIsAcceptedOnlyWhenUnambiguous() {
        val one = listOf(call(7, Call.STATE_RINGING))
        assertEquals(7L, CallActionRouter.resolve(CallAction.ANSWER, CallActionRouter.NO_CALL_ID, one))
        assertEquals(7L, CallActionRouter.resolve(CallAction.DECLINE, CallActionRouter.NO_CALL_ID, one))

        val two = listOf(call(7, Call.STATE_RINGING), call(8, Call.STATE_RINGING))
        assertNull(CallActionRouter.resolve(CallAction.DECLINE, CallActionRouter.NO_CALL_ID, two))

        val oneActive = listOf(call(9, Call.STATE_ACTIVE))
        assertEquals(9L, CallActionRouter.resolve(CallAction.HANG_UP, CallActionRouter.NO_CALL_ID, oneActive))
    }

    @Test
    fun presentationKeepsRingingCallWhileAnotherCallIsActive() {
        val activePlusRinging = listOf(
            call(1, Call.STATE_ACTIVE),
            call(2, Call.STATE_RINGING)
        )
        val waiting = IncomingPresentationPlanner.plan(activePlusRinging)
        assertNull(waiting.primaryRingingId)
        assertEquals(2L, waiting.waitingRingingId)

        val ringingOnly = listOf(call(2, Call.STATE_RINGING))
        val primary = IncomingPresentationPlanner.plan(ringingOnly)
        assertEquals(2L, primary.primaryRingingId)
        assertNull(primary.waitingRingingId)
    }

    @Test
    fun preferredWaitingCallIsHonouredWhenMultipleCallsRing() {
        val calls = listOf(
            call(1, Call.STATE_ACTIVE),
            call(2, Call.STATE_RINGING),
            call(3, Call.STATE_RINGING)
        )
        val presentation = IncomingPresentationPlanner.plan(calls, preferredWaitingId = 2)
        assertEquals(2L, presentation.waitingRingingId)
        assertNull(presentation.primaryRingingId)
    }

    @Test
    fun noLiveCallHasNoRingingPresentation() {
        val presentation = IncomingPresentationPlanner.plan(
            listOf(call(1, Call.STATE_DISCONNECTED))
        )
        assertNull(presentation.primaryRingingId)
        assertNull(presentation.waitingRingingId)
    }

    @Test
    fun appLockNeverUsesALiveCallAsAuthentication() {
        assertEquals(
            MainContentMode.RESTRICTED_CALL,
            resolveMainContentMode(
                isAppLockEnabled = true,
                isAppAuthenticated = false,
                isCallActive = true,
                hasLiveCall = true
            )
        )
        assertEquals(
            MainContentMode.LOCKED,
            resolveMainContentMode(
                isAppLockEnabled = true,
                isAppAuthenticated = false,
                isCallActive = false,
                hasLiveCall = false
            )
        )
        assertEquals(
            MainContentMode.FULL_APP,
            resolveMainContentMode(
                isAppLockEnabled = true,
                isAppAuthenticated = true,
                isCallActive = false,
                hasLiveCall = false
            )
        )
        assertEquals(
            MainContentMode.FULL_APP,
            resolveMainContentMode(
                isAppLockEnabled = false,
                isAppAuthenticated = false,
                isCallActive = true,
                hasLiveCall = true
            )
        )
    }
}


