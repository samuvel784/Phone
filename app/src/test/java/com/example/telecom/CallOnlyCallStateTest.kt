/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import android.telecom.Call
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The call-only surface must follow Telecom's exact call state. These cases cover every state the
 * answering UI distinguishes, including the short window after an answer was dispatched but
 * before Telecom reports CONNECTING/ACTIVE.
 */
class CallOnlyCallStateTest {

    @Test
    fun ringingShowsAnswerAndDecline() {
        val state = CallOnlyCallState.from(Call.STATE_RINGING)
        assertTrue(state.isIncoming)
        assertTrue(state.canAnswer)
        assertTrue(state.canDecline)
        assertFalse(state.isAnswerPending)
        assertEquals(Call.STATE_RINGING, state.telecomState)
    }

    @Test
    fun pendingAnswerShowsConnectingInsteadOfRinging() {
        val state = CallOnlyCallState.from(Call.STATE_RINGING, answerPending = true)
        assertFalse("The user must not be able to answer twice", state.canAnswer)
        assertFalse("A pending answer is no longer an incoming call", state.isIncoming)
        assertTrue(state.isAnswerPending)
        assertEquals(Call.STATE_CONNECTING, state.telecomState)
    }

    @Test
    fun connectingHasNoAnswerAction() {
        val state = CallOnlyCallState.from(Call.STATE_CONNECTING)
        assertFalse(state.isIncoming)
        assertFalse(state.canAnswer)
        assertFalse(state.canDecline)
        assertEquals(Call.STATE_CONNECTING, state.telecomState)
    }

    @Test
    fun activeHasNoAnswerAction() {
        val state = CallOnlyCallState.from(Call.STATE_ACTIVE)
        assertFalse(state.isIncoming)
        assertFalse(state.canAnswer)
        assertFalse(state.canDecline)
        assertEquals(Call.STATE_ACTIVE, state.telecomState)
    }

    @Test
    fun holdingHasNoAnswerAction() {
        val state = CallOnlyCallState.from(Call.STATE_HOLDING)
        assertFalse(state.isIncoming)
        assertFalse(state.canAnswer)
        assertFalse(state.canDecline)
        assertEquals(Call.STATE_HOLDING, state.telecomState)
    }

    @Test
    fun disconnectedHasNoActions() {
        val state = CallOnlyCallState.from(Call.STATE_DISCONNECTED)
        assertFalse(state.isIncoming)
        assertFalse(state.canAnswer)
        assertFalse(state.canDecline)
        assertEquals(Call.STATE_DISCONNECTED, state.telecomState)
    }
}
