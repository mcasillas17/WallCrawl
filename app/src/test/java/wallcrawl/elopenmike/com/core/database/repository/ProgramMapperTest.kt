package wallcrawl.elopenmike.com.core.database.repository

import org.junit.Assert.*
import org.junit.Test
import wallcrawl.elopenmike.com.core.ai.MultiWeekProgramPolicy
import wallcrawl.elopenmike.com.core.database.dao.ProgramSlotRow
import wallcrawl.elopenmike.com.core.database.entity.ProgramEnrollmentEntity
import wallcrawl.elopenmike.com.core.model.*

class ProgramMapperTest {
    private val row = ProgramEnrollmentEntity("profile", "11111111-1111-1111-1111-111111111111", 1,
        MULTI_WEEK_PROGRAM_VERSION, PROGRAM_CONFIGURATION_VERSION, "BUILD_MUSCLE", "BUILD_MUSCLE", 2,
        "ACTIVE", 1_000, 1_000)

    @Test fun headerIncludesEveryRawColumnWithoutDecodingConfiguration() {
        val original = row.header()
        val changes = listOf(row.copy(profileId = "other"), row.copy(programId = "22222222-2222-2222-2222-222222222222"),
            row.copy(revision = 2), row.copy(lifecyclePolicyVersion = "FUTURE"), row.copy(configurationPolicyVersion = "FUTURE"),
            row.copy(primaryGoal = "BAD"), row.copy(goals = "BAD"), row.copy(sessionsPerWeek = 1), row.copy(status = "BAD"),
            row.copy(createdAtTimestamp = 0), row.copy(updatedAtTimestamp = 0))
        changes.forEach { assertNotEquals(original.rawStorageFingerprint, it.header().rawStorageFingerprint) }
        assertEquals(original, row.header())
    }
    @Test fun knownMalformedFieldsAreNeverDefaulted() {
        for (bad in listOf(row.copy(primaryGoal = "BAD"), row.copy(goals = ""), row.copy(goals = "BUILD_MUSCLE|||BUILD_MUSCLE"))) {
            try { bad.toProgramEnrollment(); fail("Invalid configuration must fail") }
            catch (e: ProgramException) { assertEquals(ProgramFailure.INVALID_CONFIGURATION, e.failure) }
        }
    }
    @Test fun unknownSnapshotsKeepRawRecordedFactsWithoutParsingGoals() {
        val snapshot = MultiWeekProgramPolicy.snapshot(row.toProgramEnrollment(), ProgramPosition(0, 1, 1, 1))
        val raw = ProgramAssociation("session", "profile", snapshot).toEntity().copy(
            lifecyclePolicyVersion = "FUTURE_LIFECYCLE", configurationPolicyVersion = "FUTURE_CONFIGURATION",
            primaryGoal = "FUTURE_GOAL", goals = "FUTURE_GOAL", objective = "FUTURE_OBJECTIVE")
        val history = raw.toHistoryAssociation()
        assertEquals("FUTURE_GOAL", history.facts.primaryGoal)
        assertEquals("FUTURE_OBJECTIVE", history.objective)
        assertEquals(snapshot.position, history.position)
        assertNull(history.supportedSnapshot)
    }
    @Test fun bindingDetectsEveryFrozenFieldChangeAndAbsentRecommendation() {
        val snapshot = MultiWeekProgramPolicy.snapshot(row.toProgramEnrollment(), ProgramPosition(0, 1, 1, 1))
        val association = ProgramAssociation("session", "profile", snapshot)
        val raw = association.toEntity()
        val digest = MultiWeekProgramPolicy.associationDigest(association)
        assertEquals(digest, raw.rawAssociationDigest())
        assertEquals(association, raw.toProgramAssociation())
        assertEquals(association, ProgramSlotRow(raw, "IN_PROGRESS", null, digest).toSlot().association)
        for (bad in listOf(raw.copy(enrollmentRevision = 2), raw.copy(profileId = "other"), raw.copy(sessionId = "other"))) {
            try { ProgramSlotRow(bad, "IN_PROGRESS", null, digest).toSlot(); fail("Changed binding must fail") }
            catch (_: IllegalArgumentException) { }
        }
        try { ProgramSlotRow(raw, "IN_PROGRESS", null, null).toSlot(); fail("Missing binding must fail") }
        catch (_: IllegalArgumentException) { }
    }
}
