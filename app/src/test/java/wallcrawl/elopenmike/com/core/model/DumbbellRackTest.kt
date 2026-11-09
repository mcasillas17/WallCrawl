package wallcrawl.elopenmike.com.core.model

import org.junit.Assert.*
import org.junit.Test

class DumbbellRackTest {
    @Test fun validatesExplicitRackWithoutChangingUnits() {
        val rack = DumbbellRack(WeightUnit.LBS, listOf(2.5, 5.0, 7.25))
        assertEquals(rack, UserProfile(dumbbellRack = rack).copy(preferredUnit = WeightUnit.KG).dumbbellRack)
        assertNull(UserProfile().dumbbellRack)
        assertEquals(64, DumbbellRack.MAX_WEIGHTS)
    }

    @Test fun rejectsInvalidLoadsAndNoncanonicalOrdering() {
        listOf(emptyList(), listOf(0.0), listOf(-1.0), listOf(Double.NaN),
            listOf(Double.POSITIVE_INFINITY), listOf(1.001), listOf(5.0, 5.0), listOf(10.0, 5.0),
            List(65) { it + 1.0 }).forEach { weights ->
            assertThrows(IllegalArgumentException::class.java) { DumbbellRack(WeightUnit.LBS, weights) }
        }
        WeightUnit.entries.forEach { unit ->
            assertThrows(IllegalArgumentException::class.java) {
                DumbbellRack(unit, listOf(WorkoutTemplate.maxLoad(unit) + 1.0))
            }
        }
    }
}
