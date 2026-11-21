package wallcrawl.elopenmike.com.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration19To20Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: WallCrawlDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun existingProfile_migratesAsPersonalized() {
        // Fixtures stop at v11, so the whole chain runs and ends with 19 -> 20.
        context.deleteDatabase(DATABASE_NAME)
        context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { db ->
            LegacyDatabaseFixtures.createSchema(db, version = 11)
            LegacyDatabaseFixtures.insertProfile(db, version = 11, onboarded = true)
            db.version = 11
        }

        database = Room.databaseBuilder(context, WallCrawlDatabase::class.java, DATABASE_NAME)
            .addMigrations(*WallCrawlDatabase.ALL_MIGRATIONS)
            .build()
        val sqlite = checkNotNull(database).openHelper.writableDatabase

        assertThat(sqlite.version).isEqualTo(20)
        sqlite.query("SELECT personalizationCompleted FROM user_profiles").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(0)).isEqualTo(1)
        }
    }

    private companion object {
        const val DATABASE_NAME = "migration-19-20.db"
    }
}
