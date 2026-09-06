package wallcrawl.elopenmike.com

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import wallcrawl.elopenmike.com.app.WallCrawlApp
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

/**
 * AppCompatActivity rather than ComponentActivity: below Android 13 the per-app language
 * APIs are applied by the AppCompat delegate, so a plain ComponentActivity would keep
 * rendering in the device language whatever the selector said.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as WallCrawlApplication).container

        setContent {
            val profile by appContainer.userProfileRepository.getUserProfile().collectAsState(initial = null)
            val themePreference = profile?.themePreference ?: ThemePreference.SYSTEM

            WallCrawlTheme(themePreference = themePreference) {
                WallCrawlApp(container = appContainer, profile = profile)
            }
        }
    }
}
