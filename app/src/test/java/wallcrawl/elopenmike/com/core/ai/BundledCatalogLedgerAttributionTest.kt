package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.ZoneId
import org.json.JSONObject
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.LedgerOmissionReason
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.TrainingWeek

/**
 * What the *shipped* catalog credits today, read straight from the bundled asset.
 *
 * The 37-entry reviewed cohort is entirely AI-authored and `DRAFT`. Until a human reviewer
 * deliberately approves an entry, `PRIMARY_ONLY_V1` must credit nothing at all from it, and
 * this test fails the build the moment that stops being true — whether because an entry was
 * approved without review or because the ledger started guessing from legacy fields.
 */
class BundledCatalogLedgerAttributionTest {

    private val catalog = JSONObject(CATALOG_FILE.readText())
    private val exercises: List<CatalogEntry> = catalog.getJSONArray("exercises").let { array ->
        (0 until array.length()).map { index ->
            val entry = array.getJSONObject(index)
            CatalogEntry(
                id = entry.getString("id"),
                reviewState = entry.optJSONObject("reviewedMetadata")?.getString("reviewState")
            )
        }
    }

    private val calculator = WeeklyDoseLedgerCalculator()
    private val week = TrainingWeek.startingOn(MONDAY_EPOCH_DAY, ZoneId.of("UTC"))

    @Test
    fun theBundledCatalogStillShipsThreeHundredAndTwoExercises() {
        assertThat(CATALOG_FILE.exists()).isTrue()
        assertThat(exercises).hasSize(EXPECTED_EXERCISES)
        assertThat(exercises.map { it.id }.distinct()).hasSize(EXPECTED_EXERCISES)
    }

    @Test
    fun allThirtySevenReviewedEntriesAreStillDraftAndNoneAreApproved() {
        val reviewed = exercises.filter { it.reviewState != null }

        assertThat(reviewed).hasSize(EXPECTED_REVIEWED_ENTRIES)
        assertThat(reviewed.map { it.reviewState }.toSet()).containsExactly("draft")
        assertThat(reviewed.filter { it.reviewState == "approved" }).isEmpty()
    }

    @Test
    fun aWeekOfWorkAgainstTheShippedCatalogCreditsNothingAndOmitsEverythingWithATypedReason() {
        val reviewedIds = exercises.filter { it.reviewState != null }.map { it.id }
        val unreviewedIds = exercises.filter { it.reviewState == null }.map { it.id }.take(5)
        val exercisesById = (reviewedIds + unreviewedIds).associateWith { id ->
            bundledExercise(id, hasReviewedBlock = id in reviewedIds)
        }

        val ledger = calculator.calculate(
            sessions = listOf(
                completedSession(
                    id = "bundled-week",
                    completedAtEpochMillis = week.startEpochMillis,
                    exercises = (reviewedIds + unreviewedIds).mapIndexed { index, exerciseId ->
                        exerciseInstance(
                            exerciseId = exerciseId,
                            id = "instance-$index",
                            orderIndex = index,
                            sets = listOf(completedNormalSet(), completedNormalSet())
                        )
                    }
                )
            ),
            exercisesById = exercisesById,
            policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
            week = week,
            catalogVersion = catalog.getJSONObject("source").getString("commit"),
            reviewPolicyVersion = 1
        )

        assertThat(ledger.directPrimarySets).isEmpty()
        assertThat(ledger.secondaryInvolvement).isEmpty()
        assertThat(ledger.unattributedWorkSets).containsExactly(
            LedgerOmissionReason.MISSING_REVIEWED_METADATA, 2 * unreviewedIds.size,
            LedgerOmissionReason.METADATA_NOT_APPROVED, 2 * reviewedIds.size
        )
    }

    /**
     * Builds the domain shape of a bundled entry.
     *
     * The reviewed block deliberately carries the muscle the catalog actually names, so the
     * assertion above proves nothing is credited because the entry is `DRAFT` — not because
     * the fixture forgot to supply a muscle.
     */
    private fun bundledExercise(id: String, hasReviewedBlock: Boolean): Exercise =
        if (hasReviewedBlock) {
            syntheticExercise(
                id = id,
                reviewedMetadata = syntheticReviewedMetadata(
                    reviewState = ReviewState.DRAFT,
                    directPrimaryMuscle = "Chest",
                    descriptiveSecondaryMuscles = setOf("Triceps")
                )
            )
        } else {
            syntheticExerciseWithoutReviewedMetadata(id)
        }

    private data class CatalogEntry(val id: String, val reviewState: String?)

    private companion object {
        const val EXPECTED_EXERCISES = 302
        const val EXPECTED_REVIEWED_ENTRIES = 37
        val CATALOG_FILE = File("src/main/assets/workout-guide/catalog.json")
    }
}
