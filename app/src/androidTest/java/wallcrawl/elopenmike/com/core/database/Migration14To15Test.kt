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

@RunWith(AndroidJUnit4::class)
class Migration14To15Test {
    @Test fun migrationPreservesLegacyRecordsAndStartsWithNoProgramFacts() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val name = "program-migration-14-15.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use {
            LegacyDatabaseFixtures.createSchema(it, 11)
            LegacyDatabaseFixtures.insertProfile(it, 11)
            LegacyDatabaseFixtures.insertHistoryAndTemplate(it)
            it.version = 11
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(14) {
                    override fun onCreate(db: SupportSQLiteDatabase) = error("Existing database required")
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        WallCrawlDatabase.MIGRATION_11_12.migrate(db)
                        WallCrawlDatabase.MIGRATION_12_13.migrate(db)
                        WallCrawlDatabase.MIGRATION_13_14.migrate(db)
                        db.execSQL("""INSERT INTO workout_recommendation_records
                            (sessionId,validatorVersion,durationEstimatorVersion,outcome,reviewedPathEnabled,reviewPolicyVersion,
                            profileRevision,contextIdentity,reasonCodes,doseAccounting,recordedAtTimestamp)
                            SELECT id,'WHOLE_PROGRAM_V2','DURATION_V1','VALID',0,0,17,'legacy','','wallcrawl-recommendation-dose-v1',1000
                            FROM workout_sessions LIMIT 1""")
                    }
                }).build())
        helper.writableDatabase
        helper.close()
        val database = Room.databaseBuilder(context, WallCrawlDatabase::class.java, name)
            .addMigrations(WallCrawlDatabase.MIGRATION_14_15, WallCrawlDatabase.MIGRATION_15_16).build()
        try {
            assertEquals(WALLCRAWL_SCHEMA_VERSION, database.openHelper.writableDatabase.version)
            assertEquals(17L, database.userProfileDao().getProfile("default_user")!!.revision)
            assertNull(database.programDao().getProgramEnrollment("default_user"))
            database.openHelper.writableDatabase.query("SELECT programAssociationDigest FROM workout_recommendation_records").use {
                assertTrue(it.moveToFirst()); assertTrue(it.isNull(0)); assertFalse(it.moveToNext())
            }
            database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM program_session_associations").use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
            }
            database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
