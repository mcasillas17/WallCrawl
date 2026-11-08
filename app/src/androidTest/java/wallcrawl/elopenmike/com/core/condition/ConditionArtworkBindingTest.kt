package wallcrawl.elopenmike.com.core.condition

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.DefaultAppContainer
import wallcrawl.elopenmike.com.core.exercise.ExerciseCatalog
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExerciseType

/** Only fictional support and artificial geometric SVGs; no clinical or movement approval. */
@RunWith(AndroidJUnit4::class)
class ConditionArtworkBindingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val assets = instrumentation.context.assets
    private val id = "fictional-exercise"
    private val exercise = Exercise(id, name = "FICTIONAL TEST EXERCISE", primaryMuscles = listOf("Core"), type = ExerciseType.WEIGHT_REPS)
    private val paths = listOf("canonical", "male", "female").flatMap { variant ->
        (1..3).map { "condition-art/artificial/$variant/frame-$it.svg" }
    }
    private fun catalog(exercises: List<Exercise>) = object : ExerciseCatalog {
        override fun getAllExercises() = flowOf(exercises)
        override suspend fun getExerciseById(id: String) = exercises.find { it.id == id }
        override fun searchExercises(query: String, muscle: String?, equipment: String?) = flowOf(exercises)
        override suspend fun getMuscleGroups() = emptyList<String>()
        override suspend fun getEquipmentTypes() = emptyList<String>()
    }
    private fun files() = paths.associateWith { path -> assets.open(path).use { it.readBytes() } }.toMutableMap()
    private fun store(files: Map<String, ByteArray>, framePaths: List<String> = paths, actualExercise: Exercise = exercise, read: (String) -> Unit = {}) =
        ConditionSupportStore(assets, catalog(listOf(actualExercise, exercise.copy(id = "unrelated"))), prepareArtworkIdentity = {
            ConditionArtworkIdentity(mapOf(id to framePaths, "unrelated" to listOf("missing-unrelated.svg"))) { path ->
                check(Looper.myLooper() != Looper.getMainLooper()) { "Asset hashing ran on main thread" }
                read(path)
                files[path] ?: throw IOException("Missing artificial geometry")
            }
        })

    @Test fun unchangedFictionalReleaseBindsActualAssetsAndCachesSuccessfulLoad() = runBlocking<Unit> {
        val reads = mutableListOf<String>()
        val store = store(files(), read = { reads += it })
        val first = store.load()
        assertThat(first.records).hasSize(1)
        assertThat(store.load()).isSameInstanceAs(first)
        assertThat(reads).containsExactlyElementsIn(paths)
    }

    @Test fun actualFemaleAndCanonicalMutationsInvalidateExistingRelease() {
        for (path in listOf(paths.first(), paths.last())) {
            val files = files()
            files[path] = String(files.getValue(path)).replace("10h10", "10h11").toByteArray()
            assertThrows(ConditionSupportLoadException::class.java) { runBlocking { store(files).load() } }
        }
    }

    @Test fun sameBytesAtNewPathInvalidateExistingRelease() {
        val files = files()
        val moved = "condition-art/artificial/female/renamed.svg"
        files[moved] = files.getValue(paths.last())
        assertThrows(ConditionSupportLoadException::class.java) { runBlocking { store(files, paths.dropLast(1) + moved).load() } }
    }

    @Test fun missingOrMalformedRelevantArtRejectsReleaseAndFailureIsNotCached() = runBlocking<Unit> {
        val files = files()
        val original = files.remove(paths.last())!!
        val store = store(files)
        assertThrows(ConditionSupportLoadException::class.java) { runBlocking { store.load() } }
        files[paths.last()] = "<svg".toByteArray()
        assertThrows(ConditionSupportLoadException::class.java) { runBlocking { store.load() } }
        files[paths.last()] = original
        assertThat(store.load().records).hasSize(1)
    }

    @Test fun unrelatedMissingOrChangedArtDoesNotRejectBoundSupport() = runBlocking<Unit> {
        val files = files()
        assertThat(store(files).load().records).hasSize(1)
        files["missing-unrelated.svg"] = "malformed unrelated bytes".toByteArray()
        assertThat(store(files).load().records).hasSize(1)
    }

    @Test fun changedContentOrGuidanceRejectsExistingRelease() {
        for (changed in listOf(exercise.copy(name = "CHANGED FICTIONAL NAME"), exercise.copy(executionGuidance = "FICTIONAL CHANGED GUIDANCE"))) {
            assertThrows(ConditionSupportLoadException::class.java) { runBlocking { store(files(), actualExercise = changed).load() } }
        }
    }

    @Test fun emptyProductionSupportReadsNoFrameBytes() = runBlocking<Unit> {
        val container = DefaultAppContainer(instrumentation.targetContext)
        var reads = 0
        val store = ConditionSupportStore(instrumentation.targetContext.assets, container.exerciseCatalog, prepareArtworkIdentity = { ids ->
            ConditionArtworkIdentity.prepare(ids, container.exerciseVisualProvider) { reads++; error("Empty support must not read art") }
        })
        assertThat(store.load().records).isEmpty()
        assertThat(reads).isEqualTo(0)
    }
}
