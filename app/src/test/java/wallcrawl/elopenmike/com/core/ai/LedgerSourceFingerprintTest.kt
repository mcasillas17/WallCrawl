package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.ZoneId
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.Exercise
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.TrainingWeek
import wallcrawl.elopenmike.com.core.model.WorkoutSession

/**
 * The fingerprint decides whether a cached ledger may be reused, so it has to move for
 * every input that can change credit and stay still for every input that cannot.
 */
class LedgerSourceFingerprintTest {

    private val week = TrainingWeek.startingOn(MONDAY_EPOCH_DAY, ZoneId.of("UTC"))
    private val catalog = mapOf(
        "synthetic-bench-press" to syntheticApprovedExercise(
            id = "synthetic-bench-press",
            directPrimaryMuscle = "Chest",
            descriptiveSecondaryMuscles = setOf("Triceps")
        )
    )
    private val sessions = listOf(
        completedSession(
            id = "session-1",
            completedAtEpochMillis = week.startEpochMillis,
            exercises = listOf(
                exerciseInstance(
                    exerciseId = "synthetic-bench-press",
                    id = "instance-1",
                    sets = listOf(completedNormalSet(id = "set-1"), completedNormalSet(id = "set-2"))
                )
            )
        )
    )

    @Test
    fun identicalInputsProduceAnIdenticalFingerprint() {
        assertThat(fingerprintOf()).isEqualTo(fingerprintOf())
        assertThat(fingerprintOf()).matches("[0-9a-f]{64}")
    }

    @Test
    fun inputOrderingDoesNotChangeTheFingerprint() {
        val reordered = sessions.map { session ->
            session.copy(
                exercises = session.exercises.map { it.copy(sets = it.sets.reversed()) }
            )
        }

        assertThat(fingerprintOf(sessions = reordered)).isEqualTo(fingerprintOf())
    }

    @Test
    fun everyInputThatCanChangeCreditAlsoChangesTheFingerprint() {
        val baseline = fingerprintOf()
        val variants = mapOf(
            "session id" to fingerprintOf(
                sessions = sessions.map { it.copy(id = "session-renamed") }
            ),
            "session completion time" to fingerprintOf(
                sessions = sessions.map { it.copy(completedAtTimestamp = week.startEpochMillis + 1L) }
            ),
            "exercise instance id" to fingerprintOf(
                sessions = sessions.mutateExercises { it.copy(id = "instance-renamed") }
            ),
            "catalog exercise id" to fingerprintOf(
                sessions = sessions.mutateExercises { it.copy(exerciseId = "synthetic-unknown") }
            ),
            "set id" to fingerprintOf(
                sessions = sessions.mutateSets { set ->
                    if (set.id == "set-2") set.copy(id = "set-renamed") else set
                }
            ),
            "set type" to fingerprintOf(
                sessions = sessions.mutateSets { set ->
                    if (set.id == "set-2") set.copy(type = SetType.WARMUP) else set
                }
            ),
            "set completion state" to fingerprintOf(
                sessions = sessions.mutateSets { set ->
                    if (set.id == "set-2") set.copy(isCompleted = false) else set
                }
            ),
            "review state" to fingerprintOf(
                exercisesById = mapOf(
                    "synthetic-bench-press" to syntheticDraftExercise(
                        id = "synthetic-bench-press",
                        directPrimaryMuscle = "Chest",
                        descriptiveSecondaryMuscles = setOf("Triceps")
                    )
                )
            ),
            "direct primary mapping" to fingerprintOf(
                exercisesById = mapOf(
                    "synthetic-bench-press" to syntheticApprovedExercise(
                        id = "synthetic-bench-press",
                        directPrimaryMuscle = "Shoulders",
                        descriptiveSecondaryMuscles = setOf("Triceps")
                    )
                )
            ),
            "descriptive secondary mapping" to fingerprintOf(
                exercisesById = mapOf(
                    "synthetic-bench-press" to syntheticApprovedExercise(
                        id = "synthetic-bench-press",
                        directPrimaryMuscle = "Chest",
                        descriptiveSecondaryMuscles = setOf("Triceps", "Shoulders")
                    )
                )
            ),
            "catalog version" to fingerprintOf(catalogVersion = "another-catalog-commit"),
            "review policy version" to fingerprintOf(reviewPolicyVersion = 2),
            "week" to fingerprintOf(
                week = TrainingWeek.startingOn(MONDAY_EPOCH_DAY - 7L, ZoneId.of("UTC"))
            ),
            "time zone" to fingerprintOf(
                week = TrainingWeek.startingOn(MONDAY_EPOCH_DAY, ZoneId.of("Asia/Kolkata"))
            )
        )

        variants.forEach { (changedInput, fingerprint) ->
            assertWithMessage("fingerprint after changing %s", changedInput)
                .that(fingerprint)
                .isNotEqualTo(baseline)
        }
        assertThat(variants.values.toSet()).hasSize(variants.size)
    }

    @Test
    fun valuesThatCannotAffectCreditAreExcludedFromTheFingerprint() {
        val withPrivateValues = sessions
            .map { session ->
                session.copy(
                    name = "Leg day at the gym",
                    notes = "free text the user typed",
                    actualDurationMinutes = 99
                )
            }
            .mutateSets { set ->
                set.copy(
                    rpe = 9.5f,
                    rir = 0,
                    feltManageable = false,
                    completedReps = 3,
                    completedWeight = 275.0,
                    completedAtTimestamp = 1_700_000_000_000L
                )
            }

        assertThat(fingerprintOf(sessions = withPrivateValues)).isEqualTo(fingerprintOf())
    }

    private fun fingerprintOf(
        sessions: List<WorkoutSession> = this.sessions,
        exercisesById: Map<String, Exercise> = catalog,
        week: TrainingWeek = this.week,
        catalogVersion: String = SYNTHETIC_CATALOG_VERSION,
        reviewPolicyVersion: Int = 1
    ): String = LedgerSourceFingerprint.of(
        sessions = sessions,
        exercisesById = exercisesById,
        policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
        week = week,
        catalogVersion = catalogVersion,
        reviewPolicyVersion = reviewPolicyVersion
    )
}
