package wallcrawl.elopenmike.com.feature.today

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.*
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlSecondaryButton
import wallcrawl.elopenmike.com.core.ui.components.a11yHeading
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary

@Composable
internal fun TodayDeloadCard(
    state: TodayDeloadState,
    onAction: (DeloadAction) -> Unit,
    enabled: Boolean,
    workout: GeneratedWorkout?,
    unit: WeightUnit?
) {
    val accepted = state.acceptedChoice != null
    val offer = state.acceptedChoice?.offer ?: state.offer ?: return
    Text(stringResource(when (offer.source) {
        DeloadSource.EXPLICIT_REQUEST -> R.string.today_deload_reason_request
        DeloadSource.RETURNING -> R.string.today_deload_reason_returning
    }))
    Text(stringResource(R.string.today_deload_effect))
    if (accepted) {
        Text(stringResource(R.string.today_deload_accepted_detail))
        WallCrawlSecondaryButton(stringResource(R.string.today_deload_cancel),
            { onAction(DeloadAction.CANCEL) }, enabled = enabled)
    } else {
        val decisions = workout?.progressionDecisions.orEmpty()
        if (unit != null && decisions.isNotEmpty()) {
            Text(stringResource(R.string.today_deload_preview_title), Modifier.a11yHeading(), style = MaterialTheme.typography.titleMedium)
            decisions.forEach { decision ->
                val reference = decision.referencePrescription
                Text(LocalExerciseVocabulary.current.exerciseName(decision.exerciseId), Modifier.a11yHeading(), style = MaterialTheme.typography.titleSmall)
                PrescriptionPreview(PlannedExercise(decision.exerciseId,
                    reference.copy(targetSets = (reference.targetSets - 1).coerceAtLeast(1))), unit)
            }
        } else {
            Text(stringResource(R.string.guided_train_adjustment_unavailable))
        }
        WallCrawlPrimaryButton(stringResource(R.string.today_deload_accept),
            { onAction(DeloadAction.ACCEPT) }, enabled = enabled && unit != null && decisions.isNotEmpty())
        WallCrawlSecondaryButton(stringResource(R.string.today_deload_decline), { onAction(DeloadAction.DECLINE) }, enabled = enabled)
        WallCrawlSecondaryButton(stringResource(R.string.today_deload_dismiss), { onAction(DeloadAction.DISMISS) }, enabled = enabled)
    }
}

@Composable
internal fun PrescriptionPreview(exercise: PlannedExercise, unit: WeightUnit) {
    val locale = LocalConfiguration.current.locales[0]
    fun count(value: Int) = LocaleFormatting.formatCount(value, locale)
    val target = exercise.prescription
    val values = buildList {
        target.repRange?.let { add(if (it.min == it.max) count(it.min)
            else stringResource(R.string.prescription_rep_range, count(it.min), count(it.max))) }
        target.targetDurationSeconds?.let { add(stringResource(R.string.prescription_seconds, count(it))) }
        target.targetDistanceMeters?.let { add(stringResource(R.string.prescription_meters,
            LocaleFormatting.formatMeasurement(it, locale))) }
    }
    val sets = stringResource(R.string.prescription_sets_by_reps, count(target.targetSets), values.joinToString(" · "))
    Text(target.holdSeconds?.let { stringResource(R.string.workout_target_with_hold, sets, count(it)) } ?: sets)
    target.targetWeight?.let { Text(stringResource(R.string.previous_load, LocaleFormatting.formatMeasurement(it, locale), unit.symbol)) }
    target.targetAssistanceWeight?.let { Text(stringResource(R.string.workout_assistance, LocaleFormatting.formatMeasurement(it, locale), unit.symbol)) }
    Text(stringResource(R.string.today_deload_preview_rest, stringResource(R.string.prescription_seconds, count(target.restSeconds))))
    target.effortTarget?.let {
        Text(stringResource(R.string.today_deload_preview_effort, stringResource(R.string.prescription_rep_range, count(it.minRir), count(it.maxRir))))
    }
}
