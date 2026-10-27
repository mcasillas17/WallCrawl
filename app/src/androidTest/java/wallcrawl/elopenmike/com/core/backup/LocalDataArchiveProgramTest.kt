package wallcrawl.elopenmike.com.core.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.model.*
import wallcrawl.elopenmike.com.core.ai.MultiWeekProgramPolicy

@RunWith(AndroidJUnit4::class)
class LocalDataArchiveProgramTest {
    @Test fun formatFiveRoundTripsOwnedEnrollmentFrozenAssociationsAndDigest() {
        val archive = programArchive()
        val restored = LocalDataArchiveCodec.read(ByteArrayInputStream(bytes(archive)))
        assertEquals(archive, restored)
        assertEquals(5, restored.metadata.archiveVersion)
    }

    @Test fun membershipCannotBeLostAddedOrRebound() {
        val valid = programArchive()
        val association = valid.snapshot.programAssociations.single()
        val record = valid.snapshot.recommendationRecords.single()
        listOf(
            valid.snapshot.copy(programAssociations = emptyList()),
            valid.snapshot.copy(recommendationRecords = listOf(record.copy(programAssociationDigest = null))),
            valid.snapshot.copy(recommendationRecords = listOf(record.copy(programAssociationDigest = "b".repeat(64)))),
            valid.snapshot.copy(recommendationRecords = emptyList()),
            valid.snapshot.copy(programAssociations = listOf(association, association)),
            valid.snapshot.copy(programAssociations = listOf(association.copy(sessionId = "missing"))),
            valid.snapshot.copy(programEnrollment = null)
        ).forEach { snapshot -> refusesBeforeOpen(valid.copy(snapshot = snapshot)) }
    }

    @Test fun currentAndHistoricalPrefixesStatusesConfigurationsAndRevisionBudgetsAreStrict() {
        val valid = programArchive()
        val enrollment = valid.snapshot.programEnrollment!!
        val association = valid.snapshot.programAssociations.single()
        val frozen = association.snapshot
        val wrongConfigurations = listOf(
            frozen.copy(configuration = frozen.configuration.copy(sessionsPerWeek = 1)),
            frozen.copy(configuration = frozen.configuration.copy(goals = emptyList())),
            frozen.copy(configuration = frozen.configuration.copy(goals = listOf(FitnessGoal.STRENGTH, FitnessGoal.STRENGTH))),
            frozen.copy(position = frozen.position.copy(slotOrdinal = 1)),
            frozen.copy(position = frozen.position.copy(blockNumber = 2)),
            frozen.copy(objective = ProgramObjective.CONTINUE_AND_RECORD),
            frozen.copy(enrollmentRevision = enrollment.revision),
            frozen.copy(programId = "22222222-2222-2222-2222-222222222222")
        )
        wrongConfigurations.forEach { snapshot ->
            val changed = association.copy(snapshot = snapshot)
            refusesBeforeOpen(valid.copy(snapshot = valid.snapshot.copy(
                programAssociations = listOf(changed), recommendationRecords = listOf(
                    valid.snapshot.recommendationRecords.single().copy(programAssociationDigest = MultiWeekProgramPolicy.associationDigest(changed))))))
        }
        listOf(ProgramStatus.PAUSED, ProgramStatus.COMPLETED).forEach { status ->
            refusesBeforeOpen(valid.copy(snapshot = valid.snapshot.copy(programEnrollment = enrollment.copy(status = status))))
        }
        refusesBeforeOpen(valid.copy(snapshot = valid.snapshot.copy(programEnrollment = enrollment.copy(revision = 3))))
        refusesBeforeOpen(valid.copy(snapshot = valid.snapshot.copy(profile = valid.snapshot.profile!!.copy(onboardingCompleted = false))))
    }

    @Test fun unknownProgramPoliciesAreRefusedBeforeOpeningDestination() {
        val valid = programArchive()
        listOf(
            valid.snapshot.copy(programEnrollment = valid.snapshot.programEnrollment!!.copy(lifecyclePolicyVersion = "future")),
            valid.snapshot.copy(programEnrollment = valid.snapshot.programEnrollment.let { it.copy(configuration = it.configuration.copy(policyVersion = "future")) }),
            valid.snapshot.copy(programAssociations = valid.snapshot.programAssociations.map { it.copy(snapshot = it.snapshot.copy(lifecyclePolicyVersion = "future")) })
        ).forEach { snapshot ->
            assertEquals(ArchiveRejection.UNSUPPORTED_PROGRAM, refusesBeforeOpen(valid.copy(snapshot = snapshot)).rejection)
        }
    }

    @Test fun completedPrefixesAndPausedCurrentRunsRoundTripWithoutClockOrdering() {
        val complete = completedArchive(8)
        assertEquals(complete, LocalDataArchiveCodec.read(ByteArrayInputStream(bytes(complete))))
        val partial = completedArchive(2)
        val paused = partial.copy(snapshot = partial.snapshot.copy(programEnrollment = partial.snapshot.programEnrollment!!.copy(
            status = ProgramStatus.PAUSED, createdAtEpochMillis = 2000, updatedAtEpochMillis = 500)))
        assertEquals(paused, LocalDataArchiveCodec.read(ByteArrayInputStream(bytes(paused))))
        listOf(
            complete.snapshot.copy(programEnrollment = complete.snapshot.programEnrollment!!.copy(status = ProgramStatus.ACTIVE)),
            partial.snapshot.copy(programEnrollment = partial.snapshot.programEnrollment.copy(status = ProgramStatus.COMPLETED)),
            partial.snapshot.copy(programAssociations = partial.snapshot.programAssociations.drop(1)),
            partial.snapshot.copy(programEnrollment = partial.snapshot.programEnrollment.copy(revision = 4))
        ).forEach { refusesBeforeOpen(partial.copy(snapshot = rebind(it))) }
    }

    @Test fun replacedRunsHaveTheirOwnConfigurationCompletedPrefixAndReplacementRevisionBudget() {
        val partial = completedArchive(2)
        val old = partial.snapshot.programEnrollment!!
        val latest = old.copy(programId = "22222222-2222-2222-2222-222222222222", revision = 6,
            configuration = old.configuration.copy(sessionsPerWeek = 6), status = ProgramStatus.ACTIVE)
        val valid = partial.copy(snapshot = partial.snapshot.copy(programEnrollment = latest))
        assertEquals(valid, LocalDataArchiveCodec.read(ByteArrayInputStream(bytes(valid))))
        listOf(
            valid.snapshot.copy(programEnrollment = latest.copy(revision = 5)),
            valid.snapshot.copy(programAssociations = valid.snapshot.programAssociations.drop(1)),
            valid.snapshot.copy(programAssociations = valid.snapshot.programAssociations.mapIndexed { index, row ->
                if (index == 1) row.copy(snapshot = row.snapshot.copy(enrollmentRevision = 2)) else row
            }),
            valid.snapshot.copy(programAssociations = valid.snapshot.programAssociations.mapIndexed { index, row ->
                if (index == 1) row.copy(snapshot = row.snapshot.copy(configuration = row.snapshot.configuration.copy(sessionsPerWeek = 3))) else row
            }),
            valid.snapshot.copy(programAssociations = valid.snapshot.programAssociations.mapIndexed { index, row ->
                if (index == 1) row.copy(snapshot = row.snapshot.copy(enrollmentRevision = Long.MAX_VALUE - 1)) else row
            })
        ).forEach { refusesBeforeOpen(valid.copy(snapshot = rebind(it))) }
        val cancelled = valid.snapshot.sessions.mapIndexed { index, row ->
            if (index == 1) row.copy(status = SessionStatus.CANCELLED, completedAtTimestamp = null) else row
        }
        refusesBeforeOpen(valid.copy(snapshot = valid.snapshot.copy(sessions = cancelled)))
        val active = cancelled.mapIndexed { index, row -> if (index == 1) row.copy(status = SessionStatus.IN_PROGRESS) else row }
        refusesBeforeOpen(valid.copy(snapshot = valid.snapshot.copy(sessions = active)))
    }

    @Test fun overlappingReplacedRunRevisionRangesAreRejectedEvenWithMatchingDigests() {
        val base = completedArchive(2)
        val rows = base.snapshot.programAssociations
        val second = rows[1].copy(snapshot = rows[1].snapshot.copy(programId = "22222222-2222-2222-2222-222222222222",
            position = ProgramPosition(0, 1, 1, 1), enrollmentRevision = 3))
        val latest = base.snapshot.programEnrollment!!.copy(programId = "33333333-3333-3333-3333-333333333333", revision = 6)
        val overlap = rebind(base.snapshot.copy(programEnrollment = latest, programAssociations = listOf(rows[0], second)))
        refusesBeforeOpen(base.copy(snapshot = overlap))
        val legal = rebind(overlap.copy(programAssociations = listOf(rows[0], second.copy(snapshot = second.snapshot.copy(enrollmentRevision = 4))),
            programEnrollment = latest.copy(revision = 7)))
        assertEquals(legal, LocalDataArchiveCodec.read(ByteArrayInputStream(bytes(base.copy(snapshot = legal)))).snapshot)
    }

    @Test fun programFieldsAreStrictBoundedAndForbiddenInEarlierFormats() {
        val valid = bytes(programArchive()).decodeToString()
        val changes = listOf(
            "\"archiveVersion\":5" to "\"archiveVersion\":4",
            "\"enrollmentRevision\":1" to "\"enrollmentRevision\":9223372036854775808",
            "\"slotOrdinal\":0" to "\"slotOrdinal\":24",
            "\"blockNumber\":1" to "\"blockNumber\":0",
            "\"weekNumber\":1" to "\"weekNumber\":5",
            "\"sessionInWeek\":1" to "\"sessionInWeek\":7",
            "\"sessionsPerWeek\":2" to "\"sessionsPerWeek\":7",
            "\"enrollmentRevision\":1" to "\"enrollmentRevision\":0",
            "\"slotOrdinal\":0" to "\"slotOrdinal\":0,\"slotOrdinal\":0",
            "\"objective\":\"ESTABLISH_ROUTINE\"" to "\"objective\":\"secret-invalid\"",
            "\"revision\":2" to "\"revision\":2,\"extra\":0",
            "\"profileId\":\"default_user\"" to "\"profileId\":\"secret|||invalid\"",
            "\"lifecyclePolicyVersion\":\"MULTI_WEEK_PROGRAM_V1\"" to "\"lifecyclePolicyVersion\":\"future\"",
            "\"goals\":[\"STRENGTH\"]" to "\"goals\":[\"STRENGTH\",\"BUILD_MUSCLE\"]"
        )
        changes.forEach { (from, to) ->
            assertTrue(valid.contains(from))
            val error = assertThrows(LocalDataArchiveException::class.java) {
                LocalDataArchiveCodec.read(ByteArrayInputStream(valid.replace(from, to).encodeToByteArray()))
            }
            assertNotEquals(ArchiveRejection.CHECKSUM_MISMATCH, error.rejection)
            assertFalse(error.message.orEmpty().contains("secret"))
        }
        val oversized = programArchive().let { it.copy(snapshot = it.snapshot.copy(
            programAssociations = List(LocalDataArchiveLimits.MAX_SESSIONS + 1) { _ -> it.snapshot.programAssociations.single() })) }
        assertEquals(ArchiveRejection.TOO_LARGE, refusesBeforeOpen(oversized).rejection)
        val digestOnly = LocalDataArchiveFixtures.archive().let { it.copy(metadata = it.metadata.copy(archiveVersion = 4),
            snapshot = it.snapshot.copy(recommendationRecords = it.snapshot.recommendationRecords.map { r -> r.copy(programAssociationDigest = "a".repeat(64)) })) }
        assertThrows(IllegalArgumentException::class.java) { bytes(digestOnly) }
    }

    @Test fun readerCapsAssociationArrayAndNeverDefaultsMissingProgramFields() {
        val valid = bytes(programArchive()).decodeToString()
        val missing = JSONObject(valid)
        missing.getJSONObject("data").getJSONObject("programEnrollment").remove("configuration")
        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(ByteArrayInputStream(missing.toString().encodeToByteArray()))
        }
        assertEquals(ArchiveRejection.MALFORMED, error.rejection)
        val oversized = JSONObject(valid)
        val data = oversized.getJSONObject("data")
        val row = data.getJSONArray("programAssociations").getJSONObject(0)
        val rows = JSONArray()
        repeat(LocalDataArchiveLimits.MAX_SESSIONS + 1) { rows.put(row) }
        data.put("programAssociations", rows)
        val limit = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(ByteArrayInputStream(oversized.toString().encodeToByteArray()))
        }
        assertEquals(ArchiveRejection.TOO_LARGE, limit.rejection)
    }

    @Test fun exactRevisionParsingRejectsOverflowWithoutLettingChecksumOrCoherenceMaskIt() {
        val base = programArchive()
        val empty = base.copy(snapshot = base.snapshot.copy(
            programEnrollment = base.snapshot.programEnrollment!!.copy(revision = Long.MAX_VALUE),
            programAssociations = emptyList(), sessions = emptyList(), recommendationRecords = emptyList()))
        val valid = bytes(empty).decodeToString()
        assertEquals(empty, LocalDataArchiveCodec.read(ByteArrayInputStream(valid.encodeToByteArray())))
        listOf("9223372036854775808", "9223372036854775807.0", "9.223372036854776E18", "\"9223372036854775807\"").forEach { literal ->
            val changed = valid.replace("\"revision\":9223372036854775807", "\"revision\":$literal")
            assertNotEquals(valid, changed)
            val error = assertThrows(LocalDataArchiveException::class.java) {
                LocalDataArchiveCodec.read(ByteArrayInputStream(changed.encodeToByteArray()))
            }
            assertEquals(ArchiveRejection.INVALID_VALUE, error.rejection)
        }
        val lastReservation = base.copy(snapshot = rebind(base.snapshot.copy(
            programEnrollment = base.snapshot.programEnrollment.copy(revision = Long.MAX_VALUE),
            programAssociations = base.snapshot.programAssociations.map {
                it.copy(snapshot = it.snapshot.copy(enrollmentRevision = Long.MAX_VALUE - 1))
            })))
        val lastValid = bytes(lastReservation).decodeToString()
        assertEquals(lastReservation, LocalDataArchiveCodec.read(ByteArrayInputStream(lastValid.encodeToByteArray())))
        listOf("9223372036854775808", "9223372036854775806.0", "\"9223372036854775806\"", "0", "-1").forEach { literal ->
            val changed = lastValid.replace("\"enrollmentRevision\":9223372036854775806", "\"enrollmentRevision\":$literal")
            val error = assertThrows(LocalDataArchiveException::class.java) {
                LocalDataArchiveCodec.read(ByteArrayInputStream(changed.encodeToByteArray()))
            }
            assertEquals(ArchiveRejection.INVALID_VALUE, error.rejection)
        }
    }

    @Test fun newProgramNumbersRejectQuotedDecimalAndExponentTokensWithoutChangingChecksum() {
        val valid = bytes(programArchive()).decodeToString()
        mapOf("revision" to 2, "enrollmentRevision" to 1, "sessionsPerWeek" to 2,
            "slotOrdinal" to 0, "blockNumber" to 1, "weekNumber" to 1, "sessionInWeek" to 1,
            "createdAtEpochMillis" to 1000, "updatedAtEpochMillis" to 1000).forEach { (field, value) ->
            listOf("\"$value\"", "$value.0", "${value}e0").forEach { literal ->
                val changed = valid.replace("\"$field\":$value", "\"$field\":$literal")
                assertNotEquals(field, valid, changed)
                val error = assertThrows(LocalDataArchiveException::class.java) {
                    LocalDataArchiveCodec.read(ByteArrayInputStream(changed.encodeToByteArray()))
                }
                assertEquals(field, ArchiveRejection.INVALID_VALUE, error.rejection)
            }
        }
    }

    @Test fun numericAssociationIdentifierCannotCoerceIntoAValidBoundString() {
        val base = programArchive()
        val association = base.snapshot.programAssociations.single().copy(sessionId = "123")
        val numericId = base.copy(snapshot = rebind(base.snapshot.copy(programAssociations = listOf(association),
            sessions = base.snapshot.sessions.map { it.copy(id = "123", exercises = emptyList()) })))
        val valid = bytes(numericId).decodeToString()
        assertEquals(numericId, LocalDataArchiveCodec.read(ByteArrayInputStream(valid.encodeToByteArray())))
        // Only the last sessionId occurrence is the association; recommendation/session stay strings.
        val target = "\"sessionId\":\"123\""
        val start = valid.lastIndexOf(target)
        assertTrue(start >= 0)
        val changed = valid.replaceRange(start, start + target.length, "\"sessionId\":123")
        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(ByteArrayInputStream(changed.encodeToByteArray()))
        }
        assertEquals(ArchiveRejection.INVALID_VALUE, error.rejection)
    }

    @Test fun everyNewProgramTextFieldRequiresAStringToken() {
        val valid = bytes(programArchive()).decodeToString()
        val fields = mapOf("policyVersion" to PROGRAM_CONFIGURATION_VERSION, "primaryGoal" to "STRENGTH",
            "profileId" to "default_user", "programId" to "11111111-1111-1111-1111-111111111111",
            "lifecyclePolicyVersion" to MULTI_WEEK_PROGRAM_VERSION, "status" to "ACTIVE",
            "objective" to "ESTABLISH_ROUTINE", "programAssociationDigest" to
                programArchive().snapshot.recommendationRecords.single().programAssociationDigest!!)
        fields.forEach { (field, value) ->
            // Restrict replacement to the program section; older profile/session fields keep their readers.
            val target = "\"$field\":\"$value\""
            val start = valid.lastIndexOf(target)
            assertTrue(field, start >= 0)
            listOf("123", "true", "null").forEach { literal ->
                val changed = valid.replaceRange(start, start + target.length, "\"$field\":$literal")
                val error = assertThrows(LocalDataArchiveException::class.java) {
                    LocalDataArchiveCodec.read(ByteArrayInputStream(changed.encodeToByteArray()))
                }
                assertEquals(field, ArchiveRejection.INVALID_VALUE, error.rejection)
            }
        }
        val numericGoal = valid.replace("\"goals\":[\"STRENGTH\"]", "\"goals\":[123]")
        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(ByteArrayInputStream(numericGoal.encodeToByteArray()))
        }
        assertEquals(ArchiveRejection.INVALID_VALUE, error.rejection)
    }

    private fun completedArchive(count: Int): LocalDataArchive {
        val base = programArchive()
        val template = base.snapshot.programAssociations.single()
        val rows = (0 until count).map { ordinal ->
            val position = ProgramPosition(ordinal, ordinal / 4 + 1, ordinal / 2 + 1, ordinal % 2 + 1)
            template.copy(sessionId = "program-$ordinal", snapshot = template.snapshot.copy(enrollmentRevision = 1L + 2 * ordinal,
                position = position, objective = if (ordinal < 4) ProgramObjective.ESTABLISH_ROUTINE else ProgramObjective.CONTINUE_AND_RECORD))
        }
        return base.copy(snapshot = rebind(base.snapshot.copy(
            programEnrollment = base.snapshot.programEnrollment!!.copy(revision = 1L + 2 * count,
                status = if (count == 8) ProgramStatus.COMPLETED else ProgramStatus.ACTIVE),
            programAssociations = rows,
            sessions = rows.map { base.snapshot.sessions.single().copy(id = it.sessionId, exercises = emptyList(),
                status = SessionStatus.COMPLETED, completedAtTimestamp = 1_700_500_600_000L) })))
    }

    private fun rebind(snapshot: LocalDataSnapshot): LocalDataSnapshot = snapshot.copy(
        recommendationRecords = snapshot.programAssociations.map { association ->
            LocalDataArchiveFixtures.recommendationRecords().first().copy(sessionId = association.sessionId,
                programAssociationDigest = MultiWeekProgramPolicy.associationDigest(association))
        })

    private fun refusesBeforeOpen(archive: LocalDataArchive): LocalDataArchiveException {
        var opened = false
        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.write(archive) { opened = true; ByteArrayOutputStream() }
        }
        assertFalse(opened)
        return error
    }

    private fun programArchive(): LocalDataArchive {
        // Format 5 is the one that introduced program membership; later formats carry it too.
        val base = LocalDataArchiveFixtures.archive(LocalDataArchiveFixtures.metadata(archiveVersion = 5))
        val configuration = ProgramConfiguration(FitnessGoal.STRENGTH, listOf(FitnessGoal.STRENGTH), 2)
        val enrollment = ProgramEnrollment(base.snapshot.profile!!.id, "11111111-1111-1111-1111-111111111111", 2,
            configuration, ProgramStatus.ACTIVE, 1000, 1000)
        val association = ProgramAssociation("session-4", enrollment.profileId, ProgramSessionSnapshot(
            enrollment.programId, 1, configuration, ProgramPosition(0, 1, 1, 1), ProgramObjective.ESTABLISH_ROUTINE))
        return base.copy(snapshot = base.snapshot.copy(sessions = base.snapshot.sessions.filter { it.id == association.sessionId },
            recommendationRecords = listOf(base.snapshot.recommendationRecords.first().copy(sessionId = association.sessionId,
                programAssociationDigest = MultiWeekProgramPolicy.associationDigest(association))),
            programEnrollment = enrollment, programAssociations = listOf(association)))
    }

    @Test fun originalCanonicalChecksumsArePinned() {
        // Format 5 is pinned to the checksum main's codec produced while 5 was current
        // (08f027026), so the template-unit format 6 provably leaves it byte for byte.
        val checksums = (1..5).map { version ->
            val base = LocalDataArchiveFixtures.archive()
            val archive = base.copy(metadata = base.metadata.copy(archiveVersion = version),
                snapshot = base.snapshot.copy(recommendationRecords =
                    if (version == 1) emptyList() else base.snapshot.recommendationRecords))
            val encoded = bytes(archive)
            val restored = LocalDataArchiveCodec.read(ByteArrayInputStream(encoded))
            assertNull(restored.snapshot.programEnrollment)
            assertTrue(restored.snapshot.programAssociations.isEmpty())
            assertArrayEquals(encoded, bytes(restored))
            Regex("\\\"value\\\":\\\"([a-f0-9]{64})\\\"").find(encoded.decodeToString())!!.groupValues[1]
        }
        assertEquals(listOf("63d059a6e3c1253121dec66f923977cdfd5fb279efa19056a3cd51c3f0649358", "4449245d8d02c7f51bdc1ae6b5ecbf0f70c9b7dd0b77ee3a007f33d138d66bf0", "1a3a5a3e72850887fde7b18e4bb836d5ade84aa73465c50f13e6ddc453c7778d", "5fbbf382e960640eefbe8eb25b88ad7c31851c2e55413fe35af6908f2ee8f05e", "d3548046604c87939b2e6c088c25676cdf0f3ff0606b2c06a927fc1e491699b6"), checksums)
    }

    private fun bytes(archive: LocalDataArchive): ByteArray =
        ByteArrayOutputStream().also { sink -> LocalDataArchiveCodec.write(archive) { sink } }.toByteArray()
}
