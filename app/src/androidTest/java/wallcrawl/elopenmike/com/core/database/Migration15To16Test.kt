package wallcrawl.elopenmike.com.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.model.WeightUnit

@RunWith(AndroidJUnit4::class)
class Migration15To16Test {
    @Test fun templateUnitIsAddedAsUnknownAndNothingElseIsRewritten() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val name = "template-unit-migration-15-16.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use {
            LegacyDatabaseFixtures.createSchema(it, 11)
            LegacyDatabaseFixtures.insertProfile(it, 11)
            LegacyDatabaseFixtures.insertHistoryAndTemplate(it)
            it.version = 11
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(15) {
                    override fun onCreate(db: SupportSQLiteDatabase) = error("Existing database required")
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        WallCrawlDatabase.MIGRATION_11_12.migrate(db)
                        WallCrawlDatabase.MIGRATION_12_13.migrate(db)
                        WallCrawlDatabase.MIGRATION_13_14.migrate(db)
                        WallCrawlDatabase.MIGRATION_14_15.migrate(db)
                    }
                }).build())
        val oldDatabase = helper.writableDatabase
        oldDatabase.execSQL("""INSERT INTO deload_preferences
            (profileId,revision,offerId,source,policyVersion,status,decidedAtEpochMillis,sessionId,lastHandledReturnKey)
            VALUES ('default_user',3,'offer-15','EXPLICIT_REQUEST','DELOAD_ONE_WORKOUT_V1',
                'CONSUMED',150,'session-7','return-15')""")
        oldDatabase.execSQL("""INSERT INTO program_enrollments
            (profileId,programId,revision,lifecyclePolicyVersion,configurationPolicyVersion,
                primaryGoal,goals,sessionsPerWeek,status,createdAtTimestamp,updatedAtTimestamp)
            VALUES ('default_user','program-15',2,'MULTI_WEEK_PROGRAM_V1','PROGRAM_CONFIGURATION_V1',
                'STRENGTH','STRENGTH',2,'ACTIVE',100,200)""")
        oldDatabase.execSQL("""INSERT INTO program_session_associations
            (sessionId,profileId,programId,enrollmentRevision,lifecyclePolicyVersion,configurationPolicyVersion,
                primaryGoal,goals,sessionsPerWeek,slotOrdinal,blockNumber,weekNumber,sessionInWeek,objective)
            VALUES ('session-7','default_user','program-15',1,'MULTI_WEEK_PROGRAM_V1','PROGRAM_CONFIGURATION_V1',
                'STRENGTH','STRENGTH',2,0,1,1,1,'ESTABLISH_ROUTINE')""")
        oldDatabase.execSQL("""INSERT INTO workout_recommendation_records
            (sessionId,validatorVersion,durationEstimatorVersion,outcome,reviewedPathEnabled,reviewPolicyVersion,
                profileRevision,contextIdentity,reasonCodes,doseAccounting,recordedAtTimestamp,programAssociationDigest)
            VALUES ('session-7','WHOLE_PROGRAM_V2','DURATION_V1','VALID',0,0,17,'legacy-context',
                'legacy-reason','wallcrawl-recommendation-dose-v1',100,'preserved-program-digest')""")
        val retained = retainedRows(oldDatabase)
        retained.values.forEach { assertEquals(1, it.size) }
        helper.close()
        val database = Room.databaseBuilder(context, WallCrawlDatabase::class.java, name)
            .addMigrations(WallCrawlDatabase.MIGRATION_15_16).build()
        try {
            assertEquals(WALLCRAWL_SCHEMA_VERSION, database.openHelper.writableDatabase.version)
            assertEquals(retained, retainedRows(database.openHelper.writableDatabase))
            database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use {
                assertEquals(0, it.count)
            }
            val template = database.workoutTemplateDao().getTemplateWithExercises("template-7")!!
            // A legacy template's unit was never recorded, so the migration must not pick one.
            assertNull(template.template.weightUnit)
            assertEquals(24.0, template.exercises.single().targetWeight!!, 0.0)
            // The session started from it keeps its own, separately recorded unit.
            val session = database.workoutSessionDao().getSessionWithDetails("session-7")!!
            assertEquals(WeightUnit.KG, session.session.weightUnit)
            assertEquals(24.0, session.exercisesWithSets.single().exercise.targetWeight!!, 0.0)

            database.workoutTemplateDao().upsertTemplate(
                template.template.copy(weightUnit = WeightUnit.KG.name)
            )
            assertEquals(
                "KG",
                database.workoutTemplateDao().getTemplateWithExercises("template-7")!!.template.weightUnit
            )
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    private fun retainedRows(db: SupportSQLiteDatabase): Map<String, List<List<String?>>> =
        listOf("deload_preferences", "program_enrollments", "program_session_associations",
            "workout_recommendation_records").associateWith { table ->
            db.query("SELECT * FROM $table ORDER BY 1").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add((0 until cursor.columnCount).map(cursor::getString))
                }
            }
        }
}
