/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.model.CallRecording
import com.example.model.SpamNumber
import java.util.Locale

/**
 * Everything a call-log row needs to know about recordings, spam numbers and the SIM count, prepared ONCE for the
 * whole list. Before, every row collected the app-wide recordings and spam flows itself and filtered them again,
 * so a long call log did thousands of scans on every change. Now each row does a single map lookup.
 */
@Immutable
class RecentsLookup(
    recordings: List<CallRecording>,
    spamNumbers: List<SpamNumber>,
    val physicalSimCount: Int
) {
    private val recordingsByTail: Map<String, List<CallRecording>> =
        recordings.groupBy { tail(it.number) }

    private val recordingsByName: Map<String, List<CallRecording>> =
        recordings.filter { it.name.isNotBlank() }.groupBy { it.name.lowercase(Locale.ROOT) }

    private val spamByNumber: Map<String, SpamNumber> = HashMap<String, SpamNumber>().also { map ->
        spamNumbers.forEach { map.putIfAbsent(it.number, it) }
    }

    fun recordingsFor(number: String, name: String): List<CallRecording> {
        val result = LinkedHashSet<CallRecording>()
        val key = tail(number)
        if (key.isNotEmpty()) recordingsByTail[key]?.let { result.addAll(it) }
        if (name.isNotEmpty() && name != "Unknown") {
            recordingsByName[name.lowercase(Locale.ROOT)]?.let { result.addAll(it) }
        }
        return result.toList()
    }

    fun spamFor(number: String): SpamNumber? = spamByNumber[number]

    companion object {
        val Empty = RecentsLookup(emptyList(), emptyList(), 1)

        /** Last 7 digits: the same number written with or without the country code gives the same key. */
        fun tail(number: String): String {
            val digits = number.filter { it.isDigit() }
            return if (digits.length > 7) digits.takeLast(7) else digits
        }
    }
}

val LocalRecentsLookup = staticCompositionLocalOf { RecentsLookup.Empty }
