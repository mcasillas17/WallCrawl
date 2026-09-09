package wallcrawl.elopenmike.com.core.model

/** Optional, self-described gender. This is not a training or eligibility input. */
enum class ProfileGender { UNSPECIFIED, WOMAN, MAN, NON_BINARY }

/** Stored only to preserve initial-preview databases and archives; ignored when selecting artwork. */
enum class IllustrationPreference { AUTOMATIC, MALE, FEMALE }

enum class IllustrationVariant(val assetDirectory: String) { MALE("male"), FEMALE("female") }

val UserProfile.illustrationVariant: IllustrationVariant
    get() = if (gender == ProfileGender.WOMAN) {
        IllustrationVariant.FEMALE
    } else {
        IllustrationVariant.MALE
    }
