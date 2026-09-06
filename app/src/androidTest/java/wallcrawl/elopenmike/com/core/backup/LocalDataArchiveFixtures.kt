package wallcrawl.elopenmike.com.core.backup

import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.EffortTarget
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.RestClass
import wallcrawl.elopenmike.com.core.model.RestTargetSource
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetStopReason
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutOrigin
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate

/**
 * One snapshot exercising every shape the archive has to carry without losing meaning.
 *
 * It deliberately mixes units between sessions, includes each supported exercise type, every
 * set outcome (completed with feedback, stopped with a typed reason, never recorded, and
 * completed history from before completion timestamps were stored), an active workout, a
 * cancelled workout, and prescriptions with and without effort and rest guidance.
 */
object LocalDataArchiveFixtures {

    fun metadata(
        archiveVersion: Int = LocalDataArchiveFormat.ARCHIVE_VERSION,
        catalogCommit: String? = "ba0b709cb20430361b2cb33aaadd20998164a916"
    ) = LocalDataArchiveMetadata(
        archiveVersion = archiveVersion,
        createdAtEpochMillis = 1_735_689_600_000L,
        appVersionName = "0.1.0-dev",
        appVersionCode = 1L,
        roomSchemaVersion = 11,
        catalogCommit = catalogCommit
    )

    fun profile() = UserProfile(
        id = UserProfile.DEFAULT_PROFILE_ID,
        revision = 7L,
        name = "Crawler",
        goals = linkedSetOf(FitnessGoal.STRENGTH, FitnessGoal.BUILD_MUSCLE),
        experienceLevel = ExperienceLevel.ADVANCED,
        preferredDurationMinutes = 65,
        daysPerWeek = 5,
        availableEquipment = listOf(
            StandardEquipment.BODYWEIGHT,
            StandardEquipment.BARBELL,
            StandardEquipment.PULLUP_BAR
        ),
        preferredUnit = WeightUnit.KG,
        // Canonical vocabulary names, exactly as live persistence stores them: the archive
        // records what the app records, it does not introduce a second spelling.
        musclePriorities = mapOf(
            StandardMuscles.CHEST to PriorityLevel.HIGH,
            StandardMuscles.BACK to PriorityLevel.LOW
        ),
        excludedExerciseIds = listOf("barbell-back-squat"),
        onboardingCompleted = true,
        trainingConstraints = linkedSetOf(TrainingConstraint.KNEE_SENSITIVE),
        returningAfterBreakWeeks = 12,
        confirmedStartingLoads = mapOf("barbell-bench-press" to 62.5),
        movementCapabilities = MovementCapabilities.from(
            mapOf(
                MovementCapabilityType.IMPACT to CapabilityLevel.AVOID,
                MovementCapabilityType.VERTICAL_PULL_OR_HANG to CapabilityLevel.COMFORTABLE
            )
        ),
        themePreference = ThemePreference.LIGHT
    )

    fun templates(): List<WorkoutTemplate> = listOf(
        WorkoutTemplate(
            id = "template-upper",
            name = "Upper Focus",
            notes = "Bar work first",
            createdAtTimestamp = 1_700_000_000_000L,
            updatedAtTimestamp = 1_700_000_500_000L,
            exercises = listOf(
                PlannedExercise(
                    exerciseId = "barbell-bench-press",
                    prescription = ExercisePrescription(
                        exerciseType = ExerciseType.WEIGHT_REPS,
                        targetSets = 4,
                        repRange = RepRange(4, 6),
                        targetWeight = 70.0,
                        restSeconds = 180,
                        effortTarget = EffortTarget(minRir = 1, maxRir = 2),
                        restClass = RestClass.LONG,
                        restTargetSource = RestTargetSource.USER_PREFERENCE
                    ),
                    notes = "Pause on the chest"
                ),
                PlannedExercise(
                    exerciseId = "pull-up",
                    prescription = ExercisePrescription(
                        exerciseType = ExerciseType.BODYWEIGHT_REPS,
                        targetSets = 3,
                        repRange = RepRange(5, 8),
                        restSeconds = 120
                    )
                )
            )
        ),
        WorkoutTemplate(
            id = "template-conditioning",
            name = "Conditioning",
            notes = "",
            createdAtTimestamp = 1_700_100_000_000L,
            updatedAtTimestamp = 1_700_100_000_000L,
            exercises = listOf(
                PlannedExercise(
                    exerciseId = "row-erg",
                    prescription = ExercisePrescription(
                        exerciseType = ExerciseType.DISTANCE_DURATION,
                        targetSets = 2,
                        targetDurationSeconds = 600,
                        targetDistanceMeters = 2_000.0,
                        restSeconds = 240
                    )
                )
            )
        )
    )

    fun sessions(): List<WorkoutSession> = listOf(
        completedPoundsSession(),
        completedKilogramSession(),
        cancelledSession(),
        activeSession()
    )

    fun snapshot(
        profile: UserProfile? = profile(),
        templates: List<WorkoutTemplate> = templates(),
        sessions: List<WorkoutSession> = sessions()
    ) = LocalDataSnapshot(profile = profile, templates = templates, sessions = sessions)

    fun archive(
        metadata: LocalDataArchiveMetadata = metadata(),
        snapshot: LocalDataSnapshot = snapshot()
    ) = LocalDataArchive(metadata, snapshot)

    /** Weight and repetition work logged in pounds, with full effort feedback. */
    private fun completedPoundsSession(): WorkoutSession {
        val exerciseId = "session-1-exercise-1"
        return WorkoutSession(
            id = "session-1",
            name = "Push Day",
            startedAtTimestamp = 1_700_200_000_000L,
            completedAtTimestamp = 1_700_203_600_000L,
            targetDurationMinutes = 60,
            actualDurationMinutes = 58,
            weightUnit = WeightUnit.LBS,
            status = SessionStatus.COMPLETED,
            origin = WorkoutOrigin.PLANNER,
            sourceTemplateId = null,
            focusMuscles = listOf("chest", "triceps"),
            notes = "Felt strong",
            exercises = listOf(
                WorkoutExercise(
                    id = exerciseId,
                    sessionId = "session-1",
                    exerciseId = "barbell-bench-press",
                    orderIndex = 0,
                    prescription = ExercisePrescription(
                        exerciseType = ExerciseType.WEIGHT_REPS,
                        targetSets = 3,
                        repRange = RepRange(5, 8),
                        targetWeight = 155.0,
                        restSeconds = 150,
                        effortTarget = EffortTarget(minRir = 2, maxRir = 3),
                        restClass = RestClass.MODERATE,
                        restTargetSource = RestTargetSource.PRODUCT_POLICY
                    ),
                    notes = "Wide grip",
                    sets = listOf(
                        WorkoutSet(
                            id = "session-1-set-1",
                            workoutExerciseId = exerciseId,
                            setNumber = 1,
                            exerciseType = ExerciseType.WEIGHT_REPS,
                            targetReps = 8,
                            completedReps = 8,
                            targetWeight = 155.0,
                            completedWeight = 155.0,
                            isCompleted = true,
                            rpe = 8.5f,
                            rir = 2,
                            feltManageable = true,
                            completedAtTimestamp = 1_700_200_600_000L,
                            type = SetType.NORMAL
                        ),
                        // Logged before typed outcomes existed: completed, time unknown, and
                        // never given a fabricated one.
                        WorkoutSet(
                            id = "session-1-set-2",
                            workoutExerciseId = exerciseId,
                            setNumber = 2,
                            exerciseType = ExerciseType.WEIGHT_REPS,
                            targetReps = 8,
                            completedReps = 7,
                            targetWeight = 155.0,
                            completedWeight = 155.0,
                            isCompleted = true,
                            type = SetType.NORMAL
                        ),
                        WorkoutSet(
                            id = "session-1-set-3",
                            workoutExerciseId = exerciseId,
                            setNumber = 3,
                            exerciseType = ExerciseType.WEIGHT_REPS,
                            targetReps = 8,
                            targetWeight = 155.0,
                            isCompleted = false,
                            rpe = 9f,
                            stoppedAtTimestamp = 1_700_202_000_000L,
                            stopReason = SetStopReason.TIME_CONSTRAINT,
                            type = SetType.FAILURE
                        )
                    )
                )
            )
        )
    }

    /** The same history in kilograms, with assisted, duration, and distance work. */
    private fun completedKilogramSession(): WorkoutSession {
        val assisted = "session-2-exercise-1"
        val hold = "session-2-exercise-2"
        val row = "session-2-exercise-3"
        return WorkoutSession(
            id = "session-2",
            name = "Pull and Carry",
            startedAtTimestamp = 1_700_300_000_000L,
            completedAtTimestamp = 1_700_303_000_000L,
            targetDurationMinutes = 50,
            actualDurationMinutes = 50,
            weightUnit = WeightUnit.KG,
            status = SessionStatus.COMPLETED,
            origin = WorkoutOrigin.CUSTOM_TEMPLATE,
            sourceTemplateId = "template-upper",
            focusMuscles = emptyList(),
            notes = "",
            exercises = listOf(
                WorkoutExercise(
                    id = assisted,
                    sessionId = "session-2",
                    exerciseId = "assisted-pull-up",
                    orderIndex = 0,
                    prescription = ExercisePrescription(
                        exerciseType = ExerciseType.ASSISTED_BODYWEIGHT,
                        targetSets = 1,
                        repRange = RepRange(6, 10),
                        targetAssistanceWeight = 18.0,
                        restSeconds = 90
                    ),
                    sets = listOf(
                        WorkoutSet(
                            id = "session-2-set-1",
                            workoutExerciseId = assisted,
                            setNumber = 1,
                            exerciseType = ExerciseType.ASSISTED_BODYWEIGHT,
                            targetReps = 10,
                            completedReps = 9,
                            targetAssistanceWeight = 18.0,
                            completedAssistanceWeight = 16.0,
                            isCompleted = true,
                            rir = 0,
                            feltManageable = false,
                            completedAtTimestamp = 1_700_300_600_000L,
                            type = SetType.NORMAL
                        )
                    )
                ),
                WorkoutExercise(
                    id = hold,
                    sessionId = "session-2",
                    exerciseId = "plank",
                    orderIndex = 1,
                    prescription = ExercisePrescription(
                        exerciseType = ExerciseType.DURATION,
                        targetSets = 1,
                        targetDurationSeconds = 60,
                        restSeconds = 60
                    ),
                    sets = listOf(
                        // Never resolved: no completion, no stop reason, no feedback.
                        WorkoutSet(
                            id = "session-2-set-2",
                            workoutExerciseId = hold,
                            setNumber = 1,
                            exerciseType = ExerciseType.DURATION,
                            targetDurationSeconds = 60,
                            isCompleted = false,
                            type = SetType.NORMAL
                        )
                    )
                ),
                WorkoutExercise(
                    id = row,
                    sessionId = "session-2",
                    exerciseId = "row-erg",
                    orderIndex = 2,
                    prescription = ExercisePrescription(
                        exerciseType = ExerciseType.DISTANCE_DURATION,
                        targetSets = 1,
                        targetDurationSeconds = 480,
                        targetDistanceMeters = 1_500.0,
                        restSeconds = 0
                    ),
                    sets = listOf(
                        WorkoutSet(
                            id = "session-2-set-3",
                            workoutExerciseId = row,
                            setNumber = 1,
                            exerciseType = ExerciseType.DISTANCE_DURATION,
                            targetDurationSeconds = 480,
                            completedDurationSeconds = 470,
                            targetDistanceMeters = 1_500.0,
                            completedDistanceMeters = 1_512.5,
                            isCompleted = true,
                            completedAtTimestamp = 1_700_302_000_000L,
                            type = SetType.NORMAL
                        )
                    )
                )
            )
        )
    }

    private fun cancelledSession(): WorkoutSession {
        val exerciseId = "session-3-exercise-1"
        return WorkoutSession(
            id = "session-3",
            name = "Abandoned",
            startedAtTimestamp = 1_700_400_000_000L,
            completedAtTimestamp = null,
            targetDurationMinutes = 45,
            actualDurationMinutes = 0,
            weightUnit = WeightUnit.LBS,
            status = SessionStatus.CANCELLED,
            origin = WorkoutOrigin.PLANNER,
            focusMuscles = listOf("legs"),
            notes = "",
            exercises = listOf(
                WorkoutExercise(
                    id = exerciseId,
                    sessionId = "session-3",
                    exerciseId = "goblet-squat",
                    orderIndex = 0,
                    prescription = ExercisePrescription(
                        exerciseType = ExerciseType.WEIGHT_REPS,
                        targetSets = 1,
                        repRange = RepRange(8, 12),
                        restSeconds = 90
                    ),
                    sets = listOf(
                        WorkoutSet(
                            id = "session-3-set-1",
                            workoutExerciseId = exerciseId,
                            setNumber = 1,
                            exerciseType = ExerciseType.WEIGHT_REPS,
                            targetReps = 12,
                            isCompleted = false,
                            stoppedAtTimestamp = 1_700_400_300_000L,
                            stopReason = SetStopReason.PAIN_STOP,
                            type = SetType.NORMAL
                        )
                    )
                )
            )
        )
    }

    private fun activeSession(): WorkoutSession {
        val exerciseId = "session-4-exercise-1"
        return WorkoutSession(
            id = "session-4",
            name = "In Progress",
            startedAtTimestamp = 1_700_500_000_000L,
            completedAtTimestamp = null,
            targetDurationMinutes = 40,
            actualDurationMinutes = 0,
            weightUnit = WeightUnit.KG,
            status = SessionStatus.IN_PROGRESS,
            origin = WorkoutOrigin.PLANNER,
            focusMuscles = listOf("core"),
            notes = "",
            exercises = listOf(
                WorkoutExercise(
                    id = exerciseId,
                    sessionId = "session-4",
                    exerciseId = "push-up",
                    orderIndex = 0,
                    prescription = ExercisePrescription(
                        exerciseType = ExerciseType.BODYWEIGHT_REPS,
                        targetSets = 2,
                        repRange = RepRange(10, 15),
                        restSeconds = 60
                    ),
                    sets = listOf(
                        WorkoutSet(
                            id = "session-4-set-1",
                            workoutExerciseId = exerciseId,
                            setNumber = 1,
                            exerciseType = ExerciseType.BODYWEIGHT_REPS,
                            targetReps = 15,
                            completedReps = 15,
                            isCompleted = true,
                            completedAtTimestamp = 1_700_500_200_000L,
                            type = SetType.NORMAL
                        ),
                        WorkoutSet(
                            id = "session-4-set-2",
                            workoutExerciseId = exerciseId,
                            setNumber = 2,
                            exerciseType = ExerciseType.BODYWEIGHT_REPS,
                            targetReps = 15,
                            isCompleted = false,
                            type = SetType.NORMAL
                        )
                    )
                )
            )
        )
    }
}
