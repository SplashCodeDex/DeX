# Compose Multiplatform LiquidGlass & UX Component Architecture
**Document Version:** 1.0.0  
**Target Platform:** Compose Multiplatform Desktop (JVM / Skia) & Android  
**Target Repository:** `SplashCodeDex/DeX`  
**Reference Source of Truth:** WPF Floating Dock Card Implementation (`MSIX_Source/Themes/MainWindow.xaml`, `AppStyles.xaml`, `DarkTheme.xaml`, `LightTheme.xaml`, `Bindings_*.ps1`)

---

## Executive Summary

This specification defines the complete Compose Multiplatform UI component tree, design token system, visual effects pipeline, and state machines required to achieve **1:1 visual and UX parity** with the legacy WPF floating docked card interface.

By leveraging `io.github.kyant0:backdrop` (the original AndroidLiquidGlass library by Kyant) combined with Compose Desktop's Skia graphics pipeline, AWT windowing hooks, and coroutine-driven physics transitions, this architecture delivers a state-of-the-art, GPU-accelerated liquid glass desktop interface that replicates every micro-interaction, easing curve, tactile response, and layout mechanism of the WPF engine.

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                     TRANSPARENT 1420×760 AWT SKIA WINDOW                                    │
│                                                                                                             │
│   ┌──────────────────────────────────────────────────────────┐    ┌──────────────────────────────────────┐  │
│   │               EXPANDED PANEL (Left Column)               │    │       MAIN MENU (Right Column)       │  │
│   │                                                          │    │                                      │  │
│   │  [ ⬆ Up ] [ 🔍 Search Transfers/Files... ] [ 📁 Mode ]   │    │  [ 📌 Pin ] ─── Drag Pill ───        │  │
│   │                                                          │    │                                      │  │
│   │  ┌────────────────────────────────────────────────────┐  │    │  [ 🌙 DND ] [ 📱 Mirror ] [ 📁 Pull ]│  │
│   │  │  LazyVerticalGrid (100×105dp Item Cards)           │  │    │  [ 📋 Clip ] [ ❌ Close (Expanded) ]  │  │
│   │  │  📁 Documents   📁 DCIM    📁 Downloads   📄 Photo │  │    │                                      │  │
│   │  │  📄 Log.txt     📄 Data    📄 Video.mp4   📄 APK   │  │    │  Status: Ready [ 📋 Copy IP ]        │  │
│   │  └────────────────────────────────────────────────────┘  │    │  ──────────────────────────────────  │  │
│   │                                                          │    │  Discovered Devices (UDP Peers)      │  │
│   │  [ ⬆ Send Files ]                [ 📁 Send Folders ]     │    │    📱 Nicholas Adima S21 (Online)    │  │
│   │  ───────────────────────────────────────────────────     │    │  Your Devices (Paired Peers)         │  │
│   │  [ 📥 Pull Progress Dock: 45% • 14.2 MB/s [Cancel] ]     │    │    💻 DeXStudios (Windows Desktop)   │  │
│   │                                                          │    │  ──────────────────────────────────  │  │
│   └──────────────────────────────────────────────────────────┘    │  👤 Profile Avatar │ Exit Engine ⌘Q  │  │
│                                                                   └──────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 1. Liquid Glass & Frosted Glass Visual Effects Pipeline

### 1.1 `io.github.kyant0:backdrop` Desktop Configuration

The `io.github.kyant0:backdrop` library (v2.0.0) operates across Android, iOS, Desktop (JVM), and Web via Skia. In Compose Desktop, the library uses Skia's `ImageFilter` and `RuntimeEffect` (SkSL) to deliver real-time backdrop sampling, Gaussian blur, lens refraction, and vibrancy.

#### Mental Model: Two-Layer Architecture
1. **Backdrop Layer (`LayerBackdrop`)**: The root content captured into an offscreen GPU graphics layer via `Modifier.layerBackdrop(backdrop)`.
2. **Glass Layer (`Modifier.drawBackdrop`)**: The foreground component that samples from the captured layer, applies the shader chain, renders decorative highlights/shadows, and paints the tinted surface.

```kotlin
// Setup in commonMain / desktopMain
dependencies {
    implementation("io.github.kyant0:backdrop:2.0.0")
}
```

#### Complete Glass Shader Specification
```kotlin
package com.dexstudios.dex.core.designsystem.components.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/**
 * Curated Liquid Glass Configurations for DeX Desktop & Android
 */
object DeXGlassPresets {
    /**
     * Primary Floating Dock Card Glass (Heavy Frosted Look)
     */
    val DockCardDark = LiquidGlassConfig(
        shape = RoundedCornerShape(34.dp),
        blurRadius = 24.dp,
        lensHeight = 18.dp,
        lensAmount = 36.dp,
        vibrancyEnabled = true,
        chromaticAberration = true,
        depthEffect = true,
        restRefraction = 0.25f,
        surfaceTint = Color(0xFF16121A),
        surfaceTintAlpha = 0.82f,
        highlight = Highlight.Ambient.copy(alpha = 0.4f),
        shadowRadius = 32.dp,
        shadowColor = Color.Black.copy(alpha = 0.55f),
        innerShadow = InnerShadow(radius = 6.dp, alpha = 0.15f)
    )

    val DockCardLight = LiquidGlassConfig(
        shape = RoundedCornerShape(34.dp),
        blurRadius = 24.dp,
        lensHeight = 18.dp,
        lensAmount = 36.dp,
        vibrancyEnabled = true,
        chromaticAberration = true,
        depthEffect = true,
        restRefraction = 0.25f,
        surfaceTint = Color(0xFFFFFFFF),
        surfaceTintAlpha = 0.85f,
        highlight = Highlight.Ambient.copy(alpha = 0.6f),
        shadowRadius = 32.dp,
        shadowColor = Color.Black.copy(alpha = 0.18f),
        innerShadow = InnerShadow(radius = 4.dp, alpha = 0.08f)
    )

    /**
     * Quick Action Icon Buttons (Crisp, High-Refraction Glass)
     */
    val QuickActionDark = LiquidGlassConfig(
        shape = RoundedCornerShape(20.dp),
        blurRadius = 4.dp,
        lensHeight = 14.dp,
        lensAmount = 28.dp,
        vibrancyEnabled = true,
        chromaticAberration = true,
        depthEffect = true,
        restRefraction = 0.4f,
        surfaceTint = Color(0xFF2B2631),
        surfaceTintAlpha = 0.70f,
        highlight = Highlight.Default,
        shadowRadius = 8.dp,
        shadowColor = Color.Black.copy(alpha = 0.35f)
    )

    /**
     * Active/Selected Button Glass (Emerald Accent Glow)
     */
    val QuickActionActive = LiquidGlassConfig(
        shape = RoundedCornerShape(20.dp),
        blurRadius = 6.dp,
        lensHeight = 16.dp,
        lensAmount = 32.dp,
        vibrancyEnabled = true,
        chromaticAberration = true,
        depthEffect = true,
        restRefraction = 0.6f,
        surfaceTint = Color(0xFF0AE66D),
        surfaceTintAlpha = 0.90f,
        highlight = Highlight.Ambient.copy(alpha = 0.8f),
        shadowRadius = 14.dp,
        shadowColor = Color(0xFF0AE66D).copy(alpha = 0.45f)
    )

    /**
     * Item Card Hover Glass (File/Folder 8dp cards)
     */
    val ItemCardHover = LiquidGlassConfig(
        shape = RoundedCornerShape(8.dp),
        blurRadius = 8.dp,
        lensHeight = 8.dp,
        lensAmount = 16.dp,
        vibrancyEnabled = false,
        chromaticAberration = false,
        depthEffect = false,
        surfaceTint = Color(0xFF2B2631),
        surfaceTintAlpha = 0.50f,
        highlight = Highlight.Plain,
        shadowRadius = 6.dp,
        shadowColor = Color.Black.copy(alpha = 0.25f)
    )
}
```

### 1.2 Desktop Fallback & Skia Shader / RenderEffect Mechanics

#### Window Transparency & Graphics Pipeline
In Compose Desktop JVM, setting `Window(undecorated = true, transparent = true)` removes OS decorations and clears the default AWT background.

1. **In-Window Backdrop Sampling**:
   The `io.github.kyant0:backdrop` library captures composables marked with `Modifier.layerBackdrop(backdrop)`. In desktop mode, a canvas wallpaper/ambient gradient layer is rendered at the root of the 1420×760 transparent window canvas. The floating dock card samples from this layer, rendering real-time blur and refraction.

2. **OS Desktop Desktop Fallback (No Backdrop in Scope)**:
   When sampling OS desktop pixels outside the application process (where OS security prevents arbitrary cross-window frame grabbing without native DWM composition), the architecture applies an **Adaptive Solid Frosted Fallback**:
   - Skia `RuntimeEffect` / `ColorFilter` combined with translucent surface alpha (`#16121A` @ 85% opacity).
   - High-fidelity ambient rim highlights (`Highlight.Ambient`).
   - Deep Gaussian drop shadows via Skia `MaskFilter.makeBlur`.
   - Windows 11 Native Acrylic / Mica DWM Attribute integration via JNA (`DwmSetWindowAttribute`) when running on Windows 10/11 21H2+.

```kotlin
// Desktop Native Skia Custom Drop Shadow Modifier
fun Modifier.skiaDropShadow(
    color: Color = Color.Black.copy(alpha = 0.45f),
    blurRadius: Dp = 24.dp,
    borderRadius: Dp = 34.dp,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 8.dp
): Modifier = this.drawBehind {
    drawIntoCanvas { canvas ->
        val paint = org.jetbrains.skia.Paint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
            this.maskFilter = org.jetbrains.skia.MaskFilter.makeBlur(
                org.jetbrains.skia.FilterBlurMode.NORMAL,
                blurRadius.toPx()
            )
        }
        canvas.nativeCanvas.drawRoundRect(
            left = offsetX.toPx(),
            top = offsetY.toPx(),
            right = size.width + offsetX.toPx(),
            bottom = size.height + offsetY.toPx(),
            radiusX = borderRadius.toPx(),
            radiusY = borderRadius.toPx(),
            paint = paint
        )
    }
}
```

### 1.3 Anti-Aliasing & Outer Border Glows

On transparent desktop surfaces, sub-pixel rasterization can produce aliased fringes along 34dp rounded borders if strokes are drawn outside the clipping bounds.

**Solution: Inset Sub-Pixel Stroke Drawing**:
```kotlin
fun Modifier.subpixelBorderGlow(
    strokeWidth: Dp = 1.dp,
    borderColor: Color = Color(0xFF2B2631),
    glowColor: Color = Color(0xFF0AE66D).copy(alpha = 0.15f),
    cornerRadius: Dp = 34.dp
): Modifier = this.drawWithContent {
    drawContent()
    val halfStroke = strokeWidth.toPx() / 2f
    // 1. Subtle Outer Glow
    drawRoundRect(
        color = glowColor,
        topLeft = Offset(-halfStroke, -halfStroke),
        size = Size(size.width + halfStroke * 2, size.height + halfStroke * 2),
        cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
        style = Stroke(width = strokeWidth.toPx() * 2)
    )
    // 2. Crisp Inset Border Line
    drawRoundRect(
        color = borderColor,
        topLeft = Offset(halfStroke, halfStroke),
        size = Size(size.width - halfStroke * 2, size.height - halfStroke * 2),
        cornerRadius = CornerRadius(cornerRadius.toPx() - halfStroke, cornerRadius.toPx() - halfStroke),
        style = Stroke(width = strokeWidth.toPx())
    )
}
```

---

## 2. Floating Card Component Hierarchy & State Transitions

### 2.1 State Dimension Matrix

| State | Card Width | Card Height | Left Panel Visibility | Close (X) Button | Drag Pill | Max Height |
|---|---|---|---|---|---|---|
| **Contracted (Default)** | `300.dp` | `500.dp` | `Collapsed` (Opacity 0) | `Collapsed` (Width 0) | Centered | `352.dp` (Views container) |
| **Expanded (File Explorer)** | `1054.dp` (+754) | `695.dp` (+195) | `Visible` (`FileExplorer`) | `Visible` (Width 56dp) | Left-Aligned | `9999.dp` (Uncapped) |
| **Expanded (Settings)** | `1054.dp` (+754) | `695.dp` (+195) | `Visible` (`SettingsPanel`) | `Visible` (Width 56dp) | Left-Aligned | `9999.dp` (Uncapped) |
| **Pairing PIN/QR View** | `300.dp` | `500.dp` | `Collapsed` | `Collapsed` | Centered | Slide X: 300 → 0 |

### 2.2 Component Hierarchy Tree

```
FloatingDockCard (Root Window Container - 1420×760 Canvas)
 └── Box (Modifier.fillMaxSize())
      └── DockCardContent (Modifier.align(Alignment.BottomEnd).padding(25.dp))
           └── LiquidGlassPanel (DeXGlassPresets.DockCardDark, Shape = 34.dp)
                └── Row (Modifier.fillMaxSize())
                     ├── AnimatedVisibility (isExpanded: Left Column Panel)
                     │    ├── FileExplorerPanel (SlideInHorizontally X: 150 -> 0 + FadeIn)
                     │    │    ├── ExplorerHeaderBar (btnUpDir, txtSearch, btnToggleExplorerMode)
                     │    │    ├── FileExplorerContent (LazyVerticalGrid - 100×105dp Cards)
                     │    │    │    ├── FolderItemCard (0xE8B7 folder icon, name, meta)
                     │    │    │    └── FileItemCard (0xE7C3 icon/thumbnail, name, size)
                     │    │    ├── EmptyFolderStateOverlay (if items.isEmpty)
                     │    │    ├── BottomPushActionBar (btnPushFiles, btnPushFolder)
                     │    │    ├── DockDownloadToast (Toast capsule: "Saved to Downloads\DeX")
                     │    │    └── DockPullProgress (Pulling progress bar, B/s, Cancel)
                     │    └── SettingsPanel (SlideInHorizontally X: 150 -> 0 + FadeIn)
                     │         ├── SettingsHeaderBar
                     │         └── SettingsScrollViewer (Profile, DND, ADB, Google, Theme, Storage, About)
                     └── MainMenuColumn (Always visible right column, width = 300.dp)
                          ├── TopActionsBar (DragPillHandle + btnToggleTopmost)
                          ├── QuickActionToolbar (DND, Mirror Phone, Transfers/Files, Clipboard, btnCloseMenu)
                          ├── AdbStatusPane (Collapsible 39dp height, "Status: Ready", btnCopyIP)
                          ├── SeparatorLine
                          ├── ScrollableDeviceList (Modifier.weight(1f))
                          │    ├── DiscoveredDevicesSection (Dynamic UDP peers, pulse ring, status dot)
                          │    ├── YourDevicesSection (Paired peers, model, Wi-Fi, battery % and status)
                          │    └── ScaffoldingProfilesSection (Sample WAN profiles: Ama, Akua, Kwame)
                          ├── PinPairingOverlayView (SlideIn X: 300 -> 0, Digits 44×56dp, QR Code switch)
                          ├── SeparatorLine
                          └── BottomDockFooter (Profile Avatar Settings Trigger + btnExit "Exit Engine ⌘Q")
```

### 2.3 60/120fps Animation Physics & Easing Specifications

WPF storyboards rely on `ElasticEase` (bouncy expansion) and `BackEase` (anticipation / overshoot). In Compose, these are ported directly using custom `Easing` curves and tuned `Spring` physics:

```kotlin
package com.dexstudios.dex.core.designsystem.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

object DockCardPhysics {
    // 1:1 Port of WPF AppStyles.xaml Animations
    
    // WPF ElasticEase(Oscillations=1, Springiness=7)
    val ElasticExpansionSpec = spring<Float>(
        dampingRatio = 0.65f,
        stiffness = 300f
    )
    val ElasticDpSpec = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = 0.65f,
        stiffness = 300f
    )

    // WPF BackEase(Amplitude=3.53) - PopIn Window Entrance
    val PopInEase = Easing { fraction ->
        val t = fraction - 1f
        val a = 3.53f
        1f + t * t * ((a + 1f) * t + a)
    }

    // WPF BackEase(Amplitude=1.22) - Hover Button Scale & Translate
    val HoverEase = Easing { fraction ->
        val t = fraction - 1f
        val a = 1.22f
        1f + t * t * ((a + 1f) * t + a)
    }

    // WPF BackEase(Amplitude=0.15) - Contract Menu Panel Shrink
    val ContractEase = Easing { fraction ->
        val t = fraction - 1f
        val a = 0.15f
        1f + t * t * ((a + 1f) * t + a)
    }
}
```

#### Transition Coordination Implementation
```kotlin
@Composable
fun DockCardTransitionContainer(
    isExpanded: Boolean,
    expandedPanel: ExpandedPanelType?,
    onContract: () -> Unit,
    content: @Composable (cardWidth: Dp, cardHeight: Dp, contentOpacity: Float) -> Unit
) {
    val transition = updateTransition(targetState = isExpanded, label = "dockCardState")

    val cardWidth by transition.animateDp(
        transitionSpec = {
            if (targetState) {
                // Expanding: 800ms Elastic Spring
                spring(dampingRatio = 0.65f, stiffness = 300f)
            } else {
                // Contracting: 250ms delayed start after content fades
                spring(dampingRatio = 0.85f, stiffness = 400f)
            }
        },
        label = "cardWidth"
    ) { expanded ->
        if (expanded) 1054.dp else 300.dp
    }

    val cardHeight by transition.animateDp(
        transitionSpec = {
            if (targetState) {
                spring(dampingRatio = 0.65f, stiffness = 300f)
            } else {
                spring(dampingRatio = 0.85f, stiffness = 400f)
            }
        },
        label = "cardHeight"
    ) { expanded ->
        if (expanded) 695.dp else 500.dp
    }

    val leftPanelOpacity by transition.animateFloat(
        transitionSpec = {
            if (targetState) {
                // Fade in starting after 100ms
                tween(durationMillis = 600, delayMillis = 100)
            } else {
                // Fade out immediately in 250ms BEFORE panel shrinks
                tween(durationMillis = 250)
            }
        },
        label = "panelOpacity"
    ) { expanded ->
        if (expanded) 1f else 0f
    }

    content(cardWidth, cardHeight, leftPanelOpacity)
}
```

---

## 3. Quick Action Buttons & Micro-Interactions

### 3.1 Button Specifications

WPF defines `QuickActionBtn` (`MSIX_Source/Themes/AppStyles.xaml L612-680`) and `DangerQuickActionBtn` (`L682-752`) with exact tactile kinematics:

- **Dimensions**: `Width = 56.dp, Height = 44.dp, CornerRadius = 20.dp`.
- **Rest State**: Background `AccentBrush` (`#2B2631`), Glyph 20sp in `PrimaryTextBrush` (`#FFFFFF`).
- **Hover State**:
  - `ScaleX / ScaleY`: `1.0 → 1.08` (Easing: `HoverEase`, duration 500ms).
  - `TranslateY`: `0 → -3.dp` (Subtle tactile lift).
- **Press State**:
  - `ScaleX / ScaleY`: `1.08 → 0.85` (Quick punch-in, duration 100ms).
  - `TranslateY`: `-3.dp → +3.dp` (Tactile sink).
- **Checked/Active State**:
  - Background morphs to `SecondaryBrush` (`#0AE66D`), glyph tint `#000000`.
- **Danger Button (`btnCloseMenu`)**:
  - Hover background morphs to `DangerBrush` (`#FF453A`), white icon.
  - Width animates `0.dp → 56.dp` when card expands, sliding action buttons smoothly into place.

### 3.2 Compose `QuickActionButton` Implementation

```kotlin
package com.dexstudios.dex.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.core.designsystem.components.glass.DeXGlassPresets
import com.dexstudios.dex.core.designsystem.theme.DockCardPhysics
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow

@Composable
fun DeXQuickActionButton(
    icon: ImageVector,
    tooltip: String,
    isChecked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDanger: Boolean = false,
    backdrop: Backdrop? = null,
    badgeCount: Int = 0
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    // 1:1 Port of WPF Hover & Press Kinematics
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.85f
            isHovered -> 1.08f
            else -> 1.0f
        },
        animationSpec = if (isPressed) tween(100) else tween(300, easing = DockCardPhysics.HoverEase),
        label = "btnScale"
    )

    val translateY by animateDpAsState(
        targetValue = when {
            isPressed -> 3.dp
            isHovered -> (-3).dp
            else -> 0.dp
        },
        animationSpec = if (isPressed) tween(100) else tween(300, easing = DockCardPhysics.HoverEase),
        label = "btnTransY"
    )

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isDanger && (isHovered || isPressed) -> Color(0xFFFF453A)
            isChecked -> Color(0xFF0AE66D)
            isHovered -> Color(0xFF332D3B)
            else -> Color(0xFF2B2631)
        },
        animationSpec = tween(200),
        label = "btnBgColor"
    )

    val iconColor by animateColorAsState(
        targetValue = when {
            isChecked -> Color(0xFF000000)
            isDanger && (isHovered || isPressed) -> Color(0xFFFFFFFF)
            else -> Color(0xFFFFFFFF)
        },
        animationSpec = tween(200),
        label = "btnIconColor"
    )

    Box(
        modifier = modifier
            .size(width = 56.dp, height = 44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = translateY.toPx()
            }
            .then(
                if (backdrop != null) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedCornerShape(20.dp) },
                        effects = {
                            if (isChecked) vibrancy()
                            blur(if (isPressed) 2.dp.toPx() else 4.dp.toPx())
                            lens(12.dp.toPx(), 24.dp.toPx(), depthEffect = true)
                        },
                        highlight = { DeXGlassPresets.QuickActionDark.highlight },
                        shadow = {
                            Shadow(
                                radius = if (isChecked) 14.dp else 8.dp,
                                color = if (isChecked) Color(0xFF0AE66D).copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.3f)
                            )
                        },
                        onDrawSurface = { drawRect(backgroundColor.copy(alpha = 0.75f)) }
                    )
                } else {
                    Modifier
                        .skiaDropShadow(
                            color = if (isChecked) Color(0xFF0AE66D).copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.25f),
                            blurRadius = if (isChecked) 12.dp else 6.dp,
                            borderRadius = 20.dp
                        )
                        .background(backgroundColor, RoundedCornerShape(20.dp))
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = tooltip,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )

        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 4.dp)
                    .background(Color(0xFF0AE66D), RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badgeCount.toString(),
                    color = Color.Black,
                    fontSize = 9.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        }
    }
}
```

---

## 4. Embedded File Explorer & Directory Tree Architecture

### 4.1 Layout Structure (WPF `WrapPanel` → Compose `LazyVerticalGrid`)

In WPF (`MainWindow.xaml L100-143`), `lbFiles` uses a `WrapPanel` where items are rendered as fixed 100×105dp interactive tiles. In Compose Desktop, this is implemented using `LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 100.dp))` for optimal multiplatform scrolling performance and zero-jank directory swaps.

```kotlin
package com.dexstudios.dex.desktop.window.explorer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.core.designsystem.components.DeXScrollbar
import com.dexstudios.dex.desktop.window.explorer.models.ExplorerItem

@Composable
fun FileExplorerGrid(
    items: List<ExplorerItem>,
    selectedIds: Set<String>,
    onItemClick: (ExplorerItem) -> Unit,
    onItemDoubleClick: (ExplorerItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            state = gridState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(items, key = { it.id }) { item ->
                ExplorerItemCard(
                    item = item,
                    isSelected = selectedIds.contains(item.id),
                    onClick = { onItemClick(item) },
                    onDoubleClick = { onItemDoubleClick(item) }
                )
            }
        }

        // Custom minimal 2px spatial scrollbar
        DeXScrollbar(
            gridState = gridState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp)
        )
    }
}
```

### 4.2 File & Folder Item Cards

Matches WPF `FolderGridTemplate` (`AppStyles.xaml L294-352`) and `FileGridTemplate` (`L353-418`):
- **Tile Dimensions**: `Width = 100.dp, Height = 105.dp`.
- **Card Hover Physics**:
  - `ScaleX / ScaleY`: `1.0 → 1.05` (Easing: `HoverEase`, duration 300ms).
  - `TranslateY`: `0 → -2.dp`.
- **Card Press Physics**:
  - `ScaleX / ScaleY`: `1.05 → 0.94` (Duration 100ms).
- **Double-Click Threshold Guard**: 400ms interval check to prevent double-tap race conditions.

```kotlin
@Composable
fun ExplorerItemCard(
    item: ExplorerItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    var lastClickTime by remember { mutableStateOf(0L) }

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.94f
            isHovered -> 1.05f
            else -> 1.0f
        },
        animationSpec = if (isPressed) tween(100) else tween(300, easing = DockCardPhysics.HoverEase),
        label = "itemScale"
    )

    val translateY by animateDpAsState(
        targetValue = if (isHovered && !isPressed) (-2).dp else 0.dp,
        animationSpec = tween(300, easing = DockCardPhysics.HoverEase),
        label = "itemTransY"
    )

    val backgroundColor = when {
        isSelected && isHovered -> Color(0xFF3D3647)
        isSelected -> Color(0xFF332D3B)
        isHovered -> Color(0xFF2B2631)
        else -> Color.Transparent
    }

    val borderColor = if (isSelected) Color(0xFF0AE66D) else Color.Transparent

    Column(
        modifier = modifier
            .size(width = 100.dp, height = 105.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.translationY = translateY.toPx()
            }
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    val now = System.currentTimeMillis()
                    if (now - lastClickTime < 400) {
                        onDoubleClick()
                    } else {
                        onClick()
                    }
                    lastClickTime = now
                }
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (item.isDirectory) {
            // Folder Glyph 0xE8B7
            Icon(
                imageVector = MaterialSymbols.Folder,
                contentDescription = null,
                tint = Color(0xFF0AE66D),
                modifier = Modifier.size(42.dp)
            )
        } else {
            // File Thumbnail with 4dp rounded clip or File Glyph 0xE7C3
            if (item.thumbnailUrl != null) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp))
                )
            } else {
                Icon(
                    imageVector = MaterialSymbols.Description,
                    contentDescription = null,
                    tint = Color(0xFFA0A0A0),
                    modifier = Modifier.size(42.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.name,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Text(
            text = item.metaText,
            color = Color(0xFFA0A0A0),
            fontSize = 10.sp,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
```

### 4.3 Breadcrumb Bar & Parent Directory Up Navigation

Matches `Bindings_FileBrowser.ps1 L251-292`:
- Supports both **SAF Document URIs** (`content://.../document/...%2F...`) and **Local Windows Paths** (`C:\Users\...\Downloads\DeX`).
- Parent segment truncation:
  - SAF: Strips last `%2F` token; if at tree root, transitions to Phone Folders list.
  - Windows: Strips last `\` directory segment down to drive root.

```kotlin
@Composable
fun ExplorerHeaderBar(
    currentPath: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onNavigateUp: () -> Unit,
    isExplorerMode: Boolean,
    onToggleExplorerMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Up Directory Button (Visible when inside a subfolder)
        if (currentPath.isNotBlank() && currentPath != "Phone Folders") {
            IconButton(
                onClick = onNavigateUp,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFF2B2631), RoundedCornerShape(18.dp))
            ) {
                Icon(
                    imageVector = MaterialSymbols.ArrowUpward,
                    contentDescription = "Up Directory",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Search Transfers / Files Input with Instant Debounce
        Box(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .background(Color(0xFF2B2631), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = MaterialSymbols.Search,
                    contentDescription = null,
                    tint = Color(0xFFA0A0A0),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    ),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = if (isExplorerMode) "Search files..." else "Search transfers...",
                                color = Color(0xFFA0A0A0),
                                fontSize = 14.sp
                            )
                        }
                        innerTextField()
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchChange("") },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Close,
                            contentDescription = "Clear",
                            tint = Color(0xFFA0A0A0),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Mode Toggle Button (Toggle between Local History & Phone SAF Explorer)
        IconButton(
            onClick = onToggleExplorerMode,
            modifier = Modifier
                .size(36.dp)
                .background(Color(0xFF2B2631), RoundedCornerShape(18.dp))
        ) {
            Icon(
                imageVector = if (isExplorerMode) MaterialSymbols.FolderShared else MaterialSymbols.History,
                contentDescription = "Toggle Explorer Mode",
                tint = if (isExplorerMode) Color(0xFF0AE66D) else Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
```

### 4.4 Pull Progress Floating Toast Dock

Matches `MainWindow.xaml L185-200` & `Bindings_FileBrowser.ps1 L26-140`:
- Floats at the bottom center of the left panel during active background phone pulls.
- Displays file count, throughput speed, 4dp progress bar, and active cancellation hook.

```kotlin
@Composable
fun PullProgressDock(
    progressPercent: Int,
    statusText: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(360.dp)
            .background(Color(0xFF2B2631), RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF332D3B), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statusText,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Close,
                        contentDescription = "Cancel Pull",
                        tint = Color(0xFFA0A0A0),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progressPercent / 100f },
                color = Color(0xFF0AE66D),
                trackColor = Color(0xFF16121A),
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
            )
        }
    }
}
```

---

## 5. Complete Design System Tokens (1:1 WPF Mapping)

### 5.1 Color Palette

```kotlin
package com.dexstudios.dex.core.designsystem.theme

import androidx.compose.ui.graphics.Color

object DeXColors {
    // === Dark Theme (Default) ===
    object Dark {
        val Primary = Color(0xFF16121A)          // Main window card background
        val Accent = Color(0xFF2B2631)           // Secondary containers, borders, unselected items
        val PrimaryText = Color(0xFFFFFFFF)      // Pure white primary titles/headers
        val SecondaryText = Color(0xFFA0A0A0)    // Muted grey metadata, subtitles, placeholders
        val Secondary = Color(0xFF0AE66D)        // Emerald vibrant status / selection / toggle active
        val SecondaryForeground = Color(0xFF000000) // Black text over emerald buttons
        val Danger = Color(0xFFFF453A)           // Red for delete, exit, and close actions
        
        // Item Highlights (Solid & Translucent)
        val SecondaryHover = Color(0xFF2B2631)
        val SecondarySelected = Color(0xFF332D3B)
        val SecondarySelectedHover = Color(0xFF3D3647)
        val SecondarySelectedBorder = Color(0xFF0AE66D)
    }

    // === Light Theme ===
    object Light {
        val Primary = Color(0xFFFFFFFF)
        val Accent = Color(0xFFF2F2F7)
        val PrimaryText = Color(0xFF000000)
        val SecondaryText = Color(0xFF3A3A3C)
        val Secondary = Color(0xFF0AE66D)
        val SecondaryForeground = Color(0xFF000000)
        val Danger = Color(0xFFFF3B30)
        
        val SecondaryHover = Color(0xFFE5E5EA)
        val SecondarySelected = Color(0xFFD1D1D6)
        val SecondarySelectedHover = Color(0xFFC7C7CC)
        val SecondarySelectedBorder = Color(0xFF0AE66D)
    }
}
```

### 5.2 Typography Scale

```kotlin
package com.dexstudios.dex.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val DeXTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        lineHeight = 14.sp
    )
)
```

### 5.3 Corner Radius & Elevation Hierarchy

```kotlin
object DeXShapes {
    val WindowCard = androidx.compose.foundation.shape.RoundedCornerShape(34.dp)
    val QuickActionButton = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
    val SearchPill = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
    val TopAppBarIsland = androidx.compose.foundation.shape.RoundedCornerShape(48.dp)
    val ModalPanel = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    val ListItem = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    val BadgePill = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    val GridItemCard = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
    val DragPill = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)
}
```

---

## 6. Implementation Blueprint & File Mapping

The following table maps the legacy WPF/PowerShell architecture files directly to their corresponding new and modified Compose Multiplatform components:

| Legacy WPF/PS Component | Compose Multiplatform File | Role / Responsibility |
|---|---|---|
| `MainWindow.xaml` | `desktopMain/.../window/FloatingDockCard.kt` | Root window layout & dock card container |
| `MainWindow.xaml (mainBorder Column 1)` | `desktopMain/.../window/MainMenuColumn.kt` | Right column (Drag pill, quick actions, device lists, profile) |
| `MainWindow.xaml (FileExplorer Grid)` | `desktopMain/.../window/FileExplorerPanel.kt` | Left column file explorer, breadcrumb, grid, push buttons |
| `MainWindow.xaml (SettingsPanel Grid)` | `desktopMain/.../window/SettingsPanel.kt` | Left column settings, DND, ADB, themes, Google sign-in |
| `MainWindow.xaml (pinViewPanel Grid)` | `desktopMain/.../window/PinPairingView.kt` | PIN digits / QR code pairing overlay |
| `AppStyles.xaml (QuickActionBtn)` | `commonMain/.../components/DeXQuickActionButton.kt` | Reusable tactile liquid glass action button |
| `AppStyles.xaml (SpatialListItem)` | `commonMain/.../components/SpatialListItem.kt` | Device and settings list item with hover translate |
| `AppStyles.xaml (PopIn / ExpandMenu)` | `commonMain/.../theme/DockCardPhysics.kt` | Physics springs, Easing curves, transition specs |
| `Bindings_FileBrowser.ps1` | `desktopMain/.../window/explorer/FileExplorerViewModel.kt` | Directory navigation, SAF relay, pull progress tasks |
| `DarkTheme.xaml / LightTheme.xaml` | `commonMain/.../theme/DeXColors.kt` | Color tokens & dynamic theme switching |

---

## 7. Edge Cases & Robustness Matrix

| Edge Case | Risk | Compose Architectural Solution |
|---|---|---|
| **Multiple rapid clicks on expand/contract** | Inconsistent animation state, layout flicker | Single-source `updateTransition` with `Animatable` locks, preventing mid-animation state corruption. |
| **High DPI / Multi-Monitor Display Scaling** | Blur radius or window offsets misaligned across screens | `LocalDensity.current` scaling for all shader radii (`.toPx()`) + `getWorkAreaBounds()` querying active display configuration. |
| **Fast double-click on file grid** | Multiple concurrent batch pull jobs spawned | 400ms timestamp debounce guard in `ExplorerItemCard` preventing duplicate request IDs. |
| **Transparent window click-through** | Transparent areas blocking user clicks to desktop icons | Window root canvas sized to fit card bounds or transparent hit-test bounds using AWT shape masking (`Window.setShape`). |
| **Phone disconnected during SAF pull** | Pull progress hangs indefinitely at 99% | Activity-based stall timer (120s timeout) in `FileExplorerViewModel` with clean failure toast. |
| **GPU / Skia shader unsupported** | App crash or black screen on legacy hardware | Graceful fallback in `LiquidGlassPanel` to solid translucent surface + Skia drop shadow. |
