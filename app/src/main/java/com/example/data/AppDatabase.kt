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

package com.example.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Converters {
    @TypeConverter
    fun fromCallType(value: CallType?): String = (value ?: CallType.INCOMING).name

    @TypeConverter
    fun toCallType(value: String?): CallType = try {
        if (!value.isNullOrBlank()) CallType.valueOf(value) else CallType.INCOMING
    } catch (_: Exception) {
        CallType.INCOMING
    }

    @TypeConverter
    fun fromLabeledNumberList(list: List<LabeledNumber>?): String {
        if (list.isNullOrEmpty()) return ""
        val jsonArray = JSONArray()
        list.forEach { item ->
            jsonArray.put(JSONObject().apply {
                put("num", item.number)
                put("lbl", item.label)
                put("pri", item.isPrimary)
            })
        }
        return jsonArray.toString()
    }

    @TypeConverter
    fun toLabeledNumberList(value: String?): List<LabeledNumber> {
        if (value.isNullOrBlank()) return emptyList()
        val list = mutableListOf<LabeledNumber>()
        try {
            val jsonArray = JSONArray(value)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    LabeledNumber(
                        number = obj.optString("num", ""),
                        label = obj.optString("lbl", "Mobile"),
                        isPrimary = obj.optBoolean("pri", false)
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    @TypeConverter
    fun fromLabeledEmailList(list: List<LabeledEmail>?): String {
        if (list.isNullOrEmpty()) return ""
        val jsonArray = JSONArray()
        list.forEach { item ->
            jsonArray.put(JSONObject().apply {
                put("eml", item.email)
                put("lbl", item.label)
            })
        }
        return jsonArray.toString()
    }

    @TypeConverter
    fun toLabeledEmailList(value: String?): List<LabeledEmail> {
        if (value.isNullOrBlank()) return emptyList()
        val list = mutableListOf<LabeledEmail>()
        try {
            val jsonArray = JSONArray(value)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    LabeledEmail(
                        email = obj.optString("eml", ""),
                        label = obj.optString("lbl", "Home")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    @TypeConverter
    fun fromLabeledAddressList(list: List<LabeledAddress>?): String {
        if (list.isNullOrEmpty()) return ""
        val jsonArray = JSONArray()
        list.forEach { item ->
            jsonArray.put(JSONObject().apply {
                put("adr", item.address)
                put("lbl", item.label)
            })
        }
        return jsonArray.toString()
    }

    @TypeConverter
    fun toLabeledAddressList(value: String?): List<LabeledAddress> {
        if (value.isNullOrBlank()) return emptyList()
        val list = mutableListOf<LabeledAddress>()
        try {
            val jsonArray = JSONArray(value)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    LabeledAddress(
                        address = obj.optString("adr", ""),
                        label = obj.optString("lbl", "Home")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }
}

@Database(
    entities = [
        Contact::class,
        CallRecord::class,
        BlockedNumber::class,
        SpeedDial::class,
        QuickResponse::class,
        AppSetting::class,
        CallNote::class,
        CallRecording::class,
        SpamNumber::class,
        CallReminder::class
    ],
    version = 9,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dialerDao(): DialerDao

    companion object {
        const val DATABASE_NAME = "dialer_database"

        /**
         * v8 -> v9 keeps every table and row. New columns are added with safe defaults;
         * missing tables are created. The old code used destructive downgrade/fallback,
         * which silently removed user data whenever any upgrade check failed.
         */
        internal val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ensureSchemaV9(db)
            }
        }

        private val MIGRATIONS = arrayOf(MIGRATION_8_9)

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun warmUpAsync(context: Context) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val db = getDatabase(context)
                    // Trigger database opening and WAL pragma execution in background
                    db.dialerDao().getContactsCount()
                } catch (_: Throwable) {}
            }
        }

        private fun buildDatabase(appContext: Context): AppDatabase {
            if (isRobolectricTest()) {
                return Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()
            }

            System.loadLibrary("sqlcipher")
            recoverInterruptedPlaintextSwap(appContext)
            val dbKey = DatabaseKeyManager.getDatabaseKey(appContext)
            ensureDatabaseIntegrity(appContext, dbKey)
            // Disable automatic password clearing so Room connection pool and concurrent readers can reopen connections
            val factory = SupportOpenHelperFactory(dbKey, null, false)

            return Room.databaseBuilder(
                appContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(factory)
                // High-concurrency encrypted WAL mode: readers never block writers
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(*MIGRATIONS)
                .build()
        }

        private fun isRobolectricTest(): Boolean {
            return try {
                Class.forName("org.robolectric.Robolectric") != null
            } catch (_: ClassNotFoundException) {
                false
            }
        }

        /**
         * Idempotent v8 -> v9 schema repair. It creates tables that did not exist yet and
         * adds any columns introduced in v9. Existing rows are preserved; SQLite supplies
         * the declared defaults for the new columns.
         */
        private fun ensureSchemaV9(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `contacts` (`id` INTEGER NOT NULL, `rawContactId` INTEGER NOT NULL, `contactId` INTEGER NOT NULL, `number` TEXT NOT NULL, `name` TEXT NOT NULL, `label` TEXT NOT NULL, `favorite` INTEGER NOT NULL, `avatarText` TEXT NOT NULL, `avatarBgValue` INTEGER NOT NULL, `avatarTextColorValue` INTEGER NOT NULL, `t9Mapping` TEXT NOT NULL, `email` TEXT NOT NULL, `photoUri` TEXT NOT NULL, `accountName` TEXT NOT NULL, `accountType` TEXT NOT NULL, `numbers` TEXT NOT NULL, `emails` TEXT NOT NULL, `addresses` TEXT NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `call_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `number` TEXT NOT NULL, `label` TEXT NOT NULL, `timestamp` TEXT NOT NULL, `type` TEXT NOT NULL, `avatarText` TEXT NOT NULL, `avatarBgValue` INTEGER NOT NULL, `avatarTextColorValue` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `hasVoicemail` INTEGER NOT NULL, `photoUri` TEXT NOT NULL, `timestampMs` INTEGER NOT NULL, `isVerified` INTEGER NOT NULL, `simSlot` INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `blocked_numbers` (`number` TEXT NOT NULL, PRIMARY KEY(`number`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `speed_dial` (`key` INTEGER NOT NULL, `number` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`key`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `quick_responses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `message` TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `app_settings` (`key` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`key`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `call_notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `number` TEXT NOT NULL, `note` TEXT NOT NULL, `lastUpdated` INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `call_recordings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `number` TEXT NOT NULL, `name` TEXT NOT NULL, `timestamp` TEXT NOT NULL, `duration` INTEGER NOT NULL, `filePath` TEXT NOT NULL, `note` TEXT NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `spam_numbers` (`number` TEXT NOT NULL, `label` TEXT NOT NULL, PRIMARY KEY(`number`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `call_reminders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `number` TEXT NOT NULL, `name` TEXT NOT NULL, `reminderTime` INTEGER NOT NULL, `isCompleted` INTEGER NOT NULL, `note` TEXT NOT NULL)")

            ensureColumn(db, "contacts", "rawContactId", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(db, "contacts", "contactId", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(db, "contacts", "number", "TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, "contacts", "name", "TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, "contacts", "label", "TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, "contacts", "favorite", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(db, "contacts", "avatarText", "TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, "contacts", "avatarBgValue", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(db, "contacts", "avatarTextColorValue", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(db, "contacts", "t9Mapping", "TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, "contacts", "email", "TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, "contacts", "photoUri", "TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, "contacts", "accountName", "TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, "contacts", "accountType", "TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, "contacts", "numbers", "TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, "contacts", "emails", "TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, "contacts", "addresses", "TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, "contacts", "note", "TEXT NOT NULL DEFAULT ''")

            ensureColumn(db, "call_history", "name", "TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, "call_history", "number", "TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, "call_history", "label", "TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, "call_history", "timestamp", "TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, "call_history", "type", "TEXT NOT NULL DEFAULT 'INCOMING'")
            ensureColumn(db, "call_history", "avatarText", "TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, "call_history", "avatarBgValue", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(db, "call_history", "avatarTextColorValue", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(db, "call_history", "duration", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(db, "call_history", "hasVoicemail", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(db, "call_history", "photoUri", "TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, "call_history", "timestampMs", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(db, "call_history", "isVerified", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(db, "call_history", "simSlot", "INTEGER NOT NULL DEFAULT 1")

            ensureColumn(db, "call_notes", "lastUpdated", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(db, "call_recordings", "note", "TEXT NOT NULL DEFAULT ''")
            ensureColumn(db, "spam_numbers", "label", "TEXT NOT NULL DEFAULT 'Spam'")
            ensureColumn(db, "call_reminders", "note", "TEXT NOT NULL DEFAULT ''")

            db.execSQL("CREATE INDEX IF NOT EXISTS `index_contacts_number` ON `contacts` (`number`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_contacts_name` ON `contacts` (`name`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_contacts_t9Mapping` ON `contacts` (`t9Mapping`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_contacts_accountName` ON `contacts` (`accountName`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_contacts_rawContactId` ON `contacts` (`rawContactId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_contacts_contactId` ON `contacts` (`contactId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_history_number` ON `call_history` (`number`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_call_history_timestamp` ON `call_history` (`timestamp`)")
        }

        private fun ensureColumn(
            db: SupportSQLiteDatabase,
            table: String,
            column: String,
            definition: String
        ) {
            var exists = false
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex >= 0) {
                    while (cursor.moveToNext()) {
                        if (cursor.getString(nameIndex) == column) {
                            exists = true
                            break
                        }
                    }
                }
            }
            if (!exists) {
                db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $definition")
            }
        }

        private fun ensureDatabaseIntegrity(appContext: Context, dbKey: ByteArray) {
            val dbFile = appContext.getDatabasePath(DATABASE_NAME)
            if (!dbFile.exists() || dbFile.length() == 0L) {
                return
            }

            // 1. Verify if database opens cleanly with the current key via native SQLCipher open
            if (canOpenDatabase(dbFile, dbKey)) {
                return
            }

            // 2. If it cannot be opened with the key, check if it's an unencrypted plaintext SQLite database.
            //    Plaintext migration is a one-time import, not recovery-by-deletion, and keeps a
            //    byte-for-byte copy of the original file.
            if (isPlaintextDatabase(dbFile)) {
                if (migratePlaintextDatabase(dbFile, dbKey) && canOpenDatabase(dbFile, dbKey)) {
                    return
                }
            }

            // 3. Never delete or replace an unreadable database. Preserve a copy (including WAL/SHM)
            //    for manual recovery and fail closed. The user's data stays on the device.
            val preserved = preserveDatabaseFamily(dbFile, "${dbFile.name}_unreadable_bak.db")
            throw DatabaseRecoveryRequiredException(
                "The encrypted dialer database could not be opened. " +
                    "Original files were left intact" +
                    (preserved?.let { "; a protected copy is at ${it.name}" } ?: "") +
                    "."
            )
        }

        private fun canOpenDatabase(dbFile: File, dbKey: ByteArray): Boolean {
            if (!dbFile.exists() || dbFile.length() == 0L) return true
            var testDb: SQLiteDatabase? = null
            var cursor: android.database.Cursor? = null
            return try {
                testDb = SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    dbKey,
                    null,
                    SQLiteDatabase.OPEN_READWRITE,
                    null as net.zetetic.database.DatabaseErrorHandler?,
                    null as net.zetetic.database.sqlcipher.SQLiteDatabaseHook?
                )
                cursor = testDb?.rawQuery("SELECT count(*) FROM sqlite_master;", null)
                cursor != null && cursor.moveToFirst()
            } catch (_: Throwable) {
                false
            } finally {
                try { cursor?.close() } catch (_: Throwable) {}
                try { testDb?.close() } catch (_: Throwable) {}
            }
        }

        private fun isPlaintextDatabase(dbFile: File): Boolean {
            if (!dbFile.exists() || dbFile.length() < 16L) return false
            return try {
                dbFile.inputStream().use { stream ->
                    val header = ByteArray(16)
                    val read = stream.read(header)
                    read == 16 && String(header, Charsets.US_ASCII).startsWith("SQLite format 3")
                }
            } catch (_: Exception) {
                false
            }
        }

        private fun migratePlaintextDatabase(dbFile: File, dbKey: ByteArray): Boolean {
            val tempEncryptedFile = File(dbFile.parentFile, "${dbFile.name}_encrypted_temp.db")
            val originalBackup = File(dbFile.parentFile, "${dbFile.name}_plaintext_bak.db")
            val swapBackup = File(dbFile.parentFile, "${dbFile.name}_swap_original.db")
            if (tempEncryptedFile.exists()) tempEncryptedFile.delete()
            if (swapBackup.exists()) swapBackup.delete()

            var plaintextDb: SQLiteDatabase? = null
            return try {
                plaintextDb = SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READWRITE
                )
                // Flush any WAL contents before exporting, otherwise rows still in the
                // -wal file can be missing from the encrypted copy.
                try {
                    plaintextDb.rawQuery("PRAGMA wal_checkpoint(FULL);", null)?.use { it.moveToFirst() }
                } catch (_: Exception) {}

                val keyHex = dbKey.joinToString("") { "%02x".format(it) }
                plaintextDb.execSQL("ATTACH DATABASE '${tempEncryptedFile.absolutePath}' AS encrypted KEY \"x'$keyHex'\";")
                plaintextDb.rawQuery("SELECT sqlcipher_export('encrypted');", null)?.use { it.moveToFirst() }
                plaintextDb.execSQL("DETACH DATABASE encrypted;")
                plaintextDb.close()
                plaintextDb = null

                // Do not touch the original until the encrypted export is proven readable.
                if (!canOpenDatabase(tempEncryptedFile, dbKey)) {
                    tempEncryptedFile.delete()
                    return false
                }

                // Keep a permanent plaintext copy for manual recovery, then swap using a
                // same-directory rename so an interrupted migration can be rolled back.
                if (!originalBackup.exists()) dbFile.copyTo(originalBackup, overwrite = false)
                if (!dbFile.renameTo(swapBackup)) {
                    tempEncryptedFile.delete()
                    return false
                }
                if (!tempEncryptedFile.renameTo(dbFile)) {
                    swapBackup.renameTo(dbFile)
                    tempEncryptedFile.delete()
                    return false
                }
                deleteDatabaseFiles(swapBackup)
                deleteDatabaseSidecars(dbFile)
                true
            } catch (_: Exception) {
                try { plaintextDb?.close() } catch (_: Exception) {}
                if (tempEncryptedFile.exists()) tempEncryptedFile.delete()
                if (!dbFile.exists() && swapBackup.exists()) swapBackup.renameTo(dbFile)
                false
            }
        }

        /**
         * If the process died between the two renames of the plaintext migration, restore
         * the original before opening anything. This prevents a transient missing DB from
         * causing DatabaseKeyManager to mint a fresh key against an orphaned file.
         */
        private fun recoverInterruptedPlaintextSwap(appContext: Context) {
            val dbFile = appContext.getDatabasePath(DATABASE_NAME)
            val swapBackup = File(dbFile.parentFile, "${dbFile.name}_swap_original.db")
            if (!dbFile.exists() && swapBackup.exists()) {
                swapBackup.renameTo(dbFile)
            }
        }

        private fun preserveDatabaseFamily(dbFile: File, backupName: String): File? {
            return try {
                val backupFile = File(dbFile.parentFile, backupName)
                dbFile.copyTo(backupFile, overwrite = true)
                for (suffix in listOf("-wal", "-shm", "-journal")) {
                    val source = File("${dbFile.absolutePath}$suffix")
                    if (source.exists()) source.copyTo(File("${backupFile.absolutePath}$suffix"), overwrite = true)
                }
                backupFile
            } catch (_: Exception) {
                null
            }
        }

        private fun deleteDatabaseSidecars(dbFile: File) {
            for (suffix in listOf("-wal", "-shm", "-journal")) {
                try { File("${dbFile.absolutePath}$suffix").delete() } catch (_: Throwable) {}
            }
        }

        private fun deleteDatabaseFiles(dbFile: File) {
            try {
                android.database.sqlite.SQLiteDatabase.deleteDatabase(dbFile)
            } catch (_: Throwable) {}
            try {
                dbFile.delete()
                deleteDatabaseSidecars(dbFile)
            } catch (_: Throwable) {}
        }
    }
}

/** Raised instead of deleting an unreadable encrypted database. */
class DatabaseRecoveryRequiredException(message: String) : IllegalStateException(message)
