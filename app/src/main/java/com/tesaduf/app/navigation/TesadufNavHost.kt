package com.tesaduf.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tesaduf.app.AppContainer
import com.tesaduf.app.ui.chat.ChatScreen
import com.tesaduf.app.ui.chat.ChatViewModel
import com.tesaduf.app.ui.history.HistoryScreen
import com.tesaduf.app.ui.history.HistoryViewModel
import com.tesaduf.app.ui.home.HomeScreen
import com.tesaduf.app.ui.matchmaking.MatchmakingScreen
import com.tesaduf.app.ui.matchmaking.MatchmakingViewModel
import com.tesaduf.app.ui.splash.SplashScreen

object Routes {
    const val SPLASH = "splash"
    const val HOME = "home"
    const val MATCHMAKING = "matchmaking"
    const val HISTORY = "history"
    const val CHAT = "chat/{matchId}"
    fun chat(matchId: String) = "chat/$matchId"
}

@Composable
fun TesadufNavHost(container: AppContainer, navController: NavHostController = rememberNavController()) {
    val repository = container.repository

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(180)) },
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onStart = repository::ensureSession,
                onFinished = {
                    navController.navigate(Routes.HOME) { popUpTo(Routes.SPLASH) { inclusive = true } }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                repository = repository,
                onStart = { navController.navigate(Routes.MATCHMAKING) { launchSingleTop = true } },
                onResume = { matchId -> navController.navigate(Routes.chat(matchId)) { launchSingleTop = true } },
                onHistory = { navController.navigate(Routes.HISTORY) { launchSingleTop = true } },
            )
        }
        composable(Routes.MATCHMAKING) {
            val vm: MatchmakingViewModel = viewModel(factory = viewModelFactory {
                initializer { MatchmakingViewModel(repository) }
            })
            MatchmakingScreen(
                viewModel = vm,
                networkMonitor = container.networkMonitor,
                onMatched = { matchId ->
                    navController.navigate(Routes.chat(matchId)) {
                        popUpTo(Routes.MATCHMAKING) { inclusive = true }
                    }
                },
                onCancel = { navController.popBackStack() },
            )
        }
        composable(
            Routes.CHAT,
            arguments = listOf(navArgument("matchId") { type = NavType.StringType }),
        ) { entry ->
            val matchId = entry.arguments?.getString("matchId").orEmpty()
            val vm: ChatViewModel = viewModel(factory = viewModelFactory {
                initializer { ChatViewModel(matchId, repository) }
            })
            ChatScreen(
                viewModel = vm,
                networkMonitor = container.networkMonitor,
                serverNow = repository.clock::now,
                onBack = {
                    if (!navController.popBackStack()) navController.navigate(Routes.HOME)
                },
                onNewTesaduf = {
                    navController.navigate(Routes.MATCHMAKING) {
                        popUpTo(Routes.HOME) { inclusive = false }
                    }
                },
            )
        }
        composable(Routes.HISTORY) {
            val vm: HistoryViewModel = viewModel(factory = viewModelFactory {
                initializer { HistoryViewModel(repository) }
            })
            HistoryScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpen = { matchId -> navController.navigate(Routes.chat(matchId)) { launchSingleTop = true } },
            )
        }
    }
}
