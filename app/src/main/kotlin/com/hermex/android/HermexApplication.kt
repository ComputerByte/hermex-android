package com.hermex.android

import android.app.Application
import coil3.SingletonImageLoader
import com.hermex.android.core.notifications.HermexNotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HermexApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        HermexNotificationChannels.ensureCreated(this)
        appContainer = AppContainer(this)
        SingletonImageLoader.setSafe { context -> appContainer.newImageLoader(context) }
        applicationScope.launch { appContainer.authRepository.restoreSavedServer() }
        applicationScope.launch { appContainer.reconcileAppIcon() }
        applicationScope.launch { appContainer.pruneOfflineCaches() }
    }
}
