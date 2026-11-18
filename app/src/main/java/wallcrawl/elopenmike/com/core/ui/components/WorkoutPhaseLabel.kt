package wallcrawl.elopenmike.com.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.PrescriptionPurpose

@Composable
fun WorkoutPhaseLabel(purpose: PrescriptionPurpose) {
    val label = when (purpose) {
        PrescriptionPurpose.WARMUP -> R.string.history_warmup
        PrescriptionPurpose.COOLDOWN -> R.string.workout_cooldown
        else -> return
    }
    Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
}
