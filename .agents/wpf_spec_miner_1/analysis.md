# WPF Floating Card UI & Logic — Deep Technical Specification Analysis

## Executive Summary
This document provides the complete, authoritative, reverse-engineered technical specification for the WPF / C# floating docked card interface found in the DeX codebase (`MSIX_Source/Themes/MainWindow.xaml`, `MSIX_Source/Themes/AppStyles.xaml`, `MSIX_Source/bin/Modules/*.ps1`, and `DeXShareTarget`).

---

## 1. Bottom-Right Docking & Geometry

### 1.1 Canvas and Window Geometry
The root WPF window acts as a transparent, frameless canvas within which the card (`mainBorder`) is positioned and animated:
- **Canvas Size**: `Width = 1420`, `Height = 760` (`ResizeMode = NoResize`)
- **Card Placement in Canvas**: `HorizontalAlignment = Right`, `VerticalAlignment = Top`, `Margin = 25`
- **Contracted Card Dimensions**: Default `Width = 300px` (actual layout width), `Height = 430px` to `500px` (auto-sized to content).
- **Physical Content Screen Position Formula**:
  $$\text{contentLeft} = \text{Window.Left} + \text{winWidth} - 25 - \text{contentWidth}$$
  $$\text{contentTop} = \text{Window.Top} + 25$$

### 1.2 Initial Tray-Click Placement Logic (`Bindings_Tray.ps1` L46-59)
When triggered from the tray icon or reset to default:
```powershell
$workArea = [System.Windows.SystemParameters]::WorkArea
$winWidth = 1420
$contentH = if ($mb.ActualHeight -gt 0) { $mb.ActualHeight } else { 430 }

$left = $workArea.Right - $winWidth + 13
$top  = $workArea.Bottom - $contentH - 38

# Work area boundary clamping
if ($left -lt $workArea.Left) { $left = $workArea.Left - 13 }
if ($top  -lt $workArea.Top)  { $top  = $workArea.Top  - 13 }

$window.Left = $left
$window.Top  = $top
```
- **Right Gap**: `13px` from work area right boundary.
- **Bottom Gap**: `38px` above taskbar (compensates for taskbar margin & shadow).
- **Canvas Offset**: Window.Left aligns the rightmost margin `25px` of the `1420px` canvas with `workArea.Right - 13px`.

### 1.3 Per-Frame DPI & Multi-Monitor Dragging (`Bindings_Window.ps1` L1-465)
- **Win32 P/Invoke**:
  ```csharp
  [DllImport("user32.dll")] public static extern bool GetCursorPos(out POINT lpPoint);
  [DllImport("user32.dll")] public static extern uint GetDpiForWindow(IntPtr hwnd);
  [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X; public int Y; }
  ```
- **3-Phase Drag Model**:
  1. **Phase 1: Dead Zone Check**: Drag does not initiate until cursor Manhattan delta $|\Delta X| + |\Delta Y| \ge 5\text{px}$.
  2. **Phase 2: Commit**: First frame past $5\text{px}$ dead zone captures `dragStartCursorX/Y`, sets `$script:hasBeenDragged = $true`, and fades `dragPillAccent` (Opacity $1 \to 0$ over $150\text{ms}$).
  3. **Phase 3: Active Drag & Magnetism**:
     - Queries `GetDpiForWindow(hwnd)` per frame: $\text{scale} = \text{dpi} / 96.0$.
     - Computes candidate position:
       $$\text{newLeft} = \text{dragContentLeft} + \frac{\Delta X}{\text{scale}}$$
       $$\text{newTop} = \text{dragContentTop} + \frac{\Delta Y}{\text{scale}}$$
     - **Magnetism Snap Distance**: `snap = 20px`.
       - If $\text{newTop} - \text{wa.Top} < 20\text{px} \implies \text{newTop} = \text{wa.Top}$
       - If $\text{wa.Bottom} - (\text{newTop} + \text{ch}) < 20\text{px} \implies \text{newTop} = \text{wa.Bottom} - \text{ch}$
       - If $\text{newLeft} - \text{wa.Left} < 20\text{px} \implies \text{newLeft} = \text{wa.Left}$
       - If $\text{wa.Right} - (\text{newLeft} + \text{cw}) < 20\text{px} \implies \text{newLeft} = \text{wa.Right} - \text{cw}$
  4. **Snap Animation on Release**: If snapped to an edge, animates smoothly to target over $120\text{ms}$ with `CubicEase(EaseOut)`:
     ```powershell
     $animX = New-Object DoubleAnimation -Property @{ From = $window.Left; To = $snapWinLeft; Duration = "0:0:0.12"; EasingFunction = $ease; FillBehavior = 'Stop' }
     ```
  5. **Off-Screen Sanity Clamping**: Keeps at least $\max(\text{cw} \times 0.2, 60\text{px})$ reachable inside `WorkArea`.

### 1.4 Double-Click Reset
Double-clicking `dragPill` (when `$script:hasBeenDragged == true` and not pinned) animates the window back to bottom-right resting position using `BouncyEase` over $450\text{ms}$. If pinned, performs a 3-iteration shake animation ($5\text{px}, 0, -5\text{px}, 0$) over $50\text{ms}$ per cycle.

### 1.5 Multi-Directional Nudge on Expansion (`UIComponents.ps1` L267-372)
Because the card inside the transparent canvas is right-aligned and top-aligned, expanding the card width naturally grows **leftward**, and expanding height naturally grows **downward**.
If the card is positioned near the left or bottom edge of the screen:
- **Nudge Algorithm (`Nudge-ForExpand`)**:
  - Checks available space:
    $$\text{spaceL} = \text{cLeft} - \text{wa.Left}, \quad \text{spaceR} = \text{wa.Right} - \text{cRight}$$
    $$\text{spaceU} = \text{cTop} - \text{wa.Top}, \quad \text{spaceD} = \text{wa.Bottom} - \text{cBottom}$$
  - $\text{goLeft} = (\text{spaceL} \ge \text{spaceR}) \lor (\text{spaceL} \ge \text{expandW} + 20)$
  - $\text{goDown} = (\text{spaceD} \ge \text{spaceU}) \lor (\text{spaceD} \ge \text{expandH} + 20)$
  - If $\neg \text{goLeft} \implies \text{Window.Left} \mathrel{+}= \text{expandW}$
  - If $\neg \text{goDown} \implies \text{Window.Top} \mathrel{-}= \text{expandH}$
  - Window position change is animated in sync with card expansion over $800\text{ms}$ using `BouncyEase`.
  - `Restore-ExpandPosition` reverses this shift when contracting.

---

## 2. Window Style & Shell Behaviors

### 2.1 Window Configuration (`MainWindow.xaml` L1-7)
| Property | Value | Rationale |
|---|---|---|
| `WindowStyle` | `None` | Eliminates OS window borders, caption bar, system menu |
| `Background` | `Transparent` | Canvas is invisible; only `mainBorder` renders pixels |
| `AllowsTransparency` | `True` | Direct composition surface for custom rounded corners |
| `Topmost` | `True` | Floats above all normal application windows |
| `ShowInTaskbar` | `False` | Application is tray-resident (no taskbar button) |
| `ResizeMode` | `NoResize` | Manual programmatic sizing and dragging only |
| `Width` / `Height` | `1420` / `760` | Spacious canvas providing bounding box for expansion |

### 2.2 Shell Activation, Foreground Lock & Focus Management (`Connect-Engine.ps1` L438-456)
- **Foreground Win32 Interop**:
  ```csharp
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow); // SW_RESTORE = 9
  ```
- **Auto-Hide on Deactivation (`Bindings_Window.ps1` L587-611)**:
  - Listens to `Window.Deactivated`.
  - **Debounce**: Suppressed if within $200\text{ms}$ of last deactivation or while `$script:isShowingMenu` guard is active ($800\text{ms}$ timer).
  - **Exemptions (Never Auto-Hide when)**:
    1. `FileExplorer.Visibility == Visible`
    2. `SettingsPanel.Visibility == Visible`
    3. `pinViewPanel.Visibility == Visible`
    4. Active pairing session in flight (`$script:activeOutboundPairIp` or `$script:pairWaitTimer`)
- **Topmost Lock During Inbound Pairing**: Temporarily forces `Topmost = true` during phone-initiated PIN requests so users never miss a pairing prompt, and restores prior z-order on pairing termination.

---

## 3. Expand / Collapse States & Animations

### 3.1 State Dimension Map
| State | Width | Height | Easing Function | Duration |
|---|---|---|---|---|
| **Contracted (Default)** | $300\text{px}$ | $430\text{px}$ (auto, max $500\text{px}$) | N/A | Rest state |
| **File Explorer Expanded** | $+754\text{px}$ ($1054\text{px}$) | $+195\text{px}$ ($625\text{px}-695\text{px}$) | `BouncyEase` (Elastic) | $800\text{ms}$ |
| **Settings Expanded** | $+375\text{px}$ ($675\text{px}$) | $+195\text{px}$ ($625\text{px}-695\text{px}$) | `BouncyEase` (Elastic) | $800\text{ms}$ |
| **Contracting** | Back to contracted | Back to contracted | `BackEase(0.15)` | $600\text{ms}$ (starts at $250\text{ms}$) |

### 3.2 Storyboard Specifications (`AppStyles.xaml`)

#### A. `PopIn` (Card Entrance on Tray Click or Wiggle)
- `winScale.ScaleX`: `From = 0.85`, `To = 1.0`, Duration: $500\text{ms}$, `BouncyEase`
- `winScale.ScaleY`: `From = 0.85`, `To = 1.0`, Duration: $500\text{ms}$, `BouncyEase`
- `winTrans.Y`: `From = 15`, `To = 0`, Duration: $500\text{ms}$, `BouncyEase`
- `mainBorder.Opacity`: `From = 0`, `To = 1.0`, Duration: $150\text{ms}$
- `menuTrans.Y`: `From = 20`, `To = 0`, Duration: $600\text{ms}`, `BouncyEase`
- `menuContentTrans.Y`: `From = 35`, `To = 0`, Duration: $750\text{ms}$, `BouncyEase` (BeginTime: $80\text{ms}$)
- `menuContentPanel.Opacity`: `From = 0`, `To = 1.0`, Duration: $400\text{ms}$ (BeginTime: $80\text{ms}$)

#### B. `ExpandMenu` (File Explorer Expand)
- `mainBorder.Width`: `By = 754`, Duration: $800\text{ms}$, `BouncyEase`
- `mainBorder.Height`: `By = 195`, Duration: $800\text{ms}$, `BouncyEase`
- `fileTrans.X`: `From = 150`, `To = 0`, Duration: $800\text{ms}$, `BouncyEase`
- `FileExplorer.Opacity`: `To = 1.0`, Duration: $600\text{ms}$ (BeginTime: $100\text{ms}$)
- `FileExplorer.Visibility`: `Visible` at $0\text{ms}$
- `SettingsPanel.Visibility`: `Collapsed` at $0\text{ms}$
- `btnCloseMenu.Visibility`: `Visible` at $0\text{ms}$, `Width`: $56\text{px}$, `Opacity`: $0 \to 1$ ($400\text{ms}$, BeginTime: $300\text{ms}$)
- `btnProfileBottom.Visibility`: `Collapsed` at $0\text{ms}$
- `btnProfileTop.Visibility`: `Visible` at $0\text{ms}$, `Opacity`: $0 \to 1$ ($400\text{ms}$, BeginTime: $300\text{ms}$)
- `menuViewsContainer.MaxHeight`: `9999` at $0\text{ms}$
- `menuTrans.X`: `From = -30`, `To = 0`, Duration: $800\text{ms}$, `BouncyEase`

#### C. `ContractMenu` (File Explorer Collapse)
- `FileExplorer.Opacity`: `To = 0`, Duration: $250\text{ms}$
- `FileExplorer.Visibility`: `Collapsed` at $250\text{ms}$
- `mainBorder.Width`: `By = -754`, Duration: $600\text{ms}$, `BackEase(0.15)` (BeginTime: $250\text{ms}$)
- `mainBorder.Height`: `By = -195`, Duration: $600\text{ms}$, `BackEase(0.15)` (BeginTime: $250\text{ms}$)
- `fileTrans.X`: `To = 150`, Duration: $600\text{ms}$, `BackEase(0.15)` (BeginTime: $250\text{ms}$)
- `btnCloseMenu.Width`: `To = 0`, Duration: $300\text{ms}$, `CubicEase`
- `btnCloseMenu.Opacity`: `To = 0`, Duration: $200\text{ms}`
- `btnCloseMenu.Visibility`: `Collapsed` at $300\text{ms}`
- `btnProfileTop.Opacity`: `To = 0`, Duration: $250\text{ms}`, `Visibility = Collapsed` at $300\text{ms}`
- `btnProfileBottom.Visibility`: `Visible` at $550\text{ms}`
- `btnProfileBottom.Opacity`: `From = 0`, `To = 1`, Duration: $350\text{ms}$ (BeginTime: $550\text{ms}`)
- `btnProfileBottomScale.ScaleX/Y`: `From = 0.2`, `To = 1.0`, Duration: $500\text{ms}$, `BouncyEase` (BeginTime: $550\text{ms}`)
- `menuViewsContainer.MaxHeight`: `352` at $850\text{ms}`

#### D. `SlideInPinAnim` / `SlideOutPinAnim` (Pairing Panel Slide)
- `SlideInPinAnim`:
  - `menuContentTrans.X`: `To = -300`, Duration: $250\text{ms}$, `PowerEase(EaseOut)`
  - `menuContentPanel.Opacity`: `To = 0`, Duration: $200\text{ms}`
  - `pinViewTrans.X`: `To = 0`, Duration: $300\text{ms}$, `PowerEase(EaseOut)`
  - `pinViewPanel.Opacity`: `To = 1`, Duration: $250\text{ms}`
  - `pinContentScale.ScaleX/Y`: `From = 0.95`, `To = 1.0`, Duration: $250\text{ms}$, `PowerEase(EaseOut)`
  - `qrContentScale.ScaleX/Y`: `From = 0.95`, `To = 1.0`, Duration: $250\text{ms}$, `PowerEase(EaseOut)`
- `SlideOutPinAnim`:
  - `pinViewTrans.X`: `To = 300`, Duration: $250\text{ms}$, `PowerEase(EaseIn)`
  - `pinViewPanel.Opacity`: `To = 0`, Duration: $200\text{ms}`
  - `menuContentTrans.X`: `To = 0`, Duration: $300\text{ms}$, `PowerEase(EaseOut)`
  - `menuContentPanel.Opacity`: `To = 1`, Duration: $250\text{ms}`

#### E. Easing Function Parameters
- **`BouncyEase`**: `ElasticEase { Oscillations = 1, Springiness = 7, EasingMode = EaseOut }`
- **`PopInEase`**: `BackEase { Amplitude = 3.53, EasingMode = EaseOut }`
- **`HoverEase`**: `BackEase { Amplitude = 1.22, EasingMode = EaseOut }`
- **`SmoothEase`**: `CubicEase { EasingMode = EaseOut }`
- **`PowerEase`**: Standard quadratic power curve (`EasingMode = EaseOut` or `EaseIn`)

---

## 4. Quick Action Buttons

### 4.1 Visual Layout & Components (`MainWindow.xaml` L657-673)
Layout container is a centered horizontal `StackPanel` (`Margin = 0,0,0,12`):

```
┌─────────────────────────────────────────────────────────────┐
│  [  DND  ]   [ Mirror ]   [ Transfers ]   [ Clipboard ]   [X]│
│   (btn)       (btn)         (btn)            (btn)      (btn)│
└─────────────────────────────────────────────────────────────┘
```

| Element Name | Type | Icon Glyph | Tooltip | Command / Action |
|---|---|---|---|---|
| `btnQADnd` | `ToggleButton` | `&#xE711;` (Cancel/X) | "Do Not Disturb" | Toggles DND mode (`/local/dnd?enabled=...`). Declines incoming connections. |
| `btnQAMirror` | `ToggleButton` | `&#xE8EA;` (Phone) | "Mirror Phone" | Toggles WebSocket screen mirroring window (`MirrorWindow.cs`). |
| `btnQAPull` | `ToggleButton` | `&#xE8B7;` (Folder) | "Transfers" | Toggles expansion of File Explorer left panel (`ExpandMenu`). |
| `btnQAClipboard` | `ToggleButton` | `&#xE77F;` (Clipboard) | "Clipboard" | Toggles automatic bidirectional clipboard sync. |
| `btnCloseMenu` | `Button` (Danger) | `&#xE711;` (X) | "Close" | Collapses expanded panel back to compact card (`ContractMenu`). Hidden when contracted. |

### 4.2 Button Sizing & Shapes (`AppStyles.xaml` L612-751)
- **Dimensions**: `Width = 56px`, `Height = 44px`
- **Corner Radius**: `CornerRadius = 20px` (Pill shape)
- **Margin**: `3,0,3,0`
- **Typography**: `FontFamily = "Segoe Fluent Icons, Segoe MDL2 Assets"`, `FontSize = 20px`

### 4.3 Interactive State Transitions (`QuickActionBtn` Style)
- **Resting State**: `Background = AccentBrush` (`#2B2631`), `Foreground = PrimaryTextBrush` (`#FFFFFF`)
- **Hover (`MouseEnter`)**:
  - `btnScale.ScaleX`: `To = 1.08` ($500\text{ms}$, `HoverEase`)
  - `btnScale.ScaleY`: `To = 1.08` ($500\text{ms}$, `HoverEase`)
  - `btnTrans.Y`: `To = -3` ($500\text{ms}$, `HoverEase`)
- **Press (`PreviewMouseDown`)**:
  - `btnScale.ScaleX`: `To = 0.85` ($100\text{ms}$)
  - `btnScale.ScaleY`: `To = 0.85` ($100\text{ms}$)
  - `btnTrans.Y`: `To = +3` ($100\text{ms}$)
- **Checked State (`IsChecked == true`)**:
  - `Background = SecondaryBrush` (`#0AE66D` Neon Green)
  - `Foreground = SecondaryForegroundBrush` (`#000000` Black)
- **Danger Button (`btnCloseMenu` - `DangerQuickActionBtn`)**:
  - Hover: `Background = DangerBrush` (`#FF453A`), `Foreground = White`
  - Press: `Background = #CCFF453A` (80% opacity red)

---

## 5. Embedded File Explorer & Navigation

### 5.1 Architecture & Layout (`MainWindow.xaml` L39-200)
The left-hand expansion panel `FileExplorer` is structured in 3 Grid rows:

```
┌─────────────────────────────────────────────────────────────┐
│ [⬆] [ 🔍 Search transfers...           ] [📁 Mode] [Avatar] │ ← Row 0 (Header)
├─────────────────────────────────────────────────────────────┤
│ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ │
│ │ 📁 Doc1 │ │ 📁 Doc2 │ │ 🖼 Img1 │ │ 🎵 Aud1 │ │ 📄 Txt1 │ │ ← Row 1 (lbFiles WrapPanel)
│ └─────────┘ └─────────┘ └─────────┘ └─────────┘ └─────────┘ │
│ ─────────────────────────────────────────────────────────── │
│                       [Empty State Overlay]                 │
├─────────────────────────────────────────────────────────────┤
│                  ─────────────────────────                  │
│             [ ⬆ Send Files ]     [ 📁 Send Folders ]        │ ← Row 2 (Actions & Floating Docks)
│              [ ⬇ Saved to Downloads\DeX (Change) ]          │
│              [ ⏳ Pulling 3 of 10 files... [====] (X) ]      │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 Header Controls (Row 0)
- **`btnUpDir`**: Circle button (`CornerRadius = 18`, `Padding = 12`), glyph `&#xE898;` (Up/Back arrow).
  - Navigates up in SAF folder tree (strips `%2F` document segment) or Windows folder tree (`Path.GetDirectoryName`).
- **`txtSearch`**: Rounded search bar (`CornerRadius = 20`, `Padding = 14,8`, `Background = AccentBrush`), magnifying glass glyph `&#xE721;`.
  - Placeholder: `"Search transfers..."` (History mode) or `"Search files..."` (SAF mode).
  - Real-time search filter with $150\text{ms}$ DispatcherTimer debounce.
- **`btnToggleExplorerMode`**: Circle button (`CornerRadius = 18`, `Padding = 12`), glyph `&#xE8B7;` (Folder).
  - Toggles between Local Transfer History (`Downloads\DeX`) and Phone SAF Files (`content://...`).

### 5.3 File List Grid & Item Templates (Row 1)
- **`lbFiles` Control**:
  - `SelectionMode = Extended` (supports multi-select, Ctrl+A, Shift+Click)
  - `ItemsPanel`: `WrapPanel` (`Orientation = Horizontal`)
  - `ScrollViewer.VerticalScrollBarVisibility = Hidden`
  - Item size: `Width = 100px`, `Height = 105px`, `Margin = 6px`
  - Item `CornerRadius = 8px`
- **`FolderGridTemplate`**:
  - Big icon: `&#xE8B7;` (`FontSize = 42`, `Foreground = SecondaryBrush`)
  - Title: `Name` (`FontSize = 12`, `FontWeight = Medium`, `MaxHeight = 35`, `TextWrapping = Wrap`)
  - Subtitle: `Meta` (`FontSize = 10`, `Foreground = SecondaryTextBrush`, `Opacity = 0.7`)
- **`FileGridTemplate`**:
  - Thumbnail box: $48 \times 48\text{px}$ with `RadiusX="4" RadiusY="4"` clip.
  - Fallback icon if no thumbnail: `&#xE7C3;` (`FontSize = 42`, `Foreground = SecondaryTextBrush`)
  - Title & Meta same as folder template.
- **Item Hover & Press Micro-Animations**:
  - MouseEnter: ScaleX/Y $\to 1.05$, TranslateY $\to -2\text{px}$ ($300\text{ms}$, `HoverEase`)
  - PreviewMouseDown: ScaleX/Y $\to 0.94$ ($100\text{ms}$)
  - PreviewMouseUp: ScaleX/Y $\to 1.05$ ($300\text{ms}$)
  - MouseLeave: ScaleX/Y $\to 1.0$, TranslateY $\to 0$ ($400\text{ms}$)
- **Context Menu (`TransferContextMenu`)**:
  - Open (`CtxOpen`), Open Containing Folder (`CtxOpenFolder`), Copy Path (`CtxCopyPath`), Delete (`CtxDelete` in `DangerBrush`).

### 5.4 Drag and Drop & Transfer Docks (Row 2)
- **`FileExplorer.PreviewDrop` / `PreviewDragOver`**: Accepts external Windows shell drag-and-drop (`DataFormats.FileDrop`). Automatically traverses folders and launches `DeXShareTarget.exe -IP <target> <files>`.
- **`dockDownloadToast` (Destination Pill)**:
  - Floating pill (`CornerRadius = 20`, `Padding = 14,8`, `Background = AccentBrush`).
  - Animates in from bottom ($Y: 25 \to 0$, Scale: $0.8 \to 1.0$, Opacity: $0 \to 1.0$, $450\text{ms}$, `BackEase(0.6)`).
  - Auto-hides after $4\text{s}$ unless hovered.
- **`dockPullProgress` (Live Progress Dock)**:
  - Width: $360\text{px}$, `CornerRadius = 16`, `Padding = 14,10`.
  - Displays file count progress ("Pulling 3 of 10 files..."), progress bar (`Height = 4`, `Foreground = SecondaryBrush`), and Cancel button (`btnCancelPull`, `&#xE711;`).

---

## 6. Design Tokens & Styling Constants

### 6.1 Color Palette Matrix

| Token Name | Dark Theme Hex | Light Theme Hex | Usage Context |
|---|---|---|---|
| `PrimaryBrush` | `#16121A` | `#FFFFFF` | Main card background, ContextMenu background |
| `AccentBrush` | `#2B2631` | `#F2F2F7` | Card borders, button backgrounds, separators, search bar |
| `PrimaryTextBrush` | `#FFFFFF` | `#000000` | Primary titles, active text, entered PIN digits |
| `SecondaryTextBrush` | `#A0A0A0` | `#3A3A3C` | Subtitles, descriptions, inactive icons, timestamps |
| `SecondaryBrush` | `#0AE66D` | `#0AE66D` | Neon green active state, online badges, progress bars, toggle active |
| `SecondaryForegroundBrush` | `#000000` | `#000000` | Text/icons rendered on top of `SecondaryBrush` |
| `DangerBrush` | `#FF453A` | `#FF3B30` | Exit engine, delete, close button hover, DND active |
| `SecondaryHoverBrush` | `#2B2631` | `#E5E5EA` | List item hover background |
| `SecondarySelectedBrush` | `#332D3B` | `#D1D1D6` | List item selected background |
| `SecondarySelectedHoverBrush` | `#3D3647` | `#C7C7CC` | List item selected + hover background |
| `SecondarySelectedBorderBrush` | `#0AE66D` | `#0AE66D` | Selected item outline border |

### 6.2 Geometry & Corner Radii

| Component | Corner Radius | Dimensions | Border |
|---|---|---|---|
| **Main Card (`mainBorder`)** | `34px` | $300\text{px}$ (contracted) $\to 1054\text{px}$ (expanded) | `1px AccentBrush` |
| **Settings / Inner Groups** | `16px` | Variable width, auto height | `0px` or `1px AccentBrush` |
| **List Items / Device Items** | `12px` | Full width $\times 48\text{px}-56\text{px}$, `Padding = 16,10` | `0px` (normal), `1px #0AE66D` (selected) |
| **Quick Action Buttons** | `20px` (pill) | $56 \times 44\text{px}$ | `0px` |
| **Grid Items (Files/Folders)**| `8px` / `10px` | $100 \times 105\text{px}$, `Padding = 4` | `1px` (on selection) |
| **Drag Pill Handle** | `2px` | Handle area $66 \times 16\text{px}$, bar $66 \times 4\text{px}$ | `0px` |
| **Topmost Pin Toggle** | `4px` | $16 \times 16\text{px}$ | `0px` |
| **Search Bar** | `20px` | Auto width $\times 36\text{px}$, `Padding = 14,8` | `0px` |
| **PIN Digit Container** | `8px` | $\text{MinWidth} = 44\text{px}, \text{MinHeight} = 56\text{px}$ | `2px` (Transparent $\to$ Green/Red/Shimmer) |
| **QR Code Container** | `8px` | $164 \times 164\text{px}$, `Padding = 12` | `0px` (White background) |
| **Context Menu** | `16px` | Auto width, `Padding = 6` | `1px AccentBrush` |

### 6.3 Typography
- **Primary Font Family**: `Segoe UI`
- **Icon Font Family**: `Segoe Fluent Icons, Segoe MDL2 Assets`
- **Monospace Font**: `Consolas` (used for `⌘Q` shortcut badge)
- **Font Sizes & Weights**:
  - Window Title / PIN Title: `18px SemiBold`
  - PIN Digits: `32px Bold`
  - Section Headers ("Discovered Devices", "Your Devices"): `13px SemiBold`
  - Device Alias / Primary Labels: `15px Medium`
  - Subtext / Telemetry / Metadata: `12px` to `13px Regular`
  - Badges / Micro-labels: `10px` to `11px Bold / SemiBold`

---

## 7. Advanced Features & State Machines

### 7.1 Real-Time Interactive PIN Digit Sync (`UIComponents.ps1` L582-798)
- Synchronizes phone digit entry keystrokes in real-time ($250\text{ms}$ polling interval).
- **Shimmer Entrance Animation**: When digit index $i < \text{digitCount}$, applies an animated `LinearGradientBrush` shimmering from $-1.0$ to $1.0$ over $800\text{ms}$ (repeat 2x) with a $1.15\times$ scale pop ($100\text{ms}$).
- **Wrong PIN / Error Handling**: When digitCount signals $-1$ or status is `Rejected`/`Failed`, turns all digit borders `Red` and triggers a 5-keyframe horizontal shake animation ($\pm 10\text{px}$ over $400\text{ms}$).

### 7.2 Wiggle-to-Open Gesture (`Bindings_Wiggle.ps1` L1-155)
- Background mouse tracking ($50\text{ms}$ sample rate / $20\text{Hz}$).
- Analyzes trailing 1-second cursor trajectory ($20$ samples).
- Counts horizontal velocity direction reversals ($\Delta X > 5\text{px}$).
- **Trigger Condition**: $\ge 3$ reversals within a localized area $< 150\text{px}$ while dragging.
- Automatically centers and pops in the floating card around the cursor, clamped to the target screen's working area.

---

## Features Discovered

| # | Category | Feature | Description | Inputs | Outputs | Error Behavior | Discovered Via |
|---|---|---|---|---|---|---|---|
| 1 | Docking & Geometry | WorkArea Taskbar Docking | Positions window in bottom-right corner above taskbar with exact gaps | Tray Click / Init | `Window.Left = workArea.Right - 1420 + 13`, `Window.Top = workArea.Bottom - contentH - 38` | Clamps to `workArea.Left - 13`, `workArea.Top - 13` | `Bindings_Tray.ps1:46-59` |
| 2 | Docking & Geometry | Per-Frame DPI Scaled Dragging | Drags card with Win32 `GetCursorPos` and `GetDpiForWindow` | Mouse Drag on `dragPill` | Repositions `Window.Left/Top` adjusted for DPI ($DPI/96.0$) | Ignores movements $< 5\text{px}$ (dead zone) | `Bindings_Window.ps1:1-465` |
| 3 | Docking & Geometry | 4-Edge Magnetic Snapping | Snaps card magnetically when within $20\text{px}$ of monitor work area edges | Cursor near work area bounds | Snaps position & triggers $120\text{ms}$ `CubicEase` settle animation | Restores to nearest valid bounds if off-screen | `Bindings_Window.ps1:438-530` |
| 4 | Docking & Geometry | Multi-Directional Nudge | Shifts window position if expansion would exceed monitor bounds | Panel expand event | Shifts window X/Y over $800\text{ms}$ with `BouncyEase` | Sanity clamped against screen edges | `UIComponents.ps1:267-341` |
| 5 | Window Style | Frameless Transparent Canvas | Undecorated $1420 \times 760$ transparent canvas containing styled $34\text{px}$ card | XAML initialization | Transparent window, no taskbar presence, Topmost | Degrades to WinForms tray menu if XAML fails | `MainWindow.xaml:1-7`, `Connect-Engine.ps1:198-276` |
| 6 | Window Style | Smart Auto-Hide Focus Guard | Auto-hides on `Deactivated` unless expanded, pairing, or during $800\text{ms}$ pop-in | Window focus loss | `Window.Hide()`, state reset | Preserves visibility during modal states | `Bindings_Window.ps1:587-611` |
| 7 | Animations | PopIn Entrance Tween | Scale ($0.85 \to 1.0$) and parallax slide on card open | Tray Click / Wiggle | Smooth spring bounce ($500\text{ms}$ `ElasticEase`) | Stops prior storyboard if re-triggered | `AppStyles.xaml:281-291` |
| 8 | Animations | Parallax File Explorer Expand | Expands card width ($+754\text{px}$) and height ($+195\text{px}$) with sliding explorer | `btnQAPull` Click | Smooth expansion over $800\text{ms}$ | Collapses Settings first if open | `AppStyles.xaml:115-151` |
| 9 | Animations | Parallax Settings Expand | Expands card width (to $675\text{px}$) and height ($+195\text{px}$) with sliding settings | Avatar Click | Smooth expansion over $800\text{ms}$ | Collapses FileExplorer first if open | `AppStyles.xaml:200-233` |
| 10 | Quick Actions | 4-Pill Action Toolbar | DND, Mirror Phone, File Transfers, and Clipboard 2-way sync | Button clicks | Visual state toggles, triggers services | Shows error toasts if service unavailable | `MainWindow.xaml:657-673` |
| 11 | File Explorer | Dual Mode File Browser | Toggles between Local History (`Downloads\DeX`) and Phone SAF folders | Mode button click | Updates `lbFiles` with files/folders | Prompts phone folder grant if none granted | `Bindings_FileBrowser.ps1:294-420` |
| 12 | File Explorer | External File Drag-and-Drop | Handles files dropped onto File Explorer panel | `FileDrop` payload | Initiates push transfer via `DeXShareTarget.exe` | Sets `Effects = None` if invalid format | `Bindings_FileBrowser.ps1:562-596` |
| 13 | File Explorer | Async Pull with Progress Dock | Pulls files from phone with live progress bar and cancellation | Double click on items | `dockPullProgress` popup, HTTP poll job | Displays stalled/cancelled toasts | `Bindings_FileBrowser.ps1:26-248` |
| 14 | Pairing / Security | QR Code & Real-Time PIN Sync | Animated QR code display and real-time keystroke digit box sync | Device click | Interactive digit shimmer, shake on error | 60s timeout with automatic expiry toast | `UIComponents.ps1:374-798` |
| 15 | Interaction | Wiggle-to-Open Gesture | Opens floating card centered at mouse cursor on wiggle | Cursor oscillation ($20\text{Hz}$) | Card pops in at cursor location | Disabled when toggled off in settings | `Bindings_Wiggle.ps1:1-155` |

---

## Edge Cases

| # | Feature | Input | Observed Behavior |
|---|---|---|---|
| 1 | Double-Click Reset | Pill double-clicked while Location Pinned | Shakes topmost pin button ($5\text{px}, 0, -5\text{px}, 0$ for 3 cycles) instead of moving window (`Bindings_Window.ps1:266-273`). |
| 2 | Drag Boundary | Dragged far off-screen | Clamps so that at least $20\%$ of content width ($\min 60\text{px}$) remains visible and grab-able on screen (`Bindings_Window.ps1:540-556`). |
| 3 | Rapid Tray Clicking | Multiple tray clicks $< 300\text{ms}$ apart | Debounced: ignores secondary clicks to prevent flickering animation loops (`Bindings_Tray.ps1:7-11`). |
| 4 | Panel Swapping | Opening Settings while File Explorer is open | Instantly resets pre-expand position and collapses File Explorer without contracting the outer window (`TrayUIHandlers.ps1:340-357`). |
| 5 | Deactivation Guard | Click outside while PIN / QR screen is active | Window stays open and active; only clicking Cancel or Escape dismisses the pairing session (`Bindings_Window.ps1:596-601`). |
| 6 | Exit Engine with Transfer | Exit clicked while file pull or mirror active | Button expands left (`Margin = -62,0,0,0`), avatar shrinks to $0.6\times$, text prompts: "Transfer Active! Click to Force Exit" for 3 seconds (`Bindings_Window.ps1:33-128`). |
| 7 | Keyboard Esc Handling | Esc pressed with focused search box | Clears search text if present; if empty, clears focus without closing window (`Bindings_Window.ps1:178-195`). |
| 8 | Dangerous File Execution | Double-click or open on `.exe`, `.bat`, `.ps1`, `.vbs` | Opens Windows Explorer with `/select,"<path>"` instead of executing directly (`Bindings_FileBrowser.ps1:469-482`). |
| 9 | Corrupted/Missing XAML | XAML parsing fails at startup | Degrades immediately to a fallback WinForms `ContextMenuStrip` on the tray icon (`Connect-Engine.ps1:217-276`). |
| 10 | High-Res Frame Streaming | Mirroring frames arrive faster than UI render | Drops in-flight pending frames (`_framePending`) to prevent latency queues (`MirrorWindow.cs:41-64`). |
