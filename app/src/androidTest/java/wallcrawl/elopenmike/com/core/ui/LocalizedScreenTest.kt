package wallcrawl.elopenmike.com.core.ui

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import wallcrawl.elopenmike.com.core.locale.AppLanguage
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.SetValuesDraft
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.ui.components.GymFloorSetRow
import wallcrawl.elopenmike.com.core.ui.components.LanguageChip
import wallcrawl.elopenmike.com.core.ui.components.MovementCapabilityQuestion
import wallcrawl.elopenmike.com.core.ui.localization.ExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme
import wallcrawl.elopenmike.com.feature.profile.AppPreferencesCard

/**
 * Renders real controls under a Spanish configuration and checks that Spanish is what comes
 * out — chrome, accessibility announcements, and catalog vocabulary alike.
 *
 * The locale is overridden through the composition rather than by recreating the activity.
 * Applying the app language for real is AndroidX's job and is covered by `AppLanguageTest`
 * plus the manifest declaration; what these need to prove is that the resources and the
 * bundled overlay actually resolve to Spanish when they are asked to.
 */
@RunWith(AndroidJUnit4::class)
class LocalizedScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val spanish: Context = context.localizedTo(LATIN_AMERICAN_SPANISH)
    private val decimalCommaSpanish: Context = context.localizedTo(EUROPEAN_SPANISH)

    // Pinned rather than taken from the device: the "this really is Spanish" guards below
    // compare the two languages, and on a Spanish-configured device the ambient context
    // would resolve to values-es and make them compare Spanish with itself.
    private val english: Context = context.localizedTo(Locale.ENGLISH)

    @Test
    fun theBundledOverlayLoadsAndTranslatesTheCatalogVocabulary() {
        val localization = readBundledOverlay(context)

        assertThat(localization.languages).containsExactly(AppLanguage.SPANISH.languageTag)
        assertThat(localization.exerciseName("barbell-back-squat", "es-MX"))
            .isEqualTo("Sentadilla con barra")
        assertThat(localization.muscle("Chest", "es")).isEqualTo("Pecho")
        assertThat(localization.equipment("Barbell", "es")).isEqualTo("Barra")
    }

    @Test
    fun theLanguageChipRendersInSpanish() {
        composeRule.setContent {
            InSpanish { LanguageChip(current = AppLanguage.SPANISH, onSelect = {}) }
        }

        // Genuinely different text, not English falling through: if values-es had not
        // resolved, this announcement would still read "Language: Spanish".
        assertThat(spanish.getString(R.string.language_option_accessibility, "x"))
            .isNotEqualTo(english.getString(R.string.language_option_accessibility, "x"))
        composeRule
            .onNodeWithText(spanish.getString(R.string.language_option_spanish_short))
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(
                spanish.getString(
                    R.string.language_option_accessibility,
                    spanish.getString(R.string.language_option_spanish)
                )
            )
            .assertIsDisplayed()
    }

    @Test
    fun profileAppPreferencesHoldsTheLanguageSettingInSpanishToo() {
        composeRule.setContent {
            InSpanish {
                AppPreferencesCard(currentTheme = ThemePreference.SYSTEM, onSelectTheme = {})
            }
        }

        // Both sides of a `spanish.getString` assertion would agree even if values-es never
        // resolved, so pin the difference first: this is what fails if Spanish stops
        // resolving and the card quietly renders English.
        assertThat(spanish.getString(R.string.language_setting_title))
            .isNotEqualTo(english.getString(R.string.language_setting_title))
        assertThat(spanish.getString(R.string.profile_app_preferences_heading))
            .isNotEqualTo(english.getString(R.string.profile_app_preferences_heading))

        composeRule
            .onNodeWithText(spanish.getString(R.string.profile_app_preferences_heading))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(spanish.getString(R.string.language_setting_title))
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(
                spanish.getString(
                    R.string.language_option_accessibility,
                    spanish.getString(R.string.language_option_system)
                )
            )
            .assertIsDisplayed()
    }

    @Test
    fun aMovementPreferenceQuestionRendersItsLabelsAndOptionsInSpanish() {
        composeRule.setContent {
            InSpanish {
                MovementCapabilityQuestion(
                    type = MovementCapabilityType.IMPACT,
                    selectedLevel = CapabilityLevel.COMFORTABLE,
                    onSelect = {}
                )
            }
        }

        composeRule
            .onNodeWithText(spanish.getString(R.string.movement_capability_impact_label))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(spanish.getString(R.string.capability_level_comfortable))
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(
                spanish.getString(
                    R.string.movement_capability_option_accessibility,
                    spanish.getString(R.string.movement_capability_impact_label),
                    spanish.getString(R.string.capability_level_avoid)
                )
            )
            .assertIsDisplayed()
    }

    @Test
    fun theSetLoggerRendersItsControlsAndAnnouncementsInSpanish() {
        composeRule.setContent {
            InSpanish {
                GymFloorSetRow(
                    set = weightSet(),
                    weightUnit = "kg",
                    previousSet = null,
                    onValuesChanged = {},
                    onCompletionChanged = { _, _ -> },
                    onSkipSet = {},
                    onRecordEffort = { _, _ -> },
                    onRecordFeltManageable = {}
                )
            }
        }

        composeRule.onNodeWithText(spanish.getString(R.string.set_complete)).assertIsDisplayed()
        composeRule.onNodeWithText(spanish.getString(R.string.set_skip_or_stop))
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(
                spanish.getString(R.string.set_complete_accessibility, "1")
            )
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription(
                spanish.getString(
                    R.string.set_field_decrease_accessibility,
                    spanish.getString(R.string.set_field_load),
                    "1"
                )
            )
            .assertIsDisplayed()
    }

    @Test
    fun aLoadFieldRendersAndReadsBackADecimalCommaWithoutChangingItsValue() {
        // The prescribed 42.5 kg must render as "42,5" under a decimal-comma locale and
        // come back as 42.5, never as 425.
        val recorded = mutableListOf<SetValuesDraft>()
        composeRule.setContent {
            InSpanish(EUROPEAN_SPANISH) {
                GymFloorSetRow(
                    set = weightSet(targetWeight = 42.5),
                    weightUnit = "kg",
                    previousSet = null,
                    onValuesChanged = { recorded += it },
                    onCompletionChanged = { values, _ -> recorded += values },
                    onSkipSet = {},
                    onRecordEffort = { _, _ -> },
                    onRecordFeltManageable = {}
                )
            }
        }

        composeRule.onNodeWithText("42,5").assertIsDisplayed()
        composeRule
            .onNodeWithText(decimalCommaSpanish.getString(R.string.set_complete))
            .performClick()

        assertThat(recorded).isNotEmpty()
        assertThat(recorded.last().weight).isEqualTo(42.5)
    }

    @Composable
    private fun InSpanish(
        locale: Locale = LATIN_AMERICAN_SPANISH,
        content: @Composable () -> Unit
    ) {
        val localized = LocalContext.current.localizedTo(locale)
        val vocabulary = ExerciseVocabulary(
            localization = readBundledOverlay(localized),
            languageTag = locale.toLanguageTag()
        )
        CompositionLocalProvider(
            LocalContext provides localized,
            LocalConfiguration provides localized.resources.configuration,
            LocalExerciseVocabulary provides vocabulary
        ) {
            WallCrawlTheme { content() }
        }
    }

    private fun readBundledOverlay(from: Context) =
        from.assets.open(ExerciseLocalizationStore.LOCALIZATION_ASSET_PATH)
            .bufferedReader()
            .use(ExerciseLocalizationParser()::parse)

    private fun Context.localizedTo(locale: Locale): Context {
        val configuration = Configuration(resources.configuration)
        configuration.setLocales(LocaleList(locale))
        return createConfigurationContext(configuration)
    }

    private fun weightSet(targetWeight: Double = 40.0) = WorkoutSet(
        id = "set-1",
        workoutExerciseId = "exercise-1",
        setNumber = 1,
        exerciseType = ExerciseType.WEIGHT_REPS,
        targetReps = 10,
        targetWeight = targetWeight
    )

    private companion object {
        val LATIN_AMERICAN_SPANISH: Locale = Locale.forLanguageTag("es-MX")
        val EUROPEAN_SPANISH: Locale = Locale.forLanguageTag("es-ES")
    }
}
