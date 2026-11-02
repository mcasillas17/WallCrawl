package wallcrawl.elopenmike.com.core.exercise.visual

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import coil3.ImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import wallcrawl.elopenmike.com.core.exercise.workoutguide.WorkoutGuideCatalogStore
import wallcrawl.elopenmike.com.core.model.IllustrationVariant

/**
 * Loads the owned exercises' frames the way the app does: catalog snapshot, visual provider and
 * Coil's SVG decoder on `file:///android_asset/` URIs. Each exercise's three frames are also
 * drawn side by side on the card colour into the app's external files directory for review.
 */
@RunWith(AndroidJUnit4::class)
class OwnedIllustrationFramesTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun everyOwnedExerciseResolvesThreeFramesForBothVariantsAndRendersThem() = runBlocking {
        val store = WorkoutGuideCatalogStore(context.assets)
        store.snapshot()
        val provider = WorkoutGuideVisualProvider(
            store,
            IllustrationCatalog { context.assets.open("exercise-illustrations/index.json").bufferedReader().readText() }
        )
        val loader = ImageLoader(context)
        val output = File(context.getExternalFilesDir(null), "task-9-art").apply { mkdirs() }

        OWNED_IDS.forEach { id ->
            IllustrationVariant.entries.forEach { variant ->
                // No gender variant exists for owned art, so the whole original sequence is used.
                val frames = provider.framesFor(id, variant)
                assertThat(frames.map { it.assetPath })
                    .containsExactly(
                        "workout-guide/assets/$id/frame-1.svg",
                        "workout-guide/assets/$id/frame-2.svg",
                        "workout-guide/assets/$id/frame-3.svg"
                    ).inOrder()
                frames.forEach {
                    assertThat(it.attribution?.license).isEqualTo("Original work by the WallCrawl project")
                }
            }
            val sheet = Bitmap.createBitmap(3 * FRAME_PX, FRAME_PX, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet).apply { drawColor(Color.rgb(26, 26, 30)) }
            provider.framesFor(id).forEachIndexed { index, frame ->
                val result = loader.execute(
                    ImageRequest.Builder(context)
                        .data("file:///android_asset/${frame.assetPath}")
                        .size(Size(FRAME_PX, FRAME_PX))
                        .allowHardware(false)
                        .build()
                )
                assertWithMessage("$id frame ${index + 1}").that(result).isInstanceOf(SuccessResult::class.java)
                val drawable = (result as SuccessResult).image.asDrawable(context.resources)
                drawable.setBounds(index * FRAME_PX, 0, (index + 1) * FRAME_PX, FRAME_PX)
                drawable.draw(canvas)
            }
            val lit = IntArray(sheet.width * sheet.height).also {
                sheet.getPixels(it, 0, sheet.width, 0, 0, sheet.width, sheet.height)
            }.count { Color.red(it) > 200 && Color.green(it) > 200 }
            assertWithMessage(id).that(lit).isGreaterThan(1_500)
            File(output, "$id.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private companion object {
        const val FRAME_PX = 384
        val OWNED_IDS = listOf(
            "figure-four-stretch", "lying-piriformis-stretch", "side-lying-isometric-hip-abduction-hold",
            "standing-isometric-gluteal-contraction", "wrist-extensor-stretch", "tendon-gliding-sequence",
            "straight-leg-raise"
        )
    }
}
