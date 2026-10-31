package wallcrawl.elopenmike.com.feature.progress

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.core.database.repository.AnalyticsRangeTooLargeException
import wallcrawl.elopenmike.com.core.database.repository.ProgressAnalyticsSnapshot
import wallcrawl.elopenmike.com.core.database.repository.ProgressRepository
import wallcrawl.elopenmike.com.core.database.repository.ProgressSnapshot
import wallcrawl.elopenmike.com.core.database.repository.RecordedSessionDecision
import wallcrawl.elopenmike.com.core.model.AnalyticsMetric
import wallcrawl.elopenmike.com.core.model.AnalyticsRange
import wallcrawl.elopenmike.com.core.model.AnalyticsScope
import wallcrawl.elopenmike.com.core.model.AnalyticsSelection
import wallcrawl.elopenmike.com.core.model.DeloadReasonCode
import wallcrawl.elopenmike.com.core.model.DeloadRecommendationProvenance
import wallcrawl.elopenmike.com.core.model.DeloadSource
import wallcrawl.elopenmike.com.core.model.ProgressAnalytics
import wallcrawl.elopenmike.com.core.model.ProgressionProvenance
import wallcrawl.elopenmike.com.core.model.ProgressionReason
import wallcrawl.elopenmike.com.core.model.ProgressionReasonCode
import wallcrawl.elopenmike.com.core.model.RecommendationRecord
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.test.MainDispatcherRule

/** Filter state is bounded saved state; reads follow the latest selection only. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressAnalyticsViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun defaultsAndRestoredSelectionDriveTheRead() = runTest {
        val repository = FakeRepository()
        val fresh = viewModel(repository)
        collect(fresh)
        assertThat(repository.requests.last()).isEqualTo(AnalyticsSelection())

        val restored = viewModel(repository, SavedStateHandle(mapOf(
            "analytics_range" to "WEEKS_26", "analytics_scope_kind" to "EXERCISE",
            "analytics_scope_id" to "pull-ups", "analytics_metric" to "REPS"
        )))
        collect(restored)
        assertThat(repository.requests.last()).isEqualTo(
            AnalyticsSelection(AnalyticsRange.WEEKS_26, AnalyticsScope.Exercise("pull-ups"))
        )
        assertThat(restored.analyticsSelection.value.metric).isEqualTo(AnalyticsMetric.REPS)
    }

    @Test
    fun invalidOrOversizedSavedStateFallsBackToDefaults() = runTest {
        listOf(
            mapOf("analytics_range" to "WEEKS_9", "analytics_metric" to "STRENGTH"),
            mapOf("analytics_scope_kind" to "MUSCLE", "analytics_scope_id" to "Neck"),
            mapOf("analytics_scope_kind" to "MUSCLE", "analytics_scope_id" to "Cardio"),
            mapOf("analytics_scope_kind" to "EXERCISE", "analytics_scope_id" to "x".repeat(201)),
            mapOf("analytics_scope_kind" to "EXERCISE", "analytics_scope_id" to " "),
            mapOf("analytics_scope_kind" to "EXERCISE")
        ).forEach { saved ->
            val repository = FakeRepository()
            collect(viewModel(repository, SavedStateHandle(saved)))
            assertThat(repository.requests.last()).isEqualTo(AnalyticsSelection())
        }
    }

    @Test
    fun selectionsAreSavedAsFourShortStringsAndResetRestoresDefaults() = runTest {
        val handle = SavedStateHandle()
        val viewModel = viewModel(FakeRepository(), handle)
        viewModel.selectAnalyticsRange(AnalyticsRange.WEEKS_52)
        viewModel.selectAnalyticsScope(AnalyticsScope.Muscle("Chest"))
        viewModel.selectAnalyticsMetric(AnalyticsMetric.EXTERNAL_LOAD_VOLUME)
        assertThat(handle.keys()).containsExactly(
            "analytics_range", "analytics_scope_kind", "analytics_scope_id", "analytics_metric"
        )
        assertThat(handle.get<String>("analytics_scope_id")).isEqualTo("Chest")
        assertThat(viewModel.analyticsSelection.value.range).isEqualTo(AnalyticsRange.WEEKS_52)

        viewModel.resetAnalytics()
        assertThat(viewModel.analyticsSelection.value).isEqualTo(AnalyticsSelection())
    }

    @Test
    fun aSlowObsoleteReadCannotReplaceTheLatestSelection() = runTest {
        val slow = CompletableDeferred<Unit>()
        val repository = FakeRepository().apply {
            respond = { selection ->
                if (selection.range == AnalyticsRange.WEEKS_4) slow.await()
                snapshot(selection)
            }
        }
        val viewModel = viewModel(repository)
        collect(viewModel)
        viewModel.selectAnalyticsRange(AnalyticsRange.WEEKS_12)
        runCurrent()
        slow.complete(Unit)
        runCurrent()
        val state = viewModel.analyticsState.value as ProgressAnalyticsUiState.Loaded
        assertThat(state.analytics.range).isEqualTo(AnalyticsRange.WEEKS_12)
    }

    @Test
    fun analyticsRetryReadsAgainWithoutReloadingTheWeeklyOverview() = runTest {
        val repository = FakeRepository().apply { respond = { error("read failed") } }
        val viewModel = viewModel(repository)
        val overview = mutableListOf<ProgressUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { overview += it } }
        collect(viewModel)
        assertThat(viewModel.analyticsState.value).isEqualTo(ProgressAnalyticsUiState.Error)
        val overviewEmissions = overview.size
        val reads = repository.requests.size
        repository.respond = { snapshot(it) }
        viewModel.retryAnalytics()
        runCurrent()
        assertThat(repository.requests).hasSize(reads + 1)
        assertThat(viewModel.analyticsState.value).isInstanceOf(ProgressAnalyticsUiState.Loaded::class.java)
        assertThat(overview).hasSize(overviewEmissions)
        assertThat(repository.overviewReads).isEqualTo(1)
    }

    @Test
    fun analyticsReadsAgainAtTheWeekBoundaryAndForANewZone() = runTest {
        val repository = FakeRepository()
        val start = MONDAY - 1_000L
        var zone = UTC
        val viewModel = ProgressViewModel(repository, SavedStateHandle(),
            nowTimestamp = { start + testScheduler.currentTime }, zoneId = { zone },
            analyticsDispatcher = kotlinx.coroutines.Dispatchers.Main)
        collect(viewModel)
        assertThat(repository.zones).containsExactly(UTC)
        advanceTimeBy(1_000)
        runCurrent()
        assertThat(repository.zones).containsExactly(UTC, UTC)
        zone = ZoneId.of("America/Los_Angeles")
        viewModel.refresh()
        runCurrent()
        assertThat(repository.zones.last()).isEqualTo(zone)
        assertThat(repository.requests.map { it.scope }.distinct()).containsExactly(AnalyticsScope.All)
    }

    @Test
    fun changingOnlyTheMetricDoesNotReadAgain() = runTest {
        val repository = FakeRepository()
        val viewModel = viewModel(repository)
        collect(viewModel)
        val reads = repository.requests.size
        viewModel.selectAnalyticsMetric(AnalyticsMetric.EXTERNAL_LOAD_VOLUME)
        runCurrent()
        assertThat(repository.requests).hasSize(reads)
        assertThat(viewModel.analyticsState.value).isInstanceOf(ProgressAnalyticsUiState.Loaded::class.java)
    }

    @Test
    fun tooLargeErrorAndNoProfileAreDistinctAndRetryRecovers() = runTest {
        val repository = FakeRepository().apply { respond = { throw AnalyticsRangeTooLargeException() } }
        val viewModel = viewModel(repository)
        collect(viewModel)
        assertThat(viewModel.analyticsState.value).isEqualTo(ProgressAnalyticsUiState.TooLarge)
        repository.respond = { error("read failed") }
        viewModel.refresh()
        runCurrent()
        assertThat(viewModel.analyticsState.value).isEqualTo(ProgressAnalyticsUiState.Error)
        repository.respond = { null }
        viewModel.refresh()
        runCurrent()
        assertThat(viewModel.analyticsState.value).isEqualTo(ProgressAnalyticsUiState.NoProfile)
        repository.respond = { snapshot(it) }
        viewModel.refresh()
        runCurrent()
        assertThat(viewModel.analyticsState.value).isInstanceOf(ProgressAnalyticsUiState.Loaded::class.java)
    }

    @Test
    fun recordedDecisionsAreDecodedForTheSelectedExerciseOnly() = runTest {
        val codes = ProgressionReasonCode.encode(listOf(
            // An accepted deload cannot also advance, so the recorded decision is a hold.
            ProgressionProvenance("pull-ups", ProgressionReason.HOLD_TARGET_NOT_MET, null,
                listOf("s0", "s1"), "a".repeat(64)),
            ProgressionProvenance("row", ProgressionReason.HOLD_INSUFFICIENT_HISTORY, null, emptyList(), "b".repeat(64))
        )) + DeloadReasonCode.encode(DeloadRecommendationProvenance(2, "offer-1", DeloadSource.EXPLICIT_REQUEST)) +
            listOf("FUTURE_POLICY_V9.SOMETHING")
        val repository = FakeRepository().apply {
            respond = { snapshot(it, listOf(decision("recorded", codes), decision("plain", emptyList()))) }
        }
        val viewModel = viewModel(repository, SavedStateHandle(mapOf(
            "analytics_scope_kind" to "EXERCISE", "analytics_scope_id" to "pull-ups"
        )))
        collect(viewModel)
        val state = viewModel.analyticsState.value as ProgressAnalyticsUiState.Loaded
        val recorded = state.decisions.single()
        assertThat(recorded.sessionId).isEqualTo("recorded")
        assertThat(recorded.progression!!.exerciseId).isEqualTo("pull-ups")
        assertThat(recorded.progression!!.sourceSessionIds).containsExactly("s0", "s1").inOrder()
        assertThat(recorded.acceptedDeload).isEqualTo(DeloadSource.EXPLICIT_REQUEST)
        assertThat(recorded.unsupportedIdentities).containsExactly("FUTURE_POLICY_V9.SOMETHING")
    }

    @Test
    fun aMalformedKnownReasonGroupIsAReadErrorNotAMissingDecision() = runTest {
        val repository = FakeRepository().apply {
            respond = { snapshot(it, listOf(decision("bad", listOf("ONE_VARIABLE_PROGRESSION_V1.0.EXERCISE:pull-ups")))) }
        }
        val viewModel = viewModel(repository, SavedStateHandle(mapOf(
            "analytics_scope_kind" to "EXERCISE", "analytics_scope_id" to "pull-ups"
        )))
        collect(viewModel)
        assertThat(viewModel.analyticsState.value).isEqualTo(ProgressAnalyticsUiState.Error)
    }

    private fun kotlinx.coroutines.test.TestScope.collect(viewModel: ProgressViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.analyticsState.collect {} }
        runCurrent()
    }

    private fun viewModel(repository: ProgressRepository, handle: SavedStateHandle = SavedStateHandle()) =
        ProgressViewModel(repository, handle, nowTimestamp = { NOW }, zoneId = { UTC },
            analyticsDispatcher = kotlinx.coroutines.Dispatchers.Main)

    private class FakeRepository : ProgressRepository {
        val requests = mutableListOf<AnalyticsSelection>()
        val zones = mutableListOf<ZoneId>()
        var overviewReads = 0
        var respond: suspend (AnalyticsSelection) -> ProgressAnalyticsSnapshot? = { snapshot(it) }
        override fun observeProgress(now: () -> Instant, zoneId: ZoneId): Flow<ProgressSnapshot?> =
            flow<ProgressSnapshot?> { overviewReads++ }
        // Recorded as a selection with the default metric: the metric is never part of a read.
        override fun observeAnalytics(
            range: AnalyticsRange, scope: AnalyticsScope, now: () -> Instant, zoneId: ZoneId
        ): Flow<ProgressAnalyticsSnapshot?> = flow {
            val selection = AnalyticsSelection(range, scope)
            requests += selection
            zones += zoneId
            emit(respond(selection))
        }
    }

    companion object {
        private val UTC = ZoneId.of("UTC")
        private val NOW = Instant.parse("2026-09-02T12:00:00Z").toEpochMilli()
        private val MONDAY = Instant.parse("2026-09-07T00:00:00Z").toEpochMilli()

        private fun decision(sessionId: String, codes: List<String>) = RecordedSessionDecision(
            sessionId, "Recorded $sessionId", NOW, RecommendationRecord(
                sessionId = sessionId, validatorVersion = "WHOLE_PROGRAM_V2", durationEstimatorVersion = "DURATION_ESTIMATOR_V1",
                outcome = "VALID", reviewedPathEnabled = true, catalogVersion = null, reviewPolicyVersion = 1,
                trainingPolicyVersion = null, ledgerPolicyVersion = null, programStatePolicyVersion = null,
                adaptationState = null, weekStartEpochDay = null, timeZoneId = null, profileRevision = 0,
                contextIdentity = "ctx", reasonCodes = codes, doseAccounting = emptyList(), recordedAtEpochMillis = NOW
            )
        )

        private fun snapshot(
            selection: AnalyticsSelection,
            decisions: List<RecordedSessionDecision> = emptyList()
        ) = ProgressAnalyticsSnapshot(
            analytics = ProgressAnalytics(selection.range, selection.scope, emptyMap(), 3, false, 0, emptyMap(), emptyList()),
            preferredUnit = WeightUnit.KG,
            exerciseOptions = emptyList(),
            catalogNames = emptyMap(),
            decisions = decisions
        )
    }
}
