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
import wallcrawl.elopenmike.com.core.model.ExerciseRole

@RunWith(AndroidJUnit4::class)
class Migration16To17Test {
    @Test fun migrationDefaultsConditionsEmptyAndRolesMain() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val name = "conditions-migration-16-17.db"
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
        helper.writableDatabase
        helper.close()
        val database = Room.databaseBuilder(context, WallCrawlDatabase::class.java, name)
            .addMigrations(WallCrawlDatabase.MIGRATION_15_16, WallCrawlDatabase.MIGRATION_16_17).build()
        try {
            assertEquals(17, database.openHelper.writableDatabase.version)
            val profile = database.userProfileDao().getProfile("default_user")!!
            assertEquals("", profile.reportedConditionsJson)
            val exercise = database.workoutSessionDao().getSessionWithDetails("session-7")!!
                .exercisesWithSets.single().exercise
            assertEquals(ExerciseRole.MAIN, exercise.role)
            assertNull(exercise.targetHoldSeconds)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
