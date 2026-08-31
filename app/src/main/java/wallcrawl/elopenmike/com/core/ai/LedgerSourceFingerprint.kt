package wallcrawl.elopenmike.com.core.ai

import java.security.MessageDigest
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.ReviewState
import wallcrawl.elopenmike.com.core.model.TrainingWeek
import wallcrawl.elopenmike.com.core.model.WeeklyDoseLedger
import wallcrawl.elopenmike.com.core.model.WorkoutSession

/**
 * A deterministic digest of every input that can change a [WeeklyDoseLedger].
 *
 * A cached ledger may only be reused while its stored fingerprint still matches the one its
 * current inputs produce. The digest therefore covers exactly the values `PRIMARY_ONLY_V1`
 * reads — history identity, set identity and completion state, the reviewed muscle mapping,
 * and the policy, catalog, review-policy, week, and zone provenance — and deliberately
 * excludes everything the policy cannot read.
 *
 * Nothing private is hashed. RPE, RIR, the manageable confirmation, loads, repetitions,
 * durations, notes, session names, and every profile or body value are absent, so the stored
 * fingerprint can never become a fingerprint of a user's private log. It is a cache-validity
 * check and not a security control: the cache is local, derived, and always replaceable by
 * recomputing from completed history.
 */
object LedgerSourceFingerprint {

    private const val FORMAT_VERSION = "wallcrawl-ledger-fingerprint-v1"

    /** ASCII unit separator: it cannot occur in a catalog id, muscle name, or zone id. */
    private const val FIELD_SEPARATOR = "\u001F"

    fun of(
        sessions: List<WorkoutSession>,
        exercisesById: Map<String, Exercise>,
        policyVersion: LedgerPolicyVersion,
        week: TrainingWeek,
        catalogVersion: String,
        reviewPolicyVersion: Int
    ): String {
        val lines = mutableListOf(
            line("policy", policyVersion.name),
            line("catalog", catalogVersion),
            line("reviewPolicy", reviewPolicyVersion.toString()),
            line("weekStart", week.startEpochDay.toString()),
            line("zone", week.zoneId.id)
        )

        val referencedExerciseIds = sortedSetOf<String>()
        sessions.forEach { session ->
            lines += line(
                "session",
                session.id,
                session.completedAtTimestamp?.toString() ?: "none"
            )
            session.exercises.forEach { exercise ->
                referencedExerciseIds += exercise.exerciseId
                lines += line("instance", session.id, exercise.id, exercise.exerciseId)
                exercise.sets.forEach { set ->
                    lines += line(
                        "set",
                        exercise.id,
                        set.id,
                        set.type.name,
                        set.isCompleted.toString()
                    )
                }
            }
        }

        referencedExerciseIds.forEach { exerciseId ->
            lines += mappingLine(exerciseId, exercisesById)
        }

        // Canonical ordering before hashing, so the same history read back in a different
        // order can never look like different history.
        lines.sort()
        val canonical = (sequenceOf(FORMAT_VERSION) + lines.asSequence()).joinToString("\n")
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    /**
     * The mapping a referenced exercise currently resolves to.
     *
     * Unresolved and unapproved exercises are hashed too, so approving metadata or shipping
     * a catalog that adds a missing exercise invalidates the cached ledger instead of
     * leaving stale omission counts in place.
     */
    private fun mappingLine(
        exerciseId: String,
        exercisesById: Map<String, Exercise>
    ): String {
        val exercise = exercisesById[exerciseId]
            ?: return line("mapping", exerciseId, "UNKNOWN_EXERCISE")
        val reviewed = exercise.reviewedMetadata
            ?: return line("mapping", exerciseId, "NO_REVIEWED_METADATA")
        if (reviewed.reviewState != ReviewState.APPROVED) {
            return line("mapping", exerciseId, reviewed.reviewState.name)
        }
        return line(
            "mapping",
            exerciseId,
            reviewed.reviewState.name,
            reviewed.directPrimaryMuscle,
            reviewed.descriptiveSecondaryMuscles.sorted().joinToString(","),
            reviewed.provenance.policyVersion.toString()
        )
    }

    private fun line(vararg fields: String): String = fields.joinToString(FIELD_SEPARATOR)
}
