/*
 * Copyright (C) 2026 MovStore
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.ui.components

import android.os.Build
import android.telecom.Call
import android.telecom.Connection
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.CallManager
import com.example.ContactCache
import com.example.R
import com.example.safeState
import com.example.model.Contact
import com.example.ui.theme.AppShapes
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun CallDurationText(
    activeStartTimestamp: Long,
    callState: Int,
    fallbackFormattedTime: String = "",
    isFake: Boolean = false,
    fakeState: String = "RINGING",
    fakeActiveStartTimestamp: Long = 0L
) {
    if (callState != Call.STATE_ACTIVE && callState != Call.STATE_HOLDING) return

    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(callState, isFake, fakeState) {
        val isActive = if (isFake) fakeState == "ACTIVE" else callState == Call.STATE_ACTIVE
        if (isActive) {
            while (true) {
                delay(1000.milliseconds)
                tick++
            }
        }
    }

    val durationSeconds = remember(tick, activeStartTimestamp, callState, isFake, fakeState, fakeActiveStartTimestamp) {
        if (isFake) {
            if (fakeState == "ACTIVE" && fakeActiveStartTimestamp > 0L) {
                ((System.currentTimeMillis() - fakeActiveStartTimestamp) / 1000).coerceAtLeast(0L).toInt()
            } else 0
        } else {
            if (callState == Call.STATE_ACTIVE) {
                val start = if (activeStartTimestamp > 0L) activeStartTimestamp else System.currentTimeMillis()
                ((System.currentTimeMillis() - start) / 1000).coerceAtLeast(0L).toInt()
            } else 0
        }
    }

    val formatted = if (fallbackFormattedTime.isNotBlank() && durationSeconds == 0) {
        fallbackFormattedTime
    } else {
        val mins = durationSeconds / 60
        val secs = durationSeconds % 60
        "%02d:%02d".format(mins, secs)
    }

    Text(
        text = formatted,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun InCallHeader(
    isOnHold: Boolean,
    callState: Int,
    participants: List<Pair<String, String>>,
    preferredSim: String,
    contactName: String,
    contactNumber: String,
    formattedTime: String = "",
    contacts: List<Contact>,
    /**
     * The other call when two calls exist (a second outgoing call being placed, the call that was
     * auto-held when it connected, or the participant set of a conference). Rendered as a status
     * card; Swap/Merge themselves live in the control grid and only appear when Telecom supports
     * them.
     */
    secondaryCall: Call? = null,
    secondaryName: String = "",
    secondaryNumber: String = "",
    onEndSecondary: (() -> Unit)? = null,
    isConference: Boolean = false,
    isRecording: Boolean = false,
    activeStartTimestamp: Long = 0L,
    isFake: Boolean = false,
    fakeState: String = "RINGING",
    fakeActiveStartTimestamp: Long = 0L,
    isSpam: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 8.dp)
    ) {
        val simDisplay = if (preferredSim.equals("Ask", ignoreCase = true)) {
            stringResource(R.string.sim_ask)
        } else {
            preferredSim
        }

        val displayHeader = when {
            callState == Call.STATE_DISCONNECTED -> stringResource(R.string.call_status_ended)
            isOnHold || callState == Call.STATE_HOLDING -> stringResource(R.string.call_status_hold)
            callState == Call.STATE_DIALING -> stringResource(R.string.call_status_dialing)
            callState == Call.STATE_RINGING -> stringResource(R.string.call_status_ringing)
            callState == Call.STATE_CONNECTING -> stringResource(R.string.call_status_connecting)
            isConference || participants.size > 1 -> "${stringResource(R.string.call_status_conference)} • $simDisplay"
            else -> "${stringResource(R.string.call_status_ongoing)} • $simDisplay"
        }

        if (isConference || participants.size > 1) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 2.dp,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("👥", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.call_status_conference),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Text(
            text = displayHeader,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        val displayName = if (isConference || participants.size > 1) {
            if (participants.size >= 2) {
                "${participants[0].first.ifEmpty { participants[0].second }} & ${participants[1].first.ifEmpty { participants[1].second }}"
            } else if (participants.size == 1 && participants[0].first.isNotEmpty() && participants[0].first != participants[0].second) {
                "${participants[0].first} (${stringResource(R.string.call_status_conference)})"
            } else if (contactName.isNotEmpty() && contactName != contactNumber) {
                "$contactName (${stringResource(R.string.call_status_conference)})"
            } else {
                stringResource(R.string.call_status_conference)
            }
        } else {
            val rawName = contactName.ifEmpty { contactNumber }
            if (rawName == "Unknown") stringResource(R.string.unknown) else rawName
        }

        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        val displaySubtitle = if (isConference || participants.size > 1) {
            if (participants.size > 2) {
                participants.joinToString(", ") { it.first.ifEmpty { it.second } }
            } else if (participants.size == 2) {
                "${participants[0].second} • ${participants[1].second}"
            } else if (participants.size == 1 && participants[0].second.isNotEmpty()) {
                participants[0].second
            } else if (contactNumber.isNotEmpty()) {
                contactNumber
            } else {
                stringResource(R.string.call_status_conference)
            }
        } else {
            if (contactName.isNotEmpty() && contactNumber.isNotEmpty() && contactName != contactNumber) contactNumber else ""
        }

        if (displaySubtitle.isNotEmpty()) {
            Text(
                text = displaySubtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // STIR/SHAKEN Anti-Spoofing Detection (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && callState == Call.STATE_RINGING) {
            val activeCallObj = CallManager.currentCall.value
            val verificationStatus = activeCallObj?.details?.callerNumberVerificationStatus
            if (verificationStatus == Connection.VERIFICATION_STATUS_FAILED) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.in_call_spoof_warning),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (isSpam) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Red.copy(alpha = 0.25f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "⚠️ SPAM CALLER DETECTED",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isRecording) {
                RecordingBadge()
            }

            CallDurationText(
                activeStartTimestamp = activeStartTimestamp,
                callState = callState,
                fallbackFormattedTime = formattedTime,
                isFake = isFake,
                fakeState = fakeState,
                fakeActiveStartTimestamp = fakeActiveStartTimestamp
            )
        }

        if (secondaryCall != null) {
            Spacer(modifier = Modifier.height(16.dp))
            val otherNumber = secondaryNumber.ifBlank {
                secondaryCall.details?.handle?.schemeSpecificPart ?: ""
            }
            val otherName = secondaryName.ifBlank {
                ContactCache.getContact(otherNumber)?.name
                    ?: contacts.find { it.number == otherNumber }?.name
                    ?: otherNumber
            }
            val otherState = secondaryCall.safeState
            val statusText = when (otherState) {
                Call.STATE_DIALING -> stringResource(R.string.second_call_dialing)
                Call.STATE_CONNECTING -> stringResource(R.string.second_call_connecting)
                Call.STATE_RINGING -> stringResource(R.string.second_call_ringing)
                Call.STATE_HOLDING -> stringResource(R.string.second_call_held)
                Call.STATE_ACTIVE -> stringResource(R.string.second_call_active)
                else -> stringResource(R.string.second_call_failed)
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text(
                            if (otherState == Call.STATE_HOLDING) "⏸️" else "📞",
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = otherName.ifBlank { stringResource(R.string.unknown) },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (otherName != otherNumber && otherNumber.isNotEmpty()) {
                                Text(
                                    text = otherNumber,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    if (onEndSecondary != null) {
                        IconButton(
                            onClick = onEndSecondary,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = stringResource(R.string.btn_end_call),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingBadge() {
    val infiniteTransition = rememberInfiniteTransition(label = "header_rec_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "header_rec_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "header_rec_alpha"
    )

    Surface(
        shape = AppShapes.Chip,
        color = Color(0xFFB71C1C).copy(alpha = 0.15f),
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .scale(pulseScale)
                        .clip(AppShapes.Small)
                        .background(Color(0xFFE53935).copy(alpha = pulseAlpha))
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(AppShapes.Small)
                        .background(Color(0xFFE53935))
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "REC",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE53935),
                fontSize = 11.sp
            )
        }
    }
}
