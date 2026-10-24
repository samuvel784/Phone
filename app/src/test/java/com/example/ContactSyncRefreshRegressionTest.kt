/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.model.CallRecord
import com.example.model.CallType
import com.example.model.Contact
import com.example.model.LabeledNumber
import com.example.model.getInitials
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression coverage for contact mutations and the Recent-page identity refresh.
 *
 * The Room flow collector is asynchronous, so these tests exercise the synchronous refresh
 * entry points that the repository uses right after a mutation. They intentionally drive the
 * DAO instead of the system ContactsProvider: the point is that local rows and [ContactCache]
 * agree immediately, without relying on a later flow emission.
 */
@RunWith(RobolectricTestRunner::class)
class ContactSyncRefreshRegressionTest {

    private lateinit var context: Context
    private lateinit var repository: DialerRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AppDatabase.closeInstance()
        repository = DialerRepository(context)
        ContactCache.init(emptyList(), emptyList())
    }

    @After
    fun tearDown() {
        ContactCache.init(emptyList(), emptyList())
        AppDatabase.closeInstance()
    }

    private fun contact(id: Long, name: String, number: String) = Contact(
        id = id,
        rawContactId = id,
        contactId = id,
        name = name,
        number = number,
        label = "Mobile",
        favorite = false,
        avatarText = getInitials(name),
        avatarBgValue = 0L,
        avatarTextColorValue = 0L,
        numbers = listOf(LabeledNumber(number, "Mobile", isPrimary = true))
    )

    private fun record(number: String, name: String) = CallRecord(
        name = name,
        number = number,
        label = "",
        timestamp = "Jan 1, 10:00",
        type = CallType.OUTGOING,
        avatarText = getInitials(name),
        avatarBgValue = 0L,
        avatarTextColorValue = 0L,
        duration = 5L,
        hasVoicemail = false,
        timestampMs = 1_700_000_000_000L
    )

    @Test
    fun `adding a contact updates the cache and the local recents row immediately`() = runBlocking {
        repository.dao.insertCallLogs(listOf(record("+14155550100", "+14155550100")))
        repository.dao.insertContacts(listOf(contact(1L, "Alice Added", "+14155550100")))

        repository.refreshContactCacheFromDatabase()
        repository.refreshLocalCallHistoryIdentity()

        assertEquals("Alice Added", ContactCache.getContact("+14155550100")?.name)
        val row = repository.dao.getCallHistoryByNumber("+14155550100").single()
        assertEquals("Alice Added", row.name)
        assertEquals("Mobile", row.label)
    }

    @Test
    fun `renaming a contact replaces the stale recents name`() = runBlocking {
        val original = contact(2L, "Bob Old", "+14155550101")
        repository.dao.insertContacts(listOf(original))
        repository.dao.insertCallLogs(listOf(record("+14155550101", "Bob Old")))
        repository.refreshContactCacheFromDatabase()
        repository.refreshLocalCallHistoryIdentity()

        repository.dao.updateContact(original.copy(name = "Bob New"))
        repository.refreshContactCacheFromDatabase()
        repository.refreshLocalCallHistoryIdentity(previousName = "Bob Old")

        assertEquals("Bob New", ContactCache.getContact("+14155550101")?.name)
        assertEquals("Bob New", repository.dao.getCallHistoryByNumber("+14155550101").single().name)
    }

    @Test
    fun `deleting a contact reverts recents to the number instead of keeping the old name`() = runBlocking {
        val victim = contact(3L, "Carol Deleted", "+14155550102")
        repository.dao.insertContacts(listOf(victim))
        repository.dao.insertCallLogs(listOf(record("+14155550102", "Carol Deleted")))
        repository.refreshContactCacheFromDatabase()
        repository.refreshLocalCallHistoryIdentity()

        repository.dao.deleteContact(victim)
        repository.refreshContactCacheFromDatabase()
        repository.refreshLocalCallHistoryIdentity(previousName = "Carol Deleted")

        assertNull(ContactCache.getContact("+14155550102"))
        assertEquals("+14155550102", repository.dao.getCallHistoryByNumber("+14155550102").single().name)
    }

    @Test
    fun `two contacts sharing a number are ambiguous and never pick an arbitrary name`() = runBlocking {
        repository.dao.insertContacts(
            listOf(
                contact(41L, "Person One", "+14155550103"),
                contact(42L, "Person Two", "+14155550103")
            )
        )
        repository.refreshContactCacheFromDatabase()
        repository.dao.insertCallLogs(listOf(record("+14155550103", "+14155550103")))
        repository.refreshLocalCallHistoryIdentity()

        assertNull(ContactCache.getContact("+14155550103"))
        assertEquals("+14155550103", repository.dao.getCallHistoryByNumber("+14155550103").single().name)
    }

    @Test
    fun `numbers sharing only a national tail are never cross matched`() = runBlocking {
        repository.dao.insertContacts(
            listOf(
                contact(51L, "US Contact", "+14155551234"),
                contact(52L, "UK Contact", "+442075551234")
            )
        )
        repository.refreshContactCacheFromDatabase()

        assertEquals("US Contact", ContactCache.getContact("+14155551234")?.name)
        assertEquals("UK Contact", ContactCache.getContact("+442075551234")?.name)
        assertNull(ContactCache.getContact("+3315551234"))
    }
}
