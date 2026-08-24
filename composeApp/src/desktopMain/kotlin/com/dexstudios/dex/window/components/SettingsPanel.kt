package com.dexstudios.dex.window.components
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_account_circle
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_bolt
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_computer
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_do_not_disturb
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_folder
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_info
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_palette
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_settings
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_touch_app
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_tune
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_warning
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_wifi
import com.dexstudios.dex.core.designsystem.generated.resources.profile_avatar
import com.dexstudios.dex.core.designsystem.icons.AnimatedDndBell
import com.dexstudios.dex.core.designsystem.theme.DeXTheme
import com.dexstudios.dex.core.network.DeviceConfig
import com.dexstudios.dex.mirror.toImageBitmap
import com.dexstudios.dex.window.DockedWindowStateController
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import java.awt.Desktop
import java.io.File
import java.net.URI
import javax.swing.JFileChooser

/**
 * SettingsPanel:
 * - Profile & Account header (avatar — Google picture when signed in, display name, email,
 *   Sign Out; neutral placeholders when signed out, never fabricated account data)
 * - Categorized preferences:
 *   1. Connection (Do Not Disturb switch, UPnP Port Forwarding toggle)
 *   2. Dev Tools (ADB Connect & Auto-Connect Hotspot)
 *   3. Identity (Device Name editor, Google OAuth loopback sign-in)
 *   4. Appearance (Theme: System / Dark / Light)
 *   5. Interaction (Wiggle-to-Open Menu + read-only shortcut reference card: global
 *      Show/Hide toggle, Shift+Click instant exit, force-exit during transfers)
 *   6. Storage (Download Location folder chooser — persisted via DeviceConfig.downloadDir;
 *      the modal-dialog guard is raised during pick)
 *   7. About & Maintenance (version from AppBuildConfig, GitHub link, Reset Identity & Trust
 *      behind a confirmation dialog)
 */
@Composable
fun SettingsPanel(
    onClose: () -> Unit = {},
    controller: DockedWindowStateController? = null,
    modifier: Modifier = Modifier,
    deviceConfig: DeviceConfig = koinInject(),
    discoveryEngine: com.dexstudios.dex.core.network.DiscoveryEngine = koinInject(),
) {
    val coroutineScope = rememberCoroutineScope()
    val googleProfile by deviceConfig.googleProfileFlow.collectAsState()
    val isDndActive by deviceConfig.dndEnabledFlow.collectAsState()
    val isAutoAdbHotspotActive by deviceConfig.autoAdbHotspotEnabledFlow.collectAsState()
    val themeOverride by deviceConfig.themeOverrideFlow.collectAsState()
    val isWiggleEnabled by deviceConfig.wiggleEnabledFlow.collectAsState()
    val isUpnpEnabled by deviceConfig.upnpEnabledFlow.collectAsState()
    val downloadDirPref by deviceConfig.downloadDirFlow.collectAsState()
    val deviceAlias by deviceConfig.aliasFlow.collectAsState()
    var showAliasEditor by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }

    // Real Google avatar when signed in; fetched and decoded off the UI thread via the
    // shared skia helper. Falls back to the bundled placeholder when signed out or on
    // any fetch/decode failure.
    val avatarBitmap by produceState<ImageBitmap?>(initialValue = null, googleProfile.picture) {
        val url = googleProfile.picture
        if (url.isBlank()) return@produceState
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    java.net.URI(url).toURL().readBytes().toImageBitmap()
                }.getOrNull()
            }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 24.dp, top = 20.dp, end = 16.dp, bottom = 16.dp),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_fluent_settings),
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(end = 6.dp).size(18.dp),
                    )
                    Text(
                        text = "Settings",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        try {
                            Desktop.getDesktop().browse(URI(com.dexstudios.dex.AppBuildConfig.REPO_URL))
                        } catch (_: Exception) {}
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_fluent_info),
                        contentDescription = "About",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 6.dp).size(16.dp),
                    )
                    Text(
                        text = "DeX v${com.dexstudios.dex.AppBuildConfig.VERSION_NAME}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Scrollable Settings Sections
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(end = 8.dp),
        ) {
            // Profile Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val avatar = avatarBitmap
                        if (avatar != null) {
                            Image(
                                bitmap = avatar,
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape),
                            )
                        } else {
                            Image(
                                painter = painterResource(Res.drawable.profile_avatar),
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape),
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = googleProfile.name.ifBlank { "DeX Desktop" },
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = googleProfile.email.ifBlank { "Not signed in" },
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = if (googleProfile.email.isNotBlank()) {
                                    "Same-account devices auto-trusted"
                                } else {
                                    "Sign in below to auto-trust your devices"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }

                    if (googleProfile.email.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                                .clickable { deviceConfig.signOut() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "Sign Out",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            // Connection Settings
            SettingsSectionHeader("Connection")
            SettingsCard {
                SettingsItem(
                    title = "Do Not Disturb",
                    subtitle = "Auto-reject all pairing/transfer requests",
                    badge = if (isDndActive) "ON" else "OFF",
                    isBadgeDanger = isDndActive,
                    onClick = { deviceConfig.dndEnabled = !isDndActive },
                    iconContent = { tint ->
                        AnimatedDndBell(
                            isDndActive = isDndActive,
                            size = 20.dp,
                            tint = tint,
                            contentDescription = "Do Not Disturb",
                        )
                    },
                )
                SettingsItem(
                    icon = painterResource(Res.drawable.ic_fluent_wifi),
                    title = "UPnP Port Forwarding",
                    subtitle = "Allow file transfers over the Internet via WAN router forwarding",
                    badge = if (isUpnpEnabled) "ON" else "OFF",
                    onClick = { deviceConfig.upnpEnabled = !isUpnpEnabled },
                )
            }

            // Developer Tools
            SettingsSectionHeader("Developer Tools")
            SettingsCard {
                SettingsItem(
                    icon = painterResource(Res.drawable.ic_fluent_tune),
                    title = "Connect ADB",
                    subtitle = "Run adb connect against every discovered device",
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            val devices = discoveryEngine.devices.value.values.toList()
                            if (devices.isEmpty()) {
                                co.touchlab.kermit.Logger.i("Connect ADB: no devices discovered yet")
                            }
                            devices.forEach { device ->
                                // Self-gating: AdbManager probes port 5555 with a bounded TCP
                                // ping before invoking the adb binary, so offline devices cost
                                // ~400ms each instead of hanging the session.
                                com.dexstudios.dex.desktop.AdbManager.connect(device.ip)
                            }
                        }
                    },
                )
                SettingsItem(
                    icon = painterResource(Res.drawable.ic_fluent_bolt),
                    title = "Auto-Connect ADB Hotspot",
                    subtitle = "Run adb connect for discovered devices as soon as they appear",
                    badge = if (isAutoAdbHotspotActive) "ON" else "OFF",
                    onClick = { deviceConfig.autoAdbHotspotEnabled = !isAutoAdbHotspotActive },
                )
            }

            // Identity
            SettingsSectionHeader("Identity")
            SettingsCard {
                SettingsItem(
                    icon = painterResource(Res.drawable.ic_fluent_settings),
                    title = "Device Name",
                    subtitle = deviceAlias.ifBlank { "Not set — phones see this name while pairing" },
                    onClick = { showAliasEditor = true },
                )
                SettingsItem(
                    icon = painterResource(Res.drawable.ic_fluent_account_circle),
                    title = "Sign in with Google",
                    subtitle = if (googleProfile.email.isNotBlank()) "Signed in as ${googleProfile.email}" else "Trust all devices signed in with your email",
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                com.dexstudios.dex.core.network.LoopbackControlApi.triggerGoogleSignIn()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                )
            }

            // Appearance
            SettingsSectionHeader("Appearance")
            SettingsCard {
                SettingsItem(
                    icon = painterResource(Res.drawable.ic_fluent_palette),
                    title = "Theme",
                    subtitle = when (themeOverride) {
                        DeviceConfig.THEME_DARK -> "Dark"
                        DeviceConfig.THEME_LIGHT -> "Light"
                        else -> "System"
                    },
                    onClick = {
                        // Cycle System -> Dark -> Light; persisted and honored app-wide
                        // by the DeXTheme composition in main.kt.
                        deviceConfig.themeOverride = when (themeOverride) {
                            DeviceConfig.THEME_SYSTEM -> DeviceConfig.THEME_DARK
                            DeviceConfig.THEME_DARK -> DeviceConfig.THEME_LIGHT
                            else -> DeviceConfig.THEME_SYSTEM
                        }
                    },
                )
            }

            // Interaction
            SettingsSectionHeader("Interaction")
            SettingsCard {
                SettingsItem(
                    icon = painterResource(Res.drawable.ic_fluent_touch_app),
                    title = "Wiggle-to-Open Menu",
                    subtitle = if (isWiggleEnabled) "Enabled" else "Disabled",
                    onClick = { deviceConfig.wiggleEnabled = !isWiggleEnabled },
                )
            }
            // Shortcut reference (read-only): surfaces gestures that have no visible
            // affordance anywhere in the UI. The global-toggle row renders the combo
            // actually registered by GlobalShortcutService via DesktopEnvironment and
            // hides entirely on platforms without one (never advertise a fake shortcut).
            SettingsCard {
                if (com.dexstudios.dex.platform.DesktopEnvironment.globalToggleShortcutHint.isNotEmpty()) {
                    SettingsItem(
                        icon = painterResource(Res.drawable.ic_fluent_computer),
                        title = "Show / Hide DeX",
                        subtitle = "Global shortcut — works even while the menu is hidden",
                        badge = com.dexstudios.dex.platform.DesktopEnvironment.globalToggleShortcutHint,
                    )
                }
                SettingsItem(
                    icon = painterResource(Res.drawable.ic_fluent_bolt),
                    title = "Instant Exit",
                    subtitle = "Hold Shift and click Exit Engine to skip confirmation",
                    badge = "Shift+Click",
                )
                SettingsItem(
                    icon = painterResource(Res.drawable.ic_fluent_info),
                    title = "Force Exit During Transfers",
                    subtitle = "With a transfer running, clicking Exit Engine quits immediately",
                )
            }

            // Storage
            SettingsSectionHeader("Storage")
            SettingsCard {
                SettingsItem(
                    icon = painterResource(Res.drawable.ic_fluent_folder),
                    title = "Download Location",
                    subtitle = downloadDirPref.ifBlank { getDeXDownloadDirectory() },
                    onClick = {
                        coroutineScope.launch {
                            controller?.isModalDialogOpen = true
                            try {
                                val selectedDir = withContext(Dispatchers.IO) {
                                    val chooser = JFileChooser(downloadDirPref.ifBlank { getDeXDownloadDirectory() }).apply {
                                        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                        dialogTitle = "Select DeX Download Location"
                                        isAcceptAllFileFilterUsed = false
                                    }
                                    val result = chooser.showOpenDialog(null)
                                    if (result == JFileChooser.APPROVE_OPTION) {
                                        chooser.selectedFile?.absolutePath
                                    } else {
                                        null
                                    }
                                }
                                if (selectedDir != null) {
                                    // Persisted: transfers land here immediately and the choice survives restarts.
                                    deviceConfig.downloadDir = selectedDir
                                }
                            } finally {
                                controller?.isModalDialogOpen = false
                            }
                        }
                    },
                )
            }

            // About & Reset
            SettingsSectionHeader("About & Maintenance")
            SettingsCard {
                SettingsItem(
                    icon = painterResource(Res.drawable.ic_fluent_info),
                    title = "DeX Project",
                    subtitle = "Version ${com.dexstudios.dex.AppBuildConfig.VERSION_NAME} — View on GitHub",
                    onClick = {
                        try {
                            Desktop.getDesktop().browse(URI(com.dexstudios.dex.AppBuildConfig.REPO_URL))
                        } catch (_: Exception) {}
                    },
                )
                SettingsItem(
                    icon = painterResource(Res.drawable.ic_fluent_warning),
                    title = "Reset Identity & Trust",
                    subtitle = "Revokes all paired devices and restarts identity",
                    isDanger = true,
                    onClick = { showResetConfirm = true },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showAliasEditor) {
        AliasEditorDialog(
            current = deviceAlias,
            onDismiss = { showAliasEditor = false },
            onSave = { value ->
                deviceConfig.alias = value
                showAliasEditor = false
            },
        )
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset Identity & Trust?") },
            text = {
                Text(
                    "This signs you out, revokes every paired device and rotates your identity hash. " +
                        "Phones will have to pair again from scratch.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    // Revoke every persisted pairing, then rotate the identity hash so a
                    // previously known auto-trust credential dies with the reset.
                    coroutineScope.launch(Dispatchers.IO) {
                        val paired = com.dexstudios.dex.auth.AuthState.pairedFingerprints.value.toList()
                        paired.forEach { fp ->
                            runCatching {
                                com.dexstudios.dex.core.network.DeviceManager.removePairedFingerprint(fp)
                            }
                        }
                        deviceConfig.resetIdentity()
                    }
                }) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AliasEditorDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var draft by remember(current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Device Name") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(32) },
                singleLine = true,
                label = { Text("Shown to your phone while pairing") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft.trim()) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        content = content,
    )
}

@Composable
private fun SettingsItem(icon: Painter, title: String, subtitle: String, badge: String? = null, isBadgeDanger: Boolean = false, isDanger: Boolean = false, onClick: () -> Unit = {}) {
    SettingsItem(
        title = title,
        subtitle = subtitle,
        badge = badge,
        isBadgeDanger = isBadgeDanger,
        isDanger = isDanger,
        onClick = onClick,
        iconContent = { tint ->
            Icon(
                painter = icon,
                contentDescription = title,
                tint = tint,
                modifier = Modifier.padding(end = 12.dp).size(20.dp),
            )
        },
    )
}

@Composable
private fun SettingsItem(
    title: String,
    subtitle: String,
    badge: String? = null,
    isBadgeDanger: Boolean = false,
    isDanger: Boolean = false,
    onClick: () -> Unit = {},
    iconContent: @Composable (tint: Color) -> Unit,
) {
    val iconTint = if (isDanger) {
        MaterialTheme.colorScheme.error
    } else if (isBadgeDanger) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.padding(end = 12.dp).size(20.dp), contentAlignment = Alignment.Center) {
            iconContent(iconTint)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (badge != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isBadgeDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = badge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isBadgeDanger) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
