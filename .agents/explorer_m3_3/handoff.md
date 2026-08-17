# Milestone 3 Investigation Report: FileExplorerPanel & SettingsPanel Architecture

## 1. Observation

### 1.1 Backend Storage, SAF Integration, and Transfer History in `core` and `feature`

Direct observations across the existing codebase:

1. **Persistent Transfer History Model (`core:data`)**:
   - In `core/data/src/commonMain/kotlin/com/dexstudios/dex/network/TransferHistory.kt`:
     - Lines 24–33 define `TransferRecord`:
       ```kotlin
       @Serializable
       data class TransferRecord(
           val id: String,
           val name: String,
           val size: Long,
           val timestamp: Long,
           val direction: String, // "sent" or "received"
           val uri: String? = null,
           val peerDevice: String? = null,
           val status: String = "success" // "success" or "failed"
       )
       ```
     - Lines 35–48: `TransferHistory` is a singleton `KoinComponent` reading from DataStore with `KEY_TRANSFERS = stringPreferencesKey("transfers")` (capped at 200 entries). Exposes `val items: StateFlow<List<TransferRecord>> = _items.asStateFlow()`.
     - Operations provided: `init()`, `refresh()`, `delete(id)`, `clear()`, `log(...)`.

2. **Server-Side SAF Routing (`core:network`)**:
   - In `core/network/src/jvmMain/kotlin/com/dexstudios/dex/network/server/routes/FileExplorerRoutes.kt`:
     - Line 21: `post("/list-folders")` sends `{"type": "list-shared-folders", "requestId": ...}` to the connected phone device via `WebSocketConnectionManager.sendRequest(fp, ...)` and awaits deferred result from `DexRequestStore` (25s timeout).
     - Line 51: `post("/browse")` with query parameters `fingerprint` and `folderUri` sends `{"type": "browse-folder", "requestId": ..., "folderUri": ...}` (25s timeout).
     - Line 83: `post("/grant-folder")` sends `{"type": "grant-shared-folder", "requestId": ...}` with a 190s timeout (allowing mobile user to select directory via Android SAF document picker).

3. **Device Configuration & Google Identity (`core:data`)**:
   - In `core/data/src/commonMain/kotlin/com/dexstudios/dex/network/DeviceConfig.kt`:
     - Lines 23–27: `GoogleProfile(name, picture, email)`
     - Lines 65–70: `val googleProfileFlow: StateFlow<GoogleProfile>` combines name, picture, and email flows.
     - Lines 42–43: `ALIAS_KEY`, `CLIPBOARD_SYNC_ENABLED_KEY`, `EMAIL_KEY`, `FINGERPRINT_KEY`.
     - Methods: `signOut()`, `setGoogleProfile(name, picture)`, `setGoogleSub(sub)`, `alias`, `email`, `clipboardSyncEnabled`.

4. **Existing Desktop Stubs & Layout Structure (`composeApp`)**:
   - In `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/DockCardContent.kt`:
     - Lines 48–56: `cardWidth` animates to `1054.dp` (FileExplorer), `675.dp` (Settings), `400.dp` (Pairing), or `300.dp` (Contracted).
     - Lines 59–63: `cardHeight` animates to `625.dp` (Expanded) or `430.dp` (Contracted).
     - Lines 77–100:
       ```kotlin
       AnimatedVisibility(
           visible = controller.isExpanded,
           enter = slideInHorizontally(
               initialOffsetX = { it },
               animationSpec = DockCardPhysics.ElasticIntOffsetSpec
           ) + fadeIn(animationSpec = DockCardAnimations.SmoothEase),
           exit = slideOutHorizontally(
               targetOffsetX = { it },
               animationSpec = DockCardPhysics.ElasticIntOffsetSpec
           ) + fadeOut(animationSpec = DockCardAnimations.SmoothEase),
           modifier = Modifier.weight(1f).fillMaxSize()
       ) {
           Box(modifier = Modifier.fillMaxSize()) {
               when (controller.expandedPanel) {
                   ExpandedPanel.FileExplorer -> FileExplorerPanel()
                   ExpandedPanel.Settings -> SettingsPanel()
                   ExpandedPanel.Pairing -> PinPairingPanel(
                       pairingEngine = pairingEngine,
                       onClose = { controller.collapsePanel() }
                   )
                   else -> {}
               }
           }
       }
       ```
   - In `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/ExpandedPanel.kt`:
     - Lines 6–10:
       ```kotlin
       enum class ExpandedPanel {
           FileExplorer,
           Settings,
           Pairing
       }
       ```
   - In `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/components/FileExplorerPanel.kt`:
     - Currently a rudimentary 156-line placeholder with un-debounced static search text, static empty state, no grid cards, no double-click guard, no dangerous extension protection, and no `PullProgressDock`.
   - In `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/components/SettingsPanel.kt`:
     - Currently a 282-line partial static mock using hardcoded strings, missing live DataStore / ViewModel state wiring, missing OS path picker integration for Download Location, and missing DND / ADB toggles.

5. **Legacy WPF / PowerShell Reference Implementation (`MSIX_Source`)**:
   - In `MSIX_Source/Themes/MainWindow.xaml`:
     - Lines 40–200 define the WPF `FileExplorer` grid (Row 0: `btnUpDir`, `txtSearch`, `btnToggleExplorerMode`; Row 1: `lbFiles` WrapPanel, `emptyFolderState`; Row 2: `btnPushFiles`, `btnPushFolder`, `dockDownloadToast`, `dockPullProgress`).
     - Lines 203–450 define `SettingsPanel` (Profile Header, Sign Out, Connection DND, Dev Tools ADB, Identity Google Sign-In, Appearance Theme, Interaction Wiggle, Storage Download Path, About DeX & Reset Identity).
   - In `MSIX_Source/bin/Modules/Bindings_FileBrowser.ps1`:
     - Lines 26–131: `dockPullProgress` implementation with 250ms polling timer, progress percentage computation, cancellation handler (`Invoke-RestMethod /local/dex/pull-cancel`), and completion toast.
     - Lines 251–292: `btnUpDir` logic stripping `%2F` for SAF `content://` document URIs or parent path for Windows paths (`^[A-Za-z]:\\`).
     - Lines 443–538: `lbFiles.Add_MouseDoubleClick` with **400ms delta filter guard**, folder drill-down, **dangerous file protection** (`.exe`, `.bat`, `.cmd`, `.ps1`, `.vbs`, `.vbe`, `.msi`, `.scr`, `.com`, `.pif`, `.wsf` launched with `explorer.exe /select,"<path>"`), and phone pull dispatch.
   - In `MSIX_Source/bin/Modules/Bindings_Search.ps1`:
     - Lines 21–36: `txtSearch` with **150ms debounce timer**, mode-dependent placeholder switching (`"Search transfers..."` vs `"Search files..."`).

---

## 2. Logic Chain

### 2.1 Complete FileExplorerPanel Architectural Specification

`FileExplorerPanel` occupies the expandable left drawer ($754\text{ dp}$ additional width, expanding total card to $1054 \times 625\text{ dp}$) and operates in two distinct browsing modes:
1. **Local Transfer History Mode**: Browses local files in `%USERPROFILE%\Downloads\DeX` synced with `TransferHistory.items`.
2. **Phone SAF File Explorer Mode**: Queries the connected phone via `/local/dex/list-folders` and `/local/dex/browse` to navigate granted Document Provider directories.

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ [⬆ btnUpDir]  [ 🔍 txtSearch Pill (150ms debounce) ]  [📁 btnToggleMode]  [Avatar]    │  ← Row 0: Top Nav (Height Auto)
├────────────────────────────────────────────────────────────────────────────────────────┤
│ ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                 │
│ │  📁 Doc 1    │  │  🖼 Img 1    │  │  🎵 Audio 1  │  │  📄 Text 1   │                 │  ← Row 1: LazyVerticalGrid
│ │  48x48 Thumb │  │  48x48 Thumb │  │  48x48 Thumb │  │  48x48 Thumb │                 │     (100×105dp Cards, 8dp Spacing)
│ │  Title/Size  │  │  Title/Size  │  │  Title/Size  │  │  Title/Size  │                 │
│ └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘                 │
├────────────────────────────────────────────────────────────────────────────────────────┤
│                [ 📄 Send Files ]          [ 📁 Send Folders ]                          │  ← Row 2: Action Dock &
│                 [ ⬇ Saved to Downloads\DeX (Change) ]                                  │     PullProgressDock Floating Toast
│           [ ⏳ dockPullProgress: Pulling 3 of 10 files... [====] (X) ]                 │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

#### Row 0: Top Navigation Controls
- **`btnUpDir`**:
  - Size: $36 \times 36\text{ dp}$ circular button (`RoundedCornerShape(18.dp)`), background `DeXTheme.colors.accent`.
  - Icon: `MaterialSymbols.ArrowBack` / `ArrowUpward` ($16\text{ sp}$).
  - Visibility / State: Enabled only when current path is not root (i.e. inside a subfolder or SAF document). When at root, opacity drops to $0.4\text{f}$ and click is disabled.
  - Up Navigation Logic:
    - *SAF Mode*: If URI contains `/document/`, locate the last `%2F` delimiter; if present, navigate to parent document URI; if not present, navigate back to `Phone Folders` root list.
    - *Local Mode*: If Windows path (`C:\...`), trim trailing separator and navigate to parent directory unless at drive root.
- **`txtSearch`**:
  - Geometry: $40\text{ dp}$ height pill container (`RoundedCornerShape(20.dp)`), background `DeXTheme.colors.accent`, inner padding $14\text{ dp}$ horizontal, $8\text{ dp}$ vertical.
  - Search Icon: `MaterialSymbols.Search` ($14\text{ sp}$) in `secondaryText`.
  - Debounced Filtering: Uses a $150\text{ ms}$ debounce (`snapshotFlow { query }.debounce(150)` or `LaunchedEffect(query) { delay(150); filter() }`).
  - Dynamic Placeholder: Evaluates to `"Search transfers..."` in History mode and `"Search files..."` in SAF mode.
- **`btnToggleExplorerMode`**:
  - Size: $36 \times 36\text{ dp}$ circular button (`RoundedCornerShape(18.dp)`), background `DeXTheme.colors.accent`.
  - Icon: `MaterialSymbols.Folder` ($16\text{ sp}$) in `primaryText`.
  - Function: Toggles between Local History and Phone SAF mode.
  - Device Eligibility Check: If user toggles to SAF mode while no LAN device is connected/trusted, displays toast: *"Tap a phone connected on your network to browse its files."* If connected phone has 0 granted folders, triggers `/local/dex/grant-folder` async flow.

#### Row 1: Middle Grid (`LazyVerticalGrid`)
- **Grid Layout**: `LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 100.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp))`.
- **Item Cards ($100 \times 105\text{ dp}$)**:
  - Shape: `RoundedCornerShape(8.dp)` (`DeXShapes.GridItemCard`).
  - Animated State Transitions:
    - *Resting*: Transparent or `accent` surface, $1\text{ dp}$ border.
    - *Hover*: Scale $1.05\times$, translateY $-2\text{ dp}$ over $300\text{ ms}$ (`FastOutSlowInEasing`), background `#2B2631`.
    - *Press*: Scale $0.94\times$ over $100\text{ ms}$.
    - *Selected*: Background `#332D3B`, border $1\text{ dp}$ `#0AE66D` (Emerald).
  - Thumbnail Container ($48 \times 48\text{ dp}$):
    - Rounded clip `RoundedCornerShape(4.dp)`.
    - SubcomposeAsyncImage via Coil 3.x with crossfade for image/video formats.
    - Fallback: Material Symbols icon according to file category (`Photo`, `VideoCamera`, `Article`, `Inventory`, `Folder`) in `#A0A0A0`.
  - Text Metadata: File name ($12\text{ sp}$ `primaryText`, max 1 line with ellipsis) and formatted file size / timestamp ($10\text{ sp}$ `secondaryText`).
- **Interactions & Safety Guards**:
  - **400ms Double-Click Delta Guard**:
    ```kotlin
    val now = System.currentTimeMillis()
    if (now - lastDoubleClickTime < 400L) return
    lastDoubleClickTime = now
    ```
  - **Folder Drill-Down**: Double-clicking a folder card calls `loadDirectory(folder.fullPath)`.
  - **Dangerous File Protection**:
    - Extensions list: `setOf(".exe", ".bat", ".cmd", ".ps1", ".vbs", ".vbe", ".msi", ".scr", ".com", ".pif", ".wsf")`.
    - If double-clicked file matches dangerous extension, DeX strictly executes:
      ```kotlin
      ProcessBuilder("explorer.exe", "/select,\"${file.absolutePath}\"").start()
      ```
      This highlights the file safely in Windows File Explorer instead of executing binary code.
    - Non-dangerous files: Opened with `java.awt.Desktop.getDesktop().open(file)`.
  - **Phone SAF Pull**: Double-clicking a file in SAF mode initiates pull via `/local/dex/pull` and reveals `PullProgressDock`.
  - **Keyboard Bindings**:
    - `Ctrl+A`: Select all visible filtered items.
    - `Escape`: Clear item selection.
    - `Enter`: Open / pull selected items.
- **Empty State Overlay**:
  - Displays when item list is empty or search yields zero results.
  - $48\text{ dp}$ folder icon with $0.4\text{f}$ alpha, title *"No transfers yet"*, subtitle *"Received files appear here"*.

#### Row 2: Bottom Actions & Floating `PullProgressDock`
- **Separator**: $1\text{ dp}$ `accent` divider ($300\text{ dp}$ width, $20\text{ dp}$ bottom margin).
- **Send Files Button**: `Row` pill with `MaterialSymbols.FileUpload` ($19\text{ sp}$) and text *"Send Files"* ($13\text{ sp}$ Medium). Launches native AWT `FileDialog` or Swing `JFileChooser`.
- **Send Folders Button**: `Row` pill with `MaterialSymbols.Folder` ($19\text{ sp}$) and text *"Send Folders"* ($13\text{ sp}$ Medium). Launches native directory chooser.
- **Floating `dockDownloadToast`**:
  - Toast pill ($20\text{ dp}$ corner radius, background `#2B2631`, $14 \times 8\text{ dp}$ padding) showing *"Saved to Downloads\DeX"* with Emerald checkmark and *"Change"* button.
- **Floating `PullProgressDock`**:
  - Geometry: $360\text{ dp}$ width container (`RoundedCornerShape(16.dp)`), background `#2B2631`, border $1\text{ dp}$ `#332D3B`, padding $14\text{ dp}$ horizontal, $10\text{ dp}$ vertical.
  - Animated entrance: Scale $0.85 \to 1.0$, translateY $25 \to 0\text{ dp}$, opacity $0 \to 1$.
  - Contents:
    - Status Row: Status text (e.g., *"Pulling 3 of 10 files..."*, $13\text{ sp}$ SemiBold White) + Cancel icon button (`MaterialSymbols.Close`, $12\text{ dp}$, `#A0A0A0`).
    - Progress Bar: $4\text{ dp}$ emerald linear progress bar (`Color(0xFF0AE66D)`), track `Color(0xFF16121A)`, `clip(RoundedCornerShape(2.dp))`.
  - Cancellation: Calls `/local/dex/pull-cancel?requestId=...`.
  - Terminal Action: Dispatches desktop notification toast *"Pull Complete: N files pulled to Downloads\DeX"* and opens destination directory via `explorer.exe`.

---

### 2.2 Complete SettingsPanel Architectural Specification

`SettingsPanel` provides user preferences, identity state, connection toggles, and developer tooling in an organized scrollable view.

```
┌─────────────────────────────────────────────────────────────┐
│ [⚙ Settings Pill]                  [ℹ DeX Version Pill]    │  ← Header
├─────────────────────────────────────────────────────────────┤
│ ┌─────────────────────────────────────────────────────────┐ │
│ │  [ 👤 Avatar ]   DeXStudios                             │ │  ← Profile Section
│ │                  dexify@dex.net                         │ │     (56x56dp Avatar, Premium Badge)
│ │                  [⭐ Premium User]                      │ │
│ └─────────────────────────────────────────────────────────┘ │
│                  [ Sign out Button ]                        │
│ ─── Connection ──────────────────────────────────────────── │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │  [⛔] Do Not Disturb (Auto-reject requests)     [OFF/ON]│ │  ← DND Switch
│ └─────────────────────────────────────────────────────────┘ │
│ ─── Developer Tools ─────────────────────────────────────── │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │  [🔌] Connect ADB (Terminal debugging)                  │ │  ← Manual ADB
│ │  [⚡] Auto-Connect ADB Hotspot                  [ON/OFF]│ │  ← Hotspot ADB
│ └─────────────────────────────────────────────────────────┘ │
│ ─── Identity ────────────────────────────────────────────── │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │  [👤] Sign in with Google (OAuth loopback)           [>]│ │  ← Google Auth
│ └─────────────────────────────────────────────────────────┘ │
│ ─── Appearance ──────────────────────────────────────────── │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │  [🎨] Theme (Dark / Light)                           [>]│ │  ← Theme Toggle
│ └─────────────────────────────────────────────────────────┘ │
│ ─── Interaction ─────────────────────────────────────────── │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │  [✨] Wiggle-to-Open Menu (Enabled / Disabled)       [>]│ │  ← Wiggle Gesture
│ └─────────────────────────────────────────────────────────┘ │
│ ─── Storage ─────────────────────────────────────────────── │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │  [💾] Download Location (Downloads\DeX)              [>]│ │  ← Path Picker
│ └─────────────────────────────────────────────────────────┘ │
│ ─── About ───────────────────────────────────────────────── │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │  [ℹ] DeX (Version 1.0.0 - GitHub Link)               [>]│ │
│ │  [⚠] Reset Identity & Trust (Revoke & Restart)       [>]│ │  ← Danger Action
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

#### Settings Categories & Data Bindings:
1. **Header**:
   - Left: *"⚙ Settings"* pill ($18\text{ dp}$ corner radius, background `accent`, $12 \times 8\text{ dp}$ padding, $15\text{ sp}$ SemiBold).
   - Right: *"ℹ DeX"* pill ($20\text{ dp}$ corner radius, background `accent`, $14 \times 8\text{ dp}$ padding, $13\text{ sp}$ Medium).
2. **Profile / Account Card**:
   - Avatar: $56 \times 56\text{ dp}$ circular image (`profile_avatar` or Google profile picture).
   - Name: $18\text{ sp}$ SemiBold (`googleProfile.name` or default `"DeXStudios"`).
   - Email: $13\text{ sp}$ `secondaryText` (`googleProfile.email` or `"dexify@dex.net"`).
   - Badge: $12\text{ sp}$ Emerald SemiBold (`#0AE66D`) *"Premium User"*.
   - Sign Out Button: Visible when signed in with Google (`deviceConfig.signOut()`).
3. **Connection Settings**:
   - Header: *"Connection"* ($14\text{ sp}$ SemiBold `secondaryText`).
   - Do Not Disturb: Icon `MaterialSymbols.Close` / `DoNotDisturb` ($18\text{ sp}$ in Danger red `#FF453A`), Title *"Do Not Disturb"*, Subtitle *"Auto-reject all pairing/transfer requests"*. Badge *"OFF"* (`accent` background) or *"ON"* (Danger red background). Synchronizes with `QuickActionBar` DND button.
4. **Developer Tools**:
   - Connect ADB: Icon `MaterialSymbols.Tune` ($18\text{ sp}$ in Emerald), Title *"Connect ADB"*, Subtitle *"Enable ADB terminal debugging for power users"*.
   - Auto-Connect ADB Hotspot: Icon `MaterialSymbols.Bolt` ($18\text{ sp}$ `primaryText`), Title *"Auto-Connect ADB Hotspot"*, Subtitle *"Auto-connect ADB daemon when joining phone hotspot"*, Badge *"ON"* / *"OFF"*.
5. **Identity Settings**:
   - Sign in with Google: Icon `MaterialSymbols.AccountCircle` ($18\text{ sp}$), Title *"Sign in with Google"*, Subtitle *"Trust all devices signed in with your email"* or *"Signed in as <email>"*, Trailing Chevron `>`. Triggers PC-side Google OAuth 2.0 loopback flow.
6. **Appearance Settings**:
   - Theme: Icon `MaterialSymbols.Palette` ($18\text{ sp}$), Title *"Theme"*, Subtitle *"Dark"* or *"Light"*, Trailing Chevron `>`. Toggles between Dark and Light color schemes.
7. **Interaction Settings**:
   - Wiggle-to-Open Menu: Icon `MaterialSymbols.TouchApp` / Wiggle ($18\text{ sp}$), Title *"Wiggle-to-Open Menu"*, Subtitle *"Enabled"* / *"Disabled"*, Trailing Chevron `>`.
8. **Storage Settings**:
   - Download Location: Icon `MaterialSymbols.Folder` ($18\text{ sp}$), Title *"Download Location"*, Subtitle current path (e.g. `Downloads\DeX`), Trailing Chevron `>`. Clicking opens native OS folder chooser dialog (`JFileChooser` / `DirectoryDialog`), sets `controller.isModalDialogOpen = true` during pick to guard focus loss.
9. **About & Maintenance**:
   - About DeX: Icon `MaterialSymbols.Info` ($18\text{ sp}$), Title *"DeX"*, Subtitle *"Version 1.0.0"*. Clicking opens GitHub project page (`https://github.com/SplashCodeDex/DeX`).
   - Reset Identity & Trust: Icon `MaterialSymbols.Warning` ($18\text{ sp}$ in `#FF453A` Danger), Title *"Reset Identity & Trust"*, Subtitle *"Revokes all devices and restarts"*, `isDanger = true`. Shows confirmation dialog and deletes `identity.json`.

---

### 2.3 Integration Points with `DockCardContent.kt`, `ExpandedPanel`, and `MainScreenViewModel.kt`

1. **`ExpandedPanel` Enumeration (`composeApp:window`)**:
   ```kotlin
   enum class ExpandedPanel {
       FileExplorer,
       Settings,
       Pairing
   }
   ```

2. **`DockedWindowStateController` Expansion Integration**:
   - When expanding to `FileExplorer`: Width expands $+754\text{ dp}$ ($300\text{ dp} \to 1054\text{ dp}$), Height expands $+195\text{ dp}$ ($430\text{ dp} \to 625\text{ dp}$). Dynamic `calculateExpansionNudge` shifts window X origin if near screen left edge.
   - When expanding to `Settings`: Width expands $+375\text{ dp}$ ($300\text{ dp} \to 675\text{ dp}$), Height expands $+195\text{ dp}$ ($430\text{ dp} \to 625\text{ dp}$).
   - Focus Loss 5-Point Guard:
     ```kotlin
     fun shouldDismissOnFocusLoss(): Boolean =
         !isPinned && !isShowingTransition && !isPairingActive && !isExpanded && !isModalDialogOpen
     ```
     `isExpanded` is `true` while either `FileExplorer` or `Settings` drawer is open, and `isModalDialogOpen` is set to `true` whenever native file/folder pickers are active.

3. **`DockCardContent.kt` Compositing**:
   - The drawer container wraps `FileExplorerPanel` and `SettingsPanel` inside `AnimatedVisibility` with spring slide and fade animations:
     ```kotlin
     when (controller.expandedPanel) {
         ExpandedPanel.FileExplorer -> FileExplorerPanel(
             onClose = { controller.collapsePanel() },
             onSendFiles = { /* launch file picker */ },
             onSendFolders = { /* launch dir picker */ }
         )
         ExpandedPanel.Settings -> SettingsPanel(
             onClose = { controller.collapsePanel() }
         )
         ExpandedPanel.Pairing -> PinPairingPanel(
             pairingEngine = pairingEngine,
             onClose = { controller.collapsePanel() }
         )
         else -> {}
     }
     ```

4. **`MainScreenViewModel.kt` Interface Contract**:
   - `MainScreenViewModel` exposes:
     - `val uiState: StateFlow<MainScreenUiState>` (discovered devices)
     - `val transferItems: StateFlow<List<TransferRecord>> = TransferHistory.items`
     - `val googleProfile: StateFlow<GoogleProfile> = deviceConfig.googleProfileFlow`
     - `fun requestPairing(device, onResult)`
     - `fun requestUnpair(device, onResult)`
     - `fun sendFiles(device, paths)`
     - `fun toggleDnd(enabled)`
     - `fun setCustomDownloadPath(path)`

---

## 3. Caveats

1. **Native OS Modal Dialog Focus Guard**: When spawning Swing `JFileChooser` or AWT `FileDialog`, the AWT window temporarily loses focus. `controller.isModalDialogOpen = true` must be set immediately before showing the dialog and reset to `false` in a `finally` block to prevent accidental auto-dismissal of the card.
2. **Path Delimiter Normalization**: Windows uses backslashes (`\`), whereas SAF content document URIs use forward slashes (`/`) and `%2F` percent-encoded segment delimiters. Navigation logic must branch on URI scheme (`content://` vs Windows disk paths).
3. **Threading in Compose Desktop**: DataStore and WebSocket network calls must execute on `Dispatchers.IO` or `scope.launch`, with state collected in Compose via `collectAsStateWithLifecycle()` or `collectAsState()`.

---

## 4. Conclusion

- The architecture and specifications for `FileExplorerPanel.kt` and `SettingsPanel.kt` have been completely analyzed and mapped 1:1 from the legacy WPF implementation (`Bindings_FileBrowser.ps1`, `Bindings_Settings.ps1`, `MainWindow.xaml`) into modern Compose Multiplatform Desktop componentry.
- `FileExplorerPanel.kt` is fully specified with its 3-row layout: $36\text{ dp}$ Up-Dir navigation button, $40\text{ dp}$ search pill with $150\text{ ms}$ debounce, SAF vs History mode switch, $100 \times 105\text{ dp}$ grid cards with $48 \times 48\text{ dp}$ thumbnails and hover/press animations, $400\text{ ms}$ double-click delta guard, dangerous file `/select` protection, bottom action buttons (Send Files/Folders), and the floating $360\text{ dp}$ `PullProgressDock` with $4\text{ dp}$ emerald progress bar.
- `SettingsPanel.kt` is fully specified with its complete categorical hierarchy: Profile header with Google Sign-In / Sign Out, Connection DND toggle with emerald/danger badges, Developer Tools ADB Connect / Auto-Connect, Appearance Theme toggle, Storage Download Location folder picker, and About DeX / Reset Identity actions.
- Integration points across `DockCardContent.kt`, `ExpandedPanel` in `DockedWindowStateController.kt`, and `MainScreenViewModel.kt` are strictly defined and validated against the 5-point focus loss guard and spring kinematics.

---

## 5. Verification Method

### 5.1 Independent Code & Layout Verification

1. **Verify State Controller & Stress Tests**:
   - Run controller unit and stress tests:
     ```powershell
     cd w:\CodeDeX\DeX\DeX
     .\gradlew.bat :composeApp:desktopTest --tests "com.dexstudios.dex.window.*"
     ```
   - Invalidation condition: Any failure in `DockedWindowStateControllerStressTest` (especially `testFocusLoss5PointGuardExhaustiveTruthTable` or `testRapidConsecutivePanelExpansionsAndContractions`).

2. **Verify Desktop Compilation**:
   - Run Kotlin Desktop compilation:
     ```powershell
     cd w:\CodeDeX\DeX\DeX
     .\gradlew.bat :composeApp:compileKotlinDesktop
     ```
   - Invalidation condition: Unresolved imports or type mismatches in `composeApp` or dependent `core` / `feature` modules.

3. **Verify Design System & Tokens Compliance**:
   - Inspect that `FileExplorerPanel.kt` and `SettingsPanel.kt` exclusively use `DeXTheme.colors` (`primaryText`, `secondaryText`, `accent`, `secondary`, `danger`), `MaterialSymbols`, and standard `RoundedCornerShape` tokens with zero hardcoded hex colors or arbitrary paddings.
