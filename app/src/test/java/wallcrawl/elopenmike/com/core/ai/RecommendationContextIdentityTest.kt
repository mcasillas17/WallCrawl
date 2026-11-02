package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test
import wallcrawl.elopenmike.com.core.conditions.ConditionGuidance
import wallcrawl.elopenmike.com.core.conditions.ConditionRestrictions
import wallcrawl.elopenmike.com.core.model.AdaptationState
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.TrainingProgramState
import wallcrawl.elopenmike.com.core.model.TrainingProgramStatePolicyVersion
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeeklyDoseLedger
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext

/**
 * The freshness digest has to move for every input that can make a displayed
 * recommendation wrong, and stay still for everything else. Both halves are load-bearing:
 * a digest that never moves silently starts stale plans, and one that moves for a display
 * language throws away valid ones.
 */
class RecommendationContextIdentityTest {
    @Test
    fun directFrequencyAndCanonicalSchedulingDatesZoneAndWindowChangeIdentity() {
        val original = context().copy(schedulingEvidence =
            wallcrawl.elopenmike.com.core.model.TrainingFrequencyRecencyEvidence(
                20_000, "UTC", mapOf("Chest" to listOf(19_996, 19_997))
            ))
        val changes = listOf(
            original.copy(userProfile = original.userProfile.copy(daysPerWeek = 6)),
            original.copy(trainingFrequencyDaysPerWeek = 6),
            original.copy(schedulingEvidence = wallcrawl.elopenmike.com.core.model.TrainingFrequencyRecencyEvidence(
                20_000, "UTC", mapOf("Chest" to listOf(19_996, 19_998))
            )),
            original.copy(schedulingEvidence = wallcrawl.elopenmike.com.core.model.TrainingFrequencyRecencyEvidence(
                20_001, "UTC", mapOf("Chest" to listOf(19_996, 19_997))
            )),
            original.copy(schedulingEvidence = wallcrawl.elopenmike.com.core.model.TrainingFrequencyRecencyEvidence(
                20_000, "Europe/Madrid", mapOf("Chest" to listOf(19_996, 19_997))
            ))
        )
        changes.forEach {
            assertThat(RecommendationContextIdentity.of(it)).isNotEqualTo(RecommendationContextIdentity.of(original))
        }
    }

    @Test
    fun identicalContexts_produceTheSameIdentity() {
        assertThat(RecommendationContextIdentity.of(context()))
            .isEqualTo(RecommendationContextIdentity.of(context()))
    }

    @Test
    fun aChangedProfileRevision_changesTheIdentity() {
        val edited = context().let { original ->
            original.copy(userProfile = original.userProfile.copy(revision = 9))
        }

        assertThat(RecommendationContextIdentity.of(edited))
            .isNotEqualTo(RecommendationContextIdentity.of(context()))
    }

    @Test
    fun newlyCompletedHistory_changesTheIdentity() {
        assertThat(RecommendationContextIdentity.of(context().copy(completedWorkoutCount = 4)))
            .isNotEqualTo(RecommendationContextIdentity.of(context()))
    }

    @Test
    fun aChangedCandidateSet_changesTheIdentity() {
        val narrowed = context().let { original ->
            original.copy(allowedExercises = original.allowedExercises.take(1))
        }

        assertThat(RecommendationContextIdentity.of(narrowed))
            .isNotEqualTo(RecommendationContextIdentity.of(context()))
    }

    @Test
    fun aReorderedCandidateSet_changesTheIdentity() {
        val reordered = context().let { original ->
            original.copy(allowedExercises = original.allowedExercises.reversed())
        }

        // Candidate order is an input to selection, so a different order is a different
        // context even when the same exercises are legal.
        assertThat(RecommendationContextIdentity.of(reordered))
            .isNotEqualTo(RecommendationContextIdentity.of(context()))
    }

    @Test
    fun aNewCatalogOrReviewPolicy_changesTheIdentity() {
        assertThat(RecommendationContextIdentity.of(context().copy(catalogVersion = "other")))
            .isNotEqualTo(RecommendationContextIdentity.of(context()))
        assertThat(RecommendationContextIdentity.of(context().copy(reviewPolicyVersion = 2)))
            .isNotEqualTo(RecommendationContextIdentity.of(context()))
    }

    @Test
    fun aCrossedWeekBoundary_changesTheIdentity() {
        val nextWeek = context(weekStartEpochDay = MONDAY_EPOCH_DAY + 7)

        assertThat(RecommendationContextIdentity.of(nextWeek))
            .isNotEqualTo(RecommendationContextIdentity.of(context()))
    }

    @Test
    fun aChangedTimeZone_changesTheIdentity() {
        val elsewhere = context(timeZoneId = "Europe/Madrid")

        assertThat(RecommendationContextIdentity.of(elsewhere))
            .isNotEqualTo(RecommendationContextIdentity.of(context()))
    }

    @Test
    fun aChangedAdaptationState_changesTheIdentity() {
        val returning = context(adaptationState = AdaptationState.RETURNING)

        assertThat(RecommendationContextIdentity.of(returning))
            .isNotEqualTo(RecommendationContextIdentity.of(context()))
    }

    @Test
    fun enablingTheReviewedPath_changesTheIdentity() {
        assertThat(RecommendationContextIdentity.of(context(withProgramState = false)))
            .isNotEqualTo(RecommendationContextIdentity.of(context()))
    }

    @Test
    fun aTranslatedExerciseNameDoesNotChangeTheIdentity() {
        val translated = context().let { original ->
            original.copy(
                allowedExercises = original.allowedExercises.map { exercise ->
                    exercise.copy(name = "Prensa de banca inclinada")
                }
            )
        }

        assertThat(RecommendationContextIdentity.of(translated))
            .isEqualTo(RecommendationContextIdentity.of(context()))
    }

    @Test
    fun theDefaultLocaleDoesNotChangeTheIdentity() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("es-MX"))
            val spanish = RecommendationContextIdentity.of(context())
            Locale.setDefault(Locale.ENGLISH)
            val english = RecommendationContextIdentity.of(context())

            assertThat(spanish).isEqualTo(english)
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun acceptedAndPendingCandidates_produceDifferentIdentitiesWithoutLosingHistory() {
        // An accepted candidate is a candidate; a pending one never reaches the list at all,
        // so the two contexts can never be mistaken for the same one. Reviewed content and
        // policy travel with the catalog commit and the review-policy version, both of which
        // are hashed, and the completed-history count stays untouched by either.
        val aiAccepted = context(
            candidates = listOf(
                syntheticAiAcceptedExercise(id = "incline-press", directPrimaryMuscle = "Chest")
            )
        )
        val humanApproved = context(
            candidates = listOf(
                syntheticApprovedExercise(id = "incline-press", directPrimaryMuscle = "Chest")
            )
        )
        val pending = context(candidates = emptyList())

        assertThat(RecommendationContextIdentity.of(aiAccepted))
            .isNotEqualTo(RecommendationContextIdentity.of(humanApproved))
        assertThat(RecommendationContextIdentity.of(aiAccepted))
            .isNotEqualTo(RecommendationContextIdentity.of(pending))
        assertThat(RecommendationContextIdentity.of(aiAccepted.copy(catalogVersion = "later-commit")))
            .isNotEqualTo(RecommendationContextIdentity.of(aiAccepted))
        assertThat(RecommendationContextIdentity.of(aiAccepted.copy(reviewPolicyVersion = 2)))
            .isNotEqualTo(RecommendationContextIdentity.of(aiAccepted))
        assertThat(aiAccepted.completedWorkoutCount)
            .isEqualTo(humanApproved.completedWorkoutCount)
    }

    @Test
    fun changedAcceptedMetadataContent_changesTheIdentityWithoutChangingTheCatalogCommit() {
        val original = context(
            candidates = listOf(
                syntheticAiAcceptedExercise(id = "incline-press", directPrimaryMuscle = "Chest")
            )
        )
        val changed = original.copy(
            allowedExercises = listOf(
                syntheticAiAcceptedExercise(id = "incline-press", directPrimaryMuscle = "Back")
            )
        )

        assertThat(changed.catalogVersion).isEqualTo(original.catalogVersion)
        assertThat(RecommendationContextIdentity.of(changed))
            .isNotEqualTo(RecommendationContextIdentity.of(original))
    }

    private fun withConditions(conditions: Set<String>, overlayVersion: String): WorkoutGenerationContext =
        context().let { base ->
            base.copy(
                userProfile = base.userProfile.copy(reportedConditions = conditions),
                conditionRestrictions = ConditionRestrictions.Active(
                    avoid = emptySet(), modify = emptySet(), conditionIds = conditions,
                    guidance = ConditionGuidance(overlayVersion, emptyMap(), emptyMap(), emptyMap(), emptyMap())
                )
            )
        }

    @Test
    fun identityChangesWhenConditionsChange() {
        val one = RecommendationContextIdentity.of(withConditions(setOf("a"), "v1"))
        assertThat(RecommendationContextIdentity.of(withConditions(setOf("a", "b"), "v1"))).isNotEqualTo(one)
        assertThat(RecommendationContextIdentity.of(withConditions(setOf("a"), "v1"))).isEqualTo(one)
        assertThat(RecommendationContextIdentity.of(withConditions(setOf("b", "a"), "v1")))
            .isEqualTo(RecommendationContextIdentity.of(withConditions(setOf("a", "b"), "v1")))
        // Unknown ids fail closed in planning; the unavailable overlay is its own state.
        val unavailable = context().let {
            it.copy(
                userProfile = it.userProfile.copy(reportedConditions = setOf("a")),
                conditionRestrictions = ConditionRestrictions.Unavailable
            )
        }
        assertThat(RecommendationContextIdentity.of(unavailable)).isNotEqualTo(one)
    }

    @Test
    fun identityChangesWhenOverlayVersionChanges() {
        assertThat(RecommendationContextIdentity.of(withConditions(setOf("a"), "v2")))
            .isNotEqualTo(RecommendationContextIdentity.of(withConditions(setOf("a"), "v1")))
    }

    @Test
    fun aProfileWithoutConditionsDoesNotDependOnTheOverlayAndKeepsItsDigest() {
        // Pinned from the build before conditions existed: this release must not invalidate
        // the plans such a profile already displays.
        assertThat(RecommendationContextIdentity.of(context()))
            .isEqualTo(PRE_CONDITIONS_DIGEST)
    }

    private fun context(
        weekStartEpochDay: Long = MONDAY_EPOCH_DAY,
        timeZoneId: String = "America/Mexico_City",
        adaptationState: AdaptationState = AdaptationState.UNCALIBRATED,
        withProgramState: Boolean = true,
        candidates: List<wallcrawl.elopenmike.com.core.model.Exercise> = listOf(
            syntheticApprovedExercise(id = "incline-press", directPrimaryMuscle = "Chest"),
            syntheticApprovedExercise(id = "seated-row", directPrimaryMuscle = "Back")
        )
    ): WorkoutGenerationContext = WorkoutGenerationContext(
        userProfile = UserProfile(id = "profile-under-test", revision = 3),
        completedWorkoutCount = 2,
        allowedExercises = candidates,
        catalogVersion = "catalog-commit",
        reviewPolicyVersion = 1,
        trainingProgramState = if (withProgramState) {
            TrainingProgramState(
                policyVersion = TrainingProgramStatePolicyVersion.PROGRAM_STATE_V1,
                adaptationState = adaptationState,
                weeklyLedger = WeeklyDoseLedger(
                    policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
                    weekStartEpochDay = weekStartEpochDay,
                    timeZoneId = timeZoneId,
                    catalogVersion = "catalog-commit",
                    reviewPolicyVersion = 1,
                    directPrimarySets = emptyMap(),
                    secondaryInvolvement = emptyMap(),
                    unattributedWorkSets = emptyMap()
                )
            )
        } else {
            null
        }
    )

    private fun withHistory(hold: Int?): WorkoutGenerationContext {
        val session = WorkoutSession(
            id = "done-1", name = "Done", startedAtTimestamp = 1_000L, completedAtTimestamp = 2_000L,
            status = SessionStatus.COMPLETED,
            exercises = listOf(
                WorkoutExercise(
                    id = "ex-1", sessionId = "done-1", exerciseId = "push-up", orderIndex = 0,
                    prescription = ExercisePrescription(
                        exerciseType = ExerciseType.BODYWEIGHT_REPS, targetSets = 2,
                        repRange = RepRange(8, 12), restSeconds = 60, holdSeconds = hold
                    )
                )
            )
        )
        return context().copy(progressionHistory = listOf(session), historyAsOfTimestamp = 10_000L)
    }

    @Test
    fun aHoldFreeHistoryKeepsItsDigestAndAHoldChangesIt() {
        // The expected value was produced by this code, not by the base commit: the only input that
        // differs from 29fb4927a is the prescription text, which is pinned to the base wording in
        // ExercisePrescriptionTest.absentHoldPrintsTheTextFromBeforeHoldsExisted. Together they
        // show the digest is what the base commit produced.
        assertThat(RecommendationContextIdentity.of(withHistory(null))).isEqualTo(HISTORY_DIGEST)
        assertThat(RecommendationContextIdentity.of(withHistory(30)))
            .isNotEqualTo(RecommendationContextIdentity.of(withHistory(null)))
        assertThat(RecommendationContextIdentity.of(withHistory(30)))
            .isNotEqualTo(RecommendationContextIdentity.of(withHistory(45)))
    }

    @Test
    fun mobilityRowsInProgressionHistoryDoNotChangeTheDigest() {
        val context = withHistory(null)
        val withMobility = context.copy(progressionHistory = context.progressionHistory.map { session ->
            session.copy(exercises = session.exercises + session.exercises.single().copy(
                id = "mobility-1", exerciseId = "clamshell", orderIndex = 1,
                role = wallcrawl.elopenmike.com.core.model.ExerciseRole.MOBILITY
            ))
        })
        assertThat(RecommendationContextIdentity.of(withMobility)).isEqualTo(HISTORY_DIGEST)
    }

    private companion object {
        const val HISTORY_DIGEST = "ccbb4205db360e82f329cd7b513add93cd382cd1e2ca0b0ed7882425fc343704"
        const val PRE_CONDITIONS_DIGEST = "5f74554467b20a16c606e31008e1500b297d4c940b0f2f52e4a434f73d6f45b2"
    }
}
