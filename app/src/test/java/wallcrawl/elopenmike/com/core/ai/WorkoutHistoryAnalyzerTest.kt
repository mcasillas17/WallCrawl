package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet

class WorkoutHistoryAnalyzerTest {

    private val analyzer = WorkoutHistoryAnalyzer()

    @Test
    fun exerciseHistory_usesLatestCompletedSessionForRecentPerformance() {
        val older = completedSession(
            id = "older",
            completedAtTimestamp = 1_000L,
            exerciseId = "incline-dumbbell-press",
            sets = listOf(completedSet("older-set", 1, weight = 45.0, reps = 10))
        )
        val newer = completedSession(
            id = "newer",
            completedAtTimestamp = 2_000L,
            exerciseId = "incline-dumbbell-press",
            sets = listOf(
                completedSet("newer-set-1", 1, weight = 50.0, reps = 10),
                completedSet("newer-set-2", 2, weight = 50.0, reps = 9),
                completedSet("newer-set-3", 3, weight = 50.0, reps = 8)
            )
        )

        val history = analyzer.exerciseHistory(listOf(older, newer))

        val inclinePress = history.getValue("incline-dumbbell-press")
        assertThat(inclinePress.lastWeight).isEqualTo(50.0)
        assertThat(inclinePress.lastReps).isEqualTo(8)
        assertThat(inclinePress.recentSets.map { it.id })
            .containsExactly("newer-set-1", "newer-set-2", "newer-set-3")
            .inOrder()
    }

    @Test
    fun exerciseHistory_calculatesBestEstimatedOneRepMaxAcrossSessions() {
        val higherReps = completedSession(
            id = "higher-reps",
            completedAtTimestamp = 2_000L,
            exerciseId = "barbell-bench-press",
            sets = listOf(completedSet("set-1", 1, weight = 100.0, reps = 10))
        )
        val heavierWeight = completedSession(
            id = "heavier-weight",
            completedAtTimestamp = 1_000L,
            exerciseId = "barbell-bench-press",
            sets = listOf(completedSet("set-2", 1, weight = 120.0, reps = 3))
        )

        val history = analyzer.exerciseHistory(listOf(heavierWeight, higherReps))

        assertThat(history.getValue("barbell-bench-press").bestEstimated1RM)
            .isWithin(0.001)
            .of(133.333)
    }

    @Test
    fun exerciseHistory_convertsPersistedWeightsToRequestedPlannerUnit() {
        val metricSession = completedSession(
            id = "metric",
            completedAtTimestamp = 2_000L,
            exerciseId = "incline-dumbbell-press",
            sets = listOf(completedSet("metric-set", 1, weight = 50.0, reps = 10)),
            weightUnit = WeightUnit.KG
        )

        val history = analyzer.exerciseHistory(
            sessions = listOf(metricSession),
            targetWeightUnit = WeightUnit.LBS
        )

        assertThat(history.getValue("incline-dumbbell-press").lastWeight)
            .isWithin(0.001)
            .of(110.231)
    }

    @Test
    fun exerciseHistory_ignoresIncompleteAndCancelledPerformance() {
        val completed = completedSession(
            id = "completed",
            completedAtTimestamp = 1_000L,
            exerciseId = "pull-ups",
            sets = listOf(
                completedSet("completed-set", 1, weight = null, reps = 8),
                WorkoutSet(
                    id = "incomplete-set",
                    workoutExerciseId = "completed-exercise",
                    setNumber = 2,
                    targetReps = 8,
                    completedReps = 20,
                    completedWeight = 200.0,
                    isCompleted = false
                )
            )
        )
        val cancelled = completed.copy(
            id = "cancelled",
            status = SessionStatus.CANCELLED,
            completedAtTimestamp = 2_000L
        )

        val history = analyzer.exerciseHistory(listOf(cancelled, completed))

        assertThat(history.getValue("pull-ups").recentSets.map { it.id })
            .containsExactly("completed-set")
        assertThat(history.getValue("pull-ups").bestEstimated1RM).isNull()
    }

    @Test
    fun recentlyTrainedMuscles_includesOnlyCompletedSessionsInsideLookback() {
        val now = 10 * HOUR_MILLIS
        val recent = completedSession(
            id = "recent",
            completedAtTimestamp = now - HOUR_MILLIS,
            exerciseId = "incline-dumbbell-press",
            sets = emptyList(),
            focusMuscles = listOf("Chest", "Triceps")
        )
        val old = completedSession(
            id = "old",
            completedAtTimestamp = now - (73 * HOUR_MILLIS),
            exerciseId = "barbell-back-squat",
            sets = emptyList(),
            focusMuscles = listOf("Quadriceps")
        )
        val active = recent.copy(
            id = "active",
            status = SessionStatus.IN_PROGRESS,
            focusMuscles = listOf("Back")
        )

        val muscles = analyzer.recentlyTrainedMuscles(
            sessions = listOf(old, active, recent),
            nowTimestamp = now,
            lookbackHours = 72
        )

        assertThat(muscles).containsExactly("Chest", "Triceps").inOrder()
    }

    @Test
    fun latestCompletedExercisePerformance_returnsStructuredSetsFromNewestMatchingSession() {
        val older = completedSession(
            id = "older",
            completedAtTimestamp = 1_000L,
            exerciseId = "incline-dumbbell-press",
            sets = listOf(completedSet("older-set", 1, weight = 45.0, reps = 10))
        )
        val newer = completedSession(
            id = "newer",
            completedAtTimestamp = 2_000L,
            exerciseId = "incline-dumbbell-press",
            sets = listOf(
                completedSet("newer-set-1", 1, weight = 50.0, reps = 9),
                completedSet("newer-set-2", 2, weight = 50.0, reps = 8)
            )
        )

        val performance = analyzer.latestCompletedExercisePerformance(
            sessions = listOf(older, newer),
            exerciseId = "incline-dumbbell-press"
        )

        assertThat(performance?.sessionCompletedAtTimestamp).isEqualTo(2_000L)
        assertThat(performance?.weightUnit).isEqualTo(WeightUnit.LBS)
        assertThat(performance?.sets?.map { it.id })
            .containsExactly("newer-set-1", "newer-set-2")
            .inOrder()
    }

    private fun completedSession(
        id: String,
        completedAtTimestamp: Long,
        exerciseId: String,
        sets: List<WorkoutSet>,
        focusMuscles: List<String> = listOf("Chest"),
        weightUnit: WeightUnit = WeightUnit.LBS
    ): WorkoutSession {
        val workoutExerciseId = "$id-exercise"
        return WorkoutSession(
            id = id,
            name = "Workout $id",
            completedAtTimestamp = completedAtTimestamp,
            weightUnit = weightUnit,
            status = SessionStatus.COMPLETED,
            focusMuscles = focusMuscles,
            exercises = listOf(
                WorkoutExercise(
                    id = workoutExerciseId,
                    sessionId = id,
                    exerciseId = exerciseId,
                    orderIndex = 0,
                    targetSets = maxOf(1, sets.size),
                    targetRepMin = 8,
                    targetRepMax = 10,
                    sets = sets.map { it.copy(workoutExerciseId = workoutExerciseId) }
                )
            )
        )
    }

    private fun completedSet(
        id: String,
        setNumber: Int,
        weight: Double?,
        reps: Int
    ) = WorkoutSet(
        id = id,
        workoutExerciseId = "exercise",
        setNumber = setNumber,
        targetReps = reps,
        completedReps = reps,
        targetWeight = weight,
        completedWeight = weight,
        isCompleted = true
    )

    private companion object {
        const val HOUR_MILLIS = 60 * 60 * 1_000L
    }
}
