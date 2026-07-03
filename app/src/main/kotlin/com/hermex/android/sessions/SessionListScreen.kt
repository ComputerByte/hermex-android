package com.hermex.android.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The nav destinations that live above the session list (Skills, and -- as each phase lands --
 * Memory/Tasks/Profiles/Projects/Insights) render as leading rows in one continuous scrollable
 * list here, mirroring the iOS app's single-screen layout rather than hiding them behind a
 * separate drawer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    viewModel: SessionListViewModel,
    onOpenSession: (String) -> Unit,
    onOpenSkills: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isSearchActive by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) searchFocusRequester.requestFocus()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        TextField(
                            value = uiState.searchQuery,
                            onValueChange = viewModel::onSearchQueryChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester),
                            placeholder = { Text("Search sessions") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                            ),
                        )
                    } else {
                        Text("Hermex")
                    }
                },
                actions = {
                    if (isSearchActive) {
                        IconButton(onClick = {
                            isSearchActive = false
                            viewModel.onSearchQueryChanged("")
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close search")
                        }
                    } else {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search sessions")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.AccountCircle, contentDescription = "Settings")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.createSession(onOpenSession) }) {
                if (uiState.isCreatingSession) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Add, contentDescription = "New session")
                }
            }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // Bottom padding clears the FAB, which floats over the list and isn't
                // otherwise accounted for by Scaffold's innerPadding.
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                item(key = "nav-tasks") {
                    ListItem(
                        modifier = Modifier.clickable(onClick = onOpenTasks),
                        headlineContent = { Text("Tasks") },
                        leadingContent = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                    )
                }
                item(key = "nav-skills") {
                    ListItem(
                        modifier = Modifier.clickable(onClick = onOpenSkills),
                        headlineContent = { Text("Skills") },
                        leadingContent = { Icon(Icons.Filled.Build, contentDescription = null) },
                    )
                }
                item(key = "nav-memory") {
                    ListItem(
                        modifier = Modifier.clickable(onClick = onOpenMemory),
                        headlineContent = { Text("Memory") },
                        leadingContent = { Icon(Icons.Filled.Face, contentDescription = null) },
                    )
                }
                item(key = "nav-insights") {
                    ListItem(
                        modifier = Modifier.clickable(onClick = onOpenInsights),
                        headlineContent = { Text("Insights") },
                        leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
                    )
                }
                item(key = "nav-profiles") {
                    ListItem(
                        modifier = Modifier.clickable(onClick = onOpenProfiles),
                        headlineContent = { Text("Active Profile") },
                        leadingContent = { Icon(Icons.Filled.Person, contentDescription = null) },
                    )
                }
                item(key = "nav-projects") {
                    ListItem(
                        modifier = Modifier.clickable(onClick = onOpenProjects),
                        headlineContent = { Text("Projects") },
                        leadingContent = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    )
                }
                item(key = "sessions-header") {
                    Text(
                        text = "Sessions",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                when {
                    uiState.isLoading -> item(key = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    uiState.sessions.isEmpty() -> item(key = "empty") {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("No sessions yet", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Tap + to start one.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    uiState.filteredSessions.isEmpty() -> item(key = "no-search-matches") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("No matching sessions.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    else -> items(uiState.filteredSessions, key = { it.sessionId ?: it.hashCode() }) { session ->
                        SessionRow(
                            session = session,
                            onClick = { session.sessionId?.let(onOpenSession) },
                        )
                    }
                }
            }

            uiState.errorMessage?.let { message ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
