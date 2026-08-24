# DeX Desktop — WebSocket Protocol Contract

> Exact wire contract for the control channel. Field names here are canonical; the `count`
> vs `digitCount` mismatch bug happened because this file did not exist. Do not invent
> message types or rename fields without updating both peers and this document.

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
| `pin-digit-entered` | → | `digitCount: int` | Live keystroke telemetry while the phone types (note: field is `digitCount`, inside `data`) |
| `trust-check` | ↔ | `isTrusted: bool`, `fingerprint?: string` | Trust reconciliation; receiver downgrades local trust when peer reports distrust |
| `unpair` | ↔ | `fingerprint` | Revoke trust; receiver removes local pairing entries |
| `prepare-upload` | ↔ | `PrepareUploadRequestDto { info: RegisterDto, files: Map<id, FileDto> }` | LocalSend v2 transfer offer |
| `pull-progress` | → | `doneFiles`, `totalFiles`, `sentBytes`, `totalBytes`, `currentFile`, `state` (`done`/`cancelled`/`failed` terminal) | Phone reports pull progress; folded into `FileExplorerService.updatePullProgress` |
| `device-roster` | ↔ | `{}` request / `{devices: [{fingerprint, alias, deviceType}]}` | Same-account roster refresh (also periodic 60 s keepalive) |
| `public-address` | ← | `address` | PC advertises WAN address; phone auto-fills only if blank |
| `endpoint-info` | ← | `{targetFingerprint, ip, port}` | Resolved punch endpoint for a pending NAT punch |
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
  │                              │    ├─ match   → persist trust, Success
  │                              │    └─ no/miss → rejected (manual Accept still possible)
```

**Desktop-initiated (client role):** desktop sends `pair-request` over ITS socket to another
PC's `/ws`; inbound `pair-prompt` surfaces `InboundPairingDialogOverlay`; auto-accept only if
already paired locally; simultaneous prompts resolved by lexicographic fingerprint tie-break.

## RegisterDto (discovery + transfer identity payload)

`alias, version, deviceModel, deviceType ("desktop"/"phone"/...), fingerprint, port,
quicPort=48423, tcpFallbackPort=48426, protocol, download, identityHash?, googleSub?,
battery?, isCharging?, wifiBand?, wifiSsid?`

Canonical definition: `core/data/src/commonMain/.../protocol/ProtocolDto.kt`. HTTP routes under
`/api/localsend/v2` require a Bearer token resolved by trust priority:
googleSub → identityHash → paired token.
