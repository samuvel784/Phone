/*
 * Copyright (C) 2026 NovaDial contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import android.telecom.CallAudioState

/**
 * Platform independent description of a call audio route.
 *
 * Telecom exposes call audio through two different APIs depending on the Android version:
 *
 *  * API 24..33  – [CallAudioState] (`InCallService.onCallAudioStateChanged` / `setAudioRoute`).
 *    Available routes are described by [CallAudioState.getSupportedRouteMask].
 *  * API 34+     – [android.telecom.CallEndpoint] (`onAvailableCallEndpointsChanged`,
 *    `onCallEndpointChanged`, `requestCallEndpointChange`). Available routes are described by
 *    the endpoints Telecom reports for the *live call*, which is the only reliable way to know
 *    whether e.g. Bluetooth can actually carry call audio right now.
 *
 * [AudioRoutePolicy] converts both representations into the same model so the UI can be driven
 * by one source of truth and never lies about the route that is actually active.
 */
enum class AudioRoute {
    EARPIECE,
    SPEAKER,
    BLUETOOTH,
    WIRED_HEADSET,
    STREAMING,
    UNKNOWN
}

/** One selectable call audio endpoint as reported to us by Telecom. */
data class AudioEndpointInfo(
    /** Stable per-device identifier (`CallEndpoint.getIdentifier()`), used to request a change. */
    val id: String,
    val route: AudioRoute
)

/**
 * The audio routing state for the current call, derived from whichever API the device supports.
 *
 * @param currentRoute the route Telecom reports as *actually active* (never the last tapped button).
 * @param availableRoutes routes the platform currently offers for this call.
 * @param isMuted whether the platform reports the microphone as muted.
 * @param usesEndpoints true when the values came from API 34+ call endpoints.
 */
data class CallAudioRouteState(
    val currentRoute: AudioRoute = AudioRoute.UNKNOWN,
    val availableRoutes: Set<AudioRoute> = emptySet(),
    val isMuted: Boolean = false,
    val usesEndpoints: Boolean = false
) {
    fun isAvailable(route: AudioRoute): Boolean = route in availableRoutes
    fun isSelected(route: AudioRoute): Boolean = currentRoute == route

    val hasBluetooth: Boolean get() = AudioRoute.BLUETOOTH in availableRoutes
    val hasSpeaker: Boolean get() = AudioRoute.SPEAKER in availableRoutes
    val hasWiredHeadset: Boolean get() = AudioRoute.WIRED_HEADSET in availableRoutes

    /** Speaker and Bluetooth are mutually exclusive; the platform decides which one wins. */
    val isSpeakerActive: Boolean get() = currentRoute == AudioRoute.SPEAKER
    val isBluetoothActive: Boolean get() = currentRoute == AudioRoute.BLUETOOTH
    val isEarpieceActive: Boolean get() = currentRoute == AudioRoute.EARPIECE
    val isWiredHeadsetActive: Boolean get() = currentRoute == AudioRoute.WIRED_HEADSET

    /** True while the user is on a hands-free route (speaker, Bluetooth or a wired headset). */
    val isHandsFree: Boolean
        get() = currentRoute == AudioRoute.SPEAKER ||
                currentRoute == AudioRoute.BLUETOOTH ||
                currentRoute == AudioRoute.WIRED_HEADSET ||
                currentRoute == AudioRoute.STREAMING

    companion object {
        /** Conservative fallback when the platform reports no route information at all. */
        val Unknown = CallAudioRouteState()
    }
}

/**
 * Pure conversion/dispatch rules for call audio routing. Contains no Android service calls so it
 * can be unit tested on the JVM for every API-version branch.
 */
object AudioRoutePolicy {

    // android.telecom.CallEndpoint endpoint types (API 34+), duplicated as plain ints so this
    // file stays loadable on API 24 where CallEndpoint does not exist.
    const val ENDPOINT_TYPE_UNKNOWN = 0
    const val ENDPOINT_TYPE_EARPIECE = 1
    const val ENDPOINT_TYPE_BLUETOOTH = 2
    const val ENDPOINT_TYPE_WIRED_HEADSET = 3
    const val ENDPOINT_TYPE_SPEAKER = 4
    const val ENDPOINT_TYPE_STREAMING = 5

    /**
     * Routes that are physically present on every telephony device and can therefore be assumed
     * to exist when the platform does not tell us better. Bluetooth is deliberately excluded:
     * a paired headset does not prove it supports call audio.
     */
    private val ALWAYS_PRESENT = setOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER)

    fun routeForEndpointType(type: Int): AudioRoute = when (type) {
        ENDPOINT_TYPE_EARPIECE -> AudioRoute.EARPIECE
        ENDPOINT_TYPE_BLUETOOTH -> AudioRoute.BLUETOOTH
        ENDPOINT_TYPE_WIRED_HEADSET -> AudioRoute.WIRED_HEADSET
        ENDPOINT_TYPE_SPEAKER -> AudioRoute.SPEAKER
        ENDPOINT_TYPE_STREAMING -> AudioRoute.STREAMING
        else -> AudioRoute.UNKNOWN
    }

    fun endpointTypeFor(route: AudioRoute): Int = when (route) {
        AudioRoute.EARPIECE -> ENDPOINT_TYPE_EARPIECE
        AudioRoute.BLUETOOTH -> ENDPOINT_TYPE_BLUETOOTH
        AudioRoute.WIRED_HEADSET -> ENDPOINT_TYPE_WIRED_HEADSET
        AudioRoute.SPEAKER -> ENDPOINT_TYPE_SPEAKER
        AudioRoute.STREAMING -> ENDPOINT_TYPE_STREAMING
        AudioRoute.UNKNOWN -> ENDPOINT_TYPE_UNKNOWN
    }

    fun routeForLegacyRoute(route: Int): AudioRoute = when (route) {
        CallAudioState.ROUTE_EARPIECE -> AudioRoute.EARPIECE
        CallAudioState.ROUTE_BLUETOOTH -> AudioRoute.BLUETOOTH
        CallAudioState.ROUTE_WIRED_HEADSET -> AudioRoute.WIRED_HEADSET
        CallAudioState.ROUTE_SPEAKER -> AudioRoute.SPEAKER
        else -> AudioRoute.UNKNOWN
    }

    /** Maps our model back onto the deprecated [CallAudioState] route constants. */
    fun legacyRouteFor(route: AudioRoute): Int = when (route) {
        AudioRoute.EARPIECE -> CallAudioState.ROUTE_EARPIECE
        AudioRoute.BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
        AudioRoute.WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
        AudioRoute.SPEAKER -> CallAudioState.ROUTE_SPEAKER
        AudioRoute.STREAMING -> CallAudioState.ROUTE_SPEAKER
        AudioRoute.UNKNOWN -> CallAudioState.ROUTE_EARPIECE
    }

    /**
     * Builds the routing state from the legacy [CallAudioState] (API 24..33, and as a fallback on
     * API 34+ if endpoints are unavailable).
     */
    fun fromLegacy(route: Int?, supportedRouteMask: Int, isMuted: Boolean): CallAudioRouteState {
        val current = route?.let { routeForLegacyRoute(it) } ?: AudioRoute.UNKNOWN
        val mask = supportedRouteMask and ROUTE_MASK_ALL
        var available = routesInMask(mask)
        if (available.isEmpty()) {
            // Some OEM stacks report 0. Earpiece and speaker always exist in a voice call.
            available = ALWAYS_PRESENT
        }
        if (current != AudioRoute.UNKNOWN) available = available + current
        return CallAudioRouteState(
            currentRoute = current,
            availableRoutes = available,
            isMuted = isMuted,
            usesEndpoints = false
        )
    }

    /**
     * Builds the routing state from API 34+ call endpoints.
     *
     * Only endpoints Telecom reported for the live call are considered available, so a paired
     * Bluetooth headset that cannot carry call audio never shows up as selectable.
     */
    fun fromEndpoints(
        current: AudioEndpointInfo?,
        available: List<AudioEndpointInfo>,
        isMuted: Boolean
    ): CallAudioRouteState {
        val availableRoutes = LinkedHashSet<AudioRoute>()
        available.forEach { endpoint ->
            if (endpoint.route != AudioRoute.UNKNOWN) availableRoutes.add(endpoint.route)
        }
        if (availableRoutes.isEmpty()) {
            availableRoutes.addAll(ALWAYS_PRESENT)
        }
        val currentRoute = current?.route ?: AudioRoute.UNKNOWN
        if (currentRoute != AudioRoute.UNKNOWN) availableRoutes.add(currentRoute)
        return CallAudioRouteState(
            currentRoute = currentRoute,
            availableRoutes = availableRoutes,
            isMuted = isMuted,
            usesEndpoints = true
        )
    }

    /** Picks the endpoint Telecom should be asked to switch to for [target]. */
    fun findEndpointFor(
        target: AudioRoute,
        available: List<AudioEndpointInfo>
    ): AudioEndpointInfo? = available.firstOrNull { it.route == target }

    /** Target route for the Bluetooth button: Bluetooth when available, otherwise no change. */
    fun bluetoothToggleTarget(state: CallAudioRouteState): AudioRoute? {
        if (!state.hasBluetooth) return null
        // Toggling off Bluetooth falls back to the wired headset when one is present, else earpiece.
        return if (state.isBluetoothActive) {
            if (state.hasWiredHeadset) AudioRoute.WIRED_HEADSET else AudioRoute.EARPIECE
        } else {
            AudioRoute.BLUETOOTH
        }
    }

    /** Target route for the speaker button, honouring a connected Bluetooth/wired device. */
    fun speakerToggleTarget(state: CallAudioRouteState): AudioRoute {
        if (state.isSpeakerActive) {
            return when {
                state.hasBluetooth -> AudioRoute.BLUETOOTH
                state.hasWiredHeadset -> AudioRoute.WIRED_HEADSET
                else -> AudioRoute.EARPIECE
            }
        }
        return AudioRoute.SPEAKER
    }

    private const val ROUTE_MASK_ALL = CallAudioState.ROUTE_EARPIECE or
            CallAudioState.ROUTE_BLUETOOTH or
            CallAudioState.ROUTE_WIRED_HEADSET or
            CallAudioState.ROUTE_SPEAKER

    private fun routesInMask(mask: Int): Set<AudioRoute> {
        val out = LinkedHashSet<AudioRoute>()
        if (mask and CallAudioState.ROUTE_EARPIECE != 0) out.add(AudioRoute.EARPIECE)
        if (mask and CallAudioState.ROUTE_BLUETOOTH != 0) out.add(AudioRoute.BLUETOOTH)
        if (mask and CallAudioState.ROUTE_WIRED_HEADSET != 0) out.add(AudioRoute.WIRED_HEADSET)
        if (mask and CallAudioState.ROUTE_SPEAKER != 0) out.add(AudioRoute.SPEAKER)
        return out
    }
}
