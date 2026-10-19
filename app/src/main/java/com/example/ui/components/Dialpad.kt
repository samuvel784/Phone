/*
 * Copyright (C) 2026 MovStore
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.ui.components

/** Canonical dial-pad key layout (digit, sub-label, dial index). */
val DIALPAD_KEYS = listOf(
    Triple("1", "", 1),
    Triple("2", "ABC", 2),
    Triple("3", "DEF", 3),
    Triple("4", "GHI", 4),
    Triple("5", "JKL", 5),
    Triple("6", "MNO", 6),
    Triple("7", "PQRS", 7),
    Triple("8", "TUV", 8),
    Triple("9", "WXYZ", 9),
    Triple("*", "", -1),
    Triple("0", "+", 0),
    Triple("#", "", -1)
)
