package wallcrawl.elopenmike.com.feature.today

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisualProvider
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.PrescriptionPurpose
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.ui.components.ExerciseStill
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.localization.generatedWorkoutTitle
import wallcrawl.elopenmike.com.core.ui.localization.progressionNotice
import wallcrawl.elopenmike.com.core.ui.theme.GraphiteSurfaceElevated

private data class PreviewRow(val exercise: PlannedExercise, val effort: String?, val notice: String?)

/**
 * The generated workout as Warm-up / Main / Cooldown sections. An effort target or progression
 * notice that every training exercise shares is said once under the header; a row shows only
 * what differs. Descriptions open on tap so the whole session fits in about one screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkoutPreviewSheet(
    state: TodayUiState.Success,
    visualProvider: ExerciseVisualProvider?,
    startLabel: String,
    startEnabled: Boolean,
    onStart: () -> Unit,
    onDismiss: () -> Unit
) {
    val workout = state.suggestedWorkout
    val locale = LocalConfiguration.current.locales[0]
    val vocabulary = LocalExerciseVocabulary.current
    fun count(value: Int) = LocaleFormatting.formatCount(value, locale)

    val rows = workout.exercises.map { exercise ->
        val training = !exercise.prescription.purpose.isWorkoutPhase
        PreviewRow(
            exercise,
            exercise.prescription.effortTarget?.takeIf { training }?.let {
                stringResource(R.string.today_deload_preview_effort,
                    stringResource(R.string.prescription_rep_range, count(it.minRir), count(it.maxRir)))
            },
            workout.progressionDecisions.firstOrNull { it.exerciseId == exercise.exerciseId }?.let {
                progressionNotice(it, state.prescriptionUnit, state.prescriptionUnit)
            }
        )
    }
    val trainingRows = rows.filterNot { it.exercise.prescription.purpose.isWorkoutPhase }
    fun shared(pick: (PreviewRow) -> String?) =
        trainingRows.map(pick).distinct().singleOrNull()?.takeIf { trainingRows.size > 1 }
    val sharedEffort = shared { it.effort }
    val sharedNotice = shared { it.notice }
    val sections = listOf(
        R.string.history_warmup to rows.filter { it.exercise.prescription.purpose == PrescriptionPurpose.WARMUP },
        R.string.today_preview_main to trainingRows,
        R.string.workout_cooldown to rows.filter { it.exercise.prescription.purpose == PrescriptionPurpose.COOLDOWN }
    ).filter { it.second.isNotEmpty() }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    // No drag gestures: Material's sheet stretches when pulled up past its open position, so
    // trying to scroll a list that already fits made the whole sheet jump on every swipe.
    // Close, Back and the scrim still dismiss it.
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = false) {
        val pane = stringResource(R.string.guided_train_preview)
        Column(Modifier.fillMaxWidth().navigationBarsPadding().semantics { paneTitle = pane }) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(generatedWorkoutTitle(workout.title), style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                    val sets = trainingRows.sumOf { it.exercise.targetSets }
                    Text(listOf(
                        stringResource(R.string.today_stat_duration, count(workout.estimatedDurationMinutes)),
                        pluralStringResource(R.plurals.count_exercises, trainingRows.size, count(trainingRows.size)),
                        pluralStringResource(R.plurals.count_sets, sets, count(sets))
                    ).joinToString(stringResource(R.string.detail_separator)), style = MaterialTheme.typography.bodyMedium)
                    val focus = vocabulary.muscles(workout.focusMuscles).joinToString(stringResource(R.string.detail_separator))
                    if (focus.isNotEmpty()) Text(focus, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                }
            }
            val note = listOfNotNull(sharedNotice, sharedEffort)
            if (note.isNotEmpty()) Row(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp).fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp))
                Column { note.forEach { Text(it, style = MaterialTheme.typography.bodySmall) } }
            }
            LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                sections.forEach { (title, sectionRows) ->
                    item(key = "section-$title") {
                        Text(stringResource(title).uppercase(locale), style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() })
                    }
                    items(sectionRows, key = { it.exercise.exerciseId + it.exercise.prescription.purpose }) { row ->
                        val id = row.exercise.exerciseId
                        // Generated notes are the catalog's English text; show the reader's language.
                        val description = vocabulary.coachingSummary(id) ?: row.exercise.notes.takeIf(String::isNotBlank)
                        val open = expanded[id] == true
                        PreviewExerciseRow(row, state.prescriptionUnit, visualProvider, description, open,
                            effort = row.effort?.takeIf { it != sharedEffort },
                            notice = row.notice?.takeIf { it != sharedNotice }) { expanded[id] = !open }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                WallCrawlPrimaryButton(startLabel, onStart, enabled = startEnabled, showForwardArrow = true)
            }
        }
    }
}

@Composable
private fun PreviewExerciseRow(
    row: PreviewRow,
    unit: WeightUnit,
    visualProvider: ExerciseVisualProvider?,
    description: String?,
    open: Boolean,
    effort: String?,
    notice: String?,
    onToggle: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    val target = row.exercise.prescription
    val phase = target.purpose.isWorkoutPhase
    val rest = target.restSeconds.takeIf { it > 0 && !phase }?.let { seconds ->
        val time = if (seconds % 60 == 0) stringResource(R.string.prescription_minutes, LocaleFormatting.formatCount(seconds / 60, locale))
            else stringResource(R.string.prescription_seconds, LocaleFormatting.formatCount(seconds, locale))
        stringResource(R.string.editor_summary_rest, time)
    }
    val dose = listOfNotNull(prescriptionSetsText(target), rest).joinToString(stringResource(R.string.detail_separator))
    val toggle = if (description != null) Modifier.clickable(role = Role.Button, onClick = onToggle) else Modifier
    Row(Modifier.fillMaxWidth().then(toggle).heightIn(min = 56.dp).padding(vertical = 8.dp).animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // The illustrations are drawn light-on-dark, as on the Train poster, in either theme.
        Box(Modifier.size(56.dp).background(GraphiteSurfaceElevated, RoundedCornerShape(12.dp))
            .clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
            if (visualProvider != null) ExerciseStill(row.exercise.exerciseId, visualProvider, Modifier.size(48.dp))
        }
        Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(LocalExerciseVocabulary.current.exerciseName(row.exercise.exerciseId),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("preview-exercise-name"))
            Text(dose, style = MaterialTheme.typography.bodyMedium)
            listOfNotNull(
                target.targetWeight?.let {
                    stringResource(R.string.previous_load, LocaleFormatting.formatMeasurement(it, locale), unit.symbol)
                },
                target.targetAssistanceWeight?.let {
                    stringResource(R.string.workout_assistance, LocaleFormatting.formatMeasurement(it, locale), unit.symbol)
                },
                effort, notice
            ).forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (open && description != null) Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        if (description != null) Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp).rotate(if (open) 180f else 0f))
    }
}
