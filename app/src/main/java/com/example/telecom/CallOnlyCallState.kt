/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import android.telecom.Call

/**
 * The state the call-only screen should render, derived only from the exact Telecom call state.
 *
 * `android.telecom.Call.Callback` updates the same [Call] object in place, so the screen must never
 * keep a remembered `isIncoming` boolean. This pure mapping is deliberately small and exhaustive
 * so the ringing -> connecting -> active transition (and held/disconnected cases) can be tested.
 */
data class CallOnlyCallUiState(
    val telecomState: Int,
    val isIncoming: Boolean,
    val canAnswer: Boolean,
    val canDecline: Boolean,
    val isAnswerPending: Boolean
)

object CallOnlyCallState {

    /**
     * @param telecomState the live state of the exact call displayed by the call-only activity.
     * @param answerPending true only after an answer was dispatched for this exact call and before
     *        Telecom has published a different state. It produces an honest "Connecting" state
     *        without claiming the call is active.
     */
    fun from(telecomState: Int, answerPending: Boolean = false): CallOnlyCallUiState {
        val pending = answerPending && telecomState == Call.STATE_RINGING
        val effectiveState = if (pending) Call.STATE_CONNECTING else telecomState
        return when (effectiveState) {
            Call.STATE_RINGING -> CallOnlyCallUiState(
                telecomState = effectiveState,
                isIncoming = true,
                canAnswer = true,
                canDecline = true,
                isAnswerPending = false
            )

            Call.STATE_CONNECTING,
            Call.STATE_DIALING -> CallOnlyCallUiState(
                telecomState = effectiveState,
                isIncoming = false,
                canAnswer = false,
                canDecline = false,
                isAnswerPending = pending
            )

            Call.STATE_ACTIVE -> CallOnlyCallUiState(
                telecomState = effectiveState,
                isIncoming = false,
                canAnswer = false,
                canDecline = false,
                isAnswerPending = false
            )

            Call.STATE_HOLDING -> CallOnlyCallUiState(
                telecomState = effectiveState,
                isIncoming = false,
                canAnswer = false,
                canDecline = false,
                isAnswerPending = false
            )

            Call.STATE_DISCONNECTED -> CallOnlyCallUiState(
                telecomState = effectiveState,
                isIncoming = false,
                canAnswer = false,
                canDecline = false,
                isAnswerPending = false
            )

            else -> CallOnlyCallUiState(
                telecomState = effectiveState,
                isIncoming = false,
                canAnswer = false,
                canDecline = false,
                isAnswerPending = pending
            )
        }
    }
}
