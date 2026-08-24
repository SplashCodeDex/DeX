package com.dexstudios.dex.window.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.core.designsystem.theme.DeXTheme
import kotlinx.coroutines.delay

@Composable
fun DownloadDockToast(message: String, isVisible: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(4000)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(300)) + slideInVertically(animationSpec = tween(300), initialOffsetY = { 25 }) + scaleIn(initialScale = 0.8f, animationSpec = tween(300)),
        exit = fadeOut(tween(300)) + slideOutVertically(animationSpec = tween(300), targetOffsetY = { 25 }) + scaleOut(targetScale = 0.8f, animationSpec = tween(300)),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier.padding(16.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
