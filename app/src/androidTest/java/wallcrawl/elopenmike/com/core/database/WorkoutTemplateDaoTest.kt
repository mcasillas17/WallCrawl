package wallcrawl.elopenmike.com.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.database.entity.WorkoutTemplateEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutTemplateExerciseEntity
import wallcrawl.elopenmike.com.core.model.ExerciseType

@RunWith(AndroidJUnit4::class)
class WorkoutTemplateDaoTest {

    private lateinit var database: WallCrawlDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WallCrawlDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun replaceTemplate_atomicallyReplacesOrderedExercises() = runBlocking {
        val dao = database.workoutTemplateDao()
        val template = templateEntity()
        dao.replaceTemplate(
            template,
            listOf(
                repetitionExercise(template.id, 0, "push-up"),
                durationExercise(template.id, 1, "plank")
            )
        )

        dao.replaceTemplate(
            template.copy(name = "Short Push", updatedAtTimestamp = 2_000L),
            listOf(durationExercise(template.id, 0, "plank"))
        )

        val persisted = dao.observeTemplatesWithExercises().first().single()
        assertThat(persisted.template.name).isEqualTo("Short Push")
        assertThat(persisted.exercises.map { it.exerciseId }).containsExactly("plank")
        assertThat(persisted.exercises.single().targetDurationSeconds).isEqualTo(45)
    }

    @Test
    fun deleteTemplate_cascadesToExercises() = runBlocking {
        val dao = database.workoutTemplateDao()
        val template = templateEntity()
        dao.replaceTemplate(template, listOf(repetitionExercise(template.id, 0, "push-up")))

        assertThat(dao.deleteTemplateById(template.id)).isEqualTo(1)

        assertThat(dao.getTemplateWithExercises(template.id)).isNull()
        assertThat(dao.getExercisesForTemplate(template.id)).isEmpty()
    }

    private fun templateEntity() = WorkoutTemplateEntity(
        id = "template",
        name = "Push",
        notes = "",
        createdAtTimestamp = 1_000L,
        updatedAtTimestamp = 1_000L
    )

    private fun repetitionExercise(
        templateId: String,
        orderIndex: Int,
        exerciseId: String
    ) = WorkoutTemplateExerciseEntity(
        templateId = templateId,
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

    private fun durationExercise(
        templateId: String,
        orderIndex: Int,
        exerciseId: String
    ) = WorkoutTemplateExerciseEntity(
        templateId = templateId,
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
}
