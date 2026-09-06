package wallcrawl.elopenmike.com.core.locale

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppLanguageTest {

    @Test
    fun theSelectorOffersSystemEnglishAndSpanishInOrder() {
        assertThat(AppLanguage.entries)
            .containsExactly(AppLanguage.SYSTEM, AppLanguage.ENGLISH, AppLanguage.SPANISH)
            .inOrder()
    }

    @Test
    fun spanishShipsUnqualifiedSoEveryRegionResolvesToIt() {
        assertThat(AppLanguage.SPANISH.languageTag).isEqualTo("es")
        assertThat(AppLanguage.ENGLISH.languageTag).isEqualTo("en")
        assertThat(AppLanguage.SYSTEM.languageTag).isEmpty()
    }

    @Test
    fun everySpanishRegionalTagReadsBackAsSpanish() {
        listOf("es", "es-MX", "es-419", "es-ES", "es-AR", "es-US").forEach { tag ->
            assertThat(AppLanguage.fromLanguageTag(tag)).isEqualTo(AppLanguage.SPANISH)
        }
    }

    @Test
    fun everyEnglishRegionalTagReadsBackAsEnglish() {
        listOf("en", "en-US", "en-GB").forEach { tag ->
            assertThat(AppLanguage.fromLanguageTag(tag)).isEqualTo(AppLanguage.ENGLISH)
        }
    }

    @Test
    fun anUnsupportedLanguageReadsBackAsSystemRatherThanBeingInvented() {
        listOf("fr", "pt-BR", "de-DE", "ja").forEach { tag ->
            assertThat(AppLanguage.fromLanguageTag(tag)).isEqualTo(AppLanguage.SYSTEM)
        }
    }

    @Test
    fun absentOrBlankTagsReadBackAsSystem() {
        assertThat(AppLanguage.fromLanguageTag(null)).isEqualTo(AppLanguage.SYSTEM)
        assertThat(AppLanguage.fromLanguageTag("")).isEqualTo(AppLanguage.SYSTEM)
        assertThat(AppLanguage.fromLanguageTag("   ")).isEqualTo(AppLanguage.SYSTEM)
    }

    @Test
    fun aMalformedTagReadsBackAsSystemInsteadOfThrowing() {
        listOf("-", "!!", "???", "x").forEach { tag ->
            assertThat(AppLanguage.fromLanguageTag(tag)).isEqualTo(AppLanguage.SYSTEM)
        }
    }

    @Test
    fun aTagCarryingTrailingJunkStillResolvesByItsLeadingLanguage() {
        // Locale.forLanguageTag drops ill-formed trailing subtags rather than failing, so a
        // stored value that somehow arrives as a joined list still reports the language the
        // user is actually reading instead of silently claiming "System default".
        assertThat(AppLanguage.fromLanguageTag("es-MX,en-US")).isEqualTo(AppLanguage.SPANISH)
    }
}
