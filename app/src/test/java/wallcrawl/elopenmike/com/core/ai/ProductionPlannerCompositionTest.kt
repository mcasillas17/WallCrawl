package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WeeklyDoseLedgerRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.AdaptationState
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityFailure
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityResult
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.ImpactLevel
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.RecommendationRecord
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.TrainingWeek
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeeklyDoseLedger
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutRankingReason
import wallcrawl.elopenmike.com.core.model.WorkoutRankingReasonCode

/**
 * The composition `WallCrawlApplication` builds, run against the actual bundled catalog.
 *
 * Nothing here synthesizes a review. The exercises are the ones parsed out of the shipped
 * `workout-guide/catalog.json`, with the `AI_ACCEPTED` records exactly as audited, and the
 * planner reads them through `PlannerFeatureFlags.PRODUCTION` rather than a locally built
 * flag object. A cohort that cannot be served must therefore surface the typed reviewed
 * refusal, never a legacy candidate list.
 */
class ProductionPlannerCompositionTest {

    private val factory = PlannerFixtureContextFactory()
    private val bundledCatalog = factory.bundledCatalogProjection()
    private val bundledExercises = bundledCatalog.exercises
    private val acceptedIds =
        bundledExercises.filter { it.acceptedMetadata() != null }.map(Exercise::id).toSet()

    @Test
    fun unrepresentableWorkingLoadsProduceAnExplicitHoldAfterUnitConversion() = runTest {
        for (id in listOf("assisted-chin-up", "dumbbell-bench-press")) {
            val exercise = bundledExercises.single { it.id == id }
            for ((logged, targetUnit) in listOf(10_001.0 to WeightUnit.KG, 5_000.0 to WeightUnit.LBS)) {
                val profile = fullGymProfile().copy(
                    preferredUnit = targetUnit,
                    confirmedStartingLoads = mapOf(id to 20.0),
                    excludedExerciseIds = bundledExercises.map { it.id }.filterNot { it == id }
                )
                val reference = if (exercise.type == ExerciseType.ASSISTED_BODYWEIGHT) {
                    wallcrawl.elopenmike.com.core.model.ExercisePrescription(
                        exercise.type, 2, wallcrawl.elopenmike.com.core.model.RepRange(6, 10),
                        targetAssistanceWeight = 5_000.0
                    )
                } else wallcrawl.elopenmike.com.core.model.ExercisePrescription(
                    exercise.type, 2, wallcrawl.elopenmike.com.core.model.RepRange(8, 12), targetWeight = 5_000.0
                )
                val history = progressionSession("recorded", id, reference, schedulingNow() - 8 * DAY_MILLIS)
                    .let { session -> session.copy(exercises = session.exercises.map { instance ->
                        instance.copy(sets = instance.sets.map { set ->
                            if (exercise.type == ExerciseType.ASSISTED_BODYWEIGHT) set.copy(completedAssistanceWeight = logged)
                            else set.copy(completedWeight = logged)
                        })
                    }) }
                val context = productionContextBuilder(profile, listOf(history)).build()
                val plan = FakeWorkoutPlanner().generateWorkout(context)
                assertThat(plan.exercises.single().prescription.targetWeight).isNull()
                assertThat(plan.exercises.single().prescription.targetAssistanceWeight).isNull()
                assertThat(plan.progressionDecisions.single().reason)
                    .isEqualTo(wallcrawl.elopenmike.com.core.model.ProgressionReason.HOLD_AT_BOUND)
                assertThat(ProgramValidator(GeneratedWorkoutValidator(InMemoryExerciseCatalog(bundledExercises)))
                    .validate(plan, context)).isInstanceOf(ProgramValidationResult.Valid::class.java)
                assertThat(context.progressionHistory.single()).isEqualTo(history)
            }
        }
    }

    @Test
    fun advancedRepTargetsPersistAcrossNewEvidenceAndChangedGoalsResetComparison() = runTest {
        val id = "push-up"
        val exercise = bundledExercises.single { it.id == id }
        val profile = bodyweightProfile().copy(
            preferredUnit = WeightUnit.KG,
            excludedExerciseIds = bundledExercises.map { it.id }.filterNot { it == id }
        )
        val validator = ProgramValidator(GeneratedWorkoutValidator(InMemoryExerciseCatalog(bundledExercises)))
        val base = DefaultExercisePrescriptionFactory().create(exercise, productionContextBuilder(profile).build())
        val history = mutableListOf(
            progressionSession("one", id, base, schedulingNow() - 12 * DAY_MILLIS),
            progressionSession("two", id, base, schedulingNow() - 11 * DAY_MILLIS)
        )
        val firstContext = productionContextBuilder(profile, history).build()
        val first = validator.validate(FakeWorkoutPlanner().generateWorkout(firstContext), firstContext) as ProgramValidationResult.Valid
        val advanced = first.workout.exercises.single().prescription
        assertThat(advanced.repRange!!.max).isEqualTo(base.repRange!!.max + 1)
        history += progressionSession("three", id, advanced, schedulingNow() - 10 * DAY_MILLIS)
        val records = mutableListOf(first.snapshot.asRecord("three", schedulingNow() - 10 * DAY_MILLIS))
        val betweenContext = productionContextBuilder(profile, history, records = records).build()
        val between = validator.validate(FakeWorkoutPlanner().generateWorkout(betweenContext), betweenContext) as ProgramValidationResult.Valid
        assertThat(between.workout.exercises.single().prescription).isEqualTo(advanced)
        assertThat(between.workout.progressionDecisions.single().reason)
            .isEqualTo(wallcrawl.elopenmike.com.core.model.ProgressionReason.HOLD_TARGETS_CHANGED)
        val constrainedContext = productionContextBuilder(
            profile,
            history + completedSessionOf(
                "current-week-dose", bundledExercises.single { it.id == "cable-fly" }, 5,
                schedulingNow() - HOUR_MILLIS
            ),
            records = records
        ).build()
        val constrained = FakeWorkoutPlanner().generateWorkout(constrainedContext)
        assertThat(constrained.exercises.single().prescription)
            .isEqualTo(advanced.copy(targetSets = 1))
        assertThat(constrained.progressionDecisions.single().reason)
            .isEqualTo(wallcrawl.elopenmike.com.core.model.ProgressionReason.HOLD_TARGETS_CHANGED)
        history += progressionSession("four", id, advanced, schedulingNow() - 9 * DAY_MILLIS)
        records += between.snapshot.asRecord("four", schedulingNow() - 9 * DAY_MILLIS)
        val nextContext = productionContextBuilder(profile, history, records = records).build()
        val next = FakeWorkoutPlanner().generateWorkout(nextContext)
        assertThat(next.exercises.single().prescription.repRange!!.max).isEqualTo(base.repRange.max + 2)
        val changed = productionContextBuilder(
            profile.copy(goals = setOf(FitnessGoal.ATHLETIC_PERFORMANCE)), history, records = records
        ).build()
        assertThat(FakeWorkoutPlanner().generateWorkout(changed).progressionDecisions.single().reason)
            .isEqualTo(wallcrawl.elopenmike.com.core.model.ProgressionReason.HOLD_CONFIGURATION_CHANGED)
    }

    @Test
    fun repairSuppressesAProgressionInsteadOfChangingBothSetsAndLoad() = runTest {
        val ids = setOf("dumbbell-bench-press", "machine-chest-press")
        val profile = fullGymProfile().copy(
            preferredUnit = WeightUnit.KG,
            confirmedStartingLoads = ids.associateWith { 40.0 },
            excludedExerciseIds = bundledExercises.map { it.id }.filterNot { it in ids }
        )
        val baseContext = productionContextBuilder(profile).build()
        val history = ids.flatMapIndexed { index, id ->
            val exercise = bundledExercises.single { it.id == id }
            val reference = DefaultExercisePrescriptionFactory().create(exercise, baseContext)
            listOf(
                progressionSession("old-$index", id, reference, schedulingNow() - 9 * DAY_MILLIS + index * HOUR_MILLIS),
                progressionSession("new-$index", id, reference, schedulingNow() - 8 * DAY_MILLIS + index * HOUR_MILLIS)
            )
        } + completedSessionOf("current-dose", bundledExercises.single { it.id == "cable-fly" }, 3, schedulingNow() - HOUR_MILLIS)
        val context = productionContextBuilder(profile, history).build()
        val proposal = FakeWorkoutPlanner().generateWorkout(context)
        assertThat(proposal.progressionDecisions.all { it.axis == wallcrawl.elopenmike.com.core.model.ProgressionAxis.LOAD }).isTrue()
        val validator = ProgramValidator(GeneratedWorkoutValidator(InMemoryExerciseCatalog(bundledExercises)))
        val result = validator.validate(proposal, context, allowRepair = true) as ProgramValidationResult.Valid
        assertThat(result.snapshot.outcome).isEqualTo(RecommendationOutcome.REPAIRED)
        val reduced = result.workout.progressionDecisions.single {
            it.reason == wallcrawl.elopenmike.com.core.model.ProgressionReason.HOLD_VALIDATION_REPAIR
        }
        assertThat(reduced.axis).isNull()
        assertThat(reduced.prescription.targetWeight).isEqualTo(40.0)
        assertThat(reduced.prescription.targetSets).isEqualTo(1)
        assertThat(validator.validate(result.workout, context, allowRepair = false))
            .isInstanceOf(ProgramValidationResult.Valid::class.java)
    }

    @Test
    fun assistanceCannotBeAuthorizedByExternalLoadConfirmationOrByTheLegacyIncrement() = runTest {
        val id = "assisted-chin-up"
        val profile = fullGymProfile().copy(
            confirmedStartingLoads = mapOf(id to 40.0),
            excludedExerciseIds = bundledExercises.map { it.id }.filterNot { it == id }
        )
        val context = productionContextBuilder(profile).build()
        val normal = FakeWorkoutPlanner().generateWorkout(context)
        val tampered = normal.copy(
            progressionDecisions = emptyList(),
            exercises = normal.exercises.map { it.copy(prescription = it.prescription.copy(targetAssistanceWeight = 40.0)) }
        )
        val validation = ProgramValidator(GeneratedWorkoutValidator(InMemoryExerciseCatalog(bundledExercises)))
            .validate(tampered, context)
        assertThat((validation as ProgramValidationResult.Invalid).violations.map { it.code })
            .contains(ProgramViolationCode.UNTRACEABLE_LOAD)
    }

    @Test
    fun removingProgressionProvenanceCannotBypassTheProductionSingleAxisContract() = runTest {
        val context = productionContextBuilder(bodyweightProfile()).build()
        val plan = FakeWorkoutPlanner().generateWorkout(context)
        val stripped = plan.copy(progressionDecisions = emptyList())
        val validation = ProgramValidator(GeneratedWorkoutValidator(InMemoryExerciseCatalog(bundledExercises)))
            .validate(stripped, context)
        assertThat(validation).isInstanceOf(ProgramValidationResult.Invalid::class.java)
        assertThat((validation as ProgramValidationResult.Invalid).violations.map { it.code })
            .contains(ProgramViolationCode.PROGRESSION_POLICY_MISMATCH)
    }

    @Test
    fun duplicateHistoricalRecommendationRowsAreRejectedInsteadOfLastWriteWinning() = runTest {
        val context = productionContextBuilder(bodyweightProfile()).build()
        val workout = FakeWorkoutPlanner().generateWorkout(context)
        val exercise = workout.exercises.first()
        val session = progressionSession("source", exercise.exerciseId, exercise.prescription, schedulingNow() - DAY_MILLIS)
        val record = progressionRecord("source", workout.progressionDecisions)
        try {
            productionContextBuilder(bodyweightProfile(), listOf(session), records = listOf(record, record)).build()
            org.junit.Assert.fail("Duplicate records must not be silently collapsed.")
        } catch (expected: IllegalArgumentException) {
            assertThat(expected.message).contains("recommendation")
        }
    }

    @Test
    fun warmupOnlyAndFutureWorkCannotSupplyAReviewedStartingLoad() = runTest {
        val id = "dumbbell-bench-press"
        val exercise = bundledExercises.single { it.id == id }
        val profile = fullGymProfile().copy(
            preferredUnit = WeightUnit.KG,
            excludedExerciseIds = bundledExercises.map { it.id }.filterNot { it == id }
        )
        val reference = DefaultExercisePrescriptionFactory().create(
            exercise, productionContextBuilder(profile.copy(confirmedStartingLoads = mapOf(id to 40.0))).build()
        )
        val normal = progressionSession("normal", id, reference, schedulingNow() - DAY_MILLIS)
        val warmup = normal.copy(exercises = normal.exercises.map { ex ->
            ex.copy(sets = ex.sets.map { it.copy(type = wallcrawl.elopenmike.com.core.model.SetType.WARMUP) })
        })
        val future = progressionSession("future", id, reference, schedulingNow() + DAY_MILLIS)
        for (history in listOf(listOf(warmup), listOf(future))) {
            val context = productionContextBuilder(profile, history).build()
            assertThat(FakeWorkoutPlanner().generateWorkout(context).exercises.single().prescription.targetWeight).isNull()
        }
        val actualWork = productionContextBuilder(profile, listOf(normal)).build()
        assertThat(FakeWorkoutPlanner().generateWorkout(actualWork).exercises.single().prescription.targetWeight)
            .isEqualTo(40.0)
    }

    @Test
    fun progressionUsesTheActualAcceptedCohortThroughContextLedgerPlannerAndValidator() = runTest {
        val axes = mapOf(
            "dumbbell-bench-press" to wallcrawl.elopenmike.com.core.model.ProgressionAxis.LOAD,
            "push-up" to wallcrawl.elopenmike.com.core.model.ProgressionAxis.REP_RANGE,
            "assisted-chin-up" to wallcrawl.elopenmike.com.core.model.ProgressionAxis.ASSISTANCE,
            "wall-sit" to wallcrawl.elopenmike.com.core.model.ProgressionAxis.DURATION
        )
        for ((id, axis) in axes) {
            val exercise = bundledExercises.single { it.id == id }
            assertThat(exercise.reviewedMetadata?.reviewState).isEqualTo(ReviewState.AI_ACCEPTED)
            val profile = fullGymProfile().copy(
                preferredUnit = WeightUnit.KG,
                confirmedStartingLoads = if (axis == wallcrawl.elopenmike.com.core.model.ProgressionAxis.LOAD) mapOf(id to 40.0) else emptyMap(),
                excludedExerciseIds = bundledExercises.map { it.id }.filterNot { it == id }
            )
            val empty = productionContextBuilder(profile).build()
            var reference = DefaultExercisePrescriptionFactory().create(exercise, empty)
            if (axis == wallcrawl.elopenmike.com.core.model.ProgressionAxis.ASSISTANCE) {
                reference = reference.copy(targetAssistanceWeight = 20.0)
            }
            val history = listOf(
                progressionSession("one", id, reference, schedulingNow() - 9 * DAY_MILLIS),
                progressionSession("two", id, reference, schedulingNow() - 8 * DAY_MILLIS)
            )
            val context = productionContextBuilder(profile, history).build()
            val plan = FakeWorkoutPlanner().generateWorkout(context)
            assertThat(plan.exercises).hasSize(1)
            val result = ProgramValidator(GeneratedWorkoutValidator(InMemoryExerciseCatalog(bundledExercises)))
                .validate(plan, context, allowRepair = true)
            assertWithMessage(id).that(result).isInstanceOf(ProgramValidationResult.Valid::class.java)
            val valid = result as ProgramValidationResult.Valid
            assertThat(valid.workout.progressionDecisions.single().axis).isEqualTo(axis)
            assertThat(wallcrawl.elopenmike.com.core.model.ProgressionReasonCode.decode(
                valid.snapshot.asRecord("started", schedulingNow()).reasonCodes
            ).single().axis).isEqualTo(axis)
            val accepted = wallcrawl.elopenmike.com.core.model.DeloadPreferences(
                profile.id, 1, wallcrawl.elopenmike.com.core.model.DeloadChoice(
                    wallcrawl.elopenmike.com.core.model.DeloadOffer(
                        "request", wallcrawl.elopenmike.com.core.model.DeloadSource.EXPLICIT_REQUEST, DeloadOfferPolicy.VERSION
                    ),
                    wallcrawl.elopenmike.com.core.model.DeloadChoiceStatus.ACCEPTED, 1
                )
            )
            val deloadContext = productionContextBuilder(profile, history, deload = accepted).build()
            val held = FakeWorkoutPlanner().generateWorkout(deloadContext)
            assertThat(held.exercises.single().prescription)
                .isEqualTo(reference.copy(targetSets = maxOf(1, reference.targetSets - 1)))
            assertThat(held.progressionDecisions.single().axis).isNull()
            assertThat(held.progressionDecisions.single().reason)
                .isEqualTo(wallcrawl.elopenmike.com.core.model.ProgressionReason.HOLD_ACCEPTED_DELOAD)
            assertThat(ProgramValidator(GeneratedWorkoutValidator(InMemoryExerciseCatalog(bundledExercises)))
                .validate(held, deloadContext)).isInstanceOf(ProgramValidationResult.Valid::class.java)
        }
    }

    @Test
    fun displayedOrDeclinedDeloadDoesNotChangeProductionPlan_butAcceptedChoiceDoes() = runTest {
        val profile = bodyweightProfile()
        val offer = wallcrawl.elopenmike.com.core.model.DeloadOffer(
            "request", wallcrawl.elopenmike.com.core.model.DeloadSource.EXPLICIT_REQUEST, DeloadOfferPolicy.VERSION
        )
        val plain = productionContextBuilder(profile).build()
        val baseline = FakeWorkoutPlanner().generateWorkout(plain)
        for (status in listOf(
            wallcrawl.elopenmike.com.core.model.DeloadChoiceStatus.OFFERED,
            wallcrawl.elopenmike.com.core.model.DeloadChoiceStatus.DECLINED,
            wallcrawl.elopenmike.com.core.model.DeloadChoiceStatus.ACCEPTED
        )) {
            val preferences = wallcrawl.elopenmike.com.core.model.DeloadPreferences(
                profile.id, 1, wallcrawl.elopenmike.com.core.model.DeloadChoice(offer, status, 1)
            )
            val context = productionContextBuilder(profile, deload = preferences).build()
            assertThat(context.allowedExercises).isEqualTo(plain.allowedExercises)
            val accepted = status == wallcrawl.elopenmike.com.core.model.DeloadChoiceStatus.ACCEPTED
            assertThat(context.trainingProgramState?.adaptationState)
                .isEqualTo(if (accepted) AdaptationState.HOLD else AdaptationState.UNCALIBRATED)
            val plan = FakeWorkoutPlanner().generateWorkout(context)
            val expected = baseline.exercises.map {
                if (accepted) it.copy(prescription = it.prescription.copy(targetSets = maxOf(1, it.targetSets - 1))) else it
            }
            assertThat(plan.exercises).isEqualTo(expected)
            val result = ProgramValidator(GeneratedWorkoutValidator(InMemoryExerciseCatalog(bundledExercises)))
                .validate(plan, context, allowRepair = true)
            assertThat(result).isInstanceOf(ProgramValidationResult.Valid::class.java)
            val snapshot = (result as ProgramValidationResult.Valid).snapshot
            assertThat(snapshot.deloadDecisionRevision).isEqualTo(1)
            assertThat(snapshot.acceptedDeloadOfferId).isEqualTo(if (accepted) offer.id else null)
        }
    }

    @Test
    fun recencyAloneChangesBothPassesAndPreservesTheOtherPrescriptionInputs() = runTest {
        val oldHistory = schedulingHistory()
        val recentHistory = oldHistory.mapIndexed { index, session ->
            session.copy(completedAtTimestamp = schedulingNow() - index * DAY_MILLIS)
        }
        val old = productionContextBuilder(schedulingProfile(), oldHistory).build()
        val recent = productionContextBuilder(schedulingProfile(), recentHistory).build()
        val repeat = FakeWorkoutPlanner().generateWorkout(old)
        val neutral = FakeWorkoutPlanner().generateWorkout(recent)
        assertThat(old.completedWorkoutCount).isEqualTo(recent.completedWorkoutCount)
        assertThat(old.allowedExercises).isEqualTo(recent.allowedExercises)
        assertThat(repeat.exercises.map { it.exerciseId }).containsExactly(
            "dumbbell-shoulder-press", "dumbbell-bench-press", "cable-lateral-raise"
        ).inOrder()
        assertThat(neutral.exercises.map { it.exerciseId }).containsExactly(
            "dumbbell-bench-press", "dumbbell-shoulder-press", "cable-fly"
        ).inOrder()
        assertThat(neutral.rankingReasons).isEmpty()
        // Recency changes order only, not the prescribed targets for any candidate.
        val factory = DefaultExercisePrescriptionFactory()
        old.allowedExercises.forEach {
            assertThat(factory.create(it, old)).isEqualTo(factory.create(it, recent))
        }
    }

    @Test
    fun allFourteenDatesAreReadEvenWhenPracticeIsOutsideTheRecentEightAndCurrentWeek() = runTest {
        val history = schedulingHistory().map {
            it.copy(completedAtTimestamp = requireNotNull(it.completedAtTimestamp) - 7 * DAY_MILLIS)
        } + (1..9).map { index ->
            completedSession("empty-$index", schedulingNow() - HOUR_MILLIS, emptyList())
        }
        val context = productionContextBuilder(schedulingProfile(), history).build()
        assertThat(context.recentWorkoutHistory).hasSize(8)
        assertThat(context.exerciseHistory).isEmpty()
        assertThat(context.schedulingEvidence?.practiceDates?.get("Shoulders")).hasSize(2)
        assertThat(FakeWorkoutPlanner().generateWorkout(context).exercises.first().exerciseId)
            .isEqualTo("dumbbell-shoulder-press")
        assertThat(context.trainingProgramState?.weeklyLedger?.creditedWorkSets).isEqualTo(0)
    }

    @Test
    fun samePrimaryHasNoExactExerciseNoveltyRotationOrSignalOnlyReason() = runTest {
        val ids = setOf("arnold-press", "dumbbell-shoulder-press", "standing-dumbbell-press", "cable-lateral-raise")
        val context = productionContextBuilder(schedulingProfile(ids), schedulingHistory()).build()
        val actual = FakeWorkoutPlanner().generateWorkout(context)
        val baseline = FakeWorkoutPlanner().generateWorkout(context.copy(schedulingEvidence = null))
        assertThat(actual.exercises).isEqualTo(baseline.exercises)
        assertThat(actual.rankingReasons).isEmpty()
        assertThat(actual.exercises.first().exerciseId).isEqualTo("arnold-press")
    }

    @Test
    fun capabilityExperienceAndFocusRemainStrongerThanScheduling() = runTest {
        val ids = setOf("overhead-press", "dumbbell-bench-press", "cable-fly")
        val limited = schedulingProfile(ids).copy(
            movementCapabilities = MovementCapabilities.from(
                MovementCapabilityType.entries.associateWith { CapabilityLevel.COMFORTABLE } +
                    (MovementCapabilityType.BALANCE_WITHOUT_SUPPORT to CapabilityLevel.LIMITED)
            )
        )
        val limitedContext = productionContextBuilder(limited, schedulingHistory("overhead-press")).build()
        val limitedPlan = FakeWorkoutPlanner().generateWorkout(limitedContext)
        assertThat(limitedPlan.exercises.first().exerciseId).isEqualTo("dumbbell-bench-press")
        assertThat(limitedPlan.rankingReasons).isEmpty()

        val beginner = schedulingProfile(setOf("machine-chest-press", "dumbbell-shoulder-press", "cable-fly"))
            .copy(experienceLevel = ExperienceLevel.BEGINNER)
        val beginnerContext = productionContextBuilder(beginner, schedulingHistory()).build()
        val beginnerPlan = FakeWorkoutPlanner().generateWorkout(beginnerContext)
        assertThat(beginnerPlan.exercises.first().exerciseId).isEqualTo("machine-chest-press")
        assertThat(beginnerPlan.rankingReasons).isEmpty()

        val focusProfile = schedulingProfile().copy(musclePriorities = mapOf(StandardMuscles.CHEST to PriorityLevel.HIGH))
        val focusContext = productionContextBuilder(focusProfile, schedulingHistory()).build()
        val focused = FakeWorkoutPlanner().generateWorkout(focusContext)
        assertThat(focused.title.split).isEqualTo(wallcrawl.elopenmike.com.core.model.WorkoutSplit.FULL_BODY)
        assertThat(focused.rankingReasons).isEmpty()
        val validator = ProgramValidator(GeneratedWorkoutValidator(InMemoryExerciseCatalog(bundledExercises)))
        for ((context, plan) in listOf(limitedContext to limitedPlan, beginnerContext to beginnerPlan, focusContext to focused)) {
            val raw = validator.validate(plan, context, allowRepair = false)
            if (raw is ProgramValidationResult.Invalid) {
                assertThat(raw.violations.map { it.code }.distinct())
                    .containsExactly(ProgramViolationCode.WEEKLY_ALLOWANCE_EXCEEDED)
            }
            assertThat(validator.validate(plan, context, allowRepair = true))
                .isInstanceOf(ProgramValidationResult.Valid::class.java)
        }
    }

    @Test
    fun recordedRegenerationInputReplaysWithoutBecomingPartOfStartFreshness() = runTest {
        val context = productionContextBuilder(schedulingProfile(), schedulingHistory()).build()
        val planner = FakeWorkoutPlanner()
        repeat(4) { index ->
            val plan = planner.generateWorkout(context)
            assertThat(plan.generationIndex).isEqualTo(index)
            val replay = context.copy(regenerationIndex = index)
            assertThat(FakeWorkoutPlanner().generateWorkout(replay).normalizedPlannerFixtureWorkout())
                .isEqualTo(plan.normalizedPlannerFixtureWorkout())
            assertThat(RecommendationContextIdentity.of(replay)).isEqualTo(RecommendationContextIdentity.of(context))
        }
    }

    private fun schedulingNow() = TrainingWeek.startingOn(MONDAY_EPOCH_DAY, LEDGER_ZONE).startEpochMillis + 3 * DAY_MILLIS

    private fun schedulingHistory(exerciseId: String = "dumbbell-shoulder-press") = listOf(3L, 2L).map { age ->
        completedSessionOf("practice-$age", bundledExercises.single { it.id == exerciseId }, 1,
            schedulingNow() - age * DAY_MILLIS)
    }

    private fun schedulingProfile(ids: Set<String> = setOf(
        "dumbbell-bench-press", "dumbbell-shoulder-press", "cable-fly", "cable-lateral-raise"
    )) = fullGymProfile().copy(
        daysPerWeek = 6, preferredDurationMinutes = 30,
        musclePriorities = mapOf(StandardMuscles.SHOULDERS to PriorityLevel.HIGH),
        excludedExerciseIds = bundledExercises.map(Exercise::id).filterNot { it in ids }
    )
    @Test
    fun frequencyAloneReordersBothPassesUsingActualCompletedPrimaryWork() = runTest {
        val ids = setOf(
            "dumbbell-bench-press", "dumbbell-shoulder-press",
            "cable-fly", "cable-lateral-raise"
        )
        val history = schedulingHistory()
        val profile = fullGymProfile().copy(
            preferredDurationMinutes = 30,
            musclePriorities = mapOf(StandardMuscles.SHOULDERS to PriorityLevel.HIGH),
            excludedExerciseIds = bundledExercises.map(Exercise::id).filterNot { it in ids }
        )
        val twice = productionContextBuilder(profile.copy(daysPerWeek = 2), history).build()
        val six = productionContextBuilder(profile.copy(daysPerWeek = 6), history).build()
        val before = FakeWorkoutPlanner().generateWorkout(twice)
        val after = FakeWorkoutPlanner().generateWorkout(six)

        assertThat(six.allowedExercises).containsExactlyElementsIn(twice.allowedExercises).inOrder()
        assertThat(before.exercises.map { it.exerciseId }).containsExactly(
            "dumbbell-bench-press", "dumbbell-shoulder-press", "cable-fly"
        ).inOrder()
        assertThat(after.exercises.map { it.exerciseId }).containsExactly(
            "dumbbell-shoulder-press", "dumbbell-bench-press", "cable-lateral-raise"
        ).inOrder()
        val expectedReasons = listOf(
            WorkoutRankingReason.TrainingFrequencyRecencyPreference(
                "dumbbell-shoulder-press", "dumbbell-bench-press", StandardMuscles.SHOULDERS, 6, 2
            ),
            WorkoutRankingReason.TrainingFrequencyRecencyPreference(
                "cable-lateral-raise", "cable-fly", StandardMuscles.SHOULDERS, 6, 2
            )
        )
        assertThat(before.rankingReasons).isEmpty()
        assertThat(after.rankingReasons).containsExactlyElementsIn(expectedReasons).inOrder()
        for ((context, workout) in listOf(twice to before, six to after)) {
            val validation = ProgramValidator(GeneratedWorkoutValidator(InMemoryExerciseCatalog(bundledExercises)))
                .validate(workout, context, allowRepair = true)
            assertThat(validation)
                .isInstanceOf(ProgramValidationResult.Valid::class.java)
            val record = (validation as ProgramValidationResult.Valid).snapshot.asRecord("test", 1)
            val expected = if (context == six) expectedReasons else emptyList()
            assertThat(validation.snapshot.rankingReasons).containsExactlyElementsIn(expected).inOrder()
            assertThat(WorkoutRankingReasonCode.decode(record.reasonCodes))
                .containsExactlyElementsIn(expected).inOrder()
            assertThat(record.reasonCodes).contains("TRAINING_FREQUENCY_RECENCY_V1")
            assertThat(record.reasonCodes).contains("PLANNER_GENERATION_V1:0")
        }
    }

    @Test
    fun productionInjectsTheEnabledReviewedFlagAndNothingElseBuildsItsOwn() {
        assertThat(PlannerFeatureFlags.PRODUCTION.reviewedCapabilityEligibility).isTrue()

        val application =
            File("src/main/java/wallcrawl/elopenmike/com/WallCrawlApplication.kt").readText()
        assertThat(application).contains("plannerFeatureFlags = PlannerFeatureFlags.PRODUCTION")
        assertThat(application).doesNotContain("PlannerFeatureFlags(")
    }

    @Test
    fun theProductionBuilderReadsTheActualAcceptedCohortWithoutAnySyntheticApproval() = runTest {
        val profile = fullGymProfile()
        val context = productionContextBuilder(profile).build()

        assertThat(bundledExercises.none {
            it.reviewedMetadata?.reviewState == ReviewState.APPROVED
        }).isTrue()
        assertThat(acceptedIds).hasSize(182)
        val result = context.automaticEligibilityResult
        assertThat(result).isInstanceOf(AutomaticEligibilityResult.Candidates::class.java)
        assertThat(context.allowedExercises.map(Exercise::id)).isNotEmpty()
        // Every candidate on the production path is an actual AI-accepted record.
        assertThat(acceptedIds).containsAtLeastElementsIn(context.allowedExercises.map(Exercise::id))
        context.allowedExercises.forEach { exercise ->
            val metadata = requireNotNull(exercise.acceptedMetadata()) { exercise.id }
            assertWithMessage(exercise.id).that(metadata.reviewState)
                .isEqualTo(ReviewState.AI_ACCEPTED)
            assertWithMessage(exercise.id).that(metadata.aiReviewProvenance).isNotNull()
            assertWithMessage(exercise.id).that(metadata.provenance.reviewerRole).isNull()
        }
        assertThat(context.reviewPolicyVersion).isEqualTo(2)
        assertThat(context.trainingProgramState).isNotNull()
    }

    @Test
    fun everySupportedCohortIsServedFromAcceptedRecordsWithATruthfulPlan() = runTest {
        for (cohort in SUPPORTED_COHORTS) {
            val outcome = planFor(cohort)
            assertWithMessage(cohort.name).that(outcome).isInstanceOf(Served::class.java)
            val served = outcome as Served
            val plan = served.workout

            assertWithMessage(cohort.name).that(plan.exercises).isNotEmpty()
            // Only actually accepted catalog records are ever prescribed.
            assertWithMessage(cohort.name).that(acceptedIds)
                .containsAtLeastElementsIn(plan.exercises.map { it.exerciseId })
            // The advertised focus is trained by the plan's own exercises.
            assertWithMessage(cohort.name).that(plan.focusMuscles).isNotEmpty()
            val trained = plan.exercises.flatMap { planned ->
                val metadata = requireNotNull(served.byId.getValue(planned.exerciseId).acceptedMetadata())
                listOf(metadata.directPrimaryMuscle) + metadata.descriptiveSecondaryMuscles
            }.toSet()
            assertWithMessage(cohort.name).that(trained).containsAtLeastElementsIn(plan.focusMuscles)

            plan.exercises.forEach { planned ->
                val exercise = served.byId.getValue(planned.exerciseId)
                val message = "${cohort.name}/${planned.exerciseId}"
                assertWithMessage(message).that(planned.prescription.exerciseType)
                    .isEqualTo(exercise.type)
                assertWithMessage(message).that(planned.targetSets).isIn(1..MAX_SETS_PER_EXERCISE)
                assertWithMessage(message).that(planned.restSeconds).isIn(15..600)
                if (exercise.type != ExerciseType.DURATION &&
                    exercise.type != ExerciseType.DISTANCE_DURATION
                ) {
                    val repRange = requireNotNull(planned.prescription.repRange) { message }
                    assertWithMessage(message).that(repRange.min).isAtLeast(1)
                    assertWithMessage(message).that(repRange.max).isAtLeast(repRange.min)
                } else {
                    assertWithMessage(message)
                        .that(planned.prescription.targetDurationSeconds)
                        .isNotNull()
                }
            }
            assertWithMessage(cohort.name).that(served.snapshot.reviewedPathEnabled).isTrue()
            assertWithMessage(cohort.name).that(served.snapshot.trainingPolicyVersion)
                .isEqualTo(TrainingPolicyVersion.STATE_BASED_DOSE_EFFORT_REST_V2)
            assertWithMessage(cohort.name).that(served.snapshot.ledgerPolicyVersion)
                .isEqualTo(LedgerPolicyVersion.PRIMARY_ONLY_V1)
            assertWithMessage(cohort.name).that(served.snapshot.outcome)
                .isIn(listOf(RecommendationOutcome.VALID, RecommendationOutcome.REPAIRED))
        }
    }

    @Test
    fun everySupportedCohortHonoursItsEquipmentAnchorsAndExclusions() = runTest {
        for (cohort in SUPPORTED_COHORTS) {
            val served = planFor(cohort) as Served
            val owned = cohort.profile.availableEquipment.toSet()

            served.workout.exercises.forEach { planned ->
                val exercise = served.byId.getValue(planned.exerciseId)
                val metadata = requireNotNull(exercise.acceptedMetadata())
                val message = "${cohort.name}/${planned.exerciseId}"
                assertWithMessage(message)
                    .that(metadata.equipmentAlternatives.any { owned.containsAll(it) })
                    .isTrue()
                // A fixed band anchor is never inferred from an incidental object.
                fixedAnchorBandAlternativesOf(exercise)?.let { alternatives ->
                    assertWithMessage(message)
                        .that(alternatives.any { owned.containsAll(it) }).isTrue()
                }
                assertWithMessage(message)
                    .that(planned.exerciseId in cohort.profile.excludedExerciseIds).isFalse()
                metadata.capabilityRequirements.forEach { capability ->
                    assertWithMessage("$message/$capability")
                        .that(cohort.profile.movementCapabilities[capability])
                        .isNotEqualTo(CapabilityLevel.AVOID)
                }
            }
        }
    }

    @Test
    fun anAvoidedCapabilityRemovesEveryExerciseThatNeedsItWithoutStoppingThePlan() = runTest {
        val comfortable = planFor(Cohort("full-gym", fullGymProfile())) as Served
        val needsBalance = comfortable.byId.values
            .filter {
                MovementCapabilityType.BALANCE_WITHOUT_SUPPORT in
                    requireNotNull(it.acceptedMetadata()).capabilityRequirements
            }
            .map(Exercise::id)
            .toSet()
        assertThat(needsBalance).isNotEmpty()

        val avoiding = fullGymProfile().copy(
            movementCapabilities = MovementCapabilities.from(
                MovementCapabilityType.entries.associateWith { CapabilityLevel.COMFORTABLE } +
                    mapOf(MovementCapabilityType.BALANCE_WITHOUT_SUPPORT to CapabilityLevel.AVOID)
            )
        )
        val context = productionContextBuilder(avoiding).build()
        assertThat(context.allowedExercises.map(Exercise::id).intersect(needsBalance)).isEmpty()
        assertThat(context.allowedExercises).isNotEmpty()

        // Avoiding every capability still leaves a plan, because accepted records that
        // declare no capability requirement are unaffected by any capability answer. That is
        // why `CAPABILITIES_REMOVED_ALL` is not reachable from this corpus, and asserting it
        // as an unsupported cohort would be asserting something untrue.
        val avoidingEverything = fullGymProfile().copy(
            movementCapabilities = MovementCapabilities.from(
                MovementCapabilityType.entries.associateWith { CapabilityLevel.AVOID }
            )
        )
        val allAvoided = productionContextBuilder(avoidingEverything).build().allowedExercises
        assertThat(allAvoided).isNotEmpty()
        allAvoided.forEach { exercise ->
            assertWithMessage(exercise.id)
                .that(requireNotNull(exercise.acceptedMetadata()).capabilityRequirements).isEmpty()
        }
    }

    @Test
    fun anUnsupportedCombinationReturnsTheTypedRefusalAndNeverALegacyFallback() = runTest {
        for (cohort in UNSUPPORTED_COHORTS) {
            val outcome = planFor(cohort.cohort)
            assertWithMessage(cohort.cohort.name).that(outcome).isInstanceOf(Refused::class.java)
            val refused = outcome as Refused
            assertWithMessage(cohort.cohort.name).that(refused.planningFailure)
                .isEqualTo(WorkoutPlanningFailure.REVIEWED_ELIGIBILITY_NO_CANDIDATES)
            assertWithMessage(cohort.cohort.name).that(refused.eligibilityFailure)
                .isEqualTo(cohort.expectedFailure)
            // A legacy filter would have produced candidates here; the reviewed path must not.
            assertWithMessage(cohort.cohort.name).that(refused.allowedExerciseIds).isEmpty()
        }
    }

    @Test
    fun aSelectedJointConstraintRefusesAutomaticPlanningRatherThanIgnoringIt() = runTest {
        // No accepted record clears any joint constraint, so selecting one is a refusal on
        // the production path. Ignoring the selection silently would be the untruthful
        // alternative, and the legacy filter does exactly that.
        val constrained = fullGymProfile().copy(
            trainingConstraints = setOf(TrainingConstraint.KNEE_SENSITIVE)
        )
        val legacyCandidates = ExerciseFilter().filterCandidates(bundledExercises, constrained)
        assertThat(legacyCandidates).isNotEmpty()

        val refused = planFor(Cohort("knee-sensitive", constrained)) as Refused
        assertThat(refused.eligibilityFailure)
            .isEqualTo(AutomaticEligibilityFailure.TRAINING_CONSTRAINTS_REMOVED_ALL)
    }

    @Test
    fun lowImpactOnlyIsHonouredByImpactLevelAndStillLeavesAPlan() = runTest {
        // The one selection on the safety step that is decided by each record's own impact
        // level rather than by a clearance, which is why the copy names it separately.
        val lowImpactOnly = fullGymProfile().copy(
            trainingConstraints = setOf(TrainingConstraint.LOW_IMPACT_ONLY)
        )
        val served = planFor(Cohort("low-impact-only", lowImpactOnly)) as Served
        assertThat(served.workout.exercises).isNotEmpty()
        served.byId.values.forEach { exercise ->
            assertWithMessage(exercise.id)
                .that(requireNotNull(exercise.acceptedMetadata()).impactLevel)
                .isNotEqualTo(ImpactLevel.HIGH)
        }
    }

    @Test
    fun preexistingCompletedHistoryIsAttributedAndSpendsTheWeeklyAllowance() = runTest {
        // History recorded before acceptance is not mutated or reset; it simply becomes
        // attributable, because the exercises it names now resolve to accepted records.
        val benchPress = bundledExercises.single { it.id == "barbell-bench-press" }
        assertThat(benchPress.acceptedMetadata()).isNotNull()
        val week = TrainingWeek.startingOn(MONDAY_EPOCH_DAY, LEDGER_ZONE)
        val history = listOf(
            completedSessionOf(
                sessionId = "pre-acceptance",
                exercise = benchPress,
                completedSets = 4,
                completedAtEpochMillis = week.startEpochMillis + HOUR_MILLIS
            )
        )

        val attributed = ledgerFor(history, bundledExercises, week)
        assertThat(attributed.directPrimarySets)
            .containsEntry(requireNotNull(benchPress.acceptedMetadata()).directPrimaryMuscle, 4)
        assertThat(attributed.unattributedWorkSets).isEmpty()

        // The same history against the pre-acceptance view of the catalog is unattributed,
        // which is what makes the attribution above the consequence of acceptance.
        val pending = bundledExercises.map { exercise ->
            exercise.reviewedMetadata?.takeIf { it.reviewState == ReviewState.AI_ACCEPTED }
                ?.let { metadata ->
                    exercise.copy(
                        reviewedMetadata = metadata.copy(
                            reviewState = ReviewState.DRAFT,
                            aiReviewProvenance = null
                        )
                    )
                } ?: exercise
        }
        val unattributed = ledgerFor(history, pending, week)
        assertThat(unattributed.directPrimarySets).isEmpty()
        assertThat(unattributed.unattributedWorkSets.values.sum()).isEqualTo(4)

        // Acceptance, content and review-policy changes each move the cache key, so a
        // derived ledger cached before acceptance can never be reused after it.
        assertThat(fingerprintOf(history, attributedExercises = bundledExercises, week = week))
            .isNotEqualTo(fingerprintOf(history, attributedExercises = pending, week = week))
        assertThat(
            fingerprintOf(history, bundledExercises, week, reviewPolicyVersion = 2)
        ).isNotEqualTo(fingerprintOf(history, bundledExercises, week, reviewPolicyVersion = 3))
    }

    @Test
    fun aWeekAlreadyAtItsAllowanceRefusesRatherThanPrescribingOverIt() = runTest {
        val profile = bodyweightProfile()
        val week = TrainingWeek.startingOn(MONDAY_EPOCH_DAY, LEDGER_ZONE)
        val candidates = productionContextBuilder(profile).build().allowedExercises
        val exhausting = candidates.take(CANDIDATES_TRAINED_TO_EXHAUSTION).mapIndexed { index, exercise ->
            completedSessionOf(
                sessionId = "already-trained-$index",
                exercise = exercise,
                completedSets = SETS_THAT_EXHAUST_ANY_ALLOWANCE,
                completedAtEpochMillis = week.startEpochMillis + HOUR_MILLIS + index
            )
        }

        val ledger = ledgerFor(exhausting, bundledExercises, week)
        assertThat(ledger.directPrimarySets.values.any { it >= SETS_THAT_EXHAUST_ANY_ALLOWANCE })
            .isTrue()

        val outcome = planFor(
            Cohort("bodyweight-exhausted", profile),
            history = exhausting,
            week = week
        )
        // Either the proposal is reduced to fit, or planning refuses for the spent
        // allowance — never accepted over an allowance that is already gone.
        when (outcome) {
            is Served -> {
                outcome.snapshot.doseAccounting.forEach { accounting ->
                    assertWithMessage(accounting.muscle)
                        .that(accounting.completedSets + accounting.proposedSets)
                        .isAtMost(accounting.allowanceSets)
                }
            }

            is Refused -> assertThat(
                outcome.policyRefusal == TrainingPolicyResult.NoGuidance(
                    TrainingPolicyNoGuidanceReason.WEEKLY_DIRECT_PRIMARY_ALLOWANCE_EXHAUSTED
                ) || ProgramViolationCode.WEEKLY_ALLOWANCE_EXCEEDED in outcome.violationCodes
            ).isTrue()
        }
    }

    @Test
    fun aRecommendationFromTheEnabledPathPersistsItsWholeReviewedProvenance() = runTest {
        // Enabling the rollout is the first time a production record carries anything in the
        // reviewed-only columns: every shipped record so far left them null. Nothing about
        // the stored shape changed, so this is a regression check on the existing envelope
        // rather than a migration.
        val served = planFor(Cohort("full-gym", fullGymProfile())) as Served
        val record = served.snapshot.asRecord(
            sessionId = "session-under-test",
            recordedAtEpochMillis = 1_789_000_000_000L
        )

        assertThat(record.reviewedPathEnabled).isTrue()
        assertThat(record.catalogVersion).isEqualTo(bundledCatalog.sourceCommit)
        assertThat(record.reviewPolicyVersion).isEqualTo(REVIEW_POLICY_VERSION)
        assertThat(record.trainingPolicyVersion).isEqualTo("STATE_BASED_DOSE_EFFORT_REST_V2")
        assertThat(record.ledgerPolicyVersion).isEqualTo("PRIMARY_ONLY_V1")
        assertThat(record.programStatePolicyVersion).isEqualTo("PROGRAM_STATE_V2")
        assertThat(record.adaptationState).isEqualTo(AdaptationState.UNCALIBRATED.name)
        assertThat(record.weekStartEpochDay).isEqualTo(MONDAY_EPOCH_DAY)
        assertThat(record.timeZoneId).isEqualTo(LEDGER_ZONE.id)
        assertThat(record.contextIdentity).hasLength(64)

        // Within the bounds the archive already enforces, so an export of this record needs
        // no format change.
        assertThat(record.reasonCodes.size).isAtMost(RecommendationRecord.MAX_REASON_CODES)
        assertThat(record.doseAccounting.size).isAtMost(RecommendationRecord.MAX_DOSE_ENTRIES)
        assertThat(record.doseAccounting.map { it.muscle }).containsNoDuplicates()
    }

    @Test
    fun aLimitedCapabilityRemovesNoSetInAnyStateProductionCanReach() = runTest {
        // The copy on both movement-preference surfaces may only claim what this proves.
        // `AdaptationStatePolicy` derives UNCALIBRATED or RETURNING and nothing else, and
        // both cap a single exercise at the same number the limited-capability rule does, so
        // answering Limited changes no set count. A copy change claiming a reduction has to
        // change this first.
        val defaults = StateBasedTrainingPolicyDefaults.V1
        val reachableStates = setOf(AdaptationState.UNCALIBRATED, AdaptationState.RETURNING)
        assertThat(AdaptationStatePolicy().derive(UserProfile())).isIn(reachableStates)
        assertThat(
            AdaptationStatePolicy().derive(UserProfile(returningAfterBreakWeeks = 12))
        ).isIn(reachableStates)
        reachableStates.forEach { state ->
            val limits = requireNotNull(defaults.doseLimitsByState[state]) { state.name }
            assertWithMessage(state.name).that(defaults.limitedCapabilityMaxTargetSets)
                .isAtLeast(limits.maxTargetSetsPerExercise)
        }

        // And the plans agree: the same profile answered Comfortable and Limited is
        // prescribed the same sets for the same exercises.
        val comfortable = planFor(
            Cohort("full-gym-comfortable", fullGymProfile())
        ) as Served
        val limited = planFor(
            Cohort(
                "full-gym-limited",
                fullGymProfile().copy(
                    movementCapabilities = MovementCapabilities.from(
                        MovementCapabilityType.entries.associateWith { CapabilityLevel.LIMITED }
                    )
                )
            )
        ) as Served
        assertThat(limited.workout.exercises.map { it.targetSets })
            .isEqualTo(comfortable.workout.exercises.map { it.targetSets })
    }

    // region harness

    private suspend fun planFor(
        cohort: Cohort,
        history: List<WorkoutSession> = cohort.history,
        week: TrainingWeek = TrainingWeek.startingOn(MONDAY_EPOCH_DAY, LEDGER_ZONE)
    ): PlanOutcome {
        val context = productionContextBuilder(cohort.profile, history, week).build()
        val catalog = InMemoryExerciseCatalog(bundledExercises)
        val generated = try {
            FakeWorkoutPlanner().generateWorkout(context)
        } catch (failure: WorkoutValidationException) {
            return Refused(
                planningFailure = failure.failure,
                eligibilityFailure = failure.automaticEligibilityFailure,
                allowedExerciseIds = context.allowedExercises.map(Exercise::id),
                violationCodes = emptyList(),
                policyRefusal = null
            )
        } catch (refusal: TrainingPolicyResultException) {
            // A spent allowance stops the prescription policy before a proposal exists, so
            // it never reaches whole-program validation.
            return Refused(
                planningFailure = null,
                eligibilityFailure = null,
                allowedExerciseIds = context.allowedExercises.map(Exercise::id),
                violationCodes = emptyList(),
                policyRefusal = refusal.result
            )
        }
        return when (
            val validation = ProgramValidator(GeneratedWorkoutValidator(catalog))
                .validate(workout = generated, context = context, allowRepair = true)
        ) {
            is ProgramValidationResult.Valid -> Served(
                workout = validation.workout,
                snapshot = validation.snapshot,
                byId = context.allowedExercises.associateBy(Exercise::id)
            )

            is ProgramValidationResult.Invalid -> Refused(
                planningFailure = null,
                eligibilityFailure = null,
                allowedExerciseIds = context.allowedExercises.map(Exercise::id),
                violationCodes = validation.violations.map { it.code },
                policyRefusal = null
            )
        }
    }

    private fun productionContextBuilder(
        profile: UserProfile,
        history: List<WorkoutSession> = emptyList(),
        week: TrainingWeek = TrainingWeek.startingOn(MONDAY_EPOCH_DAY, LEDGER_ZONE),
        deload: wallcrawl.elopenmike.com.core.model.DeloadPreferences? = null,
        records: List<RecommendationRecord> = emptyList()
    ) = WorkoutGenerationContextBuilder(
        userProfileRepository = StaticUserProfileRepository(profile),
        workoutRepository = StaticWorkoutRepository(history, records),
        exerciseCatalog = InMemoryExerciseCatalog(bundledExercises),
        exerciseFilter = ExerciseFilter(),
        historyAnalyzer = WorkoutHistoryAnalyzer(),
        // The production constant itself, not a locally constructed copy.
        plannerFeatureFlags = PlannerFeatureFlags.PRODUCTION,
        trainingProgramStateProvider = TrainingProgramStateProvider(
            weeklyDoseLedgerRepository = RecomputingLedgerRepository(
                sessions = history,
                exercises = bundledExercises,
                week = week,
                catalogVersion = bundledCatalog.sourceCommit
            ),
            zoneId = { LEDGER_ZONE }
        ),
        catalogVersion = { bundledCatalog.sourceCommit },
        nowTimestamp = { week.startEpochMillis + 3 * DAY_MILLIS },
        zoneId = { LEDGER_ZONE },
        deloadRepository = (deload ?: wallcrawl.elopenmike.com.core.model.DeloadPreferences(profile.id)).let { preferences ->
            object : wallcrawl.elopenmike.com.core.database.repository.DeloadRepository {
                override fun observe() = flowOf(preferences)
                override suspend fun get() = preferences
                override suspend fun decide(
                    action: wallcrawl.elopenmike.com.core.model.DeloadAction,
                    expectedProfileRevision: Long,
                    expectedDecisionRevision: Long,
                    offerId: String?
                ) = error("Read-only composition fixture.")
            }
        }
    )

    private fun ledgerFor(
        sessions: List<WorkoutSession>,
        exercises: List<Exercise>,
        week: TrainingWeek
    ): WeeklyDoseLedger = WeeklyDoseLedgerCalculator().calculate(
        sessions = sessions,
        exercisesById = exercises.associateBy(Exercise::id),
        policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
        week = week,
        catalogVersion = bundledCatalog.sourceCommit,
        reviewPolicyVersion = REVIEW_POLICY_VERSION
    )

    private fun fingerprintOf(
        sessions: List<WorkoutSession>,
        attributedExercises: List<Exercise>,
        week: TrainingWeek,
        reviewPolicyVersion: Int = REVIEW_POLICY_VERSION
    ): String = LedgerSourceFingerprint.of(
        sessions = sessions,
        exercisesById = attributedExercises.associateBy(Exercise::id),
        policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
        week = week,
        catalogVersion = bundledCatalog.sourceCommit,
        reviewPolicyVersion = reviewPolicyVersion
    )

    private fun completedSessionOf(
        sessionId: String,
        exercise: Exercise,
        completedSets: Int,
        completedAtEpochMillis: Long
    ): WorkoutSession = completedSession(
        id = sessionId,
        completedAtEpochMillis = completedAtEpochMillis,
        exercises = listOf(
            exerciseInstance(
                exerciseId = exercise.id,
                id = "$sessionId-${exercise.id}",
                sets = (1..completedSets).map { setNumber ->
                    completedNormalSet(id = "$sessionId-set-$setNumber").copy(
                        setNumber = setNumber,
                        exerciseType = exercise.type
                    )
                }
            )
        )
    )

    private fun fixedAnchorBandAlternativesOf(exercise: Exercise): List<List<String>>? =
        wallcrawl.elopenmike.com.core.model.fixedAnchorBandRequirements[exercise.id]

    private fun bodyweightProfile() = UserProfile(
        goals = setOf(FitnessGoal.GENERAL_FITNESS, FitnessGoal.BUILD_MUSCLE),
        experienceLevel = ExperienceLevel.BEGINNER,
        availableEquipment = listOf(StandardEquipment.BODYWEIGHT),
        movementCapabilities = comfortableCapabilities(),
        onboardingCompleted = true
    )

    private fun fullGymProfile() = UserProfile(
        experienceLevel = ExperienceLevel.ADVANCED,
        availableEquipment = StandardEquipment.ALL,
        movementCapabilities = comfortableCapabilities(),
        onboardingCompleted = true
    )

    private fun comfortableCapabilities() = MovementCapabilities.from(
        MovementCapabilityType.entries.associateWith { CapabilityLevel.COMFORTABLE }
    )

    private inner class Cohort(
        val name: String,
        val profile: UserProfile,
        val history: List<WorkoutSession> = emptyList()
    )

    private sealed interface PlanOutcome

    private class Served(
        val workout: GeneratedWorkout,
        val snapshot: RecommendationSnapshot,
        val byId: Map<String, Exercise>
    ) : PlanOutcome

    private class Refused(
        val planningFailure: WorkoutPlanningFailure?,
        val eligibilityFailure: AutomaticEligibilityFailure?,
        val allowedExerciseIds: List<String>,
        val violationCodes: List<ProgramViolationCode>,
        val policyRefusal: TrainingPolicyResult?
    ) : PlanOutcome

    private inner class UnsupportedCohort(
        val cohort: Cohort,
        val expectedFailure: AutomaticEligibilityFailure
    )

    private val SUPPORTED_COHORTS: List<Cohort> by lazy {
        val week = TrainingWeek.startingOn(MONDAY_EPOCH_DAY, LEDGER_ZONE)
        listOf(
            Cohort("bodyweight", bodyweightProfile()),
            Cohort(
                "dumbbell-and-bench",
                UserProfile(
                    availableEquipment = listOf(
                        StandardEquipment.BODYWEIGHT,
                        StandardEquipment.DUMBBELL,
                        StandardEquipment.BENCH
                    ),
                    movementCapabilities = comfortableCapabilities(),
                    onboardingCompleted = true
                )
            ),
            Cohort(
                "machine",
                UserProfile(
                    availableEquipment = listOf(
                        StandardEquipment.MACHINE,
                        StandardEquipment.CABLE,
                        StandardEquipment.BENCH
                    ),
                    movementCapabilities = comfortableCapabilities(),
                    onboardingCompleted = true
                )
            ),
            Cohort("full-gym", fullGymProfile()),
            Cohort(
                "band-only",
                UserProfile(
                    availableEquipment = listOf(StandardEquipment.RESISTANCE_BAND),
                    movementCapabilities = comfortableCapabilities(),
                    onboardingCompleted = true
                )
            ),
            Cohort(
                "limited-capability",
                UserProfile(
                    availableEquipment = StandardEquipment.FULL_GYM,
                    movementCapabilities = MovementCapabilities.from(
                        MovementCapabilityType.entries.associateWith { CapabilityLevel.LIMITED }
                    ),
                    onboardingCompleted = true
                )
            ),
            Cohort(
                "returner",
                UserProfile(
                    experienceLevel = ExperienceLevel.INTERMEDIATE,
                    availableEquipment = StandardEquipment.FULL_GYM,
                    returningAfterBreakWeeks = 26,
                    movementCapabilities = comfortableCapabilities(),
                    onboardingCompleted = true
                )
            ),
            Cohort(
                name = "mixed-unit-history",
                profile = UserProfile(
                    availableEquipment = StandardEquipment.FULL_GYM,
                    preferredUnit = WeightUnit.KG,
                    movementCapabilities = comfortableCapabilities(),
                    onboardingCompleted = true
                ),
                history = listOf(
                    completedSessionOf(
                        sessionId = "logged-in-lbs",
                        exercise = bundledExercises.single { it.id == "barbell-bench-press" },
                        completedSets = 2,
                        completedAtEpochMillis = week.startEpochMillis + HOUR_MILLIS
                    ).copy(weightUnit = WeightUnit.LBS)
                )
            ),
            Cohort(
                name = "sparse-history",
                profile = UserProfile(
                    availableEquipment = StandardEquipment.FULL_GYM,
                    movementCapabilities = comfortableCapabilities(),
                    onboardingCompleted = true
                ),
                history = listOf(
                    completedSessionOf(
                        sessionId = "one-session",
                        exercise = bundledExercises.single { it.id == "bodyweight-squat" },
                        completedSets = 1,
                        completedAtEpochMillis = week.startEpochMillis + HOUR_MILLIS
                    )
                )
            )
        )
    }

    private val UNSUPPORTED_COHORTS: List<UnsupportedCohort> by lazy {
        listOf(
            UnsupportedCohort(
                Cohort(
                    "inventory-matching-nothing-accepted",
                    UserProfile(
                        availableEquipment = listOf(StandardEquipment.CARDIO),
                        movementCapabilities = comfortableCapabilities(),
                        onboardingCompleted = true
                    )
                ),
                // An inventory that satisfies no accepted record leaves the 120 pending and
                // outside-scope records as the last ones standing, so the aggregate names the
                // missing acceptance rather than the equipment. That is the honest summary of
                // this corpus: equipment alone can never be the last reason while records
                // remain unaccepted.
                AutomaticEligibilityFailure.NO_APPROVED_METADATA
            ),
            UnsupportedCohort(
                Cohort(
                    "everything-excluded",
                    UserProfile(
                        availableEquipment = StandardEquipment.ALL,
                        excludedExerciseIds = bundledExercises.map(Exercise::id),
                        movementCapabilities = comfortableCapabilities(),
                        onboardingCompleted = true
                    )
                ),
                AutomaticEligibilityFailure.USER_EXCLUSIONS_REMOVED_ALL
            ),
            UnsupportedCohort(
                Cohort(
                    "joint-constraint-selected",
                    UserProfile(
                        availableEquipment = StandardEquipment.ALL,
                        trainingConstraints = setOf(TrainingConstraint.SHOULDER_SENSITIVE),
                        movementCapabilities = comfortableCapabilities(),
                        onboardingCompleted = true
                    )
                ),
                AutomaticEligibilityFailure.TRAINING_CONSTRAINTS_REMOVED_ALL
            )
        )
    }

    // endregion

    private companion object {
        const val MONDAY_EPOCH_DAY = 20_696L
        val LEDGER_ZONE: ZoneId = ZoneId.of("UTC")
        const val DAY_MILLIS = 24 * 60 * 60 * 1_000L
        const val HOUR_MILLIS = 60 * 60 * 1_000L
        const val REVIEW_POLICY_VERSION = 2
        const val MAX_SETS_PER_EXERCISE = 20
        const val SETS_THAT_EXHAUST_ANY_ALLOWANCE = 12
        const val CANDIDATES_TRAINED_TO_EXHAUSTION = 12
    }
}

internal class StaticUserProfileRepository(
    private val profile: UserProfile
) : UserProfileRepository {
    override fun getUserProfile(): Flow<UserProfile> = flowOf(profile)
    override suspend fun getProfileOnce(): UserProfile = profile
    override suspend fun saveUserProfile(profile: UserProfile) = unsupported()
    override suspend fun saveProfile(profile: UserProfile) = unsupported()
    override suspend fun updateGoals(goals: Set<FitnessGoal>) = unsupported()
    override suspend fun updatePrimaryGoal(goal: FitnessGoal) = unsupported()
    override suspend fun updateExperienceLevel(level: ExperienceLevel) = unsupported()
    override suspend fun updatePreferredDuration(minutes: Int) = unsupported()
    override suspend fun updateDaysPerWeek(days: Int) = unsupported()
    override suspend fun updateEquipment(equipment: List<String>) = unsupported()
    override suspend fun updateUnit(unit: WeightUnit) = unsupported()
    override suspend fun updateMusclePriorities(priorities: Map<String, PriorityLevel>) =
        unsupported()

    override suspend fun updateExcludedExercises(excludedIds: List<String>) = unsupported()
    override suspend fun updateTrainingConstraints(constraints: Set<TrainingConstraint>) =
        unsupported()

    override suspend fun updateReturningAfterBreakWeeks(weeks: Int) = unsupported()
    override suspend fun updateGender(
        gender: wallcrawl.elopenmike.com.core.model.ProfileGender
    ) = unsupported()

    override suspend fun updateThemePreference(
        themePreference: wallcrawl.elopenmike.com.core.model.ThemePreference
    ) = unsupported()

    private fun unsupported(): Nothing =
        error("This composition reads a profile; it never writes one.")
}

internal class StaticWorkoutRepository(
    private val completedSessions: List<WorkoutSession>,
    private val records: List<RecommendationRecord> = emptyList()
) : WorkoutRepository {
    override suspend fun getCompletedSessionsInRange(startTimestamp: Long, endTimestampExclusive: Long) =
        completedSessions.filter { it.status == wallcrawl.elopenmike.com.core.model.SessionStatus.COMPLETED &&
            it.completedAtTimestamp?.let { time -> time >= startTimestamp && time < endTimestampExclusive } == true }
    override fun observeActiveSession(): Flow<WorkoutSession?> = flowOf(null)
    override suspend fun getActiveSessionOnce(): WorkoutSession? = null
    override suspend fun getSessionById(sessionId: String): WorkoutSession? =
        completedSessions.firstOrNull { it.id == sessionId }

    override fun observeSession(sessionId: String): Flow<WorkoutSession?> = flowOf(null)
    override fun observeCompletedSessions(limit: Int): Flow<List<WorkoutSession>> =
        flowOf(completedSessions.take(limit))

    override fun observeCompletedWorkoutCount(): Flow<Int> = flowOf(completedSessions.size)
    override fun observeCompletedWorkoutCountInRange(
        startTimestamp: Long,
        endTimestampExclusive: Long
    ): Flow<Int> = flowOf(
        completedSessions.count { session ->
            session.completedAtTimestamp?.let {
                it >= startTimestamp && it < endTimestampExclusive
            } == true
        }
    )

    override suspend fun getRecentCompletedSessions(limit: Int): List<WorkoutSession> =
        completedSessions.sortedByDescending { it.completedAtTimestamp }.take(limit)

    override suspend fun getRecentSessions(limit: Int): List<WorkoutSession> =
        completedSessions.sortedWith(compareByDescending<WorkoutSession> { it.startedAtTimestamp }.thenBy { it.id }).take(limit)

    override suspend fun getRecommendationRecords(sessionIds: List<String>): List<wallcrawl.elopenmike.com.core.model.RecommendationRecord> =
        records.filter { it.sessionId in sessionIds }

    override suspend fun startWorkoutFromGenerated(
        generated: GeneratedWorkout,
        displayName: String,
        displayRationale: String,
        userProfile: UserProfile,
        recommendation: RecommendationSnapshot?
    ): WorkoutSession = error("This composition only generates; starting is covered elsewhere.")

    override suspend fun startWorkoutFromTemplate(
        template: wallcrawl.elopenmike.com.core.model.WorkoutTemplate,
        userProfile: UserProfile
    ): WorkoutSession = error("Not used")

    override suspend fun logSetCompletion(
        setId: String,
        performance: wallcrawl.elopenmike.com.core.model.SetPerformanceInput
    ) = Unit

    override suspend fun completeWorkout(
        sessionId: String,
        actualDurationMinutes: Int
    ): wallcrawl.elopenmike.com.core.model.WorkoutSummary = error("Not used")

    override suspend fun getWorkoutSummary(
        sessionId: String
    ): wallcrawl.elopenmike.com.core.model.WorkoutSummary? = error("Not used")

    override suspend fun cancelWorkout(sessionId: String) = Unit
}

/**
 * The ledger the production repository would reconstruct, computed by the real calculator.
 *
 * A hand-written map would let this suite assert against numbers no production code
 * produces; the point of these cohorts is that completed history is credited by the same
 * accounting the app runs.
 */
internal class RecomputingLedgerRepository(
    private val sessions: List<WorkoutSession>,
    private val exercises: List<Exercise>,
    private val week: TrainingWeek,
    private val catalogVersion: String
) : WeeklyDoseLedgerRepository {
    private val calculator = WeeklyDoseLedgerCalculator()

    override suspend fun weeklyLedgerAt(
        profileId: String,
        instant: Instant,
        zoneId: ZoneId
    ): WeeklyDoseLedger = current()

    override suspend fun currentWeeklyLedger(
        profileId: String,
        zoneId: ZoneId
    ): WeeklyDoseLedger = current()

    private fun current(): WeeklyDoseLedger = calculator.calculate(
        sessions = sessions.filter { it.completedAtTimestamp?.let(week::contains) == true },
        exercisesById = exercises.associateBy(Exercise::id),
        policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
        week = week,
        catalogVersion = catalogVersion,
        reviewPolicyVersion = exercises
            .mapNotNull { it.reviewedMetadata?.provenance?.policyVersion }
            .maxOrNull() ?: 0
    )
}
