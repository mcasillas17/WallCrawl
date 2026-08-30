package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.WeightUnit

class PlannerFixtureCorpusTest {

    private val loader = PlannerFixtureLoader()
    private val contextFactory = PlannerFixtureContextFactory()

    @Test
    fun loadCorpus_containsExactlyTheTask2PersonaFixtures() {
        val fixtures = loader.loadCorpus()

        assertThat(fixtures.map { it.id }).containsExactly(
            "bodyweight-beginner",
            "band-only",
            "machine-only",
            "full-gym-advanced",
            "returning-user",
            "limited-capability",
            "mixed-unit-history",
            "sparse-history",
            "no-strength-candidates"
        ).inOrder()
        assertThat(fixtures.map { it.id }.distinct()).hasSize(9)
    }

    @Test
    fun loadCorpus_buildsRealProfilesAndContextsFromBundledCatalog() {
        val fixtures = loader.loadCorpus()

        val realized = fixtures.map(contextFactory::create)

        assertThat(realized.map { it.catalogExercises.size }.distinct()).containsExactly(302)
        realized.forEach { built ->
            assertThat(built.userProfile.availableEquipment)
                .containsExactlyElementsIn(built.fixture.profile.availableEquipment)
                .inOrder()
            assertThat(built.context.userProfile).isEqualTo(built.userProfile)
            assertThat(built.context.exerciseHistory.keys)
                .containsExactlyElementsIn(built.fixture.exerciseHistory.map { it.exerciseId })
            assertThat(built.context.exerciseHistory.size).isAtMost(8)
            assertThat(built.context.allowedExercises.map(Exercise::id))
                .containsNoDuplicates()
            val catalogIds = built.catalogExercises.map(Exercise::id).toSet()
            val allowedIds = built.context.allowedExercises.map(Exercise::id).toSet()
            assertThat(catalogIds.containsAll(allowedIds)).isTrue()
            assertThat(catalogIds.containsAll(built.fixture.allowedExerciseIds.toSet())).isTrue()
        }
    }

    @Test
    fun helper_readsBundledCatalogFromUnitTestRuntime() {
        val catalogText = contextFactory.readResourceText("workout-guide/catalog.json")

        assertThat(catalogText).contains("\"exercises\"")
    }

    @Test
    fun loadCorpus_appliesRealFilterBeforeOptionalAllowedExerciseRestriction() {
        val fixture = loader.loadCorpus().single { it.id == "no-strength-candidates" }

        val built = contextFactory.create(fixture)

        assertThat(fixture.allowedExerciseIds).contains("lat-pulldown")
        assertThat(built.filteredExercises.map(Exercise::id)).contains("bodyweight-squat")
        assertThat(built.filteredExercises.map(Exercise::id)).doesNotContain("lat-pulldown")
        assertThat(built.context.allowedExercises.map(Exercise::id))
            .containsExactly("arm-circles", "jump-rope", "walking")
            .inOrder()
        assertThat(built.context.allowedExercises.all(::isNonStrengthCandidate)).isTrue()
    }

    @Test
    fun loadCorpus_filteredCandidatesHonorAvailableEquipmentAndOutcomePremises() {
        val fixtures = loader.loadCorpus()

        fixtures.map(contextFactory::create).forEach { built ->
            assertThat(built.filteredExercises.all { candidate ->
                hasSatisfiedEquipment(candidate, built.userProfile.availableEquipment)
            }).isTrue()
            if (built.fixture.expected.outcome == PlannerFixtureOutcome.SUCCESS) {
                assertThat(built.context.allowedExercises.any(::isStrengthCandidate)).isTrue()
            }
        }
    }

    @Test
    fun loadCorpus_normalizesMovementCapabilitiesThroughTheLoader() {
        val fixtures = loader.loadCorpus()

        fixtures.forEach { fixture ->
            assertThat(fixture.profile.movementCapabilities.values.keys)
                .containsExactlyElementsIn(MovementCapabilityType.entries)
        }

        val limited = fixtures.single { it.id == "limited-capability" }
        assertThat(limited.profile.movementCapabilities[MovementCapabilityType.IMPACT])
            .isEqualTo(CapabilityLevel.AVOID)
        assertThat(
            limited.profile.movementCapabilities[MovementCapabilityType.FLOOR_TRANSITION]
        ).isEqualTo(CapabilityLevel.LIMITED)
        assertThat(
            limited.profile.movementCapabilities[MovementCapabilityType.CONTINUOUS_ACTIVITY]
        ).isEqualTo(CapabilityLevel.UNKNOWN)
    }

    @Test
    fun loadCorpus_buildsMixedUnitAndSparseHistoryContexts() {
        val fixturesById = loader.loadCorpus().associateBy { it.id }

        val mixed = contextFactory.create(checkNotNull(fixturesById["mixed-unit-history"]))
        val sparse = contextFactory.create(checkNotNull(fixturesById["sparse-history"]))

        assertThat(mixed.userProfile.preferredUnit).isEqualTo(WeightUnit.KG)
        assertThat(mixed.context.exerciseHistory.getValue("incline-dumbbell-press").lastWeight)
            .isWithin(0.0001)
            .of(27.5)
        assertThat(mixed.context.exerciseHistory.getValue("bodyweight-squat").recentSets).hasSize(1)

        assertThat(sparse.context.exerciseHistory.keys).containsExactly("pull-ups")
        val sparsePullUps = sparse.context.exerciseHistory.getValue("pull-ups")
        assertThat(sparsePullUps.lastWeight).isNull()
        assertThat(sparsePullUps.bestEstimated1RM).isNull()
        assertThat(sparsePullUps.recentSets.single().exerciseType).isEqualTo(ExerciseType.BODYWEIGHT_REPS)
    }

    @Test
    fun loadCorpus_personaResourcesDoNotEmbedRawExerciseDefinitions() {
        val resourcePaths = contextFactory.manifestResourcePaths()

        resourcePaths.forEach { path ->
            val text = contextFactory.readResourceText(path)
            assertThat(text).doesNotContain("\"exerciseType\"")
            assertThat(text).doesNotContain("\"primaryMuscles\"")
            assertThat(text).doesNotContain("\"secondaryMuscles\"")
            assertThat(text).doesNotContain("\"listedEquipment\"")
            assertThat(text).doesNotContain("\"searchAliases\"")
            assertThat(text).doesNotContain("\"programming\"")
            assertThat(text).doesNotContain("\"sourceId\"")
        }
    }

    private fun isStrengthCandidate(exercise: Exercise): Boolean = when {
        exercise.isStretch -> false
        exercise.type == ExerciseType.DISTANCE_DURATION -> false
        exercise.type == ExerciseType.DURATION ->
            StandardEquipment.CARDIO !in exercise.listedEquipment
        else -> true
    }

    private fun isNonStrengthCandidate(exercise: Exercise): Boolean = !isStrengthCandidate(exercise)

    private fun hasSatisfiedEquipment(exercise: Exercise, ownedEquipment: List<String>): Boolean {
        val owned = ownedEquipment.toSet()
        val combinations = exercise.programming?.requiredEquipmentCombinations
            ?: listOf(exercise.listedEquipment.filter(String::isNotBlank))
        return combinations.isEmpty() || combinations.any { combination ->
            combination.all { it in owned }
        }
    }
}
