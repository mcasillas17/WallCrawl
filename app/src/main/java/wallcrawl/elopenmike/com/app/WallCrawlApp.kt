package wallcrawl.elopenmike.com.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import wallcrawl.elopenmike.com.feature.progress.ProgressSection
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import wallcrawl.elopenmike.com.R
import wallcrawl.elopenmike.com.feature.program.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import wallcrawl.elopenmike.com.core.ui.components.WallCrawlWordmark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import wallcrawl.elopenmike.com.AppContainer
import wallcrawl.elopenmike.com.core.ui.components.LoadingIndicator
import wallcrawl.elopenmike.com.feature.backup.LocalDataOperation
import wallcrawl.elopenmike.com.feature.backup.LocalDataOutcomeEffect
import wallcrawl.elopenmike.com.feature.backup.LocalDataSection
import wallcrawl.elopenmike.com.feature.backup.LocalDataViewModel
import wallcrawl.elopenmike.com.feature.backup.RestoreFromArchiveButton
import wallcrawl.elopenmike.com.feature.credits.CreditsScreen
import wallcrawl.elopenmike.com.feature.credits.CreditsViewModel
import wallcrawl.elopenmike.com.feature.conditions.ConditionReferenceScreen
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
import wallcrawl.elopenmike.com.feature.history.WorkoutHistoryDetailScreen
import wallcrawl.elopenmike.com.feature.history.WorkoutHistoryDetailViewModel
import wallcrawl.elopenmike.com.feature.history.WorkoutHistoryListScreen
import wallcrawl.elopenmike.com.feature.history.WorkoutHistoryListViewModel
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
                    LoadingIndicator()
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

    var savedWorkoutId by rememberSaveable { mutableStateOf<String?>(null) }

    fun openWorkout(sessionId: String) {
        val resume = savedWorkoutId == sessionId
        if (!resume && savedWorkoutId != null) navController.clearBackStack(AppRoutes.ACTIVE_WORKOUT)
        navController.navigate(AppRoutes.activeWorkout(sessionId)) {
            launchSingleTop = true
            restoreState = resume
        }
        savedWorkoutId = null
    }

    fun clearSavedDestinations() {
        (Screen.bottomNavItems + Screen.Profile).forEach { navController.clearBackStack(it.route) }
        navController.clearBackStack(AppRoutes.ACTIVE_WORKOUT)
        savedWorkoutId = null
    }

    fun openRoot(screen: Screen) {
        navController.navigate(screen.route) {
            popUpTo(AppRoutes.TODAY) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    /**
     * Returns to first-run onboarding with nothing behind it.
     *
     * The graph's start destination was chosen when the app launched, so after deleting
     * every local record the back stack has to be replaced rather than merely added to.
     */
    fun restartAtOnboarding() {
        clearSavedDestinations()
        navController.navigate(AppRoutes.ONBOARDING) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    LaunchedEffect(startDestination, currentRoute) {
        if (startDestination == AppRoutes.ONBOARDING &&
            currentRoute != null && currentRoute != AppRoutes.ONBOARDING
        ) {
            restartAtOnboarding()
        }
    }

    fun openTodayAfterRestore() {
        clearSavedDestinations()
        navController.navigate(AppRoutes.TODAY) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    val localDataViewModel: LocalDataViewModel = viewModel(
        key = "wallcrawl-local-data",
        factory = LocalDataViewModel.provideFactory(contentResolver, container.localDataBackupRepository)
    )
    LocalDataOutcomeEffect(
        viewModel = localDataViewModel,
        onRestored = { completed ->
            if (completed) openTodayAfterRestore()
            else if (navController.currentDestination?.route != AppRoutes.ONBOARDING) restartAtOnboarding()
        },
        onDeleted = { restartAtOnboarding() }
    )

    val rootScreen = Screen.bottomNavItems.find { it.route == currentRoute }
    val libraryOwner = navBackStackEntry?.takeIf { currentRoute == AppRoutes.EXERCISES }
    val libraryTemplates = libraryOwner?.let { entry ->
        viewModel<WorkoutTemplatesViewModel>(
            viewModelStoreOwner = entry,
            factory = templatesFactory(container)
        )
    }
    val libraryLocked = libraryTemplates?.navigationLocked?.collectAsState()?.value == true

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (rootScreen != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (rootScreen == Screen.Today) {
                        WallCrawlWordmark(fontSize = 16.sp)
                    } else {
                        Text(
                            stringResource(rootScreen.titleRes),
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.weight(1f).semantics { heading() }
                        )
                    }
                    IconButton(
                        enabled = !libraryLocked,
                        onClick = {
                            if (libraryTemplates?.navigationLocked?.value != true) {
                                navController.navigate(AppRoutes.PROFILE) { launchSingleTop = true }
                            }
                        }
                    ) {
                        Icon(Icons.Outlined.AccountCircle, stringResource(R.string.guided_profile_settings))
                    }
                }
            }
        },
        bottomBar = {
            if (rootScreen != null) {
                WallCrawlBottomBar(
                    currentRoute = currentRoute,
                    enabled = !libraryLocked,
                    onNavigate = { if (libraryTemplates?.navigationLocked?.value != true) openRoot(it) }
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
                        conditionSupport = { container.conditionSupportStore.load() },
                        userProfileRepository = container.userProfileRepository
                    )
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
                    isRestoreInFlight = localDataState.operation == LocalDataOperation.RESTORING
                )
            }

            composable(AppRoutes.TODAY) { entry ->
                val todayViewModel: TodayViewModel = viewModel(
                    viewModelStoreOwner = entry,
                    extras = entry.defaultViewModelCreationExtras,
                    factory = TodayViewModel.provideFactory(
                        userProfileRepository = container.userProfileRepository,
                        workoutRepository = container.workoutRepository,
                        workoutGenerationContextBuilder = container.workoutGenerationContextBuilder,
                        workoutPlanner = container.workoutPlanner,
                        programValidator = container.programValidator,
                        deloadRepository = container.deloadRepository,
                        programRepository = container.programRepository
                    )
                )
                val programViewModel: ProgramViewModel = viewModel(factory = ProgramViewModel.provideFactory(
                    container.programRepository, container.workoutRepository.observeActiveSession()))
                val programState = rememberProgramState(programViewModel)
                TodayScreen(
                    onOpenProfile = { navController.navigate(AppRoutes.PROFILE) },
                    viewModel = todayViewModel,
                    onStartWorkout = ::openWorkout,
                    onResumeWorkout = ::openWorkout,
                    onOpenTemplates = { navController.navigate(AppRoutes.WORKOUT_TEMPLATES) },
                    programState = programState,
                    onManageProgram = { navController.navigate(AppRoutes.PROGRAM) },
                    onRetryProgram = programViewModel::retry
                )
            }

            listOf(
                AppRoutes.PROGRESS to ProgressSection.OVERVIEW,
                AppRoutes.TRENDS to ProgressSection.TRENDS,
                AppRoutes.MUSCLES to ProgressSection.MUSCLES
            ).forEach { (route, section) ->
                composable(route) { entry ->
                    val owner = remember(entry) { navController.getBackStackEntry(AppRoutes.PROGRESS) }
                    val progressViewModel: ProgressViewModel = viewModel(
                        viewModelStoreOwner = owner,
                        factory = ProgressViewModel.provideFactory(container.progressRepository)
                    )
                    ProgressScreen(
                        viewModel = progressViewModel,
                        section = section,
                        onBack = { navController.popBackStack() },
                        onOpenSession = { navController.navigate(AppRoutes.historyDetail(it)) },
                        onOpenHistory = { navController.navigate(AppRoutes.HISTORY) },
                        onOpenTrain = { openRoot(Screen.Today) },
                        onOpenTrends = { navController.navigate(AppRoutes.TRENDS) },
                        onOpenMuscles = { navController.navigate(AppRoutes.MUSCLES) }
                    )
                }
            }

            composable(AppRoutes.PROGRAM) {
                val programViewModel: ProgramViewModel = viewModel(factory = ProgramViewModel.provideFactory(
                    container.programRepository, container.workoutRepository.observeActiveSession()))
                ProgramScreen(programViewModel, onBack = { navController.popBackStack() },
                    onEditProfile = { navController.navigate(AppRoutes.PROFILE) },
                    onResume = ::openWorkout,
                    onOpenHistory = { navController.navigate(AppRoutes.historyDetail(it)) })
            }

            composable(AppRoutes.HISTORY) {
                val historyViewModel: WorkoutHistoryListViewModel = viewModel(
                    factory = WorkoutHistoryListViewModel.provideFactory(container.workoutHistoryRepository)
                )
                WorkoutHistoryListScreen(
                    viewModel = historyViewModel,
                    onOpenSession = { navController.navigate(AppRoutes.historyDetail(it)) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = AppRoutes.HISTORY_DETAIL,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
            ) { entry ->
                val sessionId = requireNotNull(entry.arguments?.getString("sessionId"))
                val historyViewModel: WorkoutHistoryDetailViewModel = viewModel(
                    key = sessionId,
                    factory = WorkoutHistoryDetailViewModel.provideFactory(
                        sessionId, container.workoutHistoryRepository
                    )
                )
                WorkoutHistoryDetailScreen(
                    viewModel = historyViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(AppRoutes.EXERCISES) {
                val exercisesViewModel: ExercisesViewModel = viewModel(
                    factory = ExercisesViewModel.provideFactory(
                        exerciseCatalog = container.exerciseCatalog
                    )
                )
                val templates: WorkoutTemplatesViewModel = viewModel(factory = templatesFactory(container))
                BackHandler {
                    if (!templates.navigationLocked.value) navController.popBackStack()
                }
                ExercisesScreen(
                    viewModel = exercisesViewModel,
                    visualProvider = container.exerciseVisualProvider,
                    onOpenConditionReference = { navController.navigate(AppRoutes.CONDITION_REFERENCE) },
                    templatesViewModel = templates,
                    onCreateTemplate = { navController.navigate(AppRoutes.TEMPLATE_NEW) },
                    onEditTemplate = { navController.navigate(AppRoutes.editTemplate(it)) },
                    onWorkoutStarted = ::openWorkout
                )
            }

            composable(AppRoutes.CONDITION_REFERENCE) {
                ConditionReferenceScreen(container.exerciseCatalog, container.exerciseVisualProvider, onBack = { navController.popBackStack() })
            }

            composable(AppRoutes.PROFILE) {
                val profileViewModel: ProfileViewModel = viewModel(
                    factory = ProfileViewModel.provideFactory(
                        conditionSupport = { container.conditionSupportStore.load() },
                        workoutRepository = container.workoutRepository,
                        userProfileRepository = container.userProfileRepository
                    )
                )
                ProfileScreen(
                    viewModel = profileViewModel,
                    onBack = { navController.popBackStack() },
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
                        workoutHistoryAnalyzer = container.workoutHistoryAnalyzer,
                        programRepository = container.programRepository,
                        userProfileRepository = container.userProfileRepository
                    )
                )
                ActiveWorkoutScreen(
                    viewModel = workoutViewModel,
                    visualProvider = container.exerciseVisualProvider,
                    onNavigateBack = { navController.popBackStack() },
                    onLeaveForLater = {
                        if (navController.popBackStack(AppRoutes.activeWorkout(sessionId), inclusive = true, saveState = true)) {
                            savedWorkoutId = sessionId
                        }
                    },
                    onOpenHistory = { navController.navigate(AppRoutes.historyDetail(it)) },
                    onWorkoutFinished = {
                        navController.popBackStack()
                        openRoot(Screen.Progress)
                    }
                )
            }

            composable(AppRoutes.WORKOUT_TEMPLATES) {
                val templatesViewModel: WorkoutTemplatesViewModel = viewModel(
                    factory = templatesFactory(container)
                )
                WorkoutTemplatesScreen(
                    viewModel = templatesViewModel,
                    onBack = { navController.popBackStack() },
                    // The list takes these only while settled; see WorkoutTemplatesScreen.
                    onCreate = { navController.navigate(AppRoutes.TEMPLATE_NEW) },
                    onEdit = { navController.navigate(AppRoutes.editTemplate(it)) },
                    // Called by the list once it holds a started session, including one that
                    // finished while the app was paused or recreated; single-top keeps any
                    // repeat to one entry.
                    onWorkoutStarted = ::openWorkout
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
                        localizationSource = container.exerciseLocalizationSource,
                        conditionContext = { container.workoutGenerationContextBuilder.build() }
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
                        localizationSource = container.exerciseLocalizationSource,
                        conditionContext = { container.workoutGenerationContextBuilder.build() }
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

private fun templatesFactory(container: AppContainer) = WorkoutTemplatesViewModel.provideFactory(
    templateRepository = container.workoutTemplateRepository,
    workoutRepository = container.workoutRepository,
    userProfileRepository = container.userProfileRepository,
    exerciseCatalog = container.exerciseCatalog
)

internal const val WALL_CRAWL_BOTTOM_BAR_TEST_TAG = "wall_crawl_bottom_bar"

@Composable
internal fun WallCrawlBottomBar(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val items = Screen.bottomNavItems
    // The style a label is drawn in, letter spacing included, at its widest (selected) weight.
    val labelStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
    val measurer = rememberTextMeasurer()
    val titles = items.associateWith { stringResource(it.titleRes) }
    BoxWithConstraints(modifier = modifier.testTag(WALL_CRAWL_BOTTOM_BAR_TEST_TAG)) {
        // Large text can make a word wider than a quarter of a narrow screen. Two rows give each
        // label the room to stay whole at the size the reader chose, instead of breaking mid-word.
        val widest = titles.values.maxOf { measurer.measure(it, labelStyle).size.width }
        // Material spaces items 8 dp apart and pads a label 4 dp on each side.
        val labelSlot = (maxWidth - 8.dp * (items.size - 1)) / items.size - 8.dp
        val perRow = if (with(LocalDensity.current) { widest.toDp() } > labelSlot) (items.size + 1) / 2 else items.size
        Column {
            val rows = items.chunked(perRow)
            rows.forEachIndexed { index, row ->
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    windowInsets = if (index == rows.lastIndex) NavigationBarDefaults.windowInsets else NavigationBarDefaults.windowInsets.only(WindowInsetsSides.Horizontal)
                ) {
                    row.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = isSelected,
                            enabled = enabled,
                            onClick = { onNavigate(screen) },
                            icon = {
                                // Null: the label names this item, so a description would be read twice.
                                Icon(screen.icon, contentDescription = null, modifier = Modifier.size(22.dp))
                            },
                            label = {
                                Text(
                                    text = titles.getValue(screen),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedTextColor = MaterialTheme.colorScheme.tertiary,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    }
}
