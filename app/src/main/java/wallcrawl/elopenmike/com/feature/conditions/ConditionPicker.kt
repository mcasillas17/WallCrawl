package wallcrawl.elopenmike.com.feature.conditions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.conditions.ConditionGuidanceResult
import wallcrawl.elopenmike.com.core.conditions.textIn
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlCard
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlPrimaryButton
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlSecondaryButton

/** A condition the user picked but has not yet acknowledged the warning signs for. */
data class PendingCondition(val id: String, val acknowledged: Boolean = false)

/** The resource for a condition's display name, or a neutral label for an id this build does not know. */
fun conditionNameRes(id: String): Int = when (id) {
    "piriformis_syndrome" -> R.string.condition_name_piriformis_syndrome
    "gluteal_tendinopathy" -> R.string.condition_name_gluteal_tendinopathy
    "lumbar_radiculopathy" -> R.string.condition_name_lumbar_radiculopathy
    "nonspecific_low_back_pain" -> R.string.condition_name_nonspecific_low_back_pain
    "rotator_cuff_related_shoulder_pain" -> R.string.condition_name_rotator_cuff_related_shoulder_pain
    "lateral_elbow_tendinopathy" -> R.string.condition_name_lateral_elbow_tendinopathy
    "carpal_tunnel_syndrome" -> R.string.condition_name_carpal_tunnel_syndrome
    "patellofemoral_pain" -> R.string.condition_name_patellofemoral_pain
    else -> R.string.condition_name_unknown
}

/**
 * Optional condition selection, shared by Profile and onboarding.
 *
 * Adding shows the overlay's warning signs and notes in the reader's language and needs an
 * acknowledgement; it never blocks anything else. Removing needs none. When the guidance is
 * [ConditionGuidanceResult.Unavailable] nothing can be added, and what is already saved stays
 * listed and removable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConditionPicker(
    guidance: ConditionGuidanceResult?,
    selected: Set<String>,
    pending: PendingCondition?,
    onSelect: (String) -> Unit,
    onAcknowledge: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    @androidx.annotation.StringRes errorRes: Int? = null
) {
    val language = LocalConfiguration.current.locales[0].language
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.condition_section_heading),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.condition_section_hint),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        selected.sorted().forEach { id ->
            val name = stringResource(conditionNameRes(id))
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val removeDescription = stringResource(R.string.condition_remove_description, name)
                TextButton(
                    onClick = { onRemove(id) },
                    modifier = Modifier
                        .testTag("condition-remove-$id")
                        .semantics { contentDescription = removeDescription }
                ) {
                    Text(stringResource(R.string.condition_remove_action))
                }
            }
        }

        errorRes?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(error),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("condition-save-error")
            )
        }

        when (guidance) {
            null -> Unit
            ConditionGuidanceResult.Unavailable -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.condition_unavailable),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("condition-unavailable")
                )
            }
            is ConditionGuidanceResult.Loaded -> {
                val addable = guidance.guidance.conditions.keys.sorted().filter { it !in selected }
                if (addable.isNotEmpty() && pending == null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.condition_add_prompt),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        addable.forEach { id ->
                            AssistChip(
                                onClick = { onSelect(id) },
                                label = { Text(stringResource(conditionNameRes(id)), fontSize = 12.sp) },
                                modifier = Modifier.testTag("condition-chip-$id")
                            )
                        }
                    }
                }
                val record = pending?.let { guidance.guidance.conditions[it.id] }
                if (pending != null && record != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    WallCrawlCard(
                        modifier = Modifier.testTag("condition-acknowledgement"),
                        contentPadding = 12.dp
                    ) {
                        Text(
                            text = stringResource(
                                R.string.condition_ack_heading,
                                stringResource(conditionNameRes(pending.id))
                            ),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.condition_red_flags_heading),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        record.redFlags.textIn(language).forEach { flag ->
                            Text(
                                text = "• $flag",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        val notes = record.notes.textIn(language)
                        if (notes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = stringResource(R.string.condition_notes_heading),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            notes.forEach { note ->
                                Text(
                                    text = "• $note",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .heightIn(min = 48.dp)
                                .toggleable(
                                    value = pending.acknowledged,
                                    role = Role.Checkbox,
                                    onValueChange = onAcknowledge
                                )
                                .testTag("condition-ack"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = pending.acknowledged, onCheckedChange = null)
                            Text(
                                text = stringResource(R.string.condition_ack_checkbox),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(start = 8.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        WallCrawlPrimaryButton(
                            text = stringResource(R.string.condition_ack_confirm),
                            onClick = onConfirm,
                            enabled = pending.acknowledged,
                            modifier = Modifier.fillMaxWidth().testTag("condition-confirm")
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        WallCrawlSecondaryButton(
                            text = stringResource(R.string.condition_ack_cancel),
                            onClick = onCancel,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
