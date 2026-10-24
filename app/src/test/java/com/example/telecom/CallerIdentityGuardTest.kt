/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import android.telecom.Call
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Async contact resolution must not leak a name onto a different or already ended call.
 */
class CallerIdentityGuardTest {

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
}
