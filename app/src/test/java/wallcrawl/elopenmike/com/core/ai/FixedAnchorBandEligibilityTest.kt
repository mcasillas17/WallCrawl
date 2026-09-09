package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.json.JSONObject
import java.io.File
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.model.AdaptationState
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.EligibilityReason
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.ComplexityTier
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseLink
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.SupportRequirement
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.equipmentRequirements
import wallcrawl.elopenmike.com.core.model.fixedAnchorBandRequirements
import wallcrawl.elopenmike.com.core.model.hasUnresolvedEquipmentRequirements

class FixedAnchorBandEligibilityTest {
    private val catalog = PlannerFixtureContextFactory().bundledCatalogProjection().exercises
    private val anchored = catalog.filter { it.id in REQUIRED }

    @Test
    fun correctionsAreBoundToThePinnedCatalogAndCanonicalSchemaVocabulary() {
        assertThat(fixedAnchorBandRequirements.keys).containsExactlyElementsIn(REQUIRED.keys)
        assertThat(anchored).hasSize(6)
        anchored.forEach { exercise ->
            assertThat(exercise.listedEquipment).containsExactly(BAND)
            assertThat(exercise.equipmentRequirements)
                .containsExactlyElementsIn(REQUIRED.getValue(exercise.id)?.let(::listOf).orEmpty())
            assertThat(exercise.hasUnresolvedEquipmentRequirements)
                .isEqualTo(exercise.id == "banded-row")
        }
        val schemaEquipment = JSONObject(File("../tools/workout-guide/review-schema.json").readText())
            .getJSONObject("\$defs").getJSONObject("equipment").getJSONArray("enum")
        assertThat((0 until schemaEquipment.length()).map(schemaEquipment::getString))
            .containsExactlyElementsIn(StandardEquipment.ALL)
        assertThat(StandardEquipment.FULL_GYM).hasSize(19)
        assertThat(StandardEquipment.FULL_GYM.intersect(StandardEquipment.BAND_SETUPS.toSet()))
            .isEmpty()
    }

    @Test
    fun everyInventorySubset_requiresTheExactConfirmedConfigurationAndBand() {
        // Contract literals intentionally do not derive expected outcomes from the implementation.
        val selectable = listOf(BAND, UPPER, OVERHEAD, LOW, KICKBACK_SUPPORT, "Chair")
        for (bits in 0 until (1 shl selectable.size)) {
            val owned = selectable.filterIndexed { index, _ -> bits and (1 shl index) != 0 }
            val profile = UserProfile(availableEquipment = owned)
            val expected = REQUIRED.filter { (_, required) ->
                required != null && owned.containsAll(required)
            }.keys
            val legacy = ExerciseFilter().filterCandidates(anchored, profile)
            val reviewed = ExerciseEligibilityPolicy().evaluate(
                anchored.map(::syntheticApproval), profile, AdaptationState.BUILD
            )
            assertWithMessage("Legacy inventory: $owned").that(legacy.map(Exercise::id))
                .containsExactlyElementsIn(expected)
            assertWithMessage("Synthetic-reviewed inventory: $owned")
                .that(reviewed.decisions.filter { it.eligible }.map { it.exerciseId })
                .containsExactlyElementsIn(expected)
        }
    }

    @Test
    fun incidentalObjectsAndUnknownLabelsNeverEstablishAnAnchor() {
        val profile = UserProfile(availableEquipment = listOf(
            BAND, "Wall", "Doorway", "Chair", "Squat Rack", "Pull-up Bar", "Full gym",
            "Anchor", "Anclaje para banda", "Future Anchor",
            "band anchor - upper body", " Band Anchor - Overhead ", "BAND ANCHOR - LOW",
            "band kickback attachment and support"
        ))
        assertThat(ExerciseFilter().filterCandidates(anchored, profile)).isEmpty()
        assertThat(ExerciseEligibilityPolicy().evaluate(
            anchored.map(::syntheticApproval), profile, AdaptationState.BUILD
        ).decisions.all { EligibilityReason.MISSING_EQUIPMENT in it.reasons }).isTrue()
    }

    @Test
    fun incompleteSyntheticApprovalCannotBypassTheSourceBoundMinimum() {
        val incompleteApprovals = anchored.map { exercise ->
            syntheticApproval(exercise).let {
                it.copy(reviewedMetadata = it.reviewedMetadata!!.copy(
                    equipmentAlternatives = listOf(listOf(BAND))
                ))
            }
        }
        val result = ExerciseEligibilityPolicy().evaluate(
            incompleteApprovals, UserProfile(availableEquipment = listOf(BAND)),
            AdaptationState.BUILD
        )
        assertThat(result.decisions.all { EligibilityReason.MISSING_EQUIPMENT in it.reasons })
            .isTrue()
    }

    @Test
    fun exclusionsApprovalAndApplicableMovementConstraintsStillWin() {
        val profile = UserProfile(availableEquipment = listOf(
            BAND, UPPER, OVERHEAD, LOW, KICKBACK_SUPPORT, "Chair"
        ), excludedExerciseIds = REQUIRED.keys.toList())
        assertThat(ExerciseFilter().filterCandidates(anchored, profile)).isEmpty()
        assertThat(ExerciseEligibilityPolicy().evaluate(
            anchored.map(::syntheticApproval), profile, AdaptationState.BUILD
        ).decisions.all { EligibilityReason.USER_EXCLUDED in it.reasons }).isTrue()

        val notExcluded = profile.copy(excludedExerciseIds = emptyList())
        assertThat(ExerciseEligibilityPolicy().evaluate(
            anchored, notExcluded, AdaptationState.BUILD
        ).decisions.all { EligibilityReason.MISSING_APPROVED_METADATA in it.reasons }).isTrue()
        val draft = anchored.map(::syntheticApproval).map {
            it.copy(reviewedMetadata = it.reviewedMetadata!!.copy(reviewState = ReviewState.DRAFT))
        }
        assertThat(ExerciseEligibilityPolicy().evaluate(
            draft, notExcluded, AdaptationState.BUILD
        ).decisions.none { it.eligible }).isTrue()

        val standing = syntheticApproval(anchored.single { it.id == "banded-pallof-press" })
        val avoidBalance = notExcluded.copy(movementCapabilities = MovementCapabilities.from(
            mapOf(MovementCapabilityType.BALANCE_WITHOUT_SUPPORT to CapabilityLevel.AVOID)
        ))
        assertThat(ExerciseEligibilityPolicy().evaluate(
            listOf(standing), avoidBalance, AdaptationState.BUILD
        ).decisions.single().reasons).contains(EligibilityReason.CAPABILITY_AVOID)
        assertThat(ExerciseEligibilityPolicy().evaluate(
            listOf(standing),
            notExcluded.copy(trainingConstraints = setOf(TrainingConstraint.SHOULDER_SENSITIVE)),
            AdaptationState.BUILD
        ).decisions.single().reasons).contains(EligibilityReason.UNMAPPED_TRAINING_CONSTRAINT)
    }

    @Test
    fun bandOnlyCandidatesRemainUsableAndInputsStayUnchanged() {
        val profile = UserProfile(availableEquipment = listOf(BAND))
        val candidates = ExerciseFilter().filterCandidates(catalog, profile)
        assertThat(candidates.map(Exercise::id)).containsExactly(
            "band-pull-apart", "banded-clamshell", "banded-dead-bug", "banded-donkey-kick",
            "banded-fire-hydrant", "banded-frog-pump", "banded-glute-bridge",
            "banded-hip-thrust", "banded-lateral-walk", "banded-monster-walk",
            "banded-seated-hip-abduction", "banded-squat", "banded-standing-hip-abduction"
        )
        assertThat(profile.availableEquipment).containsExactly(BAND)
        assertThat(profile.confirmedStartingLoads).isEmpty()
        assertThat(catalog).hasSize(302)
        assertThat(catalog.count { it.reviewedMetadata?.reviewState == ReviewState.APPROVED })
            .isEqualTo(0)
        assertThat(PlannerFeatureFlags().reviewedCapabilityEligibility).isFalse()
    }

    @Test
    fun unavailableAnchorRegressionCannotLiftTheReviewedAdvancedCeiling() {
        val regression = syntheticApproval(anchored.single { it.id == "banded-pallof-press" })
            .let { it.copy(reviewedMetadata = it.reviewedMetadata!!.copy(
                supportRequirement = SupportRequirement.SUPPORTED,
                equipmentAlternatives = listOf(listOf(BAND))
            )) }
        val advanced = catalog.single { it.id == "band-pull-apart" }.let {
            it.copy(reviewedMetadata = regression.reviewedMetadata!!.copy(
                complexity = ComplexityTier.ADVANCED,
                approvedRegressions = listOf(ReviewedExerciseLink(regression.id))
            ))
        }
        val result = ExerciseEligibilityPolicy().evaluate(
            listOf(advanced, regression), UserProfile(availableEquipment = listOf(BAND)),
            AdaptationState.UNCALIBRATED
        )
        assertThat(result.decisions.first().reasons)
            .contains(EligibilityReason.ADVANCED_WHILE_UNCALIBRATED)
        assertThat(result.decisions.last().reasons).contains(EligibilityReason.MISSING_EQUIPMENT)
    }

    private fun syntheticApproval(exercise: Exercise): Exercise {
        // Synthetic equipment-policy approvals only, not proposals for these exercises' fields.
        val seed = catalog.single { it.id == "band-pull-apart" }.reviewedMetadata!!
        return exercise.copy(reviewedMetadata = seed.copy(
            reviewState = ReviewState.APPROVED,
            equipmentAlternatives = listOf(REQUIRED.getValue(exercise.id) ?: listOf(BAND)),
            capabilityRequirements = setOf(MovementCapabilityType.BALANCE_WITHOUT_SUPPORT),
            provenance = seed.provenance.copy(
                reviewerRole = "Synthetic test-only reviewer",
                rationaleOrSource = "SYNTHETIC equipment eligibility fixture; not human approval.",
                reviewedAtEpochMillis = 1L
            )
        ))
    }

    companion object {
        private const val BAND = "Resistance Band"
        private const val UPPER = "Band Anchor - Upper Body"
        private const val OVERHEAD = "Band Anchor - Overhead"
        private const val LOW = "Band Anchor - Low"
        private const val KICKBACK_SUPPORT = "Band Kickback Attachment and Support"
        private val REQUIRED = linkedMapOf(
            "banded-face-pull" to listOf(BAND, UPPER),
            "banded-kickback" to listOf(BAND, LOW, KICKBACK_SUPPORT),
            "banded-lat-pulldown" to listOf(BAND, OVERHEAD, "Chair"),
            "banded-pallof-press" to listOf(BAND, UPPER),
            "banded-row" to null,
            "banded-woodchop" to listOf(BAND, LOW)
        )
    }
}
