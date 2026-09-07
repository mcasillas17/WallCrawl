package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.AdaptationState
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityResult
import wallcrawl.elopenmike.com.core.model.EligibilityDecision
import wallcrawl.elopenmike.com.core.model.EligibilityReason
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.SessionProgramConstraints
import wallcrawl.elopenmike.com.core.model.TrainingProgramState
import wallcrawl.elopenmike.com.core.model.TrainingProgramStatePolicyVersion
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeeklyDoseLedger
import wallcrawl.elopenmike.com.core.model.WorkoutEmphasis
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext
import wallcrawl.elopenmike.com.core.model.WorkoutRationaleSpec
import wallcrawl.elopenmike.com.core.model.WorkoutSplit
import wallcrawl.elopenmike.com.core.model.WorkoutTitleSpec

/**
 * Shared fixtures for whole-program validation.
 *
 * Every approved reviewed block these build comes from [syntheticApprovedExercise], which
 * is test-only and unreachable from `src/main`, so a synthetic approval can never reach the
 * bundled catalog or a real user's plan.
 */
const val VALIDATOR_CATALOG_VERSION: String = "catalog-commit-under-test"

/** A structurally valid plan whose reported duration agrees with `DURATION_ESTIMATOR_V1`. */
fun validatedWorkout(exercises: List<PlannedExercise>): GeneratedWorkout = GeneratedWorkout(
    title = WorkoutTitleSpec(split = WorkoutSplit.PUSH, emphasis = WorkoutEmphasis.HYPERTROPHY),
    rationale = WorkoutRationaleSpec.GoalFocus(goals = emptyList(), focusMuscles = emptyList()),
    focusMuscles = listOf("Chest"),
    estimatedDurationMinutes = WorkoutDurationEstimator.estimateMinutes(exercises),
    exercises = exercises
)

fun repetitionPlan(
    exerciseId: String,
    targetSets: Int = 3,
    targetWeight: Double? = null,
    restSeconds: Int = 90
): PlannedExercise = PlannedExercise(
    exerciseId = exerciseId,
    prescription = ExercisePrescription(
        exerciseType = ExerciseType.WEIGHT_REPS,
        targetSets = targetSets,
        repRange = RepRange(8, 10),
        targetWeight = targetWeight,
        restSeconds = restSeconds
    )
)

fun validatorContext(
    allowedExercises: List<Exercise>,
    profile: UserProfile = UserProfile(id = "profile-under-test", revision = 1),
    reviewedPath: Boolean = false,
    adaptationState: AdaptationState = AdaptationState.UNCALIBRATED,
    directPrimarySets: Map<String, Int> = emptyMap(),
    ledgerPolicyVersion: LedgerPolicyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
    ledgerReviewPolicyVersion: Int = 1,
    ledgerCatalogVersion: String = VALIDATOR_CATALOG_VERSION,
    programConstraints: SessionProgramConstraints = SessionProgramConstraints(),
    exerciseHistory: Map<String, wallcrawl.elopenmike.com.core.model.ExercisePerformanceHistory> =
        emptyMap(),
    ineligibleExerciseIds: Set<String> = emptySet(),
    /**
     * Whether an eligibility result is present, independently of the program state.
     *
     * They always travel together in production, and that is exactly why a test has to be
     * able to separate them: a rule gated on the wrong one of the two would look correct
     * forever.
     */
    reviewedEligibilityResult: Boolean = reviewedPath
): WorkoutGenerationContext = WorkoutGenerationContext(
    userProfile = profile,
    allowedExercises = allowedExercises,
    exerciseHistory = exerciseHistory,
    catalogVersion = VALIDATOR_CATALOG_VERSION,
    reviewPolicyVersion = 1,
    programConstraints = programConstraints,
    automaticEligibilityResult = if (reviewedEligibilityResult) {
        AutomaticEligibilityResult.Candidates(
            exercises = allowedExercises,
            decisions = allowedExercises.map { exercise ->
                val eligible = exercise.id !in ineligibleExerciseIds
                EligibilityDecision(
                    exerciseId = exercise.id,
                    eligible = eligible,
                    reasons = if (eligible) {
                        listOf(EligibilityReason.APPROVED)
                    } else {
                        listOf(EligibilityReason.USER_EXCLUDED)
                    }
                )
            }
        )
    } else {
        null
    },
    trainingProgramState = if (reviewedPath) {
        TrainingProgramState(
            policyVersion = TrainingProgramStatePolicyVersion.PROGRAM_STATE_V1,
            adaptationState = adaptationState,
            weeklyLedger = WeeklyDoseLedger(
                policyVersion = ledgerPolicyVersion,
                weekStartEpochDay = MONDAY_EPOCH_DAY,
                timeZoneId = "America/Mexico_City",
                catalogVersion = ledgerCatalogVersion,
                reviewPolicyVersion = ledgerReviewPolicyVersion,
                directPrimarySets = directPrimarySets,
                secondaryInvolvement = emptyMap(),
                unattributedWorkSets = emptyMap()
            )
        )
    } else {
        null
    }
)
