/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import com.example.model.Contact
import com.example.model.LabeledNumber
import com.example.util.PhoneNumberNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
class ContactIdentityAuthorityTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context as Application)
            .grantPermissions(android.Manifest.permission.READ_CONTACTS)
        PhoneNumberNormalizer.setRegion("IN")
        ContactCache.init(emptyList(), emptyList(), "IN")
    }

    private fun contact(
        id: Long,
        contactId: Long,
        name: String,
        vararg numbers: String
    ) = Contact(
        id = id,
        contactId = contactId,
        number = numbers.first(),
        name = name,
        label = "Mobile",
        avatarText = name.take(1),
        avatarBgValue = 0L,
        avatarTextColorValue = 0L,
        numbers = numbers.mapIndexed { index, value ->
            LabeledNumber(number = value, label = "Mobile", isPrimary = index == 0)
        }
    )

    @Test
    fun `same number on two different contact ids is ambiguous and never picks a name`() {
        ContactCache.init(
            listOf(
                contact(1, 101, "Alice", "9876543210"),
                contact(2, 202, "Bob", "9876543210")
            ),
            emptyList(),
            "IN"
        )

        assertTrue(ContactCache.lookup("+919876543210") is ContactLookup.Ambiguous)
        assertNull(ContactCache.getContact("9876543210"))
        assertNull(getContactNameFromNumber(context, "9876543210"))
    }

    @Test
    fun `one contact with duplicate and secondary numbers is unique for every number`() {
        ContactCache.init(
            listOf(
                contact(
                    1, 101, "Alice",
                    "+919876543210", "9876543210", "+919800000001"
                )
            ),
            emptyList(),
            "IN"
        )

        assertEquals("Alice", ContactCache.getContact("9876543210")?.name)
        assertEquals("Alice", ContactCache.getContact("+91 98765 43210")?.name)
        assertEquals("Alice", ContactCache.getContact("09876543210")?.name)
        assertEquals("Alice", ContactCache.getContact("+919800000001")?.name)
    }

    @Test
    fun `unknown number sharing the final ten digits is not a contact`() {
        ContactCache.init(
            listOf(contact(1, 101, "Alice", "+14155550100")),
            emptyList(),
            "US"
        )

        assertEquals("Alice", ContactCache.getContact("+14155550100")?.name)
        assertNull(ContactCache.getContact("+4414155550100"))
        assertTrue(ContactCache.lookup("+4414155550100") is ContactLookup.NotSaved)
        assertNull(getContactNameFromNumber(context, "+4414155550100"))
    }

    @Test
    fun `indian national and E164 forms resolve to the same saved contact`() {
        ContactCache.init(
            listOf(contact(1, 101, "Amma", "+91 98765 43210")),
            emptyList(),
            "IN"
        )

        assertEquals("Amma", ContactCache.getContact("09876543210")?.name)
        assertEquals("Amma", ContactCache.getContact("9876543210")?.name)
        assertEquals("Amma", getContactNameFromNumber(context, "98765 43210"))
    }

    @Test
    fun `rename and deletion are reflected immediately with existing history`() {
        ContactCache.init(
            listOf(contact(1, 101, "Old Name", "9876543210")),
            emptyList(),
            "IN"
        )
        assertEquals("Old Name", ContactCache.getContact("9876543210")?.name)

        // Contact renamed in the provider, then synced.
        ContactCache.init(
            listOf(contact(1, 101, "New Name", "9876543210")),
            emptyList(),
            "IN"
        )
        assertEquals("New Name", ContactCache.getContact("9876543210")?.name)

        // Contact deleted: the old snapshot must not survive as a saved identity.
        ContactCache.init(emptyList(), emptyList(), "IN")
        assertTrue(ContactCache.lookup("9876543210") is ContactLookup.NotSaved)
        assertNull(getContactNameFromNumber(context, "9876543210"))
    }

    @Test
    fun `provider lookup returns a name only for one unique contact id`() {
        registerProvider(rows = listOf(77L to "Provider Alice"))
        ContactCache.init(emptyList(), emptyList(), "IN")

        assertEquals("Provider Alice", getContactNameFromNumber(context, "+919876543210"))
    }

    @Test
    fun `provider lookup refuses to choose between two contact ids`() {
        registerProvider(rows = listOf(77L to "Alice", 88L to "Bob"))
        ContactCache.init(emptyList(), emptyList(), "IN")

        assertNull(getContactNameFromNumber(context, "+919876543210"))
    }

    @Test
    fun `provider permission denial never falls back to a name`() {
        registerProvider(rows = listOf(77L to "Alice"), deny = true)
        ContactCache.init(emptyList(), emptyList(), "IN")

        assertNull(getContactNameFromNumber(context, "+919876543210"))
    }

    private fun registerProvider(rows: List<Pair<Long, String>>, deny: Boolean = false) {
        val provider = FakePhoneLookupProvider(rows, deny)
        // PhoneLookup's authority is the standard contacts provider authority.
        ShadowContentResolver.registerProviderInternal("com.android.contacts", provider)
    }

    private class FakePhoneLookupProvider(
        private val rows: List<Pair<Long, String>>,
        val deny: Boolean
    ) : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor {
            if (deny) throw SecurityException("READ_CONTACTS denied")
            val cursor = MatrixCursor(
                arrayOf(
                    ContactsContract.PhoneLookup.CONTACT_ID,
                    ContactsContract.PhoneLookup.DISPLAY_NAME
                )
            )
            rows.forEach { (id, name) -> cursor.addRow(arrayOf<Any>(id, name)) }
            return cursor
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ): Int = 0
    }
}
