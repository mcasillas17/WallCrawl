package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.AdaptationState
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityFailure
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityResult
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.EligibilityPreference
import wallcrawl.elopenmike.com.core.model.EligibilityReason
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.MovementPattern
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.RestTargetSource
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.TrainingWeek
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSplit

/**
 * Separate prospective structural upper-bound and AI-ready cohorts, neither human-approved
 * nor clinically validated. The research ledger supplies readiness, never production approval.
 *
 * Only the existing fixture factory synthesizes approvals, in memory. No reviewed records
 * are copied into fixtures. Production drafts and absent metadata remain unchanged.
 */
@RunWith(Parameterized::class)
class ReviewedCatalogCoverageTest(private val caseId: String) {

    private val factory = PlannerFixtureContextFactory()
    private val projection = factory.bundledCatalogProjection()
    private val draftIds = projection.exercises
        .filter { it.reviewedMetadata?.reviewState == ReviewState.DRAFT }
        .map(Exercise::id)

    @Test
    fun allDraftStructuralUpperBound_exercisesActualEligibilityPlannerAndValidator() = runTest {
        verifyProspectiveCohort(COHORT, draftIds)
    }

    @Test
    fun aiReadySynthetic_exercisesActualEligibilityPlannerAndValidator() = runTest {
        val ledgerFile = File("../$READINESS_LEDGER_PATH")
        val preparationReport = JSONObject().put("schemaVersion", 1)
            .put("cohort", READY_COHORT).put("caseId", caseId)
            .put("readinessLedgerPath", READINESS_LEDGER_PATH)
            .put("ledgerPresent", ledgerFile.isFile)
            .put("status", "COHORT_PREPARATION_FAILED")
            .put("assertionsPassed", false)
        var verificationStarted = false
        try {
            assertWithMessage("Required readiness ledger is missing: $READINESS_LEDGER_PATH")
                .that(ledgerFile.isFile).isTrue()
            val entries = JSONObject(ledgerFile.readText()).getJSONArray("entries")
            val readyIds = (0 until entries.length()).map { entries.getJSONObject(it) }
                .filter { it.getString("disposition") == "ready_for_human_review" }
                .map { it.getString("id") }
            preparationReport.put("readyForHumanReviewIds", JSONArray(readyIds))
            assertThat(readyIds).isNotEmpty()
            assertThat(readyIds).containsNoDuplicates()
            assertWithMessage("Every ready ID must name existing bundled DRAFT metadata")
                .that(draftIds).containsAtLeastElementsIn(readyIds)
            val readyIdSet = readyIds.toSet()
            val orderedReadyIds = draftIds.filter { it in readyIdSet }
            verificationStarted = true
            verifyProspectiveCohort(READY_COHORT, orderedReadyIds)
        } finally {
            if (!verificationStarted) writeReport(READY_COHORT, preparationReport)
        }
    }

    private suspend fun verifyProspectiveCohort(cohort: String, cohortApprovalIds: List<String>) {
        val fixture = fixture(cohortApprovalIds)
        val syntheticApprovedIds = requireNotNull(fixture.reviewedEligibility)
            .syntheticApprovedExerciseIds.toSet()
        val built = factory.create(fixture)
        val context = versionedContext(built)
        val eligibility = requireNotNull(context.automaticEligibilityResult)
        val report = report(built, context, cohort, cohortApprovalIds)
        val inputBefore = inputSnapshot(built, context)

        try {
            assertThat(projection.exercises.none {
                it.reviewedMetadata?.reviewState == ReviewState.APPROVED
            }).isTrue()
            assertThat(draftIds).isNotEmpty()
            assertThat(fixture.allowedExerciseIds).isEmpty()
            assertThat(built.catalogExercises.filter {
                it.reviewedMetadata?.reviewState == ReviewState.APPROVED
            }.map(Exercise::id))
                .containsExactlyElementsIn(fixture.reviewedEligibility.syntheticApprovedExerciseIds)
                .inOrder()
            assertThat(eligibility.decisions.map { it.exerciseId })
                .containsExactlyElementsIn(projection.exercises.map(Exercise::id)).inOrder()
            assertThat(context.allowedExercises.map(Exercise::id))
                .containsExactlyElementsIn(
                    eligibility.decisions.filter { it.eligible }.map { it.exerciseId }
                ).inOrder()
            built.catalogExercises.filter {
                it.reviewedMetadata?.reviewState == ReviewState.APPROVED
            }.forEach {
                assertThat(it.reviewedMetadata!!.provenance.reviewerRole)
                    .isEqualTo("Synthetic test-only reviewer")
                assertThat(it.reviewedMetadata.provenance.rationaleOrSource)
                    .startsWith("SYNTHETIC PLANNER FIXTURE")
                assertThat(it.reviewedMetadata.isWellFormedApprovedMetadata()).isTrue()
                assertThat(it.reviewedMetadata.matches(it.type, it.type)).isTrue()
            }

            if (caseId in NO_PLAN_CASES) {
                assertNoPlan(context, report, syntheticApprovedIds)
            } else {
                val first = FakeWorkoutPlanner().generateWorkout(context)
                assertThat(inputSnapshot(built, context)).isEqualTo(inputBefore)
                val second = FakeWorkoutPlanner().generateWorkout(context)
                val deterministic = first.normalizedPlannerFixtureWorkout() ==
                    second.normalizedPlannerFixtureWorkout()
                report.put("proposal", workoutJson(first))
                    .put("selectedIds", JSONArray(first.exercises.map { it.exerciseId }))
                    .put("deterministicNormalizedOutput", deterministic)

                val validator = ProgramValidator(
                    GeneratedWorkoutValidator(InMemoryExerciseCatalog(built.catalogExercises))
                )
                val raw = validator.validate(first, context, allowRepair = false)
                val displayable = validator.validate(first, context, allowRepair = true)
                val repeated = validator.validate(second, context, allowRepair = true)
                report.put("validationWithoutRepair", validationJson(raw))
                    .put("validationWithSingleRepair", validationJson(displayable))
                // This declared profile matrix reports unrepaired validity, not merely
                // eventual acceptance. Production repair remains covered separately.
                assertWithMessage("$caseId raw proposal requires repair: $raw")
                    .that(raw).isInstanceOf(ProgramValidationResult.Valid::class.java)
                assertThat(validationJson(displayable).toString())
                    .isEqualTo(validationJson(repeated).toString())
                assertThat(deterministic).isTrue()
                assertWithMessage("$caseId rejected: $displayable")
                    .that(displayable).isInstanceOf(ProgramValidationResult.Valid::class.java)
                val valid = displayable as ProgramValidationResult.Valid
                assertThat(valid.snapshot.outcome).isEqualTo(RecommendationOutcome.VALID)
                assertThat(valid.workout).isEqualTo(first)
                assertThat(valid.snapshot.catalogVersion).isEqualTo(context.catalogVersion)
                assertThat(valid.snapshot.reviewPolicyVersion).isEqualTo(context.reviewPolicyVersion)
                assertThat(valid.snapshot.reviewedPathEnabled).isTrue()
                assertThat(valid.snapshot.adaptationState)
                    .isEqualTo(context.trainingProgramState!!.adaptationState)
                report.put("acceptedSelectedIds", JSONArray(valid.workout.exercises.map { it.exerciseId }))
                assertThat(
                    validator.validate(valid.workout, context, allowRepair = false)
                ).isInstanceOf(ProgramValidationResult.Valid::class.java)
                assertPrescriptions(first, context)
                assertPrescriptions(valid.workout, context)
                assertScenarioPremise(first, context, report, syntheticApprovedIds)
            }
            assertThat(inputSnapshot(built, context)).isEqualTo(inputBefore)
            assertThat(factory.bundledCatalogProjection().exercises).isEqualTo(projection.exercises)
            report.put("assertionsPassed", true)
        } catch (error: WorkoutValidationException) {
            report.put("unexpectedPlanningFailure", error.failure.name)
                .put("automaticEligibilityFailure", error.automaticEligibilityFailure?.name)
            throw error
        } catch (error: TrainingPolicyResultException) {
            report.put("unexpectedTrainingPolicyResult", error.result.toString())
            throw error
        } finally {
            report.put("inputUnchanged", inputSnapshot(built, context) == inputBefore)
            writeReport(cohort, report)
        }
    }

    @Test
    fun disabledRecommendations_areInvariantWhenReviewedMetadataIsStripped() = runTest {
        val fixture = fixture().copy(
            reviewedEligibility = null,
            expected = PlannerFixtureExpected(
                outcome = PlannerFixtureOutcome.SUCCESS,
                requiredExerciseIds = emptySet(),
                forbiddenExerciseIds = emptySet()
            )
        )
        val built = factory.create(fixture)
        val withDrafts = built.context
        val strippedCatalog = built.catalogExercises.map { it.copy(reviewedMetadata = null) }
        val withoutDrafts = withDrafts.copy(
            allowedExercises = ExerciseFilter().filterCandidates(strippedCatalog, built.userProfile)
        )
        val before = inputSnapshot(built, withDrafts) + inputSnapshot(built, withoutDrafts)
        val report = JSONObject().put("schemaVersion", 1).put("caseId", caseId)
            .put("cohort", "disabled-metadata-invariance")
            .put("context", contextSettings(withDrafts))
            .put("metadataWithDrafts", "Unmodified bundled DRAFT metadata; no synthetic approvals")
            .put("metadataWithoutDrafts", "Only reviewedMetadata stripped from the same full catalog")
            .put("catalogCount", built.catalogExercises.size)
            .put("draftCount", draftIds.size)
            .put("candidateIdsWithDrafts", JSONArray(withDrafts.allowedExercises.map(Exercise::id)))
            .put("candidateIdsWithoutDrafts", JSONArray(withoutDrafts.allowedExercises.map(Exercise::id)))
            .put("assertionsPassed", false)
        if (caseId == "band-only-push-gap") {
            report.put("coverageInterpretation",
                "Legacy invariance and schema validity do not establish a successful band-only push plan")
        }
        try {
            assertThat(built.catalogExercises).isEqualTo(projection.exercises)
            assertThat(withDrafts.automaticEligibilityResult).isNull()
            assertThat(withoutDrafts.automaticEligibilityResult).isNull()
            assertThat(withDrafts.trainingProgramState).isNull()
            assertThat(withoutDrafts.trainingProgramState).isNull()
            assertThat(withDrafts.allowedExercises.map { it.copy(reviewedMetadata = null) })
                .containsExactlyElementsIn(withoutDrafts.allowedExercises).inOrder()

            val first = legacyAttempt(withDrafts)
            val second = legacyAttempt(withoutDrafts)
            report.put("withDrafts", first.workout?.let(::workoutJson)
                ?: JSONObject().put("planningFailure", first.failure?.name))
                .put("withoutDrafts", second.workout?.let(::workoutJson)
                    ?: JSONObject().put("planningFailure", second.failure?.name))
            assertThat(first).isEqualTo(second)
            if (first.workout != null) {
                val withDraftValidation = ProgramValidator(
                    GeneratedWorkoutValidator(InMemoryExerciseCatalog(built.catalogExercises))
                ).validate(first.workout, withDrafts)
                val withoutDraftValidation = ProgramValidator(
                    GeneratedWorkoutValidator(InMemoryExerciseCatalog(strippedCatalog))
                ).validate(requireNotNull(second.workout), withoutDrafts)
                report.put("validationWithDrafts", validationJson(withDraftValidation))
                    .put("validationWithoutDrafts", validationJson(withoutDraftValidation))
                assertThat(withDraftValidation).isInstanceOf(ProgramValidationResult.Valid::class.java)
                assertThat(withoutDraftValidation).isInstanceOf(ProgramValidationResult.Valid::class.java)
                assertThat((withDraftValidation as ProgramValidationResult.Valid).snapshot.reviewedPathEnabled)
                    .isFalse()
                assertThat((withoutDraftValidation as ProgramValidationResult.Valid).snapshot.reviewedPathEnabled)
                    .isFalse()
            } else {
                assertThat(first.failure).isNotNull()
                assertThat(first.failure)
                    .isNotEqualTo(WorkoutPlanningFailure.REVIEWED_ELIGIBILITY_NO_CANDIDATES)
            }
            assertThat(inputSnapshot(built, withDrafts) + inputSnapshot(built, withoutDrafts))
                .isEqualTo(before)
            if (caseId == "full-gym") {
                probeSingleCandidateReachability(withDrafts, built.catalogExercises)
            }
            report.put("deterministicNormalizedOutput", true).put("assertionsPassed", true)
        } finally {
            report.put("inputUnchanged",
                inputSnapshot(built, withDrafts) + inputSnapshot(built, withoutDrafts) == before)
            writeReport("disabled-metadata-invariance", report)
        }
    }

    private data class LegacyAttempt(
        val workout: GeneratedWorkout? = null,
        val failure: WorkoutPlanningFailure? = null
    )

    private suspend fun legacyAttempt(context: WorkoutGenerationContext): LegacyAttempt = try {
        LegacyAttempt(workout = FakeWorkoutPlanner().generateWorkout(context).normalizedPlannerFixtureWorkout())
    } catch (error: WorkoutValidationException) {
        assertThat(error.automaticEligibilityFailure).isNull()
        LegacyAttempt(failure = error.failure)
    }

    private suspend fun probeSingleCandidateReachability(
        fullGymContext: WorkoutGenerationContext,
        catalog: List<Exercise>
    ) {
        val rows = JSONArray()
        val outcomeCounts = linkedMapOf(
            "SELECTED" to 0,
            WorkoutPlanningFailure.NO_STRENGTH_CANDIDATES.name to 0,
            WorkoutPlanningFailure.NO_CANDIDATES_FOR_ANY_SPLIT.name to 0
        )
        val report = JSONObject().put("schemaVersion", 1)
            .put("scope", "Single-candidate legacy reachability; not full-pool selection, eligibility or approval")
            .put("catalogVersion", projection.sourceCommit)
            .put("catalogCount", catalog.size)
            .put("context", contextSettings(fullGymContext))
            .put("results", rows).put("assertionsPassed", false)
        try {
            assertThat(fullGymContext.automaticEligibilityResult).isNull()
            assertThat(fullGymContext.trainingProgramState).isNull()
            assertThat(catalog.map(Exercise::id))
                .containsExactlyElementsIn(projection.exercises.map(Exercise::id)).inOrder()
            for (exercise in catalog) {
                val context = fullGymContext.copy(allowedExercises = listOf(exercise))
                val before = context.toString()
                val attempt = legacyAttempt(context)
                val outcome = attempt.failure?.name ?: "SELECTED"
                val row = JSONObject().put("id", exercise.id)
                    .put("exerciseType", exercise.type.name)
                    .put("outcome", outcome)
                    .put("selectedSplit", attempt.workout?.title?.split?.name ?: JSONObject.NULL)
                    .put("workout", attempt.workout?.let(::workoutJson) ?: JSONObject.NULL)
                rows.put(row)
                outcomeCounts[outcome] = outcomeCounts.getOrDefault(outcome, 0) + 1
                if (attempt.workout != null) {
                    assertThat(attempt.workout.exercises.map { it.exerciseId }).containsExactly(exercise.id)
                } else {
                    assertThat(attempt.failure).isAnyOf(
                        WorkoutPlanningFailure.NO_STRENGTH_CANDIDATES,
                        WorkoutPlanningFailure.NO_CANDIDATES_FOR_ANY_SPLIT
                    )
                }
                assertThat(context.toString()).isEqualTo(before)
            }
            assertThat(rows.length()).isEqualTo(catalog.size)
            report.put("assertionsPassed", true)
        } finally {
            report.put("outcomeCounts", JSONObject(outcomeCounts))
            writeReport("single-candidate-legacy-reachability", report)
        }
    }

    private fun fixture(cohortApprovalIds: List<String> = draftIds): PlannerFixture {
        val resource = when (caseId) {
            "band-only-push-gap" -> "band-only"
            "dumbbells-push", "mixed-unit-history" -> "mixed-unit-history"
            "machines-push" -> "machine-only"
            "full-gym", "uncalibrated", "joint-constraint", "no-equipment",
            "avoid-standing-balance", "avoid-floor-transition" -> "full-gym-advanced"
            "returning" -> "returning-user"
            "sparse-history" -> "sparse-history"
            else -> "bodyweight-beginner"
        }
        val source = PlannerFixtureLoader().loadResource("planner-fixtures/$resource.json")
        var profile = source.profile.copy(
            musclePriorities = mapOf(StandardMuscles.CHEST to PriorityLevel.HIGH),
            movementCapabilities = MovementCapabilities.from(
                MovementCapabilityType.entries.associateWith { CapabilityLevel.COMFORTABLE }
            )
        )
        profile = when (caseId) {
            "dumbbells-push" -> profile.copy(
                availableEquipment = listOf(StandardEquipment.DUMBBELL, StandardEquipment.BENCH),
                confirmedStartingLoads = emptyMap()
            )
            "limited-push", "avoid-push" -> profile.copy(
                movementCapabilities = MovementCapabilities.from(
                    profile.movementCapabilities.values + (
                        MovementCapabilityType.UPPER_BODY_BODYWEIGHT_PUSH to
                            if (caseId == "limited-push") CapabilityLevel.LIMITED
                            else CapabilityLevel.AVOID
                        )
                )
            )
            "joint-constraint" -> profile.copy(
                trainingConstraints = setOf(TrainingConstraint.KNEE_SENSITIVE)
            )
            // An explicitly empty equipment inventory is not implicit Bodyweight access.
            "no-equipment" -> profile.copy(availableEquipment = emptyList())
            "avoid-standing-balance" -> profile.copy(
                preferredDurationMinutes = 35,
                movementCapabilities = MovementCapabilities.from(
                    profile.movementCapabilities.values +
                        (MovementCapabilityType.BALANCE_WITHOUT_SUPPORT to CapabilityLevel.AVOID)
                )
            )
            "avoid-floor-transition" -> profile.copy(
                preferredDurationMinutes = 35,
                movementCapabilities = MovementCapabilities.from(
                    profile.movementCapabilities.values +
                        (MovementCapabilityType.FLOOR_TRANSITION to CapabilityLevel.AVOID)
                )
            )
            "sparse-history" -> profile.copy(
                musclePriorities = mapOf(StandardMuscles.BACK to PriorityLevel.HIGH)
            )
            else -> profile
        }
        return source.copy(
            id = caseId,
            profile = profile,
            completedWorkoutCount = 0,
            exerciseHistory = when (caseId) {
                "mixed-unit-history" -> source.exerciseHistory.filter {
                    it.exerciseId == "incline-dumbbell-press"
                }.map { it.copy(exerciseId = "dumbbell-bench-press") }
                "sparse-history", "machines-push" -> source.exerciseHistory
                else -> emptyList()
            },
            allowedExerciseIds = emptyList(),
            reviewedEligibility = PlannerFixtureReviewedEligibility(
                adaptationState = when (caseId) {
                    "returning" -> AdaptationState.RETURNING
                    "uncalibrated", "bodyweight-push" -> AdaptationState.UNCALIBRATED
                    else -> AdaptationState.BUILD
                },
                syntheticApprovedExerciseIds =
                    if (caseId == "real-no-approved") emptyList() else cohortApprovalIds
            ),
            expected = PlannerFixtureExpected(
                outcome = if (caseId in NO_PLAN_CASES) {
                    PlannerFixtureOutcome.REVIEWED_ELIGIBILITY_NO_CANDIDATES
                } else {
                    PlannerFixtureOutcome.SUCCESS
                },
                requiredExerciseIds = emptySet(),
                forbiddenExerciseIds = emptySet()
            )
        )
    }

    private fun versionedContext(built: PlannerFixtureContext): WorkoutGenerationContext {
        val state = requireNotNull(built.context.trainingProgramState)
        val fixtureLedger = state.weeklyLedger
        val week = TrainingWeek.startingOn(
            fixtureLedger.weekStartEpochDay, ZoneId.of(fixtureLedger.timeZoneId)
        )
        val sessions = if (caseId == "mixed-unit-history") mixedUnitSessions(built, week) else emptyList()
        val exercisesById = built.catalogExercises.associateBy(Exercise::id)
        // Ordinary fixture summaries are not completed sessions. Only the explicitly
        // constructed mixed-unit completed sessions below receive ledger credit.
        val ledger = WeeklyDoseLedgerCalculator().calculate(
            sessions = sessions,
            exercisesById = exercisesById,
            policyVersion = fixtureLedger.policyVersion,
            week = week,
            catalogVersion = projection.sourceCommit,
            reviewPolicyVersion = fixtureLedger.reviewPolicyVersion
        )
        if (sessions.isEmpty()) assertThat(ledger).isEqualTo(fixtureLedger)
        assertThat(WeeklyDoseLedgerCalculator().calculate(
            sessions.reversed(), exercisesById, ledger.policyVersion, week,
            ledger.catalogVersion, ledger.reviewPolicyVersion
        )).isEqualTo(ledger)
        assertThat(LedgerSourceFingerprint.of(
            sessions, exercisesById, ledger.policyVersion, week,
            ledger.catalogVersion, ledger.reviewPolicyVersion
        )).isEqualTo(LedgerSourceFingerprint.of(
            sessions.reversed(), exercisesById, ledger.policyVersion, week,
            ledger.catalogVersion, ledger.reviewPolicyVersion
        ))
        return built.context.copy(
            catalogVersion = projection.sourceCommit,
            reviewPolicyVersion = ledger.reviewPolicyVersion,
            trainingProgramState = state.copy(weeklyLedger = ledger),
            recentWorkoutHistory = sessions,
            exerciseHistory = if (sessions.isEmpty()) built.context.exerciseHistory else
                WorkoutHistoryAnalyzer().exerciseHistory(sessions, built.userProfile.preferredUnit)
        )
    }

    private fun mixedUnitSessions(built: PlannerFixtureContext, week: TrainingWeek): List<WorkoutSession> {
        val history = built.fixture.exerciseHistory.single()
        val sourceSet = history.recentSets.last()
        return listOf(WeightUnit.LBS to 55.0, WeightUnit.KG to 27.5).mapIndexed { index, (unit, load) ->
            val sessionId = "coverage-mixed-unit-session-$index"
            val exerciseInstanceId = "$sessionId-exercise"
            WorkoutSession(
                id = sessionId,
                name = "Synthetic mixed-unit history",
                startedAtTimestamp = week.startEpochMillis + index * 120_000L,
                completedAtTimestamp = week.startEpochMillis + index * 120_000L + 60_000L,
                weightUnit = unit,
                status = SessionStatus.COMPLETED,
                exercises = listOf(WorkoutExercise(
                    id = exerciseInstanceId,
                    sessionId = sessionId,
                    exerciseId = history.exerciseId,
                    orderIndex = 0,
                    targetSets = 1,
                    targetRepMin = 8,
                    targetRepMax = 12,
                    sets = listOf(sourceSet.copy(
                        id = "$exerciseInstanceId-set",
                        workoutExerciseId = exerciseInstanceId,
                        setNumber = 1,
                        completedWeight = load
                    ))
                ))
            )
        }
    }

    private suspend fun assertNoPlan(
        context: WorkoutGenerationContext,
        report: JSONObject,
        syntheticApprovedIds: Set<String>
    ) {
        assertThat(context.allowedExercises).isEmpty()
        val result = context.automaticEligibilityResult as AutomaticEligibilityResult.NoCandidates
        repeat(2) {
            try {
                FakeWorkoutPlanner().generateWorkout(context)
                throw AssertionError("$caseId unexpectedly produced a fallback workout")
            } catch (error: WorkoutValidationException) {
                report.put("planningFailure", error.failure.name)
                    .put("automaticEligibilityFailure", error.automaticEligibilityFailure?.name)
                assertThat(error.failure).isEqualTo(
                    WorkoutPlanningFailure.REVIEWED_ELIGIBILITY_NO_CANDIDATES
                )
                assertThat(error.automaticEligibilityFailure).isEqualTo(result.failure)
            }
        }
        when (caseId) {
            "real-no-approved" -> {
                assertThat(result.failure).isEqualTo(AutomaticEligibilityFailure.NO_APPROVED_METADATA)
                result.decisions.forEach {
                    assertThat(it.reasons).containsExactly(EligibilityReason.MISSING_APPROVED_METADATA)
                }
            }
            "joint-constraint" -> {
                assertThat(result.failure)
                    .isEqualTo(AutomaticEligibilityFailure.TRAINING_CONSTRAINTS_REMOVED_ALL)
                assertThat(syntheticApprovedIds).isNotEmpty()
                result.decisions.filter { it.exerciseId in syntheticApprovedIds }.forEach {
                    assertThat(it.reasons).contains(EligibilityReason.UNMAPPED_TRAINING_CONSTRAINT)
                }
            }
            "no-equipment" -> {
                assertThat(syntheticApprovedIds).isNotEmpty()
                result.decisions.filter { it.exerciseId in syntheticApprovedIds }.forEach {
                    assertThat(it.reasons).contains(EligibilityReason.MISSING_EQUIPMENT)
                }
            }
        }
        report.put("deterministicNormalizedOutput", true)
            .put("validationWithoutRepair", JSONObject().put("type", "NOT_RUN_NO_PROPOSAL"))
    }

    private fun assertPrescriptions(workout: GeneratedWorkout, context: WorkoutGenerationContext) {
        val byId = context.allowedExercises.associateBy(Exercise::id)
        assertThat(workout.exercises).isNotEmpty()
        assertThat(workout.exercises.map { it.exerciseId }).containsNoDuplicates()
        workout.exercises.forEach { planned ->
            val exercise = requireNotNull(byId[planned.exerciseId])
            assertThat(exercise.isStretch).isFalse()
            assertThat(exercise.type).isNotEqualTo(ExerciseType.DISTANCE_DURATION)
            assertThat(planned.prescription.exerciseType).isEqualTo(exercise.type)
            assertThat(planned.prescription.restTargetSource).isEqualTo(RestTargetSource.PRODUCT_POLICY)
            if (context.exerciseHistory[exercise.id]?.lastWeight == null &&
                context.userProfile.confirmedStartingLoads[exercise.id] == null
            ) {
                assertThat(planned.targetWeight).isNull()
                assertThat(planned.prescription.targetAssistanceWeight).isNull()
            }
            if (context.trainingProgramState!!.adaptationState in setOf(
                    AdaptationState.UNCALIBRATED, AdaptationState.RETURNING
                )
            ) {
                assertThat(planned.targetSets).isAtMost(2)
                assertThat(planned.prescription.effortTarget)
                    .isEqualTo(StateBasedTrainingPolicyDefaults.V1.conservativeEffort)
            }
        }
    }

    private fun assertScenarioPremise(
        workout: GeneratedWorkout,
        context: WorkoutGenerationContext,
        report: JSONObject,
        syntheticApprovedIds: Set<String>
    ) {
        val byId = context.allowedExercises.associateBy(Exercise::id)
        val selected = workout.exercises.map { byId.getValue(it.exerciseId) }
        if (caseId in PUSH_CASES || caseId == "band-only-push-gap") {
            // A shoulder secondary on a row or Pallof press must not masquerade as a push.
            val candidatePush = context.allowedExercises.filter {
                it.reviewedMetadata!!.movementPattern in PUSH_PATTERNS &&
                    it.reviewedMetadata.directPrimaryMuscle in WorkoutSplit.PUSH.targetMuscles
            }.map(Exercise::id)
            val selectedPush = selected.filter { it.id in candidatePush }.map(Exercise::id)
            report.put("candidateViablePushIds", JSONArray(candidatePush))
                .put("selectedViablePushIds", JSONArray(selectedPush))
                .put("pushCoverageSatisfied", selectedPush.isNotEmpty())
            if (caseId == "band-only-push-gap") {
                report.put("coverageGap", candidatePush.isEmpty() && selectedPush.isEmpty())
                    .put("coverageGapKind", "NO_DIRECT_PRIMARY_BAND_PUSH")
                    .put("coverageInterpretation",
                        "Negative coverage regression: schema-valid output is not a successful push plan")
                assertThat(context.availableEquipment).containsExactly(StandardEquipment.RESISTANCE_BAND)
                assertThat(context.allowedExercises).isNotEmpty()
                assertWithMessage("Reassess the documented band-only gap if a real push becomes eligible")
                    .that(candidatePush).isEmpty()
                assertThat(selectedPush).isEmpty()
            } else {
                assertThat(workout.title.split).isEqualTo(WorkoutSplit.PUSH)
                assertWithMessage("$caseId requires a selected direct-primary push, not secondary involvement")
                    .that(selectedPush).isNotEmpty()
            }
        }
        if (caseId == "avoid-standing-balance") {
            val decisions = requireNotNull(context.automaticEligibilityResult).decisions
                .associateBy { it.exerciseId }
            for (id in listOf("wrist-curl", "wrist-extension", "inchworm")) {
                val decision = decisions.getValue(id)
                assertThat(decision.eligible).isFalse()
                assertThat(decision.reasons).contains(
                    if (id in syntheticApprovedIds) EligibilityReason.CAPABILITY_AVOID
                    else EligibilityReason.MISSING_APPROVED_METADATA
                )
                assertThat(selected.map(Exercise::id)).doesNotContain(id)
            }
            assertThat(context.allowedExercises.none {
                MovementCapabilityType.BALANCE_WITHOUT_SUPPORT in
                    requireNotNull(it.reviewedMetadata).capabilityRequirements
            }).isTrue()
        }
        if (caseId == "avoid-floor-transition") {
            assertThat(syntheticApprovedIds).contains("wall-handstand-push-up")
            val decision = requireNotNull(context.automaticEligibilityResult).decisions
                .single { it.exerciseId == "wall-handstand-push-up" }
            assertThat(decision.eligible).isFalse()
            assertThat(decision.reasons).contains(EligibilityReason.CAPABILITY_AVOID)
            assertThat(context.allowedExercises.none {
                MovementCapabilityType.FLOOR_TRANSITION in
                    requireNotNull(it.reviewedMetadata).capabilityRequirements
            }).isTrue()
        }
        if (caseId == "limited-push" || caseId == "avoid-push") {
            val decisions = requireNotNull(context.automaticEligibilityResult).decisions
            val affected = projection.exercises.filter {
                it.id in syntheticApprovedIds &&
                    MovementCapabilityType.UPPER_BODY_BODYWEIGHT_PUSH in
                    it.reviewedMetadata!!.capabilityRequirements
            }.map(Exercise::id).toSet()
            assertThat(affected).contains("push-up")
            // A pending knee-push-up support-label decision excludes it from the AI-ready cohort.
            if ("knee-push-up" in syntheticApprovedIds) {
                assertThat(affected).contains("knee-push-up")
            }
            val affectedDecisions = decisions.filter {
                it.exerciseId in affected && EligibilityReason.MISSING_EQUIPMENT !in it.reasons
            }
            report.put("capabilityProbe", JSONObject()
                .put("requirement", MovementCapabilityType.UPPER_BODY_BODYWEIGHT_PUSH.name)
                .put("syntheticApprovedRequirementIds", JSONArray(affected))
                .put("equipmentCompatibleRequirementIds", JSONArray(affectedDecisions.map { it.exerciseId }))
                .put("selectedRequirementIds", JSONArray(selected.filter { it.id in affected }.map(Exercise::id))))
            assertThat(affectedDecisions).isNotEmpty()
            if (caseId == "limited-push") {
                affectedDecisions.forEach {
                    assertThat(it.eligible).isTrue()
                    assertThat(it.preferences).contains(
                        EligibilityPreference.Limited(MovementCapabilityType.UPPER_BODY_BODYWEIGHT_PUSH)
                    )
                }
                val affectedSelected = workout.exercises.filter { it.exerciseId in affected }
                assertThat(affectedSelected).isNotEmpty()
                affectedSelected.forEach { assertThat(it.targetSets).isAtMost(2) }
            } else {
                affectedDecisions.forEach {
                    assertThat(it.eligible).isFalse()
                    assertThat(it.reasons).contains(EligibilityReason.CAPABILITY_AVOID)
                }
                assertThat(selected.none { it.id in affected }).isTrue()
            }
        }
        if (caseId == "returning") assertThat(workout.title.isReEntry).isTrue()
        if (caseId == "mixed-unit-history") {
            assertThat(context.preferredUnits).isEqualTo(WeightUnit.KG)
            assertThat(context.recentWorkoutHistory.map { it.weightUnit })
                .containsExactly(WeightUnit.LBS, WeightUnit.KG).inOrder()
            assertThat(context.exerciseHistory.getValue("dumbbell-bench-press").lastWeight)
                .isEqualTo(27.5)
            assertThat(selected.map(Exercise::id)).contains("dumbbell-bench-press")
            assertThat(workout.exercises.single { it.exerciseId == "dumbbell-bench-press" }.targetWeight)
                .isEqualTo(27.5)
            val primary = byId.getValue("dumbbell-bench-press").reviewedMetadata!!.directPrimaryMuscle
            assertThat(context.trainingProgramState!!.weeklyLedger.directPrimarySets)
                .containsExactly(primary, 2)
            val pounds = WorkoutHistoryAnalyzer().exerciseHistory(context.recentWorkoutHistory, WeightUnit.LBS)
            assertThat(pounds.getValue("dumbbell-bench-press").lastWeight)
                .isWithin(0.000001).of(60.6271221)
            val olderPoundsInKg = WorkoutHistoryAnalyzer().exerciseHistory(
                context.recentWorkoutHistory.take(1), WeightUnit.KG
            )
            assertThat(olderPoundsInKg.getValue("dumbbell-bench-press").lastWeight)
                .isWithin(0.000001).of(24.94758035)
        }
        if (caseId == "sparse-history") {
            assertThat(context.exerciseHistory.keys).containsExactly("inverted-row")
            assertThat(context.exerciseHistory.getValue("inverted-row").lastWeight).isNull()
            assertThat(workout.exercises.mapNotNull { it.targetWeight }).isEmpty()
        }
    }

    private fun report(
        built: PlannerFixtureContext,
        context: WorkoutGenerationContext,
        cohort: String,
        cohortApprovalIds: List<String>
    ): JSONObject {
        val ledger = context.trainingProgramState!!.weeklyLedger
        val week = TrainingWeek.startingOn(ledger.weekStartEpochDay, ZoneId.of(ledger.timeZoneId))
        val fingerprint = LedgerSourceFingerprint.of(
            sessions = context.recentWorkoutHistory,
            exercisesById = built.catalogExercises.associateBy(Exercise::id),
            policyVersion = ledger.policyVersion,
            week = week,
            catalogVersion = ledger.catalogVersion,
            reviewPolicyVersion = ledger.reviewPolicyVersion
        )
        return JSONObject()
            .put("schemaVersion", 1)
            .put("cohort", cohort)
            .put("caseId", caseId)
            .put("syntheticTestOnly", true)
            .put("contentReadyOrClinicallyValidated", false)
            .put("context", contextSettings(context))
            .put("assertionsPassed", false)
            .put("catalogVersion", context.catalogVersion)
            .put("reviewPolicyVersion", context.reviewPolicyVersion)
            .put("catalogCount", projection.exercises.size)
            .put("draftCount", draftIds.size)
            .put("cohortApprovalIds", JSONArray(cohortApprovalIds))
            .put("readinessLedgerPath", if (cohort == READY_COHORT) READINESS_LEDGER_PATH else JSONObject.NULL)
            .put("readyForHumanReviewIds", if (cohort == READY_COHORT) JSONArray(cohortApprovalIds) else JSONObject.NULL)
            .put("readinessInterpretation", if (cohort == READY_COHORT) {
                "AI-ready for human review only; synthetic approvals are not human approval or clinical validation"
            } else {
                "All-DRAFT structural upper bound includes pending conflicts; not an AI-ready cohort"
            })
            .put("syntheticApprovedIds", JSONArray(
                built.fixture.reviewedEligibility!!.syntheticApprovedExerciseIds
            ))
            .put("equipment", JSONArray(context.availableEquipment))
            .put("trainingConstraints", JSONArray(context.userProfile.trainingConstraints.map { it.name }))
            .put("preferredUnits", context.preferredUnits.name)
            .put("historySourceUnits", JSONArray(context.recentWorkoutHistory.map { it.weightUnit.name }))
            .put("adaptationState", context.trainingProgramState.adaptationState.name)
            .put("capabilities", JSONObject(
                context.userProfile.movementCapabilities.values.mapKeys { it.key.name }
                    .mapValues { it.value.name }
            ))
            .put("candidateCount", context.allowedExercises.size)
            .put("candidateIds", JSONArray(context.allowedExercises.map(Exercise::id)))
            .put("selectedIds", JSONArray())
            .put("eligibilityDecisions", JSONArray(
                context.automaticEligibilityResult!!.decisions.map {
                    JSONObject().put("exerciseId", it.exerciseId).put("eligible", it.eligible)
                        .put("reasons", JSONArray(it.reasons.map { reason -> reason.name }))
                        .put("preferences", JSONArray(it.preferences.map { preference -> preference.toString() }))
                }
            ))
            .put("ledger", JSONObject()
                .put("policyVersion", ledger.policyVersion.name)
                .put("completedDirectPrimarySets", JSONObject(ledger.directPrimarySets))
                .put("completedSecondaryInvolvement", JSONObject(ledger.secondaryInvolvement))
                .put("omittedWorkSets", JSONObject(ledger.unattributedWorkSets.mapKeys { it.key.name }))
                .put("sourceFingerprint", fingerprint)
                .put("performanceSummaryIds", JSONArray(context.exerciseHistory.keys.sorted()))
                .put("completedSessionCount", context.recentWorkoutHistory.size))
            .put("legacyReviewedPatternDifferences", JSONArray(
                built.catalogExercises.filter {
                    it.programming != null && it.reviewedMetadata != null &&
                        it.programming.movementPattern != it.reviewedMetadata.movementPattern
                }.map { it.id }
            ))
            .put("legacyReviewedEquipmentDifferences", JSONArray(
                built.catalogExercises.filter {
                    it.programming != null && it.reviewedMetadata != null &&
                        it.programming.requiredEquipmentCombinations.map { option -> option.toSet() }.toSet() !=
                        it.reviewedMetadata.equipmentAlternatives.map { option -> option.toSet() }.toSet()
                }.map { it.id }
            ))
    }

    private fun workoutJson(workout: GeneratedWorkout): JSONObject = JSONObject()
        .put("normalizedId", workout.normalizedPlannerFixtureWorkout().id)
        .put("split", workout.title.split.name)
        .put("title", workout.title.toString())
        .put("rationale", workout.rationale.toString())
        .put("focusMuscles", JSONArray(workout.focusMuscles))
        .put("estimatedDurationMinutes", workout.estimatedDurationMinutes)
        .put("exercises", JSONArray(workout.exercises.map {
            JSONObject().put("exerciseId", it.exerciseId)
                .put("prescription", prescriptionJson(it.prescription)).put("notes", it.notes)
        }))

    private fun contextSettings(context: WorkoutGenerationContext): JSONObject {
        val profile = context.userProfile
        return JSONObject()
            .put("personaSource", "Synthetic test-only profile derived from PlannerFixtureLoader")
            .put("profile", JSONObject()
                .put("id", profile.id)
                .put("revision", profile.revision)
                .put("name", profile.name)
                .put("onboardingCompleted", profile.onboardingCompleted)
                .put("goals", JSONArray(profile.goals.map { it.name }))
                .put("experienceLevel", profile.experienceLevel.name)
                .put("preferredDurationMinutes", profile.preferredDurationMinutes)
                .put("daysPerWeek", profile.daysPerWeek)
                .put("availableEquipment", JSONArray(profile.availableEquipment))
                .put("preferredUnit", profile.preferredUnit.name)
                .put("musclePriorities", JSONObject(profile.musclePriorities.mapValues { it.value.name }))
                .put("excludedExerciseIds", JSONArray(profile.excludedExerciseIds))
                .put("trainingConstraints", JSONArray(profile.trainingConstraints.map { it.name }))
                .put("returningAfterBreakWeeks", profile.returningAfterBreakWeeks)
                .put("confirmedStartingLoads", JSONObject(profile.confirmedStartingLoads))
                .put("movementCapabilities", JSONObject(profile.movementCapabilities.values
                    .mapKeys { it.key.name }.mapValues { it.value.name })))
            .put("completedWorkoutCount", context.completedWorkoutCount)
            .put("initialGenerationIndex", 0)
            .put("plannerInstancePolicy", "Fresh FakeWorkoutPlanner instance for each comparison")
            .put("automaticEligibilityPresent", context.automaticEligibilityResult != null)
            .put("adaptationState", context.trainingProgramState?.adaptationState?.name ?: JSONObject.NULL)
            .put("requiredMovementPatterns", JSONArray(context.programConstraints.requiredMovementPatterns
                .map { it.name }))
            .put("pushCoverageAssertion", when {
                caseId == "band-only-push-gap" && context.automaticEligibilityResult != null ->
                    "Negative premise: no direct-primary horizontal/vertical push in the full eligible pool or selected set"
                caseId in PUSH_CASES && context.automaticEligibilityResult != null ->
                    "Test premise only: selected reviewed HORIZONTAL_PUSH or VERTICAL_PUSH with direct-primary push muscle"
                else -> "No anatomical push coverage requirement"
            })
    }

    private fun prescriptionJson(prescription: ExercisePrescription): JSONObject = JSONObject()
        .put("exerciseType", prescription.exerciseType.name)
        .put("targetSets", prescription.targetSets)
        .put("repMin", prescription.repRange?.min ?: JSONObject.NULL)
        .put("repMax", prescription.repRange?.max ?: JSONObject.NULL)
        .put("targetWeight", prescription.targetWeight ?: JSONObject.NULL)
        .put("targetAssistanceWeight", prescription.targetAssistanceWeight ?: JSONObject.NULL)
        .put("targetDurationSeconds", prescription.targetDurationSeconds ?: JSONObject.NULL)
        .put("targetDistanceMeters", prescription.targetDistanceMeters ?: JSONObject.NULL)
        .put("restSeconds", prescription.restSeconds)
        .put("restClass", prescription.restClass?.name ?: JSONObject.NULL)
        .put("restTargetSource", prescription.restTargetSource?.name ?: JSONObject.NULL)
        .put("effortTarget", prescription.effortTarget?.let {
            JSONObject().put("minRir", it.minRir).put("maxRir", it.maxRir)
        } ?: JSONObject.NULL)

    private fun validationJson(result: ProgramValidationResult): JSONObject = when (result) {
        is ProgramValidationResult.Invalid -> JSONObject()
            .put("type", "Invalid")
            .put("violations", JSONArray(result.violations.map {
                JSONObject().put("code", it.code.name)
                    .put("exerciseId", it.exerciseId ?: JSONObject.NULL)
                    .put("orderIndex", it.orderIndex ?: JSONObject.NULL)
                    .put("detail", it.detail ?: JSONObject.NULL)
            }))
        is ProgramValidationResult.Valid -> JSONObject()
            .put("type", "Valid")
            .put("outcome", result.snapshot.outcome.name)
            .put("reasonCodes", JSONArray(result.snapshot.reasonCodes.map { it.name }))
            .put("workout", workoutJson(result.workout))
            .put("snapshot", JSONObject()
                .put("validatorVersion", result.snapshot.validatorVersion.name)
                .put("durationEstimatorVersion", result.snapshot.durationEstimatorVersion)
                .put("catalogVersion", result.snapshot.catalogVersion)
                .put("reviewPolicyVersion", result.snapshot.reviewPolicyVersion)
                .put("contextIdentity", result.snapshot.contextIdentity)
                .put("adaptationState", result.snapshot.adaptationState?.name)
                .put("trainingPolicyVersion", result.snapshot.trainingPolicyVersion?.name)
                .put("ledgerPolicyVersion", result.snapshot.ledgerPolicyVersion?.name)
                .put("programStatePolicyVersion", result.snapshot.programStatePolicyVersion?.name)
                .put("profileRevision", result.snapshot.profileRevision)
                .put("weekStartEpochDay", result.snapshot.weekStartEpochDay)
                .put("timeZoneId", result.snapshot.timeZoneId)
                .put("doseAccounting", JSONArray(result.snapshot.doseAccounting.map {
                    JSONObject().put("muscle", it.muscle).put("completedSets", it.completedSets)
                        .put("proposedSets", it.proposedSets).put("allowanceSets", it.allowanceSets)
                })))
    }

    // Eager value strings capture mutable collections before either planner or validator runs.
    // Capability values need an explicit read because their normal toString is redacted.
    private fun inputSnapshot(built: PlannerFixtureContext, context: WorkoutGenerationContext) =
        listOf(context.toString(), built.catalogExercises.toString(), projection.exercises.toString(),
            context.userProfile.movementCapabilities.values.toString())

    private fun writeReport(cohort: String, report: JSONObject) {
        val directory = File("build/reports/reviewed-catalog-coverage/$cohort")
        check(directory.isDirectory || directory.mkdirs()) {
            "Cannot create coverage report directory: $directory"
        }
        File(directory, "$caseId.json").writeText(report.toString(2) + "\n")
    }

    companion object {
        private const val COHORT = "all-draft-structural-upper-bound"
        private const val READY_COHORT = "ai-ready-synthetic"
        private const val READINESS_LEDGER_PATH = "docs/research/2026-09-07-full-exercise-catalog-review.json"
        private val NO_PLAN_CASES = setOf("joint-constraint", "no-equipment", "real-no-approved")
        private val PUSH_PATTERNS = setOf(MovementPattern.HORIZONTAL_PUSH, MovementPattern.VERTICAL_PUSH)
        private val PUSH_CASES = setOf(
            "bodyweight-push", "dumbbells-push", "machines-push",
            "full-gym", "limited-push", "returning", "uncalibrated"
        )

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<String>> = listOf(
            "bodyweight-push", "band-only-push-gap", "dumbbells-push", "machines-push",
            "full-gym", "limited-push", "avoid-push", "returning", "mixed-unit-history",
            "sparse-history", "uncalibrated", "joint-constraint", "no-equipment",
            "real-no-approved", "avoid-standing-balance", "avoid-floor-transition"
        ).map { arrayOf(it) }
    }
}
