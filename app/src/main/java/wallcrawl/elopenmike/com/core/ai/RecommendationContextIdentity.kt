package wallcrawl.elopenmike.com.core.ai

import java.security.MessageDigest
import java.util.Locale
import wallcrawl.elopenmike.com.core.model.SessionProgramConstraints
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext

/**
 * A deterministic digest of the generation inputs that decide whether a displayed
 * recommendation is still the right one.
 *
 * A recommendation is produced from one context and started against another, and the two
 * can differ: the profile may have been edited, a workout may have been completed, or the
 * clock may have crossed into a new ISO week or a new zone. Comparing this digest is how
 * that is noticed, so a stale plan is refused with an explanation rather than started
 * quietly.
 *
 * ## What it covers
 *
 * Profile identity and revision, lifetime completed workouts, the ordered candidate id
 * list, catalog and review-policy identity, whether the reviewed path was enabled, the
 * derived adaptation state, the declared session constraints, and the accounting week and
 * zone when a program state exists. Candidate **order** is included because it is an input
 * to selection.
 *
 * ## What it deliberately excludes
 *
 * Everything a decision cannot read: display language, translated exercise names, notes,
 * loads, repetitions, effort values, and every body or profile measurement. The same
 * canonical inputs read in either language produce the same digest, which is what makes the
 * localization boundary testable here rather than only asserted.
 *
 * ## What it is not
 *
 * A freshness check over local, derived state. It is not a security control, and it does
 * not reconstruct the inputs it summarises.
 */
object RecommendationContextIdentity {

    private const val FORMAT_VERSION = "wallcrawl-recommendation-context-v1"

    /** ASCII unit separator: it cannot occur in a catalog id, muscle name, or zone id. */
    private const val FIELD_SEPARATOR = "\u001F"

    fun of(context: WorkoutGenerationContext): String {
        val programState = context.trainingProgramState
        val ledger = programState?.weeklyLedger
        val lines = mutableListOf(
            line("profile", context.userProfile.id),
            line("profileRevision", context.userProfile.revision.toString()),
            line("completedWorkouts", context.completedWorkoutCount.toString()),
            line("catalog", context.catalogVersion ?: "none"),
            line("reviewPolicy", context.reviewPolicyVersion.toString()),
            line("reviewedPath", (context.automaticEligibilityResult != null).toString()),
            line("adaptationState", programState?.adaptationState?.name ?: "none"),
            line("programStatePolicy", programState?.policyVersion?.name ?: "none"),
            line("ledgerPolicy", ledger?.policyVersion?.name ?: "none"),
            line("weekStart", ledger?.weekStartEpochDay?.toString() ?: "none"),
            line("zone", ledger?.timeZoneId ?: "none"),
            line("constraints", context.programConstraints.canonicalForm())
        )

        // Positional, so a reordered candidate list is a different context. The list is not
        // sorted for the same reason: order is an input, not an incidental detail.
        context.allowedExercises.forEachIndexed { index, exercise ->
            lines += line("candidate", index.toString(), exercise.id)
        }

        val canonical = (sequenceOf(FORMAT_VERSION) + lines.asSequence()).joinToString("\n")
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> String.format(Locale.ROOT, "%02x", byte) }
    }

    private fun SessionProgramConstraints.canonicalForm(): String = listOf(
        uniqueExerciseIds.toString(),
        uniqueProgressionFamilies.toString(),
        requiredMovementPatterns.map { it.name }.sorted().joinToString(",")
    ).joinToString(",")

    private fun line(vararg fields: String): String = fields.joinToString(FIELD_SEPARATOR)
}
