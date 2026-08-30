package wallcrawl.elopenmike.com.core.database.repository

import com.google.common.truth.Truth.assertThat
import wallcrawl.elopenmike.com.core.database.dao.UserProfileDao
import wallcrawl.elopenmike.com.core.database.entity.UserProfileEntity
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.CapabilityLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.MovementCapabilities
import wallcrawl.elopenmike.com.core.model.MovementCapabilityType
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.TrainingConstraint
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.convertWeight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertThrows

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
        assertThat(profile.goals).containsExactly(FitnessGoal.STRENGTH)
    }

    @Test
    fun updateGoals_persistsMultipleGoals() = runTest {
        val goals = setOf(FitnessGoal.STRENGTH, FitnessGoal.BUILD_MUSCLE, FitnessGoal.ATHLETIC_PERFORMANCE)
        repository.updateGoals(goals)
        val profile = repository.getUserProfile().first()
        assertThat(profile.goals).containsExactlyElementsIn(goals)
    }

    @Test
    fun updateThemePreference_persistsTheme() = runTest {
        repository.updateThemePreference(wallcrawl.elopenmike.com.core.model.ThemePreference.LIGHT)
        val profile = repository.getUserProfile().first()
        assertThat(profile.themePreference).isEqualTo(wallcrawl.elopenmike.com.core.model.ThemePreference.LIGHT)

        repository.updateThemePreference(wallcrawl.elopenmike.com.core.model.ThemePreference.DARK)
        val darkProfile = repository.getUserProfile().first()
        assertThat(darkProfile.themePreference).isEqualTo(wallcrawl.elopenmike.com.core.model.ThemePreference.DARK)
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

    @Test
    fun saveProfile_persistsOnboardingInputsInASingleRevisionUpdate() = runTest {
        val onboarded = UserProfile(
            name = "Alex",
            goals = setOf(FitnessGoal.STRENGTH),
            experienceLevel = ExperienceLevel.BEGINNER,
            daysPerWeek = 3,
            preferredDurationMinutes = 45,
            preferredUnit = WeightUnit.KG,
            availableEquipment = listOf(StandardEquipment.BODYWEIGHT, StandardEquipment.DUMBBELL),
            trainingConstraints = setOf(TrainingConstraint.SHOULDER_SENSITIVE),
            returningAfterBreakWeeks = 6,
            onboardingCompleted = true
        )

        repository.saveProfile(onboarded)

        val profile = repository.getUserProfile().first()
        assertThat(profile.onboardingCompleted).isTrue()
        assertThat(profile.name).isEqualTo("Alex")
        assertThat(profile.trainingConstraints).containsExactly(TrainingConstraint.SHOULDER_SENSITIVE)
        assertThat(profile.returningAfterBreakWeeks).isEqualTo(6)
        assertThat(profile.availableEquipment)
            .containsExactly(StandardEquipment.BODYWEIGHT, StandardEquipment.DUMBBELL)
        // One save must be one revision bump, not one write per onboarding field.
        assertThat(profile.revision).isEqualTo(0L)
    }

    @Test
    fun saveProfile_rejectsDaysPerWeekOutsideSupportedRange() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest { repository.saveProfile(UserProfile(daysPerWeek = 1)) }
        }
    }

    @Test
    fun saveProfile_rejectsDurationOutsideSupportedRange() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest { repository.saveProfile(UserProfile(preferredDurationMinutes = 10)) }
        }
    }

    @Test
    fun saveProfile_rejectsReturningAfterBreakWeeksOutsideSupportedRange() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest { repository.saveProfile(UserProfile(returningAfterBreakWeeks = 521)) }
        }
    }

    @Test
    fun saveProfile_rejectsEmptyGoals() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest { repository.saveProfile(UserProfile(goals = emptySet())) }
        }
    }

    @Test
    fun saveProfile_rejectsEmptyEquipment() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest { repository.saveProfile(UserProfile(availableEquipment = emptyList())) }
        }
    }

    @Test
    fun saveProfile_rejectsUnknownEquipmentNames() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest { repository.saveProfile(UserProfile(availableEquipment = listOf("Trampoline"))) }
        }
    }

    @Test
    fun saveProfile_rejectsNonFiniteOrNegativeConfirmedLoads() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                repository.saveProfile(
                    UserProfile(confirmedStartingLoads = mapOf("barbell-back-squat" to -5.0))
                )
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runTest {
                repository.saveProfile(
                    UserProfile(confirmedStartingLoads = mapOf("barbell-back-squat" to Double.NaN))
                )
            }
        }
    }

    @Test
    fun getProfile_dropsMalformedTrainingConstraintNamesRatherThanGuessing() = runTest {
        // A future/renamed constraint value, or corruption, must not silently resolve to
        // some other constraint: that would misrepresent what the user actually confirmed.
        fakeDao.seed(
            musclePrioritiesJson = "",
            trainingConstraintsJson = "SHOULDER_SENSITIVE|||NOT_A_REAL_CONSTRAINT"
        )

        val profile = repository.getUserProfile().first()

        assertThat(profile.trainingConstraints).containsExactly(TrainingConstraint.SHOULDER_SENSITIVE)
    }

    @Test
    fun getProfile_decodesFitnessGoalsJson_andFallsBackToPrimaryGoal() = runTest {
        // Test decoding encoded multiple goals
        fakeDao.seed(
            musclePrioritiesJson = "",
            fitnessGoalsJson = "STRENGTH|||ATHLETIC_PERFORMANCE"
        )
        val profileWithGoals = repository.getUserProfile().first()
        assertThat(profileWithGoals.goals).containsExactly(FitnessGoal.STRENGTH, FitnessGoal.ATHLETIC_PERFORMANCE)

        // Test fallback when fitnessGoalsJson is blank (legacy row)
        fakeDao.seed(
            musclePrioritiesJson = "",
            fitnessGoalsJson = ""
        )
        val profileLegacy = repository.getUserProfile().first()
        assertThat(profileLegacy.goals).containsExactly(FitnessGoal.BUILD_MUSCLE)
    }

    @Test
    fun getProfile_dropsMalformedConfirmedStartingLoadsRatherThanInventingAWeight() = runTest {
        // Inventing a starting weight for a corrupted row would be an unsafe guess;
        // the safe behavior is to drop just that entry, not the whole profile.
        fakeDao.seed(
            musclePrioritiesJson = "",
            confirmedStartingLoadsJson = "barbell-back-squat:135.0|||dumbbell-row:not-a-number"
        )

        val profile = repository.getUserProfile().first()

        assertThat(profile.confirmedStartingLoads).containsExactly("barbell-back-squat", 135.0)
    }

    @Test
    fun updateUnit_convertsConfirmedStartingLoadsRatherThanRelabelingThem() = runTest {
        // The confirmed-loads map is defined in preferredUnit. Switching the unit without
        // converting stored values would silently relabel a confirmed 135 lb baseline as
        // "135 kg" -- the exact unsafe-default problem Task 2 exists to remove.
        repository.saveProfile(
            UserProfile(
                preferredUnit = WeightUnit.LBS,
                confirmedStartingLoads = mapOf("barbell-bench-press" to 135.0),
                onboardingCompleted = true
            )
        )

        repository.updateUnit(WeightUnit.KG)

        val profile = repository.getUserProfile().first()
        assertThat(profile.preferredUnit).isEqualTo(WeightUnit.KG)
        assertThat(profile.confirmedStartingLoads["barbell-bench-press"])
            .isWithin(0.001).of(convertWeight(135.0, from = WeightUnit.LBS, to = WeightUnit.KG))
    }

    @Test
    fun updateUnit_withSameUnit_leavesConfirmedStartingLoadsUnchanged() = runTest {
        repository.saveProfile(
            UserProfile(
                preferredUnit = WeightUnit.LBS,
                confirmedStartingLoads = mapOf("barbell-bench-press" to 135.0),
                onboardingCompleted = true
            )
        )

        repository.updateUnit(WeightUnit.LBS)

        val profile = repository.getUserProfile().first()
        assertThat(profile.confirmedStartingLoads["barbell-bench-press"]).isEqualTo(135.0)
    }

    @Test
    fun saveProfile_roundTripsCapabilitiesAndPreservesUnrelatedFields() = runTest {
        val capabilities = MovementCapabilities.from(
            MovementCapabilityType.entries.associateWith { type ->
                when (type) {
                    MovementCapabilityType.IMPACT -> CapabilityLevel.AVOID
                    MovementCapabilityType.FLOOR_TRANSITION -> CapabilityLevel.LIMITED
                    else -> CapabilityLevel.COMFORTABLE
                }
            }
        )
        val original = UserProfile(
            name = "Alex",
            goals = setOf(FitnessGoal.STRENGTH, FitnessGoal.BUILD_MUSCLE),
            availableEquipment = listOf(StandardEquipment.BODYWEIGHT, StandardEquipment.DUMBBELL),
            trainingConstraints = setOf(TrainingConstraint.KNEE_SENSITIVE),
            confirmedStartingLoads = mapOf("goblet-squat" to 30.0),
            themePreference = wallcrawl.elopenmike.com.core.model.ThemePreference.DARK,
            onboardingCompleted = true,
            movementCapabilities = capabilities
        )

        repository.saveProfile(original)
        val reloaded = OfflineUserProfileRepository(fakeDao).getProfileOnce()

        assertThat(reloaded.movementCapabilities).isEqualTo(capabilities)
        assertThat(reloaded.name).isEqualTo("Alex")
        assertThat(reloaded.goals).containsExactlyElementsIn(original.goals)
        assertThat(reloaded.availableEquipment)
            .containsExactlyElementsIn(original.availableEquipment)
        assertThat(reloaded.trainingConstraints)
            .containsExactlyElementsIn(original.trainingConstraints)
        assertThat(reloaded.confirmedStartingLoads)
            .containsExactlyEntriesIn(original.confirmedStartingLoads)
        assertThat(reloaded.themePreference).isEqualTo(original.themePreference)
        assertThat(reloaded.onboardingCompleted).isTrue()
    }

    @Test
    fun saveProfile_updatesCapabilitiesInOneRepositoryOwnedRevision() = runTest {
        fakeDao.seed(musclePrioritiesJson = "", revision = 41L)
        val current = repository.getProfileOnce()

        repository.saveProfile(
            current.copy(
                movementCapabilities = MovementCapabilities.from(
                    mapOf(MovementCapabilityType.IMPACT to CapabilityLevel.LIMITED)
                )
            )
        )

        val reloaded = OfflineUserProfileRepository(fakeDao).getProfileOnce()
        assertThat(reloaded.revision).isEqualTo(42L)
        assertThat(reloaded.movementCapabilities[MovementCapabilityType.IMPACT])
            .isEqualTo(CapabilityLevel.LIMITED)
    }
}

class FakeUserProfileDao : UserProfileDao {
    private val profileState = MutableStateFlow<UserProfileEntity?>(null)

    /** Writes a profile row directly, standing in for one saved by an earlier app version. */
    fun seed(
        musclePrioritiesJson: String,
        trainingConstraintsJson: String = "",
        confirmedStartingLoadsJson: String = "",
        fitnessGoalsJson: String = "",
        revision: Long = 0L
    ) {
        profileState.value = UserProfileEntity(
            id = UserProfile.DEFAULT_PROFILE_ID,
            revision = revision,
            name = "Crawler",
            primaryGoal = FitnessGoal.BUILD_MUSCLE,
            experienceLevel = ExperienceLevel.INTERMEDIATE,
            preferredDurationMinutes = 50,
            daysPerWeek = 4,
            availableEquipmentJson = StandardEquipment.BODYWEIGHT,
            preferredUnit = WeightUnit.LBS,
            musclePrioritiesJson = musclePrioritiesJson,
            excludedExerciseIdsJson = "",
            trainingConstraintsJson = trainingConstraintsJson,
            confirmedStartingLoadsJson = confirmedStartingLoadsJson,
            fitnessGoalsJson = fitnessGoalsJson
        )
    }

    override fun observeProfile(id: String): Flow<UserProfileEntity?> = profileState

    override suspend fun getProfile(id: String): UserProfileEntity? = profileState.value

    override suspend fun insertOrUpdate(profile: UserProfileEntity) {
        profileState.value = profile
    }
}
