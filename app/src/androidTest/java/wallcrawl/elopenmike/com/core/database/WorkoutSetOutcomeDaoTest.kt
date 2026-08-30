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
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.database.entity.UserProfileEntity
import wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutRepository
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.SetOutcome
import wallcrawl.elopenmike.com.core.model.SetPerformanceInput
import wallcrawl.elopenmike.com.core.model.SetStopReason
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.model.outcome

/**
 * Proves the typed set outcome survives a real Room round trip: one guarded, atomic
 * write, an active-session-only guard, and no contradictory intermediate state.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutSetOutcomeDaoTest {

    private lateinit var database: WallCrawlDatabase
    private lateinit var repository: OfflineWorkoutRepository

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WallCrawlDatabase::class.java
        ).build()
        database.userProfileDao().insertOrUpdate(profileEntity())
        repository = OfflineWorkoutRepository(
            sessionDao = database.workoutSessionDao(),
            setDao = database.workoutSetDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun completedSet_roundTripsEveryTypedOutcomeField() = runBlocking {
        val setId = startSingleSetSession()

        repository.logSetCompletion(
            setId,
            SetPerformanceInput(
                reps = 9,
                weight = 42.5,
                rpe = 8.5f,
                rir = 1,
                feltManageable = true,
                completedAtTimestamp = COMPLETED_AT,
                isCompleted = true
            )
        )

        val set = readSet(setId)
        assertThat(set.isCompleted).isTrue()
        assertThat(set.completedReps).isEqualTo(9)
        assertThat(set.completedWeight).isEqualTo(42.5)
        assertThat(set.outcome).isEqualTo(
            SetOutcome.Completed(
                recordedAtTimestamp = COMPLETED_AT,
                rpe = 8.5f,
                rir = 1,
                feltManageable = true
            )
        )
        assertForeignKeysIntact()
    }

    @Test
    fun completedSetWithoutEffortFeedback_keepsNullAsNull() = runBlocking {
        val setId = startSingleSetSession()

        repository.logSetCompletion(
            setId,
            SetPerformanceInput(
                reps = 9,
                weight = 42.5,
                completedAtTimestamp = COMPLETED_AT,
                isCompleted = true
            )
        )

        val set = readSet(setId)
        assertThat(set.rpe).isNull()
        assertThat(set.rir).isNull()
        assertThat(set.feltManageable).isNull()
        assertThat(set.stopReason).isNull()
        assertThat(set.stoppedAtTimestamp).isNull()
    }

    @Test
    fun clearingCompletion_clearsCompletionOnlyFeedbackInTheSameWrite() = runBlocking {
        val setId = startSingleSetSession()
        repository.logSetCompletion(
            setId,
            SetPerformanceInput(
                reps = 9,
                weight = 42.5,
                rpe = 8f,
                rir = 2,
                feltManageable = true,
                completedAtTimestamp = COMPLETED_AT,
                isCompleted = true
            )
        )

        repository.logSetCompletion(
            setId,
            SetPerformanceInput(reps = 9, weight = 42.5, isCompleted = false)
        )

        val set = readSet(setId)
        assertThat(set.isCompleted).isFalse()
        assertThat(set.completedAtTimestamp).isNull()
        assertThat(set.feltManageable).isNull()
        assertThat(set.rpe).isNull()
        assertThat(set.rir).isNull()
        assertThat(set.outcome).isEqualTo(SetOutcome.NotRecorded)
    }

    @Test
    fun editingAStillCompletedSet_keepsItsRecordedFeedback() = runBlocking {
        val setId = startSingleSetSession()
        repository.logSetCompletion(
            setId,
            SetPerformanceInput(
                reps = 9,
                weight = 42.5,
                rpe = 8f,
                rir = 2,
                feltManageable = true,
                completedAtTimestamp = COMPLETED_AT,
                isCompleted = true
            )
        )

        repository.logSetCompletion(
            setId,
            SetPerformanceInput(
                reps = 10,
                weight = 45.0,
                rpe = 8f,
                rir = 2,
                feltManageable = true,
                completedAtTimestamp = COMPLETED_AT,
                isCompleted = true
            )
        )

        val set = readSet(setId)
        assertThat(set.completedReps).isEqualTo(10)
        assertThat(set.completedWeight).isEqualTo(45.0)
        assertThat(set.feltManageable).isTrue()
        assertThat(set.completedAtTimestamp).isEqualTo(COMPLETED_AT)
    }

    @Test
    fun stoppedSet_staysDistinguishableFromASetThatWasNeverStarted() = runBlocking {
        val setId = startSingleSetSession(targetSets = 2)
        val untouchedSetId = allSetIds().first { it != setId }

        repository.logSetCompletion(
            setId,
            SetPerformanceInput(
                stopReason = SetStopReason.PAIN_STOP,
                stoppedAtTimestamp = STOPPED_AT,
                isCompleted = false
            )
        )

        val stopped = readSet(setId)
        assertThat(stopped.isCompleted).isFalse()
        assertThat(stopped.isResolved).isTrue()
        assertThat(stopped.outcome).isEqualTo(
            SetOutcome.Stopped(
                reason = SetStopReason.PAIN_STOP,
                recordedAtTimestamp = STOPPED_AT
            )
        )
        assertThat(readSet(untouchedSetId).outcome).isEqualTo(SetOutcome.NotRecorded)

        // Neither a stopped nor an untouched set contributes any volume.
        val session = checkNotNull(repository.getSessionById(sessionId))
        assertThat(session.totalVolume).isEqualTo(0.0)
        assertThat(session.completedSetsCount).isEqualTo(0)
    }

    @Test
    fun rejectedOutcome_writesNothingAtAll() = runBlocking {
        val setId = startSingleSetSession()
        repository.logSetCompletion(
            setId,
            SetPerformanceInput(
                reps = 9,
                weight = 42.5,
                rpe = 8f,
                completedAtTimestamp = COMPLETED_AT,
                isCompleted = true
            )
        )

        try {
            repository.logSetCompletion(
                setId,
                SetPerformanceInput(
                    reps = 11,
                    weight = 50.0,
                    rpe = 99f,
                    completedAtTimestamp = COMPLETED_AT,
                    isCompleted = true
                )
            )
            fail("Expected an out-of-range rpe to be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected rejection.
        }

        val set = readSet(setId)
        assertThat(set.completedReps).isEqualTo(9)
        assertThat(set.completedWeight).isEqualTo(42.5)
        assertThat(set.rpe).isEqualTo(8f)
    }

    @Test
    fun setBelongingToACompletedSession_cannotChange() = runBlocking {
        val setId = startSingleSetSession()
        repository.logSetCompletion(
            setId,
            SetPerformanceInput(
                reps = 9,
                weight = 42.5,
                rpe = 8f,
                completedAtTimestamp = COMPLETED_AT,
                isCompleted = true
            )
        )
        repository.completeWorkout(sessionId, actualDurationMinutes = 30)

        try {
            repository.logSetCompletion(
                setId,
                SetPerformanceInput(
                    reps = 20,
                    weight = 90.0,
                    rpe = 10f,
                    feltManageable = false,
                    completedAtTimestamp = COMPLETED_AT + 1,
                    isCompleted = true
                )
            )
            fail("Expected a write into a completed session to be rejected")
        } catch (_: IllegalStateException) {
            // Expected rejection.
        }

        val set = readSet(setId)
        assertThat(set.completedReps).isEqualTo(9)
        assertThat(set.rpe).isEqualTo(8f)
        assertThat(set.feltManageable).isNull()
    }

    @Test
    fun concurrentIdenticalCompletions_leaveOneConsistentRow() = runBlocking {
        val setId = startSingleSetSession()
        val performance = SetPerformanceInput(
            reps = 9,
            weight = 42.5,
            completedAtTimestamp = COMPLETED_AT,
            isCompleted = true
        )

        listOf(1, 2, 3).map {
            async(Dispatchers.IO) { repository.logSetCompletion(setId, performance) }
        }.awaitAll()

        val set = readSet(setId)
        assertThat(set.isCompleted).isTrue()
        assertThat(set.completedReps).isEqualTo(9)
        assertThat(set.completedAtTimestamp).isEqualTo(COMPLETED_AT)
        assertThat(allSetIds()).hasSize(1)
    }

    @Test
    fun unknownPersistedStopReason_isRejectedInsteadOfBeingGuessed() = runBlocking {
        val setId = startSingleSetSession()
        database.openHelper.writableDatabase.execSQL(
            "UPDATE workout_sets SET stopReason = 'NOT_A_REAL_REASON' WHERE id = ?",
            arrayOf<Any>(setId)
        )

        try {
            database.workoutSetDao().getSetById(setId)
            fail("Expected an unknown persisted stop reason to be rejected")
        } catch (exception: IllegalArgumentException) {
            assertThat(exception.message).contains("stopReason")
        }
        Unit
    }

    @Test
    fun everyExerciseType_persistsItsOwnTypedOutcome() = runBlocking {
        val generated = GeneratedWorkout(
            name = "All types",
            focusMuscles = listOf("Full body"),
            estimatedDurationMinutes = 45,
            exercises = listOf(
                planned(
                    "dumbbell-curl",
                    ExercisePrescription(
                        exerciseType = ExerciseType.WEIGHT_REPS,
                        targetSets = 1,
                        repRange = RepRange(8, 12),
                        targetWeight = 20.0
                    )
                ),
                planned(
                    "push-up",
                    ExercisePrescription(
                        exerciseType = ExerciseType.BODYWEIGHT_REPS,
                        targetSets = 1,
                        repRange = RepRange(8, 15)
                    )
                ),
                planned(
                    "assisted-pull-up",
                    ExercisePrescription(
                        exerciseType = ExerciseType.ASSISTED_BODYWEIGHT,
                        targetSets = 1,
                        repRange = RepRange(6, 10),
                        targetAssistanceWeight = 35.0
                    )
                ),
                planned(
                    "plank",
                    ExercisePrescription(
                        exerciseType = ExerciseType.DURATION,
                        targetSets = 1,
                        targetDurationSeconds = 45
                    )
                ),
                planned(
                    "walking",
                    ExercisePrescription(
                        exerciseType = ExerciseType.DISTANCE_DURATION,
                        targetSets = 1,
                        targetDurationSeconds = 600,
                        targetDistanceMeters = 1_000.0
                    )
                )
            )
        )
        val session = repository.startWorkoutFromGenerated(generated, UserProfile())
        val setsByType = session.exercises.associate { exercise ->
            exercise.prescription.exerciseType to exercise.sets.single().id
        }
        val performanceByType = mapOf(
            ExerciseType.WEIGHT_REPS to SetPerformanceInput(
                reps = 12,
                weight = 20.0,
                rpe = 7f,
                rir = 3,
                feltManageable = true,
                completedAtTimestamp = COMPLETED_AT,
                isCompleted = true
            ),
            ExerciseType.BODYWEIGHT_REPS to SetPerformanceInput(
                reps = 15,
                rir = 2,
                completedAtTimestamp = COMPLETED_AT,
                isCompleted = true
            ),
            ExerciseType.ASSISTED_BODYWEIGHT to SetPerformanceInput(
                reps = 8,
                assistanceWeight = 30.0,
                rpe = 9f,
                completedAtTimestamp = COMPLETED_AT,
                isCompleted = true
            ),
            ExerciseType.DURATION to SetPerformanceInput(
                durationSeconds = 50,
                feltManageable = false,
                completedAtTimestamp = COMPLETED_AT,
                isCompleted = true
            ),
            ExerciseType.DISTANCE_DURATION to SetPerformanceInput(
                durationSeconds = 580,
                distanceMeters = 1_000.0,
                completedAtTimestamp = COMPLETED_AT,
                isCompleted = true
            )
        )
        performanceByType.forEach { (type, performance) ->
            repository.logSetCompletion(setsByType.getValue(type), performance)
        }

        val persisted = checkNotNull(repository.getSessionById(session.id))
            .exercises
            .associate { it.prescription.exerciseType to it.sets.single() }
        assertThat(persisted.getValue(ExerciseType.WEIGHT_REPS).outcome).isEqualTo(
            SetOutcome.Completed(COMPLETED_AT, rpe = 7f, rir = 3, feltManageable = true)
        )
        assertThat(persisted.getValue(ExerciseType.BODYWEIGHT_REPS).outcome).isEqualTo(
            SetOutcome.Completed(COMPLETED_AT, rir = 2)
        )
        assertThat(persisted.getValue(ExerciseType.ASSISTED_BODYWEIGHT).completedAssistanceWeight)
            .isEqualTo(30.0)
        assertThat(persisted.getValue(ExerciseType.DURATION).outcome).isEqualTo(
            SetOutcome.Completed(COMPLETED_AT, feltManageable = false)
        )
        assertThat(persisted.getValue(ExerciseType.DISTANCE_DURATION).completedDistanceMeters)
            .isEqualTo(1_000.0)
        assertForeignKeysIntact()
    }

    private lateinit var sessionId: String

    private suspend fun startSingleSetSession(targetSets: Int = 1): String {
        val session = repository.startWorkoutFromGenerated(
            GeneratedWorkout(
                name = "Session",
                focusMuscles = listOf("Chest"),
                estimatedDurationMinutes = 30,
                exercises = listOf(
                    planned(
                        "incline-dumbbell-press",
                        ExercisePrescription(
                            exerciseType = ExerciseType.WEIGHT_REPS,
                            targetSets = targetSets,
                            repRange = RepRange(8, 10),
                            targetWeight = 40.0
                        )
                    )
                )
            ),
            UserProfile()
        )
        sessionId = session.id
        return session.exercises.single().sets.first().id
    }

    private suspend fun readSet(setId: String): WorkoutSet =
        checkNotNull(repository.getSessionById(sessionId))
            .exercises
            .flatMap { it.sets }
            .single { it.id == setId }

    private suspend fun allSetIds(): List<String> =
        checkNotNull(repository.getSessionById(sessionId)).exercises.flatMap { ex ->
            ex.sets.map { it.id }
        }

    private fun assertForeignKeysIntact() {
        database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
            assertThat(cursor.count).isEqualTo(0)
        }
    }

    private fun planned(id: String, prescription: ExercisePrescription) = PlannedExercise(
        exerciseId = id,
        prescription = prescription
    )

    private fun profileEntity() = UserProfileEntity(
        id = UserProfile.DEFAULT_PROFILE_ID,
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
        const val COMPLETED_AT = 1_777_777L
        const val STOPPED_AT = 1_888_888L
    }
}
