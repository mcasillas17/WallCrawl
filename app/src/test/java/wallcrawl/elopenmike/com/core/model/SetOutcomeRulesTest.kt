package wallcrawl.elopenmike.com.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Test

/**
 * The typed set-outcome invariants are pure rules so they can be proven here and reused
 * unchanged by the repository boundary that guards every persisted write.
 */
class SetOutcomeRulesTest {

    @Test
    fun completedSet_withRpeAboveTheProductScale_isRejectedNamingTheFieldOnly() {
        val message = expectRejection(
            completedWeightReps().copy(rpe = 10.5f)
        )

        assertThat(message).contains("rpe")
        assertThat(message).doesNotContain("10.5")
    }

    @Test
    fun completedSet_withNonFiniteRpe_isRejected() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { value ->
            assertThat(expectRejection(completedWeightReps().copy(rpe = value))).contains("rpe")
        }
    }

    @Test
    fun completedSet_withRirOutsideZeroToTen_isRejectedNamingTheFieldOnly() {
        val message = expectRejection(completedWeightReps().copy(rir = 11))

        assertThat(message).contains("rir")
        assertThat(message).doesNotContain("11")
    }

    @Test
    fun completedSet_withoutEffortFeedback_keepsNullAsNull() {
        val performance = completedWeightReps()

        SetOutcomeRules.requireValidOutcome(performance)

        assertThat(performance.rpe).isNull()
        assertThat(performance.rir).isNull()
        assertThat(performance.feltManageable).isNull()
    }

    @Test
    fun completedSet_withoutAPositiveCompletionTimestamp_isRejected() {
        assertThat(expectRejection(completedWeightReps().copy(completedAtTimestamp = null)))
            .contains("completedAtTimestamp")
        assertThat(expectRejection(completedWeightReps().copy(completedAtTimestamp = 0L)))
            .contains("completedAtTimestamp")
        assertThat(expectRejection(completedWeightReps().copy(completedAtTimestamp = -1L)))
            .contains("completedAtTimestamp")
    }

    @Test
    fun completedSet_cannotAlsoCarryAStopReasonOrStopTimestamp() {
        assertThat(expectRejection(completedWeightReps().copy(stopReason = SetStopReason.PAIN_STOP)))
            .contains("stopReason")
        assertThat(expectRejection(completedWeightReps().copy(stoppedAtTimestamp = 5L)))
            .contains("stoppedAtTimestamp")
    }

    @Test
    fun stoppedSet_requiresATypedReasonAndAPositiveOutcomeTimestamp() {
        val stopped = SetPerformanceInput(
            reps = 3,
            weight = 20.0,
            stopReason = SetStopReason.PAIN_STOP,
            stoppedAtTimestamp = 1_777_777L,
            isCompleted = false
        )

        SetOutcomeRules.requireValidOutcome(stopped)

        assertThat(expectRejection(stopped.copy(stoppedAtTimestamp = null)))
            .contains("stoppedAtTimestamp")
        assertThat(expectRejection(stopped.copy(stoppedAtTimestamp = 0L)))
            .contains("stoppedAtTimestamp")
    }

    @Test
    fun incompleteUntouchedSet_carriesNoTimestampFeedbackOrStopReason() {
        val untouched = SetPerformanceInput(isCompleted = false)

        SetOutcomeRules.requireValidOutcome(untouched)

        assertThat(expectRejection(untouched.copy(completedAtTimestamp = 5L)))
            .contains("completedAtTimestamp")
        assertThat(expectRejection(untouched.copy(stoppedAtTimestamp = 5L)))
            .contains("stoppedAtTimestamp")
        assertThat(expectRejection(untouched.copy(rpe = 8f))).contains("rpe")
        assertThat(expectRejection(untouched.copy(rir = 2))).contains("rir")
        assertThat(expectRejection(untouched.copy(feltManageable = true)))
            .contains("feltManageable")
    }

    @Test
    fun feltManageable_isRecordedOnlyForCompletedWorkAndNeverInferred() {
        val stopped = SetPerformanceInput(
            reps = 3,
            weight = 20.0,
            stopReason = SetStopReason.TIME_CONSTRAINT,
            stoppedAtTimestamp = 10L,
            isCompleted = false
        )

        assertThat(expectRejection(stopped.copy(feltManageable = true)))
            .contains("feltManageable")

        // A completed set with a low RPE still leaves the confirmation unanswered.
        val lowEffort = completedWeightReps().copy(rpe = 5f, rir = 5)
        SetOutcomeRules.requireValidOutcome(lowEffort)
        assertThat(lowEffort.feltManageable).isNull()
    }

    @Test
    fun completedSet_acceptsTheFullDocumentedRpeAndRirScale() {
        listOf(0f, 5.5f, 10f).forEach { rpe ->
            SetOutcomeRules.requireValidOutcome(completedWeightReps().copy(rpe = rpe))
        }
        listOf(0, 5, 10).forEach { rir ->
            SetOutcomeRules.requireValidOutcome(completedWeightReps().copy(rir = rir))
        }
    }

    private fun completedWeightReps() = SetPerformanceInput(
        reps = 8,
        weight = 20.0,
        completedAtTimestamp = 1_777_777L,
        isCompleted = true
    )

    private fun expectRejection(performance: SetPerformanceInput): String {
        try {
            SetOutcomeRules.requireValidOutcome(performance)
            fail("Expected the typed set outcome to be rejected")
        } catch (exception: IllegalArgumentException) {
            return exception.message.orEmpty()
        }
        error("unreachable")
    }
}
