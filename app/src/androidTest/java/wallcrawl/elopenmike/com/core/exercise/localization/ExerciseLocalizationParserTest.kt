package wallcrawl.elopenmike.com.core.exercise.localization

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Trust-boundary coverage for the translation overlay.
 *
 * The overlay is a build artefact rather than user input, so the risk it carries is not a
 * hostile document — it is a silently half-translated one. Every rejection here is a
 * mistake that would otherwise ship as English text on a Spanish screen, because the store
 * falls back to an empty overlay rather than crashing the app.
 */
@RunWith(AndroidJUnit4::class)
class ExerciseLocalizationParserTest {

    private val parser = ExerciseLocalizationParser()

    @Test
    fun aWellFormedOverlayParsesIntoLookupsKeyedByCanonicalIdentifiers() {
        val localization = parser.parse(
            overlay(
                exercises = """
                    "barbell-back-squat": {
                      "es": {
                        "name": "Sentadilla con barra",
                        "aliases": ["Back squat"],
                        "coachingSummary": "Sentadilla con barra."
                      }
                    }
                """
            ).reader()
        )

        assertThat(localization.languages).containsExactly("es")
        assertThat(localization.exerciseName("barbell-back-squat", "es-MX"))
            .isEqualTo("Sentadilla con barra")
        assertThat(localization.coachingSummary("barbell-back-squat", "es"))
            .isEqualTo("Sentadilla con barra.")
        assertThat(localization.searchTerms("barbell-back-squat"))
            .containsExactly("Sentadilla con barra", "Back squat")
        assertThat(localization.muscle("Chest", "es")).isEqualTo("Pecho")
        assertThat(localization.equipment("Barbell", "es")).isEqualTo("Barra")
    }

    @Test
    fun theDeclaredLanguagesArrayIsReadAsValuesNotKeys() {
        // The array element is a string value. Reading it as an object name is a mistake
        // JsonReader rejects, and the failure surfaces as "not valid JSON" on a document
        // that is perfectly well formed — which is exactly how it hides.
        val localization = parser.parse(overlay(languages = """["es"]""").reader())

        assertThat(localization.languages).containsExactly("es")
    }

    @Test
    fun anUnsupportedSchemaVersionIsRefused() {
        val error = assertThrows(ExerciseLocalizationFormatException::class.java) {
            parser.parse(overlay(schemaVersion = 2).reader())
        }
        assertThat(error).hasMessageThat().contains("schemaVersion")
    }

    @Test
    fun aRepeatedExerciseIsRefusedRatherThanSilentlyKeepingOne() {
        val error = assertThrows(ExerciseLocalizationFormatException::class.java) {
            parser.parse(
                overlay(
                    exercises = """
                        "squat": { "es": { "name": "Sentadilla" } },
                        "squat": { "es": { "name": "Otra sentadilla" } }
                    """
                ).reader()
            )
        }
        assertThat(error).hasMessageThat().contains("repeats exercise")
    }

    @Test
    fun aBlankOrMissingNameIsRefused() {
        assertThrows(ExerciseLocalizationFormatException::class.java) {
            parser.parse(overlay(exercises = """"squat": { "es": { "name": "  " } }""").reader())
        }
        assertThrows(ExerciseLocalizationFormatException::class.java) {
            parser.parse(
                overlay(exercises = """"squat": { "es": { "aliases": [] } }""").reader()
            )
        }
    }

    @Test
    fun textInAnUndeclaredLanguageIsRefused() {
        // Otherwise the overlay could carry translations no reader can ever reach, which
        // reads as "translated" in review while the screen stays English.
        val error = assertThrows(ExerciseLocalizationFormatException::class.java) {
            parser.parse(
                overlay(exercises = """"squat": { "fr": { "name": "Squat" } }""").reader()
            )
        }
        assertThat(error).hasMessageThat().contains("undeclared language")
    }

    @Test
    fun anUnsafeExerciseIdIsRefused() {
        assertThrows(ExerciseLocalizationFormatException::class.java) {
            parser.parse(
                overlay(exercises = """"../escape": { "es": { "name": "Sentadilla" } }""").reader()
            )
        }
    }

    @Test
    fun anUnknownFieldIsRefusedRatherThanIgnored() {
        // A typo'd field would otherwise drop a translation without a word about it.
        val error = assertThrows(ExerciseLocalizationFormatException::class.java) {
            parser.parse(
                overlay(
                    exercises = """"squat": { "es": { "name": "Sentadilla", "nombre": "x" } }"""
                ).reader()
            )
        }
        assertThat(error).hasMessageThat().contains("nombre")
    }

    @Test
    fun malformedJsonIsRefused() {
        assertThrows(ExerciseLocalizationFormatException::class.java) {
            parser.parse("{ not json".reader())
        }
    }

    private fun overlay(
        schemaVersion: Int = 1,
        languages: String = """["es"]""",
        exercises: String = """"squat": { "es": { "name": "Sentadilla" } }"""
    ): String = """
        {
          "schemaVersion": $schemaVersion,
          "languages": $languages,
          "vocabulary": {
            "muscles": { "Chest": { "es": "Pecho" } },
            "equipment": { "Barbell": { "es": "Barra" } }
          },
          "exercises": { $exercises }
        }
    """.trimIndent()
}
