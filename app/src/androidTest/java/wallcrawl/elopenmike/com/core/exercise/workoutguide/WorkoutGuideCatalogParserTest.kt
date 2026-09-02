package wallcrawl.elopenmike.com.core.exercise.workoutguide

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.StringReader
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.model.MuscleVocabulary
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.UserProfile

@RunWith(AndroidJUnit4::class)
class WorkoutGuideCatalogParserTest {

    private val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
    private val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
    private val parser = WorkoutGuideCatalogParser()

    @Test
    fun parse_packagedCatalogLoadsEveryExerciseAndFrame() {
        val snapshot = assets.open("workout-guide/catalog.json").bufferedReader().use(parser::parse)

        assertThat(snapshot.exercises).hasSize(302)
        assertThat(snapshot.exercises.map { it.id }.toSet()).hasSize(302)
        assertThat(snapshot.framesByExerciseId.values.flatten()).hasSize(906)
        // Reviewed programming metadata covers the exercises the planner selects from,
        // not the whole catalog; the rest fall back to conservative defaults.
        assertThat(snapshot.exercises.count { it.programming != null }).isEqualTo(131)
        assertThat(snapshot.exercises.count { it.reviewedMetadata != null }).isEqualTo(37)
        assertThat(snapshot.exercises.map { it.id }).containsAtLeastElementsIn(HISTORICAL_IDS)

        snapshot.framesByExerciseId.values.flatten().forEach { frame ->
            assets.open(frame.assetPath).use { stream ->
                assertThat(stream.read()).isNotEqualTo(-1)
            }
        }
    }

    @Test
    fun parse_everyMuscleInThePackagedCatalogIsCanonical() {
        val snapshot = assets.open("workout-guide/catalog.json").bufferedReader().use(parser::parse)

        val unrecognized = snapshot.exercises
            .flatMap { it.primaryMuscles + it.secondaryMuscles }
            .distinct()
            .filterNot(MuscleVocabulary::isCanonical)

        // A catalog update that introduces new muscle vocabulary must be taught to
        // MuscleVocabulary, otherwise those exercises silently stop matching every split.
        assertThat(unrecognized).isEmpty()
        snapshot.exercises.forEach { exercise ->
            assertThat(exercise.primaryMuscles).isNotEmpty()
        }
    }

    @Test
    fun parse_resolvesUpstreamMuscleNamesOntoTheAppVocabulary() {
        val snapshot = assets.open("workout-guide/catalog.json").bufferedReader().use(parser::parse)

        val backSquat = snapshot.exercises.single { it.id == "barbell-back-squat" }
        assertThat(backSquat.primaryMuscles).contains(StandardMuscles.QUADS)

        // Upstream files the deadlift under the umbrella term "Posterior Chain". One group
        // is named as the primary mover so weekly set counts stay one per set; the rest
        // become secondary, which split matching also reads.
        val deadlift = snapshot.exercises.single { it.id == "barbell-deadlift" }
        assertThat(deadlift.primaryMuscles).containsExactly(StandardMuscles.HAMSTRINGS)
        assertThat(deadlift.secondaryMuscles).containsAtLeast(
            StandardMuscles.GLUTES,
            StandardMuscles.LOWER_BACK
        )
    }

    @Test
    fun parse_exposesCatalogProvenanceForTheCreditsScreen() {
        val snapshot = assets.open("workout-guide/catalog.json").bufferedReader().use(parser::parse)

        val attribution = snapshot.catalogAttribution
        assertThat(attribution.attribution.license).isEqualTo("CC BY-SA 4.0")
        assertThat(attribution.repository).startsWith("https://")
        assertThat(attribution.commit).hasLength(40)
        assertThat(attribution.exerciseCount).isEqualTo(302)
        assertThat(attribution.frameCount).isEqualTo(906)
    }

    @Test
    fun parse_preservesAliasedIdentityAndSourceMetadata() {
        val snapshot = assets.open("workout-guide/catalog.json").bufferedReader().use(parser::parse)

        val benchPress = snapshot.exercises.single { it.id == "barbell-bench-press" }
        assertThat(benchPress.source?.sourceId).isEqualTo("exercise-bench-press")
        assertThat(benchPress.source?.sourceSlug).isEqualTo("bench-press")
        assertThat(benchPress.searchAliases).contains("Barbell Bench Press")
        assertThat(benchPress.source?.attribution?.license).isEqualTo("CC BY-SA 4.0")
        assertThat(snapshot.framesByExerciseId.getValue(benchPress.id)).hasSize(3)
    }

    @Test
    fun packagedCatalog_all302ExercisesCanEnterThePlannerCandidatePool() {
        val exercises = assets.open("workout-guide/catalog.json")
            .bufferedReader()
            .use(parser::parse)
            .exercises

        val catalogEquipment = exercises.flatMap { it.listedEquipment }.toSet()
        val candidates = ExerciseFilter().filterCandidates(
            allExercises = exercises,
            profile = UserProfile(availableEquipment = StandardEquipment.ALL)
        )

        assertThat(StandardEquipment.ALL).containsAtLeastElementsIn(catalogEquipment)
        assertThat(candidates.map { it.id }.toSet()).hasSize(302)
    }

    @Test
    fun parse_sharedProgrammingContract_isTypeDependentAndFieldOrderIndependent() {
        val fixtures = testAssets.open("programming-validation-fixtures.json").bufferedReader()
            .use { JSONObject(it.readText()) }
        val cases = fixtures.getJSONArray("cases")
        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            // Preserve raw numeric notation: JSONObject.toString would turn 1.0 into 1.
            val programming = fixtures.getJSONObject("baseProgramming").toString().dropLast(1) +
                (if (case.has("rangeJson")) ",\"recommendedRepRange\":${case.getString("rangeJson")}" else "") + "}"
            val exercise = exerciseJson(
                exerciseType = case.getString("exerciseType"),
                programming = programming
            )
            // The stream must validate after type resolution, including when type comes last.
            val typeField = "\"exerciseType\": \"${case.getString("exerciseType")}\","
            val typeLast = exercise.replace(typeField, "")
                .trimEnd().dropLast(1) + "," + typeField.dropLast(1) + "}"
            listOf(exercise, typeLast).forEach { entry ->
                val result = runCatching { parser.parse(StringReader(catalogJson(entry))) }
                assertWithMessage(case.getString("name")).that(result.isSuccess)
                    .isEqualTo(case.getBoolean("accepted"))
                if (result.isSuccess) {
                    val range = result.getOrThrow().exercises.single().programming!!.recommendedRepRange
                    if (JSONObject(programming).isNull("recommendedRepRange")) {
                        assertThat(range).isNull()
                    } else {
                        assertThat(range?.min).isEqualTo(JSONObject(programming).getJSONObject("recommendedRepRange").getInt("min"))
                    }
                } else {
                    val error = result.exceptionOrNull()!!
                    assertThat(error).isInstanceOf(WorkoutGuideCatalogFormatException::class.java)
                    assertThat(error.message!!.length).isLessThan(512)
                    assertThat(error.message).doesNotContain("UNTRUSTED_RECORD_MARKER")
                }
            }
        }
    }

    @Test
    fun parse_rejectsUnsupportedSchema() {
        val malformed = """{"schemaVersion":2,"source":{},"exercises":[]}"""

        try {
            parser.parse(StringReader(malformed))
            throw AssertionError("Expected catalog format failure")
        } catch (error: WorkoutGuideCatalogFormatException) {
            assertThat(error.message).contains("schemaVersion")
        }
    }

    @Test
    fun parse_rejectsDuplicateExerciseIds() {
        val malformed = catalogJson("${exerciseJson()} , ${exerciseJson()}")

        assertFormatFailure(malformed, "Duplicate exercise id")
    }

    @Test
    fun parse_rejectsInvalidVisualSpecification() {
        val malformed = catalogJson(exerciseJson())
            .replace("\"frameCount\": 3", "\"frameCount\": 4")

        assertFormatFailure(malformed, "frameCount")
    }

    @Test
    fun parse_rejectsTruncatedJsonWithOriginalCause() {
        try {
            parser.parse(StringReader("""{"schemaVersion":1,"exercises":["""))
            throw AssertionError("Expected catalog format failure")
        } catch (error: WorkoutGuideCatalogFormatException) {
            assertThat(error.cause).isNotNull()
        }
    }

    @Test
    fun parse_readsTypedReviewedMetadataWithoutTreatingDraftAsApproved() {
        val snapshot = parser.parse(StringReader(catalogJson(exerciseJson(reviewedMetadataJson()))))

        val metadata = snapshot.exercises.single().reviewedMetadata
        assertThat(metadata).isNotNull()
        assertThat(metadata?.reviewState).isEqualTo(ReviewState.DRAFT)
        assertThat(metadata?.directPrimaryMuscle).isEqualTo(StandardMuscles.CORE)
        assertThat(metadata?.provenance?.reviewerRole).isNull()
        assertThat(metadata?.approvedRegressions).isEmpty()
    }

    @Test
    fun parse_rejectsUnknownReviewedMetadataField() {
        val metadata = reviewedMetadataJson().replace(
            "\"reviewState\": \"draft\"",
            "\"reviewState\": \"draft\", \"fatigueScore\": 5"
        )

        assertFormatFailure(catalogJson(exerciseJson(metadata)), "sample reviewedMetadata.fatigueScore")
    }

    @Test
    fun parse_rejectsMalformedReviewedMetadataAtTheSameContractBoundariesAsTheImporter() {
        val fixtures = testAssets.open("reviewed-validation-fixtures.json")
            .bufferedReader()
            .use { JSONObject(it.readText()) }
        val base = fixtures.getJSONObject("baseReviewedMetadata")
        val cases = fixtures.getJSONArray("invalidCases")

        for (index in 0 until cases.length()) {
            val case = cases.getJSONObject(index)
            val metadata = JSONObject(base.toString())
            applyFixtureOperation(metadata, case)
            assertFormatFailure(
                catalogJson(
                    exerciseJson(
                        reviewedMetadata = metadata.toString(),
                        primaryMuscles = "[\"Chest\"]",
                        secondaryMuscles = "[\"Shoulders\", \"Triceps\"]",
                        exerciseType = "weight_reps"
                    )
                ),
                case.getString("errorFragment")
            )
        }
    }

    @Test
    fun parse_rejectsReviewedMetadataOnStretchAndCardioDurationExercises() {
        assertFormatFailure(
            catalogJson(exerciseJson(reviewedMetadataJson(), isStretch = true)),
            "stretch"
        )
        val duration = reviewedMetadataJson()
            .replace("\"directPrimaryMuscle\": \"Core\"", "\"directPrimaryMuscle\": \"Chest\"")
            .replace("\"prescriptionShape\": \"bodyweight_reps\"", "\"prescriptionShape\": \"duration\"")
        assertFormatFailure(
            catalogJson(
                exerciseJson(
                    reviewedMetadata = duration,
                    primaryMuscles = "[\"Chest\"]",
                    secondaryMuscles = "[\"cardio\"]",
                    exerciseType = "duration"
                )
            ),
            "cardio duration"
        )
    }

    @Test
    fun parse_rejectsDecimalNotationForReviewedIntegerFields() {
        for (field in listOf("schemaVersion", "policyVersion")) {
            val metadata = reviewedMetadataJson().replace(
                "\"$field\": 1",
                "\"$field\": 1.0"
            )

            assertFormatFailure(catalogJson(exerciseJson(metadata)), field)
        }
        val reviewedAt = reviewedMetadataJson().replace(
            "\"reviewedAtEpochMillis\": null",
            "\"reviewedAtEpochMillis\": 1.0"
        )
        assertFormatFailure(catalogJson(exerciseJson(reviewedAt)), "reviewedAtEpochMillis")
    }

    @Test
    fun parse_rejectsMoreReviewedEntriesThanTheAuthoredSchemaAllows() {
        val exercises = (0..500).joinToString(",") { index ->
            exerciseJson(reviewedMetadataJson(), id = "sample-$index")
        }

        assertFormatFailure(catalogJson(exercises), "500 reviewed entries")
    }

    @Test
    fun parse_rejectsDuplicateReviewedMetadataExerciseField() {
        val metadata = reviewedMetadataJson()
        val duplicate = exerciseJson(metadata).replace(
            "\"isStretch\": false",
            "\"isStretch\": false,\n              \"reviewedMetadata\": $metadata"
        )

        assertFormatFailure(catalogJson(duplicate), "duplicate field reviewedMetadata")
    }

    @Test
    fun parse_rejectsDuplicateFieldsAcrossCatalogCompatibilityObjects() {
        val base = catalogJson(exerciseJson(programming = programmingJson()))
        val attributionSource = """
            "source": {
              "name": "Workout Guide",
              "url": "https://github.com/bryllim/workout-guide",
              "license": "CC BY-SA 4.0",
              "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/",
              "changes": "Normalized for offline use."
            }
        """.trimIndent()
        val withAttributionSource = base.replace(
            "\"licenseUrl\": \"https://creativecommons.org/licenses/by-sa/4.0/\"",
            "\"licenseUrl\": \"https://creativecommons.org/licenses/by-sa/4.0/\",\n" +
                attributionSource
        )
        val cases = listOf(
            base.replaceFirst(
                "\"exercises\": [",
                "\"exercises\": [],\n  \"exercises\": ["
            ) to "catalog contains duplicate field exercises",
            base.replaceFirst(
                "\"commit\": \"ba0b709cb20430361b2cb33aaadd20998164a916\"",
                "\"commit\": \"ba0b709cb20430361b2cb33aaadd20998164a916\",\n" +
                    "    \"commit\": \"ba0b709cb20430361b2cb33aaadd20998164a916\""
            ) to "source contains duplicate field commit",
            base.replaceFirst(
                "\"frameCount\": 3",
                "\"frameCount\": 3, \"frameCount\": 3"
            ) to "visuals contains duplicate field frameCount",
            base.replaceFirst(
                "\"creator\": \"Bryl Lim\"",
                "\"creator\": \"Bryl Lim\", \"creator\": \"Bryl Lim\""
            ) to "source.attribution contains duplicate field creator",
            withAttributionSource.replaceFirst(
                "\"name\": \"Workout Guide\"",
                "\"name\": \"Workout Guide\", \"name\": \"Workout Guide\""
            ) to "source.attribution.source contains duplicate field name",
            base.replaceFirst(
                "\"fatigueScore\": 1",
                "\"fatigueScore\": 1, \"fatigueScore\": 1"
            ) to "programming contains duplicate field fatigueScore",
            base.replaceFirst(
                "\"min\": 6",
                "\"min\": 6, \"min\": 6"
            ) to "programming.recommendedRepRange contains duplicate field min",
            base.replaceFirst(
                "{",
                "{\"ignored\": {\"field\": 1, \"field\": 2},"
            ) to "catalog.ignored contains duplicate field field"
        )

        cases.forEach { (json, message) -> assertFormatFailure(json, message) }
    }

    @Test
    fun parse_boundsIgnoredCatalogPayloadAndDepth() {
        val nested = buildString {
            repeat(14) { append("{\"nested\":") }
            append("\"value\"")
            repeat(14) { append('}') }
        }
        val tooDeep = catalogJson(exerciseJson()).replaceFirst(
            "{",
            "{\"ignored\":$nested,"
        )
        assertFormatFailure(tooDeep, "nesting depth")

        val oversized = catalogJson(exerciseJson()).replaceFirst(
            "{",
            "{\"ignored\":\"${"x".repeat(8_000_001)}\","
        )
        assertFormatFailure(oversized, "input limit")
    }

    @Test
    fun parse_validationErrorsDoNotRevealUntrustedCatalogValues() {
        val marker = "INJECTED-CATALOG-VALUE"
        val graphMarker = "injected-catalog-value"
        val unknownReviewedField = reviewedMetadataJson().replace(
            "\"reviewState\": \"draft\"",
            "\"reviewState\": \"draft\", \"unknown\": true"
        )
        val invalidProgrammingEnum = programmingJson().replace(
            "\"movementPattern\": \"core\"",
            "\"movementPattern\": \"bad\\n$marker\""
        )
        val invalidAlternative = programmingJson().replace(
            "\"alternativeExerciseIds\": []",
            "\"alternativeExerciseIds\": [\"bad\\n$marker\"]"
        )
        val unknownGraphTarget = reviewedMetadataJson().replace(
            "\"approvedRegressions\": []",
            "\"approvedRegressions\": [{\"exerciseId\": \"$graphMarker\"}]"
        )
        val duplicateGraphTarget = reviewedMetadataJson().replace(
            "\"approvedRegressions\": []",
            "\"approvedRegressions\": [" +
                "{\"exerciseId\": \"$graphMarker\"}," +
                "{\"exerciseId\": \"$graphMarker\", \"rationale\": \"Duplicate edge.\"}]"
        )
        val crossRoleGraphTarget = reviewedMetadataJson()
            .replace(
                "\"approvedRegressions\": []",
                "\"approvedRegressions\": [{\"exerciseId\": \"$graphMarker\"}]"
            )
            .replace(
                "\"approvedSubstitutions\": []",
                "\"approvedSubstitutions\": [{\"exerciseId\": \"$graphMarker\"}]"
            )
        val cases = listOf(
            catalogJson(exerciseJson(id = "bad\\n$marker")) to "exercise.id",
            catalogJson(exerciseJson(exerciseType = "bad\\n$marker")) to "exerciseType",
            catalogJson(
                exerciseJson(reviewedMetadata = unknownReviewedField, id = "bad\\n$marker")
            ) to "exercise[0].reviewedMetadata",
            catalogJson(exerciseJson(programming = invalidProgrammingEnum)) to
                "programming movementPattern",
            catalogJson(exerciseJson(programming = invalidAlternative)) to
                "alternativeExerciseId"
        )
        val graphCases = listOf(
            catalogJson(exerciseJson(unknownGraphTarget)) to "unknown exercise id",
            catalogJson(exerciseJson(duplicateGraphTarget)) to "duplicate edge",
            catalogJson(exerciseJson(crossRoleGraphTarget)) to
                "as regression and substitution"
        )

        cases.forEach { (json, field) -> assertFormatFailureRedacts(json, field, marker) }
        graphCases.forEach { (json, field) ->
            assertFormatFailureRedacts(json, field, graphMarker)
        }
    }

    @Test
    fun parse_rejectsInvalidReviewedRegressionGraphs() {
        val sourceToTarget = reviewedMetadataJson().replace(
            "\"approvedRegressions\": []",
            "\"approvedRegressions\": [{\"exerciseId\": \"target\"}]"
        )
        assertFormatFailure(catalogJson(exerciseJson(sourceToTarget)), "unknown exercise id")
        assertFormatFailure(
            catalogJson(
                exerciseJson(sourceToTarget) + "," + exerciseJson(null, id = "target")
            ),
            "target lacks reviewed metadata"
        )

        val targetToSource = reviewedMetadataJson().replace(
            "\"approvedRegressions\": []",
            "\"approvedRegressions\": [{\"exerciseId\": \"sample\"}]"
        )
        assertFormatFailure(
            catalogJson(
                exerciseJson(sourceToTarget) + "," +
                    exerciseJson(targetToSource, id = "target")
            ),
            "regression cycle"
        )

        val harderTarget = reviewedMetadataJson().replace(
            "\"complexity\": \"foundational\"",
            "\"complexity\": \"standard\""
        )
        assertFormatFailure(
            catalogJson(
                exerciseJson(sourceToTarget) + "," +
                    exerciseJson(harderTarget, id = "target")
            ),
            "more complex"
        )
    }

    @Test
    fun parse_rejectsUndocumentedCrossFamilyAndRoleChangingEdges() {
        val regression = reviewedMetadataJson().replace(
            "\"approvedRegressions\": []",
            "\"approvedRegressions\": [{\"exerciseId\": \"target\"}]"
        )
        val otherFamily = reviewedMetadataJson().replace(
            "\"progressionFamily\": \"core-hold\"",
            "\"progressionFamily\": \"other-core-family\""
        )
        assertFormatFailure(
            catalogJson(
                exerciseJson(regression) + "," + exerciseJson(otherFamily, id = "target")
            ),
            "crosses progressionFamily without rationale"
        )

        val substitution = reviewedMetadataJson().replace(
            "\"approvedSubstitutions\": []",
            "\"approvedSubstitutions\": [{\"exerciseId\": \"target\"}]"
        )
        val changedRole = reviewedMetadataJson()
            .replace("\"directPrimaryMuscle\": \"Core\"", "\"directPrimaryMuscle\": \"Chest\"")
            .replace("\"movementPattern\": \"core\"", "\"movementPattern\": \"horizontal_push\"")
        assertFormatFailure(
            catalogJson(
                exerciseJson(substitution) + "," +
                    exerciseJson(
                        reviewedMetadata = changedRole,
                        id = "target",
                        primaryMuscles = "[\"Chest\"]"
                    )
            ),
            "changes movement role without rationale"
        )
    }

    private fun assertFormatFailure(json: String, messageFragment: String) {
        try {
            parser.parse(StringReader(json))
            throw AssertionError("Expected catalog format failure for $messageFragment")
        } catch (error: WorkoutGuideCatalogFormatException) {
            assertThat(error.message).contains(messageFragment)
        }
    }

    private fun assertFormatFailureRedacts(
        json: String,
        messageFragment: String,
        untrustedFragment: String
    ) {
        try {
            parser.parse(StringReader(json))
            throw AssertionError("Expected catalog format failure for $messageFragment")
        } catch (error: WorkoutGuideCatalogFormatException) {
            assertThat(error.message).contains(messageFragment)
            assertThat(error.message).doesNotContain(untrustedFragment)
            assertThat(error.message).doesNotContain("\n")
        }
    }

    private fun applyFixtureOperation(metadata: JSONObject, case: JSONObject) {
        val path = case.getJSONArray("path")
        var target = metadata
        for (index in 0 until path.length() - 1) {
            target = target.getJSONObject(path.getString(index))
        }
        val key = path.getString(path.length() - 1)
        when (case.getString("operation")) {
            "remove" -> target.remove(key)
            "set" -> target.put(key, case.get("value"))
            else -> throw AssertionError("Unknown shared fixture operation")
        }
    }

    private fun catalogJson(exercises: String): String = """
        {
          "schemaVersion": 1,
          "source": {
            "repository": "https://github.com/bryllim/workout-guide",
            "commit": "ba0b709cb20430361b2cb33aaadd20998164a916",
            "assetLicense": "CC-BY-SA-4.0",
            "attribution": {
              "creator": "Bryl Lim",
              "creatorUrl": "https://bryllim.com",
              "license": "CC BY-SA 4.0",
              "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/"
            }
          },
          "visuals": {"frameCount": 3, "widthPx": 512, "heightPx": 512, "format": "svg"},
          "exercises": [$exercises]
        }
    """.trimIndent()

    private fun exerciseJson(
        reviewedMetadata: String? = null,
        id: String = "sample",
        primaryMuscles: String = "[\"Core\"]",
        secondaryMuscles: String = "[]",
        exerciseType: String = "bodyweight_reps",
        isStretch: Boolean = false,
        programming: String? = null
    ): String {
        return """
            {
              "id": "$id",
              "sourceId": "exercise-$id",
              "sourceSlug": "$id",
              "name": "Sample Exercise",
              "searchAliases": [],
              "primaryMuscles": $primaryMuscles,
              "secondaryMuscles": $secondaryMuscles,
              "listedEquipment": ["Bodyweight"],
              "exerciseType": "$exerciseType",
              "isStretch": $isStretch${programming?.let { ",\n              \"programming\": $it" }.orEmpty()}${reviewedMetadata?.let { ",\n              \"reviewedMetadata\": $it" }.orEmpty()}
            }
        """.trimIndent()
    }

    private fun programmingJson(): String = """
        {
          "requiredEquipmentCombinations": [["Bodyweight"]],
          "movementPattern": "core",
          "difficulty": "beginner",
          "mechanics": "isolation",
          "recommendedRepRange": {"min": 6, "max": 12},
          "fatigueScore": 1,
          "progressionType": "repetitions",
          "alternativeExerciseIds": [],
          "coachingSummary": "Controlled core repetition."
        }
    """.trimIndent()

    private fun reviewedMetadataJson(): String = """
        {
          "reviewState": "draft",
          "directPrimaryMuscle": "Core",
          "descriptiveSecondaryMuscles": [],
          "movementPattern": "core",
          "complexity": "foundational",
          "progressionFamily": "core-hold",
          "prescriptionShape": "bodyweight_reps",
          "approvedRegressions": [],
          "approvedSubstitutions": [],
          "capabilityRequirements": [],
          "supportRequirement": "supported",
          "impactLevel": "none",
          "equipmentAlternatives": [["Bodyweight"]],
          "provenance": {
            "reviewerRole": null,
            "rationaleOrSource": "Draft fixture awaiting human review.",
            "reviewedAtEpochMillis": null,
            "schemaVersion": 1,
            "policyVersion": 1
          }
        }
    """.trimIndent()

    private companion object {
        val HISTORICAL_IDS = setOf(
            "incline-dumbbell-press",
            "barbell-bench-press",
            "pull-ups",
            "barbell-deadlift",
            "barbell-back-squat",
            "dumbbell-shoulder-press",
            "dumbbell-lateral-raise",
            "cable-triceps-pushdown",
            "barbell-bicep-curl",
            "parallel-bar-dips",
            "hanging-leg-raise",
            "romanian-deadlift"
        )
    }
}
