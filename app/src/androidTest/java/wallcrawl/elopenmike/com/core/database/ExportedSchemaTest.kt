package wallcrawl.elopenmike.com.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportedSchemaTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val schemaPath = "${WallCrawlDatabase::class.java.name}/$WALLCRAWL_SCHEMA_VERSION.json"
    private val schema by lazy {
        instrumentation.context.assets.open(schemaPath).bufferedReader().use {
            JSONObject(it.readText()).getJSONObject("database")
        }
    }

    @Test
    fun currentExportPassesRoomValidationAndIsOnlyInTestAssets() {
        assertThat(context.assets.list(WallCrawlDatabase::class.java.name)).isEmpty()
        val name = "export-current.db"
        context.deleteDatabase(name)
        try {
            createDatabaseFromExport(name)
            val database = Room.databaseBuilder(context, WallCrawlDatabase::class.java, name).build()
            try {
                assertDefaultsIdentityAndForeignKeys(database.openHelper.writableDatabase, migrated = false)
            } finally {
                database.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun everySupportedStartMatchesCurrentExportAndStillOpensWithRoom() {
        for (startingVersion in 1 until WALLCRAWL_SCHEMA_VERSION) {
            val name = "export-migration-$startingVersion.db"
            context.deleteDatabase(name)
            try {
                createHistoricalDatabase(name, startingVersion)
                val database = Room.databaseBuilder(context, WallCrawlDatabase::class.java, name)
                    .addMigrations(*WallCrawlDatabase.ALL_MIGRATIONS).build()
                try {
                    val sqlite = database.openHelper.writableDatabase
                    assertDefaultsIdentityAndForeignKeys(sqlite, migrated = true)
                    sqlite.query("SELECT name FROM user_profiles").use { cursor ->
                        assertThat(cursor.moveToFirst()).isTrue()
                        assertThat(cursor.getString(0)).isEqualTo("Migration Crawler")
                    }
                    if (startingVersion >= 4) {
                        sqlite.query("SELECT weightUnit FROM workout_sessions WHERE id='session-7'").use { cursor ->
                            assertThat(cursor.moveToFirst()).isTrue()
                            assertThat(cursor.getString(0)).isEqualTo("KG")
                        }
                        sqlite.query("SELECT completedWeight FROM workout_sets WHERE id='set-7'").use { cursor ->
                            assertThat(cursor.moveToFirst()).isTrue()
                            assertThat(cursor.getDouble(0)).isEqualTo(24.0)
                        }
                        sqlite.query("SELECT weightUnit FROM workout_templates WHERE id='template-7'").use { cursor ->
                            assertThat(cursor.moveToFirst()).isTrue()
                            assertThat(cursor.isNull(0)).isTrue()
                        }
                    }
                } finally {
                    database.close()
                }
            } finally {
                context.deleteDatabase(name)
            }
        }
    }

    private fun createDatabaseFromExport(name: String) {
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { sqlite ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                entity.optJSONArray("indices")?.let { indices ->
                    for (indexIndex in 0 until indices.length()) {
                        sqlite.execSQL(indices.getJSONObject(indexIndex).getString("createSql")
                            .replace("\${TABLE_NAME}", table))
                    }
                }
            }
            schema.optJSONArray("views")?.let { views ->
                for (index in 0 until views.length()) sqlite.execSQL(views.getJSONObject(index).getString("createSql"))
            }
            // Omit identity setup so Room must validate the exported SQL on opening.
            sqlite.version = schema.getInt("version")
        }
    }

    private fun createHistoricalDatabase(name: String, version: Int) {
        val fixtureVersion = minOf(version, 11)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use {
            LegacyDatabaseFixtures.createSchema(it, fixtureVersion)
            LegacyDatabaseFixtures.insertProfile(it, fixtureVersion)
            if (fixtureVersion >= 4) LegacyDatabaseFixtures.insertHistoryAndTemplate(it)
            it.version = fixtureVersion
        }
        if (version > 11) {
            FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                    .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                        override fun onCreate(db: SupportSQLiteDatabase) = error("Existing fixture required")
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                            WallCrawlDatabase.ALL_MIGRATIONS.filter {
                                it.startVersion >= oldVersion && it.endVersion <= newVersion
                            }.forEach { it.migrate(db) }
                        }
                    }).build()
            ).use { it.writableDatabase }
        }
    }

    private fun assertDefaultsIdentityAndForeignKeys(sqlite: SupportSQLiteDatabase, migrated: Boolean) {
        assertThat(sqlite.version).isEqualTo(WALLCRAWL_SCHEMA_VERSION)
        sqlite.query("SELECT identity_hash FROM room_master_table WHERE id=42").use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo(schema.getString("identityHash"))
        }
        sqlite.query("PRAGMA foreign_key_check").use { assertThat(it.count).isEqualTo(0) }
        val entities = schema.getJSONArray("entities")
        val expectedTables = (0 until entities.length()).map { entities.getJSONObject(it).getString("tableName") }
        sqlite.query("SELECT name FROM sqlite_master WHERE type='table' " +
            "AND name NOT IN ('room_master_table','android_metadata','sqlite_sequence')").use { cursor ->
            val actual = buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            assertThat(actual).containsExactlyElementsIn(expectedTables)
        }
        for (index in 0 until entities.length()) {
            val entity = entities.getJSONObject(index)
            val table = entity.getString("tableName")
            val fields = entity.getJSONArray("fields")
            val defaults = (0 until fields.length()).associate { fieldIndex ->
                val field = fields.getJSONObject(fieldIndex)
                field.getString("columnName") to if (field.has("defaultValue")) field.getString("defaultValue") else null
            }.toMutableMap()
            // Pre-export migrations have two extra SQL defaults; preserve them (see room-schemas.md).
            if (migrated && table == "user_profiles") defaults["revision"] = "0"
            if (migrated && table == "workout_sessions") defaults["weightUnit"] = "'LBS'"
            sqlite.query("PRAGMA table_info(`$table`)").use { cursor ->
                val actual = mutableMapOf<String, String?>()
                while (cursor.moveToNext()) {
                    actual[cursor.getString(cursor.getColumnIndexOrThrow("name"))] =
                        cursor.getString(cursor.getColumnIndexOrThrow("dflt_value"))
                }
                assertThat(actual).containsExactlyEntriesIn(defaults)
            }
        }
    }
}
