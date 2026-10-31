package wallcrawl.elopenmike.com.feature.program

import androidx.annotation.StringRes
import wallcrawl.elopenmike.com.core.model.*

data class ProgramConfirmation(val action: ProgramAction, val expected: ProgramExpectation)

data class ProgramUiState(
    val loading: Boolean = true,
    val program: ProgramReadState? = null,
    val activeSession: WorkoutSession? = null,
    val saving: Boolean = false,
    @StringRes val error: Int? = null,
    val confirmation: ProgramConfirmation? = null
)
