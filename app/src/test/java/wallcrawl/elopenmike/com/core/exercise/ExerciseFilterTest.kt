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
        // Bodyweight-only is now the safe default, so confirm equipment for a chest
        // movement explicitly rather than relying on an assumed full gym.
        val profile = UserProfile(
            availableEquipment = listOf(StandardEquipment.DUMBBELL, StandardEquipment.BENCH)
        )
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

    @Test
    fun filterCandidates_includesUnreviewedExerciseWhenListedEquipmentMatches() {
        val reviewed = allExercises.first()
        val unreviewed = reviewed.copy(id = "catalog-only-exercise", programming = null)

        val candidates = filter.filterCandidates(
            allExercises = listOf(reviewed, unreviewed),
            profile = UserProfile(
                availableEquipment = listOf(StandardEquipment.DUMBBELL, StandardEquipment.BENCH)
            )
        )

        assertThat(candidates.map { it.id }).containsExactly(reviewed.id, unreviewed.id)
    }

    @Test
    fun filterCandidates_requiresEveryListedEquipmentItemForUnreviewedExercise() {
        val unreviewed = allExercises.first().copy(
            id = "dumbbell-bench-movement",
            listedEquipment = listOf(StandardEquipment.DUMBBELL, StandardEquipment.BENCH),
            programming = null
        )

        val withoutBench = filter.filterCandidates(
            allExercises = listOf(unreviewed),
            profile = UserProfile(availableEquipment = listOf(StandardEquipment.DUMBBELL))
        )
        val withBench = filter.filterCandidates(
            allExercises = listOf(unreviewed),
            profile = UserProfile(
                availableEquipment = listOf(StandardEquipment.DUMBBELL, StandardEquipment.BENCH)
            )
        )

        assertThat(withoutBench).isEmpty()
        assertThat(withBench.map { it.id }).containsExactly(unreviewed.id)
    }

    @Test
    fun filterCandidates_acceptsAnyCompleteEquipmentCombination() {
        val romanianDeadlift = allExercises.single { it.id == "romanian-deadlift" }
        val dumbbellOnlyProfile = UserProfile(
            availableEquipment = listOf(StandardEquipment.DUMBBELL)
        )

        val candidates = filter.filterCandidates(
            allExercises = listOf(romanianDeadlift),
            profile = dumbbellOnlyProfile
        )

        assertThat(candidates.map { it.id }).containsExactly("romanian-deadlift")
    }
}
