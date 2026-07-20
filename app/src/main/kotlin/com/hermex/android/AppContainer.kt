package com.hermex.android

import android.content.Context
import android.content.res.Configuration
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.hermex.android.auth.AuthRepository
import com.hermex.android.chat.ChatViewModel
import com.hermex.android.chat.ContentResolverAttachmentReader
import com.hermex.android.chat.RetainedChatViewModelStoreOwner
import com.hermex.android.core.appicon.AppIconSwitcher
import com.hermex.android.core.appicon.PackageManagerAppIconAliasWriter
import com.hermex.android.core.cache.HermexDatabase
import com.hermex.android.core.cache.MIGRATION_1_2
import com.hermex.android.core.cache.MIGRATION_2_3
import com.hermex.android.core.cache.OfflineCacheRepository
import com.hermex.android.core.cache.RoomOfflineCacheRepository
import com.hermex.android.core.network.NetworkModule
import com.hermex.android.core.network.SseClient
import com.hermex.android.core.network.SseStreamSource
import com.hermex.android.core.foreground.AndroidStreamingForegroundController
import com.hermex.android.core.notifications.HermexNotifier
import com.hermex.android.core.notifications.HermexResponseCompletionNotifier
import com.hermex.android.core.storage.DataStoreAppearancePreferencesStore
import com.hermex.android.core.storage.DataStoreChatPreferencesStore
import com.hermex.android.core.storage.DataStoreCookieStore
import com.hermex.android.core.storage.DataStoreCustomHeadersStore
import com.hermex.android.core.storage.DataStoreServerStore
import com.hermex.android.core.storage.EncryptedCookieStore
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
import com.hermex.android.workspace.WorkspaceViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
class AppContainer(val context: Context) {
    private val applicationContext = context.applicationContext
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val serverStore = DataStoreServerStore(context)
    private val chatPreferencesStore = DataStoreChatPreferencesStore(context)
    private val appearancePreferencesStore = DataStoreAppearancePreferencesStore(context)

    /** Best-effort foreground/background signal. True while at least one activity is started.
     * Updated by [MainActivity.onStart] / [MainActivity.onStop]. Single-activity app, so this
     * faithfully tracks "app is on screen." Read by [HermexResponseCompletionNotifier] to gate
     * response-completion notifications. */
    @Volatile
    var isAppInForeground: Boolean = false
        private set

    fun setAppInForeground(foreground: Boolean) {
        isAppInForeground = foreground
    }

    /** Cached copy of [ChatPreferencesStore.loadNotificationsEnabled] -- loaded once on app start
     * and kept in sync by [SettingsViewModel.setNotificationsEnabled]. Read synchronously by
     * [HermexNotifier] (wired below) at notification time, never via a suspend DataStore read on
     * the hot notification path. */
    @Volatile
    var notificationsEnabled: Boolean = false
        private set

    fun setNotificationsEnabled(enabled: Boolean) {
        notificationsEnabled = enabled
    }
    // Stored (rather than referencing the constructor's `context` param directly) so it's usable
    // from a regular member function like chatViewModelFactory -- an unstored constructor
    // parameter is only visible inside property initializers/init blocks, not later methods.
    private val contentResolver = context.contentResolver

    // Structured, queryable offline cache (session lists + chat/message history) -- distinct
    // from the DataStore-backed stores above, which are for small preference values, not this
    // kind of payload. Never holds cookies/custom headers/secrets.
    private val database: HermexDatabase = Room
        .databaseBuilder(context.applicationContext, HermexDatabase::class.java, "hermex_cache.db")
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .build()
    private val offlineCacheRepository: OfflineCacheRepository = RoomOfflineCacheRepository(database.cachedSessionDao())

    /** [AppIconSwitcher.resolvedAlias] for `SYSTEM` is re-evaluated from this lambda every time
     * it's called (not cached) -- see [reconcileAppIcon] for where that matters. */
    val appIconSwitcher = AppIconSwitcher(
        aliasWriter = PackageManagerAppIconAliasWriter(context),
        isDarkModeProvider = {
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        },
    )

    private lateinit var authRepositoryRef: AuthRepository

    /** The process-level owner that keeps each session's ChatViewModel alive across navigation. */
    internal val retainedChatViewModelStoreOwner = RetainedChatViewModelStoreOwner()

    init {
        // HermexNotifier itself (not each individual caller) enforces the in-app notification
        // preference for every public entry point it exposes -- wiring this once here means
        // there's exactly one path to that Volatile-cached value, not a per-call-site copy.
        HermexNotifier.isNotificationsEnabled = { notificationsEnabled }
        // Load the cached notification preference so the notifier gate has a value
        // before the user ever opens Settings. Default false until the DataStore read completes.
        CoroutineScope(Dispatchers.IO).launch {
            notificationsEnabled = chatPreferencesStore.loadNotificationsEnabled()
        }
    }

    // Placeholder scope only -- AuthRepository.restoreSavedServer() (called right after this is
    // built, see HermexApplication) repoints this via NetworkModule.useServer() before any real
    // request happens, so nothing ever actually reads/writes through these two.
    val networkModule: NetworkModule =
        NetworkModule(InMemoryCookieStore(), NoOpCustomHeadersStore) { authRepositoryRef.handleUnauthorized() }

    val authRepository: AuthRepository = AuthRepository(
        networkModule = networkModule,
        serverStore = serverStore,
        cookieStoreFactory = { serverId -> EncryptedCookieStore(context, serverId, DataStoreCookieStore(context, serverId)) },
        customHeadersStoreFactory = { serverId -> DataStoreCustomHeadersStore(context, serverId) },
        onServerForgotten = { serverId -> offlineCacheRepository.clearServer(serverId) },
    ).also { authRepositoryRef = it }

    init {
        applicationScope.launch {
            var previousState = authRepository.state.value
            authRepository.state.collect { state ->
                if (state != previousState) {
                    // Logout, login, and server switches all define a new authentication scope.
                    // Cancel retained streams so none can keep using a superseded network scope.
                    retainedChatViewModelStoreOwner.reset()
                    previousState = state
                }
            }
        }
    }

    private val sseClient: SseStreamSource = SseClient(networkModule.sseClient)

    /** Coil's default singleton has no network fetcher in Coil 3, and a standalone fetcher would
     * not share Hermex's session cookies/custom proxy headers. Reusing [NetworkModule.restClient]
     * makes `/api/file/raw` image requests authenticate exactly like every Retrofit call. */
    fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(networkModule.restClient))
        }
        .build()

    fun onboardingViewModelFactory() = viewModelFactory {
        initializer { OnboardingViewModel(authRepository) }
    }

    fun sessionListViewModelFactory() = viewModelFactory {
        initializer { SessionListViewModel(authRepository, appearancePreferencesStore, offlineCacheRepository, chatPreferencesStore) }
    }

    fun chatViewModelFactory(sessionId: String) = viewModelFactory {
        initializer {
            ChatViewModel(
                sessionId,
                authRepository,
                sseClient,
                chatPreferencesStore,
                offlineCacheRepository,
                ContentResolverAttachmentReader(contentResolver),
                HermexResponseCompletionNotifier(
                    context = applicationContext,
                    isAppInForeground = { this@AppContainer.isAppInForeground },
                ),
                AndroidStreamingForegroundController(applicationContext),
            )
        }
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

    fun workspaceViewModelFactory(sessionId: String) = viewModelFactory {
        initializer { WorkspaceViewModel(sessionId, authRepository) }
    }

    fun settingsViewModelFactory() = viewModelFactory {
        initializer {
            val vm = SettingsViewModel(
                authRepository,
                chatPreferencesStore,
                authRepository.customHeadersStoreForActiveServer() ?: NoOpCustomHeadersStore,
                serverStore,
                appearancePreferencesStore,
                appIconSwitcher,
            )
            vm.onNotificationsChanged = { enabled -> this@AppContainer.setNotificationsEnabled(enabled) }
            vm
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

    /** Re-applies the stored [com.hermex.android.core.storage.AppIconVariant] on every app start
     * (see [HermexApplication]) -- Android has no live notification for "the device's dark/light
     * mode changed while the app was closed," so `SYSTEM` has to be re-resolved here rather than
     * observed continuously. Safe to call unconditionally; re-enabling an already-enabled alias
     * is a no-op. */
    suspend fun reconcileAppIcon() {
        appIconSwitcher.applyVariant(appearancePreferencesStore.loadAppIconVariant())
    }

    /** Bounds every configured server's offline cache under a conservative retention policy (see
     * [com.hermex.android.core.cache.sessionIdsToPrune]) once per app process start (see
     * [HermexApplication]) -- never tied to a session-list refresh, a chat send, or any other
     * per-action path. Scoped to [com.hermex.android.core.storage.ServerStore]'s own server list,
     * so a server the user never configured (or already removed) is never touched. No session is
     * known to be "active" this early in the app's lifecycle -- no chat screen has loaded yet --
     * so nothing is passed as the session to preserve; see [pruneAllServerCaches] (kept as a
     * standalone, plain-JVM-unit-testable function) for the actual per-server loop. */
    suspend fun pruneOfflineCaches() {
        pruneAllServerCaches(serverStore.load().servers, offlineCacheRepository)
    }
}
