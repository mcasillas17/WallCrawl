package wallcrawl.elopenmike.com.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import wallcrawl.elopenmike.com.AppContainer
import wallcrawl.elopenmike.com.core.ui.theme.CrimsonRedPrimary
import wallcrawl.elopenmike.com.core.ui.theme.GraphiteBorder
import wallcrawl.elopenmike.com.core.ui.theme.GraphiteSurface
import wallcrawl.elopenmike.com.core.ui.theme.ObsidianBlack
import wallcrawl.elopenmike.com.core.ui.theme.TextMuted
import wallcrawl.elopenmike.com.core.ui.theme.TextSecondary
import wallcrawl.elopenmike.com.core.ui.theme.TextWhite
import wallcrawl.elopenmike.com.feature.exercises.ExercisesScreen
import wallcrawl.elopenmike.com.feature.exercises.ExercisesViewModel
import wallcrawl.elopenmike.com.feature.profile.ProfileScreen
import wallcrawl.elopenmike.com.feature.profile.ProfileViewModel
import wallcrawl.elopenmike.com.feature.progress.ProgressScreen
import wallcrawl.elopenmike.com.feature.progress.ProgressViewModel
import wallcrawl.elopenmike.com.feature.today.TodayScreen
import wallcrawl.elopenmike.com.feature.today.TodayViewModel
import wallcrawl.elopenmike.com.feature.workout.ActiveWorkoutScreen
import wallcrawl.elopenmike.com.feature.workout.ActiveWorkoutViewModel

@Composable
fun WallCrawlApp(
    container: AppContainer,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val shouldShowBottomBar = currentRoute in listOf(
        AppRoutes.TODAY,
        AppRoutes.PROGRESS,
        AppRoutes.EXERCISES,
        AppRoutes.PROFILE
    )

    Scaffold(
        containerColor = ObsidianBlack,
        bottomBar = {
            if (shouldShowBottomBar) {
                NavigationBar(
                    containerColor = GraphiteSurface,
                    modifier = Modifier
                        .height(68.dp)
                        .border(1.dp, GraphiteBorder)
                ) {
                    Screen.bottomNavItems.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title,
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TextWhite,
                                unselectedIconColor = TextMuted,
                                selectedTextColor = CrimsonRedPrimary,
                                unselectedTextColor = TextMuted,
                                indicatorColor = CrimsonRedPrimary
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRoutes.TODAY,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(AppRoutes.TODAY) {
                val todayViewModel: TodayViewModel = viewModel(
                    factory = TodayViewModel.provideFactory(
                        userProfileRepository = container.userProfileRepository,
                        workoutRepository = container.workoutRepository,
                        workoutGenerationContextBuilder = container.workoutGenerationContextBuilder,
                        workoutPlanner = container.workoutPlanner,
                        workoutValidator = container.workoutValidator
                    )
                )
                TodayScreen(
                    viewModel = todayViewModel,
                    onStartWorkout = { sessionId ->
                        navController.navigate(AppRoutes.activeWorkout(sessionId))
                    },
                    onResumeWorkout = { sessionId ->
                        navController.navigate(AppRoutes.activeWorkout(sessionId))
                    }
                )
            }

            composable(AppRoutes.PROGRESS) {
                val progressViewModel: ProgressViewModel = viewModel(
                    factory = ProgressViewModel.provideFactory(
                        workoutRepository = container.workoutRepository,
                        userProfileRepository = container.userProfileRepository,
                        exerciseCatalog = container.exerciseCatalog,
                        progressCalculator = container.progressCalculator
                    )
                )
                ProgressScreen(viewModel = progressViewModel)
            }

            composable(AppRoutes.EXERCISES) {
                val exercisesViewModel: ExercisesViewModel = viewModel(
                    factory = ExercisesViewModel.provideFactory(
                        exerciseCatalog = container.exerciseCatalog
                    )
                )
                ExercisesScreen(viewModel = exercisesViewModel)
            }

            composable(AppRoutes.PROFILE) {
                val profileViewModel: ProfileViewModel = viewModel(
                    factory = ProfileViewModel.provideFactory(
                        userProfileRepository = container.userProfileRepository
                    )
                )
                ProfileScreen(viewModel = profileViewModel)
            }

            composable(
                route = AppRoutes.ACTIVE_WORKOUT,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId") ?: ""
                val workoutViewModel: ActiveWorkoutViewModel = viewModel(
                    key = sessionId,
                    factory = ActiveWorkoutViewModel.provideFactory(
                        sessionId = sessionId,
                        workoutRepository = container.workoutRepository,
                        userProfileRepository = container.userProfileRepository,
                        exerciseCatalog = container.exerciseCatalog,
                        workoutHistoryAnalyzer = container.workoutHistoryAnalyzer
                    )
                )
                ActiveWorkoutScreen(
                    viewModel = workoutViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onWorkoutFinished = {
                        navController.navigate(AppRoutes.PROGRESS) {
                            popUpTo(AppRoutes.TODAY)
                        }
                    }
                )
            }
        }
    }
}
