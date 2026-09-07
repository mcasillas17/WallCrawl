package wallcrawl.elopenmike.com.core.database

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Schema 11 to 12 adds recommendation provenance and touches nothing else.
 *
 * The point of the assertions below is that an upgrade cannot invent history: the new table
 * starts empty, so a session recorded before whole-program validation existed keeps an
 * honestly absent record rather than a fabricated validation outcome.
 */
@RunWith(AndroidJUnit4::class)
class Migration11To12Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: WallCrawlDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrate_addsAnEmptyRecommendationTableWithoutChangingAnythingElse() {
        createVersion11Database()

        database = Room.databaseBuilder(
            context,
            WallCrawlDatabase::class.java,
            DATABASE_NAME
        )
            .addMigrations(*WallCrawlDatabase.ALL_MIGRATIONS)
            .build()
        val sqlite = checkNotNull(database).openHelper.writableDatabase

        assertThat(sqlite.version).isEqualTo(12)

        // Existing rows are untouched, including the guidance columns schema 11 added.
        sqlite.query("SELECT name,revision FROM user_profiles").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            cursor.assertString("name", "Migration Crawler")
            cursor.assertLong("revision", 17L)
        }
        sqlite.query(
            "SELECT exerciseId,targetSets,restSeconds,effortMinRir,restClass " +
                "FROM workout_exercises"
        ).use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            cursor.assertString("exerciseId", "goblet-squat")
            cursor.assertInt("targetSets", 1)
            cursor.assertInt("restSeconds", 90)
            cursor.assertNull("effortMinRir")
            cursor.assertNull("restClass")
        }
        sqlite.query("SELECT completedReps,rpe,rir FROM workout_sets").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            cursor.assertInt("completedReps", 9)
            cursor.assertInt("rir", 2)
        }
        sqlite.query("SELECT ledgerPayload FROM weekly_dose_ledger_state").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            cursor.assertString("ledgerPayload", "wallcrawl-weekly-dose-ledger-v1")
        }

        // The new table exists, is empty, and carries no default that could pass for a
        // recorded decision.
        sqlite.query("SELECT COUNT(*) FROM workout_recommendation_records").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(0)).isEqualTo(0)
        }
        val columns = linkedMapOf<String, Triple<String, Int, String?>>()
        sqlite.query("PRAGMA table_info(workout_recommendation_records)").use { cursor ->
            while (cursor.moveToNext()) {
                columns[cursor.getString(cursor.getColumnIndexOrThrow("name"))] = Triple(
                    cursor.getString(cursor.getColumnIndexOrThrow("type")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("notnull")),
                    cursor.getString(cursor.getColumnIndexOrThrow("dflt_value"))
                )
            }
        }
        assertThat(columns.keys).containsExactly(
            "sessionId",
            "validatorVersion",
            "durationEstimatorVersion",
            "outcome",
            "reviewedPathEnabled",
            "catalogVersion",
            "reviewPolicyVersion",
            "trainingPolicyVersion",
            "ledgerPolicyVersion",
            "programStatePolicyVersion",
            "adaptationState",
            "weekStartEpochDay",
            "timeZoneId",
            "profileRevision",
            "contextIdentity",
            "reasonCodes",
            "doseAccounting",
            "recordedAtTimestamp"
        )
        listOf("catalogVersion", "trainingPolicyVersion", "adaptationState", "timeZoneId")
            .forEach { nullable -> assertThat(columns.getValue(nullable).second).isEqualTo(0) }
        assertThat(columns.getValue("sessionId").second).isEqualTo(1)
        assertThat(columns.values.map { it.third }.filterNotNull()).isEmpty()

        sqlite.query("PRAGMA foreign_key_check").use { cursor ->
            assertThat(cursor.count).isEqualTo(0)
        }
    }

    @Test
    fun deletingASession_takesItsRecommendationRecordWithIt() {
        createVersion11Database()

        database = Room.databaseBuilder(
            context,
            WallCrawlDatabase::class.java,
            DATABASE_NAME
        )
            .addMigrations(*WallCrawlDatabase.ALL_MIGRATIONS)
            .build()
        val sqlite = checkNotNull(database).openHelper.writableDatabase
        sqlite.execSQL("PRAGMA foreign_keys=ON")
        sqlite.execSQL(
            """
            INSERT INTO workout_recommendation_records (
                sessionId, validatorVersion, durationEstimatorVersion, outcome,
                reviewedPathEnabled, catalogVersion, reviewPolicyVersion,
                trainingPolicyVersion, ledgerPolicyVersion, programStatePolicyVersion,
                adaptationState, weekStartEpochDay, timeZoneId, profileRevision,
                contextIdentity, reasonCodes, doseAccounting, recordedAtTimestamp
            ) VALUES (
                'session-7', 'WHOLE_PROGRAM_V1', 'DURATION_ESTIMATOR_V1', 'VALID',
                0, 'catalog', 0, NULL, NULL, NULL, NULL, NULL, NULL, 17,
                'identity', '', 'wallcrawl-recommendation-dose-v1', 5
            )
            """.trimIndent()
        )

        sqlite.execSQL("DELETE FROM workout_sessions WHERE id = 'session-7'")

        sqlite.query("SELECT COUNT(*) FROM workout_recommendation_records").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(0)).isEqualTo(0)
        }
    }

    private fun createVersion11Database() {
        context.deleteDatabase(DATABASE_NAME)
        context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { db ->
            LegacyDatabaseFixtures.createSchema(db, version = 11)
            LegacyDatabaseFixtures.insertProfile(db, version = 11)
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
            db.version = 11
        }
    }

    private fun Cursor.assertString(column: String, expected: String) {
        assertThat(getString(getColumnIndexOrThrow(column))).isEqualTo(expected)
    }

    private fun Cursor.assertInt(column: String, expected: Int) {
        assertThat(getInt(getColumnIndexOrThrow(column))).isEqualTo(expected)
    }

    private fun Cursor.assertLong(column: String, expected: Long) {
        assertThat(getLong(getColumnIndexOrThrow(column))).isEqualTo(expected)
    }

    private fun Cursor.assertNull(column: String) {
        assertThat(isNull(getColumnIndexOrThrow(column))).isTrue()
    }

    private companion object {
        const val DATABASE_NAME = "migration-11-12.db"
    }
}
