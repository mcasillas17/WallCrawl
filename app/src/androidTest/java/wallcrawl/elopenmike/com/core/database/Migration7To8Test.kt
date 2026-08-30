package wallcrawl.elopenmike.com.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType

@RunWith(AndroidJUnit4::class)
class Migration7To8Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: WallCrawlDatabase? = null

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrate_preservesProfileAndHistoryWhileAddingUnknownCapabilities() = runBlocking {
        createVersion7Database()

        database = openDatabase()
        val sqlite = checkNotNull(database).openHelper.writableDatabase

        sqlite.query(
            "SELECT revision,name,primaryGoal,experienceLevel,preferredDurationMinutes," +
                "daysPerWeek,availableEquipmentJson,preferredUnit,musclePrioritiesJson," +
                "excludedExerciseIdsJson,onboardingCompleted,trainingConstraintsJson," +
                "returningAfterBreakWeeks,confirmedStartingLoadsJson,fitnessGoalsJson," +
                "themePreference,movementCapabilitiesJson FROM user_profiles"
        ).use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getLong(0)).isEqualTo(17L)
            assertThat(cursor.getString(1)).isEqualTo("Migration Crawler")
            assertThat(cursor.getString(2)).isEqualTo("STRENGTH")
            assertThat(cursor.getString(3)).isEqualTo("ADVANCED")
            assertThat(cursor.getInt(4)).isEqualTo(75)
            assertThat(cursor.getInt(5)).isEqualTo(5)
            assertThat(cursor.getString(6)).isEqualTo("Bodyweight|||Dumbbell")
            assertThat(cursor.getString(7)).isEqualTo("KG")
            assertThat(cursor.getString(8)).isEqualTo("Chest:HIGH|||Back:LOW")
            assertThat(cursor.getString(9)).isEqualTo("burpee")
            assertThat(cursor.getInt(10)).isEqualTo(1)
            assertThat(cursor.getString(11)).isEqualTo("KNEE_SENSITIVE|||LOW_IMPACT_ONLY")
            assertThat(cursor.getInt(12)).isEqualTo(12)
            assertThat(cursor.getString(13)).isEqualTo("goblet-squat:24.0")
            assertThat(cursor.getString(14)).isEqualTo("STRENGTH|||BUILD_MUSCLE")
            assertThat(cursor.getString(15)).isEqualTo("DARK")
            assertThat(cursor.getString(16)).isEqualTo("{}")
        }

        assertPreservedRows(sqlite)
        sqlite.query("PRAGMA foreign_key_check").use { cursor ->
            assertThat(cursor.count).isEqualTo(0)
        }

        val profile = OfflineUserProfileRepository(checkNotNull(database).userProfileDao())
            .getProfileOnce()
        assertThat(profile.onboardingCompleted).isTrue()
        MovementCapabilityType.entries.forEach { type ->
            assertThat(profile.movementCapabilities[type]).isEqualTo(CapabilityLevel.UNKNOWN)
        }
    }

    @Test
    fun migratedDatabase_reloadsThroughANewRoomAndRepositoryInstance() = runBlocking {
        createVersion7Database()
        database = openDatabase()
        checkNotNull(database).close()
        database = null

        database = openDatabase()
        val profile = OfflineUserProfileRepository(checkNotNull(database).userProfileDao())
            .getProfileOnce()

        assertThat(profile.name).isEqualTo("Migration Crawler")
        assertThat(profile.revision).isEqualTo(17L)
        assertThat(profile.onboardingCompleted).isTrue()
        MovementCapabilityType.entries.forEach { type ->
            assertThat(profile.movementCapabilities[type]).isEqualTo(CapabilityLevel.UNKNOWN)
        }
    }

    private fun createVersion7Database() {
        context.deleteDatabase(DATABASE_NAME)
        context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null).use { db ->
            LegacyDatabaseFixtures.createSchema(db, version = 7)
            LegacyDatabaseFixtures.insertProfile(db, version = 7)
            LegacyDatabaseFixtures.insertVersion7HistoryAndTemplate(db)
            db.version = 7
        }
    }

    private fun openDatabase(): WallCrawlDatabase =
        Room.databaseBuilder(context, WallCrawlDatabase::class.java, DATABASE_NAME)
            .addMigrations(WallCrawlDatabase.MIGRATION_7_8)
            .build()

    private fun assertPreservedRows(sqlite: androidx.sqlite.db.SupportSQLiteDatabase) {
        sqlite.query(
            "SELECT name,status,origin,sourceTemplateId,notes FROM workout_sessions"
        ).use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("Preserved Session")
            assertThat(cursor.getString(1)).isEqualTo("COMPLETED")
            assertThat(cursor.getString(2)).isEqualTo("CUSTOM_TEMPLATE")
            assertThat(cursor.getString(3)).isEqualTo("template-7")
            assertThat(cursor.getString(4)).isEqualTo("session note")
        }
        sqlite.query("SELECT exerciseId,targetWeight,notes FROM workout_exercises").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("goblet-squat")
            assertThat(cursor.getDouble(1)).isEqualTo(24.0)
            assertThat(cursor.getString(2)).isEqualTo("exercise note")
        }
        sqlite.query("SELECT completedReps,completedWeight,isCompleted FROM workout_sets").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getInt(0)).isEqualTo(9)
            assertThat(cursor.getDouble(1)).isEqualTo(24.0)
            assertThat(cursor.getInt(2)).isEqualTo(1)
        }
        sqlite.query("SELECT name,notes FROM workout_templates").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("Preserved Template")
            assertThat(cursor.getString(1)).isEqualTo("template note")
        }
        sqlite.query(
            "SELECT exerciseId,targetWeight,notes FROM workout_template_exercises"
        ).use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("goblet-squat")
            assertThat(cursor.getDouble(1)).isEqualTo(24.0)
            assertThat(cursor.getString(2)).isEqualTo("template exercise note")
        }
    }

    private companion object {
        const val DATABASE_NAME = "migration-7-8.db"
    }
}
