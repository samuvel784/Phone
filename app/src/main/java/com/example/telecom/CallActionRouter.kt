/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import android.telecom.Call

/** A Telecom action that may be requested by a notification or an activity intent. */
enum class CallAction {
    ANSWER,
    DECLINE,
    HANG_UP
}

/**
 * Resolves the single call a Telecom action is allowed to touch.
 *
 * A requested id is authoritative: if it is stale or no longer eligible, the action is ignored.
 * A legacy id-less intent is accepted only when exactly one eligible call exists. This prevents
 * a decline/answer/hang-up written for one call from being applied to an unrelated live call.
 */
object CallActionRouter {
    const val NO_CALL_ID = -1L

    fun resolve(
        action: CallAction,
        requestedId: Long,
        calls: List<CallSnapshot>
    ): Long? {
        if (requestedId != NO_CALL_ID) {
            return calls.firstOrNull { it.id == requestedId && isEligible(action, it.state) }?.id
        }
        return calls.filter { isEligible(action, it.state) }.singleOrNull()?.id
    }

    private fun isEligible(action: CallAction, state: Int): Boolean = when (action) {
        CallAction.ANSWER, CallAction.DECLINE -> state == Call.STATE_RINGING
        CallAction.HANG_UP -> state != Call.STATE_DISCONNECTED
    }
}

/**
 * Which incoming-call surfaces should be presented for the current Telecom snapshot.
 *
 * A ringing call is either the primary incoming call (full ringtone and incoming notification)
 * or call waiting (tone plus waiting notification). Ending/declining one call must not stop the
 * ringtone or cancel notifications that still belong to another live ringing call.
 */
data class IncomingPresentation(
    val primaryRingingId: Long?,
    val waitingRingingId: Long?
) {
    val hasRingingCall: Boolean
        get() = primaryRingingId != null || waitingRingingId != null
}

object IncomingPresentationPlanner {
    fun plan(
        calls: List<CallSnapshot>,
        preferredWaitingId: Long? = null
    ): IncomingPresentation {
        val ringing = calls.filter { it.state == Call.STATE_RINGING }
        if (ringing.isEmpty()) return IncomingPresentation(null, null)

        val hasLiveNonRingingCall = calls.any {
            it.state == Call.STATE_ACTIVE ||
                it.state == Call.STATE_HOLDING ||
                it.state == Call.STATE_DIALING ||
                it.state == Call.STATE_CONNECTING
        }
        if (!hasLiveNonRingingCall) {
            return IncomingPresentation(
                primaryRingingId = ringing.first().id,
                waitingRingingId = null
            )
        }

        val waiting = ringing.firstOrNull { it.id == preferredWaitingId } ?: ringing.last()
        return IncomingPresentation(
            primaryRingingId = null,
            waitingRingingId = waiting.id
        )
    }
}
