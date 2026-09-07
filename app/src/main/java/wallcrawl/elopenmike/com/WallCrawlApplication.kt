package wallcrawl.elopenmike.com

import android.app.Application
import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.sync.Mutex
import wallcrawl.elopenmike.com.core.ai.FakeWorkoutPlanner
import wallcrawl.elopenmike.com.core.ai.GeneratedWorkoutValidator
import wallcrawl.elopenmike.com.core.ai.PlannerFeatureFlags
import wallcrawl.elopenmike.com.core.ai.ProgramValidator
import wallcrawl.elopenmike.com.core.ai.TrainingProgramStateProvider
import wallcrawl.elopenmike.com.core.ai.WorkoutGenerationContextBuilder
import wallcrawl.elopenmike.com.core.ai.WorkoutHistoryAnalyzer
import wallcrawl.elopenmike.com.core.ai.WorkoutPlanner
import wallcrawl.elopenmike.com.core.database.WallCrawlDatabase
import wallcrawl.elopenmike.com.core.database.repository.LocalDataBackupRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineLocalDataBackupRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutTemplateRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWeeklyDoseLedgerRepository
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WeeklyDoseLedgerRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutTemplateRepository
import wallcrawl.elopenmike.com.core.exercise.BundledExerciseCatalog
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.exercise.localization.ExerciseLocalizationSource
import wallcrawl.elopenmike.com.core.exercise.localization.ExerciseLocalizationStore
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
    val weeklyDoseLedgerRepository: WeeklyDoseLedgerRepository
    val workoutTemplateRepository: WorkoutTemplateRepository
    val localDataBackupRepository: LocalDataBackupRepository
    val exerciseCatalog: ExerciseCatalog
    val exerciseLocalizationSource: ExerciseLocalizationSource
    val exerciseVisualProvider: ExerciseVisualProvider
    val exerciseFilter: ExerciseFilter
    val workoutPlanner: WorkoutPlanner
    val workoutValidator: GeneratedWorkoutValidator
    val programValidator: ProgramValidator
    val workoutGenerationContextBuilder: WorkoutGenerationContextBuilder
    val workoutHistoryAnalyzer: WorkoutHistoryAnalyzer
    val progressCalculator: ProgressCalculator
    val workoutGuideCatalogSource: WorkoutGuideCatalogSource
    val attributionNoticeSource: AttributionNoticeSource
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    /**
     * One gate shared by every repository that writes user-owned rows.
     *
     * Deleting or restoring all local data has to beat the ordinary writes it races, so the
     * destructive operations and the profile and template writers take the same lock rather
     * than each guarding only itself.
     */
    private val localDataWriteGate = Mutex()

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
        OfflineUserProfileRepository(
            userProfileDao = database.userProfileDao(),
            localDataWriteGate = localDataWriteGate
        )
    }

    override val workoutRepository: WorkoutRepository by lazy {
        OfflineWorkoutRepository(
            sessionDao = database.workoutSessionDao(),
            setDao = database.workoutSetDao()
        )
    }

    /**
     * Reconstructs the weekly dose ledger from completed history. The state-based prescription
     * policy reads it only on the production-disabled reviewed-eligibility path.
     */
    override val weeklyDoseLedgerRepository: WeeklyDoseLedgerRepository by lazy {
        OfflineWeeklyDoseLedgerRepository(
            historyDao = database.completedWorkoutHistoryDao(),
            ledgerStateDao = database.weeklyDoseLedgerStateDao(),
            catalogSource = workoutGuideCatalogSource
        )
    }

    override val workoutTemplateRepository: WorkoutTemplateRepository by lazy {
        OfflineWorkoutTemplateRepository(
            templateDao = database.workoutTemplateDao(),
            exerciseCatalog = exerciseCatalog,
            localDataWriteGate = localDataWriteGate
        )
    }

    /**
     * Owns user-driven export, restore, and deletion across every table.
     *
     * Provenance is read from the installed package and the loaded catalog, so an archive
     * records the build and catalog that produced it without adding a build-time constant
     * that could drift. An unavailable catalog snapshot simply omits the commit; it never
     * blocks an export.
     */
    override val localDataBackupRepository: LocalDataBackupRepository by lazy {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        OfflineLocalDataBackupRepository(
            backupDao = database.localDataBackupDao(),
            appVersionName = packageInfo?.versionName ?: UNKNOWN_APP_VERSION,
            appVersionCode = packageInfo?.let(PackageInfoCompat::getLongVersionCode) ?: 0L,
            catalogCommit = {
                workoutGuideCatalogStore.currentSnapshot()?.catalogAttribution?.commit
            },
            localDataWriteGate = localDataWriteGate
        )
    }

    override val exerciseLocalizationSource: ExerciseLocalizationSource by lazy {
        ExerciseLocalizationStore(context.assets)
    }

    override val exerciseCatalog: ExerciseCatalog by lazy {
        BundledExerciseCatalog(workoutGuideCatalogStore, exerciseLocalizationSource)
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

    /**
     * Whole-program validation for every automatic recommendation.
     *
     * It reuses [workoutValidator] rather than restating the catalog and candidate checks,
     * and it takes the same `STATE_BASED_DOSE_EFFORT_REST_V1` defaults the prescription
     * policy uses, so the allowance a plan is checked against is the one it was built under.
     */
    override val programValidator: ProgramValidator by lazy {
        ProgramValidator(workoutValidator)
    }

    override val workoutGenerationContextBuilder: WorkoutGenerationContextBuilder by lazy {
        WorkoutGenerationContextBuilder(
            userProfileRepository = userProfileRepository,
            workoutRepository = workoutRepository,
            exerciseCatalog = exerciseCatalog,
            exerciseFilter = exerciseFilter,
            historyAnalyzer = workoutHistoryAnalyzer,
            plannerFeatureFlags = PlannerFeatureFlags(
                reviewedCapabilityEligibility = false
            ),
            trainingProgramStateProvider = TrainingProgramStateProvider(
                weeklyDoseLedgerRepository = weeklyDoseLedgerRepository
            ),
            // Read from the already-loaded snapshot, so recording the catalog a plan was
            // built against never forces asset I/O on the generation path.
            catalogVersion = {
                workoutGuideCatalogStore.currentSnapshot()?.catalogAttribution?.commit
            }
        )
    }

    override val workoutHistoryAnalyzer: WorkoutHistoryAnalyzer by lazy {
        WorkoutHistoryAnalyzer()
    }

    override val progressCalculator: ProgressCalculator by lazy {
        ProgressCalculator()
    }

    private companion object {
        const val UNKNOWN_APP_VERSION = "unknown"
    }
}

class WallCrawlApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
