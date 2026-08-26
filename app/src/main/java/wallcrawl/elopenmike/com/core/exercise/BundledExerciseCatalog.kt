package wallcrawl.elopenmike.com.core.exercise

import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import wallcrawl.elopenmike.com.core.exercise.workoutguide.WorkoutGuideCatalogSource
import wallcrawl.elopenmike.com.core.model.Exercise

/** Exercise catalog backed by the validated catalog bundled in Android assets. */
class BundledExerciseCatalog(
    private val source: WorkoutGuideCatalogSource
) : ExerciseCatalog {

    override fun getAllExercises(): Flow<List<Exercise>> = flow {
        emit(source.snapshot().exercises)
    }

    override suspend fun getExerciseById(id: String): Exercise? =
        source.snapshot().exercises.firstOrNull { exercise ->
            exercise.id.equals(id.trim(), ignoreCase = true)
        }

    override fun searchExercises(
        query: String,
        muscle: String?,
        equipment: String?
    ): Flow<List<Exercise>> = flow {
        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        val normalizedMuscle = muscle?.trim()?.takeIf(String::isNotEmpty)
        val normalizedEquipment = equipment?.trim()?.takeIf(String::isNotEmpty)

        emit(
            source.snapshot().exercises.filter { exercise ->
                exercise.matchesQuery(normalizedQuery) &&
                    exercise.matchesMuscle(normalizedMuscle) &&
                    exercise.matchesEquipment(normalizedEquipment)
            }
        )
    }

    override suspend fun getMuscleGroups(): List<String> =
        source.snapshot().exercises
            .flatMap { exercise -> exercise.primaryMuscles + exercise.secondaryMuscles }
            .distinctCaseInsensitively()

    override suspend fun getEquipmentTypes(): List<String> =
        source.snapshot().exercises
            .flatMap(Exercise::listedEquipment)
            .distinctCaseInsensitively()

    private fun Exercise.matchesQuery(normalizedQuery: String): Boolean {
        if (normalizedQuery.isEmpty()) return true
        return sequenceOf(name)
            .plus(searchAliases)
            .plus(primaryMuscles)
            .plus(secondaryMuscles)
            .plus(listedEquipment)
            .any { value -> value.lowercase(Locale.ROOT).contains(normalizedQuery) }
    }

    private fun Exercise.matchesMuscle(muscle: String?): Boolean =
        muscle == null || (primaryMuscles + secondaryMuscles).any { value ->
            value.equals(muscle, ignoreCase = true)
        }

    private fun Exercise.matchesEquipment(equipment: String?): Boolean =
        equipment == null || listedEquipment.any { value ->
            value.equals(equipment, ignoreCase = true)
        }

    private fun List<String>.distinctCaseInsensitively(): List<String> =
        distinctBy { value -> value.lowercase(Locale.ROOT) }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
}
