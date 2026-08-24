package com.dexstudios.dex.window.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.core.designsystem.components.bubbleFluidity
import com.dexstudios.dex.core.designsystem.generated.resources.Res
import com.dexstudios.dex.core.designsystem.generated.resources.ic_fluent_close
import com.dexstudios.dex.core.network.ClientEngine
import com.dexstudios.dex.core.network.services.FileExplorerService
import org.jetbrains.compose.resources.painterResource

/**
 * Floating transfer toast with a 4dp Emerald Progress Bar.
 * Renders the active pull from the phone when one is running, otherwise the upload state.
 */
@Composable
fun PullProgressDock(clientEngine: ClientEngine, onCancel: () -> Unit, modifier: Modifier = Modifier, fileExplorerService: FileExplorerService? = null) {
    val uploadState by clientEngine.uploadState.collectAsState()
    val pullState = fileExplorerService?.pullProgress?.collectAsState()?.value

    val showPull = pullState?.isPulling == true
    val title = if (showPull) {
        pullState!!.activeFileName.ifBlank {
            if (pullState.totalFiles > 1) "Receiving ${pullState.totalFiles} files from phone" else "Receiving from phone"
        }
    } else {
        uploadState.fileName.ifBlank { "Transferring files..." }
    }
    val progress = if (showPull) {
        pullState!!.progress.coerceIn(0f, 1f)
    } else {
        uploadState.progress.coerceIn(0f, 1f)
    }
    val detail = if (showPull) {
        "${(pullState!!.progress * 100).toInt()}% • ${pullState.completedFiles}/${pullState.totalFiles} files • ${formatFileSize(pullState.bytesTransferred)}"
    } else {
        "${(uploadState.progress * 100).toInt()}% • ${formatSpeed(uploadState.speedBps)}"
    }

    Box(
        modifier = modifier
            .fillMaxWidth(0.92f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141118))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = detail,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Icon(
                    painter = painterResource(Res.drawable.ic_fluent_close),
                    contentDescription = "Cancel Transfer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .bubbleFluidity()
                        .clip(CircleShape)
                        .clickable { onCancel() }
                        .padding(2.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4dp Emerald Progress Indicator
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}
