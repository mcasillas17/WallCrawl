package wallcrawl.elopenmike.com.core.ui

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.exercise.localization.ExerciseLocalizationParser
import wallcrawl.elopenmike.com.core.exercise.localization.ExerciseLocalizationStore
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
import wallcrawl.elopenmike.com.core.ui.localization.ExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.localization.generatedWorkoutRationale
import wallcrawl.elopenmike.com.core.ui.localization.generatedWorkoutTitle
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme
import wallcrawl.elopenmike.com.feature.today.TodayContent
import wallcrawl.elopenmike.com.feature.today.TodayUiState

/**
 * A prioritised muscle nothing can train is told to the user, in their language.
 *
 * The planner produces a truthfully labelled alternative session rather than a Push day
 * with no pushing in it. That is only honest if the reader is also told why the emphasis
 * they asked for is missing, so this renders the real explanation composable with the real
 * string resources and the real catalog vocabulary overlay in both shipped languages.
 */
@RunWith(AndroidJUnit4::class)
class GeneratedWorkoutFocusNoticeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val english: Context = context.localizedTo(Locale.ENGLISH)
    private val spanish: Context = context.localizedTo(LATIN_AMERICAN_SPANISH)

    private val spec = WorkoutRationaleSpec.GoalFocus(
        goals = listOf(FitnessGoal.BUILD_MUSCLE),
        focusMuscles = listOf(StandardMuscles.UPPER_BACK)
    )

    @Test
    fun anUnavailablePriorityIsExplainedInEnglish() {
        composeRule.setContent {
            InLocale(Locale.ENGLISH) {
                Text(
                    generatedWorkoutRationale(
                        spec = spec,
                        unavailableFocusMuscles = listOf(StandardMuscles.CHEST)
                    )
                )
            }
        }

        composeRule
            .onNodeWithText(
                english.getString(R.string.generated_rationale_focus_unavailable, "Chest"),
                substring = true
            )
            .assertIsDisplayed()
    }

    @Test
    fun anUnavailablePriorityIsExplainedInSpanish() {
        val translated = readBundledOverlay(context).muscle(StandardMuscles.CHEST, "es")
        // Genuinely Spanish, not English falling through the resource lookup.
        assertThat(spanish.getString(R.string.generated_rationale_focus_unavailable, "x"))
            .isNotEqualTo(english.getString(R.string.generated_rationale_focus_unavailable, "x"))
        assertThat(translated).isEqualTo("Pecho")

        composeRule.setContent {
            InLocale(LATIN_AMERICAN_SPANISH) {
                Text(
                    generatedWorkoutRationale(
                        spec = spec,
                        unavailableFocusMuscles = listOf(StandardMuscles.CHEST)
                    )
                )
            }
        }

        composeRule
            .onNodeWithText(
                spanish.getString(R.string.generated_rationale_focus_unavailable, translated),
                substring = true
            )
            .assertIsDisplayed()
    }

    @Test
    fun theTodayCardShowsTheExplanationInEnglish() {
        assertTodayCardExplains(Locale.ENGLISH, english, "Chest")
    }

    @Test
    fun theTodayCardShowsTheExplanationInSpanish() {
        assertTodayCardExplains(
            locale = LATIN_AMERICAN_SPANISH,
            resources = spanish,
            muscle = checkNotNull(readBundledOverlay(context).muscle(StandardMuscles.CHEST, "es"))
        )
    }

    /**
     * The card the reader actually sees, not the composable in isolation.
     *
     * The explanation only counts as delivered if it survives the trip through
     * `TodayContent` and `SuggestedWorkoutCard`; asserting on `generatedWorkoutRationale`
     * alone would pass even if no screen rendered it.
     */
    private fun assertTodayCardExplains(locale: Locale, resources: Context, muscle: String) {
        val workout = suggestedWorkout(unavailableFocusMuscles = listOf(StandardMuscles.CHEST))
        composeRule.setContent {
            InLocale(locale) {
                TodayContent(
                    state = TodayUiState.Success(
                        userProfile = UserProfile(),
                        suggestedWorkout = workout
                    ),
                    workoutName = generatedWorkoutTitle(workout.title),
                    workoutRationale = generatedWorkoutRationale(
                        spec = workout.rationale,
                        unavailableFocusMuscles = workout.unavailableFocusMuscles
                    ),
                    onStartWorkout = {},
                    onResumeWorkout = {},
                    onRegenerate = {},
                    onOpenTemplates = {}
                )
            }
        }

        composeRule
            .onNodeWithText(
                resources.getString(R.string.generated_rationale_focus_unavailable, muscle),
                substring = true
            )
            .assertIsDisplayed()
    }

    private fun suggestedWorkout(unavailableFocusMuscles: List<String>) = GeneratedWorkout(
        title = WorkoutTitleSpec(
            split = WorkoutSplit.UPPER_BODY,
            emphasis = WorkoutEmphasis.HYPERTROPHY
        ),
        focusMuscles = listOf(StandardMuscles.UPPER_BACK),
        estimatedDurationMinutes = 30,
        exercises = listOf(
            PlannedExercise(
                exerciseId = "band-pull-apart",
                prescription = ExercisePrescription(
                    exerciseType = ExerciseType.BODYWEIGHT_REPS,
                    targetSets = 3,
                    repRange = RepRange(12, 20),
                    restSeconds = 75
                )
            )
        ),
        rationale = spec,
        unavailableFocusMuscles = unavailableFocusMuscles
    )

    @Test
    fun theOrdinaryCaseAddsNothing() {
        composeRule.setContent {
            InLocale(Locale.ENGLISH) { Text(generatedWorkoutRationale(spec = spec)) }
        }

        composeRule
            .onNodeWithText(
                english.getString(R.string.generated_rationale_focus_unavailable, "Chest"),
                substring = true
            )
            .assertDoesNotExist()
    }

    @Composable
    private fun InLocale(locale: Locale, content: @Composable () -> Unit) {
        val localized = LocalContext.current.localizedTo(locale)
        CompositionLocalProvider(
            LocalContext provides localized,
            LocalConfiguration provides localized.resources.configuration,
            LocalExerciseVocabulary provides ExerciseVocabulary(
                localization = readBundledOverlay(localized),
                languageTag = locale.toLanguageTag()
            )
        ) {
            WallCrawlTheme { content() }
        }
    }

    private fun Context.localizedTo(locale: Locale): Context {
        val configuration = Configuration(resources.configuration)
        configuration.setLocales(LocaleList(locale))
        return createConfigurationContext(configuration)
    }

    private fun readBundledOverlay(from: Context) =
        from.assets.open(ExerciseLocalizationStore.LOCALIZATION_ASSET_PATH)
            .bufferedReader()
            .use(ExerciseLocalizationParser()::parse)

    private companion object {
        val LATIN_AMERICAN_SPANISH: Locale = Locale.forLanguageTag("es-MX")
    }
}
