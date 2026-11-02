package wallcrawl.elopenmike.com.feature.conditions

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.conditions.ConditionGuidanceResult
import wallcrawl.elopenmike.com.core.conditions.ConditionRestrictions
import wallcrawl.elopenmike.com.core.conditions.ConditionGuidance
import wallcrawl.elopenmike.com.core.conditions.ConditionRecord
import wallcrawl.elopenmike.com.core.conditions.MovementDemand
import wallcrawl.elopenmike.com.core.conditions.RoutineEntry
import wallcrawl.elopenmike.com.core.conditions.restrictionsFor
import wallcrawl.elopenmike.com.core.model.AiReviewProvenance
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WorkoutEmphasis
import wallcrawl.elopenmike.com.core.model.WorkoutRationaleSpec
import wallcrawl.elopenmike.com.core.model.WorkoutSplit
import wallcrawl.elopenmike.com.core.model.WorkoutTitleSpec
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme
import wallcrawl.elopenmike.com.feature.today.TodayContent
import wallcrawl.elopenmike.com.feature.today.TodayUiState

/**
 * The condition picker and the Today card, rendered for real in both languages. They scroll
 * with hasScrollAction() and performScrollToNode, so they hold at 320x640 dp too.
 */
@RunWith(AndroidJUnit4::class)
class ConditionPresentationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val english = context.localizedTo(Locale.ENGLISH)
    private val spanish = context.localizedTo(SPANISH)

    // --- picker ----------------------------------------------------------------------

    @Test
    fun addingAConditionShowsItsRedFlagsAndNotesAndRequiresAcknowledgement() {
        var saved: Set<String> = emptySet()
        composeRule.setContent {
            InLocale(Locale.ENGLISH) { PickerHost(onSaved = { saved = it }) }
        }

        scrollTo(hasTextTag("condition-chip-$ID"))
        composeRule.onNodeWithTag("condition-chip-$ID").performClick()

        scrollTo(hasText(RED_FLAG_EN, substring = true))
        composeRule.onNodeWithText(RED_FLAG_EN, substring = true).assertIsDisplayed()
        scrollTo(hasText(NOTE_EN, substring = true))
        composeRule.onNodeWithText(NOTE_EN, substring = true).assertIsDisplayed()

        scrollTo(hasTextTag("condition-confirm"))
        composeRule.onNodeWithTag("condition-confirm").assertIsNotEnabled()
        assertThat(saved).isEmpty()

        scrollTo(hasTextTag("condition-ack"))
        composeRule.onNodeWithTag("condition-ack").performClick()
        scrollTo(hasTextTag("condition-confirm"))
        composeRule.onNodeWithTag("condition-confirm").assertIsEnabled().performClick()

        assertThat(saved).containsExactly(ID)
        // It now appears as a saved condition, by name, with a way to remove it.
        scrollTo(hasTextTag("condition-remove-$ID"))
        composeRule.onNodeWithTag("condition-remove-$ID").assertIsDisplayed()
    }

    @Test
    fun theRedFlagsAndNotesAppearInTheCurrentLanguage() {
        composeRule.setContent {
            InLocale(SPANISH) { PickerHost(onSaved = {}) }
        }
        scrollTo(hasTextTag("condition-chip-$ID"))
        composeRule.onNodeWithTag("condition-chip-$ID").performClick()

        scrollTo(hasText(RED_FLAG_ES, substring = true))
        composeRule.onNodeWithText(RED_FLAG_ES, substring = true).assertIsDisplayed()
        scrollTo(hasText(NOTE_ES, substring = true))
        composeRule.onNodeWithText(NOTE_ES, substring = true).assertIsDisplayed()
        assertThat(composeRule.onAllNodesWithText(RED_FLAG_EN, substring = true).fetchSemanticsNodes())
            .isEmpty()
    }

    @Test
    fun anUnavailableOverlaySaysSoAndKeepsExistingConditionsRemovable() {
        var removed: String? = null
        composeRule.setContent {
            InLocale(Locale.ENGLISH) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ConditionPicker(
                        guidance = ConditionGuidanceResult.Unavailable,
                        selected = setOf(ID, "from_a_newer_build"),
                        pending = null,
                        onSelect = {}, onAcknowledge = {}, onConfirm = {}, onCancel = {},
                        onRemove = { removed = it }
                    )
                }
            }
        }
        scrollTo(hasTextTag("condition-unavailable"))
        composeRule.onNodeWithText(english.getString(R.string.condition_unavailable)).assertIsDisplayed()
        // Nothing can be added.
        assertThat(composeRule.onAllNodesWithTagPrefix("condition-chip-")).isEmpty()
        // The unknown id shows a neutral label and can still be removed.
        scrollTo(hasText(english.getString(R.string.condition_name_unknown)))
        composeRule.onNodeWithTag("condition-remove-from_a_newer_build").performClick()
        assertThat(removed).isEqualTo("from_a_newer_build")
    }

    // --- Today -----------------------------------------------------------------------

    @Test
    fun todayNamesTheConditionForTheMobilityBlock() {
        renderToday(Locale.ENGLISH)
        scrollTo(hasText(english.getString(R.string.mobility_today_heading)))
        composeRule.onNodeWithText(english.getString(R.string.mobility_today_heading)).assertIsDisplayed()
        composeRule.onNodeWithText(
            english.getString(R.string.mobility_today_for, english.getString(R.string.condition_name_piriformis_syndrome))
        ).assertIsDisplayed()
    }

    @Test
    fun todayShowsHoldTimeRestAndTheMobilityCue() {
        renderToday(Locale.ENGLISH)
        val dose = "3 × 8 · ${english.getString(R.string.mobility_hold, "5")} · ${english.getString(R.string.mobility_rest, "45")}"
        scrollTo(hasText(dose, substring = true))
        composeRule.onNodeWithText(dose, substring = true).assertIsDisplayed()
        val cue = english.getString(R.string.condition_cue, "Stop if symptoms spread.")
        scrollTo(hasText(cue))
        composeRule.onNodeWithText(cue).assertIsDisplayed()
    }

    @Test
    fun modifyExerciseShowsItsCueInToday() {
        renderToday(Locale.ENGLISH)
        val cue = english.getString(R.string.condition_cue, "Keep the load light.")
        scrollTo(hasText(cue))
        composeRule.onNodeWithText(cue).assertIsDisplayed()
    }

    @Test
    fun todayShowsTheCueAndNameInSpanish() {
        renderToday(SPANISH)
        val name = spanish.getString(R.string.condition_name_piriformis_syndrome)
        val heading = spanish.getString(R.string.mobility_today_heading)
        scrollTo(hasText(heading))
        composeRule.onNodeWithText(spanish.getString(R.string.mobility_today_for, name)).assertIsDisplayed()
        val cue = spanish.getString(R.string.condition_cue, "Usa poca carga.")
        scrollTo(hasText(cue))
        composeRule.onNodeWithText(cue).assertIsDisplayed()
    }

    // --- harness ---------------------------------------------------------------------

    private fun scrollTo(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(matcher)
    }

    private fun hasTextTag(tag: String) =
        androidx.compose.ui.test.hasTestTag(tag)

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagPrefix(
        prefix: String
    ) = onAllNodes(
        androidx.compose.ui.test.SemanticsMatcher("tag starts with $prefix") { node ->
            node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)
                ?.startsWith(prefix) == true
        }
    ).fetchSemanticsNodes()

    @Composable
    private fun PickerHost(onSaved: (Set<String>) -> Unit) {
        var selected by remember { mutableStateOf(emptySet<String>()) }
        var pending by remember { mutableStateOf<PendingCondition?>(null) }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            ConditionPicker(
                guidance = guidance,
                selected = selected,
                pending = pending,
                onSelect = { pending = PendingCondition(it) },
                onAcknowledge = { pending = pending?.copy(acknowledged = it) },
                onConfirm = {
                    pending?.takeIf { it.acknowledged }?.let {
                        selected = selected + it.id
                        pending = null
                        onSaved(selected)
                    }
                },
                onCancel = { pending = null },
                onRemove = { selected = selected - it }
            )
        }
    }

    private fun renderToday(locale: Locale) {
        val profile = UserProfile(reportedConditions = setOf(ID))
        val restrictions: ConditionRestrictions = restrictionsFor(profile, guidance)
        val mobility = PlannedExercise(
            exerciseId = "bird-dog",
            prescription = ExercisePrescription(
                exerciseType = ExerciseType.BODYWEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(8, 8),
                restSeconds = 45,
                holdSeconds = 5
            )
        )
        val main = PlannedExercise("heavy-lift", 3, 8, 10)
        val workout = GeneratedWorkout(
            title = WorkoutTitleSpec(split = WorkoutSplit.UPPER_BODY, emphasis = WorkoutEmphasis.HYPERTROPHY),
            focusMuscles = listOf(StandardMuscles.CHEST),
            estimatedDurationMinutes = 40,
            exercises = listOf(main),
            rationale = WorkoutRationaleSpec.GoalFocus(listOf(FitnessGoal.BUILD_MUSCLE), listOf(StandardMuscles.CHEST)),
            mobilityBlock = listOf(mobility)
        )
        composeRule.setContent {
            InLocale(locale) {
                TodayContent(
                    state = TodayUiState.Success(userProfile = profile, suggestedWorkout = workout),
                    workoutName = "Workout",
                    onStartWorkout = {},
                    onResumeWorkout = {},
                    onRegenerate = {},
                    onOpenTemplates = {},
                    restrictions = restrictions
                )
            }
        }
    }

    @Composable
    private fun InLocale(locale: Locale, content: @Composable () -> Unit) {
        val localized = LocalContext.current.localizedTo(locale)
        CompositionLocalProvider(
            LocalContext provides localized,
            LocalConfiguration provides localized.resources.configuration
        ) {
            WallCrawlTheme { content() }
        }
    }

    private fun Context.localizedTo(locale: Locale): Context {
        val configuration = Configuration(resources.configuration)
        configuration.setLocales(LocaleList(locale))
        return createConfigurationContext(configuration)
    }

    private val guidance = ConditionGuidanceResult.Loaded(
        ConditionGuidance(
            version = "test",
            exerciseDemands = mapOf(
                "heavy-lift" to setOf(MovementDemand.HEAVY_EXTERNAL_LOAD),
                "bird-dog" to emptySet()
            ),
            conditions = mapOf(
                ID to ConditionRecord(
                    id = ID,
                    avoidDemands = emptySet(),
                    modifyDemands = setOf(MovementDemand.HEAVY_EXTERNAL_LOAD),
                    routine = listOf(RoutineEntry("bird-dog", 3, 8, null, 5, 45, true)),
                    exerciseCues = mapOf(
                        "bird-dog" to mapOf("en" to "Stop if symptoms spread.", "es" to "Detente si los síntomas se extienden.")
                    ),
                    notes = mapOf("en" to listOf(NOTE_EN), "es" to listOf(NOTE_ES)),
                    redFlags = mapOf("en" to listOf(RED_FLAG_EN), "es" to listOf(RED_FLAG_ES)),
                    evidenceGrade = "C",
                    sources = emptyList(),
                    provenance = AiReviewProvenance(
                        reviewerModelId = "test", reviewedAtEpochMillis = 1L, reviewedContentId = "x",
                        reviewedContentSha256 = "0", sourceReferences = emptyList(),
                        decisionRationale = "t", limitations = "t", schemaVersion = 1, policyVersion = 1
                    )
                )
            ),
            jointLabelDemands = emptyMap(),
            demandCues = mapOf(
                MovementDemand.HEAVY_EXTERNAL_LOAD to mapOf("en" to "Keep the load light.", "es" to "Usa poca carga.")
            )
        )
    )

    private companion object {
        const val ID = "piriformis_syndrome"
        const val RED_FLAG_EN = "New numbness around your genitals"
        const val RED_FLAG_ES = "Adormecimiento nuevo alrededor de los genitales"
        const val NOTE_EN = "Ease off if symptoms start."
        const val NOTE_ES = "Baja la intensidad si empiezan los síntomas."
        val SPANISH: Locale = Locale.forLanguageTag("es-MX")
    }
}
