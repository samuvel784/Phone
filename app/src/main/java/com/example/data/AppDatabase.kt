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
import android.util.Log
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.model.*
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
        const val DATABASE_VERSION = 9

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

            // Inspect the whole file family *before* asking DatabaseKeyManager for a key. A missing
            // active file must never mint a new key when an interrupted swap, a legacy plaintext
            // copy, a sidecar, or existing key material proves this is not a fresh install.
            val databaseFile = appContext.getDatabasePath(DATABASE_NAME)
            val family = DatabaseFileFamily.from(databaseFile)
            val plan = DatabaseFileGuard.plan(
                family = family,
                hasPersistedKey = DatabaseKeyManager.hasPersistedKey(appContext)
            )
            when (plan) {
                DatabaseFilePlan.FreshInstall -> Unit
                is DatabaseFilePlan.RestoreSwap -> {
                    DatabaseFileGuard.restoreInterruptedSwap(family, plan)
                    android.util.Log.w(
                        "NovaDialerDatabase",
                        "Restored an interrupted database swap before opening the database."
                    )
                }
                is DatabaseFilePlan.FailClosed -> {
                    throw DatabaseRecoveryRequiredException(plan.reason)
                }
                is DatabaseFilePlan.Active -> {
                    if (plan.preservedArtifacts.isNotEmpty()) {
                        android.util.Log.w(
                            "NovaDialerDatabase",
                            "Preserving recovery artifacts: " +
                                plan.preservedArtifacts.joinToString { it.name }
                        )
                    }
                }
            }

            val dbKey = DatabaseKeyManager.getDatabaseKey(appContext)
            // A genuinely fresh install has no active file yet; Room will create and encrypt it.
            // Every other path has an existing file that must pass validation before Room opens it.
            if (family.active.exists()) {
                ensureDatabaseIntegrity(appContext, dbKey)
            }
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
        internal fun ensureSchemaV9(db: SupportSQLiteDatabase) {
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
            // Defensive, idempotent guard: a legacy v8 database may predate the completion
            // flag. Adding it with the pending default preserves every reminder row, and
            // ensureColumn is a no-op when the column already exists.
            ensureColumn(db, "call_reminders", "isCompleted", "INTEGER NOT NULL DEFAULT 0")

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
                throw DatabaseRecoveryRequiredException(
                    "The dialer database is missing or empty after recovery checks. " +
                        "No replacement database was created."
                )
            }

            // 1. An encrypted database must open, pass SQLite integrity checking, expose the
            //    core Room tables, and not claim a schema version newer than this app supports.
            val reader = SqlCipherDatabaseInspector
            val snapshot = reader.readEncrypted(dbFile, dbKey)
            if (snapshot != null && isAcceptableSnapshot(snapshot)) {
                return
            }

            // 2. A plaintext SQLite database is a legacy install. Migrate it through a
            //    crash-safe same-directory swap, then validate the installed copy again.
            if (isPlaintextDatabase(dbFile)) {
                val coordinator = PlaintextMigrationCoordinator(
                    family = DatabaseFileFamily.from(dbFile),
                    key = dbKey,
                    reader = reader,
                    exporter = SqlCipherDatabaseInspector
                )
                when (val result = coordinator.migrate()) {
                    PlaintextMigrationCoordinator.Result.Migrated -> {
                        val installed = reader.readEncrypted(dbFile, dbKey)
                        if (installed != null && isAcceptableSnapshot(installed)) return
                        throw DatabaseRecoveryRequiredException(
                            "The plaintext database was migrated, but the installed encrypted " +
                                "database did not pass validation. The original swap was preserved."
                        )
                    }
                    is PlaintextMigrationCoordinator.Result.Failed -> {
                        throw DatabaseRecoveryRequiredException(result.reason)
                    }
                }
            }

            // 3. Never delete or replace an unreadable database. Preserve a copy (including
            //    WAL/SHM/journal) for manual recovery and fail closed.
            val preserved = preserveDatabaseFamily(dbFile, "${dbFile.name}_unreadable_bak.db")
            throw DatabaseRecoveryRequiredException(
                "The encrypted dialer database could not be opened or failed integrity/schema " +
                    "validation. Original files were left intact" +
                    (preserved?.let { "; a protected copy is at ${it.name}" } ?: "") +
                    "."
            )
        }

        private fun isAcceptableSnapshot(snapshot: DatabaseSnapshot): Boolean =
            snapshot.integrityOk &&
                snapshot.hasCoreTables &&
                snapshot.userVersion <= DATABASE_VERSION

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

        private fun preserveDatabaseFamily(dbFile: File, backupName: String): File? {
            return try {
                val parent = dbFile.parentFile ?: return null
                var backupFile = File(parent, backupName)
                var suffix = 1
                while (
                    backupFile.exists() ||
                    listOf("-wal", "-shm", "-journal").any {
                        File("${backupFile.absolutePath}$it").exists()
                    }
                ) {
                    backupFile = File(parent, "${backupName.removeSuffix(".db")}_$suffix.db")
                    suffix++
                }
                dbFile.copyTo(backupFile, overwrite = false)
                for (sidecar in listOf("-wal", "-shm", "-journal")) {
                    val source = File("${dbFile.absolutePath}$sidecar")
                    if (source.exists()) {
                        source.copyTo(File("${backupFile.absolutePath}$sidecar"), overwrite = false)
                    }
                }
                backupFile
            } catch (_: Exception) {
                null
            }
        }
    }
}

/** Raised instead of deleting an unreadable encrypted database. */
class DatabaseRecoveryRequiredException(message: String) : IllegalStateException(message)

/**
 * SQLCipher-backed inspector/exporter. Kept separate from the file-state coordinator so the
 * rename/rollback behavior can be tested on the JVM without pretending Robolectric exercises
 * native SQLCipher.
 */
internal object SqlCipherDatabaseInspector : DatabaseSnapshotReader, DatabaseEncryptionExporter {

    override fun readPlaintext(file: File): DatabaseSnapshot? = readSnapshot(file, null)

    override fun readEncrypted(file: File, key: ByteArray): DatabaseSnapshot? =
        readSnapshot(file, key)

    private fun readSnapshot(file: File, key: ByteArray?): DatabaseSnapshot? {
        if (!file.exists() || file.length() == 0L) return null
        var database: SQLiteDatabase? = null
        return try {
            database = if (key == null) {
                SQLiteDatabase.openDatabase(
                    file.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READWRITE
                )
            } else {
                SQLiteDatabase.openDatabase(
                    file.absolutePath,
                    key,
                    null,
                    SQLiteDatabase.OPEN_READWRITE,
                    null as net.zetetic.database.DatabaseErrorHandler?,
                    null as net.zetetic.database.sqlcipher.SQLiteDatabaseHook?
                )
            }

            val integrityOk = readIntegrity(database)
            val userVersion = readUserVersion(database)
            val tableCounts = readTableCounts(database)
            DatabaseSnapshot(
                integrityOk = integrityOk,
                userVersion = userVersion,
                tableCounts = tableCounts
            )
        } catch (_: Throwable) {
            null
        } finally {
            try { database?.close() } catch (_: Throwable) {}
        }
    }

    private fun readIntegrity(database: SQLiteDatabase): Boolean {
        var sawRow = false
        database.rawQuery("PRAGMA integrity_check;", null).use { cursor ->
            while (cursor.moveToNext()) {
                sawRow = true
                if (cursor.getString(0)?.trim() != "ok") return false
            }
        }
        return sawRow
    }

    private fun readUserVersion(database: SQLiteDatabase): Int {
        database.rawQuery("PRAGMA user_version;", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun readTableCounts(database: SQLiteDatabase): Map<String, Long> {
        val names = mutableListOf<String>()
        database.rawQuery(
            "SELECT name FROM sqlite_master " +
                "WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%';",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.let { names.add(it) }
            }
        }

        val counts = linkedMapOf<String, Long>()
        for (name in names) {
            val quoted = name.replace("`", "``")
            database.rawQuery("SELECT COUNT(*) FROM `$quoted`;", null).use { cursor ->
                if (!cursor.moveToFirst()) {
                    throw IllegalStateException("Could not count table $name")
                }
                counts[name] = cursor.getLong(0)
            }
        }
        return counts
    }

    override fun export(plaintext: File, encrypted: File, key: ByteArray): Boolean {
        if (!plaintext.exists() || plaintext.length() == 0L) return false
        if (encrypted.exists()) return false
        encrypted.parentFile?.mkdirs()
        if (!encrypted.createNewFile() && !encrypted.exists()) return false

        var source: SQLiteDatabase? = null
        var target: SQLiteDatabase? = null
        return try {
            // SQLCipher's `ATTACH ... KEY "x'...'"` creates a raw-key database, while
            // SupportOpenHelperFactory passes the key bytes through sqlite3_key() as a
            // passphrase. Those formats are not interchangeable. Open the encrypted target with
            // the same key API used by Room, attach the plaintext source without a key, and copy
            // the schema and rows explicitly. The caller still validates the result before any
            // file is moved into place.
            val sourceDatabase = SQLiteDatabase.openDatabase(
                plaintext.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            )
            source = sourceDatabase
            sourceDatabase.rawQuery("PRAGMA wal_checkpoint(FULL);", null).use { it.moveToFirst() }
            val sourceVersion = sourceDatabase.version
            sourceDatabase.close()
            source = null

            val targetDatabase = SQLiteDatabase.openDatabase(
                encrypted.absolutePath,
                key,
                null,
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY,
                null as net.zetetic.database.DatabaseErrorHandler?,
                null as net.zetetic.database.sqlcipher.SQLiteDatabaseHook?
            )
            target = targetDatabase
            val escapedSource = plaintext.absolutePath.replace("'", "''")
            targetDatabase.execSQL(
                "ATTACH DATABASE '$escapedSource' AS plaintext_source KEY '';"
            )

            val tables = mutableListOf<String>()
            val postDataSchema = mutableListOf<String>()
            targetDatabase.rawQuery(
                "SELECT type, name, sql FROM plaintext_source.sqlite_master " +
                    "WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%';",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    when (cursor.getString(0)) {
                        "table" -> {
                            tables.add(cursor.getString(1))
                            targetDatabase.execSQL(cursor.getString(2))
                        }
                        "index", "view", "trigger" -> postDataSchema.add(cursor.getString(2))
                    }
                }
            }

            targetDatabase.beginTransaction()
            try {
                for (table in tables) {
                    val quoted = quoteIdentifier(table)
                    targetDatabase.execSQL(
                        "INSERT INTO main.$quoted SELECT * FROM plaintext_source.$quoted;"
                    )
                }
                targetDatabase.execSQL("PRAGMA user_version = $sourceVersion;")
                targetDatabase.setTransactionSuccessful()
            } finally {
                targetDatabase.endTransaction()
            }

            for (sql in postDataSchema) {
                targetDatabase.execSQL(sql)
            }
            targetDatabase.execSQL("DETACH DATABASE plaintext_source;")
            true
        } catch (t: Throwable) {
            Log.e("NovaDialerDatabase", "SQLCipher export failed", t)
            try { target?.close() } catch (_: Throwable) {}
            target = null
            try { encrypted.delete() } catch (_: Throwable) {}
            false
        } finally {
            try { source?.close() } catch (_: Throwable) {}
            try { target?.close() } catch (_: Throwable) {}
        }
    }

    private fun quoteIdentifier(identifier: String): String =
        "\"" + identifier.replace("\"", "\"\"") + "\""
}
