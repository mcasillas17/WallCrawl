package wallcrawl.elopenmike.com.core.condition

import android.content.res.AssetManager
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.model.ConditionExerciseIdentity
import wallcrawl.elopenmike.com.core.model.ConditionSupportCatalog

class ConditionSupportLoadException(message: String, cause: Throwable) : IllegalStateException(message, cause)

/** Caches successful, fully bound loads only. Failure never means an empty support catalog. */
class ConditionSupportStore(
    private val assetManager: AssetManager,
    private val exerciseCatalog: ExerciseCatalog,
    private val artworkSha256ByExerciseId: suspend () -> Map<String, String>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    @Volatile private var cached: ConditionSupportCatalog? = null
    private val mutex = Mutex()

    suspend fun load(): ConditionSupportCatalog = cached ?: mutex.withLock {
        cached ?: withContext(ioDispatcher) {
            try {
                val exercises = exerciseCatalog.getAllExercises().first()
                require(exercises.map { it.id }.distinct().size == exercises.size) { "Duplicate exercise IDs prevent condition identity binding." }
                val artworkIdentities = artworkSha256ByExerciseId()
                val knownIds = exercises.map { it.id }.toSet()
                require(knownIds.containsAll(artworkIdentities.keys)) { "Artwork binding references an unknown exercise." }
                val parser = ConditionSupportParser(exercises.filter { it.id in artworkIdentities }.associate {
                    it.id to ConditionExerciseIdentity.from(it, requireNotNull(artworkIdentities[it.id]))
                })
                assetManager.open(ASSET_PATH).bufferedReader().use(parser::parse)
            } catch (error: IOException) {
                throw ConditionSupportLoadException("Unable to load released condition support.", error)
            } catch (error: IllegalArgumentException) {
                throw ConditionSupportLoadException("Released condition support or its exercise bindings are invalid.", error)
            }
        }.also { cached = it }
    }
    companion object { const val ASSET_PATH = "conditions/support.json" }
}
