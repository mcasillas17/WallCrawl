package wallcrawl.elopenmike.com.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.database.relation.toWorkoutSession
import wallcrawl.elopenmike.com.core.database.relation.toWorkoutTemplate
import wallcrawl.elopenmike.com.core.database.repository.ConditionContextCodec
import wallcrawl.elopenmike.com.core.database.repository.toUserProfile
import wallcrawl.elopenmike.com.core.database.repository.toUserProfileEntity
import wallcrawl.elopenmike.com.core.model.PrescriptionPurpose
import wallcrawl.elopenmike.com.core.model.SelectedCondition
import wallcrawl.elopenmike.com.core.model.SessionConditionContext

@RunWith(AndroidJUnit4::class)
class Migration17To18Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun released16ExportRetainsItsHistoryAndAcquiresOnlyEmptyConditionDefaults() = runBlocking {
        withMigratedDatabase("released-16", { db ->
            createFromRoomExport(db, 16)
            seedProfileAndHistory(db)
        }, version = 16) { database ->
            val profile = database.userProfileDao().getProfile("default_user")!!.toUserProfile()
            assertTrue(profile.activeConditions.isEmpty())
            assertTrue(profile.reportedConditions.isEmpty())
            val session = database.workoutSessionDao().getSessionWithDetails("session-7")!!.toWorkoutSession()
            val exercise = session.exercises.single()
            assertEquals(PrescriptionPurpose.ORDINARY, exercise.prescription.purpose)
            assertFalse(exercise.isLegacyMobility)
            assertNull(exercise.prescription.holdSeconds)
            assertEquals(9, exercise.sets.single().completedReps)
            assertNull(session.conditionContext)
        }
    }

    @Test fun released17PreservesAllSevenMobilityIdsHoldsSetsAndOpaqueReports() = runBlocking {
        val targets = listOf(
            // ID, repetitions, seconds per repetition, duration of a timed set.
            MobilityTarget("figure-four-stretch", duration = 120),
            MobilityTarget("lying-piriformis-stretch", reps = 10, hold = 30),
            MobilityTarget("side-lying-isometric-hip-abduction-hold", reps = 6, hold = 30),
            MobilityTarget("standing-isometric-gluteal-contraction", reps = 10, hold = 6),
            MobilityTarget("wrist-extensor-stretch", duration = 30),
            MobilityTarget("tendon-gliding-sequence", reps = 5, hold = 7),
            MobilityTarget("straight-leg-raise", reps = 10)
        )
        val opaque = "x".repeat(200)
        val originallyWritten = listOf("piriformis_syndrome", "future/opaque condition||", opaque)
        // Preserve exactly the report values the v17 reader returned for this ambiguous pipe boundary.
        val reports = linkedSetOf("piriformis_syndrome", "future/opaque condition", "||$opaque")
        withMigratedDatabase("released-17", { db ->
            createFromRoomExport(db, 17)
            seedProfileAndHistory(db)
            db.execSQL("UPDATE user_profiles SET reportedConditionsJson=?", arrayOf(originallyWritten.joinToString("|||")))
            db.execSQL("""INSERT INTO workout_sessions
                (id,name,startedAtTimestamp,completedAtTimestamp,targetDurationMinutes,actualDurationMinutes,
                weightUnit,status,focusMusclesJson,notes) VALUES ('mobility','Mobility',300,500,5,5,'KG','COMPLETED','','')""")
            targets.forEachIndexed { index, target ->
                val type = if (target.duration == null) "BODYWEIGHT_REPS" else "DURATION"
                db.execSQL("""INSERT INTO workout_exercises
                    (id,sessionId,exerciseId,orderIndex,exerciseType,targetSets,targetRepMin,targetRepMax,
                    targetDurationSeconds,restSeconds,notes,role,targetHoldSeconds)
                    VALUES (?,'mobility',?,?,?,1,?,?,?,90,'','MOBILITY',?)""",
                    arrayOf<Any?>(target.id, target.id, index, type, target.reps, target.reps, target.duration, target.hold))
                db.execSQL("""INSERT INTO workout_sets
                    (id,workoutExerciseId,setNumber,exerciseType,targetReps,completedReps,targetDurationSeconds,
                    completedDurationSeconds,isCompleted,completedAtTimestamp,type)
                    VALUES (?,?,1,?,?,?,?,?,1,450,'NORMAL')""",
                    arrayOf<Any?>("set-$index", target.id, type, target.reps, target.reps, target.duration, target.duration))
            }
        }) { database ->
            val profileDao = database.userProfileDao()
            val profile = profileDao.getProfile("default_user")!!.toUserProfile()
            assertEquals(reports, profile.reportedConditions)
            assertTrue(profile.activeConditions.isEmpty())
            profileDao.insertOrUpdate(profile.copy(name = "Renamed").toUserProfileEntity())
            assertEquals(reports, profileDao.getProfile(profile.id)!!.toUserProfile().reportedConditions)
            val dao = database.workoutSessionDao()
            val session = dao.getSessionWithDetails("mobility")!!.toWorkoutSession()
            assertNull(session.conditionContext)
            assertEquals(targets.map { it.id }, session.exercises.map { it.exerciseId })
            session.exercises.zip(targets).forEach { (exercise, target) ->
                assertEquals(PrescriptionPurpose.SUPPORTIVE, exercise.prescription.purpose)
                assertTrue(exercise.isLegacyMobility)
                assertEquals(target.hold, exercise.prescription.holdSeconds)
                assertEquals(target.duration, exercise.prescription.targetDurationSeconds)
                assertEquals(target.reps, exercise.sets.single().completedReps)
                assertEquals(target.duration, exercise.sets.single().completedDurationSeconds)
                assertEquals(450L, exercise.sets.single().completedAtTimestamp)
                assertTrue(dao.selectAnalyticsComparableSetIds(0, 600, target.id).isEmpty())
            }
            assertEquals(2, dao.selectHistoryPage(10, 0).size)
            assertEquals(listOf(200L), dao.getCompletedTimestamps())
            assertEquals(1, dao.observeCompletedSessionCount().first())
            assertEquals(1, dao.observeCompletedSessionCountInRange(0, 600).first())
            val main = dao.getSessionWithDetails("session-7")!!.toWorkoutSession().exercises.single()
            assertEquals(PrescriptionPurpose.ORDINARY, main.prescription.purpose)
            assertNull(main.prescription.holdSeconds)
            assertEquals(24.0, main.sets.single().completedWeight!!, 0.0)
        }
    }

    @Test fun preview17RetainsFrozenContextSelectionsReceiptTemplatePurposeAndMarker() = runBlocking {
        val selected = listOf(SelectedCondition("preview-only", 9, mapOf("stage" to null)))
        val frozen = SessionConditionContext(1, 2, selected, mapOf("preview-only" to "preview-version"),
            emptyList(), emptyList(), emptyList())
        withMigratedDatabase("preview-17", { db ->
            instrumentation.context.assets.open("room/preview-17.sql").bufferedReader().useLines { lines ->
                lines.filterNot { it.startsWith("--") || it.isBlank() }.forEach { db.execSQL(it) }
            }
            seedProfileAndHistory(db)
            db.execSQL("UPDATE user_profiles SET activeConditionsJson=?", arrayOf(ConditionContextCodec.encodeSelections(selected)))
            db.execSQL("UPDATE workout_sessions SET conditionContextJson=?", arrayOf(ConditionContextCodec.encodeSession(frozen)))
            db.execSQL("UPDATE workout_exercises SET purpose='SUPPORTIVE'")
            db.execSQL("UPDATE workout_template_exercises SET purpose='SUPPORTIVE'")
            db.execSQL("""INSERT INTO workout_recommendation_records
                (sessionId,validatorVersion,durationEstimatorVersion,outcome,reviewedPathEnabled,reviewPolicyVersion,
                profileRevision,contextIdentity,reasonCodes,doseAccounting,recordedAtTimestamp,conditionContextDigest)
                VALUES ('session-7','WHOLE_PROGRAM_V2','DURATION_V1','VALID',0,0,17,'preview','','wallcrawl-recommendation-dose-v1',1000,?)""",
                arrayOf(frozen.canonicalDigest))
            db.execSQL("""INSERT INTO condition_reassessment_pending VALUES
                ('default_user','preview-only','goblet-squat','preview-event',?,1,200,'set-7')""",
                arrayOf(frozen.canonicalDigest))
        }) { database ->
            val profile = database.userProfileDao().getProfile("default_user")!!.toUserProfile()
            assertEquals(selected, profile.activeConditions)
            assertTrue(profile.reportedConditions.isEmpty())
            val dao = database.workoutSessionDao()
            val session = dao.getSessionWithDetails("session-7")!!.toWorkoutSession()
            assertEquals(frozen, session.conditionContext)
            assertEquals(PrescriptionPurpose.SUPPORTIVE, session.exercises.single().prescription.purpose)
            assertFalse(session.exercises.single().isLegacyMobility)
            assertNull(session.exercises.single().prescription.holdSeconds)
            assertEquals("set-7", session.exercises.single().sets.single().id)
            assertEquals(frozen.canonicalDigest, dao.getRecommendationRecord(session.id)!!.conditionContextDigest)
            assertEquals("preview-event", database.conditionReassessmentDao().forProfile(profile.id).single().eventId)
            val template = database.workoutTemplateDao().getTemplateWithExercises("template-7")!!.toWorkoutTemplate()
            assertEquals(PrescriptionPurpose.SUPPORTIVE, template.exercises.single().prescription.purpose)
            assertEquals(listOf(200L), dao.getCompletedTimestamps())
        }
    }

    private data class MobilityTarget(val id: String, val reps: Int? = null, val hold: Int? = null, val duration: Int? = null)

    private fun seedProfileAndHistory(db: SQLiteDatabase) {
        LegacyDatabaseFixtures.insertProfile(db, 11)
        LegacyDatabaseFixtures.insertHistoryAndTemplate(db)
    }

    private suspend fun withMigratedDatabase(
        fixture: String, seed: (SQLiteDatabase) -> Unit, version: Int = 17, check: suspend (WallCrawlDatabase) -> Unit
    ) {
        val name = "migration-$fixture.db"
        context.deleteDatabase(name)
        try {
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use {
                it.setForeignKeyConstraintsEnabled(true)
                seed(it)
                it.version = version
            }
            val database = Room.databaseBuilder(context, WallCrawlDatabase::class.java, name)
                .addMigrations(*WallCrawlDatabase.ALL_MIGRATIONS).build()
            try {
                assertEquals(WALLCRAWL_SCHEMA_VERSION, database.openHelper.writableDatabase.version)
                database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
                check(database)
            } finally {
                database.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private fun createFromRoomExport(db: SQLiteDatabase, version: Int) {
        val path = "${WallCrawlDatabase::class.java.name}/$version.json"
        val schema = instrumentation.context.assets.open(path).bufferedReader().use { JSONObject(it.readText()) }
            .getJSONObject("database")
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
    }
}
