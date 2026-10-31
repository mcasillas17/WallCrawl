package wallcrawl.elopenmike.com.feature.history

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.graphics.ColorUtils
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.database.repository.WorkoutHistoryDetail
import wallcrawl.elopenmike.com.core.database.repository.HistoricalExercisePerformance
import wallcrawl.elopenmike.com.core.database.repository.HistoricalSessionHeader
import wallcrawl.elopenmike.com.core.database.repository.HistoricalSourceKey
import wallcrawl.elopenmike.com.core.model.*
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme
import wallcrawl.elopenmike.com.core.ui.theme.LightBackground
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting

@RunWith(AndroidJUnit4::class)
class WorkoutHistoryScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val spanish = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
        setLocales(LocaleList(Locale.forLanguageTag("es-419")))
    })

    @Test fun spanishAssistanceProgressionNeverClaimsAssistanceIncreased() {
        val exercise = WorkoutExercise("assisted-instance", "recorded", "assisted-pull-up", 0,
            ExercisePrescription(ExerciseType.ASSISTED_BODYWEIGHT, 1, RepRange(8, 12),
                targetAssistanceWeight = 20.0))
        val record = RecommendationRecord(
            sessionId = "recorded", validatorVersion = "WHOLE_PROGRAM_V2",
            durationEstimatorVersion = "DURATION_ESTIMATOR_V1", outcome = "VALID",
            reviewedPathEnabled = true, catalogVersion = null, reviewPolicyVersion = 2,
            trainingPolicyVersion = null, ledgerPolicyVersion = null, programStatePolicyVersion = null,
            adaptationState = null, weekStartEpochDay = null, timeZoneId = null, profileRevision = 0,
            contextIdentity = "recorded-context",
            reasonCodes = ProgressionReasonCode.encode(listOf(ProgressionProvenance(
                "assisted-pull-up", ProgressionReason.ADVANCED, ProgressionAxis.ASSISTANCE,
                listOf("prior-a", "prior-b"), "a".repeat(64)
            ))),
            doseAccounting = emptyList(), recordedAtEpochMillis = 1_000
        )
        val stored = detail().copy(
            session = detail().session.copy(exercises = listOf(exercise)),
            recommendation = record
        )
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides spanish,
                LocalConfiguration provides spanish.resources.configuration,
                LocalDensity provides Density(LocalDensity.current.density, 1.8f)
            ) {
                WallCrawlTheme(themePreference = ThemePreference.LIGHT) {
                    Box(Modifier.width(320.dp)) {
                        WorkoutHistoryDetailScreen(
                            WorkoutHistoryDetailUiState.Loaded(stored, decodeHistoryReasons(record)), {}, {}
                        )
                    }
                }
            }
        }
        val explanation = "Se registró un avance en asistencia. No se guardó un par fijo de prescripciones anterior y posterior."
        scrollTo(explanation)
        compose.onNodeWithText(explanation).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(explanation).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertThat(layouts.single().hasVisualOverflow).isFalse()
    }

    @Test fun spanishLightLargeTextPreservesCompleteMeasurementsAndReadOnlyControls() =
        verifyLargeText(ThemePreference.LIGHT)

    @Test fun spanishDarkLargeTextPreservesCompleteMeasurementsAndReadOnlyControls() =
        verifyLargeText(ThemePreference.DARK)

    @Test fun lightThemeHeadingAndFieldTextHaveReadableContrast() {
        compose.setContent {
            WallCrawlTheme(themePreference = ThemePreference.LIGHT) {
                WorkoutHistoryDetailScreen(WorkoutHistoryDetailUiState.Loaded(detail(), null), {}, {})
            }
        }
        listOf("Original historical text", context.getString(R.string.history_started)).forEach { text ->
            scrollTo(text)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertThat(layouts).isNotEmpty()
            assertThat(ColorUtils.calculateContrast(layouts.first().layoutInput.style.color.toArgb(),
                LightBackground.toArgb())).isAtLeast(4.5)
        }
    }

    private fun verifyLargeText(theme: ThemePreference) {
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides spanish,
                LocalConfiguration provides spanish.resources.configuration,
                LocalDensity provides Density(LocalDensity.current.density, 1.8f)
            ) {
                WallCrawlTheme(themePreference = theme) {
                    Box(Modifier.width(320.dp)) {
                        WorkoutHistoryDetailScreen(WorkoutHistoryDetailUiState.Loaded(detail(), null), {}, {})
                    }
                }
            }
        }
        compose.onNodeWithText("Original historical text").assertIsDisplayed()
        val fullMeasurement = spanish.getString(R.string.prescription_meters,
            LocaleFormatting.formatMeasurement(123456.79, Locale.forLanguageTag("es-419")))
        scrollTo(fullMeasurement)
        compose.onNodeWithText(fullMeasurement, substring = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            val layouts = mutableListOf<TextLayoutResult>()
            action(layouts)
            assertThat(layouts).isNotEmpty()
            layouts.forEach { layout ->
                assertWithMessage("text=${layout.layoutInput.text}, size=${layout.size}, " +
                    "constraints=${layout.layoutInput.constraints}, lines=${layout.lineCount}, " +
                    "widthOverflow=${layout.didOverflowWidth}, heightOverflow=${layout.didOverflowHeight}")
                    .that(layout.hasVisualOverflow).isFalse()
            }
        }
        scrollTo(spanish.getString(R.string.history_technical))
        compose.onNodeWithText(spanish.getString(R.string.history_technical))
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                spanish.getString(R.string.progress_details_collapsed)))
            .performClick()
        compose.onNodeWithText(spanish.getString(R.string.history_technical))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,
                spanish.getString(R.string.progress_details_expanded)))
        compose.onAllNodes(hasText("Editar") or hasText("Eliminar") or hasText("Repetir")).assertCountEquals(0)
    }

    @Test fun missingUnsupportedAndErrorHaveBackAndErrorRetryWorks() {
        var retries = 0
        var backs = 0
        val state = mutableStateOf<WorkoutHistoryDetailUiState>(WorkoutHistoryDetailUiState.Missing)
        compose.setContent {
            WallCrawlTheme {
                WorkoutHistoryDetailScreen(state.value, { retries++ }, { backs++ })
            }
        }
        compose.onNodeWithText(context.getString(R.string.history_missing)).assertIsDisplayed()
        compose.runOnIdle { state.value = WorkoutHistoryDetailUiState.Unsupported(detail().session.copy(status = SessionStatus.CANCELLED)) }
        compose.onNodeWithText(context.getString(R.string.history_unsupported)).assertIsDisplayed()
        compose.runOnIdle { state.value = WorkoutHistoryDetailUiState.ReadError }
        compose.onNodeWithText(context.getString(R.string.history_retry)).performClick()
        compose.onNodeWithText(context.getString(R.string.action_back)).performClick()
        assertThat(retries).isEqualTo(1)
        assertThat(backs).isEqualTo(1)
    }

    @Test fun previousAndCitedMeasurementsKeepTheirOwnUnitsAndMissingCitationStaysObvious() {
        val viewedExercise = WorkoutExercise("instance", "recorded", "squat", 0,
            ExercisePrescription(ExerciseType.WEIGHT_REPS, 1, RepRange(8, 12), targetWeight = 40.0),
            sets = listOf(WorkoutSet(workoutExerciseId = "instance", setNumber = 1, targetReps = 10,
                targetWeight = 40.0, completedReps = 9, completedWeight = 42.0, isCompleted = true)))
        val viewed = detail().session.copy(weightUnit = WeightUnit.KG, exercises = listOf(viewedExercise))
        fun prior(id: String, load: Double): WorkoutSession {
            val ex = viewedExercise.copy(id = "$id-instance", sessionId = id, sets = listOf(
                WorkoutSet(workoutExerciseId = "$id-instance", setNumber = 1, targetReps = 8, targetWeight = load - 1,
                    completedReps = 8, completedWeight = load, isCompleted = true)))
            return viewed.copy(id = id, name = id, weightUnit = WeightUnit.LBS, startedAtTimestamp = 100,
                completedAtTimestamp = 900, exercises = listOf(ex))
        }
        val previous = prior("ordinary", 80.0)
        val cited = prior("cited", 95.0)
        fun performance(session: WorkoutSession) = HistoricalExercisePerformance(
            HistoricalSessionHeader(session.id, session.name, session.startedAtTimestamp,
                requireNotNull(session.completedAtTimestamp), session.weightUnit),
            session.exercises.single()
        )
        val reasons = HistoryReasons("VALID", emptyList(), listOf(
            ProgressionProvenance("squat", ProgressionReason.ADVANCED, ProgressionAxis.LOAD,
                listOf("cited", "missing"), "a".repeat(64))
        ), null, emptyList(), emptyList())
        compose.setContent {
            WallCrawlTheme {
                WorkoutHistoryDetailScreen(WorkoutHistoryDetailUiState.Loaded(
                    detail().copy(session = viewed,
                        previousPerformances = mapOf("instance" to performance(previous)),
                        progressionSources = mapOf(HistoricalSourceKey("cited", "instance") to performance(cited))), reasons), {}, {})
            }
        }
        listOf("80 ${WeightUnit.LBS.symbol}", "42 ${WeightUnit.KG.symbol}", "95 ${WeightUnit.LBS.symbol}").forEach {
            scrollTo(it)
            compose.onNodeWithText(it).assertExists()
        }
        scrollTo(context.getString(R.string.history_source_unavailable))
        compose.onNodeWithText(context.getString(R.string.history_source_unavailable)).assertExists()
    }

    @Test fun listUsesExplicitPagingButtonsAndPassesOnlyRecordedId() {
        var opened: String? = null
        var older = 0
        compose.setContent {
            WallCrawlTheme {
                WorkoutHistoryListScreen(
                    WorkoutHistoryListUiState.Loaded(0, listOf(WorkoutHistoryEntry(
                        detail().session.id, detail().session.name,
                        detail().session.completedAtTimestamp, detail().session.actualDurationMinutes
                    )), true),
                    onOpenSession = { opened = it }, onBack = {}, onRetry = {},
                    onOlder = { older++ }, onNewer = {}
                )
            }
        }
        compose.onNodeWithText("Original historical text").assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertThat(opened).isEqualTo("recorded")
        compose.onNodeWithTag("history-list").performScrollToNode(hasText("Older workouts"))
        compose.onNodeWithText("Older workouts").assertHasClickAction().performClick()
        assertThat(older).isEqualTo(1)
    }

    @Test fun unknownReasonIdentitiesAreDisclosedNotPresentedAsExplanationProse() {
        val record = RecommendationRecord(
            sessionId = "recorded", validatorVersion = "WHOLE_PROGRAM_V99",
            durationEstimatorVersion = "DURATION_ESTIMATOR_V99", outcome = "VALID",
            reviewedPathEnabled = false, catalogVersion = null, reviewPolicyVersion = 0,
            trainingPolicyVersion = null, ledgerPolicyVersion = null, programStatePolicyVersion = null,
            adaptationState = null, weekStartEpochDay = null, timeZoneId = null, profileRevision = 0,
            contextIdentity = "opaque-context", reasonCodes = listOf("FUTURE_REASON:opaque"),
            doseAccounting = emptyList(), recordedAtEpochMillis = 1_000
        )
        compose.setContent {
            WallCrawlTheme {
                WorkoutHistoryDetailScreen(WorkoutHistoryDetailUiState.Loaded(
                    detail().copy(recommendation = record), decodeHistoryReasons(record)), {}, {})
            }
        }
        scrollTo("Some recorded explanation details are unsupported. Original identities are available in technical details.")
        compose.onAllNodesWithText("FUTURE_REASON:opaque", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("WHOLE_PROGRAM_V99", substring = true).assertCountEquals(0)
        scrollTo(context.getString(R.string.history_technical))
        compose.onNodeWithText(context.getString(R.string.history_technical)).performClick()
        scrollTo("FUTURE_REASON:opaque")
        compose.onAllNodesWithText("FUTURE_REASON:opaque", substring = true).assertAny(hasText("FUTURE_REASON:opaque", substring = true))
    }

    @Test fun missingDistanceTimeMeasurementsNeverRenderAnEmptyMeasurementCard() {
        val original = detail()
        val exercise = original.session.exercises.single()
        val missing = exercise.sets.single().copy(
            targetDistanceMeters = null, targetDurationSeconds = null,
            completedDistanceMeters = null, completedDurationSeconds = null, isCompleted = false
        )
        compose.setContent {
            WallCrawlTheme {
                WorkoutHistoryDetailScreen(WorkoutHistoryDetailUiState.Loaded(original.copy(
                    session = original.session.copy(exercises = listOf(exercise.copy(sets = listOf(missing))))
                ), null), {}, {})
            }
        }
        scrollTo(context.getString(R.string.history_planned))
        compose.onNode(hasText(context.getString(R.string.effort_not_recorded)) and
            hasAnySibling(hasText(context.getString(R.string.history_planned))),
            useUnmergedTree = true).assertExists()
        scrollTo(context.getString(R.string.history_performed))
        compose.onNode(hasText(context.getString(R.string.effort_not_recorded)) and
            hasAnySibling(hasText(context.getString(R.string.history_performed))),
            useUnmergedTree = true).assertExists()
    }

    private fun scrollTo(text: String) {
        compose.onNodeWithTag("history-detail").performScrollToNode(hasText(text, substring = true))
    }

    private fun detail(): WorkoutHistoryDetail {
        val session = WorkoutSession(
            id = "recorded", name = "Original historical text", status = SessionStatus.COMPLETED,
            startedAtTimestamp = 1_000, completedAtTimestamp = 2_000,
            exercises = listOf(WorkoutExercise(
                id = "instance", sessionId = "recorded", exerciseId = "stored-run-id", orderIndex = 0,
                prescription = ExercisePrescription(ExerciseType.DISTANCE_DURATION, 1,
                    targetDistanceMeters = 123456.78, targetDurationSeconds = 3600),
                sets = listOf(WorkoutSet(
                    id = "stored-set", workoutExerciseId = "instance", setNumber = 1,
                    exerciseType = ExerciseType.DISTANCE_DURATION, targetDistanceMeters = 123456.78,
                    targetDurationSeconds = 3600, completedDistanceMeters = 123456.79,
                    completedDurationSeconds = 3601, isCompleted = true, feltManageable = false
                ))
            ))
        )
        return WorkoutHistoryDetail(session, WorkoutSummary("recorded", session.name, 1, 1, 0.0),
            null, emptyMap(), emptyMap())
    }
}
