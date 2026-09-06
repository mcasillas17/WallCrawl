package wallcrawl.elopenmike.com.core.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The archive is machine-readable, so it must not move when the interface language does.
 *
 * A locale reaches serialisation through more paths than it looks: number formatting, case
 * folding of field names, digit shaping, and the hex digits of the checksum itself. If any
 * of them picked up the default locale, an archive written in English would fail its own
 * checksum when read in Spanish — the user would be told their file was damaged. These
 * tests write and read the same archive under several locales and require the bytes, the
 * checksum, and every restored value to be identical.
 */
@RunWith(AndroidJUnit4::class)
class LocalDataArchiveLocaleTest {

    private val defaultLocale: Locale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun theSameArchiveSerialisesToIdenticalBytesInEveryLocale() {
        val documents = LOCALES.associateWith { locale ->
            Locale.setDefault(locale)
            LocalDataArchiveFixtures.archive().toBytes()
        }

        val reference = documents.getValue(LOCALES.first())
        documents.forEach { (locale, bytes) ->
            assertWithMessage("archive bytes under $locale")
                .that(bytes.toList()).isEqualTo(reference.toList())
        }
    }

    @Test
    fun anArchiveWrittenInEnglishRestoresUnchangedUnderSpanish() {
        Locale.setDefault(ENGLISH)
        val original = LocalDataArchiveFixtures.archive()
        val document = original.toBytes()

        Locale.setDefault(DECIMAL_COMMA_SPANISH)
        val restored = LocalDataArchiveCodec.read(ByteArrayInputStream(document))

        // Whole-archive equality, so a single drifted load, timestamp, or enum fails here.
        assertThat(restored).isEqualTo(original)
    }

    @Test
    fun anArchiveWrittenInSpanishRestoresUnchangedUnderEnglish() {
        Locale.setDefault(DECIMAL_COMMA_SPANISH)
        val original = LocalDataArchiveFixtures.archive()
        val document = original.toBytes()

        Locale.setDefault(ENGLISH)
        val restored = LocalDataArchiveCodec.read(ByteArrayInputStream(document))

        assertThat(restored).isEqualTo(original)
    }

    @Test
    fun aDecimalLoadKeepsItsMagnitudeAndItsSerialisedFormAcrossLanguages() {
        // The document is JSON, not display text: a load is written with a decimal point
        // whatever the reader's language writes decimals with.
        Locale.setDefault(DECIMAL_COMMA_SPANISH)
        val document = LocalDataArchiveFixtures.archive().toBytes()
        val expected = loggedLoads(LocalDataArchiveFixtures.archive())

        assertThat(expected).isNotEmpty()
        LOCALES.forEach { locale ->
            Locale.setDefault(locale)
            val restored = LocalDataArchiveCodec.read(ByteArrayInputStream(document))
            assertWithMessage("logged loads under $locale")
                .that(loggedLoads(restored)).isEqualTo(expected)
        }
    }

    private fun loggedLoads(archive: LocalDataArchive): List<Double> =
        archive.snapshot.sessions
            .flatMap { session -> session.exercises }
            .flatMap { exercise -> exercise.sets }
            .mapNotNull { it.completedWeight }

    @Test
    fun theChecksumIsTheSameHexStringInEveryLocale() {
        // Written with %02x. A locale whose default numbering system is not Latin would
        // shape those digits if the formatter localised them, and every archive would then
        // fail verification on the next device.
        val checksums = LOCALES.associateWith { locale ->
            Locale.setDefault(locale)
            CHECKSUM_VALUE
                .find(String(LocalDataArchiveFixtures.archive().toBytes(), Charsets.UTF_8))
                ?.groupValues
                ?.get(1)
                .orEmpty()
        }

        val reference = checksums.getValue(LOCALES.first())
        assertThat(reference).matches("[0-9a-f]{64}")
        checksums.forEach { (locale, checksum) ->
            assertWithMessage("checksum under $locale").that(checksum).isEqualTo(reference)
        }
    }

    private fun LocalDataArchive.toBytes(): ByteArray =
        ByteArrayOutputStream().also { sink -> LocalDataArchiveCodec.write(this) { sink } }
            .toByteArray()

    private companion object {
        val ENGLISH: Locale = Locale.forLanguageTag("en-US")
        val LATIN_AMERICAN_SPANISH: Locale = Locale.forLanguageTag("es-MX")
        val DECIMAL_COMMA_SPANISH: Locale = Locale.forLanguageTag("es-ES")

        /** Includes a locale whose default numbering system is not Latin digits. */
        val ARABIC_INDIC: Locale = Locale.forLanguageTag("ar-EG-u-nu-arab")

        val LOCALES = listOf(ENGLISH, LATIN_AMERICAN_SPANISH, DECIMAL_COMMA_SPANISH, ARABIC_INDIC)

        /** The digest the document carries, whatever whitespace the writer emitted. */
        val CHECKSUM_VALUE = Regex("\"value\"\\s*:\\s*\"([0-9a-fA-F]+)\"")
    }
}
