package wallcrawl.elopenmike.com.core.ui.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SetRowTest {

    @Test
    fun weightInputLabel_withNullTarget_asksUserToChooseStartingLoad() {
        assertThat(weightInputLabel(targetWeight = null, weightUnit = "kg"))
            .isEqualTo("Choose starting load")
    }

    @Test
    fun weightInputLabel_withKnownTarget_showsLoadAndUnit() {
        assertThat(weightInputLabel(targetWeight = 40.0, weightUnit = "kg"))
            .isEqualTo("Load kg")
    }
}
