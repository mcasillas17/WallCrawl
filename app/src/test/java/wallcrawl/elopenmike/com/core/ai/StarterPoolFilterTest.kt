package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Test
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.Difficulty
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.StarterProfile
import wallcrawl.elopenmike.com.core.model.TrainingWeek
import wallcrawl.elopenmike.com.core.model.UserProfile

class StarterPoolFilterTest {
    private val catalog = PlannerFixtureContextFactory().bundledCatalogProjection()
    private val zone = ZoneId.of("UTC")
    private val week = TrainingWeek.startingOn(20_696L, zone)

    private suspend fun pool(profile: UserProfile) = WorkoutGenerationContextBuilder(
        userProfileRepository = StaticUserProfileRepository(profile),
        workoutRepository = StaticWorkoutRepository(emptyList()),
        exerciseCatalog = InMemoryExerciseCatalog(catalog.exercises),
        exerciseFilter = ExerciseFilter(),
        historyAnalyzer = WorkoutHistoryAnalyzer(),
        plannerFeatureFlags = PlannerFeatureFlags.PRODUCTION,
        trainingProgramStateProvider = TrainingProgramStateProvider(
            RecomputingLedgerRepository(emptyList(), catalog.exercises, week, catalog.sourceCommit),
            zoneId = { zone }
        ),
        catalogVersion = { catalog.sourceCommit },
        nowTimestamp = { week.startEpochMillis + 3 * 24 * 60 * 60 * 1_000L },
        zoneId = { zone }
    ).build().allowedExercises

    @Test
    fun starterPoolIsBeginnerOnly_personalizedPoolIsUnfiltered() = runTest {
        val starter = StarterProfile.apply(UserProfile(), setOf(FitnessGoal.GENERAL_FITNESS), 3, 45, "US")
        val personalized = starter.copy(personalizationCompleted = true)
        val starterPool = pool(starter)
        val fullPool = pool(personalized)

        assertThat(starterPool).isNotEmpty()
        assertThat(starterPool.map { it.programming?.difficulty }.toSet())
            .containsExactly(Difficulty.BEGINNER)
        // Personalized profiles keep the old pool, including non-beginner exercises.
        assertThat(fullPool.map { it.id }).containsAtLeastElementsIn(starterPool.map { it.id })
        assertThat(fullPool.any { it.programming?.difficulty != Difficulty.BEGINNER }).isTrue()
    }
}
