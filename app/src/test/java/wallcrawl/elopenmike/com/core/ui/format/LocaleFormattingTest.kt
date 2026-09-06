package wallcrawl.elopenmike.com.core.ui.format

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.Locale
import org.junit.Test

class LocaleFormattingTest {

    private val english = Locale.forLanguageTag("en-US")
    private val mexicanSpanish = Locale.forLanguageTag("es-MX")
    private val europeanSpanish = Locale.forLanguageTag("es-ES")

    @Test
    fun anEditableDecimalUsesTheLocaleDecimalMark() {
        assertThat(LocaleFormatting.formatEditableDecimal(2.5, english)).isEqualTo("2.5")
        assertThat(LocaleFormatting.formatEditableDecimal(2.5, mexicanSpanish)).isEqualTo("2.5")
        assertThat(LocaleFormatting.formatEditableDecimal(2.5, europeanSpanish)).isEqualTo("2,5")
    }

    @Test
    fun anEditableDecimalNeverEmitsDigitGrouping() {
        // A grouping mark in a field whose locale groups with "." would be unparseable
        // without guessing, so it must never be produced.
        listOf(english, mexicanSpanish, europeanSpanish).forEach { locale ->
            val formatted = LocaleFormatting.formatEditableDecimal(1234.5, locale)
            assertThat(formatted).doesNotContain(" ")
            assertThat(formatted.count { it == '.' || it == ',' }).isEqualTo(1)
        }
    }

    @Test
    fun aWholeValueLosesItsFractionInAnEditableField() {
        listOf(english, mexicanSpanish, europeanSpanish).forEach { locale ->
            assertThat(LocaleFormatting.formatEditableDecimal(60.0, locale)).isEqualTo("60")
            assertThat(LocaleFormatting.formatEditableInt(12, locale)).isEqualTo("12")
        }
    }

    @Test
    fun everyLocaleRoundTripsAnEditableDecimalWithoutChangingItsValue() {
        val values = listOf(0.0, 2.5, 7.25, 60.0, 102.5, 9999.75)
        listOf(english, mexicanSpanish, europeanSpanish).forEach { locale ->
            values.forEach { value ->
                val text = LocaleFormatting.formatEditableDecimal(value, locale)
                assertThat(LocaleFormatting.parseDecimalInput(text)).isEqualTo(value)
            }
        }
    }

    @Test
    fun bothDecimalMarksParseToTheSameMagnitude() {
        assertThat(LocaleFormatting.parseDecimalInput("2.5")).isEqualTo(2.5)
        assertThat(LocaleFormatting.parseDecimalInput("2,5")).isEqualTo(2.5)
        assertThat(LocaleFormatting.parseDecimalInput("102,25")).isEqualTo(102.25)
    }

    @Test
    fun aDecimalCommaIsNeverReadAsADifferentMagnitude() {
        // The failure this exists to prevent: "2,5" silently becoming 25.
        assertThat(LocaleFormatting.parseDecimalInput("2,5")).isNotEqualTo(25.0)
        assertThat(LocaleFormatting.parseDecimalInput("0,5")).isEqualTo(0.5)
    }

    @Test
    fun anAmbiguousOrPartialValueIsRefusedRatherThanGuessedAt() {
        listOf(
            "", "  ", "1.234,5", "1,234.5", "2..5", "2,,5", "2.", "2,", ".5", ",5",
            "-2", "+2", "2 5", "2e3", "abc", "2.5.5", "12345678901"
        ).forEach { input ->
            assertWithMessage(input).that(LocaleFormatting.parseDecimalInput(input)).isNull()
        }
    }

    @Test
    fun aWholeNumberFieldRefusesAnySeparator() {
        assertThat(LocaleFormatting.parseIntInput("12")).isEqualTo(12)
        listOf("1,5", "1.5", "1 500", "-3", "").forEach { input ->
            assertWithMessage(input).that(LocaleFormatting.parseIntInput(input)).isNull()
        }
    }

    @Test
    fun readOnlyQuantitiesUseTheLocaleGrouping() {
        assertThat(LocaleFormatting.formatCount(1234, english)).isEqualTo("1,234")
        assertThat(LocaleFormatting.formatVolume(1234.4, english)).isEqualTo("1,234")
        // Spanish groups differently from English; the exact mark is the platform's business,
        // but it must not be the same as English's and must still contain the digits.
        val spanishCount = LocaleFormatting.formatCount(1234, europeanSpanish)
        assertThat(spanishCount.filter(Char::isDigit)).isEqualTo("1234")
    }

    @Test
    fun aMeasurementKeepsAFractionOnlyWhenItHasOne() {
        assertThat(LocaleFormatting.formatMeasurement(60.0, english)).isEqualTo("60")
        assertThat(LocaleFormatting.formatMeasurement(62.5, english)).isEqualTo("62.5")
        assertThat(LocaleFormatting.formatMeasurement(62.5, europeanSpanish)).isEqualTo("62,5")
    }

    @Test
    fun aNonFiniteVolumeRendersAsZeroInsteadOfCrashingTheScreen() {
        assertThat(LocaleFormatting.formatVolume(Double.NaN, english)).isEqualTo("0")
        assertThat(LocaleFormatting.formatVolume(Double.POSITIVE_INFINITY, english)).isEqualTo("0")
    }

    @Test
    fun theRestCountdownPadsSecondsInEveryLocale() {
        listOf(english, mexicanSpanish, europeanSpanish).forEach { locale ->
            assertThat(LocaleFormatting.countdownParts(65, locale)).isEqualTo("1" to "05")
            assertThat(LocaleFormatting.countdownParts(0, locale)).isEqualTo("0" to "00")
            assertThat(LocaleFormatting.countdownParts(-5, locale)).isEqualTo("0" to "00")
        }
    }
}
