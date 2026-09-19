package wallcrawl.elopenmike.com.core.ai

import java.security.MessageDigest
import java.util.Locale
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.SessionProgramConstraints
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext
import wallcrawl.elopenmike.com.core.model.ProgressionDecision
import wallcrawl.elopenmike.com.core.model.ProgressionReasonCode

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
 * Profile identity and revision, lifetime completed workouts, the ordered candidate id and
 * accepted-metadata content list, catalog and review-policy identity, whether the reviewed
 * path was enabled, the derived adaptation state, the declared session constraints, and the
 * accounting week and zone when a program state exists. Candidate **order** is included
 * because it is an input to selection. Frequency and the scheduling policy's canonical
 * practice dates, current local date and zone are included even when no muscle is preferred.
 * Independently read history must not compare equal merely because count/dates match:
 * candidate capability-evidence membership, usable load sources, the legacy-only completed-rep
 * floor, explicit rest choices, and ledger direct counts/provenance/integrity also participate.
 * Reviewed progression consumes bounded full outcomes and target-continuity provenance,
 * including source identities, timestamps, measurements, feedback and user deload choices.
 *
 * ## What it deliberately excludes
 *
 * Display language, translated names, notes, body/gender fields, and unused performance
 * metrics are excluded. Capability evidence is read
 * as membership by ranking, not as session-id or measurement provenance. Valid secondary
 * and omitted ledger counts are analytics-only; their shared integrity verdict is consumed.
 * The same canonical inputs read in either language produce the same digest, which makes the
 * localization boundary testable here rather than only asserted.
 * The explicit regeneration index is deliberately separate provenance: advancing the
 * planner counter must not invalidate a recommendation already displayed. Exact replay
 * supplies the recorded index along with the same context.
 *
 * ## Scope
 *
 * A freshness check over local state, not a security control or a promise of transactional
 * reads. It retains only the digest, not another copy of history.
 */
object RecommendationContextIdentity {

    private const val FORMAT_VERSION = "wallcrawl-recommendation-context-v4"

    /** ASCII unit separator: it cannot occur in a catalog id, muscle name, or zone id. */
    private const val FIELD_SEPARATOR = "\u001F"

    fun of(context: WorkoutGenerationContext): String {
        val programState = context.trainingProgramState
        val ledger = programState?.weeklyLedger
        val lines = mutableListOf(
            line("validator", ProgramValidatorVersion.WHOLE_PROGRAM_V2.name),
            line("durationEstimator", WorkoutDurationEstimator.VERSION),
            line("profile", context.userProfile.id),
            line("profileRevision", context.userProfile.revision.toString()),
            line("completedWorkouts", context.completedWorkoutCount.toString()),
            line("profileFrequency", context.userProfile.daysPerWeek.toString()),
            line("trainingFrequency", context.trainingFrequencyDaysPerWeek.toString()),
            line("loadUnit", context.preferredUnits.name),
            line("catalog", context.catalogVersion ?: "none"),
            line("reviewPolicy", context.reviewPolicyVersion.toString()),
            line("reviewedPath", (context.automaticEligibilityResult != null).toString()),
            line("adaptationState", programState?.adaptationState?.name ?: "none"),
            line("programStatePolicy", programState?.policyVersion?.name ?: "none"),
            line("ledgerPolicy", ledger?.policyVersion?.name ?: "none"),
            line("ledgerCatalog", ledger?.catalogVersion ?: "none"),
            line("ledgerReviewPolicy", ledger?.reviewPolicyVersion?.toString() ?: "none"),
            line("weekStart", ledger?.weekStartEpochDay?.toString() ?: "none"),
            line("zone", ledger?.timeZoneId ?: "none"),
            line("constraints", context.programConstraints.canonicalForm())
        )
        ledger?.let {
            val usable = it.isWellFormed()
            lines += line("ledgerIntegrity", usable.toString())
            // The shared guard bounds valid maps to 64 keys. An invalid ledger stays an
            // explicit invalid marker, not an empty successful week or an unbounded hash.
            if (usable) it.directPrimarySets.toSortedMap().forEach { (muscle, count) ->
                lines += line("directSets", muscle, count.toString())
            }
        }
        context.schedulingEvidence?.let { evidence ->
            lines += line("scheduling", evidence.policyVersion, evidence.todayEpochDay.toString(), evidence.timeZoneId)
            evidence.practiceDates.forEach { (primary, dates) ->
                lines += line("practice", primary, dates.joinToString(","))
            }
        }
        if (programState != null) {
            lines += line("trainingPolicy", TrainingPolicyVersion.STATE_BASED_DOSE_EFFORT_REST_V2.name)
            lines += line("progressionPolicy", ProgressionDecision.VERSION)
            lines += line("deloadPolicy", DeloadOfferPolicy.VERSION)
            lines += line("deloadChoice", context.deloadPreferences.toString())
            ProgressionEngine.requireBounded(context.progressionHistory)
            context.progressionHistory.sortedBy { it.id }.forEach { session ->
                lines += line(
                    "progressionSession", session.id, session.startedAtTimestamp.toString(),
                    session.completedAtTimestamp.toString(), session.status.name, session.weightUnit.name,
                    session.origin.name,
                    ((session.completedAtTimestamp ?: Long.MAX_VALUE) <= context.historyAsOfTimestamp).toString()
                )
                session.exercises.sortedWith(compareBy({ it.exerciseId }, { it.id })).forEach { exercise ->
                    lines += line("progressionExercise", exercise.id, exercise.sessionId,
                        exercise.exerciseId, exercise.prescription.toString())
                    exercise.sets.sortedWith(compareBy({ it.setNumber }, { it.id })).forEach { set ->
                        lines += line("progressionSet", set.toString())
                    }
                }
                context.recentRecommendationRecords[session.id]?.let { record ->
                    lines += line("progressionBasis", ProgressionReasonCode.decode(record.reasonCodes)
                        .sortedBy { it.exerciseId }.toString())
                }
            }
        }

        // Positional, so a reordered candidate list is a different context. The list is not
        // sorted for the same reason: order is an input, not an incidental detail.
        context.allowedExercises.forEachIndexed { index, exercise ->
            lines += line(
                "candidate",
                index.toString(),
                exercise.id,
                exercise.acceptedMetadata()?.contextIdentityForm() ?: "legacy"
            )
            val performance = context.exerciseHistory[exercise.id]
            val lastWeight = performance?.lastWeight.usableLoad()
            val minimumReps = if (programState == null && lastWeight != null && exercise.type == ExerciseType.WEIGHT_REPS) {
                performance?.minimumCompletedReps()
            } else null
            lines += line(
                "loadHistory", exercise.id, lastWeight?.toString() ?: "none",
                minimumReps?.toString() ?: "none",
                context.userProfile.confirmedStartingLoads[exercise.id].usableLoad()?.toString() ?: "none"
            )
            if (programState != null) {
                lines += line("reviewedWorkLoad", exercise.id, context.recordedWorkLoad(exercise.id, exercise.type).toString())
            }
            if (context.automaticEligibilityResult != null) {
                lines += line("capabilityEvidence", exercise.id, context.capabilityEvidence.appliesTo(exercise.id).toString())
            }
            if (programState != null) context.priorUserRestPreferences[exercise.id]?.let { preference ->
                lines += line("userRest", exercise.id, preference.restClass.name, preference.restSeconds.toString())
            }
        }

        val canonical = (sequenceOf(FORMAT_VERSION) + lines.asSequence()).joinToString("\n")
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> String.format(Locale.ROOT, "%02x", byte) }
    }

    /** Matches both prescription construction and validation's traceable-load boundary. */
    private fun Double?.usableLoad(): Double? = this?.takeIf { it.isFinite() && it >= 0.0 }

    private fun SessionProgramConstraints.canonicalForm(): String = listOf(
        uniqueExerciseIds.toString(),
        uniqueProgressionFamilies.toString(),
        requiredMovementPatterns.map { it.name }.sorted().joinToString(",")
    ).joinToString(",")

    private fun wallcrawl.elopenmike.com.core.model.ReviewedExerciseMetadata.contextIdentityForm():
        String {
        val fields = buildList {
            add(reviewState.name)
            add(directPrimaryMuscle)
            add(descriptiveSecondaryMuscles.sorted().joinToString(","))
            add(movementPattern.name)
            add(complexity.name)
            add(progressionFamily)
            add(prescriptionShape.name)
            add(approvedRegressions.joinToString(",") { "${it.exerciseId}:${it.rationale.orEmpty()}" })
            add(approvedSubstitutions.joinToString(",") { "${it.exerciseId}:${it.rationale.orEmpty()}" })
            add(capabilityRequirements.map { it.name }.sorted().joinToString(","))
            add(supportRequirement.name)
            add(impactLevel.name)
            add(equipmentAlternatives.joinToString(";") { it.sorted().joinToString(",") })
            add(clearedTrainingConstraints.map { it.name }.sorted().joinToString(","))
            add(provenance.schemaVersion.toString())
            add(provenance.policyVersion.toString())
            add(aiReviewProvenance?.reviewedContentSha256 ?: "human")
        }
        return fields.joinToString(FIELD_SEPARATOR)
    }

    private fun line(vararg fields: String): String = fields.joinToString(FIELD_SEPARATOR)
}
