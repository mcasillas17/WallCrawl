package wallcrawl.elopenmike.com.core.database.repository

import wallcrawl.elopenmike.com.core.database.PERSISTED_LIST_SEPARATOR
import wallcrawl.elopenmike.com.core.database.PERSISTED_PAIR_SEPARATOR
import wallcrawl.elopenmike.com.core.database.entity.UserProfileEntity
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.MuscleVocabulary
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.UserProfile

/**
 * The one translation between the persisted profile row and the domain profile.
 *
 * Ordinary profile writes and archive restore share it deliberately: a restored profile has
 * to be encoded exactly the way the app encodes its own, or a value would round-trip into a
 * different meaning than the one the user saved.
 */
internal fun UserProfileEntity.toUserProfile(): UserProfile {
    val priorities = if (musclePrioritiesJson.isBlank()) {
        emptyMap()
    } else {
        musclePrioritiesJson.split(PERSISTED_LIST_SEPARATOR)
            .mapNotNull { entry ->
                val parts = entry.split(PERSISTED_PAIR_SEPARATOR)
                if (parts.size != 2) return@mapNotNull null
                val level = try {
                    PriorityLevel.valueOf(parts[1])
                } catch (error: IllegalArgumentException) {
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

    val equipment = if (availableEquipmentJson.isBlank()) {
        emptyList()
    } else {
        availableEquipmentJson.split(PERSISTED_LIST_SEPARATOR).filter { it.isNotBlank() }
    }
    val excluded = if (excludedExerciseIdsJson.isBlank()) {
        emptyList()
    } else {
        excludedExerciseIdsJson.split(PERSISTED_LIST_SEPARATOR).filter { it.isNotBlank() }
    }
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
        themePreference = themePreference,
        gender = gender,
        illustrationPreference = illustrationPreference
    )
}

internal fun UserProfile.toUserProfileEntity(): UserProfileEntity = UserProfileEntity(
    id = id,
    revision = revision,
    name = name,
    primaryGoal = primaryGoal,
    experienceLevel = experienceLevel,
    preferredDurationMinutes = preferredDurationMinutes,
    daysPerWeek = daysPerWeek,
    availableEquipmentJson = availableEquipment.joinToString(PERSISTED_LIST_SEPARATOR),
    preferredUnit = preferredUnit,
    musclePrioritiesJson = musclePriorities.entries.joinToString(PERSISTED_LIST_SEPARATOR) {
        "${it.key}$PERSISTED_PAIR_SEPARATOR${it.value.name}"
    },
    excludedExerciseIdsJson = excludedExerciseIds.joinToString(PERSISTED_LIST_SEPARATOR),
    onboardingCompleted = onboardingCompleted,
    trainingConstraintsJson = trainingConstraints.joinToString(PERSISTED_LIST_SEPARATOR) { it.name },
    returningAfterBreakWeeks = returningAfterBreakWeeks,
    confirmedStartingLoadsJson = confirmedStartingLoads.entries
        .joinToString(PERSISTED_LIST_SEPARATOR) { "${it.key}$PERSISTED_PAIR_SEPARATOR${it.value}" },
    fitnessGoalsJson = goals.joinToString(PERSISTED_LIST_SEPARATOR) { it.name },
    movementCapabilitiesJson = MovementCapabilitiesCodec.encode(movementCapabilities),
    themePreference = themePreference,
    gender = gender,
    illustrationPreference = illustrationPreference
)

private fun decodeFitnessGoals(raw: String): Set<FitnessGoal> {
    if (raw.isBlank()) return emptySet()
    return raw.split(PERSISTED_LIST_SEPARATOR)
        .filter { it.isNotBlank() }
        .mapNotNull { name ->
            try {
                FitnessGoal.valueOf(name)
            } catch (error: IllegalArgumentException) {
                null
            }
        }
        .toSet()
}

private fun decodeTrainingConstraints(raw: String): Set<TrainingConstraint> {
    if (raw.isBlank()) return emptySet()
    return raw.split(PERSISTED_LIST_SEPARATOR)
        .filter { it.isNotBlank() }
        .mapNotNull { name ->
            try {
                TrainingConstraint.valueOf(name)
            } catch (error: IllegalArgumentException) {
                null
            }
        }
        .toSet()
}

private fun decodeConfirmedStartingLoads(raw: String): Map<String, Double> {
    if (raw.isBlank()) return emptyMap()
    return raw.split(PERSISTED_LIST_SEPARATOR)
        .mapNotNull { entry ->
            val parts = entry.split(PERSISTED_PAIR_SEPARATOR, limit = 2)
            if (parts.size != 2 || parts[0].isBlank()) return@mapNotNull null
            val weight = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            if (!weight.isFinite() || weight < 0.0) return@mapNotNull null
            parts[0] to weight
        }
        .toMap()
}
