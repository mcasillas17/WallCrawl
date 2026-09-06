package wallcrawl.elopenmike.com.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import wallcrawl.elopenmike.com.R

/**
 * A bottom-navigation destination. [route] is the stable identifier the graph and any saved
 * back stack use; [titleRes] is what the reader sees, so a language change relabels the bar
 * without touching navigation state.
 */
sealed class Screen(val route: String, @StringRes val titleRes: Int, val icon: ImageVector) {
    data object Today : Screen("today", R.string.nav_today, Icons.Default.Home)
    data object Progress :
        Screen("progress", R.string.nav_progress, Icons.AutoMirrored.Filled.TrendingUp)
    data object Exercises :
        Screen("exercises", R.string.nav_exercises, Icons.Default.FitnessCenter)
    data object Profile : Screen("profile", R.string.nav_profile, Icons.Default.Person)

    companion object {
        val bottomNavItems = listOf(Today, Progress, Exercises, Profile)
    }
}

object AppRoutes {
    const val ONBOARDING = "onboarding"
    const val TODAY = "today"
    const val PROGRESS = "progress"
    const val EXERCISES = "exercises"
    const val PROFILE = "profile"
    const val ACTIVE_WORKOUT = "workout_active/{sessionId}"
    const val WORKOUT_TEMPLATES = "workout_templates"
    const val TEMPLATE_NEW = "workout_template/new"
    const val TEMPLATE_EDIT = "workout_template/{templateId}"
    const val WORKOUT_SUMMARY = "workout_summary/{sessionId}"
    const val CREDITS = "credits"

    fun activeWorkout(sessionId: String) = "workout_active/$sessionId"
    fun editTemplate(templateId: String) = "workout_template/$templateId"
    fun workoutSummary(sessionId: String) = "workout_summary/$sessionId"
}
