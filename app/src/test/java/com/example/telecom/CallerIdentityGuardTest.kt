/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import android.telecom.Call
import com.example.util.PhoneNumberNormalizer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Async contact resolution must not leak a name onto a different or already ended call.
 *
 * Robolectric is required for the number-comparison cases: they use the platform E.164 formatter.
 */
@RunWith(RobolectricTestRunner::class)
class CallerIdentityGuardTest {

    @Before
    fun useIndianRegion() {
        PhoneNumberNormalizer.setRegion("IN")
    }

    @Test
    fun `result is applied only while the same call is still live`() {
        assertTrue(
            CallerIdentityGuard.canApplyResolvedName(
                resolvedCallId = 7L,
                currentCallId = 7L,
                currentCallState = Call.STATE_RINGING
            )
        )
        assertTrue(
            CallerIdentityGuard.canApplyResolvedName(
                resolvedCallId = 7L,
                currentCallId = 7L,
                currentCallState = Call.STATE_ACTIVE
            )
        )
    }

    @Test
    fun `result for an ended call is rejected`() {
        assertFalse(
            CallerIdentityGuard.canApplyResolvedName(
                resolvedCallId = 7L,
                currentCallId = 7L,
                currentCallState = Call.STATE_DISCONNECTED
            )
        )
    }

    @Test
    fun `result for a replaced or waiting call never overwrites the current caller`() {
        assertFalse(
            CallerIdentityGuard.canApplyResolvedName(
                resolvedCallId = 7L,
                currentCallId = 8L,
                currentCallState = Call.STATE_ACTIVE
            )
        )
        assertFalse(
            CallerIdentityGuard.canApplyResolvedName(
                resolvedCallId = 7L,
                currentCallId = null,
                currentCallState = Call.STATE_DISCONNECTED
            )
        )
    }

    @Test
    fun `repeated number on a new call id is still rejected`() {
        // The old call and the new call have the same number, which is exactly the case where
        // number-based de-duplication would wrongly accept the stale result.
        assertFalse(
            CallerIdentityGuard.canApplyResolvedName(
                resolvedCallId = 7L,
                currentCallId = 8L,
                currentCallState = Call.STATE_RINGING,
                resolvedNumber = "+14155550100",
                currentNumber = "+14155550100"
            )
        )
    }

    @Test
    fun `same live call with a different number is rejected`() {
        assertFalse(
            CallerIdentityGuard.canApplyResolvedName(
                resolvedCallId = 7L,
                currentCallId = 7L,
                currentCallState = Call.STATE_ACTIVE,
                resolvedNumber = "+14155550100",
                currentNumber = "+14155550199"
            )
        )
    }

    @Test
    fun `same live call keeps formatting variants accepted`() {
        assertTrue(
            CallerIdentityGuard.canApplyResolvedName(
                resolvedCallId = 7L,
                currentCallId = 7L,
                currentCallState = Call.STATE_ACTIVE,
                resolvedNumber = "+919876543210",
                currentNumber = "09876543210"
            )
        )
    }
}
