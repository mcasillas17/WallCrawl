package wallcrawl.elopenmike.com.core.model

import java.math.BigDecimal
import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest
import java.util.Collections

enum class PrescriptionPurpose { ORDINARY, SUPPORTIVE }
enum class ConditionReviewState { DRAFT, RELEASED }
enum class ConditionRuleEffect { EXCLUDE, PERMIT_ORDINARY, PRESCRIBE_SUPPORTIVE }
enum class ConditionPopulation { ADULT_NONOPERATIVE }

data class ConditionText(val en: String, val es: String) {
    init { require(en.isNotBlank() && es.isNotBlank() && en.length <= 4_096 && es.length <= 4_096) { "Both English and Spanish condition text are required and bounded." } }
}

class ConditionInputDefinition(val id: String, val prompt: ConditionText, val required: Boolean, choices: Map<String, ConditionText>) {
    val choices = conditionMap(choices)
    init {
        requireConditionToken(id)
        require(choices.size in 1..32)
        choices.keys.forEach(::requireConditionToken)
    }

    private fun valueFields(): List<Any?> = listOf(id, prompt, required, choices)
    override fun equals(other: Any?): Boolean = other is ConditionInputDefinition && valueFields() == other.valueFields()
    override fun hashCode(): Int = valueFields().hashCode()
}

data class ConditionSource(val id: String, val url: String, val contentSha256: String) {
    init {
        requireConditionToken(id); requireConditionDigest(contentSha256)
        val uri = try { URI(url) } catch (error: URISyntaxException) {
            throw IllegalArgumentException("Invalid condition source URL.", error)
        }
        require(url.length <= 2_048 && uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null)
    }
}

/** Shape and exact-content binding only; software cannot authenticate a clinician's identity. */
class ConditionReleaseReview(
    val state: ConditionReviewState,
    val reviewerIdentity: String,
    val reviewerQualification: String,
    val reviewedAtEpochMillis: Long,
    val releaseAuthorization: String,
    val reviewedContentSha256: String,
    sourceIds: Set<String>
) {
    val sourceIds = conditionSet(sourceIds)
    init {
        require(state == ConditionReviewState.RELEASED)
        require(listOf(reviewerIdentity, reviewerQualification, releaseAuthorization).all { it.isNotBlank() && it.length <= 2_048 })
        require(reviewedAtEpochMillis > 0)
        requireConditionDigest(reviewedContentSha256)
        require(sourceIds.size in 1..64); sourceIds.forEach(::requireConditionToken)
    }

    private fun valueFields(): List<Any?> = listOf(state, reviewerIdentity, reviewerQualification, reviewedAtEpochMillis, releaseAuthorization, reviewedContentSha256, sourceIds)
    override fun equals(other: Any?): Boolean = other is ConditionReleaseReview && valueFields() == other.valueFields()
    override fun hashCode(): Int = valueFields().hashCode()
}

/** Loads are normalized to kilograms with convertWeight; null adds no condition bound. */
data class ConditionPrescriptionBounds(
    val minSets: Int? = null, val maxSets: Int? = null,
    val minReps: Int? = null, val maxReps: Int? = null,
    val minLoadKg: Double? = null, val maxLoadKg: Double? = null,
    val minAssistanceKg: Double? = null, val maxAssistanceKg: Double? = null,
    val minDurationSeconds: Int? = null, val maxDurationSeconds: Int? = null,
    val minDistanceMeters: Double? = null, val maxDistanceMeters: Double? = null,
    val minRestSeconds: Int? = null, val maxRestSeconds: Int? = null
) {
    init {
        ordered(minSets, maxSets, 1, ExercisePrescription.MAX_TARGET_SETS)
        ordered(minReps, maxReps, 1, ExercisePrescription.MAX_TARGET_REPS)
        ordered(minDurationSeconds, maxDurationSeconds, 1, ExercisePrescription.MAX_DURATION_SECONDS)
        ordered(minRestSeconds, maxRestSeconds, 0, ExercisePrescription.MAX_REST_SECONDS)
        ordered(minLoadKg, maxLoadKg, 0.0, WorkoutTemplate.maxLoad(WeightUnit.KG))
        ordered(minAssistanceKg, maxAssistanceKg, 0.0, WorkoutTemplate.maxLoad(WeightUnit.KG))
        ordered(minDistanceMeters, maxDistanceMeters, ExercisePrescription.MIN_DISTANCE_METERS, ExercisePrescription.MAX_DISTANCE_METERS)
    }
    private fun ordered(min: Int?, max: Int?, lower: Int, upper: Int) {
        require((min == null || min in lower..upper) && (max == null || max in lower..upper) && (min == null || max == null || min <= max)) { "Invalid integer condition bounds." }
    }
    private fun ordered(min: Double?, max: Double?, lower: Double, upper: Double) {
        require((min == null || min.isFinite() && min in lower..upper) && (max == null || max.isFinite() && max in lower..upper) && (min == null || max == null || min <= max)) { "Invalid decimal condition bounds." }
    }
}

/** Exact reviewed execution is identified separately from the underlying catalog content. */
data class ConditionExerciseIdentity(val exerciseId: String, val contentSha256: String, val exerciseType: ExerciseType, val artworkSha256: String) {
    init { requireConditionToken(exerciseId); requireConditionDigest(contentSha256); requireConditionDigest(artworkSha256) }
    companion object {
        fun from(exercise: Exercise, artworkSha256: String): ConditionExerciseIdentity = ConditionExerciseIdentity(
            exercise.id, conditionDigest(mapOf(
                "id" to exercise.id, "name" to exercise.name, "type" to exercise.type.name,
                "isStretch" to exercise.isStretch, "primaryMuscles" to exercise.primaryMuscles,
                "secondaryMuscles" to exercise.secondaryMuscles, "equipment" to exercise.listedEquipment,
                "source" to exercise.source?.let { source -> listOf(source.catalogId, source.sourceId, source.sourceSlug,
                    source.attribution.creator, source.attribution.creatorUrl, source.attribution.license, source.attribution.licenseUrl,
                    source.attribution.source?.let { listOf(it.name, it.url, it.license, it.licenseUrl, it.changes) }) },
                "programming" to exercise.programming?.let { listOf(it.requiredEquipmentCombinations,
                    it.movementPattern.name, it.difficulty.name, it.mechanics.name, it.recommendedRepRange?.let { range -> listOf(range.min, range.max) },
                    it.fatigueScore, it.progressionType.name, it.alternativeExerciseIds, it.coachingSummary) }
            )), exercise.type, artworkSha256
        )
    }
}

data class ConditionSchedule(val sessionsPerWeek: Int, val minimumHoursBetweenSessions: Int, val instructions: ConditionText) {
    init { require(sessionsPerWeek in 1..7 && minimumHoursBetweenSessions in 0..168) }
}

/** All response choices are authored tokens; no symptom interpretation or default threshold. */
class ConditionResponsePolicy(
    val version: Int, inputs: List<ConditionInputDefinition>,
    stopWhenAnswers: Map<String, String>, reassessmentRequiredAnswers: Map<String, String>,
    val escalation: ConditionText
) {
    val inputs = conditionList(inputs)
    val stopWhenAnswers = conditionMap(stopWhenAnswers)
    val reassessmentRequiredAnswers = conditionMap(reassessmentRequiredAnswers)
    init {
        require(version > 0); validateConditionInputs(inputs)
        require(stopWhenAnswers.isNotEmpty() && reassessmentRequiredAnswers.isNotEmpty())
        validateConditionAnswers(stopWhenAnswers, inputs); validateConditionAnswers(reassessmentRequiredAnswers, inputs)
    }

    private fun valueFields(): List<Any?> = listOf(version, inputs, stopWhenAnswers, reassessmentRequiredAnswers, escalation)
    override fun equals(other: Any?): Boolean = other is ConditionResponsePolicy && valueFields() == other.valueFields()
    override fun hashCode(): Int = valueFields().hashCode()
}

class ConditionRule(
    val id: String, val exerciseIdentity: ConditionExerciseIdentity,
    val effect: ConditionRuleEffect, val executionIdentity: String, val instructions: ConditionText,
    sourceIds: Set<String>, val bounds: ConditionPrescriptionBounds?,
    val allowOrdinaryProgression: Boolean,
    val supportivePrescription: ExercisePrescription?, val schedule: ConditionSchedule?
) {
    val sourceIds = conditionSet(sourceIds)
    init {
        requireConditionToken(id); requireConditionToken(executionIdentity)
        require(sourceIds.size in 1..64); sourceIds.forEach(::requireConditionToken)
        when (effect) {
            ConditionRuleEffect.EXCLUDE -> require(bounds == null && !allowOrdinaryProgression && supportivePrescription == null && schedule == null)
            ConditionRuleEffect.PERMIT_ORDINARY -> {
                require(bounds != null && supportivePrescription == null && schedule == null)
                validateBoundsForType(bounds, exerciseIdentity.exerciseType)
            }
            ConditionRuleEffect.PRESCRIBE_SUPPORTIVE -> require(bounds == null && !allowOrdinaryProgression && supportivePrescription != null && schedule != null && supportivePrescription.purpose == PrescriptionPurpose.SUPPORTIVE && supportivePrescription.exerciseType == exerciseIdentity.exerciseType)
        }
    }

    private fun valueFields(): List<Any?> = listOf(id, exerciseIdentity, effect, executionIdentity, instructions, sourceIds, bounds, allowOrdinaryProgression, supportivePrescription, schedule)
    override fun equals(other: Any?): Boolean = other is ConditionRule && valueFields() == other.valueFields()
    override fun hashCode(): Int = valueFields().hashCode()
}

private fun validateBoundsForType(bounds: ConditionPrescriptionBounds, type: ExerciseType) {
    val hasReps = bounds.minReps != null || bounds.maxReps != null
    val hasLoad = bounds.minLoadKg != null || bounds.maxLoadKg != null
    val hasAssistance = bounds.minAssistanceKg != null || bounds.maxAssistanceKg != null
    val hasDuration = bounds.minDurationSeconds != null || bounds.maxDurationSeconds != null
    val hasDistance = bounds.minDistanceMeters != null || bounds.maxDistanceMeters != null
    when (type) {
        ExerciseType.WEIGHT_REPS -> require(!hasAssistance && !hasDuration && !hasDistance)
        ExerciseType.BODYWEIGHT_REPS -> require(!hasLoad && !hasAssistance && !hasDuration && !hasDistance)
        ExerciseType.ASSISTED_BODYWEIGHT -> require(!hasLoad && !hasDuration && !hasDistance)
        ExerciseType.DURATION -> require(!hasReps && !hasLoad && !hasAssistance && !hasDistance)
        ExerciseType.DISTANCE_DURATION -> require(!hasReps && !hasLoad && !hasAssistance)
    }
}

class ConditionApplicabilityCase(val id: String, requiredAnswers: Map<String, String>, rules: List<ConditionRule>) {
    val requiredAnswers = conditionMap(requiredAnswers)
    val rules = conditionList(rules)
    init {
        requireConditionToken(id); require(rules.size in 1..512)
        require(rules.map { it.id }.distinct().size == rules.size)
        require(rules.map { it.exerciseIdentity.exerciseId }.distinct().size == rules.size)
    }

    private fun valueFields(): List<Any?> = listOf(id, requiredAnswers, rules)
    override fun equals(other: Any?): Boolean = other is ConditionApplicabilityCase && valueFields() == other.valueFields()
    override fun hashCode(): Int = valueFields().hashCode()
}

class ConditionSupportRecord(
    val conditionId: String, val contentVersion: String, val contextSchemaVersion: Int, val policyVersion: Int,
    val population: ConditionPopulation, val label: ConditionText, val instructions: ConditionText,
    inputs: List<ConditionInputDefinition>, cases: List<ConditionApplicabilityCase>,
    sources: List<ConditionSource>, val responsePolicy: ConditionResponsePolicy,
    val contentSha256: String, val review: ConditionReleaseReview
) {
    val inputs = conditionList(inputs)
    val cases = conditionList(cases)
    val sources = conditionList(sources)
    init {
        requireConditionToken(conditionId); requireConditionToken(contentVersion)
        require(contextSchemaVersion > 0 && policyVersion > 0); validateConditionInputs(inputs)
        require(cases.size in 1..128 && cases.map { it.id }.distinct().size == cases.size)
        require(sources.size in 1..64 && sources.map { it.id }.distinct().size == sources.size)
        requireConditionDigest(contentSha256); require(review.reviewedContentSha256 == contentSha256)
        val sourceIds = sources.map { it.id }.toSet()
        require(review.sourceIds == sourceIds) { "Review must bind every exact source." }
        cases.forEach { case ->
            validateConditionAnswers(case.requiredAnswers, inputs)
            require(inputs.filter { it.required }.all { it.id in case.requiredAnswers })
            case.rules.forEach { require(sourceIds.containsAll(it.sourceIds)) }
        }
        cases.forEachIndexed { index, first -> cases.drop(index + 1).forEach { second ->
            require(first.requiredAnswers.any { (key, value) -> second.requiredAnswers[key]?.let { it != value } == true }) { "Ambiguous applicability cases." }
        } }
    }

    private fun valueFields(): List<Any?> = listOf(conditionId, contentVersion, contextSchemaVersion, policyVersion, population, label, instructions, inputs, cases, sources, responsePolicy, contentSha256, review)
    override fun equals(other: Any?): Boolean = other is ConditionSupportRecord && valueFields() == other.valueFields()
    override fun hashCode(): Int = valueFields().hashCode()
}

class ConditionSupportCatalog(val contentVersion: String, records: Map<String, ConditionSupportRecord>) {
    val records = conditionMap(records)
    init {
        requireConditionToken(contentVersion); require(records.size <= 128)
        require(records.all { (id, record) -> id == record.conditionId })
    }

    private fun valueFields(): List<Any?> = listOf(contentVersion, records)
    override fun equals(other: Any?): Boolean = other is ConditionSupportCatalog && valueFields() == other.valueFields()
    override fun hashCode(): Int = valueFields().hashCode()
}

class ConditionViolation(val reason: String, conditionIds: Set<String>, exerciseIds: Set<String> = emptySet()) {
    val conditionIds = conditionSet(conditionIds)
    val exerciseIds = conditionSet(exerciseIds)
    init {
        requireConditionToken(reason)
        require(conditionIds.size <= 32 && exerciseIds.size <= 512)
        conditionIds.forEach(::requireConditionToken); exerciseIds.forEach(::requireConditionToken)
    }

    private fun valueFields(): List<Any?> = listOf(reason, conditionIds, exerciseIds)
    override fun equals(other: Any?): Boolean = other is ConditionViolation && valueFields() == other.valueFields()
    override fun hashCode(): Int = valueFields().hashCode()
}

data class ConditionRuleReference(val conditionId: String, val supportVersion: String, val caseId: String, val ruleId: String, val policyVersion: Int) {
    init { listOf(conditionId, supportVersion, caseId, ruleId).forEach(::requireConditionToken); require(policyVersion > 0) }
}

class ResolvedConditionRule(val bounds: ConditionPrescriptionBounds, val executionIdentity: String, val allowOrdinaryProgression: Boolean, sourceRuleReferences: Set<ConditionRuleReference>, policyVersions: Set<Int>) {
    val sourceRuleReferences = conditionSet(sourceRuleReferences)
    val policyVersions = conditionSet(policyVersions)
    init {
        requireConditionToken(executionIdentity)
        require(sourceRuleReferences.size in 1..512 && policyVersions.isNotEmpty() && policyVersions.all { it > 0 })
        require(sourceRuleReferences.map { it.policyVersion }.toSet() == policyVersions)
    }

    private fun valueFields(): List<Any?> = listOf(bounds, executionIdentity, allowOrdinaryProgression, sourceRuleReferences, policyVersions)
    override fun equals(other: Any?): Boolean = other is ResolvedConditionRule && valueFields() == other.valueFields()
    override fun hashCode(): Int = valueFields().hashCode()
}

/** Exact supportive prescription loads use kilograms, matching normalized condition bounds. */
class ConditionSupportivePrescription(val exerciseId: String, val prescription: ExercisePrescription, val executionIdentity: String, sourceRuleReferences: Set<ConditionRuleReference>, val schedule: ConditionSchedule) {
    val sourceRuleReferences = conditionSet(sourceRuleReferences)
    init { requireConditionToken(exerciseId); requireConditionToken(executionIdentity); require(prescription.purpose == PrescriptionPurpose.SUPPORTIVE && sourceRuleReferences.size in 1..512) }

    private fun valueFields(): List<Any?> = listOf(exerciseId, prescription, executionIdentity, sourceRuleReferences, schedule)
    override fun equals(other: Any?): Boolean = other is ConditionSupportivePrescription && valueFields() == other.valueFields()
    override fun hashCode(): Int = valueFields().hashCode()
}

sealed interface ConditionPlanningResult {
    data object NotApplicable : ConditionPlanningResult
    class Blocked(reasons: List<ConditionViolation>) : ConditionPlanningResult {
        val reasons = conditionList(reasons)
        init { require(reasons.size in 1..512) }

        private fun valueFields(): List<Any?> = listOf(reasons)
        override fun equals(other: Any?): Boolean = other is Blocked && valueFields() == other.valueFields()
        override fun hashCode(): Int = valueFields().hashCode()
    }
    class Ready(ordinaryRules: Map<String, ResolvedConditionRule>, supportive: List<ConditionSupportivePrescription>, val context: SessionConditionContext) : ConditionPlanningResult {
        val ordinaryRules = conditionMap(ordinaryRules)
        val supportive = conditionList(supportive)
        init { require(ordinaryRules.size <= 512 && supportive.size <= 512) }

        private fun valueFields(): List<Any?> = listOf(ordinaryRules, supportive, context)
        override fun equals(other: Any?): Boolean = other is Ready && valueFields() == other.valueFields()
        override fun hashCode(): Int = valueFields().hashCode()
    }
}

internal fun requireConditionToken(value: String) {
    require(value.length in 1..128 && value.all { it.isLetterOrDigit() && it.code < 128 || it == '-' || it == '_' || it == '.' }) { "Invalid condition token." }
}
internal fun requireConditionDigest(value: String) { require(value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }) { "Invalid SHA-256 digest." } }
internal fun validateConditionInputs(inputs: List<ConditionInputDefinition>) { require(inputs.size <= 32 && inputs.map { it.id }.distinct().size == inputs.size) }
internal fun validateConditionAnswers(answers: Map<String, String>, inputs: List<ConditionInputDefinition>) {
    require(answers.size <= 32)
    answers.forEach { (id, token) -> require(inputs.singleOrNull { it.id == id }?.choices?.containsKey(token) == true) { "Unknown input or exact answer token." } }
}

/** Sorted JSON objects, ordered arrays, finite plain decimals and UTF-8 form the digest input. */
internal fun conditionDigest(value: Any?): String = MessageDigest.getInstance("SHA-256")
    .digest(conditionCanonicalJson(value).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

internal fun conditionCanonicalJson(value: Any?): String = when (value) {
    null -> "null"
    is String -> buildString {
        append('"'); value.forEach { character -> when (character) {
            '"' -> append("\\\""); '\\' -> append("\\\\")
            in '\u0000'..'\u001f', in '\uD800'..'\uDFFF' -> append("\\u%04x".format(character.code))
            else -> append(character)
        } }; append('"')
    }
    is Boolean -> value.toString()
    is Number -> BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
    is Map<*, *> -> value.entries.sortedBy { it.key as String }.joinToString(",", "{", "}") { conditionCanonicalJson(it.key) + ":" + conditionCanonicalJson(it.value) }
    is Iterable<*> -> value.joinToString(",", "[", "]") { conditionCanonicalJson(it) }
    else -> error("Unsupported condition digest value.")
}

internal fun <T> conditionList(values: List<T>): List<T> = Collections.unmodifiableList(values.toList())
internal fun <T> conditionSet(values: Set<T>): Set<T> = Collections.unmodifiableSet(values.toSet())
internal fun <K, V> conditionMap(values: Map<K, V>): Map<K, V> = Collections.unmodifiableMap(values.toMap())
