package wallcrawl.elopenmike.com.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.database.entity.UserProfileEntity
import wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutRepository
import wallcrawl.elopenmike.com.core.database.repository.OfflineWorkoutTemplateRepository
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.EffortTarget
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.ExperienceLevel
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.RestClass
import wallcrawl.elopenmike.com.core.model.RestTargetSource
import wallcrawl.elopenmike.com.core.model.SetPerformanceInput
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutOrigin
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate

@RunWith(AndroidJUnit4::class)
class WorkoutTemplateSessionTest {

    private lateinit var database: WallCrawlDatabase
    private lateinit var workoutRepository: OfflineWorkoutRepository
    private lateinit var templateRepository: OfflineWorkoutTemplateRepository

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WallCrawlDatabase::class.java
        ).build()
        database.userProfileDao().insertOrUpdate(profileEntity())
        workoutRepository = OfflineWorkoutRepository(
            sessionDao = database.workoutSessionDao(),
            setDao = database.workoutSetDao()
        )
        templateRepository = OfflineWorkoutTemplateRepository(
            templateDao = database.workoutTemplateDao(),
            exerciseCatalog = InMemoryExerciseCatalog(CATALOG_EXERCISES)
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun startTemplate_freezesPrescriptionWhenSourceTemplateChangesAndIsDeleted() = runBlocking {
        val original = template(
            listOf(
                planned(
                    id = "plank",
                    prescription = ExercisePrescription(
                        exerciseType = ExerciseType.DURATION,
                        targetSets = 3,
                        targetDurationSeconds = 45,
                        restSeconds = 240,
                        effortTarget = EffortTarget(2, 4),
                        restClass = RestClass.LONG,
                        restTargetSource = RestTargetSource.USER_PREFERENCE
                    )
                )
            )
        )
        templateRepository.saveTemplate(original)

        val session = workoutRepository.startWorkoutFromTemplate(original, UserProfile())
        templateRepository.saveTemplate(
            original.copy(
                updatedAtTimestamp = original.updatedAtTimestamp + 1,
                exercises = listOf(
                    planned(
                        id = "plank",
                        prescription = ExercisePrescription(
                            exerciseType = ExerciseType.DURATION,
                            targetSets = 1,
                            targetDurationSeconds = 90
                        )
                    )
                )
            )
        )
        templateRepository.deleteTemplate(original.id)

        val persisted = checkNotNull(workoutRepository.getSessionById(session.id))
        assertThat(persisted.origin).isEqualTo(WorkoutOrigin.CUSTOM_TEMPLATE)
        assertThat(persisted.sourceTemplateId).isEqualTo(original.id)
        assertThat(persisted.exercises.single().prescription.targetSets).isEqualTo(3)
        assertThat(persisted.exercises.single().prescription.targetDurationSeconds).isEqualTo(45)
        assertThat(persisted.exercises.single().prescription.effortTarget)
            .isEqualTo(EffortTarget(2, 4))
        assertThat(persisted.exercises.single().prescription.restClass)
            .isEqualTo(RestClass.LONG)
        assertThat(persisted.exercises.single().prescription.restTargetSource)
            .isEqualTo(RestTargetSource.USER_PREFERENCE)
        assertThat(persisted.exercises.single().prescription.restSeconds).isEqualTo(240)
        assertThat(persisted.exercises.single().sets).hasSize(3)
        assertThat(templateRepository.getTemplate(original.id)).isNull()
    }

    @Test
    fun logSetCompletion_persistsOutcomesForEveryExerciseType() = runBlocking {
        val template = template(
            listOf(
                planned(
                    "dumbbell-curl",
                    ExercisePrescription(
                        exerciseType = ExerciseType.WEIGHT_REPS,
                        targetSets = 1,
                        repRange = RepRange(8, 12),
                        targetWeight = 20.0
                    )
                ),
                planned(
                    "push-up",
                    ExercisePrescription(
                        exerciseType = ExerciseType.BODYWEIGHT_REPS,
                        targetSets = 1,
                        repRange = RepRange(8, 15)
                    )
                ),
                planned(
                    "assisted-pull-up",
                    ExercisePrescription(
                        exerciseType = ExerciseType.ASSISTED_BODYWEIGHT,
                        targetSets = 1,
                        repRange = RepRange(6, 10),
                        targetAssistanceWeight = 35.0
                    )
                ),
                planned(
                    "plank",
                    ExercisePrescription(
                        exerciseType = ExerciseType.DURATION,
                        targetSets = 1,
                        targetDurationSeconds = 45
                    )
                ),
                planned(
                    "walking",
                    ExercisePrescription(
                        exerciseType = ExerciseType.DISTANCE_DURATION,
                        targetSets = 1,
                        targetDurationSeconds = 600,
                        targetDistanceMeters = 1_000.0,
                        restSeconds = 0
                    )
                )
            )
        )
        val session = workoutRepository.startWorkoutFromTemplate(template, UserProfile())
        val setsByType = session.exercises.associate { exercise ->
            exercise.prescription.exerciseType to exercise.sets.single().id
        }

        workoutRepository.logSetCompletion(
            setsByType.getValue(ExerciseType.WEIGHT_REPS),
            SetPerformanceInput(
                reps = 12,
                weight = 20.0,
                completedAtTimestamp = COMPLETED_AT,
                isCompleted = true
            )
        )
        workoutRepository.logSetCompletion(
            setsByType.getValue(ExerciseType.BODYWEIGHT_REPS),
            SetPerformanceInput(
                reps = 15,
                completedAtTimestamp = COMPLETED_AT,
                isCompleted = true
            )
        )
        workoutRepository.logSetCompletion(
            setsByType.getValue(ExerciseType.ASSISTED_BODYWEIGHT),
            SetPerformanceInput(
                reps = 8,
                assistanceWeight = 30.0,
                completedAtTimestamp = COMPLETED_AT,
                isCompleted = true
            )
        )
        workoutRepository.logSetCompletion(
            setsByType.getValue(ExerciseType.DURATION),
            SetPerformanceInput(
                durationSeconds = 50,
                completedAtTimestamp = COMPLETED_AT,
                isCompleted = true
            )
        )
        workoutRepository.logSetCompletion(
            setsByType.getValue(ExerciseType.DISTANCE_DURATION),
            SetPerformanceInput(
                durationSeconds = 580,
                distanceMeters = 1_000.0,
                completedAtTimestamp = COMPLETED_AT,
                isCompleted = true
            )
        )

        val persisted = checkNotNull(workoutRepository.getSessionById(session.id))
        val outcomes = persisted.exercises.associateBy { it.prescription.exerciseType }
        assertThat(outcomes.getValue(ExerciseType.WEIGHT_REPS).sets.single().completedWeight)
            .isEqualTo(20.0)
        assertThat(outcomes.getValue(ExerciseType.BODYWEIGHT_REPS).sets.single().completedReps)
            .isEqualTo(15)
        assertThat(
            outcomes.getValue(ExerciseType.ASSISTED_BODYWEIGHT)
                .sets.single().completedAssistanceWeight
        ).isEqualTo(30.0)
        assertThat(outcomes.getValue(ExerciseType.DURATION).sets.single().completedDurationSeconds)
            .isEqualTo(50)
        assertThat(
            outcomes.getValue(ExerciseType.DISTANCE_DURATION)
                .sets.single().completedDistanceMeters
        ).isEqualTo(1_000.0)
    }

    private fun template(
        exercises: List<PlannedExercise>,
        weightUnit: WeightUnit? = WeightUnit.LBS
    ) = WorkoutTemplate(
        id = "template",
        name = "My Workout",
        createdAtTimestamp = 1_000L,
        updatedAtTimestamp = 1_000L,
        exercises = exercises,
        weightUnit = weightUnit
    )

    private fun loadedTemplate(unit: WeightUnit?) = template(
        listOf(
            planned(
                "dumbbell-curl",
                ExercisePrescription(
                    exerciseType = ExerciseType.WEIGHT_REPS,
                    targetSets = 2,
                    repRange = RepRange(8, 12),
                    targetWeight = 20.0
                )
            ),
            planned(
                "assisted-pull-up",
                ExercisePrescription(
                    exerciseType = ExerciseType.ASSISTED_BODYWEIGHT,
                    targetSets = 1,
                    repRange = RepRange(6, 10),
                    targetAssistanceWeight = 35.5
                )
            )
        ),
        weightUnit = unit
    )

    @Test
    fun startTemplate_convertsLoadsIntoTheProfileUnitAndLeavesTheTemplateAlone() = runBlocking {
        val kilograms = loadedTemplate(WeightUnit.KG)
        templateRepository.saveTemplate(kilograms)

        val session = workoutRepository.startWorkoutFromTemplate(
            kilograms,
            UserProfile(preferredUnit = WeightUnit.LBS)
        )

        val persisted = checkNotNull(workoutRepository.getSessionById(session.id))
        assertThat(persisted.weightUnit).isEqualTo(WeightUnit.LBS)
        // 20 kg = 44.0924... lb and 35.5 kg = 78.2643... lb, rounded to the editable precision.
        assertThat(persisted.exercises[0].prescription.targetWeight).isEqualTo(44.09)
        assertThat(persisted.exercises[0].sets.map { it.targetWeight }).containsExactly(44.09, 44.09)
        assertThat(persisted.exercises[1].prescription.targetAssistanceWeight).isEqualTo(78.26)
        assertThat(templateRepository.getTemplate(kilograms.id)).isEqualTo(kilograms)
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM workout_recommendation_records").use {
            it.moveToFirst()
            assertThat(it.getInt(0)).isEqualTo(0)
        }
    }

    @Test
    fun startTemplate_inItsOwnUnitCopiesLoadsExactly() = runBlocking {
        val kilograms = loadedTemplate(WeightUnit.KG)
        val session = workoutRepository.startWorkoutFromTemplate(
            kilograms,
            UserProfile(preferredUnit = WeightUnit.KG)
        )
        assertThat(session.weightUnit).isEqualTo(WeightUnit.KG)
        assertThat(session.exercises[0].prescription.targetWeight).isEqualTo(20.0)
        assertThat(session.exercises[1].prescription.targetAssistanceWeight).isEqualTo(35.5)
    }

    @Test
    fun startTemplate_neverTurnsASmallLoadIntoAnExplicitZero() = runBlocking {
        val tiny = template(
            listOf(
                planned(
                    "barbell-back-squat",
                    ExercisePrescription(
                        exerciseType = ExerciseType.WEIGHT_REPS,
                        targetSets = 1,
                        repRange = RepRange(5, 8),
                        targetWeight = 0.01
                    )
                )
            ),
            weightUnit = WeightUnit.LBS
        )
        // 0.01 lb is 0.0045 kg; rounded to two places that would be a stated 0.
        val session = workoutRepository.startWorkoutFromTemplate(tiny, UserProfile(preferredUnit = WeightUnit.KG))
        assertThat(session.exercises.single().prescription.targetWeight).isEqualTo(0.01)
    }

    @Test
    fun startTemplate_resumesAWorkoutInProgressEvenForAnUnconfirmedLegacyTemplate() = runBlocking {
        val inProgress = workoutRepository.startWorkoutFromTemplate(
            loadedTemplate(WeightUnit.KG),
            UserProfile(preferredUnit = WeightUnit.KG)
        )
        // Nothing is copied from the legacy template, so no unit has to be guessed.
        val resumed = workoutRepository.startWorkoutFromTemplate(loadedTemplate(null), UserProfile())
        assertThat(resumed.id).isEqualTo(inProgress.id)
    }

    @Test
    fun startTemplate_refusesALegacyLoadWhoseUnitWasNeverRecorded() = runBlocking {
        val legacy = loadedTemplate(null)
        val refusal = runCatching { workoutRepository.startWorkoutFromTemplate(legacy, UserProfile()) }
            .exceptionOrNull()
        // The domain's own refusal, not any failure that happens to stop the start.
        assertThat(refusal).isInstanceOf(IllegalStateException::class.java)
        assertThat(refusal).hasMessageThat().contains("not been confirmed")
        assertThat(workoutRepository.getActiveSessionOnce()).isNull()

        // Without a load there is nothing to misread, so a legacy template still starts.
        val bodyweight = template(
            listOf(
                planned(
                    "push-up",
                    ExercisePrescription(
                        exerciseType = ExerciseType.BODYWEIGHT_REPS,
                        targetSets = 1,
                        repRange = RepRange(8, 15)
                    )
                )
            ),
            weightUnit = null
        )
        assertThat(workoutRepository.startWorkoutFromTemplate(bodyweight, UserProfile()).origin)
            .isEqualTo(WorkoutOrigin.CUSTOM_TEMPLATE)
    }

    private fun planned(id: String, prescription: ExercisePrescription) = PlannedExercise(
        exerciseId = id,
        prescription = prescription
    )

    private fun profileEntity() = UserProfileEntity(
        id = UserProfile.DEFAULT_PROFILE_ID,
        revision = 0L,
        name = "Crawler",
        primaryGoal = FitnessGoal.BUILD_MUSCLE,
        experienceLevel = ExperienceLevel.INTERMEDIATE,
        preferredDurationMinutes = 50,
        daysPerWeek = 4,
        availableEquipmentJson = "Bodyweight",
        preferredUnit = WeightUnit.LBS,
        musclePrioritiesJson = "",
        excludedExerciseIdsJson = ""
    )

    private companion object {
        const val COMPLETED_AT = 1_777_777L
        val CATALOG_EXERCISES = listOf(
            exercise("dumbbell-curl", ExerciseType.WEIGHT_REPS),
            exercise("push-up", ExerciseType.BODYWEIGHT_REPS),
            exercise("assisted-pull-up", ExerciseType.ASSISTED_BODYWEIGHT),
            exercise("plank", ExerciseType.DURATION),
            exercise("walking", ExerciseType.DISTANCE_DURATION)
        )

        fun exercise(id: String, type: ExerciseType) = Exercise(
            id = id,
            name = id,
            primaryMuscles = listOf("Core"),
            listedEquipment = listOf("Bodyweight"),
            type = type
        )
    }
}
