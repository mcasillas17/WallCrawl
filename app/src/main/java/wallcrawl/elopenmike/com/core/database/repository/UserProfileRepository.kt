package wallcrawl.elopenmike.com.core.database.repository

import wallcrawl.elopenmike.com.core.database.dao.UserProfileDao
import wallcrawl.elopenmike.com.core.database.entity.UserProfileEntity
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.MuscleVocabulary
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.convertWeight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface UserProfileRepository {
    fun getUserProfile(): Flow<UserProfile>
    suspend fun getProfileOnce(): UserProfile
    suspend fun saveUserProfile(profile: UserProfile)

    /**
     * Persists a whole profile as a single revision update, validating every
     * planning-relevant input first. Onboarding must call this instead of chaining
     * per-field update* calls, so completing it never writes more than one revision.
     */
    suspend fun saveProfile(profile: UserProfile)
    suspend fun updateGoals(goals: Set<FitnessGoal>)
    suspend fun updatePrimaryGoal(goal: FitnessGoal)
    suspend fun updateExperienceLevel(level: ExperienceLevel)
    suspend fun updatePreferredDuration(minutes: Int)
    suspend fun updateDaysPerWeek(days: Int)
    suspend fun updateEquipment(equipment: List<String>)
    suspend fun updateUnit(unit: WeightUnit)
    suspend fun updateMusclePriorities(priorities: Map<String, PriorityLevel>)
    suspend fun updateExcludedExercises(excludedIds: List<String>)
    suspend fun updateTrainingConstraints(constraints: Set<TrainingConstraint>)
    suspend fun updateReturningAfterBreakWeeks(weeks: Int)
    suspend fun updateThemePreference(theme: ThemePreference)
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

    override suspend fun saveProfile(profile: UserProfile) {
        require(profile.goals.isNotEmpty()) { "goals must not be empty." }
        require(profile.daysPerWeek in 2..6) {
            "daysPerWeek must be between 2 and 6, was ${profile.daysPerWeek}."
        }
        require(profile.preferredDurationMinutes in 20..120) {
            "preferredDurationMinutes must be between 20 and 120, " +
                "was ${profile.preferredDurationMinutes}."
        }
        require(profile.returningAfterBreakWeeks in 0..520) {
            "returningAfterBreakWeeks must be between 0 and 520, " +
                "was ${profile.returningAfterBreakWeeks}."
        }
        require(profile.availableEquipment.isNotEmpty()) {
            "availableEquipment must not be empty."
        }
        val unknownEquipment = profile.availableEquipment.filterNot { it in KNOWN_EQUIPMENT }
        require(unknownEquipment.isEmpty()) {
            "availableEquipment contains unknown equipment: $unknownEquipment."
        }
        val invalidLoads = profile.confirmedStartingLoads.filterValues { !it.isFinite() || it < 0.0 }
        require(invalidLoads.isEmpty()) {
            "confirmedStartingLoads must be finite and not negative: $invalidLoads."
        }
        require(
            profile.movementCapabilities.values.keys == MovementCapabilityType.entries.toSet()
        ) {
            "movementCapabilities must contain every supported capability."
        }
        saveUserProfile(profile)
    }

    override suspend fun updateGoals(goals: Set<FitnessGoal>) {
        require(goals.isNotEmpty()) { "goals must not be empty." }
        val current = getProfileOnce()
        saveUserProfile(current.copy(goals = goals))
    }

    override suspend fun updatePrimaryGoal(goal: FitnessGoal) {
        updateGoals(setOf(goal))
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
        if (current.preferredUnit == unit) return
        val convertedLoads = current.confirmedStartingLoads.mapValues { (_, load) ->
            convertWeight(load, from = current.preferredUnit, to = unit)
        }
        saveUserProfile(current.copy(preferredUnit = unit, confirmedStartingLoads = convertedLoads))
    }

    override suspend fun updateMusclePriorities(priorities: Map<String, PriorityLevel>) {
        val current = getProfileOnce()
        saveUserProfile(current.copy(musclePriorities = priorities))
    }

    override suspend fun updateExcludedExercises(excludedIds: List<String>) {
        val current = getProfileOnce()
        saveUserProfile(current.copy(excludedExerciseIds = excludedIds))
    }

    override suspend fun updateTrainingConstraints(constraints: Set<TrainingConstraint>) {
        val current = getProfileOnce()
        saveUserProfile(current.copy(trainingConstraints = constraints))
    }

    override suspend fun updateReturningAfterBreakWeeks(weeks: Int) {
        val current = getProfileOnce()
        saveUserProfile(current.copy(returningAfterBreakWeeks = weeks))
    }

    override suspend fun updateThemePreference(theme: ThemePreference) {
        val current = getProfileOnce()
        saveUserProfile(current.copy(themePreference = theme))
    }

    private fun UserProfileEntity.toDomainModel(): UserProfile {
        val priorities = if (musclePrioritiesJson.isBlank()) {
            emptyMap()
        } else {
            musclePrioritiesJson.split("|||")
                .mapNotNull { entry ->
                    val parts = entry.split(":")
                    if (parts.size != 2) return@mapNotNull null
                    val level = try {
                        PriorityLevel.valueOf(parts[1])
                    } catch (e: IllegalArgumentException) {
                        PriorityLevel.NORMAL
                    }
                    parts[0] to level
                }
                .flatMap { (muscle, level) ->
                    MuscleVocabulary.canonicalize(muscle).map { canonical -> canonical to level }
                }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, levels) -> levels.maxBy(PriorityLevel::multiplier) }
        }

        val equipment = if (availableEquipmentJson.isBlank()) emptyList() else availableEquipmentJson.split("|||").filter { it.isNotBlank() }
        val excluded = if (excludedExerciseIdsJson.isBlank()) emptyList() else excludedExerciseIdsJson.split("|||").filter { it.isNotBlank() }
        val decodedGoals = decodeFitnessGoals(fitnessGoalsJson).ifEmpty { setOf(primaryGoal) }

        return UserProfile(
            id = id,
            revision = revision,
            name = name,
            goals = decodedGoals,
            experienceLevel = experienceLevel,
            preferredDurationMinutes = preferredDurationMinutes,
            daysPerWeek = daysPerWeek,
            availableEquipment = equipment,
            preferredUnit = preferredUnit,
            musclePriorities = priorities,
            excludedExerciseIds = excluded,
            onboardingCompleted = onboardingCompleted,
            trainingConstraints = decodeTrainingConstraints(trainingConstraintsJson),
            returningAfterBreakWeeks = returningAfterBreakWeeks,
            confirmedStartingLoads = decodeConfirmedStartingLoads(confirmedStartingLoadsJson),
            movementCapabilities = MovementCapabilitiesCodec.decode(movementCapabilitiesJson),
            themePreference = themePreference
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
            excludedExerciseIdsJson = excludedStr,
            onboardingCompleted = onboardingCompleted,
            trainingConstraintsJson = encodeTrainingConstraints(trainingConstraints),
            returningAfterBreakWeeks = returningAfterBreakWeeks,
            confirmedStartingLoadsJson = encodeConfirmedStartingLoads(confirmedStartingLoads),
            fitnessGoalsJson = encodeFitnessGoals(goals),
            movementCapabilitiesJson = MovementCapabilitiesCodec.encode(movementCapabilities),
            themePreference = themePreference
        )
    }

    private fun encodeFitnessGoals(goals: Set<FitnessGoal>): String =
        goals.joinToString("|||") { it.name }

    private fun decodeFitnessGoals(raw: String): Set<FitnessGoal> {
        if (raw.isBlank()) return emptySet()
        return raw.split("|||")
            .filter { it.isNotBlank() }
            .mapNotNull { name ->
                try {
                    FitnessGoal.valueOf(name)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
            .toSet()
    }

    private fun encodeTrainingConstraints(constraints: Set<TrainingConstraint>): String =
        constraints.joinToString("|||") { it.name }

    private fun decodeTrainingConstraints(raw: String): Set<TrainingConstraint> {
        if (raw.isBlank()) return emptySet()
        return raw.split("|||")
            .filter { it.isNotBlank() }
            .mapNotNull { name ->
                try {
                    TrainingConstraint.valueOf(name)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
            .toSet()
    }

    private fun encodeConfirmedStartingLoads(loads: Map<String, Double>): String =
        loads.entries.joinToString("|||") { "${it.key}:${it.value}" }

    private fun decodeConfirmedStartingLoads(raw: String): Map<String, Double> {
        if (raw.isBlank()) return emptyMap()
        return raw.split("|||")
            .mapNotNull { entry ->
                val parts = entry.split(":", limit = 2)
                if (parts.size != 2 || parts[0].isBlank()) return@mapNotNull null
                val weight = parts[1].toDoubleOrNull() ?: return@mapNotNull null
                if (!weight.isFinite() || weight < 0.0) return@mapNotNull null
                parts[0] to weight
            }
            .toMap()
    }

    private companion object {
        val KNOWN_EQUIPMENT = StandardEquipment.ALL.toSet()
    }
}
