package wallcrawl.elopenmike.com.core.ai

import kotlin.math.abs
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveLimits
import wallcrawl.elopenmike.com.core.model.*

class ProgressionEngine {
    private val measurementGuards = CapabilityEvidencePolicy()

    fun evaluate(
        exerciseId: String,
        reference: ExercisePrescription,
        sessions: List<WorkoutSession>,
        unit: WeightUnit,
        nowTimestamp: Long,
        baseConfigurationDigest: String
    ): ProgressionDecision {
        requireBounded(sessions)
        fun hold(reason: ProgressionReason) = ProgressionDecision(
            exerciseId, reason, null, reference, reference, emptyList(), baseConfigurationDigest
        )
        if (reference.exerciseType == ExerciseType.WEIGHT_REPS && reference.targetWeight == null ||
            reference.exerciseType == ExerciseType.ASSISTED_BODYWEIGHT && reference.targetAssistanceWeight == null
        ) return hold(ProgressionReason.HOLD_UNKNOWN_LOAD)
        if (sessions.map { it.id }.distinct().size != sessions.size) {
            return hold(ProgressionReason.HOLD_DUPLICATE_OBSERVATION)
        }
        val attempts = sessions.filter { s -> s.exercises.any { it.exerciseId == exerciseId } }
            .sortedWith(compareByDescending<WorkoutSession> { it.startedAtTimestamp }.thenBy { it.id })
            .take(2)
        if (attempts.size < 2) return hold(ProgressionReason.HOLD_INSUFFICIENT_HISTORY)
        val instanceIds = mutableSetOf<String>()
        val setIds = mutableSetOf<String>()
        for (session in attempts) {
            for (instance in session.exercises) {
                if (!instanceIds.add(instance.id) || instance.sets.any { !setIds.add(it.id) } ||
                    instance.sets.map { it.setNumber }.distinct().size != instance.sets.size
                ) return hold(ProgressionReason.HOLD_DUPLICATE_OBSERVATION)
                if (!instance.id.isRecordableProgressionId() || instance.sessionId != session.id ||
                    instance.sets.any { !it.id.isRecordableProgressionId() || it.workoutExerciseId != instance.id }
                ) return hold(ProgressionReason.HOLD_INVALID_OBSERVATION)
            }
            val matches = session.exercises.filter { it.exerciseId == exerciseId }
            if (matches.size != 1) return hold(ProgressionReason.HOLD_DUPLICATE_OBSERVATION)
            val exercise = matches.single()
            if (session.status != SessionStatus.COMPLETED ||
                session.exercises.flatMap { it.sets }.filter { it.type != SetType.WARMUP }
                    .any { !it.isCompleted || it.stopReason != null || it.stoppedAtTimestamp != null }
            ) return hold(ProgressionReason.HOLD_INCOMPLETE_WORK)
            val completed = session.completedAtTimestamp
            if (!session.id.isRecordableProgressionId() || session.startedAtTimestamp <= 0 || completed == null ||
                completed < session.startedAtTimestamp || completed > nowTimestamp ||
                exercise.id.isBlank() || exercise.sessionId != session.id
            ) return hold(ProgressionReason.HOLD_INVALID_OBSERVATION)
            if (!samePrescription(reference, unit, exercise.prescription, session.weightUnit)) {
                return hold(ProgressionReason.HOLD_TARGETS_CHANGED)
            }
            val work = exercise.sets.filter { it.type != SetType.WARMUP }.sortedBy { it.setNumber }
            if (work.size != reference.targetSets || work.any { it.type != SetType.NORMAL }) {
                return hold(ProgressionReason.HOLD_INCOMPLETE_WORK)
            }
            val shape = measurementGuards.measurementShape(exercise.prescription)
                ?: return hold(ProgressionReason.HOLD_INVALID_OBSERVATION)
            var previousTimestamp = session.startedAtTimestamp
            for (set in work) {
                val timestamp = set.completedAtTimestamp
                if (set.id.isBlank() || set.workoutExerciseId != exercise.id || set.setNumber <= 0 ||
                    timestamp == null || timestamp < previousTimestamp || timestamp > completed ||
                    set.rir?.let { it !in SetOutcomeRules.MIN_RIR..SetOutcomeRules.MAX_RIR } == true ||
                    set.rpe?.let { !it.isFinite() || it !in SetOutcomeRules.MIN_RPE..SetOutcomeRules.MAX_RPE } == true
                ) return hold(ProgressionReason.HOLD_INVALID_OBSERVATION)
                previousTimestamp = timestamp
                if (set.feltManageable != true) return hold(ProgressionReason.HOLD_MANAGEABLE_NOT_CONFIRMED)
                if (!measurementGuards.isConfirmedComparableSet(set, exercise.prescription, shape)) {
                    return hold(ProgressionReason.HOLD_INVALID_OBSERVATION)
                }
                if (!matchesTargets(set, exercise.prescription)) return hold(ProgressionReason.HOLD_TARGETS_CHANGED)
                if (set.rir == null && set.rpe == null) return hold(ProgressionReason.HOLD_MISSING_EFFORT)
                if (set.rir?.let { it < MIN_QUALIFYING_RIR } == true ||
                    set.rpe?.let { it > MAX_QUALIFYING_RPE } == true
                ) return hold(ProgressionReason.HOLD_EFFORT_NOT_QUALIFYING)
                if (!metTargets(set, exercise.prescription)) return hold(ProgressionReason.HOLD_TARGET_NOT_MET)
            }
        }
        val latestPrescription = attempts[0].exercises.single { it.exerciseId == exerciseId }.prescription
        val earlierPrescription = attempts[1].exercises.single { it.exerciseId == exerciseId }.prescription
        if (!samePrescription(
                latestPrescription, attempts[0].weightUnit,
                earlierPrescription, attempts[1].weightUnit
            )
        ) return hold(ProgressionReason.HOLD_TARGETS_CHANGED)
        if (attempts[1].completedAtTimestamp!! > attempts[0].startedAtTimestamp) {
            return hold(ProgressionReason.HOLD_INVALID_OBSERVATION)
        }
        val advanced = advance(reference, unit) ?: return hold(ProgressionReason.HOLD_AT_BOUND)
        return ProgressionDecision(
            exerciseId, ProgressionReason.ADVANCED, advanced.first, reference, advanced.second,
            attempts.asReversed().map { it.id }, baseConfigurationDigest
        )
    }

    /** Distance shapes belong to this domain contract, not automatic strength eligibility. */
    private fun advance(reference: ExercisePrescription, unit: WeightUnit): Pair<ProgressionAxis, ExercisePrescription>? {
        val weightStep = convertWeight(WEIGHT_STEP_KG, WeightUnit.KG, unit)
        return when (reference.exerciseType) {
            ExerciseType.WEIGHT_REPS -> {
                val next = requireNotNull(reference.targetWeight) + weightStep
                if (next > ExercisePrescription.MAX_WEIGHT) null
                else ProgressionAxis.LOAD to reference.copy(targetWeight = next)
            }
            ExerciseType.BODYWEIGHT_REPS -> {
                val range = requireNotNull(reference.repRange)
                if (range.max >= ExercisePrescription.MAX_TARGET_REPS) null
                else ProgressionAxis.REP_RANGE to reference.copy(repRange = RepRange(range.min + 1, range.max + 1))
            }
            ExerciseType.ASSISTED_BODYWEIGHT -> {
                val next = requireNotNull(reference.targetAssistanceWeight) - weightStep
                if (next < 0.0) null
                else ProgressionAxis.ASSISTANCE to reference.copy(targetAssistanceWeight = next)
            }
            ExerciseType.DURATION, ExerciseType.DISTANCE_DURATION -> {
                val distance = reference.targetDistanceMeters
                if (distance != null) {
                    val next = distance + DISTANCE_STEP_METERS
                    if (next > ExercisePrescription.MAX_DISTANCE_METERS) null
                    else ProgressionAxis.DISTANCE to reference.copy(targetDistanceMeters = next)
                } else {
                    val next = requireNotNull(reference.targetDurationSeconds) + DURATION_STEP_SECONDS
                    if (next > ExercisePrescription.MAX_DURATION_SECONDS) null
                    else ProgressionAxis.DURATION to reference.copy(targetDurationSeconds = next)
                }
            }
        }
    }

    private fun matchesTargets(set: WorkoutSet, prescription: ExercisePrescription): Boolean =
        set.targetReps == prescription.repRange?.max &&
            equalValue(set.targetWeight, prescription.targetWeight) &&
            equalValue(set.targetAssistanceWeight, prescription.targetAssistanceWeight) &&
            set.targetDurationSeconds == prescription.targetDurationSeconds &&
            equalValue(set.targetDistanceMeters, prescription.targetDistanceMeters)

    private fun metTargets(set: WorkoutSet, prescription: ExercisePrescription): Boolean =
        (prescription.repRange?.let { (set.completedReps ?: 0) >= it.max } ?: true) &&
            (prescription.targetWeight?.let { equalValue(set.completedWeight, it) } ?: true) &&
            (prescription.targetAssistanceWeight?.let { equalValue(set.completedAssistanceWeight, it) } ?: true) &&
            (prescription.targetDurationSeconds?.let { (set.completedDurationSeconds ?: 0) >= it } ?: true) &&
            (prescription.targetDistanceMeters?.let {
                (set.completedDistanceMeters ?: 0.0) >= it || equalValue(set.completedDistanceMeters, it)
            } ?: true)

    companion object {
        const val MAX_SESSIONS = 8
        const val WEIGHT_STEP_KG = 2.5
        const val DURATION_STEP_SECONDS = 5
        const val DISTANCE_STEP_METERS = 25.0
        const val MIN_QUALIFYING_RIR = 2
        const val MAX_QUALIFYING_RPE = 8f
        const val CONVERSION_TOLERANCE_KG = 0.000001

        fun requireBounded(sessions: List<WorkoutSession>) {
            require(sessions.size <= MAX_SESSIONS) { "Progression history exceeds its session bound." }
            require(sessions.all { s ->
                s.exercises.size <= LocalDataArchiveLimits.MAX_EXERCISES_PER_SESSION &&
                    s.exercises.all { it.sets.size <= LocalDataArchiveLimits.MAX_SETS_PER_EXERCISE }
            }) { "Progression history exceeds its exercise or set bound." }
        }

        fun samePrescription(
            a: ExercisePrescription, aUnit: WeightUnit,
            b: ExercisePrescription, bUnit: WeightUnit
        ): Boolean {
            if (a.exerciseType != b.exerciseType ||
                (a.targetDistanceMeters == null) != (b.targetDistanceMeters == null)
            ) return false
            return a.copy(targetWeight = null, targetAssistanceWeight = null, targetDistanceMeters = b.targetDistanceMeters) ==
                b.copy(targetWeight = null, targetAssistanceWeight = null) &&
                equalWeight(a.targetWeight, aUnit, b.targetWeight, bUnit) &&
                equalWeight(a.targetAssistanceWeight, aUnit, b.targetAssistanceWeight, bUnit) &&
                equalValue(a.targetDistanceMeters, b.targetDistanceMeters)
        }

        private fun equalWeight(a: Double?, aUnit: WeightUnit, b: Double?, bUnit: WeightUnit): Boolean =
            if (a == null || b == null) a == b else if (aUnit == bUnit) equalValue(a, b) else
                abs(convertWeight(a, aUnit, WeightUnit.KG) - convertWeight(b, bUnit, WeightUnit.KG)) <= CONVERSION_TOLERANCE_KG ||
                    MeasurementPrecision.sameEditableValue(convertWeight(a, aUnit, bUnit), b) ||
                    MeasurementPrecision.sameEditableValue(a, convertWeight(b, bUnit, aUnit))

        private fun equalValue(a: Double?, b: Double?): Boolean =
            if (a == null || b == null) a == b else MeasurementPrecision.sameEditableValue(a, b)
    }
}
