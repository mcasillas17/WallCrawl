package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.ZoneId
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.*

class ConditionRecommendationIdentityTest {
    private val f = ConditionPlannerFixtures
    private fun rebuild(c: WorkoutGenerationContext, history: ConditionHistory): WorkoutGenerationContext = c.copy(
        conditionHistory = history, conditionPlanning = ConditionPlanningPolicy().resolve(c.userProfile, c.allowedExercises,
            c.allowedExercises.associateBy { it.id }, c.conditionSupport!!, history))
    private fun identity(c: WorkoutGenerationContext) = RecommendationContextIdentity.of(c)

    @Test fun smallClockAdvanceIsFreshAndReceiptRemainsExact() = runTest {
        val c = f.context()
        val h = c.conditionHistory!!
        val later = rebuild(c, ConditionHistory(f.now + 1_000, h.zoneId, h.windowStartTimestamp, true, h.exposures, h.pending))
        assertThat(identity(later)).isEqualTo(identity(c))
        val validator = ProgramValidator(GeneratedWorkoutValidator(InMemoryExerciseCatalog(c.allowedExercises)))
        val plan = FakeWorkoutPlanner().generateWorkout(c)
        val generation = validator.validate(plan, c) as ProgramValidationResult.Valid
        assertThat(validator.validate(plan, later)).isInstanceOf(ProgramValidationResult.Valid::class.java)
        val frozen = (c.conditionPlanning as ConditionPlanningResult.Ready).context
        assertThat(generation.snapshot.asRecord("session", f.now + 1_000).conditionContextDigest).isEqualTo(frozen.canonicalDigest)
        assertThat(frozen.canonicalDigest).isNotEqualTo((later.conditionPlanning as ConditionPlanningResult.Ready).context.canonicalDigest)
    }

    @Test fun actualContentExecutionDoseAndHistoryChangeIdentity() {
        val c = f.context()
        val variants = listOf(f.context(supportContentSha256 = "d".repeat(64)), f.context(execution = "different"),
            f.context(supportiveDose = f.dose.copy(targetDurationSeconds = 21)),
            f.context(exposures = listOf(ConditionExposure("manual", f.supportive.id, f.now - 1))),
            c.copy(conditionPlanning = ConditionPlanningResult.Blocked(listOf(ConditionViolation("blocked", setOf("fictional-condition"))))),
            c.copy(conditionPlanning = ConditionPlanningResult.NotApplicable))
        variants.forEach { assertThat(identity(it)).isNotEqualTo(identity(c)) }
    }

    @Test fun eligibilityBoundaryAndCalendarOrZoneChangesInvalidate() = runTest {
        val c = f.context(exposures = listOf(ConditionExposure("previous", f.supportive.id, f.now - 23 * 3_600_000)))
        val h = c.conditionHistory!!
        val due = rebuild(c, ConditionHistory(f.now + 3_600_000, h.zoneId, h.windowStartTimestamp, true, h.exposures, emptyList()))
        assertThat(identity(due)).isNotEqualTo(identity(c))
        val validator = ProgramValidator(GeneratedWorkoutValidator(InMemoryExerciseCatalog(c.allowedExercises)))
        assertThat(validator.validate(FakeWorkoutPlanner().generateWorkout(c), due)).isInstanceOf(ProgramValidationResult.Invalid::class.java)
        val tomorrow = rebuild(c, ConditionHistory(f.now + 86_400_000, h.zoneId, h.windowStartTimestamp, true, h.exposures, emptyList()))
        assertThat(identity(tomorrow)).isNotEqualTo(identity(c))
        val zone = rebuild(c, ConditionHistory(f.now, ZoneId.of("America/Los_Angeles"), h.windowStartTimestamp, true, h.exposures, emptyList()))
        assertThat(identity(zone)).isNotEqualTo(identity(c))
    }

    @Test fun removedConditionPendingStateAndReassessmentFactsRemainInIdentity() {
        val c = f.context().let { it.copy(userProfile = it.userProfile.copy(activeConditions = emptyList()), conditionPlanning = ConditionPlanningResult.NotApplicable) }
        val h = c.conditionHistory!!
        val marker = PendingConditionReassessment(c.userProfile.id, "fictional-condition", f.supportive.id, "event", "a".repeat(64), 1, f.now - 1)
        val pending = rebuild(c, ConditionHistory(f.now, h.zoneId, h.windowStartTimestamp, true, emptyList(), listOf(marker)))
        assertThat(identity(pending)).isNotEqualTo(identity(c))
        assertThat(identity(pending.copy(conditionPlanning = ConditionPlanningResult.NotApplicable))).isNotEqualTo(identity(c))
    }
    @Test fun selectedAnswersReviewSourcesArtworkAndReassessmentAreBound() {
        val c = f.context()
        val record = c.conditionSupport!!.records.values.single()
        val review = record.review
        fun withRecord(sources: List<ConditionSource> = record.sources,
            release: ConditionReleaseReview = review): WorkoutGenerationContext {
            val replacement = ConditionSupportRecord(record.conditionId, record.contentVersion, record.contextSchemaVersion,
                record.policyVersion, record.population, record.label, record.instructions, record.inputs, record.cases,
                sources, record.responsePolicy, record.contentSha256, release)
            return c.copy(conditionSupport = ConditionSupportCatalog(c.conditionSupport.contentVersion, mapOf(record.conditionId to replacement)))
        }
        val ready = c.conditionPlanning as ConditionPlanningResult.Ready
        val frozen = ready.context
        fun withFrozen(rules: List<AppliedConditionRule> = frozen.appliedRules, reassessment: List<String> = emptyList()) =
            c.copy(conditionPlanning = ConditionPlanningResult.Ready(ready.ordinaryRules, ready.supportive,
                SessionConditionContext(1, 1, frozen.selectedConditions, frozen.sourceSupportVersions, rules,
                    frozen.consumedScheduleEvidence, reassessment), ready.weightUnit))
        val variants = listOf(
            c.copy(userProfile = c.userProfile.copy(activeConditions = listOf(SelectedCondition("fictional-condition", 1, mapOf("response" to "stop"))))),
            withRecord(sources = record.sources.map { it.copy(contentSha256 = "d".repeat(64)) }),
            withRecord(release = ConditionReleaseReview(review.state, "DIFFERENT FICTIONAL REVIEWER", review.reviewerQualification,
                review.reviewedAtEpochMillis, review.releaseAuthorization, review.reviewedContentSha256, review.sourceIds)),
            withFrozen(rules = frozen.appliedRules.map { it.copy(exerciseIdentity = it.exerciseIdentity.copy(artworkSha256 = "e".repeat(64))) }),
            withFrozen(reassessment = listOf("cleared-event"))
        )
        variants.forEach { assertThat(identity(it)).isNotEqualTo(identity(c)) }
    }

    @Test fun calendarBoundaryChangesIdentityEvenWhenSupportRemainsDue() {
        val c = f.context()
        val h = c.conditionHistory!!
        val nextDate = rebuild(c, ConditionHistory(f.now + 86_400_000, h.zoneId, h.windowStartTimestamp, true, emptyList(), emptyList()))
        assertThat((nextDate.conditionPlanning as ConditionPlanningResult.Ready).supportive).isEqualTo((c.conditionPlanning as ConditionPlanningResult.Ready).supportive)
        assertThat(identity(nextDate)).isNotEqualTo(identity(c))
    }

    @Test fun consumedCatalogContentChangesWithoutVersionLabelsStillInvalidate() {
        val c = f.context()
        val changed = c.copy(allowedExercises = c.allowedExercises.map {
            if (it.id == f.ordinary.id) it.copy(name = "CHANGED CATALOG CONTENT") else it
        })
        assertThat(identity(changed)).isNotEqualTo(identity(c))
    }

}
