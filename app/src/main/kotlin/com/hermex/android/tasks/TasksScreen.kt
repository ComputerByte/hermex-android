package com.hermex.android.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermex.android.core.network.dto.CronJob
import com.hermex.android.core.network.dto.CronJobStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    onOpenTask: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Tasks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.load() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                uiState.jobs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No scheduled tasks on this server.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> PullToRefreshBox(
                    isRefreshing = uiState.isLoading,
                    onRefresh = viewModel::load,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.jobs, key = { it.jobId ?: it.hashCode() }) { job ->
                        TaskRow(job = job, onClick = { job.jobId?.let(onOpenTask) })
                    }
                }
            }
            }

            uiState.errorMessage?.let { message ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 8.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Text(text = message, color = MaterialTheme.colorScheme.error)
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
    ListItem(
        modifier = modifier.clickable(enabled = job.jobId != null, onClick = onClick),
        headlineContent = { Text(job.displayName) },
        supportingContent = {
            val schedule = job.effectiveScheduleText
            if (!schedule.isNullOrBlank()) {
                Text(schedule, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingContent = {
            val (label, color) = statusLabelAndColor(job.status)
            Text(text = label, color = color, style = MaterialTheme.typography.labelMedium)
        },
    )
}

@Composable
private fun statusLabelAndColor(status: CronJobStatus): Pair<String, Color> = when (status) {
    CronJobStatus.ACTIVE -> "Active" to MaterialTheme.colorScheme.primary
    CronJobStatus.PAUSED -> "Paused" to MaterialTheme.colorScheme.onSurfaceVariant
    CronJobStatus.OFF -> "Off" to MaterialTheme.colorScheme.onSurfaceVariant
    CronJobStatus.ERROR -> "Error" to MaterialTheme.colorScheme.error
}
