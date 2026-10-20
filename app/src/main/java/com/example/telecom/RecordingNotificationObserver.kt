/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import kotlinx.coroutines.flow.StateFlow

/**
 * Event-driven bridge from the recorder's [StateFlow] to the notification owner.
 *
 * This deliberately observes the recorder instead of polling and avoids making the recorder or
 * UI depend on the service: any start/stop from the notification, in-call UI, Dynamic Island or
 * call teardown rebuilds the active-call notification.
 */
internal class RecordingNotificationObserver(
    private val isRecording: StateFlow<Boolean>,
    private val onStateChanged: (Boolean) -> Unit
) {
    suspend fun observe() {
        isRecording.collect { onStateChanged(it) }
    }
}
