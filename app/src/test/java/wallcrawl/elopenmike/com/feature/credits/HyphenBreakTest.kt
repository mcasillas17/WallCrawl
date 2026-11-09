package wallcrawl.elopenmike.com.feature.credits

import org.junit.Assert.assertEquals
import org.junit.Test

class HyphenBreakTest {

    @Test
    fun aHyphenBetweenLettersGetsOneBreakOpportunity() {
        assertEquals("non-\u200Bcommercial use", allowBreaksAfterHyphens("non-commercial use"))
    }

    @Test
    fun aLinkLabelBreaksButItsTargetDoesNot() {
        assertEquals(
            "[Non-\u200Bcommercial use](https://example.org/non-commercial)",
            allowBreaksAfterHyphens("[Non-commercial use](https://example.org/non-commercial)")
        )
        assertEquals(
            "a 12-month follow-\u200Bup.](https://example.org/x-y/)",
            allowBreaksAfterHyphens("a 12-month follow-up.](https://example.org/x-y/)")
        )
    }

    @Test
    fun linksPathsAndNumbersAreLeftAlone() {
        val text = "[CC BY 4.0](https://creativecommons.org/licenses/by-sa/4.0/) assets/body-front.png 2020-2024"
        assertEquals(text, allowBreaksAfterHyphens(text))
    }
}
