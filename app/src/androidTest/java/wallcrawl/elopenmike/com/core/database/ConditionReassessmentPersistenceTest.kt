package wallcrawl.elopenmike.com.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.database.entity.ConditionReassessmentPendingEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSessionEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutExerciseEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSetEntity
import wallcrawl.elopenmike.com.core.database.entity.toModel
import wallcrawl.elopenmike.com.core.database.repository.ConditionContextCodec
import wallcrawl.elopenmike.com.core.database.repository.toUserProfileEntity
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.model.*

@RunWith(AndroidJUnit4::class)
class ConditionReassessmentPersistenceTest {
    private lateinit var database: WallCrawlDatabase
    private val condition = SelectedCondition("fictional-condition", 1, mapOf("answer" to "fictional-choice"))
    private val digest = "a".repeat(64)

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WallCrawlDatabase::class.java).build()
    }
    @After fun tearDown() = database.close()

    @Test fun onboardingConditionDraftIsOneRevisionAndActiveRefusalIsAtomic() = runBlocking<Unit> {
        val repository = OfflineUserProfileRepository(database.userProfileDao())
        val initial = repository.getProfileOnce()
        val saved = androidx.lifecycle.SavedStateHandle()
        suspend fun complete(name: String, selections: List<SelectedCondition>) {
            val vm = kotlinx.coroutines.withContext(Dispatchers.Main) {
                wallcrawl.elopenmike.com.feature.onboarding.OnboardingViewModel(repository, saved).also {
                    it.updateName(name)
                    MovementCapabilityType.entries.forEach { type -> it.updateMovementCapability(type, CapabilityLevel.UNKNOWN) }
                    it.updateConditions(selections)
                    it.complete()
                }
            }
            withTimeout(5000) { vm.uiState.first { it.isComplete || it.error != null } }
        }
        complete("saved atomically", listOf(condition))
        val success = repository.getProfileOnce()
        assertEquals(initial.revision + 1, success.revision)
        assertEquals(listOf(condition), success.activeConditions)
        database.workoutSessionDao().insertWorkout(session("active"), emptyList(), emptyList())
        complete("must not persist", emptyList())
        assertEquals(success, repository.getProfileOnce())
        assertTrue(saved.get<List<String>>("onboarding.conditions")!!.isEmpty())
    }

    @Test fun pendingObservationTracksExactReplacementAndRejectsOverflowWithoutSelections() = runBlocking<Unit> {
        val repository = wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutRepository(database.workoutSessionDao(), database.workoutSetDao())
        val flow = repository.observePendingConditionReassessments(UserProfile.DEFAULT_PROFILE_ID)
        assertTrue(flow.first().isEmpty())
        val marker = ConditionReassessmentPendingEntity(UserProfile.DEFAULT_PROFILE_ID, "withdrawn", "exercise", "first", digest, 1, 100, null)
        val changed = async { withTimeout(5000) { flow.first { it.singleOrNull()?.eventId == "second" } } }
        database.conditionReassessmentDao().replace(marker)
        database.conditionReassessmentDao().replace(marker.copy(eventId = "second"))
        assertEquals("second", changed.await().single().eventId)
        repeat(512) { database.conditionReassessmentDao().replace(marker.copy(exerciseId = "exercise-$it")) }
        assertThrows(IllegalStateException::class.java) { runBlocking { flow.first() } }
    }

    @Test fun selectedFactsRoundTripThroughRepositoryWithoutCatalogRecognition() = runBlocking {
        val repository = OfflineUserProfileRepository(database.userProfileDao())
        val selections = listOf(condition, SelectedCondition("another-future-condition", 2, emptyMap()))
        repository.updateActiveConditions(selections)
        val persisted = repository.getProfileOnce()
        assertEquals(selections, persisted.activeConditions)
        assertEquals(ConditionContextCodec.encodeSelections(selections),
            database.userProfileDao().getProfile(persisted.id)?.activeConditionsJson)
    }

    @Test fun markerSurvivesReplaceCancellationAndConditionRemoval() = runBlocking {
        val profiles = database.userProfileDao()
        val sessions = database.workoutSessionDao()
        val markers = database.conditionReassessmentDao()
        val selected = profiles.insertOrUpdateWithNextRevision(UserProfile(activeConditions = listOf(condition)).toUserProfileEntity())
        val sourceSetId = "a03c8a04-9f34-45e2-9654-d5cdb16aeb77"
        val marker = ConditionReassessmentPendingEntity(selected.id, condition.conditionId, "fictional-exercise", "event-1",
            digest, 1, 100, sourceSetId)
        assertTrue(markers.upsertIfNewer(marker))
        assertFalse(markers.upsertIfNewer(marker))
        assertFalse(markers.upsertIfNewer(marker.copy(eventId = "older", stoppedAtTimestamp = 99)))
        assertTrue(markers.upsertIfNewer(marker.copy(eventId = "event-2", stoppedAtTimestamp = 101)))
        assertEquals(0, markers.deleteIfEventMatches(selected.id, condition.conditionId, "fictional-exercise", "event-1"))
        val context = SessionConditionContext(1, 1, emptyList(), emptyMap(), emptyList(), emptyList(), emptyList())
        sessions.insertWorkout(session("session-1").copy(
            conditionContextJson = ConditionContextCodec.encodeSession(context)),
            listOf(WorkoutExerciseEntity("exercise-row", "session-1", "fictional-exercise", 0,
                targetSets = 1, targetRepMin = null, targetRepMax = null, targetWeight = null, notes = "")),
            listOf(WorkoutSetEntity(sourceSetId, "exercise-row", 1, targetReps = null, completedReps = null,
                targetWeight = null, completedWeight = null, isCompleted = false, rpe = null, rir = null, type = SetType.NORMAL)))
        assertNotNull(database.workoutSetDao().getSetById(sourceSetId))
        assertEquals(context, sessions.getSessionWithDetails("session-1")!!.session.conditionContextJson
            ?.let(ConditionContextCodec::decodeSession))
        assertNull(sessions.getRecommendationRecord("session-1"))
        assertEquals(1, sessions.deleteActiveSession("session-1"))
        assertNull(database.workoutSetDao().getSetById(sourceSetId))
        assertEquals(sourceSetId, markers.get(selected.id, condition.conditionId, "fictional-exercise")?.toModel()?.sourceSetId)
        profiles.insertOrUpdateWithNextRevision(selected.copy(activeConditionsJson = ConditionContextCodec.encodeSelections(emptyList())))
        profiles.insertOrUpdateWithNextRevision(selected)
        assertEquals("event-2", markers.get(selected.id, condition.conditionId, "fictional-exercise")?.eventId)
        assertEquals(1, markers.deleteIfEventMatches(selected.id, condition.conditionId, "fictional-exercise", "event-2"))
    }

    @Test fun profileChangeAndStartInterleavingsCannotBothCommit() = runBlocking {
        val profiles = database.userProfileDao()
        val sessions = database.workoutSessionDao()
        val initial = profiles.insertOrUpdateWithNextRevision(UserProfile().toUserProfileEntity())
        val changed = initial.copy(activeConditionsJson = ConditionContextCodec.encodeSelections(listOf(condition)))
        sessions.insertWorkoutUnlessActive(session("first"), emptyList(), emptyList(), initial.id, initial.revision)
        assertThrows(IllegalStateException::class.java) { runBlocking { profiles.insertOrUpdateWithNextRevision(changed) } }
        assertEquals(initial.activeConditionsJson, profiles.getProfile(initial.id)?.activeConditionsJson)
        profiles.insertOrUpdateWithNextRevision(initial.copy(themePreference = ThemePreference.DARK))
        assertEquals("first", sessions.insertWorkoutUnlessActive(session("ignored"), emptyList(), emptyList(),
            initial.id, initial.revision + 1).session.id)
        sessions.deleteActiveSession("first")
        val revised = profiles.insertOrUpdateWithNextRevision(changed)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { sessions.insertWorkoutUnlessActive(session("stale"), emptyList(), emptyList(), initial.id, initial.revision) }
        }
        assertNull(sessions.getActiveSession())
        assertEquals(revised.activeConditionsJson, profiles.getProfile(initial.id)?.activeConditionsJson)
    }

    @Test fun concurrentConditionChangeAndStartCommitExactlyOneCompatibleState() = runBlocking {
        val profiles = database.userProfileDao()
        val sessions = database.workoutSessionDao()
        repeat(10) { attempt ->
            val initial = profiles.insertOrUpdateWithNextRevision(UserProfile().toUserProfileEntity())
            val gate = CompletableDeferred<Unit>()
            val change = async(Dispatchers.Default) {
                gate.await()
                runCatching { profiles.insertOrUpdateWithNextRevision(initial.copy(
                    activeConditionsJson = ConditionContextCodec.encodeSelections(listOf(condition)))) }
            }
            val start = async(Dispatchers.Default) {
                gate.await()
                runCatching { sessions.insertWorkoutUnlessActive(session("race-$attempt"), emptyList(), emptyList(),
                    initial.id, initial.revision) }
            }
            gate.complete(Unit)
            val changed = change.await()
            val started = start.await()
            assertNotEquals(changed.isSuccess, started.isSuccess)
            if (started.isSuccess) {
                assertEquals(initial.activeConditionsJson, profiles.getProfile(initial.id)?.activeConditionsJson)
                assertEquals("race-$attempt", sessions.getActiveSession()?.session?.id)
                sessions.deleteActiveSession("race-$attempt")
            } else {
                assertTrue(started.exceptionOrNull() is IllegalStateException)
                assertEquals(ConditionContextCodec.encodeSelections(listOf(condition)),
                    profiles.getProfile(initial.id)?.activeConditionsJson)
                assertNull(sessions.getActiveSession())
            }
        }
    }

    @Test fun unresolvedLegacyConstraintBlocksNewStartButAllowsResumeAndCancel() = runBlocking {
        val profiles = database.userProfileDao()
        val sessions = database.workoutSessionDao()
        val initial = profiles.insertOrUpdateWithNextRevision(UserProfile().toUserProfileEntity())
        sessions.insertWorkoutUnlessActive(session("active"), emptyList(), emptyList(), initial.id, initial.revision)
        profiles.insertOrUpdate(initial.copy(trainingConstraintsJson = "FUTURE_CONSTRAINT"))
        assertEquals("active", sessions.insertWorkoutUnlessActive(session("resume"), emptyList(), emptyList(),
            initial.id, initial.revision).session.id)
        assertEquals(1, sessions.deleteActiveSession("active"))
        assertThrows(IllegalStateException::class.java) {
            runBlocking { sessions.insertWorkoutUnlessActive(session("refused"), emptyList(), emptyList(), initial.id, initial.revision) }
        }
        assertNull(sessions.getActiveSession())
    }

    private fun session(id: String) = WorkoutSessionEntity(id, id, 1, null, 30, 0, WeightUnit.LBS,
        SessionStatus.IN_PROGRESS, focusMusclesJson = "", notes = "")
}
