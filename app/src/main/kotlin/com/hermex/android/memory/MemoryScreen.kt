package com.hermex.android.memory

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermex.android.chat.MarkdownText
import com.hermex.android.navigation.LocalHermexDrawerOpener
import com.hermex.android.sessions.relativeTimeText
import com.hermex.android.ui.theme.HermexErrorBanner
import com.hermex.android.ui.theme.HermexReadableContent
import com.hermex.android.ui.theme.HermexRadii

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    viewModel: MemoryViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    // True only in the wide-layout right pane, where the persistent left pane already shows
    // "Memory" is the open section -- the top bar's own literal title adds nothing there, so it's
    // dropped rather than shown redundantly. Back/Refresh stay exactly as they are either way.
    isPaneMode: Boolean = false,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val openDrawer = LocalHermexDrawerOpener.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    if (!isPaneMode) {
                        Text(
                            "Memory",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { openDrawer() }) {
                        Icon(Icons.Filled.Menu, contentDescription = "Open menu")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
    HermexReadableContent(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = viewModel::load,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    MemorySection(title = "Memory", section = uiState.memorySection)
                    Spacer(Modifier.height(24.dp))
                    MemorySection(title = "User", section = uiState.userSection)
                    Spacer(Modifier.height(24.dp))
                    MemorySection(title = "Soul", section = uiState.soulSection)
                }
            }

            uiState.errorMessage?.let { message ->
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
                    HermexErrorBanner(message = message, onRetry = { viewModel.load() })
                }
            }
        }
    }
}
}

@Composable
private fun MemorySection(
    title: String,
    section: MemorySectionUi?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            section?.mtime?.let { mtime ->
                Text(
                    text = " · ${relativeTimeText(mtime)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(HermexRadii.SettingsCard),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                val sectionContent = section?.content?.takeIf { it.isNotBlank() } ?: "No content."
                if (sectionContent == "No content." || section?.content == null) {
                    Text(
                        text = "No content.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    MarkdownText(
                        markdown = sectionContent,
                        textColor = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
