package com.hermex.android.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermex.android.core.network.dto.CronJob
import com.hermex.android.core.network.dto.CronJobStatus
import com.hermex.android.navigation.LocalHermexDrawerOpener
import com.hermex.android.ui.theme.HermexErrorBanner
import com.hermex.android.ui.theme.HermexReadableContent
import com.hermex.android.ui.theme.HermexRadii

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    onOpenTask: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    // True only in the wide-layout right pane, where the persistent left pane already shows
    // "Tasks" is the open section -- the top bar's own literal title adds nothing there, so it's
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
                            "Tasks",
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
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                uiState.jobs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No tasks on this server.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Scheduled jobs will appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }

                else -> PullToRefreshBox(
                    isRefreshing = uiState.isLoading,
                    onRefresh = viewModel::load,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        items(uiState.jobs, key = { it.jobId ?: it.hashCode() }) { job ->
                            TaskRow(
                                job = job,
                                onClick = { job.jobId?.let(onOpenTask) },
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
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

@Composable
private fun TaskRow(
    job: CronJob,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(HermexRadii.Cell),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        ListItem(
            modifier = Modifier.clickable(enabled = job.jobId != null, onClick = onClick),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(job.displayName, fontWeight = FontWeight.SemiBold) },
            supportingContent = {
                val schedule = job.effectiveScheduleText
                if (!schedule.isNullOrBlank()) {
                    Text(schedule, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            trailingContent = {
                val (label, color) = statusLabelAndColor(job.status)
                Surface(
                    shape = RoundedCornerShape(percent = 50),
                    color = color.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = label,
                        color = color,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            },
        )
    }
}

@Composable
private fun statusLabelAndColor(status: CronJobStatus): Pair<String, Color> = when (status) {
    CronJobStatus.ACTIVE -> "Active" to MaterialTheme.colorScheme.primary
    CronJobStatus.PAUSED -> "Paused" to MaterialTheme.colorScheme.onSurfaceVariant
    CronJobStatus.OFF -> "Off" to MaterialTheme.colorScheme.onSurfaceVariant
    CronJobStatus.ERROR -> "Error" to MaterialTheme.colorScheme.error
}
