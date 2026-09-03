# Plan 046 — Punch Data-Plane E2EE (phone-to-phone direct transfers)

> Status: TODO — BLOCKED on user GO (wire-protocol change: both phones must update
> in the same release; the punch handshake gains new frames).
> Depends on: plan 042 (split landed — the seams this plan wraps now exist).
> Effort: L. Risk: HIGH (crypto + wire).

## Why (verified gap, 2026-09-03)

Every other DeX transport encrypts: PC<->phone uses TLS with TOFU cert pinning
(`PinnedTrustManager`) on WS/HTTPS/QUIC, and WAN relay transfers are E2EE
(`RelayCrypto`, opaque frames — the relay cannot read content). The DIRECT
phone-to-phone punch path is the exception: `PunchSession`/`PunchTransferChannel`
speak PLAINTEXT TCP over the punched socket — the manifest (including
`identityHash`, alias, file names/sizes), the resume offsets, and every file byte
are readable by anything on-path (hostile NAT/gateway, rogue hotspot, ISP NAT log).

`RelayCrypto.deriveSessionKey(secret, sessionId)` already exists in `core/data`
(used by the WAN relay); the punch trust model is same-email (`identityHash`
equality), which is a capability check, NOT key material.

## Design sketch (for user approval)

1. **Key agreement via the PC rendezvous**: the sender generates an ephemeral X25519
   keypair; the `resolve-endpoint` reply (PC-mediated, already authenticated per
   tenant) carries the target's ephemeral public key; both sides derive the session
   key with HKDF over the shared secret + sessionId. The PC relays opaque keys but
   never holds the derived secret — same trust shape as the WAN relay.
2. **Data plane**: after the manifest (or including an encrypted manifest), seal
   frames with AES-256-GCM exactly like `WanDownloadWorker` consumes relay frames
   (length-prefixed nonce+ciphertext, `MAX_FRAME_BYTES` bounds already exist there).
   Resume offsets then operate on ciphertext byte counts — wire-visible change.
3. **Golden fixtures**: new punch frames get fixtures in `core/protocol` (or the
   Android-local ProtocolDto if the punch surface stays app-local — decision point).

## STOP conditions

- Desktop `Archived_Legacy_WPF` untouched; desktop punch paths (`DeviceRoutes`
  `/punch/endpoint`) only gain the opaque key-relay field — their semantics unchanged.
- Old/new phone versions must fail CLEANLY: a v1 receiver seeing v1.1 frames responds
  with the existing reject frame, never a hang.
- No key reuse across sessions; nonce uniqueness enforced (AES-GCM).

## Verification

- `:app:assembleDebug`, `:app:testDebugUnitTest` + new crypto interop tests
  (encrypt->decrypt round-trip, wrong-key rejection, tampered-frame rejection).
- Manual soak: punch transfer both directions, resume across a mid-transfer drop
  (ciphertext offset continuity), old-version phone cleanly rejected.
