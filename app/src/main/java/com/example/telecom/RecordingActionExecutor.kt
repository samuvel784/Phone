/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

/** Immutable view of the state an explicit notification action must validate. */
internal data class RecordingActionState(
    val isRecording: Boolean,
    val hasLiveCall: Boolean,
    val microphoneGranted: Boolean,
    val disclosureAcknowledged: Boolean
)

/** Observable result of executing one explicit notification action. */
internal enum class RecordingActionResult {
    STARTED,
    STOPPED,
    ALREADY_RECORDING,
    NOT_RECORDING,
    SHOW_DISCLOSURE,
    PERMISSION_REQUIRED,
    NO_LIVE_CALL,
    START_FAILED
}

/**
 * Executes an explicit notification action against the recorder's real state.
 *
 * The executor is intentionally free of Android, service and UI dependencies. The service binds
 * the real recorder/CallManager operations; tests bind fakes. Every path refreshes the
 * notification afterwards so a failed start or a no-op stale action cannot leave a misleading
 * action visible.
 */
internal class RecordingActionExecutor(
    private val state: () -> RecordingActionState,
    private val startRecording: () -> Boolean,
    private val stopRecording: () -> Unit,
    private val onShowDisclosure: () -> Unit,
    private val onPermissionRequired: () -> Unit,
    private val onStartFailed: () -> Unit,
    private val refreshNotification: () -> Unit
) {

    fun execute(action: RecordingNotificationAction): RecordingActionResult {
        val current = state()
        val decision = NotificationRecordingPolicy.decide(
            action = action,
            isRecording = current.isRecording,
            permissionGranted = current.microphoneGranted,
            hasLiveCall = current.hasLiveCall,
            disclosureAcknowledged = current.disclosureAcknowledged
        )

        return try {
            when (decision) {
                NotificationRecordingDecision.START -> {
                    val started = runCatching { startRecording() }.getOrDefault(false)
                    if (started) {
                        RecordingActionResult.STARTED
                    } else {
                        onStartFailed()
                        RecordingActionResult.START_FAILED
                    }
                }

                NotificationRecordingDecision.STOP -> {
                    runCatching { stopRecording() }
                    RecordingActionResult.STOPPED
                }

                NotificationRecordingDecision.SHOW_DISCLOSURE -> {
                    onShowDisclosure()
                    RecordingActionResult.SHOW_DISCLOSURE
                }

                NotificationRecordingDecision.PERMISSION_REQUIRED -> {
                    onPermissionRequired()
                    RecordingActionResult.PERMISSION_REQUIRED
                }

                NotificationRecordingDecision.NO_LIVE_CALL ->
                    RecordingActionResult.NO_LIVE_CALL

                NotificationRecordingDecision.ALREADY_RECORDING ->
                    RecordingActionResult.ALREADY_RECORDING

                NotificationRecordingDecision.NOT_RECORDING ->
                    RecordingActionResult.NOT_RECORDING
            }
        } finally {
            // Also runs after a failed start or a stale/no-op action.
            runCatching { refreshNotification() }
        }
    }
}
