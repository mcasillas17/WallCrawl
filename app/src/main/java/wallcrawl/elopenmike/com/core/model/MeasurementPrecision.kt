package wallcrawl.elopenmike.com.core.model

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** The logger's representation precision, not a training tolerance. */
object MeasurementPrecision {
    const val EDITABLE_FRACTION_DIGITS = 2

    fun sameEditableValue(a: Double, b: Double): Boolean {
        if (!a.isFinite() || !b.isFinite()) return false
        if (a == b) return true
        val format = DecimalFormat("0", DecimalFormatSymbols(Locale.ROOT)).apply {
            isGroupingUsed = false
            maximumFractionDigits = EDITABLE_FRACTION_DIGITS
            roundingMode = RoundingMode.HALF_UP
        }
        return format.format(a) == format.format(b)
    }
}
