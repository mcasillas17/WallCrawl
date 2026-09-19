package wallcrawl.elopenmike.com.feature.today

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.DeloadAction
import wallcrawl.elopenmike.com.core.model.DeloadSource
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
import wallcrawl.elopenmike.com.core.ui.theme.TextWhite

@Composable
internal fun TodayDeloadCard(
    state: TodayDeloadState,
    onAction: (DeloadAction) -> Unit,
    preview: (@Composable () -> Unit)? = null
) {
    WallCrawlCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val accepted = state.acceptedChoice != null
            Text(
                stringResource(if (accepted) R.string.today_deload_accepted else R.string.today_deload_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() }
            )
            val offer = state.acceptedChoice?.offer ?: state.offer
            if (offer != null) {
                Text(stringResource(when (offer.source) {
                    DeloadSource.EXPLICIT_REQUEST -> R.string.today_deload_reason_request
                    DeloadSource.RETURNING -> R.string.today_deload_reason_returning
                }), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.today_deload_effect), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.offer != null) preview?.invoke()
            }
            if (accepted) {
                Text(stringResource(R.string.today_deload_accepted_detail), color = MaterialTheme.colorScheme.onSurfaceVariant)
                DeloadButton(stringResource(R.string.today_deload_cancel), !state.isSaving) {
                    onAction(DeloadAction.CANCEL)
                }
            } else if (state.offer != null) {
                DeloadButton(stringResource(R.string.today_deload_accept), !state.isSaving, primary = true) {
                    onAction(DeloadAction.ACCEPT)
                }
                DeloadButton(stringResource(R.string.today_deload_decline), !state.isSaving) {
                    onAction(DeloadAction.DECLINE)
                }
                DeloadButton(stringResource(R.string.today_deload_dismiss), !state.isSaving) {
                    onAction(DeloadAction.DISMISS)
                }
            }
            if (!accepted) {
                DeloadButton(stringResource(R.string.today_deload_request), !state.isSaving) {
                    onAction(DeloadAction.REQUEST)
                }
            }
            if (state.isSaving) {
                Text(stringResource(R.string.today_deload_saving), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                })
            }
            state.error?.let { error ->
                Text(stringResource(error.messageRes), color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
    }
}

@Composable
internal fun DeloadPrescriptionPreview(
    workout: GeneratedWorkout,
    prescriptionUnit: WeightUnit
) {
    if (workout.progressionDecisions.isEmpty()) return
    val vocabulary = LocalExerciseVocabulary.current
    val locale = LocalConfiguration.current.locales[0]
    Text(stringResource(R.string.today_deload_preview_title), color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold)
    workout.progressionDecisions.forEach { decision ->
        val reference = decision.referencePrescription
        val proposed = reference.copy(targetSets = (reference.targetSets - 1).coerceAtLeast(1))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(vocabulary.exerciseName(decision.exerciseId), color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold)
            Text(prescriptionSummary(PlannedExercise(decision.exerciseId, proposed)),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            proposed.targetWeight?.let {
                Text(stringResource(R.string.previous_load,
                    LocaleFormatting.formatMeasurement(it, locale), prescriptionUnit.symbol),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            proposed.targetAssistanceWeight?.let {
                Text(stringResource(R.string.workout_assistance,
                    LocaleFormatting.formatMeasurement(it, locale), prescriptionUnit.symbol),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // The existing compact summary uses duration when both distance and time exist.
            if (proposed.targetDurationSeconds != null) {
                proposed.targetDistanceMeters?.let {
                    Text(stringResource(R.string.prescription_meters, LocaleFormatting.formatMeasurement(it, locale)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(stringResource(R.string.today_deload_preview_rest,
                stringResource(R.string.prescription_seconds, LocaleFormatting.formatCount(proposed.restSeconds, locale))),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            proposed.effortTarget?.let {
                Text(stringResource(R.string.today_deload_preview_effort,
                    stringResource(R.string.prescription_rep_range,
                        LocaleFormatting.formatCount(it.minRir, locale),
                        LocaleFormatting.formatCount(it.maxRir, locale))),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Existing button colors/shapes, but no fixed height: translated large text must wrap. */
@Composable
private fun DeloadButton(text: String, enabled: Boolean, primary: Boolean = false, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(if (primary) 14.dp else 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) CrimsonRedPrimary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (primary) TextWhite else MaterialTheme.colorScheme.onSurface
        ),
        border = if (primary) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Text(text, fontWeight = FontWeight.SemiBold,
            color = if (primary) TextWhite else MaterialTheme.colorScheme.onSurface)
    }
}
