package wallcrawl.elopenmike.com.core.exercise

import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import wallcrawl.elopenmike.com.core.exercise.localization.ExerciseLocalization
import wallcrawl.elopenmike.com.core.exercise.localization.ExerciseLocalizationSource
import wallcrawl.elopenmike.com.core.exercise.workoutguide.WorkoutGuideCatalogSource
import wallcrawl.elopenmike.com.core.model.Exercise

/** Exercise catalog backed by the validated catalog bundled in Android assets. */
class BundledExerciseCatalog(
    private val source: WorkoutGuideCatalogSource,
    /**
     * Translations used for search only. The exercises this catalog returns keep their
     * canonical English names and vocabulary, because those are the values eligibility,
     * muscle matching, equipment filtering, and dose accounting read.
     */
    private val localizationSource: ExerciseLocalizationSource? = null
) : ExerciseCatalog {

    @Volatile
    private var searchIndex: CachedIndex? = null

    override fun getAllExercises(): Flow<List<Exercise>> = flow {
        emit(source.snapshot().exercises)
    }

    override suspend fun getExerciseById(id: String): Exercise? =
        source.snapshot().exercises.firstOrNull { exercise ->
            exercise.id.equals(id.trim(), ignoreCase = true)
        }

    /**
     * Searches across every shipped language at once.
     *
     * A query is matched against the English name, aliases, muscles, and equipment and
     * against their translations, so "sentadilla" and "squat" resolve the same catalog ids
     * whichever language the interface happens to be in. [muscle] and [equipment] stay
     * canonical English keys: they come from filter chips, which carry the key alongside
     * the translated label they display.
     */
    override fun searchExercises(
        query: String,
        muscle: String?,
        equipment: String?
    ): Flow<List<Exercise>> = flow {
        val normalizedMuscle = muscle?.trim()?.takeIf(String::isNotEmpty)
        val normalizedEquipment = equipment?.trim()?.takeIf(String::isNotEmpty)
        val exercises = source.snapshot().exercises
        val localization = localizationSource?.localization() ?: ExerciseLocalization.EMPTY

        emit(
            indexFor(exercises, localization).matching(query).filter { exercise ->
                exercise.matchesMuscle(normalizedMuscle) &&
                    exercise.matchesEquipment(normalizedEquipment)
            }
        )
    }.flowOn(Dispatchers.Default)

    /**
     * The search index for this snapshot and overlay, built at most once for each pair.
     *
     * Both are loaded once and cached, so identity comparison is enough; a concurrent first
     * search can at worst build the same index twice and keep one of them.
     */
    private fun indexFor(
        exercises: List<Exercise>,
        localization: ExerciseLocalization
    ): ExerciseSearchIndex {
        searchIndex
            ?.takeIf { it.all === exercises && it.localization === localization }
            ?.let { return it.index }
        return CachedIndex(exercises, localization, ExerciseSearchIndex(exercises, localization))
            .also { searchIndex = it }
            .index
    }

    override suspend fun getMuscleGroups(): List<String> =
        source.snapshot().exercises
            .flatMap { exercise -> exercise.primaryMuscles + exercise.secondaryMuscles }
            .distinctCaseInsensitively()

    override suspend fun getEquipmentTypes(): List<String> =
        source.snapshot().exercises
            .flatMap(Exercise::listedEquipment)
            .distinctCaseInsensitively()

    private fun Exercise.matchesMuscle(muscle: String?): Boolean =
        muscle == null || (primaryMuscles + secondaryMuscles).any { value ->
            value.equals(muscle, ignoreCase = true)
        }

    private fun Exercise.matchesEquipment(equipment: String?): Boolean =
        equipment == null || listedEquipment.any { value ->
            value.equals(equipment, ignoreCase = true)
        }

    private class CachedIndex(
        val all: List<Exercise>,
        val localization: ExerciseLocalization,
        val index: ExerciseSearchIndex
    )

    private fun List<String>.distinctCaseInsensitively(): List<String> =
        distinctBy { value -> value.lowercase(Locale.ROOT) }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
}
