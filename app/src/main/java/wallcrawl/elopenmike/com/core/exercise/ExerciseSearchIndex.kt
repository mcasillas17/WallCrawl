package wallcrawl.elopenmike.com.core.exercise

import wallcrawl.elopenmike.com.core.exercise.localization.ExerciseLocalization
import wallcrawl.elopenmike.com.core.model.Exercise

/**
 * Accent- and case-insensitive catalog search across every shipped language at once.
 *
 * Terms are folded once, when the catalog snapshot and the translation overlay are paired,
 * rather than on every keystroke: both are fixed for the life of the process, so
 * re-normalizing roughly fifteen strings for each of 302 exercises per typed character is
 * pure repetition. Only the query is normalized per search.
 *
 * Matching reads presentation text — including translations — but returns catalog
 * exercises unchanged. Their canonical English names, muscles, and equipment are still what
 * eligibility, muscle matching, equipment filtering, and dose accounting read, so a
 * translation is never a key.
 */
class ExerciseSearchIndex(
    /** Every exercise the index covers, in catalog order. */
    val all: List<Exercise>,
    localization: ExerciseLocalization
) {
    private val normalizedTermsById: Map<String, List<String>> =
        all.associate { exercise ->
            val vocabulary =
                exercise.primaryMuscles + exercise.secondaryMuscles + exercise.listedEquipment
            val terms = listOf(exercise.id, exercise.name) +
                exercise.searchAliases +
                vocabulary +
                localization.searchTerms(exercise.id) +
                vocabulary.flatMap(localization::vocabularySearchTerms)
            exercise.id to terms.map(ExerciseLocalization::normalizeForSearch).distinct()
        }

    /** The exercises with a term containing [query]; all of them when it is blank. */
    fun matching(query: String): List<Exercise> {
        val normalized = ExerciseLocalization.normalizeForSearch(query.trim())
        if (normalized.isEmpty()) return all
        return all.filter { exercise ->
            normalizedTermsById[exercise.id].orEmpty().any { term -> term.contains(normalized) }
        }
    }

    companion object {
        /** Matches nothing, for a screen whose catalog has not loaded yet. */
        val EMPTY = ExerciseSearchIndex(emptyList(), ExerciseLocalization.EMPTY)
    }
}
