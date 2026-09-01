package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wallcrawl.elopenmike.com.core.database.repository.WeeklyDoseLedgerRepository
import wallcrawl.elopenmike.com.core.model.AdaptationState
import wallcrawl.elopenmike.com.core.model.LedgerOmissionReason
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.TrainingProgramStatePolicyVersion
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeeklyDoseLedger

class TrainingProgramStateProviderTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    private val ledger = WeeklyDoseLedger(
        policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
        weekStartEpochDay = 20_696L,
        timeZoneId = zone.id,
        catalogVersion = "catalog-commit",
        reviewPolicyVersion = 1,
        directPrimarySets = emptyMap(),
        secondaryInvolvement = emptyMap(),
        unattributedWorkSets = mapOf(LedgerOmissionReason.METADATA_NOT_APPROVED to 6)
    )

    private class RecordingLedgerRepository(
        private val ledger: WeeklyDoseLedger
    ) : WeeklyDoseLedgerRepository {
        var requestedProfileId: String? = null
        var requestedZone: ZoneId? = null

        override suspend fun weeklyLedgerAt(
            profileId: String,
            instant: Instant,
            zoneId: ZoneId
        ): WeeklyDoseLedger = ledger

        override suspend fun currentWeeklyLedger(
            profileId: String,
            zoneId: ZoneId
        ): WeeklyDoseLedger {
            requestedProfileId = profileId
            requestedZone = zoneId
            return ledger
        }
    }

    @Test
    fun theProviderComposesTheDerivedStateWithThisWeeksLedger() = runTest {
        val provider = TrainingProgramStateProvider(
            weeklyDoseLedgerRepository = RecordingLedgerRepository(ledger),
            zoneId = { zone }
        )

        val state = provider.currentState(UserProfile(returningAfterBreakWeeks = 2))

        assertThat(state.policyVersion)
            .isEqualTo(TrainingProgramStatePolicyVersion.PROGRAM_STATE_V1)
        assertThat(state.adaptationState).isEqualTo(AdaptationState.RETURNING)
        assertThat(state.weeklyLedger).isEqualTo(ledger)
    }

    @Test
    fun theLedgerIsReadForThatProfileInTheInjectedZone() = runTest {
        val repository = RecordingLedgerRepository(ledger)
        val provider = TrainingProgramStateProvider(
            weeklyDoseLedgerRepository = repository,
            zoneId = { zone }
        )
        val profile = UserProfile()

        provider.currentState(profile)

        assertThat(repository.requestedProfileId).isEqualTo(profile.id)
        assertThat(repository.requestedZone).isEqualTo(zone)
    }
}
