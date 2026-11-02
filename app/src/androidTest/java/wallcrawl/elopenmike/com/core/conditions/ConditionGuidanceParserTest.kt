package wallcrawl.elopenmike.com.core.conditions

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import wallcrawl.elopenmike.com.core.model.TrainingConstraint

/** Trust-boundary coverage for the bundled condition overlay, using inline fixtures. */
@RunWith(AndroidJUnit4::class)
class ConditionGuidanceParserTest {

    private val parser = ConditionGuidanceParser()

    @Test
    fun validOverlayParses() {
        val guidance = parser.parse(overlay().reader())

        assertThat(guidance.version).isEqualTo("test-1")
        assertThat(guidance.exerciseDemands["barbell-back-squat"])
            .containsExactly(MovementDemand.HEAVY_EXTERNAL_LOAD, MovementDemand.DEEP_KNEE_FLEXION_LOAD)
        assertThat(guidance.jointLabelDemands[TrainingConstraint.KNEE_SENSITIVE])
            .containsExactly(MovementDemand.DEEP_KNEE_FLEXION_LOAD)
        assertThat(guidance.demandCues[MovementDemand.DEEP_KNEE_FLEXION_LOAD]).containsKey("es")

        val record = guidance.conditions.getValue("patellofemoral_pain")
        assertThat(record.id).isEqualTo("patellofemoral_pain")
        assertThat(record.avoidDemands).containsExactly(MovementDemand.END_RANGE_KNEE_EXTENSION_LOAD)
        assertThat(record.modifyDemands).containsExactly(MovementDemand.DEEP_KNEE_FLEXION_LOAD)
        assertThat(record.routine).containsExactly(
            RoutineEntry("glute-bridge", 2, 10, null, 3, 30),
            RoutineEntry("dead-bug", 2, null, 30, null, 30)
        ).inOrder()
        assertThat(record.exerciseCues["step-up"]).containsEntry("es", "Baja despacio.")
        assertThat(record.notes["en"]).containsExactly("Chosen to work around knee pain.")
        assertThat(record.redFlags["en"]).containsExactly("Seek care for swelling.")
        assertThat(record.provenance.reviewerModelId).isEqualTo("claude-test")
        assertThat(record.provenance.sourceReferences).containsExactly("https://example.org/a")
    }

    @Test
    fun unknownDemandIsRejected() {
        assertRejected(overlay(modify = """["NOT_A_DEMAND"]"""))
    }

    @Test
    fun missingProvenanceIsRejected() {
        assertRejected(overlay(provenance = null))
    }

    @Test
    fun duplicateKeyIsRejected() {
        assertRejected(overlay(extraConditionField = """"evidenceGrade": "B","""))
    }

    @Test
    fun oversizedInputIsRejected() {
        val padding = "x".repeat(2_100_000)
        assertRejected(overlay(version = padding))
    }

    @Test
    fun lowImpactOnlyInJointLabelsIsRejected() {
        assertRejected(overlay(jointLabels = """"LOW_IMPACT_ONLY": ["PLYOMETRIC_IMPACT"]"""))
    }

    @Test
    fun routineEntryWithBothTargetsIsRejected() {
        assertRejected(overlay(routine = entry(reps = "10", duration = "30")))
    }

    @Test
    fun routineEntryWithNeitherTargetIsRejected() {
        assertRejected(overlay(routine = entry(reps = "null", duration = "null")))
    }

    @Test
    fun holdWithoutRepsIsRejected() {
        assertRejected(overlay(routine = entry(reps = "null", duration = "30", hold = "3")))
    }

    @Test
    fun routineEntryWithoutRestIsRejected() {
        assertRejected(overlay(routine = entry(reps = "10", duration = "null", rest = null)))
    }

    @Test
    fun demandInBothAvoidAndModifyIsRejected() {
        assertRejected(
            overlay(
                avoid = """["DEEP_KNEE_FLEXION_LOAD"]""",
                modify = """["DEEP_KNEE_FLEXION_LOAD"]"""
            )
        )
    }

    @Test
    fun emptyConditionsIsRejected() {
        assertRejected(
            overlay().replace(Regex("\"conditions\": \\{.*?\\n          \\},\\n", RegexOption.DOT_MATCHES_ALL), "\"conditions\": {},\n")
        )
    }

    @Test
    fun emptyExerciseDemandsIsRejected() {
        assertRejected(
            overlay().replace(Regex("\"exerciseDemands\": \\{.*?\\n          \\},\\n", RegexOption.DOT_MATCHES_ALL), "\"exerciseDemands\": {},\n")
        )
    }

    @Test
    fun trailingContentIsRejected() {
        assertRejected(overlay() + " {}")
    }

    @Test
    fun nonHttpsSourceIsRejected() {
        assertRejected(overlay(sources = """["http://example.org/a"]"""))
    }

    private fun assertRejected(json: String) {
        assertThrows(ConditionGuidanceFormatException::class.java) { parser.parse(json.reader()) }
    }

    private fun entry(reps: String, duration: String, hold: String = "null", rest: String? = "30") =
        """{"exerciseId": "glute-bridge", "targetSets": 2, "targetReps": $reps,
            "targetDurationSeconds": $duration, "holdSeconds": $hold
            ${rest?.let { ""","restSeconds": $it""" } ?: ""}}"""

    private fun overlay(
        version: String = "test-1",
        avoid: String = """["END_RANGE_KNEE_EXTENSION_LOAD"]""",
        modify: String = """["DEEP_KNEE_FLEXION_LOAD"]""",
        routine: String = """
            {"exerciseId": "glute-bridge", "targetSets": 2, "targetReps": 10,
             "targetDurationSeconds": null, "holdSeconds": 3, "restSeconds": 30},
            {"exerciseId": "dead-bug", "targetSets": 2, "targetReps": null,
             "targetDurationSeconds": 30, "holdSeconds": null, "restSeconds": 30}
        """,
        sources: String = """["https://example.org/a"]""",
        extraConditionField: String = "",
        jointLabels: String = """"KNEE_SENSITIVE": ["DEEP_KNEE_FLEXION_LOAD"]""",
        provenance: String? = """
            {"reviewerModelId": "claude-test", "reviewedAtEpochMillis": 1,
             "reviewedContentId": "patellofemoral_pain",
             "reviewedContentSha256": "${"a".repeat(64)}",
             "sourceReferences": ["https://example.org/a"],
             "decisionRationale": "Reviewed.", "limitations": "None.",
             "schemaVersion": 1, "policyVersion": 1}
        """
    ): String = """
        {
          "schemaVersion": 1,
          "version": "$version",
          "exerciseDemands": {
            "barbell-back-squat": ["HEAVY_EXTERNAL_LOAD", "DEEP_KNEE_FLEXION_LOAD"]
          },
          "conditions": {
            "patellofemoral_pain": {
              "avoidDemands": $avoid,
              "modifyDemands": $modify,
              "routine": [$routine],
              "exerciseCues": {"step-up": {"es": "Baja despacio."}},
              "notes": {"en": ["Chosen to work around knee pain."]},
              "redFlags": {"en": ["Seek care for swelling."]},
              "evidenceGrade": "B",
              $extraConditionField
              "sources": $sources
              ${provenance?.let { ""","provenance": $it""" } ?: ""}
            }
          },
          "jointLabelDemands": {$jointLabels},
          "demandCues": {"DEEP_KNEE_FLEXION_LOAD": {"es": "Reduce la profundidad."}}
        }
    """
}
