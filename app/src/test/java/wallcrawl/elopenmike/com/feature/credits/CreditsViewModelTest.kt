package wallcrawl.elopenmike.com.feature.credits

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import wallcrawl.elopenmike.com.core.exercise.workoutguide.AttributionNotice
import wallcrawl.elopenmike.com.core.exercise.workoutguide.AttributionNoticeSource
import wallcrawl.elopenmike.com.core.exercise.workoutguide.WorkoutGuideCatalogSnapshot
import wallcrawl.elopenmike.com.core.exercise.workoutguide.WorkoutGuideCatalogSource
import wallcrawl.elopenmike.com.core.exercise.workoutguide.testCatalogAttribution

@OptIn(ExperimentalCoroutinesApi::class)
class CreditsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun load_exposesCatalogAttributionAndBundledNotices() = runTest(dispatcher) {
        val viewModel = CreditsViewModel(
            catalogSource = FakeCatalogSource(),
            noticeSource = FakeNoticeSource(
                listOf(AttributionNotice("Attribution", "Everkinetic, CC BY-SA 4.0"))
            )
        )

        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(CreditsUiState.Success::class.java)
        val success = state as CreditsUiState.Success
        assertThat(success.catalog.attribution.license).isEqualTo("CC BY-SA 4.0")
        assertThat(success.catalog.exerciseCount).isEqualTo(302)
        assertThat(success.notices.single().body).contains("Everkinetic")
    }

    @Test
    fun load_reportsFailureInsteadOfShowingNoAttribution() = runTest(dispatcher) {
        val viewModel = CreditsViewModel(
            catalogSource = object : WorkoutGuideCatalogSource {
                override suspend fun snapshot(): WorkoutGuideCatalogSnapshot =
                    throw IllegalStateException("catalog unavailable")

                override fun currentSnapshot(): WorkoutGuideCatalogSnapshot? = null
            },
            noticeSource = FakeNoticeSource(emptyList())
        )

        dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(CreditsUiState.Error::class.java)
        assertThat((state as CreditsUiState.Error).message).contains("catalog unavailable")
    }

    @Test
    fun load_stillCreditsTheCatalogWhenNoticeFilesAreUnreadable() = runTest(dispatcher) {
        val viewModel = CreditsViewModel(
            catalogSource = FakeCatalogSource(),
            noticeSource = FakeNoticeSource(emptyList())
        )

        dispatcher.scheduler.advanceUntilIdle()

        val success = viewModel.uiState.value as CreditsUiState.Success
        assertThat(success.notices).isEmpty()
        assertThat(success.catalog.attribution.creator).isNotEmpty()
    }

    private class FakeCatalogSource : WorkoutGuideCatalogSource {
        private val snapshot = WorkoutGuideCatalogSnapshot(
            exercises = emptyList(),
            framesByExerciseId = emptyMap(),
            catalogAttribution = testCatalogAttribution(exerciseCount = 302, frameCount = 906)
        )

        override suspend fun snapshot(): WorkoutGuideCatalogSnapshot = snapshot
        override fun currentSnapshot(): WorkoutGuideCatalogSnapshot = snapshot
    }

    private class FakeNoticeSource(
        private val notices: List<AttributionNotice>
    ) : AttributionNoticeSource {
        override suspend fun notices(): List<AttributionNotice> = notices
    }
}
