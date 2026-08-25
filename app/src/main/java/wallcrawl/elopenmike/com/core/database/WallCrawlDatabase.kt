package wallcrawl.elopenmike.com.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
    version = 1,
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
                ).build().also { INSTANCE = it }
            }
        }
    }
}
