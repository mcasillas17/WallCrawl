package wallcrawl.elopenmike.com.core.conditions

import wallcrawl.elopenmike.com.core.model.EligibilityReason
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.UserProfile

/**
 * What a profile's reported conditions and joint labels mean for exercise choice.
 *
 * One predicate for eligibility, its supported-regression exception and the prescription
 * policy, so they cannot drift into different readings. Avoid demands exclude; modify demands
 * only rank an exercise lower. Language never reaches a decision.
 */
sealed interface ConditionRestrictions {
    /** No conditions and no joint labels: planning behaves exactly as it did before. */
    data object None : ConditionRestrictions

    /**
     * [avoid] and [modify] are unions across [conditionIds] and joint labels; a demand in
     * both is only in [avoid]. [conditionIds] selects the per-exercise cues to show.
     */
    data class Active(
        val avoid: Set<MovementDemand>,
        val modify: Set<MovementDemand>,
        val guidance: ConditionGuidance,
        val conditionIds: Set<String> = emptySet()
    ) : ConditionRestrictions

    /** The profile has a restriction the overlay cannot describe. Everything is refused. */
    data object Unavailable : ConditionRestrictions
}

internal fun UserProfile.hasJointLabels(): Boolean =
    trainingConstraints.any { it != TrainingConstraint.LOW_IMPACT_ONLY }

internal fun UserProfile.hasConditionInputs(): Boolean =
    reportedConditions.isNotEmpty() || hasJointLabels()

fun restrictionsFor(
    profile: UserProfile,
    result: ConditionGuidanceResult
): ConditionRestrictions {
    if (!profile.hasConditionInputs()) return ConditionRestrictions.None
    val guidance = (result as? ConditionGuidanceResult.Loaded)?.guidance
        ?: return ConditionRestrictions.Unavailable
    // An id the overlay does not know is a restriction we cannot apply, never one to ignore.
    val records = profile.reportedConditions.map { guidance.conditions[it] }
    if (records.any { it == null }) return ConditionRestrictions.Unavailable

    // Same rule for a joint label the overlay does not describe: never ignored.
    val jointLabels = profile.trainingConstraints.filter { it != TrainingConstraint.LOW_IMPACT_ONLY }
    if (jointLabels.any { it !in guidance.jointLabelDemands }) return ConditionRestrictions.Unavailable

    val avoid = records.flatMapTo(mutableSetOf()) { it!!.avoidDemands }
    val modify = records.flatMapTo(mutableSetOf()) { it!!.modifyDemands }
    jointLabels.forEach { modify += guidance.jointLabelDemands.getValue(it) }
    return ConditionRestrictions.Active(
        avoid = avoid,
        modify = modify - avoid,
        guidance = guidance,
        conditionIds = profile.reportedConditions
    )
}

/** The reason to refuse [exerciseId], or null when it may be planned (including a modify one). */
fun ConditionRestrictions.decisionFor(exerciseId: String): EligibilityReason? = when (this) {
    ConditionRestrictions.None -> null
    ConditionRestrictions.Unavailable -> EligibilityReason.CONDITION_GUIDANCE_UNAVAILABLE
    is ConditionRestrictions.Active -> {
        val demands = guidance.exerciseDemands[exerciseId]
        when {
            demands == null -> EligibilityReason.CONDITION_DEMANDS_UNREVIEWED
            demands.any { it in avoid } -> EligibilityReason.CONDITION_RESTRICTED_DEMAND
            else -> null
        }
    }
}

/** True or false, never a count: whether [exerciseId] carries any modify demand. */
fun ConditionRestrictions.ranksLower(exerciseId: String): Boolean =
    this is ConditionRestrictions.Active &&
        guidance.exerciseDemands[exerciseId].orEmpty().any { it in modify }

/**
 * Display text only. Cues of the exercise's modify demands, then any exercise-specific cues
 * from the selected conditions, in [language] (English when that text is missing).
 */
fun ConditionRestrictions.cuesFor(exerciseId: String, language: String): List<String> {
    if (this !is ConditionRestrictions.Active) return emptyList()
    fun Map<String, String>?.pick(): String? = this?.get(language) ?: this?.get(FALLBACK_LANGUAGE)

    val demandCues = guidance.exerciseDemands[exerciseId].orEmpty()
        .filter { it in modify }
        .sortedBy(MovementDemand::ordinal)
        .mapNotNull { guidance.demandCues[it].pick() }
    val conditionCues = conditionIds.sorted()
        .mapNotNull { guidance.conditions[it]?.exerciseCues?.get(exerciseId).pick() }
    return (demandCues + conditionCues).distinct()
}

private const val FALLBACK_LANGUAGE = "en"
