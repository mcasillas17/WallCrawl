package wallcrawl.elopenmike.com.core.database.repository

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import wallcrawl.elopenmike.com.core.ai.MultiWeekProgramPolicy
import wallcrawl.elopenmike.com.core.database.PERSISTED_LIST_SEPARATOR
import wallcrawl.elopenmike.com.core.database.entity.*
import wallcrawl.elopenmike.com.core.model.*

private fun framedDigest(fields: List<String>): String {
    val bytes = ByteArrayOutputStream()
    DataOutputStream(bytes).use { output ->
        fields.forEach { value -> value.toByteArray(Charsets.UTF_8).let { output.writeInt(it.size); output.write(it) } }
    }
    return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
        .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
}

internal fun ProgramEnrollmentEntity.header(): ProgramHeader = ProgramHeader(profileId, programId, revision,
    framedDigest(listOf(profileId, programId, revision.toString(), lifecyclePolicyVersion, configurationPolicyVersion,
        primaryGoal, goals, sessionsPerWeek.toString(), status, createdAtTimestamp.toString(), updatedAtTimestamp.toString())))

/** Hashes the stored representation without interpreting an unsupported configuration. */
internal fun ProgramSessionAssociationEntity.rawAssociationDigest(): String {
    val goalNames = if (goals.isEmpty()) emptyList() else goals.split(PERSISTED_LIST_SEPARATOR).sorted()
    return framedDigest(listOf("wallcrawl-program-association-v1", sessionId, profileId, programId, enrollmentRevision.toString(),
        lifecyclePolicyVersion, configurationPolicyVersion, primaryGoal, goalNames.size.toString()) + goalNames +
        listOf(sessionsPerWeek.toString(), slotOrdinal.toString(), blockNumber.toString(), weekNumber.toString(),
            sessionInWeek.toString(), objective))
}

internal fun ProgramEnrollmentEntity.supported() = lifecyclePolicyVersion == MULTI_WEEK_PROGRAM_VERSION &&
    configurationPolicyVersion == PROGRAM_CONFIGURATION_VERSION
internal fun ProgramSessionAssociationEntity.supported() = lifecyclePolicyVersion == MULTI_WEEK_PROGRAM_VERSION &&
    configurationPolicyVersion == PROGRAM_CONFIGURATION_VERSION

internal fun ProgramEnrollmentEntity.validateRaw() {
    header()
    validateFacts(lifecyclePolicyVersion, configurationPolicyVersion, primaryGoal, goals)
    if (status !in ProgramStatus.entries.map { it.name } || createdAtTimestamp !in 0..4_102_444_800_000L ||
        updatedAtTimestamp !in 0..4_102_444_800_000L) throw ProgramException(ProgramFailure.INVALID_IDENTITY)
}
internal fun ProgramSessionAssociationEntity.validateRaw() {
    requireProgramIdentityToken(sessionId); requireProgramIdentityToken(profileId); requireProgramRunId(programId)
    if (enrollmentRevision <= 0) throw ProgramException(ProgramFailure.INVALID_IDENTITY)
    validateFacts(lifecyclePolicyVersion, configurationPolicyVersion, primaryGoal, goals)
    requireProgramIdentityToken(objective)
}
private fun validateFacts(lifecycle: String, configuration: String, primary: String, goals: String) {
    listOf(lifecycle, configuration, primary).forEach(::requireProgramIdentityToken)
    if (goals.length > 4096 || goals.any(Char::isISOControl)) throw ProgramException(ProgramFailure.INVALID_CONFIGURATION)
}
private fun configuration(policy: String, primary: String, goals: String, frequency: Int): ProgramConfiguration {
    val rawGoals = goals.split(PERSISTED_LIST_SEPARATOR)
    val primaryGoal = FitnessGoal.entries.firstOrNull { it.name == primary }
        ?: throw ProgramException(ProgramFailure.INVALID_CONFIGURATION)
    val parsed = rawGoals.map { token -> FitnessGoal.entries.firstOrNull { it.name == token }
        ?: throw ProgramException(ProgramFailure.INVALID_CONFIGURATION) }
    if (parsed != parsed.sortedBy { it.name } || parsed.distinct().size != parsed.size) {
        throw ProgramException(ProgramFailure.INVALID_CONFIGURATION)
    }
    return ProgramConfiguration(primaryGoal, parsed, frequency, policy)
}
internal fun ProgramEnrollmentEntity.toProgramEnrollment(): ProgramEnrollment {
    validateRaw()
    if (!supported()) throw ProgramException(ProgramFailure.UNSUPPORTED_POLICY)
    return ProgramEnrollment(profileId, programId, revision,
        configuration(configurationPolicyVersion, primaryGoal, goals, sessionsPerWeek),
        ProgramStatus.valueOf(status), createdAtTimestamp, updatedAtTimestamp, lifecyclePolicyVersion)
        .also(MultiWeekProgramPolicy::validateSupported)
}
internal fun ProgramSessionAssociationEntity.toProgramAssociation(): ProgramAssociation {
    validateRaw()
    if (!supported()) throw ProgramException(ProgramFailure.UNSUPPORTED_POLICY)
    return ProgramAssociation(sessionId, profileId, ProgramSessionSnapshot(programId, enrollmentRevision,
        configuration(configurationPolicyVersion, primaryGoal, goals, sessionsPerWeek),
        ProgramPosition(slotOrdinal, blockNumber, weekNumber, sessionInWeek),
        ProgramObjective.entries.firstOrNull { it.name == objective } ?: throw ProgramException(ProgramFailure.INVALID_PROGRESS),
        lifecyclePolicyVersion)).also { MultiWeekProgramPolicy.validateSupported(it.snapshot) }
}
internal fun ProgramEnrollment.toEntity() = ProgramEnrollmentEntity(profileId, programId, revision, lifecyclePolicyVersion,
    configuration.policyVersion, configuration.primaryGoal.name,
    configuration.goals.sortedBy { it.name }.joinToString(PERSISTED_LIST_SEPARATOR) { it.name },
    configuration.sessionsPerWeek, status.name, createdAtEpochMillis, updatedAtEpochMillis)
internal fun ProgramAssociation.toEntity() = ProgramSessionAssociationEntity(sessionId, profileId, snapshot.programId,
    snapshot.enrollmentRevision, snapshot.lifecyclePolicyVersion, snapshot.configuration.policyVersion,
    snapshot.configuration.primaryGoal.name, snapshot.configuration.goals.sortedBy { it.name }.joinToString(PERSISTED_LIST_SEPARATOR) { it.name },
    snapshot.configuration.sessionsPerWeek, snapshot.position.slotOrdinal, snapshot.position.blockNumber,
    snapshot.position.weekNumber, snapshot.position.sessionInWeek, snapshot.objective.name)
internal fun ProgramEnrollmentEntity.facts() = ProgramRecordedFacts(programId, lifecyclePolicyVersion, configurationPolicyVersion,
    primaryGoal, goals.split(PERSISTED_LIST_SEPARATOR), sessionsPerWeek)
internal fun ProgramSessionAssociationEntity.toHistoryAssociation(): ProgramHistoryAssociation {
    validateRaw()
    return ProgramHistoryAssociation(sessionId, profileId, ProgramRecordedFacts(programId, lifecyclePolicyVersion,
        configurationPolicyVersion, primaryGoal, goals.split(PERSISTED_LIST_SEPARATOR), sessionsPerWeek),
        enrollmentRevision, ProgramPosition(slotOrdinal, blockNumber, weekNumber, sessionInWeek), objective,
        if (supported()) toProgramAssociation().snapshot else null)
}

/** Membership is never inferred from the presence of only one half of the stored agreement. */
internal fun validateProgramBinding(record: WorkoutRecommendationRecordEntity?, association: ProgramSessionAssociationEntity?) {
    require((record?.programAssociationDigest != null) == (association != null)) { "Recommendation program membership is inconsistent." }
    require(record?.programAssociationDigest == null || record.programAssociationDigest.matches(Regex("[0-9a-f]{64}"))) {
        "Invalid recommendation program association digest."
    }
    association?.let {
        it.validateRaw()
        require(record?.programAssociationDigest == it.rawAssociationDigest()) {
            "Recommendation program association digest does not match."
        }
        if (it.supported()) it.toProgramAssociation()
    }
}
