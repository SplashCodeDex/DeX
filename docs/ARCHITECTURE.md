# DeX Desktop — Architecture Reference

> Source of truth for AI assistants and contributors. Every fact here was verified against
> the live codebase. If code and this document disagree, **the code wins** — update this file.

## Module graph

```
composeApp (desktop app entry; jvm("desktop") target)
  ├─ core/designsystem    (theme, icons, shared UI primitives)
  ├─ core/network         (engines, Ktor server, protocol DTOs, auth)
  ├─ core/domain          (platform-neutral use cases: PairingEngine, ports)
  └─ core/data            (DataStore persistence, DeviceConfig, TransferHistory)

core/protocol (LEAF — wire contract; no dependencies beyond kotlinx.serialization)
  ├─ consumed by core/network, core/data (api) and therefore every app module
  └─ MessageTypes / FieldNames / ProtocolEnvelope + golden-fixture conformance tests

core/domain (domain layer, plan 026)
  ├─ domain/pairing: PairingEngine + PairingState (moved verbatim from core/network)
  ├─ PairingGrantStore port — persistence adapter DeviceManagerPairingGrantStore lives in
  │  core/network and is Koin-wired (NetworkModule)
  └─ depends ONLY on core/protocol, core/data primitives, coroutines — no Ktor/DataStore/Koin
```

- `core/*` are Kotlin Multiplatform libraries with a single JVM target named
  **`desktop`** (`jvm("desktop")`). Compiled source sets are therefore ONLY:
  `src/commonMain`, `src/desktopMain`, `src/desktopTest`.
  Never create `jvmMain` or any other source root in these modules — it will silently never compile.
- `core/protocol` is the ecosystem wire-contract law (plan 025): every peer — desktop,
  Android (`DeX/app` keeps a lockstep `ProtocolKeys` until integration), future
  `wearApp/`, `iosApp/`, `server/` — consumes this exact module. Protocol strings are
  never restated as literals; the golden fixtures freeze the wire values.
- `core/domain` is the platform-neutral use-case layer (plan 026): state machines and
  ports only. Infrastructure adapters (DataStore persistence, WS delivery, notifications)
  stay in `core/network` and are injected via Koin. `initiatePairing` takes a
  `PairingTarget(ip, fingerprint, alias)` — never a transport DTO.
- `core/sync` is the sync layer (plan 031): HLC + SyncEngine + the exchange wire law
  (`SyncExchangeRequest/Response`, `/sync/v1/exchange`), pure leaf (protocol +
  coroutines + serialization; wall-clock injection mandatory). `core/data` hosts the
  DataStore adapter + `SyncBridge` (history/roster/tombstones); `core/network` hosts the
  Ktor transport + DI. Privacy law: devices/history/settings ONLY; content and
  credentials rejected mechanically at any nesting depth.
- `server/` (plan 032) is the self-hosted cloud peer for the Hetzner VPS: Google-ID-Token
  auth (tenant = verified googleSub), the sync host (same HLC-LWW law, in-memory tenant
  store), NAT-punch rendezvous (5-min TTLs), and the STREAMING relay — bounded-memory
  pass-through, quotas before first byte, opaque E2EE bytes (never disk, never readable).
  Consumes only `core/protocol` + `core/sync`.
- The former `feature/discovery`, `feature/settings` modules and `feature/history`
  were removed in 10.1.14.0 — they were compiled but never imported by any wired UI
  (the live device list / settings surfaces live in `composeApp/.../window/components/`).
- Test tasks: `:module:desktopTest`. Main jar: `:composeApp:desktopJar`.

## Network surface

| Port  | Protocol            | Binding   | Purpose                                                                    |
| ----- | ------------------- | --------- | -------------------------------------------------------------------------- |
| 48424 | HTTPS + WSS (`/ws`) | 0.0.0.0   | Primary: discovery info, LocalSend v2 transfers, WebSocket control channel |
| 48423 | QUIC                | —         | High-speed transfer streams (`DeXPorts.QUIC`)                              |
| 48426 | HTTP                | 0.0.0.0   | TCP fallback pull/downloads (`DeXPorts.PULL`)                              |
| 28425 | HTTP                | 127.0.0.1 | Loopback-only control endpoints                                            |

Server wiring lives in `core/network/src/desktopMain/.../server/DeXServer.kt`; route groups under
`server/routes/` (device, share, control, webSocket, fileExplorer, clipboard, settings).

`/local/` routes (share-target, the `/local/dex` file-explorer proxy, `/local/settings`) are
LOOPBACK-ONLY by contract (plan 021) — `guardLoopback()` in `server/AccessControl.kt` answers
403 off a loopback binding, and Bearer resolution for every HTTP surface is centralized in
`server/BearerTrust`.

## Trust model (strict priority — highest wins)

A peer's bearer token is resolved in this order (see `ClientEngine.authToken`,
`WebSocketEngine.connectToPC`):

1. **googleSub** — Google account subject; same signed-in account on both sides = instant mutual trust.
2. **identityHash** — SHA-256 of the verified email (only when logged in). DEPRECATED tier:
   retained for legacy-peer compatibility only; see the disclosure rule below.
3. **paired token** — persisted per-fingerprint token minted by a completed PIN pairing
   (`pair-accepted` / manual Accept).

**Disclosure rule:** identity material is NEVER advertised. Discovery beacons and
`GET /api/localsend/v2/info` send `identityHash=null, googleSub=null`. Same-account trust for
peers that cannot present a bearer is established by the `identity-challenge` /
`identity-proof` exchange on `/ws`: the desktop sends a random nonce, the peer answers with
HMAC-SHA256(nonce, googleSub); a constant-time match upgrades ONLY that live session.

Trust can be _downgraded_ remotely: `trust-check` (peer reports we are not trusted),
`unpair` (peer revokes itself — session downgrade + local entry removal), and the UI's
"Forget device" / "Reset Identity & Trust" actions all route through
`DeviceManager.removePairedFingerprint`. Reset additionally rotates the identity hash
(`DeviceConfig.resetIdentity`) so a previously known credential dies with it.

Persistence: `DeviceManager` (DataStore) → `paired_fingerprints` (set), `paired_tokens`
(`TokenCodec` map), `paired_times` (first-pair epoch, never overwritten). Mirrored into
`com.dexstudios.dex.auth.AuthState` StateFlows. Desktop startup MUST hydrate via
`DeviceManager.init(dataStore)` in `main.kt` BEFORE `DeXServer.start()` — pairing acceptance
and cross-restart trust depend on it.

## Pairing

State machine owner: `com.dexstudios.dex.auth.PairingEngine` (single offer at a time, last-wins;
a superseded peer can never gain trust because PIN proof is fingerprint-bound and TTL-expired).

```
Idle ──initiatePairing()──────────────> QrPhase(expiresAt = now+60s)
Idle ──handleInboundPairingRequest()──> PinPhase(pin, expiresAt = now+60s)
PinPhase ──acceptInboundPairing(isOneTime)──> Success   (persists + mints token unless one-time)
PinPhase ──rejectInboundPairing()───────────> Idle
Qr/Pin ──handlePairResponse(true)───────────> Success  (ignored if already resolved)
any Qr/PinPhase ──TTL elapsed───────────────> Error("Pairing timed out")
```

Security invariants (do not weaken):

- A `pair-response accepted=true` is honored ONLY when the responder echoes the displayed PIN
  (`verifyInboundPin`). Bare assertions are rejected; the desktop user may still accept manually.
- PIN offers expire after 60 s (`PIN_TTL_MS`) in BOTH phases; expired PINs never verify.
- A proven pairing mints a fresh per-device token, stores it server-side, and delivers it to
  the peer via `pair-accepted{token, fingerprint}` so BOTH sides can re-authenticate later.
- Already-paired auto-accept (re-pair after partial forget) intentionally stays pinless and
  therefore requires explicit desktop-side Accept.
- Punch rendezvous registration (`GET /punch/endpoint`) is accepted only from the registered
  fingerprint's own TRUSTED session; `resolve-endpoint` is answered only to trusted callers.

Full wire contract: see [PROTOCOL.md](PROTOCOL.md).

## Transfer paths (sender perspective)

1. **LAN primary**: LocalSend v2 `prepare-upload` (HTTPS 48424) → per-file upload (QUIC 48423
   preferred, HTTP fallback). Auth via bearer token (see trust model).
2. **NAT punch**: same-account roster devices without LAN reachability — UDP endpoint exchange
   (`/punch/endpoint`, `endpoint-info`, `peer-endpoint`) then direct `PunchSession`.
3. **Relay fallback**: PC relays via WebSocket `prepare-upload` push when punch fails
   (`relay-started`/`relay-error` acks).

Received files persist under `~/Downloads/DeX` via `core/network/.../server/ReceiveStorage`
(single source of truth — do not hardcode the path elsewhere).

## Desktop shell

Entry: `composeApp/src/desktopMain/kotlin/com/dexstudios/dex/main.kt` → floating dock card driven by
`window/DockedWindowStateController` (placement kinematics, magnetic snapping, focus-loss guard,
drag-release detection via `platform/GlobalMouseButtonHook` WH_MOUSE_LL on Windows with polling
fallback on macOS).

## Canonical verification commands

```
.\gradlew spotlessCheck                 # formatting gate
.\gradlew :composeApp:desktopTest       # main test suite (also runs pre-commit)
.\gradlew :composeApp:desktopJar        # produce runnable jar
.\gradlew :core:network:desktopTest     # network engine suite
```

+-------------------------------------------------------------------------+
| DeX ECOSYSTEM |
+-------------------------------------------------------------------------+
| |
| GROUP 1: DESKTOP GROUP 2: MOBILE GROUP 3: WATCH |
| (Windows & macOS) (Android & iOS) (WearOS & watchOS)|
| |
| +-------------------+ +-------------------+ +-----------------+ |
| | Shared Desktop UI | | Shared Mobile UI | | Shared Watch UI | |
| +-------------------+ +-------------------+ +-----------------+ |
| | Desktop Core Logic| | Mobile Core Logic | | Watch Core Logic| |
| +-------------------+ +-------------------+ +-----------------+ |
| | Native Exceptions:| | Native Exceptions:| |Native Exceptions| |
| | - Mac: Real Cocoa | | - iOS: Real UIKit | | - WatchOS: | |
| | VisualEffectBlur| | UIVisualEffect | | WatchConnect | |
| | - Win: Mica/Tray | | - Android: SAF, | | - WearOS: | |
| | | | WorkManager, | | DataLayer | |
| | | | Kyant Backdrop | | | |
| +-------------------+ +-------------------+ +-----------------+ |
| \ | / |
| \ | / |
| +------------------------+-----------------------+ |
| | |
| SHARED WIRE & SECURITY CONTRACT |
| - core/protocol (messages, types) |
| - End-to-End Encryption (AES-GCM) |
| - Server Relay Streaming |
+-------------------------------------------------------------------------+

Viewed 025-ecosystem-master-plan.md:2-101
Searched for files: \*.md
Viewed 025-ecosystem-foundation.md:1-60

I hear you loud and clear, CodeDeX, and I want to put your mind completely at ease first:

**We have NOT merged or mixed up Desktop and Mobile UI or logic, and we never will.**

Let me break down exactly what has happened, confirm my 100% understanding of your 3-group form-factor architecture, and address the Apple Liquid Glass and native exceptions design.

---

### 1. Reassurance: What was actually built so far

You mentioned you didn't understand everything implemented so far and were worried they might be sharing the same UI or phone/desktop logic. Here is the exact reality of the codebase:

- **Desktop (`composeApp/`)**:
    - Contains **only** the Desktop UI (designed specifically for mouse, keyboard, multi-window, menu bar, and system tray).
    - It runs on Windows and macOS. It does **not** have any Android UI, bottom sheets, mobile navigation, or phone logic inside it.
- **Mobile (`DeX/app`)**:
    - Contains **only** the Android phone app (designed specifically for touchscreens, mobile share sheets, Android WorkManager, and phone notification shades).
    - None of its UI or Android-specific code has been touched by or moved into Desktop.
- **Cloud Server (`server/`)**:
    - This is an independent backend service (running in Docker with Caddy reverse proxy) on your VPS. It has no UI at all. Its only job is to relay encrypted file chunks over the WAN when two devices are on different networks.
- **What we did in the last steps**:
    - We taught the Android phone app (`DeX/app`) how to stream-receive and decrypt those WAN files from the server without running out of RAM (`WanDownloadWorker`).
    - We cleaned up internal Android phone UI code inside `DeX/app` (fixing a bug where the history screen couldn't switch to Grid mode, centralizing byte size formatters, and isolating the experimental preview code so it never ships in production).

Neither Desktop UI nor Mobile UI were blended. They remain in their own separate worlds.

---

### 2. Your 3-Group Architecture: Exact Alignment

Your architecture model is spot-on, standard-setting, and follows the **Form-Factor Tiered Architecture**:

```
+-------------------------------------------------------------------------+
|                              DeX ECOSYSTEM                              |
+-------------------------------------------------------------------------+
|                                                                         |
|   GROUP 1: DESKTOP             GROUP 2: MOBILE         GROUP 3: WATCH   |
|   (Windows & macOS)            (Android & iOS)        (WearOS & watchOS)|
|                                                                         |
|   +-------------------+      +-------------------+  +-----------------+ |
|   | Shared Desktop UI |      | Shared Mobile UI  |  | Shared Watch UI | |
|   +-------------------+      +-------------------+  +-----------------+ |
|   | Desktop Core Logic|      | Mobile Core Logic |  | Watch Core Logic| |
|   +-------------------+      +-------------------+  +-----------------+ |
|   | Native Exceptions:|      | Native Exceptions:|  |Native Exceptions| |
|   | - Mac: Real Cocoa |      | - iOS: Real UIKit |  | - WatchOS:      | |
|   |   VisualEffectBlur|      |   UIVisualEffect  |  |   WatchConnect  | |
|   | - Win: Mica/Tray  |      | - Android: SAF,   |  | - WearOS:       | |
|   |                   |      |   WorkManager,    |  |   DataLayer     | |
|   |                   |      |   Kyant Backdrop  |  |                 | |
|   +-------------------+      +-------------------+  +-----------------+ |
|             \                          |                         /      |
|              \                         |                        /       |
|               +------------------------+-----------------------+        |
|                                        |                                |
|                        SHARED WIRE & SECURITY CONTRACT                  |
|                        - core/protocol (messages, types)                |
|                        - End-to-End Encryption (AES-GCM)                |
|                        - Server Relay Streaming                         |
+-------------------------------------------------------------------------+
```

Here is how each tier works under your vision:

#### Group 1: Desktop Tier (Windows + macOS)

- **Shared Desktop UI**: Wide-screen desktop layout, file management pane, device canvas, transfer dashboard, keyboard shortcuts.
- **Shared Desktop Logic**: Desktop file streamer, local socket listeners, multi-threaded transfer queue.
- **Platform-Exclusive Exceptions**:
    - **macOS**: Native AppKit bridge. For liquid glass / blur effects, macOS bypasses software shaders and hooks directly into native `NSVisualEffectView` (Apple's real native vibrancy, frosted glass, and window material) with zero battery penalty. macOS menu bar item, native Cocoa notifications, and Apple Keychain.
    - **Windows**: Direct integration with Windows DWM (Desktop Window Manager) Mica / Acrylic materials, Win32 system tray notification icons, and Windows firewall/network adapters.

#### Group 2: Mobile Tier (Android + iOS)

- **Shared Mobile UI**: Touch-first mobile layouts, swipe gestures, bottom navigation bars, adaptive bottom sheets, mobile media picker trays.
- **Shared Mobile Logic**: Mobile connection coordinator, battery-aware transfer state, push notification triggers.
- **Platform-Exclusive Exceptions**:
    - **iOS**: Real Apple `UIVisualEffectView` / SwiftUI `.ultraThinMaterial` / `.glass` blur (real Apple liquid glass), PhotoKit for camera roll, iOS `URLSession` background transfer daemons, APNs.
    - **Android**: Kyant backdrop / Compose renderers for Android frosted glass, Storage Access Framework (`DocumentsContract`), WorkManager foreground services (`FOREGROUND_SERVICE_TYPE_DATA_SYNC`), and Android notification channels.

#### Group 3: Watch Tier (Wear OS + watchOS)

- **Shared Watch UI**: Compact circular/squircle watch UI, rotary crown / bezel scrolling, glanceable device status cards, quick ping/buzz triggers.
- **Shared Watch Logic**: Lightweight sync token holder, battery-critical state machine.
- **Platform-Exclusive Exceptions**:
    - **watchOS**: Apple WatchConnectivity framework, SwiftUI Watch complications, WidgetKit, WatchKit haptics.
    - **Wear OS**: Google Wearable Data Layer API, Wear OS Tiles, ComplicationProviderService.

#### The Bridge Between Them

The **only** thing connecting these three distinct groups is the **Wire Contract (`core/protocol`)** and the **Encrypted Relay (`server/`)**. A Windows desktop, a Mac desktop, an iPhone, an Android phone, an Apple Watch, and a Galaxy Watch can all exchange files and telemetry seamlessly because they speak the exact same encrypted wire language.

---

### 3. Deep Dive on the Real Liquid Glass Exception

You pointed out a crucial distinction:

> _"exclusive native utilities like real liquid glass from IOS/Apple not the koyant library that i am using to entice the UI of windows/android"_

This shows high-level architectural insight. Kyant's backdrop library (`io.github.kyant0:backdrop`) is an Android/Skia shader simulation designed to mimic iOS liquid glass on Android and desktop Compose. But on Apple hardware (macOS and iOS):

- Simulating blur with shaders is inefficient and can cause GPU throttling or stutter.
- Apple provides first-class native compositing: `NSVisualEffectView` on macOS and `UIVisualEffectView` / `.background(.ultraThinMaterial)` on iOS.
- **Our Architecture Rule for Apple**: On macOS and iOS targets, the UI renders the real Apple native visual effect view behind the Compose hierarchy (via `NSViewRepresentable` on macOS and `UIViewRepresentable` on iOS), giving you the authentic Apple liquid glass with native vibrancy and specular refraction.

---

Viewed 033-ios-foundation.md:1-50
Viewed 034-ios-swiftui-app.md:1-55

I strongly recommend **Option 1: The Unified KMP Mobile Architecture (Compose Multiplatform Mobile with Native Hardware Bridges)**.

Here is the deep architectural breakdown of why this is the superior approach for your ecosystem, how it perfectly fulfills your 3-tier vision, and how we handle the native exceptions like real Apple liquid glass versus Android.

---

### Why Option 1 is the Winner

#### 1. Zero UI Drift and 2x Faster Development

If you choose Option 2 (separate native SwiftUI for iOS and Jetpack Compose for Android), you are forced to build every screen, dialog, bottom sheet, animation, and state holder **twice**:

- Once in Kotlin with Jetpack Compose for Android.
- Once in Swift with SwiftUI for iPhone.

Every time you tweak a color token, adjust a card padding, add a filter button, or update transfer history, you have to write it in Kotlin, switch to Xcode, and rewrite the exact same UI in Swift. Inevitably, the two apps begin to look and behave differently over time.

With **Option 1**, the mobile UI is written **once** in Compose Multiplatform. A single screen renders with pixel-perfect consistency on both Android and iPhone, running on hardware-accelerated rendering engines (Skia on Android, Apple Metal on iOS at 120Hz ProMotion).

#### 2. You Can Develop and Test on Windows First

iOS development traditionally requires a Mac for everything. With a unified KMP Mobile module:

- You can build, iterate, and test 90% of the mobile screens and navigation directly on your Windows PC using Android emulator or connected Android devices.
- When you are ready to produce the iPhone build, the exact same mobile UI compiles straight to iOS native binary via Kotlin/Native.

#### 3. How "Real Apple Liquid Glass" Works under Option 1

This is the most critical detail you mentioned. In Compose Multiplatform on iOS, you are **not** trapped inside a sandbox where you can only use multiplatform widgets. Compose provides `UIKitView` and `UIKitViewController`, allowing you to embed authentic native Apple views directly into the Compose tree.

#### 4. How the "Native Utility Exceptions" Work Under the Hood

The same pattern applies to everything impossible or different on the other OS:

| Mobile Responsibility    | Android Native Exception                             | iOS Native Exception                                     | Shared Mobile UI           |
| :----------------------- | :--------------------------------------------------- | :------------------------------------------------------- | :------------------------- |
| **Liquid Glass / Blur**  | Kyant Backdrop / `RenderEffect`                      | Native `UIVisualEffectView` (`.systemUltraThinMaterial`) | Same card & dialog layouts |
| **File / Photo Picking** | Storage Access Framework (`SAF`)                     | `PHPickerViewController` / PhotoKit                      | Same media picker tray UI  |
| **Background Transfers** | WorkManager Foreground Service (`DATA_SYNC`)         | iOS `URLSessionConfiguration.background`                 | Same progress bar & ETA    |
| **Notifications**        | Android Notification Channels (`NotificationCompat`) | Apple `UNUserNotificationCenter`                         | Same alert triggers        |
| **Local Storage**        | Android EncryptedSharedPreferences / DataStore       | Apple iOS Keychain / DataStore                           | Same repository interfaces |

The shared UI calls high-level actions (e.g. `mobileTransferManager.startTransfer(...)`). The Android implementation delegates to `UploadWorker` / `WanDownloadWorker`, while the iOS implementation delegates to iOS `URLSession` background daemon. The UI never knows or cares about the underlying operating system differences.

---

### Comparison Matrix

| Criteria                        | Option 1: Unified KMP Mobile (`mobileApp/`)      | Option 2: Split Android (`DeX/app`) + iOS (`iosApp/`) |
| :------------------------------ | :----------------------------------------------- | :---------------------------------------------------- |
| **Matches Your 3-Tier Vision**  | 100% (Shared Mobile UI + Native Exceptions)      | Partial (Logic shared, but UI duplicated)             |
| **Code Duplication**            | Lowest (~85% shared code across mobile)          | High (100% of UI rewritten in SwiftUI)                |
| **Apple Native Glass Fidelity** | Native (via `UIKitView` -> `UIVisualEffectView`) | Native (via SwiftUI `.ultraThinMaterial`)             |
| **Maintenance Burden**          | 1 Mobile codebase to update                      | 2 separate UI codebases to synchronize                |
| **Bug Fixing Velocity**         | Fix once in mobile common, fixes both            | Fix in Android, then port fix to Swift                |

---

### Recommended Staged Roadmap

We do not do a disruptive "big bang" migration. We follow a safe, phased approach:

1. **Step 1 (Now)**: Keep Android developing cleanly in `DeX/app` while finishing the Android centralization (Plan 024 is now done!).
2. **Step 2 (Shared Wire & Core)**: Connect `DeX/app` to `:core:protocol` and `:core:data` so wire types and encryption are unified across desktop and mobile.
3. **Step 3 (KMP Mobile Foundation)**: Introduce the `mobileApp/` module structure. Move the composable screens from `DeX/app` into `mobileApp/commonMain` and wire the Android-specific exceptions into `mobileApp/androidMain`.
4. **Step 4 (iOS Target & Real Apple Glass)**: Add `iosMain` targets to `mobileApp/`, wire the `UIVisualEffectView` real Apple liquid glass bridge, and compile the iOS app.

This approach gives you the exact ecosystem architecture you envisioned with maximum code quality, zero UI drift, and full native Apple performance where it matters.
