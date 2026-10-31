package wallcrawl.elopenmike.com.core.database.repository

import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import wallcrawl.elopenmike.com.core.ai.DeloadOfferPolicy
import wallcrawl.elopenmike.com.core.database.dao.DeloadPreferencesDao
import wallcrawl.elopenmike.com.core.database.entity.DeloadPreferencesEntity
import wallcrawl.elopenmike.com.core.database.entity.UserProfileEntity
import wallcrawl.elopenmike.com.core.model.*

class DeloadRepositoryTest {
    private val dao = FakeDeloadDao()
    private var sequence = 0
    private val repository = OfflineDeloadRepository(dao, Mutex(),
        Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC), { "request-${++sequence}" })

    @Test fun absentRowIsRevisionZeroAndReturningOfferReadsNeverWrite() = runTest {
        assertEquals(DeloadPreferences(), repository.get())
        assertEquals(DeloadPreferences(), repository.observe().first())
        assertNotNull(DeloadOfferPolicy.offer(dao.profile!!.toUserProfile(), repository.get()))
        assertNull(dao.row.value)
    }

    @Test fun acceptedChoiceRequiresCancellationBeforeAnotherRequest() = runTest {
        repository.decide(DeloadAction.REQUEST, 4, 0, null)
        val first = repository.get()
        assertEquals(DeloadChoiceStatus.OFFERED, first.choice!!.status)
        repository.decide(DeloadAction.ACCEPT, 4, 1, first.choice.offer.id)
        assertEquals(DeloadChoiceStatus.ACCEPTED, repository.get().choice!!.status)
        val accepted = repository.get()
        rejected { repository.decide(DeloadAction.REQUEST, 4, 2, null) }
        assertEquals(accepted, repository.get())
        repository.decide(DeloadAction.CANCEL, 4, 2, first.choice.offer.id)
        repository.decide(DeloadAction.REQUEST, 4, 3, null)
        val second = repository.get()
        assertEquals(4L, second.revision)
        assertEquals(DeloadChoiceStatus.OFFERED, second.choice!!.status)
        assertNotEquals(first.choice.offer.id, second.choice.offer.id)
        rejected { repository.decide(DeloadAction.ACCEPT, 4, 4, first.choice.offer.id) }
        assertEquals(second, repository.get())
    }

    @Test fun handledReturningDecisionsSuppressAndCancelUsesCurrentReturnKey() = runTest {
        for (action in listOf(DeloadAction.ACCEPT, DeloadAction.DECLINE, DeloadAction.DISMISS)) {
            dao.row.value = null
            val offer = DeloadOfferPolicy.offer(dao.profile!!.toUserProfile(), repository.get())!!
            repository.decide(action, 4, 0, offer.id)
            assertEquals(offer.id, repository.get().lastHandledReturnKey)
            assertNull(DeloadOfferPolicy.offer(dao.profile!!.toUserProfile(), repository.get()))
        }
        repository.decide(DeloadAction.REQUEST, 4, 1, null)
        repository.decide(DeloadAction.ACCEPT, 4, 2, repository.get().choice!!.offer.id)
        dao.profile = dao.profile!!.copy(revision = 5, returningAfterBreakWeeks = 26)
        val accepted = repository.get().choice!!
        repository.decide(DeloadAction.CANCEL, 5, 3, accepted.offer.id)
        assertEquals(DeloadChoiceStatus.CANCELLED, repository.get().choice!!.status)
        assertEquals(DeloadOfferPolicy.returnKey(dao.profile!!.toUserProfile()), repository.get().lastHandledReturnKey)
        assertNull(DeloadOfferPolicy.offer(dao.profile!!.toUserProfile(), repository.get()))
    }

    @Test fun staleMissingUnonboardedActiveAndInvalidTransitionsDoNotWrite() = runTest {
        rejected { repository.decide(DeloadAction.REQUEST, 3, 0, null) }
        rejected { repository.decide(DeloadAction.REQUEST, 4, 1, null) }
        rejected { repository.decide(DeloadAction.CANCEL, 4, 0, null) }
        rejected { repository.decide(DeloadAction.ACCEPT, 4, 0, "stale") }
        dao.active = true
        rejected { repository.decide(DeloadAction.REQUEST, 4, 0, null) }
        dao.active = false
        dao.profile = dao.profile!!.copy(onboardingCompleted = false)
        rejected { repository.decide(DeloadAction.REQUEST, 4, 0, null) }
        dao.profile = null // a write queued behind delete-all must not resurrect preferences
        rejected { repository.decide(DeloadAction.REQUEST, 4, 0, null) }
        assertNull(dao.row.value)
    }

    @Test fun readsPropagateIoAndCorruptRowsInsteadOfInventingRevisionZero() = runTest {
        dao.readError = IOException("disk")
        assertThrows(IOException::class.java) { kotlinx.coroutines.runBlocking { repository.get() } }
        dao.readError = null
        repository.decide(DeloadAction.REQUEST, 4, 0, null)
        dao.row.value = dao.row.value!!.copy(status = "secret-invalid")
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { repository.get() } }
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { repository.observe().first() } }
    }

    @Test fun repeatAndNoncurrentTransitionsAreRefusedAndRevisionCannotWrap() = runTest {
        repository.decide(DeloadAction.REQUEST, 4, 0, null)
        val id = repository.get().choice!!.offer.id
        repository.decide(DeloadAction.ACCEPT, 4, 1, id)
        for (action in listOf(DeloadAction.ACCEPT, DeloadAction.DECLINE, DeloadAction.DISMISS)) {
            rejected { repository.decide(action, 4, 2, id) }
        }
        rejected { repository.decide(DeloadAction.CANCEL, 4, 2, "wrong") }
        rejected { repository.decide(DeloadAction.REQUEST, 4, 2, id) }
        assertEquals(2L, repository.get().revision)
        dao.row.value = dao.row.value!!.copy(revision = Long.MAX_VALUE)
        rejected { repository.decide(DeloadAction.CANCEL, 4, Long.MAX_VALUE, id) }
        assertEquals(Long.MAX_VALUE, repository.get().revision)
    }

    @Test fun scalarMappingRoundTripsAllStatusesAndRejectsPartialOrUnknownValues() = runTest {
        val offer = DeloadOffer("request", DeloadSource.EXPLICIT_REQUEST, DeloadOfferPolicy.VERSION)
        for (status in DeloadChoiceStatus.entries) {
            val preferences = DeloadPreferences(revision = 4,
                choice = DeloadChoice(offer, status, 10,
                    if (status == DeloadChoiceStatus.CONSUMED) "cancelled-session" else null))
            assertEquals(preferences, preferences.toEntity().toDeloadPreferences())
        }
        val row = DeloadPreferences(revision = 1, choice = DeloadChoice(offer, DeloadChoiceStatus.ACCEPTED, 10)).toEntity()
        for (bad in listOf(row.copy(source = null), row.copy(source = "private-invalid"),
            row.copy(policyVersion = "private-invalid"), row.copy(sessionId = "private-invalid"),
            row.copy(decidedAtEpochMillis = null), row.copy(offerId = null))) {
            val error = assertThrows(IllegalArgumentException::class.java) { bad.toDeloadPreferences() }
            assertFalse(error.message.orEmpty().contains("private-invalid"))
        }
    }

    private suspend fun rejected(block: suspend () -> Unit) {
        try { block(); fail("Expected refusal") } catch (_: IllegalStateException) { }
    }
}

private class FakeDeloadDao : DeloadPreferencesDao {
    var profile: UserProfileEntity? = UserProfile(onboardingCompleted = true,
        revision = 4, returningAfterBreakWeeks = 12).toUserProfileEntity()
    val row = MutableStateFlow<DeloadPreferencesEntity?>(null)
    var active = false
    var readError: IOException? = null
    override fun observe(profileId: String): Flow<DeloadPreferencesEntity?> = row
    override suspend fun get(profileId: String): DeloadPreferencesEntity? {
        readError?.let { throw it }
        return row.value
    }
    override suspend fun getProfile(profileId: String): UserProfileEntity? = profile
    override suspend fun hasActiveWorkout(): Boolean = active
    override suspend fun upsert(preferences: DeloadPreferencesEntity) { row.value = preferences }
}
