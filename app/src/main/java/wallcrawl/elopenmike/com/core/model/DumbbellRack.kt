package wallcrawl.elopenmike.com.core.model

/** Explicit per-dumbbell loads in their original unit; display-unit changes never rewrite them. */
data class DumbbellRack(val unit: WeightUnit, val weights: List<Double>) {
    init { validate() }

    fun validate() {
        require(weights.size in 1..MAX_WEIGHTS) { "A dumbbell rack needs 1..$MAX_WEIGHTS loads." }
        require(weights.all { it.isFinite() && it > 0.0 && it <= WorkoutTemplate.maxLoad(unit) && roundForEditing(it) == it }) {
            "Dumbbell loads must be positive, finite, within the load limit and at editable precision."
        }
        require(weights.zipWithNext().all { (a, b) -> a < b }) { "Dumbbell loads must be sorted and unique." }
    }

    companion object { const val MAX_WEIGHTS = 64 }
}
