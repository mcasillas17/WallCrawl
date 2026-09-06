package wallcrawl.elopenmike.com.core.exercise.localization

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles

class ExerciseLocalizationTest {

    private val localization = ExerciseLocalization(
        languages = listOf("es"),
        exercisesById = mapOf(
            "barbell-back-squat" to mapOf(
                "es" to LocalizedExerciseText(
                    name = "Sentadilla con barra",
                    aliases = listOf("Back squat"),
                    coachingSummary = "Sentadilla con barra centrada en cuádriceps y glúteos."
                )
            ),
            "bicep-curl" to mapOf("es" to LocalizedExerciseText(name = "Curl de bíceps"))
        ),
        musclesByCanonicalName = mapOf(StandardMuscles.CHEST to mapOf("es" to "Pecho")),
        equipmentByCanonicalName = mapOf(StandardEquipment.BARBELL to mapOf("es" to "Barra"))
    )

    @Test
    fun aTranslatedExerciseReadsInTheRequestedLanguage() {
        assertThat(localization.exerciseName("barbell-back-squat", "es"))
            .isEqualTo("Sentadilla con barra")
        assertThat(localization.coachingSummary("barbell-back-squat", "es"))
            .isEqualTo("Sentadilla con barra centrada en cuádriceps y glúteos.")
    }

    @Test
    fun everySpanishRegionResolvesToTheSameNeutralTranslation() {
        listOf("es", "es-MX", "es-419", "es-ES", "es-AR").forEach { tag ->
            assertWithMessage(tag).that(localization.exerciseName("barbell-back-squat", tag))
                .isEqualTo("Sentadilla con barra")
        }
    }

    @Test
    fun anUntranslatedLanguageFallsBackRatherThanInventingAName() {
        // Null, so the caller can show the catalog's own English name instead of a
        // machine-mangled approximation of it.
        assertThat(localization.exerciseName("barbell-back-squat", "en")).isNull()
        assertThat(localization.exerciseName("barbell-back-squat", "fr")).isNull()
        assertThat(localization.coachingSummary("bicep-curl", "es")).isNull()
        assertThat(localization.exerciseName("not-in-the-overlay", "es")).isNull()
    }

    @Test
    fun vocabularyResolvesByItsCanonicalEnglishKey() {
        assertThat(localization.muscle(StandardMuscles.CHEST, "es-MX")).isEqualTo("Pecho")
        assertThat(localization.equipment(StandardEquipment.BARBELL, "es")).isEqualTo("Barra")
        assertThat(localization.muscle("Not A Muscle", "es")).isNull()
    }

    @Test
    fun searchTermsCoverEveryShippedLanguageAtOnce() {
        // Search is deliberately language-agnostic: an English name must keep working
        // while the interface is Spanish, and vice versa.
        assertThat(localization.searchTerms("barbell-back-squat"))
            .containsExactly("Sentadilla con barra", "Back squat")
        assertThat(localization.searchTerms("unknown")).isEmpty()
    }

    @Test
    fun normalizationStripsAccentsAndCaseInBothDirections() {
        assertThat(ExerciseLocalization.normalizeForSearch("Bíceps"))
            .isEqualTo(ExerciseLocalization.normalizeForSearch("biceps"))
        assertThat(ExerciseLocalization.normalizeForSearch("SENTADILLA BÚLGARA"))
            .isEqualTo(ExerciseLocalization.normalizeForSearch("sentadilla bulgara"))
        assertThat(ExerciseLocalization.normalizeForSearch("Cuádriceps"))
            .isEqualTo("cuadriceps")
    }

    @Test
    fun theEmptyOverlayTranslatesNothingAndNeverThrows() {
        assertThat(ExerciseLocalization.EMPTY.exerciseName("barbell-back-squat", "es")).isNull()
        assertThat(ExerciseLocalization.EMPTY.muscle(StandardMuscles.CHEST, "es")).isNull()
        assertThat(ExerciseLocalization.EMPTY.searchTerms("barbell-back-squat")).isEmpty()
        assertThat(ExerciseLocalization.EMPTY.languages).isEmpty()
    }
}
