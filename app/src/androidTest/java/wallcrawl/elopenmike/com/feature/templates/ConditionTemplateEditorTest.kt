package wallcrawl.elopenmike.com.feature.templates

import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.graphics.asAndroidBitmap
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.ai.*
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import kotlinx.coroutines.flow.first
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.*
import org.junit.Assert.*
import java.util.Locale
import java.time.ZoneId
import wallcrawl.elopenmike.com.core.database.*
import wallcrawl.elopenmike.com.core.database.repository.*
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.*
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

class ConditionTemplateEditorTest {
    @get:Rule val compose = createComposeRule()
    private val f = ConditionStartFixtures
    private val gate = Mutex()
    private var catalog = InMemoryExerciseCatalog(listOf(f.ordinary, f.supportive))
    private lateinit var db: WallCrawlDatabase
    private lateinit var profiles: OfflineUserProfileRepository
    private lateinit var templates: OfflineWorkoutTemplateRepository
    private lateinit var workouts: OfflineWorkoutRepository
    private lateinit var editor: TemplateEditorViewModel
    private var support = f.context().conditionSupport!!
    @Before fun setup() = runBlocking<Unit> {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WallCrawlDatabase::class.java).build()
        profiles = OfflineUserProfileRepository(db.userProfileDao(), gate)
        profiles.saveUserProfile(f.context().userProfile)
        repositories()
    }
    private fun repositories() {
        templates = OfflineWorkoutTemplateRepository(db.workoutTemplateDao(), catalog, gate)
        workouts = OfflineWorkoutRepository(db.workoutSessionDao(), db.workoutSetDao(), localDataWriteGate = gate,
            conditionSupport = { support }, exerciseCatalog = catalog, nowTimestamp = { f.now }, currentZone = { ZoneId.of("UTC") })
    }
    @After fun close() = db.close()

    @Test fun explicitSupportControlSavesExactManualPrescriptionAndStartsWithoutReceipt() = successfulEditor("en")
    @Test fun spanishLargeFontSupportControlEditsSavedTemplateThenStarts() = successfulEditor("es", editExisting = true)

    private fun successfulEditor(language: String, editExisting: Boolean = false) {
        val original = WorkoutTemplate(name = "Manual", weightUnit = WeightUnit.KG,
            exercises = listOf(PlannedExercise(f.ordinary.id, ExercisePrescription(ExerciseType.BODYWEIGHT_REPS, 2, repRange = RepRange(8, 10)))))
        if (editExisting) runBlocking { templates.saveTemplate(original) }
        show(language, 1.6f, original.id.takeIf { editExisting })
        compose.runOnIdle {
            if (!editExisting) {
                editor.updateName("Manual")
                editor.addExercise(f.ordinary)
            }
        }
        if (editExisting) assertEquals(original, runBlocking { templates.getTemplate(original.id) })
        val label = if (language == "es") "Añadir apoyo para las afecciones" else "Add condition support"
        compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        capture("editor-$language-large-before")
        compose.onNodeWithText(label).performClick()
        compose.waitUntil(10_000) { !editor.uiState.value.isCheckingSupport && editor.uiState.value.selectedExercises.any { it.purpose == PrescriptionPurpose.SUPPORTIVE } }
        if (editExisting) assertEquals(original, runBlocking { templates.getTemplate(original.id) })
        val template = save(language)
        val supportRow = template.exercises.single { it.exerciseId == f.supportive.id }
        assertEquals(f.dose, supportRow.prescription)
        val session = runBlocking { workouts.startWorkoutFromTemplate(template, profiles.getProfileOnce()) }
        assertEquals(f.dose, session.exercises.single { it.exerciseId == f.supportive.id }.prescription)
        assertEquals(17, session.exercises.single { it.exerciseId == f.supportive.id }.prescription.restSeconds)
        assertEquals((runBlocking { context() }.conditionPlanning as ConditionPlanningResult.Ready).context, session.conditionContext)
        assertNull(runBlocking { db.workoutSessionDao().getRecommendationRecord(session.id) })
    }

    @Test fun changedSupportAtTapRequiresReviewWithoutChangingTheDraft() {
        show("en")
        val before = editor.uiState.value.selectedExercises
        support = f.context(supportiveDose = f.dose.copy(targetDurationSeconds = 21)).conditionSupport!!
        adopt()
        assertEquals(before, editor.uiState.value.selectedExercises)
        assertEquals(R.string.editor_support_changed, editor.uiState.value.errorMessage)
        adopt()
        assertEquals("21", editor.uiState.value.selectedExercises.single().durationSeconds)
    }

    @Test fun withdrawnSupportAtTapDoesNotFabricateADose() {
        show("en")
        support = ConditionSupportCatalog("withdrawn", emptyMap())
        adopt()
        assertTrue(editor.uiState.value.selectedExercises.isEmpty())
        assertTrue(editor.uiState.value.conditionPlanning is ConditionPlanningResult.Blocked)
    }

    @Test fun changedOrWithdrawnSupportAfterSavingRefusesStartWithoutRewritingTemplate() {
        show("en")
        adopt()
        val template = save()
        support = f.context(supportiveDose = f.dose.copy(targetDurationSeconds = 21)).conditionSupport!!
        assertRefused(template)
        support = ConditionSupportCatalog("withdrawn", emptyMap())
        assertRefused(template)
        assertEquals(template, runBlocking { templates.getTemplate(template.id) })
    }

    @Test fun userEditedSupportDoseRemainsInvalidAtStart() {
        show("en")
        adopt()
        compose.runOnIdle {
            editor.editExercise(editor.uiState.value.selectedExercises.single().rowId)
            editor.updateDraftField(DraftField.DURATION, "21")
            editor.applyDraft()
        }
        val template = save()
        assertEquals(21, template.exercises.single().prescription.targetDurationSeconds)
        assertRefused(template)
    }

    @Test fun pendingReassessmentAtTapPreservesDraft() {
        show("en")
        runBlocking {
            val session = workouts.startWorkoutFromTemplate(WorkoutTemplate(name = "stop", weightUnit = WeightUnit.KG,
                exercises = listOf(PlannedExercise(f.supportive.id, f.dose))), profiles.getProfileOnce())
            workouts.logSetCompletion(session.exercises.single().sets.first().id,
                SetPerformanceInput(isCompleted = false, stoppedAtTimestamp = f.now, stopReason = SetStopReason.PAIN_STOP))
            workouts.cancelWorkout(session.id)
        }
        adopt()
        assertTrue(editor.uiState.value.selectedExercises.isEmpty())
        assertTrue(editor.uiState.value.conditionPlanning is ConditionPlanningResult.Blocked)
    }

    @Test fun unknownConditionContextCannotOfferSupport() {
        runBlocking { profiles.saveUserProfile(profiles.getProfileOnce().copy(activeConditions =
            listOf(SelectedCondition("unknown", 1, emptyMap())))) }
        show("en")
        assertTrue(editor.uiState.value.conditionPlanning is ConditionPlanningResult.Blocked)
        compose.onNodeWithText("Add condition support").assertDoesNotExist()
        compose.runOnIdle { editor.addExercise(f.ordinary) }
        assertRefused(save())
    }

    @Test fun ordinaryNoConditionEditingAndStartRemainUnchanged() {
        runBlocking { profiles.saveUserProfile(profiles.getProfileOnce().copy(activeConditions = emptyList())) }
        show("en", conditions = false)
        compose.onNodeWithText("Add condition support").assertDoesNotExist()
        compose.runOnIdle { editor.addExercise(f.ordinary) }
        val template = save()
        assertEquals(PrescriptionPurpose.ORDINARY, template.exercises.single().prescription.purpose)
        val session = runBlocking { workouts.startWorkoutFromTemplate(template, profiles.getProfileOnce()) }
        assertNull(session.conditionContext)
    }

    @Test fun exactKilogramSupportIsAdopted() = loadedDose(WeightUnit.KG, 10.0, accepted = true)
    @Test fun exactPoundSupportIsConvertedAndAdopted() = loadedDose(WeightUnit.LBS,
        convertWeight(10.0, WeightUnit.LBS, WeightUnit.KG), accepted = true)
    @Test fun fractionalKilogramSupportIsRefusedWithoutPartialAdoption() = loadedDose(WeightUnit.KG, 10.123, accepted = false)
    @Test fun poundConversionThatWouldRoundSupportIsRefusedWithoutPartialAdoption() = loadedDose(WeightUnit.LBS, 10.0, accepted = false)

    private fun loadedDose(unit: WeightUnit, kilograms: Double, accepted: Boolean) {
        val exercise = f.supportive.copy(type = ExerciseType.WEIGHT_REPS)
        val dose = ExercisePrescription(ExerciseType.WEIGHT_REPS, 2, repRange = RepRange(8, 10),
            targetWeight = kilograms, restSeconds = 17, purpose = PrescriptionPurpose.SUPPORTIVE)
        support = f.context(supportExercise = exercise, supportiveDose = dose).conditionSupport!!
        catalog = InMemoryExerciseCatalog(listOf(f.ordinary, exercise))
        repositories()
        runBlocking { profiles.saveUserProfile(profiles.getProfileOnce().copy(preferredUnit = unit)) }
        show("en")
        compose.runOnIdle { editor.addExercise(f.ordinary) }
        val before = editor.uiState.value.selectedExercises
        if (kilograms == 10.123) compose.onNodeWithText("10.123", substring = true).performScrollTo().assertIsDisplayed()
        adopt()
        if (accepted) {
            val template = save()
            val session = runBlocking { workouts.startWorkoutFromTemplate(template, profiles.getProfileOnce()) }
            assertEquals(convertWeight(kilograms, WeightUnit.KG, unit), session.exercises.last().prescription.targetWeight!!, 0.0)
        } else {
            assertEquals(before, editor.uiState.value.selectedExercises)
            assertEquals(R.string.editor_support_precision, editor.uiState.value.errorMessage)
            // The same refusal again is a new attempt, so its keyed error card is announced again.
            val attempt = editor.uiState.value.submitAttempt
            adopt()
            assertEquals(R.string.editor_support_precision, editor.uiState.value.errorMessage)
            assertEquals(attempt + 1, editor.uiState.value.submitAttempt)
        }
    }

    private fun adopt() {
        compose.onNodeWithText("Add condition support").performScrollTo().performClick()
        compose.waitUntil(10_000) { !editor.uiState.value.isCheckingSupport }
        compose.waitForIdle()
    }
    private fun save(language: String = "en"): WorkoutTemplate {
        compose.runOnIdle { editor.updateName("Manual") }
        val resources = ApplicationProvider.getApplicationContext<Context>().createConfigurationContext(
            Configuration().apply { setLocales(LocaleList(Locale.forLanguageTag(language))) }).resources
        val label = resources.getString(R.string.editor_action_save)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(label))
        compose.onNodeWithText(label).assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { editor.uiState.value.isSaved }
        return runBlocking { templates.observeTemplates().first().single() }
    }
    private fun assertRefused(template: WorkoutTemplate) = runBlocking {
        val failure = runCatching { workouts.startWorkoutFromTemplate(template, profiles.getProfileOnce()) }.exceptionOrNull()
        assertTrue("Expected refusal, got $failure", failure is ConditionStartRefused)
        assertNull(workouts.getActiveSessionOnce())
        assertTrue(db.localDataBackupDao().selectRecommendationRecords().isEmpty())
    }
    private fun capture(name: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File) ?: context.filesDir
        output.mkdirs()
        val file = File(output, "final-fix-$name.png")
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private suspend fun context() = WorkoutGenerationContextBuilder(profiles, workouts, catalog,
        ExerciseFilter(), WorkoutHistoryAnalyzer(), nowTimestamp = { f.now }, zoneId = { ZoneId.of("UTC") },
        conditionSupport = { support }).build()

    private fun show(language: String, fontScale: Float = 1f, templateId: String? = null, conditions: Boolean = true) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(Locale.forLanguageTag(language)))
            this.fontScale = fontScale
        }
        val localized = context.createConfigurationContext(config)
        editor = TemplateEditorViewModel(templateId, templates, profiles, catalog, locale = { Locale.forLanguageTag(language) }, conditionContext = { context() })
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides config,
                LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                WallCrawlTheme { TemplateEditorScreen(editor, {}, {}) }
            }
        }
        compose.waitUntil(10_000) { !editor.uiState.value.isLoading && (!conditions || editor.uiState.value.conditionPlanning !is ConditionPlanningResult.NotApplicable) }
    }
}
