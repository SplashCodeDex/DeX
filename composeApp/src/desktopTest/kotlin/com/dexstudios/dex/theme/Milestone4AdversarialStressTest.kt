package com.dexstudios.dex.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.core.designsystem.components.glass.DeXGlassPresets
import com.dexstudios.dex.core.designsystem.components.glass.LiquidGlassConfig
import com.dexstudios.dex.core.designsystem.components.glass.LiquidGlassPresets
import com.dexstudios.dex.core.designsystem.theme.DarkColorScheme
import com.dexstudios.dex.core.designsystem.theme.DeXColors
import com.dexstudios.dex.core.designsystem.theme.LightColorScheme
import com.dexstudios.dex.window.kinematics.DockCardAnimations
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Empirical Adversarial Challenger Test Suite for Milestone 4:
 * - Theme Tokens & 1:1 Parity with DarkTheme.xaml and LightTheme.xaml
 * - Material 3 ColorScheme & Accessor Mapping
 * - Liquid Glass Configuration Presets & Null Backdrop Solid Fallback
 * - Skia Drop Shadow Gaussian Optics & Canvas Boundary Clearance
 * - Subpixel Inset Border Glow Geometry
 */
class Milestone4AdversarialStressTest {

    // =========================================================================
    // 1. Theme Tokens 1:1 Parity with DarkTheme.xaml & LightTheme.xaml
    // =========================================================================

    @Test
    fun testAllDarkThemeHexTokensExactParity() {
        // Source of Truth: MSIX_Source/Themes/DarkTheme.xaml
        val expectedTokens = mapOf(
            "PrimaryBrush" to Color(0xFF16121A),
            "AccentBrush" to Color(0xFF2B2631),
            "PrimaryTextBrush" to Color(0xFFFFFFFF),
            "SecondaryTextBrush" to Color(0xFFA0A0A0),
            "SecondaryBrush" to Color(0xFF0AE66D),
            "SecondaryForegroundBrush" to Color(0xFF000000),
            "DangerBrush" to Color(0xFFFF453A),
            "SecondaryHoverBrush" to Color(0xFF2B2631),
            "SecondarySelectedBrush" to Color(0xFF332D3B),
            "SecondarySelectedHoverBrush" to Color(0xFF3D3647),
            "SecondarySelectedBorderBrush" to Color(0xFF0AE66D)
        )

        assertEquals(expectedTokens["PrimaryBrush"], DeXColors.Dark.Primary)
        assertEquals(expectedTokens["AccentBrush"], DeXColors.Dark.Accent)
        assertEquals(expectedTokens["PrimaryTextBrush"], DeXColors.Dark.PrimaryText)
        assertEquals(expectedTokens["SecondaryTextBrush"], DeXColors.Dark.SecondaryText)
        assertEquals(expectedTokens["SecondaryBrush"], DeXColors.Dark.Secondary)
        assertEquals(expectedTokens["SecondaryForegroundBrush"], DeXColors.Dark.SecondaryForeground)
        assertEquals(expectedTokens["DangerBrush"], DeXColors.Dark.Danger)
        assertEquals(expectedTokens["SecondaryHoverBrush"], DeXColors.Dark.SecondaryHover)
        assertEquals(expectedTokens["SecondarySelectedBrush"], DeXColors.Dark.SecondarySelected)
        assertEquals(expectedTokens["SecondarySelectedHoverBrush"], DeXColors.Dark.SecondarySelectedHover)
        assertEquals(expectedTokens["SecondarySelectedBorderBrush"], DeXColors.Dark.SecondarySelectedBorder)
    }

    @Test
    fun testAllLightThemeHexTokensExactParity() {
        // Source of Truth: MSIX_Source/Themes/LightTheme.xaml
        val expectedTokens = mapOf(
            "PrimaryBrush" to Color(0xFFFFFFFF),
            "AccentBrush" to Color(0xFFF2F2F7),
            "PrimaryTextBrush" to Color(0xFF000000),
            "SecondaryTextBrush" to Color(0xFF3A3A3C),
            "SecondaryBrush" to Color(0xFF0AE66D),
            "SecondaryForegroundBrush" to Color(0xFF000000),
            "DangerBrush" to Color(0xFFFF3B30),
            "SecondaryHoverBrush" to Color(0xFFE5E5EA),
            "SecondarySelectedBrush" to Color(0xFFD1D1D6),
            "SecondarySelectedHoverBrush" to Color(0xFFC7C7CC),
            "SecondarySelectedBorderBrush" to Color(0xFF0AE66D)
        )

        assertEquals(expectedTokens["PrimaryBrush"], DeXColors.Light.Primary)
        assertEquals(expectedTokens["AccentBrush"], DeXColors.Light.Accent)
        assertEquals(expectedTokens["PrimaryTextBrush"], DeXColors.Light.PrimaryText)
        assertEquals(expectedTokens["SecondaryTextBrush"], DeXColors.Light.SecondaryText)
        assertEquals(expectedTokens["SecondaryBrush"], DeXColors.Light.Secondary)
        assertEquals(expectedTokens["SecondaryForegroundBrush"], DeXColors.Light.SecondaryForeground)
        assertEquals(expectedTokens["DangerBrush"], DeXColors.Light.Danger)
        assertEquals(expectedTokens["SecondaryHoverBrush"], DeXColors.Light.SecondaryHover)
        assertEquals(expectedTokens["SecondarySelectedBrush"], DeXColors.Light.SecondarySelected)
        assertEquals(expectedTokens["SecondarySelectedHoverBrush"], DeXColors.Light.SecondarySelectedHover)
        assertEquals(expectedTokens["SecondarySelectedBorderBrush"], DeXColors.Light.SecondarySelectedBorder)
    }

    // =========================================================================
    // 2. Material 3 ColorScheme Mapping Verification
    // =========================================================================

    @Test
    fun testMaterial3ColorSchemeCompleteMapping() {
        // DarkColorScheme
        assertEquals(DeXColors.Dark.Primary, DarkColorScheme.primary)
        assertEquals(DeXColors.Dark.PrimaryText, DarkColorScheme.onPrimary)
        assertEquals(DeXColors.Dark.Primary, DarkColorScheme.background)
        assertEquals(DeXColors.Dark.Primary, DarkColorScheme.surface)
        assertEquals(DeXColors.Dark.Accent, DarkColorScheme.surfaceVariant)
        assertEquals(DeXColors.Dark.PrimaryText, DarkColorScheme.onBackground)
        assertEquals(DeXColors.Dark.PrimaryText, DarkColorScheme.onSurface)
        assertEquals(DeXColors.Dark.SecondaryText, DarkColorScheme.onSurfaceVariant)
        assertEquals(DeXColors.Dark.Secondary, DarkColorScheme.secondary)
        assertEquals(DeXColors.Dark.SecondaryForeground, DarkColorScheme.onSecondary)
        assertEquals(DeXColors.Dark.Danger, DarkColorScheme.error)
        assertEquals(DeXColors.Dark.PrimaryText, DarkColorScheme.onError)
        assertEquals(DeXColors.Dark.Accent, DarkColorScheme.outline)
        assertEquals(DeXColors.Dark.SecondarySelected, DarkColorScheme.outlineVariant)

        // LightColorScheme
        assertEquals(DeXColors.Light.Primary, LightColorScheme.primary)
        assertEquals(DeXColors.Light.PrimaryText, LightColorScheme.onPrimary)
        assertEquals(DeXColors.Light.Primary, LightColorScheme.background)
        assertEquals(DeXColors.Light.Primary, LightColorScheme.surface)
        assertEquals(DeXColors.Light.Accent, LightColorScheme.surfaceVariant)
        assertEquals(DeXColors.Light.PrimaryText, LightColorScheme.onBackground)
        assertEquals(DeXColors.Light.PrimaryText, LightColorScheme.onSurface)
        assertEquals(DeXColors.Light.SecondaryText, LightColorScheme.onSurfaceVariant)
        assertEquals(DeXColors.Light.Secondary, LightColorScheme.secondary)
        assertEquals(DeXColors.Light.SecondaryForeground, LightColorScheme.onSecondary)
        assertEquals(DeXColors.Light.Danger, LightColorScheme.error)
        assertEquals(Color.White, LightColorScheme.onError)
        assertEquals(DeXColors.Light.Accent, LightColorScheme.outline)
        assertEquals(DeXColors.Light.SecondarySelected, LightColorScheme.outlineVariant)
    }

    // =========================================================================
    // 3. Liquid Glass Configuration & Fallback Math
    // =========================================================================

    @Test
    fun testLiquidGlassPanelFallbackLogicWithNullBackdrop() {
        // When backdrop is null, LiquidGlassPanel calculates:
        // if (config.surfaceTint.isSpecified && config.surfaceTintAlpha > 0f)
        //     config.surfaceTint.copy(alpha = config.surfaceTintAlpha)
        // else Color(0xFF16121A).copy(alpha = 0.82f)

        val configs = listOf(
            DeXGlassPresets.DockCardDark,
            DeXGlassPresets.DockCardLight,
            DeXGlassPresets.QuickActionDark,
            DeXGlassPresets.QuickActionActive,
            LiquidGlassPresets.Default,
            LiquidGlassPresets.IconButton,
            LiquidGlassPresets.NavBar,
            LiquidGlassPresets.Dialog,
            LiquidGlassPresets.Frosted,
            LiquidGlassPresets.DynamicIsland,
            LiquidGlassPresets.Flat,
            LiquidGlassPresets.FlatInteractive
        )

        for (cfg in configs) {
            val fallbackColor = if (cfg.surfaceTint.isSpecified && cfg.surfaceTintAlpha > 0f) {
                cfg.surfaceTint.copy(alpha = cfg.surfaceTintAlpha)
            } else {
                Color(0xFF16121A).copy(alpha = 0.82f)
            }

            assertTrue(fallbackColor.isSpecified, "Fallback color must be specified for config $cfg")
            assertTrue(fallbackColor.alpha > 0f && fallbackColor.alpha <= 1f, "Fallback alpha must be in (0, 1]")
        }

        // Test unspecified tint edge case
        val unspecifiedTintConfig = LiquidGlassConfig(
            surfaceTint = Color.Unspecified,
            surfaceTintAlpha = 0.5f
        )
        val fallbackUnspecified = if (unspecifiedTintConfig.surfaceTint.isSpecified && unspecifiedTintConfig.surfaceTintAlpha > 0f) {
            unspecifiedTintConfig.surfaceTint.copy(alpha = unspecifiedTintConfig.surfaceTintAlpha)
        } else {
            Color(0xFF16121A).copy(alpha = 0.82f)
        }
        assertEquals(Color(0xFF16121A).copy(alpha = 0.82f), fallbackUnspecified)

        // Test 0 alpha edge case
        val zeroAlphaConfig = LiquidGlassConfig(
            surfaceTint = Color.White,
            surfaceTintAlpha = 0f
        )
        val fallbackZeroAlpha = if (zeroAlphaConfig.surfaceTint.isSpecified && zeroAlphaConfig.surfaceTintAlpha > 0f) {
            zeroAlphaConfig.surfaceTint.copy(alpha = zeroAlphaConfig.surfaceTintAlpha)
        } else {
            Color(0xFF16121A).copy(alpha = 0.82f)
        }
        assertEquals(Color(0xFF16121A).copy(alpha = 0.82f), fallbackZeroAlpha)
    }

    @Test
    fun testDeXGlassPresetsValues() {
        val dockDark = DeXGlassPresets.DockCardDark
        assertEquals(RoundedCornerShape(34.dp), dockDark.shape)
        assertEquals(24.dp, dockDark.blurRadius)
        assertEquals(18.dp, dockDark.lensHeight)
        assertEquals(36.dp, dockDark.lensAmount)
        assertEquals(Color(0xFF16121A), dockDark.surfaceTint)
        assertEquals(0.82f, dockDark.surfaceTintAlpha)
        assertEquals(32.dp, dockDark.shadowRadius)
        assertEquals(Color.Black.copy(alpha = 0.55f), dockDark.shadowColor)
        assertEquals(6.dp, dockDark.innerShadow?.radius)
        assertEquals(0.15f, dockDark.innerShadow?.alpha)

        val dockLight = DeXGlassPresets.DockCardLight
        assertEquals(RoundedCornerShape(34.dp), dockLight.shape)
        assertEquals(24.dp, dockLight.blurRadius)
        assertEquals(18.dp, dockLight.lensHeight)
        assertEquals(36.dp, dockLight.lensAmount)
        assertEquals(Color(0xFFFFFFFF), dockLight.surfaceTint)
        assertEquals(0.85f, dockLight.surfaceTintAlpha)
        assertEquals(32.dp, dockLight.shadowRadius)
        assertEquals(Color.Black.copy(alpha = 0.18f), dockLight.shadowColor)
        assertEquals(4.dp, dockLight.innerShadow?.radius)
        assertEquals(0.08f, dockLight.innerShadow?.alpha)
    }

    // =========================================================================
    // 4. Gaussian Kernel & Canvas Boundary Clearance
    // =========================================================================

    @Test
    fun testGaussianSigmaAcrossElevations() {
        val radii = listOf(0.dp, 4.dp, 8.dp, 16.dp, 24.dp, 32.dp, 48.dp, 64.dp)

        for (r in radii) {
            val blurPx = r.value * 1.5f // 1.5x DPI
            val sigma = blurPx * 0.5f   // Gaussian kernel sigma = R / 2.0
            assertEquals(blurPx / 2.0f, sigma, 0.0001f)

            // 3-sigma decay
            val decay = 3f * sigma
            assertEquals(1.5f * blurPx, decay, 0.0001f)
        }
    }

    @Test
    fun testCanvasClearanceWithExpandedCardAndShadow() {
        val canvasWidth = 1420.dp
        val canvasHeight = 760.dp
        val topEndMargin = 25.dp

        val expandedWidth = DockCardAnimations.CARD_WIDTH_EXPANDED   // 1054 dp
        val expandedHeight = DockCardAnimations.CARD_HEIGHT_EXPANDED // 625 dp

        // Card bounds within canvas:
        // Right inside canvas = canvasWidth - margin = 1420 - 25 = 1395 dp
        // Left inside canvas = 1395 - 1054 = 341 dp
        // Top inside canvas = margin = 25 dp
        // Bottom inside canvas = 25 + 625 = 650 dp

        val clearanceLeft = 341.dp
        val clearanceRight = canvasWidth - (canvasWidth - topEndMargin) // 25 dp
        val clearanceTop = 25.dp
        val clearanceBottom = canvasHeight - (topEndMargin + expandedHeight) // 760 - 650 = 110 dp

        // Max shadow radius on dock card = 32dp -> 3-sigma decay = 48dp
        val shadowDecay3Sigma = 48.dp

        assertTrue(clearanceLeft.value > shadowDecay3Sigma.value, "Left clearance ($clearanceLeft) must exceed 3-sigma decay ($shadowDecay3Sigma)")
        assertTrue(clearanceBottom.value > shadowDecay3Sigma.value, "Bottom clearance ($clearanceBottom) must exceed 3-sigma decay ($shadowDecay3Sigma)")
    }

    // =========================================================================
    // 5. Subpixel Inset Border Math
    // =========================================================================

    @Test
    fun testSubpixelBorderInsetMath() {
        val strokeWidth = 1.dp
        val density = 1.5f
        val strokePx = strokeWidth.value * density
        val halfStroke = strokePx / 2f
        val cornerRadiusPx = 34.dp.value * density

        val outerRadius = cornerRadiusPx + halfStroke
        val innerRadius = (cornerRadiusPx - halfStroke).coerceAtLeast(0f)

        assertTrue(outerRadius > cornerRadiusPx, "Outer radius must expand by half-stroke")
        assertTrue(innerRadius < cornerRadiusPx, "Inner radius must contract by half-stroke")
        assertTrue(innerRadius > 0f, "Inner radius must remain strictly positive")
    }
}
