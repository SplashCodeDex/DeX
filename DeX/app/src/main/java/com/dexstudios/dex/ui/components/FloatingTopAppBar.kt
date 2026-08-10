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
import kotlinx.coroutines.launch
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

    // Signed-in Google profile: single combined flow — one recomposition instead of three
    val deviceConfig: DeviceConfig = koinInject()
    val profile by deviceConfig.googleProfileFlow.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
                if (profile.picture.isNotBlank()) {
                    showProfileDialog = true
                } else {
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        scope.launch {
                            val credential = GoogleSignInManager.signIn(activity)
                            val email = credential?.let { GoogleSignInManager.applyToDeviceConfig(it, deviceConfig) }
                            if (email != null) {
                                Toast.makeText(context, context.getString(R.string.google_signed_in_as, email), Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, context.getString(R.string.google_sign_in_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            },
            size = 56.dp,
            backdrop = backdrop,
            config = LiquidGlassPresets.IconButton
        ) {
            if (profile.picture.isNotBlank()) {
                AsyncImage(
                    model = profile.picture,
                    contentDescription = "Profile",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
            } else if (profile.email.isNotBlank()) {
                // Signed in but no avatar: show the account's initial
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (profile.name.ifBlank { profile.email }).first().uppercase(),
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
            title = { Text(text = profile.name.ifBlank { profile.email }) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (profile.picture.isNotBlank()) {
                        AsyncImage(
                            model = profile.picture,
                            contentDescription = "Profile",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(profile.email, style = MaterialTheme.typography.bodyMedium)
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
