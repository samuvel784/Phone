/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the header policy: the settings menu belongs to the Home tab. Recents and Favourites keep
 * the search field, but the same hamburger must not be shown on every page.
 */
class HeaderTabMenuPolicyTest {

    @Test
    fun `settings menu is exposed only on the home tab`() {
        assertTrue(headerMenuVisibleForTab("HOME"))
        assertFalse(headerMenuVisibleForTab("RECENTS"))
        assertFalse(headerMenuVisibleForTab("FAVORITES"))
        assertFalse(headerMenuVisibleForTab("CONTACTS"))
        assertFalse(headerMenuVisibleForTab(""))
    }
}
