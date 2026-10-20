/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.telecom

/**
 * Pure Kotlin (no Android classes), so it can be unit tested.
 *
 * Template variables:
 *   {name} the saved contact name, or "an unknown number"
 *   {user} the user's own name from settings
 *   {type} "VIP", "saved contact" or "unknown number"
 */
object AnnouncerScript {
    const val DEFAULT_TEMPLATE = "Call from {name}"
    const val MAX_TEMPLATE_LENGTH = 120
    const val MAX_USER_NAME_LENGTH = 40

    /** A call announcement never lasts longer than this. */
    const val MAX_TOTAL_MS = 15_000L
    const val REPEAT_GAP_MS = 5_000L

    val PRESETS: List<Pair<String, String>> = listOf(
        "Friendly" to "Call from {name}",
        "Formal" to "Incoming call from {name}",
        "Playful" to "Hey {user}, {name} is calling!"
    )

    data class Line(val atMs: Long, val text: String)

    fun render(template: String, name: String, user: String, type: String): String {
        val source = template.trim().ifEmpty { DEFAULT_TEMPLATE }.take(MAX_TEMPLATE_LENGTH)
        return source
            .replace("{name}", name)
            .replace("{user}", user.trim().ifEmpty { "there" })
            .replace("{type}", type)
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** The text repeated 1..3 times, every 5 seconds, never past the 15 second limit. */
    fun buildScript(template: String, name: String, user: String, type: String, repeats: Int): List<Line> {
        val text = render(template, name, user, type)
        if (text.isBlank()) return emptyList()
        return (0 until repeats.coerceIn(1, 3))
            .map { Line(it * REPEAT_GAP_MS, text) }
            .filter { it.atMs < MAX_TOTAL_MS }
    }
}
