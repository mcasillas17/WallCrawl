package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.Locale
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.CapabilityEvidence
import wallcrawl.elopenmike.com.core.model.CapabilityEvidencePolicyVersion
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceReason
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceScope
import wallcrawl.elopenmike.com.core.model.CapabilityEvidenceSet
import wallcrawl.elopenmike.com.core.model.ComparableMovementShape
import wallcrawl.elopenmike.com.core.model.ExercisePerformanceHistory
import wallcrawl.elopenmike.com.core.model.LedgerOmissionReason
import wallcrawl.elopenmike.com.core.model.ProfileGender
import wallcrawl.elopenmike.com.core.model.RestClass
import wallcrawl.elopenmike.com.core.model.UserRestPreference
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext

class RecommendationHistoryIdentityTest {
    @Test
    fun sharedRepProjectionPreservesTheOriginalAllCompletedSetsComparison() {
        val sets = listOf(
            emptyList(),
            listOf(completedNormalSet("missing").copy(completedReps = null)),
            listOf(completedNormalSet("unfinished").copy(isCompleted = false)),
            listOf(completedNormalSet("low").copy(completedReps = 8), completedNormalSet("high").copy(completedReps = 15)),
            listOf(completedNormalSet("low-unfinished").copy(isCompleted = false, completedReps = 1),
                completedNormalSet("complete").copy(completedReps = 12))
        )
        for (recent in sets) {
            val performance = history(PRESS).copy(recentSets = recent)
            for (targetMaximum in 1..20) {
                val completed = recent.filter { it.isCompleted }
                val original = completed.isNotEmpty() && completed.all { (it.completedReps ?: 0) >= targetMaximum }
                assertThat(performance.minimumCompletedReps()?.let { it >= targetMaximum } == true).isEqualTo(original)
            }
        }
    }

    @Test
    fun nonMinimumRepsAndMissingVersusZeroComparisonDoNotCreateFalseInputChanges() {
        val original = context()
        val performance = original.exerciseHistory.getValue(PRESS)
        val higher = original.copy(exerciseHistory = mapOf(PRESS to performance.copy(
            recentSets = performance.recentSets.map { set ->
                if (set.completedReps == 12) set.copy(completedReps = 15) else set
            }
        )))
        assertThat(RecommendationContextIdentity.of(higher)).isEqualTo(RecommendationContextIdentity.of(original))
        val missing = original.copy(exerciseHistory = mapOf(PRESS to performance.copy(
            recentSets = listOf(performance.recentSets.first().copy(completedReps = null))
        )))
        val zero = original.copy(exerciseHistory = mapOf(PRESS to performance.copy(
            recentSets = listOf(performance.recentSets.first().copy(completedReps = 0))
        )))
        assertThat(RecommendationContextIdentity.of(missing)).isEqualTo(RecommendationContextIdentity.of(zero))
    }
    @Test
    fun everyConsumedDerivedHistoryInputChangesIdentityAtEqualCountAndCandidates() {
        val original = context()
        val performance = original.exerciseHistory.getValue(PRESS)
        val state = checkNotNull(original.trainingProgramState)
        val ledger = state.weeklyLedger
        val changes = linkedMapOf(
            "capability membership" to original.copy(capabilityEvidence = evidence()),
            "latest usable load" to original.copy(exerciseHistory = mapOf(PRESS to performance.copy(lastWeight = 60.0))),
            "completed rep floor" to original.copy(exerciseHistory = mapOf(PRESS to performance.copy(
                recentSets = performance.recentSets.map { it.copy(completedReps = 15) }
            ))),
            "no completed sets" to original.copy(exerciseHistory = mapOf(PRESS to performance.copy(
                recentSets = performance.recentSets.map { it.copy(isCompleted = false) }
            ))),
            "history load units" to original.copy(preferredUnits = WeightUnit.KG),
            "confirmed load provenance" to original.copy(userProfile = original.userProfile.copy(
                confirmedStartingLoads = mapOf(PRESS to 90.0)
            )),
            "rest preference presence" to original.copy(priorUserRestPreferences = mapOf(PRESS to UserRestPreference(RestClass.MODERATE, 90))),
            "direct weekly counts" to original.copy(trainingProgramState = state.copy(
                weeklyLedger = ledger.copy(directPrimarySets = mapOf("Chest" to 2))
            )),
            "ledger catalog provenance" to original.copy(trainingProgramState = state.copy(
                weeklyLedger = ledger.copy(catalogVersion = "another-catalog")
            )),
            "ledger review policy" to original.copy(trainingProgramState = state.copy(
                weeklyLedger = ledger.copy(reviewPolicyVersion = 2)
            )),
            "secondary integrity" to original.copy(trainingProgramState = state.copy(
                weeklyLedger = ledger.copy(secondaryInvolvement = mapOf("Back" to 0))
            )),
            "omission integrity" to original.copy(trainingProgramState = state.copy(
                weeklyLedger = ledger.copy(unattributedWorkSets = mapOf(LedgerOmissionReason.UNKNOWN_EXERCISE to 0))
            ))
        )
        for ((label, changed) in changes) {
            assertThat(changed.completedWorkoutCount).isEqualTo(original.completedWorkoutCount)
            assertThat(changed.allowedExercises).isEqualTo(original.allowedExercises)
            assertThat(changed.schedulingEvidence).isEqualTo(original.schedulingEvidence)
            assertWithMessage(label).that(RecommendationContextIdentity.of(changed))
                .isNotEqualTo(RecommendationContextIdentity.of(original))
        }
    }

    @Test
    fun bothRestFieldsAndDefaultVersusExplicitProvenanceAreInputs() {
        val original = context().copy(priorUserRestPreferences = mapOf(PRESS to UserRestPreference(RestClass.MODERATE, 90)))
        for (preference in listOf(UserRestPreference(RestClass.LONG, 90), UserRestPreference(RestClass.MODERATE, 180))) {
            val changed = original.copy(priorUserRestPreferences = mapOf(PRESS to preference))
            assertThat(RecommendationContextIdentity.of(changed)).isNotEqualTo(RecommendationContextIdentity.of(original))
            assertThat(DefaultExercisePrescriptionFactory().create(original.allowedExercises.first(), changed))
                .isNotEqualTo(DefaultExercisePrescriptionFactory().create(original.allowedExercises.first(), original))
        }
        assertThat(RecommendationContextIdentity.of(original.copy(priorUserRestPreferences = emptyMap())))
            .isNotEqualTo(RecommendationContextIdentity.of(original))
    }

    @Test
    fun unusedPerformanceDetailsRawHistoryAndEvidenceProvenanceDoNotInvalidate() {
        val original = context().copy(capabilityEvidence = evidence())
        val performance = original.exerciseHistory.getValue(PRESS)
        val changed = original.copy(
            userProfile = original.userProfile.copy(name = "Other display name", gender = ProfileGender.WOMAN),
            recentWorkoutHistory = listOf(completedSession("unused-raw-session", 1000, emptyList()).copy(name = "Display text")),
            recentlyTrainedMuscles = listOf("Unread legacy summary"),
            exerciseHistory = mapOf(PRESS to performance.copy(
                lastReps = 99, bestEstimated1RM = 999.0,
                recentSets = performance.recentSets.map { it.copy(
                    id = "other-${it.id}", workoutExerciseId = "other-instance",
                    targetWeight = 200.0, completedWeight = 200.0, targetReps = 100,
                    rpe = 8f, rir = 2, feltManageable = false, completedAtTimestamp = 2000
                ) }.reversed()
            )),
            capabilityEvidence = CapabilityEvidenceSet.from(evidence().records.mapValues { (_, record) ->
                record.copy(
                    qualifyingSessionIds = listOf("other-a", "other-b"),
                    demonstratedExerciseId = "other-exercise",
                    scope = CapabilityEvidenceScope.DIRECT_APPROVED_REGRESSION
                )
            })
        )
        assertThat(RecommendationContextIdentity.of(changed)).isEqualTo(RecommendationContextIdentity.of(original))
        assertThat(DefaultExercisePrescriptionFactory().create(original.allowedExercises.first(), changed))
            .isEqualTo(DefaultExercisePrescriptionFactory().create(original.allowedExercises.first(), original))
    }

    @Test
    fun validAnalyticsCountsAndUnusedExerciseEntriesAreNotDecisionInputs() {
        val original = context()
        val state = checkNotNull(original.trainingProgramState)
        val changed = original.copy(
            exerciseHistory = original.exerciseHistory + ("not-a-candidate" to history("not-a-candidate")),
            priorUserRestPreferences = mapOf("not-a-candidate" to UserRestPreference(RestClass.LONG, 180)),
            trainingProgramState = state.copy(weeklyLedger = state.weeklyLedger.copy(
                secondaryInvolvement = mapOf("Back" to 10),
                unattributedWorkSets = mapOf(LedgerOmissionReason.UNKNOWN_EXERCISE to 12)
            ))
        )
        assertThat(RecommendationContextIdentity.of(changed)).isEqualTo(RecommendationContextIdentity.of(original))
    }

    @Test
    fun canonicalMapOrderAndLocalePreserveHistoryIdentity() {
        val original = context().copy(
            exerciseHistory = linkedMapOf(PRESS to history(PRESS), ROW to history(ROW)),
            capabilityEvidence = evidence(),
            priorUserRestPreferences = linkedMapOf(PRESS to UserRestPreference(RestClass.SHORT, 60), ROW to UserRestPreference(RestClass.LONG, 180))
        )
        val state = checkNotNull(original.trainingProgramState)
        val ordered = original.copy(trainingProgramState = state.copy(
            weeklyLedger = state.weeklyLedger.copy(directPrimarySets = linkedMapOf("Chest" to 1, "Back" to 2))
        ))
        val reversed = ordered.copy(
            exerciseHistory = ordered.exerciseHistory.entries.reversed().associate { it.toPair() },
            priorUserRestPreferences = ordered.priorUserRestPreferences.entries.reversed().associate { it.toPair() },
            trainingProgramState = ordered.trainingProgramState?.copy(
                weeklyLedger = ordered.trainingProgramState.weeklyLedger.copy(directPrimarySets = linkedMapOf("Back" to 2, "Chest" to 1))
            )
        )
        val locale = Locale.getDefault()
        try {
            val identity = RecommendationContextIdentity.of(ordered)
            for (language in listOf("en-US", "es-MX", "tr-TR")) {
                Locale.setDefault(Locale.forLanguageTag(language))
                assertThat(RecommendationContextIdentity.of(reversed)).isEqualTo(identity)
            }
        } finally {
            Locale.setDefault(locale)
        }
    }

    @Test
    fun changingTheMinimumCompletedRepsChangesTheActualPrescription() {
        val original = context()
        val performance = original.exerciseHistory.getValue(PRESS)
        val topped = original.copy(exerciseHistory = mapOf(PRESS to performance.copy(
            recentSets = performance.recentSets.map { it.copy(completedReps = 20) }
        )))
        val exercise = original.allowedExercises.first()
        assertThat(DefaultExercisePrescriptionFactory().create(exercise, topped).targetWeight)
            .isNotEqualTo(DefaultExercisePrescriptionFactory().create(exercise, original).targetWeight)
        assertThat(RecommendationContextIdentity.of(topped)).isNotEqualTo(RecommendationContextIdentity.of(original))
    }

    private fun context(): WorkoutGenerationContext = validatorContext(
        allowedExercises = listOf(
            syntheticApprovedExercise(PRESS, "Chest"),
            syntheticApprovedExercise(ROW, "Back")
        ), reviewedPath = true
    ).copy(exerciseHistory = mapOf(PRESS to history(PRESS)))

    private fun history(id: String) = ExercisePerformanceHistory(
        exerciseId = id, lastWeight = 50.25, lastReps = 12, bestEstimated1RM = 70.0,
        recentSets = listOf(completedNormalSet("$id-low").copy(completedReps = 8), completedNormalSet("$id-high").copy(completedReps = 12))
    )

    private fun evidence(): CapabilityEvidenceSet = CapabilityEvidenceSet.from(
        listOf(PRESS, ROW).associateWith { id ->
            CapabilityEvidence(
                CapabilityEvidencePolicyVersion.TWO_COMPARABLE_MANAGEABLE_SESSIONS_V1,
                CapabilityEvidenceReason.TWO_COMPARABLE_MANAGEABLE_COMPLETED_SESSIONS,
                id, id, CapabilityEvidenceScope.EXACT_EXERCISE, ComparableMovementShape.WEIGHT_REPETITIONS,
                listOf("session-a", "session-b")
            )
        }
    )

    private companion object {
        const val PRESS = "incline-press"
        const val ROW = "seated-row"
    }
}
