package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.AdaptationState
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.EligibilityReason
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.UserProfile

/** The actual bundle, with no synthetic approvals anywhere in it. */
class AiAcceptedCatalogTest {
    private val factory = PlannerFixtureContextFactory()
    private val exercises = factory.bundledCatalogProjection().exercises
    private val byId = exercises.associateBy(Exercise::id)
    private val partition = JSONObject(factory.readResourceText("ai-acceptance/partition-v2.json"))
    private val acceptedIds = partition.getJSONArray("aiAccepted").strings().toSet()
    private val pendingDraftIds = partition.getJSONArray("pendingWithDraft").strings().toSet()
    private val pendingAbsentIds = partition.getJSONArray("pendingWithoutMetadata").strings().toSet()
    private val outsideIds = partition.getJSONArray("outsideScope").strings().toSet()

    @Test
    fun actualBundleHasTheExactAuditedExhaustivePartitionAndZeroHumanApprovals() {
        val groups = listOf(acceptedIds, pendingDraftIds, pendingAbsentIds, outsideIds)
        assertThat(groups.map { it.size }).containsExactly(182, 29, 56, 35).inOrder()
        assertThat(groups.flatten()).containsNoDuplicates()
        assertThat(exercises.map(Exercise::id)).containsExactlyElementsIn(groups.flatten())
        assertThat(exercises.filter { it.acceptedMetadata() != null }.map(Exercise::id))
            .containsExactlyElementsIn(acceptedIds)
        assertThat(exercises.filter { it.reviewedMetadata?.reviewState == ReviewState.DRAFT }
            .map(Exercise::id)).containsExactlyElementsIn(pendingDraftIds)
        assertThat(exercises.filter { it.reviewedMetadata == null }.map(Exercise::id))
            .containsExactlyElementsIn(pendingAbsentIds + outsideIds)
        assertThat(exercises.any { it.reviewedMetadata?.reviewState == ReviewState.APPROVED }).isFalse()
        assertThat(PlannerFeatureFlags.PRODUCTION.reviewedCapabilityEligibility).isTrue()
    }

    @Test
    fun committedAuditReproducesThePartitionAndBindsTheActualReviewerAndTimestamp() {
        val auditPath = "docs/research/2026-09-13-ai-acceptance-audit.json"
        val reference = partition.getJSONObject("auditArtifact")
        assertThat(reference.getString("path")).isEqualTo(auditPath)
        val bytes = File("../$auditPath").readBytes()
        assertThat(bytes.size).isAtMost(64 * 1024)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertThat(digest).isEqualTo(reference.getString("sha256"))
        val audit = JSONObject(bytes.toString(Charsets.UTF_8))
        val report = audit.getJSONObject("report")
        val pending = report.getJSONObject("pending_withheld")
        val held = pending.getJSONObject("existingDrafts").keys().asSequence().toSet() +
            pending.getJSONObject("existingOmittedMetadata").keys().asSequence().toSet() +
            pending.getJSONObject("additionalAuditHolds").keys().asSequence().toSet()
        val outside = report.getJSONObject("outside_scope")
        val outsideScope = listOf("excluded_stretch", "excluded_distance_duration", "excluded_timed_conditioning")
            .flatMap { outside.getJSONArray(it).strings() }.toSet()
        assertThat(byId.keys - held - outsideScope).containsExactlyElementsIn(acceptedIds)
        assertThat(held).containsExactlyElementsIn(pendingDraftIds + pendingAbsentIds)
        assertThat(outsideScope).containsExactlyElementsIn(outsideIds)
        assertThat(audit.has("sourceAuditSha256")).isFalse()
        assertThat(audit.has("auditSha256")).isFalse()
        assertThat(partition.has("auditSha256")).isFalse()
        val programmingBytes = File("../tools/workout-guide/programming-overrides.json").readBytes()
        val programmingDigest = MessageDigest.getInstance("SHA-256").digest(programmingBytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertThat(audit.getString("currentProgrammingSha256")).isEqualTo(programmingDigest)
        assertThat(partition.getString("currentProgrammingSha256")).isEqualTo(programmingDigest)
        for (id in acceptedIds) {
            val provenance = requireNotNull(byId.getValue(id).reviewedMetadata?.aiReviewProvenance)
            assertThat(provenance.reviewerModelId).isEqualTo(audit.getString("reviewerModelId"))
            assertThat(provenance.reviewedAtEpochMillis).isEqualTo(audit.getLong("reviewedAtEpochMillis"))
        }
    }

    @Test
    fun projectionRetainsEveryAiProvenanceFieldFromTheGeneratedCatalog() {
        val raw = JSONObject(factory.readResourceText("workout-guide/catalog.json"))
            .getJSONArray("exercises")
        val rawById = (0 until raw.length()).map(raw::getJSONObject).associateBy { it.getString("id") }
        for (id in acceptedIds) {
            val metadata = requireNotNull(byId.getValue(id).reviewedMetadata)
            assertWithMessage(id).that(metadata.reviewState).isEqualTo(ReviewState.AI_ACCEPTED)
            val ai = requireNotNull(metadata.aiReviewProvenance)
            val bundled = rawById.getValue(id).getJSONObject("reviewedMetadata")
                .getJSONObject("aiReviewProvenance")
            assertThat(ai.reviewerModelId).isEqualTo("gpt-6-astra")
            assertThat(ai.reviewedContentId).isEqualTo(id)
            assertThat(ai.reviewedAtEpochMillis).isEqualTo(partition.getLong("auditMtimeEpochMillis"))
            assertThat(ai.reviewedContentSha256).isEqualTo(bundled.getString("reviewedContentSha256"))
            assertThat(ai.sourceReferences)
                .containsExactlyElementsIn(bundled.getJSONArray("sourceReferences").strings()).inOrder()
            assertThat(ai.sourceReferences.size).isIn(1..8)
            assertThat(ai.sourceReferences.all { it.startsWith("https://") }).isTrue()
            assertThat(ai.decisionRationale).isEqualTo(bundled.getString("decisionRationale"))
            assertThat(ai.limitations).isEqualTo(bundled.getString("limitations"))
            assertThat(ai.schemaVersion).isEqualTo(3)
            assertThat(ai.policyVersion).isEqualTo(2)
            assertThat(metadata.provenance.schemaVersion).isEqualTo(3)
            assertThat(metadata.provenance.policyVersion).isEqualTo(2)
        }
        exercises.mapNotNull { it.reviewedMetadata }.forEach { metadata ->
            assertThat(metadata.provenance.reviewerRole).isNull()
            assertThat(metadata.provenance.reviewedAtEpochMillis).isNull()
            assertThat(metadata.clearedTrainingConstraints).isEmpty()
        }
    }

    @Test
    fun allAcceptedRecordsAreEligibleOnlyUnderAnExplicitUnrestrictedTestProfile() {
        val decisions = ExerciseEligibilityPolicy()
            .evaluate(exercises, fullInventoryProfile(), AdaptationState.BUILD,
                demonstratedProgressionFamilies = exercises.mapNotNullTo(mutableSetOf()) {
                    it.acceptedMetadata()?.progressionFamily
                }).decisions

        assertThat(decisions.filter { it.eligible }.map { it.exerciseId })
            .containsExactlyElementsIn(acceptedIds)
        decisions.filterNot { it.exerciseId in acceptedIds }.forEach {
            assertThat(it.reasons).contains(EligibilityReason.MISSING_APPROVED_METADATA)
        }
        for (constraint in TrainingConstraint.entries.filterNot { it == TrainingConstraint.LOW_IMPACT_ONLY }) {
            val constrained = ExerciseEligibilityPolicy().evaluate(
                exercises, fullInventoryProfile().copy(trainingConstraints = setOf(constraint)),
                AdaptationState.BUILD
            ).decisions
            assertWithMessage(constraint.name).that(constrained.none { it.eligible }).isTrue()
            constrained.filter { it.exerciseId in acceptedIds }.forEach {
                assertThat(it.reasons).contains(EligibilityReason.UNMAPPED_TRAINING_CONSTRAINT)
            }
        }
    }

    @Test
    fun sevenAcceptedSourcesRetainAuthorizedEdgesWithoutAcceptingPendingEndpoints() {
        val sources = setOf(
            "archer-push-up", "dumbbell-bent-over-row", "hindu-push-up",
            "hip-adduction-machine", "pistol-squat", "push-up", "seated-row"
        )
        sources.forEach { assertWithMessage(it).that(byId.getValue(it).acceptedMetadata()).isNotNull() }
        val edgesToPending = acceptedIds.flatMap { id ->
            val metadata = requireNotNull(byId.getValue(id).acceptedMetadata())
            (metadata.approvedRegressions + metadata.approvedSubstitutions)
                .filter { byId.getValue(it.exerciseId).acceptedMetadata() == null }
                .map { id to it.exerciseId }
        }
        assertThat(edgesToPending).containsExactly(
            "dumbbell-bent-over-row" to "machine-row",
            "hip-adduction-machine" to "cable-standing-hip-adduction",
            "pistol-squat" to "assisted-pistol-squat",
            "push-up" to "knee-push-up",
            "seated-row" to "machine-row"
        )
        edgesToPending.forEach { (_, target) ->
            assertThat(byId.getValue(target).reviewedMetadata?.reviewState).isEqualTo(ReviewState.DRAFT)
        }
        listOf(
            "cable-kickback", "cable-standing-hip-abduction",
            "cable-standing-hip-adduction", "fire-hydrant"
        ).forEach { id ->
            assertThat(byId.getValue(id).reviewedMetadata?.reviewState).isEqualTo(ReviewState.DRAFT)
            assertThat(byId.getValue(id).reviewedMetadata?.aiReviewProvenance).isNull()
        }
    }

    @Test
    fun pendingRegressionOpensNoComplexityExceptionOrCapabilityInheritance() {
        val decisions = ExerciseEligibilityPolicy().evaluate(
            exercises, fullInventoryProfile(), AdaptationState.UNCALIBRATED
        ).decisions.associateBy { it.exerciseId }
        assertThat(byId.getValue("pistol-squat").acceptedMetadata()).isNotNull()
        assertThat(decisions.getValue("pistol-squat").reasons)
            .contains(EligibilityReason.ADVANCED_WHILE_UNCALIBRATED)
        assertThat(decisions.getValue("assisted-pistol-squat").reasons)
            .contains(EligibilityReason.MISSING_APPROVED_METADATA)

        for (source in listOf("dumbbell-bent-over-row", "pistol-squat", "push-up")) {
            val evidence = CapabilityEvidencePolicy().derive(
                manageableSessionsFor(byId.getValue(source)), exercises
            )
            assertThat(evidence.records.keys).containsExactly(source)
        }
        // Positive control: the same history can propagate across an accepted endpoint.
        val acceptedEdgeEvidence = CapabilityEvidencePolicy().derive(
            manageableSessionsFor(byId.getValue("archer-push-up")), exercises
        )
        assertThat(acceptedEdgeEvidence.records.keys).containsExactly("archer-push-up", "push-up")
    }

    private fun fullInventoryProfile() = UserProfile(
        availableEquipment = exercises.flatMap { exercise ->
            exercise.reviewedMetadata?.equipmentAlternatives.orEmpty().flatten()
        }.distinct(),
        movementCapabilities = MovementCapabilities.from(
            MovementCapabilityType.entries.associateWith { CapabilityLevel.COMFORTABLE }
        )
    )

    private fun manageableSessionsFor(exercise: Exercise) = (1..2).map { index ->
        val weight = if (exercise.type == ExerciseType.WEIGHT_REPS) 10.0 else null
        completedSession(
            id = "audited-$index",
            completedAtEpochMillis = 1789278919309L + index,
            exercises = listOf(
                exerciseInstance(
                    exerciseId = exercise.id,
                    id = "${exercise.id}-$index",
                    sets = listOf(completedNormalSet(id = "set-$index", feltManageable = true).copy(
                        exerciseType = exercise.type, targetWeight = weight, completedWeight = weight
                    ))
                ).copy(prescription = ExercisePrescription(
                    exerciseType = exercise.type, targetSets = 1,
                    repRange = RepRange(8, 10), targetWeight = weight
                ))
            )
        )
    }

    private fun JSONArray.strings() = (0 until length()).map(::getString)
}
