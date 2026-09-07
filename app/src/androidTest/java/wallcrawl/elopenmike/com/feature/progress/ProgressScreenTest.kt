package wallcrawl.elopenmike.com.feature.progress

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.exercise.localization.ExerciseLocalizationParser
import wallcrawl.elopenmike.com.core.exercise.localization.ExerciseLocalizationStore
import wallcrawl.elopenmike.com.core.model.LedgerOmissionReason
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.MuscleProgressStat
import wallcrawl.elopenmike.com.core.model.ProgressOverview
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.model.TrainingWeek
import wallcrawl.elopenmike.com.core.model.WeeklyDoseLedger
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.ui.localization.ExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme
import wallcrawl.elopenmike.com.core.ui.theme.LightSurfaceCard

/**
 * Drives the stateless Progress surface across languages, themes, real large text, and every
 * state that has to read differently: loaded activity, the production all-unattributed
 * ledger, an empty week, no local profile, and a query error with a working retry.
 *
 * The screen is rendered from a resolved [ProgressUiState] rather than a ViewModel and a
 * database, so each state is reachable directly. Locale, density, and catalog vocabulary are
 * overridden through the composition, the same way `LocalizedScreenTest` proves Spanish
 * resolves for real — including large text, which scales through [LocalDensity] and not the
 * configuration alone.
 */
@RunWith(AndroidJUnit4::class)
class ProgressScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val english: Context = context.localized(Locale.ENGLISH)
    private val spanish: Context = context.localized(LATIN_AMERICAN_SPANISH)

    @Test
    fun loadedProgressLabelsActivityDoseAndInvolvementSeparately() {
        render(success(), Locale.ENGLISH, ThemePreference.DARK)

        // Capture the first screen before any scrolling, so the shot is the real above-the-fold view.
        maybeCaptureScreenshot("progress-loaded-en-dark")

        composeRule.onNodeWithText(english.getString(R.string.progress_activity_heading))
            .assertIsDisplayed()
        scrollTo(english.getString(R.string.progress_dose_heading))
        composeRule.onNodeWithText(english.getString(R.string.progress_dose_heading))
            .assertIsDisplayed()
        scrollTo(english.getString(R.string.progress_involvement_heading))
        composeRule.onNodeWithText(english.getString(R.string.progress_involvement_heading))
            .assertIsDisplayed()
    }

    @Test
    fun loadedProgressRendersInSpanishIncludingCatalogVocabulary() {
        // Pin the difference first: if values-es never resolved, this would read English.
        assertThat(spanish.getString(R.string.progress_activity_heading))
            .isNotEqualTo(english.getString(R.string.progress_activity_heading))

        render(success(), LATIN_AMERICAN_SPANISH, ThemePreference.LIGHT)
        maybeCaptureScreenshot("progress-loaded-es-light")

        composeRule.onNodeWithText(spanish.getString(R.string.progress_activity_heading))
            .assertIsDisplayed()

        // Expand the reviewed-dose disclosure and confirm the credited muscle reads in Spanish
        // from the bundled overlay, not English falling through.
        scrollTo(spanish.getString(R.string.progress_dose_details))
        composeRule.onNodeWithText(spanish.getString(R.string.progress_dose_details)).performClick()
        val reviewedMuscle = hasText(SPANISH_CHEST) and
            hasAnyAncestor(hasTestTag(PROGRESS_REVIEWED_DOSE_TEST_TAG))
        composeRule.onNode(hasScrollAction()).performScrollToNode(reviewedMuscle)
        composeRule.onNode(reviewedMuscle).assertIsDisplayed()
    }

    @Test
    fun loadedProgressStaysReadableAtDoubleTextSize() {
        render(success(), Locale.ENGLISH, ThemePreference.LIGHT, fontScale = 2.0f)
        maybeCaptureScreenshot("progress-loaded-en-large-text")

        composeRule.onNodeWithText(english.getString(R.string.progress_activity_heading))
            .assertIsDisplayed()
        scrollTo(english.getString(R.string.progress_dose_heading))
        composeRule.onNodeWithText(english.getString(R.string.progress_dose_heading))
            .assertIsDisplayed()
    }

    @Test
    fun spanishDarkLargeTextRemainsReadable() {
        render(success(), LATIN_AMERICAN_SPANISH, ThemePreference.DARK, fontScale = 2.0f)
        maybeCaptureScreenshot("progress-loaded-es-dark-large-text")

        composeRule.onNodeWithText(spanish.getString(R.string.progress_activity_heading))
            .assertIsDisplayed()
        scrollTo(spanish.getString(R.string.progress_involvement_heading))
        composeRule.onNodeWithText(spanish.getString(R.string.progress_involvement_heading))
            .assertIsDisplayed()
    }

    @Test
    fun lightThemeDisclosureTextHasReadableContrast() {
        render(success(), LATIN_AMERICAN_SPANISH, ThemePreference.LIGHT)
        val label = spanish.getString(R.string.progress_activity_details)
        scrollTo(label)
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(label, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertThat(layouts).isNotEmpty()
        val contrast = ColorUtils.calculateContrast(
            layouts.first().layoutInput.style.color.toArgb(), LightSurfaceCard.toArgb()
        )
        assertThat(contrast).isAtLeast(4.5)
    }

    @Test
    fun largeTextWorkoutCountKeepsEnoughWidthForWords() {
        render(success(), LATIN_AMERICAN_SPANISH, ThemePreference.DARK, fontScale = 2.0f)
        val text = spanish.resources.getQuantityString(R.plurals.progress_workouts_this_week, 3, "3")
        scrollTo(text)
        val textWidth = composeRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.width
        val screenWidth = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.width
        assertThat(textWidth / screenWidth).isGreaterThan(0.55f)
    }

    @Test
    fun reviewedDoseComparisonUsesThePreviousReviewedLedger() {
        render(success(), Locale.ENGLISH, ThemePreference.DARK)
        val label = english.getString(R.string.progress_dose_details)
        scrollTo(label)
        composeRule.onNodeWithText(label).performClick()
        scrollTo("Last week: 4 reviewed work sets")
        composeRule.onNodeWithText("Last week: 4 reviewed work sets").assertIsDisplayed()
    }

    @Test fun emptyCurrentWeekKeepsPreviousReviewedDoseAccessible() {
        assertPriorDoseVisible(
            emptyOverview().copy(totalWorkoutsLogged = 1, currentStreakWeeks = 1),
            Locale.ENGLISH
        )
    }

    @Test fun spanishWarmupOnlyWeekKeepsPreviousReviewedDoseAccessible() {
        assertPriorDoseVisible(
            emptyOverview().copy(
                workoutsThisWeek = 1, totalWorkoutsLogged = 2, currentStreakWeeks = 2,
                completedSetsThisWeek = 2, warmupSetsThisWeek = 2
            ),
            LATIN_AMERICAN_SPANISH
        )
    }

    @Test fun previousOnlyInvolvementExplainsTheEmptyCurrentWeekAndKeepsComparison() {
        render(
            success(
                overview = emptyOverview().copy(
                    totalWorkoutsLogged = 1, currentStreakWeeks = 1,
                    legacyPrimaryActivity = listOf(MuscleProgressStat("Chest", 0, 4, -100))
                ),
                ledger = noneLedger()
            ),
            Locale.ENGLISH, ThemePreference.LIGHT
        )
        val message = english.getString(R.string.progress_involvement_empty)
        scrollTo(message)
        composeRule.onNodeWithText(message).assertIsDisplayed()
        val details = english.getString(R.string.progress_involvement_details)
        scrollTo(details)
        composeRule.onNodeWithText(details).performClick()
        val comparison = english.getString(R.string.progress_involvement_change, "-100")
        scrollTo(comparison)
        composeRule.onNodeWithText(comparison).assertIsDisplayed()
    }

    private fun assertPriorDoseVisible(overview: ProgressOverview, locale: Locale) {
        val localized = context.localized(locale)
        render(success(overview = overview, ledger = noneLedger()), locale, ThemePreference.LIGHT)
        val noWork = localized.getString(R.string.progress_dose_none)
        scrollTo(noWork)
        composeRule.onNodeWithText(noWork).assertIsDisplayed()
        val details = localized.getString(R.string.progress_dose_details)
        scrollTo(details)
        composeRule.onNodeWithText(details).performClick()
        val prior = localized.resources.getQuantityString(R.plurals.progress_dose_previous_credited, 4, "4")
        scrollTo(prior)
        composeRule.onNodeWithText(prior).assertIsDisplayed()
        scrollTo(localized.getString(R.string.progress_involvement_partial_note))
        composeRule.onNodeWithText(localized.getString(R.string.progress_involvement_partial_note))
            .assertIsDisplayed()
    }

    @Test fun englishLightNormalAppearance() =
        verifyAppearance(Locale.ENGLISH, ThemePreference.LIGHT, 1.0f, "en-light")

    @Test fun englishDarkLargeAppearance() =
        verifyAppearance(Locale.ENGLISH, ThemePreference.DARK, 2.0f, "en-dark-large")

    @Test fun spanishDarkNormalAppearance() =
        verifyAppearance(LATIN_AMERICAN_SPANISH, ThemePreference.DARK, 1.0f, "es-dark")

    @Test fun spanishLightLargeAppearance() =
        verifyAppearance(LATIN_AMERICAN_SPANISH, ThemePreference.LIGHT, 2.0f, "es-light-large")

    @Test fun productionOverviewEnglishDark() =
        verifyAppearance(Locale.ENGLISH, ThemePreference.DARK, 1.0f, "en-dark")

    @Test fun productionOverviewSpanishLight() =
        verifyAppearance(LATIN_AMERICAN_SPANISH, ThemePreference.LIGHT, 1.0f, "es-light")

    private fun verifyAppearance(locale: Locale, theme: ThemePreference, fontScale: Float, name: String) {
        val localized = context.localized(locale)
        render(
            success(ledger = productionLedger(), previous = productionLedger().copy(weekStartEpochDay = WEEK.startEpochDay - 7)),
            locale, theme, fontScale
        )
        maybeCaptureScreenshot("progress-activity-$name")
        val label = localized.getString(R.string.progress_dose_details)
        scrollTo(label)
        composeRule.onNodeWithText(label).assertHeightIsAtLeast(48.dp).performClick()
        scrollTo(localized.getString(R.string.progress_omission_not_approved))
        composeRule.onNodeWithText(localized.getString(R.string.progress_omission_not_approved))
            .assertIsDisplayed()
        maybeCaptureScreenshot("progress-accounting-$name")
    }

    @Test
    fun disclosureTogglesAreLargeEnoughAndOperable() {
        render(success(), Locale.ENGLISH, ThemePreference.DARK)

        val doseDetails = english.getString(R.string.progress_dose_details)
        scrollTo(doseDetails)
        composeRule.onNodeWithText(doseDetails)
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        scrollTo(english.getString(R.string.progress_dose_policy))
        composeRule.onNodeWithText(english.getString(R.string.progress_dose_policy))
            .assertIsDisplayed()
    }

    @Test
    fun productionAllUnattributedExplainsInsteadOfShowingZeroDose() {
        render(
            success(ledger = productionLedger(), previous = productionLedger().copy(weekStartEpochDay = WEEK.startEpochDay - 7)),
            Locale.ENGLISH, ThemePreference.DARK
        )

        scrollTo(english.getString(R.string.progress_dose_unattributed))
        composeRule.onNodeWithText(english.getString(R.string.progress_dose_unattributed))
            .assertIsDisplayed()

        // Expanding the section-specific reviewed-dose disclosure states the named policy and
        // the typed reason the work is not attributed.
        scrollTo(english.getString(R.string.progress_dose_details))
        composeRule.onNodeWithText(english.getString(R.string.progress_dose_details)).performClick()
        scrollTo(english.getString(R.string.progress_dose_policy))
        composeRule.onNodeWithText(english.getString(R.string.progress_dose_policy))
            .assertIsDisplayed()
        maybeCaptureScreenshot("progress-dose-details-en")
        scrollTo(english.getString(R.string.progress_omission_not_approved))
        composeRule.onNodeWithText(english.getString(R.string.progress_omission_not_approved))
            .assertIsDisplayed()
    }

    @Test
    fun emptyWeekShowsAnHonestNoActivityState() {
        render(
            success(
                overview = emptyOverview(), ledger = noneLedger(),
                previous = noneLedger().copy(weekStartEpochDay = WEEK.startEpochDay - 7)
            ),
            Locale.ENGLISH,
            ThemePreference.DARK
        )

        composeRule.onNodeWithText(english.getString(R.string.progress_activity_empty))
            .assertIsDisplayed()
    }

    @Test
    fun noProfileShowsAResourceBackedExplanation() {
        render(ProgressUiState.NoProfile, Locale.ENGLISH, ThemePreference.DARK)

        composeRule.onNodeWithText(english.getString(R.string.progress_no_profile))
            .assertIsDisplayed()
    }

    @Test
    fun errorStateOffersARetryThatCallsBack() {
        var retries = 0
        render(ProgressUiState.Error(), Locale.ENGLISH, ThemePreference.DARK, onRetry = { retries++ })

        composeRule.onNodeWithText(english.getString(R.string.progress_error)).assertIsDisplayed()
        composeRule.onNodeWithText(english.getString(R.string.action_try_again)).performClick()

        assertThat(retries).isEqualTo(1)
    }

    private fun scrollTo(textValue: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(textValue))
    }

    private fun render(
        state: ProgressUiState,
        locale: Locale,
        theme: ThemePreference,
        fontScale: Float = 1.0f,
        onRetry: () -> Unit = {}
    ) {
        composeRule.setContent {
            val localized = LocalContext.current.localized(locale, fontScale)
            val baseDensity = LocalDensity.current
            val vocabulary = ExerciseVocabulary(
                localization = readBundledOverlay(localized),
                languageTag = locale.toLanguageTag()
            )
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides localized.resources.configuration,
                // Real text scaling happens through density, not the configuration alone.
                LocalDensity provides Density(baseDensity.density, fontScale),
                LocalExerciseVocabulary provides vocabulary
            ) {
                WallCrawlTheme(themePreference = theme) {
                    ProgressScreen(uiState = state, onRetry = onRetry)
                }
            }
        }
    }

    /**
     * Writes a PNG of the current screen for later inspection, only when the run asks for it
     * via `-e captureProgressScreenshots true`, so CI does not pay for image capture. Storage
     * failures fail the test loudly rather than being swallowed.
     */
    private fun maybeCaptureScreenshot(name: String) {
        val requested = InstrumentationRegistry.getArguments()
            .getString("captureProgressScreenshots") == "true"
        if (!requested) return
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(context.getExternalFilesDir(null), "progress-screenshots")
        check(dir.isDirectory || dir.mkdirs()) { "Could not create screenshot directory: $dir" }
        File(dir, "$name.png").outputStream().use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "PNG encode failed for $name" }
        }
    }

    private fun readBundledOverlay(from: Context) =
        from.assets.open(ExerciseLocalizationStore.LOCALIZATION_ASSET_PATH)
            .bufferedReader()
            .use(ExerciseLocalizationParser()::parse)

    private fun Context.localized(locale: Locale, fontScale: Float = 1.0f): Context {
        val configuration = Configuration(resources.configuration)
        configuration.setLocales(LocaleList(locale))
        configuration.fontScale = fontScale
        return createConfigurationContext(configuration)
    }

    private fun success(
        overview: ProgressOverview = overview(),
        ledger: WeeklyDoseLedger = approvedLedger(),
        previous: WeeklyDoseLedger = previousLedger()
    ) = ProgressUiState.Success(
        overview = overview,
        preferredUnit = WeightUnit.LBS,
        week = WEEK,
        reviewedDose = ledger,
        previousReviewedDose = previous
    )

    /** 14 completed sets, 2 of them warm-ups, so 12 work sets reconcile with the ledger below. */
    private fun overview(): ProgressOverview = ProgressOverview(
        workoutsThisWeek = 3,
        weeklyGoal = 4,
        currentStreakWeeks = 3,
        totalWorkoutsLogged = 21,
        totalVolumeThisWeek = 8400.0,
        totalRepsThisWeek = 132,
        completedSetsThisWeek = 14,
        warmupSetsThisWeek = 2,
        legacyPrimaryActivity = listOf(
            MuscleProgressStat("Chest", setsThisWeek = 6, setsPreviousWeek = 4, percentageChange = 50),
            MuscleProgressStat("Back", setsThisWeek = 3, setsPreviousWeek = 0, percentageChange = null),
            MuscleProgressStat("Biceps", setsThisWeek = 0, setsPreviousWeek = 2, percentageChange = -100)
        )
    )

    private fun emptyOverview(): ProgressOverview = ProgressOverview(
        workoutsThisWeek = 0,
        weeklyGoal = 4,
        currentStreakWeeks = 0,
        totalWorkoutsLogged = 0,
        totalVolumeThisWeek = 0.0,
        totalRepsThisWeek = 0,
        completedSetsThisWeek = 0,
        warmupSetsThisWeek = 0,
        legacyPrimaryActivity = emptyList()
    )

    /** Credited 11 + omitted 1 == 12 work sets (14 completed − 2 warm-ups). */
    private fun approvedLedger() = WeeklyDoseLedger(
        policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
        weekStartEpochDay = WEEK.startEpochDay,
        timeZoneId = WEEK.zoneId.id,
        catalogVersion = "test",
        reviewPolicyVersion = 1,
        directPrimarySets = mapOf("Chest" to 8, "Back" to 3),
        secondaryInvolvement = mapOf("Triceps" to 8),
        unattributedWorkSets = mapOf(LedgerOmissionReason.UNKNOWN_EXERCISE to 1)
    )

    /** A distinct prior-week ledger, keyed to the previous week rather than the current one. */
    private fun previousLedger() = WeeklyDoseLedger(
        policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
        weekStartEpochDay = WEEK.startEpochDay - 7,
        timeZoneId = WEEK.zoneId.id,
        catalogVersion = "test",
        reviewPolicyVersion = 1,
        directPrimarySets = mapOf("Chest" to 4),
        secondaryInvolvement = emptyMap(),
        unattributedWorkSets = emptyMap()
    )

    private fun noneLedger() = WeeklyDoseLedger(
        policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
        weekStartEpochDay = WEEK.startEpochDay,
        timeZoneId = WEEK.zoneId.id,
        catalogVersion = "test",
        reviewPolicyVersion = 1,
        directPrimarySets = emptyMap(),
        secondaryInvolvement = emptyMap(),
        unattributedWorkSets = emptyMap()
    )

    /** The current bundled reality: 37 DRAFT / 0 APPROVED, so nothing is credited. */
    private fun productionLedger() = WeeklyDoseLedger(
        policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
        weekStartEpochDay = WEEK.startEpochDay,
        timeZoneId = WEEK.zoneId.id,
        catalogVersion = "test",
        reviewPolicyVersion = 1,
        directPrimarySets = emptyMap(),
        secondaryInvolvement = emptyMap(),
        unattributedWorkSets = mapOf(LedgerOmissionReason.METADATA_NOT_APPROVED to 12)
    )

    private companion object {
        val LATIN_AMERICAN_SPANISH: Locale = Locale.forLanguageTag("es-MX")
        const val SPANISH_CHEST = "Pecho"
        val WEEK: TrainingWeek = TrainingWeek.containing(
            Instant.parse("2026-09-02T12:00:00Z"),
            ZoneId.of("UTC")
        )
    }
}
