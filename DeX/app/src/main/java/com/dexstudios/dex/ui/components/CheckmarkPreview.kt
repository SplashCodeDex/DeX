package com.dexstudios.dex.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.ui.icons.MaterialSymbols as DeXIcons

@Preview(showBackground = true)
@Composable
fun CheckmarkIconsPreview() {
    MaterialTheme {
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = DeXIcons.Check,
                    contentDescription = "Check",
                    modifier = Modifier.size(48.dp),
                    tint = Color(0xFF4CAF50)
                )
                Text("Check", style = MaterialTheme.typography.labelMedium)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = DeXIcons.CheckCircle,
                    contentDescription = "Check Circle",
                    modifier = Modifier.size(48.dp),
                    tint = Color(0xFF4CAF50)
                )
                Text("CheckCircle", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun OnboardingStepIconGrantedPreview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(48.dp), contentAlignment = Alignment.Center) {
            // Replicating OnboardingStepIcon logic since it's private in ErrorDialogs.kt
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                    modifier = Modifier.size(96.dp)
                ) {
                    Icon(
                        imageVector = DeXIcons.Wifi,
                        contentDescription = null,
                        modifier = Modifier.padding(24.dp),
                        tint = Color(0xFF4CAF50)
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.size(32.dp).align(Alignment.BottomEnd).offset(x = (-8).dp, y = (-8).dp)
                ) {
                    Icon(
                        imageVector = DeXIcons.Check,
                        contentDescription = null,
                        modifier = Modifier.padding(6.dp),
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun OnboardingCompletionPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = DeXIcons.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color(0xFF4CAF50)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "All Set!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "You are ready to use DeX. Enjoy seamless file sharing!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
