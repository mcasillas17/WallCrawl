package wallcrawl.elopenmike.com.core.ui.localization

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ProgressionAxis
import wallcrawl.elopenmike.com.core.model.ProgressionDecision
import wallcrawl.elopenmike.com.core.model.ProgressionReason
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.convertWeight
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting

@Composable
fun progressionNotice(decision: ProgressionDecision, prescriptionUnit: WeightUnit, displayUnit: WeightUnit): String {
    if (decision.reason != ProgressionReason.ADVANCED) {
        return stringResource(requireNotNull(progressionHoldReasonRes(decision.reason)))
    }
    val axis = requireNotNull(decision.axis)
    val label = stringResource(progressionAxisRes(axis))
    return stringResource(
        R.string.progression_advanced, label,
        progressionTarget(decision.referencePrescription, axis, prescriptionUnit, displayUnit),
        progressionTarget(decision.prescription, axis, prescriptionUnit, displayUnit)
    )
}

/** Historical provenance has a reason and axis, but no frozen before/after prescription pair. */
fun progressionHoldReasonRes(reason: ProgressionReason): Int? = when (reason) {
    ProgressionReason.HOLD_INSUFFICIENT_HISTORY, ProgressionReason.HOLD_DUPLICATE_OBSERVATION,
    ProgressionReason.HOLD_INCOMPLETE_WORK, ProgressionReason.HOLD_INVALID_OBSERVATION ->
        R.string.progression_hold_evidence
    ProgressionReason.HOLD_LEGACY_PATH -> R.string.progression_hold_legacy
    ProgressionReason.HOLD_UNSUPPORTED_AUTOMATIC_SHAPE -> R.string.progression_hold_unsupported
    ProgressionReason.HOLD_UNKNOWN_LOAD -> R.string.progression_hold_unknown_load
    ProgressionReason.HOLD_TARGETS_CHANGED, ProgressionReason.HOLD_CONFIGURATION_CHANGED ->
        R.string.progression_hold_changed
    ProgressionReason.HOLD_TARGET_NOT_MET -> R.string.progression_hold_target
    ProgressionReason.HOLD_MANAGEABLE_NOT_CONFIRMED -> R.string.progression_hold_manageable
    ProgressionReason.HOLD_MISSING_EFFORT -> R.string.progression_hold_effort_missing
    ProgressionReason.HOLD_EFFORT_NOT_QUALIFYING -> R.string.progression_hold_effort
    ProgressionReason.HOLD_AT_BOUND -> R.string.progression_hold_bound
    ProgressionReason.HOLD_RETURNING_GUIDANCE -> R.string.progression_hold_returning
    ProgressionReason.HOLD_ACCEPTED_DELOAD -> R.string.progression_hold_deload
    ProgressionReason.HOLD_VALIDATION_REPAIR -> R.string.progression_hold_repair
    ProgressionReason.ADVANCED -> null
}

fun progressionAxisRes(axis: ProgressionAxis): Int = when (axis) {
    ProgressionAxis.LOAD -> R.string.progression_axis_load
    ProgressionAxis.REP_RANGE -> R.string.progression_axis_reps
    ProgressionAxis.ASSISTANCE -> R.string.progression_axis_assistance
    ProgressionAxis.DURATION -> R.string.progression_axis_duration
    ProgressionAxis.DISTANCE -> R.string.progression_axis_distance
}

@Composable
private fun progressionTarget(
    prescription: ExercisePrescription, axis: ProgressionAxis, sourceUnit: WeightUnit, displayUnit: WeightUnit
): String {
    val locale = LocalConfiguration.current.locales[0]
    return when (axis) {
        ProgressionAxis.LOAD, ProgressionAxis.ASSISTANCE -> {
            val value = if (axis == ProgressionAxis.LOAD) prescription.targetWeight else prescription.targetAssistanceWeight
            stringResource(R.string.previous_load,
                LocaleFormatting.formatMeasurement(convertWeight(requireNotNull(value), sourceUnit, displayUnit), locale),
                displayUnit.symbol)
        }
        ProgressionAxis.REP_RANGE -> {
            val range = requireNotNull(prescription.repRange)
            stringResource(R.string.prescription_rep_range,
                LocaleFormatting.formatCount(range.min, locale), LocaleFormatting.formatCount(range.max, locale))
        }
        ProgressionAxis.DURATION -> stringResource(R.string.prescription_seconds,
            LocaleFormatting.formatCount(requireNotNull(prescription.targetDurationSeconds), locale))
        ProgressionAxis.DISTANCE -> stringResource(R.string.prescription_meters,
            LocaleFormatting.formatMeasurement(requireNotNull(prescription.targetDistanceMeters), locale))
    }
}
