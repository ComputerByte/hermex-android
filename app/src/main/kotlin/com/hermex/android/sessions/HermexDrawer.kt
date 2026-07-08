package com.hermex.android.sessions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hermex.android.core.storage.HeaderLogoColor
import com.hermex.android.ui.theme.HermexColors
import com.hermex.android.ui.theme.toComposeColor

@Composable
fun HermexDrawerContent(
    uiState: SessionListUiState,
    selectedNavItem: SessionListNavItem?,
    onNavItemSelected: (SessionListNavItem) -> Unit,
    onOpenSession: (String) -> Unit,
    onOpenSessions: () -> Unit,
    onCreateSession: () -> Unit,
    onOpenSettings: () -> Unit,
    initialText: String?,
    headerLogoColor: HeaderLogoColor = HeaderLogoColor.DEFAULT,
    modifier: Modifier = Modifier,
) {
    ModalDrawerSheet(
        modifier = modifier,
        drawerContainerColor = Color.Black,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = "Hermex",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = headerLogoColor.toComposeColor(),
                    modifier = Modifier.padding(start = 20.dp, top = 32.dp, bottom = 24.dp),
                )
                DrawerNavItem(
                    icon = Icons.Filled.Forum,
                    label = "Chats",
                    isSelected = false,
                    onClick = onOpenSessions,
                )
                DrawerNavItem(
                    icon = Icons.Filled.DateRange,
                    label = "Tasks",
                    isSelected = selectedNavItem == SessionListNavItem.TASKS,
                    onClick = { onNavItemSelected(SessionListNavItem.TASKS) },
                )
                DrawerNavItem(
                    icon = Icons.Filled.Build,
                    label = "Skills",
                    isSelected = selectedNavItem == SessionListNavItem.SKILLS,
                    onClick = { onNavItemSelected(SessionListNavItem.SKILLS) },
                )
                DrawerNavItem(
                    icon = Icons.Filled.Face,
                    label = "Memory",
                    isSelected = selectedNavItem == SessionListNavItem.MEMORY,
                    onClick = { onNavItemSelected(SessionListNavItem.MEMORY) },
                )
                DrawerNavItem(
                    icon = Icons.Filled.Info,
                    label = "Insights",
                    isSelected = selectedNavItem == SessionListNavItem.INSIGHTS,
                    onClick = { onNavItemSelected(SessionListNavItem.INSIGHTS) },
                )
                DrawerNavItem(
                    icon = Icons.AutoMirrored.Filled.List,
                    label = "Projects",
                    isSelected = selectedNavItem == SessionListNavItem.PROJECTS,
                    onClick = { onNavItemSelected(SessionListNavItem.PROJECTS) },
                )
                DrawerNavItem(
                    icon = Icons.Filled.Settings,
                    label = "Settings",
                    isSelected = false,
                    onClick = onOpenSettings,
                )

                val recentSessions = uiState.filteredSessions.take(5)
                if (recentSessions.isNotEmpty()) {
                    Text(
                        text = "Recents",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
                    )
                    recentSessions.forEach { session ->
                        Text(
                            text = session.title ?: "Untitled",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { session.sessionId?.let(onOpenSession) }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.clickable(onClick = onOpenSettings),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3A3A3C)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (initialText != null) {
                            Text(
                                text = initialText,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        } else {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }

                Button(
                    onClick = onCreateSession,
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "New Chat",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
        )
    }
}
