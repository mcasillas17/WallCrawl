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
    val recentPersonalRecords: List<PersonalRecord> = emptyList(),
    val muscleGroupFocus: List<MuscleProgressStat> = emptyList(),
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

data class MuscleProgressStat(
    val muscle: String,
    val setsThisWeek: Int,
    val percentageGrowth: Int
)

data class StrengthTrend(
    val exerciseId: String,
    val exerciseName: String,
    val previousMetric: String,
    val currentMetric: String,
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
