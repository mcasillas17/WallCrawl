package wallcrawl.elopenmike.com.core.ui.localization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import java.text.Collator
import java.util.Locale
import wallcrawl.elopenmike.com.core.exercise.localization.ExerciseLocalization
import wallcrawl.elopenmike.com.core.exercise.localization.ExerciseLocalizationSource
import wallcrawl.elopenmike.com.core.model.Exercise

/**
 * Catalog text written in the language the screen is currently rendering.
 *
 * Exercise names, muscles, and equipment are content rather than interface chrome, so they
 * live in a validated overlay keyed by catalog ids and canonical English words instead of
 * in `strings.xml`. This is the read side of that overlay: it takes a canonical key and
 * gives back the reader's wording, falling back to the canonical English when a translation
 * is genuinely unavailable. The key it was given is never changed by the lookup.
 */
@Immutable
class ExerciseVocabulary(
    private val localization: ExerciseLocalization,
    private val languageTag: String
) {
    /** The exercise's name for this reader, or its catalog name when untranslated. */
    fun exerciseName(exercise: Exercise): String =
        localization.exerciseName(exercise.id, languageTag) ?: exercise.name

    /**
     * The exercise's name from an id alone, for screens that only stored the id.
     *
     * [fallback] is the catalog's English name when the caller has it. With neither, the id
     * is turned into words — the same last resort the app already used for a workout logged
     * against an exercise the current catalog no longer contains.
     */
    fun exerciseName(exerciseId: String, fallback: String? = null): String =
        localization.exerciseName(exerciseId, languageTag)
            ?: fallback
            ?: exerciseId.split('-').joinToString(" ") { word -> word.replaceFirstChar(Char::uppercase) }

    /** The coaching summary for this reader, or null when it is not translated. */
    fun coachingSummary(exercise: Exercise): String? =
        localization.coachingSummary(exercise.id, languageTag)

    /**
     * True when the exercise has programming text but none of it in the reader's language.
     *
     * A reader of the catalog's own language is never in that position: the overlay carries
     * no English block because the catalog already is the English, so asking it for one
     * always comes back empty. Without this a reader in English would be told every coached
     * exercise is missing a translation they do not need.
     */
    fun hasUntranslatedCoachingSummary(exercise: Exercise): Boolean =
        !readsCatalogLanguage &&
            exercise.programming?.coachingSummary != null &&
            coachingSummary(exercise) == null

    /** Whether this reader reads the language the catalog itself is written in. */
    private val readsCatalogLanguage: Boolean =
        Locale.forLanguageTag(languageTag).language == CATALOG_LANGUAGE

    /** A canonical muscle name written for this reader. */
    fun muscle(canonicalName: String): String =
        localization.muscle(canonicalName, languageTag) ?: canonicalName

    /** A canonical equipment name written for this reader. */
    fun equipment(canonicalName: String): String =
        localization.equipment(canonicalName, languageTag) ?: canonicalName

    fun muscles(canonicalNames: List<String>): List<String> = canonicalNames.map(::muscle)

    fun equipment(canonicalNames: List<String>): List<String> = canonicalNames.map(::equipment)

    /**
     * [canonicalNames] in the order their labels read to this reader.
     *
     * A filter row is browsed by its labels, so ordering it by the canonical English keys
     * leaves a Spanish reader with a row in no discernible order. Collation is the
     * platform's, so "Bíceps" sorts with "B" rather than after "Z". Only the display order
     * changes: each chip still carries its canonical key.
     */
    fun musclesByLabel(canonicalNames: List<String>): List<String> =
        canonicalNames.sortedWith(labelOrder(::muscle))

    /** [canonicalNames] in the order their labels read to this reader. */
    fun equipmentByLabel(canonicalNames: List<String>): List<String> =
        canonicalNames.sortedWith(labelOrder(::equipment))

    private fun labelOrder(label: (String) -> String): Comparator<String> {
        val collator = Collator.getInstance(Locale.forLanguageTag(languageTag))
        return Comparator { first, second -> collator.compare(label(first), label(second)) }
    }

    private companion object {
        /** The language the bundled catalog's own names and summaries are written in. */
        const val CATALOG_LANGUAGE = "en"
    }
}

/**
 * The vocabulary in scope, defaulting to English-only so a preview or a test that has not
 * provided one still renders the catalog rather than crashing.
 */
val LocalExerciseVocabulary = staticCompositionLocalOf {
    ExerciseVocabulary(ExerciseLocalization.EMPTY, "en")
}

/**
 * Loads the overlay once and re-derives the vocabulary whenever the configuration's locale
 * changes, which is what makes a language switch reach exercise names as well as chrome.
 */
@Composable
fun rememberExerciseVocabulary(source: ExerciseLocalizationSource): ExerciseVocabulary {
    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    var localization by remember(source) {
        mutableStateOf(source.currentLocalization() ?: ExerciseLocalization.EMPTY)
    }
    LaunchedEffect(source) {
        localization = source.localization()
    }
    return remember(localization, languageTag) { ExerciseVocabulary(localization, languageTag) }
}
