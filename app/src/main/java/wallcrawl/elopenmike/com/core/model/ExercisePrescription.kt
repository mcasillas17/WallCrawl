package wallcrawl.elopenmike.com.core.model

/**
 * Type-specific targets for one exercise in a planned workout.
 *
 * The editor keeps incomplete user input in its own draft model; instances of this
 * domain type are always structurally valid and safe to persist or send to a planner.
 */
data class ExercisePrescription(
    val exerciseType: ExerciseType,
    val targetSets: Int,
    val repRange: RepRange? = null,
    val targetWeight: Double? = null,
    val targetAssistanceWeight: Double? = null,
    val targetDurationSeconds: Int? = null,
    val targetDistanceMeters: Double? = null,
    val restSeconds: Int = DEFAULT_REST_SECONDS,
    val effortTarget: EffortTarget? = null,
    val restClass: RestClass? = null,
    val restTargetSource: RestTargetSource? = null
) {
    init {
        require(targetSets in MIN_TARGET_SETS..MAX_TARGET_SETS) {
            "Target sets must be between $MIN_TARGET_SETS and $MAX_TARGET_SETS."
        }
        require(restSeconds in MIN_REST_SECONDS..MAX_REST_SECONDS) {
            "Rest seconds must be between $MIN_REST_SECONDS and $MAX_REST_SECONDS."
        }
        require((restClass == null) == (restTargetSource == null)) {
            "Rest class and target source must both be present or both be absent."
        }
        repRange?.let { range ->
            require(range.max <= MAX_TARGET_REPS) {
                "Target repetitions must not exceed $MAX_TARGET_REPS."
            }
        }
        targetWeight.requireValidNonNegativeDecimal("Target weight")
        targetAssistanceWeight.requireValidNonNegativeDecimal("Target assistance weight")
        targetDistanceMeters.requireValidPositiveDecimal("Target distance")
        targetDurationSeconds?.let { seconds ->
            require(seconds in MIN_DURATION_SECONDS..MAX_DURATION_SECONDS) {
                "Target duration must be between $MIN_DURATION_SECONDS and $MAX_DURATION_SECONDS seconds."
            }
        }

        when (exerciseType) {
            ExerciseType.WEIGHT_REPS -> {
                require(repRange != null) { "Weight and repetition exercises require a rep range." }
                require(targetAssistanceWeight == null) {
                    "Weight and repetition exercises cannot use assistance weight."
                }
                require(targetDurationSeconds == null && targetDistanceMeters == null) {
                    "Weight and repetition exercises cannot use duration or distance targets."
                }
            }

            ExerciseType.BODYWEIGHT_REPS -> {
                require(repRange != null) { "Bodyweight exercises require a rep range." }
                require(targetWeight == null && targetAssistanceWeight == null) {
                    "Bodyweight exercises cannot use load or assistance targets."
                }
                require(targetDurationSeconds == null && targetDistanceMeters == null) {
                    "Bodyweight exercises cannot use duration or distance targets."
                }
            }

            ExerciseType.ASSISTED_BODYWEIGHT -> {
                require(repRange != null) { "Assisted bodyweight exercises require a rep range." }
                require(targetWeight == null) {
                    "Assisted bodyweight exercises use assistance weight instead of target weight."
                }
                require(targetDurationSeconds == null && targetDistanceMeters == null) {
                    "Assisted bodyweight exercises cannot use duration or distance targets."
                }
            }

            ExerciseType.DURATION -> {
                require(targetDurationSeconds != null) { "Duration exercises require target seconds." }
                require(repRange == null) { "Duration exercises cannot use a rep range." }
                require(
                    targetWeight == null &&
                        targetAssistanceWeight == null &&
                        targetDistanceMeters == null
                ) {
                    "Duration exercises cannot use load, assistance, or distance targets."
                }
            }

            ExerciseType.DISTANCE_DURATION -> {
                require(targetDurationSeconds != null || targetDistanceMeters != null) {
                    "Distance and duration exercises require a distance or duration target."
                }
                require(repRange == null) {
                    "Distance and duration exercises cannot use a rep range."
                }
                require(targetWeight == null && targetAssistanceWeight == null) {
                    "Distance and duration exercises cannot use load or assistance targets."
                }
            }
        }
    }

    fun withUserRestPreference(preference: UserRestPreference): ExercisePrescription =
        copy(
            restSeconds = preference.restSeconds,
            restClass = preference.restClass,
            restTargetSource = RestTargetSource.USER_PREFERENCE
        )

    fun userRestPreferenceOrNull(): UserRestPreference? =
        if (restTargetSource == RestTargetSource.USER_PREFERENCE) {
            UserRestPreference(
                restClass = requireNotNull(restClass),
                restSeconds = restSeconds
            )
        } else {
            null
        }

    private fun Double?.requireValidNonNegativeDecimal(label: String) {
        if (this != null) {
            require(isFinite() && this in 0.0..MAX_WEIGHT) {
                "$label must be finite and between 0 and $MAX_WEIGHT."
            }
        }
    }

    private fun Double?.requireValidPositiveDecimal(label: String) {
        if (this != null) {
            require(isFinite() && this in MIN_DISTANCE_METERS..MAX_DISTANCE_METERS) {
                "$label must be finite and between $MIN_DISTANCE_METERS and $MAX_DISTANCE_METERS meters."
            }
        }
    }

    private companion object {
        const val MIN_TARGET_SETS = 1
        const val MAX_TARGET_SETS = 20
        const val MIN_REST_SECONDS = 0
        const val MAX_REST_SECONDS = 1_800
        const val MAX_TARGET_REPS = 1_000
        const val MIN_DURATION_SECONDS = 1
        const val MAX_DURATION_SECONDS = 86_400
        const val MIN_DISTANCE_METERS = 0.1
        const val MAX_DISTANCE_METERS = 1_000_000.0
        const val MAX_WEIGHT = 10_000.0
        const val DEFAULT_REST_SECONDS = 90
    }
}

/**
 * Values recorded for one set; validation against its prescription happens at persistence.
 *
 * Every feedback field is nullable and means unknown when null -- nothing here is ever
 * inferred from anything else. The cross-field outcome invariants (which timestamp a
 * given outcome requires, and which feedback that outcome may carry) live in
 * [SetOutcomeRules] and are enforced at the repository boundary.
 */
data class SetPerformanceInput(
    val reps: Int? = null,
    val weight: Double? = null,
    val assistanceWeight: Double? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    val rpe: Float? = null,
    val rir: Int? = null,
    val feltManageable: Boolean? = null,
    val completedAtTimestamp: Long? = null,
    val stoppedAtTimestamp: Long? = null,
    val stopReason: SetStopReason? = null,
    val isCompleted: Boolean
)

enum class WorkoutOrigin {
    PLANNER,
    CUSTOM_TEMPLATE
}

/** A catalog exercise plus its targets, independent of who planned it. */
data class PlannedExercise(
    val exerciseId: String,
    val prescription: ExercisePrescription,
    val notes: String = ""
) {
    /** Compatibility constructor for the existing repetition-only planner call sites. */
    constructor(
        exerciseId: String,
        targetSets: Int,
        repMin: Int,
        repMax: Int,
        targetWeight: Double? = null,
        restSeconds: Int = 90,
        notes: String = ""
    ) : this(
        exerciseId = exerciseId,
        prescription = ExercisePrescription(
            exerciseType = ExerciseType.WEIGHT_REPS,
            targetSets = targetSets,
            repRange = RepRange(repMin, repMax),
            targetWeight = targetWeight,
            restSeconds = restSeconds
        ),
        notes = notes
    )

    val targetSets: Int get() = prescription.targetSets
    val repMin: Int get() = prescription.repRange?.min ?: 0
    val repMax: Int get() = prescription.repRange?.max ?: 0
    val targetWeight: Double? get() = prescription.targetWeight
    val restSeconds: Int get() = prescription.restSeconds
}

typealias GeneratedExercise = PlannedExercise
