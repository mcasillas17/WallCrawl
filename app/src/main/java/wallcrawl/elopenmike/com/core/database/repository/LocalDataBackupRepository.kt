package wallcrawl.elopenmike.com.core.database.repository

import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import wallcrawl.elopenmike.com.core.backup.LocalDataArchive
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveCodec
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveFormat
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveMetadata
import wallcrawl.elopenmike.com.core.backup.LocalDataSnapshot
import wallcrawl.elopenmike.com.core.database.dao.LocalDataBackupDao
import wallcrawl.elopenmike.com.core.database.dao.LocalDataRows
import wallcrawl.elopenmike.com.core.database.PERSISTED_LIST_SEPARATOR
import wallcrawl.elopenmike.com.core.database.WALLCRAWL_SCHEMA_VERSION
import wallcrawl.elopenmike.com.core.database.entity.WorkoutExerciseEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSessionEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSetEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutTemplateEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutTemplateExerciseEntity
import wallcrawl.elopenmike.com.core.database.relation.WorkoutExerciseWithSets
import wallcrawl.elopenmike.com.core.database.relation.WorkoutSessionWithExercisesAndSets
import wallcrawl.elopenmike.com.core.database.relation.WorkoutTemplateWithExercises
import wallcrawl.elopenmike.com.core.database.relation.toWorkoutSession
import wallcrawl.elopenmike.com.core.database.relation.toWorkoutTemplate
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate

/** What an export produced, for the message shown once the document is safely written. */
data class LocalDataExportResult(
    val sessionCount: Int,
    val templateCount: Int
)

/** What a restore put back, and where the app should go next. */
data class LocalDataRestoreResult(
    val onboardingCompleted: Boolean,
    val sessionCount: Int,
    val templateCount: Int
)

/**
 * User-owned export, restore, and deletion of everything WallCrawl stores locally.
 *
 * ## Restore policy, version 1
 *
 * A restore only ever writes into an **empty destination**: no workouts, no templates, and
 * onboarding not completed. There is no merge and no replace. That is a deliberate first
 * release choice — merging two histories needs conflict rules for identifiers, active
 * workouts, and units that nothing in the app can currently decide, and replacing would let
 * one tap destroy data the user never chose to lose.
 *
 * Because deletion returns the app to a fresh start, the supported recovery route is
 * explicit: **export, then delete all local data, then restore**. Restore is reachable from
 * onboarding so a fresh install or a just-cleared install can use it without first being
 * forced to build a profile it is about to discard.
 *
 * ## Active workouts
 *
 * An in-progress workout is part of the archive and is restored as it was. Only one workout
 * may be active, so an archive carrying more than one is rejected before anything is
 * written. A workout in progress is never silently dropped from an export.
 *
 * ## Derived data
 *
 * The weekly dose ledger cache is never exported and never restored. It is cleared as part
 * of both restore and deletion, and recomputed from restored completed history the next
 * time it is read, so a restored install can never serve a count its history does not
 * support.
 */
interface LocalDataBackupRepository {

    /** Whether this installation currently accepts a restore. */
    suspend fun isRestoreAllowed(): Boolean

    /**
     * Writes a complete archive to the stream [openOutput] supplies.
     *
     * [openOutput] is called only once the archive has passed every check, so a refused
     * export never opens — and therefore never truncates — the destination. The stream is
     * closed before success is reported.
     */
    suspend fun exportTo(openOutput: () -> OutputStream): LocalDataExportResult

    /**
     * Validates [input] completely, then restores it in one transaction, or changes nothing.
     *
     * @throws wallcrawl.elopenmike.com.core.backup.LocalDataArchiveException if the archive
     *   is unusable.
     * @throws IllegalStateException if the destination is not empty.
     */
    suspend fun restoreFrom(input: InputStream): LocalDataRestoreResult

    /** Removes every user-owned row and every derived cache row. */
    suspend fun deleteAllLocalData()
}

/**
 * @param localDataWriteGate the gate every local-data mutation takes.
 *
 * Because the profile and template repositories take the same gate, an ordinary write that
 * was already in flight cannot land after a deletion and resurrect data the user just
 * erased. Workout writes need no gate: creating a session is refused inside its own
 * transaction when the profile revision it was started against no longer exists, and every
 * set or completion write is an UPDATE that matches nothing once history is gone.
 *
 * The gate is held for database work only, never across document I/O: the user's chosen
 * destination may be slow, and no ordinary app write should wait on it.
 */
class OfflineLocalDataBackupRepository(
    private val backupDao: LocalDataBackupDao,
    private val appVersionName: String,
    private val appVersionCode: Long,
    private val catalogCommit: () -> String?,
    private val localDataWriteGate: Mutex = Mutex(),
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) : LocalDataBackupRepository {

    override suspend fun isRestoreAllowed(): Boolean = backupDao.isEmptyDestination()

    override suspend fun exportTo(openOutput: () -> OutputStream): LocalDataExportResult {
        // No gate. `readAll()` is itself a transaction, so the snapshot is one point in
        // time whatever else is running, and export mutates nothing. Taking the shared gate
        // here would only make every profile and template write queue behind a full-history
        // read and then behind a document write the user's provider controls the speed of.
        val snapshot = readSnapshot()
        val metadata = LocalDataArchiveMetadata(
            archiveVersion = LocalDataArchiveFormat.ARCHIVE_VERSION,
            createdAtEpochMillis = currentTimeMillis(),
            appVersionName = appVersionName,
            appVersionCode = appVersionCode,
            catalogCommit = catalogCommit(),
            roomSchemaVersion = WALLCRAWL_SCHEMA_VERSION
        )
        LocalDataArchiveCodec.write(LocalDataArchive(metadata, snapshot), openOutput)
        return LocalDataExportResult(
            sessionCount = snapshot.sessions.size,
            templateCount = snapshot.templates.size
        )
    }

    override suspend fun restoreFrom(input: InputStream): LocalDataRestoreResult {
        // Reading and validating the document is pure in-memory work over the caller's
        // stream, so it happens outside the gate for the same reason as the export write.
        // No statement runs against the database until the archive has passed every check.
        val archive = LocalDataArchiveCodec.read(input)
        val rows = archive.snapshot.toRows()
        // The gate covers only the write, and the transaction rechecks eligibility inside
        // itself, so a second restore that got this far while the first was committing is
        // refused rather than merged.
        localDataWriteGate.withLock { backupDao.restoreIntoEmptyDestination(rows) }
        return LocalDataRestoreResult(
            onboardingCompleted = archive.snapshot.profile?.onboardingCompleted == true,
            sessionCount = archive.snapshot.sessions.size,
            templateCount = archive.snapshot.templates.size
        )
    }

    override suspend fun deleteAllLocalData() = localDataWriteGate.withLock {
        backupDao.deleteAll()
    }

    private suspend fun readSnapshot(): LocalDataSnapshot {
        val rows = backupDao.readAll()
        check(rows.profiles.size <= 1) {
            "The local database holds more than one profile row."
        }

        val templateExercisesByTemplate = rows.templateExercises.groupBy { it.templateId }
        val templates = rows.templates.map { template ->
            WorkoutTemplateWithExercises(
                template = template,
                exercises = templateExercisesByTemplate[template.id].orEmpty()
            ).toWorkoutTemplate()
        }

        val setsByExercise = rows.sets.groupBy { it.workoutExerciseId }
        val exercisesBySession = rows.sessionExercises.groupBy { it.sessionId }
        val sessions = rows.sessions.map { session ->
            WorkoutSessionWithExercisesAndSets(
                session = session,
                exercisesWithSets = exercisesBySession[session.id].orEmpty().map { exercise ->
                    WorkoutExerciseWithSets(
                        exercise = exercise,
                        sets = setsByExercise[exercise.id].orEmpty()
                    )
                }
            ).toWorkoutSession()
        }

        return LocalDataSnapshot(
            profile = rows.profiles.firstOrNull()?.toUserProfile(),
            templates = templates,
            sessions = sessions
        )
    }

    private fun LocalDataSnapshot.toRows(): LocalDataRows = LocalDataRows(
        profiles = listOfNotNull(profile?.toUserProfileEntity()),
        templates = templates.map { it.toTemplateEntity() },
        templateExercises = templates.flatMap { it.toTemplateExerciseEntities() },
        sessions = sessions.map { it.toSessionEntity() },
        sessionExercises = sessions.flatMap { session ->
            session.exercises.map { exercise -> exercise.toExerciseEntity() }
        },
        sets = sessions.flatMap { session ->
            session.exercises.flatMap { exercise ->
                exercise.sets.map { set -> set.toSetEntity() }
            }
        }
    )

    private fun WorkoutTemplate.toTemplateEntity() = WorkoutTemplateEntity(
        id = id,
        name = name,
        notes = notes,
        createdAtTimestamp = createdAtTimestamp,
        updatedAtTimestamp = updatedAtTimestamp
    )

    private fun WorkoutTemplate.toTemplateExerciseEntities() =
        exercises.mapIndexed { index, exercise ->
            val prescription = exercise.prescription
            WorkoutTemplateExerciseEntity(
                templateId = id,
                orderIndex = index,
                exerciseId = exercise.exerciseId,
                exerciseType = prescription.exerciseType,
                targetSets = prescription.targetSets,
                targetRepMin = prescription.repRange?.min,
                targetRepMax = prescription.repRange?.max,
                targetWeight = prescription.targetWeight,
                targetAssistanceWeight = prescription.targetAssistanceWeight,
                targetDurationSeconds = prescription.targetDurationSeconds,
                targetDistanceMeters = prescription.targetDistanceMeters,
                restSeconds = prescription.restSeconds,
                notes = exercise.notes,
                effortMinRir = prescription.effortTarget?.minRir,
                effortMaxRir = prescription.effortTarget?.maxRir,
                restClass = prescription.restClass,
                restTargetSource = prescription.restTargetSource
            )
        }

    private fun WorkoutSession.toSessionEntity() = WorkoutSessionEntity(
        id = id,
        name = name,
        startedAtTimestamp = startedAtTimestamp,
        completedAtTimestamp = completedAtTimestamp,
        targetDurationMinutes = targetDurationMinutes,
        actualDurationMinutes = actualDurationMinutes,
        // The archived unit is restored as recorded. A session is never relabelled with the
        // unit the profile happens to prefer now.
        weightUnit = weightUnit,
        status = status,
        origin = origin,
        sourceTemplateId = sourceTemplateId,
        focusMusclesJson = focusMuscles.joinToString(PERSISTED_LIST_SEPARATOR),
        notes = notes
    )

    private fun WorkoutExercise.toExerciseEntity() =
        WorkoutExerciseEntity(
            id = id,
            sessionId = sessionId,
            exerciseId = exerciseId,
            orderIndex = orderIndex,
            exerciseType = prescription.exerciseType,
            targetSets = prescription.targetSets,
            targetRepMin = prescription.repRange?.min,
            targetRepMax = prescription.repRange?.max,
            targetWeight = prescription.targetWeight,
            targetAssistanceWeight = prescription.targetAssistanceWeight,
            targetDurationSeconds = prescription.targetDurationSeconds,
            targetDistanceMeters = prescription.targetDistanceMeters,
            restSeconds = prescription.restSeconds,
            notes = notes,
            effortMinRir = prescription.effortTarget?.minRir,
            effortMaxRir = prescription.effortTarget?.maxRir,
            restClass = prescription.restClass,
            restTargetSource = prescription.restTargetSource
        )

    private fun WorkoutSet.toSetEntity() = WorkoutSetEntity(
        id = id,
        workoutExerciseId = workoutExerciseId,
        setNumber = setNumber,
        exerciseType = exerciseType,
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
        isCompleted = isCompleted,
        rpe = rpe,
        rir = rir,
        feltManageable = feltManageable,
        completedAtTimestamp = completedAtTimestamp,
        stoppedAtTimestamp = stoppedAtTimestamp,
        stopReason = stopReason,
        type = type
    )
}
