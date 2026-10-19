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

package com.example

import com.example.model.Contact
import com.example.model.LabeledNumber
import com.example.util.DialpadSearch
import com.example.util.T9HighlightHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DialpadSearchTest {

    private fun contact(
        id: Long,
        name: String,
        primaryNumber: String,
        numbers: List<LabeledNumber>
    ) = Contact(
        id = id,
        number = primaryNumber,
        name = name,
        label = numbers.firstOrNull()?.label ?: "Mobile",
        avatarText = name.take(1).uppercase(),
        avatarBgValue = 0xFF6750A4,
        avatarTextColorValue = 0xFFFFFFFF,
        t9Mapping = T9HighlightHelper.nameToT9(name),
        numbers = numbers
    )

    @Test
    fun `matching secondary number is shown instead of hiding behind primary number`() {
        val person = contact(
            id = 1,
            name = "Alex",
            primaryNumber = "9999999999",
            numbers = listOf(
                LabeledNumber("9999999999", "Mobile"),
                LabeledNumber("1234567890", "Custom")
            )
        )

        val result = DialpadSearch.findContactMatches(listOf(person), "12345")

        assertEquals(1, result.size)
        assertEquals("1234567890", result[0].number)
        assertEquals("Custom", result[0].label)
    }

    @Test
    fun `numeric search returns every matching number for a contact`() {
        val person = contact(
            id = 2,
            name = "Sam",
            primaryNumber = "1234567890",
            numbers = listOf(
                LabeledNumber("1234567890", "Mobile"),
                LabeledNumber("1234500000", "Work")
            )
        )

        val result = DialpadSearch.findContactMatches(listOf(person), "12345")

        assertEquals(2, result.size)
        assertEquals(setOf("1234567890", "1234500000"), result.map { it.number }.toSet())
        assertEquals(setOf("Mobile", "Work"), result.map { it.label }.toSet())
    }

    @Test
    fun `formatted numbers match digits across separators`() {
        val person = contact(
            id = 3,
            name = "International",
            primaryNumber = "+1 (555) 123-4567",
            numbers = listOf(LabeledNumber("+1 (555) 123-4567", "Mobile"))
        )

        val result = DialpadSearch.findContactMatches(listOf(person), "555123")

        assertEquals(1, result.size)
        assertEquals("+1 (555) 123-4567", result[0].number)
    }

    @Test
    fun `name and T9 matches expose all stored numbers`() {
        val person = contact(
            id = 4,
            name = "Alice",
            primaryNumber = "1111111111",
            numbers = listOf(
                LabeledNumber("1111111111", "Mobile"),
                LabeledNumber("2222222222", "Work")
            )
        )

        val byName = DialpadSearch.findContactMatches(listOf(person), "Alice")
        val byT9 = DialpadSearch.findContactMatches(listOf(person), "25423")

        assertEquals(2, byName.size)
        assertEquals(2, byT9.size)
        assertTrue(byName.all { it.name == "Alice" })
    }

    @Test
    fun `limit is respected`() {
        val people = (1L..5L).map { id ->
            contact(
                id = id,
                name = "Person $id",
                primaryNumber = "12345$id",
                numbers = listOf(LabeledNumber("12345$id", "Mobile"))
            )
        }

        val result = DialpadSearch.findContactMatches(people, "12345", limit = 2)

        assertEquals(2, result.size)
    }

    @Test
    fun `blank or unrelated query returns no matches`() {
        val person = contact(
            id = 6,
            name = "Alice",
            primaryNumber = "1111111111",
            numbers = listOf(LabeledNumber("1111111111", "Mobile"))
        )

        assertTrue(DialpadSearch.findContactMatches(listOf(person), "").isEmpty())
        assertTrue(DialpadSearch.findContactMatches(listOf(person), "999999").isEmpty())
    }
}
