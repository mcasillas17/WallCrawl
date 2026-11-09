package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.*

/** Choices use the active session's unit. The reference keeps the actual recorded unit. */
data class FirstWeightGuidance(
    val perDumbbell: Boolean = false,
    val rackChoices: List<Double> = emptyList(),
    val reference: FirstWeightReference? = null,
    /** Shown only after the user confirms that the source set was logged per dumbbell. */
    val confirmedReferenceChoices: List<Double> = emptyList()
)

data class FirstWeightReference(
    val exerciseId: String, val weight: Double, val unit: WeightUnit, val reps: Int, val sourceSetId: String
)

object FirstWeightGuidancePolicy {
    private val conditionPolicy = ConditionPlanningPolicy()
    private val comparableVariants = listOf(
        setOf("dumbbell-bench-press", "incline-dumbbell-press"),
        setOf("bicep-curl", "incline-dumbbell-curl"),
        setOf("dumbbell-shoulder-press", "standing-dumbbell-press")
    )
    private val dumbbellSetup = setOf(StandardEquipment.DUMBBELL, StandardEquipment.BENCH, StandardEquipment.BOX, StandardEquipment.CHAIR)
    private const val LOOKBACK_MS = 28L * 24 * 60 * 60 * 1000

    fun comparableExerciseIds(exerciseId: String): Set<String> =
        comparableVariants.firstOrNull { exerciseId in it }.orEmpty() - exerciseId

    fun evaluate(
        exercise: WorkoutExercise,
        session: WorkoutSession,
        profile: UserProfile?,
        history: List<WorkoutSession>,
        catalogExercise: Exercise?,
        hasPendingReassessment: Boolean,
        comparableCatalog: Map<String, Exercise>,
        oldestUnexaminedSession: Long? = null
    ): FirstWeightGuidance {
        if (catalogExercise?.id != exercise.exerciseId || !requiresDumbbellLoad(catalogExercise) ||
            exercise.prescription.exerciseType != ExerciseType.WEIGHT_REPS
        ) return FirstWeightGuidance()
        val manual = FirstWeightGuidance(perDumbbell = true)
        if (profile == null || hasPendingReassessment || profile.activeConditions.isNotEmpty() ||
            profile.unresolvedTrainingConstraintsRaw != null || !eligibleForGuidance(catalogExercise, profile) ||
            session.conditionContext?.selectedConditions?.isNotEmpty() == true ||
            session.status != SessionStatus.IN_PROGRESS || session.startedAtTimestamp <= 0 ||
            exercise.sessionId != session.id || exercise.prescription.purpose != PrescriptionPurpose.ORDINARY ||
            exercise.targetWeight != null || exercise.sets.any { it.isCompleted && it.type == SetType.NORMAL }
        ) return manual

        val comparable = comparableExerciseIds(exercise.exerciseId) + exercise.exerciseId
        val earliest = session.startedAtTimestamp - LOOKBACK_MS
        // Unexamined recent sessions may contain a pain stop for this lift or its variant.
        if (oldestUnexaminedSession != null && oldestUnexaminedSession >= earliest) return manual
        val recent = history.filter {
            it.id != session.id && it.hasValidCompletedChronology() &&
                requireNotNull(it.completedAtTimestamp) in earliest until session.startedAtTimestamp
        }
        if ((recent + session).any { workout -> workout.exercises.any { lift ->
                lift.exerciseId in comparable && lift.sets.any { it.stopReason == SetStopReason.PAIN_STOP }
            } }
        ) return manual

        val available = profile.dumbbellRack?.let { rack ->
            rack.weights.map { convertWeightForEditing(it, rack.unit, session.weightUnit) }
                .filter { it <= ExercisePrescription.MAX_WEIGHT }.distinct()
        }.orEmpty()
        if (profile.returningAfterBreakWeeks > 0) return manual.copy(rackChoices = available.take(2))

        // Use the most recent attempt; an older successful set must not hide a newer stop.
        val previous = recent.sortedWith(compareByDescending<WorkoutSession> { it.completedAtTimestamp }.thenBy { it.id })
            .firstOrNull { workout -> workout.exercises.any { it.exerciseId in comparable } }
        val previousLift = previous?.exercises?.filter { it.exerciseId in comparable }?.maxByOrNull { it.orderIndex }
        val sourceCatalog = previousLift?.let { comparableCatalog[it.exerciseId] }
        val reference = if (previous != null && previousLift != null && previousLift.exerciseId != exercise.exerciseId &&
            sourceCatalog != null && sourceCatalog.id == previousLift.exerciseId &&
            requiresDumbbellLoad(sourceCatalog) && eligibleForGuidance(sourceCatalog, profile) &&
            previous.conditionContext?.selectedConditions?.isNotEmpty() != true &&
            previousLift.sessionId == previous.id && previousLift.prescription.purpose == PrescriptionPurpose.ORDINARY &&
            previousLift.prescription.exerciseType == ExerciseType.WEIGHT_REPS
        ) referenceFrom(previousLift, previous, exercise.prescription, earliest) else null
        if (reference == null) return manual.copy(rackChoices = available.take(3))
        val ceiling = convertWeightForEditing(reference.weight, reference.unit, session.weightUnit)
        val atOrBelowReference = available.filter { it <= ceiling }
        val choices = atOrBelowReference.take(3)
        // Legacy logs carried no load convention. Only explicit confirmation unlocks nearby weights.
        val confirmed = if (atOrBelowReference.size > 3 && !profile.hasConditionReports && profile.trainingConstraints.isEmpty()) {
            atOrBelowReference.takeLast(3)
        } else emptyList()
        return manual.copy(rackChoices = choices, reference = reference, confirmedReferenceChoices = confirmed)
    }

    private fun eligibleForGuidance(exercise: Exercise, profile: UserProfile): Boolean =
        !profile.demandRestrictions.ranksLower(exercise.id) &&
            conditionPolicy.ordinaryEligible(exercise, profile, reviewed = exercise.acceptedMetadata() != null)

    private fun requiresDumbbellLoad(exercise: Exercise): Boolean {
        fun dumbbellsWithSupport(equipment: List<String>) =
            StandardEquipment.DUMBBELL in equipment && equipment.all { it in dumbbellSetup }
        if (exercise.type != ExerciseType.WEIGHT_REPS || exercise.isStretch ||
            !dumbbellsWithSupport(exercise.listedEquipment)
        ) return false
        // A catalog label can say Dumbbell even when authored setups permit kettlebells or bodyweight.
        val programming = exercise.programming?.requiredEquipmentCombinations.orEmpty()
        val reviewed = exercise.acceptedMetadata()?.equipmentAlternatives.orEmpty()
        val setups = programming + reviewed
        return setups.isNotEmpty() && setups.all(::dumbbellsWithSupport)
    }

    private fun referenceFrom(
        lift: WorkoutExercise, workout: WorkoutSession, target: ExercisePrescription, earliest: Long
    ): FirstWeightReference? {
        if (lift.sets.any { it.stopReason != null || it.stoppedAtTimestamp != null }) return null
        val set = lift.sets.filter { it.type == SetType.NORMAL && it.isCompleted }.maxByOrNull { it.setNumber } ?: return null
        val range = requireNotNull(target.repRange)
        val reps = set.completedReps ?: return null
        val weight = set.completedWeight ?: return null
        val timestamp = set.completedAtTimestamp ?: return null
        if (set.id.isBlank() || set.workoutExerciseId != lift.id || set.setNumber <= 0 || set.exerciseType != ExerciseType.WEIGHT_REPS ||
            timestamp !in maxOf(earliest, workout.startedAtTimestamp)..requireNotNull(workout.completedAtTimestamp) ||
            reps !in range.min..range.max || !weight.isFinite() || weight <= 0.0 || weight > ExercisePrescription.MAX_WEIGHT ||
            set.completedAssistanceWeight != null || set.completedDurationSeconds != null || set.completedDistanceMeters != null ||
            set.feltManageable == false
        ) return null
        val minRir = target.effortTarget?.minRir ?: ProgressionEngine.MIN_QUALIFYING_RIR
        if (set.rir?.let { it !in minRir..SetOutcomeRules.MAX_RIR } == true ||
            set.rpe?.let { it !in SetOutcomeRules.MIN_RPE..ProgressionEngine.MAX_QUALIFYING_RPE } == true
        ) return null
        return FirstWeightReference(lift.exerciseId, weight, workout.weightUnit, reps, set.id)
    }
}
