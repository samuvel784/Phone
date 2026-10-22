/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.model.CallRecord
import com.example.model.CallType
import com.example.model.Contact
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * Instrumentation coverage for the real, file-backed SQLCipher path.
 *
 * The unit tests use Robolectric, whose Room in-memory bypass never touches SQLCipher. These tests
 * use only disposable files under the instrumentation target's cache directory and never point at
 * `getDatabasePath(AppDatabase.DATABASE_NAME)` or the production key.
 */
@RunWith(AndroidJUnit4::class)
class SqlCipherRecoveryInstrumentedTest {

    private lateinit var root: File
    private val key = ByteArray(32) { index -> (index * 17 + 3).toByte() }
    private val wrongKey = ByteArray(32) { index -> (index * 29 + 11).toByte() }

    @Before
    fun setUp() {
        root = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "sqlcipher-recovery-${UUID.randomUUID()}"
        )
        assertTrue(root.mkdirs() || root.isDirectory)
    }

    @After
    fun tearDown() {
        // Only this test's isolated directory is removed. A failed test does not touch app data.
        root.deleteRecursively()
    }

    @Test
    fun freshEncryptedDatabaseUsesKeyAndSurvivesCloseAndReopen() {
        val file = File(root, "fresh-encrypted.db")
        createEncryptedDatabase(file, key)

        assertFalse("SQLCipher files must not have a plaintext SQLite header", isPlaintext(file))
        val first = SqlCipherDatabaseInspector.readEncrypted(file, key)
        assertNotNull(first)
        assertTrue(first!!.integrityOk)
        assertEquals(AppDatabase.DATABASE_VERSION, first.userVersion)
        assertEquals(1L, first.tableCounts["contacts"] ?: -1L)
        assertEquals(1L, first.tableCounts["call_history"] ?: -1L)

        val reopened = openEncryptedDatabase(file, key)
        try {
            val contacts = runBlocking { reopened.dialerDao().getAllContactsList() }
            val calls = runBlocking { reopened.dialerDao().getAllCallHistoryList() }
            assertEquals(listOf("Alice Example"), contacts.map { it.name })
            assertEquals(listOf("+15551234567"), calls.map { it.number })
        } finally {
            reopened.close()
        }
    }

    @Test
    fun plaintextMigrationPreservesRepresentativeRowsAndRemovesSwap() {
        val file = File(root, "legacy-plain.db")
        createPlaintextDatabase(file)
        val family = DatabaseFileFamily.from(file)

        val result = PlaintextMigrationCoordinator(
            family = family,
            key = key,
            reader = SqlCipherDatabaseInspector,
            exporter = SqlCipherDatabaseInspector
        ).migrate()

        assertEquals(PlaintextMigrationCoordinator.Result.Migrated, result)
        assertFalse(isPlaintext(file))
        assertFalse(family.swapBackup.exists())
        assertFalse(family.encryptedTemp.exists())

        val snapshot = SqlCipherDatabaseInspector.readEncrypted(file, key)
        assertNotNull(snapshot)
        assertTrue(snapshot!!.integrityOk)
        assertEquals(1L, snapshot.tableCounts["contacts"] ?: -1L)
        assertEquals(1L, snapshot.tableCounts["call_history"] ?: -1L)

        val migrated = openEncryptedDatabase(file, key)
        try {
            assertEquals(
                "private note",
                runBlocking { migrated.dialerDao().getAllContactsList() }.single().note
            )
            assertEquals(
                12L,
                runBlocking { migrated.dialerDao().getAllCallHistoryList() }.single().duration
            )
        } finally {
            migrated.close()
        }
    }

    @Test
    fun wrongKeyAndUnreadableFilesFailWithoutChangingEvidence() {
        val encrypted = File(root, "wrong-key.db")
        createEncryptedDatabase(encrypted, key)
        val original = encrypted.readBytes()

        assertNull(SqlCipherDatabaseInspector.readEncrypted(encrypted, wrongKey))
        assertNull(SqlCipherDatabaseInspector.readPlaintext(encrypted))
        assertTrue(original.contentEquals(encrypted.readBytes()))

        val junk = File(root, "junk.db").apply {
            writeBytes(ByteArray(4096) { index -> (index * 7).toByte() })
        }
        val junkBefore = junk.readBytes()
        val exported = File(root, "should-not-exist.db")
        assertFalse(SqlCipherDatabaseInspector.export(junk, exported, key))
        assertNull(SqlCipherDatabaseInspector.readEncrypted(junk, key))
        assertNull(SqlCipherDatabaseInspector.readPlaintext(junk))
        assertFalse(exported.exists())
        assertTrue(junkBefore.contentEquals(junk.readBytes()))
    }

    @Test
    fun emptyAndIncompleteCandidatesAreRejectedWithoutReplacingTheSource() {
        val empty = File(root, "empty.db").apply { createNewFile() }
        assertNull(SqlCipherDatabaseInspector.readPlaintext(empty))
        assertNull(SqlCipherDatabaseInspector.readEncrypted(empty, key))

        val corrupt = File(root, "corrupt.db").apply { writeText("not a sqlite database") }
        val corruptBytes = corrupt.readBytes()
        val corruptResult = PlaintextMigrationCoordinator(
            family = DatabaseFileFamily.from(corrupt),
            key = key,
            reader = SqlCipherDatabaseInspector,
            exporter = SqlCipherDatabaseInspector
        ).migrate()
        assertTrue(corruptResult is PlaintextMigrationCoordinator.Result.Failed)
        assertTrue(corruptBytes.contentEquals(corrupt.readBytes()))
        assertFalse(DatabaseFileFamily.from(corrupt).swapBackup.exists())

        val source = File(root, "partial-source.db")
        createPlaintextDatabase(source)
        val family = DatabaseFileFamily.from(source)
        family.encryptedTemp.writeText("partial export")
        val partialResult = PlaintextMigrationCoordinator(
            family = family,
            key = key,
            reader = SqlCipherDatabaseInspector,
            exporter = SqlCipherDatabaseInspector
        ).migrate()
        assertTrue(partialResult is PlaintextMigrationCoordinator.Result.Failed)
        assertTrue("the unvalidated temp copy must be preserved", family.encryptedTemp.exists())
        assertTrue("the original source must remain in place", source.exists())
        assertFalse("a failed migration must not create a swap", family.swapBackup.exists())
    }

    @Test
    fun interruptedSwapRestoresTheEncryptedDatabaseAndMovesSidecars() {
        val seed = File(root, "seed.db")
        createEncryptedDatabase(seed, key)

        val active = File(root, "active.db").apply { createNewFile() }
        val family = DatabaseFileFamily.from(active)
        seed.copyTo(family.swapBackup)
        File(active.path + "-wal").writeText("stale-active-wal")
        File(active.path + "-shm").writeText("stale-active-shm")
        File(active.path + "-journal").writeText("stale-active-journal")
        File(family.swapBackup.path + "-wal").writeText("swap-wal")
        File(family.swapBackup.path + "-shm").writeText("swap-shm")
        File(family.swapBackup.path + "-journal").writeText("swap-journal")

        val plan = DatabaseFileGuard.plan(family, hasPersistedKey = true)
        assertTrue(plan is DatabaseFilePlan.RestoreSwap)
        DatabaseFileGuard.restoreInterruptedSwap(family, plan as DatabaseFilePlan.RestoreSwap)

        assertFalse(family.swapBackup.exists())
        assertTrue(active.exists())
        val quarantine = File(root, "${active.name}_empty_bak.db")
        assertTrue("the empty active file must be retained", quarantine.exists())
        assertEquals("stale-active-wal", File(quarantine.path + "-wal").readText())
        assertEquals("stale-active-shm", File(quarantine.path + "-shm").readText())
        assertEquals("stale-active-journal", File(quarantine.path + "-journal").readText())
        assertEquals("swap-wal", File(active.path + "-wal").readText())
        assertEquals("swap-shm", File(active.path + "-shm").readText())
        assertEquals("swap-journal", File(active.path + "-journal").readText())

        // The synthetic sidecars are deliberately arbitrary payloads. Remove them only to verify
        // that the base database restored from the swap is the real SQLCipher database.
        listOf("-wal", "-shm", "-journal").forEach { File(active.path + it).delete() }
        assertNotNull(SqlCipherDatabaseInspector.readEncrypted(active, key))
    }

    @Test
    fun interruptedMigrationCanReuseAValidatedEncryptedTempCopy() {
        val file = File(root, "interrupted.db")
        createPlaintextDatabase(file)
        val family = DatabaseFileFamily.from(file)
        assertTrue(SqlCipherDatabaseInspector.export(file, family.encryptedTemp, key))
        moveFamilyForTest(file, family.swapBackup)

        val plan = DatabaseFileGuard.plan(family, hasPersistedKey = true)
        assertTrue(plan is DatabaseFilePlan.RestoreSwap)
        DatabaseFileGuard.restoreInterruptedSwap(family, plan as DatabaseFilePlan.RestoreSwap)
        assertTrue(file.exists())
        assertFalse(family.swapBackup.exists())
        assertTrue(family.encryptedTemp.exists())

        val result = PlaintextMigrationCoordinator(
            family = family,
            key = key,
            reader = SqlCipherDatabaseInspector,
            exporter = SqlCipherDatabaseInspector
        ).migrate()
        assertEquals(PlaintextMigrationCoordinator.Result.Migrated, result)
        assertFalse(isPlaintext(file))
        assertFalse(family.swapBackup.exists())
        assertNotNull(SqlCipherDatabaseInspector.readEncrypted(file, key))
    }

    @Test
    fun guardFailsClosedAndPreservesLegacyEvidenceInsteadOfCreatingAReplacement() {
        val missing = File(root, "missing.db")
        val family = DatabaseFileFamily.from(missing)
        family.legacyPlaintextBackup.writeText("legacy evidence")
        val plan = DatabaseFileGuard.plan(family, hasPersistedKey = true)

        assertTrue(plan is DatabaseFilePlan.FailClosed)
        assertFalse("the guard must not create an active database", missing.exists())
        assertTrue(family.legacyPlaintextBackup.exists())
        assertEquals("legacy evidence", family.legacyPlaintextBackup.readText())

        val empty = File(root, "zero.db").apply { createNewFile() }
        val emptyFamily = DatabaseFileFamily.from(empty)
        assertTrue(
            DatabaseFileGuard.plan(emptyFamily, hasPersistedKey = true) is DatabaseFilePlan.FailClosed
        )
        assertEquals(0L, empty.length())

        val fresh = File(root, "fresh-install.db")
        assertEquals(
            DatabaseFilePlan.FreshInstall,
            DatabaseFileGuard.plan(DatabaseFileFamily.from(fresh), hasPersistedKey = false)
        )
        assertFalse(fresh.exists())
    }

    private fun createEncryptedDatabase(file: File, databaseKey: ByteArray) {
        val database = openEncryptedDatabase(file, databaseKey)
        try {
            insertRepresentativeRows(database)
        } finally {
            database.close()
        }
    }

    private fun createPlaintextDatabase(file: File) {
        val context = TempContext(
            InstrumentationRegistry.getInstrumentation().targetContext,
            file.parentFile ?: error("missing parent")
        )
        val database = Room.databaseBuilder(context, AppDatabase::class.java, file.name)
            .allowMainThreadQueries()
            .build()
        try {
            insertRepresentativeRows(database)
        } finally {
            database.close()
        }
    }

    private fun openEncryptedDatabase(file: File, databaseKey: ByteArray): AppDatabase {
        val context = TempContext(
            InstrumentationRegistry.getInstrumentation().targetContext,
            file.parentFile ?: error("missing parent")
        )
        return Room.databaseBuilder(context, AppDatabase::class.java, file.name)
            .openHelperFactory(SupportOpenHelperFactory(databaseKey, null, false))
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
    }

    private fun insertRepresentativeRows(database: AppDatabase) {
        runBlocking {
            database.dialerDao().insertContacts(
                listOf(
                    Contact(
                        id = 42L,
                        number = "+15551234567",
                        name = "Alice Example",
                        label = "Mobile",
                        avatarText = "AE",
                        avatarBgValue = 0x123456,
                        avatarTextColorValue = 0xFFFFFF,
                        note = "private note"
                    )
                )
            )
            database.dialerDao().insertCallLogs(
                listOf(
                    CallRecord(
                        id = 7,
                        name = "Alice Example",
                        number = "+15551234567",
                        label = "Mobile",
                        timestamp = "2026-10-21 12:00:00",
                        type = CallType.INCOMING,
                        avatarText = "AE",
                        avatarBgValue = 0x123456,
                        avatarTextColorValue = 0xFFFFFF,
                        duration = 12L,
                        hasVoicemail = false,
                        timestampMs = 1_792_000_000_000L
                    )
                )
            )
        }
    }

    private fun moveFamilyForTest(from: File, to: File) {
        assertTrue(from.renameTo(to))
        for (suffix in listOf("-wal", "-shm", "-journal")) {
            val source = File(from.path + suffix)
            if (source.exists()) {
                assertTrue(source.renameTo(File(to.path + suffix)))
            }
        }
    }

    private fun isPlaintext(file: File): Boolean {
        if (!file.exists() || file.length() < 16L) return false
        val header = ByteArray(16)
        file.inputStream().use { stream ->
            if (stream.read(header) != 16) return false
        }
        return String(header, Charsets.US_ASCII).startsWith("SQLite format 3")
    }

    private class TempContext(base: Context, private val root: File) : ContextWrapper(base) {
        init {
            root.mkdirs()
        }

        override fun getApplicationContext(): Context = this

        override fun getDatabasePath(name: String): File =
            File(root, name).also { it.parentFile?.mkdirs() }
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun loadSqlCipher() {
            System.loadLibrary("sqlcipher")
        }
    }
}


