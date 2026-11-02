package wallcrawl.elopenmike.com.core.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.*
import wallcrawl.elopenmike.com.core.ui.components.*
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

/** FICTIONAL SOFTWARE FIXTURES ONLY. These records never enter the bundled support store. */
@RunWith(AndroidJUnit4::class)
class ConditionControlsTest {
    private val compose = createComposeRule()
    private val viewModels = androidx.lifecycle.ViewModelStore()
    private var database: androidx.room.RoomDatabase? = null
    @get:Rule val rules = org.junit.rules.RuleChain.outerRule(object : org.junit.rules.ExternalResource() {
        override fun after() { InstrumentationRegistry.getInstrumentation().runOnMainSync { viewModels.clear() }; database?.close() }
    }).around(compose)
    private val text = ConditionText("Fictional condition", "Condición ficticia")
    private val input = ConditionInputDefinition("side", ConditionText("Recorded side", "Lado registrado"), true,
        mapOf("left" to ConditionText("Left", "Izquierdo"), "right" to ConditionText("Right", "Derecho")))
    private val identity = ConditionExerciseIdentity("fictional-exercise", "a".repeat(64), ExerciseType.DURATION, "b".repeat(64))
    private val instruction = ConditionText("Fictional instruction: use the recorded side.", "Instrucción ficticia: usa el lado registrado.")
    private val record = ConditionSupportRecord("fictional", "fictional-v1", 1, 1, ConditionPopulation.ADULT_NONOPERATIVE,
        text, text, listOf(input), listOf("left", "right").map { side ->
            ConditionApplicabilityCase(side, mapOf("side" to side), listOf(ConditionRule("rule", identity,
                ConditionRuleEffect.EXCLUDE, "fictional-execution", instruction, setOf("source"), null, false, null, null)))
        }, listOf(ConditionSource("source", "https://example.invalid/fictional", "a".repeat(64))),
        ConditionResponsePolicy(1, listOf(ConditionInputDefinition("response", text, true,
            mapOf("stop" to ConditionText("Stop response", "Respuesta de detener"), "continue" to ConditionText("Continue response", "Respuesta de continuar")))), mapOf("response" to "stop"), mapOf("response" to "stop"), text),
        "c".repeat(64), ConditionReleaseReview(ConditionReviewState.RELEASED, "FICTIONAL", "FICTIONAL", 1, "FICTIONAL", "c".repeat(64), setOf("source")))

    @Test fun fullRetainedAnswersDisableOnlyNewInputsInEnglish() = checkFullRetainedAnswers("en")
    @Test fun fullRetainedAnswersDisableOnlyNewInputsInSpanish() = checkFullRetainedAnswers("es")

    private fun checkFullRetainedAnswers(language: String) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        val context = base.createConfigurationContext(config)
        val original = (1..32).associate { "future-$it" to if (it % 2 == 0) null else "retained" }
        var selected by mutableStateOf(listOf(SelectedCondition("fictional", 1, original)))
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config, LocalResources provides context.resources) {
                WallCrawlTheme { Surface { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    ConditionSelectionControls(listOf(record), selected, { selected = it })
                } } }
            }
        }
        val left = if (language == "es") "Izquierdo" else "Left"
        val right = if (language == "es") "Derecho" else "Right"
        val refusal = if (language == "es") "No se puede añadir otra respuesta porque los datos guardados han alcanzado el límite. Se conservan todas tus respuestas."
            else "Another answer cannot be added because the saved answers have reached the limit. All your answers are kept."
        compose.onNodeWithText(left).performScrollTo().assertIsNotEnabled().performClick()
        compose.onNodeWithText(refusal).performScrollTo().assertIsDisplayed()
        assertThat(selected.single().answers).isEqualTo(original)
        save("answer-limit-$language.png")
        // Replacing an existing explicit-null key is permitted at the same bound.
        compose.runOnIdle { selected = listOf(SelectedCondition("fictional", 1, original - "future-32" + ("side" to null))) }
        compose.onNodeWithText(right).performScrollTo().assertIsEnabled().performClick()
        assertThat(selected.single().answers).isEqualTo(original - "future-32" + ("side" to "right"))
        assertThat(original).hasSize(32)
    }

    @Test fun fullReassessmentAnswersDisableNewKeysAndKeepExactEventForExistingEdits() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val db = androidx.room.Room.inMemoryDatabaseBuilder(base, wallcrawl.elopenmike.com.core.database.WallCrawlDatabase::class.java).build()
        database = db
        val vm = wallcrawl.elopenmike.com.feature.profile.ProfileViewModel(
            wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository(db.userProfileDao()))
        viewModels.put("answer-limit", vm)
        val event = PendingConditionReassessment(UserProfile.DEFAULT_PROFILE_ID, "fictional", identity.exerciseId, "exact-event", "a".repeat(64), 1, 100, null)
        val answers = (1..31).associate { "future-$it" to if (it % 2 == 0) null else "retained" } + ("response" to "stop")
        compose.runOnUiThread { vm.updateReassessmentAnswers(event.eventId, answers) }
        var submitted: PendingConditionReassessment? = null
        compose.setContent { WallCrawlTheme { Surface { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            val state by vm.conditions.collectAsState()
            PendingConditionControls(state.copy(records = listOf(record), pending = listOf(event)), emptyList(),
                vm::updateReassessmentAnswers, { submitted = it })
        } } } }
        compose.onNodeWithText("Left").performScrollTo().assertIsNotEnabled().performClick()
        assertThat(vm.conditions.value.reassessmentAnswers).isEqualTo(answers)
        compose.onNodeWithText("Continue response").performScrollTo().assertIsEnabled().performClick()
        assertThat(vm.conditions.value.reassessmentAnswers).isEqualTo(answers + ("response" to "continue"))
        // With all current prompts already represented, only the direct boundary refusal supplies this message.
        val complete = answers - "future-31" + ("side" to "left")
        compose.runOnUiThread {
            vm.updateReassessmentAnswers(event.eventId, complete)
            vm.updateReassessmentAnswers(event.eventId, complete + ("extra" to "answer"))
        }
        compose.onNodeWithText(base.getString(R.string.condition_answer_limit)).performScrollTo().assertIsDisplayed()
        assertThat(vm.conditions.value.reassessmentAnswers).isEqualTo(complete)
        compose.onNodeWithText("Continue response").performScrollTo().performClick()
        compose.onNodeWithText(base.getString(R.string.condition_answer_limit)).assertDoesNotExist()
        compose.onNodeWithText(base.getString(R.string.condition_reassess)).performScrollTo().performClick()
        assertThat(submitted).isSameInstanceAs(event)
        assertThat(vm.conditions.value.reassessmentEventId).isEqualTo("exact-event")
    }

    @Test fun englishLightLargeText() = checkControls("en", ThemePreference.LIGHT)
    @Test fun englishDarkLargeText() = checkControls("en", ThemePreference.DARK)
    @Test fun spanishLightLargeText() = checkControls("es", ThemePreference.LIGHT)
    @Test fun spanishDarkLargeText() = checkControls("es", ThemePreference.DARK)

    private fun checkControls(language: String, theme: ThemePreference) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale(language)); fontScale = 1.6f }
        val context = base.createConfigurationContext(config)
        var selected by mutableStateOf(emptyList<SelectedCondition>())
        var records by mutableStateOf(listOf(record))
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config, LocalResources provides context.resources,
                LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                WallCrawlTheme(themePreference = theme) { Surface {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                        ConditionSelectionControls(records, selected, { selected = it })
                    }
                } }
            }
        }
        val label = if (language == "es") text.es else text.en
        compose.onNodeWithText(context.getString(R.string.condition_add, label)).performScrollTo().performClick()
        val left = if (language == "es") "Izquierdo" else "Left"
        compose.onNodeWithText(left).performScrollTo().performClick()
        assertThat(selected.single().answers).containsEntry("side", "left")
        compose.onNodeWithText(left).assertIsSelected()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(left).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        if (theme == ThemePreference.LIGHT) assertThat(layouts.first().layoutInput.style.color.luminance()).isLessThan(0.4f)
        else assertThat(layouts.first().layoutInput.style.color.luminance()).isGreaterThan(0.6f)
        compose.waitForIdle()
        save("condition-$language-${theme.name.lowercase()}-large.png")
        compose.runOnIdle { selected = listOf(SelectedCondition("fictional", 1, selected.single().answers + mapOf("future" to "token", "response" to null))) }
        compose.onNodeWithText(if (language == "es") "Derecho" else "Right").performScrollTo().performClick()
        assertThat(selected.single().answers).containsExactly("side", "right", "future", "token", "response", null)
        compose.runOnIdle { records = emptyList() }
        compose.onNodeWithText(context.getString(R.string.condition_unsupported, "fictional", 1)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.condition_remove, "fictional")).performScrollTo().performClick()
        assertThat(selected).isEmpty()
        compose.onNodeWithText(context.getString(R.string.condition_title)).assertDoesNotExist()
    }

    @Test fun workoutPreviewUsesFrozenSupportInstructions() {
        val selected = SelectedCondition("fictional", 1, mapOf("side" to "left"))
        val frozen = SessionConditionContext(1, 1, listOf(selected), mapOf("fictional" to "old-v1"), listOf(
            AppliedConditionRule(ConditionRuleReference("fictional", "old-v1", "case", "rule", 1), identity,
                "recorded-execution", PrescriptionPurpose.SUPPORTIVE, instruction)), emptyList(), emptyList())
        val workout = GeneratedWorkout(title = WorkoutTitleSpec(WorkoutSplit.PUSH, WorkoutEmphasis.HYPERTROPHY),
            focusMuscles = listOf("Chest"), estimatedDurationMinutes = 20, exercises = listOf(PlannedExercise(identity.exerciseId,
                ExercisePrescription(ExerciseType.DURATION, 1, targetDurationSeconds = 20, purpose = PrescriptionPurpose.SUPPORTIVE))),
            rationale = WorkoutRationaleSpec.GoalFocus(emptyList(), listOf("Chest")))
        compose.setContent { WallCrawlTheme(themePreference = ThemePreference.DARK) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            wallcrawl.elopenmike.com.feature.today.TodayContent(
                wallcrawl.elopenmike.com.feature.today.TodayUiState.Success(UserProfile(), workout, conditionContext = frozen),
                "Fictional preview", {}, {}, {}, {})
            }
        } }
        compose.onNodeWithTag("today-list").performScrollToNode(hasText(instruction.en))
        compose.onNodeWithText(instruction.en).assertIsDisplayed()
        compose.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.condition_supportive)).assertExists()
        save("today-frozen-supportive-dark.png")
    }

    @Test fun realProfileRetainsConditionDraftThroughLocaleChange() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val db = androidx.room.Room.inMemoryDatabaseBuilder(base, wallcrawl.elopenmike.com.core.database.WallCrawlDatabase::class.java).build()
        database = db
        val repository = wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository(db.userProfileDao())
        kotlinx.coroutines.runBlocking { repository.updateActiveConditions(listOf(SelectedCondition("fictional", 1, mapOf("side" to "left", "future" to null)))) }
        val handle = androidx.lifecycle.SavedStateHandle()
        val vm = wallcrawl.elopenmike.com.feature.profile.ProfileViewModel(repository, handle,
            { ConditionSupportCatalog("fixture", mapOf(record.conditionId to record)) },
            wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutRepository(db.workoutSessionDao(), db.workoutSetDao()))
        viewModels.put("profile", vm)
        var language by mutableStateOf("en")
        compose.setContent {
            val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
            val context = base.createConfigurationContext(config)
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config, LocalResources provides context.resources,
                LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                WallCrawlTheme(themePreference = ThemePreference.LIGHT) {
                    wallcrawl.elopenmike.com.feature.profile.ProfileScreen(vm, {})
                }
            }
        }
        compose.waitUntil(10_000) { vm.uiState.value is wallcrawl.elopenmike.com.feature.profile.ProfileUiState.Success && vm.conditions.value.records.isNotEmpty() }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Edit conditions"))
        compose.onNodeWithText("Recorded side: Left").assertExists()
        compose.onNodeWithText("Edit conditions").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Right"))
        compose.onNodeWithText("Right").performScrollTo().performClick()
        compose.runOnIdle { language = "es" }
        compose.onNodeWithText("Derecho").performScrollTo().assertIsSelected()
        assertThat(vm.conditions.value.draft!!.single().answers).containsExactly("side", "right", "future", null)
        save("profile-condition-es-light-large.png")
    }

    @Test fun onboardingSafetyShowsRetainedSelectionAndExplicitRemoval() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(base.resources.configuration).apply { setLocale(Locale.forLanguageTag("es")) }
        val context = base.createConfigurationContext(config)
        val db = androidx.room.Room.inMemoryDatabaseBuilder(base, wallcrawl.elopenmike.com.core.database.WallCrawlDatabase::class.java).build()
        database = db
        val repository = wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository(db.userProfileDao())
        kotlinx.coroutines.runBlocking { repository.updateActiveConditions(listOf(SelectedCondition("withdrawn", 8, mapOf("side" to null)))) }
        val vm = wallcrawl.elopenmike.com.feature.onboarding.OnboardingViewModel(repository)
        viewModels.put("onboarding", vm)
        compose.runOnUiThread { vm.goToStep(wallcrawl.elopenmike.com.feature.onboarding.OnboardingStep.SAFETY) }
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config, LocalResources provides context.resources,
                LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                WallCrawlTheme(themePreference = ThemePreference.DARK) {
                    wallcrawl.elopenmike.com.feature.onboarding.OnboardingScreen(vm, {})
                }
            }
        }
        val unsupported = context.getString(R.string.condition_unsupported, "withdrawn", 8)
        compose.waitUntil(10_000) { vm.uiState.value.retainedConditions.isNotEmpty() }
        compose.onNodeWithText(unsupported).performScrollTo().assertIsDisplayed()
        save("onboarding-withdrawn-es-dark-large.png")
        compose.onNodeWithText(context.getString(R.string.condition_remove, "withdrawn")).performScrollTo().performClick()
        assertThat(vm.uiState.value.conditionDraft).isEmpty()
    }

    @Test fun savedStateParcelRetainsUnknownAnswersAndExplicitlyEmptyDraft() {
        val selection = SelectedCondition("withdrawn", 42, mapOf("future" to "future-token", "side" to null, "response" to "recorded"))
        fun recreate(rows: ArrayList<String>): List<SelectedCondition> {
            val bundle = android.os.Bundle().apply { putStringArrayList("draft", rows) }
            val parcel = android.os.Parcel.obtain()
            try {
                parcel.writeBundle(bundle)
                parcel.setDataPosition(0)
                val restored = parcel.readBundle(javaClass.classLoader)!!.getStringArrayList("draft")!!
                val handle = androidx.lifecycle.SavedStateHandle(mapOf("profile.conditions" to restored))
                return decodeConditionDraft(handle.get<List<String>>("profile.conditions")!!)
            } finally { parcel.recycle() }
        }
        assertThat(recreate(encodeConditionDraft(listOf(selection)))).containsExactly(selection)
        assertThat(recreate(encodeConditionDraft(emptyList()))).isEmpty()
    }

    @Test fun reassessmentFormOwnsExactEventAndDoesNotReusePreviousResponse() {
        val event = PendingConditionReassessment(UserProfile.DEFAULT_PROFILE_ID, "fictional", identity.exerciseId,
            "event-one", "a".repeat(64), 1, 100, null)
        var state by mutableStateOf(ConditionUiState(records = listOf(record), pending = listOf(event)))
        var submitted: PendingConditionReassessment? = null
        var selections by mutableStateOf(listOf(SelectedCondition("fictional", 1, mapOf("response" to "continue"))))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val action = context.getString(R.string.condition_reassess)
        compose.setContent { WallCrawlTheme { Surface { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            PendingConditionControls(state, selections,
                { eventId, answers -> state = state.copy(reassessmentEventId = eventId, reassessmentAnswers = answers) },
                { submitted = it })
        } } } }
        compose.onNodeWithText(action).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Left").performScrollTo().performClick()
        compose.onNodeWithText("Continue response").performScrollTo().performClick()
        compose.onNodeWithText(action).performScrollTo().performClick()
        assertThat(submitted).isSameInstanceAs(event)
        assertThat(state.reassessmentAnswers).containsExactly("side", "left", "response", "continue")
        save("pending-source-bound-response.png")
        compose.runOnIdle { selections = emptyList(); state = state.copy(pending = listOf(event.copy(eventId = "event-two"))) }
        compose.onNodeWithText(action).performScrollTo().assertIsNotEnabled()
        assertThat(state.reassessmentEventId).isEqualTo("event-one")
        assertThat(state.reassessmentAnswers).containsEntry("response", "continue")
    }

    @Test fun pendingRemainsVisibleWithoutSelectionsOrReleasedRecords() {
        val pending = PendingConditionReassessment(UserProfile.DEFAULT_PROFILE_ID, "withdrawn", "exercise", "exact-event", "a".repeat(64), 9, 100, null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent { WallCrawlTheme { Surface { Column {
            ConditionSelectionControls(emptyList(), emptyList(), {})
            PendingConditionControls(ConditionUiState(pending = listOf(pending)), emptyList(), { _, _ -> error("Unsupported policy") }, { error("Unsupported policy") })
        } } } }
        compose.onNodeWithText(context.getString(R.string.condition_title)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.condition_pending_title)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.condition_reassess)).assertDoesNotExist()
        save("condition-withdrawn-pending.png")
    }

    @Test fun frozenInstructionsAndAnswersSurviveSupportWithdrawalAndLocaleSwitch() {
        val selected = SelectedCondition("fictional", 1, mapOf("side" to "left", "future" to null))
        val frozen = SessionConditionContext(1, 2, listOf(selected), mapOf("fictional" to "old-version"), listOf(
            AppliedConditionRule(ConditionRuleReference("fictional", "old-version", "case", "rule", 2), identity,
                "old-execution", PrescriptionPurpose.SUPPORTIVE, instruction)), emptyList(), emptyList())
        var language by mutableStateOf("en")
        val digest = frozen.canonicalDigest
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            val config = Configuration(base.resources.configuration).apply { setLocale(Locale(language)); fontScale = 1.6f }
            val context = base.createConfigurationContext(config)
            CompositionLocalProvider(LocalContext provides context, LocalConfiguration provides config, LocalResources provides context.resources,
                LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                WallCrawlTheme { Surface { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    RecordedConditions(frozen)
                    ConditionExecution(frozen, "fictional-exercise", PrescriptionPurpose.SUPPORTIVE)
                } } }
            }
        }
        compose.onNodeWithText(instruction.en).performScrollTo().assertIsDisplayed()
        compose.runOnIdle { language = "es" }
        compose.onNodeWithText(instruction.es).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(instruction.en).assertDoesNotExist()
        assertThat(frozen.canonicalDigest).isEqualTo(digest)
        save("condition-frozen-history-es-large.png")
    }

    private fun save(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let(::File) ?: context.filesDir
        output.mkdirs()
        File(output, name).outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
