package wallcrawl.elopenmike.com.core.exercise

import com.google.common.truth.Truth.assertThat
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.UserProfile
import org.junit.Before
import org.junit.Test

class ExerciseFilterTest {

    private lateinit var filter: ExerciseFilter
    private val allExercises = InMemoryExerciseCatalog.SAMPLE_EXERCISES

    @Before
    fun setup() {
        filter = ExerciseFilter()
    }

    @Test
    fun filterCandidates_withOnlyDumbbells_excludesBarbellAndCableExercises() {
        val dumbbellProfile = UserProfile(
            availableEquipment = listOf(
                StandardEquipment.DUMBBELL,
                StandardEquipment.BENCH,
                StandardEquipment.BODYWEIGHT
            )
        )

        val candidates = filter.filterCandidates(allExercises, dumbbellProfile)

        assertThat(candidates).isNotEmpty()
        // Should contain dumbbell press & lateral raise & dips
        assertThat(candidates.map { it.id }).contains("incline-dumbbell-press")
        assertThat(candidates.map { it.id }).contains("dumbbell-lateral-raise")

        // Should NOT contain barbell-only or cable-only exercises
        assertThat(candidates.map { it.id }).doesNotContain("barbell-deadlift")
        assertThat(candidates.map { it.id }).doesNotContain("cable-triceps-pushdown")
    }

    @Test
    fun filterCandidates_withExclusions_excludesSpecifiedExercises() {
        val profileWithExclusion = UserProfile(
            excludedExerciseIds = listOf("incline-dumbbell-press", "barbell-back-squat")
        )

        val candidates = filter.filterCandidates(allExercises, profileWithExclusion)

        assertThat(candidates.map { it.id }).doesNotContain("incline-dumbbell-press")
        assertThat(candidates.map { it.id }).doesNotContain("barbell-back-squat")
    }

    @Test
    fun filterCandidates_withTargetMuscles_returnsOnlyMatchingMuscles() {
        val profile = UserProfile()
        val chestCandidates = filter.filterCandidates(
            allExercises = allExercises,
            profile = profile,
            targetMuscles = listOf(StandardMuscles.CHEST)
        )

        assertThat(chestCandidates).isNotEmpty()
        assertThat(chestCandidates.all {
            it.primaryMuscles.contains(StandardMuscles.CHEST) || it.secondaryMuscles.contains(StandardMuscles.CHEST)
        }).isTrue()
    }
}
