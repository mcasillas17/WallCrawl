package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityFailure
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityResult
import wallcrawl.elopenmike.com.core.model.EligibilityReason
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion

class PlannerFixtureCorpusTest {

    private val loader = PlannerFixtureLoader()
    private val contextFactory = PlannerFixtureContextFactory()

    @Test
    fun loadCorpus_containsLegacyAndReviewedEligibilityFixtures() {
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
            "no-strength-candidates",
            "reviewed-enabled-bodyweight",
            "reviewed-enabled-no-approved"
        ).inOrder()
        assertThat(fixtures.map { it.id }.distinct()).hasSize(11)
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
    fun bundledCatalogProjection_keepsAllAuthoredReviewedMetadataDraft() {
        val exercises = contextFactory.bundledCatalogProjection().exercises

        assertThat(exercises).hasSize(302)
        assertThat(exercises.count { it.reviewedMetadata?.reviewState == ReviewState.DRAFT })
            .isEqualTo(37)
        assertThat(exercises.count { it.reviewedMetadata?.reviewState == ReviewState.APPROVED })
            .isEqualTo(0)
    }

    @Test
    fun reviewedEnabledFixture_usesOnlyExplicitSyntheticApprovals() {
        val fixture = loader.loadCorpus().single { it.id == "reviewed-enabled-bodyweight" }
        val reviewedEligibility = requireNotNull(fixture.reviewedEligibility)
        val syntheticIds = reviewedEligibility.syntheticApprovedExerciseIds
        val built = contextFactory.create(fixture)
        val result = built.context.automaticEligibilityResult as
            AutomaticEligibilityResult.Candidates

        assertThat(built.context.trainingProgramState).isNotNull()
        assertThat(built.context.trainingProgramState?.adaptationState)
            .isEqualTo(reviewedEligibility.adaptationState)
        assertThat(built.context.trainingProgramState?.weeklyLedger?.policyVersion)
            .isEqualTo(LedgerPolicyVersion.PRIMARY_ONLY_V1)
        assertThat(built.context.trainingProgramState?.weeklyLedger?.directPrimarySets)
            .isEmpty()
        assertThat(built.catalogExercises).hasSize(302)
        assertThat(
            built.catalogExercises.filter {
                it.reviewedMetadata?.reviewState == ReviewState.APPROVED
            }.map(Exercise::id)
        ).containsExactlyElementsIn(syntheticIds)
        assertThat(result.exercises.map(Exercise::id))
            .containsExactlyElementsIn(
                built.catalogExercises.filter { it.id in syntheticIds }.map(Exercise::id)
            )
            .inOrder()
        assertThat(result.decisions).hasSize(302)
        assertThat(result.decisions.filter { it.eligible }.map { it.exerciseId })
            .containsExactlyElementsIn(result.exercises.map(Exercise::id))
            .inOrder()
        result.exercises.forEach { exercise ->
            assertThat(exercise.reviewedMetadata!!.provenance.rationaleOrSource)
                .startsWith("SYNTHETIC PLANNER FIXTURE")
        }
    }

    @Test
    fun reviewedEnabledFixture_withoutSyntheticApprovalsFailsWithTypedReviewGateReason() {
        val fixture = loader.loadCorpus().single { it.id == "reviewed-enabled-no-approved" }
        val built = contextFactory.create(fixture)
        val result = built.context.automaticEligibilityResult as
            AutomaticEligibilityResult.NoCandidates

        assertThat(built.context.allowedExercises).isEmpty()
        assertThat(result.failure).isEqualTo(AutomaticEligibilityFailure.NO_APPROVED_METADATA)
        assertThat(result.decisions).hasSize(302)
        assertThat(result.decisions.all { decision ->
            !decision.eligible &&
                decision.reasons == listOf(EligibilityReason.MISSING_APPROVED_METADATA)
        }).isTrue()
    }

    @Test
    fun helper_readsBundledCatalogFromUnitTestRuntime() {
        val catalogText = contextFactory.readResourceText("workout-guide/catalog.json")

        assertThat(catalogText).contains("\"exercises\"")
    }

    @Test
    fun loadCorpus_usesPinnedCatalogCommitAndSupportedPolicyVersion() {
        val fixtures = loader.loadCorpus()

        fixtures.forEach { fixture ->
            assertThat(fixture.policyVersion).isEqualTo(3)
            assertThat(fixture.catalogVersion)
                .isEqualTo("ba0b709cb20430361b2cb33aaadd20998164a916")
        }
    }

    @Test
    fun loadCorpus_noStrengthCandidatesIsHarnessOnlyCardioFailureFixture() {
        val fixture = loader.loadCorpus().single { it.id == "no-strength-candidates" }
        val built = contextFactory.create(fixture)

        assertThat(fixture.profile.availableEquipment)
            .containsExactly(StandardEquipment.CARDIO)
        assertThat(fixture.allowedExerciseIds)
            .containsExactly("walking")
        assertThat(fixture.expected.requiredExerciseIds).isEmpty()
        assertThat(fixture.expected.forbiddenExerciseIds).isEmpty()
        assertThat(fixture.expected.requiredAnyExerciseIdGroups).isEmpty()
        assertThat(fixture.expected.expectedTargetWeights).isEmpty()
        assertThat(fixture.expected.workoutNameContains).isNull()
        assertThat(fixture.expected.maxTargetSetsPerExercise).isNull()
        assertThat(built.context.allowedExercises.map(Exercise::id))
            .containsExactly("walking")
    }

    @Test
    fun loadCorpus_filteredCandidatesHonorAvailableEquipmentAndConstructionPremises() {
        val fixtures = loader.loadCorpus()

        fixtures.map(contextFactory::create).forEach { built ->
            assertThat(built.filteredExercises.all { candidate ->
                hasSatisfiedEquipment(candidate, built.userProfile.availableEquipment)
            }).isTrue()
            if (built.fixture.expected.outcome == PlannerFixtureOutcome.SUCCESS) {
                assertThat(built.context.allowedExercises).isNotEmpty()
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
    fun loadCorpus_returningUserCuratesLowerDemandFullBodyCandidates() {
        val fixture = loader.loadCorpus().single { it.id == "returning-user" }

        assertThat(fixture.allowedExerciseIds)
            .containsExactly(
                "incline-dumbbell-press",
                "one-arm-dumbbell-row",
                "goblet-squat",
                "glute-bridge",
                "dead-bug"
            )
            .inOrder()
        assertThat(fixture.allowedExerciseIds)
            .containsNoneOf("ab-wheel", "single-leg-romanian-deadlift")
        assertThat(fixture.expected.workoutNameContains).isEqualTo("(Re-entry)")
        assertThat(fixture.expected.maxTargetSetsPerExercise).isEqualTo(2)
        assertThat(fixture.expected.expectedTargetWeights)
            .containsExactly("incline-dumbbell-press", 40.0)
    }

    @Test
    fun loadCorpus_bodyweightBeginnerModelsConservativeCuratedPushSubset() {
        val fixture = loader.loadCorpus().single { it.id == "bodyweight-beginner" }

        assertThat(fixture.allowedExerciseIds)
            .containsExactly("push-up", "knee-push-up", "bodyweight-squat", "dead-bug")
            .inOrder()
        assertThat(fixture.allowedExerciseIds)
            .containsNoneOf("bench-dip", "handstand-push-up", "burpee", "jump-squat")
        assertThat(fixture.expected.requiredAnyExerciseIdGroups)
            .containsExactly(setOf("knee-push-up", "push-up"))
    }

    @Test
    fun loadCorpus_limitedCapabilityModelsCuratedLegalCandidateSubset() {
        val fixture = loader.loadCorpus().single { it.id == "limited-capability" }

        assertThat(fixture.allowedExerciseIds)
            .containsExactly(
                "dumbbell-bench-press",
                "dumbbell-shoulder-press",
                "incline-dumbbell-press",
                "dumbbell-lateral-raise"
            )
            .inOrder()
        assertThat(fixture.allowedExerciseIds)
            .containsNoneOf("push-up", "bench-dip", "burpee", "jump-squat")
        assertThat(fixture.expected.requiredExerciseIds)
            .containsExactly("dumbbell-shoulder-press")
        assertThat(fixture.expected.forbiddenExerciseIds).isEmpty()
        assertThat(fixture.expected.expectedTargetWeights)
            .containsExactly("dumbbell-shoulder-press", 25.0)
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

        assertThat(sparse.userProfile.availableEquipment)
            .containsExactly(StandardEquipment.BODYWEIGHT, StandardEquipment.RESISTANCE_BAND)
            .inOrder()
        assertThat(sparse.context.exerciseHistory.keys).containsExactly("inverted-row")
        val sparseRow = sparse.context.exerciseHistory.getValue("inverted-row")
        assertThat(sparseRow.lastWeight).isNull()
        assertThat(sparseRow.bestEstimated1RM).isNull()
        assertThat(sparseRow.recentSets.single().exerciseType).isEqualTo(ExerciseType.BODYWEIGHT_REPS)
    }

    @Test
    fun loadCorpus_sparseHistoryUsesRegressionsInsteadOfHangingRequirement() {
        val fixture = loader.loadCorpus().single { it.id == "sparse-history" }

        assertThat(fixture.allowedExerciseIds)
            .containsExactly("inverted-row", "banded-lat-pulldown", "prone-y-raise")
            .inOrder()
        assertThat(fixture.expected.requiredAnyExerciseIdGroups)
            .containsExactly(setOf("inverted-row", "banded-lat-pulldown"))
    }

    @Test
    fun create_rejectsExpectedExerciseAssertionsThatReferenceMissingCatalogIds() {
        val fixture = loader.loadResource("planner-fixtures/valid-basic.json").copy(
            catalogVersion = "ba0b709cb20430361b2cb33aaadd20998164a916",
            expected = PlannerFixtureExpected(
                outcome = PlannerFixtureOutcome.SUCCESS,
                requiredExerciseIds = emptySet(),
                forbiddenExerciseIds = emptySet(),
                expectedTargetWeights = mapOf("not-in-catalog" to 10.0)
            )
        )

        val error = org.junit.Assert.assertThrows(PlannerFixtureFormatException::class.java) {
            contextFactory.create(fixture)
        }

        assertThat(error.message)
            .isEqualTo("root.expected.expectedTargetWeights.not-in-catalog references unknown bundled catalog id 'not-in-catalog'.")
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

    private fun hasSatisfiedEquipment(exercise: Exercise, ownedEquipment: List<String>): Boolean {
        val owned = ownedEquipment.toSet()
        val combinations = exercise.programming?.requiredEquipmentCombinations
            ?: listOf(exercise.listedEquipment.filter(String::isNotBlank))
        return combinations.isEmpty() || combinations.any { combination ->
            combination.all { it in owned }
        }
    }
}
