/*
 * Copyright (C) 2026 MovStore
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.example.data.AppDatabase
import com.example.model.AppSetting
import com.example.model.CallRecording
import com.example.telecom.AudioEndpointInfo
import com.example.telecom.AudioRoute
import com.example.telecom.AudioRoutePolicy
import com.example.telecom.CallAudioRouteState
import com.example.telecom.CallSnapshot
import com.example.telecom.MultiCallStateMachine
import com.example.telecom.MultiCallUiState
import com.example.ui.components.getCurrentLocale
import com.example.util.CallAudioHelper
import com.example.util.CallAudioRecorder
import com.example.util.RecordingCrypto
import com.example.util.MultiSimManager
import com.example.util.PhoneNumberCompat
import com.example.util.RecordingFeedbackHelper
import com.example.util.SimCallTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import androidx.core.net.toUri
import kotlin.time.Duration.Companion.milliseconds

/**
 * Implemented by the [InCallService] so [CallManager] can dispatch route changes through the API
 * that matches the device's Android version (call endpoints on API 34+, legacy audio route below).
 *
 * Keeping it behind an interface means the routing decision logic is testable without Telecom.
 */
interface CallAudioRouteController {
    /**
     * Asks Telecom to switch call audio to [target].
     *
     * @return true when the request was dispatched (the result arrives asynchronously and is
     * reported back through the audio route state flow), false when it could not be dispatched.
     */
    fun requestRoute(target: AudioRoute): Boolean
}

object CallManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var dtmfJob: Job? = null

    /** Window in which an identical outgoing number is treated as a duplicate tap. */
    private const val DUPLICATE_CALL_WINDOW_MS = 2000L
    @Volatile
    private var lastPlacedNumber: String = ""
    @Volatile
    private var lastPlacedAtMs: Long = 0L

    /** Smallest file that can contain a usable AAC frame; shorter files are failed recordings. */
    private const val MIN_VALID_RECORDING_BYTES = 1024L

    /**
     * Keeps a double tap (or a stale UI) from sending a second, conflicting conference request
     * while Telecom is still processing the first one. The live conference state remains the
     * authority; this is only a short dispatch guard.
     */
    private const val MERGE_REQUEST_GUARD_MS = 2_500L
    private val mergeRequestLock = Any()
    private var pendingMergePair: Pair<Long, Long>? = null
    private var pendingMergeAtMs = 0L

    /**
     * All stop paths (UI, notification, call-end, service destruction) converge on this lock.
     * A single job finalises the file, so an in-flight encryption can never be duplicated and
     * a second stop cannot insert a second database row.
     */
    private val recordingFinalizationLock = Any()
    private var recordingFinalizationJob: Job? = null
    private var lastFinalizedRecordingPath: String? = null
    private var recordingCallId: Long = -1L

    private val _isAppInForeground = MutableStateFlow(false)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    fun setAppInForeground(inForeground: Boolean) {
        _isAppInForeground.value = inForeground
    }

    private val _currentCall = MutableStateFlow<Call?>(null)
    val currentCall: StateFlow<Call?> = _currentCall.asStateFlow()

    private val _waitingCall = MutableStateFlow<Call?>(null)
    val waitingCall: StateFlow<Call?> = _waitingCall.asStateFlow()

    private val _calls = MutableStateFlow<List<Call>>(emptyList())
    val calls: StateFlow<List<Call>> = _calls.asStateFlow()

    private val _callState = MutableStateFlow(Call.STATE_DISCONNECTED)
    val callState: StateFlow<Int> = _callState.asStateFlow()

    private val _audioState = MutableStateFlow<CallAudioState?>(null)
    val audioState: StateFlow<CallAudioState?> = _audioState.asStateFlow()

    // ---------------------------------------------------------------------------------------
    //  Call audio routing (API-version aware).
    //
    //  On API 34+ Bluetooth is only selectable when Telecom reports a Bluetooth *call endpoint*
    //  for the live call; a paired headset that cannot carry call audio never appears. On older
    //  versions the legacy CallAudioState route mask is used. Both are normalised into
    //  CallAudioRouteState, which is the only thing the in-call UI reads.
    // ---------------------------------------------------------------------------------------
    private val _routeState = MutableStateFlow(CallAudioRouteState.Unknown)
    val routeState: StateFlow<CallAudioRouteState> = _routeState.asStateFlow()

    private val _availableEndpoints = MutableStateFlow<List<AudioEndpointInfo>>(emptyList())
    val availableEndpoints: StateFlow<List<AudioEndpointInfo>> = _availableEndpoints.asStateFlow()

    @Volatile
    private var currentEndpoint: AudioEndpointInfo? = null

    @Volatile
    private var routeController: CallAudioRouteController? = null

    /** Binds the [InCallService] that performs route changes, or null when it goes away. */
    fun registerRouteController(controller: CallAudioRouteController?) {
        routeController = controller
    }

    /**
     * The call list, states, capabilities and conferenceable calls reported by Telecom, reduced
     * to the actions the in-call UI may currently offer. This is the multi-call source of truth.
     */
    private val _multiCallState = MutableStateFlow(MultiCallUiState())
    val multiCallState: StateFlow<MultiCallUiState> = _multiCallState.asStateFlow()

    // Identity based call registry so notification actions can target the exact Call they were
    // created for (Telecom handed us the object; only the id travels through an Intent).
    private val callIds = java.util.IdentityHashMap<Call, Long>()
    private val callsById = HashMap<Long, Call>()
    private var nextCallId = 1L
    private val conferenceableByCall = java.util.IdentityHashMap<Call, List<Call>>()
    private val lastEndpointsLog = java.util.concurrent.atomic.AtomicReference<String?>(null)

    private val _callerNumber = MutableStateFlow("")
    val callerNumber: StateFlow<String> = _callerNumber.asStateFlow()

    private val _callerName = MutableStateFlow("")
    val callerName: StateFlow<String> = _callerName.asStateFlow()

    private val _callerCnapName = MutableStateFlow("")
    val callerCnapName: StateFlow<String> = _callerCnapName.asStateFlow()

    private val _callerPhotoUri = MutableStateFlow("")
    val callerPhotoUri: StateFlow<String> = _callerPhotoUri.asStateFlow()

    private val _callerLabel = MutableStateFlow("")
    val callerLabel: StateFlow<String> = _callerLabel.asStateFlow()

    fun setCallerName(name: String) {
        if (name.isNotBlank()) {
            _callerName.value = name
        }
    }

    fun setCallerDetails(name: String, photoUri: String = "", label: String = "") {
        if (name.isNotBlank()) _callerName.value = name
        if (photoUri.isNotBlank()) _callerPhotoUri.value = photoUri
        if (label.isNotBlank()) _callerLabel.value = label
    }

    private val _activeStartTimestamp = MutableStateFlow(0L)
    val activeStartTimestamp: StateFlow<Long> = _activeStartTimestamp.asStateFlow()

    private val _currentSimSlot = MutableStateFlow(1)
    val currentSimSlot: StateFlow<Int> = _currentSimSlot.asStateFlow()

    private val _isConferenceActive = MutableStateFlow(false)
    val isConferenceActive: StateFlow<Boolean> = _isConferenceActive.asStateFlow()

    fun isConference(call: Call?): Boolean {
        if (call == null) return false
        // 1. Telecom Call Hierarchy: Has children (conference host) or has parent (merged participant)
        if (call.children.isNotEmpty() || call.parent != null) return true
        val details = call.details ?: return false
        val properties = details.callProperties
        // 2. Official Telecom Call Properties: PROPERTY_CONFERENCE or PROPERTY_GENERIC_CONFERENCE
        val hasConferenceProperty = (properties and Call.Details.PROPERTY_CONFERENCE != 0) ||
                (properties and Call.Details.PROPERTY_GENERIC_CONFERENCE != 0) ||
                details.hasProperty(Call.Details.PROPERTY_CONFERENCE) ||
                details.hasProperty(Call.Details.PROPERTY_GENERIC_CONFERENCE)
        if (hasConferenceProperty) return true

        // 3. Conference Management Capabilities (Active conference participant management)
        // NOTE: CAPABILITY_MERGE_CONFERENCE and CAPABILITY_SWAP_CONFERENCE must NOT be checked here
        // because Telecom assigns them to normal 1-on-1 calls to indicate they CAN be merged in future!
        val capabilities = details.callCapabilities
        val isManagingConference = (capabilities and Call.Details.CAPABILITY_MANAGE_CONFERENCE != 0)
        if (isManagingConference) return true

        // 4. Extras signaling active multi-party conference
        val extras = details.extras
        if (extras != null) {
            if (extras.getBoolean("android.telecom.extra.IS_CONFERENCE", false)) return true
            val participants = androidx.core.os.BundleCompat.getParcelableArrayList(extras, "android.telecom.extra.CONFERENCE_PARTICIPANTS", android.net.Uri::class.java)
            if (participants != null && participants.size > 1) return true
        }
        return false
    }

    // Held for the whole process lifetime only to expose the Application context to Telecom
    // helpers. The Application context is process-scoped and never holds an Activity, so this
    // is not a real leak (lint's generic static-field rule does not know the context type).
    @SuppressLint("StaticFieldLeak")
    @Volatile
    var appContext: Context? = null

    // The live InCallService also implements Context (via Service). It is the process-scoped
    // Telecom service, is always cleared in onDestroy(), and never outlives the process, so the
    // static reference is intentional and bounded rather than a leaked Activity.
    @SuppressLint("StaticFieldLeak")
    var inCallService: InCallService? = null
        set(value) {
            field = value
            if (value != null) {
                appContext = value.applicationContext
            } else {
                _audioState.value = null
                _availableEndpoints.value = emptyList()
                currentEndpoint = null
                _routeState.value = CallAudioRouteState.Unknown
                routeController = null
            }
        }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            notifyCallsChanged()

            if (state == Call.STATE_DISCONNECTED) {
                // Clear the waiting-call slot *before* the early return; otherwise the
                // waitingCall state would never be reset (this branch was previously unreachable).
                if (call == _waitingCall.value) updateWaitingCall(null)
                removeCall(call)
                return
            }

            if (call == _currentCall.value) {
                _callState.value = state
                if (state == Call.STATE_ACTIVE) {
                    if (_activeStartTimestamp.value == 0L) {
                        val connect = call.details?.connectTimeMillis ?: 0L
                        _activeStartTimestamp.value = if (connect > 0L) connect else System.currentTimeMillis()
                    }
                    autoStartRecordingIfNeeded()
                }
            }
            autoSelectCurrentCall()
        }

        override fun onParentChanged(call: Call, parent: Call?) {
            super.onParentChanged(call, parent)
            notifyCallsChanged()
            autoSelectCurrentCall()
        }

        override fun onChildrenChanged(call: Call, children: List<Call>) {
            super.onChildrenChanged(call, children)
            notifyCallsChanged()
            autoSelectCurrentCall()
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            super.onDetailsChanged(call, details)
            notifyCallsChanged()
            autoSelectCurrentCall()
        }

        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: List<Call>) {
            super.onConferenceableCallsChanged(call, conferenceableCalls)
            conferenceableByCall[call] = conferenceableCalls
            notifyCallsChanged()
        }

        override fun onConnectionEvent(call: Call, event: String, extras: Bundle?) {
            super.onConnectionEvent(call, event, extras)
            notifyCallsChanged()
            autoSelectCurrentCall()
        }
    }

    private fun notifyCallsChanged() {
        val currentList = ArrayList(_calls.value)
        _calls.value = currentList
        _isConferenceActive.value = currentList.any { it.safeState != Call.STATE_DISCONNECTED && isConference(it) } ||
                isConference(_currentCall.value)
        clearSettledMergeRequest()
        recomputeMultiCallState()
    }

    /**
     * Drops the duplicate-merge guard once Telecom has clearly moved past the pending pair. The
     * guard is never treated as proof that a merge succeeded; it only prevents a second request
     * while the first is still in flight.
     */
    private fun clearSettledMergeRequest() {
        synchronized(mergeRequestLock) {
            val pair = pendingMergePair ?: return
            val first = callById(pair.first)
            val second = callById(pair.second)
            val settled = first == null ||
                    second == null ||
                    first.safeState == Call.STATE_DISCONNECTED ||
                    second.safeState == Call.STATE_DISCONNECTED ||
                    isConference(first) ||
                    isConference(second)
            if (settled) {
                pendingMergePair = null
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    //  Multi-call state
    // ---------------------------------------------------------------------------------------

    /** Assigns (once) a stable, process-local id to a call. */
    fun callId(call: Call): Long = synchronized(callIds) {
        callIds[call] ?: (nextCallId++).also { id ->
            callIds[call] = id
            callsById[id] = call
        }
    }

    /** Resolves an id produced by [callId], or null when the call is no longer known. */
    fun callById(id: Long): Call? = synchronized(callIds) { callsById[id] }

    /** Immutable snapshots for pure action/presentation routing. */
    fun liveCallSnapshots(): List<CallSnapshot> = _calls.value.map { snapshotOf(it) }

    /** Snapshot of a live call, reduced to what [MultiCallStateMachine] needs. */
    private fun snapshotOf(call: Call): CallSnapshot {
        val details = call.details
        val children = runCatching { call.children }.getOrDefault(emptyList())
        val parent = runCatching { call.parent }.getOrNull()
        // Refresh from the live Call as well as the callback cache. A details change can arrive
        // without a separate conferenceable-calls callback on some Telecom stacks.
        runCatching { call.conferenceableCalls }.getOrNull()?.let {
            conferenceableByCall[call] = it
        }
        val conferenceables = conferenceableByCall[call].orEmpty()
        return CallSnapshot(
            id = callId(call),
            state = call.safeState,
            capabilities = details?.callCapabilities ?: 0,
            properties = details?.callProperties ?: 0,
            conferenceableIds = conferenceables.map { callId(it) },
            isConferenceHost = children.isNotEmpty(),
            isConferenceParticipant = parent != null
        )
    }

    private fun recomputeMultiCallState() {
        val snapshots = _calls.value.map { snapshotOf(it) }
        _multiCallState.value = MultiCallStateMachine.evaluate(
            calls = snapshots,
            preferredPrimaryId = _currentCall.value?.let { callId(it) },
            waitingCallId = _waitingCall.value?.let { callId(it) }
        )
    }

    // ---------------------------------------------------------------------------------------
    //  Call audio routing
    // ---------------------------------------------------------------------------------------

    /** Reports the endpoints Telecom currently offers for the live call (API 34+). */
    fun updateAvailableEndpoints(endpoints: List<AudioEndpointInfo>) {
        _availableEndpoints.value = endpoints
        val summary = endpoints.joinToString { it.route.name }
        if (lastEndpointsLog.getAndSet(summary) != summary) {
            android.util.Log.i("Nova Dialer Audio", "available call endpoints: [$summary]")
        }
        recomputeRouteState()
    }

    /** Reports the endpoint Telecom says is *actually* carrying call audio right now (API 34+). */
    fun updateCurrentEndpoint(endpoint: AudioEndpointInfo?) {
        currentEndpoint = endpoint
        recomputeRouteState()
    }

    fun updateAudioState(state: CallAudioState?) {
        _audioState.value = state
        recomputeRouteState()
    }

    /**
     * Combines whatever the platform reported into one consistent routing state. Endpoint data
     * wins on API 34+; the legacy [CallAudioState] is used when endpoints are unavailable so the
     * UI still works on devices whose stack only implements the deprecated callbacks.
     */
    private fun recomputeRouteState() {
        val legacy = _audioState.value
        val endpoints = _availableEndpoints.value
        val muted = legacy?.isMuted ?: _routeState.value.isMuted
        val state = if (endpoints.isNotEmpty()) {
            AudioRoutePolicy.fromEndpoints(currentEndpoint, endpoints, muted)
        } else if (currentEndpoint != null) {
            // Endpoints are known but the platform has not listed them (e.g. only onCallEndpointChanged
            // was delivered). Treat the reported endpoint as the only extra selectable route.
            AudioRoutePolicy.fromEndpoints(
                currentEndpoint,
                listOf(currentEndpoint!!),
                muted
            )
        } else if (legacy != null) {
            AudioRoutePolicy.fromLegacy(legacy.route, legacy.supportedRouteMask, legacy.isMuted)
        } else if (_currentCall.value != null) {
            // A call is live but neither endpoints nor a legacy audio state have arrived yet.
            // Earpiece and speaker physically exist in a voice call; Bluetooth must not be assumed.
            AudioRoutePolicy.fromLegacy(null, 0, false)
        } else {
            CallAudioRouteState.Unknown
        }
        if (state != _routeState.value) _routeState.value = state
    }

    /**
     * Requests a route change through the API appropriate for this device.
     *
     * Never claims success: the UI is driven by the state Telecom reports back, so an unavailable
     * endpoint, a refused request or a disconnected headset all simply leave the UI on the real
     * route.
     */
    fun requestAudioRoute(target: AudioRoute): Boolean {
        val state = _routeState.value
        if (target == AudioRoute.UNKNOWN) return false
        if (!state.isAvailable(target)) {
            android.util.Log.i("Nova Dialer Audio", "route ${target.name} not available: ${state.availableRoutes}")
            return false
        }
        val controller = routeController
        if (controller == null) {
            android.util.Log.i("Nova Dialer Audio", "no route controller bound yet; ignoring ${target.name}")
            return false
        }
        return controller.requestRoute(target)
    }

    /** Speaker button behaviour: reflects the real route and requests the correct target. */
    fun toggleSpeaker() {
        requestAudioRoute(AudioRoutePolicy.speakerToggleTarget(_routeState.value))
    }

    /** Bluetooth button behaviour: only meaningful when a Bluetooth call endpoint exists. */
    fun toggleBluetooth() {
        AudioRoutePolicy.bluetoothToggleTarget(_routeState.value)?.let { requestAudioRoute(it) }
    }

    fun addCall(call: Call) {
        callId(call) // register a stable id before anything can reference the call
        if (call !in _calls.value) {
            _calls.value = _calls.value + call
            call.registerCallback(callCallback)
            // Seed the conferenceable set from the current details so merge decisions are correct
            // even if onConferenceableCallsChanged is only delivered on the next change.
            runCatching { call.conferenceableCalls }.getOrNull()?.let { conferenceableByCall[call] = it }
        }
        // A ringing call that arrives while another call is already live is call waiting.
        val isWaiting = call.safeState == Call.STATE_RINGING && _calls.value.any {
            it != call && it.safeState in listOf(Call.STATE_ACTIVE, Call.STATE_HOLDING, Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_RINGING)
        } && _currentCall.value != null && _currentCall.value != call
        notifyCallsChanged()
        if (isWaiting) {
            updateWaitingCall(call)
        } else {
            autoSelectCurrentCall()
        }
    }

    fun removeCall(call: Call) {
        val removedCallId = callId(call)
        if (call in _calls.value) {
            _calls.value = _calls.value - call
            call.unregisterCallback(callCallback)
        }
        synchronized(callIds) {
            callIds.remove(call)?.let { callsById.remove(it) }
        }
        conferenceableByCall.remove(call)
        // Drop this call from any other call's conferenceable set so merge never targets a dead call.
        conferenceableByCall.entries.forEach { entry ->
            if (entry.value.any { it === call }) {
                conferenceableByCall[entry.key] = entry.value.filter { it !== call }
            }
        }
        notifyCallsChanged()
        if (_currentCall.value == call || recordingCallId == removedCallId) {
            autoStopRecordingIfNeeded()
        }
        if (_waitingCall.value == call) {
            updateWaitingCall(null)
        }
        autoSelectCurrentCall()
    }

    fun autoSelectCurrentCall() {
        val activeCalls = _calls.value.filter { it.safeState != Call.STATE_DISCONNECTED }
        if (activeCalls.isEmpty()) {
            updateCall(null)
            return
        }

        val target = activeCalls.find { it.children.isNotEmpty() || isConference(it) }
            ?: activeCalls.find { it.safeState == Call.STATE_ACTIVE }
            ?: activeCalls.find { it.safeState in listOf(Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_RINGING) }
            ?: activeCalls.find { it.safeState == Call.STATE_HOLDING }
            ?: activeCalls.firstOrNull()

        if (_currentCall.value != target) {
            updateCall(target)
        } else {
            // Re-evaluate conference status
            _isConferenceActive.value = activeCalls.any { isConference(it) } || isConference(target)
        }
    }

    fun updateCall(call: Call?) {
        _currentCall.value = call
        if (call != null) {
            _callState.value = call.safeState
            _isConferenceActive.value = _calls.value.any { it.safeState != Call.STATE_DISCONNECTED && isConference(it) } || isConference(call)
            if (call.safeState == Call.STATE_ACTIVE) {
                if (_activeStartTimestamp.value == 0L) {
                    val connect = call.details?.connectTimeMillis ?: 0L
                    _activeStartTimestamp.value = if (connect > 0L) connect else System.currentTimeMillis()
                }
            } else {
                _activeStartTimestamp.value = 0L
            }

            // NOTE: Nova Dialer deliberately does NOT call unhold() here.
            // When the second call of a swap ends, Telecom restores the remaining call itself.
            // Silently unholding a call the platform put on hold fights Telecom for control and
            // caused the "original call was taken off hold" / "unrelated call hung up" reports.
            // The in-call screen offers an explicit Unhold action driven by CAPABILITY_HOLD and
            // the reported state instead.

            val number = call.details?.handle?.schemeSpecificPart ?: ""
            _callerNumber.value = number
            val cnap = call.details?.callerDisplayName ?: ""

            // 1. Immediate resolution from in-memory ContactCache
            val cachedContact = if (number.isNotEmpty()) ContactCache.getContact(number) else null
            if (cachedContact != null && cachedContact.name.isNotBlank()) {
                _callerName.value = cachedContact.name
                _callerPhotoUri.value = cachedContact.photoUri
                _callerLabel.value = cachedContact.label
            } else if (cnap.isNotBlank()) {
                _callerName.value = cnap
                _callerPhotoUri.value = ""
                _callerLabel.value = ""
            } else {
                val cachedCnap = if (number.isNotEmpty()) ContactCache.getCnapName(number) else null
                _callerName.value = cachedCnap ?: ""
                _callerPhotoUri.value = ""
                _callerLabel.value = ""
            }

            // SIM Slot resolution
            val accountHandle = call.details?.accountHandle
            val ctx = appContext ?: inCallService?.applicationContext
            if (accountHandle != null && ctx != null) {
                try {
                    val activeSims = MultiSimManager.getActiveSimAccounts(ctx)
                    val matchedSim = activeSims.find { it.accountHandle == accountHandle }
                    if (matchedSim != null) {
                        _currentSimSlot.value = matchedSim.slotIndex + 1
                    }
                } catch (_: Exception) {}
            }

            if (cnap.isNotBlank() && number.isNotEmpty()) {
                ContactCache.putCnapName(number, cnap)
                _callerCnapName.value = cnap
                persistCnap(number, cnap)
            } else if (number.isNotEmpty()) {
                val cached = ContactCache.getCnapName(number)
                if (!cached.isNullOrBlank()) {
                    _callerCnapName.value = cached
                } else {
                    resolveCnapAsync(number)
                }
            } else {
                _callerCnapName.value = ""
            }

            // 2. Asynchronous contact resolution via system ContactsProvider if needed
            if (number.isNotEmpty() && (_callerName.value.isEmpty() || _callerName.value == number)) {
                resolveCallerDetailsAsync(number, cnap)
            }
        } else {
            autoStopRecordingIfNeeded()
            stopDtmf()
            _callState.value = Call.STATE_DISCONNECTED
            _callerNumber.value = ""
            _callerName.value = ""
            _callerCnapName.value = ""
            _callerPhotoUri.value = ""
            _callerLabel.value = ""
            _activeStartTimestamp.value = 0L
            _isConferenceActive.value = false
            if (_calls.value.isEmpty()) inCallService = null
        }
        recomputeMultiCallState()
    }

    private fun resolveCallerDetailsAsync(number: String, cnap: String) {
        val ctx = appContext ?: inCallService?.applicationContext ?: return
        scope.launch {
            val resolvedName = getContactNameFromNumber(ctx, number)
            if (!resolvedName.isNullOrBlank()) {
                _callerName.value = resolvedName
                val contact = ContactCache.getContact(number)
                if (contact != null) {
                    if (contact.photoUri.isNotBlank()) _callerPhotoUri.value = contact.photoUri
                    if (contact.label.isNotBlank()) _callerLabel.value = contact.label
                }
                return@launch
            }
            if (cnap.isNotBlank()) {
                _callerName.value = cnap
                return@launch
            }
            val dbCnap = getSavedCnapName(ctx, number)
            if (!dbCnap.isNullOrBlank()) {
                _callerName.value = dbCnap
            }
        }
    }

    private fun persistCnap(number: String, cnap: String) {
        val ctx = appContext ?: inCallService?.applicationContext ?: return
        scope.launch {
            try {
                AppDatabase.getDatabase(ctx).dialerDao().insertSetting(AppSetting("cnap_" + number.filter { it.isDigit() }, cnap))
            } catch (_: Exception) {}
        }
    }

    private fun resolveCnapAsync(number: String) {
        val ctx = appContext ?: inCallService?.applicationContext ?: return
        scope.launch {
            val dbCnap = getSavedCnapName(ctx, number)
            if (!dbCnap.isNullOrBlank()) _callerCnapName.value = dbCnap
        }
    }

    /** Outcome of the one authoritative recording finalizer. */
    enum class RecordingSaveStatus {
        /** File validated, encrypted and recorded in the vault. */
        SAVED_ENCRYPTED,

        /** File validated and recorded, but encryption failed. Never reported as success. */
        SAVED_UNENCRYPTED,

        /** The recorder produced no usable audio. */
        NO_AUDIO,

        /** Database/IO failure; the original file is kept for recovery. */
        FAILED
    }

    data class RecordingSaveResult(
        val status: RecordingSaveStatus,
        val filePath: String? = null
    )

    /** Starts the call-owned recording session. UI, notification and auto-record all use this. */
    fun startCallRecording(context: Context): Boolean {
        // Recording is deferred in simplified v1 and RECORD_AUDIO is not declared.
        if (!ReleaseScope.CALL_RECORDING_ENABLED) return false
        if (CallAudioRecorder.isRecording.value) return false
        val ctx = context.applicationContext
        val prefs = ctx.getSharedPreferences("dialer_prefs", Context.MODE_PRIVATE)
        val chime = prefs.getBoolean("recording_chime_enabled", false)
        val autoTune = prefs.getBoolean("auto_tune_recording_volume", true)

        RecordingFeedbackHelper.triggerRecordingStartFeedback(ctx, chime)
        if (autoTune) {
            try { CallAudioHelper.prepareSpeakerForRecording(ctx, inCallService, _audioState.value) } catch (_: Exception) {}
        }

        val started = CallAudioRecorder.startRecording(ctx, _callerNumber.value.ifEmpty { "Unknown" })
        if (started) {
            recordingCallId = _currentCall.value?.let { callId(it) } ?: -1L
        } else if (autoTune) {
            // Starting failed: do not leave the speaker tuned for a recording that is not running.
            try { CallAudioHelper.restoreAudioState(ctx, inCallService) } catch (_: Exception) {}
        }
        return started
    }

    fun autoStartRecordingIfNeeded() {
        // A restored backup or an older install may still carry the auto-record preference.
        if (!ReleaseScope.CALL_RECORDING_ENABLED) return
        if (CallAudioRecorder.isRecording.value) return
        val ctx = appContext ?: inCallService?.applicationContext ?: return
        val prefs = ctx.getSharedPreferences("dialer_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("is_auto_record_calls_enabled", false)) {
            startCallRecording(ctx)
        }
    }

    /**
     * Stops and finalises the current recording exactly once. Safe to call from the in-call UI
     * being disposed, the notification action, Telecom call removal and service teardown.
     */
    fun stopCallRecording(
        context: Context? = null,
        playFeedback: Boolean = false,
        onComplete: ((RecordingSaveResult) -> Unit)? = null
    ): Job? {
        val ctx = context?.applicationContext ?: appContext ?: inCallService?.applicationContext ?: return null
        synchronized(recordingFinalizationLock) {
            // Duplicate stop requests while a finalisation is running share that same job.
            // A *new* active recorder must not be blocked by a previous finalisation job.
            val running = recordingFinalizationJob
            if (running?.isActive == true && !CallAudioRecorder.isRecording.value) {
                return running
            }

            if (playFeedback && CallAudioRecorder.isRecording.value) {
                val prefs = ctx.getSharedPreferences("dialer_prefs", Context.MODE_PRIVATE)
                RecordingFeedbackHelper.triggerRecordingStopFeedback(
                    ctx,
                    prefs.getBoolean("recording_chime_enabled", false)
                )
            }

            val result = CallAudioRecorder.stopRecording()
            try { CallAudioHelper.restoreAudioState(ctx, inCallService) } catch (_: Exception) {}
            recordingCallId = -1L

            val file = result.file
            if (file == null || !file.exists() || file.length() < MIN_VALID_RECORDING_BYTES) {
                onComplete?.invoke(RecordingSaveResult(RecordingSaveStatus.NO_AUDIO))
                return null
            }
            if (lastFinalizedRecordingPath == file.absolutePath) {
                return null
            }
            lastFinalizedRecordingPath = file.absolutePath

            val durationSec = result.durationSeconds.coerceAtLeast(1L)
            val number = _callerNumber.value.ifEmpty {
                result.phoneNumber.ifEmpty {
                    // Legacy files encoded the number in the name; new RECV_ files do not.
                    val legacyName = file.nameWithoutExtension
                    if (legacyName.startsWith("REC_")) {
                        legacyName.removePrefix("REC_").split("_").firstOrNull()
                            ?.filter { it.isDigit() }?.ifEmpty { "Unknown" } ?: "Unknown"
                    } else {
                        "Unknown"
                    }
                }
            }
            val name = _callerName.value.ifEmpty { number }
            val timestamp = SimpleDateFormat("MMM d, HH:mm", getCurrentLocale(ctx)).format(Date())

            // LAZY guarantees the job is published before it can complete and clear itself.
            val job = scope.launch(start = CoroutineStart.LAZY) {
                val outcome = persistCompletedRecording(ctx, file, number, name, timestamp, durationSec)
                if (onComplete != null) {
                    withContext(Dispatchers.Main.immediate) { onComplete(outcome) }
                }
            }
            recordingFinalizationJob = job
            job.invokeOnCompletion {
                CallAudioRecorder.clearLastResult()
                synchronized(recordingFinalizationLock) {
                    if (recordingFinalizationJob === job) recordingFinalizationJob = null
                }
            }
            job.start()
            return job
        }
    }

    /** Backwards-compatible call-end hook. All work now goes through [stopCallRecording]. */
    fun autoStopRecordingIfNeeded(context: Context? = null): Job? = stopCallRecording(context)

    private suspend fun persistCompletedRecording(
        ctx: Context,
        file: File,
        number: String,
        name: String,
        timestamp: String,
        durationSec: Long
    ): RecordingSaveResult = withContext(Dispatchers.IO) {
        val secureFile = try {
            RecordingCrypto.encryptInPlace(file)
        } catch (_: Exception) {
            null
        }
        val finalFile = secureFile ?: file
        try {
            val recording = CallRecording(
                number = number,
                name = name,
                timestamp = timestamp,
                duration = durationSec,
                filePath = finalFile.absolutePath
            )
            val db = AppDatabase.getDatabase(ctx)
            if (db.dialerDao().getCallRecordingByPath(finalFile.absolutePath) == null) {
                db.dialerDao().insertCallRecording(recording)
            }
            // Never auto-export a file that could not be encrypted; that would copy plaintext
            // call audio outside the app's private storage.
            if (secureFile != null) {
                val autoExport = db.dialerDao()
                    .getSetting("is_auto_export_recordings_enabled")
                    ?.toBooleanStrictOrNull() ?: false
                if (autoExport) CallAudioRecorder.exportRecordingToPublicDownloads(ctx, secureFile)
            }
            RecordingSaveResult(
                if (secureFile != null) RecordingSaveStatus.SAVED_ENCRYPTED
                else RecordingSaveStatus.SAVED_UNENCRYPTED,
                finalFile.absolutePath
            )
        } catch (_: Exception) {
            RecordingSaveResult(RecordingSaveStatus.FAILED, finalFile.absolutePath)
        }
    }

    fun mergeCalls(): Boolean {
        // Recompute from the live Telecom registry rather than trusting the last rendered state.
        val liveSnapshots = liveCallSnapshots()
        val state = MultiCallStateMachine.evaluate(
            calls = liveSnapshots,
            preferredPrimaryId = _currentCall.value?.let { callId(it) },
            waitingCallId = _waitingCall.value?.let { callId(it) }
        )
        if (!state.canMerge) {
            android.util.Log.i("Nova Dialer MultiCall", "merge not supported in phase ${state.phase}")
            return false
        }

        val active = state.primary?.takeIf { it.isActive && !it.isConference }
            ?: state.secondary?.takeIf { it.isActive && !it.isConference }
            ?: return false
        val held = state.primary?.takeIf { it.isHolding && !it.isConference }
            ?: state.secondary?.takeIf { it.isHolding && !it.isConference }
            ?: return false
        if (active.id == held.id) return false

        // Validate against the live Telecom objects one more time before dispatching. This catches
        // a call that disconnected or entered a conference between composition and the tap.
        val activeNow = liveSnapshots.firstOrNull { it.id == active.id } ?: return false
        val heldNow = liveSnapshots.firstOrNull { it.id == held.id } ?: return false
        if (activeNow.state != Call.STATE_ACTIVE || heldNow.state != Call.STATE_HOLDING) return false
        if (!MultiCallStateMachine.canMergePair(activeNow, heldNow)) return false

        val activeCall = callById(active.id) ?: return false
        val heldCall = callById(held.id) ?: return false
        if (activeCall.safeState != Call.STATE_ACTIVE || heldCall.safeState != Call.STATE_HOLDING) {
            return false
        }

        // Key the guard by the unordered pair so a state refresh that swaps active/held roles
        // cannot make the same in-flight merge look like a new request.
        val pair = if (active.id <= held.id) {
            active.id to held.id
        } else {
            held.id to active.id
        }
        synchronized(mergeRequestLock) {
            val now = System.currentTimeMillis()
            if (pendingMergePair == pair && now - pendingMergeAtMs < MERGE_REQUEST_GUARD_MS) {
                android.util.Log.i(
                    "Nova Dialer MultiCall",
                    "ignoring duplicate merge request for calls ${pair.first}/${pair.second}"
                )
                return false
            }
            pendingMergePair = pair
            pendingMergeAtMs = now
        }

        return try {
            // For two ordinary calls this is the supported Telecom operation. Do not retry from
            // the other side: that can submit a second, conflicting conference request.
            activeCall.conference(heldCall)
            android.util.Log.i(
                "Nova Dialer MultiCall",
                "conference request submitted for calls ${pair.first}/${pair.second}; " +
                    "waiting for Telecom to report the conference state"
            )
            true
        } catch (e: Exception) {
            synchronized(mergeRequestLock) {
                if (pendingMergePair == pair) pendingMergePair = null
            }
            android.util.Log.w(
                "Nova Dialer MultiCall",
                "conference request failed for calls ${pair.first}/${pair.second}: " +
                    "${e.javaClass.simpleName}: ${e.message}"
            )
            false
        }
    }

    /**
     * Switches between the connected call and the call on hold.
     *
     * This is only dispatched when exactly one active and one held call exist and both report
     * hold support — the states where Telecom can actually perform the switch. Merging is *not*
     * performed here; the user must ask for it explicitly.
     */
    fun swapCalls(): Boolean {
        val liveSnapshots = liveCallSnapshots()
        val state = MultiCallStateMachine.evaluate(
            calls = liveSnapshots,
            preferredPrimaryId = _currentCall.value?.let { callId(it) },
            waitingCallId = _waitingCall.value?.let { callId(it) }
        )
        if (!state.canSwap) {
            android.util.Log.i("Nova Dialer MultiCall", "swap not supported in phase ${state.phase}")
            return false
        }
        val activeSnapshot = listOfNotNull(state.primary, state.secondary).firstOrNull { it.isActive } ?: return false
        val heldSnapshot = listOfNotNull(state.primary, state.secondary).firstOrNull { it.isHolding } ?: return false
        val activeCall = callById(activeSnapshot.id) ?: return false
        val heldCall = callById(heldSnapshot.id) ?: return false
        if (activeCall.safeState != Call.STATE_ACTIVE || heldCall.safeState != Call.STATE_HOLDING) {
            // State moved on since the button was rendered; stay with what Telecom reports.
            return false
        }
        if (isConference(activeCall) || isConference(heldCall)) return false

        try {
            activeCall.hold()
        } catch (e: Exception) {
            android.util.Log.w(
                "Nova Dialer MultiCall",
                "swap hold failed: ${e.javaClass.simpleName}: ${e.message}"
            )
            return false
        }

        try {
            heldCall.unhold()
            return true
        } catch (e: Exception) {
            android.util.Log.w(
                "Nova Dialer MultiCall",
                "swap unhold failed: ${e.javaClass.simpleName}: ${e.message}"
            )
            return false
        }
    }

    /**
     * Ends a specific call. Defaults to the call the in-call screen is showing.
     *
     * The explicit target prevents notification/quick-decline actions from hanging up an
     * unrelated call (e.g. declining a waiting call must never end the active call).
     */
    fun disconnect(call: Call? = null) {
        val target = call ?: _currentCall.value ?: return
        if (target.safeState == Call.STATE_DISCONNECTED) return
        try {
            if (target.safeState == Call.STATE_RINGING) target.reject(false, null) else target.disconnect()
            if (_calls.value.none { it != target && it.safeState != Call.STATE_DISCONNECTED }) updateCall(null)
        } catch (_: Exception) {}
    }

    /** Ends the call with the given id, if it is still live. */
    fun disconnectById(id: Long): Boolean {
        val call = callById(id) ?: return false
        if (call.safeState == Call.STATE_DISCONNECTED) return false
        disconnect(call)
        return true
    }

    /** Answers the ringing call with the given id; no-op when it already changed state. */
    fun answerCallById(id: Long): Boolean {
        val call = callById(id) ?: return false
        if (call.safeState != Call.STATE_RINGING) return false
        return try {
            call.answer(VideoProfile.STATE_AUDIO_ONLY)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Rejects the ringing call with the given id. Never touches any other call. */
    fun rejectCallById(id: Long): Boolean {
        val call = callById(id) ?: return false
        if (call.safeState != Call.STATE_RINGING) return false
        return try {
            call.reject(false, null)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Answers an incoming call that arrived while another call was live: the active call is put
     * on hold (only when it is actually holdable and connected) and the waiting call is answered.
     */
    fun answerWaitingCall(): Boolean {
        val waiting = _waitingCall.value ?: return false
        if (waiting.safeState != Call.STATE_RINGING) {
            updateWaitingCall(null)
            return false
        }
        _currentCall.value
            ?.takeIf { it !== waiting && it.safeState == Call.STATE_ACTIVE && it.details?.callCapabilities?.let { caps ->
                (caps and (Call.Details.CAPABILITY_HOLD or Call.Details.CAPABILITY_SUPPORT_HOLD)) != 0
            } == true }
            ?.let { runCatching { it.hold() } }
        val answered = try {
            waiting.answer(VideoProfile.STATE_AUDIO_ONLY)
            true
        } catch (_: Exception) {
            false
        }
        if (answered) updateWaitingCall(null)
        return answered
    }

    /** Declines a waiting incoming call while leaving the active call untouched. */
    fun rejectWaitingCall(): Boolean {
        val waiting = _waitingCall.value ?: return false
        val rejected = if (waiting.safeState == Call.STATE_RINGING) {
            try {
                waiting.reject(false, null)
                true
            } catch (_: Exception) {
                false
            }
        } else false
        updateWaitingCall(null)
        return rejected
    }

    fun updateWaitingCall(call: Call?) {
        _waitingCall.value = call
        recomputeMultiCallState()
    }

    fun answerCall(targetCall: Call? = null) {
        val call = targetCall ?: _waitingCall.value ?: _currentCall.value ?: return
        try {
            if (call.safeState == Call.STATE_RINGING) {
                call.answer(VideoProfile.STATE_AUDIO_ONLY)
            }
        } catch (_: Exception) {}
    }

    fun answer() { answerCall() }

    fun rejectCall(targetCall: Call? = null) {
        val call = targetCall ?: _waitingCall.value ?: _currentCall.value ?: return
        // Declining is strictly a "reject this ringing call" operation: never disconnect another
        // call just because it stopped ringing before the user's tap arrived.
        if (call.safeState != Call.STATE_RINGING) return
        try {
            call.reject(false, null)
        } catch (_: Exception) {}
    }

    fun setMuted(muted: Boolean) { inCallService?.setMuted(muted) }

    /**
     * Speaker/handset toggle used by the notification actions and the Dynamic Island pill.
     * The request is validated against the routes Telecom actually offers; on failure nothing is
     * changed and the UI keeps showing the real route.
     */
    fun setSpeaker(speaker: Boolean) {
        requestAudioRoute(if (speaker) AudioRoute.SPEAKER else AudioRoute.EARPIECE)
    }

    /**
     * Holds or unholds the call the in-call screen is showing, honouring the capabilities
     * Telecom reports for it.
     */
    fun setHold(hold: Boolean) {
        val state = _multiCallState.value
        val target = _currentCall.value ?: return
        if (target.safeState == Call.STATE_DISCONNECTED) return
        if (hold) {
            if (!state.canHoldPrimary) return
            runCatching { target.hold() }
        } else {
            if (!state.canUnholdPrimary && target.safeState != Call.STATE_HOLDING) return
            runCatching { target.unhold() }
        }
    }

    fun silenceRinger(context: Context) {
        MyInCallService.silenceRingtone()
        @SuppressLint("MissingPermission")
        try {
            (context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.silenceRinger()
        } catch (_: SecurityException) {
            // Not permitted on this device; ignore.
        }
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            audioManager?.ringerMode = android.media.AudioManager.RINGER_MODE_SILENT
        } catch (_: Exception) {}
    }

    fun playDtmf(key: Char) {
        val call = _currentCall.value ?: return
        dtmfJob?.cancel()
        try { call.playDtmfTone(key) } catch (_: Exception) {}
        dtmfJob = scope.launch(Dispatchers.Default) {
            delay(150.milliseconds)
            try { call.stopDtmfTone() } catch (_: Exception) {}
        }
    }

    fun stopDtmf() {
        dtmfJob?.cancel()
        try { _currentCall.value?.stopDtmfTone() } catch (_: Exception) {}
    }

    fun formatOutgoingNumberWithClir(number: String, isHideCallerId: Boolean, clirPrefix: String): String {
        if (!isHideCallerId || number.isBlank() || isEmergencyNumber(number)) return number
        val clir = clirPrefix.trim().ifBlank { "#31#" }
        return if (number.startsWith(clir)) number else "$clir$number"
    }

    fun isEmergencyNumber(number: String): Boolean {
        val trimmed = number.trim()
        if (trimmed.isBlank()) return false
        val digits = trimmed.filter { it.isDigit() }
        val standardEmergency = setOf("911", "112", "999", "000", "108", "110", "119", "995", "100", "101", "102")
        if (digits in standardEmergency) return true
        if (digits.length > 3 && (digits.startsWith("1") || digits.startsWith("0"))) {
            val withoutPrefix = digits.substring(1)
            if (withoutPrefix in standardEmergency) return true
        }
        return try {
            val context = appContext ?: return false
            PhoneNumberCompat.isEmergencyNumber(context, trimmed)
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun placeCall(context: Context, number: String, preferredSim: String = "Ask"): Boolean {
        appContext = context.applicationContext
        val trimmed = number.trim()
        if (trimmed.isBlank()) return false

        // Guard against duplicate submissions from rapid taps / re-composed buttons. Telecom would
        // otherwise be asked to place the same call twice, which some stacks turn into an
        // immediate second dial that cancels the first.
        val now = System.currentTimeMillis()
        if (trimmed == lastPlacedNumber && now - lastPlacedAtMs < DUPLICATE_CALL_WINDOW_MS) {
            android.util.Log.i("Nova Dialer MultiCall", "ignoring duplicate placeCall(${com.example.util.LogRedaction.redactPhone(trimmed)}) within window")
            return false
        }
        // The in-call UI only offers Add Call when a second call is allowed; enforce it here too.
        if (_multiCallState.value.liveCallCount >= 2) {
            android.util.Log.i("Nova Dialer MultiCall", "refusing placeCall: ${_multiCallState.value.liveCallCount} live calls")
            return false
        }
        lastPlacedNumber = trimmed
        lastPlacedAtMs = now

        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.let { imm ->
            (context as? Activity)?.currentFocus?.windowToken?.let { imm.hideSoftInputFromWindow(it, 0) }
        }

        val isEmergency = isEmergencyNumber(trimmed)
        // Resolve the SIM account from its label first, then derive the slot from the account, so a
        // carrier name that merely contains a digit can never route the call to the wrong SIM.
        val matchedAccount = if (!isEmergency && preferredSim != "Ask") {
            findPhoneAccountForSlot(context, preferredSim)
        } else {
            null
        }
        val targetSlot = matchedAccount?.slotIndex?.plus(1)
            ?: if (preferredSim.endsWith("2")) 2 else 1
        _currentSimSlot.value = targetSlot
        SimCallTracker.recordOutgoingCall(context, trimmed, targetSlot)

        // Placing a second call: put the connected call on hold first. Telecom will also do this,
        // but doing it here keeps the UI in step and matches the AOSP dialer behaviour.
        _currentCall.value
            ?.takeIf {
                it.safeState == Call.STATE_ACTIVE &&
                    it.details?.callCapabilities?.let { caps ->
                        (caps and (Call.Details.CAPABILITY_HOLD or Call.Details.CAPABILITY_SUPPORT_HOLD)) != 0
                    } != false
            }
            ?.let { runCatching { it.hold() } }

        val prefs = context.getSharedPreferences("dialer_prefs", Context.MODE_PRIVATE)
        val hideId = prefs.getBoolean("is_hide_caller_id_enabled", false)
        val clirPrefix = prefs.getString("clir_prefix", "#31#") ?: "#31#"
        val dialedNumber = formatOutgoingNumberWithClir(trimmed, hideId, clirPrefix)

        val uri = Uri.fromParts("tel", dialedNumber, null)
        val extras = Bundle()

        val accountHandle = matchedAccount?.accountHandle
        if (!isEmergency && preferredSim != "Ask" && matchedAccount != null && accountHandle != null) {
            // Official AOSP Telecom binding
            extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accountHandle)

            // Inject OEM-specific Multi-SIM extras so Samsung, Xiaomi, and MediaTek modems don't drop back to SIM 1
            extras.putInt("android.telephony.extra.SUBSCRIPTION_INDEX", matchedAccount.subscriptionId)
            extras.putInt("subscription", matchedAccount.subscriptionId)
            extras.putInt("phone_subscription", matchedAccount.subscriptionId)
            extras.putInt("slot", matchedAccount.slotIndex)
            extras.putInt("simSlot", matchedAccount.slotIndex)
            extras.putInt("com.android.phone.extra.slot", matchedAccount.slotIndex)
        }

        val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        var placedSuccessfully = false
        if (tm != null) {
            try {
                tm.placeCall(uri, extras)
                placedSuccessfully = true
            } catch (_: Exception) {
                placedSuccessfully = false
            }
        }

        if (!placedSuccessfully) {
            try {
                val callIntent = Intent(Intent.ACTION_CALL, uri).apply {
                    if (extras.size() > 0) putExtras(extras)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(callIntent)
            } catch (_: Exception) {
                try {
                    val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(dialIntent)
                } catch (_: Exception) {}
            }
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun findPhoneAccountForSlot(context: Context, preferredSimLabel: String): com.example.util.SimAccountInfo? {
        val simAccounts = MultiSimManager.getCallableSims(context)
        if (simAccounts.isEmpty()) return null

        val label = preferredSimLabel.trim()
        simAccounts.find {
            it.displayName.equals(label, ignoreCase = true) || it.carrierName.equals(label, ignoreCase = true)
        }?.let { return it }

        // "SIM 2" / "sim2" / "2" → hardware slot index 1.
        val slotNumber = Regex("\\d+").find(label)?.value?.toIntOrNull()
        if (slotNumber != null) {
            simAccounts.find { it.slotIndex == slotNumber - 1 }?.let { return it }
        }
        return simAccounts.firstOrNull()
    }

    fun rejectCallWithMessage(context: Context, number: String, textMessage: String) {
        val call = _currentCall.value
        // An active ringing call: let the platform decline-with-message on our behalf
        if (call != null && call.safeState == Call.STATE_RINGING) {
            try {
                call.reject(true, textMessage)
                Toast.makeText(context, context.getString(R.string.sms_sent), Toast.LENGTH_SHORT).show()
                return
            } catch (_: Exception) {}
        }

        call?.disconnect()

        // A decline-only quick reply (toggle off) must not open an SMS composer.
        val directSmsEnabled = context
            .getSharedPreferences("dialer_prefs", Context.MODE_PRIVATE)
            .getBoolean("quick_reply_direct_sms", true)
        if (!directSmsEnabled) return

        // Nova Dialer is a dialer, not the default SMS handler. Sending silently would require
        // the restricted SEND_SMS permission, so hand the message to the user's SMS app
        // (no permission required, no Google Play SMS-policy risk).
        try {
            val intent = Intent(Intent.ACTION_SENDTO, "smsto:$number".toUri()).apply {
                putExtra("sms_body", textMessage)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.sms_failed), Toast.LENGTH_SHORT).show()
        }
    }
}
