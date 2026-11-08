package wallcrawl.elopenmike.com.core.model

/** Null is unknown, including optional answers; selection never infers clinical context. */
class SelectedCondition(val conditionId: String, val contextSchemaVersion: Int, answers: Map<String, String?>) {
    val answers = conditionMap(answers)
    init {
        requireConditionToken(conditionId); require(contextSchemaVersion > 0 && answers.size <= MAX_ANSWERS)
        answers.forEach { (id, answer) -> requireConditionToken(id); answer?.let(::requireConditionToken) }
    }

    companion object { const val MAX_ANSWERS = 32 }

    private fun valueFields(): List<Any?> = listOf(conditionId, contextSchemaVersion, answers)
    override fun equals(other: Any?): Boolean = other is SelectedCondition && valueFields() == other.valueFields()
    override fun hashCode(): Int = valueFields().hashCode()
}
