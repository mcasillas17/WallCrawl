package wallcrawl.elopenmike.com.core.ui

import wallcrawl.elopenmike.com.core.model.*

data class ConditionUiState(
    val records: List<ConditionSupportRecord> = emptyList(),
    val loadFailed: Boolean = false,
    val draft: List<SelectedCondition>? = null,
    val saving: Boolean = false,
    val saveFailed: Boolean = false,
    val reportedSaveFailed: Boolean = false,
    val pending: List<PendingConditionReassessment>? = null,
    val pendingFailed: Boolean = false,
    val reassessmentEventId: String? = null,
    val reassessmentAnswers: Map<String, String?> = emptyMap(),
    val reassessmentAnswerLimitEventId: String? = null,
    val reassessmentRefusal: ConditionReassessmentRefused? = null
)
