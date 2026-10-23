/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Per-list Recents state. The shipped [RecentCallRow] only needs the physical SIM count to draw
 * its SIM badge, so this no longer builds recording or spam indexes that no production row reads.
 * Legacy recording/spam tables stay in the database for upgrade and backup compatibility.
 */
@Immutable
class RecentsLookup(
    val physicalSimCount: Int
) {
    companion object {
        val Empty = RecentsLookup(physicalSimCount = 1)
    }
}

/** Exact-match blocked-number check shared by Recents rows and unit tests. */
fun isBlockedNumber(number: String, blockedNumbers: Set<String>): Boolean = number in blockedNumbers

val LocalRecentsLookup = staticCompositionLocalOf { RecentsLookup.Empty }

