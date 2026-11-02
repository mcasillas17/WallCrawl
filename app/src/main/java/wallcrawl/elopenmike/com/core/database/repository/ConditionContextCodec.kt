package wallcrawl.elopenmike.com.core.database.repository

import android.util.JsonReader
import android.util.JsonToken
import java.io.StringReader
import wallcrawl.elopenmike.com.core.io.BoundedCharacterReader
import wallcrawl.elopenmike.com.core.model.*

/** Versioned, bounded storage for selected facts and frozen session evidence. */
object ConditionContextCodec {
    fun encodeSelections(values: List<SelectedCondition>): String {
        require(values.size <= 32 && values.map { it.conditionId }.distinct().size == values.size)
        return conditionCanonicalJson(values.map(::selection))
    }

    fun decodeSelections(json: String): List<SelectedCondition> {
        if (json == "[]") return emptyList()
        val values = parse(json).array().map { item ->
            val row = item.obj("conditionId", "contextSchemaVersion", "answers")
            SelectedCondition(row.string("conditionId"), row.int("contextSchemaVersion"),
                row.field("answers").obj().mapValues { (_, value) -> value?.string() })
        }
        require(values.size <= 32 && values.map { it.conditionId }.distinct().size == values.size)
        require(json == encodeSelections(values)) { "Noncanonical condition selections." }
        return values
    }

    fun encodeSession(value: SessionConditionContext): String = conditionCanonicalJson(mapOf(
        "formatVersion" to value.formatVersion,
        "policyVersion" to value.policyVersion,
        "selectedConditions" to value.selectedConditions.map(::selection),
        "sourceSupportVersions" to value.sourceSupportVersions,
        "appliedRules" to value.appliedRules.map { rule -> mapOf(
            "reference" to reference(rule.reference),
            "exerciseIdentity" to identity(rule.exerciseIdentity),
            "executionIdentity" to rule.executionIdentity,
            "purpose" to rule.purpose.name,
            "explanation" to mapOf("en" to rule.explanation.en, "es" to rule.explanation.es)
        ) },
        "consumedScheduleEvidence" to value.consumedScheduleEvidence.map { evidence -> mapOf(
            "reference" to reference(evidence.reference),
            "previousPerformedAtTimestamp" to evidence.previousPerformedAtTimestamp,
            "checkedAtTimestamp" to evidence.checkedAtTimestamp
        ) },
        "consumedReassessmentEventIds" to value.consumedReassessmentEventIds
    )).also { require(it.length <= 1_048_576) { "Condition payload exceeds transport limit." } }

    fun decodeSession(json: String): SessionConditionContext {
        val row = parse(json).obj("formatVersion", "policyVersion", "selectedConditions",
            "sourceSupportVersions", "appliedRules", "consumedScheduleEvidence", "consumedReassessmentEventIds")
        val selections = row.field("selectedConditions").array().map { item ->
            val selected = item.obj("conditionId", "contextSchemaVersion", "answers")
            SelectedCondition(selected.string("conditionId"), selected.int("contextSchemaVersion"),
                selected.field("answers").obj().mapValues { (_, value) -> value?.string() })
        }
        val versions = row.field("sourceSupportVersions").obj().mapValues { (_, value) -> value.string() }
        val rules = row.field("appliedRules").array().map { item ->
            val rule = item.obj("reference", "exerciseIdentity", "executionIdentity", "purpose", "explanation")
            val exercise = rule.field("exerciseIdentity").obj("exerciseId", "contentSha256", "exerciseType", "artworkSha256")
            val explanation = rule.field("explanation").obj("en", "es")
            AppliedConditionRule(
                readReference(rule.field("reference")),
                ConditionExerciseIdentity(exercise.string("exerciseId"), exercise.string("contentSha256"),
                    ExerciseType.valueOf(exercise.string("exerciseType")), exercise.string("artworkSha256")),
                rule.string("executionIdentity"), PrescriptionPurpose.valueOf(rule.string("purpose")),
                ConditionText(explanation.string("en"), explanation.string("es"))
            )
        }
        val schedule = row.field("consumedScheduleEvidence").array().map { item ->
            val evidence = item.obj("reference", "previousPerformedAtTimestamp", "checkedAtTimestamp")
            ConditionScheduleEvidence(readReference(evidence.field("reference")),
                evidence.field("previousPerformedAtTimestamp")?.long(), evidence.long("checkedAtTimestamp"))
        }
        val value = SessionConditionContext(row.int("formatVersion"), row.int("policyVersion"),
            selections, versions, rules, schedule,
            row.field("consumedReassessmentEventIds").array().map { it.string() })
        require(json == encodeSession(value)) { "Noncanonical session condition context." }
        return value
    }

    private fun selection(value: SelectedCondition) = mapOf(
        "conditionId" to value.conditionId, "contextSchemaVersion" to value.contextSchemaVersion,
        "answers" to value.answers
    )

    private fun reference(value: ConditionRuleReference) = mapOf(
        "conditionId" to value.conditionId, "supportVersion" to value.supportVersion,
        "caseId" to value.caseId, "ruleId" to value.ruleId, "policyVersion" to value.policyVersion
    )

    private fun identity(value: ConditionExerciseIdentity) = mapOf(
        "exerciseId" to value.exerciseId, "contentSha256" to value.contentSha256,
        "exerciseType" to value.exerciseType.name, "artworkSha256" to value.artworkSha256
    )

    private fun readReference(value: Any?): ConditionRuleReference {
        val row = value.obj("conditionId", "supportVersion", "caseId", "ruleId", "policyVersion")
        return ConditionRuleReference(row.string("conditionId"), row.string("supportVersion"),
            row.string("caseId"), row.string("ruleId"), row.int("policyVersion"))
    }

    private fun parse(json: String): Any? {
        require(json.length <= 1_048_576) { "Condition payload exceeds transport limit." }
        val reader = JsonReader(BoundedCharacterReader(StringReader(json), 1_048_576) {
            throw IllegalArgumentException("Condition payload exceeds transport limit.")
        })
        val value = read(reader, 0)
        require(reader.peek() == JsonToken.END_DOCUMENT) { "Trailing condition payload." }
        return value
    }

    private fun read(reader: JsonReader, depth: Int): Any? {
        require(depth <= 16) { "Condition payload exceeds nesting limit." }
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                val fields = linkedMapOf<String, Any?>()
                reader.beginObject()
                while (reader.hasNext()) {
                    require(fields.size < 128)
                    val name = reader.nextName()
                    require(name.length <= 128 && !fields.containsKey(name)) { "Duplicate or oversized condition field." }
                    fields[name] = read(reader, depth + 1)
                }
                reader.endObject()
                fields
            }
            JsonToken.BEGIN_ARRAY -> {
                val values = mutableListOf<Any?>()
                reader.beginArray()
                while (reader.hasNext()) { require(values.size < 512); values += read(reader, depth + 1) }
                reader.endArray()
                values
            }
            JsonToken.STRING -> reader.nextString().also { require(it.length <= 4_096) }
            JsonToken.NUMBER -> reader.nextString().also { require(it.length <= 64 && it.matches(Regex("-?(0|[1-9][0-9]*)"))) }.let(::IntegerToken)
            JsonToken.NULL -> { reader.nextNull(); null }
            else -> throw IllegalArgumentException("Unexpected condition payload token.")
        }
    }

    private class IntegerToken(val text: String)
    private fun Any?.array(): List<Any?> = this as? List<*> ?: throw IllegalArgumentException("Expected condition array.")
    private fun Any?.obj(vararg keys: String): Map<String, Any?> {
        val value = this as? Map<*, *> ?: throw IllegalArgumentException("Expected condition object.")
        require(keys.isEmpty() || value.keys == keys.toSet()) { "Unexpected condition fields." }
        @Suppress("UNCHECKED_CAST")
        return value as Map<String, Any?>
    }
    private fun Map<String, Any?>.field(name: String): Any? = getValue(name)
    private fun Map<String, Any?>.string(name: String) = field(name).string()
    private fun Map<String, Any?>.int(name: String) = field(name).int()
    private fun Map<String, Any?>.long(name: String) = field(name).long()
    private fun Any?.string(): String = this as? String ?: throw IllegalArgumentException("Expected condition string.")
    private fun Any?.int(): Int = (this as? IntegerToken)?.text?.toIntOrNull() ?: throw IllegalArgumentException("Expected condition integer.")
    private fun Any?.long(): Long = (this as? IntegerToken)?.text?.toLongOrNull() ?: throw IllegalArgumentException("Expected condition integer.")
}
