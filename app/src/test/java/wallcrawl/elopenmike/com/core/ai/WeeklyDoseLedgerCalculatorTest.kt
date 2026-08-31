package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.time.ZoneId
import org.junit.Assert.assertThrows
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.LedgerOmissionReason
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.SessionStatus
import wallcrawl.elopenmike.com.core.model.SetStopReason
import wallcrawl.elopenmike.com.core.model.SetType
import wallcrawl.elopenmike.com.core.model.TrainingWeek
import wallcrawl.elopenmike.com.core.model.WeeklyDoseLedger
import wallcrawl.elopenmike.com.core.model.WorkoutSession
import wallcrawl.elopenmike.com.core.model.WorkoutSet

/**
 * `PRIMARY_ONLY_V1` crediting rules, exercised against real domain models.
 */
class WeeklyDoseLedgerCalculatorTest {

    private val calculator = WeeklyDoseLedgerCalculator()
    private val week = TrainingWeek.startingOn(MONDAY_EPOCH_DAY, ZoneId.of("UTC"))

    @Test
    fun oneCompletedNormalWorkSetCreditsItsDirectPrimaryMuscleExactlyOnce() {
        val session = completedSession(
            id = "session-1",
            completedAtEpochMillis = week.startEpochMillis,
            exercises = listOf(
                exerciseInstance(
                    exerciseId = "synthetic-bench-press",
                    sets = listOf(completedNormalSet())
                )
            )
        )

        val ledger = calculator.calculate(
            sessions = listOf(session),
            exercisesById = mapOf(
                "synthetic-bench-press" to syntheticApprovedExercise(
                    id = "synthetic-bench-press",
                    directPrimaryMuscle = "Chest",
                    descriptiveSecondaryMuscles = setOf("Triceps")
                )
            ),
            policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
            week = week,
            catalogVersion = SYNTHETIC_CATALOG_VERSION,
            reviewPolicyVersion = 1
        )

        assertThat(ledger.directPrimarySets).containsExactly("Chest", 1)
    }

    @Test
    fun aCompletedWarmUpSetIsNeverCreditedAsWorkExposure() {
        val ledger = ledgerOf(
            sets = listOf(
                completedNormalSet(type = SetType.WARMUP),
                completedNormalSet(type = SetType.WARMUP),
                completedNormalSet()
            )
        )

        assertThat(ledger.directPrimarySets).containsExactly("Chest", 1)
    }

    @Test
    fun everyNonWarmUpSetTypeCountsAsExactlyOneCompletedWorkSet() {
        SetType.entries.filter { it != SetType.WARMUP }.forEach { setType ->
            val ledger = ledgerOf(sets = listOf(completedNormalSet(type = setType)))

            assertWithMessage("credit for %s", setType)
                .that(ledger.directPrimarySets)
                .containsExactly("Chest", 1)
        }
    }

    @Test
    fun completedWorkSetsAcrossExercisesAndSessionsCreditIndependently() {
        val ledger = calculator.calculate(
            sessions = listOf(
                completedSession(
                    id = "session-1",
                    completedAtEpochMillis = week.startEpochMillis,
                    exercises = listOf(
                        exerciseInstance(
                            exerciseId = "synthetic-bench-press",
                            sets = listOf(completedNormalSet(), completedNormalSet())
                        ),
                        exerciseInstance(
                            exerciseId = "synthetic-row",
                            id = "instance-row",
                            orderIndex = 1,
                            sets = listOf(completedNormalSet())
                        )
                    )
                ),
                completedSession(
                    id = "session-2",
                    completedAtEpochMillis = week.startEpochMillis + 86_400_000L,
                    exercises = listOf(
                        exerciseInstance(
                            exerciseId = "synthetic-bench-press",
                            sets = listOf(completedNormalSet(), completedNormalSet(), completedNormalSet())
                        )
                    )
                )
            ),
            exercisesById = mapOf(
                "synthetic-bench-press" to syntheticApprovedExercise(
                    id = "synthetic-bench-press",
                    directPrimaryMuscle = "Chest"
                ),
                "synthetic-row" to syntheticApprovedExercise(
                    id = "synthetic-row",
                    directPrimaryMuscle = "Back"
                )
            ),
            policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
            week = week,
            catalogVersion = SYNTHETIC_CATALOG_VERSION,
            reviewPolicyVersion = 1
        )

        assertThat(ledger.directPrimarySets).containsExactly("Back", 1, "Chest", 5)
    }

    @Test
    fun incompleteAndStoppedSetsAreNeverCredited() {
        val ledger = ledgerOf(
            sets = listOf(
                completedNormalSet(),
                unfinishedSet(),
                stoppedSet(reason = SetStopReason.USER_SKIPPED),
                stoppedSet(reason = SetStopReason.PAIN_STOP),
                stoppedSet(reason = SetStopReason.TIME_CONSTRAINT)
            )
        )

        assertThat(ledger.directPrimarySets).containsExactly("Chest", 1)
        assertThat(ledger.omittedWorkSets).isEqualTo(0)
    }

    @Test
    fun descriptiveSecondaryMusclesAreRecordedForAnalyticsButNeverDoseCredited() {
        val ledger = ledgerOf(sets = listOf(completedNormalSet(), completedNormalSet()))

        assertThat(ledger.directPrimarySets).containsExactly("Chest", 2)
        assertThat(ledger.secondaryInvolvement).containsExactly("Triceps", 2)
    }

    @Test
    fun unknownMissingAndDraftMetadataAreOmittedWithTypedReasonsInsteadOfGuessed() {
        val ledger = calculator.calculate(
            sessions = listOf(
                completedSession(
                    id = "session-1",
                    completedAtEpochMillis = week.startEpochMillis,
                    exercises = listOf(
                        exerciseInstance(
                            exerciseId = "not-in-the-catalog",
                            id = "instance-unknown",
                            orderIndex = 0,
                            sets = listOf(completedNormalSet(), completedNormalSet())
                        ),
                        exerciseInstance(
                            exerciseId = "synthetic-no-reviewed-block",
                            id = "instance-missing",
                            orderIndex = 1,
                            sets = listOf(completedNormalSet())
                        ),
                        exerciseInstance(
                            exerciseId = "synthetic-draft",
                            id = "instance-draft",
                            orderIndex = 2,
                            sets = listOf(
                                completedNormalSet(),
                                completedNormalSet(),
                                completedNormalSet(type = SetType.WARMUP),
                                unfinishedSet()
                            )
                        )
                    )
                )
            ),
            exercisesById = mapOf(
                "synthetic-no-reviewed-block" to
                    syntheticExerciseWithoutReviewedMetadata("synthetic-no-reviewed-block"),
                "synthetic-draft" to syntheticDraftExercise(
                    id = "synthetic-draft",
                    directPrimaryMuscle = "Quadriceps",
                    descriptiveSecondaryMuscles = setOf("Glutes")
                )
            ),
            policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
            week = week,
            catalogVersion = SYNTHETIC_CATALOG_VERSION,
            reviewPolicyVersion = 1
        )

        // Nothing is guessed: no legacy primaryMuscles, no exercise name, no movement
        // pattern, and no DRAFT direct primary reaches either credit map.
        assertThat(ledger.directPrimarySets).isEmpty()
        assertThat(ledger.secondaryInvolvement).isEmpty()
        assertThat(ledger.unattributedWorkSets).containsExactly(
            LedgerOmissionReason.UNKNOWN_EXERCISE, 2,
            LedgerOmissionReason.MISSING_REVIEWED_METADATA, 1,
            LedgerOmissionReason.METADATA_NOT_APPROVED, 2
        )
    }

    @Test
    fun omissionReasonsIterateInAStableDeclaredOrder() {
        val ledger = calculator.calculate(
            sessions = listOf(
                completedSession(
                    id = "session-1",
                    completedAtEpochMillis = week.startEpochMillis,
                    exercises = listOf(
                        exerciseInstance("synthetic-draft", listOf(completedNormalSet()), "a", 0),
                        exerciseInstance("synthetic-missing", listOf(completedNormalSet()), "b", 1),
                        exerciseInstance("synthetic-unknown", listOf(completedNormalSet()), "c", 2)
                    )
                )
            ),
            exercisesById = mapOf(
                "synthetic-draft" to syntheticDraftExercise("synthetic-draft", "Quadriceps"),
                "synthetic-missing" to syntheticExerciseWithoutReviewedMetadata("synthetic-missing")
            ),
            policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
            week = week,
            catalogVersion = SYNTHETIC_CATALOG_VERSION,
            reviewPolicyVersion = 1
        )

        assertThat(ledger.unattributedWorkSets.keys)
            .containsExactly(
                LedgerOmissionReason.UNKNOWN_EXERCISE,
                LedgerOmissionReason.MISSING_REVIEWED_METADATA,
                LedgerOmissionReason.METADATA_NOT_APPROVED
            )
            .inOrder()
    }

    @Test
    fun aSessionThatIsNotCompletedIsRejectedRatherThanSilentlyIgnored() {
        listOf(SessionStatus.IN_PROGRESS, SessionStatus.CANCELLED).forEach { status ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                calculateWith(
                    completedSession(
                        id = "session-1",
                        completedAtEpochMillis = week.startEpochMillis,
                        exercises = listOf(
                            exerciseInstance("synthetic-bench-press", listOf(completedNormalSet()))
                        ),
                        status = status
                    )
                )
            }

            assertWithMessage("rejection for %s", status)
                .that(error).hasMessageThat().contains("status")
        }
    }

    @Test
    fun aCompletionTimestampOutsideTheWeekIsRejectedRatherThanCredited() {
        listOf(
            week.startEpochMillis - 1L,
            week.endEpochMillisExclusive,
            week.endEpochMillisExclusive + 1L
        ).forEach { outsideTheWeek ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                calculateWith(
                    completedSession(
                        id = "session-1",
                        completedAtEpochMillis = outsideTheWeek,
                        exercises = listOf(
                            exerciseInstance("synthetic-bench-press", listOf(completedNormalSet()))
                        )
                    )
                )
            }

            assertWithMessage("rejection for %s", outsideTheWeek)
                .that(error).hasMessageThat().contains("completedAtTimestamp")
        }
    }

    @Test
    fun theExactWeekStartIsIncludedAndTheNextWeekStartIsExcluded() {
        val ledger = calculateWith(
            completedSession(
                id = "session-first-instant",
                completedAtEpochMillis = week.startEpochMillis,
                exercises = listOf(
                    exerciseInstance("synthetic-bench-press", listOf(completedNormalSet()))
                )
            ),
            completedSession(
                id = "session-last-instant",
                completedAtEpochMillis = week.endEpochMillisExclusive - 1L,
                exercises = listOf(
                    exerciseInstance(
                        exerciseId = "synthetic-bench-press",
                        id = "instance-late",
                        sets = listOf(completedNormalSet())
                    )
                )
            )
        )

        assertThat(ledger.directPrimarySets).containsExactly("Chest", 2)
    }

    @Test
    fun duplicateSessionIdentifiersAreRejectedInsteadOfDoubleCounted() {
        val session = completedSession(
            id = "session-1",
            completedAtEpochMillis = week.startEpochMillis,
            exercises = listOf(
                exerciseInstance("synthetic-bench-press", listOf(completedNormalSet()))
            )
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            calculateWith(session, session)
        }

        assertThat(error).hasMessageThat().contains("session id")
    }

    @Test
    fun aBlankCatalogVersionOrNegativeReviewPolicyVersionIsRejected() {
        assertThat(
            assertThrows(IllegalArgumentException::class.java) {
                calculator.calculate(
                    sessions = emptyList(),
                    exercisesById = emptyMap(),
                    policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
                    week = week,
                    catalogVersion = "  ",
                    reviewPolicyVersion = 1
                )
            }
        ).hasMessageThat().contains("catalogVersion")

        assertThat(
            assertThrows(IllegalArgumentException::class.java) {
                calculator.calculate(
                    sessions = emptyList(),
                    exercisesById = emptyMap(),
                    policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
                    week = week,
                    catalogVersion = SYNTHETIC_CATALOG_VERSION,
                    reviewPolicyVersion = -1
                )
            }
        ).hasMessageThat().contains("reviewPolicyVersion")
    }

    @Test
    fun aGenuinelyEmptyWeekProducesAnExplicitEmptyLedgerRatherThanAFailure() {
        val ledger = calculator.calculate(
            sessions = emptyList(),
            exercisesById = emptyMap(),
            policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
            week = week,
            catalogVersion = SYNTHETIC_CATALOG_VERSION,
            reviewPolicyVersion = 1
        )

        assertThat(ledger.directPrimarySets).isEmpty()
        assertThat(ledger.secondaryInvolvement).isEmpty()
        assertThat(ledger.unattributedWorkSets).isEmpty()
        assertThat(ledger.weekStartEpochDay).isEqualTo(MONDAY_EPOCH_DAY)
        assertThat(ledger.timeZoneId).isEqualTo("UTC")
        assertThat(ledger.catalogVersion).isEqualTo(SYNTHETIC_CATALOG_VERSION)
        assertThat(ledger.reviewPolicyVersion).isEqualTo(1)
    }

    @Test
    fun outputIsIdenticalWhateverOrderSessionsExercisesAndSetsArriveIn() {
        val sessions = listOf(
            completedSession(
                id = "session-a",
                completedAtEpochMillis = week.startEpochMillis,
                exercises = listOf(
                    exerciseInstance("synthetic-row", listOf(completedNormalSet()), "a", 0),
                    exerciseInstance(
                        exerciseId = "synthetic-bench-press",
                        sets = listOf(completedNormalSet(), completedNormalSet()),
                        id = "b",
                        orderIndex = 1
                    )
                )
            ),
            completedSession(
                id = "session-b",
                completedAtEpochMillis = week.startEpochMillis + 3_600_000L,
                exercises = listOf(
                    exerciseInstance("synthetic-squat", listOf(completedNormalSet()), "c", 0),
                    exerciseInstance("synthetic-unknown", listOf(completedNormalSet()), "d", 1)
                )
            )
        )

        val forward = calculateOrdered(sessions)
        val reversed = calculateOrdered(
            sessions.reversed().map { session ->
                session.copy(
                    exercises = session.exercises.reversed().map { exercise ->
                        exercise.copy(sets = exercise.sets.reversed())
                    }
                )
            }
        )

        assertThat(reversed).isEqualTo(forward)
        assertThat(reversed.directPrimarySets.keys.toList())
            .isEqualTo(forward.directPrimarySets.keys.toList())
        assertThat(forward.directPrimarySets.keys.toList())
            .containsExactly("Back", "Chest", "Quadriceps")
            .inOrder()
    }

    @Test
    fun recordedEffortAndManageableFeedbackNeverChangeSetCredit() {
        val withoutFeedback = ledgerOf(
            sets = listOf(completedNormalSet(), completedNormalSet())
        )
        val withFeedback = ledgerOf(
            sets = listOf(
                completedNormalSet(rpe = 9.5f, rir = 0, feltManageable = false),
                completedNormalSet(rpe = 4f, rir = 6, feltManageable = true)
            )
        )

        assertThat(withFeedback).isEqualTo(withoutFeedback)
    }

    @Test
    fun identicalInputsReproduceAnIdenticalLedger() {
        val first = ledgerOf(sets = listOf(completedNormalSet(id = "set-fixed-1")))
        val second = ledgerOf(sets = listOf(completedNormalSet(id = "set-fixed-1")))

        assertThat(second).isEqualTo(first)
        assertThat(second.hashCode()).isEqualTo(first.hashCode())
    }

    private fun calculateOrdered(sessions: List<WorkoutSession>): WeeklyDoseLedger =
        calculator.calculate(
            sessions = sessions,
            exercisesById = mapOf(
                "synthetic-bench-press" to
                    syntheticApprovedExercise("synthetic-bench-press", "Chest"),
                "synthetic-row" to syntheticApprovedExercise("synthetic-row", "Back"),
                "synthetic-squat" to syntheticApprovedExercise("synthetic-squat", "Quadriceps")
            ),
            policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
            week = week,
            catalogVersion = SYNTHETIC_CATALOG_VERSION,
            reviewPolicyVersion = 1
        )

    private fun calculateWith(vararg sessions: WorkoutSession): WeeklyDoseLedger =
        calculator.calculate(
            sessions = sessions.toList(),
            exercisesById = mapOf(
                "synthetic-bench-press" to syntheticApprovedExercise(
                    id = "synthetic-bench-press",
                    directPrimaryMuscle = "Chest"
                )
            ),
            policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
            week = week,
            catalogVersion = SYNTHETIC_CATALOG_VERSION,
            reviewPolicyVersion = 1
        )

    private fun ledgerOf(sets: List<WorkoutSet>): WeeklyDoseLedger = calculator.calculate(
        sessions = listOf(
            completedSession(
                id = "session-1",
                completedAtEpochMillis = week.startEpochMillis,
                exercises = listOf(exerciseInstance("synthetic-bench-press", sets))
            )
        ),
        exercisesById = mapOf(
            "synthetic-bench-press" to syntheticApprovedExercise(
                id = "synthetic-bench-press",
                directPrimaryMuscle = "Chest",
                descriptiveSecondaryMuscles = setOf("Triceps")
            )
        ),
        policyVersion = LedgerPolicyVersion.PRIMARY_ONLY_V1,
        week = week,
        catalogVersion = SYNTHETIC_CATALOG_VERSION,
        reviewPolicyVersion = 1
    )
}
