/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example

import android.app.Application
import android.telecom.Call
import androidx.test.core.app.ApplicationProvider
import com.example.telecom.AudioRoute
import com.example.telecom.CallAudioRouteState
import com.example.telecom.CallTimingLogger
import com.example.telecom.MultiCallPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies two safety-critical guarantees that do not need a real (unconstructable) Telecom [Call]:
 *
 *  * Notification actions are targeted by call id, and an unknown/stale id can never touch a live
 *    call — declining a waiting call must not hang up the active call.
 *  * The incoming-call timing trace records the app-side path so the 2–3 s delay can be attributed
 *    honestly rather than assumed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CallTargetingAndTimingTest {

    private lateinit var context: Application

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        CallManager.updateCall(null)
        CallManager.updateWaitingCall(null)
        CallTimingLogger.clear()
    }

    @Test
    fun unknownCallIdNeverResolvesToACall() {
        assertNull(CallManager.callById(4242L))
    }

    @Test
    fun answerRejectAndDisconnectWithUnknownIdAreSafeNoOps() {
        assertFalse("answer with a stale id must be refused", CallManager.answerCallById(4242L))
        assertFalse("reject with a stale id must be refused", CallManager.rejectCallById(4242L))
        assertFalse("disconnect with a stale id must be refused", CallManager.disconnectById(4242L))
    }

    @Test
    fun decliningWithNoWaitingCallLeavesEverythingUntouched() {
        assertFalse(CallManager.rejectWaitingCall())
        assertFalse(CallManager.answerWaitingCall())
    }

    @Test
    fun multiCallStateStartsIdleAndRouteStateStartsUnknown() {
        assertEquals(MultiCallPhase.IDLE, CallManager.multiCallState.value.phase)
        assertEquals(0, CallManager.multiCallState.value.liveCallCount)
        assertFalse(CallManager.multiCallState.value.canSwap)
        assertFalse(CallManager.multiCallState.value.canMerge)
        assertEquals(CallAudioRouteState.Unknown, CallManager.routeState.value)
    }

    @Test
    fun swapAndMergeAreRefusedWithoutLiveCalls() {
        assertFalse(CallManager.swapCalls())
        assertFalse(CallManager.mergeCalls())
    }

    @Test
    fun routeRequestIsRefusedWhenRouteIsNotAvailable() {
        // Unknown route is never dispatchable.
        assertFalse(CallManager.requestAudioRoute(AudioRoute.UNKNOWN))
        // Bluetooth is not offered by an unknown/empty route state, so the request must be refused
        // rather than dispatched hopefully.
        assertFalse(CallManager.requestAudioRoute(AudioRoute.BLUETOOTH))
    }

    @Test
    fun blankNumberIsNeverPlacedAsACall() {
        assertFalse(CallManager.placeCall(context, "   "))
    }

    @Test
    fun timingTraceCapturesTheIncomingPath() {
        val trace = CallTimingLogger.begin(1L, "+15551234567", isIncoming = true)
        assertTrue(CallTimingLogger.activeTrace() === trace)
        trace.mark("wakeLockAcquired")
        trace.mark(CallTimingLogger.Event.NOTIFICATION_POSTED)
        CallTimingLogger.markActive(CallTimingLogger.Event.FIRST_FRAME)
        CallTimingLogger.finishActive()

        assertNull("A finished trace is no longer active", CallTimingLogger.activeTrace())
        val reports = CallTimingLogger.recentReports()
        assertEquals(1, reports.size)

        val report = reports.first()
        // Diagnostics keep only a masked identifier; the full number must never be retained.
        assertEquals("***4567", report.number)
        assertTrue(report.isIncoming)
        assertNotNull(report.offsetOf(CallTimingLogger.Event.ON_CALL_ADDED))
        assertNotNull(report.offsetOf("wakeLockAcquired"))
        assertNotNull(report.offsetOf(CallTimingLogger.Event.NOTIFICATION_POSTED))
        assertNotNull(report.offsetOf(CallTimingLogger.Event.FIRST_FRAME))
        assertTrue("every mark is measured relative to onCallAdded", report.totalMs >= 0L)
        assertFalse(
            "the formatted report must not contain the full number",
            report.format().contains("+15551234567")
        )
        assertTrue(report.format().contains("***4567"))
    }

    @Test
    fun reportFormatRedactsEvenWhenConstructedWithARawNumber() {
        val report = CallTimingLogger.Report(
            number = "+15551234567",
            isIncoming = true,
            startedAtUptimeMs = 0L,
            marks = listOf(CallTimingLogger.Mark(CallTimingLogger.Event.ON_CALL_ADDED, 0L))
        )

        val formatted = report.format()
        assertFalse("a full number must never be rendered", formatted.contains("+15551234567"))
        assertTrue(formatted.contains("***4567"))
    }

    @Test
    fun timingReportsAreBounded() {
        repeat(20) { index ->
            val trace = CallTimingLogger.begin(index.toLong(), "num$index", isIncoming = true)
            trace.mark("x")
            CallTimingLogger.finishActive()
        }
        assertTrue("only the most recent traces are retained", CallTimingLogger.recentReports().size <= 12)
    }

    @Test
    fun timingTraceForOutgoingCallIsMarkedAsNotIncoming() {
        val trace = CallTimingLogger.begin(9L, "555", isIncoming = false)
        trace.mark("x")
        CallTimingLogger.finishActive()
        assertFalse(CallTimingLogger.recentReports().first().isIncoming)
    }
}
