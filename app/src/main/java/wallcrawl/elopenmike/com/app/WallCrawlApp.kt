package wallcrawl.elopenmike.com.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
import wallcrawl.elopenmike.com.feature.backup.LocalDataOutcomeEffect
import wallcrawl.elopenmike.com.feature.backup.LocalDataSection
import wallcrawl.elopenmike.com.feature.backup.LocalDataViewModel
import wallcrawl.elopenmike.com.feature.backup.RestoreFromArchiveButton
import wallcrawl.elopenmike.com.feature.credits.CreditsScreen
import wallcrawl.elopenmike.com.feature.credits.CreditsViewModel
import wallcrawl.elopenmike.com.feature.exercises.ExercisesScreen
import wallcrawl.elopenmike.com.feature.exercises.ExercisesViewModel
import wallcrawl.elopenmike.com.feature.onboarding.OnboardingScreen
import wallcrawl.elopenmike.com.feature.onboarding.OnboardingViewModel
import wallcrawl.elopenmike.com.feature.profile.ProfileScreen
import wallcrawl.elopenmike.com.feature.profile.ProfileViewModel
import wallcrawl.elopenmike.com.feature.progress.ProgressScreen
import wallcrawl.elopenmike.com.feature.progress.ProgressViewModel
import wallcrawl.elopenmike.com.feature.today.TodayScreen
import wallcrawl.elopenmike.com.feature.today.TodayViewModel
import wallcrawl.elopenmike.com.feature.templates.TemplateEditorScreen
import wallcrawl.elopenmike.com.feature.templates.TemplateEditorViewModel
import wallcrawl.elopenmike.com.feature.templates.WorkoutTemplatesScreen
import wallcrawl.elopenmike.com.feature.templates.WorkoutTemplatesViewModel
import wallcrawl.elopenmike.com.feature.workout.ActiveWorkoutScreen
import wallcrawl.elopenmike.com.feature.workout.ActiveWorkoutViewModel

import androidx.compose.material3.MaterialTheme
import wallcrawl.elopenmike.com.core.model.UserProfile
import wallcrawl.elopenmike.com.core.model.illustrationVariant
import wallcrawl.elopenmike.com.core.model.IllustrationVariant
import wallcrawl.elopenmike.com.core.ui.components.LocalIllustrationVariant
import wallcrawl.elopenmike.com.core.ui.localization.LocalExerciseVocabulary
import wallcrawl.elopenmike.com.core.ui.localization.rememberExerciseVocabulary

@Composable
fun WallCrawlApp(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
    profile: UserProfile? = null
) {
    // The profile's onboarding status decides the start destination, so Today is never
    // reachable - and TodayViewModel is never constructed - until onboarding is confirmed
    // complete. A null first emission means the profile hasn't loaded yet.
    val profileState by container.userProfileRepository.getUserProfile().collectAsState(initial = profile)
    val effectiveProfile = profile ?: profileState

    // Catalog text is looked up by canonical id, so the vocabulary is provided once here and
    // re-derived whenever the configuration's locale changes.
    val vocabulary = rememberExerciseVocabulary(container.exerciseLocalizationSource)

    CompositionLocalProvider(
        LocalExerciseVocabulary provides vocabulary,
        LocalIllustrationVariant provides (effectiveProfile?.illustrationVariant ?: IllustrationVariant.MALE)
    ) {
        when (effectiveProfile) {
            null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            else -> {
                WallCrawlAppContent(
                    container = container,
                    navController = navController,
                    startDestination = if (effectiveProfile.onboardingCompleted) {
                        AppRoutes.TODAY
                    } else {
                        AppRoutes.ONBOARDING
                    }
                )
            }
        }
    }
}

@Composable
private fun WallCrawlAppContent(
    container: AppContainer,
    navController: NavHostController,
    startDestination: String
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    // The application context's resolver outlives any single screen, so an export or a
    // restore keeps running across a rotation instead of losing the document it opened.
    val contentResolver = LocalContext.current.applicationContext.contentResolver

    /**
     * Returns to first-run onboarding with nothing behind it.
     *
     * The graph's start destination was chosen when the app launched, so after deleting
     * every local record the back stack has to be replaced rather than merely added to.
     */
    fun restartAtOnboarding() {
        navController.navigate(AppRoutes.ONBOARDING) {
            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
            launchSingleTop = true
        }
    }

    fun openTodayAfterRestore() {
        navController.navigate(AppRoutes.TODAY) {
            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
            launchSingleTop = true
        }
    }

    val shouldShowBottomBar = currentRoute in listOf(
        AppRoutes.TODAY,
        AppRoutes.PROGRESS,
        AppRoutes.EXERCISES,
        AppRoutes.PROFILE
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (shouldShowBottomBar) {
                WallCrawlBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { screen ->
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // Marks those insets spent. Plain padding does not, so a screen applying
                // its own navigationBarsPadding()/imePadding() inside would otherwise end
                // a navigation bar's height above where it should.
                .consumeWindowInsets(innerPadding)
        ) {
            composable(AppRoutes.ONBOARDING) {
                val onboardingViewModel: OnboardingViewModel = viewModel(
                    factory = OnboardingViewModel.provideFactory(
                        userProfileRepository = container.userProfileRepository
                    )
                )
                val localDataViewModel: LocalDataViewModel = viewModel(
                    key = "onboarding-local-data",
                    factory = LocalDataViewModel.provideFactory(
                        contentResolver = contentResolver,
                        repository = container.localDataBackupRepository
                    )
                )
                // Observed here, not inside the card: the card only exists on the first
                // step, and a restore from a slow document can finish after the user has
                // moved on. This stays composed for the whole wizard.
                LocalDataOutcomeEffect(
                    viewModel = localDataViewModel,
                    onRestored = { onboardingCompleted ->
                        // A restored profile that never finished onboarding stays in the
                        // wizard; there is nothing for Today to render yet.
                        if (onboardingCompleted) openTodayAfterRestore()
                    }
                )
                val localDataState by localDataViewModel.uiState.collectAsState()
                OnboardingScreen(
                    viewModel = onboardingViewModel,
                    onCompleted = {
                        navController.navigate(AppRoutes.TODAY) {
                            popUpTo(AppRoutes.ONBOARDING) { inclusive = true }
                        }
                    },
                    restoreFromArchive = {
                        RestoreFromArchiveButton(viewModel = localDataViewModel)
                    },
                    isRestoreInFlight = localDataState.isBusy
                )
            }

            composable(AppRoutes.TODAY) {
                val todayViewModel: TodayViewModel = viewModel(
                    factory = TodayViewModel.provideFactory(
                        userProfileRepository = container.userProfileRepository,
                        workoutRepository = container.workoutRepository,
                        workoutGenerationContextBuilder = container.workoutGenerationContextBuilder,
                        workoutPlanner = container.workoutPlanner,
                        programValidator = container.programValidator
                    )
                )
                TodayScreen(
                    viewModel = todayViewModel,
                    onStartWorkout = { sessionId ->
                        navController.navigate(AppRoutes.activeWorkout(sessionId))
                    },
                    onResumeWorkout = { sessionId ->
                        navController.navigate(AppRoutes.activeWorkout(sessionId))
                    },
                    onOpenTemplates = { navController.navigate(AppRoutes.WORKOUT_TEMPLATES) }
                )
            }

            composable(AppRoutes.PROGRESS) {
                val progressViewModel: ProgressViewModel = viewModel(
                    factory = ProgressViewModel.provideFactory(
                        progressRepository = container.progressRepository
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
                ExercisesScreen(
                    viewModel = exercisesViewModel,
                    visualProvider = container.exerciseVisualProvider
                )
            }

            composable(AppRoutes.PROFILE) {
                val profileViewModel: ProfileViewModel = viewModel(
                    factory = ProfileViewModel.provideFactory(
                        userProfileRepository = container.userProfileRepository
                    )
                )
                val localDataViewModel: LocalDataViewModel = viewModel(
                    key = "profile-local-data",
                    factory = LocalDataViewModel.provideFactory(
                        contentResolver = contentResolver,
                        repository = container.localDataBackupRepository
                    )
                )
                // Outside the profile's scrolling list, so scrolling the card out of view
                // cannot lose the outcome of a restore or a deletion.
                LocalDataOutcomeEffect(
                    viewModel = localDataViewModel,
                    onRestored = { onboardingCompleted ->
                        if (onboardingCompleted) openTodayAfterRestore() else restartAtOnboarding()
                    },
                    onDeleted = { restartAtOnboarding() }
                )
                ProfileScreen(
                    viewModel = profileViewModel,
                    onOpenCredits = { navController.navigate(AppRoutes.CREDITS) },
                    localDataSection = { LocalDataSection(viewModel = localDataViewModel) }
                )
            }

            composable(AppRoutes.CREDITS) {
                val creditsViewModel: CreditsViewModel = viewModel(
                    factory = CreditsViewModel.provideFactory(
                        catalogSource = container.workoutGuideCatalogSource,
                        noticeSource = container.attributionNoticeSource
                    )
                )
                CreditsScreen(
                    viewModel = creditsViewModel,
                    onBack = { navController.popBackStack() }
                )
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
                        exerciseCatalog = container.exerciseCatalog,
                        workoutHistoryAnalyzer = container.workoutHistoryAnalyzer
                    )
                )
                ActiveWorkoutScreen(
                    viewModel = workoutViewModel,
                    visualProvider = container.exerciseVisualProvider,
                    onNavigateBack = { navController.popBackStack() },
                    onWorkoutFinished = {
                        navController.popBackStack()
                        navController.navigate(AppRoutes.PROGRESS) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }

            composable(AppRoutes.WORKOUT_TEMPLATES) {
                val templatesViewModel: WorkoutTemplatesViewModel = viewModel(
                    factory = WorkoutTemplatesViewModel.provideFactory(
                        templateRepository = container.workoutTemplateRepository,
                        workoutRepository = container.workoutRepository,
                        userProfileRepository = container.userProfileRepository,
                        exerciseCatalog = container.exerciseCatalog
                    )
                )
                WorkoutTemplatesScreen(
                    viewModel = templatesViewModel,
                    onBack = { navController.popBackStack() },
                    onCreate = { navController.navigate(AppRoutes.TEMPLATE_NEW) },
                    onEdit = { navController.navigate(AppRoutes.editTemplate(it)) },
                    onWorkoutStarted = { sessionId ->
                        navController.navigate(AppRoutes.activeWorkout(sessionId))
                    }
                )
            }

            composable(AppRoutes.TEMPLATE_NEW) {
                val editorViewModel: TemplateEditorViewModel = viewModel(
                    key = "new-template",
                    factory = TemplateEditorViewModel.provideFactory(
                        templateId = null,
                        templateRepository = container.workoutTemplateRepository,
                        userProfileRepository = container.userProfileRepository,
                        exerciseCatalog = container.exerciseCatalog,
                        localizationSource = container.exerciseLocalizationSource
                    )
                )
                TemplateEditorScreen(
                    viewModel = editorViewModel,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }

            composable(
                AppRoutes.TEMPLATE_EDIT,
                arguments = listOf(navArgument("templateId") { type = NavType.StringType })
            ) { backStackEntry ->
                val templateId = backStackEntry.arguments?.getString("templateId").orEmpty()
                val editorViewModel: TemplateEditorViewModel = viewModel(
                    key = "edit-template-$templateId",
                    factory = TemplateEditorViewModel.provideFactory(
                        templateId = templateId,
                        templateRepository = container.workoutTemplateRepository,
                        userProfileRepository = container.userProfileRepository,
                        exerciseCatalog = container.exerciseCatalog,
                        localizationSource = container.exerciseLocalizationSource
                    )
                )
                TemplateEditorScreen(
                    viewModel = editorViewModel,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
        }
    }
}

internal const val WALL_CRAWL_BOTTOM_BAR_TEST_TAG = "wall_crawl_bottom_bar"

@Composable
internal fun WallCrawlBottomBar(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .testTag(WALL_CRAWL_BOTTOM_BAR_TEST_TAG)
            .border(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Screen.bottomNavItems.forEach { screen ->
            val isSelected = currentRoute == screen.route
            val title = stringResource(screen.titleRes)
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(screen) },
                icon = {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = title,
                        modifier = Modifier.size(22.dp)
                    )
                },
                label = {
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}
