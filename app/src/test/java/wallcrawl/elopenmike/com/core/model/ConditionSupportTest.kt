package wallcrawl.elopenmike.com.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ConditionSupportTest {
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
}
