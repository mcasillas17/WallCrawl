package wallcrawl.elopenmike.com.feature.templates

import androidx.annotation.StringRes
import java.util.Locale
import java.util.UUID
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.core.ai.StateBasedTrainingPolicyDefaults
import wallcrawl.elopenmike.com.core.model.EffortTarget
import wallcrawl.elopenmike.com.core.model.ExercisePrescription
import wallcrawl.elopenmike.com.core.model.ExerciseType
import wallcrawl.elopenmike.com.core.model.MeasurementPrecision
import wallcrawl.elopenmike.com.core.model.WeightUnit
import wallcrawl.elopenmike.com.core.model.WorkoutTemplate
import wallcrawl.elopenmike.com.core.model.convertWeightForEditing
import wallcrawl.elopenmike.com.core.model.roundForEditing
import wallcrawl.elopenmike.com.core.model.PlannedExercise
import wallcrawl.elopenmike.com.core.model.RepRange
import wallcrawl.elopenmike.com.core.model.RestClass
import wallcrawl.elopenmike.com.core.model.RestTargetSource
import wallcrawl.elopenmike.com.core.model.UserRestPreference
import wallcrawl.elopenmike.com.core.ui.format.LocaleFormatting

/** One editable measurement of a prescription, and the anchor for its error message. */
enum class DraftField {
    SETS,
    REP_MIN,
    REP_MAX,
    LOAD,
    ASSISTANCE,
    DURATION,
    DISTANCE,
    REST,
    EFFORT_MIN,
    EFFORT_MAX
}

/** The outcome of reading a draft row: a domain object, or what is wrong and where. */
sealed interface DraftValidation {
    data class Valid(val exercise: PlannedExercise) : DraftValidation

    /**
     * [errors] anchors a message to the field that caused it. [message] carries the rare
     * whole-row rejection that belongs to no single field.
     */
    data class Invalid(
        val errors: Map<DraftField, Int>,
        @StringRes val message: Int? = null
    ) : DraftValidation
}

/**
 * One exercise row while it is being edited.
 *
 * Every measurement is the text the user typed, not a number. `ExercisePrescription`
 * validates in its constructor, so a field holding `"12."`, `","` or nothing at all has
 * nowhere else to live; keeping the text here is what lets someone type a decimal without
 * the half-typed keystroke either crashing construction or being silently reset.
 *
 * [rowId] is the row's identity and does not change when the row moves, so two rows of the
 * same exercise stay independent and an in-flight edit never retargets after a reorder.
 */
data class TemplateExerciseDraft(
    val rowId: String = UUID.randomUUID().toString(),
    val exerciseId: String,
    val exerciseType: ExerciseType,
    val sets: String = "",
    val repMin: String = "",
    val repMax: String = "",
    val load: String = "",
    val assistance: String = "",
    val durationSeconds: String = "",
    val distanceMeters: String = "",
    val restSeconds: String = "",
    val effortMinRir: String = "",
    val effortMaxRir: String = "",
    val notes: String = "",
    /**
     * Whether the rest value was changed in this editing session. A rest number the user
     * chose must not keep provenance saying product policy produced it.
     */
    val restWasEdited: Boolean = false,
    val originalRestClass: RestClass? = null,
    val originalRestTargetSource: RestTargetSource? = null,
    /**
     * The exact text [load] and [assistance] held in the other unit before the last unit
     * switch, or null. Converting back restores it instead of re-converting rounded text, so
     * looking at a template in the other unit and switching back changes nothing. It is only
     * restored while the field still holds exactly what that switch produced; a different
     * number typed since is converted instead.
     */
    val loadBeforeConversion: String? = null,
    val assistanceBeforeConversion: String? = null,
    /**
     * The rest text the row was loaded or added with. A rest value counts as the user's
     * choice only while it differs from this, so changing it in one sheet and typing it back
     * in a later one leaves the original classification alone.
     */
    val originalRestSeconds: String = ""
) {

    /**
     * Reads the row as a [PlannedExercise], or reports every invalid field at once.
     *
     * Only the fields [fieldsFor] offers for this exercise type are read. Text left in a
     * field the type does not support is ignored rather than passed on, so changing what
     * is on screen can never assemble a combination the domain forbids.
     *
     * [weightUnit] is the unit the draft's loads are in; it sets their upper bound, which
     * [WorkoutTemplate.maxLoad] keeps representable in either unit.
     */
    fun validate(weightUnit: WeightUnit?): DraftValidation {
        val errors = linkedMapOf<DraftField, Int>()
        val fields = fieldsFor(exerciseType)

        val targetSets = readInt(DraftField.SETS, sets, errors)
        val rest = readInt(DraftField.REST, restSeconds, errors)

        val repRange = if (DraftField.REP_MIN in fields) {
            readOrderedPair(DraftField.REP_MIN, repMin, DraftField.REP_MAX, repMax, errors)
                ?.let { (min, max) -> RepRange(min, max) }
        } else {
            null
        }
        val weight = if (DraftField.LOAD in fields) {
            readDecimal(DraftField.LOAD, load, weightUnit, errors)
        } else {
            null
        }
        val assistanceWeight = if (DraftField.ASSISTANCE in fields) {
            readDecimal(DraftField.ASSISTANCE, assistance, weightUnit, errors)
        } else {
            null
        }
        val duration = if (DraftField.DURATION in fields) {
            readInt(DraftField.DURATION, durationSeconds, errors)
        } else {
            null
        }
        val distance = if (DraftField.DISTANCE in fields) {
            readDecimal(DraftField.DISTANCE, distanceMeters, weightUnit, errors)
        } else {
            null
        }
        if (exerciseType == ExerciseType.DISTANCE_DURATION &&
            duration == null &&
            distance == null &&
            DraftField.DURATION !in errors &&
            DraftField.DISTANCE !in errors
        ) {
            errors[DraftField.DISTANCE] = R.string.draft_error_distance_or_duration
            errors[DraftField.DURATION] = R.string.draft_error_distance_or_duration
        }

        val effort = readEffortTarget(errors)

        if (errors.isNotEmpty() || targetSets == null || rest == null) {
            return DraftValidation.Invalid(errors)
        }

        // Per-field validation above already covers every rule the constructor enforces.
        // This still goes through the domain type rather than around it, so the domain
        // stays the last word on what a prescription may be.
        return runCatching {
            val prescription = ExercisePrescription(
                exerciseType = exerciseType,
                targetSets = targetSets,
                repRange = repRange,
                targetWeight = weight,
                targetAssistanceWeight = assistanceWeight,
                targetDurationSeconds = duration,
                targetDistanceMeters = distance,
                restSeconds = rest,
                effortTarget = effort,
                restClass = originalRestClass,
                restTargetSource = originalRestTargetSource
            )
            PlannedExercise(
                exerciseId = exerciseId,
                prescription = if (restWasEdited) {
                    prescription.withUserRestPreference(
                        UserRestPreference(restClass = nearestRestClass(rest), restSeconds = rest)
                    )
                } else {
                    prescription
                },
                notes = notes.trim()
            )
        }.fold(
            onSuccess = { DraftValidation.Valid(it) },
            onFailure = {
                DraftValidation.Invalid(
                    errors = emptyMap(),
                    message = R.string.draft_error_invalid_combination
                )
            }
        )
    }

    /**
     * This row with its load and assistance text expressed in [to] instead of [from],
     * rounded half-up to the editable precision. Blank stays blank. Null when either field
     * holds text that is not a number, which has no value to convert.
     */
    fun convertedLoads(from: WeightUnit, to: WeightUnit, locale: Locale): TemplateExerciseDraft? {
        fun String.converted(from: WeightUnit, to: WeightUnit): String? {
            if (isBlank()) return this
            // Refused like unparseable text: rounding it here would accept a precision
            // validation refuses.
            if (hasTooManyDecimals(this)) return null
            val value = LocaleFormatting.parseDecimalInput(this) ?: return null
            return LocaleFormatting.formatEditableDecimal(
                convertWeightForEditing(value, from, to),
                locale
            )
        }
        // There are two units, so a remembered value is always in the one being switched to.
        // It still stands only if converting it forward again gives what the field shows.
        fun String.restoredFrom(anchor: String?): String? {
            val expected = anchor?.converted(to, from) ?: return null
            val unchanged = if (isBlank() || expected.isBlank()) {
                isBlank() && expected.isBlank()
            } else {
                LocaleFormatting.parseDecimalInput(this) == LocaleFormatting.parseDecimalInput(expected)
            }
            return anchor.takeIf { unchanged }
        }
        if (from == to) return this
        val restoredLoad = load.restoredFrom(loadBeforeConversion)
        val restoredAssistance = assistance.restoredFrom(assistanceBeforeConversion)
        return copy(
            load = restoredLoad ?: load.converted(from, to) ?: return null,
            assistance = restoredAssistance ?: assistance.converted(from, to) ?: return null,
            loadBeforeConversion = if (restoredLoad == null) load else null,
            assistanceBeforeConversion = if (restoredAssistance == null) assistance else null
        )
    }

    /** Whether a load or assistance in this row is larger than [unit] allows. */
    fun exceedsLoadBound(unit: WeightUnit): Boolean = listOf(load, assistance).any { text ->
        LocaleFormatting.parseDecimalInput(text)?.let { it > maxLoad(unit) } == true
    }

    /**
     * The row as "has anything changed?" sees it: identity and remembered pre-switch values
     * are bookkeeping, not content.
     */
    fun comparable(): TemplateExerciseDraft {
        // Numbers compare as numbers, so "090" or "60.0" typed over "90" or "60" is no change.
        fun String.canonical() = LocaleFormatting.parseDecimalInput(this)?.toString() ?: this
        return copy(
            rowId = "",
            loadBeforeConversion = null,
            assistanceBeforeConversion = null,
            sets = sets.canonical(),
            repMin = repMin.canonical(),
            repMax = repMax.canonical(),
            load = load.canonical(),
            assistance = assistance.canonical(),
            durationSeconds = durationSeconds.canonical(),
            distanceMeters = distanceMeters.canonical(),
            restSeconds = restSeconds.canonical(),
            effortMinRir = effortMinRir.canonical(),
            effortMaxRir = effortMaxRir.canonical(),
            originalRestSeconds = originalRestSeconds.canonical(),
            // Saved trimmed, so compared trimmed.
            notes = notes.trim()
        )
    }

    /** The row as one saved-state record. See [decode] for the shape. */
    fun encode(): String = listOf(
        rowId,
        exerciseId,
        exerciseType.name,
        sets,
        repMin,
        repMax,
        load,
        assistance,
        durationSeconds,
        distanceMeters,
        restSeconds,
        effortMinRir,
        effortMaxRir,
        sanitize(notes),
        restWasEdited.toString(),
        originalRestClass?.name.orEmpty(),
        originalRestTargetSource?.name.orEmpty(),
        loadBeforeConversion.encodeNullable(),
        assistanceBeforeConversion.encodeNullable(),
        originalRestSeconds
    ).joinToString(FIELD_SEPARATOR.toString())

    /** A whole-number field, held to [rule]: required or optional, and its bounds. */
    private fun readInt(field: DraftField, text: String, errors: MutableMap<DraftField, Int>): Int? {
        val rule = rule(field, exerciseType, weightUnit = null)
        if (text.isBlank()) {
            if (!rule.isOptional) errors[field] = R.string.draft_error_required
            return null
        }
        // parseIntInput refuses a separator, a non-digit, an over-long entry and an
        // overflowing one, so "9999999999" is reported rather than wrapping to a number.
        val value = LocaleFormatting.parseIntInput(text)
        if (value == null) {
            errors[field] = unparsedError(text, COMPLETE_WHOLE_NUMBER)
            return null
        }
        if (value.toDouble() !in rule.minimum..rule.maximum) {
            errors[field] = R.string.draft_error_out_of_range_with_range
            return null
        }
        return value
    }

    /** Two whole numbers where the second may not be below the first. */
    private fun readOrderedPair(
        minField: DraftField,
        minText: String,
        maxField: DraftField,
        maxText: String,
        errors: MutableMap<DraftField, Int>
    ): Pair<Int, Int>? {
        val min = readInt(minField, minText, errors)
        val max = readInt(maxField, maxText, errors)
        if (min == null || max == null) return null
        if (max < min) {
            errors[maxField] = R.string.draft_error_range_inverted
            return null
        }
        return min to max
    }

    /**
     * The planned effort target, which is what the user intends to leave in reserve --
     * not the RPE or RIR recorded after a set, which lives on `SetPerformanceInput`.
     *
     * Both fields empty means no target. One empty is reported rather than paired with a
     * guessed second value.
     */
    private fun readEffortTarget(errors: MutableMap<DraftField, Int>): EffortTarget? {
        val minBlank = effortMinRir.isBlank()
        val maxBlank = effortMaxRir.isBlank()
        if (minBlank && maxBlank) return null
        if (minBlank) {
            errors[DraftField.EFFORT_MIN] = R.string.draft_error_effort_pair
            return null
        }
        if (maxBlank) {
            errors[DraftField.EFFORT_MAX] = R.string.draft_error_effort_pair
            return null
        }

        return readOrderedPair(DraftField.EFFORT_MIN, effortMinRir, DraftField.EFFORT_MAX, effortMaxRir, errors)
            ?.let { (min, max) -> EffortTarget(min, max) }
    }

    /**
     * A decimal field; each one may be left empty. Blank means unspecified and stays null;
     * "0" is an explicit zero and is stored as one.
     */
    private fun readDecimal(
        field: DraftField,
        text: String,
        weightUnit: WeightUnit?,
        errors: MutableMap<DraftField, Int>
    ): Double? {
        if (text.isBlank()) return null
        val rule = rule(field, exerciseType, weightUnit)
        // More precision than the field can show would be saved, then shown rounded and
        // written back rounded by the next unrelated save. Checked before parsing, so a
        // too-precise number past the parser's length is still reported as too precise.
        if (COMPLETE_DECIMAL.matches(text.trim()) && hasTooManyDecimals(text)) {
            errors[field] = R.string.draft_error_too_precise
            return null
        }
        val value = LocaleFormatting.parseDecimalInput(text)
        if (value == null) {
            errors[field] = unparsedError(text, COMPLETE_DECIMAL, NUMBER_PREFIX)
            return null
        }
        if (value !in rule.minimum..rule.maximum) {
            errors[field] = R.string.draft_error_out_of_range_with_range
            return null
        }
        return value
    }

    companion object {
        /**
         * Separates the fields of one encoded row. [sanitize] strips it from every field
         * the editor accepts — a numeric keyboard will not produce one, but a paste can —
         * so no value can split its own record.
         */
        private const val FIELD_SEPARATOR = '\u001F'
        private const val ENCODED_FIELD_COUNT = 20
        /** `RepRange` requires a positive minimum; the maximum is the prescription's. */
        internal const val MIN_TARGET_REPS = 1

        /** The largest load or assistance a draft in [weightUnit] accepts. */
        fun maxLoad(weightUnit: WeightUnit?): Double =
            weightUnit?.let(WorkoutTemplate::maxLoad) ?: ExercisePrescription.MAX_WEIGHT

        /** What one field accepts. Validation and the field's hint both read it from here. */
        class FieldRule(
            val minimum: Double,
            val maximum: Double,
            val isOptional: Boolean,
            val isDecimal: Boolean
        )

        /**
         * Duration is required on a `DURATION` exercise and optional on a `DISTANCE_DURATION`
         * one, where the rule is "a distance, a duration, or both", which is why the type is
         * part of the lookup. [weightUnit] sets the load bound, so a kilogram field states
         * and checks 4535.92 rather than 10000.
         */
        fun rule(field: DraftField, exerciseType: ExerciseType, weightUnit: WeightUnit?): FieldRule =
            when (field) {
                DraftField.SETS -> wholeNumber(
                    ExercisePrescription.MIN_TARGET_SETS,
                    ExercisePrescription.MAX_TARGET_SETS,
                    isOptional = false
                )
                DraftField.REP_MIN, DraftField.REP_MAX ->
                    wholeNumber(MIN_TARGET_REPS, ExercisePrescription.MAX_TARGET_REPS, isOptional = false)
                DraftField.LOAD, DraftField.ASSISTANCE ->
                    FieldRule(0.0, maxLoad(weightUnit), isOptional = true, isDecimal = true)
                DraftField.DURATION -> wholeNumber(
                    ExercisePrescription.MIN_DURATION_SECONDS,
                    ExercisePrescription.MAX_DURATION_SECONDS,
                    isOptional = exerciseType == ExerciseType.DISTANCE_DURATION
                )
                DraftField.DISTANCE -> FieldRule(
                    ExercisePrescription.MIN_DISTANCE_METERS,
                    ExercisePrescription.MAX_DISTANCE_METERS,
                    isOptional = true,
                    isDecimal = true
                )
                DraftField.REST -> wholeNumber(
                    ExercisePrescription.MIN_REST_SECONDS,
                    ExercisePrescription.MAX_REST_SECONDS,
                    isOptional = false
                )
                // A pair: both blank is no target, one blank is an error the reader reports.
                DraftField.EFFORT_MIN, DraftField.EFFORT_MAX ->
                    wholeNumber(EffortTarget.MIN_RIR, EffortTarget.MAX_RIR, isOptional = true)
            }

        private fun wholeNumber(minimum: Int, maximum: Int, isOptional: Boolean) =
            FieldRule(minimum.toDouble(), maximum.toDouble(), isOptional, isDecimal = false)

        /** The measurements this exercise type offers, in the order the sheet shows them. */
        fun fieldsFor(exerciseType: ExerciseType): List<DraftField> = when (exerciseType) {
            ExerciseType.WEIGHT_REPS ->
                listOf(DraftField.SETS, DraftField.REP_MIN, DraftField.REP_MAX, DraftField.LOAD)

            ExerciseType.BODYWEIGHT_REPS ->
                listOf(DraftField.SETS, DraftField.REP_MIN, DraftField.REP_MAX)

            ExerciseType.ASSISTED_BODYWEIGHT ->
                listOf(
                    DraftField.SETS,
                    DraftField.REP_MIN,
                    DraftField.REP_MAX,
                    DraftField.ASSISTANCE
                )

            ExerciseType.DURATION -> listOf(DraftField.SETS, DraftField.DURATION)

            ExerciseType.DISTANCE_DURATION ->
                listOf(DraftField.SETS, DraftField.DISTANCE, DraftField.DURATION)
        }

        /**
         * Seeds a row from a saved prescription, rendering each number with [locale]'s
         * decimal mark at the two decimals a field shows. Nothing is converted between
         * units; a stored value with more decimals is shown rounded by [roundForEditing] (a
         * positive one too small to show becomes 0.01, never 0), which the editor discloses
         * ([isRoundedForEditing]). Both Spanish decimal marks read back to the
         * same number.
         */
        fun from(
            exercise: PlannedExercise,
            locale: Locale,
            rowId: String = UUID.randomUUID().toString()
        ): TemplateExerciseDraft {
            val prescription = exercise.prescription
            return TemplateExerciseDraft(
                rowId = rowId,
                exerciseId = exercise.exerciseId,
                exerciseType = prescription.exerciseType,
                sets = LocaleFormatting.formatEditableInt(prescription.targetSets, locale),
                repMin = prescription.repRange?.min.editableInt(locale),
                repMax = prescription.repRange?.max.editableInt(locale),
                load = prescription.targetWeight.editableDecimal(locale),
                assistance = prescription.targetAssistanceWeight.editableDecimal(locale),
                durationSeconds = prescription.targetDurationSeconds.editableInt(locale),
                distanceMeters = prescription.targetDistanceMeters.editableDecimal(locale),
                originalRestSeconds = LocaleFormatting.formatEditableInt(prescription.restSeconds, locale),
                restSeconds = LocaleFormatting.formatEditableInt(prescription.restSeconds, locale),
                effortMinRir = prescription.effortTarget?.minRir.editableInt(locale),
                effortMaxRir = prescription.effortTarget?.maxRir.editableInt(locale),
                notes = exercise.notes,
                restWasEdited = false,
                originalRestClass = prescription.restClass,
                originalRestTargetSource = prescription.restTargetSource
            )
        }

        /** Reads one [encode]d row, or null if the record is not one this build wrote. */
        fun decode(text: String): TemplateExerciseDraft? {
            val parts = text.split(FIELD_SEPARATOR)
            if (parts.size != ENCODED_FIELD_COUNT) return null
            val exerciseType = enumOrNull<ExerciseType>(parts[2]) ?: return null
            val restClass = parts[15].takeIf(String::isNotEmpty)?.let {
                enumOrNull<RestClass>(it) ?: return null
            }
            val restTargetSource = parts[16].takeIf(String::isNotEmpty)?.let {
                enumOrNull<RestTargetSource>(it) ?: return null
            }
            if ((restClass == null) != (restTargetSource == null)) return null
            if (parts[0].isEmpty() || parts[1].isEmpty()) return null
            return TemplateExerciseDraft(
                rowId = parts[0],
                exerciseId = parts[1],
                exerciseType = exerciseType,
                sets = parts[3],
                repMin = parts[4],
                repMax = parts[5],
                load = parts[6],
                assistance = parts[7],
                durationSeconds = parts[8],
                distanceMeters = parts[9],
                restSeconds = parts[10],
                effortMinRir = parts[11],
                effortMaxRir = parts[12],
                notes = parts[13],
                restWasEdited = parts[14].toBooleanStrictOrNull() ?: return null,
                originalRestClass = restClass,
                originalRestTargetSource = restTargetSource,
                loadBeforeConversion = parts[17].decodeNullable(),
                assistanceBeforeConversion = parts[18].decodeNullable(),
                originalRestSeconds = parts[19]
            )
        }

        /**
         * Drops the record separator so no field can split its own saved row. A numeric
         * keyboard will not produce one, but a paste into any field can.
         */
        fun sanitize(value: String): String = value.replace(FIELD_SEPARATOR.toString(), "")

        /**
         * The rest class nearest to a user-chosen number of seconds, read from the one
         * product rest table rather than a second set of thresholds.
         */
        private fun nearestRestClass(seconds: Int): RestClass =
            // The table the planner itself applies (StateBasedTrainingPolicy's default).
            StateBasedTrainingPolicyDefaults.V2.productRestSecondsByClass
                .minBy { (_, policySeconds) -> kotlin.math.abs(policySeconds - seconds) }
                .key

        /** Null is empty; any value, including an empty one, is prefixed so the two differ. */
        private fun String?.encodeNullable(): String = this?.let { "=$it" }.orEmpty()

        private fun String.decodeNullable(): String? = takeIf { it.isNotEmpty() }?.drop(1)

        private fun Int?.editableInt(locale: Locale): String =
            this?.let { LocaleFormatting.formatEditableInt(it, locale) }.orEmpty()

        // Seeded at field precision by the same rule conversion uses, so a stored 0.004 is
        // shown as 0.01, never as an explicit 0 that a later save would write back.
        private fun Double?.editableDecimal(locale: Locale): String =
            this?.let { LocaleFormatting.formatEditableDecimal(roundForEditing(it), locale) }.orEmpty()

        /**
         * Why the parser refused [text]. A number written the way the field takes it — whole
         * for a whole-number field — that is refused only for its sign or size ("-5",
         * "9999999999") is complete but out of range; anything else, including a decimal in
         * a whole-number field, is not a finished value for that field.
         */
        private fun unparsedError(text: String, complete: Regex, prefix: Regex? = null): Int =
            // A number longer than the parser takes: every valid value fits, so it is out of
            // range, even once the field's cap has cut it mid-number. Text that is not shaped
            // like a number stays an unfinished entry whatever its length.
            if (complete.matches(text.trim()) ||
                (prefix != null && text.trim().length > LocaleFormatting.MAX_INPUT_LENGTH && prefix.matches(text.trim()))
            ) {
                R.string.draft_error_out_of_range_with_range
            } else {
                R.string.set_field_incomplete_number
            }

        private val COMPLETE_WHOLE_NUMBER = Regex("-?[0-9]+")
        private val COMPLETE_DECIMAL = Regex("-?[0-9]+([.,][0-9]+)?")
        /** A number possibly cut short: digits, then at most one mark and digits after it. */
        private val NUMBER_PREFIX = Regex("-?[0-9]+([.,][0-9]*)?")

        private fun hasTooManyDecimals(text: String): Boolean {
            val trimmed = text.trim()
            val separator = trimmed.indexOfAny(charArrayOf('.', ','))
            return separator >= 0 &&
                trimmed.length - separator - 1 > MeasurementPrecision.EDITABLE_FRACTION_DIGITS
        }

        /** Whether a field showing [value] shows it rounded. */
        fun isRoundedForEditing(value: Double): Boolean = roundForEditing(value) != value
    }
}

/** The constant named exactly [name], or null: a saved name this build does not know. */
internal inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
    enumValues<T>().firstOrNull { it.name == name }
