package wallcrawl.elopenmike.com.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.database.entity.UserProfileEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutExerciseEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutRecommendationRecordEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSessionEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSetEntity
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.WeightUnit

/**
 * A started session and the provenance of the plan it came from land together, or not at all.
 *
 * Every refusal below is checked for both halves: nothing that stops a start may leave a
 * half-written session behind, and nothing may leave a record without the session it
 * describes.
 */
@RunWith(AndroidJUnit4::class)
class RecommendationRecordDaoTest {

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
    fun startingAWorkout_writesTheSessionItsChildrenAndItsRecordTogether() = runBlocking {
        val sessionDao = database.workoutSessionDao()
        database.userProfileDao().insertOrUpdate(profileEntity())

        sessionDao.insertWorkoutUnlessActive(
            session = sessionEntity(SESSION_ID),
            exercises = listOf(exerciseEntity(SESSION_ID)),
            sets = listOf(setEntity()),
            expectedProfileId = PROFILE_ID,
            expectedProfileRevision = 0L,
            recommendation = recordEntity(SESSION_ID)
        )

        val stored = checkNotNull(sessionDao.getRecommendationRecord(SESSION_ID))
        assertThat(stored.outcome).isEqualTo("REPAIRED")
        assertThat(stored.reasonCodes).isEqualTo("WEEKLY_ALLOWANCE_EXCEEDED")
        assertThat(stored.contextIdentity).isEqualTo("context-identity")
        assertThat(checkNotNull(sessionDao.getSessionWithDetails(SESSION_ID)).exercisesWithSets)
            .hasSize(1)
    }

    @Test
    fun aProfileEditedUnderneathTheStart_writesNeitherASessionNorARecord() = runBlocking {
        val sessionDao = database.workoutSessionDao()
        database.userProfileDao().insertOrUpdate(profileEntity())

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                sessionDao.insertWorkoutUnlessActive(
                    session = sessionEntity(SESSION_ID),
                    exercises = listOf(exerciseEntity(SESSION_ID)),
                    sets = listOf(setEntity()),
                    expectedProfileId = PROFILE_ID,
                    // The profile moved on between building the recommendation and starting it.
                    expectedProfileRevision = 4L,
                    recommendation = recordEntity(SESSION_ID)
                )
            }
        }

        assertThat(sessionDao.getSessionWithDetails(SESSION_ID)).isNull()
        assertThat(sessionDao.getRecommendationRecord(SESSION_ID)).isNull()
    }

    @Test
    fun anAlreadyActiveWorkout_leavesTheSecondStartWithNoSessionAndNoRecord() = runBlocking {
        val sessionDao = database.workoutSessionDao()
        database.userProfileDao().insertOrUpdate(profileEntity())
        sessionDao.insertWorkoutUnlessActive(
            session = sessionEntity("first-session"),
            exercises = emptyList(),
            sets = emptyList(),
            expectedProfileId = PROFILE_ID,
            expectedProfileRevision = 0L,
            recommendation = recordEntity("first-session")
        )

        val returned = sessionDao.insertWorkoutUnlessActive(
            session = sessionEntity(SESSION_ID),
            exercises = emptyList(),
            sets = emptyList(),
            expectedProfileId = PROFILE_ID,
            expectedProfileRevision = 0L,
            recommendation = recordEntity(SESSION_ID)
        )

        assertThat(returned.session.id).isEqualTo("first-session")
        assertThat(sessionDao.getRecommendationRecord(SESSION_ID)).isNull()
        assertThat(sessionDao.getRecommendationRecord("first-session")).isNotNull()
    }

    @Test
    fun aRecordThatDoesNotBelongToTheSession_writesNothingAtAll() = runBlocking {
        val sessionDao = database.workoutSessionDao()
        database.userProfileDao().insertOrUpdate(profileEntity())

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                sessionDao.insertWorkoutUnlessActive(
                    session = sessionEntity(SESSION_ID),
                    exercises = emptyList(),
                    sets = emptyList(),
                    expectedProfileId = PROFILE_ID,
                    expectedProfileRevision = 0L,
                    recommendation = recordEntity("some-other-session")
                )
            }
        }

        assertThat(sessionDao.getSessionWithDetails(SESSION_ID)).isNull()
        assertThat(sessionDao.getRecommendationRecord("some-other-session")).isNull()
    }

    @Test
    fun aStartWithNoRecommendation_stillWritesTheSession() = runBlocking {
        val sessionDao = database.workoutSessionDao()
        database.userProfileDao().insertOrUpdate(profileEntity())

        // A manual template start supplies no validation provenance, and that is not a
        // failure: history recorded without a record is the honest shape for it.
        sessionDao.insertWorkoutUnlessActive(
            session = sessionEntity(SESSION_ID),
            exercises = emptyList(),
            sets = emptyList(),
            expectedProfileId = PROFILE_ID,
            expectedProfileRevision = 0L,
            recommendation = null
        )

        assertThat(sessionDao.getSessionWithDetails(SESSION_ID)).isNotNull()
        assertThat(sessionDao.getRecommendationRecord(SESSION_ID)).isNull()
    }

    private fun profileEntity() = UserProfileEntity(
        id = PROFILE_ID,
        name = "Crawler",
        primaryGoal = FitnessGoal.BUILD_MUSCLE,
        experienceLevel = ExperienceLevel.INTERMEDIATE,
        preferredDurationMinutes = 45,
        daysPerWeek = 4,
        availableEquipmentJson = "",
        preferredUnit = WeightUnit.LBS,
        musclePrioritiesJson = "",
        excludedExerciseIdsJson = ""
    )

    private fun sessionEntity(sessionId: String) = WorkoutSessionEntity(
        id = sessionId,
        name = "Session $sessionId",
        startedAtTimestamp = 1_000L,
        completedAtTimestamp = null,
        targetDurationMinutes = 45,
        actualDurationMinutes = 0,
        status = SessionStatus.IN_PROGRESS,
        focusMusclesJson = "",
        notes = ""
    )

    private fun exerciseEntity(sessionId: String) = WorkoutExerciseEntity(
        id = EXERCISE_ID,
        sessionId = sessionId,
        exerciseId = "barbell-bench-press",
        orderIndex = 0,
        exerciseType = ExerciseType.WEIGHT_REPS,
        targetSets = 2,
        targetRepMin = 8,
        targetRepMax = 10,
        targetWeight = null,
        notes = ""
    )

    private fun setEntity() = WorkoutSetEntity(
        id = "set-1",
        workoutExerciseId = EXERCISE_ID,
        setNumber = 1,
        exerciseType = ExerciseType.WEIGHT_REPS,
        targetReps = 10,
        completedReps = null,
        targetWeight = null,
        completedWeight = null,
        isCompleted = false,
        rpe = null,
        rir = null,
        type = SetType.NORMAL
    )

    private fun recordEntity(sessionId: String) = WorkoutRecommendationRecordEntity(
        sessionId = sessionId,
        validatorVersion = "WHOLE_PROGRAM_V1",
        durationEstimatorVersion = "DURATION_ESTIMATOR_V1",
        outcome = "REPAIRED",
        reviewedPathEnabled = true,
        catalogVersion = "catalog-commit",
        reviewPolicyVersion = 1,
        trainingPolicyVersion = "STATE_BASED_DOSE_EFFORT_REST_V1",
        ledgerPolicyVersion = "PRIMARY_ONLY_V1",
        programStatePolicyVersion = "PROGRAM_STATE_V1",
        adaptationState = "UNCALIBRATED",
        weekStartEpochDay = 20_696L,
        timeZoneId = "America/Mexico_City",
        profileRevision = 0L,
        contextIdentity = "context-identity",
        reasonCodes = "WEEKLY_ALLOWANCE_EXCEEDED",
        doseAccounting = "wallcrawl-recommendation-dose-v1\nChest\t4\t2\t6",
        recordedAtTimestamp = 1_000L
    )

    private companion object {
        const val PROFILE_ID = "default_user"
        const val SESSION_ID = "session-under-test"
        const val EXERCISE_ID = "exercise-under-test"
    }
}
