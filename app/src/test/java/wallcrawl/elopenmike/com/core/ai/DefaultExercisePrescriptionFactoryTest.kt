package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExercisePerformanceHistory
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext

class DefaultExercisePrescriptionFactoryTest {

    private val factory = DefaultExercisePrescriptionFactory()
    private val context = WorkoutGenerationContext(
        userProfile = UserProfile(primaryGoal = FitnessGoal.GENERAL_FITNESS)
    )

    @Test
    fun noHistoryAndNoConfirmedBaseline_doesNotPrescribeLoad() {
        val benchPress = exercise(ExerciseType.WEIGHT_REPS).copy(id = "barbell-bench-press")
        val profile = UserProfile(preferredUnit = WeightUnit.KG)

        val prescription = factory.create(
            benchPress,
            WorkoutGenerationContext(userProfile = profile)
        )

        assertThat(prescription.targetWeight).isNull()
    }

    @Test
    fun confirmedBaseline_isUsedInTheProfilesUnit() {
        val benchPress = exercise(ExerciseType.WEIGHT_REPS).copy(id = "barbell-bench-press")
        val profile = UserProfile(
            preferredUnit = WeightUnit.KG,
            confirmedStartingLoads = mapOf("barbell-bench-press" to 40.0)
        )

        val prescription = factory.create(
            benchPress,
            WorkoutGenerationContext(userProfile = profile)
        )

        assertThat(prescription.targetWeight).isEqualTo(40.0)
    }

    @Test
    fun priorHistory_takesPrecedenceOverConfirmedBaseline() {
        val benchPress = exercise(ExerciseType.WEIGHT_REPS).copy(id = "barbell-bench-press")
        val profile = UserProfile(
            primaryGoal = FitnessGoal.GENERAL_FITNESS,
            preferredUnit = WeightUnit.KG,
            confirmedStartingLoads = mapOf("barbell-bench-press" to 40.0)
        )
        val historyContext = WorkoutGenerationContext(
            userProfile = profile,
            exerciseHistory = mapOf(
                "barbell-bench-press" to ExercisePerformanceHistory(
                    exerciseId = "barbell-bench-press",
                    lastWeight = 60.0,
                    lastReps = 8,
                    bestEstimated1RM = 75.0,
                    recentSets = emptyList()
                )
            )
        )

        val prescription = factory.create(benchPress, historyContext)

        assertThat(prescription.targetWeight).isEqualTo(60.0)
    }

    @Test
    fun create_returnsStructurallyValidDefaultsForEveryCatalogExerciseType() {
        val prescriptions = ExerciseType.entries.associateWith { type ->
            factory.create(exercise(type), context)
        }

        assertThat(prescriptions.getValue(ExerciseType.WEIGHT_REPS).repRange).isNotNull()
        assertThat(prescriptions.getValue(ExerciseType.BODYWEIGHT_REPS).repRange).isNotNull()
        assertThat(prescriptions.getValue(ExerciseType.ASSISTED_BODYWEIGHT).repRange).isNotNull()
        assertThat(prescriptions.getValue(ExerciseType.DURATION).targetDurationSeconds)
            .isEqualTo(45)
        assertThat(prescriptions.getValue(ExerciseType.DISTANCE_DURATION).targetDurationSeconds)
            .isEqualTo(600)
        assertThat(prescriptions.values.map { it.exerciseType })
            .containsExactlyElementsIn(ExerciseType.entries)
    }

    @Test
    fun create_usesShorterDurationAndOneSetForStretch() {
        val stretch = exercise(ExerciseType.DURATION).copy(isStretch = true)

        val prescription = factory.create(stretch, context)

        assertThat(prescription.targetSets).isEqualTo(1)
        assertThat(prescription.targetDurationSeconds).isEqualTo(30)
    }

    private fun exercise(type: ExerciseType) = Exercise(
        id = "unreviewed-${type.name.lowercase()}",
        name = type.name,
        primaryMuscles = listOf("Core"),
        listedEquipment = listOf("Bodyweight"),
        type = type,
        programming = null
    )
}
