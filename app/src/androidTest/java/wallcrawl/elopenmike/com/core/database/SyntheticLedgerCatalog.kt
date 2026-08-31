package wallcrawl.elopenmike.com.core.database

import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisual
import wallcrawl.elopenmike.com.core.exercise.workoutguide.CatalogAttribution
import wallcrawl.elopenmike.com.core.exercise.workoutguide.WorkoutGuideCatalogSnapshot
import wallcrawl.elopenmike.com.core.exercise.workoutguide.WorkoutGuideCatalogSource
import wallcrawl.elopenmike.com.core.model.ComplexityTier
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseAttribution
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.ImpactLevel
import wallcrawl.elopenmike.com.core.model.MovementPattern
import wallcrawl.elopenmike.com.core.model.PrescriptionShape
import wallcrawl.elopenmike.com.core.model.ReviewProvenance
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseMetadata
import wallcrawl.elopenmike.com.core.model.SupportRequirement

/**
 * Test-only catalog fixtures for the weekly dose ledger.
 *
 * Every reviewed block built here is **synthetic**. It exists only so crediting can be
 * tested against `APPROVED` metadata while the shipped 37-entry cohort stays `DRAFT`, and
 * the provenance strings say exactly that. Nothing in `src/main` can read this file, so a
 * synthetic approval can never reach the bundled catalog or a real user's ledger.
 */
internal const val SYNTHETIC_REVIEWER_ROLE = "SYNTHETIC_TEST_REVIEWER_NOT_A_HUMAN"

internal const val SYNTHETIC_REVIEW_RATIONALE =
    "SYNTHETIC TEST FIXTURE. Not a human review and never shipped in the bundled catalog."

internal const val SYNTHETIC_CATALOG_COMMIT = "synthetic-catalog-commit-for-tests"

/** A catalog source that serves a fixed snapshot, or fails on demand. */
internal class FakeCatalogSource(
    private var snapshot: WorkoutGuideCatalogSnapshot,
    private var failure: Throwable? = null
) : WorkoutGuideCatalogSource {

    override suspend fun snapshot(): WorkoutGuideCatalogSnapshot {
        failure?.let { throw it }
        return snapshot
    }

    override fun currentSnapshot(): WorkoutGuideCatalogSnapshot? = snapshot

    fun replaceExercises(exercises: List<Exercise>, commit: String = SYNTHETIC_CATALOG_COMMIT) {
        snapshot = syntheticSnapshot(exercises, commit)
    }

    fun failWith(error: Throwable) {
        failure = error
    }
}

internal fun syntheticSnapshot(
    exercises: List<Exercise>,
    commit: String = SYNTHETIC_CATALOG_COMMIT
): WorkoutGuideCatalogSnapshot = WorkoutGuideCatalogSnapshot(
    exercises = exercises,
    framesByExerciseId = emptyMap<String, List<ExerciseVisual>>(),
    catalogAttribution = CatalogAttribution(
        repository = "https://example.invalid/synthetic",
        commit = commit,
        assetLicense = "CC-BY-SA-4.0",
        attribution = ExerciseAttribution(
            creator = "Synthetic Fixture",
            creatorUrl = "https://example.invalid",
            license = "CC BY-SA 4.0",
            licenseUrl = "https://creativecommons.org/licenses/by-sa/4.0/"
        ),
        exerciseCount = exercises.size,
        frameCount = 0
    )
)

internal fun syntheticCatalogExercise(
    id: String,
    directPrimaryMuscle: String,
    descriptiveSecondaryMuscles: Set<String> = emptySet(),
    reviewState: ReviewState? = ReviewState.APPROVED,
    policyVersion: Int = 1
): Exercise = Exercise(
    id = id,
    name = "Synthetic $id",
    primaryMuscles = listOf("LEGACY-MUST-NOT-BE-CREDITED"),
    secondaryMuscles = listOf("LEGACY-MUST-NOT-BE-CREDITED"),
    listedEquipment = listOf("Bodyweight"),
    type = ExerciseType.WEIGHT_REPS,
    reviewedMetadata = reviewState?.let { state ->
        ReviewedExerciseMetadata(
            reviewState = state,
            directPrimaryMuscle = directPrimaryMuscle,
            descriptiveSecondaryMuscles = descriptiveSecondaryMuscles,
            movementPattern = MovementPattern.HORIZONTAL_PUSH,
            complexity = ComplexityTier.STANDARD,
            progressionFamily = "synthetic-family",
            prescriptionShape = PrescriptionShape.WEIGHT_REPS,
            approvedRegressions = emptyList(),
            approvedSubstitutions = emptyList(),
            capabilityRequirements = emptySet(),
            supportRequirement = SupportRequirement.SUPPORTED,
            impactLevel = ImpactLevel.LOW,
            equipmentAlternatives = listOf(listOf("Bodyweight")),
            provenance = ReviewProvenance(
                reviewerRole = if (state == ReviewState.APPROVED) SYNTHETIC_REVIEWER_ROLE else null,
                rationaleOrSource = SYNTHETIC_REVIEW_RATIONALE,
                reviewedAtEpochMillis = if (state == ReviewState.APPROVED) {
                    1_756_000_000_000L
                } else {
                    null
                },
                schemaVersion = 1,
                policyVersion = policyVersion
            )
        )
    }
)
