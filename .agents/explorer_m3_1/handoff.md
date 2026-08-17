# Investigation Report: Milestone 3 — DeX Compose Multiplatform Desktop UI

## Executive Summary
This report delivers an exhaustive, line-referenced architectural investigation for Milestone 3 of DeX Desktop (Compose Multiplatform on Kotlin 2.4.10 / Compose 1.11.1 / Java 17). It provides the exact Kotlin Compose signatures, state flows, event handlers, kinematics, and styling needed to integrate `MainScreenViewModel`, `QuickActionBar`, `TopActionsPanel`, and the supporting windowing subcomponents into the 1:1 WPF-parity floating dock card architecture.

---

## 1. Observation

### 1.1 `MainScreenViewModel` & State Models Analysis
- **`MainScreenViewModel.kt`** (`feature/discovery/src/commonMain/kotlin/com/dexstudios/dex/feature/discovery/MainScreenViewModel.kt`):
  - Injected dependencies: `discoveryEngine: DiscoveryEngine`, `clientEngine: ClientEngine`, `webSocketEngine: WebSocketEngine`.
  - Exposed UI state:
    ```kotlin
    val uiState: StateFlow<MainScreenUiState> = discoveryEngine.devices
        .map<Map<String, DiscoveredDevice>, MainScreenUiState> { devicesMap -> 
            MainScreenUiState.Success(devicesMap.values.toList()) 
        }
        .catch { emit(MainScreenUiState.Error(it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainScreenUiState.Loading)
    ```
  - Pairing actions: `requestPairing(device: DiscoveredDevice, onResult: (Boolean) -> Unit)` and `requestUnpair(device: DiscoveredDevice, onResult: (Boolean) -> Unit)`.
- **`DiscoveredDevice` & `RegisterDto`** (`core/data/src/commonMain/kotlin/com/dexstudios/dex/network/protocol/ProtocolDto.kt` L6-58):
  - `DiscoveredDevice` carries `ip: String`, `info: RegisterDto`, `lastSeenTimestamp: Long`, `viaWan: Boolean`, `viaRoster: Boolean`.
  - `RegisterDto` carries hardware telemetry: `alias`, `deviceModel`, `deviceType`, `fingerprint`, `port`, `quicPort`, `protocol`, `identityHash`, `googleSub`, `battery: Int?`, `isCharging: Boolean?`, `wifiBand: String?`, `wifiSsid: String?`.
- **`AuthState` & `DeviceManager`** (`core/network/src/commonMain/kotlin/com/dexstudios/dex/auth/AuthState.kt`, `DeviceManager.kt`):
  - `AuthState.pairedFingerprints: StateFlow<Set<String>>` tracks paired trusted device fingerprints.
  - `AuthState.pairedTokens: StateFlow<Map<String, String>>` and `AuthState.pairedTimes: StateFlow<Map<String, Long>>`.
  - `AuthState.incomingPairRequest: StateFlow<PairRequestInfo?>`.
- **`ClientEngine` & `UploadState`** (`core/network/src/commonMain/kotlin/com/dexstudios/dex/network/ClientEngine.kt` L54-56, L302-316):
  - `uploadState: StateFlow<UploadState>` exposes real-time transfer progress: `fileName`, `progress`, `aggregateProgress`, `isUploading`, `isSuccess`, `error`, `protocol`, `speedBps`, `peerName`.
- **`DeviceConfig`** (`core/data/src/commonMain/kotlin/com/dexstudios/dex/network/DeviceConfig.kt` L46-70):
  - Exposes `emailFlow`, `aliasFlow`, `clipboardSyncEnabledFlow`, `googleProfileFlow`, `googleSubFlow`.

### 1.2 Desktop Windowing & Kinematics Baseline
- **`DockedWindowStateController.kt`** (`composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/DockedWindowStateController.kt`):
  - Core state: `isVisible`, `isPinned`, `isShowingTransition`, `hasBeenDragged`, `isPairingActive`, `isModalDialogOpen`, `expandedPanel` (`FileExplorer`, `Settings`, `Pairing`), `isDragging`, `dragPending`, `isShaking`.
  - 5-point focus loss guard (L98-100): `!isPinned && !isShowingTransition && !isPairingActive && !isExpanded && !isModalDialogOpen`.
  - Kinematics: 3-phase drag gestures, 5px Manhattan deadzone (`DockCardPhysics.MANHATTAN_DEADZONE_PX`), high-DPI mouse scaling, 20px magnetic edge snapping, post-expansion boundary nudge (`Nudge-ForExpand`), contraction origin clamping (void prevention), and 450ms atomic 2D double-tap reset.
- **`FloatingDockCard.kt`** (`composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/FloatingDockCard.kt`):
  - Transparent fixed bounding canvas ($1420 \times 760\text{ dp}$) anchored at `Alignment.TopEnd` with `25.dp` padding.
  - Integrates `popInTransition(visible = controller.isVisible)` (scale 0.85→1.0, translateY 15→0dp, alpha 0→1 over 500ms).
- **`DockCardContent.kt`** (`composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/DockCardContent.kt`):
  - Internal animated width: $300\text{ dp}$ (contracted) $\leftrightarrow$ $1054\text{ dp}$ (File Explorer) / $675\text{ dp}$ (Settings) / $400\text{ dp}$ (Pairing).
  - Internal animated height: $430\text{ dp}$ (contracted) $\leftrightarrow$ $625\text{ dp}$ (expanded).
  - Shape: `RoundedCornerShape(34.dp)`.
- **`MainMenuColumn.kt`** (`composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/MainMenuColumn.kt`):
  - Right 300dp column containing `TopActionsPanel`, `DeviceListPanel`, `BottomDockPanel`.

### 1.3 Discrepancies & Missing M3 Implementations
1. **Quick Actions**: Currently `TopActionsPanel.kt` (L154-182) renders a crude private placeholder `QuickActionButton` ($36\text{ dp}$ square, 1-character text, no hover/press kinematics, no Emerald morphing, no contrast-inverted badges, no Danger Close button).
2. **Component Separation**: `QuickActionBar.kt` is missing as a dedicated component file under `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/window/components/QuickActionBar.kt`.
3. **Telemetry Status Bar**: Status bar in `TopActionsPanel.kt` (L108-144) lacks live local IP/port telemetry integration and feedback on "Copy IP".
4. **Device List Filtering**: `DeviceListPanel.kt` currently defines a local dummy `DiscoveredDevice` data class (L21-26) instead of using the real `com.dexstudios.dex.network.DiscoveredDevice` and does not distinguish between Discovered (untrusted) and Paired (live/offline) devices.

---

## 2. Logic Chain

### 2.1 ViewModel & State Flow Integration
- *Premise*: The floating dock card is the primary desktop UI representation of the active DeX node.
- *Observation*: `MainScreenViewModel` and injected core engines (`DiscoveryEngine`, `DeviceConfig`, `ClientEngine`, `AuthState`, `PairingEngine`, `DeXServer`) already provide full reactive state flows for devices, pairings, transfers, and identity.
- *Inference*: `MainMenuColumn` and its child panels (`TopActionsPanel`, `DeviceListPanel`, `BottomDockPanel`) should consume these state flows cleanly without creating dummy data holders.
- *Partitioning Logic*:
  1. `Discovered Devices`: Devices from `discoveryEngine.devices` whose fingerprint is NOT in `AuthState.pairedFingerprints`.
  2. `Your Devices` (Paired): Devices whose fingerprint IS in `AuthState.pairedFingerprints`. If currently present in `discoveryEngine.devices`, render with active battery %, WiFi glyph, and online badge; if absent, render in offline greyed state.
  3. `WAN Roster Devices`: Synthetic WAN profile devices (`viaWan == true` / `viaRoster == true`).

### 2.2 `QuickActionBar` Specification & Kinematics Port
- *Observation*: Legacy WPF (`AppStyles.xaml`, `MainWindow.xaml`) uses a centered horizontal stack of 4 primary action buttons + 1 dynamic danger close pill with tactile micro-interactions.
- *Geometry*:
  - Button Dimensions: $56\text{ dp}$ width $\times$ $44\text{ dp}$ height, `RoundedCornerShape(20.dp)`.
  - Total compact toolbar width: $4 \times 56\text{ dp} + 3 \times 6\text{ dp} = 242\text{ dp}$ (centered inside $300\text{ dp}$ column).
- *Kinematic Equations & Specs*:
  - **Hover Micro-Lift**:
    $$\text{Scale} = 1.08\times, \quad \text{TranslateY} = -3\text{ dp} \quad (300\text{ ms, } \text{CubicBezier}(0.34, 1.56, 0.64, 1.0))$$
  - **Press Sink**:
    $$\text{Scale} = 0.85\times, \quad \text{TranslateY} = +3\text{ dp} \quad (100\text{ ms, FastOutSlowInEasing})$$
  - **Emerald State Morphing**:
    - Inactive: Background `Color(0xFF2B2631)`, Icon `Color(0xFFFFFFFF)`.
    - Active / Checked: Background `Color(0xFF0AE66D)`, Icon `Color(0xFF000000)`, Drop Shadow `Color(0xFF0AE66D).copy(alpha = 0.35f)`, blur $12\text{ dp}$.
  - **Contrast-Inverted Badge Counter**:
    - Unchecked pill: Badge container `Color(0xFF0AE66D)`, Badge text `Color(0xFF000000)`.
    - Checked (Emerald) pill: Badge container `Color(0xFF16121A)`, Badge text `Color(0xFFFFFFFF)`, Border $1\text{ dp}$ `Color(0xFF0AE66D)`.
  - **Collapsible Danger Close Pill (`btnCloseMenu`)**:
    - When `isPanelExpanded == false`: Width $= 0\text{ dp}$, Opacity $= 0.0$ (collapsed).
    - When `isPanelExpanded == true`: Width $= 56\text{ dp}$, Opacity $= 1.0$ (slides/expands smoothly with `DockCardPhysics.ElasticDpSpec`).
    - Hover/Press: Background `Color(0xFFFF453A)`, Icon `Color(0xFFFFFFFF)`.
    - On Click: Invokes `onCloseExpandedPanel()`, which triggers `controller.collapsePanel()`.

### 2.3 `TopActionsPanel` & Telemetry Status Bar
- *Layout*:
  1. Top: `DragPillHandle(controller, showPinButton = true)`.
  2. Center: `QuickActionBar(...)`.
  3. Bottom: Collapsible $39\text{ dp}$ Telemetry Status Bar (`AnimatedVisibility(visible = showTelemetry)`).
- *Telemetry Properties*:
  - Status text: e.g., `"Status: Ready (192.168.1.100:53317)"` or `"ADB: Connected"`.
  - Action: "Copy IP" button copies the active local network address to the clipboard and shows a temporary 1.5s visual feedback state (`"Copied!"`).

---

## 3. Caveats
1. **Material Symbols vs Custom SVG Resources**: Some icon glyphs (e.g. DND slash moon) can leverage `MaterialSymbols.Notifications` or `MaterialSymbols.Close` from `core/designsystem/icons/MaterialSymbols.kt` until dedicated multi-layer vector paths are loaded.
2. **Screen Mirroring Window Launch**: The "Mirror" button toggles mirroring session state; actual video rendering uses `IMirrorEngine` (`JvmMirrorEngine`).
3. **External File Pickers**: Invoking native OS file pickers sets `controller.isModalDialogOpen = true` to preserve the 5-point focus guard during window focus loss.

---

## 4. Conclusion & Complete Kotlin Interface Contracts

### 4.1 `QuickActionBar.kt` Contract & Signature
```kotlin
package com.dexstudios.dex.window.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.core.designsystem.icons.MaterialSymbols
import com.dexstudios.dex.core.designsystem.theme.DeXTheme
import com.dexstudios.dex.window.kinematics.DockCardPhysics

@Composable
fun QuickActionBar(
    isDndActive: Boolean,
    isMirroringActive: Boolean,
    isTransfersActive: Boolean,
    isClipboardActive: Boolean,
    clipboardBadgeCount: Int = 0,
    isPanelExpanded: Boolean,
    onToggleDnd: () -> Unit,
    onToggleMirror: () -> Unit,
    onToggleTransfers: () -> Unit,
    onToggleClipboard: () -> Unit,
    onCloseExpandedPanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Do Not Disturb Pill (56x44dp)
        DeXQuickActionButton(
            icon = MaterialSymbols.Notifications,
            tooltip = "Do Not Disturb",
            isChecked = isDndActive,
            onClick = onToggleDnd
        )

        // 2. Screen Mirror Pill (56x44dp)
        DeXQuickActionButton(
            icon = MaterialSymbols.Smartphone,
            tooltip = "Mirror Phone",
            isChecked = isMirroringActive,
            onClick = onToggleMirror
        )

        // 3. Transfers / File Explorer Pill (56x44dp)
        DeXQuickActionButton(
            icon = MaterialSymbols.Folder,
            tooltip = "Transfers",
            isChecked = isTransfersActive,
            onClick = onToggleTransfers
        )

        // 4. Clipboard Sync Pill (56x44dp)
        DeXQuickActionButton(
            icon = MaterialSymbols.Clipboard,
            tooltip = "Clipboard",
            isChecked = isClipboardActive,
            badgeCount = clipboardBadgeCount,
            onClick = onToggleClipboard
        )

        // 5. Dynamic Collapsible Danger Close Pill (0dp <-> 56dp)
        AnimatedVisibility(
            visible = isPanelExpanded,
            enter = expandHorizontally(animationSpec = DockCardPhysics.ElasticIntOffsetSpec) + fadeIn(),
            exit = shrinkHorizontally(animationSpec = DockCardPhysics.ElasticIntOffsetSpec) + fadeOut()
        ) {
            DeXQuickActionButton(
                icon = MaterialSymbols.Close,
                tooltip = "Close",
                isChecked = false,
                isDanger = true,
                onClick = onCloseExpandedPanel
            )
        }
    }
}

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

    // Tactile Scale: 1.0 -> 1.08 (hover) -> 0.85 (press sink)
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.85f
            isHovered -> 1.08f
            else -> 1.0f
        },
        animationSpec = if (isPressed) tween(100) else tween(300, easing = DockCardPhysics.HoverEase),
        label = "btnScale"
    )

    // Tactile Translation: 0 -> -3dp (lift) -> +3dp (sink)
    val translateY by animateDpAsState(
        targetValue = when {
            isPressed -> 3.dp
            isHovered -> (-3).dp
            else -> 0.dp
        },
        animationSpec = if (isPressed) tween(100) else tween(300, easing = DockCardPhysics.HoverEase),
        label = "btnTransY"
    )

    // Emerald State Morphing Background Color
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

    // Icon Color Morphing
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
            .clip(RoundedCornerShape(20.dp))
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
            // Contrast Inversion for Badge Counter
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
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
```

### 4.2 `TopActionsPanel.kt` Unified Refactoring Contract
```kotlin
package com.dexstudios.dex.window.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dexstudios.dex.core.designsystem.theme.DeXTheme
import com.dexstudios.dex.window.DockedWindowStateController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun TopActionsPanel(
    controller: DockedWindowStateController,
    isDndActive: Boolean,
    onToggleDnd: () -> Unit,
    isMirroringActive: Boolean,
    onToggleMirror: () -> Unit,
    isTransfersActive: Boolean,
    onToggleTransfers: () -> Unit,
    isClipboardActive: Boolean,
    onToggleClipboard: () -> Unit,
    clipboardBadgeCount: Int = 0,
    statusTelemetryText: String = "Ready",
    serverIpPort: String = "",
    showTelemetry: Boolean = true,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Drag Pill & Pin Handle Row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            DragPillHandle(
                controller = controller,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 2. Tactile Quick Actions Row (56x44dp Pills + Dynamic Danger Close)
        Box(
            modifier = Modifier.padding(bottom = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            QuickActionBar(
                isDndActive = isDndActive,
                isMirroringActive = isMirroringActive,
                isTransfersActive = isTransfersActive,
                isClipboardActive = isClipboardActive,
                clipboardBadgeCount = clipboardBadgeCount,
                isPanelExpanded = controller.isExpanded,
                onToggleDnd = onToggleDnd,
                onToggleMirror = onToggleMirror,
                onToggleTransfers = onToggleTransfers,
                onToggleClipboard = onToggleClipboard,
                onCloseExpandedPanel = { controller.collapsePanel() }
            )
        }

        // 3. Status Bar Telemetry (Collapsible 39dp Row)
        AnimatedVisibility(
            visible = showTelemetry,
            enter = expandVertically(expandFrom = Alignment.Top),
            exit = shrinkVertically(shrinkTowards = Alignment.Top)
        ) {
            Column {
                HorizontalDivider(
                    color = DeXTheme.colors.accent,
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val displayText = if (serverIpPort.isNotBlank()) {
                        "Status: $statusTelemetryText ($serverIpPort)"
                    } else {
                        "Status: $statusTelemetryText"
                    }

                    Text(
                        text = displayText,
                        color = DeXTheme.colors.secondaryText,
                        fontSize = 12.sp,
                        maxLines = 1
                    )

                    if (serverIpPort.isNotBlank()) {
                        Text(
                            text = if (isCopied) "Copied!" else "Copy IP",
                            fontSize = 11.sp,
                            fontWeight = if (isCopied) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCopied) DeXTheme.colors.secondary else DeXTheme.colors.secondaryText,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    Toolkit.getDefaultToolkit().systemClipboard.setContents(
                                        StringSelection(serverIpPort),
                                        null
                                    )
                                    isCopied = true
                                    scope.launch {
                                        delay(1500)
                                        isCopied = false
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            color = DeXTheme.colors.accent,
            thickness = 1.dp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
```

---

## 5. Verification Method

### 5.1 Compilation and Packaging Commands
```bash
# In directory: w:\CodeDeX\DeX\DeX
./gradlew :composeApp:compileKotlinDesktop
./gradlew :composeApp:desktopJar
./gradlew :composeApp:desktopTest
```

### 5.2 Inspection Checklist for Implementation Agent
1. **Geometry & Shape**:
   - `QuickActionBar` pills must measure exactly $56 \times 44\text{ dp}$ with $20\text{ dp}$ corner radius.
   - Danger close button must expand from $0\text{ dp}$ to $56\text{ dp}$ when `controller.isExpanded` is true and collapse smoothly on click.
2. **Tactile Kinematics**:
   - Hover scale $1.08\times$ / translateY $-3\text{ dp}$ (300ms `HoverEase`).
   - Press scale $0.85\times$ / translateY $+3\text{ dp}$ (100ms snappy sink).
3. **Emerald State Morphing**:
   - Background `#0AE66D` on active state with black icon and inverted badge colors.
4. **Zero Regressions**:
   - Ensure all desktop unit tests in `composeApp/src/desktopTest/` pass.
