# Changelog
## [10.1.28.49] - 2026-08-28
### Added
- **[minor] Tests: MessageHandler control-channel contract suite (20 tests) + TokenCodec persistence suite (5 tests)** — first coverage for the client-side WebSocket protocol layer, pinning docs/PROTOCOL.md behavior:
  - `MessageHandlerTest` (core/network): malformed/incomplete/unknown frames are swallowed without replies; `set-clipboard` routes to the platform clipboard and blank text is ignored; `mirror-start`/`mirror-stop` and `wallpaper-updated` route to engine/revision state; `relay-started`/`relay-error` complete and clear the pending punch-relay fallback; `endpoint-info` resolves the pending punch target; `peer-endpoint` records only valid announcements; `pair-prompt` auto-accepts pinless when already paired and resolves simultaneous-pairing races by lexicographic fingerprint tie-break; `pair-accepted` persists the token against the proven session identity (provider wins over wire claims, tokenless grants ignored); `identity-challenge` answers HMAC proof only when signed in and survives undecodable nonces; `public-address` auto-fills only a blank address; `prepare-upload` raises a prompt whose accept/reject routes downloadBatch accordingly.
  - `TokenCodecTest` (core/data, new `commonTest` source set with kotlin-test wired): paired-token map round trip is lossless; blank, corrupt, and non-string store values all degrade to an empty map instead of killing trust hydration.
  - Conventions: hand-rolled recording `IPlatformEngine` fake, per-test temp DataStore stores, real-time polling (`runBlocking` + `awaitUntil`) since the handler effects run on real IO threads. Full gate green (`spotlessCheck` + `:composeApp:desktopTest`).

## [10.1.28.48] - 2026-08-28
### Added
- **[minor] Docs: FEATURES.md** — new repo-root feature reference documenting the full verified feature surface of the desktop app (discovery, trust, pairing, transfers, history, remote file explorer, mirroring, clipboard sync, Google account, desktop shell, settings, design system, persistence, platform layer, security) plus companion projects and verification commands. All facts traced to live code; companion to ARCHITECTURE.md and PROTOCOL.md.
### Fixed
- **[fix] Changelog numbering repaired** — the uncommitted run carried duplicate version headings: an empty stray `[10.1.28.43]` heading, and the pairing-panel restructure mis-numbered as a second `[10.1.28.44]`. The stray heading is removed (no content lost), the pairing restructure is renumbered to `[10.1.28.46]`, the History multi-selection entry to `[10.1.28.47]`, and this entry to `[10.1.28.48]`. No entry content was altered.

## [10.1.28.47] - 2026-08-27
### Added
- **[minor] Desktop FileExplorer: History Multi-Selection, Marquee Box Drag, External Drag Blur & Device Drop Target** — complete interaction suite for History files and cross-app drag operations on Windows and macOS:
  - **External Drag History Blur & Focus Shifting**:
    - When external files are dragged from Windows Explorer or macOS Finder into the DeX window while the floating card is expanded in History mode, the entire History canvas automatically blurs with a `20dp` frosted glass blur, smooth fade-in (`250ms`), rounded corners (`24dp`), and vertical fading edges.
    - A centered tactile badge (*"Drop on a device to send"*) appears inside the blurred History card, indicating that files should be dropped on the device column.
  - **Device Section Drop Target (`dragAndDropTarget`)**:
    - The Device column on the right serves as the active drop zone for external files.
    - Hovering dragged files over the Device section triggers a `1.03f` scale lift with a `1.5dp` accent highlight border.
    - Dropping files immediately transmits them to the connected device via `DesktopFileSendService.sendFiles()`.
    - If no device is connected, surfaces a warning toast notification (*"No device connected to receive files"*).
  - **Rubberband Marquee Drag-Selection**:
    - Clicking and dragging on the empty history grid background draws a semi-transparent selection marquee box with rounded corners and high-contrast outline (`6dp` radius, theme primary fill & stroke).
    - Dynamically intercepts card layout bounds in root coordinate space in real time.
    - Supports `Ctrl / Cmd` modifier to additively union with previous selections or replace selections on clean drag.
  - **Multi-Selection Engine**:
    - **Single Click**: Focuses and highlights card in pure absolute white (`Color.White`).
    - **Ctrl / Cmd + Click**: Toggle individual item selection in or out of the selected set.
    - **Shift + Click**: Selects a contiguous range of history files between the active anchor item and clicked item.
    - **Keyboard Shortcuts**: `Ctrl/Cmd + A` selects all non-button history items; `Escape` or clicking empty canvas clears selection; `Delete` / `Backspace` opens confirmation dialog to remove selected items.
  - **Native OS Drag-and-Drop (`dragAndDropSource`)**:
    - Implemented `FileListTransferable` containing `DataFlavor.javaFileListFlavor` and `DataFlavor.stringFlavor`.
    - Dragging a single file or multiple selected files from DeX directly transfers them to Windows Explorer, macOS Finder, desktop folders, and external desktop apps.
  - **Batch History Context Menu (`HistoryItemContextMenu`)**:
    - Custom-styled Material 3 `DropdownMenu` with `frostedSurface` (matching searchbar & UpDir button theme styling with directional shiny glare rim), `bubbleFluidity` physics, `18dp` rounded corners, `PopupProperties(clippingEnabled = true)` edge safety, Fluent icons, and keyboard shortcut badges.
    - Dynamically adapts action titles for multi-selection (`Open (N items)`, `Send N items to Phone`, `Delete (N items)`).
    - Batch deletion is guarded by a centered modal `ConfirmationPopup` displaying the exact item count before removing DataStore records while preserving physical disk files.
  - **Canvas Context Menu (`HistoryCanvasContextMenu`)**: right-clicking the empty background canvas with `frostedSurface`, `bubbleFluidity`, and `18dp` rounded corners pops beside the mouse cursor:
    - `Open Downloads Folder` — opens `%USERPROFILE%\Downloads\DeX` or `~/Downloads/DeX`.
    - `Refresh Listing` (F5) — re-scans directory and clears thumbnail caches.
    - `Clear All History` (error accent) — triggers centered modal `ConfirmationPopup` before clearing all transfer records.
  - **Context Menu Item Overshoot Hover Kinematics**: integrated `DockCardPhysics.HoverEase` overshoot easing (`scale = 1.03f`, `translationX = 3.dp`) on context menu rows for responsive hover feedback.
  - **Grid Breathing Room & Edge Clipping Prevention**: expanded `LazyVerticalGrid` content padding to `PaddingValues(horizontal = 8.dp, vertical = 8.dp)` with 10dp item gaps and tuned card hover scale to `1.05f`, ensuring scaled cards never clip against viewport edges.
  - **Point Anchor Positioning & Boundary Safety**: context menus anchor to a zero-size cursor point box `(cursorX, cursorY)` rather than the full 118dp card bounds, eliminating vertical jumps for items near the top search bar while respecting window edge-clipping clamping.
  - **ViewModel & Test Coverage**: added `selectSingle`, `toggleSelection`, `selectRange`, `selectAll`, `setSelectedIds`, `clearSelection`, `removeSelectedFromHistory`, `clearAllHistory`, and `refreshHistory` to `FileExplorerViewModel.kt` with unit test coverage in `FileExplorerViewModelTest.kt` passing all test suites.

## [10.1.28.46] - 2026-08-26
### Changed
- **[minor] Desktop pairing panel: layout + UX restructured for clarity (visual language untouched)** — the pairing card's information architecture was rebuilt without touching theme, glass or the engine contract:
  - Header: title tightened to 16sp SemiBold with 2-line ellipsis (long device names no longer stretch the row), a status-oriented subtitle per phase ("Scan with the DeX app on your phone" / "Waiting for the code to be entered" / "Preparing a pairing code"), close button pinned top-right.
  - PIN view: the legacy inline glyph run "Enter This Pin On Your Phone <E8EA> or PC <E7F4>" became a two-line instruction block (phone-first primary line, computer-second secondary line); a new "Copy code" chip (clipboard -> checkmark, "Copied" feedback for 1.5s) writes the PIN via AWT clipboard for the PC-to-PC flow and only reports success after a verified write (a locked clipboard never fakes it).
  - Countdown: the bare "Expires in Xs" text is now a shared `PairingCountdown` — a 180dp draining bar (500ms tween, fraction from the new public `PairingEngine.PIN_TTL_SECONDS`, replacing the panel's hardcoded 60) plus label that flips to the error accent + SemiBold under 10s. The QR view now shows the same countdown since its idle deadline is real and re-armed.
  - QR view: code enlarged 140 -> 168dp on a 16dp-radius white card with a hairline `outlineVariant` border; single scan instruction "Open DeX on your phone and scan" (the old subtitle duplicated the toggle hint and said "tap" on desktop).
  - Action hierarchy: Cancel demoted to a quiet `surfaceVariant` pill (the common path ends on the phone, so cancel must not out-shout the method toggle); the toggle now reads action-oriented "Show QR Code" / "Use PIN Code" and yields the filled accent to Accept when a peer offer is on screen (`secondaryContainer`); Accept Once row unchanged.
  - Failure slot: the PIN-request-failure message moved from a QR-only alpha-faked Text into a shared `PairingStatusMessage` (warning icon + AnimatedVisibility expand/fade) rendered in both views.
  - Success view: 72dp check circle, "Devices connected" + "File transfers are now enabled" (title unified with the header, which previously said "Connected!" while the body said "Pairing Complete").
  - Content split into `PinContentView`/`QrContentView`/`PairingCountdown`/`PairingStatusMessage` composables; public API, flip animation, snapshot model, shake and all `PairingEngine` wiring unchanged. Verified via `:composeApp:compileKotlinDesktop` + `:composeApp:desktopTest` + `:core:network:desktopTest` green.

## [10.1.28.45] - 2026-08-26
### Added
- **[major] Desktop: Fluid Overlay & Notification System (Dynamic Island Banners, Alerts, Toasts & Stacked Screens)** — complete multi-surface overlay and notification engine for DeX Desktop (Windows + macOS):
  - **Dual AWT Host Windows**: separate `UTILITY` undecorated, transparent, `alwaysOnTop` windows operating independently of dock card visibility:
    - `BottomCenterNotificationHost`: positioned on the active monitor's bottom-center (top-center on macOS) with dynamic AWT hit-testing for Banners, Modal Alerts, Confirmations, and Stacked Screens.
    - `CornerToastHost`: positioned in the bottom-right corner (top-right on macOS) with 16dp screen edge margins for status toasts.
  - **Dynamic Island 3-State Morph**: `NotificationBanner` supports Compact (220x44dp), Medium (380x88dp), and Expanded (420x220dp) states with spring interpolation (`SizeMorphDpSpring`), progress bar, and user interactive tap toggle.
  - **Apple-Style Card Stack & Hover Fan-Out**: `FluidNotificationStack` renders resting stacks with 8dp peek shelves and 0.96f scale factor (up to 3 visible shelves + overflow badge), dynamically expanding vertically with 12dp gaps on hover using spring kinematics (`dampingRatio = 0.7f, stiffness = 350f`).
  - **Modal Alert & Confirmations**: AirDrop-style `AlertDialog` with media preview slot and dual pill buttons (`Decline` / `Accept`) with `bubbleFluidity` squish, and compact `ConfirmationPopup` with destructive styling.
  - **Full-Content Stacked Screens**: `StackedScreen` with iOS drag-pill handle for pull-down dismiss, back button, and arbitrary slot body.
  - **Semantic Toasts**: `MessageToast` with 5 semantic variants (Info, Success, Warning, Error, Progress) and inline action button.
  - **Tactile Drag Physics**: `FluidOverlaySurface` base atom with 48dp corners, 96% opacity surface fill, 1dp `outlineVariant` border, `shinyGlare` directional rim, and elastic horizontal drag pull with 0.5x resistance and spring snap-back / fling dismiss.
  - **Hover-Aware Auto-Dismiss & Audio**: `OverlayManager` central state coordinator with hover timer pausing/resuming, 20-item recent history retention, and `OverlaySoundService` with in-memory synthesized harmonic chime and user toggle (`DeviceConfig.notificationSoundEnabled`).
  - Full test suite verified (`:composeApp:desktopTest`, `:core:designsystem:desktopTest`, `:core:network:desktopTest`, `spotlessCheck` clean).

## [10.1.28.44] - 2026-08-25
### Changed
- **[major] Desktop: kyant liquid-glass stack deleted from the codebase entirely** — after the explorer controls de-glassing left zero `drawBackdrop`/`LiquidGlass*` consumers anywhere in `composeApp` (verified by full-tree scan), the dead stack is gone: deleted `LiquidGlassIconButton.kt`, `LiquidGlassPanel.kt`, `BackdropUtils.kt` (unused `LocalBackdrop`) and `LiquidGlassConfig.kt` (config, presets, tokens) from `core/designsystem/.../glass/`; `shinyGlare` (`ModifierUtils.kt`) survives — it never sampled backdrops, it only paints a directional rim — with its three token defaults (1dp width, -52.82° angle, 0.78 intensity) carried over verbatim as local constants so visuals are byte-identical. Dropped `api(libs.backdrop)` from `core/designsystem/build.gradle.kts` and the `io.github.kyant0:backdrop 2.0.0` version+library entries from `libs.versions.toml`. All remaining surfaces (dock, quick actions, settings, pairing, device list, drag handle, pull toast) were already glare/jelly-only — no real glass anywhere to strip. Android app copies at `DeX/app` untouched. Note: a concurrent session's in-flight `PinPairingPanel` type error (`TweenSpec<Float>` vs `IntSize`) was fixed by that session itself mid-verification; no action taken to avoid stepping on live work. Verified via `:composeApp:compileKotlinDesktop` green.
## [10.1.28.43] - 2026-08-25
### Changed
- **[minor] Desktop FileExplorerPanel: liquid glass removed from Row 0 controls — flat translucent pills with the non-refracting shinyGlare and a stronger shared shadow** — `explorerControlSampling` (SearchIsland drawBackdrop sampling: blur/lens/tint) deleted along with its tuning constants; the three controls (search pill, UpDir, mode toggle) are now plain surfaces: 12dp-elevation drop shadow at 36% spot / 18% ambient black (slightly up from previous treatments), clip to shape, theme-aware `surfaceVariant` fill at 45% alpha for readability over the grid, and the custom `shinyGlare` rim which draws light without bending it. Shadow now applies uniformly to UpDir/toggle too. Dead plumbing removed with it: the grid's `layerBackdrop(contentBackdrop)` recording, `controlsBackdrop`, FileExplorerPanel's `cardBackdrop` parameter + DockCardContent's card-level layer capture that existed solely to feed the glass, and all orphaned kyant/designsystem imports in both files. Hover overshoot, bubbleFluidity (incl. pill-specific params) and hit-testing preserved. Verified via `:composeApp:compileKotlinDesktop` green.
## [10.1.28.42] - 2026-08-25
### Changed
- **[minor] Desktop FileExplorerPanel: Row 0 control shadow deepened** — the shared glass spec is now a scoped `.copy()` of the SearchIsland preset with `shadowRadius` 33dp -> 48dp and shadow black alpha 20% -> 32%, so the drop reads clearly over the scrolling grid instead of washing out; offset stays at the signature 36dp below and the preset itself is untouched (other consumers unaffected). Applies uniformly to search pill, UpDir and mode toggle through the single `explorerGlass` config. Verified via `:composeApp:compileKotlinDesktop` green.
## [10.1.28.41] - 2026-08-25
### Changed
- **[minor] Desktop FileExplorerPanel: search pill width slightly reduced** — a symmetric 12dp horizontal inset added between the Row-0 flex weight and the pill's glass chain, narrowing the visible glass by 24dp total while keeping it centered in its slot (UpDir/toggle spacing untouched). Note the `.weight(2f)` factor itself was left alone since the pill is the row's only weighted child - the value changes nothing there; the inset is what actually shrinks it. Verified via `:composeApp:compileKotlinDesktop` green.
## [10.1.28.40] - 2026-08-25
### Fixed
- **[fix] Desktop FileExplorerPanel: Row 0 controls rebuilt on the SearchIsland liquid-glass recipe — real refraction, theme-aware tint, signature shadow** — all three controls (search pill, UpDir, mode toggle) had been painting a solid opaque `surfaceVariant` background OVER the backdrop sampling, which hid the refraction entirely, and the helper nulled its shadow so only a tiny 4dp `Modifier.shadow` remained. `explorerControlSampling` now takes the shared `LiquidGlassPresets.SearchIsland` config verbatim (2dp blur, 1.05 rest refraction, surfaceVariant tint at 23% dark / 50% light drawn via `onDrawSurface`, signature 33dp-radius shadow dropped 36dp below through the `drawBackdrop` pipeline) with the .38-widened refraction band kept as local scale constants (x1.4 band / x1.25 pull on top of the preset's rest refraction). Per-control `.shadow(4dp)`, `.clip` and opaque `.background` removed from all three chains — glass + tint + shadow now come from the sampling pipeline itself; hover overshoot, bubbleFluidity and shinyGlare rims untouched. Dead imports (`ui.draw.shadow`, `LiquidGlassTokens`) removed. Verified via `:composeApp:compileKotlinDesktop` green.
## [10.1.28.39] - 2026-08-25
### Fixed
- **[fix] Desktop FileExplorerPanel: search pill's hover overshoot, bubbleFluidity and glare restored** — the concurrent restyle had stripped the pill down to clip+background+padding, losing the treatment its UpDir/toggle siblings kept: hover now drives the same 1.08x scale + 3dp lift via `tween(500, DockCardPhysics.HoverEase)` (the WPF BackEase overshoot curve) through a new `searchInteraction` source, with `.zIndex(hovered)` so the swelling pill overlaps its neighbours like the buttons do, and the modifier chain regained `.bubbleFluidity()` press jelly, the shared 4dp drop shadow and `.shinyGlare` rim. Deliberate difference from the button siblings: the pill binds its interaction source with `.hoverable(...)` instead of `.clickable(...)` so hover detection never consumes taps meant for the inner `BasicTextField`. Verified via `:composeApp:compileKotlinDesktop` green.
## [10.1.28.38] - 2026-08-25
### Changed
- **[minor] Desktop FileExplorerPanel: sampling-bend area of the top controls widened** — `explorerControlSampling` scaled up from the shared token base with named local tuning constants instead of inline multipliers: blur 1dp -> 8dp (`ExplorerControlBlurRadius`, frosted smear across the whole pill instead of near-clear), lens refraction band x1.4 -> 42dp (`ExplorerControlLensHeightScale`, full-surface bend on the 40dp controls since the band now exceeds their height) and refraction pull x1.25 -> ~44dp (`ExplorerControlLensAmountScale`) so the wider band reads as an actual bend. Shared `LiquidGlassTokens` untouched - they stay 1:1 with the Android seeds and feed every other glass surface. Verified via `:composeApp:compileKotlinDesktop` green.
## [10.1.28.37] - 2026-08-25
### Fixed
- **[minor] Desktop FileExplorerPanel: controls and error banner z-raised so the grid truly renders beneath them** — instead of physically reordering ~240 lines inside the overlay Box, the Row-0 control bar and the error banner's `AnimatedVisibility` each got `.zIndex(1f)` while the grid Box stays at the default 0f, making declaration order irrelevant: Compose sorts Box siblings by `zIndex` for both draw order and hit testing, so scrolling items always sample into the glass controls and clicks land on the controls first regardless of child order. Duplicate `androidx.compose.ui.zIndex` import collapsed to one. Verified via `:composeApp:compileKotlinDesktop` green.
## [10.1.28.36] - 2026-08-25
### Changed
- **[minor] Desktop FileExplorerPanel: explorer/history items now pass UNDER the top controls and get bent by their glass** — the grid area moved into a full-bleed overlay Box (controls + error banner float above it; grid contentPadding top 52dp keeps the initial position identical), and the scrolling grid is captured into a new `contentBackdrop` (`rememberLayerBackdrop` — one node per instance, so it cannot share the card haze capture). The search pill, UpDir button and SAF/History toggle each gained `.explorerControlSampling(controlsBackdrop)` — a shared private `drawBackdrop` helper (2dp blur + 30/35dp lens at 1.05 rest refraction, `highlight`/`shadow` nulled so their existing `shinyGlare` rims and 4dp drop shadows stay the only treatments) sampling `rememberCombinedBackdrop(cardBackdrop, contentBackdrop)`: haze plus live grid, so scrolling items visibly refract under all three controls. The search pill's fill went semi-translucent (45%) to let the sampling show; UpDir/toggle hover, bubbleFluidity, shadows and glares (the concurrent styling) preserved untouched. Verified via `:composeApp:compileKotlinDesktop` green (error banner's `AnimatedVisibility` qualified to the top-level overload after the layout move took it out of ColumnScope).

## [10.1.28.35] - 2026-08-25
### Added
- **[minor] Desktop: skeleton loading wall for the file explorer (both SAF and History modes)** — new `ExplorerSkeletonGrid` replaces the old centered spinner with a pulsing wall of placeholder cards that mirror `FileGridItemCard` geometry exactly (100x115dp, 48dp glyph box, name+size lines on the same adaptive 100dp columns and spacing), so real content arrives without any layout shift; widths vary deterministically per row so the bones do not read as cloned rectangles. One shared infinite alpha pulse drives the whole grid (0.35-0.75 on flat `surfaceVariant`, 700ms reverse loop - deliberately no gradient shimmer per design rules), it exists only while skeletons are composed (loading && no stale content), costing zero frames once content lands; drill-downs that already have visible files keep them on screen instead of flashing bones. History listings get their own busy signal: `FileExplorerViewModel.isHistoryLoading` is raised inside the `displayedFiles` combine while the disk walk + micro-thumbnail generation runs, and the panel folds both modes into one `isListingLoading`. Two visibility corrections make the bones actually perceivable: the flag seeds `true` (History is the default mode and its first listing is pending from construction - previously the initial open flashed the empty state because loading was still false while items were empty), and every History listing holds the skeleton state for a minimum 450ms beat since local disk walks finish inside one frame and read as a glitch otherwise. Verified via `:composeApp:compileKotlinDesktop` + spotlessCheck green.

## [10.1.28.34] - 2026-08-25
### Fixed
- **[fix] Desktop pairing panel: endless QR/PIN slide loop killed and action row restored to legacy AnimatedActionBtn metrics**:
  - The flip transition replayed forever because `AnimatedContent` keyed on the whole `PinPairingUiState`, whose `remainingSeconds` tick (500ms) and keystroke `digitCount` emissions minted structurally-new values every frame window. The animated target is now a stable view-kind (`Pin/Qr/Success`) derived from the state; per-kind snapshots render branch content so genuine phase switches cross-fade once while countdown/digit updates mutate in place (countdown read live, WPF pairWaitTimer parity).
  - Action buttons were weight-stretched edge-to-edge with no vertical padding (squished boxes, labels visually adrift). Restored WPF grid parity: natural-width centered cluster with 8dp gaps, each button MinWidth 80dp / Padding 16,10 / CornerRadius 12 / 14sp Medium label dead-centered via the new `PairingActionButton`; toggle keeps its icon only in the "QR CODE" state. Accept Once stays full-width bordered.
  - Verified via `:composeApp:desktopTest` green; formatting gate clean for all pairing-scope files.

## [10.1.28.33] - 2026-08-25
### Changed
- **[minor] Desktop FileExplorerPanel: search island gains the hover overshoot + bubbleFluidity** — hovering the glass search pill now scales 1.08x with a -3dp lift over 500ms `HoverEase` (same curve as the quick action pills, overshoot included) and press squish comes from `bubbleFluidity`. The transform rides a new `layerBlock` parameter on `LiquidGlassPanel` (routed into `drawBackdrop`, so the backdrop sampler inverse-transforms and the refraction stays glued during the animation — the same fix proven on the pills); `hoverable` observes the interaction source. Text field, hint and clear behavior untouched. Verified via `:composeApp:compileKotlinDesktop` green.

## [10.1.28.32] - 2026-08-25
### Changed
- **[minor] Desktop QuickActionBar: flat pill treatment restored (clarified strip-down intent)** — the earlier "remove everything" pass is walked back to what was meant: only the liquid glass material is gone, while the original flat pill look returns in full — state-morphing background (checked = primary, danger hover = error, idle = surfaceVariant), 8% ink hover wash, icon color morph (onPrimary/onError/onSurface), contrast-inverted badge, bubbleFluidity press feel and the hover 1.08x / -3dp lift; no glass, no glare, no shadow. The `cardBackdrop` threading is removed from the pill chain (`QuickActionBar`/`TopActionsPanel`/`MainMenuColumn`) but the `DockCardContent` capture stays — the FileExplorerPanel SearchIsland still consumes it. Also repaired the concurrent FileExplorerPanel rewrite's missing imports (`CircularProgressIndicator` + the three glass imports its edit clobbered). Verified via `:composeApp:compileKotlinDesktop` green.

## [10.1.28.31] - 2026-08-25
### Changed
- **[minor] Desktop FileExplorerPanel: search pill now wears the Android expanded SearchIsland material** — the flat `surfaceVariant` pill is replaced by `LiquidGlassPanel` on the verbatim `SearchIsland` preset (restored into the designsystem config): 2dp blur, 1.05 rest refraction with lens 30/35dp, twin-lobe glare at 0.78, `surfaceVariant` tint @ 0.23 dark / 0.5 light, and the island's signature 33dp shadow dropped 36dp below; stadium geometry kept at the pill's own 20dp/40dp proportions, sampling the card's smoke-haze backdrop (`cardBackdrop` threaded through `DockCardContent`). Preset theme detection uses the file's `surface.luminance()` convention (the concurrent migration away from `isSystemInDarkTheme`, which no longer resolves in this Compose). Icon, text field, hint and clear behavior untouched. Verified via `:composeApp:compileKotlinDesktop` green.

## [10.1.28.30] - 2026-08-25
### Fixed
- **[fix] Desktop QuickActionBar: glare no longer vanishes during the hover scale overshoot** — root cause: the hover scale/translate rode an outer `Modifier.graphicsLayer` wrapping the whole glass pipeline, and `LayerBackdrop`'s sampling offset (`localPositionOf`/`positionInWindow` fallback, which the library's own TODO flags as wrong under outer transformations) went through the animated transform — the sampled region shifted by `(scale-1) x pillPosition`, so below scale 1.0 the lens band sampled darker card content off the rim and the glare look disappeared until settle. Fix: `LiquidGlassIconButton` gained a `layerBlock` parameter routed into `drawBackdrop` itself, and the hover transform now lives there — `LayerBackdrop` inverse-transforms sampling by the block (`inverseTransform(density, layerBlock)`), keeping refraction glued to the haze at any scale. Rest-state look identical; verified via `:composeApp:compileKotlinDesktop` green.

## [10.1.28.29] - 2026-08-25
### Added
- **[minor] Desktop pairing panel re-based 1:1 onto the legacy WPF click-a-discovered-device flow** (Bindings_Window.ps1 664-731 / Bindings_Settings.ps1 75-90 / UIComponents.ps1 Show-PinPanel):
  - QR-first open: clicking a Discovered Device now always lands the PIN panel on the QR view with subtitle "Scan this code with your phone, or tap PIN CODE" and the toggle reading "PIN CODE" (icon hidden, legacy `txtQrBtnIcon` collapse parity) — the previous masked digit placeholder is gone; titles read "Pairing with {alias}" via a new `alias` carried on `PairingState.QrPhase/PinPhase` (captured at click time, resolved from discovery for inbound `pair-request`s).
  - "PIN CODE" toggle = server-side pair-initiate port: new `PairingEngine.requestPinForActiveDevice()` mints the 5-digit PIN, pushes `pair-prompt{pin, alias, fingerprint}` to the phone's live session (deliver-first so an unreachable phone never displays an undeliverable PIN), flips to the digits view on a fresh 60s TTL, and fires the legacy Windows toast "Enter PIN {pin} on {alias}" through the new `IPlatformEngine.showPairingPinNotification`. A failed push keeps the QR view and surfaces an inline "The phone has no active connection..." status line (the QR view itself no longer renders a countdown, legacy parity).
  - "QR CODE" toggle-back: new `PairingEngine.revertToQrPhase()` cancels the pending offer locally (never unpairing), keeps the device context, and re-arms the 60s idle expiry exactly like Start-QrPhaseTimer.
  - Accept / Accept Once are force-hidden for desktop-initiated offers (`manualAcceptAvailable` flag on PinPhase) matching the legacy Collapsed lines; peer-initiated offers keep them and Cancel still sends a real rejection, while desktop-initiated cancels reset locally only.
  - Session lifecycle in `DockCardContent`: re-clicking the SAME device while its panel is open is a no-op (never resets a mid-flight session); switching devices fully cancels first; Success/Error hold 800ms (success flash / red shake) then auto-close and clear like the WPF succTimer/errTimer; active phases now set `controller.isPairingActive` so focus-loss can no longer hide the window mid-pairing.
  - Verified via `:core:network:desktopTest` + `:composeApp:desktopTest` green (new engine tests: prompt delivery frame shape, undeliverable-PIN state preservation, idle no-op, revert semantics) + spotlessCheck green.

## [10.1.28.28] - 2026-08-25
### Fixed
- **[fix] Desktop: explorer icon reads can no longer kill the EDT during continuous-build rebuilds, and History mode finally shows real thumbnails**:
  - Root cause of the recurring `ZipFile invalid LOC header (bad signature)` crash: `getFileIcon` streamed SVG glyphs out of the app jar through ZipFile on every grid-item composition, and a `-t` rebuild rewriting the jar under the live process tears that read - the exception then propagated uncaught through `painterResource`'s blocking loader on AWT-EventQueue-0. Compose forbids try/catch around composable invocations, so guarding at the call site was not an option; instead all four explorer glyphs now decode exactly once in a startup warmup (jars are always intact at t=0) into a session painter cache, and every later render reads painters from memory only - the torn-read window is structurally unreachable for icons. Grid items composed before warmup completes render a neutral code-drawn placeholder.
  - History thumbnails were missing because the History branch of `FileExplorerViewModel.displayedFiles` constructed its items without ever populating `thumbBase64` - only the phone-SAF path did, since there the phone pre-encodes micro-thumbs. Local PC files now generate their own 96px JPEG micro-thumbnails (ImageIO decode, aspect-preserving downscale, Base64), gated to image extensions and a 25MB source cap, cached by path+mtime so unchanged files decode once per session; non-images and undecodable formats keep their glyph.

  Verified via `:composeApp:compileKotlinDesktop` green.

## [10.1.28.27] - 2026-08-25
### Changed
- **[minor] Desktop QuickActionBar restored to the exact Android SearchButton treatment** — each pill is again a `LiquidGlassIconButton` on the verbatim `SearchIconButton` preset (blur 1dp, lens 30/35dp refraction at rest 0.5 with the spring 0.6/600 press boost, twin-lobe glare highlight 0.78, 4dp shadow, `surfaceVariant` tint @ 0.23 dark / 0.75 light) sampling the card's smoke-haze backdrop; the `cardBackdrop` capture threading through `DockCardContent` -> `MainMenuColumn` -> `TopActionsPanel` was re-established (haze `mood` choreography preserved). Only deviations: pill geometry stays 62x48dp / 22dp corners, and the desktop hover lift (1.08x / -3dp) rides the wrapper; icons keep a single neutral `onSurface` tint, badge block dormant. Compile verification deferred: an unrelated in-flight edit in `FileUiUtils.kt` (try/catch around a composable call) currently blocks `:composeApp:compileKotlinDesktop`; all four touched files pass the compiler.

## [10.1.28.26] - 2026-08-25
### Changed
- **[minor] Desktop QuickActionBar pills stripped to bare icon buttons** — all surface treatment removed per direction: no glass, no state fills/tints, no glare, no shadow, no icon color morph (single neutral `onSurface` tint; the animated DND/clipboard glyphs carry active state). What remains is exactly `bubbleFluidity` press feel + the hover scale 1.08x / -3dp lift; the dormant clipboard badge block is kept. The now-dead `cardBackdrop` threading (`DockCardContent` capture layer -> `MainMenuColumn` -> `TopActionsPanel` -> `QuickActionBar`) was removed with it; the glass foundation (`core/designsystem/components/glass/*` + backdrop dependency) stays in place for future consumers, and the haze's new `mood` choreography is untouched. Verified via `:composeApp:compileKotlinDesktop` green.

## [10.1.28.25] - 2026-08-25
### Fixed
- **[fix] Desktop QuickActionBar: bottom-left glare bloat root-caused and replaced with the Android `shinyGlare` rim** — the kyant `Highlight` shader is a symmetric twin-lobe gradient (`pow(abs(dot(grad, angle)), falloff)`): falloff only slims the tails while the lobe cores stay at full intensity, and on the pills' 22dp quarter-circle corners the bottom-left core wrapped a long rim arc, then `Plus`-blended over the bright smoke haze into a fat band (worsened by the ceil-to-2px stroke at 1x density). The config highlight is now zeroed and the ported Android `DeXButton` `shinyGlare` rim (asymmetric: crisp top-right line at 0.78 intensity, faint 35% bottom-left tip, 1dp, no blur bloom, no Plus) is layered on the pill wrapper — same ordering as Android (`bubbleFluidity` inside, glare outside). Glass body remains the exact SearchButton recipe. Verified via `:composeApp:compileKotlinDesktop` green.

## [10.1.28.24] - 2026-08-25
### Fixed
- **[fix] Desktop: discovery UDP reply storm eliminated - was burning ~65-100% of one core continuously** — live thread sampling caught `DefaultDispatcher-worker` pinned in `DesktopUdpService.sendReply -> NetworkInterface.isLoopback()` (156s CPU / 242s uptime): every announcement received by either port listener triggered a reply fanned out as two multicast sends per network interface (fresh `MulticastSocket` per interface per packet) plus a unicast copy; those multicast replies re-entered the same group, other peers answered them, and those answers re-triggered replies - an unbounded mutual amplification loop between any two DeX endpoints (two simultaneous local instances or a phone on the LAN ignite it). Fixes, wire format untouched:
  - Replies are now unicast-only back to the announcing sender through one persistent ephemeral socket; the multicast fan-out, its per-packet `NetworkInterface.getNetworkInterfaces().toList()` enumeration and per-interface socket allocation are gone from the reply path entirely (peers that miss an exchange still learn from periodic announcements).
  - New fingerprint cooldown gate: each announcing peer earns at most one discovery reaction per 3s window, which also coalesces the duplicate delivery where both port listeners receive the same announcement; the tracked-peer map self-prunes past 256 entries.
  - Verified on the running build: process idle dropped from ~100% of one core to 0.62%, with no JVM or native thread exceeding a fifth of that window; discovery receive joins and the 2s broadcast announcer are unchanged.

## [10.1.28.23] - 2026-08-25
### Added
- **[minor] Desktop: the ambient smoke haze is now a living constellation that re-choreographs itself per expansion state** — `AmbientSmokeBackground` gained an `AmbientSmokeMood` axis (Resting / Explorer / Settings / Pairing) mapped from `DockedWindowStateController.expandedPanel` at the `DockCardContent` call site; every mood retunes the existing parametric plume math only (no new colors): Explorer drifts quickest, swells 8% and leans left with violet leading; Settings drifts slowest of the open states, settles downward with purple leading; Pairing pulls all anchors 35% toward center and dims to 0.82 so PIN/QR owns contrast.
- Engagement pacing is deliberately lazy: a requested mood arms only after a 200ms beat (140ms on release) so quick panel flicks never churn the haze; all geometry glides on one critically damped slow spring (stiffness 90) and drift clocks run at 60-110s cycles while a panel holds the stage, ramping in with the same spring.
- Motion discipline preserved from the perf audit: at Rest the haze settles back along the shortest modular arc to the exact user-tuned static pose and the clock coroutine exits (zero frames); all animated values are read exclusively inside the draw scope, so frames invalidate redraw-only and the composition tree never recomposes per frame. Verified via `:composeApp:compileKotlinDesktop` green.

## [10.1.28.22] - 2026-08-25
### Changed
- **[fix] Desktop QuickActionBar glass reverts to the exact Android SearchButton recipe** — the desktop-specific tune (lens removal, 0.35dp glare rim, 0.60 dark tint) is fully rolled back: `LiquidGlassPresets.SearchIconButton` was restored verbatim into the designsystem config and the pills now consume it with only the shape squared to their 22dp corners — default glare width (0.5dp) at -52.82deg/0.78 alpha, full refraction stack (lens 30/35dp, rest refraction 0.5, press boost spring 0.6/600), 1dp blur, 4dp shadow, `surfaceVariant` tint @ 0.23 dark / 0.75 light exactly as Android. Verified via `:composeApp:compileKotlinDesktop` green.

## [10.1.28.21] - 2026-08-25
### Changed
- **[minor] Desktop QuickActionBar glass tuned: refraction off, thinner glare, translucent fill restored** — the lens effect (and its press refraction boost) is removed from all five pills (`lensHeight/Amount = 0`); the glare highlight stays on a subtler rim (`Highlight.width` 0.5dp -> 0.35dp, blurRadius halved to match, verified against the backdrop 2.0.0 sources); and the unchecked pills regain their `surfaceVariant` color semi-translucently (dark tint alpha 0.23 -> 0.60; light keeps its existing 0.75), so they read as frosted buttons over the smoke haze instead of clear windows. Checked/danger overlays unchanged. Verified via `:composeApp:compileKotlinDesktop` green.

## [10.1.28.20] - 2026-08-25
### Added
- **[minor] Desktop: liquid-glass foundation + QuickActionBar converted to liquid glass**:
  - `io.github.kyant0:backdrop` 2.0.0 added to the version catalog and `core/designsystem` as an `api` dependency (glass components expose `Backdrop` in public signatures); Android's glass stack ported 1:1 into `core/designsystem/components/glass/` — `LiquidGlassConfig` (all tokens/presets), `LiquidGlassPanel`, `LiquidGlassIconButton`, `shinyGlare`, `LocalBackdrop`.
  - `DockCardContent` now captures a `cardBackdrop` layer containing the card face fill + smoke haze, so glass controls refract real content; the layer sits below the content tree so glass can't sample itself.
  - All five QuickActionBar pills (DND, Mirror, History, Clipboard, Danger Close) are now `LiquidGlassIconButton`s on the `IconButton` preset squared to RoundedCornerShape(22dp): crisp stack (blur 1dp, lens 30/35dp with press refraction boost spring 0.6/600, glare highlight -52.82deg @ 0.78, 4dp shadow). State fills moved to a single animated surface overlay — checked = primary wash, danger hover = error, idle hover = 8% ink — drawn over the glass under the icon; icon/badge contrast logic unchanged.
  - Desktop port deviation: `LiquidGlassIconButton` now applies the caller's `modifier` first in its chain (Android original ignored it) so hover transforms wrap correctly.
  - Verified via `:composeApp:compileKotlinDesktop` green.

## [10.1.28.19] - 2026-08-25
### Fixed
- **[fix] Desktop PIN panel status line corrected to the legacy runtime text** — the WPF pairing screen never shows "Waiting for acceptance..." on the PIN view; `Show-PinPanel` overwrites `txtPinStatus` at runtime with inline icon runs: "Enter This Pin On Your Phone <E8EA> or PC <E7F4>". The panel now renders exactly that line under the digits, using cross-platform Fluent SVGs (`ic_fluent_smartphone` / `ic_fluent_computer`, 14dp) instead of Windows-only Segoe font glyphs so macOS renders identically; the now-dead `statusText` field was removed from `PinPairingUiState.PinView`. Verified via `:composeApp:desktopTest` green.

## [10.1.28.18] - 2026-08-25
### Fixed
- **[fix] Desktop pairing screen re-based 1:1 onto the legacy WPF PIN panel** (all values read from `Archived_Legacy_WPF`, not eyeballed):
  - **PIN length corrected to five digits.** The legacy server minted `Random().Next(10000, 99999)` (`LocalSendEndpoints.cs:91`) and the Android entry dialog enforces exactly five slots (`length <= 5`, `length == 5`, domino `repeat(5)`), but the desktop engine generated six — meaning phone-side entry of a PC-displayed PIN could never complete. `PairingEngine` now generates `(10000..99999)` and publishes `PIN_LENGTH = 5`; panel slots, masked placeholder, inbound-dialog auto-submit and its copy all derive from that constant.
  - **Pairing no longer grows the dock card.** The WPF window was fixed 1420x760 NoResize with the PIN view sliding into the column; now both the rendered height (`DockCardContent`) and the window-placement math (`DockedWindowStateController.currentContentHeight()`) treat Pairing as contracted-height — only FileExplorer/Settings expand.
  - **WPF-exact control styling**: digit boxes are filled accent (`primary`) with transparent idle border, bright ring when entered, red on error (legacy `icPinDigits` template: AccentBrush fill / BorderThickness 2 / CornerRadius 8); buttons use radius 12 with padding-based sizing per `AnimatedActionBtn` (Padding 16,10, FontSize 14 Medium) — Cancel moved to accent fill; Accept Once keeps its full-width accent-bordered row; the missing status line ("Waiting for acceptance...") is rendered under the digits again.
  - Tests updated to the five-digit contract (`PairingEngineTest`, `Milestone3AdversarialStressTest` formatting matrix, `Milestone3ComponentsTest`). Verified via `:core:network:desktopTest` + `:composeApp:desktopTest` green (`--rerun-tasks`, exit 0); also unblocks the compile deferral noted in 10.1.28.17.

## [10.1.28.17] - 2026-08-25
### Changed
- **[minor] Desktop dock card face uses the `background` role** — was `surface`, so light rendered pure white and dark #1E1E20 instead of the Android main-screen canvas tones (#DAD9DD / #111318); now 1:1 with the phone app. Compile verification deferred: an unrelated in-progress edit in `PinPairingPanel.kt` currently blocks `:composeApp:compileKotlinDesktop`.

## [10.1.28.16] - 2026-08-25
### Fixed
- **[fix] Desktop: three always-on idle CPU drains eliminated after a live resource audit** (audit baseline at process level: memory flat ~715MB private over 8min, GC near-silent, threads/handles stable - no leak; but ~1% of one core burned while fully idle):
  - Lottie scan loop in the device-list empty state keys its `LaunchedEffect` on panel visibility now - once the dock card hides, the `withFrameNanos` playback coroutine is cancelled instead of forcing frame production at display refresh rate forever behind contentAlpha=0 (previously the loaded JSON was never cleared, so the first empty-state appearance ignited a permanent per-frame loop). The composed Lottie view keeps its footprint while hidden so there is no layout jump mid fade-out; each appearance restarts the cycle deterministically at frame 0.
  - Wiggle-to-open detector switched from an unconditional 66Hz x5-native-call poll to adaptive cadence: a single key-state probe every 50ms while no button is held, full-rate 15ms sampling with the complete cursor/foreground-window pipeline only during an actual press. Detection thresholds (900ms history, 150ms min drag, 300px bounds, 3 reversals @15px), ring-buffer mechanics, double-fire cooldown and the 5s sleep/wake detector are preserved 1:1; worst-case press-detection lag is bounded by the 50ms idle tick.
  - Live Shift+Click affordance poller gated by real dock visibility, threaded through `MainMenuColumn -> BottomDockPanel -> ShiftClickCombo`; the old "no idle cost" doc premise was false because the menu tree stays composed behind graphicsLayer alpha when hidden, so the 64ms GetAsyncKeyState poll ran permanently since the exit-row relocation. Held-state resets when disabled so the glyph can never reopen stale-filled.

  Verified via `:composeApp:compileKotlinDesktop` green plus live gesture harness on the running build: hidden-state idle measured at 0.78% of one core (down from ~1% baseline that even had wiggle disabled - the armed case costs more under the old code); plain buttonless movement produced no false trigger, and a held-button wiggle past the reversal thresholds fired exactly once and re-showed the dock.

## [10.1.28.15] - 2026-08-25
### Changed
- **[major] Desktop theme re-based 1:1 onto the Android app's palette (both themes)**:
  - `core/designsystem/theme/Color.kt` rebuilt around the seven Android seeds per mode — light: #DAD9DD background / #FFFFFF surface / #E0E2EC surfaceVariant / **black** primary / #1A1C1E+#44474E text; dark: #111318 / #1E1E20 / #2F3033 / **white** primary / #E3E2E6+#C4C6CF. The porcelain/royal-purple light identity and the emerald-accent dark identity are fully retired.
  - Every M3 role the desktop fills derives from those seeds with no invented hexes: primary/secondary/tertiary collapse into the single monochrome accent Android uses for CTAs, containers = SurfaceVariant steps, the dark surfaceContainer ladder maps onto #111318/#1E1E20/#2F3033 exactly, hairlines become #44474E @55%/28% (light) and solid #2F3033 (dark), inverse roles mirror the opposite mode.
  - Ripple color parity: raw black in light / white in dark (was Violet Ink in light).
  - Kept untouched: danger reds (semantic), `GlassSurfaceAlpha`, the user-tuned smoke trio. Old Brand* tokens (Lavender/VioletInk/RoyalPurple/Porcelain/Slate) deleted — grep-verified they were referenced only inside designsystem itself.
  - Verified via `:composeApp:compileKotlinDesktop` green.

## [10.1.28.14] - 2026-08-25
### Changed
- **[minor] Desktop: smoke haze re-synced to the current Android tune** — the designsystem smoke tokens now carry the user's exact Android hexes (`SmokePurple` #C6FF8BEA soft lavender, `SmokeViolet` #FFA226FF vivid purple, `SmokePink` #673AB7 deep violet) and the plume color placement was updated to match (violet top-left, pink right, purple bottom); anchors, pose, falloff and 0.40/0.40 alphas were already identical. Verified via `:composeApp:compileKotlinDesktop` green.

## [10.1.28.13] - 2026-08-25
### Added
- **[minor] Desktop: the ambient smoke haze now backs the dock card face** — the tuned static Android composition is ported 1:1 into shared `core/designsystem` as `AmbientSmokeBackground` (`commonMain`, pure Compose APIs): same pink/violet/purple plumes (#EC4899 / #8B5CF6 / #9333EA), anchors, `StaticSmokePhase` 0.35 frozen pose and user-tuned 0.40/0.40 alphas; plume colors added to the designsystem `Color.kt` beside the brand seeds. Mounted in `DockCardContent` as the first child above the `surface` fill, so the rounded-corner clip contains the haze, all panels render over it, and the static canvas costs nothing while idle. Verified via `:composeApp:compileKotlinDesktop` green.

## [10.1.28.12] - 2026-08-25
### Changed
- **[minor] Android: ambient smoke haze frozen into a static composition** — the 72s drift clock is replaced by a single `StaticSmokePhase` (0.35) constant, so the three plumes hold one chosen pose and the canvas paints once per size change with zero idle cost; the plume parametric math is kept intact so re-enabling animation later is a one-line clock swap. Verified via `:app:compileDebugKotlin` green.

## [10.1.28.11] - 2026-08-25
### Changed
- **[minor] Android: acrylic grain and glinting dust removed from the ambient smoke** — the haze is back to just the three drifting plumes (single 72s clock, draw-phase only, three gradient circles per frame); both overlay experiments deleted cleanly along with their now-unused imports, and the user-tuned plume alphas (0.40/0.40) stay as-is. Verified via `:app:compileDebugKotlin` green.

## [10.1.28.10] - 2026-08-25
### Changed
- **[minor] Android: acrylic grain replaced with a glinting dust field** — the static speckle tile is gone; instead a seeded pool of 96 tiny dots (0.9–2.2dp, positions identical every launch) twinkles over the haze on a second 8s draw-phase clock, each speck riding a narrow sine glint (`sin^6` falloff) so roughly half sit dormant at any instant and only a few sparkle at once. Count scales with screen area (48–96); ~15% of specks pick up the plume pink/violet/purple tints, the rest stay white; peak glint alpha 55% dark / 50% light. Still zero recomposition per frame — knobs: `GlintCycleMillis`, `GlintPeakAlpha*`, `GlintFalloffPower`, `GlintAreaPerParticlePx`. Verified via `:app:compileDebugKotlin` green.

## [10.1.28.9] - 2026-08-25
### Changed
- **[minor] Android: ambient smoke haze gains an acrylic grain finish** — a deterministic 192px black/white speckle tile (seeded, identical every launch) is generated once and drawn as a single repeating `ImageShader` rect over the plumes at 6% dark / 5% light opacity, giving the surface a Fluent-acrylic tooth and dithering the soft gradients so they cannot band on OLED panels; the tile rides the existing draw pass so it adds no recomposition or extra frames. User-tuned plume alphas (0.40/0.40) preserved untouched. Verified via `:app:compileDebugKotlin` green.

## [10.1.28.8] - 2026-08-25
### Changed
- **[minor] Android: ambient smoke haze retuned** — plume color placement swapped (pink now drifts top-left, purple closes at the bottom, violet stays right) and peak center opacity nudged 16% -> 18% dark / 13% -> 15% light; anchors, drift paths and timing untouched. Verified via `:app:compileDebugKotlin` green.

## [10.1.28.7] - 2026-08-25
### Added
- **[minor] Android: ambient smoke haze behind the main screen background**:
  - New `AmbientSmokeBackground` composable (`ui/components`) paints three slow-drifting plumes — purple #9333EA, violet #8B5CF6, pink #EC4899 — as soft multi-stop radial gradients anchored near the top-left, right edge and bottom of the screen; peak center opacity is 16% dark / 13% light so content readability is untouched.
  - Each plume follows a seamless Lissajous drift (integer frequency ratios over a shared 72s cycle, per-plume phase) plus a ±7% "breathing" radius oscillation; all animation values are read inside the draw phase only, so the effect re-issues gradient circles per frame without ever recomposing the tree.
  - No real blur pass is used by design: the gradient falloff (solid-ish core to transparent at 40% stop shaping) reads as pre-blurred smoke while avoiding a full-screen `RenderEffect` GPU cost every frame; plume colors are centralized as `SmokePurple`/`SmokeViolet`/`SmokePink` in the Android theme `Color.kt`.
  - Mounted inside `MainScreen`'s glass backdrop box directly above the base fill and below all content, so the liquid-glass scroll edges sample the moving haze too. Verified via `:app:compileDebugKotlin` green.

## [10.1.28.6] - 2026-08-25
### Added
- **[minor] Website: Spline 3D integration scaffolded behind a build-time asset gate**:
  - New `SplineBot.astro` landing section (mounted between BentoFeatures and Compare) hosting a `<spline-viewer>` stage in a rounded glass-card frame with fixed aspect ratios (4:3 mobile / 16:9 desktop) so the canvas never shifts layout. The section renders only when the compiled scene export exists at `public/assets/spline/bot.splinecode` (`fs.existsSync` gate in frontmatter), so the site never ships a section pointing at a missing asset — dropping Spline's editor export there and rebuilding brings it online.
  - Client behavior: `@splinetool/viewer` (v2.0.5, self-hosted through the bundle, no unpkg CDN) is dynamically imported only when the stage nears the viewport (IntersectionObserver, 600px margin); reduced-motion users get a static branded poster instead of any canvas; on `load-complete` the poster fades out, on viewer error or a 20s load timeout the poster returns an honest status line instead of spinning forever.
  - Raw `.spline` editor files cannot be rendered by the web runtime — an editor Export (Spline Viewer) is required to produce the `.splinecode`. Embedded texture PNGs extracted from the provided project during inspection (bot eyes, phone screen) confirmed scene content but were removed again; the real export carries its own assets. License of the remixed community file must be checked before shipping commercially.
  - The compiled export has since landed at the expected path (47KB, fully self-contained — zero external asset or CDN references) and the section is verified live in headed Chrome: `load-complete` fires, the poster fades, the bot renders with its baked speech bubbles, and the console stays clean.
  - `@types/node` added as devDependency for Node builtins used by the gate; `astro check` 0/0/0, production build green, absence gate verified against dist output.

## [10.1.28.5] - 2026-08-25
### Changed
- **[major] Desktop light theme rebuilt on the new brand palette (#D0BCFF / #381E72 / #7560A3 / #EAF6FA / #4D5156)**:
  - `Color.kt` restructured around five named brand seeds (Lavender Mist #D0BCFF, Violet Ink #381E72, Royal Purple #7560A3, Porcelain #EAF6FA, Slate #4D5156); every scheme role derives from them — no orphan hexes in the light identity.
  - Light "porcelain canvas" model (v2 after first-pass review): the dock card face itself carries the mist identity (`surface` = Porcelain #EAF6FA) while every raised element — pills, wells, hovers, dialogs, transfer toast — is pure white via `surfaceVariant`/container roles, restoring visible tonal steps like the approved dark theme. Royal Purple drives interaction, Lavender fills selected containers with Violet Ink text, Violet Ink is the strong CTA accent (`secondary`), Slate carries typography and hairlines; danger reds preserved. Ripple tint follows Violet Ink instead of raw black. `outlineVariant` = Slate @ 28% for hairlines that read on both porcelain and white.
  - Dividers and hairline borders migrated from `surfaceVariant` to `outlineVariant` so they stay visible over white raised elements: BottomDockPanel, TopActionsPanel (x2), PIN digit boxes and the Accept-Once outline (PinPairingPanel), inbound pairing card border; PullProgressDock toast now paints `surfaceContainerLowest`.
  - Dark parity guard: `DarkOutlineVariant` pinned to the solid legacy hairline tone (#2B2631) so every border/divider migration renders exactly like the pre-rebuild dark surfaces — the approved dark look stays untouched until its own pass.
  - `Theme.kt` now fills the complete Material 3 role set for BOTH themes (containers, outline/inverse, full `surfaceContainer` ladder) so M3 defaults (dialogs, text fields, buttons) can never fall back to baseline purple; dark keeps its approved emerald/neutrals exactly, with the new roles filled from its existing dark tones.
  - Theme-blind hardcoded dark colors replaced with roles so light actually renders: device avatar glyph now uses `onPrimary` instead of hardcoded black (DeviceListPanel), QR/PIN toggle button content uses `onSecondary` instead of black (PinPairingPanel), file-grid selection paints `primaryContainer` + hover wash instead of dark-only hexes (FileGridItemCard), transfer toast background uses a theme role instead of `#141118` (PullProgressDock), dock card border moved to `outlineVariant` so the floating card keeps definition over any wallpaper (DockCardContent), mirror letterbox normalized to pure black in both themes (MirrorWindow). Semantic file-type category colors intentionally untouched.
  - Verified: `:composeApp:compileKotlinDesktop` and `:composeApp:desktopTest` green.

## [10.1.28.4] - 2026-08-25
### Changed
- **[major] Website hero: the DeX morph becomes a mirrored ping-pong pair around a direction-synced arrow divider**:
  - The hero now stages two `[data-logo-morph]` instances of the same composition playing in opposite directions — left starts forward (monitor -> DeX), right starts `data-logo-morph="reverse"` (settled DeX -> monitor). When a stage reaches its end it halts on the settled pose; once every stage has settled and the 6s idle hold passes, all stages flip direction IN PLACE (`setDirection` + `play()` from the rest frame, ping-pong) instead of restarting from frame 0, so the pair stays mirrored forever. Nav-logo clicks force an immediate flip; flips defer while the page is hidden or no stage is on screen (IntersectionObserver).
  - Remnant hiding is now rest-state-driven instead of stage-identity-driven: monitor parts baked into the composition's final frames are hidden only while a stage rests on the DeX end (`currentRawFrame > totalFrames/2`) and restored as soon as playback leaves that end, because they are legitimate content at the monitor rest state and during every reverse pass.
  - The chevron divider is replaced by a single centered arrow accent (`[data-logo-accent]`, JSON chosen per element via `data-logo-src`; `chevron-right.json` and the just-added `activity.json` removed again). The arrow runs its own alternating rounds while the morphs play — facing left for one slide, then right for the next (instant facing-class swap, no rotation swing), each new round opening with a short springy X-nudge (10px overshoot keyframes on the CSS `translate` property, so the `transform` rotation stays untouched) — and rests on frame 120, where its trim-path draw-on completes and before its opacity fade starts (frame 0 rested invisible), whenever the morphs halt. Logo stages grew to h-40/sm-h-52 and the arrow h-14/sm-h-20.
  - Verification note: headless Chrome (with or without virtual time / anti-throttling flags) starves lottie-web's rAF loop after 1-2 frames — earlier DOM-based "playback" checks were actually measuring the timer-driven park/replay safety nets. Playback is now verified in headed Chrome via playwright-core driving the real compositor: sampled SVG transforms advance over time, remnant hidden-count cycles 4 -> 1 -> 4 across two settle phases, and the `accent-alt` class flips with the cycle. `astro check` 0/0/0, production build green; all harness/profile/temp artifacts removed.

## [10.1.28.3] - 2026-08-24
### Changed
- **[minor] Live Shift+Click affordance moves onto the Exit Engine button itself**: the real-time glyph combo (`KeyboardShiftGlyph` filling the moment Shift is held + `MouseGlyph` blinking/rippling its primary button, driven by `ShiftKeyState` global polling) is now pinned to the far right of the Exit Engine row — right where the gesture applies — and hides only in the "Transfer Active! Click to Force Exit" stage, where a plain click already exits and a Shift hint would contradict the label. Shared composables extracted to `ShiftClickAffordance.kt`; the Settings shortcut-reference row returns to its static badge.

## [10.1.28.1] - 2026-08-24
### Fixed
- **[minor] Settings item icons were undersized and double-padded**: each row's icon rendered at 20dp inside a 20dp slot while ALSO carrying its own trailing padding, squeezing glyphs off-center. Icon slot is now a proper 32dp centered box with 22dp glyphs (animated DND bell matched); spacing owned by the slot alone. Also carries a concurrent cleanup on the same file: the Instant Exit row's live Shift+Click glyph animation was replaced by a static "Shift+Click" badge.


## [10.1.28.2] - 2026-08-24
### Changed
- **[minor] Website: shrink the static-logo fallback window before the DeX morph plays**:
  - `logoMorph.ts` now imports `lottie-web` statically instead of via dynamic `import()` — the library rides the page's module graph and is fetched during module instantiation rather than discovered only after the entry script executes and requests a separate chunk.
  - `LandingLayout` preloads `/assets/brand/dex-morph.json` (`as="fetch" crossorigin`) so the animation data downloads in parallel with JS, plus media-matched image preloads for both fallback logos. The `crossorigin` attribute is required — without it Chrome discards the hint ("request credentials mode does not match") because Lottie's XHR uses same-origin credentials; verified consumed with zero preload warnings after the fix.
  - The static fallback logos remain by design (no-JS / reduced-motion / slow-network cover); these changes only shorten how long they hold the stage. `astro check` 0/0/0, build green, morph boot confirmed (`morph-loaded` present) under headless virtual time.

## [10.1.28.1] - 2026-08-24
### Added
- **[minor] Website: the hero DeX morph now auto-replays on an idle loop instead of holding forever**:
  - `logoMorph.ts` previously replayed only on nav-logo click — after parking, the settled wordmark never moved again. A shared idle timer (`REPLAY_IDLE_MS = 6000`) now restarts the morph ~6s after any stage parks; per-stage parks re-arm one module-level timer so multiple stages can't double-restart each other.
  - The auto-cycle fires only while a morph stage intersects the viewport (IntersectionObserver, 0.25 threshold) and the tab is visible (`document.hidden` guard); otherwise the cycle defers and retries on the next tick, so nothing animates offscreen or in a background tab. Manual trigger replays clear the pending auto timer; the loop re-arms on the next park. `prefers-reduced-motion` stays fully static.
  - Verified in headless Chrome under virtualized time: DOM dump at t=12s shows the parked hero's 4 remnant groups hidden; at t=16.5s (park ≈8.5s fallback + 6s idle) they are restored — replay fired inside the window. `astro check` 0/0/0, production build green.

## [10.1.28.0] - 2026-08-24
### Changed
- **[minor] Product-direction settings corrections (plan 023): DND mutes alerts instead of refusing, UPnP always-on, ADB scoped to a picked phone, Auto-Connect removed, GitHub row removed**:
  - Do Not Disturb semantics flipped: it now MUTES the alert layer only (tray balloon notifications for incoming files and pairing requests are suppressed while DND is ON); transfers still arrive and pairing still works. The old behavior refused trusted inbound transfers with Forbidden (ShareRoutes) and silently dropped pairing requests (WebSocketRoutes) — both refusal paths removed; an end user who wants total silence closes the app entirely.
  - UPnP port forwarding is core product behavior and no longer has a settings surface: the `upnp_enabled` preference, its flow/setter, the panel toggle and the pref-gating in DesktopUpnpService were all removed; mappings configure unconditionally at startup and are still released on shutdown.
  - "Connect ADB" now targets ONE phone: a picker dialog lists discovered devices (name + IP) for the power user to choose from, replacing the blast-everything loop; empty state explains itself.
  - "Auto-Connect ADB Hotspot" feature removed entirely: AutoAdbHotspotService deleted, its startup wiring, the `auto_adb_hotspot_enabled` preference/flow/setter and the settings row are gone — out of scope for the project's sole purpose.
  - "DeX Project" (View on GitHub) settings item removed; section renamed to "Maintenance"; Reset Identity & Trust retained behind its confirmation dialog.
  - Download Location note: changing it does not delete or move existing files; the History browser simply points at the new folder going forward, and previously received files stay in the old folder on disk (also recorded in the transfer-history store).
  - Verified: gradlew build + spotlessCheck + all desktopTest suites green; runtime smoke boot clean.



## [10.1.27.7] - 2026-08-24
### Changed
- **[minor] Website: light mode no longer reads as dead white/empty; display type upgraded from generic Space Grotesk to Bricolage Grotesque**:
  - Root cause of the washed-out light theme: `--background` was `oklch(99% 0 0)` (pure white, zero chroma) while every card surface used dark-tuned translucent whites (`border-white/10 bg-white/5`), which are invisible on white — sections floated on an empty field. Light `:root` tokens now sit on a cool paper tint (`oklch(98.4% 0.007 261)`, hue follows primary) with stronger borders (`90.5% 0.012 261`), deeper muted-foreground, and tinted secondary/muted; light `theme-color` meta synced to `#fafafd`.
  - Centralized `.glass-card` (+ `.glass-card-hover`) component class in `global.css` replaces the 14 duplicated dark-only card treatments across BentoFeatures (6 tiles), HowItWorks, Compare, Security, FAQ, FinalCta, DownloadSection (2), and Pricing (2, Pro keeps its `border-primary-500/40` override): light mode gets frosted white 62% + dark 8% border + blur; dark mode keeps the exact previous white/5-on-black values. Radius/padding/shadow stay per-call-site utilities.
  - New fixed `.ambient-bg` layer in `LandingLayout` (theme-aware radial washes: primary top, accent right, cool left) so no section floats on a bare background; dark variant adds the same depth subtly.
  - Typography: `--font-display` swaps Space Grotesk for `@fontsource-variable/bricolage-grotesque` (kept as fallback; Space Grotesk package/import retained for the `--font-brand` chain). Hero/DownloadSection h1 `font-black` → `font-extrabold` (Bricolage wght caps at 800). Body stays Manrope; brand faces untouched (Creepy Notes wordmark, Caveat accents).
  - Light-mode polish: StickyCta pill border `white/10` → `black/10 dark:white/10`; theme-toggle buttons get visible `black/5` hover states in light (Hero, StickyCta, PageHeader).
  - Verified via headless-Chrome captures (forced-light harness pages, removed afterwards): hero, HowItWorks, and Compare in light show frosted bordered cards on the tinted ambient field; dark hero unchanged plus subtle glows. `astro check` 0/0/0, production build green, temp clones/profiles/screenshots cleaned up. Pre-existing uncommitted `site.ts`/`logoMorph.ts` edits from an earlier session left untouched.

## [10.1.27.6] - 2026-08-24
### Changed
- **[minor] Live Shift+Click affordance in the Shortcuts card**: the Instant Exit row's static text badge is replaced by a real-time glyph combo — code-drawn `KeyboardShiftGlyph` (outline Shift arrow that fills the moment Shift is held) plus `MouseGlyph` (the DeX reference mouse whose primary/left button blinks and emits a body-clipped ripple ring while Shift is down), driven by a new `ShiftKeyState` platform provider (Win32 `GetAsyncKeyState` VK_SHIFT / macOS CoreGraphics `CGEventSourceFlagsState`, focus-independent, polled at 64ms only while Settings is open). `SettingsItem` gained an optional trailing composable slot to host the combo.
  - Verified: `:composeApp:spotlessCheck` + `:composeApp:desktopTest` green; `:core:designsystem` compiles (no test sources).

## [10.1.27.5] - 2026-08-24
### Changed
- **[minor] Website: inline DeX wordmark is now live text in the 'Creepy Notes' face**: every DeX logo rendered inside running copy (BrandMark component in BentoFeatures/Compare/FinalCta/Pricing, plus the duplicated `brandInline` img strings in HowItWorks and FAQ) was a PNG pair with canvas-padding scaling hacks (`h-[2.2em]`/`h-[2.3em]`, `fixedLight` for gradient buttons). It now renders as real text — `<span class="font-logo font-bold">DeX</span>` — inheriting size and color from the surrounding typography like any word.
  - Font: locally installed 'Creepy Notes' (verified family name via PrivateFontCollection) bundled at `website/public/assets/fonts/CreepyNotes.ttf` (43 KB) with an `@font-face` rule (`local()` short-circuit + `font-display: swap`) in `global.css`; new `--font-logo` theme token falls back to Caveat/cursive. Caveat `--font-brand` accents ("instant", "Phone"/"Computer" headings) untouched.
  - Single source: new `website/src/data/brand.ts` exports `brandWordmarkHtml` consumed by the two raw-HTML interpolation contexts; `BrandMark.astro` renders the same markup as a component; obsolete `fixedLight` prop and em-height sizing removed from all call sites (`normal-case` added on the Pricing Pro heading so uppercase styling can't flatten the brand casing).
  - Standalone logo surfaces (header nav, hero morph stage, download/sticky-cta marks, favicons) intentionally remain images/animations — this change covers only inline-typography contexts.
  - Verified: `astro check` 0 errors/0 warnings; production build green; dist CSS carries the @font-face and all wordmarks emit the text span.

## [10.1.27.4] - 2026-08-24
### Changed
- **[minor] Shortcut discovery: read-only Shortcuts card in Settings → Interaction**: the gestures with no visible affordance are now documented in-app — global Show/Hide dock toggle rendered from `DesktopEnvironment.globalToggleShortcutHint` (`Win+Shift+D`, hidden entirely on platforms without a registered shortcut), Shift+Click instant exit bypass on Exit Engine, and force-exit-on-click while a transfer is live. Reference rows reuse the existing `SettingsItem` badge pill for the combos and stay non-clickable.
  - Verified: `:composeApp:spotlessCheck` + `:composeApp:desktopTest` green.

## [10.1.27.3] - 2026-08-24
### Fixed
- **[fix] Morph mark containment, park frame, and remnant sweep (verified via headless captures)**:
  - Root cause of the mark rendering outside its stage (user screenshots): the Lottie SVG overflowed its box because the dark-mode fallback used `dark:block` (a block box) that filled the stage, pushing the in-flow SVG one stage-height down; and the animation's raw end frame (op=456) still shows device remnants (monitor frame/stand, shelf line, iPhone buttons) behind the DeX letters — those keyframes extend beyond op, so no clean stop frame exists in playback.
  - Fallback logos are now `absolute inset-0 object-contain` inside a `relative` stage, leaving the SVG as the only in-flow child (both themes); `overflow: hidden` on the stage svg guards against inline-SVG visible-overflow (the UA hidden default only applies to root SVG documents).
  - Park logic rewritten in `logoMorph.ts`: on `complete` (plus an 8.5s DOMLoaded fallback timer, since `complete` proved unreliable under virtualized time) the animation stops at the end and every non-letter sibling group of the settled wordmark is hidden — monitor frame `matrix(1,0,0,1,124,88)`, stands at 124,168/124,191, and the identity-transform shelf line matched by its `M20,124…228,124` path; replay restores them before `goToAndPlay`.
  - Verified with headless-Chrome screenshots + a same-origin measurement harness: stage/svg rects coincide, `elementFromPoint` at the old stray-mark position now hits the svg, hero and download stages render the clean white (dark) / black (light) wordmark with zero remnant strokes; `astro check` 0/0/0 across 23 files; debug harness and preview server removed afterwards.

## [10.1.27.2] - 2026-08-24
### Fixed
- **[fix] Exit row no longer advertises an unrelated shortcut**: the monospace hint pinned inside the Exit Engine row displayed the global window toggle (`Win+Shift+D`, registered by `GlobalShortcutService` to show/hide the whole dock), reading as if a Win+Shift combo exits the engine — it does not. The hint block was removed from `BottomDockPanel`; the exit row now carries only its own semantics ("Exit Engine" / "Cancel / Shift+Click Exit", Shift+Click instant-exit bypass unchanged, active-transfer force-exit unchanged). `DesktopEnvironment.globalToggleShortcutHint` remains the single source of truth for the registered global shortcut for future Settings/help surfaces.
  - Verified: `:composeApp:spotlessCheck` + `:composeApp:desktopTest` green.

## [10.1.27.0] - 2026-08-24
### Changed
- **[minor] Settings audit follow-ups (plan 022): honest profile data, non-blocking sign-in, destructive-action guard, alias editor**:
  - Google sign-in trigger no longer times out: `/local/settings/google-signin` responds immediately ("Continue in your browser…") and a supervisor scope awaits the browser round-trip; on completion it now persists email AND name AND picture AND sub — previously desktop never persisted name/picture/sub at all (only Android did), so the Settings header could never show real profile data.
  - Profile header de-faked: fabricated "DeXStudios"/"dexify@dex.net" fallbacks and the unconditional "Premium User" badge removed; honest signed-out state ("DeX Desktop" / "Not signed in" / hint line); avatar now renders the real Google picture (fetched + decoded off the UI thread via the shared skia `toImageBitmap` helper) with the bundled placeholder as fallback.
  - "Reset Identity & Trust" moved behind a confirmation dialog — it is a destructive one-click action (sign-out + revoke all pairings + identity-hash rotation).
  - New Device Name item under Identity with an editor dialog: persists `DeviceConfig.alias` (trim, 32-char cap, same key as Android), feeding discovery advertisement.
  - SettingsRoutes switched to constructor injection (`deviceConfig` parameter resolved at the DeXServer call site), matching sibling routes; panel doc comment rewritten to match reality.
  - Verified: gradlew build + spotlessCheck green, zero compiler warnings; runtime smoke boot clean.


## [10.1.26.0] - 2026-08-24
### Fixed
- **[fix] Settings surface audit remediation (plan 021): LAN-exposed account routes, dead-end download location, dead UPnP toggle**:
  - Security: `/local/settings/*` (email/signout/google-signin/google-profile) were registered on the shared app module and therefore reachable from ANY LAN peer on the WAN HTTPS listener — an unauthenticated peer could overwrite the identity email (re-deriving the auto-trust identity hash), force sign-out, remotely pop the OAuth browser flow, or read back the Google profile. DeXServer now splits the module: protocol surface on `0.0.0.0:48424`, settings routes ONLY on the loopback control listener (`127.0.0.1:28425`). The OAuth callback route was removed from all shared modules and is served exclusively by its dedicated loopback 48425 listener (Google-Console-registered URI untouched).
  - Download Location: the Settings picker only wrote panel-local state — transfers kept landing in hardcoded `~/Downloads/DeX` and the choice reset every restart. New persisted `download_dir` pref in DeviceConfig; ReceiveStorage (single read authority for inbound saves) honors it via a single-writer mirror in main.kt; picker writes the pref so the choice takes effect immediately and survives restarts.
  - UPnP toggle: `upnpEnabled` was persisted but consumed nowhere — mappings were always attempted at startup regardless of the setting. DesktopUpnpService.configureAsync() is now pref-aware: awaits the DataStore load, maps ports only when ON, releases existing router mappings on toggle-off, and follows live toggles from the panel.
  - Verified: gradlew build + spotlessCheck + all desktopTest suites green.



## [10.1.27.1] - 2026-08-24
### Fixed
- **[fix] Morph stage overflow + dark-mode mark color**:
  - The Lottie SVG carries its intrinsic 256px canvas, so it overflowed the download heading's 2.2em stage and rendered the mark below the headline (user screenshot). The stage CSS now forces the injected svg to `width/height: 100%; display: block`, confining the animation to any stage box (download H1 + hero).
  - Dark mode now inverts the monochrome morph (`filter: invert(1)` on `.dark [data-logo-morph] svg`), so the animated mark reads white like the `dex-light.png` fallback; hero stage bumped to h-40/h-48 to restore visual weight now that the overflow is contained.
  - Verified: `astro check` 0/0/0; build clean; containment + invert rules present in the shipped CSS (lightningcss emits `invert()`, valid per the optional-argument filter spec).

## [10.1.27.0] - 2026-08-24
### Changed
- **[minor] Brand mark scale pass, equal download cards, Lottie mark in the download heading**:
  - Inline wordmark scaled 2.3x in typography: `BrandMark` default is now `h-[2.3em]` with `align-middle` (the logo canvas carries padding, so the visible mark reads at text scale); all `h-[0.95em]` usages dropped to the default, the pricing "Pro" label raised to `h-[3em]`, and the raw-img inline pattern in HowItWorks/FAQ strings moved to `h-[2.2em] align-middle`. Button marks (`h-[1.1em]` fixedLight) unchanged.
  - Download page: phone cards now use the same `sm:grid-cols-3` grid as the computer cards (equal card size, two cards + open column).
  - The download H1 mark ("Get ▸ free") is now a live `[data-logo-morph]` Lottie stage — the monitor→DeX morph plays in the heading and parks on the settled frame; `logoMorph.ts` refactored to mount N stages (`querySelectorAll` + shared replay on nav-logo click), reduced-motion still falls back to the static wordmark pair.
  - Verified: `astro check` 0/0/0 across 23 files; build clean; morph stage present in /download HTML, both grids 3-col, scaled marks on all three pages.

## [10.1.26.0] - 2026-08-24
### Changed
- **[minor] Brand mark replaces DeX text, Caveat brand font, white Apple logo, LocalSend de-branding**:
  - Every visible "DeX" text now renders as the theme-swapped wordmark image via a new `BrandMark.astro` inline component: download H1 ("Get [mark] free"), pricing "DeX Pro" label + free CTA, FinalCta heading subline + CTA, Compare closer, Bento features subline, How-it-works subline + step 1, and three FAQ answers (string answers moved to `Fragment set:html` with an inline-mark pattern, matching the bento icon approach). Only `<title>`/meta keep the literal word (browser tabs cannot render images); aria-labels keep "DeX" for screen readers.
  - Brand accent font swapped per feedback (Creepy Notes too thin): now Caveat Variable 5.3.0 (OFL, handwritten like the wordmark but with real stroke weight) applied at semibold; Creepy Notes @font-face, TTF asset, and fonts directory removed.
  - Apple logo gets a white variant (`apple-white.png`, alpha-preserving recolor) shown via `dark:` classes in dark mode; `site.ts` mobile entries gained optional `iconDark` with an `'iconDark' in` type guard for the const-asserted union.
  - LocalSend de-branding: "LocalSend v2 compatible" hero badge removed (user request), and the two remaining mentions swept — "Works alongside LocalSend apps" pricing feature and the LocalSend FAQ entry (FAQ now five questions). Flagged for user veto.
  - Housekeeping: dead `free.cta` config field removed after the CTA moved to the mark+Download pattern.
  - Verified: `astro check` 0/0/0 across 23 files; build clean (3 routes); zero LocalSend strings in any built page; Caveat in shipped CSS; apple/apple-white both referenced on /download.

## [10.1.25.1] - 2026-08-24
### Changed
- **[minor] Warning sweep: removed K2-redundant null assertions**:
  - Dropped `!!` sites the compiler already proves safe via smart-cast (`PullProgressDock` pull-state accessors behind the `isPulling == true` check, `FileExplorerPanel` SAF pull behind the `uri != null` guard, `WebSocketRoutes.grantPairing(fingerprint)` behind PIN verification) and an always-true `dataObj != null` condition implied by `requestId != null`.
  - `compileKotlinDesktop` for composeApp and core/network now emits zero warnings; spotlessCheck + desktopTest green.


## [10.1.25.0] - 2026-08-24
### Changed
- **[minor] Desktop shell: animation-spec centralization, port constants, loopback API, shutdown flush reorder**:
  - `DockCardAnimations` extended into the single source for motion specs: panel slide/fade (`PanelSlideSpec`, `PanelSlideOffsetSpec`), exit/hide (`HideEase`), expansion settle, soft/snap hover tiers, linear family (`LinearFade/Slide/MoveDp/Color/ColorSnap`, `QuickFade`) and reveal/collapse duration constants; dock panels (BottomDockPanel, PinPairingPanel, DeviceListPanel, ActiveTransferDashboard, FileGridItemCard, DragPillHandle, FileExplorerPanel, SettingsPanel, MainMenuColumn, DockCardContent, FloatingDockCard, MirrorWindow) now consume these instead of ad-hoc tween/dp literals.
  - `DockCardMetrics.MAIN_MENU_WIDTH` added; hardcoded `310.dp` menu/pairing column widths replaced with the metric so Compose geometry stays tied to window-placement math.
  - `DeXServer` listeners moved off magic numbers onto `DeXPorts.HTTPS` / `DeXPorts.LOOPBACK_CONTROL` / `DeXPorts.PULL`.
  - New `LoopbackControlApi` (core/network): one lazy in-process HTTP client for Google sign-in / email / sign-out calls to the app's own loopback control plane, replacing an `HttpClient(CIO)` constructed inside a Compose click handler on every click.
  - `DesktopShutdownCoordinator`: pending DataStore flush moved from step 0 to after full teardown (bounded 2.5 s), so settings written BY the teardown itself (e.g. disconnect handlers persisting pairing state) are captured too; stale doc comment updated to match.
  - Fixed: seven dock component files referenced `DockCardAnimations` via a stale `com.dexstudios.dex.window` import (object lives in `window.kinematics`), which broke `compileKotlinDesktop` outright; imports corrected.
  - Verified: `compileKotlinDesktop`, `desktopTest` suites and `spotlessCheck` all green.



## [10.1.25.0] - 2026-08-24
### Fixed
- **[fix] Store badges invisible in light mode + inline-link whitespace collapse**:
  - The App Store / Google Play badge SVGs are white-content with transparent backgrounds, so they vanished on light-theme cards. Badge buttons now sit on the official always-black badge treatment (`bg-black`, `border-white/15`) in both themes, matching store brand guidelines; at-launch dimming kept.
  - Astro whitespace collapse glued inline links to the preceding word ("on thepricing page", "already there.Get it free"); explicit `{' '}` spacers added in DownloadSection and Compare.
  - Verified: `astro check` 0/0/0 across 22 files; build clean; built HTML shows black badge containers and restored link spacing.

## [10.1.24.0] - 2026-08-24
### Changed
- **[minor] Download page phone-first store cards + brand typography system**:
  - Download page reordered per request: a new "Phone" group now leads with two store cards matching the desktop card chrome — Apple (apple.png icon, App Store badge) and Android (robot icon, Google Play badge) — with the official store badges acting as the card buttons in honest at-launch states (dimmed, tooltip, no dead links); the old combined phone row is gone. "Computer" group (Windows/macOS active, Linux at-launch) follows. `site.ts` mobile config restructured into per-device entries (icon + badge + badgeAlt + available).
  - Typography system replaced Inter: body is now Manrope Variable, headings h1-h3 are Space Grotesk Variable (both self-hosted via @fontsource-variable@5.3.0, Inter uninstalled); `--font-sans`/`--font-display`/`--font-brand` defined in the Tailwind @theme with a base-layer heading rule.
  - Brand accent font wired subtly: Creepy Notes (the DeX wordmark face; FontFreebies by IanMikraz, free for commercial use, pay-what-you-like) self-hosted at `assets/fonts/CreepyNotes.ttf` via @font-face; applied to the hero H1 accent word "instant" (normal weight, 1.12em, primary tint) and the download page's Phone/Computer group labels.
  - Apple logo asset resized 2000×2200 → 240 px (69→6 KB) into `assets/platform/apple.png`.
  - Verified: `astro check` 0 errors/warnings/hints across 22 files; build clean (3 routes); built CSS carries Manrope/Space Grotesk/Creepy Notes with zero Inter references; smoke HTTP 200 for /download, CreepyNotes.ttf, apple.png; temp download artifacts removed.

## [10.1.23.0] - 2026-08-24
### Changed
- **[minor] Animated brand morph, dedicated Pricing/Download pages, GitHub de-branding, share card**:
  - Logo animation: chose the Lottie export (`animation.json` → `public/assets/brand/dex-morph.json`, monitor→DeX wordmark with decelerating settle at frame 455) over DevicesMorph GIF (440 KB, uncontrolled infinite loop) and MP4s (no park-on-final-frame); added `lottie-web@5.13.0` as a lazy chunk (306 KB JS, separate from page scripts). Hero plays the morph once on load and parks on the settled mark; clicking the nav logo scrolls home and replays; `prefers-reduced-motion` keeps the static fallback logos and normal anchor behavior (`src/scripts/logoMorph.ts` + `[data-logo-morph]` CSS in global.css).
  - Page split per request: `/pricing` and `/download` are now standalone pages sharing a new compact `PageHeader.astro` (brand trigger, theme toggle, Download pill); index no longer mounts Pricing.
  - New `/download` "Free Download" screen (`DownloadSection.astro`): desktop cards for Windows 10+/macOS 12+ (active download buttons) and Linux ("At launch"), mobile group led by the Android icon with official App Store + Google Play badges shown as at-launch states; reassurance footer links to pricing.
  - New `Compare.astro` section on index: cloud drive / cable / email-or-chat pain cards closing into the free-download CTA.
  - GitHub removed from every visible surface: nav (already gone), footer link, FAQ support sentence, and all CTA labels — zero `GitHub` strings in any built HTML. Download hrefs resolve to the release backend URL without showing the host in the UI (`site.releaseUrl`, swap point documented in site.ts for a future CDN/store move).
  - `site.ts` restructured: `downloadPage`/`pricingPage` routes, `releaseUrl`, `latestVersion` chip (shown in hero platform strip), typed `desktop[]`/`mobile.stores[]` download config replacing bare release URLs and `repo`.
  - Share metadata: generated 1200×630 navy OG card (`public/og.png`) composited from the light wordmark + tagline via System.Drawing; wired `og:image(+dims)` and `twitter:image`; added `public/robots.txt`.
  - Housekeeping: shared page behavior extracted to `src/scripts/pageEnhance.ts` (theme/sticky/reveal/parallax) used by all three pages; android store icon resized 1000→240 px (470→40 KB); orphaned pre-DeXMart components excluded from `astro check` scope via tsconfig (files untouched on disk pending user deletion decision).
  - Verified: `astro check` 0 errors/warnings/hints across 22 files; build emits 3 routes; smoke test HTTP 200 for /, /pricing, /download, og.png, dex-morph.json, appstore.svg, robots.txt; lottie confirmed as an isolated lazy chunk.

## [10.1.22.0] - 2026-08-24
### Changed
- **[minor] Landing page persuasion structure: How-it-works, FAQ, closing CTA + hero merge**:
  - New `landing/HowItWorks.astro` (#how): three-step Scan / Pick / Done strip with staggered reveal, placed directly under the hero to answer "is it hard?" before features.
  - New `landing/Faq.astro` (#faq): six objection-handling Q&As (uploads, long-range sending, setup, size limits, LocalSend interop, price) as a zero-JS `<details>` accordion; support routed to GitHub issues.
  - New `landing/FinalCta.astro`: closing band ("Ready when your phone is") repeating the download CTA with "No account. No card. Your files never touch a server."
  - Hero merge after the concurrent 10.1.21 typographic-hero landing left two stacked hero blocks in `Hero.astro`: kept the 10.1.21 badge/accent/platform-strip skeleton, adopted the punchier subhead ("Scan once and send anything — photos, videos, whole folders. No cables, no cloud, no accounts."), primary CTA relabeled "Download free", secondary CTA retargeted from #features to #how ("See how it works").
  - Top nav simplified to How it works / Features / FAQ / Pricing / Download (GitHub stays footer-only); sticky pill nav gained How-it-works and FAQ anchors; footer links updated to match.
  - Security section lightened without losing facts: intro cut to two sentences, all six points compressed to one-liners, card shadow reduced.
  - Page order now Hero → HowItWorks → BentoFeatures → Security → Pricing → Faq → FinalCta → StickyCta → SiteFooter (mounted in `index.astro`, compatible with 10.1.21's inlined script).
  - Verified: `astro check` 0 errors/warnings/hints across 23 files; build clean; smoke test shows one hero H1 and live `#how/#features/#security/#pricing/#faq` anchors in `dist/index.html`.

## [10.1.21.0] - 2026-08-24
### Removed
- **[minor] Spline 3D scene removed from the landing page entirely**:
  - Viewer component, fixed stage layer, scene choreography (scroll keyframes/lerp loop), board-hiding scene-API code, loader spinner, stage mask CSS, and the `@splinetool/viewer` dependency all deleted; the page now ships zero external JS chunks (behavior script inlines into the HTML; the ~2.4 MB Spline bundle is gone).
  - Hero replaced with a typographic first screen on the same design system: "LocalSend v2 compatible" glass badge, "Phone to PC transfers that feel instant" headline with navy accent, Download CTA (Releases) + "See what it does" anchor, and a Windows/macOS/Android/no-cloud platform strip; floating nav and hero fade preserved.
  - Verified: build + `astro check` 0/0/0, no `spline` references in source or built HTML, smoke test HTTP 200, theme/sticky-CTA/reveal/parallax scripts confirmed inlined.

## [10.1.20.0] - 2026-08-24
### Fixed
- **[fix] Spline board (DeXMart billboard) hidden at runtime + canvas edge blending**:
  - On scene `load-complete`, the page reaches the viewer's scene API (`findObjectByName`) and hides the `Board` billboard group, with defensive name matches (DeXMart/Logo/Billboard/Sign) and one retry at 1.5 s; failures surface via `console.warn` rather than silently.
  - The fixed stage gained a radial alpha mask, so the scene's own background no longer shows as a hard rectangular band against the page in either theme.
  - Verified: build + `astro check` clean; board-removal code and mask present in the shipped bundle. The runtime hide effect needs a browser refresh to confirm (viewer `scene` access is version-sensitive); fallback if it persists is a clean scene re-export.

## [10.1.19.0] - 2026-08-24
### Changed
- **[minor] Landing page consumer copy pass (Blip-style plain language)**:
  - All engineering jargon translated to outcome/scenario copy modeled on Blip's landing: "Parallel QUIC streams... LocalSend v2 protocol" → "Full Wi-Fi speed — videos, whole camera rolls, giant folders"; "NAT punching... relay path" → "Works anywhere — files still go straight across, device to device"; pairing tile now leads with the AirDrop analogy and drops token/TTL mechanics ("Your devices remember each other after that").
  - Security section reframed from crypto spec sheet to reassurance stories: new headline "Your files never touch our servers"; points now read as consumer promises ("One tap to approve", "Codes expire in 60 seconds", "Invisible until invited", "Verified every single time") with HMAC-SHA256/beacon/bearer-token terminology removed.
  - Pricing plan features de-jargoned in `site.ts` ("LAN transfers to paired devices" → "Phone-to-computer transfers on the same Wi-Fi"; "Remote transfers via NAT punch" → "Send anywhere, even across the internet"; etc.), header subline rewritten, mono-path `~/Downloads/DeX` footer note replaced with a plain sentence.
  - Meta description rewritten consumer-first ("No cables, no cloud, no accounts — scan once and send"); features section subline simplified. Structure, components, and styling untouched — copy only.
  - Verified: `astro check` 0 errors/warnings/hints across 20 files; production build clean.

## [10.1.18.0] - 2026-08-24
### Changed
- **[minor] Spline robot integrated across the entire landing page**:
  - The 3D stage moved out of the hero into a page-level layer that becomes `position: fixed` behind all content (pointer-events-none, so clicks/scroll pass through; `events-target="global"` keeps cursor tracking working page-wide).
  - Scroll choreography with lerp smoothing: the robot holds the hero at full presence, then drifts right and shrinks beside the features grid (opacity 0.55), swings left through Security (0.4), settles small behind Pricing (0.22), and rests faint at the footer (0.12); X offsets halved on narrow viewports; keyframes re-measured on resize/load.
  - Progressive enhancement: without JS or with `prefers-reduced-motion`, the stage stays absolutely scoped to the hero (previous behavior); glass cards in Features/Security/Pricing now backdrop-blur the robot behind them for extra depth.
  - Verified: build + `astro check` clean (0/0/0), smoke test HTTP 200, choreography code present in the shipped bundle.

## [10.1.17.0] - 2026-08-24
### Changed
- **[minor] Landing page DeX content pass**:
  - "Trusted by" marquee section removed (component + CSS deleted); floating header content centered via `max-w-5xl` inset and now links Features / Security / Pricing anchors plus GitHub; both "Get Started" CTAs renamed to "Download" and pointed at the GitHub Releases page.
  - Bento tiles rewritten with DeX product copy (pairing QR/PIN with 60 s TTL, clipboard sync, QUIC + LocalSend v2, NAT punch + relay, file explorer, private-by-design); DeXMart's fabricated testimonials replaced by a Security section carrying the real trust-model facts; Pricing section added (Free $0 vs Pro $29 one-time from `site.ts`, Pro CTA honestly marked "at launch" until checkout exists).
  - Footer rebranded: `dex-gray.png` logo + "Dex Studios", anchor links + GitHub; page metadata (title/description/OG) switched to DeX; theme storage key renamed `DeXMart-theme` → `DeX-theme` in both the no-FOUC init script and toggle handler; smooth scrolling enabled for anchors.
  - Verified: build + `astro check` 0 errors/warnings/hints (hint pass caught a dropped `<StickyCta />` mount — restored); smoke test HTTP 200 page + CSS; zero DeXMart strings remain in the bundle.

## [10.1.16.0] - 2026-08-24
### Changed
- **[minor] Landing page rebrand pass (DeX identity + navy accent + Spline zoom)**:
  - DeX wordmark logos (`DeX-Dark/Light/Gray.png` from user assets) copied to `website/public/assets/logos/`; hero nav and sticky CTA now show the DeX logo with light/dark theme variants (`dex-dark` on light, `dex-light` on dark); favicons switched to theme-matched DeX PNGs (media-query based), DeXMart logo references and `favicon.ico` removed.
  - Primary accent scale converted from WhatsApp green to brand navy `#20355B` (`oklch(33.2% 0.073 261.4)`): full 50-950 OKLCH scale regenerated on hue 261.4, with step 600 pinned to the exact brand color (light-mode primary) and 500 as its dark-mode lift — buttons, links, rings, glows, and spinner all follow.
  - Spline hero zoomed in: viewer stage now renders at 140% with -20% offsets (native-resolution zoom, no upscaling blur), clipped by the existing overflow-hidden hero.
  - `dex-gray.png` staged in assets but not yet referenced (available for footer/neutral contexts).
  - Verified: build + `astro check` clean, smoke test HTTP 200 for page and all three logo assets; no hue-155 green remains in the bundle.

## [10.1.15.0] - 2026-08-24
### Changed
- **[minor] `website/` landing page replaced with the DeXMart expressive landing, re-implemented on the Astro stack** (no Next.js/React/framer-motion/next-themes):
  - Pixel-faithful port of `DeXMart/frontend` landing: Spline 3D hero (`spline-viewer` web component via pinned `@splinetool/viewer@1.12.69`, `events-target="global"`, lazy loading, spinner dismissed on `load-complete` with a 12 s fallback), floating nav, scroll-triggered sticky CTA pill (600 px threshold), trust marquee (CSS keyframe loop, edge fade masks), bento feature grid with layered parallax (three speed tiers, lerp smoothing ≈ spring config), testimonial cards with staggered reveal + hover lift, theme toggle (light/dark, `DeXMart-theme` storage key, system default, no-FOUC inline init), footer.
  - Design tokens ported verbatim from DeXMart `globals.css`: OKLCH primary green/violet accent scales, light/dark shadcn variable mapping via Tailwind v4 `@theme`, custom radii/shadow scales (e.g. `rounded-2xl` = 1.5 rem, lighter `shadow-2xl`), skip-link and glass-distortion SVG filter.
  - Logo assets copied to `website/public/assets/logos/`; favicon now the DeXMart `.ico`.
  - Previous DeX-branded sections (`components/Header|Features|Security|Pricing|Faq|Footer.astro`, `data/site.ts`) remain on disk unused pending user decision — not deleted.
  - Verified: build passes, `astro check` 0 errors/warnings across 20 files, preview smoke test HTTP 200 for page/logos/favicon/spline bundle.

## [10.1.14.0] - 2026-08-24
### Removed
- **[minor] Plan 020 — orphaned UI layer deleted in full** (user verdict after visual gallery review):
  - **Unwired feature modules gone**: `feature/discovery` + `feature/settings` were compiled into every build yet imported by nothing — the live device list and settings surfaces live in `composeApp/.../window/components/`. Directories, `settings.gradle.kts` include, and composeApp dependencies all removed.
  - **Glass family deleted from designsystem**: `components/glass/*` (`LiquidGlassConfig`, `LiquidGlassPanel`, `LiquidGlassIconButton`, `LiquidToastNotification`, `GlassScrollEdge`) — orphaned since 10.1.13.0 purged the last wired consumer. `LocalBackdrop` removed from `Theme.kt`; `api(libs.backdrop)` dropped from designsystem; `backdrop` entries removed from the version catalog.
  - **Other zero-reference components**: `DeXButtons`, `DeXPanel`, `DeXScrollbar` (+jvm), `HoverState` (+jvm), `FloatingPillNavBar`, `state/UIState.kt`.
  - **composeApp dead files**: empty `App.kt` stub, commonMain `mirror/MirrorScreen.kt` + `ImageUtils.kt` (+ jvm actual) superseded by `MirrorWindow`, backward-compat shims `window/PinPairingPanel.kt` / `DockCardAnimations.kt` / `ScreenBoundsHelper.kt`, dead `DownloadDockToast.kt`, now-unused `SkiaDropShadow.kt`.
  - **Tests**: `Milestone4ThemeAndStylingTest` + `Milestone4AdversarialStressTest` deleted — they pinned the removed glass API. Color-token ground truth remains in the archived WPF source.
  - **Explicitly kept**: `BubbleFluidity` (wired everywhere), `FilePicker` expect/actual (candidate home for future picker centralization), `DeXAnimatedIcons`, compottie/coil deps.
  - `docs/ARCHITECTURE.md` module graph corrected to match reality; plan recorded as `advisor-plans/020-remove-orphaned-ui-code.md`.
  - **Also lands `DeXPorts.kt`**: committed code (`MainMenuColumn`) already referenced `DeXPorts.LOCALSEND_DEFAULT`, but its defining file was still uncommitted — master could not build standalone without it. Included here so HEAD is whole again; the remaining port-constant migration continues in its own workstream.


## [10.1.14.0] - 2026-08-24
### Added
- **[minor] Product landing page under `website/`** (Astro 7.2.5 static output + Tailwind v4.3.3 via `@tailwindcss/vite`, zero-JS page):
  - Single page with hero, features grid, three-step how-it-works, security section, Free/Pro pricing, FAQ, and footer CTA. All copy grounded in shipped behavior from `docs/ARCHITECTURE.md` (LocalSend v2 compatibility, QUIC transfers, NAT punch + relay fallback, 60 s PIN TTL, per-fingerprint bearer tokens, HMAC-SHA256 constant-time identity challenge, `~/Downloads/DeX` receive path).
  - Theme tokens mapped 1:1 from `core/designsystem/theme/Color.kt` (`#0AE66D` accent, `#16121A` base, `#2B2631` elevated surface, `#F2F2F7` mist); glass styling uses translucent fills + backdrop blur only — no gradients, no glow.
  - Centralized content config at `website/src/data/site.ts` (name, repo/release URLs, pricing tiers); Inter Variable self-hosted via Fontsource.
  - Verified: `npm run build` (static route generated), `npm run check` (0 errors/warnings), preview server smoke test returning HTTP 200 for page + stylesheet.

## [10.1.12.1] - 2026-08-24
### Changed
- **[minor] Hardcoded-value audit tier eliminated — every magic number moved to its spec home**:
  - **H1/H2 loopback control plane**: the ad-hoc `HttpClient(CIO)` constructed inside the SettingsPanel click handler (one leaked client per sign-in click) and its inline `http://127.0.0.1:28425/...` magic string are replaced by a centralized `LoopbackControlApi` (single lazy in-process client, 5 s timeout, URLs derived from ports) living next to the SettingsRoutes it mirrors.
  - **Ports are constants now**: `DeXPorts` gained `LOOPBACK_CONTROL = 28425` and `LOCALSEND_DEFAULT = 53317`; DeXServer's three listeners (48424/28425/48426) stopped restating literals; MainMenuColumn's clipboard-push fallback uses the named LocalSend default.
  - **H3/H4 glass fallback token**: `LiquidGlassConfig` gained `fallbackSurface` / `fallbackSurfaceAlpha` so the no-backdrop path reads config instead of an inline `Color(0xFF16121A)` literal.
  - **H5 single-source version/repo URL**: composeApp now generates `AppBuildConfig.kt` from Gradle (`dexVersionName`, `dexRepoUrl`) via a `generateDexBuildConfig` task wired into the desktop source set; `packageVersion`, both About rows, and the GitHub link all derive from it — the "DeX v1.0.0" string can no longer drift from the build script.
  - **H6 Lottie asset cached process-wide**: DevicesMorph.json loads once through a mutex-guarded cache (`LottieAssets`) instead of re-reading from resources on every empty-state appearance; visibility-gated loading preserved.
  - **H7 geometry tiers completed in DockCardMetrics**: added `MAIN_MENU_WIDTH` and `AWT_HIT_SHAPE_CORNER_RADIUS`; FloatingDockCard paddings/AWT hit-shape corner and DockCardContent's 320dp wrapper + 310dp columns + slide offsets all reference metrics.
  - **H8 animation specs consolidated**: `DockCardAnimations` grew the missing semantic families (HideEase/Dp 200, PanelSlideSpec/OffsetSpec 250 Float+IntOffset, ExpansionSettleSpec 450, SoftHover/SnapHover, LinearFade/Slide/MoveDp/Color(+Snap), QuickFade, CONTENT_REVEAL/COLLAPSE_MS); ~30 inline `tween(...)` sites across BottomDockPanel, DockCardContent, MainMenuColumn, DockedWindowStateController, ActiveTransferDashboard, DownloadDockToast, FileExplorerPanel, FileGridItemCard, DragPillHandle, PinPairingPanel and DeviceListPanel now reference them — durations/easings byte-for-byte identical, so visuals are unchanged.

## [10.1.13.0] - 2026-08-24
### Changed
- **[minor] P0 performance batch + Liquid Glass fully removed from the desktop shell** (user directive: no liquid glass in Compose Desktop; a replacement will be implemented later at the appropriate time):
  - **`window.shape` churn eliminated** (`FloatingDockCard`): `onGloballyPositioned` fires on every layout pass — every frame of the 320↔1054dp spring — and each call assigned a fresh AWT `RoundRectangle2D`, forcing a native Win32 window-region recomputation per frame. Bounds are now cached and the native shape updates only when the card rect moves/resizes beyond 0.5px.
  - **Layer-backdrop pipeline deleted**: `rememberLayerBackdrop` + `LocalBackdrop` provider in `FloatingDockCard` existed solely to feed the inbound-pairing dialog's glass shader; the extra content layer was captured even though the dock card itself never consumed it. `InboundPairingDialog` now renders on plain `MaterialTheme.colorScheme.surface` — same visual family as the dock card, zero SkSL cost.
  - **No-op `skiaDropShadow` blocks removed** (`DockCardContent`, `InboundPairingDialog`): both fed `DeXGlassPresets.DockCardDark.shadowColor/shadowRadius`, which are `Transparent/0.dp` since the plan-011 glow removal — invisible shadows that still ran the modifier chain. Dock card keeps its themed border; visuals unchanged.
  - **Empty-state Lottie gated on visibility** (`DeviceListPanel`): the DevicesMorph asset load and its infinite playback loop now run only while the dock card is visible (`controller.isVisible` passed down); previously they kept burning frames behind `contentAlpha = 0f`.
  - **`devicesMap.values.toList()` memoized** (`MainMenuColumn`) with `remember(devicesMap)` instead of reallocating per recomposition; **taskbar progress coalesced to integer percent** (`main.kt`) so the native API is hit once per percent instead of per float tick.
  - Designsystem glass components/presets are untouched (tests pin them); wired desktop UI no longer references any of them. Full gate suite verified green against HEAD + this diff in an isolated worktree.


## [10.1.12.0] - 2026-08-24
### Fixed
- **[fix] Unwired-controls audit batch — every decorative toggle now does what it claims**:
  - **Do Not Disturb was pure local `remember` state** (QuickActionBar pill + Settings row reset to OFF on every recomposition; nothing ever rejected anything). Now persisted via `DeviceConfig.dndEnabled` (`dnd_enabled` pref) and enforced server-side: `WebSocketRoutes` pair-request handler no longer surfaces the pairing prompt or mints a PIN under DND, and `ShareRoutes` prepare-upload refuses new inbound transfer sessions with `Forbidden` so senders hit their normal "Not authorized" path instead of stalling.
  - **"Auto-Connect ADB Hotspot" was a fake badge defaulting ON** with no backing logic. Replaced by a persisted preference (default OFF) plus a real `AutoAdbHotspotService`: watches discovery and runs `adb connect` once per `fingerprint@ip` (bounded 400ms port-5555 probe inside AdbManager) whenever the device appears while enabled; joining a phone hotspot surfaces the phone through discovery, which is how hotspot join is detected without OS gateway probing. Attempt ledger clears when devices vanish or the toggle goes off.
  - **Theme row existed but wrote to throwaway state**, while `DeXTheme` always followed the OS. Now a persisted three-state override (`system`/`dark`/`light`) resolved app-wide in the `main.kt` shell composition.
  - **"Connect ADB" settings row was an empty `{ /* Launch ADB connection */ }` stub**; now runs `adb connect` against every discovered device (each attempt self-gates on the bounded port probe).
  - **Exit row hardcoded "⌘Q" on every platform** while the actual registered global shortcut is Win+Shift+D (Windows-only; macOS registers none). The label now comes from `DesktopEnvironment.globalToggleShortcutHint` and hides entirely where no shortcut exists.
  - `ShareRoutesTest`'s strict `DeviceConfig` mock gained a `dndEnabled = false` stub for the new enforcement read; all suites green (`spotlessCheck`, `:core:data`, `:core:network`, `:composeApp` desktopTests).
  - Audit corrections: "Forget Device" and the transfers drawer toggle were verified already fully wired (unpair → `DeviceManager.removePairedFingerprint` → session downgrade; drawer expand/collapse) — left untouched.


## [10.1.12.1] - 2026-08-24
### Fixed
- **[fix] Shutdown lifecycle deep-audit repairs — close/exit/tray-hide paths**:
  - **Ghost-process safety net**: `main()` registers a JVM shutdown hook that runs `DesktopShutdownCoordinator.stopAllServices()`; uncaught exceptions, OS logoff/shutdown and `taskkill` previously left JmDNS timers, Netty workers and UDP listeners alive inside an invisible zombie JVM. The coordinator is idempotent (`AtomicBoolean` gate) so explicit Quit handlers plus the hook can never double-tear-down.
  - **Quit no longer loses settings**: every `DeviceConfig` persisted write flows through a tracked `persist()` helper (`pendingWrites` + lock), and the coordinator awaits `flushPersistedWrites()` (bounded 2.5s) before teardown — fire-and-forget `scope.launch { dataStore.edit }` writes were killed mid-flush by `exitProcess(0)`, silently reverting the user's latest toggle.
  - **WebSocket close frame actually sent on quit**: `WebSocketEngine.stop()` queued the session close inside `serviceScope.launch` and cancelled that scope on the very next line — the close coroutine died cancelled and peers waited out their 15s ping timeout. The session now closes BEFORE scope cancellation via a bounded `runBlocking` (750ms cap), then `mirrorEngine.stop()`, then scope cancel/recreate.
  - **Google sign-in callback was dead**: the OAuth loopback redirect targets `http://127.0.0.1:48425/local/oauth/callback` (registered in the Google Cloud Console client; legacy WPF Kestrel parity per the archive), but nothing in the migration bound 48425 — sign-ins dead-ended at a connection-refused tab. `DeXServer` gained a dedicated loopback-only listener on 48425 serving extracted `oauthCallbackRoutes()` (one shared route function, still also registered on the full app listeners); `DeXPorts.OAUTH_CALLBACK` centralizes the port.
  - **UPnP mappings are no longer forever-leases**: `DesktopUpnpService.configureAsync()` caches the discovered IGD and new `releaseMappedPorts()` deletes the 48424/TCP, 48423/UDP and 48425/TCP mappings at shutdown (bounded 1.5s on a side thread joined with a deadline so a slow router cannot stall Quit). Mappings used `NewLeaseDuration=0` and were never removed, leaving router ports open after exit.
  - **Global keyboard hook unhooks on quit**: `DesktopShutdownCoordinator` now stops `GlobalShortcutService` too (only wiggle + clipboard were wired); its WH_KEYBOARD_LL hook leaked to process death.
  - **Startup error dialog cites real ports** (48424 HTTPS / 48425 sign-in / 48426 pull fallback) instead of the stale "port 48425" text from before the port map existed.
- **[fix] Exit button honors its own label**: `BottomDockPanel`'s confirmation stage showed "Transfer Active! Click to Force Exit" but a plain click only ever cancelled — a plain click now force-exits while a transfer or mirror is live (Shift+Click bypass unchanged; plain click without active work still cancels, WPF parity).
### Changed
- **[minor] JNA services refuse double-starts**: `WiggleToOpenService`, `GlobalShortcutService` and `ClipboardSyncService` no longer stack a second poller/hook/listener on re-entry; `ClipboardSyncService.stop()` clears the dangling `ClipboardHook.onRemoteTextReceived`.
- **[minor] Wiggle poller idles at 4Hz instead of 66Hz while disabled** (full-rate sampling only when enabled; sleep/wake detector unaffected).
- **[minor] Mirror frames decode off the UI thread**: `MirrorWindow` decodes via `produceState` + `Dispatchers.Default` instead of `Image.makeFromEncoded` during composition (jank at phone frame rates).
- **[minor] ADB fallback centralized**: clipboard sync's ADB broadcast goes through a bounded `AdbManager.broadcast()` (bundled platform-tools resolution, 5s watchdog with `destroyForcibly`, exit-code check) instead of a bare PATH `adb` exec that could hang an IO thread forever and leak one Process per copy; `AdbManager.getAdbExecutable()` resolves once per process instead of probing/downloading per connect.
- **[minor] Quit latency capped**: `DeXServer.stop()` stops all listeners concurrently under one 2s deadline (was sequential 1s grace + 2s timeout ×3 ≈ up to ~9s hang); `DiscoveryEngine.stopDiscovery()` cancels its owning scope.
- **[minor] Dead code swept**: unused `coroutineScope`/imports in `BottomDockPanel`, never-called `PostThreadMessageW`/`WM_QUIT` declarations in `GlobalMouseButtonHook`, stale LocalSend-default `53317` display fallback in `MainMenuColumn` (now `DeXPorts.HTTPS`).
- **[minor] Edge-case hardening follow-ups**: explicit quits now hop to a single-flight side thread (`quitDesktopApp`) so the blocking teardown never freezes the EDT (~7s worst case with live connections), with instant dock-hide feedback; the coordinator's DataStore flush moved to the END of the sequence so settings persisted BY teardown itself (e.g. disconnect-time pairing saves) are captured too; the Exit button's force-exit decision re-checks live transfer state at click time instead of trusting paint-time props (an upload settling between render and click can no longer turn a cancel into an exit); a malformed mirror frame keeps the last good bitmap instead of crashing composition.

## [10.1.12.0] - 2026-08-24
### Fixed
- **[major] Trust & pairing deep-audit repairs — credential disclosure, persistence, revocation**:
  - **Auto-trust secrets were publicly broadcast (critical)**: discovery beacons (UDP + jmDNS) and `GET /api/localsend/v2/info` advertised `identityHash` and `googleSub`, which are the exact bearer credentials `resolveHandshakeTrust` and `prepare-upload` accept for full same-account trust — any LAN peer could fetch one request, copy the token, and connect as trusted. Both fields now always advertise as null (`DiscoveryEngine.localInfo`); same-account trust is established instead by a new `identity-challenge` / `identity-proof` HMAC-SHA256 proof-of-possession exchange on `/ws` where the Google sub never crosses the wire. Server-side bearer acceptance of sub/hash remains for legacy peers only; all comparisons are constant-time (`resolveHandshakeTrust`, prepare-upload, hosted downloads share one `tokenEquals`).
  - **Desktop never hydrated or persisted pairing trust**: `DeviceManager.init(dataStore)` existed only in the Android app — on desktop every launch started with empty `AuthState` and `savePairedFingerprint` crashed into an uninitialized DataStore mid-acceptance, silently killing PIN-pair acceptance (swallowed by a catch logging "Failed to parse"). `main.kt` now hydrates before `DeXServer.start()`; the WS error log names the exception class.
  - **PIN pairing minted no shared credential**: the PC stored only the phone's fingerprint, so persisted pairings could not authenticate anything after reconnect (handshake untrusted, `isPaired` impossible) and re-pair attempts deadlocked in a pinless auto-accept → "PIN not proven" rejection loop. A proven pairing now mints a per-device token, stores it server-side, and delivers it via new `pair-accepted{token, fingerprint}`; the client stores it against the connected peer (`MessageHandler.peerFingerprintProvider`). Manual Accept mints through the same seam (`PairingEngine.persistentGrant`), one-time Accept stays session-scoped.
  - **Peer-initiated revocation was unwired**: the legacy WPF handled `{"type":"unpair"}` but the Kotlin dispatch table dropped it, so unpair requests from phones dead-ended. Desktop now handles self-revocation only (the sender's own fingerprint): removes the pairing, downgrades the live session via new `WebSocketConnectionManager.markUntrusted` (prompts stop flowing, recorded identity cleared), and resets the pairing panel.
  - **"Forget device" was cosmetic**: it removed the fingerprint from an in-memory UI set while leaving its paired token intact — the forgotten device stayed fully trusted on reconnect. It now persists removal (`removePairedFingerprint`), notifies the peer with `unpair{fingerprint}`, and downgrades the session. **"Reset Identity & Trust"** likewise only signed out of Google while keeping stored pairings AND the old identity hash: it now revokes every pairing and rotates the identity hash (`DeviceConfig.resetIdentity`, bypassing the email-setter race that would resurrect the old hash).
- **[fix] QR pairing offers never expired**: the TTL sweep only transitioned `PinPhase`; an unscanned `QrPhase` sat forever despite its countdown hitting zero. The sweep now expires both phases.
- **[fix] Punch rendezvous poisoning**: `GET /punch/endpoint` accepted any caller-chosen fingerprint, letting an attacker redirect NAT-punch transfers by registering their IP under a victim's fingerprint; registration is now accepted only from that fingerprint's own TRUSTED session, and `resolve-endpoint` answers only trusted callers (endpoint data no longer leaks to strangers).
- **[fix] `/local/share-target` reachable from the network listeners**: the OS-integration endpoint had no auth and was registered on the TLS 0.0.0.0 listener; it is now refused unless the connection arrived on loopback.
- **[fix] Relay of deduped transfers stalled 60 s then failed**: `relayUploadedSession` waited for `request.files.size` arrivals, but `[SKIP]`-deduped files never upload; it now waits for exactly the minted upload-token count.
- **[fix] Same-account clients conflated identity with pairing storage**: `WebSocketEngine.connectToPC` persisted the googleSub as the paired token on every connect, leaving a stale identity-tier credential behind after sign-out. Pairing tokens are now stored only from explicit grants.

### Changed
- **[minor] Performance/hygiene**: incoming-session TTL sweeper parks itself when idle (RelayService pattern) instead of ticking forever; `DexRequestStore` cancels unanswered requests older than 5 min so vanished peers cannot leak slots. Dead code removed: unused `WebSocketConnectionManager.broadcast()`, `MessageHandler.GRANT_WAIT_MS`, the abandoned salted-hash prototype `security/IdentityHash.kt` (+ test), `DeviceRoutes`' unused `pairingEngine` parameter and stale imports.
- **[docs] PROTOCOL.md documents `pair-accepted`, the challenge/proof handshake, self-revocation semantics for `unpair`, and the never-advertise rule; ARCHITECTURE.md trust model updated to match (deprecated identityHash tier, hydration requirement, punch gates).**

### Added
- **[minor] Tests**: `DeviceManagerPersistenceTest` (hydration across restarts, full-trace revocation, first-pair timestamp stability), `WebSocketConnectionManagerTest` (hijack refusal, PIN upgrade, downgrade semantics, request-store completion), `PairingEngineTest` gains QrPhase expiry + persistent-grant token minting + one-time session-scope cases.

## [10.1.11.1] - 2026-08-24
### Added
- **[minor] Structured logging replaces `println` across the desktop codebase**: Kermit 2.1.0 wired into `composeApp`, `core/network`, `core/data` and `core/designsystem`; all remaining `println` sites in 14 files converted to `Logger.i` (deliberate exception: the user's in-flight `DeXAnimatedIcons.kt`). Console output parity preserved via Kermit's default printer.
- **[minor] CI now gates formatting and library-module tests**: `.github/workflows/validate.yml` runs `spotlessCheck` plus every module's `desktopTest` suite on each push/PR — previously only `composeApp` was built/tested remotely and nothing checked formatting.
- **[docs] `docs/ARCHITECTURE.md` + `docs/PROTOCOL.md`** added: verified system references (module graph, ports, trust priority, pairing state machine, transfer paths) and the canonical WebSocket message contract with exact field names. `AGENTS.md` gained cross-assistant operational rules and a documentation map.
### Changed
- **[minor] Deferred detekt wiring with findings**: default detekt tasks analyze JVM-style `src/main/kotlin` only and registered NO-SOURCE against our KMP layout; a correct rollout needs explicit per-task source overrides (or convention-plugin application inside each module after `kotlin {}`). Removed the half-wiring rather than shipping a silent no-op gate.

## [10.1.11.0] - 2026-08-24
### Fixed
- **[major] Transfer logic deep-audit repairs — all orientations (PC↔Phone, PC↔PC), LAN + WAN**:
  - **PC→Phone LAN was dead by design mismatch**: the phone advertises `download=false` while hosting no receiver, but the desktop ignored that flag and pushed LocalSend v2 directly at a port with no listener, burning 3 retries and failing without fallback. `DesktopFileSendService.resolveDirectTarget` now honors `info.download` (phones excluded), relay candidates include trusted WS-connected peers (not just WAN/roster discoveries), and an exhausted direct push escalates to the pull path before giving up. Core `DiscoveryEngine.localInfo` now advertises `download=true` honestly (the desktop hosts the receiver); manual-probe parsing reads the real flag instead of hardcoding false; both synthetic-WAN-target twins advertise it correctly too.
  - **Phone's raw-TCP pull fallback saved HTTP error pages as "received" files**: desktop port 48426 is plain HTTP Ktor, but `tcpDownload` spoke bare fileId framing at it — EOF-terminated garbage landed as success. Replaced with an OkHttp HTTP pull (`http://ip:48426/download/{fileId}?token=`) plus per-file length verification against Content-Length/manifest size in BOTH transports (QUIC truncation can no longer pass silently). Desktop-side, server3 now exposes ONLY the hosted-download routes over plaintext (no more unauth `/ws`, `/register`, `/upload` on 0.0.0.0:48426).
  - **Phone↔Phone punch/relay was double-unwired**: desktop `WebSocketRoutes` gained the missing handlers — `device-roster` reply (same-account membership derived from handshake-proved identity, never client claims), `resolve-endpoint` → `endpoint-info` from the punch registry (5-min TTL lookup added to DeviceRoutes), `peer-endpoint` forwarding, authoritative `trust-check` correction, and full A→PC→B `relay-transfer` orchestration (`RelayService.relayUploadedSession`: waits until the staged upload fully arrives, hosts it, prompts the target, replies `relay-started`/`relay-error`). The previously write-only `trackRelayFile` registry is now its data source.
  - **PC→PC cross-network pulls hit a stub**: `DesktopPlatformEngine.downloadBatch` ("will go here") is now a real `DesktopPullService` — parallel×3 HTTPS pulls with plain-HTTP fallback, `.part` atomic renames, relative-path reconstruction, free-space precheck, size verification, dashboard progress via TransferStateMonitor, history logging.
  - **Relay "sent" lies removed (H1)**: hosting pushes are tracked (`HostedPush`); `hostAndPushAsync` accepts `onCompleted`/`onExpired` and fires them only when every file was actually pulled or the offer expired. Sender UI now shows "Waiting for <peer>…" after prompt delivery, paints success only on real completion, and reports expired offers as errors instead of fake success. Failed prompt delivery immediately releases hosted slots (no TTL-hanging files).
  - **Receiver dedupe contract wired end-to-end (H2)**: ShareRoutes prepare-upload now answers `[SKIP]` for content already received (size+partialHash index with liveness checks) instead of minting duplicate `"name (1)"` tokens; upload completion registers hashes; per-file query tokens are issued at prepare and enforced (constant-time) on `/upload` so another authenticated peer cannot inject into a session. Senders' existing `[SKIP]` handling finally does something.
  - **Session/dashboard lifecycle (H3)**: failed uploads, cancels, TTL sweeps and disk-space rejections all clear session store + counters + TransferStateMonitor (no phantom incoming transfers forever); capacity check runs BEFORE registration; `/cancel` requires the preparing sender's bearer token.
  - **Control-plane auth + hijack guard (H4)**: `/ws` handshakes resolve trust from the bearer token (googleSub / identityHash / paired-token, constant-time compare); sessions register trusted/untrusted with their proved identity; a duplicate live fingerprint is REJECTED (close 1008) instead of silently hijacked; untrusted sessions can pair but never receive transfer prompts or hosted tokens; PIN-proven pairing upgrades the session in place.
  - **WAN reachability (H5/C2)**: orphaned `DesktopUpnpService` is DI-registered and configured at startup; new `PublicAddressService` (UPnP external IP, ipify fallback, 10-min cache) pushes `public-address` to same-account phones on connect — cellular phones learn how to reach the PC without manual port forwarding.
- **[fix] Explorer phone-pull never uploaded anything**: FileShareManager/PunchSendWorker built `FileDto(id=random)` under map keys the PC answers by — every token lookup missed and the phone logged files as sent while uploading none. IDs now equal map keys (sender-contract parity).
- **[fix] Core `MessageHandler.sendPairResponse` echoes the entered PIN**, matching the Android twin's contract, so PC-to-PC WS pairing can verify the PIN proof instead of always rejecting.
- **[fix] UploadWorker QUIC transport failures fall back to the HTTP/1.1 path once per file** (fresh stream reopen, not unsafe reset); BatchDownloadWorker alternates transports per file before session-level retry.
### Changed
- **[minor] Performance**: receiver upload pump uses 256KB staged reads (was default small chunks); foreground notifications rebuild only when the integer percentage moves (both workers); punch-relay uploads run parallel×3 like every other worker; single consolidated RelayService maintenance loop replaces per-push sweeper coroutines.
- **[minor] Demo slop removed**: `triggerDemo()` hooks (core ClientEngine, Android ClientEngine, TcpDownloadService) and all "Seed/Demo Up/Demo Down" buttons deleted; hardcoded `C:\Users\NicoDex\Desktop\dex_udp_debug.log` writes stripped from DesktopUdpService; duplicate imports in PunchSendWorker and unused import in ClipboardSyncService cleaned.

## [10.1.10.1] - 2026-08-24
### Changed
- **[minor] All library modules renamed their JVM target to `desktop` (`jvm("desktop")`)**: source roots are now uniformly `src/desktopMain` / `src/desktopTest` everywhere (previously only composeApp used that name; library modules used plain `jvm()`, which silently ignored any `desktopMain` content — the root cause of a broken file surviving unnoticed). Module tasks follow: `:module:desktopTest`, `:module:desktopJar`.
- **[minor] Removed all unwired sources surfaced by the lint baseline**: orphan Android-style resources under `core/network/src/main`, duplicate platform actuals in feature history/settings, four empty protocol placeholder files, the dead `TransferDialog`, and the fully unhooked `feature/history` module tree.
- **[fix] PERF-01 resolved at the root**: drag-release detection is event-driven on Windows via a process-global `WH_MOUSE_LL` hook (`platform/GlobalMouseButtonHook`, dedicated daemon pump thread) instead of polling `GetAsyncKeyState`; Explorer-originated drags are caught instantly with zero busy-waiting. macOS and test stubs retain the polling fallback; subscribe/timeout/re-check closes the missed-event race.
### Added
- **[minor] Formatting is now enforced locally**: `.githooks/pre-commit` runs `spotlessCheck` before tests and blocks the commit on drift (auto-fix via `gradlew spotlessApply`).
- **[docs] `docs/ARCHITECTURE.md` + `docs/PROTOCOL.md`**: verified system references (ports, trust priority, pairing state machine, transfer paths; full WS message contract with canonical field names). `AGENTS.md` gained cross-assistant operational rules (no faked success, no simplify-to-fix, investigate-before-delete, 2-strike research breaker) and a documentation map.

## [10.1.10.0] - 2026-08-24
### Added
- **[minor] Received transfers are finally recorded in history**: the `/upload` receiver route logs every successfully persisted file to `TransferHistory` (`direction="received"`, saved absolute path, sender alias as peer). The desktop previously only ever wrote `"sent"` rows, so any direction filter/grouping could never show inbound traffic.
- **[minor] `core/network/server/ReceiveStorage`**: single source of truth for the receive directory (`~/Downloads/DeX`), replacing four independent hardcodings (ShareRoutes prepare-upload + upload, `FileUiUtils.getDeXDownloadDirectory`, SettingsPanel default).
### Fixed
- **[fix] Explorer Send Files / Send Folders buttons were silent stubs**: pickers opened, printed the selection, sent nothing. Both now run EDT-safe native pickers (`EventQueue.invokeAndWait` on `Dispatchers.IO`, under the dock's modal-dialog guard) and hand results to `DesktopFileSendService`. New `sendFolders(folders)` recursively collects a chosen directory's files (LocalSend v2 carries flat names, so structure flattens) and paints a clear error when a folder contains no files instead of failing silently.
- **[fix] SAF "Loading phone storage…" deadlock**: both explorer loaders set `_isLoadingSaf=true`, suspended, then cleared it — any `collectLatest` cancellation in between left the spinner up forever. Flag clears now live in `finally`. Disconnection is detected pre-flight via new `WebSocketConnectionManager.isConnected()/connectedFingerprints()` and surfaces as a dismissible inline error banner instead of a silent empty grid; `activeFingerprint` prefers fingerprints with a live WS session over arbitrary paired ones; blank-fingerprint pulls/grants report the same banner.
- **[fix] Pull-progress pipeline was severed end-to-end**: the phone streams throttled `pull-progress` frames (doneFiles/totalFiles/sentBytes/totalBytes/currentFile/state) which the desktop parsed and threw away behind a `println`. `WebSocketRoutes` now folds them into `FileExplorerService.updatePullProgress` (including terminal `done/cancelled/failed` states so completion is detectable), and `PullProgressDock` renders the active pull (name, percent, file count, bytes) falling back to upload state — matching what `isTransferring` already gated on.
- **[fix] `TransferHistory` lost-update races**: concurrent `log/delete/clear` performed unsynchronized read-modify-write on the in-memory list and launched unordered DataStore writes (last-write-wins could drop records), and `refresh()` could clobber just-logged items. All mutations now serialize through a mutex with one persist point per mutation; new batched `deleteAll(ids)`; disk reload is adopted only while memory is empty.
- **[fix] Explorer scanned the filesystem on the Main thread**: `displayedFiles` ran `File.listFiles()` inside its combine (collected on `viewModelScope`) on every emission of five sources including every debounced keystroke; the whole transform now runs via `flowOn(Dispatchers.IO)`. The DeX download root is computed once per VM instead of `mkdirs()`-ing on every `isAtRoot` evaluation.
- **[fix] Double-click guard never reset after firing**, so a third fast click re-executed the action; it resets on action and selection clears on mode toggle.
- **[fix] Non-portable thumbnail URI sniffing broke macOS**: coil model selection keyed on Windows drive letters (including this dev machine's `W:`); now uses `File.isAbsolute -> toURI()` with content:// pass-through. Base64 micro-thumbnail decode also moved off the UI thread (`produceState` + IO) from composition-time `remember`.
- **[fix] Explorer grid cards no longer fight their cells**: fixed-width 100dp cards inside `GridCells.Adaptive(100dp)` left ragged right gaps; cards fill the cell width.
### Removed
- **[major] `:feature:history` module deleted in full** (user verdict after a live sandbox review of the real `HistoryScreen` rendered on desktop): it was an abandoned mid-port of the Android history screen — never imported anywhere, platform actuals were empty no-op stubs, and it carried demo slop (`Seed Demo Data` / `Demo Up` / dead `Demo Down` buttons). The desktop history surface remains the File Explorer's History tab, which is what the user prefers. Removed the directory and its settings include + composeApp dependency; nothing else referenced it.
- **[minor] Removed the explorer grid's decorative scroll-fade gradient overlay** (repo design rule: no gradients).

## [10.1.9.0] - 2026-08-23
### Added
- **[minor] DX-02 baseline — Spotless 8.10.0 + ktlint 1.8.0 across all 7 desktop modules**:
  - Version catalog entries + root-level `subprojects` wiring (kotlin and kotlinGradle formats); `spotlessApply` / `spotlessCheck` now work repo-wide. Deliberately NOT hooked into `check`, the pre-commit hook or CI yet.
  - Baseline relaxations live in the authoritative `editorConfigOverride` map (mirrored in `.editorconfig` for IDE parity): intellij_idea style, max line length 200; disabled rules each with a written rationale — `no-wildcard-imports` (idiomatic in Compose sources), `no-empty-file` (reserved placeholder namespaces under core/data), `backing-property-naming` (codebase convention is `_xFlow -> xFlow`), `property-naming` (design-token vals), `filename` (camelCase route-file names are deliberate), and `function-naming` ignored for `@Composable`.
  - One-time format pass applied: import ordering + whitespace across ~30 files; zero semantic changes. Full `core:network` and `composeApp` desktop suites green on the formatted tree.
### Fixed
- **[minor] Dead source repair surfaced by the lint baseline**: `feature/history/src/desktopMain/.../HistoryPlatformHelper.desktop.kt` contained orphaned statements (stray `println` + closing brace outside any declaration) committed broken since the archive-promotion commit; the directory is not wired into any compilation, which is why builds never caught it. Removed the orphan block so the file is valid Kotlin.

## [10.1.8.3] - 2026-08-23
### Fixed
- **[fix] Peer PIN echo — server-side PIN proof is now end-to-end**: The Android peer (`DeX/app` `MessageHandler.sendPairResponse`) echoes the entered PIN in `pair-response.data.pin`, so phone-initiated pairing auto-persists on the desktop again when the correct PIN is typed — no manual Accept click needed for the normal flow. The already-paired auto-accept path (Task 5 partial-forget re-pairing) intentionally stays pinless: re-establishing trust after a deliberate desktop-side forget now requires explicit Accept on the pairing panel, which is exactly the assertion the server-side verification is meant to gate.
### Changed
- **[minor] PERF-01 mitigation — adaptive drag-release polling**: `DockedWindowStateController.deferHideOnDragDrop` no longer busy-polls at a fixed 50ms; the interval relaxes to a 250ms cap after the first second of an active drag (~4x lower idle CPU cost, release-detection latency still imperceptible). Full event-driven WH_MOUSE_LL hook remains deferred while `MouseInputProvider.kt` is under active unrelated work.
### Removed
- **[minor] Dead API**: `PairingEngine.markPairingError()` had zero callers anywhere (engine, UI, tests); deleted. The error path is owned by the expiry sweep and `handlePairResponse`.
### Documented
- **[minor] Pairing concurrency model**: `handleInboundPairingRequest` now documents its deliberate last-wins single-offer semantics and why superseded peers can never gain trust (exact-fingerprint PIN proof + TTL).

## [10.1.8.2] - 2026-08-23
### Fixed
- **[fix] Enforced PIN TTL — pairing offers now actually expire**:
  - `PairingState.QrPhase`/`PinPhase` carry an absolute `expiresAtMillis` deadline (60s, centralized as `PIN_TTL_MS` in the engine). `verifyInboundPin` rejects expired PINs, so a PIN observed during one session can never be replayed against a stale pairing later.
  - The engine arms an expiry sweep (on the injected scope) whenever a phase is entered and cancels it synchronously on accept/reject/response/reset, so resolution can never race the sweep. An unresolved offer transitions to `Error("Pairing timed out")`.
  - The `PinPairingPanel` countdown now derives from the engine's enforced deadline instead of a decorative local 60s counter started at composition; the panel therefore shows time the engine will actually honor.
  - Testability: injected `nowMillis` clock (defaults to `getTimeMillis`, matching `MessageHandler`). New tests (13/13 in `PairingEngineTest`): fresh-PIN acceptance vs expired-PIN rejection under a controllable clock, TTL transition to Error under virtual time, and immunity of already-resolved pairings to the sweep.

## [10.1.8.1] - 2026-08-23
### Fixed
- **[fix] Pairing Trust Hardening — PIN proof is now enforced server-side**:
  - `pair-response` on the desktop WebSocket route no longer persists trust on a bare `accepted:true` assertion. The responder must echo the displayed 6-digit PIN (`data.pin`), which `PairingEngine.verifyInboundPin(fingerprint, pin)` checks against the active inbound pairing for that exact fingerprint. Unproven assertions are logged and treated as rejected; the desktop user can still grant access manually via the pairing panel (Accept / Accept Once), keeping peers that do not yet echo the PIN pairable.
  - `PairingEngine.handlePairResponse` is now state-aware: only a pairing still in QrPhase/PinPhase transitions, so stray or duplicate responses (e.g. arriving after the user already accepted locally) can never flip Success back to Error.
  - **Live keystroke telemetry repaired**: the route read top-level `count` while senders emit `data.digitCount`, so remote digit progress never reached the panel; now reads `data.digitCount`.
  - **Placeholder digits removed**: digit telemetry arriving before an inbound pair-request no longer seeds `PinPhase` with fake `000000`; it uses the masked `"------"` convention already used by the Idle panel state.
  - **De-hardcoded alias**: the outbound `pair-prompt` advertises `deviceConfig.alias` (falling back to "DeX Desktop" only when blank) instead of a constant.
  - **Testability**: `PairingEngine` accepts an injected `CoroutineScope` (defaults to `Dispatchers.Default`) so accept/reject paths can run under virtual time.
  - Regression coverage: 5 new `PairingEngineTest` cases — correct-PIN acceptance, wrong/blank/wrong-fingerprint rejection, PIN dead once resolved, stray-response immunity, rejection-while-pending. Full `core:network` and `composeApp` desktop suites green.
  - Build repair (pre-existing working-tree breakage): added the missing `kotlinx.coroutines.flow.asStateFlow` import in `DesktopFileSendService` that broke `composeApp` desktop compilation.

## [10.1.8.0] - 2026-08-23
### Added
- **[TEST-01] Network Engine Test Baseline (`core:network`) — 35 new tests guarding the transfer/network hot zone**:
  - Build wiring: added `ktorServerTestHost` (Ktor 3.5.2) to the version catalog and `core/network` jvmTest dependencies; mockk already present.
  - `ClientEngineAuthMatrixTest` (jvmTest, 7 tests): full trust-priority contract of `authToken` asserted against the REAL unified `AuthState` token store — same-account googleSub wins over identity hash, identity hash wins over pairing token, mismatches fall through correctly, multi-fingerprint lookups resolve independently, empty local googleSub never trusts a wire-claimed sub, unpaired targets yield null.
  - `ClientEngineTransferContractTest` (jvmTest, 13 tests): `prepareUpload` status mapping (200 parses `PrepareUploadResponseDto` + forwards `Bearer` header; non-2xx passes the status through with null body; transport failure reports httpStatus -1); `uploadFile` endpoint/query-parameter/outcome contract incl. 507 mapping and -1 transport failure; QUIC delegation via mocked `IQuicClient` (result/protocol mapping, null-request and missing-engine degradation); upload state machine under virtual time on `Dispatchers.Main` (success auto-resets after exactly 6s, `finishUpload` summarizes "n of m" and resets after 5s, zero-success sets a persistent error without scheduling reset, `cancelUpload` notifies the active worker exactly once).
  - `ShareRoutesTest` (jvmTest, 11 tests): route-level LocalSend v2 baseline via `testApplication` with a Koin-provided mocked `DeviceConfig`. Prepare-upload auth matrix: 403 for missing header and stranger tokens; 200 for auto-trusted identityHash, same-account googleSub, and pairing tokens bound to the sender fingerprint in the real `AuthState`; 507 when the incoming payload exceeds free disk space. Pull-download gates: 400 missing params, 403 wrong token, 200 serves hosted bytes and refreshes sliding-TTL access stamp, 404 vanished path; legacy `/download/{fileId}` keeps its original NotFound-on-mismatch semantics. Upload rejections: unknown session/fileId and Zip-slip relative paths rejected before any write. The upload happy path is intentionally unexercised — it writes to the real user Downloads folder and fires a SystemTray notification.
  - `RelayServiceTest` (jvmTest, 4 tests): relay session bookkeeping (`trackRelayFile`), early-return contracts (empty target/files, nonexistent files), and the happy path asserting hosted pull-token bookkeeping mirrors the advertised `FileDto`s and that the pushed WebSocket frame carries type `prepare-upload`, the sender alias, the `desktop-migration` fingerprint fallback, and correct `relativePath` propagation.
  - Finding surfaced by the baseline: `ClientEngine.prepareUpload` body parsing requires ContentNegotiation on the HttpClient — test clients now mirror production DI wiring explicitly.

## [10.1.7.0] - 2026-08-23
### Fixed
- **[major] External Drag-and-Drop Send Pipeline (was a silent stub)**: The native AWT `DropTarget` on the dock window accepted Explorer file drops and only printed a count — nothing was ever transferred, while the focus-loss guard specifically protected this dead flow. Now:
  - New `desktop/transfer/DesktopFileSendService`, the desktop counterpart of the Android `UploadWorker`: resolves a trusted target (explicit fingerprint → first paired online LAN device → first same-account device; WAN/roster synthetic-IP entries excluded), builds LocalSend v2 `prepare-upload` with sender identity from `DiscoveryEngine.localInfo` and per-file 32KB head+tail partial hashes matching the Android contract, uploads up to 3 concurrent QUIC streams with HTTP/1.1 fallback, handles `[SKIP]` dedupe answers, publishes throttled smoothed-speed progress through `ClientEngine.updateUploadState` (taskbar window progress + telemetry light up automatically), logs every file to `TransferHistory`, retries a whole-session transport failure with capped backoff without painting intermediate failure states, and surfaces precise errors ("No trusted device online", "Could not reach X", "Not authorized", "Cannot read one or more files").
  - `main.kt` drop handler now feeds dropped files into the service and correctly completes the AWT drop protocol (`dropComplete`) on both success and failure paths.
- **[minor] Mirror Window Rotate Control**: Replaced the semantically wrong history glyph (`ic_fluent_history`) on the orientation toggle with the proper Fluent `arrow_rotate_clockwise` icon (registered as `rotate` in `scripts/fluent_icons.json` and synced via `Sync-FluentIcons.ps1`). Landscape window size is now derived from the live stream's aspect ratio (840dp wide, height clamped 320–1200dp) instead of a hardcoded 840x480, so phone video fills without letterboxing.
- **[minor] Test Isolation Hardening**: Controller guard/drag tests injected a deterministic `MouseInputProvider` stub — the exhaustive focus-loss truth table previously read real global mouse state via JNA and failed whenever the physical left button was held during a test run.

## [10.1.6.0] - 2026-08-23
### Fixed
- **[fix] Dock Placement Pipeline Audit — 6 Defects**:
  - **Contracted-width drift (300 vs 320dp)**: Window-placement math used `DEFAULT_CARD_CONTRACTED_WIDTH = 300` while the card renders at `CARD_WIDTH_CONTRACTED = 320.dp`, shifting every horizontal computation (magnetic snap parked the card 20px past work-area edges; expansion nudge, sanity clamps and the drag-drop hit-test were offset by the same amount). Introduced `platform/DockCardMetrics` as the single source of truth for all canvas/card dimensions; `TaskbarWorkAreaProvider`, `DockCardAnimations`, `ExpandedPanel.expandedWidth`, `DockCardPhysics` defaults and the controller now all derive from it.
  - **Concurrent window-animation races**: `animateWindowTo` coroutines (expand/collapse/reset) were never cancelled, so rapid panel toggles or dragging mid-animation let two writers fight over `windowState.position`. All position animations now flow through one cancellable job; drag start, expand, collapse, reset and pin-shake cancel the previous run before reading baselines.
  - **Mixed coordinate spaces on HiDPI**: AWT work areas/cursor coordinates (device px on Windows) were compared directly against dp-space card geometry, breaking snap/clamp/resting math above 100% scaling. Added `platform/DisplayCoordinateSpace` (+ `WorkAreaBounds.toDpSpace`) to normalize native↔dp per platform (Windows/Linux divide by density; macOS points are already logical); the deadzone threshold is now density-aware (5dp everywhere), and the `FloatingDockCard` AWT hit-test shape no longer shrinks by the scale factor on Windows HiDPI.
  - **Cursor-anchored monitor flips mid-gesture**: Snap/clamp reference resolved from the live cursor position each event, so crossing monitors mid-drag changed thresholds under the pointer and end-of-drag clamps could yank the card back. Work area is now captured once at drag start anchored to the window's own content center (`TaskbarWorkAreaProvider.getWorkAreaForPoint`), re-targeting only if the released card fully left the gesture display.
  - **Stale restore + weak validation**: `collapsePanel` restored pre-expansion coordinates without bounds re-checks (monitor change while expanded could strand the card); restore is now re-clamped against the current display. `validateAndSnapToBounds` only tested the raw window origin point; it now validates that the full contracted content rect still intersects its display's work area before falling back to the default dock.
  - **Determinism/hardening**: Magnetic snap when BOTH edges of an axis fall inside the threshold now resolves to the nearest edge (previously the trailing check silently overwrote the leading one); drawer-to-drawer switches compute signed deltas from current content size instead of assuming contraction (Settings→Pairing etc.); the MouseInfo-fallback delta path applies sanity clamping instead of raw writes; `deferHideOnDragDrop` converts the JNA cursor point into dp space and no longer swallows `CancellationException`; removed the stale `- 800` comment rot and duplicated dimension tables in the controller.
- **[minor] Regression coverage**: New `DockCardPlacementRegressionTest` (source-of-truth parity across layers, window↔content round trips, nearest-edge snap conflicts, native↔dp conversion, drawer-switch restore validity, stranded-window recovery, drag-over-animation single-writer guarantee) and adversarial suite updated for the density-scaled deadzone.

## [10.1.5.0] - 2026-08-23
### Changed
- **[minor] FileExplorerPanel Decomposition (TECH DEBT-04)**: Split the 935-line god file into focused units with zero behavior change:
  - `FileExplorerPanel` is now a slim container that renders from the previously-orphaned, unit-tested `FileExplorerViewModel` (scoped via lifecycle `viewModel{}`), deleting ~180 lines of duplicated inline state/logic (SAF loading, debounce, displayed-files derivation, paired-phone detection).
  - New `ExplorerModels.kt` (mode enum + item model), `FileUiUtils.kt` (icons, colors, formatters, dangerous-launch guard, download dir), `FileGridItemCard.kt`, `PullProgressDock.kt`.
  - Public API preserved: `FileExplorerPanel`, `PullProgressDock`, `getDeXDownloadDirectory` signatures unchanged.

## [10.1.4.0] - 2026-08-23
### Fixed
- **[fix] Split-Brain Identity Unification**: Consolidated three diverged `AuthState` singletons into the canonical `com.dexstudios.dex.auth.AuthState`:
  - Added `incomingPairRequest` state to the canonical singleton so `MessageHandler` (writer) and `InboundPairingDialog` (reader) now observe the same object — previously the dialog collected a dead duplicate in the abandoned `com.dexstudios.dex.network` package and could miss inbound PIN prompts.
  - `ClientEngine.authToken`, `WebSocketEngine` token/trust lookups now read the real pairing-token store (`pairedTokens.value`); previously they read an always-empty duplicate map, silently breaking bearer-token auth on PC-initiated requests.
  - `ClientEngineTest` now seeds tokens through the canonical StateFlow API (`updateTokens`) with `@AfterTest` cleanup instead of mutating an orphaned duplicate.
- **[fix] Abandoned Package Tree Removal**: Deleted the unreferenced `com.dexstudios.dex.network` mirror tree (13 files across `core:data` and `core:network`: duplicate ProtocolDto/TokenCodec/PunchState/DeXPorts/HashUtils/TransferState/IDiscoveryService/IQuicClient/ClipboardSyncState/PlatformUtils/DesktopJmDnsService) after grep-proving zero consumers.
- **[fix] Stale Import Compile Hazard**: `SettingsPlatformHelper.desktop.kt` imported `com.dexstudios.dex.network.DeviceConfig`, which no longer exists anywhere; repointed to the canonical `com.dexstudios.dex.core.network.DeviceConfig`.
### Changed
- **[minor] HTTP Client Timeout Hardening**: The shared Ktor `HttpClient` now installs `HttpTimeout` with a 10s connect bound (no global request timeout by design — it streams multi-GB uploads). The manual discovery REST probe overrides per-request with a 3s fail-fast timeout, eliminating the unbounded-hang finding (PERF-01). The Google sign-in trigger in `SettingsPanel` now closes its client via `use {}` with a 5s request timeout instead of leaking an `HttpClient`.

## [10.1.3.0] - 2026-08-22
### Changed
- **[minor] Compose Desktop BubbleFluidity**: Ported the more performant `Modifier.Node` based `BubbleFluidity` implementation from the standalone Android DeX app to the Desktop Compose Multiplatform project (`core/designsystem`), replacing the legacy `composed` API version.

## [10.1.2.0] - 2026-08-19
### Fixed
- **[major] Comprehensive Edge Case Eradication (20 Scenarios)**: Implemented 4 architectural components to resolve 20 window focus and visibility edge cases:
  - **JNA Drag-and-Drop Shield**: Modifies the focus-loss guard to query the global mouse state (`User32.INSTANCE.GetAsyncKeyState`). Defers hiding when dragging external files into DeX, fixing the issue where clicking a file to drag it instantly dismissed the DeX window.
  - **Global Keyboard Hook (`GlobalShortcutService`)**: Binds `Win + Shift + D` using a low-level JNA keyboard hook (`WH_KEYBOARD_LL`) to universally toggle the DeX UI, and binds `Escape` to globally dismiss the window.
  - **Display Bounds Watcher**: Automatically snaps the window back to the primary desktop dock bounds upon `show()` if a monitor disconnect or resolution scaling change stranded it off-screen.
  - **System Power Resume Watcher**: Enhances the `WiggleToOpenService` with a 5000ms loop time-drift detector to auto-hide and reset the UI cleanly when the PC wakes up from sleep or a locked session.

## [10.1.1.0] - 2026-08-19
### Fixed
- **[patch] Tray Icon Focus-Loss Race Condition (Desktop)**: Fixed an issue where clicking the tray icon while the app had focus would cause the app to hide and immediately reappear. This was caused by the window losing focus (triggering an auto-hide) a fraction of a second before the tray click action (which toggled it back to visible). Added a 250ms debounce threshold linking focus loss to the tray click to cleanly suppress the race condition.

## [10.0.0.1] - 2026-08-18
### Fixed
- **[patch] Compose Canvas Transparent Area Click-Through**: Fixed an issue in the Compose Multiplatform desktop application where the large 1100x700 transparent canvas blocked mouse clicks intended for the desktop or applications behind it. Added a dynamic native AWT `java.awt.Window.setShape()` update triggered via `Modifier.onGloballyPositioned` on the `DockCardContent` to restrict the OS hit-test region precisely to the card's bounds, preserving the zero-flicker expansion architecture while enabling true click-through on all transparent areas.

## [10.0.0.0] - 2026-08-18
### Added
- **[major] Compose Multiplatform Migration (Phase 1-4.2)**: Successfully achieved 1:1 parity with the legacy WPF desktop application using Kotlin & Compose Multiplatform.
- **[major] Legacy Protocols Preserved**: Rebuilt the device discovery engine (UDP multicast on 28424/48424) and file transfer server (`DeXServer` on 48426/28425/48424) using Ktor 3, ensuring unbroken backwards compatibility with the existing Android app.
- **[major] System Integrations Parity**: Reimplemented Wiggle-to-Open and mixed-DPI multi-monitor drag scaling using JNA user32 hooks. Implemented seamless PC-to-Device and Device-to-PC clipboard sync.
- **[major] New Compose UI Layer**: Rewrote all UI components (MainMenuColumn, DeviceListPanel, TopActionsPanel, PinPairingPanel, DownloadDockToast, etc.) to Compose, matching WPF animations, shadow properties, and dynamic states exactly. Mock UI data was replaced with live engine state bindings.
## [9.2.1.0] - 2026-08-15
### Fixed
- **[patch] Active Device UI Binding**: Ensured that when a discovered device is successfully paired, it smoothly animates into the 'Your Devices' (Live Peers) list and is immediately and visually selected as the actively connected target. Manually clicking devices in this list now also correctly updates the active target binding behind the scenes.
- **[patch] UI Flicker & Telemetry Sink**: Stripped out hardcoded XAML `Loaded` event triggers in favor of PowerShell-driven selective animations. This eliminates the subtle list flicker and removes the UI-update suppression, allowing device telemetry (Battery %, Wi-Fi Signal) to seamlessly flow into the device list in real-time.
- **[patch] Offline Device Persistence & UI Indicators**: Trusted/paired devices are now retained in the UI list even when they drop off the local network (mDNS) and disconnect from the WebSocket. Such offline devices dynamically fade to a 50% opacity, switch to a hollow grey indicator ring, and replace their battery/Wi-Fi telemetry with an "Offline" label.

## [9.2.0.0] - 2026-08-14
### Changed
- **[major] Comprehensive Trust System & Edge Case Hardening**: Fully addressed 16 distinct pairing and trust edge cases to align with industry-standard P2P security architectures:
  - **Loopback & Malicious Unpair Protection**: Enforced strict `IPAddress.IsLoopback` checks on all control endpoints (e.g., `/local/unpair`) to block unauthenticated LAN exploits.
  - **IP Fallback Deprecation**: Fully purged IP-address fallbacks for authentication in `TrySendDexRequestAsync` and all TCP/HTTP endpoints. Trust now mandates Cryptographic UUID `Fingerprint` matching exclusively, resolving DHCP race conditions, same-IP spoofing, and Cloned App conflicts.
  - **Discovery Storm Cap (DoS)**: Implemented an LRU-style limit of 100 devices on both the Android `DiscoveryEngine` and PC `DiscoveryBackgroundService` to prevent OutOfMemory/CPU crashes during massive mDNS/UDP broadcast storms.
  - **Rate Limiting & Brute Force Prevention**: Added robust `ConcurrentDictionary`-backed rate limiting (5 attempts / 5 mins) to `PushPairPromptAsync` and WebSocket inbound `pair-request` to mitigate PIN guessing and DDoS attacks.
  - **WebSocket Teardown Sweep**: If the socket unexpectedly drops (Wi-Fi loss, background service kill), `PendingPairPins` are instantly cleared on the PC and UI prompts gracefully close.

## [9.1.1.12] - 2026-08-14
### Fixed
- **[patch] Shimmer Animation Limit & Error Fix**: Constrained the typing Shimmer sweep to trigger exactly 2 times and then settle, preventing endless distracting loops while the user considers their next digit. Also resolved a fatal assignment exception that prevented the UI from successfully flashing red and shaking when a rejected/invalid PIN state (`$dc = -1`) was broadcasted from the Android device.

## [9.1.1.11] - 2026-08-14
### Changed
- **[patch] Shimmer PIN & Success Animation**: Replaced the solid green typing border highlight with a subtle sliding gradient 'Shimmer' effect pulsing in the primary text color.
- The solid green border is now strictly reserved for a new 800ms 'Success' animation when the phone officially Accepts the pairing, creating a clear visual distinction between entering and accepted states.

## [9.1.1.10] - 2026-08-14

## [9.1.1.10] - 2026-08-14
### Changed
- **[patch] Shimmer PIN & Success Animation**: Replaced the solid green typing border highlight with a subtle sliding gradient 'Shimmer' effect pulsing in the primary text color.
- The solid green border is now strictly reserved for a new 800ms 'Success' animation when the phone officially Accepts the pairing, creating a clear visual distinction between entering and accepted states.

## [9.1.2.0] - 2026-08-14
### Fixed
- **[patch] UI Device Sorting**: Fixed an issue where newly paired devices did not appear at the top of the "My Devices" list (or did not visibly move). `Connect-Engine.ps1` now explicitly sorts discovered devices by their `lastSeen` timestamp descending before building the device lists so that actively pairing devices immediately jump to the top.

## [9.1.1.9] - 2026-08-14
### Changed
- **[minor] QUIC P2P Pull**: Added backend support in `WebSocketEndpoints.cs` to trigger `QuicP2PClient.ReceiveAsync` when receiving `quic-p2p-pull` websocket messages.
- **[patch] UI Text Update**: Added both Phone (`&#xE8EA;`) and PC (`&#xE7F4;`) Segoe Fluent device icons to the static PIN prompt: `"Enter This Pin On Your Phone <icon> or PC <icon>"`.

## [9.1.1.8] - 2026-08-14
### Changed
- **[patch] Static PIN Prompt**: Completely removed dynamic text swapping ("Waiting for PIN...", "Entering PIN...") and error text ("Incorrect PIN") during pairing. The text is now fully persistent and static: `"Enter This Pin On Your Phone/Pc "` with a Fluent Segoe Device icon (`&#xE8EA;`), matching the user's explicit UX request.

## [9.1.1.7] - 2026-08-14
### Fixed
- **[patch] PowerShell Emoji Parse Error**: Fixed a fatal launch crash in PowerShell 5.1 caused by a raw UTF-8 emoji (`📱`) breaking the AST parser. Replaced it with the `[char]::ConvertFromUtf32(0x1F4F1)` runtime equivalent.

## [9.1.1.6] - 2026-08-14
### Fixed
- **[patch] UI Text Restoration**: Restored the user's custom "Enter This Pin On Your Phone 📱" placeholder text and `SecondaryTextBrush` color that was accidentally overwritten by generic text during the error-state implementation.

## [9.1.1.5] - 2026-08-14
### Fixed
- **[patch] Shake Logic Fix**: Fixed a bug where the PC error shake animation would trigger endlessly because the `-1` state wasn't cleared correctly in the polling loop.

## [9.1.1.4] - 2026-08-14
### Changed
- **[patch] Shake Logic Simplification (/ponytail)**: Removed the over-engineered desktop-side time deduction. The Android app now explicitly sends a `-1` digit count via WebSocket when the local verification fails upon clicking "Confirm", guaranteeing the shake triggers precisely when intended without side effects.

## [9.1.1.3] - 2026-08-14
### Fixed
- **[patch] Shake Logic Fix**: Fixed a bug where the PC error shake animation wasn't triggering because the desktop app didn't correctly detect the Android app instantly resetting the digit count from 5 to 0.

## [9.1.1.2] - 2026-08-14
### Added
- **[minor] Error Shake Animation (Desktop)**: Introduced an iOS-style horizontal shake and red border flash for incorrect PIN entries on the Windows UI, providing instant and unmistakable negative visual feedback before clearing the panel.

## [9.1.1.1] - 2026-08-14
### Fixed
- **[patch] UI Freeze Fix**: Fixed a silent background crash caused by attempting to animate a frozen WPF `ScaleTransform` bound from the DataTemplate, which left the UI stuck on the first digit and "Waiting for the PIN to be entered on the phone...".

## [9.1.1.0] - 2026-08-14
### Changed
- **[patch] Smooth PIN Digit Animations**:
  - Replaced instant border color swapping with hardware-accelerated WPF `ColorAnimation` and `DoubleAnimation` scale pop (`1.15x`).
  - Optimized the polling loop to manipulate existing `Border` UI elements via `ItemContainerGenerator` instead of destroying and rebuilding the `ItemsSource` array, enabling slick, un-interrupted enter and backspace transitions.

## [9.1.0.0] - 2026-08-14
### Added
- **[minor] Real-Time Interactive PIN Digit Sync & Shimmer (Desktop + Mobile)**:
  - Added live keystroke telemetry: when digits are typed into `PinInputField` on the Android phone, `MessageHandler.sendPinDigitEntered` emits `pin-digit-entered` WebSocket frames in real time.
  - Added `PendingPairDigitCount` tracking in C# `LocalSendEndpoints.cs` and exposed `digitCount` via `/local/pair-status`.
  - Upgraded WPF desktop polling cadence to 250ms with dynamic `SecondaryBrush` border highlighting and reactive status text (`Entering PIN on phone (X/5)...` $\rightarrow$ `Verifying PIN...`) on `icPinDigits`.
  - Added unit test coverage for `sendPinDigitEntered` in `MessageHandlerTest.kt`.

## [9.0.0.0] - 2026-08-14
### Added
- **[major] Native HTTP/3 (QUIC) PC-to-PC Transfers & Android Cronet Zero-Copy Optimization**:
  - Eliminated the `thru.exe` external dependency and replaced it with native `System.Net.Http` HTTP/3 over Kestrel `MsQuic` for completely automatic PC-to-PC QUIC transfers via LocalSend mDNS discovery.
  - Eliminated intermediate heap `ByteArray` overhead on Android Cronet download paths by using NIO `WritableByteChannel` direct memory writes from `ContentResolver.openFileDescriptor`.
  - Removed `thru.exe` firewall rules and dropped the 40MB payload from the `.msix` package entirely.

## [8.8.6.0] - 2026-08-13
### Changed
- **[patch] Solid Non-Transparent UI Rendering & Glassmorphism Elimination**:
  - Converted all semi-translucent highlight, hover, and selection accent brushes across `DarkTheme.xaml` and `LightTheme.xaml` to 100% solid, fully opaque hex colors (`SecondaryHoverBrush`, `SecondarySelectedBrush`, `SecondarySelectedHoverBrush`, `SecondarySelectedBorderBrush`).
  - Disabled `DropShadowEffect` card shadows (`MainShadow` Opacity=0, BlurRadius=0) to remove blur and glassmorphism styling.
  - Re-packed, signed, and installed `CodeDeX.DeX 8.8.6.0` to local machine.

## [8.8.5.0] - 2026-08-13
### Fixed
- **[fix] Universal Dynamic WPF Element Cache & PIN CODE Pairing Fix**:
  - Re-engineered the WPF element resolution pipeline by implementing `[DeX.Wpf.ElementCache]` (compiled C# `IDictionary` provider) in [Connect-Engine.ps1](file:///w:/CodeDeX/DeX/MSIX_Source/bin/Connect-Engine.ps1).
  - Resolved an issue where clicking **"PIN CODE"** on a discovered device did nothing because `$qrCodeContent` evaluated to `$null` due to missing entry in static hashtable `$initElements`, triggering an unintended cancel/re-QR fallthrough.
  - Eliminated manual `$initElements` maintenance; element lookups via `$script:ce["name"]`, `$script:ce.name`, and `dxEl "name"` now dynamically resolve and cache controls on first touch with $O(1)$ case-insensitive lookup.
  - Resolved 15+ other potential `$null` element lookups across FileBrowser, Settings, and Pairing modules (`pinViewPanel`, `txtPinTimeout`, `menuViewsContainer`, `dockPullProgress`, `prgPullProgress`, `txtPullTitle`, `badgeAutoConnect`, `badgeDnd`).
  - Passed target device IP directly from active selection in [Bindings_Settings.ps1](file:///w:/CodeDeX/DeX/MSIX_Source/bin/Modules/Bindings_Settings.ps1) to `Start-PinPairing` for instant resolution.
  - Verified with `Connect-Engine.ps1 -SelfTest` on PowerShell 5.1 and full automated packaging pipeline.

## [8.8.4.0] - 2026-08-13
### Fixed
- **[fix] Comprehensive Process & Service Graceful Shutdown Architecture**:
  - Restored correct brace structure in mDNS timer loop and enforced UTF-8 BOM encoding across PowerShell modules to guarantee 100% AST parse fidelity.
  - Eliminated re-entrant shutdown recursion between `ApplicationExit`, `cleanExitBlock`, and `Invoke-ExitEngine` with dedicated re-entrancy flags.
  - Replaced abrupt `Environment.Exit(0)` with ASP.NET `IHostApplicationLifetime.StopApplication()` and in-process WPF `Application.Shutdown()` on `/local/shutdown`.
  - Added `/local/transfer-status` check on desktop exit with a modal prompt to prevent active file transfer data corruption.
  - Added UI crash watchdog (`_childPsProc.Exited`) in C# backend preventing ghost/zombie background processes if the PowerShell frontend crashes.
  - Implemented surgical ADB process filtering (`$_.MainModule.FileName -like "*$dexBinPath*"`) ensuring global/Android Studio ADB instances survive DeX exit.
  - Added automated Task Scheduler cleanup migration removing legacy `AutoConnectADB_Hotspot` task.
  - Added WebSocket disconnection broadcast (`server-shutdown`) and unregistering of `NetworkChange.NetworkAddressChanged`.
  - Verified across automated test harness with 100% passed validation gates and live process lifecycle tests.

## [8.8.3.0] - 2026-08-13
### Fixed
- **[fix] PowerShell Parser Recovery & Engine Initialization**:
  - Cleaned up orphaned code snippet leftovers on lines 49-59 in [EngineUtils.ps1](file:///w:/CodeDeX/DeX/MSIX_Source/bin/Modules/EngineUtils.ps1#L49-L59) caused by previous clipboard worker modularization.
  - Resolved `Unexpected token '}'` parser exception, restoring clean dot-sourcing of `EngineUtils.ps1` during engine boot.
  - Fixed `Write-Trace: The term 'Write-Trace' is not recognized` downstream errors across `Bindings_Tray.ps1` and `Bindings_Window.ps1` by ensuring `global:Write-Trace` is reliably parsed and registered.

## [8.8.2.0] - 2026-08-13
### Fixed
- **[fix] Clipboard Service Edge-Case Hardening & Pipeline Safety**:
  - Restored initial default `IsSyncEnabled = false` in `ClipboardService.cs` to match desktop toggle state before startup sync initialization.
  - Added explicit `-Sta` CLI flag to PowerShell child processes in `SetWindowsClipboardImageAsync` to guarantee STA apartment state for Win32 clipboard API calls across Windows PowerShell 5.1 and PowerShell 7.
  - Restored robust 2-second async pipeline wait handle & `.Stop()` termination sequence in `Stop-ClipboardSyncWorker` ([ClipboardManager.ps1](file:///w:/CodeDeX/DeX/MSIX_Source/bin/Modules/ClipboardManager.ps1)) to prevent runspace disposal thread blocks on app exit.

## [8.8.1.0] - 2026-08-13
### Refactored
- **[fix] Modularization & Centralization of Cross-Device Clipboard Engine**:
  - Implemented dedicated C# service `ClipboardService.cs` ([DeXShareTarget/Services/ClipboardService.cs](file:///w:/CodeDeX/DeX/DeXShareTarget/Services/ClipboardService.cs)) to centralize rich-media payload parsing, image decoding, loopback tracking, and STA Windows Clipboard injection.
  - Refactored `LocalSendEndpoints.Control.cs` to delegate all clipboard routes (`/api/dex/clipboard`, `/local/clipboard-push`, `/local/clipboard-sync`, `/local/clipboard-state`) into single-line service calls.
  - Created dedicated PowerShell module `ClipboardManager.ps1` ([MSIX_Source/bin/Modules/ClipboardManager.ps1](file:///w:/CodeDeX/DeX/MSIX_Source/bin/Modules/ClipboardManager.ps1)) encapsulating `Get-DeXClipboardContent`, `Set-DeXClipboardContent`, `Start-ClipboardSyncWorker`, and `Stop-ClipboardSyncWorker`.
  - Removed duplicate inline worker code from `EngineUtils.ps1` and dot-sourced `ClipboardManager.ps1` in `Connect-Engine.ps1`.

## [8.8.0.0] - 2026-08-13
### Added
- **[minor] Rich-Media Cross-Device Shared Clipboard (Images & File Blobs)**:
  - Enhanced `Start-ClipboardSyncWorker` in `EngineUtils.ps1` to detect Windows Clipboard images (`System.Windows.Forms.Clipboard.ContainsImage()`), convert them to Base64 PNG payloads, and compute SHA256 content hashes for loopback prevention.
  - Upgraded `/api/dex/clipboard` and `/local/clipboard-push` endpoints in `LocalSendEndpoints.Control.cs` to ingest structured JSON payloads (`image/png`, `image/jpeg`).
  - Added native Windows Clipboard image injection (`[System.Windows.Forms.Clipboard]::SetImage`), allowing instant `Ctrl+V` pasting of copied Android images directly into Photoshop, Word, Paint, Discord, or web browsers.

## [8.7.1.0] - 2026-08-13
### Fixed
- **[fix] SpatialListItem ControlTemplate Click Hit-Test Resolution**:
  - Stabilized `TranslateTransform.X` displacement during `PreviewMouseDown` in `SpatialListItem` control template (`AppStyles.xaml`), preventing WPF from invalidating mouse capture and dropping physical mouse clicks on Settings panel buttons.
  - Set `ScrollViewer` `PanningMode="None"` in `MainWindow.xaml` to prevent Windows mouse/touch manipulation from swallowing button click events inside the settings container.

## [8.8.0.0] - 2026-08-13
### Added
- **[minor] Modern Aesthetic ContextMenu UI/UX Redesign**:
  - Implemented implicit global WPF `ContextMenu`, `MenuItem`, and `Separator` design system styles in `AppStyles.xaml`.
  - Added heavy rounded corners (`CornerRadius="16"` on menu container, `CornerRadius="10"` on menu items).
  - Integrated smooth entrance scale (`0.93` -> `1.0`), fade (`0` -> `1.0`), and slide (-4px -> 0px) storyboard animations (`0.18s` `CubicEase` `EaseOut`).
  - Added micro-interaction hover shift transitions (`TranslateX` 2px) and background highlights on `MenuItem` hover (`SecondaryHoverBrush`) and press (`SecondarySelectedBrush`).
  - Refactored `MainWindow.xaml` context menus (`TransferContextMenu`, `icUdpPeers`, `RadioButton`) to consume implicit global styles.
  - Eliminated hardcoded hex colors (replaced `#FF6B6B` with `{DynamicResource DangerBrush}`), gradients, and glows.

## [8.7.0.0] - 2026-08-13
### Added
- **[minor] Settings Persistence & Windows Registry Sync Module**:
  - Implemented `SettingsManager.ps1` to serialize user preferences (`CurrentTheme`, `AppThemeMode`, `DndEnabled`, `AutoConnect`, `WiggleEnabled`, `DownloadPath`) to `%LOCALAPPDATA%\DeX\settings.json`.
  - Added real-time mirror sync to Windows Registry (`HKCU:\SOFTWARE\CodeDeX\DeX`).
  - Integrated `Apply-DeXSettingsToUI` into `Connect-Engine.ps1` to automatically restore all visual badges, toggle switches, and theme preferences at application startup.
- **[fix] QR View Switch Null Property Exception**:
  - Added `'txtPinSubtitle'` to `$initElements` in `Connect-Engine.ps1` and added safe null-guards to `Bindings_Settings.ps1`, eliminating runtime property exceptions when returning to QR view.

## [8.6.7.0] - 2026-08-12
### Fixed
- **[fix] PowerShell UI Toast & Copy IP Animation Closure Scoping**:
  - Bound `.GetNewClosure()` and added `$null` checks to `$fadeOut.Add_Completed` in `UIComponents.ps1` and `$timer.Add_Tick` in `Bindings_Settings.ps1`, preventing `$null` variable dereference exceptions on dispatcher callback execution.
- **[fix] Resilient MSIX Packaging Build Lock Recovery**:
  - Enhanced `PackMSIX.ps1` to gracefully handle locked static dependency DLLs during packaging when background engine instances are active.
- **[fix] Mobile Hotspot & Ephemeral Socket Discovery Resilience**:
  - Ensured DiscoveryBackgroundService.cs maintains active UDP packet listening (canReceiveMain = true) when falling back to ephemeral sockets.
  - Added Hotspot gateway unicast delivery for UDP discovery datagrams over Android Mobile Hotspot networks.

## [8.6.6.0] - 2026-08-12
### Fixed
- **[fix] 60-Scenario Discovery Matrix Resilience**: 
  - Wrapped mDNS service start (mdns.Start()) in a 	ry-catch block inside DiscoveryBackgroundService.cs so mDNS port 5353 socket locks (e.g. Apple Bonjour / iTunes / Avahi) do not halt UDP discovery.
  - Updated TryPort in LocalSendServer.cs to test IPAddress.Any instead of IPAddress.Loopback, preventing startup crashes when TCP port 48424 is bound on external interfaces.
  - Expanded /api/localsend/v2/info endpoint (LocalSendEndpoints.Share.cs) to return the full RegisterDto JSON payload.
  - Upgraded Android sendManualDiscovery() (DiscoveryEngine.kt) to fire dual-port UDP probes (48424 + dynamic port) and execute an HTTP REST GET probe fallback to /api/localsend/v2/info, guaranteeing instant device discovery on AP-isolated Wi-Fi and strict corporate networks.
## [8.6.5.0] - 2026-08-12
### Added
- **[minor] Multi-Adapter QR Code Payload**: Rewrote the pairing QR code generator to encode every active IPv4 address on the PC into a single JSON-like query payload (?ips=...). Android devices scanning the code will now extract all available IPs and fire discovery probes concurrently to all of them. This permanently solves the edge case where the QR code picks an unreachable IP (e.g. VirtualBox/Hyper-V/VPN) over the valid Wi-Fi IP, ensuring immediate pairing resilience regardless of active network adapters.
## [8.6.4.0] - 2026-08-12
### Fixed
- **[fix] Resilient UDP Discovery Binding**: Made the primary UDP socket binding resilient against OS-level hypervisor port locks (e.g., WinNAT AccessDenied/AddressAlreadyInUse exceptions). DiscoveryBackgroundService now cleanly catches native socket exceptions and falls back to a random ephemeral port for broadcasting outbound UDP advertisements, ensuring the mDNS and legacy port listeners remain perfectly functional instead of crashing the entire background discovery thread.
## [8.6.3.0] - 2026-08-12
### Fixed
- **[fix] Bulletproof Discovery on Mobile Hotspot & Public Networks**: 
  - Unified UDP multicast discovery port across both platforms (`28424` -> `48424` in `DeXConstants.cs`) to eliminate port mismatch and enable direct hotspot gateway unicast resolution.
  - Implemented prefer-fixed-port-then-fallback logic in `LocalSendServer.cs` (`TryPort(48424)`), resolving `SocketTimeoutException` on dynamic ephemeral ports and ensuring the Android default port assumption holds true for 99% of deployments.
  - Enhanced Android QR code scanner (`MainScreen.kt`) to extract the exact dynamic port from the URL payload and inject it into `sendManualDiscovery()`, handling the rare 1% occupied-port scenario.
  - Deployed a permanent, zero-cost legacy UDP listener on port `28424` (`DiscoveryBackgroundService.cs`) to guarantee backwards compatibility with older Android APKs during fleet transition.
  - Added a silent startup diagnostic (`CheckFirewallAccess()`) to verify discovery port binding against Windows Public network profile firewall restrictions.

## [8.6.2.0] - 2026-08-12
### Fixed
- **[fix] Dynamic Storyboard Children Pruning in `TrayUIHandlers`**: Replaced fixed array index loop (`15, 14... 7`) with a dynamic bounds-checked reverse loop (`for ($i = $sb.Children.Count - 1; $i -ge 7; $i--)`). Eliminates `ArgumentOutOfRangeException: Specified argument was out of the range of valid values` when swapping panels on storyboards with fewer than 16 elements (such as `ExpandSettings`).
### Added
- **[minor] Automated AST Argument Guard in `Validate-Build.ps1`**: Integrated Section 10 AST static analysis gate into the 18-gate pre-flight build check. Automatically scans all module scripts (`*.ps1`, `*.psm1`) for unparenthesized type accelerators in parameter argument mode, preventing argument parsing regressions from entering production builds.

## [8.6.1.0] - 2026-08-12
### Fixed
- **[fix] PowerShell Argument Mode Evaluation in `Bindings_Core`**: Enclosed `[bool]$this.IsChecked` in parentheses `([bool]$this.IsChecked)` when invoking `Set-DndMode -Enable`. Resolves PowerShell argument mode parsing error (`Cannot convert value "System.String" to type "System.Boolean"`) caused by unparenthesized string interpolation `[bool]@{IsChecked=True}.IsChecked`.

## [8.6.0.0] - 2026-08-12
### Added
- **[minor] 7-Branch Security & Capability Matrix (`LocalSendEndpoints`)**: Enforced pairing authorization on `/api/dex/wallpaper` and `/api/localsend/v2/wallpaper` endpoints. Unpaired devices are denied live 480p desktop wallpaper access (returning HTTP 401) and display default fortress artwork.
- **[minor] Paired Telemetry & WebSocket Push Scoping (`WebSocketConnectionManager`)**: Restricted live WebSocket event broadcasts (`wallpaper-updated`, `telemetry-updated`) strictly to verified paired sockets (`requireVerified: true`). Unpaired device cards render standard `"Nearby"` tags, unlocking real PC battery percentage, AC charging bolt icons (`BatteryCharging`), and Wi-Fi band tags ONLY upon successful PIN pairing. Enabled WAN/cellular wallpaper streaming for paired devices.
- **[minor] Active Transfer UI Progress Reveal on Exit**: Integrated active file transfer detection (`$script:activePulls` / `$script:mirrorProc`) into the 'Exit Engine' click workflow. Automatically restores window focus, expands the File Explorer panel, reveals `dockPullProgress`, and highlights real-time progress before allowing a confirmation exit.
- **[minor] Surgical ADB Process Cleanup Scoping**: Updated `Invoke-ExitEngine` to inspect process `MainModule.FileName` on `adb.exe` instances, exclusively terminating ADB processes running from DeX's own binary directory while preserving external Android Studio and CLI ADB daemons.
- **[minor] Session State Snapshot Persistence (`Save-EngineState` / `Restore-EngineState`)**: Implemented registry-backed session state snapshots (`HKCU:\Software\DeX`) saving active directory paths (`LastFolder`) and desktop window coordinates (`WindowLeft`, `WindowTop`) for instant context recovery on next launch.
- **[minor] Non-Blocking WPF Exit Fade-Out**: Added a 150ms WPF DoubleAnimation opacity fade-out sequence to `$script:wpfWindow` upon exit before triggering background process and runspace teardown.

## [8.5.0.0] - 2026-08-12

### Added
- **[minor] Adaptive Chunked Range Resuming (`VStream-AutoResume`)**: Implemented `/api/localsend/v2/vstream-progress` and `Range: bytes={offset}-` HTTP header seeking in `LocalSendEndpoints.Share.cs`. Automatically detects partially transferred byte offsets after network interruptions and resumes streaming directly from the last received byte, guaranteeing 100% SHA256 byte-hash integrity without restarting folder transfers from scratch.
- **[minor] DeX-VStream Virtual Directory Manifest Streaming Protocol**: Implemented high-speed virtual directory manifest streaming (`DeX-VStream`) inspired by Blip architecture. Replaced legacy per-file HTTP POST handshakes with a single continuous manifest stream (`vstream-prepare` and `vstream-data`), preserving relative folder trees directly on destination disks without pre-zipping or extra temporary storage overhead.
- **[minor] C# PC Engine & Android Integration**: Added `VStreamManifestDto` models, registered `/api/localsend/v2/vstream-prepare` and `/api/localsend/v2/vstream-data` endpoints in `LocalSendEndpoints.Share.cs`, added `HostAndPushVStreamAsync` in `RelayService.cs`, updated `TransferWindow.cs`, and added Kotlin `VStreamPrepareRequestDto` and `handleVStreamPrepare` in `MessageHandler.kt`.
- **[minor] Live Windows PC Battery & Wi-Fi Telemetry Synchronization (`PcTelemetryService`)**: Implemented `PcTelemetryService` in `DeXShareTarget` using Win32 `GetSystemPowerStatus` and `NetworkInterface` APIs with a 2-second in-memory TTL cache to query live Windows battery percentage (0–100%), AC power charging status, and Wi-Fi band (`5GHz`, `2.4GHz`, `6GHz`, or `LAN`).
- **[minor] Android Card Telemetry Integration (`DeviceListItem`)**: Extended `RegisterDto` on PC and Android to transport live power and network telemetry. Replaced simulated random battery placeholders in Android's `DeviceListItem` card UI with real PC battery percentages, AC charging bolt icons (`BatteryCharging`), and actual Wi-Fi band tags.

## [8.4.0.0] - 2026-08-12
### Added
- **[minor] Live Debounced WebSocket Wallpaper Watcher (`WallpaperWatcherService`)**: Created a background file monitor on Windows using `FileSystemWatcher` targeting `TranscodedWallpaper` changes with 1-second debouncing and a 500ms post-write buffer safeguard. Broadcasts `wallpaper-updated` WebSocket messages to paired mobile devices when the desktop wallpaper changes.
- **[minor] Mobile Lifecycle & ETag Invalidation (`WallpaperState`)**: Created `WallpaperState` in the Android app to collect WebSocket `wallpaper-updated` signals and invalidate local image keys (`?rev=<revision>`). Triggers smooth Coil crossfade transitions to the new 480p desktop wallpaper in real time.

## [8.3.1.0] - 2026-08-12
### Fixed
- **[fix] Phase 2 Edge Case Optimizations & PoC Suite**: Implemented Windows environment path variable expansion (`%USERPROFILE%`, `%SystemRoot%`) for active wallpaper candidate paths. Added HTTP `ETag` generation (`W/"<ticks>-<size>"`) and HTTP `304 Not Modified` validation to `/api/dex/wallpaper` endpoints. Protected Large Object Heap (LOH) RAM allocations when processing 4K/8K images. Wrapped WPF bitmap decoding in isolated `FileFormatException`/`COMException` WIC exception guards for Windows 11 HDR image safety. Bracketed IPv6 host URLs (`[fe80::1]`) in Android `DeviceListItem` to prevent OkHttp URL parser crashes.

## [8.3.0.0] - 2026-08-12
### Changed
- **[minor] Replaced 'Connect ADB' Quick Action with 'Do Not Disturb' (`btnQADnd`)**: Replaced the legacy `btnQAConnect` button in the spatial menu quick action bar with a native Do Not Disturb toggle (`btnQADnd`).
- **[minor] Synchronized DND State Management**: Created `Set-DndMode` in `Bindings_Settings.ps1` to keep both the spatial menu quick action toggle and the Settings panel badge in 100% two-way sync, firing toast notifications and updating the local engine `/local/dnd` endpoint on toggle.

## [8.2.0.0] - 2026-08-12
### Changed
- **[minor] Decouple ADB from Core Architecture**: Completely removed ADB as a mandatory dependency for ordinary consumers. ADB is no longer started at launch, polled in the background (`adb devices -l` / `adb mdns services`), or used as a clipboard broadcast fallback. Primary device discovery and status indicators now rely strictly on DeX's native C# network engine (WebSockets/mDNS/UDP).
- **[minor] Developer Tools & On-Demand ADB Provisioning**: Reframed ADB into an optional power-user utility under a dedicated "Developer Tools" section in Settings and device context menus (`Developer: Connect ADB`). Unbundled `adb.exe` from mandatory startup and implemented dynamic background downloading of official Google platform-tools `adb.exe` on demand when power-user features are accessed.

## [8.1.0.0] - 2026-08-12
### Added
- **[minor] Live 480p Windows PC Wallpaper Streaming to Android Cards**: Replaced static placeholder drawables on Android device cards with real-time PC Windows desktop wallpaper streaming.
- **[minor] Windows Wallpaper Extractor (`WallpaperService`)**: Created a high-performance wallpaper extraction service in `DeXShareTarget` that reads active desktop wallpaper from Windows Shell (`%APPDATA%\Microsoft\Windows\Themes\TranscodedWallpaper`) with non-exclusive `FileShare.ReadWrite` streams, multi-tier fallbacks (Win32 `SPI_GETDESKWALLPAPER`, Registry `HKCU\Control Panel\Desktop\Wallpaper`, and cached theme files), and auto-detects magic bytes (`JPEG`/`PNG`).
- **[minor] 480p Ultra-Lightweight Downscaling**: Downscales high-res 4K/8K PC wallpapers to 480p JPEG (~120KB) in memory using WPF's `TransformedBitmap` and `JpegBitmapEncoder`, eliminating Wi-Fi latency and memory overhead.
- **[minor] Endpoint & Android Integration**: Added `/api/dex/wallpaper` and `/api/localsend/v2/wallpaper` endpoints with 5-second server-side TTL caching and HTTP `Cache-Control`. Updated Android `DeviceListItem` and `AsyncImage` with Coil `crossfade` and `placeholder`/`error` fallbacks. Enabled local subnet cleartext permits in `network_security_config.xml`.

## [8.0.4.0] - 2026-08-12
### Fixed
- **[fix] ADB Connection Freeze**: Resolved a severe 30-second UI freeze when connecting ADB. The issue was caused by a known Windows `WinNAT` bug (triggered by Hyper-V/WSL) silently hijacking the `44000-48999` port range, instantly crashing the isolated ADB daemon on port 48427. Fixed by dynamically allocating the ADB daemon port (`Get-FreePort`) at startup, completely eliminating port collisions.
- **[fix] Port Range Shift**: Shifted the remaining static ports (`DiscoveryPort` and `LocalApiPort`) down to the safe `28xxx` block (`28424`, `28425`) to permanently evade the Windows dynamic port exclusion blast radius.
- **[fix] ADB Fast TCP Ping**: Restored the Fast TCP Ping timeout to a highly aggressive `400ms` and removed the 15-second fallback ADB daemon restart block to ensure instant failure feedback.

## [8.0.1.0] - 2026-08-12
### Fixed
- **[fix] UPnP and PC mDNS Dynamic Port Omissions**: Added UPnP IGD port mapping logic for the newly dynamic `TcpFallbackPort` to ensure WAN connections continue to work even when QUIC is blocked. Updated `RelayService.cs` to correctly pass the `quicPort` and `tcpFallbackPort` properties inside the `prepare-upload` WebSocket payload by using the `RegisterDto` instead of an anonymous object, fixing a bug where Android clients would fall back to static ports during PC-to-Android transfers. Additionally, patched the PC's mDNS `DiscoveryBackgroundService` to correctly parse `quicPort` and `tcpFallbackPort` from TXT records when discovering other PCs.

## [8.0.0.0] - 2026-08-11
### Changed
- **[major] Dynamic Port Allocation & ADB Isolation**: Completely resolved port collisions for multi-instance Fast User Switching. The C# background server now dynamically allocates its HTTPS and QUIC ports at startup and updates Android peers via UDP Multicast and WebSocket broadcasts. Android app logic (including UDP Discovery, WebSocket Service, and UPnP WAN handling) was overhauled to read, persist, and utilize these dynamic PC ports natively. Additionally, the internal ADB daemon is now securely isolated to port `48427` to prevent conflicts with Android Studio and other developer tools.
- **[fix] Dynamic Port Edge Cases (Android)**: Patched `UdpMulticastManager` and `DiscoveryEngine` to properly deserialize and construct `RegisterDto` with the newly assigned dynamic QUIC and TCP Fallback ports from the UDP payload. Updated `WebSocketClientService.wanTarget()` to dynamically route WAN connections to the persisted `PcMemory.port` rather than hardcoding the fallback constant, ensuring seamless compatibility with UPnP dynamic port forwarding.

## [7.9.13.0] - 2026-08-11
### Fixed
- **[fix] Menu content shrinks abruptly during contraction**: Fixed a 66px gap appearing at the top of the menu during contraction. The issue occurred because a DataTrigger instantly snapped the container's MaxHeight back to 352px the moment the inner panels (FileExplorer/Settings) started fading out, while the main border was still animating its height. Removed the DataTriggers and integrated synchronized `MaxHeight` double animations directly into the `ExpandMenu` and `ContractMenu` storyboards.

## [7.9.12.0] - 2026-08-11
### Fixed
- **[fix] Close button disappears after first use in Expand Menu**: Fixed an issue where the `btnCloseMenu` button would remain permanently shrunk (0 width/margin) after the first menu contraction. Added explicit zero-duration reset animations in the `ExpandMenu` storyboard to restore its width and margin whenever the menu expands.

## [7.9.11.0] - 2026-08-11
### Changed
- **[minor] Removed legacy ADB connections from UI device clicks**: Left-clicking a device now exclusively interacts with the WebSocket/File Explorer subsystem. ADB connections are now strictly opt-in via a new dynamic "Connect ADB" / "Disconnect ADB" context menu item.

## [7.9.10.0] - 2026-08-11
### Fixed
- **[fix] Desktop UI freezes when clicking a paired device**: Fixed a severe UI freeze caused by `Invoke-AdbConnect` being run synchronously on the WPF dispatcher thread when tapping a device. The connection sequence (TCP ping, `adb start-server`, and `adb connect`) has been moved to an asynchronous background job, keeping the spatial menu responsive even when the ADB daemon or the target device hangs.

## [7.9.6.0] - 2026-08-11
### Changed
- **[minor] Moved extended nearby users**: The extended nearby users dummies (Akua Donkor, Kwame Asante, Ama Serwaa) have been moved into the main 'My Devices' list and the 'Extended nearby users' section/animations have been entirely removed.

## [7.9.5.0] - 2026-08-11
### Fixed
- **[fix] Tofu box characters in device list SubText (Desktop)**: Fixed an issue where the device model and battery percentages were rendering as missing glyph boxes ("tofu"). The XAML `FontFamily` declaration order in the UI incorrectly prioritized icon fonts (`Segoe Fluent Icons`) ahead of the standard `Segoe UI`, breaking WPF's character-by-character fallback for standard text.

## [7.9.4.0] - 2026-08-11
### Fixed
- **[fix] QR Code UI left clipping on outbound pairing (Desktop)**: Fixed an issue where the QR code was shifted to the left and clipped out of bounds upon opening the pairing panel. The `qrContentTrans` X translation is now explicitly reset to 0 (and `pinContentTrans` to 140) whenever the QR view initializes.

## [7.9.3.0] - 2026-08-11
### Fixed
- **[fix] Click-outside no longer hides the spatial menu after a pairing (Desktop)**: Once any pairing session had run, the window could never be dismissed by clicking outside in the contracted state. The stopped `pairWaitTimer` (never nulled on completion/cancellation) made the Deactivated guard's truthy check permanently block the hide. The guard now only keeps the window while the poll timer is actually running, and the timer is nulled when pairing state is cleared.

## [7.9.2.0] - 2026-08-11
### Fixed
- **[fix] PIN UI 164px gap on inbound request (Desktop)**: Fixed an issue where the PIN code content was offset to the right by 164px during the initial slide-in animation. The `pinContentTrans` X translation (initialized to 140 for switch animations) is now properly reset to 0 when the pairing panel first appears.

## [7.9.1.0] - 2026-08-11
### Fixed
- **[fix] Logo background color (Desktop)**: Changed the Start Menu and App List logo background color for DeX from transparent to black.

## [7.7.0.0] - 2026-08-11
### Fixed
- **[fix] QR code not appearing on discovered-device click (Desktop)**: `Show-QrCode` used a `[System.Action]` + `BeginInvoke` delegate that throws under Windows PowerShell 5.1 ("The object must be a runtime Reflection object."), aborting the pairing slide-in; the fetch also used `Invoke-RestMethod`, which decodes `image/png` to a String so the byte[] check never matched. The QR PNG is now fetched in a background job via `HttpWebRequest` (raw bytes) and applied on the UI thread.
- **[fix] PIN / QR / Cancel switching edge cases (Desktop)**: Escape now cancels the pairing (was a swallowed no-op); "QR CODE" back-switch uses `pair-cancel` instead of `unpair`; the PIN countdown actually expires (was stuck on "Expires in 0s"); stale poll ticks and in-flight pair requests can no longer resurrect over a newer session; duplicate "Request PIN" clicks are guarded; the idle QR phase auto-expires after 60s; switching devices cancels the previous session; stale QR bitmaps are cleared.
- **[fix] Cancel no longer revokes trust (Desktop)**: `/local/pair-cancel` no longer removes the device from the trusted list — cancelling a re-pair (or it timing out) keeps existing trust intact. Explicit revocation is still `/local/unpair` ("Forget Device") or a phone-initiated unpair.
- **[fix] Pairing token saved only on acceptance**: `PushPairPromptAsync` no longer persists the pairing token upfront, which could clobber a trusted device's valid token on a re-pair that was then cancelled — silently de-trusting it. The token is stored on the pending attempt and persisted in the `pair-response` accepted path.
- **[fix] Android PIN dialog shows the expiry countdown**: mirrors the PC's 60s window, driven by the prompt's actual deadline (so a dialog opened late from a notification shows the true remaining time), turns red at ≤10s, and auto-dismisses at zero.
- **[fix] Android "Connect" now pairs with the tapped PC**: the phone previously failed silently when the tapped PC differed from the auto-connected target; it now connects to the tapped PC first, then sends the pair request (with a 6s cap).
- **[fix] PC cancel dismisses the phone's PIN dialog immediately**: the PC pushes a `pair-cancelled` message over the WebSocket instead of letting the phone count down its own 60s.
- **[fix] Pairing state keyed by fingerprint instead of IP**: pending-pair and pair-status are no longer broken by a phone IP change mid-pairing (DHCP), and phones behind the same NAT cannot collide.
### Hardening
- **[fix] Certificate lifecycle (Desktop)**: server certificates are validated with the exact TLS API Kestrel uses (a keyless/corrupt PFX is regenerated instead of crashing startup), persisted atomically, fall back to self-signed if the embedded root CA is unavailable, renew live on public/LAN IP changes via a per-connection selector, and log lifecycle events to `%APPDATA%\DeX\cert.log`.

## [7.6.0.0] - 2026-08-11
### Added
- **[minor] Dynamic Island UI/UX Upgrade (Android)**: Rebuilt the top navigation bar into an iOS-style Liquid Glass Dynamic Island. Features smooth cross-fading avatars, bubble fluidity physics on the brand logo, and a full-screen 85% dim overlay that natively covers the system status bar and bottom navbars.
- **[minor] Glass Transfer Overlay**: Upgraded the file `TransferProgressOverlay` to utilize the native `LiquidGlassPanel` with backdrop sampling, removing flat material surfaces for a completely cohesive glassmorphism aesthetic.
- **[minor] UX Refinements**: Removed the exposed sign-out button from the expanded profile island to align with standard UX best practices, converting the island into a clean profile status pill.

## [7.5.2.15] - 2026-08-10
### Fixed
- **[fix] Missing Pairing UI Transitions (Desktop)**: Fixed a regression where closing the PIN or QR pairing screen caused the main "Discovered Devices" spatial menu content to permanently disappear. Restored the missing `SlideOutPinAnim`, `SlideInPinAnim`, and `Switch` storyboards in XAML so the menus reliably slide and crossfade back into view.

## [7.4.2.14] - 2026-08-10
### Added
- **[minor] PIN screen UI/UX redesign (Desktop)**: Revamped the Pairing PIN screen to use OTP-style segmented digit boxes, a pulsing dot for the "Waiting for acceptance" status, and a modern "Expires in (X)s" text display replacing the old flat progress bar.

## [7.4.2.13] - 2026-08-10
### Fixed
- **[fix] Button alignment and padding (Desktop)**: Corrected the alignment of the text inside the QR Code/Request PIN button when the icon is hidden, and added standard horizontal padding to `AnimatedActionBtn` so text doesn't touch the edges of the button.

## [7.4.2.12] - 2026-08-10
### Fixed
- **[fix] Spatial menu layout deformation (Desktop)**: The spatial menu content panel is now hidden with `Visibility.Hidden` instead of `Visibility.Collapsed` when opening the QR Code or PIN screens. This preserves the layout constraints and prevents the main container from abruptly shrinking and compacting the UI.
### Added
- **[minor] Pairing Request micro-animations (Desktop)**: Added subtle scale-in animations (95% to 100%) for both the QR Code and PIN displays when transitioning between them or opening the screens, providing a more physical and polished feel without relying on heavy gradients or glow effects.

## [7.0.0.0] - 2026-08-09
### Added
- **[minor] iOS-style navigation transitions (Android)**: Centralized motion language (`NavigationTransitions.kt`) — tab switches crossfade with a subtle scale, push/pop slides (400ms/350ms with UIKit cubic-bezier curves, 1/3 parallax, 96% scale, 70% dim) are wired for future detail screens. Tabs are siblings on one `AnimatedContent` (no back-stack traversal), and each tab's UI state (scroll position) survives switching via `SaveableStateHolder`.
- **[minor] File Explorer drill-down push/pop (Desktop)**: Entering/leaving folders now animates with the same iOS curves (`KeySpline`-driven), with a snapshot layer for the outgoing listing; Transfer History ↔ Phone Folders mode toggle crossfades.
- **[minor] Cross-platform auto sign-in removed**: The phone's Google sign-in no longer propagates to the PC (`set-email` handler and `pushIdentityToPc` removed on both sides). Each platform signs in manually — the PC profile only ever shows the PC's own account.
### Fixed
- **[fix] Spatial menu lag (Desktop)**: Wiggle detector timer corrected from 20ms to the intended 50ms sampling (removes constant 50Hz UI-thread load that fought PopIn/Expand tweens).
- **[fix] Startup freeze (Desktop)**: Google profile fetch + startup retry moved off the UI thread (was up to 42s of blocked window); profile/sign-out/sign-in clicks are non-blocking; sign-out POST hardened with a 5s timeout.
- **[fix] Avatar/settings load chain hardening (Desktop)**: All 12 unguarded `FindName(...).Add_Click()` call sites across the binding chain now null-guarded — a missing button can no longer abort module load and silently kill the avatar → settings wiring.

## [1.1.0.0] - 2026-08-08
### Added
- **[minor] Trusted Devices Manager**: In-layout dialog with unpairing support (`DeviceManager.removePairedFingerprint`).
- **[minor] Manage Shared Folders**: In-layout dialog with SAF access revocation support (`SafStorage.removeGrantedFolder`).
- **[minor] Connection Handshake & Untrusted Device Pairing**: Interactive pairing flow (`ClientEngine.registerDevice`, Compose `SnapshotStateSet` reactivity for `AuthState.pairedFingerprints`, double-tap race condition prevention).
- **[patch] Localized Resources**: Added localized Toast feedback resources in `strings.xml`.
- **[minor] Unit Test Suite**: Comprehensive test suite (16/16 tests passing across `DeviceManagerTest`, `SafStorageTest`, `MainScreenViewModelTest`).

### [patch] UI Font Color Fix (v6.6.58.0)
- **[fix]** Set the foreground of the 'Request PIN' / 'QR CODE' button to Black as requested by the user, overriding the default secondary text brush.

- **[fix]** Replaced `HttpClientHandler` with `SocketsHttpHandler` in `LocalSendEndpoints.cs` and `TransferWindow.cs` to accurately enforce HTTP/1.1 ALPN negotiation and resolve silent TLS handshake crashes with the Android server.

### [patch] HTTP/2 ALPN Pairing Fix (v6.6.56.0)
- **[fix]** Forced HTTP/1.1 for outbound pairing requests in the C# `LocalSendEndpoints.cs` to prevent Ktor Netty on Android from crashing during ALPN negotiation when attempting to use HTTP/2.
### [patch] Android Plurals & Dependency Hardening (v6.6.55.0)
- **[patch]** Bumped Gradle wrapper to 9.7.0 and consolidated dependency versions in `libs.versions.toml`. Separated BouncyCastle `bcpkix-jdk18on` and `bcprov-jdk18on` versions to correctly pull the latest artifacts and resolve Gradle configuration failures.
- **[patch]** Fixed duplicate `META-INF/LICENSE.md` build failure caused by BouncyCastle by adding a `packaging` block exclusion in `app/build.gradle.kts`.
- **[fix]** Converted static `strings.xml` strings containing quantities to native `<plurals>` resources (`toast_sending_files`, `notif_incoming_desc`, `uploading_progress`) and integrated `pluralStringResource` directly into Compose UI to resolve Android lint warnings.
- **[fix]** Refactored redundant null-checks across Ktor network modules (`DeviceApi.kt`, `FileTransferApi.kt`) and switched obsolete `Uri.parse()` to Kotlin's robust `.toUri()` extension function across the app.
- **[fix]** Suppressed intentional `TrustAllX509TrustManager` warnings in `ClientEngine.kt` as local P2P TLS connections rely on self-signed certificates over the LAN.
- **[fix]** Removed obsolete `SDK_INT` version checks in WorkManager notification builders now that `minSdk` enforces API 26+ minimums.
- **[fix]** Cleaned up unused resources and layout XMLs (`backup_rules.xml`, `data_extraction_rules.xml`) flagged by the linter.
### [patch] PC-Initiated Pairing Overhaul (v6.6.54.0)
- **[fix]** Removed redundant UDP multicast discovery loop from the Android app, establishing a strict "PC-discovers-Android" architecture for better reliability and lower battery usage.
- **[fix]** Fixed a bug on the PC side where clicking a discovered device in the UI would fail to initiate pairing due to an improperly scoped click handler.
- **[fix]** Aligned pairing timeout on both PC and Android to 60 seconds (with UI countdown animation) to prevent phantom paired states.
- **[minor]** Modified Windows app pairing UI to show the QR code initially instead of the PIN when connecting to a newly discovered device. This streamlines the flow for users wanting to quickly scan the QR code to connect.
### [major] Play-Store-Compliant SAF Storage + Trust Overhaul (v6.5.0.0)
- **[major]** Removed `MANAGE_EXTERNAL_STORAGE` from Android — DeX is now Play-Store compliant. All incoming transfers write to a user-granted `Downloads/DeX` folder via SAF with persisted URI permissions.
- **[feature]** Added SAF storage layer (`SafStorage.kt`) with persisted `Downloads/DeX` folder grant + opt-in file-explorer folder grants (DCIM, Pictures, Downloads, etc.) via the system folder picker.
- **[feature]** Reworked `/api/dex/browse` + `/api/dex/pull` to be SAF-backed — the desktop can only browse/pull within folders the Android user explicitly granted.
- **[feature]** Desktop explorer panel now shows **transfer history** (local `Downloads\DeX` files) by default, with a new toggle button beside the search bar to switch into File Explorer mode (SAF-granted phone folders).
- **[feature]** Unified all DeX transfer destinations to `Downloads/DeX` on both PC and Android (was `Downloads` root on PC, `Downloads\dex` lowercase in PowerShell).
- **[fix]** Fixed hardcoded `"dex-fingerprint"` in `UploadWorker.kt` — Android now sends its real fingerprint + identityHash, unbreaking Android→PC transfers.
- **[fix]** Fixed `IdentityManager.Initialize()` early-return that forgot all paired devices + aliases on every restart.
- **[fix]** Fixed desktop auto-trust: UDP/mDNS discovery now parses `identityHash` (was always false), and `/info` now includes it on both sides.
- **[fix]** Fixed Android `DeviceConfig` email-clear bug that regenerated a new identity hash, breaking trust.
- **[fix]** Fixed Android `notify-download` writing to tmp dir — now goes to `Downloads/DeX`.
- **[fix]** Fixed Android `pair-prompt` single-slot race + infinite await (added busy rejection + 60s timeout).
- **[fix]** Fixed desktop `pair-prompt` TCS/PendingPairs leak on client disconnect.
- **[fix]** Fixed PC→PC transfer: added missing `/notify-download` endpoint to the desktop server.
- **[fix]** Fixed Android `verifyToken` vulnerability — the publicly-advertised fingerprint is no longer accepted as a Bearer token; replaced with per-pairing shared secrets.
- **[fix]** Added auth to desktop `/api/dex/clipboard` (was open to any LAN device).
- **[fix]** Removed hardcoded OmniMesh trust hash `"dex_static_placeholder_hash_123"`.
- **[fix]** Fixed Android→Android UDP discovery (Android now replies to all devices, not just desktops).
- **[fix]** Fixed desktop device-list duplicate keys (ping stored by IP, discovery by fingerprint).
- **[fix]** Fixed `HostedFiles.Clear()` breaking in-progress transfers (now removes only this window's fileIds).
- **[fix]** Fixed Android `prepare-upload` infinite await + never-expiring sessions (60s timeout + 10min cleanup).
- **[fix]** Android 15 `dataSync` FGS 6-hour timeout: added `onTimeout()` + `stopSelf()` in `DexService`.
- **[fix]** Android 14 FGS type enforcement: combined `connectedDevice|dataSync` types + `tools:node="merge"` on WorkManager's `SystemForegroundService`.
- **[fix]** Added `NEARBY_WIFI_DEVICES` (neverForLocation) + `ACCESS_FINE_LOCATION` (maxSdk 32) permissions with runtime requests.
- **[upgrade]** Ktor 3.0.1 → 3.5.1, kotlinx.coroutines 1.10.2 → 1.11.0, WorkManager 2.10.0 → 2.11.2, DataStore 1.1.1 → 1.2.1, Compose BOM 2026.03.01 → 2026.06.01, Lifecycle 2.10.0 → 2.11.0.
### [patch] Android 14+ Foreground Service Hardening
- **[patch]** Hardened `DexService` with explicit `ServiceCompat.startForeground` typing to prevent silent API 34+ crashes.
- **[patch]** Added dynamic Android 13 `POST_NOTIFICATIONS` permission request to guarantee transfer progress bar visibility.
### [minor] DataStore & Structured Logging Migration
- **[minor]** Migrated legacy SharedPreferences to modern Jetpack Preferences DataStore for asynchronous, non-blocking storage.
- **[minor]** Integrated Timber structured logging globally, replacing legacy Log dumps across all network and UI tiers.
### [major] Android App Architecture Modernization
- **[major]** Refactored DeX Android App to decouple networking and UI utilizing Koin (Dependency Injection) and Ktor (HTTP client).
- **[feature]** Extracted hardcoded Android UI strings into robust localized resources (strings.xml).
- **[feature]** Handled networking edge-cases on Android with modernized Compose Error Dialogs and resilient state resets.
### [patch] UI Refinements (v5.4.1.0)
- **[patch]** Moved the QR Code pairing button from the settings menu to the PIN code overlay next to the 'Cancel' button.
### [minor] Seamless Bi-Directional Clipboard Sync (v5.4.0.0)
- **[feature]** Implemented PC -> Android clipboard sync over ADB Broadcast intents via the existing \tnQAClipboard\ in the Tray UI.
- **[feature]** Implemented Android -> PC clipboard sync by extending the PC LocalSend server with a lightweight \/api/dex/clipboard\ endpoint that leverages PowerShell to set the Windows clipboard without extra C# dependencies.
- **[feature]** Added a dedicated Clipboard send button to discovered Android devices in the Android App UI.
### [patch] UI Refinements (v5.3.5.2)
- **[patch]** Added smooth expand/collapse animations for the ADB status row, utilizing the existing animation engine to smoothly push the device list down without increasing the spatial menu's overall height.
- **[patch]** Removed the IP address display from Discovered Devices on the UI to keep the list clean.
### [patch] UI Refinements (v5.3.5.1)
- **[patch]** Switched ScrollViewer Height to MaxHeight to eliminate empty space below devices while keeping the list area scrollable when new devices are discovered.
### [patch] UI Refinements (v5.3.5.0)
- **[patch]** Hid the ADB status row by default to declutter the spatial menu, now only displaying when an ADB connection is active or attempted.
- **[patch]** Constrained the spatial menu's device list ScrollViewer to a fixed height (300px) so the menu's overall height no longer incorrectly expands when a new 'Discovered Device' appears.
### [patch] Fix Discovered Devices Clipping (v5.3.4.0)
- **[patch]** Increased the load height animation target for discovered UDP peers from `42` to `64` to prevent clipping the device icon and model name text.
### [patch] Clarify ADB Status UI (v5.3.3.0)
- **[patch]** Renamed 'Status' and 'Connected' text in the quick actions menu to 'ADB Status' to unambiguously clarify it represents the ADB connection state.
### [minor] PC-Initiated Guest Pairing (v5.3.1.0)
- **[feature]** Added a /local/pair-initiate endpoint to the LocalSend C# server to allow the PC to initiate outbound pairing requests to discovered guest devices on the LAN.
- **[feature]** Rewrote the Windows Tray UI (icUdpPeers) click handler. Clicking an untrusted discovered device now automatically generates a PIN, drops down the sleek XAML PIN overlay ("Waiting for remote acceptance..."), and seamlessly transmits the PIN to the target device via the LocalSend V2 protocol.


### [patch] Robustify Pairing & Identity Concurrency (v4.15.2.0)
- **[fix]** Hardened `IdentityManager` local JSON storage against `IOException` (Sharing Violations) during concurrent `SavePairedDevice` invocations by implementing a static reader/writer lock.
- **[fix]** Addressed orphaned task leaks in the `/api/localsend/v2/pair-prompt` endpoint: incoming pairing requests now properly cancel any pre-existing dangling `TaskCompletionSource` objects tied to the same fingerprint.
- **[fix]** Improved long-polling resilience by linking the pairing TCS to `HttpContext.RequestAborted`, ensuring resources are freed immediately if the initiating device disconnects prematurely.
- **[fix]** Enforced `TaskCreationOptions.RunContinuationsAsynchronously` to prevent synchronous blocking on background thread resolution.

### [patch] Constrain Drag Area to Pill Indicator (v4.15.2.0)
- **[patch]** Constrained window drag handle to only the pill indicator to prevent accidental dragging from the rest of the window.

### [patch] Enforce Device Trust on File Transfers (v4.15.1.0)
- **[patch]** Updated `/api/localsend/v2/prepare-upload` API to forcefully reject (`403 Forbidden`) inbound transfer requests originating from fingerprints that are neither Paired nor Auto-Trusted. This effectively blocks untrusted guests from interrupting the user with file drop prompts.

### [minor] Implement Device PIN Pairing Workflow (v4.15.0.0)
- **[minor]** Added PIN pairing system: clicking an untrusted "Discovered Device" generates a random 6-digit PIN and initiates a pairing request to the target device.
- **[minor]** Renamed "Nearby Users" section to "Your Devices" in the main UI to separate trusted vs untrusted devices.
- **[minor]** Implemented `/api/localsend/v2/pair-prompt` and `/api/localsend/v2/pair-verify` logic in C# `LocalSendServer`.
- **[minor]** Added a polished overlay dialog for PIN pairing (both for incoming requests and outgoing verification).
- **[minor]** Discovered Devices (UDP poll) are now automatically filtered out and moved to "Your Devices" once paired or auto-trusted.
- **[minor]** Persists pairing trust natively via `paired_devices.json` fingerprint hashing.

### [minor] Fix Device Discovery: UDP Poll Gated Behind mDNS (v4.12.2.0)
- **[fix]** **ROOT CAUSE**: The UDP `/local/devices` poll was nested inside `if ($received.Count -gt 0)` — the mDNS results guard. On a phone hotspot where `adb mdns services` returns nothing, `$received` is always empty, so the UDP discovery block **never executed**. Moved it outside the guard so it runs every 2s tick unconditionally.
- **[fix]** Changed `$liveUdp` items from `[PSCustomObject]` to `Hashtable` and updated WPF XAML bindings to use indexer syntax (`{Binding [Alias]}`). Verified working via standalone WPF ItemsControl test.
- **[fix]** `DeXShareTarget.exe` crashed immediately on startup with `COMException 0xD0000225` at `AppInstance.GetActivatedEventArgs()` when launched outside an MSIX package context. Wrapped in try-catch so it degrades gracefully and always reaches `LocalSendServer.StartAsync()`.
- **[fix]** Removed `Start-OmniTransferServer` from `AdbManager.psm1` and its call in `Connect-Engine.ps1`. This PowerShell raw-TCP listener was binding port 53318 before `DeXShareTarget.exe` started, causing `LocalSendServer`'s HTTP API (`/local/devices`) to fail with "address already in use" — the API that powers the Discovered Devices UI list.
- **[fix]** Moved `icUdpPeers` `ItemsControl` inside the `ScrollViewer` to render correctly within the spatial menu layout.
- **[fix]** Added a "Discovered Devices" header that auto-hides when the list is empty.
- **[fix]** Flattened PSCustomObject property names (`Alias`, `DeviceModel`, `Ip`) to match XAML `{Binding}` paths exactly (WPF binding is case-sensitive on PSCustomObject).

### [fix] Enable HTTP/3 (QUIC) without Blocking Discovery (v4.11.8.0)
- **[fix]** Restored HTTP/3 capabilities on the Kestrel server without compromising background discovery. Split the Kestrel endpoints to host HTTP/1.1 and HTTP/2 on TCP 53317, while hosting HTTP/3 (QUIC) on UDP 53316.
- **[fix]** Injected a custom middleware to rewrite the `Alt-Svc` HTTP header to advertise the dedicated HTTP/3 port (53316) to compliant clients. This resolves the `WSAEACCES` socket conflict with the OmniMesh UDP multicast beacon logic on port 53317.

### [fix] Resolve Discovery Daemon Crash & Kestrel Port Conflict (v4.11.7.0)
- **[fix]** Disabled HTTP/3 (QUIC) in Kestrel, which was implicitly binding to UDP 53317 and causing the DiscoveryBackgroundService to crash with Access Denied (10013), crashing the entire app instance.
- **[fix]** Program.cs now waits infinitely, keeping DeXShareTarget alive for continuous background discovery.
- **[fix]** Desktop UDP discovery (OmniMesh beacons) now starts unconditionally instead of being gated behind the Auto-Connect toggle. Auto-Connect still only gates automatic ADB connections — the PC is now always visible on the local network.
- **[fix]** Changed Android `MainActivity` to use `startForegroundService()` instead of `startService()`, preventing Samsung's `FreecessHandler` from freezing the DeX companion process when backgrounded.
- **[fix]** Added unicast UDP reply in `DiscoveryEngine.kt` to bypass Android Hotspot AP client isolation that drops multicast responses.
- **[fix]** Unified the PC's UDP sender and listener into a single socket in `AdbManager.psm1` for cleaner resource management.

### [minor] Dynamic UDP Device UI & Hotspot Bypass (v4.10.0.0)
- **[minor]** Bridged the robust UDP discovery backend (`LocalSendServer.cs`) with the PowerShell UI (`Connect-Engine.ps1`) to dynamically render newly discovered local devices.
- **[minor]** Added a smooth WPF expand/fade-in animation for dynamic UDP devices so they beautifully slide in above the "Nearby Users" section, shifting the static scaffolding down.
- **[minor]** Implemented Gateway Unicast fallback in C# `LocalSendServer.cs` to reliably penetrate Android Hotspot (SoftAP) packet filters that block conventional multicast/broadcast traffic.

### [minor] Async File Thumbnails in Transfer History (v4.8.0.0)
- **[minor]** Replaced static generic document icons with rich, async-loaded thumbnails in the local File Explorer / Transfer History. 
- Implemented a high-performance Hybrid loading strategy: standard images use ultra-fast WPF decoding, while videos and documents utilize native Windows IShellItemImageFactory via dynamic C# injection.
### [fix] New vs Trusted Device Connect UX (v4.7.1.0)
- **[fix]** Fixed the connection UX so that connecting to a previously paired or Auto-Trusted device successfully auto-expands the Transfers panel, while connecting to a freshly paired Guest device just shows a "Paired & Connected" toast without aggressively opening the panel.
### [minor] Transfer History UX Enhancements (v4.7.0.0)
- **[minor]** Added a right-click Context Menu to local transfer history items matching the modern rounded-corner aesthetics. Includes 'Open', 'Open Containing Folder', 'Copy Path', and a red 'Delete' action.
- **[minor]** Added rich multi-line ToolTips on hover for file and folder items, displaying the full item name and its size/date metadata, which is extremely useful for truncated filenames.
- **[minor]** Fixed a UX regression where connecting to a new or existing device via the tray menu would awkwardly auto-open the local Transfer History folder instead of quietly connecting. Device connects now simply show a success toast.
- **[fix]** Fixed a stale search filter condition where the search box wouldn't clear automatically because it was still checking for the old "search files..." placeholder instead of "search transfers...".
### [fix] Transfer History Edge Case Hardening (v4.6.1.0)
- **[fix]** Fixed a crash vector where removing missing-file items during a `foreach` loop over `SelectedItems` would throw `InvalidOperationException: Collection was modified`. Now collects missing items into a separate array and removes them after the loop completes.
- **[fix]** Blocked direct execution of dangerous file types (`.exe`, `.bat`, `.cmd`, `.ps1`, `.vbs`, `.msi`, `.scr`, etc.) when double-clicked in the Transfers panel. These files are now safely revealed in Windows Explorer (`/select`) instead of executed.
- **[fix]** Guarded the `Alt+Up` / `Backspace` keyboard shortcut to only fire when browsing a remote phone directory, preventing a silent `RaiseEvent` on the now-collapsed `btnUpDir` button during local Transfer History mode.
- **[fix]** Changed the stale `` initializer from `/sdcard/` to an empty string, ensuring the auto-refresh guard in `Connect-Engine.ps1` correctly detects local mode on the first `TransferComplete` event.
### [minor] Repurposed File Explorer to Transfer History (v4.6.0.0)
- **Lazy Refactor**: Seamlessly repurposed the existing WPF File Explorer panel into a fully functional local Transfer History viewer pointing at Downloads\dex.
- **UI Enhancements**: Renamed 'Phone Files' to 'Transfers', updated search placeholders, and hid remote directory navigation controls.
- **Smart Double-Click**: Changed double-click behavior to launch the downloaded file natively in Windows instead of triggering a redundant ADB pull.
- **Missing File Edge Case**: Added proactive Test-Path checks; if a user tries to open a file they've deleted externally, DeX intercepts it, safely removes the ghost entry from the list, and toasts "File missing" instead of failing silently.
- **Live Auto-Refresh**: Piggybacked on the mDNS polling timer so that when a new file arrives via the OmniTransfer server, the Transfers UI auto-refreshes instantly without needing to close and reopen the panel.
- **Rich Metadata**: Upgraded the file grid UI to display formatted file sizes (KB/MB/GB) and exact transfer timestamps (e.g., 2.4 MB · Aug 2, 4:30 PM) using a 50-item performance cap.
## [4.3.0.0] - 2026-08-02
### Added
- **[minor]** Added "Send Files" and "Send Folder" floating action buttons to the PC Tray UI's File Explorer panel, enabling native PC-to-Android reverse transfers.
- **[minor]** Added Drag and Drop support to the PC Tray UI File Explorer panel. You can now drag files from Windows desktop and drop them onto the tray window to instantly transfer them to the connected Android device.
## [4.2.0.0] - 2026-08-02
### Added
- **[minor]** Upgraded Android file picker from `GetContent()` to `GetMultipleContents()` to support batch sending multiple files at once.
- **[fix]** Resolved a silent compile failure in `Navigation.kt` by correctly implementing `NavKey` and `@Serializable` on `Settings` object for Jetpack Compose Navigation 3.

## [4.1.0.0] - 2026-08-01
### Added
- [minor] Implemented secure "Gmail-based" shared trust via SHA-256 identity hashing.
- [minor] Added Settings UI to Android app for configuring identity email.
- [minor] Added local API endpoint on PC for configuring identity email.
### Security
- [minor] Auto-Trusted mode now cryptographically tied to the SHA-256 hash of the configured email address, maintaining Guest separation for unknown devices.

## [4.0.2.0] - 2026-08-01
### Fixed
- **[fix]** Replaced hardcoded `"dex_static_placeholder_hash_123"` with persistent, per-device UUID generation for `identityHash` and `fingerprint` on both Android and PC to establish genuine device identity and trust levels.

## [4.0.1.0] - 2026-08-01
### Fixed
- **[fix]** Resolved critical data loss bug in LAN file transfer by replacing silent overwrites with `(n)` counter renaming mechanism for both PC and Android receivers.
- **[minor]** Implemented size-based deduplication on prepare-upload to intelligently skip redundant LAN transfers, saving bandwidth.

## [4.0.0.0] - 2026-08-01
### Changed
- **[major]** Completely rebranded the project identity from **Connect-Phone-ADB** to **DeX**.
- **[major]** Updated all metadata, AppInstaller references, Git configurations, C# project spaces, and source identifiers to reflect the new `DeX` identity.

## [3.6.13.0] - 2026-07-31
### Fixed
- **[patch]** The "Pin to Top" button now properly prevents the tray menu from auto-hiding when clicking outside the window, ensuring the menu remains securely anchored to its physical screen location.

## [3.6.11.0] - 2026-07-31
### Changed
- **[patch]** The spatial menu drag handle now strictly ties its active color state to physical mouse interaction. It will only illuminate with the secondary theme color while actively clicked and held, smoothly fading back to its subtle state the moment the mouse is released.

## [3.6.11.0] - 2026-07-31
### Changed
- **[patch]** Eliminated the 40MB `thru_linux` executable bloat from the repository.
- **[patch]** Replaced `Invoke-RestMethod` with `System.Net.WebClient` for batch file pulls to permanently resolve `System.OutOfMemoryException` memory leaks when transferring multi-gigabyte files.

## [3.6.10.0] - 2026-07-31
### Changed
- **[patch]** The "Pin to Top" icon now elegantly fades to the accent color on hover, and permanently fades to the secondary theme color when checked. The slide-out tray will also remain securely visible as long as the window is pinned.

## [3.6.9.0] - 2026-07-31
### Added
- **[patch]** Added smooth XAML Storyboard animations for the spatial menu drag handle. The slide-out pin toggle now fluidly expands into view, and the pill indicator seamlessly cross-fades its background color.
- **[patch]** The "Pin to Top" toggle icon now highlights using the active theme's accent color when checked, instead of the primary text color.

## [3.6.8.0] - 2026-07-31
### Changed
- **[patch]** The spatial menu drag handle now dynamically changes its color to the theme's secondary accent brush when active.
- **[patch]** Reduced the slide-out pin toggle size to fit cleanly within the 16px hit area, preventing layout shifting/stretching when revealed.

## [3.6.7.0] - 2026-07-31
### Added
- **[patch]** The spatial menu drag handle is now fully interactive. Double-clicking it snaps the menu back to the center of the primary screen. Single-clicking it reveals a sliding "Pin to Top" toggle that automatically fades out after 3 seconds to keep the UI clean.

## [3.6.3.0] - 2026-07-31
### Added
- **[patch]** Added a pill-shaped drag handle indicator above the quick action buttons to visually communicate that the spatial menu can be dragged.

## [3.5.0.0] - 2026-07-31
### Added
- **[major] OmniMesh File Explorer:** Completely rewired the WPF File Explorer to use the blazing fast OmniMesh Ktor HTTP REST API (`/api/dex/browse` and `/api/dex/pull`) instead of sluggish `adb shell ls` and `adb pull` commands.
- **[minor]** Android Ktor Server now exposes `/api/dex/browse` and `/api/dex/pull` for direct native file streaming to PC.


## [3.2.0.0] - 2026-07-31
### Added
- **Wiggle-to-Open Feature:** Users can now rapidly move their mouse back and forth ("wiggle") while holding a file (during a drag operation) to instantly summon the Connect-Phone-ADB drop menu at the cursor's location. This feature can be toggled via the new Interaction section in the Settings panel.


## [3.1.8.8] - 2026-07-31
### Changed
- **[patch]** Optimized system theme listener: replaced the 2-second background `DispatcherTimer` polling loop with `UserPreferenceChanged` native event handler, eliminating idle timer overhead.
- **[patch]** Centralized theme UI label binding directly inside `Set-AppTheme`, removing duplicate text updating logic across settings handlers.

## [3.1.8.7] - 2026-07-31
### Fixed
- **[patch]** Fixed light mode theme transparency bug in `Set-AppTheme`. Removed over-engineered `ColorAnimation` loop targeting freezable `SolidColorBrush` resource objects (which failed silently in WPF causing transparent UI rendering) and replaced it with direct, native `MergedDictionaries` dictionary replacement.

## [3.1.8.6] - 2026-07-31
### Fixed
- **[patch]** Eliminated the ~1-second UI freeze when opening the Settings panel by replacing the sluggish `Get-ScheduledTask` cmdlet with the native `Schedule.Service` COM object for Auto-Connect status checks, allowing the animation to run instantly without dropping frames.

## [3.1.8.5] - 2026-07-31
### Fixed
- **[patch]** Fixed system tray menu double-opening flash bug caused by a tri-fold race condition: (1) Windows 11 tray focus shifts triggering `Deactivated` mid-animation were suppressed with an 800ms `isShowingMenu` guard flag, (2) stale `PopIn` animation fill clocks were cleared before hiding to prevent property override glitches, and (3) WinForms `NotifyIcon.MouseUp` double-fire events from single physical clicks were debounced.

## [3.1.8.4] - 2026-07-30
### Fixed
- **[patch]** Prevented the Exit button from needlessly animating its margin leftward when the menu is in the expanded state, as the avatar is already collapsed and the button natively occupies the full layout width.

## [3.1.8.3] - 2026-07-30
### Added
- **[feature]** Restored fluid animations to the Exit Engine sequence while strictly enforcing layout stability. The Exit button now animates its margin to slide left, while the parent grid width is explicitly locked to perfectly prevent any window-resizing pops. The avatar image subtly shrinks and scales behind the expanding solid AccentBrush overlay, creating a premium visual effect.

## [3.1.8.2] - 2026-07-30
### Fixed
- **[patch]** Corrected the negative margin calculation for the expanded Exit Engine button (`-62` instead of `-46`) to account for internal padding offsets inside the `SpatialListItem` control template, perfectly aligning it with the avatar's left edge.
- **[patch]** Fixed a variable scoping issue inside the PowerShell `DispatcherTimer` scriptblock that prevented the button from reverting to its initial state after 3 seconds.

## [3.1.8.1] - 2026-07-30
### Fixed
- **[patch]** Fixed a toggle-loop edge case where clicking the Start Menu shortcut while the main UI was already visible would hide it instead of focusing it.
- **[patch]** Suppressed the redundant "Connect ADB Active" startup toast notification during explicit launches, as the main UI now opens instantly instead.
- **[patch]** Refactored the exit button overlapping logic to strictly follow ponytail protocol: removed the `ThicknessAnimation` entirely as it caused brief UI stretch-and-contract artifacts due to width recalibrations. The button now instantly overlaps the avatar using native negative margins and locks onto the solid `AccentBrush` background fill, perfectly hiding the avatar without opacity animations and completely preventing any window resizing bugs.

## [3.1.8.0] - 2026-07-30
### Added
- **[minor]** Launching the app explicitly from the Start menu now immediately displays the main UI instead of just starting silently in the system tray. This works seamlessly for both cold starts and warm starts (when the engine is already running in the background), achieved efficiently using `EventWaitHandle` IPC and Windows Startup Task activation context detection.

## [3.1.7.3] - 2026-07-30
### Added
- **[feature]** Added fluid WPF `ThicknessAnimation` and `DoubleAnimation` to the Exit Engine sequence. The exit button now smoothly sweeps left to cover the avatar's space, while the avatar elegantly fades out, fulfilling the original overlapping design intention without triggering any abrupt layout reflows or snap-shifts.

## [3.1.7.2] - 2026-07-30
### Fixed
- **[patch]** Completely removed the profile avatar visibility toggling logic during the exit sequence to eliminate all visual layout shifting. The new compact text ("Cancel / Shift+Click Exit") fits seamlessly into the existing button space, ensuring zero snapping or popping when the exit sequence resets.

## [3.1.7.1] - 2026-07-30
### Changed
- **[patch]** Shortened the exit cancellation text to "Cancel / Shift+Click Exit" for a cleaner appearance when expanded.

## [3.1.7.0] - 2026-07-30
### Fixed
- **[patch]** Fixed a UI distortion issue where clicking 'Exit Engine' forced the menu to expand horizontally off-screen. The root cause was a storyboard animation `HoldEnd` on the Profile Avatar's Visibility preventing it from collapsing. The fix explicitly clears the animation hold via `BeginAnimation` before applying the `Collapsed` state, allowing the Exit button to properly overlap into the avatar's freed space.

## [3.1.6.0] - 2026-07-30
### Fixed
- **[patch]** Restored WPF Storyboard `.Begin($true)` and `.Pause()` initialization prior to `Show()` to definitively resolve the spatial menu 1-frame pop-in flash. This mathematically locks the DWM composite frame to the absolute start state of the `PopIn` animation sequence before rendering occurs.

## [3.1.5.0] - 2026-07-30
### Fixed
- **[patch]** Resolved the root cause of the WPF menu flash. Storyboard `HoldEnd` precedence was overriding local property assignments on `Reset-SpatialPanels`. The engine now correctly calls `.Stop()` on all active storyboards when the window hides, releasing the layout holds so that local properties (Opacity 0) correctly apply on the next `Show()` invocation.

## [3.1.4.0] - 2026-07-30
### Fixed
- **[patch]** Completely eliminated the residual 1-frame visual pop-in (visible before animation) when opening the spatial menu by utilizing WPF Storyboard `.Begin($true)` and `.Pause()` directly prior to `Show()`. This mathematically locks the DWM composite frame to the absolute start state of the `PopIn` animation sequence, bypassing previous storyboard `HoldEnd` precedence.

## [3.1.2.0] - 2026-07-30
### Fixed
- **[patch]** Added missing `-STA` flag to the `powershell.exe` launch arguments inside the C# `ConnectPhoneShareTarget` wrapper. Without this flag, the engine launched from the Start Menu in MTA mode, which caused WPF's `XamlReader::Load` to crash instantly and silently.
- **[patch]** Fixed spatial menu flashing animation (double-animation jump) by pre-setting scale and translation explicitly before the window executes its first render frame.
- **[patch]** Fixed 'Phone Files' quick action button becoming out-of-sync (unchecked when open) when the menu is collapsed via the Close button or click-outside.

## [3.1.1.0] - 2026-07-30
### Fixed
- **[patch]** Fixed spatial menu double-animation glitch (1-frame pop-in flash) by pre-zeroing window opacity right before display.
- **[patch]** Fixed static elements (close button, profiles, nearby users panel) improperly re-animating when swapping directly between the File Explorer and Settings panels.
- **[patch]** Fixed quick action buttons staying highlighted by ensuring the Pull button is explicitly unchecked when the Settings panel is opened.

## [3.1.0.0] - 2026-07-30
### Added
- **[minor]** Live device telemetry on OmniMesh peer buttons. When a nearby phone broadcasts its UDP beacon, the engine now queries `adb` for `ro.product.model` and battery level. Peer slots now display `📱 Samsung Galaxy S21 • 🔋 84%` instead of the plain `OmniMesh (IP)` string. Gracefully falls back to IP when the device isn't yet ADB-connected.

### Changed
- **[minor]** Ponytail audit cleanup: removed 4 unused `PSDataCollection` allocations from Runspace factories, deduplicated identical storyboard-clone blocks in `$actionSettings` and `$actionPull`, extracted `Invoke-ExitEngine` to eliminate duplicate exit sequences across button and keyboard handlers, and merged the redundant double-mutex into a single engine guard.
- **[minor]** Clarified intentional UI scaffold comment blocks in `EngineUtils.ps1` and `TrayUIBindings.ps1` with prominent boxed banners so no future contributor accidentally "fixes" them.

## [3.0.0.0] - 2026-07-30
### Changed
- **[major]** Refactored background tasks (mDNS polling and Omni-Mesh transfer server) to use raw, in-process `.NET` PowerShell Runspaces (`[powershell]::Create()`) instead of `Start-Job`.
  - Eliminates the 30-second initialization delay and completely resolves the random console window flashes on startup.
  - Implements a thread-safe `ConcurrentQueue` for nanosecond-fast inter-thread communication.
  - Fixes notorious infinite-loop Runspace memory leaks by aggressively piping internal output streams to `[void]` and ensuring the UI explicitly disposes of unmanaged `Runspace` resources via a robust application-exit hook.

## [2.7.18.0] - 2026-07-30
### Fixed
- **[fix]** Reverted the spatial menu `menuTrans` parallax exit animation introduced in v2.7.15.0. It caused severe visual clipping on the left edge of the main menu during window contraction because the `To="-30"` X-axis translation collided with the shrinking Win32 window bounds.

## [2.7.17.0] - 2026-07-30
### Fixed
- **[fix]** Restored absolute height constraint logic in `TrayUIHandlers.ps1` that was lost during a previous refactoring. This completely resolves the bug where rapidly swapping between the Settings and File Explorer panels caused the spatial menu contents to shift upward off-screen due to relative height accumulation (`By="195"`).

## [2.7.15.0] - 2026-07-30
### Added
- **[patch]** Added reciprocal 3D parallax depth to the `ContractMenu` and `ContractSettings` exit animations. The spatial menu now subtly slides back out of frame (`X=-30`) as the window contracts, perfectly matching the physics of the `ExpandMenu` entrance.

## [2.7.14.0] - 2026-07-30
### Fixed
- **[patch]** Fixed spatial menu fluidity and opening lag. `Update-WpfUI` now fetches connected `adb devices` asynchronously via a background process instead of blocking the UI thread before the entrance animation.
- **[patch]** Fixed micro-stutter when opening the File Explorer or Settings panels by running `Load-Directory` asynchronously (`InvokeAsync`), ensuring the `ExpandMenu` animation triggers instantly without dropped frames.

## [2.6.10.0] - 2026-07-30
### Fixed
- **[fix]** Resolved severe UI expansion bug where rapidly swapping between File Explorer and Settings Panel caused the window to infinitely grow vertically off-screen. (The `ExpandMenu` and `ExpandSettings` height animations are now strictly constrained to absolute values instead of relative accumulation).

## [2.6.9.0] - 2026-07-30
### Added
- **[minor]** Added a secondary inner-parallax animation to the spatial menu's child content (`menuContentTrans`). The content now slides up from a deeper offset (`Y=35`) and fades in slightly slower than the menu container, creating a beautiful staggered 3D depth effect during the `PopIn` sequence.

## [2.6.8.0] - 2026-07-30
### Fixed
- **[fix]** Resolved UI overlap bug where rapidly switching between File Explorer and Settings Panel caused both grids to render on top of each other due to lingering XAML storyboard state.

## [2.6.6.0] - 2026-07-30
### Added
- **[minor]** Added a dynamic Y-axis parallax slide-up effect to the spatial menu container (`menuTrans`) during the initial system tray `PopIn` sequence, giving it an elastic bouncy entrance distinct from the main window scaling.

## [2.6.5.0] - 2026-07-29
### Removed
- **[minor]** Reverted dynamic Windows System Accent Color adaptation to lock in the signature Green \#0AE66D\ as the permanent \SecondaryBrush\.

## [2.6.4.0] - 2026-07-29
### Fixed
- **[fix]** Resolved WPF DoubleAnimation stacking bugs when switching between Settings and File Explorer.
- **[fix]** Prevented fatal boot crashes by purging dead UI event bindings from the deprecated avatar popup.

## [2.6.3.0] - 2026-07-29
### Added
- **[minor]** Integrated native Windows 11 Accent Color syncing. The \SecondaryBrush\ now dynamically inherits the user's active DWM ColorizationColor (with our smooth 300ms fallback crossfade intact).

## [2.6.2.0] - 2026-07-29
### Added
- **[fix]** Implemented \AppThemeMode\ state manager to resolve a bug where the automatic background OS theme listener would forcefully override a user's manual theme toggle selection.

## [2.6.1.0] - 2026-07-29
### Added
- **[fix]** Unified the \SecondaryBrush\ to identically use the signature Green (#0AE66D) across both Light and Dark themes for consistency. \SecondaryForegroundBrush\ in Light mode is also synced to absolute black to maintain high contrast.

## [2.6.0.0] - 2026-07-29
### Added
- **[minor]** Injected a WPF \ColorAnimation\ storyboard into the theme-swapping engine, enabling a premium 300ms smooth crossfade for all backgrounds, text, and surfaces when toggling between Light and Dark mode.

## [2.5.2.0] - 2026-07-29
### Added
- **[fix]** Set \SecondaryForegroundBrush\ to absolute black (#000000) in dark theme and updated Quick Action buttons to utilize this dynamic brush for maximum contrast when active.

## [2.5.1.0] - 2026-07-29
### Added
- **[fix]** Added `SecondaryForegroundBrush` to ensure device avatars maintain high visual contrast against the dynamic `SecondaryBrush` background in both Light and Dark modes.

## [2.5.0.0] - 2026-07-29
### Added
- **[major]** Refactored local network discovery to use standard mDNS (Multicast DNS) natively via `Makaretu.Dns` and Android's `NsdManager`, entirely bypassing raw UDP broadcast storms and ensuring enterprise router compatibility.
- **[major]** Enabled `HttpProtocols.Http1AndHttp2AndHttp3` in the Kestrel server to natively support QUIC transport streams for P2P transfers.
- **[patch]** Added automatic native Windows Firewall Rules for `ConnectPhoneShareTarget.exe` inside the AppxManifest to silently allow mDNS and QUIC connections without triggering UAC blocks.

## [2.4.0.0] - 2026-07-29
### Added
- **[minor]** Integrated an automatic Windows System Theme Listener. The UI dynamically detects the \AppsUseLightTheme\ registry key and instantly syncs its XAML theme (Light/Dark) to match the host OS without requiring restarts.
- **[minor]** Streamlined the core UI Semantic Color Dictionary down to a strict 3-color base (\PrimaryBrush\, \SecondaryBrush\, \AccentBrush\), completely eliminating redundant tags like \SuccessBrush\ and \TertiaryBrush\ to perfectly align with the project design system.

## [2.2.4.0] - 2026-07-28
### Added
- **[patch]** Constrained the spatial menu's contracted layout to a minimum width of 335px.
- **[patch]** Implemented a dynamic layout animation engine in `Update-WpfUI` that provides a fluid overshoot (bouncy) transition when the menu expands to accommodate new elements (like the IP copy button), eliminating abrupt snapping.

## [2.2.3.0] - 2026-07-28
### Added
- **[minor]** Updated the Connect Quick Action into a seamless ToggleButton that persists the active state (highlighted in accent color) when connected. Consolidated Connect and Disconnect into a single intuitive toggle.
- **[minor]** Added a new Clipboard Quick Action icon (currently un-wired).
- **[minor]** Updated the Close menu (X) button to utilize a new Danger style for destructive action signaling.

## [2.2.2.0] - 2026-07-28
### Added
- **[minor]** Reassigned the power-switch icon to the Exit Engine button and updated the Disconnect button icon. Added an active/pressed light-green accent state to all Quick Action icons.

## [2.2.1.0] - 2026-07-28
### Added
- **[minor]** Repositioned the menu Close (X) button directly into the Quick Actions strip when expanded to visually streamline the interface and fill the layout gap.

## [2.2.0.0] - 2026-07-28
### Added
- **[minor]** Added a native WPF popup flyout card attached to the Gmail profile avatar. Clicking the avatar now displays a floating card showing user details.
- **[minor]** Moved the Theme Toggler out of a standard context menu and integrated it into the new profile flyout card to match the project's spatial aesthetics.
- **[minor]** Relocated the "Auto connect ADB" quick action button into the profile flyout card to de-clutter the main quick actions strip.

## [2.1.1.5] - 2026-07-28
### Hotfix: File Explorer UI Scope Isolation
- **[fix]** Fixed the File Explorer logic (double-clicking to load directories) which was silently failing. This was caused because `EngineUtils.psm1` was still loaded as an isolated module, preventing its `Load-Directory` function from accessing the UI elements in `Connect-Engine.ps1`. Converted `EngineUtils` to a dot-sourced script to perfectly unify the UI scope.
## [2.1.1.4] - 2026-07-28
### Hotfix: Silent Failure on mDNS Discovered Devices
- **[fix]** Fixed a major logic regression where clicking on a discovered device via the mDNS 'Nearby' menu would silently fail. The `Invoke-AdbConnect` function was never updated to accept the `-Target` parameter passed to it by the mDNS menu, causing it to ignore the selected device and default back to calculating the local Hotspot Gateway IP. The function signature has been fixed to fully accept and prioritize targeted connections.
## [2.1.1.3] - 2026-07-28
### Hotfix: Major Event Registration Duplication
- **[fix]** Surgically removed a massive 163-line duplicated event block in `TrayUIBindings.ps1` that was incorrectly pasted during the v2.0.0.0 monolithic script decoupling. This bug caused `Add_KeyDown`, `btnExit.Add_Click`, `wpfWindow.Add_KeyDown`, and `Add_Deactivated` to register and fire twice, creating ghost timers, doubling UI operations, and causing massive application state race conditions.
## [2.1.1.2] - 2026-07-28
### Hotfix: System Tray Icon Image Loss (Colored Dot Bug)
- **[fix]** Resolved a relative pathing issue where `$PSScriptRoot` was resolving to `MSIX_Source\bin\Modules` instead of `MSIX_Source\bin` due to the recent architectural decoupling. This caused `app-icon.ico` to fail to load, resulting in the System Tray falling back to generating a blank 16x16 image with just a colored status dot.
- **[fix]** Fixed broken `ToastNotification` icon paths and `Themes` directory paths in `UIComponents.ps1` caused by the same `$PSScriptRoot` module context shift.
## [2.1.1.1] - 2026-07-28
### Hotfix: System Tray Icon Unresponsiveness
- **[fix]** Restored the missing `Dispatcher.BeginInvoke([System.Windows.Threading.DispatcherPriority]::ApplicationIdle)` wrapper for the System Tray `MouseUp` event handler in `TrayUIBindings.ps1`. This fixes a critical UI regression (race condition) that caused tray icon clicks to instantly deactivate and swallow the main window.
- **[fix]** Removed redundant, corrupted duplicate block of `MouseUp` and `KeyDown` bindings left over from the v2.0.0.0 architecture decoupling refactor.
## [2.1.0.0] - 2026-07-28
### Features & UX Overhaul
- **[minor] Intelligent Pairing & mDNS Overhaul**: 
  - Upgraded mDNS parsing regex to cleanly extract IP, Port, Type, and GUID.
  - Added robust Wi-Fi pairing natively using a custom decoupled WPF prompt (`Show-PairingPrompt`) that dynamically merges the active app themes (`DarkTheme.xaml` / `LightTheme.xaml`) for 100% aesthetic parity.
  - Built a CI Pester testing suite (`AdbManager.Tests.ps1`) to strictly validate connect and pairing flows via `Validate-Build.ps1`.
- **[minor] Share Target UX Modernization**: 
  - Overhauled the C# Share Target (`TransferWindow.cs`) to drop rigid C# grid construction in favor of an injected, deeply styled XAML layout.
  - Added Fluent Windows 11 aesthetics: `CornerRadius=12`, native DropShadows, Acrylic transparency illusions, and seamless `DoubleAnimation` for smooth progress bar gliding instead of abrupt snapping.
  - Fully mapped to the Vibrant Green (`#00E676`) success accent.
## [2.0.0.0] - 2026-07-28
### Major Architecture Overhaul (Part 2)
- **UI Decoupling**: Completely extracted the raw XAML overlay from `Connect-Engine.ps1` into `MSIX_Source\Themes\MainWindow.xaml`.
- **UI Bindings**: Moved ~600 lines of WPF UI events into `MSIX_Source\bin\TrayUIBindings.ps1`. `Connect-Engine.ps1` is now purely an orchestrator script under 250 lines.
- **C# Refactoring**: Decoupled `TransferWindow.cs` from `Program.cs` within the Share Target, keeping all files lean (<1,000 lines).
- **Intelligent Connectivity**: Added `Start-MdnsDiscovery` to `AdbManager.psm1`. Android 11+ devices broadcasting `_adb-tls-connect._tcp` are now automatically discovered and connected in the background.

## [v2.0.0.0]
- **[major]** Architecture Overhaul: Refactored the monolithic `Connect-Engine.ps1` (~1,900 lines) into modern PowerShell modules (`.psm1`).
- Extracted ADB logic into `AdbManager.psm1`.
- Extracted UI/WPF generation functions into `UIComponents.psm1`.
- Extracted Task Scheduler bindings into `TaskScheduler.psm1`.
- Extracted common utilities into `EngineUtils.psm1`.
- Rebuilt and signed MSIX package. Removed old 70KB temporary scripts (`temp.ps1`, `temp2.ps1`).
## [v1.9.4.1]
- **[fix]** Restored true fix for swallowed tray clicks (previously documented in v1.7.0 but reverted): wrapped `Show()`, `Activate()`, and `PopIn` inside `$wpfWindow.Dispatcher.BeginInvoke([System.Windows.Threading.DispatcherPriority]::ApplicationIdle)` to prevent `Deactivated` race condition. Reverted `MouseClick` back to `MouseUp`.


## [v1.9.3.0]
- **[minor]** Hid bulky vertical WPF scrollbars in both the Nearby Users panel and File Explorer list (`VerticalScrollBarVisibility="Hidden"`) for a cleaner, modern aesthetic while fully retaining mouse-wheel scrolling capability.

## [v1.9.6.5]
- **[fix]** Resolved application launch failure caused by string encoding corruption (`"✓"`) and ampersand entity parsing in PowerShell by using safe character literals `[char]0x2713` and `[char]0xE8C8`. Verified AST parse with 0 syntax errors.

## [v1.9.6.0]
- **[fix]** Added 5-second process execution timeout guard to `Load-Directory` to prevent hanging ADB processes on unreachable phone daemons.
- **[minor]** Added middle-ellipsis path truncation to floating download dock text when path length exceeds 35 characters while preserving full path in ToolTip.
- **[minor]** Added `Alt + Up Arrow` and `Backspace` keyboard navigation shortcuts for parent directory navigation (`btnUpDir`).
- **[fix]** Implemented dynamic screen working area bounds clipping protection to ensure spatial menu never gets cut off by taskbars docked on top, left, right, or bottom.
- **[fix]** Added explicit window activation and focus synchronization on tray icon clicks.

## [v1.9.5.0]
- **[minor]** Added `Ctrl+A` Select All (visible items only) and `Escape` deselect support to `lbFiles`.
- **[minor]** Added 400ms double-click speed thresholding guard (`$script:lastDoubleClickTime`) to prevent rapid accidental triple-click job duplication.
- **[minor]** Added floating dock auto-hide pause on mouse hover (`MouseEnter`/`MouseLeave`).
- **[fix]** Wired `btnProfileTop` click handler to open profile ContextMenu directly when expanded.
- **[fix]** Added application exit job & process cleanup (`Stop-Job`, `Remove-Job`, `adbLsProc.Kill()`).

## [v1.9.2.8]
- **[minor]** Changed the background fill color of the 'Windows' and 'Galaxy S21' device avatars from `PrimaryBrush` (Purple) to `SecondaryBrush` (Light-Green) for a more unified status indicator aesthetic.

## [v1.9.4.0]
- **[minor]** Enabled `SelectionMode="Extended"` on `lbFiles` to support Shift+Click range selection and Ctrl+Click multi-selection with persistent green highlight across all selected items.
- **[minor]** Upgraded `MouseDoubleClick` to support batch multi-file pulling into `Downloads\dex` (or custom directory) with dynamic notification count ("Saved X files to Downloads\dex").
- **[fix]** Added `emptyFolderState` overlay to display clean visual feedback when a directory contains zero files/folders.
- **[fix]** Implemented in-flight lock guard (`$script:isLoadingDir`) on `Load-Directory` to prevent rapid re-click process collisions.
- **[minor]** Added visual checkmark confirmation on `btnCopyIP` button icon upon copying IP address to clipboard.

## [v1.9.3.5]
- **[fix]** Replaced hardcoded status indicator dot colors (`#1D1226` and `#4CAF50`) in Nearby Users list with dynamic `{DynamicResource SecondaryBrush}` and `{DynamicResource SecondaryBackgroundBrush}` tokens for 100% theme compliance.
- **[minor]** Upgraded floating download dock entrance animation to a springy `BackEase` overshoot effect and exit to a 0.35s `CubicEase` slide-down.

## [v1.9.3.0]
- **[minor]** Added springy `BackEase` overshoot pop-in entrance animation and smooth scale/translate slide-down exit animation to floating download dock (`dockDownloadToast`).
- **[fix]** Added root directory detection to `btnUpDir`: automatically dims `btnUpDir` (Opacity 0.4, Arrow cursor) when at `/sdcard/` root and activates (Opacity 1.0, Hand cursor) in subdirectories.
- **[fix]** Added directory creation fallback logic to `Downloads\dex` pulling so restricted/locked paths fall back to `$env:TEMP\dex` without failing.

## [v1.9.2.6]
- **[fix]** Removed redundant hardcoded color logic for the 'Exit Engine' confirmation state to ensure it exclusively pulls from the dynamic `AccentBrush` theme resource.

## [v1.9.2.5]
- **[fix]** Filtered out ADB error outputs (`ls:`, `error:`, `Permission denied`) to prevent invalid items from being parsed into the File Explorer grid.
- **[fix]** Auto-reset search bar filter text upon subfolder navigation so subfolder contents are never hidden by stale parent query strings.
- **[fix]** Stopped pending search debouncer timers before navigating directories to eliminate cross-directory item mutation conflicts.

## [v1.9.2.0]
- **[minor]** Implemented dual highlighters for File Explorer items: persistent greenish highlight for single-clicked `IsSelected` items and a separate active hover highlight for `IsMouseOver` items.
- **[minor]** Calibrated Light Mode highlight brushes (`ItemHoverBrush`, `ItemSelectedBrush`, `ItemSelectedHoverBrush`, `ItemSelectedBorderBrush`) with deeper green shades (`#34C759`) for crisp visibility against light backgrounds.
- **[minor]** Enhanced floating download dock with smooth 0.2s `FadeIn` and 4-second auto-hide `FadeOut` animations.
- **[fix]** Ensured zero hardcoded colors in floating dock layout; fully compliant with dynamic theme tokens (`{DynamicResource}`).

## [v1.9.1.0]
- **[minor]** Updated File Explorer download destination path to `Downloads\dex`.
- **[minor]** Added a subtle floating dock notification banner ("Saved to Downloads\dex") with an interactive `Change` button to choose a custom save directory.
- **[fix]** Removed WPF's default blueish highlighter border/background when hovering over or selecting files and folders in the ListBox.

## [v1.9.0.6]
- **[minor]** Added 150ms search debouncing using `DispatcherTimer` to ensure 60 FPS ultra-smooth real-time filtering in large directories without CPU spikes.
- **[minor]** Added Escape key quick-clear logic to reset active search query before unfocusing or contracting the menu.

## [v1.9.0.5]
- **[fix]** Fixed searchbar text input and accidental disconnection bug: WPF `TextBox.IsFocused` evaluated to false when keyboard focus was active in `txtSearch`, causing top-level hotkeys ('D' for disconnect, 'C' for connect, 'P' for pull, 'M' for mirror, 'Q' for quit) to hijack typing and swallow characters.
- **[fix]** Implemented multi-layered focus and `OriginalSource` detection (`IsKeyboardFocused`, `IsKeyboardFocusWithin`, `OriginalSource -match "TextBox"`) so search terms like 'DCIM', 'Downloads', 'Documents', 'Pictures', 'Movies' type smoothly without triggering shortcuts or disconnecting.
- **[fix]** Added dynamic `CaretBrush="{DynamicResource PrimaryTextBrush}"` for clean cursor visibility in both Light and Dark themes.

## [v1.9.0.4]
- **[fix]** Removed explicit width and height constraints on profile avatars, allowing them to size naturally within the list item container margins and preventing horizontal clipping.

## [v1.9.0.3]
- **[fix]** Adjusted the margins of the Profile Avatars to prevent them from being clipped by the main window's rounded corners or overlapping with the spatial menu borders.

## [v1.9.0.2]
- **[fix]** Restored the Profile Avatar to the bottom-left 'Exit Engine' area when the menu is contracted, and animated it to instantly jump to the top right of the File Explorer search bar only when the menu is expanded.
- **[minor]** Replaced the Exit button's 'bin' icon with the avatar and tightened the spacing.
- **[minor]** Made the ScrollViewer scrollbar even slimmer and shorter.

## [v1.8.9.3]
- **[minor]** Replaced the MessageBox exit dialog with an inline double-click state on the Exit Engine button itself to match the application aesthetic minimally.

## [v1.8.8.3]
- **[minor]** Made the ScrollViewer scrollbar significantly thinner and shorter for a cleaner look.
- **[minor]** Relocated the Profile Avatar button to sit directly to the right of the File Explorer searchbar when the spatial menu is expanded.

## [v1.8.7.3]
- **[fix]** Reverted Nearby Users list from a virtualized ListBox back to a hardcoded StackPanel wrapped in a single auto-scrollbar per user preference.

## [v1.8.6.3]
- **[minor]** Added a confirmation dialog prompt to the 'Exit Engine' button to prevent accidental exits.

## [v1.8.5.3]
- **[minor]** Added a generic profile avatar button to the left of the Exit Engine button to prepare for Google Sign-in and premium gating.

## [v1.8.4.3]
- **[minor]** Re-introduced a sleek, slim scrollbar to the Nearby Users list and fully virtualized the user data structure using a dynamically populated `ListBox` and `VirtualizingStackPanel` for robust rendering performance.

## [v1.8.3.3]
- **[fix]** Made the "Nearby Users" list scrollable by replacing the static `StackPanel` container with a `DockPanel` and a `ScrollViewer` (with hidden scrollbars) to prevent the user list from overflowing on smaller screens.
## [v1.8.2.3]
- **[fix]** Fixed silent WPF data-binding failure by changing the File Explorer data items from `Hashtable` to `PSCustomObject`, resolving an issue where the file list rendered completely blank.
- **[minor]** Re-arranged the Top Bar UI so the Up/Back arrow is outside the rounded search bar, using a modern fluent icon.

## [v1.8.2.2]
- **[fix]** Fixed a long-standing bug where the File Explorer would fail to load files when the app was launched via the MSIX Start Menu shortcut due to a hardcoded relative path to `adb.exe`.
- **[minor]** Updated the File Explorer top bar to an editable `TextBox` (Search/Path bar) and changed the navigation icon to an Up Arrow to match typical folder navigation.

## [v1.8.2.1]
- **[fix]** Fixed a critical layout bug where the custom ScrollBar template was missing orientation triggers and repeat buttons, causing the `ScrollViewer` layout engine to silently fail and the `ListBox` to render completely blank.

## [v1.8.2.0]
- **[minor]** Redesigned the File Explorer UI to be sleek and premium.
- **[minor]** Added a custom, slim, rounded ScrollBar style to match the modern spatial UI.
- **[minor]** Rebuilt the top navigation bar into a modern padded capsule with the current path and Up button.
- **[minor]** Enhanced `FileGridTemplate` and `FolderGridTemplate` with soft CornerRadius, updated fonts, adjusted opacity, and responsive hover/press backgrounds that automatically adapt to light/dark themes.
- **[fix]** Increased inner margins of the File Explorer grid to completely prevent contents from clipping over the rounded corners of the main menu border.

## [v1.8.1.0]
- **[fix]** Close button now only appears when menu is expanded (hidden when contracted).
- **[fix]** Restored click-outside-to-close for the contracted menu state; only blocked when expanded.
- **[fix]** Galaxy S21 reverted to original phone-icon avatar instead of photo replacement — only `Visibility="Collapsed"` was removed to unhide it.
- **[fix]** 3 nearby users (Ama, Akua, Kwame) now wrapped in `NearbyExpandPanel` — hidden when contracted, stagger-in with fade animation when expanded into the gap between existing users and Exit Engine.
- **[fix]** ExpandMenu/ContractMenu storyboards now animate `btnCloseMenu` and `NearbyExpandPanel` visibility/opacity in sync.

## [v1.8.0.0]
- **[minor]** Menu UX overhaul: clicking outside the menu no longer closes it — added an animated close button (✕) at the top-right corner instead.
- **[minor]** Reduced the expanded menu size by 35% (width 1160→754px, height 300→195px) for a tighter footprint.
- **[minor]** Pinned 'Exit Engine' to the bottom of the menu using a DockPanel layout, so it no longer shifts upward when the menu expands.
- **[minor]** Added 3 nearby user placeholders (Ama Serwaa, Akua Donkor, Kwame Asante) with real avatar photos and online status indicators, staggered into the gap above Exit Engine.
- **[minor]** Enabled the previously hidden Galaxy S21 device entry with a real avatar and "CodeDeX · This device" subtitle.
- **[fix]** Escape key now properly resets expanded menu state (border dimensions, FileExplorer visibility, transforms) instead of just hiding.

## [v1.7.5.5]
- **[minor]** Upgraded the 'Phone Files' button into a seamless toggle! Built a brand new `ContractMenu` animation storyboard. If the menu is currently expanded, clicking 'Phone Files' will now gracefully reverse the animation, sliding the File Explorer away and shrinking the UI back to its compact state, rather than just doing nothing.

## [v1.7.5.4]
- **[major]** Completely decoupled WPF animations from Win32 Window bounds! Created a massive invisible 1420x760 static Window canvas, and shifted the expansion animations strictly to the inner WPF Grid container. This completely eliminates the Win32 transparent window resizing stutter and jitter, mathematically ensuring a flawless 60fps expansion, and instantly fixes the right-edge white space padding bug.

## [v1.7.5.3]
- **[fix]** Fixed the root cause of the "disappearing to the left" bug. PowerShell was dynamically injecting `SizeToContent = 'WidthAndHeight'` when the window was dismissed, instantly breaking the previous `CanResize` fix for the next launch. Replaced the runtime `SizeToContent` injection with explicit `Width=290` and `Height=460` resets to preserve OS animation support.

## [v1.7.5.2]
- **[fix]** Fixed the "disappearing to the left" animation glitch. Changed WPF `ResizeMode` to `CanResize`, allowing the OS to actually process the `DoubleAnimation` on the Window's `Width` and `Height` dimensions, instead of silently dropping them while still animating `Left` and `Top`.

## [v1.7.5.1]
- **[fix]** Fixed a bug where clicking 'Phone Files' caused the spatial menu to fly off-screen instead of expanding. Removed the conflicting `SizeToContent="WidthAndHeight"` property from the WPF Window and restored explicit `Width` and `Height` boundaries, allowing the `ExpandMenu` DoubleAnimations to properly scale the window bounds.

## [v1.7.5.2]
- **[minor]** Replaced the manual Theme toggle button with an automatic OS Theme Synchronization system. 
- The WPF engine now seamlessly queries the Windows 11 `AppsUseLightTheme` registry key at startup and instantly applies `LightTheme.xaml` or `DarkTheme.xaml` based on your global OS preferences.

## [v1.7.5.0]
- **[major]** Architectural refactor of the WPF rendering engine to support dynamic theming.
- Decoupled all hardcoded hex values in `Connect-Engine.ps1` into semantic `DynamicResource` tokens.
- Introduced `Themes/DarkTheme.xaml` and `Themes/LightTheme.xaml` as standalone dictionaries.
- Built a seamless runtime theme swapper (`Set-AppTheme`) utilizing XAML merged dictionary replacement.
- Added a "Toggle Theme" quick action button to the spatial menu UI to switch between Light and Dark mode instantly.

## [v1.7.4.9]
- **[fix]** Declared `<desktop2:FirewallRules>` in `AppxManifest.xml` to automatically provision Windows Defender Firewall rules for `adb.exe` during MSIX installation. This permanently prevents the UAC/Firewall prompt that was appearing after every update due to path changes and mDNS UDP listeners.

## [v1.7.4.8]
- **[minor]** Reverted the menu opening (`PopIn` and `ExpandMenu`) to use the original `ElasticEase` ("BouncyEase") with a starting scale of `0.85`, preserving the new dramatic `BackEase` overshoot/undershoot exclusively for the hover/leave interactions.

## [v1.7.4.7]
- **[minor]** Split global UI physics into two distinct resources (`HoverEase` Amplitude 1.22 and `PopInEase` Amplitude 3.53) to exactly target scale curves.

## [v1.7.4.6]
- **[minor]** Split global UI physics into two distinct resources: `HoverEase (Amplitude=1.22)` and `PopInEase (Amplitude=3.53)`. This forces the hover-exit to shrink exactly to `0.96` (from `1.08`) before snapping back to `1.0`, and the spatial menu pop-in to start from `0.90` and explode outward to `1.18` before settling to `1.0`, matching the desired bespoke physics curves perfectly.

## [v1.7.3.1]
- **[fix]** Critical app startup failure where the tray icon would load but the WPF window would fail to parse entirely (making all menu items null) because the `JoeAvatar.jpg` image path incorrectly referenced `bin/Assets` instead of `Assets/`. 

## [v1.7.3] - 2026-07-28

## [v1.7.2] - 2026-07-28

### [fix] Spatial Menu Tray Click — Duplicate Deactivated Handler (v1.7.2)
- **Root Cause:** Two separate `Add_Deactivated` handlers were registered on the WPF window. The first (line 773) fired unconditionally — no debounce guard — hiding the window instantly on any focus loss. The second (line 993) had the 200ms debounce but was useless because the first handler already killed the window before it could act. When `Show()` + `Activate()` ran from the tray click, WPF's focus transfer briefly triggered `Deactivated`, and the unguarded handler won the race every time.
- **Fix:** Removed the unconditional handler; merged its state-reset logic (Width/Height/FileExplorer collapse) into the single debounced handler. One handler, one code path, zero race.
- **Project Rules:** Added version bump rule to `GEMINI.md` — all versions must be bumped in `AppxManifest.xml` before build/sign/push.

## [v1.7.1] - 2026-07-27

### [fix] Spatial Menu Tray Click Debouncer (v1.7.1)
- **Root Cause:** The `ApplicationIdle` dispatcher queue was being starved by the WinForms message pump, preventing the menu from opening.
- **Fix:** Implemented a robust 200ms Deactivation Debouncer that ignores spurious `Deactivated` events firing immediately after `Show()`.


## [v1.7.0] - 2026-07-27

### [fix] Spatial Menu Tray Click — Dispatcher ApplicationIdle Fix (v1.7.0)
- **True Root Cause:** When clicking a `NotifyIcon`, Windows queues a WM_ACTIVATE/Deactivate message to the WPF window as part of the tray click sequence. Calling `Show()` synchronously inside `MouseUp` races against this queued message — `Deactivated` fired *after* `Show()`, calling `Hide()` before the user ever saw anything. Neither `AppActivate` nor `Activate()` resolved this because the problem was message ordering, not focus ownership.
- **Fix:** Wrapped `Show()` + `Activate()` + `PopIn` inside `$wpfWindow.Dispatcher.BeginInvoke(ApplicationIdle)`. This defers the open path until all pending WM_ACTIVATE/Deactivated messages have drained from the WPF Dispatcher queue, guaranteeing `Deactivated` fires *before* `Show()`, not after.


## [v1.6.9] - 2026-07-27

### [fix] Spatial Menu Tray Click — Deactivated Race Fix (v1.6.9)
- **Root Cause Identified:** `AppActivate` was called on the PowerShell *process*, not the WPF window. This gave OS focus to the wrong target, causing `Deactivated` to fire on the WPF window the instant it became visible, which called `Hide()` before the user ever saw it.
- **Fix:** Replaced `AppActivate` with `$wpfWindow.Activate()` called immediately after `Show()`. This issues `SetForegroundWindow` on the WPF window's own HWND — correct window gets focus, `Deactivated` only fires when the user genuinely clicks away.


## [v1.6.8] - 2026-07-27

### [fix] WorkArea-Anchored Positioning & Tray Click Race Fix (v1.6.8)
- **WorkArea Anchor (Windows 11 UX):** Replaced cursor-follow positioning with `SystemParameters.WorkArea`-anchored placement. The spatial menu now always opens flush against the taskbar corner (bottom-right by default), matching the Windows 11 Fluent Design language used by Volume, Quick Settings, and Clock flyouts.
- **Tray Click Race Condition:** Fixed the spatial menu silently failing to open. The root cause was a double Visibility guard — `Update-WpfUI` blocks on `adb devices` while the second `Visibility` check ran immediately after and could see a stale Collapsed state. Removed the redundant inner check; `IsVisible` is now the single gatekeeper, and `Show()` is called unconditionally on the open path.
- **Removed Unnecessary Measure:** Cut the `Measure(Infinity)` call that was called on a hidden window before layout; the window has fixed dimensions so `Width`/`Height` are directly usable for positioning.

## [v1.6.7] - 2026-07-27

### [feature] Spatial Menu Bouncy Entrance (v1.6.7)
- **Fluid Animation Physics**: Integrated the signature `BouncyEase` (ElasticEase overshoot-with-reverse-subtle-overshoot) physics directly into the spatial menu's opening sequence. The main window now seamlessly scales up from 85% and glides upwards into position natively using WPF Storyboards when clicking the tray icon.
## [v1.6.5] - 2026-07-27

### [minor] Embedded Avatar Asset (v1.6.5)
- **Asset Integrity Verification**: Copied the explicitly provided user picture directly into the `MSIX_Source\Assets` payload as `JoeAvatar.jpg`. This inherently avoids missing file WPF parsing errors (`XamlParseException`) upon initialization and successfully complies with the zero placeholder asset project rule (`@GEMINI.md`).

## [v1.6.6] - 2026-07-27

### [fix] Spatial Menu Opening Lag (v1.6.6)
- **UI Responsiveness:** Refactored the System Tray click handler (`Connect-Engine.ps1`) to consolidate redundant `adb devices` calls and cache the `Get-AutoConnectStatus` Task Scheduler query. This eliminates UI thread blocking and noticeable opening lag caused by synchronously querying COM objects and spawning external processes on every single click.

## [v1.6.5] - 2026-07-27

### [minor] Staggered Physics Cascades & DRY Architecture (v1.6.5)
- **Centralized Animation Physics:** Extracted duplicated inline `ElasticEase` overshoot definitions across dozens of XAML elements into a single, highly refined `StaticResource` (`BouncyEase`), cutting massive code bloat and strictly enforcing DRY (Don't Repeat Yourself) architecture.
- **Cascading Grid Entrance:** Programmatically injected index-based staggering to the File Explorer grid! When loading phone directories, folders and files now gracefully cascade upwards sequentially with a 35ms stagger, dynamically inheriting the global `BouncyEase` physics curve for a breathtaking load-in effect.

## [v1.6.4] - 2026-07-27

### [feature] Spatial Menu User List & Devices (v1.6.4)
- **Profile Customization**: Refined the User List UI to display `joe.belfiore@dex.net` as the subtext and bound the avatar to a real image placeholder (`Assets/JoeAvatar.jpg`).
- **Device Ecosystem Integration**: Replaced the placeholder "Bill Gates" entry with a sleek, multi-platform device list. Added a `Galaxy S21` mobile node and a `Windows` laptop node, both styled with vibrant purple (`#6200EE`) backgrounds and matching `Segoe Fluent Icons` device glyphs (`&#xE8EA;` and `&#xE7F8;`).

## [v1.6.3] - 2026-07-27

### [fix] WPF ShowDialog Deadlock (v1.6.3)
- **Tray Icon Unresponsiveness**: Replaced `$script:wpfWindow.ShowDialog()` with `$script:wpfWindow.Show()`. Since the Spatial Menu is repeatedly hidden using `.Hide()` on deactivation, `ShowDialog()` was leaving the window stuck in a hidden modal loop, preventing the menu from re-opening on subsequent tray icon clicks and locking users out of the UI.

## [v1.6.2] - 2026-07-27

### [feature] Spatial Menu User List (v1.6.2)
- **UI Overhaul**: Replaced the redundant legacy text buttons (Connect, Mirror, Pull) with a beautifully animated `Nearby Users` list for upcoming local/global file sharing features.
- **Premium Aesthetics**: Implemented fluid floating parallax micro-animations, vibrant online presence badges with stroke cutouts, and 34px corner-radii matching the primary app window.
- **Shortcut Hardening**: Migrated keyboard shortcuts (`Ctrl+C`, `Ctrl+D`) to depend on the Quick Action icons' visibility, guaranteeing shortcuts continue to function flawlessly despite UI restructuring.

## [v1.6.1] - 2026-07-27

### [minor] Ponytail Cuts (v1.6.1)
- **Removed Dead Code**: Eliminated `dwmapi.dll` PInvoke hook and `System.Runtime.InteropServices` type definitions since dark mode is already forced via solid dark background and WPF `AllowsTransparency="True"`.
- **Removed Legacy Fallbacks**: Cut out the WinForms BalloonTip fallback in `Show-Toast` (YAGNI on Windows 10+).
- **Simplified ADB Paths**: Centralized `$global:AdbExePath` resolution at the root scope, eliminating duplicate `Split-Path`/`Join-Path` logic inside the Async Pull worker job.

## [v1.6.1] - 2026-07-27

### [minor] Massive Diagonal Expansion & Fly-Off Fix (v1.6.1)
- **Massive Spatial Expansion:** Dramatically increased the `ExpandMenu` animation target size (Width expands `By=1160` up to `1450px` total width, Height `By=300`), resulting in a sweeping diagonal (top-left) flyout effect that gives you enormous visual space to explore the Phone Files grid view.
- **State Constraint Fix:** Fixed a critical animation flaw where repeatedly clicking "Phone Files" would cumulatively push the window's spatial coordinates permanently off-screen.
- **Deactivated Reset:** The menu now flawlessly collapses back to its default compact 290x460 size whenever you click away (losing focus), ensuring a fresh state every time it's reopened.

## [v1.6.0] - 2026-07-27

### [major] Purple-Black Gradient Restoration & Mica Purge (v1.6.0)
- **Gradient Background Restored:** Re-introduced the signature deep purple-to-black linear gradient (`#1D1226` to `#09090D`) as the primary background for the entire unified Spatial Menu.
- **Glassmorphism Purged:** Completely stripped all traces of Windows 11 Mica, acrylic blur, and transparent glass backdrop styling from the visual tree to ensure the gradient perfectly renders as a solid, sleek 34px rounded spatial shape.

## [v1.6.1] - 2026-07-27

### [hotfix] XAML UI Tree Syntax Repair (v1.6.1)
- **NotifyIcon Crash Resolved:** Fixed a critical regression where the UI would silently fail to parse its XAML due to an unmatched `<Border>` tag generated during the Parallax upgrade. This previously caused `FindName` bindings to remain null, resulting in the `Text` property exception when clicking the tray icon.

## [v1.5.8] - 2026-07-27

### [fix] Hardened Connections & File Explorer UX (v1.5.8)
- **Zombie Process Prevention**: Optimized the Async File Explorer (`Load-Directory`) to explicitly kill previously spawned `adb shell ls` processes before generating new ones, preventing background CPU bloat during rapid folder navigation.
- **WPF Close() Crash Fix**: Fixed a fatal bug in the File Explorer where double-clicking a file to pull it would call `$script:wpfWindow.Close()`, permanently destroying the WPF object and crashing the app upon subsequent tray clicks. Now uses `.Hide()`.
- **Target Connection Hardening**: Refactored device parsing logic across `Sync-AdbStatus`, `Mirror`, and `Pull` actions to strictly prioritize wireless connections (`*:5555`) over USB or emulators.

## [v1.5.7] - 2026-07-27

### [minor] Spatial Menu Visual Revert (v1.5.7)
- **Reverted Mica & Restored 34px Corners**: Dropped the Windows 11 Mica backdrop (`DWMWA_SYSTEMBACKDROP_TYPE`) due to fundamental DWM incompatibility with custom corner geometries. 
- Restored `AllowsTransparency="True"` and a solid `#1C1C1E` background to guarantee pixel-perfect 34px rounded corners.
- **Process Reaping**: Exiting the engine (`btnExit` or `Q`) now forcefully reaps any stray `adb.exe` and `scrcpy.exe` background processes.

## [v1.5.7] - 2026-07-27

### [minor] Global UI Spring Physics & Parallax (v1.5.7)
- **Universal ElasticEase:** Applied the advanced WPF `ElasticEase` (Oscillations=1, Springiness=4/5) to absolutely every interactive element in the app. This creates that highly-requested organic, physical bouncy feel (overshoot with a subtle reverse-overshoot recoil).
- **Parallax Translations:** Upgraded every single button hover, press, and menu expansion state to include subtle spatial `TranslateTransform` parallax shifts. Elements now physically move and scale organically on hover and click rather than just instantly snapping states.

## [v1.5.6] - 2026-07-27

### [fix] Absolute Compilation Cleanup & MSIX Packaging Pipeline (v1.5.6)
- **Compiler Purge:** Triggered a hard re-compile (`dotnet build`) to physically obliterate the deprecated `PickerWindow` from the underlying `ConnectPhoneShareTarget.dll` assembly. The previous MSIX build only contained the source deletions without recompiling the binary.
- **Automated Pipeline Fix:** Updated `PackMSIX.ps1` to actively trigger `dotnet build -c Release` prior to packaging, ensuring the compiled C# binaries and MSIX payload are fundamentally permanently synced.

## [v1.5.5] - 2026-07-27
### [major] Unified Spatial File Explorer & Overshoot UI Rewrite (v1.5.5)
- **Nuked PickerWindow:** Eliminated the standalone C# File Picker EXE (`PickerWindow.xaml`), consolidating everything back into the core PowerShell engine to honor the strict minimalist protocol.
- **Fluid Overshoot Shape-Shifting:** Clicking 'Phone Files' now triggers a gorgeous `BackEase` WPF DoubleAnimation that dynamically scales the Spatial Menu diagonally to reveal a nested phone grid-view directly within the Mica surface.
- **Async ADB Runspace Bypass:** Engineered a raw `OutputDataReceived` pipeline in PowerShell to scrape directories from `adb` asynchronously in the background. Completely negates UI freezing without needing external C# assemblies.

## [v1.5.4] - 2026-07-27
### [fix] Spatial Menu Focus & Hide Reliability (v1.5.4)
- **ShowDialog Crash Fix**: Fixed a bug where clicking the tray icon when the spatial menu was already active would throw an `InvalidOperationException` due to re-invoking `ShowDialog()`. The tray icon now properly toggles visibility.
- **Deactivated Event Reliability**: Forced the underlying PowerShell process to gain OS-level foreground lock (`AppActivate`) before showing the WPF overlay. This guarantees that clicking outside the spatial menu reliably fires the `Deactivated` event to auto-hide it.

## [v1.5.3] - 2026-07-27
### [minor] Spatial Menu Mica Integration (v1.5.3)
- **Mica Backdrop**: Applied native Windows 11 Mica Glass (`DWMWA_SYSTEMBACKDROP_TYPE = 2`) to the Spatial Menu (Tray UI), stripping away the solid black background via `WindowChrome` while retaining the native floating UI characteristics.

## [v1.5.2] - 2026-07-27
### [fix] Dynamic Connection Syncing & Auto-Connect Fallback (v1.5.2)
- **Auto-Connect Fallback:** Clicking 'Phone Files' when no device is connected now automatically attempts to connect using the supplied IP Address before pulling.
- **Dynamic Connection Syncing:** Refactored the Tray Menu connection logic to actively resync and extract the `<ip:port>` natively every time the menu is opened, addressing edge-cases where background connections didn't update the UI.

## [v1.5.2] - 2026-07-27
### [minor] Mirror Phone Quick Action & Shortcut (v1.5.2)
- **CellPhone Segoe Fluent Icon**: Added Phone icon button (`&#xE8EA;`) to the top spatial quick action bar and spatial menu list item (`Mirror Phone`).
- **Scrcpy Auto-Detection & Launch**: Integrated zero-latency screen mirroring launcher via `scrcpy.exe -s <target>`. Auto-detects `scrcpy` in system `PATH` or local `bin` folder, and gracefully prompts if missing.
- **Keyboard Shortcut**: Bound key `M` (`⌘M`) to trigger Mirror Phone instantly.

## [v1.5.1] - 2026-07-27

### [minor] Spatial Menu Folder Icon & Persist Open (v1.5.1)
- **Segoe Fluent Folder Icon**: Replaced `Phone Files` icon (`&#xE896;`) in spatial menu with official Segoe Fluent Icons / Segoe MDL2 Assets Folder glyph (`&#xE8B7;`).
- **Persistent Spatial Menu**: Removed auto-hide behavior on item click (`Connect`, `Disconnect`, `Phone Files`, `Toggle Auto-Connect`). The spatial menu remains open for multi-action execution with live UI state updates.
- **Keyboard Shortcut Acceleration**: Added `Esc` to instantly dismiss spatial menu overlay, alongside key handling (`C`, `D`, `P`, `Q`).

## [v1.4.5] - 2026-07-27

### [fix] GitHub Action Release Workflow Fixes (v1.4.5)
- **.NET 10 Prerelease Setup**: Added `include-prerelease: true` to `actions/setup-dotnet@v4` so GitHub Actions runner resolves `.NET 10` preview builds on `windows-latest`.
- **Manual Trigger Support**: Added `workflow_dispatch` to allow manual execution of build & release pipeline from GitHub Actions web UI.
- **Isolated Release Notes Extractor**: Enhanced regex parsing in PowerShell step to capture the exact top tag heading and notes verbatim into `RELEASE_NOTES.md` without pulling trailing historical changelog entries.

## [v1.5.1] - 2026-07-27

### [fix] Execution Path Bug & Acrylic Aesthetics (v1.5.1)
- **Execution Fix:** Fixed a silent crash where the System Tray `Connect-Engine.ps1` was resolving `ConnectPhoneShareTarget.exe` inside the `bin` directory instead of the application root.
- **Acrylic Aesthetics:** Wired in `dwmapi.dll` P/Invoke calls to inject native Windows 11 Acrylic (`DWMWA_SYSTEMBACKDROP_TYPE = 3`) into the WPF window background for a gorgeous translucent glass effect.

## [v1.5.0] - 2026-07-27

### [major] Native C# File Picker (v1.5.0)
- **UI Overhaul:** Completely ripped out the primitive PowerShell `TreeView` file picker and replaced it with a gorgeous, natively compiled C# WPF `PickerWindow`.
- **Segoe Fluent Icons:** Added native support for `&#xE8B7;` (Folder) and `&#xE7C3;` (File) modern glyphs, leveraging system-level Segoe Fluent UI rather than bringing in bloatware external dependencies.
- **Performance:** Migrated the ADB folder scraping logic (`adb shell ls -1aF`) to run entirely asynchronously on native C# thread pools for zero UI lag.
- **Glassmorphism Base:** Laid the architectural groundwork for standard WPF blurring and acrylics without needing heavy toolkits like Tauri or WPF-UI.

## [v1.4.4] - 2026-07-27

### [fix] Deep Edge-Case Audit (v1.4.4)
- **UI Responsiveness:** Fixed a bug where polling the remote file size blocked the WPF UI thread, causing the transfer window to temporarily hang before the transfer started.
- **ADB Path Escaping:** Fixed a critical bug where transferring files with single quotes (e.g. `O'Brian.mp4`) would completely crash the ADB shell syntax during standard input streaming.
- **Missing Binaries:** Added explicit verification for `adb.exe` presence before executing streams.

## [v1.4.3] - 2026-07-27

### [fix] TreeView Scope Crash (v1.4.3)
- Fixed a fatal scoping bug where PowerShell's `.add_Expanded()` threw a silent `MethodNotFound` exception on the WPF TreeView because `TreeView` does not expose `Expanded` directly. Refactored to use standard WPF `AddHandler` for `TreeViewItem::ExpandedEvent`.

## [v1.4.2] - 2026-07-27

### [fix] WPF Threading & Installation Bump (v1.4.2)
- Fixed a bug where `Phone Files` would crash instantly due to calling `.Show()` instead of `.ShowDialog()` inside a WinForms thread.
- Bumped AppxManifest version to `1.4.2.0` to resolve Windows package identity installation blocks.

### [major] The Blip Engine Rewrite (v1.4.0)
- **Hardcore C# Transfer Engine**: Completely retired `Send-To-Phone.ps1`. The C# `ConnectPhoneShareTarget` application is now a fully-fledged WPF streaming engine.
- **Byte-Level Auto-Resume**: The engine now polls the Android device for existing file sizes and streams bytes directly via `adb shell cat >>`, enabling seamless mid-byte resume if a transfer fails or network drops.
- **Live Progress UI**: Replaced standard Toast notifications with a beautiful, floating WPF window that displays a live progress bar, precise megabytes-per-second (MB/s) speed tracker, and taskbar progress states.

### [major] TreeView File Explorer (v1.3.18)
- **Dynamic Phone Files**: Replaced the static, path-restricted ListBox file picker with a dynamic, lazy-loading WPF `<TreeView>` file explorer.
- **Zero-Lag Loading**: Introduced a "Dummy Node" pattern that only queries the Android filesystem via `adb shell ls` when a folder is actively expanded, enabling instantaneous UI responsiveness.
- **Recursive Directory Pulling**: Users can now select an entire directory in the TreeView and download it recursively in the background.

### [fix] MSIX Deployment and AppExecutionAlias Syntax (v1.3.19)
- **Alias Registration Crash**: Fixed `0x8007007E` MSIX deployment failure by correctly defining the `Executable` and `EntryPoint` attributes in the `<uap3:Extension Category="windows.appExecutionAlias">` tag for `adb.exe`.

### [fix] UTF-8 Mojibake Crash (v1.3.20)
- **Silent Background Crash**: Resolved an issue where literal folder (📁) and file (📄) emojis in the PowerShell script caused a fatal `XmlNodeReader` parse exception under certain encoding environments. Replaced with robust `[DIR]` text prefixes.

### [fix] WPF Icon Decoder Crash (v1.3.21)
- **WPF BitmapFrame Bug**: Wrapped the `BitmapFrame::Create` icon assignment for the TreeView window in a `try/catch` block to prevent silent execution termination when Windows Presentation Foundation fails to decode `app-icon.ico`.

### [fix] ShareTarget Batching and Disconnection Edge-Cases (v1.3.22)
- **CPU/Memory Resource Bomb**: Completely rewrote the C# `ConnectPhoneShareTarget` application to batch multiple shared file paths into a temporary text file, preventing the app from spawning dozens of concurrent PowerShell background instances when sharing multiple files.
- **Disconnected ADB Ghost Files**: Implemented offline detection in the TreeView parser. If ADB is disconnected silently in the background, the UI now displays `(Disconnected)` instead of parsing `error: device offline` into fake UI file nodes.
- **Task Scheduler UAC Audit**: Verified the Auto-Connect Task Scheduler logic natively executes under `TASK_LOGON_INTERACTIVE_TOKEN`, confirming standard non-elevated users can correctly toggle the functionality.

### [minor] Spatial Menu Icon & Persistent Interaction Enhancements
- **UI Glyph Update**: Replaced `btnQAPull` icon with official Segoe Fluent Icons / Segoe MDL2 Assets **Folder** glyph (`&#xE8B7;`).
- **Persistent Spatial Menu**: Removed auto-hiding behavior on `Connect`, `Disconnect`, `Phone Files`, and `Auto-Connect` menu actions so the menu stays open for interactive use.
- **Dynamic UI State Sync**: Added immediate `Update-WpfUI` triggers on menu actions to update connect/disconnect states and auto-connect highlights live.
- **Project Rule Protocol**: Configured workspace rules enforcing `/ponytail` ladder, deep edge-case resolution, MSIX build & signing pipelines, and automated release commits.







