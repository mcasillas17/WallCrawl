package wallcrawl.elopenmike.com.core.exercise.workoutguide

import android.util.JsonReader
import android.util.JsonToken
import java.io.FilterReader
import java.io.IOException
import java.io.Reader
import java.util.Locale
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisual
import wallcrawl.elopenmike.com.core.model.ComplexityTier
import wallcrawl.elopenmike.com.core.model.Difficulty
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseAttribution
import wallcrawl.elopenmike.com.core.model.ExerciseAttributionSource
import wallcrawl.elopenmike.com.core.model.ExerciseProgrammingMetadata
import wallcrawl.elopenmike.com.core.model.ExerciseSource
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.ImpactLevel
import wallcrawl.elopenmike.com.core.model.MechanicsType
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
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.SupportRequirement

class WorkoutGuideCatalogFormatException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

/** Converts the import format into WallCrawl-owned domain and visual models. */
class WorkoutGuideCatalogParser {

    fun parse(input: Reader): WorkoutGuideCatalogSnapshot {
        try {
            val reader = JsonReader(BoundedCatalogReader(input, MAX_CATALOG_CHARACTERS))
            var schemaVersion: Int? = null
            var source: CatalogSource? = null
            var visualSpecification: VisualSpecification? = null
            var parsedExercises: List<ParsedExercise>? = null

            reader.beginObject()
            while (reader.hasNext()) {
                val field = reader.nextName()
                when (field) {
                    "schemaVersion" -> schemaVersion = reader.nextInt()
                    "source" -> source = reader.readCatalogSource()
                    "visuals" -> visualSpecification = reader.readVisualSpecification()
                    "exercises" -> parsedExercises = reader.readExercises()
                    else -> reader.skipBoundedValue("catalog.${safeField(field)}")
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
            validateReviewedGraphs(entries)

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
            val field = nextName()
            when (field) {
                "repository" -> repository = readString("source.repository")
                "commit" -> commit = readString("source.commit")
                "assetLicense" -> assetLicense = readString("source.assetLicense")
                "attribution" -> attribution = readAttribution("source.attribution")
                else -> skipBoundedValue("source.${safeField(field)}")
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
            val field = nextName()
            when (field) {
                "frameCount" -> frameCount = nextInt()
                "widthPx" -> widthPx = nextInt()
                "heightPx" -> heightPx = nextInt()
                "format" -> format = readString("visuals.format")
                else -> skipBoundedValue("visuals.${safeField(field)}")
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
            exercises += readExercise(exercises.size)
        }
        endArray()
        return exercises
    }

    private fun JsonReader.readExercise(position: Int): ParsedExercise {
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
        var reviewedMetadata: ReviewedExerciseMetadata? = null
        val seenFields = mutableSetOf<String>()

        beginObject()
        while (hasNext()) {
            val field = nextName()
            requireUniqueField(seenFields, field, "exercise[$position]")
            when (field) {
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
                "reviewedMetadata" -> reviewedMetadata = readReviewedMetadata(
                    if (id == null) {
                        "exercise[$position].reviewedMetadata"
                    } else {
                        "Exercise $id reviewedMetadata"
                    }
                )
                else -> skipBoundedValue("exercise[$position].${safeField(field)}")
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

        val resolvedType = exerciseType ?: malformed("Exercise $exerciseId is missing exerciseType.")
        val resolvedIsStretch = isStretch ?: malformed("Exercise $exerciseId is missing isStretch.")
        reviewedMetadata?.validateForExercise(
            exerciseId = exerciseId,
            type = resolvedType,
            isStretch = resolvedIsStretch,
            representedMuscles = (canonicalPrimary + canonicalSecondary).toSet()
        )

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
            type = resolvedType,
            isStretch = resolvedIsStretch,
            programming = programming,
            reviewedMetadata = reviewedMetadata
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
            val field = nextName()
            when (field) {
                "creator" -> creator = readString("$label.creator")
                "creatorUrl" -> creatorUrl = readString("$label.creatorUrl", MAX_URL_LENGTH)
                "license" -> license = readString("$label.license")
                "licenseUrl" -> licenseUrl = readString("$label.licenseUrl", MAX_URL_LENGTH)
                "source" -> source = readAttributionSource("$label.source")
                else -> skipBoundedValue("$label.${safeField(field)}")
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
            val field = nextName()
            when (field) {
                "name" -> name = readString("$label.name")
                "url" -> url = readString("$label.url", MAX_URL_LENGTH)
                "license" -> license = readString("$label.license")
                "licenseUrl" -> licenseUrl = readString("$label.licenseUrl", MAX_URL_LENGTH)
                "changes" -> changes = readString("$label.changes", MAX_DESCRIPTION_LENGTH)
                else -> skipBoundedValue("$label.${safeField(field)}")
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
            val field = nextName()
            when (field) {
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
                else -> skipBoundedValue("programming.${safeField(field)}")
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

    private fun JsonReader.readReviewedMetadata(label: String): ReviewedExerciseMetadata {
        expectToken(JsonToken.BEGIN_OBJECT, label)
        var reviewState: ReviewState? = null
        var directPrimaryMuscle: String? = null
        var descriptiveSecondaryMuscles: Set<String>? = null
        var movementPattern: MovementPattern? = null
        var complexity: ComplexityTier? = null
        var progressionFamily: String? = null
        var prescriptionShape: PrescriptionShape? = null
        var regressions: List<ReviewedExerciseLink>? = null
        var substitutions: List<ReviewedExerciseLink>? = null
        var capabilityRequirements: Set<MovementCapabilityType>? = null
        var supportRequirement: SupportRequirement? = null
        var impactLevel: ImpactLevel? = null
        var equipmentAlternatives: List<List<String>>? = null
        var provenance: ReviewProvenance? = null
        val seenFields = mutableSetOf<String>()

        beginObject()
        while (hasNext()) {
            val field = nextName()
            requireUniqueField(seenFields, field, label)
            when (field) {
                "reviewState" -> reviewState = readReviewedEnum<ReviewState>("$label.reviewState")
                "directPrimaryMuscle" -> directPrimaryMuscle = readReviewedString(
                    "$label.directPrimaryMuscle",
                    MAX_REVIEWED_MUSCLE_LENGTH
                )
                "descriptiveSecondaryMuscles" -> descriptiveSecondaryMuscles =
                    readReviewedStringSet(
                        "$label.descriptiveSecondaryMuscles",
                        MAX_REVIEWED_SECONDARY_MUSCLES,
                        MAX_REVIEWED_MUSCLE_LENGTH
                    )
                "movementPattern" -> movementPattern =
                    readReviewedEnum<MovementPattern>("$label.movementPattern")
                "complexity" -> complexity =
                    readReviewedEnum<ComplexityTier>("$label.complexity")
                "progressionFamily" -> progressionFamily = readReviewedString(
                    "$label.progressionFamily",
                    MAX_PROGRESSION_FAMILY_LENGTH
                )
                "prescriptionShape" -> prescriptionShape =
                    readReviewedEnum<PrescriptionShape>("$label.prescriptionShape")
                "approvedRegressions" -> regressions = readReviewedLinks(
                    "$label.approvedRegressions"
                )
                "approvedSubstitutions" -> substitutions = readReviewedLinks(
                    "$label.approvedSubstitutions"
                )
                "capabilityRequirements" -> capabilityRequirements =
                    readReviewedEnumSet(
                        "$label.capabilityRequirements",
                        MAX_CAPABILITY_REQUIREMENTS
                    )
                "supportRequirement" -> supportRequirement =
                    readReviewedEnum<SupportRequirement>("$label.supportRequirement")
                "impactLevel" -> impactLevel =
                    readReviewedEnum<ImpactLevel>("$label.impactLevel")
                "equipmentAlternatives" -> equipmentAlternatives =
                    readReviewedEquipmentAlternatives("$label.equipmentAlternatives")
                "provenance" -> provenance = readReviewProvenance("$label.provenance")
                else -> malformed("$label.${safeField(field)} is unknown.")
            }
        }
        endObject()

        val family = progressionFamily
            ?: malformed("$label is missing progressionFamily.")
        if (!SAFE_PROGRESSION_FAMILY.matches(family)) {
            malformed("$label.progressionFamily is not a safe bounded slug.")
        }
        val primary = directPrimaryMuscle
            ?: malformed("$label is missing directPrimaryMuscle.")
        if (primary !in StandardMuscles.TRAINABLE) {
            malformed("$label.directPrimaryMuscle is not a canonical WallCrawl muscle.")
        }
        val secondaries = descriptiveSecondaryMuscles
            ?: malformed("$label is missing descriptiveSecondaryMuscles.")
        if (secondaries.any { it !in StandardMuscles.TRAINABLE }) {
            malformed("$label.descriptiveSecondaryMuscles contains a non-canonical muscle.")
        }
        if (primary in secondaries) {
            malformed("$label.descriptiveSecondaryMuscles duplicates directPrimaryMuscle.")
        }

        return ReviewedExerciseMetadata(
            reviewState = reviewState ?: malformed("$label is missing reviewState."),
            directPrimaryMuscle = primary,
            descriptiveSecondaryMuscles = secondaries,
            movementPattern = movementPattern ?: malformed("$label is missing movementPattern."),
            complexity = complexity ?: malformed("$label is missing complexity."),
            progressionFamily = family,
            prescriptionShape = prescriptionShape
                ?: malformed("$label is missing prescriptionShape."),
            approvedRegressions = regressions
                ?: malformed("$label is missing approvedRegressions."),
            approvedSubstitutions = substitutions
                ?: malformed("$label is missing approvedSubstitutions."),
            capabilityRequirements = capabilityRequirements
                ?: malformed("$label is missing capabilityRequirements."),
            supportRequirement = supportRequirement
                ?: malformed("$label is missing supportRequirement."),
            impactLevel = impactLevel ?: malformed("$label is missing impactLevel."),
            equipmentAlternatives = equipmentAlternatives
                ?: malformed("$label is missing equipmentAlternatives."),
            provenance = provenance ?: malformed("$label is missing provenance.")
        )
    }

    private fun JsonReader.readReviewProvenance(label: String): ReviewProvenance {
        expectToken(JsonToken.BEGIN_OBJECT, label)
        var reviewerRole: String? = null
        var reviewerRolePresent = false
        var rationaleOrSource: String? = null
        var reviewedAtEpochMillis: Long? = null
        var reviewedAtPresent = false
        var schemaVersion: Int? = null
        var policyVersion: Int? = null
        val seenFields = mutableSetOf<String>()

        beginObject()
        while (hasNext()) {
            val field = nextName()
            requireUniqueField(seenFields, field, label)
            when (field) {
                "reviewerRole" -> {
                    reviewerRolePresent = true
                    reviewerRole = readNullableReviewedString(
                        "$label.reviewerRole",
                        MAX_REVIEWER_ROLE_LENGTH
                    )
                }
                "rationaleOrSource" -> rationaleOrSource = readReviewedString(
                    "$label.rationaleOrSource",
                    MAX_PROVENANCE_RATIONALE_LENGTH
                )
                "reviewedAtEpochMillis" -> {
                    reviewedAtPresent = true
                    reviewedAtEpochMillis = readNullableReviewedLong(
                        "$label.reviewedAtEpochMillis",
                        MIN_REVIEWED_AT_EPOCH_MILLIS,
                        MAX_REVIEWED_AT_EPOCH_MILLIS
                    )
                }
                "schemaVersion" -> schemaVersion = readReviewedInt(
                    "$label.schemaVersion",
                    REVIEWED_SCHEMA_VERSION,
                    REVIEWED_SCHEMA_VERSION
                )
                "policyVersion" -> policyVersion = readReviewedInt(
                    "$label.policyVersion",
                    MIN_POLICY_VERSION,
                    MAX_POLICY_VERSION
                )
                else -> malformed("$label.${safeField(field)} is unknown.")
            }
        }
        endObject()

        if (!reviewerRolePresent) malformed("$label is missing reviewerRole.")
        if (!reviewedAtPresent) malformed("$label is missing reviewedAtEpochMillis.")
        return ReviewProvenance(
            reviewerRole = reviewerRole,
            rationaleOrSource = rationaleOrSource
                ?: malformed("$label is missing rationaleOrSource."),
            reviewedAtEpochMillis = reviewedAtEpochMillis,
            schemaVersion = schemaVersion ?: malformed("$label is missing schemaVersion."),
            policyVersion = policyVersion ?: malformed("$label is missing policyVersion.")
        )
    }

    private fun JsonReader.readReviewedLinks(label: String): List<ReviewedExerciseLink> {
        expectToken(JsonToken.BEGIN_ARRAY, label)
        val links = mutableListOf<ReviewedExerciseLink>()
        val targetIds = mutableSetOf<String>()
        beginArray()
        while (hasNext()) {
            if (links.size >= MAX_REVIEWED_LINKS) {
                malformed("$label contains more than $MAX_REVIEWED_LINKS entries.")
            }
            val linkLabel = "$label[${links.size}]"
            expectToken(JsonToken.BEGIN_OBJECT, linkLabel)
            var exerciseId: String? = null
            var rationale: String? = null
            val seenFields = mutableSetOf<String>()
            beginObject()
            while (hasNext()) {
                val field = nextName()
                requireUniqueField(seenFields, field, linkLabel)
                when (field) {
                    "exerciseId" -> exerciseId = readReviewedString(
                        "$linkLabel.exerciseId",
                        MAX_EXERCISE_ID_LENGTH
                    )
                    "rationale" -> rationale = readReviewedString(
                        "$linkLabel.rationale",
                        MAX_LINK_RATIONALE_LENGTH
                    )
                    else -> malformed("$linkLabel.${safeField(field)} is unknown.")
                }
            }
            endObject()
            val targetId = exerciseId ?: malformed("$linkLabel is missing exerciseId.")
            if (!SAFE_IDENTIFIER.matches(targetId)) {
                malformed("$linkLabel.exerciseId is not a safe identifier.")
            }
            if (!targetIds.add(targetId)) {
                malformed("$label contains duplicate edge $targetId.")
            }
            links += ReviewedExerciseLink(targetId, rationale)
        }
        endArray()
        return links
    }

    private fun JsonReader.readReviewedEquipmentAlternatives(label: String): List<List<String>> {
        expectToken(JsonToken.BEGIN_ARRAY, label)
        val combinations = mutableListOf<List<String>>()
        val normalizedCombinations = mutableSetOf<List<String>>()
        beginArray()
        while (hasNext()) {
            if (combinations.size >= MAX_REVIEWED_EQUIPMENT_ALTERNATIVES) {
                malformed(
                    "$label contains more than $MAX_REVIEWED_EQUIPMENT_ALTERNATIVES combinations."
                )
            }
            val combination = readReviewedStringList(
                "$label[${combinations.size}]",
                MAX_REVIEWED_EQUIPMENT_PER_ALTERNATIVE,
                MAX_REVIEWED_EQUIPMENT_LENGTH,
                requireNonEmpty = true
            )
            if (combination.any { it !in StandardEquipment.ALL }) {
                malformed("$label contains an unknown StandardEquipment value.")
            }
            if (!normalizedCombinations.add(combination.sorted())) {
                malformed("$label contains a duplicate equipment combination.")
            }
            combinations += combination
        }
        endArray()
        if (combinations.isEmpty()) malformed("$label must not be empty.")
        return combinations
    }

    private fun JsonReader.readReviewedStringSet(
        label: String,
        maximumItems: Int,
        maximumLength: Int
    ): Set<String> = readReviewedStringList(
        label = label,
        maximumItems = maximumItems,
        maximumLength = maximumLength,
        requireNonEmpty = false
    ).toSet()

    private fun JsonReader.readReviewedStringList(
        label: String,
        maximumItems: Int,
        maximumLength: Int,
        requireNonEmpty: Boolean
    ): List<String> {
        expectToken(JsonToken.BEGIN_ARRAY, label)
        val values = mutableListOf<String>()
        val unique = mutableSetOf<String>()
        beginArray()
        while (hasNext()) {
            if (values.size >= maximumItems) {
                malformed("$label contains more than $maximumItems entries.")
            }
            val value = readReviewedString("$label[${values.size}]", maximumLength)
            if (!unique.add(value)) malformed("$label contains duplicate value.")
            values += value
        }
        endArray()
        if (requireNonEmpty && values.isEmpty()) malformed("$label must not be empty.")
        return values
    }

    private inline fun <reified T : Enum<T>> JsonReader.readReviewedEnumSet(
        label: String,
        maximumItems: Int
    ): Set<T> {
        expectToken(JsonToken.BEGIN_ARRAY, label)
        val values = mutableSetOf<T>()
        beginArray()
        while (hasNext()) {
            if (values.size >= maximumItems) {
                malformed("$label contains more than $maximumItems entries.")
            }
            val value = readReviewedEnum<T>("$label[${values.size}]")
            if (!values.add(value)) malformed("$label contains duplicate value.")
        }
        endArray()
        return values
    }

    private inline fun <reified T : Enum<T>> JsonReader.readReviewedEnum(label: String): T {
        val value = readReviewedString(label, MAX_REVIEWED_ENUM_LENGTH)
        return enumValues<T>().firstOrNull { candidate ->
            candidate.name.lowercase(Locale.ROOT) == value
        } ?: malformed("$label contains an unknown enum value.")
    }

    private fun JsonReader.readNullableReviewedString(label: String, maximumLength: Int): String? {
        if (peek() == JsonToken.NULL) {
            nextNull()
            return null
        }
        return readReviewedString(label, maximumLength)
    }

    private fun JsonReader.readReviewedString(label: String, maximumLength: Int): String {
        expectToken(JsonToken.STRING, label)
        val value = nextString()
        if (
            value.isEmpty() || value != value.trim() || value.length > maximumLength ||
            value.any { character -> character.code < 32 || character.code == 127 }
        ) {
            malformed("$label is not a safe string of 1 to $maximumLength characters.")
        }
        return value
    }

    private fun JsonReader.readNullableReviewedLong(
        label: String,
        minimum: Long,
        maximum: Long
    ): Long? {
        if (peek() == JsonToken.NULL) {
            nextNull()
            return null
        }
        expectToken(JsonToken.NUMBER, label)
        val rawValue = nextString()
        val value = rawValue.takeIf(SAFE_POSITIVE_INTEGER_LITERAL::matches)?.toLongOrNull()
            ?: malformed("$label must use integer JSON notation.")
        if (value !in minimum..maximum) {
            malformed("$label must be between $minimum and $maximum.")
        }
        return value
    }

    private fun JsonReader.readReviewedInt(label: String, minimum: Int, maximum: Int): Int {
        expectToken(JsonToken.NUMBER, label)
        val rawValue = nextString()
        val value = rawValue.takeIf(SAFE_POSITIVE_INTEGER_LITERAL::matches)?.toIntOrNull()
            ?: malformed("$label must use integer JSON notation.")
        if (value !in minimum..maximum) {
            malformed("$label must be between $minimum and $maximum.")
        }
        return value
    }

    private fun JsonReader.expectToken(expected: JsonToken, label: String) {
        if (peek() != expected) malformed("$label has the wrong JSON type.")
    }

    private fun requireUniqueField(seenFields: MutableSet<String>, field: String, label: String) {
        if (!seenFields.add(field)) malformed("$label contains duplicate field ${safeField(field)}.")
    }

    private fun safeField(field: String): String =
        if (SAFE_ERROR_FIELD.matches(field)) field else "<invalid-field>"

    private fun ReviewedExerciseMetadata.validateForExercise(
        exerciseId: String,
        type: ExerciseType,
        isStretch: Boolean,
        representedMuscles: Set<String>
    ) {
        if (directPrimaryMuscle !in representedMuscles) {
            malformed(
                "Exercise $exerciseId reviewedMetadata.directPrimaryMuscle is not represented " +
                    "by the catalog exercise."
            )
        }
        val expectedShape = when (type) {
            ExerciseType.WEIGHT_REPS -> PrescriptionShape.WEIGHT_REPS
            ExerciseType.BODYWEIGHT_REPS -> PrescriptionShape.BODYWEIGHT_REPS
            ExerciseType.ASSISTED_BODYWEIGHT -> PrescriptionShape.ASSISTED_BODYWEIGHT
            ExerciseType.DURATION -> PrescriptionShape.DURATION
            ExerciseType.DISTANCE_DURATION -> null
        }
        if (expectedShape == null || prescriptionShape != expectedShape) {
            malformed(
                "Exercise $exerciseId reviewedMetadata.prescriptionShape does not match exerciseType."
            )
        }
        if (isStretch) {
            malformed("Exercise $exerciseId is a stretch and cannot have reviewedMetadata.")
        }
        if (type == ExerciseType.DURATION && StandardMuscles.CARDIO in representedMuscles) {
            malformed("Exercise $exerciseId is cardio duration work and cannot have reviewedMetadata.")
        }
        if (
            reviewState == ReviewState.APPROVED &&
            (provenance.reviewerRole == null || provenance.reviewedAtEpochMillis == null)
        ) {
            malformed(
                "Exercise $exerciseId approved reviewedMetadata requires explicit human provenance."
            )
        }
        if (
            reviewState == ReviewState.DRAFT &&
            (provenance.reviewerRole != null || provenance.reviewedAtEpochMillis != null)
        ) {
            malformed(
                "Exercise $exerciseId draft provenance requires null reviewerRole and " +
                    "reviewedAtEpochMillis."
            )
        }
    }

    private fun validateReviewedGraphs(entries: List<ParsedExercise>) {
        val entriesById = entries.associateBy(ParsedExercise::id)
        val reviewedEntries = entries.filter { it.reviewedMetadata != null }
        if (reviewedEntries.size > MAX_REVIEWED_EXERCISES) {
            malformed(
                "Catalog contains more than $MAX_REVIEWED_EXERCISES reviewed entries."
            )
        }
        val regressionGraph = mutableMapOf<String, List<String>>()

        reviewedEntries.forEach { entry ->
            val metadata = requireNotNull(entry.reviewedMetadata)
            val regressionTargets = metadata.approvedRegressions.map(ReviewedExerciseLink::exerciseId)
            val substitutionTargets = metadata.approvedSubstitutions.map(ReviewedExerciseLink::exerciseId)
            val duplicateRole = regressionTargets.toSet().intersect(substitutionTargets.toSet()).firstOrNull()
            if (duplicateRole != null) {
                malformed(
                    "Exercise ${entry.id} repeats graph edge $duplicateRole as regression and substitution."
                )
            }
            regressionGraph[entry.id] = regressionTargets

            listOf(
                "approvedRegressions" to metadata.approvedRegressions,
                "approvedSubstitutions" to metadata.approvedSubstitutions
            ).forEach { (field, links) ->
                links.forEach { link ->
                    if (link.exerciseId == entry.id) {
                        malformed("Exercise ${entry.id} reviewedMetadata.$field contains a self-edge.")
                    }
                    val target = entriesById[link.exerciseId]
                        ?: malformed(
                            "Exercise ${entry.id} reviewedMetadata.$field references unknown exercise id."
                        )
                    if (target.reviewedMetadata == null) {
                        malformed(
                            "Exercise ${entry.id} reviewedMetadata.$field target lacks reviewed metadata."
                        )
                    }
                }
            }

            metadata.approvedRegressions.forEach { link ->
                val target = requireNotNull(entriesById.getValue(link.exerciseId).reviewedMetadata)
                if (metadata.movementPattern != target.movementPattern) {
                    malformed(
                        "Exercise ${entry.id} reviewedMetadata.approvedRegressions has incompatible " +
                            "movementPattern."
                    )
                }
                if (!regressionShapesCompatible(metadata.prescriptionShape, target.prescriptionShape)) {
                    malformed(
                        "Exercise ${entry.id} reviewedMetadata.approvedRegressions has incompatible " +
                            "prescriptionShape."
                    )
                }
                if (metadata.directPrimaryMuscle != target.directPrimaryMuscle) {
                    malformed(
                        "Exercise ${entry.id} reviewedMetadata.approvedRegressions changes " +
                            "directPrimaryMuscle."
                    )
                }
                if (
                    COMPLEXITY_DEMAND.getValue(target.complexity) >
                    COMPLEXITY_DEMAND.getValue(metadata.complexity)
                ) {
                    malformed(
                        "Exercise ${entry.id} reviewedMetadata.approvedRegressions is more complex."
                    )
                }
                if (
                    SUPPORT_DEMAND.getValue(target.supportRequirement) >
                    SUPPORT_DEMAND.getValue(metadata.supportRequirement)
                ) {
                    malformed(
                        "Exercise ${entry.id} reviewedMetadata.approvedRegressions requires less support."
                    )
                }
                if (!metadata.capabilityRequirements.containsAll(target.capabilityRequirements)) {
                    malformed(
                        "Exercise ${entry.id} reviewedMetadata.approvedRegressions adds capability " +
                            "requirements."
                    )
                }
                if (
                    metadata.progressionFamily != target.progressionFamily &&
                    link.rationale == null
                ) {
                    malformed(
                        "Exercise ${entry.id} reviewedMetadata.approvedRegressions crosses " +
                            "progressionFamily without rationale."
                    )
                }
            }

            metadata.approvedSubstitutions.forEach { link ->
                val target = requireNotNull(entriesById.getValue(link.exerciseId).reviewedMetadata)
                if (metadata.prescriptionShape != target.prescriptionShape) {
                    malformed(
                        "Exercise ${entry.id} reviewedMetadata.approvedSubstitutions has incompatible " +
                            "prescriptionShape."
                    )
                }
                val changesRole = metadata.movementPattern != target.movementPattern ||
                    metadata.directPrimaryMuscle != target.directPrimaryMuscle
                if (changesRole && link.rationale == null) {
                    malformed(
                        "Exercise ${entry.id} reviewedMetadata.approvedSubstitutions changes movement " +
                            "role without rationale."
                    )
                }
            }
        }

        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        fun visit(exerciseId: String, path: List<String>) {
            if (exerciseId in visiting) {
                val cycleStart = path.indexOf(exerciseId)
                val cycle = path.drop(cycleStart) + exerciseId
                malformed("Reviewed metadata regression cycle detected: ${cycle.joinToString(" -> ")}.")
            }
            if (exerciseId in visited) return
            visiting += exerciseId
            regressionGraph[exerciseId].orEmpty().forEach { targetId ->
                visit(targetId, path + exerciseId)
            }
            visiting -= exerciseId
            visited += exerciseId
        }
        regressionGraph.keys.sorted().forEach { exerciseId -> visit(exerciseId, emptyList()) }
    }

    private fun regressionShapesCompatible(
        source: PrescriptionShape,
        target: PrescriptionShape
    ): Boolean = source == target || (
        source == PrescriptionShape.BODYWEIGHT_REPS &&
            target == PrescriptionShape.ASSISTED_BODYWEIGHT
        )

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
            val field = nextName()
            when (field) {
                "min" -> min = nextInt()
                "max" -> max = nextInt()
                else -> skipBoundedValue("programming.recommendedRepRange.${safeField(field)}")
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

    private fun JsonReader.skipBoundedValue(label: String, depth: Int = 0) {
        if (depth > MAX_JSON_DEPTH) {
            malformed("$label exceeds maximum JSON nesting depth.")
        }
        when (peek()) {
            JsonToken.BEGIN_ARRAY -> {
                beginArray()
                var itemCount = 0
                while (hasNext()) {
                    if (itemCount >= MAX_IGNORED_CONTAINER_ITEMS) {
                        malformed("$label contains too many items.")
                    }
                    skipBoundedValue("$label[$itemCount]", depth + 1)
                    itemCount += 1
                }
                endArray()
            }
            JsonToken.BEGIN_OBJECT -> {
                beginObject()
                var fieldCount = 0
                while (hasNext()) {
                    if (fieldCount >= MAX_IGNORED_CONTAINER_ITEMS) {
                        malformed("$label contains too many fields.")
                    }
                    val field = nextName()
                    if (field.length > MAX_STRING_LENGTH) {
                        malformed("$label contains an oversized field name.")
                    }
                    skipBoundedValue("$label.${safeField(field)}", depth + 1)
                    fieldCount += 1
                }
                endObject()
            }
            JsonToken.STRING -> {
                if (nextString().length > MAX_RAW_JSON_STRING_LENGTH) {
                    malformed("$label exceeds the ignored string limit.")
                }
            }
            JsonToken.NUMBER -> {
                if (nextString().length > MAX_NUMBER_LITERAL_LENGTH) {
                    malformed("$label exceeds the number literal limit.")
                }
            }
            JsonToken.BOOLEAN -> nextBoolean()
            JsonToken.NULL -> nextNull()
            else -> malformed("$label has an invalid JSON shape.")
        }
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
        val programming: ExerciseProgrammingMetadata?,
        val reviewedMetadata: ReviewedExerciseMetadata?
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
            programming = programming,
            reviewedMetadata = reviewedMetadata
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

    private class BoundedCatalogReader(
        input: Reader,
        private val maximumCharacters: Long
    ) : FilterReader(input) {
        private var charactersRead = 0L

        override fun read(): Int {
            val value = super.read()
            if (value != -1) recordCharacters(1)
            return value
        }

        override fun read(buffer: CharArray, offset: Int, length: Int): Int {
            val count = super.read(buffer, offset, length)
            if (count > 0) recordCharacters(count)
            return count
        }

        private fun recordCharacters(count: Int) {
            charactersRead += count
            if (charactersRead > maximumCharacters) {
                malformed(
                    "Workout Guide catalog exceeds the $maximumCharacters-character input limit."
                )
            }
        }
    }

    private companion object {
        const val SUPPORTED_SCHEMA_VERSION = 1
        const val CATALOG_ID = "workout-guide"
        const val ASSET_PATH_PREFIX = "workout-guide/assets/"
        const val EXPECTED_FRAME_COUNT = 3
        const val MAX_VISUAL_DIMENSION = 8_192
        const val MAX_EXERCISES = 5_000
        const val MAX_LIST_ITEMS = 100
        const val MAX_IGNORED_CONTAINER_ITEMS = 1_000
        const val MAX_STRING_LENGTH = 256
        const val MAX_RAW_JSON_STRING_LENGTH = 8_192
        const val MAX_NUMBER_LITERAL_LENGTH = 128
        const val MAX_JSON_DEPTH = 12
        const val MAX_CATALOG_CHARACTERS = 8_000_000L
        const val MAX_DESCRIPTION_LENGTH = 2_000
        const val MAX_URL_LENGTH = 2_048
        const val MAX_REPETITIONS = 1_000
        const val MIN_FATIGUE_SCORE = 1
        const val MAX_FATIGUE_SCORE = 5
        const val MAX_REVIEWED_MUSCLE_LENGTH = 64
        const val MAX_REVIEWED_EXERCISES = 500
        const val MAX_REVIEWED_SECONDARY_MUSCLES = 16
        const val MAX_PROGRESSION_FAMILY_LENGTH = 64
        const val MAX_REVIEWED_LINKS = 24
        const val MAX_EXERCISE_ID_LENGTH = 128
        const val MAX_LINK_RATIONALE_LENGTH = 500
        const val MAX_CAPABILITY_REQUIREMENTS = 7
        const val MAX_REVIEWED_EQUIPMENT_ALTERNATIVES = 20
        const val MAX_REVIEWED_EQUIPMENT_PER_ALTERNATIVE = 20
        const val MAX_REVIEWED_EQUIPMENT_LENGTH = 64
        const val MAX_REVIEWED_ENUM_LENGTH = 64
        const val MAX_REVIEWER_ROLE_LENGTH = 120
        const val MAX_PROVENANCE_RATIONALE_LENGTH = 1_000
        const val MIN_REVIEWED_AT_EPOCH_MILLIS = 1L
        const val MAX_REVIEWED_AT_EPOCH_MILLIS = 253_402_300_799_999L
        const val REVIEWED_SCHEMA_VERSION = 1
        const val MIN_POLICY_VERSION = 1
        const val MAX_POLICY_VERSION = 10_000
        val SAFE_IDENTIFIER = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
        val COMMIT_HASH = Regex("[0-9a-fA-F]{40}")
        val SAFE_PROGRESSION_FAMILY = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
        val SAFE_POSITIVE_INTEGER_LITERAL = Regex("[1-9][0-9]*")
        val SAFE_ERROR_FIELD = Regex("[A-Za-z0-9_.-]{1,80}")
        val COMPLEXITY_DEMAND = mapOf(
            ComplexityTier.FOUNDATIONAL to 0,
            ComplexityTier.STANDARD to 1,
            ComplexityTier.ADVANCED to 2
        )
        val SUPPORT_DEMAND = mapOf(
            SupportRequirement.SUPPORTED to 0,
            SupportRequirement.OPTIONAL_SUPPORT to 1,
            SupportRequirement.UNSUPPORTED to 2
        )

        fun malformed(message: String): Nothing = throw WorkoutGuideCatalogFormatException(message)
    }
}
