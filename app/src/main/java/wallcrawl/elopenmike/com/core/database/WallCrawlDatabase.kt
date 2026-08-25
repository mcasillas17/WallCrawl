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
import wallcrawl.elopenmike.com.core.database.entity.UserProfileEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutExerciseEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSessionEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSetEntity

@Database(
    entities = [
        UserProfileEntity::class,
        WorkoutSessionEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutSetEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(RoomTypeConverters::class)
abstract class WallCrawlDatabase : RoomDatabase() {

    abstract fun userProfileDao(): UserProfileDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun workoutExerciseDao(): WorkoutExerciseDao
    abstract fun workoutSetDao(): WorkoutSetDao

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
    }
}
