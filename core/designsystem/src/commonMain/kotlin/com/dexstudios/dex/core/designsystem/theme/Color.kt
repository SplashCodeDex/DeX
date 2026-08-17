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
        val Accent = Color(0xFFF2F2F7)                   // Containers, button resting state, search bar (#F2F2F7)
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
