package wallcrawl.elopenmike.com.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.database.entity.WorkoutExerciseEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSessionEntity
import wallcrawl.elopenmike.com.core.database.entity.WorkoutSetEntity
import wallcrawl.elopenmike.com.core.model.ExerciseRole
import wallcrawl.elopenmike.com.core.model.ExerciseRole.MAIN
import wallcrawl.elopenmike.com.core.model.ExerciseRole.MOBILITY
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetType

/** A MAIN row is compared with MAIN history only, and a MOBILITY row with MOBILITY history only. */
@RunWith(AndroidJUnit4::class)
class MobilityRoleHistoryDaoTest {
    private lateinit var db: WallCrawlDatabase
    private val dao get() = db.workoutSessionDao()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WallCrawlDatabase::class.java)
            .build()
    }

    @After fun tearDown() { db.close() }

    @Test fun aMobilityPriorGivesNoBaselineToAMainViewedExerciseAndTheReverse() = runBlocking {
        insert("prior-mobility", 100, MOBILITY)
        insert("viewed-main", 300, MAIN)
        assertThat(dao.selectPersonalRecordBaselines("viewed-main")).isEmpty()

        insert("prior-main", 400, MAIN)
        insert("viewed-mobility", 600, MOBILITY)
        assertThat(dao.selectPersonalRecordBaselines("viewed-mobility")).isEmpty()
        // The MAIN viewed session now has no MAIN prior either, only the mobility one.
        assertThat(dao.selectPersonalRecordBaselines("viewed-main")).isEmpty()
        insert("viewed-main-later", 800, MAIN)
        assertThat(dao.selectPersonalRecordBaselines("viewed-main-later").single().maxWeight).isEqualTo(20.0)
    }

    @Test fun aMainRowSkipsANewerMobilityPriorAndAMobilityRowSkipsANewerMainPrior() = runBlocking {
        insert("old-main", 100, MAIN)
        insert("new-mobility", 300, MOBILITY)
        insert("viewed-main", 500, MAIN)
        assertThat(dao.selectPreviousPerformanceIds("viewed-main").single().previousSessionId).isEqualTo("old-main")

        insert("old-mobility2", 600, MOBILITY)
        insert("new-main", 800, MAIN)
        insert("viewed-mobility", 1_000, MOBILITY)
        assertThat(dao.selectPreviousPerformanceIds("viewed-mobility").single().previousSessionId)
            .isEqualTo("old-mobility2")
    }

    @Test fun aMainRowWithOnlyMobilityHistoryHasNoPreviousPerformance() = runBlocking {
        insert("prior-mobility", 100, MOBILITY)
        insert("viewed-main", 300, MAIN)
        assertThat(dao.selectPreviousPerformanceIds("viewed-main")).isEmpty()
    }

    @Test fun citedPerformanceFollowsTheSameRoleRule() = runBlocking {
        insert("source-mobility", 100, MOBILITY)
        insert("source-main", 300, MAIN)
        insert("viewed-main", 500, MAIN)
        fun cited(source: String) = runBlocking {
            dao.selectCitedPerformanceIds("viewed-main", listOf(source), listOf("$source\u001Fclamshell"))
        }
        assertThat(cited("source-mobility")).isEmpty()
        assertThat(cited("source-main").single().previousSessionId).isEqualTo("source-main")

        insert("viewed-mobility", 700, MOBILITY)
        fun citedMobility(source: String) = runBlocking {
            dao.selectCitedPerformanceIds("viewed-mobility", listOf(source), listOf("$source\u001Fclamshell"))
        }
        assertThat(citedMobility("source-main")).isEmpty()
        assertThat(citedMobility("source-mobility").single().previousSessionId).isEqualTo("source-mobility")
    }

    @Test fun aMobilityOnlySessionStaysInTheHistoryPage() = runBlocking {
        insert("mobility-only", 100, MOBILITY)
        insert("main", 300, MAIN)
        assertThat(dao.selectHistoryPage(10, 0).map { it.id }).containsExactly("main", "mobility-only").inOrder()
    }

    /** One completed session of a single `clamshell` row, 20 x 10, spanning [start, start + 50]. */
    private suspend fun insert(id: String, start: Long, role: ExerciseRole) {
        dao.insertWorkout(
            WorkoutSessionEntity(
                id = id, name = "Session $id", startedAtTimestamp = start, completedAtTimestamp = start + 50,
                targetDurationMinutes = 30, actualDurationMinutes = 30, status = SessionStatus.COMPLETED,
                focusMusclesJson = "", notes = ""
            ),
            listOf(WorkoutExerciseEntity(
                id = "$id-exercise", sessionId = id, exerciseId = "clamshell", orderIndex = 0,
                exerciseType = ExerciseType.WEIGHT_REPS, targetSets = 1, targetRepMin = 8, targetRepMax = 10,
                targetWeight = 20.0, notes = "", role = role
            )),
            listOf(WorkoutSetEntity(
                id = "$id-set", workoutExerciseId = "$id-exercise", setNumber = 1,
                exerciseType = ExerciseType.WEIGHT_REPS, targetReps = 10, completedReps = 10,
                targetWeight = 20.0, completedWeight = 20.0, isCompleted = true, rpe = null, rir = null,
                completedAtTimestamp = start + 10, type = SetType.NORMAL
            ))
        )
    }
}
