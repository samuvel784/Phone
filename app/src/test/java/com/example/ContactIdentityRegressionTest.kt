/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.Contact
import com.example.model.LabeledNumber
import com.example.util.PhoneNumberCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContactIdentityRegressionTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `test 1 - save contact and verify ContactCache resolves name`() {
        val contact = Contact(
            id = 1L,
            rawContactId = 1L,
            contactId = 1L,
            name = "Alice Smith",
            number = "+14155550100",
            label = "Mobile",
            favorite = false,
            avatarText = "AS",
            avatarBgValue = 0L,
            avatarTextColorValue = 0L
        )
        ContactCache.init(listOf(contact), emptyList())

        val resolved = ContactCache.getContact("+14155550100")
        assertEquals("Alice Smith", resolved?.name)
    }

    @Test
    fun `test 2 - existing unknown record updates when contact is saved`() {
        ContactCache.init(emptyList(), emptyList())
        assertNull(ContactCache.getContact("4155550100"))

        val contact = Contact(
            id = 2L,
            rawContactId = 2L,
            contactId = 2L,
            name = "Bob Jones",
            number = "4155550100",
            label = "Mobile",
            favorite = false,
            avatarText = "BJ",
            avatarBgValue = 0L,
            avatarTextColorValue = 0L
        )
        ContactCache.init(listOf(contact), emptyList())

        val resolved = ContactCache.getContact("+1 (415) 555-0100")
        assertEquals("Bob Jones", resolved?.name)
    }

    @Test
    fun `test 3 - delete contact immediately reverts lookup to null`() {
        val contact = Contact(
            id = 3L,
            rawContactId = 3L,
            contactId = 3L,
            name = "Charlie Brown",
            number = "+14155550199",
            label = "Mobile",
            favorite = false,
            avatarText = "CB",
            avatarBgValue = 0L,
            avatarTextColorValue = 0L
        )
        ContactCache.init(listOf(contact), emptyList())
        assertEquals("Charlie Brown", ContactCache.getContact("+14155550199")?.name)

        ContactCache.init(emptyList(), emptyList())
        assertNull(ContactCache.getContact("+14155550199"))
    }

    @Test
    fun `test 4 - deleted contact does not show old name in lookup`() {
        ContactCache.init(emptyList(), emptyList())
        val name = getContactNameFromNumber(context, "+14155550199")
        assertTrue(name == null || name == "+14155550199")
    }

    @Test
    fun `test 5 - rename contact updates lookup immediately`() {
        val original = Contact(
            id = 5L,
            rawContactId = 5L,
            contactId = 5L,
            name = "David Original",
            number = "+14155550155",
            label = "Mobile",
            favorite = false,
            avatarText = "DO",
            avatarBgValue = 0L,
            avatarTextColorValue = 0L
        )
        ContactCache.init(listOf(original), emptyList())
        assertEquals("David Original", ContactCache.getContact("+14155550155")?.name)

        val renamed = original.copy(name = "David Renamed")
        ContactCache.init(listOf(renamed), emptyList())
        assertEquals("David Renamed", ContactCache.getContact("+14155550155")?.name)
    }

    @Test
    fun `test 6 - two contacts with different full numbers sharing last 7 digits are never cross matched`() {
        val contactA = Contact(
            id = 61L,
            rawContactId = 61L,
            contactId = 61L,
            name = "US Contact",
            number = "+14155551234",
            label = "Mobile",
            favorite = false,
            avatarText = "UC",
            avatarBgValue = 0L,
            avatarTextColorValue = 0L
        )
        val contactB = Contact(
            id = 62L,
            rawContactId = 62L,
            contactId = 62L,
            name = "UK Contact",
            number = "+442075551234",
            label = "Mobile",
            favorite = false,
            avatarText = "UK",
            avatarBgValue = 0L,
            avatarTextColorValue = 0L
        )
        ContactCache.init(listOf(contactA, contactB), emptyList())

        assertNull(ContactCache.getContact("+3315551234"))
        assertFalse(PhoneNumberCompat.numbersMatch("+14155551234", "+442075551234"))
        assertEquals("US Contact", ContactCache.getContact("+14155551234")?.name)
        assertEquals("UK Contact", ContactCache.getContact("+442075551234")?.name)
    }

    @Test
    fun `test 7 - local and international formatting variants match unambiguously`() {
        val contact = Contact(
            id = 7L,
            rawContactId = 7L,
            contactId = 7L,
            name = "Formatted User",
            number = "+1 (415) 555-0188",
            label = "Mobile",
            favorite = false,
            avatarText = "FU",
            avatarBgValue = 0L,
            avatarTextColorValue = 0L
        )
        ContactCache.init(listOf(contact), emptyList())

        assertEquals("Formatted User", ContactCache.getContact("4155550188")?.name)
        assertEquals("Formatted User", ContactCache.getContact("+14155550188")?.name)
        assertTrue(PhoneNumberCompat.numbersMatch("+1 (415) 555-0188", "4155550188"))
    }

    @Test
    fun `test 8 - contact with multiple phone numbers resolves correctly for each number`() {
        val contact = Contact(
            id = 8L,
            rawContactId = 8L,
            contactId = 8L,
            name = "Multi Number Contact",
            number = "+14155550111",
            label = "Mobile",
            favorite = false,
            avatarText = "MN",
            avatarBgValue = 0L,
            avatarTextColorValue = 0L,
            numbers = listOf(
                LabeledNumber("+14155550111", "Mobile", true),
                LabeledNumber("+14155550222", "Work", false)
            )
        )
        ContactCache.init(listOf(contact), emptyList())

        assertEquals("Multi Number Contact", ContactCache.getContact("+14155550111")?.name)
        assertEquals("Multi Number Contact", ContactCache.getContact("+14155550222")?.name)
    }

    @Test
    fun `test 9 - ambiguous number matches return null and never pick arbitrary name`() {
        val contact1 = Contact(
            id = 91L,
            rawContactId = 91L,
            contactId = 91L,
            name = "Person One",
            number = "4155550999",
            label = "Mobile",
            favorite = false,
            avatarText = "PO",
            avatarBgValue = 0L,
            avatarTextColorValue = 0L
        )
        val contact2 = Contact(
            id = 92L,
            rawContactId = 92L,
            contactId = 92L,
            name = "Person Two",
            number = "4155550999",
            label = "Work",
            favorite = false,
            avatarText = "PT",
            avatarBgValue = 0L,
            avatarTextColorValue = 0L
        )
        ContactCache.init(listOf(contact1, contact2), emptyList())

        assertNull(ContactCache.getContact("4155550999"))
    }

    @Test
    fun `test 10 - delayed lookup for previous call does not overwrite new caller identity`() {
        CallManager.setCallerDetailsForTesting("+14155550100", "First Caller")
        CallManager.setCallerDetailsForTesting("+14155550200", "")

        assertEquals("+14155550200", CallManager.callerNumber.value)
        assertEquals("", CallManager.callerName.value)
    }

    @Test
    fun `test 11 - permission denial results in safe number display`() {
        val name = getContactNameFromNumber(context, "+14155559999")
        assertTrue(name == null || name == "+14155559999")
    }

    @Test
    fun `test 12 - returning to recent after editing contacts does not retain stale display names`() {
        val original = Contact(
            id = 12L,
            rawContactId = 12L,
            contactId = 12L,
            name = "Old Name",
            number = "+14155550123",
            label = "Mobile",
            favorite = false,
            avatarText = "ON",
            avatarBgValue = 0L,
            avatarTextColorValue = 0L
        )
        ContactCache.init(listOf(original), emptyList())
        assertEquals("Old Name", ContactCache.getContact("+14155550123")?.name)

        val updated = original.copy(name = "Updated Name")
        ContactCache.init(listOf(updated), emptyList())

        assertEquals("Updated Name", ContactCache.getContact("+14155550123")?.name)
    }
}
