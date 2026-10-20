/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.telecom.Call
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.ui.theme.MyApplicationTheme
import com.example.util.CallAudioRecorder
import com.example.util.RecordingDisclosure
import kotlinx.coroutines.launch

/**
 * Activity-owned disclosure for the notification Record action.
 *
 * A background service must never show a dialog or start capturing audio before the one-time
 * notice has been accepted. This activity is private, shows the same notice as the in-call UI,
 * and only starts the recording after explicit confirmation.
 */
class RecordingDisclosureActivity : ComponentActivity() {

    private val expectedCallId: Long by lazy {
        intent?.getLongExtra(MyInCallService.EXTRA_CALL_ID, MyInCallService.NO_CALL_ID)
            ?: MyInCallService.NO_CALL_ID
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A second notification tap must not create a second recording or a second dialog.
        if (CallAudioRecorder.isRecording.value) {
            finish()
            return
        }

        // Stale notifications (and calls that ended before this activity opened) must not leave a
        // disclosure dialog floating with no call behind it.
        if (!hasLiveCall()) {
            MyInCallService.refreshActiveCallNotification()
            finish()
            return
        }

        observeCallLifecycle()

        // Covers the race where the notice was accepted elsewhere after the notification was built.
        if (RecordingDisclosure.isManualAcknowledged(this)) {
            startRecordingIfReady()
            finish()
            return
        }

        setContent {
            MyApplicationTheme {
                var showDialog by remember { mutableStateOf(true) }
                if (showDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            showDialog = false
                            finish()
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        title = { Text(stringResource(R.string.recording_disclosure_title)) },
                        text = { Text(stringResource(R.string.recording_disclosure_body)) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (!isFinishing && !isDestroyed) {
                                        // Re-check on the main thread immediately before committing
                                        // the acknowledgement: the call may have ended while the
                                        // dialog was open.
                                        if (hasLiveCall()) {
                                            RecordingDisclosure.acknowledgeManual(
                                                this@RecordingDisclosureActivity
                                            )
                                        }
                                        startRecordingIfReady()
                                        showDialog = false
                                        finish()
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.recording_disclosure_accept))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showDialog = false
                                    finish()
                                }
                            ) {
                                Text(stringResource(R.string.btn_cancel))
                            }
                        }
                    )
                }
            }
        }
    }

    /**
     * Dismiss the disclosure if the call ends (or the activity is left holding an action for a
     * different call) before the user confirms. No acknowledgement or recording is produced.
     */
    private fun observeCallLifecycle() {
        lifecycleScope.launch {
            CallManager.currentCall.collect { call ->
                if (!isLiveCallForThisActivity(call) && !isFinishing && !isDestroyed) {
                    finish()
                }
            }
        }
    }

    private fun hasLiveCall(): Boolean = isLiveCallForThisActivity(CallManager.currentCall.value)

    private fun isLiveCallForThisActivity(call: Call?): Boolean {
        if (call == null || call.safeState == Call.STATE_DISCONNECTED) return false
        return expectedCallId == MyInCallService.NO_CALL_ID ||
            CallManager.callId(call) == expectedCallId
    }

    private fun startRecordingIfReady() {
        if (isFinishing || isDestroyed) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, getString(R.string.recording_start_failed), Toast.LENGTH_LONG).show()
            MyInCallService.refreshActiveCallNotification()
            return
        }

        if (!hasLiveCall()) {
            MyInCallService.refreshActiveCallNotification()
            return
        }

        val started = try {
            CallManager.startCallRecording(this)
        } catch (_: Exception) {
            false
        }

        if (!started && !CallAudioRecorder.isRecording.value) {
            Toast.makeText(this, getString(R.string.recording_start_failed), Toast.LENGTH_LONG).show()
        }

        // Refresh on success and failure: the recorder state determines the next explicit action.
        MyInCallService.refreshActiveCallNotification()
    }
}
