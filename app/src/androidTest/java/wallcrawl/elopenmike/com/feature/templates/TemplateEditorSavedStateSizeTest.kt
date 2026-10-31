package wallcrawl.elopenmike.com.feature.templates

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Parcel
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.database.WallCrawlDatabase
import wallcrawl.elopenmike.com.core.database.repository.OfflineUserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutTemplateRepository
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate

/**
 * The editor's recovery payload at every limit the domain allows, written through a real
 * [Parcel] the way `onSaveInstanceState` writes it, and read back into a new editor.
 *
 * Fifty rows of the same exercise, every numeric field and both remembered pre-switch values
 * at the eleven-character field cap (one past what the parser accepts), every
 * note at its length cap in supplementary-plane characters (two UTF-16 units each, the
 * worst case for a `String` in a parcel), an exercise id at the archive's id limit (longer
 * than any the catalog parser accepts, so an over-estimate; a recovered draft can keep an id
 * a later catalog dropped, as an unresolved row), UUID-length row ids, a loaded rest
 * provenance on every row, and the open sheet holding one more full encoded row, the
 * worst-case record again under row 0's identity, as an open sheet edits an existing row.
 */
@RunWith(AndroidJUnit4::class)
class TemplateEditorSavedStateSizeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: WallCrawlDatabase

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, WallCrawlDatabase::class.java).build()
    }

    @After fun tearDown() = database.close()

    private fun editor(handle: SavedStateHandle) = TemplateEditorViewModel(
        templateId = null,
        templateRepository = OfflineWorkoutTemplateRepository(
            database.workoutTemplateDao(),
            InMemoryExerciseCatalog()
        ),
        userProfileRepository = OfflineUserProfileRepository(database.userProfileDao()),
        exerciseCatalog = InMemoryExerciseCatalog(),
        savedStateHandle = handle
    )

    @SuppressLint("RestrictedApi")
    @Test fun anOverEstimateOfTheLargestDraftParcelsWellUnderBudgetAndRestoresUnchanged() = runBlocking {
        OfflineUserProfileRepository(database.userProfileDao())
            .saveProfile(UserProfile(preferredUnit = WeightUnit.KG))
        val handle = SavedStateHandle()
        val viewModel = withContext(Dispatchers.Main) { editor(handle) }
        viewModel.uiState.first { !it.isLoading }
        val squat = InMemoryExerciseCatalog.SAMPLE_EXERCISES.single { it.id == "barbell-back-squat" }
        val emoji = "💪" // one code point, two UTF-16 units
        fun maxText(length: Int) = emoji.repeat(length / 2)

        withContext(Dispatchers.Main) {
            viewModel.updateName(maxText(WorkoutTemplate.MAX_NAME_LENGTH))
            viewModel.updateNotes(maxText(WorkoutTemplate.MAX_NOTES_LENGTH))
            repeat(WorkoutTemplate.MAX_EXERCISES) { viewModel.addExercise(squat) }
        }
        assertThat(viewModel.uiState.value.selectedExercises).hasSize(WorkoutTemplate.MAX_EXERCISES)
        // The editor's own rows hold seeded values. The worst case is raw text at every input
        // cap, which a sheet can hold but Apply would refuse, so it is written in the shape
        // the editor itself encodes.
        val worstRow = TemplateExerciseDraft(
            exerciseId = "x".repeat(wallcrawl.elopenmike.com.core.backup.LocalDataArchiveLimits.MAX_ID_LENGTH),
            exerciseType = squat.type,
            sets = "12345678901", repMin = "12345678901", repMax = "12345678901",
            load = "12345678901", assistance = "12345678901", durationSeconds = "12345678901",
            distanceMeters = "12345678901", restSeconds = "12345678901",
            effortMinRir = "12345678901", effortMaxRir = "12345678901",
            notes = maxText(WorkoutTemplate.MAX_EXERCISE_NOTES_LENGTH),
            restWasEdited = true,
            originalRestClass = wallcrawl.elopenmike.com.core.model.RestClass.MODERATE,
            originalRestTargetSource = wallcrawl.elopenmike.com.core.model.RestTargetSource.USER_PREFERENCE,
            loadBeforeConversion = "12345678901",
            assistanceBeforeConversion = "12345678901",
            originalRestSeconds = "12345678901"
        )
        handle["templateEditor.rows"] = ArrayList(
            List(WorkoutTemplate.MAX_EXERCISES) { worstRow.copy(rowId = rowId(it)).encode() }
        )
        handle["templateEditor.editingRow"] = worstRow.copy(rowId = rowId(0)).encode()

        val bundle: Bundle = handle.savedStateProvider().saveState()
        val parcel = Parcel.obtain()
        val bytes = try {
            parcel.writeBundle(bundle)
            parcel.marshall()
        } finally {
            parcel.recycle()
        }
        // Recorded for the design document's size claim.
        println("TemplateEditorSavedStateSizeTest: ${bytes.size} bytes at supported limits")
        assertThat(bytes.size).isLessThan(BUDGET_BYTES)
        // custom-workouts.md and ROADMAP.md state this figure; a larger payload must update them.
        assertThat(bytes.size).isAtMost(DOCUMENTED_BYTES)

        val restoredBundle = Parcel.obtain().let { reading ->
            try {
                reading.unmarshall(bytes, 0, bytes.size)
                reading.setDataPosition(0)
                reading.readBundle(javaClass.classLoader)!!
            } finally {
                reading.recycle()
            }
        }
        val restored = withContext(Dispatchers.Main) {
            editor(SavedStateHandle.createHandle(restoredBundle, null))
        }
        val state = restored.uiState.first { !it.isLoading }
        assertThat(state.name).isEqualTo(maxText(WorkoutTemplate.MAX_NAME_LENGTH))
        assertThat(state.notes).isEqualTo(maxText(WorkoutTemplate.MAX_NOTES_LENGTH))
        assertThat(state.selectedExercises).hasSize(WorkoutTemplate.MAX_EXERCISES)
        assertThat(state.selectedExercises.map { it.rowId })
            .containsExactlyElementsIn(List(WorkoutTemplate.MAX_EXERCISES) { rowId(it) }).inOrder()
        assertThat(state.selectedExercises.all { it.copy(rowId = "") == worstRow.copy(rowId = "") })
            .isTrue()
        assertThat(state.weightUnit).isEqualTo(WeightUnit.KG)
        assertThat(state.isDraftIncomplete).isFalse()
        // Kept and flagged, never dropped or substituted.
        assertThat(state.unresolvedRowIds)
            .containsExactlyElementsIn(List(WorkoutTemplate.MAX_EXERCISES) { rowId(it) }).inOrder()
    }

    /** As long as the editor's own `UUID.randomUUID()` row ids. */
    private fun rowId(index: Int) = "%08d-0000-0000-0000-000000000000".format(index)

    private companion object {

        /** A quarter of the 1 MiB binder transaction buffer the whole task's state shares. */
        const val BUDGET_BYTES = 256 * 1024
        const val DOCUMENTED_BYTES = 152_112
    }
}
