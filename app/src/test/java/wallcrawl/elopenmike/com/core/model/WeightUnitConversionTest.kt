package wallcrawl.elopenmike.com.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WeightUnitConversionTest {

    @Test
    fun convertWeight_betweenPoundsAndKilograms_preservesPhysicalLoad() {
        assertThat(convertWeight(100.0, from = WeightUnit.LBS, to = WeightUnit.KG))
            .isWithin(0.0001)
            .of(45.3592)
        assertThat(convertWeight(45.3592, from = WeightUnit.KG, to = WeightUnit.LBS))
            .isWithin(0.0001)
            .of(100.0)
    }

    @Test
    fun convertWeight_sameUnit_returnsOriginalValue() {
        assertThat(convertWeight(47.5, from = WeightUnit.LBS, to = WeightUnit.LBS))
            .isEqualTo(47.5)
    }
}
