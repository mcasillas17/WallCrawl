package wallcrawl.elopenmike.com.core.database

import android.content.Context
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
import wallcrawl.elopenmike.com.core.database.entity.WorkoutExerciseEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSessionEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSetEntity
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetType

/**
 * The weekly range query has to return the *whole* week, in a stable order.
 */
@RunWith(AndroidJUnit4::class)
class CompletedWorkoutHistoryDaoTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: WallCrawlDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, WallCrawlDatabase::class.java)
            .addMigrations(*WallCrawlDatabase.ALL_MIGRATIONS)
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun everySessionInTheRangeIsReturned_notJustTheMostRecentEight(): Unit = runBlocking {
        repeat(20) { index ->
            insertSession(id = "session-%02d".format(index), completedAt = 1_000L + index)
        }

        val sessions = database.completedWorkoutHistoryDao()
            .getCompletedSessionsInRange(startEpochMillis = 1_000L, endEpochMillisExclusive = 2_000L)

        assertThat(sessions).hasSize(20)
    }

    @Test
    fun sessionsAreOrderedByCompletionThenBySessionIdAsAStableTieBreak(): Unit = runBlocking {
        insertSession(id = "zzz-same-instant", completedAt = 5_000L)
        insertSession(id = "aaa-same-instant", completedAt = 5_000L)
        insertSession(id = "mmm-earlier", completedAt = 4_000L)

        val ids = database.completedWorkoutHistoryDao()
            .getCompletedSessionsInRange(0L, 10_000L)
            .map { it.session.id }

        assertThat(ids)
            .containsExactly("mmm-earlier", "aaa-same-instant", "zzz-same-instant")
            .inOrder()
    }

    @Test
    fun theRangeIncludesItsStartAndExcludesItsEnd(): Unit = runBlocking {
        insertSession(id = "before", completedAt = 999L)
        insertSession(id = "at-start", completedAt = 1_000L)
        insertSession(id = "inside", completedAt = 1_500L)
        insertSession(id = "at-end", completedAt = 2_000L)

        val ids = database.completedWorkoutHistoryDao()
            .getCompletedSessionsInRange(1_000L, 2_000L)
            .map { it.session.id }

        assertThat(ids).containsExactly("at-start", "inside").inOrder()
    }

    @Test
    fun onlyCompletedSessionsAreReturned(): Unit = runBlocking {
        insertSession(id = "completed", completedAt = 1_000L)
        insertSession(id = "cancelled", completedAt = 1_100L, status = SessionStatus.CANCELLED)
        insertSession(id = "in-progress", completedAt = null, status = SessionStatus.IN_PROGRESS)

        val ids = database.completedWorkoutHistoryDao()
            .getCompletedSessionsInRange(0L, 10_000L)
            .map { it.session.id }

        assertThat(ids).containsExactly("completed")
    }

    @Test
    fun eachSessionCarriesItsExercisesAndSets(): Unit = runBlocking {
        insertSession(id = "session", completedAt = 1_000L, setCount = 3)

        val session = database.completedWorkoutHistoryDao()
            .getCompletedSessionsInRange(0L, 10_000L)
            .single()

        assertThat(session.exercisesWithSets).hasSize(1)
        assertThat(session.exercisesWithSets.single().sets).hasSize(3)
    }

    @Test
    fun anEmptyOrInvertedRangeIsRejectedRatherThanReturningAnEmptyWeek(): Unit = runBlocking {
        val dao = database.completedWorkoutHistoryDao()

        listOf(1_000L to 1_000L, 2_000L to 1_000L).forEach { (start, end) ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                runBlocking { dao.getCompletedSessionsInRange(start, end) }
            }
            assertThat(error).hasMessageThat().contains("endEpochMillisExclusive")
        }
    }

    private suspend fun insertSession(
        id: String,
        completedAt: Long?,
        status: SessionStatus = SessionStatus.COMPLETED,
        setCount: Int = 1
    ) {
        database.workoutSessionDao().insertWorkout(
            session = WorkoutSessionEntity(
                id = id,
                name = "Session $id",
                startedAtTimestamp = (completedAt ?: 0L) - 1L,
                completedAtTimestamp = completedAt,
                targetDurationMinutes = 45,
                actualDurationMinutes = 44,
                status = status,
                focusMusclesJson = "",
                notes = ""
            ),
            exercises = listOf(
                WorkoutExerciseEntity(
                    id = "$id-exercise",
                    sessionId = id,
                    exerciseId = "goblet-squat",
                    orderIndex = 0,
                    exerciseType = ExerciseType.WEIGHT_REPS,
                    targetSets = setCount,
                    targetRepMin = 8,
                    targetRepMax = 10,
                    targetWeight = 24.0,
                    notes = ""
                )
            ),
            sets = (1..setCount).map { setNumber ->
                WorkoutSetEntity(
                    id = "$id-set-$setNumber",
                    workoutExerciseId = "$id-exercise",
                    setNumber = setNumber,
                    exerciseType = ExerciseType.WEIGHT_REPS,
                    targetReps = 10,
                    completedReps = 10,
                    targetWeight = 24.0,
                    completedWeight = 24.0,
                    isCompleted = true,
                    rpe = null,
                    rir = null,
                    completedAtTimestamp = completedAt,
                    type = SetType.NORMAL
                )
            }
        )
    }
}
