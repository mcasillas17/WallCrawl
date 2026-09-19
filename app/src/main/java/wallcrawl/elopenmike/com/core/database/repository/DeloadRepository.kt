package wallcrawl.elopenmike.com.core.database.repository

import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import wallcrawl.elopenmike.com.core.database.dao.DeloadPreferencesDao
import wallcrawl.elopenmike.com.core.model.DeloadAction
import wallcrawl.elopenmike.com.core.model.DeloadPreferences
import wallcrawl.elopenmike.com.core.model.UserProfile

interface DeloadRepository {
    fun observe(): Flow<DeloadPreferences>
    suspend fun get(): DeloadPreferences
    suspend fun decide(action: DeloadAction, expectedProfileRevision: Long,
        expectedDecisionRevision: Long, offerId: String?)
}

class OfflineDeloadRepository(
    private val deloadPreferencesDao: DeloadPreferencesDao,
    private val localDataWriteGate: Mutex,
    private val clock: Clock = Clock.systemUTC(),
    private val newOfferId: () -> String = { UUID.randomUUID().toString() }
) : DeloadRepository {
    override fun observe(): Flow<DeloadPreferences> =
        deloadPreferencesDao.observe(UserProfile.DEFAULT_PROFILE_ID).map {
            it?.toDeloadPreferences() ?: DeloadPreferences()
        }

    override suspend fun get(): DeloadPreferences =
        deloadPreferencesDao.get(UserProfile.DEFAULT_PROFILE_ID)?.toDeloadPreferences() ?: DeloadPreferences()

    override suspend fun decide(action: DeloadAction, expectedProfileRevision: Long,
        expectedDecisionRevision: Long, offerId: String?) = localDataWriteGate.withLock {
        deloadPreferencesDao.decide(action, expectedProfileRevision, expectedDecisionRevision, offerId,
            clock.millis(), if (action == DeloadAction.REQUEST) newOfferId() else null)
    }
}
