package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertThrows
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.*

class TrainingFrequencyRecencyPolicyTest {
    @Test
    fun staleFutureOnlyAndOnceSeenAreNeutralAndPathologicalHistoryFailsClosed() {
        val sessions = listOf(practice("old-a", -15), practice("old-b", -14),
            practice("future-a", 1), practice("future-b", 2))
        assertThat(policy.derive(sessions, catalog, now, zone).practiceDates).isEmpty()
        assertThat(policy.preferredMuscles(policy.derive(listOf(practice("once", -10)), catalog, now, zone), 6)).isEmpty()
        assertThrows(IllegalArgumentException::class.java) {
            policy.derive((0..TrainingFrequencyRecencyPolicy.MAX_SESSIONS).map { practice("many-$it", -2) },
                catalog, now, zone)
        }
    }

    @Test
    fun calendarRevisitIsNotAFixedNumberOfHoursAcrossSpringAndAutumnDst() {
        for (instant in listOf(Instant.parse("2026-03-09T07:00:00Z"), Instant.parse("2026-11-02T08:00:00Z"))) {
            val date = instant.atZone(zone).toLocalDate()
            val history = listOf(2L, 3L).map { age ->
                practice("dst-$age", -age).copy(completedAtTimestamp =
                    date.minusDays(age).atTime(23, 59).atZone(zone).toInstant().toEpochMilli())
            }
            val evidence = policy.derive(history, catalog, instant, zone)
            // Last local date is two dates ago despite fewer than 48 elapsed hours.
            assertThat(policy.preferredMuscles(evidence, 6)).containsExactly("Shoulders")
        }
    }
    private val policy = TrainingFrequencyRecencyPolicy()
    private val catalog = PlannerFixtureContextFactory().bundledCatalogProjection().exercises
    private val now = Instant.parse("2026-03-09T07:30:00Z") // Monday 00:30, after DST jump.
    private val zone = ZoneId.of("America/Los_Angeles")
    private val today = now.atZone(zone).toLocalDate()

    @Test
    fun distinctLocalDatesNotSessionCountEstablishFamiliarityAndBinaryPreference() {
        val sameDateLater = practice("c", -3).let {
            it.copy(completedAtTimestamp = requireNotNull(it.completedAtTimestamp) + 3_600_000)
        }
        val sessions = listOf(practice("a", -4), practice("b", -3), sameDateLater)
        val evidence = policy.derive(sessions, catalog, now, zone)
        assertThat(evidence.practiceDates.getValue("Shoulders"))
            .containsExactly(today.minusDays(4).toEpochDay(), today.minusDays(3).toEpochDay()).inOrder()
        assertThat(policy.preferredMuscles(evidence, 2)).isEmpty()
        assertThat(policy.preferredMuscles(evidence, 6)).containsExactly("Shoulders")
        assertThat(policy.preferredMuscles(policy.derive(sessions.takeLast(2), catalog, now, zone), 6))
            .isEmpty()
        assertThat(policy.preferredMuscles(policy.derive(sessions + practice("recent", -1), catalog, now, zone), 6))
            .isEmpty()
        assertThat((2..6).map(policy::revisitBandDays)).containsExactly(4, 3, 2, 2, 2).inOrder()
        for (days in listOf(0, 1, 7, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { policy.revisitBandDays(days) }
        }
    }

    @Test
    fun windowIncludesLocalStartThirteenDatesAgoAndNowButExcludesStaleAndFuture() {
        val start = today.minusDays(13).atStartOfDay(zone).toInstant().toEpochMilli()
        val sessions = listOf(
            practice("start", -13).copy(completedAtTimestamp = start),
            practice("stale", -14).copy(completedAtTimestamp = start - 1),
            practice("now", 0).copy(completedAtTimestamp = now.toEpochMilli()),
            practice("future", 0).copy(completedAtTimestamp = now.toEpochMilli() + 1)
        )
        val evidence = policy.derive(sessions, catalog, now, zone)
        assertThat(evidence.practiceDates.getValue("Shoulders"))
            .containsExactly(today.minusDays(13).toEpochDay(), today.toEpochDay()).inOrder()
        assertThat(policy.windowStart(now, zone)).isEqualTo(start)
        assertThat(policy.derive(sessions.reversed(), catalog.reversed(), now, zone)).isEqualTo(evidence)
        assertThat(evidence.timeZoneId).isEqualTo(zone.id)
        assertThat(policy.derive(listOf(sessions.last()), catalog, now, zone).practiceDates).isEmpty()
    }

    @Test
    fun onlyExactAcceptedPrimaryCompletedWorkCountsAndMissingTimestampsStayMissing() {
        val ordinary = practice("work", -3)
        val stopped = ordinary.exercises.single().sets.single().copy(
            id = "stopped", isCompleted = false, stopReason = SetStopReason.PAIN_STOP,
            stoppedAtTimestamp = now.minusSeconds(100).toEpochMilli()
        )
        val partial = ordinary.copy(exercises = listOf(ordinary.exercises.single().copy(
            sets = ordinary.exercises.single().sets + stopped
        )))
        val warmup = practice("warmup", -2).mapSets { it.copy(type = SetType.WARMUP) }
        val unfinished = practice("unfinished", -1).mapSets { it.copy(isCompleted = false) }
        val cancelled = practice("cancelled", -5).copy(status = SessionStatus.CANCELLED)
        val active = practice("active", -6).copy(status = SessionStatus.IN_PROGRESS)
        val unknown = practice("unknown", -7, "DUMBBELL-SHOULDER-PRESS")
        val draft = practice("draft", -8, "plank")
        val result = policy.derive(listOf(partial, warmup, unfinished, cancelled, active, unknown, draft),
            catalog, now, zone)
        assertThat(result.practiceDates).containsExactly("Shoulders", listOf(today.minusDays(3).toEpochDay()))
        assertThat(partial.exercises.single().sets.first().completedAtTimestamp).isNull()
        assertThat(result.practiceDates).doesNotContainKey("Chest") // session label is deliberately false
    }

    @Test
    fun malformedDuplicateAndContradictoryWorkIsRejectedRatherThanQuietlyCounted() {
        val session = practice("a", -3)
        for (sessions in listOf(
            listOf(session, session),
            listOf(session.copy(completedAtTimestamp = null)),
            listOf(session.mapSets { it.copy(stopReason = SetStopReason.PAIN_STOP) }),
            listOf(session.copy(exercises = session.exercises + session.exercises)),
            listOf(session.copy(exercises = listOf(session.exercises.single().copy(
                sets = session.exercises.single().sets + session.exercises.single().sets))))
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                policy.derive(sessions, catalog, now, zone)
            }
        }
    }

    @Test
    fun differentZoneUsesDifferentCalendarDatesAndSummaryCannotBeMutated() {
        val sessions = mutableListOf(practice("a", -4), practice("b", -3))
        val evidence = policy.derive(sessions, catalog, now, zone)
        sessions.clear()
        assertThat(evidence.practiceDates).isNotEmpty()
        assertThrows(UnsupportedOperationException::class.java) {
            (evidence.practiceDates as MutableMap<*, *>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (evidence.practiceDates.getValue("Shoulders") as MutableList<*>).clear()
        }
        assertThat(policy.derive(emptyList(), catalog, now, ZoneId.of("Pacific/Honolulu")).todayEpochDay)
            .isEqualTo(today.minusDays(1).toEpochDay())
    }

    @Test
    fun everyCompletedWorkSetTypeCountsWithoutAnyMagnitudeOrEffortCriterion() {
        for (type in SetType.entries) {
            val sessions = listOf(practice("first", -4), practice("second", -3))
                .map { it.mapSets { set -> set.copy(type = type) } }
            val evidence = policy.derive(sessions, catalog, now, zone)
            assertThat(policy.preferredMuscles(evidence, 6))
                .containsExactlyElementsIn(if (type == SetType.WARMUP) emptyList() else listOf("Shoulders"))
        }
    }

    private fun practice(id: String, day: Long, exerciseId: String = "dumbbell-shoulder-press") =
        completedSession(
            id = id,
            completedAtEpochMillis = today.plusDays(day).atStartOfDay(zone).toInstant().toEpochMilli(),
            exercises = listOf(exerciseInstance(
                id = "$id-ex", exerciseId = exerciseId,
                sets = listOf(completedNormalSet("$id-set").copy(
                    completedAtTimestamp = null, completedWeight = null, completedReps = null,
                    feltManageable = null, rpe = null, rir = null
                ))
            ))
        ).copy(focusMuscles = listOf("Chest"))

    private fun WorkoutSession.mapSets(transform: (WorkoutSet) -> WorkoutSet) =
        copy(exercises = exercises.map { it.copy(sets = it.sets.map(transform)) })
}
