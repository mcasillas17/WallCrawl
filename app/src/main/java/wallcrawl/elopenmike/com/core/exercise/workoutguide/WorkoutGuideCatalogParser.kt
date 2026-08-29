package wallcrawl.elopenmike.com.core.exercise.workoutguide

import android.util.JsonReader
import android.util.JsonToken
import java.io.IOException
import java.io.Reader
import java.util.Locale
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisual
import wallcrawl.elopenmike.com.core.model.Difficulty
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseAttribution
import wallcrawl.elopenmike.com.core.model.ExerciseAttributionSource
import wallcrawl.elopenmike.com.core.model.ExerciseProgrammingMetadata
import wallcrawl.elopenmike.com.core.model.ExerciseSource
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.MechanicsType
import wallcrawl.elopenmike.com.core.model.MovementPattern
import wallcrawl.elopenmike.com.core.model.MuscleVocabulary
import wallcrawl.elopenmike.com.core.model.ProgressionType
import wallcrawl.elopenmike.com.core.model.RepRange

class WorkoutGuideCatalogFormatException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

/** Converts the import format into WallCrawl-owned domain and visual models. */
class WorkoutGuideCatalogParser {

    fun parse(input: Reader): WorkoutGuideCatalogSnapshot {
        try {
            val reader = JsonReader(input)
            var schemaVersion: Int? = null
            var source: CatalogSource? = null
            var visualSpecification: VisualSpecification? = null
            var parsedExercises: List<ParsedExercise>? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "schemaVersion" -> schemaVersion = reader.nextInt()
                    "source" -> source = reader.readCatalogSource()
                    "visuals" -> visualSpecification = reader.readVisualSpecification()
                    "exercises" -> parsedExercises = reader.readExercises()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                malformed("Unexpected content after the catalog root object.")
            }

            if (schemaVersion != SUPPORTED_SCHEMA_VERSION) {
                malformed(
                    "Unsupported Workout Guide schemaVersion $schemaVersion; " +
                        "expected $SUPPORTED_SCHEMA_VERSION."
                )
            }
            val validatedSource = source.requireValid()
            val visuals = visualSpecification.requireValid()
            val entries = parsedExercises
                ?: malformed("Catalog is missing its exercises array.")
            if (entries.isEmpty()) malformed("Catalog must contain at least one exercise.")

            val duplicateId = entries.groupingBy(ParsedExercise::id).eachCount()
                .entries.firstOrNull { it.value > 1 }?.key
            if (duplicateId != null) malformed("Duplicate exercise id: $duplicateId")
            val duplicateSourceId = entries.groupingBy(ParsedExercise::sourceId).eachCount()
                .entries.firstOrNull { it.value > 1 }?.key
            if (duplicateSourceId != null) malformed("Duplicate source exercise id: $duplicateSourceId")
            val duplicateSlug = entries.groupingBy(ParsedExercise::sourceSlug).eachCount()
                .entries.firstOrNull { it.value > 1 }?.key
            if (duplicateSlug != null) malformed("Duplicate source exercise slug: $duplicateSlug")
            val knownExerciseIds = entries.mapTo(mutableSetOf(), ParsedExercise::id)
            entries.forEach { entry ->
                entry.programming?.alternativeExerciseIds.orEmpty().forEach { alternativeId ->
                    if (alternativeId !in knownExerciseIds) {
                        malformed(
                            "Exercise ${entry.id} references unknown alternative: $alternativeId"
                        )
                    }
                }
            }

            val framesByExerciseId = entries.associate { entry ->
                entry.id to (1..visuals.frameCount).map { frameIndex ->
                    ExerciseVisual(
                        assetPath = "$ASSET_PATH_PREFIX${entry.sourceSlug}/frame-$frameIndex.svg",
                        widthPx = visuals.widthPx,
                        heightPx = visuals.heightPx,
                        attribution = validatedSource.attribution
                    )
                }
            }

            return WorkoutGuideCatalogSnapshot(
                exercises = entries.map { entry -> entry.toExercise(validatedSource.attribution) },
                framesByExerciseId = framesByExerciseId,
                catalogAttribution = CatalogAttribution(
                    repository = validatedSource.repository,
                    commit = validatedSource.commit,
                    assetLicense = validatedSource.assetLicense,
                    attribution = validatedSource.attribution,
                    exerciseCount = entries.size,
                    // Counted, not inferred: the credits screen states this number publicly.
                    frameCount = framesByExerciseId.values.sumOf { it.size }
                )
            )
        } catch (error: WorkoutGuideCatalogFormatException) {
            throw error
        } catch (error: IOException) {
            throw WorkoutGuideCatalogFormatException("Unable to read Workout Guide catalog JSON.", error)
        } catch (error: RuntimeException) {
            throw WorkoutGuideCatalogFormatException("Malformed Workout Guide catalog JSON.", error)
        }
    }

    private fun JsonReader.readCatalogSource(): CatalogSource {
        var repository: String? = null
        var commit: String? = null
        var assetLicense: String? = null
        var attribution: ExerciseAttribution? = null
        beginObject()
        while (hasNext()) {
            when (nextName()) {
                "repository" -> repository = readString("source.repository")
                "commit" -> commit = readString("source.commit")
                "assetLicense" -> assetLicense = readString("source.assetLicense")
                "attribution" -> attribution = readAttribution("source.attribution")
                else -> skipValue()
            }
        }
        endObject()
        return CatalogSource(repository, commit, assetLicense, attribution)
    }

    private fun JsonReader.readVisualSpecification(): VisualSpecification {
        var frameCount: Int? = null
        var widthPx: Int? = null
        var heightPx: Int? = null
        var format: String? = null
        beginObject()
        while (hasNext()) {
            when (nextName()) {
                "frameCount" -> frameCount = nextInt()
                "widthPx" -> widthPx = nextInt()
                "heightPx" -> heightPx = nextInt()
                "format" -> format = readString("visuals.format")
                else -> skipValue()
            }
        }
        endObject()
        return VisualSpecification(frameCount, widthPx, heightPx, format)
    }

    private fun JsonReader.readExercises(): List<ParsedExercise> {
        val exercises = mutableListOf<ParsedExercise>()
        beginArray()
        while (hasNext()) {
            if (exercises.size >= MAX_EXERCISES) {
                malformed("Catalog exceeds the $MAX_EXERCISES exercise limit.")
            }
            exercises += readExercise()
        }
        endArray()
        return exercises
    }

    private fun JsonReader.readExercise(): ParsedExercise {
        var id: String? = null
        var sourceId: String? = null
        var sourceSlug: String? = null
        var name: String? = null
        var aliases: List<String>? = null
        var primaryMuscles: List<String>? = null
        var secondaryMuscles: List<String>? = null
        var listedEquipment: List<String>? = null
        var exerciseType: ExerciseType? = null
        var isStretch: Boolean? = null
        var programming: ExerciseProgrammingMetadata? = null

        beginObject()
        while (hasNext()) {
            when (nextName()) {
                "id" -> id = readString("exercise.id")
                "sourceId" -> sourceId = readString("exercise.sourceId")
                "sourceSlug" -> sourceSlug = readString("exercise.sourceSlug")
                "name" -> name = readString("exercise.name")
                "searchAliases" -> aliases = readStringArray("exercise.searchAliases")
                "primaryMuscles" -> primaryMuscles = readStringArray("exercise.primaryMuscles")
                "secondaryMuscles" -> secondaryMuscles = readStringArray("exercise.secondaryMuscles")
                "listedEquipment" -> listedEquipment = readStringArray("exercise.listedEquipment")
                "exerciseType" -> exerciseType = readExerciseType(readString("exercise.exerciseType"))
                "isStretch" -> isStretch = nextBoolean()
                "programming" -> programming = readProgramming()
                else -> skipValue()
            }
        }
        endObject()

        val exerciseId = id ?: malformed("Exercise is missing id.")
        requireSafeIdentifier(exerciseId, "exercise.id")
        val upstreamId = sourceId ?: malformed("Exercise $exerciseId is missing sourceId.")
        requireSafeIdentifier(upstreamId, "exercise $exerciseId sourceId")
        val upstreamSlug = sourceSlug ?: malformed("Exercise $exerciseId is missing sourceSlug.")
        requireSafeIdentifier(upstreamSlug, "exercise $exerciseId sourceSlug")

        val primary = primaryMuscles
            ?: malformed("Exercise $exerciseId is missing primaryMuscles.")
        if (primary.isEmpty()) malformed("Exercise $exerciseId must have a primary muscle.")

        // Upstream muscle names enter the domain only through the canonical vocabulary, so the
        // planner, muscle priorities, and volume attribution all share one set of names.
        // Umbrella names ("Legs", "Posterior Chain") keep a single representative as primary
        // and contribute the rest as secondary, so weekly set counts stay one per set while
        // split matching — which reads both lists — still sees every group involved.
        val canonicalPrimary = primary.mapNotNull(MuscleVocabulary::canonicalizePrimary).distinct()
        if (canonicalPrimary.isEmpty()) {
            malformed("Exercise $exerciseId has no recognizable primary muscle: $primary")
        }
        val declaredSecondary = secondaryMuscles
            ?: malformed("Exercise $exerciseId is missing secondaryMuscles.")
        val canonicalSecondary = (
            MuscleVocabulary.canonicalizeAll(primary) +
                MuscleVocabulary.canonicalizeAll(declaredSecondary)
            ).filterNot { it in canonicalPrimary }.distinct()

        return ParsedExercise(
            id = exerciseId,
            sourceId = upstreamId,
            sourceSlug = upstreamSlug,
            name = name ?: malformed("Exercise $exerciseId is missing name."),
            searchAliases = aliases ?: malformed("Exercise $exerciseId is missing searchAliases."),
            primaryMuscles = canonicalPrimary,
            secondaryMuscles = canonicalSecondary,
            listedEquipment = listedEquipment
                ?: malformed("Exercise $exerciseId is missing listedEquipment."),
            type = exerciseType ?: malformed("Exercise $exerciseId is missing exerciseType."),
            isStretch = isStretch ?: malformed("Exercise $exerciseId is missing isStretch."),
            programming = programming
        )
    }

    private fun JsonReader.readAttribution(label: String): ExerciseAttribution {
        var creator: String? = null
        var creatorUrl: String? = null
        var license: String? = null
        var licenseUrl: String? = null
        var source: ExerciseAttributionSource? = null
        beginObject()
        while (hasNext()) {
            when (nextName()) {
                "creator" -> creator = readString("$label.creator")
                "creatorUrl" -> creatorUrl = readString("$label.creatorUrl", MAX_URL_LENGTH)
                "license" -> license = readString("$label.license")
                "licenseUrl" -> licenseUrl = readString("$label.licenseUrl", MAX_URL_LENGTH)
                "source" -> source = readAttributionSource("$label.source")
                else -> skipValue()
            }
        }
        endObject()
        return ExerciseAttribution(
            creator = creator ?: malformed("$label is missing creator."),
            // These two are the only values the app ever hands to the system browser, so
            // they are held to the same scheme requirement as the displayed repository URL.
            creatorUrl = requireHttps(creatorUrl, "$label.creatorUrl"),
            license = license ?: malformed("$label is missing license."),
            licenseUrl = requireHttps(licenseUrl, "$label.licenseUrl"),
            source = source
        )
    }

    private fun JsonReader.readAttributionSource(label: String): ExerciseAttributionSource {
        var name: String? = null
        var url: String? = null
        var license: String? = null
        var licenseUrl: String? = null
        var changes: String? = null
        beginObject()
        while (hasNext()) {
            when (nextName()) {
                "name" -> name = readString("$label.name")
                "url" -> url = readString("$label.url", MAX_URL_LENGTH)
                "license" -> license = readString("$label.license")
                "licenseUrl" -> licenseUrl = readString("$label.licenseUrl", MAX_URL_LENGTH)
                "changes" -> changes = readString("$label.changes", MAX_DESCRIPTION_LENGTH)
                else -> skipValue()
            }
        }
        endObject()
        return ExerciseAttributionSource(
            name = name ?: malformed("$label is missing name."),
            url = url ?: malformed("$label is missing url."),
            license = license ?: malformed("$label is missing license."),
            licenseUrl = licenseUrl ?: malformed("$label is missing licenseUrl."),
            changes = changes ?: malformed("$label is missing changes.")
        )
    }

    private fun JsonReader.readProgramming(): ExerciseProgrammingMetadata {
        var combinations: List<List<String>>? = null
        var movementPattern: MovementPattern? = null
        var difficulty: Difficulty? = null
        var mechanics: MechanicsType? = null
        var repRange: RepRange? = null
        var fatigueScore: Int? = null
        var progressionType: ProgressionType? = null
        var alternatives: List<String>? = null
        var coachingSummary: String? = null
        beginObject()
        while (hasNext()) {
            when (nextName()) {
                "requiredEquipmentCombinations" -> combinations = readStringMatrix()
                "movementPattern" -> movementPattern = readMovementPattern(readString("programming.movementPattern"))
                "difficulty" -> difficulty = readDifficulty(readString("programming.difficulty"))
                "mechanics" -> mechanics = readMechanics(readString("programming.mechanics"))
                "recommendedRepRange" -> repRange = readRepRange()
                "fatigueScore" -> fatigueScore = nextInt()
                "progressionType" -> progressionType = readProgressionType(readString("programming.progressionType"))
                "alternativeExerciseIds" -> alternatives = readStringArray("programming.alternativeExerciseIds")
                "coachingSummary" -> coachingSummary = readString(
                    "programming.coachingSummary",
                    MAX_DESCRIPTION_LENGTH
                )
                else -> skipValue()
            }
        }
        endObject()

        val requiredCombinations = combinations
            ?: malformed("Programming metadata is missing requiredEquipmentCombinations.")
        if (requiredCombinations.isEmpty() || requiredCombinations.any(List<String>::isEmpty)) {
            malformed("Programming equipment combinations must be non-empty.")
        }
        val fatigue = fatigueScore ?: malformed("Programming metadata is missing fatigueScore.")
        if (fatigue !in MIN_FATIGUE_SCORE..MAX_FATIGUE_SCORE) {
            malformed("Programming fatigueScore must be between $MIN_FATIGUE_SCORE and $MAX_FATIGUE_SCORE.")
        }

        return ExerciseProgrammingMetadata(
            requiredEquipmentCombinations = requiredCombinations,
            movementPattern = movementPattern
                ?: malformed("Programming metadata is missing movementPattern."),
            difficulty = difficulty ?: malformed("Programming metadata is missing difficulty."),
            mechanics = mechanics ?: malformed("Programming metadata is missing mechanics."),
            recommendedRepRange = repRange
                ?: malformed("Programming metadata is missing recommendedRepRange."),
            fatigueScore = fatigue,
            progressionType = progressionType
                ?: malformed("Programming metadata is missing progressionType."),
            alternativeExerciseIds = alternatives
                ?: malformed("Programming metadata is missing alternativeExerciseIds."),
            coachingSummary = coachingSummary
                ?: malformed("Programming metadata is missing coachingSummary.")
        )
    }

    private fun JsonReader.readStringMatrix(): List<List<String>> {
        val values = mutableListOf<List<String>>()
        beginArray()
        while (hasNext()) {
            if (values.size >= MAX_LIST_ITEMS) malformed("Too many equipment combinations.")
            values += readStringArray("programming.requiredEquipmentCombinations")
        }
        endArray()
        return values
    }

    private fun JsonReader.readRepRange(): RepRange {
        var min: Int? = null
        var max: Int? = null
        beginObject()
        while (hasNext()) {
            when (nextName()) {
                "min" -> min = nextInt()
                "max" -> max = nextInt()
                else -> skipValue()
            }
        }
        endObject()
        val resolvedMin = min ?: malformed("Recommended rep range is missing min.")
        val resolvedMax = max ?: malformed("Recommended rep range is missing max.")
        if (resolvedMin <= 0 || resolvedMax < resolvedMin || resolvedMax > MAX_REPETITIONS) {
            malformed("Recommended rep range is invalid: $resolvedMin-$resolvedMax")
        }
        return RepRange(resolvedMin, resolvedMax)
    }

    private fun JsonReader.readStringArray(label: String): List<String> {
        val values = mutableListOf<String>()
        beginArray()
        while (hasNext()) {
            if (values.size >= MAX_LIST_ITEMS) malformed("$label has too many values.")
            values += readString(label)
        }
        endArray()
        return values
    }

    private fun JsonReader.readString(label: String, maxLength: Int = MAX_STRING_LENGTH): String {
        val value = nextString().trim()
        if (value.isEmpty() || value.length > maxLength) {
            malformed("$label must contain between 1 and $maxLength characters.")
        }
        return value
    }

    private fun requireHttps(value: String?, label: String): String {
        val url = value ?: malformed("$label is missing.")
        if (!url.startsWith("https://")) malformed("$label must be an HTTPS URL.")
        return url
    }

    private fun requireSafeIdentifier(value: String, label: String) {
        if (!SAFE_IDENTIFIER.matches(value)) malformed("$label is not a safe identifier: $value")
    }

    private fun CatalogSource?.requireValid(): ValidatedCatalogSource {
        val source = this ?: malformed("Catalog is missing source metadata.")
        val repository = source.repository.orEmpty()
        if (!repository.startsWith("https://")) {
            malformed("Catalog source.repository must be an HTTPS URL.")
        }
        val commit = source.commit.orEmpty()
        if (!COMMIT_HASH.matches(commit)) {
            malformed("Catalog source.commit must be a full Git commit hash.")
        }
        val assetLicense = source.assetLicense
        if (assetLicense.isNullOrBlank()) {
            malformed("Catalog source.assetLicense is missing.")
        }
        return ValidatedCatalogSource(
            repository = repository,
            commit = commit,
            assetLicense = assetLicense,
            attribution = source.attribution
                ?: malformed("Catalog source.attribution is missing.")
        )
    }

    private fun VisualSpecification?.requireValid(): ValidatedVisualSpecification {
        val visuals = this ?: malformed("Catalog is missing visuals metadata.")
        if (visuals.frameCount != EXPECTED_FRAME_COUNT) {
            malformed("Catalog visuals.frameCount must be $EXPECTED_FRAME_COUNT.")
        }
        if (!visuals.format.equals("svg", ignoreCase = true)) {
            malformed("Catalog visuals.format must be svg.")
        }
        val width = visuals.widthPx ?: malformed("Catalog visuals.widthPx is missing.")
        val height = visuals.heightPx ?: malformed("Catalog visuals.heightPx is missing.")
        if (width <= 0 || height <= 0 || width > MAX_VISUAL_DIMENSION || height > MAX_VISUAL_DIMENSION) {
            malformed("Catalog visual dimensions must be between 1 and $MAX_VISUAL_DIMENSION pixels.")
        }
        return ValidatedVisualSpecification(
            frameCount = EXPECTED_FRAME_COUNT,
            widthPx = width,
            heightPx = height
        )
    }

    private fun readExerciseType(value: String): ExerciseType = when (value) {
        "weight_reps" -> ExerciseType.WEIGHT_REPS
        "bodyweight_reps" -> ExerciseType.BODYWEIGHT_REPS
        "assisted_bodyweight" -> ExerciseType.ASSISTED_BODYWEIGHT
        "duration" -> ExerciseType.DURATION
        "distance_duration" -> ExerciseType.DISTANCE_DURATION
        else -> malformed("Unknown exerciseType: $value")
    }

    private fun readMovementPattern(value: String): MovementPattern = readEnum("movementPattern", value)
    private fun readDifficulty(value: String): Difficulty = readEnum("difficulty", value)
    private fun readMechanics(value: String): MechanicsType = readEnum("mechanics", value)
    private fun readProgressionType(value: String): ProgressionType = readEnum("progressionType", value)

    private inline fun <reified T : Enum<T>> readEnum(label: String, value: String): T =
        enumValues<T>().firstOrNull { enumValue ->
            enumValue.name.lowercase(Locale.ROOT) == value
        } ?: malformed("Unknown programming $label: $value")

    private data class CatalogSource(
        val repository: String?,
        val commit: String?,
        val assetLicense: String?,
        val attribution: ExerciseAttribution?
    )

    private data class ParsedExercise(
        val id: String,
        val sourceId: String,
        val sourceSlug: String,
        val name: String,
        val searchAliases: List<String>,
        val primaryMuscles: List<String>,
        val secondaryMuscles: List<String>,
        val listedEquipment: List<String>,
        val type: ExerciseType,
        val isStretch: Boolean,
        val programming: ExerciseProgrammingMetadata?
    ) {
        fun toExercise(attribution: ExerciseAttribution): Exercise = Exercise(
            id = id,
            source = ExerciseSource(
                catalogId = CATALOG_ID,
                sourceId = sourceId,
                sourceSlug = sourceSlug,
                attribution = attribution
            ),
            name = name,
            searchAliases = searchAliases,
            primaryMuscles = primaryMuscles,
            secondaryMuscles = secondaryMuscles,
            listedEquipment = listedEquipment,
            type = type,
            isStretch = isStretch,
            programming = programming
        )
    }

    private data class VisualSpecification(
        val frameCount: Int?,
        val widthPx: Int?,
        val heightPx: Int?,
        val format: String?
    )

    private data class ValidatedCatalogSource(
        val repository: String,
        val commit: String,
        val assetLicense: String,
        val attribution: ExerciseAttribution
    )

    private data class ValidatedVisualSpecification(
        val frameCount: Int,
        val widthPx: Int,
        val heightPx: Int
    )

    private companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
        const val CATALOG_ID = "workout-guide"
        const val ASSET_PATH_PREFIX = "workout-guide/assets/"
        const val EXPECTED_FRAME_COUNT = 3
        const val MAX_VISUAL_DIMENSION = 8_192
        const val MAX_EXERCISES = 5_000
        const val MAX_LIST_ITEMS = 100
        const val MAX_STRING_LENGTH = 256
        const val MAX_DESCRIPTION_LENGTH = 2_000
        const val MAX_URL_LENGTH = 2_048
        const val MAX_REPETITIONS = 1_000
        const val MIN_FATIGUE_SCORE = 1
        const val MAX_FATIGUE_SCORE = 5
        val SAFE_IDENTIFIER = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
        val COMMIT_HASH = Regex("[0-9a-fA-F]{40}")

        fun malformed(message: String): Nothing = throw WorkoutGuideCatalogFormatException(message)
    }
}
