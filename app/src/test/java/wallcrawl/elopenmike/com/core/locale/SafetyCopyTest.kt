package wallcrawl.elopenmike.com.core.locale

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Test
import wallcrawl.elopenmike.com.core.ai.PlannerFeatureFlags
import org.w3c.dom.Element

/**
 * Holds every language to the same claim boundary.
 *
 * A translation is the easiest place for a safety claim to grow: "conservative volume"
 * becomes "protects your joints", and nothing in the build notices. These checks run over
 * the shipped resource files in both languages, so wording that promises injury prevention,
 * a diagnosis, or a medical outcome fails here rather than shipping.
 *
 * The forbidden lists are the vocabulary of a claim WallCrawl does not make, not a
 * blocklist of unpleasant words: `docs/architecture.md` and `ROADMAP.md` set the boundary
 * these enforce — product defaults, never physiological or medical assertions.
 */
class SafetyCopyTest {

    private val english = readStrings(ENGLISH_FILE)
    private val spanish = readStrings(SPANISH_FILE)
    private val translatedExerciseText = readTranslatedExerciseText(OVERLAY_FILE)

    @Test
    fun bothResourceFilesWereActuallyRead() {
        assertThat(english).isNotEmpty()
        assertThat(spanish).isNotEmpty()
        assertThat(english.keys).containsAtLeast(
            "stop_reason_pain",
            "break_guidance_hiatus",
            "generated_rationale_reentry"
        )
    }

    @Test
    fun aStopReasonSaysOnlyThatTheUserStoppedInEveryLanguage() {
        val stopReasons = listOf(
            "stop_reason_user_skipped",
            "stop_reason_pain",
            "stop_reason_equipment",
            "stop_reason_time",
            "stop_reason_other"
        )
        listOf(english, spanish).forEach { strings ->
            stopReasons.forEach { key ->
                val value = strings.getValue(key)
                assertWithMessage(key).that(value).isNotEmpty()
                assertNoDiagnosticLanguage(key, value)
            }
        }
    }

    @Test
    fun noTrainingCopyPromisesAMedicalOutcomeInEitherLanguage() {
        // Every string that talks about volume, breaks, or joints. These describe what
        // WallCrawl plans, never what it will do to a body.
        val claimSensitiveKeys = english.keys.filter { key ->
            key.startsWith("break_guidance_") ||
                key.startsWith("break_range_") ||
                key.startsWith("generated_rationale_") ||
                key.startsWith("onboarding_break_") ||
                key.startsWith("onboarding_safety_") ||
                key.startsWith("profile_break_") ||
                key.startsWith("movement_capability_") ||
                // The Profile twins of the onboarding disclaimers carry the same class of
                // claim and must clear the same boundary as their originals.
                key.startsWith("profile_safety_") ||
                key.startsWith("profile_capability_") ||
                // Planning refusals explain a configured product limit. "This week's planned
                // sets are already covered" is one sentence away from "more would hurt you",
                // and that sentence must never ship in either language.
                key.startsWith("today_error_")
        }
        assertThat(claimSensitiveKeys).isNotEmpty()
        // A prefix that matches nothing would drop its surface from this check in silence, so
        // anchor the two Profile twins added with those prefixes.
        assertThat(claimSensitiveKeys)
            .containsAtLeast("profile_safety_description", "profile_capability_description")

        listOf(english, spanish).forEach { strings ->
            claimSensitiveKeys.forEach { key ->
                strings[key]?.let { value -> assertNoMedicalPromise(key, value) }
            }
        }
    }

    @Test
    fun theSafetyAndCapabilityCopySaysWhatTheEnabledReviewedPathActuallyDoes() {
        // The forbidden-vocabulary checks above are about medical claims, and a sentence can
        // be untrue without using any of those words: "WallCrawl filters or substitutes
        // high-stress movements" shipped and passed them while the planner ignored
        // TrainingConstraint entirely, and "they do not change current recommendations yet"
        // became false the moment the reviewed path was enabled. This binds the copy to the
        // flag in both directions.
        assertThat(PlannerFeatureFlags.PRODUCTION.reviewedCapabilityEligibility).isTrue()

        // Joint labels and conditions now change what is planned, and the copy says how: the
        // plan is chosen to work around the report, and it promises nothing about outcomes.
        listOf(
            "onboarding_safety_hint", "profile_safety_description"
        ).forEach { key ->
            assertWithMessage("$key (en) must use the app's phrase")
                .that(english.getValue(key)).contains("chosen to work around")
            assertWithMessage("$key (es) must use the app's phrase")
                .that(spanish.getValue(key)).contains("se eligen para sortear")
            assertWithMessage("$key (en) must say it cannot guarantee the outcome")
                .that(english.getValue(key)).contains("cannot guarantee")
            assertWithMessage("$key (es) must say it cannot guarantee the outcome")
                .that(spanish.getValue(key)).contains("no puede garantizar")
            assertWithMessage("$key (en) must not read as a clearance for unflagged exercises")
                .that(english.getValue(key)).contains("is not a statement that it suits you")
            assertWithMessage("$key (es) must not read as a clearance for unflagged exercises")
                .that(spanish.getValue(key)).contains("no significa que te convenga")
            // The statements the earlier cohort made are no longer true.
            assertWithMessage("$key (en) must not say a joint choice leaves no workout")
                .that(english.getValue(key)).doesNotContain("no automatic workout to offer")
            assertWithMessage("$key (es) must not say a joint choice leaves no workout")
                .that(spanish.getValue(key)).doesNotContain("no tendrá entrenamientos automáticos que ofrecer")
        }

        // The reviewed cohort is AI-accepted under owner authorization. Neither surface may
        // describe it as human review or as clinical validation.
        listOf("en" to english, "es" to spanish).forEach { (language, strings) ->
            listOf(
                "onboarding_safety_hint", "profile_safety_description",
                "profile_capability_description", "movement_capability_current_use"
            ).forEach { key ->
                listOf(
                    "human-reviewed", "revisadas por una persona", "revisado por una persona",
                    "clinically", "clínicamente", "clinicamente", "doctor", "physician",
                    "physical therapist", "fisioterapeuta"
                ).forEach { claim ->
                    assertWithMessage("$key ($language) must not claim human or clinical review")
                        .that(strings.getValue(key).lowercase()).doesNotContain(claim)
                }
            }
        }

        // Movement capabilities do change recommendations now, on both surfaces that edit
        // them, so neither may still say they are saved for later.
        listOf("profile_capability_description", "movement_capability_current_use").forEach { key ->
            assertWithMessage("$key (en) must say the settings are read")
                .that(english.getValue(key)).contains("Automatic workouts read these settings")
            assertWithMessage("$key (es) must say the settings are read")
                .that(spanish.getValue(key)).contains("leen estos ajustes")
        }

        // The Profile card also holds the return-after-break selector, which the planner does
        // consume. The disclaimer must name the joint selection, or it reads as a card-level
        // note and understates what the app does with that other input.
        assertWithMessage("the English Profile disclaimer must name what it covers")
            .that(english.getValue("profile_safety_description"))
            .startsWith("Your joint and condition selection")
        assertWithMessage("the Spanish Profile disclaimer must name what it covers")
            .that(spanish.getValue("profile_safety_description"))
            .startsWith("Tu selección de articulaciones y condiciones")
    }

    @Test
    fun aReachableNoAcceptedMetadataRefusalDescribesScopeRatherThanMissingHumanApproval() {
        // `NO_APPROVED_METADATA` is reachable in production: an inventory that satisfies no
        // accepted record leaves the still-pending ones as the last decisions standing. The
        // copy therefore cannot say nothing has been reviewed — 182 records have been — only
        // that nothing matching this user is inside the reviewed automatic scope.
        listOf("en" to english, "es" to spanish).forEach { (language, strings) ->
            REVIEWED_REFUSAL_KEYS.forEach { key ->
                HUMAN_REVIEW_CLAIMS.forEach { claim ->
                    assertWithMessage("$key ($language) must not claim human approval: $claim")
                        .that(strings.getValue(key).lowercase()).doesNotContain(claim)
                }
            }
        }

        assertWithMessage("the English refusal must name the reviewed automatic scope")
            .that(english.getValue("today_error_reviewed_no_approved_metadata"))
            .contains("reviewed automatic scope")
        assertWithMessage("the Spanish refusal must name the reviewed automatic scope")
            .that(spanish.getValue("today_error_reviewed_no_approved_metadata"))
            .contains("alcance automático revisado")
        assertWithMessage("the English constraint refusal must name the reviewed scope too")
            .that(english.getValue("today_error_reviewed_constraints"))
            .contains("reviewed automatic scope")
        assertWithMessage("the Spanish constraint refusal must name the reviewed scope too")
            .that(spanish.getValue("today_error_reviewed_constraints"))
            .contains("alcance automático revisado")

        // Still no medical or diagnostic framing on a refusal a user will actually see.
        listOf(english, spanish).forEach { strings ->
            REVIEWED_REFUSAL_KEYS.forEach { key ->
                assertNoMedicalPromise(key, strings.getValue(key))
                assertNoDiagnosticLanguage(key, strings.getValue(key))
            }
        }
    }

    @Test
    fun theMovementPreferenceCopyClaimsNoSetReductionThePolicyDoesNotMake() {
        // `limitedCapabilityMaxTargetSets` and the per-exercise cap of every adaptation state
        // production can reach are both 2, so a Limited answer removes no set from anything.
        // Claiming otherwise would be the kind of sentence that passes a vocabulary check and
        // is still untrue. `ProductionPlannerCompositionTest` holds the policy side of this.
        listOf("en" to english, "es" to spanish).forEach { (language, strings) ->
            listOf("profile_capability_description", "movement_capability_current_use")
                .forEach { key ->
                    listOf("fewer sets", "menos series", "fewer repetitions", "menos repeticiones")
                        .forEach { claim ->
                            assertWithMessage("$key ($language) must not promise $claim")
                                .that(strings.getValue(key).lowercase()).doesNotContain(claim)
                        }
                }
        }

        assertWithMessage("the English capability copy must name the exclusion")
            .that(english.getValue("movement_capability_current_use"))
            .contains("left out of your recommendations")
        assertWithMessage("the English capability copy must name the no-plan outcome")
            .that(english.getValue("movement_capability_current_use"))
            .contains("WallCrawl says so instead of offering one")
        assertWithMessage("the Spanish capability copy must name the exclusion")
            .that(spanish.getValue("movement_capability_current_use"))
            .contains("queda fuera de tus recomendaciones")
        assertWithMessage("the Spanish capability copy must name the no-plan outcome")
            .that(spanish.getValue("movement_capability_current_use"))
            .contains("WallCrawl lo dice en vez de ofrecer uno")
        assertWithMessage("both capability surfaces must carry the same sentence in English")
            .that(english.getValue("profile_capability_description"))
            .isEqualTo(english.getValue("movement_capability_current_use"))
        assertWithMessage("both capability surfaces must carry the same sentence in Spanish")
            .that(spanish.getValue("profile_capability_description"))
            .isEqualTo(spanish.getValue("movement_capability_current_use"))

        // Every string on both surfaces that offer the selector, not just the hint: a sibling
        // option subtitle is just as able to imply a filter that does not run, and the Profile
        // card edits the same inert setting after onboarding.
        val stepKeys = english.keys.filter {
            it.startsWith("onboarding_safety_") || it.startsWith("profile_safety_")
        }
        assertThat(stepKeys).contains("onboarding_safety_none_subtitle")
        assertThat(stepKeys).contains("profile_safety_description")
        // A regression guard for the exact phrasings that were removed, not a synonym filter.
        // A differently-worded claim would pass; this stops these ones coming back.
        val impliedFiltering = listOf(
            // English
            "filters or substitutes", "joint filters", "without joint",
            // "needs conservative exercise selection" is the same promise in softer words:
            // it says the answer changes what gets selected, and nothing does that yet.
            "conservative exercise selection",
            // The step is a topic label, not a promise that the app protects a joint.
            "protect sensitive joints", "areas to protect",
            // Spanish
            "filtra o cambia", "filtros por articulación", "filtros por articulacion",
            "selección conservadora", "seleccion conservadora",
            "protege las articulaciones", "para proteger"
        )
        listOf("en" to english, "es" to spanish).forEach { (language, strings) ->
            stepKeys.forEach { key ->
                val value = strings[key] ?: return@forEach
                impliedFiltering.forEach { claim ->
                    assertWithMessage("$key ($language) must not imply automatic filtering: $claim")
                        .that(value.lowercase()).doesNotContain(claim)
                }
            }
        }
    }

    @Test
    fun conditionNamesAreAllowlistedAndClaimVocabularyStaysForbidden() {
        // Every condition the overlay ships has a name in both languages, and the names are
        // exactly this list: a new condition is a deliberate edit to it.
        val allowedNames = mapOf(
            "piriformis_syndrome" to ("Piriformis syndrome" to "Síndrome del piriforme"),
            "gluteal_tendinopathy" to ("Gluteal tendinopathy" to "Tendinopatía glútea"),
            "lumbar_radiculopathy" to ("Lumbar radiculopathy" to "Radiculopatía lumbar"),
            "nonspecific_low_back_pain" to ("Nonspecific low back pain" to "Dolor lumbar inespecífico"),
            "rotator_cuff_related_shoulder_pain" to (
                "Rotator cuff-related shoulder pain" to "Dolor de hombro relacionado con el manguito rotador"
                ),
            "lateral_elbow_tendinopathy" to ("Lateral elbow tendinopathy" to "Tendinopatía lateral del codo"),
            "carpal_tunnel_syndrome" to ("Carpal tunnel syndrome" to "Síndrome del túnel carpiano"),
            "patellofemoral_pain" to ("Patellofemoral pain" to "Dolor patelofemoral")
        )
        val shippedIds = (JSONTokener(CONDITION_FILE.readText()).nextValue() as JSONObject)
            .getJSONObject("conditions").keys().asSequence().toSet()
        assertThat(shippedIds).isEqualTo(allowedNames.keys)
        allowedNames.forEach { (id, names) ->
            assertThat(english["condition_name_$id"]).isEqualTo(names.first)
            assertThat(spanish["condition_name_$id"]).isEqualTo(names.second)
        }
        val nameKeys = english.keys.filter { it.startsWith("condition_name_") }.toSet()
        assertThat(nameKeys).isEqualTo(
            allowedNames.keys.map { "condition_name_$it" }.toSet() + "condition_name_unknown"
        )
        assertThat(spanish.keys.filter { it.startsWith("condition_name_") }.toSet()).isEqualTo(nameKeys)

        // Everything else this feature says is held to the claim vocabulary in both languages.
        val featureKeys = english.keys.filter { key ->
            (key.startsWith("condition_") && !key.startsWith("condition_name_")) ||
                key.startsWith("mobility_") || key.startsWith("pain_offer_") ||
                key.startsWith("editor_condition_") ||
                key.startsWith("onboarding_safety_") || key.startsWith("profile_safety_") ||
                key.startsWith("profile_excluded_")
        }
        assertThat(featureKeys.size).isAtLeast(25)
        val claims = listOf(
            // English
            "treat", "heal", "cure", "prevent", "safe", "protect", "rehab", "recover", "therap",
            // Spanish
            "trata", "curar", "sana", "previen", "prevenir", "seguro", "segura",
            "proteg", "rehabilit", "recuper", "terapi"
        )
        listOf("en" to english, "es" to spanish).forEach { (language, strings) ->
            featureKeys.forEach { key ->
                val value = strings.getValue(key).lowercase()
                claims.forEach { claim ->
                    assertWithMessage("$key ($language) must not claim: $claim")
                        .that(value).doesNotContain(claim)
                }
                // "cura" is a claim as a word, but only a fragment of "procura" or "curar".
                assertWithMessage("$key ($language) must not claim: cura")
                    .that(Regex("\\bcura\\b").containsMatchIn(value)).isFalse()
            }
            // A routine is general information and the pain offer promises nothing.
            assertThat(strings.getValue("mobility_today_for").lowercase())
                .containsMatch("(general|médico)")
        }
    }

    @Test
    fun noTranslatedExerciseTextPromisesAMedicalOutcome() {
        // The overlay is the other half of the shipped Spanish: 302 names and 131 coaching
        // summaries that reach the same screens as the resource strings, and that the
        // resource-file checks above cannot see.
        assertThat(translatedExerciseText).isNotEmpty()
        translatedExerciseText.forEach { (key, value) -> assertNoMedicalPromise(key, value) }
    }

    private fun assertNoDiagnosticLanguage(key: String, value: String) {
        val forbidden = listOf(
            // English
            "injur", "diagnos", "symptom", "medical", "pain level",
            // Spanish
            "lesión", "lesion", "diagnóst", "diagnost", "síntoma", "sintoma", "médic", "medic"
        )
        forbidden.forEach { word ->
            assertWithMessage("$key must not read as a diagnosis: $word")
                .that(value.lowercase()).doesNotContain(word)
        }
    }

    private fun assertNoMedicalPromise(key: String, value: String) {
        val forbidden = listOf(
            // English
            "injur", "prevent", "heal", "safely rebuild", "protect your joint",
            "protect joint", "protects your", "tendon", "connective tissue",
            // Spanish
            "lesion", "lesión", "prevenir", "previene", "cura", "sanar",
            "protege tus", "proteger tus articulaciones", "tendón", "tendon",
            "tejido conectivo"
        )
        forbidden.forEach { word ->
            assertWithMessage("$key must not promise a medical outcome: $word")
                .that(value.lowercase()).doesNotContain(word)
        }
    }

    /** Every translated exercise name and coaching summary, keyed for a readable failure. */
    private fun readTranslatedExerciseText(file: File): Map<String, String> {
        val exercises = (JSONTokener(file.readText()).nextValue() as JSONObject)
            .getJSONObject("exercises")
        return buildMap {
            exercises.keys().forEach { id ->
                val languages = exercises.getJSONObject(id)
                languages.keys().forEach { language ->
                    val text = languages.getJSONObject(language)
                    listOf("name", "coachingSummary").forEach { field ->
                        text.optString(field).takeIf(String::isNotBlank)?.let { value ->
                            put("$id.$language.$field", value)
                        }
                    }
                }
            }
        }
    }

    private fun readStrings(file: File): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }.newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        return buildMap {
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as Element
                put(element.getAttribute("name"), element.textContent)
            }
        }
    }

    private companion object {
        /** Every typed reviewed refusal a user can actually be shown. */
        val REVIEWED_REFUSAL_KEYS = listOf(
            "today_error_reviewed_no_approved_metadata",
            "today_error_reviewed_exclusions",
            "today_error_reviewed_equipment",
            "today_error_reviewed_capabilities",
            "today_error_reviewed_constraints",
            "today_error_reviewed_conditions",
            "today_error_condition_guidance_unavailable",
            "today_error_reviewed_calibration",
            "today_error_reviewed_none_eligible"
        )

        /** Phrasings that describe the accepted cohort as human or clinical review. */
        val HUMAN_REVIEW_CLAIMS = listOf(
            "human approval", "human-approved", "human approved", "human-reviewed",
            "aprobados por una persona", "aprobado por una persona",
            "revisadas por una persona", "revisado por una persona",
            "clinically", "clínicamente", "clinicamente"
        )

        val RESOURCE_ROOT: File = generateSequence(File(".").absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/res") }
            .firstOrNull(File::isDirectory)
            ?: File("src/main/res")

        val ENGLISH_FILE = File(RESOURCE_ROOT, "values/strings.xml")
        val SPANISH_FILE = File(RESOURCE_ROOT, "values-es/strings.xml")
        val CONDITION_FILE = File(
            RESOURCE_ROOT.parentFile,
            "assets/conditions/condition-guidance.json"
        )
        val OVERLAY_FILE = File(
            RESOURCE_ROOT.parentFile,
            "assets/localization/exercise-localization.json"
        )
    }
}
