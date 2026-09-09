package wallcrawl.elopenmike.com.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.model.IllustrationPreference
import wallcrawl.elopenmike.com.core.model.ProfileGender

@RunWith(AndroidJUnit4::class)
class Migration12To13Test {
    @Test
    fun existingProfileAndHistorySurviveWithUnspecifiedGenderAndAutomaticArtwork() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val name = "illustration-migration-12-13.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            LegacyDatabaseFixtures.createSchema(db, 11)
            LegacyDatabaseFixtures.insertProfile(db, 11)
            LegacyDatabaseFixtures.insertHistoryAndTemplate(db)
            db.version = 11
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(12) {
                    override fun onCreate(db: SupportSQLiteDatabase) = error("Existing database required")
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        assertThat(oldVersion).isEqualTo(11)
                        WallCrawlDatabase.MIGRATION_11_12.migrate(db)
                    }
                }).build()
        )
        assertThat(helper.writableDatabase.version).isEqualTo(12)
        helper.close()
        val database = Room.databaseBuilder(context, WallCrawlDatabase::class.java, name)
            .addMigrations(*WallCrawlDatabase.ALL_MIGRATIONS).build()
        try {
            val sqlite = database.openHelper.writableDatabase
            assertThat(sqlite.version).isEqualTo(13)
            val profile = database.userProfileDao().getProfile("default_user")!!
            assertThat(profile.name).isEqualTo("Migration Crawler")
            assertThat(profile.revision).isEqualTo(17L)
            assertThat(profile.gender).isEqualTo(ProfileGender.UNSPECIFIED)
            assertThat(profile.illustrationPreference).isEqualTo(IllustrationPreference.AUTOMATIC)
            sqlite.query("SELECT COUNT(*) FROM workout_sets").use {
                assertThat(it.moveToFirst()).isTrue()
                assertThat(it.getInt(0)).isGreaterThan(0)
            }
            database.userProfileDao().insertOrUpdate(profile.copy(
                gender = ProfileGender.WOMAN, illustrationPreference = IllustrationPreference.MALE
            ))
            assertThat(database.userProfileDao().getProfile("default_user")!!.gender).isEqualTo(ProfileGender.WOMAN)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
