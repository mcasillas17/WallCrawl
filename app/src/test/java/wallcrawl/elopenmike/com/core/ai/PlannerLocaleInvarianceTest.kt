package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import wallcrawl.elopenmike.com.core.exercise.ExerciseFilter
import wallcrawl.elopenmike.com.core.exercise.InMemoryExerciseCatalog
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.FitnessGoal
import wallcrawl.elopenmike.com.core.model.PriorityLevel
import wallcrawl.elopenmike.com.core.model.StandardEquipment
import wallcrawl.elopenmike.com.core.model.StandardMuscles
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.WorkoutGenerationContext

/**
 * Language is presentation. It must never reach a training decision.
 *
 * Running the planner under a Spanish default locale is not a hypothetical: `Locale.getDefault`
 * changes for the whole process when the app language changes, and any comparison,
 * case fold, sort, or number parse that quietly picks it up would make the plan depend on
 * the interface language. These tests plan the same profile under every locale in
 * [LOCALES] — including one with a decimal comma and one with locale-specific case
 * folding — and require the result to be identical down to the prescriptions.
 */
class PlannerLocaleInvarianceTest {

    private val defaultLocale: Locale = Locale.getDefault()
    private val filter = ExerciseFilter()
    private val allExercises = InMemoryExerciseCatalog.SAMPLE_EXERCISES

    @After
    fun restoreLocale() {
        // Every test here changes the process default, so it is always put back — a leaked
        // locale would quietly change what every later test is running under.
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun identicalInputsProduceAnIdenticalPlanInEveryLocale() = runTest {
        val plans = LOCALES.associateWith { locale ->
            Locale.setDefault(locale)
            FakeWorkoutPlanner().generateWorkout(context())
        }

        val reference = plans.getValue(LOCALES.first())
        plans.forEach { (locale, plan) ->
            assertWithMessage("exercise selection and order under $locale")
                .that(plan.exercises.map { it.exerciseId })
                .isEqualTo(reference.exercises.map { it.exerciseId })
            assertWithMessage("prescriptions under $locale")
                .that(plan.exercises.map { it.prescription })
                .isEqualTo(reference.exercises.map { it.prescription })
            assertWithMessage("focus muscles under $locale")
                .that(plan.focusMuscles).isEqualTo(reference.focusMuscles)
            assertWithMessage("estimated duration under $locale")
                .that(plan.estimatedDurationMinutes)
                .isEqualTo(reference.estimatedDurationMinutes)
            assertWithMessage("title under $locale").that(plan.title).isEqualTo(reference.title)
            assertWithMessage("explanation under $locale")
                .that(plan.rationale).isEqualTo(reference.rationale)
        }
    }

    @Test
    fun loadsAndRestTargetsAreTheSameNumbersInEveryLocale() = runTest {
        // A decimal comma is the classic way a load silently changes magnitude. The plan
        // carries Doubles, and this pins that they are equal, not merely formatted alike.
        val loads = LOCALES.associateWith { locale ->
            Locale.setDefault(locale)
            FakeWorkoutPlanner().generateWorkout(context()).exercises.map { exercise ->
                Triple(
                    exercise.prescription.targetWeight,
                    exercise.prescription.targetAssistanceWeight,
                    exercise.prescription.restSeconds
                )
            }
        }

        val reference = loads.getValue(LOCALES.first())
        loads.forEach { (locale, values) ->
            assertWithMessage("loads and rest under $locale").that(values).isEqualTo(reference)
        }
    }

    @Test
    fun eligibilityFilteringMatchesTheSameExercisesInEveryLocale() = runTest {
        // Equipment matching lower-cases its inputs. Under a Turkish locale a naive
        // `lowercase()` turns "BARBELL" into "barbell" with a dotless i elsewhere in the
        // vocabulary; the filter uses the root locale so the result cannot move.
        val filtered = LOCALES.associateWith { locale ->
            Locale.setDefault(locale)
            filter.filterCandidates(allExercises, profile()).map(Exercise::id)
        }

        val reference = filtered.getValue(LOCALES.first())
        filtered.forEach { (locale, ids) ->
            assertWithMessage("filtered candidates under $locale").that(ids).isEqualTo(reference)
        }
    }

    @Test
    fun aFailureReportsTheSameTypedReasonInEveryLocale() = runTest {
        val failures = LOCALES.associateWith { locale ->
            Locale.setDefault(locale)
            runCatching {
                FakeWorkoutPlanner().generateWorkout(
                    WorkoutGenerationContext(userProfile = profile(), allowedExercises = emptyList())
                )
            }.exceptionOrNull() as? WorkoutValidationException
        }

        failures.forEach { (locale, failure) ->
            assertWithMessage("failure reason under $locale").that(failure?.failure)
                .isEqualTo(WorkoutPlanningFailure.NO_CANDIDATES)
        }
    }

    @Test
    fun theCatalogReturnsTheSameExercisesInEveryLocale() = runTest {
        val catalog = InMemoryExerciseCatalog()
        val results = LOCALES.associateWith { locale ->
            Locale.setDefault(locale)
            catalog.searchExercises(query = "press").first().map(Exercise::id)
        }

        val reference = results.getValue(LOCALES.first())
        results.forEach { (locale, ids) ->
            assertWithMessage("search under $locale").that(ids).isEqualTo(reference)
        }
        assertThat(reference).isNotEmpty()
    }

    @Test
    fun wholeProgramValidationDecidesTheSameWayInEveryLocale() = runTest {
        val outcomes = LOCALES.associateWith { locale ->
            Locale.setDefault(locale)
            val context = context()
            val plan = FakeWorkoutPlanner().generateWorkout(context)
            ProgramValidator(GeneratedWorkoutValidator(InMemoryExerciseCatalog()))
                .validate(workout = plan, context = context, allowRepair = true)
        }

        val reference = outcomes.getValue(LOCALES.first())
        outcomes.forEach { (locale, result) ->
            assertWithMessage("validation outcome under $locale")
                .that(result.snapshot.outcome).isEqualTo(reference.snapshot.outcome)
            assertWithMessage("reason codes under $locale")
                .that(result.snapshot.reasonCodes).isEqualTo(reference.snapshot.reasonCodes)
            assertWithMessage("dose accounting under $locale")
                .that(result.snapshot.doseAccounting)
                .isEqualTo(reference.snapshot.doseAccounting)
            // The context fingerprint decides whether a displayed plan may still be started.
            // If a display language could move it, changing language would refuse every plan
            // already on screen.
            assertWithMessage("context identity under $locale")
                .that(result.snapshot.contextIdentity)
                .isEqualTo(reference.snapshot.contextIdentity)
        }
        assertThat(reference).isInstanceOf(ProgramValidationResult.Valid::class.java)
    }

    @Test
    fun theProductionLegacyPlanValidatesUnchangedWhileTheReviewedGateIsDisabled() = runTest {
        // The shipped catalog carries 37 DRAFT reviewed records and zero APPROVED ones, and
        // production leaves `reviewedCapabilityEligibility` false. Whole-program validation
        // must therefore accept exactly what the legacy planner already produced: no
        // approved-metadata rule, no eligibility-decision rule, and no dose accounting.
        val context = context()
        assertThat(context.automaticEligibilityResult).isNull()
        assertThat(context.trainingProgramState).isNull()
        val plan = FakeWorkoutPlanner().generateWorkout(context)

        val result = ProgramValidator(GeneratedWorkoutValidator(InMemoryExerciseCatalog()))
            .validate(workout = plan, context = context, allowRepair = false)

        assertThat(result).isInstanceOf(ProgramValidationResult.Valid::class.java)
        assertThat((result as ProgramValidationResult.Valid).workout).isEqualTo(plan)
        assertThat(result.snapshot.reviewedPathEnabled).isFalse()
        assertThat(result.snapshot.doseAccounting).isEmpty()
        assertThat(result.snapshot.trainingPolicyVersion).isNull()
        assertThat(result.snapshot.ledgerPolicyVersion).isNull()
        assertThat(result.snapshot.adaptationState).isNull()
    }

    private fun profile() = UserProfile(
        goals = setOf(FitnessGoal.BUILD_MUSCLE, FitnessGoal.STRENGTH),
        availableEquipment = StandardEquipment.ALL,
        musclePriorities = mapOf(
            StandardMuscles.CHEST to PriorityLevel.HIGH,
            StandardMuscles.SHOULDERS to PriorityLevel.HIGH
        )
    )

    private fun context() = WorkoutGenerationContext(
        userProfile = profile(),
        allowedExercises = filter.filterCandidates(allExercises, profile())
    )

    private companion object {
        /**
         * English, the neutral Latin American Spanish target, a Spanish locale that writes
         * decimals with a comma, and Turkish — whose case folding is the classic way a
         * locale leaks into a comparison.
         */
        val LOCALES = listOf(
            Locale.forLanguageTag("en-US"),
            Locale.forLanguageTag("es-MX"),
            Locale.forLanguageTag("es-ES"),
            Locale.forLanguageTag("tr-TR")
        )
    }
}
