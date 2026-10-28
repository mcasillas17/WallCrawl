package wallcrawl.elopenmike.com.core.ui.format

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The editable round trip on the device's own ICU-backed java.text, where a default
 * non-ASCII digit locale actually occurs; the JVM test runs on the JDK's implementation.
 */
@RunWith(AndroidJUnit4::class)
class LocaleFormattingDeviceTest {
    @Test fun editableTextReadsBackUnderLocalesWithNonAsciiDefaultDigits() {
        listOf("ar-EG-u-nu-arab", "fa-IR-u-nu-arabext", "ar-EG", "fa-IR", "es-ES", "en-US")
            .map(Locale::forLanguageTag)
            .forEach { locale ->
                val tag = locale.toLanguageTag()
                assertEquals(tag, 61.25, LocaleFormatting.parseDecimalInput(LocaleFormatting.formatEditableDecimal(61.25, locale)))
                assertEquals(tag, 90, LocaleFormatting.parseIntInput(LocaleFormatting.formatEditableInt(90, locale)))
            }
    }

    @Test fun readOnlyLabelsKeepTheLocalesOwnDigits() {
        val arabic = Locale.forLanguageTag("ar-EG-u-nu-arab")
        assertEquals("\u0661" to "\u0660\u0665", LocaleFormatting.countdownParts(65, arabic))
        assertEquals("\u0663", LocaleFormatting.formatCount(3, arabic))
        assertEquals(false, LocaleFormatting.formatMeasurement(47.5, arabic).any { it in '0'..'9' })
        assertEquals("1" to "05", LocaleFormatting.countdownParts(65, Locale.US))
    }
}
