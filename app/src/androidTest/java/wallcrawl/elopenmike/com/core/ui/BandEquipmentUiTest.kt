package wallcrawl.elopenmike.com.core.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.graphics.ColorUtils
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import java.io.File
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.core.database.WallCrawlDatabase
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveCodec
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveFixtures
import wallcrawl.elopenmike.com.core.database.repository.OfflineLocalDataBackupRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutTemplateRepository
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.exercise.workoutguide.WorkoutGuideCatalogParser
import wallcrawl.elopenmike.com.core.exercise.localization.ExerciseLocalizationParser
import wallcrawl.elopenmike.com.core.exercise.localization.ExerciseLocalizationStore
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.ui.localization.ExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.components.BandSetupControls
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme
import wallcrawl.elopenmike.com.core.ui.theme.LightSurfaceCard
import wallcrawl.elopenmike.com.core.ui.theme.GraphiteSurfaceElevated
import wallcrawl.elopenmike.com.feature.onboarding.OnboardingScreen
import wallcrawl.elopenmike.com.feature.onboarding.OnboardingStep
import wallcrawl.elopenmike.com.feature.onboarding.OnboardingViewModel
import wallcrawl.elopenmike.com.feature.profile.ProfileScreen
import wallcrawl.elopenmike.com.feature.profile.ProfileViewModel
import wallcrawl.elopenmike.com.feature.templates.TemplateEditorScreen
import wallcrawl.elopenmike.com.feature.templates.TemplateEditorViewModel

class BandEquipmentUiTest {
    @get:Rule
    val composeRule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: WallCrawlDatabase
    private lateinit var profiles: OfflineUserProfileRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, WallCrawlDatabase::class.java).build()
        profiles = OfflineUserProfileRepository(database.userProfileDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun onboarding_fullGymDoesNotConfirmSetupsAndEachControlHasAnExplanation() {
        val viewModel = OnboardingViewModel(profiles)
        viewModel.goToStep(OnboardingStep.EQUIPMENT)
        composeRule.setContent {
            InLanguage("en") { OnboardingScreen(viewModel, onCompleted = {}) }
        }
        composeRule.onNodeWithText("Full gym access").performClick()
        StandardEquipment.BAND_SETUPS.forEach { setup ->
            composeRule.onNodeWithText(setup).performScrollTo().assertIsNotSelected()
        }
        composeRule.onNodeWithText(StandardEquipment.BAND_ANCHOR_LOW)
            .performScrollTo().performClick().assertIsSelected()
        composeRule.onNodeWithText("Secured fixed attachment near floor level.")
            .assertIsDisplayed()
        assertThat(viewModel.uiState.value.equipment)
            .containsExactlyElementsIn(StandardEquipment.FULL_GYM + StandardEquipment.BAND_ANCHOR_LOW)
    }

    @Test
    fun profile_spanishSetupControlsPersistCanonicalKeysIndependently() {
        runBlocking { profiles.saveProfile(UserProfile(availableEquipment = StandardEquipment.FULL_GYM)) }
        val viewModel = ProfileViewModel(profiles)
        composeRule.setContent { InLanguage("es") { ProfileScreen(viewModel, onOpenCredits = {}) } }
        composeRule.waitUntil(10_000) {
            viewModel.uiState.value is wallcrawl.elopenmike.com.feature.profile.ProfileUiState.Success
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Anclaje de banda: bajo"))
        composeRule.onNodeWithText("Anclaje de banda: bajo")
            .performScrollTo().assertIsNotSelected().performClick()
        composeRule.waitUntil(10_000) {
            val state = viewModel.uiState.value as? wallcrawl.elopenmike.com.feature.profile.ProfileUiState.Success
            StandardEquipment.BAND_ANCHOR_LOW in state?.profile?.availableEquipment.orEmpty()
        }
        composeRule.onNodeWithText("Anclaje de banda: bajo").assertIsSelected()
        composeRule.onNodeWithText("Sujeción y apoyo para patada con banda")
            .performScrollTo().assertIsNotSelected()
        assertThat(runBlocking { profiles.getProfileOnce().availableEquipment })
            .containsExactlyElementsIn(StandardEquipment.FULL_GYM + StandardEquipment.BAND_ANCHOR_LOW)
    }

    @Test
    fun restoredSetupAliasesRemainInertAndExplicitConfirmationCanBeRevoked() {
        val aliases = listOf("band anchor - upper body", " Band Anchor - Upper Body ")
        val inventory = listOf(StandardEquipment.RESISTANCE_BAND) + aliases
        val archive = LocalDataArchiveFixtures.archive(snapshot = LocalDataArchiveFixtures.snapshot(
            profile = LocalDataArchiveFixtures.profile().copy(availableEquipment = inventory)
        ))
        val bytes = ByteArrayOutputStream().also { output ->
            LocalDataArchiveCodec.write(archive) { output }
        }.toByteArray()
        runBlocking {
            OfflineLocalDataBackupRepository(
                database.localDataBackupDao(), "test", 1L, { "test-commit" }
            ).restoreFrom(bytes.inputStream())
        }
        val facePull = context.assets.open("workout-guide/catalog.json").bufferedReader()
            .use(WorkoutGuideCatalogParser()::parse).exercises.single { it.id == "banded-face-pull" }
        val viewModel = ProfileViewModel(profiles)
        composeRule.setContent { InLanguage("en") { ProfileScreen(viewModel, onOpenCredits = {}) } }
        composeRule.waitUntil(10_000) {
            viewModel.uiState.value is wallcrawl.elopenmike.com.feature.profile.ProfileUiState.Success
        }
        val label = StandardEquipment.BAND_ANCHOR_UPPER_BODY
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(label))
        composeRule.onNodeWithText(label).assertIsNotSelected()
        assertThat(ExerciseFilter().filterCandidates(
            listOf(facePull), runBlocking { profiles.getProfileOnce() }
        )).isEmpty()

        composeRule.onNodeWithText(label).performClick()
        composeRule.waitUntil(10_000) {
            val state = viewModel.uiState.value as? wallcrawl.elopenmike.com.feature.profile.ProfileUiState.Success
            label in state?.profile?.availableEquipment.orEmpty()
        }
        composeRule.onNodeWithText(label).assertIsSelected()
        assertThat(ExerciseFilter().filterCandidates(
            listOf(facePull), runBlocking { profiles.getProfileOnce() }
        )).containsExactly(facePull)
        composeRule.onNodeWithText(label).performClick()
        composeRule.waitUntil(10_000) {
            val state = viewModel.uiState.value as? wallcrawl.elopenmike.com.feature.profile.ProfileUiState.Success
            label !in state?.profile?.availableEquipment.orEmpty()
        }
        composeRule.onNodeWithText(label).assertIsNotSelected()
        val saved = runBlocking { profiles.getProfileOnce() }
        assertThat(saved.availableEquipment).containsExactlyElementsIn(inventory).inOrder()
        assertThat(ExerciseFilter().filterCandidates(listOf(facePull), saved)).isEmpty()
    }

    @Test
    fun manualTemplate_missingSetupWarningNamesTheActualCombinationInEnglish() {
        showTemplate("en", "banded-kickback")
        composeRule.onNodeWithText(
            "Missing: (Band Anchor - Low AND Band Kickback Attachment and Support)"
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun manualTemplate_missingSetupWarningUsesSpanishVocabulary() {
        showTemplate("es", "banded-kickback")
        composeRule.onNodeWithText(
            "Falta: (Anclaje de banda: bajo Y Sujeción y apoyo para patada con banda)"
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun manualTemplate_unresolvedRowWarnsEvenWithEveryConfirmation() {
        showTemplate("en", "banded-row", StandardEquipment.ALL)
        composeRule.onNodeWithText(
            "Setup unresolved: the required attachment is not verified. You can keep this manual choice, but it is excluded from automatic workouts."
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun manualTemplate_alternativeEquipmentUsesTheSpanishOrSeparator() {
        showTemplate("es", "romanian-deadlift", listOf(StandardEquipment.BODYWEIGHT))
        composeRule.onNodeWithText("Falta: (Barra) O (Mancuerna)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun missingEquipmentWarning_hasReadableLightThemeContrast() =
        assertManualWarningContrast("banded-kickback", ThemePreference.LIGHT,
            "Missing: (Band Anchor - Low AND Band Kickback Attachment and Support)")

    @Test
    fun missingEquipmentWarning_hasReadableDarkThemeContrast() =
        assertManualWarningContrast("banded-kickback", ThemePreference.DARK,
            "Missing: (Band Anchor - Low AND Band Kickback Attachment and Support)")

    @Test
    fun unresolvedSetupWarning_hasReadableLightThemeContrast() =
        assertManualWarningContrast("banded-row", ThemePreference.LIGHT, UNRESOLVED_WARNING)

    @Test
    fun unresolvedSetupWarning_hasReadableDarkThemeContrast() =
        assertManualWarningContrast("banded-row", ThemePreference.DARK, UNRESOLVED_WARNING)

    private fun assertManualWarningContrast(exerciseId: String, theme: ThemePreference, text: String) {
        showTemplate("en", exerciseId, theme = theme)
        val node = composeRule.onNodeWithText(text, useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val background = if (theme == ThemePreference.LIGHT) LightSurfaceCard else GraphiteSurfaceElevated
        assertThat(ColorUtils.calculateContrast(
            layouts.single().layoutInput.style.color.toArgb(), background.toArgb()
        )).isAtLeast(4.5)
    }

    @Test
    fun bandSetupControls_showAllEnglishLabelsWithoutInferredSelections() = captureControls("en")

    @Test
    fun bandSetupControls_showAllSpanishLabelsWithoutInferredSelections() = captureControls("es")

    @Test
    fun bandSetupControls_keepHeadingAndSelectedAndUnselectedLabelsReadableInLightTheme() {
        var backgrounds = emptyMap<String, Color>()
        composeRule.setContent {
            InLanguage("en", ThemePreference.LIGHT) {
                backgrounds = mapOf(
                    "Band setups — confirm separately" to MaterialTheme.colorScheme.surfaceVariant,
                    StandardEquipment.BAND_ANCHOR_UPPER_BODY to MaterialTheme.colorScheme.secondaryContainer,
                    StandardEquipment.BAND_ANCHOR_OVERHEAD to MaterialTheme.colorScheme.surface
                )
                WallCrawlCard {
                    BandSetupControls(listOf(StandardEquipment.BAND_ANCHOR_UPPER_BODY), onToggle = {})
                }
            }
        }
        composeRule.waitForIdle()
        backgrounds.forEach { (text, background) ->
            val layouts = mutableListOf<TextLayoutResult>()
            composeRule.onNodeWithText(text, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertThat(layouts).isNotEmpty()
            assertThat(ColorUtils.calculateContrast(
                layouts.single().layoutInput.style.color.toArgb(), background.toArgb()
            )).isAtLeast(4.5)
        }
    }

    private fun captureControls(language: String) {
        val selected = listOf(StandardEquipment.BAND_ANCHOR_UPPER_BODY, StandardEquipment.BAND_ANCHOR_LOW)
        composeRule.setContent {
            InLanguage(language) {
                WallCrawlCard { BandSetupControls(selected, onToggle = {}) }
            }
        }
        val overlay = context.assets.open(ExerciseLocalizationStore.LOCALIZATION_ASSET_PATH)
            .bufferedReader().use(ExerciseLocalizationParser()::parse)
        val vocabulary = ExerciseVocabulary(overlay, language)
        StandardEquipment.BAND_SETUPS.forEach { setup ->
            val node = composeRule.onNodeWithText(vocabulary.equipment(setup)).assertIsDisplayed()
            if (setup in selected) node.assertIsSelected() else node.assertIsNotSelected()
        }
        if (language == "es") {
            composeRule.onNodeWithText("selecciona Banda elástica por separado.", substring = true)
                .assertIsDisplayed()
        }
        val directory = File(context.getExternalFilesDir(null), "band-anchor-screenshots")
        check(directory.isDirectory || directory.mkdirs())
        File(directory, "band-setups-$language.png").outputStream().use { output ->
            check(composeRule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private fun showTemplate(
        language: String,
        exerciseId: String,
        equipment: List<String> = listOf(StandardEquipment.RESISTANCE_BAND),
        theme: ThemePreference = ThemePreference.SYSTEM
    ) {
        runBlocking { profiles.saveProfile(UserProfile(availableEquipment = equipment)) }
        val exercise = InMemoryExerciseCatalog.SAMPLE_EXERCISES.firstOrNull { it.id == exerciseId }
            ?: InMemoryExerciseCatalog.SAMPLE_EXERCISES.first().copy(
            id = exerciseId, name = "Manual band exercise", programming = null,
            listedEquipment = listOf(StandardEquipment.RESISTANCE_BAND)
        )
        val catalog = InMemoryExerciseCatalog(listOf(exercise))
        val viewModel = TemplateEditorViewModel(
            null, OfflineWorkoutTemplateRepository(database.workoutTemplateDao(), catalog), profiles, catalog
        )
        composeRule.setContent {
            InLanguage(language, theme) { TemplateEditorScreen(viewModel, onBack = {}, onSaved = {}) }
        }
        composeRule.waitUntil(10_000) { !viewModel.uiState.value.isLoading }
        composeRule.runOnIdle { viewModel.addExercise(exercise) }
    }

    @Composable
    private fun InLanguage(
        language: String,
        theme: ThemePreference = ThemePreference.SYSTEM,
        content: @Composable () -> Unit
    ) {
        val localized: Context = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply {
                setLocales(LocaleList(Locale.forLanguageTag(language)))
            }
        )
        val overlay = localized.assets.open(ExerciseLocalizationStore.LOCALIZATION_ASSET_PATH)
            .bufferedReader().use(ExerciseLocalizationParser()::parse)
        CompositionLocalProvider(
            LocalContext provides localized,
            LocalConfiguration provides localized.resources.configuration,
            LocalExerciseVocabulary provides ExerciseVocabulary(overlay, language)
        ) {
            WallCrawlTheme(themePreference = theme) { content() }
        }

    }

    private companion object {
        const val UNRESOLVED_WARNING =
            "Setup unresolved: the required attachment is not verified. You can keep this manual choice, but it is excluded from automatic workouts."
    }
}
