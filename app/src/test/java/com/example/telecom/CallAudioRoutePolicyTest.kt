/*
 * Copyright (C) 2026 NovaDial contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import android.telecom.CallAudioState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the call-audio routing policy shared by the API 34+ endpoint path and the legacy
 * [CallAudioState] path.
 *
 * The key product rule verified here: Bluetooth is only offered when the platform actually reports
 * a Bluetooth **call-audio** route/endpoint for the live call — a merely paired headset never turns
 * the button on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CallAudioRoutePolicyTest {

    private fun endpoint(route: AudioRoute, id: String = route.name) = AudioEndpointInfo(id, route)

    @Test
    fun endpointTypeMappingIsSymmetric() {
        val routes = listOf(
            AudioRoute.EARPIECE,
            AudioRoute.SPEAKER,
            AudioRoute.BLUETOOTH,
            AudioRoute.WIRED_HEADSET,
            AudioRoute.STREAMING
        )
        for (route in routes) {
            assertEquals(route, AudioRoutePolicy.routeForEndpointType(AudioRoutePolicy.endpointTypeFor(route)))
        }
        assertEquals(AudioRoute.UNKNOWN, AudioRoutePolicy.routeForEndpointType(AudioRoutePolicy.ENDPOINT_TYPE_UNKNOWN))
    }

    @Test
    fun bluetoothIsAvailableOnlyWhenAnEndpointIsReported() {
        val withBluetooth = AudioRoutePolicy.fromEndpoints(
            current = endpoint(AudioRoute.EARPIECE),
            available = listOf(endpoint(AudioRoute.EARPIECE), endpoint(AudioRoute.SPEAKER), endpoint(AudioRoute.BLUETOOTH)),
            isMuted = false
        )
        assertTrue(withBluetooth.hasBluetooth)
        assertTrue(withBluetooth.usesEndpoints)

        val withoutBluetooth = AudioRoutePolicy.fromEndpoints(
            current = endpoint(AudioRoute.EARPIECE),
            available = listOf(endpoint(AudioRoute.EARPIECE), endpoint(AudioRoute.SPEAKER)),
            isMuted = false
        )
        assertFalse("A paired-but-not-endpoint headset must not enable Bluetooth", withoutBluetooth.hasBluetooth)
    }

    @Test
    fun selectedRouteReflectsTheReportedEndpointNotARequest() {
        val state = AudioRoutePolicy.fromEndpoints(
            current = endpoint(AudioRoute.SPEAKER),
            available = listOf(endpoint(AudioRoute.EARPIECE), endpoint(AudioRoute.SPEAKER)),
            isMuted = false
        )
        assertTrue(state.isSpeakerActive)
        assertFalse(state.isEarpieceActive)
    }

    @Test
    fun emptyEndpointListFallsBackToEarpieceAndSpeaker() {
        val state = AudioRoutePolicy.fromEndpoints(current = null, available = emptyList(), isMuted = false)
        assertTrue(state.availableRoutes.contains(AudioRoute.EARPIECE))
        assertTrue(state.availableRoutes.contains(AudioRoute.SPEAKER))
        assertFalse("Bluetooth must never be assumed", state.hasBluetooth)
    }

    @Test
    fun legacyMaskIsTranslated() {
        val mask = CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER or CallAudioState.ROUTE_BLUETOOTH
        val state = AudioRoutePolicy.fromLegacy(CallAudioState.ROUTE_BLUETOOTH, mask, isMuted = true)
        assertTrue(state.hasBluetooth)
        assertTrue(state.isBluetoothActive)
        assertTrue(state.isMuted)
        assertFalse(state.usesEndpoints)
    }

    @Test
    fun legacyZeroMaskStillOffersEarpieceAndSpeaker() {
        val state = AudioRoutePolicy.fromLegacy(route = null, supportedRouteMask = 0, isMuted = false)
        assertEquals(AudioRoute.UNKNOWN, state.currentRoute)
        assertTrue(state.availableRoutes.contains(AudioRoute.EARPIECE))
        assertTrue(state.availableRoutes.contains(AudioRoute.SPEAKER))
        assertFalse(state.hasBluetooth)
    }

    @Test
    fun bluetoothToggleTargetIsNullWhenUnavailable() {
        val state = AudioRoutePolicy.fromEndpoints(
            current = endpoint(AudioRoute.EARPIECE),
            available = listOf(endpoint(AudioRoute.EARPIECE), endpoint(AudioRoute.SPEAKER)),
            isMuted = false
        )
        assertNull(AudioRoutePolicy.bluetoothToggleTarget(state))
    }

    @Test
    fun bluetoothToggleTargetSwitchesInAndOut() {
        val onEarpiece = AudioRoutePolicy.fromEndpoints(
            current = endpoint(AudioRoute.EARPIECE),
            available = listOf(endpoint(AudioRoute.EARPIECE), endpoint(AudioRoute.BLUETOOTH)),
            isMuted = false
        )
        assertEquals(AudioRoute.BLUETOOTH, AudioRoutePolicy.bluetoothToggleTarget(onEarpiece))

        val onBluetooth = AudioRoutePolicy.fromEndpoints(
            current = endpoint(AudioRoute.BLUETOOTH),
            available = listOf(endpoint(AudioRoute.EARPIECE), endpoint(AudioRoute.BLUETOOTH)),
            isMuted = false
        )
        assertEquals(AudioRoute.EARPIECE, AudioRoutePolicy.bluetoothToggleTarget(onBluetooth))
    }

    @Test
    fun bluetoothToggleOffPrefersWiredHeadset() {
        val state = AudioRoutePolicy.fromEndpoints(
            current = endpoint(AudioRoute.BLUETOOTH),
            available = listOf(
                endpoint(AudioRoute.EARPIECE),
                endpoint(AudioRoute.BLUETOOTH),
                endpoint(AudioRoute.WIRED_HEADSET)
            ),
            isMuted = false
        )
        assertEquals(AudioRoute.WIRED_HEADSET, AudioRoutePolicy.bluetoothToggleTarget(state))
    }

    @Test
    fun speakerToggleReturnsToTheBestHandsFreeRouteWhenAlreadyOnSpeaker() {
        val withBluetooth = AudioRoutePolicy.fromEndpoints(
            current = endpoint(AudioRoute.SPEAKER),
            available = listOf(endpoint(AudioRoute.EARPIECE), endpoint(AudioRoute.SPEAKER), endpoint(AudioRoute.BLUETOOTH)),
            isMuted = false
        )
        assertEquals(AudioRoute.BLUETOOTH, AudioRoutePolicy.speakerToggleTarget(withBluetooth))

        val earpieceOnly = AudioRoutePolicy.fromEndpoints(
            current = endpoint(AudioRoute.SPEAKER),
            available = listOf(endpoint(AudioRoute.EARPIECE), endpoint(AudioRoute.SPEAKER)),
            isMuted = false
        )
        assertEquals(AudioRoute.EARPIECE, AudioRoutePolicy.speakerToggleTarget(earpieceOnly))
    }

    @Test
    fun speakerToggleTurnsSpeakerOnWhenNotOnSpeaker() {
        val state = AudioRoutePolicy.fromEndpoints(
            current = endpoint(AudioRoute.EARPIECE),
            available = listOf(endpoint(AudioRoute.EARPIECE), endpoint(AudioRoute.SPEAKER)),
            isMuted = false
        )
        assertEquals(AudioRoute.SPEAKER, AudioRoutePolicy.speakerToggleTarget(state))
    }

    @Test
    fun findEndpointForMatchesRoute() {
        val endpoints = listOf(endpoint(AudioRoute.EARPIECE, "1"), endpoint(AudioRoute.BLUETOOTH, "2"))
        assertEquals("2", AudioRoutePolicy.findEndpointFor(AudioRoute.BLUETOOTH, endpoints)?.id)
        assertNull(AudioRoutePolicy.findEndpointFor(AudioRoute.SPEAKER, endpoints))
    }

    @Test
    fun handsFreeRoutesAreRecognised() {
        fun state(route: AudioRoute) = CallAudioRouteState(currentRoute = route, availableRoutes = setOf(route))
        assertFalse(state(AudioRoute.EARPIECE).isHandsFree)
        assertTrue(state(AudioRoute.SPEAKER).isHandsFree)
        assertTrue(state(AudioRoute.BLUETOOTH).isHandsFree)
        assertTrue(state(AudioRoute.WIRED_HEADSET).isHandsFree)
    }
}
