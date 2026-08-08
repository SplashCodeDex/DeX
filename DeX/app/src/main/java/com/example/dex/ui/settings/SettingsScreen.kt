package com.example.dex.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dex.R
import com.example.dex.network.DeviceConfig
import com.example.dex.network.DiscoveryEngine
import com.example.dex.network.PcMemory
import com.example.dex.network.QuicClient
import com.example.dex.network.WebSocketClientService
import com.example.dex.ui.components.*
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    deviceConfig: DeviceConfig = koinInject(),
    discoveryEngine: DiscoveryEngine = koinInject(),
    quicClient: QuicClient = koinInject(),
    webSocketClientService: WebSocketClientService = koinInject()
) {
    val emailText by deviceConfig.emailFlow.collectAsState()
    val hashPreview by deviceConfig.identityHashFlow.collectAsState()
    val publicAddress by deviceConfig.publicAddressFlow.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    DeXIconButton(onClick = onBack) {
                        Icon(ImageVector.vectorResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
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

                    var showSharedFoldersDialog by remember { mutableStateOf(false) }

                    DeXTextButton(
                        onClick = { showSharedFoldersDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_folder),
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Manage Shared Folders")
                    }

                    if (showSharedFoldersDialog) {
                        SharedFoldersDialog(
                            onDismiss = { showSharedFoldersDialog = false }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        stringResource(R.string.settings_quic_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        stringResource(R.string.settings_quic_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    var certInstalled by remember { mutableStateOf(quicClient.certInstalled) }
                    var installing by remember { mutableStateOf(false) }

                    DeXTextButton(
                        onClick = {
                            val ip = PcMemory.ip(context)
                            if (ip.isNullOrBlank()) {
                                Toast.makeText(context, context.getString(R.string.settings_cert_no_pc), Toast.LENGTH_SHORT).show()
                                return@DeXTextButton
                            }
                            installing = true
                            scope.launch {
                                val der = quicClient.fetchCert(ip)
                                installing = false
                                if (der == null) {
                                    Toast.makeText(context, context.getString(R.string.settings_cert_fetch_failed), Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                try {
                                    val certFile = java.io.File(context.cacheDir, "dex_pc_cert.der")
                                    certFile.writeBytes(der)
                                    val intent = android.content.Intent("android.credentials.INSTALL").apply {
                                        putExtra("name", "DeX PC Certificate")
                                        putExtra("certificate", der)
                                    }
                                    context.startActivity(intent)
                                    quicClient.certInstalled = true
                                    certInstalled = true
                                } catch (e: Exception) {
                                    Toast.makeText(context, context.getString(R.string.settings_cert_fetch_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !installing
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_send),
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            if (certInstalled) stringResource(R.string.settings_cert_installed)
                            else stringResource(R.string.settings_install_cert)
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
}
