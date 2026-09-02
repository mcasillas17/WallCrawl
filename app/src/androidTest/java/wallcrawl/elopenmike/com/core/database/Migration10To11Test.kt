package wallcrawl.elopenmike.com.core.database

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration10To11Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: WallCrawlDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrate_addsNullableGuidanceWithoutChangingHistoryTemplatesOrLedgerCache() {
        createVersion10Database()

        database = Room.databaseBuilder(
            context,
            WallCrawlDatabase::class.java,
            DATABASE_NAME
        )
            .addMigrations(*WallCrawlDatabase.ALL_MIGRATIONS)
            .build()
        val sqlite = checkNotNull(database).openHelper.writableDatabase

        assertThat(sqlite.version).isEqualTo(11)
        assertNullableGuidanceColumns(sqlite, "workout_exercises")
        assertNullableGuidanceColumns(sqlite, "workout_template_exercises")
        sqlite.query(
            "SELECT restSeconds,effortMinRir,effortMaxRir,restClass,restTargetSource " +
                "FROM workout_exercises"
        ).use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            cursor.assertInt("restSeconds", 90)
            cursor.assertNull("effortMinRir")
            cursor.assertNull("effortMaxRir")
            cursor.assertNull("restClass")
            cursor.assertNull("restTargetSource")
        }
        sqlite.query(
            "SELECT restSeconds,effortMinRir,effortMaxRir,restClass,restTargetSource " +
                "FROM workout_template_exercises"
        ).use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            cursor.assertInt("restSeconds", 90)
            cursor.assertNull("effortMinRir")
            cursor.assertNull("effortMaxRir")
            cursor.assertNull("restClass")
            cursor.assertNull("restTargetSource")
        }
        sqlite.query(
            "SELECT ledgerPayload,sourceFingerprint FROM weekly_dose_ledger_state"
        ).use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            cursor.assertString("ledgerPayload", "wallcrawl-weekly-dose-ledger-v1")
            cursor.assertString("sourceFingerprint", "fingerprint")
        }
        sqlite.query("PRAGMA foreign_key_check").use { cursor ->
            assertThat(cursor.count).isEqualTo(0)
        }
    }

    private fun createVersion10Database() {
        context.deleteDatabase(DATABASE_NAME)
        context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { db ->
            LegacyDatabaseFixtures.createSchema(db, version = 10)
            LegacyDatabaseFixtures.insertProfile(db, version = 10)
            LegacyDatabaseFixtures.insertHistoryAndTemplate(db)
            db.execSQL(
                """
                INSERT INTO weekly_dose_ledger_state (
                    profileId, weekStartEpochDay, timeZoneId, policyVersion,
                    catalogVersion, reviewPolicyVersion, ledgerPayload,
                    sourceFingerprint, generatedAtTimestamp
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "default_user",
                    20_696L,
                    "UTC",
                    "PRIMARY_ONLY_V1",
                    "catalog",
                    1,
                    "wallcrawl-weekly-dose-ledger-v1",
                    "fingerprint",
                    1L
                )
            )
            db.version = 10
        }
    }

    private fun assertNullableGuidanceColumns(
        sqlite: SupportSQLiteDatabase,
        table: String
    ) {
        val columns = linkedMapOf<String, Pair<String, String?>>()
        sqlite.query("PRAGMA table_info($table)").use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                columns[name] =
                    cursor.getString(cursor.getColumnIndexOrThrow("type")) to
                    cursor.getString(cursor.getColumnIndexOrThrow("dflt_value"))
            }
        }
        listOf("effortMinRir", "effortMaxRir").forEach { name ->
            assertThat(columns[name]).isEqualTo("INTEGER" to null)
        }
        listOf("restClass", "restTargetSource").forEach { name ->
            assertThat(columns[name]).isEqualTo("TEXT" to null)
        }
    }

    private fun Cursor.assertString(column: String, expected: String) {
        assertThat(getString(getColumnIndexOrThrow(column))).isEqualTo(expected)
    }

    private fun Cursor.assertInt(column: String, expected: Int) {
        assertThat(getInt(getColumnIndexOrThrow(column))).isEqualTo(expected)
    }

    private fun Cursor.assertNull(column: String) {
        assertThat(isNull(getColumnIndexOrThrow(column))).isTrue()
    }

    private companion object {
        const val DATABASE_NAME = "migration-10-11.db"
    }
}
