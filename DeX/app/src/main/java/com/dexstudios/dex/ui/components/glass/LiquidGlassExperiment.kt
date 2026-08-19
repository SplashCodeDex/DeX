package com.dexstudios.dex.ui.components.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.ui.icons.MaterialSymbols
import com.dexstudios.dex.ui.theme.DeXTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Composable
fun LiquidGlassRefractionExperiment(
    lensHeight: Float = 32f,
    lensAmount: Float = 34f,
    restRefraction: Float = 0.56f,
    chromaticAberration: Boolean = false,
    surfaceTintAlpha: Float = 0.15f,
    highlighterHeight: Float = 78f,
    navbarHeight: Float = 72f,
    iconsOnTop: Boolean = false
) {
    val localNavBackdrop = rememberLayerBackdrop()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .background(Color(0xFF080808)),
        contentAlignment = Alignment.Center
    ) {
        // Background Scene
        Box(
            modifier = Modifier
                .size(400.dp, 40.dp)
                .background(Color.Red.copy(alpha = 0.1f))
        )

        Box(
            modifier = Modifier
                .requiredSize(320.dp, 170.dp),
            contentAlignment = Alignment.Center
        ) {
            // 1. The Captured Layer
            Box(
                modifier = Modifier
                    .size(320.dp, 72.dp)
                    .let { if (!iconsOnTop) it.layerBackdrop(localNavBackdrop) else it },
                contentAlignment = Alignment.Center
            ) {
                // Navbar Board
                Box(modifier = if (iconsOnTop) Modifier.fillMaxSize().layerBackdrop(localNavBackdrop) else Modifier.fillMaxSize()) {
                    LiquidGlassPanel(
                        backdrop = rememberLayerBackdrop(),
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        config = LiquidGlassPresets.NavBar.copy(surfaceTintAlpha = 0.25f)
                    ) {}
                }

                // Icons (if not on top)
                if (!iconsOnTop) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(4) { index ->
                            NavBarIconExperiment(index == 3)
                        }
                    }
                }
            }

            // 2. The Highlighter
            val config = LiquidGlassPresets.IconButton.copy(
                blurRadius = 0.dp,
                lensHeight = lensHeight.dp,
                lensAmount = lensAmount.dp,
                restRefraction = restRefraction,
                chromaticAberration = chromaticAberration,
                surfaceTintAlpha = surfaceTintAlpha,
                depthEffect = true,
                highlight = LiquidGlassPresets.IconButton.highlight.copy(alpha = 0.4f)
            )

            LiquidGlassPanel(
                backdrop = localNavBackdrop,
                modifier = Modifier
                    .offset(x = 105.dp)
                    .size(105.dp, highlighterHeight.dp),
                shape = CircleShape,
                config = config
            ) {}

            // 3. Icons (if on top)
            if (iconsOnTop) {
                Row(
                    modifier = Modifier.size(320.dp, 72.dp).padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(4) { index ->
                        NavBarIconExperiment(index == 3)
                    }
                }
            }
        }
    }
}

@Composable
fun NavBarIconExperiment(isSelected: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = MaterialSymbols.AccountCircle,
            contentDescription = null,
            tint = if (isSelected) Color(0xFF00BFFF) else Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = "Profile",
            color = if (isSelected) Color(0xFF00BFFF) else Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp
        )
    }
}

@Preview
@Composable
fun Preview_IconsOnTop() {
    DeXTheme {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Icons On Top (Sharp Icons, Bent Background)", color = Color.White)
            LiquidGlassRefractionExperiment(
                lensAmount = 80f,
                restRefraction = 1.2f,
                iconsOnTop = true
            )
        }
    }
}

@Preview
@Composable
fun Preview_ReferenceMatch() {
    DeXTheme {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Reference Match (Extreme Warp + Dark Tint)", color = Color.White)
            LiquidGlassRefractionExperiment(
                lensHeight = 40f,
                lensAmount = 140f,      // Deep bending
                restRefraction = 1.25f, // Magnification
                surfaceTintAlpha = 0.4f, // Darker center like screenshot
                highlighterHeight = 92f, // Much taller than 72dp navbar
                chromaticAberration = true,
                iconsOnTop = false      // Everything under the lens
            )
        }
    }
}
