package com.hermex.android.sessions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hermex.android.ui.theme.HermexColors
import com.hermex.android.ui.theme.HermexRadii
import com.hermex.android.ui.theme.HermexSpacing
import com.hermex.android.ui.theme.toComposeColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Identifies one of the leading nav rows for selected-state highlighting in the wide-layout left
 * pane. Deliberately nav-graph-agnostic -- callers (e.g. HermexNavGraph) map their own route
 * constants onto this rather than this file knowing route strings.
 */
enum class SessionListNavItem { TASKS, SKILLS, MEMORY, INSIGHTS, PROFILES, PROJECTS }

/** Clears the floating New Chat + avatar row at the bottom of the list, independent of the
 * system navigation bar inset (added separately via `navigationBarsPadding()` where this is used). */
private val SessionListBottomControlsHeight = 96.dp

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
    // Only meaningful in the wide-layout left pane, where this screen stays resident while the
    // right pane's route changes -- compact callers never pass these, so nothing here is ever
    // "selected" there, matching today's behavior exactly.
    selectedNavItem: SessionListNavItem? = null,
    selectedSessionId: String? = null,
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
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(percent = 50),
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                            ),
                        )
                    } else {
                        Text(
                            text = "Hermex",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = uiState.headerLogoColor.toComposeColor(),
                        )
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
                        // Reuses the existing refresh() call that already backs pull-to-refresh
                        // below -- no new ViewModel/API surface needed for this action.
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh sessions")
                        }
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search sessions")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.AccountCircle, contentDescription = "Settings")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        floatingActionButton = {
            // BoxWithConstraints reports the width Scaffold gives this slot (effectively the
            // screen width), so narrow devices fall back to an icon-only pill rather than
            // clipping or crowding an "New Chat" label. Sharing this single slot (rather than a
            // second independent overlay) means the avatar and the CTA share the same Scaffold-
            // computed placement/inset handling.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val useCompactFab = maxWidth < 360.dp
                val onNewChat = { viewModel.createSession(onOpenSession) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Icon(
                            Icons.Filled.AccountCircle,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (useCompactFab) {
                        FloatingActionButton(
                            onClick = onNewChat,
                            shape = RoundedCornerShape(HermexRadii.Dialog),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            if (uiState.isCreatingSession) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Icon(Icons.Filled.Edit, contentDescription = "New chat")
                            }
                        }
                    } else {
                        ExtendedFloatingActionButton(
                            onClick = onNewChat,
                            shape = RoundedCornerShape(HermexRadii.Dialog),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            icon = {
                                if (uiState.isCreatingSession) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                    )
                                } else {
                                    Icon(Icons.Filled.Edit, contentDescription = null)
                                }
                            },
                            text = { Text("New Chat", fontWeight = FontWeight.SemiBold) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        SessionListBody(
            viewModel = viewModel,
            onOpenSession = onOpenSession,
            onOpenSkills = onOpenSkills,
            onOpenMemory = onOpenMemory,
            onOpenTasks = onOpenTasks,
            onOpenProfiles = onOpenProfiles,
            onOpenProjects = onOpenProjects,
            onOpenInsights = onOpenInsights,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            selectedNavItem = selectedNavItem,
            selectedSessionId = selectedSessionId,
        )
    }
}

/**
 * The scrollable body of the session list screen (nav rows + session list + status/error
 * banners), extracted from [SessionListScreen] so it can be reused as the persistent left-pane
 * content once the adaptive two-pane shell lands, without duplicating this rendering logic.
 * [SessionListScreen] retains ownership of the surrounding `Scaffold` (top bar, FAB/avatar row).
 */
@Composable
fun SessionListBody(
    viewModel: SessionListViewModel,
    onOpenSession: (String) -> Unit,
    onOpenSkills: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenInsights: () -> Unit,
    modifier: Modifier = Modifier,
    selectedNavItem: SessionListNavItem? = null,
    selectedSessionId: String? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = modifier,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
        ) {
            uiState.cacheStatusMessage?.let { message ->
                item(key = "cache-status-banner") {
                    Surface(
                        shape = RoundedCornerShape(HermexRadii.Accessory),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item(key = "nav-tasks") {
                val isSelected = selectedNavItem == SessionListNavItem.TASKS
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenTasks).padding(vertical = HermexSpacing.XS),
                    headlineContent = { NavItemLabel("Tasks", isSelected = isSelected) },
                    leadingContent = {
                        Icon(
                            Icons.Filled.DateRange,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                    ),
                )
            }
            item(key = "nav-skills") {
                val isSelected = selectedNavItem == SessionListNavItem.SKILLS
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenSkills).padding(vertical = HermexSpacing.XS),
                    headlineContent = { NavItemLabel("Skills", isSelected = isSelected) },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Build,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                    ),
                )
            }
            item(key = "nav-memory") {
                val isSelected = selectedNavItem == SessionListNavItem.MEMORY
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenMemory).padding(vertical = HermexSpacing.XS),
                    headlineContent = { NavItemLabel("Memory", isSelected = isSelected) },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Face,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                    ),
                )
            }
            item(key = "nav-insights") {
                val isSelected = selectedNavItem == SessionListNavItem.INSIGHTS
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenInsights).padding(vertical = HermexSpacing.XS),
                    headlineContent = { NavItemLabel("Insights", isSelected = isSelected) },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                    ),
                )
            }
            item(key = "nav-profiles") {
                val isSelected = selectedNavItem == SessionListNavItem.PROFILES
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenProfiles).padding(vertical = HermexSpacing.XS),
                    headlineContent = { NavItemLabel("Profiles", isSelected = isSelected) },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                    ),
                )
            }
            item(key = "nav-projects") {
                val isSelected = selectedNavItem == SessionListNavItem.PROJECTS
                ListItem(
                    modifier = Modifier.clickable(onClick = onOpenProjects).padding(vertical = HermexSpacing.XS),
                    headlineContent = { NavItemLabel("Projects", isSelected = isSelected) },
                    leadingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                    ),
                )
            }
            item(key = "sessions-header") {
                Text(
                    text = "Sessions",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 0.5.sp,
                    color = if (isSystemInDarkTheme()) HermexColors.DarkTertiaryLabel else HermexColors.LightTertiaryLabel,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = HermexSpacing.LG, bottom = HermexSpacing.SM),
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
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("No sessions yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tap + to start one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                uiState.filteredSessions.isEmpty() -> item(key = "no-search-matches") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("No matching sessions.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                else -> {
                    // Groups by day bucket without touching sort order: the server's
                    // existing ordering in filteredSessions is walked as-is, and a header
                    // is inserted only when the bucket changes from the previous row.
                    var previousBucket: String? = null
                    uiState.filteredSessions.forEach { session ->
                        val bucket = (session.lastMessageAt ?: session.createdAt)?.let(::sessionDateBucket)
                        if (bucket != null && bucket != previousBucket) {
                            item(key = "date-header-$bucket-${session.sessionId ?: session.hashCode()}") {
                                Text(
                                    text = bucket,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                                )
                            }
                            previousBucket = bucket
                        }
                        item(key = session.sessionId ?: session.hashCode()) {
                            SessionRow(
                                session = session,
                                onClick = { session.sessionId?.let(onOpenSession) },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                isSelected = session.sessionId != null && session.sessionId == selectedSessionId,
                            )
                        }
                    }
                }
            }
            item(key = "bottom-controls-clearance") {
                // The FAB row (New Chat + avatar) floats over this list and isn't otherwise
                // accounted for by Scaffold's innerPadding, so the last session row would
                // otherwise be permanently stuck behind it. navigationBarsPadding() stacks the
                // system nav bar inset on top of that fixed clearance -- both compact and wide
                // layouts draw edge-to-edge (see ChatComposer's own navigationBarsPadding() use),
                // so on-screen nav bar height must be cleared in addition to the FAB/avatar row.
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(SessionListBottomControlsHeight),
                )
            }
        }

        uiState.errorMessage?.let { message ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    shape = RoundedCornerShape(HermexRadii.Accessory),
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

/** A slightly bolder, more spaced nav-row label than plain body text, without going oversized. */
@Composable
private fun NavItemLabel(text: String, isSelected: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp,
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified,
    )
}

/** Buckets a session's most-recent-activity timestamp into "TODAY" / "YESTERDAY" / a short date
 * string, purely for display grouping -- callers must not use this for sorting or filtering. */
private fun sessionDateBucket(epochSeconds: Double): String {
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochSecond(epochSeconds.toLong()).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (date) {
        today -> "TODAY"
        today.minusDays(1) -> "YESTERDAY"
        else -> date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
    }
}
