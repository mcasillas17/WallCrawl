package wallcrawl.elopenmike.com.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Today : Screen("today", "Today", Icons.Default.Home)
    data object Progress : Screen("progress", "Progress", Icons.Default.TrendingUp)
    data object Exercises : Screen("exercises", "Exercises", Icons.Default.FitnessCenter)
    data object Profile : Screen("profile", "Profile", Icons.Default.Person)

    companion object {
        val bottomNavItems = listOf(Today, Progress, Exercises, Profile)
    }
}

object AppRoutes {
    const val TODAY = "today"
    const val PROGRESS = "progress"
    const val EXERCISES = "exercises"
    const val PROFILE = "profile"
    const val ACTIVE_WORKOUT = "workout_active/{sessionId}"
    const val WORKOUT_SUMMARY = "workout_summary/{sessionId}"

    fun activeWorkout(sessionId: String) = "workout_active/$sessionId"
    fun workoutSummary(sessionId: String) = "workout_summary/$sessionId"
}
