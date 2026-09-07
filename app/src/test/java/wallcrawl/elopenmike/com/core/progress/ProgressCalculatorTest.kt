package wallcrawl.elopenmike.com.core.progress

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.After
import org.junit.Test
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetStopReason
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.StrengthPerformance
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet

/**
 * Weekly progress is measured against an explicit ISO training week in a device zone, never
 * a rolling 168-hour window. Real logged activity is reported separately from any reviewed
 * dose, so identical history can never answer "sets this week" two different ways.
 */
class ProgressCalculatorTest {

    private val calculator = ProgressCalculator()
    private val exercises = InMemoryExerciseCatalog.SAMPLE_EXERCISES
    private val profile = UserProfile(daysPerWeek = 4, preferredUnit = WeightUnit.LBS)

    @After
    fun restoreLocale() {
        Locale.setDefault(DEFAULT_LOCALE)
    }

    @Test
    fun calculate_emptyHistoryReportsZeroedActivity() {
        val result = calculator.calculate(
            completedSessions = emptyList(),
            profile = profile,
            catalogExercises = exercises,
            nowTimestamp = NOW,
            zoneId = ZONE
        )

        assertThat(result.workoutsThisWeek).isEqualTo(0)
        assertThat(result.weeklyGoal).isEqualTo(4)
        assertThat(result.currentStreakWeeks).isEqualTo(0)
        assertThat(result.totalWorkoutsLogged).isEqualTo(0)
        assertThat(result.totalVolumeThisWeek).isEqualTo(0.0)
        assertThat(result.totalRepsThisWeek).isEqualTo(0)
        assertThat(result.completedSetsThisWeek).isEqualTo(0)
        assertThat(result.warmupSetsThisWeek).isEqualTo(0)
        assertThat(result.recentPersonalRecords).isEmpty()
        assertThat(result.legacyPrimaryActivity).isEmpty()
        assertThat(result.strengthTrends).isEmpty()
        assertThat(result.recentHistory).isEmpty()
    }

    @Test
    fun calculate_countsCompletedSessionsInIsoWeekAndExcludesOtherStates() {
        val thisWeek = session("a", THIS_WEEK)
        val alsoThisWeek = session("b", THIS_WEEK_2)
        val lastWeek = session("c", PREV_WEEK)
        val active = session("d", THIS_WEEK).copy(status = SessionStatus.IN_PROGRESS)
        val cancelled = session("e", THIS_WEEK).copy(status = SessionStatus.CANCELLED)

        val result = calculator.calculate(
            completedSessions = listOf(thisWeek, alsoThisWeek, lastWeek, active, cancelled),
            profile = profile,
            catalogExercises = exercises,
            nowTimestamp = NOW,
            zoneId = ZONE
        )

        assertThat(result.workoutsThisWeek).isEqualTo(2)
    }

    @Test
    fun calculate_weekMembershipUsesCalendarWeekNotWallClockSoLaterTimestampsStillCount() {
        val afterNowButSameWeek = session("skewed", LATER_THIS_WEEK_AFTER_NOW)
        val nextWeek = session("next", NEXT_WEEK_START)

        val result = calculator.calculate(
            completedSessions = listOf(afterNowButSameWeek, nextWeek),
            profile = profile,
            catalogExercises = exercises,
            nowTimestamp = NOW,
            zoneId = ZONE
        )

        assertThat(result.workoutsThisWeek).isEqualTo(1)
    }

    @Test
    fun calculate_weekBoundariesAreInclusiveStartExclusiveEnd() {
        val onMondayMidnight = session("start", WEEK_START)
        val onNextMondayMidnight = session("end", NEXT_WEEK_START)

        val result = calculator.calculate(
            completedSessions = listOf(onMondayMidnight, onNextMondayMidnight),
            profile = profile,
            catalogExercises = exercises,
            nowTimestamp = NOW,
            zoneId = ZONE
        )

        assertThat(result.workoutsThisWeek).isEqualTo(1)
        // The next-Monday session is outside the week (exclusive end) and dated ahead of the
        // clock, so it is neither weekly activity nor surfaced in recent history.
        assertThat(result.recentHistory.map { it.id }).containsExactly("start")
    }

    @Test
    fun calculate_weekMembershipDependsOnTheProvidedZone() {
        // 2026-09-07T03:00Z is Monday 03:00 in UTC but still Sunday evening in Los Angeles.
        val boundary = Instant.parse("2026-09-07T03:00:00Z").toEpochMilli()
        val now = Instant.parse("2026-09-07T12:00:00Z").toEpochMilli()
        val sessions = listOf(session("boundary", boundary))

        val utc = calculator.calculate(sessions, profile, exercises, now, ZoneId.of("UTC"))
        val losAngeles = calculator.calculate(
            sessions, profile, exercises, now, ZoneId.of("America/Los_Angeles")
        )

        assertThat(utc.workoutsThisWeek).isEqualTo(1)
        assertThat(losAngeles.workoutsThisWeek).isEqualTo(0)
    }

    @Test
    fun calculate_daylightSavingWeekIsTheLocalCalendarWeekNotAFixed168Hours() {
        // New York springs forward on 2026-03-08; the ISO week 03-02..03-09 spans 167 hours.
        val zone = ZoneId.of("America/New_York")
        val nowSaturday = Instant.parse("2026-03-07T17:00:00Z").toEpochMilli()
        val lateSundayAfterTransition = Instant.parse("2026-03-09T03:30:00Z").toEpochMilli() // Sun 23:30 EDT
        val nextWeek = Instant.parse("2026-03-09T04:30:00Z").toEpochMilli() // Mon 00:30 EDT

        val result = calculator.calculate(
            completedSessions = listOf(
                session("late-sunday", lateSundayAfterTransition),
                session("next-week", nextWeek)
            ),
            profile = profile,
            catalogExercises = exercises,
            nowTimestamp = nowSaturday,
            zoneId = zone
        )

        assertThat(result.workoutsThisWeek).isEqualTo(1)
    }

    @Test
    fun calculate_completedSetsIncludeWarmupsAndTimedWorkButRepsExcludeTimed() {
        val session = session(
            id = "mixed",
            completedAt = THIS_WEEK,
            exerciseId = "incline-dumbbell-press",
            sets = listOf(
                completedSet(1, 50.0, 10),
                completedSet(2, 40.0, 8, type = SetType.WARMUP),
                timedSet(3),
                openSet(4)
            )
        )

        val result = calculator.calculate(
            completedSessions = listOf(session),
            profile = profile,
            catalogExercises = exercises,
            nowTimestamp = NOW,
            zoneId = ZONE
        )

        assertThat(result.completedSetsThisWeek).isEqualTo(3)
        assertThat(result.warmupSetsThisWeek).isEqualTo(1)
        assertThat(result.totalRepsThisWeek).isEqualTo(18)
    }

    @Test
    fun calculate_skippedAndOpenSetsAreNeverCompleted() {
        val session = session(
            id = "abandoned",
            completedAt = THIS_WEEK,
            sets = listOf(skippedSet(1), openSet(2))
        )

        val result = calculator.calculate(
            completedSessions = listOf(session),
            profile = profile,
            catalogExercises = exercises,
            nowTimestamp = NOW,
            zoneId = ZONE
        )

        assertThat(result.completedSetsThisWeek).isEqualTo(0)
        assertThat(result.totalRepsThisWeek).isEqualTo(0)
        assertThat(result.totalVolumeThisWeek).isEqualTo(0.0)
    }

    @Test
    fun calculate_volumeUsesOnlyValidExternalLoadAndIncludesWarmups() {
        val session = session(
            id = "loaded",
            completedAt = THIS_WEEK,
            exerciseId = "incline-dumbbell-press",
            sets = listOf(
                completedSet(1, 50.0, 10),
                completedSet(2, 40.0, 5, type = SetType.WARMUP),
                bodyweightSet(3, reps = 12)
            )
        )

        val result = calculator.calculate(
            completedSessions = listOf(session),
            profile = profile,
            catalogExercises = exercises,
            nowTimestamp = NOW,
            zoneId = ZONE
        )

        // 50×10 + 40×5 = 700; the bodyweight set contributes reps but no tonnage.
        assertThat(result.totalVolumeThisWeek).isEqualTo(700.0)
        assertThat(result.totalRepsThisWeek).isEqualTo(27)
    }

    @Test
    fun calculate_repsCountEvenWhenTheLoadIsInvalid() {
        val session = session(
            id = "bad-load",
            completedAt = THIS_WEEK,
            sets = listOf(completedSet(1, -5.0, 8))
        )

        val result = calculator.calculate(
            completedSessions = listOf(session),
            profile = profile,
            catalogExercises = exercises,
            nowTimestamp = NOW,
            zoneId = ZONE
        )

        assertThat(result.totalRepsThisWeek).isEqualTo(8)
        assertThat(result.totalVolumeThisWeek).isEqualTo(0.0)
    }

    @Test
    fun calculate_convertsEachSessionUnitIntoThePreferredUnit() {
        val metricSession = session(
            id = "metric",
            completedAt = THIS_WEEK,
            sets = listOf(completedSet(1, 10.0, 10)),
            weightUnit = WeightUnit.KG
        )

        val result = calculator.calculate(
            completedSessions = listOf(metricSession),
            profile = profile,
            catalogExercises = exercises,
            nowTimestamp = NOW,
            zoneId = ZONE
        )

        assertThat(result.totalVolumeThisWeek).isWithin(0.001).of(220.462)
        assertThat(result.recentHistory.single().weightUnit).isEqualTo(WeightUnit.KG)
    }

    @Test
    fun calculate_legacyPrimaryInvolvementIsNonAdditiveAndIncludesWarmups() {
        val deadlift = session(
            id = "deadlift",
            completedAt = THIS_WEEK,
            exerciseId = "barbell-deadlift", // Back + Hamstrings + Glutes
            sets = listOf(
                completedSet(1, 225.0, 5),
                completedSet(2, 225.0, 5),
                completedSet(3, 135.0, 8, type = SetType.WARMUP)
            )
        )

        val result = calculator.calculate(
            completedSessions = listOf(deadlift),
            profile = profile,
            catalogExercises = exercises,
            nowTimestamp = NOW,
            zoneId = ZONE
        )

        val back = result.legacyPrimaryActivity.single { it.muscle == StandardMuscles.BACK }
        val hamstrings = result.legacyPrimaryActivity.single { it.muscle == StandardMuscles.HAMSTRINGS }
        val glutes = result.legacyPrimaryActivity.single { it.muscle == StandardMuscles.GLUTES }
        assertThat(back.setsThisWeek).isEqualTo(3)
        assertThat(hamstrings.setsThisWeek).isEqualTo(3)
        assertThat(glutes.setsThisWeek).isEqualTo(3)
        // Involvement is never the completed-set total: three sets involve three muscles nine times.
        assertThat(result.legacyPrimaryActivity.sumOf { it.setsThisWeek }).isEqualTo(9)
        assertThat(result.completedSetsThisWeek).isEqualTo(3)
    }

    @Test
    fun calculate_legacyPrimaryOmitsUnknownCatalogAndConditioningTags() {
        val mobility = exercises.first().copy(
            id = "cat-cow-stretch",
            name = "Cat Cow Stretch",
            primaryMuscles = listOf(StandardMuscles.MOBILITY),
            secondaryMuscles = emptyList()
        )
        val stretch = session(
            id = "stretch",
            completedAt = THIS_WEEK,
            exerciseId = mobility.id,
            sets = listOf(completedSet(1, null, 10))
        )
        val unknown = session(
            id = "unknown",
            completedAt = THIS_WEEK,
            exerciseId = "not-in-catalog",
            sets = listOf(completedSet(1, 20.0, 10))
        )

        val result = calculator.calculate(
            completedSessions = listOf(stretch, unknown),
            profile = profile,
            catalogExercises = exercises + mobility,
            nowTimestamp = NOW,
            zoneId = ZONE
        )

        assertThat(result.legacyPrimaryActivity).isEmpty()
        // The unknown exercise still counts as real logged activity.
        assertThat(result.completedSetsThisWeek).isEqualTo(2)
    }

    @Test
    fun calculate_comparesInvolvementAgainstThePreviousCalendarWeekAsAUnion() {
        val currentPress = session(
            id = "current-press",
            completedAt = THIS_WEEK,
            exerciseId = "incline-dumbbell-press", // Chest
            sets = listOf(completedSet(1, 50.0, 10), completedSet(2, 50.0, 10), completedSet(3, 50.0, 10))
        )
        val currentPull = session(
            id = "current-pull",
            completedAt = THIS_WEEK_2,
            exerciseId = "pull-ups", // Back + Lats
            sets = listOf(completedSet(1, null, 8))
        )
        val previousPress = session(
            id = "previous-press",
            completedAt = PREV_WEEK,
            exerciseId = "incline-dumbbell-press", // Chest
            sets = listOf(completedSet(1, 45.0, 10), completedSet(2, 45.0, 10))
        )
        val previousCurl = session(
            id = "previous-curl",
            completedAt = PREV_WEEK,
            exerciseId = "barbell-bicep-curl", // Biceps
            sets = listOf(completedSet(1, 40.0, 10))
        )

        val result = calculator.calculate(
            completedSessions = listOf(currentPress, currentPull, previousPress, previousCurl),
            profile = profile,
            catalogExercises = exercises,
            nowTimestamp = NOW,
            zoneId = ZONE
        )

        val chest = result.legacyPrimaryActivity.single { it.muscle == StandardMuscles.CHEST }
        assertThat(chest.setsThisWeek).isEqualTo(3)
        assertThat(chest.setsPreviousWeek).isEqualTo(2)
        assertThat(chest.percentageChange).isEqualTo(50)

        // Trained this week, not last: new activity, not a 100% jump.
        val back = result.legacyPrimaryActivity.single { it.muscle == StandardMuscles.BACK }
        assertThat(back.setsThisWeek).isEqualTo(1)
        assertThat(back.setsPreviousWeek).isEqualTo(0)
        assertThat(back.percentageChange).isNull()

        // Trained last week, not this week: a reduction, still shown.
        val biceps = result.legacyPrimaryActivity.single { it.muscle == StandardMuscles.BICEPS }
        assertThat(biceps.setsThisWeek).isEqualTo(0)
        assertThat(biceps.setsPreviousWeek).isEqualTo(1)
        assertThat(biceps.percentageChange).isEqualTo(-100)
    }

    @Test
    fun calculate_streakCountsConsecutiveWeeksFromTheCurrentWeekWhenOccupied() {
        val result = calculator.calculate(
            completedSessions = listOf(
                session("w0", THIS_WEEK),
                session("w1", PREV_WEEK),
                session("w2", TWO_WEEKS_AGO)
            ),
            profile = profile,
            catalogExercises = exercises,
            nowTimestamp = NOW,
            zoneId = ZONE
        )

        assertThat(result.currentStreakWeeks).isEqualTo(3)
    }

    @Test
    fun calculate_streakGivesTheUnfinishedEmptyCurrentWeekGrace() {
        val result = calculator.calculate(
            completedSessions = listOf(
                session("w1", PREV_WEEK),
                session("w2", TWO_WEEKS_AGO)
            ),
            profile = profile,
            catalogExercises = exercises,
            nowTimestamp = NOW,
            zoneId = ZONE
        )

        assertThat(result.currentStreakWeeks).isEqualTo(2)
    }

    @Test
    fun calculate_streakBreaksWhenBothCurrentAndPreviousWeeksAreEmpty() {
        val result = calculator.calculate(
            completedSessions = listOf(session("old", TWO_WEEKS_AGO)),
            profile = profile,
            catalogExercises = exercises,
            nowTimestamp = NOW,
            zoneId = ZONE
        )

        assertThat(result.currentStreakWeeks).isEqualTo(0)
    }

    @Test
    fun calculate_totalWorkoutsCountsAllCompletedTimestampsNotTheBoundedList() {
        val boundedRecent = listOf(session("recent", THIS_WEEK))
        val everyWeek = (0L until 12L).map {
            Instant.parse("2026-08-31T00:00:00Z").toEpochMilli() - it * 7 * DAY_MILLIS
        }

        val result = calculator.calculate(
            completedSessions = boundedRecent,
            profile = profile,
            catalogExercises = exercises,
            nowTimestamp = NOW,
            zoneId = ZONE,
            weeklySessions = boundedRecent,
            completedTimestamps = everyWeek
        )

        assertThat(result.totalWorkoutsLogged).isEqualTo(12)
        // A completion in every prior week keeps the streak going independent of the bounded list.
        assertThat(result.currentStreakWeeks).isEqualTo(12)
    }

    @Test
    fun calculate_weeklyActivityReadsWeeklySessionsWhileRecordsReadTheBoundedList() {
        val weekly = listOf(
            session("weekly-a", THIS_WEEK),
            session("weekly-b", THIS_WEEK_2)
        )
        val bounded = listOf(
            session("bounded", THIS_WEEK, sets = listOf(completedSet(1, 50.0, 10)))
        )

        val result = calculator.calculate(
            completedSessions = bounded,
            profile = profile,
            catalogExercises = exercises,
            nowTimestamp = NOW,
            zoneId = ZONE,
            weeklySessions = weekly
        )

        assertThat(result.workoutsThisWeek).isEqualTo(2)
        assertThat(result.recentHistory.map { it.id }).containsExactly("bounded")
    }

    @Test
    fun calculate_reportsRealActivityFromUnreviewedCatalogExercises() {
        // Every sample exercise is DRAFT (no reviewed metadata); activity must still surface.
        val session = session(
            id = "draft-real",
            completedAt = THIS_WEEK,
            exerciseId = "barbell-back-squat",
            sets = listOf(completedSet(1, 185.0, 5), completedSet(2, 185.0, 5))
        )

        val result = calculator.calculate(
            completedSessions = listOf(session),
            profile = profile,
            catalogExercises = exercises,
            nowTimestamp = NOW,
            zoneId = ZONE
        )

        assertThat(result.workoutsThisWeek).isEqualTo(1)
        assertThat(result.completedSetsThisWeek).isEqualTo(2)
        assertThat(result.totalVolumeThisWeek).isEqualTo(1_850.0)
        assertThat(result.legacyPrimaryActivity.map { it.muscle })
            .containsAtLeast(StandardMuscles.QUADS, StandardMuscles.GLUTES)
    }

    @Test
    fun calculate_derivesRecentWeightRecordAndStrengthTrendFromTheBoundedPath() {
        val previous = session(
            id = "previous",
            completedAt = PREV_WEEK,
            exerciseId = "incline-dumbbell-press",
            sets = listOf(completedSet(1, 45.0, 10))
        )
        val current = session(
            id = "current",
            completedAt = THIS_WEEK,
            exerciseId = "incline-dumbbell-press",
            sets = listOf(completedSet(1, 50.0, 10))
        )

        val result = calculator.calculate(
            completedSessions = listOf(previous, current),
            profile = profile,
            catalogExercises = exercises,
            nowTimestamp = NOW,
            zoneId = ZONE
        )

        val record = result.recentPersonalRecords.single()
        assertThat(record.exerciseName).isEqualTo("Incline Dumbbell Press")
        assertThat(record.value).isEqualTo(50.0)
        assertThat(record.previousValue).isEqualTo(45.0)
        assertThat(record.unit).isEqualTo("lb")

        val trend = result.strengthTrends.single()
        assertThat(trend.previous).isEqualTo(StrengthPerformance(weight = 45.0, reps = 10))
        assertThat(trend.current).isEqualTo(StrengthPerformance(weight = 50.0, reps = 10))
        assertThat(trend.percentageChange).isEqualTo(11)
        assertThat(trend.isPositive).isTrue()
    }

    @Test
    fun calculate_marksRegressingStrengthTrendAsNegative() {
        val previous = session("previous", PREV_WEEK, sets = listOf(completedSet(1, 50.0, 10)))
        val current = session("current", THIS_WEEK, sets = listOf(completedSet(1, 40.0, 10)))

        val trend = calculator.calculate(
            completedSessions = listOf(previous, current),
            profile = profile,
            catalogExercises = exercises,
            nowTimestamp = NOW,
            zoneId = ZONE
        ).strengthTrends.single()

        assertThat(trend.percentageChange).isEqualTo(-20)
        assertThat(trend.isPositive).isFalse()
    }

    @Test
    fun calculate_producesIdenticalNumbersRegardlessOfDefaultLocale() {
        val sessions = listOf(
            session(
                "press",
                THIS_WEEK,
                exerciseId = "incline-dumbbell-press",
                sets = listOf(completedSet(1, 50.0, 10), completedSet(2, 40.0, 8, type = SetType.WARMUP))
            ),
            session("prev", PREV_WEEK, exerciseId = "incline-dumbbell-press", sets = listOf(completedSet(1, 45.0, 10)))
        )
        fun run() = calculator.calculate(sessions, profile, exercises, NOW, ZONE)

        Locale.setDefault(Locale.forLanguageTag("es-MX"))
        val spanish = run()
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        val turkish = run()
        Locale.setDefault(Locale.ENGLISH)
        val english = run()

        assertThat(spanish).isEqualTo(english)
        assertThat(turkish).isEqualTo(english)
    }

    @Test
    fun countPersonalRecords_countsAnExerciseOnceEvenIfLoggedTwiceInOneSession() {
        val older = session("older", PREV_WEEK, sets = listOf(completedSet(1, 45.0, 10)))
        val repeated = session("repeated", THIS_WEEK, sets = listOf(completedSet(1, 50.0, 10)))
            .let { built -> built.copy(exercises = built.exercises + built.exercises.first().copy(id = "second")) }

        assertThat(calculator.countPersonalRecords(repeated, listOf(older))).isEqualTo(1)
    }

    @Test
    fun countPersonalRecords_countsOnlyExercisesBeatingPriorBest() {
        val older = session("older", PREV_WEEK, sets = listOf(completedSet(1, 45.0, 10)))
        val heavier = session("heavier", THIS_WEEK, sets = listOf(completedSet(1, 50.0, 10)))

        assertThat(calculator.countPersonalRecords(heavier, listOf(older, heavier))).isEqualTo(1)
        assertThat(calculator.countPersonalRecords(older, listOf(older, heavier))).isEqualTo(0)
    }

    @Test
    fun countPersonalRecords_firstEverPerformanceIsNotARecord() {
        val first = session("first", THIS_WEEK, sets = listOf(completedSet(1, 100.0, 5)))

        assertThat(calculator.countPersonalRecords(first, listOf(first))).isEqualTo(0)
    }

    @Test
    fun countPersonalRecords_usesRepsForBodyweightWork() {
        val older = session("older", PREV_WEEK, exerciseId = "pull-ups", sets = listOf(completedSet(1, null, 8)))
        val moreReps = session("more-reps", THIS_WEEK, exerciseId = "pull-ups", sets = listOf(completedSet(1, null, 10)))

        assertThat(calculator.countPersonalRecords(moreReps, listOf(older))).isEqualTo(1)
    }

    @Test
    fun countPersonalRecords_comparesAcrossDifferentlyLoggedUnits() {
        val metricPriorBest = session(
            "metric", PREV_WEEK, sets = listOf(completedSet(1, 100.0, 5)), weightUnit = WeightUnit.KG
        )
        val imperialAttempt = session(
            "imperial", THIS_WEEK, sets = listOf(completedSet(1, 200.0, 5)), weightUnit = WeightUnit.LBS
        )

        assertThat(calculator.countPersonalRecords(imperialAttempt, listOf(metricPriorBest))).isEqualTo(0)
    }

    @Test
    fun countPersonalRecords_ignoresIncompleteSets() {
        val older = session("older", PREV_WEEK, sets = listOf(completedSet(1, 45.0, 10)))
        val abandoned = session("abandoned", THIS_WEEK, sets = listOf(openSet(1, 500.0, 10)))

        assertThat(calculator.countPersonalRecords(abandoned, listOf(older))).isEqualTo(0)
    }

    private fun session(
        id: String,
        completedAt: Long,
        exerciseId: String = "incline-dumbbell-press",
        sets: List<WorkoutSet> = listOf(completedSet(1, 45.0, 10)),
        weightUnit: WeightUnit = WeightUnit.LBS
    ): WorkoutSession {
        val workoutExerciseId = "$id-exercise"
        return WorkoutSession(
            id = id,
            name = "Workout $id",
            completedAtTimestamp = completedAt,
            actualDurationMinutes = 45,
            weightUnit = weightUnit,
            status = SessionStatus.COMPLETED,
            exercises = listOf(
                WorkoutExercise(
                    id = workoutExerciseId,
                    sessionId = id,
                    exerciseId = exerciseId,
                    orderIndex = 0,
                    targetSets = sets.size,
                    targetRepMin = 8,
                    targetRepMax = 10,
                    sets = sets.map { it.copy(workoutExerciseId = workoutExerciseId) }
                )
            )
        )
    }

    private fun completedSet(
        setNumber: Int,
        weight: Double?,
        reps: Int,
        type: SetType = SetType.NORMAL
    ) = WorkoutSet(
        id = "set-$setNumber-$weight-$reps-$type",
        workoutExerciseId = "exercise",
        setNumber = setNumber,
        exerciseType = ExerciseType.WEIGHT_REPS,
        targetReps = reps,
        completedReps = reps,
        targetWeight = weight,
        completedWeight = weight,
        isCompleted = true,
        type = type
    )

    private fun bodyweightSet(setNumber: Int, reps: Int) = WorkoutSet(
        id = "bw-$setNumber",
        workoutExerciseId = "exercise",
        setNumber = setNumber,
        exerciseType = ExerciseType.BODYWEIGHT_REPS,
        targetReps = reps,
        completedReps = reps,
        isCompleted = true
    )

    private fun timedSet(setNumber: Int) = WorkoutSet(
        id = "timed-$setNumber",
        workoutExerciseId = "exercise",
        setNumber = setNumber,
        exerciseType = ExerciseType.DURATION,
        completedDurationSeconds = 45,
        isCompleted = true
    )

    private fun openSet(setNumber: Int, weight: Double = 60.0, reps: Int = 10) =
        completedSet(setNumber, weight, reps).copy(isCompleted = false)

    private fun skippedSet(setNumber: Int) = WorkoutSet(
        id = "skipped-$setNumber",
        workoutExerciseId = "exercise",
        setNumber = setNumber,
        exerciseType = ExerciseType.WEIGHT_REPS,
        isCompleted = false,
        stopReason = SetStopReason.USER_SKIPPED,
        stoppedAtTimestamp = THIS_WEEK
    )

    private companion object {
        val DEFAULT_LOCALE: Locale = Locale.getDefault()
        val ZONE: ZoneId = ZoneId.of("UTC")
        const val DAY_MILLIS = 24 * 60 * 60 * 1_000L

        // Current ISO week (UTC): Monday 2026-08-31 00:00 .. Monday 2026-09-07 00:00.
        val NOW = Instant.parse("2026-09-02T12:00:00Z").toEpochMilli() // Wednesday, mid-week
        val THIS_WEEK = Instant.parse("2026-09-01T09:00:00Z").toEpochMilli() // Tuesday
        val THIS_WEEK_2 = Instant.parse("2026-09-02T06:00:00Z").toEpochMilli() // Wednesday
        val LATER_THIS_WEEK_AFTER_NOW = Instant.parse("2026-09-04T09:00:00Z").toEpochMilli() // Friday
        val WEEK_START = Instant.parse("2026-08-31T00:00:00Z").toEpochMilli() // Monday 00:00 inclusive
        val NEXT_WEEK_START = Instant.parse("2026-09-07T00:00:00Z").toEpochMilli() // next Monday, exclusive
        val PREV_WEEK = Instant.parse("2026-08-26T09:00:00Z").toEpochMilli() // previous Wednesday
        val TWO_WEEKS_AGO = Instant.parse("2026-08-19T09:00:00Z").toEpochMilli()
    }
}
