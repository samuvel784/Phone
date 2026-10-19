/*
 * Copyright (C) 2026 MovStore
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.util

import com.example.model.Contact
import com.example.model.DialpadMatch
import java.text.Normalizer
import java.util.Locale

/**
 * Shared contact lookup for the dialpad quick-match list and the in-call keypad.
 *
 * Matches are emitted per phone number rather than per contact. That way a contact with several
 * numbers shows the exact number that matched the typed digits, instead of only its primary
 * number or its label ("Mobile", "Work", ...).
 */
object DialpadSearch {

    private val combiningMarks = Regex("\\p{InCombiningDiacriticalMarks}+")

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .lowercase(Locale.ROOT)

    private fun digitsOf(value: String): String =
        value.filter { it.isDigit() || it == '+' }

    fun findContactMatches(
        contacts: List<Contact>,
        rawQuery: String,
        limit: Int = 15
    ): List<DialpadMatch> {
        val query = rawQuery.trim()
        if (query.isEmpty() || limit <= 0) return emptyList()

        val queryDigits = digitsOf(query)
        val normalizedQuery = normalize(query)
        val matches = ArrayList<DialpadMatch>(minOf(limit, contacts.size))
        val seenNumbers = HashSet<String>()

        for (contact in contacts) {
            if (matches.size >= limit) break

            val numbers = contact.getAllNumbers()
            if (numbers.isEmpty()) continue

            val normalizedName = normalize(contact.name)
            val t9 = contact.t9Mapping.ifBlank { T9HighlightHelper.nameToT9(contact.name) }
            val nameMatches =
                (normalizedQuery.isNotEmpty() && normalizedName.contains(normalizedQuery)) ||
                    (queryDigits.isNotEmpty() && t9.contains(queryDigits, ignoreCase = true))

            val matchingNumbers = if (queryDigits.isEmpty()) {
                emptyList()
            } else {
                numbers.filter { digitsOf(it.number).contains(queryDigits, ignoreCase = true) }
            }

            // Digit searches prefer the numbers that actually contain the typed digits. Name/T9
            // searches fall back to every stored number so the card never hides a number.
            val candidates = when {
                matchingNumbers.isNotEmpty() -> matchingNumbers
                nameMatches -> numbers
                else -> emptyList()
            }

            for (labeledNumber in candidates) {
                val number = labeledNumber.number.ifBlank { contact.number }
                if (number.isBlank() || !seenNumbers.add(number)) continue

                matches += DialpadMatch(
                    number = number,
                    name = contact.name.ifBlank { number },
                    label = labeledNumber.label.ifBlank { contact.label },
                    avatarText = contact.avatarText,
                    avatarBgValue = contact.avatarBgValue,
                    avatarTextColorValue = contact.avatarTextColorValue,
                    isFromContacts = true,
                    isFromRecents = false,
                    photoUri = contact.photoUri
                )

                if (matches.size >= limit) break
            }
        }

        return matches
    }
}
