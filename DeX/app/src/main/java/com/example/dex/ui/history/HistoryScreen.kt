package com.example.dex.ui.history

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.dex.R
import com.example.dex.network.TransferHistory
import com.example.dex.network.TransferRecord
import com.example.dex.ui.components.FloatingTopAppBar
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val items by TransferHistory.items.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        TransferHistory.refresh(context)
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FloatingTopAppBar()

        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        )

        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items, key = { it.id }) { record ->
                    HistoryRow(record = record, onClick = { openRecord(context, record) })
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(record: TransferRecord, onClick: () -> Unit) {
    val isSent = record.direction == "sent"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isSent) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(
                    if (isSent) R.drawable.ic_send else R.drawable.ic_folder
                ),
                contentDescription = null,
                tint = if (isSent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${formatSize(record.size)} · ${formatDate(record.timestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = stringResource(
                if (isSent) R.string.history_sent else R.string.history_received
            ),
            style = MaterialTheme.typography.labelSmall,
            color = if (isSent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

private fun formatSize(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> String.format(Locale.US, "%.1f GB", bytes / gb)
        bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
        bytes >= kb -> String.format(Locale.US, "%.1f KB", bytes / kb)
        else -> "$bytes B"
    }
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
}

private fun openRecord(context: Context, record: TransferRecord) {
    val uri = record.uri
    if (uri == null) {
        Toast.makeText(context, context.getString(R.string.history_no_source), Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val type = runCatching {
            context.contentResolver.getType(Uri.parse(uri))
        }.getOrNull() ?: "application/octet-stream"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(uri), type)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, context.getString(R.string.history_open_failed), Toast.LENGTH_SHORT).show()
    }
}
