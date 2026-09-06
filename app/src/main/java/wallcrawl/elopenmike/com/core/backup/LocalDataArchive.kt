package wallcrawl.elopenmike.com.core.backup

import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate

/**
 * The versioned contract for a user-owned WallCrawl export.
 *
 * ## What this is
 *
 * An archive is a plain JSON document the user owns and stores wherever they choose. It is
 * deliberately **not** a copy of the live SQLite database and its journal sidecars: those are
 * an implementation detail whose meaning changes with every Room migration, and copying them
 * while the app is running can capture a torn state.
 *
 * ## Versioning
 *
 * [ARCHIVE_VERSION] describes the document format and is independent of the Room schema
 * version, which is recorded separately as provenance. A reader accepts only the exact
 * archive version it implements: a higher version is refused rather than partially
 * understood, because a future format may attach meaning to fields this build would drop.
 *
 * ## What it contains
 *
 * Everything the user owns: the profile with its preferences and movement capabilities,
 * saved templates, and every workout session with its planned prescriptions, performed set
 * values, units, effort feedback, timestamps, stop reasons, and status. Derived caches
 * (currently the weekly dose ledger) are never exported; they are rebuilt from restored
 * history instead of being trusted.
 *
 * ## Sensitivity
 *
 * An archive is readable personal information: training history, physical capability
 * answers, and body-adjacent preferences in clear text. The checksum detects corruption or
 * accidental modification. It is **not** encryption and **not** proof of authenticity;
 * anyone who edits an archive can recompute it. Users choose where an archive is written,
 * and a Storage Access Framework destination may be a cloud-backed provider.
 */
object LocalDataArchiveFormat {

    /** The only archive format this build writes and accepts. */
    const val ARCHIVE_VERSION: Int = 1

    /** Checksum algorithm recorded in, and required by, a version 1 archive. */
    const val CHECKSUM_ALGORITHM: String = "SHA-256"

    /** MIME type used for both the created and the opened document. */
    const val MIME_TYPE: String = "application/json"

    /** Suffix of the suggested export filename. */
    const val FILE_EXTENSION: String = ".wallcrawl.json"
}

/**
 * Bounds every archive is read and written under.
 *
 * These are resource limits, not product rules. They exist so an untrusted document cannot
 * exhaust memory before validation runs, and they are set far above any plausible local
 * history so an honest export is never refused.
 *
 * [MAX_CHARACTERS] is the binding limit and the only one that can be reached first: the
 * record counts below are ceilings on shape, not a promise that a document of that many
 * records fits. A history large enough to exceed the character bound is refused at export
 * time, by the same check the reader applies, so an export never produces a document that
 * could not be read back.
 */
object LocalDataArchiveLimits {
    const val MAX_CHARACTERS: Long = 16_000_000L

    const val MAX_SESSIONS: Int = 20_000
    const val MAX_TEMPLATES: Int = 2_000
    const val MAX_EXERCISES_PER_SESSION: Int = 100
    const val MAX_SETS_PER_EXERCISE: Int = 100
    const val MAX_TOTAL_SETS: Int = 50_000

    const val MAX_ID_LENGTH: Int = 200
    const val MAX_NAME_LENGTH: Int = 500
    const val MAX_NOTES_LENGTH: Int = 10_000
    const val MAX_SHORT_TEXT_LENGTH: Int = 200
    const val MAX_COLLECTION_ITEMS: Int = 2_000

    /**
     * Upper bound for any archived weight, in the unit the archive already records.
     * It is deliberately looser than the logging limit so history logged under an older,
     * different bound still restores exactly as it was recorded.
     */
    const val MAX_WEIGHT: Double = 1_000_000.0
    const val MAX_DISTANCE_METERS: Double = 10_000_000.0
    const val MAX_DURATION_SECONDS: Int = 604_800
    const val MAX_REPETITIONS: Int = 100_000
    const val MAX_TIMESTAMP_MILLIS: Long = 4_102_444_800_000L // 2100-01-01T00:00:00Z
}

/**
 * Provenance recorded with an archive.
 *
 * None of these fields decide whether an archive can be restored except [archiveVersion].
 * The rest exist so a user or a maintainer can tell what produced a document.
 */
data class LocalDataArchiveMetadata(
    val archiveVersion: Int,
    val createdAtEpochMillis: Long,
    val appVersionName: String,
    val appVersionCode: Long,
    val roomSchemaVersion: Int,
    /** Commit of the bundled catalog the archived exercise ids came from, when known. */
    val catalogCommit: String?
)

/**
 * The user-owned records in an archive.
 *
 * The profile is nullable because an archive may be written before onboarding completes.
 * Sessions carry their own exercises and sets, so the parent/child relationships are part
 * of the document structure rather than something a reader has to reassemble from
 * free-floating foreign keys.
 */
data class LocalDataSnapshot(
    val profile: UserProfile?,
    val templates: List<WorkoutTemplate>,
    val sessions: List<WorkoutSession>
)

/** A complete archive: its provenance and the records it carries. */
data class LocalDataArchive(
    val metadata: LocalDataArchiveMetadata,
    val snapshot: LocalDataSnapshot
)

/**
 * An archive that cannot be trusted or understood.
 *
 * [rejection] is what the user is shown, through resource-backed copy. [message] names the
 * offending field and the rule it broke for logs and tests; it never reaches the screen, so
 * no parser's text — including the platform JSON reader's, which quotes the offending
 * literal — can carry an archived value out of the document.
 */
class LocalDataArchiveException(
    val rejection: ArchiveRejection,
    message: String
) : IllegalArgumentException(message)

/**
 * Why an archive was refused, in the terms a user can act on.
 *
 * The exception's message stays precise for logs and tests, but only this closed set
 * reaches the screen. That keeps rejection copy resource-backed and translatable, the way
 * typed planning failures already are in this codebase, and it means no message produced
 * deep in a parser — including one produced by the platform's JSON reader — can carry an
 * archived value out to the UI.
 */
enum class ArchiveRejection {
    /** Written by a newer WallCrawl than this build understands. */
    UNSUPPORTED_VERSION,

    /** The recorded checksum does not match the contents. */
    CHECKSUM_MISMATCH,

    /** Larger than the reader will accept. */
    TOO_LARGE,

    /** Not a WallCrawl archive, or not well-formed JSON with the expected fields. */
    MALFORMED,

    /** Well-formed, but a value is outside what WallCrawl can represent. */
    INVALID_VALUE,

    /** Well-formed values that contradict each other, such as a reused identifier. */
    INCONSISTENT
}

/**
 * A restore refused because the destination still holds user-owned data.
 *
 * It is an [IllegalStateException] so that being thrown inside the restore transaction
 * rolls that transaction back, and a distinct type so the UI can explain the one thing the
 * user has to do first instead of showing a generic failure.
 */
class LocalDataRestoreRefusedException(message: String) : IllegalStateException(message)
