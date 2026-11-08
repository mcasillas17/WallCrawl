package wallcrawl.elopenmike.com.core.condition

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisual
import wallcrawl.elopenmike.com.core.exercise.visual.ExerciseVisualProvider
import wallcrawl.elopenmike.com.core.model.IllustrationVariant

class ConditionArtworkIdentityTest {
    private val id = "fictional-geometry"
    private val paths = (listOf("canonical", "male", "female")).flatMap { variant ->
        (1..3).map { "art/$variant/$id/frame-$it.svg" }
    }
    private val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512"><path fill="#fff" d="M10 10h10v10z"/></svg>""".toByteArray()
    private fun bytes() = paths.associateWith { svg.copyOf() }.toMutableMap()
    private fun identity(files: Map<String, ByteArray>, orderedPaths: List<String> = paths): String =
        ConditionArtworkIdentity(mapOf(id to orderedPaths)) { files[it] ?: throw IOException("Missing fictional art") }.sha256For(id)

    @Test fun unchangedIdentityIsIndependentOfPathOrderAndIncludesEveryVariantAndFallback() {
        val files = bytes()
        val original = identity(files)
        assertThat(identity(files, paths.reversed() + paths)).isEqualTo(original)
        for (path in listOf(paths.first(), paths.last())) {
            val changed = files.toMutableMap().apply { this[path] = String(svg).replace("10h10", "10h11").toByteArray() }
            assertThat(identity(changed)).isNotEqualTo(original)
        }
    }

    @Test fun sameBytesMovedToAnotherPathChangeIdentity() {
        val files = bytes()
        val moved = "art/female/$id/renamed.svg"
        files[moved] = svg
        assertThat(identity(files, paths.dropLast(1) + moved)).isNotEqualTo(identity(files))
    }

    @Test fun missingMalformedEmptyAndUnsafeRelevantArtFail() {
        val files = bytes()
        files.remove(paths.last())
        assertThrows(IOException::class.java) { identity(files) }
        for (bad in listOf(byteArrayOf(), "<svg".toByteArray(), "<html/>".toByteArray(), "<!DOCTYPE svg><svg/>".toByteArray())) {
            files[paths.last()] = bad
            assertThrows(IllegalArgumentException::class.java) { identity(files) }
        }
        for (bad in listOf("../escape.svg", "/absolute.svg", "art/../escape.svg", "art\\escape.svg")) {
            assertThrows(IllegalArgumentException::class.java) { identity(files, listOf(bad)) }
        }
        assertThrows(IllegalArgumentException::class.java) { identity(files, emptyList()) }
    }

    @Test fun unrelatedChangedOrMissingArtIsNeverRead() {
        val files = bytes()
        val reads = mutableListOf<String>()
        val resolver = ConditionArtworkIdentity(mapOf(id to paths, "unrelated" to listOf("missing.svg"))) {
            reads += it
            files.getValue(it)
        }
        val first = resolver.sha256For(id)
        files["missing.svg"] = "changed unrelated bytes".toByteArray()
        assertThat(resolver.sha256For(id)).isEqualTo(first)
        assertThat(reads).doesNotContain("missing.svg")
    }

    @Test fun preparationResolvesActualRenderableVariantsWithoutReadingFrameBytes() = runTest {
        val reads = mutableListOf<String>()
        val provider = object : ExerciseVisualProvider {
            override fun framesFor(exerciseId: String) = paths.take(3).map(::ExerciseVisual)
            override suspend fun framesFor(exerciseId: String, variant: IllustrationVariant) =
                paths.filter { it.startsWith("art/${variant.assetDirectory}/") }.map(::ExerciseVisual)
        }
        val resolver = ConditionArtworkIdentity.prepare(setOf(id), provider) { reads += it; svg }
        assertThat(reads).isEmpty()
        assertThat(resolver.sha256For(id)).isEqualTo(identity(bytes()))
        assertThat(reads).containsExactlyElementsIn(paths)
    }
}
