package wallcrawl.elopenmike.com.core.database

/**
 * Separators the persistence layer reserves inside its joined columns.
 *
 * Several columns store a list, or a map, as one string: equipment, excluded exercises,
 * goals, constraints and focus muscles are joined with [PERSISTED_LIST_SEPARATOR], and
 * muscle priorities and confirmed loads store each entry as `key` + [PERSISTED_PAIR_SEPARATOR]
 * + `value`.
 *
 * They live here, in one place, because the archive's trust boundary rejects these
 * sequences inside restored values. A second copy of either separator would let the guard
 * and the encoding it protects drift apart, and a restored value would then split into
 * entries the document never contained.
 */
internal const val PERSISTED_LIST_SEPARATOR = "|||"

internal const val PERSISTED_PAIR_SEPARATOR = ":"
