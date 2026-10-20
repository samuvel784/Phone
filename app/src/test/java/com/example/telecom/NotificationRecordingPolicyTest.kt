package com.example.telecom

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationRecordingPolicyTest {

    @Test
    fun unacknowledgedDisclosureRequiresTheActivityNotice() {
        val decision = NotificationRecordingPolicy.decide(
            action = RecordingNotificationAction.START,
            isRecording = false,
            permissionGranted = true,
            hasLiveCall = true,
            disclosureAcknowledged = false
        )

        assertEquals(NotificationRecordingDecision.SHOW_DISCLOSURE, decision)
    }

    @Test
    fun acknowledgedDisclosureAllowsRecordingToStart() {
        val decision = NotificationRecordingPolicy.decide(
            action = RecordingNotificationAction.START,
            isRecording = false,
            permissionGranted = true,
            hasLiveCall = true,
            disclosureAcknowledged = true
        )

        assertEquals(NotificationRecordingDecision.START, decision)
    }

    @Test
    fun missingMicrophonePermissionNeverStartsRecording() {
        val decision = NotificationRecordingPolicy.decide(
            action = RecordingNotificationAction.START,
            isRecording = false,
            permissionGranted = false,
            hasLiveCall = true,
            disclosureAcknowledged = true
        )

        assertEquals(NotificationRecordingDecision.PERMISSION_REQUIRED, decision)
    }

    @Test
    fun noLiveCallNeverStartsRecording() {
        val decision = NotificationRecordingPolicy.decide(
            action = RecordingNotificationAction.START,
            isRecording = false,
            permissionGranted = true,
            hasLiveCall = false,
            disclosureAcknowledged = true
        )

        assertEquals(NotificationRecordingDecision.NO_LIVE_CALL, decision)
    }

    @Test
    fun startActionNeverStopsAnActiveRecording() {
        val decision = NotificationRecordingPolicy.decide(
            action = RecordingNotificationAction.START,
            isRecording = true,
            permissionGranted = false,
            hasLiveCall = false,
            disclosureAcknowledged = false
        )

        assertEquals(NotificationRecordingDecision.ALREADY_RECORDING, decision)
    }

    @Test
    fun stopActionStopsAnActiveRecordingEvenWithoutStartGates() {
        val decision = NotificationRecordingPolicy.decide(
            action = RecordingNotificationAction.STOP,
            isRecording = true,
            permissionGranted = false,
            hasLiveCall = false,
            disclosureAcknowledged = false
        )

        assertEquals(NotificationRecordingDecision.STOP, decision)
    }

    @Test
    fun staleStopActionCanNeverStartRecording() {
        val decision = NotificationRecordingPolicy.decide(
            action = RecordingNotificationAction.STOP,
            isRecording = false,
            permissionGranted = true,
            hasLiveCall = true,
            disclosureAcknowledged = true
        )

        assertEquals(NotificationRecordingDecision.NOT_RECORDING, decision)
    }

    @Test
    fun notificationActionFollowsTheRecorderState() {
        assertEquals(
            RecordingNotificationAction.STOP,
            NotificationRecordingPolicy.actionFor(isRecording = true)
        )
        assertEquals(
            RecordingNotificationAction.START,
            NotificationRecordingPolicy.actionFor(isRecording = false)
        )
    }
}
