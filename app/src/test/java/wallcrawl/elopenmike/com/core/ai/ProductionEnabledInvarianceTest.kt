package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.ProfileGender
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.TrainingWeek
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.convertWeight

/**
 * Language and gender are presentation; a logged load is a number.
 *
 * These run the enabled production composition over the actual bundled catalog, not the
 * in-memory sample set, because that is where a locale-sensitive comparison or a
 * gender-dependent lookup would actually reach a training decision. The unit case is here
 * for the same reason: a cohort test that only asserts "a plan exists" would pass while the
 * prescribed load silently stayed in the unit it was logged in.
 */
class ProductionEnabledInvarianceTest {
    @Test
    fun activeSchedulingReasonsAndSelectionsAreInvariantAcrossLanguageAndGender() = runTest {
        val ids = setOf("dumbbell-bench-press", "dumbbell-shoulder-press", "cable-fly", "cable-lateral-raise")
        val history = (0L..1L).map { day ->
            completedSession("practice-$day", WEEK.startEpochMillis + day * DAY_MILLIS,
                listOf(exerciseInstance("dumbbell-shoulder-press",
                    listOf(completedNormalSet("set-$day")), id = "instance-$day")))
        }
        val results = LOCALES.flatMap { locale ->
            Locale.setDefault(locale)
            ProfileGender.entries.map { gender ->
                val context = productionContext(fullGymProfile().copy(
                    daysPerWeek = 6, preferredDurationMinutes = 30, gender = gender,
                    musclePriorities = mapOf(wallcrawl.elopenmike.com.core.model.StandardMuscles.SHOULDERS to
                        wallcrawl.elopenmike.com.core.model.PriorityLevel.HIGH),
                    excludedExerciseIds = bundledExercises.map(Exercise::id).filterNot { it in ids }
                ), history)
                FakeWorkoutPlanner().generateWorkout(context).normalizedPlannerFixtureWorkout() to
                    RecommendationContextIdentity.of(context)
            }
        }
        assertThat(results.first().first.rankingReasons).hasSize(2)
        results.forEach { assertThat(it).isEqualTo(results.first()) }
    }

    private val bundledCatalog = PlannerFixtureContextFactory().bundledCatalogProjection()
    private val bundledExercises = bundledCatalog.exercises
    private val defaultLocale: Locale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun everyLocaleProducesTheSameSelectionPrescriptionsContextAndFingerprint() = runTest {
        val results = LOCALES.associateWith { locale ->
            Locale.setDefault(locale)
            planUnderProduction(fullGymProfile())
        }

        val reference = results.getValue(LOCALES.first())
        results.forEach { (locale, result) ->
            assertWithMessage("selection and order under $locale")
                .that(result.exerciseIds).isEqualTo(reference.exerciseIds)
            assertWithMessage("prescriptions under $locale")
                .that(result.prescriptions).isEqualTo(reference.prescriptions)
            assertWithMessage("advertised focus under $locale")
                .that(result.focusMuscles).isEqualTo(reference.focusMuscles)
            assertWithMessage("candidate set under $locale")
                .that(result.candidateIds).isEqualTo(reference.candidateIds)
            // The digest decides whether a displayed plan may still be started. A language
            // that moved it would refuse every plan already on screen.
            assertWithMessage("context identity under $locale")
                .that(result.contextIdentity).isEqualTo(reference.contextIdentity)
            assertWithMessage("ledger cache key under $locale")
                .that(result.ledgerFingerprint).isEqualTo(reference.ledgerFingerprint)
            assertWithMessage("recorded dose accounting under $locale")
                .that(result.doseAccounting).isEqualTo(reference.doseAccounting)
        }
        assertThat(reference.exerciseIds).isNotEmpty()
    }

    @Test
    fun everyGenderProducesTheSameSelectionPrescriptionsContextAndCanonicalIds() = runTest {
        val results = ProfileGender.entries.associateWith { gender ->
            planUnderProduction(fullGymProfile().copy(gender = gender))
        }

        val reference = results.getValue(ProfileGender.UNSPECIFIED)
        results.forEach { (gender, result) ->
            assertWithMessage("selection and order for $gender")
                .that(result.exerciseIds).isEqualTo(reference.exerciseIds)
            assertWithMessage("prescriptions for $gender")
                .that(result.prescriptions).isEqualTo(reference.prescriptions)
            assertWithMessage("context identity for $gender")
                .that(result.contextIdentity).isEqualTo(reference.contextIdentity)
            assertWithMessage("ledger cache key for $gender")
                .that(result.ledgerFingerprint).isEqualTo(reference.ledgerFingerprint)
        }

        // Artwork is addressed by the canonical catalog id, so gender cannot move which
        // drawing a planned exercise resolves to. Only the rendered variant is a preference,
        // and it is chosen above this layer.
        val canonicalIds = bundledExercises.mapTo(linkedSetOf(), Exercise::id)
        assertThat(canonicalIds).containsAtLeastElementsIn(reference.exerciseIds)
        assertThat(reference.exerciseIds.all { it == it.lowercase(Locale.ROOT) }).isTrue()
    }

    @Test
    fun aLoadLoggedInPoundsIsPrescribedAsTheConvertedKilogramNumber() = runTest {
        val benchPress = bundledExercises.single { it.id == "barbell-bench-press" }
        assertThat(benchPress.type).isEqualTo(ExerciseType.WEIGHT_REPS)
        val loggedPounds = 135.0
        val history = listOf(
            sessionLoggedIn(
                unit = WeightUnit.LBS,
                exercise = benchPress,
                completedWeight = loggedPounds,
                completedReps = 3
            )
        )
        val kilogramProfile = fullGymProfile().copy(preferredUnit = WeightUnit.KG)

        val context = productionContext(kilogramProfile, history)
        val expectedKilograms = convertWeight(loggedPounds, WeightUnit.LBS, WeightUnit.KG)
        val recorded = requireNotNull(context.exerciseHistory[benchPress.id])
        assertThat(recorded.lastWeight).isWithin(TOLERANCE).of(expectedKilograms)
        assertThat(recorded.recentSets.map { it.completedWeight })
            .containsExactly(expectedKilograms)

        // The prescription carries that number, not the pounds it was logged in.
        val prescription = DefaultExercisePrescriptionFactory().create(benchPress, context)
        assertThat(requireNotNull(prescription.targetWeight))
            .isWithin(TOLERANCE).of(expectedKilograms)
        assertThat(prescription.targetWeight).isNotWithin(TOLERANCE).of(loggedPounds)

        // Provenance: the same profile with no history is prescribed no load at all, so the
        // number above can only have come from what was logged.
        val withoutHistory = productionContext(kilogramProfile, emptyList())
        assertThat(withoutHistory.exerciseHistory).isEmpty()
        assertThat(
            DefaultExercisePrescriptionFactory().create(benchPress, withoutHistory).targetWeight
        ).isNull()
    }

    @Test
    fun readingTheSameHistoryInPoundsLeavesTheLoggedNumberUnchanged() = runTest {
        // The counterpart that makes the conversion above a conversion rather than a constant.
        val benchPress = bundledExercises.single { it.id == "barbell-bench-press" }
        val loggedPounds = 135.0
        val history = listOf(
            sessionLoggedIn(
                unit = WeightUnit.LBS,
                exercise = benchPress,
                completedWeight = loggedPounds,
                completedReps = 3
            )
        )

        val context = productionContext(fullGymProfile(), history)
        assertThat(requireNotNull(context.exerciseHistory[benchPress.id]).lastWeight)
            .isWithin(TOLERANCE).of(loggedPounds)
        assertThat(
            requireNotNull(
                DefaultExercisePrescriptionFactory().create(benchPress, context).targetWeight
            )
        ).isWithin(TOLERANCE).of(loggedPounds)
    }

    // region harness

    private suspend fun planUnderProduction(profile: UserProfile): PlanUnderLocale {
        val context = productionContext(profile, emptyList())
        val plan = FakeWorkoutPlanner().generateWorkout(context)
        val validation = ProgramValidator(
            GeneratedWorkoutValidator(InMemoryExerciseCatalog(bundledExercises))
        ).validate(workout = plan, context = context, allowRepair = true)
        val accepted = validation as ProgramValidationResult.Valid
        return PlanUnderLocale(
            exerciseIds = plan.exercises.map { it.exerciseId },
            prescriptions = plan.exercises.map { it.prescription },
            focusMuscles = plan.focusMuscles,
            candidateIds = context.allowedExercises.map(Exercise::id),
            contextIdentity = accepted.snapshot.contextIdentity,
            doseAccounting = accepted.snapshot.doseAccounting.toString(),
            ledgerFingerprint = LedgerSourceFingerprint.of(
                sessions = emptyList(),
                exercisesById = bundledExercises.associateBy(Exercise::id),
                policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
                week = WEEK,
                catalogVersion = bundledCatalog.sourceCommit,
                reviewPolicyVersion = REVIEW_POLICY_VERSION
            )
        )
    }

    private suspend fun productionContext(
        profile: UserProfile,
        history: List<WorkoutSession>
    ): WorkoutGenerationContext = WorkoutGenerationContextBuilder(
        userProfileRepository = StaticUserProfileRepository(profile),
        workoutRepository = StaticWorkoutRepository(history),
        exerciseCatalog = InMemoryExerciseCatalog(bundledExercises),
        exerciseFilter = ExerciseFilter(),
        historyAnalyzer = WorkoutHistoryAnalyzer(),
        plannerFeatureFlags = PlannerFeatureFlags.PRODUCTION,
        trainingProgramStateProvider = TrainingProgramStateProvider(
            weeklyDoseLedgerRepository = RecomputingLedgerRepository(
                sessions = history,
                exercises = bundledExercises,
                week = WEEK,
                catalogVersion = bundledCatalog.sourceCommit
            ),
            zoneId = { LEDGER_ZONE }
        ),
        catalogVersion = { bundledCatalog.sourceCommit },
        nowTimestamp = { WEEK.startEpochMillis + 3 * DAY_MILLIS },
        zoneId = { LEDGER_ZONE }
    ).build()

    private fun sessionLoggedIn(
        unit: WeightUnit,
        exercise: Exercise,
        completedWeight: Double,
        completedReps: Int
    ): WorkoutSession = completedSession(
        id = "logged-in-${unit.name.lowercase(Locale.ROOT)}",
        completedAtEpochMillis = WEEK.startEpochMillis + HOUR_MILLIS,
        exercises = listOf(
            exerciseInstance(
                exerciseId = exercise.id,
                id = "logged-${exercise.id}",
                sets = listOf(
                    completedNormalSet(id = "logged-set-1").copy(
                        exerciseType = exercise.type,
                        targetWeight = completedWeight,
                        completedWeight = completedWeight,
                        targetReps = completedReps,
                        completedReps = completedReps
                    )
                )
            )
        )
    ).copy(weightUnit = unit)

    private fun fullGymProfile() = UserProfile(
        availableEquipment = StandardEquipment.ALL,
        movementCapabilities = MovementCapabilities.from(
            MovementCapabilityType.entries.associateWith { CapabilityLevel.COMFORTABLE }
        ),
        onboardingCompleted = true
    )

    private class PlanUnderLocale(
        val exerciseIds: List<String>,
        val prescriptions: List<wallcrawl.elopenmike.com.core.model.ExercisePrescription>,
        val focusMuscles: List<String>,
        val candidateIds: List<String>,
        val contextIdentity: String,
        val doseAccounting: String,
        val ledgerFingerprint: String
    )

    // endregion

    private companion object {
        const val MONDAY_EPOCH_DAY = 20_696L
        val LEDGER_ZONE: ZoneId = ZoneId.of("UTC")
        val WEEK: TrainingWeek = TrainingWeek.startingOn(MONDAY_EPOCH_DAY, LEDGER_ZONE)
        const val HOUR_MILLIS = 60 * 60 * 1_000L
        const val DAY_MILLIS = 24 * HOUR_MILLIS
        const val REVIEW_POLICY_VERSION = 2
        const val TOLERANCE = 1e-9

        /**
         * English, Spanish, a decimal-comma locale, and one whose case folding differs from
         * the root locale. The last two are what catch a comparison or number parse that
         * quietly picked up the process default.
         */
        val LOCALES = listOf(
            Locale.ENGLISH,
            Locale.forLanguageTag("es-MX"),
            Locale.forLanguageTag("de-DE"),
            Locale.forLanguageTag("tr-TR")
        )
    }
}
