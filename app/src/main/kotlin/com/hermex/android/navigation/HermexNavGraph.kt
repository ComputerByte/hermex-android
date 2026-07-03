package com.hermex.android.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hermex.android.AppContainer
import com.hermex.android.auth.AuthState
import com.hermex.android.chat.ChatScreen
import com.hermex.android.chat.ChatViewModel
import com.hermex.android.onboarding.OnboardingScreen
import com.hermex.android.onboarding.OnboardingViewModel
import com.hermex.android.sessions.SessionListScreen
import com.hermex.android.sessions.SessionListViewModel

private object Routes {
    const val ONBOARDING = "onboarding"
    const val SESSION_LIST = "sessionList"
    const val CHAT_PATTERN = "chat/{sessionId}"
    fun chat(sessionId: String) = "chat/$sessionId"
}

/**
 * Top-level router, the Compose/multi-screen analogue of iOS's `ContentView` (a plain switch on
 * `AuthManager.state`). [AuthRepository.state] is the single source of truth for which "area" of
 * the app is visible -- screens don't independently decide to bounce to Onboarding on a 401,
 * they just react to the shared state flipping. Navigation *within* the LoggedIn area (session
 * list -> chat) is ordinary NavHost navigation and doesn't touch this gate.
 */
@Composable
fun HermexNavGraph(appContainer: AppContainer) {
    val isRestoring by appContainer.authRepository.isRestoring.collectAsStateWithLifecycle()
    val authState by appContainer.authRepository.state.collectAsStateWithLifecycle()

    if (isRestoring) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val navController = rememberNavController()

    LaunchedEffect(authState) {
        val target = if (authState is AuthState.LoggedIn) Routes.SESSION_LIST else Routes.ONBOARDING
        if (navController.currentDestination?.route != target) {
            navController.navigate(target) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (authState is AuthState.LoggedIn) Routes.SESSION_LIST else Routes.ONBOARDING,
    ) {
        composable(Routes.ONBOARDING) {
            val viewModel: OnboardingViewModel = viewModel(factory = appContainer.onboardingViewModelFactory())
            OnboardingScreen(viewModel = viewModel, modifier = Modifier.fillMaxSize())
        }
        composable(Routes.SESSION_LIST) {
            val viewModel: SessionListViewModel = viewModel(factory = appContainer.sessionListViewModelFactory())
            SessionListScreen(
                viewModel = viewModel,
                onOpenSession = { sessionId -> navController.navigate(Routes.chat(sessionId)) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(
            route = Routes.CHAT_PATTERN,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId").orEmpty()
            val viewModel: ChatViewModel = viewModel(
                key = sessionId,
                factory = appContainer.chatViewModelFactory(sessionId),
            )
            ChatScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
