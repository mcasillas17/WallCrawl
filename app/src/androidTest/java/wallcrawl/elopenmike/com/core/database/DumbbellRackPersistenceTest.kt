package wallcrawl.elopenmike.com.core.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.database.repository.*
import wallcrawl.elopenmike.com.core.model.*

@RunWith(AndroidJUnit4::class)
class DumbbellRackPersistenceTest {
    @Test fun targetedWritesPreserveOtherFieldsAndRackSourceUnit() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, WallCrawlDatabase::class.java).build()
        try {
            val repository = OfflineUserProfileRepository(database.userProfileDao())
            val rack = DumbbellRack(WeightUnit.LBS, listOf(5.0, 12.5, 20.0))
            repository.updateDumbbellRackIfUnchanged(null, rack)
            repository.updateUnit(WeightUnit.KG)
            assertEquals(rack, repository.getProfileOnce().dumbbellRack)
            assertTrue(repository.updateDumbbellRackIfUnchanged(rack, null))
            assertFalse(repository.updateDumbbellRackIfUnchanged(rack, rack))
            assertNull(repository.getProfileOnce().dumbbellRack)
            assertEquals(WeightUnit.KG, repository.getProfileOnce().preferredUnit)
        } finally { database.close() }
    }

    @Test fun rowCodecRejectsMalformedAndFutureRacks() {
        val rack = DumbbellRack(WeightUnit.KG, listOf(2.5, 5.0))
        assertEquals(rack, DumbbellRackCodec.decode(DumbbellRackCodec.encode(rack)))
        listOf(
            "{\"unit\":\"KG\",\"weights\":[5,5]}",
            "{\"unit\":\"KG\",\"weights\":[0]}",
            "{\"unit\":\"KG\",\"weights\":[1.001]}",
            "{\"unit\":\"KG\",\"weights\":[5],\"future\":true}",
            "{\"unit\":\"FUTURE\",\"weights\":[5]}"
        ).forEach { raw -> assertThrows(Exception::class.java) { DumbbellRackCodec.decode(raw) } }
    }
}
