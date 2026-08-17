package com.dexstudios.dex.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dexstudios.dex.core.designsystem.generated.resources.*
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.network.DeviceConfig
import com.dexstudios.dex.core.designsystem.icons.MaterialSymbols
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    deviceConfig: DeviceConfig = koinInject()
) {
    val googleProfile by deviceConfig.googleProfileFlow.collectAsState()
    val alias by deviceConfig.aliasFlow.collectAsState()
    val clipboardSyncEnabled by deviceConfig.clipboardSyncEnabledFlow.collectAsState()

    val scope = rememberCoroutineScope()
    val platformHelper = rememberSettingsPlatformHelper()


    var showAliasDialog by remember { mutableStateOf(false) }
    var tempAlias by remember { mutableStateOf("") }

    val contentBackdrop = rememberLayerBackdrop()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(contentBackdrop)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 104.dp, // Below FloatingTopAppBar
                    bottom = 88.dp - navBarInset
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Account Section
                item {
                    SettingsGroup(title = stringResource(Res.string.settings_account_title)) {
                        if (googleProfile.email.isBlank()) {
                            SettingsClickableRow(
                                title = stringResource(Res.string.google_sign_in),
                                subtitle = stringResource(Res.string.trust_identity_desc),
                                icon = MaterialSymbols.AccountCircle,
                                onClick = {
                                    platformHelper.signInWithGoogle(deviceConfig)
                                }
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                AsyncImage(
                                    model = googleProfile.picture,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = googleProfile.name,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = googleProfile.email,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            SettingsClickableRow(
                                title = stringResource(Res.string.settings_sign_out),
                                icon = MaterialSymbols.Tune, // Placeholder for Exit
                                onClick = { deviceConfig.signOut() }
                            )
                        }
                    }
                }

                // Connectivity Section
                item {
                    SettingsGroup(title = stringResource(Res.string.settings_connectivity_title)) {
                        SettingsClickableRow(
                            title = stringResource(Res.string.settings_device_alias),
                            subtitle = alias.ifBlank { platformHelper.getDeviceName() },
                            icon = MaterialSymbols.Smartphone,
                            onClick = {
                                tempAlias = alias.ifBlank { platformHelper.getDeviceName() }
                                showAliasDialog = true
                            }
                        )
                        SettingsSwitchRow(
                            title = stringResource(Res.string.settings_clipboard_sync),
                            subtitle = stringResource(Res.string.settings_clipboard_sync_desc),
                            icon = MaterialSymbols.CheckCircle, // Placeholder for clipboard
                            checked = clipboardSyncEnabled,
                            onCheckedChange = { deviceConfig.clipboardSyncEnabled = it }
                        )
                        SettingsInfoRow(
                            title = stringResource(Res.string.settings_ip_address),
                            value = deviceConfig.publicAddress.ifBlank { "Unknown" },
                            icon = MaterialSymbols.Wifi
                        )
                        SettingsInfoRow(
                            title = stringResource(Res.string.settings_device_fingerprint),
                            value = deviceConfig.fingerprint,
                            icon = MaterialSymbols.Smartphone
                        )
                    }
                }

                // Reliability Section
                item {
                    SettingsGroup(title = "Reliability") {
                        val isIgnoring = remember {
                            platformHelper.isIgnoringBatteryOptimizations
                        }
                        SettingsClickableRow(
                            title = "Background Optimization",
                            subtitle = if (isIgnoring) "Unrestricted" else "Optimized",
                            icon = MaterialSymbols.BatteryCharging,
                            onClick = {
                                platformHelper.requestIgnoreBatteryOptimizations()
                            }
                        )

                        if (platformHelper.canAddQuickSettingsTile) {
                            SettingsClickableRow(
                                title = "Add Quick Settings Tile",
                                subtitle = "Quickly toggle Clipboard Sync",
                                icon = MaterialSymbols.Clipboard,
                                onClick = {
                                    platformHelper.addQuickSettingsTile()
                                }
                            )
                        }
                    }
                }

                // About Section
                item {
                    SettingsGroup(title = stringResource(Res.string.settings_about_title)) {
                        SettingsInfoRow(
                            title = stringResource(Res.string.settings_app_version),
                            value = platformHelper.appVersion,
                            icon = MaterialSymbols.CheckCircle
                        )
                    }
                }
            }
        }

        // FloatingTopAppBar(
            // modifier = Modifier.align(Alignment.TopCenter),
            // backdrop = contentBackdrop,
            // showSearch = false
        // )
    }

    if (showAliasDialog) {
        AlertDialog(
            onDismissRequest = { showAliasDialog = false },
            title = { Text(stringResource(Res.string.settings_device_alias)) },
            text = {
                OutlinedTextField(
                    value = tempAlias,
                    onValueChange = { if (it.length <= 32) tempAlias = it },
                    label = { Text(stringResource(Res.string.settings_device_alias_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deviceConfig.alias = tempAlias
                    showAliasDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAliasDialog = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}






