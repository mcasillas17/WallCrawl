package wallcrawl.elopenmike.com.core.database.repository

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import wallcrawl.elopenmike.com.core.database.dao.WorkoutTemplateDao
import wallcrawl.elopenmike.com.core.database.entity.WorkoutTemplateEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutTemplateExerciseEntity
import wallcrawl.elopenmike.com.core.database.relation.WorkoutTemplateWithExercises
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate

class WorkoutTemplateRepositoryTest {

    private val catalog = InMemoryExerciseCatalog(
        listOf(
            exercise("assisted-pull-up", ExerciseType.ASSISTED_BODYWEIGHT),
            exercise("push-up", ExerciseType.BODYWEIGHT_REPS),
            exercise("plank", ExerciseType.DURATION)
        )
    )

    @Test
    fun saveTemplate_mapsEveryTypeAwarePrescriptionField() = runTest {
        val dao = RecordingWorkoutTemplateDao()
        val repository = OfflineWorkoutTemplateRepository(dao, catalog)
        val template = WorkoutTemplate(
            id = "template",
            name = "Assisted Pull",
            notes = "Technique first",
            createdAtTimestamp = 1_000L,
            updatedAtTimestamp = 2_000L,
            exercises = listOf(
                PlannedExercise(
                    exerciseId = "assisted-pull-up",
                    prescription = ExercisePrescription(
                        exerciseType = ExerciseType.ASSISTED_BODYWEIGHT,
                        targetSets = 4,
                        repRange = RepRange(6, 10),
                        targetAssistanceWeight = 35.0,
                        restSeconds = 120
                    ),
                    notes = "Full range"
                )
            )
        )

        repository.saveTemplate(template)

        assertThat(dao.replacedTemplate?.name).isEqualTo("Assisted Pull")
        val exercise = dao.replacedExercises.single()
        assertThat(exercise.exerciseType).isEqualTo(ExerciseType.ASSISTED_BODYWEIGHT)
        assertThat(exercise.targetSets).isEqualTo(4)
        assertThat(exercise.targetRepMin).isEqualTo(6)
        assertThat(exercise.targetRepMax).isEqualTo(10)
        assertThat(exercise.targetAssistanceWeight).isEqualTo(35.0)
        assertThat(exercise.restSeconds).isEqualTo(120)
        assertThat(exercise.notes).isEqualTo("Full range")
    }

    @Test
    fun observeTemplates_restoresPersistedExerciseOrder() = runTest {
        val dao = RecordingWorkoutTemplateDao(
            stored = listOf(
                WorkoutTemplateWithExercises(
                    template = WorkoutTemplateEntity(
                        id = "template",
                        name = "Mixed",
                        notes = "",
                        createdAtTimestamp = 1_000L,
                        updatedAtTimestamp = 1_000L
                    ),
                    exercises = listOf(
                        durationEntity(orderIndex = 1, exerciseId = "plank"),
                        repetitionEntity(orderIndex = 0, exerciseId = "push-up")
                    )
                )
            )
        )
        val repository = OfflineWorkoutTemplateRepository(dao, catalog)

        val template = repository.observeTemplates().first().single()

        assertThat(template.exercises.map { it.exerciseId })
            .containsExactly("push-up", "plank")
            .inOrder()
        assertThat(template.exercises[1].prescription.targetDurationSeconds).isEqualTo(45)
    }

    @Test
    fun getTemplate_rejectsAStaleExerciseThatIsNoLongerInTheCatalog() {
        val dao = RecordingWorkoutTemplateDao(
            stored = listOf(
                WorkoutTemplateWithExercises(
                    template = WorkoutTemplateEntity(
                        id = "template",
                        name = "Stale",
                        notes = "",
                        createdAtTimestamp = 1_000L,
                        updatedAtTimestamp = 1_000L
                    ),
                    exercises = listOf(
                        repetitionEntity(orderIndex = 0, exerciseId = "removed-exercise")
                    )
                )
            )
        )
        val repository = OfflineWorkoutTemplateRepository(dao, catalog)

        assertThrows(IllegalArgumentException::class.java) {
            runTest { repository.getTemplate("template") }
        }
    }

    private fun repetitionEntity(orderIndex: Int, exerciseId: String) =
        WorkoutTemplateExerciseEntity(
            templateId = "template",
            orderIndex = orderIndex,
            exerciseId = exerciseId,
            exerciseType = ExerciseType.BODYWEIGHT_REPS,
            targetSets = 3,
            targetRepMin = 8,
            targetRepMax = 12,
            targetWeight = null,
            targetAssistanceWeight = null,
            targetDurationSeconds = null,
            targetDistanceMeters = null,
            restSeconds = 75,
            notes = ""
        )

    private fun durationEntity(orderIndex: Int, exerciseId: String) =
        WorkoutTemplateExerciseEntity(
            templateId = "template",
            orderIndex = orderIndex,
            exerciseId = exerciseId,
            exerciseType = ExerciseType.DURATION,
            targetSets = 3,
            targetRepMin = null,
            targetRepMax = null,
            targetWeight = null,
            targetAssistanceWeight = null,
            targetDurationSeconds = 45,
            targetDistanceMeters = null,
            restSeconds = 45,
            notes = ""
        )

    private fun exercise(id: String, type: ExerciseType) = Exercise(
        id = id,
        name = id,
        primaryMuscles = listOf("Core"),
        listedEquipment = listOf("Bodyweight"),
        type = type
    )
}

private class RecordingWorkoutTemplateDao(
    private val stored: List<WorkoutTemplateWithExercises> = emptyList()
) : WorkoutTemplateDao {
    var replacedTemplate: WorkoutTemplateEntity? = null
    var replacedExercises: List<WorkoutTemplateExerciseEntity> = emptyList()

    override fun observeTemplatesWithExercises(): Flow<List<WorkoutTemplateWithExercises>> =
        flowOf(stored)

    override fun observeTemplateWithExercises(
        templateId: String
    ): Flow<WorkoutTemplateWithExercises?> = flowOf(stored.singleOrNull { it.template.id == templateId })

    override suspend fun getTemplateWithExercises(templateId: String): WorkoutTemplateWithExercises? =
        stored.singleOrNull { it.template.id == templateId }

    override suspend fun getExercisesForTemplate(
        templateId: String
    ): List<WorkoutTemplateExerciseEntity> = stored
        .singleOrNull { it.template.id == templateId }
        ?.exercises
        .orEmpty()

    override suspend fun upsertTemplate(template: WorkoutTemplateEntity) = Unit
    override suspend fun insertTemplateExercises(exercises: List<WorkoutTemplateExerciseEntity>) = Unit
    override suspend fun deleteExercisesForTemplate(templateId: String) = Unit
    override suspend fun deleteTemplateById(templateId: String): Int = 1

    override suspend fun replaceTemplate(
        template: WorkoutTemplateEntity,
        exercises: List<WorkoutTemplateExerciseEntity>
    ) {
        replacedTemplate = template
        replacedExercises = exercises
    }
}
