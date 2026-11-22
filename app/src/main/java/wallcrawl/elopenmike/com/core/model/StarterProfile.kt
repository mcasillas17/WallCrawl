package wallcrawl.elopenmike.com.core.model

/**
 * The safe profile written by the one-screen basic setup: bodyweight, low impact, beginner.
 * Personalization replaces these answers later.
 */
object StarterProfile {
    private val poundsRegions = setOf("US", "LR", "MM")

    fun apply(
        base: UserProfile,
        goals: Set<FitnessGoal>,
        daysPerWeek: Int,
        durationMinutes: Int,
        regionCountry: String
    ): UserProfile = base.copy(
        name = UserProfile().name,
        gender = ProfileGender.UNSPECIFIED,
        goals = goals,
        daysPerWeek = daysPerWeek,
        preferredDurationMinutes = durationMinutes,
        experienceLevel = ExperienceLevel.BEGINNER,
        preferredUnit = unitFor(regionCountry),
        availableEquipment = listOf(StandardEquipment.BODYWEIGHT),
        trainingConstraints = setOf(TrainingConstraint.LOW_IMPACT_ONLY),
        movementCapabilities = MovementCapabilities.unknown(),
        activeConditions = emptyList(),
        reportedConditions = emptySet(),
        onboardingCompleted = true,
        personalizationCompleted = false
    )

    fun unitFor(regionCountry: String): WeightUnit =
        if (regionCountry.uppercase() in poundsRegions) WeightUnit.LBS else WeightUnit.KG
}
