package wallcrawl.elopenmike.com.core.database.repository

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import wallcrawl.elopenmike.com.core.database.dao.WorkoutSessionDao
import wallcrawl.elopenmike.com.core.database.dao.WorkoutSetDao
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSessionEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSetEntity
import wallcrawl.elopenmike.com.core.database.relation.WorkoutSessionWithExercisesAndSets
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.SetPerformanceInput

class WorkoutRepositoryTest {

    private lateinit var setDao: RecordingWorkoutSetDao
    private lateinit var repository: OfflineWorkoutRepository

    @Before
    fun setUp() {
        setDao = RecordingWorkoutSetDao()
        repository = OfflineWorkoutRepository(
            sessionDao = EmptyWorkoutSessionDao(),
            setDao = setDao
        )
    }

    @Test
    fun logSetCompletion_completedSetWithoutPositiveReps_isRejectedBeforePersistence() = runTest {
        assertIllegalArgument {
            repository.logSetCompletion(
                setId = "set-id",
                reps = 0,
                weight = 20.0,
                isCompleted = true
            )
        }

        assertThat(setDao.completionUpdates).isEmpty()
    }

    @Test
    fun logSetCompletion_negativeOrNonFiniteWeight_isRejectedBeforePersistence() = runTest {
        listOf(-0.1, Double.NaN, Double.POSITIVE_INFINITY).forEach { invalidWeight ->
            assertIllegalArgument {
                repository.logSetCompletion(
                    setId = "set-id",
                    reps = 8,
                    weight = invalidWeight,
                    isCompleted = false
                )
            }
        }

        assertThat(setDao.completionUpdates).isEmpty()
    }

    @Test
    fun logSetCompletion_implausiblyLargeValuesAreRejectedBeforePersistence() = runTest {
        assertIllegalArgument {
            repository.logSetCompletion(
                setId = "set-id",
                reps = 1_001,
                weight = 20.0,
                isCompleted = true
            )
        }
        assertIllegalArgument {
            repository.logSetCompletion(
                setId = "set-id",
                reps = 8,
                weight = 100_001.0,
                isCompleted = true
            )
        }

        assertThat(setDao.completionUpdates).isEmpty()
    }

    @Test
    fun logSetCompletion_completingWeightRepsSetWithoutAPositiveLoad_isRejectedBeforePersistence() = runTest {
        // With no fabricated default weight anymore (Task 2), a completed weight-and-reps
        // set could otherwise silently persist with no load recorded at all. Completion
        // must require a user-entered positive load.
        assertIllegalArgument {
            repository.logSetCompletion(
                setId = "set-id",
                reps = 8,
                weight = null,
                isCompleted = true
            )
        }
        assertIllegalArgument {
            repository.logSetCompletion(
                setId = "set-id",
                reps = 8,
                weight = 0.0,
                isCompleted = true
            )
        }

        assertThat(setDao.completionUpdates).isEmpty()
    }

    @Test
    fun logSetCompletion_incompletePartialEdit_isPersisted() = runTest {
        repository.logSetCompletion(
            setId = "set-id",
            reps = null,
            weight = null,
            isCompleted = false
        )

        assertThat(setDao.completionUpdates)
            .containsExactly(SetCompletionUpdate("set-id", null, null, false))
    }

    @Test
    fun logSetCompletion_unknownSetId_isRejected() = runTest {
        setDao.affectedRows = 0

        try {
            repository.logSetCompletion(
                setId = "missing-set",
                reps = 8,
                weight = 20.0,
                isCompleted = true
            )
            fail("Expected an unknown set to be rejected")
        } catch (exception: IllegalStateException) {
            assertThat(exception.message).contains("missing-set")
        }
    }

    @Test
    fun logSetCompletion_durationRejectsRepetitionsBeforePersistence() = runTest {
        setDao.exerciseType = ExerciseType.DURATION

        assertIllegalArgument {
            repository.logSetCompletion(
                setId = "duration-set",
                performance = SetPerformanceInput(
                    reps = 10,
                    durationSeconds = 45,
                    isCompleted = true
                )
            )
        }

        assertThat(setDao.completionUpdates).isEmpty()
    }

    @Test
    fun logSetCompletion_completedDistanceSetRequiresDistanceOrDuration() = runTest {
        setDao.exerciseType = ExerciseType.DISTANCE_DURATION

        assertIllegalArgument {
            repository.logSetCompletion(
                setId = "distance-set",
                performance = SetPerformanceInput(isCompleted = true)
            )
        }

        assertThat(setDao.completionUpdates).isEmpty()
    }

    @Test
    fun logSetCompletion_assistedSetPersistsAssistanceInsteadOfLoad() = runTest {
        setDao.exerciseType = ExerciseType.ASSISTED_BODYWEIGHT

        repository.logSetCompletion(
            setId = "assisted-set",
            performance = SetPerformanceInput(
                reps = 8,
                assistanceWeight = 30.0,
                isCompleted = true
            )
        )

        assertThat(setDao.completionUpdates.single().assistanceWeight).isEqualTo(30.0)
        assertThat(setDao.completionUpdates.single().weight).isNull()
    }

    @Test
    fun completeWorkout_unknownSession_isRejected() = runTest {
        try {
            repository.completeWorkout(sessionId = "missing", actualDurationMinutes = 30)
            fail("Expected an unknown session to be rejected")
        } catch (exception: IllegalStateException) {
            assertThat(exception.message).contains("missing")
        }
    }

    @Test
    fun completeWorkout_nonPositiveDuration_isRejected() = runTest {
        assertIllegalArgument {
            repository.completeWorkout(sessionId = "session-id", actualDurationMinutes = 0)
        }
    }

    private suspend fun assertIllegalArgument(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected rejection.
        }
    }
}

private data class SetCompletionUpdate(
    val setId: String,
    val reps: Int?,
    val weight: Double?,
    val isCompleted: Boolean,
    val assistanceWeight: Double? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null
)

private class RecordingWorkoutSetDao : WorkoutSetDao {
    val completionUpdates = mutableListOf<SetCompletionUpdate>()
    var affectedRows: Int = 1
    var exerciseType: ExerciseType = ExerciseType.WEIGHT_REPS

    override suspend fun insertSets(sets: List<WorkoutSetEntity>) = Unit
    override suspend fun insertOrUpdateSet(set: WorkoutSetEntity) = Unit
    override suspend fun getSetById(setId: String): WorkoutSetEntity? = WorkoutSetEntity(
        id = setId,
        workoutExerciseId = "exercise",
        setNumber = 1,
        exerciseType = exerciseType,
        targetReps = 10,
        completedReps = null,
        targetWeight = 20.0,
        completedWeight = null,
        isCompleted = false,
        rpe = null,
        rir = null,
        type = SetType.NORMAL
    )

    override suspend fun updateSetCompletion(
        setId: String,
        reps: Int?,
        weight: Double?,
        assistanceWeight: Double?,
        durationSeconds: Int?,
        distanceMeters: Double?,
        isCompleted: Boolean,
        requiredStatus: SessionStatus
    ): Int {
        completionUpdates += SetCompletionUpdate(
            setId = setId,
            reps = reps,
            weight = weight,
            isCompleted = isCompleted,
            assistanceWeight = assistanceWeight,
            durationSeconds = durationSeconds,
            distanceMeters = distanceMeters
        )
        return affectedRows
    }

    override suspend fun getSetsForExercise(workoutExerciseId: String): List<WorkoutSetEntity> =
        emptyList()
}

private class EmptyWorkoutSessionDao : WorkoutSessionDao {
    override fun observeSessionWithDetails(
        sessionId: String
    ): Flow<WorkoutSessionWithExercisesAndSets?> = flowOf(null)

    override suspend fun getSessionWithDetails(
        sessionId: String
    ): WorkoutSessionWithExercisesAndSets? = null

    override fun observeActiveSession(
        status: SessionStatus
    ): Flow<WorkoutSessionWithExercisesAndSets?> = flowOf(null)

    override suspend fun getActiveSession(
        status: SessionStatus
    ): WorkoutSessionWithExercisesAndSets? = null

    override fun observeRecentCompletedSessions(
        limit: Int,
        status: SessionStatus
    ): Flow<List<WorkoutSessionWithExercisesAndSets>> = flowOf(emptyList())

    override fun observeCompletedSessionCount(status: SessionStatus): Flow<Int> = flowOf(0)

    override fun observeCompletedSessionCountSince(
        startTimestamp: Long,
        status: SessionStatus
    ): Flow<Int> = flowOf(0)

    override suspend fun getProfileRevision(profileId: String): Long? = null

    override suspend fun getRecentCompletedSessions(
        limit: Int,
        status: SessionStatus
    ): List<WorkoutSessionWithExercisesAndSets> = emptyList()

    override fun observeAllSessions(): Flow<List<WorkoutSessionWithExercisesAndSets>> =
        flowOf(emptyList())

    override suspend fun insertSession(session: WorkoutSessionEntity) = Unit
    override suspend fun insertWorkoutExercises(
        exercises: List<wallcrawl.elopenmike.com.core.database.entity.WorkoutExerciseEntity>
    ) = Unit

    override suspend fun insertWorkoutSets(sets: List<WorkoutSetEntity>) = Unit
    override suspend fun updateSession(session: WorkoutSessionEntity) = Unit

    override suspend fun completeSessionIfActive(
        sessionId: String,
        completedStatus: SessionStatus,
        requiredStatus: SessionStatus,
        completedAt: Long,
        actualDuration: Int
    ): Int = 0

    override suspend fun deleteActiveSession(
        sessionId: String,
        requiredStatus: SessionStatus
    ): Int = 0
}
