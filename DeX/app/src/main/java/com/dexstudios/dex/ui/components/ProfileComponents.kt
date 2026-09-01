package com.dexstudios.dex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dexstudios.dex.network.GoogleProfile
import com.dexstudios.dex.ui.icons.MaterialSymbols

@Composable
fun CollapsedProfileContent(
    profile: GoogleProfile,
    modifier: Modifier = Modifier
) {
    if (profile.picture.isNotBlank()) {
        AsyncImage(
            model = profile.picture,
            contentDescription = "Profile",
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape)
        )
    } else if (profile.email.isNotBlank()) {
        Box(
            modifier = modifier
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
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = MaterialSymbols.AccountCircle,
                contentDescription = "Profile",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
