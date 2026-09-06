package wallcrawl.elopenmike.com.core.exercise.localization

import java.text.Normalizer
import java.util.Locale

/** One exercise's text in one language. Only [name] is required. */
data class LocalizedExerciseText(
    val name: String,
    val aliases: List<String> = emptyList(),
    val coachingSummary: String? = null
)

/**
 * Translations for catalog content, keyed by stable identifiers.
 *
 * This is an overlay, never a replacement. The bundled catalog is a byte-for-byte mirror of
 * its pinned upstream source and is verified against it in CI, so nothing here edits it:
 * exercises are keyed by their catalog id, and muscles and equipment by their canonical
 * English vocabulary word. Those keys are what eligibility, muscle matching, equipment
 * filtering, and dose accounting continue to use; a translated label is only ever read on
 * its way to a screen or into the search index.
 */
class ExerciseLocalization(
    val languages: List<String>,
    private val exercisesById: Map<String, Map<String, LocalizedExerciseText>>,
    private val musclesByCanonicalName: Map<String, Map<String, String>>,
    private val equipmentByCanonicalName: Map<String, Map<String, String>>
) {

    /**
     * The exercise's name in [languageTag], or null when it is not translated.
     *
     * Callers fall back to the catalog's own English name, which is why this returns null
     * rather than inventing one: an untranslated exercise shows its real English name
     * instead of a machine-mangled approximation of it.
     */
    fun exerciseName(exerciseId: String, languageTag: String): String? =
        textFor(exerciseId, languageTag)?.name

    /** The exercise's coaching summary in [languageTag], or null when it is not translated. */
    fun coachingSummary(exerciseId: String, languageTag: String): String? =
        textFor(exerciseId, languageTag)?.coachingSummary

    /** The canonical muscle name written in [languageTag], or null when untranslated. */
    fun muscle(canonicalName: String, languageTag: String): String? =
        musclesByCanonicalName[canonicalName]?.get(language(languageTag))

    /** The canonical equipment name written in [languageTag], or null when untranslated. */
    fun equipment(canonicalName: String, languageTag: String): String? =
        equipmentByCanonicalName[canonicalName]?.get(language(languageTag))

    /**
     * Everything an exercise can be found by, in every shipped language at once.
     *
     * Search deliberately ignores the reader's current language: someone who knows a lift by
     * its English name should find it while the interface is in Spanish, and someone typing
     * "sentadilla" should find it while the interface is in English. The result is that a
     * query resolves the same canonical ids either way.
     */
    fun searchTerms(exerciseId: String): List<String> =
        exercisesById[exerciseId]
            ?.values
            ?.flatMap { text -> listOf(text.name) + text.aliases }
            .orEmpty()

    /** Localized muscle and equipment words, in every shipped language, for search. */
    fun vocabularySearchTerms(canonicalName: String): List<String> =
        (musclesByCanonicalName[canonicalName]?.values.orEmpty() +
            equipmentByCanonicalName[canonicalName]?.values.orEmpty()).toList()

    private fun textFor(exerciseId: String, languageTag: String): LocalizedExerciseText? =
        exercisesById[exerciseId]?.get(language(languageTag))

    private fun language(languageTag: String): String =
        Locale.forLanguageTag(languageTag).language.lowercase(Locale.ROOT)

    companion object {
        /** An overlay that translates nothing; every lookup falls back to the catalog. */
        val EMPTY = ExerciseLocalization(emptyList(), emptyMap(), emptyMap(), emptyMap())

        /**
         * The form both stored terms and typed queries are compared in.
         *
         * Accents are stripped rather than required, so "biceps" finds "bíceps" and
         * "sentadilla búlgara" is found by someone typing without accents — which is how
         * most people type on a phone keyboard. Case folding uses the root locale so the
         * device language cannot change what matches.
         */
        fun normalizeForSearch(value: String): String =
            Normalizer.normalize(value, Normalizer.Form.NFD)
                .replace(COMBINING_MARKS, "")
                .lowercase(Locale.ROOT)

        private val COMBINING_MARKS = Regex("\\p{Mn}+")
    }
}
