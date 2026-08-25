package wallcrawl.elopenmike.com.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

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
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): WallCrawlDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                WallCrawlDatabase::class.java,
                DATABASE_NAME
            )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed initial default user profile & sample history in background
                        CoroutineScope(Dispatchers.IO).launch {
                            getInstance(context).seedInitialData()
                        }
                    }
                })
                .fallbackToDestructiveMigration()
                .build()
        }
    }

    suspend fun seedInitialData() {
        val profileDao = userProfileDao()
        val sessionDao = workoutSessionDao()
        val exerciseDao = workoutExerciseDao()
        val setDao = workoutSetDao()

        // 1. Seed Default User Profile
        val defaultProfile = UserProfileEntity(
            id = UserProfile.DEFAULT_PROFILE_ID,
            name = "Crawler",
            primaryGoal = FitnessGoal.BUILD_MUSCLE,
            experienceLevel = ExperienceLevel.INTERMEDIATE,
            preferredDurationMinutes = 50,
            daysPerWeek = 4,
            availableEquipmentJson = listOf(
                StandardEquipment.BARBELL,
                StandardEquipment.DUMBBELL,
                StandardEquipment.BENCH,
                StandardEquipment.PULLUP_BAR,
                StandardEquipment.BODYWEIGHT,
                StandardEquipment.CABLE
            ).joinToString("|||"),
            preferredUnit = WeightUnit.LBS,
            musclePrioritiesJson = "${StandardMuscles.CHEST}:HIGH|||${StandardMuscles.SHOULDERS}:HIGH|||${StandardMuscles.BACK}:NORMAL|||${StandardMuscles.TRICEPS}:NORMAL",
            excludedExerciseIdsJson = ""
        )
        profileDao.insertOrUpdate(defaultProfile)

        // 2. Seed a completed past workout session to populate Progress immediately
        val pastSessionId = UUID.randomUUID().toString()
        val twoDaysAgo = System.currentTimeMillis() - (48 * 60 * 60 * 1000L)
        val pastSession = WorkoutSessionEntity(
            id = pastSessionId,
            name = "Upper Body · Hypertrophy",
            startedAtTimestamp = twoDaysAgo - (52 * 60 * 1000L),
            completedAtTimestamp = twoDaysAgo,
            targetDurationMinutes = 50,
            actualDurationMinutes = 52,
            status = SessionStatus.COMPLETED,
            focusMusclesJson = listOf(StandardMuscles.CHEST, StandardMuscles.BACK, StandardMuscles.SHOULDERS).joinToString("|||"),
            notes = "Felt great, solid chest pump."
        )
        sessionDao.insertSession(pastSession)

        val ex1Id = UUID.randomUUID().toString()
        val ex1 = WorkoutExerciseEntity(
            id = ex1Id,
            sessionId = pastSessionId,
            exerciseId = "incline-dumbbell-press",
            orderIndex = 0,
            targetSets = 3,
            targetRepMin = 8,
            targetRepMax = 10,
            targetWeight = 45.0,
            notes = ""
        )
        exerciseDao.insertExercise(ex1)

        val sets1 = listOf(
            WorkoutSetEntity(
                id = UUID.randomUUID().toString(),
                workoutExerciseId = ex1Id,
                setNumber = 1,
                targetReps = 10,
                completedReps = 10,
                targetWeight = 45.0,
                completedWeight = 45.0,
                isCompleted = true,
                rpe = 8.0f,
                rir = 2,
                type = SetType.NORMAL
            ),
            WorkoutSetEntity(
                id = UUID.randomUUID().toString(),
                workoutExerciseId = ex1Id,
                setNumber = 2,
                targetReps = 10,
                completedReps = 9,
                targetWeight = 45.0,
                completedWeight = 45.0,
                isCompleted = true,
                rpe = 8.5f,
                rir = 1,
                type = SetType.NORMAL
            ),
            WorkoutSetEntity(
                id = UUID.randomUUID().toString(),
                workoutExerciseId = ex1Id,
                setNumber = 3,
                targetReps = 10,
                completedReps = 8,
                targetWeight = 45.0,
                completedWeight = 45.0,
                isCompleted = true,
                rpe = 9.5f,
                rir = 0,
                type = SetType.NORMAL
            )
        )
        setDao.insertSets(sets1)

        val ex2Id = UUID.randomUUID().toString()
        val ex2 = WorkoutExerciseEntity(
            id = ex2Id,
            sessionId = pastSessionId,
            exerciseId = "pull-ups",
            orderIndex = 1,
            targetSets = 3,
            targetRepMin = 6,
            targetRepMax = 10,
            targetWeight = null,
            notes = ""
        )
        exerciseDao.insertExercise(ex2)

        val sets2 = listOf(
            WorkoutSetEntity(
                id = UUID.randomUUID().toString(),
                workoutExerciseId = ex2Id,
                setNumber = 1,
                targetReps = 8,
                completedReps = 8,
                targetWeight = null,
                completedWeight = null,
                isCompleted = true,
                rpe = 8.0f,
                rir = 2,
                type = SetType.NORMAL
            ),
            WorkoutSetEntity(
                id = UUID.randomUUID().toString(),
                workoutExerciseId = ex2Id,
                setNumber = 2,
                targetReps = 8,
                completedReps = 7,
                targetWeight = null,
                completedWeight = null,
                isCompleted = true,
                rpe = 9.0f,
                rir = 1,
                type = SetType.NORMAL
            )
        )
        setDao.insertSets(sets2)
    }
}
