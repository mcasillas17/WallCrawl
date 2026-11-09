package wallcrawl.elopenmike.com.feature.workout

import android.graphics.Bitmap
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import wallcrawl.elopenmike.com.WallCrawlApplication
import wallcrawl.elopenmike.com.core.ui.localization.ExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary
import java.util.Locale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.onRoot
import java.io.File
import wallcrawl.elopenmike.com.core.model.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.ai.WorkoutHistoryAnalyzer
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisual
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisualProvider
import wallcrawl.elopenmike.com.core.ai.RecommendationSnapshot
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetOutcomeRules
import wallcrawl.elopenmike.com.core.model.SetPerformanceInput
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.model.WorkoutSummary
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate
import androidx.compose.material3.MaterialTheme
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

/**
 * Drives the assembled active-workout screen: the rest countdown, its explicit controls,
 * and the confirmations guarding finish and discard.
 */
class ActiveWorkoutScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * A label read from resources rather than repeated here. These tests are about the rest
     * timer and the discard confirmation, not about the wording of a button, so a copy edit
     * in either language must not break them.
     */
    private fun text(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as WallCrawlApplication
    private var destructive = Color.Unspecified
    private val clock = TestClock()
    private val repository = ScreenTestRepository(session())
    private val viewModel = ActiveWorkoutViewModel(
        sessionId = SESSION_ID,
        workoutRepository = repository,
        exerciseCatalog = app.container.exerciseCatalog,
        workoutHistoryAnalyzer = WorkoutHistoryAnalyzer(),
        nowMillis = { WALL_CLOCK },
        elapsedRealtimeClock = clock
    )

    @Test fun existingWeightTargetAndPreviousPerformanceAreVisibleTogether() {
        val active = session().let { base ->
            base.copy(name = "Push · Athletic conditioning", exercises = base.exercises.map {
                it.copy(exerciseId = "dumbbell-bench-press")
            })
        }
        val prior = active.copy(id = "earlier", status = SessionStatus.COMPLETED, completedAtTimestamp = WALL_CLOCK - 1,
            exercises = active.exercises.map { ex -> ex.copy(sets = ex.sets.map { it.copy(isCompleted = true,
                completedWeight = 35.0, completedReps = 8) }) })
        val repo = ScreenTestRepository(active, listOf(prior))
        val vm = ActiveWorkoutViewModel(SESSION_ID, repo, app.container.exerciseCatalog, WorkoutHistoryAnalyzer())
        composeRule.setContent { WallCrawlTheme(themePreference = ThemePreference.DARK) {
            ActiveWorkoutScreen(vm, app.container.exerciseVisualProvider, {}, {})
        } }
        composeRule.onNodeWithText("Planned weight · 40 lb").assertIsDisplayed()
        composeRule.onNodeWithText("Last time · 35 lb × 8").assertIsDisplayed()
        composeRule.onNodeWithText("Log set").assertIsEnabled()
        capture("set-with-history")
        composeRule.onNodeWithText("Log set").performClick()
        composeRule.waitForIdle()
        assertThat(runBlocking { repo.getActiveSessionOnce() }!!.exercises.single().sets.first().completedWeight).isEqualTo(40.0)
    }

    private fun firstPress(): WorkoutSession {
        val base = session()
        val exercise = base.exercises.single().let { ex ->
            ex.copy(exerciseId = "dumbbell-bench-press",
                prescription = ex.prescription.copy(targetWeight = null, effortTarget = EffortTarget(2, 4)),
                sets = ex.sets.map { it.copy(targetWeight = null) })
        }
        return base.copy(name = "Push · Athletic conditioning", exercises = listOf(exercise))
    }

    private fun showFirstPress(repo: ScreenTestRepository, profile: UserProfile) {
        val profiles = object : UserProfileRepository by app.container.userProfileRepository {
            override fun getUserProfile() = flowOf(profile)
        }
        val vm = ActiveWorkoutViewModel(SESSION_ID, repo, app.container.exerciseCatalog, WorkoutHistoryAnalyzer(),
            userProfileRepository = profiles)
        composeRule.setContent { WallCrawlTheme(themePreference = ThemePreference.DARK) {
            ActiveWorkoutScreen(vm, app.container.exerciseVisualProvider, {}, {})
        } }
        composeRule.onNodeWithText("Aim for 8–10 reps with 2–4 left in reserve.").assertExists()
    }

    @Test fun firstWorkingWeightCarriesIntoTheNextSetOnlyAfterLogging() {
        val active = firstPress()
        val now = active.startedAtTimestamp
        val related = session().let { prior ->
            prior.copy(id = "earlier", status = SessionStatus.COMPLETED, startedAtTimestamp = now - 95_000,
                completedAtTimestamp = now - 60_000, exercises = prior.exercises.map { lift ->
                    lift.copy(sessionId = "earlier", sets = lift.sets.map { it.copy(
                        completedAtTimestamp = now - 70_000, isCompleted = true, completedWeight = 22.5, completedReps = 8) })
                })
        }
        val repo = ScreenTestRepository(active, listOf(related))
        val profile = UserProfile(onboardingCompleted = true,
            availableEquipment = listOf(StandardEquipment.DUMBBELL, StandardEquipment.BENCH),
            dumbbellRack = DumbbellRack(WeightUnit.LBS, listOf(7.5, 12.5, 17.5, 22.5)))
        showFirstPress(repo, profile)
        val recorded = composeRule.onNodeWithText("You logged Incline Dumbbell Press · 22.5 lb × 8")
        recorded.performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Log set").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Choose 22.5 lb").assertDoesNotExist()
        composeRule.onNodeWithText("I logged that per dumbbell").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Choose 22.5 lb").assertExists()
        composeRule.onNodeWithText("Log set").assertIsNotEnabled()
        capture("first-weight-with-rack")
        composeRule.onNodeWithContentDescription("Choose 12.5 lb").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Increase Load for set 1").performClick()
        composeRule.onNodeWithText("Log set").performScrollTo().performClick()
        composeRule.onNodeWithText("Weight from set 1").performScrollTo().assertIsDisplayed()
        assertThat(runBlocking { repo.getActiveSessionOnce() }!!.exercises.single().sets.last().completedWeight).isNull()
        composeRule.onNodeWithText("Log set").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertThat(runBlocking { repo.getActiveSessionOnce() }!!.exercises.single().sets.map { it.completedWeight })
            .containsExactly(15.0, 15.0).inOrder()
    }

    @Test fun noRackOrHistoryStillLetsTheLifterChooseInline() {
        val repo = ScreenTestRepository(firstPress())
        showFirstPress(repo, UserProfile())
        composeRule.onNodeWithText("From your dumbbell rack").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Choose 15 lb").assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.guided_set_need_load_reps)).assertDoesNotExist()
        composeRule.onNodeWithText("Log set").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Increase Load for set 1").performScrollTo().performClick()
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        composeRule.onNodeWithText("Log set").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertThat(runBlocking { repo.getActiveSessionOnce() }!!.exercises.single().sets.first().completedWeight).isEqualTo(2.5)
    }

    @Test fun supportiveLoggingShowsFrozenExecutionAndKeepsFinishReachable() {
        val selected = SelectedCondition("withdrawn", 1, mapOf("side" to "recorded-left"))
        val instruction = ConditionText("Original fictional logging instruction", "Instrucción ficticia de registro original")
        val frozen = SessionConditionContext(1, 1, listOf(selected), mapOf("withdrawn" to "old-v1"), listOf(
            AppliedConditionRule(ConditionRuleReference("withdrawn", "old-v1", "case", "rule", 1),
                ConditionExerciseIdentity("incline-dumbbell-press", "a".repeat(64), ExerciseType.WEIGHT_REPS, "b".repeat(64)),
                "original-execution", PrescriptionPurpose.SUPPORTIVE, instruction)), emptyList(), emptyList())
        val initial = session()
        val supportive = initial.copy(conditionContext = frozen, exercises = initial.exercises.map {
            it.copy(prescription = it.prescription.copy(purpose = PrescriptionPurpose.SUPPORTIVE)) })
        val vm = ActiveWorkoutViewModel(SESSION_ID, ScreenTestRepository(supportive), InMemoryExerciseCatalog(), WorkoutHistoryAnalyzer())
        composeRule.setContent { WallCrawlTheme(themePreference = ThemePreference.DARK) { ActiveWorkoutScreen(vm, NoVisualProvider, {}, {}) } }
        composeRule.onNodeWithText(instruction.en).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.condition_supportive)).assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File) ?: context.filesDir
        output.mkdirs()
        File(output, "active-frozen-supportive-dark.png").outputStream().use {
            composeRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        composeRule.onNodeWithText("Finish").performClick()
        composeRule.onNodeWithText("Finish anyway").assertIsDisplayed()
    }

    @Test
    fun adjustedLoadSurvivesSwitchingSetsAndAnUnrelatedPersistedUpdate() {
        showScreen()
        composeRule.onNodeWithContentDescription("Increase Load for set 1").performScrollTo().performClick()
        composeRule.onNodeWithText("42.5").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Set 2").performScrollTo().performClick()
        repository.historyReady.value = false
        try {
            composeRule.runOnIdle {
                runBlocking {
                    repository.logSetCompletion("set-2", SetPerformanceInput(
                        reps = 11, weight = 40.0, isCompleted = false
                    ))
                }
            }
            composeRule.onNodeWithContentDescription("Set 1").performScrollTo().performClick()
            composeRule.onNodeWithText("42.5").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Log set").performScrollTo().performClick()
            composeRule.waitForIdle()
            assertThat(runBlocking { repository.getActiveSessionOnce() }!!.exercises.first()
                .sets.first().completedWeight).isEqualTo(42.5)
            assertThat(repository.historySubscriptions).isEqualTo(1)
        } finally {
            repository.historyReady.value = true
        }
    }

    @Test
    fun completingASet_showsTheRestCountdownWithItsExplicitControls() {
        showScreen()

        composeRule.onNodeWithText("Log set").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Rest remaining 2:00").assertIsDisplayed()

        composeRule.onNodeWithText(text(R.string.rest_add_thirty)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Rest remaining 2:30").assertIsDisplayed()

        composeRule.onNodeWithText("Skip rest").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Rest finished").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Rest timer options").performClick()
        composeRule.onNodeWithText("Dismiss timer").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasTextStartingWith("Rest")).assertCountEquals(0)
    }

    @Test
    fun spanishFineStepsWithRestKeepLogReachable() {
        val locale = Locale.forLanguageTag("es-ES")
        val localized = app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(locale) })
        val vocabulary = ExerciseVocabulary(runBlocking { app.container.exerciseLocalizationSource.localization() }, locale.toLanguageTag())
        composeRule.setContent {
            CompositionLocalProvider(LocalConfiguration provides localized.resources.configuration,
                LocalContext provides localized, LocalResources provides localized.resources,
                LocalExerciseVocabulary provides vocabulary) {
                WallCrawlTheme(themePreference = ThemePreference.LIGHT) {
                    ActiveWorkoutScreen(viewModel, app.container.exerciseVisualProvider, {}, {})
                }
            }
        }
        val log = localized.getString(R.string.guided_log_set)
        composeRule.onNodeWithText(log).performScrollTo().performClick()
        composeRule.waitUntil(10_000) {
            (viewModel.uiState.value as? ActiveWorkoutUiState.Active)?.restTimer?.isRunning == true
        }
        val load = localized.getString(R.string.set_field_load)
        val withUnit = localized.getString(R.string.set_field_with_unit, load, "lb")
        val increase = localized.getString(R.string.set_field_increase_accessibility, load, "2")
        listOf("2,5", "1").forEach { step ->
            composeRule.onNodeWithContentDescription(localized.getString(R.string.guided_change_step, withUnit, step))
                .performScrollTo().performClick()
        }
        composeRule.onNodeWithContentDescription(increase).performScrollTo().performClick()
        composeRule.onNodeWithText("40,1").assertIsDisplayed()
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        composeRule.onNodeWithText(log).performScrollTo().assertIsDisplayed()
        capture("guided-steps-es-light")
        composeRule.onNodeWithText(log).performClick()
        composeRule.waitUntil(10_000) {
            runBlocking { repository.getActiveSessionOnce() }!!.exercises.first().sets[1].completedWeight == 40.1
        }
    }

    @Test
    fun rejectedWriteLeavesTheEditorAndCanBeRetried() {
        showScreen()
        capture("guided-active-en")
        repository.rejectWrites = true
        composeRule.onNodeWithContentDescription("Increase Load for set 1").performClick()
        composeRule.onNodeWithText("Log set").performClick()
        composeRule.onNodeWithText(text(R.string.workout_set_update_error)).performScrollTo().assertIsDisplayed()
        capture("guided-save-error-en")
        composeRule.onNodeWithText("42.5").assertExists()
        repository.rejectWrites = false
        composeRule.onNodeWithText("Log set").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Rest remaining 2:00").performScrollTo().assertIsDisplayed()
        capture("guided-rest-en")
        composeRule.onNodeWithText("Finish").performClick()
        composeRule.onNodeWithText("Finish anyway").performClick()
        composeRule.onNodeWithText("Done").assertIsDisplayed()
        capture("guided-summary-en")
    }

    @Test
    fun swipingArtworkChangesExercisesAndTappingOpensGuidance() {
        val original = session()
        val first = original.exercises.single()
        val second = first.copy(id = "second", exerciseId = "lat-pulldown", orderIndex = 1,
            sets = first.sets.map { it.copy(id = "second-${it.id}", workoutExerciseId = "second") })
        val vm = ActiveWorkoutViewModel(SESSION_ID, ScreenTestRepository(original.copy(exercises = listOf(first, second))),
            InMemoryExerciseCatalog(), WorkoutHistoryAnalyzer(), elapsedRealtimeClock = clock)
        composeRule.setContent { WallCrawlTheme { ActiveWorkoutScreen(vm, NoVisualProvider, {}, {}) } }
        val art = composeRule.onNodeWithContentDescription("Expand ", substring = true)
        art.performTouchInput { swipeRight() }
        assertThat((vm.uiState.value as ActiveWorkoutUiState.Active).currentExerciseIndex).isEqualTo(0)
        composeRule.onNodeWithText("Log set").performClick()
        composeRule.waitUntil(10_000) { (vm.uiState.value as? ActiveWorkoutUiState.Active)?.restTimer?.isRunning == true }
        composeRule.onNodeWithContentDescription("Rest remaining 2:00").assertIsDisplayed()
        val deadline = RestTimerState.Running("set-1", 120_000L)
        composeRule.onNodeWithContentDescription("Decrease Load for set 2").performScrollTo().performClick()
        art.performScrollTo().performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("Previous exercise").assertExists()
        assertThat((vm.uiState.value as ActiveWorkoutUiState.Active).currentExerciseIndex).isEqualTo(1)
        art.performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("Close").assertDoesNotExist()
        art.performClick()
        composeRule.onNodeWithText("Close").assertIsDisplayed().performClick()
        art.performTouchInput { swipeRight() }
        composeRule.onNodeWithText("Next exercise").assertExists()
        composeRule.onNodeWithText("37.5").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Log set").assertIsEnabled()
        assertThat((vm.uiState.value as ActiveWorkoutUiState.Active).restTimer.state).isEqualTo(deadline)
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File) ?: context.filesDir
        output.mkdirs()
        File(output, "$name.png").outputStream().use {
            composeRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun timeSpentInTheBackground_isReflectedWhenTheCountdownIsRefreshed() {
        showScreen()
        composeRule.onNodeWithText("Log set").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        // The app was away for ninety seconds and ticked nothing while it was gone.
        clock.advance(90_000L)
        viewModel.onRestTimerTick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Rest remaining 0:30").assertIsDisplayed()
    }

    @Test
    fun theRunningRestTimer_survivesRecomposition() {
        var recompositionKey by mutableStateOf(0)
        composeRule.setContent {
            WallCrawlTheme {
                key(recompositionKey) {
                    ActiveWorkoutScreen(
                        viewModel = viewModel,
                        visualProvider = NoVisualProvider,
                        onNavigateBack = {},
                        onWorkoutFinished = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("Log set").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Rest remaining 2:00").assertIsDisplayed()

        recompositionKey = 1
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Rest remaining 2:00").assertIsDisplayed()
    }

    @Test
    fun finishingWithOpenSets_confirmsTheCountBeforePersistingAnything() {
        showScreen()

        composeRule.onNodeWithText("Finish").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Finish with 2 unlogged sets?").assertIsDisplayed()
        assertThat(repository.completeCalls).isEqualTo(0)

        composeRule.onNodeWithText("Keep going").performClick()
        composeRule.waitForIdle()
        assertThat(repository.completeCalls).isEqualTo(0)

        composeRule.onNodeWithText("Finish").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Finish anyway").performClick()
        composeRule.waitForIdle()

        assertThat(repository.completeCalls).isEqualTo(1)
    }

    @Test
    fun closingTheWorkout_requiresAnExplicitDiscardConfirmation() {
        showScreen()

        composeRule.onNodeWithContentDescription(text(R.string.guided_leave_workout)).performClick()
        composeRule.onNodeWithText(text(R.string.guided_discard_workout)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Discard this workout?").assertIsDisplayed()
        composeRule.onNodeWithText("Keep workout").performClick()
        composeRule.waitForIdle()
        assertThat(repository.cancelCalls).isEqualTo(0)

        composeRule.onNodeWithContentDescription(text(R.string.guided_leave_workout)).performClick()
        composeRule.onNodeWithText(text(R.string.guided_discard_workout)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Discard").performClick()
        composeRule.waitForIdle()

        assertThat(repository.cancelCalls).isEqualTo(1)
    }

    @Test
    fun confirmationDialogs_reserveRedForDestructiveActions() {
        showScreen()

        composeRule.onNodeWithText("Finish").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Finish anyway").assertContainsColor(destructive)
        composeRule.onNodeWithText("Keep going").assertDoesNotContainColor(destructive)
        composeRule.onNodeWithText("Keep going").performClick()

        composeRule.onNodeWithContentDescription(text(R.string.guided_leave_workout)).performClick()
        composeRule.onNodeWithText(text(R.string.guided_discard_workout)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Discard").assertContainsColor(destructive)
        composeRule.onNodeWithText("Keep workout").assertDoesNotContainColor(destructive)
    }

    private fun showScreen() {
        composeRule.setContent {
            WallCrawlTheme {
                destructive = MaterialTheme.colorScheme.error
                ActiveWorkoutScreen(
                    viewModel = viewModel,
                    visualProvider = app.container.exerciseVisualProvider,
                    onNavigateBack = {},
                    onWorkoutFinished = {},
                    onOpenHistory = {}
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun session(): WorkoutSession {
        val workoutExerciseId = "workout-exercise"
        return WorkoutSession(
            id = SESSION_ID,
            name = "Push",
            status = SessionStatus.IN_PROGRESS,
            exercises = listOf(
                WorkoutExercise(
                    id = workoutExerciseId,
                    sessionId = SESSION_ID,
                    exerciseId = "incline-dumbbell-press",
                    orderIndex = 0,
                    prescription = ExercisePrescription(
                        exerciseType = ExerciseType.WEIGHT_REPS,
                        targetSets = 2,
                        repRange = RepRange(8, 10),
                        targetWeight = 40.0,
                        restSeconds = 120
                    ),
                    sets = (1..2).map { number ->
                        WorkoutSet(
                            id = "set-$number",
                            workoutExerciseId = workoutExerciseId,
                            setNumber = number,
                            targetReps = 10,
                            targetWeight = 40.0,
                            isCompleted = false
                        )
                    }
                )
            )
        )
    }

    private fun hasTextStartingWith(prefix: String) = SemanticsMatcher(
        "text starts with '$prefix'"
    ) { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith(prefix) } == true
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertContainsColor(
        expected: Color
    ) {
        assertThat(closestColorDistance(expected)).isAtMost(COLOR_MATCH_TOLERANCE)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotContainColor(
        unexpected: Color
    ) {
        assertThat(closestColorDistance(unexpected)).isGreaterThan(COLOR_MATCH_TOLERANCE)
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.closestColorDistance(
        target: Color
    ): Float {
        val pixels = captureToImage().toPixelMap()
        var closest = Float.MAX_VALUE
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val color = pixels[x, y]
                val distance =
                    square(color.red - target.red) +
                        square(color.green - target.green) +
                        square(color.blue - target.blue)
                closest = minOf(closest, distance)
            }
        }
        return closest
    }

    private fun square(value: Float): Float = value * value

    private companion object {
        const val SESSION_ID = "session"
        const val WALL_CLOCK = 1_777_777L
        const val COLOR_MATCH_TOLERANCE = 0.01f
    }
}

private class TestClock(private var now: Long = 0L) : ElapsedRealtimeClock {
    override fun elapsedRealtimeMillis(): Long = now
    fun advance(millis: Long) {
        now += millis
    }
}

private object NoVisualProvider : ExerciseVisualProvider {
    override fun framesFor(exerciseId: String) = emptyList<ExerciseVisual>()
}

private class ScreenTestRepository(initial: WorkoutSession, private val history: List<WorkoutSession> = emptyList()) : WorkoutRepository {
    override fun observePendingConditionReassessments(profileId: String): Flow<List<PendingConditionReassessment>> = flowOf(emptyList())
    override suspend fun hasPendingConditionReassessment(profileId: String) = false

    override suspend fun getCompletedSessionsInRange(startTimestamp: Long, endTimestampExclusive: Long): List<WorkoutSession> = emptyList()
    private val session = MutableStateFlow<WorkoutSession?>(initial)
    var rejectWrites: Boolean = false
    var completeCalls: Int = 0
        private set
    var cancelCalls: Int = 0
        private set
    val historyReady = MutableStateFlow(true)
    var historySubscriptions = 0
        private set

    override fun observeActiveSession(): Flow<WorkoutSession?> = session
    override suspend fun getActiveSessionOnce(): WorkoutSession? = session.value
    override suspend fun getSessionById(sessionId: String): WorkoutSession? = session.value
    override fun observeSession(sessionId: String): Flow<WorkoutSession?> = session
    override fun observeCompletedSessions(limit: Int): Flow<List<WorkoutSession>> = flow {
        historySubscriptions++
        historyReady.first { it }
        emit(history.take(limit))
        awaitCancellation()
    }

    override fun observeCompletedWorkoutCount(): Flow<Int> = flowOf(0)
    override fun observeCompletedWorkoutCountInRange(startTimestamp: Long, endTimestampExclusive: Long): Flow<Int> = flowOf(0)
    override suspend fun getRecentCompletedSessions(limit: Int): List<WorkoutSession> = emptyList()
    override suspend fun getRecentSessions(limit: Int): List<WorkoutSession> = emptyList()
    override suspend fun getRecommendationRecords(sessionIds: List<String>): List<wallcrawl.elopenmike.com.core.model.RecommendationRecord> = emptyList()

    override suspend fun startWorkoutFromGenerated(
        generated: GeneratedWorkout,
        displayName: String,
        displayRationale: String,
        userProfile: UserProfile,
        recommendation: RecommendationSnapshot?
    ): WorkoutSession = error("Not used")

    override suspend fun startWorkoutFromTemplate(
        template: WorkoutTemplate,
        userProfile: UserProfile
    ): WorkoutSession = error("Not used")

    override suspend fun logSetCompletion(setId: String, performance: SetPerformanceInput) {
        check(!rejectWrites)
        SetOutcomeRules.requireValidOutcome(performance)
        val current = session.value ?: return
        session.value = current.copy(
            exercises = current.exercises.map { exercise ->
                exercise.copy(
                    sets = exercise.sets.map { set ->
                        if (set.id != setId) {
                            set
                        } else {
                            set.copy(
                                completedReps = performance.reps,
                                completedWeight = performance.weight,
                                rpe = performance.rpe,
                                rir = performance.rir,
                                feltManageable = performance.feltManageable,
                                completedAtTimestamp = performance.completedAtTimestamp,
                                stoppedAtTimestamp = performance.stoppedAtTimestamp,
                                stopReason = performance.stopReason,
                                isCompleted = performance.isCompleted
                            )
                        }
                    }
                )
            }
        )
    }

    override suspend fun completeWorkout(
        sessionId: String,
        actualDurationMinutes: Int
    ): WorkoutSummary {
        completeCalls += 1
        val completed = requireNotNull(session.value).copy(
            status = SessionStatus.COMPLETED,
            completedAtTimestamp = 5_000L,
            actualDurationMinutes = actualDurationMinutes
        )
        session.value = completed
        return summary(completed)
    }

    override suspend fun getWorkoutSummary(sessionId: String): WorkoutSummary? =
        session.value?.takeIf { it.status == SessionStatus.COMPLETED }?.let(::summary)

    override suspend fun cancelWorkout(sessionId: String) {
        cancelCalls += 1
    }

    private fun summary(current: WorkoutSession) = WorkoutSummary(
        sessionId = current.id,
        workoutName = current.name,
        durationMinutes = current.actualDurationMinutes,
        totalSetsCompleted = current.completedSetsCount,
        totalVolume = current.totalVolume,
        unit = current.weightUnit,
        completedAtTimestamp = current.completedAtTimestamp ?: current.startedAtTimestamp
    )
}
