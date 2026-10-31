package wallcrawl.elopenmike.com.core.model

/**
 * Progress and analytics models for the WallCrawl Progress screen.
 */
data class ProgressOverview(
    val workoutsThisWeek: Int = 0,
    val weeklyGoal: Int = 0,
    val currentStreakWeeks: Int = 0,
    val totalWorkoutsLogged: Int = 0,
    val totalVolumeThisWeek: Double = 0.0,
    val totalRepsThisWeek: Int = 0,
    /** Every completed set this week, warm-ups and timed work included; not a dose. */
    val completedSetsThisWeek: Int = 0,
    /** The warm-up subset of [completedSetsThisWeek], so the reviewed-dose gap is explainable. */
    val warmupSetsThisWeek: Int = 0,
    val recentPersonalRecords: List<PersonalRecord> = emptyList(),
    /**
     * Broad legacy-primary muscle involvement, deliberately named apart from reviewed dose.
     * Counts are non-additive: one completed set involving two primaries is one set for each.
     */
    val legacyPrimaryActivity: List<MuscleProgressStat> = emptyList(),
    val strengthTrends: List<StrengthTrend> = emptyList(),
    val recentHistory: List<WorkoutSession> = emptyList()
)

data class PersonalRecord(
    val exerciseId: String,
    val exerciseName: String,
    val recordType: RecordType = RecordType.WEIGHT,
    val value: Double,
    val unit: String,
    val achievedTimestamp: Long = System.currentTimeMillis(),
    val previousValue: Double? = null
)

enum class RecordType {
    WEIGHT,
    REPS,
    ESTIMATED_1RM,
    VOLUME
}

/**
 * One muscle's non-additive legacy-primary involvement this week against the previous
 * calendar week.
 *
 * [percentageChange] is null when [setsPreviousWeek] is zero: there is no baseline to grow
 * from, so the reader is shown new activity rather than an invented 100%. Otherwise it is a
 * signed whole percent, and a reduction is shown as a negative number.
 */
data class MuscleProgressStat(
    val muscle: String,
    val setsThisWeek: Int,
    val setsPreviousWeek: Int,
    val percentageChange: Int?
)

/**
 * One completed set expressed as the numbers behind it, not as a sentence.
 *
 * A null [weight] means bodyweight work rather than "no load recorded"; the screen decides
 * how to word either case, and formats the numbers for the reader's locale.
 */
data class StrengthPerformance(
    val weight: Double?,
    val reps: Int
)

data class StrengthTrend(
    val exerciseId: String,
    /** The catalog's English name, used only when the overlay has no translation. */
    val exerciseName: String,
    val previous: StrengthPerformance,
    val current: StrengthPerformance,
    val percentageChange: Int,
    val isPositive: Boolean = true
)

data class WorkoutSummary(
    val sessionId: String,
    val workoutName: String,
    val durationMinutes: Int,
    val totalSetsCompleted: Int,
    val totalVolume: Double,
    val prCount: Int = 0,
    val unit: WeightUnit = WeightUnit.LBS,
    val completedAtTimestamp: Long = System.currentTimeMillis()
)

/** Monday-start calendar weeks ending with the current, partial week; never a rolling window or a program week. */
enum class AnalyticsRange(val weeks: Int) {
    WEEKS_4(4),
    WEEKS_12(12),
    WEEKS_26(26),
    WEEKS_52(52)
}

/** One canonical selection; exercise IDs and muscle names are never translated labels. */
sealed interface AnalyticsScope {
    data object All : AnalyticsScope
    data class Exercise(val exerciseId: String) : AnalyticsScope
    data class Muscle(val muscle: String) : AnalyticsScope
}

enum class AnalyticsMetric {
    WORKOUTS,
    COMPLETED_SETS,
    REPS,
    EXTERNAL_LOAD_VOLUME,
    REVIEWED_PRIMARY_DOSE
}

data class AnalyticsSelection(
    val range: AnalyticsRange = AnalyticsRange.WEEKS_4,
    val scope: AnalyticsScope = AnalyticsScope.All,
    val metric: AnalyticsMetric = AnalyticsMetric.COMPLETED_SETS
)

/**
 * Every metric over the range's weekly buckets, computed in one pass so switching the metric
 * needs no new read. Range and scope decide what is read; the metric only picks a [series].
 *
 * [unclassifiableSets] counts in-range completed sets whose exercise the current catalog does
 * not know, which a muscle scope cannot classify. [unattributedWorkSets] is the ledger's typed
 * omission total for the scope's sessions (all exercises for a muscle scope).
 */
data class ProgressAnalytics(
    val range: AnalyticsRange,
    val scope: AnalyticsScope,
    val series: Map<AnalyticsMetric, AnalyticsSeries>,
    val fullWeeks: Int,
    val hasWorkoutsInRange: Boolean,
    val unclassifiableSets: Int,
    val unattributedWorkSets: Map<LedgerOmissionReason, Int>,
    val performance: List<PerformanceSeries>
)

/** One metric's buckets; the total and match flag are derived from them, so they cannot disagree. */
data class AnalyticsSeries(val buckets: List<AnalyticsBucket>) {
    val rangeTotal: Double get() = buckets.sumOf { it.value }

    /** False when no session contributes to this metric anywhere in the range. */
    val hasScopeMatches: Boolean get() = buckets.any { it.contributors.isNotEmpty() }
}

/** One calendar [TrainingWeek]; [partial] marks the unfinished current week, which is never compared. */
data class AnalyticsBucket(
    val week: TrainingWeek,
    val partial: Boolean,
    val value: Double,
    val contributors: List<AnalyticsContributor>
)

/** One completed session's share of a bucket, in the selected metric. */
data class AnalyticsContributor(
    val sessionId: String,
    val name: String,
    val completedAtTimestamp: Long,
    val value: Double
)

/** A persisted measurement shape; only [charted] shapes have a comparable single value. */
enum class PerformanceShape(val charted: Boolean) {
    WEIGHTED_REPS(true),
    BODYWEIGHT_REPS(true),
    ASSISTED_REPS(false),
    HOLD_DURATION(true),
    DISTANCE_ONLY(true),
    TIME_ONLY(true),
    DISTANCE_AND_TIME(false)
}

data class PerformanceSeries(
    val shape: PerformanceShape,
    val observations: List<PerformanceObservation>,
    /** Null with fewer than two sessions or for a shape that is listed but not compared. */
    val comparison: PerformanceComparison?
)

/**
 * One session's best comparable set for one shape. [value] is load in the preferred unit,
 * reps, seconds or metres; [reps] accompanies a weighted or assisted value and
 * [secondaryValue] is assistance (preferred unit) or the seconds of a distance-and-time set.
 */
data class PerformanceObservation(
    val sessionId: String,
    val sessionName: String,
    val completedAtTimestamp: Long,
    val exerciseInstanceIds: List<String>,
    val value: Double,
    val reps: Int? = null,
    val secondaryValue: Double? = null
)

/** Earliest and latest observation in the range and their signed difference; no percentage. */
data class PerformanceComparison(
    val earliest: PerformanceObservation,
    val latest: PerformanceObservation,
    val difference: Double
)
