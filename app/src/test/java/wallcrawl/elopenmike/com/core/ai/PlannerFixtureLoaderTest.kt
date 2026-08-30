package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import java.io.InputStream
import org.junit.Assert.assertThrows
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.WeightUnit

class PlannerFixtureLoaderTest {

    private val loader = PlannerFixtureLoader()

    @Test
    fun loadResource_parsesValidFixtureAndNormalizesCapabilities() {
        val fixture = loader.loadResource("planner-fixtures/valid-basic.json")

        assertThat(fixture.schemaVersion).isEqualTo(1)
        assertThat(fixture.id).isEqualTo("valid-basic")
        assertThat(fixture.policyVersion).isEqualTo(3)
        assertThat(fixture.catalogVersion).isEqualTo("test-catalog-2026-08-30")
        assertThat(fixture.profile.goals)
            .containsExactly(FitnessGoal.BUILD_MUSCLE, FitnessGoal.GENERAL_FITNESS)
        assertThat(fixture.profile.experienceLevel.name).isEqualTo("BEGINNER")
        assertThat(fixture.profile.preferredDurationMinutes).isEqualTo(45)
        assertThat(fixture.profile.daysPerWeek).isEqualTo(3)
        assertThat(fixture.profile.availableEquipment)
            .containsExactly(StandardEquipment.BODYWEIGHT, StandardEquipment.DUMBBELL)
            .inOrder()
        assertThat(fixture.profile.preferredUnit).isEqualTo(WeightUnit.KG)
        assertThat(fixture.profile.musclePriorities)
            .containsExactly("Chest", PriorityLevel.HIGH, "Core", PriorityLevel.NORMAL)
        assertThat(fixture.profile.excludedExerciseIds).containsExactly("barbell-bench-press")
        assertThat(fixture.profile.trainingConstraints)
            .containsExactly(TrainingConstraint.LOW_IMPACT_ONLY)
        assertThat(fixture.profile.returningAfterBreakWeeks).isEqualTo(6)
        assertThat(fixture.profile.confirmedStartingLoads)
            .containsExactly("incline-dumbbell-press", 20.0)
        assertThat(fixture.profile.movementCapabilities[MovementCapabilityType.IMPACT])
            .isEqualTo(CapabilityLevel.LIMITED)
        MovementCapabilityType.entries
            .filterNot { it == MovementCapabilityType.IMPACT }
            .forEach { type ->
                assertThat(fixture.profile.movementCapabilities[type])
                    .isEqualTo(CapabilityLevel.UNKNOWN)
            }
        assertThat(fixture.completedWorkoutCount).isEqualTo(12)
        assertThat(fixture.exerciseHistory).hasSize(1)
        val history = fixture.exerciseHistory.single()
        assertThat(history.exerciseId).isEqualTo("incline-dumbbell-press")
        assertThat(history.lastWeight).isEqualTo(20.0)
        assertThat(history.lastReps).isEqualTo(10)
        assertThat(history.bestEstimated1RM).isWithin(0.0001).of(26.7)
        assertThat(history.recentSets.map { it.id })
            .containsExactly(
                "incline-dumbbell-press-recent-set-1",
                "incline-dumbbell-press-recent-set-2"
            )
            .inOrder()
        assertThat(fixture.expected.outcome).isEqualTo(PlannerFixtureOutcome.SUCCESS)
        assertThat(fixture.expected.requiredExerciseIds)
            .containsExactly("incline-dumbbell-press")
        assertThat(fixture.expected.forbiddenExerciseIds)
            .containsExactly("barbell-bench-press")
    }

    @Test
    fun loadCorpus_usesManifestResourceInsteadOfFilesystemEnumeration() {
        val fixtures = loader.loadCorpus()

        assertThat(fixtures.map { it.id }).containsExactly("valid-basic").inOrder()
    }

    @Test
    fun loadResource_rejectsUnknownFieldsAtEveryObjectLevel() {
        assertFormatError("planner-fixtures/invalid-unknown-root-field.json", "root.bonus")
        assertFormatError("planner-fixtures/invalid-unknown-profile-field.json", "profile.nickname")
        assertFormatError("planner-fixtures/invalid-unknown-history-field.json", "root.exerciseHistory[0].note")
        assertFormatError("planner-fixtures/invalid-unknown-set-field.json", "root.exerciseHistory[0].recentSets[0].tempo")
        assertFormatError("planner-fixtures/invalid-unknown-expected-field.json", "root.expected.notes")
    }

    @Test
    fun loadResource_rejectsDuplicateObjectFieldsBeforeParsing() {
        assertFormatError("planner-fixtures/invalid-duplicate-field.json", "root.id")
    }

    @Test
    fun loadResource_rejectsExcessiveJsonNestingBeforeOverflowingStack() {
        val resourcePath = "planner-fixtures/excessive-nesting.json"
        val loader = loaderWithResource(
            resourcePath = resourcePath,
            resourceContents = deeplyNestedArrayJson(depth = 20_000)
        )

        val error = assertThrows(PlannerFixtureFormatException::class.java) {
            loader.loadResource(resourcePath)
        }

        assertThat(error.message).isEqualTo("Planner fixture JSON exceeds maximum nesting depth.")
    }

    @Test
    fun loadResource_rejectsUnsafeIdsAndUnsupportedSchema() {
        assertFormatError("planner-fixtures/invalid-unsafe-id.json", "root.id")
        assertFormatError("planner-fixtures/invalid-unsupported-schema.json", "root.schemaVersion")
    }

    @Test
    fun loadResource_rejectsUnknownEnumsAndConstants() {
        assertFormatError("planner-fixtures/invalid-unknown-enum.json", "profile.experienceLevel")
        assertFormatError("planner-fixtures/invalid-unknown-equipment.json", "profile.availableEquipment[1]")
        assertFormatError("planner-fixtures/invalid-unknown-muscle.json", "profile.musclePriorities.Serratus")
        assertFormatError("planner-fixtures/invalid-unknown-capability.json", "profile.movementCapabilities.FLYING")
    }

    @Test
    fun loadResource_rejectsDuplicateArraysAndContradictoryExpectations() {
        assertFormatError("planner-fixtures/invalid-duplicate-array.json", "profile.goals[1]")
        assertFormatError("planner-fixtures/invalid-contradictory-expected.json", "expected")
    }

    @Test
    fun loadResource_rejectsOutOfRangeNonFiniteOversizedInputs() {
        assertFormatError("planner-fixtures/invalid-out-of-range-number.json", "profile.preferredDurationMinutes")
        assertFormatError("planner-fixtures/invalid-non-finite-number.json", "profile.confirmedStartingLoads.incline-dumbbell-press")
        assertFormatError("planner-fixtures/invalid-oversized-string.json", "root.catalogVersion")
        assertFormatError("planner-fixtures/invalid-oversized-collection.json", "profile.excludedExerciseIds")
        assertFormatError("planner-fixtures/oversized-resource.json", "planner-fixtures/oversized-resource.json")
    }

    @Test
    fun loadResource_rejectsMalformedJsonAndMissingResources() {
        assertFormatError("planner-fixtures/malformed.json", "planner-fixtures/malformed.json")

        val error = assertThrows(PlannerFixtureFormatException::class.java) {
            loader.loadResource("planner-fixtures/missing.json")
        }
        assertThat(error.message).contains("planner-fixtures/missing.json")
    }

    private fun assertFormatError(resourcePath: String, expectedMessageFragment: String) {
        val error = assertThrows(PlannerFixtureFormatException::class.java) {
            loader.loadResource(resourcePath)
        }

        assertThat(error.message).contains(expectedMessageFragment)
    }

    private fun loaderWithResource(resourcePath: String, resourceContents: String): PlannerFixtureLoader =
        PlannerFixtureLoader(
            classLoader = object : ClassLoader(PlannerFixtureLoader::class.java.classLoader) {
                override fun getResourceAsStream(name: String): InputStream? =
                    if (name == resourcePath) {
                        resourceContents.byteInputStream()
                    } else {
                        super.getResourceAsStream(name)
                    }
            }
        )

    private fun deeplyNestedArrayJson(depth: Int): String = buildString(depth * 2 + 2) {
        repeat(depth) { append('[') }
        append("{}")
        repeat(depth) { append(']') }
    }
}
