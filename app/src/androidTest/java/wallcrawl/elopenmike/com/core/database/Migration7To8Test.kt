package wallcrawl.elopenmike.com.core.database

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType

@RunWith(AndroidJUnit4::class)
class Migration7To8Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: WallCrawlDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrate_preservesProfileAndHistoryWhileAddingUnknownCapabilities() = runBlocking {
        createVersion7Database()

        database = openDatabase()
        val sqlite = checkNotNull(database).openHelper.writableDatabase

        assertRawMigratedProfile(sqlite)

        assertPreservedRows(sqlite)
        sqlite.query("PRAGMA foreign_key_check").use { cursor ->
            assertThat(cursor.count).isEqualTo(0)
        }

        val profile = OfflineUserProfileRepository(checkNotNull(database).userProfileDao())
            .getProfileOnce()
        assertThat(profile.onboardingCompleted).isTrue()
        MovementCapabilityType.entries.forEach { type ->
            assertThat(profile.movementCapabilities[type]).isEqualTo(CapabilityLevel.UNKNOWN)
        }
    }

    @Test
    fun migratedDatabase_reloadsCompleteProfileAndCapabilitiesThroughNewInstances() = runBlocking {
        createVersion7Database()
        database = openDatabase()

        val original = OfflineUserProfileRepository(checkNotNull(database).userProfileDao())
            .getProfileOnce()
        val explicitCapabilities = MovementCapabilities.from(
            MovementCapabilityType.entries.associateWith { type ->
                when (type.ordinal % 3) {
                    0 -> CapabilityLevel.COMFORTABLE
                    1 -> CapabilityLevel.LIMITED
                    else -> CapabilityLevel.AVOID
                }
            }
        )
        OfflineUserProfileRepository(checkNotNull(database).userProfileDao()).saveProfile(
            original.copy(movementCapabilities = explicitCapabilities)
        )
        checkNotNull(database).close()
        database = null

        database = openDatabase()
        val profile = OfflineUserProfileRepository(checkNotNull(database).userProfileDao())
            .getProfileOnce()

        assertThat(profile).isEqualTo(
            original.copy(
                revision = 18L,
                movementCapabilities = explicitCapabilities
            )
        )
        val sqlite = checkNotNull(database).openHelper.writableDatabase
        assertPreservedRows(sqlite)
        sqlite.query("PRAGMA foreign_key_check").use { cursor ->
            assertThat(cursor.count).isEqualTo(0)
        }
    }

    private fun createVersion7Database() {
        context.deleteDatabase(DATABASE_NAME)
        context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { db ->
            LegacyDatabaseFixtures.createSchema(db, version = 7)
            LegacyDatabaseFixtures.insertProfile(db, version = 7)
            LegacyDatabaseFixtures.insertVersion7HistoryAndTemplate(db)
            db.version = 7
        }
    }

    private fun openDatabase(): WallCrawlDatabase =
        Room.databaseBuilder(context, WallCrawlDatabase::class.java, DATABASE_NAME)
            .addMigrations(WallCrawlDatabase.MIGRATION_7_8)
            .build()

    private fun assertRawMigratedProfile(sqlite: androidx.sqlite.db.SupportSQLiteDatabase) {
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
            cursor.assertString(
                "trainingConstraintsJson",
                "KNEE_SENSITIVE|||LOW_IMPACT_ONLY"
            )
            cursor.assertInt("returningAfterBreakWeeks", 12)
            cursor.assertString("confirmedStartingLoadsJson", "goblet-squat:24.0")
            cursor.assertString("fitnessGoalsJson", "STRENGTH|||BUILD_MUSCLE")
            cursor.assertString("themePreference", "DARK")
            cursor.assertString("movementCapabilitiesJson", "{}")
            assertThat(cursor.moveToNext()).isFalse()
        }
    }

    private fun assertPreservedRows(sqlite: androidx.sqlite.db.SupportSQLiteDatabase) {
        sqlite.query("SELECT * FROM workout_sessions").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            cursor.assertString("id", "session-7")
            cursor.assertString("name", "Preserved Session")
            cursor.assertLong("startedAtTimestamp", 100L)
            cursor.assertLong("completedAtTimestamp", 200L)
            cursor.assertInt("targetDurationMinutes", 45)
            cursor.assertInt("actualDurationMinutes", 43)
            cursor.assertString("weightUnit", "KG")
            cursor.assertString("status", "COMPLETED")
            cursor.assertString("origin", "CUSTOM_TEMPLATE")
            cursor.assertString("sourceTemplateId", "template-7")
            cursor.assertString("focusMusclesJson", "Chest")
            cursor.assertString("notes", "session note")
            assertThat(cursor.moveToNext()).isFalse()
        }
        sqlite.query("SELECT * FROM workout_exercises").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            cursor.assertString("id", "workout-exercise-7")
            cursor.assertString("sessionId", "session-7")
            cursor.assertString("exerciseId", "goblet-squat")
            cursor.assertInt("orderIndex", 0)
            cursor.assertString("exerciseType", "WEIGHT_REPS")
            cursor.assertInt("targetSets", 1)
            cursor.assertInt("targetRepMin", 8)
            cursor.assertInt("targetRepMax", 10)
            cursor.assertDouble("targetWeight", 24.0)
            cursor.assertNull("targetAssistanceWeight")
            cursor.assertNull("targetDurationSeconds")
            cursor.assertNull("targetDistanceMeters")
            cursor.assertInt("restSeconds", 90)
            cursor.assertString("notes", "exercise note")
            assertThat(cursor.moveToNext()).isFalse()
        }
        sqlite.query("SELECT * FROM workout_sets").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            cursor.assertString("id", "set-7")
            cursor.assertString("workoutExerciseId", "workout-exercise-7")
            cursor.assertInt("setNumber", 1)
            cursor.assertString("exerciseType", "WEIGHT_REPS")
            cursor.assertInt("targetReps", 10)
            cursor.assertInt("completedReps", 9)
            cursor.assertDouble("targetWeight", 24.0)
            cursor.assertDouble("completedWeight", 24.0)
            cursor.assertNull("targetAssistanceWeight")
            cursor.assertNull("completedAssistanceWeight")
            cursor.assertNull("targetDurationSeconds")
            cursor.assertNull("completedDurationSeconds")
            cursor.assertNull("targetDistanceMeters")
            cursor.assertNull("completedDistanceMeters")
            cursor.assertInt("isCompleted", 1)
            cursor.assertDouble("rpe", 8.0)
            cursor.assertInt("rir", 2)
            cursor.assertString("type", "NORMAL")
            assertThat(cursor.moveToNext()).isFalse()
        }
        sqlite.query("SELECT * FROM workout_templates").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            cursor.assertString("id", "template-7")
            cursor.assertString("name", "Preserved Template")
            cursor.assertString("notes", "template note")
            cursor.assertLong("createdAtTimestamp", 50L)
            cursor.assertLong("updatedAtTimestamp", 60L)
            assertThat(cursor.moveToNext()).isFalse()
        }
        sqlite.query("SELECT * FROM workout_template_exercises").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            cursor.assertString("templateId", "template-7")
            cursor.assertInt("orderIndex", 0)
            cursor.assertString("exerciseId", "goblet-squat")
            cursor.assertString("exerciseType", "WEIGHT_REPS")
            cursor.assertInt("targetSets", 1)
            cursor.assertInt("targetRepMin", 8)
            cursor.assertInt("targetRepMax", 10)
            cursor.assertDouble("targetWeight", 24.0)
            cursor.assertNull("targetAssistanceWeight")
            cursor.assertNull("targetDurationSeconds")
            cursor.assertNull("targetDistanceMeters")
            cursor.assertInt("restSeconds", 90)
            cursor.assertString("notes", "template exercise note")
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
        const val DATABASE_NAME = "migration-7-8.db"
    }
}
