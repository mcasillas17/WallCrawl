package wallcrawl.elopenmike.com.core.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.*

class ProgressionEngineTest {
    private val engine = ProgressionEngine()
    private val cases = listOf(
        Case(ExercisePrescription(ExerciseType.WEIGHT_REPS, 2, RepRange(8, 12), targetWeight = 40.0), ProgressionAxis.LOAD),
        Case(ExercisePrescription(ExerciseType.BODYWEIGHT_REPS, 2, RepRange(8, 12)), ProgressionAxis.REP_RANGE),
        Case(ExercisePrescription(ExerciseType.ASSISTED_BODYWEIGHT, 2, RepRange(8, 12), targetAssistanceWeight = 20.0), ProgressionAxis.ASSISTANCE),
        Case(ExercisePrescription(ExerciseType.DURATION, 2, targetDurationSeconds = 45), ProgressionAxis.DURATION),
        Case(ExercisePrescription(ExerciseType.DISTANCE_DURATION, 2, targetDistanceMeters = 100.0), ProgressionAxis.DISTANCE),
        Case(ExercisePrescription(ExerciseType.DISTANCE_DURATION, 2, targetDurationSeconds = 60), ProgressionAxis.DURATION),
        Case(ExercisePrescription(ExerciseType.DISTANCE_DURATION, 2, targetDurationSeconds = 60, targetDistanceMeters = 100.0), ProgressionAxis.DISTANCE)
    )

    @Test
    fun allShapes_changeExactlyOneDeclaredAxis_andReplayIdentically() {
        cases.forEach { (reference, axis) ->
            val sessions = pair(reference)
            val result = evaluate(reference, sessions)
            val expected = when (axis) {
                ProgressionAxis.LOAD -> reference.copy(targetWeight = 42.5)
                ProgressionAxis.REP_RANGE -> reference.copy(repRange = RepRange(9, 13))
                ProgressionAxis.ASSISTANCE -> reference.copy(targetAssistanceWeight = 17.5)
                ProgressionAxis.DURATION -> reference.copy(targetDurationSeconds = reference.targetDurationSeconds!! + 5)
                ProgressionAxis.DISTANCE -> reference.copy(targetDistanceMeters = 125.0)
            }
            assertThat(result.axis).isEqualTo(axis)
            assertThat(result.reason).isEqualTo(ProgressionReason.ADVANCED)
            assertThat(result.referencePrescription).isEqualTo(reference)
            assertThat(result.prescription).isEqualTo(expected)
            assertThat(result.sourceSessionIds).containsExactly("one", "two").inOrder()
            assertThat(evaluate(reference, sessions.reversed())).isEqualTo(result)
            assertThat(evaluate(reference, sessions)).isEqualTo(result)
            assertThat(sessions).isEqualTo(pair(reference))
        }
    }

    @Test
    fun allShapes_requireTwoAttempts_andDoNotBorrowFromAnotherExercise() {
        cases.forEach { (reference, _) ->
            assertHold(reference, emptyList(), ProgressionReason.HOLD_INSUFFICIENT_HISTORY)
            assertHold(reference, pair(reference).take(1), ProgressionReason.HOLD_INSUFFICIENT_HISTORY)
            val unrelated = pair(reference).map { session ->
                session.copy(exercises = session.exercises.map { it.copy(exerciseId = "same-family-peer") })
            }
            assertHold(reference, unrelated, ProgressionReason.HOLD_INSUFFICIENT_HISTORY)
        }
    }

    @Test
    fun allShapes_missingManageableOrEffortIsNeverFavorable() {
        cases.forEach { (reference, _) ->
            assertHold(reference, changeLatestSet(pair(reference)) { it.copy(feltManageable = null) }, ProgressionReason.HOLD_MANAGEABLE_NOT_CONFIRMED)
            assertHold(reference, changeLatestSet(pair(reference)) { it.copy(feltManageable = false) }, ProgressionReason.HOLD_MANAGEABLE_NOT_CONFIRMED)
            assertHold(reference, changeLatestSet(pair(reference)) { it.copy(rir = null, rpe = null) }, ProgressionReason.HOLD_MISSING_EFFORT)
            assertHold(reference, changeLatestSet(pair(reference)) { it.copy(rir = 0) }, ProgressionReason.HOLD_EFFORT_NOT_QUALIFYING)
            assertHold(reference, changeLatestSet(pair(reference)) { it.copy(rpe = 9f, rir = 3) }, ProgressionReason.HOLD_EFFORT_NOT_QUALIFYING)
            val rpeOnly = pair(reference).map { session ->
                session.copy(exercises = session.exercises.map { ex ->
                    ex.copy(sets = ex.sets.map { it.copy(rir = null, rpe = 8f) })
                })
            }
            assertThat(evaluate(reference, rpeOnly).axis).isNotNull()
        }
    }

    @Test
    fun allShapes_stoppedPartialWarmupAndFailureWorkDoesNotQualify() {
        cases.forEach { (reference, _) ->
            assertHold(reference, changeLatestSet(pair(reference)) {
                it.copy(isCompleted = false, completedAtTimestamp = null, feltManageable = null,
                    stoppedAtTimestamp = 21_500, stopReason = SetStopReason.PAIN_STOP)
            }, ProgressionReason.HOLD_INCOMPLETE_WORK)
            assertHold(reference, changeLatestSet(pair(reference)) {
                it.copy(isCompleted = false, completedAtTimestamp = null, feltManageable = null, rir = null)
            }, ProgressionReason.HOLD_INCOMPLETE_WORK)
            assertHold(reference, pair(reference).map { session ->
                session.copy(exercises = session.exercises.map { ex ->
                    ex.copy(sets = ex.sets.map { it.copy(type = SetType.WARMUP) })
                })
            }, ProgressionReason.HOLD_INCOMPLETE_WORK)
            assertHold(reference, changeLatestSet(pair(reference)) { it.copy(type = SetType.FAILURE) },
                ProgressionReason.HOLD_INCOMPLETE_WORK)
            assertHold(reference, pair(reference).mapIndexed { i, s ->
                if (i == 1) s.copy(status = SessionStatus.CANCELLED) else s
            }, ProgressionReason.HOLD_INCOMPLETE_WORK)
        }
    }

    @Test
    fun allShapes_changedTargetsCannotReuseEarlierEvidence() {
        cases.forEach { (reference, _) ->
            val changed = reference.copy(restSeconds = reference.restSeconds + 1)
            assertHold(changed, pair(reference), ProgressionReason.HOLD_TARGETS_CHANGED)
            val changedCount = pair(reference).mapIndexed { i, session ->
                if (i == 0) session.copy(exercises = session.exercises.map { ex ->
                    ex.copy(prescription = ex.prescription.copy(targetSets = 1), sets = ex.sets.take(1))
                }) else session
            }
            assertHold(reference, changedCount, ProgressionReason.HOLD_TARGETS_CHANGED)
        }
    }

    @Test
    fun allShapes_duplicateObservationsAndMalformedTimestampsAreHolds() {
        cases.forEach { (reference, _) ->
            val sessions = pair(reference)
            assertHold(reference, sessions + sessions.first(), ProgressionReason.HOLD_DUPLICATE_OBSERVATION)
            assertHold(reference, changeLatestSession(sessions) { s ->
                s.copy(exercises = s.exercises + s.exercises.first())
            }, ProgressionReason.HOLD_DUPLICATE_OBSERVATION)
            assertHold(reference, changeLatestSet(sessions) { it.copy(id = "two-set-1") },
                ProgressionReason.HOLD_DUPLICATE_OBSERVATION)
            assertHold(reference, changeLatestSet(sessions) { it.copy(completedAtTimestamp = null) },
                ProgressionReason.HOLD_INVALID_OBSERVATION)
            assertHold(reference, changeLatestSet(sessions) { it.copy(completedAtTimestamp = 30_001) },
                ProgressionReason.HOLD_INVALID_OBSERVATION)
            assertHold(reference, changeLatestSession(sessions) { it.copy(completedAtTimestamp = NOW + 1) },
                ProgressionReason.HOLD_INVALID_OBSERVATION)
        }
    }

    @Test
    fun allShapes_missingOrUnderTargetMeasurementsAreHolds() {
        cases.forEach { (reference, axis) ->
            val missing = changeLatestSet(pair(reference)) {
                when (axis) {
                    ProgressionAxis.LOAD -> it.copy(completedWeight = null)
                    ProgressionAxis.REP_RANGE -> it.copy(completedReps = null)
                    ProgressionAxis.ASSISTANCE -> it.copy(completedAssistanceWeight = null)
                    ProgressionAxis.DURATION -> it.copy(completedDurationSeconds = null)
                    ProgressionAxis.DISTANCE -> it.copy(completedDistanceMeters = null)
                }
            }
            assertHold(reference, missing, ProgressionReason.HOLD_INVALID_OBSERVATION)
            val under = changeLatestSet(pair(reference)) {
                when (axis) {
                    ProgressionAxis.LOAD, ProgressionAxis.REP_RANGE, ProgressionAxis.ASSISTANCE ->
                        it.copy(completedReps = reference.repRange!!.max - 1)
                    ProgressionAxis.DURATION -> it.copy(completedDurationSeconds = reference.targetDurationSeconds!! - 1)
                    ProgressionAxis.DISTANCE -> it.copy(completedDistanceMeters = reference.targetDistanceMeters!! - 1)
                }
            }
            assertHold(reference, under, ProgressionReason.HOLD_TARGET_NOT_MET)
        }
    }

    @Test
    fun kilogramAndPoundHistories_comparePhysicalValues_andConvertTheIncrement() {
        listOf(cases[0], cases[2]).forEach { (kg, axis) ->
            val lb = kg.copy(
                targetWeight = kg.targetWeight?.let { convertWeight(it, WeightUnit.KG, WeightUnit.LBS) },
                targetAssistanceWeight = kg.targetAssistanceWeight?.let { convertWeight(it, WeightUnit.KG, WeightUnit.LBS) }
            )
            val mixed = listOf(session("one", kg, 10_000), session("two", lb, 20_000, WeightUnit.LBS))
            val result = evaluate(kg, mixed)
            val pounds = evaluate(lb, mixed, WeightUnit.LBS)
            assertThat(result.axis).isEqualTo(axis)
            assertThat(pounds.axis).isEqualTo(axis)
            val kgValue = if (axis == ProgressionAxis.LOAD) result.prescription.targetWeight!! else result.prescription.targetAssistanceWeight!!
            val lbValue = if (axis == ProgressionAxis.LOAD) pounds.prescription.targetWeight!! else pounds.prescription.targetAssistanceWeight!!
            assertThat(convertWeight(lbValue, WeightUnit.LBS, WeightUnit.KG)).isWithin(1e-8).of(kgValue)
        }
    }

    @Test
    fun changingDisplayUnitsCannotCollapseDistinctHistoricalTargetsIntoProgressionEvidence() {
        for (case in listOf(cases[0], cases[2])) {
            val assisted = case.prescription.exerciseType == ExerciseType.ASSISTED_BODYWEIGHT
            fun pounds(value: Double) = if (assisted) case.prescription.copy(targetAssistanceWeight = value)
                else case.prescription.copy(targetWeight = value)
            val older = pounds(88.18)
            val latest = pounds(88.19)
            val history = listOf(
                session("one", older, 10_000, WeightUnit.LBS),
                session("two", latest, 20_000, WeightUnit.LBS)
            )
            for (unit in WeightUnit.entries) {
                val value = convertWeight(88.19, WeightUnit.LBS, unit)
                val reference = if (assisted) latest.copy(targetAssistanceWeight = value)
                    else latest.copy(targetWeight = value)
                val decision = evaluate(reference, history, unit)
                assertThat(decision.reason).isEqualTo(ProgressionReason.HOLD_TARGETS_CHANGED)
                assertThat(decision.axis).isNull()
                assertThat(decision.prescription).isEqualTo(reference)
            }
        }
    }

    @Test
    fun loggingTheDisplayedConvertedTargetIsComparable_butADifferentDisplayedTargetIsNot() {
        val precise = cases.first().prescription.copy(
            targetWeight = convertWeight(42.5, WeightUnit.KG, WeightUnit.LBS)
        )
        val entered = wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting.parseDecimalInput(
            wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting.formatEditableDecimal(
                precise.targetWeight!!, java.util.Locale.US
            )
        )!!
        val sessions = listOf(session("one", precise, 10_000, WeightUnit.LBS), session("two", precise, 20_000, WeightUnit.LBS))
            .map { s -> s.copy(exercises = s.exercises.map { ex ->
                ex.copy(sets = ex.sets.map { it.copy(completedWeight = entered) })
            }) }
        val reference = precise.copy(targetWeight = entered)
        assertThat(evaluate(reference, sessions, WeightUnit.LBS).axis).isEqualTo(ProgressionAxis.LOAD)
        assertThat(evaluate(reference.copy(targetWeight = entered + 0.01), sessions, WeightUnit.LBS).reason)
            .isEqualTo(ProgressionReason.HOLD_TARGETS_CHANGED)
    }

    @Test
    fun measurementComparisonUsesTheActualLoggerRoundingAtBinaryTies() {
        for (value in listOf(2.675, 1.005, 0.125, 0.375, 2.685)) {
            val entered = wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting.parseDecimalInput(
                wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting.formatEditableDecimal(
                    value, java.util.Locale.US
                )
            )!!
            assertThat(MeasurementPrecision.sameEditableValue(value, entered)).isTrue()
        }
    }

    @Test
    fun distanceComparisonAlsoRespectsTheLoggerRepresentation_withoutChangingDuration() {
        for (case in listOf(cases[4], cases[6])) {
            val precise = case.prescription.copy(targetDistanceMeters = 100.003)
            val reference = precise.copy(targetDistanceMeters = 100.0)
            val history = pair(precise).map { s -> s.copy(exercises = s.exercises.map { ex ->
                ex.copy(sets = ex.sets.map { it.copy(completedDistanceMeters = 100.0) })
            }) }
            val result = evaluate(reference, history)
            assertThat(result.prescription).isEqualTo(reference.copy(targetDistanceMeters = 125.0))
            assertThat(evaluate(reference.copy(targetDistanceMeters = 100.01), history).reason)
                .isEqualTo(ProgressionReason.HOLD_TARGETS_CHANGED)
        }
    }

    @Test
    fun allAxes_holdAtBounds_withoutChangingAnotherField() {
        val bounded = listOf(
            cases[0].prescription.copy(targetWeight = 10_000.0),
            cases[1].prescription.copy(repRange = RepRange(999, 1_000)),
            cases[2].prescription.copy(targetAssistanceWeight = 1.0),
            cases[3].prescription.copy(targetDurationSeconds = 86_400),
            cases[4].prescription.copy(targetDistanceMeters = 1_000_000.0),
            cases[5].prescription.copy(targetDurationSeconds = 86_400),
            cases[6].prescription.copy(targetDistanceMeters = 1_000_000.0)
        )
        bounded.forEach { assertHold(it, pair(it), ProgressionReason.HOLD_AT_BOUND) }
    }

    @Test
    fun unknownLoadOrAssistance_staysUnknown() {
        listOf(cases[0].prescription.copy(targetWeight = null),
            cases[2].prescription.copy(targetAssistanceWeight = null)).forEach {
            assertHold(it, pair(it), ProgressionReason.HOLD_UNKNOWN_LOAD)
        }
    }

    @Test
    fun reconstructionBounds_failLoudlyInsteadOfTruncating() {
        assertThrows(IllegalArgumentException::class.java) {
            evaluate(cases[0].prescription, (1..9).map { session("$it", cases[0].prescription, it * 1_000L) })
        }
    }

    @Test
    fun sourceIdsThatCannotRoundTripThePersistedReasonChannel_areExplicitHolds() {
        val reference = cases.first().prescription
        assertHold(
            reference,
            listOf(session("one|||split", reference, 10_000), session("two", reference, 20_000)),
            ProgressionReason.HOLD_INVALID_OBSERVATION
        )
    }

    @Test
    fun duplicateInstanceIdsInTheCompletedSessionDoNotEstablishEvidence() {
        val reference = cases.first().prescription
        val duplicated = changeLatestSession(pair(reference)) { session ->
            session.copy(exercises = session.exercises + session.exercises.single().copy(exerciseId = "unrelated"))
        }
        assertHold(reference, duplicated, ProgressionReason.HOLD_DUPLICATE_OBSERVATION)
    }

    private fun evaluate(reference: ExercisePrescription, sessions: List<WorkoutSession>, unit: WeightUnit = WeightUnit.KG) =
        engine.evaluate("exercise", reference, sessions, unit, NOW, DIGEST)

    private fun assertHold(reference: ExercisePrescription, sessions: List<WorkoutSession>, reason: ProgressionReason) {
        val result = evaluate(reference, sessions)
        assertThat(result.axis).isNull()
        assertThat(result.reason).isEqualTo(reason)
        assertThat(result.prescription).isEqualTo(reference)
        assertThat(result.referencePrescription).isEqualTo(reference)
    }

    private fun pair(prescription: ExercisePrescription) =
        listOf(session("one", prescription, 10_000), session("two", prescription, 20_000))

    private fun session(id: String, prescription: ExercisePrescription, start: Long, unit: WeightUnit = WeightUnit.KG) =
        WorkoutSession(
            id = id, name = "Fixture", startedAtTimestamp = start,
            completedAtTimestamp = start + 2_000, status = SessionStatus.COMPLETED, weightUnit = unit,
            exercises = listOf(WorkoutExercise(
                id = "$id-exercise", sessionId = id, exerciseId = "exercise", orderIndex = 0,
                prescription = prescription,
                sets = (1..prescription.targetSets).map { number ->
                    WorkoutSet(
                        id = "$id-set-$number", workoutExerciseId = "$id-exercise", setNumber = number,
                        exerciseType = prescription.exerciseType, targetReps = prescription.repRange?.max,
                        completedReps = prescription.repRange?.max, targetWeight = prescription.targetWeight,
                        completedWeight = prescription.targetWeight, targetAssistanceWeight = prescription.targetAssistanceWeight,
                        completedAssistanceWeight = prescription.targetAssistanceWeight,
                        targetDurationSeconds = prescription.targetDurationSeconds,
                        completedDurationSeconds = prescription.targetDurationSeconds,
                        targetDistanceMeters = prescription.targetDistanceMeters,
                        completedDistanceMeters = prescription.targetDistanceMeters,
                        isCompleted = true, rir = 3, feltManageable = true, completedAtTimestamp = start + number * 500
                    )
                }
            ))
        )

    private fun changeLatestSession(sessions: List<WorkoutSession>, change: (WorkoutSession) -> WorkoutSession) =
        sessions.dropLast(1) + change(sessions.last())

    private fun changeLatestSet(sessions: List<WorkoutSession>, change: (WorkoutSet) -> WorkoutSet) =
        changeLatestSession(sessions) { s -> s.copy(exercises = s.exercises.map { ex ->
            ex.copy(sets = ex.sets.dropLast(1) + change(ex.sets.last()))
        }) }

    private data class Case(val prescription: ExercisePrescription, val axis: ProgressionAxis)

    companion object {
        private const val NOW = 30_000L
        private val DIGEST = "a".repeat(64)
    }
}
