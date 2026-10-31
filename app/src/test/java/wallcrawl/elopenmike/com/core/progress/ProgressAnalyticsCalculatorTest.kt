package wallcrawl.elopenmike.com.core.progress

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertThrows
import org.junit.Test
import wallcrawl.elopenmike.com.core.ai.WeeklyDoseLedgerCalculator
import wallcrawl.elopenmike.com.core.ai.syntheticApprovedExercise
import wallcrawl.elopenmike.com.core.ai.syntheticExerciseWithoutReviewedMetadata
import wallcrawl.elopenmike.com.core.model.AnalyticsMetric
import wallcrawl.elopenmike.com.core.model.AnalyticsRange
import wallcrawl.elopenmike.com.core.model.AnalyticsScope
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.LedgerOmissionReason
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.PerformanceShape
import wallcrawl.elopenmike.com.core.model.ProgressAnalytics
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetStopReason
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.TrainingWeek
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.model.convertWeight

/**
 * Every analytics number is a documented sum over completed sets in explicit calendar weeks.
 * These cases pin each formula, its scope and its omissions, so a chart can never show a
 * value the contract does not define.
 */
class ProgressAnalyticsCalculatorTest {

    private val calculator = ProgressAnalyticsCalculator()
    private val bench = syntheticApprovedExercise("bench", StandardMuscles.CHEST,
        legacyPrimaryMuscles = listOf(StandardMuscles.CHEST, StandardMuscles.TRICEPS))
    private val row = syntheticApprovedExercise("row", StandardMuscles.BACK,
        legacyPrimaryMuscles = listOf(StandardMuscles.BACK))
    private val draft = syntheticExerciseWithoutReviewedMetadata("draft-press")
        .copy(primaryMuscles = listOf(StandardMuscles.CHEST))
    // The parser allows a reviewed direct primary that is only a legacy secondary muscle.
    private val pulldown = syntheticApprovedExercise("pulldown", StandardMuscles.LATS,
        legacyPrimaryMuscles = listOf(StandardMuscles.BACK), legacySecondaryMuscles = listOf(StandardMuscles.LATS))
    private val catalog = listOf(bench, row, draft, pulldown).associateBy { it.id }

    @Test
    fun rangeIsCalendarWeeksEndingWithThePartialCurrentWeek() {
        val weeks = ProgressAnalyticsCalculator.rangeWeeks(AnalyticsRange.WEEKS_4, NOW, UTC)

        assertThat(weeks.map { it.startEpochMillis }).containsExactly(
            ms("2026-08-10T00:00:00Z"), ms("2026-08-17T00:00:00Z"),
            ms("2026-08-24T00:00:00Z"), ms("2026-08-31T00:00:00Z")
        ).inOrder()
        val result = calculate(emptyList())
        assertThat(result.buckets.map { it.partial }).containsExactly(false, false, false, true).inOrder()
        assertThat(result.fullWeeks).isEqualTo(3)
        assertThat(result.hasWorkoutsInRange).isFalse()
        assertThat(result.buckets.map { it.value }).containsExactly(0.0, 0.0, 0.0, 0.0)
    }

    @Test
    fun fiftyTwoWeekRangeHasExactlyFiftyTwoWeeklyBuckets() {
        val weeks = ProgressAnalyticsCalculator.rangeWeeks(AnalyticsRange.WEEKS_52, NOW, UTC)
        assertThat(weeks).hasSize(52)
        assertThat(weeks.zipWithNext().all { (a, b) -> a.endEpochMillisExclusive == b.startEpochMillis }).isTrue()
    }

    @Test
    fun daylightSavingWeekSpansTheHoursThatActuallyElapsed() {
        val zone = ZoneId.of("America/New_York")
        val weeks = ProgressAnalyticsCalculator.rangeWeeks(
            AnalyticsRange.WEEKS_4, Instant.parse("2026-03-12T15:00:00Z"), zone
        )
        // 2026-03-08 is the spring-forward Sunday, inside the week starting Monday 03-02.
        assertThat(weeks.map { it.elapsedMillis / HOUR }).containsExactly(168L, 168L, 167L, 168L).inOrder()
    }

    @Test
    fun zoneDecidesWhichWeekACompletionBelongsTo() {
        // Sunday evening in Los Angeles is already Monday in UTC.
        val now = Instant.parse("2026-09-06T12:00:00Z")
        val late = session("late", ms("2026-09-07T03:00:00Z"))
        val la = ZoneId.of("America/Los_Angeles")

        val result = calculate(listOf(late), now = now, zone = la)
        assertThat(result.buckets.last().value).isEqualTo(1.0)
        assertThrows(IllegalArgumentException::class.java) { calculate(listOf(late), now = now, zone = UTC) }
    }

    @Test
    fun completedSetsIncludeWarmupsAndTimedWorkButNotOpenOrStoppedSets() {
        val s = session("a", THIS_WEEK, sets = listOf(
            weighted(1, 100.0, 5), weighted(2, 40.0, 10, type = SetType.WARMUP), timed(3, 30),
            weighted(4, 100.0, 5).copy(isCompleted = false),
            weighted(5, 100.0, 5).copy(isCompleted = false, stopReason = SetStopReason.USER_SKIPPED)
        ))

        val result = calculate(listOf(s))
        val current = result.buckets.last()
        assertThat(current.value).isEqualTo(3.0)
        assertThat(current.contributors.map { it.sessionId to it.value }).containsExactly("a" to 3.0)
        assertThat(result.rangeTotal).isEqualTo(3.0)
    }

    @Test
    fun repsCountOnlyPositiveRepsOfCompletedRepBasedSets() {
        val s = session("a", THIS_WEEK, sets = listOf(
            weighted(1, 100.0, 5), bodyweight(2, 12), assisted(3, 8, 20.0), timed(4, 45),
            weighted(5, 100.0, 5).copy(isCompleted = false)
        ))
        assertThat(calculate(listOf(s), metric = AnalyticsMetric.REPS).buckets.last().value).isEqualTo(25.0)
    }

    @Test
    fun volumeConvertsEachSessionUnitBeforeSummingAndExcludesBodyMassAndAssistance() {
        val kg = session("kg", THIS_WEEK, unit = WeightUnit.KG, sets = listOf(weighted(1, 100.0, 5)))
        val lb = session("lb", THIS_WEEK + HOUR, sets = listOf(
            weighted(1, 100.0, 5), weighted(2, 0.333, 3), bodyweight(3, 20), assisted(4, 10, 50.0), timed(5, 60)
        ))

        val result = calculate(listOf(kg, lb), metric = AnalyticsMetric.EXTERNAL_LOAD_VOLUME)
        val expected = convertWeight(100.0, WeightUnit.KG, WeightUnit.LBS) * 5 + 500.0 + 0.333 * 3
        assertThat(result.buckets.last().value).isWithin(1e-9).of(expected)
        assertThat(result.buckets.last().contributors.first { it.sessionId == "kg" }.value)
            .isWithin(1e-9).of(convertWeight(100.0, WeightUnit.KG, WeightUnit.LBS) * 5)
    }

    @Test
    fun workoutsCountZeroSetFinishesOnlyWhenAllExercisesAreSelected() {
        val empty = session("empty", THIS_WEEK, sets = emptyList())
        val benchDay = session("bench-day", THIS_WEEK + HOUR)

        assertThat(calculate(listOf(empty, benchDay), metric = AnalyticsMetric.WORKOUTS)
            .buckets.last().value).isEqualTo(2.0)
        val scoped = calculate(listOf(empty, benchDay), metric = AnalyticsMetric.WORKOUTS,
            scope = AnalyticsScope.Exercise("bench"))
        assertThat(scoped.buckets.last().value).isEqualTo(1.0)
        assertThat(scoped.buckets.last().contributors.map { it.sessionId }).containsExactly("bench-day")
    }

    @Test
    fun exerciseScopeCountsEveryInstanceOfThatIdAndNothingElse() {
        val s = WorkoutSession(
            id = "dup", name = "Dup", startedAtTimestamp = THIS_WEEK - HOUR, completedAtTimestamp = THIS_WEEK,
            status = SessionStatus.COMPLETED,
            exercises = listOf(
                instance("dup", "i1", "bench", 0, listOf(weighted(1, 100.0, 5))),
                instance("dup", "i2", "row", 1, listOf(weighted(1, 80.0, 8))),
                instance("dup", "i3", "bench", 2, listOf(weighted(1, 90.0, 6), weighted(2, 90.0, 6)))
            )
        )
        val result = calculate(listOf(s), scope = AnalyticsScope.Exercise("bench"))
        assertThat(result.buckets.last().value).isEqualTo(3.0)
        val workouts = calculate(listOf(s), scope = AnalyticsScope.Exercise("bench"), metric = AnalyticsMetric.WORKOUTS)
        assertThat(workouts.buckets.last().value).isEqualTo(1.0)
    }

    @Test
    fun muscleScopeUsesCurrentLegacyPrimariesOverlappingAndReportsUnknownExercises() {
        val s = WorkoutSession(
            id = "m", name = "M", startedAtTimestamp = THIS_WEEK - HOUR, completedAtTimestamp = THIS_WEEK,
            status = SessionStatus.COMPLETED,
            exercises = listOf(
                instance("m", "i1", "bench", 0, listOf(weighted(1, 100.0, 5), weighted(2, 100.0, 5))),
                instance("m", "i2", "draft-press", 1, listOf(weighted(1, 50.0, 10))),
                instance("m", "i3", "retired-id", 2, listOf(weighted(1, 50.0, 10)))
            )
        )
        val chest = calculate(listOf(s), scope = AnalyticsScope.Muscle(StandardMuscles.CHEST))
        val triceps = calculate(listOf(s), scope = AnalyticsScope.Muscle(StandardMuscles.TRICEPS))
        assertThat(chest.buckets.last().value).isEqualTo(3.0)
        assertThat(triceps.buckets.last().value).isEqualTo(2.0)
        assertThat(chest.unclassifiableSets).isEqualTo(1)
    }

    @Test
    fun reviewedDoseIsExactlyTheExistingLedgerForEachScope() {
        val s = WorkoutSession(
            id = "r", name = "R", startedAtTimestamp = THIS_WEEK - HOUR, completedAtTimestamp = THIS_WEEK,
            status = SessionStatus.COMPLETED,
            exercises = listOf(
                instance("r", "i1", "bench", 0, listOf(
                    weighted(1, 40.0, 10, type = SetType.WARMUP), weighted(2, 100.0, 5), weighted(3, 100.0, 5))),
                instance("r", "i2", "row", 1, listOf(weighted(1, 80.0, 8))),
                instance("r", "i3", "draft-press", 2, listOf(weighted(1, 50.0, 10))),
                instance("r", "i4", "retired-id", 3, listOf(weighted(1, 50.0, 10)))
            )
        )
        val week = TrainingWeek.containing(NOW, UTC)
        val ledger = WeeklyDoseLedgerCalculator().calculate(
            listOf(s), catalog, LedgerPolicyVersion.PRIMARY_ONLY_V1, week, CATALOG_VERSION, 1
        )
        val all = calculate(listOf(s), metric = AnalyticsMetric.REVIEWED_PRIMARY_DOSE)
        assertThat(all.buckets.last().value).isEqualTo(ledger.creditedWorkSets.toDouble())
        assertThat(all.unattributedWorkSets).containsExactly(
            LedgerOmissionReason.MISSING_REVIEWED_METADATA, 1, LedgerOmissionReason.UNKNOWN_EXERCISE, 1
        )
        assertThat(calculate(listOf(s), metric = AnalyticsMetric.REVIEWED_PRIMARY_DOSE,
            scope = AnalyticsScope.Muscle(StandardMuscles.CHEST)).buckets.last().value).isEqualTo(2.0)
        val draftOnly = calculate(listOf(s), metric = AnalyticsMetric.REVIEWED_PRIMARY_DOSE,
            scope = AnalyticsScope.Exercise("draft-press"))
        assertThat(draftOnly.buckets.last().value).isEqualTo(0.0)
        assertThat(draftOnly.unattributedWorkSets).containsExactly(LedgerOmissionReason.MISSING_REVIEWED_METADATA, 1)
    }

    @Test
    fun muscleDoseFollowsTheReviewedPrimaryEvenWhenItIsOnlyALegacySecondary() {
        val s = WorkoutSession(
            id = "lats", name = "Lats", startedAtTimestamp = THIS_WEEK - HOUR, completedAtTimestamp = THIS_WEEK,
            status = SessionStatus.COMPLETED,
            exercises = listOf(instance("lats", "i1", "pulldown", 0, listOf(weighted(1, 80.0, 8), weighted(2, 80.0, 8))))
        )
        val ledger = WeeklyDoseLedgerCalculator().calculate(
            listOf(s), catalog, LedgerPolicyVersion.PRIMARY_ONLY_V1, TrainingWeek.containing(NOW, UTC), CATALOG_VERSION, 1
        )
        val dose = calculate(listOf(s), metric = AnalyticsMetric.REVIEWED_PRIMARY_DOSE,
            scope = AnalyticsScope.Muscle(StandardMuscles.LATS))
        assertThat(dose.buckets.last().value).isEqualTo(ledger.directPrimarySets.getValue(StandardMuscles.LATS).toDouble())
        assertThat(dose.buckets.last().contributors.map { it.sessionId }).containsExactly("lats")
        assertThat(dose.hasScopeMatches).isTrue()
        // Activity under the same muscle uses legacy primaries, where Lats is not listed.
        assertThat(calculate(listOf(s), scope = AnalyticsScope.Muscle(StandardMuscles.LATS)).hasScopeMatches).isFalse()
        // A legacy match with no reviewed credit is not a zero-valued dose contributor.
        val back = calculate(listOf(s), metric = AnalyticsMetric.REVIEWED_PRIMARY_DOSE,
            scope = AnalyticsScope.Muscle(StandardMuscles.BACK))
        assertThat(back.buckets.last().contributors).isEmpty()
        assertThat(back.hasScopeMatches).isFalse()
    }

    @Test
    fun repsAndLoadVolumeDoNotTurnWorkTheyDoNotApplyToIntoZero() {
        val plank = session("plank", THIS_WEEK, type = ExerciseType.DURATION, sets = listOf(timed(1, 45), timed(2, 50)))
        val pullUps = session("pull", THIS_WEEK + HOUR, type = ExerciseType.BODYWEIGHT_REPS, sets = listOf(bodyweight(1, 8)))
        val scope = AnalyticsScope.Exercise("bench")
        val reps = calculate(listOf(plank), metric = AnalyticsMetric.REPS, scope = scope)
        assertThat(reps.buckets.flatMap { it.contributors }).isEmpty()
        assertThat(reps.hasScopeMatches).isFalse()
        val volume = calculate(listOf(pullUps), metric = AnalyticsMetric.EXTERNAL_LOAD_VOLUME, scope = scope)
        assertThat(volume.buckets.flatMap { it.contributors }).isEmpty()
        assertThat(volume.hasScopeMatches).isFalse()
        // The same timed sets are still completed sets.
        assertThat(calculate(listOf(plank), scope = scope).rangeTotal).isEqualTo(2.0)
        // Across all exercises, a timed-only workout is not a zero-rep contributor either.
        assertThat(calculate(listOf(plank, pullUps), metric = AnalyticsMetric.REPS).buckets.last().contributors
            .map { it.sessionId }).containsExactly("pull")
    }

    @Test
    fun everyMetricComesFromOnePassSoChangingTheMetricNeedsNoNewRead() {
        val result = calculateAll(listOf(session("a", THIS_WEEK, sets = listOf(weighted(1, 100.0, 5)))))
        assertThat(result.series.keys).containsExactlyElementsIn(AnalyticsMetric.entries)
        assertThat(result.series.getValue(AnalyticsMetric.REPS).rangeTotal).isEqualTo(5.0)
        assertThat(result.series.getValue(AnalyticsMetric.EXTERNAL_LOAD_VOLUME).rangeTotal).isEqualTo(500.0)
    }

    @Test
    fun tiedBestSetsAreChosenDeterministicallyRegardlessOfStoredOrder() {
        val sets = listOf(assisted(1, 10, 30.0, id = "x"), assisted(2, 10, 20.0, id = "y"))
        val values = listOf(sets, sets.reversed()).map { ordered ->
            calculate(listOf(session("a", THIS_WEEK, type = ExerciseType.ASSISTED_BODYWEIGHT, sets = ordered)),
                scope = AnalyticsScope.Exercise("bench"), comparable = setOf("x", "y")).performance.single()
                .observations.single().secondaryValue
        }
        assertThat(values[0]).isEqualTo(values[1])
    }

    @Test
    fun aCompletionLaterThisWeekThanTheClockStillCountsLikeWeeklyActivity() {
        val skewed = session("skewed", ms("2026-09-05T09:00:00Z"))
        assertThat(calculate(listOf(skewed)).buckets.last().value).isEqualTo(1.0)
    }

    @Test
    fun malformedInputFailsInsteadOfShrinkingTheChart() {
        assertThrows(IllegalArgumentException::class.java) {
            calculate(listOf(session("a", THIS_WEEK).copy(status = SessionStatus.CANCELLED)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            calculate(listOf(session("a", THIS_WEEK), session("a", THIS_WEEK + HOUR)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            calculate(listOf(session("old", ms("2026-08-01T00:00:00Z"))))
        }
    }

    @Test
    fun noMatchesIsDistinctFromNoWorkouts() {
        val skippedBench = session("s", THIS_WEEK, sets = listOf(weighted(1, 100.0, 5).copy(isCompleted = false)))
        val result = calculate(listOf(skippedBench), scope = AnalyticsScope.Exercise("bench"))
        assertThat(result.hasWorkoutsInRange).isTrue()
        assertThat(result.hasScopeMatches).isFalse()
    }

    @Test
    fun performanceIsOnlyForExerciseScope() {
        assertThat(calculate(listOf(session("a", THIS_WEEK))).performance).isEmpty()
    }

    @Test
    fun weightedPerformanceIsTheHeaviestComparableLoadPooledPerSession() {
        val s = WorkoutSession(
            id = "dup", name = "Dup", startedAtTimestamp = THIS_WEEK - HOUR, completedAtTimestamp = THIS_WEEK,
            status = SessionStatus.COMPLETED,
            exercises = listOf(
                instance("dup", "i1", "bench", 0, listOf(weighted(1, 100.0, 5, id = "a"))),
                instance("dup", "i2", "bench", 1, listOf(weighted(1, 110.0, 3, id = "b"), weighted(2, 200.0, 1, id = "warm")))
            )
        )
        val result = calculate(listOf(s), scope = AnalyticsScope.Exercise("bench"), comparable = setOf("a", "b"))
        val series = result.performance.single()
        assertThat(series.shape).isEqualTo(PerformanceShape.WEIGHTED_REPS)
        val observation = series.observations.single()
        assertThat(observation.sessionId).isEqualTo("dup")
        assertThat(observation.exerciseInstanceIds).containsExactly("i1", "i2").inOrder()
        assertThat(observation.value).isEqualTo(110.0)
        assertThat(observation.reps).isEqualTo(3)
        assertThat(series.comparison).isNull()
    }

    @Test
    fun comparisonNeedsTwoSessionsAndReportsTheSignedDifferenceWithoutAPercentage() {
        val first = session("first", PREV_WEEK, sets = listOf(weighted(1, 100.0, 5, id = "f")))
        val second = session("second", THIS_WEEK, sets = listOf(weighted(1, 95.0, 5, id = "s")))
        val series = calculate(listOf(first, second), scope = AnalyticsScope.Exercise("bench"),
            comparable = setOf("f", "s")).performance.single()

        assertThat(series.observations.map { it.sessionId }).containsExactly("first", "second").inOrder()
        assertThat(series.comparison!!.earliest.sessionId).isEqualTo("first")
        assertThat(series.comparison!!.latest.sessionId).isEqualTo("second")
        assertThat(series.comparison!!.difference).isEqualTo(-5.0)
    }

    @Test
    fun changingTheStoredOrPreferredUnitNeverManufacturesAChange() {
        val kg = session("kg", PREV_WEEK, unit = WeightUnit.KG, sets = listOf(weighted(1, 100.0, 5, id = "k")))
        val lb = session("lb", THIS_WEEK, sets = listOf(
            weighted(1, convertWeight(100.0, WeightUnit.KG, WeightUnit.LBS), 5, id = "l")))
        listOf(WeightUnit.KG, WeightUnit.LBS).forEach { unit ->
            val series = calculate(listOf(kg, lb), scope = AnalyticsScope.Exercise("bench"),
                comparable = setOf("k", "l"), unit = unit).performance.single()
            assertThat(series.comparison!!.difference).isWithin(1e-9).of(0.0)
        }
        val inKg = calculate(listOf(kg, lb), scope = AnalyticsScope.Exercise("bench"),
            comparable = setOf("k", "l"), unit = WeightUnit.KG).performance.single()
        assertThat(inKg.observations.map { it.value }).containsExactly(100.0, 100.0)
    }

    @Test
    fun shapesStaySeparateAndAmbiguousShapesAreListedButNotCompared() {
        fun distance(id: String, at: Long, durationTarget: Int?, distanceTarget: Double?, seconds: Int?, meters: Double?) =
            WorkoutSession(
                id = id, name = id, startedAtTimestamp = at - HOUR, completedAtTimestamp = at,
                status = SessionStatus.COMPLETED,
                exercises = listOf(WorkoutExercise(
                    id = "$id-i", sessionId = id, exerciseId = "bench", orderIndex = 0,
                    prescription = ExercisePrescription(ExerciseType.DISTANCE_DURATION, 1,
                        targetDurationSeconds = durationTarget, targetDistanceMeters = distanceTarget),
                    sets = listOf(WorkoutSet(id = "$id-s", workoutExerciseId = "$id-i", setNumber = 1,
                        exerciseType = ExerciseType.DISTANCE_DURATION, completedDurationSeconds = seconds,
                        completedDistanceMeters = meters, isCompleted = true))
                ))
            )
        val sessions = listOf(
            distance("d1", PREV_WEEK, null, 400.0, null, 400.0),
            distance("d2", THIS_WEEK, null, 400.0, null, 500.0),
            distance("t1", THIS_WEEK + HOUR, 60, null, 75, null),
            distance("b1", THIS_WEEK + 2 * HOUR, 60, 400.0, 70, 420.0),
            distance("b2", THIS_WEEK + 3 * HOUR, 60, 400.0, 65, 430.0)
        )
        val series = calculate(sessions, scope = AnalyticsScope.Exercise("bench"),
            comparable = sessions.map { "${it.id}-s" }.toSet()).performance.associateBy { it.shape }

        assertThat(series.keys).containsExactly(
            PerformanceShape.DISTANCE_ONLY, PerformanceShape.TIME_ONLY, PerformanceShape.DISTANCE_AND_TIME
        )
        assertThat(series.getValue(PerformanceShape.DISTANCE_ONLY).comparison!!.difference).isEqualTo(100.0)
        assertThat(series.getValue(PerformanceShape.TIME_ONLY).comparison).isNull()
        assertThat(PerformanceShape.DISTANCE_AND_TIME.charted).isFalse()
        assertThat(series.getValue(PerformanceShape.DISTANCE_AND_TIME).comparison).isNull()
        assertThat(series.getValue(PerformanceShape.DISTANCE_AND_TIME).observations.map { it.secondaryValue })
            .containsExactly(70.0, 65.0).inOrder()
    }

    @Test
    fun assistedRepsAreListedWithAssistanceButNeverComparedOrCountedAsLoad() {
        val a = session("a", PREV_WEEK, type = ExerciseType.ASSISTED_BODYWEIGHT, sets = listOf(assisted(1, 8, 30.0, id = "x")))
        val b = session("b", THIS_WEEK, type = ExerciseType.ASSISTED_BODYWEIGHT, sets = listOf(assisted(1, 10, 20.0, id = "y")))
        val series = calculate(listOf(a, b), scope = AnalyticsScope.Exercise("bench"),
            comparable = setOf("x", "y")).performance.single()
        assertThat(series.shape).isEqualTo(PerformanceShape.ASSISTED_REPS)
        assertThat(series.comparison).isNull()
        assertThat(series.observations.map { it.reps to it.secondaryValue }).containsExactly(8 to 30.0, 10 to 20.0).inOrder()
    }

    @Test
    fun bodyweightAndDurationPerformanceUseRepsAndSeconds() {
        val bw1 = session("bw1", PREV_WEEK, type = ExerciseType.BODYWEIGHT_REPS, sets = listOf(bodyweight(1, 10, id = "p")))
        val bw2 = session("bw2", THIS_WEEK, type = ExerciseType.BODYWEIGHT_REPS, sets = listOf(bodyweight(1, 12, id = "q")))
        val bw = calculate(listOf(bw1, bw2), scope = AnalyticsScope.Exercise("bench"),
            comparable = setOf("p", "q")).performance.single()
        assertThat(bw.shape).isEqualTo(PerformanceShape.BODYWEIGHT_REPS)
        assertThat(bw.comparison!!.difference).isEqualTo(2.0)

        val hold = session("h", THIS_WEEK, type = ExerciseType.DURATION, sets = listOf(timed(1, 45, id = "h1")))
        val holdSeries = calculate(listOf(hold), scope = AnalyticsScope.Exercise("bench"),
            comparable = setOf("h1")).performance.single()
        assertThat(holdSeries.shape).isEqualTo(PerformanceShape.HOLD_DURATION)
        assertThat(holdSeries.observations.single().value).isEqualTo(45.0)
    }

    @Test
    fun aSessionWithoutComparableSetsContributesNoObservation() {
        val s = session("warm", THIS_WEEK, sets = listOf(weighted(1, 40.0, 10, type = SetType.WARMUP, id = "w")))
        val result = calculate(listOf(s), scope = AnalyticsScope.Exercise("bench"), comparable = emptySet())
        assertThat(result.performance).isEmpty()
        assertThat(result.hasScopeMatches).isTrue()
    }

    /** One metric's series beside the shared range facts, so each case reads like the card. */
    private class Result(val analytics: ProgressAnalytics, metric: AnalyticsMetric) {
        private val series = analytics.series.getValue(metric)
        val buckets = series.buckets
        val rangeTotal = series.rangeTotal
        val hasScopeMatches = series.hasScopeMatches
        val fullWeeks = analytics.fullWeeks
        val hasWorkoutsInRange = analytics.hasWorkoutsInRange
        val unclassifiableSets = analytics.unclassifiableSets
        val unattributedWorkSets = analytics.unattributedWorkSets
        val performance = analytics.performance
    }

    private fun calculate(
        sessions: List<WorkoutSession>,
        metric: AnalyticsMetric = AnalyticsMetric.COMPLETED_SETS,
        scope: AnalyticsScope = AnalyticsScope.All,
        comparable: Set<String> = emptySet(),
        unit: WeightUnit = WeightUnit.LBS,
        now: Instant = NOW,
        zone: ZoneId = UTC
    ) = Result(calculateAll(sessions, scope, comparable, unit, now, zone), metric)

    private fun calculateAll(
        sessions: List<WorkoutSession>,
        scope: AnalyticsScope = AnalyticsScope.All,
        comparable: Set<String> = emptySet(),
        unit: WeightUnit = WeightUnit.LBS,
        now: Instant = NOW,
        zone: ZoneId = UTC
    ) = calculator.calculate(
        range = AnalyticsRange.WEEKS_4,
        scope = scope,
        sessions = sessions,
        comparableSetIds = comparable,
        exercisesById = catalog,
        catalogVersion = CATALOG_VERSION,
        reviewPolicyVersion = 1,
        preferredUnit = unit,
        now = now,
        zoneId = zone
    )

    private fun session(
        id: String,
        completedAt: Long,
        unit: WeightUnit = WeightUnit.LBS,
        type: ExerciseType = ExerciseType.WEIGHT_REPS,
        sets: List<WorkoutSet> = listOf(weighted(1, 100.0, 5))
    ) = WorkoutSession(
        id = id, name = "Workout $id", startedAtTimestamp = completedAt - HOUR,
        completedAtTimestamp = completedAt, weightUnit = unit, status = SessionStatus.COMPLETED,
        exercises = if (sets.isEmpty()) emptyList() else listOf(
            WorkoutExercise(
                id = "$id-i", sessionId = id, exerciseId = "bench", orderIndex = 0,
                prescription = prescription(type), sets = sets.map { it.copy(workoutExerciseId = "$id-i") }
            )
        )
    )

    private fun instance(sessionId: String, id: String, exerciseId: String, order: Int, sets: List<WorkoutSet>) =
        WorkoutExercise(id = id, sessionId = sessionId, exerciseId = exerciseId, orderIndex = order,
            prescription = prescription(ExerciseType.WEIGHT_REPS),
            // Explicit ids are referenced by comparability fixtures; generated ones are made unique.
            sets = sets.map { it.copy(id = if (it.id in EXPLICIT_IDS) it.id else "$id-${it.id}", workoutExerciseId = id) })

    private fun prescription(type: ExerciseType) = when (type) {
        ExerciseType.WEIGHT_REPS, ExerciseType.BODYWEIGHT_REPS, ExerciseType.ASSISTED_BODYWEIGHT ->
            ExercisePrescription(type, 3, repRange = RepRange(5, 10))
        ExerciseType.DURATION -> ExercisePrescription(type, 3, targetDurationSeconds = 30)
        ExerciseType.DISTANCE_DURATION -> ExercisePrescription(type, 1, targetDistanceMeters = 400.0)
    }

    private fun weighted(n: Int, weight: Double, reps: Int, type: SetType = SetType.NORMAL, id: String = "w$n-$weight-$reps-$type") =
        WorkoutSet(id = id, workoutExerciseId = "x", setNumber = n, exerciseType = ExerciseType.WEIGHT_REPS,
            completedReps = reps, completedWeight = weight, isCompleted = true, type = type)

    private fun bodyweight(n: Int, reps: Int, id: String = "bw$n-$reps") =
        WorkoutSet(id = id, workoutExerciseId = "x", setNumber = n, exerciseType = ExerciseType.BODYWEIGHT_REPS,
            completedReps = reps, isCompleted = true)

    private fun assisted(n: Int, reps: Int, assistance: Double, id: String = "as$n-$reps") =
        WorkoutSet(id = id, workoutExerciseId = "x", setNumber = n, exerciseType = ExerciseType.ASSISTED_BODYWEIGHT,
            completedReps = reps, completedAssistanceWeight = assistance, isCompleted = true)

    private fun timed(n: Int, seconds: Int, id: String = "t$n-$seconds") =
        WorkoutSet(id = id, workoutExerciseId = "x", setNumber = n, exerciseType = ExerciseType.DURATION,
            completedDurationSeconds = seconds, isCompleted = true)

    private companion object {
        val UTC: ZoneId = ZoneId.of("UTC")
        const val HOUR = 3_600_000L
        const val CATALOG_VERSION = "test-catalog"
        val EXPLICIT_IDS = setOf("a", "b", "warm")
        fun ms(text: String) = Instant.parse(text).toEpochMilli()
        val NOW: Instant = Instant.parse("2026-09-02T12:00:00Z")
        val THIS_WEEK = ms("2026-09-01T09:00:00Z")
        val PREV_WEEK = ms("2026-08-26T09:00:00Z")
    }
}
