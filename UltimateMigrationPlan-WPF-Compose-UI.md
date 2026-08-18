# Floating Dock Card UI — Compose Desktop Migration Plan

> # ⚠️ NON-NEGOTIABLE MIGRATION RULES — READ BEFORE ANY WORK ⚠️
>
> ## SCOPE: DESKTOP ONLY — WINDOWS AND macOS
> 1. **DESKTOP ONLY.** The legacy **WPF / C# / PowerShell** desktop implementation is being migrated to **Kotlin + Compose Multiplatform**. The Compose/Kotlin Multiplatform codebase is the desktop application for **Windows AND macOS** — both platforms run the **SAME shared Kotlin code**. That is the ONLY target.
> 2. **The Android app (`DeX/DeX/app`) is NOT part of this migration.** Never modify, refactor, rewire, or migrate it. It stays exactly as it is — it lives ONLY at `W:\CodeDeX\DeX\DeX` and never moves during archiving.
> 3. **No Android target may be added to the Compose desktop app** (`composeApp` is desktop-only: `desktopMain` + `commonMain`, no `androidMain`, no `androidTarget()`).
>
> ## HARD RULES — ZERO TOLERANCE, NO EXCEPTIONS
> 1. **NEVER delete, remove, archive, rename, or move ANY WPF / C# / PowerShell / legacy file** — not one file, not one line, not one asset — **for ANY reason**.
> 2. **NEVER** "tidy up", "fix", or "improve" legacy WPF/C#/PowerShell code on your own initiative.
> 3. **Only the USER may decide** when the legacy WPF/C#/PowerShell is archived. Until the user says so, it is untouchable.
> 4. **The ONLY archive procedure** (executed only when the user orders it): move the Compose/Kotlin Multiplatform code **UP one directory — from `W:\CodeDeX\DeX\DeX` to `W:\CodeDeX\DeX`**. The Android app stays at `W:\CodeDeX\DeX\DeX`. Nothing else is archived, removed, or restructured.
> 5. **If in doubt — STOP and ASK. Do not act.**
>
> **REPEAT: DESKTOP ONLY. Windows + macOS. ONE Kotlin/Compose Multiplatform codebase. Legacy WPF/C#/PowerShell stays untouched until the user says otherwise. Android stays at `W:\CodeDeX\DeX\DeX`.**

## Problem

The current Compose Desktop entry point ([main.kt](file:///w:/CodeDeX/DeX/composeApp/src/desktopMain/kotlin/com/dexstudios/dex/main.kt)) creates a **full decorated window** titled "DeX Workstation" — a standard desktop app with title bar, taskbar presence, and maximize/minimize/close buttons. This is fundamentally wrong for the desktop target.

The legacy WPF/PowerShell implementation ([MainWindow.xaml](file:///w:/CodeDeX/DeX/MSIX_Source/Themes/MainWindow.xaml)) creates a **floating dock card** that:
- Has **no window chrome** (no title bar, no borders)
- Is **transparent** (the rounded card floats over the desktop)
- Is **always on top** and **not in the taskbar**
- **Docks to the bottom-right** of the screen, above the Windows taskbar
- **Pops in/out** with scale+translate animation on tray icon click
- **Expands left+down** when opening File Explorer or Settings panels
- Has a **drag pill** for repositioning with double-click-to-reset
- **Hides on deactivation** (clicking outside closes the card)
- Lives in the **system tray** — the tray icon is the primary entry point
> [!IMPORTANT]
> 1. keep LOGICS/FUNCTIONS/ENGINES/COMPONENTS AND EVERYTHING(THAT CAN EXIST ON ITS OWN) MUST BE REFACTORED, MODULARIZE AND CENTRALIZE.
> 2. NOTHING MUST BE HARD CODED.
> 3. NEVER BREAK THE EXISTING FUNCTIONALITIES. INSTEAD OPTIMIZE THEM.
> 4. A STARTED TASK/PHASE/STEP MUST BE MARKED/ANNOTATED/DISPLAYED BESIDE ITS TITLE WITH EITHER: - Pending Task: Type - [ ] to show a task that is open. - Completed Task: Type - [x] to show a finished task with a check.
## User Review Required

> [!IMPORTANT]
> This plan replaces the `main.kt` Window configuration and requires a **new desktop-only composable layer** (`FloatingDockCard.kt`). `App.kt` is the DESKTOP app's entry composable (`composeApp` is desktop-only — no Android target). The Android app is a separate, standalone project at `W:\CodeDeX\DeX\DeX` and is NEVER modified by this plan.

> [!WARNING]  
> 2. **macOS behavior**: On macOS, the dock card should appear from the **menu bar tray icon** (top-right) adapt to macOS convention (top-right dropdown)


> [!IMPORTANT]
> **Q2: Deactivation Behavior**
>
> The WPF version hides the card when focus is lost (clicking outside). Compose Desktop's `Window.onFocusChanged` + AWT `WindowFocusListener` can replicate this.
> Should or add a "pin" mode (the toggle button already exists in the WPF XAML) that prevents auto-hide?

---

## WPF Architecture Decoded (Source of Truth)

Before proposing changes, here's exactly what the WPF floating card does, extracted from the source code:

### Window Properties ([MainWindow.xaml L1-7](file:///w:/CodeDeX/DeX/MSIX_Source/Themes/MainWindow.xaml#L1-L7))
```xml
WindowStyle="None"         <!-- No title bar, no borders -->
Background="Transparent"   <!-- See-through window -->
AllowsTransparency="True"  <!-- Enable transparency -->
Topmost="True"             <!-- Always on top -->
ShowInTaskbar="False"       <!-- Hidden from taskbar -->
Width="1420" Height="760"   <!-- Large canvas for expansion room -->
ResizeMode="NoResize"       <!-- Fixed size -->
```

### Content Layout ([MainWindow.xaml L25-1062](file:///w:/CodeDeX/DeX/MSIX_Source/Themes/MainWindow.xaml#L25-L1062))
```
┌──────────────────────────────────────────────────────────────────┐
│                    Transparent 1420×760 Window                   │
│                                                                  │
│                                                                  │
│                                  ┌──────────────────────────────┐│
│   ┌──────────────────────┐       │  mainBorder (dark card)      ││
│   │  FileExplorer        │       │  CornerRadius=34              ││
│   │  (collapsed by       │       │  Right-aligned               ││
│   │   default)           │       │  ┌─────────────────────────┐ ││
│   │                      │       │  │ Drag Pill               │ ││
│   │  OR                  │       │  │ Quick Action Buttons    │ ││
│   │                      │       │  │ Status Bar              │ ││
│   │  SettingsPanel       │       │  │ ─────────────────────── │ ││
│   │  (collapsed by       │       │  │ Discovered Devices      │ ││
│   │   default)           │       │  │ Your Devices            │ ││
│   │                      │       │  │ ─────────────────────── │ ││
│   └──────────────────────┘       │  │ 👤 Profile │ Exit Engine│ ││
│                                  │  └─────────────────────────┘ ││
│                                  └──────────────────────────────┘│
└──────────────────────────────────────────────────────────────────┘
```

### Positioning Logic ([Bindings_Tray.ps1 L46-58](file:///w:/CodeDeX/DeX/MSIX_Source/bin/Modules/Bindings_Tray.ps1#L46-L58))
```
left = workArea.Right - winWidth + 13      // Right edge of screen, with 13px gap
top  = workArea.Bottom - contentH - 38     // Above taskbar, with 38px gap
```

### Animation System ([AppStyles.xaml L281-291](file:///w:/CodeDeX/DeX/MSIX_Source/Themes/AppStyles.xaml#L281-L291))
- **PopIn**: ScaleX/Y 0.85→1.0, translateY 15→0, opacity 0→1 (500ms, ElasticEase)
- **ExpandMenu**: mainBorder width +754, height +195, FileExplorer slides from right (800ms, ElasticEase)
- **ContractMenu**: Reverse of expand with staggered fade-out (600ms, BackEase)
- **Nudge-ForExpand**: When expanding would push content off-screen, animates the window position in sync

### Interaction Model ([Bindings_Tray.ps1](file:///w:/CodeDeX/DeX/MSIX_Source/bin/Modules/Bindings_Tray.ps1), [Bindings_Window.ps1](file:///w:/CodeDeX/DeX/MSIX_Source/bin/Modules/Bindings_Window.ps1))
1. **Tray icon click** → Show/Hide toggle with PopIn/PopOut animation
2. **Click outside** → Window deactivates → Hide
3. **Drag pill** → WindowDraggableArea equivalent, repositions card
4. **Double-click drag pill** → Animate back to default bottom-right position
5. **Quick action buttons** → DND, Mirror, Transfers (File Explorer), Clipboard
6. **File Explorer button** → Expand card left, show file browser panel
7. **Profile avatar** → Expand card left, show settings panel
8. **Close (X) button** → Contract back to compact card

---

## Proposed Changes

### Desktop Window Layer (`desktopMain`)

---

#### [x] [`main.kt`](file:///w:/CodeDeX/DeX/composeApp/src/desktopMain/kotlin/com/dexstudios/dex/main.kt)

Complete rewrite to implement the floating dock card window:

```kotlin
fun main() = application {
    startKoin { modules(networkModule) }
    DeXServer.start()

    var isVisible by remember { mutableStateOf(false) }
    val trayState = rememberTrayState()

    // Calculate work area bounds (above taskbar)
    val workArea = remember { getWorkAreaBounds() }

    val windowState = rememberWindowState(
        size = DpSize(1420.dp, 760.dp),  // Large transparent canvas
        position = WindowPosition(
            x = (workArea.right - 1420 + 12).dp,
            y = (workArea.bottom - 430 - 38).dp  // Contracted height 430dp, resting 38px above taskbar
        )
    )

    Tray(
        icon = painterResource(Res.drawable.dex_logo),
        state = trayState,
        tooltip = "DeX",
        onAction = { isVisible = !isVisible },  // Toggle on click
        menu = {
            Item(if (isVisible) "Hide DeX" else "Show DeX", onClick = { isVisible = !isVisible })
            Separator()
            Item("Quit") { DeXServer.stop(); exitApplication() }
        }
    )

    if (isVisible) {
        Window(
            onCloseRequest = { isVisible = false },
            state = windowState,
            undecorated = true,
            transparent = true,
            alwaysOnTop = true,
            resizable = false,
            title = "DeX",
            onPreviewKeyEvent = { /* Ctrl+Q etc. */ }
        ) {
            // AWT-level: hide from taskbar, enable drag-and-drop
            LaunchedEffect(Unit) {
                window.type = java.awt.Window.Type.UTILITY  // No taskbar
                // macOS transparent title bar
                window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
                window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
                // Drag-and-drop target setup...
            }

            // Dismiss on focus loss (click outside) with complete deactivation guards
            DisposableEffect(Unit) {
                val listener = object : java.awt.event.WindowFocusListener {
                    override fun windowGainedFocus(e: java.awt.event.WindowEvent?) {}
                    override fun windowLostFocus(e: java.awt.event.WindowEvent?) {
                        // Guard against dismissal when pinned, in animation, pairing, drawer expanded, or modal dialog open
                        if (!isPinned && !isShowingTransition && !isPairingActive && !isExpanded && !isModalDialogOpen) {
                            isVisible = false
                        }
                    }
                }
                window.addWindowFocusListener(listener)
                onDispose { window.removeWindowFocusListener(listener) }
            }

            FloatingDockCard(
                onDismiss = { isVisible = false },
                onExitEngine = { DeXServer.stop(); exitApplication() }
            )
        }
    }
}
```

Key changes from current implementation:
- `undecorated = true` — No title bar
- `transparent = true` — Transparent window background
- `alwaysOnTop = true` — Always on top
- `window.type = UTILITY` — No taskbar entry (AWT equivalent of `ShowInTaskbar=False`)
- `WindowFocusListener` — Auto-hide on deactivation guarded by pin, transition, pairing, expanded drawer, and modal dialog states (matches WPF behavior)
- Tray click toggles visibility instead of always showing

---

#### [x] `FloatingDockCard.kt` — `desktopMain/kotlin/.../window/FloatingDockCard.kt`

The root composable for the desktop floating card layout. This replaces the full-screen `App()` call with the dock card structure:

```kotlin
@Composable
fun FloatingDockCard(
    onDismiss: () -> Unit,
    onExitEngine: () -> Unit
) {
    // Transparent canvas — clicks pass through transparent areas
    Box(modifier = Modifier.fillMaxSize()) {
        // The actual card, anchored to TopEnd within the canvas with internal margin padding
        DockCardContent(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 25.dp, end = 25.dp),
            onDismiss = onDismiss,
            onExitEngine = onExitEngine
        )
    }
}

@Composable
fun DockCardContent(
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onExitEngine: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var expandedPanel by remember { mutableStateOf<ExpandedPanel?>(null) }

    // Animated card dimensions (matches WPF: contracted ~300×500, expanded +754w +195h)
    val cardWidth by animateDpAsState(
        targetValue = if (isExpanded) 1054.dp else 300.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 300f)  // ElasticEase equivalent
    )
    val cardHeight by animateDpAsState(
        targetValue = if (isExpanded) 695.dp else 500.dp,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 300f)
    )

    Surface(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight),
        shape = RoundedCornerShape(34.dp),  // Matches WPF CornerRadius=34
        color = DeXTheme.colorScheme.surface,
        border = BorderStroke(1.dp, DeXTheme.colorScheme.outline)
    ) {
        Row {
            // Expanded panel (File Explorer or Settings) — slides in from right
            AnimatedVisibility(
                visible = isExpanded,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            ) {
                when (expandedPanel) {
                    ExpandedPanel.FileExplorer -> FileExplorerPanel(...)
                    ExpandedPanel.Settings -> SettingsPanel(...)
                    else -> {}
                }
            }

            // Main menu column (always visible, ~300dp wide)
            MainMenuColumn(
                onExpandFileExplorer = { expandedPanel = ExpandedPanel.FileExplorer; isExpanded = true },
                onExpandSettings = { expandedPanel = ExpandedPanel.Settings; isExpanded = true },
                onContract = { isExpanded = false; expandedPanel = null },
                isExpanded = isExpanded,
                onExitEngine = onExitEngine
            )
        }
    }
}
```

---

#### [x] `MainMenuColumn.kt` — `desktopMain/kotlin/.../window/MainMenuColumn.kt`

The always-visible right column of the dock card (the compact card from the screenshot):

```
┌─────────────────────────┐
│    ──── Drag Pill ────  │  ← WindowDraggableArea
│                         │
│  [X] [📱] [📁] [📋]   │  ← Quick Action Buttons
│                         │
│  Status: Ready          │  ← ADB Status (collapsible)
│  ───────────────────    │
│  Discovered Devices     │
│    📱 Nicholas Adima S21│  ← Dynamic UDP peers
│  Your Devices           │
│    🖥 DeXStudios        │  ← Paired devices
│  ───────────────────    │
│  👤 Profile | Exit ⌘Q  │  ← Bottom dock
└─────────────────────────┘
```

This composable reuses the **existing shared components** from `commonMain`:
- `DeviceListItem` for device entries
- Quick action icon buttons (DND, Mirror, File Explorer, Clipboard)
- Device discovery list from `MainScreenViewModel`

---

#### [x] `ScreenBoundsHelper.kt` — `desktopMain/kotlin/.../window/ScreenBoundsHelper.kt`

JVM/AWT utility to get screen work area (above taskbar):

```kotlin
data class WorkAreaBounds(val left: Int, val top: Int, val right: Int, val bottom: Int)

fun getWorkAreaBounds(): WorkAreaBounds {
    val ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
    val bounds = ge.maximumWindowBounds  // Excludes taskbar
    return WorkAreaBounds(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height)
}

fun getTaskbarInsets(): java.awt.Insets {
    val toolkit = java.awt.Toolkit.getDefaultToolkit()
    val gc = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
        .defaultScreenDevice.defaultConfiguration
    return toolkit.getScreenInsets(gc)
}
```

---

#### [x] `DockCardAnimations.kt` — `desktopMain/kotlin/.../window/DockCardAnimations.kt`

Ported animation specs from [AppStyles.xaml](file:///w:/CodeDeX/DeX/MSIX_Source/Themes/AppStyles.xaml#L111-L291):

| WPF Animation | Compose Equivalent |
|---|---|
| `ElasticEase(Oscillations=1, Springiness=7)` | `spring(dampingRatio=0.65f, stiffness=300f)` |
| `BackEase(Amplitude=3.53)` | `spring(dampingRatio=0.5f, stiffness=400f)` |
| `CubicEase(EaseOut)` | `tween(easing = FastOutSlowInEasing)` |
| PopIn scale 0.85→1.0 + translateY 15→0 (500ms) | `animateFloatAsState` + `graphicsLayer { scaleX; translationY }` |
| Expand mainBorder width +754 (800ms) | `animateDpAsState` with spring spec |
| Contract fade-out → size shrink (250ms + 600ms) | Staggered `LaunchedEffect` with `Animatable` |

---

### Shared UI Layer (`commonMain`)

---

#### [x] [`App.kt`](file:///w:/CodeDeX/DeX/composeApp/src/commonMain/kotlin/com/dexstudios/dex/App.kt)

`App()` is the **desktop-only** root composable of the CMP desktop app (`composeApp` has no Android target). Desktop's `main.kt` calls `App()` / `FloatingDockCard()` directly:

- Desktop uses the `FloatingDockCard` dock card layout exclusively
- The Android app is a **separate standalone project** at `W:\CodeDeX\DeX\DeX` with its own entry point — this plan NEVER touches it

---

### File Structure (New Files)

```
composeApp/src/desktopMain/kotlin/com/dexstudios/dex/
├── main.kt                          [x] Floating dock window config
└── window/
    ├── FloatingDockCard.kt          [x] Root dock card composable
    ├── MainMenuColumn.kt            [x] Compact card column (always visible)
    ├── FileExplorerPanel.kt         [x] Expanded file browser panel
    ├── SettingsPanel.kt             [x] Expanded settings panel  
    ├── QuickActionBar.kt            [x] DND/Mirror/Files/Clipboard buttons
    ├── DragPillHandle.kt            [x] Draggable pill + WindowDraggableArea
    ├── PinCodeView.kt               [x] PIN pairing view (animated slide-in)
    ├── DockCardAnimations.kt        [x] Ported animation specs
    └── ScreenBoundsHelper.kt        [x] JVM work area calculation
```

---

## WPF → Compose Behavior Mapping

| WPF Behavior | Source | Compose Desktop Implementation |
|---|---|---|
| `WindowStyle=None` | [MainWindow.xaml L4](file:///w:/CodeDeX/DeX/MSIX_Source/Themes/MainWindow.xaml#L4) | `Window(undecorated = true)` |
| `Background=Transparent, AllowsTransparency=True` | [MainWindow.xaml L4](file:///w:/CodeDeX/DeX/MSIX_Source/Themes/MainWindow.xaml#L4) | `Window(transparent = true)` |
| `Topmost=True` | [MainWindow.xaml L5](file:///w:/CodeDeX/DeX/MSIX_Source/Themes/MainWindow.xaml#L5) | `Window(alwaysOnTop = true)` |
| `ShowInTaskbar=False` | [MainWindow.xaml L5](file:///w:/CodeDeX/DeX/MSIX_Source/Themes/MainWindow.xaml#L5) | `window.type = Window.Type.UTILITY` (AWT) |
| Bottom-right positioning | [Bindings_Tray.ps1 L46-58](file:///w:/CodeDeX/DeX/MSIX_Source/bin/Modules/Bindings_Tray.ps1#L46-L58) | `getWorkAreaBounds()` + `WindowPosition(x, y)` |
| Tray icon show/hide | [Bindings_Tray.ps1 L2-96](file:///w:/CodeDeX/DeX/MSIX_Source/bin/Modules/Bindings_Tray.ps1#L2-L96) | `Tray(onAction = { isVisible = !isVisible })` |
| Click-outside dismiss | WPF `Deactivated` event | AWT `WindowFocusListener.windowLostFocus` |
| PopIn animation | [AppStyles.xaml L281-291](file:///w:/CodeDeX/DeX/MSIX_Source/Themes/AppStyles.xaml#L281-L291) | `spring()` + `graphicsLayer { scaleX; translationY }` |
| ExpandMenu animation | [AppStyles.xaml L115-151](file:///w:/CodeDeX/DeX/MSIX_Source/Themes/AppStyles.xaml#L115-L151) | `animateDpAsState` + `AnimatedVisibility(slideInHorizontally)` |
| ContractMenu animation | [AppStyles.xaml L152-197](file:///w:/CodeDeX/DeX/MSIX_Source/Themes/AppStyles.xaml#L152-L197) | `AnimatedVisibility(exit)` with staggered delay |
| Drag pill repositioning | [Bindings_Window.ps1 L262-304](file:///w:/CodeDeX/DeX/MSIX_Source/bin/Modules/Bindings_Window.ps1#L262-L304) | `WindowDraggableArea` composable |
| Double-click reset to default | [Bindings_Window.ps1 L264-301](file:///w:/CodeDeX/DeX/MSIX_Source/bin/Modules/Bindings_Window.ps1#L264-L301) | Update `windowState.position` with spring animation |
| Nudge-ForExpand | [UIComponents.ps1 L267-341](file:///w:/CodeDeX/DeX/MSIX_Source/bin/Modules/UIComponents.ps1#L267-L341) | No-op if using large transparent canvas approach |
| Wiggle-to-open | [Bindings_Wiggle.ps1](file:///w:/CodeDeX/DeX/MSIX_Source/bin/Modules/Bindings_Wiggle.ps1) | Global mouse hook via JNA — Phase 2 feature |
| System theme follow | [Connect-Engine.ps1 L289-300](file:///w:/CodeDeX/DeX/MSIX_Source/bin/Connect-Engine.ps1#L289-L300) | `java.util.prefs` or AWT `getSystemColor` + recompose |

---

## Verification Plan

### Automated Tests
```bash
# Build desktop target
./gradlew :composeApp:desktopJar

# Run desktop tests
./gradlew :composeApp:desktopTest

# Compose preview screenshots for dock card
./gradlew :composeApp:updateDebugScreenshotTest
```

### Manual Verification
1. **Launch** — Tray icon appears, no taskbar entry, no window visible initially
2. **Tray click** — Floating card pops in at bottom-right with scale animation
3. **Click outside** — Card dismisses
4. **Quick actions** — DND, Mirror, File Explorer, Clipboard buttons functional
5. **File Explorer expand** — Card smoothly expands left, file browser slides in
6. **Settings expand** — Card smoothly expands left, settings panel slides in
7. **Contract** — Close button collapses back to compact card
8. **Drag pill** — Card can be dragged to reposition
9. **Double-click pill** — Card snaps back to default bottom-right position
10. **Exit Engine** — Quits application cleanly
11. **Side-by-side comparison** — Run WPF app alongside, verify visual parity

---

# Part II: Deep Technical Specification & 1:1 Parity Implementation Guide

This section provides the comprehensive, authoritative, production-grade technical specification and implementation blueprint for achieving **1:1 visual, kinematic, and UX parity** with the legacy WPF/Win32 floating docked card interface using Compose Multiplatform Desktop (JVM / Skia).

---

## 1. Executive Architectural Blueprint

### 1.1 Architectural Comparison: WPF / Win32 vs Compose Multiplatform Desktop

The legacy WPF implementation relies on Win32 Desktop Window Manager (DWM) composition, WPF visual tree layout pipelines, and direct GDI/Win32 P/Invoke hooks. The Compose Multiplatform Desktop target replicates and exceeds this behavior using Skiko (Kotlin Multiplatform bindings for Skia), hardware-accelerated DirectComposition swapchains, and coroutine-driven physics.

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                  LEGACY WPF / WIN32 ARCHITECTURE                                 │
├──────────────────────────────────────────────────────────────────────────────────────────────────┤
│ • WindowStyle="None", Background="Transparent", AllowsTransparency="True", Topmost="True"        │
│ • DirectWrite & WPF MilCore rendering pipeline via DirectX 9Ex / Direct3D 11                    │
│ • Win32 P/Invoke: user32!GetCursorPos, user32!GetDpiForWindow, user32!SetForegroundWindow        │
│ • Storyboards: DoubleAnimation using ElasticEase(Springiness=7) & BackEase(Amplitude=3.53)      │
│ • Layout: System.Windows.Controls.Grid with right-aligned mainBorder inside 1420×760 canvas     │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
                                               ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                         COMPOSE MULTIPLATFORM (JVM / SKIA) ARCHITECTURE                          │
├──────────────────────────────────────────────────────────────────────────────────────────────────┤
│ • Window(undecorated = true, transparent = true, alwaysOnTop = true, resizable = false)          │
│ • Skiko SkiaLayer on DirectX 11/12 (Windows) / Metal (macOS) / OpenGL (Linux)                    │
│ • DirectComposition per-pixel alpha blending with DXGI_ALPHA_MODE_PREMULTIPLIED swapchain       │
│ • io.github.kyant0:backdrop (v2.0.0) real-time SkSL liquid blur, lens refraction, & vibrancy     │
│ • Coroutines & Compose Animation: spring(dampingRatio = 0.65f, stiffness = 300f), CubicBezier   │
│ • Multi-monitor DPI-aware TaskbarWorkAreaProvider via Java AWT GraphicsConfiguration & Insets   │
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### 1.2 Core Libraries & Dependency Ecosystem

To build the floating docked card interface with full LiquidGlass visual effects and native desktop integration, the following dependencies are specified:

```kotlin
// composeApp/build.gradle.kts
dependencies {
    // 1. Compose Multiplatform 1.8.x+ Core
    commonMainImplementation(compose.runtime)
    commonMainImplementation(compose.foundation)
    commonMainImplementation(compose.material3)
    commonMainImplementation(compose.ui)
    commonMainImplementation(compose.components.resources)

    // 2. Liquid Glass / Frosted Glass Backdrop Library (v2.0.0)
    // The official AndroidLiquidGlass / Compose Multiplatform Backdrop by Kyant
    commonMainImplementation("io.github.kyant0:backdrop:2.0.0")

    // 3. Dependency Injection & State Management
    commonMainImplementation("io.insert-koin:koin-core:4.0.0")
    commonMainImplementation("io.insert-koin:koin-compose:4.0.0")

    // 4. Desktop Native Tray Integration
    desktopMainImplementation("dev.nucleusframework.composenativetray:composenativetray:1.0.0")

    // 5. Native OS & JNA Interop (Multi-monitor, Taskbar Insets, Acrylic DWM)
    desktopMainImplementation("net.java.dev.jna:jna:5.14.0")
    desktopMainImplementation("net.java.dev.jna:jna-platform:5.14.0")
}
```

---

## 2. Window Shell, Docking & Geometry Mechanics

### 2.1 Window Properties Specification

In Compose Desktop, the root entry point must configure the `Window` composable to match the legacy WPF window characteristics:

```kotlin
Window(
    onCloseRequest = { windowController.hide() },
    visible = windowController.isVisible,
    state = windowController.windowState,
    undecorated = true,       // Strips OS title bar, system border chrome, and minimize/maximize buttons
    transparent = true,       // Enables per-pixel alpha transparency in Skiko DirectComposition swapchain
    alwaysOnTop = true,       // Keeps card floating above standard desktop applications (WPF Topmost=True)
    resizable = false,        // Disables OS resize handles and sizing borders
    title = "DeX"             // Application identifier for OS accessibility
) {
    // Docked Card Surface Tree
}
```

#### Taskbar Icon Suppression (`ShowInTaskbar = false`)
In Java AWT, a standard `JFrame` generates an entry on the Windows taskbar. Setting the native AWT window type to `UTILITY` eliminates the taskbar button:
```kotlin
LaunchedEffect(window) {
    window.type = java.awt.Window.Type.UTILITY
}
```

#### Focus Deactivation & Auto-Dismissal
When the user clicks outside the floating card, WPF fires `Window.Deactivated` to dismiss the menu. In Compose Desktop, an AWT `WindowFocusListener` reproduces this behavior with strict guards matching legacy WPF `Bindings_Window.ps1` (L592–601):
```kotlin
DisposableEffect(window) {
    val focusListener = object : java.awt.event.WindowFocusListener {
        override fun windowGainedFocus(e: java.awt.event.WindowEvent?) {}
        override fun windowLostFocus(e: java.awt.event.WindowEvent?) {
            // Strict 5-point deactivation guard matching legacy WPF parity:
            // 1. isPinned: User pinned card to screen
            // 2. isShowingTransition: Mid-animation entrance/exit
            // 3. isPairingActive: Active PIN/QR pairing session in progress
            // 4. isExpanded: File Explorer / Settings drawer is open (must not dismiss during external drag-and-drop)
            // 5. isModalDialogOpen: Native OS file/folder picker dialog currently has focus
            if (!windowController.isPinned &&
                !windowController.isShowingTransition &&
                !windowController.isPairingActive &&
                !windowController.isExpanded &&
                !windowController.isModalDialogOpen) {
                windowController.hide()
            }
        }
    }
    window.addWindowFocusListener(focusListener)
    onDispose { window.removeWindowFocusListener(focusListener) }
}
```

> **Critical Guard Rationale (Parity with WPF `Bindings_Window.ps1` L592–601):**
> 1. **Expanded Drawer Guard (`!isExpanded`)**: When File Explorer is expanded ($1054\text{ dp}$), users interact with external Windows Explorer windows or the desktop to drag-and-drop files. Bypassing this guard would instantly collapse the card on clicking the desktop.
> 2. **Modal Dialog Guard (`!isModalDialogOpen`)**: Summoning native OS file/folder pickers (`FileDialog` / `JFileChooser` / `FolderBrowserDialog`) temporarily transfers OS window focus away from the Compose window. Without this guard, opening a file picker causes the parent window to vanish behind the modal.

---

### 2.2 Taskbar-Aware Work Area Calculations

The Windows taskbar can reside at the Bottom, Top, Left, or Right edge of any monitor, and may be set to auto-hide. The usable work area $R_{\text{work}}$ represents the monitor space available after subtracting taskbar insets $I = (I_{\text{left}}, I_{\text{top}}, I_{\text{right}}, I_{\text{bottom}})$.

#### Mathematical Model
Let physical screen bounds in virtual desktop coordinates be $R_{\text{screen}} = (x_0, y_0, W_{\text{screen}}, H_{\text{screen}})$.
$$\begin{aligned}
x_{\text{work}} &= x_0 + I_{\text{left}} \\
y_{\text{work}} &= y_0 + I_{\text{top}} \\
W_{\text{work}} &= W_{\text{screen}} - I_{\text{left}} - I_{\text{right}} \\
H_{\text{work}} &= H_{\text{screen}} - I_{\text{top}} - I_{\text{bottom}} \\
\text{Right}_{\text{work}} &= x_{\text{work}} + W_{\text{work}} = x_0 + W_{\text{screen}} - I_{\text{right}} \\
\text{Bottom}_{\text{work}} &= y_{\text{work}} + H_{\text{work}} = y_0 + H_{\text{screen}} - I_{\text{bottom}}
\end{aligned}$$

#### Kotlin `TaskbarWorkAreaProvider` Implementation
```kotlin
package com.dexstudios.dex.window

import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit

data class WorkAreaBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val width: Int,
    val height: Int,
    val insets: Insets,
    val screenBounds: Rectangle
)

object TaskbarWorkAreaProvider {

    /**
     * Resolves the active screen work area based on the cursor position,
     * falling back to the primary display if cursor tracking is unavailable.
     */
    fun getActiveScreenWorkArea(): WorkAreaBounds {
        val ge = GraphicsEnvironment.getLocalGraphicsEnvironment()
        val defaultDevice = ge.defaultScreenDevice

        val mouseLocation: Point? = try {
            java.awt.MouseInfo.getPointerInfo()?.location
        } catch (_: Exception) {
            null
        }

        val targetDevice: GraphicsDevice = if (mouseLocation != null) {
            ge.screenDevices.firstOrNull { device ->
                device.defaultConfiguration.bounds.contains(mouseLocation)
            } ?: defaultDevice
        } else {
            defaultDevice
        }

        return getWorkAreaForDevice(targetDevice)
    }

    /**
     * Computes the taskbar-subtracted work area for a specific GraphicsDevice.
     */
    fun getWorkAreaForDevice(device: GraphicsDevice): WorkAreaBounds {
        val gc = device.defaultConfiguration
        val screenBounds = gc.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(gc)

        val left = screenBounds.x + insets.left
        val top = screenBounds.y + insets.top
        val right = screenBounds.x + screenBounds.width - insets.right
        val bottom = screenBounds.y + screenBounds.height - insets.bottom
        val width = right - left
        val height = bottom - top

        return WorkAreaBounds(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            width = width,
            height = height,
            insets = insets,
            screenBounds = screenBounds
        )
    }
}
```

---

### 2.3 Resting Position & Canvas Alignment Geometry

The card layout uses a fixed transparent bounding canvas of $W_{\text{canvas}} = 1420\text{ dp}$ and $H_{\text{canvas}} = 760\text{ dp}$. Inside this canvas, the card composable is strictly anchored to **`Alignment.TopEnd`** with an internal margin $M = 25\text{ dp}$ (`Modifier.padding(top = 25.dp, end = 25.dp)`), contracted width $W_{\text{card}} = 300\text{ dp}$, and contracted height $H_{\text{card}} = 430\text{ dp}$ (expandable to $625\text{ dp}$ / $695\text{ dp}$).

```
┌────────────────────────────────────────────────────────────────────────┐
│ Active Monitor Work Area (Right_work, Bottom_work)                    │
│                                                                        │
│                                  ┌───────────────────────────────────┐ │
│                                  │ Transparent Canvas (1420×760 dp)  │ │
│                                  │        [Alignment.TopEnd]         │ │
│                                  │        ┌────────────────────────┐ │ │
│                                  │        │ mainBorder Card        │ │ │
│                                  │        │ (300×430 dp contracted)│ │ │
│                                  │        │ Margin = 25 dp         │ │ │
│                                  │        └────────────────────────┘ │ │
│                                  │               13 dp gap ───►  │ │ │
│                                  └───────────────────────────────────┘ │
│ ══════════════════════════════════════════════════════════════════════ │
│ Taskbar Inset (Bottom_work)                               ▲ 38 dp gap  │
└────────────────────────────────────────────────────────────────────────┘
```

#### Exact Coordinate Equations:
1. **Target Content Position on Physical Screen:**
   $$X_{\text{content, right}} = \text{Right}_{\text{work}} - 13$$
   $$Y_{\text{content, top}} = \text{Bottom}_{\text{work}} - H_{\text{card}} - 13$$

2. **Compose Window Origin Coordinates ($X_{\text{window}}, Y_{\text{window}}$):**
   Inside the canvas, with `Alignment.TopEnd` and margin $M = 25\text{ dp}$:
   - Card Right inside canvas: $X_{\text{canvas, right}} = W_{\text{canvas}} - M = 1420 - 25 = 1395\text{ dp}$
   - Card Top inside canvas: $Y_{\text{canvas, top}} = M = 25\text{ dp}$
   - Card Bottom inside canvas: $Y_{\text{canvas, bottom}} = M + H_{\text{card}} = 25 + 430 = 455\text{ dp}$

   Setting screen content right to $\text{Right}_{\text{work}} - 13$:
   $$X_{\text{window}} + (W_{\text{canvas}} - M) = \text{Right}_{\text{work}} - 13 \implies X_{\text{window}} = \text{Right}_{\text{work}} - W_{\text{canvas}} + M - 13$$
   Setting screen content top to $\text{Bottom}_{\text{work}} - H_{\text{card}} - 13$ (or $38\text{ px}$ taskbar gap):
   $$Y_{\text{window}} + M = \text{Bottom}_{\text{work}} - H_{\text{card}} - 13 \implies Y_{\text{window}} = \text{Bottom}_{\text{work}} - H_{\text{card}} - M - 13$$

   Substituting $W_{\text{canvas}} = 1420\text{ dp}$, $M = 25\text{ dp}$, and $H_{\text{card}} = 430\text{ dp}$:
   $$X_{\text{window}} = \text{Right}_{\text{work}} - 1420 + 25 - 13 = \text{Right}_{\text{work}} - 1420 + 12$$
   $$Y_{\text{window}} = \text{Bottom}_{\text{work}} - 430 - 25 - 13 = \text{Bottom}_{\text{work}} - 430 - 38$$

#### Mathematical Proof of `TopEnd` vs `BottomEnd` Canvas Alignment:
- **With `Alignment.TopEnd`**:
  $$\text{Card Screen Bottom} = Y_{\text{window}} + Y_{\text{canvas, bottom}} = (\text{Bottom}_{\text{work}} - 468) + 455 = \text{Bottom}_{\text{work}} - 13\text{ px} \quad \text{[VERIFIED: Exactly 13px above taskbar]}$$
  When File Explorer expands by $+754\text{ dp}$ width and $+195\text{ dp}$ height, the card expands **leftward** (from canvas $X=1095$ to $X=341$) and **downward** (from canvas $Y=455$ to $Y=650$). It fits entirely within the $1420 \times 760\text{ dp}$ canvas without moving the OS window!
- **With `Alignment.BottomEnd` (Flawed Inversion)**:
  $$\text{Card Canvas Bottom} = H_{\text{canvas}} - M = 760 - 25 = 735\text{ dp}$$
  $$\text{Card Screen Bottom} = (\text{Bottom}_{\text{work}} - 468) + 735 = \text{Bottom}_{\text{work}} + 267\text{ px}$$
  This pushes the bottom half of the card **$267\text{ px}$ below the Windows taskbar** into non-visible space. Therefore, `Alignment.TopEnd` is mathematically mandatory.

---

### 2.4 Zero-Flicker State Transition Strategy: Fixed Canvas vs OS Window Resizing

| Criterion | Approach A: Dynamic OS Window Resizing | Approach B: Fixed Transparent Canvas + Compose Layout (Selected Standard) |
|---|---|---|
| **Underlying Mechanism** | Invokes Win32 `SetWindowPos` / AWT `window.setSize()` every frame (60–120 FPS). | Window canvas remains static ($1420 \times 760\text{ dp}$); Compose animates internal `Surface` `Modifier.width/height`. |
| **DirectX Swapchain** | Reallocates Direct3D swapchain buffers on every frame ($48\text{–}96$ allocations per expansion). | Allocated once at startup. Zero swapchain recreations during animations. |
| **Visual Artifacts** | Stutter, dropped frames, black/white rectangular flashing, DWM redraw clipping. | Locked 120 FPS GPU rendering, zero flicker, smooth alpha blend. |
| **Animation Subsystem** | Constrained to OS message pump timing; jitter between Win32 thread and Compose render tree. | Full Compose Animation subsystem: `spring(dampingRatio = 0.65f, stiffness = 300f)`, `IntOffset` parallax. |
| **Verdict** | ❌ **Rejected (Produces Severe Stutter & Visual Tearing)** | ✅ **Authoritative Architectural Standard** |

---

### 2.5 Dynamic Nudging Algorithm (`Nudge-ForExpand`)

When the user moves the floating card near the left or top boundary of the display, expanding the File Explorer ($+754\text{ dp}$ width, $+195\text{ dp}$ height) would push the panel off-screen. The `Nudge-ForExpand` algorithm calculates available directional margins and shifts the window origin synchronously with the expansion animation.

> **Critical Mathematical Requirement (Post-Expansion Boundary Evaluation):**
> Boundary sanity clamps MUST evaluate against the **target expanded dimensions** ($W_{\text{card}} + \Delta W = 1054\text{ dp}$, $H_{\text{card}} + \Delta H = 625\text{ dp}$) rather than resting unexpanded dimensions ($300 \times 430\text{ dp}$). Evaluating against unexpanded dimensions causes expanded panels on displays $\le 1024\text{ px}$ to clip off-screen by $43\text{ px}$ without triggering the clamp.

```kotlin
fun calculateExpansionNudge(
    currentWindowX: Int,
    currentWindowY: Int,
    cardWidth: Int = 300,
    cardHeight: Int = 430,
    expandDeltaWidth: Int,
    expandDeltaHeight: Int,
    workArea: WorkAreaBounds,
    canvasWidth: Int = 1420,
    margin: Int = 25
): Pair<Int, Int> {
    val contentLeft = currentWindowX + canvasWidth - margin - cardWidth
    val contentRight = currentWindowX + canvasWidth - margin
    val contentTop = currentWindowY + margin
    val contentBottom = contentTop + cardHeight

    val spaceLeft = contentLeft - workArea.left
    val spaceRight = workArea.right - contentRight
    val spaceUp = contentTop - workArea.top
    val spaceDown = workArea.bottom - contentBottom

    val canExpandLeft = spaceLeft >= (expandDeltaWidth + 20) || spaceLeft >= spaceRight
    val canExpandDown = spaceDown >= (expandDeltaHeight + 20) || spaceDown >= spaceUp

    var targetX = currentWindowX
    var targetY = currentWindowY

    if (!canExpandLeft) {
        targetX += (expandDeltaWidth - spaceLeft + 20).coerceAtLeast(expandDeltaWidth)
    }
    if (!canExpandDown) {
        targetY -= (expandDeltaHeight - spaceDown + 20).coerceAtLeast(expandDeltaHeight)
    }

    // Post-expansion boundary clamping: evaluate against target expanded dimensions
    val expW = cardWidth + expandDeltaWidth
    val expH = cardHeight + expandDeltaHeight
    val expLeft = targetX + canvasWidth - margin - expW
    val expRight = targetX + canvasWidth - margin
    val expTop = targetY + margin
    val expBottom = expTop + expH

    if (expLeft < workArea.left) targetX += (workArea.left - expLeft)
    if (expRight > workArea.right) targetX -= (expRight - workArea.right)
    if (expTop < workArea.top) targetY += (workArea.top - expTop)
    if (expBottom > workArea.bottom) targetY -= (expBottom - workArea.bottom)

    return Pair(targetX, targetY)
}
```

---

### 2.6 3-Phase Drag, High-DPI Scaling, Magnetic Snapping, Contraction Clamping & Double-Click Reset

```
[ Mouse Down on Drag Pill ]
            │
            ▼
┌───────────────────────────────┐
│ Phase 1: Pending Dead-Zone    │  Accumulate Manhattan delta: |Δx| + |Δy|
└──────────────┬────────────────┘
               │
       [ |Δx| + |Δy| ≥ 5px ? ]
        ├── NO  ──► [ Mouse Up: Trigger Click / Reset Accumulator ]
        └── YES ──► Lock drag baseline, fade drag pill accent (150ms)
               │
               ▼
┌───────────────────────────────┐
│ Phase 2: Active Drag Tracking │  Apply DPI scale: Δdp = Δpx / density
└──────────────┬────────────────┘  Evaluate 20px magnetic boundary snap
               │
               ▼
┌───────────────────────────────┐
│ Phase 3: Drag Release & Snap  │  Snapped to edge?
└──────────────┬────────────────┘   ├── YES ──► Settle animation (120ms CubicEase)
               │                    └── NO  ──► Off-screen clamp: max(cw * 0.2, 60px)
               ▼
   [ Final Window Coordinates ]
```

1. **Phase 1: Dead-Zone Filtering**: Drag gestures require a minimum Manhattan delta $|\Delta X| + |\Delta Y| \ge 5\text{ px}$ before moving the window, preventing accidental repositioning during double clicks.
2. **Phase 2: Active Drag & High-DPI Scaling**: AWT `MouseInfo.getPointerInfo().location` supplies physical display pixels, whereas Compose `WindowState.position` operates in density-independent pixels (`Dp`). Drag deltas MUST be scaled by display density $\rho = \text{DPI} / 96.0$ (`LocalDensity.current.density`):
   $$\Delta X_{\text{dp}} = \frac{\Delta X_{\text{physical}}}{\rho}, \quad \Delta Y_{\text{dp}} = \frac{\Delta Y_{\text{physical}}}{\rho}$$
   $$\text{candidateX} = \text{dragStartWindowX} + \Delta X_{\text{dp}}, \quad \text{candidateY} = \text{dragStartWindowY} + \Delta Y_{\text{dp}}$$
   Without this density division, the window moves $1.5\times$ (at 150% DPI) or $2.0\times$ (at 200% DPI) faster than the cursor, breaking 1:1 tactile tracking.
3. **Phase 3: 20px Magnetic Snapping**: When the card edge is within $\Delta_{\text{snap}} = 20\text{ px}$ of any work area boundary:
   - Left Snap: $|L_c - L_{\text{wa}}| < 20\text{ px} \implies L_c = L_{\text{wa}}$
   - Right Snap: $|R_c - R_{\text{wa}}| < 20\text{ px} \implies L_c = R_{\text{wa}} - W_{\text{card}}$
   - Top Snap: $|T_c - T_{\text{wa}}| < 20\text{ px} \implies T_c = T_{\text{wa}}$
   - Bottom Snap: $|B_c - B_{\text{wa}}| < 20\text{ px} \implies T_c = B_{\text{wa}} - H_{\text{card}}$
   - On release, snaps animate smoothly to edge over $120\text{ ms}$ with `CubicEase(EaseOut)`.
4. **Sanity Clamping (On Drag Release)**: Enforces that at least $\text{grab} = \max(W_{\text{card}} \times 0.2, 60\text{ px})$ remains reachable inside the monitor work area.
5. **Contraction Clamping (Void Prevention)**: When collapsing from expanded ($1054\text{ dp}$) to contracted ($300\text{ dp}$), if the user dragged the expanded card near the right edge of the screen, the right-anchored card without sanitization would jump to $X = X_{\text{win}} + W_{\text{canvas}} - M - W_{\text{contracted}}$—stranding the contracted card $544\text{ px}$ beyond the physical screen into an unreachable void. On contraction, the window origin is automatically clamped:
   $$c_{\text{contractedLeft}} = X_{\text{window}} + W_{\text{canvas}} - M - W_{\text{contracted}}$$
   $$\text{if } c_{\text{contractedLeft}} > \text{Right}_{\text{work}} - \text{grab} \implies X_{\text{window, safe}} = (\text{Right}_{\text{work}} - \text{grab}) - W_{\text{canvas}} + M + W_{\text{contracted}}$$
6. **Synchronized Double-Click Reset**: Double-clicking the drag pill (when unpinned) animates the window back to the resting dock position using an atomic 2D animation loop over $450\text{ ms}$ (`FastOutSlowInEasing`), eliminating diagonal visual tearing. If pinned, executes a 3-cycle shake animation ($\pm 5\text{ px}$ over $50\text{ ms}$ per cycle).

---

## 3. Liquid Glass & Visual Effects Architecture

### 3.1 `io.github.kyant0:backdrop` (v2.0.0) Implementation

The `io.github.kyant0:backdrop` library provides real-time GPU-accelerated frosted glass, refraction, lens distortion, and vibrancy.

#### Core Mechanics: Two-Layer Architecture
1. **LayerBackdrop**: Root layer capturing background composables into an off-screen GPU texture via `Modifier.layerBackdrop(backdrop)`.
2. **DrawBackdrop**: Foreground surface sampling the backdrop texture, executing the SkSL shader pipeline, and rendering ambient highlights/shadows via `Modifier.drawBackdrop(backdrop, ...)`.

```kotlin
package com.dexstudios.dex.core.designsystem.components.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

data class LiquidGlassConfig(
    val shape: RoundedCornerShape,
    val blurRadius: Dp,
    val lensHeight: Dp,
    val lensAmount: Dp,
    val vibrancyEnabled: Boolean = true,
    val chromaticAberration: Boolean = true,
    val depthEffect: Boolean = true,
    val restRefraction: Float = 0.25f,
    val surfaceTint: Color,
    val surfaceTintAlpha: Float,
    val highlight: Highlight,
    val shadowRadius: Dp,
    val shadowColor: Color,
    val innerShadow: InnerShadow? = null
)

object DeXGlassPresets {
    val DockCardDark = LiquidGlassConfig(
        shape = RoundedCornerShape(34.dp),
        blurRadius = 24.dp,
        lensHeight = 18.dp,
        lensAmount = 36.dp,
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
        surfaceTint = Color(0xFFFFFFFF),
        surfaceTintAlpha = 0.85f,
        highlight = Highlight.Ambient.copy(alpha = 0.6f),
        shadowRadius = 32.dp,
        shadowColor = Color.Black.copy(alpha = 0.18f),
        innerShadow = InnerShadow(radius = 4.dp, alpha = 0.08f)
    )

    val QuickActionDark = LiquidGlassConfig(
        shape = RoundedCornerShape(20.dp),
        blurRadius = 4.dp,
        lensHeight = 14.dp,
        lensAmount = 28.dp,
        surfaceTint = Color(0xFF2B2631),
        surfaceTintAlpha = 0.70f,
        highlight = Highlight.Default,
        shadowRadius = 8.dp,
        shadowColor = Color.Black.copy(alpha = 0.35f)
    )

    val QuickActionActive = LiquidGlassConfig(
        shape = RoundedCornerShape(20.dp),
        blurRadius = 6.dp,
        lensHeight = 16.dp,
        lensAmount = 32.dp,
        surfaceTint = Color(0xFF0AE66D),
        surfaceTintAlpha = 0.90f,
        highlight = Highlight.Ambient.copy(alpha = 0.8f),
        shadowRadius = 14.dp,
        shadowColor = Color(0xFF0AE66D).copy(alpha = 0.45f)
    )
}
```

---

### 3.2 Skia Fallback Shader Pipeline & Drop Shadow Rendering

When sampling pixels from the host OS desktop across transparent window boundaries (where OS security models restrict inter-process frame capture without native DWM composition), the architecture applies an **Adaptive Solid Frosted Fallback** with GPU Gaussian drop shadows.

> **Critical Shader & Skia Mathematics:**
> 1. **Gaussian Sigma ($\sigma$) vs Blur Radius**: Skia's `MaskFilter.makeBlur(FilterBlurMode.NORMAL, sigma)` accepts standard deviation $\sigma$, whereas WPF and CSS specify blur radius. In Gaussian kernel theory, $\sigma = \text{blurRadius} / 2.0\text{f}$. Passing `blurRadius.toPx()` directly doubles the standard deviation ($3\sigma = 72\text{ dp}$), creating an excessively diffuse shadow.
> 2. **GC Allocation Hoisting**: Creating `org.jetbrains.skia.Paint()` and `MaskFilter` inside `drawBehind` creates allocations on every frame (60–120 FPS), causing garbage collector spikes during $800\text{ ms}$ spring animations. Hoisting into a remembered `@Composable Modifier` or stateful modifier caches native Skia C++ objects across frames.
> 3. **Shadow Canvas Clearance**: For a $32\text{ dp}$ blur radius ($\sigma = 16\text{ dp}$), the Gaussian decay spans $3\sigma = 48\text{ dp}$. The transparent window margin padding ($25\text{ dp}$ to $48\text{ dp}$) guarantees zero rectangular border clipping.

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
    color: Color = Color.Black.copy(alpha = 0.45f),
    blurRadius: Dp = 24.dp,
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
 * Subpixel antialiased inset border stroke with glow highlight
 */
fun Modifier.subpixelBorderGlow(
    strokeWidth: Dp = 1.dp,
    borderColor: Color = Color(0xFF2B2631),
    glowColor: Color = Color(0xFFFFFFFF).copy(alpha = 0.15f),
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

## 4. State Machine & Kinematic Physics

### 4.1 State Dimension Matrix

| State | Card Width | Card Height | Left Panel | Close (X) Button | Drag Pill | Easing Curve | Duration |
|---|---|---|---|---|---|---|---|
| **Contracted (Default)** | $300\text{ dp}$ | $500\text{ dp}$ | `Collapsed` (Opacity 0) | `Collapsed` (Width 0) | Centered | Rest State | N/A |
| **File Explorer Expanded** | $1054\text{ dp}$ ($+754$) | $695\text{ dp}$ ($+195$) | `Visible` (`FileExplorer`) | `Visible` ($56\text{ dp}$) | Left-Aligned | `ElasticExpansionSpec` | $800\text{ ms}$ |
| **Settings Expanded** | $675\text{ dp}$ ($+375$) | $695\text{ dp}$ ($+195$) | `Visible` (`SettingsPanel`) | `Visible` ($56\text{ dp}$) | Left-Aligned | `ElasticExpansionSpec` | $800\text{ ms}$ |
| **Contracting** | $300\text{ dp}$ | $500\text{ dp}$ | Fade out ($250\text{ ms}$) | Collapse ($300\text{ ms}$) | Centered | `ContractEase` | $600\text{ ms}$ |
| **PIN/QR Pairing Modal** | $300\text{ dp}$ | $500\text{ dp}$ | `Collapsed` | `Collapsed` | Centered | `PowerEaseOut` | $250\text{ ms}$ |

---

### 4.2 Mathematical Kinematic Physics Port

```kotlin
package com.dexstudios.dex.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.Dp

object DockCardPhysics {

    // 1:1 Port of WPF ElasticEase(Oscillations=1, Springiness=7, EasingMode=EaseOut)
    val ElasticExpansionSpec = spring<Float>(
        dampingRatio = 0.65f,
        stiffness = 300f
    )
    val ElasticDpSpec = spring<Dp>(
        dampingRatio = 0.65f,
        stiffness = 300f
    )

    // 1:1 Port of WPF BackEase(Amplitude=3.53) - PopIn Entrance
    val PopInEase = Easing { fraction ->
        val t = fraction - 1f
        val a = 3.53f
        1f + t * t * ((a + 1f) * t + a)
    }

    // 1:1 Port of WPF BackEase(Amplitude=1.22) - Button Hover Micro-lift
    val HoverEase = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1.0f)

    // 1:1 Port of WPF BackEase(Amplitude=0.15) - Contract Menu Panel Shrink
    val ContractEase = Easing { fraction ->
        val t = fraction - 1f
        val a = 0.15f
        1f + t * t * ((a + 1f) * t + a)
    }
}
```

---

## 5. Tactile Quick Action Buttons

### 5.1 Quick Actions Toolbar Specification

The quick actions toolbar is a centered horizontal stack of 4 primary action pills plus a dynamic danger close pill:

```
┌─────────────────────────────────────────────────────────────┐
│  [  DND  ]   [ Mirror ]   [ Transfers ]   [ Clipboard ]   [X]│
│   (56×44)     (56×44)       (56×44)          (56×44)    (56×44)│
└─────────────────────────────────────────────────────────────┘
```

| Pill Identifier | Icon Glyph | Tooltip | Active Condition / Function |
|---|---|---|---|
| `btnQADnd` | Moon / Slash | "Do Not Disturb" | Toggles DND status; declines incoming connection handshakes. |
| `btnQAMirror` | Mobile Screen | "Mirror Phone" | Launches WebSocket screen streaming mirror window. |
| `btnQAPull` | Folder Arrow | "Transfers" | Toggles left-hand File Explorer panel expansion. |
| `btnQAClipboard` | Clipboard Sync | "Clipboard" | Toggles bidirectional clipboard text & image synchronization. |
| `btnCloseMenu` | Cross (X) | "Close" | Collapses expanded panel back to contracted state ($56\text{ dp} \to 0\text{ dp}$). |

#### Tactile Micro-Interactions:
- **Resting State**: Background `AccentBrush` (`#2B2631`), Icon `20sp` in `PrimaryTextBrush` (`#FFFFFF`).
- **Hover State (`MouseEnter`)**: Scale $1.0 \to 1.08\times$, translateY $0 \to -3\text{ dp}$ over $500\text{ ms}$ (`HoverEase`).
- **Press State (`MouseDown`)**: Scale $1.08 \to 0.85\times$, translateY $-3 \to +3\text{ dp}$ over $100\text{ ms}$ snappy sink.
- **Checked / Active State**: Background morphs to `SecondaryBrush` (`#0AE66D`), Icon color `#000000`.
- **Danger Pill (`btnCloseMenu`)**: Background `#FF453A` on hover, `#CCFF453A` on press.

---

### 5.2 Compose `DeXQuickActionButton` Implementation

```kotlin
package com.dexstudios.dex.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
import com.dexstudios.dex.core.designsystem.components.glass.skiaDropShadow
import com.dexstudios.dex.core.designsystem.theme.DockCardPhysics

@Composable
fun DeXQuickActionButton(
    icon: ImageVector,
    tooltip: String,
    isChecked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDanger: Boolean = false,
    badgeCount: Int = 0
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

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
                this.translationY = translateY.toPx()
            }
            .skiaDropShadow(
                color = if (isChecked) Color(0xFF0AE66D).copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.25f),
                blurRadius = if (isChecked) 12.dp else 6.dp,
                borderRadius = 20.dp
            )
            .background(backgroundColor, RoundedCornerShape(20.dp))
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
            // Contrast Inversion: Invert to dark container with white text and emerald border when checked
            val badgeBgColor = if (isChecked) Color(0xFF16121A) else Color(0xFF0AE66D)
            val badgeTextColor = if (isChecked) Color(0xFFFFFFFF) else Color(0xFF000000)
            val badgeBorder = if (isChecked) BorderStroke(1.dp, Color(0xFF0AE66D)) else null

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 4.dp)
                    .then(
                        if (badgeBorder != null) Modifier.border(badgeBorder, RoundedCornerShape(10.dp))
                        else Modifier
                    )
                    .background(badgeBgColor, RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badgeCount.toString(),
                    color = badgeTextColor,
                    fontSize = 9.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        }
    }
}
```

---

## 6. Embedded File Explorer & Directory Navigation

### 6.1 Architecture & Layout

The left-hand expansion panel `FileExplorerPanel` is structured in 3 vertical rows:

```
┌─────────────────────────────────────────────────────────────┐
│ [⬆] [ 🔍 Search transfers...           ] [📁 Mode] [Avatar] │ ← Row 0: Header Navigation
├─────────────────────────────────────────────────────────────┤
│ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ │
│ │ 📁 Doc1 │ │ 📁 Doc2 │ │ 🖼 Img1 │ │ 🎵 Aud1 │ │ 📄 Txt1 │ │ ← Row 1: LazyVerticalGrid
│ └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘ │   (100×105dp Item Cards)
├─────────────────────────────────────────────────────────────┤
│             [ ⬆ Send Files ]     [ 📁 Send Folders ]        │ ← Row 2: Action Docks &
│              [ ⬇ Saved to Downloads\DeX (Change) ]          │   Floating Pull Progress
│              [ ⏳ Pulling 3 of 10 files... [====] (X) ]      │
└─────────────────────────────────────────────────────────────┘
```

---

### 6.2 Header Controls (Row 0)
- **`btnUpDir`**: Circular button ($36\text{ dp}$, `CornerRadius=18dp`), navigates up one folder level (strips trailing `%2F` for SAF or directory path for Windows).
- **`txtSearch`**: Pill search input ($40\text{ dp}$ height, `CornerRadius=20dp`, background `#2B2631`) with real-time $150\text{ ms}$ debounced search filter.
- **`btnToggleExplorerMode`**: Circular toggle button switching between **Local Transfer History** (`Downloads\DeX`) and **Phone SAF Tree** (`content://...`).

---

### 6.3 File List Grid & Item Templates (Row 1)
- **Grid Layout**: `LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 100.dp))` with $8\text{ dp}$ inter-item spacing.
- **Item Cards ($100 \times 105\text{ dp}$)**:
  - Hover: Scale $1.05\times$, translateY $-2\text{ dp}$ ($300\text{ ms}$, `HoverEase`).
  - Press: Scale $0.94\times$ ($100\text{ ms}$).
  - Double-Click Protection: $400\text{ ms}$ timestamp delta filter preventing duplicate concurrent pull requests.
  - Thumbnails: $48 \times 48\text{ dp}$ image container with $4\text{ dp}$ rounded clip; fallback to file category glyph (`#A0A0A0`).

---

### 6.4 Footer Actions & Floating Progress Dock (Row 2)
- **Send Files / Send Folders**: Triggers native file/directory picker dialogs.
- **External Drag-and-Drop**: Registered via AWT `DropTarget` on the transparent canvas window, accepting external Windows Explorer drops (`DataFlavor.javaFileListFlavor`).
- **`PullProgressDock`**: Floating $360\text{ dp}$ bottom toast displaying file pull count, throughput speed, $4\text{ dp}$ emerald progress bar, and cancellation button.

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
                        imageVector = Icons.Default.Close,
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

## 7. Production Kotlin / Compose Implementation Reference

### 7.1 `DockedWindowStateController.kt`

```kotlin
package com.dexstudios.dex.window

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

enum class ExpandedPanel {
    FileExplorer,
    Settings,
    Pairing
}

class DockedWindowStateController(
    private val scope: CoroutineScope
) {
    val canvasWidth = 1420
    val canvasHeight = 760
    val cardMargin = 25

    var isVisible by mutableStateOf(false)
    var isPinned by mutableStateOf(false)
    var isShowingTransition by mutableStateOf(false)
    var hasBeenDragged by mutableStateOf(false)
    var isPairingActive by mutableStateOf(false)
    var isModalDialogOpen by mutableStateOf(false) // Guards focus loss during native OS file pickers

    var isExpanded by mutableStateOf(false)
    var expandedPanel by mutableStateOf<ExpandedPanel?>(null)

    val windowState = WindowState(
        size = DpSize(canvasWidth.dp, canvasHeight.dp),
        position = WindowPosition(0.dp, 0.dp)
    )

    private var preExpandX: Int? = null
    private var preExpandY: Int? = null

    // Drag tracking state
    private var dragPending = false
    private var isDragging = false
    private var dragStartCursorX = 0
    private var dragStartCursorY = 0
    private var dragStartWindowX = 0
    private var dragStartWindowY = 0

    init {
        recalculateDefaultDockPosition()
    }

    fun recalculateDefaultDockPosition() {
        val workArea = TaskbarWorkAreaProvider.getActiveScreenWorkArea()
        val defaultX = workArea.right - canvasWidth + 12
        val defaultY = workArea.bottom - 430 - 38
        windowState.position = WindowPosition(defaultX.dp, defaultY.dp)
    }

    fun show() {
        if (!hasBeenDragged) {
            recalculateDefaultDockPosition()
        }
        isVisible = true
    }

    fun hide() {
        isVisible = false
        if (isExpanded) {
            contractPanel()
        }
    }

    fun toggleVisibility() {
        if (isVisible) hide() else show()
    }

    fun expandPanel(panel: ExpandedPanel) {
        val workArea = TaskbarWorkAreaProvider.getActiveScreenWorkArea()
        val currentX = windowState.position.x.value.toInt()
        val currentY = windowState.position.y.value.toInt()

        if (preExpandX == null) preExpandX = currentX
        if (preExpandY == null) preExpandY = currentY

        val deltaW = when (panel) {
            ExpandedPanel.Settings -> 375
            ExpandedPanel.Pairing -> 100
            ExpandedPanel.FileExplorer -> 754
        }
        val deltaH = 195

        val (targetX, targetY) = calculateExpansionNudge(
            currentWindowX = currentX,
            currentWindowY = currentY,
            cardWidth = 300,
            cardHeight = 430,
            expandDeltaWidth = deltaW,
            expandDeltaHeight = deltaH,
            workArea = workArea
        )

        expandedPanel = panel
        isExpanded = true

        if (targetX != currentX || targetY != currentY) {
            scope.launch {
                animateWindowTo(targetX, targetY)
            }
        }
    }

    fun contractPanel() {
        isExpanded = false
        expandedPanel = null

        val restoreX = preExpandX
        val restoreY = preExpandY
        preExpandX = null
        preExpandY = null

        if (restoreX != null && restoreY != null) {
            scope.launch {
                animateWindowTo(restoreX, restoreY)
            }
        } else {
            // Contraction Clamping (Void Prevention):
            // If user dragged expanded card near right screen boundary, sanitize windowX so contracted card remains visible
            val workArea = TaskbarWorkAreaProvider.getActiveScreenWorkArea()
            val winX = windowState.position.x.value.toInt()
            val contractedCardW = 300
            val cRight = winX + canvasWidth - cardMargin
            val cContractedLeft = cRight - contractedCardW
            val grab = max((contractedCardW * 0.2f).toInt(), 60) // 60px
            if (cContractedLeft > workArea.right - grab) {
                val targetLeft = workArea.right - grab
                val safeWinX = targetLeft - canvasWidth + cardMargin + contractedCardW
                scope.launch {
                    animateWindowTo(safeWinX, windowState.position.y.value.toInt())
                }
            }
        }
    }

    fun onDragStart(cursorScreenX: Int, cursorScreenY: Int) {
        dragPending = true
        isDragging = false
        dragStartCursorX = cursorScreenX
        dragStartCursorY = cursorScreenY
        dragStartWindowX = windowState.position.x.value.toInt()
        dragStartWindowY = windowState.position.y.value.toInt()
    }

    fun onDragMove(cursorScreenX: Int, cursorScreenY: Int, density: Float = 1.0f) {
        val dxPhysical = cursorScreenX - dragStartCursorX
        val dyPhysical = cursorScreenY - dragStartCursorY

        if (dragPending && !isDragging) {
            if (abs(dxPhysical) + abs(dyPhysical) < 5) return // 5px deadzone threshold
            dragPending = false
            isDragging = true
            hasBeenDragged = true
            preExpandX = null
            preExpandY = null
        }

        if (isDragging) {
            // High-DPI scaling: convert physical mouse deltas to Dp units
            val dpDx = (dxPhysical / density).toInt()
            val dpDy = (dyPhysical / density).toInt()

            val workArea = TaskbarWorkAreaProvider.getActiveScreenWorkArea()
            var candidateX = dragStartWindowX + dpDx
            var candidateY = dragStartWindowY + dpDy

            val currentCardW = if (isExpanded) 1054 else 300
            val currentCardH = if (isExpanded) 625 else 430

            val contentLeft = candidateX + canvasWidth - cardMargin - currentCardW
            val contentTop = candidateY + cardMargin
            val contentRight = contentLeft + currentCardW
            val contentBottom = contentTop + currentCardH

            // 20px Magnetic Edge Snapping
            val snapThreshold = 20
            var finalLeft = contentLeft
            var finalTop = contentTop

            if (abs(contentLeft - workArea.left) < snapThreshold) finalLeft = workArea.left
            if (abs(contentRight - workArea.right) < snapThreshold) finalLeft = workArea.right - currentCardW
            if (abs(contentTop - workArea.top) < snapThreshold) finalTop = workArea.top
            if (abs(contentBottom - workArea.bottom) < snapThreshold) finalTop = workArea.bottom - currentCardH

            candidateX = finalLeft - canvasWidth + cardMargin + currentCardW
            candidateY = finalTop - cardMargin

            windowState.position = WindowPosition(candidateX.dp, candidateY.dp)
        }
    }

    fun onDragEnd() {
        if (isDragging) {
            val workArea = TaskbarWorkAreaProvider.getActiveScreenWorkArea()
            val winX = windowState.position.x.value.toInt()
            val winY = windowState.position.y.value.toInt()

            val currentCardW = if (isExpanded) 1054 else 300
            val currentCardH = if (isExpanded) 625 else 430

            val cLeft = winX + canvasWidth - cardMargin - currentCardW
            val cTop = winY + cardMargin
            val grab = max((currentCardW * 0.2f).toInt(), 60)

            var clampedLeft = cLeft
            var clampedTop = cTop

            if (cLeft + currentCardW < workArea.left + grab) clampedLeft = workArea.left + grab - currentCardW
            if (cLeft > workArea.right - grab) clampedLeft = workArea.right - grab
            if (cTop + currentCardH < workArea.top + grab) clampedTop = workArea.top + grab - currentCardH
            if (cTop > workArea.bottom - grab) clampedTop = workArea.bottom - grab

            val finalWinX = clampedLeft - canvasWidth + cardMargin + currentCardW
            val finalWinY = clampedTop - cardMargin

            windowState.position = WindowPosition(finalWinX.dp, finalWinY.dp)
        }
        dragPending = false
        isDragging = false
    }

    fun resetPositionToDefault() {
        if (!isPinned && hasBeenDragged) {
            val workArea = TaskbarWorkAreaProvider.getActiveScreenWorkArea()
            val targetX = workArea.right - canvasWidth + 12
            val targetY = workArea.bottom - 430 - 38

            scope.launch {
                animateWindowTo(targetX, targetY)
                hasBeenDragged = false
            }
        }
    }

    private suspend fun animateWindowTo(targetX: Int, targetY: Int) {
        val startX = windowState.position.x.value
        val startY = windowState.position.y.value
        val anim = Animatable(0f)

        // Single atomic 2D animation loop: eliminates concurrent coroutine race conditions and diagonal tearing
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
        ) {
            val curX = startX + (targetX - startX) * value
            val curY = startY + (targetY - startY) * value
            windowState.position = WindowPosition(curX.dp, curY.dp)
        }
    }
}
```

---

### 7.2 `FloatingDockCard.kt` (Root Composable)

```kotlin
package com.dexstudios.dex.window

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dexstudios.dex.core.designsystem.components.glass.skiaDropShadow
import com.dexstudios.dex.core.designsystem.components.glass.subpixelBorderGlow
import com.dexstudios.dex.core.designsystem.theme.DockCardPhysics

@Composable
fun FloatingDockCard(
    controller: DockedWindowStateController,
    onDismiss: () -> Unit,
    onExitEngine: () -> Unit
) {
    // 1420×760 Transparent Window Canvas
    Box(modifier = Modifier.fillMaxSize()) {
        val cardWidth by animateDpAsState(
            targetValue = when {
                !controller.isExpanded -> 300.dp
                controller.expandedPanel == ExpandedPanel.Settings -> 675.dp
                else -> 1054.dp
            },
            animationSpec = DockCardPhysics.ElasticDpSpec,
            label = "cardWidth"
        )

        val cardHeight by animateDpAsState(
            targetValue = if (controller.isExpanded) 625.dp else 430.dp,
            animationSpec = DockCardPhysics.ElasticDpSpec,
            label = "cardHeight"
        )

        // Card anchored strictly to Alignment.TopEnd with margin padding inside 1420×760dp canvas.
        // Expanding width grows leftward, expanding height grows downward within the canvas.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 25.dp, end = 25.dp)
                .width(cardWidth)
                .height(cardHeight)
                .skiaDropShadow(
                    color = Color.Black.copy(alpha = 0.55f),
                    blurRadius = 32.dp,
                    borderRadius = 34.dp
                )
                .background(Color(0xFF16121A).copy(alpha = 0.88f), RoundedCornerShape(34.dp))
                .subpixelBorderGlow(
                    strokeWidth = 1.dp,
                    borderColor = Color(0xFF2B2631),
                    glowColor = Color(0xFFFFFFFF).copy(alpha = 0.12f),
                    cornerRadius = 34.dp
                )
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Column: Expanded Panel (File Explorer or Settings)
                AnimatedVisibility(
                    visible = controller.isExpanded,
                    enter = slideInHorizontally(initialOffsetX = { 150 }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { 150 }) + fadeOut(),
                    modifier = Modifier.weight(1f)
                ) {
                    when (controller.expandedPanel) {
                        ExpandedPanel.FileExplorer -> {
                            FileExplorerPanel(
                                onContract = { controller.contractPanel() }
                            )
                        }
                        ExpandedPanel.Settings -> {
                            SettingsPanel(
                                onContract = { controller.contractPanel() }
                            )
                        }
                        else -> {}
                    }
                }

                // Right Column: Always-Visible Main Menu Column (300dp)
                MainMenuColumn(
                    controller = controller,
                    onExpandFileExplorer = { controller.expandPanel(ExpandedPanel.FileExplorer) },
                    onExpandSettings = { controller.expandPanel(ExpandedPanel.Settings) },
                    onContract = { controller.contractPanel() },
                    onExitEngine = onExitEngine,
                    modifier = Modifier.width(300.dp)
                )
            }
        }
    }
}
```

---

## 8. Complete Design Tokens Matrix (1:1 WPF Mapping)

### 8.1 Color Palette Tokens

```kotlin
package com.dexstudios.dex.core.designsystem.theme

import androidx.compose.ui.graphics.Color

object DeXColors {
    // === Dark Theme (Default) ===
    object Dark {
        val Primary = Color(0xFF16121A)                  // Main floating card background
        val Accent = Color(0xFF2B2631)                   // Containers, button resting state, search bar, borders
        val PrimaryText = Color(0xFFFFFFFF)              // Primary titles, labels, entered PIN digits
        val SecondaryText = Color(0xFFA0A0A0)            // Subtitles, metadata, inactive icons, timestamps
        val Secondary = Color(0xFF0AE66D)                // Emerald accent: active toggles, badges, progress bar
        val SecondaryForeground = Color(0xFF000000)      // Foreground text/icons over Secondary green
        val Danger = Color(0xFFFF453A)                   // Red for delete actions, close button, force exit
        
        // List Item Selection & Hover
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

### 8.2 Typography Hierarchy

| Typography Token | Font Family | Size | Weight | Line Height | Legacy WPF Counterpart |
|---|---|---|---|---|---|
| `TitleLarge` | Segoe UI / SansSerif | `18sp` | SemiBold (600) | `24sp` | Window / Modal Titles |
| `TitleMedium` | Segoe UI / SansSerif | `15sp` | Medium (500) | `20sp` | Device Alias / Section Headers |
| `BodyMedium` | Segoe UI / SansSerif | `13sp` | Normal (400) | `18sp` | List item subtext, telemetry |
| `BodySmall` | Segoe UI / SansSerif | `12sp` | Normal (400) | `16sp` | File Grid item names, metadata |
| `LabelSmall` | Segoe UI / SansSerif | `10sp` | SemiBold (600) | `14sp` | Badges, status pills |
| `MonospaceBadge` | Consolas / Monospace | `11sp` | Bold (700) | `14sp` | `⌘Q` Exit shortcut badge |
| `PinDisplay` | Segoe UI / SansSerif | `32sp` | Bold (700) | `40sp` | Real-time interactive PIN digits |

### 8.3 Shape & Elevation Tokens

```kotlin
object DeXShapes {
    val WindowCard = RoundedCornerShape(34.dp)         // Root floating dock card
    val QuickActionButton = RoundedCornerShape(20.dp)  // 56×44dp quick action pills
    val SearchPill = RoundedCornerShape(20.dp)         // Header search bar
    val ModalPanel = RoundedCornerShape(16.dp)         // Toast progress dock, inner groups
    val ListItem = RoundedCornerShape(12.dp)           // Device & settings row items
    val BadgePill = RoundedCornerShape(10.dp)          // Counter badges
    val GridItemCard = RoundedCornerShape(8.dp)        // 100×105dp file & folder cards
    val DragPill = RoundedCornerShape(2.dp)            // Top drag handle bar
}
```
