/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.security.IntentGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Notification and lock-screen entry points must all target the one private call-only activity and
 * carry the exact call id. Answer is an explicit action, never an incidental side effect of tapping
 * the notification body.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CallOnlyIntentsTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val expectedFlags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_SINGLE_TOP or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT

    @Test
    fun contentIntentTargetsOnlyTheCallOnlyActivityWithTheExactCall() {
        val intent = CallOnlyIntents.callScreen(
            context = context,
            callId = 42L,
            callerName = "Test Caller",
            callerNumber = "+15550100",
            guardToken = "guard-token"
        )

        assertEquals(IncomingCallActivity::class.java.name, intent.component?.className)
        assertEquals(MyInCallService.ACTION_INCOMING_CALL, intent.action)
        assertEquals(42L, intent.getLongExtra(MyInCallService.EXTRA_CALL_ID, MyInCallService.NO_CALL_ID))
        assertEquals("Test Caller", intent.getStringExtra(IncomingCallActivity.EXTRA_CALLER_NAME))
        assertEquals("+15550100", intent.getStringExtra(IncomingCallActivity.EXTRA_CALLER_NUMBER))
        assertEquals("guard-token", intent.getStringExtra(IntentGuard.EXTRA_GUARD_TOKEN))
        assertEquals(expectedFlags, intent.flags and expectedFlags)
        assertFalse(intent.getBooleanExtra(MyInCallService.EXTRA_ANSWER, false))
    }

    @Test
    fun answerActionIsExplicitAndScopedToTheSameCall() {
        val intent = CallOnlyIntents.callScreen(
            context = context,
            callId = 77L,
            guardToken = "guard-token",
            action = MyInCallService.ACTION_ANSWER,
            answer = true
        )

        assertEquals(IncomingCallActivity::class.java.name, intent.component?.className)
        assertEquals(MyInCallService.ACTION_ANSWER, intent.action)
        assertEquals(77L, intent.getLongExtra(MyInCallService.EXTRA_CALL_ID, MyInCallService.NO_CALL_ID))
        assertTrue(intent.getBooleanExtra(MyInCallService.EXTRA_ANSWER, false))
    }

    @Test
    fun callScreenIntentNeverFallsBackToTheMainActivity() {
        val intent = CallOnlyIntents.callScreen(context, callId = 1L)

        assertEquals(IncomingCallActivity::class.java.name, intent.component?.className)
        assertTrue(intent.component?.className != MainActivity::class.java.name)
    }
}
