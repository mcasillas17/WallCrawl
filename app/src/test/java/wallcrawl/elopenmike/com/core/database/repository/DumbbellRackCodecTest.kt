package wallcrawl.elopenmike.com.core.database.repository

import org.junit.Assert.*
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.DumbbellRack
import wallcrawl.elopenmike.com.core.model.WeightUnit

class DumbbellRackCodecTest {
    @Test fun canonicalRowsRoundTripInBothUnits() {
        WeightUnit.entries.forEach { unit ->
            val rack = DumbbellRack(unit, listOf(0.01, 2.5, 12.25))
            assertEquals(rack, DumbbellRackCodec.decode(DumbbellRackCodec.encode(rack)))
        }
    }

    @Test fun malformedAndFutureRowsAreRefused() {
        listOf(
            "{\"unit\":\"KG\",\"weights\":[5.0,5.0]}",
            "{\"unit\":\"KG\",\"weights\":[0.0]}",
            "{\"unit\":\"KG\",\"weights\":[1.001]}",
            "{\"unit\":\"KG\",\"weights\":[\"5.0\"]}",
            "{\"unit\":\"KG\",\"weights\":[NaN]}",
            "{\"unit\":\"KG\",\"weights\":[5.0],\"future\":true}",
            "{\"unit\":\"FUTURE\",\"weights\":[5.0]}",
            "{\"unit\":\"KG\",\"weights\":[]}",
            "x".repeat(4097)
        ).forEach { raw -> assertThrows(IllegalArgumentException::class.java) { DumbbellRackCodec.decode(raw) } }
    }
}
