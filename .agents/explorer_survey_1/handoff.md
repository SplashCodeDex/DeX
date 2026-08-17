# DeX Specification & Requirements Inventory Report (Survey 1)

## Observation

Authoritative specification sources inspected:
1. `w:\CodeDeX\DeX\.agents\ORIGINAL_REQUEST.md` (User requirements for UI parity, floating dock card, expand/collapse states, file explorer, quick actions, trusted devices, SAF folders, handshake).
2. `w:\CodeDeX\DeX\UltimateMigrationPlan-WPF-Compose-UI.md` (Part I and Part II technical blueprints, mathematical coordinate models, kinematic physics, Skia shaders, design tokens, state machine).
3. Legacy WPF/Win32 source code and XAML stylesheets in `w:\CodeDeX\DeX\MSIX_Source\`:
   - `Themes\MainWindow.xaml` (L1–1064: 1420×760 transparent window, CornerRadius=34, FileExplorer grid, SettingsPanel, MainMenuColumn, pairing view, device templates, quick action buttons).
   - `Themes\AppStyles.xaml` (L1–1047: PopIn, ExpandMenu, ContractMenu, ExpandSettings, ContractSettings, SlideInPinAnim, SwitchQrToPinAnim, HoverEase, BouncyEase, SmoothEase, item templates).
   - `Themes\DarkTheme.xaml` & `LightTheme.xaml` (Color palette: Primary `#16121A`, Accent `#2B2631`, Secondary `#0AE66D`, Danger `#FF453A`, PrimaryText `#FFFFFF`, SecondaryText `#A0A0A0`).
   - `bin\Modules\Bindings_Tray.ps1` (L1–99: Tray click debounce 300ms, work area bounds calculation, 13px screen gap, 38px taskbar gap, PopIn storyboard trigger, guard timer 800ms).
   - `bin\Modules\Bindings_Window.ps1` (L1–759: Win32 `GetCursorPos`/`GetDpiForWindow`, 3-phase drag with 5px deadzone, 20px magnetic snap, double-click reset, 5-point focus loss guard, Exit Engine 2-stage confirm, shortcut keys).
   - `bin\Modules\Bindings_FileBrowser.ps1` (L1–597: SAF phone folders vs local history `Downloads\DeX`, 150ms search debounce, 400ms double-click guard, pull progress dock, drag-and-drop).
   - `bin\Modules\Bindings_Settings.ps1` (L1–392: DND toggle, ADB auto-connect, Google OAuth loopback, theme switcher, download location picker, reset trust).
   - `bin\Modules\UIComponents.ps1` (L1–891: Nudge-ForExpand directional algorithm, QR code background job generator, toast notifications, pin panel controller).
4. Compose Multiplatform codebase in `w:\CodeDeX\DeX\DeX\`:
   - `composeApp\src\desktopMain\kotlin\com\dexstudios\dex\main.kt` and `window\` composables.
   - `core\designsystem\` components, liquid glass backdrop presets (`LiquidGlassConfig.kt`), and theme definitions.
   - `feature\discovery\` (`MainScreenViewModel.kt`, `DeviceListItem.kt`).

---

## Logic Chain

1. **Window & Shell Foundation**:
   - The desktop app must float seamlessly above the desktop without OS window borders or taskbar footprint. In Win32/WPF, this was achieved via `WindowStyle="None"`, `Background="Transparent"`, `AllowsTransparency="True"`, `Topmost="True"`, and `ShowInTaskbar="False"`.
   - In Compose Multiplatform Desktop, setting `Window(undecorated = true, transparent = true, alwaysOnTop = true, resizable = false)` alongside AWT `window.type = java.awt.Window.Type.UTILITY` replicates this exact environment.
   - DPI-aware work area calculations (`TaskbarWorkAreaProvider` / `ScreenBoundsHelper`) subtract multi-monitor taskbar insets so the card rests at $X_{\text{window}} = \text{Right}_{\text{work}} - 1420 + 12\text{ dp}$ and $Y_{\text{window}} = \text{Bottom}_{\text{work}} - 430 - 38\text{ dp}$, giving exactly $13\text{ px}$ from the screen right and $38\text{ px}$ above the taskbar.
   - Deactivation/dismissal on focus loss must enforce the strict 5-point guard (`!isPinned && !isShowingTransition && !isPairingActive && !isExpanded && !isModalDialogOpen`) to prevent unwanted dismissals during external file drags or modal dialog interactions.

2. **Kinematics & Layout Mechanics**:
   - Dynamic OS window resizing (Approach A) reallocates Direct3D swapchains at 60–120 FPS causing severe flickering and dropped frames. The authoritative solution is a static $1420 \times 760\text{ dp}$ transparent bounding canvas with `Alignment.TopEnd` anchoring + $25\text{ dp}$ internal margin padding.
   - Mathematical proof confirms `Alignment.TopEnd` expands leftward (from $X=1095$ to $X=341$) and downward (from $Y=455$ to $Y=650$) inside the canvas, staying $13\text{ px}$ above the taskbar without moving the OS window. `Alignment.BottomEnd` would push the card $267\text{ px}$ below the taskbar.
   - All legacy WPF animation curves map 1:1 to Compose animation specs: `ElasticEase(Oscillations=1, Springiness=7)` $\to$ `spring(dampingRatio = 0.65f, stiffness = 300f)`, `BackEase(Amplitude=3.53)` $\to$ `PopInEase` (scale $0.85 \to 1.0$, translateY $15 \to 0$, $500\text{ ms}$), `BackEase(Amplitude=1.22)` $\to$ `HoverEase` (scale $1.08\times$, translateY $-3\text{ dp}$), `BackEase(Amplitude=0.15)` $\to$ `ContractEase` ($600\text{ ms}$).
   - The 3-phase drag pill handler incorporates a $5\text{ px}$ deadzone, density scaling ($\Delta\text{px}/\rho$), $20\text{ px}$ magnetic boundary snapping, $120\text{ ms}$ cubic ease-out settle, contraction clamping (void prevention), and synchronized atomic 2D double-click reset ($450\text{ ms}$).
   - Dynamic nudging (`Nudge-ForExpand`) slides the window origin synchronously when expanding near screen edges, evaluating boundaries against target expanded dimensions ($1054 \times 625\text{ dp}$).

3. **Subcomponent & State Integration**:
   - `QuickActionBar` renders 4 primary $56 \times 44\text{ dp}$ pill buttons (DND, Mirror, Transfers/Files, Clipboard) plus dynamic danger Close button ($56\text{ dp} \to 0\text{ dp}$), with tactile hover/press animations and emerald morphing on active state.
   - `FileExplorerPanel` provides 3-row layout: header (up-dir, $150\text{ ms}$ debounced search, SAF/History mode toggle), grid ($100 \times 105\text{ dp}$ item cards, double-click protection, thumbnails), and footers (Send Files/Folders, external AWT file drop, floating $360\text{ dp}$ `PullProgressDock`).
   - `SettingsPanel` renders profile avatar, DND toggle, ADB debugging controls, Google Sign-In loopback, theme toggle, custom download folder, and identity reset.
   - `PinPairingPanel` displays interactive $32\text{ sp}$ PIN digit boxes with smooth QR/PIN flip transitions and error shake animations.
   - MainMenuColumn footer hosts $34\text{ dp}$ profile avatar and Exit Engine button with 2-stage active transfer guard and Shift+Click bypass.

4. **Visual Styling & Liquid Glass**:
   - Uses `io.github.kyant0:backdrop` (v2.0.0) Two-Layer architecture (`Modifier.layerBackdrop` / `Modifier.drawBackdrop`) or Skia backdrop blur shader with Gaussian drop shadow (`MaskFilter.makeBlur(FilterBlurMode.NORMAL, sigma)` where $\sigma = \text{blurRadius} / 2.0\text{f}$).
   - Corner radius is strictly $34\text{ dp}$ for the root card, with subpixel antialiased inset borders (`#2B2631` + white ambient glow alpha 0.12).
   - Full dark/light palette and typography hierarchy mapped 1:1 from WPF styles.

---

## Features Discovered

| # | Category | Feature | Description | Inputs | Outputs | Error Behavior | Discovered Via |
|---|---|---|---|---|---|---|---|
| 1 | Window Shell | Undecorated Transparent Canvas | Window canvas fixed at 1420×760dp with per-pixel alpha transparency and zero OS window borders | `WindowState(1420.dp, 760.dp)` | Transparent desktop overlay | Fallback to solid background on unsupported OS | `MainWindow.xaml:L4-7`, `UltimateMigrationPlan:L43-52` |
| 2 | Window Shell | Taskbar Suppression | Suppresses Windows taskbar icon by assigning AWT window type to UTILITY | `window.type = Window.Type.UTILITY` | Hidden from Windows taskbar | Standard window fallback if security manager blocks | `UltimateMigrationPlan:L518-524`, `Bindings_Tray.ps1` |
| 3 | Window Shell | Multi-Monitor Work Area Calculation | Resolves taskbar insets across active display using cursor position & GraphicsDevice | Mouse cursor coordinates, Screen Insets | `WorkAreaBounds(left, top, right, bottom)` | Fallback to primary display work area | `ScreenBoundsHelper.kt`, `UltimateMigrationPlan:L574-648` |
| 4 | Window Shell | Exact Resting Positioning | Positions card resting 13px from right edge and 38px above taskbar | `Right_work - 1420 + 12`, `Bottom_work - 430 - 38` | `WindowPosition(x, y)` | Clamped to screen boundary | `Bindings_Tray.ps1:L51-58`, `UltimateMigrationPlan:L681-704` |
| 5 | Window Shell | 5-Point Focus Loss Guard | Deactivation listener auto-hides card on click-outside, guarded against pin, transition, pairing, expanded drawer, and modal picker | Window focus lost event, 5 state booleans | Window hide / dismiss | Dismissal suppressed when any guard is true | `Bindings_Window.ps1:L587-611`, `UltimateMigrationPlan:L528-555` |
| 6 | Window Shell | System Tray Toggle & Debounce | Tray icon click toggles card visibility with 300ms debounce filter | Mouse click on system tray icon | `isVisible = !isVisible`, PopIn animation | Debounced if clicked <300ms | `Bindings_Tray.ps1:L2-21`, `main.kt:L63-71` |
| 7 | Window Shell | External Drag & Drop Target | Transparent canvas registers AWT DropTarget accepting external Windows Explorer file drops | File drop event (`DataFlavor.javaFileListFlavor`) | Target IP file push via `DeXShareTarget.exe` | Ignored if no files present | `Bindings_FileBrowser.ps1:L562-596`, `main.kt:L85-98` |
| 8 | Animation Layer | TopEnd Alignment Expansion | Canvas anchors card to TopEnd; width expands leftward (+754dp), height expands downward (+195dp) | `isExpanded: Boolean`, `ExpandedPanel` | Card size animated from 300×430dp to 1054×625dp | Post-expansion clamping prevents overflow | `FloatingDockCard.kt`, `UltimateMigrationPlan:L696-704` |
| 9 | Animation Layer | PopIn Entrance Transition | Spring physics pop-in with scale 0.85→1.0, translateY 15→0dp, and opacity 0→1 over 500ms | Card visibility triggered | Animated scale, translation, opacity | Storyboard stop on interruption | `AppStyles.xaml:L281-291`, `UltimateMigrationPlan:L1062-1067` |
| 10 | Animation Layer | Elastic Expansion Physics | 1:1 port of WPF ElasticEase(Oscillations=1, Springiness=7) using Compose spring spec | Dimension changes (Dp / IntOffset) | `spring(dampingRatio = 0.65f, stiffness = 300f)` | Clamped to max canvas bounds | `AppStyles.xaml:L113-118`, `DockCardPhysics.kt` |
| 11 | Animation Layer | 3-Phase Drag Pill Deadzone | Drag handle filters jitter with 5px Manhattan distance deadzone accumulator | Mouse move physical deltas | Drag commit / pending lock | Drag cancelled if mouse released before 5px | `Bindings_Window.ps1:L370-403`, `UltimateMigrationPlan:L783-808` |
| 12 | Animation Layer | High-DPI Cursor Tracking | Divides physical Win32 mouse deltas by display density ratio (DPI / 96.0) for 1:1 tactile drag | Physical mouse ΔX/ΔY, Display Density | Dp window delta position | Default to 1.0 density if DPI query fails | `Bindings_Window.ps1:L405-416`, `UltimateMigrationPlan:L809-812` |
| 13 | Animation Layer | 20px Magnetic Boundary Snap | Snaps card edge to monitor work area boundaries when within 20px, with 120ms ease-out settle | Card content rect vs WorkArea rect | Snapped coordinates + animation | Smooth return if dragged away | `Bindings_Window.ps1:L438-464`, `UltimateMigrationPlan:L813-818` |
| 14 | Animation Layer | Contraction Clamping (Void Prevention) | Clamps window position upon panel collapse near screen right edge, preventing 544px void stranding | Panel contraction trigger | Clamped window X origin | Window animated to safe origin | `Bindings_Window.ps1:L540-557`, `UltimateMigrationPlan:L820-822` |
| 15 | Animation Layer | Double-Click Reset & Pin Shake | Double-clicking drag pill animates card back to resting dock (450ms atomic 2D loop); if pinned, shakes ±5px 3 cycles | Double-click on drag pill | Window position animated / shake animation | Ignored if already at default position | `Bindings_Window.ps1:L263-303`, `UltimateMigrationPlan:L823` |
| 16 | Animation Layer | Dynamic Nudge-ForExpand | Slides window origin synchronously when expanding would overflow display, evaluating against target 1054dp | Panel expand trigger, monitor bounds | Window X/Y slide animation (800ms) | Clamped to prevent pushing opposite edge off | `UIComponents.ps1:L267-341`, `UltimateMigrationPlan:L720-776` |
| 17 | Quick Actions | QuickActionBar Pills | Centered horizontal row of 4 primary 56×44dp pills (DND, Mirror, Transfers, Clipboard) + Danger Close pill | User tap, hover, press | Toggle feature state / expand panel | Disabled/grayed if prerequisites unmet | `MainWindow.xaml:L657-673`, `AppStyles.xaml:L612-680` |
| 18 | Quick Actions | Tactile Press-Sink & Hover | Hover scales 1.08× / translateY -3dp (300ms HoverEase); Press sinks to 0.85× / translateY +3dp (100ms) | Pointer hover / press interaction | Transformed graphics layer | Reset on mouse exit | `AppStyles.xaml:L640-676`, `UltimateMigrationPlan:L1162-1180` |
| 19 | Quick Actions | Emerald State Morphing | Active toggle transforms background to Emerald (#0AE66D) with black icon and inverted badge colors | `isChecked: Boolean` | Morphing color and shadow glow | Smooth 200ms tween transition | `AppStyles.xaml:L636-639`, `UltimateMigrationPlan:L1182-1202` |
| 20 | Quick Actions | Collapsible Danger Close Pill | Red (#FF453A) close button expands from width 0 to 56dp when panel is open, collapsing drawer on click | Drawer expanded state | Drawer contract animation | Width animates to 0dp on contract | `AppStyles.xaml:L132-137`, `AppStyles.xaml:L255-265` |
| 21 | Quick Actions | Status Bar & ADB Telemetry | Collapsible 39dp status bar displaying connection state with text ellipsis and Copy IP button | Connection state, IP:Port string | Status text, clipboard copy, checkmark icon | Error toast if clipboard is locked | `MainWindow.xaml:L675-701`, `Bindings_Settings.ps1:L1-36` |
| 22 | Device Lists | Discovered Devices (UDP Peers) | Dynamic list of discovered nearby devices with model, alias, online indicator dot, and context menu | `DiscoveryEngine.devices` Flow | Rendered device row items | Empty state hidden when list count is 0 | `MainWindow.xaml:L707-756`, `DeviceListPanel.kt` |
| 23 | Device Lists | Discovered Device Context Menu | Context menu providing PIN Code (Pair), Connect ADB, Copy IP Address, and Forget Device | Right-click / long-press on device | Context menu popup | Actions validate IP and fingerprint | `MainWindow.xaml:L726-733`, `Bindings_Window.ps1:L668-731` |
| 24 | Device Lists | Paired Devices (Live Peers) | Dynamic list of paired devices with battery glyph/%, wifi icon/band, subtext telemetry, and offline styling | Paired devices repository state | Device item with telemetry badges | Offline devices rendered at 0.5 opacity | `MainWindow.xaml:L807-863`, `DeviceListItem.kt` |
| 25 | Device Lists | Paired Device Context Menu | Context menu providing Send Clipboard, Mirror Screen, Copy IP, Connect/Disconnect ADB, Rename, Forget | Right-click / long-press on paired device | Context menu popup | Disconnect ADB only shown if connected | `MainWindow.xaml:L812-821`, `Bindings_Window.ps1:L733-755` |
| 26 | Device Lists | WAN Placeholder Profiles | Static visual scaffolding for upcoming WAN cross-email feature (Ama Serwaa, Akua Donkor, Kwame Asante) | Static user profiles | Scaffolding profile list items | Non-interactive layout scaffolding | `MainWindow.xaml:L866-958` |
| 27 | Expandable Panels | FileExplorer Header Navigation | 36dp circular Up Directory button, 40dp search pill with 150ms debounce, mode toggle button | User typing, folder navigation | Filtered file grid / directory change | Up-dir no-ops at root | `MainWindow.xaml:L50-87`, `Bindings_FileBrowser.ps1:L251-292` |
| 28 | Expandable Panels | SAF vs Local History Toggle | Toggle button switches between Local Transfer History (`Downloads\DeX`) and Phone SAF Tree | Mode toggle click | Reloads directory grid | Prompts SAF folder grant on phone if none | `Bindings_FileBrowser.ps1:L303-394`, `Bindings_FileBrowser.ps1:L397-420` |
| 29 | Expandable Panels | File & Folder Grid Cards | 100×105dp item cards with folder/file glyphs, 48×48dp thumbnail clip, hover lift (-2dp), 400ms double-click guard | Directory items list | Interactive grid cards | Dangerous extensions opened via `/select` | `AppStyles.xaml:L294-417`, `Bindings_FileBrowser.ps1:L443-490` |
| 30 | Expandable Panels | PullProgressDock Toast | Floating 360dp bottom toast showing pull progress count, throughput speed, 4dp emerald progress bar, cancel button | Active pull WebSocket stream | Progress toast overlay | Auto-hides on complete/cancel; stall timeout | `MainWindow.xaml:L184-199`, `Bindings_FileBrowser.ps1:L26-248` |
| 31 | Expandable Panels | Send Files / Folders Actions | Footer action toggle buttons triggering native OS file and directory picker dialogs | User click | Launches `FileDialog` / `FolderDialog` | Focus loss guarded during picker modal | `MainWindow.xaml:L153-167`, `Bindings_FileBrowser.ps1:L558-561` |
| 32 | Expandable Panels | Settings Profile & Account | Header displaying 56×56dp avatar, profile name, email, Premium badge, and Sign Out button | Google Profile state | Rendered profile header | Degrades to guest view when signed out | `MainWindow.xaml:L248-278`, `Bindings_Settings.ps1:L229-290` |
| 33 | Expandable Panels | Settings Option Categories | Categorized list: Connection (DND), Dev Tools (ADB, Auto-Connect), Identity (OAuth), Appearance (Theme), Storage, About | User click on settings row | Executes preference changes / dialogs | Settings saved to persistent storage | `MainWindow.xaml:L279-470`, `Bindings_Settings.ps1:L38-226` |
| 34 | Expandable Panels | PIN / QR Pairing View | Slide-in pairing panel supporting 6-digit PIN display (44×56dp boxes, 32sp bold) and 140×140dp QR code | Pairing handshake trigger | Pairing panel with flip animations | Error shake animation on PIN failure | `MainWindow.xaml:L964-1053`, `PinPairingPanel.kt` |
| 35 | Expandable Panels | QR ↔ PIN Flip Transition | Smooth 250ms horizontal slide and opacity crossfade switching between QR code and PIN digits | "PIN CODE" / "QR CODE" button tap | `SwitchQrToPinAnim` / `SwitchPinToQrAnim` | Session timer preserved across view toggle | `AppStyles.xaml:L27-47`, `UIComponents.ps1:L374-474` |
| 36 | Expandable Panels | Pairing Expiry Timers | 60s idle QR phase expiry and 60s PIN acceptance countdown timer | Pairing session start | Live countdown subtext / auto-cancel | Session automatically stops on timeout | `Bindings_Window.ps1:L716-723`, `UIComponents.ps1:L476-500` |
| 37 | Expandable Panels | Exit Engine 2-Stage Confirmation | Exit button checks for active transfers or mirroring; prompts "Transfer Active! Click to Force Exit" (3s timeout) | Click Exit button / Shift+Click | Confirmation prompt or immediate exit | Reverts to "Exit Engine" after 3s timeout | `Bindings_Window.ps1:L27-174`, `BottomDockPanel.kt` |
| 38 | Visual Styling | Liquid Glass Backdrop Surface | Real-time GPU frosted glass blur, lens refraction edge, chromatic aberration, and ambient highlight | `LiquidGlassConfig` parameters | Rendered backdrop shader surface | Falls back to Skia drop shadow + solid tint | `LiquidGlassConfig.kt`, `UltimateMigrationPlan:L830-916` |
| 39 | Visual Styling | Skia Gaussian Drop Shadow | Skia `MaskFilter.makeBlur(NORMAL, sigma)` drop shadow with sigma = radius / 2.0f and cached Paint instance | Blur radius, border radius, offset | GPU accelerated drop shadow | Canvas padding prevents rectangular clipping | `UltimateMigrationPlan:L920-990` |
| 40 | Visual Styling | Subpixel Inset Border Glow | Subpixel antialiased inset border stroke (1dp #2B2631) with subtle ambient outer glow (#FFFFFF alpha 0.12) | Stroke width, border color, glow color | Inset double stroke | Snaps to device pixels without blurriness | `UltimateMigrationPlan:L993-1022` |
| 41 | Visual Styling | 34dp Corner Radius Geometry | Standard 34dp rounded corner geometry across main card, clipping all child components and shadows | `RoundedCornerShape(34.dp)` | Clipped rounded card surface | Consistent across expanded and compact states | `MainWindow.xaml:L25`, `UltimateMigrationPlan:L1810` |
| 42 | Visual Styling | Dark & Light Color Tokens | Complete color token matrix defining Primary (#16121A / #FFFFFF), Accent (#2B2631 / #F2F2F7), Secondary (#0AE66D), Danger (#FF453A / #FF3B30) | Theme selection | Dynamic theme color scheme | Instant recomposition on theme change | `DarkTheme.xaml`, `LightTheme.xaml`, `Theme.kt` |

---

## Edge Cases

| # | Feature | Input | Observed Behavior |
|---|---|---|---|
| 1 | Window Shell | Tray click double-firing | Rapid physical clicks within 300ms are debounced to prevent flashing/stuttering. |
| 2 | Window Shell | Focus loss during expanded File Explorer | Deactivation listener checks `!isExpanded`; card remains open so desktop drag-and-drop works seamlessly. |
| 3 | Window Shell | Focus loss during native OS File/Folder Picker | Deactivation listener checks `!isModalDialogOpen`; opening Windows Explorer picker dialog does not dismiss card. |
| 4 | Window Shell | Focus loss during active Pairing session | Deactivation listener checks `!isPairingActive`; auto-shown inbound PIN/QR remains visible until user acts. |
| 5 | Window Shell | Focus loss during PopIn entrance animation | Deactivation listener checks `!isShowingTransition` (800ms guard timer); prevents double-flash race conditions. |
| 6 | Window Shell | Multi-monitor with mixed DPI scaling (100% + 150%) | Win32 `GetDpiForWindow` dynamically scales physical cursor mouse deltas by density $\rho$, eliminating cursor runaway. |
| 7 | Animation Layer | Drag gesture under 5px Manhattan distance | Manhattan accumulator $|\Delta X| + |\Delta Y| < 5\text{ px}$ prevents accidental 1px jitter from triggering drag state. |
| 8 | Animation Layer | Drag card within 20px of monitor edge | Magnetic snapping locks card coordinates to work area edge; smooth 120ms cubic ease-out settles on release. |
| 9 | Animation Layer | Drag release near monitor perimeter | Sanity clamp ensures at least $\max(W_{\text{card}} \times 0.2, 60\text{ px})$ remains reachable inside the visible monitor. |
| 10 | Animation Layer | Contraction near right screen boundary | Contraction clamping dynamically shifts window X origin so collapsing from 1054dp to 300dp does not strand card 544px offscreen. |
| 11 | Animation Layer | Double-click drag pill while pinned | Bypasses position reset; executes 3-cycle shake animation ($\pm 5\text{ px}$ over $50\text{ ms}$) indicating locked position. |
| 12 | Animation Layer | Panel expansion on small displays ($\le 1024\text{ px}$) | `Nudge-ForExpand` clamps against target expanded dimensions ($1054\text{ dp}$), sliding window origin to prevent 43px offscreen clipping. |
| 13 | Animation Layer | Canvas alignment inverted to `BottomEnd` | Mathematically pushes bottom of card $267\text{ px}$ below taskbar; `Alignment.TopEnd` with margin $25\text{ dp}$ is strictly required. |
| 14 | Quick Actions | Pressing Quick Action button | Snappy 100ms press sink (scale 0.85×, translateY +3dp) provides immediate tactile feedback before action executes. |
| 15 | Quick Actions | Tapping active Emerald toggle | Smoothly morphs background from Emerald `#0AE66D` back to Accent `#2B2631` over 200ms tween. |
| 16 | File Explorer | Double-clicking file items within 400ms | Double-click throttle filter ($400\text{ ms}$ timestamp delta) blocks duplicate concurrent pull requests. |
| 17 | File Explorer | Double-clicking dangerous file types (`.exe`, `.bat`, `.ps1`) | Launches `explorer.exe /select,"<path>"` rather than directly executing the executable for security. |
| 18 | File Explorer | Pulling files when phone stalls / disconnects | 120s activity-based stall timer detects silence and reports "Pull Stalled" toast rather than hanging indefinitely. |
| 19 | File Explorer | Hovering over download toast dock | Mouse enter pauses 4-second auto-hide timer; mouse leave resumes countdown. |
| 20 | File Explorer | Navigating Up from SAF tree root | Strips `%2F` document markers until root is reached, then seamlessly transitions back to "Phone Folders" root list. |
| 21 | File Explorer | Path text exceeding 35 characters | Truncates text with middle ellipsis (`Downloads\DeX...subfolder`) while preserving full path in tooltip. |
| 22 | Settings | Long-running Google OAuth sign-in | Executes in background job with 2s UI poll timer; prevents UI freeze during multi-minute browser approval. |
| 23 | Settings | Fast repeated clicks on Google sign-in | Checks for active background job and toasts "Sign-in already in progress - check your browser." |
| 24 | Pairing | User switches between QR and PIN views | Preserves device pairing context and restarts 60s expiry timer without revoking existing device trust. |
| 25 | Pairing | Incorrect PIN entered on phone | Triggers 15px spring shake animation on digit boxes and resets input state after 400ms delay. |
| 26 | Exit Engine | Exiting while file transfer or screen mirror is active | Replaces button label with "Transfer Active! Click to Force Exit" (3s timeout) to prevent accidental data corruption. |
| 27 | Exit Engine | User holds Shift while clicking Exit Engine | Bypasses transfer warning and forces immediate clean engine shutdown and process exit. |
| 28 | Visual Styling | Skia drop shadow blur sigma calculation | Passing blur radius directly doubles Gaussian sigma ($3\sigma = 72\text{ dp}$); must compute $\sigma = \text{radius} / 2.0\text{f}$. |
| 29 | Visual Styling | Native Skia Paint allocation inside render loop | Hoists `Paint` and `MaskFilter` into remembered `@Composable` modifier to prevent GC allocation spikes during spring animations. |

---

## Caveats

1. **macOS / Linux Desktop Portability**:
   - The primary authoritative specification is based on Windows 11 Desktop Window Manager (DWM), Win32 APIs, and AWT `UTILITY` window behavior. On macOS, tray mechanics use the menu bar top-right icon and `apple.awt.fullWindowContent`.
2. **Backdrop Fallback on Multiplatform Skia**:
   - `io.github.kyant0:backdrop` (v2.0.0) renders in-app backdrop blurs beautifully; however, sampling host OS desktop wallpaper through a transparent window boundary across separate OS processes is governed by OS compositor security. The Skia Gaussian drop shadow + solid dark tint fallback handles this seamlessly.
3. **WAN Cross-Email Scaffolding**:
   - In `MainWindow.xaml`, `btnUser1`, `btnUser2`, and `btnUser3` are explicit intentional scaffolding for upcoming WAN transfers and currently have no backend handlers attached.

---

## Conclusion

The authoritative specification discovery reveals a rich, highly sophisticated desktop interface designed for tactile responsiveness, zero visual tearing, and rigorous edge-case resilience. 

Key architectural standards established:
1. **Window Canvas**: Fixed $1420 \times 760\text{ dp}$ transparent window anchored at `Alignment.TopEnd` with $25\text{ dp}$ margin, positioned $13\text{ px}$ from display right and $38\text{ px}$ above the taskbar ($X = \text{Right}_{\text{work}} - 1408$, $Y = \text{Bottom}_{\text{work}} - 468$).
2. **Animation Engine**: Compose `spring(dampingRatio = 0.65f, stiffness = 300f)` for expanding from compact $300 \times 430\text{ dp}$ to wide $1054 \times 625\text{ dp}$ / $675 \times 625\text{ dp}$.
3. **Safety Guards**: Complete 5-point focus deactivation guard (`!isPinned && !isShowingTransition && !isPairingActive && !isExpanded && !isModalDialogOpen`).
4. **Interaction Layer**: 3-phase drag tracking with $5\text{ px}$ deadzone, high-DPI scaling, $20\text{ px}$ magnetic boundary snapping, contraction clamping, and $450\text{ ms}$ atomic double-click reset.
5. **Panels & Quick Actions**: 4 tactile quick action buttons, expandable SAF / Transfer History File Explorer, comprehensive Settings panel, QR/PIN pairing view, and 2-stage Exit Engine confirmation.
6. **Styling**: $34\text{ dp}$ corner radius, DarkTheme palette (`#16121A`, `#2B2631`, `#0AE66D`, `#FF453A`), Skia Gaussian drop shadow with $\sigma = \text{radius} / 2.0\text{f}$ and GC allocation hoisting.

---

## Verification Method

1. **Authoritative XAML / PowerShell Inspection**:
   - Verify window properties: `view_file` on `w:\CodeDeX\DeX\MSIX_Source\Themes\MainWindow.xaml` lines 1–7.
   - Verify layout and dimensions: `view_file` on `w:\CodeDeX\DeX\MSIX_Source\Themes\MainWindow.xaml` lines 25–45.
   - Verify animation parameters: `view_file` on `w:\CodeDeX\DeX\MSIX_Source\Themes\AppStyles.xaml` lines 111–291.
   - Verify positioning & tray logic: `view_file` on `w:\CodeDeX\DeX\MSIX_Source\bin\Modules\Bindings_Tray.ps1` lines 46–95.
   - Verify drag, DPI, and deactivation: `view_file` on `w:\CodeDeX\DeX\MSIX_Source\bin\Modules\Bindings_Window.ps1` lines 330–612.
   - Verify File Explorer & SAF: `view_file` on `w:\CodeDeX\DeX\MSIX_Source\bin\Modules\Bindings_FileBrowser.ps1` lines 250–540.
2. **Compose Desktop Codebase Validation**:
   - Verify Kotlin sources: `w:\CodeDeX\DeX\DeX\composeApp\src\desktopMain\kotlin\com\dexstudios\dex\`
   - Check build target: `./gradlew :composeApp:compileKotlinDesktop` (when executing compilation).
