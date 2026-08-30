# Plan 024: android-refactor-centralization (phased)

Priority: P1. Effort: L (4 phases, one commit per phase). Depends on: —.
Scope: ANDROID ONLY (`DeX/app`). Desktop Compose modules and Archived_Legacy_WPF untouched.

Source: 2026-08-30 full audit of the Android app (4 parallel deep passes; ~6,600 ln
network package + all large UI files). Goal: refactoring, modularization, and
centralization with ZERO behavioral/wire regression.

## Phase overview

- **Phase 1 — protocol constants + dead code (S-M, low risk):**
  `network/protocol/ProtocolKeys.kt` (message-type + envelope field names),
  `WsMessages.kt` (envelope builder), `ApiRoutes.kt` (URL paths), `DexJson.kt`
  (single shared kotlinx Json; TransferHistory's stricter instance kept separate),
  `NetConfig.kt` (multicast group, NSD type/name, timeouts, buffers, notification
  IDs, WorkManager input keys, intent actions/extras, prefs file names — values
  unchanged, moved verbatim). PIN length 5 → protocol constant. Dead code removal:
  `HistoryScreen.getDateGroupLabel`, `MessageHandler` dead constants, unused
  `DeviceContextMenu.backdrop` param, MainScreen/DiscoveryComponents unused imports.
- **Phase 2 — mechanical dedup (M, low risk):** SettingsSwitchRow shadow merge;
  unify 3 icon-in-circle option buttons; `rememberSpatialDialogState()`;
  `ui/util/Formatters.kt`; SelectionBadge/EmptyState/Motion tokens;
  `UploadWorkRequestFactory`; mock preview devices behind BuildConfig.DEBUG;
  LiquidGlassExperiment → debug source set; toast extension; strings.xml sweep.
- **Phase 3 — structural (L, medium risk):** TransferProgressNotifier delegate for
  3 workers + FileShareManager; MessageHandler split per feature;
  TelemetryReporter/PairingCoordinator extraction; MediaStore queries → repository;
  ErrorDialogs → `ui/pairing/`; OnboardingSheet → `ui/onboarding/` + permission-step
  merge; DI cleanup (kill lateinit, MessageSender interface, QuicClient lazy);
  package re-sort (data/, system/, auth/).
- **Phase 4 — high risk (defer; each needs own plan + soak testing):** PunchSession
  split; NavBottomSheet drag engine; FloatingPillNavBar interaction holder;
  TopAppBarState → ViewModel/state holders; OkHttp fallback client consolidation.

## Bugs found (flag to user; fix separately from refactor where chosen)

- HistoryScreen.kt:227 view-mode toggle maps LIST→LIST (grid unreachable).
- MainScreen troubleshoot dialog unreachable (never set true).
- NsdManagerHelper advertises `download=true` vs DiscoveryEngine localInfo `false`.
- DeviceManager `lateinit prefs` crash-before-init hazard.

## STOP conditions

- NEVER change any wire-visible value: `type`/`data` envelope field names, message
  type strings, NSD TXT attribute names, UDP payload field lists, punch
  line-protocol framing, WorkManager input keys' string values, URL path values,
  shared-transition keys. Constants move VERBATIM.
- No dependency downgrades; no deletion of legacy/archive content; no touching the
  in-flight dirty files (MainActivity, PermissionManager, OnboardingSheet,
  MediaPickerTray, strings.xml, MediaPermissions.kt) — Phase 1 avoids them
  entirely; later phases re-check tree state first.
- Never commit the pre-existing in-flight changes (they belong to a separate work
  stream); stage only files this plan creates/modifies.
- `FileDto.id == map key` contract, `[SKIP]` sentinel, and trust-predicate
  variants are load-bearing; any change is deliberate + user-approved, never
  incidental.
- STOP and escalate if baseline build fails before changes (in-flight work broken,
  not ours to fix), or if any migration cannot be done value-verbatim.

## Verification

- Baseline: `./gradlew :app:assembleDebug` BEFORE changes (record result).
- After each phase: `./gradlew :app:assembleDebug` green; diff-grep confirming no
  remaining scattered literals (message types, paths, keys).
- Manual soak (user-driven) after Phase 1: pairing, LAN send, WAN/relay send,
  punch, batch download, clipboard sync.

## Status

- Phase 1 DONE (2026-08-30): ProtocolKeys/WsMessages(envelopeOf)/ApiRoutes/DexJson/NetConfig/TransferWorkKeys/TransferIntents created and adopted across 20 files; PIN_LENGTH; dead code (getDateGroupLabel, MessageHandler dead constants, 16 unused imports); punch line-protocol reject frames intentionally left verbatim (wire framing). `:app:assembleDebug` green. Punch session timers (120s refresh / 60s prune / 12s window) left as Duration literals — Kotlin-native units, moving to NetConfig ms values would invite unit bugs; revisit only if Phase 3 splits PunchSession.
- Phase 2 TODO. Phase 3 TODO. Phase 4 deferred (own plan entries).
