package wallcrawl.elopenmike.com.core.exercise

import com.google.common.truth.Truth.assertThat
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ExerciseCatalogTest {

    private lateinit var catalog: InMemoryExerciseCatalog

    @Before
    fun setup() {
        catalog = InMemoryExerciseCatalog()
    }

    @Test
    fun getExerciseById_returnsCorrectExercise() = runTest {
        val exercise = catalog.getExerciseById("incline-dumbbell-press")
        assertThat(exercise).isNotNull()
        assertThat(exercise?.name).isEqualTo("Incline Dumbbell Press")
        assertThat(exercise?.primaryMuscles).contains(StandardMuscles.CHEST)
    }

    @Test
    fun getExerciseById_nonExistentId_returnsNull() = runTest {
        val exercise = catalog.getExerciseById("flying-spider-kick-press")
        assertThat(exercise).isNull()
    }

    @Test
    fun searchExercises_byQuery_filtersCorrectly() = runTest {
        val results = catalog.searchExercises(query = "press").first()
        assertThat(results).isNotEmpty()
        assertThat(results.all { it.name.contains("Press", ignoreCase = true) || it.primaryMuscles.any { m -> m.contains("press", true) } }).isTrue()
    }

    @Test
    fun searchExercises_byMuscle_filtersCorrectly() = runTest {
        val chestExercises = catalog.searchExercises(muscle = StandardMuscles.CHEST).first()
        assertThat(chestExercises).isNotEmpty()
        assertThat(chestExercises.all {
            it.primaryMuscles.contains(StandardMuscles.CHEST) || it.secondaryMuscles.contains(StandardMuscles.CHEST)
        }).isTrue()
    }

    @Test
    fun searchExercises_byEquipment_filtersCorrectly() = runTest {
        val barbellOnly = catalog.searchExercises(equipment = StandardEquipment.BARBELL).first()
        assertThat(barbellOnly).isNotEmpty()
        assertThat(barbellOnly.all { it.equipment.contains(StandardEquipment.BARBELL) }).isTrue()
    }
}
