package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.*

class FirstWeightGuidancePolicyTest {
    private val catalog = PlannerFixtureContextFactory().bundledCatalogProjection().exercises.associateBy { it.id }
    private val profile = UserProfile(
        availableEquipment = listOf(StandardEquipment.DUMBBELL, StandardEquipment.BENCH),
        dumbbellRack = DumbbellRack(WeightUnit.LBS, listOf(5.0, 10.0, 15.0, 20.0, 100.0))
    )
    private val active = WorkoutSession(id = "now", name = "Current", startedAtTimestamp = 3_000_000_000)
    private val lift = WorkoutExercise(
        id = "target", sessionId = active.id, exerciseId = "dumbbell-bench-press", orderIndex = 0,
        prescription = ExercisePrescription(ExerciseType.WEIGHT_REPS, 2, RepRange(8, 12), effortTarget = EffortTarget(2, 4))
    )
    private val source = WorkoutSet(
        workoutExerciseId = "source", setNumber = 1, completedReps = 10, completedWeight = 12.5,
        completedAtTimestamp = active.startedAtTimestamp - 10, isCompleted = true, rir = 3
    )
    private fun history(id: String = "incline-dumbbell-press", set: WorkoutSet = source) =
        WorkoutSession(id = "prior", name = "Prior", startedAtTimestamp = active.startedAtTimestamp - 100,
            completedAtTimestamp = active.startedAtTimestamp - 1, status = SessionStatus.COMPLETED,
            exercises = listOf(lift.copy(id = "source", sessionId = "prior", exerciseId = id, sets = listOf(set))))

    private fun guidance(
        current: WorkoutExercise = lift, workout: WorkoutSession = active, owner: UserProfile? = profile,
        past: List<WorkoutSession> = emptyList(), pending: Boolean = false,
        entry: Exercise? = catalog[current.exerciseId], sourceCatalog: Map<String, Exercise> = catalog,
        oldestUnexaminedSession: Long? = null
    ) = FirstWeightGuidancePolicy.evaluate(current, workout.copy(exercises = listOf(current)), owner, past, entry,
        pending, sourceCatalog, oldestUnexaminedSession)

    @Test fun firstSessionOffersOnlyActualLightRackWeightsInSessionUnit() {
        val first = guidance(workout = active.copy(weightUnit = WeightUnit.KG))
        assertThat(first.rackChoices).containsExactly(2.27, 4.54, 6.8).inOrder()
        assertThat(first.perDumbbell).isTrue()
        assertThat(first.reference).isNull()
        assertThat(guidance(owner = profile.copy(dumbbellRack = null)).rackChoices).isEmpty()
        assertThat(guidance(owner = null).rackChoices).isEmpty()
    }

    @Test fun pressVariantKeepsActualUnitWeightAndRepsAndNeverMovesChoicesUpTheRack() {
        val past = listOf(history().copy(weightUnit = WeightUnit.KG))
        val result = guidance(past = past)
        assertThat(result.reference).isEqualTo(FirstWeightReference("incline-dumbbell-press", 12.5, WeightUnit.KG, 10, source.id))
        assertThat(result.rackChoices).containsExactly(5.0, 10.0, 15.0).inOrder()
        assertThat(guidance(owner = profile.copy(dumbbellRack = null), past = past).reference).isEqualTo(result.reference)
        assertThat(guidance(past = listOf(history())).rackChoices).containsExactly(5.0, 10.0).inOrder()
        assertThat(guidance(past = listOf(history(lift.exerciseId))).reference).isNull()
    }

    @Test fun onlyExplicitlyConfirmedPerDumbbellReferenceUnlocksNearbyRackChoices() {
        val owner = profile.copy(dumbbellRack = DumbbellRack(WeightUnit.LBS, (5..50 step 5).map(Int::toDouble)))
        val past = listOf(history(set = source.copy(completedWeight = 35.0)))
        val result = guidance(owner = owner, past = past)
        assertThat(result.rackChoices).containsExactly(5.0, 10.0, 15.0).inOrder()
        assertThat(result.confirmedReferenceChoices).containsExactly(25.0, 30.0, 35.0).inOrder()
        val restricted = owner.copy(reportedConditions = setOf(ReportedCondition.NONSPECIFIC_LOW_BACK_PAIN.id))
        assertThat(guidance(owner = restricted, past = past).confirmedReferenceChoices).isEmpty()
        assertThat(guidance(owner = owner, past = listOf(history())).confirmedReferenceChoices).isEmpty()
    }

    @Test fun existingTargetOrRecordedLoadLeavesTheNormalLoggerInCharge() {
        val targeted = lift.copy(prescription = lift.prescription.copy(targetWeight = 50.0))
        val recorded = lift.copy(sets = listOf(source.copy(workoutExerciseId = lift.id)))
        val afterWarmup = lift.copy(sets = listOf(source.copy(workoutExerciseId = lift.id, type = SetType.WARMUP)))
        assertThat(guidance(afterWarmup).rackChoices).containsExactly(5.0, 10.0, 15.0).inOrder()
        for (current in listOf(targeted, recorded)) {
            assertThat(guidance(current, past = listOf(history()))).isEqualTo(FirstWeightGuidance(perDumbbell = true))
        }
    }

    @Test fun permittedPressWithLowerBackReportAndTrainingBreakShowsLightChoicesWithoutComparison() {
        val owner = profile.copy(
            reportedConditions = setOf(ReportedCondition.NONSPECIFIC_LOW_BACK_PAIN.id),
            trainingConstraints = setOf(TrainingConstraint.LOWER_BACK_SENSITIVE),
            returningAfterBreakWeeks = 12
        )
        val result = guidance(owner = owner, past = listOf(history()))
        assertThat(result.rackChoices).containsExactly(5.0, 10.0).inOrder()
        assertThat(result.reference).isNull()
    }

    @Test fun unsafePairsAndAmbiguousDumbbellExercisesNeverTransferLoad() {
        for (id in listOf("barbell-bench-press", "machine-chest-press", "goblet-squat")) {
            assertThat(guidance(past = listOf(history(id))).reference).isNull()
        }
        assertThat(guidance(lift.copy(exerciseId = "bicep-curl"), past = listOf(history("goblet-squat"))).reference).isNull()
        for (id in listOf("barbell-bench-press", "goblet-squat", "unknown")) {
            assertThat(guidance(lift.copy(exerciseId = id))).isEqualTo(FirstWeightGuidance())
        }
        assertThat(guidance(entry = catalog.getValue(lift.exerciseId).copy(listedEquipment = listOf("Machine"))))
            .isEqualTo(FirstWeightGuidance())
    }

    @Test fun catalogDefinesRackEligibilityForSingleAndPairedDumbbellsAndRejectsAlternateImplements() {
        for (id in listOf("arnold-press", "dumbbell-romanian-deadlift", "dumbbell-shrug", "dumbbell-hip-thrust")) {
            val result = guidance(lift.copy(exerciseId = id))
            assertThat(result.perDumbbell).isTrue()
            assertThat(result.rackChoices).containsExactly(5.0, 10.0, 15.0).inOrder()
        }
        for (id in listOf("goblet-squat", "reverse-lunge", "single-leg-romanian-deadlift", "farmer-carry")) {
            assertThat(guidance(lift.copy(exerciseId = id))).isEqualTo(FirstWeightGuidance())
        }
    }

    @Test fun onlyNamedCurlAndOverheadPressVariantsProvideAdditionalReferences() {
        for ((current, previous) in listOf(
            "incline-dumbbell-curl" to "bicep-curl",
            "dumbbell-shoulder-press" to "standing-dumbbell-press"
        )) {
            assertThat(guidance(lift.copy(exerciseId = current), past = listOf(history(previous))).reference?.exerciseId)
                .isEqualTo(previous)
        }
        assertThat(guidance(lift.copy(exerciseId = "arnold-press"), past = listOf(history("standing-dumbbell-press"))).reference)
            .isNull()
        assertThat(guidance(lift.copy(exerciseId = "bicep-curl"), past = listOf(history("hammer-curl"))).reference)
            .isNull()
    }

    @Test fun referenceMustIndependentlyMatchTheCatalogAndCurrentProfileRestrictions() {
        val seated = lift.copy(exerciseId = "dumbbell-shoulder-press")
        val standing = "standing-dumbbell-press"
        val past = listOf(history(standing))
        val avoidStanding = profile.copy(movementCapabilities = MovementCapabilities.from(
            mapOf(MovementCapabilityType.BALANCE_WITHOUT_SUPPORT to CapabilityLevel.AVOID)
        ))
        val result = guidance(seated, owner = avoidStanding, past = past)
        assertThat(result.reference).isNull()
        assertThat(result.rackChoices).containsExactly(5.0, 10.0, 15.0).inOrder()
        assertThat(FirstWeightGuidancePolicy.comparableExerciseIds(seated.exerciseId)).containsExactly(standing)
        val wrongId = mapOf(standing to catalog.getValue(seated.exerciseId))
        val wrongEquipment = mapOf(standing to catalog.getValue(standing).copy(listedEquipment = listOf("Barbell")))
        for (entries in listOf(emptyMap(), wrongId, wrongEquipment)) {
            assertThat(guidance(seated, past = past, sourceCatalog = entries).reference).isNull()
        }
    }

    @Test fun boundedHistorySuppressesChoicesUntilTheEntirePainLookbackIsCovered() {
        val earliest = active.startedAtTimestamp - 28L * 24 * 60 * 60 * 1000
        val past = listOf(history())
        assertThat(guidance(past = past, oldestUnexaminedSession = earliest)).isEqualTo(FirstWeightGuidance(perDumbbell = true))
        assertThat(guidance(past = past, oldestUnexaminedSession = earliest - 1)).isEqualTo(guidance(past = past))
    }

    @Test fun onlyRecentCompletedNormalSetsMatchingRepsAndEffortAreComparable() {
        val invalid = listOf(
            source.copy(completedAtTimestamp = null), source.copy(completedAtTimestamp = active.startedAtTimestamp),
            source.copy(workoutExerciseId = "other"), source.copy(type = SetType.WARMUP),
            source.copy(stopReason = SetStopReason.USER_SKIPPED), source.copy(isCompleted = false),
            source.copy(completedWeight = Double.NaN), source.copy(completedReps = 7), source.copy(rir = 1),
            source.copy(rpe = 9f), source.copy(feltManageable = false), source.copy(completedAssistanceWeight = 1.0)
        )
        for (set in invalid) assertThat(guidance(past = listOf(history(set = set))).reference).isNull()
        val stale = history(set = source.copy(completedAtTimestamp = 15)).copy(startedAtTimestamp = 10, completedAtTimestamp = 20)
        val overlapping = history().copy(completedAtTimestamp = active.startedAtTimestamp)
        for (session in listOf(stale, overlapping, history().copy(status = SessionStatus.CANCELLED))) {
            assertThat(guidance(past = listOf(session)).reference).isNull()
        }
    }

    @Test fun painExclusionsAndUnverifiedBoundsLeaveManualEntryAvailable() {
        val manual = FirstWeightGuidance(perDumbbell = true)
        val pain = source.copy(isCompleted = false, completedAtTimestamp = null,
            stopReason = SetStopReason.PAIN_STOP, stoppedAtTimestamp = active.startedAtTimestamp - 9)
        assertThat(guidance(past = listOf(history(set = pain)))).isEqualTo(manual)
        assertThat(guidance(pending = true)).isEqualTo(manual)
        assertThat(guidance(owner = profile.copy(excludedExerciseIds = listOf(lift.exerciseId)))).isEqualTo(manual)
        assertThat(guidance(owner = profile.copy(reportedConditions = setOf("unrecognized")))).isEqualTo(manual)
        val condition = SelectedCondition("condition", 1, emptyMap())
        assertThat(guidance(owner = profile.copy(activeConditions = listOf(condition)))).isEqualTo(manual)
        assertThat(guidance(lift.copy(prescription = lift.prescription.copy(purpose = PrescriptionPurpose.SUPPORTIVE)))).isEqualTo(manual)
        assertThat(guidance(owner = profile.copy(availableEquipment = listOf(StandardEquipment.DUMBBELL)))).isEqualTo(manual)
    }
}
