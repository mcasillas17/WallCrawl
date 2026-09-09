package wallcrawl.elopenmike.com.core.exercise.visual

import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONException
import org.json.JSONObject
import wallcrawl.elopenmike.com.core.model.IllustrationVariant

/** A small index of complete, ordered variant sequences; raster masters stay outside the app. */
class IllustrationCatalog(private val readManifest: suspend () -> String) {
    private val mutex = Mutex()
    private var sequences: Map<String, Map<IllustrationVariant, List<String>>>? = null

    suspend fun framesFor(exerciseId: String, variant: IllustrationVariant): List<String> =
        mutex.withLock {
            val index = sequences ?: load().also { sequences = it }
            index[exerciseId]?.get(variant).orEmpty()
        }

    private suspend fun load(): Map<String, Map<IllustrationVariant, List<String>>> = try {
        val root = JSONObject(readManifest())
        if (root.getInt("schemaVersion") != 1) emptyMap() else {
            val exercises = root.getJSONObject("exercises")
            exercises.keys().asSequence().filter { it.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*")) }
                .associateWith { id ->
                    val entry = exercises.getJSONObject(id)
                    IllustrationVariant.entries.associateWith { variant ->
                        val array = entry.optJSONArray(variant.assetDirectory)
                        val paths = array?.let { (0 until it.length()).map(it::getString) }.orEmpty()
                        val expected = (1..3).map {
                            "exercise-illustrations/${variant.assetDirectory}/$id/frame-$it.svg"
                        }
                        paths.takeIf { it == expected }.orEmpty()
                    }
                }
        }
    } catch (error: IOException) {
        emptyMap()
    } catch (error: JSONException) {
        emptyMap()
    }
}
