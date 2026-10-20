/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

/** The explicit operation carried by an active-call notification action. */
internal enum class RecordingNotificationAction {
    START,
    STOP
}

/** What an explicit notification recording action is allowed to do. */
internal enum class NotificationRecordingDecision {
    START,
    STOP,
    SHOW_DISCLOSURE,
    PERMISSION_REQUIRED,
    NO_LIVE_CALL,
    ALREADY_RECORDING,
    NOT_RECORDING
}

/**
 * Single decision point for the active-call notification recording action.
 *
 * Actions are deliberately explicit. A STOP action can only stop an active recording and can
 * never start one; a START action can never stop an active recording. This prevents a stale
 * "Stop Rec" action from beginning a new recording after the user stopped from another surface.
 *
 * Stopping is allowed without the notice because the user already consented before that recording
 * started. Starting still requires microphone permission and the one-time manual-recording
 * acknowledgement.
 */
internal object NotificationRecordingPolicy {

    fun decide(
        action: RecordingNotificationAction,
        isRecording: Boolean,
        permissionGranted: Boolean,
        hasLiveCall: Boolean,
        disclosureAcknowledged: Boolean
    ): NotificationRecordingDecision = when {
        action == RecordingNotificationAction.STOP ->
            if (isRecording) NotificationRecordingDecision.STOP
            else NotificationRecordingDecision.NOT_RECORDING

        isRecording -> NotificationRecordingDecision.ALREADY_RECORDING
        !hasLiveCall -> NotificationRecordingDecision.NO_LIVE_CALL
        !permissionGranted -> NotificationRecordingDecision.PERMISSION_REQUIRED
        !disclosureAcknowledged -> NotificationRecordingDecision.SHOW_DISCLOSURE
        else -> NotificationRecordingDecision.START
    }

    /** The action the notification must expose for the recorder's current state. */
    fun actionFor(isRecording: Boolean): RecordingNotificationAction =
        if (isRecording) RecordingNotificationAction.STOP else RecordingNotificationAction.START
}
