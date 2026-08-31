package wallcrawl.elopenmike.com.core.ai

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityResult
import wallcrawl.elopenmike.com.core.model.ComplexityTier
import wallcrawl.elopenmike.com.core.model.Difficulty
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseProgrammingMetadata
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.MechanicsType
import wallcrawl.elopenmike.com.core.model.ImpactLevel
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.MovementPattern
import wallcrawl.elopenmike.com.core.model.MuscleVocabulary
import wallcrawl.elopenmike.com.core.model.PrescriptionShape
import wallcrawl.elopenmike.com.core.model.ProgressionType
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.ReviewProvenance
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseLink
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseMetadata
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.SupportRequirement
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext

internal data class PlannerFixtureContext(
    val fixture: PlannerFixture,
    val userProfile: UserProfile,
    val catalogExercises: List<Exercise>,
    val filteredExercises: List<Exercise>,
    val context: WorkoutGenerationContext
)

internal data class PlannerFixtureBundledCatalogProjection(
    val schemaVersion: Int,
    val sourceCommit: String,
    val exercises: List<Exercise>
)

internal class PlannerFixtureContextFactory(
    private val classLoader: ClassLoader = checkNotNull(PlannerFixtureContextFactory::class.java.classLoader),
    private val exerciseFilter: ExerciseFilter = ExerciseFilter()
) {

    private val catalogProjection: PlannerFixtureBundledCatalogProjection by lazy {
        loadBundledCatalogProjection()
    }

    internal fun bundledCatalogProjection(): PlannerFixtureBundledCatalogProjection = catalogProjection

    fun create(fixture: PlannerFixture): PlannerFixtureContext {
        validateSupportedCorpusContract(fixture, catalogProjection)
        validateReviewedFixtureContract(fixture)
        validateCatalogReferences(fixture, catalogProjection.exercises)
        val catalogExercises = applySyntheticApprovals(
            exercises = catalogProjection.exercises,
            reviewedEligibility = fixture.reviewedEligibility
        )
        val profile = UserProfile(
            goals = fixture.profile.goals,
            experienceLevel = fixture.profile.experienceLevel,
            preferredDurationMinutes = fixture.profile.preferredDurationMinutes,
            daysPerWeek = fixture.profile.daysPerWeek,
            availableEquipment = fixture.profile.availableEquipment,
            preferredUnit = fixture.profile.preferredUnit,
            musclePriorities = fixture.profile.musclePriorities,
            excludedExerciseIds = fixture.profile.excludedExerciseIds,
            onboardingCompleted = true,
            trainingConstraints = fixture.profile.trainingConstraints,
            returningAfterBreakWeeks = fixture.profile.returningAfterBreakWeeks,
            confirmedStartingLoads = fixture.profile.confirmedStartingLoads,
            movementCapabilities = fixture.profile.movementCapabilities
        )
        val filteredExercises = exerciseFilter.filterCandidates(
            allExercises = catalogExercises,
            profile = profile
        )
        val automaticEligibilityResult = fixture.reviewedEligibility?.let { reviewedEligibility ->
            ExerciseEligibilityPolicy().evaluate(
                exercises = catalogExercises,
                profile = profile,
                adaptationState = reviewedEligibility.adaptationState,
                demonstratedProgressionFamilies = fixture.exerciseHistory.mapNotNullTo(
                    linkedSetOf()
                ) { history ->
                    catalogExercises.single { it.id == history.exerciseId }
                        .reviewedMetadata
                        ?.takeIf { it.reviewState == ReviewState.APPROVED }
                        ?.progressionFamily
                }
            )
        }
        val allowedExercises = when (automaticEligibilityResult) {
            is AutomaticEligibilityResult.Candidates -> automaticEligibilityResult.exercises
            is AutomaticEligibilityResult.NoCandidates -> emptyList()
            null -> restrictToAllowedExerciseIds(
                filteredExercises = filteredExercises,
                catalogExercises = catalogExercises,
                fixture = fixture
            )
        }
        return PlannerFixtureContext(
            fixture = fixture,
            userProfile = profile,
            catalogExercises = catalogExercises,
            filteredExercises = filteredExercises,
            context = WorkoutGenerationContext(
                userProfile = profile,
                completedWorkoutCount = fixture.completedWorkoutCount,
                exerciseHistory = fixture.exerciseHistory.associateBy { it.exerciseId },
                allowedExercises = allowedExercises,
                automaticEligibilityResult = automaticEligibilityResult,
                preferredUnits = profile.preferredUnit
            )
        )
    }

    private fun applySyntheticApprovals(
        exercises: List<Exercise>,
        reviewedEligibility: PlannerFixtureReviewedEligibility?
    ): List<Exercise> {
        val approvedIds = reviewedEligibility?.syntheticApprovedExerciseIds?.toSet()
            ?: return exercises
        return exercises.map { exercise ->
            if (exercise.id !in approvedIds) return@map exercise
            val metadata = exercise.reviewedMetadata ?: throw PlannerFixtureFormatException(
                "root.reviewedEligibility.syntheticApprovedExerciseIds contains '${exercise.id}', " +
                    "which has no bundled reviewed metadata."
            )
            if (metadata.reviewState != ReviewState.DRAFT) {
                throw PlannerFixtureFormatException(
                    "root.reviewedEligibility.syntheticApprovedExerciseIds contains '${exercise.id}', " +
                        "which is not bundled as DRAFT."
                )
            }
            exercise.copy(
                reviewedMetadata = metadata.copy(
                    reviewState = ReviewState.APPROVED,
                    provenance = ReviewProvenance(
                        reviewerRole = "Synthetic test-only reviewer",
                        rationaleOrSource =
                            "SYNTHETIC PLANNER FIXTURE — never bundled in production assets.",
                        reviewedAtEpochMillis = 1L,
                        schemaVersion = metadata.provenance.schemaVersion,
                        policyVersion = metadata.provenance.policyVersion
                    )
                )
            )
        }
    }

    private fun validateReviewedFixtureContract(fixture: PlannerFixture) {
        if (
            fixture.reviewedEligibility != null &&
            fixture.allowedExerciseIds.isNotEmpty()
        ) {
            throw PlannerFixtureFormatException(
                "root.allowedExerciseIds cannot restrict an enabled reviewed eligibility fixture."
            )
        }
        if (
            fixture.expected.outcome ==
            PlannerFixtureOutcome.REVIEWED_ELIGIBILITY_NO_CANDIDATES &&
            fixture.reviewedEligibility == null
        ) {
            throw PlannerFixtureFormatException(
                "root.reviewedEligibility is required for a reviewed eligibility failure fixture."
            )
        }
    }

    fun manifestResourcePaths(manifestPath: String = DEFAULT_MANIFEST_RESOURCE): List<String> =
        readResourceText(manifestPath)
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()

    fun readResourceText(path: String): String = decodeUtf8(readResourceBytes(path), path)

    private fun restrictToAllowedExerciseIds(
        filteredExercises: List<Exercise>,
        catalogExercises: List<Exercise>,
        fixture: PlannerFixture
    ): List<Exercise> {
        if (fixture.allowedExerciseIds.isEmpty()) return filteredExercises

        val filteredIds = filteredExercises.mapTo(linkedSetOf(), Exercise::id)
        val missingAllowedIds = fixture.allowedExerciseIds.distinct().filterNot { it in filteredIds }
        if (missingAllowedIds.isNotEmpty()) {
            throw PlannerFixtureFormatException(
                "root.allowedExerciseIds contains ids absent from filteredExercises after the real ExerciseFilter: " +
                    missingAllowedIds.joinToString(", ") + "."
            )
        }
        val allowedIds = fixture.allowedExerciseIds.toSet()
        return filteredExercises.filter { it.id in allowedIds }
    }

    private fun validateSupportedCorpusContract(
        fixture: PlannerFixture,
        catalogProjection: PlannerFixtureBundledCatalogProjection
    ) {
        if (fixture.policyVersion != SUPPORTED_CORPUS_POLICY_VERSION) {
            throw PlannerFixtureFormatException(
                "root.policyVersion must equal supported corpus policy version $SUPPORTED_CORPUS_POLICY_VERSION."
            )
        }
        if (fixture.catalogVersion != catalogProjection.sourceCommit) {
            throw PlannerFixtureFormatException(
                "root.catalogVersion must equal bundled catalog source.commit ${catalogProjection.sourceCommit}."
            )
        }
    }

    private fun validateCatalogReferences(
        fixture: PlannerFixture,
        catalogExercises: List<Exercise>
    ) {
        val catalogIds = catalogExercises.mapTo(linkedSetOf(), Exercise::id)
        val references = buildList<Pair<String, String>> {
            fixture.allowedExerciseIds.forEachIndexed { index, exerciseId ->
                add("root.allowedExerciseIds[$index]" to exerciseId)
            }
            fixture.reviewedEligibility?.syntheticApprovedExerciseIds
                ?.forEachIndexed { index, exerciseId ->
                    add(
                        "root.reviewedEligibility.syntheticApprovedExerciseIds[$index]" to
                            exerciseId
                    )
                }
            fixture.profile.excludedExerciseIds.forEachIndexed { index, exerciseId ->
                add("root.profile.excludedExerciseIds[$index]" to exerciseId)
            }
            fixture.profile.confirmedStartingLoads.keys.forEach { exerciseId ->
                add("root.profile.confirmedStartingLoads.$exerciseId" to exerciseId)
            }
            fixture.exerciseHistory.forEachIndexed { index, history ->
                add("root.exerciseHistory[$index].exerciseId" to history.exerciseId)
            }
            fixture.expected.requiredExerciseIds.toList().forEachIndexed { index, exerciseId ->
                add("root.expected.requiredExerciseIds[$index]" to exerciseId)
            }
            fixture.expected.requiredAnyExerciseIdGroups.forEachIndexed { groupIndex, group ->
                group.toList().forEachIndexed { memberIndex, exerciseId ->
                    add("root.expected.requiredAnyExerciseIdGroups[$groupIndex][$memberIndex]" to exerciseId)
                }
            }
            fixture.expected.expectedTargetWeights.keys.forEach { exerciseId ->
                add("root.expected.expectedTargetWeights.$exerciseId" to exerciseId)
            }
            fixture.expected.forbiddenExerciseIds.toList().forEachIndexed { index, exerciseId ->
                add("root.expected.forbiddenExerciseIds[$index]" to exerciseId)
            }
        }
        val unknownReference = references.firstOrNull { (_, exerciseId) -> exerciseId !in catalogIds }
            ?: return
        throw PlannerFixtureFormatException(
            "${unknownReference.first} references unknown bundled catalog id '${unknownReference.second}'."
        )
    }

    private fun loadBundledCatalogProjection(): PlannerFixtureBundledCatalogProjection {
        val catalogText = readBundledCatalogText()
        val root = try {
            JSONTokener(catalogText).nextValue() as? JSONObject
                ?: throw PlannerFixtureFormatException("Bundled planner catalog must be a JSON object.")
        } catch (error: JSONException) {
            throw PlannerFixtureFormatException("Bundled planner catalog is malformed JSON.", error)
        }
        val schemaVersion = requireInt(root, "schemaVersion", "catalog.schemaVersion")
        if (schemaVersion != SUPPORTED_BUNDLED_CATALOG_SCHEMA_VERSION) {
            throw PlannerFixtureFormatException(
                "catalog.schemaVersion must equal $SUPPORTED_BUNDLED_CATALOG_SCHEMA_VERSION."
            )
        }
        val sourceCommit = requireString(
            requireObject(root, "source", "catalog.source"),
            "commit",
            "catalog.source.commit"
        )
        val exercisesArray = root.optJSONArray("exercises")
            ?: throw PlannerFixtureFormatException("Bundled planner catalog is missing exercises.")
        val exercises = buildList(exercisesArray.length()) {
            for (index in 0 until exercisesArray.length()) {
                val exercise = exercisesArray.optJSONObject(index)
                    ?: throw PlannerFixtureFormatException("Bundled catalog exercise[$index] must be an object.")
                add(parseExercise(exercise, index))
            }
        }
        if (exercises.size != EXPECTED_BUNDLED_EXERCISE_COUNT) {
            throw PlannerFixtureFormatException(
                "Bundled planner catalog must contain exactly $EXPECTED_BUNDLED_EXERCISE_COUNT exercises."
            )
        }
        val duplicateId = exercises.groupingBy(Exercise::id).eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.key
        if (duplicateId != null) {
            throw PlannerFixtureFormatException("Bundled planner catalog contains duplicate exercise id $duplicateId.")
        }
        return PlannerFixtureBundledCatalogProjection(
            schemaVersion = schemaVersion,
            sourceCommit = sourceCommit,
            exercises = exercises
        )
    }

    private fun readBundledCatalogText(): String {
        for (path in BUNDLED_CATALOG_RESOURCE_PATHS) {
            val stream = classLoader.getResourceAsStream(path) ?: continue
            return stream.use { input -> decodeUtf8(readBoundedBytes(input, path), path) }
        }
        throw PlannerFixtureFormatException(
            "Bundled planner catalog resource not found at " +
                BUNDLED_CATALOG_RESOURCE_PATHS.joinToString(" or ") + "."
        )
    }

    private fun parseExercise(exercise: JSONObject, index: Int): Exercise {
        val path = "catalog.exercises[$index]"
        val id = requireString(exercise, "id", "$path.id")
        val name = requireString(exercise, "name", "$path.name")
        val searchAliases = requireStringList(
            requireArray(exercise, "searchAliases", "$path.searchAliases"),
            "$path.searchAliases"
        )
        val rawPrimaryMuscles = requireStringList(
            requireArray(exercise, "primaryMuscles", "$path.primaryMuscles"),
            "$path.primaryMuscles",
            minSize = 1
        )
        val rawSecondaryMuscles = requireStringList(
            requireArray(exercise, "secondaryMuscles", "$path.secondaryMuscles"),
            "$path.secondaryMuscles"
        )
        val primaryMuscles = rawPrimaryMuscles.mapNotNull(MuscleVocabulary::canonicalizePrimary).distinct()
        if (primaryMuscles.isEmpty()) {
            throw PlannerFixtureFormatException("$path.primaryMuscles must resolve to at least one canonical muscle.")
        }
        val secondaryMuscles = (
            MuscleVocabulary.canonicalizeAll(rawPrimaryMuscles) +
                MuscleVocabulary.canonicalizeAll(rawSecondaryMuscles)
            ).filterNot { it in primaryMuscles }
            .distinct()
        val listedEquipment = requireEquipmentList(
            requireArray(exercise, "listedEquipment", "$path.listedEquipment"),
            "$path.listedEquipment"
        )
        return Exercise(
            id = id,
            name = name,
            searchAliases = searchAliases,
            primaryMuscles = primaryMuscles,
            secondaryMuscles = secondaryMuscles,
            listedEquipment = listedEquipment,
            type = readEnum(
                requireString(exercise, "exerciseType", "$path.exerciseType"),
                "$path.exerciseType"
            ),
            isStretch = requireBoolean(exercise, "isStretch", "$path.isStretch"),
            programming = if (exercise.isNull("programming")) {
                null
            } else {
                parseProgramming(
                    requireObject(exercise, "programming", "$path.programming"),
                    "$path.programming"
                )
            },
            reviewedMetadata = if (exercise.isNull("reviewedMetadata")) {
                null
            } else {
                parseReviewedMetadata(
                    requireObject(exercise, "reviewedMetadata", "$path.reviewedMetadata"),
                    "$path.reviewedMetadata"
                )
            }
        )
    }

    private fun parseReviewedMetadata(
        metadata: JSONObject,
        path: String
    ): ReviewedExerciseMetadata = ReviewedExerciseMetadata(
        reviewState = readEnum(
            requireString(metadata, "reviewState", "$path.reviewState"),
            "$path.reviewState"
        ),
        directPrimaryMuscle = requireString(
            metadata,
            "directPrimaryMuscle",
            "$path.directPrimaryMuscle"
        ),
        descriptiveSecondaryMuscles = requireStringList(
            requireArray(
                metadata,
                "descriptiveSecondaryMuscles",
                "$path.descriptiveSecondaryMuscles"
            ),
            "$path.descriptiveSecondaryMuscles"
        ).toSet(),
        movementPattern = readEnum(
            requireString(metadata, "movementPattern", "$path.movementPattern"),
            "$path.movementPattern"
        ),
        complexity = readEnum<ComplexityTier>(
            requireString(metadata, "complexity", "$path.complexity"),
            "$path.complexity"
        ),
        progressionFamily = requireString(
            metadata,
            "progressionFamily",
            "$path.progressionFamily"
        ),
        prescriptionShape = readEnum<PrescriptionShape>(
            requireString(metadata, "prescriptionShape", "$path.prescriptionShape"),
            "$path.prescriptionShape"
        ),
        approvedRegressions = parseReviewedLinks(
            requireArray(metadata, "approvedRegressions", "$path.approvedRegressions"),
            "$path.approvedRegressions"
        ),
        approvedSubstitutions = parseReviewedLinks(
            requireArray(metadata, "approvedSubstitutions", "$path.approvedSubstitutions"),
            "$path.approvedSubstitutions"
        ),
        capabilityRequirements = requireStringList(
            requireArray(
                metadata,
                "capabilityRequirements",
                "$path.capabilityRequirements"
            ),
            "$path.capabilityRequirements"
        ).mapTo(linkedSetOf()) { value ->
            readEnum<MovementCapabilityType>(value, "$path.capabilityRequirements")
        },
        supportRequirement = readEnum<SupportRequirement>(
            requireString(metadata, "supportRequirement", "$path.supportRequirement"),
            "$path.supportRequirement"
        ),
        impactLevel = readEnum<ImpactLevel>(
            requireString(metadata, "impactLevel", "$path.impactLevel"),
            "$path.impactLevel"
        ),
        equipmentAlternatives = requireEquipmentMatrix(
            requireArray(metadata, "equipmentAlternatives", "$path.equipmentAlternatives"),
            "$path.equipmentAlternatives"
        ),
        provenance = parseReviewProvenance(
            requireObject(metadata, "provenance", "$path.provenance"),
            "$path.provenance"
        )
    )

    private fun parseReviewedLinks(array: JSONArray, path: String): List<ReviewedExerciseLink> =
        buildList(array.length()) {
            for (index in 0 until array.length()) {
                val linkPath = "$path[$index]"
                val link = array.optJSONObject(index)
                    ?: throw PlannerFixtureFormatException("$linkPath must be an object.")
                add(
                    ReviewedExerciseLink(
                        exerciseId = requireString(link, "exerciseId", "$linkPath.exerciseId"),
                        rationale = if (link.isNull("rationale")) {
                            null
                        } else {
                            requireString(link, "rationale", "$linkPath.rationale")
                        }
                    )
                )
            }
        }

    private fun parseReviewProvenance(
        provenance: JSONObject,
        path: String
    ): ReviewProvenance = ReviewProvenance(
        reviewerRole = if (provenance.isNull("reviewerRole")) {
            null
        } else {
            requireString(provenance, "reviewerRole", "$path.reviewerRole")
        },
        rationaleOrSource = requireString(
            provenance,
            "rationaleOrSource",
            "$path.rationaleOrSource"
        ),
        reviewedAtEpochMillis = if (provenance.isNull("reviewedAtEpochMillis")) {
            null
        } else {
            requireLong(provenance, "reviewedAtEpochMillis", "$path.reviewedAtEpochMillis")
        },
        schemaVersion = requireInt(provenance, "schemaVersion", "$path.schemaVersion"),
        policyVersion = requireInt(provenance, "policyVersion", "$path.policyVersion")
    )

    private fun parseProgramming(programming: JSONObject, path: String): ExerciseProgrammingMetadata =
        ExerciseProgrammingMetadata(
            requiredEquipmentCombinations = requireEquipmentMatrix(
                requireArray(
                    programming,
                    "requiredEquipmentCombinations",
                    "$path.requiredEquipmentCombinations"
                ),
                "$path.requiredEquipmentCombinations"
            ),
            movementPattern = readEnum(
                requireString(programming, "movementPattern", "$path.movementPattern"),
                "$path.movementPattern"
            ),
            difficulty = readEnum(
                requireString(programming, "difficulty", "$path.difficulty"),
                "$path.difficulty"
            ),
            mechanics = readEnum(
                requireString(programming, "mechanics", "$path.mechanics"),
                "$path.mechanics"
            ),
            recommendedRepRange = parseRepRange(
                requireObject(programming, "recommendedRepRange", "$path.recommendedRepRange"),
                "$path.recommendedRepRange"
            ),
            fatigueScore = requireInt(programming, "fatigueScore", "$path.fatigueScore"),
            progressionType = readEnum(
                requireString(programming, "progressionType", "$path.progressionType"),
                "$path.progressionType"
            ),
            alternativeExerciseIds = requireStringList(
                requireArray(programming, "alternativeExerciseIds", "$path.alternativeExerciseIds"),
                "$path.alternativeExerciseIds"
            ),
            coachingSummary = requireString(programming, "coachingSummary", "$path.coachingSummary")
        )

    private fun parseRepRange(repRange: JSONObject, path: String): RepRange {
        val min = requireInt(repRange, "min", "$path.min")
        val max = requireInt(repRange, "max", "$path.max")
        if (min <= 0 || max < min) {
            throw PlannerFixtureFormatException("$path must have positive ordered min/max values.")
        }
        return RepRange(min = min, max = max)
    }

    private fun requireEquipmentMatrix(array: JSONArray, path: String): List<List<String>> =
        buildList(array.length()) {
            for (index in 0 until array.length()) {
                add(
                    requireEquipmentList(
                        requireArrayObject(array, index, "$path[$index]"),
                        "$path[$index]",
                        minSize = 1
                    )
                )
            }
        }

    private fun requireEquipmentList(
        array: JSONArray,
        path: String,
        minSize: Int = 0
    ): List<String> {
        val values = requireStringList(array, path, minSize)
        val unknown = values.firstOrNull { it !in StandardEquipment.ALL }
        if (unknown != null) {
            throw PlannerFixtureFormatException("$path contains unknown equipment '$unknown'.")
        }
        return values
    }

    private fun requireStringList(
        array: JSONArray,
        path: String,
        minSize: Int = 0
    ): List<String> {
        val values = buildList(array.length()) {
            for (index in 0 until array.length()) {
                val value = array.opt(index)
                if (value !is String || value.isBlank()) {
                    throw PlannerFixtureFormatException("$path[$index] must be a non-blank string.")
                }
                add(value)
            }
        }
        if (values.size < minSize) {
            throw PlannerFixtureFormatException("$path must contain at least $minSize item(s).")
        }
        return values
    }

    private fun requireObject(source: JSONObject, key: String, path: String): JSONObject =
        source.optJSONObject(key) ?: throw PlannerFixtureFormatException("$path must be an object.")

    private fun requireArray(source: JSONObject, key: String, path: String): JSONArray =
        source.optJSONArray(key) ?: throw PlannerFixtureFormatException("$path must be an array.")

    private fun requireArrayObject(array: JSONArray, index: Int, path: String): JSONArray =
        array.optJSONArray(index) ?: throw PlannerFixtureFormatException("$path must be an array.")

    private fun requireString(source: JSONObject, key: String, path: String): String {
        val value = source.opt(key)
        if (value !is String || value.isBlank()) {
            throw PlannerFixtureFormatException("$path must be a non-blank string.")
        }
        return value
    }

    private fun requireBoolean(source: JSONObject, key: String, path: String): Boolean {
        val value = source.opt(key)
        if (value !is Boolean) {
            throw PlannerFixtureFormatException("$path must be a boolean.")
        }
        return value
    }

    private fun requireInt(source: JSONObject, key: String, path: String): Int {
        val value = source.opt(key)
        val number = value as? Number
            ?: throw PlannerFixtureFormatException("$path must be an integer.")
        val intValue = number.toInt()
        if (intValue.toDouble() != number.toDouble()) {
            throw PlannerFixtureFormatException("$path must be an integer.")
        }
        return intValue
    }

    private fun requireLong(source: JSONObject, key: String, path: String): Long {
        val value = source.opt(key)
        val number = value as? Number
            ?: throw PlannerFixtureFormatException("$path must be an integer.")
        val longValue = number.toLong()
        if (longValue.toDouble() != number.toDouble()) {
            throw PlannerFixtureFormatException("$path must be an integer.")
        }
        return longValue
    }

    private inline fun <reified T : Enum<T>> readEnum(value: String, path: String): T {
        val normalized = value.trim().replace('-', '_').uppercase(Locale.ROOT)
        return enumValues<T>().firstOrNull { it.name == normalized }
            ?: throw PlannerFixtureFormatException("$path has unsupported value '$value'.")
    }

    private fun readResourceBytes(path: String): ByteArray {
        val normalizedPath = path.trim().removePrefix("/")
        val stream = classLoader.getResourceAsStream(normalizedPath)
            ?: throw PlannerFixtureFormatException("Resource not found: $normalizedPath")
        return stream.use { input -> readBoundedBytes(input, normalizedPath) }
    }

    private fun readBoundedBytes(input: InputStream, path: String): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            if (output.size() > MAX_RESOURCE_BYTES) {
                throw PlannerFixtureFormatException(
                    "$path exceeds the maximum resource size of $MAX_RESOURCE_BYTES bytes."
                )
            }
        }
        return output.toByteArray()
    }

    private fun decodeUtf8(bytes: ByteArray, path: String): String = try {
        StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (error: CharacterCodingException) {
        throw PlannerFixtureFormatException("$path must be valid UTF-8.", error)
    }

    private companion object {
        private const val DEFAULT_MANIFEST_RESOURCE = "planner-fixtures/manifest.txt"
        internal const val SUPPORTED_CORPUS_POLICY_VERSION = 3
        private const val SUPPORTED_BUNDLED_CATALOG_SCHEMA_VERSION = 1
        private const val EXPECTED_BUNDLED_EXERCISE_COUNT = 302
        private const val MAX_RESOURCE_BYTES = 512 * 1024
        private val BUNDLED_CATALOG_RESOURCE_PATHS = listOf(
            "workout-guide/catalog.json",
            "assets/workout-guide/catalog.json"
        )
    }
}
