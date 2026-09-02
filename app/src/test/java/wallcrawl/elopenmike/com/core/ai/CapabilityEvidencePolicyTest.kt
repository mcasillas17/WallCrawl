package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.CapabilityEvidence
import wallcrawl.elopenmike.com.core.model.CapabilityEvidencePolicyVersion
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceReason
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceScope
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceSet
import wallcrawl.elopenmike.com.core.model.ComparableMovementShape
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetStopReason
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.model.WorkoutSession

class CapabilityEvidencePolicyTest {

    private val policy = CapabilityEvidencePolicy()

    @Test
    fun derive_requiresTwoDistinctQualifyingSessions() {
        val sessions = listOf(
            weightRepsSession(
                sessionId = "session-1",
                exerciseId = "bench-press"
            )
        )

        assertThat(policy.derive(sessions = sessions, exercises = exactExerciseCatalog("bench-press" to ExerciseType.WEIGHT_REPS)))
            .isEqualTo(CapabilityEvidenceSet.empty())
    }

    @Test
    fun derive_qualifiesWeightRepsSessionsAsWeightRepetitions() {
        val exerciseId = "bench-press"
        val sessions = listOf(
            weightRepsSession(
                sessionId = "session-b",
                exerciseId = exerciseId,
                sessionCompletedAt = 20_000L,
                prescriptionTargetWeight = 55.0,
                targetReps = 6,
                completedReps = 6,
                completedWeight = 60.0
            ),
            weightRepsSession(
                sessionId = "session-a",
                exerciseId = exerciseId,
                sessionCompletedAt = 10_000L,
                prescriptionTargetWeight = 45.0,
                targetReps = 8,
                completedReps = 8,
                completedWeight = 45.0
            )
        )

        assertThat(policy.derive(sessions = sessions, exercises = exactExerciseCatalog(exerciseId to ExerciseType.WEIGHT_REPS)))
            .isEqualTo(expectedEvidence(exerciseId, ComparableMovementShape.WEIGHT_REPETITIONS, listOf("session-a", "session-b")))
    }

    @Test
    fun derive_rejectsSessionsThatAreNotCompleted() {
        listOf(SessionStatus.CANCELLED, SessionStatus.IN_PROGRESS).forEach { status ->
            val exerciseId = "bench-press"
            val sessions = listOf(
                weightRepsSession(
                    sessionId = "valid-$status",
                    exerciseId = exerciseId,
                    sessionCompletedAt = 20_000L
                ),
                weightRepsSession(
                    sessionId = "invalid-$status",
                    exerciseId = exerciseId,
                    status = status,
                    sessionCompletedAt = 10_000L
                )
            )

            assertWithMessage("status=%s should not qualify", status).that(
                policy.derive(sessions = sessions, exercises = exactExerciseCatalog(exerciseId to ExerciseType.WEIGHT_REPS))
            ).isEqualTo(CapabilityEvidenceSet.empty())
        }
    }

    @Test
    fun derive_rejectsSessionsWithoutPositiveCompletionTimestamp() {
        listOf<Long?>(null, 0L, -1L).forEach { completedAt ->
            val exerciseId = "bench-press"
            val sessions = listOf(
                weightRepsSession(
                    sessionId = "valid-${completedAt ?: "null"}",
                    exerciseId = exerciseId,
                    sessionCompletedAt = 20_000L
                ),
                weightRepsSession(
                    sessionId = "invalid-${completedAt ?: "null"}",
                    exerciseId = exerciseId,
                    sessionCompletedAt = completedAt
                )
            )

            assertWithMessage("session completedAt=%s should not qualify", completedAt).that(
                policy.derive(sessions = sessions, exercises = exactExerciseCatalog(exerciseId to ExerciseType.WEIGHT_REPS))
            ).isEqualTo(CapabilityEvidenceSet.empty())
        }
    }

    @Test
    fun derive_rejectsBlankSessionAndExerciseIds() {
        val blankSessionIdSessions = listOf(
            weightRepsSession(
                sessionId = "session-valid",
                exerciseId = "bench-press",
                sessionCompletedAt = 20_000L
            ),
            weightRepsSession(
                sessionId = "",
                exerciseId = "bench-press",
                sessionCompletedAt = 10_000L
            )
        )
        val blankExerciseIdSessions = listOf(
            weightRepsSession(
                sessionId = "session-valid",
                exerciseId = "bench-press",
                sessionCompletedAt = 20_000L
            ),
            weightRepsSession(
                sessionId = "session-blank-exercise",
                exerciseId = "",
                sessionCompletedAt = 10_000L
            )
        )

        assertThat(policy.derive(blankSessionIdSessions, exercises = exactExerciseCatalog("bench-press" to ExerciseType.WEIGHT_REPS)))
            .isEqualTo(CapabilityEvidenceSet.empty())
        assertThat(policy.derive(blankExerciseIdSessions, exercises = exactExerciseCatalog("bench-press" to ExerciseType.WEIGHT_REPS)))
            .isEqualTo(CapabilityEvidenceSet.empty())
    }

    @Test
    fun derive_rejectsObservationWhenAnyNonWarmupSetIsIncompleteOrStoppedOrUnconfirmed() {
        val cases = listOf(
            "incomplete work set" to validWeightRepsWorkSet().copy(
                isCompleted = false,
                completedAtTimestamp = null,
                feltManageable = null
            ),
            "user skipped stop reason" to validWeightRepsWorkSet().copy(
                stopReason = SetStopReason.USER_SKIPPED,
                stoppedAtTimestamp = 9_001L
            ),
            "pain stop reason" to validWeightRepsWorkSet().copy(
                stopReason = SetStopReason.PAIN_STOP,
                stoppedAtTimestamp = 9_001L
            ),
            "other stop reason" to validWeightRepsWorkSet().copy(
                stopReason = SetStopReason.TIME_CONSTRAINT,
                stoppedAtTimestamp = 9_001L
            ),
            "stopped timestamp without stop reason" to validWeightRepsWorkSet().copy(
                stoppedAtTimestamp = 9_001L
            ),
            "felt manageable null" to validWeightRepsWorkSet().copy(feltManageable = null),
            "felt manageable false" to validWeightRepsWorkSet().copy(feltManageable = false),
            "completion timestamp null" to validWeightRepsWorkSet().copy(completedAtTimestamp = null),
            "completion timestamp zero" to validWeightRepsWorkSet().copy(completedAtTimestamp = 0L),
            "completion timestamp negative" to validWeightRepsWorkSet().copy(completedAtTimestamp = -1L),
            "mismatched set exercise type" to validWeightRepsWorkSet().copy(exerciseType = ExerciseType.BODYWEIGHT_REPS)
        )

        cases.forEach { (label, invalidSet) ->
            val exerciseId = "bench-press-${label.replace(' ', '-') }"
            val sessions = listOf(
                weightRepsSession(
                    sessionId = "session-valid-$label",
                    exerciseId = exerciseId,
                    sessionCompletedAt = 20_000L
                ),
                weightRepsSession(
                    sessionId = "session-invalid-$label",
                    exerciseId = exerciseId,
                    sessionCompletedAt = 10_000L,
                    workSets = listOf(invalidSet)
                )
            )

            assertWithMessage(label).that(
                policy.derive(sessions = sessions, exercises = exactExerciseCatalog(exerciseId to ExerciseType.WEIGHT_REPS))
            ).isEqualTo(CapabilityEvidenceSet.empty())
        }
    }

    @Test
    fun derive_rejectsObservationWhenOneValidAndOneInvalidWorkSetCoexist() {
        val exerciseId = "bench-press"
        val sessions = listOf(
            weightRepsSession(
                sessionId = "session-valid",
                exerciseId = exerciseId,
                sessionCompletedAt = 20_000L
            ),
            weightRepsSession(
                sessionId = "session-mixed",
                exerciseId = exerciseId,
                sessionCompletedAt = 10_000L,
                workSets = listOf(
                    validWeightRepsWorkSet(),
                    validWeightRepsWorkSet().copy(feltManageable = false)
                )
            )
        )

        assertThat(policy.derive(sessions = sessions, exercises = exactExerciseCatalog(exerciseId to ExerciseType.WEIGHT_REPS)))
            .isEqualTo(CapabilityEvidenceSet.empty())
    }

    @Test
    fun derive_warmupOnlySetsNeverCount() {
        val exerciseId = "bench-press"
        val sessions = listOf(
            weightRepsSession(
                sessionId = "session-a",
                exerciseId = exerciseId,
                sessionCompletedAt = 20_000L,
                workSets = emptyList(),
                warmupSets = listOf(ignoredWarmupSet())
            ),
            weightRepsSession(
                sessionId = "session-b",
                exerciseId = exerciseId,
                sessionCompletedAt = 10_000L,
                workSets = emptyList(),
                warmupSets = listOf(ignoredWarmupSet())
            )
        )

        assertThat(policy.derive(sessions = sessions, exercises = exactExerciseCatalog(exerciseId to ExerciseType.WEIGHT_REPS)))
            .isEqualTo(CapabilityEvidenceSet.empty())
    }

    @Test
    fun derive_allowsWarmupsWhenWorkSetsFullyQualify() {
        val exerciseId = "bench-press"
        val sessions = listOf(
            weightRepsSession(
                sessionId = "session-b",
                exerciseId = exerciseId,
                sessionCompletedAt = 20_000L,
                workSets = listOf(validWeightRepsWorkSet(targetWeight = 55.0, completedWeight = 55.0)),
                warmupSets = listOf(ignoredWarmupSet(exerciseType = ExerciseType.DURATION))
            ),
            weightRepsSession(
                sessionId = "session-a",
                exerciseId = exerciseId,
                sessionCompletedAt = 10_000L,
                workSets = listOf(validWeightRepsWorkSet(targetWeight = 45.0, completedWeight = 45.0)),
                warmupSets = listOf(ignoredWarmupSet(exerciseType = ExerciseType.BODYWEIGHT_REPS))
            )
        )

        assertThat(policy.derive(sessions = sessions, exercises = exactExerciseCatalog(exerciseId to ExerciseType.WEIGHT_REPS)))
            .isEqualTo(expectedEvidence(exerciseId, ComparableMovementShape.WEIGHT_REPETITIONS, listOf("session-a", "session-b")))
    }

    @Test
    fun derive_qualifiesBodyweightSessionsAsBodyweightRepetitions() {
        val exerciseId = "push-up"
        val sessions = listOf(
            bodyweightRepsSession(
                sessionId = "session-b",
                exerciseId = exerciseId,
                sessionCompletedAt = 20_000L,
                targetReps = 15,
                completedReps = 15
            ),
            bodyweightRepsSession(
                sessionId = "session-a",
                exerciseId = exerciseId,
                sessionCompletedAt = 10_000L,
                targetReps = 10,
                completedReps = 12
            )
        )

        assertThat(policy.derive(sessions = sessions, exercises = exactExerciseCatalog(exerciseId to ExerciseType.BODYWEIGHT_REPS)))
            .isEqualTo(expectedEvidence(exerciseId, ComparableMovementShape.BODYWEIGHT_REPETITIONS, listOf("session-a", "session-b")))
    }

    @Test
    fun derive_qualifiesAssistedBodyweightSessionsAsAssistedBodyweightRepetitions() {
        val exerciseId = "pull-up"
        val sessions = listOf(
            assistedBodyweightSession(
                sessionId = "session-b",
                exerciseId = exerciseId,
                sessionCompletedAt = 20_000L,
                targetAssistanceWeight = 30.0,
                completedAssistanceWeight = 25.0,
                targetReps = 6,
                completedReps = 6
            ),
            assistedBodyweightSession(
                sessionId = "session-a",
                exerciseId = exerciseId,
                sessionCompletedAt = 10_000L,
                targetAssistanceWeight = 20.0,
                completedAssistanceWeight = 20.0,
                targetReps = 8,
                completedReps = 8
            )
        )

        assertThat(policy.derive(sessions = sessions, exercises = exactExerciseCatalog(exerciseId to ExerciseType.ASSISTED_BODYWEIGHT)))
            .isEqualTo(expectedEvidence(exerciseId, ComparableMovementShape.ASSISTED_BODYWEIGHT_REPETITIONS, listOf("session-a", "session-b")))
    }

    @Test
    fun derive_qualifiesDurationSessionsAsTimedDuration() {
        val exerciseId = "plank"
        val sessions = listOf(
            durationSession(
                sessionId = "session-b",
                exerciseId = exerciseId,
                sessionCompletedAt = 20_000L,
                targetDurationSeconds = 90,
                completedDurationSeconds = 100
            ),
            durationSession(
                sessionId = "session-a",
                exerciseId = exerciseId,
                sessionCompletedAt = 10_000L,
                targetDurationSeconds = 60,
                completedDurationSeconds = 60
            )
        )

        assertThat(policy.derive(sessions = sessions, exercises = exactExerciseCatalog(exerciseId to ExerciseType.DURATION)))
            .isEqualTo(expectedEvidence(exerciseId, ComparableMovementShape.TIMED_DURATION, listOf("session-a", "session-b")))
    }

    @Test
    fun derive_qualifiesDistanceOnlySessionsAsDistanceDurationDistanceOnly() {
        val exerciseId = "sled-push"
        val sessions = listOf(
            distanceDurationSession(
                sessionId = "session-b",
                exerciseId = exerciseId,
                sessionCompletedAt = 20_000L,
                targetDistanceMeters = 60.0,
                completedDistanceMeters = 65.0
            ),
            distanceDurationSession(
                sessionId = "session-a",
                exerciseId = exerciseId,
                sessionCompletedAt = 10_000L,
                targetDistanceMeters = 40.0,
                completedDistanceMeters = 40.0
            )
        )

        assertThat(policy.derive(sessions = sessions, exercises = exactExerciseCatalog(exerciseId to ExerciseType.DISTANCE_DURATION)))
            .isEqualTo(expectedEvidence(exerciseId, ComparableMovementShape.DISTANCE_DURATION_DISTANCE_ONLY, listOf("session-a", "session-b")))
    }

    @Test
    fun derive_qualifiesTimeOnlySessionsAsDistanceDurationTimeOnly() {
        val exerciseId = "rower-interval"
        val sessions = listOf(
            distanceDurationSession(
                sessionId = "session-b",
                exerciseId = exerciseId,
                sessionCompletedAt = 20_000L,
                targetDurationSeconds = 150,
                completedDurationSeconds = 160
            ),
            distanceDurationSession(
                sessionId = "session-a",
                exerciseId = exerciseId,
                sessionCompletedAt = 10_000L,
                targetDurationSeconds = 120,
                completedDurationSeconds = 120
            )
        )

        assertThat(policy.derive(sessions = sessions, exercises = exactExerciseCatalog(exerciseId to ExerciseType.DISTANCE_DURATION)))
            .isEqualTo(expectedEvidence(exerciseId, ComparableMovementShape.DISTANCE_DURATION_TIME_ONLY, listOf("session-a", "session-b")))
    }

    @Test
    fun derive_qualifiesDistanceAndTimeSessionsAsDistanceDurationDistanceAndTime() {
        val exerciseId = "air-bike"
        val sessions = listOf(
            distanceDurationSession(
                sessionId = "session-b",
                exerciseId = exerciseId,
                sessionCompletedAt = 20_000L,
                targetDistanceMeters = 800.0,
                completedDistanceMeters = 820.0,
                targetDurationSeconds = 120,
                completedDurationSeconds = 118
            ),
            distanceDurationSession(
                sessionId = "session-a",
                exerciseId = exerciseId,
                sessionCompletedAt = 10_000L,
                targetDistanceMeters = 500.0,
                completedDistanceMeters = 500.0,
                targetDurationSeconds = 90,
                completedDurationSeconds = 90
            )
        )

        assertThat(policy.derive(sessions = sessions, exercises = exactExerciseCatalog(exerciseId to ExerciseType.DISTANCE_DURATION)))
            .isEqualTo(expectedEvidence(exerciseId, ComparableMovementShape.DISTANCE_DURATION_DISTANCE_AND_TIME, listOf("session-a", "session-b")))
    }

    @Test
    fun derive_doesNotCombineAssistedAndBodyweightObservations() {
        val exerciseId = "pull-up"
        val sessions = listOf(
            bodyweightRepsSession(
                sessionId = "session-a",
                exerciseId = exerciseId,
                sessionCompletedAt = 10_000L
            ),
            assistedBodyweightSession(
                sessionId = "session-b",
                exerciseId = exerciseId,
                sessionCompletedAt = 20_000L
            )
        )

        assertThat(policy.derive(sessions = sessions, exercises = exactExerciseCatalog(exerciseId to ExerciseType.BODYWEIGHT_REPS)))
            .isEqualTo(CapabilityEvidenceSet.empty())
    }

    @Test
    fun derive_doesNotCombineDurationAndTimeOnlyDistanceDurationObservations() {
        val exerciseId = "carry"
        val sessions = listOf(
            durationSession(
                sessionId = "session-a",
                exerciseId = exerciseId,
                sessionCompletedAt = 10_000L
            ),
            distanceDurationSession(
                sessionId = "session-b",
                exerciseId = exerciseId,
                sessionCompletedAt = 20_000L,
                targetDurationSeconds = 120,
                completedDurationSeconds = 120
            )
        )

        assertThat(policy.derive(sessions = sessions, exercises = exactExerciseCatalog(exerciseId to ExerciseType.DURATION)))
            .isEqualTo(CapabilityEvidenceSet.empty())
    }

    @Test
    fun derive_doesNotCombineDistanceOnlyAndTimeOnlyObservations() {
        val exerciseId = "air-bike"
        val sessions = listOf(
            distanceDurationSession(
                sessionId = "session-a",
                exerciseId = exerciseId,
                sessionCompletedAt = 10_000L,
                targetDistanceMeters = 500.0,
                completedDistanceMeters = 500.0
            ),
            distanceDurationSession(
                sessionId = "session-b",
                exerciseId = exerciseId,
                sessionCompletedAt = 20_000L,
                targetDurationSeconds = 120,
                completedDurationSeconds = 120
            )
        )

        assertThat(policy.derive(sessions = sessions, exercises = exactExerciseCatalog(exerciseId to ExerciseType.DISTANCE_DURATION)))
            .isEqualTo(CapabilityEvidenceSet.empty())
    }

    @Test
    fun derive_choosesStableComparableShapeWhenExactExerciseHasMultipleQualifyingShapeGroups() {
        val exerciseId = "pull-up"
        val sessions = listOf(
            bodyweightRepsSession(
                sessionId = "session-a",
                exerciseId = exerciseId,
                sessionCompletedAt = 10_000L
            ),
            bodyweightRepsSession(
                sessionId = "session-b",
                exerciseId = exerciseId,
                sessionCompletedAt = 20_000L
            ),
            assistedBodyweightSession(
                sessionId = "session-c",
                exerciseId = exerciseId,
                sessionCompletedAt = 30_000L
            ),
            assistedBodyweightSession(
                sessionId = "session-d",
                exerciseId = exerciseId,
                sessionCompletedAt = 40_000L
            )
        )

        assertThat(policy.derive(sessions = sessions, exercises = exactExerciseCatalog(exerciseId to ExerciseType.BODYWEIGHT_REPS)))
            .isEqualTo(expectedEvidence(exerciseId, ComparableMovementShape.BODYWEIGHT_REPETITIONS, listOf("session-a", "session-b")))
    }

    @Test
    fun derive_rejectsOutOfRangeLoggedValues() {
        val cases = listOf(
            "weight reps target weight above target cap" to listOf(
                weightRepsSession(sessionId = "session-valid-weight-target-cap", exerciseId = "weight-target-cap", sessionCompletedAt = 20_000L),
                weightRepsSession(
                    sessionId = "session-invalid-weight-target-cap",
                    exerciseId = "weight-target-cap",
                    sessionCompletedAt = 10_000L,
                    workSets = listOf(validWeightRepsWorkSet(targetWeight = 10_000.1))
                )
            ),
            "weight reps completed reps above cap" to listOf(
                weightRepsSession(sessionId = "session-valid-weight-cap", exerciseId = "weight-cap", sessionCompletedAt = 20_000L),
                weightRepsSession(
                    sessionId = "session-invalid-weight-cap",
                    exerciseId = "weight-cap",
                    sessionCompletedAt = 10_000L,
                    workSets = listOf(validWeightRepsWorkSet(completedReps = 1_001))
                )
            ),
            "bodyweight completed reps above cap" to listOf(
                bodyweightRepsSession(sessionId = "session-valid-bodyweight-cap", exerciseId = "bodyweight-cap", sessionCompletedAt = 20_000L),
                bodyweightRepsSession(
                    sessionId = "session-invalid-bodyweight-cap",
                    exerciseId = "bodyweight-cap",
                    sessionCompletedAt = 10_000L,
                    workSets = listOf(validBodyweightWorkSet(completedReps = 1_001))
                )
            ),
            "assisted bodyweight completed assistance above cap" to listOf(
                assistedBodyweightSession(sessionId = "session-valid-assisted-cap", exerciseId = "assisted-cap", sessionCompletedAt = 20_000L),
                assistedBodyweightSession(
                    sessionId = "session-invalid-assisted-cap",
                    exerciseId = "assisted-cap",
                    sessionCompletedAt = 10_000L,
                    workSets = listOf(validAssistedBodyweightWorkSet(completedAssistanceWeight = 100_000.1))
                )
            ),
            "assisted bodyweight target assistance above target cap" to listOf(
                assistedBodyweightSession(sessionId = "session-valid-assisted-target-cap", exerciseId = "assisted-target-cap", sessionCompletedAt = 20_000L),
                assistedBodyweightSession(
                    sessionId = "session-invalid-assisted-target-cap",
                    exerciseId = "assisted-target-cap",
                    sessionCompletedAt = 10_000L,
                    workSets = listOf(validAssistedBodyweightWorkSet(targetAssistanceWeight = 10_000.1))
                )
            ),
            "duration completed seconds above cap" to listOf(
                durationSession(sessionId = "session-valid-duration-cap", exerciseId = "duration-cap", sessionCompletedAt = 20_000L),
                durationSession(
                    sessionId = "session-invalid-duration-cap",
                    exerciseId = "duration-cap",
                    sessionCompletedAt = 10_000L,
                    workSets = listOf(validDurationWorkSet(completedDurationSeconds = 86_401))
                )
            ),
            "distance completed meters above cap" to listOf(
                distanceDurationSession(
                    sessionId = "session-valid-distance-cap",
                    exerciseId = "distance-cap",
                    sessionCompletedAt = 20_000L,
                    targetDistanceMeters = 100.0,
                    completedDistanceMeters = 100.0
                ),
                distanceDurationSession(
                    sessionId = "session-invalid-distance-cap",
                    exerciseId = "distance-cap",
                    sessionCompletedAt = 10_000L,
                    targetDistanceMeters = 100.0,
                    completedDistanceMeters = 100.0,
                    workSets = listOf(
                        validDistanceDurationWorkSet(
                            targetDistanceMeters = 100.0,
                            completedDistanceMeters = 1_000_000.1
                        )
                    )
                )
            )
        )

        cases.forEach { (label, sessions) ->
            assertWithMessage(label).that(
                policy.derive(sessions = sessions, exercises = emptyList())
            ).isEqualTo(CapabilityEvidenceSet.empty())
        }
    }

    @Test
    fun derive_rejectsInvalidTypeSpecificValues() {
        val cases = listOf(
            "weight reps requires positive prescription target weight" to listOf(
                weightRepsSession(sessionId = "session-valid-weight-target", exerciseId = "weight-target", sessionCompletedAt = 20_000L),
                weightRepsSession(
                    sessionId = "session-invalid-weight-target",
                    exerciseId = "weight-target",
                    sessionCompletedAt = 10_000L,
                    prescriptionTargetWeight = 0.0
                )
            ),
            "weight reps requires positive finite completed weight" to listOf(
                weightRepsSession(sessionId = "session-valid-weight", exerciseId = "weight-completed", sessionCompletedAt = 20_000L),
                weightRepsSession(
                    sessionId = "session-invalid-weight",
                    exerciseId = "weight-completed",
                    sessionCompletedAt = 10_000L,
                    workSets = listOf(validWeightRepsWorkSet(completedWeight = Double.NaN))
                )
            ),
            "bodyweight requires positive completed reps" to listOf(
                bodyweightRepsSession(sessionId = "session-valid-bodyweight", exerciseId = "bodyweight-reps", sessionCompletedAt = 20_000L),
                bodyweightRepsSession(
                    sessionId = "session-invalid-bodyweight",
                    exerciseId = "bodyweight-reps",
                    sessionCompletedAt = 10_000L,
                    workSets = listOf(validBodyweightWorkSet(completedReps = 0))
                )
            ),
            "assisted bodyweight requires nonnegative finite assistance" to listOf(
                assistedBodyweightSession(sessionId = "session-valid-assisted", exerciseId = "assisted-reps", sessionCompletedAt = 20_000L),
                assistedBodyweightSession(
                    sessionId = "session-invalid-assisted",
                    exerciseId = "assisted-reps",
                    sessionCompletedAt = 10_000L,
                    workSets = listOf(validAssistedBodyweightWorkSet(completedAssistanceWeight = Double.NEGATIVE_INFINITY))
                )
            ),
            "duration requires positive completed duration" to listOf(
                durationSession(sessionId = "session-valid-duration", exerciseId = "duration-only", sessionCompletedAt = 20_000L),
                durationSession(
                    sessionId = "session-invalid-duration",
                    exerciseId = "duration-only",
                    sessionCompletedAt = 10_000L,
                    workSets = listOf(validDurationWorkSet(completedDurationSeconds = 0))
                )
            ),
            "distance only requires positive finite completed distance" to listOf(
                distanceDurationSession(
                    sessionId = "session-valid-distance",
                    exerciseId = "distance-only",
                    sessionCompletedAt = 20_000L,
                    targetDistanceMeters = 100.0,
                    completedDistanceMeters = 100.0
                ),
                distanceDurationSession(
                    sessionId = "session-invalid-distance",
                    exerciseId = "distance-only",
                    sessionCompletedAt = 10_000L,
                    targetDistanceMeters = 100.0,
                    completedDistanceMeters = 100.0,
                    workSets = listOf(validDistanceDurationWorkSet(targetDistanceMeters = 100.0, completedDistanceMeters = Double.POSITIVE_INFINITY))
                )
            ),
            "time only requires positive completed duration" to listOf(
                distanceDurationSession(
                    sessionId = "session-valid-time",
                    exerciseId = "time-only",
                    sessionCompletedAt = 20_000L,
                    targetDurationSeconds = 120,
                    completedDurationSeconds = 120
                ),
                distanceDurationSession(
                    sessionId = "session-invalid-time",
                    exerciseId = "time-only",
                    sessionCompletedAt = 10_000L,
                    targetDurationSeconds = 120,
                    completedDurationSeconds = 120,
                    workSets = listOf(validDistanceDurationWorkSet(targetDurationSeconds = 120, completedDurationSeconds = -1))
                )
            ),
            "distance and time requires both completed dimensions" to listOf(
                distanceDurationSession(
                    sessionId = "session-valid-distance-time",
                    exerciseId = "distance-and-time",
                    sessionCompletedAt = 20_000L,
                    targetDistanceMeters = 500.0,
                    completedDistanceMeters = 500.0,
                    targetDurationSeconds = 90,
                    completedDurationSeconds = 90
                ),
                distanceDurationSession(
                    sessionId = "session-invalid-distance-time",
                    exerciseId = "distance-and-time",
                    sessionCompletedAt = 10_000L,
                    targetDistanceMeters = 500.0,
                    completedDistanceMeters = 500.0,
                    targetDurationSeconds = 90,
                    completedDurationSeconds = 90,
                    workSets = listOf(
                        validDistanceDurationWorkSet(
                            targetDistanceMeters = 500.0,
                            completedDistanceMeters = 500.0,
                            targetDurationSeconds = 90,
                            completedDurationSeconds = null
                        )
                    )
                )
            )
        )

        cases.forEach { (label, sessions) ->
            assertWithMessage(label).that(
                policy.derive(sessions = sessions, exercises = emptyList())
            ).isEqualTo(CapabilityEvidenceSet.empty())
        }
    }

    @Test
    fun derive_rejectsPopulatedFieldsThatBelongToAnotherShape() {
        val cases = listOf(
            "weight reps with duration field" to listOf(
                weightRepsSession(sessionId = "session-valid-weight", exerciseId = "weight-extra-field", sessionCompletedAt = 20_000L),
                weightRepsSession(
                    sessionId = "session-invalid-weight",
                    exerciseId = "weight-extra-field",
                    sessionCompletedAt = 10_000L,
                    workSets = listOf(validWeightRepsWorkSet().copy(completedDurationSeconds = 30))
                )
            ),
            "bodyweight with weight field" to listOf(
                bodyweightRepsSession(sessionId = "session-valid-bodyweight", exerciseId = "bodyweight-extra-field", sessionCompletedAt = 20_000L),
                bodyweightRepsSession(
                    sessionId = "session-invalid-bodyweight",
                    exerciseId = "bodyweight-extra-field",
                    sessionCompletedAt = 10_000L,
                    workSets = listOf(validBodyweightWorkSet().copy(completedWeight = 10.0))
                )
            ),
            "assisted bodyweight with external load field" to listOf(
                assistedBodyweightSession(sessionId = "session-valid-assisted", exerciseId = "assisted-extra-field", sessionCompletedAt = 20_000L),
                assistedBodyweightSession(
                    sessionId = "session-invalid-assisted",
                    exerciseId = "assisted-extra-field",
                    sessionCompletedAt = 10_000L,
                    workSets = listOf(validAssistedBodyweightWorkSet().copy(completedWeight = 10.0))
                )
            ),
            "duration with distance field" to listOf(
                durationSession(sessionId = "session-valid-duration", exerciseId = "duration-extra-field", sessionCompletedAt = 20_000L),
                durationSession(
                    sessionId = "session-invalid-duration",
                    exerciseId = "duration-extra-field",
                    sessionCompletedAt = 10_000L,
                    workSets = listOf(validDurationWorkSet().copy(completedDistanceMeters = 100.0))
                )
            ),
            "distance only with completed time" to listOf(
                distanceDurationSession(
                    sessionId = "session-valid-distance-only",
                    exerciseId = "distance-only-extra-field",
                    sessionCompletedAt = 20_000L,
                    targetDistanceMeters = 100.0,
                    completedDistanceMeters = 100.0
                ),
                distanceDurationSession(
                    sessionId = "session-invalid-distance-only",
                    exerciseId = "distance-only-extra-field",
                    sessionCompletedAt = 10_000L,
                    targetDistanceMeters = 100.0,
                    completedDistanceMeters = 100.0,
                    workSets = listOf(
                        validDistanceDurationWorkSet(
                            targetDistanceMeters = 100.0,
                            completedDistanceMeters = 100.0,
                            completedDurationSeconds = 60
                        )
                    )
                )
            ),
            "time only with completed distance" to listOf(
                distanceDurationSession(
                    sessionId = "session-valid-time-only",
                    exerciseId = "time-only-extra-field",
                    sessionCompletedAt = 20_000L,
                    targetDurationSeconds = 120,
                    completedDurationSeconds = 120
                ),
                distanceDurationSession(
                    sessionId = "session-invalid-time-only",
                    exerciseId = "time-only-extra-field",
                    sessionCompletedAt = 10_000L,
                    targetDurationSeconds = 120,
                    completedDurationSeconds = 120,
                    workSets = listOf(
                        validDistanceDurationWorkSet(
                            targetDurationSeconds = 120,
                            completedDurationSeconds = 120,
                            completedDistanceMeters = 100.0
                        )
                    )
                )
            ),
            "distance and time with load field" to listOf(
                distanceDurationSession(
                    sessionId = "session-valid-distance-time",
                    exerciseId = "distance-time-extra-field",
                    sessionCompletedAt = 20_000L,
                    targetDistanceMeters = 500.0,
                    completedDistanceMeters = 500.0,
                    targetDurationSeconds = 90,
                    completedDurationSeconds = 90
                ),
                distanceDurationSession(
                    sessionId = "session-invalid-distance-time",
                    exerciseId = "distance-time-extra-field",
                    sessionCompletedAt = 10_000L,
                    targetDistanceMeters = 500.0,
                    completedDistanceMeters = 500.0,
                    targetDurationSeconds = 90,
                    completedDurationSeconds = 90,
                    workSets = listOf(
                        validDistanceDurationWorkSet(
                            targetDistanceMeters = 500.0,
                            completedDistanceMeters = 500.0,
                            targetDurationSeconds = 90,
                            completedDurationSeconds = 90,
                            completedWeight = 10.0
                        )
                    )
                )
            )
        )

        cases.forEach { (label, sessions) ->
            assertWithMessage(label).that(
                policy.derive(sessions = sessions, exercises = emptyList())
            ).isEqualTo(CapabilityEvidenceSet.empty())
        }
    }

    @Test
    fun derive_ignoresDuplicateExerciseObservationsWithinOneSession() {
        val sessions = listOf(
            workoutSessionWithDuplicateObservations(
                sessionId = "session-1",
                exerciseId = "bench-press",
                sessionCompletedAt = 10_000L
            )
        )

        assertThat(policy.derive(sessions = sessions, exercises = exactExerciseCatalog("bench-press" to ExerciseType.WEIGHT_REPS)))
            .isEqualTo(CapabilityEvidenceSet.empty())
    }

    private fun expectedEvidence(
        exerciseId: String,
        comparableShape: ComparableMovementShape,
        sessionIds: List<String>
    ): CapabilityEvidenceSet =
        CapabilityEvidenceSet.from(
            mapOf(
                exerciseId to CapabilityEvidence(
                    policyVersion = CapabilityEvidencePolicyVersion.TWO_COMPARABLE_MANAGEABLE_SESSIONS_V1,
                    reason = CapabilityEvidenceReason.TWO_COMPARABLE_MANAGEABLE_COMPLETED_SESSIONS,
                    appliesToExerciseId = exerciseId,
                    demonstratedExerciseId = exerciseId,
                    scope = CapabilityEvidenceScope.EXACT_EXERCISE,
                    comparableShape = comparableShape,
                    qualifyingSessionIds = sessionIds
                )
            )
        )

    private fun exactExerciseCatalog(vararg definitions: Pair<String, ExerciseType>): List<Exercise> =
        definitions.map { (exerciseId, exerciseType) ->
            Exercise(
                id = exerciseId,
                name = exerciseId,
                primaryMuscles = listOf("Primary"),
                type = exerciseType
            )
        }

    private fun weightRepsSession(
        sessionId: String,
        exerciseId: String,
        sessionCompletedAt: Long? = 10_000L,
        status: SessionStatus = SessionStatus.COMPLETED,
        prescriptionTargetWeight: Double? = 45.0,
        targetReps: Int = 8,
        completedReps: Int = 8,
        completedWeight: Double = prescriptionTargetWeight ?: 45.0,
        workSets: List<WorkoutSet> = listOf(
            validWeightRepsWorkSet(
                targetReps = targetReps,
                completedReps = completedReps,
                targetWeight = prescriptionTargetWeight ?: 45.0,
                completedWeight = completedWeight
            )
        ),
        warmupSets: List<WorkoutSet> = emptyList()
    ): WorkoutSession =
        workoutSession(
            sessionId = sessionId,
            exerciseId = exerciseId,
            sessionCompletedAt = sessionCompletedAt,
            status = status,
            prescription = ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = (workSets + warmupSets).size.coerceAtLeast(1),
                repRange = RepRange(targetReps, targetReps),
                targetWeight = prescriptionTargetWeight
            ),
            sets = warmupSets + workSets
        )

    private fun bodyweightRepsSession(
        sessionId: String,
        exerciseId: String,
        sessionCompletedAt: Long? = 10_000L,
        status: SessionStatus = SessionStatus.COMPLETED,
        targetReps: Int = 10,
        completedReps: Int = 10,
        workSets: List<WorkoutSet> = listOf(
            validBodyweightWorkSet(
                targetReps = targetReps,
                completedReps = completedReps
            )
        ),
        warmupSets: List<WorkoutSet> = emptyList()
    ): WorkoutSession =
        workoutSession(
            sessionId = sessionId,
            exerciseId = exerciseId,
            sessionCompletedAt = sessionCompletedAt,
            status = status,
            prescription = ExercisePrescription(
                exerciseType = ExerciseType.BODYWEIGHT_REPS,
                targetSets = (workSets + warmupSets).size.coerceAtLeast(1),
                repRange = RepRange(targetReps, targetReps)
            ),
            sets = warmupSets + workSets
        )

    private fun assistedBodyweightSession(
        sessionId: String,
        exerciseId: String,
        sessionCompletedAt: Long? = 10_000L,
        status: SessionStatus = SessionStatus.COMPLETED,
        targetAssistanceWeight: Double = 20.0,
        completedAssistanceWeight: Double = targetAssistanceWeight,
        targetReps: Int = 8,
        completedReps: Int = 8,
        workSets: List<WorkoutSet> = listOf(
            validAssistedBodyweightWorkSet(
                targetAssistanceWeight = targetAssistanceWeight,
                completedAssistanceWeight = completedAssistanceWeight,
                targetReps = targetReps,
                completedReps = completedReps
            )
        ),
        warmupSets: List<WorkoutSet> = emptyList()
    ): WorkoutSession =
        workoutSession(
            sessionId = sessionId,
            exerciseId = exerciseId,
            sessionCompletedAt = sessionCompletedAt,
            status = status,
            prescription = ExercisePrescription(
                exerciseType = ExerciseType.ASSISTED_BODYWEIGHT,
                targetSets = (workSets + warmupSets).size.coerceAtLeast(1),
                repRange = RepRange(targetReps, targetReps),
                targetAssistanceWeight = targetAssistanceWeight
            ),
            sets = warmupSets + workSets
        )

    private fun durationSession(
        sessionId: String,
        exerciseId: String,
        sessionCompletedAt: Long? = 10_000L,
        status: SessionStatus = SessionStatus.COMPLETED,
        targetDurationSeconds: Int = 60,
        completedDurationSeconds: Int = targetDurationSeconds,
        workSets: List<WorkoutSet> = listOf(
            validDurationWorkSet(
                targetDurationSeconds = targetDurationSeconds,
                completedDurationSeconds = completedDurationSeconds
            )
        ),
        warmupSets: List<WorkoutSet> = emptyList()
    ): WorkoutSession =
        workoutSession(
            sessionId = sessionId,
            exerciseId = exerciseId,
            sessionCompletedAt = sessionCompletedAt,
            status = status,
            prescription = ExercisePrescription(
                exerciseType = ExerciseType.DURATION,
                targetSets = (workSets + warmupSets).size.coerceAtLeast(1),
                targetDurationSeconds = targetDurationSeconds
            ),
            sets = warmupSets + workSets
        )

    private fun distanceDurationSession(
        sessionId: String,
        exerciseId: String,
        sessionCompletedAt: Long? = 10_000L,
        status: SessionStatus = SessionStatus.COMPLETED,
        targetDistanceMeters: Double? = null,
        completedDistanceMeters: Double? = targetDistanceMeters,
        targetDurationSeconds: Int? = null,
        completedDurationSeconds: Int? = targetDurationSeconds,
        workSets: List<WorkoutSet> = listOf(
            validDistanceDurationWorkSet(
                targetDistanceMeters = targetDistanceMeters,
                completedDistanceMeters = completedDistanceMeters,
                targetDurationSeconds = targetDurationSeconds,
                completedDurationSeconds = completedDurationSeconds
            )
        ),
        warmupSets: List<WorkoutSet> = emptyList()
    ): WorkoutSession =
        workoutSession(
            sessionId = sessionId,
            exerciseId = exerciseId,
            sessionCompletedAt = sessionCompletedAt,
            status = status,
            prescription = ExercisePrescription(
                exerciseType = ExerciseType.DISTANCE_DURATION,
                targetSets = (workSets + warmupSets).size.coerceAtLeast(1),
                targetDurationSeconds = targetDurationSeconds,
                targetDistanceMeters = targetDistanceMeters
            ),
            sets = warmupSets + workSets
        )

    private fun workoutSession(
        sessionId: String,
        exerciseId: String,
        sessionCompletedAt: Long?,
        status: SessionStatus,
        prescription: ExercisePrescription,
        sets: List<WorkoutSet>
    ): WorkoutSession {
        val workoutExerciseId = workoutExerciseId(sessionId, exerciseId)
        return WorkoutSession(
            id = sessionId,
            name = "Session ${sessionId.ifBlank { "blank" }}",
            completedAtTimestamp = sessionCompletedAt,
            status = status,
            exercises = listOf(
                WorkoutExercise(
                    id = workoutExerciseId,
                    sessionId = sessionId,
                    exerciseId = exerciseId,
                    orderIndex = 0,
                    prescription = prescription,
                    sets = attachToExercise(workoutExerciseId, sets)
                )
            )
        )
    }

    private fun validWeightRepsWorkSet(
        targetReps: Int = 8,
        completedReps: Int = targetReps,
        targetWeight: Double = 45.0,
        completedWeight: Double = targetWeight,
        feltManageable: Boolean? = true,
        completedAtTimestamp: Long? = 9_000L
    ): WorkoutSet =
        WorkoutSet(
            workoutExerciseId = "pending",
            setNumber = 1,
            exerciseType = ExerciseType.WEIGHT_REPS,
            targetReps = targetReps,
            completedReps = completedReps,
            targetWeight = targetWeight,
            completedWeight = completedWeight,
            isCompleted = true,
            rpe = 8.5f,
            rir = 1,
            feltManageable = feltManageable,
            completedAtTimestamp = completedAtTimestamp,
            type = SetType.NORMAL
        )

    private fun validBodyweightWorkSet(
        targetReps: Int = 10,
        completedReps: Int = targetReps,
        feltManageable: Boolean? = true,
        completedAtTimestamp: Long? = 9_000L
    ): WorkoutSet =
        WorkoutSet(
            workoutExerciseId = "pending",
            setNumber = 1,
            exerciseType = ExerciseType.BODYWEIGHT_REPS,
            targetReps = targetReps,
            completedReps = completedReps,
            isCompleted = true,
            rpe = 7.0f,
            rir = 2,
            feltManageable = feltManageable,
            completedAtTimestamp = completedAtTimestamp,
            type = SetType.NORMAL
        )

    private fun validAssistedBodyweightWorkSet(
        targetAssistanceWeight: Double = 20.0,
        completedAssistanceWeight: Double = targetAssistanceWeight,
        targetReps: Int = 8,
        completedReps: Int = targetReps,
        feltManageable: Boolean? = true,
        completedAtTimestamp: Long? = 9_000L
    ): WorkoutSet =
        WorkoutSet(
            workoutExerciseId = "pending",
            setNumber = 1,
            exerciseType = ExerciseType.ASSISTED_BODYWEIGHT,
            targetReps = targetReps,
            completedReps = completedReps,
            targetAssistanceWeight = targetAssistanceWeight,
            completedAssistanceWeight = completedAssistanceWeight,
            isCompleted = true,
            rpe = 8.0f,
            rir = 1,
            feltManageable = feltManageable,
            completedAtTimestamp = completedAtTimestamp,
            type = SetType.NORMAL
        )

    private fun validDurationWorkSet(
        targetDurationSeconds: Int = 60,
        completedDurationSeconds: Int = targetDurationSeconds,
        feltManageable: Boolean? = true,
        completedAtTimestamp: Long? = 9_000L
    ): WorkoutSet =
        WorkoutSet(
            workoutExerciseId = "pending",
            setNumber = 1,
            exerciseType = ExerciseType.DURATION,
            targetDurationSeconds = targetDurationSeconds,
            completedDurationSeconds = completedDurationSeconds,
            isCompleted = true,
            rpe = 6.5f,
            rir = 3,
            feltManageable = feltManageable,
            completedAtTimestamp = completedAtTimestamp,
            type = SetType.NORMAL
        )

    private fun validDistanceDurationWorkSet(
        targetDistanceMeters: Double? = null,
        completedDistanceMeters: Double? = targetDistanceMeters,
        targetDurationSeconds: Int? = null,
        completedDurationSeconds: Int? = targetDurationSeconds,
        feltManageable: Boolean? = true,
        completedAtTimestamp: Long? = 9_000L,
        completedWeight: Double? = null
    ): WorkoutSet =
        WorkoutSet(
            workoutExerciseId = "pending",
            setNumber = 1,
            exerciseType = ExerciseType.DISTANCE_DURATION,
            targetDurationSeconds = targetDurationSeconds,
            completedDurationSeconds = completedDurationSeconds,
            targetDistanceMeters = targetDistanceMeters,
            completedDistanceMeters = completedDistanceMeters,
            completedWeight = completedWeight,
            isCompleted = true,
            rpe = 7.5f,
            rir = 2,
            feltManageable = feltManageable,
            completedAtTimestamp = completedAtTimestamp,
            type = SetType.NORMAL
        )

    private fun ignoredWarmupSet(
        exerciseType: ExerciseType = ExerciseType.WEIGHT_REPS
    ): WorkoutSet =
        WorkoutSet(
            workoutExerciseId = "pending",
            setNumber = 1,
            exerciseType = exerciseType,
            targetReps = 5,
            completedReps = 0,
            targetWeight = 999.0,
            completedWeight = 999.0,
            completedDurationSeconds = 30,
            stoppedAtTimestamp = 123L,
            stopReason = SetStopReason.USER_SKIPPED,
            isCompleted = false,
            feltManageable = false,
            type = SetType.WARMUP
        )

    private fun workoutSessionWithDuplicateObservations(
        sessionId: String,
        exerciseId: String,
        sessionCompletedAt: Long
    ): WorkoutSession {
        val firstExercise = weightRepsExercise(
            sessionId = sessionId,
            exerciseId = exerciseId,
            orderIndex = 0,
            sets = listOf(validWeightRepsWorkSet())
        )
        val duplicateExercise = weightRepsExercise(
            sessionId = sessionId,
            exerciseId = exerciseId,
            orderIndex = 1,
            sets = listOf(validWeightRepsWorkSet())
        )
        return WorkoutSession(
            id = sessionId,
            name = "Session $sessionId",
            completedAtTimestamp = sessionCompletedAt,
            status = SessionStatus.COMPLETED,
            exercises = listOf(firstExercise, duplicateExercise)
        )
    }

    private fun weightRepsExercise(
        sessionId: String,
        exerciseId: String,
        orderIndex: Int,
        sets: List<WorkoutSet>
    ): WorkoutExercise {
        val workoutExerciseId = workoutExerciseId(sessionId, exerciseId, orderIndex)
        return WorkoutExercise(
            id = workoutExerciseId,
            sessionId = sessionId,
            exerciseId = exerciseId,
            orderIndex = orderIndex,
            prescription = ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = sets.size.coerceAtLeast(1),
                repRange = RepRange(8, 8),
                targetWeight = 45.0
            ),
            sets = attachToExercise(workoutExerciseId, sets)
        )
    }

    private fun attachToExercise(workoutExerciseId: String, sets: List<WorkoutSet>): List<WorkoutSet> =
        sets.mapIndexed { index, set ->
            set.copy(
                id = "$workoutExerciseId-set-${index + 1}",
                workoutExerciseId = workoutExerciseId,
                setNumber = index + 1
            )
        }

    private fun workoutExerciseId(
        sessionId: String,
        exerciseId: String,
        orderIndex: Int = 0
    ): String =
        "${sessionId.ifBlank { "blank-session" }}-${exerciseId.ifBlank { "blank-exercise" }}-$orderIndex"
}
