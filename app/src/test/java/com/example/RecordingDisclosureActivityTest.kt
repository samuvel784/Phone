package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.util.CallAudioRecorder
import com.example.util.RecordingDisclosure
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecordingDisclosureActivityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun resetState() {
        CallManager.updateCall(null)
        CallAudioRecorder.stopRecording()
        context.getSharedPreferences("recording_disclosure", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun activityFinishesWithoutLiveCallInsteadOfShowingMisleadingNotice() {
        val controller = Robolectric.buildActivity(RecordingDisclosureActivity::class.java)
        val activity = controller.setup().get()

        assertTrue(activity.isFinishing)
        assertFalse(CallAudioRecorder.isRecording.value)
        assertFalse(RecordingDisclosure.isManualAcknowledged(context))
    }
}
