package wallcrawl.elopenmike.com.core.database.repository

import wallcrawl.elopenmike.com.core.model.MuscleDoseAccounting
import wallcrawl.elopenmike.com.core.model.RecommendationRecord

/**
 * The on-disk form of a recommendation record's dose accounting.
 *
 * The format follows the weekly-ledger cache's: a versioned, line-oriented list of
 * `muscle`, `completed`, `proposed`, `allowance` fields rather than JSON. It needs no
 * serialization dependency, sorts into exactly one canonical spelling per accounting, and
 * is small enough to validate field by field.
 *
 * Decoding is strict and total. A record can arrive from an archive anyone could have
 * edited, so a payload that is not exactly well formed decodes as `null` and the caller
 * refuses the whole record. Nothing is coerced into a plausible-looking count, and an
 * absent allowance stays absent rather than becoming zero.
 */
object RecommendationDoseAccountingPayload {

    const val PAYLOAD_HEADER: String = "wallcrawl-recommendation-dose-v1"

    private const val FIELD_SEPARATOR = '\t'
    private const val ABSENT_ALLOWANCE = "none"
    private const val FIELD_COUNT = 4

    fun encode(accounting: List<MuscleDoseAccounting>): String {
        val lines = mutableListOf(PAYLOAD_HEADER)
        // Sorted, so two equal accountings can never serialize two different ways.
        accounting.sortedBy { it.muscle }.forEach { entry ->
            lines += listOf(
                entry.muscle,
                entry.completedSets.toString(),
                entry.proposedSets.toString(),
                entry.allowanceSets?.toString() ?: ABSENT_ALLOWANCE
            ).joinToString(FIELD_SEPARATOR.toString())
        }
        return lines.joinToString("\n")
    }

    /** The accounting [payload] holds, or null when it is not exactly well formed. */
    fun decode(payload: String): List<MuscleDoseAccounting>? {
        val lines = payload.split("\n")
        if (lines.firstOrNull() != PAYLOAD_HEADER) return null
        val entries = lines.drop(1).filter { it.isNotEmpty() }
        if (entries.size > RecommendationRecord.MAX_DOSE_ENTRIES) return null

        val decoded = mutableListOf<MuscleDoseAccounting>()
        val seen = mutableSetOf<String>()
        entries.forEach { line ->
            val fields = line.split(FIELD_SEPARATOR)
            if (fields.size != FIELD_COUNT) return null
            val (muscle, rawCompleted, rawProposed, rawAllowance) = fields

            if (!muscle.isValidMuscle()) return null
            if (!seen.add(muscle)) return null
            val completed = rawCompleted.toIntOrNull() ?: return null
            val proposed = rawProposed.toIntOrNull() ?: return null
            if (completed < 0 || proposed < 0) return null
            val allowance = when (rawAllowance) {
                ABSENT_ALLOWANCE -> null
                else -> (rawAllowance.toIntOrNull() ?: return null).also { if (it <= 0) return null }
            }

            decoded += MuscleDoseAccounting(
                muscle = muscle,
                completedSets = completed,
                proposedSets = proposed,
                allowanceSets = allowance
            )
        }
        return decoded
    }

    private fun String.isValidMuscle(): Boolean =
        isNotBlank() &&
            length <= MuscleDoseAccounting.MAX_MUSCLE_LENGTH &&
            none(Char::isISOControl)
}
