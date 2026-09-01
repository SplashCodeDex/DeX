# DeX Desktop — WebSocket Protocol Contract

> Exact wire contract for the control channel. Field names here are canonical; the `count`
> vs `digitCount` mismatch bug happened because this file did not exist. Do not invent
> message types or rename fields without updating both peers and this document.
>
> **Single source of truth (plan 025):** the constants live in `core/protocol`
> (`MessageTypes`, `FieldNames`, `ProtocolEnvelope`) with golden-fixture tests. Desktop
> call sites reference the constants; the Android app keeps a lockstep `ProtocolKeys`
> registry (`DeX/app`) until the shared-core integration phase. A wire-value change must
> land in `core/protocol`, this document, and every peer in the same release.

## Transport

`wss://<host>:48424/ws?fingerprint=<fp>&alias=<urlencoded>[&token=<bearer>]`

- Connections are keyed by the `fingerprint` query parameter (`WebSocketConnectionManager`).
- Unverified sockets are allowed to connect so they can pair; verified sockets present a token.
- Frames: JSON text (`{"type": "...", "data": {...}}`) for control, binary for mirror video.

## Message catalogue

Direction is from the **desktop server** perspective. `→` = received by desktop,
`←` = sent by desktop.

| Type | Dir | `data` fields | Purpose |
|------|-----|---------------|---------|
| `pair-request` | → | — | Phone asks to start PIN pairing |
| `pair-prompt` | ← | `pin`, `alias`, `fingerprint` | Desktop offers its generated PIN (TTL 60 s) |
| `pair-response` | → | `accepted: bool`, `pin?: string` | Phone's answer. Persisted ONLY if `pin` proves knowledge of the offered PIN; bare assertions are rejected (manual Accept still possible) |
| `pair-accepted` | ← | `token`, `fingerprint` | Grant confirmation carrying the freshly minted per-device pairing token and the desktop's own fingerprint; receiver persists both so reconnects authenticate without re-pairing |
| `identity-challenge` | ← | `nonce: base64` | Sent to untrusted sessions when we are signed in; proof-of-possession of the shared account ID |
| `identity-proof` | → | `mac: base64(HMAC-SHA256(nonce, googleSub))` | Same-account proof. The sub itself never crosses the wire; success upgrades ONLY the live session (no persistence) |
| `pin-digit-entered` | → | `digitCount: int` | Live keystroke telemetry while the phone types (note: field is `digitCount`, inside `data`) |
| `trust-check` | ↔ | `isTrusted: bool`, `fingerprint?: string` | Trust reconciliation; receiver downgrades local trust when peer reports distrust |
| `unpair` | → | `fingerprint` (sender's OWN) | Peer-initiated revocation: desktop removes that pairing, downgrades the session, and never accepts a third-party fingerprint here |
| `prepare-upload` | ↔ | `PrepareUploadRequestDto { info: RegisterDto, files: Map<id, FileDto> }` | LocalSend v2 transfer offer |
| `pull-progress` | → | `doneFiles`, `totalFiles`, `sentBytes`, `totalBytes`, `currentFile`, `state` (`done`/`cancelled`/`failed` terminal) | Phone reports pull progress; folded into `FileExplorerService.updatePullProgress` |
| `device-roster` | ↔ | `{}` request / `{devices: [{fingerprint, alias, deviceType}]}` | Same-account roster refresh (also periodic 60 s keepalive); membership derived only from PROVEN session identity |
| `public-address` | ← | `address` | PC advertises WAN address; phone auto-fills only if blank |
| `endpoint-info` | ← | `{targetFingerprint, ip, port}` | Resolved punch endpoint for a pending NAT punch (trusted callers only) |
| `peer-endpoint` | → | `{peerFingerprint, ip, port}` | Punch peer announces where to hit it |
| `relay-started` / `relay-error` | ← | — | Relay-transfer fallback ack |
| `set-clipboard` | ← | `text` | Clipboard sync push (receiver remembers it to avoid echo) |
| `wallpaper-updated` | ← | — | Invalidate wallpaper cache |
| `mirror-start` / `mirror-stop` | ← | — | Toggle screen mirroring (binary frames follow on same socket) |
| `list-shared-folders` / `browse-folder` / `pull-files` / `grant-shared-folder` | ← | request-scoped; replies carry `requestId` and resolve via `DexRequestStore` | File explorer over WS |
| `telemetry` | → | optional `battery`, `wifiSsid`, `wifiRssi` | Periodic device telemetry |

## Pairing sequences

**Phone-initiated (primary):**
```
phone                          desktop server
  │ ── pair-request ────────────▶│  PinPhase(pin, TTL 60s); panel shows PIN
  │ ◀────── pair-prompt ─────────│
  │ ── pin-digit-entered × n ───▶│  live digit highlight on panel
  │ ── pair-response{accepted,   │
  │      pin="482910"} ─────────▶│  verifyInboundPin(fp, pin)?
  │                              │    ├─ match   → mint token, persist BOTH sides,
  │                              │    │            ◀── pair-accepted{token, fingerprint}
  │                              │    └─ no/miss → rejected (manual Accept still possible)
```

**Same-account auto-trust (replaces advertisement-based matching):**
```
phone                          desktop server
  │ ── wss connect (no token) ──▶│
  │ ◀─ identity-challenge{nonce}─│  only when untrusted AND signed in
  │ ── identity-proof{mac} ─────▶│  HMAC(nonce, googleSub), constant-time compare
  │                              │    ├─ match   → session upgraded to trusted (roster,
  │                              │    │            prompts, WAN address flow)
  │                              │    └─ miss    → stays untrusted; pairing still available
```

**Desktop-initiated (client role):** desktop sends `pair-request` over ITS socket to another
PC's `/ws`; inbound `pair-prompt` surfaces `InboundPairingDialogOverlay`; auto-accept only if
already paired locally; simultaneous prompts resolved by lexicographic fingerprint tie-break.

## Identity material handling

`identityHash` and `googleSub` are BEARER credentials. They are **never advertised**: not in
discovery beacons, not in `GET /api/localsend/v2/info`. Discovery payloads carry
`identityHash=null, googleSub=null`. Legacy peers may still present them as bearer tokens
(server-side acceptance retained for compatibility), but no disclosure channel exists anymore.

## RegisterDto (discovery + transfer identity payload)

`alias, version, deviceModel, deviceType ("desktop"/"phone"/...), fingerprint, port,
quicPort=48423, tcpFallbackPort=48426, protocol, download, identityHash?*, googleSub?*,
battery?, isCharging?, wifiBand?, wifiSsid?`

`*` fields exist on the DTO for peer ingestion but OUR advertisements always send null.

Canonical definition: `core/data/src/commonMain/.../protocol/ProtocolDto.kt`. HTTP routes under
`/api/localsend/v2` require a Bearer token resolved by trust priority:
googleSub → identityHash → paired token (constant-time comparisons throughout).

The same Bearer rule applies to `POST /api/dex/clipboard` (401 otherwise; token resolution
lives in `server/BearerTrust`). All `/local/` routes (share-target, the `/local/dex` file
explorer proxy, `/local/settings`) are LOOPBACK-ONLY by contract: `guardLoopback()` answers
403 unless the request arrived on a loopback-bound listener.
