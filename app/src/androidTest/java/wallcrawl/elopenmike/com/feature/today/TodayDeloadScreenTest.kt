package wallcrawl.elopenmike.com.feature.today

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import java.util.Locale
import org.junit.Rule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.ai.DeloadOfferPolicy
import wallcrawl.elopenmike.com.core.ai.ProgressionEngine
import wallcrawl.elopenmike.com.core.database.WallCrawlDatabase
import wallcrawl.elopenmike.com.core.database.repository.DeloadRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineDeloadRepository
import wallcrawl.elopenmike.com.core.database.repository.toUserProfileEntity
import wallcrawl.elopenmike.com.core.model.*
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

@RunWith(AndroidJUnit4::class)
class TodayDeloadScreenTest {
    @get:Rule val compose = createComposeRule()
    private var database: WallCrawlDatabase? = null
    private val mountKey = mutableIntStateOf(0)
    @After fun closeDatabase() { database?.close() }

    @Test fun lightThemeOfferAndPreviewHaveRenderedTextContrast() {
        val reference = ExercisePrescription(ExerciseType.WEIGHT_REPS, 2, RepRange(8, 12),
            targetWeight = 40.0, restSeconds = 90, effortTarget = EffortTarget(2, 4))
        val decision = ProgressionDecision("overhead-press", ProgressionReason.ADVANCED, ProgressionAxis.LOAD,
            reference, reference.copy(targetWeight = 42.5), listOf("first", "second"), "e".repeat(64))
        val offer = DeloadOffer("contrast", DeloadSource.EXPLICIT_REQUEST, DeloadOfferPolicy.VERSION)
        render(Locale.ENGLISH, choice = DeloadChoice(offer, DeloadChoiceStatus.OFFERED, 1),
            decisions = listOf(decision), profile = UserProfile(onboardingCompleted = true, preferredUnit = WeightUnit.KG),
            theme = ThemePreference.LIGHT)
        listOf(
            "One workout with fewer sets", "You requested fewer sets.",
            "Your next ONE automatic workout uses the current reference working targets with one fewer work set per exercise, minimum one. It keeps reference load, assistance, repetition, duration, effort, and rest targets. Progression is paused, including any suggested increase.",
            "If you accept", "Overhead Press", "1 × 8–12", "40 kg", "Rest: 90 s", "Target effort: 2–4 RIR",
            "Accept for one workout", "Decline", "Dismiss", "Request fewer sets"
        ).forEach(::assertRenderedTextContrast)
    }

    @Test fun lightThemeDeclinedAndAcceptedCardsHaveRenderedTextContrast() {
        val profile = UserProfile(onboardingCompleted = true)
        val repository = durableRepository(profile)
        render(Locale.ENGLISH, repository = repository, profile = profile, theme = ThemePreference.LIGHT)
        compose.onNodeWithText("Request fewer sets").performScrollTo().performClick()
        waitForStatus(repository, DeloadChoiceStatus.OFFERED)
        compose.onNodeWithText("Decline").performScrollTo().performClick()
        waitForStatus(repository, DeloadChoiceStatus.DECLINED)
        assertRenderedTextContrast("One workout with fewer sets")
        assertRenderedTextContrast("Request fewer sets")
        compose.onNodeWithText("Request fewer sets").performClick()
        waitForStatus(repository, DeloadChoiceStatus.OFFERED)
        compose.onNodeWithText("Accept for one workout").performScrollTo().performClick()
        waitForStatus(repository, DeloadChoiceStatus.ACCEPTED)
        compose.onNodeWithText("Request fewer sets").assertDoesNotExist()
        listOf(
            "Fewer sets accepted for one workout", "You requested fewer sets.",
            "This choice stays pending until you start an automatic workout. A manual routine does not use it. You can cancel before starting.",
            "Cancel before starting"
        ).forEach(::assertRenderedTextContrast)
    }

    @Test fun lightThemeLargeSpanishDeclinedCardHasRenderedTextContrast() {
        val offer = DeloadOffer("declined", DeloadSource.EXPLICIT_REQUEST, DeloadOfferPolicy.VERSION)
        render(Locale.forLanguageTag("es-MX"), fontScale = 1.8f,
            choice = DeloadChoice(offer, DeloadChoiceStatus.DECLINED, 1), theme = ThemePreference.LIGHT)
        assertRenderedTextContrast("Un entrenamiento con menos series")
        assertRenderedTextContrast("Solicitar menos series")
    }

    /** Capture the text itself, not a merged button whose border could hide washed-out glyphs. */
    private fun assertRenderedTextContrast(text: String) {
        compose.onNodeWithTag("today-list").performScrollToNode(hasText(text))
        val label = compose.onAllNodesWithText(text, useUnmergedTree = true)[0]
            .performScrollTo().assertIsDisplayed()
        val pixels = label.captureToImage().toPixelMap()
        val background = pixels[0, 0].luminance()
        var contrast = 1.0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val foreground = pixels[x, y].luminance()
                contrast = maxOf(contrast,
                    (maxOf(background, foreground) + 0.05) / (minOf(background, foreground) + 0.05))
            }
        }
        assertTrue("$text rendered contrast $contrast must be at least 4.5:1", contrast >= 4.5)
    }

    @Test fun explicitRequestHasAccessibleTouchTargetInEnglish() {
        render(Locale.ENGLISH)
        compose.onNodeWithText("Request fewer sets").performScrollTo().assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
    }

    @Test fun explicitRequestHasAccessibleTouchTargetInLargeSpanish() {
        render(Locale.forLanguageTag("es-MX"), fontScale = 2f)
        compose.onNodeWithTag("today-list").performScrollToNode(hasText("Solicitar menos series"))
        compose.onNodeWithText("Solicitar menos series").performScrollTo().assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
    }

    @Test fun largeSpanishWeeklySummaryStacksWithoutNarrowingToOneWord() {
        render(Locale.forLanguageTag("es-MX"), fontScale = 1.8f,
            maxWidth = 320.dp,
            profile = UserProfile(onboardingCompleted = true, daysPerWeek = 4))
        val progress = compose.onNodeWithText("0 de 4 entrenamientos completados esta semana")
        val remaining = compose.onNodeWithText("Faltan 4 para la meta semanal")
        progress.assertIsDisplayed().assertWidthIsAtLeast(200.dp)
        remaining.assertIsDisplayed()
        val progressBounds = progress.fetchSemanticsNode().boundsInRoot
        val remainingBounds = remaining.fetchSemanticsNode().boundsInRoot
        val maximumHeightPx = 100f *
            InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        val layouts = mutableListOf<TextLayoutResult>()
        progress.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.size)
        assertTrue("Weekly summary must not clip or ellipsize", !layouts.single().hasVisualOverflow)
        assertTrue(
            "Weekly summary height ${progressBounds.height} px must remain below $maximumHeightPx px " +
                "(100 dp); ${layouts.single().lineCount} lines at ${layouts.single().layoutInput.style.lineHeight}",
            progressBounds.height <= maximumHeightPx
        )
        assertTrue("Remaining-goal label must remain below 100 dp", remainingBounds.height <= maximumHeightPx)
        assertTrue(
            "Weekly labels must stack rather than compete for horizontal space",
            progressBounds.bottom <= remainingBounds.top
        )
        compose.onNodeWithText("Un entrenamiento con menos series").assertIsDisplayed()
    }

    @Test fun offerExplainsEffectBeforeAcceptAndExposesDistinctDeclineAndDismiss() {
        val offer = DeloadOffer("explicit", DeloadSource.EXPLICIT_REQUEST, DeloadOfferPolicy.VERSION)
        render(Locale.ENGLISH, choice = DeloadChoice(offer, DeloadChoiceStatus.OFFERED, 1))
        compose.onNodeWithText("You requested fewer sets.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Your next ONE automatic workout uses the current reference working targets with one fewer work set per exercise, minimum one. It keeps reference load, assistance, repetition, duration, effort, and rest targets. Progression is paused, including any suggested increase.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Accept for one workout").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Decline").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Dismiss").performScrollTo().assertIsDisplayed()
    }

    @Test fun activeWorkoutHidesDecisionControlsAndKeepsResume() {
        render(Locale.ENGLISH, active = WorkoutSession(
            id = "active", name = "Existing workout", status = SessionStatus.IN_PROGRESS
        ))
        compose.onNodeWithText("Request fewer sets").assertDoesNotExist()
        compose.onNodeWithText("Resume").performScrollTo().assertIsDisplayed()
    }

    @Test fun actualRecommendationShowsTypedProgressionNotice() {
        val prescription = ExercisePrescription(ExerciseType.BODYWEIGHT_REPS, 2, RepRange(8, 12))
        val decision = ProgressionDecision("push-up", ProgressionReason.ADVANCED, ProgressionAxis.REP_RANGE,
            prescription, prescription.copy(repRange = RepRange(9, 13)), listOf("first", "second"), "a".repeat(64))
        render(Locale.ENGLISH, decisions = listOf(decision))
        compose.onNodeWithText("Progression: repetitions 8–12 → 9–13. Other targets stay unchanged.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test fun acceptingAndCancellingRealControlsRestoresTheDurableChoice() {
        val profile = UserProfile(onboardingCompleted = true)
        val repository = durableRepository(profile)
        render(Locale.ENGLISH, repository = repository, profile = profile)
        compose.onNodeWithText("Request fewer sets").performScrollTo().performClick()
        waitForStatus(repository, DeloadChoiceStatus.OFFERED)
        compose.onNodeWithTag("today-list").performScrollToNode(hasText("Accept for one workout"))
        compose.onNodeWithText("Accept for one workout").performScrollTo().performClick()
        waitForStatus(repository, DeloadChoiceStatus.ACCEPTED)
        compose.runOnIdle { mountKey.intValue++ }
        compose.onNodeWithTag("today-list").performScrollToNode(hasText("Cancel before starting"))
        compose.onNodeWithText("Fewer sets accepted for one workout").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Accept for one workout").assertDoesNotExist()
        compose.onNodeWithText("Cancel before starting").performScrollTo().performClick()
        waitForStatus(repository, DeloadChoiceStatus.CANCELLED)
    }

    @Test fun decliningReturningOfferRestoresOrdinaryChoiceInLargeSpanish() {
        val profile = UserProfile(onboardingCompleted = true, returningAfterBreakWeeks = 4)
        val repository = durableRepository(profile)
        render(Locale.forLanguageTag("es-MX"), fontScale = 2f, repository = repository, profile = profile)
        compose.onNodeWithTag("today-list").performScrollToNode(hasText("Rechazar"))
        compose.onNodeWithText("Rechazar").performScrollTo().performClick()
        waitForStatus(repository, DeloadChoiceStatus.DECLINED)
        compose.runOnIdle { mountKey.intValue++ }
        compose.onNodeWithTag("today-list").performScrollToNode(hasText("Solicitar menos series"))
        compose.onNodeWithText("Solicitar menos series").assertIsDisplayed()
        compose.onNodeWithText("Aceptar para un entrenamiento").assertDoesNotExist()
        assertEquals(DeloadOfferPolicy.returnKey(profile), runBlocking { repository.get().lastHandledReturnKey })
    }

    @Test fun savingDisablesDecisionChangingControls() {
        val offer = DeloadOffer("explicit", DeloadSource.EXPLICIT_REQUEST, DeloadOfferPolicy.VERSION)
        render(Locale.ENGLISH, choice = DeloadChoice(offer, DeloadChoiceStatus.OFFERED, 1), saving = true)
        compose.onNodeWithText("Accept for one workout").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Decline").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Dismiss").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Request fewer sets").performScrollTo().assertIsNotEnabled()
    }

    @Test fun acceptedHoldAndCancelRenderInLargeSpanish() {
        val reference = ExercisePrescription(ExerciseType.BODYWEIGHT_REPS, 2, RepRange(8, 12))
        val decision = ProgressionDecision("push-up", ProgressionReason.HOLD_ACCEPTED_DELOAD, null,
            reference, reference.copy(targetSets = 1), emptyList(), "c".repeat(64))
        val offer = DeloadOffer("explicit", DeloadSource.EXPLICIT_REQUEST, DeloadOfferPolicy.VERSION)
        render(Locale.forLanguageTag("es-MX"), fontScale = 2f,
            choice = DeloadChoice(offer, DeloadChoiceStatus.ACCEPTED, 1), decisions = listOf(decision))
        compose.onNodeWithTag("today-list").performScrollToNode(hasText("Cancelar antes de empezar"))
        compose.onNodeWithText("Cancelar antes de empezar").performScrollTo().assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        val notice = "Progresión en pausa: se aplica tu elección aceptada para un entrenamiento."
        compose.onNodeWithTag("today-list").performScrollToNode(hasText(notice))
        compose.onNodeWithText(notice).performScrollTo().assertIsDisplayed()
    }

    @Test fun pendingUnitEditKeepsRecommendationAndDeloadPreviewInTheirPublishedUnit() {
        val reference = ExercisePrescription(ExerciseType.WEIGHT_REPS, 2, RepRange(8, 12), targetWeight = 40.0)
        val decision = ProgressionDecision("overhead-press", ProgressionReason.ADVANCED, ProgressionAxis.LOAD,
            reference, reference.copy(targetWeight = 40.0 + convertWeight(2.5, WeightUnit.KG, WeightUnit.LBS)),
            listOf("first", "second"), "b".repeat(64))
        val offer = DeloadOffer("pending", DeloadSource.EXPLICIT_REQUEST, DeloadOfferPolicy.VERSION)
        render(Locale.ENGLISH, decisions = listOf(decision), prescriptionUnit = WeightUnit.LBS,
            profile = UserProfile(onboardingCompleted = true, preferredUnit = WeightUnit.KG),
            choice = DeloadChoice(offer, DeloadChoiceStatus.OFFERED, 1))
        compose.onNodeWithTag("today-list").performScrollToNode(hasText("40 lb"))
        compose.onNodeWithText("40 lb").performScrollTo().assertIsDisplayed()
        val notice = "Progression: load 40 lb → 45.51 lb. Other targets stay unchanged."
        compose.onNodeWithTag("today-list").performScrollToNode(hasText(notice))
        compose.onNodeWithText(notice).performScrollTo().assertIsDisplayed()
    }

    @Test fun offerPreviewsHeldReferenceLoadNotTheSuggestedIncrease() {
        val reference = ExercisePrescription(ExerciseType.WEIGHT_REPS, 2, RepRange(8, 12),
            targetWeight = 40.0, restSeconds = 90, effortTarget = EffortTarget(2, 4))
        val sessions = listOf(1_000L, 4_000L).mapIndexed { index, start ->
            val id = "completed-$index"
            val instance = "$id-exercise"
            WorkoutSession(
                id = id, name = "Completed work", startedAtTimestamp = start,
                completedAtTimestamp = start + 1_000, status = SessionStatus.COMPLETED,
                weightUnit = WeightUnit.KG,
                exercises = listOf(WorkoutExercise(
                    id = instance, sessionId = id, exerciseId = "overhead-press", orderIndex = 0,
                    prescription = reference,
                    sets = (1..2).map { number -> WorkoutSet(
                        id = "$id-set-$number", workoutExerciseId = instance, setNumber = number,
                        exerciseType = ExerciseType.WEIGHT_REPS, targetReps = 12, completedReps = 12,
                        targetWeight = 40.0, completedWeight = 40.0, isCompleted = true,
                        feltManageable = true, rir = 3, completedAtTimestamp = start + number * 100
                    ) }
                ))
            )
        }
        val decision = ProgressionEngine().evaluate(
            "overhead-press", reference, sessions, WeightUnit.KG, 10_000, "d".repeat(64))
        assertEquals(ProgressionReason.ADVANCED, decision.reason)
        assertEquals(42.5, decision.prescription.targetWeight)
        val offer = DeloadOffer("explicit", DeloadSource.EXPLICIT_REQUEST, DeloadOfferPolicy.VERSION)
        render(Locale.ENGLISH, choice = DeloadChoice(offer, DeloadChoiceStatus.OFFERED, 1),
            decisions = listOf(decision), profile = UserProfile(onboardingCompleted = true, preferredUnit = WeightUnit.KG))
        compose.onNodeWithTag("today-list").performScrollToNode(hasText("If you accept"))
        compose.onNodeWithText("1 × 8–12").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("40 kg").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Rest: 90 s").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Target effort: 2–4 RIR").performScrollTo().assertIsDisplayed()
        val ordinaryNotice = "Progression: load 40 kg → 42.5 kg. Other targets stay unchanged."
        compose.onNodeWithTag("today-list").performScrollToNode(hasText(ordinaryNotice))
        compose.onNodeWithText(ordinaryNotice).performScrollTo().assertIsDisplayed()
    }

    private fun durableRepository(profile: UserProfile): DeloadRepository {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, WallCrawlDatabase::class.java).build()
        database = db
        runBlocking { db.userProfileDao().insertOrUpdate(profile.toUserProfileEntity()) }
        return OfflineDeloadRepository(db.deloadPreferencesDao(), Mutex())
    }

    private fun waitForStatus(repository: DeloadRepository, status: DeloadChoiceStatus) {
        compose.waitUntil(5_000) { runBlocking { repository.get().choice?.status == status } }
        compose.waitForIdle()
    }

    private fun render(
        locale: Locale,
        fontScale: Float = 1f,
        choice: DeloadChoice? = null,
        active: WorkoutSession? = null,
        decisions: List<ProgressionDecision> = emptyList(),
        repository: DeloadRepository? = null,
        profile: UserProfile = UserProfile(onboardingCompleted = true),
        prescriptionUnit: WeightUnit = profile.preferredUnit,
        saving: Boolean = false,
        theme: ThemePreference = ThemePreference.SYSTEM,
        maxWidth: Dp = Dp.Infinity
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(locale))
            this.fontScale = fontScale
        }
        val localized = context.createConfigurationContext(configuration)
        val preferences = DeloadPreferences(revision = if (choice == null) 0 else 1, choice = choice)
        val prescription = decisions.firstOrNull()?.prescription
            ?: ExercisePrescription(ExerciseType.BODYWEIGHT_REPS, 2, RepRange(8, 12))
        val workout = GeneratedWorkout(
            title = WorkoutTitleSpec(WorkoutSplit.PUSH, WorkoutEmphasis.HYPERTROPHY),
            focusMuscles = listOf("Chest"),
            estimatedDurationMinutes = 20,
            exercises = listOf(PlannedExercise(decisions.firstOrNull()?.exerciseId ?: "push-up", prescription)),
            rationale = WorkoutRationaleSpec.GoalFocus(emptyList(), listOf("Chest")),
            progressionDecisions = decisions
        )
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, fontScale)
            ) {
                WallCrawlTheme(themePreference = theme) {
                    key(mountKey.intValue) {
                        val flow = remember { repository?.observe() ?: flowOf(preferences) }
                        val stored by flow.collectAsState(initial = preferences)
                        val scope = rememberCoroutineScope()
                        Box(Modifier.widthIn(max = maxWidth)) {
                            TodayContent(
                                state = TodayUiState.Success(profile, workout, activeSession = active,
                                    prescriptionUnit = prescriptionUnit,
                                    deload = TodayDeloadState(profile.revision, stored,
                                        DeloadOfferPolicy.offer(profile, stored), DeloadOfferPolicy.accepted(stored), isSaving = saving)),
                                workoutName = "Workout",
                                onStartWorkout = {}, onResumeWorkout = {}, onRegenerate = {}, onOpenTemplates = {},
                                onDeloadAction = { action ->
                                    scope.launch {
                                        val offerId = when (action) {
                                            DeloadAction.REQUEST -> null
                                            DeloadAction.CANCEL -> DeloadOfferPolicy.accepted(stored)?.offer?.id
                                            else -> DeloadOfferPolicy.offer(profile, stored)?.id
                                        }
                                        repository?.decide(action, profile.revision, stored.revision, offerId)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
