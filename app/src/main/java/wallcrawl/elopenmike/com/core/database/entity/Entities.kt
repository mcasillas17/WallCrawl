package wallcrawl.elopenmike.com.core.database.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutOrigin

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey
    val id: String,
    val revision: Long = 0,
    val name: String,
    val primaryGoal: FitnessGoal,
    val experienceLevel: ExperienceLevel,
    val preferredDurationMinutes: Int,
    val daysPerWeek: Int,
    val availableEquipmentJson: String,
    val preferredUnit: WeightUnit,
    val musclePrioritiesJson: String,
    val excludedExerciseIdsJson: String,
    @ColumnInfo(defaultValue = "0")
    val onboardingCompleted: Boolean = false,
    @ColumnInfo(defaultValue = "''")
    val trainingConstraintsJson: String = "",
    @ColumnInfo(defaultValue = "0")
    val returningAfterBreakWeeks: Int = 0,
    @ColumnInfo(defaultValue = "''")
    val confirmedStartingLoadsJson: String = "",
    @ColumnInfo(defaultValue = "''")
    val fitnessGoalsJson: String = "",
    @ColumnInfo(defaultValue = "'{}'")
    val movementCapabilitiesJson: String = "{}",
    @ColumnInfo(defaultValue = "'SYSTEM'")
    val themePreference: ThemePreference = ThemePreference.SYSTEM
)

@Entity(
    tableName = "workout_sessions",
    indices = [Index(value = ["status", "completedAtTimestamp"])]
)
data class WorkoutSessionEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val startedAtTimestamp: Long,
    val completedAtTimestamp: Long?,
    val targetDurationMinutes: Int,
    val actualDurationMinutes: Int,
    val weightUnit: WeightUnit = WeightUnit.LBS,
    val status: SessionStatus,
    @ColumnInfo(defaultValue = "'PLANNER'")
    val origin: WorkoutOrigin = WorkoutOrigin.PLANNER,
    val sourceTemplateId: String? = null,
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
    val exerciseType: ExerciseType = ExerciseType.WEIGHT_REPS,
    val targetSets: Int,
    val targetRepMin: Int?,
    val targetRepMax: Int?,
    val targetWeight: Double?,
    val targetAssistanceWeight: Double? = null,
    val targetDurationSeconds: Int? = null,
    val targetDistanceMeters: Double? = null,
    val restSeconds: Int = 90,
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
    val exerciseType: ExerciseType = ExerciseType.WEIGHT_REPS,
    val targetReps: Int?,
    val completedReps: Int?,
    val targetWeight: Double?,
    val completedWeight: Double?,
    val targetAssistanceWeight: Double? = null,
    val completedAssistanceWeight: Double? = null,
    val targetDurationSeconds: Int? = null,
    val completedDurationSeconds: Int? = null,
    val targetDistanceMeters: Double? = null,
    val completedDistanceMeters: Double? = null,
    val isCompleted: Boolean,
    val rpe: Float?,
    val rir: Int?,
    val type: SetType
)

@Entity(tableName = "workout_templates")
data class WorkoutTemplateEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val notes: String,
    val createdAtTimestamp: Long,
    val updatedAtTimestamp: Long
)

@Entity(
    tableName = "workout_template_exercises",
    primaryKeys = ["templateId", "orderIndex"],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["templateId"])]
)
data class WorkoutTemplateExerciseEntity(
    val templateId: String,
    val orderIndex: Int,
    val exerciseId: String,
    val exerciseType: ExerciseType,
    val targetSets: Int,
    val targetRepMin: Int?,
    val targetRepMax: Int?,
    val targetWeight: Double?,
    val targetAssistanceWeight: Double?,
    val targetDurationSeconds: Int?,
    val targetDistanceMeters: Double?,
    val restSeconds: Int,
    val notes: String
)
