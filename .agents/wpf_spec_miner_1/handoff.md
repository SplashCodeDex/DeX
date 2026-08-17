# Handoff Report — WPF Floating Card UI & Logic Spec Miner

## 1. Observation
Directly analyzed and reverse-engineered the entire WPF/C# and PowerShell UI subsystem in the workspace:
- **`MSIX_Source/Themes/MainWindow.xaml`** (Lines 1–1064): Defined the root frameless window (`WindowStyle="None"`, `AllowsTransparency="True"`, `Topmost="True"`, `ShowInTaskbar="False"`, `Width="1420"`, `Height="760"`), the primary card border (`mainBorder` with `CornerRadius="34"`, `BorderThickness="1"`), the expandable `FileExplorer` (Lines 40–200), the expandable `SettingsPanel` (Lines 203–473), the 4-pill quick actions bar (`btnQADnd`, `btnQAMirror`, `btnQAPull`, `btnQAClipboard`, `btnCloseMenu`, Lines 657–673), the `dragPill` handle (Lines 650–656), the dynamic device lists (`icUdpPeers` and `icLivePeers`, Lines 721–864), and the pairing PIN/QR panel (`pinViewPanel`, Lines 964–1053).
- **`MSIX_Source/Themes/AppStyles.xaml`** (Lines 1–1047): Extracted all animation storyboards (`PopIn`, `ExpandMenu`, `ContractMenu`, `ExpandSettings`, `ContractSettings`, `SlideInPinAnim`, `SlideOutPinAnim`, `SwitchQrToPinAnim`, `SwitchPinToQrAnim`), easing curves (`BouncyEase = ElasticEase(1, 7)`, `HoverEase = BackEase(1.22)`, `PopInEase = BackEase(3.53)`, `SmoothEase = CubicEase`), control templates, and hover/press micro-interactions.
- **`MSIX_Source/Themes/DarkTheme.xaml` & `LightTheme.xaml`** (Lines 1–24): Mapped complete design tokens (`PrimaryBrush = #16121A` / `#FFFFFF`, `AccentBrush = #2B2631` / `#F2F2F7`, `SecondaryBrush = #0AE66D`, `DangerBrush = #FF453A` / `#FF3B30`).
- **`MSIX_Source/bin/Modules/Bindings_Window.ps1`** (Lines 1–759): Discovered Win32 P/Invoke declarations (`GetCursorPos`, `GetDpiForWindow`), per-frame DPI adjustment formula (`dpi / 96.0`), 5px drag dead zone, 20px 4-edge magnetic work area snapping, 120ms snap animation, double-click reset to bottom-right, exit engine confirmation with transfer protection, and deactivation auto-hide filters.
- **`MSIX_Source/bin/Modules/Bindings_Tray.ps1`** (Lines 1–99): Discovered work area bottom-right docking algorithm (`Left = workArea.Right - 1420 + 13`, `Top = workArea.Bottom - contentH - 38`), 300ms double-click debounce, and PopIn initial state initialization.
- **`MSIX_Source/bin/Modules/UIComponents.ps1`** (Lines 1–891): Discovered `Nudge-ForExpand` multi-directional expansion algorithm, `Restore-ExpandPosition`, real-time PIN digit sync with shimmer LinearGradientBrush animation and 5-keyframe error shake, 60s timeout handling, and toast notification dispatchers.
- **`MSIX_Source/bin/Modules/Bindings_FileBrowser.ps1`** (Lines 1–597): Discovered dual-mode architecture (Local Transfer History vs Android SAF folders), async folder grant flow, async pull progress dock (`dockPullProgress`), drag-and-drop external file handler, double-click batch pulling, and dangerous extension execution shields (`.exe`, `.bat`, `.ps1` opened in Explorer).
- **`MSIX_Source/bin/Modules/Bindings_Wiggle.ps1`** (Lines 1–155): Discovered 20Hz background cursor oscillation detector triggering card PopIn at cursor position when $\ge 3$ reversals occur in $<150\text{px}$.
- **`DeXShareTarget/TransferWindow.cs` & `Windows/MirrorWindow.cs`**: Discovered companion WPF utility windows for file transfer progress and low-latency JPEG screen mirror streaming.

## 2. Logic Chain
1. **Docking Geometry**:
   - The root window is an undecorated $1420 \times 760$ canvas (`WindowStyle=None`, `AllowsTransparency=True`).
   - The physical card inside is right-aligned and top-aligned with `Margin=25`.
   - To place the card $13\text{px}$ from the right screen boundary and $38\text{px}$ above the taskbar, the window top-left is positioned at `workArea.Right - 1420 + 13` and `workArea.Bottom - contentH - 38`.
   - When dragged, Win32 `GetDpiForWindow` adjusts cursor deltas so drag distance is identical across monitors with different DPI scalings.
   - Snapping checks against the per-monitor `Screen.FromPoint(cursor).WorkingArea` with a $20\text{px}$ threshold.
2. **Expansion Mechanics**:
   - Expansion increases card width by $+754\text{px}$ (File Explorer) or $+375\text{px}$ (Settings) and height by $+195\text{px}$.
   - Because the card is right-aligned inside the canvas, width growth expands leftward toward screen center.
   - If the window is near the left edge, `Nudge-ForExpand` calculates if available space is $< \text{expandW} + 20\text{px}$ and translates `Window.Left` by $+\text{expandW}$ in sync with the $800\text{ms}$ `BouncyEase` expansion.
3. **Quick Action Bar & Interaction**:
   - Quick action pills are $56 \times 44\text{px}$ with $20\text{px}$ corner radius.
   - The close button (`btnCloseMenu`) is dynamically revealed only when the card is expanded, collapsing it back to $300\text{px}$ with a $600\text{ms}$ staggered contraction.

## 3. Caveats
- The legacy WPF engine used PowerShell 5.1 / WPF XAML with code-behind modules dot-sourced at runtime; Compose Multiplatform Desktop will implement these behaviors in idiomatic Kotlin/Compose using `Window(undecorated = true, transparent = true, alwaysOnTop = true)` and Skiko / AWT interop.
- DropShadowEffect on the WPF `mainBorder` was set to `BlurRadius=0, Opacity=0` in XAML because WPF software rasterization on transparent windows caused performance drops; in Compose Desktop (GPU accelerated via Skia/Skiko), full shadow rendering and backdrop blur (via Kyant `backdrop` library or Skia shaders) can be natively rendered with 60+ FPS.

## 4. Conclusion
The entire WPF floating card UI/UX design, math, geometry, styling tokens, animations, and interaction models have been fully mined, cataloged, and documented in detail in `W:\CodeDeX\DeX\.agents\wpf_spec_miner_1\analysis.md`. The findings provide a complete 1:1 blueprint for the Compose Desktop migration.

## 5. Verification Method
- **Inspect `analysis.md`**: Verify that all 15 discovered features, 10 edge cases, exact geometry formulas, XAML storyboards, easing parameters, and color hex tokens match `MSIX_Source/Themes/MainWindow.xaml`, `AppStyles.xaml`, and `Bindings_*.ps1`.
- **Review WPF Files**:
  - `MainWindow.xaml`: Check L1–7 (Window style), L25 (Card margin/radius), L657–673 (Quick action bar).
  - `AppStyles.xaml`: Check L111–291 (Storyboards and easing parameters).
  - `Bindings_Window.ps1`: Check L1–11 (Win32 P/Invoke), L46–58 (Docking math), L262–566 (Drag & Snapping).
  - `UIComponents.ps1`: Check L267–372 (Multi-directional nudge).
