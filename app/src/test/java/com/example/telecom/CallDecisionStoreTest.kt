/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallDecisionStoreTest {

    @Test
    fun `silencing one number never silences a different subscriber with the same tail`() {
        CallDecisionStore.markSilenced("+15550001234")

        assertTrue(CallDecisionStore.wasSilenced("+1 (555) 000-1234"))
        assertFalse(CallDecisionStore.wasSilenced("+15559991234"))
    }

    @Test
    fun `blank and digitless numbers are ignored`() {
        CallDecisionStore.markSilenced("")
        CallDecisionStore.markSilenced("private")

        assertFalse(CallDecisionStore.wasSilenced(""))
        assertFalse(CallDecisionStore.wasSilenced("private"))
    }
}
