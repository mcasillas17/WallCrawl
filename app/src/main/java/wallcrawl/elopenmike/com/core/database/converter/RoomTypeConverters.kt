package wallcrawl.elopenmike.com.core.database.converter

import androidx.room.TypeConverter
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetStopReason
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutOrigin

class RoomTypeConverters {

    @TypeConverter
    fun fromThemePreference(theme: ThemePreference): String = theme.name

    @TypeConverter
    fun toThemePreference(value: String): ThemePreference = try {
        ThemePreference.valueOf(value)
    } catch (e: Exception) {
        ThemePreference.SYSTEM
    }

    @TypeConverter
    fun fromFitnessGoal(goal: FitnessGoal): String = goal.name

    @TypeConverter
    fun toFitnessGoal(value: String): FitnessGoal = try {
        FitnessGoal.valueOf(value)
    } catch (e: Exception) {
        FitnessGoal.BUILD_MUSCLE
    }

    @TypeConverter
    fun fromExperienceLevel(level: ExperienceLevel): String = level.name

    @TypeConverter
    fun toExperienceLevel(value: String): ExperienceLevel = try {
        ExperienceLevel.valueOf(value)
    } catch (e: Exception) {
        ExperienceLevel.INTERMEDIATE
    }

    @TypeConverter
    fun fromWeightUnit(unit: WeightUnit): String = unit.name

    @TypeConverter
    fun toWeightUnit(value: String): WeightUnit = try {
        WeightUnit.valueOf(value)
    } catch (e: Exception) {
        WeightUnit.LBS
    }

    @TypeConverter
    fun fromSessionStatus(status: SessionStatus): String = status.name

    @TypeConverter
    fun toSessionStatus(value: String): SessionStatus = try {
        SessionStatus.valueOf(value)
    } catch (e: Exception) {
        SessionStatus.IN_PROGRESS
    }

    @TypeConverter
    fun fromSetType(type: SetType): String = type.name

    @TypeConverter
    fun toSetType(value: String): SetType = try {
        SetType.valueOf(value)
    } catch (e: Exception) {
        SetType.NORMAL
    }

    /**
     * A stop reason is nullable: NULL means the set was never skipped or stopped.
     *
     * Unlike the tolerant converters above, an unrecognised non-null value is rejected
     * instead of being mapped to a fallback. Every write goes through one guarded
     * repository path, so an unknown value can only mean the row is not what the domain
     * believes it is -- and silently choosing a reason would invent an outcome the user
     * never recorded. The message names the column, never the stored value.
     */
    @TypeConverter
    fun fromSetStopReason(reason: SetStopReason?): String? = reason?.name

    @TypeConverter
    fun toSetStopReason(value: String?): SetStopReason? {
        if (value == null) return null
        return SetStopReason.entries.firstOrNull { it.name == value }
            ?: throw IllegalArgumentException(
                "workout_sets.stopReason holds a value that is not a known SetStopReason."
            )
    }

    @TypeConverter
    fun fromExerciseType(type: ExerciseType): String = type.name

    @TypeConverter
    fun toExerciseType(value: String): ExerciseType = try {
        ExerciseType.valueOf(value)
    } catch (e: Exception) {
        ExerciseType.WEIGHT_REPS
    }

    @TypeConverter
    fun fromWorkoutOrigin(origin: WorkoutOrigin): String = origin.name

    @TypeConverter
    fun toWorkoutOrigin(value: String): WorkoutOrigin = try {
        WorkoutOrigin.valueOf(value)
    } catch (e: Exception) {
        WorkoutOrigin.PLANNER
    }

    @TypeConverter
    fun fromPriorityLevel(level: PriorityLevel): String = level.name

    @TypeConverter
    fun toPriorityLevel(value: String): PriorityLevel = try {
        PriorityLevel.valueOf(value)
    } catch (e: Exception) {
        PriorityLevel.NORMAL
    }

    // List<String> helper
    @TypeConverter
    fun fromStringList(list: List<String>): String = list.joinToString("|||")

    @TypeConverter
    fun toStringList(data: String): List<String> =
        if (data.isBlank()) emptyList() else data.split("|||").filter { it.isNotBlank() }
}
