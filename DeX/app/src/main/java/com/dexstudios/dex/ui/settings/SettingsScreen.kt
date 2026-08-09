package com.dexstudios.dex.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.R
import com.dexstudios.dex.network.DeviceConfig
import com.dexstudios.dex.network.DiscoveryEngine
import com.dexstudios.dex.network.WebSocketClientService
import com.dexstudios.dex.ui.components.*
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    deviceConfig: DeviceConfig = koinInject(),
    discoveryEngine: DiscoveryEngine = koinInject(),
    webSocketClientService: WebSocketClientService = koinInject()
) {
    val emailText by deviceConfig.emailFlow.collectAsState()
    val hashPreview by deviceConfig.identityHashFlow.collectAsState()
    val publicAddress by deviceConfig.publicAddressFlow.collectAsState()

    // Screen-owned backdrop: captures this screen's content so the glass header
    // samples it. Separate from the navbar's backdrop (which captures this whole
    // screen) — the header must never sample a backdrop that captures it.
    val contentBackdrop = rememberLayerBackdrop()
    // System navigation bar inset — lines the last setting up exactly with the
    // floating navbar's top edge (72dp + 16dp margin) on any device.
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier = modifier.fillMaxSize()) {
        // ===== Backdrop source: this screen's content =====
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(contentBackdrop)
        ) {
            // Background so the backdrop layer is never empty
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    // Top content rests exactly at the status bar line; the last
                    // setting rests exactly at the navbar's top line — no gaps.
                    .padding(top = 0.dp, bottom = 88.dp - navBarInset),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                    Text(
                        stringResource(R.string.trust_identity_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        stringResource(R.string.trust_identity_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
        
                    OutlinedTextField(
                        value = emailText,
                        onValueChange = { 
                            deviceConfig.email = it
                            discoveryEngine.stopDiscovery()
                            discoveryEngine.startDiscovery()
                        },
                        label = { Text(stringResource(R.string.email_address)) },
                        placeholder = { Text(stringResource(R.string.email_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                            focusedContainerColor = Color.White.copy(alpha = 0.2f)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Google Sign-In: verified email identity, propagated to the PC over the WebSocket
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val googleLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
                    ) { result ->
                        val account = com.dexstudios.dex.network.GoogleSignInManager.handleResult(result.data)
                        val email = account?.let { com.dexstudios.dex.network.GoogleSignInManager.applyToDeviceConfig(it, deviceConfig) }
                        if (email != null) {
                            com.dexstudios.dex.network.GoogleSignInManager.pushIdentityToPc(webSocketClientService, deviceConfig)
                            Toast.makeText(context, context.getString(R.string.google_signed_in_as, email), Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, context.getString(R.string.google_sign_in_failed), Toast.LENGTH_SHORT).show()
                        }
                    }

                    if (com.dexstudios.dex.network.GoogleSignInManager.isConfigured()) {
                        DeXButton(
                            onClick = { com.dexstudios.dex.network.GoogleSignInManager.signInIntent(context)?.let { googleLauncher.launch(it) } },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (emailText.isBlank()) stringResource(R.string.google_sign_in)
                                else stringResource(R.string.google_signed_in_as, emailText),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                            stringResource(R.string.google_sign_in_not_configured),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
        
                    Text(
                        stringResource(R.string.current_identity_hash),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Surface(
                        color = Color.Black.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = hashPreview,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        stringResource(R.string.settings_public_address),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = publicAddress,
                        onValueChange = {
                            deviceConfig.setPublicAddress(it)
                            webSocketClientService.sendPublicAddress(it)
                        },
                        label = { Text(stringResource(R.string.settings_public_address_label)) },
                        placeholder = { Text(stringResource(R.string.settings_public_address_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                            focusedContainerColor = Color.White.copy(alpha = 0.2f)
                        )
                    )

                    Text(
                        stringResource(R.string.settings_public_address_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
            }
        }

        // ===== Glass header overlay — drawn AFTER the captured content =====
        // Settings shows only the avatar (navbar handles navigation; search is
        // for Devices/History only).
        FloatingTopAppBar(
            modifier = Modifier.align(Alignment.TopCenter),
            backdrop = contentBackdrop,
            showSearch = false
        )
    }
}
