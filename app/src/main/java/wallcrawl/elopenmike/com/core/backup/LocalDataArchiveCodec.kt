package wallcrawl.elopenmike.com.core.backup

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import android.util.MalformedJsonException
import java.io.EOFException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.security.DigestOutputStream
import java.security.MessageDigest
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveFormat.ARCHIVE_VERSION
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveFormat.SUPPORTED_ARCHIVE_VERSIONS
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveFormat.CHECKSUM_ALGORITHM
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveLimits.MAX_CHARACTERS
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveLimits.MAX_COLLECTION_ITEMS
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveLimits.MAX_DISTANCE_METERS
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveLimits.MAX_DURATION_SECONDS
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveLimits.MAX_EXERCISES_PER_SESSION
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveLimits.MAX_ID_LENGTH
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveLimits.MAX_NAME_LENGTH
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveLimits.MAX_NOTES_LENGTH
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveLimits.MAX_RECOMMENDATION_RECORDS
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveLimits.MAX_REPETITIONS
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveLimits.MAX_SESSIONS
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveLimits.MAX_SETS_PER_EXERCISE
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveLimits.MAX_SHORT_TEXT_LENGTH
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveLimits.MAX_TEMPLATES
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveLimits.MAX_TIMESTAMP_MILLIS
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveLimits.MAX_TOTAL_SETS
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveLimits.MAX_WEIGHT
import wallcrawl.elopenmike.com.core.database.PERSISTED_LIST_SEPARATOR
import wallcrawl.elopenmike.com.core.database.PERSISTED_PAIR_SEPARATOR
import wallcrawl.elopenmike.com.core.database.repository.RecommendationDoseAccountingPayload
import wallcrawl.elopenmike.com.core.io.BoundedCharacterReader
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.EffortTarget
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.MuscleDoseAccounting
import wallcrawl.elopenmike.com.core.model.MuscleVocabulary
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.RecommendationRecord
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.RestClass
import wallcrawl.elopenmike.com.core.model.RestTargetSource
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetOutcomeRules
import wallcrawl.elopenmike.com.core.model.SetPerformanceInput
import wallcrawl.elopenmike.com.core.model.SetStopReason
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.model.ProfileGender
import wallcrawl.elopenmike.com.core.model.IllustrationPreference
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutOrigin
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate

/**
 * Reads and writes the version 1 archive document.
 *
 * ## Document shape
 *
 * ```json
 * {
 *   "wallcrawlArchive": { "archiveVersion": 2, ... },
 *   "data": {
 *     "profile": {...}, "templates": [...], "sessions": [...],
 *     "recommendations": [...]
 *   },
 *   "checksum": { "algorithm": "SHA-256", "value": "<hex>" }
 * }
 * ```
 *
 * The metadata object must come first so an archive written by a future version is rejected
 * with an "unsupported version" error rather than an incidental "unknown field" one.
 *
 * A value that is absent is written as an absent member rather than an explicit null, which
 * keeps the canonical form the checksum covers unambiguous: exactly one encoding per value.
 *
 * ## Checksum
 *
 * The recorded value is the digest of the archive's **canonical serialization**: the
 * metadata and data written by [writeCanonicalBody], which is the same code path that
 * produces the document itself. A reader parses the document, re-serializes what it
 * understood, and compares. Any corruption or edit that changes a decoded value therefore
 * changes the digest.
 *
 * This detects damage and accidental modification. It is not a signature: anyone who edits
 * an archive can recompute the digest, so a valid checksum says nothing about who wrote the
 * document. Every archive is treated as untrusted regardless.
 *
 * ## Trust boundary
 *
 * [read] performs every check an import needs before any database work begins: bounded
 * input, exactly the known fields with no duplicates, strict enum names, numeric and length
 * bounds, the domain invariants already enforced by [ExercisePrescription], [RepRange],
 * [WorkoutTemplate] and [SetOutcomeRules], unique identifiers, parent/child agreement, and
 * the one-active-session rule. It never repairs a document and never substitutes a default
 * for a value the archive got wrong.
 */
object LocalDataArchiveCodec {

    /**
     * Writes [archive] as a complete document to the stream [openOutput] supplies.
     *
     * The stream is opened only after every check that could refuse this archive has
     * passed, and it is closed here: an export is not successful until the document has
     * been flushed and closed without error.
     */
    fun write(archive: LocalDataArchive, openOutput: () -> OutputStream) {
        // The codec writes any format it can read, which is what makes the older format's
        // round trip testable at all. Production always exports [ARCHIVE_VERSION]: the
        // repository names the version, not this function.
        require(archive.metadata.archiveVersion in SUPPORTED_ARCHIVE_VERSIONS) {
            "Only archive versions $SUPPORTED_ARCHIVE_VERSIONS can be written."
        }
        require(archive.metadata.archiveVersion >= 3 || archive.snapshot.profile.let {
            it == null || (it.gender == ProfileGender.UNSPECIFIED &&
                it.illustrationPreference == IllustrationPreference.AUTOMATIC)
        }) { "Gender and illustration preferences require archive version 3." }
        require(
            archive.metadata.archiveVersion >= RECOMMENDATION_RECORDS_ARCHIVE_VERSION ||
                archive.snapshot.recommendationRecords.isEmpty()
        ) {
            "Recommendation records need archive version " +
                "$RECOMMENDATION_RECORDS_ARCHIVE_VERSION or newer."
        }
        // Everything that can refuse this export runs before [openOutput] is called, and
        // nothing here touches the destination. That matters because opening a document for
        // writing truncates it: the user may have picked their previous export, and a
        // refusal that can never succeed must not destroy the archive they already had.
        validateMetadata(archive.metadata)
        validateSnapshot(archive.snapshot)

        // The archive is serialised twice: once here into the digest, and once below into
        // the document. That is deliberate. Computing the checksum first is what lets an
        // export that breaks the contract, or exceeds the size ceiling, fail before the
        // destination is even opened. Teeing one pass into both would move that check to the
        // middle of the write, and buffering the whole document to keep one pass would hold
        // up to the entire character ceiling in memory on a phone. The extra pass is CPU
        // over an already in-memory snapshot.
        val checksum = canonicalChecksum(archive)

        openOutput().use { output ->
            val writer = JsonWriter(OutputStreamWriter(output, Charsets.UTF_8))
            writer.beginObject()
            writer.name(FIELD_METADATA)
            writer.writeMetadata(archive.metadata)
            writer.name(FIELD_DATA)
            writer.writeSnapshot(archive.snapshot, archive.metadata.archiveVersion)
            writer.name(FIELD_CHECKSUM)
            writer.beginObject()
            writer.name("algorithm").value(CHECKSUM_ALGORITHM)
            writer.name("value").value(checksum)
            writer.endObject()
            writer.endObject()
            // Flushed inside use{}, so the bytes reach the stream before it is closed and a
            // provider that fails on close still fails the export.
            writer.flush()
        }
    }

    /**
     * Parses and fully validates an archive from [input].
     *
     * @throws LocalDataArchiveException when the document is malformed, unsupported,
     *   corrupt, oversized, or violates a domain invariant. Nothing is mutated either way.
     * @throws java.io.IOException when reading [input] itself fails. That is a transport
     *   problem rather than a defect in the document, and it is deliberately not turned into
     *   a rejection.
     */
    fun read(input: InputStream): LocalDataArchive {
        val reader = JsonReader(
            BoundedCharacterReader(
                InputStreamReader(input, Charsets.UTF_8),
                MAX_CHARACTERS
            ) { limit -> malformed("The archive exceeds the $limit-character limit.", ArchiveRejection.TOO_LARGE) }
        )
        var metadata: LocalDataArchiveMetadata? = null
        var snapshot: LocalDataSnapshot? = null
        var checksum: String? = null

        try {
            reader.beginObject()
            val seen = mutableSetOf<String>()
            while (reader.hasNext()) {
                val field = reader.nextName()
                if (!seen.add(field)) malformed("The archive has a duplicate top-level field.")
                when (field) {
                    FIELD_METADATA -> metadata = reader.readMetadata()
                    FIELD_DATA -> {
                        if (metadata == null) {
                            malformed("The archive must declare '$FIELD_METADATA' first.")
                        }
                        snapshot = reader.readSnapshot(metadata.archiveVersion)
                    }

                    FIELD_CHECKSUM -> checksum = reader.readChecksum()
                    else -> malformed("The archive has an unsupported top-level field.")
                }
            }
            reader.endObject()
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                malformed("The archive has content after its root object.")
            }
        } catch (error: LocalDataArchiveException) {
            throw error
        } catch (error: IllegalArgumentException) {
            // Domain constructors (prescriptions, rep ranges, templates) reject invalid
            // values with their own messages; they are already field-specific and carry no
            // archived content.
            malformed(
                error.message ?: "The archive contains an invalid value.",
                ArchiveRejection.INVALID_VALUE
            )
        } catch (error: IllegalStateException) {
            malformed("The archive is not valid JSON.")
        } catch (error: MalformedJsonException) {
            malformed("The archive is not valid JSON.")
        } catch (error: EOFException) {
            malformed("The archive ended before the document was complete.")
        }
        // Any other IOException is the transport failing, not the document being wrong: the
        // chosen document may live on a provider that drops a connection mid-read. It
        // propagates unchanged so the caller reports a retryable failure instead of telling
        // the user their intact archive is not a WallCrawl file.

        val readMetadata = metadata ?: malformed("The archive is missing '$FIELD_METADATA'.")
        val readSnapshot = snapshot ?: malformed("The archive is missing '$FIELD_DATA'.")
        val readChecksum = checksum ?: malformed("The archive is missing '$FIELD_CHECKSUM'.")

        // Whole-document rules run before the checksum comparison so a document that is
        // internally inconsistent is reported for the rule it actually broke, rather than as
        // a generic "damaged file". Both checks precede every database statement, so either
        // one refuses the import with nothing written.
        validateMetadata(readMetadata)
        validateSnapshot(readSnapshot)

        val archive = LocalDataArchive(readMetadata, readSnapshot)
        if (!canonicalChecksum(archive).equals(readChecksum, ignoreCase = true)) {
            malformed(
                "The archive checksum does not match its contents; it is damaged or was edited.",
                ArchiveRejection.CHECKSUM_MISMATCH
            )
        }
        return archive
    }

    /**
     * The digest of the archive's canonical serialization.
     *
     * Exported and imported archives run through the identical writer, so an unmodified
     * document always reproduces the value it carries.
     */
    private fun canonicalChecksum(archive: LocalDataArchive): String {
        val digest = MessageDigest.getInstance(CHECKSUM_ALGORITHM)
        val sink = CountingSink()
        // OutputStreamWriter keeps the UTF-8 encoder's state across writes, so a character
        // that spans two writes still digests as the bytes it would occupy in the document.
        val writer = JsonWriter(
            OutputStreamWriter(DigestOutputStream(sink, digest), Charsets.UTF_8)
        )
        writer.writeCanonicalBody(archive)
        writer.flush()
        // This pass runs on both sides, and on export it runs before a byte reaches the
        // document, so the size ceiling the reader enforces is applied to an export too
        // rather than being discovered only at restore time. Counting UTF-8 bytes against a
        // character ceiling is deliberately conservative: it can only refuse earlier than
        // the reader would, never later, so an export that succeeds always reads back.
        if (sink.bytesWritten > MAX_CANONICAL_BYTES) {
            malformed(
                "The archive would exceed the $MAX_CHARACTERS-character limit.",
                ArchiveRejection.TOO_LARGE
            )
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun JsonWriter.writeCanonicalBody(archive: LocalDataArchive) {
        beginObject()
        name(FIELD_METADATA)
        writeMetadata(archive.metadata)
        name(FIELD_DATA)
        writeSnapshot(archive.snapshot, archive.metadata.archiveVersion)
        endObject()
    }

    // ---------------------------------------------------------------- writing

    private fun JsonWriter.writeMetadata(metadata: LocalDataArchiveMetadata) {
        beginObject()
        name("archiveVersion").value(metadata.archiveVersion.toLong())
        name("createdAtEpochMillis").value(metadata.createdAtEpochMillis)
        name("appVersionName").value(metadata.appVersionName)
        name("appVersionCode").value(metadata.appVersionCode)
        name("roomSchemaVersion").value(metadata.roomSchemaVersion.toLong())
        metadata.catalogCommit?.let { commit -> name("catalogCommit").value(commit) }
        endObject()
    }

    private fun JsonWriter.writeSnapshot(snapshot: LocalDataSnapshot, archiveVersion: Int) {
        beginObject()
        snapshot.profile?.let { profile ->
            name("profile")
            writeProfile(profile, archiveVersion)
        }
        name("templates")
        beginArray()
        snapshot.templates.forEach { template -> writeTemplate(template) }
        endArray()
        name("sessions")
        beginArray()
        snapshot.sessions.forEach { session -> writeSession(session) }
        endArray()
        // Omitted entirely when there is none, like every other absent value here, so a
        // version 1 document re-serializes byte for byte and keeps its original checksum.
        if (snapshot.recommendationRecords.isNotEmpty()) {
            name("recommendations")
            beginArray()
            snapshot.recommendationRecords.forEach { record -> writeRecommendation(record) }
            endArray()
        }
        endObject()
    }

    private fun JsonWriter.writeRecommendation(record: RecommendationRecord) {
        beginObject()
        name("sessionId").value(record.sessionId)
        name("validatorVersion").value(record.validatorVersion)
        name("durationEstimatorVersion").value(record.durationEstimatorVersion)
        name("outcome").value(record.outcome)
        name("reviewedPathEnabled").value(record.reviewedPathEnabled)
        record.catalogVersion?.let { name("catalogVersion").value(it) }
        name("reviewPolicyVersion").value(record.reviewPolicyVersion.toLong())
        record.trainingPolicyVersion?.let { name("trainingPolicyVersion").value(it) }
        record.ledgerPolicyVersion?.let { name("ledgerPolicyVersion").value(it) }
        record.programStatePolicyVersion?.let { name("programStatePolicyVersion").value(it) }
        record.adaptationState?.let { name("adaptationState").value(it) }
        record.weekStartEpochDay?.let { name("weekStartEpochDay").value(it) }
        record.timeZoneId?.let { name("timeZoneId").value(it) }
        name("profileRevision").value(record.profileRevision)
        name("contextIdentity").value(record.contextIdentity)
        name("reasonCodes").writeStringArray(record.reasonCodes)
        // The same versioned payload the row stores, so the archive and the database agree
        // on one encoding and one strict decoder rather than two that can drift.
        name("doseAccounting")
            .value(RecommendationDoseAccountingPayload.encode(record.doseAccounting))
        name("recordedAtEpochMillis").value(record.recordedAtEpochMillis)
        endObject()
    }

    private fun JsonWriter.writeProfile(profile: UserProfile, archiveVersion: Int) {
        beginObject()
        name("id").value(profile.id)
        name("revision").value(profile.revision)
        name("name").value(profile.name)
        // Goal order is meaningful: UserProfile.primaryGoal is the first goal, so this list
        // is written and read in order rather than sorted into a canonical shape.
        name("goals").writeStringArray(profile.goals.map(FitnessGoal::name))
        name("experienceLevel").value(profile.experienceLevel.name)
        name("preferredDurationMinutes").value(profile.preferredDurationMinutes.toLong())
        name("daysPerWeek").value(profile.daysPerWeek.toLong())
        name("availableEquipment").writeStringArray(profile.availableEquipment)
        name("preferredUnit").value(profile.preferredUnit.name)
        name("musclePriorities")
        beginObject()
        // Written in the profile's own order, not sorted. The persistence layer stores these
        // maps as ordered joined columns, and the reader preserves document order, so a
        // restored profile reproduces the exact column the app wrote instead of a reordered
        // one that means the same thing. Order is deterministic either way, so the checksum
        // is unaffected.
        profile.musclePriorities.forEach { (muscle, priority) ->
            name(muscle).value(priority.name)
        }
        endObject()
        name("excludedExerciseIds").writeStringArray(profile.excludedExerciseIds)
        name("onboardingCompleted").value(profile.onboardingCompleted)
        name("trainingConstraints").writeStringArray(
            profile.trainingConstraints.map(TrainingConstraint::name)
        )
        name("returningAfterBreakWeeks").value(profile.returningAfterBreakWeeks.toLong())
        name("confirmedStartingLoads")
        beginObject()
        profile.confirmedStartingLoads.forEach { (exerciseId, load) ->
            name(exerciseId).value(load)
        }
        endObject()
        name("movementCapabilities")
        beginObject()
        // MovementCapabilities always holds every declared type, so entry order is stable.
        MovementCapabilityType.entries.forEach { type ->
            name(type.name).value(profile.movementCapabilities[type].name)
        }
        endObject()
        name("themePreference").value(profile.themePreference.name)
        // Keep the canonical bytes and checksums of versions 1 and 2 unchanged.
        if (archiveVersion >= 3) {
            name("gender").value(profile.gender.name)
            name("illustrationPreference").value(profile.illustrationPreference.name)
        }
        endObject()
    }

    private fun JsonWriter.writeTemplate(template: WorkoutTemplate) {
        beginObject()
        name("id").value(template.id)
        name("name").value(template.name)
        name("notes").value(template.notes)
        name("createdAtTimestamp").value(template.createdAtTimestamp)
        name("updatedAtTimestamp").value(template.updatedAtTimestamp)
        name("exercises")
        beginArray()
        template.exercises.forEach { exercise ->
            beginObject()
            name("exerciseId").value(exercise.exerciseId)
            name("notes").value(exercise.notes)
            name("prescription")
            writePrescription(exercise.prescription)
            endObject()
        }
        endArray()
        endObject()
    }

    private fun JsonWriter.writeSession(session: WorkoutSession) {
        beginObject()
        name("id").value(session.id)
        name("name").value(session.name)
        name("startedAtTimestamp").value(session.startedAtTimestamp)
        session.completedAtTimestamp?.let { name("completedAtTimestamp").value(it) }
        name("targetDurationMinutes").value(session.targetDurationMinutes.toLong())
        name("actualDurationMinutes").value(session.actualDurationMinutes.toLong())
        name("weightUnit").value(session.weightUnit.name)
        name("status").value(session.status.name)
        name("origin").value(session.origin.name)
        session.sourceTemplateId?.let { name("sourceTemplateId").value(it) }
        name("focusMuscles").writeStringArray(session.focusMuscles)
        name("notes").value(session.notes)
        name("exercises")
        beginArray()
        session.exercises.forEach { exercise -> writeSessionExercise(exercise) }
        endArray()
        endObject()
    }

    private fun JsonWriter.writeSessionExercise(exercise: WorkoutExercise) {
        beginObject()
        name("id").value(exercise.id)
        name("sessionId").value(exercise.sessionId)
        name("exerciseId").value(exercise.exerciseId)
        name("orderIndex").value(exercise.orderIndex.toLong())
        name("notes").value(exercise.notes)
        name("prescription")
        writePrescription(exercise.prescription)
        name("sets")
        beginArray()
        exercise.sets.forEach { set -> writeSet(set) }
        endArray()
        endObject()
    }

    private fun JsonWriter.writePrescription(prescription: ExercisePrescription) {
        beginObject()
        name("exerciseType").value(prescription.exerciseType.name)
        name("targetSets").value(prescription.targetSets.toLong())
        prescription.repRange?.let { range ->
            name("targetRepMin").value(range.min.toLong())
            name("targetRepMax").value(range.max.toLong())
        }
        prescription.targetWeight?.let { name("targetWeight").value(it) }
        prescription.targetAssistanceWeight?.let { name("targetAssistanceWeight").value(it) }
        prescription.targetDurationSeconds?.let {
            name("targetDurationSeconds").value(it.toLong())
        }
        prescription.targetDistanceMeters?.let { name("targetDistanceMeters").value(it) }
        name("restSeconds").value(prescription.restSeconds.toLong())
        prescription.effortTarget?.let { effort ->
            name("effortMinRir").value(effort.minRir.toLong())
            name("effortMaxRir").value(effort.maxRir.toLong())
        }
        prescription.restClass?.let { name("restClass").value(it.name) }
        prescription.restTargetSource?.let { name("restTargetSource").value(it.name) }
        endObject()
    }

    private fun JsonWriter.writeSet(set: WorkoutSet) {
        beginObject()
        name("id").value(set.id)
        name("workoutExerciseId").value(set.workoutExerciseId)
        name("setNumber").value(set.setNumber.toLong())
        name("exerciseType").value(set.exerciseType.name)
        set.targetReps?.let { name("targetReps").value(it.toLong()) }
        set.completedReps?.let { name("completedReps").value(it.toLong()) }
        set.targetWeight?.let { name("targetWeight").value(it) }
        set.completedWeight?.let { name("completedWeight").value(it) }
        set.targetAssistanceWeight?.let { name("targetAssistanceWeight").value(it) }
        set.completedAssistanceWeight?.let { name("completedAssistanceWeight").value(it) }
        set.targetDurationSeconds?.let { name("targetDurationSeconds").value(it.toLong()) }
        set.completedDurationSeconds?.let {
            name("completedDurationSeconds").value(it.toLong())
        }
        set.targetDistanceMeters?.let { name("targetDistanceMeters").value(it) }
        set.completedDistanceMeters?.let { name("completedDistanceMeters").value(it) }
        name("isCompleted").value(set.isCompleted)
        // Float is written through Number so the canonical text is the shortest value that
        // round-trips, which keeps the checksum stable across export and re-serialization.
        set.rpe?.let { name("rpe").value(it as Number) }
        set.rir?.let { name("rir").value(it.toLong()) }
        set.feltManageable?.let { name("feltManageable").value(it) }
        set.completedAtTimestamp?.let { name("completedAtTimestamp").value(it) }
        set.stoppedAtTimestamp?.let { name("stoppedAtTimestamp").value(it) }
        set.stopReason?.let { name("stopReason").value(it.name) }
        name("type").value(set.type.name)
        endObject()
    }

    private fun JsonWriter.writeStringArray(values: List<String>) {
        beginArray()
        values.forEach { item -> value(item) }
        endArray()
    }

    // ---------------------------------------------------------------- reading

    private fun JsonReader.readMetadata(): LocalDataArchiveMetadata {
        var archiveVersion: Int? = null
        var createdAt: Long? = null
        var appVersionName: String? = null
        var appVersionCode: Long? = null
        var roomSchemaVersion: Int? = null
        var catalogCommit: String? = null

        readObject("archive metadata") { field ->
            when (field) {
                "archiveVersion" -> {
                    archiveVersion = nextBoundedInt("archiveVersion", 0, 1_000_000)
                    if (archiveVersion !in SUPPORTED_ARCHIVE_VERSIONS) {
                        malformed(
                            "Archive version $archiveVersion is not supported; " +
                                "this build reads versions $SUPPORTED_ARCHIVE_VERSIONS.",
                            ArchiveRejection.UNSUPPORTED_VERSION
                        )
                    }
                }

                "createdAtEpochMillis" ->
                    createdAt = nextTimestamp("createdAtEpochMillis")

                "appVersionName" ->
                    appVersionName = nextBoundedString("appVersionName", MAX_SHORT_TEXT_LENGTH)

                "appVersionCode" ->
                    appVersionCode = nextBoundedLong("appVersionCode", 0L, Long.MAX_VALUE)

                "roomSchemaVersion" ->
                    roomSchemaVersion = nextBoundedInt("roomSchemaVersion", 1, 1_000_000)

                "catalogCommit" ->
                    catalogCommit = nextBoundedString("catalogCommit", MAX_SHORT_TEXT_LENGTH)

                else -> malformed("The archive metadata has an unsupported field.")
            }
        }

        return LocalDataArchiveMetadata(
            archiveVersion = archiveVersion
                ?: malformed("The archive metadata is missing 'archiveVersion'."),
            createdAtEpochMillis = createdAt
                ?: malformed("The archive metadata is missing 'createdAtEpochMillis'."),
            appVersionName = appVersionName
                ?: malformed("The archive metadata is missing 'appVersionName'."),
            appVersionCode = appVersionCode
                ?: malformed("The archive metadata is missing 'appVersionCode'."),
            roomSchemaVersion = roomSchemaVersion
                ?: malformed("The archive metadata is missing 'roomSchemaVersion'."),
            catalogCommit = catalogCommit
        )
    }

    private fun JsonReader.readChecksum(): String {
        var algorithm: String? = null
        var value: String? = null
        readObject("archive checksum") { field ->
            when (field) {
                "algorithm" -> algorithm = nextBoundedString("algorithm", MAX_SHORT_TEXT_LENGTH)
                "value" -> value = nextBoundedString("value", MAX_SHORT_TEXT_LENGTH)
                else -> malformed("The archive checksum has an unsupported field.")
            }
        }
        if (algorithm != CHECKSUM_ALGORITHM) {
            malformed("The archive checksum algorithm is not $CHECKSUM_ALGORITHM.")
        }
        return value ?: malformed("The archive checksum is missing its value.")
    }

    /**
     * Reads the data object under the version its own metadata declared.
     *
     * The declared version gates which fields are legal, so a document claiming version 1
     * while carrying version 2 content is refused rather than quietly upgraded.
     */
    private fun JsonReader.readSnapshot(archiveVersion: Int): LocalDataSnapshot {
        var profile: UserProfile? = null
        var templates: List<WorkoutTemplate>? = null
        var sessions: List<WorkoutSession>? = null
        var recommendations: List<RecommendationRecord>? = null

        readObject("archive data") { field ->
            when (field) {
                "profile" -> profile = readProfile(archiveVersion)
                "templates" -> templates = readArray("templates", MAX_TEMPLATES) { readTemplate() }
                "sessions" -> sessions = readArray("sessions", MAX_SESSIONS) { readSession() }
                "recommendations" -> {
                    if (archiveVersion < RECOMMENDATION_RECORDS_ARCHIVE_VERSION) {
                        malformed(
                            "A version $archiveVersion archive cannot carry recommendation records."
                        )
                    }
                    recommendations = readArray(
                        "recommendations",
                        MAX_RECOMMENDATION_RECORDS
                    ) { readRecommendation() }
                }

                else -> malformed("The archive data has an unsupported field.")
            }
        }

        return LocalDataSnapshot(
            profile = profile,
            templates = templates ?: malformed("The archive data is missing 'templates'."),
            sessions = sessions ?: malformed("The archive data is missing 'sessions'."),
            recommendationRecords = recommendations.orEmpty()
        )
    }

    private fun JsonReader.readRecommendation(): RecommendationRecord {
        var sessionId: String? = null
        var validatorVersion: String? = null
        var durationEstimatorVersion: String? = null
        var outcome: String? = null
        var reviewedPathEnabled: Boolean? = null
        var catalogVersion: String? = null
        var reviewPolicyVersion: Int? = null
        var trainingPolicyVersion: String? = null
        var ledgerPolicyVersion: String? = null
        var programStatePolicyVersion: String? = null
        var adaptationState: String? = null
        var weekStartEpochDay: Long? = null
        var timeZoneId: String? = null
        var profileRevision: Long? = null
        var contextIdentity: String? = null
        var reasonCodes: List<String>? = null
        var doseAccounting: List<MuscleDoseAccounting>? = null
        var recordedAtEpochMillis: Long? = null

        readObject("recommendation") { field ->
            when (field) {
                "sessionId" ->
                    sessionId = nextBoundedString("recommendation.sessionId", MAX_ID_LENGTH)

                "validatorVersion" -> validatorVersion =
                    nextBoundedString("recommendation.validatorVersion", MAX_SHORT_TEXT_LENGTH)

                "durationEstimatorVersion" -> durationEstimatorVersion = nextBoundedString(
                    "recommendation.durationEstimatorVersion",
                    MAX_SHORT_TEXT_LENGTH
                )

                "outcome" ->
                    outcome = nextBoundedString("recommendation.outcome", MAX_SHORT_TEXT_LENGTH)

                "reviewedPathEnabled" -> reviewedPathEnabled = nextBoolean()
                "catalogVersion" -> catalogVersion =
                    nextBoundedString("recommendation.catalogVersion", MAX_ID_LENGTH)

                "reviewPolicyVersion" -> reviewPolicyVersion =
                    nextBoundedInt("recommendation.reviewPolicyVersion", 0, MAX_POLICY_VERSION)

                "trainingPolicyVersion" -> trainingPolicyVersion = nextBoundedString(
                    "recommendation.trainingPolicyVersion",
                    MAX_SHORT_TEXT_LENGTH
                )

                "ledgerPolicyVersion" -> ledgerPolicyVersion = nextBoundedString(
                    "recommendation.ledgerPolicyVersion",
                    MAX_SHORT_TEXT_LENGTH
                )

                "programStatePolicyVersion" -> programStatePolicyVersion = nextBoundedString(
                    "recommendation.programStatePolicyVersion",
                    MAX_SHORT_TEXT_LENGTH
                )

                "adaptationState" -> adaptationState =
                    nextBoundedString("recommendation.adaptationState", MAX_SHORT_TEXT_LENGTH)

                "weekStartEpochDay" -> weekStartEpochDay = nextBoundedLong(
                    "recommendation.weekStartEpochDay",
                    MIN_WEEK_START_EPOCH_DAY,
                    MAX_WEEK_START_EPOCH_DAY
                )

                "timeZoneId" -> timeZoneId =
                    nextBoundedString("recommendation.timeZoneId", MAX_SHORT_TEXT_LENGTH)

                "profileRevision" -> profileRevision =
                    nextBoundedLong("recommendation.profileRevision", 0L, Long.MAX_VALUE)

                "contextIdentity" -> contextIdentity =
                    nextBoundedString("recommendation.contextIdentity", MAX_ID_LENGTH)

                "reasonCodes" -> reasonCodes = readArray(
                    "recommendation.reasonCodes",
                    RecommendationRecord.MAX_REASON_CODES
                ) { nextBoundedString("recommendation.reasonCode", MAX_SHORT_TEXT_LENGTH) }

                "doseAccounting" -> doseAccounting = RecommendationDoseAccountingPayload
                    .decode(nextBoundedString("recommendation.doseAccounting", MAX_NOTES_LENGTH))
                    ?: malformed(
                        "The archived recommendation.doseAccounting is not a payload this " +
                            "build can read.",
                        ArchiveRejection.INVALID_VALUE
                    )

                "recordedAtEpochMillis" ->
                    recordedAtEpochMillis = nextTimestamp("recommendation.recordedAtEpochMillis")

                else -> malformed("The archived recommendation has an unsupported field.")
            }
        }

        // The domain type owns the remaining rules — bounds, blank and control-character
        // tokens, duplicate reason codes, duplicate muscles — so the archive and the
        // database cannot disagree about what a well-formed record is.
        return RecommendationRecord(
            sessionId = sessionId
                ?: malformed("The archived recommendation is missing 'sessionId'."),
            validatorVersion = validatorVersion
                ?: malformed("The archived recommendation is missing 'validatorVersion'."),
            durationEstimatorVersion = durationEstimatorVersion
                ?: malformed("The archived recommendation is missing 'durationEstimatorVersion'."),
            outcome = outcome
                ?: malformed("The archived recommendation is missing 'outcome'."),
            reviewedPathEnabled = reviewedPathEnabled
                ?: malformed("The archived recommendation is missing 'reviewedPathEnabled'."),
            catalogVersion = catalogVersion,
            reviewPolicyVersion = reviewPolicyVersion
                ?: malformed("The archived recommendation is missing 'reviewPolicyVersion'."),
            trainingPolicyVersion = trainingPolicyVersion,
            ledgerPolicyVersion = ledgerPolicyVersion,
            programStatePolicyVersion = programStatePolicyVersion,
            adaptationState = adaptationState,
            weekStartEpochDay = weekStartEpochDay,
            timeZoneId = timeZoneId,
            profileRevision = profileRevision
                ?: malformed("The archived recommendation is missing 'profileRevision'."),
            contextIdentity = contextIdentity
                ?: malformed("The archived recommendation is missing 'contextIdentity'."),
            reasonCodes = reasonCodes
                ?: malformed("The archived recommendation is missing 'reasonCodes'."),
            doseAccounting = doseAccounting
                ?: malformed("The archived recommendation is missing 'doseAccounting'."),
            recordedAtEpochMillis = recordedAtEpochMillis
                ?: malformed("The archived recommendation is missing 'recordedAtEpochMillis'.")
        )
    }

    private fun JsonReader.readProfile(archiveVersion: Int): UserProfile {
        var id: String? = null
        var revision: Long? = null
        var name: String? = null
        var goals: List<FitnessGoal>? = null
        var experienceLevel: ExperienceLevel? = null
        var preferredDurationMinutes: Int? = null
        var daysPerWeek: Int? = null
        var availableEquipment: List<String>? = null
        var preferredUnit: WeightUnit? = null
        var musclePriorities: Map<String, PriorityLevel>? = null
        var excludedExerciseIds: List<String>? = null
        var onboardingCompleted: Boolean? = null
        var trainingConstraints: List<TrainingConstraint>? = null
        var returningAfterBreakWeeks: Int? = null
        var confirmedStartingLoads: Map<String, Double>? = null
        var movementCapabilities: MovementCapabilities? = null
        var themePreference: ThemePreference? = null
        var gender: ProfileGender? = null
        var illustrationPreference: IllustrationPreference? = null

        readObject("profile") { field ->
            when (field) {
                "id" -> id = nextBoundedString("profile.id", MAX_ID_LENGTH)
                "revision" -> revision = nextBoundedLong("profile.revision", 0L, Long.MAX_VALUE)
                "name" -> name = nextBoundedString("profile.name", MAX_SHORT_TEXT_LENGTH)
                "goals" -> goals = readArray("profile.goals", MAX_COLLECTION_ITEMS) {
                    nextEnum<FitnessGoal>("profile.goals")
                }

                "experienceLevel" ->
                    experienceLevel = nextEnum<ExperienceLevel>("profile.experienceLevel")
                "preferredDurationMinutes" ->
                    preferredDurationMinutes =
                        nextBoundedInt(
                            "profile.preferredDurationMinutes",
                            1,
                            MAX_PREFERRED_DURATION_MINUTES
                        )

                "daysPerWeek" ->
                    daysPerWeek = nextBoundedInt("profile.daysPerWeek", 0, MAX_DAYS_PER_WEEK)
                "availableEquipment" ->
                    availableEquipment =
                        readArray("profile.availableEquipment", MAX_COLLECTION_ITEMS) {
                            nextBoundedString("profile.availableEquipment", MAX_SHORT_TEXT_LENGTH)
                        }

                "preferredUnit" -> preferredUnit = nextEnum<WeightUnit>("profile.preferredUnit")
                "musclePriorities" -> musclePriorities =
                    readMap("profile.musclePriorities") {
                        nextEnum<PriorityLevel>("profile.musclePriorities")
                    }

                "excludedExerciseIds" ->
                    excludedExerciseIds =
                        readArray("profile.excludedExerciseIds", MAX_COLLECTION_ITEMS) {
                            nextBoundedString("profile.excludedExerciseIds", MAX_ID_LENGTH)
                        }

                "onboardingCompleted" -> onboardingCompleted = nextBoolean()
                "trainingConstraints" ->
                    trainingConstraints =
                        readArray("profile.trainingConstraints", MAX_COLLECTION_ITEMS) {
                            nextEnum<TrainingConstraint>("profile.trainingConstraints")
                        }

                "returningAfterBreakWeeks" ->
                    returningAfterBreakWeeks =
                        nextBoundedInt("profile.returningAfterBreakWeeks", 0, MAX_BREAK_WEEKS)

                "confirmedStartingLoads" -> confirmedStartingLoads =
                    readMap("profile.confirmedStartingLoads") {
                        nextBoundedDouble("profile.confirmedStartingLoads", 0.0, MAX_WEIGHT)
                    }

                "movementCapabilities" -> {
                    val answers = readMap("profile.movementCapabilities") {
                        nextEnum<CapabilityLevel>("profile.movementCapabilities")
                    }.mapKeys { (rawType, _) ->
                        // Rejected rather than dropped. A dropped key would not survive
                        // re-serialisation, so the archive's own checksum would then refuse
                        // it as "damaged" and blame the wrong thing. A movement this build
                        // does not know is an unsupported value, and it is reported as one.
                        MovementCapabilityType.entries.firstOrNull { it.name == rawType }
                            ?: malformed(
                                "The archived profile.movementCapabilities names a movement " +
                                    "this build does not support.",
                                ArchiveRejection.INVALID_VALUE
                            )
                    }
                    // MovementCapabilities.from() fills anything absent with UNKNOWN. That is
                    // right for a stored row written before a movement existed, but wrong
                    // here: a document that omits an answer would restore as "not sure" when
                    // the user may have recorded "avoid", quietly changing a safety-relevant
                    // planning input. An archive answers every movement or it is refused.
                    if (answers.keys != MovementCapabilityType.entries.toSet()) {
                        malformed(
                            "The archived profile.movementCapabilities does not answer every " +
                                "movement this build asks about.",
                            ArchiveRejection.INVALID_VALUE
                        )
                    }
                    movementCapabilities = MovementCapabilities.from(answers)
                }

                "themePreference" ->
                    themePreference = nextEnum<ThemePreference>("profile.themePreference")
                "gender" -> {
                    if (archiveVersion < 3) malformed("Gender requires archive version 3.")
                    gender = nextEnum<ProfileGender>("profile.gender")
                }
                "illustrationPreference" -> {
                    if (archiveVersion < 3) malformed("Illustration preferences require archive version 3.")
                    illustrationPreference = nextEnum<IllustrationPreference>("profile.illustrationPreference")
                }
                else -> malformed("The archived profile has an unsupported field.")
            }
        }

        val profileGoals = goals ?: malformed("The archived profile is missing 'goals'.")
        // Goals and constraints are sets in the domain. A repeated entry would silently
        // collapse and then fail the checksum as if the file were damaged, so a duplicate is
        // named for what it is instead.
        requireNoDuplicates(profileGoals.map(FitnessGoal::name), "profile.goals")
        requireNoDuplicates(
            (trainingConstraints ?: emptyList()).map(TrainingConstraint::name),
            "profile.trainingConstraints"
        )
        return UserProfile(
            id = id ?: malformed("The archived profile is missing 'id'."),
            revision = revision ?: malformed("The archived profile is missing 'revision'."),
            name = name ?: malformed("The archived profile is missing 'name'."),
            // LinkedHashSet: the archived order decides which goal is primary.
            goals = LinkedHashSet(profileGoals),
            experienceLevel = experienceLevel
                ?: malformed("The archived profile is missing 'experienceLevel'."),
            preferredDurationMinutes = preferredDurationMinutes
                ?: malformed("The archived profile is missing 'preferredDurationMinutes'."),
            daysPerWeek = daysPerWeek
                ?: malformed("The archived profile is missing 'daysPerWeek'."),
            availableEquipment = availableEquipment
                ?: malformed("The archived profile is missing 'availableEquipment'."),
            preferredUnit = preferredUnit
                ?: malformed("The archived profile is missing 'preferredUnit'."),
            musclePriorities = musclePriorities
                ?: malformed("The archived profile is missing 'musclePriorities'."),
            excludedExerciseIds = excludedExerciseIds
                ?: malformed("The archived profile is missing 'excludedExerciseIds'."),
            onboardingCompleted = onboardingCompleted
                ?: malformed("The archived profile is missing 'onboardingCompleted'."),
            trainingConstraints = LinkedHashSet(
                trainingConstraints
                    ?: malformed("The archived profile is missing 'trainingConstraints'.")
            ),
            returningAfterBreakWeeks = returningAfterBreakWeeks
                ?: malformed("The archived profile is missing 'returningAfterBreakWeeks'."),
            confirmedStartingLoads = confirmedStartingLoads
                ?: malformed("The archived profile is missing 'confirmedStartingLoads'."),
            movementCapabilities = movementCapabilities
                ?: malformed("The archived profile is missing 'movementCapabilities'."),
            themePreference = themePreference
                ?: malformed("The archived profile is missing 'themePreference'."),
            gender = gender ?: if (archiveVersion < 3) ProfileGender.UNSPECIFIED
                else malformed("The archived profile is missing 'gender'."),
            illustrationPreference = illustrationPreference ?: if (archiveVersion < 3) IllustrationPreference.AUTOMATIC
                else malformed("The archived profile is missing 'illustrationPreference'.")
        )
    }

    private fun JsonReader.readTemplate(): WorkoutTemplate {
        var id: String? = null
        var name: String? = null
        var notes: String? = null
        var createdAt: Long? = null
        var updatedAt: Long? = null
        var exercises: List<PlannedExercise>? = null

        readObject("template") { field ->
            when (field) {
                "id" -> id = nextBoundedString("template.id", MAX_ID_LENGTH)
                "name" -> name = nextBoundedString("template.name", MAX_NAME_LENGTH)
                "notes" -> notes = nextBoundedString("template.notes", MAX_NOTES_LENGTH)
                "createdAtTimestamp" -> createdAt = nextTimestamp("template.createdAtTimestamp")
                "updatedAtTimestamp" -> updatedAt = nextTimestamp("template.updatedAtTimestamp")
                "exercises" -> exercises =
                    readArray("template.exercises", MAX_EXERCISES_PER_SESSION) {
                        readPlannedExercise("template.exercises")
                    }

                else -> malformed("An archived template has an unsupported field.")
            }
        }

        // WorkoutTemplate's own invariants (identifier, name and note lengths, ordered
        // timestamps, exercise count) run here rather than being restated.
        return WorkoutTemplate(
            id = id ?: malformed("An archived template is missing 'id'."),
            name = name ?: malformed("An archived template is missing 'name'."),
            notes = notes ?: malformed("An archived template is missing 'notes'."),
            createdAtTimestamp = createdAt
                ?: malformed("An archived template is missing 'createdAtTimestamp'."),
            updatedAtTimestamp = updatedAt
                ?: malformed("An archived template is missing 'updatedAtTimestamp'."),
            exercises = exercises ?: malformed("An archived template is missing 'exercises'.")
        )
    }

    private fun JsonReader.readPlannedExercise(label: String): PlannedExercise {
        var exerciseId: String? = null
        var notes: String? = null
        var prescription: ExercisePrescription? = null

        readObject(label) { field ->
            when (field) {
                "exerciseId" -> exerciseId = nextBoundedString("$label.exerciseId", MAX_ID_LENGTH)
                "notes" -> notes = nextBoundedString("$label.notes", MAX_NOTES_LENGTH)
                "prescription" -> prescription = readPrescription("$label.prescription")
                else -> malformed("An archived template exercise has an unsupported field.")
            }
        }

        return PlannedExercise(
            exerciseId = exerciseId ?: malformed("An archived template exercise is missing 'exerciseId'."),
            prescription = prescription
                ?: malformed("An archived template exercise is missing 'prescription'."),
            notes = notes ?: malformed("An archived template exercise is missing 'notes'.")
        )
    }

    private fun JsonReader.readSession(): WorkoutSession {
        var id: String? = null
        var name: String? = null
        var startedAt: Long? = null
        var completedAt: Long? = null
        var targetDurationMinutes: Int? = null
        var actualDurationMinutes: Int? = null
        var weightUnit: WeightUnit? = null
        var status: SessionStatus? = null
        var origin: WorkoutOrigin? = null
        var sourceTemplateId: String? = null
        var focusMuscles: List<String>? = null
        var notes: String? = null
        var exercises: List<WorkoutExercise>? = null

        readObject("session") { field ->
            when (field) {
                "id" -> id = nextBoundedString("session.id", MAX_ID_LENGTH)
                "name" -> name = nextBoundedString("session.name", MAX_NAME_LENGTH)
                "startedAtTimestamp" -> startedAt = nextTimestamp("session.startedAtTimestamp")
                "completedAtTimestamp" ->
                    completedAt = nextTimestamp("session.completedAtTimestamp")

                "targetDurationMinutes" ->
                    targetDurationMinutes =
                        nextBoundedInt("session.targetDurationMinutes", 0, MAX_SESSION_MINUTES)

                "actualDurationMinutes" ->
                    actualDurationMinutes =
                        nextBoundedInt("session.actualDurationMinutes", 0, MAX_SESSION_MINUTES)

                "weightUnit" -> weightUnit = nextEnum<WeightUnit>("session.weightUnit")
                "status" -> status = nextEnum<SessionStatus>("session.status")
                "origin" -> origin = nextEnum<WorkoutOrigin>("session.origin")
                "sourceTemplateId" ->
                    sourceTemplateId = nextBoundedString("session.sourceTemplateId", MAX_ID_LENGTH)

                "focusMuscles" -> focusMuscles =
                    readArray("session.focusMuscles", MAX_COLLECTION_ITEMS) {
                        nextBoundedString("session.focusMuscles", MAX_SHORT_TEXT_LENGTH)
                    }

                "notes" -> notes = nextBoundedString("session.notes", MAX_NOTES_LENGTH)
                "exercises" -> exercises =
                    readArray("session.exercises", MAX_EXERCISES_PER_SESSION) {
                        readSessionExercise()
                    }

                else -> malformed("An archived session has an unsupported field.")
            }
        }

        val sessionStatus = status ?: malformed("An archived session is missing 'status'.")

        return WorkoutSession(
            id = id ?: malformed("An archived session is missing 'id'."),
            name = name ?: malformed("An archived session is missing 'name'."),
            startedAtTimestamp = startedAt
                ?: malformed("An archived session is missing 'startedAtTimestamp'."),
            completedAtTimestamp = completedAt,
            targetDurationMinutes = targetDurationMinutes
                ?: malformed("An archived session is missing 'targetDurationMinutes'."),
            actualDurationMinutes = actualDurationMinutes
                ?: malformed("An archived session is missing 'actualDurationMinutes'."),
            weightUnit = weightUnit ?: malformed("An archived session is missing 'weightUnit'."),
            status = sessionStatus,
            origin = origin ?: malformed("An archived session is missing 'origin'."),
            sourceTemplateId = sourceTemplateId,
            focusMuscles = focusMuscles
                ?: malformed("An archived session is missing 'focusMuscles'."),
            exercises = exercises ?: malformed("An archived session is missing 'exercises'."),
            notes = notes ?: malformed("An archived session is missing 'notes'.")
        )
    }

    private fun JsonReader.readSessionExercise(): WorkoutExercise {
        var id: String? = null
        var sessionId: String? = null
        var exerciseId: String? = null
        var orderIndex: Int? = null
        var notes: String? = null
        var prescription: ExercisePrescription? = null
        var sets: List<WorkoutSet>? = null

        readObject("session exercise") { field ->
            when (field) {
                "id" -> id = nextBoundedString("session exercise.id", MAX_ID_LENGTH)
                "sessionId" ->
                    sessionId = nextBoundedString("session exercise.sessionId", MAX_ID_LENGTH)

                "exerciseId" ->
                    exerciseId = nextBoundedString("session exercise.exerciseId", MAX_ID_LENGTH)

                "orderIndex" ->
                    orderIndex = nextBoundedInt("session exercise.orderIndex", 0, MAX_ORDER_INDEX)

                "notes" -> notes = nextBoundedString("session exercise.notes", MAX_NOTES_LENGTH)
                "prescription" -> prescription = readPrescription("session exercise.prescription")
                "sets" -> sets = readArray("session exercise.sets", MAX_SETS_PER_EXERCISE) {
                    readSet()
                }

                else -> malformed("An archived session exercise has an unsupported field.")
            }
        }

        return WorkoutExercise(
            id = id ?: malformed("An archived session exercise is missing 'id'."),
            sessionId = sessionId
                ?: malformed("An archived session exercise is missing 'sessionId'."),
            exerciseId = exerciseId
                ?: malformed("An archived session exercise is missing 'exerciseId'."),
            orderIndex = orderIndex
                ?: malformed("An archived session exercise is missing 'orderIndex'."),
            prescription = prescription
                ?: malformed("An archived session exercise is missing 'prescription'."),
            notes = notes ?: malformed("An archived session exercise is missing 'notes'."),
            sets = sets ?: malformed("An archived session exercise is missing 'sets'.")
        )
    }

    private fun JsonReader.readPrescription(label: String): ExercisePrescription {
        var exerciseType: ExerciseType? = null
        var targetSets: Int? = null
        var repMin: Int? = null
        var repMax: Int? = null
        var targetWeight: Double? = null
        var targetAssistanceWeight: Double? = null
        var targetDurationSeconds: Int? = null
        var targetDistanceMeters: Double? = null
        var restSeconds: Int? = null
        var effortMinRir: Int? = null
        var effortMaxRir: Int? = null
        var restClass: RestClass? = null
        var restTargetSource: RestTargetSource? = null

        readObject(label) { field ->
            when (field) {
                "exerciseType" -> exerciseType = nextEnum<ExerciseType>("$label.exerciseType")
                "targetSets" -> targetSets = nextBoundedInt("$label.targetSets", 0, 1_000)
                "targetRepMin" -> repMin = nextBoundedInt("$label.targetRepMin", 0, MAX_REPETITIONS)
                "targetRepMax" -> repMax = nextBoundedInt("$label.targetRepMax", 0, MAX_REPETITIONS)
                "targetWeight" ->
                    targetWeight = nextBoundedDouble("$label.targetWeight", 0.0, MAX_WEIGHT)

                "targetAssistanceWeight" ->
                    targetAssistanceWeight =
                        nextBoundedDouble("$label.targetAssistanceWeight", 0.0, MAX_WEIGHT)

                "targetDurationSeconds" ->
                    targetDurationSeconds =
                        nextBoundedInt("$label.targetDurationSeconds", 0, MAX_DURATION_SECONDS)

                "targetDistanceMeters" ->
                    targetDistanceMeters =
                        nextBoundedDouble("$label.targetDistanceMeters", 0.0, MAX_DISTANCE_METERS)

                "restSeconds" -> restSeconds = nextBoundedInt("$label.restSeconds", 0, 86_400)
                "effortMinRir" -> effortMinRir = nextBoundedInt("$label.effortMinRir", 0, 1_000)
                "effortMaxRir" -> effortMaxRir = nextBoundedInt("$label.effortMaxRir", 0, 1_000)
                "restClass" -> restClass = nextEnum<RestClass>("$label.restClass")
                "restTargetSource" ->
                    restTargetSource = nextEnum<RestTargetSource>("$label.restTargetSource")
                else -> malformed("An archived prescription has an unsupported field.")
            }
        }

        if ((repMin == null) != (repMax == null)) {
            malformed("An archived prescription has only one half of its repetition range.")
        }
        if ((effortMinRir == null) != (effortMaxRir == null)) {
            malformed("An archived prescription has only one half of its effort target.")
        }

        // ExercisePrescription, RepRange and EffortTarget enforce the type-specific rules
        // (which targets a type may carry, ordered ranges, bounds) that already govern every
        // persisted row, so an archive cannot introduce a shape the app could not have written.
        return ExercisePrescription(
            exerciseType = exerciseType
                ?: malformed("An archived prescription is missing 'exerciseType'."),
            targetSets = targetSets
                ?: malformed("An archived prescription is missing 'targetSets'."),
            repRange = repMin?.let { min -> RepRange(min = min, max = repMax!!) },
            targetWeight = targetWeight,
            targetAssistanceWeight = targetAssistanceWeight,
            targetDurationSeconds = targetDurationSeconds,
            targetDistanceMeters = targetDistanceMeters,
            restSeconds = restSeconds
                ?: malformed("An archived prescription is missing 'restSeconds'."),
            effortTarget = effortMinRir?.let { min ->
                EffortTarget(minRir = min, maxRir = effortMaxRir!!)
            },
            restClass = restClass,
            restTargetSource = restTargetSource
        )
    }

    private fun JsonReader.readSet(): WorkoutSet {
        var id: String? = null
        var workoutExerciseId: String? = null
        var setNumber: Int? = null
        var exerciseType: ExerciseType? = null
        var targetReps: Int? = null
        var completedReps: Int? = null
        var targetWeight: Double? = null
        var completedWeight: Double? = null
        var targetAssistanceWeight: Double? = null
        var completedAssistanceWeight: Double? = null
        var targetDurationSeconds: Int? = null
        var completedDurationSeconds: Int? = null
        var targetDistanceMeters: Double? = null
        var completedDistanceMeters: Double? = null
        var isCompleted: Boolean? = null
        var rpe: Float? = null
        var rir: Int? = null
        var feltManageable: Boolean? = null
        var completedAtTimestamp: Long? = null
        var stoppedAtTimestamp: Long? = null
        var stopReason: SetStopReason? = null
        var type: SetType? = null

        readObject("set") { field ->
            when (field) {
                "id" -> id = nextBoundedString("set.id", MAX_ID_LENGTH)
                "workoutExerciseId" ->
                    workoutExerciseId = nextBoundedString("set.workoutExerciseId", MAX_ID_LENGTH)

                "setNumber" -> setNumber = nextBoundedInt("set.setNumber", 1, MAX_ORDER_INDEX)
                "exerciseType" -> exerciseType = nextEnum<ExerciseType>("set.exerciseType")
                "targetReps" -> targetReps = nextBoundedInt("set.targetReps", 0, MAX_REPETITIONS)
                "completedReps" ->
                    completedReps = nextBoundedInt("set.completedReps", 0, MAX_REPETITIONS)

                "targetWeight" -> targetWeight = nextBoundedDouble("set.targetWeight", 0.0, MAX_WEIGHT)
                "completedWeight" ->
                    completedWeight = nextBoundedDouble("set.completedWeight", 0.0, MAX_WEIGHT)

                "targetAssistanceWeight" ->
                    targetAssistanceWeight =
                        nextBoundedDouble("set.targetAssistanceWeight", 0.0, MAX_WEIGHT)

                "completedAssistanceWeight" ->
                    completedAssistanceWeight =
                        nextBoundedDouble("set.completedAssistanceWeight", 0.0, MAX_WEIGHT)

                "targetDurationSeconds" ->
                    targetDurationSeconds =
                        nextBoundedInt("set.targetDurationSeconds", 0, MAX_DURATION_SECONDS)

                "completedDurationSeconds" ->
                    completedDurationSeconds =
                        nextBoundedInt("set.completedDurationSeconds", 0, MAX_DURATION_SECONDS)

                "targetDistanceMeters" ->
                    targetDistanceMeters =
                        nextBoundedDouble("set.targetDistanceMeters", 0.0, MAX_DISTANCE_METERS)

                "completedDistanceMeters" ->
                    completedDistanceMeters =
                        nextBoundedDouble("set.completedDistanceMeters", 0.0, MAX_DISTANCE_METERS)

                "isCompleted" -> isCompleted = nextBoolean()
                "rpe" -> rpe = nextBoundedDouble(
                    "set.rpe",
                    SetOutcomeRules.MIN_RPE.toDouble(),
                    SetOutcomeRules.MAX_RPE.toDouble()
                ).toFloat()

                "rir" -> rir = nextBoundedInt("set.rir", SetOutcomeRules.MIN_RIR, SetOutcomeRules.MAX_RIR)
                "feltManageable" -> feltManageable = nextBoolean()
                "completedAtTimestamp" ->
                    completedAtTimestamp = nextTimestamp("set.completedAtTimestamp")

                "stoppedAtTimestamp" ->
                    stoppedAtTimestamp = nextTimestamp("set.stoppedAtTimestamp")

                "stopReason" -> stopReason = nextEnum<SetStopReason>("set.stopReason")
                "type" -> type = nextEnum<SetType>("set.type")
                else -> malformed("An archived set has an unsupported field.")
            }
        }

        val setIsCompleted = isCompleted ?: malformed("An archived set is missing 'isCompleted'.")

        return WorkoutSet(
            id = id ?: malformed("An archived set is missing 'id'."),
            workoutExerciseId = workoutExerciseId
                ?: malformed("An archived set is missing 'workoutExerciseId'."),
            setNumber = setNumber ?: malformed("An archived set is missing 'setNumber'."),
            exerciseType = exerciseType ?: malformed("An archived set is missing 'exerciseType'."),
            targetReps = targetReps,
            completedReps = completedReps,
            targetWeight = targetWeight,
            completedWeight = completedWeight,
            targetAssistanceWeight = targetAssistanceWeight,
            completedAssistanceWeight = completedAssistanceWeight,
            targetDurationSeconds = targetDurationSeconds,
            completedDurationSeconds = completedDurationSeconds,
            targetDistanceMeters = targetDistanceMeters,
            completedDistanceMeters = completedDistanceMeters,
            isCompleted = setIsCompleted,
            rpe = rpe,
            rir = rir,
            feltManageable = feltManageable,
            completedAtTimestamp = completedAtTimestamp,
            stoppedAtTimestamp = stoppedAtTimestamp,
            stopReason = stopReason,
            type = type ?: malformed("An archived set is missing 'type'.")
        )
    }

    // -------------------------------------------------------------- validation

    /**
     * Whole-document rules that no single record can check on its own.
     *
     * These run on both sides. On export they stop an unreadable document from being
     * written; on import they run before any transaction opens.
     */
    private fun validateSnapshot(snapshot: LocalDataSnapshot) {
        if (snapshot.sessions.size > MAX_SESSIONS) {
            tooLarge("The archive contains more than $MAX_SESSIONS sessions.")
        }
        if (snapshot.templates.size > MAX_TEMPLATES) {
            tooLarge("The archive contains more than $MAX_TEMPLATES templates.")
        }

        // A profile-less archive is legitimate only before onboarding finishes, which is
        // also the only time the app has nothing else to write. History with no owning
        // profile would restore into a state the app cannot produce or reach: onboarding
        // would start over while a full history already blocked the next restore.
        if (snapshot.profile == null &&
            (snapshot.sessions.isNotEmpty() || snapshot.templates.isNotEmpty())
        ) {
            inconsistent("The archive holds workouts or templates but no profile.")
        }
        snapshot.profile?.let(::validateProfile)
        snapshot.templates.forEach(::validateTemplate)

        requireUniqueIds(snapshot.templates.map(WorkoutTemplate::id), "template")
        requireUniqueIds(snapshot.sessions.map(WorkoutSession::id), "session")

        val activeSessions = snapshot.sessions.count { it.status == SessionStatus.IN_PROGRESS }
        if (activeSessions > 1) {
            inconsistent(
                "The archive contains $activeSessions active workouts; only one workout may be active."
            )
        }

        val exerciseIds = mutableSetOf<String>()
        val setIds = mutableSetOf<String>()
        var totalSets = 0
        snapshot.sessions.forEach { session ->
            validateSession(session)
            session.exercises.forEach { exercise ->
                if (!exerciseIds.add(exercise.id)) {
                    inconsistent("The archive reuses a session exercise identifier.")
                }
                if (exercise.sessionId != session.id) {
                    inconsistent("An archived session exercise references a different session.")
                }
                if (exercise.sets.size > MAX_SETS_PER_EXERCISE) {
                    tooLarge("An archived exercise contains more than $MAX_SETS_PER_EXERCISE sets.")
                }
                requireIdentifier(exercise.id, "session exercise.id")
                requireIdentifier(exercise.exerciseId, "session exercise.exerciseId")
                requireText(exercise.notes, "session exercise.notes", MAX_NOTES_LENGTH)
                requireInRange(exercise.orderIndex, "session exercise.orderIndex", 0, MAX_ORDER_INDEX)

                exercise.sets.forEach { set ->
                    if (!setIds.add(set.id)) inconsistent("The archive reuses a set identifier.")
                    if (set.workoutExerciseId != exercise.id) {
                        inconsistent("An archived set references a different exercise.")
                    }
                    if (set.exerciseType != exercise.prescription.exerciseType) {
                        inconsistent("An archived set does not match its exercise type.")
                    }
                    validateSet(set)
                    totalSets += 1
                    if (totalSets > MAX_TOTAL_SETS) {
                        tooLarge("The archive contains more than $MAX_TOTAL_SETS sets.")
                    }
                }
            }
        }

        // Session and exercise identifiers share no table, but a set identifier colliding
        // with an exercise identifier would still be a document that means two things.
        if (exerciseIds.intersect(setIds).isNotEmpty()) {
            inconsistent("The archive uses the same identifier for an exercise and a set.")
        }

        validateRecommendations(snapshot)
    }

    /**
     * Recommendation records must belong to sessions the same document carries.
     *
     * A record naming a session that is not here would restore into a row the foreign key
     * refuses, and two records for one session would mean the document holds two answers to
     * how one plan was validated. Both are refused before anything is written.
     */
    private fun validateRecommendations(snapshot: LocalDataSnapshot) {
        if (snapshot.recommendationRecords.size > MAX_RECOMMENDATION_RECORDS) {
            tooLarge(
                "The archive contains more than $MAX_RECOMMENDATION_RECORDS recommendation records."
            )
        }
        if (snapshot.recommendationRecords.isEmpty()) return

        val sessionIds = snapshot.sessions.mapTo(mutableSetOf(), WorkoutSession::id)
        val seen = mutableSetOf<String>()
        snapshot.recommendationRecords.forEach { record ->
            requireIdentifier(record.sessionId, "recommendation.sessionId")
            if (record.sessionId !in sessionIds) {
                inconsistent("An archived recommendation references an unknown session.")
            }
            if (!seen.add(record.sessionId)) {
                inconsistent("The archive holds more than one recommendation for a session.")
            }
            // Reason codes are persisted into a "|||"-joined column, so they take the same
            // reserved-separator guard as every other joined value. Without it a restored
            // code of "A|||B" would read back as two codes the document never contained.
            record.reasonCodes.forEach { code ->
                requireJoinableText(code, "recommendation.reasonCode", MAX_SHORT_TEXT_LENGTH)
            }
        }
    }

    /**
     * Provenance bounds, applied on both sides.
     *
     * A device whose clock is set outside the supported range would otherwise produce an
     * archive that reports export success and is then refused at restore.
     */
    private fun validateMetadata(metadata: LocalDataArchiveMetadata) {
        if (metadata.archiveVersion !in SUPPORTED_ARCHIVE_VERSIONS) {
            malformed(
                "Archive version ${metadata.archiveVersion} is not supported; " +
                    "this build reads versions $SUPPORTED_ARCHIVE_VERSIONS.",
                ArchiveRejection.UNSUPPORTED_VERSION
            )
        }
        requireTimestamp(metadata.createdAtEpochMillis, "createdAtEpochMillis")
        requireInRange(metadata.appVersionCode, "appVersionCode", 0L, Long.MAX_VALUE)
        requireInRange(metadata.roomSchemaVersion, "roomSchemaVersion", 1, 1_000_000)
        requireText(metadata.appVersionName, "appVersionName", MAX_SHORT_TEXT_LENGTH)
        metadata.catalogCommit?.let { commit ->
            requireText(commit, "catalogCommit", MAX_SHORT_TEXT_LENGTH)
        }
    }

    private fun validateProfile(profile: UserProfile) {
        if (profile.id != UserProfile.DEFAULT_PROFILE_ID) {
            inconsistent("The archived profile does not use the identifier this app reads.")
        }
        // An empty goal set reads back as the default goal rather than as "none", so an
        // archive is never allowed to restore a goal it did not actually record.
        if (profile.goals.isEmpty()) {
            inconsistent("The archived profile records no fitness goal.")
        }
        requireText(profile.name, "profile.name", MAX_SHORT_TEXT_LENGTH)
        requireInRange(profile.revision, "profile.revision", 0L, Long.MAX_VALUE)
        requireInRange(profile.daysPerWeek, "profile.daysPerWeek", 0, MAX_DAYS_PER_WEEK)
        requireInRange(
            profile.preferredDurationMinutes,
            "profile.preferredDurationMinutes",
            1,
            MAX_PREFERRED_DURATION_MINUTES
        )
        requireInRange(
            profile.returningAfterBreakWeeks,
            "profile.returningAfterBreakWeeks",
            0,
            MAX_BREAK_WEEKS
        )

        requireCollection(profile.availableEquipment, "profile.availableEquipment")
        profile.availableEquipment.forEach { equipment ->
            requireJoinableText(equipment, "profile.availableEquipment", MAX_SHORT_TEXT_LENGTH)
        }
        requireCollection(profile.excludedExerciseIds, "profile.excludedExerciseIds")
        profile.excludedExerciseIds.forEach { exerciseId ->
            requireJoinableText(exerciseId, "profile.excludedExerciseIds", MAX_ID_LENGTH)
        }
        requireCollection(profile.musclePriorities.keys, "profile.musclePriorities")
        profile.musclePriorities.keys.forEach { muscle ->
            requireJoinableText(muscle, "profile.musclePriorities", MAX_ID_LENGTH)
            if (muscle.contains(PERSISTED_PAIR_SEPARATOR)) {
                invalidValue("The archived profile.musclePriorities has a key containing ':'.")
            }
            // Stored priorities are read back through the muscle vocabulary, which trims
            // names and expands umbrella terms such as "Legs" into several groups. A key
            // that does not survive that unchanged would restore as different priorities
            // than the document declared, so it is refused instead.
            if (MuscleVocabulary.canonicalize(muscle) != listOf(muscle)) {
                invalidValue(
                    "The archived profile.musclePriorities names a muscle group that is not " +
                        "how this app stores it."
                )
            }
        }
        requireCollection(profile.confirmedStartingLoads.keys, "profile.confirmedStartingLoads")
        profile.confirmedStartingLoads.forEach { (exerciseId, load) ->
            requireJoinableText(exerciseId, "profile.confirmedStartingLoads", MAX_ID_LENGTH)
            // The persisted form is "<id>:<load>", so a ':' in the identifier would make the
            // entry unreadable on the next start-up rather than merely odd.
            if (exerciseId.contains(PERSISTED_PAIR_SEPARATOR)) {
                invalidValue("The archived profile.confirmedStartingLoads has a key containing ':'.")
            }
            requireFinite(load, "profile.confirmedStartingLoads", 0.0, MAX_WEIGHT)
        }
    }

    private fun validateTemplate(template: WorkoutTemplate) {
        requireIdentifier(template.id, "template.id")
        requireTimestamp(template.createdAtTimestamp, "template.createdAtTimestamp")
        requireTimestamp(template.updatedAtTimestamp, "template.updatedAtTimestamp")
        if (template.exercises.size > MAX_EXERCISES_PER_SESSION) {
            tooLarge("An archived template contains more than $MAX_EXERCISES_PER_SESSION exercises.")
        }
        template.exercises.forEach { exercise ->
            requireIdentifier(exercise.exerciseId, "template.exercises.exerciseId")
        }
    }

    private fun validateSession(session: WorkoutSession) {
        requireIdentifier(session.id, "session.id")
        requireText(session.name, "session.name", MAX_NAME_LENGTH)
        requireText(session.notes, "session.notes", MAX_NOTES_LENGTH)
        session.sourceTemplateId?.let { requireIdentifier(it, "session.sourceTemplateId") }
        requireTimestamp(session.startedAtTimestamp, "session.startedAtTimestamp")
        session.completedAtTimestamp?.let {
            requireTimestamp(it, "session.completedAtTimestamp")
        }
        requireInRange(
            session.targetDurationMinutes,
            "session.targetDurationMinutes",
            0,
            MAX_SESSION_MINUTES
        )
        requireInRange(
            session.actualDurationMinutes,
            "session.actualDurationMinutes",
            0,
            MAX_SESSION_MINUTES
        )
        requireCollection(session.focusMuscles, "session.focusMuscles")
        session.focusMuscles.forEach { muscle ->
            requireJoinableText(muscle, "session.focusMuscles", MAX_SHORT_TEXT_LENGTH)
        }
        if (session.exercises.size > MAX_EXERCISES_PER_SESSION) {
            tooLarge("An archived session contains more than $MAX_EXERCISES_PER_SESSION exercises.")
        }
        // A session that is not completed cannot carry a completion time. The reverse is
        // deliberately not required: history written before completion timestamps were
        // always recorded reads back as completed with an unknown time, and that stays true
        // through an export rather than being rejected or given an invented one.
        if (session.status != SessionStatus.COMPLETED && session.completedAtTimestamp != null) {
            inconsistent("An archived session that is not completed carries a completion time.")
        }
    }

    private fun validateSet(set: WorkoutSet) {
        requireIdentifier(set.id, "set.id")
        requireInRange(set.setNumber, "set.setNumber", 1, MAX_ORDER_INDEX)
        requireOptionalInRange(set.targetReps, "set.targetReps", 0, MAX_REPETITIONS)
        requireOptionalInRange(set.completedReps, "set.completedReps", 0, MAX_REPETITIONS)
        requireOptionalFinite(set.targetWeight, "set.targetWeight", 0.0, MAX_WEIGHT)
        requireOptionalFinite(set.completedWeight, "set.completedWeight", 0.0, MAX_WEIGHT)
        requireOptionalFinite(
            set.targetAssistanceWeight,
            "set.targetAssistanceWeight",
            0.0,
            MAX_WEIGHT
        )
        requireOptionalFinite(
            set.completedAssistanceWeight,
            "set.completedAssistanceWeight",
            0.0,
            MAX_WEIGHT
        )
        requireOptionalInRange(
            set.targetDurationSeconds,
            "set.targetDurationSeconds",
            0,
            MAX_DURATION_SECONDS
        )
        requireOptionalInRange(
            set.completedDurationSeconds,
            "set.completedDurationSeconds",
            0,
            MAX_DURATION_SECONDS
        )
        requireOptionalFinite(
            set.targetDistanceMeters,
            "set.targetDistanceMeters",
            0.0,
            MAX_DISTANCE_METERS
        )
        requireOptionalFinite(
            set.completedDistanceMeters,
            "set.completedDistanceMeters",
            0.0,
            MAX_DISTANCE_METERS
        )
        set.completedAtTimestamp?.let { requireTimestamp(it, "set.completedAtTimestamp") }
        set.stoppedAtTimestamp?.let { requireTimestamp(it, "set.stoppedAtTimestamp") }

        // The cross-field outcome rules the logger already enforces, with one documented
        // relaxation: history logged before typed outcomes existed carries a completed set
        // with no completion timestamp, and that honest "unknown when" must survive a round
        // trip instead of being rejected or given a fabricated time.
        try {
            SetOutcomeRules.requireValidOutcome(
                SetPerformanceInput(
                    reps = set.completedReps,
                    weight = set.completedWeight,
                    assistanceWeight = set.completedAssistanceWeight,
                    durationSeconds = set.completedDurationSeconds,
                    distanceMeters = set.completedDistanceMeters,
                    rpe = set.rpe,
                    rir = set.rir,
                    feltManageable = set.feltManageable,
                    completedAtTimestamp = set.completedAtTimestamp,
                    stoppedAtTimestamp = set.stoppedAtTimestamp,
                    stopReason = set.stopReason,
                    isCompleted = set.isCompleted
                ),
                allowMissingCompletionTimestamp = true
            )
        } catch (error: IllegalArgumentException) {
            invalidValue(error.message ?: "An archived set records a contradictory outcome.")
        }

        requireArchivedSetShape(set)
    }

    private fun requireText(value: String, label: String, maximumLength: Int) {
        if (value.length > maximumLength) {
            invalidValue("The archived $label exceeds $maximumLength characters.")
        }
    }

    /**
     * An identifier a row is addressed by.
     *
     * Sessions, their exercises and their sets are plain data classes with no invariants of
     * their own, so this is the only thing standing between a blank identifier and a
     * restored row nothing can address: logging a set, cancelling a workout and building a
     * workout route all require a non-blank id. Free text such as a name or a note is
     * checked with [requireText] instead, because empty is a legitimate value there.
     */
    private fun requireIdentifier(value: String, label: String) {
        requireText(value, label, MAX_ID_LENGTH)
        if (value.isBlank()) invalidValue("The archived $label is blank.")
    }

    /**
     * A value that is persisted into a "|||"-joined column.
     *
     * The archive is the trust boundary for these: a restored value containing the separator
     * would silently become two entries the next time the column is read, so it is refused
     * here rather than quietly changing meaning after the restore succeeds.
     */
    private fun requireJoinableText(value: String, label: String, maximumLength: Int) {
        requireText(value, label, maximumLength)
        if (value.contains(PERSISTED_LIST_SEPARATOR)) {
            invalidValue("The archived $label contains the reserved '$PERSISTED_LIST_SEPARATOR' separator.")
        }
        // A blank entry is dropped when the joined column is read back, so restoring one
        // would quietly produce a profile with less in it than the document declared.
        if (value.isBlank()) {
            invalidValue("The archived $label contains a blank entry.")
        }
    }

    private fun requireCollection(values: Collection<*>, label: String) {
        if (values.size > MAX_COLLECTION_ITEMS) {
            tooLarge("The archived $label contains more than $MAX_COLLECTION_ITEMS entries.")
        }
    }

    private fun requireInRange(value: Int, label: String, minimum: Int, maximum: Int) {
        if (value < minimum || value > maximum) outOfRange(label)
    }

    private fun requireInRange(value: Long, label: String, minimum: Long, maximum: Long) {
        if (value < minimum || value > maximum) outOfRange(label)
    }

    private fun requireOptionalInRange(value: Int?, label: String, minimum: Int, maximum: Int) {
        if (value != null) requireInRange(value, label, minimum, maximum)
    }

    private fun requireFinite(value: Double, label: String, minimum: Double, maximum: Double) {
        if (!value.isFinite() || value < minimum || value > maximum) outOfRange(label)
    }

    private fun requireOptionalFinite(
        value: Double?,
        label: String,
        minimum: Double,
        maximum: Double
    ) {
        if (value != null) requireFinite(value, label, minimum, maximum)
    }

    private fun requireTimestamp(value: Long, label: String) {
        requireInRange(value, label, 0L, MAX_TIMESTAMP_MILLIS)
    }

    private fun requireNoDuplicates(values: List<String>, label: String) {
        if (values.size != values.toSet().size) {
            inconsistent("The archived $label repeats a value.")
        }
    }

    private fun inconsistent(message: String): Nothing =
        malformed(message, ArchiveRejection.INCONSISTENT)

    private fun invalidValue(message: String): Nothing =
        malformed(message, ArchiveRejection.INVALID_VALUE)

    private fun tooLarge(message: String): Nothing =
        malformed(message, ArchiveRejection.TOO_LARGE)

    /**
     * The value columns a set of this type is allowed to carry, on both the target and the
     * performed side.
     *
     * These are shape rules, and they hold for every row the app has ever written, including
     * history migrated from before types existed. The logger's stricter completion rules — a
     * completed load set needs a positive load, a completed duration set needs positive
     * seconds — are deliberately not applied: they were introduced later, and re-imposing
     * them here would reject a user's own honest older history.
     */
    private fun requireArchivedSetShape(set: WorkoutSet) {
        fun reject(detail: String): Nothing =
            inconsistent("An archived set records $detail its exercise type cannot carry.")

        val hasWeight = set.targetWeight != null || set.completedWeight != null
        val hasAssistance =
            set.targetAssistanceWeight != null || set.completedAssistanceWeight != null
        val hasDuration =
            set.targetDurationSeconds != null || set.completedDurationSeconds != null
        val hasDistance =
            set.targetDistanceMeters != null || set.completedDistanceMeters != null
        val hasReps = set.targetReps != null || set.completedReps != null

        when (set.exerciseType) {
            ExerciseType.WEIGHT_REPS -> {
                if (hasAssistance) reject("assistance weight")
                if (hasDuration) reject("a duration")
                if (hasDistance) reject("a distance")
            }

            ExerciseType.BODYWEIGHT_REPS -> {
                if (hasWeight || hasAssistance) reject("a load")
                if (hasDuration) reject("a duration")
                if (hasDistance) reject("a distance")
            }

            ExerciseType.ASSISTED_BODYWEIGHT -> {
                if (hasWeight) reject("a load")
                if (hasDuration) reject("a duration")
                if (hasDistance) reject("a distance")
            }

            ExerciseType.DURATION -> {
                if (hasReps) reject("repetitions")
                if (hasWeight || hasAssistance) reject("a load")
                if (hasDistance) reject("a distance")
            }

            ExerciseType.DISTANCE_DURATION -> {
                if (hasReps) reject("repetitions")
                if (hasWeight || hasAssistance) reject("a load")
            }
        }
    }

    private fun requireUniqueIds(ids: List<String>, label: String) {
        if (ids.size != ids.toSet().size) inconsistent("The archive reuses a $label identifier.")
    }

    // ------------------------------------------------------------ json helpers

    private inline fun JsonReader.readObject(label: String, readField: (String) -> Unit) {
        beginObject()
        val seen = mutableSetOf<String>()
        while (hasNext()) {
            val field = nextName()
            if (!seen.add(field)) malformed("The archived $label has a duplicate field.")
            readField(field)
        }
        endObject()
    }

    private inline fun <T> JsonReader.readArray(
        label: String,
        maximumItems: Int,
        readItem: () -> T
    ): List<T> {
        beginArray()
        val items = mutableListOf<T>()
        while (hasNext()) {
            if (items.size >= maximumItems) {
                malformed(
                    "The archived $label contains more than $maximumItems items.",
                    ArchiveRejection.TOO_LARGE
                )
            }
            items.add(readItem())
        }
        endArray()
        return items
    }

    private inline fun <T> JsonReader.readMap(label: String, readValue: () -> T): Map<String, T> {
        beginObject()
        val entries = linkedMapOf<String, T>()
        while (hasNext()) {
            if (entries.size >= MAX_COLLECTION_ITEMS) {
                malformed(
                    "The archived $label contains more than $MAX_COLLECTION_ITEMS entries.",
                    ArchiveRejection.TOO_LARGE
                )
            }
            val key = nextName()
            if (key.isBlank() || key.length > MAX_ID_LENGTH) {
                malformed(
                    "The archived $label has an out-of-range key.",
                    ArchiveRejection.INVALID_VALUE
                )
            }
            if (entries.put(key, readValue()) != null) {
                malformed("The archived $label has a duplicate key.")
            }
        }
        endObject()
        return entries
    }

    private inline fun <reified T : Enum<T>> JsonReader.nextEnum(label: String): T {
        val raw = nextBoundedString(label, MAX_SHORT_TEXT_LENGTH)
        return enumValues<T>().firstOrNull { it.name == raw }
            ?: malformed(
                "The archived $label is not a value this build supports.",
                ArchiveRejection.INVALID_VALUE
            )
    }

    private fun JsonReader.nextBoundedString(label: String, maximumLength: Int): String {
        val value = nextString()
        if (value.length > maximumLength) {
            malformed(
                "The archived $label exceeds $maximumLength characters.",
                ArchiveRejection.INVALID_VALUE
            )
        }
        return value
    }

    // The platform reader reports a bad number by quoting the offending literal and its
    // JSON path. That text must not escape, so every numeric read replaces it with a
    // field-named message of this codec's own.
    private fun JsonReader.nextBoundedInt(label: String, minimum: Int, maximum: Int): Int {
        val value = try {
            nextInt()
        } catch (error: NumberFormatException) {
            malformed("The archived $label is not a whole number.", ArchiveRejection.INVALID_VALUE)
        }
        if (value < minimum || value > maximum) outOfRange(label)
        return value
    }

    private fun JsonReader.nextBoundedLong(label: String, minimum: Long, maximum: Long): Long {
        val value = try {
            nextLong()
        } catch (error: NumberFormatException) {
            malformed("The archived $label is not a whole number.", ArchiveRejection.INVALID_VALUE)
        }
        if (value < minimum || value > maximum) outOfRange(label)
        return value
    }

    private fun JsonReader.nextBoundedDouble(label: String, minimum: Double, maximum: Double): Double {
        val value = try {
            nextDouble()
        } catch (error: NumberFormatException) {
            malformed("The archived $label is not a number.", ArchiveRejection.INVALID_VALUE)
        }
        if (!value.isFinite() || value < minimum || value > maximum) outOfRange(label)
        return value
    }

    private fun outOfRange(label: String): Nothing = malformed(
        "The archived $label is outside the supported range.",
        ArchiveRejection.INVALID_VALUE
    )

    private fun JsonReader.nextTimestamp(label: String): Long =
        nextBoundedLong(label, 0L, MAX_TIMESTAMP_MILLIS)

    /**
     * Refuses the archive with a user-facing [rejection] and a precise message for logs.
     *
     * [ArchiveRejection.MALFORMED] is the default because most refusals are structural: a
     * field this build does not know, a missing one, or text that is not the JSON the
     * format defines.
     */
    private fun malformed(
        message: String,
        rejection: ArchiveRejection = ArchiveRejection.MALFORMED
    ): Nothing = throw LocalDataArchiveException(rejection, message)

    /**
     * Ceiling for the canonical body, leaving room for the checksum member the document
     * adds around it, so the finished file still fits inside [MAX_CHARACTERS].
     */
    private const val MAX_CANONICAL_BYTES: Long = MAX_CHARACTERS - 1_000L

    private const val MAX_ORDER_INDEX = 10_000
    private const val MAX_SESSION_MINUTES = 10_080
    private const val MAX_DAYS_PER_WEEK = 7
    private const val MAX_PREFERRED_DURATION_MINUTES = 1_440
    private const val MAX_BREAK_WEEKS = 5_200

    /** The first archive format that can carry recommendation records. */
    private const val RECOMMENDATION_RECORDS_ARCHIVE_VERSION = 2

    /** Far above any authored review policy; a bound, not a product rule. */
    private const val MAX_POLICY_VERSION = 1_000_000

    /** 1970-01-01 and 2100-01-01 as epoch days, matching the archive's timestamp bounds. */
    private const val MIN_WEEK_START_EPOCH_DAY = 0L
    private const val MAX_WEEK_START_EPOCH_DAY = 47_482L

    private const val FIELD_METADATA = "wallcrawlArchive"
    private const val FIELD_DATA = "data"
    private const val FIELD_CHECKSUM = "checksum"

    /** Sink for the checksum pass: the digest sees every byte, only the count is kept. */
    private class CountingSink : java.io.OutputStream() {
        var bytesWritten: Long = 0L
            private set

        override fun write(byte: Int) {
            bytesWritten += 1
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            bytesWritten += length
        }
    }
}
