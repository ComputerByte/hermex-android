package com.hermex.android

import android.content.Context
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hermex.android.auth.AuthRepository
import com.hermex.android.chat.ChatViewModel
import com.hermex.android.core.network.NetworkModule
import com.hermex.android.core.network.SseClient
import com.hermex.android.core.network.SseStreamSource
import com.hermex.android.core.storage.DataStoreChatPreferencesStore
import com.hermex.android.core.storage.DataStoreCookieStore
import com.hermex.android.core.storage.DataStoreCustomHeadersStore
import com.hermex.android.core.storage.DataStoreServerStore
import com.hermex.android.core.storage.InMemoryCookieStore
import com.hermex.android.core.storage.NoOpCustomHeadersStore
import com.hermex.android.insights.InsightsViewModel
import com.hermex.android.memory.MemoryViewModel
import com.hermex.android.models.DefaultModelViewModel
import com.hermex.android.onboarding.OnboardingViewModel
import com.hermex.android.profiles.ProfilesViewModel
import com.hermex.android.projects.ProjectsViewModel
import com.hermex.android.sessions.SessionListViewModel
import com.hermex.android.settings.CustomHeadersViewModel
import com.hermex.android.settings.ServersViewModel
import com.hermex.android.settings.SettingsViewModel
import com.hermex.android.skills.SkillDetailViewModel
import com.hermex.android.skills.SkillsViewModel
import com.hermex.android.tasks.TaskDetailViewModel
import com.hermex.android.tasks.TasksViewModel

/**
 * Manual dependency wiring for the whole app -- no Hilt for a 3-screen MVP (the dependency
 * graph is small enough that a DI framework's build-time cost isn't worth it yet; Hilt is the
 * natural upgrade once module/screen count grows).
 *
 * [authRepository] closes a two-way dependency: [NetworkModule]'s 401 interceptor needs a
 * callback into [AuthRepository], but [AuthRepository] needs [NetworkModule]'s cookie jar and
 * per-server API factory. The lambda passed to `NetworkModule` captures `authRepositoryRef` by
 * reference and is only ever invoked later (on an actual 401), by which point both are fully
 * constructed -- a standard way to break constructor-order circular deps in manual DI.
 */
class AppContainer(context: Context) {
    private val serverStore = DataStoreServerStore(context)
    private val chatPreferencesStore = DataStoreChatPreferencesStore(context)

    private lateinit var authRepositoryRef: AuthRepository

    // Placeholder scope only -- AuthRepository.restoreSavedServer() (called right after this is
    // built, see HermexApplication) repoints this via NetworkModule.useServer() before any real
    // request happens, so nothing ever actually reads/writes through these two.
    val networkModule: NetworkModule =
        NetworkModule(InMemoryCookieStore(), NoOpCustomHeadersStore) { authRepositoryRef.handleUnauthorized() }

    val authRepository: AuthRepository = AuthRepository(
        networkModule = networkModule,
        serverStore = serverStore,
        cookieStoreFactory = { serverId -> DataStoreCookieStore(context, serverId) },
        customHeadersStoreFactory = { serverId -> DataStoreCustomHeadersStore(context, serverId) },
    ).also { authRepositoryRef = it }

    private val sseClient: SseStreamSource = SseClient(networkModule.sseClient)

    fun onboardingViewModelFactory() = viewModelFactory {
        initializer { OnboardingViewModel(authRepository) }
    }

    fun sessionListViewModelFactory() = viewModelFactory {
        initializer { SessionListViewModel(authRepository) }
    }

    fun chatViewModelFactory(sessionId: String) = viewModelFactory {
        initializer { ChatViewModel(sessionId, authRepository, sseClient, chatPreferencesStore) }
    }

    fun skillsViewModelFactory() = viewModelFactory {
        initializer { SkillsViewModel(authRepository) }
    }

    fun skillDetailViewModelFactory(skillName: String) = viewModelFactory {
        initializer { SkillDetailViewModel(skillName, authRepository) }
    }

    fun memoryViewModelFactory() = viewModelFactory {
        initializer { MemoryViewModel(authRepository) }
    }

    fun tasksViewModelFactory() = viewModelFactory {
        initializer { TasksViewModel(authRepository) }
    }

    fun taskDetailViewModelFactory(jobId: String) = viewModelFactory {
        initializer { TaskDetailViewModel(jobId, authRepository) }
    }

    fun profilesViewModelFactory() = viewModelFactory {
        initializer { ProfilesViewModel(authRepository) }
    }

    fun projectsViewModelFactory() = viewModelFactory {
        initializer { ProjectsViewModel(authRepository) }
    }

    fun insightsViewModelFactory() = viewModelFactory {
        initializer { InsightsViewModel(authRepository) }
    }

    fun settingsViewModelFactory() = viewModelFactory {
        initializer {
            SettingsViewModel(
                authRepository,
                chatPreferencesStore,
                authRepository.customHeadersStoreForActiveServer() ?: NoOpCustomHeadersStore,
                serverStore,
            )
        }
    }

    fun customHeadersViewModelFactory() = viewModelFactory {
        initializer { CustomHeadersViewModel(authRepository.customHeadersStoreForActiveServer() ?: NoOpCustomHeadersStore) }
    }

    fun serversViewModelFactory() = viewModelFactory {
        initializer { ServersViewModel(authRepository, serverStore) }
    }

    fun defaultModelViewModelFactory() = viewModelFactory {
        initializer { DefaultModelViewModel(authRepository) }
    }
}
