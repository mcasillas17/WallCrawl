package wallcrawl.elopenmike.com.core.condition

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.StringReader
import java.security.MessageDigest
import java.math.BigDecimal
import org.json.JSONArray
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.DefaultAppContainer
import wallcrawl.elopenmike.com.core.model.*

@RunWith(AndroidJUnit4::class)
class ConditionSupportParserTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val fictionalExercise = Exercise("fictional-exercise", name = "FICTIONAL TEST EXERCISE", primaryMuscles = listOf("Core"), type = ExerciseType.WEIGHT_REPS)
    private val identities = mapOf(fictionalExercise.id to ConditionExerciseIdentity.from(fictionalExercise, "b".repeat(64)))
    private val parser = ConditionSupportParser(identities)
    private fun fixture(): String = instrumentation.context.assets.open("conditions/fictional-support.json").bufferedReader().use { it.readText() }
    private fun parse(json: String) = parser.parse(StringReader(json))
    private fun reject(json: String) { assertThrows(ConditionSupportFormatException::class.java) { parse(json) } }

    @Test fun productionCatalogIsEmptyAndStoreUsesApplicationWiring() = runBlocking {
        val container = DefaultAppContainer(instrumentation.targetContext)
        val first = container.conditionSupportStore.load()
        assertThat(first.records).isEmpty()
        assertThat(container.conditionSupportStore.load()).isSameInstanceAs(first)
    }
    @Test fun fictionalReleasedFixtureHasBilingualCopyAndExactAnswers() {
        val record = parse(fixture()).records.getValue("fictional-condition")
        assertThat(record.label.en).contains("FICTIONAL")
        assertThat(record.label.es).contains("FICTICIA")
        assertThat(record.cases.single().requiredAnswers["stage"]).isEqualTo("STABLE")
        assertThat(record.review.reviewerIdentity).isEqualTo("FICTIONAL TEST REVIEWER — NOT CLINICAL APPROVAL")
    }
    @Test fun supportivePrescriptionRetainsExactDosePurposeAndReviewedSchedule() {
        val exercise = fictionalExercise.copy(type = ExerciseType.DURATION)
        val parser = ConditionSupportParser(mapOf(exercise.id to ConditionExerciseIdentity.from(exercise, "b".repeat(64))))
        val support = instrumentation.context.assets.open("conditions/fictional-supportive.json").bufferedReader().use(parser::parse)
        val rule = support.records.getValue("fictional-condition").cases.single().rules.single()
        assertThat(rule.supportivePrescription!!.purpose).isEqualTo(PrescriptionPurpose.SUPPORTIVE)
        assertThat(rule.supportivePrescription.targetDurationSeconds).isEqualTo(20)
        assertThat(rule.schedule!!.sessionsPerWeek).isEqualTo(2)
        assertThat(rule.allowOrdinaryProgression).isFalse()
    }
    @Test fun resigningUnchangedFixturePreservesTheReviewedDigest() {
        val root = JSONObject(fixture())
        val review = root.getJSONArray("records").getJSONObject(0).getJSONObject("review")
        val original = review.getString("reviewedContentSha256")
        rebindReview(root)
        assertThat(review.getString("reviewedContentSha256")).isEqualTo(original)
        assertThat(parse(root.toString()).records).hasSize(1)
    }
    @Test fun changedReviewedRequirementsRejectThePreviouslyBoundRelease() {
        val metadata = fictionalReviewedMetadata()
        val originalExercise = fictionalExercise.copy(reviewedMetadata = metadata)
        val originalIdentity = ConditionExerciseIdentity.from(originalExercise, "b".repeat(64))
        val root = JSONObject(fixture())
        val content = root.getJSONArray("records").getJSONObject(0).getJSONObject("content")
        content.getJSONArray("cases").getJSONObject(0).getJSONArray("rules").getJSONObject(0)
            .getJSONObject("exerciseIdentity").put("contentSha256", originalIdentity.contentSha256)
        rebindReview(root)
        val released = root.toString()
        assertThat(ConditionSupportParser(mapOf(originalExercise.id to originalIdentity)).parse(StringReader(released)).records).hasSize(1)
        listOf(
            metadata.copy(equipmentAlternatives = listOf(listOf(StandardEquipment.WALL))),
            metadata.copy(supportRequirement = SupportRequirement.UNSUPPORTED),
            metadata.copy(impactLevel = ImpactLevel.HIGH),
            metadata.copy(provenance = metadata.provenance.copy(policyVersion = 2)),
            metadata.copy(aiReviewProvenance = metadata.aiReviewProvenance!!.copy(reviewedContentSha256 = "c".repeat(64)))
        ).forEach { changed ->
            val actual = ConditionExerciseIdentity.from(originalExercise.copy(reviewedMetadata = changed), "b".repeat(64))
            assertThrows(ConditionSupportFormatException::class.java) {
                ConditionSupportParser(mapOf(actual.exerciseId to actual)).parse(StringReader(released))
            }
        }
    }
    @Test fun correctlyReboundSupportiveScheduleMustFitItsWeeklyCycle() {
        val exercise = fictionalExercise.copy(type = ExerciseType.DURATION)
        val parser = ConditionSupportParser(mapOf(exercise.id to ConditionExerciseIdentity.from(exercise, "b".repeat(64))))
        val root = JSONObject(instrumentation.context.assets.open("conditions/fictional-supportive.json").bufferedReader().use { it.readText() })
        val schedule = root.getJSONArray("records").getJSONObject(0).getJSONObject("content")
            .getJSONArray("cases").getJSONObject(0).getJSONArray("rules").getJSONObject(0).getJSONObject("schedule")
        schedule.put("sessionsPerWeek", 7).put("minimumHoursBetweenSessions", 24)
        rebindReview(root)
        assertThat(parser.parse(StringReader(root.toString())).records).hasSize(1)
        schedule.put("minimumHoursBetweenSessions", 168)
        rebindReview(root)
        assertThrows(ConditionSupportFormatException::class.java) { parser.parse(StringReader(root.toString())) }
    }
    @Test fun immutableParsedContractsCompareStructurallyAcrossLoads() {
        val first = parse(fixture()); val second = parse(fixture())
        assertThat(first).isEqualTo(second)
        assertThat(first.hashCode()).isEqualTo(second.hashCode())
        assertThrows(UnsupportedOperationException::class.java) { (first.records as MutableMap).clear() }
        val record = first.records.getValue("fictional-condition")
        assertThrows(UnsupportedOperationException::class.java) { (record.inputs.single().choices as MutableMap).clear() }
    }
    @Test fun duplicateSourcesAndInputsAreRejected() {
        mutateContent { val sources = it.getJSONArray("sources"); sources.put(sources.getJSONObject(0)) }
        mutateContent { val inputs = it.getJSONArray("inputs"); inputs.put(inputs.getJSONObject(0)) }
    }
    @Test fun reviewedRulesCannotReferToUnknownSources() {
        mutateContent { it.getJSONArray("cases").getJSONObject(0).getJSONArray("rules").getJSONObject(0).put("sourceIds", JSONArray().put("unknown-source")) }
    }
    @Test fun malformedMissingAndWrongTypesAreRejected() {
        reject("{")
        reject("[]")
        reject(fixture().replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""))
        val root = JSONObject(fixture()); root.remove("records"); reject(root.toString())
    }
    @Test fun requiredReviewFieldsCannotBeMissingOrBlank() {
        listOf("reviewerQualification", "releaseAuthorization", "reviewedAtEpochMillis").forEach { field ->
            val root = JSONObject(fixture()); root.getJSONArray("records").getJSONObject(0).getJSONObject("review").remove(field); reject(root.toString())
        }
        val root = JSONObject(fixture()); root.getJSONArray("records").getJSONObject(0).getJSONObject("review").put("reviewerIdentity", " "); reject(root.toString())
    }
    @Test fun emptyReleasedCatalogIsValid() { assertThat(parse("""{"schemaVersion":1,"contentVersion":"test-v1","records":[]}""").records).isEmpty() }
    @Test fun unknownSchemaIsRejected() { reject(fixture().replace("\"schemaVersion\":1", "\"schemaVersion\":2")) }
    @Test fun unknownReviewStateIsRejected() { reject(fixture().replace("RELEASED", "AI_ACCEPTED")) }
    @Test fun draftRecordIsRejectedFromReleasedCatalog() { reject(fixture().replace("RELEASED", "DRAFT")) }
    @Test fun duplicateRootFieldsAreRejected() { reject(fixture().replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1")) }
    @Test fun duplicateNestedFieldsAreRejected() { reject(fixture().replace("\"en\":\"FICTIONAL", "\"en\":\"duplicate\",\"en\":\"FICTIONAL")) }
    @Test fun duplicateConditionIdsAreRejected() {
        val root = JSONObject(fixture()); root.getJSONArray("records").put(root.getJSONArray("records").getJSONObject(0))
        reject(root.toString())
    }
    @Test fun duplicateRuleIdsAreRejected() { mutateContent { content -> val rules = content.getJSONArray("cases").getJSONObject(0).getJSONArray("rules"); rules.put(rules.getJSONObject(0)) } }
    @Test fun invalidBoundsAreRejected() { mutateContent { it.getJSONArray("cases").getJSONObject(0).getJSONArray("rules").getJSONObject(0).getJSONObject("bounds").put("minSets", 5) } }
    @Test fun invalidLoadUnitIsRejected() { mutateContent { it.getJSONArray("cases").getJSONObject(0).getJSONArray("rules").getJSONObject(0).getJSONObject("bounds").put("loadUnit", "STONE") } }
    @Test fun nonIntegerBoundIsRejected() { reject(fixture().replace("\"maxSets\":3", "\"maxSets\":3.5")) }
    @Test fun overlappingApplicabilityCasesAreRejected() { mutateContent { val cases = it.getJSONArray("cases"); val duplicate = JSONObject(cases.getJSONObject(0).toString()).put("id", "other-case"); cases.put(duplicate) } }
    @Test fun answerTokensAreNotCaseNormalized() { mutateContent { it.getJSONArray("cases").getJSONObject(0).getJSONObject("requiredAnswers").put("stage", "stable") } }
    @Test fun changedClinicalContentInvalidatesReviewDigest() { reject(fixture().replace("FICTIONAL INSTRUCTIONS", "CHANGED INSTRUCTIONS")) }
    @Test fun releasedRecordRequiresActualReviewProvenanceFields() { val root = JSONObject(fixture()); root.getJSONArray("records").getJSONObject(0).getJSONObject("review").remove("reviewerIdentity"); reject(root.toString()) }
    @Test fun releasedRecordRequiresBoundReviewSources() { val root = JSONObject(fixture()); root.getJSONArray("records").getJSONObject(0).getJSONObject("review").put("sourceIds", org.json.JSONArray().put("unknown-source")); reject(root.toString()) }
    @Test fun malformedSourceUrlIsReportedAsAFormatFailure() {
        mutateContent { it.getJSONArray("sources").getJSONObject(0).put("url", "https://[") }
    }
    @Test fun missingSpanishTextIsRejected() { mutateContent { it.getJSONObject("label").remove("es") } }
    @Test fun unresolvedExerciseReferenceIsRejected() { assertThrows(ConditionSupportFormatException::class.java) { ConditionSupportParser().parse(StringReader(fixture())) } }
    @Test fun missingArtworkBindingCannotAuthorizeNonemptySupport() {
        mutateContent { it.getJSONArray("cases").getJSONObject(0).getJSONArray("rules").getJSONObject(0).getJSONObject("exerciseIdentity").remove("artworkSha256") }
    }
    @Test fun changedActualArtworkInvalidatesBinding() {
        val identity = ConditionExerciseIdentity.from(fictionalExercise, "c".repeat(64))
        assertThrows(ConditionSupportFormatException::class.java) { ConditionSupportParser(mapOf(identity.exerciseId to identity)).parse(StringReader(fixture())) }
    }
    @Test fun changedActualExerciseContentInvalidatesBinding() {
        val changed = fictionalExercise.copy(listedEquipment = listOf("Wall"))
        assertThrows(ConditionSupportFormatException::class.java) { ConditionSupportParser(mapOf(changed.id to ConditionExerciseIdentity.from(changed, "b".repeat(64)))).parse(StringReader(fixture())) }
    }
    @Test fun unknownFieldsTrailingContentAndOversizedInputAreRejected() {
        reject(fixture().replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"unexpected\":true"))
        reject(fixture() + "{}")
        reject(" ".repeat(1_048_577))
    }
    @Test fun loadBoundsUseDeclaredUnitsAndExistingConversion() {
        val bounds = parse(fixture()).records.getValue("fictional-condition").cases.single().rules.single().bounds!!
        assertThat(bounds.maxLoadKg).isWithin(0.0000001).of(4.5359237)
    }
    // Independent fixture canonicalizer allows each malformed contract to reach its own validator.
    private fun fixtureCanonical(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().sorted().joinToString(",", "{", "}") { JSONObject.quote(it).replace("\\/", "/") + ":" + fixtureCanonical(value.get(it)) }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { fixtureCanonical(value.get(it)) }
        is Number -> BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
        is Boolean -> value.toString()
        else -> JSONObject.quote(value.toString()).replace("\\/", "/")
    }
    private fun fixtureDigest(content: JSONObject): String = MessageDigest.getInstance("SHA-256").digest(fixtureCanonical(content).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun fictionalReviewedMetadata() = ReviewedExerciseMetadata(
        reviewState = ReviewState.AI_ACCEPTED, directPrimaryMuscle = "Core", descriptiveSecondaryMuscles = emptySet(),
        movementPattern = MovementPattern.CORE, complexity = ComplexityTier.FOUNDATIONAL, progressionFamily = "fictional-family",
        prescriptionShape = PrescriptionShape.WEIGHT_REPS, approvedRegressions = emptyList(), approvedSubstitutions = emptyList(),
        capabilityRequirements = emptySet(), supportRequirement = SupportRequirement.SUPPORTED, impactLevel = ImpactLevel.NONE,
        equipmentAlternatives = listOf(listOf(StandardEquipment.BODYWEIGHT)), clearedTrainingConstraints = emptySet(),
        provenance = ReviewProvenance(null, "FICTIONAL TEST ONLY — NO CLINICAL APPROVAL", null, 1, 1),
        aiReviewProvenance = AiReviewProvenance("FICTIONAL TEST MODEL", 1L, "fictional-exercise", "a".repeat(64),
            listOf("https://example.invalid/fictional-review"), "FICTIONAL TEST ONLY", "NOT CLINICAL APPROVAL", 1, 1)
    )
    private fun rebindReview(root: JSONObject) {
        val record = root.getJSONArray("records").getJSONObject(0)
        record.getJSONObject("review").put("reviewedContentSha256", fixtureDigest(record.getJSONObject("content")))
    }
    private fun mutateContent(change: (JSONObject) -> Unit) {
        val root = JSONObject(fixture())
        val record = root.getJSONArray("records").getJSONObject(0)
        change(record.getJSONObject("content"))
        rebindReview(root)
        reject(root.toString())
    }
}
