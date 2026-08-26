package wallcrawl.elopenmike.com.core.exercise.visual

import wallcrawl.elopenmike.com.core.model.ExerciseAttribution

data class ExerciseVisual(
    val assetPath: String,
    val widthPx: Int = 512,
    val heightPx: Int = 512,
    val attribution: ExerciseAttribution? = null
)

interface ExerciseVisualProvider {
    fun framesFor(exerciseId: String): List<ExerciseVisual>
}
