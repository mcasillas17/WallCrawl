package wallcrawl.elopenmike.com.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ConditionReassessmentTest {
    @Test fun pendingMarkerUsesProfileAndOptionalSetStringIds() {
        val marker = PendingConditionReassessment("default_user", "condition", "exercise", "event",
            "a".repeat(64), 1, 100, "a03c8a04-9f34-45e2-9654-d5cdb16aeb77")
        assertEquals("default_user", marker.profileId)
        assertEquals("a03c8a04-9f34-45e2-9654-d5cdb16aeb77", marker.sourceSetId)
        assertEquals(null, marker.copy(sourceSetId = null).sourceSetId)
        assertThrows(IllegalArgumentException::class.java) { marker.copy(profileId = "") }
        assertThrows(IllegalArgumentException::class.java) { marker.copy(sourceSetId = "") }
    }
}
