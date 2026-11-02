package wallcrawl.elopenmike.com.core.conditions

import wallcrawl.elopenmike.com.core.model.AiReviewProvenance

/** One small overlay for screen tests: a condition with an avoid, a modify and a routine cue. */
object ConditionAndroidFixtures {
    const val CONDITION_ID = "piriformis_syndrome"

    fun loaded(
        exerciseDemands: Map<String, Set<MovementDemand>>,
        avoid: Set<MovementDemand> = emptySet(),
        modify: Set<MovementDemand> = emptySet(),
        exerciseCues: Map<String, Map<String, String>> = emptyMap(),
        demandCues: Map<MovementDemand, Map<String, String>> = emptyMap()
    ) = ConditionGuidanceResult.Loaded(
        ConditionGuidance(
            version = "test",
            exerciseDemands = exerciseDemands,
            conditions = mapOf(
                CONDITION_ID to ConditionRecord(
                    id = CONDITION_ID,
                    avoidDemands = avoid,
                    modifyDemands = modify,
                    routine = emptyList(),
                    exerciseCues = exerciseCues,
                    notes = mapOf("en" to listOf("Ease off.")),
                    redFlags = mapOf("en" to listOf("See a clinician.")),
                    evidenceGrade = "C",
                    sources = emptyList(),
                    provenance = AiReviewProvenance(
                        reviewerModelId = "test", reviewedAtEpochMillis = 1L, reviewedContentId = "x",
                        reviewedContentSha256 = "0", sourceReferences = emptyList(),
                        decisionRationale = "t", limitations = "t", schemaVersion = 1, policyVersion = 1
                    )
                )
            ),
            jointLabelDemands = emptyMap(),
            demandCues = demandCues
        )
    )

    fun source(result: ConditionGuidanceResult): ConditionGuidanceSource =
        object : ConditionGuidanceSource {
            override suspend fun guidance() = result
        }
}
