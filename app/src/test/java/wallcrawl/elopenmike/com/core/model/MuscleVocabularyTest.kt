package wallcrawl.elopenmike.com.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MuscleVocabularyTest {

    @Test
    fun canonicalize_mapsUpstreamSpellingOntoThePersistedName() {
        // The catalog says "Quads" for 35 exercises while profiles persist "Quadriceps";
        // before they were reconciled the planner never matched a squat by its primary mover.
        assertThat(MuscleVocabulary.canonicalize("Quads"))
            .containsExactly(StandardMuscles.QUADS)
        assertThat(StandardMuscles.QUADS).isEqualTo("Quadriceps")
    }

    @Test
    fun canonicalize_isCaseAndWhitespaceInsensitive() {
        assertThat(MuscleVocabulary.canonicalize("  qUaDs  "))
            .containsExactly(StandardMuscles.QUADS)
    }

    @Test
    fun canonicalize_expandsUmbrellaTermsIntoEveryGroupTheyCover() {
        assertThat(MuscleVocabulary.canonicalize("Posterior Chain"))
            .containsExactly(
                StandardMuscles.GLUTES,
                StandardMuscles.HAMSTRINGS,
                StandardMuscles.LOWER_BACK
            )
        assertThat(MuscleVocabulary.canonicalize("Legs"))
            .containsExactly(
                StandardMuscles.QUADS,
                StandardMuscles.HAMSTRINGS,
                StandardMuscles.GLUTES
            )
    }

    @Test
    fun canonicalize_collapsesRetiredAliases() {
        assertThat(MuscleVocabulary.canonicalize("Abs")).containsExactly(StandardMuscles.CORE)
        assertThat(MuscleVocabulary.canonicalize("Groin"))
            .containsExactly(StandardMuscles.ADDUCTORS)
        assertThat(MuscleVocabulary.canonicalize("Grip"))
            .containsExactly(StandardMuscles.FOREARMS)
    }

    @Test
    fun canonicalize_blankInputYieldsNothing() {
        assertThat(MuscleVocabulary.canonicalize("   ")).isEmpty()
    }

    @Test
    fun canonicalize_unknownNamePassesThroughInsteadOfDisappearing() {
        // Losing the value entirely would leave an exercise with no muscles at all, which no
        // split can ever match. Passing it through degrades to "unmatched" instead.
        assertThat(MuscleVocabulary.canonicalize("Serratus")).containsExactly("Serratus")
        assertThat(MuscleVocabulary.isCanonical("Serratus")).isFalse()
    }

    @Test
    fun canonicalizeAll_expandsAndDeduplicates() {
        assertThat(MuscleVocabulary.canonicalizeAll(listOf("Legs", "Quads", "Glutes")))
            .containsExactly(
                StandardMuscles.QUADS,
                StandardMuscles.HAMSTRINGS,
                StandardMuscles.GLUTES
            )
    }

    @Test
    fun conditioningTagsAreNotTrainableVolumeTargets() {
        assertThat(MuscleVocabulary.isTrainable(StandardMuscles.CARDIO)).isFalse()
        assertThat(MuscleVocabulary.isTrainable(StandardMuscles.MOBILITY)).isFalse()
        assertThat(MuscleVocabulary.isTrainable(StandardMuscles.QUADS)).isTrue()
    }

    @Test
    fun everyTrainableGroupIsOfferedAsAMusclePriority() {
        assertThat(StandardMuscles.TRAINABLE).containsNoDuplicates()
        StandardMuscles.TRAINABLE.forEach { muscle ->
            assertThat(MuscleVocabulary.isCanonical(muscle)).isTrue()
        }
    }

    @Test
    fun canonicalizingACanonicalNameIsStable() {
        StandardMuscles.ALL.forEach { muscle ->
            assertThat(MuscleVocabulary.canonicalize(muscle)).containsExactly(muscle)
        }
    }

    @Test
    fun defaultProfilePrioritiesUseCanonicalNames() {
        UserProfile().musclePriorities.keys.forEach { muscle ->
            assertThat(MuscleVocabulary.isCanonical(muscle)).isTrue()
        }
    }
}
