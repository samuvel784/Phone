/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.ui.components

import android.telecom.Call
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.CallManager
import com.example.ContactCache
import com.example.safeState

/**
 * The single call-only surface used by [com.example.IncomingCallActivity].
 *
 * It reuses the existing in-call dashboard (rather than maintaining a second set of call
 * controls), but switches the dashboard into restricted mode: private app destinations such as
 * contact picking, quick responses, notes and recording are not offered. Actions are bound to the
 * exact call id supplied by the notification/full-screen intent.
 */
@Composable
fun CallOnlyCallSurface(
    callId: Long,
    fallbackName: String = "",
    fallbackNumber: String = "",
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val calls by CallManager.calls.collectAsStateWithLifecycle()
    val simSlot by CallManager.currentSimSlot.collectAsStateWithLifecycle()
    val pendingAnswerCallId by CallManager.pendingAnswerCallId.collectAsStateWithLifecycle()

    val call = remember(calls, callId) {
        calls.firstOrNull {
            CallManager.callId(it) == callId && it.safeState != Call.STATE_DISCONNECTED
        }
    }

    if (call == null) {
        // The activity owns the stale-launch grace period and will finish this screen. Never
        // substitute an unrelated call here: a stale notification must not open a false call UI.
        Box(modifier = modifier.fillMaxSize())
        return
    }

    val number = call.details?.handle?.schemeSpecificPart
        .orEmpty()
        .ifBlank { fallbackNumber }
    val directName = call.details?.callerDisplayName.orEmpty()
    val cachedName = if (number.isNotBlank()) ContactCache.getContact(number)?.name else null
    val displayName = cachedName
        ?.takeIf { it.isNotBlank() }
        ?: directName.takeIf { it.isNotBlank() }
        ?: fallbackName.takeIf { it.isNotBlank() }
        ?: number

    val state = call.safeState
    val presentation = remember(state, pendingAnswerCallId, callId) {
        com.example.telecom.CallOnlyCallState.from(
            telecomState = state,
            answerPending = pendingAnswerCallId == callId
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        ActiveCallScreen(
            contactName = displayName,
            contactNumber = number,
            preferredSim = "SIM $simSlot",
            quickResponses = emptyList(),
            onHangUp = { CallManager.disconnectById(callId) },
            onQuickDecline = { CallManager.rejectCallById(callId) },
            isIncoming = presentation.isIncoming,
            onAnswer = { CallManager.answerCallById(callId) },
            callState = presentation.telecomState,
            callNotesEnabled = false,
            onMinimize = onBack,
            isPocketProtectionEnabled = true,
            callOnlyMode = true
        )
    }
}
