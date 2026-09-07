package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.AdaptationState
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

    private fun context(
        weekStartEpochDay: Long = MONDAY_EPOCH_DAY,
        timeZoneId: String = "America/Mexico_City",
        adaptationState: AdaptationState = AdaptationState.UNCALIBRATED,
        withProgramState: Boolean = true
    ): WorkoutGenerationContext = WorkoutGenerationContext(
        userProfile = UserProfile(id = "profile-under-test", revision = 3),
        completedWorkoutCount = 2,
        allowedExercises = listOf(
            syntheticApprovedExercise(id = "incline-press", directPrimaryMuscle = "Chest"),
            syntheticApprovedExercise(id = "seated-row", directPrimaryMuscle = "Back")
        ),
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
}
