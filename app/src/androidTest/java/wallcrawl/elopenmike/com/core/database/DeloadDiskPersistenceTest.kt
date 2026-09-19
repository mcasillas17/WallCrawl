package wallcrawl.elopenmike.com.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.WallCrawlApplication
import wallcrawl.elopenmike.com.core.ai.DeloadOfferPolicy
import wallcrawl.elopenmike.com.core.database.repository.OfflineDeloadRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.model.DeloadAction
import wallcrawl.elopenmike.com.core.model.DeloadChoiceStatus
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit

@RunWith(AndroidJUnit4::class)
class DeloadDiskPersistenceTest {
    @Test
    fun decisionsSurviveClosingAndReopeningTheDatabase() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "deload-${UUID.randomUUID()}.db"
        val gate = Mutex()
        fun open() = Room.databaseBuilder(context, WallCrawlDatabase::class.java, name).build()
        var database = open()
        try {
            val profiles = OfflineUserProfileRepository(database.userProfileDao(), gate)
            profiles.saveProfile(UserProfile(onboardingCompleted = true, returningAfterBreakWeeks = 4))
            val profile = profiles.getProfileOnce()
            var repository = OfflineDeloadRepository(database.deloadPreferencesDao(), gate)
            val offer = requireNotNull(DeloadOfferPolicy.offer(profile, repository.get()))
            repository.decide(DeloadAction.ACCEPT, profile.revision, 0, offer.id)
            database.close()
            database = open()
            repository = OfflineDeloadRepository(database.deloadPreferencesDao(), gate)
            assertThat(repository.get().choice?.status).isEqualTo(DeloadChoiceStatus.ACCEPTED)
            assertThat(repository.get().choice?.offer).isEqualTo(offer)
            repository.decide(DeloadAction.CANCEL, profile.revision, 1, offer.id)
            repository.decide(DeloadAction.REQUEST, profile.revision, 2, null)
            val request = requireNotNull(repository.get().choice).offer
            repository.decide(DeloadAction.DECLINE, profile.revision, 3, request.id)
            database.close()
            database = open()
            repository = OfflineDeloadRepository(database.deloadPreferencesDao(), gate)
            assertThat(repository.get().choice?.status).isEqualTo(DeloadChoiceStatus.DECLINED)
            assertThat(DeloadOfferPolicy.offer(profile, repository.get())).isNull()
        } finally {
            database.close()
            context.deleteDatabase(name)
        }

        // Explicit opt-in fixture for repeatable manual process-death/UI evidence on a
        // dedicated emulator. The normal connected suite never changes the app database.
        if (InstrumentationRegistry.getArguments().getString("seedPackage9Demo") == "true") {
            (context.applicationContext as WallCrawlApplication).container.userProfileRepository.saveProfile(
                UserProfile(name = "Package9", onboardingCompleted = true, preferredUnit = WeightUnit.KG)
            )
        }
    }
}
