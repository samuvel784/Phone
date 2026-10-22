/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import android.telecom.Call

/**
 * An immutable snapshot of one Telecom call, holding only the values the call-state machine needs.
 *
 * Snapshots exist so the decision logic (which buttons may be shown, whether a swap/merge is
 * legal) is a pure function that can be exhaustively unit tested without real [Call] objects,
 * which cannot be constructed outside of Telecom.
 */
data class CallSnapshot(
    val id: Long,
    val state: Int,
    val capabilities: Int,
    val properties: Int,
    /** Ids of the calls Telecom reports as conferenceable with this one. */
    val conferenceableIds: List<Long> = emptyList(),
    /** True when this call is a conference host (it has children). */
    val isConferenceHost: Boolean = false,
    /** True when this call is a participant of a conference (it has a parent). */
    val isConferenceParticipant: Boolean = false
) {
    val isDisconnected: Boolean get() = state == Call.STATE_DISCONNECTED
    val isActive: Boolean get() = state == Call.STATE_ACTIVE
    val isHolding: Boolean get() = state == Call.STATE_HOLDING
    val isRinging: Boolean get() = state == Call.STATE_RINGING
    val isDialing: Boolean get() = state == Call.STATE_DIALING || state == Call.STATE_CONNECTING

    /** A call the user is actually talking on / waiting to connect. */
    val isLive: Boolean get() = !isDisconnected

    val isConference: Boolean
        get() = isConferenceHost ||
                isConferenceParticipant ||
                (properties and Call.Details.PROPERTY_CONFERENCE) != 0 ||
                (properties and Call.Details.PROPERTY_GENERIC_CONFERENCE) != 0

    /** Call can be held right now. */
    val canBeHeld: Boolean
        get() = has(Call.Details.CAPABILITY_HOLD) || has(Call.Details.CAPABILITY_SUPPORT_HOLD)

    /** Call supports being merged into (or split out of) a conference. */
    val supportsMergeConference: Boolean get() = has(Call.Details.CAPABILITY_MERGE_CONFERENCE)

    val supportsManageConference: Boolean get() = has(Call.Details.CAPABILITY_MANAGE_CONFERENCE)

    private fun has(capability: Int): Boolean = (capabilities and capability) != 0
}

/** High level shape of the current Telecom call list. */
enum class MultiCallPhase {
    /** No live calls. */
    IDLE,

    /** Exactly one call, and it is connected. */
    SINGLE_ACTIVE,

    /** Exactly one call, and it is ringing (incoming). */
    SINGLE_INCOMING,

    /** Exactly one call, and it is dialing/connecting (outgoing). */
    SINGLE_OUTGOING,

    /** Exactly one call, and the user put it on hold. */
    SINGLE_HELD,

    /** A call is live and a second call is being dialed, connecting or ringing. */
    ADD_CALL_IN_PROGRESS,

    /** Two calls exist: one active plus one held. Switching is available. */
    TWO_CALLS,

    /** A conference call exists (possibly with participants). */
    CONFERENCE
}

/**
 * The complete set of actions the in-call UI may offer, derived **only** from the current Telecom
 * call list, call states, capabilities and conferenceable-call information.
 *
 * Nothing here is derived from "did the user tap Add Call" or "did the user pick a contact".
 */
data class MultiCallUiState(
    val phase: MultiCallPhase = MultiCallPhase.IDLE,
    /** The call the in-call screen is showing. */
    val primary: CallSnapshot? = null,
    /** The other call, when two calls exist (e.g. the second call while adding a caller). */
    val secondary: CallSnapshot? = null,
    /** The conference host, when a conference exists. */
    val conference: CallSnapshot? = null,
    /** Number of live (non disconnected) calls. */
    val liveCallCount: Int = 0,
    val conferenceParticipantCount: Int = 0,

    /** A second call is currently dialing, connecting or ringing. */
    val hasSecondCallInProgress: Boolean = false,
    /** An incoming call is waiting while another call is live. */
    val hasWaitingCall: Boolean = false,

    // ---- capabilities of the primary call, exposed for button enablement ----
    val canHoldPrimary: Boolean = false,
    val canUnholdPrimary: Boolean = false,

    // ---- multi call actions ----
    val canAddCall: Boolean = false,
    val canSwap: Boolean = false,
    val canMerge: Boolean = false,
    val canManageConference: Boolean = false,
    /** True when more than one call exists, so per-call end actions are appropriate. */
    val supportsPerCallEnd: Boolean = false
) {
    val isConference: Boolean get() = phase == MultiCallPhase.CONFERENCE
}

/**
 * Telecom-driven multi-call state machine.
 *
 * The call list, call states, capabilities and conferenceable-call information reported by
 * `android.telecom` are the single source of truth. The rules below deliberately mirror what
 * Google's Phone app / AOSP Dialer expose so that Nova Dialer never offers an action the platform
 * or the carrier cannot perform.
 */
object MultiCallStateMachine {

    /**
     * @param calls every call Telecom knows about, including disconnected ones (they are ignored).
     * @param preferredPrimaryId the call the in-call screen currently shows, if any.
     * @param waitingCallId a ringing call that arrived while another call was live, if any.
     */
    fun evaluate(
        calls: List<CallSnapshot>,
        preferredPrimaryId: Long? = null,
        waitingCallId: Long? = null
    ): MultiCallUiState {
        val live = calls.filter { it.isLive }
        if (live.isEmpty()) return MultiCallUiState()

        val conference = live.firstOrNull { it.isConferenceHost } ?: live.firstOrNull { it.isConference }
        val active = live.filter { it.isActive && !it.isConference }
        val held = live.filter { it.isHolding && !it.isConference }
        val dialing = live.filter { it.isDialing }
        val ringing = live.filter { it.isRinging }

        val primary = pickPrimary(live, conference, preferredPrimaryId)
        // Once Telecom reports a real conference, its children/participants are not independent
        // held calls. Keep a genuinely separate live call as the secondary, but never expose a
        // conference participant as a "pause/held" second line.
        val secondary = if (conference != null) {
            live.firstOrNull {
                it.id != conference.id &&
                    !it.isConference &&
                    it.id != primary?.id
            }
        } else {
            pickSecondary(live, primary)
        }

        val liveCount = live.size
        val inProgressSecond = liveCount >= 2 && (dialing.isNotEmpty() || ringing.isNotEmpty())

        val canAddCall = conference == null &&
                liveCount == 1 &&
                primary != null &&
                (primary.isActive || primary.isHolding) &&
                ringing.isEmpty() &&
                dialing.isEmpty()

        val twoCleanCalls = conference == null && liveCount == 2 &&
                active.size == 1 && held.size == 1 &&
                !active[0].isConference && !held[0].isConference

        val canSwap = twoCleanCalls &&
                active[0].canBeHeld &&
                held[0].canBeHeld

        // Telecom's conferenceable-call report is authoritative for a two-call merge. A stack may
        // explicitly list the held call as conferenceable without setting the per-call
        // CAPABILITY_MERGE_CONFERENCE bit. Only fall back to the capability when neither call has
        // published a candidate list yet.
        val canMerge = twoCleanCalls && canMergePair(active[0], held[0])

        val phase = when {
            conference != null -> MultiCallPhase.CONFERENCE
            inProgressSecond -> MultiCallPhase.ADD_CALL_IN_PROGRESS
            liveCount >= 2 && active.isNotEmpty() && held.isNotEmpty() -> MultiCallPhase.TWO_CALLS
            liveCount >= 2 -> MultiCallPhase.TWO_CALLS
            primary == null -> MultiCallPhase.IDLE
            primary.isHolding -> MultiCallPhase.SINGLE_HELD
            primary.isActive -> MultiCallPhase.SINGLE_ACTIVE
            primary.isRinging -> MultiCallPhase.SINGLE_INCOMING
            else -> MultiCallPhase.SINGLE_OUTGOING
        }

        return MultiCallUiState(
            phase = phase,
            primary = primary,
            secondary = secondary,
            conference = conference,
            liveCallCount = liveCount,
            conferenceParticipantCount = conference?.let { live.count { c -> c != conference } } ?: 0,
            hasSecondCallInProgress = inProgressSecond,
            hasWaitingCall = waitingCallId != null && ringing.any { it.id == waitingCallId },
            canHoldPrimary = primary != null && primary.isActive && primary.canBeHeld,
            canUnholdPrimary = primary != null && primary.isHolding && primary.canBeHeld,
            canAddCall = canAddCall,
            canSwap = canSwap,
            canMerge = canMerge,
            canManageConference = conference != null && conference.supportsManageConference,
            supportsPerCallEnd = liveCount >= 2
        )
    }

    /**
     * Returns whether [first] and [second] form a mergeable ordinary two-call pair.
     *
     * The pair must be exactly one established active call and one established held call. An
     * explicit Telecom conferenceable relationship is sufficient by itself. The merge capability
     * is only a fallback while neither call has published any conferenceable candidates: a
     * non-empty candidate list that excludes the other call is positive evidence that this
     * particular pair is not mergeable.
     */
    fun canMergePair(first: CallSnapshot, second: CallSnapshot): Boolean {
        if (first.id == second.id) return false
        if (first.isDisconnected || second.isDisconnected) return false
        if (first.isConference || second.isConference) return false

        val oneActiveAndOneHeld =
            (first.isActive && second.isHolding) || (first.isHolding && second.isActive)
        if (!oneActiveAndOneHeld) return false

        val explicitlyConferenceable =
            first.conferenceableIds.contains(second.id) ||
                second.conferenceableIds.contains(first.id)
        if (explicitlyConferenceable) return true

        val candidatesPublished =
            first.conferenceableIds.isNotEmpty() || second.conferenceableIds.isNotEmpty()
        if (candidatesPublished) return false

        return first.supportsMergeConference || second.supportsMergeConference
    }

    /**
     * Foreground call selection, matching how Nova Dialer tracked calls before: a conference host,
     * then the connected call, then a call that is still connecting or ringing, then a held call.
     */
    private fun pickPrimary(
        live: List<CallSnapshot>,
        conference: CallSnapshot?,
        preferredPrimaryId: Long?
    ): CallSnapshot? {
        preferredPrimaryId?.let { id ->
            live.firstOrNull { it.id == id }?.let { return it }
        }
        return conference
            ?: live.firstOrNull { it.isActive }
            ?: live.firstOrNull { it.isDialing || it.isRinging }
            ?: live.firstOrNull { it.isHolding }
            ?: live.firstOrNull()
    }

    private fun pickSecondary(live: List<CallSnapshot>, primary: CallSnapshot?): CallSnapshot? {
        if (primary == null) return null
        // Prefer the call that is not the conference host so the second-call card shows a person.
        return live.firstOrNull { it.id != primary.id && !it.isConference }
            ?: live.firstOrNull { it.id != primary.id }
    }
}
