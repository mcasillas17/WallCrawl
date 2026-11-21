package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertWithMessage
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.Difficulty
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.ImpactLevel
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StarterProfile
import wallcrawl.elopenmike.com.core.model.TrainingWeek
import wallcrawl.elopenmike.com.core.model.UserProfile

/**
 * A new user's first workout comes from the production planner and context builder over the
 * bundled catalog. The main work must be bodyweight only, not high impact, and beginner
 * difficulty, and must fit the chosen length. Warm-ups and cooldowns are only checked for
 * impact; their difficulty and equipment are exempt.
 */
@RunWith(Parameterized::class)
class StarterWorkoutGuardTest(private val goal: FitnessGoal, private val minutes: Int) {

    private val catalog = PlannerFixtureContextFactory().bundledCatalogProjection()

    @Test
    fun starterWorkout_isSimpleAndSafe() = runTest {
        val week = TrainingWeek.startingOn(MONDAY_EPOCH_DAY, ZONE)
        val profile = StarterProfile.apply(UserProfile(), goal, 3, minutes, "US")
        val builder = WorkoutGenerationContextBuilder(
            userProfileRepository = StaticUserProfileRepository(profile),
            workoutRepository = StaticWorkoutRepository(emptyList()),
            exerciseCatalog = InMemoryExerciseCatalog(catalog.exercises),
            exerciseFilter = ExerciseFilter(),
            historyAnalyzer = WorkoutHistoryAnalyzer(),
            plannerFeatureFlags = PlannerFeatureFlags.PRODUCTION,
            trainingProgramStateProvider = TrainingProgramStateProvider(
                weeklyDoseLedgerRepository = RecomputingLedgerRepository(
                    emptyList(), catalog.exercises, week, catalog.sourceCommit
                ),
                zoneId = { ZONE }
            ),
            catalogVersion = { catalog.sourceCommit },
            nowTimestamp = { week.startEpochMillis + 3 * DAY_MILLIS },
            zoneId = { ZONE }
        )
        val workout = FakeWorkoutPlanner().generateWorkout(builder.build())
        val label = "$goal x $minutes min"
        val byId = catalog.exercises.associateBy { it.id }

        assertWithMessage("$label: empty workout").that(workout.exercises).isNotEmpty()
        assertWithMessage("$label: ${workout.estimatedDurationMinutes} min over budget")
            .that(workout.estimatedDurationMinutes).isAtMost(minutes)
        val problems = mutableListOf<String>()
        for (planned in workout.exercises) {
            val exercise = byId.getValue(planned.exerciseId)
            val phase = planned.prescription.purpose
            // Warm-up and cooldown moves have no programming block; they are exempt from the
            // difficulty and equipment checks (decided by the controller) but not from impact.
            if (exercise.reviewedMetadata?.impactLevel == ImpactLevel.HIGH) {
                problems += "${exercise.id} [$phase] impact=HIGH"
            }
            if (phase.isWorkoutPhase) continue
            if (exercise.programming?.difficulty != Difficulty.BEGINNER) {
                problems += "${exercise.id} [$phase] difficulty=${exercise.programming?.difficulty}"
            }
            val bodyweightOnly = exercise.programming?.requiredEquipmentCombinations?.any { combo ->
                combo.all { it == StandardEquipment.BODYWEIGHT }
            } == true
            if (!bodyweightOnly) {
                problems += "${exercise.id} [$phase] equipment=${exercise.programming?.requiredEquipmentCombinations}"
            }
        }
        assertWithMessage("$label: $problems").that(problems).isEmpty()
    }

    private companion object {
        const val MONDAY_EPOCH_DAY = 20_696L
        const val DAY_MILLIS = 24 * 60 * 60 * 1_000L
        val ZONE: ZoneId = ZoneId.of("UTC")

        @JvmStatic
        @Parameterized.Parameters(name = "{0} {1}min")
        fun cases(): List<Array<Any>> =
            FitnessGoal.entries.flatMap { g -> listOf(20, 30, 45, 60).map { arrayOf<Any>(g, it) } }
    }
}
