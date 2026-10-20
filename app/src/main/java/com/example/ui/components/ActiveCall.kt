/*
 * Copyright (C) 2026 MovStore
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.telecom.Call
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.CallManager
import com.example.R
import com.example.safeState
import com.example.model.Contact
import com.example.telecom.AudioRoute
import com.example.ui.theme.LocalAmoledMode
import com.example.util.CallAudioHelper
import com.example.util.CallAudioRecorder
import com.example.util.CallGestureSensorManager
import com.example.util.RecordingDisclosure
import com.example.util.RecordingFeedbackHelper
import com.example.util.RichHapticEngine

@Composable
fun ActiveCallScreen(
    contactName: String,
    contactNumber: String,
    preferredSim: String,
    quickResponses: List<String>,
    onHangUp: () -> Unit,
    onQuickDecline: (String) -> Unit,
    isIncoming: Boolean = false,
    contacts: List<Contact> = emptyList(),
    onAnswer: () -> Unit = {},
    callState: Int = Call.STATE_DISCONNECTED,
    recordingEnabled: Boolean = false,
    autoTuneVolume: Boolean = true,
    recordingChimeEnabled: Boolean = false,
    alwaysRecordEnabled: Boolean = false,
    callNotesEnabled: Boolean = true,
    onSaveNote: (String) -> Unit = {},
    onMinimize: (() -> Unit)? = null,
    avatarShapeType: String = "circular",
    isPocketProtectionEnabled: Boolean = false,
    isFake: Boolean = false,
    fakeState: String = "RINGING",
    onFakeAnswer: () -> Unit = {},
    onFakeHangUp: () -> Unit = {},
    isSpam: Boolean = false
) {
    BackHandler(enabled = true) {
        onMinimize?.invoke()
    }

    val context = LocalContext.current
    val activeStartTimestamp by CallManager.activeStartTimestamp.collectAsStateWithLifecycle()

    val audioState by CallManager.audioState.collectAsStateWithLifecycle()
    val waitingCall by CallManager.waitingCall.collectAsStateWithLifecycle()
    val allCalls by CallManager.calls.collectAsStateWithLifecycle()
    val currentCall by CallManager.currentCall.collectAsStateWithLifecycle()
    val recorderIsActive by CallAudioRecorder.isRecording.collectAsStateWithLifecycle()

    // Single source of truth for call audio: what Telecom reports as available and selected —
    // never the last button the user tapped.
    val routeState by CallManager.routeState.collectAsStateWithLifecycle()
    // Single source of truth for every multi-call action (swap/merge/add call).
    val multiCallState by CallManager.multiCallState.collectAsStateWithLifecycle()

    var isAddCallDialogOpen by remember { mutableStateOf(false) }
    var fakeActiveStartTimestamp by remember { mutableLongStateOf(0L) }

    val isMuted = routeState.isMuted
    val isSpeakerOn = routeState.isSpeakerActive
    val isBluetoothOn = routeState.isBluetoothActive
    val isBluetoothAvailable = routeState.hasBluetooth
    val isHeadsetOn = routeState.isWiredHeadsetActive

    val isRecording = recorderIsActive

    // The in-call UI is on screen: record that for the incoming-call timing trace.
    LaunchedEffect(Unit) {
        com.example.telecom.CallTimingLogger.markActive(com.example.telecom.CallTimingLogger.Event.FIRST_FRAME)
    }

    // FIXED: Sync hold status with real carrier network state
    val currentCallState = if (isFake) {
        if (fakeState == "ACTIVE") Call.STATE_ACTIVE else Call.STATE_RINGING
    } else {
        callState
    }

    val isOnHold = currentCallState == Call.STATE_HOLDING

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Microphone permission granted. Tap Record to start.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Microphone permission required for call recording", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Leaving the screen (minimise, tab switch, rotation) must NEVER stop recording.
            // The recording session belongs to the call/service; only call-end, an explicit
            // stop, or service teardown finalise it. Restore audio only when nothing is recording.
            if (!CallAudioRecorder.isRecording.value) {
                CallAudioHelper.restoreAudioState(context, CallManager.inCallService)
            }
        }
    }

    LaunchedEffect(isFake, fakeState) {
        if (isFake && fakeState == "ACTIVE") {
            fakeActiveStartTimestamp = System.currentTimeMillis()
            if (alwaysRecordEnabled && !CallAudioRecorder.isRecording.value) {
                RecordingFeedbackHelper.triggerRecordingStartFeedback(context, recordingChimeEnabled)
                CallAudioRecorder.startRecording(context, contactNumber)
            }
        }
    }

    val isAmoled = LocalAmoledMode.current
    val surfaceColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)

    // The other live call, when two calls exist, resolved from the Telecom-driven multi-call state
    // so the status card and its per-call End action always target a real, current call.
    val secondaryCall = remember(multiCallState.secondary, allCalls) {
        multiCallState.secondary?.let { CallManager.callById(it.id) }
    }

    var isQuickDeclineMenuOpen by remember { mutableStateOf(false) }
    var isInCallDialpadOpen by remember { mutableStateOf(false) }
    // Shown once, before the first manual recording, until the user accepts the notice.
    var showRecordingDisclosure by remember { mutableStateOf(false) }

    var fakeParticipants by remember(contactName, contactNumber) {
        mutableStateOf(listOf(Pair(contactName, contactNumber)))
    }

    val participants = remember(isFake, currentCall, allCalls, contactName, contactNumber, contacts, fakeParticipants) {
        if (isFake) {
            fakeParticipants
        } else {
            val activeList = allCalls.filter { it.safeState != Call.STATE_DISCONNECTED }
            val conferenceCall = activeList.find {
                it.children.isNotEmpty() || it.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true
            }
            if (conferenceCall != null && conferenceCall.children.isNotEmpty()) {
                conferenceCall.children.map { child ->
                    val num = child.details?.handle?.schemeSpecificPart ?: ""
                    val name = contacts.find { it.number == num }?.name ?: num
                    Pair(name, num)
                }
            } else {
                val num = currentCall?.details?.handle?.schemeSpecificPart ?: contactNumber
                val name = contacts.find { it.number == num }?.name ?: contactName.ifEmpty { num }
                listOf(Pair(name, num))
            }
        }
    }

    val isConferenceActiveFlow by CallManager.isConferenceActive.collectAsStateWithLifecycle()
    val isConference = remember(isFake, fakeParticipants, currentCall, allCalls, isConferenceActiveFlow) {
        if (isFake) {
            fakeParticipants.size > 1
        } else {
            isConferenceActiveFlow || CallManager.isConference(currentCall) || allCalls.any {
                it.safeState != Call.STATE_DISCONNECTED && CallManager.isConference(it)
            }
        }
    }

    var isNear by remember { mutableStateOf(false) }
    val powerManager = remember(context) { context.getSystemService(Context.POWER_SERVICE) as? PowerManager }
    val isProximitySupported = remember(powerManager) {
        powerManager?.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) == true
    }

    // Proximity screen-off only applies to an earpiece-style route. The *reported* route decides
    // this, never a button the user tapped (which may have been refused by Telecom).
    val isEarpieceActive = routeState.isEarpieceActive || routeState.currentRoute == AudioRoute.UNKNOWN

    val shouldActivateProximity = isEarpieceActive && !isSpeakerOn && !isBluetoothOn && !isHeadsetOn &&
            (currentCallState in listOf(Call.STATE_ACTIVE, Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_RINGING))

    DisposableEffect(shouldActivateProximity, powerManager) {
        var wakeLock: PowerManager.WakeLock? = null
        if (shouldActivateProximity && isProximitySupported && powerManager != null) {
            try {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                    "Nova Dialer:InCallProximityWakeLock"
                )
                wakeLock.acquire(12 * 60 * 60 * 1000L)
            } catch (_: Exception) {}
        }
        onDispose {
            try {
                if (wakeLock?.isHeld == true) wakeLock.release()
            } catch (_: Exception) {}
        }
    }

    DisposableEffect(Unit) {
        val gestureManager = CallGestureSensorManager(
            context = context,
            onProximityChanged = { near -> isNear = near },
            onFlipFaceDown = {
                if (callState == Call.STATE_RINGING) {
                    CallManager.silenceRinger(context)
                }
            }
        )
        gestureManager.startListening()
        onDispose { gestureManager.stopListening() }
    }

    // ---------------------------------------------------------------------------------------
    //  Modern backdrop: gradient tint that shifts with call state + soft radial glow + pulse
    // ---------------------------------------------------------------------------------------
    val callAccent by animateColorAsState(
        targetValue = when {
            isOnHold || currentCallState == Call.STATE_HOLDING -> Color(0xFFF59E0B)
            currentCallState == Call.STATE_RINGING -> Color(0xFF7C3AED)
            currentCallState == Call.STATE_DIALING ||
                    currentCallState == Call.STATE_CONNECTING -> Color(0xFF2563EB)
            currentCallState == Call.STATE_ACTIVE -> Color(0xFF22C55E)
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "callAccent"
    )

    val backdrop by remember(isAmoled) {
        derivedStateOf {
            if (isAmoled) {
                Brush.verticalGradient(
                    listOf(Color(0xFF0A0A0A), Color(0xFF000000))
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        surfaceColor,
                        surfaceColor.copy(alpha = 0.96f)
                    )
                )
            }
        }
    }

    // The screen "breathes" only while the call is ringing or connecting. During a normal conversation the glow stays
    // still, so there is no animation running every frame (smoother, and much kinder to the battery).
    val isPulsing = currentCallState == Call.STATE_RINGING ||
        currentCallState == Call.STATE_DIALING ||
        currentCallState == Call.STATE_CONNECTING
    val pulseState: State<Float> = if (isPulsing) {
        val infinite = rememberInfiniteTransition(label = "ambientPulse")
        infinite.animateFloat(
            initialValue = 0.55f,
            targetValue = if (currentCallState == Call.STATE_RINGING) 1f else 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = if (currentCallState == Call.STATE_RINGING) 1200 else 2200,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseValue"
        )
    } else {
        remember { mutableFloatStateOf(0.7f) }
    }
    val pulse by pulseState

    // Subtle entry animation for the whole shell.
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val shellAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(380, easing = FastOutSlowInEasing),
        label = "shellAlpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent
        ) {
            Box(modifier = Modifier
                .fillMaxSize()
                .background(backdrop)
            ) {
                // Ambient radial glow that sits behind the content, colored by call state.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.55f)
                        .align(Alignment.TopCenter)
                        .graphicsLayer { alpha = 0.34f * pulse }
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    callAccent.copy(alpha = 0.55f),
                                    callAccent.copy(alpha = 0.18f),
                                    Color.Transparent
                                ),
                                radius = 900f
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = shellAlpha }
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    InCallHeader(
                        isOnHold = isOnHold,
                        callState = currentCallState,
                        participants = participants,
                        preferredSim = preferredSim,
                        contactName = contactName,
                        contactNumber = contactNumber,
                        contacts = contacts,
                        secondaryCall = if (isFake) null else secondaryCall,
                        secondaryNumber = if (isFake) "" else secondaryCall?.details?.handle?.schemeSpecificPart ?: "",
                        onEndSecondary = if (isFake || secondaryCall == null) null else {
                            { CallManager.disconnect(secondaryCall) }
                        },
                        isConference = isConference,
                        isRecording = isRecording,
                        activeStartTimestamp = activeStartTimestamp,
                        isFake = isFake,
                        fakeState = fakeState,
                        fakeActiveStartTimestamp = fakeActiveStartTimestamp,
                        isSpam = isSpam
                    )

                    waitingCall?.let { call ->
                        InCallWaitingCallDialog(
                            waitingCall = call,
                            contacts = contacts,
                            avatarShapeType = avatarShapeType
                        )
                    }

                    if (isQuickDeclineMenuOpen) {
                        InCallQuickDeclineSheet(
                            contactNumber = contactNumber,
                            quickResponses = quickResponses,
                            onClose = { isQuickDeclineMenuOpen = false },
                            onQuickDecline = onQuickDecline
                        )
                    } else if (isInCallDialpadOpen) {
                        InCallKeypad(
                            onClose = { isInCallDialpadOpen = false },
                            avatarShapeType = avatarShapeType,
                            contacts = contacts
                        )
                    } else {
                        InCallAvatarDisplay(
                            participants = participants,
                            contactName = contactName,
                            contactNumber = contactNumber,
                            contacts = contacts,
                            avatarShapeType = avatarShapeType,
                            isConference = isConference,
                            isSpam = isSpam
                        )
                    }

                    if (isAddCallDialogOpen) {
                        InCallAddCallDialog(
                            contacts = contacts,
                            onDismiss = { isAddCallDialogOpen = false },
                            onAddCall = { finalName, number ->
                                if (isFake) {
                                    fakeParticipants = fakeParticipants + Pair(finalName, number)
                                } else if (!CallManager.placeCall(context, number)) {
                                    // The call manager refuses a second call when Telecom already has
                                    // two live calls or the same number was just dialled.
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.add_call_unavailable),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            avatarShapeType = avatarShapeType
                        )
                    }

                    // Starting a manual recording is gated by the one-time notice below.
                    val startManualRecording: () -> Unit = {
                        // "Hands-free" detection uses the route Telecom actually reports.
                        val isHeadset = isBluetoothOn || isHeadsetOn
                        if (CallManager.startCallRecording(context)) {
                            if (isHeadset) {
                                Toast.makeText(context, context.getString(R.string.headset_recording_warning), Toast.LENGTH_LONG).show()
                            } else if (autoTuneVolume) {
                                Toast.makeText(context, "⏺️ Recording started. Speaker tuned for balanced audio.", Toast.LENGTH_SHORT).show()
                            } else if (!isSpeakerOn) {
                                Toast.makeText(context, "⏺️ Recording started. Tip: Turn ON Speakerphone.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, context.getString(R.string.recording_started), Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, context.getString(R.string.recording_start_failed), Toast.LENGTH_LONG).show()
                        }
                    }

                    if (showRecordingDisclosure) {
                        AlertDialog(
                            onDismissRequest = { showRecordingDisclosure = false },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            title = { Text(stringResource(R.string.recording_disclosure_title)) },
                            text = { Text(stringResource(R.string.recording_disclosure_body)) },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        RecordingDisclosure.acknowledgeManual(context)
                                        showRecordingDisclosure = false
                                        startManualRecording()
                                    }
                                ) {
                                    Text(stringResource(R.string.recording_disclosure_accept))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showRecordingDisclosure = false }) {
                                    Text(stringResource(R.string.btn_cancel))
                                }
                            }
                        )
                    }

                    InCallControlGrid(
                        isInCallDialpadOpen = isInCallDialpadOpen,
                        onToggleDialpad = {
                            RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                            isInCallDialpadOpen = !isInCallDialpadOpen
                        },
                        isMuted = isMuted,
                        onToggleMute = {
                            val next = !isMuted
                            RichHapticEngine.performHaptic(context, if (next) RichHapticEngine.HapticStyle.WARNING else RichHapticEngine.HapticStyle.CLICK)
                            CallManager.setMuted(next)
                        },
                        isSpeakerOn = isSpeakerOn,
                        onToggleSpeaker = {
                            RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.CLICK)
                            // Requests the real speaker endpoint; the UI is driven by the route
                            // Telecom reports back, so a refused request simply changes nothing.
                            CallManager.toggleSpeaker()
                        },
                        isOnHold = isOnHold,
                        onToggleHold = {
                            RichHapticEngine.performHaptic(context, if (isOnHold) RichHapticEngine.HapticStyle.CLICK else RichHapticEngine.HapticStyle.WARNING)
                            CallManager.setHold(!isOnHold)
                        },
                        isBluetoothOn = isBluetoothOn,
                        isBluetoothAvailable = isBluetoothAvailable,
                        onToggleBluetooth = {
                            RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.CLICK)
                            // Only dispatches when a Bluetooth call endpoint exists; turning it off
                            // falls back to a wired headset when present, else the earpiece.
                            CallManager.toggleBluetooth()
                        },
                        isAddCallDialogOpen = isAddCallDialogOpen,
                        isAddCallEnabled = multiCallState.canAddCall,
                        onOpenAddCallDialog = {
                            RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.CLICK)
                            isAddCallDialogOpen = true
                        },
                        isHoldEnabled = multiCallState.canHoldPrimary || multiCallState.canUnholdPrimary,
                        // Swap/Merge are derived solely from the Telecom call list + capabilities;
                        // they never appear because a contact was selected or Add Call was tapped.
                        isSwapVisible = multiCallState.canSwap,
                        onSwap = { CallManager.swapCalls() },
                        isMergeVisible = multiCallState.canMerge,
                        onMerge = { CallManager.mergeCalls() },
                        recordingEnabled = recordingEnabled,
                        isRecording = isRecording,
                        onToggleRecording = {
                            if (isRecording || CallAudioRecorder.isRecording.value) {
                                CallManager.stopCallRecording(
                                    context = context,
                                    playFeedback = true
                                ) { result ->
                                    val message = when (result.status) {
                                        CallManager.RecordingSaveStatus.SAVED_ENCRYPTED ->
                                            R.string.recording_saved
                                        CallManager.RecordingSaveStatus.SAVED_UNENCRYPTED ->
                                            R.string.recording_encryption_failed
                                        CallManager.RecordingSaveStatus.FAILED ->
                                            R.string.recording_save_failed
                                        CallManager.RecordingSaveStatus.NO_AUDIO ->
                                            R.string.recording_empty
                                    }
                                    Toast.makeText(context, context.getString(message), Toast.LENGTH_LONG).show()
                                }
                            } else {
                                val hasAudioPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED

                                if (!hasAudioPermission) {
                                    Toast.makeText(context, "Microphone permission required for call recording", Toast.LENGTH_SHORT).show()
                                    recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else if (!RecordingDisclosure.isManualAcknowledged(context)) {
                                    // First manual recording: show the notice before any audio is captured.
                                    showRecordingDisclosure = true
                                } else {
                                    startManualRecording()
                                }
                            }
                        },
                        isQuickNotesOpen = isQuickDeclineMenuOpen,
                        onOpenQuickNotes = {
                            RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.CLICK)
                            isQuickDeclineMenuOpen = !isQuickDeclineMenuOpen
                        },
                        avatarShapeType = avatarShapeType
                    )

                    InCallBottomBar(
                        isIncoming = if (isFake) (fakeState == "RINGING") else isIncoming,
                        onAnswer = if (isFake) onFakeAnswer else onAnswer,
                        onHangUp = if (isFake) onFakeHangUp else onHangUp,
                        onToggleQuickDeclineMenu = { isQuickDeclineMenuOpen = !isQuickDeclineMenuOpen },
                        avatarShapeType = avatarShapeType
                    )
                }

            }
        }

        if (isNear && shouldActivateProximity) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isPocketProtectionEnabled) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.06f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = stringResource(R.string.pocket_lock_active),
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = stringResource(R.string.pocket_lock_active),
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.pocket_lock_desc),
                            color = Color.White.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

