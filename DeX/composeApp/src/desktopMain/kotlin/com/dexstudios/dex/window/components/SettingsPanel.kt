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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.profile_avatar
import com.dexstudios.dex.core.designsystem.icons.MaterialSymbols
import com.dexstudios.dex.core.designsystem.theme.DeXTheme
import com.dexstudios.dex.core.network.DeviceConfig
import com.dexstudios.dex.window.DockedWindowStateController
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
 * - Profile & Account header (56x56dp avatar, name, email, "Premium User" badge, Sign Out)
 * - Categorized preferences:
 *   1. Connection (DND switch)
 *   2. Dev Tools (ADB Connect & Auto-Connect Hotspot)
 *   3. Identity (Google OAuth loopback)
 *   4. Appearance (Dark/Light Theme)
 *   5. Interaction (Wiggle-to-Open Menu)
 *   6. Storage (Download Location folder chooser setting controller.isModalDialogOpen = true during pick)
 *   7. About DeX (Version 1.0.0 & GitHub link) & Reset Identity
 */
@Composable
fun SettingsPanel(
    onClose: () -> Unit = {},
    controller: DockedWindowStateController? = null,
    modifier: Modifier = Modifier,
    deviceConfig: DeviceConfig = koinInject()
) {
    val coroutineScope = rememberCoroutineScope()
    val googleProfile by deviceConfig.googleProfileFlow.collectAsState()

    var isDndActive by remember { mutableStateOf(false) }
    var isAutoAdbHotspotActive by remember { mutableStateOf(true) }
    var isDarkTheme by remember { mutableStateOf(true) }
    var isWiggleEnabled by remember { mutableStateOf(true) }
    var downloadPath by remember {
        val userHome = System.getProperty("user.home") ?: ""
        mutableStateOf(File(userHome, "Downloads" + File.separator + "DeX").absolutePath)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 24.dp, top = 20.dp, end = 16.dp, bottom = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(DeXTheme.colors.accent)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = MaterialSymbols.Settings,
                        contentDescription = "Settings",
                        tint = DeXTheme.colors.primaryText,
                        modifier = Modifier.size(18.dp).padding(end = 6.dp)
                    )
                    Text(
                        text = "Settings",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DeXTheme.colors.primaryText
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(DeXTheme.colors.accent)
                    .clickable {
                        try {
                            Desktop.getDesktop().browse(URI("https://github.com/SplashCodeDex/DeX"))
                        } catch (_: Exception) {}
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = MaterialSymbols.Info,
                        contentDescription = "About",
                        tint = DeXTheme.colors.secondaryText,
                        modifier = Modifier.size(16.dp).padding(end = 6.dp)
                    )
                    Text(
                        text = "DeX v1.0.0",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = DeXTheme.colors.secondaryText
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
                .padding(end = 8.dp)
        ) {
            // Profile Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DeXTheme.colors.accent)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(Res.drawable.profile_avatar),
                            contentDescription = "Avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = googleProfile.name.ifBlank { "DeXStudios" },
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DeXTheme.colors.primaryText
                            )
                            Text(
                                text = googleProfile.email.ifBlank { "dexify@dex.net" },
                                fontSize = 13.sp,
                                color = DeXTheme.colors.secondaryText
                            )
                            Text(
                                text = "Premium User",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DeXTheme.colors.secondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    if (googleProfile.email.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF332D3B))
                                .clickable { deviceConfig.signOut() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Sign Out",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = DeXTheme.colors.danger
                            )
                        }
                    }
                }
            }

            // Connection Settings
            SettingsSectionHeader("Connection")
            SettingsCard {
                SettingsItem(
                    icon = MaterialSymbols.DoNotDisturb,
                    title = "Do Not Disturb",
                    subtitle = "Auto-reject all pairing/transfer requests",
                    badge = if (isDndActive) "ON" else "OFF",
                    isBadgeDanger = isDndActive,
                    onClick = { isDndActive = !isDndActive }
                )
            }

            // Developer Tools
            SettingsSectionHeader("Developer Tools")
            SettingsCard {
                SettingsItem(
                    icon = MaterialSymbols.Tune,
                    title = "Connect ADB",
                    subtitle = "Enable ADB terminal debugging for power users",
                    onClick = { /* Launch ADB connection */ }
                )
                SettingsItem(
                    icon = MaterialSymbols.Bolt,
                    title = "Auto-Connect ADB Hotspot",
                    subtitle = "Auto-connect ADB daemon when joining phone hotspot",
                    badge = if (isAutoAdbHotspotActive) "ON" else "OFF",
                    onClick = { isAutoAdbHotspotActive = !isAutoAdbHotspotActive }
                )
            }

            // Identity
            SettingsSectionHeader("Identity")
            SettingsCard {
                SettingsItem(
                    icon = MaterialSymbols.AccountCircle,
                    title = "Sign in with Google",
                    subtitle = if (googleProfile.email.isNotBlank()) "Signed in as ${googleProfile.email}" else "Trust all devices signed in with your email",
                    onClick = {
                        // Google OAuth Loopback
                    }
                )
            }

            // Appearance
            SettingsSectionHeader("Appearance")
            SettingsCard {
                SettingsItem(
                    icon = MaterialSymbols.Palette,
                    title = "Theme",
                    subtitle = if (isDarkTheme) "Dark" else "Light",
                    onClick = { isDarkTheme = !isDarkTheme }
                )
            }

            // Interaction
            SettingsSectionHeader("Interaction")
            SettingsCard {
                SettingsItem(
                    icon = MaterialSymbols.TouchApp,
                    title = "Wiggle-to-Open Menu",
                    subtitle = if (isWiggleEnabled) "Enabled" else "Disabled",
                    onClick = { isWiggleEnabled = !isWiggleEnabled }
                )
            }

            // Storage
            SettingsSectionHeader("Storage")
            SettingsCard {
                SettingsItem(
                    icon = MaterialSymbols.Folder,
                    title = "Download Location",
                    subtitle = downloadPath,
                    onClick = {
                        coroutineScope.launch {
                            controller?.isModalDialogOpen = true
                            try {
                                val selectedDir = withContext(Dispatchers.IO) {
                                    val chooser = JFileChooser(downloadPath).apply {
                                        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                        dialogTitle = "Select DeX Download Location"
                                        isAcceptAllFileFilterUsed = false
                                    }
                                    val result = chooser.showOpenDialog(null)
                                    if (result == JFileChooser.APPROVE_OPTION) {
                                        chooser.selectedFile?.absolutePath
                                    } else null
                                }
                                if (selectedDir != null) {
                                    downloadPath = selectedDir
                                }
                            } finally {
                                controller?.isModalDialogOpen = false
                            }
                        }
                    }
                )
            }

            // About & Reset
            SettingsSectionHeader("About & Maintenance")
            SettingsCard {
                SettingsItem(
                    icon = MaterialSymbols.Info,
                    title = "DeX Project",
                    subtitle = "Version 1.0.0 — View on GitHub",
                    onClick = {
                        try {
                            Desktop.getDesktop().browse(URI("https://github.com/SplashCodeDex/DeX"))
                        } catch (_: Exception) {}
                    }
                )
                SettingsItem(
                    icon = MaterialSymbols.Warning,
                    title = "Reset Identity & Trust",
                    subtitle = "Revokes all paired devices and restarts identity",
                    isDanger = true,
                    onClick = {
                        deviceConfig.signOut()
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = DeXTheme.colors.secondaryText,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DeXTheme.colors.accent)
            .padding(4.dp),
        content = content
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
    isBadgeDanger: Boolean = false,
    isDanger: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (isDanger) DeXTheme.colors.danger else if (isBadgeDanger) DeXTheme.colors.danger else DeXTheme.colors.secondaryText,
            modifier = Modifier.size(20.dp).padding(end = 12.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDanger) DeXTheme.colors.danger else DeXTheme.colors.primaryText
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = DeXTheme.colors.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (badge != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isBadgeDanger) DeXTheme.colors.danger else DeXTheme.colors.secondary)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = badge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isBadgeDanger) Color.White else Color.Black
                )
            }
        }
    }
}
