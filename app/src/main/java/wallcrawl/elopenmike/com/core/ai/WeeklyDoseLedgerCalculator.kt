package wallcrawl.elopenmike.com.core.ai

import java.util.EnumMap
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.LedgerOmissionReason
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseMetadata
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.TrainingWeek
import wallcrawl.elopenmike.com.core.model.WeeklyDoseLedger
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet

/**
 * Reconstructs a [WeeklyDoseLedger] from completed history under [LedgerPolicyVersion].
 *
 * The calculator is pure: it performs no I/O, reads no clock, and keeps no state, so the
 * same inputs always produce the same ledger. Gathering sessions from the database and
 * caching the result belong to the repository layer, never here.
 */
class WeeklyDoseLedgerCalculator {

    fun calculate(
        sessions: List<WorkoutSession>,
        exercisesById: Map<String, Exercise>,
        policyVersion: LedgerPolicyVersion,
        week: TrainingWeek,
        catalogVersion: String,
        reviewPolicyVersion: Int
    ): WeeklyDoseLedger {
        val directPrimarySets = sortedMapOf<String, Int>()
        val secondaryInvolvement = sortedMapOf<String, Int>()
        val unattributedWorkSets = EnumMap<LedgerOmissionReason, Int>(LedgerOmissionReason::class.java)

        sessions.forEach { session ->
            session.exercises.forEach { exercise ->
                val workSets = exercise.sets.count(WorkoutSet::isCreditableWorkSet)
                if (workSets == 0) return@forEach

                when (val attribution = exercise.resolveAttribution(exercisesById)) {
                    is LedgerAttribution.Omitted ->
                        unattributedWorkSets.merge(attribution.reason, workSets, Int::plus)

                    is LedgerAttribution.Credited -> {
                        directPrimarySets.merge(
                            attribution.reviewed.directPrimaryMuscle,
                            workSets,
                            Int::plus
                        )
                        // Secondary involvement is analytics only. It accumulates in its own
                        // map and is never merged into directPrimarySets, so no amount of
                        // secondary work can inflate weekly dose under PRIMARY_ONLY_V1.
                        attribution.reviewed.descriptiveSecondaryMuscles
                            .filterNot { it == attribution.reviewed.directPrimaryMuscle }
                            .forEach { secondaryMuscle ->
                                secondaryInvolvement.merge(secondaryMuscle, workSets, Int::plus)
                            }
                    }
                }
            }
        }

        return WeeklyDoseLedger(
            policyVersion = policyVersion,
            weekStartEpochDay = week.startEpochDay,
            timeZoneId = week.zoneId.id,
            catalogVersion = catalogVersion,
            reviewPolicyVersion = reviewPolicyVersion,
            directPrimarySets = directPrimarySets,
            secondaryInvolvement = secondaryInvolvement,
            unattributedWorkSets = unattributedWorkSets.toDeclaredOrderMap()
        )
    }
}

/**
 * How one completed exercise instance's work sets are attributed.
 *
 * Attribution is deliberately total and fail-closed: an exercise either resolves to approved
 * reviewed metadata or is counted as omitted with a typed reason. There is no third branch
 * that falls back to legacy `primaryMuscles`, the exercise name, legacy `programming`, or an
 * inferred movement pattern.
 */
private sealed interface LedgerAttribution {
    data class Credited(val reviewed: ReviewedExerciseMetadata) : LedgerAttribution
    data class Omitted(val reason: LedgerOmissionReason) : LedgerAttribution
}

private fun WorkoutExercise.resolveAttribution(
    exercisesById: Map<String, Exercise>
): LedgerAttribution {
    val exercise = exercisesById[exerciseId]
        ?: return LedgerAttribution.Omitted(LedgerOmissionReason.UNKNOWN_EXERCISE)
    val reviewed = exercise.reviewedMetadata
        ?: return LedgerAttribution.Omitted(LedgerOmissionReason.MISSING_REVIEWED_METADATA)
    return when (reviewed.reviewState) {
        ReviewState.APPROVED -> LedgerAttribution.Credited(reviewed)
        ReviewState.DRAFT ->
            LedgerAttribution.Omitted(LedgerOmissionReason.METADATA_NOT_APPROVED)
    }
}

/**
 * True when this set is one completed work set under `PRIMARY_ONLY_V1`.
 *
 * The classification is exhaustive on purpose: adding a set type later fails compilation
 * here, so a new type has to be credited or excluded deliberately rather than by default.
 */
private val WorkoutSet.isCreditableWorkSet: Boolean
    get() = isCompleted && when (type) {
        // Warm-ups are preparation, not exposure, however they were logged.
        SetType.WARMUP -> false
        // FAILURE is historical data about how a set went, never an automatic target.
        SetType.NORMAL, SetType.DROPSET, SetType.MYOREP, SetType.FAILURE -> true
    }

/** Copies into a map that iterates in [LedgerOmissionReason] declaration order. */
private fun EnumMap<LedgerOmissionReason, Int>.toDeclaredOrderMap(): Map<LedgerOmissionReason, Int> =
    LedgerOmissionReason.entries
        .mapNotNull { reason -> this[reason]?.let { reason to it } }
        .toMap(LinkedHashMap())
