# Project: DeX Floating Docked Card UI (Compose Multiplatform Desktop)

> # ⚠️ NON-NEGOTIABLE MIGRATION RULES — READ BEFORE ANY WORK ⚠️
>
> ## SCOPE: DESKTOP ONLY — WINDOWS AND macOS
> 1. **DESKTOP ONLY.** The legacy **WPF / C# / PowerShell** desktop implementation is being migrated to **Kotlin + Compose Multiplatform**. The Compose/Kotlin Multiplatform codebase is the desktop application for **Windows AND macOS** — both platforms run the **SAME shared Kotlin code**. That is the ONLY target.
> 2. **The Android app (`DeX/app`) is NOT part of this migration.** Never modify, refactor, rewire, or migrate it. It stays exactly as it is — it lives ONLY at `W:\CodeDeX\DeX\DeX` and never moves during archiving.
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

## Architecture
DeX Desktop is a modern Compose Multiplatform Desktop application running on Kotlin 2.4.10, Compose Multiplatform 1.11.1, and Java 17 bytecode.
The UI architecture follows a zero-flicker fixed bounding canvas ($1420 \times 760\text{ dp}$) approach rendered in an undecorated, per-pixel alpha transparent, always-on-top desktop window.

### Key Architectural Layers:
1. **Window Shell & Host Platform**: Pure Java AWT interop (`Window.Type.UTILITY`, `DropTarget`, `GraphicsEnvironment`, `ScreenInsets`, `MouseInfo`) with `composenativetray` for native Windows 11 taskbar suppression, tray icon toggle with 300ms debounce, and multi-monitor DPI-aware bounds calculation.
2. **Fixed Bounding Canvas & Kinematics**: A fixed $1420 \times 760\text{ dp}$ transparent window anchored at `Alignment.TopEnd` with $25\text{ dp}$ padding. All expand/collapse transitions ($300 \times 430\text{ dp} \leftrightarrow 1054 \times 625\text{ dp}$) occur internally via Compose spring physics (`spring(dampingRatio = 0.65f, stiffness = 300f)`), eliminating OS-level Direct3D swapchain recreation stutter.
3. **State Controller & Gesture Engine**: `DockedWindowStateController` orchestrates 3-phase drag tracking (5px deadzone, high-DPI scaling, 20px magnetic snap, contraction clamping, 450ms atomic 2D double-click reset), dynamic `Nudge-ForExpand`, and the 5-point focus loss guard.
4. **Interactive Componentry & Panels**: Tactile `QuickActionBar` ($56 \times 44\text{ dp}$ pill buttons with hover lift / press sink and Emerald state morphing), expandable `FileExplorerPanel` (SAF/History mode, 100x105dp grid cards, `PullProgressDock`), `SettingsPanel`, `PinPairingPanel`, and `MainMenuColumn`.
5. **Visual Styling & Liquid Glass**: `io.github.kyant0:backdrop:2.0.0` frosted glass shaders, $34\text{ dp}$ corner radius, Skia Gaussian drop shadows ($\sigma = \text{radius} / 2.0\text{f}$ with GC allocation hoisting), and 1:1 Dark/Light theme color tokens.

---

## Code Layout
```
w:\CodeDeX\DeX\DeX\
├── composeApp\src\desktopMain\kotlin\com\dexstudios\dex\
│   ├── main.kt                                    # Desktop entry point, AWT UTILITY, Window, Tray, DropTarget
│   ├── platform\
│   │   ├── ScreenBoundsHelper.kt                  # Multi-monitor bounds, graphics device queries
│   │   └── TaskbarWorkAreaProvider.kt             # Taskbar insets, resting dock coordinates calculation
│   └── window\
│       ├── DockedWindowStateController.kt         # Central state machine, panel states, drag, 5-point guard
│       ├── FloatingDockCard.kt                    # Fixed 1420x760 canvas, Alignment.TopEnd, root surface
│       ├── DockCardContent.kt                     # Card body container, panel layout, spring width/height
│       ├── MainMenuColumn.kt                      # TopActions, DeviceListPanel, BottomDockPanel
│       ├── components\
│       │   ├── DragPillHandle.kt                  # 3-phase drag, deadzone, DPI divide, magnetic snap, reset
│       │   ├── QuickActionBar.kt                  # 56x44dp pill buttons, hover/press animations, Emerald morph
│       │   ├── TopActionsPanel.kt                 # Drag pill row, quick actions bar, status bar telemetry
│       │   ├── DeviceListPanel.kt                 # Discovered (untrusted) & Paired (live) device lists
│       │   ├── BottomDockPanel.kt                 # Avatar button, 2-stage Exit Engine confirmation
│       │   ├── FileExplorerPanel.kt               # SAF / Transfer History 3-row layout, grid cards, pull dock
│       │   ├── SettingsPanel.kt                   # Profile header, DND, ADB, OAuth, theme, download picker
│       │   └── PinPairingPanel.kt                  # 6-digit PIN display, QR/PIN flip, shake animations
│       ├── kinematics\
│       │   ├── DockCardAnimations.kt              # PopIn, HoverEase, ContractEase, Spring physics
│       │   └── DockCardPhysics.kt                 # Magnetic snap, contraction clamp, Nudge-ForExpand math
│       └── styling\
│           ├── SkiaDropShadow.kt                  # Skia MaskFilter.makeBlur drop shadow with cached Paint
│           └── BorderGlow.kt                      # Subpixel antialiased inset double stroke
├── core\designsystem\src\commonMain\kotlin\com\dexstudios\dex\core\designsystem\
│   ├── components\glass\
│   │   ├── LiquidGlassConfig.kt                   # Liquid glass presets & parameters
│   │   └── LiquidGlassPanel.kt                    # Backdrop wrapper component
│   └── theme\
│       ├── Color.kt                               # Dark/Light color tokens (Primary, Accent, Secondary, Danger)
│       └── Theme.kt                               # DeX Theme provider
└── feature\discovery\src\commonMain\kotlin\com\dexstudios\dex\feature\discovery\
    └── MainScreenViewModel.kt                     # UI State, Discovery, Pairing, Transfers, Settings
```

---

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Undecorated Transparent Window | Fixed 1420x760dp canvas with per-pixel alpha transparency and undecorated frame | M1 | Survey |
| 2 | Taskbar Suppression | AWT UTILITY window type suppresses Windows taskbar icon | M1 | Survey |
| 3 | Multi-Monitor Work Area Insets | Calculates taskbar insets on active monitor using mouse cursor & screen insets | M1 | Survey |
| 4 | Exact Resting Positioning | Card rests 13px from right edge and 38px above taskbar ($X = \text{Right}_{\text{work}} - 1408$, $Y = \text{Bottom}_{\text{work}} - 468$) | M1 | Survey |
| 5 | 5-Point Focus Loss Guard | Auto-dismisses card unless pinned, transitioning, pairing, panel expanded, or modal open | M1 | Survey |
| 6 | System Tray & 300ms Debounce | Tray icon toggles visibility with 300ms debounce and right-click context menu (Show/Hide, Separator, Quit) | M1 | Survey |
| 7 | External AWT File Drop Target | Transparent canvas registers DropTarget forwarding external files to DeX transfer engine | M1 | Survey |
| 8 | Window State Controller | `DockedWindowStateController` managing window position, panel states, drag, and deactivation | M1 | Survey |
| 9 | TopEnd Alignment Expansion | Fixed 1420x760 canvas with `Alignment.TopEnd` and 25dp padding; expands leftward (+754dp) and downward (+195dp) | M2 | Survey |
| 10 | Pop-In Entrance Transition | Scale 0.85→1.0, translateY 15→0dp, opacity 0→1 over 500ms on tray open | M2 | Survey |
| 11 | Spring Physics Expansion | Port of WPF ElasticEase using Compose `spring(dampingRatio = 0.65f, stiffness = 300f)` | M2 | Survey |
| 12 | 3-Phase Drag Pill Deadzone | 5px Manhattan distance accumulator prevents accidental jitter from triggering drag | M2 | Survey |
| 13 | High-DPI Cursor Tracking | Divides physical mouse deltas by display density ($\Delta\text{px}/\rho$) for 1:1 tactile dragging | M2 | Survey |
| 14 | 20px Magnetic Boundary Snapping | Snaps card edge to monitor work area boundaries with 120ms cubic ease-out settle | M2 | Survey |
| 15 | Contraction Clamping (Void Prevention) | Dynamic origin clamping shifts window X origin when collapsing near screen right edge | M2 | Survey |
| 16 | Double-Click Reset & Pin Shake | Double-click animates card to resting dock (450ms 2D loop); if pinned, shakes ±5px 3 cycles | M2 | Survey |
| 17 | Dynamic Nudge-ForExpand | Slides window origin synchronously when expanding would overflow display, evaluating against target 1054dp | M2 | Survey |
| 18 | QuickActionBar Pill Geometry | Centered row of 4 primary 56x44dp pills (DND, Mirror, Transfers, Clipboard) + Danger Close pill | M3 | Survey |
| 19 | Tactile Hover Lift & Press Sink | Hover scales 1.08x / translateY -3dp (300ms); Press sinks 0.85x / translateY +3dp (100ms) | M3 | Survey |
| 20 | Emerald State Morphing | Active toggle smoothly morphs background to Emerald (#0AE66D) with black icon and inverted badges | M3 | Survey |
| 21 | Collapsible Danger Close Pill | Red (#FF453A) close button expands 0→56dp when panel is open, collapsing drawer on click | M3 | Survey |
| 22 | Status Bar & ADB Telemetry | Collapsible 39dp status bar with connection status, IP:port display, and Copy IP button | M3 | Survey |
| 23 | Discovered Devices List | UDP discovered devices with model, alias, online indicator, and PIN code handshake routing | M3 | Survey |
| 24 | Discovered Device Context Menu | Context menu providing PIN Code (Pair), Connect ADB, Copy IP Address, and Forget Device | M3 | Survey |
| 25 | Paired Devices List | Trusted paired devices with battery glyph/%, wifi icon/band, subtext telemetry, and offline styling | M3 | Survey |
| 26 | Paired Device Context Menu | Context menu providing Send Clipboard, Mirror Screen, Copy IP, Connect/Disconnect ADB, Rename, Forget | M3 | Survey |
| 27 | WAN Placeholder Profiles | Visual scaffolding for upcoming WAN cross-email feature (Ama Serwaa, Akua Donkor, Kwame Asante) | M3 | Survey |
| 28 | FileExplorer Header Navigation | 36dp circular Up-Dir button, 40dp search pill with 150ms debounce, SAF vs History mode toggle | M3 | Survey |
| 29 | SAF vs Local History Toggle | Toggle button switches between Local Transfer History (`Downloads\DeX`) and Phone SAF Tree | M3 | Survey |
| 30 | File & Folder Grid Cards | 100x105dp item cards with folder/file glyphs, 48x48dp thumbnails, hover lift, 400ms double-click guard | M3 | Survey |
| 31 | Dangerous File Protection | Dangerous extensions (`.exe`, `.bat`, `.ps1`) opened via `/select` in Explorer instead of direct launch | M3 | Survey |
| 32 | PullProgressDock Toast | Floating 360dp bottom toast with progress count, throughput speed, 4dp emerald progress bar, cancel button | M3 | Survey |
| 33 | Send Files & Folders Actions | Footer action buttons launching native OS file and directory picker dialogs | M3 | Survey |
| 34 | Settings Profile & Account Header | 56x56dp avatar, profile name, email, Premium badge, and Sign Out button | M3 | Survey |
| 35 | Settings Preference Categories | Categorized list: Connection (DND), Dev Tools (ADB), Identity (OAuth), Appearance (Theme), Storage, About | M3 | Survey |
| 36 | PIN / QR Pairing View | 6-digit PIN display (44x56dp boxes, 32sp bold) and 140x140dp QR code with 60s expiry timers | M3 | Survey |
| 37 | QR ↔ PIN Flip Transition | Smooth 250ms horizontal slide and crossfade between QR code and PIN digits | M3 | Survey |
| 38 | PIN Error Shake Animation | 15px spring shake animation on digit boxes upon incorrect PIN entry | M3 | Survey |
| 39 | Exit Engine 2-Stage Confirmation | Exit button checks for active transfers; prompts confirmation (3s timeout) with Shift+Click bypass | M3 | Survey |
| 40 | Liquid Glass Backdrop Surface | Real-time GPU frosted glass blur, lens refraction edge, and ambient highlight via Backdrop library | M4 | Survey |
| 41 | Skia Gaussian Drop Shadow | Skia `MaskFilter.makeBlur(NORMAL, sigma)` with $\sigma = \text{radius} / 2.0\text{f}$ and GC allocation hoisting | M4 | Survey |
| 42 | Subpixel Inset Border Glow | Subpixel antialiased inset border stroke (1dp #2B2631) with subtle ambient outer glow | M4 | Survey |
| 43 | 34dp Corner Radius Geometry | Standard 34dp rounded corner geometry across main card, clipping all child components | M4 | Survey |
| 44 | Dark & Light Color Tokens | Complete color tokens: Primary (#16121A), Accent (#2B2631), Secondary (#0AE66D), Danger (#FF453A) | M4 | Survey |
| 45 | Clean Desktop Compilation | 100% build pass rate on `./gradlew :composeApp:compileKotlinDesktop` & `./gradlew :composeApp:desktopJar` | M4 | Survey |

---

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | M1: Desktop Window & Shell Architecture | Window transparency, AWT UTILITY taskbar suppression, Multi-monitor WorkArea bounds, Exact resting positioning, 5-point focus loss guard, System Tray with debounce and menu, External DropTarget, DockedWindowStateController | None | DONE |
| 2 | M2: Floating Dock Card Canvas & Kinematics Layer | Fixed 1420x760dp canvas with TopEnd alignment & 25dp padding, Spring physics expansion (300x430dp to 1054x625dp), Pop-in entrance transition, 3-phase drag pill handler (5px deadzone, DPI scaling, 20px magnetic snap, contraction clamp, 450ms atomic reset), Nudge-ForExpand boundary math | M1 | DONE |
| 3 | M3: Quick Actions, Panels & ViewModel Integration | QuickActionBar (56x44dp pills, hover/press, emerald morph, danger close), Discovered & Paired Device Lists with context menus, FileExplorerPanel (3-row layout, grid cards, PullProgressDock), SettingsPanel, PinPairingPanel, BottomDockPanel with 2-stage exit | M2 | DONE |
| 4 | M4: Visual Styling, Liquid Glass & Final Build Verification | 34dp corner radius, Skia Gaussian drop shadow with GC hoisting, Subpixel inset border glow, Backdrop liquid glass integration, Dark/Light color tokens, Complete compilation and packaging verification | M3 | DONE |

---

## Interface Contracts

### `DockedWindowStateController` ↔ `main.kt` & `FloatingDockCard.kt`
```kotlin
class DockedWindowStateController(
    val windowState: WindowState,
    val density: Float,
    val workArea: WorkAreaBounds,
    val coroutineScope: CoroutineScope
) {
    var isVisible by mutableStateOf(false)
    var isPinned by mutableStateOf(false)
    var isShowingTransition by mutableStateOf(false)
    var isPairingActive by mutableStateOf(false)
    var isModalDialogOpen by mutableStateOf(false)
    var expandedPanel by mutableStateOf<ExpandedPanel>(ExpandedPanel.None)
    val isExpanded: Boolean get() = expandedPanel != ExpandedPanel.None

    fun shouldDismissOnFocusLoss(): Boolean =
        !isPinned && !isShowingTransition && !isPairingActive && !isExpanded && !isModalDialogOpen

    fun onDragStart(screenX: Float, screenY: Float)
    fun onDragDelta(deltaX: Float, deltaY: Float)
    fun onDragEnd()
    fun onDoubleTapReset()
    fun expandPanel(panel: ExpandedPanel)
    fun collapsePanel()
}
```

### `TaskbarWorkAreaProvider` ↔ `main.kt`
```kotlin
object TaskbarWorkAreaProvider {
    fun getWorkAreaForCursor(): WorkAreaBounds
    fun calculateInitialWindowPosition(workArea: WorkAreaBounds, canvasWidth: Int = 1420, cardCollapsedHeight: Int = 430): IntOffset {
        val x = workArea.right - canvasWidth + 12
        val y = workArea.bottom - cardCollapsedHeight - 38
        return IntOffset(x, y)
    }
}
```

### `QuickActionBar` ↔ `DockedWindowStateController`
```kotlin
@Composable
fun QuickActionBar(
    isDndActive: Boolean,
    isMirroringActive: Boolean,
    isTransfersActive: Boolean,
    clipboardBadgeCount: Int,
    isPanelExpanded: Boolean,
    onToggleDnd: () -> Unit,
    onToggleMirror: () -> Unit,
    onToggleTransfers: () -> Unit,
    onClipboardClick: () -> Unit,
    onCloseExpandedPanel: () -> Unit,
    modifier: Modifier = Modifier
)
```
