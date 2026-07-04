package com.hermex.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.hermex.android.navigation.HermexNavGraph
import com.hermex.android.navigation.HermexIntentDestination
import com.hermex.android.navigation.hermexDestination
import com.hermex.android.ui.theme.HermexTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val externalIntentDestination = MutableStateFlow<HermexIntentDestination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        externalIntentDestination.value = intent?.hermexDestination()
        enableEdgeToEdge()
        val appContainer = (application as HermexApplication).appContainer
        setContent {
            HermexTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    HermexNavGraph(
                        appContainer = appContainer,
                        externalIntentDestination = externalIntentDestination,
                        onExternalIntentConsumed = { externalIntentDestination.value = null },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as HermexApplication).appContainer.setAppInForeground(true)
    }

    override fun onStop() {
        super.onStop()
        (application as HermexApplication).appContainer.setAppInForeground(false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        externalIntentDestination.value = intent.hermexDestination()
    }
}
