package wallcrawl.elopenmike.com.core.exercise.workoutguide

import android.content.res.AssetManager
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One licence or attribution document shipped alongside the bundled catalog. */
data class AttributionNotice(
    val title: String,
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
        DOCUMENTS.mapNotNull { (title, assetPath) ->
            readAsset(assetPath)?.let { body -> AttributionNotice(title = title, body = body) }
        }
    }

    private fun readAsset(assetPath: String): String? = try {
        assetManager.open(assetPath).bufferedReader().use { reader ->
            reader.readText().take(MAX_NOTICE_CHARACTERS).trim().takeIf(String::isNotEmpty)
        }
    } catch (error: IOException) {
        null
    }

    private companion object {
        const val MAX_NOTICE_CHARACTERS = 20_000

        val DOCUMENTS = listOf(
            "Attribution" to "workout-guide/ATTRIBUTION.md",
            "Bundled artwork" to "workout-guide/NOTICE.md",
            "Asset licence" to "workout-guide/LICENSE-ASSETS",
            "Upstream licence" to "workout-guide/LICENSE"
        )
    }
}
