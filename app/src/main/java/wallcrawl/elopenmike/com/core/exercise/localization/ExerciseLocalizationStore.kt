package wallcrawl.elopenmike.com.core.exercise.localization

import android.content.res.AssetManager
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface ExerciseLocalizationSource {
    suspend fun localization(): ExerciseLocalization

    /** The loaded overlay, without performing asset I/O. Null before the first load. */
    fun currentLocalization(): ExerciseLocalization?
}

/**
 * Loads and validates the bundled translation overlay once, off the main thread.
 *
 * A missing or invalid overlay is not fatal: every screen falls back to the catalog's own
 * English text, which is a degraded interface rather than a broken one. The catalog is a
 * different matter — it carries the exercises themselves — so it is still allowed to fail
 * loudly while this is not.
 */
class ExerciseLocalizationStore(
    private val assetManager: AssetManager,
    private val parser: ExerciseLocalizationParser = ExerciseLocalizationParser(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ExerciseLocalizationSource {

    @Volatile
    private var cached: ExerciseLocalization? = null
    private val loadMutex = Mutex()

    override suspend fun localization(): ExerciseLocalization =
        cached ?: loadMutex.withLock {
            cached ?: load().also { loaded -> cached = loaded }
        }

    override fun currentLocalization(): ExerciseLocalization? = cached

    private suspend fun load(): ExerciseLocalization = withContext(ioDispatcher) {
        try {
            assetManager.open(LOCALIZATION_ASSET_PATH).bufferedReader().use(parser::parse)
        } catch (error: IOException) {
            ExerciseLocalization.EMPTY
        } catch (error: ExerciseLocalizationFormatException) {
            ExerciseLocalization.EMPTY
        }
    }

    companion object {
        const val LOCALIZATION_ASSET_PATH = "localization/exercise-localization.json"
    }
}
