package wallcrawl.elopenmike.com.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrainingProgramStateTest {

    private val ledger = WeeklyDoseLedger(
        policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
        weekStartEpochDay = 20_696L,
        timeZoneId = "UTC",
        catalogVersion = "catalog-commit",
        reviewPolicyVersion = 1,
        directPrimarySets = mapOf("Chest" to 4),
        secondaryInvolvement = mapOf("Triceps" to 4),
        unattributedWorkSets = mapOf(LedgerOmissionReason.METADATA_NOT_APPROVED to 2)
    )

    @Test
    fun theStateCarriesItsAdaptationStateAndLedgerUnchanged() {
        val state = TrainingProgramState(
            policyVersion = TrainingProgramStatePolicyVersion.PROGRAM_STATE_V1,
            adaptationState = AdaptationState.RETURNING,
            weeklyLedger = ledger
        )

        assertThat(state.adaptationState).isEqualTo(AdaptationState.RETURNING)
        assertThat(state.weeklyLedger).isEqualTo(ledger)
        assertThat(state.weeklyLedger.directPrimarySets).containsExactly("Chest", 4)
    }

    @Test
    fun statesWithIdenticalInputsAreEqual() {
        fun build() = TrainingProgramState(
            policyVersion = TrainingProgramStatePolicyVersion.PROGRAM_STATE_V1,
            adaptationState = AdaptationState.UNCALIBRATED,
            weeklyLedger = ledger
        )

        assertThat(build()).isEqualTo(build())
    }
}
