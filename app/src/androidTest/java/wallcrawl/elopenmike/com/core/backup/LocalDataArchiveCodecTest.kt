package wallcrawl.elopenmike.com.core.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetStopReason
import wallcrawl.elopenmike.com.core.model.WeightUnit

/**
 * Trust-boundary coverage for the archive document.
 *
 * The reader is the only thing standing between an untrusted file and a database
 * transaction, so every rejection here is a rejection that happens before any write.
 */
@RunWith(AndroidJUnit4::class)
class LocalDataArchiveCodecTest {

    @Test
    fun roundTrip_preservesEveryUserOwnedValue() {
        val original = LocalDataArchiveFixtures.archive()

        val restored = LocalDataArchiveCodec.read(ByteArrayInputStream(original.toBytes()))

        assertThat(restored).isEqualTo(original)
    }

    @Test
    fun roundTrip_keepsMixedUnitsPlannedValuesAndNullFeedback() {
        val restored = LocalDataArchiveCodec.read(
            ByteArrayInputStream(LocalDataArchiveFixtures.archive().toBytes())
        )

        val units = restored.snapshot.sessions.associate { it.id to it.weightUnit }
        assertThat(units["session-1"]).isEqualTo(WeightUnit.LBS)
        assertThat(units["session-2"]).isEqualTo(WeightUnit.KG)

        val benchSets = restored.snapshot.sessions
            .single { it.id == "session-1" }
            .exercises.single()
            .sets
        // Planned and performed values stay separate.
        assertThat(benchSets[0].targetReps).isEqualTo(8)
        assertThat(benchSets[0].completedReps).isEqualTo(8)
        assertThat(benchSets[0].rpe).isEqualTo(8.5f)
        assertThat(benchSets[0].feltManageable).isTrue()
        // History from before typed outcomes keeps an unknown completion time.
        assertThat(benchSets[1].isCompleted).isTrue()
        assertThat(benchSets[1].completedAtTimestamp).isNull()
        assertThat(benchSets[1].rpe).isNull()
        assertThat(benchSets[1].feltManageable).isNull()
        // Stopped work keeps its typed reason and its own timestamp.
        assertThat(benchSets[2].isCompleted).isFalse()
        assertThat(benchSets[2].stopReason).isEqualTo(SetStopReason.TIME_CONSTRAINT)
        assertThat(benchSets[2].stoppedAtTimestamp).isEqualTo(1_700_202_000_000L)

        val neverRecorded = restored.snapshot.sessions
            .single { it.id == "session-2" }
            .exercises.single { it.prescription.exerciseType == ExerciseType.DURATION }
            .sets.single()
        assertThat(neverRecorded.isCompleted).isFalse()
        assertThat(neverRecorded.stopReason).isNull()
        assertThat(neverRecorded.completedDurationSeconds).isNull()

        assertThat(restored.snapshot.sessions.count { it.status == SessionStatus.IN_PROGRESS })
            .isEqualTo(1)
        assertThat(restored.snapshot.sessions.count { it.status == SessionStatus.CANCELLED })
            .isEqualTo(1)
    }

    @Test
    fun roundTrip_keepsGoalOrderThatDecidesThePrimaryGoal() {
        val restored = LocalDataArchiveCodec.read(
            ByteArrayInputStream(LocalDataArchiveFixtures.archive().toBytes())
        )

        val profile = requireNotNull(restored.snapshot.profile)
        assertThat(profile.goals.toList())
            .isEqualTo(LocalDataArchiveFixtures.profile().goals.toList())
        assertThat(profile.primaryGoal).isEqualTo(LocalDataArchiveFixtures.profile().primaryGoal)
    }

    @Test
    fun read_rejectsEditedContentThroughTheChecksum() {
        val document = LocalDataArchiveFixtures.archive().toText()
        val edited = document.replace("\"completedReps\":8", "\"completedReps\":88")
        assertThat(edited).isNotEqualTo(document)

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(edited.byteInputStream())
        }
        assertThat(error.rejection).isEqualTo(ArchiveRejection.CHECKSUM_MISMATCH)
        assertThat(error).hasMessageThat().contains("checksum")
    }

    @Test
    fun read_rejectsAFutureArchiveVersion() {
        val document = LocalDataArchiveFixtures.archive().toText()
            .replace("\"archiveVersion\":2", "\"archiveVersion\":3")

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(document.byteInputStream())
        }
        assertThat(error.rejection).isEqualTo(ArchiveRejection.UNSUPPORTED_VERSION)
        assertThat(error).hasMessageThat().contains("not supported")
    }

    @Test
    fun read_rejectsAnUnknownField() {
        val document = LocalDataArchiveFixtures.archive().toText()
            .replace("\"templates\":", "\"futureThing\":true,\"templates\":")

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(document.byteInputStream())
        }
        assertThat(error.rejection).isEqualTo(ArchiveRejection.MALFORMED)
        assertThat(error).hasMessageThat().contains("unsupported field")
    }

    @Test
    fun read_rejectsAnUnknownEnumValue() {
        val document = LocalDataArchiveFixtures.archive().toText()
            .replace("\"status\":\"CANCELLED\"", "\"status\":\"ABANDONED\"")

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(document.byteInputStream())
        }
        assertThat(error.rejection).isEqualTo(ArchiveRejection.INVALID_VALUE)
        assertThat(error).hasMessageThat().contains("not a value this build supports")
    }

    @Test
    fun read_rejectsADuplicateSessionIdentifierInACoherentDocument() {
        val snapshot = LocalDataArchiveFixtures.snapshot(
            sessions = LocalDataArchiveFixtures.sessions().let { sessions ->
                listOf(sessions[0], sessions[0])
            }
        )

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.write(
                LocalDataArchive(LocalDataArchiveFixtures.metadata(), snapshot)
            ) { ByteArrayOutputStream() }
        }
        assertThat(error.rejection).isEqualTo(ArchiveRejection.INCONSISTENT)
        assertThat(error).hasMessageThat().contains("reuses a session identifier")
    }

    @Test
    fun read_rejectsMoreThanOneActiveWorkout() {
        val active = LocalDataArchiveFixtures.sessions()
            .single { it.status == SessionStatus.IN_PROGRESS }
        val second = active.copy(
            id = "session-5",
            exercises = active.exercises.map { exercise ->
                exercise.copy(
                    id = "session-5-exercise-1",
                    sessionId = "session-5",
                    sets = exercise.sets.mapIndexed { index, set ->
                        set.copy(
                            id = "session-5-set-$index",
                            workoutExerciseId = "session-5-exercise-1"
                        )
                    }
                )
            }
        )

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.write(
                LocalDataArchive(
                    LocalDataArchiveFixtures.metadata(),
                    LocalDataArchiveFixtures.snapshot(sessions = listOf(active, second))
                )
            ) { ByteArrayOutputStream() }
        }
        assertThat(error.rejection).isEqualTo(ArchiveRejection.INCONSISTENT)
        assertThat(error).hasMessageThat().contains("only one workout may be active")
    }

    @Test
    fun read_rejectsASetThatReferencesAnotherExercise() {
        val document = LocalDataArchiveFixtures.archive().toText()
            .replace("\"workoutExerciseId\":\"session-3-exercise-1\"", "\"workoutExerciseId\":\"session-1-exercise-1\"")

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(document.byteInputStream())
        }
        assertThat(error.rejection).isEqualTo(ArchiveRejection.INCONSISTENT)
        assertThat(error).hasMessageThat().contains("references a different exercise")
    }

    @Test
    fun read_rejectsAValueTheExerciseTypeCannotCarry() {
        val document = LocalDataArchiveFixtures.archive().toText()
            .replace(
                "\"id\":\"session-4-set-1\",\"workoutExerciseId\":\"session-4-exercise-1\",\"setNumber\":1,\"exerciseType\":\"BODYWEIGHT_REPS\",",
                "\"id\":\"session-4-set-1\",\"workoutExerciseId\":\"session-4-exercise-1\",\"setNumber\":1,\"exerciseType\":\"BODYWEIGHT_REPS\",\"completedWeight\":40.0,"
            )

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(document.byteInputStream())
        }
        assertThat(error.rejection).isEqualTo(ArchiveRejection.INCONSISTENT)
        assertThat(error).hasMessageThat().contains("its exercise type cannot carry")
    }

    @Test
    fun read_rejectsAnOutOfRangeEffortValue() {
        val document = LocalDataArchiveFixtures.archive().toText()
            .replace("\"rpe\":8.5", "\"rpe\":42.0")

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(document.byteInputStream())
        }
        assertThat(error.rejection).isEqualTo(ArchiveRejection.INVALID_VALUE)
        assertThat(error).hasMessageThat().contains("outside the supported range")
    }

    @Test
    fun read_rejectsADocumentLargerThanTheInputLimit() {
        val padding = "x".repeat(1_024)
        val oversized = buildString {
            append("{\"wallcrawlArchive\":{\"archiveVersion\":1,\"appVersionName\":\"")
            repeat((LocalDataArchiveLimits.MAX_CHARACTERS / padding.length).toInt() + 128) {
                append(padding)
            }
            append("\"}}")
        }

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(oversized.byteInputStream())
        }
        assertThat(error.rejection).isEqualTo(ArchiveRejection.TOO_LARGE)
        assertThat(error).hasMessageThat().contains("character limit")
    }

    @Test
    fun read_rejectsAnEmptyOrTruncatedDocument() {
        assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read("".byteInputStream())
        }

        val truncated = LocalDataArchiveFixtures.archive().toText().dropLast(200)
        assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(truncated.byteInputStream())
        }
    }

    @Test
    fun read_rejectsAProfileThisAppCouldNeverLoad() {
        val document = LocalDataArchiveFixtures.archive(
            snapshot = LocalDataArchiveFixtures.snapshot(
                profile = LocalDataArchiveFixtures.profile().copy(id = "someone_else")
            )
        )

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.write(document) { ByteArrayOutputStream() }
        }
        assertThat(error.rejection).isEqualTo(ArchiveRejection.INCONSISTENT)
        assertThat(error).hasMessageThat().contains("identifier this app reads")
    }

    @Test
    fun read_acceptsAnArchiveWrittenBeforeOnboardingFinished() {
        val archive = LocalDataArchiveFixtures.archive(
            snapshot = LocalDataArchiveFixtures.snapshot(
                profile = LocalDataArchiveFixtures.profile().copy(onboardingCompleted = false),
                templates = emptyList(),
                sessions = emptyList()
            )
        )

        val restored = LocalDataArchiveCodec.read(ByteArrayInputStream(archive.toBytes()))

        assertThat(restored.snapshot.profile?.onboardingCompleted).isFalse()
        assertThat(restored.snapshot.sessions).isEmpty()
    }

    @Test
    fun read_rejectsANonIntegralNumberWithoutEchoingIt() {
        val document = LocalDataArchiveFixtures.archive().toText()
            .replace("\"targetSets\":3", "\"targetSets\":3.5")

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(document.byteInputStream())
        }
        assertThat(error.rejection).isEqualTo(ArchiveRejection.INVALID_VALUE)
        assertThat(error).hasMessageThat().contains("targetSets")
        // The platform reader quotes the offending literal; this codec must not pass it on.
        assertThat(error).hasMessageThat().doesNotContain("3.5")
    }

    @Test
    fun read_rejectsAMovementThisBuildDoesNotKnow() {
        val document = LocalDataArchiveFixtures.archive().toText()
            .replace("\"IMPACT\":", "\"TELEPORTATION\":")

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(document.byteInputStream())
        }
        // Reported for what it is, not as a checksum failure caused by silently dropping it.
        assertThat(error.rejection).isEqualTo(ArchiveRejection.INVALID_VALUE)
        assertThat(error).hasMessageThat().contains("movementCapabilities")
    }

    @Test
    fun write_rejectsARepeatedGoalThatWouldCollapseIntoASet() {
        // A duplicate cannot be built through the Set-typed model, so it is introduced in the
        // document itself, which is exactly where an edited archive would carry one.
        val document = LocalDataArchiveFixtures.archive().toText()
            .replace("\"goals\":[\"STRENGTH\",\"BUILD_MUSCLE\"]", "\"goals\":[\"STRENGTH\",\"STRENGTH\"]")

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(document.byteInputStream())
        }
        assertThat(error.rejection).isEqualTo(ArchiveRejection.INCONSISTENT)
        assertThat(error).hasMessageThat().contains("profile.goals")
    }

    @Test
    fun write_rejectsAValueCarryingThePersistenceListSeparator() {
        val archive = LocalDataArchiveFixtures.archive(
            snapshot = LocalDataArchiveFixtures.snapshot(
                profile = LocalDataArchiveFixtures.profile().copy(
                    availableEquipment = listOf("Bodyweight|||Barbell")
                )
            )
        )

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.write(archive) { ByteArrayOutputStream() }
        }
        assertThat(error.rejection).isEqualTo(ArchiveRejection.INVALID_VALUE)
        assertThat(error).hasMessageThat().contains("availableEquipment")
    }

    @Test
    fun write_rejectsAConfirmedLoadKeyCarryingThePairSeparator() {
        val archive = LocalDataArchiveFixtures.archive(
            snapshot = LocalDataArchiveFixtures.snapshot(
                profile = LocalDataArchiveFixtures.profile().copy(
                    confirmedStartingLoads = mapOf("bench:press" to 60.0)
                )
            )
        )

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.write(archive) { ByteArrayOutputStream() }
        }
        assertThat(error.rejection).isEqualTo(ArchiveRejection.INVALID_VALUE)
        assertThat(error).hasMessageThat().contains("confirmedStartingLoads")
    }

    @Test
    fun write_refusesAnExportThatWouldExceedTheReadersSizeLimit() {
        // The export path has to apply the reader's ceiling too, or it would hand the user a
        // file that only fails when they try to restore it.
        val padding = "n".repeat(10_000)
        val bulky = LocalDataArchiveFixtures.sessions().first()
        val sessions = (0 until 1_800).map { index ->
            bulky.copy(
                id = "bulk-session-$index",
                notes = padding,
                exercises = emptyList()
            )
        }

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.write(
                LocalDataArchiveFixtures.archive(
                    snapshot = LocalDataArchiveFixtures.snapshot(sessions = sessions)
                )
            ) { ByteArrayOutputStream() }
        }
        assertThat(error.rejection).isEqualTo(ArchiveRejection.TOO_LARGE)
    }

    @Test
    fun write_rejectsAContradictorySetOutcomeOnTheExportSideToo() {
        val session = LocalDataArchiveFixtures.sessions().first()
        val exercise = session.exercises.single()
        val contradictory = session.copy(
            exercises = listOf(
                exercise.copy(
                    sets = listOf(
                        // Never completed, yet carrying completion-only feedback.
                        exercise.sets.first().copy(
                            isCompleted = false,
                            completedAtTimestamp = null,
                            feltManageable = true
                        )
                    )
                )
            )
        )

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.write(
                LocalDataArchiveFixtures.archive(
                    snapshot = LocalDataArchiveFixtures.snapshot(sessions = listOf(contradictory))
                )
            ) { ByteArrayOutputStream() }
        }
        assertThat(error.rejection).isEqualTo(ArchiveRejection.INVALID_VALUE)
        assertThat(error).hasMessageThat().contains("feltManageable")
    }

    @Test
    fun write_rejectsABlankEntryThePersistenceLayerWouldDrop() {
        val archive = LocalDataArchiveFixtures.archive(
            snapshot = LocalDataArchiveFixtures.snapshot(
                profile = LocalDataArchiveFixtures.profile().copy(
                    availableEquipment = listOf("Bodyweight", "  ")
                )
            )
        )

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.write(archive) { ByteArrayOutputStream() }
        }
        assertThat(error.rejection).isEqualTo(ArchiveRejection.INVALID_VALUE)
        assertThat(error).hasMessageThat().contains("blank entry")
    }

    @Test
    fun write_rejectsAProfileWithNoGoalBecauseRestoreWouldInventOne() {
        val archive = LocalDataArchiveFixtures.archive(
            snapshot = LocalDataArchiveFixtures.snapshot(
                profile = LocalDataArchiveFixtures.profile().copy(goals = emptySet())
            )
        )

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.write(archive) { ByteArrayOutputStream() }
        }
        assertThat(error.rejection).isEqualTo(ArchiveRejection.INCONSISTENT)
        assertThat(error).hasMessageThat().contains("no fitness goal")
    }

    @Test
    fun read_rejectsABlankMapKeyThatWouldNotSurviveTheRoundTrip() {
        val document = LocalDataArchiveFixtures.archive().toText()
            .replace("\"Chest\":\"HIGH\"", "\" \":\"HIGH\"")

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(document.byteInputStream())
        }
        assertThat(error.rejection).isEqualTo(ArchiveRejection.INVALID_VALUE)
    }

    @Test
    fun write_rejectsAMuscleGroupTheVocabularyWouldRewrite() {
        // "Legs" expands to three canonical groups when the stored column is read back, so
        // an archive holding it would restore as priorities it never declared.
        val archive = LocalDataArchiveFixtures.archive(
            snapshot = LocalDataArchiveFixtures.snapshot(
                profile = LocalDataArchiveFixtures.profile().copy(
                    musclePriorities = mapOf("Legs" to wallcrawl.elopenmike.com.core.model.PriorityLevel.HIGH)
                )
            )
        )

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.write(archive) { ByteArrayOutputStream() }
        }
        assertThat(error.rejection).isEqualTo(ArchiveRejection.INVALID_VALUE)
        assertThat(error).hasMessageThat().contains("musclePriorities")
    }

    @Test
    fun write_rejectsMetadataTheReaderWouldRefuse() {
        // A device clock outside the supported range would otherwise produce an archive that
        // reports success and is then refused at restore.
        val archive = LocalDataArchiveFixtures.archive(
            metadata = LocalDataArchiveFixtures.metadata().copy(
                createdAtEpochMillis = LocalDataArchiveLimits.MAX_TIMESTAMP_MILLIS + 1
            )
        )

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.write(archive) { ByteArrayOutputStream() }
        }
        assertThat(error.rejection).isEqualTo(ArchiveRejection.INVALID_VALUE)
        assertThat(error).hasMessageThat().contains("createdAtEpochMillis")
    }

    @Test
    fun read_letsATransportFailurePropagateInsteadOfBlamingTheDocument() {
        // The document is intact; the stream carrying it fails part way through, which is
        // what a cloud-backed provider dropping a connection looks like. Reporting that as
        // "not a WallCrawl file" would invite deleting a perfectly good archive.
        val document = LocalDataArchiveFixtures.archive().toBytes()
        val interrupted = object : InputStream() {
            private var delivered = 0

            override fun read(): Int {
                if (delivered >= INTERRUPT_AFTER_BYTES) throw IOException("connection dropped")
                return document[delivered++].toInt() and 0xff
            }
        }

        // LocalDataArchiveException is an IllegalArgumentException, so this assertion fails
        // if the codec turns the transport failure into a rejection.
        assertThrows(IOException::class.java) { LocalDataArchiveCodec.read(interrupted) }
    }

    @Test
    fun write_neverOpensTheDestinationForAnArchiveItRefuses() {
        // Opening a document for writing truncates it, and the destination may be the
        // user's previous export. A refusal that can never succeed must not destroy it.
        var opened = false
        val archive = LocalDataArchiveFixtures.archive(
            snapshot = LocalDataArchiveFixtures.snapshot(
                profile = LocalDataArchiveFixtures.profile().copy(goals = emptySet())
            )
        )

        assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.write(archive) {
                opened = true
                ByteArrayOutputStream()
            }
        }
        assertThat(opened).isFalse()
    }

    @Test
    fun read_rejectsAnArchiveThatDoesNotAnswerEveryMovement() {
        // MovementCapabilities fills anything absent with UNKNOWN. A restore must not do
        // that: an omitted answer would replace a recorded "avoid" with "not sure".
        val document = LocalDataArchiveFixtures.archive().toText()
            .replace(",\"CONTINUOUS_ACTIVITY\":\"UNKNOWN\"", "")

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.read(document.byteInputStream())
        }
        assertThat(error.rejection).isEqualTo(ArchiveRejection.INVALID_VALUE)
        assertThat(error).hasMessageThat().contains("movementCapabilities")
    }

    @Test
    fun write_rejectsABlankIdentifierNothingCouldAddress() {
        // Sessions, exercises and sets have no invariants of their own, so a blank id would
        // restore a row that logging, cancelling and navigation all refuse to work with.
        val session = LocalDataArchiveFixtures.sessions().first()
        val exercise = session.exercises.single()
        val blankSetId = session.copy(
            exercises = listOf(
                exercise.copy(sets = listOf(exercise.sets.first().copy(id = "  ")))
            )
        )

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.write(
                LocalDataArchiveFixtures.archive(
                    snapshot = LocalDataArchiveFixtures.snapshot(sessions = listOf(blankSetId))
                )
            ) { ByteArrayOutputStream() }
        }
        assertThat(error.rejection).isEqualTo(ArchiveRejection.INVALID_VALUE)
        assertThat(error).hasMessageThat().contains("set.id")
    }

    @Test
    fun write_rejectsHistoryWithoutTheProfileThatOwnsIt() {
        // The app only ever writes a profile-less archive when it has nothing else to write.
        // Restoring history with no owning profile would leave onboarding restarting while a
        // full history already blocked the next restore — a state the app cannot produce.
        val archive = LocalDataArchiveFixtures.archive(
            snapshot = LocalDataArchiveFixtures.snapshot(profile = null)
        )

        val error = assertThrows(LocalDataArchiveException::class.java) {
            LocalDataArchiveCodec.write(archive) { ByteArrayOutputStream() }
        }
        assertThat(error.rejection).isEqualTo(ArchiveRejection.INCONSISTENT)
        assertThat(error).hasMessageThat().contains("no profile")
    }

    private fun LocalDataArchive.toBytes(): ByteArray =
        ByteArrayOutputStream().also { sink -> LocalDataArchiveCodec.write(this) { sink } }
            .toByteArray()

    private fun LocalDataArchive.toText(): String = String(toBytes(), Charsets.UTF_8)

    private companion object {
        const val INTERRUPT_AFTER_BYTES = 64
    }
}
