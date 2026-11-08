package wallcrawl.elopenmike.com.core.database.repository

import wallcrawl.elopenmike.com.core.database.PERSISTED_LIST_SEPARATOR
import wallcrawl.elopenmike.com.core.database.dao.UserProfileDao
import wallcrawl.elopenmike.com.core.database.dao.ConditionReassessmentDao
import wallcrawl.elopenmike.com.core.database.entity.toModel
import wallcrawl.elopenmike.com.core.model.ConditionSupportCatalog
import wallcrawl.elopenmike.com.core.model.ConditionViolation
import wallcrawl.elopenmike.com.core.model.ConditionReassessmentRefused
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.model.ProfileGender
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.SelectedCondition
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
    /** Adds or removes one ID without replacing exclusions edited by another screen. */
    suspend fun setExerciseExcluded(exerciseId: String, excluded: Boolean) {
        throw UnsupportedOperationException("Individual exercise exclusions are unavailable.")
    }
    suspend fun updateTrainingConstraints(constraints: Set<TrainingConstraint>)
    suspend fun removeReportedCondition(conditionId: String) {
        error("Reported condition removal is unavailable.")
    }
    suspend fun updateActiveConditions(conditions: List<SelectedCondition>) {
        throw UnsupportedOperationException("Condition selection is unavailable in this repository.")
    }
    suspend fun reassessCondition(conditionId: String, exerciseId: String, expectedEventId: String, answers: Map<String, String?>) {
        throw ConditionReassessmentRefused(ConditionViolation("reassessment-unavailable", setOf(conditionId), setOf(exerciseId)), expectedEventId)
    }
    suspend fun updateReturningAfterBreakWeeks(weeks: Int)
    suspend fun updateThemePreference(theme: ThemePreference)
    suspend fun updateGender(gender: ProfileGender)
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
    private val localDataWriteGate: Mutex = Mutex(),
    private val conditionReassessmentDao: ConditionReassessmentDao? = null,
    private val conditionSupport: (suspend () -> ConditionSupportCatalog)? = null,
    private val exerciseCatalog: ExerciseCatalog? = null
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

    override suspend fun setExerciseExcluded(exerciseId: String, excluded: Boolean) {
        mutateProfile { current ->
            val ids = current.excludedExerciseIds
            if ((exerciseId in ids) == excluded) return@mutateProfile null
            val updated = if (excluded) ids + exerciseId else ids.filterNot { it == exerciseId }
            // Imported session IDs may contain the separator used by the profile column.
            require(updated.none { it.isBlank() } &&
                (updated.isEmpty() || updated.joinToString(PERSISTED_LIST_SEPARATOR).split(PERSISTED_LIST_SEPARATOR) == updated))
            current.copy(excludedExerciseIds = updated)
        }
    }

    override suspend fun updateTrainingConstraints(constraints: Set<TrainingConstraint>) {
        mutateProfile { current ->
            check(current.unresolvedTrainingConstraintsRaw == null) { "Training constraints require review before editing." }
            current.copy(trainingConstraints = constraints)
        }
    }

    override suspend fun removeReportedCondition(conditionId: String) {
        mutateProfile { current ->
            if (conditionId in current.reportedConditions) current.copy(reportedConditions = current.reportedConditions - conditionId) else null
        }
    }

    override suspend fun updateActiveConditions(conditions: List<SelectedCondition>) {
        mutateProfile { current -> current.copy(activeConditions = conditions) }
    }

    override suspend fun reassessCondition(conditionId: String, exerciseId: String, expectedEventId: String, answers: Map<String, String?>) {
        fun refused(reason: String): Nothing = throw ConditionReassessmentRefused(
            ConditionViolation(reason, setOf(conditionId), setOf(exerciseId)), expectedEventId)
        val submittedAnswers = answers.toMap()
        val dao = conditionReassessmentDao ?: refused("reassessment-unavailable")
        val captured = localDataWriteGate.withLock {
            dao.readReassessment(UserProfile.DEFAULT_PROFILE_ID, conditionId, exerciseId)
        }
        val profile = captured.profile ?: refused("missing-profile")
        val event = captured.event ?: refused("stale-event")
        if (event.eventId != expectedEventId) refused("stale-event")
        val load = conditionSupport ?: refused("missing-support")
        val catalog = exerciseCatalog ?: refused("missing-exercise")
        suspend fun loadSupport(): ConditionSupportCatalog = try { load()
        } catch (error: CancellationException) { throw error
        } catch (error: Exception) { refused("support-unavailable") }
        val support = loadSupport()
        val record = support.records[conditionId] ?: refused("unsupported-condition")
        fun validatedSelections(currentRecord: wallcrawl.elopenmike.com.core.model.ConditionSupportRecord,
            exercises: Map<String, wallcrawl.elopenmike.com.core.model.Exercise>): List<SelectedCondition> {
            try { event.toModel() } catch (error: IllegalArgumentException) { refused("unsupported-policy-or-context") }
            if (event.responsePolicyVersion != 1 || currentRecord.policyVersion != 1 || currentRecord.responsePolicy.version != 1)
                refused("unsupported-policy-or-context")
            val current = try { profile.toUserProfile() } catch (error: IllegalArgumentException) { refused("unsupported-policy-or-context") }
            val previous = current.activeConditions.singleOrNull { it.conditionId == conditionId }
            if (previous != null && previous.contextSchemaVersion != currentRecord.contextSchemaVersion) refused("unsupported-policy-or-context")
            val definitions = (currentRecord.inputs + currentRecord.responsePolicy.inputs).groupBy { it.id }
            if (definitions.values.any { it.distinct().size != 1 }) refused("conflicting-input-definitions")
            val merged = previous?.answers.orEmpty() + submittedAnswers
            if (merged.any { (key, value) -> definitions[key] == null || value != null && value !in definitions.getValue(key).first().choices }) refused("unknown-answer")
            if (currentRecord.responsePolicy.inputs.any { it.required && submittedAnswers[it.id] == null } ||
                currentRecord.inputs.any { it.required && merged[it.id] == null }) refused("missing-context")
            if ((currentRecord.responsePolicy.stopWhenAnswers.entries + currentRecord.responsePolicy.reassessmentRequiredAnswers.entries)
                    .any { (key, value) -> merged[key] == value }) refused("response-requires-reassessment")
            val case = currentRecord.cases.singleOrNull { it.requiredAnswers.all { (key, value) -> merged[key] == value } }
                ?: refused("unresolved-case")
            if (currentRecord.cases.none { it.rules.any { rule -> rule.exerciseIdentity.exerciseId == exerciseId } }) refused("unassessed-exercise")
            for (rule in case.rules) {
                val exercise = exercises[rule.exerciseIdentity.exerciseId] ?: refused("missing-exercise")
                if (exercise.type != rule.exerciseIdentity.exerciseType ||
                    wallcrawl.elopenmike.com.core.model.ConditionExerciseIdentity.contentSha256(exercise) != rule.exerciseIdentity.contentSha256)
                    refused("changed-exercise")
            }
            if (previous == null && current.activeConditions.size >= 32) refused("planning-bound-exceeded")
            val selection = SelectedCondition(conditionId, currentRecord.contextSchemaVersion, merged)
            return if (previous == null) current.activeConditions + selection else current.activeConditions.map {
                if (it.conditionId == conditionId) selection else it
            }
        }
        val selections = validatedSelections(record, catalog.getAllExercises().first().associateBy { it.id })
        val encoded = ConditionContextCodec.encodeSelections(selections)
        localDataWriteGate.withLock {
            dao.applyReassessment(profile.id, profile.revision, conditionId, exerciseId, expectedEventId, encoded) {
                if (dao.get(profile.id, conditionId, exerciseId) != event) refused("stale-event")
                val current = loadSupport().records[conditionId] ?: refused("unsupported-condition")
                if (current != record) refused("changed-condition-facts")
                validatedSelections(current, catalog.getAllExercises().first().associateBy { it.id })
            }
        }
    }

    override suspend fun updateReturningAfterBreakWeeks(weeks: Int) {
        mutateProfile { current -> current.copy(returningAfterBreakWeeks = weeks) }
    }

    override suspend fun updateThemePreference(theme: ThemePreference) {
        mutateProfile { current -> current.copy(themePreference = theme) }
    }

    override suspend fun updateGender(gender: ProfileGender) {
        mutateProfile { current -> if (current.gender == gender) null else current.copy(gender = gender) }
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
