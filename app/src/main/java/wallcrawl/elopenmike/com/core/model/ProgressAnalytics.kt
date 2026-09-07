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
