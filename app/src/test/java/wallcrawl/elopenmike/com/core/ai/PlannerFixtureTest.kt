package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.AdaptationState
import wallcrawl.elopenmike.com.core.model.SupportRequirement
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseMetadata
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseLink
import wallcrawl.elopenmike.com.core.model.ReviewProvenance
import wallcrawl.elopenmike.com.core.model.PrescriptionShape
import wallcrawl.elopenmike.com.core.model.ImpactLevel
import wallcrawl.elopenmike.com.core.model.EligibilityPreference
import wallcrawl.elopenmike.com.core.model.ComplexityTier
import wallcrawl.elopenmike.com.core.model.SessionProgramConstraints
import wallcrawl.elopenmike.com.core.model.EligibilityReason
import wallcrawl.elopenmike.com.core.model.EligibilityDecision
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityFailure
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityResult
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.CapabilityEvidence
import wallcrawl.elopenmike.com.core.model.CapabilityEvidencePolicyVersion
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceReason
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceScope
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceSet
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.ComparableMovementShape
import wallcrawl.elopenmike.com.core.model.Difficulty
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseAttribution
import wallcrawl.elopenmike.com.core.model.ExerciseAttributionSource
import wallcrawl.elopenmike.com.core.model.ExercisePerformanceHistory
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseProgrammingMetadata
import wallcrawl.elopenmike.com.core.model.ExerciseSource
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.LedgerOmissionReason
import wallcrawl.elopenmike.com.core.model.WeeklyDoseLedger
import wallcrawl.elopenmike.com.core.model.TrainingProgramStatePolicyVersion
import wallcrawl.elopenmike.com.core.model.TrainingProgramState
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.MechanicsType
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.MovementPattern
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.ProgressionType
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.EffortTarget
import wallcrawl.elopenmike.com.core.model.RestTargetSource
import wallcrawl.elopenmike.com.core.model.UserRestPreference
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet

class PlannerFixtureTest {

    private val evaluator = SharedPlannerFixtureHarness.evaluator
    private val prescriptionFactory = DefaultExercisePrescriptionFactory()

    private fun corpus(): List<PlannerFixture> = SharedPlannerFixtureHarness.corpus

    @Test
    fun evaluateCorpus_runsEveryPlannerFixture() = runTest {
        val evaluations = SHARED_CORPUS_EVALUATIONS

        assertThat(evaluations.map { it.built.fixture.id }).containsExactly(
            "bodyweight-beginner",
            "band-only",
            "machine-only",
            "full-gym-advanced",
            "returning-user",
            "limited-capability",
            "mixed-unit-history",
            "sparse-history",
            "no-strength-candidates",
            "reviewed-enabled-bodyweight",
            "reviewed-enabled-no-approved",
            "concurrent-activity"
        )
    }

    @Test
    fun evaluateCorpus_enforcesDeterminismAndPlannerInvariants() = runTest {
        val evaluations = SHARED_CORPUS_EVALUATIONS

        evaluations.forEach { evaluation ->
            assertThat(evaluation.inputAfterFirstAttempt).isEqualTo(evaluation.inputBefore)
            assertThat(evaluation.inputAfterSecondAttempt).isEqualTo(evaluation.inputBefore)

            when (evaluation) {
                is PlannerFixtureSuccessEvaluation -> assertSuccessfulFixture(evaluation)
                is PlannerFixtureFailureEvaluation -> assertFailureFixture(evaluation)
            }
        }
    }

    @Test
    fun evaluateCorpus_validatesTheWholeProposalAgainstTheContextThatProducedIt() = runTest {
        val successes = SHARED_CORPUS_EVALUATIONS
            .filterIsInstance<PlannerFixtureSuccessEvaluation>()

        assertThat(successes).isNotEmpty()
        successes.forEach { evaluation ->
            val id = evaluation.built.fixture.id
            val expected = evaluation.built.fixture.expected
            val raw = evaluation.firstValidation.raw
            val displayed = evaluation.firstValidation.displayed

            when (expected.wholeProgramOutcome) {
                RecommendationOutcome.VALID -> {
                    assertWithMessage("$id raw violations").that(raw.codes()).isEmpty()
                    assertWithMessage(id).that(displayed.acceptedSnapshot.outcome)
                        .isEqualTo(RecommendationOutcome.VALID)
                    // Permitting repair changed nothing, so what would be displayed is the
                    // raw plan rather than a silently different one.
                    assertWithMessage(id).that(displayed.acceptedWorkout.exercises)
                        .isEqualTo(evaluation.firstWorkout.exercises)
                }

                RecommendationOutcome.REPAIRED -> {
                    // The raw proposal really was rejected. A repaired plan is never
                    // evidence that the planner's own output was valid.
                    assertWithMessage("$id raw violations").that(raw.codes())
                        .isEqualTo(expected.wholeProgramRepairReasonCodes)
                    assertWithMessage(id).that(displayed.acceptedSnapshot.outcome)
                        .isEqualTo(RecommendationOutcome.REPAIRED)
                    assertWithMessage(id).that(displayed.acceptedSnapshot.reasonCodes)
                        .isEqualTo(expected.wholeProgramRepairReasonCodes)
                }
            }

            // Whatever repair did, it only ever reduced sets, and never below one.
            val rawSets = evaluation.firstWorkout.exercises.associate {
                it.exerciseId to it.targetSets
            }
            displayed.acceptedWorkout.exercises.forEach { planned ->
                val before = checkNotNull(rawSets[planned.exerciseId])
                assertWithMessage("$id ${planned.exerciseId}")
                    .that(planned.targetSets).isAtMost(before)
                assertWithMessage("$id ${planned.exerciseId}")
                    .that(planned.targetSets).isAtLeast(1)
            }
            assertWithMessage(id).that(displayed.acceptedWorkout.exercises.map { it.exerciseId })
                .isEqualTo(evaluation.firstWorkout.exercises.map { it.exerciseId })
        }
    }

    @Test
    fun bodyweightBeginnerPersona_keepsExperienceRankingSoftAndInventsNoLoad() = runTest {
        val evaluation = sharedSuccess("bodyweight-beginner")
        val catalogById = evaluation.built.catalogExercises.associateBy(Exercise::id)
        val selectedIds = evaluation.firstWorkout.exercises.map { it.exerciseId }
        val allowedIds = evaluation.built.context.allowedExercises.map(Exercise::id).toSet()
        val difficultyPolicy = ExerciseDifficultyRankingPolicy()

        assertThat(evaluation.built.userProfile.experienceLevel)
            .isEqualTo(ExperienceLevel.BEGINNER)
        assertThat(allowedIds.containsAll(selectedIds)).isTrue()
        assertThat(evaluation.built.context.capabilityEvidence)
            .isEqualTo(CapabilityEvidenceSet.empty())
        evaluation.firstWorkout.exercises.forEach { generated ->
            val exercise = checkNotNull(catalogById[generated.exerciseId])
            assertThat(
                difficultyPolicy.aboveExperiencePenalty(
                    exercise = exercise,
                    experienceLevel = ExperienceLevel.BEGINNER,
                    reviewedEligibilityEnabled = false
                )
            ).isEqualTo(0)
            assertThat(generated.prescription.targetWeight).isNull()
        }
    }

    @Test
    fun fullGymAdvancedPersona_preservesLegalPoolAndExistingLoadRules() = runTest {
        val evaluation = sharedSuccess("full-gym-advanced")
        val catalogById = evaluation.built.catalogExercises.associateBy(Exercise::id)
        val selectedIds = evaluation.firstWorkout.exercises.map { it.exerciseId }
        val allowedIds = evaluation.built.context.allowedExercises.map(Exercise::id).toSet()
        val difficultyPolicy = ExerciseDifficultyRankingPolicy()

        assertThat(evaluation.built.userProfile.experienceLevel)
            .isEqualTo(ExperienceLevel.ADVANCED)
        assertThat(allowedIds.containsAll(selectedIds)).isTrue()
        assertThat(
            evaluation.built.context.allowedExercises.any {
                it.programming?.difficulty == Difficulty.ADVANCED
            }
        ).isTrue()
        evaluation.firstWorkout.exercises.forEach { generated ->
            val exercise = checkNotNull(catalogById[generated.exerciseId])
            assertThat(
                difficultyPolicy.aboveExperiencePenalty(
                    exercise = exercise,
                    experienceLevel = ExperienceLevel.ADVANCED,
                    reviewedEligibilityEnabled = false
                )
            ).isEqualTo(0)
            if (
                exercise.type == ExerciseType.WEIGHT_REPS &&
                exercise.id !in evaluation.built.context.exerciseHistory &&
                exercise.id !in evaluation.built.userProfile.confirmedStartingLoads
            ) {
                assertThat(generated.prescription.targetWeight).isNull()
            }
        }
        assertThat(
            evaluation.firstWorkout.exercises.single {
                it.exerciseId == "barbell-bench-press"
            }.prescription.targetWeight
        ).isEqualTo(185.0)
    }

    @Test
    fun concurrentActivityPersona_creditsResistanceWorkAndTypesTheAerobicWorkItCannotCredit() =
        runTest {
            val evaluation = sharedSuccess("concurrent-activity")
            val ledger = checkNotNull(evaluation.built.context.trainingProgramState).weeklyLedger

            // Ten completed work sets across two approved chest exercises. The logged warm-up
            // and the set that was never finished are not exposure and are simply absent.
            assertThat(ledger.directPrimarySets).containsExactly(StandardMuscles.CHEST, 10)
            assertThat(ledger.creditedWorkSets).isEqualTo(10)
            // Descriptive involvement is recorded beside the dose and never inside it.
            assertThat(ledger.secondaryInvolvement)
                .containsExactly(StandardMuscles.SHOULDERS, 10, StandardMuscles.TRICEPS, 10)
            assertThat(ledger.secondaryInvolvement.keys).doesNotContain(StandardMuscles.CHEST)
            // The cycling session's two completed sets are counted, and typed as work whose
            // muscle this policy will not guess at, rather than credited to the legs.
            assertThat(ledger.unattributedWorkSets)
                .containsExactly(LedgerOmissionReason.MISSING_REVIEWED_METADATA, 2)
            assertThat(ledger.omittedWorkSets).isEqualTo(2)
        }

    @Test
    fun concurrentActivityPersona_keepsUnsupportedActivityOutOfAutomaticStrengthSlots() = runTest {
        val evaluation = sharedSuccess("concurrent-activity")
        val catalogById = evaluation.built.catalogExercises.associateBy(Exercise::id)

        // The persona genuinely owns cardio equipment, so the real filter keeps this entry:
        // it stays browsable and usable in a manual template.
        assertThat(evaluation.built.filteredExercises.map(Exercise::id)).contains("cycling")
        // It is still not automatic strength work. On this reviewed-enabled persona the
        // reviewed gate is what removes it, because it carries no approved metadata at all.
        assertThat(evaluation.built.context.allowedExercises.map(Exercise::id))
            .doesNotContain("cycling")
        evaluation.firstWorkout.exercises.forEach { planned ->
            val exercise = checkNotNull(catalogById[planned.exerciseId])
            assertThat(exercise.type).isNotEqualTo(ExerciseType.DISTANCE_DURATION)
            assertThat(exercise.listedEquipment).doesNotContain(StandardEquipment.CARDIO)
        }
    }

    @Test
    fun concurrentActivityPersona_isNotChangedByTheAerobicSessionAlone() = runTest {
        val fixture = corpus().single { it.id == "concurrent-activity" }
        // Only the aerobic record is removed. The lifetime workout counter is held constant
        // on purpose: adding or removing a completed session legitimately rotates the split,
        // and that would be a counter effect rather than anything about the activity.
        val withoutAerobicWork = fixture.copy(
            completedSessions = fixture.completedSessions.filterNot { it.id == "aerobic-thursday" }
        )

        val withAerobic = sharedSuccess("concurrent-activity")
        val control = evaluator.evaluateFixture(withoutAerobicWork)
            as PlannerFixtureSuccessEvaluation
        val controlLedger = checkNotNull(control.built.context.trainingProgramState).weeklyLedger
        val ledger = checkNotNull(withAerobic.built.context.trainingProgramState).weeklyLedger

        assertThat(controlLedger.directPrimarySets).isEqualTo(ledger.directPrimarySets)
        assertThat(controlLedger.secondaryInvolvement).isEqualTo(ledger.secondaryInvolvement)
        assertThat(controlLedger.unattributedWorkSets).isEmpty()
        assertThat(control.firstWorkout.normalizedPlannerFixtureWorkout())
            .isEqualTo(withAerobic.firstWorkout.normalizedPlannerFixtureWorkout())
        assertThat(control.firstValidation.raw.codes())
            .isEqualTo(withAerobic.firstValidation.raw.codes())
    }

    @Test
    fun concurrentActivityPersona_isChangedWhenTheResistanceSessionIsRemoved() = runTest {
        // The sensitivity control for the assertion above: if removing logged resistance work
        // also changed nothing, the previous test would be proving nothing about aerobic work.
        val fixture = corpus().single { it.id == "concurrent-activity" }
        val withoutResistanceWork = fixture.copy(
            completedSessions = fixture.completedSessions
                .filterNot { it.id == "resistance-tuesday" }
        )

        val withResistance = sharedSuccess("concurrent-activity")
        val control = evaluator.evaluateFixture(withoutResistanceWork)
            as PlannerFixtureSuccessEvaluation
        val controlLedger = checkNotNull(control.built.context.trainingProgramState).weeklyLedger

        assertThat(controlLedger.directPrimarySets).isEmpty()
        // With the week's chest allowance untouched, the same proposal is no longer over it.
        assertThat(withResistance.firstValidation.raw.codes())
            .containsExactly(ProgramViolationCode.WEEKLY_ALLOWANCE_EXCEEDED)
        assertThat(control.firstValidation.raw.codes()).isEmpty()
        assertThat(control.firstWorkout.normalizedPlannerFixtureWorkout())
            .isNotEqualTo(withResistance.firstWorkout.normalizedPlannerFixtureWorkout())
    }

    @Test
    fun concurrentActivityPersona_readsTheWeekRatherThanTheTimeInsideIt() = runTest {
        // Nothing infers readiness, overload or recovery from when in the week work happened.
        // Moving both sessions to other days of the same ISO week must change nothing at all.
        val fixture = corpus().single { it.id == "concurrent-activity" }
        val movedWithinTheWeek = fixture.copy(
            completedSessions = fixture.completedSessions.map { session ->
                // `+2 mod 7` has no fixed point, so every declared session really moves.
                // `6 - offset` did not: the Thursday aerobic session sat on offset 3 and
                // stayed exactly where it was.
                session.copy(completedDayOffset = (session.completedDayOffset + 2) % 7)
            }
        )

        val original = sharedSuccess("concurrent-activity")
        val moved = evaluator.evaluateFixture(movedWithinTheWeek) as PlannerFixtureSuccessEvaluation

        assertThat(checkNotNull(moved.built.context.trainingProgramState).weeklyLedger)
            .isEqualTo(checkNotNull(original.built.context.trainingProgramState).weeklyLedger)
        assertThat(moved.firstValidation.raw.codes())
            .isEqualTo(original.firstValidation.raw.codes())
        assertThat(moved.firstWorkout.normalizedPlannerFixtureWorkout())
            .isEqualTo(original.firstWorkout.normalizedPlannerFixtureWorkout())
    }

    @Test
    fun theContinuousActivityAnswerIsNotARecordOfAerobicActivity() = runTest {
        // The capability answer says how the user feels about sustained work. It is not a log
        // of anything they did, so it can neither add nor remove a single counted set.
        //
        // This is not the same claim as the aerobic-session control above, and neither
        // subsumes the other: that one varies the *record* of activity, this one varies the
        // *answer* about it. Keeping them apart is the point — an implementation that read
        // the capability answer as evidence of training would pass the aerobic control and
        // fail here. Whether aerobic work is credited at all is asserted separately, by
        // concurrentActivityPersona_creditsResistanceWorkAndTypesTheAerobicWorkItCannotCredit.
        // The sensitivity control for this one is
        // aCapabilityTheReviewedGateDoesReadChangesTheProposal, which proves capability
        // answers are not simply inert.
        val fixture = corpus().single { it.id == "concurrent-activity" }
        val avoidsContinuousActivity = fixture.copy(
            profile = fixture.profile.copy(
                movementCapabilities = MovementCapabilities.from(
                    mapOf(MovementCapabilityType.CONTINUOUS_ACTIVITY to CapabilityLevel.AVOID)
                )
            )
        )

        val comfortable = sharedSuccess("concurrent-activity")
        val avoiding = evaluator.evaluateFixture(avoidsContinuousActivity)
            as PlannerFixtureSuccessEvaluation

        assertThat(checkNotNull(avoiding.built.context.trainingProgramState).weeklyLedger)
            .isEqualTo(checkNotNull(comfortable.built.context.trainingProgramState).weeklyLedger)
        assertThat(avoiding.firstWorkout.normalizedPlannerFixtureWorkout())
            .isEqualTo(comfortable.firstWorkout.normalizedPlannerFixtureWorkout())
    }

    @Test
    fun aCapabilityTheReviewedGateDoesReadChangesTheProposal() = runTest {
        // The sensitivity control for the assertion above. `CONTINUOUS_ACTIVITY` changes
        // nothing because nothing in this persona's pool requires it — not because capability
        // answers are inert. `BALANCE_WITHOUT_SUPPORT` is required by `dumbbell-lateral-raise`,
        // so avoiding it must visibly change the candidate set and the plan. Without this,
        // the invariance above could be read as proof that the harness ignores capabilities.
        val fixture = corpus().single { it.id == "concurrent-activity" }
        val avoidsBalance = fixture.copy(
            profile = fixture.profile.copy(
                movementCapabilities = MovementCapabilities.from(
                    mapOf(
                        MovementCapabilityType.CONTINUOUS_ACTIVITY to CapabilityLevel.COMFORTABLE,
                        MovementCapabilityType.BALANCE_WITHOUT_SUPPORT to CapabilityLevel.AVOID
                    )
                )
            )
        )

        val baseline = sharedSuccess("concurrent-activity")
        val avoiding = evaluator.evaluateFixture(avoidsBalance) as PlannerFixtureSuccessEvaluation

        assertThat(baseline.built.context.allowedExercises.map(Exercise::id))
            .contains("dumbbell-lateral-raise")
        assertThat(avoiding.built.context.allowedExercises.map(Exercise::id))
            .doesNotContain("dumbbell-lateral-raise")
        assertThat(avoiding.firstWorkout.normalizedPlannerFixtureWorkout())
            .isNotEqualTo(baseline.firstWorkout.normalizedPlannerFixtureWorkout())
    }

    @Test
    fun weeklyDoseIsCountedInSetsRatherThanDerivedFromFatigueScores() = runTest {
        // The prohibited interpretation is the legacy ordinal `programming.fatigueScore`
        // becoming a summed physiological budget. The ordinal's real role in ranking is not
        // asserted against — it is legitimate and unchanged.
        //
        // This is checked by recomputing both halves of the accounting from set counts alone
        // and requiring equality, so any arithmetic that mixed a fatigue value into dose
        // breaks here. The earlier form of this test compared two validations that differed
        // only in `fatigueScore`, which the validator never reads: it could not fail.
        val evaluation = sharedSuccess("concurrent-activity")
        val fixture = evaluation.built.fixture
        val catalogById = evaluation.built.catalogExercises.associateBy(Exercise::id)
        val accounting = evaluation.firstValidation.displayed.acceptedSnapshot.doseAccounting
        assertThat(accounting).isNotEmpty()

        // Completed exposure, recomputed straight from the declared history: one credit per
        // completed non-warm-up set of an approved exercise, and nothing else.
        val expectedCompletedByMuscle = mutableMapOf<String, Int>()
        fixture.completedSessions.forEach { session ->
            session.exercises.forEach { logged ->
                val muscle = catalogById[logged.exerciseId]
                    ?.reviewedMetadata
                    ?.takeIf { it.reviewState == ReviewState.APPROVED }
                    ?.directPrimaryMuscle
                    ?: return@forEach
                val creditable = logged.sets.count {
                    it.isCompleted && it.type != SetType.WARMUP
                }
                expectedCompletedByMuscle.merge(muscle, creditable, Int::plus)
            }
        }

        // Proposed exposure, recomputed from the plan the accounting actually describes.
        // That is the repaired plan for this persona, not the raw one: the snapshot on a
        // REPAIRED result reports the second evaluation, and reading the raw target sets
        // here would be the very confusion this corpus exists to prevent.
        val accountedPlan = evaluation.firstValidation.displayed.acceptedWorkout
        val expectedProposedByMuscle = mutableMapOf<String, Int>()
        accountedPlan.exercises.forEach { planned ->
            val muscle = catalogById[planned.exerciseId]
                ?.reviewedMetadata
                ?.takeIf { it.reviewState == ReviewState.APPROVED }
                ?.directPrimaryMuscle
                ?: return@forEach
            expectedProposedByMuscle.merge(muscle, planned.targetSets, Int::plus)
        }

        accounting.forEach { muscle ->
            assertWithMessage("completed ${muscle.muscle}").that(muscle.completedSets)
                .isEqualTo(expectedCompletedByMuscle[muscle.muscle] ?: 0)
            assertWithMessage("proposed ${muscle.muscle}").that(muscle.proposedSets)
                .isEqualTo(expectedProposedByMuscle[muscle.muscle] ?: 0)
            // The allowance is the configured product number for this state, not a value
            // derived from anything the catalog says about the selected exercises.
            assertWithMessage("allowance ${muscle.muscle}").that(muscle.allowanceSets)
                .isEqualTo(
                    StateBasedTrainingPolicyDefaults.V1
                        .doseLimitsByState[AdaptationState.BUILD]
                        ?.maxWeeklyDirectPrimarySets
                )
        }

        // The fatigue sums the prohibited reading would have produced are genuinely
        // different numbers, so the equalities above are not accidentally satisfied by one.
        val completedFatigueSum = fixture.completedSessions.sumOf { session ->
            session.exercises.sumOf { logged ->
                val score = catalogById[logged.exerciseId]?.programming?.fatigueScore ?: 0
                score * logged.sets.count { it.isCompleted && it.type != SetType.WARMUP }
            }
        }
        assertThat(completedFatigueSum)
            .isNotEqualTo(accounting.sumOf { it.completedSets })
    }

    @Test
    fun prospectiveDoseAccountingIgnoresTheLegacyFatigueScore() = runTest {
        // The narrower companion to the assertion above, kept because it covers the one path
        // set-count equality cannot: validating an identical proposal against a context whose
        // candidates carry different ordinal labels must reach an identical verdict.
        val evaluation = sharedSuccess("concurrent-activity")
        val context = evaluation.built.context
        val maximumFatigue = context.copy(
            allowedExercises = context.allowedExercises.map { exercise ->
                exercise.copy(
                    programming = exercise.programming?.copy(fatigueScore = MAX_FATIGUE_SCORE)
                )
            }
        )
        // Without this the test could quietly compare a context with itself, which is the
        // exact defect the previous version of this assertion had.
        assertThat(maximumFatigue.allowedExercises).isNotEqualTo(context.allowedExercises)
        val validator = ProgramValidator(
            GeneratedWorkoutValidator(InMemoryExerciseCatalog(evaluation.built.catalogExercises))
        )

        val baseline = validator.validate(evaluation.firstWorkout, context, allowRepair = true)
        val loaded = validator.validate(evaluation.firstWorkout, maximumFatigue, allowRepair = true)

        assertThat(loaded.codes()).isEqualTo(baseline.codes())
        assertThat(loaded.acceptedSnapshot.doseAccounting)
            .isEqualTo(baseline.acceptedSnapshot.doseAccounting)
    }

    @Test
    fun aProposalWellUnderTheConfiguredAllowanceIsAcceptedWithoutAWeeklyMinimum() = runTest {
        // There is no scientific floor and no automatic increase: proposing far less than the
        // configured allowance is simply valid, and validation adds no sets to close the gap.
        val evaluation = sharedSuccess("reviewed-enabled-bodyweight")

        val accounting = evaluation.firstValidation.displayed.acceptedSnapshot.doseAccounting
        assertThat(accounting).isNotEmpty()
        accounting.forEach { muscle ->
            val allowance = checkNotNull(muscle.allowanceSets)
            assertThat(muscle.completedSets + muscle.proposedSets).isLessThan(allowance)
        }
        assertThat(evaluation.firstValidation.raw.codes()).isEmpty()
        assertThat(evaluation.firstValidation.displayed.acceptedWorkout.exercises)
            .isEqualTo(evaluation.firstWorkout.exercises)
    }

    @Test
    fun limitedCapabilityFixture_matchesAllComfortableCapabilitiesControl() = runTest {
        val limitedFixture = corpus().single { it.id == "limited-capability" }
        val comfortableControl = limitedFixture.copy(
            profile = limitedFixture.profile.copy(
                movementCapabilities = MovementCapabilities.from(
                    MovementCapabilityType.entries.associateWith { CapabilityLevel.COMFORTABLE }
                )
            )
        )

        val limited = evaluator.evaluateFixture(limitedFixture) as PlannerFixtureSuccessEvaluation
        val control = evaluator.evaluateFixture(comfortableControl) as PlannerFixtureSuccessEvaluation

        assertThat(limited.firstWorkout.normalizedPlannerFixtureWorkout())
            .isEqualTo(control.firstWorkout.normalizedPlannerFixtureWorkout())
        assertThat(limited.secondWorkout.normalizedPlannerFixtureWorkout())
            .isEqualTo(control.secondWorkout.normalizedPlannerFixtureWorkout())
    }

    @Test
    fun reviewedEnabledPersona_consumesProgramStateWithoutHardRuleOrLoadRegression() = runTest {
        val evaluation = sharedSuccess("reviewed-enabled-bodyweight")
        val context = evaluation.built.context
        val allowedIds = context.allowedExercises.map(Exercise::id).toSet()

        assertThat(context.trainingProgramState).isNotNull()
        assertThat(context.capabilityEvidence).isEqualTo(CapabilityEvidenceSet.empty())
        assertThat(
            context.trainingProgramState?.weeklyLedger?.directPrimarySets
        ).isEmpty()
        evaluation.firstWorkout.exercises.forEach { generated ->
            assertThat(allowedIds).contains(generated.exerciseId)
            assertThat(generated.prescription.effortTarget).isEqualTo(EffortTarget(1, 3))
            assertThat(generated.prescription.restClass).isNotNull()
            assertThat(generated.prescription.restTargetSource)
                .isEqualTo(RestTargetSource.PRODUCT_POLICY)
            assertThat(generated.prescription.targetWeight).isNull()
        }
        assertThat(evaluation.firstWorkout.normalizedPlannerFixtureWorkout())
            .isEqualTo(evaluation.secondWorkout.normalizedPlannerFixtureWorkout())
    }

    @Test
    fun corpusMetadata_usesSupportedVersionsAndBoundedHistory() {
        val fixtures = corpus()

        assertThat(fixtures).hasSize(12)
        fixtures.forEach { fixture ->
            assertThat(fixture.schemaVersion).isEqualTo(1)
            assertThat(fixture.policyVersion).isEqualTo(4)
            assertThat(fixture.catalogVersion)
                .isEqualTo("ba0b709cb20430361b2cb33aaadd20998164a916")
            assertThat(fixture.exerciseHistory.size).isAtMost(8)
        }
    }

    @Test
    fun plannerFixtureInputSnapshot_capturesEveryWorkoutGenerationContextField() {
        val snapshotFieldNames = PlannerFixtureInputSnapshot::class.java.declaredFields
            .map { it.name }
            .filterNot { it.startsWith("$") }
        val contextFieldNames = WorkoutGenerationContext::class.java.declaredFields
            .map { it.name }
            .filterNot { it.startsWith("$") }

        assertThat(snapshotFieldNames).containsExactlyElementsIn(contextFieldNames)
    }

    @Test
    fun plannerFixtureInputSnapshot_deepCopiesEveryMutableContextBranch() {
        val context = snapshotProbeContext()

        val snapshot = snapshotOf(context)
        val snapshotUserProfile = readField(snapshot, "userProfile")
        val snapshotRecentWorkoutHistory = readField(snapshot, "recentWorkoutHistory") as List<*>
        val snapshotExerciseHistory = readField(snapshot, "exerciseHistory") as Map<*, *>
        val snapshotAllowedExercises = readField(snapshot, "allowedExercises") as List<*>
        val snapshotCapabilityEvidence = readField(snapshot, "capabilityEvidence")
        val snapshotRestPreferences =
            readField(snapshot, "priorUserRestPreferences") as Map<*, *>

        assertThat(snapshotUserProfile).isEqualTo(context.userProfile)
        assertThat(snapshotUserProfile).isNotSameInstanceAs(context.userProfile)
        assertDistinctNestedUserProfileCollections(snapshotUserProfile, context.userProfile)

        assertThat(readField(snapshot, "fitnessGoals")).isEqualTo(context.fitnessGoals)
        assertThat(readField(snapshot, "fitnessGoals")).isNotSameInstanceAs(context.fitnessGoals)
        assertThat(readField(snapshot, "availableEquipment")).isEqualTo(context.availableEquipment)
        assertThat(readField(snapshot, "availableEquipment")).isNotSameInstanceAs(context.availableEquipment)
        assertThat(readField(snapshot, "musclePriorities")).isEqualTo(context.musclePriorities)
        assertThat(readField(snapshot, "musclePriorities")).isNotSameInstanceAs(context.musclePriorities)
        assertThat(snapshotRecentWorkoutHistory).isEqualTo(context.recentWorkoutHistory)
        assertThat(snapshotRecentWorkoutHistory).isNotSameInstanceAs(context.recentWorkoutHistory)
        assertThat(snapshotExerciseHistory).isEqualTo(context.exerciseHistory)
        assertThat(snapshotExerciseHistory).isNotSameInstanceAs(context.exerciseHistory)
        assertThat(readField(snapshot, "recentlyTrainedMuscles")).isEqualTo(context.recentlyTrainedMuscles)
        assertThat(readField(snapshot, "recentlyTrainedMuscles")).isNotSameInstanceAs(context.recentlyTrainedMuscles)
        assertThat(readField(snapshot, "excludedExerciseIds")).isEqualTo(context.excludedExerciseIds)
        assertThat(readField(snapshot, "excludedExerciseIds")).isNotSameInstanceAs(context.excludedExerciseIds)
        assertThat(snapshotAllowedExercises).isEqualTo(context.allowedExercises)
        assertThat(snapshotAllowedExercises).isNotSameInstanceAs(context.allowedExercises)
        assertThat(snapshotCapabilityEvidence).isEqualTo(context.capabilityEvidence)
        assertThat(snapshotCapabilityEvidence).isSameInstanceAs(context.capabilityEvidence)
        assertThat(snapshotRestPreferences).isEqualTo(context.priorUserRestPreferences)
        assertThat(snapshotRestPreferences)
            .isNotSameInstanceAs(context.priorUserRestPreferences)

        // The reconstructed weekly ledger is this corpus's headline planner input, and
        // `WeeklyDoseLedger` stores its three count maps by reference, so the snapshot has to
        // copy them or the non-mutation assertion would be trivially true for exactly the
        // input it most needs to cover.
        val snapshotProgramState = readField(snapshot, "trainingProgramState")
        val sourceProgramState = checkNotNull(context.trainingProgramState)
        assertThat(snapshotProgramState).isEqualTo(sourceProgramState)
        assertThat(snapshotProgramState).isNotSameInstanceAs(sourceProgramState)
        val snapshotEligibility = readField(snapshot, "automaticEligibilityResult")
        val sourceEligibility = checkNotNull(context.automaticEligibilityResult)
        assertThat(snapshotEligibility).isEqualTo(sourceEligibility)
        assertThat(snapshotEligibility).isNotSameInstanceAs(sourceEligibility)
        val snapshotCandidates = snapshotEligibility as AutomaticEligibilityResult.Candidates
        val sourceCandidates = sourceEligibility as AutomaticEligibilityResult.Candidates
        assertThat(snapshotCandidates.exercises).isNotSameInstanceAs(sourceCandidates.exercises)
        // As with `decisions`, the outer list alone is satisfied by a plain `toList()`.
        assertThat(snapshotCandidates.exercises.single())
            .isNotSameInstanceAs(sourceCandidates.exercises.single())
        assertThat(snapshotCandidates.exercises.single().primaryMuscles)
            .isNotSameInstanceAs(sourceCandidates.exercises.single().primaryMuscles)
        assertThat(snapshotCandidates.decisions).isNotSameInstanceAs(sourceCandidates.decisions)
        // The outer list alone is satisfied by a plain `toList()`, so the element and its
        // own collections have to be checked too.
        val snapshotDecision = snapshotCandidates.decisions.single()
        val sourceDecision = sourceCandidates.decisions.single()
        assertThat(snapshotDecision).isNotSameInstanceAs(sourceDecision)
        assertThat(snapshotDecision.reasons).isNotSameInstanceAs(sourceDecision.reasons)
        assertThat(snapshotDecision.preferences).isNotSameInstanceAs(sourceDecision.preferences)

        // The other arm of the eligibility `when`. Nothing else in the suite enters it:
        // `reviewed-enabled-no-approved` only ever compares snapshots for value equality,
        // which passes whether or not its decisions are copied.
        val noCandidatesContext = context.copy(
            automaticEligibilityResult = AutomaticEligibilityResult.NoCandidates(
                failure = AutomaticEligibilityFailure.NO_APPROVED_METADATA,
                decisions = sourceCandidates.decisions
            )
        )
        val noCandidatesSnapshot = snapshotOf(noCandidatesContext)
        val snapshotNoCandidates = readField(noCandidatesSnapshot, "automaticEligibilityResult")
            as AutomaticEligibilityResult.NoCandidates
        val sourceNoCandidates =
            noCandidatesContext.automaticEligibilityResult as AutomaticEligibilityResult.NoCandidates
        assertThat(snapshotNoCandidates).isEqualTo(sourceNoCandidates)
        assertThat(snapshotNoCandidates).isNotSameInstanceAs(sourceNoCandidates)
        assertThat(snapshotNoCandidates.decisions)
            .isNotSameInstanceAs(sourceNoCandidates.decisions)
        assertThat(snapshotNoCandidates.decisions.single())
            .isNotSameInstanceAs(sourceNoCandidates.decisions.single())

        val snapshotConstraints = readField(snapshot, "programConstraints")
        assertThat(snapshotConstraints).isEqualTo(context.programConstraints)
        assertThat(snapshotConstraints).isNotSameInstanceAs(context.programConstraints)
        assertThat((snapshotConstraints as SessionProgramConstraints).requiredMovementPatterns)
            .isNotSameInstanceAs(context.programConstraints.requiredMovementPatterns)

        val snapshotLedger = (snapshotProgramState as TrainingProgramState).weeklyLedger
        assertThat(snapshotLedger.directPrimarySets)
            .isNotSameInstanceAs(sourceProgramState.weeklyLedger.directPrimarySets)
        assertThat(snapshotLedger.secondaryInvolvement)
            .isNotSameInstanceAs(sourceProgramState.weeklyLedger.secondaryInvolvement)
        assertThat(snapshotLedger.unattributedWorkSets)
            .isNotSameInstanceAs(sourceProgramState.weeklyLedger.unattributedWorkSets)

        val snapshotSession = snapshotRecentWorkoutHistory.single() as WorkoutSession
        val sourceSession = context.recentWorkoutHistory.single()
        assertThat(snapshotSession).isNotSameInstanceAs(sourceSession)
        assertThat(snapshotSession.focusMuscles).isNotSameInstanceAs(sourceSession.focusMuscles)
        assertThat(snapshotSession.exercises).isNotSameInstanceAs(sourceSession.exercises)
        assertThat(snapshotSession.exercises.single()).isNotSameInstanceAs(sourceSession.exercises.single())
        assertThat(snapshotSession.exercises.single().sets).isNotSameInstanceAs(sourceSession.exercises.single().sets)
        // `WorkoutSet` holds nothing mutable, so the elements are deliberately shared.
        assertThat(snapshotSession.exercises.single().sets.single())
            .isSameInstanceAs(sourceSession.exercises.single().sets.single())

        @Suppress("UNCHECKED_CAST")
        val snapshotHistory = (snapshotExerciseHistory as Map<String, *>)
            .getValue("incline-dumbbell-press") as ExercisePerformanceHistory
        val sourceHistory = context.exerciseHistory.getValue("incline-dumbbell-press")
        assertThat(snapshotHistory).isNotSameInstanceAs(sourceHistory)
        assertThat(snapshotHistory.recentSets).isNotSameInstanceAs(sourceHistory.recentSets)
        // `WorkoutSet` holds nothing mutable, so the elements are deliberately shared.
        assertThat(snapshotHistory.recentSets.single())
            .isSameInstanceAs(sourceHistory.recentSets.single())

        val snapshotExercise = snapshotAllowedExercises.single() as Exercise
        val sourceExercise = context.allowedExercises.single()
        assertThat(snapshotExercise).isNotSameInstanceAs(sourceExercise)
        // A tree of strings with no collection in it, so it is deliberately shared.
        assertThat(snapshotExercise.source).isSameInstanceAs(sourceExercise.source)
        assertThat(snapshotExercise.searchAliases).isNotSameInstanceAs(sourceExercise.searchAliases)
        assertThat(snapshotExercise.primaryMuscles).isNotSameInstanceAs(sourceExercise.primaryMuscles)
        assertThat(snapshotExercise.secondaryMuscles).isNotSameInstanceAs(sourceExercise.secondaryMuscles)
        assertThat(snapshotExercise.listedEquipment).isNotSameInstanceAs(sourceExercise.listedEquipment)
        assertThat(snapshotExercise.programming).isNotSameInstanceAs(sourceExercise.programming)
        assertThat(snapshotExercise.programming!!.requiredEquipmentCombinations)
            .isNotSameInstanceAs(sourceExercise.programming!!.requiredEquipmentCombinations)
        assertThat(snapshotExercise.programming!!.requiredEquipmentCombinations.single())
            .isNotSameInstanceAs(sourceExercise.programming!!.requiredEquipmentCombinations.single())
        assertThat(snapshotExercise.programming!!.alternativeExerciseIds)
            .isNotSameInstanceAs(sourceExercise.programming!!.alternativeExerciseIds)

        val snapshotReviewed = checkNotNull(snapshotExercise.reviewedMetadata)
        val sourceReviewed = checkNotNull(sourceExercise.reviewedMetadata)
        assertThat(snapshotReviewed).isEqualTo(sourceReviewed)
        assertThat(snapshotReviewed).isNotSameInstanceAs(sourceReviewed)
        assertThat(snapshotReviewed.descriptiveSecondaryMuscles)
            .isNotSameInstanceAs(sourceReviewed.descriptiveSecondaryMuscles)
        assertThat(snapshotReviewed.approvedRegressions)
            .isNotSameInstanceAs(sourceReviewed.approvedRegressions)
        assertThat(snapshotReviewed.approvedSubstitutions)
            .isNotSameInstanceAs(sourceReviewed.approvedSubstitutions)
        // `ReviewedExerciseLink` and `ReviewProvenance` are flat values, deliberately shared.
        assertThat(snapshotReviewed.approvedRegressions.single())
            .isSameInstanceAs(sourceReviewed.approvedRegressions.single())
        assertThat(snapshotReviewed.approvedSubstitutions.single())
            .isSameInstanceAs(sourceReviewed.approvedSubstitutions.single())
        assertThat(snapshotReviewed.capabilityRequirements)
            .isNotSameInstanceAs(sourceReviewed.capabilityRequirements)
        assertThat(snapshotReviewed.equipmentAlternatives)
            .isNotSameInstanceAs(sourceReviewed.equipmentAlternatives)
        assertThat(snapshotReviewed.equipmentAlternatives.single())
            .isNotSameInstanceAs(sourceReviewed.equipmentAlternatives.single())
        assertThat(snapshotReviewed.provenance).isSameInstanceAs(sourceReviewed.provenance)
    }

    /**
     * The unmodified replay of one corpus persona, taken from the single shared corpus run.
     *
     * Tests that vary a persona still call [PlannerFixtureEvaluator.evaluateFixture] directly
     * with their modified fixture, so only unmodified baselines are reused.
     */
    private fun sharedSuccess(id: String): PlannerFixtureSuccessEvaluation =
        SHARED_CORPUS_EVALUATIONS
            .filterIsInstance<PlannerFixtureSuccessEvaluation>()
            .single { it.built.fixture.id == id }

    private fun assertSuccessfulFixture(evaluation: PlannerFixtureSuccessEvaluation) {
        val normalizedFirst = evaluation.firstWorkout.normalizedPlannerFixtureWorkout()
        val normalizedSecond = evaluation.secondWorkout.normalizedPlannerFixtureWorkout()
        assertThat(normalizedFirst).isEqualTo(normalizedSecond)

        val selectedIds = normalizedFirst.exercises.map { it.exerciseId }
        val allowedIds = evaluation.built.context.allowedExercises.map(Exercise::id).toSet()
        val filteredIds = evaluation.built.filteredExercises.map(Exercise::id).toSet()
        val catalogById = evaluation.built.catalogExercises.associateBy(Exercise::id)
        val excludedIds = evaluation.built.userProfile.excludedExerciseIds.toSet()

        assertThat(selectedIds).isNotEmpty()
        assertThat(allowedIds.containsAll(selectedIds)).isTrue()
        assertThat(filteredIds.containsAll(selectedIds)).isTrue()
        assertThat(excludedIds.intersect(selectedIds.toSet())).isEmpty()

        if (evaluation.built.fixture.expected.requiredExerciseIds.isNotEmpty()) {
            evaluation.built.fixture.expected.requiredExerciseIds.forEach { requiredId ->
                assertThat(selectedIds).contains(requiredId)
            }
        }
        if (evaluation.built.fixture.expected.forbiddenExerciseIds.isNotEmpty()) {
            evaluation.built.fixture.expected.forbiddenExerciseIds.forEach { forbiddenId ->
                assertThat(selectedIds).doesNotContain(forbiddenId)
            }
        }
        readRequiredAnyExerciseIdGroups(evaluation.built.fixture.expected).forEach { group ->
            assertWithMessage("${evaluation.built.fixture.id}: expected one of $group; selected $selectedIds")
                .that(selectedIds.any { it in group }).isTrue()
        }

        val expectedTargetWeights = readExpectedTargetWeights(evaluation.built.fixture.expected)
        expectedTargetWeights.forEach { (exerciseId, expectedWeight) ->
            val generated = normalizedFirst.exercises.singleOrNull { it.exerciseId == exerciseId }
            assertThat(generated).isNotNull()
            assertThat(generated!!.prescription.targetWeight).isEqualTo(expectedWeight)
        }
        readTitleIdentityContains(evaluation.built.fixture.expected)?.let { expectedFragment ->
            // The title is a structured spec now, so a fixture asserts against the
            // identifiers the planner chose rather than against a rendered English
            // sentence that would differ in every language.
            val titleIdentity = buildList {
                add(normalizedFirst.title.split.name)
                add(normalizedFirst.title.emphasis.name)
                if (normalizedFirst.title.isReEntry) add("RE_ENTRY")
            }.joinToString(" ")
            assertThat(titleIdentity).contains(expectedFragment)
        }
        readMaxTargetSetsPerExercise(evaluation.built.fixture.expected)?.let { maxTargetSets ->
            normalizedFirst.exercises.forEach { generated ->
                assertThat(generated.prescription.targetSets).isAtMost(maxTargetSets)
            }
        }
        if (evaluation.built.fixture.id == "bodyweight-beginner") {
            normalizedFirst.exercises.forEach { generated ->
                val catalogExercise = checkNotNull(catalogById[generated.exerciseId])
                assertThat(hasSatisfiedEquipment(catalogExercise, listOf(StandardEquipment.BODYWEIGHT))).isTrue()
            }
        }

        normalizedFirst.exercises.forEach { generated ->
            val catalogExercise = checkNotNull(catalogById[generated.exerciseId])
            val expectedPrescription = prescriptionFactory.create(catalogExercise, evaluation.built.context)

            assertThat(generated.prescription.exerciseType).isEqualTo(catalogExercise.type)
            assertThat(
                generated.prescription.copy(targetWeight = null)
            ).isEqualTo(
                expectedPrescription.copy(targetWeight = null)
            )

            when (catalogExercise.type) {
                ExerciseType.WEIGHT_REPS -> {
                    val history = evaluation.built.context.exerciseHistory[catalogExercise.id]
                    val confirmedLoad = evaluation.built.userProfile.confirmedStartingLoads[catalogExercise.id]
                        ?.takeIf { it.isFinite() && it >= 0.0 }
                    if (history == null && confirmedLoad == null) {
                        assertThat(generated.prescription.targetWeight).isNull()
                    }
                    assertThat(generated.prescription.targetAssistanceWeight).isNull()
                    assertThat(generated.prescription.targetDurationSeconds).isNull()
                    assertThat(generated.prescription.targetDistanceMeters).isNull()
                }

                ExerciseType.BODYWEIGHT_REPS -> {
                    assertThat(generated.prescription.repRange).isNotNull()
                    assertThat(generated.prescription.targetWeight).isNull()
                    assertThat(generated.prescription.targetAssistanceWeight).isNull()
                    assertThat(generated.prescription.targetDurationSeconds).isNull()
                    assertThat(generated.prescription.targetDistanceMeters).isNull()
                }

                ExerciseType.ASSISTED_BODYWEIGHT -> {
                    assertThat(generated.prescription.repRange).isNotNull()
                    assertThat(generated.prescription.targetWeight).isNull()
                    assertThat(generated.prescription.targetDurationSeconds).isNull()
                    assertThat(generated.prescription.targetDistanceMeters).isNull()
                }

                ExerciseType.DURATION -> {
                    assertThat(generated.prescription.repRange).isNull()
                    assertThat(generated.prescription.targetWeight).isNull()
                    assertThat(generated.prescription.targetAssistanceWeight).isNull()
                    assertThat(generated.prescription.targetDurationSeconds).isNotNull()
                    assertThat(generated.prescription.targetDistanceMeters).isNull()
                }

                ExerciseType.DISTANCE_DURATION -> {
                    assertThat(generated.prescription.repRange).isNull()
                    assertThat(generated.prescription.targetWeight).isNull()
                    assertThat(generated.prescription.targetAssistanceWeight).isNull()
                    assertThat(
                        generated.prescription.targetDurationSeconds != null ||
                            generated.prescription.targetDistanceMeters != null
                    ).isTrue()
                }
            }
        }
    }

    private fun assertFailureFixture(evaluation: PlannerFixtureFailureEvaluation) {
        val expectedFailure = when (evaluation.built.fixture.expected.outcome) {
            PlannerFixtureOutcome.SUCCESS -> error("Expected failure fixture but found success")
            PlannerFixtureOutcome.NO_CANDIDATES -> WorkoutPlanningFailure.NO_CANDIDATES
            PlannerFixtureOutcome.NO_STRENGTH_CANDIDATES ->
                WorkoutPlanningFailure.NO_STRENGTH_CANDIDATES
            PlannerFixtureOutcome.NO_CANDIDATES_FOR_ANY_SPLIT ->
                WorkoutPlanningFailure.NO_CANDIDATES_FOR_ANY_SPLIT
            PlannerFixtureOutcome.REVIEWED_ELIGIBILITY_NO_CANDIDATES ->
                WorkoutPlanningFailure.REVIEWED_ELIGIBILITY_NO_CANDIDATES
        }

        assertThat(evaluation.firstFailure).isEqualTo(expectedFailure)
        assertThat(evaluation.secondFailure).isEqualTo(expectedFailure)
        assertThat(evaluation.firstAutomaticEligibilityFailure)
            .isEqualTo(evaluation.built.fixture.expected.automaticEligibilityFailure)
        assertThat(evaluation.secondAutomaticEligibilityFailure)
            .isEqualTo(evaluation.built.fixture.expected.automaticEligibilityFailure)
    }

    @Suppress("UNCHECKED_CAST")
    private fun readExpectedTargetWeights(expected: Any): Map<String, Double> {
        val getter = expected.javaClass.methods.singleOrNull {
            it.name == "getExpectedTargetWeights" && it.parameterCount == 0
        } ?: error("PlannerFixtureExpected must expose expectedTargetWeights.")
        val value = getter.invoke(expected) as Map<*, *>
        return value.mapKeys { it.key as String }.mapValues { (_, weight) -> (weight as Number).toDouble() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readRequiredAnyExerciseIdGroups(expected: Any): List<Set<String>> {
        val getter = expected.javaClass.methods.singleOrNull {
            it.name == "getRequiredAnyExerciseIdGroups" && it.parameterCount == 0
        } ?: error("PlannerFixtureExpected must expose requiredAnyExerciseIdGroups.")
        val value = getter.invoke(expected) as List<*>
        return value.map { group ->
            (group as Set<*>).map { it as String }.toSet()
        }
    }

    private fun readTitleIdentityContains(expected: Any): String? {
        val getter = expected.javaClass.methods.singleOrNull {
            it.name == "getTitleIdentityContains" && it.parameterCount == 0
        } ?: error("PlannerFixtureExpected must expose titleIdentityContains.")
        return getter.invoke(expected) as String?
    }

    private fun readMaxTargetSetsPerExercise(expected: Any): Int? {
        val getter = expected.javaClass.methods.singleOrNull {
            it.name == "getMaxTargetSetsPerExercise" && it.parameterCount == 0
        } ?: error("PlannerFixtureExpected must expose maxTargetSetsPerExercise.")
        return getter.invoke(expected) as Int?
    }

    private fun hasSatisfiedEquipment(exercise: Exercise, ownedEquipment: List<String>): Boolean {
        val owned = ownedEquipment.toSet()
        val combinations = exercise.programming?.requiredEquipmentCombinations
            ?: listOf(exercise.listedEquipment.filter(String::isNotBlank))
        return combinations.isEmpty() || combinations.any { combination ->
            combination.all { it in owned }
        }
    }

    private fun snapshotOf(context: WorkoutGenerationContext): Any {
        val method = Class.forName("wallcrawl.elopenmike.com.core.ai.PlannerFixtureEvaluatorKt")
            .getDeclaredMethod("snapshot", WorkoutGenerationContext::class.java)
        method.isAccessible = true
        return checkNotNull(method.invoke(null, context))
    }

    private fun readField(instance: Any, name: String): Any {
        val field = instance.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return checkNotNull(field.get(instance))
    }

    private fun assertDistinctNestedUserProfileCollections(snapshot: Any, source: UserProfile) {
        assertThat(readField(snapshot, "goals")).isNotSameInstanceAs(source.goals)
        assertThat(readField(snapshot, "availableEquipment")).isNotSameInstanceAs(source.availableEquipment)
        assertThat(readField(snapshot, "musclePriorities")).isNotSameInstanceAs(source.musclePriorities)
        assertThat(readField(snapshot, "excludedExerciseIds")).isNotSameInstanceAs(source.excludedExerciseIds)
        assertThat(readField(snapshot, "trainingConstraints")).isNotSameInstanceAs(source.trainingConstraints)
        assertThat(readField(snapshot, "confirmedStartingLoads")).isNotSameInstanceAs(source.confirmedStartingLoads)
        // `MovementCapabilities` has a private constructor and its only factory wraps a
        // freshly built map in `unmodifiableMap`, so it is deeply immutable and deliberately
        // shared — the same rule the snapshot applies to `capabilityEvidence`.
        val snapshotCapabilities = readField(snapshot, "movementCapabilities")
        assertThat(snapshotCapabilities).isSameInstanceAs(source.movementCapabilities)
    }

    private fun snapshotProbeContext(): WorkoutGenerationContext {
        val workingSet = WorkoutSet(
            id = "history-set-1",
            workoutExerciseId = "history-exercise-1",
            setNumber = 1,
            exerciseType = ExerciseType.WEIGHT_REPS,
            targetReps = 10,
            completedReps = 10,
            targetWeight = 27.5,
            completedWeight = 27.5,
            isCompleted = true
        )
        val sessionExercise = WorkoutExercise(
            id = "session-exercise-1",
            sessionId = "session-1",
            exerciseId = "incline-dumbbell-press",
            orderIndex = 0,
            prescription = ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(8, 12),
                targetWeight = 27.5,
                restSeconds = 90
            ),
            sets = listOf(workingSet)
        )
        val recentSession = WorkoutSession(
            id = "session-1",
            name = "Upper Body · Hypertrophy",
            status = SessionStatus.COMPLETED,
            completedAtTimestamp = 1234L,
            focusMuscles = listOf(StandardMuscles.CHEST, StandardMuscles.BACK),
            exercises = listOf(sessionExercise)
        )
        val allowedExercise = Exercise(
            id = "incline-dumbbell-press",
            source = ExerciseSource(
                catalogId = UUID.randomUUID().toString(),
                sourceId = "exercise-incline-dumbbell-press",
                sourceSlug = "incline-dumbbell-press",
                attribution = ExerciseAttribution(
                    creator = "Fixture Creator",
                    creatorUrl = "https://example.com/creator",
                    license = "CC BY",
                    licenseUrl = "https://example.com/license",
                    source = ExerciseAttributionSource(
                        name = "Fixture Catalog",
                        url = "https://example.com/source",
                        license = "CC BY",
                        licenseUrl = "https://example.com/source-license",
                        changes = "Normalized for tests"
                    )
                )
            ),
            name = "Incline Dumbbell Press",
            searchAliases = listOf("incline press"),
            primaryMuscles = listOf(StandardMuscles.CHEST),
            secondaryMuscles = listOf(StandardMuscles.SHOULDERS, StandardMuscles.TRICEPS),
            listedEquipment = listOf(StandardEquipment.DUMBBELL, StandardEquipment.BENCH),
            type = ExerciseType.WEIGHT_REPS,
            programming = ExerciseProgrammingMetadata(
                requiredEquipmentCombinations = listOf(
                    listOf(StandardEquipment.DUMBBELL, StandardEquipment.BENCH)
                ),
                movementPattern = MovementPattern.HORIZONTAL_PUSH,
                difficulty = Difficulty.INTERMEDIATE,
                mechanics = MechanicsType.COMPOUND,
                recommendedRepRange = RepRange(8, 12),
                fatigueScore = 3,
                progressionType = ProgressionType.REPETITIONS_THEN_LOAD,
                alternativeExerciseIds = listOf("dumbbell-bench-press"),
                coachingSummary = "Drive through the upper chest."
            ),
            // Populated on purpose: left null, the whole six-branch
            // `ReviewedExerciseMetadata.deepCopy` is never invoked and could be deleted with
            // this probe still green. Every reviewed-enabled persona carries such a block.
            reviewedMetadata = ReviewedExerciseMetadata(
                reviewState = ReviewState.APPROVED,
                directPrimaryMuscle = StandardMuscles.CHEST,
                descriptiveSecondaryMuscles = setOf(
                    StandardMuscles.SHOULDERS,
                    StandardMuscles.TRICEPS
                ),
                movementPattern = MovementPattern.HORIZONTAL_PUSH,
                complexity = ComplexityTier.STANDARD,
                progressionFamily = "probe-family",
                prescriptionShape = PrescriptionShape.WEIGHT_REPS,
                approvedRegressions = listOf(ReviewedExerciseLink("knee-push-up")),
                approvedSubstitutions = listOf(ReviewedExerciseLink("dumbbell-bench-press")),
                capabilityRequirements = setOf(MovementCapabilityType.IMPACT),
                supportRequirement = SupportRequirement.SUPPORTED,
                impactLevel = ImpactLevel.LOW,
                equipmentAlternatives = listOf(
                    listOf(StandardEquipment.DUMBBELL, StandardEquipment.BENCH)
                ),
                provenance = ReviewProvenance(
                    reviewerRole = "SYNTHETIC_PROBE_REVIEWER_NOT_A_HUMAN",
                    rationaleOrSource = "SYNTHETIC PROBE FIXTURE. Never bundled.",
                    reviewedAtEpochMillis = 1L,
                    schemaVersion = 1,
                    policyVersion = 1
                )
            )
        )
        val profile = UserProfile(
            goals = linkedSetOf(FitnessGoal.GENERAL_FITNESS, FitnessGoal.BUILD_MUSCLE),
            experienceLevel = wallcrawl.elopenmike.com.core.model.ExperienceLevel.BEGINNER,
            preferredDurationMinutes = 45,
            daysPerWeek = 3,
            availableEquipment = listOf(StandardEquipment.DUMBBELL, StandardEquipment.BENCH),
            preferredUnit = WeightUnit.KG,
            musclePriorities = linkedMapOf(
                StandardMuscles.CHEST to PriorityLevel.HIGH,
                StandardMuscles.BACK to PriorityLevel.NORMAL
            ),
            excludedExerciseIds = listOf("barbell-bench-press"),
            onboardingCompleted = true,
            trainingConstraints = linkedSetOf(TrainingConstraint.LOW_IMPACT_ONLY),
            returningAfterBreakWeeks = 6,
            confirmedStartingLoads = linkedMapOf("incline-dumbbell-press" to 27.5),
            movementCapabilities = MovementCapabilities.from(
                linkedMapOf(
                    MovementCapabilityType.IMPACT to CapabilityLevel.LIMITED,
                    MovementCapabilityType.FLOOR_TRANSITION to CapabilityLevel.COMFORTABLE
                )
            )
        )
        return WorkoutGenerationContext(
            userProfile = profile,
            fitnessGoals = linkedSetOf(FitnessGoal.GENERAL_FITNESS, FitnessGoal.BUILD_MUSCLE),
            fitnessGoal = FitnessGoal.GENERAL_FITNESS,
            experienceLevel = wallcrawl.elopenmike.com.core.model.ExperienceLevel.BEGINNER,
            availableEquipment = listOf(StandardEquipment.DUMBBELL, StandardEquipment.BENCH),
            preferredWorkoutDurationMinutes = 45,
            trainingFrequencyDaysPerWeek = 3,
            musclePriorities = linkedMapOf(
                StandardMuscles.CHEST to PriorityLevel.HIGH,
                StandardMuscles.BACK to PriorityLevel.NORMAL
            ),
            recentWorkoutHistory = listOf(recentSession),
            completedWorkoutCount = 9,
            exerciseHistory = linkedMapOf(
                allowedExercise.id to ExercisePerformanceHistory(
                    exerciseId = allowedExercise.id,
                    lastWeight = 27.5,
                    lastReps = 10,
                    bestEstimated1RM = 34.0,
                    recentSets = listOf(workingSet)
                )
            ),
            recentlyTrainedMuscles = listOf(StandardMuscles.CHEST, StandardMuscles.TRICEPS),
            excludedExerciseIds = listOf("barbell-bench-press"),
            allowedExercises = listOf(allowedExercise),
            capabilityEvidence = CapabilityEvidenceSet.from(
                mapOf(
                    allowedExercise.id to CapabilityEvidence(
                        policyVersion =
                            CapabilityEvidencePolicyVersion.TWO_COMPARABLE_MANAGEABLE_SESSIONS_V1,
                        reason =
                            CapabilityEvidenceReason.TWO_COMPARABLE_MANAGEABLE_COMPLETED_SESSIONS,
                        appliesToExerciseId = allowedExercise.id,
                        demonstratedExerciseId = allowedExercise.id,
                        scope = CapabilityEvidenceScope.EXACT_EXERCISE,
                        comparableShape = ComparableMovementShape.WEIGHT_REPETITIONS,
                        qualifyingSessionIds = listOf("session-1", "session-2")
                    )
                )
            ),
            priorUserRestPreferences = linkedMapOf(
                allowedExercise.id to UserRestPreference(
                    restClass = wallcrawl.elopenmike.com.core.model.RestClass.LONG,
                    restSeconds = 240
                )
            ),
            // Both of these are supplied non-empty on purpose. Left at their defaults —
            // a null eligibility result and an empty pattern set — the snapshot's copies of
            // them are never exercised, and dropping either copy would still pass.
            automaticEligibilityResult = AutomaticEligibilityResult.Candidates(
                exercises = listOf(allowedExercise),
                decisions = listOf(
                    EligibilityDecision(
                        exerciseId = allowedExercise.id,
                        eligible = true,
                        reasons = listOf(EligibilityReason.APPROVED),
                        // Non-empty on purpose: `emptyList().toList()` returns the shared
                        // singleton, so an empty list could never detect a dropped copy.
                        preferences = listOf(
                            EligibilityPreference.Limited(MovementCapabilityType.IMPACT)
                        )
                    )
                )
            ),
            programConstraints = SessionProgramConstraints(
                requiredMovementPatterns = setOf(MovementPattern.HORIZONTAL_PUSH)
            ),
            // Deliberately the mutable map types `WeeklyDoseLedgerCalculator` actually
            // supplies: the ledger stores them by reference, so the probe has to as well.
            trainingProgramState = TrainingProgramState(
                policyVersion = TrainingProgramStatePolicyVersion.PROGRAM_STATE_V1,
                adaptationState = AdaptationState.BUILD,
                weeklyLedger = WeeklyDoseLedger(
                    policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
                    weekStartEpochDay = MONDAY_EPOCH_DAY,
                    timeZoneId = "UTC",
                    catalogVersion = "probe-catalog",
                    reviewPolicyVersion = 1,
                    directPrimarySets = sortedMapOf(StandardMuscles.CHEST to 4),
                    secondaryInvolvement = sortedMapOf(StandardMuscles.TRICEPS to 4),
                    unattributedWorkSets = linkedMapOf(
                        LedgerOmissionReason.MISSING_REVIEWED_METADATA to 2
                    )
                )
            ),
            preferredUnits = WeightUnit.KG
        )
    }

    private companion object {
        /** The upper bound the packaged catalog parser accepts for the legacy ordinal label. */
        const val MAX_FATIGUE_SCORE = 5

        /** One replay of the shared parsed roster, reused by every assertion that reads it. */
        val SHARED_CORPUS_EVALUATIONS: List<PlannerFixtureEvaluation> by lazy {
            runBlocking {
                SharedPlannerFixtureHarness.corpus.map {
                    SharedPlannerFixtureHarness.evaluator.evaluateFixture(it)
                }
            }
        }

    }
}
