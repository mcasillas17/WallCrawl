package wallcrawl.elopenmike.com.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertThrows
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.database.entity.WorkoutExerciseEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSessionEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSetEntity
import wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutHistoryRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineLocalDataBackupRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutHistoryDetail
import wallcrawl.elopenmike.com.core.database.repository.HistoricalSourceKey
import wallcrawl.elopenmike.com.core.backup.LocalDataArchiveLimits
import wallcrawl.elopenmike.com.core.database.repository.toEntity
import wallcrawl.elopenmike.com.core.model.ProgressionProvenance
import wallcrawl.elopenmike.com.core.model.ProgressionReason
import wallcrawl.elopenmike.com.core.model.ProgressionReasonCode
import wallcrawl.elopenmike.com.core.model.RecommendationRecord
import wallcrawl.elopenmike.com.core.model.SetStopReason
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.WeightUnit

@RunWith(AndroidJUnit4::class)
class WorkoutHistoryRepositoryTest {
    private lateinit var db: WallCrawlDatabase
    private val gate = Mutex()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), WallCrawlDatabase::class.java
        ).build()
    }

    @After fun tearDown() { db.close() }

    @Test fun ordinaryComparisonsDoNotLoad60000UnrelatedPriorSets() = runBlocking {
        assertLargePriorComparisons(citations = false)
    }

    @Test fun recordedCitationsDoNotLoad60000UnrelatedPriorSets() = runBlocking {
        assertLargePriorComparisons(citations = true)
    }

    private suspend fun assertLargePriorComparisons(citations: Boolean) {
        repeat(6) {
            insert("large-prior-$it", 100, 200, exerciseId = "lift-$it",
                unit = if (it % 2 == 0) WeightUnit.KG else WeightUnit.LBS)
        }
        val sql = db.openHelper.writableDatabase
        sql.execSQL("""
            WITH RECURSIVE n(value) AS (SELECT 1 UNION ALL SELECT value + 1 FROM n WHERE value < 99)
            INSERT INTO workout_exercises
                (id, sessionId, exerciseId, orderIndex, exerciseType, targetSets,
                 targetRepMin, targetRepMax, targetWeight, restSeconds, notes)
            SELECT s.id || '-unrelated-' || n.value, s.id, 'unrelated-' || n.value, n.value,
                'WEIGHT_REPS', 20, 8, 10, 20, 90, ''
            FROM workout_sessions s CROSS JOIN n WHERE s.id LIKE 'large-prior-%'
        """.trimIndent())
        sql.execSQL("UPDATE workout_exercises SET targetSets = 20 WHERE sessionId LIKE 'large-prior-%'")
        sql.execSQL("DELETE FROM workout_sets")
        sql.execSQL("""
            WITH RECURSIVE n(value) AS (SELECT 1 UNION ALL SELECT value + 1 FROM n WHERE value < 100)
            INSERT INTO workout_sets
                (id, workoutExerciseId, setNumber, exerciseType, targetReps, targetWeight,
                 completedReps, completedWeight, isCompleted, type)
            SELECT e.id || '-set-' || n.value, e.id, n.value, 'WEIGHT_REPS', 10, 20,
                CASE WHEN e.orderIndex = 0 AND n.value = 1 THEN 10 END,
                CASE WHEN e.orderIndex = 0 AND n.value = 1 THEN 20 END,
                CASE WHEN e.orderIndex = 0 AND n.value = 1 THEN 1 ELSE 0 END,
                CASE WHEN n.value <= 20 THEN 'NORMAL' ELSE 'WARMUP' END
            FROM workout_exercises e CROSS JOIN n WHERE e.sessionId LIKE 'large-prior-%'
        """.trimIndent())
        insert("viewed", 300, 400, exerciseId = "lift-0")
        val original = db.workoutSessionDao().getSessionWithDetails("viewed")!!.exercisesWithSets.single()
        (1..5).forEach { index ->
            val exercise = original.exercise.copy(id = "viewed-$index", exerciseId = "lift-$index", orderIndex = index)
            db.workoutSessionDao().insertWorkoutExercises(listOf(exercise))
            db.workoutSessionDao().insertWorkoutSets(listOf(original.sets.single().copy(
                id = "viewed-set-$index", workoutExerciseId = exercise.id
            )))
        }
        if (citations) {
            val reasons = (0..5).map { index ->
                ProgressionProvenance("lift-$index", ProgressionReason.HOLD_INSUFFICIENT_HISTORY,
                    null, listOf("large-prior-$index"), "a".repeat(64))
            }
            db.workoutSessionDao().insertRecommendationRecord(
                record("viewed", ProgressionReasonCode.encode(reasons)).toEntity()
            )
        }
        val before = historyRowsDigest()
        val detail = requireNotNull(history().observeDetail("viewed").first())
        assertThat(detail.previousPerformances).hasSize(6)
        detail.previousPerformances.values.forEach {
            assertThat(it.exercise.sets).hasSize(1)
            assertThat(it.exercise.sets.single().completedWeight).isEqualTo(20.0)
            assertThat(it.exercise.sets.single().targetWeight).isEqualTo(20.0)
            assertThat(it.session.weightUnit).isEqualTo(
                if (it.session.id.last().digitToInt() % 2 == 0) WeightUnit.KG else WeightUnit.LBS
            )
        }
        assertThat(detail.progressionSources).hasSize(if (citations) 6 else 0)
        detail.progressionSources.values.forEach { source ->
            assertThat(source.exercise.sets).hasSize(1)
            assertThat(source.exercise.sessionId).isEqualTo(source.session.id)
        }
        assertThat(historyRowsDigest()).isEqualTo(before)
    }

    /** Stream a fingerprint of original rows without materializing the 60,000-set fixture. */
    private fun historyRowsDigest(): List<Byte> {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        for (table in listOf("workout_sessions", "workout_exercises", "workout_sets", "workout_recommendation_records")) {
            val key = if (table == "workout_recommendation_records") "sessionId" else "id"
            db.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY $key").use { cursor ->
                while (cursor.moveToNext()) for (column in 0 until cursor.columnCount) {
                    val value = if (cursor.isNull(column)) "<null>" else cursor.getString(column)
                    digest.update("${value.length}:$value;".toByteArray())
                }
            }
        }
        return digest.digest().toList()
    }

    @Test fun browsingLargeValidSessionsDoesNotApplyAnArchiveWideSetLimitToThePage() = runBlocking {
        repeat(6) { insert("large-page-$it", 100, 200) }
        val sql = db.openHelper.writableDatabase
        sql.execSQL("""
            WITH RECURSIVE n(value) AS (SELECT 1 UNION ALL SELECT value + 1 FROM n WHERE value < 99)
            INSERT INTO workout_exercises
                (id, sessionId, exerciseId, orderIndex, exerciseType, targetSets,
                 targetRepMin, targetRepMax, targetWeight, restSeconds, notes)
            SELECT s.id || '-exercise-' || n.value, s.id, 'lift-' || n.value, n.value,
                'WEIGHT_REPS', 20, 8, 10, 20, 90, ''
            FROM workout_sessions s CROSS JOIN n WHERE s.id LIKE 'large-page-%'
        """.trimIndent())
        sql.execSQL("UPDATE workout_exercises SET targetSets = 20 WHERE sessionId LIKE 'large-page-%'")
        sql.execSQL("""
            DELETE FROM workout_sets WHERE workoutExerciseId IN
                (SELECT id FROM workout_exercises WHERE sessionId LIKE 'large-page-%')
        """.trimIndent())
        sql.execSQL("""
            WITH RECURSIVE n(value) AS (SELECT 1 UNION ALL SELECT value + 1 FROM n WHERE value < 100)
            INSERT INTO workout_sets
                (id, workoutExerciseId, setNumber, exerciseType, targetReps, targetWeight, isCompleted, type)
            SELECT e.id || '-set-' || n.value, e.id, n.value, 'WEIGHT_REPS', 10, 20, 0,
                CASE WHEN n.value <= 20 THEN 'NORMAL' ELSE 'WARMUP' END
            FROM workout_exercises e CROSS JOIN n WHERE e.sessionId LIKE 'large-page-%'
        """.trimIndent())
        val one = requireNotNull(history().observeDetail("large-page-0").first())
        assertThat(one.session.exercises).hasSize(100)
        assertThat(one.session.exercises.sumOf { it.sets.size }).isEqualTo(10_000)
        sql.query("SELECT COUNT(*) FROM workout_sets").use {
            check(it.moveToFirst())
            assertThat(it.getInt(0)).isEqualTo(60_000)
        }
        assertThat(history().observeHistory(0).first().sessions.map { it.id })
            .containsExactlyElementsIn((0..5).map { "large-page-$it" }).inOrder()
    }

    @Test fun ordinaryAndCitedEvidenceAgreeForArchiveValidPerformanceBeyondTargetBounds() = runBlocking {
        listOf(ExerciseType.WEIGHT_REPS to "reps", ExerciseType.BODYWEIGHT_REPS to "reps",
            ExerciseType.ASSISTED_BODYWEIGHT to "reps", ExerciseType.DURATION to "time",
            ExerciseType.DISTANCE_DURATION to "distance", ExerciseType.DISTANCE_DURATION to "distance")
            .forEachIndexed { index, (type, shape) ->
                val priorId = "prior-bounds-$index"
                val viewedId = "viewed-bounds-$index"
                val exerciseId = "bounds-$index"
                insertTyped(priorId, 100, 200, exerciseId, type, shape)
                insertTyped(viewedId, 300, 400, exerciseId, type, shape)
                val set = requireNotNull(db.workoutSetDao().getSetById("$priorId-set"))
                db.workoutSetDao().insertOrUpdateSet(when (type) {
                    ExerciseType.WEIGHT_REPS -> set.copy(completedWeight = 20_000.0)
                    ExerciseType.BODYWEIGHT_REPS -> set.copy(completedReps = LocalDataArchiveLimits.MAX_REPETITIONS)
                    ExerciseType.ASSISTED_BODYWEIGHT -> set.copy(completedAssistanceWeight = LocalDataArchiveLimits.MAX_WEIGHT)
                    ExerciseType.DURATION -> set.copy(completedDurationSeconds = LocalDataArchiveLimits.MAX_DURATION_SECONDS)
                    ExerciseType.DISTANCE_DURATION -> set.copy(completedDistanceMeters =
                        if (index == 4) LocalDataArchiveLimits.MAX_DISTANCE_METERS else 0.05)
                })
                val reason = ProgressionProvenance(exerciseId, ProgressionReason.HOLD_INSUFFICIENT_HISTORY,
                    null, listOf(priorId), "a".repeat(64))
                db.workoutSessionDao().insertRecommendationRecord(
                    record(viewedId, ProgressionReasonCode.encode(listOf(reason))).toEntity()
                )
                val detail = requireNotNull(history().observeDetail(viewedId).first())
                val previous = detail.previousPerformances.getValue("$viewedId-exercise")
                val cited = detail.progressionSources[HistoricalSourceKey(priorId, "$viewedId-exercise")]?.exercise
                assertThat(cited).isNotNull()
                assertThat(requireNotNull(cited).sets).containsExactlyElementsIn(previous.exercise.sets)
            }
    }

    @Test fun citedProjectionEnforcesStrictChronologyAndExactSourceExerciseShape() = runBlocking {
        insertTyped("prior", 100, 200, "run", ExerciseType.DISTANCE_DURATION, "distance")
        insertTyped("viewed", 300, 400, "run", ExerciseType.DISTANCE_DURATION, "distance")
        cite("viewed", "run", "prior")
        suspend fun read() = history().observeDetail("viewed").first()!!.progressionSources
        val prior = db.workoutSessionDao().getSessionWithDetails("prior")!!
        assertThat(read()).hasSize(1)
        for (invalid in listOf(
            prior.session.copy(completedAtTimestamp = 300), // Equal boundary is not earlier.
            prior.session.copy(completedAtTimestamp = 301),
            prior.session.copy(completedAtTimestamp = 50),
            prior.session.copy(completedAtTimestamp = null),
            prior.session.copy(startedAtTimestamp = 0),
            prior.session.copy(status = SessionStatus.CANCELLED)
        )) {
            db.workoutSessionDao().updateSession(invalid)
            assertThat(read()).isEmpty()
        }
        db.workoutSessionDao().updateSession(prior.session)
        val exercise = prior.exercisesWithSets.single().exercise
        for (incompatible in listOf(
            exercise.copy(exerciseId = "other"),
            exercise.copy(targetDistanceMeters = null, targetDurationSeconds = 30),
            exercise.copy(exerciseType = ExerciseType.DURATION, targetDistanceMeters = null, targetDurationSeconds = 30)
        )) {
            db.workoutSessionDao().insertWorkoutExercises(listOf(incompatible))
            db.workoutSessionDao().insertWorkoutSets(prior.exercisesWithSets.single().sets)
            assertThat(read()).isEmpty()
        }
        cite("viewed", "run", "viewed")
        assertThat(read()).isEmpty()
        cite("viewed", "run", "missing")
        assertThat(read()).isEmpty()
    }

    @Test fun citedProjectionKeepsOnlyValidSetsIncludingLegacyNullTimesAndSessionBounds() = runBlocking {
        insertTyped("prior", 100, 200, "body", ExerciseType.BODYWEIGHT_REPS, "reps")
        insertTyped("viewed", 300, 400, "body", ExerciseType.BODYWEIGHT_REPS, "reps")
        cite("viewed", "body", "prior")
        val valid = db.workoutSetDao().getSetById("prior-set")!!
        db.workoutSessionDao().insertWorkoutSets(listOf(
            valid.copy(id = "start", setNumber = 2, completedAtTimestamp = 100),
            valid.copy(id = "end", setNumber = 3, completedAtTimestamp = 200),
            valid.copy(id = "early", setNumber = 4, completedAtTimestamp = 99),
            valid.copy(id = "late", setNumber = 5, completedAtTimestamp = 201),
            valid.copy(id = "warmup", setNumber = 6, type = SetType.WARMUP),
            valid.copy(id = "stopped", setNumber = 7, stopReason = SetStopReason.PAIN_STOP),
            valid.copy(id = "open", setNumber = 8, isCompleted = false),
            valid.copy(id = "missing", setNumber = 9, completedReps = null),
            valid.copy(id = "load", setNumber = 10, completedWeight = 40.0),
            valid.copy(id = "overflow", setNumber = 11, completedReps = LocalDataArchiveLimits.MAX_REPETITIONS + 1)
        ))
        val before = historyRowsDigest()
        val detail = history().observeDetail("viewed").first()!!
        val source = detail.progressionSources.getValue(HistoricalSourceKey("prior", "viewed-exercise"))
        assertThat(source.exercise.sets.map { it.id }).containsExactly("prior-set", "start", "end").inOrder()
        assertThat(source.exercise.sets.first().completedAtTimestamp).isNull()
        assertThat(source).isEqualTo(detail.previousPerformances.getValue("viewed-exercise"))
        assertThat(historyRowsDigest()).isEqualTo(before)
    }

    @Test fun citationsStayPairedWithViewedInstancesIncludingDuplicates() = runBlocking {
        insert("prior", 100, 200)
        insert("viewed", 300, 400)
        val row = db.workoutSessionDao().getSessionWithDetails("viewed")!!.exercisesWithSets.single()
        db.workoutSessionDao().insertWorkoutExercises(listOf(row.exercise.copy(id = "duplicate", orderIndex = 1)))
        db.workoutSessionDao().insertWorkoutSets(listOf(row.sets.single().copy(
            id = "duplicate-set", workoutExerciseId = "duplicate"
        )))
        // The prior has another compatible exercise, but that ID was never cited.
        val other = row.exercise.copy(id = "uncited", sessionId = "prior", exerciseId = "uncited", orderIndex = 1)
        db.workoutSessionDao().insertWorkoutExercises(listOf(other))
        db.workoutSessionDao().insertWorkoutSets(listOf(row.sets.single().copy(
            id = "uncited-set", workoutExerciseId = other.id
        )))
        db.workoutSessionDao().insertWorkoutExercises(listOf(other.copy(id = "viewed-uncited", sessionId = "viewed")))
        cite("viewed", "lift", "prior")
        val detail = history().observeDetail("viewed").first()!!
        assertThat(detail.progressionSources.keys).containsExactly(
            HistoricalSourceKey("prior", "viewed-exercise"), HistoricalSourceKey("prior", "duplicate")
        )
        assertThat(detail.progressionSources.values.toSet()).hasSize(1)
        assertThat(detail.previousPerformances).hasSize(3)
    }

    @Test fun relevantProjectionMetadataFailsStrictlyForOrdinaryAndCitedOnlySources() = runBlocking {
        for (citedOnly in listOf(false, true)) {
            val suffix = citedOnly.toString()
            insertTyped("bad-$suffix", 100, 200, suffix, ExerciseType.DURATION, "time")
            insertTyped("viewed-$suffix", 300, 400, suffix, ExerciseType.DURATION, "time")
            if (citedOnly) {
                insertTyped("good-$suffix", 210, 250, suffix, ExerciseType.DURATION, "time")
                cite("viewed-$suffix", suffix, "bad-$suffix")
            }
            val sql = db.openHelper.writableDatabase
            for ((table, column, id, original) in listOf(
                listOf("workout_sessions", "weightUnit", "bad-$suffix", "LBS"),
                listOf("workout_exercises", "exerciseType", "bad-$suffix-exercise", "DURATION")
            )) {
                sql.execSQL("UPDATE $table SET $column = 'UNKNOWN' WHERE id = ?", arrayOf(id))
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { history().observeDetail("viewed-$suffix").first() }
                }
                sql.execSQL("UPDATE $table SET $column = ? WHERE id = ?", arrayOf(original, id))
            }
        }
    }

    @Test fun actualEligibleProjectionOverflowFailsRatherThanTruncatingEvidence() = runBlocking<Unit> {
        insert("prior", 100, 200)
        insert("viewed", 300, 400)
        cite("viewed", "lift", "prior")
        val set = db.workoutSetDao().getSetById("prior-set")!!
        db.workoutSessionDao().insertWorkoutSets((2..101).map {
            set.copy(id = "eligible-$it", setNumber = it)
        })
        val failure = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { history().observeDetail("viewed").first() }
        }
        assertThat(failure.message).contains("Historical performance sets")
    }

    private suspend fun cite(viewedId: String, exerciseId: String, sourceId: String) {
        val codes = ProgressionReasonCode.encode(listOf(ProgressionProvenance(
            exerciseId, ProgressionReason.HOLD_INSUFFICIENT_HISTORY, null, listOf(sourceId), "a".repeat(64)
        )))
        db.openHelper.writableDatabase.execSQL(
            "DELETE FROM workout_recommendation_records WHERE sessionId = ?", arrayOf(viewedId)
        )
        db.workoutSessionDao().insertRecommendationRecord(record(viewedId, codes).toEntity())
    }

    @Test fun summaryKeepsAnEarlierRecordBeyond500LaterAndUnrelatedWorkouts() = runBlocking {
        insert("prior", 100, 200, weight = 20.0, unit = WeightUnit.KG)
        insert("viewed", 300, 400, weight = 50.0)
        val repository = OfflineWorkoutRepository(db.workoutSessionDao(), db.workoutSetDao())
        assertThat(repository.getWorkoutSummary("viewed")!!.prCount).isEqualTo(1)
        repeat(501) { insert("later-$it", 500L + it, 1_500L + it, exerciseId = "unrelated") }
        assertThat(repository.getWorkoutSummary("viewed")!!.prCount).isEqualTo(1)
        insert("future-stronger", 3_000, 4_000, weight = 200.0)
        assertThat(repository.getWorkoutSummary("viewed")!!.prCount).isEqualTo(1)
    }

    @Test fun completionAndReopenedSummaryUseTheSameUncappedBaseline() = runBlocking {
        insert("prior", 100, 200, weight = 20.0)
        repeat(501) { insert("unrelated-$it", 300L + it, 900L + it, exerciseId = "unrelated") }
        insert("viewed", 2_000, null, weight = 30.0, status = SessionStatus.IN_PROGRESS)
        val repository = OfflineWorkoutRepository(db.workoutSessionDao(), db.workoutSetDao())
        val finished = repository.completeWorkout("viewed", 20)
        assertThat(finished.prCount).isEqualTo(1)
        assertThat(repository.getWorkoutSummary("viewed")).isEqualTo(finished)
    }

    @Test fun detailIsReadOnlyWithoutAProfileAndPreservesUnsupportedSessions() = runBlocking {
        assertThat(history().observeDetail("missing").first()).isNull()
        insert("active", 100, null, status = SessionStatus.IN_PROGRESS)
        val before = db.localDataBackupDao().readAll()
        val detail = requireNotNull(history().observeDetail("active").first())
        assertThat(detail.session.status).isEqualTo(SessionStatus.IN_PROGRESS)
        assertThat(detail.session.notes).isEqualTo("Recorded notes")
        assertThat(detail.summary).isNull()
        assertThat(detail.recommendation).isNull()
        assertThat(detail.previousPerformances).isEmpty()
        assertThat(history().observeHistory(0).first().sessions).isEmpty()
        assertThat(db.localDataBackupDao().readAll()).isEqualTo(before)
        assertThat(db.userProfileDao().getProfile(UserProfile.DEFAULT_PROFILE_ID)).isNull()
        assertThat(cacheCount()).isEqualTo(0)
    }

    @Test fun historyPagesExposeEveryCompletedSessionWithStableTiesAndASentinel() = runBlocking<Unit> {
        repeat(41) { insert("session-${it.toString().padStart(2, '0')}", 100, 200) }
        insert("missing-end", 300, null)
        insert("newest-start", 150, 200)
        insert("active", 500, null, status = SessionStatus.IN_PROGRESS)
        val pages = (0..2).map { history().observeHistory(it).first() }
        assertThat(pages.map { it.sessions.size }).containsExactly(20, 20, 3).inOrder()
        assertThat(pages.map { it.hasOlder }).containsExactly(true, true, false).inOrder()
        assertThat(pages.flatMap { it.sessions }.map { it.id }).containsExactlyElementsIn(
            listOf("missing-end", "newest-start") + (0..40).map { "session-${it.toString().padStart(2, '0')}" }
        ).inOrder()
        assertThat(history().observeHistory(Int.MAX_VALUE).first().sessions).isEmpty()
        assertThrows(IllegalArgumentException::class.java) { history().observeHistory(-1) }
    }

    @Test fun previousPerformanceUsesStrictChronologyAndStableSessionAndInstanceOrdering() = runBlocking {
        insert("a", 100, 200, weight = 20.0, unit = WeightUnit.KG)
        insert("b", 100, 200, weight = 22.0)
        insert("earlier-start", 99, 200, weight = 23.0)
        val a = db.workoutSessionDao().getSessionWithDetails("a")!!.exercisesWithSets.single()
        db.workoutSessionDao().insertWorkoutExercises(listOf(a.exercise.copy(id = "a-first", orderIndex = 0)))
        db.workoutSessionDao().insertWorkoutSets(listOf(a.sets.single().copy(id = "a-first-set", workoutExerciseId = "a-first")))
        insert("viewed", 300, 400, weight = 50.0)
        insert("boundary", 250, 300, weight = 99.0)
        insert("overlap", 250, 350, weight = 99.0)
        insert("invalid-start", 0, 290, weight = 99.0)
        insert("reversed", 299, 290, weight = 99.0)
        repeat(501) { insert("later-$it", 500L + it, 2_000L + it) }
        val detail = requireNotNull(history().observeDetail("viewed").first())
        val previous = detail.previousPerformances.getValue("viewed-exercise")
        assertThat(previous.session.id).isEqualTo("a")
        assertThat(previous.session.weightUnit).isEqualTo(WeightUnit.KG)
        // Same orderIndex; lexicographic instance ID breaks the tie.
        assertThat(previous.exercise.id).isEqualTo("a-exercise")
        assertThat(previous.exercise.sets.single().completedWeight).isEqualTo(20.0)
        assertThat(detail.summary).isEqualTo(
            OfflineWorkoutRepository(db.workoutSessionDao(), db.workoutSetDao()).getWorkoutSummary("viewed")
        )
        assertThat(detail.summary!!.prCount).isEqualTo(1)
    }

    @Test fun everyPersistedTypeAndDistanceShapeHasOnlyCompatibleCompletedWorkAsAPreviousPerformance() = runBlocking {
        val shapes = listOf(
            ExerciseType.WEIGHT_REPS to "reps", ExerciseType.BODYWEIGHT_REPS to "reps",
            ExerciseType.ASSISTED_BODYWEIGHT to "reps", ExerciseType.DURATION to "time",
            ExerciseType.DISTANCE_DURATION to "distance", ExerciseType.DISTANCE_DURATION to "time",
            ExerciseType.DISTANCE_DURATION to "both"
        )
        shapes.forEachIndexed { index, (type, shape) ->
            val key = "exercise-$index"
            insertTyped("prior-$index", 100, 150, key, type, shape)
            insertTyped("warmup-$index", 200, 220, key, type, shape, "warmup")
            insertTyped("stopped-$index", 230, 240, key, type, shape, "stopped")
            insertTyped("unresolved-$index", 250, 260, key, type, shape, "unresolved")
            insertTyped("invalid-$index", 270, 280, key, type, shape, "invalid")
            insertTyped("viewed-$index", 400, 450, key, type, shape)
            val detail = requireNotNull(history().observeDetail("viewed-$index").first())
            assertThat(detail.previousPerformances.getValue("viewed-$index-exercise").session.id)
                .isEqualTo("prior-$index")
        }
        insertTyped("distance-only", 300, 350, "exercise-6", ExerciseType.DISTANCE_DURATION, "distance")
        insertTyped("time-only", 300, 351, "exercise-6", ExerciseType.DISTANCE_DURATION, "time")
        insertTyped("different-type", 300, 352, "exercise-6", ExerciseType.DURATION, "time")
        assertThat(history().observeDetail("viewed-6").first()!!.previousPerformances.values.single().session.id)
            .isEqualTo("prior-6")
    }

    @Test fun previousPerformanceFiltersIndividualSetsWithoutChangingTheOriginalSessionSnapshot() = runBlocking {
        insert("prior", 100, 200)
        insert("viewed", 300, 400, weight = 30.0)
        val valid = db.workoutSessionDao().getSessionWithDetails("prior")!!.exercisesWithSets.single().sets.single()
        db.workoutSessionDao().insertWorkoutSets(listOf(
            valid.copy(id = "warmup", setNumber = 2, type = SetType.WARMUP),
            valid.copy(id = "stopped", setNumber = 3, stopReason = SetStopReason.USER_SKIPPED),
            valid.copy(id = "unresolved", setNumber = 4, isCompleted = false),
            valid.copy(id = "wrong-dimension", setNumber = 5, completedDurationSeconds = 30),
            valid.copy(id = "wrong-type", setNumber = 6, exerciseType = ExerciseType.DURATION),
            valid.copy(id = "invalid-load", setNumber = 7, completedWeight = -1.0)
        ))
        val previous = history().observeDetail("viewed").first()!!.previousPerformances.getValue("viewed-exercise")
        assertThat(previous.exercise.sets.map { it.id }).containsExactly("prior-set")
        assertThat(db.workoutSessionDao().getSessionWithDetails("prior")!!.exercisesWithSets.single().sets).hasSize(7)
    }

    @Test fun previousDistancePerformanceFiltersExtraAndMissingDimensionsWithinTheChosenExercise() = runBlocking {
        for (shape in listOf("distance", "time", "both")) {
            insertTyped("prior-$shape", 100, 200, shape, ExerciseType.DISTANCE_DURATION, shape)
            insertTyped("viewed-$shape", 300, 400, shape, ExerciseType.DISTANCE_DURATION, shape)
            cite("viewed-$shape", shape, "prior-$shape")
            val valid = db.workoutSessionDao().getSessionWithDetails("prior-$shape")!!
                .exercisesWithSets.single().sets.single()
            db.workoutSessionDao().insertWorkoutSets(listOf(
                valid.copy(id = "$shape-other", setNumber = 2,
                    completedDurationSeconds = if (valid.completedDurationSeconds == null) 30 else null),
                valid.copy(id = "$shape-negative-distance", setNumber = 3, completedDistanceMeters = -1.0)
            ))
            val detail = history().observeDetail("viewed-$shape").first()!!
            val previous = detail.previousPerformances.getValue("viewed-$shape-exercise")
            assertThat(previous.exercise.sets.map { it.id }).containsExactly("prior-$shape-set")
            assertThat(detail.progressionSources.getValue(HistoricalSourceKey("prior-$shape", "viewed-$shape-exercise")))
                .isEqualTo(previous)
            assertThat(db.workoutSessionDao().getSessionWithDetails("prior-$shape")!!.exercisesWithSets.single().sets)
                .hasSize(3)
        }
    }

    @Test fun previousPerformanceRejectsOutOfSessionSetTimesButKeepsLegacyUnknownTimesAndInclusiveBounds() = runBlocking {
        insert("prior", 100, 200)
        insert("latest-but-invalid", 300, 350)
        insert("viewed", 500, 600)
        val prior = db.workoutSessionDao().getSessionWithDetails("prior")!!.exercisesWithSets.single().sets.single()
        val recent = db.workoutSessionDao().getSessionWithDetails("latest-but-invalid")!!.exercisesWithSets.single().sets.single()
        db.workoutSessionDao().insertWorkoutSets(listOf(
            prior.copy(id = "at-start", setNumber = 2, completedAtTimestamp = 100),
            prior.copy(id = "at-end", setNumber = 3, completedAtTimestamp = 200),
            prior.copy(id = "too-early", setNumber = 4, completedAtTimestamp = 99),
            prior.copy(id = "too-late", setNumber = 5, completedAtTimestamp = 201),
            recent.copy(completedAtTimestamp = 900)
        ))
        val previous = history().observeDetail("viewed").first()!!.previousPerformances.getValue("viewed-exercise")
        assertThat(previous.session.id).isEqualTo("prior")
        assertThat(previous.exercise.sets.map { it.id }).containsExactly("prior-set", "at-start", "at-end").inOrder()
        assertThat(previous.exercise.sets.first().completedAtTimestamp).isNull()
    }

    @Test fun recommendationAndSourcesAreRecordedFactsWithMissingOrIncompatibleEvidenceOmitted() = runBlocking {
        insert("prior", 100, 200)
        insert("future", 400, 500)
        insert("viewed", 300, 350)
        insertTyped("wrong-type", 100, 200, "lift", ExerciseType.DURATION, "time")
        suspend fun sources(first: String, second: String): WorkoutHistoryDetail {
            val reasons = ProgressionReasonCode.encode(listOf(ProgressionProvenance(
                "lift", ProgressionReason.HOLD_INSUFFICIENT_HISTORY, null, listOf(first, second), "a".repeat(64)
            )))
            db.openHelper.writableDatabase.execSQL("DELETE FROM workout_recommendation_records")
            db.workoutSessionDao().insertRecommendationRecord(record("viewed", reasons).toEntity())
            return requireNotNull(history().observeDetail("viewed").first())
        }
        val detail = sources("prior", "missing")
        assertThat(detail.progressionSources.keys).containsExactly(HistoricalSourceKey("prior", "viewed-exercise"))
        assertThat(detail.recommendation!!.contextIdentity).isEqualTo("recorded-context")
        assertThat(sources("future", "wrong-type").progressionSources).isEmpty()
    }

    @Test fun knownMalformedProvenanceFailsButUnknownVersionsRemainReadable() = runBlocking {
        insert("viewed", 300, 400)
        val unknown = listOf("ONE_VARIABLE_PROGRESSION_V99.0", "NEW_POLICY_V3.UNKNOWN")
        val entity = record("viewed", unknown).toEntity()
        db.workoutSessionDao().insertRecommendationRecord(entity)
        assertThat(history().observeDetail("viewed").first()!!.recommendation!!.reasonCodes)
            .containsExactlyElementsIn(unknown).inOrder()
        for (corrupt in listOf(
            entity.copy(reasonCodes = "ONE_VARIABLE_PROGRESSION_V1.0"),
            entity.copy(doseAccounting = "not-accounting")
        )) {
            db.openHelper.writableDatabase.execSQL("DELETE FROM workout_recommendation_records")
            db.workoutSessionDao().insertRecommendationRecord(corrupt)
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { history().observeDetail("viewed").first() }
            }
        }
    }

    @Test fun oversizedHistoryFailsRatherThanReturningATruncatedDetail() = runBlocking<Unit> {
        insert("viewed", 300, 400)
        val exercise = db.workoutSessionDao().getSessionWithDetails("viewed")!!.exercisesWithSets.single()
        db.workoutSessionDao().insertWorkoutSets((2..101).map { exercise.sets.single().copy(id = "set-$it", setNumber = it) })
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { history().observeDetail("viewed").first() }
        }
    }

    @Test fun historicalEnumsAndHalfPresentPrescriptionMetadataAreExplicitReadErrors() = runBlocking {
        insertTyped("viewed", 300, 400, "hold", ExerciseType.DURATION, "time")
        db.openHelper.writableDatabase.execSQL(
            "UPDATE workout_exercises SET targetRepMax = 10 WHERE sessionId = 'viewed'"
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { history().observeDetail("viewed").first() }
        }
        db.openHelper.writableDatabase.execSQL(
            "UPDATE workout_exercises SET targetRepMax = NULL WHERE sessionId = 'viewed'"
        )
        for ((table, column) in listOf(
            "workout_sessions" to "weightUnit", "workout_sessions" to "origin",
            "workout_exercises" to "exerciseType", "workout_sets" to "type"
        )) {
            val old = db.openHelper.readableDatabase.query("SELECT $column FROM $table").use {
                it.moveToFirst(); it.getString(0)
            }
            db.openHelper.writableDatabase.execSQL("UPDATE $table SET $column = 'UNKNOWN'")
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { history().observeDetail("viewed").first() }
            }
            db.openHelper.writableDatabase.execSQL("UPDATE $table SET $column = ?", arrayOf(old))
        }
    }

    @Test fun recordAggregationCannotRelabelAnUnknownPriorUnitAsPounds() = runBlocking<Unit> {
        insert("prior", 100, 200, weight = 20.0)
        insert("viewed", 300, 400, weight = 30.0)
        db.openHelper.writableDatabase.execSQL(
            "UPDATE workout_sessions SET weightUnit = 'UNKNOWN' WHERE id = 'prior'"
        )
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                OfflineWorkoutRepository(db.workoutSessionDao(), db.workoutSetDao()).getWorkoutSummary("viewed")
            }
        }
    }

    @Test fun recordAggregationPreservesWarmupsRepRecordsAndExactPersistedTypes() = runBlocking {
        insert("prior", 100, 200, weight = 20.0)
        insert("viewed", 300, 400, weight = 30.0)
        db.openHelper.writableDatabase.execSQL("UPDATE workout_sets SET type = 'WARMUP'")
        assertThat(history().observeDetail("viewed").first()!!.summary!!.prCount).isEqualTo(1)
        assertThat(history().observeDetail("viewed").first()!!.previousPerformances).isEmpty()
        insertTyped("body-prior", 100, 200, "body", ExerciseType.BODYWEIGHT_REPS, "reps")
        insertTyped("body-viewed", 300, 400, "body", ExerciseType.BODYWEIGHT_REPS, "reps")
        db.openHelper.writableDatabase.execSQL(
            "UPDATE workout_sets SET completedReps = 12 WHERE id = 'body-viewed-set'"
        )
        assertThat(history().observeDetail("body-viewed").first()!!.summary!!.prCount).isEqualTo(1)
        insertTyped("wrong-type", 250, 290, "body", ExerciseType.ASSISTED_BODYWEIGHT, "reps")
        db.openHelper.writableDatabase.execSQL(
            "UPDATE workout_sets SET completedReps = 99 WHERE id = 'wrong-type-set'"
        )
        assertThat(history().observeDetail("body-viewed").first()!!.summary!!.prCount).isEqualTo(1)
    }

    @Test fun liveDetailAndPagesInvalidateCoherentlyAfterDeleteAndRestoreWithoutWritingACache() = runBlocking {
        OfflineUserProfileRepository(db.userProfileDao(), gate).saveProfile(UserProfile(onboardingCompleted = true))
        insert("prior", 100, 200)
        insert("viewed", 300, 400, weight = 30.0)
        db.workoutSessionDao().insertRecommendationRecord(record("viewed").toEntity())
        val output = ByteArrayOutputStream()
        val backup = OfflineLocalDataBackupRepository(db.localDataBackupDao(), "test", 1L, { null }, gate)
        backup.exportTo { output }
        val values = Channel<WorkoutHistoryDetail?>(Channel.UNLIMITED)
        val pages = Channel<List<String>>(Channel.UNLIMITED)
        val detailCollector = launch { history().observeDetail("viewed").collect { values.send(it) } }
        val pageCollector = launch { history().observeHistory(0).collect { pages.send(it.sessions.map { s -> s.id }) } }
        suspend fun awaitDetail(exists: Boolean): WorkoutHistoryDetail? = withTimeout(10_000) {
            var value = values.receive()
            while ((value != null) != exists) value = values.receive()
            value
        }
        suspend fun awaitPage(size: Int) = withTimeout(10_000) {
            var value = pages.receive()
            while (value.size != size) value = pages.receive()
            value
        }
        try {
            val initial = awaitDetail(true)
            awaitPage(2)
            backup.deleteAllLocalData()
            assertThat(awaitDetail(false)).isNull()
            awaitPage(0)
            assertThat(db.userProfileDao().getProfile(UserProfile.DEFAULT_PROFILE_ID)).isNull()
            backup.restoreFrom(ByteArrayInputStream(output.toByteArray()))
            assertThat(awaitDetail(true)).isEqualTo(initial)
            awaitPage(2)
            assertThat(cacheCount()).isEqualTo(0)
        } finally {
            detailCollector.cancel()
            pageCollector.cancel()
        }
    }

    @Test fun historyWaitsForTheSharedGateAndReadsOnlyThePostDeletionState() = runBlocking {
        insert("viewed", 100, 200)
        gate.lock()
        val read = async { history().observeDetail("viewed").first() }
        try {
            yield()
            assertThat(read.isCompleted).isFalse()
            db.localDataBackupDao().deleteAll()
        } finally {
            gate.unlock()
        }
        assertThat(withTimeout(10_000) { read.await() }).isNull()
        assertThat(cacheCount()).isEqualTo(0)
    }

    private fun history() = OfflineWorkoutHistoryRepository(db, gate)
    private fun cacheCount(): Int = db.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM weekly_dose_ledger_state").use { it.moveToFirst(); it.getInt(0) }

    private fun record(id: String, reasons: List<String> = emptyList()) = RecommendationRecord(
        sessionId = id, validatorVersion = "PROGRAM_VALIDATOR_V1", durationEstimatorVersion = "DURATION_V1",
        outcome = "ACCEPTED", reviewedPathEnabled = true, catalogVersion = null, reviewPolicyVersion = 1,
        trainingPolicyVersion = null, ledgerPolicyVersion = null, programStatePolicyVersion = null,
        adaptationState = null, weekStartEpochDay = null, timeZoneId = null, profileRevision = 0,
        contextIdentity = "recorded-context", reasonCodes = reasons, doseAccounting = emptyList(), recordedAtEpochMillis = 300
    )

    private suspend fun insertTyped(
        id: String, start: Long, end: Long, exerciseId: String, type: ExerciseType,
        shape: String, outcome: String = "completed"
    ) {
        insert(id, start, end, exerciseId)
        val row = db.workoutSessionDao().getSessionWithDetails(id)!!.exercisesWithSets.single()
        val reps = if (shape == "reps") 10 else null
        val weight = if (type == ExerciseType.WEIGHT_REPS) 20.0 else null
        val assistance = if (type == ExerciseType.ASSISTED_BODYWEIGHT) 5.0 else null
        val duration = if (shape in listOf("time", "both")) 30 else null
        val distance = if (shape in listOf("distance", "both")) 100.0 else null
        db.workoutSessionDao().insertWorkoutExercises(listOf(row.exercise.copy(
            exerciseType = type, targetRepMin = reps, targetRepMax = reps, targetWeight = weight,
            targetAssistanceWeight = assistance, targetDurationSeconds = duration, targetDistanceMeters = distance
        )))
        db.workoutSessionDao().insertWorkoutSets(listOf(row.sets.single().copy(
            exerciseType = type, targetReps = reps, completedReps = reps,
            targetWeight = weight, targetAssistanceWeight = assistance,
            completedAssistanceWeight = assistance, targetDurationSeconds = duration, completedDurationSeconds = duration,
            targetDistanceMeters = distance, completedDistanceMeters = distance,
            isCompleted = outcome != "unresolved",
            stopReason = if (outcome == "stopped") SetStopReason.USER_SKIPPED else null,
            type = if (outcome == "warmup") SetType.WARMUP else SetType.NORMAL,
            // Every type rejects an irrelevant or negative measurement.
            completedWeight = if (outcome == "invalid") -1.0 else weight
        )))
    }

    private suspend fun insert(
        id: String,
        start: Long,
        end: Long?,
        exerciseId: String = "lift",
        weight: Double = 20.0,
        unit: WeightUnit = WeightUnit.LBS,
        status: SessionStatus = SessionStatus.COMPLETED
    ) {
        db.workoutSessionDao().insertWorkout(
            WorkoutSessionEntity(
                id, "Workout $id", start, end, 20, 20, unit, status,
                focusMusclesJson = "", notes = "Recorded notes"
            ),
            listOf(WorkoutExerciseEntity(
                "$id-exercise", id, exerciseId, 0, ExerciseType.WEIGHT_REPS,
                1, 8, 10, weight, notes = ""
            )),
            listOf(WorkoutSetEntity(
                id = "$id-set", workoutExerciseId = "$id-exercise", setNumber = 1,
                targetReps = 10, completedReps = 10, targetWeight = weight, completedWeight = weight,
                isCompleted = true, rpe = null, rir = null, type = SetType.NORMAL
            ))
        )
    }
}
