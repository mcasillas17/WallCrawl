package wallcrawl.elopenmike.com.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val userProfileRepository: UserProfileRepository
) : ViewModel() {

    private val isSavingFlow = MutableStateFlow(false)
    private val errorFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProfileUiState> = combine(
        userProfileRepository.getUserProfile(),
        isSavingFlow,
        errorFlow
    ) { profile, isSaving, error ->
        if (error != null) {
            ProfileUiState.Error(error)
        } else {
            ProfileUiState.Success(
                profile = profile,
                isSaving = isSaving,
                availableEquipmentOptions = StandardEquipment.ALL,
                availableMuscleOptions = listOf(
                    StandardMuscles.CHEST,
                    StandardMuscles.SHOULDERS,
                    StandardMuscles.BACK,
                    StandardMuscles.TRICEPS,
                    StandardMuscles.BICEPS,
                    StandardMuscles.QUADS,
                    StandardMuscles.HAMSTRINGS,
                    StandardMuscles.GLUTES,
                    StandardMuscles.CORE
                )
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProfileUiState.Loading
    )

    fun updateGoal(goal: FitnessGoal) {
        viewModelScope.launch {
            userProfileRepository.updatePrimaryGoal(goal)
        }
    }

    fun updateExperience(experience: ExperienceLevel) {
        viewModelScope.launch {
            userProfileRepository.updateExperienceLevel(experience)
        }
    }

    fun updateDuration(durationMinutes: Int) {
        viewModelScope.launch {
            userProfileRepository.updatePreferredDuration(durationMinutes)
        }
    }

    fun updateDaysPerWeek(days: Int) {
        viewModelScope.launch {
            userProfileRepository.updateDaysPerWeek(days)
        }
    }

    fun updateUnit(unit: WeightUnit) {
        viewModelScope.launch {
            userProfileRepository.updateUnit(unit)
        }
    }

    fun toggleEquipment(equipment: String) {
        viewModelScope.launch {
            val current = userProfileRepository.getProfileOnce()
            val updated = if (equipment in current.availableEquipment) {
                current.availableEquipment - equipment
            } else {
                current.availableEquipment + equipment
            }
            userProfileRepository.updateEquipment(updated)
        }
    }

    fun setMusclePriority(muscle: String, priority: PriorityLevel) {
        viewModelScope.launch {
            val current = userProfileRepository.getProfileOnce()
            val updated = current.musclePriorities.toMutableMap().apply {
                put(muscle, priority)
            }
            userProfileRepository.updateMusclePriorities(updated)
        }
    }

    companion object {
        fun provideFactory(
            userProfileRepository: UserProfileRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ProfileViewModel(userProfileRepository) as T
            }
        }
    }
}
