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
import wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutTemplateRepository
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutTemplateRepository
import wallcrawl.elopenmike.com.core.exercise.BundledExerciseCatalog
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisualProvider
import wallcrawl.elopenmike.com.core.exercise.visual.WorkoutGuideVisualProvider
import wallcrawl.elopenmike.com.core.exercise.workoutguide.AssetAttributionNoticeReader
import wallcrawl.elopenmike.com.core.exercise.workoutguide.AttributionNoticeSource
import wallcrawl.elopenmike.com.core.exercise.workoutguide.WorkoutGuideCatalogSource
import wallcrawl.elopenmike.com.core.exercise.workoutguide.WorkoutGuideCatalogStore
import wallcrawl.elopenmike.com.core.progress.ProgressCalculator

/**
 * Dependency container providing core database, catalog, filter, and AI planner instances.
 */
interface AppContainer {
    val database: WallCrawlDatabase
    val userProfileRepository: UserProfileRepository
    val workoutRepository: WorkoutRepository
    val workoutTemplateRepository: WorkoutTemplateRepository
    val exerciseCatalog: ExerciseCatalog
    val exerciseVisualProvider: ExerciseVisualProvider
    val exerciseFilter: ExerciseFilter
    val workoutPlanner: WorkoutPlanner
    val workoutValidator: GeneratedWorkoutValidator
    val workoutGenerationContextBuilder: WorkoutGenerationContextBuilder
    val workoutHistoryAnalyzer: WorkoutHistoryAnalyzer
    val progressCalculator: ProgressCalculator
    val workoutGuideCatalogSource: WorkoutGuideCatalogSource
    val attributionNoticeSource: AttributionNoticeSource
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    private val workoutGuideCatalogStore: WorkoutGuideCatalogStore by lazy {
        WorkoutGuideCatalogStore(context.assets)
    }

    override val workoutGuideCatalogSource: WorkoutGuideCatalogSource
        get() = workoutGuideCatalogStore

    override val attributionNoticeSource: AttributionNoticeSource by lazy {
        AssetAttributionNoticeReader(context.assets)
    }

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

    override val workoutTemplateRepository: WorkoutTemplateRepository by lazy {
        OfflineWorkoutTemplateRepository(
            templateDao = database.workoutTemplateDao(),
            exerciseCatalog = exerciseCatalog
        )
    }

    override val exerciseCatalog: ExerciseCatalog by lazy {
        BundledExerciseCatalog(workoutGuideCatalogStore)
    }

    override val exerciseVisualProvider: ExerciseVisualProvider by lazy {
        WorkoutGuideVisualProvider(workoutGuideCatalogStore)
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
            historyAnalyzer = workoutHistoryAnalyzer
        )
    }

    override val workoutHistoryAnalyzer: WorkoutHistoryAnalyzer by lazy {
        WorkoutHistoryAnalyzer()
    }

    override val progressCalculator: ProgressCalculator by lazy {
        ProgressCalculator()
    }
}

class WallCrawlApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
