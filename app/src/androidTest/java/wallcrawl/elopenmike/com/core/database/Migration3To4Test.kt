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
class Migration3To4Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: WallCrawlDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrate_preservesExistingWorkoutDataAndAddsTemplateSchema() {
        context.deleteDatabase(DATABASE_NAME)
        context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { db ->
            createVersion3Schema(db)
            insertVersion3Data(db)
            db.version = 3
        }

        database = Room.databaseBuilder(context, WallCrawlDatabase::class.java, DATABASE_NAME)
            .addMigrations(
                *WallCrawlDatabase.ALL_MIGRATIONS
            )
            .build()
        val sqlite = checkNotNull(database).openHelper.writableDatabase

        sqlite.query("SELECT name, revision FROM user_profiles").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("Crawler")
            assertThat(cursor.getLong(1)).isEqualTo(2L)
        }
        sqlite.query("SELECT name, origin, sourceTemplateId FROM workout_sessions").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("Legacy Push")
            assertThat(cursor.getString(1)).isEqualTo("PLANNER")
            assertThat(cursor.isNull(2)).isTrue()
        }
        sqlite.query(
            "SELECT exerciseType, targetRepMin, targetRepMax, restSeconds " +
                "FROM workout_exercises"
        ).use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("WEIGHT_REPS")
            assertThat(cursor.getInt(1)).isEqualTo(8)
            assertThat(cursor.getInt(2)).isEqualTo(10)
            assertThat(cursor.getInt(3)).isEqualTo(90)
        }
        sqlite.query(
            "SELECT exerciseType, targetReps, completedReps FROM workout_sets"
        ).use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("WEIGHT_REPS")
            assertThat(cursor.getInt(1)).isEqualTo(10)
            assertThat(cursor.getInt(2)).isEqualTo(9)
        }
        sqlite.query("SELECT COUNT(*) FROM workout_templates").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(0)).isEqualTo(0)
        }
        sqlite.query("PRAGMA foreign_key_check").use { cursor ->
            assertThat(cursor.count).isEqualTo(0)
        }
    }

    private fun createVersion3Schema(db: android.database.sqlite.SQLiteDatabase) {
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
                "weightUnit TEXT NOT NULL, status TEXT NOT NULL, focusMusclesJson TEXT NOT NULL, " +
                "notes TEXT NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE workout_exercises (id TEXT NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL, " +
                "exerciseId TEXT NOT NULL, orderIndex INTEGER NOT NULL, targetSets INTEGER NOT NULL, " +
                "targetRepMin INTEGER NOT NULL, targetRepMax INTEGER NOT NULL, targetWeight REAL, " +
                "notes TEXT NOT NULL, FOREIGN KEY(sessionId) REFERENCES workout_sessions(id) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE TABLE workout_sets (id TEXT NOT NULL PRIMARY KEY, workoutExerciseId TEXT NOT NULL, " +
                "setNumber INTEGER NOT NULL, targetReps INTEGER NOT NULL, completedReps INTEGER, " +
                "targetWeight REAL, completedWeight REAL, isCompleted INTEGER NOT NULL, rpe REAL, " +
                "rir INTEGER, type TEXT NOT NULL, FOREIGN KEY(workoutExerciseId) " +
                "REFERENCES workout_exercises(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL(
            "CREATE INDEX index_workout_sessions_status_completedAtTimestamp " +
                "ON workout_sessions(status, completedAtTimestamp)"
        )
        db.execSQL("CREATE INDEX index_workout_exercises_sessionId ON workout_exercises(sessionId)")
        db.execSQL("CREATE INDEX index_workout_sets_workoutExerciseId ON workout_sets(workoutExerciseId)")
    }

    private fun insertVersion3Data(db: android.database.sqlite.SQLiteDatabase) {
        db.execSQL(
            "INSERT INTO user_profiles VALUES " +
                "('default_user', 2, 'Crawler', 'BUILD_MUSCLE', 'INTERMEDIATE', 50, 4, " +
                "'Dumbbell', 'LBS', '', '')"
        )
        db.execSQL(
            "INSERT INTO workout_sessions VALUES " +
                "('session', 'Legacy Push', 1000, 2000, 45, 42, 'LBS', 'COMPLETED', 'Chest', '')"
        )
        db.execSQL(
            "INSERT INTO workout_exercises VALUES " +
                "('workout-exercise', 'session', 'incline-dumbbell-press', 0, 1, 8, 10, 45.0, '')"
        )
        db.execSQL(
            "INSERT INTO workout_sets VALUES " +
                "('set', 'workout-exercise', 1, 10, 9, 45.0, 45.0, 1, NULL, NULL, 'NORMAL')"
        )
    }

    private companion object {
        const val DATABASE_NAME = "migration-3-4.db"
    }
}
