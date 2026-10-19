/*
 * Copyright (C) 2026 NovaDial contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.security.IntentGuard
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IntentGuardTest {

    private lateinit var context: Application

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun tokenIsStableAcrossCalls() {
        val first = IntentGuard.token(context)
        val second = IntentGuard.token(context)
        assertEquals(first, second)
        assertTrue(first.length >= 64)
    }

    @Test
    fun validTokenIsAcceptedAndForgedTokenIsRejected() {
        val token = IntentGuard.token(context)
        val forged = (if (token.first() == '0') "1" else "0") + token.drop(1)
        assertTrue(IntentGuard.isValid(context, token))
        assertFalse(IntentGuard.isValid(context, forged))
        assertFalse(IntentGuard.isValid(context, "forged-token"))
        assertFalse(IntentGuard.isValid(context, null))
        assertFalse(IntentGuard.isValid(context, ""))
    }

    @Test
    fun matchesIsFalseForBlankOrDifferentLengthValues() {
        assertFalse(IntentGuard.matches(null, null))
        assertFalse(IntentGuard.matches("", ""))
        assertFalse(IntentGuard.matches("abc", ""))
        assertFalse(IntentGuard.matches("abc", "abcd"))
        assertTrue(IntentGuard.matches("abc", "abc"))
    }
}
