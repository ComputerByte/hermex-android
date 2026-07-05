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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermex.android.core.network.dto.InsightsDailyToken
import com.hermex.android.core.network.dto.InsightsModelBreakdown
import com.hermex.android.core.network.dto.SessionSummary
import com.hermex.android.ui.theme.HermexRadii
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
                title = {
                    Text(
                        "Usage Analytics",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { viewModel.load() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
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
                // Nothing has ever loaded yet -- full-screen spinner, matching iOS's
                // `isLoading && !hasLoadedAnalytics`.
                uiState.isLoading && !uiState.hasLoadedAnalytics -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Loading analytics...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Never loaded anything and the last attempt failed outright (both server
                // insights and the sessions fallback failed).
                uiState.errorMessage != null && !uiState.hasLoadedAnalytics -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Could Not Load Analytics",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            uiState.errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.load() },
                            shape = RoundedCornerShape(HermexRadii.Cell),
                        ) { Text("Try Again") }
                    }
                }

                // No error, but genuinely nothing to show (e.g. a brand-new server with no
                // sessions yet).
                !uiState.hasLoadedAnalytics -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No Data",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Session usage data will appear here once you have conversations.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                else -> InsightsContent(uiState, viewModel::selectTimeframe)
            }
        }
    }
}

@Composable
private fun InsightsContent(uiState: InsightsUiState, onSelectTimeframe: (InsightsTimeframe) -> Unit) {
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
                    onClick = { onSelectTimeframe(timeframe) },
                    shape = SegmentedButtonDefaults.itemShape(index, InsightsTimeframe.entries.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                        activeBorderColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(timeframe.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel(uiState.periodTitle.uppercase(Locale.ROOT))
        Card {
            StatRow("Sessions", uiState.sessionCount.formatCount())
            StatRow("Messages", uiState.totalMessages.formatCount())
            StatRow("Input Tokens", uiState.totalInputTokens.formatCount())
            StatRow("Output Tokens", uiState.totalOutputTokens.formatCount())
            StatRow("Total Tokens", uiState.totalTokens.formatCount())
            StatRow("Estimated Cost", formatCost(uiState.estimatedCost), showDivider = false)
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

        if (uiState.topSessions.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionLabel("Top Sessions")
            Card {
                uiState.topSessions.forEachIndexed { index, session ->
                    TopSessionRow(session, showDivider = index < uiState.topSessions.lastIndex)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            uiState.sourceDescription,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(HermexRadii.SettingsCard))
            .padding(horizontal = 16.dp),
    ) {
        content()
    }
}

@Composable
private fun InsightsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
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
                .padding(vertical = 14.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
            InsightsDivider()
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
                fontWeight = FontWeight.SemiBold,
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
            InsightsDivider()
        }
    }
}

@Composable
private fun DailyTokenRow(day: InsightsDailyToken, showDivider: Boolean) {
    Column {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(day.date ?: "--", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
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
            InsightsDivider()
        }
    }
}

@Composable
private fun TopSessionRow(session: SessionSummary, showDivider: Boolean) {
    Column {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                session.title ?: "Untitled Session",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            val total = (session.inputTokens ?: 0) + (session.outputTokens ?: 0)
            val parts = listOfNotNull(
                "${total.formatCount()} tokens",
                session.estimatedCost?.takeIf { it > 0 }?.let { formatCost(it) },
            )
            Text(
                parts.joinToString("   "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showDivider) {
            InsightsDivider()
        }
    }
}

private fun Int.formatCount(): String = String.format(Locale.US, "%,d", this)

private fun formatCost(cost: Double): String = String.format(Locale.US, "$%.4f", cost)
