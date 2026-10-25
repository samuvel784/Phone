/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.ui.components

import com.example.model.Contact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentRowIdentityTest {

    private fun contact(name: String, number: String) = Contact(
        id = 1L,
        contactId = 10L,
        number = number,
        name = name,
        label = "Mobile",
        avatarText = name.take(1),
        avatarBgValue = 0L,
        avatarTextColorValue = 0L
    )

    @Test
    fun `saved contact shows its current name and offers edit`() {
        val row = resolveRecentRowIdentity("+919876543210", contact("Amma", "+919876543210"))

        assertEquals("Amma", row.displayName)
        assertTrue(row.action is RecentRowAction.Edit)
        assertEquals(
            "Amma",
            (row.action as RecentRowAction.Edit).contact.name
        )
    }

    @Test
    fun `deleted contact falls back to the number and offers add`() {
        val row = resolveRecentRowIdentity("+919876543210", null)

        assertEquals("+919876543210", row.displayName)
        assertNull(row.savedContact)
        assertTrue(row.action is RecentRowAction.Add)
        assertEquals("+919876543210", (row.action as RecentRowAction.Add).number)
    }

    @Test
    fun `stored call-time name is never used when the contact is gone`() {
        // resolveRecentRowIdentity only accepts the current contact; the caller's stale
        // CallRecord.name is not an input, so it cannot resurface here.
        val row = resolveRecentRowIdentity("+14155550100", null)
        assertEquals("+14155550100", row.displayName)
        assertTrue(row.action is RecentRowAction.Add)
    }
}
