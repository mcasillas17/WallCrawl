package wallcrawl.elopenmike.com.core.database.relation

import wallcrawl.elopenmike.com.core.model.EffortTarget
import wallcrawl.elopenmike.com.core.model.RestClass
import wallcrawl.elopenmike.com.core.model.RestTargetSource

internal fun persistedEffortTarget(
    minRir: Int?,
    maxRir: Int?,
    owner: String
): EffortTarget? = when {
    minRir == null && maxRir == null -> null
    minRir == null -> error("$owner has effortMaxRir without effortMinRir.")
    maxRir == null -> error("$owner has effortMinRir without effortMaxRir.")
    else -> EffortTarget(minRir = minRir, maxRir = maxRir)
}

internal fun requireCompletePersistedRestTarget(
    restClass: RestClass?,
    restTargetSource: RestTargetSource?,
    owner: String
) {
    check((restClass == null) == (restTargetSource == null)) {
        "$owner must store restClass and restTargetSource together."
    }
}
