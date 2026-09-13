package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import java.io.InputStream
import org.junit.Assert.assertThrows
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.SetType
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
        assertThat(fixture.policyVersion).isEqualTo(4)
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
        assertThat(readExpectedTargetWeights(fixture.expected))
            .containsExactly("incline-dumbbell-press", 20.0)
    }

    @Test
    fun loadCorpus_usesManifestResourceInsteadOfFilesystemEnumeration() {
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
            "reviewed-enabled-no-approved",
            "reviewed-enabled-uncleared-joint-constraint",
            "reviewed-enabled-cleared-joint-constraints",
            "concurrent-activity"
        ).inOrder()
        assertThat(fixtures.map { it.id }).doesNotContain("valid-basic")
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
        // impactLevel is the only source for LOW_IMPACT_ONLY, in fixtures as in production.
        assertFormatError(
            "planner-fixtures/invalid-low-impact-clearance.json",
            "syntheticClearedTrainingConstraints"
        )
    }

    @Test
    fun loadResource_rejectsDuplicateArraysAndContradictoryExpectations() {
        assertFormatError("planner-fixtures/invalid-duplicate-array.json", "profile.goals[1]")
        assertFormatError("planner-fixtures/invalid-contradictory-expected.json", "expected")
    }

    @Test
    fun loadResource_rejectsSuccessOnlyAssertionsOnFailureOutcomes() {
        assertFormatError(
            "planner-fixtures/invalid-failure-outcome-success-assertions.json",
            "expected.requiredExerciseIds"
        )
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
    fun loadResource_rejectsInvalidExpectedTargetWeightMaps() {
        assertInlineFormatError(
            id = "invalid-expected-target-weights",
            expectedExtras = """,
                    "expectedTargetWeights": {
                      "bad id": -1.0
                    }""",
            expectedMessageFragment = "expected.expectedTargetWeights.bad id"
        )
    }

    @Test
    fun loadResource_rejectsRequiredAnyExerciseIdGroupsThatConflictWithForbiddenIds() {
        assertInlineFormatError(
            id = "invalid-required-any-group",
            expectedExtras = """,
                    "requiredAnyExerciseIdGroups": [
                      ["push-up"]
                    ]""",
            forbiddenExerciseIds = """["push-up"]""",
            expectedMessageFragment = "requiredAnyExerciseIdGroups"
        )
    }

    @Test
    fun loadResource_parsesDeclaredCompletedSessions() {
        val fixture = loadInline(
            "completed-sessions",
            reviewedEligibility = REVIEWED_ELIGIBILITY_FRAGMENT,
            rootExtras = """
                ,
                  "completedSessions": [
                    {
                      "id": "resistance-monday",
                      "completedDayOffset": 0,
                      "exercises": [
                        {
                          "exerciseId": "push-up",
                          "sets": [
                            {"type": "WARMUP", "isCompleted": true},
                            {"type": "NORMAL", "isCompleted": true},
                            {"type": "NORMAL", "isCompleted": false}
                          ]
                        }
                      ]
                    }
                  ]
            """.trimIndent()
        )

        val session = fixture.completedSessions.single()
        assertThat(session.id).isEqualTo("resistance-monday")
        assertThat(session.completedDayOffset).isEqualTo(0)
        val exercise = session.exercises.single()
        assertThat(exercise.exerciseId).isEqualTo("push-up")
        assertThat(exercise.sets.map { it.type })
            .containsExactly(SetType.WARMUP, SetType.NORMAL, SetType.NORMAL)
            .inOrder()
        assertThat(exercise.sets.map { it.isCompleted })
            .containsExactly(true, true, false)
            .inOrder()
    }

    @Test
    fun loadResource_rejectsUnknownFieldsInsideCompletedSessions() {
        assertInlineFormatError(
            id = "completed-session-unknown-field",
            reviewedEligibility = REVIEWED_ELIGIBILITY_FRAGMENT,
            rootExtras = """
                ,
                  "completedSessions": [
                    {
                      "id": "resistance-monday",
                      "completedDayOffset": 0,
                      "note": "not a supported field",
                      "exercises": []
                    }
                  ]
            """.trimIndent(),
            expectedMessageFragment = "root.completedSessions[0].note"
        )
    }

    @Test
    fun loadResource_rejectsDuplicateCompletedSessionIds() {
        assertInlineFormatError(
            id = "completed-session-duplicate-id",
            reviewedEligibility = REVIEWED_ELIGIBILITY_FRAGMENT,
            rootExtras = """
                ,
                  "completedSessions": [
                    {"id": "same", "completedDayOffset": 0, "exercises": []},
                    {"id": "same", "completedDayOffset": 1, "exercises": []}
                  ]
            """.trimIndent(),
            expectedMessageFragment = "root.completedSessions[1].id"
        )
    }

    @Test
    fun loadResource_rejectsACompletedDayOutsideTheIsoWeek() {
        assertInlineFormatError(
            id = "completed-session-out-of-week",
            reviewedEligibility = REVIEWED_ELIGIBILITY_FRAGMENT,
            rootExtras = """
                ,
                  "completedSessions": [
                    {"id": "next-week", "completedDayOffset": 7, "exercises": []}
                  ]
            """.trimIndent(),
            expectedMessageFragment = "root.completedSessions[0].completedDayOffset"
        )
    }

    @Test
    fun loadResource_rejectsMoreCompletedSessionsThanTheCorpusBoundAllows() {
        // The bound is a documented contract, so widening or dropping it has to fail here
        // rather than silently disagreeing with the documentation.
        assertInlineFormatError(
            id = "completed-sessions-over-bound",
            reviewedEligibility = REVIEWED_ELIGIBILITY_FRAGMENT,
            rootExtras = """
                ,
                  "completedSessions": [
                    {"id": "session-1", "completedDayOffset": 1, "exercises": []},
                    {"id": "session-2", "completedDayOffset": 2, "exercises": []},
                    {"id": "session-3", "completedDayOffset": 3, "exercises": []},
                    {"id": "session-4", "completedDayOffset": 4, "exercises": []},
                    {"id": "session-5", "completedDayOffset": 5, "exercises": []},
                    {"id": "session-6", "completedDayOffset": 6, "exercises": []},
                    {"id": "session-7", "completedDayOffset": 0, "exercises": []},
                    {"id": "session-8", "completedDayOffset": 1, "exercises": []},
                    {"id": "session-9", "completedDayOffset": 2, "exercises": []}
                  ]
            """.trimIndent(),
            expectedMessageFragment = "root.completedSessions must contain at most 8 item(s)."
        )
    }

    @Test
    fun loadResource_rejectsCompletedSessionsWithoutReviewedEligibility() {
        // Production composes a weekly ledger only behind the reviewed gate, so a legacy
        // fixture that declared completed sessions would describe a context the app cannot
        // build.
        assertInlineFormatError(
            id = "completed-sessions-on-legacy-path",
            rootExtras = """
                ,
                  "completedSessions": [
                    {"id": "resistance-monday", "completedDayOffset": 0, "exercises": []}
                  ]
            """.trimIndent(),
            expectedMessageFragment = "requires root.reviewedEligibility"
        )
    }

    @Test
    fun loadResource_defaultsToARawValidWholeProgramExpectation() {
        val fixture = loadInline("whole-program-default")

        assertThat(fixture.expected.wholeProgramOutcome)
            .isEqualTo(RecommendationOutcome.VALID)
        assertThat(fixture.expected.wholeProgramRepairReasonCodes).isEmpty()
    }

    @Test
    fun loadResource_rejectsAnUnknownWholeProgramOutcome() {
        assertInlineFormatError(
            id = "whole-program-unknown-outcome",
            expectedExtras = """,
                    "wholeProgramOutcome": "REWRITTEN"""",
            expectedMessageFragment = "expected.wholeProgramOutcome"
        )
    }

    @Test
    fun loadResource_rejectsWholeProgramExpectationsOnAFailureOutcome() {
        assertInlineFormatError(
            id = "whole-program-on-failure",
            outcome = "NO_STRENGTH_CANDIDATES",
            expectedExtras = """,
                    "wholeProgramOutcome": "VALID"""",
            expectedMessageFragment = "expected.wholeProgramOutcome is only supported"
        )
    }

    @Test
    fun loadResource_rejectsRepairReasonCodesWithoutARepairedOutcome() {
        assertInlineFormatError(
            id = "whole-program-codes-without-repair",
            expectedExtras = """,
                    "wholeProgramRepairReasonCodes": ["WEEKLY_ALLOWANCE_EXCEEDED"]""",
            expectedMessageFragment =
                "is only supported when expected.wholeProgramOutcome is REPAIRED"
        )
    }

    @Test
    fun loadResource_rejectsARepairedOutcomeThatNamesNoReasonCodes() {
        assertInlineFormatError(
            id = "whole-program-repair-without-codes",
            reviewedEligibility = REVIEWED_ELIGIBILITY_FRAGMENT,
            expectedExtras = """,
                    "wholeProgramOutcome": "REPAIRED"""",
            expectedMessageFragment =
                "is required when expected.wholeProgramOutcome is REPAIRED"
        )
    }

    @Test
    fun loadResource_rejectsARepairedOutcomeWithoutReviewedEligibility() {
        // The only repairable violation is an exceeded configured weekly allowance, and
        // aggregate dose accounting runs only on the reviewed path.
        assertInlineFormatError(
            id = "whole-program-repair-on-legacy-path",
            expectedExtras = """,
                    "wholeProgramOutcome": "REPAIRED",
                    "wholeProgramRepairReasonCodes": ["WEEKLY_ALLOWANCE_EXCEEDED"]""",
            expectedMessageFragment = "REPAIRED requires root.reviewedEligibility"
        )
    }

    @Test
    fun loadResource_rejectsMalformedJsonAndMissingResources() {
        assertFormatError("planner-fixtures/malformed.json", "planner-fixtures/malformed.json")

        val error = assertThrows(PlannerFixtureFormatException::class.java) {
            loader.loadResource("planner-fixtures/missing.json")
        }
        assertThat(error.message).contains("planner-fixtures/missing.json")
    }

    /**
     * Loads a fixture assembled in the test rather than committed beside the corpus.
     *
     * The manifest is the authoritative persona roster, so a schema guard that needs one
     * more shape does not deserve another resource file beside the committed personas.
     */
    private fun loadInline(
        id: String,
        outcome: String = "SUCCESS",
        reviewedEligibility: String = "",
        rootExtras: String = "",
        expectedExtras: String = "",
        forbiddenExerciseIds: String = "[]"
    ): PlannerFixture {
        val path = "planner-fixtures/inline-$id.json"
        return loaderWithResource(
            resourcePath = path,
            resourceContents = inlineFixtureJson(
                id = id,
                outcome = outcome,
                reviewedEligibility = reviewedEligibility,
                rootExtras = rootExtras,
                expectedExtras = expectedExtras,
                forbiddenExerciseIds = forbiddenExerciseIds
            )
        ).loadResource(path)
    }

    private fun assertInlineFormatError(
        id: String,
        outcome: String = "SUCCESS",
        reviewedEligibility: String = "",
        rootExtras: String = "",
        expectedExtras: String = "",
        forbiddenExerciseIds: String = "[]",
        expectedMessageFragment: String
    ) {
        val error = assertThrows(PlannerFixtureFormatException::class.java) {
            loadInline(
                id = id,
                outcome = outcome,
                reviewedEligibility = reviewedEligibility,
                rootExtras = rootExtras,
                expectedExtras = expectedExtras,
                forbiddenExerciseIds = forbiddenExerciseIds
            )
        }

        assertThat(error.message).contains(expectedMessageFragment)
    }

    private fun inlineFixtureJson(
        id: String,
        outcome: String,
        reviewedEligibility: String,
        rootExtras: String,
        expectedExtras: String,
        forbiddenExerciseIds: String
    ): String = """
        {
          "schemaVersion": 1,
          "id": "inline-$id",
          "policyVersion": 4,
          "catalogVersion": "test-catalog-2026-08-30",
          "profile": {
            "goals": ["BUILD_MUSCLE"],
            "experienceLevel": "BEGINNER",
            "preferredDurationMinutes": 45,
            "daysPerWeek": 3,
            "availableEquipment": ["Bodyweight"],
            "preferredUnit": "LBS",
            "musclePriorities": {"Chest": "HIGH"},
            "excludedExerciseIds": [],
            "trainingConstraints": [],
            "returningAfterBreakWeeks": 0,
            "confirmedStartingLoads": {},
            "movementCapabilities": {}
          },
          "completedWorkoutCount": 0,
          "exerciseHistory": []$reviewedEligibility$rootExtras,
          "expected": {
            "outcome": "$outcome",
            "requiredExerciseIds": [],
            "forbiddenExerciseIds": $forbiddenExerciseIds$expectedExtras
          }
        }
    """.trimIndent()

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

    @Suppress("UNCHECKED_CAST")
    private fun readExpectedTargetWeights(expected: Any): Map<String, Double> {
        val getter = expected.javaClass.methods.singleOrNull {
            it.name == "getExpectedTargetWeights" && it.parameterCount == 0
        } ?: error("PlannerFixtureExpected must expose expectedTargetWeights.")
        val value = getter.invoke(expected) as Map<*, *>
        return value.mapKeys { it.key as String }.mapValues { (_, weight) -> (weight as Number).toDouble() }
    }

    private companion object {
        /** Minimal reviewed-enabled block, so reviewed-only guards can be exercised. */
        const val REVIEWED_ELIGIBILITY_FRAGMENT = """,
          "reviewedEligibility": {
            "adaptationState": "BUILD",
            "syntheticApprovedExerciseIds": ["push-up"]
          }"""
    }
}
