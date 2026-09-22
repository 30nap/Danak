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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ir.danak.app.BuildConfig
import ir.danak.app.ui.screens.detail.DetailScreen
import ir.danak.app.ui.screens.feed.FeedScreen
import ir.danak.app.ui.screens.interests.InterestsScreen
import ir.danak.app.ui.screens.saved.SavedScreen
import ir.danak.app.ui.screens.settings.SettingsScreen
import ir.danak.app.ui.theme.DanakTheme

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
fun DanakApp(viewModel: DanakViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    DanakTheme(themeMode = state.themeMode) {
        val navController = rememberNavController()
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            DanakNavHost(
                navController = navController,
                state = state,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun DanakNavHost(
    navController: NavHostController,
    state: DanakUiState,
    viewModel: DanakViewModel,
) {
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
        composable(Routes.ONBOARDING) {
            InterestsScreen(
                selected = state.interests,
                onToggle = viewModel::toggleInterest,
                onContinue = {
                    viewModel.confirmInterests()
                    navController.navigate(Routes.FEED) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                continueLabel = "ادامه",
                onBack = null,
            )
        }

        composable(Routes.FEED) {
            FeedScreen(
                danaks = state.feed,
                isSaved = state::isSaved,
                onToggleSave = viewModel::toggleSaved,
                onOpenDetail = { id -> navController.navigate(Routes.detail(id)) },
                onOpenSaved = { navController.navigate(Routes.SAVED) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
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
                DetailScreen(
                    danak = danak,
                    saved = state.isSaved(danak.id),
                    onToggleSave = { viewModel.toggleSaved(danak.id) },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(Routes.SAVED) {
            SavedScreen(
                saved = state.saved,
                onOpen = { id -> navController.navigate(Routes.detail(id)) },
                onRemove = viewModel::toggleSaved,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                themeMode = state.themeMode,
                onThemeModeChange = viewModel::setThemeMode,
                interestCount = state.interests.size,
                onEditInterests = { navController.navigate(Routes.EDIT_INTERESTS) },
                onBack = { navController.popBackStack() },
                appVersion = BuildConfig.VERSION_NAME,
            )
        }

        composable(Routes.EDIT_INTERESTS) {
            InterestsScreen(
                selected = state.interests,
                onToggle = viewModel::toggleInterest,
                onContinue = { navController.popBackStack() },
                continueLabel = "ذخیره",
                onBack = { navController.popBackStack() },
            )
        }
    }
}
