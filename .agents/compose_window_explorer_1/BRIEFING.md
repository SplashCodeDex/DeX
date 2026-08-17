# BRIEFING — 2026-08-16T22:33:15Z

## Mission
Investigate and design the exact Compose Multiplatform (Desktop) windowing architecture required to achieve 1:1 parity with WPF's floating docked card window mechanics.

## 🔒 My Identity
- Archetype: explorer
- Roles: Compose Desktop Window & Docking Architect
- Working directory: W:\CodeDeX\DeX\.agents\compose_window_explorer_1
- Original parent: 71be086e-88e4-425a-b8cf-e15f26cd7dc3
- Milestone: Compose Desktop Floating Window Architecture

## 🔒 Key Constraints
- Read-only investigation — do NOT implement
- 1:1 parity with legacy WPF / Win32 floating card UI/UX
- Zero bloat, strict ponytail execution

## Current Parent
- Conversation ID: 71be086e-88e4-425a-b8cf-e15f26cd7dc3
- Updated: 2026-08-16T22:33:15Z

## Investigation State
- **Explored paths**:
  - `MSIX_Source/Themes/MainWindow.xaml`
  - `MSIX_Source/Themes/AppStyles.xaml`
  - `MSIX_Source/bin/Connect-Engine.ps1`
  - `MSIX_Source/bin/TrayUIHandlers.ps1`
  - `MSIX_Source/bin/Modules/Bindings_Window.ps1`
  - `MSIX_Source/bin/Modules/Bindings_Tray.ps1`
  - `MSIX_Source/bin/Modules/UIComponents.ps1`
  - `DeX/composeApp/src/desktopMain/kotlin/com/dexstudios/dex/main.kt`
  - `DeX/composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/ScreenBoundsHelper.kt`
  - `DeX/composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/FloatingDockCard.kt`
  - `DeX/composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/DockCardAnimations.kt`
- **Key findings**:
  - WPF uses a fixed $1420 \times 760\text{ px}$ transparent canvas (`WindowStyle="None"`, `Background="Transparent"`, `AllowsTransparency="True"`, `Topmost="True"`, `ShowInTaskbar="False"`) rather than dynamically resizing the native OS window.
  - In Compose Desktop with Skiko on Windows DWM, fixed canvas layout animation eliminates Direct3D swapchain recreation and eliminates resize stutter/flicker.
  - Docked positioning uses taskbar insets via AWT `Toolkit.getDefaultToolkit().getScreenInsets(gc)` to anchor bottom-right with $13\text{ dp}$ margin.
  - Dynamic `Nudge-ForExpand` animates window position when expanding near screen edges.
  - Dragging requires 3-phase handling ($5\text{ px}$ dead zone, $20\text{ px}$ magnetic snap, $120\text{ ms}$ snap ease-out, $60\text{ px}$ sanity clamping, and double-click reset to default).
- **Unexplored areas**: None for this windowing investigation.

## Key Decisions Made
- Recommended Approach B (Fixed Transparent Canvas with Compose-level Spring Layout Animations) as the standard for Compose Desktop over Approach A (OS Window Resizing).
- Created `TaskbarWorkAreaProvider` and `DockedWindowStateController` architecture designs.

## Artifact Index
- `W:\CodeDeX\DeX\.agents\compose_window_explorer_1\analysis.md` — Full Architecture Report & Specification
- `W:\CodeDeX\DeX\.agents\compose_window_explorer_1\handoff.md` — 5-Component Handoff Report
