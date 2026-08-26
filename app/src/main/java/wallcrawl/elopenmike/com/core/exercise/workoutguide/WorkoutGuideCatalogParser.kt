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
            var parsedExercises: List<ParsedExercise>? = null

            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextName()) {
                    "schemaVersion" -> schemaVersion = reader.nextInt()
                    "source" -> source = reader.readCatalogSource()
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
            source.requireValid()
            val entries = parsedExercises
                ?: malformed("Catalog is missing its exercises array.")
            if (entries.isEmpty()) malformed("Catalog must contain at least one exercise.")

            val duplicateId = entries.groupingBy { it.exercise.id }.eachCount()
                .entries.firstOrNull { it.value > 1 }?.key
            if (duplicateId != null) malformed("Duplicate exercise id: $duplicateId")
            val duplicateSourceId = entries.groupingBy { it.exercise.source?.sourceId }.eachCount()
                .entries.firstOrNull { it.value > 1 }?.key
            if (duplicateSourceId != null) malformed("Duplicate source exercise id: $duplicateSourceId")
            val duplicateSlug = entries.groupingBy { it.exercise.source?.sourceSlug }.eachCount()
                .entries.firstOrNull { it.value > 1 }?.key
            if (duplicateSlug != null) malformed("Duplicate source exercise slug: $duplicateSlug")
            val knownExerciseIds = entries.mapTo(mutableSetOf()) { it.exercise.id }
            entries.forEach { entry ->
                entry.exercise.programming?.alternativeExerciseIds.orEmpty().forEach { alternativeId ->
                    if (alternativeId !in knownExerciseIds) {
                        malformed(
                            "Exercise ${entry.exercise.id} references unknown alternative: $alternativeId"
                        )
                    }
                }
            }

            return WorkoutGuideCatalogSnapshot(
                exercises = entries.map(ParsedExercise::exercise),
                framesByExerciseId = entries.associate { entry ->
                    entry.exercise.id to entry.frames.sortedBy(ExerciseVisualWithIndex::index)
                        .map(ExerciseVisualWithIndex::visual)
                }
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
        beginObject()
        while (hasNext()) {
            when (nextName()) {
                "repository" -> repository = readString("source.repository")
                "commit" -> commit = readString("source.commit")
                "assetLicense" -> assetLicense = readString("source.assetLicense")
                else -> skipValue()
            }
        }
        endObject()
        return CatalogSource(repository, commit, assetLicense)
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
        var attribution: ExerciseAttribution? = null
        var frames: List<ExerciseVisualWithIndex>? = null
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
                "attribution" -> attribution = readAttribution("exercise.attribution")
                "frames" -> frames = readFrames()
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
        val exerciseAttribution = attribution
            ?: malformed("Exercise $exerciseId is missing attribution.")
        val exerciseFrames = frames ?: malformed("Exercise $exerciseId is missing frames.")
        requireFrameSet(exerciseId, upstreamSlug, exerciseFrames)

        val primary = primaryMuscles
            ?: malformed("Exercise $exerciseId is missing primaryMuscles.")
        if (primary.isEmpty()) malformed("Exercise $exerciseId must have a primary muscle.")

        return ParsedExercise(
            exercise = Exercise(
                id = exerciseId,
                source = ExerciseSource(
                    catalogId = CATALOG_ID,
                    sourceId = upstreamId,
                    sourceSlug = upstreamSlug,
                    attribution = exerciseAttribution
                ),
                name = name ?: malformed("Exercise $exerciseId is missing name."),
                searchAliases = aliases ?: malformed("Exercise $exerciseId is missing searchAliases."),
                primaryMuscles = primary,
                secondaryMuscles = secondaryMuscles
                    ?: malformed("Exercise $exerciseId is missing secondaryMuscles."),
                listedEquipment = listedEquipment
                    ?: malformed("Exercise $exerciseId is missing listedEquipment."),
                type = exerciseType ?: malformed("Exercise $exerciseId is missing exerciseType."),
                isStretch = isStretch ?: malformed("Exercise $exerciseId is missing isStretch."),
                programming = programming
            ),
            frames = exerciseFrames
        )
    }

    private fun JsonReader.readFrames(): List<ExerciseVisualWithIndex> {
        val frames = mutableListOf<ExerciseVisualWithIndex>()
        beginArray()
        while (hasNext()) {
            if (frames.size >= EXPECTED_FRAME_COUNT) {
                malformed("An exercise has more than $EXPECTED_FRAME_COUNT visual frames.")
            }
            frames += readFrame()
        }
        endArray()
        return frames
    }

    private fun JsonReader.readFrame(): ExerciseVisualWithIndex {
        var index: Int? = null
        var assetPath: String? = null
        var format: String? = null
        var widthPx: Int? = null
        var heightPx: Int? = null
        var attribution: ExerciseAttribution? = null
        beginObject()
        while (hasNext()) {
            when (nextName()) {
                "index" -> index = nextInt()
                "assetPath" -> assetPath = readString("frame.assetPath", MAX_PATH_LENGTH)
                "format" -> format = readString("frame.format")
                "widthPx" -> widthPx = nextInt()
                "heightPx" -> heightPx = nextInt()
                "attribution" -> attribution = readAttribution("frame.attribution")
                else -> skipValue()
            }
        }
        endObject()

        val resolvedIndex = index ?: malformed("Visual frame is missing index.")
        val resolvedPath = assetPath ?: malformed("Visual frame is missing assetPath.")
        if (!resolvedPath.startsWith(ASSET_PATH_PREFIX) ||
            resolvedPath.contains("..") ||
            resolvedPath.startsWith('/') ||
            !resolvedPath.endsWith(".svg", ignoreCase = true)
        ) {
            malformed("Unsafe or unsupported visual asset path: $resolvedPath")
        }
        if (!format.equals("svg", ignoreCase = true)) {
            malformed("Visual frame $resolvedPath must use SVG format.")
        }
        val resolvedWidth = widthPx ?: malformed("Visual frame $resolvedPath is missing widthPx.")
        val resolvedHeight = heightPx ?: malformed("Visual frame $resolvedPath is missing heightPx.")
        if (resolvedWidth <= 0 || resolvedHeight <= 0) {
            malformed("Visual frame $resolvedPath has invalid dimensions.")
        }

        return ExerciseVisualWithIndex(
            index = resolvedIndex,
            visual = ExerciseVisual(
                assetPath = resolvedPath,
                widthPx = resolvedWidth,
                heightPx = resolvedHeight,
                attribution = attribution
                    ?: malformed("Visual frame $resolvedPath is missing attribution.")
            )
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
            creatorUrl = creatorUrl ?: malformed("$label is missing creatorUrl."),
            license = license ?: malformed("$label is missing license."),
            licenseUrl = licenseUrl ?: malformed("$label is missing licenseUrl."),
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

    private fun requireFrameSet(
        exerciseId: String,
        sourceSlug: String,
        frames: List<ExerciseVisualWithIndex>
    ) {
        if (frames.size != EXPECTED_FRAME_COUNT ||
            frames.map(ExerciseVisualWithIndex::index).toSet() != EXPECTED_FRAME_INDICES
        ) {
            malformed("Exercise $exerciseId must have visual frame indices 1, 2, and 3.")
        }
        if (frames.map { it.visual.assetPath }.toSet().size != EXPECTED_FRAME_COUNT) {
            malformed("Exercise $exerciseId has duplicate visual asset paths.")
        }
        frames.forEach { frame ->
            val expectedPath = "$ASSET_PATH_PREFIX$sourceSlug/frame-${frame.index}.svg"
            if (frame.visual.assetPath != expectedPath) {
                malformed(
                    "Exercise $exerciseId frame ${frame.index} must resolve to $expectedPath."
                )
            }
        }
    }

    private fun requireSafeIdentifier(value: String, label: String) {
        if (!SAFE_IDENTIFIER.matches(value)) malformed("$label is not a safe identifier: $value")
    }

    private fun CatalogSource?.requireValid() {
        val source = this ?: malformed("Catalog is missing source metadata.")
        if (!source.repository.orEmpty().startsWith("https://")) {
            malformed("Catalog source.repository must be an HTTPS URL.")
        }
        if (!COMMIT_HASH.matches(source.commit.orEmpty())) {
            malformed("Catalog source.commit must be a full Git commit hash.")
        }
        if (source.assetLicense.isNullOrBlank()) {
            malformed("Catalog source.assetLicense is missing.")
        }
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
        val assetLicense: String?
    )

    private data class ParsedExercise(
        val exercise: Exercise,
        val frames: List<ExerciseVisualWithIndex>
    )

    private data class ExerciseVisualWithIndex(
        val index: Int,
        val visual: ExerciseVisual
    )

    private companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
        const val CATALOG_ID = "workout-guide"
        const val ASSET_PATH_PREFIX = "workout-guide/assets/"
        const val EXPECTED_FRAME_COUNT = 3
        const val MAX_EXERCISES = 5_000
        const val MAX_LIST_ITEMS = 100
        const val MAX_STRING_LENGTH = 256
        const val MAX_DESCRIPTION_LENGTH = 2_000
        const val MAX_URL_LENGTH = 2_048
        const val MAX_PATH_LENGTH = 1_024
        const val MAX_REPETITIONS = 1_000
        const val MIN_FATIGUE_SCORE = 1
        const val MAX_FATIGUE_SCORE = 5
        val EXPECTED_FRAME_INDICES = setOf(1, 2, 3)
        val SAFE_IDENTIFIER = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
        val COMMIT_HASH = Regex("[0-9a-fA-F]{40}")

        fun malformed(message: String): Nothing = throw WorkoutGuideCatalogFormatException(message)
    }
}
