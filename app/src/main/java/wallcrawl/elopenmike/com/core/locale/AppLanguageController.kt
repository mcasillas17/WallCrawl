package wallcrawl.elopenmike.com.core.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * The single place the app language is read and written.
 *
 * Both controls — the chip in the onboarding wizard's header, offered on every step, and
 * the segmented pill in Profile → App preferences — go through here, so they share one
 * preference by construction rather than by keeping two copies in sync. There is no local mirror of the value: [current] always asks the platform,
 * which is also what makes a change made in the system per-app language screen show up in
 * the app's own selector.
 *
 * Storage is owned by AndroidX: Android 13+ keeps the choice in the system, and below that
 * the `autoStoreLocales` service declared in the manifest persists it in SharedPreferences.
 * Nothing here writes to the profile or to an archive.
 */
object AppLanguageController {

    /** The language currently in effect, resolving an unshipped tag to [AppLanguage.SYSTEM]. */
    fun current(): AppLanguage =
        AppLanguage.fromLanguageTag(
            AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag()
        )

    /**
     * Applies [language] immediately.
     *
     * AndroidX recreates the running activities, which is why every screen keeps its
     * in-progress input in a ViewModel or a SavedStateHandle: an onboarding answer, the
     * wizard step, and a template being edited all survive the recreation.
     *
     * A repeat of the language already in effect is dropped, so re-selecting the current
     * option never throws away an active screen for no reason.
     */
    fun apply(language: AppLanguage) {
        if (current() == language) return
        AppCompatDelegate.setApplicationLocales(
            if (language == AppLanguage.SYSTEM) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(language.languageTag)
            }
        )
    }
}
