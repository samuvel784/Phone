package com.example.telecom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingActionExecutorTest {

    private class Harness(
        initialRecording: Boolean = false,
        var liveCall: Boolean = true,
        var microphoneGranted: Boolean = true,
        var disclosureAcknowledged: Boolean = true,
        var startSucceeds: Boolean = true
    ) {
        var recording = initialRecording
        var startCalls = 0
        var stopCalls = 0
        var disclosureCalls = 0
        var permissionCalls = 0
        var startFailureCalls = 0
        var refreshCalls = 0

        val executor = RecordingActionExecutor(
            state = {
                RecordingActionState(
                    isRecording = recording,
                    hasLiveCall = liveCall,
                    microphoneGranted = microphoneGranted,
                    disclosureAcknowledged = disclosureAcknowledged
                )
            },
            startRecording = {
                startCalls++
                if (startSucceeds) {
                    recording = true
                    true
                } else {
                    false
                }
            },
            stopRecording = {
                stopCalls++
                recording = false
            },
            onShowDisclosure = { disclosureCalls++ },
            onPermissionRequired = { permissionCalls++ },
            onStartFailed = { startFailureCalls++ },
            refreshNotification = { refreshCalls++ }
        )
    }

    @Test
    fun startFromNotificationThenInCallStopMakesTheNextActionStart() {
        val harness = Harness()

        assertEquals(
            RecordingActionResult.STARTED,
            harness.executor.execute(RecordingNotificationAction.START)
        )
        assertEquals(
            RecordingNotificationAction.STOP,
            NotificationRecordingPolicy.actionFor(harness.recording)
        )

        // The in-call UI and Dynamic Island stop through the same recorder state.
        harness.recording = false

        assertEquals(
            RecordingNotificationAction.START,
            NotificationRecordingPolicy.actionFor(harness.recording)
        )
        assertEquals(1, harness.startCalls)
        assertEquals(0, harness.stopCalls)
    }

    @Test
    fun staleStopWhenIdleNeverStartsRecording() {
        val harness = Harness()

        assertEquals(
            RecordingActionResult.NOT_RECORDING,
            harness.executor.execute(RecordingNotificationAction.STOP)
        )
        assertEquals(0, harness.startCalls)
        assertEquals(0, harness.stopCalls)
        assertFalse(harness.recording)
        assertEquals(1, harness.refreshCalls)
    }

    @Test
    fun staleStartWhileRecordingNeverStopsRecording() {
        val harness = Harness(initialRecording = true)

        assertEquals(
            RecordingActionResult.ALREADY_RECORDING,
            harness.executor.execute(RecordingNotificationAction.START)
        )
        assertEquals(0, harness.startCalls)
        assertEquals(0, harness.stopCalls)
        assertTrue(harness.recording)
        assertEquals(1, harness.refreshCalls)
    }

    @Test
    fun stopActionStopsOnlyAnActiveRecording() {
        val harness = Harness(initialRecording = true)

        assertEquals(
            RecordingActionResult.STOPPED,
            harness.executor.execute(RecordingNotificationAction.STOP)
        )
        assertEquals(0, harness.startCalls)
        assertEquals(1, harness.stopCalls)
        assertFalse(harness.recording)
        assertEquals(1, harness.refreshCalls)
    }

    @Test
    fun failedStartShowsFeedbackAndRefreshesNotification() {
        val harness = Harness(startSucceeds = false)

        assertEquals(
            RecordingActionResult.START_FAILED,
            harness.executor.execute(RecordingNotificationAction.START)
        )
        assertEquals(1, harness.startCalls)
        assertEquals(1, harness.startFailureCalls)
        assertEquals(1, harness.refreshCalls)
        assertFalse(harness.recording)
    }

    @Test
    fun rapidRepeatedActionsCannotDuplicateOrInvertRecorderOperations() {
        val harness = Harness()

        assertEquals(
            RecordingActionResult.STARTED,
            harness.executor.execute(RecordingNotificationAction.START)
        )
        assertEquals(
            RecordingActionResult.ALREADY_RECORDING,
            harness.executor.execute(RecordingNotificationAction.START)
        )
        assertEquals(
            RecordingActionResult.STOPPED,
            harness.executor.execute(RecordingNotificationAction.STOP)
        )
        assertEquals(
            RecordingActionResult.NOT_RECORDING,
            harness.executor.execute(RecordingNotificationAction.STOP)
        )
        assertEquals(
            RecordingActionResult.STARTED,
            harness.executor.execute(RecordingNotificationAction.START)
        )

        assertEquals(2, harness.startCalls)
        assertEquals(1, harness.stopCalls)
        assertTrue(harness.recording)
        assertEquals(5, harness.refreshCalls)
    }

    @Test
    fun unacknowledgedActionOpensDisclosureAndRefreshes() {
        val harness = Harness(disclosureAcknowledged = false)

        assertEquals(
            RecordingActionResult.SHOW_DISCLOSURE,
            harness.executor.execute(RecordingNotificationAction.START)
        )
        assertEquals(0, harness.startCalls)
        assertEquals(1, harness.disclosureCalls)
        assertEquals(1, harness.refreshCalls)
    }

    @Test
    fun missingPermissionShowsFeedbackAndRefreshes() {
        val harness = Harness(microphoneGranted = false)

        assertEquals(
            RecordingActionResult.PERMISSION_REQUIRED,
            harness.executor.execute(RecordingNotificationAction.START)
        )
        assertEquals(0, harness.startCalls)
        assertEquals(1, harness.permissionCalls)
        assertEquals(1, harness.refreshCalls)
    }

    @Test
    fun callEndingBeforeDisclosureConfirmationNeverStartsRecording() {
        val harness = Harness(
            liveCall = false,
            disclosureAcknowledged = false
        )

        assertEquals(
            RecordingActionResult.NO_LIVE_CALL,
            harness.executor.execute(RecordingNotificationAction.START)
        )
        assertEquals(0, harness.startCalls)
        assertEquals(0, harness.disclosureCalls)
        assertFalse(harness.recording)
        assertEquals(1, harness.refreshCalls)
    }

    @Test
    fun acknowledgedStartStillRefusesWithoutLiveCall() {
        val harness = Harness(
            liveCall = false,
            disclosureAcknowledged = true
        )

        assertEquals(
            RecordingActionResult.NO_LIVE_CALL,
            harness.executor.execute(RecordingNotificationAction.START)
        )
        assertEquals(0, harness.startCalls)
        assertEquals(0, harness.startFailureCalls)
    }
}
