package wallcrawl.elopenmike.com.core.database.repository

import wallcrawl.elopenmike.com.core.ai.WeeklyDoseLedgerCalculator
import wallcrawl.elopenmike.com.core.model.LedgerOmissionReason
import wallcrawl.elopenmike.com.core.model.LedgerPolicyVersion
import wallcrawl.elopenmike.com.core.model.WeeklyDoseLedger

/**
 * The on-disk form of a cached [WeeklyDoseLedger]'s counts.
 *
 * The format is a versioned, line-oriented list of `kind`, `key`, `count` triples rather
 * than JSON: it needs no serialization dependency, it sorts into exactly one canonical
 * spelling per ledger, and it is small enough to validate field by field.
 *
 * Decoding is strict and total. The cache is derived data that anything on the device could
 * have truncated or edited, so a payload that is not exactly well formed reads back as
 * `null` — no usable cache — and the caller recomputes from completed history. An unknown
 * entry kind or omission reason is never coerced into a known one, so a value this build
 * does not understand can never become credited exposure.
 */
object WeeklyDoseLedgerPayload {

    const val PAYLOAD_HEADER: String = "wallcrawl-weekly-dose-ledger-v1"

    private const val FIELD_SEPARATOR = '\t'
    private const val PRIMARY = "primary"
    private const val SECONDARY = "secondary"
    private const val UNATTRIBUTED = "unattributed"

    /** Bounded well above the catalog's muscle vocabulary and a plausible training week. */
    private const val MAX_ENTRIES = 3 * WeeklyDoseLedgerCalculator.MAX_DISTINCT_MUSCLES
    private const val MAX_KEY_LENGTH = 64

    /**
     * The same per-map cap the calculator enforces on what it produces.
     *
     * Without it the codec would accept a shape the calculator could never emit, so an
     * edited cache could hand a caller a ledger naming far more distinct muscles than any
     * real catalog contains.
     */
    private const val MAX_DISTINCT_MUSCLES = WeeklyDoseLedgerCalculator.MAX_DISTINCT_MUSCLES

    fun encode(ledger: WeeklyDoseLedger): String {
        val lines = mutableListOf(PAYLOAD_HEADER)
        ledger.directPrimarySets.toSortedMap().forEach { (muscle, count) ->
            lines += entry(PRIMARY, muscle, count)
        }
        ledger.secondaryInvolvement.toSortedMap().forEach { (muscle, count) ->
            lines += entry(SECONDARY, muscle, count)
        }
        LedgerOmissionReason.entries.forEach { reason ->
            ledger.unattributedWorkSets[reason]?.let { count ->
                lines += entry(UNATTRIBUTED, reason.name, count)
            }
        }
        return lines.joinToString("\n")
    }

    /**
     * Rebuilds a ledger from [payload] and the provenance columns stored beside it, or
     * returns null when the payload is not exactly well formed.
     */
    fun decode(
        payload: String,
        policyVersion: LedgerPolicyVersion,
        weekStartEpochDay: Long,
        timeZoneId: String,
        catalogVersion: String,
        reviewPolicyVersion: Int
    ): WeeklyDoseLedger? {
        val lines = payload.split("\n")
        if (lines.firstOrNull() != PAYLOAD_HEADER) return null
        val entries = lines.drop(1)
        if (entries.size > MAX_ENTRIES) return null

        val directPrimarySets = sortedMapOf<String, Int>()
        val secondaryInvolvement = sortedMapOf<String, Int>()
        val unattributedWorkSets = sortedMapOf<LedgerOmissionReason, Int>()
        var totalCountedUnits = 0L

        entries.forEach { line ->
            val fields = line.split(FIELD_SEPARATOR)
            if (fields.size != 3) return null
            val (kind, key, rawCount) = fields

            val count = rawCount.toIntOrNull() ?: return null
            if (count < 1 || count > WeeklyDoseLedgerCalculator.MAX_WORK_SETS_PER_WEEK) return null
            // The same bound the calculator enforces on what it produces, so a legitimately
            // produced ledger can always be read back from its own payload.
            totalCountedUnits += count
            if (totalCountedUnits > WeeklyDoseLedgerCalculator.MAX_LEDGER_COUNTED_UNITS) {
                return null
            }

            when (kind) {
                PRIMARY -> {
                    if (!key.isValidMuscleKey()) return null
                    if (directPrimarySets.put(key, count) != null) return null
                    if (directPrimarySets.size > MAX_DISTINCT_MUSCLES) return null
                }

                SECONDARY -> {
                    if (!key.isValidMuscleKey()) return null
                    if (secondaryInvolvement.put(key, count) != null) return null
                    if (secondaryInvolvement.size > MAX_DISTINCT_MUSCLES) return null
                }

                UNATTRIBUTED -> {
                    val reason = LedgerOmissionReason.entries.firstOrNull { it.name == key }
                        ?: return null
                    if (unattributedWorkSets.put(reason, count) != null) return null
                }

                else -> return null
            }
        }

        return WeeklyDoseLedger(
            policyVersion = policyVersion,
            weekStartEpochDay = weekStartEpochDay,
            timeZoneId = timeZoneId,
            catalogVersion = catalogVersion,
            reviewPolicyVersion = reviewPolicyVersion,
            directPrimarySets = directPrimarySets,
            secondaryInvolvement = secondaryInvolvement,
            unattributedWorkSets = LedgerOmissionReason.entries
                .mapNotNull { reason -> unattributedWorkSets[reason]?.let { reason to it } }
                .toMap(LinkedHashMap())
        )
    }

    private fun entry(kind: String, key: String, count: Int): String =
        "$kind$FIELD_SEPARATOR$key$FIELD_SEPARATOR$count"

    private fun String.isValidMuscleKey(): Boolean =
        isNotBlank() &&
            length <= MAX_KEY_LENGTH &&
            none { character -> character.isISOControl() }
}
