package wallcrawl.elopenmike.com.core.database.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import wallcrawl.elopenmike.com.core.database.dao.WorkoutTemplateDao
import wallcrawl.elopenmike.com.core.database.entity.WorkoutTemplateEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutTemplateExerciseEntity
import wallcrawl.elopenmike.com.core.database.relation.WorkoutTemplateWithExercises
import wallcrawl.elopenmike.com.core.database.relation.persistedEffortTarget
import wallcrawl.elopenmike.com.core.database.relation.requireCompletePersistedRestTarget
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate

interface WorkoutTemplateRepository {
    fun observeTemplates(): Flow<List<WorkoutTemplate>>
    fun observeTemplate(templateId: String): Flow<WorkoutTemplate?>
    suspend fun getTemplate(templateId: String): WorkoutTemplate?
    suspend fun saveTemplate(template: WorkoutTemplate)
    suspend fun deleteTemplate(templateId: String)
}

class OfflineWorkoutTemplateRepository(
    private val templateDao: WorkoutTemplateDao,
    private val exerciseCatalog: ExerciseCatalog
) : WorkoutTemplateRepository {

    override fun observeTemplates(): Flow<List<WorkoutTemplate>> =
        templateDao.observeTemplatesWithExercises().map { templates ->
            templates.map { it.toDomainModel() }
        }

    override fun observeTemplate(templateId: String): Flow<WorkoutTemplate?> {
        requireValidTemplateId(templateId)
        return templateDao.observeTemplateWithExercises(templateId).map { it?.toDomainModel() }
    }

    override suspend fun getTemplate(templateId: String): WorkoutTemplate? {
        requireValidTemplateId(templateId)
        val template = templateDao.getTemplateWithExercises(templateId)?.toDomainModel()
            ?: return null
        validateCatalogReferences(template)
        return template
    }

    override suspend fun saveTemplate(template: WorkoutTemplate) {
        validateCatalogReferences(template)
        templateDao.replaceTemplate(
            template = WorkoutTemplateEntity(
                id = template.id,
                name = template.name.trim(),
                notes = template.notes.trim(),
                createdAtTimestamp = template.createdAtTimestamp,
                updatedAtTimestamp = template.updatedAtTimestamp
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
        templateDao.deleteTemplateById(templateId)
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

    private fun WorkoutTemplateWithExercises.toDomainModel(): WorkoutTemplate = WorkoutTemplate(
        id = template.id,
        name = template.name,
        notes = template.notes,
        createdAtTimestamp = template.createdAtTimestamp,
        updatedAtTimestamp = template.updatedAtTimestamp,
        exercises = exercises.sortedBy { it.orderIndex }.map { exercise ->
            val effortTarget = persistedEffortTarget(
                minRir = exercise.effortMinRir,
                maxRir = exercise.effortMaxRir,
                owner = "Persisted template exercise"
            )
            requireCompletePersistedRestTarget(
                restClass = exercise.restClass,
                restTargetSource = exercise.restTargetSource,
                owner = "Persisted template exercise"
            )
            PlannedExercise(
                exerciseId = exercise.exerciseId,
                prescription = ExercisePrescription(
                    exerciseType = exercise.exerciseType,
                    targetSets = exercise.targetSets,
                    repRange = exercise.targetRepMin?.let { minimum ->
                        RepRange(
                            min = minimum,
                            max = checkNotNull(exercise.targetRepMax) {
                                "Persisted template repetition target is missing its maximum."
                            }
                        )
                    },
                    targetWeight = exercise.targetWeight,
                    targetAssistanceWeight = exercise.targetAssistanceWeight,
                    targetDurationSeconds = exercise.targetDurationSeconds,
                    targetDistanceMeters = exercise.targetDistanceMeters,
                    restSeconds = exercise.restSeconds,
                    effortTarget = effortTarget,
                    restClass = exercise.restClass,
                    restTargetSource = exercise.restTargetSource
                ),
                notes = exercise.notes
            )
        }
    )
}
