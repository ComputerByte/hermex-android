package com.hermex.android.insights

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermex.android.core.network.dto.InsightsDailyToken
import com.hermex.android.core.network.dto.InsightsModelBreakdown
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Usage Analytics") },
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    InsightsTimeframe.entries.forEachIndexed { index, timeframe ->
                        SegmentedButton(
                            selected = uiState.timeframe == timeframe,
                            onClick = { viewModel.selectTimeframe(timeframe) },
                            shape = SegmentedButtonDefaults.itemShape(index, InsightsTimeframe.entries.size),
                        ) {
                            Text(timeframe.label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                if (uiState.isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Spacer(Modifier.height(48.dp))
                        CircularProgressIndicator()
                    }
                } else {
                    val insights = uiState.insights
                    if (insights == null) {
                        Spacer(Modifier.height(32.dp))
                        Text(
                            uiState.errorMessage ?: "No insights available.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Spacer(Modifier.height(20.dp))
                        SectionLabel("${uiState.timeframe.label.uppercase(Locale.ROOT)}")
                        Card {
                            StatRow("Sessions", (insights.totalSessions ?: 0).formatCount())
                            StatRow("Messages", (insights.totalMessages ?: 0).formatCount())
                            StatRow("Input Tokens", (insights.totalInputTokens ?: 0).formatCount())
                            StatRow("Output Tokens", (insights.totalOutputTokens ?: 0).formatCount())
                            StatRow("Total Tokens", (insights.totalTokens ?: 0).formatCount())
                            StatRow("Estimated Cost", formatCost(insights.totalCost ?: 0.0), showDivider = false)
                        }

                        if (uiState.models.isNotEmpty()) {
                            Spacer(Modifier.height(24.dp))
                            SectionLabel("Models")
                            Card {
                                uiState.models.forEachIndexed { index, model ->
                                    ModelRow(model, showDivider = index < uiState.models.lastIndex)
                                }
                            }
                        }

                        if (uiState.recentDailyTokens.isNotEmpty()) {
                            Spacer(Modifier.height(24.dp))
                            SectionLabel("Recent Daily Tokens")
                            Card {
                                uiState.recentDailyTokens.forEachIndexed { index, day ->
                                    DailyTokenRow(day, showDivider = index < uiState.recentDailyTokens.lastIndex)
                                }
                            }
                        }

                        val peakDay = uiState.peakDay
                        val peakHour = uiState.peakHour
                        if (peakDay != null || peakHour != null) {
                            Spacer(Modifier.height(24.dp))
                            SectionLabel("Activity")
                            Card {
                                peakDay?.let {
                                    StatRow(
                                        "Peak Day",
                                        it.day ?: "--",
                                        trailingText = "${it.sessions ?: 0} sessions",
                                        showDivider = peakHour != null,
                                    )
                                }
                                peakHour?.let {
                                    StatRow(
                                        "Peak Hour",
                                        it.hour?.let { hour -> String.format(Locale.ROOT, "%02d:00", hour) } ?: "--",
                                        trailingText = "${it.sessions ?: 0} sessions",
                                        showDivider = false,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Source: server insights from the last ${insights.periodDays ?: uiState.timeframe.days} days.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp),
    ) {
        content()
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    trailingText: String? = null,
    showDivider: Boolean = true,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium)
            }
            trailingText?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
        if (showDivider) {
            androidx.compose.material3.HorizontalDivider()
        }
    }
}

@Composable
private fun ModelRow(model: InsightsModelBreakdown, showDivider: Boolean) {
    Column {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                model.model ?: "Unknown model",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            val parts = listOfNotNull(
                "${(model.totalTokens ?: 0).formatCount()} tokens",
                "${model.sessions ?: 0} sessions",
                model.cost?.let { formatCost(it) },
                model.displayShare?.let { "$it% share" },
            )
            Text(
                parts.joinToString("   "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showDivider) {
            androidx.compose.material3.HorizontalDivider()
        }
    }
}

@Composable
private fun DailyTokenRow(day: InsightsDailyToken, showDivider: Boolean) {
    Column {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(day.date ?: "--", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            val parts = listOfNotNull(
                "${day.totalTokens.formatCount()} tokens",
                "${day.sessions ?: 0} sessions",
                day.cost?.let { formatCost(it) },
            )
            Text(
                parts.joinToString("   "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showDivider) {
            androidx.compose.material3.HorizontalDivider()
        }
    }
}

private fun Int.formatCount(): String = String.format(Locale.US, "%,d", this)

private fun formatCost(cost: Double): String = String.format(Locale.US, "$%.4f", cost)
