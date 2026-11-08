package wallcrawl.elopenmike.com.core.ui

import wallcrawl.elopenmike.com.core.model.SelectedCondition

/** Canonical tokens cannot contain these delimiters; null and absent drafts stay distinct. */
internal fun encodeConditionDraft(selections: List<SelectedCondition>): ArrayList<String> {
    require(selections.size <= 32 && selections.map { it.conditionId }.distinct().size == selections.size)
    return ArrayList(selections.map { selection ->
        (listOf(selection.conditionId, selection.contextSchemaVersion.toString()) +
            selection.answers.toSortedMap().map { (key, value) -> "$key=${value.orEmpty()}" }).joinToString(":")
    })
}

internal fun decodeConditionDraft(rows: List<String>): List<SelectedCondition> = rows.map { row ->
    val fields = row.split(':')
    require(fields.size in 2..34)
    SelectedCondition(fields[0], fields[1].toInt(), fields.drop(2).associate { answer ->
        val parts = answer.split('=', limit = 2)
        require(parts.size == 2)
        parts[0] to parts[1].ifEmpty { null }
    })
}.also { require(rows == encodeConditionDraft(it)) { "Noncanonical condition draft." } }
