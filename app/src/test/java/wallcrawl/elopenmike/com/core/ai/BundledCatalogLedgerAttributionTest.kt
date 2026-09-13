package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.ZoneId
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.LedgerOmissionReason
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.TrainingWeek
import wallcrawl.elopenmike.com.core.model.WorkoutExercise

/**
 * What the *shipped* catalog credits today, read straight from the bundled asset.
 *
 * Uses parsed, unmodified AI_ACCEPTED metadata, never synthetic muscles or approvals.
 * Pending drafts and missing metadata stay typed omissions; descriptive secondaries do
 * not become direct-primary set credit.
 */
class BundledCatalogLedgerAttributionTest {

    private val projection = PlannerFixtureContextFactory().bundledCatalogProjection()
    private val exercises = projection.exercises

    private val calculator = WeeklyDoseLedgerCalculator()
    private val week = TrainingWeek.startingOn(MONDAY_EPOCH_DAY, ZoneId.of("UTC"))

    @Test
    fun theBundledCatalogStillShipsThreeHundredAndTwoExercises() {
        assertThat(CATALOG_FILE.exists()).isTrue()
        assertThat(exercises).hasSize(EXPECTED_EXERCISES)
        assertThat(exercises.map { it.id }.distinct()).hasSize(EXPECTED_EXERCISES)
    }

    @Test
    fun authoredEntriesSeparateAiAcceptancePendingDraftsAndHumanApproval() {
        val reviewed = exercises.mapNotNull { it.reviewedMetadata }

        assertThat(reviewed).hasSize(EXPECTED_REVIEWED_ENTRIES)
        assertThat(reviewed.count { it.reviewState == ReviewState.AI_ACCEPTED }).isEqualTo(182)
        assertThat(reviewed.count { it.reviewState == ReviewState.DRAFT }).isEqualTo(29)
        assertThat(reviewed.filter { it.reviewState == ReviewState.APPROVED }).isEmpty()
    }

    @Test
    fun aWeekOfWorkCreditsOnlyTheActualAcceptedCohortAndTypesEveryOmission() {
        val ledger = calculator.calculate(
            sessions = listOf(
                completedSession(
                    id = "bundled-week",
                    completedAtEpochMillis = week.startEpochMillis,
                    exercises = exercises.mapIndexed(::completedInstance)
                )
            ),
            exercisesById = exercises.associateBy(Exercise::id),
            policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
            week = week,
            catalogVersion = projection.sourceCommit,
            reviewPolicyVersion = 2
        )

        val accepted = exercises.mapNotNull { it.reviewedMetadata }
            .filter { it.reviewState == ReviewState.AI_ACCEPTED }
        val expectedPrimary = accepted.groupingBy { it.directPrimaryMuscle }.eachCount()
            .mapValues { (_, count) -> count * 2 }
        val expectedSecondary = accepted.flatMap { it.descriptiveSecondaryMuscles }
            .groupingBy { it }.eachCount().mapValues { (_, count) -> count * 2 }
        assertThat(ledger.creditedWorkSets).isEqualTo(364)
        assertThat(ledger.directPrimarySets).containsExactlyEntriesIn(expectedPrimary)
        assertThat(ledger.secondaryInvolvement).containsExactlyEntriesIn(expectedSecondary)
        assertThat(ledger.unattributedWorkSets).containsExactly(
            LedgerOmissionReason.MISSING_REVIEWED_METADATA, 182,
            LedgerOmissionReason.METADATA_NOT_APPROVED, 58
        )
        assertThat(ledger.creditedWorkSets + ledger.omittedWorkSets).isEqualTo(604)
    }

    private fun completedInstance(index: Int, exercise: Exercise): WorkoutExercise {
        val timed = exercise.type in setOf(ExerciseType.DURATION, ExerciseType.DISTANCE_DURATION)
        val prescription = ExercisePrescription(
            exerciseType = exercise.type,
            targetSets = 2,
            repRange = if (timed) null else RepRange(8, 10),
            targetDurationSeconds = if (timed) 30 else null
        )
        return exerciseInstance(
            exerciseId = exercise.id,
            id = "instance-$index",
            orderIndex = index,
            sets = (1..2).map { setIndex ->
                completedNormalSet(id = "set-$index-$setIndex").copy(
                    exerciseType = exercise.type,
                    targetReps = if (timed) null else 10,
                    completedReps = if (timed) null else 10,
                    targetWeight = null,
                    completedWeight = null,
                    targetDurationSeconds = prescription.targetDurationSeconds,
                    completedDurationSeconds = prescription.targetDurationSeconds
                )
            }
        ).copy(prescription = prescription)
    }

    private companion object {
        const val EXPECTED_EXERCISES = 302
        const val EXPECTED_REVIEWED_ENTRIES = 211
        val CATALOG_FILE = File("src/main/assets/workout-guide/catalog.json")
    }
}
