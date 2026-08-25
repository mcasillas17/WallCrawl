package wallcrawl.elopenmike.com.core.exercise.visual

data class ExerciseVisual(
    val assetPath: String
)

interface ExerciseVisualProvider {
    fun framesFor(exerciseId: String): List<ExerciseVisual>
}
