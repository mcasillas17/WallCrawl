package wallcrawl.elopenmike.com.core.ai

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.AdaptationState
import wallcrawl.elopenmike.com.core.model.AutomaticEligibilityFailure
import wallcrawl.elopenmike.com.core.model.ExercisePerformanceHistory
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutSet

internal data class PlannerFixture(
    val schemaVersion: Int,
    val id: String,
    val policyVersion: Int,
    val catalogVersion: String,
    val profile: PlannerFixtureProfile,
    val completedWorkoutCount: Int,
    val exerciseHistory: List<ExercisePerformanceHistory>,
    val allowedExerciseIds: List<String> = emptyList(),
    val reviewedEligibility: PlannerFixtureReviewedEligibility? = null,
    val expected: PlannerFixtureExpected
)

internal data class PlannerFixtureReviewedEligibility(
    val adaptationState: AdaptationState,
    val syntheticApprovedExerciseIds: List<String>
)

internal data class PlannerFixtureProfile(
    val goals: Set<FitnessGoal>,
    val experienceLevel: ExperienceLevel,
    val preferredDurationMinutes: Int,
    val daysPerWeek: Int,
    val availableEquipment: List<String>,
    val preferredUnit: WeightUnit,
    val musclePriorities: Map<String, PriorityLevel>,
    val excludedExerciseIds: List<String>,
    val trainingConstraints: Set<TrainingConstraint>,
    val returningAfterBreakWeeks: Int,
    val confirmedStartingLoads: Map<String, Double>,
    val movementCapabilities: MovementCapabilities
)

internal data class PlannerFixtureExpected(
    val outcome: PlannerFixtureOutcome,
    val requiredExerciseIds: Set<String>,
    val forbiddenExerciseIds: Set<String>,
    val requiredAnyExerciseIdGroups: List<Set<String>> = emptyList(),
    val expectedTargetWeights: Map<String, Double> = emptyMap(),
    val workoutNameContains: String? = null,
    val maxTargetSetsPerExercise: Int? = null,
    val automaticEligibilityFailure: AutomaticEligibilityFailure? = null
)

internal enum class PlannerFixtureOutcome {
    SUCCESS,
    NO_CANDIDATES,
    NO_STRENGTH_CANDIDATES,
    NO_CANDIDATES_FOR_ANY_SPLIT,
    REVIEWED_ELIGIBILITY_NO_CANDIDATES
}

internal class PlannerFixtureFormatException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

internal class PlannerFixtureLoader(
    private val classLoader: ClassLoader = checkNotNull(PlannerFixtureLoader::class.java.classLoader)
) {

    fun loadResource(path: String): PlannerFixture {
        val normalizedPath = normalizeResourcePath(path, "resourcePath")
        val bytes = readResourceBytes(normalizedPath)
        val text = decodeUtf8(bytes, normalizedPath)
        try {
            DuplicateFieldScanner(text).scan()
        } catch (error: PlannerFixtureFormatException) {
            if (error.message?.startsWith("Malformed JSON") == true) {
                throw PlannerFixtureFormatException("Malformed JSON in $normalizedPath.", error)
            }
            throw error
        }
        val root = parseRootObject(text, normalizedPath)
        return parseFixture(root)
    }

    fun loadCorpus(manifestPath: String = DEFAULT_MANIFEST_RESOURCE): List<PlannerFixture> {
        val normalizedManifestPath = normalizeResourcePath(manifestPath, "manifestPath")
        val manifestBytes = readResourceBytes(normalizedManifestPath)
        val manifestText = decodeUtf8(manifestBytes, normalizedManifestPath)
        val entries = manifestText.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()

        if (entries.size > MAX_COLLECTION_SIZE) {
            throw PlannerFixtureFormatException(
                "$normalizedManifestPath must list at most $MAX_COLLECTION_SIZE fixture resources."
            )
        }

        val seenPaths = linkedSetOf<String>()
        val seenFixtureIds = linkedSetOf<String>()
        return entries.mapIndexed { index, entry ->
            val entryPath = normalizeResourcePath(entry, "$normalizedManifestPath[$index]")
            if (!seenPaths.add(entryPath)) {
                throw PlannerFixtureFormatException(
                    "Duplicate manifest entry at $normalizedManifestPath[$index]."
                )
            }
            val fixture = loadResource(entryPath)
            if (!seenFixtureIds.add(fixture.id)) {
                throw PlannerFixtureFormatException(
                    "Duplicate fixture id in corpus at root.id."
                )
            }
            fixture
        }
    }

    private fun parseFixture(root: JSONObject): PlannerFixture {
        requireExactFields(root, "root", ROOT_FIELDS, OPTIONAL_ROOT_FIELDS)
        return PlannerFixture(
            schemaVersion = requireExactInt(root, "schemaVersion", "root.schemaVersion", 1..1),
            id = requireSafeId(root, "id", "root.id"),
            policyVersion = requireExactInt(root, "policyVersion", "root.policyVersion", 1..10_000),
            catalogVersion = requireString(root, "catalogVersion", "root.catalogVersion", allowBlank = false, maxLength = 80),
            profile = parseProfile(requireObject(root, "profile", "root.profile")),
            completedWorkoutCount = requireExactInt(
                root,
                "completedWorkoutCount",
                "root.completedWorkoutCount",
                0..1_000_000
            ),
            exerciseHistory = parseExerciseHistory(
                requireArray(root, "exerciseHistory", "root.exerciseHistory")
            ),
            allowedExerciseIds = if (root.has("allowedExerciseIds")) {
                parseSafeIdList(
                    requireArray(root, "allowedExerciseIds", "root.allowedExerciseIds"),
                    "root.allowedExerciseIds"
                )
            } else {
                emptyList()
            },
            reviewedEligibility = if (root.has("reviewedEligibility")) {
                parseReviewedEligibility(
                    requireObject(root, "reviewedEligibility", "root.reviewedEligibility")
                )
            } else {
                null
            },
            expected = parseExpected(requireObject(root, "expected", "root.expected"))
        )
    }

    private fun parseReviewedEligibility(
        reviewedEligibility: JSONObject
    ): PlannerFixtureReviewedEligibility {
        requireExactFields(
            reviewedEligibility,
            "root.reviewedEligibility",
            REVIEWED_ELIGIBILITY_FIELDS
        )
        return PlannerFixtureReviewedEligibility(
            adaptationState = parseEnum<AdaptationState>(
                reviewedEligibility.get("adaptationState"),
                "root.reviewedEligibility.adaptationState"
            ),
            syntheticApprovedExerciseIds = parseSafeIdList(
                requireArray(
                    reviewedEligibility,
                    "syntheticApprovedExerciseIds",
                    "root.reviewedEligibility.syntheticApprovedExerciseIds"
                ),
                "root.reviewedEligibility.syntheticApprovedExerciseIds"
            )
        )
    }

    private fun parseProfile(profile: JSONObject): PlannerFixtureProfile {
        requireExactFields(profile, "profile", PROFILE_FIELDS)
        return PlannerFixtureProfile(
            goals = parseEnumSet<FitnessGoal>(
                requireArray(profile, "goals", "profile.goals"),
                "profile.goals",
                minSize = 1
            ),
            experienceLevel = parseEnum<ExperienceLevel>(
                profile.get("experienceLevel"),
                "profile.experienceLevel"
            ),
            preferredDurationMinutes = requireExactInt(
                profile,
                "preferredDurationMinutes",
                "profile.preferredDurationMinutes",
                20..120
            ),
            daysPerWeek = requireExactInt(profile, "daysPerWeek", "profile.daysPerWeek", 2..6),
            availableEquipment = parseKnownStringList(
                requireArray(profile, "availableEquipment", "profile.availableEquipment"),
                "profile.availableEquipment",
                minSize = 1,
                knownValues = KNOWN_EQUIPMENT,
                sourceName = "StandardEquipment"
            ),
            preferredUnit = parseEnum<WeightUnit>(profile.get("preferredUnit"), "profile.preferredUnit"),
            musclePriorities = parseMusclePriorities(
                requireObject(profile, "musclePriorities", "profile.musclePriorities")
            ),
            excludedExerciseIds = parseSafeIdList(
                requireArray(profile, "excludedExerciseIds", "profile.excludedExerciseIds"),
                "profile.excludedExerciseIds"
            ),
            trainingConstraints = parseEnumSet<TrainingConstraint>(
                requireArray(profile, "trainingConstraints", "profile.trainingConstraints"),
                "profile.trainingConstraints"
            ),
            returningAfterBreakWeeks = requireExactInt(
                profile,
                "returningAfterBreakWeeks",
                "profile.returningAfterBreakWeeks",
                0..520
            ),
            confirmedStartingLoads = parseConfirmedStartingLoads(
                requireObject(profile, "confirmedStartingLoads", "profile.confirmedStartingLoads")
            ),
            movementCapabilities = parseMovementCapabilities(
                requireObject(profile, "movementCapabilities", "profile.movementCapabilities")
            )
        )
    }

    private fun parseExerciseHistory(historyArray: JSONArray): List<ExercisePerformanceHistory> {
        requireArrayBounds(historyArray, "root.exerciseHistory", MAX_HISTORY_SIZE)
        val history = mutableListOf<ExercisePerformanceHistory>()
        val seenExerciseIds = linkedSetOf<String>()
        for (index in 0 until historyArray.length()) {
            val path = "root.exerciseHistory[$index]"
            val entry = requireArrayObject(historyArray, index, path)
            requireExactFields(entry, path, HISTORY_FIELDS)
            val exerciseId = requireSafeId(entry, "exerciseId", "$path.exerciseId")
            if (!seenExerciseIds.add(exerciseId)) {
                throw PlannerFixtureFormatException("Duplicate exercise history entry at $path.exerciseId.")
            }
            history += ExercisePerformanceHistory(
                exerciseId = exerciseId,
                lastWeight = requireNullableDouble(entry, "lastWeight", "$path.lastWeight", 0.0, MAX_WEIGHT),
                lastReps = requireNullableInt(entry, "lastReps", "$path.lastReps", 1..MAX_REPS),
                bestEstimated1RM = requireNullableDouble(
                    entry,
                    "bestEstimated1RM",
                    "$path.bestEstimated1RM",
                    0.0,
                    MAX_WEIGHT
                ),
                recentSets = parseRecentSets(
                    exerciseId,
                    requireArray(entry, "recentSets", "$path.recentSets"),
                    "$path.recentSets"
                )
            )
        }
        return history
    }

    private fun parseRecentSets(
        exerciseId: String,
        setsArray: JSONArray,
        arrayPath: String
    ): List<WorkoutSet> {
        requireArrayBounds(setsArray, arrayPath, MAX_COLLECTION_SIZE)
        return List(setsArray.length()) { index ->
            val path = "$arrayPath[$index]"
            val set = requireArrayObject(setsArray, index, path)
            requireExactFields(set, path, RECENT_SET_FIELDS)
            val completedReps = requireNullableInt(set, "completedReps", "$path.completedReps", 1..MAX_REPS)
            val completedWeight = requireNullableDouble(
                set,
                "completedWeight",
                "$path.completedWeight",
                0.0,
                MAX_WEIGHT
            )
            val isCompleted = requireBoolean(set, "isCompleted", "$path.isCompleted")
            if (isCompleted && completedReps == null) {
                throw PlannerFixtureFormatException("$path.completedReps is required when $path.isCompleted is true.")
            }
            WorkoutSet(
                id = "$exerciseId-recent-set-${index + 1}",
                workoutExerciseId = "$exerciseId-history",
                setNumber = index + 1,
                exerciseType = if (completedWeight == null) {
                    ExerciseType.BODYWEIGHT_REPS
                } else {
                    ExerciseType.WEIGHT_REPS
                },
                completedReps = completedReps,
                completedWeight = completedWeight,
                isCompleted = isCompleted
            )
        }
    }

    private fun parseExpected(expected: JSONObject): PlannerFixtureExpected {
        requireExactFields(expected, "root.expected", EXPECTED_REQUIRED_FIELDS, EXPECTED_OPTIONAL_FIELDS)
        val outcome = parseEnum<PlannerFixtureOutcome>(expected.get("outcome"), "expected.outcome")
        val requiredExerciseIds = parseSafeIdSet(
            requireArray(expected, "requiredExerciseIds", "expected.requiredExerciseIds"),
            "expected.requiredExerciseIds"
        )
        val forbiddenExerciseIds = parseSafeIdSet(
            requireArray(expected, "forbiddenExerciseIds", "expected.forbiddenExerciseIds"),
            "expected.forbiddenExerciseIds"
        )
        val requiredAnyExerciseIdGroups = if (expected.has("requiredAnyExerciseIdGroups")) {
            parseRequiredAnyExerciseIdGroups(
                requireArray(
                    expected,
                    "requiredAnyExerciseIdGroups",
                    "expected.requiredAnyExerciseIdGroups"
                )
            )
        } else {
            emptyList()
        }
        val expectedTargetWeights = if (expected.has("expectedTargetWeights")) {
            parseExpectedTargetWeights(
                requireObject(expected, "expectedTargetWeights", "expected.expectedTargetWeights")
            )
        } else {
            emptyMap()
        }
        val automaticEligibilityFailure = if (expected.has("automaticEligibilityFailure")) {
            parseEnum<AutomaticEligibilityFailure>(
                expected.get("automaticEligibilityFailure"),
                "expected.automaticEligibilityFailure"
            )
        } else {
            null
        }
        if (
            outcome == PlannerFixtureOutcome.REVIEWED_ELIGIBILITY_NO_CANDIDATES &&
            automaticEligibilityFailure == null
        ) {
            throw PlannerFixtureFormatException(
                "expected.automaticEligibilityFailure is required for reviewed eligibility failures."
            )
        }
        if (
            outcome != PlannerFixtureOutcome.REVIEWED_ELIGIBILITY_NO_CANDIDATES &&
            automaticEligibilityFailure != null
        ) {
            throw PlannerFixtureFormatException(
                "expected.automaticEligibilityFailure is only supported for reviewed eligibility failures."
            )
        }
        if (outcome != PlannerFixtureOutcome.SUCCESS) {
            when {
                requiredExerciseIds.isNotEmpty() ->
                    throw PlannerFixtureFormatException(
                        "expected.requiredExerciseIds is only supported when expected.outcome is SUCCESS."
                    )
                forbiddenExerciseIds.isNotEmpty() ->
                    throw PlannerFixtureFormatException(
                        "expected.forbiddenExerciseIds is only supported when expected.outcome is SUCCESS."
                    )
                requiredAnyExerciseIdGroups.isNotEmpty() ->
                    throw PlannerFixtureFormatException(
                        "expected.requiredAnyExerciseIdGroups is only supported when expected.outcome is SUCCESS."
                    )
                expectedTargetWeights.isNotEmpty() ->
                    throw PlannerFixtureFormatException(
                        "expected.expectedTargetWeights is only supported when expected.outcome is SUCCESS."
                    )
                expected.has("workoutNameContains") ->
                    throw PlannerFixtureFormatException(
                        "expected.workoutNameContains is only supported when expected.outcome is SUCCESS."
                    )
                expected.has("maxTargetSetsPerExercise") ->
                    throw PlannerFixtureFormatException(
                        "expected.maxTargetSetsPerExercise is only supported when expected.outcome is SUCCESS."
                    )
            }
        }
        val overlap = requiredExerciseIds.intersect(forbiddenExerciseIds)
        if (overlap.isNotEmpty()) {
            throw PlannerFixtureFormatException(
                "expected requiredExerciseIds and forbiddenExerciseIds must not overlap."
            )
        }
        val contradictoryRequiredAny = requiredAnyExerciseIdGroups.firstOrNull { group ->
            group.any { it in forbiddenExerciseIds }
        }
        if (contradictoryRequiredAny != null) {
            throw PlannerFixtureFormatException(
                "expected.requiredAnyExerciseIdGroups must not overlap forbiddenExerciseIds."
            )
        }
        return PlannerFixtureExpected(
            outcome = outcome,
            requiredExerciseIds = requiredExerciseIds,
            forbiddenExerciseIds = forbiddenExerciseIds,
            requiredAnyExerciseIdGroups = requiredAnyExerciseIdGroups,
            expectedTargetWeights = expectedTargetWeights,
            workoutNameContains = if (expected.has("workoutNameContains")) {
                requireString(
                    expected,
                    "workoutNameContains",
                    "expected.workoutNameContains",
                    allowBlank = false,
                    maxLength = MAX_STRING_LENGTH
                )
            } else {
                null
            },
            maxTargetSetsPerExercise = if (expected.has("maxTargetSetsPerExercise")) {
                requireExactInt(
                    expected,
                    "maxTargetSetsPerExercise",
                    "expected.maxTargetSetsPerExercise",
                    1..20
                )
            } else {
                null
            },
            automaticEligibilityFailure = automaticEligibilityFailure
        )
    }

    private fun parseRequiredAnyExerciseIdGroups(groupsArray: JSONArray): List<Set<String>> {
        requireArrayBounds(groupsArray, "expected.requiredAnyExerciseIdGroups", MAX_COLLECTION_SIZE)
        val groups = mutableListOf<Set<String>>()
        val seenGroups = linkedSetOf<String>()
        for (index in 0 until groupsArray.length()) {
            val groupPath = "expected.requiredAnyExerciseIdGroups[$index]"
            val ids = parseSafeIdList(requireArrayArray(groupsArray, index, groupPath), groupPath).toSet()
            if (ids.isEmpty()) {
                throw PlannerFixtureFormatException("$groupPath must contain at least 1 item(s).")
            }
            val fingerprint = ids.sorted().joinToString("\u0000")
            if (!seenGroups.add(fingerprint)) {
                throw PlannerFixtureFormatException("Duplicate value at $groupPath.")
            }
            groups += ids
        }
        return groups
    }

    private fun parseExpectedTargetWeights(weights: JSONObject): Map<String, Double> {
        requireObjectBounds(weights, "expected.expectedTargetWeights", MAX_COLLECTION_SIZE)
        val result = linkedMapOf<String, Double>()
        weights.keySet().sorted().forEach { key ->
            val path = "expected.expectedTargetWeights.$key"
            validateSafeId(key, path)
            result[key] = requireDouble(weights.get(key), path, 0.0, MAX_WEIGHT)
        }
        return result
    }

    private fun parseMusclePriorities(priorities: JSONObject): Map<String, PriorityLevel> {
        requireObjectBounds(priorities, "profile.musclePriorities", MAX_COLLECTION_SIZE)
        val result = linkedMapOf<String, PriorityLevel>()
        priorities.keySet().sorted().forEach { muscle ->
            if (muscle.length > MAX_STRING_LENGTH) {
                throw PlannerFixtureFormatException(
                    "profile.musclePriorities contains a key longer than $MAX_STRING_LENGTH characters."
                )
            }
            val path = "profile.musclePriorities.$muscle"
            if (muscle !in KNOWN_MUSCLES) {
                throw PlannerFixtureFormatException("$path must use a known StandardMuscles value.")
            }
            result[muscle] = parseEnum(priorities.get(muscle), path)
        }
        return result
    }

    private fun parseConfirmedStartingLoads(loads: JSONObject): Map<String, Double> {
        requireObjectBounds(loads, "profile.confirmedStartingLoads", MAX_COLLECTION_SIZE)
        val result = linkedMapOf<String, Double>()
        loads.keySet().sorted().forEach { key ->
            val path = "profile.confirmedStartingLoads.$key"
            validateSafeId(key, path)
            result[key] = requireDouble(loads.get(key), path, 0.0, MAX_WEIGHT)
        }
        return result
    }

    private fun parseMovementCapabilities(capabilities: JSONObject): MovementCapabilities {
        requireObjectBounds(capabilities, "profile.movementCapabilities", MAX_COLLECTION_SIZE)
        val result = linkedMapOf<MovementCapabilityType, CapabilityLevel>()
        capabilities.keySet().sorted().forEach { key ->
            if (key.length > MAX_STRING_LENGTH) {
                throw PlannerFixtureFormatException(
                    "profile.movementCapabilities contains a key longer than $MAX_STRING_LENGTH characters."
                )
            }
            val path = "profile.movementCapabilities.$key"
            val type = MovementCapabilityType.entries.find { it.name == key }
                ?: throw PlannerFixtureFormatException("$path must use a known MovementCapabilityType value.")
            result[type] = parseEnum(capabilities.get(key), path)
        }
        return MovementCapabilities.from(result)
    }

    private fun parseSafeIdList(array: JSONArray, path: String): List<String> {
        requireArrayBounds(array, path, MAX_COLLECTION_SIZE)
        val seen = linkedSetOf<String>()
        return List(array.length()) { index ->
            val itemPath = "$path[$index]"
            val id = requireSafeId(array.get(index), itemPath)
            if (!seen.add(id)) {
                throw PlannerFixtureFormatException("Duplicate value at $itemPath.")
            }
            id
        }
    }

    private fun parseSafeIdSet(array: JSONArray, path: String): Set<String> =
        parseSafeIdList(array, path).toSet()

    private fun parseKnownStringList(
        array: JSONArray,
        path: String,
        minSize: Int,
        knownValues: Set<String>,
        sourceName: String
    ): List<String> {
        requireArrayBounds(array, path, MAX_COLLECTION_SIZE)
        val result = mutableListOf<String>()
        val seen = linkedSetOf<String>()
        for (index in 0 until array.length()) {
            val itemPath = "$path[$index]"
            val value = requireString(array.get(index), itemPath, allowBlank = false, maxLength = MAX_STRING_LENGTH)
            if (!seen.add(value)) {
                throw PlannerFixtureFormatException("Duplicate value at $itemPath.")
            }
            if (value !in knownValues) {
                throw PlannerFixtureFormatException("$itemPath must use a known $sourceName value.")
            }
            result += value
        }
        if (result.size < minSize) {
            throw PlannerFixtureFormatException("$path must contain at least $minSize item(s).")
        }
        return result
    }

    private inline fun <reified T : Enum<T>> parseEnumSet(
        array: JSONArray,
        path: String,
        minSize: Int = 0
    ): Set<T> {
        requireArrayBounds(array, path, MAX_COLLECTION_SIZE)
        val result = linkedSetOf<T>()
        val seenNames = linkedSetOf<String>()
        for (index in 0 until array.length()) {
            val itemPath = "$path[$index]"
            val rawName = requireString(array.get(index), itemPath, allowBlank = false, maxLength = MAX_STRING_LENGTH)
            if (!seenNames.add(rawName)) {
                throw PlannerFixtureFormatException("Duplicate value at $itemPath.")
            }
            result += parseEnum<T>(rawName, itemPath)
        }
        if (result.size < minSize) {
            throw PlannerFixtureFormatException("$path must contain at least $minSize item(s).")
        }
        return result
    }

    private inline fun <reified T : Enum<T>> parseEnum(value: Any, path: String): T {
        val enumName = requireString(value, path, allowBlank = false, maxLength = MAX_STRING_LENGTH)
        return enumValues<T>().find { it.name == enumName }
            ?: throw PlannerFixtureFormatException("$path must use a known ${T::class.java.simpleName} value.")
    }

    private fun requireExactFields(
        objectValue: JSONObject,
        path: String,
        requiredFields: Set<String>,
        optionalFields: Set<String> = emptySet()
    ) {
        val allowedFields = requiredFields + optionalFields
        val unexpectedField = objectValue.keySet().sorted().firstOrNull { it !in allowedFields }
        if (unexpectedField != null) {
            throw PlannerFixtureFormatException("Unexpected field at $path.$unexpectedField.")
        }
        val missingField = requiredFields.sorted().firstOrNull { !objectValue.has(it) }
        if (missingField != null) {
            throw PlannerFixtureFormatException("Missing required field at $path.$missingField.")
        }
    }

    private fun requireObject(source: JSONObject, key: String, path: String): JSONObject {
        val value = source.get(key)
        if (value !is JSONObject) {
            throw PlannerFixtureFormatException("$path must be an object.")
        }
        return value
    }

    private fun requireArray(source: JSONObject, key: String, path: String): JSONArray {
        val value = source.get(key)
        if (value !is JSONArray) {
            throw PlannerFixtureFormatException("$path must be an array.")
        }
        return value
    }

    private fun requireArrayObject(array: JSONArray, index: Int, path: String): JSONObject {
        val value = array.get(index)
        if (value !is JSONObject) {
            throw PlannerFixtureFormatException("$path must be an object.")
        }
        return value
    }

    private fun requireArrayArray(array: JSONArray, index: Int, path: String): JSONArray {
        val value = array.get(index)
        if (value !is JSONArray) {
            throw PlannerFixtureFormatException("$path must be an array.")
        }
        return value
    }

    private fun requireExactInt(source: JSONObject, key: String, path: String, range: IntRange): Int =
        requireInt(source.get(key), path, range)

    private fun requireNullableInt(
        source: JSONObject,
        key: String,
        path: String,
        range: IntRange
    ): Int? {
        val value = source.get(key)
        return if (value == JSONObject.NULL) null else requireInt(value, path, range)
    }

    private fun requireInt(value: Any, path: String, range: IntRange): Int {
        if (value !is Number) {
            throw PlannerFixtureFormatException("$path must be an integer.")
        }
        val raw = value.toString()
        if (!INTEGER_PATTERN.matches(raw)) {
            throw PlannerFixtureFormatException("$path must be an integer.")
        }
        val parsed = raw.toLongOrNull()
            ?: throw PlannerFixtureFormatException("$path must be an integer.")
        if (parsed !in range.first.toLong()..range.last.toLong()) {
            throw PlannerFixtureFormatException(
                "$path must be between ${range.first} and ${range.last}."
            )
        }
        return parsed.toInt()
    }

    private fun requireNullableDouble(
        source: JSONObject,
        key: String,
        path: String,
        min: Double,
        max: Double
    ): Double? {
        val value = source.get(key)
        return if (value == JSONObject.NULL) null else requireDouble(value, path, min, max)
    }

    private fun requireDouble(value: Any, path: String, min: Double, max: Double): Double {
        if (value !is Number) {
            throw PlannerFixtureFormatException("$path must be a number.")
        }
        val number = value.toString().toDoubleOrNull()
            ?: throw PlannerFixtureFormatException("$path must be a number.")
        if (!number.isFinite()) {
            throw PlannerFixtureFormatException("$path must be finite.")
        }
        if (number < min || number > max) {
            throw PlannerFixtureFormatException("$path must be between $min and $max.")
        }
        return number
    }

    private fun requireBoolean(source: JSONObject, key: String, path: String): Boolean {
        val value = source.get(key)
        return when (value) {
            is Boolean -> value
            else -> throw PlannerFixtureFormatException("$path must be a boolean.")
        }
    }

    private fun requireString(
        source: JSONObject,
        key: String,
        path: String,
        allowBlank: Boolean,
        maxLength: Int
    ): String = requireString(source.get(key), path, allowBlank, maxLength)

    private fun requireString(
        value: Any,
        path: String,
        allowBlank: Boolean,
        maxLength: Int
    ): String {
        if (value !is String) {
            throw PlannerFixtureFormatException("$path must be a string.")
        }
        if (value.length > maxLength) {
            throw PlannerFixtureFormatException(
                "$path must be at most $maxLength characters long."
            )
        }
        if (!allowBlank && value.isBlank()) {
            throw PlannerFixtureFormatException("$path must not be blank.")
        }
        return value
    }

    private fun requireSafeId(source: JSONObject, key: String, path: String): String =
        requireSafeId(source.get(key), path)

    private fun requireSafeId(value: Any, path: String): String {
        val id = requireString(value, path, allowBlank = false, maxLength = MAX_ID_LENGTH)
        validateSafeId(id, path)
        return id
    }

    private fun validateSafeId(value: String, path: String) {
        if (!SAFE_ID_PATTERN.matches(value)) {
            throw PlannerFixtureFormatException(
                "$path must match ${SAFE_ID_PATTERN.pattern} and be at most $MAX_ID_LENGTH characters long."
            )
        }
    }

    private fun requireArrayBounds(array: JSONArray, path: String, maxSize: Int) {
        if (array.length() > maxSize) {
            throw PlannerFixtureFormatException("$path must contain at most $maxSize item(s).")
        }
    }

    private fun requireObjectBounds(objectValue: JSONObject, path: String, maxSize: Int) {
        if (objectValue.length() > maxSize) {
            throw PlannerFixtureFormatException("$path must contain at most $maxSize entries.")
        }
    }

    private fun parseRootObject(text: String, path: String): JSONObject = try {
        val value = JSONTokener(text).nextValue()
        value as? JSONObject ?: throw PlannerFixtureFormatException("$path must contain a JSON object at the root.")
    } catch (error: JSONException) {
        throw PlannerFixtureFormatException("Malformed JSON in $path.", error)
    }

    private fun normalizeResourcePath(path: String, label: String): String {
        val trimmed = path.trim().removePrefix("/")
        if (trimmed.isEmpty()) {
            throw PlannerFixtureFormatException("$label must not be blank.")
        }
        if (trimmed.length > MAX_STRING_LENGTH) {
            throw PlannerFixtureFormatException(
                "$label must be at most $MAX_STRING_LENGTH characters long."
            )
        }
        if (trimmed.contains("..") || trimmed.contains('\\')) {
            throw PlannerFixtureFormatException("$label must stay within test resources.")
        }
        if (!RESOURCE_PATH_PATTERN.matches(trimmed)) {
            throw PlannerFixtureFormatException("$label must be a safe classpath resource path.")
        }
        return trimmed
    }

    private fun readResourceBytes(path: String): ByteArray {
        val stream = classLoader.getResourceAsStream(path)
            ?: throw PlannerFixtureFormatException("Resource not found: $path")
        return stream.use { input ->
            readBoundedBytes(input, path)
        }
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
        private const val MAX_RESOURCE_BYTES = 128 * 1024
        private const val MAX_STRING_LENGTH = 256
        private const val MAX_COLLECTION_SIZE = 100
        private const val MAX_HISTORY_SIZE = 8
        private const val MAX_ID_LENGTH = 80
        private const val MAX_WEIGHT = 10_000.0
        private const val MAX_REPS = 1_000
        private val SAFE_ID_PATTERN = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
        private val RESOURCE_PATH_PATTERN = Regex("[A-Za-z0-9._/-]+")
        private val INTEGER_PATTERN = Regex("-?(0|[1-9][0-9]*)")
        private val ROOT_FIELDS = setOf(
            "schemaVersion",
            "id",
            "policyVersion",
            "catalogVersion",
            "profile",
            "completedWorkoutCount",
            "exerciseHistory",
            "expected"
        )
        private val OPTIONAL_ROOT_FIELDS = setOf("allowedExerciseIds", "reviewedEligibility")
        private val REVIEWED_ELIGIBILITY_FIELDS = setOf(
            "adaptationState",
            "syntheticApprovedExerciseIds"
        )
        private val PROFILE_FIELDS = setOf(
            "goals",
            "experienceLevel",
            "preferredDurationMinutes",
            "daysPerWeek",
            "availableEquipment",
            "preferredUnit",
            "musclePriorities",
            "excludedExerciseIds",
            "trainingConstraints",
            "returningAfterBreakWeeks",
            "confirmedStartingLoads",
            "movementCapabilities"
        )
        private val HISTORY_FIELDS = setOf(
            "exerciseId",
            "lastWeight",
            "lastReps",
            "bestEstimated1RM",
            "recentSets"
        )
        private val RECENT_SET_FIELDS = setOf(
            "completedReps",
            "completedWeight",
            "isCompleted"
        )
        private val EXPECTED_REQUIRED_FIELDS = setOf(
            "outcome",
            "requiredExerciseIds",
            "forbiddenExerciseIds"
        )
        private val EXPECTED_OPTIONAL_FIELDS = setOf(
            "requiredAnyExerciseIdGroups",
            "expectedTargetWeights",
            "workoutNameContains",
            "maxTargetSetsPerExercise",
            "automaticEligibilityFailure"
        )
        private val KNOWN_EQUIPMENT = StandardEquipment.ALL.toSet()
        private val KNOWN_MUSCLES = StandardMuscles.ALL.toSet()
    }
}

private class DuplicateFieldScanner(
    private val text: String
) {
    private var index: Int = 0

    fun scan() {
        skipWhitespace()
        scanValue("root", depth = 0)
        skipWhitespace()
        if (index != text.length) {
            throw PlannerFixtureFormatException("Malformed JSON at root.")
        }
    }

    private fun scanValue(path: String, depth: Int) {
        skipWhitespace()
        when (val next = currentChar()) {
            '{' -> scanObject(path, depth)
            '[' -> scanArray(path, depth)
            '"' -> scanString(path)
            't' -> scanLiteral("true", path)
            'f' -> scanLiteral("false", path)
            'n' -> scanLiteral("null", path)
            '-', in '0'..'9' -> scanNumber(path)
            else -> throw PlannerFixtureFormatException(
                if (next == null) "Malformed JSON at $path." else "Malformed JSON at $path."
            )
        }
    }

    private fun scanObject(path: String, depth: Int) {
        requireNestedValueAllowed(depth)
        expect('{', path)
        skipWhitespace()
        if (currentChar() == '}') {
            index++
            return
        }
        val seenKeys = linkedSetOf<String>()
        while (true) {
            val key = scanString(path)
            val keyPath = "$path.$key"
            if (!seenKeys.add(key)) {
                throw PlannerFixtureFormatException("Duplicate field at $keyPath.")
            }
            skipWhitespace()
            expect(':', keyPath)
            scanValue(keyPath, depth + 1)
            skipWhitespace()
            when (currentChar()) {
                ',' -> {
                    index++
                    skipWhitespace()
                }
                '}' -> {
                    index++
                    return
                }
                else -> throw PlannerFixtureFormatException("Malformed JSON at $path.")
            }
        }
    }

    private fun scanArray(path: String, depth: Int) {
        requireNestedValueAllowed(depth)
        expect('[', path)
        skipWhitespace()
        if (currentChar() == ']') {
            index++
            return
        }
        var itemIndex = 0
        while (true) {
            scanValue("$path[$itemIndex]", depth + 1)
            itemIndex++
            skipWhitespace()
            when (currentChar()) {
                ',' -> {
                    index++
                    skipWhitespace()
                }
                ']' -> {
                    index++
                    return
                }
                else -> throw PlannerFixtureFormatException("Malformed JSON at $path.")
            }
        }
    }

    private fun requireNestedValueAllowed(depth: Int) {
        if (depth >= MAX_JSON_NESTING_DEPTH) {
            throw PlannerFixtureFormatException("Planner fixture JSON exceeds maximum nesting depth.")
        }
    }

    private fun scanString(path: String): String {
        expect('"', path)
        val builder = StringBuilder()
        while (true) {
            val ch = currentChar() ?: throw PlannerFixtureFormatException("Malformed JSON at $path.")
            index++
            when (ch) {
                '"' -> return builder.toString()
                '\\' -> builder.append(scanEscape(path))
                else -> {
                    if (ch.code < 0x20) {
                        throw PlannerFixtureFormatException("Malformed JSON at $path.")
                    }
                    builder.append(ch)
                }
            }
        }
    }

    private fun scanEscape(path: String): Char {
        val escaped = currentChar() ?: throw PlannerFixtureFormatException("Malformed JSON at $path.")
        index++
        return when (escaped) {
            '"', '\\', '/' -> escaped
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                if (index + 4 > text.length) {
                    throw PlannerFixtureFormatException("Malformed JSON at $path.")
                }
                val hex = text.substring(index, index + 4)
                if (!hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                    throw PlannerFixtureFormatException("Malformed JSON at $path.")
                }
                index += 4
                hex.toInt(16).toChar()
            }
            else -> throw PlannerFixtureFormatException("Malformed JSON at $path.")
        }
    }

    private fun scanLiteral(literal: String, path: String) {
        if (!text.startsWith(literal, index)) {
            throw PlannerFixtureFormatException("Malformed JSON at $path.")
        }
        index += literal.length
    }

    private fun scanNumber(path: String) {
        if (currentChar() == '-') {
            index++
        }
        when (currentChar()) {
            '0' -> index++
            in '1'..'9' -> {
                index++
                while (currentChar()?.isDigit() == true) {
                    index++
                }
            }
            else -> throw PlannerFixtureFormatException("Malformed JSON at $path.")
        }
        if (currentChar() == '.') {
            index++
            if (currentChar()?.isDigit() != true) {
                throw PlannerFixtureFormatException("Malformed JSON at $path.")
            }
            while (currentChar()?.isDigit() == true) {
                index++
            }
        }
        if (currentChar() == 'e' || currentChar() == 'E') {
            index++
            if (currentChar() == '+' || currentChar() == '-') {
                index++
            }
            if (currentChar()?.isDigit() != true) {
                throw PlannerFixtureFormatException("Malformed JSON at $path.")
            }
            while (currentChar()?.isDigit() == true) {
                index++
            }
        }
    }

    private fun skipWhitespace() {
        while (currentChar()?.isWhitespace() == true) {
            index++
        }
    }

    private fun expect(expected: Char, path: String) {
        if (currentChar() != expected) {
            throw PlannerFixtureFormatException("Malformed JSON at $path.")
        }
        index++
    }

    private fun currentChar(): Char? = text.getOrNull(index)

    private companion object {
        // Planner fixtures have a shallow fixed schema, so deeper JSON is malformed input.
        private const val MAX_JSON_NESTING_DEPTH = 32
    }
}
