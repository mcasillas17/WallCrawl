package wallcrawl.elopenmike.com.core.conditions

import android.util.JsonReader
import android.util.JsonToken
import android.util.MalformedJsonException
import java.io.Reader
import java.util.Locale
import wallcrawl.elopenmike.com.core.io.BoundedCharacterReader
import wallcrawl.elopenmike.com.core.model.AiReviewProvenance
import wallcrawl.elopenmike.com.core.model.TrainingConstraint

class ConditionGuidanceFormatException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

/**
 * Reads the bundled condition overlay. Strict in the same way as the translation parser: an
 * unknown demand, a duplicate key, a missing provenance or an oversized document is an
 * authoring mistake and fails loudly. JSON keys equal the Kotlin property names; a condition's
 * id is its key in `conditions`. Whether exercise ids exist in the catalog is checked elsewhere.
 */
class ConditionGuidanceParser {

    fun parse(input: Reader): ConditionGuidance {
        try {
            val reader = JsonReader(
                BoundedCharacterReader(input, MAX_CHARACTERS) { limit ->
                    malformed("Condition guidance exceeds the $limit-character input limit.")
                }
            )
            var schemaVersion: Int? = null
            var version: String? = null
            var exerciseDemands: Map<String, Set<MovementDemand>>? = null
            var conditions: Map<String, ConditionRecord>? = null
            var jointLabels: Map<TrainingConstraint, Set<MovementDemand>>? = null
            var demandCues: Map<MovementDemand, Map<String, String>>? = null
            val seen = mutableSetOf<String>()

            reader.beginObject()
            while (reader.hasNext()) {
                val field = reader.nextName()
                requireUnique(seen, field, "guidance")
                when (field) {
                    "schemaVersion" -> schemaVersion = reader.readInt("schemaVersion", 1, 1)
                    "version" -> version = reader.readText("version", MAX_SHORT)
                    "exerciseDemands" -> exerciseDemands = reader.readExerciseDemands()
                    "conditions" -> conditions = reader.readConditions()
                    "jointLabelDemands" -> jointLabels = reader.readJointLabels()
                    "demandCues" -> demandCues = reader.readDemandCues()
                    else -> malformed("Unexpected field ${safe(field)} in guidance.")
                }
            }
            reader.endObject()
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                malformed("Unexpected content after the guidance root object.")
            }

            schemaVersion ?: malformed("Guidance is missing its schemaVersion.")
            if (exerciseDemands.isNullOrEmpty()) malformed("Guidance needs a non-empty exerciseDemands.")
            if (conditions.isNullOrEmpty()) malformed("Guidance needs a non-empty conditions.")
            return ConditionGuidance(
                version = version ?: malformed("Guidance is missing its version."),
                exerciseDemands = exerciseDemands,
                conditions = conditions,
                jointLabelDemands = jointLabels ?: malformed("Guidance is missing jointLabelDemands."),
                demandCues = demandCues ?: malformed("Guidance is missing demandCues.")
            )
        } catch (error: ConditionGuidanceFormatException) {
            throw error
        } catch (error: MalformedJsonException) {
            throw ConditionGuidanceFormatException("Condition guidance is not valid JSON.", error)
        } catch (error: IllegalStateException) {
            throw ConditionGuidanceFormatException("Condition guidance is not valid JSON.", error)
        } catch (error: NumberFormatException) {
            throw ConditionGuidanceFormatException("Condition guidance holds an invalid number.", error)
        }
    }

    private fun JsonReader.readExerciseDemands(): Map<String, Set<MovementDemand>> {
        val result = mutableMapOf<String, Set<MovementDemand>>()
        beginObject()
        while (hasNext()) {
            if (result.size >= MAX_EXERCISES) malformed("exerciseDemands has too many entries.")
            val id = readExerciseIdKey("exerciseDemands")
            if (id in result) malformed("exerciseDemands repeats an exercise.")
            // Presence means reviewed, so an empty set is "reviewed, carries no demand".
            result[id] = readDemandSet("exerciseDemands.$id", allowEmpty = true)
        }
        endObject()
        return result
    }

    private fun JsonReader.readJointLabels(): Map<TrainingConstraint, Set<MovementDemand>> {
        val result = mutableMapOf<TrainingConstraint, Set<MovementDemand>>()
        beginObject()
        while (hasNext()) {
            val name = nextName()
            val label = TrainingConstraint.entries.firstOrNull { it.name == name }
                ?: malformed("jointLabelDemands names an unknown label.")
            if (label == TrainingConstraint.LOW_IMPACT_ONLY) {
                malformed("LOW_IMPACT_ONLY is not a joint label and cannot carry demands.")
            }
            if (label in result) malformed("jointLabelDemands repeats a label.")
            result[label] = readDemandSet("jointLabelDemands.$name", allowEmpty = false)
        }
        endObject()
        return result
    }

    private fun JsonReader.readDemandCues(): Map<MovementDemand, Map<String, String>> {
        val result = mutableMapOf<MovementDemand, Map<String, String>>()
        beginObject()
        while (hasNext()) {
            val demand = demandFromName(nextName())
            if (demand in result) malformed("demandCues repeats a demand.")
            result[demand] = readTranslations("demandCues.$demand", MAX_CUE)
        }
        endObject()
        return result
    }

    private fun JsonReader.readConditions(): Map<String, ConditionRecord> {
        val result = mutableMapOf<String, ConditionRecord>()
        beginObject()
        while (hasNext()) {
            if (result.size >= MAX_CONDITIONS) malformed("conditions has too many entries.")
            val id = nextName()
            if (!SAFE_CONDITION_ID.matches(id)) malformed("conditions holds an unsafe id.")
            if (id in result) malformed("conditions repeats $id.")
            result[id] = readCondition(id)
        }
        endObject()
        return result
    }

    private fun JsonReader.readCondition(id: String): ConditionRecord {
        val label = "condition $id"
        var avoid: Set<MovementDemand>? = null
        var modify: Set<MovementDemand>? = null
        var routine: List<RoutineEntry>? = null
        var cues: Map<String, Map<String, String>>? = null
        var notes: Map<String, List<String>>? = null
        var redFlags: Map<String, List<String>>? = null
        var grade: String? = null
        var sources: List<String>? = null
        var provenance: AiReviewProvenance? = null
        val seen = mutableSetOf<String>()

        beginObject()
        while (hasNext()) {
            val field = nextName()
            requireUnique(seen, field, label)
            when (field) {
                "avoidDemands" -> avoid = readDemandSet("$label.avoidDemands", allowEmpty = true)
                "modifyDemands" -> modify = readDemandSet("$label.modifyDemands", allowEmpty = true)
                "routine" -> routine = readRoutine(label)
                "exerciseCues" -> cues = readExerciseCues(label)
                "notes" -> notes = readLanguageLists("$label.notes")
                "redFlags" -> redFlags = readLanguageLists("$label.redFlags")
                "evidenceGrade" -> grade = readText("$label.evidenceGrade", MAX_SHORT)
                "sources" -> sources = readHttpsList("$label.sources")
                "provenance" -> provenance = readProvenance("$label.provenance")
                else -> malformed("Unexpected field ${safe(field)} in $label.")
            }
        }
        endObject()

        val avoidSet = avoid ?: malformed("$label is missing avoidDemands.")
        val modifySet = modify ?: malformed("$label is missing modifyDemands.")
        if (avoidSet.any { it in modifySet }) {
            malformed("$label lists a demand as both avoid and modify.")
        }
        return ConditionRecord(
            id = id,
            avoidDemands = avoidSet,
            modifyDemands = modifySet,
            routine = routine ?: malformed("$label is missing its routine."),
            exerciseCues = cues ?: malformed("$label is missing exerciseCues."),
            notes = notes ?: malformed("$label is missing notes."),
            redFlags = redFlags ?: malformed("$label is missing redFlags."),
            evidenceGrade = grade ?: malformed("$label is missing evidenceGrade."),
            sources = sources ?: malformed("$label is missing sources."),
            provenance = provenance ?: malformed("$label is missing provenance.")
        )
    }

    private fun JsonReader.readRoutine(owner: String): List<RoutineEntry> {
        val entries = mutableListOf<RoutineEntry>()
        beginArray()
        while (hasNext()) {
            if (entries.size >= MAX_ROUTINE) malformed("$owner routine has too many entries.")
            entries += readRoutineEntry("$owner routine entry")
        }
        endArray()
        return entries
    }

    private fun JsonReader.readRoutineEntry(label: String): RoutineEntry {
        var exerciseId: String? = null
        var sets: Int? = null
        var reps: Int? = null
        var duration: Int? = null
        var hold: Int? = null
        var rest: Int? = null
        var floor: Boolean? = null
        val seen = mutableSetOf<String>()

        beginObject()
        while (hasNext()) {
            val field = nextName()
            requireUnique(seen, field, label)
            when (field) {
                "exerciseId" -> exerciseId = readText("$label.exerciseId", MAX_KEY).also {
                    if (!SAFE_EXERCISE_ID.matches(it)) malformed("$label has an unsafe exercise id.")
                }
                "targetSets" -> sets = readInt("$label.targetSets", 1, 10)
                "targetReps" -> reps = readNullableInt("$label.targetReps", 1, 100)
                "targetDurationSeconds" ->
                    duration = readNullableInt("$label.targetDurationSeconds", 1, 600)
                "holdSeconds" -> hold = readNullableInt("$label.holdSeconds", 1, 120)
                "restSeconds" -> rest = readInt("$label.restSeconds", 0, 300)
                "requiresFloorTransition" -> {
                    if (peek() != JsonToken.BOOLEAN) malformed("$label.requiresFloorTransition has the wrong JSON type.")
                    floor = nextBoolean()
                }
                else -> malformed("Unexpected field ${safe(field)} in $label.")
            }
        }
        endObject()

        if ((reps == null) == (duration == null)) {
            malformed("$label needs exactly one of targetReps and targetDurationSeconds.")
        }
        if (hold != null && reps == null) malformed("$label has a hold without reps.")
        return RoutineEntry(
            exerciseId = exerciseId ?: malformed("$label is missing exerciseId."),
            targetSets = sets ?: malformed("$label is missing targetSets."),
            targetReps = reps,
            targetDurationSeconds = duration,
            holdSeconds = hold,
            restSeconds = rest ?: malformed("$label is missing restSeconds."),
            requiresFloorTransition = floor ?: malformed("$label is missing requiresFloorTransition.")
        )
    }

    private fun JsonReader.readExerciseCues(owner: String): Map<String, Map<String, String>> {
        val result = mutableMapOf<String, Map<String, String>>()
        beginObject()
        while (hasNext()) {
            if (result.size >= MAX_EXERCISES) malformed("$owner exerciseCues has too many entries.")
            val id = readExerciseIdKey("$owner exerciseCues")
            if (id in result) malformed("$owner exerciseCues repeats an exercise.")
            result[id] = readTranslations("$owner exerciseCues.$id", MAX_CUE)
        }
        endObject()
        return result
    }

    private fun JsonReader.readLanguageLists(label: String): Map<String, List<String>> {
        val result = mutableMapOf<String, List<String>>()
        beginObject()
        while (hasNext()) {
            val language = readLanguage(label)
            if (language in result) malformed("$label repeats language $language.")
            val items = mutableListOf<String>()
            beginArray()
            while (hasNext()) {
                if (items.size >= MAX_LIST) malformed("$label.$language has too many entries.")
                items += readText("$label.$language", MAX_CUE)
            }
            endArray()
            result[language] = items
        }
        endObject()
        return result
    }

    private fun JsonReader.readTranslations(label: String, maximum: Int): Map<String, String> {
        val result = mutableMapOf<String, String>()
        beginObject()
        while (hasNext()) {
            val language = readLanguage(label)
            if (language in result) malformed("$label repeats language $language.")
            result[language] = readText("$label.$language", maximum)
        }
        endObject()
        if (result.isEmpty()) malformed("$label must have at least one language.")
        return result
    }

    private fun JsonReader.readProvenance(label: String): AiReviewProvenance {
        var model: String? = null
        var reviewedAt: Long? = null
        var contentId: String? = null
        var sha: String? = null
        var references: List<String>? = null
        var rationale: String? = null
        var limitations: String? = null
        var schema: Int? = null
        var policy: Int? = null
        val seen = mutableSetOf<String>()

        beginObject()
        while (hasNext()) {
            val field = nextName()
            requireUnique(seen, field, label)
            when (field) {
                "reviewerModelId" -> model = readText("$label.$field", MAX_SHORT)
                "reviewedAtEpochMillis" -> reviewedAt = readLong("$label.$field")
                "reviewedContentId" -> contentId = readText("$label.$field", MAX_KEY)
                "reviewedContentSha256" -> sha = readText("$label.$field", 64).also {
                    if (!SHA256_HEX.matches(it)) malformed("$label.$field is not a SHA-256 digest.")
                }
                "sourceReferences" -> references = readHttpsList("$label.$field")
                "decisionRationale" -> rationale = readText("$label.$field", MAX_CUE)
                "limitations" -> limitations = readText("$label.$field", MAX_CUE)
                "schemaVersion" -> schema = readInt("$label.$field", 1, 1_000)
                "policyVersion" -> policy = readInt("$label.$field", 1, 1_000)
                else -> malformed("Unexpected field ${safe(field)} in $label.")
            }
        }
        endObject()
        return AiReviewProvenance(
            reviewerModelId = model ?: malformed("$label is missing reviewerModelId."),
            reviewedAtEpochMillis = reviewedAt ?: malformed("$label is missing reviewedAtEpochMillis."),
            reviewedContentId = contentId ?: malformed("$label is missing reviewedContentId."),
            reviewedContentSha256 = sha ?: malformed("$label is missing reviewedContentSha256."),
            sourceReferences = references ?: malformed("$label is missing sourceReferences."),
            decisionRationale = rationale ?: malformed("$label is missing decisionRationale."),
            limitations = limitations ?: malformed("$label is missing limitations."),
            schemaVersion = schema ?: malformed("$label is missing schemaVersion."),
            policyVersion = policy ?: malformed("$label is missing policyVersion.")
        )
    }

    private fun JsonReader.readDemandSet(label: String, allowEmpty: Boolean): Set<MovementDemand> {
        val demands = linkedSetOf<MovementDemand>()
        beginArray()
        while (hasNext()) {
            val demand = demandFromName(readText(label, MAX_KEY))
            if (!demands.add(demand)) malformed("$label repeats a demand.")
        }
        endArray()
        if (!allowEmpty && demands.isEmpty()) malformed("$label must not be empty.")
        return demands
    }

    private fun demandFromName(name: String): MovementDemand =
        MovementDemand.entries.firstOrNull { it.name == name }
            ?: malformed("Unknown movement demand ${safe(name)}.")

    private fun JsonReader.readHttpsList(label: String): List<String> {
        val items = mutableListOf<String>()
        beginArray()
        while (hasNext()) {
            if (items.size >= MAX_LIST) malformed("$label has too many entries.")
            val value = readText(label, MAX_URL)
            if (!value.startsWith("https://")) malformed("$label must hold https references.")
            items += value
        }
        endArray()
        if (items.isEmpty()) malformed("$label must not be empty.")
        return items
    }

    private fun JsonReader.readExerciseIdKey(label: String): String {
        val id = nextName()
        if (!SAFE_EXERCISE_ID.matches(id) || id.length > MAX_KEY) {
            malformed("$label holds an unsafe exercise id.")
        }
        return id
    }

    private fun JsonReader.readLanguage(label: String): String {
        val tag = nextName()
        if (!SAFE_LANGUAGE_TAG.matches(tag)) malformed("$label names an invalid language tag.")
        return tag.lowercase(Locale.ROOT)
    }

    private fun JsonReader.readText(label: String, maximum: Int): String {
        if (peek() != JsonToken.STRING) malformed("$label has the wrong JSON type.")
        val value = nextString()
        if (value.isBlank()) malformed("$label must not be blank.")
        if (value.length > maximum) malformed("$label exceeds $maximum characters.")
        return value.trim()
    }

    private fun JsonReader.readNullableInt(label: String, minimum: Int, maximum: Int): Int? {
        if (peek() == JsonToken.NULL) {
            nextNull()
            return null
        }
        return readInt(label, minimum, maximum)
    }

    private fun JsonReader.readInt(label: String, minimum: Int, maximum: Int): Int {
        if (peek() != JsonToken.NUMBER) malformed("$label has the wrong JSON type.")
        val value = nextString().takeIf(SAFE_INTEGER::matches)?.toIntOrNull()
            ?: malformed("$label must use integer JSON notation.")
        if (value !in minimum..maximum) malformed("$label must be between $minimum and $maximum.")
        return value
    }

    private fun JsonReader.readLong(label: String): Long {
        if (peek() != JsonToken.NUMBER) malformed("$label has the wrong JSON type.")
        val value = nextString().takeIf(SAFE_INTEGER::matches)?.toLongOrNull()
            ?: malformed("$label must use integer JSON notation.")
        if (value <= 0L) malformed("$label must be positive.")
        return value
    }

    private fun requireUnique(seen: MutableSet<String>, field: String, label: String) {
        if (!seen.add(field)) malformed("$label contains duplicate field ${safe(field)}.")
    }

    private fun safe(field: String): String =
        if (SAFE_ERROR_FIELD.matches(field)) field else "<invalid>"

    private fun malformed(message: String): Nothing = throw ConditionGuidanceFormatException(message)

    private companion object {
        const val MAX_CHARACTERS = 2_000_000L
        const val MAX_EXERCISES = 5_000
        const val MAX_CONDITIONS = 64
        const val MAX_ROUTINE = 12
        const val MAX_LIST = 32
        const val MAX_KEY = 120
        const val MAX_SHORT = 120
        const val MAX_CUE = 1_000
        const val MAX_URL = 500

        val SAFE_EXERCISE_ID = Regex("[a-z0-9]+(?:-[a-z0-9]+)*")
        val SAFE_CONDITION_ID = Regex("[a-z0-9]+(?:_[a-z0-9]+)*")
        val SAFE_LANGUAGE_TAG = Regex("[A-Za-z]{2,3}")
        val SAFE_ERROR_FIELD = Regex("[A-Za-z0-9_.-]{1,64}")
        val SAFE_INTEGER = Regex("-?\\d{1,18}")
        val SHA256_HEX = Regex("[0-9a-f]{64}")
    }
}
