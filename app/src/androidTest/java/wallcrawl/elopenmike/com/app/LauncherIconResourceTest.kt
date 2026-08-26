package wallcrawl.elopenmike.com.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.R

@RunWith(AndroidJUnit4::class)
class LauncherIconResourceTest {

    @Test
    fun adaptiveForeground_fillsTheMaskInsteadOfEmbeddingAnotherIconTile() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val launcherIcon = context.getDrawable(R.mipmap.ic_launcher)
        assertThat(launcherIcon).isInstanceOf(AdaptiveIconDrawable::class.java)

        val foreground = (launcherIcon as AdaptiveIconDrawable).foreground
        val size = 432
        val rendered = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        foreground.setBounds(0, 0, size, size)
        foreground.draw(Canvas(rendered))

        val opaqueBounds = rendered.opaquePixelBounds()
        assertThat(opaqueBounds.width).isAtLeast((size * 0.9f).toInt())
        assertThat(opaqueBounds.height).isAtLeast((size * 0.9f).toInt())
    }

    @Test
    fun monochromeIcon_hasTransparentBackgroundForThemedIcons() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val launcherIcon = context.getDrawable(R.mipmap.ic_launcher)
        assertThat(launcherIcon).isInstanceOf(AdaptiveIconDrawable::class.java)

        val monochrome = (launcherIcon as AdaptiveIconDrawable).monochrome
        assertThat(monochrome).isNotNull()

        val size = 432
        val rendered = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        monochrome!!.setBounds(0, 0, size, size)
        monochrome.draw(Canvas(rendered))

        // Ensure corners and background areas are transparent (no background plate)
        assertThat((rendered.getPixel(0, 0) ushr 24)).isEqualTo(0)
        assertThat((rendered.getPixel(size - 1, 0) ushr 24)).isEqualTo(0)
        assertThat((rendered.getPixel(0, size - 1) ushr 24)).isEqualTo(0)
        assertThat((rendered.getPixel(size - 1, size - 1) ushr 24)).isEqualTo(0)

        // Ensure the glyph itself exists and has valid bounds
        val opaqueBounds = rendered.opaquePixelBounds()
        assertThat(opaqueBounds.width).isGreaterThan(0)
        assertThat(opaqueBounds.height).isGreaterThan(0)
    }
}

private fun Bitmap.opaquePixelBounds(): PixelBounds {
    var minX = width
    var minY = height
    var maxX = -1
    var maxY = -1

    for (y in 0 until height) {
        for (x in 0 until width) {
            if ((getPixel(x, y) ushr 24) != 0) {
                minX = minOf(minX, x)
                minY = minOf(minY, y)
                maxX = maxOf(maxX, x)
                maxY = maxOf(maxY, y)
            }
        }
    }

    return if (maxX < minX || maxY < minY) {
        PixelBounds(width = 0, height = 0)
    } else {
        PixelBounds(width = maxX - minX + 1, height = maxY - minY + 1)
    }
}

private data class PixelBounds(
    val width: Int,
    val height: Int
)
