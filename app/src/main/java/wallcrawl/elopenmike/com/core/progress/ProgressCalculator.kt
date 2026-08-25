package wallcrawl.elopenmike.com.core.progress

import kotlin.math.roundToInt
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.MuscleProgressStat
import wallcrawl.elopenmike.com.core.model.PersonalRecord
import wallcrawl.elopenmike.com.core.model.ProgressOverview
import wallcrawl.elopenmike.com.core.model.RecordType
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.StrengthTrend
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet

/** Calculates user-visible progress exclusively from persisted completed workout data. */
class ProgressCalculator {

    fun calculate(
        completedSessions: List<WorkoutSession>,
        profile: UserProfile,
        catalogExercises: List<Exercise>,
        nowTimestamp: Long
    ): ProgressOverview {
        val sessions = completedSessions
            .filter { session ->
                val completedAt = session.completedAtTimestamp
                session.status == SessionStatus.COMPLETED &&
                    completedAt != null &&
                    completedAt <= nowTimestamp
            }
            .sortedByDescending { it.completedAtTimestamp }
        val catalogById = catalogExercises.associateBy { it.id }
        val thisWeek = sessions.filter { session ->
            val age = nowTimestamp - requireNotNull(session.completedAtTimestamp)
            age in 0 until WEEK_MILLIS
        }
        val previousWeek = sessions.filter { session ->
            val age = nowTimestamp - requireNotNull(session.completedAtTimestamp)
            age in WEEK_MILLIS until (2 * WEEK_MILLIS)
        }

        return ProgressOverview(
            workoutsThisWeek = thisWeek.size,
            weeklyGoal = profile.daysPerWeek,
            currentStreakWeeks = calculateStreakWeeks(sessions, nowTimestamp),
            totalWorkoutsLogged = sessions.size,
            totalVolumeThisWeek = thisWeek.sumOf(::validCompletedVolume),
            recentPersonalRecords = calculateRecentRecords(
                sessions = sessions,
                catalogById = catalogById,
                unit = profile.preferredUnit.symbol
            ),
            muscleGroupFocus = calculateMuscleFocus(
                thisWeek = thisWeek,
                previousWeek = previousWeek,
                catalogById = catalogById
            ),
            strengthTrends = calculateStrengthTrends(
                sessions = sessions,
                catalogById = catalogById,
                unit = profile.preferredUnit.symbol
            ),
            recentHistory = sessions.take(MAX_RECENT_HISTORY)
        )
    }

    private fun calculateStreakWeeks(
        sessions: List<WorkoutSession>,
        nowTimestamp: Long
    ): Int {
        val occupiedWeekBuckets = sessions.mapNotNullTo(mutableSetOf()) { session ->
            val completedAt = session.completedAtTimestamp ?: return@mapNotNullTo null
            val age = nowTimestamp - completedAt
            if (age < 0) null else (age / WEEK_MILLIS).toInt()
        }

        var streak = 0
        while (streak in occupiedWeekBuckets) streak += 1
        return streak
    }

    private fun calculateMuscleFocus(
        thisWeek: List<WorkoutSession>,
        previousWeek: List<WorkoutSession>,
        catalogById: Map<String, Exercise>
    ): List<MuscleProgressStat> {
        val currentSetsByMuscle = completedSetsByPrimaryMuscle(thisWeek, catalogById)
        val previousSetsByMuscle = completedSetsByPrimaryMuscle(previousWeek, catalogById)

        return currentSetsByMuscle
            .map { (muscle, currentSets) ->
                val previousSets = previousSetsByMuscle[muscle] ?: 0
                val percentageGrowth = when {
                    previousSets == 0 && currentSets > 0 -> 100
                    previousSets == 0 -> 0
                    else -> (((currentSets - previousSets) * 100.0) / previousSets).roundToInt()
                }
                MuscleProgressStat(
                    muscle = muscle,
                    setsThisWeek = currentSets,
                    percentageGrowth = percentageGrowth
                )
            }
            .sortedWith(compareByDescending<MuscleProgressStat> { it.setsThisWeek }.thenBy { it.muscle })
            .take(MAX_MUSCLE_STATS)
    }

    private fun completedSetsByPrimaryMuscle(
        sessions: List<WorkoutSession>,
        catalogById: Map<String, Exercise>
    ): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        sessions.forEach { session ->
            session.exercises.forEach { workoutExercise ->
                val completedSetCount = workoutExercise.sets.count { it.isValidCompletedSet() }
                if (completedSetCount == 0) return@forEach
                catalogById[workoutExercise.exerciseId]
                    ?.primaryMuscles
                    ?.filter { it.isNotBlank() }
                    ?.distinct()
                    ?.forEach { muscle ->
                        counts[muscle] = (counts[muscle] ?: 0) + completedSetCount
                    }
            }
        }
        return counts
    }

    private fun calculateRecentRecords(
        sessions: List<WorkoutSession>,
        catalogById: Map<String, Exercise>,
        unit: String
    ): List<PersonalRecord> {
        return performancesByExercise(sessions)
            .mapNotNull { (exerciseId, performances) ->
                val latest = performances.firstOrNull() ?: return@mapNotNull null
                val previous = performances.drop(1)
                if (previous.isEmpty()) return@mapNotNull null

                val latestWeight = latest.sets.mapNotNull { it.validPositiveWeight() }.maxOrNull()
                val previousWeight = previous
                    .asSequence()
                    .flatMap { it.sets.asSequence() }
                    .mapNotNull { it.validPositiveWeight() }
                    .maxOrNull()

                when {
                    latestWeight != null && previousWeight != null && latestWeight > previousWeight -> {
                        PersonalRecord(
                            exerciseId = exerciseId,
                            exerciseName = catalogById[exerciseId]?.name ?: exerciseId.toDisplayName(),
                            recordType = RecordType.WEIGHT,
                            value = latestWeight,
                            unit = unit,
                            achievedTimestamp = latest.completedAtTimestamp,
                            previousValue = previousWeight
                        )
                    }

                    latestWeight == null -> {
                        val latestReps = latest.sets.mapNotNull { it.completedReps }.maxOrNull()
                        val previousReps = previous
                            .asSequence()
                            .flatMap { it.sets.asSequence() }
                            .mapNotNull { it.completedReps }
                            .maxOrNull()
                        if (latestReps != null && previousReps != null && latestReps > previousReps) {
                            PersonalRecord(
                                exerciseId = exerciseId,
                                exerciseName = catalogById[exerciseId]?.name ?: exerciseId.toDisplayName(),
                                recordType = RecordType.REPS,
                                value = latestReps.toDouble(),
                                unit = "reps",
                                achievedTimestamp = latest.completedAtTimestamp,
                                previousValue = previousReps.toDouble()
                            )
                        } else {
                            null
                        }
                    }

                    else -> null
                }
            }
            .sortedByDescending { it.achievedTimestamp }
            .take(MAX_RECORDS)
    }

    private fun calculateStrengthTrends(
        sessions: List<WorkoutSession>,
        catalogById: Map<String, Exercise>,
        unit: String
    ): List<StrengthTrend> {
        return performancesByExercise(sessions)
            .mapNotNull { (exerciseId, performances) ->
                val current = performances.getOrNull(0)?.bestStrengthSet() ?: return@mapNotNull null
                val previous = performances.getOrNull(1)?.bestStrengthSet() ?: return@mapNotNull null
                val currentScore = current.strengthScore() ?: return@mapNotNull null
                val previousScore = previous.strengthScore() ?: return@mapNotNull null
                if (previousScore <= 0.0) return@mapNotNull null

                StrengthTrend(
                    exerciseId = exerciseId,
                    exerciseName = catalogById[exerciseId]?.name ?: exerciseId.toDisplayName(),
                    previousMetric = previous.performanceLabel(unit),
                    currentMetric = current.performanceLabel(unit),
                    percentageChange = (((currentScore - previousScore) / previousScore) * 100.0)
                        .roundToInt(),
                    isPositive = currentScore >= previousScore
                )
            }
            .sortedByDescending { it.percentageChange }
            .take(MAX_STRENGTH_TRENDS)
    }

    private fun performancesByExercise(
        sessions: List<WorkoutSession>
    ): Map<String, List<ExerciseSessionPerformance>> {
        return sessions
            .flatMap { session ->
                session.exercises.mapNotNull { exercise ->
                    val completedSets = exercise.sets.filter { it.isValidCompletedSet() }
                    if (completedSets.isEmpty()) {
                        null
                    } else {
                        ExerciseSessionPerformance(
                            exerciseId = exercise.exerciseId,
                            completedAtTimestamp = requireNotNull(session.completedAtTimestamp),
                            sets = completedSets
                        )
                    }
                }
            }
            .groupBy { it.exerciseId }
            .mapValues { (_, performances) ->
                performances.sortedByDescending { it.completedAtTimestamp }
            }
    }

    private fun validCompletedVolume(session: WorkoutSession): Double = session.exercises.sumOf { exercise ->
        exercise.sets
            .filter { it.isValidCompletedSet() }
            .sumOf { set -> (set.completedWeight ?: 0.0) * (set.completedReps ?: 0) }
    }

    private fun WorkoutSet.isValidCompletedSet(): Boolean =
        isCompleted &&
            completedReps != null &&
            completedReps > 0 &&
            (completedWeight == null || (completedWeight.isFinite() && completedWeight >= 0.0))

    private fun WorkoutSet.validPositiveWeight(): Double? =
        completedWeight?.takeIf { it.isFinite() && it > 0.0 }

    private fun WorkoutSet.strengthScore(): Double? {
        val reps = completedReps ?: return null
        val weight = validPositiveWeight()
        return if (weight == null) reps.toDouble() else weight * (1.0 + reps / 30.0)
    }

    private fun WorkoutSet.performanceLabel(unit: String): String {
        val reps = requireNotNull(completedReps)
        val weight = validPositiveWeight()
        return if (weight == null) {
            "$reps reps"
        } else {
            "${weight.toDisplayNumber()} $unit × $reps"
        }
    }

    private fun ExerciseSessionPerformance.bestStrengthSet(): WorkoutSet? =
        sets.maxByOrNull { it.strengthScore() ?: Double.NEGATIVE_INFINITY }

    private fun Double.toDisplayNumber(): String =
        if (this % 1.0 == 0.0) toInt().toString() else toString()

    private fun String.toDisplayName(): String =
        split('-').joinToString(" ") { word -> word.replaceFirstChar(Char::uppercase) }

    private data class ExerciseSessionPerformance(
        val exerciseId: String,
        val completedAtTimestamp: Long,
        val sets: List<WorkoutSet>
    )

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1_000L
        const val WEEK_MILLIS = 7 * DAY_MILLIS
        const val MAX_RECENT_HISTORY = 10
        const val MAX_MUSCLE_STATS = 4
        const val MAX_RECORDS = 3
        const val MAX_STRENGTH_TRENDS = 3
    }
}
