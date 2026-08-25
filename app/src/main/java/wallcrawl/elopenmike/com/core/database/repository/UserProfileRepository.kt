package wallcrawl.elopenmike.com.core.database.repository

import wallcrawl.elopenmike.com.core.database.dao.UserProfileDao
import wallcrawl.elopenmike.com.core.database.entity.UserProfileEntity
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface UserProfileRepository {
    fun getUserProfile(): Flow<UserProfile>
    suspend fun getProfileOnce(): UserProfile
    suspend fun saveUserProfile(profile: UserProfile)
    suspend fun updatePrimaryGoal(goal: FitnessGoal)
    suspend fun updateExperienceLevel(level: ExperienceLevel)
    suspend fun updatePreferredDuration(minutes: Int)
    suspend fun updateDaysPerWeek(days: Int)
    suspend fun updateEquipment(equipment: List<String>)
    suspend fun updateUnit(unit: WeightUnit)
    suspend fun updateMusclePriorities(priorities: Map<String, PriorityLevel>)
    suspend fun updateExcludedExercises(excludedIds: List<String>)
}

class OfflineUserProfileRepository(
    private val userProfileDao: UserProfileDao
) : UserProfileRepository {

    override fun getUserProfile(): Flow<UserProfile> {
        return userProfileDao.observeProfile(UserProfile.DEFAULT_PROFILE_ID).map { entity ->
            entity?.toDomainModel() ?: UserProfile()
        }
    }

    override suspend fun getProfileOnce(): UserProfile {
        return userProfileDao.getProfile(UserProfile.DEFAULT_PROFILE_ID)?.toDomainModel()
            ?: UserProfile().also { saveUserProfile(it) }
    }

    override suspend fun saveUserProfile(profile: UserProfile) {
        userProfileDao.insertOrUpdateWithNextRevision(profile.toEntity())
    }

    override suspend fun updatePrimaryGoal(goal: FitnessGoal) {
        val current = getProfileOnce()
        saveUserProfile(current.copy(primaryGoal = goal))
    }

    override suspend fun updateExperienceLevel(level: ExperienceLevel) {
        val current = getProfileOnce()
        saveUserProfile(current.copy(experienceLevel = level))
    }

    override suspend fun updatePreferredDuration(minutes: Int) {
        val current = getProfileOnce()
        saveUserProfile(current.copy(preferredDurationMinutes = minutes))
    }

    override suspend fun updateDaysPerWeek(days: Int) {
        val current = getProfileOnce()
        saveUserProfile(current.copy(daysPerWeek = days))
    }

    override suspend fun updateEquipment(equipment: List<String>) {
        val current = getProfileOnce()
        saveUserProfile(current.copy(availableEquipment = equipment))
    }

    override suspend fun updateUnit(unit: WeightUnit) {
        val current = getProfileOnce()
        saveUserProfile(current.copy(preferredUnit = unit))
    }

    override suspend fun updateMusclePriorities(priorities: Map<String, PriorityLevel>) {
        val current = getProfileOnce()
        saveUserProfile(current.copy(musclePriorities = priorities))
    }

    override suspend fun updateExcludedExercises(excludedIds: List<String>) {
        val current = getProfileOnce()
        saveUserProfile(current.copy(excludedExerciseIds = excludedIds))
    }

    private fun UserProfileEntity.toDomainModel(): UserProfile {
        val priorities = if (musclePrioritiesJson.isBlank()) {
            emptyMap()
        } else {
            musclePrioritiesJson.split("|||")
                .mapNotNull { item ->
                    val parts = item.split(":")
                    if (parts.size == 2) {
                        val level = try { PriorityLevel.valueOf(parts[1]) } catch (e: Exception) { PriorityLevel.NORMAL }
                        parts[0] to level
                    } else null
                }.toMap()
        }

        val equipment = if (availableEquipmentJson.isBlank()) emptyList() else availableEquipmentJson.split("|||").filter { it.isNotBlank() }
        val excluded = if (excludedExerciseIdsJson.isBlank()) emptyList() else excludedExerciseIdsJson.split("|||").filter { it.isNotBlank() }

        return UserProfile(
            id = id,
            revision = revision,
            name = name,
            primaryGoal = primaryGoal,
            experienceLevel = experienceLevel,
            preferredDurationMinutes = preferredDurationMinutes,
            daysPerWeek = daysPerWeek,
            availableEquipment = equipment,
            preferredUnit = preferredUnit,
            musclePriorities = priorities,
            excludedExerciseIds = excluded
        )
    }

    private fun UserProfile.toEntity(): UserProfileEntity {
        val prioritiesStr = musclePriorities.entries.joinToString("|||") { "${it.key}:${it.value.name}" }
        val equipmentStr = availableEquipment.joinToString("|||")
        val excludedStr = excludedExerciseIds.joinToString("|||")

        return UserProfileEntity(
            id = id,
            revision = revision,
            name = name,
            primaryGoal = primaryGoal,
            experienceLevel = experienceLevel,
            preferredDurationMinutes = preferredDurationMinutes,
            daysPerWeek = daysPerWeek,
            availableEquipmentJson = equipmentStr,
            preferredUnit = preferredUnit,
            musclePrioritiesJson = prioritiesStr,
            excludedExerciseIdsJson = excludedStr
        )
    }
}
