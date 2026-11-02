package wallcrawl.elopenmike.com.core.conditions

import android.content.res.AssetManager
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Loads and validates the bundled condition overlay once, off the main thread.
 *
 * Unlike the translation overlay there is no harmless fallback: silently planning without a
 * restriction is worse than not planning. A failed load is [ConditionGuidanceResult.Unavailable]
 * and is remembered, so a broken asset is not re-read on every plan.
 */
class ConditionGuidanceStore(
    private val assetManager: AssetManager,
    private val parser: ConditionGuidanceParser = ConditionGuidanceParser(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ConditionGuidanceSource {

    @Volatile
    private var cached: ConditionGuidanceResult? = null
    private val loadMutex = Mutex()

    override suspend fun guidance(): ConditionGuidanceResult =
        cached ?: loadMutex.withLock {
            cached ?: load().also { loaded -> cached = loaded }
        }

    private suspend fun load(): ConditionGuidanceResult = withContext(ioDispatcher) {
        try {
            ConditionGuidanceResult.Loaded(
                assetManager.open(GUIDANCE_ASSET_PATH).bufferedReader().use(parser::parse)
            )
        } catch (error: IOException) {
            ConditionGuidanceResult.Unavailable
        } catch (error: ConditionGuidanceFormatException) {
            ConditionGuidanceResult.Unavailable
        }
    }

    companion object {
        const val GUIDANCE_ASSET_PATH = "conditions/condition-guidance.json"
    }
}
