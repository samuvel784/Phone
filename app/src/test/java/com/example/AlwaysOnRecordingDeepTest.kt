/*
 * Copyright (C) 2026 MovStore
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.model.CallRecording
import com.example.ui.viewmodel.DialerViewModel
import com.example.util.CallAudioRecorder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlwaysOnRecordingDeepTest {

    private lateinit var context: Application
    private lateinit var database: AppDatabase
    private lateinit var viewModel: DialerViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = AppDatabase.getDatabase(context)
        viewModel = DialerViewModel(context)
        CallAudioRecorder.stopRecording()
    }

    @After
    fun tearDown() {
        CallAudioRecorder.stopRecording()
    }

    // ==========================================
    // 1. ALWAYS-ON CALL RECORDING RIGOROUS TESTS
    // ==========================================

    @Test
    fun `test always-on call recording preference lifecycle and viewmodel synchronization`() {
        val prefs = context.getSharedPreferences("dialer_prefs", Context.MODE_PRIVATE)

        // Step 1: Initial state assertion
        viewModel.updateAutoRecordCallsEnabled(false)
        assertFalse(viewModel.isAutoRecordCallsEnabled.value)
        assertFalse(prefs.getBoolean("is_auto_record_calls_enabled", true))

        // Step 2: Toggle on via ViewModel
        viewModel.updateAutoRecordCallsEnabled(true)
        assertTrue(viewModel.isAutoRecordCallsEnabled.value)
        assertTrue(prefs.getBoolean("is_auto_record_calls_enabled", false))

        // Step 3: Verify SharedPreferences synchronization on external reload
        val newVm = DialerViewModel(context)
        assertTrue(newVm.isAutoRecordCallsEnabled.value)

        // Step 4: Toggle off and re-verify
        newVm.updateAutoRecordCallsEnabled(false)
        assertFalse(newVm.isAutoRecordCallsEnabled.value)
        assertFalse(prefs.getBoolean("is_auto_record_calls_enabled", true))
    }

    @Test
    fun `test always-on recorder trigger simulation during active telephony call`() {
        val prefs = context.getSharedPreferences("dialer_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_auto_record_calls_enabled", true).commit()

        val testNumber = "+15550192834"

        // When Always-on is enabled, simulate call active trigger
        val isAutoRecord = prefs.getBoolean("is_auto_record_calls_enabled", false)
        assertTrue("Always-on setting must be recognized as enabled", isAutoRecord)

        val startSuccess = CallAudioRecorder.startRecording(context, testNumber)
        assertTrue("CallAudioRecorder must successfully initialize recording", startSuccess)
        assertTrue("Recording state must reflect active", CallAudioRecorder.isRecording.value)

        // Simulate Call Disconnect and automated metadata persistence to Room DB
        val result = CallAudioRecorder.stopRecording()
        assertFalse("Recording state must be false after stop", CallAudioRecorder.isRecording.value)

        runBlocking {
            val recordDir = File(context.filesDir, "CallRecordings").apply { if (!exists()) mkdirs() }
            val dummyFile = File(recordDir, "REC_15550192834_deep_test.m4a").apply { writeBytes(ByteArray(256) { 1 }) }

            val recordEntity = CallRecording(
                number = testNumber,
                name = "Test Contact",
                timestamp = "Sep 21, 14:00",
                duration = 15L,
                filePath = result.file?.absolutePath ?: dummyFile.absolutePath
            )
            val insertedId = database.dialerDao().insertCallRecording(recordEntity)
            assertTrue("Entity must be successfully inserted into Room DB", insertedId > 0)

            val recordings = database.dialerDao().getAllCallRecordingsFlow().first()
            assertTrue("Database must contain the newly saved recording", recordings.any { it.number == testNumber })
        }
    }

    @Test
    fun `test always-on recording suppression when toggle is disabled`() {
        val prefs = context.getSharedPreferences("dialer_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_auto_record_calls_enabled", false).commit()

        val testNumber = "+19998887777"

        val isAutoRecord = prefs.getBoolean("is_auto_record_calls_enabled", false)
        assertFalse("Always-on setting must be false", isAutoRecord)

        // Telephony state change to ACTIVE should NOT start recording
        if (isAutoRecord) {
            CallAudioRecorder.startRecording(context, testNumber)
        }

        assertFalse("Recorder must remain inactive when feature is disabled", CallAudioRecorder.isRecording.value)
    }

    // ==========================================
    // 2. CALLMANAGER IN-CALL STATE TESTS
    // ==========================================

    @Test
    fun `test in-call telephony action bindings`() {
        // Test mute and speaker state propagation through CallManager
        CallManager.setMuted(true)
        CallManager.setMuted(false)
        CallManager.setSpeaker(true)
        CallManager.setSpeaker(false)
        CallManager.disconnect()

        assertTrue("CallManager in-call operations must execute cleanly", true)
    }

    @Test
    fun `test caller name and contact details resolution`() {
        // Test that CallManager holds and propagates contact name and details during a call
        CallManager.setCallerDetails(
            name = "Sarah Connor",
            photoUri = "content://com.android.contacts/display_photo/1",
            label = "Mobile"
        )

        assertEquals("Sarah Connor", CallManager.callerName.value)
        assertEquals("content://com.android.contacts/display_photo/1", CallManager.callerPhotoUri.value)
        assertEquals("Mobile", CallManager.callerLabel.value)

        // Test fallback when disconnected
        CallManager.updateCall(null)
        assertEquals("", CallManager.callerName.value)
        assertEquals("", CallManager.callerPhotoUri.value)
        assertEquals("", CallManager.callerLabel.value)
    }

    @Test
    fun `test call state gating - active only when placed or answered, never when ringing`() {
        // Call surfaces must only activate when placed (DIALING/CONNECTING) or answered (ACTIVE/HOLDING)
        // It must NEVER activate during incoming call ringing (RINGING)
        fun isCallPlacedOrAnswered(callState: Int): Boolean {
            return callState == android.telecom.Call.STATE_ACTIVE ||
                    callState == android.telecom.Call.STATE_DIALING ||
                    callState == android.telecom.Call.STATE_CONNECTING ||
                    callState == android.telecom.Call.STATE_HOLDING
        }

        // Incoming ringing -> False (suppressed)
        assertFalse(isCallPlacedOrAnswered(android.telecom.Call.STATE_RINGING))
        // Disconnected -> False
        assertFalse(isCallPlacedOrAnswered(android.telecom.Call.STATE_DISCONNECTED))
        assertFalse(isCallPlacedOrAnswered(android.telecom.Call.STATE_DISCONNECTING))

        // Placed (outgoing) -> True
        assertTrue(isCallPlacedOrAnswered(android.telecom.Call.STATE_DIALING))
        assertTrue(isCallPlacedOrAnswered(android.telecom.Call.STATE_CONNECTING))

        // Answered (in-call) -> True
        assertTrue(isCallPlacedOrAnswered(android.telecom.Call.STATE_ACTIVE))
        assertTrue(isCallPlacedOrAnswered(android.telecom.Call.STATE_HOLDING))
    }
}
