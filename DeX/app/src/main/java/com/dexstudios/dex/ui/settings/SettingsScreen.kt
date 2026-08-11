package com.dexstudios.dex.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dexstudios.dex.BuildConfig
import com.dexstudios.dex.R
import com.dexstudios.dex.network.DeviceConfig
import com.dexstudios.dex.ui.components.FloatingTopAppBar
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current

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
                    SettingsGroup(title = stringResource(R.string.settings_account_title)) {
                        if (googleProfile.email.isBlank()) {
                            SettingsClickableRow(
                                title = stringResource(R.string.google_sign_in),
                                subtitle = stringResource(R.string.trust_identity_desc),
                                icon = ImageVector.vectorResource(R.drawable.ic_account_circle),
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
                                icon = ImageVector.vectorResource(R.drawable.ic_tune_outlined), // Placeholder for Exit
                                onClick = { deviceConfig.signOut() }
                            )
                        }
                    }
                }

                // Connectivity Section
                item {
                    SettingsGroup(title = stringResource(R.string.settings_connectivity_title)) {
                        SettingsInfoRow(
                            title = stringResource(R.string.settings_ip_address),
                            value = deviceConfig.publicAddress.ifBlank { "Unknown" },
                            icon = MaterialSymbols.Wifi
                        )
                        SettingsInfoRow(
                            title = stringResource(R.string.settings_device_fingerprint),
                            value = deviceConfig.fingerprint,
                            icon = ImageVector.vectorResource(R.drawable.ic_smartphone)
                        )
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

        FloatingTopAppBar(
            modifier = Modifier.align(Alignment.TopCenter),
            backdrop = contentBackdrop,
            showSearch = false
        )
    }
}
