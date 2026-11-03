package wallcrawl.elopenmike.com.core.ai

import java.security.MessageDigest
import java.util.Locale
import java.time.Instant
import wallcrawl.elopenmike.com.core.model.ConditionExerciseIdentity
import wallcrawl.elopenmike.com.core.model.ConditionSchedule
import wallcrawl.elopenmike.com.core.model.ConditionPlanningResult
import wallcrawl.elopenmike.com.core.model.conditionDigest
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
 * Condition inputs include exact support/review/art identities, purpose/dose decisions,
 * current schedule eligibility, calendar/zone, retained exposures and pending reassessment.
 * The exact schedule checkedAt timestamp belongs only to the frozen session receipt.
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

    private const val FORMAT_VERSION = "wallcrawl-recommendation-context-v6"

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
        lines += line("conditions", conditionFacts(context))
        if (context.userProfile.reportedConditions.isNotEmpty()) {
            lines += line("reportedConditions", conditionDigest(context.userProfile.reportedConditions.sorted()))
        }
        val header = context.programHeader
        lines += line("planningIntent", context.planningIntent.name)
        lines += line("programHeader", header.profileId, header.programId ?: "none", header.revision.toString(),
            header.rawStorageFingerprint ?: "none")
        context.programInput?.let { input ->
            val enrollment = input.enrollment
            val config = enrollment.configuration
            lines += line("enrollment", enrollment.profileId, enrollment.programId, enrollment.revision.toString(),
                enrollment.status.name, enrollment.lifecyclePolicyVersion)
            lines += line("programConfiguration", config.policyVersion, config.primaryGoal.name,
                config.goals.map { it.name }.sorted().joinToString(","), config.sessionsPerWeek.toString())
            val position = input.position
            lines += line("programPosition", position.slotOrdinal.toString(), position.blockNumber.toString(),
                position.weekNumber.toString(), position.sessionInWeek.toString(), input.objective.name)
        }
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
                    if (exercise.isLegacyMobility) lines += line("legacyMobility", exercise.id)
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

    /** Stable consumed facts; checkedAt belongs only to the frozen session receipt. */
    private fun conditionFacts(context: WorkoutGenerationContext): String = conditionDigest(mapOf(
        "selected" to context.userProfile.activeConditions.sortedBy { it.conditionId }
            .map { listOf(it.conditionId, it.contextSchemaVersion, it.answers) },
        "planning" to conditionPlanningFacts(context.conditionPlanning),
        "candidateContent" to (context.conditionPlanning as? ConditionPlanningResult.Ready)?.let { ready ->
            context.allowedExercises.filter { it.id in ready.ordinaryRules }.map { listOf(it.id, ConditionExerciseIdentity.contentSha256(it)) }
        },
        "durationBudget" to context.preferredWorkoutDurationMinutes.takeIf { context.conditionPlanning is ConditionPlanningResult.Ready },
        "history" to context.conditionHistory?.let { history -> listOf(
            history.sourceDigest, history.zoneId.id,
            Instant.ofEpochMilli(history.asOfTimestamp).atZone(history.zoneId).toLocalDate().toEpochDay(),
            context.conditionSupport?.records?.values?.filter { record -> context.userProfile.activeConditions.any { it.conditionId == record.conditionId } }
                ?.sortedBy { it.conditionId }?.flatMap { record -> record.cases.filter { case ->
                    val selection = context.userProfile.activeConditions.single { it.conditionId == record.conditionId }
                    case.requiredAnswers.all { (id, answer) -> selection.answers[id] == answer }
                }.flatMap { case -> case.rules.mapNotNull { rule -> rule.schedule?.let { schedule ->
                    listOf(record.conditionId, case.id, rule.id, history.schedule(rule.exerciseIdentity.exerciseId, schedule).toString())
                } } } }
        ) },
        "support" to context.userProfile.activeConditions.sortedBy { it.conditionId }.map { selected ->
            context.conditionSupport?.records?.get(selected.conditionId)?.let { record -> listOf(
                record.conditionId, record.contentVersion, record.contentSha256, record.contextSchemaVersion, record.policyVersion,
                record.sources.sortedBy { it.id }.map { listOf(it.id, it.url, it.contentSha256) },
                record.review.let { listOf(it.state.name, it.reviewerIdentity, it.reviewerQualification,
                    it.reviewedAtEpochMillis, it.releaseAuthorization, it.reviewedContentSha256, it.sourceIds.sorted()) },
                record.cases.map { case -> listOf(case.id, case.requiredAnswers, case.rules.map { rule -> listOf(
                    rule.id, rule.exerciseIdentity.toString(), rule.executionIdentity, rule.effect.name,
                    rule.bounds?.toString(), rule.allowOrdinaryProgression, rule.supportivePrescription?.toString(), rule.schedule?.identityFacts()
                ) }) }
            ) }
        }
    ))

    internal fun conditionPlanningFacts(planning: ConditionPlanningResult): Any = when (planning) {
        ConditionPlanningResult.NotApplicable -> listOf("not-applicable")
        is ConditionPlanningResult.Blocked -> listOf("blocked", planning.reasons.map {
            listOf(it.reason, it.conditionIds.sorted(), it.exerciseIds.sorted())
        })
        is ConditionPlanningResult.Ready -> listOf("ready", planning.weightUnit.name,
            planning.ordinaryRules.toSortedMap().map { (id, rule) -> listOf(id, rule.bounds.toString(),
                rule.executionIdentity, rule.allowOrdinaryProgression, rule.sourceRuleReferences.map { it.toString() }.sorted(), rule.policyVersions.sorted()) },
            planning.supportive.map { listOf(it.exerciseId, it.prescription.toString(), it.executionIdentity,
                it.sourceRuleReferences.map { it.toString() }.sorted(), it.schedule.identityFacts()) },
            planning.context.let { frozen -> listOf(frozen.formatVersion, frozen.policyVersion,
                frozen.selectedConditions.sortedBy { it.conditionId }.map { listOf(it.conditionId, it.contextSchemaVersion, it.answers) },
                frozen.sourceSupportVersions, frozen.appliedRules.map { listOf(it.reference.toString(),
                    it.exerciseIdentity.toString(), it.executionIdentity, it.purpose.name, it.explanation.en, it.explanation.es) },
                frozen.consumedScheduleEvidence.map { listOf(it.reference.toString(), it.previousPerformedAtTimestamp) },
                frozen.consumedReassessmentEventIds.sorted()) }
        )
    }

    private fun ConditionSchedule.identityFacts() = listOf(
        sessionsPerWeek, minimumHoursBetweenSessions, instructions.en, instructions.es
    )

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
