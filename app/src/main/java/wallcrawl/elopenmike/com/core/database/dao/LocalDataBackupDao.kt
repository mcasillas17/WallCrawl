package wallcrawl.elopenmike.com.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import wallcrawl.elopenmike.com.core.backup.LocalDataRestoreRefusedException
import wallcrawl.elopenmike.com.core.database.entity.UserProfileEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutExerciseEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSessionEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSetEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutTemplateEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutTemplateExerciseEntity

/** Every user-owned row, in a deterministic order, as one consistent read. */
data class LocalDataRows(
    val profiles: List<UserProfileEntity>,
    val templates: List<WorkoutTemplateEntity>,
    val templateExercises: List<WorkoutTemplateExerciseEntity>,
    val sessions: List<WorkoutSessionEntity>,
    val sessionExercises: List<WorkoutExerciseEntity>,
    val sets: List<WorkoutSetEntity>
)

/**
 * Whole-database reads and writes for user-owned export, restore, and deletion.
 *
 * This DAO deliberately sits beside the feature repositories rather than inside them:
 * export needs one consistent view across every table, and restore and deletion have to
 * span every table in a single transaction. Ordinary feature code keeps using its own
 * repository, which cannot reach any of the statements here.
 *
 * The derived weekly-ledger cache is never read into an archive and never restored from
 * one. It is cleared whenever the history it summarises changes, and rebuilt on demand
 * from restored completed history.
 */
@Dao
interface LocalDataBackupDao {

    @Query("SELECT * FROM user_profiles ORDER BY id ASC")
    suspend fun selectProfiles(): List<UserProfileEntity>

    @Query("SELECT * FROM workout_templates ORDER BY id ASC")
    suspend fun selectTemplates(): List<WorkoutTemplateEntity>

    @Query("SELECT * FROM workout_template_exercises ORDER BY templateId ASC, orderIndex ASC")
    suspend fun selectTemplateExercises(): List<WorkoutTemplateExerciseEntity>

    @Query("SELECT * FROM workout_sessions ORDER BY startedAtTimestamp ASC, id ASC")
    suspend fun selectSessions(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM workout_exercises ORDER BY sessionId ASC, orderIndex ASC, id ASC")
    suspend fun selectSessionExercises(): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM workout_sets ORDER BY workoutExerciseId ASC, setNumber ASC, id ASC")
    suspend fun selectSets(): List<WorkoutSetEntity>

    /**
     * One transactionally consistent snapshot of every user-owned table.
     *
     * Reading each table in its own statement would let a set logged between two of those
     * statements appear without its parent exercise, or a session appear with a set count
     * that no longer matches. The transaction makes the six reads one point in time.
     */
    @Transaction
    suspend fun readAll(): LocalDataRows = LocalDataRows(
        profiles = selectProfiles(),
        templates = selectTemplates(),
        templateExercises = selectTemplateExercises(),
        sessions = selectSessions(),
        sessionExercises = selectSessionExercises(),
        sets = selectSets()
    )

    @Query("SELECT COUNT(*) FROM workout_sessions")
    suspend fun countSessions(): Int

    @Query("SELECT COUNT(*) FROM workout_templates")
    suspend fun countTemplates(): Int

    @Query("SELECT COUNT(*) FROM user_profiles WHERE onboardingCompleted = 1")
    suspend fun countOnboardedProfiles(): Int

    /**
     * Whether this installation still looks like a fresh start.
     *
     * "Empty" means no workouts, no templates, and no completed onboarding. A profile row
     * that exists only because something read the profile before onboarding finished is a
     * bootstrap default, not user-owned data, so it does not block a restore.
     */
    @Transaction
    suspend fun isEmptyDestination(): Boolean =
        countSessions() == 0 && countTemplates() == 0 && countOnboardedProfiles() == 0

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProfile(profile: UserProfileEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTemplates(templates: List<WorkoutTemplateEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTemplateExercises(exercises: List<WorkoutTemplateExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSessions(sessions: List<WorkoutSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSessionExercises(exercises: List<WorkoutExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSets(sets: List<WorkoutSetEntity>)

    @Query("DELETE FROM workout_sets")
    suspend fun deleteSets()

    @Query("DELETE FROM workout_exercises")
    suspend fun deleteSessionExercises()

    @Query("DELETE FROM workout_sessions")
    suspend fun deleteSessions()

    @Query("DELETE FROM workout_template_exercises")
    suspend fun deleteTemplateExercises()

    @Query("DELETE FROM workout_templates")
    suspend fun deleteTemplates()

    @Query("DELETE FROM user_profiles")
    suspend fun deleteProfiles()

    @Query("DELETE FROM weekly_dose_ledger_state")
    suspend fun deleteLedgerCache()

    /**
     * Removes every user-owned row and every derived cache row in one transaction.
     *
     * Children are deleted before their parents so the outcome does not depend on cascade
     * configuration. Bundled catalog data and artwork are application assets and are not
     * reachable from here at all.
     */
    @Transaction
    suspend fun deleteAll() {
        deleteSets()
        deleteSessionExercises()
        deleteSessions()
        deleteTemplateExercises()
        deleteTemplates()
        deleteProfiles()
        deleteLedgerCache()
    }

    /**
     * Restores validated rows into an empty destination, or changes nothing at all.
     *
     * Eligibility is rechecked here, inside the transaction, because the check a caller ran
     * earlier could have been invalidated by a concurrent write. Throwing rolls the whole
     * transaction back, so a refused or failed restore leaves the database exactly as it
     * was — never half a history.
     *
     * The bootstrap profile row, if one exists, is removed first: it is the placeholder this
     * restore replaces, and leaving it would collide with the archived profile.
     */
    @Transaction
    suspend fun restoreIntoEmptyDestination(rows: LocalDataRows) {
        if (!isEmptyDestination()) {
            throw LocalDataRestoreRefusedException(
                "Restoring needs an installation with no workouts, no templates, and " +
                    "no completed onboarding."
            )
        }
        deleteProfiles()
        deleteLedgerCache()

        rows.profiles.forEach { profile -> insertProfile(profile) }
        insertTemplates(rows.templates)
        insertTemplateExercises(rows.templateExercises)
        insertSessions(rows.sessions)
        insertSessionExercises(rows.sessionExercises)
        insertSets(rows.sets)
    }
}
