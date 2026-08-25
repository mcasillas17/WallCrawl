package wallcrawl.elopenmike.com.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.database.entity.WorkoutExerciseEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSessionEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSetEntity
import wallcrawl.elopenmike.com.core.database.entity.UserProfileEntity
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.WeightUnit

@RunWith(AndroidJUnit4::class)
class WorkoutLifecycleDaoTest {

    private lateinit var database: WallCrawlDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WallCrawlDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun concurrentStarts_createOnlyOneActiveSession() = runBlocking {
        val sessionDao = database.workoutSessionDao()
        database.userProfileDao().insertOrUpdate(profileEntity())

        val returnedSessionIds = listOf("session-a", "session-b")
            .map { sessionId ->
                async(Dispatchers.IO) {
                    sessionDao.insertWorkoutUnlessActive(
                        session = sessionEntity(sessionId),
                        exercises = emptyList(),
                        sets = emptyList(),
                        expectedProfileId = PROFILE_ID,
                        expectedProfileRevision = 0L
                    ).session.id
                }
            }
            .awaitAll()

        assertThat(returnedSessionIds.toSet()).hasSize(1)
        assertThat(sessionDao.getActiveSession()).isNotNull()
    }

    @Test
    fun completedSession_rejectsSecondCompletionAndDelayedSetWrite() = runBlocking {
        val sessionDao = database.workoutSessionDao()
        val setDao = database.workoutSetDao()
        val sessionId = "session"
        val exerciseId = "workout-exercise"
        val setId = "set"
        sessionDao.insertWorkout(
            session = sessionEntity(sessionId),
            exercises = listOf(
                WorkoutExerciseEntity(
                    id = exerciseId,
                    sessionId = sessionId,
                    exerciseId = "incline-dumbbell-press",
                    orderIndex = 0,
                    targetSets = 1,
                    targetRepMin = 8,
                    targetRepMax = 10,
                    targetWeight = 20.0,
                    notes = ""
                )
            ),
            sets = listOf(
                WorkoutSetEntity(
                    id = setId,
                    workoutExerciseId = exerciseId,
                    setNumber = 1,
                    targetReps = 10,
                    completedReps = null,
                    targetWeight = 20.0,
                    completedWeight = null,
                    isCompleted = false,
                    rpe = null,
                    rir = null,
                    type = SetType.NORMAL
                )
            )
        )

        assertThat(
            setDao.updateSetCompletion(setId, reps = 10, weight = 20.0, isCompleted = true)
        ).isEqualTo(1)
        assertThat(
            sessionDao.completeSessionIfActive(
                sessionId = sessionId,
                completedAt = 2_000L,
                actualDuration = 20
            )
        ).isEqualTo(1)

        assertThat(
            sessionDao.completeSessionIfActive(
                sessionId = sessionId,
                completedAt = 3_000L,
                actualDuration = 30
            )
        ).isEqualTo(0)
        assertThat(
            setDao.updateSetCompletion(setId, reps = 99, weight = 999.0, isCompleted = true)
        ).isEqualTo(0)

        val persistedSet = setDao.getSetsForExercise(exerciseId).single()
        assertThat(persistedSet.completedReps).isEqualTo(10)
        assertThat(persistedSet.completedWeight).isEqualTo(20.0)
    }

    @Test
    fun staleProfileRevision_rejectsWorkoutStart() = runBlocking {
        val sessionDao = database.workoutSessionDao()
        database.userProfileDao().insertOrUpdate(profileEntity().copy(revision = 1L))

        var failure: IllegalStateException? = null
        try {
            sessionDao.insertWorkoutUnlessActive(
                session = sessionEntity("stale-session"),
                exercises = emptyList(),
                sets = emptyList(),
                expectedProfileId = PROFILE_ID,
                expectedProfileRevision = 0L
            )
        } catch (exception: IllegalStateException) {
            failure = exception
        }

        assertThat(failure).isNotNull()
        assertThat(sessionDao.getActiveSession()).isNull()
    }

    private fun sessionEntity(id: String) = WorkoutSessionEntity(
        id = id,
        name = "Workout $id",
        startedAtTimestamp = 1_000L,
        completedAtTimestamp = null,
        targetDurationMinutes = 30,
        actualDurationMinutes = 0,
        weightUnit = WeightUnit.LBS,
        status = SessionStatus.IN_PROGRESS,
        focusMusclesJson = "Chest",
        notes = ""
    )

    private fun profileEntity() = UserProfileEntity(
        id = PROFILE_ID,
        revision = 0L,
        name = "Crawler",
        primaryGoal = FitnessGoal.BUILD_MUSCLE,
        experienceLevel = ExperienceLevel.INTERMEDIATE,
        preferredDurationMinutes = 50,
        daysPerWeek = 4,
        availableEquipmentJson = "dumbbell",
        preferredUnit = WeightUnit.LBS,
        musclePrioritiesJson = "",
        excludedExerciseIdsJson = ""
    )

    private companion object {
        const val PROFILE_ID = "default_user"
    }
}
