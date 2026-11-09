package wallcrawl.elopenmike.com.feature.profile

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.database.WallCrawlDatabase
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.model.*
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class DumbbellRackEditorTest {
    private val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: WallCrawlDatabase
    private lateinit var repository: OfflineUserProfileRepository
    private var localized = context
    @get:Rule val rules = RuleChain.outerRule(object : ExternalResource() {
        override fun after() { if (::database.isInitialized) database.close() }
    }).around(compose).around(object : ExternalResource() {
        override fun after() = compose.waitForIdle()
    })

    @Test fun equipmentRackCanBeSavedEditedAndCleared() = exerciseEditor(Locale.ENGLISH, "2.75", 1f)

    @Test fun spanishDecimalInputAndAccessibleControlsFitNarrowLargeText() =
        exerciseEditor(Locale.forLanguageTag("es-ES"), "2,75", 1.5f)

    private fun exerciseEditor(locale: Locale, input: String, fontScale: Float) {
        database = Room.inMemoryDatabaseBuilder(context, WallCrawlDatabase::class.java).build()
        repository = OfflineUserProfileRepository(database.userProfileDao())
        runBlocking { repository.saveProfile(UserProfile(preferredUnit = WeightUnit.KG, availableEquipment = listOf(StandardEquipment.DUMBBELL))) }
        val configuration = Configuration(context.resources.configuration).apply { setLocales(LocaleList(locale)) }
        localized = context.createConfigurationContext(configuration)
        val vm = ProfileViewModel(repository)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized, LocalResources provides localized.resources,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(context.resources.displayMetrics.density, fontScale)) {
                WallCrawlTheme { Box(Modifier.width(320.dp)) { ProfileScreen(vm, onOpenCredits = {}) } }
            }
        }
        click(R.string.guided_equipment)
        click(R.string.rack_edit)
        reveal(hasText("lb")).performClick()
        compose.onNodeWithText(text(R.string.rack_weight_label, "lb")).assertExists()
        reveal(hasText("kg")).performClick()
        reveal(hasText(text(R.string.rack_weight_label, "kg"))).performTextInput(input)
        click(R.string.rack_add)
        reveal(hasContentDescription(text(R.string.rack_remove_weight, input, "kg")))
            .assertHasClickAction().assertHeightIsAtLeast(48.dp)
        click(R.string.rack_save)
        compose.waitUntil(5_000) { vm.dumbbellRackEditor.value.weights == null }
        assertThat(runBlocking { repository.getProfileOnce().dumbbellRack }).isEqualTo(DumbbellRack(WeightUnit.KG, listOf(2.75)))
        click(R.string.rack_edit)
        reveal(hasContentDescription(text(R.string.rack_remove_weight, input, "kg"))).performClick()
        click(R.string.rack_cancel)
        assertThat(runBlocking { repository.getProfileOnce().dumbbellRack?.weights }).containsExactly(2.75)
        click(R.string.rack_edit)
        click(R.string.rack_clear)
        click(R.string.rack_save)
        compose.waitUntil(5_000) { vm.dumbbellRackEditor.value.weights == null }
        assertThat(runBlocking { repository.getProfileOnce().dumbbellRack }).isNull()
        reveal(hasText(text(R.string.rack_unset))).assertIsDisplayed()
    }

    private fun text(id: Int, vararg args: Any) = localized.getString(id, *args)
    private fun reveal(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        compose.onNodeWithTag("profile-settings-list").performScrollToNode(matcher)
        return compose.onNode(matcher).performScrollTo()
    }
    private fun click(id: Int) { reveal(hasText(text(id))).performClick() }
}
