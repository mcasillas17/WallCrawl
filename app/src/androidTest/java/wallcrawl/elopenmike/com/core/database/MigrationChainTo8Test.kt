package wallcrawl.elopenmike.com.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationChainTo8Test {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun everyHistoricallySupportedSchemaMigratesToVersion8() {
        (1..7).forEach { startingVersion ->
            val databaseName = "migration-$startingVersion-8.db"
            context.deleteDatabase(databaseName)
            context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { db ->
                LegacyDatabaseFixtures.createSchema(db, startingVersion)
                LegacyDatabaseFixtures.insertProfile(db, startingVersion)
                db.version = startingVersion
            }

            val database = Room.databaseBuilder(
                context,
                WallCrawlDatabase::class.java,
                databaseName
            )
                .addMigrations(*WallCrawlDatabase.ALL_MIGRATIONS)
                .build()
            try {
                val sqlite = database.openHelper.writableDatabase
                assertThat(sqlite.version).isEqualTo(8)
                sqlite.query(
                    "SELECT name,movementCapabilitiesJson FROM user_profiles"
                ).use { cursor ->
                    assertThat(cursor.moveToFirst()).isTrue()
                    assertThat(cursor.getString(0)).isEqualTo("Migration Crawler")
                    assertThat(cursor.getString(1)).isEqualTo("{}")
                }
                sqlite.query("PRAGMA foreign_key_check").use { cursor ->
                    assertThat(cursor.count).isEqualTo(0)
                }
            } finally {
                database.close()
                context.deleteDatabase(databaseName)
            }
        }
    }
}
