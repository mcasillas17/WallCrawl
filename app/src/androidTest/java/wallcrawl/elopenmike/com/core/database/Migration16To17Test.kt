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
import wallcrawl.elopenmike.com.core.model.PrescriptionPurpose
import wallcrawl.elopenmike.com.core.database.entity.ConditionReassessmentPendingEntity
import wallcrawl.elopenmike.com.core.database.relation.toWorkoutSession
import wallcrawl.elopenmike.com.core.database.relation.toWorkoutTemplate

@RunWith(AndroidJUnit4::class)
class Migration16To17Test {
    @Test fun oldRowsGainOnlyEmptyConditionDefaultsAndOrdinaryPurpose() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val name = "condition-migration-16-17.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use {
            LegacyDatabaseFixtures.createSchema(it, 11)
            LegacyDatabaseFixtures.insertProfile(it, 11)
            LegacyDatabaseFixtures.insertHistoryAndTemplate(it)
            it.version = 11
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(16) {
                    override fun onCreate(db: SupportSQLiteDatabase) = error("Existing database required")
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        WallCrawlDatabase.MIGRATION_11_12.migrate(db)
                        WallCrawlDatabase.MIGRATION_12_13.migrate(db)
                        WallCrawlDatabase.MIGRATION_13_14.migrate(db)
                        WallCrawlDatabase.MIGRATION_14_15.migrate(db)
                        WallCrawlDatabase.MIGRATION_15_16.migrate(db)
                        db.execSQL("""INSERT INTO workout_recommendation_records
                            (sessionId,validatorVersion,durationEstimatorVersion,outcome,reviewedPathEnabled,reviewPolicyVersion,
                            profileRevision,contextIdentity,reasonCodes,doseAccounting,recordedAtTimestamp)
                            VALUES ('session-7','WHOLE_PROGRAM_V2','DURATION_V1','VALID',0,0,17,'legacy','','wallcrawl-recommendation-dose-v1',1000)""")
                    }
                }).build())
        helper.writableDatabase
        helper.close()
        val database = Room.databaseBuilder(context, WallCrawlDatabase::class.java, name)
            .addMigrations(*WallCrawlDatabase.ALL_MIGRATIONS).build()
        try {
            assertEquals(WALLCRAWL_SCHEMA_VERSION, database.openHelper.writableDatabase.version)
            val profile = database.userProfileDao().getProfile("default_user")!!
            assertEquals("[]", profile.activeConditionsJson)
            val session = database.workoutSessionDao().getSessionWithDetails("session-7")!!
            assertNull(session.session.conditionContextJson)
            assertEquals(PrescriptionPurpose.ORDINARY, session.toWorkoutSession().exercises.single().prescription.purpose)
            val receipt = database.workoutSessionDao().getRecommendationRecord("session-7")!!
            assertNull(receipt.conditionContextDigest)
            assertEquals("legacy", receipt.contextIdentity)
            val template = database.workoutTemplateDao().getTemplateWithExercises("template-7")!!
            assertEquals(PrescriptionPurpose.ORDINARY, template.toWorkoutTemplate().exercises.single().prescription.purpose)
            assertTrue(database.conditionReassessmentDao().forProfile(profile.id).isEmpty())
            val marker = ConditionReassessmentPendingEntity(profile.id, "condition", "exercise", "event",
                "a".repeat(64), 1, 100, "a03c8a04-9f34-45e2-9654-d5cdb16aeb77")
            database.conditionReassessmentDao().upsertIfNewer(marker)
            assertEquals(marker, database.conditionReassessmentDao().get(profile.id, "condition", "exercise"))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
