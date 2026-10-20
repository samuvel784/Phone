/*
 * Copyright (C) 2026 Nova Dialer contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guard-level coverage for the v8 -> v9 schema repair.
 *
 * The repository contains only the exported v9 schema (`app/schemas/.../9.json`), no v8
 * fixture and no historical source. This test therefore does **not** claim to replay an
 * actual v8 database. It verifies the defensive, idempotent `isCompleted` repair used by
 * `MIGRATION_8_9`: legacy reminder rows must survive, the new column must default to
 * pending, and an already-present/completed value must never be reset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationSchemaGuardTest {

    private var helper: SupportSQLiteOpenHelper? = null
    private var database: SupportSQLiteDatabase? = null
    private var databaseName: String? = null

    @After
    fun tearDown() {
        database?.close()
        helper?.close()
        databaseName?.let { ApplicationProvider.getApplicationContext<Context>().deleteDatabase(it) }
    }

    @Test
    fun `legacy reminder table gains isCompleted and keeps its rows`() {
        val db = openLegacyDatabase(
            createSql = """
                CREATE TABLE `call_reminders` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `number` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `reminderTime` INTEGER NOT NULL,
                    `note` TEXT NOT NULL
                )
            """.trimIndent(),
            insertSql = """
                INSERT INTO `call_reminders` (`number`, `name`, `reminderTime`, `note`)
                VALUES ('legacy-number', 'Legacy contact', 1234567890, 'keep this note')
            """.trimIndent()
        )

        AppDatabase.ensureSchemaV9(db)

        db.query(
            "SELECT `number`, `name`, `reminderTime`, `note`, `isCompleted` FROM `call_reminders`"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("legacy-number", cursor.getString(0))
            assertEquals("Legacy contact", cursor.getString(1))
            assertEquals(1234567890L, cursor.getLong(2))
            assertEquals("keep this note", cursor.getString(3))
            assertEquals(0, cursor.getInt(4))
            assertFalse(cursor.moveToNext())
        }

        // Running the repair twice must be harmless and must not duplicate or reset rows.
        AppDatabase.ensureSchemaV9(db)
        db.query("SELECT COUNT(*) FROM `call_reminders`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
    }

    @Test
    fun `existing completed flag is preserved`() {
        val db = openLegacyDatabase(
            createSql = """
                CREATE TABLE `call_reminders` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `number` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `reminderTime` INTEGER NOT NULL,
                    `isCompleted` INTEGER NOT NULL,
                    `note` TEXT NOT NULL
                )
            """.trimIndent(),
            insertSql = """
                INSERT INTO `call_reminders`
                    (`number`, `name`, `reminderTime`, `isCompleted`, `note`)
                VALUES ('legacy-number', 'Legacy contact', 1234567890, 1, 'already done')
            """.trimIndent()
        )

        AppDatabase.ensureSchemaV9(db)

        db.query("SELECT `isCompleted`, `note` FROM `call_reminders`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals("already done", cursor.getString(1))
            assertFalse(cursor.moveToNext())
        }
    }

    @Test
    fun `room 8 to 9 migration accepts the repaired legacy table`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-room-${System.nanoTime()}.db"
        databaseName = name

        createVersion8Database(
            context = context,
            name = name,
            createSql = """
                CREATE TABLE `call_reminders` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `number` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `reminderTime` INTEGER NOT NULL,
                    `note` TEXT NOT NULL
                )
            """.trimIndent(),
            insertSql = """
                INSERT INTO `call_reminders` (`number`, `name`, `reminderTime`, `note`)
                VALUES ('legacy-number', 'Legacy contact', 1234567890, 'keep this note')
            """.trimIndent()
        )

        val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_8_9)
            .allowMainThreadQueries()
            .build()

        try {
            room.query(
                "SELECT `number`, `note`, `isCompleted` FROM `call_reminders`",
                null
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("legacy-number", cursor.getString(0))
                assertEquals("keep this note", cursor.getString(1))
                assertEquals(0, cursor.getInt(2))
                assertFalse(cursor.moveToNext())
            }
        } finally {
            room.close()
        }
    }

    private fun createVersion8Database(
        context: Context,
        name: String,
        createSql: String,
        insertSql: String
    ) {
        val callback = object : SupportSQLiteOpenHelper.Callback(8) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(createSql)
                db.execSQL(insertSql)
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(callback)
            .build()

        FrameworkSQLiteOpenHelperFactory().create(configuration).use { helper ->
            helper.writableDatabase.use { db ->
                db.query("PRAGMA user_version").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(8, cursor.getInt(0))
                }
            }
        }
    }

    private fun openLegacyDatabase(createSql: String, insertSql: String): SupportSQLiteDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-guard-${System.nanoTime()}.db"
        databaseName = name

        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(createSql)
                db.execSQL(insertSql)
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(callback)
            .build()

        val openedHelper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        helper = openedHelper
        return openedHelper.writableDatabase.also { database = it }
    }
}
