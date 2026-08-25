package wallcrawl.elopenmike.com.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.WeightUnit

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val primaryGoal: FitnessGoal,
    val experienceLevel: ExperienceLevel,
    val preferredDurationMinutes: Int,
    val daysPerWeek: Int,
    val availableEquipmentJson: String,
    val preferredUnit: WeightUnit,
    val musclePrioritiesJson: String,
    val excludedExerciseIdsJson: String
)

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val startedAtTimestamp: Long,
    val completedAtTimestamp: Long?,
    val targetDurationMinutes: Int,
    val actualDurationMinutes: Int,
    val status: SessionStatus,
    val focusMusclesJson: String,
    val notes: String
)

@Entity(
    tableName = "workout_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class WorkoutExerciseEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    val orderIndex: Int,
    val targetSets: Int,
    val targetRepMin: Int,
    val targetRepMax: Int,
    val targetWeight: Double?,
    val notes: String
)

@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutExerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["workoutExerciseId"])]
)
data class WorkoutSetEntity(
    @PrimaryKey
    val id: String,
    val workoutExerciseId: String,
    val setNumber: Int,
    val targetReps: Int,
    val completedReps: Int?,
    val targetWeight: Double?,
    val completedWeight: Double?,
    val isCompleted: Boolean,
    val rpe: Float?,
    val rir: Int?,
    val type: SetType
)
