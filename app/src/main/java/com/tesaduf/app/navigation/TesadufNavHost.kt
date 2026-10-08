package com.tesaduf.app.navigation

import com.tesaduf.app.ui.design.TIcons
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tesaduf.app.AppContainer
import com.tesaduf.app.R
import com.tesaduf.app.repository.SessionState
import com.tesaduf.app.ui.chat.ChatScreen
import com.tesaduf.app.ui.chat.ChatViewModel
import com.tesaduf.app.ui.chats.ChatsScreen
import com.tesaduf.app.ui.design.BottomItem
import com.tesaduf.app.ui.design.TesadufBottomBar
import com.tesaduf.app.ui.history.HistoryScreen
import com.tesaduf.app.ui.history.HistoryViewModel
import com.tesaduf.app.ui.home.HomeScreen
import com.tesaduf.app.ui.matchmaking.MatchmakingScreen
import com.tesaduf.app.ui.matchmaking.MatchmakingViewModel
import com.tesaduf.app.ui.onboarding.OnboardingScreen
import com.tesaduf.app.ui.profile.ProfileSetupScreen
import com.tesaduf.app.ui.settings.BlockedUsersScreen
import com.tesaduf.app.ui.settings.BlockedUsersViewModel
import com.tesaduf.app.ui.settings.SettingsScreen
import com.tesaduf.app.ui.splash.SplashScreen

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val PROFILE_SETUP = "profile_setup?edit={edit}"
    const val HOME = "home"
    const val CHATS = "chats"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val BLOCKED = "blocked"
    const val MATCHMAKING = "matchmaking"
    const val CHAT = "chat/{matchId}"

    fun profileSetup(edit: Boolean) = "profile_setup?edit=$edit"
    fun chat(matchId: String) = "chat/$matchId"

    val tabs = setOf(HOME, CHATS, SETTINGS)
}

@Composable
fun TesadufNavHost(container: AppContainer, navController: NavHostController = rememberNavController()) {
    val repository = container.repository
    val prefs = container.preferences
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val session by repository.session.collectAsStateWithLifecycle()
    val haptics by prefs.haptics.collectAsStateWithLifecycle()

    fun goToTab(route: String) {
        navController.navigate(route) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun goHomeClearing() {
        navController.navigate(Routes.HOME) {
            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
            launchSingleTop = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            // Calm crossfade with a hint of depth; no hard cuts anywhere.
            enterTransition = { fadeIn(tween(480, easing = FastOutSlowInEasing)) + scaleIn(tween(480, easing = FastOutSlowInEasing), initialScale = 0.97f) },
            exitTransition = { fadeOut(tween(320, easing = FastOutSlowInEasing)) },
            popEnterTransition = { fadeIn(tween(420, easing = FastOutSlowInEasing)) + scaleIn(tween(420, easing = FastOutSlowInEasing), initialScale = 1.02f) },
            popExitTransition = { fadeOut(tween(300, easing = FastOutSlowInEasing)) + scaleOut(tween(300), targetScale = 0.98f) },
        ) {
            composable(Routes.SPLASH) {
                SplashScreen(
                    ready = session !is SessionState.Loading,
                    hapticsEnabled = haptics,
                    onStart = repository::ensureSession,
                    onFinished = {
                        val next = if (prefs.onboardingDone) Routes.HOME else Routes.ONBOARDING
                        navController.navigate(next) { popUpTo(Routes.SPLASH) { inclusive = true } }
                    },
                )
            }
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onFinished = {
                    navController.navigate(Routes.profileSetup(edit = false)) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                })
            }
            composable(
                Routes.PROFILE_SETUP,
                arguments = listOf(navArgument("edit") { type = NavType.BoolType; defaultValue = false }),
            ) { entry ->
                val edit = entry.arguments?.getBoolean("edit") ?: false
                ProfileSetupScreen(
                    repository = repository,
                    editMode = edit,
                    onBack = if (edit) ({ navController.popBackStack() }) else null,
                    onDone = {
                        if (edit) {
                            navController.popBackStack()
                        } else {
                            prefs.onboardingDone = true
                            goHomeClearing()
                        }
                    },
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    repository = repository,
                    onStart = { navController.navigate(Routes.MATCHMAKING) { launchSingleTop = true } },
                    onResume = { matchId -> navController.navigate(Routes.chat(matchId)) { launchSingleTop = true } },
                )
            }
            composable(Routes.CHATS) {
                val vm: HistoryViewModel = viewModel(factory = viewModelFactory { initializer { HistoryViewModel(repository) } })
                ChatsScreen(
                    viewModel = vm,
                    serverNow = repository.clock::now,
                    onOpen = { matchId -> navController.navigate(Routes.chat(matchId)) { launchSingleTop = true } },
                    onStart = { navController.navigate(Routes.MATCHMAKING) { launchSingleTop = true } },
                )
            }
            composable(Routes.HISTORY) {
                val vm: HistoryViewModel = viewModel(factory = viewModelFactory { initializer { HistoryViewModel(repository) } })
                HistoryScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onOpen = { matchId -> navController.navigate(Routes.chat(matchId)) { launchSingleTop = true } },
                    onStartFirst = { navController.navigate(Routes.MATCHMAKING) { launchSingleTop = true } },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    repository = repository,
                    onChangeAvatar = { navController.navigate(Routes.profileSetup(edit = true)) },
                    onHistory = { navController.navigate(Routes.HISTORY) { launchSingleTop = true } },
                    onBlocked = { navController.navigate(Routes.BLOCKED) },
                    onSignedOut = {
                        navController.navigate(Routes.ONBOARDING) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        }
                    },
                )
            }
            composable(Routes.BLOCKED) {
                val vm: BlockedUsersViewModel = viewModel(factory = viewModelFactory { initializer { BlockedUsersViewModel(repository) } })
                BlockedUsersScreen(vm, onBack = { navController.popBackStack() })
            }
            composable(Routes.MATCHMAKING) {
                val vm: MatchmakingViewModel = viewModel(factory = viewModelFactory { initializer { MatchmakingViewModel(repository) } })
                MatchmakingScreen(
                    viewModel = vm,
                    networkMonitor = container.networkMonitor,
                    hapticsEnabled = haptics,
                    onMatched = { matchId ->
                        navController.navigate(Routes.chat(matchId)) { popUpTo(Routes.MATCHMAKING) { inclusive = true } }
                    },
                    onCancel = { navController.popBackStack() },
                    onHome = { navController.popBackStack() },
                )
            }
            composable(
                Routes.CHAT,
                arguments = listOf(navArgument("matchId") { type = NavType.StringType }),
            ) { entry ->
                val matchId = entry.arguments?.getString("matchId").orEmpty()
                val vm: ChatViewModel = viewModel(factory = viewModelFactory { initializer { ChatViewModel(matchId, repository) } })
                ChatScreen(
                    viewModel = vm,
                    networkMonitor = container.networkMonitor,
                    myAvatar = (session as? SessionState.Ready)?.profile?.avatar,
                    serverNow = repository.clock::now,
                    onBack = { if (!navController.popBackStack()) goHomeClearing() },
                    onHome = {
                        if (!navController.popBackStack(Routes.HOME, inclusive = false)) goHomeClearing()
                    },
                    onNewTesaduf = {
                        navController.navigate(Routes.MATCHMAKING) { popUpTo(Routes.HOME) { inclusive = false } }
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = currentRoute in Routes.tabs,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            TesadufBottomBar(
                items = listOf(
                    BottomItem(Routes.HOME, stringResource(R.string.nav_home), TIcons.Home),
                    BottomItem(Routes.CHATS, stringResource(R.string.nav_chats), TIcons.Chats),
                    BottomItem(Routes.SETTINGS, stringResource(R.string.nav_settings), TIcons.Settings),
                ),
                selectedRoute = currentRoute,
                onSelect = ::goToTab,
            )
        }
    }
}
