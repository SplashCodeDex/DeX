package com.dexstudios.dex.transfer
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_file_download
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_file_upload

import com.dexstudios.dex.core.designsystem.generated.resources.Res

import org.jetbrains.compose.resources.painterResource

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.dexstudios.dex.core.designsystem.theme.LocalBackdrop
import com.dexstudios.dex.core.designsystem.components.glass.LiquidGlassPanel
import com.dexstudios.dex.core.designsystem.components.glass.LiquidGlassPresets

@Composable
fun TransferDialog(
    filename: String,
    progress: Float,
    isReceiving: Boolean,
    onDismiss: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        val backdrop = LocalBackdrop.current
        
        val content = @Composable {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = if (isReceiving) painterResource(Res.drawable.ic_fluent_file_download) else painterResource(Res.drawable.ic_fluent_file_upload),
                    contentDescription = "Transfer Icon",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isReceiving) "Receiving File" else "Sending File",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = filename,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )
                Spacer(modifier = Modifier.height(24.dp))
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }

        if (backdrop != null) {
            LiquidGlassPanel(
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                config = LiquidGlassPresets.Dialog
            ) {
                content()
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface
            ) {
                content()
            }
        }
    }
}
