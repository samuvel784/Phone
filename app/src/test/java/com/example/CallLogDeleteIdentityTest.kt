/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CallLog
import android.telecom.TelecomManager
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.model.CallRecord
import com.example.model.CallType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * A local call-history row may share its integer key with an unrelated provider row after a
 * backup restore. Deleting the local row must never delete the provider row unless the provider
 * record is provably the same call (same _ID, number and date).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CallLogDeleteIdentityTest {

    /** Minimal CallLog provider that only implements the delete path under test. */
    class FakeCallLogProvider : ContentProvider() {
        data class Row(val id: String, val number: String, val date: String)

        val rows = mutableListOf<Row>()
        var lastSelection: String? = null

        override fun onCreate(): Boolean = true

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
            lastSelection = selection
            val args = selectionArgs ?: return 0
            if (args.size < 3) return 0
            val match = rows.filter { it.id == args[0] && it.number == args[1] && it.date == args[2] }
            rows.removeAll(match)
            return match.size
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor? = null
        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ): Int = 0
        override fun getType(uri: Uri): String? = null
    }

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var provider: FakeCallLogProvider

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AppDatabase.closeInstance()
        database = AppDatabase.getDatabase(context)

        // The delete must only run for the default Phone app, so make this app the default.
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        Shadows.shadowOf(telecom).setDefaultDialerPackage(context.packageName)

        provider = Robolectric.buildContentProvider(FakeCallLogProvider::class.java)
            .create(CallLog.AUTHORITY)
            .get()
    }

    @After
    fun tearDown() {
        AppDatabase.closeInstance()
    }

    private fun record(id: Int, number: String, timestampMs: Long) = CallRecord(
        id = id,
        name = "Test",
        number = number,
        label = "Mobile",
        timestamp = "Jan 1, 10:00",
        type = CallType.OUTGOING,
        avatarText = "T",
        avatarBgValue = 0L,
        avatarTextColorValue = 0L,
        duration = 12,
        hasVoicemail = false,
        timestampMs = timestampMs
    )

    @Test
    fun deleteMatchingRow_removesTheProviderRecord() = runBlocking {
        provider.rows += FakeCallLogProvider.Row("999", "5550100", "1700000000000")
        database.dialerDao().insertCallLogs(listOf(record(999, "5550100", 1700000000000L)))

        DialerRepository(context).deleteCallLog(999)

        assertTrue(provider.rows.isEmpty())
        assertEquals(0, database.dialerDao().getCallLogCount())
        assertNotNull(provider.lastSelection)
        assertTrue(provider.lastSelection!!.contains(CallLog.Calls.NUMBER))
        assertTrue(provider.lastSelection!!.contains(CallLog.Calls.DATE))
    }

    @Test
    fun localOnlyRowWithCollidingId_doesNotDeleteUnrelatedProviderRecord() = runBlocking {
        // A restored/local row reused id 1 while the system provider has a different call at 1.
        provider.rows += FakeCallLogProvider.Row("1", "5559999", "1600000000000")
        database.dialerDao().insertCallLogs(listOf(record(1, "5550100", 1700000000000L)))

        DialerRepository(context).deleteCallLog(1)

        assertEquals(1, provider.rows.size)
        assertEquals("5559999", provider.rows.single().number)
        assertEquals(0, database.dialerDao().getCallLogCount())
    }
}
