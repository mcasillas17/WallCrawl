package wallcrawl.elopenmike.com.core.exercise.workoutguide

import android.content.res.AssetManager
import androidx.annotation.StringRes
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wallcrawl.elopenmike.com.R

/**
 * One license or attribution document shipped alongside the bundled catalog.
 *
 * [titleRes] names the section for the reader; [body] is the document itself and is never
 * translated. These are the licences the bundled artwork is distributed under, so the
 * authoritative wording is the wording that was granted, and a translation of it would not
 * be the licence.
 */
data class AttributionNotice(
    @StringRes val titleRes: Int,
    val body: String
)

interface AttributionNoticeSource {
    /**
     * Returns the bundled attribution documents in display order.
     * A document that cannot be read is omitted rather than failing the whole screen,
     * so a single unreadable file never hides the remaining credits.
     */
    suspend fun notices(): List<AttributionNotice>
}

/** Reads the attribution documents bundled next to the catalog in Android assets. */
class AssetAttributionNoticeReader(
    private val assetManager: AssetManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AttributionNoticeSource {

    override suspend fun notices(): List<AttributionNotice> = withContext(ioDispatcher) {
        DOCUMENTS.mapNotNull { (titleRes, assetPath) ->
            readAsset(assetPath)?.let { body ->
                AttributionNotice(titleRes = titleRes, body = body)
            }
        }
    }

    private fun readAsset(assetPath: String): String? = try {
        assetManager.open(assetPath).bufferedReader().use { reader ->
            // Bounded while reading rather than after: truncating a string already in memory
            // would not protect against an oversized asset.
            val buffer = CharArray(MAX_NOTICE_CHARACTERS)
            var filled = 0
            while (filled < buffer.size) {
                val read = reader.read(buffer, filled, buffer.size - filled)
                if (read < 0) break
                filled += read
            }
            String(buffer, 0, filled).trim().takeIf(String::isNotEmpty)
        }
    } catch (error: IOException) {
        null
    }

    private companion object {
        const val MAX_NOTICE_CHARACTERS = 20_000

        val DOCUMENTS = listOf(
            R.string.credits_notice_attribution to "workout-guide/ATTRIBUTION.md",
            R.string.credits_notice_artwork to "workout-guide/NOTICE.md",
            R.string.credits_notice_asset_license to "workout-guide/LICENSE-ASSETS",
            R.string.credits_notice_upstream_license to "workout-guide/LICENSE"
        )
    }
}
