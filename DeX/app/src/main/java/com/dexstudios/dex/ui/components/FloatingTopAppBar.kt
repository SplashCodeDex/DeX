package com.dexstudios.dex.ui.components

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dexstudios.dex.R
import com.dexstudios.dex.network.DeviceConfig
import com.dexstudios.dex.network.GoogleSignInManager
import com.dexstudios.dex.network.WebSocketClientService
import com.dexstudios.dex.ui.components.glass.LiquidGlassIconButton
import com.dexstudios.dex.ui.components.glass.LiquidGlassPresets
import com.kyant.backdrop.Backdrop
import org.koin.compose.koinInject

@Composable
fun FloatingTopAppBar(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    showSearch: Boolean = true,
) {
    var showProfileDialog by remember { mutableStateOf(false) }

    // Signed-in Google profile: the avatar button becomes the live account picture
    val deviceConfig: DeviceConfig = koinInject()
    val webSocketClientService: WebSocketClientService = koinInject()
    val profileName by deviceConfig.profileNameFlow.collectAsState()
    val profilePicture by deviceConfig.profilePictureFlow.collectAsState()
    val emailText by deviceConfig.emailFlow.collectAsState()
    val context = LocalContext.current

    val googleLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val account = GoogleSignInManager.handleResult(result.data)
        val email = account?.let { GoogleSignInManager.applyToDeviceConfig(it, deviceConfig) }
        if (email != null) {
            GoogleSignInManager.pushIdentityToPc(webSocketClientService, deviceConfig)
            Toast.makeText(context, context.getString(R.string.google_signed_in_as, email), Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, context.getString(R.string.google_sign_in_failed), Toast.LENGTH_SHORT).show()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // User Avatar — Google picture when signed in; tap to sign in otherwise
        LiquidGlassIconButton(
            onClick = {
                if (profilePicture.isNotBlank()) {
                    showProfileDialog = true
                } else {
                    GoogleSignInManager.signInIntent(context)?.let { googleLauncher.launch(it) }
                }
            },
            size = 56.dp,
            backdrop = backdrop,
            config = LiquidGlassPresets.IconButton
        ) {
            if (profilePicture.isNotBlank()) {
                AsyncImage(
                    model = profilePicture,
                    contentDescription = "Profile",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
            } else if (emailText.isNotBlank()) {
                // Signed in but no avatar: show the account's initial
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (profileName.ifBlank { emailText }).first().uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            } else {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_account_circle),
                    contentDescription = "Profile",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Brand Logo
        Box(
            modifier = Modifier.height(56.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.dex_logo),
                contentDescription = "DeX Logo",
                modifier = Modifier.fillMaxHeight(),
                contentScale = ContentScale.Fit
            )
        }

        // Action Buttons Group
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (showSearch) {
                // Search Button — liquid glass samples the real backdrop behind the top bar
                LiquidGlassIconButton(
                    onClick = { /* Search action placeholder */ },
                    size = 56.dp,
                    backdrop = backdrop,
                    config = LiquidGlassPresets.IconButton
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_search),
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                }
            } else {
                // Balancing placeholder so the centered logo stays centered
                Spacer(modifier = Modifier.size(56.dp))
            }
        }
    }

    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text(text = profileName.ifBlank { emailText }) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (profilePicture.isNotBlank()) {
                        AsyncImage(
                            model = profilePicture,
                            contentDescription = "Profile",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(emailText, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Signed in with Google — your devices trust each other automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                DeXTextButton(onClick = { showProfileDialog = false }) {
                    Text("OK")
                }
            },
            dismissButton = {
                DeXTextButton(onClick = {
                    deviceConfig.signOut()
                    showProfileDialog = false
                    Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Sign out")
                }
            }
        )
    }
}
