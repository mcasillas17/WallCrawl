package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.RecommendationRecord
import wallcrawl.elopenmike.com.core.model.WorkoutRankingReason

class RecommendationSnapshotRankingReasonTest {

    @Test
    fun asRecord_retainsTheMaximumPlannerInversionsAlongsideRepairProvenance() {
        val rankingReasons = (0 until MAX_PLANNED_EXERCISES).flatMap { target ->
            (0 until MAX_PLANNED_EXERCISES).map { source ->
                WorkoutRankingReason.SupportedRegressionPreference(
                    preferredExerciseId = "target-$target",
                    sourceExerciseId = "source-$source",
                    capability = MovementCapabilityType.entries[
                        (target + source) % MovementCapabilityType.entries.size
                    ]
                )
            }
        }
        val snapshot = RecommendationSnapshot(
            validatorVersion = ProgramValidatorVersion.WHOLE_PROGRAM_V1,
            durationEstimatorVersion = "test",
            outcome = RecommendationOutcome.REPAIRED,
            reviewedPathEnabled = true,
            catalogVersion = "test",
            reviewPolicyVersion = 2,
            trainingPolicyVersion = null,
            ledgerPolicyVersion = null,
            programStatePolicyVersion = null,
            adaptationState = null,
            weekStartEpochDay = null,
            timeZoneId = null,
            profileRevision = 1,
            contextIdentity = "context",
            reasonCodes = listOf(ProgramViolationCode.WEEKLY_ALLOWANCE_EXCEEDED),
            rankingReasons = rankingReasons,
            doseAccounting = emptyList()
        )

        val record = snapshot.asRecord(sessionId = "session", recordedAtEpochMillis = 1)

        assertThat(record.reasonCodes).hasSize(145)
        assertThat(record.reasonCodes.size).isAtMost(RecommendationRecord.MAX_REASON_CODES)
        val scheduling = (0 until 6).map {
            WorkoutRankingReason.TrainingFrequencyRecencyPreference("repeat-$it", "alternative-$it", "Chest", 6, 3)
        }
        val mixed = snapshot.copy(
            rankingReasons = rankingReasons + scheduling,
            schedulingPolicyVersion = "TRAINING_FREQUENCY_RECENCY_V1",
            generationIndex = 42
        ).asRecord("mixed-session", 1)
        assertThat(mixed.reasonCodes).hasSize(183)
        assertThat(mixed.reasonCodes.size).isAtMost(RecommendationRecord.MAX_REASON_CODES)
        val full = snapshot.copy(
            rankingReasons = rankingReasons + scheduling,
            schedulingPolicyVersion = "TRAINING_FREQUENCY_RECENCY_V1",
            generationIndex = 42,
            deloadDecisionRevision = 2,
            acceptedDeloadOfferId = "accepted",
            acceptedDeloadSource = wallcrawl.elopenmike.com.core.model.DeloadSource.EXPLICIT_REQUEST,
            progression = (0 until 6).map { index ->
                wallcrawl.elopenmike.com.core.model.ProgressionProvenance(
                    "exercise-$index", wallcrawl.elopenmike.com.core.model.ProgressionReason.HOLD_VALIDATION_REPAIR,
                    null,
                    listOf("source-a", "source-b"), "a".repeat(64)
                )
            }
        ).asRecord("maximum", 1)
        assertThat(full.reasonCodes.size).isEqualTo(RecommendationRecord.MAX_REASON_CODES)
        assertThat(wallcrawl.elopenmike.com.core.model.WorkoutRankingReasonCode.decode(mixed.reasonCodes))
            .containsExactlyElementsIn(rankingReasons + scheduling).inOrder()
    }

    @Test
    fun recommendationRecord_acceptsItsReasonLimitAndRejectsOneTokenMore() {
        val record = recordWith((0 until RecommendationRecord.MAX_REASON_CODES).map { "reason-$it" })

        assertThat(record.reasonCodes).hasSize(RecommendationRecord.MAX_REASON_CODES)
        assertThrows(IllegalArgumentException::class.java) {
            record.copy(reasonCodes = record.reasonCodes + "one-too-many")
        }
    }

    private fun recordWith(reasonCodes: List<String>): RecommendationRecord =
        RecommendationRecord(
            sessionId = "session",
            validatorVersion = "validator",
            durationEstimatorVersion = "duration",
            outcome = "REPAIRED",
            reviewedPathEnabled = true,
            catalogVersion = "catalog",
            reviewPolicyVersion = 2,
            trainingPolicyVersion = null,
            ledgerPolicyVersion = null,
            programStatePolicyVersion = null,
            adaptationState = null,
            weekStartEpochDay = null,
            timeZoneId = null,
            profileRevision = 1,
            contextIdentity = "context",
            reasonCodes = reasonCodes,
            doseAccounting = emptyList(),
            recordedAtEpochMillis = 1
        )

    private companion object {
        const val MAX_PLANNED_EXERCISES: Int = 6
    }
}
