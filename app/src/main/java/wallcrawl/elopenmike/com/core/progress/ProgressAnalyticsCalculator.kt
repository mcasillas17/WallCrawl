package wallcrawl.elopenmike.com.core.progress

import java.time.Instant
import java.time.ZoneId
import java.util.EnumMap
import wallcrawl.elopenmike.com.core.ai.WeeklyDoseLedgerCalculator
import wallcrawl.elopenmike.com.core.model.AnalyticsBucket
import wallcrawl.elopenmike.com.core.model.AnalyticsContributor
import wallcrawl.elopenmike.com.core.model.AnalyticsMetric
import wallcrawl.elopenmike.com.core.model.AnalyticsRange
import wallcrawl.elopenmike.com.core.model.AnalyticsScope
import wallcrawl.elopenmike.com.core.model.AnalyticsSeries
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.LedgerOmissionReason
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.MuscleVocabulary
import wallcrawl.elopenmike.com.core.model.PerformanceComparison
import wallcrawl.elopenmike.com.core.model.PerformanceObservation
import wallcrawl.elopenmike.com.core.model.PerformanceSeries
import wallcrawl.elopenmike.com.core.model.PerformanceShape
import wallcrawl.elopenmike.com.core.model.ProgressAnalytics
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.TrainingWeek
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.model.convertWeight

/**
 * Range analytics from completed history, with no I/O, clock or state.
 *
 * Weekly formulas are the Progress metric contract's own ([loggedReps], [externalLoadVolume],
 * the completed-set flag) and reviewed dose is [WeeklyDoseLedgerCalculator] itself, so this adds
 * no competing definition. Input outside the range or not `COMPLETED` is rejected rather than
 * dropped, so a malformed read can never look like a quiet week.
 */
class ProgressAnalyticsCalculator(
    private val ledgerCalculator: WeeklyDoseLedgerCalculator = WeeklyDoseLedgerCalculator()
) {
    /**
     * @param comparableSetIds sets satisfying the shared history-comparison eligibility
     *   predicate, computed in SQL so performance and history detail cannot disagree.
     */
    fun calculate(
        range: AnalyticsRange,
        scope: AnalyticsScope,
        sessions: List<WorkoutSession>,
        comparableSetIds: Set<String>,
        exercisesById: Map<String, Exercise>,
        catalogVersion: String,
        reviewPolicyVersion: Int,
        preferredUnit: WeightUnit,
        now: Instant,
        zoneId: ZoneId
    ): ProgressAnalytics {
        val weeks = rangeWeeks(range, now, zoneId)
        require(sessions.map { it.id }.distinct().size == sessions.size) { "Duplicate analytics session." }
        val byWeek = sessions.groupBy { session ->
            require(session.status == SessionStatus.COMPLETED) { "Analytics reads completed sessions only." }
            val completedAt = requireNotNull(session.completedAtTimestamp) { "Completed session lacks completion." }
            requireNotNull(weeks.firstOrNull { it.contains(completedAt) }) { "Analytics session outside its range." }
        }
        val unattributed = EnumMap<LedgerOmissionReason, Int>(LedgerOmissionReason::class.java)
        var unclassifiable = 0

        // One row per (week, session): what it contributes to each metric, or null when it is
        // not a member of that metric's scope.
        val rows = weeks.map { week ->
            byWeek[week].orEmpty().sortedWith(compareBy({ it.completedAtTimestamp }, { it.id })).map { session ->
                val completed = session.exercises
                    .filter { it.inScope(scope, exercisesById) }
                    .flatMap { exercise -> exercise.sets.filter { it.isCompleted } }
                if (scope is AnalyticsScope.Muscle) {
                    unclassifiable += session.exercises
                        .filter { it.exerciseId !in exercisesById }
                        .sumOf { exercise -> exercise.sets.count { it.isCompleted } }
                }
                val ledger = ledgerCalculator.calculate(
                    listOf(if (scope is AnalyticsScope.Exercise) {
                        session.copy(exercises = session.exercises.filter { it.exerciseId == scope.exerciseId })
                    } else session),
                    exercisesById, LedgerPolicyVersion.PRIMARY_ONLY_V1, week, catalogVersion, reviewPolicyVersion
                )
                ledger.unattributedWorkSets.forEach { (reason, count) -> unattributed.merge(reason, count, Int::plus) }
                val member = scope == AnalyticsScope.All || completed.isNotEmpty()
                session to AnalyticsMetric.entries.associateWith { metric ->
                    when (metric) {
                        AnalyticsMetric.WORKOUTS -> 1.0.takeIf { member }
                        AnalyticsMetric.COMPLETED_SETS -> completed.size.toDouble().takeIf { member }
                        // Reps and load volume count only work they apply to: timed, distance or
                        // body-mass work is not a recorded zero of either.
                        AnalyticsMetric.REPS -> completed.sumOf { it.loggedReps() }.toDouble()
                            .takeIf { completed.any { it.loggedReps() > 0 } }
                        AnalyticsMetric.EXTERNAL_LOAD_VOLUME -> completed
                            .sumOf { it.externalLoadVolume(session.weightUnit, preferredUnit) }
                            .takeIf { completed.any { it.exerciseType == ExerciseType.WEIGHT_REPS && it.loggedReps() > 0 } }
                        // A muscle's reviewed dose is the ledger's accepted direct primary, which can be a
                        // legacy secondary muscle, so its membership is the ledger credit itself.
                        AnalyticsMetric.REVIEWED_PRIMARY_DOSE -> when (scope) {
                            is AnalyticsScope.Muscle -> ledger.directPrimarySets[scope.muscle]?.takeIf { it > 0 }?.toDouble()
                            else -> ledger.creditedWorkSets.toDouble().takeIf { member }
                        }
                    }
                }
            }
        }

        return ProgressAnalytics(
            range = range,
            scope = scope,
            series = AnalyticsMetric.entries.associateWith { metric ->
                val buckets = weeks.mapIndexed { index, week ->
                    val contributors = rows[index].mapNotNull { (session, values) ->
                        values.getValue(metric)?.let {
                            AnalyticsContributor(session.id, session.name, session.completedAtTimestamp!!, it)
                        }
                    }
                    AnalyticsBucket(week, partial = index == weeks.lastIndex, value = contributors.sumOf { it.value },
                        contributors = contributors)
                }
                AnalyticsSeries(buckets, buckets.sumOf { it.value }, buckets.any { it.contributors.isNotEmpty() })
            },
            fullWeeks = weeks.size - 1,
            hasWorkoutsInRange = sessions.isNotEmpty(),
            unclassifiableSets = unclassifiable,
            unattributedWorkSets = unattributed.filterValues { it > 0 },
            performance = if (scope is AnalyticsScope.Exercise) {
                performance(sessions, scope.exerciseId, comparableSetIds, preferredUnit)
            } else emptyList()
        )
    }

    private fun WorkoutExercise.inScope(scope: AnalyticsScope, exercisesById: Map<String, Exercise>): Boolean =
        when (scope) {
            AnalyticsScope.All -> true
            is AnalyticsScope.Exercise -> exerciseId == scope.exerciseId
            // The weekly involvement card's rule: current catalog legacy primaries, overlapping.
            is AnalyticsScope.Muscle -> exercisesById[exerciseId]?.primaryMuscles.orEmpty()
                .any { it == scope.muscle && MuscleVocabulary.isTrainable(it) }
        }

    /** One observation per session and shape; repeated instances pool their comparable sets. */
    private fun performance(
        sessions: List<WorkoutSession>,
        exerciseId: String,
        comparableSetIds: Set<String>,
        unit: WeightUnit
    ): List<PerformanceSeries> {
        val observations = sessions
            .sortedWith(compareBy({ it.completedAtTimestamp }, { it.startedAtTimestamp }, { it.id }))
            .flatMap { session ->
                session.exercises
                    .filter { it.exerciseId == exerciseId }
                    .sortedWith(compareBy({ it.orderIndex }, { it.id }))
                    .groupBy { it.shape() }
                    .mapNotNull { (shape, instances) ->
                        val sets = instances.flatMap { it.sets }.filter { it.id in comparableSetIds }
                        observe(shape, session, instances.map { it.id }, sets, unit)?.let { shape to it }
                    }
            }
        return observations.groupBy({ it.first }, { it.second })
            .toSortedMap()
            .map { (shape, points) ->
                val comparison = if (shape.charted && points.size >= 2) {
                    PerformanceComparison(points.first(), points.last(), points.last().value - points.first().value)
                } else null
                PerformanceSeries(shape, points, comparison)
            }
    }

    private fun observe(
        shape: PerformanceShape,
        session: WorkoutSession,
        instanceIds: List<String>,
        sets: List<WorkoutSet>,
        unit: WeightUnit
    ): PerformanceObservation? {
        fun point(value: Double, reps: Int? = null, secondary: Double? = null) = PerformanceObservation(
            session.id, session.name, session.completedAtTimestamp!!, instanceIds, value, reps, secondary
        )
        fun Double.inUnit() = convertWeight(this, session.weightUnit, unit)
        return when (shape) {
            PerformanceShape.WEIGHTED_REPS -> sets
                .maxWithOrNull(compareBy<WorkoutSet>({ it.completedWeight }, { it.completedReps }, { it.id }))
                ?.let { point(it.completedWeight!!.inUnit(), it.completedReps) }
            PerformanceShape.BODYWEIGHT_REPS -> sets.maxOfOrNull { it.completedReps!! }?.let { point(it.toDouble()) }
            // Ties resolve by set ID, never by stored order or by preferring less assistance.
            PerformanceShape.ASSISTED_REPS -> sets.maxWithOrNull(compareBy<WorkoutSet>({ it.completedReps }, { it.id }))
                ?.let { point(it.completedReps!!.toDouble(), it.completedReps, it.completedAssistanceWeight?.inUnit()) }
            PerformanceShape.HOLD_DURATION, PerformanceShape.TIME_ONLY ->
                sets.maxOfOrNull { it.completedDurationSeconds!! }?.let { point(it.toDouble()) }
            PerformanceShape.DISTANCE_ONLY -> sets.maxOfOrNull { it.completedDistanceMeters!! }?.let { point(it) }
            PerformanceShape.DISTANCE_AND_TIME -> sets
                .maxWithOrNull(compareBy<WorkoutSet>({ it.completedDistanceMeters }, { it.id }))
                ?.let { point(it.completedDistanceMeters!!, secondary = it.completedDurationSeconds!!.toDouble()) }
        }
    }

    /** Distance/time shapes follow the stored targets, the rule history comparisons use. */
    private fun WorkoutExercise.shape(): PerformanceShape = when (prescription.exerciseType) {
        ExerciseType.WEIGHT_REPS -> PerformanceShape.WEIGHTED_REPS
        ExerciseType.BODYWEIGHT_REPS -> PerformanceShape.BODYWEIGHT_REPS
        ExerciseType.ASSISTED_BODYWEIGHT -> PerformanceShape.ASSISTED_REPS
        ExerciseType.DURATION -> PerformanceShape.HOLD_DURATION
        ExerciseType.DISTANCE_DURATION -> when {
            prescription.targetDistanceMeters != null && prescription.targetDurationSeconds != null ->
                PerformanceShape.DISTANCE_AND_TIME
            prescription.targetDistanceMeters != null -> PerformanceShape.DISTANCE_ONLY
            else -> PerformanceShape.TIME_ONLY
        }
    }

    companion object {
        /** The range's ISO weeks, oldest first; the last contains [now] and is partial. */
        fun rangeWeeks(range: AnalyticsRange, now: Instant, zoneId: ZoneId): List<TrainingWeek> {
            val current = TrainingWeek.containing(now, zoneId)
            return (range.weeks - 1 downTo 0).map {
                TrainingWeek.startingOn(current.startEpochDay - 7L * it, zoneId)
            }
        }
    }
}
