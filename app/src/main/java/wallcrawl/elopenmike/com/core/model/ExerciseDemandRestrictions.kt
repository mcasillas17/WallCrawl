package wallcrawl.elopenmike.com.core.model

import wallcrawl.elopenmike.com.core.model.MovementDemand.*

/** The earlier self-reports restrict ordinary exercise choice independently of released condition support. */
internal class ExerciseDemandRestrictions private constructor(
    val active: Boolean,
    val recognizesReports: Boolean,
    private val hasReports: Boolean,
    private val avoid: Set<MovementDemand>,
    private val modify: Set<MovementDemand>
) {
    fun permitsReportDemands(exerciseId: String): Boolean = !hasReports ||
        (recognizesReports && exerciseDemands[exerciseId]?.none { it in avoid } == true)

    fun ranksLower(exerciseId: String): Boolean =
        modify.isNotEmpty() && exerciseDemands[exerciseId]?.any { it in modify } == true

    companion object {
        const val VERSION = "2026-11-02.3-joint-filter-v1"
        private val none = ExerciseDemandRestrictions(false, true, false, emptySet(), emptySet())

        fun forProfile(profile: UserProfile): ExerciseDemandRestrictions {
            val reports = profile.reportedConditions
            val joints = profile.trainingConstraints.filter { it != TrainingConstraint.LOW_IMPACT_ONLY }
            if (reports.isEmpty() && joints.isEmpty()) return none
            val avoid = reports.flatMapTo(mutableSetOf()) { avoidDemands[it].orEmpty() }
            val modify = reports.flatMapTo(mutableSetOf()) { modifyDemands[it].orEmpty() }
            joints.forEach { modify += jointDemands[it].orEmpty() }
            val recognized = reports.all { it in ReportedCondition.ids && it in modifyDemands }
            return ExerciseDemandRestrictions(true, recognized, reports.isNotEmpty(), avoid, modify - avoid)
        }
    }
}

internal fun clearsJointSensitivities(
    exerciseId: String,
    profile: UserProfile,
    explicitClearance: Set<TrainingConstraint>
): Boolean = profile.trainingConstraints.all { joint ->
    joint == TrainingConstraint.LOW_IMPACT_ONLY || joint in explicitClearance ||
        jointDemands[joint]?.let { conflicts -> exerciseDemands[exerciseId]?.none { it in conflicts } } == true
}

// Demand fields only, from v1.0.0-alpha.16 (e993a1770), assets/conditions/condition-guidance.json,
// version 2026-11-02.3. An empty assessed set is distinct from an exercise absent from that version.
private enum class MovementDemand {
    PROLONGED_SITTING,
    SUSTAINED_LOCOMOTION,
    PLYOMETRIC_IMPACT,
    HEAVY_EXTERNAL_LOAD,
    SINGLE_LEG_HIP_CONTROL,
    CROSS_BODY_HIP_STRETCH,
    NERVE_TENSIONING,
    HAMSTRING_LENGTH_LOAD,
    RESISTED_HIP_EXTERNAL_ROTATION,
    END_RANGE_HIP_EXTENSION,
    SIDE_LYING_HIP_COMPRESSION,
    SIDE_SUPPORT_HOLD,
    TRUNK_FLEXION_UNDER_LOAD,
    LONG_LYING_SIT_UP,
    END_RANGE_SPINE_POSITION,
    WRIST_EXTENSOR_WORK,
    FOREARM_TWIST_OR_PRONATED_LIFT,
    HAND_GRIP_LOAD,
    WRIST_END_RANGE_LOAD,
    DEEP_KNEE_FLEXION_LOAD,
    END_RANGE_KNEE_EXTENSION_LOAD
}

private val avoidDemands = mapOf<String, Set<MovementDemand>>(
    "gluteal_tendinopathy" to setOf(CROSS_BODY_HIP_STRETCH),
)

private val modifyDemands = mapOf<String, Set<MovementDemand>>(
    "piriformis_syndrome" to setOf(
        PROLONGED_SITTING, SUSTAINED_LOCOMOTION, PLYOMETRIC_IMPACT, HEAVY_EXTERNAL_LOAD, SINGLE_LEG_HIP_CONTROL,
        CROSS_BODY_HIP_STRETCH, NERVE_TENSIONING, HAMSTRING_LENGTH_LOAD, RESISTED_HIP_EXTERNAL_ROTATION,
        END_RANGE_HIP_EXTENSION
    ),
    "gluteal_tendinopathy" to setOf(
        SINGLE_LEG_HIP_CONTROL, HEAVY_EXTERNAL_LOAD, SIDE_LYING_HIP_COMPRESSION, SUSTAINED_LOCOMOTION,
        PLYOMETRIC_IMPACT, SIDE_SUPPORT_HOLD
    ),
    "lumbar_radiculopathy" to setOf(
        TRUNK_FLEXION_UNDER_LOAD, HEAVY_EXTERNAL_LOAD, HAMSTRING_LENGTH_LOAD, LONG_LYING_SIT_UP, SUSTAINED_LOCOMOTION,
        PLYOMETRIC_IMPACT
    ),
    "nonspecific_low_back_pain" to setOf(TRUNK_FLEXION_UNDER_LOAD, HEAVY_EXTERNAL_LOAD, END_RANGE_SPINE_POSITION),
    "rotator_cuff_related_shoulder_pain" to setOf(HEAVY_EXTERNAL_LOAD),
    "lateral_elbow_tendinopathy" to setOf(
        WRIST_EXTENSOR_WORK, FOREARM_TWIST_OR_PRONATED_LIFT, HEAVY_EXTERNAL_LOAD, HAND_GRIP_LOAD
    ),
    "carpal_tunnel_syndrome" to setOf(WRIST_END_RANGE_LOAD, HAND_GRIP_LOAD, NERVE_TENSIONING),
    "patellofemoral_pain" to setOf(
        DEEP_KNEE_FLEXION_LOAD, END_RANGE_KNEE_EXTENSION_LOAD, SUSTAINED_LOCOMOTION, PLYOMETRIC_IMPACT,
        PROLONGED_SITTING
    ),
)

private val jointDemands = mapOf<TrainingConstraint, Set<MovementDemand>>(
    TrainingConstraint.SHOULDER_SENSITIVE to setOf(HEAVY_EXTERNAL_LOAD),
    TrainingConstraint.ELBOW_SENSITIVE to setOf(
        HEAVY_EXTERNAL_LOAD, WRIST_EXTENSOR_WORK, FOREARM_TWIST_OR_PRONATED_LIFT, HAND_GRIP_LOAD
    ),
    TrainingConstraint.WRIST_SENSITIVE to setOf(NERVE_TENSIONING, HAND_GRIP_LOAD, WRIST_END_RANGE_LOAD),
    TrainingConstraint.LOWER_BACK_SENSITIVE to setOf(HEAVY_EXTERNAL_LOAD, TRUNK_FLEXION_UNDER_LOAD),
    TrainingConstraint.HIP_SENSITIVE to setOf(
        SUSTAINED_LOCOMOTION, PLYOMETRIC_IMPACT, HEAVY_EXTERNAL_LOAD, SINGLE_LEG_HIP_CONTROL, CROSS_BODY_HIP_STRETCH
    ),
    TrainingConstraint.KNEE_SENSITIVE to setOf(
        PROLONGED_SITTING, SUSTAINED_LOCOMOTION, PLYOMETRIC_IMPACT, DEEP_KNEE_FLEXION_LOAD,
        END_RANGE_KNEE_EXTENSION_LOAD
    ),
)

private val exerciseDemands = mapOf<String, Set<MovementDemand>>(
    "active-hang" to setOf(HAND_GRIP_LOAD),
    "archer-push-up" to setOf(WRIST_END_RANGE_LOAD),
    "arnold-press" to setOf(FOREARM_TWIST_OR_PRONATED_LIFT),
    "assisted-chin-up" to setOf(HAND_GRIP_LOAD),
    "assisted-pull-up" to setOf(HAND_GRIP_LOAD),
    "band-pull-apart" to emptySet(),
    "banded-clamshell" to setOf(RESISTED_HIP_EXTERNAL_ROTATION, SIDE_LYING_HIP_COMPRESSION),
    "banded-dead-bug" to emptySet(),
    "banded-donkey-kick" to setOf(END_RANGE_HIP_EXTENSION, WRIST_END_RANGE_LOAD),
    "banded-fire-hydrant" to setOf(RESISTED_HIP_EXTERNAL_ROTATION, WRIST_END_RANGE_LOAD),
    "banded-glute-bridge" to setOf(RESISTED_HIP_EXTERNAL_ROTATION),
    "banded-lateral-walk" to setOf(RESISTED_HIP_EXTERNAL_ROTATION),
    "banded-monster-walk" to setOf(RESISTED_HIP_EXTERNAL_ROTATION),
    "banded-seated-hip-abduction" to setOf(RESISTED_HIP_EXTERNAL_ROTATION),
    "banded-squat" to setOf(DEEP_KNEE_FLEXION_LOAD),
    "banded-standing-hip-abduction" to setOf(RESISTED_HIP_EXTERNAL_ROTATION, SINGLE_LEG_HIP_CONTROL),
    "barbell-back-squat" to setOf(DEEP_KNEE_FLEXION_LOAD, HEAVY_EXTERNAL_LOAD),
    "barbell-bench-press" to setOf(HEAVY_EXTERNAL_LOAD),
    "barbell-bicep-curl" to setOf(HEAVY_EXTERNAL_LOAD, WRIST_EXTENSOR_WORK),
    "barbell-glute-bridge" to setOf(END_RANGE_HIP_EXTENSION, HEAVY_EXTERNAL_LOAD),
    "barbell-row" to setOf(FOREARM_TWIST_OR_PRONATED_LIFT, HAMSTRING_LENGTH_LOAD, HEAVY_EXTERNAL_LOAD),
    "bench-dip" to setOf(WRIST_END_RANGE_LOAD),
    "bent-over-rear-delt-raise" to setOf(FOREARM_TWIST_OR_PRONATED_LIFT, HAMSTRING_LENGTH_LOAD),
    "bicep-curl" to setOf(WRIST_EXTENSOR_WORK),
    "bicycle-crunch" to emptySet(),
    "bird-dog" to setOf(WRIST_END_RANGE_LOAD),
    "bodyweight-squat" to setOf(DEEP_KNEE_FLEXION_LOAD),
    "bulgarian-split-squat" to setOf(DEEP_KNEE_FLEXION_LOAD, END_RANGE_HIP_EXTENSION, SINGLE_LEG_HIP_CONTROL),
    "cable-curl" to setOf(WRIST_EXTENSOR_WORK),
    "cable-fly" to emptySet(),
    "cable-front-raise" to setOf(FOREARM_TWIST_OR_PRONATED_LIFT),
    "cable-lateral-raise" to setOf(FOREARM_TWIST_OR_PRONATED_LIFT),
    "cable-pallof-hold" to emptySet(),
    "cable-pull-through" to setOf(HAMSTRING_LENGTH_LOAD),
    "cable-rear-delt-fly" to setOf(FOREARM_TWIST_OR_PRONATED_LIFT),
    "cable-triceps-pushdown" to setOf(WRIST_EXTENSOR_WORK),
    "cable-woodchop" to emptySet(),
    "captains-chair-knee-raise" to emptySet(),
    "chair-dip" to setOf(WRIST_END_RANGE_LOAD),
    "chest-supported-row" to setOf(HEAVY_EXTERNAL_LOAD),
    "chin-up" to setOf(HAND_GRIP_LOAD),
    "clamshell" to setOf(RESISTED_HIP_EXTERNAL_ROTATION, SIDE_LYING_HIP_COMPRESSION),
    "close-grip-bench-press" to setOf(HEAVY_EXTERNAL_LOAD),
    "close-grip-lat-pulldown" to emptySet(),
    "cossack-squat" to setOf(DEEP_KNEE_FLEXION_LOAD, SINGLE_LEG_HIP_CONTROL),
    "cross-body-shoulder-stretch" to emptySet(),
    "crunch" to emptySet(),
    "curtsy-lunge" to setOf(DEEP_KNEE_FLEXION_LOAD, SINGLE_LEG_HIP_CONTROL),
    "dead-bug" to emptySet(),
    "dead-hang" to setOf(HAND_GRIP_LOAD),
    "decline-bench-press" to setOf(HEAVY_EXTERNAL_LOAD),
    "decline-dumbbell-press" to emptySet(),
    "decline-push-up" to setOf(WRIST_END_RANGE_LOAD),
    "decline-sit-up" to emptySet(),
    "deficit-reverse-lunge" to setOf(DEEP_KNEE_FLEXION_LOAD, END_RANGE_HIP_EXTENSION, SINGLE_LEG_HIP_CONTROL),
    "diamond-push-up" to setOf(WRIST_END_RANGE_LOAD),
    "donkey-kick" to setOf(END_RANGE_HIP_EXTENSION, WRIST_END_RANGE_LOAD),
    "doorway-row" to emptySet(),
    "drag-curl" to setOf(HEAVY_EXTERNAL_LOAD, WRIST_EXTENSOR_WORK),
    "dumbbell-bench-press" to emptySet(),
    "dumbbell-bent-over-row" to setOf(HAMSTRING_LENGTH_LOAD),
    "dumbbell-curtsy-lunge" to setOf(DEEP_KNEE_FLEXION_LOAD, SINGLE_LEG_HIP_CONTROL),
    "dumbbell-fly" to emptySet(),
    "dumbbell-glute-bridge" to setOf(END_RANGE_HIP_EXTENSION),
    "dumbbell-hip-thrust" to setOf(END_RANGE_HIP_EXTENSION),
    "dumbbell-lateral-lunge" to setOf(DEEP_KNEE_FLEXION_LOAD, SINGLE_LEG_HIP_CONTROL),
    "dumbbell-lateral-raise" to setOf(FOREARM_TWIST_OR_PRONATED_LIFT),
    "dumbbell-romanian-deadlift" to setOf(HAMSTRING_LENGTH_LOAD),
    "dumbbell-shoulder-press" to emptySet(),
    "dumbbell-shrug" to setOf(HAND_GRIP_LOAD),
    "dumbbell-side-bend" to emptySet(),
    "dumbbell-skull-crusher" to setOf(WRIST_EXTENSOR_WORK),
    "dumbbell-sumo-squat" to setOf(DEEP_KNEE_FLEXION_LOAD),
    "face-pull" to emptySet(),
    "figure-four-stretch" to emptySet(),
    "flutter-kick" to emptySet(),
    "forward-lunge" to setOf(DEEP_KNEE_FLEXION_LOAD, END_RANGE_HIP_EXTENSION, SINGLE_LEG_HIP_CONTROL),
    "frog-pump" to emptySet(),
    "front-raise" to setOf(FOREARM_TWIST_OR_PRONATED_LIFT),
    "front-squat" to setOf(DEEP_KNEE_FLEXION_LOAD, HEAVY_EXTERNAL_LOAD, WRIST_END_RANGE_LOAD),
    "glute-bridge" to emptySet(),
    "glute-bridge-march" to setOf(SINGLE_LEG_HIP_CONTROL),
    "goblet-squat" to setOf(DEEP_KNEE_FLEXION_LOAD),
    "hack-squat" to setOf(DEEP_KNEE_FLEXION_LOAD, HEAVY_EXTERNAL_LOAD),
    "half-kneeling-pallof-press" to emptySet(),
    "hammer-curl" to setOf(WRIST_EXTENSOR_WORK),
    "hamstring-stretch" to setOf(HAMSTRING_LENGTH_LOAD),
    "hanging-knee-raise" to setOf(HAND_GRIP_LOAD),
    "heel-tap" to emptySet(),
    "hindu-push-up" to setOf(END_RANGE_SPINE_POSITION, WRIST_END_RANGE_LOAD),
    "hip-abduction-machine" to setOf(HEAVY_EXTERNAL_LOAD, RESISTED_HIP_EXTERNAL_ROTATION),
    "hip-adduction-machine" to setOf(HEAVY_EXTERNAL_LOAD),
    "hip-thrust" to setOf(END_RANGE_HIP_EXTENSION, HEAVY_EXTERNAL_LOAD),
    "hollow-rock" to emptySet(),
    "inchworm" to setOf(HAMSTRING_LENGTH_LOAD, WRIST_END_RANGE_LOAD),
    "incline-bench-press" to setOf(HEAVY_EXTERNAL_LOAD),
    "incline-cable-fly" to emptySet(),
    "incline-dumbbell-curl" to setOf(WRIST_EXTENSOR_WORK),
    "incline-dumbbell-press" to emptySet(),
    "incline-push-up" to setOf(WRIST_END_RANGE_LOAD),
    "jump-squat" to setOf(DEEP_KNEE_FLEXION_LOAD, PLYOMETRIC_IMPACT),
    "kettlebell-romanian-deadlift" to setOf(HAMSTRING_LENGTH_LOAD),
    "kettlebell-swing" to setOf(HAMSTRING_LENGTH_LOAD, HAND_GRIP_LOAD),
    "l-sit-pull-up" to setOf(HAND_GRIP_LOAD),
    "lat-pulldown" to emptySet(),
    "lateral-lunge" to setOf(DEEP_KNEE_FLEXION_LOAD, SINGLE_LEG_HIP_CONTROL),
    "leg-curl" to setOf(HAMSTRING_LENGTH_LOAD, HEAVY_EXTERNAL_LOAD),
    "leg-extension" to setOf(END_RANGE_KNEE_EXTENSION_LOAD, HEAVY_EXTERNAL_LOAD),
    "leg-press" to setOf(DEEP_KNEE_FLEXION_LOAD, HEAVY_EXTERNAL_LOAD),
    "leg-press-calf-raise" to setOf(HEAVY_EXTERNAL_LOAD),
    "lying-leg-curl" to setOf(HAMSTRING_LENGTH_LOAD, HEAVY_EXTERNAL_LOAD),
    "lying-leg-raise" to emptySet(),
    "lying-piriformis-stretch" to setOf(CROSS_BODY_HIP_STRETCH),
    "machine-chest-press" to setOf(HEAVY_EXTERNAL_LOAD),
    "machine-lateral-raise" to setOf(HEAVY_EXTERNAL_LOAD),
    "machine-shoulder-press" to setOf(HEAVY_EXTERNAL_LOAD),
    "one-arm-dumbbell-row" to setOf(HAMSTRING_LENGTH_LOAD),
    "overhead-press" to setOf(HEAVY_EXTERNAL_LOAD),
    "overhead-tricep-extension" to setOf(WRIST_EXTENSOR_WORK),
    "pallof-press" to emptySet(),
    "pec-deck" to setOf(HEAVY_EXTERNAL_LOAD),
    "pike-push-up" to setOf(HAMSTRING_LENGTH_LOAD, WRIST_END_RANGE_LOAD),
    "pistol-squat" to setOf(DEEP_KNEE_FLEXION_LOAD, SINGLE_LEG_HIP_CONTROL),
    "plank-shoulder-tap" to setOf(WRIST_END_RANGE_LOAD),
    "plate-front-raise" to setOf(HAND_GRIP_LOAD),
    "preacher-curl" to setOf(HEAVY_EXTERNAL_LOAD, WRIST_EXTENSOR_WORK),
    "prone-y-raise" to emptySet(),
    "pull-ups" to setOf(HAND_GRIP_LOAD),
    "push-press" to setOf(HEAVY_EXTERNAL_LOAD),
    "push-up" to setOf(WRIST_END_RANGE_LOAD),
    "push-up-shoulder-tap" to setOf(WRIST_END_RANGE_LOAD),
    "rear-delt-fly" to setOf(FOREARM_TWIST_OR_PRONATED_LIFT, HAMSTRING_LENGTH_LOAD),
    "reverse-crunch" to emptySet(),
    "reverse-curl" to setOf(FOREARM_TWIST_OR_PRONATED_LIFT, HEAVY_EXTERNAL_LOAD, WRIST_EXTENSOR_WORK),
    "reverse-lunge" to setOf(DEEP_KNEE_FLEXION_LOAD, END_RANGE_HIP_EXTENSION, SINGLE_LEG_HIP_CONTROL),
    "reverse-pec-deck" to setOf(HEAVY_EXTERNAL_LOAD),
    "reverse-snow-angel" to emptySet(),
    "romanian-deadlift" to setOf(FOREARM_TWIST_OR_PRONATED_LIFT, HAMSTRING_LENGTH_LOAD, HEAVY_EXTERNAL_LOAD),
    "rope-hammer-curl" to setOf(WRIST_EXTENSOR_WORK),
    "rope-tricep-pushdown" to setOf(WRIST_EXTENSOR_WORK),
    "scapular-pull-up" to setOf(HAND_GRIP_LOAD),
    "scapular-push-up" to setOf(WRIST_END_RANGE_LOAD),
    "seated-calf-raise" to setOf(HEAVY_EXTERNAL_LOAD),
    "seated-leg-curl" to setOf(HAMSTRING_LENGTH_LOAD, HEAVY_EXTERNAL_LOAD),
    "seated-row" to emptySet(),
    "shrug" to setOf(FOREARM_TWIST_OR_PRONATED_LIFT, HAND_GRIP_LOAD, HEAVY_EXTERNAL_LOAD),
    "side-lying-hip-abduction" to setOf(RESISTED_HIP_EXTERNAL_ROTATION, SIDE_LYING_HIP_COMPRESSION),
    "side-lying-isometric-hip-abduction-hold" to setOf(RESISTED_HIP_EXTERNAL_ROTATION, SIDE_LYING_HIP_COMPRESSION),
    "side-lying-leg-raise" to setOf(RESISTED_HIP_EXTERNAL_ROTATION, SIDE_LYING_HIP_COMPRESSION),
    "side-plank" to setOf(RESISTED_HIP_EXTERNAL_ROTATION, SIDE_SUPPORT_HOLD),
    "side-plank-hip-dip" to setOf(RESISTED_HIP_EXTERNAL_ROTATION, SIDE_SUPPORT_HOLD),
    "single-arm-dumbbell-tricep-extension" to setOf(WRIST_EXTENSOR_WORK),
    "single-dumbbell-skullcrusher" to setOf(WRIST_EXTENSOR_WORK),
    "single-leg-glute-bridge" to setOf(RESISTED_HIP_EXTERNAL_ROTATION, SINGLE_LEG_HIP_CONTROL),
    "single-leg-romanian-deadlift" to setOf(HAMSTRING_LENGTH_LOAD, SINGLE_LEG_HIP_CONTROL),
    "skull-crusher" to setOf(HEAVY_EXTERNAL_LOAD, WRIST_EXTENSOR_WORK),
    "smith-machine-bench-press" to setOf(HEAVY_EXTERNAL_LOAD),
    "smith-machine-bulgarian-split-squat" to setOf(
        DEEP_KNEE_FLEXION_LOAD, END_RANGE_HIP_EXTENSION, HEAVY_EXTERNAL_LOAD, SINGLE_LEG_HIP_CONTROL
    ),
    "smith-machine-hip-thrust" to setOf(END_RANGE_HIP_EXTENSION, HEAVY_EXTERNAL_LOAD),
    "smith-machine-reverse-lunge" to setOf(
        DEEP_KNEE_FLEXION_LOAD, END_RANGE_HIP_EXTENSION, HEAVY_EXTERNAL_LOAD, SINGLE_LEG_HIP_CONTROL
    ),
    "smith-machine-romanian-deadlift" to setOf(FOREARM_TWIST_OR_PRONATED_LIFT, HAMSTRING_LENGTH_LOAD, HEAVY_EXTERNAL_LOAD),
    "smith-machine-split-squat" to setOf(
        DEEP_KNEE_FLEXION_LOAD, END_RANGE_HIP_EXTENSION, HEAVY_EXTERNAL_LOAD, SINGLE_LEG_HIP_CONTROL
    ),
    "smith-machine-squat" to setOf(DEEP_KNEE_FLEXION_LOAD, HEAVY_EXTERNAL_LOAD),
    "split-squat" to setOf(DEEP_KNEE_FLEXION_LOAD, END_RANGE_HIP_EXTENSION, SINGLE_LEG_HIP_CONTROL),
    "standing-calf-raise" to setOf(HEAVY_EXTERNAL_LOAD),
    "standing-dumbbell-press" to emptySet(),
    "standing-isometric-gluteal-contraction" to setOf(SINGLE_LEG_HIP_CONTROL),
    "step-down" to setOf(DEEP_KNEE_FLEXION_LOAD, SINGLE_LEG_HIP_CONTROL),
    "step-up" to setOf(DEEP_KNEE_FLEXION_LOAD, SINGLE_LEG_HIP_CONTROL),
    "straight-arm-pulldown" to emptySet(),
    "straight-leg-raise" to emptySet(),
    "superman" to setOf(END_RANGE_HIP_EXTENSION, END_RANGE_SPINE_POSITION),
    "superman-hold" to setOf(END_RANGE_HIP_EXTENSION, END_RANGE_SPINE_POSITION),
    "t-bar-row" to setOf(HAMSTRING_LENGTH_LOAD, HEAVY_EXTERNAL_LOAD),
    "tendon-gliding-sequence" to emptySet(),
    "towel-pull-up" to setOf(HAND_GRIP_LOAD),
    "tricep-kickback" to setOf(WRIST_EXTENSOR_WORK),
    "typewriter-push-up" to setOf(WRIST_END_RANGE_LOAD),
    "upright-row" to setOf(FOREARM_TWIST_OR_PRONATED_LIFT, HEAVY_EXTERNAL_LOAD),
    "v-up" to setOf(LONG_LYING_SIT_UP),
    "walking-lunge" to setOf(DEEP_KNEE_FLEXION_LOAD, END_RANGE_HIP_EXTENSION, SINGLE_LEG_HIP_CONTROL),
    "wall-handstand-push-up" to setOf(WRIST_END_RANGE_LOAD),
    "wall-push-up" to setOf(WRIST_END_RANGE_LOAD),
    "wall-sit" to setOf(DEEP_KNEE_FLEXION_LOAD),
    "wall-walk" to setOf(WRIST_END_RANGE_LOAD),
    "weighted-crunch" to setOf(TRUNK_FLEXION_UNDER_LOAD),
    "weighted-push-up" to setOf(WRIST_END_RANGE_LOAD),
    "weighted-russian-twist" to setOf(TRUNK_FLEXION_UNDER_LOAD),
    "wide-grip-lat-pulldown" to emptySet(),
    "wide-push-up" to setOf(WRIST_END_RANGE_LOAD),
    "wrist-extensor-stretch" to setOf(WRIST_END_RANGE_LOAD),
)
