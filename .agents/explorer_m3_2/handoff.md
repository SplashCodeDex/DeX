# Milestone 3 UI Investigation & Component Specification Report

## Core Summary
This investigation establishes the exact 1:1 Compose Multiplatform Desktop specifications for **`DeviceListPanel.kt`**, **`PinPairingPanel.kt`**, and **`BottomDockPanel.kt`**, reverse-engineered directly from the legacy WPF/PowerShell desktop source code (`MainWindow.xaml`, `Bindings_Pairing.ps1`, `Bindings_Window.ps1`, `UIComponents.ps1`, `DeviceTelemetry.ps1`, `DeviceActions.ps1`) and reconciled with the existing Kotlin Multiplatform architecture (`DiscoveryEngine`, `MainScreenViewModel`, `DockedWindowStateController`, `io.github.kyant0:backdrop`).

---

## 1. Observation

### 1.1 Source-of-Truth Artifacts Examined

#### 1. Legacy WPF & PowerShell Runtime (`MSIX_Source/`)
- **`Themes/MainWindow.xaml`**:
  - `icUdpPeers` (Lines 721–756): Dynamic UDP Discovered Devices with 38x38dp leading circle (`SecondaryBrush`), 12x12dp online dot, 15sp Medium alias, 13sp model, and context menu (`menuPair`, `menuGuestConnect`, `menuGuestCopyIp`, `menuGuestForget`).
  - `icLivePeers` (Lines 807–863): Trusted paired devices with offline trigger (`DataTrigger IsOffline=True`), 38x38dp glyph, subtext telemetry (Model + Wifi icon `0xE701` + RSSI/SSID + Battery icon `0xE850-0xE859` + Battery %), and context menu (`menuClipboard`, `menuMirror`, `menuCopyIp`, `menuConnectAdb`, `menuDisconnectAdb`, `menuRename`, `menuForget`).
  - WAN Scaffolding Profiles (Lines 875–958): Static mock cards for `btnUser1` ("Ama Serwaa", `user2@dex.net`), `btnUser2` ("Akua Donkor", `user1@dex.net`), `btnUser3` ("Kwame Asante", `user3@dex.net`).
  - `pinViewPanel` (Lines 965–1053): 6-digit PIN display (`icPinDigits`, 44x56dp minimum digit boxes, 32sp bold, `AccentBrush`, 8dp corner radius), QR code view (`imgQrCode`, 140x140dp on 8dp white border), 60s timer (`txtPinTimeout`), and bottom action buttons (`btnPinCancel`, `btnSettingsQrCode`, `btnPinAccept`, `btnPinAcceptOnce`).
  - `pinViewPanel` Storyboards (Lines 520–556):
    * `SlideInPinAnim`: `menuContentTrans` X 0→-300 (250ms, `PowerEaseOut`), `pinViewTrans` X 300→0 (300ms, `PowerEaseOut`), scale 0.95→1.0 (250ms).
    * `SlideOutPinAnim`: `pinViewTrans` X 0→300 (250ms, `PowerEaseIn`), `menuContentTrans` X -300→0 (300ms, `PowerEaseOut`).
    * `SwitchQrToPinAnim`: QR translates X 0→-140 (250ms, fade out), PIN translates X 140→0 (250ms, fade in, scale 0.95→1.0).
    * `SwitchPinToQrAnim`: PIN translates X 0→140 (250ms, fade out), QR translates X -140→0 (250ms, fade in, scale 0.95→1.0).
  - `btnExit` and `btnProfileBottom` (Lines 484–515): Bottom docked row with 1dp `AccentBrush` horizontal divider, 34x34dp circular profile avatar, and "Exit Engine" button with `⌘Q` shortcut badge.

- **`bin/Modules/Bindings_Window.ps1` (Lines 27–174)**:
  - 2-Stage Exit Confirmation Mechanics:
    * Shift key check: `[Keyboard]::Modifiers -match 'Shift'` -> exits immediately with zero confirmation prompt.
    * First Click: If active pulls (`activePulls > 0`) or active mirror (`mirrorProc` alive), text becomes `"Transfer Active! Click to Force Exit"`; otherwise `"Cancel / Shift+Click Exit"`.
    * First Click Animation: `btnExit` margin animates `Thickness(-62, 0, 0, 0)` over 0.3s (`CubicEase Out`); `btnProfileBottom` scales down to `0.6` over 0.3s.
    * 3-Second Timeout: `exitTimer` (3s). On tick or cancel, text fades out (0.15s) -> swaps to `"Exit Engine"` -> fades in (0.15s); `btnExit` margin contracts to 0 (0.3s); avatar scales back to `1.0` after 0.35s delay.
    * Second Click: Invokes `Invoke-ExitEngine`.

- **`bin/Modules/UIComponents.ps1` (Lines 494–887)**:
  - `Show-PinPanel` / `Show-QrCode`: Starts 250ms poll cadence for real-time PIN keystroke sync (`digitCount` from backend).
  - Real-time digit entry: Shimmer gradient animation on active box, scale pop `1.0 -> 1.15 -> 1.0` (100ms). Backspace fades border to transparent (150ms).
  - PIN Error Shake: If phone rejects PIN (`digitCount == -1` or `status == 'Rejected'`), borders turn Red, translates `[-10, 10, -10, 10, 0]` px over 400ms, dwells 800ms before sliding out.

- **`bin/Modules/Bindings_Pairing.ps1` & `DeviceActions.ps1`**:
  - Context menu actions routing: `menuRename` (alias prompt), `menuForget`/`menuGuestForget` (`/local/unpair`), `menuCopyIp`/`menuGuestCopyIp` (clipboard write), `menuClipboard` (`/local/clipboard-push`), `menuMirror` (`/local/mirror`), `menuConnectAdb`/`menuDisconnectAdb` (`AdbManager`), `menuPair` (`Start-PinPairing`).

#### 2. Existing Kotlin Codebase (`composeApp/`, `feature/`, `core/`)
- `composeApp/.../window/components/DeviceListPanel.kt`: Currently contains a stub `DiscoveredDevice` data class and a primitive list without paired devices, context menus, telemetry, or WAN placeholders.
- `composeApp/.../window/PinPairingPanel.kt`: Currently contains an initial prototype with basic circle boxes, missing the 44x56dp rounded rectangle boxes, QR/PIN horizontal flip transitions, 60s countdown timer, and 1:1 action buttons.
- `composeApp/.../window/components/BottomDockPanel.kt`: Basic static button without the 2-stage exit state machine, 3s timeout, Shift+Click bypass, or avatar shrink animations.
- `feature/discovery/.../MainScreenViewModel.kt`: Emits `uiState: StateFlow<MainScreenUiState>`, handles `requestPairing` and `requestUnpair`.

---

## 2. Logic Chain

```
[Legacy WPF / PowerShell XAML & Bindings Analysis]
                    │
                    ├── 1. DeviceListPanel: UDP Peers + Live Peers + WAN Scaffolding + Context Menus
                    │      └── Observation: WPF maintains separate templates for icUdpPeers (pairing target)
                    │                       and icLivePeers (telemetry + mirror + clipboard).
                    │      └── Logic: Compose Desktop must render both sections, wire right-click ContextMenu,
                    │                 bind battery/wifi icons from RegisterDto, and retain WAN placeholder cards.
                    │
                    ├── 2. PinPairingPanel: 6-Digit Display + QR Code + Flip Animation + Error Shake
                    │      └── Observation: Digits are 44×56dp boxes with 32sp bold text; QR is 140×140dp;
                    │                       Flip transitions slide ±140dp with 250ms PowerEaseOut;
                    │                       Error shake oscillates ±15px over 400ms with Red borders.
                    │      └── Logic: Compose Desktop must model a unified PairingUiState, animate Box borders,
                    │                 provide 60s countdown, and offer Accept / Accept Once / Cancel buttons.
                    │
                    └── 3. BottomDockPanel: Profile Avatar + 2-Stage Exit Confirmation
                           └── Observation: First click changes text to "Transfer Active! / Cancel", expands button
                                            by -62dp, shrinks avatar to 0.6x, and arms 3s auto-revert timer.
                                            Shift+Click bypasses prompt and exits immediately.
                           └── Logic: Implement a 3-state machine (Idle, Confirming, ForcedExit) with coroutine
                                      delay(3000ms), Modifier.graphicsLayer scale, and PointerKeyboardModifiers.
```

---

## 3. Caveats & Edge-Case Considerations

1. **AWT Context Menu vs Compose `ContextMenuArea`**:
   Compose Desktop supports `ContextMenuArea` and `DropdownMenu`. Using `ContextMenuArea` with `ContextMenuItem` provides native-feeling desktop right-click menus without blocking the main event loop.
2. **Keyboard Modifier Detection on Desktop**:
   Detecting Shift+Click in Compose Desktop can be achieved via `PointerEventPass.Main` examining `pointerEvent.keyboardModifiers.isShiftPressed` or checking AWT `Toolkit.getDefaultToolkit().getSystemEventQueue()`.
3. **WAN Placeholder Profiles (`btnUser1/2/3`)**:
   As explicitly noted in WPF XAML comments (L866–874), these mock profiles ("Ama Serwaa", "Akua Donkor", "Kwame Asante") are intentional scaffolding for cross-email transfer UI stability. They must be rendered cleanly in Compose Desktop as disabled/scaffolding entries.
4. **QR Code Generation**:
   Desktop QR code rendering can use a lightweight Skia bitmap generator or embed `QRCoder`/`io.github.g0dkar:qrcode-kotlin` or display a styled SVG/Canvas matrix.

---

## 4. Conclusion & Component Technical Specifications

### 4.1 Specification: `DeviceListPanel.kt`

#### State & Model Definition
```kotlin
package com.dexstudios.dex.window.components

import androidx.compose.ui.graphics.vector.ImageVector
import com.dexstudios.dex.network.DiscoveredDevice
import com.dexstudios.dex.network.RegisterDto

/**
 * UI presentation model for devices displayed in the floating card.
 */
data class DeviceItemUiModel(
    val id: String,                  // Fingerprint or IP
    val alias: String,               // Display name
    val modelText: String,           // Device model or OS
    val ip: String,                  // LAN IP address
    val fingerprint: String,         // Unique hardware fingerprint
    val isPaired: Boolean,           // Trusted/paired status
    val isOnline: Boolean = true,    // Discovered via UDP or active ping
    val batteryPercent: Int? = null, // 0..100
    val isCharging: Boolean = false,
    val wifiBand: String? = null,    // "5GHz", "2.4GHz", or SSID
    val wifiRssi: Int? = null,       // -127..0 dBm
    val isWanPlaceholder: Boolean = false,
    val wanEmail: String? = null,
    val rawDevice: DiscoveredDevice? = null
)
```

#### Composable Signature & Implementation Blueprint
```kotlin
@Composable
fun DeviceListPanel(
    discoveredDevices: List<DeviceItemUiModel>,
    pairedDevices: List<DeviceItemUiModel>,
    wanPlaceholders: List<DeviceItemUiModel> = defaultWanPlaceholders(),
    onPairDevice: (DeviceItemUiModel) -> Unit,
    onSendFile: (DeviceItemUiModel) -> Unit,
    onSendClipboard: (DeviceItemUiModel) -> Unit,
    onMirrorScreen: (DeviceItemUiModel) -> Unit,
    onConnectAdb: (DeviceItemUiModel) -> Unit,
    onDisconnectAdb: (DeviceItemUiModel) -> Unit,
    onCopyIp: (String) -> Unit,
    onRenameDevice: (DeviceItemUiModel) -> Unit,
    onForgetDevice: (DeviceItemUiModel) -> Unit,
    modifier: Modifier = Modifier
)
```

#### Detailed Interaction & Visual Requirements:
1. **Section Headers**:
   - `"Discovered Devices"`: Rendered only when `discoveredDevices.isNotEmpty()`. Header text: `13.sp`, `FontWeight.SemiBold`, `DeXTheme.colors.secondaryText`.
   - `"Your Devices"`: Always visible. Contains Host PC item ("Windows", `MaterialSymbols.Computer`), User Account ("DeXStudios", `dexify@dex.net`), paired live peers, and WAN placeholders.
2. **Item Card Visuals**:
   - Background: Transparent resting, `DeXTheme.colors.accent` (`#2B2631`) on hover (`Modifier.hoverable`), `Color(0xFF332D3B)` on selection.
   - Corner radius: `12.dp`.
   - Leading Glyph (38x38dp): Circle filled with `DeXTheme.colors.secondary` (`#0AE66D`), containing smartphone icon (`18.sp`, Black). Sub-dot (12x12dp) with 2dp border anchored at `Alignment.BottomEnd`.
   - Telemetry Subtext: Model text + Wifi Glyph (`MaterialSymbols.Wifi`) + Band/RSSI + Battery Glyph (`MaterialSymbols.BatteryFull` / dynamic percentage) + `"${battery}%"`.
   - Offline Styling: If `!isOnline`, entire row opacity drops to `0.5f`, circle fill becomes transparent with 1.5dp stroke, sub-dot is hidden, and battery text shows `"Offline"`.
3. **Context Menu Actions**:
   - **Discovered Device**:
     1. `PIN CODE (Pair)` -> triggers `onPairDevice(device)` (opens `PinPairingPanel`).
     2. `Connect ADB` -> triggers `onConnectAdb(device)`.
     3. `Copy IP Address` -> triggers `onCopyIp(device.ip)`.
     4. `Divider`.
     5. `Forget Device` -> triggers `onForgetDevice(device)` with `DeXTheme.colors.danger`.
   - **Paired Device**:
     1. `Send Clipboard` -> triggers `onSendClipboard(device)`.
     2. `Mirror Screen` -> triggers `onMirrorScreen(device)`.
     3. `Copy IP Address` -> triggers `onCopyIp(device.ip)`.
     4. `Connect ADB` / `Disconnect ADB` -> triggers ADB actions.
     5. `Divider`.
     6. `Rename / Alias` -> triggers `onRenameDevice(device)`.
     7. `Forget Device` -> triggers `onForgetDevice(device)` with `DeXTheme.colors.danger`.

---

### 4.2 Specification: `PinPairingPanel.kt`

#### State Model
```kotlin
package com.dexstudios.dex.window.components

sealed interface PinPairingUiState {
    data class PinView(
        val title: String = "Pairing Request",
        val subtitle: String = "",
        val pinCode: String,            // 6-digit PIN string (e.g. "482910")
        val enteredDigitCount: Int = 0, // 0..6, or -1 for error shake
        val remainingSeconds: Int = 60, // 60..0
        val isError: Boolean = false,
        val statusText: String = "Enter This Pin On Your Phone 📱 or PC 💻"
    ) : PinPairingUiState

    data class QrView(
        val title: String = "Pairing Request",
        val subtitle: String = "Scan with DeX Mobile",
        val qrPayload: String,          // LAN pairing URL or IP string
        val remainingSeconds: Int = 60
    ) : PinPairingUiState

    object Success : PinPairingUiState
}
```

#### Composable Signature & Blueprint
```kotlin
@Composable
fun PinPairingPanel(
    state: PinPairingUiState,
    onToggleQrPin: () -> Unit,
    onAccept: () -> Unit,
    onAcceptOnce: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
)
```

#### Kinematic & Animation Specifications:
1. **6-Digit PIN Digit Boxes (`icPinDigits`)**:
   - Layout: Centered horizontal `Row` of 6 boxes with `8.dp` inter-box spacing.
   - Dimensions: Minimum width `44.dp`, height `56.dp`.
   - Shape: `RoundedCornerShape(8.dp)`.
   - Background: `DeXTheme.colors.accent` (`#2B2631`).
   - Border: `BorderStroke(2.dp, ...)`:
     * Resting: `Color.Transparent`.
     * Entered digit ($i < \text{enteredDigitCount}$): `DeXTheme.colors.secondary` (`#0AE66D`) with scale pop `1.0 -> 1.15 -> 1.0` (100ms `FastOutSlowInEasing`).
     * Error state: `DeXTheme.colors.danger` (`#FF453A`).
   - Typography: `32.sp`, `FontWeight.Bold`, `DeXTheme.colors.primaryText` (`#FFFFFF`).
2. **15px Error Shake Animation**:
   - Triggered when `state.isError == true` or `enteredDigitCount == -1`.
   - Animation spec: Multi-cycle spring/keyframe oscillation:
     $$\Delta X(t) \in [0, -15, +15, -10, +10, -5, 0]\text{ dp} \quad \text{over } 400\text{ ms}$$
   - Applied via `Modifier.graphicsLayer { translationX = shakeOffset }`.
   - Dwell duration: 800ms before auto-dismissing with toast.
3. **QR ↔ PIN Horizontal Flip Transition**:
   - Switching between QR and PIN views animates internal translation:
     * QR Out / PIN In: QR translates $0 \to -140\text{ dp}$ (opacity $1 \to 0$ over 250ms `PowerEaseOut`); PIN translates $+140 \to 0\text{ dp}$ (opacity $0 \to 1$, scale $0.95 \to 1.0$).
     * PIN Out / QR In: PIN translates $0 \to +140\text{ dp}$ (opacity $1 \to 0$ over 250ms `PowerEaseOut`); QR translates $-140 \to 0\text{ dp}$ (opacity $0 \to 1$, scale $0.95 \to 1.0$).
4. **QR Code Container**:
   - $140 \times 140\text{ dp}$ image surface on `RoundedCornerShape(8.dp)` white card with `12.dp` padding.
5. **Countdown Timer**:
   - `"Expires in ${remainingSeconds}s"` (`13.sp`, `SecondaryText`, opacity 0.7).
   - Decrements every 1000ms. On 0s, fires `onCancel()` and emits timeout toast.
6. **Action Buttons**:
   - `Cancel`: `MinWidth = 80.dp`, `AccentBrush` background, `PrimaryText` font.
   - `QR CODE` / `PIN CODE`: `MinWidth = 80.dp`, `SecondaryBrush` background, Black text, `MaterialSymbols.QrCode2` icon.
   - `Accept`: `MinWidth = 80.dp`, `SecondaryBrush` background, Black text.
   - `Accept Once (Guest)`: `MinWidth = 110.dp`, transparent background, 1dp `AccentBrush` border, `13.sp` text.

---

### 4.3 Specification: `BottomDockPanel.kt`

#### State Machine & Composable Blueprint
```kotlin
package com.dexstudios.dex.window.components

enum class ExitConfirmationStage {
    Idle,           // Shows "Exit Engine" and "⌘Q"
    Confirming,     // Shows "Cancel / Shift+Click Exit" or "Transfer Active! Click to Force Exit"
}

@Composable
fun BottomDockPanel(
    onProfileClick: () -> Unit,
    onExitEngine: () -> Unit,
    hasActiveTransfers: Boolean = false,
    isMirroringActive: Boolean = false,
    modifier: Modifier = Modifier
)
```

#### Detailed State Transition Engine:
1. **Resting State (`ExitConfirmationStage.Idle`)**:
   - Left: Profile avatar button ($34\times 34\text{ dp}$ circular `profile_avatar`), scale $1.0\times$.
   - Right: Exit button ($40\text{ dp}$ height, transparent background):
     * Icon: `MaterialSymbols.PowerSettingsNew` (`16.sp`, `Danger` `#FF453A`).
     * Text: `"Exit Engine"` (`15.sp`, `FontWeight.Medium`, `Danger`).
     * Badge: `"⌘Q"` / `"⎇Q"` (`14.sp`, `FontFamily.Monospace`, `Danger`).
2. **Interaction Handlers**:
   - **Shift+Click (Instant Exit Bypass)**:
     If the user holds the `Shift` key while clicking, bypass the 2-stage prompt completely and execute `onExitEngine()` immediately.
   - **Click without Shift (First Stage)**:
     * Stage transitions to `ExitConfirmationStage.Confirming`.
     * Text swaps:
       - If `hasActiveTransfers || isMirroringActive`: `"Transfer Active! Click to Force Exit"`
       - Otherwise: `"Cancel / Shift+Click Exit"`
     * Exit button margin expands leftward (offset $\Delta X = -62\text{ dp}$) over $300\text{ ms}$ (`FastOutSlowInEasing`).
     * Avatar button scales down $1.0 \to 0.6\times$ over $300\text{ ms}$ (`Modifier.graphicsLayer { scaleX = avatarScale; scaleY = avatarScale }`).
     * Background animates to `DeXTheme.colors.accent` (`#2B2631`).
     * Launches a 3000ms auto-revert coroutine timer (`delay(3000)`).
   - **Click during Confirmation (Second Stage)**:
     * User confirms exit -> calls `onExitEngine()`.
   - **3s Timeout Expiry or Cancel Click**:
     * Text crossfades out ($150\text{ ms}$) -> reverts to `"Exit Engine"` -> crossfades in ($150\text{ ms}$).
     * Exit button contracts offset back to $0\text{ dp}$ ($300\text{ ms}$).
     * Avatar scales back $0.6 \to 1.0\times$ after a $350\text{ ms}$ delay.
     * Background returns to transparent.

---

## 5. Verification Method

### 5.1 Static Code & AST Analysis
1. Verify no unresolved imports, missing parameters, or broken type contracts against Compose Multiplatform 1.11.1 / Kotlin 2.4.10.
2. Confirm all color tokens use `DeXTheme.colors` / `DeXColors` instead of hardcoded hex values.

### 5.2 Build & Compilation Commands
Execute the project build commands to ensure zero compiler errors:
```bash
cd w:\CodeDeX\DeX\DeX

# Compile Desktop Kotlin Sources
./gradlew :composeApp:compileKotlinDesktop

# Package Desktop JAR Distribution
./gradlew :composeApp:desktopJar
```

### 5.3 Manual Verification Checklist
1. **DeviceListPanel**:
   - [ ] Verify UDP peers appear under `"Discovered Devices"` with green smartphone icon and 12dp status dot.
   - [ ] Right-click a discovered device -> context menu shows `PIN CODE (Pair)`, `Connect ADB`, `Copy IP Address`, `Forget Device`.
   - [ ] Click discovered device -> routes directly to PIN pairing handshake.
   - [ ] Verify trusted devices display battery %, wifi RSSI/SSID, and offline styling if disconnected.
   - [ ] Right-click paired device -> context menu shows `Send Clipboard`, `Mirror Screen`, `Copy IP`, `Connect ADB`, `Rename`, `Forget`.
2. **PinPairingPanel**:
   - [ ] 6 digit boxes render with minimum 44x56dp geometry and 32sp bold text.
   - [ ] Tapping "QR CODE" flips smoothly to 140x140dp QR view; tapping "PIN CODE" flips back.
   - [ ] Countdown decrements from 60s to 0s; cancels automatically on 0s.
   - [ ] Entering an invalid PIN triggers the 15px error shake animation and red borders.
3. **BottomDockPanel**:
   - [ ] Clicking avatar button expands `SettingsPanel`.
   - [ ] Clicking "Exit Engine" expands button leftward, shrinks avatar to 0.6x, and prompts confirmation.
   - [ ] Waiting 3s auto-reverts back to "Exit Engine" smoothly.
   - [ ] Shift+Clicking "Exit Engine" exits immediately without confirmation prompt.
