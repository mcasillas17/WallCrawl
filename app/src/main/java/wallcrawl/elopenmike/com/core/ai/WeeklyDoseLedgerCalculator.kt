package wallcrawl.elopenmike.com.core.ai

import java.util.EnumMap
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.LedgerOmissionReason
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.ReviewedExerciseMetadata
import wallcrawl.elopenmike.com.core.model.SessionStatus
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
        requireWellFormedInputs(sessions, week, catalogVersion, reviewPolicyVersion)

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
                        require(attribution.reviewed.directPrimaryMuscle.isNotBlank()) {
                            "Approved reviewed metadata is missing directPrimaryMuscle for " +
                                "exercise '${exercise.exerciseId}'."
                        }
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
        ).also(::requireWithinCatalogBounds)
    }

    /**
     * Rejects malformed input loudly and specifically instead of quietly dropping it.
     *
     * Silently skipping a session that should never have been handed to the calculator
     * would turn a query or wiring defect into an under-reported week that still looks
     * successful. Messages name the offending field and identifier, never a logged value.
     */
    private fun requireWellFormedInputs(
        sessions: List<WorkoutSession>,
        week: TrainingWeek,
        catalogVersion: String,
        reviewPolicyVersion: Int
    ) {
        require(catalogVersion.isNotBlank() && catalogVersion.length <= MAX_VERSION_LENGTH) {
            "catalogVersion must be non-blank and at most $MAX_VERSION_LENGTH characters."
        }
        require(reviewPolicyVersion >= 0) { "reviewPolicyVersion must not be negative." }
        require(sessions.size <= MAX_SESSIONS_PER_WEEK) {
            "A week cannot contain more than $MAX_SESSIONS_PER_WEEK completed sessions."
        }

        val seenSessionIds = mutableSetOf<String>()
        val seenExerciseInstanceIds = mutableSetOf<String>()
        sessions.forEach { session ->
            require(session.status == SessionStatus.COMPLETED) {
                "Session '${session.id}' has status ${session.status}; the weekly ledger " +
                    "only reconstructs completed history."
            }
            val completedAt = requireNotNull(session.completedAtTimestamp) {
                "Session '${session.id}' is completed but has no completedAtTimestamp."
            }
            require(week.contains(completedAt)) {
                "Session '${session.id}' has a completedAtTimestamp outside the requested week."
            }
            require(seenSessionIds.add(session.id)) {
                "Duplicate session id '${session.id}' was supplied to the weekly ledger."
            }

            session.exercises.forEach { exercise ->
                require(seenExerciseInstanceIds.add(exercise.id)) {
                    "Duplicate workout exercise id '${exercise.id}' was supplied to the " +
                        "weekly ledger."
                }
                val seenSetIds = mutableSetOf<String>()
                exercise.sets.forEach { set ->
                    require(seenSetIds.add(set.id)) {
                        "Duplicate workout set id '${set.id}' was supplied for workout " +
                            "exercise '${exercise.id}'."
                    }
                }
            }
        }
    }

    /**
     * Guards the output against a catalog or history that is far outside expected bounds,
     * so a malformed input can never produce an unbounded ledger to persist or cache.
     */
    private fun requireWithinCatalogBounds(ledger: WeeklyDoseLedger) {
        require(ledger.directPrimarySets.size <= MAX_DISTINCT_MUSCLES) {
            "directPrimarySets exceeds the $MAX_DISTINCT_MUSCLES muscle bound."
        }
        require(ledger.secondaryInvolvement.size <= MAX_DISTINCT_MUSCLES) {
            "secondaryInvolvement exceeds the $MAX_DISTINCT_MUSCLES muscle bound."
        }
        require(ledger.creditedWorkSets + ledger.omittedWorkSets <= MAX_WORK_SETS_PER_WEEK) {
            "A week cannot contain more than $MAX_WORK_SETS_PER_WEEK completed work sets."
        }
        // The storage codec bounds the same quantity with the same constant. Enforcing it
        // here is what makes "anything this calculator produces can be read back from its
        // own payload" true by construction: without it a ledger with many descriptive
        // secondary muscles could be cached and then refused forever, silently disabling
        // caching for that week instead of failing loudly here.
        require(ledger.totalCountedUnits <= MAX_LEDGER_COUNTED_UNITS) {
            "A ledger cannot hold more than $MAX_LEDGER_COUNTED_UNITS counted units."
        }
    }

    companion object {
        /** Generous ceilings that bound a persisted ledger without constraining real use. */
        const val MAX_SESSIONS_PER_WEEK: Int = 1_000
        const val MAX_WORK_SETS_PER_WEEK: Int = 50_000

        const val MAX_DISTINCT_MUSCLES: Int = 64

        /**
         * Every counted unit in one ledger: primary plus secondary plus unattributed.
         *
         * Secondary involvement adds one unit per descriptive secondary muscle, so a
         * ledger's unit total is a multiple of its work-set total and must not be capped as
         * if it were work sets.
         *
         * The value is the arithmetic maximum a ledger passing the guards above can hold.
         * Primary and unattributed counts are bounded together by [MAX_WORK_SETS_PER_WEEK],
         * and the primary and secondary maps are bounded independently, so one work set can
         * contribute its primary plus a full [MAX_DISTINCT_MUSCLES] distinct secondaries —
         * `MAX_DISTINCT_MUSCLES + 1` units, not `MAX_DISTINCT_MUSCLES`. Deriving it this way
         * means the producer's check cannot fire for input its other guards accept, so the
         * bound never turns a legal week into a crash. Its purpose is to give the storage
         * codec the same hard ceiling, which is what makes the round trip total.
         */
        const val MAX_LEDGER_COUNTED_UNITS: Int =
            MAX_WORK_SETS_PER_WEEK * (MAX_DISTINCT_MUSCLES + 1)
        const val MAX_VERSION_LENGTH: Int = 128
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
