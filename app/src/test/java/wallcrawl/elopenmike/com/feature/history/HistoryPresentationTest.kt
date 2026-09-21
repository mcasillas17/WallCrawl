package wallcrawl.elopenmike.com.feature.history

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import wallcrawl.elopenmike.com.core.ai.ProgramViolationCode
import wallcrawl.elopenmike.com.core.model.*

class HistoryPresentationTest {
    // Prior/cited eligibility cases moved to WorkoutHistoryRepositoryTest: the shared SQL boundary
    // now supplies validated projections to this presentation layer, including archive/time bounds.

    @Test fun supportedReasonsDecodeWithoutInventingReferenceTargets() {
        val provenance = ProgressionProvenance("squat", ProgressionReason.ADVANCED, ProgressionAxis.LOAD,
            listOf("source-a", "source-b"), "a".repeat(64))
        val result = decodeHistoryReasons(record(ProgressionReasonCode.encode(listOf(provenance))))
        assertThat(result.progression).containsExactly(provenance)
        assertThat(result.outcome).isEqualTo("VALID")
        assertThat(result.unsupportedIdentities).isEmpty()
    }

    @Test fun validatorVersionGatesOutcomeAndViolationMeaningIndependentlyOfProgression() {
        val reason = ProgressionProvenance("squat", ProgressionReason.HOLD_UNKNOWN_LOAD, null, emptyList(), "b".repeat(64))
        val result = decodeHistoryReasons(record(
            listOf("WEEKLY_ALLOWANCE_EXCEEDED") + ProgressionReasonCode.encode(listOf(reason)),
            validator = "WHOLE_PROGRAM_V99"
        ))
        assertThat(result.outcome).isNull()
        assertThat(result.violations).isEmpty()
        assertThat(result.progression).containsExactly(reason)
        assertThat(result.unsupportedIdentities).contains("WHOLE_PROGRAM_V99")
    }

    @Test fun futureReasonAndPolicyVersionsStayOpaque() {
        val result = decodeHistoryReasons(record(listOf("ONE_VARIABLE_PROGRESSION_V99.0", "DELOAD_ONE_WORKOUT_V99.REVISION:1"))
            .copy(ledgerPolicyVersion = "PRIMARY_ONLY_V99", durationEstimatorVersion = "DURATION_ESTIMATOR_V99"))
        assertThat(result.progression).isEmpty()
        assertThat(result.deload).isNull()
        assertThat(result.unsupportedIdentities).containsAtLeast(
            "ONE_VARIABLE_PROGRESSION_V99.0", "DELOAD_ONE_WORKOUT_V99.REVISION:1",
            "PRIMARY_ONLY_V99", "DURATION_ESTIMATOR_V99"
        )
    }

    @Test fun knownMalformedProvenanceIsRejectedRatherThanPresentedAsUnavailable() {
        assertThrows(IllegalArgumentException::class.java) {
            decodeHistoryReasons(record(listOf("ONE_VARIABLE_PROGRESSION_V1.0")))
        }
    }

    @Test fun knownGenerationTokensRejectMalformedAndOverflowingIndices() {
        listOf(
            "PLANNER_GENERATION_V1", "PLANNER_GENERATION_V1:",
            "PLANNER_GENERATION_V1:-1", "PLANNER_GENERATION_V1:+1",
            "PLANNER_GENERATION_V1:01", "PLANNER_GENERATION_V1:1.0",
            "PLANNER_GENERATION_V1:2147483648", "PLANNER_GENERATION_V1:9223372036854775808",
            "PLANNER_GENERATION_V1.NOT_AN_INDEX"
        ).forEach { token ->
            assertThrows(token, IllegalArgumentException::class.java) {
                decodeHistoryReasons(record(listOf(token)))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            decodeHistoryReasons(record(listOf("PLANNER_GENERATION_V1:1", "PLANNER_GENERATION_V1:2")))
        }
    }

    @Test fun generationIntBoundsAreSupportedWhileFutureVersionsStayOpaque() {
        listOf(0, Int.MAX_VALUE).forEach { index ->
            assertThat(decodeHistoryReasons(record(listOf("PLANNER_GENERATION_V1:$index"))).unsupportedIdentities)
                .isEmpty()
        }
        val future = "PLANNER_GENERATION_V2:future-format"
        assertThat(decodeHistoryReasons(record(listOf(future))).unsupportedIdentities).contains(future)
    }

    @Test fun supportedViolationAndAcceptedDeloadRemainRecordedFacts() {
        val result = decodeHistoryReasons(record(listOf("WEEKLY_ALLOWANCE_EXCEEDED") +
            DeloadReasonCode.encode(DeloadRecommendationProvenance(2, "offer", DeloadSource.RETURNING)))
            .copy(outcome = "REPAIRED"))
        assertThat(result.violations).containsExactly(ProgramViolationCode.WEEKLY_ALLOWANCE_EXCEEDED)
        assertThat(result.deload?.source).isEqualTo(DeloadSource.RETURNING)
    }

    @Test fun unknownAdaptationAndUnknownRankingCapabilityRemainUnsupported() {
        val result = decodeHistoryReasons(record(listOf(
            "SUPPORTED_REGRESSION_PREFERENCE_V1.0",
            "SUPPORTED_REGRESSION_PREFERENCE_V1.0.PREFERRED:push",
            "SUPPORTED_REGRESSION_PREFERENCE_V1.0.SOURCE:dip",
            "SUPPORTED_REGRESSION_PREFERENCE_V1.0.CAPABILITY:FUTURE_CAPABILITY"
        )).copy(programStatePolicyVersion = "PROGRAM_STATE_V2", adaptationState = "FUTURE_STATE"))
        assertThat(result.ranking).isEmpty()
        assertThat(result.unsupportedIdentities).contains("FUTURE_STATE")
        assertThat(result.unsupportedIdentities).contains("SUPPORTED_REGRESSION_PREFERENCE_V1.0.CAPABILITY:FUTURE_CAPABILITY")
    }

    @Test fun everyShapePreservesApplicableNullsAndOmitsIrrelevantFields() {
        fun fields(type: ExerciseType, duration: Int? = null, distance: Double? = null) =
            historyMeasurements(WorkoutSet(workoutExerciseId = "e", setNumber = 1, exerciseType = type,
                targetDurationSeconds = duration, targetDistanceMeters = distance), performed = true).map { it.kind }
        assertThat(fields(ExerciseType.WEIGHT_REPS)).containsExactly(HistoryMeasurementKind.REPS, HistoryMeasurementKind.LOAD)
        assertThat(fields(ExerciseType.BODYWEIGHT_REPS)).containsExactly(HistoryMeasurementKind.REPS)
        assertThat(fields(ExerciseType.ASSISTED_BODYWEIGHT)).containsExactly(HistoryMeasurementKind.REPS, HistoryMeasurementKind.ASSISTANCE)
        assertThat(fields(ExerciseType.DURATION, duration = 60)).containsExactly(HistoryMeasurementKind.SECONDS)
        assertThat(fields(ExerciseType.DISTANCE_DURATION, distance = 200.0)).containsExactly(HistoryMeasurementKind.METERS)
        assertThat(fields(ExerciseType.DISTANCE_DURATION, duration = 60)).containsExactly(HistoryMeasurementKind.SECONDS)
        assertThat(fields(ExerciseType.DISTANCE_DURATION, duration = 60, distance = 200.0))
            .containsExactly(HistoryMeasurementKind.SECONDS, HistoryMeasurementKind.METERS)
        val missing = historyMeasurements(WorkoutSet(workoutExerciseId = "e", setNumber = 1), true)
        assertThat(missing.map { it.value }).containsExactly(null, null)
    }

    @Test fun performedExtraDistanceTimeComponentIsRetainedRatherThanLost() {
        val set = WorkoutSet(workoutExerciseId = "e", setNumber = 1, exerciseType = ExerciseType.DISTANCE_DURATION,
            targetDistanceMeters = 100.0, completedDistanceMeters = 120.0, completedDurationSeconds = 40)
        assertThat(historyMeasurements(set, true)).containsExactly(
            HistoryMeasurement(HistoryMeasurementKind.SECONDS, 40.0),
            HistoryMeasurement(HistoryMeasurementKind.METERS, 120.0)
        ).inOrder()
    }

    private fun record(codes: List<String>, validator: String = "WHOLE_PROGRAM_V2") = RecommendationRecord(
        sessionId = "viewed", validatorVersion = validator, durationEstimatorVersion = "DURATION_ESTIMATOR_V1",
        outcome = "VALID", reviewedPathEnabled = false, catalogVersion = null, reviewPolicyVersion = 0,
        trainingPolicyVersion = null, ledgerPolicyVersion = null, programStatePolicyVersion = null,
        adaptationState = null, weekStartEpochDay = null, timeZoneId = null, profileRevision = 0,
        contextIdentity = "digest", reasonCodes = codes, doseAccounting = emptyList(), recordedAtEpochMillis = 0
    )
}
