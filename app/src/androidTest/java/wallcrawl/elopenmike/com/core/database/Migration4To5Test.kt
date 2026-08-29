package wallcrawl.elopenmike.com.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration4To5Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: WallCrawlDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrate_addsOnboardingColumnsAndKeepsExistingProfileUnonboarded() {
        context.deleteDatabase(DATABASE_NAME)
        context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { db ->
            createVersion4Schema(db)
            insertVersion4Data(db)
            db.version = 4
        }

        database = Room.databaseBuilder(context, WallCrawlDatabase::class.java, DATABASE_NAME)
            .addMigrations(
                WallCrawlDatabase.MIGRATION_4_5,
                WallCrawlDatabase.MIGRATION_5_6
            )
            .build()
        val sqlite = checkNotNull(database).openHelper.writableDatabase

        sqlite.query(
            "SELECT name, onboardingCompleted, trainingConstraintsJson, " +
                "returningAfterBreakWeeks, confirmedStartingLoadsJson FROM user_profiles"
        ).use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("Crawler")
            // A migrated user has never confirmed the new safety-relevant fields, so the
            // migration must not mark them onboarded even though the row already existed.
            assertThat(cursor.getInt(1)).isEqualTo(0)
            assertThat(cursor.getString(2)).isEqualTo("")
            assertThat(cursor.getInt(3)).isEqualTo(0)
            assertThat(cursor.getString(4)).isEqualTo("")
        }
        sqlite.query("PRAGMA foreign_key_check").use { cursor ->
            assertThat(cursor.count).isEqualTo(0)
        }
    }

    private fun createVersion4Schema(db: android.database.sqlite.SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE user_profiles (id TEXT NOT NULL PRIMARY KEY, revision INTEGER NOT NULL, " +
                "name TEXT NOT NULL, primaryGoal TEXT NOT NULL, experienceLevel TEXT NOT NULL, " +
                "preferredDurationMinutes INTEGER NOT NULL, daysPerWeek INTEGER NOT NULL, " +
                "availableEquipmentJson TEXT NOT NULL, preferredUnit TEXT NOT NULL, " +
                "musclePrioritiesJson TEXT NOT NULL, excludedExerciseIdsJson TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE workout_sessions (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                "startedAtTimestamp INTEGER NOT NULL, completedAtTimestamp INTEGER, " +
                "targetDurationMinutes INTEGER NOT NULL, actualDurationMinutes INTEGER NOT NULL, " +
                "weightUnit TEXT NOT NULL, status TEXT NOT NULL, " +
                "origin TEXT NOT NULL DEFAULT 'PLANNER', " +
                "sourceTemplateId TEXT, focusMusclesJson TEXT NOT NULL, notes TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE workout_exercises (id TEXT NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL, " +
                "exerciseId TEXT NOT NULL, orderIndex INTEGER NOT NULL, exerciseType TEXT NOT NULL, " +
                "targetSets INTEGER NOT NULL, targetRepMin INTEGER, targetRepMax INTEGER, " +
                "targetWeight REAL, targetAssistanceWeight REAL, targetDurationSeconds INTEGER, " +
                "targetDistanceMeters REAL, restSeconds INTEGER NOT NULL, notes TEXT NOT NULL, " +
                "FOREIGN KEY(sessionId) REFERENCES workout_sessions(id) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE TABLE workout_sets (id TEXT NOT NULL PRIMARY KEY, workoutExerciseId TEXT NOT NULL, " +
                "setNumber INTEGER NOT NULL, exerciseType TEXT NOT NULL, targetReps INTEGER, " +
                "completedReps INTEGER, targetWeight REAL, completedWeight REAL, " +
                "targetAssistanceWeight REAL, completedAssistanceWeight REAL, " +
                "targetDurationSeconds INTEGER, completedDurationSeconds INTEGER, " +
                "targetDistanceMeters REAL, completedDistanceMeters REAL, isCompleted INTEGER NOT NULL, " +
                "rpe REAL, rir INTEGER, type TEXT NOT NULL, FOREIGN KEY(workoutExerciseId) " +
                "REFERENCES workout_exercises(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE TABLE workout_templates (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                "notes TEXT NOT NULL, createdAtTimestamp INTEGER NOT NULL, " +
                "updatedAtTimestamp INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE workout_template_exercises (templateId TEXT NOT NULL, " +
                "orderIndex INTEGER NOT NULL, exerciseId TEXT NOT NULL, exerciseType TEXT NOT NULL, " +
                "targetSets INTEGER NOT NULL, targetRepMin INTEGER, targetRepMax INTEGER, " +
                "targetWeight REAL, targetAssistanceWeight REAL, targetDurationSeconds INTEGER, " +
                "targetDistanceMeters REAL, restSeconds INTEGER NOT NULL, notes TEXT NOT NULL, " +
                "PRIMARY KEY(templateId, orderIndex), FOREIGN KEY(templateId) " +
                "REFERENCES workout_templates(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE INDEX index_workout_sessions_status_completedAtTimestamp " +
                "ON workout_sessions(status, completedAtTimestamp)"
        )
        db.execSQL("CREATE INDEX index_workout_exercises_sessionId ON workout_exercises(sessionId)")
        db.execSQL("CREATE INDEX index_workout_sets_workoutExerciseId ON workout_sets(workoutExerciseId)")
        db.execSQL(
            "CREATE INDEX index_workout_template_exercises_templateId " +
                "ON workout_template_exercises(templateId)"
        )
    }

    private fun insertVersion4Data(db: android.database.sqlite.SQLiteDatabase) {
        db.execSQL(
            "INSERT INTO user_profiles VALUES " +
                "('default_user', 5, 'Crawler', 'BUILD_MUSCLE', 'INTERMEDIATE', 50, 4, " +
                "'Barbell|||Dumbbell|||Bodyweight', 'LBS', '', '')"
        )
    }

    private companion object {
        const val DATABASE_NAME = "migration-4-5.db"
    }
}
