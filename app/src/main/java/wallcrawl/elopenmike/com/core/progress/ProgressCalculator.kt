package wallcrawl.elopenmike.com.core.progress

import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.MuscleProgressStat
import wallcrawl.elopenmike.com.core.model.MuscleVocabulary
import wallcrawl.elopenmike.com.core.model.PersonalRecord
import wallcrawl.elopenmike.com.core.model.ProgressOverview
import wallcrawl.elopenmike.com.core.model.RecordType
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.StrengthPerformance
import wallcrawl.elopenmike.com.core.model.StrengthTrend
import wallcrawl.elopenmike.com.core.model.TrainingWeek
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.model.convertWeight

/** Calculates user-visible progress exclusively from persisted completed workout data. */
class ProgressCalculator {

    /**
     * @param weeklySessions completed sessions covering at least the current and previous
     *   calendar weeks, used for every weekly activity metric. Defaults to
     *   [completedSessions] for callers that pass their whole history.
     * @param completedTimestamps every completed session's completion time, used for the
     *   streak and all-time count so neither is capped by the bounded [completedSessions].
     */
    fun calculate(
        completedSessions: List<WorkoutSession>,
        profile: UserProfile,
        catalogExercises: List<Exercise>,
        nowTimestamp: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        weeklySessions: List<WorkoutSession> = completedSessions,
        completedTimestamps: List<Long> = completedSessions
            .filter { it.status == SessionStatus.COMPLETED }
            .mapNotNull { it.completedAtTimestamp }
    ): ProgressOverview {
        val currentWeek = TrainingWeek.containing(Instant.ofEpochMilli(nowTimestamp), zoneId)
        val previousWeek = TrainingWeek.startingOn(currentWeek.startEpochDay - 7, zoneId)

        // Bounded completed history for records, strength trends, and the recent list. Kept
        // in each session's own unit for history; converted only for the derived numbers.
        // This surface keeps its existing rule of not surfacing a completion dated ahead of
        // the clock; weekly activity below is the clock-skew-tolerant, ledger-matched view.
        val boundedSessions = completedSessions
            .filter {
                it.status == SessionStatus.COMPLETED &&
                    it.completedAtTimestamp != null &&
                    it.completedAtTimestamp <= nowTimestamp
            }
            .sortedByDescending { it.completedAtTimestamp }
        val boundedConverted = boundedSessions.map { it.convertWeightsTo(profile.preferredUnit) }
        val catalogById = catalogExercises.associateBy { it.id }

        // Weekly activity is membership by completion timestamp against the calendar week,
        // never against the wall clock, so a completion recorded ahead of "now" still counts.
        val weeklyCompleted = weeklySessions
            .filter { it.status == SessionStatus.COMPLETED && it.completedAtTimestamp != null }
        val thisWeek = weeklyCompleted
            .filter { currentWeek.contains(it.completedAtTimestamp!!) }
            .map { it.convertWeightsTo(profile.preferredUnit) }
        val previousWeekSessions = weeklyCompleted
            .filter { previousWeek.contains(it.completedAtTimestamp!!) }

        return ProgressOverview(
            workoutsThisWeek = thisWeek.size,
            weeklyGoal = profile.daysPerWeek,
            currentStreakWeeks = calculateStreakWeeks(completedTimestamps, currentWeek, zoneId),
            totalWorkoutsLogged = completedTimestamps.size,
            totalVolumeThisWeek = thisWeek.sumOf(::weeklyExternalLoadVolume),
            totalRepsThisWeek = thisWeek.sumOf(::weeklyCompletedReps),
            completedSetsThisWeek = thisWeek.sumOf { it.completedSetsCount },
            warmupSetsThisWeek = thisWeek.sumOf(::warmupSetCount),
            recentPersonalRecords = calculateRecentRecords(
                sessions = boundedConverted,
                catalogById = catalogById,
                unit = profile.preferredUnit.symbol
            ),
            legacyPrimaryActivity = calculateLegacyPrimaryActivity(
                thisWeek = thisWeek,
                previousWeek = previousWeekSessions,
                catalogById = catalogById
            ),
            strengthTrends = calculateStrengthTrends(
                sessions = boundedConverted,
                catalogById = catalogById
            ),
            recentHistory = boundedSessions.take(MAX_RECENT_HISTORY)
        )
    }

    /**
     * Counts exercises in [session] that beat every prior completed performance of the same
     * exercise, using the same rules as the Progress screen's records list: a heavier top set
     * for loaded work, more reps for bodyweight work, and no record without prior history to
     * beat. [session] must already carry its own completed sets; [priorCompletedSessions] may
     * include [session] itself, which is filtered out.
     */
    fun countPersonalRecords(
        session: WorkoutSession,
        priorCompletedSessions: List<WorkoutSession>
    ): Int {
        val bestByExercise = mutableMapOf<String, ExerciseBest>()
        priorCompletedSessions
            .asSequence()
            .filter { it.id != session.id && it.status == SessionStatus.COMPLETED }
            .map { it.convertWeightsTo(session.weightUnit) }
            .forEach { prior ->
                prior.exercises.forEach { exercise ->
                    val completedSets = exercise.sets.filter { it.isValidCompletedSet() }
                    if (completedSets.isEmpty()) return@forEach
                    val existing = bestByExercise[exercise.exerciseId]
                    bestByExercise[exercise.exerciseId] = ExerciseBest(
                        weight = maxOfNullable(
                            existing?.weight,
                            completedSets.mapNotNull { it.validPositiveWeight() }.maxOrNull()
                        ),
                        reps = maxOfNullable(
                            existing?.reps,
                            completedSets.mapNotNull { it.completedReps }.maxOrNull()
                        )
                    )
                }
            }

        // Grouped by exercise so the same lift entered twice in one session counts once,
        // matching how the Progress screen lists records.
        return session.exercises
            .groupBy { it.exerciseId }
            .count { (exerciseId, entries) ->
                val completedSets = entries.flatMap { entry ->
                    entry.sets.filter { it.isValidCompletedSet() }
                }
                if (completedSets.isEmpty()) return@count false
                val best = bestByExercise[exerciseId] ?: return@count false

                val topWeight = completedSets.mapNotNull { it.validPositiveWeight() }.maxOrNull()
                if (topWeight != null) {
                    best.weight != null && topWeight > best.weight
                } else {
                    val topReps = completedSets.mapNotNull { it.completedReps }.maxOrNull()
                    topReps != null && best.reps != null && topReps > best.reps
                }
            }
    }

    private fun calculateStreakWeeks(
        completedTimestamps: List<Long>,
        currentWeek: TrainingWeek,
        zoneId: ZoneId
    ): Int {
        val occupiedWeekStarts = completedTimestamps.mapTo(mutableSetOf()) { timestamp ->
            TrainingWeek.startEpochDayContaining(Instant.ofEpochMilli(timestamp), zoneId)
        }

        // Count from the current week when it already has work, otherwise from the previous
        // week: an unfinished, still-empty current week is grace rather than a broken streak.
        var cursor = if (currentWeek.startEpochDay in occupiedWeekStarts) {
            currentWeek.startEpochDay
        } else {
            currentWeek.startEpochDay - 7
        }
        var streak = 0
        while (cursor in occupiedWeekStarts) {
            streak += 1
            cursor -= 7
        }
        return streak
    }

    /**
     * Non-additive legacy-primary involvement for the current week against the previous
     * calendar week, over the union of muscles trained in either. A muscle trained last week
     * but not this week is kept so a reduction is visible rather than silently dropped.
     */
    private fun calculateLegacyPrimaryActivity(
        thisWeek: List<WorkoutSession>,
        previousWeek: List<WorkoutSession>,
        catalogById: Map<String, Exercise>
    ): List<MuscleProgressStat> {
        val currentSetsByMuscle = legacyPrimarySetsByMuscle(thisWeek, catalogById)
        val previousSetsByMuscle = legacyPrimarySetsByMuscle(previousWeek, catalogById)

        return (currentSetsByMuscle.keys + previousSetsByMuscle.keys)
            .map { muscle ->
                val current = currentSetsByMuscle[muscle] ?: 0
                val previous = previousSetsByMuscle[muscle] ?: 0
                MuscleProgressStat(
                    muscle = muscle,
                    setsThisWeek = current,
                    setsPreviousWeek = previous,
                    // No baseline to grow from is new activity, not an invented 100%.
                    percentageChange = if (previous == 0) {
                        null
                    } else {
                        (((current - previous) * 100.0) / previous).roundToInt()
                    }
                )
            }
            .sortedWith(
                compareByDescending<MuscleProgressStat> { it.setsThisWeek }
                    .thenByDescending { it.setsPreviousWeek }
                    .thenBy { it.muscle }
            )
    }

    private fun legacyPrimarySetsByMuscle(
        sessions: List<WorkoutSession>,
        catalogById: Map<String, Exercise>
    ): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        sessions.forEach { session ->
            session.exercises.forEach { workoutExercise ->
                // Every completed set counts, warm-ups and timed work included; involvement
                // describes exposure, not the loaded work sets a reviewed dose credits.
                val completedSetCount = workoutExercise.sets.count { it.isCompleted }
                if (completedSetCount == 0) return@forEach
                catalogById[workoutExercise.exerciseId]
                    ?.primaryMuscles
                    // "Cardio" and "Mobility" are training qualities, not muscles; counting
                    // them here would put "Mobility — 6 sets" beside Chest and Glutes.
                    ?.filter { it.isNotBlank() && MuscleVocabulary.isTrainable(it) }
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
        catalogById: Map<String, Exercise>
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
                    previous = previous.performance(),
                    current = current.performance(),
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

    /** External-load tonnage: completed WEIGHT_REPS sets only, warm-ups included. */
    private fun weeklyExternalLoadVolume(session: WorkoutSession): Double =
        session.exercises.sumOf { exercise ->
            exercise.sets.sumOf { set ->
                if (set.isCompletedRepBasedSet() &&
                    set.exerciseType == ExerciseType.WEIGHT_REPS
                ) {
                    val load = set.completedWeight?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
                    (load * requireNotNull(set.completedReps)).takeIf(Double::isFinite) ?: 0.0
                } else {
                    0.0
                }
            }
        }

    /**
     * Reps completed across the week, so bodyweight-only training still reports real work.
     * Load validity is irrelevant to a rep count, and timed work has no reps to count.
     */
    private fun weeklyCompletedReps(session: WorkoutSession): Int =
        session.exercises.sumOf { exercise ->
            exercise.sets.sumOf { set ->
                if (set.isCompletedRepBasedSet()) requireNotNull(set.completedReps) else 0
            }
        }

    /** Completed warm-up sets, so the reviewed-dose difference can be explained. */
    private fun warmupSetCount(session: WorkoutSession): Int =
        session.exercises.sumOf { exercise ->
            exercise.sets.count { it.isCompleted && it.type == SetType.WARMUP }
        }

    private fun WorkoutSession.convertWeightsTo(targetUnit: WeightUnit): WorkoutSession {
        if (weightUnit == targetUnit) return this
        return copy(
            weightUnit = targetUnit,
            exercises = exercises.map { exercise ->
                exercise.copy(
                    prescription = exercise.prescription.copy(
                        targetWeight = exercise.prescription.targetWeight.convertIfValid(
                            weightUnit,
                            targetUnit
                        ),
                        targetAssistanceWeight = exercise.prescription.targetAssistanceWeight
                            .convertIfValid(weightUnit, targetUnit)
                    ),
                    sets = exercise.sets.map { set ->
                        set.copy(
                            targetWeight = set.targetWeight.convertIfValid(weightUnit, targetUnit),
                            completedWeight = set.completedWeight.convertIfValid(weightUnit, targetUnit),
                            targetAssistanceWeight = set.targetAssistanceWeight.convertIfValid(
                                weightUnit,
                                targetUnit
                            ),
                            completedAssistanceWeight = set.completedAssistanceWeight.convertIfValid(
                                weightUnit,
                                targetUnit
                            )
                        )
                    }
                )
            }
        )
    }

    private fun Double?.convertIfValid(from: WeightUnit, to: WeightUnit): Double? =
        this?.takeIf { it.isFinite() && it >= 0.0 }?.let { convertWeight(it, from, to) }

    private fun WorkoutSet.isValidCompletedSet(): Boolean =
        isCompleted &&
            completedReps != null &&
            completedReps > 0 &&
            (completedWeight == null || (completedWeight.isFinite() && completedWeight >= 0.0))

    /** A completed rep-based set with a positive rep count; timed and distance work excluded. */
    private fun WorkoutSet.isCompletedRepBasedSet(): Boolean =
        isCompleted &&
            exerciseType in REP_BASED_TYPES &&
            (completedReps ?: 0) > 0

    private fun WorkoutSet.validPositiveWeight(): Double? =
        completedWeight?.takeIf { it.isFinite() && it > 0.0 }

    private fun WorkoutSet.strengthScore(): Double? {
        val reps = completedReps ?: return null
        val weight = validPositiveWeight()
        return if (weight == null) reps.toDouble() else weight * (1.0 + reps / 30.0)
    }

    /**
     * The numbers behind a set, left unformatted on purpose: the screen knows the reader's
     * locale and this does not, and a preformatted "62.5 kg × 8" could not be re-rendered
     * with a decimal comma without parsing it back out again.
     */
    private fun WorkoutSet.performance(): StrengthPerformance = StrengthPerformance(
        weight = validPositiveWeight(),
        reps = requireNotNull(completedReps)
    )

    private fun ExerciseSessionPerformance.bestStrengthSet(): WorkoutSet? =
        sets.maxByOrNull { it.strengthScore() ?: Double.NEGATIVE_INFINITY }

    private fun String.toDisplayName(): String =
        split('-').joinToString(" ") { word -> word.replaceFirstChar(Char::uppercase) }

    private data class ExerciseSessionPerformance(
        val exerciseId: String,
        val completedAtTimestamp: Long,
        val sets: List<WorkoutSet>
    )

    private data class ExerciseBest(
        val weight: Double?,
        val reps: Int?
    )

    private fun maxOfNullable(first: Double?, second: Double?): Double? =
        when {
            first == null -> second
            second == null -> first
            else -> maxOf(first, second)
        }

    private fun maxOfNullable(first: Int?, second: Int?): Int? =
        when {
            first == null -> second
            second == null -> first
            else -> maxOf(first, second)
        }

    private companion object {
        const val MAX_RECENT_HISTORY = 10
        const val MAX_RECORDS = 3
        const val MAX_STRENGTH_TRENDS = 3
        val REP_BASED_TYPES = setOf(
            ExerciseType.WEIGHT_REPS,
            ExerciseType.BODYWEIGHT_REPS,
            ExerciseType.ASSISTED_BODYWEIGHT
        )
    }
}
