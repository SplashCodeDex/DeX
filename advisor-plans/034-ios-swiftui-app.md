# Plan 034 — iOS App: SwiftUI Phone Shell (Phase 1B)

> Status: TODO
> Depends on: 033 (DONE)
> Effort: XL (6–8 weeks solo) — the longest single phase

## Why

The iPhone app: native SwiftUI shell consuming the shared KMP core (protocol + domain +
its platform adapters). iOS-specific constraints shape the design and are restated here
as law so nobody rediscovers them the hard way.

## Scope

1. **iOS lifecycle tier = Ephemeral** (architecture rule 6, plan 025):
   no background listener server. The app is a transfer INITIATOR (foreground) and a
   receiver via push-to-wake (~30s accept window) or foreground. Transfers are
   checkpointed/resumable so backgrounding mid-transfer pauses, never fails.
2. **Surfaces**:
   - Dashboard: device roster (sync 031 + LAN discovery via NWBrowser for mDNS),
     connection status, recent transfers (synced history).
   - Send flow: photo/file picker → share sheet → transfer with progress.
   - Receive flow: push notification → accept/reject → background URLSession download.
   - Pairing: QR scan + PIN entry (drives shared `PairingEngine` — the 033 smoke test
     becomes real UI).
   - Settings: account, paired devices (forget = `TrustRevocationService` semantics),
     preferences (synced).
   - Clipboard sync: UIPasteboard observation while foregrounded + echo guard.
3. **Transports**: URLSession with HTTP/3 (`URLSessionConfiguration` enables h3
   automatically when the server advertises) — the QUIC story on iOS. TLS trust
   bootstrap for the self-signed LAN cert: pin per-fingerprint after pairing (reuse
   the paired-token tier; verify current ATS exception practice first — research
   trigger).
4. **Sign-in**: Google Sign-In for iOS (current SDK flow — research trigger), feeding
   the same identity-proof path (HMAC over googleSub).
5. **Push**: APNs via the server (032) — token registration on sign-in.

## STOP conditions

- App Store compliance gates are part of DONE, not follow-ups: privacy policy URL,
  privacy nutrition labels, **account deletion** (Google sign-in counts as an account —
  the delete path must exist server-side), export/compliance manifests.
- If the shared core cannot express an iOS need, the fix goes INTO `core/domain` —
  never a parallel Swift implementation (drift law).
- Transport fallback order preserved: h3/QUIC → HTTPS → relay; never invent new ports.
- Expected App Review friction: local-network permission prompts (NSLocalNetworkUsage
  + Bonjour service declarations) and background-audio-style justifications — document
  the IDFA/network answers truthfully; 1–2 rejection iterations are normal planning.

## Verification

On-device: LAN transfer desktop↔iPhone, WAN transfer via relay, pairing both
directions, clipboard echo guard, backgrounding mid-transfer → resume.
`.\gradlew` desktop suites stay green (no desktop changes).
