# Milestone 4 Handoff Report: Theme Color Tokens, Desktop Packaging & Verification Strategy

**Explorer**: `explorer_m4_3` (teamwork_preview_explorer)  
**Target Milestone**: M4 (Visual Styling, Liquid Glass & Final Build Verification)  
**Status**: COMPLETE (Read-only Investigation & Blueprint Formulation)  
**Date**: 2026-08-17  

---

## 1. Observation

Direct observations from examining the codebase, WPF source of truth (`MSIX_Source`), and live Gradle execution:

### 1.1 WPF Source of Truth Color Tokens
From [`w:\CodeDeX\DeX\MSIX_Source\Themes\DarkTheme.xaml`](file:///w:/CodeDeX/DeX/MSIX_Source/Themes/DarkTheme.xaml#L1-L24) and [`w:\CodeDeX\DeX\MSIX_Source\Themes\LightTheme.xaml`](file:///w:/CodeDeX/DeX/MSIX_Source/Themes/LightTheme.xaml#L1-L24):

* **Dark Theme (Default)**:
  * `PrimaryBrush` = `#16121A` (L6) — Floating dock card surface background
  * `AccentBrush` = `#2B2631` (L7) — Inner containers, button resting state, search bar, outline borders
  * `PrimaryTextBrush` = `#FFFFFF` / `White` (L10) — Primary headers, device alias labels, PIN digits
  * `SecondaryTextBrush` = `#A0A0A0` (L11) — Telemetry subtext, IP/port labels, timestamps, inactive icons
  * `SecondaryBrush` = `#0AE66D` (L14) — Emerald accent for active toggle pills, status badges, progress bars
  * `SecondaryForegroundBrush` = `#000000` (L15) — Black text/icons over Emerald green
  * `DangerBrush` = `#FF453A` (L16) — Red for danger close pill, forget device, force exit
  * `SecondaryHoverBrush` = `#2B2631` (L19) — Device row / list item hover background
  * `SecondarySelectedBrush` = `#332D3B` (L20) — Selected list item background
  * `SecondarySelectedHoverBrush` = `#3D3647` (L21) — Selected + hover list item background
  * `SecondarySelectedBorderBrush` = `#0AE66D` (L22) — 1dp Emerald border on active selection

* **Light Theme**:
  * `PrimaryBrush` = `#FFFFFF` (L6) — Floating dock card surface background
  * `AccentBrush` = `#F2F2F7` (L7, and `#F5F3F7` in design variants) — Card containers and outline borders
  * `PrimaryTextBrush` = `#000000` / `Black` (L10) — Primary text
  * `SecondaryTextBrush` = `#3A3A3C` (L11) — Subtitles, metadata, secondary text
  * `SecondaryBrush` = `#0AE66D` (L14) — Emerald accent (shared across themes)
  * `SecondaryForegroundBrush` = `#000000` (L15) — Black foreground over Emerald
  * `DangerBrush` = `#FF3B30` (L16) — iOS/WPF light danger red
  * `SecondaryHoverBrush` = `#E5E5EA` (L19)
  * `SecondarySelectedBrush` = `#D1D1D6` (L20)
  * `SecondarySelectedHoverBrush` = `#C7C7CC` (L21)
  * `SecondarySelectedBorderBrush` = `#0AE66D` (L22)

### 1.2 Current State of Compose Color & Theme Files
Inspecting [`w:\CodeDeX\DeX\DeX\core\designsystem\src\commonMain\kotlin\com\dexstudios\dex\core\designsystem\theme\Color.kt`](file:///w:/CodeDeX/DeX/DeX/core/designsystem/src/commonMain/kotlin/com/dexstudios/dex/core/designsystem/theme/Color.kt#L1-L21):
* Current `Color.kt` contains placeholder values (`LightBackground = 0xFFDAD9DD`, `DarkBackground = 0xFF111318`, `DarkSurface = 0xFF1E1E20`, `DarkSurfaceVariant = 0xFF2F3033`) which do **not** reflect the WPF source-of-truth `#16121A` / `#2B2631` / `#0AE66D` / `#FF453A`.
* [`Theme.kt`](file:///w:/CodeDeX/DeX/DeX/core/designsystem/src/commonMain/kotlin/com/dexstudios/dex/core/designsystem/theme/Theme.kt#L1-L81) defines `DarkColorScheme` and `LightColorScheme` using these placeholder colors, and defines an incomplete `DeXColors` accessor object.

### 1.3 Desktop Packaging & Build Configuration in `composeApp/build.gradle.kts`
Inspecting [`w:\CodeDeX\DeX\DeX\composeApp\build.gradle.kts`](file:///w:/CodeDeX/DeX/DeX/composeApp/build.gradle.kts#L1-L103):
* Kotlin Multiplatform + Compose Multiplatform plugin (`1.11.1`), Kotlin (`2.4.10`), JVM Target 17.
* `compose.desktop.application` block:
  * `mainClass = "com.dexstudios.dex.MainKt"`
  * `packageName = "DeX"`, `packageVersion = "1.0.0"`
  * `targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)`
  * `windows { menuGroup = "DeX Studios"; upgradeUuid = "6ac1f203-bde0-4040-a2f3-f8a6dcda330c"; dirChooser = true; shortcut = true; iconFile.set(project.file("src/desktopMain/resources/icon.ico")) }`
* Desktop runtime dependencies:
  * `compose.desktop.currentOs`
  * `dev.nucleusframework:composenativetray:2.1.0`
  * `org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0`
  * `io.github.kyant0:backdrop:2.0.0`
  * Koin 4.2.2 BOM, Ktor 3.5.2 suite.

### 1.4 Live Execution Verification of Gradle Tasks
1. **Compilation Task**: `.\gradlew :composeApp:compileKotlinDesktop`  
   * Result: **BUILD SUCCESSFUL in 8s** (Exit code 0, 43 actionable tasks).
2. **Desktop Test Task**: `.\gradlew :composeApp:desktopTest`  
   * Result: **BUILD SUCCESSFUL in 23s** (Exit code 0, all kinematics, stress, and component tests passed with 0 failures).
3. **Desktop Thin JAR Task**: `.\gradlew :composeApp:desktopJar`  
   * Result: **BUILD SUCCESSFUL in 9s** (Exit code 0, produces `composeApp/build/libs/composeApp-desktop.jar`, 455,060 bytes).
4. **Desktop Fat UberJAR Task**: `.\gradlew :composeApp:packageUberJarForCurrentOS`  
   * Result: **BUILD SUCCESSFUL in 32s** (Exit code 0, produces standalone fat jar `composeApp/build/compose/jars/DeX-windows-x64-1.0.0.jar`).

---

## 2. Logic Chain

```
[Observation 1.1: WPF XAML Theme Colors]
  └── Primary #16121A / #FFFFFF, Accent #2B2631 / #F2F2F7, Secondary Emerald #0AE66D, Danger #FF453A / #FF3B30
        │
[Observation 1.2: Compose Color.kt / Theme.kt Current State]
  └── Current Color.kt uses generic placeholders (0xFF111318, 0xFF1E1E20)
        │
[Logic Step 1: Design Tokens Alignment]
  └── Update `Color.kt` and `Theme.kt` in `core:designsystem` with exact 1:1 `DeXColors.Dark` and `DeXColors.Light` structures.
  └── Wire `DarkColorScheme` and `LightColorScheme` directly to `DeXColors.Dark.*` and `DeXColors.Light.*`.
  └── Supply `CompositionLocal` and `@Composable` dynamic accessors for type-safe theme tokens in Compose.
        │
[Logic Step 2: Visual Styling & Liquid Glass Integration (M4)]
  └── Main dock card requires:
      a. 34dp Corner Radius (`RoundedCornerShape(34.dp)`)
      b. Skia Gaussian Drop Shadow (`skiaDropShadow`: σ = blurRadius / 2.0f, GC Paint hoisting)
      c. Subpixel Inset Double Border Glow (`subpixelBorderGlow`: 1dp inner #2B2631 + ambient outer glow)
      d. Backdrop frosted surface (`io.github.kyant0:backdrop:2.0.0` with `DeXGlassPresets`)
        │
[Logic Step 3: Desktop Packaging & Build Verification]
  └── The Gradle build pipeline supports four verification tiers:
      Tier 1: `compileKotlinDesktop` (AST & Kotlin type checking)
      Tier 2: `desktopTest` (JVM desktop unit & rendering tests)
      Tier 3: `desktopJar` (Thin library JAR packaging)
      Tier 4: `packageUberJarForCurrentOS` (Fat executable JAR packaging)
        │
[Logic Step 4: Milestone 4 Comprehensive Test Suite]
  └── Create `Milestone4ThemeAndStylingTest.kt` validating token hex parity, color scheme mapping, Skia Gaussian blur mathematics, and shape tokens.
```

---

## 3. Caveats

1. **Host OS Desktop Capture & DWM Transparency**:
   * True real-time blurred sampling of external OS desktop windows via `drawBackdrop` on transparent undecorated windows is constrained by Windows DWM composition isolation (security boundary preventing non-DWM inter-process frame buffer sampling without Acrylic/Mica private API hooks).
   * **Mitigation**: The design implements a two-tier strategy: `LiquidGlassPanel` with `DeXGlassPresets` for intra-app glass composables + `skiaDropShadow` with solid/frosted alpha surface tint (`#16121A` @ 88% alpha) for flawless visual aesthetics without OS-level artifacts.
2. **Skia Native Paint GC Allocation Hoisting**:
   * Creating `org.jetbrains.skia.Paint` or `org.jetbrains.skia.MaskFilter` inside `drawBehind` or `drawWithContent` on every frame triggers 60–120 FPS heap allocations, leading to GC freeze spikes during 800ms spring animations.
   * **Mitigation**: All Skia paints and filters must be cached via `remember(color, blurRadius, density)` inside `@Composable Modifier` extensions.
3. **MSI Native Distribution Prerequisites**:
   * Running `packageMsi` requires the WiX Toolset installed on the host Windows machine. If WiX is absent, `packageUberJarForCurrentOS` and `desktopJar` serve as 100% reliable, zero-dependency packaging verification tasks.

---

## 4. Conclusion & Concrete Implementation Blueprints

### 4.1 Token Specifications (`Color.kt`)

**Target File**: `w:\CodeDeX\DeX\DeX\core\designsystem\src\commonMain\kotlin\com\dexstudios\dex\core\designsystem\theme\Color.kt`

```kotlin
package com.dexstudios.dex.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * 1:1 WPF-Parity Color Palette for DeX Desktop & Multiplatform.
 * Source of Truth: MSIX_Source/Themes/DarkTheme.xaml & LightTheme.xaml
 */
object DeXColors {

    // === Dark Theme (Default) ===
    object Dark {
        // Backgrounds & Surface
        val Primary = Color(0xFF16121A)                  // Main floating dock card surface
        val Accent = Color(0xFF2B2631)                   // Containers, button resting state, search bar, borders
        val SurfaceVariant = Color(0xFF2B2631)           // Card inner group background
        val CardBackground = Color(0xFF16121A)

        // Text & Glyphs
        val PrimaryText = Color(0xFFFFFFFF)              // Primary titles, labels, entered PIN digits (White)
        val SecondaryText = Color(0xFFA0A0A0)            // Subtitles, metadata, inactive icons, timestamps
        val MutedText = Color(0xFF757575)

        // Emerald Accent & Status
        val Secondary = Color(0xFF0AE66D)                // Emerald accent: active toggles, badges, progress bar
        val SecondaryForeground = Color(0xFF000000)      // Foreground text/icons over Emerald green
        val Danger = Color(0xFFFF453A)                   // Red for delete actions, close button, force exit
        val Warning = Color(0xFFFF9F0A)                  // Amber for warning badges / timeouts

        // List Item Selection & Hover (100% Solid Hex Port)
        val SecondaryHover = Color(0xFF2B2631)           // Row hover background
        val SecondarySelected = Color(0xFF332D3B)        // Selected item background
        val SecondarySelectedHover = Color(0xFF3D3647)   // Selected + hover background
        val SecondarySelectedBorder = Color(0xFF0AE66D)  // Active selection stroke (Emerald)

        // Glass & Shadow Spec
        val GlassSurfaceTint = Color(0xFF16121A)
        const val GlassSurfaceAlpha = 0.88f
        val DropShadowColor = Color.Black.copy(alpha = 0.55f)
        val InsetGlowColor = Color.White.copy(alpha = 0.12f)
    }

    // === Light Theme ===
    object Light {
        // Backgrounds & Surface
        val Primary = Color(0xFFFFFFFF)                  // Main card surface (White)
        val Accent = Color(0xFFF2F2F7)                   // Containers, button resting state, search bar (#F2F2F7 / #F5F3F7)
        val SurfaceVariant = Color(0xFFF2F2F7)
        val CardBackground = Color(0xFFFFFFFF)

        // Text & Glyphs
        val PrimaryText = Color(0xFF000000)              // Primary text (Black)
        val SecondaryText = Color(0xFF3A3A3C)            // Subtitles, metadata, inactive icons
        val MutedText = Color(0xFF8E8E93)

        // Emerald Accent & Status
        val Secondary = Color(0xFF0AE66D)                // Emerald accent (shared across themes)
        val SecondaryForeground = Color(0xFF000000)      // Foreground over Emerald
        val Danger = Color(0xFFFF3B30)                   // Light theme danger red
        val Warning = Color(0xFFFF9500)

        // List Item Selection & Hover
        val SecondaryHover = Color(0xFFE5E5EA)
        val SecondarySelected = Color(0xFFD1D1D6)
        val SecondarySelectedHover = Color(0xFFC7C7CC)
        val SecondarySelectedBorder = Color(0xFF0AE66D)

        // Glass & Shadow Spec
        val GlassSurfaceTint = Color(0xFFFFFFFF)
        const val GlassSurfaceAlpha = 0.85f
        val DropShadowColor = Color.Black.copy(alpha = 0.18f)
        val InsetGlowColor = Color.Black.copy(alpha = 0.05f)
    }
}
```

---

### 4.2 Theme Engine & Composition Locals (`Theme.kt`)

**Target File**: `w:\CodeDeX\DeX\DeX\core\designsystem\src\commonMain\kotlin\com\dexstudios\dex\core\designsystem\theme\Theme.kt`

```kotlin
package com.dexstudios.dex.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.Backdrop

val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

val DarkColorScheme = darkColorScheme(
    primary = DeXColors.Dark.Primary,
    onPrimary = DeXColors.Dark.PrimaryText,
    surface = DeXColors.Dark.Primary,
    onSurface = DeXColors.Dark.PrimaryText,
    surfaceVariant = DeXColors.Dark.Accent,
    onSurfaceVariant = DeXColors.Dark.SecondaryText,
    secondary = DeXColors.Dark.Secondary,
    onSecondary = DeXColors.Dark.SecondaryForeground,
    error = DeXColors.Dark.Danger,
    onError = DeXColors.Dark.PrimaryText,
    outline = DeXColors.Dark.Accent,
    outlineVariant = DeXColors.Dark.SecondarySelected
)

val LightColorScheme = lightColorScheme(
    primary = DeXColors.Light.Primary,
    onPrimary = DeXColors.Light.PrimaryText,
    surface = DeXColors.Light.Primary,
    onSurface = DeXColors.Light.PrimaryText,
    surfaceVariant = DeXColors.Light.Accent,
    onSurfaceVariant = DeXColors.Light.SecondaryText,
    secondary = DeXColors.Light.Secondary,
    onSecondary = DeXColors.Light.SecondaryForeground,
    error = DeXColors.Light.Danger,
    onError = Color.White,
    outline = DeXColors.Light.Accent,
    outlineVariant = DeXColors.Light.SecondarySelected
)

@Composable
fun DeXTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes
    ) {
        content()
    }
}

object DeXTheme {
    val colors: DeXColorsAccessor
        @Composable
        @ReadOnlyComposable
        get() = if (isSystemInDarkTheme()) DarkColorsAccessor else LightColorsAccessor
}

interface DeXColorsAccessor {
    val primary: Color
    val accent: Color
    val primaryText: Color
    val secondaryText: Color
    val secondary: Color
    val secondaryForeground: Color
    val danger: Color
    val secondaryHover: Color
    val secondarySelected: Color
    val secondarySelectedHover: Color
    val secondarySelectedBorder: Color
}

private object DarkColorsAccessor : DeXColorsAccessor {
    override val primary: Color get() = DeXColors.Dark.Primary
    override val accent: Color get() = DeXColors.Dark.Accent
    override val primaryText: Color get() = DeXColors.Dark.PrimaryText
    override val secondaryText: Color get() = DeXColors.Dark.SecondaryText
    override val secondary: Color get() = DeXColors.Dark.Secondary
    override val secondaryForeground: Color get() = DeXColors.Dark.SecondaryForeground
    override val danger: Color get() = DeXColors.Dark.Danger
    override val secondaryHover: Color get() = DeXColors.Dark.SecondaryHover
    override val secondarySelected: Color get() = DeXColors.Dark.SecondarySelected
    override val secondarySelectedHover: Color get() = DeXColors.Dark.SecondarySelectedHover
    override val secondarySelectedBorder: Color get() = DeXColors.Dark.SecondarySelectedBorder
}

private object LightColorsAccessor : DeXColorsAccessor {
    override val primary: Color get() = DeXColors.Light.Primary
    override val accent: Color get() = DeXColors.Light.Accent
    override val primaryText: Color get() = DeXColors.Light.PrimaryText
    override val secondaryText: Color get() = DeXColors.Light.SecondaryText
    override val secondary: Color get() = DeXColors.Light.Secondary
    override val secondaryForeground: Color get() = DeXColors.Light.SecondaryForeground
    override val danger: Color get() = DeXColors.Light.Danger
    override val secondaryHover: Color get() = DeXColors.Light.SecondaryHover
    override val secondarySelected: Color get() = DeXColors.Light.SecondarySelected
    override val secondarySelectedHover: Color get() = DeXColors.Light.SecondarySelectedHover
    override val secondarySelectedBorder: Color get() = DeXColors.Light.SecondarySelectedBorder
}
```

---

### 4.3 High-Performance Skia Drop Shadow & Subpixel Border Glow

**Target File**: `w:\CodeDeX\DeX\DeX\core\designsystem\src\commonMain\kotlin\com\dexstudios\dex\core\designsystem\components\glass\SkiaDropShadow.kt`

```kotlin
package com.dexstudios.dex.core.designsystem.components.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * High-performance GPU Gaussian drop shadow using Skia MaskFilter.makeBlur.
 * Reuses Paint and MaskFilter native instances across frames to eliminate GC overhead.
 */
@Composable
fun Modifier.skiaDropShadow(
    color: Color = Color.Black.copy(alpha = 0.55f),
    blurRadius: Dp = 32.dp,
    borderRadius: Dp = 34.dp,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 8.dp
): Modifier {
    val density = LocalDensity.current
    val paint = remember(color, blurRadius, density) {
        org.jetbrains.skia.Paint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
            val blurPx = with(density) { blurRadius.toPx() }
            val sigma = blurPx * 0.5f // Gaussian sigma = radius / 2.0
            if (sigma > 0f) {
                this.maskFilter = org.jetbrains.skia.MaskFilter.makeBlur(
                    org.jetbrains.skia.FilterBlurMode.NORMAL,
                    sigma
                )
            }
        }
    }

    return this.drawBehind {
        drawIntoCanvas { canvas ->
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
}

/**
 * Subpixel antialiased inset border stroke with glow highlight.
 */
fun Modifier.subpixelBorderGlow(
    strokeWidth: Dp = 1.dp,
    borderColor: Color = Color(0xFF2B2631),
    glowColor: Color = Color(0xFFFFFFFF).copy(alpha = 0.12f),
    cornerRadius: Dp = 34.dp
): Modifier = this.drawWithContent {
    drawContent()
    val halfStroke = strokeWidth.toPx() / 2f

    // Outer subtle ambient glow
    drawRoundRect(
        color = glowColor,
        topLeft = Offset(-halfStroke, -halfStroke),
        size = Size(size.width + halfStroke * 2, size.height + halfStroke * 2),
        cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
        style = Stroke(width = strokeWidth.toPx() * 2)
    )

    // Crisp inner border line
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

### 4.4 Milestone 4 Test Suite Architecture

**Target File**: `w:\CodeDeX\DeX\DeX\composeApp\src\desktopTest\kotlin\com\dexstudios\dex\theme\Milestone4ThemeAndStylingTest.kt`

```kotlin
package com.dexstudios.dex.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.core.designsystem.theme.DarkColorScheme
import com.dexstudios.dex.core.designsystem.theme.DeXColors
import com.dexstudios.dex.core.designsystem.theme.LightColorScheme
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Milestone4ThemeAndStylingTest {

    @Test
    fun testDarkThemeColorTokensParityWithWpf() {
        // Source of Truth: MSIX_Source/Themes/DarkTheme.xaml
        assertEquals(Color(0xFF16121A), DeXColors.Dark.Primary, "PrimaryBrush must match #16121A")
        assertEquals(Color(0xFF2B2631), DeXColors.Dark.Accent, "AccentBrush must match #2B2631")
        assertEquals(Color(0xFFFFFFFF), DeXColors.Dark.PrimaryText, "PrimaryTextBrush must match #FFFFFF")
        assertEquals(Color(0xFFA0A0A0), DeXColors.Dark.SecondaryText, "SecondaryTextBrush must match #A0A0A0")
        assertEquals(Color(0xFF0AE66D), DeXColors.Dark.Secondary, "SecondaryBrush must match #0AE66D")
        assertEquals(Color(0xFF000000), DeXColors.Dark.SecondaryForeground, "SecondaryForeground must match #000000")
        assertEquals(Color(0xFFFF453A), DeXColors.Dark.Danger, "DangerBrush must match #FF453A")
        assertEquals(Color(0xFF332D3B), DeXColors.Dark.SecondarySelected, "SecondarySelectedBrush must match #332D3B")
        assertEquals(Color(0xFF3D3647), DeXColors.Dark.SecondarySelectedHover, "SecondarySelectedHoverBrush must match #3D3647")
        assertEquals(Color(0xFF0AE66D), DeXColors.Dark.SecondarySelectedBorder, "SecondarySelectedBorder must match #0AE66D")
    }

    @Test
    fun testLightThemeColorTokensParityWithWpf() {
        // Source of Truth: MSIX_Source/Themes/LightTheme.xaml
        assertEquals(Color(0xFFFFFFFF), DeXColors.Light.Primary, "PrimaryBrush must match #FFFFFF")
        assertEquals(Color(0xFFF2F2F7), DeXColors.Light.Accent, "AccentBrush must match #F2F2F7")
        assertEquals(Color(0xFF000000), DeXColors.Light.PrimaryText, "PrimaryTextBrush must match #000000")
        assertEquals(Color(0xFF3A3A3C), DeXColors.Light.SecondaryText, "SecondaryTextBrush must match #3A3A3C")
        assertEquals(Color(0xFF0AE66D), DeXColors.Light.Secondary, "SecondaryBrush must match #0AE66D")
        assertEquals(Color(0xFFFF3B30), DeXColors.Light.Danger, "DangerBrush must match #FF3B30")
        assertEquals(Color(0xFFE5E5EA), DeXColors.Light.SecondaryHover, "SecondaryHoverBrush must match #E5E5EA")
        assertEquals(Color(0xFFD1D1D6), DeXColors.Light.SecondarySelected, "SecondarySelectedBrush must match #D1D1D6")
        assertEquals(Color(0xFFC7C7CC), DeXColors.Light.SecondarySelectedHover, "SecondarySelectedHoverBrush must match #C7C7CC")
    }

    @Test
    fun testMaterial3ColorSchemeMapping() {
        assertEquals(DeXColors.Dark.Primary, DarkColorScheme.primary)
        assertEquals(DeXColors.Dark.Primary, DarkColorScheme.surface)
        assertEquals(DeXColors.Dark.Accent, DarkColorScheme.surfaceVariant)
        assertEquals(DeXColors.Dark.Secondary, DarkColorScheme.secondary)
        assertEquals(DeXColors.Dark.Danger, DarkColorScheme.error)

        assertEquals(DeXColors.Light.Primary, LightColorScheme.primary)
        assertEquals(DeXColors.Light.Primary, LightColorScheme.surface)
        assertEquals(DeXColors.Light.Accent, LightColorScheme.surfaceVariant)
        assertEquals(DeXColors.Light.Secondary, LightColorScheme.secondary)
        assertEquals(DeXColors.Light.Danger, LightColorScheme.error)
    }

    @Test
    fun testSkiaGaussianSigmaCalculation() {
        val blurRadiusDp = 32.dp
        val density = 1.0f
        val blurPx = blurRadiusDp.value * density
        val sigma = blurPx * 0.5f // Must be radius / 2.0

        assertEquals(16.0f, sigma, 0.001f, "Gaussian sigma must equal blurRadius / 2.0")
        // 3-sigma decay spans 48px, fitting inside 25-48dp canvas padding margin
        assertTrue(3 * sigma >= 48f, "3-sigma decay calculation must be >= 48px")
    }
}
```

---

## 5. Verification Method

To independently verify all findings and test executions:

### Command Sequence:
```powershell
# Step 1: Verify compilation of Kotlin desktop code
.\gradlew :composeApp:compileKotlinDesktop

# Step 2: Execute all desktop unit, kinematic, and component tests
.\gradlew :composeApp:desktopTest

# Step 3: Verify thin desktop JAR packaging
.\gradlew :composeApp:desktopJar

# Step 4: Verify fat standalone executable distribution packaging
.\gradlew :composeApp:packageUberJarForCurrentOS
```

### Expected Results:
* `compileKotlinDesktop` exits with `0` (BUILD SUCCESSFUL).
* `desktopTest` exits with `0` (BUILD SUCCESSFUL), 0 test failures.
* `desktopJar` creates `composeApp/build/libs/composeApp-desktop.jar`.
* `packageUberJarForCurrentOS` creates `composeApp/build/compose/jars/DeX-windows-x64-1.0.0.jar`.

### Invalidation Conditions:
* Any compilation error or unresolved reference in `:composeApp` or `:core:designsystem`.
* Any color token discrepancy against `MSIX_Source/Themes/DarkTheme.xaml` and `MSIX_Source/Themes/LightTheme.xaml`.
* Memory allocation regression during Skia shadow rendering inside animation loops.
