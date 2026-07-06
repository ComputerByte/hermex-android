package com.hermex.android.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.hermex.android.BuildConfig
import com.hermex.android.R
import com.hermex.android.core.storage.AppIconVariant
import com.hermex.android.core.storage.HeaderLogoColor
import com.hermex.android.ui.theme.HermexReadableContent
import com.hermex.android.ui.theme.HermexRadii
import com.hermex.android.navigation.LocalHermexDrawerOpener
import com.hermex.android.ui.theme.toComposeColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenDefaultModel: () -> Unit,
    onOpenCustomHeaders: () -> Unit,
    onOpenServers: () -> Unit,
    modifier: Modifier = Modifier,
    // True only in the wide-layout right pane, where the persistent left pane already shows
    // "Settings" is the open section -- the top bar's own literal title adds nothing there, so
    // it's dropped rather than shown redundantly. The back button stays exactly as it is either way.
    isPaneMode: Boolean = false,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val openDrawer = LocalHermexDrawerOpener.current
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showAppIconPicker by remember { mutableStateOf(false) }
    var showInitialsDialog by remember { mutableStateOf(false) }
    var showNotificationEducation by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) {
                viewModel.setNotificationsEnabled(true)
            } else {
                viewModel.setNotificationsEnabled(false)
                showNotificationEducation = true
            }
        },
    )

    if (showNotificationEducation) {
        AlertDialog(
            onDismissRequest = { showNotificationEducation = false },
            shape = RoundedCornerShape(HermexRadii.Dialog),
            title = { Text("Notifications") },
            text = {
                Text("Notifications are disabled. Enable them in Settings → Apps → Hermex → Notifications.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showNotificationEducation = false
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${BuildConfig.APPLICATION_ID}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        // If the settings intent fails, just dismiss
                    }
                }) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationEducation = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showColorPicker) {
        HeaderLogoColorDialog(
            selected = uiState.headerLogoColor,
            onSelect = {
                viewModel.setHeaderLogoColor(it)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false },
        )
    }

    if (showInitialsDialog) {
        var draftInitials by remember { mutableStateOf(uiState.userInitials) }
        AlertDialog(
            onDismissRequest = { showInitialsDialog = false },
            shape = RoundedCornerShape(HermexRadii.Dialog),
            title = { Text("Set initials") },
            text = {
                TextField(
                    value = draftInitials,
                    onValueChange = { if (it.length <= 4) draftInitials = it.uppercase() },
                    singleLine = true,
                    label = { Text("Initials (max 4 chars)") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showInitialsDialog = false
                    viewModel.setUserInitials(draftInitials)
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showInitialsDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showAppIconPicker) {
        AppIconVariantDialog(
            selected = uiState.appIconVariant,
            onSelect = {
                viewModel.setAppIconVariant(it)
                showAppIconPicker = false
            },
            onDismiss = { showAppIconPicker = false },
        )
    }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            shape = RoundedCornerShape(HermexRadii.Dialog),
            title = { Text("Sign out?") },
            text = { Text("Signs out of the active server and returns to onboarding.") },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutConfirm = false
                    viewModel.signOut()
                }) {
                    Text("Sign Out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    if (!isPaneMode) {
                        Text(
                            "Settings",
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                if (uiState.isLoading) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                SectionLabel("Active Server")
                Card {
                    SettingsRow(
                        "Server",
                        uiState.activeServerName?.let { "$it — ${uiState.serverUrl}" } ?: (uiState.serverUrl ?: "--"),
                        onClick = onOpenServers,
                    )
                    SettingsRow("Status", if (uiState.serverUrl != null) "Connected" else "Not signed in")
                    SettingsRow("Default Model", uiState.defaultModel ?: "--", onClick = onOpenDefaultModel)
                    SettingsRow(
                        "Connection Headers",
                        if (uiState.customHeaderCount == 0) "None" else "${uiState.customHeaderCount} configured",
                        onClick = onOpenCustomHeaders,
                    )
                    SettingsRow("Version", uiState.serverVersion ?: "--", showDivider = false)
                }

                Spacer(Modifier.height(24.dp))
                SectionLabel("Chat")
                Card {
                    SettingsSwitchRow(
                        label = "Expand Thinking by Default",
                        description = "Thinking blocks start expanded instead of collapsed. Tapping a block still toggles it.",
                        checked = uiState.expandThinkingByDefault,
                        onCheckedChange = viewModel::setExpandThinkingByDefault,
                    )
                    SettingsSwitchRow(
                        label = "Expand Tool Calls by Default",
                        description = "Tool call blocks start expanded instead of collapsed. Tapping a block still toggles it.",
                        checked = uiState.expandToolCallsByDefault,
                        onCheckedChange = viewModel::setExpandToolCallsByDefault,
                        showDivider = false,
                    )
                }

                Spacer(Modifier.height(24.dp))
                SectionLabel("Notifications")
                Card {
                    SettingsSwitchRow(
                        label = "Response completion notifications",
                        description = "Notify me when a response finishes while Hermex is in the background.",
                        checked = uiState.notificationsEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val permission = Manifest.permission.POST_NOTIFICATIONS
                                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission)
                                        == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    ) {
                                        viewModel.setNotificationsEnabled(true)
                                    } else {
                                        notificationPermissionLauncher.launch(permission)
                                    }
                                } else {
                                    viewModel.setNotificationsEnabled(true)
                                }
                            } else {
                                viewModel.setNotificationsEnabled(false)
                            }
                        },
                        showDivider = false,
                    )
                }

                Spacer(Modifier.height(24.dp))
                SectionLabel("Appearance")
                Card {
                    SettingsRow(
                        "Header Logo Color",
                        uiState.headerLogoColor.displayName,
                        onClick = { showColorPicker = true },
                    )
                    ListItem(
                        headlineContent = { Text("User Name") },
                        supportingContent = { Text(uiState.userInitials) },
                        trailingContent = {
                            TextButton(onClick = { showInitialsDialog = true }) { Text("Edit") }
                        },
                    )
                    SettingsDivider()
                    SettingsRow(
                        "App Icon",
                        uiState.appIconVariant.displayName,
                        onClick = { showAppIconPicker = true },
                        showDivider = false,
                    )
                }

                Spacer(Modifier.height(24.dp))
                SectionLabel("App")
                Card {
                    SettingsRow("Version", BuildConfig.VERSION_NAME)
                    SettingsRow("Build", BuildConfig.VERSION_CODE.toString(), showDivider = false)
                }

                Spacer(Modifier.height(24.dp))
                SectionLabel("Account")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(HermexRadii.SettingsCard))
                        .padding(16.dp),
                ) {
                    Text(
                        "Signs out of the active server and returns to onboarding.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { showSignOutConfirm = true },
                        enabled = !uiState.isSigningOut,
                        shape = RoundedCornerShape(HermexRadii.Cell),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (uiState.isSigningOut) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Sign Out of This Server")
                        }
                    }
                }

                uiState.errorMessage?.let { message ->
                    Spacer(Modifier.height(16.dp))
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
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(HermexRadii.SettingsCard))
            .padding(horizontal = 16.dp),
    ) {
        content()
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun SettingsRow(label: String, value: String, showDivider: Boolean = true, onClick: (() -> Unit)? = null) {
    Column {
        Column(
            modifier = Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 14.dp),
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
        if (showDivider) {
            SettingsDivider()
        }
    }
}

@Composable
private fun HeaderLogoColorDialog(
    selected: HeaderLogoColor,
    onSelect: (HeaderLogoColor) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(HermexRadii.Dialog),
        title = { Text("Header Logo Color") },
        text = {
            Column {
                HeaderLogoColor.entries.forEach { color ->
                    ListItem(
                        modifier = Modifier.clickable { onSelect(color) },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(color.toComposeColor(), CircleShape),
                            )
                        },
                        headlineContent = { Text(color.displayName) },
                        trailingContent = {
                            if (color == selected) {
                                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun AppIconVariantDialog(
    selected: AppIconVariant,
    onSelect: (AppIconVariant) -> Unit,
    onDismiss: () -> Unit,
) {
    val isDarkTheme = isSystemInDarkTheme()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(HermexRadii.Dialog),
        title = { Text("App Icon") },
        text = {
            Column {
                AppIconVariant.entries.forEach { variant ->
                    ListItem(
                        modifier = Modifier.clickable { onSelect(variant) },
                        leadingContent = {
                            Image(
                                painter = painterResource(variant.previewMipmapRes(isDarkTheme)),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                            )
                        },
                        headlineContent = { Text(variant.displayName) },
                        supportingContent = { Text(variant.description) },
                        trailingContent = {
                            if (variant == selected) {
                                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

/** SYSTEM has no launcher icon of its own (see [AppIconVariant]) -- shown here as whichever of
 * Light/Dark it currently resolves to, matching [com.hermex.android.core.appicon.AppIconResolver]. */
private fun AppIconVariant.previewMipmapRes(isDarkTheme: Boolean): Int = when (this) {
    AppIconVariant.SYSTEM -> if (isDarkTheme) R.mipmap.ic_launcher_dark else R.mipmap.ic_launcher_light
    AppIconVariant.LIGHT -> R.mipmap.ic_launcher_light
    AppIconVariant.DARK -> R.mipmap.ic_launcher_dark
    AppIconVariant.DISCO -> R.mipmap.ic_launcher_disco
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean = true,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
        if (showDivider) {
            SettingsDivider()
        }
    }
}
