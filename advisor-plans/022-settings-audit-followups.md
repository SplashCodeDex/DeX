# Plan 022: settings-audit-followups

Priority: P2. Effort: S-M. Depends on: 021.

## Findings addressed (remaining items from the 2026-08-24 Settings audit)

1. **Sign-in trigger mismatch**: `LoopbackControlApi.triggerGoogleSignIn()` GETs an
   endpoint that blocked until the human browser round-trip finished; the client's 5s
   timeout fired on every successful sign-in and printed misleading failure noise.
2. **Fake profile data**: fallback name "DeXStudios", fallback email "dexify@dex.net",
   unconditional "Premium User" badge, static avatar even when a Google picture existed.
3. **Latent gap found during restructure**: desktop sign-in never persisted the Google
   name/picture/sub — only the Android app wired those (`GoogleSignInManager`), so the
   Settings header could never show real profile data on desktop.
4. **Reset Identity & Trust had no confirmation** despite being destructive one-click.
5. **Doc-comment drift** in SettingsPanel header (duplicated numbering, stale "Version 1.0.0").
6. **SettingsRoutes used inline Koin service-locator lookups** instead of the constructor-
   injection style of sibling routes.
7. **No desktop alias editor** although `DeviceConfig.aliasFlow` feeds discovery
   advertisement.

## Changes

- `SettingsRoutes`: `/local/settings/google-signin` responds immediately ("Continue in
  your browser…"); a dedicated supervisor scope awaits the browser round-trip and then
  persists email + name + picture + sub. Routes take `DeviceConfig` as a parameter;
  DeXServer resolves it from Koin at the call site.
- `SettingsPanel`: honest signed-out state ("DeX Desktop" / "Not signed in" / hint line),
  fabricated "Premium User" badge removed, avatar renders the real Google picture via
  `produceState` + shared skia `toImageBitmap` helper with placeholder fallback.
- Reset Identity & Trust behind a Material3 confirmation dialog.
- New Device Name item + alias editor dialog (persisted via `deviceConfig.alias`, max 32 chars).
- Header doc comment rewritten to match reality.

## STOP conditions

- Do not change the Google-registered redirect URI or the callback contract.
- Alias semantics unchanged: trim + take(32), persisted key identical to Android's.

## Verification

- `gradlew build` + spotlessCheck green, zero compiler warnings.
- Runtime smoke: boot clean, listeners up, settings UI functional.
