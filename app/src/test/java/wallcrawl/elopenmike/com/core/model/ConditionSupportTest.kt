package wallcrawl.elopenmike.com.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ConditionSupportTest {
    @Test fun supportiveRequirementsRequireExplicitValidEquipmentAndImpact() {
        val requirements = ConditionSupportiveRequirements(emptySet(), emptySet(), ImpactLevel.LOW, listOf(emptyList()))
        assertThat(requirements.equipmentAlternatives).containsExactly(emptyList<String>())
        assertThrows(IllegalArgumentException::class.java) {
            ConditionSupportiveRequirements(emptySet(), setOf(TrainingConstraint.LOW_IMPACT_ONLY), ImpactLevel.LOW, listOf(emptyList()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ConditionSupportiveRequirements(emptySet(), emptySet(), ImpactLevel.LOW, emptyList())
        }
    }

    @Test fun legacyPrescriptionsRemainOrdinaryAndSupportiveUsesExistingValidation() {
        val ordinary = ExercisePrescription(ExerciseType.DURATION, 1, targetDurationSeconds = 20)
        assertThat(ordinary.purpose).isEqualTo(PrescriptionPurpose.ORDINARY)
        assertThat(ordinary.copy(purpose = PrescriptionPurpose.SUPPORTIVE).targetDurationSeconds).isEqualTo(20)
        assertThrows(IllegalArgumentException::class.java) {
            ExercisePrescription(ExerciseType.DURATION, 1, purpose = PrescriptionPurpose.SUPPORTIVE)
        }
    }

    @Test fun ordinaryBoundsRejectInvertedOrOutOfPrescriptionRangeValues() {
        assertThrows(IllegalArgumentException::class.java) { ConditionPrescriptionBounds(minSets = 3, maxSets = 2) }
        assertThrows(IllegalArgumentException::class.java) { ConditionPrescriptionBounds(maxSets = 21) }
        assertThrows(IllegalArgumentException::class.java) { ConditionPrescriptionBounds(maxLoadKg = Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { ConditionPrescriptionBounds(maxDurationSeconds = 86_401) }
        assertThrows(IllegalArgumentException::class.java) { ConditionPrescriptionBounds(maxReps = 1_001) }
    }

    @Test fun unknownOptionalAnswersStayUnknownAndAnswerTokensAreExact() {
        val selection = SelectedCondition("fictional-condition", 1, mapOf("side" to "LEFT", "stage" to null))
        assertThat(selection.answers["stage"]).isNull()
        assertThat(selection.answers["side"]).isEqualTo("LEFT")
        assertThrows(IllegalArgumentException::class.java) { SelectedCondition("fictional-condition", 0, emptyMap()) }
    }

    @Test fun selectedAnswersAreFrozenAtConstruction() {
        val answers = mutableMapOf<String, String?>("stage" to null)
        val selected = SelectedCondition("fictional-condition", 1, answers)
        answers["stage"] = "STABLE"
        assertThat(selected.answers["stage"]).isNull()
    }

    @Test fun sessionFactsCannotChangeThroughCallerOwnedCollections() {
        val selections = mutableListOf(SelectedCondition("fictional-condition", 1, emptyMap()))
        val versions = mutableMapOf("fictional-condition" to "fictional-v1")
        val context = SessionConditionContext(1, 1, selections, versions, emptyList(), emptyList(), emptyList())
        val digest = context.canonicalDigest
        selections.clear(); versions.clear()
        assertThat(context.selectedConditions).hasSize(1)
        assertThat(context.canonicalDigest).isEqualTo(digest)
    }

    @Test fun frozenSelectionsAndSessionContextsCompareByValue() {
        val first = SelectedCondition("fictional-condition", 1, mapOf("stage" to null))
        val second = SelectedCondition("fictional-condition", 1, mapOf("stage" to null))
        assertThat(first).isEqualTo(second)
        assertThat(first.hashCode()).isEqualTo(second.hashCode())
        val left = SessionConditionContext(1, 1, listOf(first), mapOf("fictional-condition" to "v1"), emptyList(), emptyList(), emptyList())
        val right = SessionConditionContext(1, 1, listOf(second), mapOf("fictional-condition" to "v1"), emptyList(), emptyList(), emptyList())
        assertThat(left).isEqualTo(right)
        assertThat(left.hashCode()).isEqualTo(right.hashCode())
    }

    @Test fun sharedReferenceCollectionsAreBounded() {
        assertThrows(IllegalArgumentException::class.java) {
            ConditionViolation("unsupported", (1..33).map { "condition-$it" }.toSet())
        }
        val references = (1..513).map { ConditionRuleReference("fictional-condition", "v1", "case", "rule-$it", 1) }.toSet()
        assertThrows(IllegalArgumentException::class.java) {
            ConditionSupportivePrescription("fictional-exercise", ExercisePrescription(ExerciseType.DURATION, 1, targetDurationSeconds = 20, purpose = PrescriptionPurpose.SUPPORTIVE), "execution-v1", references, ConditionSchedule(1, 0, ConditionText("Fictional", "Ficticio")))
        }
    }

    @Test fun bilingualTextRequiresBothLanguages() {
        assertThrows(IllegalArgumentException::class.java) { ConditionText("", "Prueba ficticia") }
        assertThrows(IllegalArgumentException::class.java) { ConditionText("Fictional test", " ") }
    }

    @Test fun schedulesCannotClaimAnUnboundedOrImpossibleFrequency() {
        assertThrows(IllegalArgumentException::class.java) { ConditionSchedule(0, 0, ConditionText("Test", "Prueba")) }
        assertThrows(IllegalArgumentException::class.java) { ConditionSchedule(8, 0, ConditionText("Test", "Prueba")) }
        assertThrows(IllegalArgumentException::class.java) { ConditionSchedule(2, -1, ConditionText("Test", "Prueba")) }
    }

    @Test fun recurringScheduleIntervalsMustFitTheWholeWeeklyCycle() {
        val instructions = ConditionText("FICTIONAL schedule", "Programa FICTICIO")
        listOf(1 to 168, 3 to 56, 7 to 24, 7 to 0).forEach { (frequency, spacing) ->
            assertThat(ConditionSchedule(frequency, spacing, instructions).minimumHoursBetweenSessions).isEqualTo(spacing)
        }
        listOf(7 to 168, 7 to 25, 2 to 85).forEach { (frequency, spacing) ->
            assertThrows(IllegalArgumentException::class.java) { ConditionSchedule(frequency, spacing, instructions) }
        }
    }

    @Test fun reviewedExecutionRequirementsChangeConditionContentIdentity() {
        val metadata = fictionalReviewedMetadata()
        val exercise = Exercise("fictional-exercise", name = "FICTIONAL EXERCISE", primaryMuscles = listOf("Core"), type = ExerciseType.WEIGHT_REPS, reviewedMetadata = metadata)
        val original = ConditionExerciseIdentity.from(exercise, "b".repeat(64)).contentSha256
        listOf(
            metadata.copy(equipmentAlternatives = listOf(listOf(StandardEquipment.WALL))),
            metadata.copy(supportRequirement = SupportRequirement.UNSUPPORTED),
            metadata.copy(impactLevel = ImpactLevel.HIGH)
        ).forEach { changed ->
            assertThat(ConditionExerciseIdentity.from(exercise.copy(reviewedMetadata = changed), "b".repeat(64)).contentSha256).isNotEqualTo(original)
        }
    }

    @Test fun reviewedMetadataVersionsAndContentBindingChangeConditionIdentity() {
        val metadata = fictionalReviewedMetadata()
        val exercise = Exercise("fictional-exercise", name = "FICTIONAL EXERCISE", primaryMuscles = listOf("Core"), type = ExerciseType.WEIGHT_REPS, reviewedMetadata = metadata)
        val original = ConditionExerciseIdentity.from(exercise, "b".repeat(64)).contentSha256
        listOf(
            metadata.copy(provenance = metadata.provenance.copy(schemaVersion = 2)),
            metadata.copy(provenance = metadata.provenance.copy(policyVersion = 2)),
            metadata.copy(aiReviewProvenance = metadata.aiReviewProvenance!!.copy(reviewedContentSha256 = "c".repeat(64))),
            metadata.copy(aiReviewProvenance = metadata.aiReviewProvenance!!.copy(policyVersion = 2))
        ).forEach { changed ->
            assertThat(ConditionExerciseIdentity.from(exercise.copy(reviewedMetadata = changed), "b".repeat(64)).contentSha256).isNotEqualTo(original)
        }
    }

    @Test fun sessionDigestIsCanonicalAndChangesWhenUnknownAnswerBecomesKnown() {
        fun context(answers: Map<String, String?>) = SessionConditionContext(
            formatVersion = 1, policyVersion = 1,
            selectedConditions = listOf(SelectedCondition("fictional-condition", 1, answers)),
            sourceSupportVersions = mapOf("fictional-condition" to "fictional-v1"),
            appliedRules = emptyList(), consumedScheduleEvidence = emptyList(), consumedReassessmentEventIds = emptyList()
        )
        val first = context(linkedMapOf("stage" to null, "side" to "LEFT"))
        assertThat(first.canonicalDigest).isEqualTo(context(linkedMapOf("side" to "LEFT", "stage" to null)).canonicalDigest)
        assertThat(first.canonicalDigest).isNotEqualTo(context(mapOf("side" to "LEFT", "stage" to "STABLE")).canonicalDigest)
        assertThat(first.canonicalDigest).matches("[0-9a-f]{64}")
    }

    @Test fun distinctUtf16ContentCannotCollapseToTheSameDigest() {
        val exercise = Exercise("fictional-exercise", name = "\uD800", primaryMuscles = listOf("Core"), type = ExerciseType.DURATION)
        val first = ConditionExerciseIdentity.from(exercise, "b".repeat(64))
        val replacement = ConditionExerciseIdentity.from(exercise.copy(name = "?"), "b".repeat(64))
        assertThat(first.contentSha256).isNotEqualTo(replacement.contentSha256)
    }

    @Test fun exerciseContentIdentityChangesWithExecutionTypeEquipmentOrSource() {
        val exercise = Exercise("fictional-exercise", name = "Fictional only", primaryMuscles = listOf("Core"), type = ExerciseType.DURATION)
        val identity = ConditionExerciseIdentity.from(exercise, "b".repeat(64))
        assertThat(identity).isNotEqualTo(ConditionExerciseIdentity.from(exercise.copy(type = ExerciseType.BODYWEIGHT_REPS), "b".repeat(64)))
        assertThat(identity).isNotEqualTo(ConditionExerciseIdentity.from(exercise.copy(listedEquipment = listOf("Wall")), "b".repeat(64)))
        assertThat(identity).isNotEqualTo(ConditionExerciseIdentity.from(exercise.copy(name = "Changed execution"), "b".repeat(64)))
        val source = ExerciseSource("fictional-catalog", "fictional-source-id", "fictional-slug",
            ExerciseAttribution("FICTIONAL CREATOR", "https://example.invalid", "FICTIONAL LICENSE", "https://example.invalid/license"))
        assertThat(identity.contentSha256).isNotEqualTo(ConditionExerciseIdentity.from(exercise.copy(source = source), "b".repeat(64)).contentSha256)
    }

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
}
