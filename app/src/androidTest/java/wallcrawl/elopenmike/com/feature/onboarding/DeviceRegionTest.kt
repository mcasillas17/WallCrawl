package wallcrawl.elopenmike.com.feature.onboarding

import android.content.res.Resources
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceRegionTest {
    @Test
    fun regionIgnoresARegionlessAppLocale() {
        val systemRegion = Resources.getSystem().configuration.locales[0].country
        val previous = Locale.getDefault()
        try {
            // What the language chip leaves behind: a bare language, no country.
            Locale.setDefault(Locale("es"))
            assertThat(Locale.getDefault().country).isEmpty()
            assertThat(deviceRegion()).isEqualTo(systemRegion)
        } finally {
            Locale.setDefault(previous)
        }
    }
}
