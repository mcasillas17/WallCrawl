package wallcrawl.elopenmike.com.feature.exercises

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.test.MainDispatcherRule

@OptIn(ExperimentalCoroutinesApi::class)
class ExercisesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiState_catalogFailureBecomesVisibleError() = runTest {
        val viewModel = ExercisesViewModel(FailingExerciseCatalog())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        advanceUntilIdle()

        val state = viewModel.uiState.value as ExercisesUiState.Error
        assertThat(state.message).contains("offline exercise catalog")
    }

    private class FailingExerciseCatalog : ExerciseCatalog {
        private val failure = IllegalStateException("asset parse failed")

        override fun getAllExercises(): Flow<List<Exercise>> = flow { throw failure }
        override suspend fun getExerciseById(id: String): Exercise? = throw failure
        override fun searchExercises(
            query: String,
            muscle: String?,
            equipment: String?
        ): Flow<List<Exercise>> = flow { throw failure }

        override suspend fun getMuscleGroups(): List<String> = throw failure
        override suspend fun getEquipmentTypes(): List<String> = throw failure
    }
}
