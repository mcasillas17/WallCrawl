package wallcrawl.elopenmike.com.core.model

/**
 * Why a planned set was not performed as prescribed.
 *
 * [PAIN_STOP] records only that the user chose to stop because something hurt. It is not
 * a symptom report, an injury, or a diagnosis, and nothing in the app may present it as
 * one. The reasons are a closed set so the gym-floor logger never has to accept, store,
 * or later render free text.
 */
enum class SetStopReason {
    USER_SKIPPED,
    PAIN_STOP,
    EQUIPMENT_UNAVAILABLE,
    TIME_CONSTRAINT,
    OTHER
}

/**
 * The typed outcome of one planned set, derived from the persisted flat columns.
 *
 * Three outcomes are mutually exclusive by construction, so later deterministic
 * adaptation can never confuse work that was never started with work the user
 * deliberately stopped, and only [Completed] work is ever counted.
 */
sealed interface SetOutcome {
    /** Planned but never resolved: no completion, no stop reason, no feedback. */
    data object NotRecorded : SetOutcome

    /**
     * Work the user marked complete. [recordedAtTimestamp] is null only for history
     * written before typed outcomes existed; it is never fabricated after the fact.
     */
    data class Completed(
        val recordedAtTimestamp: Long?,
        val rpe: Float? = null,
        val rir: Int? = null,
        val feltManageable: Boolean? = null
    ) : SetOutcome

    /** Work the user deliberately skipped or stopped, with a typed, non-diagnostic reason. */
    data class Stopped(
        val reason: SetStopReason,
        val recordedAtTimestamp: Long?,
        val rpe: Float? = null,
        val rir: Int? = null
    ) : SetOutcome
}

/**
 * Cross-field invariants for a typed set outcome.
 *
 * These run at the domain/repository boundary before anything is persisted, so no
 * partial or contradictory outcome can ever reach the database. Messages name the
 * offending field and never echo what the user entered.
 */
object SetOutcomeRules {

    /**
     * WallCrawl records effort on the documented 0-10 RPE scale. Null stays null: a
     * missing value means unknown and is never replaced by an assumed effort.
     */
    const val MIN_RPE: Float = 0f
    const val MAX_RPE: Float = 10f
    const val MIN_RIR: Int = 0
    const val MAX_RIR: Int = 10

    fun requireValidOutcome(performance: SetPerformanceInput) {
        performance.rpe?.let { rpe ->
            require(rpe.isFinite() && rpe >= MIN_RPE && rpe <= MAX_RPE) {
                "rpe must be a finite value between $MIN_RPE and $MAX_RPE."
            }
        }
        performance.rir?.let { rir ->
            require(rir in MIN_RIR..MAX_RIR) {
                "rir must be between $MIN_RIR and $MAX_RIR."
            }
        }

        if (performance.isCompleted) {
            require(performance.stopReason == null) {
                "A completed set cannot carry a stopReason."
            }
            require(performance.stoppedAtTimestamp == null) {
                "A completed set cannot carry a stoppedAtTimestamp."
            }
            require((performance.completedAtTimestamp ?: 0L) > 0L) {
                "A completed set requires a positive completedAtTimestamp."
            }
            return
        }

        require(performance.completedAtTimestamp == null) {
            "An unfinished set cannot carry a completedAtTimestamp."
        }
        require(performance.feltManageable == null) {
            "feltManageable may only be recorded for completed work."
        }

        if (performance.stopReason != null) {
            require((performance.stoppedAtTimestamp ?: 0L) > 0L) {
                "A skipped or stopped set requires a positive stoppedAtTimestamp."
            }
            return
        }

        require(performance.stoppedAtTimestamp == null) {
            "stoppedAtTimestamp requires a typed stopReason."
        }
        require(performance.rpe == null) {
            "rpe may only be recorded for completed or stopped work."
        }
        require(performance.rir == null) {
            "rir may only be recorded for completed or stopped work."
        }
    }
}

/** The typed outcome this input records, valid only for an input that passed [SetOutcomeRules]. */
val SetPerformanceInput.outcome: SetOutcome
    get() = when {
        isCompleted -> SetOutcome.Completed(
            recordedAtTimestamp = completedAtTimestamp,
            rpe = rpe,
            rir = rir,
            feltManageable = feltManageable
        )

        stopReason != null -> SetOutcome.Stopped(
            reason = stopReason,
            recordedAtTimestamp = stoppedAtTimestamp,
            rpe = rpe,
            rir = rir
        )

        else -> SetOutcome.NotRecorded
    }

/**
 * The typed outcome of a persisted set.
 *
 * Reading is deliberately total: rows written before typed outcomes existed carry a null
 * timestamp rather than a fabricated one, and they still read back as [SetOutcome.Completed]
 * exactly as they were logged.
 */
val WorkoutSet.outcome: SetOutcome
    get() = when {
        isCompleted -> SetOutcome.Completed(
            recordedAtTimestamp = completedAtTimestamp,
            rpe = rpe,
            rir = rir,
            feltManageable = feltManageable
        )

        stopReason != null -> SetOutcome.Stopped(
            reason = stopReason,
            recordedAtTimestamp = stoppedAtTimestamp,
            rpe = rpe,
            rir = rir
        )

        else -> SetOutcome.NotRecorded
    }
