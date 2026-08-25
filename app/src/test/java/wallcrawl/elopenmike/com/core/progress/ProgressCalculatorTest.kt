package wallcrawl.elopenmike.com.core.progress

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet

class ProgressCalculatorTest {

    private val calculator = ProgressCalculator()
    private val exercises = InMemoryExerciseCatalog.SAMPLE_EXERCISES
    private val profile = UserProfile(daysPerWeek = 4, preferredUnit = WeightUnit.LBS)

    @Test
    fun calculate_emptyHistoryReturnsZeroOverview() {
        val result = calculator.calculate(
            completedSessions = emptyList(),
            profile = profile,
            catalogExercises = exercises,
            nowTimestamp = NOW
        )

        assertThat(result.workoutsThisWeek).isEqualTo(0)
        assertThat(result.weeklyGoal).isEqualTo(4)
        assertThat(result.currentStreakWeeks).isEqualTo(0)
        assertThat(result.totalWorkoutsLogged).isEqualTo(0)
        assertThat(result.totalVolumeThisWeek).isEqualTo(0.0)
        assertThat(result.recentPersonalRecords).isEmpty()
        assertThat(result.muscleGroupFocus).isEmpty()
        assertThat(result.strengthTrends).isEmpty()
        assertThat(result.recentHistory).isEmpty()
    }

    @Test
    fun calculate_usesCompletedSetsForWeeklyVolumeAndMuscleSets() {
        val thisWeekPress = session(
            id = "this-week-press",
            completedAtTimestamp = NOW - DAY_MILLIS,
            exerciseId = "incline-dumbbell-press",
            sets = listOf(
                completedSet(1, 50.0, 10),
                completedSet(2, 50.0, 10),
                completedSet(3, 50.0, 10),
                incompleteSet(4, 200.0, 20)
            )
        )
        val thisWeekPullUps = session(
            id = "this-week-pull-ups",
            completedAtTimestamp = NOW - (3 * DAY_MILLIS),
            exerciseId = "pull-ups",
            sets = listOf(completedSet(1, null, 8), completedSet(2, null, 7))
        )
        val previousWeekPress = session(
            id = "previous-week-press",
            completedAtTimestamp = NOW - (8 * DAY_MILLIS),
            exerciseId = "incline-dumbbell-press",
            sets = listOf(completedSet(1, 45.0, 10), completedSet(2, 45.0, 10))
        )

        val result = calculator.calculate(
            completedSessions = listOf(previousWeekPress, thisWeekPullUps, thisWeekPress),
            profile = profile,
            catalogExercises = exercises,
            nowTimestamp = NOW
        )

        assertThat(result.workoutsThisWeek).isEqualTo(2)
        assertThat(result.totalWorkoutsLogged).isEqualTo(3)
        assertThat(result.totalVolumeThisWeek).isEqualTo(1_500.0)
        val chest = result.muscleGroupFocus.first { it.muscle == StandardMuscles.CHEST }
        assertThat(chest.setsThisWeek).isEqualTo(3)
        assertThat(chest.percentageGrowth).isEqualTo(50)
        val lats = result.muscleGroupFocus.first { it.muscle == StandardMuscles.LATS }
        assertThat(lats.setsThisWeek).isEqualTo(2)
    }

    @Test
    fun calculate_countsConsecutiveRollingWeekBuckets() {
        val sessions = listOf(
            session("week-0", NOW - DAY_MILLIS),
            session("week-1", NOW - (8 * DAY_MILLIS)),
            session("week-2", NOW - (15 * DAY_MILLIS)),
            session("week-4", NOW - (29 * DAY_MILLIS))
        )

        val result = calculator.calculate(sessions, profile, exercises, NOW)

        assertThat(result.currentStreakWeeks).isEqualTo(3)
    }

    @Test
    fun calculate_derivesRecentWeightRecordAndStrengthTrend() {
        val previous = session(
            id = "previous",
            completedAtTimestamp = NOW - (8 * DAY_MILLIS),
            exerciseId = "incline-dumbbell-press",
            sets = listOf(completedSet(1, 45.0, 10))
        )
        val current = session(
            id = "current",
            completedAtTimestamp = NOW - DAY_MILLIS,
            exerciseId = "incline-dumbbell-press",
            sets = listOf(completedSet(1, 50.0, 10))
        )

        val result = calculator.calculate(
            completedSessions = listOf(previous, current),
            profile = profile,
            catalogExercises = exercises,
            nowTimestamp = NOW
        )

        val record = result.recentPersonalRecords.single()
        assertThat(record.exerciseName).isEqualTo("Incline Dumbbell Press")
        assertThat(record.value).isEqualTo(50.0)
        assertThat(record.previousValue).isEqualTo(45.0)
        assertThat(record.unit).isEqualTo("lb")

        val trend = result.strengthTrends.single()
        assertThat(trend.exerciseName).isEqualTo("Incline Dumbbell Press")
        assertThat(trend.previousMetric).isEqualTo("45 lb × 10")
        assertThat(trend.currentMetric).isEqualTo("50 lb × 10")
        assertThat(trend.percentageChange).isEqualTo(11)
        assertThat(trend.isPositive).isTrue()
    }

    private fun session(
        id: String,
        completedAtTimestamp: Long,
        exerciseId: String = "incline-dumbbell-press",
        sets: List<WorkoutSet> = listOf(completedSet(1, 45.0, 10))
    ): WorkoutSession {
        val workoutExerciseId = "$id-exercise"
        return WorkoutSession(
            id = id,
            name = "Workout $id",
            completedAtTimestamp = completedAtTimestamp,
            actualDurationMinutes = 45,
            status = SessionStatus.COMPLETED,
            exercises = listOf(
                WorkoutExercise(
                    id = workoutExerciseId,
                    sessionId = id,
                    exerciseId = exerciseId,
                    orderIndex = 0,
                    targetSets = sets.size,
                    targetRepMin = 8,
                    targetRepMax = 10,
                    sets = sets.map { it.copy(workoutExerciseId = workoutExerciseId) }
                )
            )
        )
    }

    private fun completedSet(
        setNumber: Int,
        weight: Double?,
        reps: Int
    ) = WorkoutSet(
        id = "set-$setNumber-$weight-$reps",
        workoutExerciseId = "exercise",
        setNumber = setNumber,
        targetReps = reps,
        completedReps = reps,
        targetWeight = weight,
        completedWeight = weight,
        isCompleted = true
    )

    private fun incompleteSet(
        setNumber: Int,
        weight: Double,
        reps: Int
    ) = completedSet(setNumber, weight, reps).copy(isCompleted = false)

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1_000L
        const val NOW = 40 * DAY_MILLIS
    }
}
