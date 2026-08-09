package com.example.dex.ui.components

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.dex.R
import com.example.dex.ui.components.glass.LiquidGlassIconButton
import com.example.dex.ui.components.glass.LiquidGlassPresets
import com.kyant.backdrop.Backdrop

@Composable
fun FloatingTopAppBar(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = null,
    showSearch: Boolean = true,
) {
    var showProfileDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // User Avatar — liquid glass samples the real backdrop behind the top bar
        LiquidGlassIconButton(
            onClick = { showProfileDialog = true },
            size = 56.dp,
            backdrop = backdrop,
            config = LiquidGlassPresets.IconButton
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_account_circle),
                contentDescription = "Profile",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
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
            title = { Text(text = "Profile") },
            text = { Text(text = "This is a placeholder for the user profile functionality. Account integration coming soon!") },
            confirmButton = {
                DeXTextButton(onClick = { showProfileDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
}
