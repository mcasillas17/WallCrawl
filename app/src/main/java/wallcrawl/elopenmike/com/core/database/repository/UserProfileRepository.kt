package wallcrawl.elopenmike.com.core.database.repository

import wallcrawl.elopenmike.com.core.database.dao.UserProfileDao
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

/**
 * @param localDataWriteGate serialises every profile write against destructive local-data
 *   operations. Deleting all local data and writing the profile are both reachable from the
 *   Training Profile screen, and a profile write that landed after a deletion would restore
 *   an onboarded profile the user had just erased. Holding the gate across the read and the
 *   write also makes each `update*` an atomic read-modify-write. The default is a private
 *   gate for tests and previews; production passes the one the container shares.
 */
class OfflineUserProfileRepository(
    private val userProfileDao: UserProfileDao,
    private val localDataWriteGate: Mutex = Mutex()
) : UserProfileRepository {

    override fun getUserProfile(): Flow<UserProfile> {
        return userProfileDao.observeProfile(UserProfile.DEFAULT_PROFILE_ID).map { entity ->
            entity?.toUserProfile() ?: UserProfile()
        }
    }

    override suspend fun getProfileOnce(): UserProfile =
        localDataWriteGate.withLock { readOrBootstrapProfile() }

    override suspend fun saveUserProfile(profile: UserProfile) {
        localDataWriteGate.withLock { writeProfile(profile) }
    }

    override suspend fun saveProfile(profile: UserProfile) {
        // Validation runs outside the gate: it reads nothing from the database, and holding
        // the gate for it would block a deletion for no reason.
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
        mutateProfile { current -> current.copy(goals = goals) }
    }

    override suspend fun updatePrimaryGoal(goal: FitnessGoal) {
        updateGoals(setOf(goal))
    }

    override suspend fun updateExperienceLevel(level: ExperienceLevel) {
        mutateProfile { current -> current.copy(experienceLevel = level) }
    }

    override suspend fun updatePreferredDuration(minutes: Int) {
        mutateProfile { current -> current.copy(preferredDurationMinutes = minutes) }
    }

    override suspend fun updateDaysPerWeek(days: Int) {
        mutateProfile { current -> current.copy(daysPerWeek = days) }
    }

    override suspend fun updateEquipment(equipment: List<String>) {
        mutateProfile { current -> current.copy(availableEquipment = equipment) }
    }

    override suspend fun updateUnit(unit: WeightUnit) {
        mutateProfile { current ->
            if (current.preferredUnit == unit) return@mutateProfile null
            current.copy(
                preferredUnit = unit,
                confirmedStartingLoads = current.confirmedStartingLoads.mapValues { (_, load) ->
                    convertWeight(load, from = current.preferredUnit, to = unit)
                }
            )
        }
    }

    override suspend fun updateMusclePriorities(priorities: Map<String, PriorityLevel>) {
        mutateProfile { current -> current.copy(musclePriorities = priorities) }
    }

    override suspend fun updateExcludedExercises(excludedIds: List<String>) {
        mutateProfile { current -> current.copy(excludedExerciseIds = excludedIds) }
    }

    override suspend fun updateTrainingConstraints(constraints: Set<TrainingConstraint>) {
        mutateProfile { current -> current.copy(trainingConstraints = constraints) }
    }

    override suspend fun updateReturningAfterBreakWeeks(weeks: Int) {
        mutateProfile { current -> current.copy(returningAfterBreakWeeks = weeks) }
    }

    override suspend fun updateThemePreference(theme: ThemePreference) {
        mutateProfile { current -> current.copy(themePreference = theme) }
    }

    /**
     * Reads the profile, applies [transform], and writes the result as one gated step.
     *
     * A transform returning null means the value is already what was asked for, and nothing
     * is written — which also means nothing is written after a deletion in that case.
     */
    private suspend fun mutateProfile(transform: (UserProfile) -> UserProfile?) {
        localDataWriteGate.withLock {
            transform(readOrBootstrapProfile())?.let { updated -> writeProfile(updated) }
        }
    }

    /** Caller must hold [localDataWriteGate]: this inserts the bootstrap row when absent. */
    private suspend fun readOrBootstrapProfile(): UserProfile =
        userProfileDao.getProfile(UserProfile.DEFAULT_PROFILE_ID)?.toUserProfile()
            ?: UserProfile().also { bootstrap -> writeProfile(bootstrap) }

    /** Caller must hold [localDataWriteGate]. */
    private suspend fun writeProfile(profile: UserProfile) {
        userProfileDao.insertOrUpdateWithNextRevision(profile.toUserProfileEntity())
    }

    private companion object {
        val KNOWN_EQUIPMENT = StandardEquipment.ALL.toSet()
    }
}
