/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.ui.components

import android.telecom.Call
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.CallManager
import com.example.ContactCache
import com.example.R
import com.example.safeState
import com.example.util.IncomingCallAccess

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
    val isIncoming = state == Call.STATE_RINGING

    ActiveCallScreen(
        contactName = displayName,
        contactNumber = number,
        preferredSim = "SIM $simSlot",
        quickResponses = emptyList(),
        onHangUp = { CallManager.disconnectById(callId) },
        onQuickDecline = { CallManager.rejectCallById(callId) },
        isIncoming = isIncoming,
        onAnswer = { CallManager.answerCallById(callId) },
        callState = state,
        recordingEnabled = false,
        autoTuneVolume = false,
        recordingChimeEnabled = false,
        alwaysRecordEnabled = false,
        callNotesEnabled = false,
        onMinimize = onBack,
        isPocketProtectionEnabled = true,
        callOnlyMode = true
    )
}

/**
 * A compact, dismissible explanation for an OS/notification setting that prevents the call UI
 * from appearing immediately. It never opens Settings by itself; the user must tap the button.
 */
@Composable
fun IncomingCallAccessNotice(
    access: IncomingCallAccess,
    onOpenFullScreenSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!access.hasActionableIssue) return

    val fullScreenIssue = access.needsFullScreenSettings
    val title = if (fullScreenIssue) {
        stringResource(R.string.settings_full_screen_intent_warning)
    } else {
        stringResource(R.string.incoming_call_alerts_limited)
    }
    val body = if (fullScreenIssue) {
        stringResource(R.string.settings_lockscreen_calling_sub)
    } else {
        stringResource(R.string.incoming_call_notification_settings_help)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("incoming_call_access_notice"),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(
                onClick = if (fullScreenIssue) onOpenFullScreenSettings else onOpenNotificationSettings
            ) {
                Text(stringResource(R.string.btn_configure), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
