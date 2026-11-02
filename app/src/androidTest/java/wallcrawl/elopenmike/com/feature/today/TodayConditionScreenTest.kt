package wallcrawl.elopenmike.com.feature.today

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.AppContainer
import wallcrawl.elopenmike.com.DefaultAppContainer
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.ai.PlannerFeatureFlags
import wallcrawl.elopenmike.com.core.ai.TrainingProgramStateProvider
import wallcrawl.elopenmike.com.core.ai.WorkoutGenerationContextBuilder
import wallcrawl.elopenmike.com.core.database.WallCrawlDatabase
import wallcrawl.elopenmike.com.core.database.repository.OfflineDeloadRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineProgramRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWeeklyDoseLedgerRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutRepository
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

/** TodayScreen end to end: the real planner, the shipped overlay and a reported condition. */
@RunWith(AndroidJUnit4::class)
class TodayConditionScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: WallCrawlDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, WallCrawlDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        composeRule.waitForIdle()
        database.close()
    }

    @Test
    fun todayNamesTheConditionAboveTheMainWorkForAReportedCondition() {
        val gate = Mutex()
        val assets = DefaultAppContainer(context)
        val profiles = OfflineUserProfileRepository(database.userProfileDao(), gate)
        val workouts = OfflineWorkoutRepository(database.workoutSessionDao(), database.workoutSetDao())
        val deloads = OfflineDeloadRepository(database.deloadPreferencesDao(), gate)
        val programs = OfflineProgramRepository(database, gate)
        val ledger = OfflineWeeklyDoseLedgerRepository(
            database.completedWorkoutHistoryDao(), database.weeklyDoseLedgerStateDao(),
            assets.workoutGuideCatalogSource
        )
        val container = object : AppContainer by assets {
            override val workoutGenerationContextBuilder = WorkoutGenerationContextBuilder(
                profiles, workouts, assets.exerciseCatalog, assets.exerciseFilter,
                assets.workoutHistoryAnalyzer, plannerFeatureFlags = PlannerFeatureFlags.PRODUCTION,
                trainingProgramStateProvider = TrainingProgramStateProvider(ledger),
                deloadRepository = deloads, programRepository = programs,
                conditionGuidanceSource = assets.conditionGuidanceSource
            )
        }
        runBlocking {
            profiles.saveProfile(
                UserProfile(
                    name = "Condition test", daysPerWeek = 3,
                    availableEquipment = listOf(StandardEquipment.BODYWEIGHT),
                    experienceLevel = ExperienceLevel.BEGINNER,
                    goals = setOf(FitnessGoal.GENERAL_FITNESS),
                    movementCapabilities = MovementCapabilities.from(
                        MovementCapabilityType.entries.associateWith { CapabilityLevel.COMFORTABLE }
                    ),
                    reportedConditions = setOf("piriformis_syndrome"),
                    onboardingCompleted = true
                )
            )
        }
        val viewModel = TodayViewModel(
            userProfileRepository = profiles, workoutRepository = workouts,
            workoutGenerationContextBuilder = container.workoutGenerationContextBuilder,
            workoutPlanner = container.workoutPlanner, programValidator = container.programValidator,
            conditionGuidanceSource = container.conditionGuidanceSource
        )
        composeRule.setContent {
            WallCrawlTheme {
                TodayScreen(
                    viewModel = viewModel, onStartWorkout = {}, onResumeWorkout = {}, onOpenTemplates = {}
                )
            }
        }
        composeRule.waitUntil(30_000) { viewModel.uiState.value is TodayUiState.Success }
        composeRule.waitForIdle()

        val heading = context.getString(R.string.mobility_today_heading)
        composeRule.onNodeWithTag("today-list").performScrollToNode(hasText(heading))
        composeRule.onNodeWithText(heading).assertIsDisplayed()
        val name = context.getString(R.string.condition_name_piriformis_syndrome)
        composeRule.onNodeWithTag("today-list").performScrollToNode(hasTestTag("today-mobility-card"))
        composeRule.onNodeWithText(
            context.getString(R.string.mobility_today_for, name)
        ).assertIsDisplayed()
    }
}
