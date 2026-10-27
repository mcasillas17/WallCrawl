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
class Migration14To15Test {
    @Test fun templateUnitIsAddedAsUnknownAndNothingElseIsRewritten() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val name = "template-unit-migration-14-15.db"
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
                    }
                }).build())
        helper.writableDatabase
        helper.close()
        val database = Room.databaseBuilder(context, WallCrawlDatabase::class.java, name)
            .addMigrations(WallCrawlDatabase.MIGRATION_14_15).build()
        try {
            assertEquals(WALLCRAWL_SCHEMA_VERSION, database.openHelper.writableDatabase.version)
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
}
