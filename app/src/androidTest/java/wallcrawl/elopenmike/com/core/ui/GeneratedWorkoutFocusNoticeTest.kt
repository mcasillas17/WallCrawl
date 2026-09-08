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
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.WorkoutRationaleSpec
import wallcrawl.elopenmike.com.core.ui.localization.ExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.localization.generatedWorkoutRationale
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

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
