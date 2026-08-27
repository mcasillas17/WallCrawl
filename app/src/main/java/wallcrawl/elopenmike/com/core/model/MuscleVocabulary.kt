package wallcrawl.elopenmike.com.core.model

import java.util.Locale

/**
 * The canonical muscle names WallCrawl reasons about.
 *
 * Spellings match the values already written into persisted user profiles, so this
 * vocabulary can be extended but existing entries must not be renamed without a
 * profile migration.
 */
object StandardMuscles {
    const val CHEST = "Chest"
    const val SHOULDERS = "Shoulders"
    const val REAR_DELTS = "Rear Delts"
    const val TRICEPS = "Triceps"
    const val BACK = "Back"
    const val UPPER_BACK = "Upper Back"
    const val LOWER_BACK = "Lower Back"
    const val LATS = "Lats"
    const val BICEPS = "Biceps"
    const val FOREARMS = "Forearms"
    const val QUADS = "Quadriceps"
    const val HAMSTRINGS = "Hamstrings"
    const val GLUTES = "Glutes"
    const val ADDUCTORS = "Adductors"
    const val CALVES = "Calves"
    const val HIPS = "Hips"
    const val CORE = "Core"

    /** Conditioning qualities the catalog stores alongside muscles. Not trainable volume targets. */
    const val CARDIO = "Cardio"
    const val MOBILITY = "Mobility"

    /** Anatomical groups eligible for muscle priorities, split targeting, and volume attribution. */
    val TRAINABLE = listOf(
        CHEST, SHOULDERS, REAR_DELTS, TRICEPS,
        BACK, UPPER_BACK, LOWER_BACK, LATS, BICEPS, FOREARMS,
        QUADS, HAMSTRINGS, GLUTES, ADDUCTORS, CALVES, HIPS,
        CORE
    )

    /** Non-anatomical tags that stay attached to exercises but never count as training volume. */
    val CONDITIONING = listOf(CARDIO, MOBILITY)

    val ALL = TRAINABLE + CONDITIONING

    /**
     * Groups offered as muscle priorities.
     *
     * Every entry has to steer the planner toward a split, so Adductors and Hips are left
     * out: they are real training targets the catalog tags, but a priority the planner
     * cannot act on is a control that does nothing.
     */
    val PRIORITY_OPTIONS = listOf(
        CHEST, SHOULDERS, REAR_DELTS, TRICEPS,
        BACK, UPPER_BACK, LATS, BICEPS, FOREARMS,
        QUADS, HAMSTRINGS, GLUTES, CALVES, LOWER_BACK,
        CORE
    )
}

/**
 * Normalizes muscle names from every source into [StandardMuscles].
 *
 * The bundled Workout Guide catalog is a faithful mirror of its upstream repository, so it
 * carries upstream's own spellings ("Quads") and umbrella terms that are not single muscles
 * ("Legs", "Posterior Chain"). Left alone those never match the names the planner targets or
 * the user picks priorities from, which silently drops exercises out of selection. Everything
 * entering the domain passes through here instead, so exactly one vocabulary reaches the rest
 * of the app.
 *
 * Umbrella terms expand rather than resolve to a single group: 24 catalog exercises would
 * otherwise be left with no muscles at all and become unreachable to every split.
 */
object MuscleVocabulary {

    private val canonicalByLowercaseName: Map<String, List<String>> = buildMap {
        StandardMuscles.ALL.forEach { canonical ->
            put(canonical.lowercase(Locale.ROOT), listOf(canonical))
        }

        // Upstream spellings for a group WallCrawl already names differently.
        put("quads", listOf(StandardMuscles.QUADS))
        put("groin", listOf(StandardMuscles.ADDUCTORS))
        put("grip", listOf(StandardMuscles.FOREARMS))
        put("abs", listOf(StandardMuscles.CORE))
        put("abdominals", listOf(StandardMuscles.CORE))
        put("glutes/hamstrings", listOf(StandardMuscles.GLUTES, StandardMuscles.HAMSTRINGS))

        // Umbrella terms that describe several groups at once.
        put(
            "legs",
            listOf(StandardMuscles.QUADS, StandardMuscles.HAMSTRINGS, StandardMuscles.GLUTES)
        )
        put(
            "posterior chain",
            listOf(StandardMuscles.GLUTES, StandardMuscles.HAMSTRINGS, StandardMuscles.LOWER_BACK)
        )
        put(
            "full body",
            listOf(StandardMuscles.CHEST, StandardMuscles.BACK, StandardMuscles.QUADS, StandardMuscles.CORE)
        )
    }

    /**
     * Returns the canonical groups [raw] describes: one name for a direct match or alias,
     * several for an umbrella term, none for blank input.
     *
     * An unrecognized name passes through trimmed rather than being dropped, so a catalog
     * update that adds vocabulary degrades to "not matched by splits" instead of "exercise
     * silently has no muscles". [isCanonical] detects that case, and
     * `BundledCatalogVocabularyTest` fails the build when the shipped catalog introduces one.
     */
    fun canonicalize(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()
        return canonicalByLowercaseName[trimmed.lowercase(Locale.ROOT)] ?: listOf(trimmed)
    }

    /** Canonicalizes every value, expanding umbrella terms and removing duplicates. */
    fun canonicalizeAll(values: List<String>): List<String> =
        values.flatMap(::canonicalize).distinct()

    /**
     * The single group that best represents [raw], or null if it names nothing.
     *
     * Primary muscles stay one-per-name because weekly set counts credit each completed set
     * to every primary: expanding "Legs" into three groups would report four sets of lunges
     * as twelve. The groups dropped here are added to the exercise's secondary muscles by
     * [canonicalizeAll], which is what split matching reads, so nothing stops matching.
     */
    fun canonicalizePrimary(raw: String): String? = canonicalize(raw).firstOrNull()

    /** True when [value] is already a known canonical name, alias, or umbrella term. */
    fun isCanonical(value: String): Boolean =
        canonicalByLowercaseName.containsKey(value.trim().lowercase(Locale.ROOT))

    /** True when [muscle] counts toward training volume, excluding conditioning tags. */
    fun isTrainable(muscle: String): Boolean =
        StandardMuscles.TRAINABLE.any { it.equals(muscle.trim(), ignoreCase = true) }
}
