package wallcrawl.elopenmike.com.core.exercise.localization

import android.util.JsonReader
import android.util.JsonToken
import android.util.MalformedJsonException
import java.io.Reader
import java.util.Locale
import wallcrawl.elopenmike.com.core.io.BoundedCharacterReader

class ExerciseLocalizationFormatException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

/**
 * Reads the bundled translation overlay.
 *
 * Deliberately strict, in the same way the catalog parser is: a duplicate key, an unknown
 * language, a blank value, or an oversized document is a build-time mistake that must fail
 * loudly rather than silently leave part of the catalog in English. Nothing here trusts the
 * document to be self-consistent — completeness against the catalog is checked separately,
 * because this parser does not know what the catalog contains.
 */
class ExerciseLocalizationParser {

    fun parse(input: Reader): ExerciseLocalization {
        try {
            val reader = JsonReader(
                BoundedCharacterReader(input, MAX_CHARACTERS) { limit ->
                    malformed("Exercise localization exceeds the $limit-character input limit.")
                }
            )
            var schemaVersion: Int? = null
            var languages: List<String>? = null
            var vocabulary: Vocabulary? = null
            var exercises: Map<String, Map<String, LocalizedExerciseText>>? = null
            val seenFields = mutableSetOf<String>()

            reader.beginObject()
            while (reader.hasNext()) {
                val field = reader.nextName()
                requireUniqueField(seenFields, field, "localization")
                when (field) {
                    "schemaVersion" -> schemaVersion = reader.readBoundedInt(
                        "localization.schemaVersion",
                        SUPPORTED_SCHEMA_VERSION,
                        SUPPORTED_SCHEMA_VERSION
                    )
                    "languages" -> languages = reader.readLanguages()
                    "vocabulary" -> vocabulary = reader.readVocabulary()
                    "exercises" -> exercises = reader.readExercises()
                    else -> malformed("Unexpected field ${safeField(field)} in localization.")
                }
            }
            reader.endObject()
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                malformed("Unexpected content after the localization root object.")
            }

            schemaVersion ?: malformed("Localization is missing its schemaVersion.")
            val declaredLanguages = languages
                ?: malformed("Localization is missing its languages array.")
            val entries = exercises ?: malformed("Localization is missing its exercises object.")
            val words = vocabulary ?: malformed("Localization is missing its vocabulary object.")

            requireDeclaredLanguages(declaredLanguages, entries, words)

            return ExerciseLocalization(
                languages = declaredLanguages,
                exercisesById = entries,
                musclesByCanonicalName = words.muscles,
                equipmentByCanonicalName = words.equipment
            )
        } catch (error: ExerciseLocalizationFormatException) {
            throw error
        } catch (error: MalformedJsonException) {
            // A syntactically broken document is a problem with the document, not with
            // reading it. MalformedJsonException is an IOException, so without this the
            // caller would be told the asset could not be read.
            throw ExerciseLocalizationFormatException(
                "Exercise localization is not valid JSON.",
                error
            )
        } catch (error: IllegalStateException) {
            throw ExerciseLocalizationFormatException("Exercise localization is not valid JSON.", error)
        } catch (error: NumberFormatException) {
            throw ExerciseLocalizationFormatException("Exercise localization holds an invalid number.", error)
        }
    }

    private fun JsonReader.readLanguages(): List<String> {
        val languages = mutableListOf<String>()
        beginArray()
        while (hasNext()) {
            if (languages.size >= MAX_LANGUAGES) {
                malformed("Localization declares more than $MAX_LANGUAGES languages.")
            }
            // An array element is a value, not a name. Reading it with `nextName()` is what
            // `readLanguageTag` does for the two places a language is an object key, and
            // JsonReader rejects that here.
            languages += normalizeLanguageTag(
                readText("localization.languages", MAX_LANGUAGE_TAG_LENGTH),
                "localization.languages"
            )
        }
        endArray()
        if (languages.isEmpty()) malformed("Localization must declare at least one language.")
        if (languages.size != languages.toSet().size) {
            malformed("Localization declares the same language twice.")
        }
        return languages
    }

    private fun JsonReader.readVocabulary(): Vocabulary {
        var muscles: Map<String, Map<String, String>>? = null
        var equipment: Map<String, Map<String, String>>? = null
        val seenFields = mutableSetOf<String>()

        beginObject()
        while (hasNext()) {
            val field = nextName()
            requireUniqueField(seenFields, field, "vocabulary")
            when (field) {
                "muscles" -> muscles = readVocabularyGroup("vocabulary.muscles")
                "equipment" -> equipment = readVocabularyGroup("vocabulary.equipment")
                else -> malformed("Unexpected field ${safeField(field)} in vocabulary.")
            }
        }
        endObject()
        return Vocabulary(
            muscles = muscles ?: malformed("Localization vocabulary is missing muscles."),
            equipment = equipment ?: malformed("Localization vocabulary is missing equipment.")
        )
    }

    private fun JsonReader.readVocabularyGroup(label: String): Map<String, Map<String, String>> {
        val group = mutableMapOf<String, Map<String, String>>()
        beginObject()
        while (hasNext()) {
            if (group.size >= MAX_VOCABULARY_ENTRIES) {
                malformed("$label contains more than $MAX_VOCABULARY_ENTRIES entries.")
            }
            val canonicalName = readKey(label)
            if (group.containsKey(canonicalName)) {
                malformed("$label repeats an entry.")
            }
            group[canonicalName] = readTranslations("$label entry")
        }
        endObject()
        if (group.isEmpty()) malformed("$label must not be empty.")
        return group
    }

    private fun JsonReader.readTranslations(label: String): Map<String, String> {
        val translations = mutableMapOf<String, String>()
        beginObject()
        while (hasNext()) {
            val language = readLanguageTag(label)
            if (translations.containsKey(language)) {
                malformed("$label repeats language $language.")
            }
            translations[language] = readText("$label.$language", MAX_VOCABULARY_LENGTH)
        }
        endObject()
        if (translations.isEmpty()) malformed("$label must translate into at least one language.")
        return translations
    }

    private fun JsonReader.readExercises(): Map<String, Map<String, LocalizedExerciseText>> {
        val entries = mutableMapOf<String, Map<String, LocalizedExerciseText>>()
        beginObject()
        while (hasNext()) {
            if (entries.size >= MAX_EXERCISES) {
                malformed("Localization contains more than $MAX_EXERCISES exercises.")
            }
            val exerciseId = readKey("localization.exercises")
            if (!SAFE_EXERCISE_ID.matches(exerciseId)) {
                malformed("Localization contains an unsafe exercise id.")
            }
            if (entries.containsKey(exerciseId)) {
                malformed("Localization repeats exercise $exerciseId.")
            }
            entries[exerciseId] = readExerciseLanguages(exerciseId)
        }
        endObject()
        if (entries.isEmpty()) malformed("Localization must contain at least one exercise.")
        return entries
    }

    private fun JsonReader.readExerciseLanguages(
        exerciseId: String
    ): Map<String, LocalizedExerciseText> {
        val byLanguage = mutableMapOf<String, LocalizedExerciseText>()
        beginObject()
        while (hasNext()) {
            val language = readLanguageTag("exercise $exerciseId")
            if (byLanguage.containsKey(language)) {
                malformed("Exercise $exerciseId repeats language $language.")
            }
            byLanguage[language] = readExerciseText("exercise $exerciseId.$language")
        }
        endObject()
        if (byLanguage.isEmpty()) {
            malformed("Exercise $exerciseId must translate into at least one language.")
        }
        return byLanguage
    }

    private fun JsonReader.readExerciseText(label: String): LocalizedExerciseText {
        var name: String? = null
        var aliases: List<String> = emptyList()
        var coachingSummary: String? = null
        val seenFields = mutableSetOf<String>()

        beginObject()
        while (hasNext()) {
            val field = nextName()
            requireUniqueField(seenFields, field, label)
            when (field) {
                "name" -> name = readText("$label.name", MAX_NAME_LENGTH)
                "aliases" -> aliases = readAliases("$label.aliases")
                "coachingSummary" ->
                    coachingSummary = readText("$label.coachingSummary", MAX_SUMMARY_LENGTH)
                else -> malformed("Unexpected field ${safeField(field)} in $label.")
            }
        }
        endObject()
        return LocalizedExerciseText(
            name = name ?: malformed("$label is missing its name."),
            aliases = aliases,
            coachingSummary = coachingSummary
        )
    }

    private fun JsonReader.readAliases(label: String): List<String> {
        val aliases = mutableListOf<String>()
        beginArray()
        while (hasNext()) {
            if (aliases.size >= MAX_ALIASES) {
                malformed("$label contains more than $MAX_ALIASES entries.")
            }
            aliases += readText(label, MAX_NAME_LENGTH)
        }
        endArray()
        if (aliases.size != aliases.toSet().size) malformed("$label repeats an alias.")
        return aliases
    }

    private fun JsonReader.readKey(label: String): String {
        val key = nextName()
        if (key.isBlank() || key.length > MAX_KEY_LENGTH) {
            malformed("$label contains an invalid key.")
        }
        return key
    }

    /** A language used as an object key, as in `{"es": …}`. */
    private fun JsonReader.readLanguageTag(label: String): String =
        normalizeLanguageTag(nextName(), label)

    /**
     * The one place a language tag is validated, so the key form and the array-element form
     * cannot drift apart. Case is folded with the root locale: the tag is an identifier,
     * and folding it under the device's locale is how "TR" becomes something else entirely.
     */
    private fun normalizeLanguageTag(tag: String, label: String): String {
        if (!SAFE_LANGUAGE_TAG.matches(tag)) {
            malformed("$label names an invalid language tag.")
        }
        return tag.lowercase(Locale.ROOT)
    }

    private fun JsonReader.readText(label: String, maximumLength: Int): String {
        if (peek() != JsonToken.STRING) malformed("$label has the wrong JSON type.")
        val value = nextString()
        if (value.isBlank()) malformed("$label must not be blank.")
        if (value.length > maximumLength) {
            malformed("$label exceeds $maximumLength characters.")
        }
        return value.trim()
    }

    private fun JsonReader.readBoundedInt(label: String, minimum: Int, maximum: Int): Int {
        if (peek() != JsonToken.NUMBER) malformed("$label has the wrong JSON type.")
        val value = nextString().takeIf(SAFE_INTEGER_LITERAL::matches)?.toIntOrNull()
            ?: malformed("$label must use integer JSON notation.")
        if (value !in minimum..maximum) malformed("$label must be between $minimum and $maximum.")
        return value
    }

    /**
     * Refuses text in a language the document never declared.
     *
     * Without this an overlay could ship translations no reader can ever reach, which reads
     * as "translated" in review while the screen stays English.
     */
    private fun requireDeclaredLanguages(
        declared: List<String>,
        entries: Map<String, Map<String, LocalizedExerciseText>>,
        vocabulary: Vocabulary
    ) {
        val allowed = declared.toSet()
        entries.forEach { (exerciseId, byLanguage) ->
            byLanguage.keys.firstOrNull { it !in allowed }?.let { language ->
                malformed("Exercise $exerciseId uses undeclared language $language.")
            }
        }
        (vocabulary.muscles.values + vocabulary.equipment.values).forEach { translations ->
            translations.keys.firstOrNull { it !in allowed }?.let { language ->
                malformed("Localization vocabulary uses undeclared language $language.")
            }
        }
    }

    private fun requireUniqueField(seenFields: MutableSet<String>, field: String, label: String) {
        if (!seenFields.add(field)) malformed("$label contains duplicate field ${safeField(field)}.")
    }

    private fun safeField(field: String): String =
        if (SAFE_ERROR_FIELD.matches(field)) field else "<invalid-field>"

    private fun malformed(message: String): Nothing = throw ExerciseLocalizationFormatException(message)

    private class Vocabulary(
        val muscles: Map<String, Map<String, String>>,
        val equipment: Map<String, Map<String, String>>
    )

    private companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
        const val MAX_CHARACTERS = 2_000_000L
        const val MAX_LANGUAGES = 16
        const val MAX_EXERCISES = 5_000
        const val MAX_VOCABULARY_ENTRIES = 500
        const val MAX_ALIASES = 12
        const val MAX_KEY_LENGTH = 120
        const val MAX_NAME_LENGTH = 160
        const val MAX_SUMMARY_LENGTH = 1_000
        const val MAX_VOCABULARY_LENGTH = 120
        const val MAX_LANGUAGE_TAG_LENGTH = 8

        val SAFE_EXERCISE_ID = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
        val SAFE_LANGUAGE_TAG = Regex("[A-Za-z]{2,3}")
        val SAFE_ERROR_FIELD = Regex("[A-Za-z0-9_.-]{1,64}")
        val SAFE_INTEGER_LITERAL = Regex("-?\\d{1,10}")
    }
}
