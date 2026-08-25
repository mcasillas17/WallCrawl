package wallcrawl.elopenmike.com

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import wallcrawl.elopenmike.com.app.WallCrawlApp
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as WallCrawlApplication).container

        setContent {
            WallCrawlTheme {
                WallCrawlApp(container = appContainer)
            }
        }
    }
}
