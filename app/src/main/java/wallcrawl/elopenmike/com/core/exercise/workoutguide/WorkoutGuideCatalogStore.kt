package wallcrawl.elopenmike.com.core.exercise.workoutguide

import android.content.res.AssetManager
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class WorkoutGuideCatalogLoadException(
    message: String,
    cause: Throwable
) : IllegalStateException(message, cause)

/** Loads and validates the catalog once, off the main thread, then shares the snapshot. */
class WorkoutGuideCatalogStore(
    private val assetManager: AssetManager,
    private val parser: WorkoutGuideCatalogParser = WorkoutGuideCatalogParser(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : WorkoutGuideCatalogSource {

    @Volatile
    private var cachedSnapshot: WorkoutGuideCatalogSnapshot? = null
    private val loadMutex = Mutex()

    override suspend fun snapshot(): WorkoutGuideCatalogSnapshot =
        cachedSnapshot ?: loadMutex.withLock {
            cachedSnapshot ?: loadSnapshot().also { loaded -> cachedSnapshot = loaded }
        }

    override fun currentSnapshot(): WorkoutGuideCatalogSnapshot? = cachedSnapshot

    private suspend fun loadSnapshot(): WorkoutGuideCatalogSnapshot = withContext(ioDispatcher) {
        try {
            assetManager.open(CATALOG_ASSET_PATH).bufferedReader().use(parser::parse)
        } catch (error: WorkoutGuideCatalogLoadException) {
            throw error
        } catch (error: IOException) {
            throw WorkoutGuideCatalogLoadException(
                "Unable to load the bundled Workout Guide catalog.",
                error
            )
        } catch (error: WorkoutGuideCatalogFormatException) {
            throw WorkoutGuideCatalogLoadException(
                "The bundled Workout Guide catalog is invalid.",
                error
            )
        }
    }

    companion object {
        const val CATALOG_ASSET_PATH = "workout-guide/catalog.json"
    }
}
