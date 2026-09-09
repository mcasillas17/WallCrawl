package wallcrawl.elopenmike.com.core.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.Locales
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.then
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Locale
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.exercise.visual.IllustrationCatalog
import wallcrawl.elopenmike.com.core.exercise.visual.WorkoutGuideVisualProvider
import wallcrawl.elopenmike.com.core.exercise.workoutguide.WorkoutGuideCatalogStore
import wallcrawl.elopenmike.com.core.model.ThemePreference
import wallcrawl.elopenmike.com.core.model.ProfileGender
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.illustrationVariant
import wallcrawl.elopenmike.com.core.ui.components.ExerciseIllustration
import wallcrawl.elopenmike.com.core.ui.components.ProfileGenderSelector
import wallcrawl.elopenmike.com.core.ui.components.LocalIllustrationVariant
import wallcrawl.elopenmike.com.core.ui.theme.WallCrawlTheme

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class IllustrationSelectionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun profileGenderSwitchesTheRealBundledArtwork() = checkSelection("en", ThemePreference.DARK)
    @Test fun spanishControlsAndArtworkRemainUsableInLightTheme() = checkSelection("es", ThemePreference.LIGHT)

    private fun checkSelection(language: String, theme: ThemePreference) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(base.resources.configuration).apply { setLocale(Locale(language)) }
        val context = base.createConfigurationContext(configuration)
        val store = WorkoutGuideCatalogStore(context.assets)
        val exercise = runBlocking { store.snapshot().exercises.first { it.id == "bodyweight-squat" } }
        val provider = WorkoutGuideVisualProvider(store, IllustrationCatalog {
            context.assets.open("exercise-illustrations/index.json").bufferedReader().use { it.readText() }
        })
        var profile by mutableStateOf(UserProfile())
        var changes = 0
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.Locales(LocaleList(language)) then
                    DeviceConfigurationOverride.FontScale(if (language == "es") 1.6f else 1f)
            ) {
                CompositionLocalProvider(
                    LocalResources provides context.resources,
                    LocalIllustrationVariant provides profile.illustrationVariant
                ) {
                    WallCrawlTheme(themePreference = theme) {
                        Surface {
                            Column(Modifier.padding(16.dp)) {
                                ProfileGenderSelector(profile.gender,
                                    { profile = profile.copy(gender = it); changes++ },
                                    modifier = Modifier.testTag("gender-selector"))
                                ExerciseIllustration(exercise, provider,
                                    modifier = Modifier.testTag("exercise-artwork"), height = 280)
                            }
                        }
                    }
                }
            }
        }
        if (theme == ThemePreference.LIGHT) {
            val pixels = compose.onNodeWithText(context.getString(R.string.profile_gender_title))
                .captureToImage().toPixelMap()
            val darkest = (0 until pixels.height).minOf { y ->
                (0 until pixels.width).minOf { x -> pixels[x, y].luminance() }
            }
            assertThat(darkest).isLessThan(0.4f)
        }
        fun openMenu() {
            compose.onNodeWithText(context.getString(R.string.profile_gender_title)).performClick()
            compose.onNode(isPopup()).assertIsDisplayed()
        }
        fun option(id: Int) = compose.onNode(
            hasText(context.getString(id)) and hasAnyAncestor(isPopup())
        )
        fun select(id: Int, expected: ProfileGender) {
            option(id).performClick()
            compose.onNode(isPopup()).assertDoesNotExist()
            compose.onNodeWithText(context.getString(id)).assertIsDisplayed()
            assertThat(profile.gender).isEqualTo(expected)
        }

        compose.onNodeWithText(context.getString(R.string.gender_unspecified)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.gender_woman)).assertDoesNotExist()
        openMenu()
        option(R.string.gender_unspecified).assertIsSelected()
        if (theme == ThemePreference.LIGHT) {
            val layouts = mutableListOf<TextLayoutResult>()
            option(R.string.gender_unspecified)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertThat(layouts).isNotEmpty()
            assertThat(layouts.first().layoutInput.style.color.luminance()).isLessThan(0.4f)
        }
        listOf(R.string.gender_woman, R.string.gender_man, R.string.gender_non_binary).forEach {
            option(it).assertIsDisplayed()
        }
        Espresso.pressBack()
        compose.onNode(isPopup()).assertDoesNotExist()
        assertThat(changes).isEqualTo(0)
        assertThat(profile.gender).isEqualTo(ProfileGender.UNSPECIFIED)

        openMenu()
        select(R.string.gender_woman, ProfileGender.WOMAN)
        waitForImage(context.getString(R.string.illustration_content_description, exercise.name))
        saveScreenshot("illustration-female-$language.png")
        saveScreenshot("gender-dropdown-$language.png", compose.onNodeWithTag("gender-selector"))
        openMenu()
        option(R.string.gender_woman).assertIsSelected()
        select(R.string.gender_man, ProfileGender.MAN)
        waitForImage(context.getString(R.string.illustration_content_description, exercise.name))
        saveScreenshot("illustration-male-$language.png")
        openMenu()
        select(R.string.gender_non_binary, ProfileGender.NON_BINARY)
        openMenu()
        option(R.string.gender_non_binary).assertIsSelected()
        select(R.string.gender_unspecified, ProfileGender.UNSPECIFIED)
        assertThat(changes).isEqualTo(4)
    }

    private fun waitForImage(description: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(description).assertIsDisplayed()
        // Let the actual SVG decoder complete before saving visual review evidence.
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
    }

    private fun saveScreenshot(
        name: String,
        node: SemanticsNodeInteraction = compose.onNodeWithTag("exercise-artwork")
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.filesDir, name).outputStream().use {
            node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
