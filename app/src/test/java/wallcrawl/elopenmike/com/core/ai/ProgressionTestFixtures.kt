package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.*

internal fun progressionSession(
    id: String,
    exerciseId: String,
    prescription: ExercisePrescription,
    start: Long,
    unit: WeightUnit = WeightUnit.KG
) = WorkoutSession(
    id = id, name = "Progression fixture", startedAtTimestamp = start,
    completedAtTimestamp = start + 2_000, status = SessionStatus.COMPLETED, weightUnit = unit,
    exercises = listOf(WorkoutExercise(
        id = "$id-exercise", sessionId = id, exerciseId = exerciseId, orderIndex = 0,
        prescription = prescription,
        sets = (1..prescription.targetSets).map { number -> WorkoutSet(
            id = "$id-set-$number", workoutExerciseId = "$id-exercise", setNumber = number,
            exerciseType = prescription.exerciseType, targetReps = prescription.repRange?.max,
            completedReps = prescription.repRange?.max, targetWeight = prescription.targetWeight,
            completedWeight = prescription.targetWeight, targetAssistanceWeight = prescription.targetAssistanceWeight,
            completedAssistanceWeight = prescription.targetAssistanceWeight,
            targetDurationSeconds = prescription.targetDurationSeconds,
            completedDurationSeconds = prescription.targetDurationSeconds,
            targetDistanceMeters = prescription.targetDistanceMeters,
            completedDistanceMeters = prescription.targetDistanceMeters,
            isCompleted = true, rir = 3, feltManageable = true, completedAtTimestamp = start + number * 100
        ) }
    ))
)

internal fun progressionRecord(sessionId: String, decisions: List<ProgressionDecision>) = RecommendationRecord(
    sessionId = sessionId, validatorVersion = "WHOLE_PROGRAM_V1",
    durationEstimatorVersion = "DURATION_ESTIMATOR_V1", outcome = "VALID",
    reviewedPathEnabled = true, catalogVersion = "fixture", reviewPolicyVersion = 1,
    trainingPolicyVersion = "STATE_BASED_DOSE_EFFORT_REST_V2", ledgerPolicyVersion = "PRIMARY_ONLY_V1",
    programStatePolicyVersion = "PROGRAM_STATE_V2", adaptationState = "UNCALIBRATED",
    weekStartEpochDay = 0, timeZoneId = "UTC", profileRevision = 1, contextIdentity = "fixture",
    reasonCodes = ProgressionReasonCode.encode(decisions.map { it.provenance() }),
    doseAccounting = emptyList(), recordedAtEpochMillis = 1
)
