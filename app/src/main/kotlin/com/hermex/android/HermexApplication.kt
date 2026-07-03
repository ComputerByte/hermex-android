package com.hermex.android

import android.app.Application
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
        appContainer = AppContainer(this)
        applicationScope.launch { appContainer.authRepository.restoreSavedServer() }
        applicationScope.launch { appContainer.reconcileAppIcon() }
    }
}
