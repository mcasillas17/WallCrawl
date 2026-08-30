package wallcrawl.elopenmike.com.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import wallcrawl.elopenmike.com.core.database.converter.RoomTypeConverters
import wallcrawl.elopenmike.com.core.database.dao.UserProfileDao
import wallcrawl.elopenmike.com.core.database.dao.WorkoutExerciseDao
import wallcrawl.elopenmike.com.core.database.dao.WorkoutSessionDao
import wallcrawl.elopenmike.com.core.database.dao.WorkoutSetDao
import wallcrawl.elopenmike.com.core.database.dao.WorkoutTemplateDao
import wallcrawl.elopenmike.com.core.database.entity.UserProfileEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutExerciseEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSessionEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSetEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutTemplateEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutTemplateExerciseEntity

@Database(
    entities = [
        UserProfileEntity::class,
        WorkoutSessionEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutSetEntity::class,
        WorkoutTemplateEntity::class,
        WorkoutTemplateExerciseEntity::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(RoomTypeConverters::class)
abstract class WallCrawlDatabase : RoomDatabase() {

    abstract fun userProfileDao(): UserProfileDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun workoutExerciseDao(): WorkoutExerciseDao
    abstract fun workoutSetDao(): WorkoutSetDao
    abstract fun workoutTemplateDao(): WorkoutTemplateDao

    companion object {
        private const val DATABASE_NAME = "wallcrawl.db"

        @Volatile
        private var INSTANCE: WallCrawlDatabase? = null

        fun getInstance(context: Context): WallCrawlDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WallCrawlDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE workout_sessions " +
                        "ADD COLUMN weightUnit TEXT NOT NULL DEFAULT 'LBS'"
                )
                db.execSQL(
                    "UPDATE workout_sessions SET weightUnit = " +
                        "COALESCE((SELECT preferredUnit FROM user_profiles " +
                        "WHERE id = 'default_user' LIMIT 1), 'LBS')"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "index_workout_sessions_status_completedAtTimestamp " +
                        "ON workout_sessions (status, completedAtTimestamp)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE user_profiles " +
                        "ADD COLUMN revision INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE workout_sessions " +
                        "ADD COLUMN origin TEXT NOT NULL DEFAULT 'PLANNER'"
                )
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN sourceTemplateId TEXT")

                db.execSQL(
                    """
                    CREATE TABLE workout_exercises_new (
                        id TEXT NOT NULL,
                        sessionId TEXT NOT NULL,
                        exerciseId TEXT NOT NULL,
                        orderIndex INTEGER NOT NULL,
                        exerciseType TEXT NOT NULL,
                        targetSets INTEGER NOT NULL,
                        targetRepMin INTEGER,
                        targetRepMax INTEGER,
                        targetWeight REAL,
                        targetAssistanceWeight REAL,
                        targetDurationSeconds INTEGER,
                        targetDistanceMeters REAL,
                        restSeconds INTEGER NOT NULL,
                        notes TEXT NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(sessionId) REFERENCES workout_sessions(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO workout_exercises_new (
                        id, sessionId, exerciseId, orderIndex, exerciseType, targetSets,
                        targetRepMin, targetRepMax, targetWeight, targetAssistanceWeight,
                        targetDurationSeconds, targetDistanceMeters, restSeconds, notes
                    )
                    SELECT
                        id, sessionId, exerciseId, orderIndex, 'WEIGHT_REPS', targetSets,
                        targetRepMin, targetRepMax, targetWeight, NULL, NULL, NULL, 90, notes
                    FROM workout_exercises
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE workout_sets_new (
                        id TEXT NOT NULL,
                        workoutExerciseId TEXT NOT NULL,
                        setNumber INTEGER NOT NULL,
                        exerciseType TEXT NOT NULL,
                        targetReps INTEGER,
                        completedReps INTEGER,
                        targetWeight REAL,
                        completedWeight REAL,
                        targetAssistanceWeight REAL,
                        completedAssistanceWeight REAL,
                        targetDurationSeconds INTEGER,
                        completedDurationSeconds INTEGER,
                        targetDistanceMeters REAL,
                        completedDistanceMeters REAL,
                        isCompleted INTEGER NOT NULL,
                        rpe REAL,
                        rir INTEGER,
                        type TEXT NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(workoutExerciseId) REFERENCES workout_exercises(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO workout_sets_new (
                        id, workoutExerciseId, setNumber, exerciseType, targetReps,
                        completedReps, targetWeight, completedWeight, targetAssistanceWeight,
                        completedAssistanceWeight, targetDurationSeconds, completedDurationSeconds,
                        targetDistanceMeters, completedDistanceMeters, isCompleted, rpe, rir, type
                    )
                    SELECT
                        id, workoutExerciseId, setNumber, 'WEIGHT_REPS', targetReps,
                        completedReps, targetWeight, completedWeight, NULL, NULL, NULL, NULL,
                        NULL, NULL, isCompleted, rpe, rir, type
                    FROM workout_sets
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE workout_sets")
                db.execSQL("DROP TABLE workout_exercises")
                db.execSQL("ALTER TABLE workout_exercises_new RENAME TO workout_exercises")
                db.execSQL("ALTER TABLE workout_sets_new RENAME TO workout_sets")
                db.execSQL(
                    "CREATE INDEX index_workout_exercises_sessionId " +
                        "ON workout_exercises(sessionId)"
                )
                db.execSQL(
                    "CREATE INDEX index_workout_sets_workoutExerciseId " +
                        "ON workout_sets(workoutExerciseId)"
                )

                db.execSQL(
                    """
                    CREATE TABLE workout_templates (
                        id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        createdAtTimestamp INTEGER NOT NULL,
                        updatedAtTimestamp INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE workout_template_exercises (
                        templateId TEXT NOT NULL,
                        orderIndex INTEGER NOT NULL,
                        exerciseId TEXT NOT NULL,
                        exerciseType TEXT NOT NULL,
                        targetSets INTEGER NOT NULL,
                        targetRepMin INTEGER,
                        targetRepMax INTEGER,
                        targetWeight REAL,
                        targetAssistanceWeight REAL,
                        targetDurationSeconds INTEGER,
                        targetDistanceMeters REAL,
                        restSeconds INTEGER NOT NULL,
                        notes TEXT NOT NULL,
                        PRIMARY KEY(templateId, orderIndex),
                        FOREIGN KEY(templateId) REFERENCES workout_templates(id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX index_workout_template_exercises_templateId " +
                        "ON workout_template_exercises(templateId)"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Additive only: existing rows keep every value they already had, and an
                // already-onboarded user's profile is left completely untouched below.
                db.execSQL(
                    "ALTER TABLE user_profiles " +
                        "ADD COLUMN onboardingCompleted INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE user_profiles " +
                        "ADD COLUMN trainingConstraintsJson TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE user_profiles " +
                        "ADD COLUMN returningAfterBreakWeeks INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE user_profiles " +
                        "ADD COLUMN confirmedStartingLoadsJson TEXT NOT NULL DEFAULT ''"
                )
                // A profile from before onboarding existed was never reviewed against the
                // new safety-relevant fields, so it must not be grandfathered in as onboarded.
                db.execSQL("UPDATE user_profiles SET onboardingCompleted = 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE user_profiles " +
                        "ADD COLUMN fitnessGoalsJson TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL("UPDATE user_profiles SET fitnessGoalsJson = primaryGoal WHERE fitnessGoalsJson = ''")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE user_profiles " +
                        "ADD COLUMN themePreference TEXT NOT NULL DEFAULT 'SYSTEM'"
                )
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE user_profiles " +
                        "ADD COLUMN movementCapabilitiesJson TEXT NOT NULL DEFAULT '{}'"
                )
            }
        }

        val ALL_MIGRATIONS: Array<Migration>
            get() = arrayOf(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8
            )
    }
}
