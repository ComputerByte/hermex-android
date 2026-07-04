package com.hermex.android.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.hermex.android.insights.InsightsScreen
import com.hermex.android.insights.InsightsViewModel
import com.hermex.android.memory.MemoryScreen
import com.hermex.android.memory.MemoryViewModel
import com.hermex.android.models.DefaultModelScreen
import com.hermex.android.models.DefaultModelViewModel
import com.hermex.android.onboarding.OnboardingScreen
import com.hermex.android.onboarding.OnboardingViewModel
import com.hermex.android.profiles.ProfilesScreen
import com.hermex.android.profiles.ProfilesViewModel
import com.hermex.android.projects.ProjectsScreen
import com.hermex.android.projects.ProjectsViewModel
import com.hermex.android.sessions.SessionListScreen
import com.hermex.android.sessions.SessionListViewModel
import com.hermex.android.sessions.ShareDestinationPicker
import com.hermex.android.navigation.decodeUriList
import com.hermex.android.navigation.encodeUriList
import com.hermex.android.settings.CustomHeadersScreen
import com.hermex.android.settings.CustomHeadersViewModel
import com.hermex.android.settings.ServersScreen
import com.hermex.android.settings.ServersViewModel
import com.hermex.android.settings.SettingsScreen
import com.hermex.android.settings.SettingsViewModel
import com.hermex.android.skills.SkillDetailScreen
import com.hermex.android.skills.SkillDetailViewModel
import com.hermex.android.skills.SkillsScreen
import com.hermex.android.skills.SkillsViewModel
import com.hermex.android.tasks.TaskDetailScreen
import com.hermex.android.tasks.TaskDetailViewModel
import com.hermex.android.tasks.TasksScreen
import com.hermex.android.tasks.TasksViewModel
import com.hermex.android.workspace.WorkspaceScreen
import com.hermex.android.workspace.WorkspaceViewModel
import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.coroutines.flow.StateFlow

private object Routes {
    const val ONBOARDING = "onboarding"
    const val SESSION_LIST = "sessionList"
    const val NEW_CHAT = "newChat"
    const val CHAT_PATTERN = "chat/{sessionId}?draft={draft}&uploadUris={uploadUris}"
    fun chat(sessionId: String, draft: String? = null, uploadUris: List<String>? = null): String {
        val encodedSessionId = URLEncoder.encode(sessionId, "UTF-8")
        val queryParts = mutableListOf<String>()
        draft?.takeIf { it.isNotBlank() }?.let {
            queryParts.add("draft=${URLEncoder.encode(it, "UTF-8")}")
        }
        uploadUris?.takeIf { it.isNotEmpty() }?.let {
            queryParts.add("uploadUris=${encodeUriList(it)}")
        }
        val query = queryParts.joinToString("&")
        return if (query.isEmpty()) "chat/$encodedSessionId" else "chat/$encodedSessionId?$query"
    }
    const val SHARE_PATTERN = "share/{text}?fileUris={fileUris}"
    fun share(text: String, fileUris: List<String>? = null): String {
        val encodedText = URLEncoder.encode(text, "UTF-8")
        val queryParts = mutableListOf<String>()
        fileUris?.takeIf { it.isNotEmpty() }?.let {
            queryParts.add("fileUris=${encodeUriList(it)}")
        }
        val query = queryParts.joinToString("&")
        return if (query.isEmpty()) "share/$encodedText" else "share/$encodedText?$query"
    }
    const val SKILLS = "skills"
    const val SKILL_DETAIL_PATTERN = "skills/{name}"

    // Skill names are arbitrary server-provided strings (may contain spaces/slashes), so they
    // must be encoded to survive as a single path segment.
    fun skillDetail(name: String) = "skills/${URLEncoder.encode(name, "UTF-8")}"

    const val MEMORY = "memory"

    const val TASKS = "tasks"
    const val TASK_DETAIL_PATTERN = "tasks/{jobId}"
    fun taskDetail(jobId: String) = "tasks/${URLEncoder.encode(jobId, "UTF-8")}"

    const val PROFILES = "profiles"

    const val PROJECTS = "projects"

    const val INSIGHTS = "insights"

    const val FILES_PATTERN = "files/{sessionId}"
    fun files(sessionId: String) = "files/${URLEncoder.encode(sessionId, "UTF-8")}"

    const val SETTINGS = "settings"
    const val DEFAULT_MODEL = "settings/defaultModel"
    const val CUSTOM_HEADERS = "settings/customHeaders"
    const val SERVERS = "settings/servers"
}

/**
 * Top-level router, the Compose/multi-screen analogue of iOS's `ContentView` (a plain switch on
 * `AuthManager.state`). [AuthRepository.state] is the single source of truth for which "area" of
 * the app is visible -- screens don't independently decide to bounce to Onboarding on a 401,
 * they just react to the shared state flipping. Navigation *within* the LoggedIn area (session
 * list -> chat) is ordinary NavHost navigation and doesn't touch this gate.
 */
@Composable
fun HermexNavGraph(
    appContainer: AppContainer,
    externalIntentDestination: StateFlow<HermexIntentDestination?>? = null,
    onExternalIntentConsumed: () -> Unit = {},
) {
    val isRestoring by appContainer.authRepository.isRestoring.collectAsStateWithLifecycle()
    val authState by appContainer.authRepository.state.collectAsStateWithLifecycle()
    val requestedDestination by externalIntentDestination?.collectAsStateWithLifecycle()
        ?: androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(null) }

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

    LaunchedEffect(authState, requestedDestination) {
        val destination = requestedDestination ?: return@LaunchedEffect
        if (authState !is AuthState.LoggedIn) return@LaunchedEffect
        val route = when (destination) {
            HermexIntentDestination.Sessions -> Routes.SESSION_LIST
            is HermexIntentDestination.Tasks -> Routes.TASKS
            is HermexIntentDestination.Session -> Routes.chat(destination.sessionId)
            is HermexIntentDestination.Task -> Routes.taskDetail(destination.jobId)
            HermexIntentDestination.NewChat -> Routes.NEW_CHAT
            is HermexIntentDestination.ShareContent -> {
                val fileUris = destination.uris.map { it.toString() }
                Routes.share(destination.text ?: "", fileUris = fileUris)
            }
        }
        navController.navigate(route) { launchSingleTop = true }
        onExternalIntentConsumed()
    }

    NavHost(
        navController = navController,
        startDestination = if (authState is AuthState.LoggedIn) Routes.SESSION_LIST else Routes.ONBOARDING,
    ) {
        composable(Routes.ONBOARDING) {
            val viewModel: OnboardingViewModel = viewModel(factory = appContainer.onboardingViewModelFactory())
            OnboardingScreen(viewModel = viewModel, modifier = Modifier.fillMaxSize())
        }
        composable(Routes.SESSION_LIST) { backStackEntry ->
            val viewModel: SessionListViewModel = viewModel(factory = appContainer.sessionListViewModelFactory())
            // SessionListViewModel's instance persists across a push-to-Settings-and-back trip,
            // since this back stack entry never leaves the graph -- so a header color change made
            // there won't otherwise be reflected here without an explicit signal. Only reloads the
            // color preference (fast, local), not a full session refetch.
            val shouldRefreshHeaderColor by backStackEntry.savedStateHandle
                .getStateFlow("refreshHeaderLogoColor", false)
                .collectAsStateWithLifecycle()
            LaunchedEffect(shouldRefreshHeaderColor) {
                if (shouldRefreshHeaderColor) {
                    viewModel.loadHeaderLogoColor()
                    backStackEntry.savedStateHandle["refreshHeaderLogoColor"] = false
                }
            }
            SessionListScreen(
                viewModel = viewModel,
                onOpenSession = { sessionId -> navController.navigate(Routes.chat(sessionId)) },
                onOpenSkills = { navController.navigate(Routes.SKILLS) },
                onOpenMemory = { navController.navigate(Routes.MEMORY) },
                onOpenTasks = { navController.navigate(Routes.TASKS) },
                onOpenProfiles = { navController.navigate(Routes.PROFILES) },
                onOpenProjects = { navController.navigate(Routes.PROJECTS) },
                onOpenInsights = { navController.navigate(Routes.INSIGHTS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(
            route = Routes.SHARE_PATTERN,
            arguments = listOf(
                navArgument("text") { type = NavType.StringType },
                navArgument("fileUris") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { backStackEntry ->
            val encodedText = backStackEntry.arguments?.getString("text").orEmpty()
            val sharedText = URLDecoder.decode(encodedText, "UTF-8")
            val rawFileUris = backStackEntry.arguments?.getString("fileUris")
            val fileUris = rawFileUris?.takeIf { it.isNotEmpty() }
                ?.let { decodeUriList(it) }
                ?.ifEmpty { null }

            val viewModel: SessionListViewModel = viewModel(factory = appContainer.sessionListViewModelFactory())

            ShareDestinationPicker(
                viewModel = viewModel,
                onSelectSession = { sessionId ->
                    navController.navigate(
                        Routes.chat(
                            sessionId = sessionId,
                            draft = sharedText.takeIf { it.isNotEmpty() },
                            uploadUris = fileUris,
                        ),
                    ) {
                        popUpTo(Routes.SHARE_PATTERN) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNewSession = {
                    viewModel.createSession { newSessionId ->
                        navController.navigate(
                            Routes.chat(
                                sessionId = newSessionId,
                                draft = sharedText.takeIf { it.isNotEmpty() },
                                uploadUris = fileUris,
                            ),
                        ) {
                            popUpTo(Routes.SHARE_PATTERN) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) { backStackEntry ->
            val viewModel: SettingsViewModel = viewModel(factory = appContainer.settingsViewModelFactory())
            SettingsScreen(
                viewModel = viewModel,
                onBack = {
                    navController.previousBackStackEntry?.savedStateHandle?.set("refreshHeaderLogoColor", true)
                    navController.popBackStack()
                },
                onOpenDefaultModel = { navController.navigate(Routes.DEFAULT_MODEL) },
                onOpenCustomHeaders = { navController.navigate(Routes.CUSTOM_HEADERS) },
                onOpenServers = { navController.navigate(Routes.SERVERS) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(Routes.NEW_CHAT) {
            val viewModel: SessionListViewModel = viewModel(factory = appContainer.sessionListViewModelFactory())
            LaunchedEffect(Unit) {
                viewModel.createSession { newSessionId ->
                    navController.navigate(Routes.chat(newSessionId)) {
                        popUpTo(Routes.NEW_CHAT) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        composable(Routes.SERVERS) {
                val viewModel: ServersViewModel = viewModel(factory = appContainer.serversViewModelFactory())
                ServersScreen(
                viewModel = viewModel,
                onBack = {
                    navController.previousBackStackEntry?.savedStateHandle?.set("refreshSettings", true)
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(Routes.CUSTOM_HEADERS) {
            val viewModel: CustomHeadersViewModel = viewModel(factory = appContainer.customHeadersViewModelFactory())
            CustomHeadersScreen(
                viewModel = viewModel,
                onBack = {
                    navController.previousBackStackEntry?.savedStateHandle?.set("refreshSettings", true)
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(Routes.INSIGHTS) {
            val viewModel: InsightsViewModel = viewModel(factory = appContainer.insightsViewModelFactory())
            InsightsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(Routes.PROFILES) {
            val viewModel: ProfilesViewModel = viewModel(factory = appContainer.profilesViewModelFactory())
            ProfilesScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(Routes.PROJECTS) {
            val viewModel: ProjectsViewModel = viewModel(factory = appContainer.projectsViewModelFactory())
            ProjectsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(Routes.TASKS) { backStackEntry ->
            val viewModel: TasksViewModel = viewModel(factory = appContainer.tasksViewModelFactory())
            // TasksViewModel's instance (and its list state) persists across a push-to-detail-
            // and-back trip, since this back stack entry never leaves the graph -- so a delete
            // on the detail screen won't otherwise be reflected here without an explicit signal.
            val shouldRefresh by backStackEntry.savedStateHandle
                .getStateFlow("refreshTasks", false)
                .collectAsStateWithLifecycle()
            LaunchedEffect(shouldRefresh) {
                if (shouldRefresh) {
                    viewModel.load()
                    backStackEntry.savedStateHandle["refreshTasks"] = false
                }
            }
            TasksScreen(
                viewModel = viewModel,
                onOpenTask = { jobId -> navController.navigate(Routes.taskDetail(jobId)) },
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(
            route = Routes.TASK_DETAIL_PATTERN,
            arguments = listOf(navArgument("jobId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val encodedJobId = backStackEntry.arguments?.getString("jobId").orEmpty()
            val jobId = URLDecoder.decode(encodedJobId, "UTF-8")
            val viewModel: TaskDetailViewModel = viewModel(
                key = jobId,
                factory = appContainer.taskDetailViewModelFactory(jobId),
            )
            TaskDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onDeleted = {
                    navController.previousBackStackEntry?.savedStateHandle?.set("refreshTasks", true)
                    navController.popBackStack()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(Routes.MEMORY) {
            val viewModel: MemoryViewModel = viewModel(factory = appContainer.memoryViewModelFactory())
            MemoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(Routes.SKILLS) {
            val viewModel: SkillsViewModel = viewModel(factory = appContainer.skillsViewModelFactory())
            SkillsScreen(
                viewModel = viewModel,
                onOpenSkill = { name -> navController.navigate(Routes.skillDetail(name)) },
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(
            route = Routes.SKILL_DETAIL_PATTERN,
            arguments = listOf(navArgument("name") { type = NavType.StringType }),
        ) { backStackEntry ->
            val encodedName = backStackEntry.arguments?.getString("name").orEmpty()
            val skillName = URLDecoder.decode(encodedName, "UTF-8")
            val viewModel: SkillDetailViewModel = viewModel(
                key = skillName,
                factory = appContainer.skillDetailViewModelFactory(skillName),
            )
            SkillDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable(
            route = Routes.CHAT_PATTERN,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("draft") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("uploadUris") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { backStackEntry ->
            val encodedSessionId = backStackEntry.arguments?.getString("sessionId").orEmpty()
            val sessionId = URLDecoder.decode(encodedSessionId, "UTF-8")
            val initialDraft = backStackEntry.arguments?.getString("draft")?.let { URLDecoder.decode(it, "UTF-8") }
            val rawUploadUris = backStackEntry.arguments?.getString("uploadUris")
            val pendingUploadUris = rawUploadUris?.takeIf { it.isNotEmpty() }
                ?.let { decodeUriList(it) }
                ?.ifEmpty { null }
            val viewModel: ChatViewModel = viewModel(
                key = sessionId,
                factory = appContainer.chatViewModelFactory(sessionId),
            )
            ChatScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSwitchedSession = { newSessionId ->
                    navController.navigate(Routes.chat(newSessionId)) {
                        popUpTo(Routes.chat(sessionId)) { inclusive = true }
                    }
                },
                onOpenWorkspace = { navController.navigate(Routes.files(sessionId)) },
                modifier = Modifier.fillMaxSize(),
                initialComposerDraft = initialDraft,
                pendingFileUploadUris = pendingUploadUris,
            )
        }
        composable(
            route = Routes.FILES_PATTERN,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val encodedSessionId = backStackEntry.arguments?.getString("sessionId").orEmpty()
            val sessionId = URLDecoder.decode(encodedSessionId, "UTF-8")
            val viewModel: WorkspaceViewModel = viewModel(
                key = sessionId,
                factory = appContainer.workspaceViewModelFactory(sessionId),
            )
            WorkspaceScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
