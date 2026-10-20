/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

import java.util.concurrent.ConcurrentHashMap

/**
 * Remembers, for a short time, which incoming numbers the screening service silenced,
 * so that the announcer does not speak for a call that the user chose to keep silent.
 */
object CallDecisionStore {
    private const val KEEP_MS = 2 * 60 * 1000L
    private val silenced = ConcurrentHashMap<String, Long>()

    fun markSilenced(number: String) {
        if (number.isBlank()) return
        val now = System.currentTimeMillis()
        silenced.entries.removeIf { now - it.value > KEEP_MS }
        silenced[key(number)] = now
    }

    fun wasSilenced(number: String): Boolean {
        if (number.isBlank()) return false
        val at = silenced[key(number)] ?: return false
        return System.currentTimeMillis() - at <= KEEP_MS
    }

    /** Last 8 digits, so "+919876543210" and "9876543210" are the same number. */
    private fun key(number: String): String = number.filter { it.isDigit() }.takeLast(8)
}
