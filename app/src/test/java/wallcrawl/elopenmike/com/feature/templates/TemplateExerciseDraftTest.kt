package wallcrawl.elopenmike.com.feature.templates

import com.google.common.truth.Truth.assertThat
import java.util.Locale
import org.junit.Test
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.model.EffortTarget
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.RestClass
import wallcrawl.elopenmike.com.core.model.RestTargetSource

/**
 * The draft is the only path from typed text to [ExercisePrescription], so these cover
 * both directions: that every shape the domain accepts can be produced, and that nothing
 * the domain rejects can escape as a quietly substituted default.
 */
class TemplateExerciseDraftTest {

    private val english: Locale = Locale.US
    private val mexico: Locale = Locale.forLanguageTag("es-MX")
    private val spain: Locale = Locale.forLanguageTag("es-ES")

    private fun weightReps() = TemplateExerciseDraft(
        rowId = "row-1",
        exerciseId = "barbell-back-squat",
        exerciseType = ExerciseType.WEIGHT_REPS,
        sets = "3",
        repMin = "8",
        repMax = "12",
        restSeconds = "90"
    )

    private fun valid(draft: TemplateExerciseDraft): PlannedExercise {
        val result = draft.validate(WeightUnit.LBS)
        assertThat(result).isInstanceOf(DraftValidation.Valid::class.java)
        return (result as DraftValidation.Valid).exercise
    }

    private fun errors(draft: TemplateExerciseDraft): Map<DraftField, Int> {
        val result = draft.validate(WeightUnit.LBS)
        assertThat(result).isInstanceOf(DraftValidation.Invalid::class.java)
        return (result as DraftValidation.Invalid).errors
    }

    // --- every supported exercise type ---------------------------------------------

    @Test
    fun weightRepsDraftProducesSetsRangeAndConfirmedLoad() {
        val prescription = valid(weightReps().copy(load = "60")).prescription

        assertThat(prescription.exerciseType).isEqualTo(ExerciseType.WEIGHT_REPS)
        assertThat(prescription.targetSets).isEqualTo(3)
        assertThat(prescription.repRange).isEqualTo(RepRange(8, 12))
        assertThat(prescription.targetWeight).isEqualTo(60.0)
        assertThat(prescription.restSeconds).isEqualTo(90)
    }

    @Test
    fun bodyweightRepsDraftCarriesNoLoadOrAssistanceEvenIfTextIsPresent() {
        val prescription = valid(
            weightReps().copy(
                exerciseType = ExerciseType.BODYWEIGHT_REPS,
                load = "60",
                assistance = "20"
            )
        ).prescription

        assertThat(prescription.targetWeight).isNull()
        assertThat(prescription.targetAssistanceWeight).isNull()
        assertThat(prescription.repRange).isEqualTo(RepRange(8, 12))
    }

    @Test
    fun assistedBodyweightDraftProducesAssistanceRatherThanLoad() {
        val prescription = valid(
            weightReps().copy(
                exerciseType = ExerciseType.ASSISTED_BODYWEIGHT,
                assistance = "20"
            )
        ).prescription

        assertThat(prescription.targetAssistanceWeight).isEqualTo(20.0)
        assertThat(prescription.targetWeight).isNull()
    }

    @Test
    fun durationDraftProducesSecondsAndNoRepRange() {
        val prescription = valid(
            weightReps().copy(
                exerciseType = ExerciseType.DURATION,
                durationSeconds = "45"
            )
        ).prescription

        assertThat(prescription.targetDurationSeconds).isEqualTo(45)
        assertThat(prescription.repRange).isNull()
    }

    @Test
    fun distanceDurationDraftAcceptsEitherTargetAloneOrBoth() {
        val base = weightReps().copy(exerciseType = ExerciseType.DISTANCE_DURATION)

        assertThat(valid(base.copy(distanceMeters = "5000")).prescription.targetDistanceMeters)
            .isEqualTo(5000.0)
        assertThat(valid(base.copy(durationSeconds = "600")).prescription.targetDurationSeconds)
            .isEqualTo(600)

        val both = valid(base.copy(distanceMeters = "5000", durationSeconds = "600")).prescription
        assertThat(both.targetDistanceMeters).isEqualTo(5000.0)
        assertThat(both.targetDurationSeconds).isEqualTo(600)
    }

    @Test
    fun distanceDurationDraftWithNeitherTargetIsRejected() {
        val result = errors(weightReps().copy(exerciseType = ExerciseType.DISTANCE_DURATION))

        assertThat(result.keys).containsExactly(DraftField.DISTANCE, DraftField.DURATION)
        assertThat(result.values.toSet())
            .containsExactly(R.string.draft_error_distance_or_duration)
    }

    // --- unspecified versus explicit zero ------------------------------------------

    @Test
    fun blankLoadIsUnspecifiedAndAnExplicitZeroIsZero() {
        assertThat(valid(weightReps().copy(load = "")).prescription.targetWeight).isNull()
        assertThat(valid(weightReps().copy(load = "   ")).prescription.targetWeight).isNull()
        assertThat(valid(weightReps().copy(load = "0")).prescription.targetWeight).isEqualTo(0.0)
    }

    @Test
    fun blankAssistanceIsUnspecifiedAndAnExplicitZeroIsZero() {
        val base = weightReps().copy(exerciseType = ExerciseType.ASSISTED_BODYWEIGHT)

        assertThat(valid(base).prescription.targetAssistanceWeight).isNull()
        assertThat(valid(base.copy(assistance = "0")).prescription.targetAssistanceWeight)
            .isEqualTo(0.0)
    }

    // --- rejected input -------------------------------------------------------------

    @Test
    fun aRequiredFieldLeftBlankIsReportedAgainstThatFieldAlone() {
        assertThat(errors(weightReps().copy(sets = ""))).containsExactly(
            DraftField.SETS,
            R.string.draft_error_required
        )
        assertThat(errors(weightReps().copy(repMin = ""))).containsExactly(
            DraftField.REP_MIN,
            R.string.draft_error_required
        )
    }

    @Test
    fun aHalfTypedDecimalIsRejectedRatherThanRoundedOrCleared() {
        listOf("60,", "60.", ",5", "1.2.3", "6o").forEach { text ->
            assertThat(errors(weightReps().copy(load = text)))
                .containsExactly(DraftField.LOAD, R.string.set_field_incomplete_number)
        }
    }

    @Test
    fun invertedRepRangeIsReportedOnTheMaximum() {
        assertThat(errors(weightReps().copy(repMin = "12", repMax = "8")))
            .containsExactly(DraftField.REP_MAX, R.string.draft_error_range_inverted)
    }

    @Test
    fun outOfRangeValuesAreRejectedPerField() {
        assertThat(errors(weightReps().copy(sets = "0")))
            .containsExactly(DraftField.SETS, R.string.draft_error_out_of_range_with_range)
        assertThat(errors(weightReps().copy(sets = "21")))
            .containsExactly(DraftField.SETS, R.string.draft_error_out_of_range_with_range)
        assertThat(errors(weightReps().copy(repMax = "1001")))
            .containsExactly(DraftField.REP_MAX, R.string.draft_error_out_of_range_with_range)
        assertThat(errors(weightReps().copy(restSeconds = "1801")))
            .containsExactly(DraftField.REST, R.string.draft_error_out_of_range_with_range)
        assertThat(errors(weightReps().copy(load = "10001")))
            .containsExactly(DraftField.LOAD, R.string.draft_error_out_of_range_with_range)
    }

    @Test
    fun overflowingAndNonFiniteInputIsRejectedWithoutASuccessfulLookingDefault() {
        // Ten digits is the longest input the field accepts, and still overflows Int.
        assertThat(errors(weightReps().copy(sets = "9999999999")).keys)
            .containsExactly(DraftField.SETS)
        assertThat(errors(weightReps().copy(load = "Infinity")).keys)
            .containsExactly(DraftField.LOAD)
        assertThat(errors(weightReps().copy(load = "NaN")).keys)
            .containsExactly(DraftField.LOAD)
        assertThat(errors(weightReps().copy(load = "1e999")).keys)
            .containsExactly(DraftField.LOAD)
    }

    @Test
    fun everyInvalidFieldIsReportedTogetherRatherThanOneAtATime() {
        assertThat(errors(weightReps().copy(sets = "0", repMax = "x")).keys)
            .containsExactly(DraftField.SETS, DraftField.REP_MAX)
    }

    // --- locale ---------------------------------------------------------------------

    @Test
    fun aCommaAndAPointDecimalStoreTheIdenticalValue() {
        assertThat(valid(weightReps().copy(load = "2,5")).prescription.targetWeight)
            .isEqualTo(valid(weightReps().copy(load = "2.5")).prescription.targetWeight)
    }

    @Test
    fun seedingUsesEachReadersDecimalMarkAndEverySeedValidatesToOneValue() {
        // The two Spanish locales WallCrawl ships disagree about the decimal mark -- Mexico
        // writes 2.5 and Spain writes 2,5 -- and the stored number is the same either way.
        val planned = PlannedExercise(
            exerciseId = "barbell-back-squat",
            prescription = ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(8, 12),
                targetWeight = 2.5
            )
        )

        val seeds = mapOf(
            english to "2.5",
            mexico to "2.5",
            spain to "2,5"
        )

        seeds.forEach { (locale, expectedText) ->
            val draft = TemplateExerciseDraft.from(planned, locale, "row-1")

            assertThat(draft.load).isEqualTo(expectedText)
            assertThat(valid(draft)).isEqualTo(planned)
        }
    }

    @Test
    fun seedingKeepsTwoDecimalsAndSaysWhenItRounded() {
        // Editable fields carry the same two decimals the set logger uses. A stored value
        // with more -- an older editor saved converted starting loads unrounded -- is seeded
        // rounded; the editor discloses that, and re-saving writes the rounded number.
        fun seeded(weight: Double) = TemplateExerciseDraft.from(
            PlannedExercise(
                exerciseId = "barbell-back-squat",
                prescription = ExercisePrescription(
                    exerciseType = ExerciseType.WEIGHT_REPS,
                    targetSets = 3,
                    repRange = RepRange(8, 12),
                    targetWeight = weight
                )
            ),
            english,
            "row-1"
        )

        // 100.125 is exactly representable, so this rounds the way the half-up rule reads.
        assertThat(seeded(100.125).load).isEqualTo("100.13")
        // Too small to show is the smallest step, never a stated 0 a save would write back.
        assertThat(seeded(0.004).load).isEqualTo("0.01")
        assertThat(valid(seeded(0.004)).prescription.targetWeight).isEqualTo(0.01)
        assertThat(valid(seeded(100.125)).prescription.targetWeight).isEqualTo(100.13)
        assertThat(seeded(100.001).load).isEqualTo("100")
        assertThat(TemplateExerciseDraft.isRoundedForEditing(100.125)).isTrue()
        assertThat(TemplateExerciseDraft.isRoundedForEditing(61.23496995)).isTrue()
        assertThat(TemplateExerciseDraft.isRoundedForEditing(100.13)).isFalse()
        assertThat(TemplateExerciseDraft.isRoundedForEditing(100.0)).isFalse()
    }

    @Test
    fun convertingTextWithMoreDecimalsThanAFieldShowsIsRefusedLikeUnparseableText() {
        assertThat(weightReps().copy(load = "12.345").convertedLoads(WeightUnit.LBS, WeightUnit.KG, english))
            .isNull()
    }

    // --- rest and effort provenance --------------------------------------------------

    @Test
    fun anEditedRestValueBecomesAUserPreferenceAndDropsPolicyProvenance() {
        val prescription = valid(
            weightReps().copy(
                restSeconds = "45",
                restWasEdited = true,
                originalRestClass = RestClass.LONG,
                originalRestTargetSource = RestTargetSource.PRODUCT_POLICY
            )
        ).prescription

        assertThat(prescription.restSeconds).isEqualTo(45)
        assertThat(prescription.restTargetSource).isEqualTo(RestTargetSource.USER_PREFERENCE)
        assertThat(prescription.restClass).isEqualTo(RestClass.SHORT)
    }

    @Test
    fun anEditedRestValueOnAnUnclassifiedPrescriptionStillGetsAPairedClassAndSource() {
        val prescription = valid(
            weightReps().copy(restSeconds = "180", restWasEdited = true)
        ).prescription

        assertThat(prescription.restClass).isEqualTo(RestClass.LONG)
        assertThat(prescription.restTargetSource).isEqualTo(RestTargetSource.USER_PREFERENCE)
    }

    @Test
    fun untouchedRestKeepsItsOriginalClassAndSourceAsAPair() {
        val kept = valid(
            weightReps().copy(
                originalRestClass = RestClass.MODERATE,
                originalRestTargetSource = RestTargetSource.PRODUCT_POLICY
            )
        ).prescription

        assertThat(kept.restClass).isEqualTo(RestClass.MODERATE)
        assertThat(kept.restTargetSource).isEqualTo(RestTargetSource.PRODUCT_POLICY)

        val unclassified = valid(weightReps()).prescription
        assertThat(unclassified.restClass).isNull()
        assertThat(unclassified.restTargetSource).isNull()
    }

    @Test
    fun bothEffortFieldsBlankMeansNoEffortTargetAndBothFilledMeansOne() {
        assertThat(valid(weightReps()).prescription.effortTarget).isNull()
        assertThat(
            valid(weightReps().copy(effortMinRir = "1", effortMaxRir = "3"))
                .prescription.effortTarget
        ).isEqualTo(EffortTarget(1, 3))
    }

    @Test
    fun oneBlankEffortFieldIsAnErrorRatherThanAGuessedPair() {
        assertThat(errors(weightReps().copy(effortMinRir = "2")))
            .containsExactly(DraftField.EFFORT_MAX, R.string.draft_error_effort_pair)
        assertThat(errors(weightReps().copy(effortMaxRir = "2")))
            .containsExactly(DraftField.EFFORT_MIN, R.string.draft_error_effort_pair)
    }

    @Test
    fun anInvertedOrOutOfRangeEffortPairIsRejected() {
        assertThat(errors(weightReps().copy(effortMinRir = "4", effortMaxRir = "2")).keys)
            .containsExactly(DraftField.EFFORT_MAX)
        assertThat(errors(weightReps().copy(effortMinRir = "0", effortMaxRir = "3")).keys)
            .containsExactly(DraftField.EFFORT_MIN)
        assertThat(errors(weightReps().copy(effortMinRir = "1", effortMaxRir = "11")).keys)
            .containsExactly(DraftField.EFFORT_MAX)
    }

    // --- notes -----------------------------------------------------------------------

    @Test
    fun exerciseNotesSurviveValidationAndAreTrimmed() {
        assertThat(valid(weightReps().copy(notes = "  belt on top sets  ")).notes)
            .isEqualTo("belt on top sets")
    }

    // --- saved-state encoding ---------------------------------------------------------

    @Test
    fun encodeDecodeRoundTripsIncompleteTextRowIdentityAndRestProvenance() {
        val draft = weightReps().copy(
            rowId = "row-9",
            load = "60,",
            notes = "keep this",
            restWasEdited = true,
            originalRestClass = RestClass.LONG,
            originalRestTargetSource = RestTargetSource.PRODUCT_POLICY
        )

        assertThat(TemplateExerciseDraft.decode(draft.encode())).isEqualTo(draft)
    }

    @Test
    fun encodeDecodeRoundTripsAnAbsentRestClassAndSource() {
        assertThat(TemplateExerciseDraft.decode(weightReps().encode())).isEqualTo(weightReps())
    }

    @Test
    fun decodeRejectsMalformedPayloadsInsteadOfInventingARow() {
        listOf(
            "",
            "garbage",
            weightReps().encode() + "\u001Fextra",
            weightReps().encode().replace("WEIGHT_REPS", "NOT_A_TYPE")
        ).forEach { payload ->
            assertThat(TemplateExerciseDraft.decode(payload)).isNull()
        }
    }

    @Test
    fun theEncodedPayloadCannotBeBrokenByASeparatorPastedIntoNotes() {
        // The field separator is the one character that could split a record in two,
        // so notes drop it on the way in rather than decoding later as a different row.
        val draft = weightReps().copy(notes = "before\u001Fafter")
        val decoded = TemplateExerciseDraft.decode(draft.encode())

        assertThat(decoded).isNotNull()
        assertThat(decoded!!.notes).doesNotContain("\u001F")
        assertThat(decoded.rowId).isEqualTo("row-1")
        assertThat(decoded).isEqualTo(draft.copy(notes = decoded.notes))
    }

    // --- field applicability ------------------------------------------------------------

    @Test
    fun eachTypeOffersOnlyTheFieldsItsDomainShapeSupports() {
        assertThat(TemplateExerciseDraft.fieldsFor(ExerciseType.WEIGHT_REPS))
            .containsExactly(DraftField.SETS, DraftField.REP_MIN, DraftField.REP_MAX, DraftField.LOAD)
            .inOrder()
        assertThat(TemplateExerciseDraft.fieldsFor(ExerciseType.BODYWEIGHT_REPS))
            .containsExactly(DraftField.SETS, DraftField.REP_MIN, DraftField.REP_MAX)
            .inOrder()
        assertThat(TemplateExerciseDraft.fieldsFor(ExerciseType.ASSISTED_BODYWEIGHT))
            .containsExactly(
                DraftField.SETS,
                DraftField.REP_MIN,
                DraftField.REP_MAX,
                DraftField.ASSISTANCE
            )
            .inOrder()
        assertThat(TemplateExerciseDraft.fieldsFor(ExerciseType.DURATION))
            .containsExactly(DraftField.SETS, DraftField.DURATION)
            .inOrder()
        assertThat(TemplateExerciseDraft.fieldsFor(ExerciseType.DISTANCE_DURATION))
            .containsExactly(DraftField.SETS, DraftField.DISTANCE, DraftField.DURATION)
            .inOrder()
    }

    @Test
    fun seedingAndValidatingRoundTripsEveryTypeUnchanged() {
        listOf(
            ExercisePrescription(
                exerciseType = ExerciseType.WEIGHT_REPS,
                targetSets = 4,
                repRange = RepRange(4, 6),
                targetWeight = 100.0,
                restSeconds = 120,
                effortTarget = EffortTarget(1, 2)
            ),
            ExercisePrescription(
                exerciseType = ExerciseType.BODYWEIGHT_REPS,
                targetSets = 3,
                repRange = RepRange(8, 15),
                restSeconds = 75
            ),
            ExercisePrescription(
                exerciseType = ExerciseType.ASSISTED_BODYWEIGHT,
                targetSets = 3,
                repRange = RepRange(6, 10),
                targetAssistanceWeight = 25.0
            ),
            ExercisePrescription(
                exerciseType = ExerciseType.DURATION,
                targetSets = 1,
                targetDurationSeconds = 30,
                restSeconds = 15
            ),
            ExercisePrescription(
                exerciseType = ExerciseType.DISTANCE_DURATION,
                targetSets = 1,
                targetDistanceMeters = 5000.0,
                targetDurationSeconds = 1_800,
                restSeconds = 0,
                restClass = RestClass.SHORT,
                restTargetSource = RestTargetSource.USER_PREFERENCE
            )
        ).forEach { prescription ->
            val planned = PlannedExercise("an-exercise", prescription, notes = "note")
            val draft = TemplateExerciseDraft.from(planned, english, "row-1")

            assertThat(valid(draft)).isEqualTo(planned)
        }
    }

    // --- rest classification and units ---------------------------------------------

    @Test
    fun anEditedRestIsBucketedOntoTheProductTableWithTiesToTheShorterClass() {
        fun classFor(seconds: String) = valid(weightReps().copy(restSeconds = seconds, restWasEdited = true))
            .prescription.restClass
        assertThat(classFor("0")).isEqualTo(RestClass.SHORT)
        assertThat(classFor("75")).isEqualTo(RestClass.SHORT)
        assertThat(classFor("76")).isEqualTo(RestClass.MODERATE)
        assertThat(classFor("135")).isEqualTo(RestClass.MODERATE)
        assertThat(classFor("136")).isEqualTo(RestClass.LONG)
        assertThat(classFor("1800")).isEqualTo(RestClass.LONG)
        // The prescribed number is the user's own, not the table's.
        assertThat(valid(weightReps().copy(restSeconds = "0", restWasEdited = true)).prescription.restSeconds)
            .isEqualTo(0)
    }

    @Test
    fun convertingLoadsRoundsHalfUpKeepsBlanksAndRefusesText() {
        val draft = weightReps().copy(load = "100")
        assertThat(draft.convertedLoads(WeightUnit.LBS, WeightUnit.KG, english)?.load).isEqualTo("45.36")
        assertThat(draft.convertedLoads(WeightUnit.LBS, WeightUnit.LBS, english)).isEqualTo(draft)
        assertThat(weightReps().convertedLoads(WeightUnit.LBS, WeightUnit.KG, english)?.load).isEmpty()
        assertThat(weightReps().copy(load = "0").convertedLoads(WeightUnit.KG, WeightUnit.LBS, english)?.load)
            .isEqualTo("0")
        assertThat(draft.copy(load = "12.").convertedLoads(WeightUnit.LBS, WeightUnit.KG, english)).isNull()
        val assisted = TemplateExerciseDraft(
            exerciseId = "assisted-pull-up",
            exerciseType = ExerciseType.ASSISTED_BODYWEIGHT,
            assistance = "20,5"
        )
        // 20.5 kg = 45.1948 lb; a comma reads as the same decimal and writes back in kind.
        assertThat(assisted.convertedLoads(WeightUnit.KG, WeightUnit.LBS, spain)?.assistance)
            .isEqualTo("45,19")
    }

    @Test
    fun theLoadBoundFollowsTheDraftsUnit() {
        val heavy = weightReps().copy(load = "5000")
        assertThat(heavy.validate(WeightUnit.LBS)).isInstanceOf(DraftValidation.Valid::class.java)
        assertThat((heavy.validate(WeightUnit.KG) as DraftValidation.Invalid).errors)
            .containsKey(DraftField.LOAD)
        assertThat(weightReps().copy(load = "4535,92").validate(WeightUnit.KG))
            .isInstanceOf(DraftValidation.Valid::class.java)
    }

    @Test
    fun aDecimalWithMorePrecisionThanTheFieldShowsIsRefusedRatherThanRoundedLater() {
        assertThat(errors(weightReps().copy(load = "12.345"))[DraftField.LOAD])
            .isEqualTo(R.string.draft_error_too_precise)
        assertThat(errors(weightReps().copy(load = "0,004"))[DraftField.LOAD])
            .isEqualTo(R.string.draft_error_too_precise)
        assertThat(valid(weightReps().copy(load = "12,34")).prescription.targetWeight).isEqualTo(12.34)
        assertThat(valid(weightReps().copy(load = "12.5")).prescription.targetWeight).isEqualTo(12.5)
    }

    @Test
    fun theConversionAnchorSurvivesTheSavedStateRoundTrip() {
        val converted = weightReps().copy(load = "135")
            .convertedLoads(WeightUnit.LBS, WeightUnit.KG, english)!!
        assertThat(converted.loadBeforeConversion).isEqualTo("135")
        assertThat(converted.assistanceBeforeConversion).isEqualTo("")
        assertThat(TemplateExerciseDraft.decode(converted.encode())).isEqualTo(converted)
        val withOriginalRest = converted.copy(originalRestSeconds = "90", restSeconds = "45", restWasEdited = true)
        assertThat(TemplateExerciseDraft.decode(withOriginalRest.encode())).isEqualTo(withOriginalRest)
        assertThat(weightReps().loadBeforeConversion).isNull()
        assertThat(TemplateExerciseDraft.decode(weightReps().encode())!!.loadBeforeConversion).isNull()
    }

    @Test
    fun comparingAsNumbersStillSeesEveryRealChange() {
        val sixty = weightReps().copy(load = "60")
        fun same(a: TemplateExerciseDraft, b: TemplateExerciseDraft) = a.comparable() == b.comparable()
        assertThat(same(sixty, sixty.copy(load = "60.0"))).isTrue()
        assertThat(same(sixty, sixty.copy(load = "60,00"))).isTrue()
        assertThat(same(sixty.copy(load = "2,5"), sixty.copy(load = "2.5"))).isTrue()
        assertThat(same(sixty, sixty.copy(load = "60.5"))).isFalse()
        assertThat(same(sixty, sixty.copy(load = "61"))).isFalse()
        // Unknown stays apart from zero, and unfinished text from the number it might become.
        assertThat(same(sixty.copy(load = ""), sixty.copy(load = "0"))).isFalse()
        assertThat(same(sixty, sixty.copy(load = "60."))).isFalse()
        assertThat(same(sixty, sixty.copy(load = "6a"))).isFalse()
        assertThat(same(sixty, sixty.copy(notes = "  "))).isTrue()
        assertThat(same(sixty, sixty.copy(notes = "slow"))).isFalse()
    }

    @Test
    fun aCompleteNumberOutsideWhatAFieldHoldsIsOutOfRangeNotIncomplete() {
        assertThat(errors(weightReps().copy(sets = "9999999999"))[DraftField.SETS])
            .isEqualTo(R.string.draft_error_out_of_range_with_range)
        assertThat(errors(weightReps().copy(load = "-5"))[DraftField.LOAD])
            .isEqualTo(R.string.draft_error_out_of_range_with_range)
        assertThat(errors(weightReps().copy(restSeconds = "-1"))[DraftField.REST])
            .isEqualTo(R.string.draft_error_out_of_range_with_range)
        // Unfinished text is still incomplete.
        assertThat(errors(weightReps().copy(load = "60."))[DraftField.LOAD])
            .isEqualTo(R.string.set_field_incomplete_number)
        assertThat(errors(weightReps().copy(load = "1.234,5"))[DraftField.LOAD])
            .isEqualTo(R.string.set_field_incomplete_number)
        // A decimal in a whole-number field is not a finished value there, whatever its size.
        assertThat(errors(weightReps().copy(sets = "2,5"))[DraftField.SETS])
            .isEqualTo(R.string.set_field_incomplete_number)
        assertThat(errors(weightReps().copy(repMax = "8.5"))[DraftField.REP_MAX])
            .isEqualTo(R.string.set_field_incomplete_number)
        assertThat(errors(weightReps().copy(restSeconds = "90.5"))[DraftField.REST])
            .isEqualTo(R.string.set_field_incomplete_number)
    }
}
