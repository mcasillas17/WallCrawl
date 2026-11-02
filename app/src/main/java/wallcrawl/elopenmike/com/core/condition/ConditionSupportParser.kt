package wallcrawl.elopenmike.com.core.condition

import android.util.JsonReader
import android.util.JsonToken
import java.io.IOException
import java.io.Reader
import java.math.BigDecimal
import wallcrawl.elopenmike.com.core.io.BoundedCharacterReader
import wallcrawl.elopenmike.com.core.model.*

class ConditionSupportFormatException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/** Reads released support only. Unknown fields, cases, units or review bindings fail closed. */
class ConditionSupportParser(private val exerciseIdentity: (String) -> ConditionExerciseIdentity?) {
    constructor(exerciseIdentities: Map<String, ConditionExerciseIdentity> = emptyMap()) : this(exerciseIdentities.toMap()::get)

    fun parse(input: Reader): ConditionSupportCatalog = try {
        val reader = JsonReader(BoundedCharacterReader(input, MAX_CHARACTERS) { fail("Condition support exceeds the input limit.") })
        val root = readValue(reader, 0).obj()
        require(reader.peek() == JsonToken.END_DOCUMENT) { "Unexpected trailing condition support content." }
        root.fields("schemaVersion", "contentVersion", "records")
        require(root.int("schemaVersion") == 1) { "Unknown condition support schema." }
        val records = root.list("records").map { readRecord(it.obj()) }
        require(records.map { it.conditionId }.distinct().size == records.size) { "Duplicate condition ID." }
        ConditionSupportCatalog(root.string("contentVersion"), conditionMap(records.associateBy { it.conditionId }))
    } catch (error: ConditionSupportFormatException) {
        throw error
    } catch (error: IOException) {
        throw ConditionSupportFormatException("Unable to read condition support JSON.", error)
    } catch (error: IllegalArgumentException) {
        throw ConditionSupportFormatException("Invalid condition support: ${error.message}", error)
    } catch (error: IllegalStateException) {
        throw ConditionSupportFormatException("Malformed condition support JSON.", error)
    }

    private fun readRecord(record: Obj): ConditionSupportRecord {
        record.fields("content", "review")
        val content = record.obj("content")
        content.fields("conditionId", "contentVersion", "contextSchemaVersion", "policyVersion", "population", "label", "instructions", "inputs", "cases", "sources", "responsePolicy")
        val review = record.obj("review")
        review.fields("state", "reviewerIdentity", "reviewerQualification", "reviewedAtEpochMillis", "releaseAuthorization", "reviewedContentSha256", "sourceIds")
        return ConditionSupportRecord(
            conditionId = content.string("conditionId"), contentVersion = content.string("contentVersion"),
            contextSchemaVersion = content.int("contextSchemaVersion"), policyVersion = content.int("policyVersion"),
            population = enumValueOf(content.string("population")), label = text(content.obj("label")),
            instructions = text(content.obj("instructions")), inputs = inputs(content.list("inputs")),
            cases = conditionList(content.list("cases").map { value ->
                val case = value.obj(); case.fields("id", "requiredAnswers", "rules")
                ConditionApplicabilityCase(case.string("id"), answers(case.obj("requiredAnswers")), conditionList(case.list("rules").map { rule(it.obj()) }))
            }),
            sources = conditionList(content.list("sources").map { value ->
                val source = value.obj(); source.fields("id", "url", "contentSha256")
                ConditionSource(source.string("id"), source.string("url"), source.string("contentSha256"))
            }),
            responsePolicy = response(content.obj("responsePolicy")), contentSha256 = conditionDigest(content.values),
            review = ConditionReleaseReview(enumValueOf(review.string("state")), review.string("reviewerIdentity"), review.string("reviewerQualification"),
                review.long("reviewedAtEpochMillis"), review.string("releaseAuthorization"), review.string("reviewedContentSha256"), review.strings("sourceIds"))
        )
    }

    private fun rule(rule: Obj): ConditionRule {
        rule.fields("id", "exerciseIdentity", "effect", "executionIdentity", "instructions", "sourceIds", "bounds", "allowOrdinaryProgression", "supportivePrescription", "schedule", "supportiveRequirements")
        val identity = rule.obj("exerciseIdentity")
        identity.fields("exerciseId", "contentSha256", "exerciseType", "artworkSha256")
        val declared = ConditionExerciseIdentity(identity.string("exerciseId"), identity.string("contentSha256"), enumValueOf(identity.string("exerciseType")), identity.string("artworkSha256"))
        require(exerciseIdentity(declared.exerciseId) == declared) { "Unknown or changed exercise content: ${declared.exerciseId}." }
        return ConditionRule(rule.string("id"), declared, enumValueOf(rule.string("effect")), rule.string("executionIdentity"), text(rule.obj("instructions")),
            rule.strings("sourceIds"), rule.optionalObj("bounds")?.let(::bounds), rule.boolean("allowOrdinaryProgression"),
            rule.optionalObj("supportivePrescription")?.let(::prescription), rule.optionalObj("schedule")?.let(::schedule),
            rule.optionalObj("supportiveRequirements")?.let(::supportiveRequirements))
    }

    private fun supportiveRequirements(value: Obj): ConditionSupportiveRequirements {
        value.fields("capabilities", "clearedConstraints", "impact", "equipmentAlternatives")
        val capabilities = value.strings("capabilities").map { enumValueOf<MovementCapabilityType>(it) }.toSet()
        val constraints = value.strings("clearedConstraints").map { enumValueOf<TrainingConstraint>(it) }.toSet()
        val equipment = value.list("equipmentAlternatives").map { combination ->
            (combination as? List<*>)?.map { it as? String ?: error("Invalid equipment token.") }
                ?: error("Invalid equipment combination.")
        }
        return ConditionSupportiveRequirements(capabilities, constraints, enumValueOf(value.string("impact")), equipment)
    }

    private fun bounds(bounds: Obj): ConditionPrescriptionBounds {
        bounds.fields("minSets", "maxSets", "minReps", "maxReps", "minLoad", "maxLoad", "minAssistance", "maxAssistance", "loadUnit", "minDurationSeconds", "maxDurationSeconds", "minDistanceMeters", "maxDistanceMeters", "minRestSeconds", "maxRestSeconds")
        val loadNames = listOf("minLoad", "maxLoad", "minAssistance", "maxAssistance")
        val hasLoads = loadNames.any { bounds.values[it] != null }
        require(hasLoads == (bounds.values["loadUnit"] != null)) { "Authored loads require an explicit unit, only with loads." }
        val unit = if (hasLoads) enumValueOf<WeightUnit>(bounds.string("loadUnit")) else null
        fun load(key: String) = bounds.decimalOrNull(key)?.let { convertWeight(it, requireNotNull(unit), WeightUnit.KG) }
        return ConditionPrescriptionBounds(bounds.intOrNull("minSets"), bounds.intOrNull("maxSets"), bounds.intOrNull("minReps"), bounds.intOrNull("maxReps"),
            load("minLoad"), load("maxLoad"), load("minAssistance"), load("maxAssistance"), bounds.intOrNull("minDurationSeconds"), bounds.intOrNull("maxDurationSeconds"),
            bounds.decimalOrNull("minDistanceMeters"), bounds.decimalOrNull("maxDistanceMeters"), bounds.intOrNull("minRestSeconds"), bounds.intOrNull("maxRestSeconds"))
    }

    private fun prescription(prescription: Obj): ExercisePrescription {
        prescription.fields("exerciseType", "targetSets", "repMin", "repMax", "targetWeight", "targetAssistanceWeight", "loadUnit", "targetDurationSeconds", "targetDistanceMeters", "restSeconds", "purpose")
        require(prescription.string("purpose") == PrescriptionPurpose.SUPPORTIVE.name) { "Supportive prescription must declare its purpose." }
        val hasLoads = prescription.values["targetWeight"] != null || prescription.values["targetAssistanceWeight"] != null
        require(hasLoads == (prescription.values["loadUnit"] != null))
        val unit = if (hasLoads) enumValueOf<WeightUnit>(prescription.string("loadUnit")) else null
        fun load(key: String) = prescription.decimalOrNull(key)?.let { convertWeight(it, requireNotNull(unit), WeightUnit.KG).also { kg -> require(kg <= WorkoutTemplate.maxLoad(WeightUnit.KG)) } }
        val min = prescription.intOrNull("repMin"); val max = prescription.intOrNull("repMax")
        require((min == null) == (max == null))
        return ExercisePrescription(exerciseType = enumValueOf(prescription.string("exerciseType")), targetSets = prescription.int("targetSets"),
            repRange = min?.let { RepRange(it, requireNotNull(max)) }, targetWeight = load("targetWeight"), targetAssistanceWeight = load("targetAssistanceWeight"),
            targetDurationSeconds = prescription.intOrNull("targetDurationSeconds"), targetDistanceMeters = prescription.decimalOrNull("targetDistanceMeters"),
            restSeconds = prescription.int("restSeconds"), purpose = PrescriptionPurpose.SUPPORTIVE)
    }

    private fun schedule(schedule: Obj): ConditionSchedule {
        schedule.fields("sessionsPerWeek", "minimumHoursBetweenSessions", "instructions")
        return ConditionSchedule(schedule.int("sessionsPerWeek"), schedule.int("minimumHoursBetweenSessions"), text(schedule.obj("instructions")))
    }
    private fun response(response: Obj): ConditionResponsePolicy {
        response.fields("version", "inputs", "stopWhenAnswers", "reassessmentRequiredAnswers", "escalation")
        return ConditionResponsePolicy(response.int("version"), inputs(response.list("inputs")), answers(response.obj("stopWhenAnswers")), answers(response.obj("reassessmentRequiredAnswers")), text(response.obj("escalation")))
    }
    private fun inputs(values: List<Any?>): List<ConditionInputDefinition> = conditionList(values.map { value ->
        val input = value.obj(); input.fields("id", "prompt", "required", "choices")
        ConditionInputDefinition(input.string("id"), text(input.obj("prompt")), input.boolean("required"), conditionMap(input.obj("choices").values.mapValues { text(it.value.obj()) }))
    })
    private fun answers(obj: Obj): Map<String, String> = conditionMap(obj.values.mapValues { (_, value) -> value as? String ?: fail("Answer must be an exact string token.") })
    private fun text(obj: Obj): ConditionText { obj.fields("en", "es"); return ConditionText(obj.string("en"), obj.string("es")) }

    /** A bounded wire tree permits canonical digesting before any clinical field is converted. */
    private fun readValue(reader: JsonReader, depth: Int): Any? {
        require(depth <= 16) { "Condition support nesting limit exceeded." }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                val result = linkedMapOf<String, Any?>(); reader.beginObject()
                while (reader.hasNext()) {
                    require(result.size < 128) { "Condition support object limit exceeded." }
                    val name = reader.nextName(); require(name.length <= 128 && !result.containsKey(name)) { "Duplicate or oversized condition support field." }
                    result[name] = readValue(reader, depth + 1)
                }; reader.endObject(); conditionMap(result)
            }
            JsonToken.BEGIN_ARRAY -> {
                val result = mutableListOf<Any?>(); reader.beginArray()
                while (reader.hasNext()) { require(result.size < 512) { "Condition support array limit exceeded." }; result += readValue(reader, depth + 1) }
                reader.endArray(); conditionList(result)
            }
            JsonToken.STRING -> reader.nextString().also { require(it.length <= 4_096) { "Condition support string limit exceeded." } }
            JsonToken.NUMBER -> JsonNumber(reader.nextString().also { require(it.length <= 64) })
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> { reader.nextNull(); null }
            else -> fail("Unexpected condition support token.")
        }
    }

    private class JsonNumber(val raw: String) : Number() {
        override fun toByte() = raw.toByte(); override fun toShort() = raw.toShort()
        override fun toInt() = raw.toInt(); override fun toLong() = raw.toLong()
        override fun toFloat() = raw.toFloat(); override fun toDouble() = raw.toDouble()
        override fun toString(): String = raw
        init { require(BigDecimal(raw).precision() <= 64 && BigDecimal(raw).scale() in -64..64) { "Condition number exceeds precision limit." } }
    }
    private class Obj(val values: Map<String, Any?>) {
        fun fields(vararg allowed: String) { require(values.keys.all { it in allowed }) { "Unknown condition support field." } }
        fun string(key: String): String = values[key] as? String ?: fail("Missing or invalid string: $key.")
        fun boolean(key: String): Boolean = values[key] as? Boolean ?: fail("Missing or invalid boolean: $key.")
        fun long(key: String): Long = (values[key] as? JsonNumber)?.raw?.toLongOrNull() ?: fail("Missing or invalid integer: $key.")
        fun int(key: String): Int = (values[key] as? JsonNumber)?.raw?.toIntOrNull() ?: fail("Missing or invalid integer: $key.")
        fun intOrNull(key: String): Int? = if (values[key] == null) null else int(key)
        fun decimalOrNull(key: String): Double? = if (values[key] == null) null else ((values[key] as? JsonNumber)?.toDouble() ?: fail("Invalid decimal: $key.")).also { require(it.isFinite()) }
        fun obj(key: String): Obj = values[key].obj()
        fun optionalObj(key: String): Obj? = values[key]?.obj()
        @Suppress("UNCHECKED_CAST") fun list(key: String): List<Any?> = values[key] as? List<Any?> ?: fail("Missing array: $key.")
        fun strings(key: String): Set<String> {
            val entries = list(key).map { it as? String ?: fail("Expected string in $key.") }
            require(entries.distinct().size == entries.size) { "Duplicate ID in $key." }
            return conditionSet(entries.toSet())
        }
    }
    private companion object {
        const val MAX_CHARACTERS = 1_048_576L
        fun fail(message: String): Nothing = throw ConditionSupportFormatException(message)
        @Suppress("UNCHECKED_CAST") fun Any?.obj(): Obj = Obj(this as? Map<String, Any?> ?: fail("Expected condition support object."))
    }
}
