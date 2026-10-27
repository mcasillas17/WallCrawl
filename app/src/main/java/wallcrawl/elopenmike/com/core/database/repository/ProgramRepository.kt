package wallcrawl.elopenmike.com.core.database.repository

import androidx.room.withTransaction
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import wallcrawl.elopenmike.com.core.ai.MultiWeekProgramPolicy
import wallcrawl.elopenmike.com.core.database.WallCrawlDatabase
import wallcrawl.elopenmike.com.core.model.*

interface ProgramRepository {
    suspend fun getHeader(profileId: String = UserProfile.DEFAULT_PROFILE_ID): ProgramHeader
    fun observeHeader(profileId: String = UserProfile.DEFAULT_PROFILE_ID): Flow<ProgramHeader>
    suspend fun get(profileId: String = UserProfile.DEFAULT_PROFILE_ID): ProgramReadState
    fun observe(profileId: String = UserProfile.DEFAULT_PROFILE_ID): Flow<ProgramReadState>
    fun observeAssociation(sessionId: String): Flow<ProgramHistoryAssociation?>
    suspend fun act(action: ProgramAction, expected: ProgramExpectation, confirmed: Boolean = false,
        newProgramId: String? = null)
}

class OfflineProgramRepository(
    private val database: WallCrawlDatabase,
    private val localDataWriteGate: Mutex,
    private val clock: Clock = Clock.systemUTC(),
    private val newId: () -> String = { UUID.randomUUID().toString() }
) : ProgramRepository {
    override suspend fun getHeader(profileId: String) = read { database.programDao().readProgramHeader(profileId) }
    override fun observeHeader(profileId: String): Flow<ProgramHeader> =
        observeRead("program_enrollments") { database.programDao().readProgramHeader(profileId) }
    override suspend fun get(profileId: String) = read { database.programDao().readProgramState(profileId) }
    override fun observe(profileId: String): Flow<ProgramReadState> = observeRead(
        "user_profiles", "program_enrollments", "program_session_associations", "workout_sessions", "workout_recommendation_records"
    ) { database.programDao().readProgramState(profileId) }

    override fun observeAssociation(sessionId: String): Flow<ProgramHistoryAssociation?> {
        requireProgramIdentityToken(sessionId)
        return observeRead("program_session_associations", "workout_recommendation_records", "workout_sessions") {
            val dao = database.workoutSessionDao()
            val record = dao.getRecommendationRecord(sessionId)
            val association = dao.getProgramAssociation(sessionId)
            validateProgramBinding(record, association)
            association?.toHistoryAssociation()
        }
    }

    override suspend fun act(action: ProgramAction, expected: ProgramExpectation, confirmed: Boolean, newProgramId: String?) = read {
        if (action !in listOf(ProgramAction.START, ProgramAction.PAUSE, ProgramAction.RESUME, ProgramAction.RESTART, ProgramAction.REPLACE)) {
            throw ProgramException(ProgramFailure.INVALID_ACTION)
        }
        val dao = database.programDao()
        val profile = dao.getProgramProfile(expected.profileId)?.toUserProfile()
        val row = dao.getProgramEnrollment(expected.profileId)
        val header = row?.header() ?: ProgramHeader.absent(expected.profileId)
        MultiWeekProgramPolicy.checkExpected(profile, header, expected)
        if (dao.hasProgramActiveWorkout()) throw ProgramException(ProgramFailure.ACTIVE_WORKOUT)
        if (row == null) dao.requireProgramAssociationIntegrity()
        val now = clock.millis()
        val freshId = if (action in listOf(ProgramAction.START, ProgramAction.RESTART, ProgramAction.REPLACE)) newProgramId ?: newId() else null
        if (freshId != null && dao.programIdExists(freshId)) throw ProgramException(ProgramFailure.INVALID_IDENTITY)
        row?.validateRaw()
        if (row != null && !row.supported()) {
            dao.validateOpaqueProgramSlots(row.profileId, row.programId)
            when (action) {
                ProgramAction.REPLACE -> dao.upsertProgramEnrollment(MultiWeekProgramPolicy.replace(profile, header, expected,
                    requireNotNull(freshId), now, false, confirmed).toEntity())
                ProgramAction.PAUSE -> {
                    if (row.status != ProgramStatus.ACTIVE.name) throw ProgramException(ProgramFailure.INVALID_ACTION)
                    if (now !in 0..4_102_444_800_000L) throw ProgramException(ProgramFailure.INVALID_IDENTITY)
                    dao.upsertProgramEnrollment(row.copy(status = ProgramStatus.PAUSED.name,
                        revision = MultiWeekProgramPolicy.nextRevision(row.revision), updatedAtTimestamp = now))
                }
                else -> throw ProgramException(ProgramFailure.UNSUPPORTED_POLICY)
            }
        } else {
            val enrollment = row?.toProgramEnrollment()
            val sessions = row?.let { dao.getProgramSlots(it.programId).map { slot -> slot.toSlot() } }.orEmpty()
            dao.upsertProgramEnrollment(MultiWeekProgramPolicy.transition(profile, enrollment, sessions, action, expected,
                now, newProgramId = freshId, confirmed = confirmed).toEntity())
        }
    }
    private suspend fun <T> read(block: suspend () -> T): T = localDataWriteGate.withLock { database.withTransaction { block() } }
    private fun <T> observeRead(vararg tables: String, block: suspend () -> T): Flow<T> =
        database.invalidationTracker.createFlow(*tables).map { read(block) }.flowOn(Dispatchers.IO)
}
