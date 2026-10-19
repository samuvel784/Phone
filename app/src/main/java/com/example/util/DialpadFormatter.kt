/*
 * Copyright (C) 2026 NovaDial contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.util

/**
 * Pure, testable formatting for the dialer keypad.
 *
 * Two responsibilities:
 *  - [sanitize] keeps only dialable characters and enforces the 30-digit cap.
 *  - [format] produces the spaced *display* string (e.g. `98765 43210`,
 *    `+91 98765 43210`) that is shown in the keypad field.
 *
 * The canonical value used for calling, searching and contacts stays the raw
 * sanitized string; only the on-screen field is spaced.
 */
object DialpadFormatter {

    /** Hard cap on how many digits the keypad accepts. */
    const val MAX_DIGITS = 30

    private const val GROUP_SIZE = 5

    /**
     * Keep digits, a single leading `+`, and the service-code symbols `*` / `#`.
     * Any `+` that is not at the very start is dropped, and no more than
     * [MAX_DIGITS] digits are kept.
     */
    fun sanitize(text: String): String {
        val sb = StringBuilder()
        var digitCount = 0
        for (ch in text) {
            when {
                ch.isDigit() -> if (digitCount < MAX_DIGITS) {
                    sb.append(ch)
                    digitCount++
                }
                ch == '+' && sb.isEmpty() -> sb.append('+')
                ch == '*' || ch == '#' -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** The canonical, dialable value for [text]. */
    fun raw(text: String): String = sanitize(text)

    /**
     * Spaced display form of [text]. Digits are grouped in blocks of five. A leading
     * `+` is kept and, when it resolves to a known calling code (e.g. `+91`, `+1`),
     * that code is separated from the subscriber digits. Service codes containing
     * `*` or `#` are shown unchanged.
     */
    fun format(text: String): String {
        val safe = sanitize(text)
        if (safe.isEmpty()) return ""
        if (safe.any { it == '*' || it == '#' }) return safe

        if (!safe.startsWith("+")) return group(safe)

        val digits = safe.substring(1)
        if (digits.isEmpty()) return "+"
        val code = countryCode(digits)
        if (code.isEmpty()) return "+" + group(digits)

        val national = digits.substring(code.length)
        return if (national.isEmpty()) "+$code" else "+$code " + group(national)
    }

    private fun group(digits: String): String = digits.chunked(GROUP_SIZE).joinToString(" ")

    /** Longest calling-code match at the start of [digits], or "" when unknown. */
    fun countryCode(digits: String): String {
        for (len in 3 downTo 1) {
            if (digits.length >= len) {
                val candidate = digits.substring(0, len)
                if (CALLING_CODES.contains(candidate)) return candidate
            }
        }
        return ""
    }

    /** Common ITU calling codes (1-3 digits). Longest match wins, so this is safe to extend. */
    private val CALLING_CODES: Set<String> = setOf(
        // 1-digit
        "1", "7",
        // 2-digit
        "20", "27", "30", "31", "32", "33", "34", "36", "39", "40", "41", "43", "44",
        "45", "46", "47", "48", "49", "51", "52", "53", "54", "55", "56", "57", "58",
        "60", "61", "62", "63", "64", "65", "66", "81", "82", "84", "86", "90", "91",
        "92", "93", "94", "95", "98",
        // 3-digit
        "211", "212", "213", "216", "218", "220", "221", "222", "223", "224", "225",
        "226", "227", "228", "229", "230", "231", "232", "233", "234", "235", "236",
        "237", "238", "239", "240", "241", "242", "243", "244", "245", "246", "247",
        "248", "249", "250", "251", "252", "253", "254", "255", "256", "257", "258",
        "260", "261", "262", "263", "264", "265", "266", "267", "268", "269", "290",
        "291", "297", "298", "299", "350", "351", "352", "353", "354", "355", "356",
        "357", "358", "359", "370", "371", "372", "373", "374", "375", "376", "377",
        "378", "379", "380", "381", "382", "383", "385", "386", "387", "389", "420",
        "421", "423", "500", "501", "502", "503", "504", "505", "506", "507", "508",
        "509", "590", "591", "592", "593", "594", "595", "596", "597", "598", "599",
        "670", "672", "673", "674", "675", "676", "677", "678", "679", "680", "681",
        "682", "683", "685", "686", "687", "688", "689", "690", "691", "692", "800",
        "808", "850", "852", "853", "855", "856", "870", "878", "880", "881", "882",
        "883", "886", "888", "960", "961", "962", "963", "964", "965", "966", "967",
        "968", "970", "971", "972", "973", "974", "975", "976", "977", "979", "992",
        "993", "994", "995", "996", "998"
    )
}
