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

import android.content.Context
import android.os.PowerManager
import android.telecom.Call
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.CallManager
import com.example.R
import com.example.safeState
import com.example.model.Contact
import com.example.telecom.AudioRoute
import com.example.ui.theme.LocalAmoledMode
import com.example.util.CallAudioHelper
import com.example.util.CallGestureSensorManager
import com.example.util.MultiSimManager
import com.example.util.RichHapticEngine
import kotlinx.coroutines.delay

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
    callNotesEnabled: Boolean = true,
    onSaveNote: (String) -> Unit = {},
    onMinimize: (() -> Unit)? = null,
    avatarShapeType: String = "circular",
    isPocketProtectionEnabled: Boolean = false,
    isFake: Boolean = false,
    fakeState: String = "RINGING",
    onFakeAnswer: () -> Unit = {},
    onFakeHangUp: () -> Unit = {},
    isSpam: Boolean = false,
    /**
     * Restricted mode for the lock-screen call activity. It keeps the call controls but removes
     * private destinations (contact picker, quick responses, notes and recording) from the
     * unauthenticated surface.
     */
    callOnlyMode: Boolean = false
) {
    BackHandler(enabled = true) {
        onMinimize?.invoke()
    }

    val context = LocalContext.current
    // A lock-screen call surface must never receive the device contact list. ContactCache is
    // still used for the current caller's already-resolved identity and photo.
    val uiContacts = if (callOnlyMode) emptyList() else contacts
    val activeStartTimestamp by CallManager.activeStartTimestamp.collectAsStateWithLifecycle()

    val audioState by CallManager.audioState.collectAsStateWithLifecycle()
    val waitingCall by CallManager.waitingCall.collectAsStateWithLifecycle()
    val allCalls by CallManager.calls.collectAsStateWithLifecycle()
    val currentCall by CallManager.currentCall.collectAsStateWithLifecycle()

    // Single source of truth for call audio: what Telecom reports as available and selected —
    // never the last button the user tapped.
    val routeState by CallManager.routeState.collectAsStateWithLifecycle()
    // Single source of truth for every multi-call action (swap/merge/add call).
    val multiCallState by CallManager.multiCallState.collectAsStateWithLifecycle()

    var isAddCallDialogOpen by remember { mutableStateOf(false) }
    var isInCallDialpadOpen by remember { mutableStateOf(false) }
    var showAddCallSimDialog by remember { mutableStateOf(false) }
    var pendingAddCallName by remember { mutableStateOf("") }
    var pendingAddCallNumber by remember { mutableStateOf("") }
    var addCallInFlight by remember { mutableStateOf(false) }
    var fakeActiveStartTimestamp by remember { mutableLongStateOf(0L) }
    val callableSims = remember(context) { MultiSimManager.getCallableSims(context) }

    // A successful second call adds a live call; a failed one changes nothing. Either way the
    // one-shot submission guard can be released once Telecom reports the new shape.
    LaunchedEffect(multiCallState.liveCallCount) {
        addCallInFlight = false
    }
    LaunchedEffect(addCallInFlight) {
        if (addCallInFlight) {
            delay(5_000L)
            if (multiCallState.liveCallCount < 2) addCallInFlight = false
        }
    }

    fun finishAddCall(name: String, number: String, sim: String): Boolean {
        if (addCallInFlight) return false
        addCallInFlight = true
        val placed = CallManager.placeCall(context, number, sim)
        if (placed) {
            isAddCallDialogOpen = false
            isInCallDialpadOpen = false
            showAddCallSimDialog = false
            pendingAddCallName = ""
            pendingAddCallNumber = ""
        } else {
            addCallInFlight = false
            Toast.makeText(
                context,
                context.getString(R.string.add_call_unavailable),
                Toast.LENGTH_SHORT
            ).show()
        }
        return placed
    }

    fun beginAddCall(name: String, number: String): Boolean {
        val trimmed = number.trim()
        if (trimmed.isEmpty() || addCallInFlight || !multiCallState.canAddCall) {
            Toast.makeText(
                context,
                context.getString(R.string.add_call_unavailable),
                Toast.LENGTH_SHORT
            ).show()
            return false
        }
        val isEmergency = CallManager.isEmergencyNumber(trimmed)
        if (!isEmergency && preferredSim.equals("Ask", ignoreCase = true) && callableSims.size >= 2) {
            pendingAddCallName = name
            pendingAddCallNumber = trimmed
            isAddCallDialogOpen = false
            isInCallDialpadOpen = false
            showAddCallSimDialog = true
            return true
        }
        val effectiveSim = when {
            isEmergency -> "Ask"
            preferredSim.equals("Ask", ignoreCase = true) && callableSims.size == 1 ->
                "SIM ${callableSims.first().slotIndex + 1}"
            else -> preferredSim
        }
        return finishAddCall(name, trimmed, effectiveSim)
    }

    val isMuted = routeState.isMuted
    val isSpeakerOn = routeState.isSpeakerActive
    val isBluetoothOn = routeState.isBluetoothActive
    val isBluetoothAvailable = routeState.hasBluetooth
    val isHeadsetOn = routeState.isWiredHeadsetActive

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

    DisposableEffect(Unit) {
        onDispose {
            CallAudioHelper.restoreAudioState(context, CallManager.inCallService)
        }
    }

    LaunchedEffect(isFake, fakeState) {
        if (isFake && fakeState == "ACTIVE") {
            fakeActiveStartTimestamp = System.currentTimeMillis()
        }
    }

    LaunchedEffect(currentCallState) {
        if (currentCallState == Call.STATE_ACTIVE) {
            CallManager.setSpeaker(true)
        }
    }

    val isAmoled = LocalAmoledMode.current
    val surfaceColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)

    // The other live call, when two calls exist, resolved from the Telecom-driven multi-call state
    // so the status card and its per-call End action always target a real, current call.
    val secondaryCall = remember(multiCallState.secondary, allCalls) {
        multiCallState.secondary
            ?.let { CallManager.callById(it.id) }
            // Defense in depth: a conference child/participant must never be rendered as a
            // separate held line, even if a stack still reports it as another live call.
            ?.takeIf { !CallManager.isConference(it) }
    }

    var isQuickDeclineMenuOpen by remember { mutableStateOf(false) }

    var fakeParticipants by remember(contactName, contactNumber) {
        mutableStateOf(listOf(Pair(contactName, contactNumber)))
    }

    val participants = remember(isFake, currentCall, allCalls, contactName, contactNumber, uiContacts, fakeParticipants) {
        if (isFake) {
            fakeParticipants
        } else {
            val activeList = allCalls.filter { it.safeState != Call.STATE_DISCONNECTED }
            val conferenceCall = activeList.find {
                it.children.isNotEmpty() || it.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true
            }
            if (conferenceCall != null && conferenceCall.children.isNotEmpty()) {
                conferenceCall.children.mapNotNull { child ->
                    val num = child.details?.handle?.schemeSpecificPart.orEmpty()
                    val name = uiContacts.find { it.number == num }?.name
                        ?: child.details?.callerDisplayName?.takeIf { it.isNotBlank() }
                        ?: num
                    // A participant with no name and no number must not create an empty
                    // " • " separator line in the conference header.
                    if (name.isBlank() && num.isBlank()) null else Pair(name, num)
                }
            } else {
                val num = currentCall?.details?.handle?.schemeSpecificPart ?: contactNumber
                val name = uiContacts.find { it.number == num }?.name ?: contactName.ifEmpty { num }
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
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Scrollable top/middle area (Header, Waiting dialog, Avatar / Dialpad / Decline sheet)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        InCallHeader(
                            isOnHold = isOnHold,
                            callState = currentCallState,
                            participants = participants,
                            preferredSim = preferredSim,
                            contactName = contactName,
                            contactNumber = contactNumber,
                            contacts = uiContacts,
                            secondaryCall = if (isFake) null else secondaryCall,
                            secondaryNumber = if (isFake) "" else secondaryCall?.details?.handle?.schemeSpecificPart ?: "",
                            onEndSecondary = if (isFake || secondaryCall == null) null else {
                                { CallManager.disconnect(secondaryCall) }
                            },
                            isConference = isConference,
                            activeStartTimestamp = activeStartTimestamp,
                            isFake = isFake,
                            fakeState = fakeState,
                            fakeActiveStartTimestamp = fakeActiveStartTimestamp,
                            isSpam = isSpam
                        )

                        if (!callOnlyMode) {
                            waitingCall?.let { call ->
                                InCallWaitingCallDialog(
                                    waitingCall = call,
                                    contacts = uiContacts,
                                    avatarShapeType = avatarShapeType
                                )
                            }
                        }

                        if (isQuickDeclineMenuOpen && !callOnlyMode) {
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
                                contacts = uiContacts,
                                isAddCallEnabled = !callOnlyMode && multiCallState.canAddCall,
                                onAddCall = { name, number -> beginAddCall(name, number) }
                            )
                        } else {
                            InCallAvatarDisplay(
                                participants = participants,
                                contactName = contactName,
                                contactNumber = contactNumber,
                                contacts = uiContacts,
                                avatarShapeType = avatarShapeType,
                                isConference = isConference,
                                isSpam = isSpam
                            )
                        }

                        if (isAddCallDialogOpen && !callOnlyMode) {
                            InCallAddCallDialog(
                                contacts = uiContacts,
                                onDismiss = { isAddCallDialogOpen = false },
                                onAddCall = { finalName, number ->
                                    if (isFake) {
                                        fakeParticipants = fakeParticipants + Pair(finalName, number)
                                    } else {
                                        beginAddCall(finalName, number)
                                    }
                                },
                                avatarShapeType = avatarShapeType
                            )
                        }

                        if (showAddCallSimDialog && pendingAddCallNumber.isNotBlank() && callableSims.size >= 2) {
                            SimSelectDialog(
                                context = context,
                                pendingCallNumber = pendingAddCallNumber,
                                sims = callableSims,
                                onDismiss = {
                                    showAddCallSimDialog = false
                                    pendingAddCallName = ""
                                    pendingAddCallNumber = ""
                                },
                                onSimSelected = { simLabel ->
                                    showAddCallSimDialog = false
                                    finishAddCall(pendingAddCallName, pendingAddCallNumber, simLabel)
                                }
                            )
                        }
                    }

                    // Pinned bottom controls area (Control grid + Bottom bar perfectly aligned down)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Transparent
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            InCallControlGrid(
                                isInCallDialpadOpen = isInCallDialpadOpen,
                                onToggleDialpad = {
                                    RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.KEY_TICK)
                                    isInCallDialpadOpen = !isInCallDialpadOpen
                                },
                                isCallActive = (currentCallState == Call.STATE_ACTIVE),
                                isMuted = isMuted,
                                onToggleMute = {
                                    val next = !isMuted
                                    RichHapticEngine.performHaptic(context, if (next) RichHapticEngine.HapticStyle.WARNING else RichHapticEngine.HapticStyle.CLICK)
                                    CallManager.setMuted(next)
                                },
                                isSpeakerOn = isSpeakerOn,
                                onToggleSpeaker = {
                                    RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.CLICK)
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
                                    CallManager.toggleBluetooth()
                                },
                                isAddCallDialogOpen = isAddCallDialogOpen,
                                isAddCallEnabled = !callOnlyMode && multiCallState.canAddCall,
                                showAddCall = !callOnlyMode,
                                onOpenAddCallDialog = {
                                    if (!callOnlyMode) {
                                        RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.CLICK)
                                        isAddCallDialogOpen = true
                                    }
                                },
                                isHoldEnabled = multiCallState.canHoldPrimary || multiCallState.canUnholdPrimary,
                                isSwapVisible = multiCallState.canSwap,
                                onSwap = { CallManager.swapCalls() },
                                isMergeVisible = multiCallState.canMerge,
                                onMerge = { CallManager.mergeCalls() },
                                isQuickNotesOpen = isQuickDeclineMenuOpen,
                                showQuickNotes = !callOnlyMode,
                                onOpenQuickNotes = {
                                    if (!callOnlyMode) {
                                        RichHapticEngine.performHaptic(context, RichHapticEngine.HapticStyle.CLICK)
                                        isQuickDeclineMenuOpen = !isQuickDeclineMenuOpen
                                    }
                                },
                                avatarShapeType = avatarShapeType
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            InCallBottomBar(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                                isIncoming = if (isFake) (fakeState == "RINGING") else isIncoming,
                                onAnswer = if (isFake) onFakeAnswer else onAnswer,
                                onHangUp = if (isFake) onFakeHangUp else onHangUp,
                                onToggleQuickDeclineMenu = { isQuickDeclineMenuOpen = !isQuickDeclineMenuOpen },
                                avatarShapeType = avatarShapeType,
                                showQuickResponseAction = !callOnlyMode
                            )
                        }
                    }
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

