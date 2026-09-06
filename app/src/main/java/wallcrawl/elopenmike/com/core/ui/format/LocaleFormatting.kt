package wallcrawl.elopenmike.com.core.ui.format

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

/**
 * Every number the interface shows or accepts passes through here.
 *
 * Presentation is locale-aware: a Spanish reader sees the decimal mark and the grouping
 * their locale uses. Meaning is not: the stored value is always the same `Double`, the
 * archive always serialises it the same way, and switching language never converts a unit
 * or changes a magnitude. That split is the whole point of this file.
 */
object LocaleFormatting {

    /** Fraction digits kept in an editable field. Loads step by 2.5, so two is enough. */
    private const val MAX_EDITABLE_FRACTION_DIGITS = 2

    /**
     * A measurement as it appears in an editable field.
     *
     * Grouping is deliberately off: the value has to be retypable, and a grouping mark in a
     * field whose locale uses "." for grouping and "," for decimals is exactly the ambiguity
     * [parseDecimalInput] refuses to guess at.
     */
    fun formatEditableDecimal(value: Double, locale: Locale): String =
        editableFormat(locale).format(value)

    /** A whole number as it appears in an editable field, with no grouping. */
    fun formatEditableInt(value: Int, locale: Locale): String =
        editableFormat(locale).format(value.toLong())

    /** A counted quantity for reading, with the locale's digit grouping. */
    fun formatCount(value: Int, locale: Locale): String =
        NumberFormat.getIntegerInstance(locale).format(value.toLong())

    /** A large read-only quantity such as weekly volume, rounded to whole units. */
    fun formatVolume(value: Double, locale: Locale): String =
        NumberFormat.getIntegerInstance(locale).format(value.roundedToLong())

    /**
     * A measurement for reading: grouped, and with a fraction only when the value has one.
     * Weight targets and logged loads use this, so "2.5" never reads as "2.50".
     */
    fun formatMeasurement(value: Double, locale: Locale): String =
        NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = MAX_EDITABLE_FRACTION_DIGITS
            minimumFractionDigits = 0
            roundingMode = RoundingMode.HALF_UP
        }.format(value)

    /**
     * Parses what someone typed into a numeric field, or null when it is not a value.
     *
     * Both "." and "," are accepted as the decimal mark, because the two Spanish locales
     * WallCrawl ships to disagree about which one their keyboard offers and a field that
     * refused the other would be unusable. Exactly one separator is allowed and it always
     * means "decimal point": a second separator, or a separator with nothing after it, is
     * refused rather than guessed at, so "1.234,5" is never silently read as 1234.5 and
     * "2,5" is never read as 25. WallCrawl never renders digit grouping into an editable
     * field, so no legitimate input can contain one.
     *
     * A partially typed value ("2.", ",5", "") is refused too, which is what keeps a
     * keystroke mid-edit from being submitted as a completed set.
     */
    fun parseDecimalInput(text: String): Double? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_INPUT_LENGTH) return null

        var separatorIndex = -1
        trimmed.forEachIndexed { index, character ->
            when {
                character in '0'..'9' -> Unit
                character == '.' || character == ',' -> {
                    if (separatorIndex >= 0) return null
                    separatorIndex = index
                }
                else -> return null
            }
        }
        if (separatorIndex == 0 || separatorIndex == trimmed.lastIndex) return null

        val normalized = if (separatorIndex < 0) {
            trimmed
        } else {
            trimmed.replaceRange(separatorIndex, separatorIndex + 1, ".")
        }
        return normalized.toDoubleOrNull()?.takeIf(Double::isFinite)
    }

    /** Parses a whole-number field. Rejects any separator, so "1,5" is never read as 15. */
    fun parseIntInput(text: String): Int? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_INPUT_LENGTH) return null
        if (!trimmed.all { it in '0'..'9' }) return null
        return trimmed.toIntOrNull()
    }

    /** Minutes and seconds for the rest countdown; the caller supplies the separator. */
    fun countdownParts(remainingSeconds: Int, locale: Locale): Pair<String, String> {
        val safeSeconds = remainingSeconds.coerceAtLeast(0)
        return formatEditableInt(safeSeconds / 60, locale) to
            formatEditableInt(safeSeconds % 60, locale).padStart(2, '0')
    }

    private fun editableFormat(locale: Locale): DecimalFormat =
        DecimalFormat("0.##", DecimalFormatSymbols.getInstance(locale)).apply {
            isGroupingUsed = false
            maximumFractionDigits = MAX_EDITABLE_FRACTION_DIGITS
            roundingMode = RoundingMode.HALF_UP
        }

    /**
     * Guards against a non-finite volume reaching a formatter. Progress sums logged loads,
     * and a corrupted row must render as zero rather than crash the screen.
     */
    private fun Double.roundedToLong(): Long =
        if (isFinite()) Math.round(this) else 0L

    /** Matches the character limit the set logger enforces on its text fields. */
    internal const val MAX_INPUT_LENGTH = 10
}
