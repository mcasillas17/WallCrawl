package wallcrawl.elopenmike.com.core.model

/** Stable profile and archive identifiers for the condition reports introduced in alpha.16. */
enum class ReportedCondition(val id: String) {
    PIRIFORMIS_SYNDROME("piriformis_syndrome"),
    GLUTEAL_TENDINOPATHY("gluteal_tendinopathy"),
    LUMBAR_RADICULOPATHY("lumbar_radiculopathy"),
    NONSPECIFIC_LOW_BACK_PAIN("nonspecific_low_back_pain"),
    ROTATOR_CUFF_RELATED_SHOULDER_PAIN("rotator_cuff_related_shoulder_pain"),
    LATERAL_ELBOW_TENDINOPATHY("lateral_elbow_tendinopathy"),
    CARPAL_TUNNEL_SYNDROME("carpal_tunnel_syndrome"),
    PATELLOFEMORAL_PAIN("patellofemoral_pain");

    companion object {
        val ids: Set<String> = entries.map { it.id }.toSet()
    }
}
