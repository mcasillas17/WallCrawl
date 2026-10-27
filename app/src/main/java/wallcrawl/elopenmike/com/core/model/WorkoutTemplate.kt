package wallcrawl.elopenmike.com.core.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * A reusable, local-only workout definition whose list order is prescription order.
 *
 * [weightUnit] is the unit of every load and assistance target in the template. It is null
 * only for a template saved before templates recorded a unit: its numbers were entered in
 * some unit nobody wrote down, so nothing may assume one. See [requiresUnitConfirmation].
 */
data class WorkoutTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val notes: String = "",
    val createdAtTimestamp: Long = System.currentTimeMillis(),
    val updatedAtTimestamp: Long = createdAtTimestamp,
    val exercises: List<PlannedExercise>,
    val weightUnit: WeightUnit? = null
) {
    init {
        require(id.isNotBlank()) { "Template id must not be blank." }
        require(name.isNotBlank() && name.length <= MAX_NAME_LENGTH) {
            "Template name must contain 1 to $MAX_NAME_LENGTH characters."
        }
        require(notes.length <= MAX_NOTES_LENGTH) {
            "Template notes must not exceed $MAX_NOTES_LENGTH characters."
        }
        require(createdAtTimestamp >= 0L) { "Created timestamp must not be negative." }
        require(updatedAtTimestamp >= createdAtTimestamp) {
            "Updated timestamp must not precede the created timestamp."
        }
        require(exercises.size in 1..MAX_EXERCISES) {
            "A template must contain between 1 and $MAX_EXERCISES exercises."
        }
        exercises.forEach { exercise ->
            require(exercise.exerciseId.isNotBlank()) { "Template exercise id must not be blank." }
            require(exercise.notes.length <= MAX_EXERCISE_NOTES_LENGTH) {
                "Exercise notes must not exceed $MAX_EXERCISE_NOTES_LENGTH characters."
            }
            if (weightUnit != null) {
                val limit = maxLoad(weightUnit)
                listOfNotNull(
                    exercise.prescription.targetWeight,
                    exercise.prescription.targetAssistanceWeight
                ).forEach { load ->
                    require(load <= limit) { "Template loads must not exceed $limit $weightUnit." }
                }
            }
        }
    }

    /**
     * The exercises with every load and assistance target expressed in [unit], each
     * converted by [convertWeightForEditing]: half-up to the editable precision, and a
     * positive load too small to show becomes 0.01 rather than 0. The template itself is
     * unchanged. Refuses a template whose loads have no recorded unit: converting from a
     * guessed unit would be a silent reinterpretation.
     */
    fun exercisesIn(unit: WeightUnit): List<PlannedExercise> {
        if (requiresUnitConfirmation) throw UnconfirmedTemplateUnitException()
        val from = weightUnit ?: return exercises
        if (from == unit) return exercises
        fun Double.converted() = convertWeightForEditing(this, from, unit)
        return exercises.map { exercise ->
            val prescription = exercise.prescription
            exercise.copy(
                prescription = prescription.copy(
                    targetWeight = prescription.targetWeight?.converted(),
                    targetAssistanceWeight = prescription.targetAssistanceWeight?.converted()
                )
            )
        }
    }

    /** True when the template carries a load whose unit was never recorded. */
    val requiresUnitConfirmation: Boolean
        get() = weightUnit == null && exercises.any {
            it.prescription.targetWeight != null || it.prescription.targetAssistanceWeight != null
        }

    /** The limits the constructor enforces, so the editor can refuse before it builds one. */
    internal companion object {
        const val MAX_NAME_LENGTH = 120
        const val MAX_NOTES_LENGTH = 2_000
        const val MAX_EXERCISE_NOTES_LENGTH = 1_000
        const val MAX_EXERCISES = 50

        /**
         * The largest load a template may hold in [unit]. A template is started in whatever
         * unit the profile has then, so a kilogram load must still fit the domain bound once
         * converted to pounds; the bound is rounded down so the conversion cannot overshoot.
         */
        fun maxLoad(unit: WeightUnit): Double = when (unit) {
            WeightUnit.LBS -> ExercisePrescription.MAX_WEIGHT
            WeightUnit.KG -> BigDecimal(
                convertWeight(ExercisePrescription.MAX_WEIGHT, WeightUnit.LBS, WeightUnit.KG)
            ).setScale(MeasurementPrecision.EDITABLE_FRACTION_DIGITS, RoundingMode.DOWN).toDouble()
        }
    }
}

/**
 * A template whose loads were saved before units were recorded was asked for them in a unit.
 * Typed, so a caller can tell this refusal from any other failure to start.
 */
class UnconfirmedTemplateUnitException :
    IllegalStateException("The template's load unit has not been confirmed.")
