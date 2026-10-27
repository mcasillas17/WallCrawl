package wallcrawl.elopenmike.com.core.database.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import wallcrawl.elopenmike.com.core.database.dao.WorkoutTemplateDao
import wallcrawl.elopenmike.com.core.database.entity.WorkoutTemplateEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutTemplateExerciseEntity
import wallcrawl.elopenmike.com.core.database.relation.toWorkoutTemplate
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate

interface WorkoutTemplateRepository {
    fun observeTemplates(): Flow<List<WorkoutTemplate>>
    fun observeTemplate(templateId: String): Flow<WorkoutTemplate?>
    suspend fun getTemplate(templateId: String): WorkoutTemplate?
    /**
     * Writes [template]. With [mustExist], the write is an edit of a template already
     * saved: if it is gone — deleted, or removed with all local data — this throws
     * [MissingTemplateException] rather than writing it back into existence.
     */
    suspend fun saveTemplate(template: WorkoutTemplate, mustExist: Boolean = false)
    suspend fun deleteTemplate(templateId: String)
}

/** An edit was saved for a template that no longer exists. */
class MissingTemplateException(templateId: String) :
    IllegalStateException("Template '$templateId' no longer exists.")

/**
 * @param localDataWriteGate serialises template writes against destructive local-data
 *   operations, so a save that was already in flight cannot land after the user deleted
 *   everything. The default is a private gate for tests; production shares the container's.
 */
class OfflineWorkoutTemplateRepository(
    private val templateDao: WorkoutTemplateDao,
    private val exerciseCatalog: ExerciseCatalog,
    private val localDataWriteGate: Mutex = Mutex()
) : WorkoutTemplateRepository {

    override fun observeTemplates(): Flow<List<WorkoutTemplate>> =
        templateDao.observeTemplatesWithExercises().map { templates ->
            templates.map { it.toWorkoutTemplate() }
        }

    override fun observeTemplate(templateId: String): Flow<WorkoutTemplate?> {
        requireValidTemplateId(templateId)
        return templateDao.observeTemplateWithExercises(templateId).map { it?.toWorkoutTemplate() }
    }

    override suspend fun getTemplate(templateId: String): WorkoutTemplate? {
        requireValidTemplateId(templateId)
        val template = templateDao.getTemplateWithExercises(templateId)?.toWorkoutTemplate()
            ?: return null
        validateCatalogReferences(template)
        return template
    }

    override suspend fun saveTemplate(template: WorkoutTemplate, mustExist: Boolean) {
        // Reading a legacy template is fine; writing a new load without saying what unit it
        // is in would recreate the ambiguity the unit column exists to end.
        require(!template.requiresUnitConfirmation) {
            "A template that carries a load must record its weight unit."
        }
        // Catalog validation may load the bundled catalog, so it happens before the gate is
        // taken rather than blocking a deletion behind asset I/O.
        validateCatalogReferences(template)
        localDataWriteGate.withLock {
            // Checked under the same gate delete-all takes, so the check and the write
            // cannot straddle a deletion.
            if (mustExist && !templateDao.templateExists(template.id)) {
                throw MissingTemplateException(template.id)
            }
            writeTemplate(template)
        }
    }

    private suspend fun writeTemplate(template: WorkoutTemplate) {
        templateDao.replaceTemplate(
            template = WorkoutTemplateEntity(
                id = template.id,
                name = template.name.trim(),
                notes = template.notes.trim(),
                createdAtTimestamp = template.createdAtTimestamp,
                updatedAtTimestamp = template.updatedAtTimestamp,
                weightUnit = template.weightUnit?.name
            ),
            exercises = template.exercises.mapIndexed { index, exercise ->
                val prescription = exercise.prescription
                WorkoutTemplateExerciseEntity(
                    templateId = template.id,
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
                    notes = exercise.notes.trim(),
                    effortMinRir = prescription.effortTarget?.minRir,
                    effortMaxRir = prescription.effortTarget?.maxRir,
                    restClass = prescription.restClass,
                    restTargetSource = prescription.restTargetSource
                )
            }
        )
    }

    override suspend fun deleteTemplate(templateId: String) {
        requireValidTemplateId(templateId)
        // Under the gate an edit's existence check takes, so the two cannot interleave.
        localDataWriteGate.withLock { templateDao.deleteTemplateById(templateId) }
    }

    private suspend fun validateCatalogReferences(template: WorkoutTemplate) {
        val catalogById = exerciseCatalog.getAllExercises().first().associateBy { it.id }
        template.exercises.forEach { exercise ->
            val catalogExercise = requireNotNull(catalogById[exercise.exerciseId]) {
                "Template exercise '${exercise.exerciseId}' does not exist in the catalog."
            }
            require(catalogExercise.type == exercise.prescription.exerciseType) {
                "Template prescription type does not match catalog exercise '${exercise.exerciseId}'."
            }
        }
    }

    private fun requireValidTemplateId(templateId: String) {
        require(templateId.isNotBlank()) { "templateId must not be blank." }
    }
}
