package wallcrawl.elopenmike.com.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationChainTo10Test {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun everyHistoricallySupportedSchemaMigratesToVersion10() {
        (1..9).forEach { startingVersion ->
            val databaseName = "migration-$startingVersion-10.db"
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
                assertThat(sqlite.version).isEqualTo(10)
                sqlite.query(
                    "SELECT name,movementCapabilitiesJson FROM user_profiles"
                ).use { cursor ->
                    assertThat(cursor.moveToFirst()).isTrue()
                    assertThat(cursor.getString(0)).isEqualTo("Migration Crawler")
                    assertThat(cursor.getString(1)).isEqualTo("{}")
                }
                // Every chain ends with the schema-9 set-outcome columns present and
                // defaulted to NULL, whichever version the upgrade started from.
                sqlite.query("PRAGMA table_info(workout_sets)").use { cursor ->
                    val columns = mutableMapOf<String, String?>()
                    while (cursor.moveToNext()) {
                        columns[cursor.getString(cursor.getColumnIndexOrThrow("name"))] =
                            cursor.getString(cursor.getColumnIndexOrThrow("dflt_value"))
                    }
                    listOf(
                        "feltManageable",
                        "completedAtTimestamp",
                        "stoppedAtTimestamp",
                        "stopReason"
                    ).forEach { column ->
                        assertThat(columns).containsKey(column)
                        assertThat(columns[column]).isNull()
                    }
                }
                // Every chain also ends with the derived weekly ledger cache present and
                // empty: a schema upgrade never fabricates training exposure.
                sqlite.query("SELECT COUNT(*) FROM weekly_dose_ledger_state").use { cursor ->
                    assertThat(cursor.moveToFirst()).isTrue()
                    assertThat(cursor.getInt(0)).isEqualTo(0)
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
