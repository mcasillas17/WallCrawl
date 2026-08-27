package wallcrawl.elopenmike.com.core.exercise.workoutguide

import wallcrawl.elopenmike.com.core.model.ExerciseAttribution

/** Attribution stand-in for tests that exercise catalog behaviour rather than provenance. */
fun testCatalogAttribution(
    exerciseCount: Int = 0,
    frameCount: Int = 0
): CatalogAttribution = CatalogAttribution(
    repository = "https://github.com/bryllim/workout-guide",
    commit = "0".repeat(40),
    assetLicense = "CC-BY-SA-4.0",
    attribution = ExerciseAttribution(
        creator = "Test Creator",
        creatorUrl = "https://example.test",
        license = "CC BY-SA 4.0",
        licenseUrl = "https://creativecommons.org/licenses/by-sa/4.0/"
    ),
    exerciseCount = exerciseCount,
    frameCount = frameCount
)
