package wallcrawl.elopenmike.com.app

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

class WallCrawlBottomBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bottomBar_isAtLeastMaterialDefaultHeight() {
        composeRule.setContent {
            WallCrawlTheme {
                WallCrawlBottomBar(
                    currentRoute = AppRoutes.TODAY,
                    onNavigate = {}
                )
            }
        }

        composeRule
            .onNodeWithTag(WALL_CRAWL_BOTTOM_BAR_TEST_TAG)
            .assertHeightIsAtLeast(80.dp)
    }
}
