package wallcrawl.elopenmike.com

import android.app.Application
import android.content.Context
import wallcrawl.elopenmike.com.core.ai.FakeWorkoutPlanner
import wallcrawl.elopenmike.com.core.ai.GeneratedWorkoutValidator
import wallcrawl.elopenmike.com.core.ai.WorkoutGenerationContextBuilder
import wallcrawl.elopenmike.com.core.ai.WorkoutHistoryAnalyzer
import wallcrawl.elopenmike.com.core.ai.WorkoutPlanner
import wallcrawl.elopenmike.com.core.database.WallCrawlDatabase
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutRepository
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog

/**
 * Dependency container providing core database, catalog, filter, and AI planner instances.
 */
interface AppContainer {
    val database: WallCrawlDatabase
    val userProfileRepository: UserProfileRepository
    val workoutRepository: WorkoutRepository
    val exerciseCatalog: ExerciseCatalog
    val exerciseFilter: ExerciseFilter
    val workoutPlanner: WorkoutPlanner
    val workoutValidator: GeneratedWorkoutValidator
    val workoutGenerationContextBuilder: WorkoutGenerationContextBuilder
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val database: WallCrawlDatabase by lazy {
        WallCrawlDatabase.getInstance(context)
    }

    override val userProfileRepository: UserProfileRepository by lazy {
        OfflineUserProfileRepository(database.userProfileDao())
    }

    override val workoutRepository: WorkoutRepository by lazy {
        OfflineWorkoutRepository(
            sessionDao = database.workoutSessionDao(),
            setDao = database.workoutSetDao()
        )
    }

    override val exerciseCatalog: ExerciseCatalog by lazy {
        InMemoryExerciseCatalog()
    }

    override val exerciseFilter: ExerciseFilter by lazy {
        ExerciseFilter()
    }

    override val workoutPlanner: WorkoutPlanner by lazy {
        FakeWorkoutPlanner()
    }

    override val workoutValidator: GeneratedWorkoutValidator by lazy {
        GeneratedWorkoutValidator(exerciseCatalog)
    }

    override val workoutGenerationContextBuilder: WorkoutGenerationContextBuilder by lazy {
        WorkoutGenerationContextBuilder(
            userProfileRepository = userProfileRepository,
            workoutRepository = workoutRepository,
            exerciseCatalog = exerciseCatalog,
            exerciseFilter = exerciseFilter,
            historyAnalyzer = WorkoutHistoryAnalyzer()
        )
    }
}

class WallCrawlApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
