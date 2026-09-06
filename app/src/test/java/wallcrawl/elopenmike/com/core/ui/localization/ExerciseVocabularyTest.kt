package wallcrawl.elopenmike.com.core.ui.localization

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wallcrawl.elopenmike.com.core.exercise.localization.ExerciseLocalization
import wallcrawl.elopenmike.com.core.exercise.localization.LocalizedExerciseText
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseProgrammingMetadata
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.MechanicsType
import wallcrawl.elopenmike.com.core.model.MovementPattern
import wallcrawl.elopenmike.com.core.model.ProgressionType
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.Difficulty

/**
 * The read side of the overlay, where a canonical key becomes the reader's wording.
 *
 * The key is never changed by a lookup and never replaced by a translation: filter chips
 * show a label and pass a key, and everything downstream of them reads the key.
 */
class ExerciseVocabularyTest {

    private val localization = ExerciseLocalization(
        languages = listOf("es"),
        exercisesById = emptyMap(),
        musclesByCanonicalName = mapOf(
            "Back" to mapOf("es" to "Espalda"),
            "Biceps" to mapOf("es" to "Bíceps"),
            "Chest" to mapOf("es" to "Pecho"),
            "Calves" to mapOf("es" to "Pantorrillas")
        ),
        equipmentByCanonicalName = mapOf(
            "Barbell" to mapOf("es" to "Barra"),
            "Cable" to mapOf("es" to "Polea"),
            "Bench" to mapOf("es" to "Banco")
        )
    )

    private val canonicalMuscles = listOf("Back", "Biceps", "Calves", "Chest")

    private val coachedExercise = Exercise(
        id = "barbell-back-squat",
        name = "Barbell Back Squat",
        primaryMuscles = listOf("Quadriceps"),
        listedEquipment = listOf("Barbell"),
        type = ExerciseType.WEIGHT_REPS,
        programming = ExerciseProgrammingMetadata(
            requiredEquipmentCombinations = listOf(listOf("Barbell")),
            movementPattern = MovementPattern.SQUAT,
            difficulty = Difficulty.INTERMEDIATE,
            mechanics = MechanicsType.COMPOUND,
            recommendedRepRange = RepRange(5, 8),
            fatigueScore = 4,
            progressionType = ProgressionType.REPETITIONS_THEN_LOAD,
            coachingSummary = "Squat with a barbell."
        )
    )

    @Test
    fun anEnglishReaderIsNeverToldAnExerciseLacksATranslation() {
        // The overlay carries no English block, because the catalog already is the
        // English. Asking it for one always comes back empty, so without a guard every
        // coached exercise would carry "no Spanish description yet" on an English screen.
        val vocabulary = ExerciseVocabulary(localization, "en-US")

        assertThat(vocabulary.coachingSummary(coachedExercise)).isNull()
        assertThat(vocabulary.hasUntranslatedCoachingSummary(coachedExercise)).isFalse()
    }

    @Test
    fun aSpanishReaderIsToldOnlyWhenTheSummaryIsReallyMissing() {
        val untranslated = ExerciseVocabulary(localization, "es-MX")
        assertThat(untranslated.hasUntranslatedCoachingSummary(coachedExercise)).isTrue()

        val translated = ExerciseVocabulary(
            ExerciseLocalization(
                languages = listOf("es"),
                exercisesById = mapOf(
                    coachedExercise.id to mapOf(
                        "es" to LocalizedExerciseText(
                            name = "Sentadilla con barra",
                            coachingSummary = "Sentadilla con barra."
                        )
                    )
                ),
                musclesByCanonicalName = emptyMap(),
                equipmentByCanonicalName = emptyMap()
            ),
            "es-MX"
        )
        assertThat(translated.hasUntranslatedCoachingSummary(coachedExercise)).isFalse()
    }

    @Test
    fun anExerciseWithNoProgrammingTextIsNeverFlagged() {
        val vocabulary = ExerciseVocabulary(localization, "es")

        assertThat(vocabulary.hasUntranslatedCoachingSummary(coachedExercise.copy(programming = null)))
            .isFalse()
    }

    @Test
    fun filterKeysAreOrderedByTheLabelTheReaderSees() {
        // Sorted by the English keys these read Espalda, Bíceps, Pantorrillas, Pecho --
        // a browsable row in no discernible order for a Spanish reader.
        val ordered = ExerciseVocabulary(localization, "es-MX").musclesByLabel(canonicalMuscles)

        assertThat(ordered).containsExactly("Biceps", "Back", "Calves", "Chest").inOrder()
    }

    @Test
    fun orderingChangesTheOrderOnlyAndNeverTheKeys() {
        val vocabulary = ExerciseVocabulary(localization, "es")

        assertThat(vocabulary.musclesByLabel(canonicalMuscles))
            .containsExactlyElementsIn(canonicalMuscles)
        assertThat(vocabulary.equipmentByLabel(listOf("Barbell", "Cable", "Bench")))
            .containsExactly("Bench", "Barbell", "Cable").inOrder()
    }

    @Test
    fun englishKeepsItsOwnOrderingAndItsOwnWords() {
        val vocabulary = ExerciseVocabulary(localization, "en")

        assertThat(vocabulary.muscle("Biceps")).isEqualTo("Biceps")
        assertThat(vocabulary.musclesByLabel(canonicalMuscles))
            .containsExactlyElementsIn(canonicalMuscles).inOrder()
    }

    @Test
    fun anUntranslatedWordSortsAndReadsAsItsCanonicalSelf() {
        val vocabulary = ExerciseVocabulary(localization, "es")

        assertThat(vocabulary.muscle("Hamstrings")).isEqualTo("Hamstrings")
        assertThat(vocabulary.musclesByLabel(listOf("Chest", "Hamstrings")))
            .containsExactly("Hamstrings", "Chest").inOrder()
    }
}
