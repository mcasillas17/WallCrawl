package wallcrawl.elopenmike.com.core.condition

import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.security.MessageDigest
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.SAXException
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisualProvider
import wallcrawl.elopenmike.com.core.model.IllustrationVariant

/** Path identity plus exact bytes of every frame that any demonstrator preference can render. */
class ConditionArtworkIdentity(
    pathsByExerciseId: Map<String, List<String>>,
    private val readAsset: (String) -> ByteArray
) {
    private val paths = pathsByExerciseId.mapValues { (_, value) -> value.toList() }

    fun sha256For(exerciseId: String): String {
        val frames = paths[exerciseId].orEmpty().distinct().sorted()
        require(frames.isNotEmpty()) { "Missing renderable artwork for $exerciseId." }
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("wallcrawl-artwork-v1\n".toByteArray(Charsets.UTF_8))
        for (path in frames) {
            require(path.length <= 240 && path.split('/').all { it.matches(Regex("[A-Za-z0-9_-][A-Za-z0-9_.-]*")) && it != "." && it != ".." } && path.endsWith(".svg")) { "Unsafe artwork asset path." }
            val data = readAsset(path)
            validateSvg(data)
            val name = path.toByteArray(Charsets.UTF_8)
            digest.update("${name.size}:".toByteArray(Charsets.UTF_8))
            digest.update(name)
            digest.update(MessageDigest.getInstance("SHA-256").digest(data))
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun validateSvg(data: ByteArray) {
        require(data.isNotEmpty() && data.size <= 4 * 1024 * 1024) { "Empty or oversized artwork." }
        // No DTD/entity expansion or external reads while checking packaged geometry.
        val text = try {
            Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(data)).toString().removePrefix("\uFEFF")
        } catch (error: CharacterCodingException) {
            throw IllegalArgumentException("Artwork must be UTF-8 SVG.", error)
        }
        require(!text.contains("<!")) { "Artwork declarations are forbidden." }
        try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isExpandEntityReferences = false
            }
            val builder = factory.newDocumentBuilder().apply {
                setErrorHandler(DefaultHandler())
                setEntityResolver { _, _ -> throw SAXException("External artwork references are forbidden.") }
            }
            val root = builder.parse(InputSource(StringReader(text))).documentElement
            require(root.localName == "svg" && root.namespaceURI == "http://www.w3.org/2000/svg") { "Artwork is not SVG." }
            val nodes = root.getElementsByTagNameNS("*", "*")
            require((0 until nodes.length).all { nodes.item(it).localName in setOf("g", "path") }) { "Artwork must be path geometry." }
            val paths = root.getElementsByTagNameNS("http://www.w3.org/2000/svg", "path")
            require(paths.length > 0 && (0 until paths.length).all { paths.item(it).attributes.getNamedItem("d")?.nodeValue?.isNotBlank() == true }) { "Artwork has no path geometry." }
        } catch (error: SAXException) {
            throw IllegalArgumentException("Malformed artwork SVG.", error)
        }
    }

    companion object {
        /** Suspension resolves paths only; frame reads happen lazily in the parser's IO context. */
        suspend fun prepare(
            exerciseIds: Set<String>,
            visuals: ExerciseVisualProvider,
            readAsset: (String) -> ByteArray
        ): ConditionArtworkIdentity = ConditionArtworkIdentity(exerciseIds.associateWith { id ->
            val canonical = visuals.framesFor(id)
            val variants = IllustrationVariant.entries.flatMap { visuals.framesFor(id, it) }
            (canonical + variants).map { it.assetPath }
        }, readAsset)
    }
}
