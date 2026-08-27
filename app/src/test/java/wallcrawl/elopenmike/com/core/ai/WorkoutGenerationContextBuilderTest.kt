package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wallcrawl.elopenmike.com.core.database.repository.UserProfileRepository
import wallcrawl.elopenmike.com.core.database.repository.WorkoutRepository
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.GeneratedWorkout
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutExercise
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet
import wallcrawl.elopenmike.com.core.model.WorkoutSummary

class WorkoutGenerationContextBuilderTest {

    @Test
    fun build_includesCatalogEntriesWithoutReviewedProgrammingWhenEquipmentMatches() = runTest {
        val reviewed = InMemoryExerciseCatalog.SAMPLE_EXERCISES.first()
        val unreviewed = reviewed.copy(id = "catalog-only-exercise", programming = null)
        val builder = WorkoutGenerationContextBuilder(
            userProfileRepository = StubUserProfileRepository(UserProfile()),
            workoutRepository = StubWorkoutRepository(emptyList()),
            exerciseCatalog = InMemoryExerciseCatalog(listOf(reviewed, unreviewed)),
            exerciseFilter = ExerciseFilter(),
            historyAnalyzer = WorkoutHistoryAnalyzer()
        )

        val context = builder.build()

        assertThat(context.allowedExercises.map { it.id })
            .containsExactly(reviewed.id, unreviewed.id)
    }

    @Test
    fun build_filtersCandidatesAndIncludesPersistedPerformance() = runTest {
        val now = 10_000_000L
        val profile = UserProfile(
            primaryGoal = FitnessGoal.STRENGTH,
            availableEquipment = listOf(
                StandardEquipment.DUMBBELL,
                StandardEquipment.BENCH,
                StandardEquipment.BODYWEIGHT
            ),
            musclePriorities = mapOf(StandardMuscles.CHEST to PriorityLevel.HIGH),
            excludedExerciseIds = listOf("dumbbell-lateral-raise")
        )
        val completedSession = completedInclinePressSession(now - 1_000L)
        val builder = WorkoutGenerationContextBuilder(
            userProfileRepository = StubUserProfileRepository(profile),
            workoutRepository = StubWorkoutRepository(listOf(completedSession)),
            exerciseCatalog = InMemoryExerciseCatalog(),
            exerciseFilter = ExerciseFilter(),
            historyAnalyzer = WorkoutHistoryAnalyzer(),
            nowTimestamp = { now }
        )

        val context = builder.build()

        assertThat(context.userProfile).isEqualTo(profile)
        assertThat(context.allowedExercises.map { it.id })
            .contains("incline-dumbbell-press")
        assertThat(context.allowedExercises.map { it.id })
            .doesNotContain("barbell-bench-press")
        assertThat(context.allowedExercises.map { it.id })
            .doesNotContain("dumbbell-lateral-raise")
        assertThat(context.recentWorkoutHistory).containsExactly(completedSession)
        assertThat(context.exerciseHistory.getValue("incline-dumbbell-press").lastWeight)
            .isEqualTo(45.0)
        assertThat(context.recentlyTrainedMuscles).containsExactly(StandardMuscles.CHEST)
    }

    @Test
    fun build_limitsHistoryPassedToPlanner() = runTest {
        val sessions = (1..12).map { index ->
            completedInclinePressSession(completedAtTimestamp = index.toLong())
                .copy(id = "session-$index")
        }
        val builder = WorkoutGenerationContextBuilder(
            userProfileRepository = StubUserProfileRepository(UserProfile()),
            workoutRepository = StubWorkoutRepository(sessions),
            exerciseCatalog = InMemoryExerciseCatalog(),
            exerciseFilter = ExerciseFilter(),
            historyAnalyzer = WorkoutHistoryAnalyzer(),
            nowTimestamp = { 12L }
        )

        val context = builder.build()

        assertThat(context.recentWorkoutHistory).hasSize(8)
        assertThat(context.recentWorkoutHistory.map { it.id })
            .containsExactly(
                "session-12",
                "session-11",
                "session-10",
                "session-9",
                "session-8",
                "session-7",
                "session-6",
                "session-5"
            )
            .inOrder()
    }

    private fun completedInclinePressSession(completedAtTimestamp: Long): WorkoutSession {
        val set = WorkoutSet(
            id = "set-$completedAtTimestamp",
            workoutExerciseId = "workout-exercise-$completedAtTimestamp",
            setNumber = 1,
            targetReps = 10,
            completedReps = 10,
            targetWeight = 45.0,
            completedWeight = 45.0,
            isCompleted = true
        )
        return WorkoutSession(
            id = "session-$completedAtTimestamp",
            name = "Push",
            completedAtTimestamp = completedAtTimestamp,
            status = SessionStatus.COMPLETED,
            focusMuscles = listOf(StandardMuscles.CHEST),
            exercises = listOf(
                WorkoutExercise(
                    id = set.workoutExerciseId,
                    sessionId = "session-$completedAtTimestamp",
                    exerciseId = "incline-dumbbell-press",
                    orderIndex = 0,
                    targetSets = 1,
                    targetRepMin = 8,
                    targetRepMax = 10,
                    targetWeight = 45.0,
                    sets = listOf(set)
                )
            )
        )
    }
}

private class StubUserProfileRepository(
    private val profile: UserProfile
) : UserProfileRepository {
    override fun getUserProfile(): Flow<UserProfile> = flowOf(profile)
    override suspend fun getProfileOnce(): UserProfile = profile
    override suspend fun saveUserProfile(profile: UserProfile) = Unit
    override suspend fun updatePrimaryGoal(goal: FitnessGoal) = Unit
    override suspend fun updateExperienceLevel(level: wallcrawl.elopenmike.com.core.model.ExperienceLevel) = Unit
    override suspend fun updatePreferredDuration(minutes: Int) = Unit
    override suspend fun updateDaysPerWeek(days: Int) = Unit
    override suspend fun updateEquipment(equipment: List<String>) = Unit
    override suspend fun updateUnit(unit: WeightUnit) = Unit
    override suspend fun updateMusclePriorities(priorities: Map<String, PriorityLevel>) = Unit
    override suspend fun updateExcludedExercises(excludedIds: List<String>) = Unit
}

private class StubWorkoutRepository(
    private val completedSessions: List<WorkoutSession>
) : WorkoutRepository {
    override fun observeActiveSession(): Flow<WorkoutSession?> = flowOf(null)
    override suspend fun getActiveSessionOnce(): WorkoutSession? = null
    override suspend fun getSessionById(sessionId: String): WorkoutSession? = null
    override fun observeSession(sessionId: String): Flow<WorkoutSession?> = flowOf(null)
    override fun observeCompletedSessions(limit: Int): Flow<List<WorkoutSession>> =
        flowOf(completedSessions.take(limit))

    override fun observeCompletedWorkoutCount(): Flow<Int> = flowOf(completedSessions.size)

    override fun observeCompletedWorkoutCountSince(startTimestamp: Long): Flow<Int> = flowOf(
        completedSessions.count { (it.completedAtTimestamp ?: Long.MIN_VALUE) >= startTimestamp }
    )
    override suspend fun getRecentCompletedSessions(limit: Int): List<WorkoutSession> =
        completedSessions.sortedByDescending { it.completedAtTimestamp }.take(limit)

    override suspend fun startWorkoutFromGenerated(
        generated: GeneratedWorkout,
        userProfile: UserProfile
    ): WorkoutSession =
        error("Not used")

    override suspend fun startWorkoutFromTemplate(
        template: wallcrawl.elopenmike.com.core.model.WorkoutTemplate,
        userProfile: UserProfile
    ): WorkoutSession = error("Not used")

    override suspend fun logSetCompletion(
        setId: String,
        reps: Int?,
        weight: Double?,
        isCompleted: Boolean
    ) = Unit

    override suspend fun completeWorkout(
        sessionId: String,
        actualDurationMinutes: Int
    ): WorkoutSummary = error("Not used")

    override suspend fun cancelWorkout(sessionId: String) = Unit
}
