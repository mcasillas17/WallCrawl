package wallcrawl.elopenmike.com.core.conditions

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wallcrawl.elopenmike.com.core.ai.FakeWorkoutPlanner
import wallcrawl.elopenmike.com.core.ai.ProgramValidationResult
import wallcrawl.elopenmike.com.core.ai.validate
import wallcrawl.elopenmike.com.core.ai.ProgramViolationCode
import wallcrawl.elopenmike.com.core.ai.WorkoutDurationEstimator
import wallcrawl.elopenmike.com.core.conditions.MovementDemand.CROSS_BODY_HIP_STRETCH
import wallcrawl.elopenmike.com.core.conditions.MovementDemand.HEAVY_EXTERNAL_LOAD
import wallcrawl.elopenmike.com.core.conditions.MovementDemand.NERVE_TENSIONING
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.AiReviewProvenance
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext

class MobilityBlockPolicyTest {

    private val profile = UserProfile(availableEquipment = listOf(StandardEquipment.BODYWEIGHT))
    private val catalog = listOf("a", "b", "c", "d", "e").associateWith { stretch(it) }

    @Test
    fun noConditionGivesAnEmptyBlock() {
        assertThat(select(ConditionRestrictions.None)).isEmpty()
        assertThat(select(ConditionRestrictions.Unavailable)).isEmpty()
        // A joint label alone is Active with no condition ids, so it contributes nothing.
        assertThat(select(active(conditions = emptyMap(), ids = emptySet()))).isEmpty()
    }

    @Test
    fun conditionsContributeInStableIdOrderThenAuthoredOrder() {
        val restrictions = active(
            conditions = mapOf("z" to record("z", listOf("a", "b")), "m" to record("m", listOf("c", "d"))),
            ids = setOf("z", "m")
        )

        assertThat(ids(select(restrictions, seed = 0))).containsExactly("c", "d", "a").inOrder()
    }

    @Test
    fun rotationAdvancesWithSeedAndIsDeterministic() {
        val restrictions = active(mapOf("c1" to record("c1", listOf("a", "b", "c", "d", "e"))))

        assertThat(ids(select(restrictions, seed = 0))).containsExactly("a", "b", "c").inOrder()
        assertThat(ids(select(restrictions, seed = 1))).containsExactly("b", "c", "d").inOrder()
        assertThat(ids(select(restrictions, seed = 4))).containsExactly("e", "a", "b").inOrder()
        assertThat(ids(select(restrictions, seed = 5))).isEqualTo(ids(select(restrictions, seed = 0)))
        assertThat(select(restrictions, seed = 7)).isEqualTo(select(restrictions, seed = 7))
        // A negative seed still lands on a valid offset.
        assertThat(ids(select(restrictions, seed = -1))).containsExactly("e", "a", "b").inOrder()
    }

    @Test
    fun blockHoldsAtMostThreeItems() {
        val restrictions = active(mapOf("c1" to record("c1", listOf("a", "b", "c", "d", "e"))))

        assertThat(select(restrictions)).hasSize(3)
    }

    @Test
    fun duplicateAcrossConditionsAppearsOnce() {
        val restrictions = active(
            mapOf("c1" to record("c1", listOf("a", "b")), "c2" to record("c2", listOf("b", "c"))),
            ids = setOf("c1", "c2")
        )

        assertThat(ids(select(restrictions))).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun itemAlreadySelectedAsMainWorkIsDropped() {
        val restrictions = active(mapOf("c1" to record("c1", listOf("a", "b", "c", "d"))))

        assertThat(ids(select(restrictions, main = setOf("a")))).containsExactly("b", "c", "d").inOrder()
    }

    @Test
    fun exclusionsEquipmentAndAvoidDemandsFilterTheBlock() {
        val bandOnly = stretch("band", equipment = listOf(StandardEquipment.RESISTANCE_BAND))
        val restrictions = active(
            mapOf("c1" to record("c1", listOf("a", "band", "b", "c", "d"), avoid = setOf(NERVE_TENSIONING))),
            demands = mapOf("c" to setOf(NERVE_TENSIONING))
        )

        val block = select(
            restrictions,
            exercises = catalog + ("band" to bandOnly),
            userProfile = profile.copy(excludedExerciseIds = listOf("a"))
        )

        // a is excluded, band needs equipment, c is avoided: filtering happens before the cut.
        assertThat(ids(block)).containsExactly("b", "d").inOrder()
    }

    @Test
    fun modifyDemandsDoNotFilterTheBlock() {
        val restrictions = active(
            mapOf("c1" to record("c1", listOf("a", "b"), modify = setOf(HEAVY_EXTERNAL_LOAD))),
            demands = mapOf("a" to setOf(HEAVY_EXTERNAL_LOAD))
        )

        assertThat(ids(select(restrictions))).containsExactly("a", "b").inOrder()
    }

    @Test
    fun avoidDemandFromAnotherConditionRemovesARoutineItem() {
        val restrictions = active(
            mapOf(
                "c1" to record("c1", listOf("a", "b")),
                "c2" to record("c2", emptyList<String>(), avoid = setOf(CROSS_BODY_HIP_STRETCH))
            ),
            ids = setOf("c1", "c2"),
            demands = mapOf("a" to setOf(CROSS_BODY_HIP_STRETCH))
        )

        assertThat(ids(select(restrictions))).containsExactly("b")
    }

    @Test
    fun holdAndRestReachThePlannedExercise() {
        val restrictions = active(
            mapOf(
                "c1" to recordOf(
                    "c1",
                    entries = listOf(
                        RoutineEntry("a", 2, 5, null, 10, 45),
                        RoutineEntry("b", 1, null, 30, null, 0),
                        RoutineEntry("c", 3, 8, null, null, 0)
                    )
                )
            )
        )

        val (a, b, c) = select(restrictions)

        assertThat(a.prescription.exerciseType).isEqualTo(ExerciseType.BODYWEIGHT_REPS)
        assertThat(a.prescription.holdSeconds).isEqualTo(10)
        assertThat(a.prescription.repRange!!.min).isEqualTo(5)
        assertThat(a.prescription.repRange!!.max).isEqualTo(5)
        assertThat(a.prescription.restSeconds).isEqualTo(45)
        assertThat(b.prescription.exerciseType).isEqualTo(ExerciseType.DURATION)
        assertThat(b.prescription.targetDurationSeconds).isEqualTo(30)
        // Zero rest means none was published, so the app default applies.
        assertThat(b.prescription.restSeconds).isEqualTo(ExercisePrescription.DEFAULT_REST_SECONDS)
        assertThat(c.prescription.restSeconds).isEqualTo(ExercisePrescription.DEFAULT_REST_SECONDS)
        assertThat(c.prescription.holdSeconds).isNull()
    }

    @Test
    fun emptyResultNeverFailsThePlan() = runTest {
        val restrictions = active(mapOf("c1" to record("c1", listOf("zzz-unknown"))))

        val workout = FakeWorkoutPlanner().generateWorkout(context(restrictions))

        assertThat(workout.mobilityBlock).isEmpty()
        assertThat(workout.exercises).isNotEmpty()
    }

    @Test
    fun stretchesStillNeverEnterMainSelection() = runTest {
        val restrictions = active(mapOf("c1" to record("c1", listOf("a", "b", "c"))))

        val workout = FakeWorkoutPlanner().generateWorkout(context(restrictions))

        assertThat(ids(workout.mobilityBlock)).containsExactly("a", "b", "c")
        assertThat(workout.exercises.map { it.exerciseId }).containsNoneIn(catalog.keys)
    }

    @Test
    fun estimateIncludesTheBlock() = runTest {
        val restrictions = active(mapOf("c1" to record("c1", listOf("a", "b", "c"))))
        val planner = FakeWorkoutPlanner()

        val withBlock = planner.generateWorkout(context(restrictions, regenerationIndex = 0))
        val without = planner.generateWorkout(context(ConditionRestrictions.None, regenerationIndex = 0))

        assertThat(withBlock.estimatedDurationMinutes).isEqualTo(
            WorkoutDurationEstimator.estimateMinutes(withBlock.exercises + withBlock.mobilityBlock)
        )
        assertThat(withBlock.estimatedDurationMinutes).isGreaterThan(without.estimatedDurationMinutes)
    }

    @Test
    fun noConditionsLeaveThePlanUnchanged() = runTest {
        val workout = FakeWorkoutPlanner().generateWorkout(context(ConditionRestrictions.None, regenerationIndex = 0))

        assertThat(workout.mobilityBlock).isEmpty()
        assertThat(workout.estimatedDurationMinutes)
            .isEqualTo(WorkoutDurationEstimator.estimateMinutes(workout.exercises))
    }

    @Test
    fun eachViolationIsReportedAndNotRepairable() = runTest {
        val restrictions = active(
            mapOf("c1" to record("c1", listOf("a", "b", "c", "d"), avoid = setOf(NERVE_TENSIONING))),
            demands = mapOf("b" to setOf(NERVE_TENSIONING))
        )
        val ctx = context(restrictions, regenerationIndex = 0)
        val good = FakeWorkoutPlanner().generateWorkout(ctx)

        suspend fun codes(block: List<PlannedExercise>): List<ProgramViolationCode> {
            val bad = good.copy(
                mobilityBlock = block,
                estimatedDurationMinutes = WorkoutDurationEstimator.estimateMinutes(good.exercises + block)
            )
            val result = validate(bad, ctx, allowRepair = true)
            assertThat(result).isInstanceOf(ProgramValidationResult.Invalid::class.java)
            return (result as ProgramValidationResult.Invalid).violations.map { it.code }
        }

        fun plan(id: String) = PlannedExercise(id, 1, 5, 5)
        assertThat(validate(good, ctx))
            .isInstanceOf(ProgramValidationResult.Valid::class.java)
        assertThat(codes(listOf(plan("a"), plan("elsewhere"))))
            .contains(ProgramViolationCode.MOBILITY_ENTRY_NOT_IN_ROUTINE)
        assertThat(codes(listOf(plan("b")))).contains(ProgramViolationCode.MOBILITY_ENTRY_RESTRICTED)
        assertThat(codes(listOf(plan("a"), plan("c"), plan("d"), plan("e"))))
            .contains(ProgramViolationCode.MOBILITY_BLOCK_TOO_LONG)
    }

    // region fixtures

    private fun select(
        restrictions: ConditionRestrictions,
        exercises: Map<String, Exercise> = catalog,
        userProfile: UserProfile = profile,
        main: Set<String> = emptySet(),
        seed: Long = 0
    ) = MobilityBlockPolicy.select(restrictions, exercises, userProfile, main, seed)

    private fun ids(block: List<PlannedExercise>) = block.map { it.exerciseId }

    private fun context(
        restrictions: ConditionRestrictions,
        regenerationIndex: Int? = 0
    ): WorkoutGenerationContext {
        val strength = InMemoryExerciseCatalog.SAMPLE_EXERCISES.filter { !it.isStretch }
        return WorkoutGenerationContext(
            userProfile = profile.copy(goals = setOf(FitnessGoal.BUILD_MUSCLE)),
            allowedExercises = strength + catalog.values,
            conditionRestrictions = restrictions,
            regenerationIndex = regenerationIndex
        )
    }

    private fun stretch(id: String, equipment: List<String> = listOf(StandardEquipment.BODYWEIGHT)) = Exercise(
        id = id,
        name = id,
        primaryMuscles = listOf(StandardMuscles.GLUTES),
        listedEquipment = equipment,
        type = ExerciseType.DURATION,
        isStretch = true
    )

    private fun active(
        conditions: Map<String, ConditionRecord>,
        ids: Set<String> = conditions.keys,
        demands: Map<String, Set<MovementDemand>> = emptyMap()
    ): ConditionRestrictions.Active {
        val guidance = ConditionGuidance(
            version = "test",
            exerciseDemands = demands,
            conditions = conditions,
            jointLabelDemands = emptyMap(),
            demandCues = emptyMap()
        )
        return ConditionRestrictions.Active(
            avoid = ids.flatMapTo(mutableSetOf()) { conditions.getValue(it).avoidDemands },
            modify = ids.flatMapTo(mutableSetOf()) { conditions.getValue(it).modifyDemands },
            guidance = guidance,
            conditionIds = ids
        )
    }

    private fun record(
        id: String,
        routine: List<String>,
        avoid: Set<MovementDemand> = emptySet(),
        modify: Set<MovementDemand> = emptySet()
    ) = recordOf(id, routine.map { RoutineEntry(it, 2, 8, null, null, 30) }, avoid, modify)

    private fun recordOf(
        id: String,
        entries: List<RoutineEntry>,
        avoid: Set<MovementDemand> = emptySet(),
        modify: Set<MovementDemand> = emptySet()
    ) = ConditionRecord(
        id = id,
        avoidDemands = avoid,
        modifyDemands = modify,
        routine = entries,
        exerciseCues = emptyMap(),
        notes = emptyMap(),
        redFlags = emptyMap(),
        evidenceGrade = "C",
        sources = emptyList(),
        provenance = AiReviewProvenance(
            reviewerModelId = "test",
            reviewedAtEpochMillis = 1L,
            reviewedContentId = "x",
            reviewedContentSha256 = "0",
            sourceReferences = emptyList(),
            decisionRationale = "t",
            limitations = "t",
            schemaVersion = 1,
            policyVersion = 1
        )
    )

    // endregion
}
