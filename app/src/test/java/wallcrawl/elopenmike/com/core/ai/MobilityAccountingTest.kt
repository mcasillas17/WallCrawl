package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.AnalyticsMetric
import wallcrawl.elopenmike.com.core.model.AnalyticsRange
import wallcrawl.elopenmike.com.core.model.AnalyticsScope
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseRole
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.ProgressionReason
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.TrainingWeek
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.mainExercises
import wallcrawl.elopenmike.com.core.progress.ProgressAnalyticsCalculator
import wallcrawl.elopenmike.com.core.progress.PersonalRecordBaseline
import wallcrawl.elopenmike.com.core.progress.ProgressCalculator

/**
 * A routine may use accepted strength exercises such as `clamshell`; logged as MOBILITY work
 * they must earn no training credit anywhere, while the same exercise logged as MAIN still does.
 */
class MobilityAccountingTest {
    private val utc = ZoneId.of("UTC")
    private val week = TrainingWeek.startingOn(MONDAY_EPOCH_DAY, utc)
    private val now = Instant.ofEpochMilli(week.startEpochMillis + 2 * DAY + 12 * HOUR)
    private val bench = syntheticApprovedExercise("bench", "Chest", legacyPrimaryMuscles = listOf("Chest"))
    private val clamshell = syntheticApprovedExercise("clamshell", "Glutes", legacyPrimaryMuscles = listOf("Glutes"))
    private val catalog = listOf(bench, clamshell)
    private val byId = catalog.associateBy { it.id }
    private val prescription = ExercisePrescription(ExerciseType.WEIGHT_REPS, 2, RepRange(8, 12), targetWeight = 40.0)

    private fun WorkoutSession.asMobility() = copy(exercises = exercises.map { it.copy(role = ExerciseRole.MOBILITY) })

    /** One completed, manageable, two-set session of [exerciseId] started at [start]. */
    private fun session(
        id: String, exerciseId: String, start: Long, role: ExerciseRole = ExerciseRole.MAIN, weight: Double = 40.0
    ) = progressionSession(id, exerciseId, prescription.copy(targetWeight = weight), start).let { s ->
            s.copy(exercises = s.exercises.map { it.copy(role = role) }, focusMuscles = listOf("Chest"))
        }

    private fun inWeek(id: String, exerciseId: String, role: ExerciseRole, dayOffset: Long = 1) =
        session(id, exerciseId, week.startEpochMillis + dayOffset * DAY, role)

    @Test fun earnsNoWeeklyLedgerCredit() {
        val mobilityOnly = inWeek("m", "clamshell", ExerciseRole.MOBILITY)
        val ledger = ledger(listOf(mobilityOnly))
        assertThat(ledger.directPrimarySets).isEmpty()
        assertThat(ledger.secondaryInvolvement).isEmpty()
        assertThat(ledger.unattributedWorkSets).isEmpty()
        // Beside real work the mobility row neither adds sets nor trips the duplicate-id guard.
        val main = inWeek("b", "bench", ExerciseRole.MAIN)
        val mixed = main.copy(exercises = main.exercises + mobilityRow(main.id, "mixed-mobility"))
        assertThat(ledger(listOf(mixed))).isEqualTo(ledger(listOf(main)))
    }

    @Test fun earnsNoSchedulingPracticeDate() {
        val policy = TrainingFrequencyRecencyPolicy()
        val mobility = listOf(-3L, -2L).map { day ->
            daySession("m$day", "clamshell", day, ExerciseRole.MOBILITY)
        }
        assertThat(policy.derive(mobility, catalog, now, utc).practiceDates).isEmpty()
        // A mobility row on the same instant in a main session adds no date for its muscle.
        val mixed = daySession("x", "bench", -2, ExerciseRole.MAIN).let {
            it.copy(exercises = it.exercises + mobilityRow(it.id, "x-mobility"))
        }
        assertThat(policy.derive(listOf(mixed), catalog, now, utc).practiceDates.keys).containsExactly("Chest")
    }

    @Test fun earnsNoCapabilityEvidence() {
        val mobility = listOf(session("a", "clamshell", 10_000, ExerciseRole.MOBILITY),
            session("b", "clamshell", 20_000, ExerciseRole.MOBILITY))
        assertThat(CapabilityEvidencePolicy().derive(mobility, catalog).appliesTo("clamshell")).isFalse()
    }

    @Test fun doesNotCountAsAProgressionAttempt() {
        val engine = ProgressionEngine()
        fun run(sessions: List<WorkoutSession>) =
            engine.evaluate("clamshell", prescription, sessions, WeightUnit.KG, 1_000_000, "a".repeat(64))
        val mobility = listOf(session("a", "clamshell", 10_000, ExerciseRole.MOBILITY),
            session("b", "clamshell", 20_000, ExerciseRole.MOBILITY))
        assertThat(run(mobility).reason).isEqualTo(ProgressionReason.HOLD_INSUFFICIENT_HISTORY)
        // A newer mobility session does not displace the two real attempts.
        val main = listOf(session("a", "clamshell", 10_000), session("b", "clamshell", 20_000))
        val withNewerMobility = main + session("c", "clamshell", 30_000, ExerciseRole.MOBILITY)
        assertThat(run(main).reason).isEqualTo(ProgressionReason.ADVANCED)
        assertThat(run(withNewerMobility)).isEqualTo(run(main))
    }

    @Test fun doesNotSupplyARecordedLoad() {
        fun load(session: WorkoutSession) = WorkoutGenerationContext(
            userProfile = UserProfile(), recentWorkoutHistory = listOf(session)
        ).recordedWorkLoad("clamshell", ExerciseType.WEIGHT_REPS)
        assertThat(load(session("m", "clamshell", 10_000, ExerciseRole.MOBILITY))).isNull()
        // The fixture is logged in kg and the default profile plans in lbs.
        assertThat(load(session("n", "clamshell", 10_000))).isWithin(1e-6).of(40.0 * 2.2046226218487757)
    }

    @Test fun doesNotAppearInExerciseHistoryOrRecentMuscles() {
        val analyzer = WorkoutHistoryAnalyzer()
        val mobility = inWeek("m", "clamshell", ExerciseRole.MOBILITY).copy(focusMuscles = listOf("Glutes"))
        assertThat(analyzer.exerciseHistory(listOf(mobility))).isEmpty()
        assertThat(analyzer.latestCompletedExercisePerformance(listOf(mobility), "clamshell")).isNull()
        // A mobility row in the logger compares with mobility history of its own role.
        assertThat(analyzer.latestCompletedExercisePerformance(listOf(mobility), "clamshell", ExerciseRole.MOBILITY))
            .isNotNull()
        assertThat(analyzer.latestCompletedExercisePerformance(
            listOf(inWeek("n", "clamshell", ExerciseRole.MAIN)), "clamshell", ExerciseRole.MOBILITY
        )).isNull()
        assertThat(analyzer.recentlyTrainedMuscles(listOf(mobility), requireNotNull(mobility.completedAtTimestamp)))
            .isEmpty()
        val main = inWeek("b", "bench", ExerciseRole.MAIN).copy(focusMuscles = listOf("Chest"))
        val mixed = main.copy(exercises = main.exercises + mobilityRow(main.id, "mixed-mobility"))
        assertThat(analyzer.exerciseHistory(listOf(mixed))).isEqualTo(analyzer.exerciseHistory(listOf(main)))
        assertThat(analyzer.recentlyTrainedMuscles(listOf(mixed), requireNotNull(mixed.completedAtTimestamp)))
            .containsExactly("Chest")
    }

    @Test fun doesNotAddVolumeOrFrequencyToProgress() {
        val calculator = ProgressCalculator()
        val main = inWeek("b", "bench", ExerciseRole.MAIN)
        val mixed = main.copy(exercises = main.exercises + mobilityRow(main.id, "mixed-mobility"))
        fun overview(vararg s: WorkoutSession) = calculator.calculate(
            completedSessions = s.toList(), profile = UserProfile(preferredUnit = WeightUnit.KG),
            catalogExercises = catalog, nowTimestamp = now.toEpochMilli(), zoneId = utc
        )
        val expected = overview(main)
        val actual = overview(mixed)
        assertThat(actual.totalVolumeThisWeek).isEqualTo(expected.totalVolumeThisWeek)
        assertThat(actual.totalRepsThisWeek).isEqualTo(expected.totalRepsThisWeek)
        assertThat(actual.completedSetsThisWeek).isEqualTo(expected.completedSetsThisWeek)
        assertThat(actual.legacyPrimaryActivity).isEqualTo(expected.legacyPrimaryActivity)

        // A session with only mobility is not a workout for the count or the streak, yet stays in history.
        val mobilityOnly = inWeek("m", "clamshell", ExerciseRole.MOBILITY)
        val only = overview(mobilityOnly)
        assertThat(only.workoutsThisWeek).isEqualTo(0)
        assertThat(only.totalWorkoutsLogged).isEqualTo(0)
        assertThat(only.currentStreakWeeks).isEqualTo(0)
        assertThat(only.recentHistory.map { it.id }).containsExactly("m")
        assertThat(only.totalVolumeThisWeek).isEqualTo(0.0)
        assertThat(only.legacyPrimaryActivity).isEmpty()
        assertThat(overview(main, mobilityOnly).workoutsThisWeek).isEqualTo(1)

        fun analytics(vararg s: WorkoutSession, scope: AnalyticsScope = AnalyticsScope.All) =
            ProgressAnalyticsCalculator().calculate(
                AnalyticsRange.WEEKS_4, scope, s.toList(), emptySet(), byId, SYNTHETIC_CATALOG_VERSION, 1,
                WeightUnit.KG, now, utc
            )
        val analyticsExpected = analytics(main)
        val analyticsActual = analytics(mixed)
        AnalyticsMetric.entries.forEach {
            assertThat(analyticsActual.series.getValue(it)).isEqualTo(analyticsExpected.series.getValue(it))
        }
        val analyticsOnly = analytics(mobilityOnly)
        AnalyticsMetric.entries.forEach {
            assertThat(analyticsOnly.series.getValue(it).buckets.sumOf { b -> b.value }).isEqualTo(0.0)
        }
        assertThat(analytics(mobilityOnly, scope = AnalyticsScope.Muscle("Glutes"))
            .series.getValue(AnalyticsMetric.COMPLETED_SETS).buckets.sumOf { it.value }).isEqualTo(0.0)
    }

    @Test fun personalRecordsAndTrendsIgnoreMobilityRows() {
        val calculator = ProgressCalculator()
        // Heavy mobility history must neither hide a real record nor create one.
        val priorMain = session("p1", "bench", 10_000, weight = 30.0)
        val priorMobility = session("p2", "bench", 20_000, ExerciseRole.MOBILITY, weight = 100.0)
        val viewedMain = session("v", "bench", 30_000, weight = 40.0)
        val viewed = viewedMain.copy(exercises = viewedMain.exercises +
            mobilityRow("v", "v-mobility", "clamshell", weight = 100.0))
        assertThat(calculator.countPersonalRecords(viewed, listOf(priorMain, priorMobility))).isEqualTo(1)
        assertThat(calculator.countPersonalRecords(viewed, listOf(priorMain))).isEqualTo(1)
        val mobilityPrior = session("p3", "clamshell", 5_000, ExerciseRole.MOBILITY, weight = 10.0)
        assertThat(calculator.countPersonalRecords(viewed, listOf(priorMain, mobilityPrior))).isEqualTo(1)

        fun baseline(id: String, kg: Double) =
            PersonalRecordBaseline(id, ExerciseType.WEIGHT_REPS, WeightUnit.KG, kg, 12)
        assertThat(calculator.countPersonalRecordsFromBaselines(
            viewed, listOf(baseline("bench", 30.0), baseline("clamshell", 50.0))
        )).isEqualTo(1)

        val sessions = listOf(
            inWeek("r1", "bench", ExerciseRole.MAIN, 0).copy(),
            inWeek("r2", "bench", ExerciseRole.MAIN, 1)
        ).mapIndexed { i, s -> s.copy(exercises = s.exercises.map { e -> e.heavier(if (i == 0) 30.0 else 40.0) }) } +
            listOf(inWeek("m1", "clamshell", ExerciseRole.MOBILITY, 0), inWeek("m2", "clamshell", ExerciseRole.MOBILITY, 1))
                .mapIndexed { i, s -> s.copy(exercises = s.exercises.map { e -> e.heavier(if (i == 0) 50.0 else 100.0) }) }
        val overview = calculator.calculate(
            completedSessions = sessions, profile = UserProfile(preferredUnit = WeightUnit.KG),
            catalogExercises = catalog, nowTimestamp = now.toEpochMilli(), zoneId = utc
        )
        assertThat(overview.recentPersonalRecords.map { it.exerciseId }).containsExactly("bench")
        assertThat(overview.strengthTrends.map { it.exerciseId }).containsExactly("bench")
    }

    private fun WorkoutExercise.heavier(kg: Double) = copy(
        prescription = prescription.copy(targetWeight = kg),
        sets = sets.map { it.copy(targetWeight = kg, completedWeight = kg) }
    )

    @Test fun doesNotChangeTheLedgerFingerprintOfOtherWork() {
        val main = inWeek("b", "bench", ExerciseRole.MAIN)
        val mixed = main.copy(exercises = main.exercises + mobilityRow(main.id, "mixed-mobility"))
        fun fingerprint(s: WorkoutSession) = LedgerSourceFingerprint.of(
            listOf(s), byId, LedgerPolicyVersion.PRIMARY_ONLY_V1, week, SYNTHETIC_CATALOG_VERSION, 1
        )
        assertThat(fingerprint(mixed)).isEqualTo(fingerprint(main))
    }

    @Test fun mainClamshellInTheSameDataStillEarnsCredit() {
        val main = inWeek("main", "clamshell", ExerciseRole.MAIN)
        val mobility = inWeek("mobility", "clamshell", ExerciseRole.MOBILITY, dayOffset = 2)
        assertThat(ledger(listOf(main, mobility)).directPrimarySets).containsExactly("Glutes", 2)
        val policy = TrainingFrequencyRecencyPolicy()
        val dated = listOf(-3L, -2L).map { daySession("m$it", "clamshell", it, ExerciseRole.MAIN) } +
            daySession("mob", "clamshell", -1, ExerciseRole.MOBILITY)
        assertThat(policy.derive(dated, catalog, now, utc).practiceDates.getValue("Glutes")).hasSize(2)
        val evidence = CapabilityEvidencePolicy().derive(
            listOf(session("a", "clamshell", 10_000), session("b", "clamshell", 20_000),
                session("c", "clamshell", 30_000, ExerciseRole.MOBILITY)), catalog
        )
        assertThat(evidence["clamshell"]?.qualifyingSessionIds).containsExactly("a", "b")
        // The same exercise as MAIN and MOBILITY inside one session: only the main row counts.
        val both = main.copy(exercises = main.exercises + mobilityRow(main.id, "both-mobility", "clamshell"))
        assertThat(ledger(listOf(both)).directPrimarySets).containsExactly("Glutes", 2)
        assertThat(both.mainExercises).isEqualTo(main.exercises)
    }

    @Test fun mainExercisesPreservesOrderAndDataWithoutMobility() {
        val main = inWeek("b", "bench", ExerciseRole.MAIN)
        val ordered = main.copy(exercises = listOf(
            main.exercises.single().copy(id = "z", orderIndex = 2),
            main.exercises.single().copy(id = "a", orderIndex = 0)
        ))
        assertThat(ordered.mainExercises).isEqualTo(ordered.exercises)
    }

    private fun ledger(sessions: List<WorkoutSession>) = WeeklyDoseLedgerCalculator().calculate(
        sessions, byId, LedgerPolicyVersion.PRIMARY_ONLY_V1, week, SYNTHETIC_CATALOG_VERSION, 1
    )

    private fun daySession(id: String, exerciseId: String, day: Long, role: ExerciseRole): WorkoutSession {
        val completedAt = now.toEpochMilli() + day * DAY
        val base = session(id, exerciseId, completedAt - 1_000, role)
        return base.copy(
            completedAtTimestamp = completedAt,
            exercises = base.exercises.map { e ->
                e.copy(sets = e.sets.map { it.copy(completedAtTimestamp = null) })
            }
        )
    }

    private fun mobilityRow(
        sessionId: String, id: String, exerciseId: String = "clamshell", weight: Double = 40.0
    ): WorkoutExercise =
        progressionSession("$id-src", exerciseId, prescription.copy(targetWeight = weight), 1).exercises.single().let { e ->
            e.copy(
                id = id, sessionId = sessionId, orderIndex = 9, role = ExerciseRole.MOBILITY,
                sets = e.sets.map { it.copy(id = "$id-${it.id}", workoutExerciseId = id) }
            )
        }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
    }
}
