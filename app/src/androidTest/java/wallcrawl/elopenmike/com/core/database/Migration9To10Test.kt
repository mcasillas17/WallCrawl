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

/**
 * Migration 9 -> 10 adds the derived weekly dose ledger cache.
 *
 * The migration is purely additive: it creates one new table and touches nothing that
 * already exists, because completed history stays the authority a ledger is rebuilt from.
 */
@RunWith(AndroidJUnit4::class)
class Migration9To10Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: WallCrawlDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrate_addsAnEmptyLedgerCacheWithoutTouchingExistingData() {
        createVersion9Database()

        database = openDatabase()
        val sqlite = checkNotNull(database).openHelper.writableDatabase

        assertThat(sqlite.version).isEqualTo(11)
        assertPreservedProfile(sqlite)
        assertPreservedHistoryAndTemplate(sqlite)

        sqlite.query("SELECT COUNT(*) FROM weekly_dose_ledger_state").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(0)).isEqualTo(0)
        }
        sqlite.query("PRAGMA foreign_key_check").use { cursor ->
            assertThat(cursor.count).isEqualTo(0)
        }
    }

    @Test
    fun migrate_createsTheLedgerCacheKeyedByProfileWeekZoneAndPolicy() {
        createVersion9Database()
        database = openDatabase()
        val sqlite = checkNotNull(database).openHelper.writableDatabase

        val columns = mutableMapOf<String, Pair<String, Int>>()
        val primaryKeyColumns = sortedMapOf<Int, String>()
        sqlite.query("PRAGMA table_info(weekly_dose_ledger_state)").use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                columns[name] = cursor.getString(cursor.getColumnIndexOrThrow("type")) to
                    cursor.getInt(cursor.getColumnIndexOrThrow("notnull"))
                val keyPosition = cursor.getInt(cursor.getColumnIndexOrThrow("pk"))
                if (keyPosition > 0) primaryKeyColumns[keyPosition] = name
            }
        }

        assertThat(primaryKeyColumns.values)
            .containsExactly("profileId", "weekStartEpochDay", "timeZoneId", "policyVersion")
            .inOrder()
        assertThat(columns.keys).containsExactly(
            "profileId",
            "weekStartEpochDay",
            "timeZoneId",
            "policyVersion",
            "catalogVersion",
            "reviewPolicyVersion",
            "ledgerPayload",
            "sourceFingerprint",
            "generatedAtTimestamp"
        )
        columns.forEach { (name, typeAndNullability) ->
            assertThat(typeAndNullability.second).isEqualTo(1)
            assertThat(typeAndNullability.first).isAnyOf("TEXT", "INTEGER")
            assertThat(name).isNotEmpty()
        }
    }

    private fun createVersion9Database() {
        context.deleteDatabase(DATABASE_NAME)
        context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { db ->
            LegacyDatabaseFixtures.createSchema(db, version = 9)
            LegacyDatabaseFixtures.insertProfile(db, version = 9)
            LegacyDatabaseFixtures.insertHistoryAndTemplate(db)
            db.version = 9
        }
    }

    private fun openDatabase(): WallCrawlDatabase =
        Room.databaseBuilder(context, WallCrawlDatabase::class.java, DATABASE_NAME)
            .addMigrations(*WallCrawlDatabase.ALL_MIGRATIONS)
            .build()

    private fun assertPreservedProfile(sqlite: SupportSQLiteDatabase) {
        sqlite.query("SELECT * FROM user_profiles").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            cursor.assertString("id", "default_user")
            cursor.assertLong("revision", 17L)
            cursor.assertString("name", "Migration Crawler")
            cursor.assertString("primaryGoal", "STRENGTH")
            cursor.assertString("experienceLevel", "ADVANCED")
            cursor.assertInt("preferredDurationMinutes", 75)
            cursor.assertInt("daysPerWeek", 5)
            cursor.assertString("availableEquipmentJson", "Bodyweight|||Dumbbell")
            cursor.assertString("preferredUnit", "KG")
            cursor.assertString("musclePrioritiesJson", "Chest:HIGH|||Back:LOW")
            cursor.assertString("excludedExerciseIdsJson", "burpee")
            cursor.assertInt("onboardingCompleted", 1)
            cursor.assertString("trainingConstraintsJson", "KNEE_SENSITIVE|||LOW_IMPACT_ONLY")
            cursor.assertInt("returningAfterBreakWeeks", 12)
            cursor.assertString("confirmedStartingLoadsJson", "goblet-squat:24.0")
            cursor.assertString("fitnessGoalsJson", "STRENGTH|||BUILD_MUSCLE")
            cursor.assertString("themePreference", "DARK")
            cursor.assertString("movementCapabilitiesJson", "{}")
            assertThat(cursor.moveToNext()).isFalse()
        }
    }

    private fun assertPreservedHistoryAndTemplate(sqlite: SupportSQLiteDatabase) {
        sqlite.query("SELECT * FROM workout_sessions").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            cursor.assertString("id", "session-7")
            cursor.assertString("name", "Preserved Session")
            cursor.assertLong("startedAtTimestamp", 100L)
            cursor.assertLong("completedAtTimestamp", 200L)
            cursor.assertInt("actualDurationMinutes", 43)
            cursor.assertString("status", "COMPLETED")
            cursor.assertString("sourceTemplateId", "template-7")
            assertThat(cursor.moveToNext()).isFalse()
        }
        sqlite.query("SELECT * FROM workout_exercises").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            cursor.assertString("id", "workout-exercise-7")
            cursor.assertString("sessionId", "session-7")
            cursor.assertString("exerciseId", "goblet-squat")
            cursor.assertInt("targetSets", 1)
            assertThat(cursor.moveToNext()).isFalse()
        }
        sqlite.query("SELECT * FROM workout_sets").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            cursor.assertString("id", "set-7")
            cursor.assertString("workoutExerciseId", "workout-exercise-7")
            cursor.assertInt("completedReps", 9)
            cursor.assertInt("isCompleted", 1)
            cursor.assertDouble("rpe", 8.0)
            cursor.assertInt("rir", 2)
            cursor.assertString("type", "NORMAL")
            // Schema 9's typed outcome columns keep the honest nulls they migrated with.
            cursor.assertNull("feltManageable")
            cursor.assertNull("completedAtTimestamp")
            cursor.assertNull("stoppedAtTimestamp")
            cursor.assertNull("stopReason")
            assertThat(cursor.moveToNext()).isFalse()
        }
        sqlite.query("SELECT * FROM workout_templates").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            cursor.assertString("id", "template-7")
            cursor.assertString("name", "Preserved Template")
            assertThat(cursor.moveToNext()).isFalse()
        }
        sqlite.query("SELECT * FROM workout_template_exercises").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            cursor.assertString("templateId", "template-7")
            cursor.assertString("exerciseId", "goblet-squat")
            assertThat(cursor.moveToNext()).isFalse()
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

    private fun Cursor.assertDouble(column: String, expected: Double) {
        assertThat(getDouble(getColumnIndexOrThrow(column))).isEqualTo(expected)
    }

    private fun Cursor.assertNull(column: String) {
        assertThat(isNull(getColumnIndexOrThrow(column))).isTrue()
    }

    private companion object {
        const val DATABASE_NAME = "migration-9-10.db"
    }
}
