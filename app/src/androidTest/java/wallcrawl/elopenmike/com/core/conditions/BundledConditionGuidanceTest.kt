package wallcrawl.elopenmike.com.core.conditions

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.ai.FakeWorkoutPlanner
import wallcrawl.elopenmike.com.core.ai.GeneratedWorkoutValidator
import wallcrawl.elopenmike.com.core.ai.PlannerFeatureFlags
import wallcrawl.elopenmike.com.core.ai.ProgramValidationResult
import wallcrawl.elopenmike.com.core.ai.ProgramValidator
import wallcrawl.elopenmike.com.core.ai.TrainingPolicyResultException
import wallcrawl.elopenmike.com.core.ai.TrainingProgramStateProvider
import wallcrawl.elopenmike.com.core.ai.WorkoutGenerationContextBuilder
import wallcrawl.elopenmike.com.core.ai.WorkoutHistoryAnalyzer
import wallcrawl.elopenmike.com.core.ai.WorkoutValidationException
import wallcrawl.elopenmike.com.core.ai.acceptedMetadata
import wallcrawl.elopenmike.com.core.database.WallCrawlDatabase
import wallcrawl.elopenmike.com.core.database.repository.OfflineDeloadRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWeeklyDoseLedgerRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutRepository
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.exercise.BundledExerciseCatalog
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.exercise.localization.ExerciseLocalizationStore
import wallcrawl.elopenmike.com.core.exercise.workoutguide.WorkoutGuideCatalogStore
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.UserProfile

/**
 * Guards the shipped overlay against the catalog, its research doses and its copy rules, then
 * runs it through the production composition. On a device because the parsers need
 * `android.util.JsonReader`.
 */
@RunWith(AndroidJUnit4::class)
class BundledConditionGuidanceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone: ZoneId = ZoneId.of("UTC")

    private lateinit var database: WallCrawlDatabase
    private lateinit var catalogStore: WorkoutGuideCatalogStore
    private lateinit var catalog: BundledExerciseCatalog
    private lateinit var guidance: ConditionGuidance
    private lateinit var exercises: Map<String, Exercise>

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(context, WallCrawlDatabase::class.java)
            .addMigrations(*WallCrawlDatabase.ALL_MIGRATIONS)
            .build()
        catalogStore = WorkoutGuideCatalogStore(context.assets)
        catalog = BundledExerciseCatalog(catalogStore, ExerciseLocalizationStore(context.assets))
        exercises = catalog.getAllExercises().first().associateBy(Exercise::id)
        guidance = context.assets.open(ASSET).reader().use { ConditionGuidanceParser().parse(it) }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun bundledOverlayParses() = runBlocking {
        assertThat(guidance.conditions.keys).containsExactlyElementsIn(EXPECTED_ROUTINES.keys)
        assertThat(guidance.jointLabelDemands).containsExactlyEntriesIn(EXPECTED_JOINT_LABELS)
        val loaded = ConditionGuidanceStore(context.assets).guidance()
        assertThat(loaded).isInstanceOf(ConditionGuidanceResult.Loaded::class.java)
    }

    @Test
    fun everyAcceptedRecordHasADemandEntry() {
        val accepted = exercises.values.filter { it.acceptedMetadata() != null }.map(Exercise::id)
        assertThat(accepted).hasSize(182)
        assertThat(guidance.exerciseDemands.keys).containsAtLeastElementsIn(accepted)
        assertThat(exercises.keys).containsAtLeastElementsIn(guidance.exerciseDemands.keys)
    }

    @Test
    fun everyRoutineEntryIsInTheCatalogAndHasAMatchingType() {
        routineEntries().forEach { (conditionId, entry) ->
            val message = "$conditionId/${entry.exerciseId}"
            val exercise = exercises[entry.exerciseId]
            assertWithMessage(message).that(exercise).isNotNull()
            assertWithMessage(message).that(guidance.exerciseDemands).containsKey(entry.exerciseId)
            val expected =
                if (entry.targetReps != null) ExerciseType.BODYWEIGHT_REPS else ExerciseType.DURATION
            assertWithMessage(message).that(exercise!!.type).isEqualTo(expected)
        }
    }

    @Test
    fun everyRoutineDoseMatchesConsolidationSectionFive() {
        EXPECTED_ROUTINES.forEach { (conditionId, routine) ->
            assertWithMessage(conditionId)
                .that(guidance.conditions.getValue(conditionId).routine)
                .containsExactlyElementsIn(routine)
                .inOrder()
        }
    }

    @Test
    fun noRoutineContainsANerveSlider() {
        val ids = routineEntries().map { it.second.exerciseId }
        ids.forEach { id ->
            assertWithMessage(id).that(id).doesNotContainMatch("slider|nerve|neural|floss")
        }
    }

    @Test
    fun routinesExcludeTheExercisesRuledOut() {
        val ids = routineEntries().map { it.second.exerciseId }
        ids.forEach { id ->
            assertWithMessage(id).that(id).doesNotContainMatch("fair|quad.*set|isometric-wrist|wrist-.*hold")
        }
        assertThat(guidance.conditions.getValue("piriformis_syndrome").routine.map { it.exerciseId })
            .containsNoneOf("clamshell", "banded-clamshell")
    }

    @Test
    fun everyRoutineStretchHasAStopIfSymptomsCue() {
        routineEntries()
            .filter { (_, entry) -> exercises.getValue(entry.exerciseId).isStretchEntry() }
            .also { assertThat(it).isNotEmpty() }
            .forEach { (conditionId, entry) ->
                val cues = guidance.conditions.getValue(conditionId).exerciseCues[entry.exerciseId]
                val message = "$conditionId/${entry.exerciseId}"
                assertWithMessage(message).that(cues?.get("en")).contains("Stop")
                assertWithMessage(message).that(cues?.get("es")).contains("Detente")
            }
    }

    @Test
    fun everyConditionHasEnglishAndSpanishRedFlagsAndNotes() {
        guidance.conditions.values.forEach { record ->
            listOf("notes" to record.notes, "redFlags" to record.redFlags).forEach { (name, text) ->
                val message = "${record.id}/$name"
                assertWithMessage(message).that(text["en"]).isNotEmpty()
                assertWithMessage(message).that(text["es"]).hasSize(text.getValue("en").size)
            }
            record.exerciseCues.forEach { (id, cue) ->
                assertWithMessage("${record.id}/$id").that(cue.keys).containsExactly("en", "es")
            }
        }
    }

    @Test
    fun everyDemandHasEnglishAndSpanishCues() {
        MovementDemand.entries.forEach { demand ->
            assertWithMessage(demand.name).that(guidance.demandCues[demand]?.keys)
                .containsExactly("en", "es")
        }
    }

    @Test
    fun everyRecordHasDoiOrPmidSources() {
        guidance.conditions.values.forEach { record ->
            assertWithMessage(record.id).that(record.sources).isNotEmpty()
            (record.sources + record.provenance.sourceReferences).forEach { source ->
                assertWithMessage(record.id).that(source).matches(DOI_OR_PMID)
            }
            assertThat(record.provenance.reviewedContentId).isEqualTo(record.id)
            assertWithMessage(record.id).that(record.provenance.limitations)
                .contains("not reviewed by a clinician")
        }
    }

    @Test
    fun copyMakesNoBenefitClaimAndNamesNoLocalAction() {
        val copy = guidance.demandCues.values.flatMap { it.values } +
            guidance.conditions.values.flatMap { record ->
                record.notes.values.flatten() +
                    record.redFlags.values.flatten() +
                    record.exerciseCues.values.flatMap { it.values } +
                    record.provenance.decisionRationale +
                    record.provenance.limitations
            }
        assertThat(copy).isNotEmpty()
        copy.forEach { text ->
            assertWithMessage(text).that(FORBIDDEN_COPY.containsMatchIn(text)).isFalse()
        }
    }

    @Test
    fun fullGymGetsAPlanForEachConditionAndJointLabel() = runBlocking {
        inputs().forEach { (name, profile) ->
            val outcome = plan(profile.copy(availableEquipment = StandardEquipment.ALL))
            assertWithMessage(name).that(outcome).isInstanceOf(ProgramValidationResult.Valid::class.java)
            val workout = (outcome as ProgramValidationResult.Valid).workout
            assertWithMessage(name).that(workout.exercises).isNotEmpty()
        }
    }

    @Test
    fun bodyweightOnlyOutcomeIsAPlanOrATypedRefusal() = runBlocking {
        inputs().forEach { (name, profile) ->
            val typed = try {
                plan(profile.copy(availableEquipment = listOf(StandardEquipment.BODYWEIGHT)))
            } catch (refused: WorkoutValidationException) {
                refused.failure
            } catch (refused: TrainingPolicyResultException) {
                refused.result
            }
            assertWithMessage(name).that(typed).isNotNull()
            if (typed is ProgramValidationResult.Invalid) {
                assertWithMessage(name).that(typed.violations).isNotEmpty()
            }
        }
    }

    private fun Exercise.isStretchEntry() = id in STRETCH_IDS

    private fun routineEntries(): List<Pair<String, RoutineEntry>> =
        guidance.conditions.values.flatMap { record -> record.routine.map { record.id to it } }

    private fun inputs(): List<Pair<String, UserProfile>> {
        val base = UserProfile(
            availableEquipment = StandardEquipment.ALL,
            movementCapabilities = MovementCapabilities.from(
                MovementCapabilityType.entries.associateWith { CapabilityLevel.COMFORTABLE }
            ),
            onboardingCompleted = true
        )
        return guidance.conditions.keys.map { it to base.copy(reportedConditions = setOf(it)) } +
            guidance.jointLabelDemands.keys.map {
                it.name to base.copy(trainingConstraints = setOf(it))
            }
    }

    private suspend fun plan(profile: UserProfile): ProgramValidationResult {
        val workoutRepository = OfflineWorkoutRepository(
            sessionDao = database.workoutSessionDao(),
            setDao = database.workoutSetDao()
        )
        val writeGate = Mutex()
        // Task 5 persists conditions; until then this profile is served as is, not round-tripped.
        val stored = OfflineUserProfileRepository(database.userProfileDao(), localDataWriteGate = writeGate)
        val profileRepository = object : UserProfileRepository by stored {
            override fun getUserProfile() = flowOf(profile)
            override suspend fun getProfileOnce() = profile
        }
        val builder = WorkoutGenerationContextBuilder(
            userProfileRepository = profileRepository,
            workoutRepository = workoutRepository,
            exerciseCatalog = catalog,
            exerciseFilter = ExerciseFilter(),
            historyAnalyzer = WorkoutHistoryAnalyzer(),
            plannerFeatureFlags = PlannerFeatureFlags.PRODUCTION,
            trainingProgramStateProvider = TrainingProgramStateProvider(
                weeklyDoseLedgerRepository = OfflineWeeklyDoseLedgerRepository(
                    historyDao = database.completedWorkoutHistoryDao(),
                    ledgerStateDao = database.weeklyDoseLedgerStateDao(),
                    catalogSource = catalogStore
                ),
                zoneId = { zone }
            ),
            catalogVersion = { runBlocking { catalogStore.snapshot() }.catalogAttribution.commit },
            deloadRepository = OfflineDeloadRepository(database.deloadPreferencesDao(), writeGate),
            conditionGuidanceSource = ConditionGuidanceStore(context.assets)
        )
        val generationContext = builder.build()
        val workout = FakeWorkoutPlanner().generateWorkout(generationContext)
        return ProgramValidator(GeneratedWorkoutValidator(catalog))
            .validate(workout, generationContext, allowRepair = true)
    }

    private companion object {
        const val ASSET = "conditions/condition-guidance.json"
        val DOI_OR_PMID = Regex("https://(doi\\.org/10\\.\\S+|pubmed\\.ncbi\\.nlm\\.nih\\.gov/\\d+/?)").pattern
        val STRETCH_IDS = setOf("hamstring-stretch", "cross-body-shoulder-stretch")

        val FORBIDDEN_COPY = Regex(
            "(?i)\\b(safe for|helps? heal|treats?|cures?|prevents?|seguro para|segura para|" +
                "ayuda a sanar|ayuda a curar|trata|tratan|cura|curan|previene|previenen|" +
                "A&E|999|NHS|GP|clinician referral|referral|derivaci[oó]n)\\b"
        )

        fun reps(id: String, sets: Int, reps: Int, hold: Int? = null, rest: Int = 0) =
            RoutineEntry(id, sets, reps, null, hold, rest)

        fun timed(id: String, sets: Int, seconds: Int) = RoutineEntry(id, sets, null, seconds, null, 0)

        /** Section 5, catalog-backed entries only. Task 9 appends the new exercises. */
        val EXPECTED_ROUTINES = mapOf(
            "piriformis_syndrome" to listOf(reps("glute-bridge", 3, 15)),
            "gluteal_tendinopathy" to listOf(
                reps("side-lying-hip-abduction", 3, 10, rest = 60),
                reps("glute-bridge", 1, 10),
                reps("bodyweight-squat", 1, 10)
            ),
            "lumbar_radiculopathy" to listOf(reps("bird-dog", 2, 10), reps("glute-bridge", 2, 10)),
            "nonspecific_low_back_pain" to listOf(
                reps("bird-dog", 2, 15),
                reps("dead-bug", 2, 15),
                reps("glute-bridge", 3, 10),
                timed("hamstring-stretch", 5, 30)
            ),
            "rotator_cuff_related_shoulder_pain" to listOf(timed("cross-body-shoulder-stretch", 3, 30)),
            "lateral_elbow_tendinopathy" to emptyList(),
            "carpal_tunnel_syndrome" to emptyList(),
            "patellofemoral_pain" to listOf(
                reps("side-lying-hip-abduction", 3, 10),
                reps("bodyweight-squat", 3, 10, hold = 2, rest = 120)
            )
        )

        /** Section 4 minus the dropped tags. */
        val EXPECTED_JOINT_LABELS = mapOf(
            TrainingConstraint.SHOULDER_SENSITIVE to setOf(MovementDemand.HEAVY_EXTERNAL_LOAD),
            TrainingConstraint.ELBOW_SENSITIVE to setOf(
                MovementDemand.HEAVY_EXTERNAL_LOAD,
                MovementDemand.WRIST_EXTENSOR_WORK,
                MovementDemand.FOREARM_TWIST_OR_PRONATED_LIFT,
                MovementDemand.HAND_GRIP_LOAD
            ),
            TrainingConstraint.WRIST_SENSITIVE to setOf(
                MovementDemand.NERVE_TENSIONING,
                MovementDemand.HAND_GRIP_LOAD,
                MovementDemand.WRIST_END_RANGE_LOAD
            ),
            TrainingConstraint.LOWER_BACK_SENSITIVE to setOf(
                MovementDemand.HEAVY_EXTERNAL_LOAD,
                MovementDemand.TRUNK_FLEXION_UNDER_LOAD
            ),
            TrainingConstraint.HIP_SENSITIVE to setOf(
                MovementDemand.SUSTAINED_LOCOMOTION,
                MovementDemand.PLYOMETRIC_IMPACT,
                MovementDemand.HEAVY_EXTERNAL_LOAD,
                MovementDemand.SINGLE_LEG_HIP_CONTROL,
                MovementDemand.CROSS_BODY_HIP_STRETCH
            ),
            TrainingConstraint.KNEE_SENSITIVE to setOf(
                MovementDemand.PROLONGED_SITTING,
                MovementDemand.SUSTAINED_LOCOMOTION,
                MovementDemand.PLYOMETRIC_IMPACT,
                MovementDemand.DEEP_KNEE_FLEXION_LOAD,
                MovementDemand.END_RANGE_KNEE_EXTENSION_LOAD
            )
        )
    }
}
