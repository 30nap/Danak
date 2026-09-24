package ir.danak.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ir.danak.app.BuildConfig
import ir.danak.app.ui.components.prefetchHero
import ir.danak.app.ui.screens.detail.DetailScreen
import ir.danak.app.ui.screens.feed.FeedScreen
import ir.danak.app.ui.screens.interests.InterestsScreen
import ir.danak.app.ui.screens.saved.SavedScreen
import ir.danak.app.ui.screens.settings.SettingsScreen
import ir.danak.app.ui.theme.DanakTheme
import ir.danak.app.ui.theme.ImmersiveSurface
import ir.danak.app.ui.theme.SystemBarsEffect
import ir.danak.app.ui.theme.resolvesToDark

private object Routes {
    const val ONBOARDING = "onboarding"
    const val FEED = "feed"
    const val DETAIL = "detail/{danakId}"
    const val SAVED = "saved"
    const val SETTINGS = "settings"
    const val EDIT_INTERESTS = "settings/interests"

    fun detail(danakId: String) = "detail/$danakId"
}

private const val TRANSITION_MS = 260

@Composable
fun DanakApp(viewModel: DanakViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DanakTheme(themeMode = state.themeMode) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val route = backStackEntry?.destination?.route
        SystemBarsEffect(
            darkBackground = route == Routes.FEED || route == Routes.DETAIL ||
                state.themeMode.resolvesToDark(),
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // The splash screen covers this frame; the graph is only built once we know
            // whether this is a first launch.
            if (state.isLoaded) {
                DanakNavHost(
                    navController = navController,
                    state = state,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun DanakNavHost(
    navController: NavHostController,
    state: DanakUiState,
    viewModel: DanakViewModel,
) {
    val context = LocalContext.current
    // Captured once: interests are chosen in-session, and letting this flip afterwards
    // would rebuild the graph under the user.
    val startDestination = remember {
        if (state.hasChosenInterests) Routes.FEED else Routes.ONBOARDING
    }
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn(tween(TRANSITION_MS)) },
        exitTransition = { fadeOut(tween(TRANSITION_MS)) },
    ) {
        composable(Routes.ONBOARDING) { entry ->
            InterestsScreen(
                selected = state.interests,
                onToggle = viewModel::toggleInterest,
                onContinue = {
                    entry.whenResumed {
                        // Start the first photo now, so the feed opens on it rather than
                        // fading it in once the transition is over.
                        state.feed.firstOrNull()?.let { prefetchHero(context, it.image) }
                        viewModel.confirmInterests()
                        navController.navigate(Routes.FEED) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                },
                continueLabel = "ادامه",
                allowEmpty = false,
                onBack = null,
            )
        }

        composable(Routes.FEED) { entry ->
            ImmersiveSurface {
                FeedScreen(
                    danaks = state.feed,
                    isSaved = state::isSaved,
                    onToggleSave = viewModel::toggleSaved,
                    onOpenDetail = { id -> entry.whenResumed { navController.navigate(Routes.detail(id)) } },
                    onOpenSaved = { entry.whenResumed { navController.navigate(Routes.SAVED) } },
                    onOpenSettings = { entry.whenResumed { navController.navigate(Routes.SETTINGS) } },
                    onEditInterests = { entry.whenResumed { navController.navigate(Routes.EDIT_INTERESTS) } },
                )
            }
        }

        composable(
            route = Routes.DETAIL,
            // Detail rises out of the feed rather than sliding in from the side: it is a
            // deeper layer of the same Danak, not a different place.
            enterTransition = {
                slideInVertically(tween(TRANSITION_MS)) { it / 6 } + fadeIn(tween(TRANSITION_MS))
            },
            popExitTransition = {
                slideOutVertically(tween(TRANSITION_MS)) { it / 6 } + fadeOut(tween(TRANSITION_MS))
            },
        ) { entry ->
            val danakId = entry.arguments?.getString("danakId")
            val danak = danakId?.let(viewModel::danakById)
            if (danak == null) {
                // The only way here is a stale id, so there is nothing to show.
                LaunchedEffect(danakId) { navController.popBackStack() }
            } else {
                ImmersiveSurface {
                    DetailScreen(
                        danak = danak,
                        saved = state.isSaved(danak.id),
                        onToggleSave = { viewModel.toggleSaved(danak.id) },
                        onBack = { entry.whenResumed { navController.popBackStack() } },
                    )
                }
            }
        }

        composable(Routes.SAVED) { entry ->
            SavedScreen(
                saved = state.saved,
                onOpen = { id -> entry.whenResumed { navController.navigate(Routes.detail(id)) } },
                onRemove = viewModel::removeSaved,
                onRestore = viewModel::restoreSaved,
                onBack = { entry.whenResumed { navController.popBackStack() } },
            )
        }

        composable(Routes.SETTINGS) { entry ->
            SettingsScreen(
                themeMode = state.themeMode,
                onThemeModeChange = viewModel::setThemeMode,
                interestCount = state.interests.size,
                onEditInterests = { entry.whenResumed { navController.navigate(Routes.EDIT_INTERESTS) } },
                onBack = { entry.whenResumed { navController.popBackStack() } },
                appVersion = BuildConfig.VERSION_NAME,
            )
        }

        composable(Routes.EDIT_INTERESTS) { entry ->
            InterestsScreen(
                selected = state.interests,
                onToggle = viewModel::toggleInterest,
                onContinue = { entry.whenResumed { navController.popBackStack() } },
                continueLabel = "ذخیره",
                // Clearing every topic is a real choice here: it means "show me everything".
                allowEmpty = true,
                onBack = { entry.whenResumed { navController.popBackStack() } },
            )
        }
    }
}

/**
 * Runs a navigation action only while this destination is the settled, visible one. A second
 * tap during a transition, or on a screen that is already leaving, is dropped: without this,
 * a double tap on «بیشتر بدان» stacked two detail screens, and a double tap on back popped
 * the feed itself and left a blank window.
 */
private inline fun NavBackStackEntry.whenResumed(action: () -> Unit) {
    if (lifecycle.currentState == Lifecycle.State.RESUMED) action()
}
