package com.example.telecom

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordingNotificationObserverTest {

    @Test
    fun notificationRefreshFollowsEveryRecorderStateChange() = runTest {
        val recorderState = MutableStateFlow(false)
        val observed = mutableListOf<Boolean>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            RecordingNotificationObserver(recorderState) { observed += it }.observe()
        }
        advanceUntilIdle()

        // Simulates a start from the notification or in-call UI.
        recorderState.value = true
        advanceUntilIdle()

        // Simulates a stop from the in-call UI or Dynamic Island.
        recorderState.value = false
        advanceUntilIdle()

        assertEquals(listOf(false, true, false), observed)
    }
}
