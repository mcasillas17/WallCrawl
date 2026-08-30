package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import java.io.InputStream
import org.junit.Assert.assertThrows
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.ExercisePerformanceHistory
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.WorkoutSet

class PlannerFixtureContextFactoryTest {

    private val loader = PlannerFixtureLoader()
    private val contextFactory = PlannerFixtureContextFactory()

    @Test
    fun create_rejectsCorpusFixturesWithUnsupportedPolicyVersion() {
        val fixture = loader.loadResource("planner-fixtures/band-only.json").copy(policyVersion = 2)

        val error = assertThrows(PlannerFixtureFormatException::class.java) {
            contextFactory.create(fixture)
        }

        assertThat(error.message)
            .isEqualTo(
                "root.policyVersion must equal supported corpus policy version 3."
            )
    }

    @Test
    fun create_rejectsCorpusFixturesWithCatalogVersionDifferentFromBundledSourceCommit() {
        val fixture = loader.loadResource("planner-fixtures/band-only.json").copy(
            catalogVersion = "not-the-bundled-source-commit"
        )

        val error = assertThrows(PlannerFixtureFormatException::class.java) {
            contextFactory.create(fixture)
        }

        assertThat(error.message)
            .isEqualTo(
                "root.catalogVersion must equal bundled catalog source.commit ba0b709cb20430361b2cb33aaadd20998164a916."
            )
    }

    @Test
    fun create_rejectsUnknownExerciseReferencesWithFieldPaths() {
        val base = corpusCompatibleFixture()
        val baseHistory = base.exerciseHistory.single()
        val cases = listOf(
            UnknownReferenceCase(
                fixture = base.copy(allowedExerciseIds = listOf("not-in-catalog")),
                path = "root.allowedExerciseIds[0]"
            ),
            UnknownReferenceCase(
                fixture = base.copy(
                    profile = base.profile.copy(excludedExerciseIds = listOf("not-in-catalog"))
                ),
                path = "root.profile.excludedExerciseIds[0]"
            ),
            UnknownReferenceCase(
                fixture = base.copy(
                    profile = base.profile.copy(
                        confirmedStartingLoads = linkedMapOf("not-in-catalog" to 10.0)
                    )
                ),
                path = "root.profile.confirmedStartingLoads.not-in-catalog"
            ),
            UnknownReferenceCase(
                fixture = base.copy(
                    exerciseHistory = listOf(baseHistory.copy(exerciseId = "not-in-catalog"))
                ),
                path = "root.exerciseHistory[0].exerciseId"
            ),
            UnknownReferenceCase(
                fixture = base.copy(
                    expected = base.expected.copy(
                        requiredExerciseIds = setOf("not-in-catalog"),
                        forbiddenExerciseIds = emptySet(),
                        requiredAnyExerciseIdGroups = emptyList(),
                        expectedTargetWeights = emptyMap()
                    )
                ),
                path = "root.expected.requiredExerciseIds[0]"
            ),
            UnknownReferenceCase(
                fixture = base.copy(
                    expected = base.expected.copy(
                        requiredExerciseIds = emptySet(),
                        forbiddenExerciseIds = emptySet(),
                        requiredAnyExerciseIdGroups = listOf(setOf("not-in-catalog")),
                        expectedTargetWeights = emptyMap()
                    )
                ),
                path = "root.expected.requiredAnyExerciseIdGroups[0][0]"
            ),
            UnknownReferenceCase(
                fixture = base.copy(
                    expected = base.expected.copy(
                        requiredExerciseIds = emptySet(),
                        forbiddenExerciseIds = emptySet(),
                        requiredAnyExerciseIdGroups = emptyList(),
                        expectedTargetWeights = linkedMapOf("not-in-catalog" to 10.0)
                    )
                ),
                path = "root.expected.expectedTargetWeights.not-in-catalog"
            ),
            UnknownReferenceCase(
                fixture = base.copy(
                    expected = base.expected.copy(
                        requiredExerciseIds = emptySet(),
                        forbiddenExerciseIds = setOf("not-in-catalog"),
                        requiredAnyExerciseIdGroups = emptyList(),
                        expectedTargetWeights = emptyMap()
                    )
                ),
                path = "root.expected.forbiddenExerciseIds[0]"
            )
        )

        cases.forEach { (fixture, path) ->
            val error = assertThrows(PlannerFixtureFormatException::class.java) {
                contextFactory.create(fixture)
            }

            assertThat(error.message)
                .isEqualTo("$path references unknown bundled catalog id 'not-in-catalog'.")
        }
    }

    @Test
    fun create_rejectsAllowedExerciseIdsMissingFromRealFilterOutput() {
        val fixture = corpusCompatibleFixture().copy(
            allowedExerciseIds = listOf("push-up", "barbell-back-squat")
        )

        val error = assertThrows(PlannerFixtureFormatException::class.java) {
            contextFactory.create(fixture)
        }

        assertThat(error.message).contains("root.allowedExerciseIds")
        assertThat(error.message).contains("barbell-back-squat")
    }

    @Test
    fun create_rejectsBundledCatalogWithoutSupportedSchemaVersion() {
        val fixture = corpusCompatibleFixture()
        val modifiedCatalog = contextFactory.readResourceText("workout-guide/catalog.json")
            .replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":2")
        val factory = contextFactoryWithCatalog(modifiedCatalog)

        val error = assertThrows(PlannerFixtureFormatException::class.java) {
            factory.create(fixture)
        }

        assertThat(error.message).isEqualTo("catalog.schemaVersion must equal 1.")
    }

    @Test
    fun create_rejectsBundledCatalogWithoutSourceCommit() {
        val fixture = corpusCompatibleFixture()
        val modifiedCatalog = contextFactory.readResourceText("workout-guide/catalog.json")
            .replaceFirst(
                "\"commit\":\"ba0b709cb20430361b2cb33aaadd20998164a916\",",
                ""
            )
        val factory = contextFactoryWithCatalog(modifiedCatalog)

        val error = assertThrows(PlannerFixtureFormatException::class.java) {
            factory.create(fixture)
        }

        assertThat(error.message).isEqualTo("catalog.source.commit must be a non-blank string.")
    }

    @Test
    fun bundledCatalogProjection_preservesPlannerConsumedFieldsForRepresentativeEntries() {
        val projection = contextFactory.bundledCatalogProjection()

        assertThat(projection.schemaVersion).isEqualTo(1)
        assertThat(projection.sourceCommit)
            .isEqualTo("ba0b709cb20430361b2cb33aaadd20998164a916")
        assertThat(projection.exercises).hasSize(302)

        val benchPress = projection.exercises.single { it.id == "barbell-bench-press" }
        assertThat(benchPress.name).isEqualTo("Bench Press")
        assertThat(benchPress.searchAliases).contains("Barbell Bench Press")
        assertThat(benchPress.primaryMuscles).containsExactly(StandardMuscles.CHEST)
        assertThat(benchPress.secondaryMuscles)
            .containsExactly(StandardMuscles.TRICEPS, StandardMuscles.SHOULDERS)
            .inOrder()
        assertThat(benchPress.listedEquipment).containsExactly(StandardEquipment.BARBELL)
        assertThat(benchPress.type.name).isEqualTo("WEIGHT_REPS")
        assertThat(benchPress.isStretch).isFalse()
        assertThat(benchPress.programming).isNotNull()
        assertThat(benchPress.programming!!.requiredEquipmentCombinations)
            .containsExactly(
                listOf(
                    StandardEquipment.BARBELL,
                    StandardEquipment.BENCH,
                    StandardEquipment.SQUAT_RACK
                )
            )
        assertThat(benchPress.programming!!.movementPattern.name).isEqualTo("HORIZONTAL_PUSH")
        assertThat(benchPress.programming!!.difficulty.name).isEqualTo("INTERMEDIATE")
        assertThat(benchPress.programming!!.mechanics.name).isEqualTo("COMPOUND")
        assertThat(benchPress.programming!!.recommendedRepRange.min).isEqualTo(5)
        assertThat(benchPress.programming!!.recommendedRepRange.max).isEqualTo(8)
        assertThat(benchPress.programming!!.fatigueScore).isEqualTo(4)
        assertThat(benchPress.programming!!.progressionType.name)
            .isEqualTo("REPETITIONS_THEN_LOAD")
        assertThat(benchPress.programming!!.alternativeExerciseIds)
            .containsExactly("incline-dumbbell-press")
        assertThat(benchPress.programming!!.coachingSummary)
            .contains("horizontal press")

        val walking = projection.exercises.single { it.id == "walking" }
        assertThat(walking.name).isEqualTo("Walking")
        assertThat(walking.primaryMuscles).containsExactly(StandardMuscles.QUADS)
        assertThat(walking.secondaryMuscles)
            .containsExactly(StandardMuscles.HAMSTRINGS, StandardMuscles.GLUTES, StandardMuscles.CARDIO)
            .inOrder()
        assertThat(walking.listedEquipment).containsExactly(StandardEquipment.CARDIO)
        assertThat(walking.type.name).isEqualTo("DISTANCE_DURATION")
        assertThat(walking.isStretch).isFalse()
        assertThat(walking.programming).isNull()
    }

    private fun corpusCompatibleFixture(): PlannerFixture =
        loader.loadResource("planner-fixtures/valid-basic.json").copy(
            catalogVersion = "ba0b709cb20430361b2cb33aaadd20998164a916"
        )

    private fun contextFactoryWithCatalog(catalogText: String): PlannerFixtureContextFactory =
        PlannerFixtureContextFactory(
            classLoader = object : ClassLoader(PlannerFixtureContextFactory::class.java.classLoader) {
                override fun getResourceAsStream(name: String): InputStream? = when (name) {
                    "workout-guide/catalog.json",
                    "assets/workout-guide/catalog.json" -> catalogText.byteInputStream()
                    else -> super.getResourceAsStream(name)
                }
            }
        )

    private data class UnknownReferenceCase(
        val fixture: PlannerFixture,
        val path: String
    )
}
