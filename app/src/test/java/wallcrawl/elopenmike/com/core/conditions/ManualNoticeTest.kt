package wallcrawl.elopenmike.com.core.conditions

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import wallcrawl.elopenmike.com.core.model.UserProfile

class ManualNoticeTest {

    private val profile = UserProfile(reportedConditions = setOf(ConditionTestGuidance.CONDITION_ID))

    private val guidance = ConditionTestGuidance.loaded(
        exerciseDemands = mapOf(
            "avoid-me" to setOf(MovementDemand.PLYOMETRIC_IMPACT),
            "adjust-me" to setOf(MovementDemand.HEAVY_EXTERNAL_LOAD),
            "plain" to emptySet()
        ),
        avoid = setOf(MovementDemand.PLYOMETRIC_IMPACT),
        modify = setOf(MovementDemand.HEAVY_EXTERNAL_LOAD),
        demandCues = mapOf(
            MovementDemand.HEAVY_EXTERNAL_LOAD to mapOf("en" to "Keep the load light.", "es" to "Usa poca carga.")
        )
    )

    @Test
    fun templateEditorWarnsOnRestrictedAndUnreviewedWithoutHiding() {
        val restrictions = restrictionsFor(profile, guidance)

        assertThat(restrictions.manualNoticeFor("avoid-me")).isEqualTo(ManualExerciseNotice.AVOID)
        assertThat(restrictions.manualNoticeFor("adjust-me")).isEqualTo(ManualExerciseNotice.MODIFY)
        assertThat(restrictions.manualNoticeFor("never-reviewed")).isEqualTo(ManualExerciseNotice.UNREVIEWED)
        // Silence is not a claim: an exercise with no restriction simply has no notice.
        assertThat(restrictions.manualNoticeFor("plain")).isNull()

        // The modify exercise's cue is what the editor shows beside the notice, per language.
        assertThat(restrictions.cuesFor("adjust-me", "en")).containsExactly("Keep the load light.")
        assertThat(restrictions.cuesFor("adjust-me", "es")).containsExactly("Usa poca carga.")
    }

    @Test
    fun noRestrictionsMeansNoWarnings() {
        val restrictions = restrictionsFor(UserProfile(), guidance)

        assertThat(restrictions).isEqualTo(ConditionRestrictions.None)
        assertThat(restrictions.manualNoticeFor("avoid-me")).isNull()
        assertThat(restrictions.manualNoticeFor("never-reviewed")).isNull()
    }

    @Test
    fun anUnavailableOverlayGivesNoPerExerciseNotice() {
        val restrictions = restrictionsFor(profile, ConditionGuidanceResult.Unavailable)

        assertThat(restrictions).isEqualTo(ConditionRestrictions.Unavailable)
        assertThat(restrictions.manualNoticeFor("avoid-me")).isNull()
        assertThat(restrictions.manualNoticeFor("never-reviewed")).isNull()
    }

    @Test
    fun cuesComeFromTheLanguageGivenAndNeverChangeTheNotice() {
        val restrictions = restrictionsFor(profile, guidance)

        // Language is display only: the same exercise gets the same notice in any language.
        listOf("en", "es", "fr").forEach { _ ->
            assertThat(restrictions.manualNoticeFor("adjust-me")).isEqualTo(ManualExerciseNotice.MODIFY)
        }
        assertThat(restrictions.cuesFor("adjust-me", "fr")).containsExactly("Keep the load light.")
    }
}
