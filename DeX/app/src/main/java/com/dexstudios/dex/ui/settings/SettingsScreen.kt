package com.dexstudios.dex.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dexstudios.dex.BuildConfig
import com.dexstudios.dex.R
import com.dexstudios.dex.network.ClipboardSyncTileService
import com.dexstudios.dex.network.DeviceConfig
import com.dexstudios.dex.ui.icons.MaterialSymbols
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
    val context = LocalContext.current
    val resources = LocalResources.current

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
                    top = 24.dp,
                    bottom = 88.dp
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Account Section
                item {
                    SettingsGroup(title = stringResource(R.string.settings_account_title)) {
                        if (googleProfile.email.isBlank()) {
                            SettingsClickableRow(
                                title = stringResource(R.string.google_sign_in),
                                subtitle = stringResource(R.string.trust_identity_desc),
                                icon = MaterialSymbols.AccountCircle,
                                onClick = {
                                    val activity = context as? android.app.Activity
                                    if (activity != null) {
                                        scope.launch {
                                            val credential = com.dexstudios.dex.network.GoogleSignInManager.signIn(activity)
                                            val email = credential?.let { com.dexstudios.dex.network.GoogleSignInManager.applyToDeviceConfig(it, deviceConfig) }
                                            if (email != null) {
                                                Toast.makeText(context, resources.getString(R.string.google_signed_in_as, email), Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, resources.getString(R.string.google_sign_in_failed), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
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
                                title = stringResource(R.string.settings_sign_out),
                                icon = MaterialSymbols.Tune, // Placeholder for Exit
                                onClick = { deviceConfig.signOut() }
                            )
                        }
                    }
                }

                // Connectivity Section
                item {
                    SettingsGroup(title = stringResource(R.string.settings_connectivity_title)) {
                        SettingsClickableRow(
                            title = stringResource(R.string.settings_device_alias),
                            subtitle = alias.ifBlank { com.dexstudios.dex.network.getDeviceName(context) },
                            icon = MaterialSymbols.Smartphone,
                            onClick = {
                                tempAlias = alias.ifBlank { com.dexstudios.dex.network.getDeviceName(context) }
                                showAliasDialog = true
                            }
                        )
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_clipboard_sync),
                            subtitle = stringResource(R.string.settings_clipboard_sync_desc),
                            icon = MaterialSymbols.CheckCircle, // Placeholder for clipboard
                            checked = clipboardSyncEnabled,
                            onCheckedChange = { deviceConfig.clipboardSyncEnabled = it }
                        )
                        SettingsInfoRow(
                            title = stringResource(R.string.settings_ip_address),
                            value = deviceConfig.publicAddress.ifBlank { "Unknown" },
                            icon = MaterialSymbols.Wifi
                        )
                        val fingerprint by deviceConfig.fingerprintFlow.collectAsStateWithLifecycle()
                        SettingsInfoRow(
                            title = stringResource(R.string.settings_device_fingerprint),
                            value = fingerprint,
                            icon = MaterialSymbols.Smartphone
                        )
                    }
                }

                // Reliability Section
                item {
                    SettingsGroup(title = "Reliability") {
                        val isIgnoring by produceState(initialValue = false, context) {
                            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                pm.isIgnoringBatteryOptimizations(context.packageName)
                            }
                        }
                        SettingsClickableRow(
                            title = "Background Optimization",
                            subtitle = if (isIgnoring) "Unrestricted" else "Optimized",
                            icon = MaterialSymbols.BatteryCharging,
                            onClick = {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        )

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            SettingsClickableRow(
                                title = "Add Quick Settings Tile",
                                subtitle = "Quickly toggle Clipboard Sync",
                                icon = MaterialSymbols.Clipboard,
                                onClick = {
                                    val sbm = context.getSystemService("statusbar")
                                    try {
                                        val method = sbm?.javaClass?.getMethod(
                                            "requestAddTileService",
                                            android.content.ComponentName::class.java,
                                            CharSequence::class.java,
                                            android.graphics.drawable.Icon::class.java,
                                            java.util.concurrent.Executor::class.java,
                                            java.util.function.Consumer::class.java
                                        )
                                        val componentName = android.content.ComponentName(
                                            context,
                                            ClipboardSyncTileService::class.java
                                        )
                                        method?.invoke(
                                            sbm,
                                            componentName,
                                            "Clipboard Sync",
                                            android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_stat_dex),
                                            context.mainExecutor,
                                            java.util.function.Consumer<Int> { }
                                        )
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            )
                        }
                    }
                }

                // About Section
                item {
                    SettingsGroup(title = stringResource(R.string.settings_about_title)) {
                        SettingsInfoRow(
                            title = stringResource(R.string.settings_app_version),
                            value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            icon = MaterialSymbols.CheckCircle
                        )
                    }
                }
            }
        }
    }

    if (showAliasDialog) {
        AlertDialog(
            onDismissRequest = { showAliasDialog = false },
            title = { Text(stringResource(R.string.settings_device_alias)) },
            text = {
                OutlinedTextField(
                    value = tempAlias,
                    onValueChange = { if (it.length <= 32) tempAlias = it },
                    label = { Text(stringResource(R.string.settings_device_alias_hint)) },
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
                    Text(stringResource(R.string.cancel))
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
