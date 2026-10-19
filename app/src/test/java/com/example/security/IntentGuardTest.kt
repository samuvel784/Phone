/*
 * Copyright (C) 2026 NovaDial contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentGuardTest {

    @Test
    fun identicalTokensMatch() {
        assertTrue(IntentGuard.matches("a1b2c3d4e5", "a1b2c3d4e5"))
    }

    @Test
    fun differentTokensDoNotMatch() {
        assertFalse(IntentGuard.matches("a1b2c3d4e5", "a1b2c3d4e6"))
        assertFalse(IntentGuard.matches("a1b2c3d4e5", "a1b2c3d4e"))
        assertFalse(IntentGuard.matches("a1b2c3d4e5", "a1b2c3d4e5f"))
    }

    @Test
    fun blankOrNullNeverMatches() {
        assertFalse(IntentGuard.matches("", ""))
        assertFalse(IntentGuard.matches(null, "abc"))
        assertFalse(IntentGuard.matches("abc", null))
        assertFalse(IntentGuard.matches("abc", ""))
    }
}
