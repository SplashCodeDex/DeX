# Plan 021: fix-settings-surface

Priority: P1. Effort: M.

## Findings addressed (from the 2026-08-24 Settings audit)

1. **[SECURITY] `/local/settings/*` routes served on the LAN-facing HTTPS listener**
   (`0.0.0.0:48424`): unauthenticated peers could overwrite the identity email (which
   re-derives the auto-trust identity hash), force sign-out, remotely pop the OAuth
   browser flow, or read back the Google profile. The `/local/` prefix signals loopback
   intent; only `127.0.0.1:28425` may serve them.
2. **Download Location setting was a dead-end**: panel-local state only; never persisted,
   never consumed by transfers (`ReceiveStorage.downloadsDir()` hardcoded `~/Downloads/DeX`).
3. **UPnP toggle controlled nothing**: `upnpEnabled` pref read nowhere;
   `DesktopUpnpService.configureAsync()` ran unconditionally and never consulted it.

## Changes

- `DeXServer`: module split into `baseModule` (protocol surface) + loopback-only
  `settingsRoutes()` on server2; `oauthCallbackRoutes()` removed from all shared modules,
  served exclusively by the dedicated `127.0.0.1:48425` listener.
- `DeviceConfig`: new persisted `download_dir` pref + `downloadDirFlow`; `initializedFlow`
  signals DataStore load completion for consumers that must not race defaults.
- `ReceiveStorage`: honors an override path mirrored from the pref by the app shell
  (single writer in `main.kt`'s `LaunchedEffect`); blank = legacy default.
- `DesktopUpnpService`: `configureAsync()` is now pref-aware — awaits
  `initializedFlow`, maps when UPnP is ON, releases mappings on toggle-off, follows live
  toggles via flow collection. DI passes `DeviceConfig`.
- `SettingsPanel`: Download Location writes the persisted pref; subtitle shows the
  effective path.

## STOP conditions

- Do NOT change the Google Cloud Console registered redirect URI
  (`http://127.0.0.1:48425/local/oauth/callback`) — it must keep serving verbatim.
- Do NOT move pairing/transfer trust decisions; scope is settings routing only.
- If removing settingsRoutes from the WAN listener breaks any phone-side flow, STOP and
  escalate to the user (no known caller exists outside the desktop UI).

## Verification

- `gradlew build` + `spotlessCheck` + all `desktopTest` suites green.
- Runtime smoke: app boots, DeXServer log unchanged (ports), settings still functional
  from the UI (loopback path).

## Status

DONE 2026-08-24. All three findings fixed; verified compile/test/format green.
