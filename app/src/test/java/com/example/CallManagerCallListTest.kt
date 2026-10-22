/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Telecom mutates the same `Call` instance. A plain `ArrayList` copy therefore compares equal to
 * the previous value and `MutableStateFlow` drops the update. This is the regression guard for the
 * stale Answer/Decline screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CallManagerCallListTest {

    @Test
    fun identityListEmitsWhenContentsAreEqual() = runTest {
        val flow = MutableStateFlow<List<String>>(
            CallManager.CallList(listOf("same-call-object"))
        )
        val emissions = mutableListOf<List<String>>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            flow.collect { emissions += it }
        }

        val previous = flow.value
        flow.value = CallManager.CallList(previous.toList())
        // A second callback with the same contents must also be published.
        flow.value = CallManager.CallList(flow.value.toList())

        assertEquals(3, emissions.size)
        assertNotEquals(previous, flow.value)

        job.cancel()
    }

    @Test
    fun identityListStillBehavesAsAList() {
        val list = CallManager.CallList(listOf("a", "b"))
        assertEquals(2, list.size)
        assertEquals("a", list[0])
        assertEquals(listOf("a", "b"), list)
        assertNotEquals(CallManager.CallList(listOf("a", "b")), list)
    }
}
