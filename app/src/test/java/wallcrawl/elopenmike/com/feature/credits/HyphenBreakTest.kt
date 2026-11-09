package wallcrawl.elopenmike.com.feature.credits

import org.junit.Assert.assertEquals
import org.junit.Test

class HyphenBreakTest {

    @Test
    fun aHyphenBetweenLettersGetsOneBreakOpportunity() {
        assertEquals("non-\u200Bcommercial use", allowBreaksAfterHyphens("non-commercial use"))
    }

    @Test
    fun linksPathsAndNumbersAreLeftAlone() {
        val text = "[CC BY-SA](https://creativecommons.org/licenses/by-sa/4.0/) assets/body-front.png 2020-2024"
        assertEquals(text, allowBreaksAfterHyphens(text))
    }
}
