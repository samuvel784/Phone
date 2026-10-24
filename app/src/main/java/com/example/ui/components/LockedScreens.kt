/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.ui.components

import android.telecom.Call
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.CallManager
import com.example.ContactCache
import com.example.R
import com.example.safeState
import com.example.ui.theme.AppShapes
import com.example.ui.theme.NovaTheme
import com.example.ui.theme.novaCanvas

/**
 * Full-screen lock gate. This is the only thing an unauthenticated user sees when there is no
 * live call, even if MainActivity is launched by an exported DIAL/VIEW intent.
 */
@Composable
fun AppLockedScreen(onUnlock: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize().novaCanvas(),
        color = Color.Transparent
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(32.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(104.dp)
                        .clip(AppShapes.Avatar)
                        .background(NovaTheme.colors.brand)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(R.string.app_locked_title),
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.size(24.dp))
                Text(
                    text = stringResource(R.string.app_locked_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    text = stringResource(R.string.app_locked_message),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.size(36.dp))
                Button(
                    onClick = onUnlock,
                    modifier = Modifier.fillMaxWidth(0.7f).height(52.dp),
                    shape = AppShapes.Keypad
                ) {
                    Text(
                        text = stringResource(R.string.btn_unlock_app),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

/**
 * Deliberately minimal call surface for an app-locked, unauthenticated user.
 *
 * It exposes only the caller identity needed to decide whether to answer, plus answer/decline or
 * mute/speaker/end. It never renders the dialer, recents, contacts, settings, notes, recordings or
 * quick responses. Every action is bound to the call id resolved from authoritative Telecom state.
 */
@Composable
fun RestrictedCallSurface(
    onUnlock: () -> Unit,
    onExit: () -> Unit,
    fallbackName: String = "",
    fallbackNumber: String = ""
) {
    BackHandler { onExit() }

    val calls by CallManager.calls.collectAsStateWithLifecycle()
    val currentCall by CallManager.currentCall.collectAsStateWithLifecycle()
    val audioState by CallManager.routeState.collectAsStateWithLifecycle()

    val ringing = calls.firstOrNull { it.safeState == Call.STATE_RINGING }
    val liveCall = ringing
        ?: currentCall?.takeIf { it.safeState != Call.STATE_DISCONNECTED }
        ?: calls.firstOrNull { it.safeState != Call.STATE_DISCONNECTED }
    val state = liveCall?.safeState ?: Call.STATE_DISCONNECTED
    val callId = liveCall?.let { CallManager.callId(it) } ?: MyCallIds.NONE

    // Resolve identity from the selected call, not from CallManager's global caller fields:
    // during call waiting the global fields can describe a different call than the one whose
    // answer/decline buttons are on screen.
    val selectedNumber = liveCall?.details?.handle?.schemeSpecificPart.orEmpty()
    val number = if (liveCall != null) selectedNumber else fallbackNumber
    val directName = liveCall?.details?.callerDisplayName.orEmpty()
    val cachedName = if (selectedNumber.isNotBlank() && liveCall != null) {
        ContactCache.getContact(selectedNumber)?.name.orEmpty()
    } else {
        ""
    }
    val name = if (liveCall != null) {
        cachedName.ifBlank { directName }.ifBlank { number }
    } else {
        fallbackName.ifBlank { fallbackNumber }
    }

    Surface(
        modifier = Modifier.fillMaxSize().novaCanvas(),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.size(16.dp))

            if (liveCall != null) {
                Text(
                    text = name.ifBlank { stringResource(R.string.unknown) },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                if (number.isNotBlank() && number != name) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = number,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    text = stringResource(
                        if (state == Call.STATE_RINGING) R.string.call_status_ringing
                        else R.string.call_status_ongoing
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.size(32.dp))

                if (state == Call.STATE_RINGING) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (callId != MyCallIds.NONE) {
                                    CallManager.rejectCallById(callId)
                                }
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(stringResource(R.string.btn_decline))
                        }
                        Button(
                            onClick = {
                                if (callId != MyCallIds.NONE) {
                                    CallManager.answerCallById(callId)
                                }
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(stringResource(R.string.btn_answer))
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { CallManager.setMuted(!audioState.isMuted) },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(stringResource(R.string.mute))
                        }
                        OutlinedButton(
                            onClick = { CallManager.toggleSpeaker() },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(stringResource(R.string.speaker))
                        }
                    }
                    Spacer(modifier = Modifier.size(16.dp))
                    Button(
                        onClick = {
                            if (callId != MyCallIds.NONE) {
                                CallManager.disconnectById(callId)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(stringResource(R.string.btn_end_call))
                    }
                }
            } else {
                Text(
                    text = name.ifBlank { stringResource(R.string.app_locked_title) },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                if (number.isNotBlank() && number != name) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = number,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    text = stringResource(R.string.app_locked_message),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.size(28.dp))
            TextButton(onClick = onUnlock) {
                Text(stringResource(R.string.btn_unlock_app))
            }
        }
    }
}

@Composable
fun DatabaseRecoveryRequiredScreen(
    errorMessage: String?,
    onResetApp: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize().novaCanvas(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Database Recovery Required",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorMessage.takeUnless { it.isNullOrBlank() }
                    ?: "Nova Dialer detected a database encryption key or file integrity issue. Your original database and recovery artifacts have been preserved safely on your device.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onResetApp,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Reset App & Start Fresh")
            }
        }
    }
}

private object MyCallIds {
    const val NONE = -1L
}
