/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the breakpoints that every responsive surface relies on. These are pure calculations, so
 * they can be verified without an emulator and without inflating a Compose tree.
 */
class ResponsiveLayoutTest {

    @Test
    fun windowWidth_usesPhoneFoldableAndTabletBreakpoints() {
        assertEquals(NovaWindowWidth.Compact, novaWindowWidth(320.dp))
        assertEquals(NovaWindowWidth.Compact, novaWindowWidth(599.dp))
        assertEquals(NovaWindowWidth.Medium, novaWindowWidth(600.dp))
        assertEquals(NovaWindowWidth.Medium, novaWindowWidth(839.dp))
        assertEquals(NovaWindowWidth.Expanded, novaWindowWidth(840.dp))
        assertEquals(NovaWindowWidth.Expanded, novaWindowWidth(1280.dp))
    }

    @Test
    fun compactHeight_flipsAtShortLandscapeWindow() {
        assertTrue(isCompactHeight(360.dp))
        assertTrue(isCompactHeight(559.dp))
        assertFalse(isCompactHeight(560.dp))
        assertFalse(isCompactHeight(891.dp))
    }

    @Test
    fun responsiveColumns_neverDropsBelowOne() {
        assertEquals(1, responsiveColumns(0.dp, 160.dp))
        assertEquals(1, responsiveColumns(120.dp, 160.dp))
        assertEquals(1, responsiveColumns(320.dp, 160.dp, maxColumns = 1))
    }

    @Test
    fun responsiveColumns_fitsAsManyMinimumCellsAsPossible() {
        assertEquals(2, responsiveColumns(320.dp, 160.dp))
        assertEquals(3, responsiveColumns(599.dp, 160.dp))
        assertEquals(4, responsiveColumns(640.dp, 160.dp))
        assertEquals(8, responsiveColumns(1280.dp, 160.dp))
    }

    @Test
    fun responsiveColumns_respectsMaximum() {
        assertEquals(2, responsiveColumns(1280.dp, 160.dp, maxColumns = 2))
        assertEquals(3, responsiveColumns(1280.dp, 160.dp, maxColumns = 3))
    }

    @Test
    fun adaptivePadding_growsWithWindow() {
        assertEquals(16.dp, adaptiveHorizontalPadding(320.dp))
        assertEquals(24.dp, adaptiveHorizontalPadding(800.dp))
        assertEquals(32.dp, adaptiveHorizontalPadding(1280.dp))
    }

    @Test
    fun contentSideInset_centresCappedColumn() {
        assertEquals(0.dp, contentSideInset(320.dp, 640.dp))
        assertEquals(0.dp, contentSideInset(640.dp, 640.dp))
        assertEquals(80.dp, contentSideInset(800.dp, 640.dp))
        assertEquals(320.dp, contentSideInset(1280.dp, 640.dp))
    }

    @Test
    fun contentMaxWidths_areOrderedForReadability() {
        assertTrue(NovaBreakpoints.CONTENT_MAX_WIDTH <= NovaBreakpoints.SETTINGS_MAX_WIDTH)
        assertTrue(NovaBreakpoints.DIALPAD_MAX_WIDTH < NovaBreakpoints.CONTENT_MAX_WIDTH)
        assertTrue(NovaBreakpoints.COMPACT_MAX < NovaBreakpoints.EXPANDED_MIN)
    }
}
