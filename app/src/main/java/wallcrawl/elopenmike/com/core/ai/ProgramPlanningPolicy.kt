package wallcrawl.elopenmike.com.core.ai

import wallcrawl.elopenmike.com.core.model.*

/** Checks scheduling provenance without changing eligibility, dose or progression. */
internal fun WorkoutGenerationContext.validatedProgramSession(): ProgramSessionSnapshot? {
    if (programHeader.profileId != userProfile.id) throw ProgramException(ProgramFailure.INVALID_IDENTITY)
    if (planningIntent == WorkoutPlanningIntent.ORDINARY) {
        if (programInput != null) throw ProgramException(ProgramFailure.INVALID_ACTION)
        return null
    }
    val input = programInput ?: throw ProgramException(ProgramFailure.MISSING_OR_NOT_ACTIVE)
    val enrollment = input.enrollment
    MultiWeekProgramPolicy.validateSupported(enrollment)
    if (enrollment.profileId != userProfile.id || enrollment.programId != programHeader.programId ||
        enrollment.revision != programHeader.revision) throw ProgramException(ProgramFailure.STALE_STATE)
    MultiWeekProgramPolicy.validateProfileMatch(enrollment.configuration, userProfile)
    if (fitnessGoal != enrollment.configuration.primaryGoal || fitnessGoals != enrollment.configuration.goals.toSet() ||
        trainingFrequencyDaysPerWeek != enrollment.configuration.sessionsPerWeek) {
        throw ProgramException(ProgramFailure.PROFILE_MISMATCH)
    }
    val progress = MultiWeekProgramPolicy.reconstruct(enrollment, input.sessions)
    if (progress.activeSessionId != null) throw ProgramException(ProgramFailure.ACTIVE_WORKOUT)
    if (enrollment.status != ProgramStatus.ACTIVE || progress.nextPosition == null) {
        throw ProgramException(ProgramFailure.MISSING_OR_NOT_ACTIVE)
    }
    if (input.position != progress.nextPosition || input.objective != MultiWeekProgramPolicy.objective(input.position)) {
        throw ProgramException(ProgramFailure.INVALID_PROGRESS)
    }
    return MultiWeekProgramPolicy.snapshot(enrollment, input.position)
}
