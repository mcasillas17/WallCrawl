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
class Migration13To14Test {
    @Test fun additiveMigrationPreservesRowsAndAddsEmptyPreferencesWithoutForeignKeys() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val name = "deload-migration-13-14.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use {
            LegacyDatabaseFixtures.createSchema(it, 11)
            LegacyDatabaseFixtures.insertProfile(it, 11)
            LegacyDatabaseFixtures.insertHistoryAndTemplate(it)
            it.version = 11
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(13) {
                    override fun onCreate(db: SupportSQLiteDatabase) = error("Existing database required")
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        WallCrawlDatabase.MIGRATION_11_12.migrate(db)
                        WallCrawlDatabase.MIGRATION_12_13.migrate(db)
                    }
                }).build())
        helper.writableDatabase
        helper.close()
        val database = Room.databaseBuilder(context, WallCrawlDatabase::class.java, name)
            .addMigrations(WallCrawlDatabase.MIGRATION_13_14).build()
        try {
            assertEquals(14, database.openHelper.writableDatabase.version)
            assertEquals(17L, database.userProfileDao().getProfile("default_user")!!.revision)
            assertNull(database.deloadPreferencesDao().get("default_user"))
            database.openHelper.writableDatabase.query("PRAGMA foreign_key_list(deload_preferences)").use {
                assertFalse(it.moveToFirst())
            }
            database.openHelper.writableDatabase.query("SELECT COUNT(*) FROM workout_sets").use {
                assertTrue(it.moveToFirst())
                assertTrue(it.getInt(0) > 0)
            }
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
