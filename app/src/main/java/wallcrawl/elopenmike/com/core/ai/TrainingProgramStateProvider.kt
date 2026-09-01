package wallcrawl.elopenmike.com.core.ai

import java.time.ZoneId
import wallcrawl.elopenmike.com.core.database.repository.WeeklyDoseLedgerRepository
import wallcrawl.elopenmike.com.core.model.TrainingProgramState
import wallcrawl.elopenmike.com.core.model.TrainingProgramStatePolicyVersion
import wallcrawl.elopenmike.com.core.model.UserProfile

/**
 * Composes the current [TrainingProgramState] for one profile.
 *
 * This is the only unit in the composition that performs I/O. The adaptation policy stays
 * pure, and the weekly ledger is read through its repository, which reconstructs it from
 * completed history and caches nothing that can drift from that history.
 *
 * The zone is supplied rather than read here, so a test controls the week boundary and the
 * application controls it in production. Reading the same calendar week in another zone
 * produces a separately reconstructed ledger, which is the ledger's own documented behavior.
 *
 * Failures propagate. An unreadable catalog or database is not an empty training week, and
 * reporting one as the other would under-report work the user actually did.
 */
class TrainingProgramStateProvider(
    private val weeklyDoseLedgerRepository: WeeklyDoseLedgerRepository,
    private val adaptationStatePolicy: AdaptationStatePolicy = AdaptationStatePolicy(),
    private val zoneId: () -> ZoneId = ZoneId::systemDefault
) {

    suspend fun currentState(profile: UserProfile): TrainingProgramState =
        TrainingProgramState(
            policyVersion = TrainingProgramStatePolicyVersion.PROGRAM_STATE_V1,
            adaptationState = adaptationStatePolicy.derive(profile),
            weeklyLedger = weeklyDoseLedgerRepository.currentWeeklyLedger(
                profileId = profile.id,
                zoneId = zoneId()
            )
        )
}
