package wallcrawl.elopenmike.com.core.database.repository

import com.google.common.truth.Truth.assertThat
import wallcrawl.elopenmike.com.core.database.dao.UserProfileDao
import wallcrawl.elopenmike.com.core.database.entity.UserProfileEntity
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class UserProfileRepositoryTest {

    private lateinit var fakeDao: FakeUserProfileDao
    private lateinit var repository: OfflineUserProfileRepository

    @Before
    fun setup() {
        fakeDao = FakeUserProfileDao()
        repository = OfflineUserProfileRepository(fakeDao)
    }

    @Test
    fun getProfile_whenEmpty_returnsDefaultProfile() = runTest {
        val profile = repository.getUserProfile().first()
        assertThat(profile.name).isEqualTo("Crawler")
        assertThat(profile.primaryGoal).isEqualTo(FitnessGoal.BUILD_MUSCLE)
    }

    @Test
    fun updatePrimaryGoal_persistsNewGoal() = runTest {
        repository.updatePrimaryGoal(FitnessGoal.STRENGTH)
        val profile = repository.getUserProfile().first()
        assertThat(profile.primaryGoal).isEqualTo(FitnessGoal.STRENGTH)
    }

    @Test
    fun updateEquipment_persistsEquipmentList() = runTest {
        val customEquipment = listOf(StandardEquipment.DUMBBELL, StandardEquipment.BENCH)
        repository.updateEquipment(customEquipment)
        val profile = repository.getUserProfile().first()
        assertThat(profile.availableEquipment).containsExactlyElementsIn(customEquipment)
    }

    @Test
    fun updateMusclePriorities_persistsMap() = runTest {
        val priorities = mapOf(
            StandardMuscles.CHEST to PriorityLevel.HIGH,
            StandardMuscles.BACK to PriorityLevel.HIGH,
            StandardMuscles.LEGS() to PriorityLevel.LOW
        )
        repository.updateMusclePriorities(priorities)
        val profile = repository.getUserProfile().first()
        assertThat(profile.musclePriorities[StandardMuscles.CHEST]).isEqualTo(PriorityLevel.HIGH)
        assertThat(profile.musclePriorities[StandardMuscles.LEGS()]).isEqualTo(PriorityLevel.LOW)
    }

    @Test
    fun getProfile_migratesRetiredMuscleNamesOntoTheCanonicalVocabulary() = runTest {
        // A profile saved before the vocabulary was unified. "Abs" is now part of "Core",
        // and the stronger of the two saved priorities has to survive the merge.
        fakeDao.seed(
            musclePrioritiesJson = "Abs:HIGH|||Core:NORMAL|||Quadriceps:LOW"
        )

        val profile = repository.getUserProfile().first()

        assertThat(profile.musclePriorities.keys).doesNotContain("Abs")
        assertThat(profile.musclePriorities[StandardMuscles.CORE]).isEqualTo(PriorityLevel.HIGH)
        assertThat(profile.musclePriorities[StandardMuscles.QUADS]).isEqualTo(PriorityLevel.LOW)
    }

    private fun StandardMuscles.LEGS() = StandardMuscles.QUADS
}

class FakeUserProfileDao : UserProfileDao {
    private val profileState = MutableStateFlow<UserProfileEntity?>(null)

    /** Writes a profile row directly, standing in for one saved by an earlier app version. */
    fun seed(musclePrioritiesJson: String) {
        profileState.value = UserProfileEntity(
            id = UserProfile.DEFAULT_PROFILE_ID,
            name = "Crawler",
            primaryGoal = FitnessGoal.BUILD_MUSCLE,
            experienceLevel = ExperienceLevel.INTERMEDIATE,
            preferredDurationMinutes = 50,
            daysPerWeek = 4,
            availableEquipmentJson = StandardEquipment.BODYWEIGHT,
            preferredUnit = WeightUnit.LBS,
            musclePrioritiesJson = musclePrioritiesJson,
            excludedExerciseIdsJson = ""
        )
    }

    override fun observeProfile(id: String): Flow<UserProfileEntity?> = profileState

    override suspend fun getProfile(id: String): UserProfileEntity? = profileState.value

    override suspend fun insertOrUpdate(profile: UserProfileEntity) {
        profileState.value = profile
    }
}
