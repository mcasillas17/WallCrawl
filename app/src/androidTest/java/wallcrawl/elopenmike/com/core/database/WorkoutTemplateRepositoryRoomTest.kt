package wallcrawl.elopenmike.com.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.database.repository.MissingTemplateException
import wallcrawl.elopenmike.com.core.database.repository.OfflineLocalDataBackupRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutTemplateRepository
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate

/** The editor's existence check, run against Room under the gate that deletion also takes. */
@RunWith(AndroidJUnit4::class)
class WorkoutTemplateRepositoryRoomTest {
    private lateinit var database: WallCrawlDatabase
    private val gate = Mutex()

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WallCrawlDatabase::class.java
        ).build()
    }

    @After fun tearDown() = database.close()

    private fun templates() =
        OfflineWorkoutTemplateRepository(database.workoutTemplateDao(), InMemoryExerciseCatalog(), gate)

    private val legs = WorkoutTemplate(
        id = "legs",
        name = "Legs",
        weightUnit = WeightUnit.KG,
        exercises = listOf(
            PlannedExercise(
                "barbell-back-squat",
                ExercisePrescription(
                    exerciseType = ExerciseType.WEIGHT_REPS,
                    targetSets = 3,
                    repRange = RepRange(5, 8),
                    targetWeight = 100.0
                )
            )
        )
    )

    @Test fun anEditIsWrittenOnlyWhileItsTemplateStillExists() = runBlocking {
        val repository = templates()
        repository.saveTemplate(legs, mustExist = false)
        repository.saveTemplate(legs.copy(name = "Legs, heavier"), mustExist = true)
        assertThat(repository.getTemplate("legs")?.name).isEqualTo("Legs, heavier")

        repository.deleteTemplate("legs")
        val refused = runCatching { repository.saveTemplate(legs.copy(name = "Stale edit"), mustExist = true) }
        assertThat(refused.exceptionOrNull()).isInstanceOf(MissingTemplateException::class.java)
        assertThat(repository.getTemplate("legs")).isNull()

        // A new template's save is a create, whatever happened before.
        repository.saveTemplate(legs, mustExist = false)
        assertThat(repository.getTemplate("legs")?.name).isEqualTo("Legs")
    }

    /** Deleting waits for the gate too, so it cannot slip between an edit's check and write. */
    @Test fun deletingATemplateWaitsForTheWriteGate() = runBlocking {
        val repository = templates()
        repository.saveTemplate(legs, mustExist = false)
        gate.lock()
        val deletion = launch { repository.deleteTemplate("legs") }
        // ponytail: a fixed wait, ample for an in-memory delete; without the gate it would be done.
        delay(500)
        assertThat(deletion.isCompleted).isFalse()
        assertThat(repository.getTemplate("legs")).isNotNull()

        gate.unlock()
        deletion.join()
        assertThat(repository.getTemplate("legs")).isNull()
    }

    @Test fun anEditOpenBeforeDeleteAllCannotBringTheTemplateBack() = runBlocking {
        templates().saveTemplate(legs, mustExist = false)
        OfflineLocalDataBackupRepository(database.localDataBackupDao(), "test", 1L, { null }, gate)
            .deleteAllLocalData()

        val refused = runCatching { templates().saveTemplate(legs.copy(name = "Stale edit"), mustExist = true) }
        assertThat(refused.exceptionOrNull()).isInstanceOf(MissingTemplateException::class.java)
        assertThat(templates().getTemplate("legs")).isNull()
    }
}
