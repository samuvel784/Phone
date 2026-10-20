/*
 * Copyright (C) 2026 Nova Dialer contributors
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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateFinderTest {

    private fun row(raw: Long, contact: Long, name: String, number: String) =
        ContactPhoneRow(rawContactId = raw, contactId = contact, name = name, number = number)

    @Test
    fun twoDistinctContactsSharingANumberFormOneGroup() {
        val groups = DuplicateFinder.groupDuplicates(
            listOf(
                row(1, 101, "Alice", "9876543210"),
                row(2, 102, "Alice Backup", "+91 98765 43210")
            )
        )
        assertEquals(1, groups.size)
        assertEquals(2, groups[0].contacts.size)
        val ids = groups[0].contacts.map { it.contactId }.toSet()
        assertEquals(setOf(101L, 102L), ids)
    }

    @Test
    fun rawContactsOfTheSameAggregatedContactAreNotDuplicates() {
        // After a merge, both raw contacts share one CONTACT_ID: not a duplicate.
        val groups = DuplicateFinder.groupDuplicates(
            listOf(
                row(1, 200, "Alice", "9876543210"),
                row(2, 200, "Alice", "9876543210")
            )
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun multipleRawContactsOfOneContactAreFoldedIntoASingleEntry() {
        val groups = DuplicateFinder.groupDuplicates(
            listOf(
                row(1, 300, "Bob", "9876543210"),
                row(2, 300, "Bob", "9876543210"), // second raw of the same contact
                row(3, 301, "Bob Work", "9876543210")
            )
        )
        assertEquals(1, groups.size)
        val bob = groups[0].contacts.first { it.contactId == 300L }
        assertEquals(setOf(1L, 2L), bob.rawContactIds.toSet())
        assertEquals(2, groups[0].contacts.size)
    }

    @Test
    fun differentNumbersDoNotGroup() {
        val groups = DuplicateFinder.groupDuplicates(
            listOf(
                row(1, 101, "Alice", "9876543210"),
                row(2, 102, "Bob", "1234567890")
            )
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun threeDistinctContactsSharingANumberFormOneGroupOfThree() {
        val groups = DuplicateFinder.groupDuplicates(
            listOf(
                row(1, 1, "A", "5551234567"),
                row(2, 2, "B", "5551234567"),
                row(3, 3, "C", "555-123-4567")
            )
        )
        assertEquals(1, groups.size)
        assertEquals(3, groups[0].contacts.size)
    }

    @Test
    fun shortNumbersAreIgnored() {
        val groups = DuplicateFinder.groupDuplicates(
            listOf(
                row(1, 101, "A", "12345"),
                row(2, 102, "B", "12345")
            )
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun blankNumbersAndInvalidIdsAreSkipped() {
        val groups = DuplicateFinder.groupDuplicates(
            listOf(
                row(1, 101, "A", ""),
                row(-1, 102, "B", "9876543210"),
                row(3, -1, "C", "9876543210")
            )
        )
        assertTrue(groups.isEmpty())
    }

    @Test
    fun sharedNumberUsesTheFullDisplayedNumberNotTheNormalisedKey() {
        val groups = DuplicateFinder.groupDuplicates(
            listOf(
                row(1, 101, "A", "+91 98765 43210"),
                row(2, 102, "B", "9876543210")
            )
        )
        assertEquals(1, groups.size)
        assertEquals("+91 98765 43210", groups[0].sharedNumber)
    }

    @Test
    fun normalizeUsesTrailingTenDigits() {
        assertEquals("9876543210", DuplicateFinder.normalize("+91 98765 43210"))
        assertEquals("12345", DuplicateFinder.normalize("12345"))
    }
}
