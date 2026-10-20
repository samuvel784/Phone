/*
 * Copyright (C) 2026 MovStore
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.OutcomeReceiver
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telecom.Call
import android.widget.Toast
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.CallEndpointException
import android.telecom.DisconnectCause
import android.telecom.InCallService
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.example.data.AppDatabase
import com.example.model.AppSetting
import com.example.telecom.AudioEndpointInfo
import com.example.telecom.AudioRoute
import com.example.telecom.AudioRoutePolicy
import com.example.telecom.CallTimingLogger
import com.example.telecom.CallDecisionStore
import com.example.telecom.CallerAnnouncerManager
import com.example.telecom.NotificationRecordingPolicy
import com.example.telecom.RecordingActionExecutor
import com.example.telecom.RecordingActionState
import com.example.telecom.RecordingNotificationAction
import com.example.telecom.RecordingNotificationObserver
import com.example.telecom.RingingPolicy
import com.example.security.IntentGuard
import com.example.util.CallAudioRecorder
import com.example.util.ContactRingtonePrefs
import com.example.util.DynamicIslandOverlayManager
import com.example.util.FlashLightManager
import com.example.util.RecordingDisclosure
import com.example.util.RichHapticEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

class MyInCallService : InCallService(), CallAudioRouteController {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nm by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    private var wakeLock: PowerManager.WakeLock? = null
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var callWaitingToneGenerator: ToneGenerator? = null
    private var announcer: CallerAnnouncerManager? = null

    /** Ensures the full-screen-intent settings prompt is offered at most once per service lifetime. */

    // ---- API 34+ call endpoints -----------------------------------------------------------
    @Volatile
    private var availableCallEndpoints: List<CallEndpoint> = emptyList()

    @Volatile
    private var currentCallEndpoint: CallEndpoint? = null

    private val audioLogTag = "Nova Dialer Audio"

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            stopRinging()
        }
    }

    private fun requestRingAudio(): Boolean {
        return try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            @Suppress("DEPRECATION")
            val res = audioManager?.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_RING,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
            res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } catch (_: Exception) {
            true
        }
    }

    private fun abandonRingAudio() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(audioFocusListener)
        } catch (_: Exception) {}
    }

    @Synchronized
    private fun playCallWaitingTone() {
        try {
            if (callWaitingToneGenerator == null) {
                callWaitingToneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 75)
            }
            callWaitingToneGenerator?.startTone(ToneGenerator.TONE_SUP_CALL_WAITING, 400)
            RichHapticEngine.performHaptic(this, RichHapticEngine.HapticStyle.DOUBLE_TICK)
        } catch (_: Exception) {}
    }

    @Synchronized
    private fun stopCallWaitingTone() {
        try {
            callWaitingToneGenerator?.stopTone()
            callWaitingToneGenerator?.release()
            callWaitingToneGenerator = null
        } catch (_: Exception) {}
    }

    /**
     * True when the platform or the screening service asked for this call to stay
     * silent. On Android 10+ the platform sets [Call.EXTRA_SILENT_RINGING_REQUESTED];
     * older versions (and Nova Dialer's own screening service) use [CallDecisionStore].
     */
    private fun isCallSilenced(call: Call?): Boolean {
        val number = call?.details?.handle?.schemeSpecificPart ?: ""
        val requested = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            call?.details?.extras?.getBoolean(Call.EXTRA_SILENT_RINGING_REQUESTED, false) == true
        } else {
            false
        }
        return requested || CallDecisionStore.wasSilenced(number)
    }

    /** True if the ringtone URI can actually be opened (file may have been moved/deleted). */
    private fun isRingtoneUriReadable(uri: Uri): Boolean = try {
        when (uri.scheme?.lowercase()) {
            "content", "android.resource", "file" -> contentResolver.openInputStream(uri)?.use { true } ?: false
            else -> RingtoneManager.getRingtone(this, uri) != null
        }
    } catch (_: Exception) {
        false
    }

    @Synchronized
    private fun startRinging(call: Call?) {
        try {
            requestRingAudio()
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val androidRinger = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
            val number = call?.details?.handle?.schemeSpecificPart ?: ""

            val decision = RingingPolicy.decide(
                ringerMode = when (androidRinger) {
                    AudioManager.RINGER_MODE_SILENT -> RingingPolicy.RingerMode.SILENT
                    AudioManager.RINGER_MODE_VIBRATE -> RingingPolicy.RingerMode.VIBRATE
                    else -> RingingPolicy.RingerMode.NORMAL
                },
                silentRequestedByScreening = isCallSilenced(call),
                silencedByRules = CallDecisionStore.wasSilenced(number)
            )

            // A silenced call: no sound, no vibration. Do not create the players at all.
            if (!decision.playSound && !decision.vibrate) return

            val assignment = if (number.isNotBlank()) {
                runCatching { ContactRingtonePrefs.find(this, number) }.getOrNull()
            } else null

            // 1. Ringtone player (contact ringtone when assigned, else the system default)
            if (decision.playSound) {
                if (ringtone == null) {
                    val contactUri = assignment?.ringtoneUri
                        ?.takeIf { it.isNotBlank() }
                        ?.let { it.toUri() }
                        ?.takeIf { isRingtoneUriReadable(it) }
                    val uri = contactUri
                        ?: RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
                        // minSdk 24 > LOLLIPOP (21); AudioAttributes is always available.
                        audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setLegacyStreamType(AudioManager.STREAM_RING)
                            .build()
                    }
                }
                if (ringtone?.isPlaying == false) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ringtone?.isLooping = true
                    }
                    ringtone?.play()
                }
                CallTimingLogger.markActive(CallTimingLogger.Event.RING_STARTED)
            }

            // 2. Hardware vibration (contact pattern when assigned, else the default pattern)
            if (decision.vibrate) {
                val rawPattern = assignment?.pattern
                    ?.let { ContactRingtonePrefs.patternTimings(it) }
                    ?: longArrayOf(0, 1000, 1000, 1000)
                val pattern = RingingPolicy.ringLoopTimings(rawPattern)
                if (pattern != null) {
                    if (vibrator == null) {
                        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
                        } else {
                            @Suppress("DEPRECATION")
                            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val effect = VibrationEffect.createWaveform(pattern, 0)
                        val audioAttrs = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setLegacyStreamType(AudioManager.STREAM_RING)
                            .build()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val vibrationAttrs = VibrationAttributes.Builder()
                                .setUsage(VibrationAttributes.USAGE_RINGTONE)
                                .build()
                            vibrator?.vibrate(effect, vibrationAttrs)
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator?.vibrate(effect, audioAttrs)
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(pattern, 0)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    @Synchronized
    private fun stopRinging() {
        try {
            if (ringtone?.isPlaying == true) {
                ringtone?.stop()
            }
            ringtone = null
        } catch (_: Exception) {}

        try {
            vibrator?.cancel()
            vibrator = null
        } catch (_: Exception) {}

        stopCallWaitingTone()
        abandonRingAudio()
    }

    @Synchronized
    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                @Suppress("DEPRECATION")
                val flags = PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE
                wakeLock = pm?.newWakeLock(flags, "Nova Dialer:InCallScreenWakeLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.acquire(30_000L)
        } catch (_: Exception) {
            try {
                if (wakeLock == null) {
                    val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                    wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Nova Dialer:InCallCpuWakeLock")?.apply {
                        setReferenceCounted(false)
                    }
                }
                wakeLock?.acquire(30_000L)
            } catch (_: Exception) {}
        }
    }

    @Synchronized
    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    override fun onCreate() {
        super.onCreate()
        instanceRef = this
        initNotificationChannels()
        announcer = CallerAnnouncerManager(this)
        CallManager.registerRouteController(this)
        logFullScreenIntentAvailability()

        // Event-driven, not polled: any start/stop from the notification, in-call UI, Dynamic
        // Island or call teardown must rebuild the notification's explicit START/STOP action.
        serviceScope.launch(Dispatchers.Main.immediate) {
            RecordingNotificationObserver(CallAudioRecorder.isRecording) {
                refreshActiveCallNotification()
            }.observe()
        }
    }

    override fun onDestroy() {
        if (instanceRef == this) instanceRef = null
        stopRinging()
        releaseWakeLock()
        announcer?.shutdown()
        announcer = null
        CallManager.registerRouteController(null)
        availableCallEndpoints = emptyList()
        currentCallEndpoint = null
        DynamicIslandOverlayManager.stopCallMonitoring()
        FlashLightManager.stopFlashing(this)
        cancelIncomingNotifications()
        nm.cancel(NOTIFICATION_ID_ACTIVE)
        // Final chance to finalise audio owned by this call. The call manager's finalizer is
        // idempotent, so a normal call-end/save path is not repeated here.
        if (CallAudioRecorder.isRecording.value) {
            CallManager.stopCallRecording(context = this, playFeedback = false)
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HANG_UP -> {
                stopRinging()
                // Only ever hangs up the call the notification was built for; falls back to the
                // currently displayed call for older notifications without an id.
                val targetId = intent.getLongExtra(EXTRA_CALL_ID, NO_CALL_ID)
                if (targetId != NO_CALL_ID) {
                    CallManager.disconnectById(targetId)
                } else {
                    CallManager.disconnect()
                }
                cancelIncomingNotifications()
            }
            ACTION_DECLINE -> {
                stopRinging()
                // IMPORTANT: declining a (waiting) incoming call must never end another call that
                // happens to be connected. Resolve the exact ringing call the action was built for.
                val targetId = intent.getLongExtra(EXTRA_CALL_ID, NO_CALL_ID)
                val rejected = if (targetId != NO_CALL_ID) {
                    CallManager.rejectCallById(targetId)
                } else {
                    false
                }
                if (!rejected) {
                    // No id (or the call moved on): decline whichever ringing call is waiting /
                    // ringing, and never touch a connected call.
                    if (!CallManager.rejectWaitingCall()) {
                        CallManager.currentCall.value
                            ?.takeIf { it.safeState == Call.STATE_RINGING }
                            ?.let { CallManager.rejectCall(it) }
                    }
                }
                cancelIncomingNotifications()
            }
            ACTION_ANSWER -> {
                stopRinging()
                val targetId = intent.getLongExtra(EXTRA_CALL_ID, NO_CALL_ID)
                val answered = if (targetId != NO_CALL_ID) CallManager.answerCallById(targetId) else false
                if (!answered) CallManager.answer()
                launchCallScreen(answerOnLaunch = true)
            }
            ACTION_TOGGLE_MUTE -> {
                val isMuted = CallManager.audioState.value?.isMuted ?: false
                CallManager.setMuted(!isMuted)
                refreshActiveCallNotification()
            }
            ACTION_TOGGLE_SPEAKER -> {
                // Routed through CallManager so the request goes out as a call endpoint change on
                // Android 14+ and as a legacy audio route below.
                CallManager.toggleSpeaker()
            }
            ACTION_START_RECORD ->
                handleRecordingAction(
                    RecordingNotificationAction.START,
                    intent.getLongExtra(EXTRA_CALL_ID, NO_CALL_ID)
                )
            ACTION_STOP_RECORD ->
                handleRecordingAction(
                    RecordingNotificationAction.STOP,
                    intent.getLongExtra(EXTRA_CALL_ID, NO_CALL_ID)
                )
        }
        return START_NOT_STICKY
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val num = call.details?.handle?.schemeSpecificPart ?: ""
        val incoming = call.safeState == Call.STATE_RINGING
        // The earliest event Nova Dialer can observe. Everything before this (paging, carrier
        // signalling, Telecom setup) is outside the app's control.
        val trace = CallTimingLogger.begin(CallManager.callId(call), num, incoming)

        acquireWakeLock()
        trace.mark(CallTimingLogger.Event.WAKELOCK_ACQUIRED)

        CallManager.inCallService = this
        CallManager.addCall(call)
        DynamicIslandOverlayManager.startCallMonitoring(this)

        // Publish the call state (ringtone + incoming notification) using only information that is
        // already available, so the UI appears immediately. Contact/provider lookups happen after.
        persistCnapIfPresent(call)
        handleCallState(call)

        call.registerCallback(object : Call.Callback() {
            private var wasRinging = (call.safeState == Call.STATE_RINGING)

            override fun onStateChanged(c: Call, state: Int) {
                super.onStateChanged(c, state)
                if (state == Call.STATE_RINGING) wasRinging = true

                if (state == Call.STATE_ACTIVE || state == Call.STATE_DISCONNECTED) {
                    stopRinging()
                    announcer?.stop()
                    cancelIncomingNotifications()
                    FlashLightManager.stopFlashing(this@MyInCallService)
                }

                if (state in listOf(Call.STATE_ACTIVE, Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_HOLDING)) {
                    stopRinging()
                    showActiveCallNotification(c)
                }

                if (state == Call.STATE_DISCONNECTED) {
                    stopRinging()
                    nm.cancel(NOTIFICATION_ID_ACTIVE)
                    if (wasRinging) {
                        val cause = c.details?.disconnectCause?.code
                        if (cause != DisconnectCause.REJECTED && cause != DisconnectCause.LOCAL) {
                            showMissedCallNotification(c)
                        }
                    }
                    if (CallManager.waitingCall.value == c) {
                        CallManager.updateWaitingCall(null)
                    }
                    c.unregisterCallback(this)
                    CallTimingLogger.finishActive()
                }

                if (state == Call.STATE_ACTIVE) wasRinging = false
            }
        })

        // Supplementary caller information: resolved off the critical path, then applied.
        resolveCallerDisplayNameAsync(call, num) { resolved ->
            trace.mark(CallTimingLogger.Event.CONTACT_RESOLVED)
            if (resolved.isNotBlank() && resolved != num && resolved != "Unknown") {
                CallManager.setCallerName(resolved)
            }
            // Refresh a still-ringing incoming notification with the resolved name.
            if (call.safeState == Call.STATE_RINGING) {
                showIncomingCallNotification(call, isCallWaiting = CallManager.waitingCall.value == call)
            }
        }
    }

    /**
     * Resolves a display name without blocking the incoming-call path.
     *
     * Synchronous (in-memory) sources are used first; the ContactsProvider query and the encrypted
     * settings database are only touched on a background dispatcher.
     */
    private fun resolveCallerDisplayNameAsync(call: Call, number: String, onResolved: (String) -> Unit) {
        if (number.isBlank() && call.details?.callerDisplayName.isNullOrBlank()) {
            onResolved("Unknown")
            return
        }
        val cached = cachedCallerDisplayName(call, number)
        if (cached != null) {
            onResolved(cached)
            return
        }
        serviceScope.launch {
            val resolved = try {
                val contactName = getContactNameFromNumber(this@MyInCallService, number)
                when {
                    !contactName.isNullOrBlank() -> contactName
                    !call.details?.callerDisplayName.isNullOrBlank() -> call.details!!.callerDisplayName.toString()
                    else -> getSavedCnapName(this@MyInCallService, number) ?: number
                }
            } catch (_: Exception) {
                number
            }
            withContext(Dispatchers.Main) { onResolved(resolved) }
        }
    }

    /** In-memory only: CNAP from the call details or Nova Dialer's contact cache. */
    private fun cachedCallerDisplayName(call: Call, number: String): String? {
        if (number.isBlank()) {
            return call.details?.callerDisplayName?.takeIf { it.isNotBlank() } ?: "Unknown"
        }
        val contactName = ContactCache.getContact(number)?.name
        if (!contactName.isNullOrBlank()) return contactName
        val cnapName = call.details?.callerDisplayName
        if (!cnapName.isNullOrBlank()) return cnapName
        return ContactCache.getCnapName(number)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        stopRinging()
        CallManager.removeCall(call)
        FlashLightManager.stopFlashing(this)
        cancelIncomingNotifications()

        val remainingActiveCalls = CallManager.calls.value.filter { it.safeState != Call.STATE_DISCONNECTED }
        if (remainingActiveCalls.isEmpty()) {
            releaseWakeLock()
            DynamicIslandOverlayManager.stopCallMonitoring()
            nm.cancel(NOTIFICATION_ID_ACTIVE)

            if (CallAudioRecorder.isRecording.value) {
                CallManager.stopCallRecording(context = this, playFeedback = false)
            }
        } else {
            val nextCall = remainingActiveCalls.firstOrNull()
            if (nextCall != null) {
                showActiveCallNotification(nextCall)
            } else {
                nm.cancel(NOTIFICATION_ID_ACTIVE)
            }
        }
    }

    @Deprecated("Deprecated in the platform; kept so older Android versions still work.")
    @Suppress("DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        CallManager.updateAudioState(audioState)
        CallTimingLogger.markActive("onCallAudioStateChanged")
        refreshActiveCallNotification()
    }

    // ---------------------------------------------------------------------------------------
    //  Android 14 (API 34) call audio endpoints
    //
    //  Telecommunications replaced "audio routes" with per-call "endpoints" for the live call.
    //  Only the endpoints Telecom reports here can actually carry call audio, which is exactly
    //  what the Bluetooth button needs to know: a paired headset that is not a call-audio
    //  endpoint simply never shows up.
    // ---------------------------------------------------------------------------------------

    /** The endpoint Telecom reports as *currently* carrying this call's audio. */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        super.onCallEndpointChanged(callEndpoint)
        currentCallEndpoint = callEndpoint
        val route = AudioRoutePolicy.routeForEndpointType(callEndpoint.endpointType)
        android.util.Log.i(audioLogTag, "current call endpoint -> ${route.name}")
        CallManager.updateCurrentEndpoint(AudioEndpointInfo(callEndpoint.identifier.toString(), route))
        CallTimingLogger.markActive("onCallEndpointChanged")
        refreshActiveCallNotification()
    }

    /** The endpoints Telecom currently offers for this call. */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onAvailableCallEndpointsChanged(availableEndpoints: MutableList<CallEndpoint>) {
        super.onAvailableCallEndpointsChanged(availableEndpoints)
        availableCallEndpoints = availableEndpoints.toList()
        CallManager.updateAvailableEndpoints(
            availableCallEndpoints.map {
                AudioEndpointInfo(it.identifier.toString(), AudioRoutePolicy.routeForEndpointType(it.endpointType))
            }
        )
        refreshActiveCallNotification()
    }

    /**
     * Dispatches a route change through the API the running Android version provides.
     *
     * Success is never assumed: the outcome callback reports failure, and the UI keeps showing
     * whichever endpoint the platform reports next ([onCallEndpointChanged]).
     */
    override fun requestRoute(target: AudioRoute): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            requestRouteViaEndpoint(target)
        } else {
            requestRouteLegacy(target)
        }

    /** Legacy path for Android 7–13 (`InCallService.setAudioRoute`). */
    @Suppress("DEPRECATION")
    private fun requestRouteLegacy(target: AudioRoute): Boolean = try {
        setAudioRoute(AudioRoutePolicy.legacyRouteFor(target))
        true
    } catch (_: Exception) {
        false
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun requestRouteViaEndpoint(target: AudioRoute): Boolean {
        val wantedType = AudioRoutePolicy.endpointTypeFor(target)
        val endpoint = availableCallEndpoints.firstOrNull { it.endpointType == wantedType }
        if (endpoint == null) {
            android.util.Log.i(audioLogTag, "no ${target.name} call endpoint available; ignoring request")
            return false
        }
        if (currentCallEndpoint?.identifier == endpoint.identifier) {
            // Already on the requested endpoint; nothing to do.
            return true
        }
        return try {
            requestCallEndpointChange(
                endpoint,
                ContextCompat.getMainExecutor(this),
                object : OutcomeReceiver<Void, CallEndpointException> {
                    override fun onResult(result: Void?) {
                        android.util.Log.i(audioLogTag, "call endpoint change to ${target.name} accepted")
                    }

                    override fun onError(error: CallEndpointException) {
                        // Refused by Telecom: keep reporting the real endpoint instead of
                        // pretending the switch happened.
                        android.util.Log.w(
                            audioLogTag,
                            "call endpoint change to ${target.name} failed: code=${error.code} ${error.message}"
                        )
                        currentCallEndpoint?.let {
                            CallManager.updateCurrentEndpoint(
                                AudioEndpointInfo(it.identifier.toString(), AudioRoutePolicy.routeForEndpointType(it.endpointType))
                            )
                        }
                    }
                }
            )
            true
        } catch (e: Exception) {
            android.util.Log.w(audioLogTag, "requestCallEndpointChange threw: ${e.message}")
            false
        }
    }

    private fun refreshActiveCallNotification() {
        val activeCall = CallManager.currentCall.value
        if (activeCall != null && activeCall.safeState in listOf(
                Call.STATE_ACTIVE, Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_HOLDING
            )
        ) {
            showActiveCallNotification(activeCall)
        } else {
            nm.cancel(NOTIFICATION_ID_ACTIVE)
        }
    }

    /** Removes both the incoming and the call-waiting alerts. */
    private fun cancelIncomingNotifications() {
        nm.cancel(NOTIFICATION_ID_INCOMING)
        nm.cancel(NOTIFICATION_ID_INCOMING_WAITING)
    }

    private fun handleCallState(call: Call) {
        when (call.safeState) {
            Call.STATE_RINGING -> {
                val hasExistingActiveCall = CallManager.calls.value.any {
                    it != call && (it.safeState == Call.STATE_ACTIVE || it.safeState == Call.STATE_HOLDING || it.safeState == Call.STATE_DIALING)
                }
                if (hasExistingActiveCall) {
                    // Call Waiting: Suppress full speaker ringtone and camera flashlight to protect user's hearing
                    CallManager.updateWaitingCall(call)
                    playCallWaitingTone()
                    showIncomingCallNotification(call, isCallWaiting = true)
                } else {
                    if (!isCallSilenced(call)) {
                        FlashLightManager.startFlashing(this)
                    }
                    acquireWakeLock()
                    startRinging(call)
                    showIncomingCallNotification(call, isCallWaiting = false)

                    announcer?.announceIncomingCall(
                        call.details?.handle?.schemeSpecificPart ?: "",
                        resolveCallerDisplayName(call, call.details?.handle?.schemeSpecificPart ?: "")
                    )
                    // Incoming UI has exactly one owner: the notification's full-screen intent
                    // (or its heads-up fallback). No competing background startActivity call is
                    // made here; Android would block it or show two call screens.
                }
            }
            Call.STATE_ACTIVE, Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_HOLDING -> {
                stopRinging()
                stopCallWaitingTone()
                FlashLightManager.stopFlashing(this)
                showActiveCallNotification(call)
                // The answered-call UI is opened by the Answer PendingIntent or by
                // IncomingCallActivity's answer hand-off. Do not force a third activity here.
            }
        }
    }

    private fun showIncomingCallNotification(call: Call, isCallWaiting: Boolean = false) {
        val number = call.details?.handle?.schemeSpecificPart ?: ""
        val displayName = resolveCallerDisplayName(call, number)
        val callId = CallManager.callId(call)

        // A stable per-call request code keeps each call's PendingIntents distinct while the
        // intent itself carries the call id so Answer/Decline hit the exact Call they were built
        // for (and never a different, unrelated call).
        val requestCodeBase = (callId % 100_000L).toInt() * 10

        // Tapping the notification body opens the call dashboard *without* answering.
        val callScreenIntent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_INCOMING_CALL
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(EXTRA_SHOW_CALL, true)
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(IntentGuard.EXTRA_GUARD_TOKEN, IntentGuard.token(this@MyInCallService))
        }

        // The full-screen intent is the only platform-sanctioned way to present UI over the
        // keyguard (Android 10+ blocks ordinary background activity starts). It targets the
        // dedicated IncomingCallActivity rather than the app-lock-gated MainActivity, and hands
        // over the already-resolved caller details so the lock screen does zero lookups.
        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            action = ACTION_INCOMING_CALL
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(IncomingCallActivity.EXTRA_CALLER_NAME, displayName)
            putExtra(IncomingCallActivity.EXTRA_CALLER_NUMBER, number)
            putExtra(IntentGuard.EXTRA_GUARD_TOKEN, IntentGuard.token(this@MyInCallService))
        }

        val declinePendingIntent = PendingIntent.getService(
            this, requestCodeBase + 2,
            Intent(this, MyInCallService::class.java).apply {
                action = ACTION_DECLINE
                putExtra(EXTRA_CALL_ID, callId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val answerPendingIntent = PendingIntent.getActivity(
            this, requestCodeBase + 3,
            Intent(this, MainActivity::class.java).apply {
                action = ACTION_ANSWER
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra(EXTRA_SHOW_CALL, true)
                putExtra(EXTRA_ANSWER, true)
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(IntentGuard.EXTRA_GUARD_TOKEN, IntentGuard.token(this@MyInCallService))
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Tapping the notification body opens the call UI *without* answering.
        val openCallScreenPendingIntent = PendingIntent.getActivity(
            this, requestCodeBase,
            callScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Used only for the lock-screen launch. Kept separate from the tap target so an unlocked
        // tap keeps landing on the dashboard that is already showing the call.
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, requestCodeBase + 1,
            fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val caller = Person.Builder().setName(displayName).setKey(number).build()
        val style = NotificationCompat.CallStyle.forIncomingCall(caller, declinePendingIntent, answerPendingIntent)

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_INCOMING)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openCallScreenPendingIntent)

        if (!isCallWaiting) {
            // While the screen is locked the full-screen intent is the only sanctioned way to
            // present the call UI. When Android refuses it (canUseFullScreenIntent() == false,
            // e.g. a non-default-dialer install on Android 14+) the notification is automatically
            // downgraded to a heads-up banner, so surface the limitation instead of failing silently.
            if (canUseFullScreenIntent()) {
                notificationBuilder.setFullScreenIntent(fullScreenPendingIntent, true)
            } else {
                android.util.Log.w(
                    NOTIFICATION_TAG,
                    "Full-screen intent not permitted (canUseFullScreenIntent=false). " +
                        "Incoming call will show as a heads-up notification; the user can " +
                        "grant full-screen intent access from system settings."
                )
            }
            CallTimingLogger.markActive(CallTimingLogger.Event.FULL_SCREEN_INTENT)
        }

        CallTimingLogger.markActive(CallTimingLogger.Event.NOTIFICATION_POSTED)
        nm.notify(
            if (isCallWaiting) NOTIFICATION_ID_INCOMING_WAITING else NOTIFICATION_ID_INCOMING,
            notificationBuilder.build()
        )
    }

    /** True when Android will actually present the full-screen (lockscreen) call UI. */
    private fun canUseFullScreenIntent(): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            nm.canUseFullScreenIntent()
        } else {
            NotificationManagerCompat.from(this).areNotificationsEnabled()
        }
    } catch (_: Exception) {
        true
    }

    /**
     * Logs why the lock-screen call UI may not appear, so the limitation is diagnosable instead of
     * failing silently. Nothing here tries to force an interface the OS has refused.
     */
    private fun logFullScreenIntentAvailability() {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            android.util.Log.w(
                NOTIFICATION_TAG,
                "Notifications are disabled for Nova Dialer; incoming calls cannot be presented. " +
                    "The user must grant POST_NOTIFICATIONS."
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !canUseFullScreenIntent()) {
            android.util.Log.w(
                NOTIFICATION_TAG,
                "Full-screen intent access is not granted; incoming calls will use the " +
                    "heads-up notification fallback."
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val incoming = nm.getNotificationChannel(CHANNEL_INCOMING)
            if (incoming != null && incoming.importance < NotificationManager.IMPORTANCE_HIGH) {
                android.util.Log.w(
                    NOTIFICATION_TAG,
                    "Incoming-call channel importance is ${incoming.importance}; " +
                        "heads-up presentation requires IMPORTANCE_HIGH."
                )
            }
        }
    }

    private fun showActiveCallNotification(call: Call) {
        if (call.safeState == Call.STATE_DISCONNECTED || CallManager.calls.value.none { it == call && it.safeState != Call.STATE_DISCONNECTED }) {
            nm.cancel(NOTIFICATION_ID_ACTIVE)
            return
        }
        val number = call.details?.handle?.schemeSpecificPart ?: ""
        val displayName = resolveCallerDisplayName(call, number)
        val activeCallId = CallManager.callId(call)

        val contentPendingIntent = PendingIntent.getActivity(
            this, 200,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_SHOW_CALL, true)
                putExtra(EXTRA_CALL_ID, activeCallId)
                putExtra(IntentGuard.EXTRA_GUARD_TOKEN, IntentGuard.token(this@MyInCallService))
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val hangUpPendingIntent = PendingIntent.getService(
            this, 201,
            Intent(this, MyInCallService::class.java).apply {
                action = ACTION_HANG_UP
                // Scope the action to the call this notification was built for.
                putExtra(EXTRA_CALL_ID, activeCallId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isRecording = CallAudioRecorder.isRecording.value
        val recordAction = NotificationRecordingPolicy.actionFor(isRecording)

        // If the one-time notice has not been accepted, route the tap straight to the private
        // activity instead of asking this service to start audio (and to try to launch UI from the
        // background). Once acknowledged, the service receives an explicit START/STOP action; it
        // never infers the operation from a potentially stale notification label.
        val recordPendingIntent = if (!isRecording &&
            !RecordingDisclosure.isManualAcknowledged(this)
        ) {
            PendingIntent.getActivity(
                this, 205,
                Intent(this, RecordingDisclosureActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(EXTRA_CALL_ID, activeCallId)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } else {
            PendingIntent.getService(
                this,
                if (recordAction == RecordingNotificationAction.START) {
                    REQUEST_START_RECORD
                } else {
                    REQUEST_STOP_RECORD
                },
                Intent(this, MyInCallService::class.java).apply {
                    action = when (recordAction) {
                        RecordingNotificationAction.START -> ACTION_START_RECORD
                        RecordingNotificationAction.STOP -> ACTION_STOP_RECORD
                    }
                    // Scope the action to the call this notification was built for.
                    putExtra(EXTRA_CALL_ID, activeCallId)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val speakerPendingIntent = PendingIntent.getService(
            this, 203,
            Intent(this, MyInCallService::class.java).apply { action = ACTION_TOGGLE_SPEAKER },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val mutePendingIntent = PendingIntent.getService(
            this, 204,
            Intent(this, MyInCallService::class.java).apply { action = ACTION_TOGGLE_MUTE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isMuted = CallManager.audioState.value?.isMuted ?: false
        val isSpeaker = (CallManager.audioState.value?.route ?: CallAudioState.ROUTE_EARPIECE) == CallAudioState.ROUTE_SPEAKER
        val isConference = CallManager.isConference(call) || CallManager.isConferenceActive.value

        val recordTitle = if (isRecording) "■ Stop Rec" else "● Record"
        val speakerTitle = if (isSpeaker) "Earpiece" else "Speaker"
        val muteTitle = if (isMuted) "Unmute" else "Mute"

        val titlePrefix = if (isConference) getString(R.string.call_status_conference) else "Ongoing Call"
        val contentTitle = if (isRecording) "$titlePrefix • [REC]" else titlePrefix
        val contentText = if (isConference) "Conference call with $displayName is active" else "Call with $displayName is active"

        val notification = NotificationCompat.Builder(this, CHANNEL_ACTIVE)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_btn_speak_now, recordTitle, recordPendingIntent)
            .addAction(android.R.drawable.stat_notify_call_mute, muteTitle, mutePendingIntent)
            .addAction(android.R.drawable.stat_sys_speakerphone, speakerTitle, speakerPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.btn_end_call), hangUpPendingIntent)
            .build()

        nm.notify(NOTIFICATION_ID_ACTIVE, notification)
    }

    private fun showMissedCallNotification(call: Call) {
        val number = call.details?.handle?.schemeSpecificPart ?: "Unknown"
        val displayName = resolveCallerDisplayName(call, number)

        val contentPendingIntent = PendingIntent.getActivity(
            this, 301,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("SHOW_CALL_LOG", true)
                putExtra(IntentGuard.EXTRA_GUARD_TOKEN, IntentGuard.token(this@MyInCallService))
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val callBackPendingIntent = PendingIntent.getActivity(
            this, 302,
            Intent(Intent.ACTION_DIAL).apply {
                data = "tel:$number".toUri()
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_MISSED)
            .setSmallIcon(android.R.drawable.sym_call_missed)
            .setContentTitle(getString(R.string.call_type_missed))
            .setContentText("${getString(R.string.call_type_missed)}: $displayName")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.sym_action_call, getString(R.string.btn_call_back), callBackPendingIntent)
            .build()

        nm.notify(NOTIFICATION_ID_MISSED, notification)
    }

    private fun handleRecordingAction(
        action: RecordingNotificationAction,
        expectedCallId: Long = NO_CALL_ID
    ) {
        val currentCall = CallManager.currentCall.value
        val actionBelongsToCurrentCall = expectedCallId == NO_CALL_ID ||
            (currentCall != null && CallManager.callId(currentCall) == expectedCallId)

        // A notification action from an old call must never start or stop audio for a newer call.
        if (!actionBelongsToCurrentCall) {
            refreshActiveCallNotification()
            return
        }

        RecordingActionExecutor(
            state = {
                val call = CallManager.currentCall.value
                RecordingActionState(
                    isRecording = CallAudioRecorder.isRecording.value,
                    hasLiveCall = call != null && call.safeState != Call.STATE_DISCONNECTED,
                    microphoneGranted = ContextCompat.checkSelfPermission(
                        this@MyInCallService,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED,
                    disclosureAcknowledged = RecordingDisclosure.isManualAcknowledged(this@MyInCallService)
                )
            },
            startRecording = { CallManager.startCallRecording(this@MyInCallService) },
            stopRecording = {
                CallManager.stopCallRecording(
                    context = this@MyInCallService,
                    playFeedback = true
                )
            },
            onShowDisclosure = { launchRecordingDisclosure() },
            onPermissionRequired = {
                Toast.makeText(
                    this@MyInCallService,
                    getString(R.string.recording_start_failed),
                    Toast.LENGTH_LONG
                ).show()
            },
            onStartFailed = {
                Toast.makeText(
                    this@MyInCallService,
                    getString(R.string.recording_start_failed),
                    Toast.LENGTH_LONG
                ).show()
            },
            refreshNotification = { refreshActiveCallNotification() }
        ).execute(action)
    }

    /** Fallback for old notifications: UI must be opened by an activity, never by this service. */
    private fun launchRecordingDisclosure() {
        val call = CallManager.currentCall.value ?: return
        try {
            startActivity(
                Intent(this, RecordingDisclosureActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(EXTRA_CALL_ID, CallManager.callId(call))
                }
            )
        } catch (_: Exception) {
            Toast.makeText(
                this,
                getString(R.string.recording_start_failed),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Caller display name for notification/announcement text, using only information that is
     * already in memory. Never touches the ContactsProvider or the database, because this runs on
     * the incoming-call critical path. The authoritative name is resolved asynchronously right
     * after (see [resolveCallerDisplayNameAsync]) and the incoming notification is refreshed.
     */
    private fun resolveCallerDisplayName(call: Call, number: String): String =
        cachedCallerDisplayName(call, number) ?: number.ifBlank { "Unknown" }

    private fun persistCnapIfPresent(call: Call) {
        val number = call.details?.handle?.schemeSpecificPart ?: return
        val cnapName = call.details?.callerDisplayName ?: return
        if (cnapName.isNotBlank() && number.isNotBlank()) {
            ContactCache.putCnapName(number, cnapName)
            serviceScope.launch {
                try {
                    AppDatabase.getDatabase(this@MyInCallService)
                        .dialerDao()
                        .insertSetting(AppSetting("cnap_" + number.filter { it.isDigit() }, cnapName))
                } catch (_: Exception) {}
            }
        }
    }

    private fun launchCallScreen(answerOnLaunch: Boolean) {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                action = if (answerOnLaunch) ACTION_ANSWER else ACTION_INCOMING_CALL
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra(EXTRA_SHOW_CALL, true)
                putExtra(IntentGuard.EXTRA_GUARD_TOKEN, IntentGuard.token(this@MyInCallService))
                if (answerOnLaunch) putExtra(EXTRA_ANSWER, true)
            }
            startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun initNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try { nm.deleteNotificationChannel("incoming_call_channel_v2") } catch (_: Exception) {}
            try { nm.deleteNotificationChannel("incoming_call_channel_v3") } catch (_: Exception) {}

            val incomingChannel = NotificationChannel(CHANNEL_INCOMING, "Incoming Calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Incoming call alerts and full screen notifications"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }

            nm.createNotificationChannels(listOf(
                incomingChannel,
                NotificationChannel(CHANNEL_ACTIVE, "Active Calls", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Ongoing call controls"
                    setShowBadge(false)
                },
                NotificationChannel(CHANNEL_MISSED, "Missed Calls", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Missed call notifications"
                }
            ))
        }
    }

    companion object {
        const val CHANNEL_INCOMING = "incoming_call_channel_v4"
        const val CHANNEL_ACTIVE = "active_call_channel"
        const val CHANNEL_MISSED = "missed_call_channel"

        const val NOTIFICATION_ID_INCOMING = 1
        const val NOTIFICATION_ID_MISSED = 2
        const val NOTIFICATION_ID_ACTIVE = 3
        /** Call waiting gets its own id so it never overwrites the main incoming-call alert. */
        const val NOTIFICATION_ID_INCOMING_WAITING = 4

        const val ACTION_HANG_UP = "com.example.ACTION_HANG_UP"
        const val ACTION_ANSWER = "com.example.ACTION_ANSWER"
        const val ACTION_DECLINE = "com.example.ACTION_DECLINE"
        const val ACTION_START_RECORD = "com.example.ACTION_START_RECORD"
        const val ACTION_STOP_RECORD = "com.example.ACTION_STOP_RECORD"
        const val ACTION_TOGGLE_SPEAKER = "com.example.ACTION_TOGGLE_SPEAKER"
        const val ACTION_TOGGLE_MUTE = "com.example.ACTION_TOGGLE_MUTE"
        const val ACTION_INCOMING_CALL = "com.example.INCOMING_CALL"

        private const val REQUEST_START_RECORD = 202
        private const val REQUEST_STOP_RECORD = 206

        const val EXTRA_SHOW_CALL = "SHOW_CALL_SCREEN"
        const val EXTRA_ANSWER = "ANSWER_ON_LAUNCH"

        /** Identifies the exact Telecom call a notification action belongs to. */
        const val EXTRA_CALL_ID = "com.example.EXTRA_CALL_ID"
        const val NO_CALL_ID = -1L

        private const val NOTIFICATION_TAG = "Nova Dialer Incoming"

        @Volatile
        private var instanceRef: MyInCallService? = null

        fun silenceRingtone() {
            instanceRef?.stopRinging()
        }

        /** Lets the disclosure activity refresh the Record/Stop action after it starts audio. */
        fun refreshActiveCallNotification() {
            instanceRef?.refreshActiveCallNotification()
        }
    }
}
