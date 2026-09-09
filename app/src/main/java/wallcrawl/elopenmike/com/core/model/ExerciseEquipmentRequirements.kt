package wallcrawl.elopenmike.com.core.model

import java.util.Locale

/**
 * Source-bound equipment corrections, independent of programming and metadata approval.
 * An empty alternatives list means the depicted setup is unresolved, not equipment-free.
 * See docs/band-anchor-equipment.md for the pinned frame evidence and confirmation contract.
 */
internal val fixedAnchorBandRequirements: Map<String, List<List<String>>> = mapOf(
    "banded-face-pull" to listOf(listOf(
        StandardEquipment.RESISTANCE_BAND, StandardEquipment.BAND_ANCHOR_UPPER_BODY
    )),
    "banded-kickback" to listOf(listOf(
        StandardEquipment.RESISTANCE_BAND, StandardEquipment.BAND_ANCHOR_LOW,
        StandardEquipment.BAND_KICKBACK_ATTACHMENT_SUPPORT
    )),
    "banded-lat-pulldown" to listOf(listOf(
        StandardEquipment.RESISTANCE_BAND, StandardEquipment.BAND_ANCHOR_OVERHEAD,
        StandardEquipment.CHAIR
    )),
    "banded-pallof-press" to listOf(listOf(
        StandardEquipment.RESISTANCE_BAND, StandardEquipment.BAND_ANCHOR_UPPER_BODY
    )),
    // Every pinned frame crops out the anchor; no supported height/configuration can be assigned.
    "banded-row" to emptyList(),
    "banded-woodchop" to listOf(listOf(
        StandardEquipment.RESISTANCE_BAND, StandardEquipment.BAND_ANCHOR_LOW
    ))
).also { requirements ->
    require(requirements.keys.all { it.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*")) }) {
        "Fixed-anchor requirements must use canonical exercise IDs."
    }
    require(requirements.values.all { alternatives ->
        alternatives.distinct().size == alternatives.size && alternatives.all { combination ->
            combination.isNotEmpty() && combination.distinct().size == combination.size &&
                combination.all { it in StandardEquipment.ALL } &&
                StandardEquipment.RESISTANCE_BAND in combination
        }
    }) { "Fixed-anchor alternatives must be distinct canonical combinations including a band." }
}

/** Effective legacy/manual requirements; source-listed equipment remains unchanged. */
val Exercise.equipmentRequirements: List<List<String>>
    get() = fixedAnchorBandRequirements[id]
        // Historically an empty legacy matrix imposed no equipment requirement.
        ?: programming?.requiredEquipmentCombinations?.ifEmpty { listOf(emptyList()) }
        ?: listOf(listedEquipment.filter(String::isNotBlank))

val Exercise.hasUnresolvedEquipmentRequirements: Boolean
    get() = fixedAnchorBandRequirements[id]?.isEmpty() == true

fun Exercise.hasRequiredEquipment(availableEquipment: Collection<String>): Boolean =
    equipmentRequirements.isEquipmentSatisfiedBy(availableEquipment.normalizedEquipmentSet())

/**
 * Missing items for each possible setup, or empty when satisfied/unresolved.
 * Callers distinguish an unresolved setup with [hasUnresolvedEquipmentRequirements].
 */
fun Exercise.missingEquipmentAlternatives(availableEquipment: Collection<String>): List<List<String>> {
    val owned = availableEquipment.normalizedEquipmentSet()
    val missing = equipmentRequirements.map { combination ->
        combination.filterNot { it.normalizedEquipment() in owned }
    }
    return if (missing.any(List<String>::isEmpty)) emptyList() else missing.distinct()
}

/** Reviewed approval cannot waive a known source-bound setup minimum. */
internal fun Exercise.hasRequiredFixedAnchorEquipment(ownedEquipment: Set<String>): Boolean =
    fixedAnchorBandRequirements[id]?.isEquipmentSatisfiedBy(ownedEquipment) ?: true

internal fun Collection<String>.normalizedEquipmentSet(): Set<String> =
    mapTo(linkedSetOf()) { it.normalizedEquipment() }

internal fun List<List<String>>.isEquipmentSatisfiedBy(ownedEquipment: Set<String>): Boolean =
    any { combination -> combination.all { it.normalizedEquipment() in ownedEquipment } }

// Confirmation IDs stay exact so every effective selection is visible and revocable in Profile.
// Other equipment retains the legacy case/whitespace matching contract.
private fun String.normalizedEquipment(): String =
    if (this in StandardEquipment.BAND_SETUPS) this else trim().lowercase(Locale.ROOT)
