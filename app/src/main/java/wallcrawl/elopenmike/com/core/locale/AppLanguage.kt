package wallcrawl.elopenmike.com.core.locale

import java.util.Locale

/**
 * The language WallCrawl renders its interface in.
 *
 * This is a presentation preference for one device, not a training-profile attribute: it is
 * never written to the profile, never exported in an archive, and a restored archive never
 * changes it. [SYSTEM] means "whatever the device asks for", which falls back to English for
 * any language WallCrawl does not ship.
 *
 * [languageTag] is the BCP 47 tag handed to the platform. Spanish is shipped as the
 * unqualified `es` so every Spanish regional locale — es-MX, es-419, es-ES — resolves to the
 * same neutral Latin American resources instead of falling back to English.
 */
enum class AppLanguage(val languageTag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    SPANISH("es");

    companion object {
        /**
         * The language a stored list of application locales represents.
         *
         * Only the primary entry is consulted, and only its language subtag: the platform
         * may hand back a region-qualified tag the user never typed (`es-MX` from a system
         * picker, say), and a selector that could not recognise it would silently report
         * "System default" while the app was plainly running in Spanish.
         *
         * A tag WallCrawl does not ship reads back as [SYSTEM] rather than being invented
         * into a language, so the selector never claims a translation that does not exist.
         */
        fun fromLanguageTag(tag: String?): AppLanguage {
            val language = tag?.takeIf(String::isNotBlank)
                ?.let { Locale.forLanguageTag(it).language }
                ?.takeIf(String::isNotEmpty)
                ?: return SYSTEM
            return entries.firstOrNull { it != SYSTEM && it.languageTag == language } ?: SYSTEM
        }
    }
}
