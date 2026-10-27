package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wallcrawl.elopenmike.com.core.database.repository.DeloadRepository
import wallcrawl.elopenmike.com.core.database.repository.WeeklyDoseLedgerRepository
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.*

/** Runs the shipped catalog and production policies, without promoting metadata or clearing constraints. */
class MultiWeekProgramProductionReplayTest {
    private val bundled = PlannerFixtureContextFactory().bundledCatalogProjection()
    private val catalog = InMemoryExerciseCatalog(bundled.exercises)
    private val validator = ProgramValidator(GeneratedWorkoutValidator(catalog))
    private val byId = bundled.exercises.associateBy { it.id }
    private val profile = UserProfile(onboardingCompleted = true, experienceLevel = ExperienceLevel.ADVANCED,
        availableEquipment = StandardEquipment.ALL, preferredUnit = WeightUnit.KG, musclePriorities = emptyMap(),
        movementCapabilities = MovementCapabilities.from(
            MovementCapabilityType.entries.associateWith { CapabilityLevel.COMFORTABLE }))

    @Test
    fun shippedCohortReplaysFourCompletionDrivenWeeksForTwoThreeAndSixSessions() = runTest {
        assertThat(bundled.exercises.count { it.reviewedMetadata?.reviewState == ReviewState.AI_ACCEPTED }).isEqualTo(182)
        assertThat(bundled.exercises.count { it.reviewedMetadata?.reviewState == ReviewState.APPROVED }).isEqualTo(0)
        assertThat(bundled.exercises.mapNotNull { it.acceptedMetadata() }
            .flatMap { it.clearedTrainingConstraints }).isEmpty()
        for (frequency in listOf(2, 3, 6)) {
            val run = Run(profile.copy(daysPerWeek = frequency))
            val planner = FakeWorkoutPlanner()
            for (slot in 0 until frequency * 4) {
                // Eight actual days between slots deliberately cannot define program weeks.
                run.now = Instant.parse("2026-10-25T19:00:00Z").toEpochMilli() + slot * 8L * DAY
                val context = run.context()
                assertThat(context.programInput!!.position.slotOrdinal).isEqualTo(slot)
                assertThat(context.programInput.position.weekNumber).isEqualTo(slot / frequency + 1)
                assertThat(context.programInput.position.blockNumber).isEqualTo(if (slot < frequency * 2) 1 else 2)
                assertThat(context.trainingProgramState!!.policyVersion).isEqualTo(TrainingProgramStatePolicyVersion.PROGRAM_STATE_V2)
                assertThat(context.trainingProgramState.adaptationState).isEqualTo(AdaptationState.UNCALIBRATED)
                val first = validator.validate(planner.generateWorkout(context), context, true) as ProgramValidationResult.Valid
                val second = validator.validate(planner.generateWorkout(run.context()), run.context(), true) as ProgramValidationResult.Valid
                assertWithMessage("frequency=$frequency slot=$slot").that(second.workout.copy(id = first.workout.id))
                    .isEqualTo(first.workout)
                assertThat(second.snapshot).isEqualTo(first.snapshot)
                assertThat(first.snapshot.programSession!!.position).isEqualTo(context.programInput.position)
                assertThat(context.automaticEligibilityResult!!.decisions.single { it.exerciseId == "pike-push-up" }.reasons)
                    .contains(EligibilityReason.ADVANCED_WHILE_UNCALIBRATED)
                assertThat(context.allowedExercises.map { it.id }).doesNotContain("pike-push-up")
                assertThat(context.exerciseHistory).isEmpty()
                assertThat(first.workout.progressionDecisions.all { it.axis == null }).isTrue()
                assertThat(run.slots).hasSize(slot)
                assertThat(run.history).hasSize(slot)
                assertThat(context.trainingProgramState.weeklyLedger.directPrimarySets).isEmpty()
                // A confirmed Finish with no completed sets advances one slot, never dose/evidence.
                run.finish(first, completedWorkSets = 0)
                assertThat(run.history.last().completedSetsCount).isEqualTo(0)
            }
            assertThat(run.enrollment.status).isEqualTo(ProgramStatus.COMPLETED)
            assertThat(MultiWeekProgramPolicy.reconstruct(run.enrollment, run.slots))
                .isEqualTo(ProgramProgress(frequency * 4, null, null))
            assertThat(run.history.sumOf { it.completedSetsCount }).isEqualTo(0)
        }
    }

    @Test
    fun performedEvidenceReplaysAcrossAllWeeksWithSharedDoseProgressionAndOneConsumedDeload() = runTest {
        val run = Run(profile.copy(daysPerWeek = 2,
            excludedExerciseIds = bundled.exercises.map { it.id }.filterNot { it == "push-up" }))
        val decisions = mutableListOf<ProgressionDecision>()
        var completedSets = 0
        for (slot in 0 until 8) {
            run.now = Instant.parse("2026-10-25T19:00:00Z").toEpochMilli() + slot * 8L * DAY
            if (slot == 4) run.deload = DeloadPreferences(profile.id, 1,
                DeloadChoice(DeloadOffer("requested", DeloadSource.EXPLICIT_REQUEST, DeloadOfferPolicy.VERSION),
                    DeloadChoiceStatus.ACCEPTED, run.now))
            val context = run.context()
            val first = validator.validate(FakeWorkoutPlanner().generateWorkout(context), context, true) as ProgramValidationResult.Valid
            val replayContext = run.context()
            val replay = validator.validate(FakeWorkoutPlanner().generateWorkout(replayContext), replayContext, true) as ProgramValidationResult.Valid
            assertThat(replay.workout.copy(id = first.workout.id)).isEqualTo(first.workout)
            assertThat(replay.snapshot).isEqualTo(first.snapshot)
            assertThat(first.snapshot.programSession!!.position.weekNumber).isEqualTo(slot / 2 + 1)
            assertThat(context.trainingProgramState!!.weeklyLedger.directPrimarySets).isEmpty()
            val decision = first.workout.progressionDecisions.single()
            decisions += decision
            if (slot == 4) {
                assertThat(context.programInput!!.position.blockNumber).isEqualTo(2)
                assertThat(context.trainingProgramState.adaptationState).isEqualTo(AdaptationState.HOLD)
                assertThat(decision.reason).isEqualTo(ProgressionReason.HOLD_ACCEPTED_DELOAD)
                assertThat(decision.axis).isNull()
                assertThat(first.snapshot.acceptedDeloadOfferId).isEqualTo("requested")
            } else {
                assertThat(context.trainingProgramState.adaptationState).isEqualTo(AdaptationState.UNCALIBRATED)
                assertThat(first.snapshot.acceptedDeloadOfferId).isNull()
            }
            val prescribedSets = first.workout.exercises.single().prescription.targetSets
            run.finish(first, completedWorkSets = prescribedSets)
            completedSets += prescribedSets
            assertThat(run.history.sumOf { it.completedSetsCount }).isEqualTo(completedSets)
            val ledgerAfter = run.context(WorkoutPlanningIntent.ORDINARY).trainingProgramState!!.weeklyLedger
            assertThat(ledgerAfter.directPrimarySets).containsExactly(StandardMuscles.CHEST, prescribedSets)
            assertThat(run.records.last().programAssociationDigest).isEqualTo(
                MultiWeekProgramPolicy.associationDigest(run.slots.last().association))
            if (slot == 4) run.deload = run.deload.copy(revision = 2,
                choice = run.deload.choice!!.copy(status = DeloadChoiceStatus.CONSUMED,
                    decidedAtEpochMillis = run.now, sessionId = run.history.last().id))
        }
        assertThat(decisions.take(2).map { it.axis }).containsExactly(null, null)
        assertThat(decisions[2].axis).isEqualTo(ProgressionAxis.REP_RANGE)
        assertThat(decisions[2].prescription.repRange!!.max).isEqualTo(decisions[1].prescription.repRange!!.max + 1)
        assertThat(decisions[2].prescription.targetSets).isEqualTo(decisions[1].prescription.targetSets)
        assertThat(run.enrollment.status).isEqualTo(ProgramStatus.COMPLETED)
        assertThat(run.slots).hasSize(8)
    }

    @Test
    fun proposalCancellationPartialFinishAndRunManagementNeverResetOrReserveTheSharedLedger() = runTest {
        val run = Run(profile.copy(daysPerWeek = 2))
        val pushUp = byId.getValue("push-up")
        val prescription = DefaultExercisePrescriptionFactory().create(pushUp, run.context())
        run.history += progressionSession("ordinary-history", pushUp.id, prescription, run.now - HOUR)
            .copy(origin = WorkoutOrigin.CUSTOM_TEMPLATE)
        val before = run.context()
        val completedBefore = before.trainingProgramState!!.weeklyLedger.directPrimarySets
        assertThat(completedBefore[StandardMuscles.CHEST]).isEqualTo(prescription.targetSets)
        val proposal = validator.validate(FakeWorkoutPlanner().generateWorkout(before), before, true) as ProgramValidationResult.Valid
        assertThat(run.context().trainingProgramState!!.weeklyLedger.directPrimarySets).isEqualTo(completedBefore)
        assertThat(proposal.snapshot.doseAccounting.all { it.completedSets == (completedBefore[it.muscle] ?: 0) }).isTrue()
        run.reserve(proposal, "cancelled")
        run.action(ProgramAction.CANCEL_SESSION, sessionId = "cancelled")
        run.slots.removeAt(run.slots.lastIndex)
        assertThat(run.context().programInput!!.position.slotOrdinal).isEqualTo(0)
        assertThat(run.context().trainingProgramState!!.weeklyLedger.directPrimarySets).isEqualTo(completedBefore)

        for (action in listOf(ProgramAction.PAUSE, ProgramAction.RESUME, ProgramAction.RESTART, ProgramAction.REPLACE)) {
            run.action(action, newProgramId = when (action) {
                ProgramAction.RESTART -> OTHER_RUN
                ProgramAction.REPLACE -> THIRD_RUN
                else -> null
            })
            val ordinary = run.context(WorkoutPlanningIntent.ORDINARY)
            assertThat(ordinary.trainingProgramState!!.weeklyLedger.directPrimarySets).isEqualTo(completedBefore)
            assertThat(ordinary.programInput).isNull()
        }
        val context = run.context()
        val valid = validator.validate(FakeWorkoutPlanner().generateWorkout(context), context, true) as ProgramValidationResult.Valid
        val primary = byId.getValue(valid.workout.exercises.first().exerciseId).acceptedMetadata()!!.directPrimaryMuscle
        run.finish(valid, completedWorkSets = 1)
        val after = run.context()
        assertThat(after.programInput!!.position.slotOrdinal).isEqualTo(1)
        assertThat(after.trainingProgramState!!.weeklyLedger.directPrimarySets[primary])
            .isEqualTo((completedBefore[primary] ?: 0) + 1)
        assertThat(run.history.last().completedSetsCount).isEqualTo(1)
        assertThat(run.history.first().origin).isEqualTo(WorkoutOrigin.CUSTOM_TEMPLATE)
    }

    @Test
    fun dateDstZoneRollbackAndMissedMonthsDoNotAdvanceOrAddCatchUpDose() = runTest {
        val run = Run(profile)
        val moments = listOf(
            "2026-10-26T06:59:00Z" to "America/Los_Angeles", // Sunday before ISO boundary
            "2026-10-26T07:01:00Z" to "America/Los_Angeles",
            "2026-11-01T08:59:00Z" to "America/Los_Angeles", // DST fall-back
            "2026-11-01T09:01:00Z" to "America/Los_Angeles",
            "2026-11-01T09:01:00Z" to "Pacific/Auckland",
            "2026-03-08T09:59:00Z" to "America/Los_Angeles", // rollback, then spring-forward
            "2026-03-08T10:01:00Z" to "America/Los_Angeles",
            "2027-01-04T20:00:00Z" to "America/Los_Angeles"
        )
        val identities = mutableListOf<String>()
        val weeks = mutableListOf<Long>()
        var baseline: GeneratedWorkout? = null
        for ((instant, zone) in moments) {
            run.now = Instant.parse(instant).toEpochMilli()
            run.zone = ZoneId.of(zone)
            val context = run.context()
            val valid = validator.validate(FakeWorkoutPlanner().generateWorkout(context), context, true) as ProgramValidationResult.Valid
            baseline?.let { assertThat(valid.workout.copy(id = it.id)).isEqualTo(it) }
            baseline = valid.workout
            assertThat(valid.snapshot.programSession!!.position).isEqualTo(ProgramPosition(0, 1, 1, 1))
            assertThat(context.trainingProgramState!!.weeklyLedger.directPrimarySets).isEmpty()
            assertThat(context.trainingProgramState.adaptationState).isEqualTo(AdaptationState.UNCALIBRATED)
            identities += valid.snapshot.contextIdentity
            weeks += valid.snapshot.weekStartEpochDay!!
        }
        assertThat(weeks.take(2)).containsExactly(20_745L, 20_752L).inOrder()
        assertThat(identities[0]).isNotEqualTo(identities[1])
        assertThat(identities[2]).isEqualTo(identities[3]) // same local date/week despite repeated hour
        assertThat(identities[3]).isNotEqualTo(identities[4])
        assertThat(run.slots).isEmpty()
        assertThat(run.history).isEmpty()
    }

    @Test
    fun unsupportedProductionPersonasAndLiveConstraintsRemainTypedInEveryProgramBlock() = runTest {
        val variants = listOf(
            profile.copy(availableEquipment = listOf(StandardEquipment.CARDIO)) to
                AutomaticEligibilityFailure.NO_APPROVED_METADATA,
            profile.copy(trainingConstraints = setOf(TrainingConstraint.KNEE_SENSITIVE)) to
                AutomaticEligibilityFailure.TRAINING_CONSTRAINTS_REMOVED_ALL,
            profile.copy(excludedExerciseIds = bundled.exercises.map { it.id }) to
                AutomaticEligibilityFailure.USER_EXCLUSIONS_REMOVED_ALL
        )
        for ((persona, failure) in variants) {
            val run = Run(persona)
            repeat(8) { ordinal -> run.appendEmptyCompletion("earlier-$ordinal") }
            val context = run.context()
            assertThat(context.programInput!!.position.blockNumber).isEqualTo(2)
            val noCandidates = context.automaticEligibilityResult as AutomaticEligibilityResult.NoCandidates
            assertThat(noCandidates.failure).isEqualTo(failure)
            try {
                FakeWorkoutPlanner().generateWorkout(context)
                org.junit.Assert.fail("Unsupported persona must not fall back")
            } catch (refused: WorkoutValidationException) {
                assertThat(refused.failure).isEqualTo(WorkoutPlanningFailure.REVIEWED_ELIGIBILITY_NO_CANDIDATES)
                assertThat(refused.automaticEligibilityFailure).isEqualTo(failure)
            }
        }
        val run = Run(profile)
        repeat(8) { ordinal -> run.appendEmptyCompletion("past-$ordinal") }
        run.profile = profile.copy(availableEquipment = listOf(StandardEquipment.BODYWEIGHT), preferredUnit = WeightUnit.LBS,
            trainingConstraints = setOf(TrainingConstraint.LOW_IMPACT_ONLY), revision = 1)
        val context = run.context()
        assertThat(context.preferredUnits).isEqualTo(WeightUnit.LBS)
        assertThat(context.allowedExercises.map { it.acceptedMetadata()!!.impactLevel }).doesNotContain(ImpactLevel.HIGH)
        assertThat(context.allowedExercises.map { it.id }).doesNotContain("pike-push-up")
        assertThat(validator.validate(FakeWorkoutPlanner().generateWorkout(context), context, true))
            .isInstanceOf(ProgramValidationResult.Valid::class.java)
    }

    @Test
    fun programProposalUsesWholeSessionAggregationAndOnlyTheExistingSingleRepair() = runTest {
        val ids = setOf("dumbbell-bench-press", "machine-chest-press")
        val run = Run(profile.copy(confirmedStartingLoads = ids.associateWith { 40.0 },
            excludedExerciseIds = bundled.exercises.map { it.id }.filterNot { it in ids }))
        val prescription = ExercisePrescription(ExerciseType.BODYWEIGHT_REPS, 3, RepRange(8, 12))
        run.history += progressionSession("ordinary-dose", "push-up", prescription, run.now - HOUR)
        val context = run.context()
        val proposal = FakeWorkoutPlanner().generateWorkout(context)
        assertThat(proposal.exercises).hasSize(2)
        assertThat(proposal.exercises.sumOf { it.prescription.targetSets }).isEqualTo(4)
        val refused = validator.validate(proposal, context, allowRepair = false) as ProgramValidationResult.Invalid
        assertThat(refused.violations.map { it.code }).contains(ProgramViolationCode.WEEKLY_ALLOWANCE_EXCEEDED)
        val repaired = validator.validate(proposal, context, allowRepair = true) as ProgramValidationResult.Valid
        assertThat(repaired.snapshot.outcome).isEqualTo(RecommendationOutcome.REPAIRED)
        assertThat(repaired.workout.exercises.sumOf { it.prescription.targetSets }).isEqualTo(3)
        assertThat(repaired.snapshot.doseAccounting.single().completedSets).isEqualTo(3)
        assertThat(validator.validate(repaired.workout, context, allowRepair = false))
            .isInstanceOf(ProgramValidationResult.Valid::class.java)
        assertThat(run.context().trainingProgramState!!.weeklyLedger.directPrimarySets[StandardMuscles.CHEST]).isEqualTo(3)
        assertThat(run.slots).isEmpty()
    }

    @Test
    fun ordinaryAndLateProgramSlotsPreserveSingleAxisProgressionAndAcceptedOneWorkoutDeload() = runTest {
        val onlyPushUp = profile.copy(excludedExerciseIds = bundled.exercises.map { it.id }.filterNot { it == "push-up" })
        val run = Run(onlyPushUp)
        repeat(8) { ordinal -> run.appendEmptyCompletion("past-$ordinal") }
        val prescription = DefaultExercisePrescriptionFactory().create(byId.getValue("push-up"), run.context())
        run.history += progressionSession("one", "push-up", prescription, run.now - 9 * DAY)
        run.history += progressionSession("two", "push-up", prescription, run.now - 8 * DAY)
        for (intent in WorkoutPlanningIntent.entries) {
            val context = run.context(intent)
            val valid = validator.validate(FakeWorkoutPlanner().generateWorkout(context), context, true) as ProgramValidationResult.Valid
            val decision = valid.workout.progressionDecisions.single()
            assertThat(decision.axis).isEqualTo(ProgressionAxis.REP_RANGE)
            assertThat(decision.prescription.repRange!!.max).isEqualTo(prescription.repRange!!.max + 1)
            assertThat(decision.prescription.targetSets).isEqualTo(prescription.targetSets)
            assertThat(valid.snapshot.programSession != null).isEqualTo(intent == WorkoutPlanningIntent.PROGRAM)
        }
        val offer = DeloadOffer("explicit", DeloadSource.EXPLICIT_REQUEST, DeloadOfferPolicy.VERSION)
        run.deload = DeloadPreferences(profile.id, 1, DeloadChoice(offer, DeloadChoiceStatus.ACCEPTED, 1))
        for (intent in WorkoutPlanningIntent.entries) {
            val context = run.context(intent)
            val valid = validator.validate(FakeWorkoutPlanner().generateWorkout(context), context, true) as ProgramValidationResult.Valid
            assertThat(context.trainingProgramState!!.adaptationState).isEqualTo(AdaptationState.HOLD)
            assertThat(valid.workout.progressionDecisions.single().reason).isEqualTo(ProgressionReason.HOLD_ACCEPTED_DELOAD)
            assertThat(valid.workout.progressionDecisions.single().axis).isNull()
            assertThat(valid.workout.exercises.single().prescription)
                .isEqualTo(prescription.copy(targetSets = maxOf(1, prescription.targetSets - 1)))
            assertThat(valid.snapshot.acceptedDeloadOfferId).isEqualTo("explicit")
        }
        run.deload = DeloadPreferences(profile.id, 2)
        assertThat(run.context().trainingProgramState!!.adaptationState).isEqualTo(AdaptationState.UNCALIBRATED)
        assertThat(run.context().programInput!!.position.slotOrdinal).isEqualTo(8)
    }

    private inner class Run(var profile: UserProfile) {
        var now = Instant.parse("2026-10-29T19:00:00Z").toEpochMilli()
        var zone = ZoneId.of("America/Los_Angeles")
        var enrollment = MultiWeekProgramPolicy.transition(profile, null, emptyList(), ProgramAction.START,
            ProgramExpectation(profile.id, profile.revision, null, 0), now, newProgramId = FIRST_RUN)
        val slots = mutableListOf<ProgramSlotSession>()
        val history = mutableListOf<WorkoutSession>()
        val records = mutableListOf<RecommendationRecord>()
        var deload = DeloadPreferences(profile.id)

        suspend fun context(intent: WorkoutPlanningIntent = WorkoutPlanningIntent.PROGRAM): WorkoutGenerationContext {
            val state = ProgramReadState.Supported(header(), enrollment,
                MultiWeekProgramPolicy.reconstruct(enrollment, slots), slots.toList(), profile)
            val ledger = object : WeeklyDoseLedgerRepository {
                override suspend fun weeklyLedgerAt(profileId: String, instant: Instant, zoneId: ZoneId): WeeklyDoseLedger {
                    val week = TrainingWeek.containing(instant, zoneId)
                    return WeeklyDoseLedgerCalculator().calculate(
                        history.filter { it.status == SessionStatus.COMPLETED &&
                            it.completedAtTimestamp?.let { time -> week.contains(time) && time <= instant.toEpochMilli() } == true },
                        byId, LedgerPolicyVersion.PRIMARY_ONLY_V1, week, bundled.sourceCommit, 2)
                }
                override suspend fun currentWeeklyLedger(profileId: String, zoneId: ZoneId) =
                    weeklyLedgerAt(profileId, Instant.ofEpochMilli(now), zoneId)
            }
            return WorkoutGenerationContextBuilder(
                StaticUserProfileRepository(profile), StaticWorkoutRepository(history.toList(), records.toList()),
                catalog, ExerciseFilter(), WorkoutHistoryAnalyzer(), PlannerFeatureFlags.PRODUCTION,
                trainingProgramStateProvider = TrainingProgramStateProvider(ledger),
                catalogVersion = { bundled.sourceCommit }, nowTimestamp = { now }, zoneId = { zone },
                deloadRepository = object : DeloadRepository {
                    override fun observe() = flowOf(deload)
                    override suspend fun get() = deload
                    override suspend fun decide(action: DeloadAction, expectedProfileRevision: Long,
                        expectedDecisionRevision: Long, offerId: String?) = error("Read-only deload fixture")
                }, programRepository = ReadOnlyProgramRepository(state)
            ).build(intent, regenerationIndex = 0)
        }

        fun header() = ProgramHeader(profile.id, enrollment.programId, enrollment.revision,
            java.security.MessageDigest.getInstance("SHA-256").digest(enrollment.toString().toByteArray())
                .joinToString("") { "%02x".format(java.util.Locale.ROOT, it) })

        fun action(action: ProgramAction, sessionId: String? = null, newProgramId: String? = null) {
            enrollment = MultiWeekProgramPolicy.transition(profile, enrollment, slots, action,
                ProgramExpectation(profile.id, profile.revision, enrollment.programId, enrollment.revision),
                now, sessionId = sessionId, confirmed = true, newProgramId = newProgramId)
        }

        fun reserve(valid: ProgramValidationResult.Valid, sessionId: String) {
            val association = ProgramAssociation(sessionId, profile.id, valid.snapshot.programSession!!)
            action(ProgramAction.START_SESSION)
            slots += ProgramSlotSession(association, SessionStatus.IN_PROGRESS, null)
        }

        fun finish(valid: ProgramValidationResult.Valid, completedWorkSets: Int) {
            val id = "finished-" + slots.size
            reserve(valid, id)
            action(ProgramAction.FINISH_SESSION, sessionId = id)
            slots[slots.lastIndex] = slots.last().copy(status = SessionStatus.COMPLETED, completedAtEpochMillis = now)
            val plan = valid.workout
            history += WorkoutSession(id, "Recorded workout", now - HOUR, now,
                status = SessionStatus.COMPLETED, weightUnit = profile.preferredUnit,
                exercises = plan.exercises.mapIndexed { index, exercise ->
                    val exerciseId = "$id-exercise-$index"
                    WorkoutExercise(exerciseId, id, exercise.exerciseId, index, exercise.prescription,
                        sets = if (index == 0) (1..minOf(completedWorkSets, exercise.prescription.targetSets)).map { number -> WorkoutSet(
                            id = "$id-set-$number", workoutExerciseId = exerciseId, setNumber = number,
                            exerciseType = exercise.prescription.exerciseType, isCompleted = true,
                            targetReps = exercise.prescription.repRange?.max,
                            completedReps = exercise.prescription.repRange?.max,
                            targetDurationSeconds = exercise.prescription.targetDurationSeconds,
                            completedDurationSeconds = exercise.prescription.targetDurationSeconds,
                            targetWeight = exercise.prescription.targetWeight,
                            completedWeight = exercise.prescription.targetWeight,
                            targetAssistanceWeight = exercise.prescription.targetAssistanceWeight,
                            completedAssistanceWeight = exercise.prescription.targetAssistanceWeight,
                            targetDistanceMeters = exercise.prescription.targetDistanceMeters,
                            completedDistanceMeters = exercise.prescription.targetDistanceMeters,
                            rir = 3, feltManageable = true, completedAtTimestamp = now - 1) } else emptyList())
                })
            records += valid.snapshot.asRecord(id, now)
        }

        fun appendEmptyCompletion(id: String) {
            val position = MultiWeekProgramPolicy.reconstruct(enrollment, slots).nextPosition!!
            val association = ProgramAssociation(id, profile.id, MultiWeekProgramPolicy.snapshot(enrollment, position))
            action(ProgramAction.START_SESSION)
            slots += ProgramSlotSession(association, SessionStatus.IN_PROGRESS, null)
            action(ProgramAction.FINISH_SESSION, sessionId = id)
            slots[slots.lastIndex] = slots.last().copy(status = SessionStatus.COMPLETED, completedAtEpochMillis = now)
        }
    }

    private companion object {
        const val DAY = 86_400_000L
        const val HOUR = 3_600_000L
        const val FIRST_RUN = "00000000-0000-0000-0000-000000000001"
        const val OTHER_RUN = "00000000-0000-0000-0000-000000000002"
        const val THIRD_RUN = "00000000-0000-0000-0000-000000000003"
    }
}
