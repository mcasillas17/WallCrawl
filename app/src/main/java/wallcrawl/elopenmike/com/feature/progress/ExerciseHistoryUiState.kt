package wallcrawl.elopenmike.com.feature.progress

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import wallcrawl.elopenmike.com.core.database.repository.ExerciseHistorySnapshot
import wallcrawl.elopenmike.com.core.model.PerformanceComparison
import wallcrawl.elopenmike.com.core.model.PerformanceSeries
import wallcrawl.elopenmike.com.core.model.WeightUnit

enum class ProgressPeriod { WEEK, MONTH, YEAR, ALL, CUSTOM }

sealed interface ExerciseHistoryUiState {
    data object Loading : ExerciseHistoryUiState
    data object NoProfile : ExerciseHistoryUiState
    data object Error : ExerciseHistoryUiState

    data class Loaded(
        val exerciseOptions: List<String>,
        val catalogNames: Map<String, String>,
        val exerciseId: String?,
        val preferredUnit: WeightUnit,
        val performance: List<PerformanceSeries>,
        val period: ProgressPeriod,
        val start: LocalDate,
        val end: LocalDate,
        val today: LocalDate,
        val zoneId: ZoneId
    ) : ExerciseHistoryUiState
}

internal data class HistoryRange(
    val period: ProgressPeriod,
    val custom: ClosedRange<LocalDate>? = null
) {
    fun apply(snapshot: ExerciseHistorySnapshot, now: Instant, zone: ZoneId): ExerciseHistoryUiState.Loaded {
        val today = now.atZone(zone).toLocalDate()
        val monthStart = today.withDayOfMonth(1)
        val start = when (period) {
            ProgressPeriod.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            ProgressPeriod.MONTH -> monthStart
            ProgressPeriod.YEAR -> today.withDayOfYear(1)
            ProgressPeriod.ALL -> snapshot.performance.minOfOrNull {
                it.observations.firstOrNull()?.completedAtTimestamp ?: now.toEpochMilli()
            }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: today
            ProgressPeriod.CUSTOM -> custom?.start?.coerceAtMost(today) ?: monthStart
        }
        val end = if (period == ProgressPeriod.CUSTOM) custom?.endInclusive?.coerceAtMost(today) ?: today else today
        val visible = snapshot.performance.mapNotNull { series ->
            val points = series.observations.filter { point ->
                Instant.ofEpochMilli(point.completedAtTimestamp).atZone(zone).toLocalDate() in start..end
            }
            if (points.isEmpty()) return@mapNotNull null
            val comparison = if (series.shape.charted && points.size > 1) PerformanceComparison(points.first(), points.last()) else null
            series.copy(observations = points, comparison = comparison)
        }
        return ExerciseHistoryUiState.Loaded(
            snapshot.exerciseOptions, snapshot.catalogNames, snapshot.exerciseId, snapshot.preferredUnit,
            visible, period, start, end, today, zone
        )
    }
}
