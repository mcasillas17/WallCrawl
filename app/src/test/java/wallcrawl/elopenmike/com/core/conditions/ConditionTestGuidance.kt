package wallcrawl.elopenmike.com.core.conditions

import wallcrawl.elopenmike.com.core.model.AiReviewProvenance

/** A small overlay for presentation tests: one condition with warning signs and notes. */
object ConditionTestGuidance {
    const val CONDITION_ID = "piriformis_syndrome"

    fun loaded(
        exerciseDemands: Map<String, Set<MovementDemand>> = emptyMap(),
        avoid: Set<MovementDemand> = emptySet(),
        modify: Set<MovementDemand> = emptySet(),
        routine: List<RoutineEntry> = emptyList(),
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
                    routine = routine,
                    exerciseCues = exerciseCues,
                    notes = mapOf(
                        "en" to listOf("Ease off if symptoms start."),
                        "es" to listOf("Baja la intensidad si empiezan los síntomas.")
                    ),
                    redFlags = mapOf(
                        "en" to listOf("New numbness around your genitals: call emergency services."),
                        "es" to listOf("Adormecimiento nuevo alrededor de los genitales: llama a emergencias.")
                    ),
                    evidenceGrade = "C",
                    sources = emptyList(),
                    provenance = AiReviewProvenance(
                        reviewerModelId = "test",
                        reviewedAtEpochMillis = 1L,
                        reviewedContentId = "x",
                        reviewedContentSha256 = "0",
                        sourceReferences = emptyList(),
                        decisionRationale = "t",
                        limitations = "t",
                        schemaVersion = 1,
                        policyVersion = 1
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
