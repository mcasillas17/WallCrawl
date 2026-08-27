package wallcrawl.elopenmike.com.core.model

import java.util.UUID

/** A reusable, local-only workout definition whose list order is prescription order. */
data class WorkoutTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val notes: String = "",
    val createdAtTimestamp: Long = System.currentTimeMillis(),
    val updatedAtTimestamp: Long = createdAtTimestamp,
    val exercises: List<PlannedExercise>
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
        }
    }

    private companion object {
        const val MAX_NAME_LENGTH = 120
        const val MAX_NOTES_LENGTH = 2_000
        const val MAX_EXERCISE_NOTES_LENGTH = 1_000
        const val MAX_EXERCISES = 50
    }
}
