# Plan 045 — OkHttp/Cronet Client Consolidation (Android, plan 024 Phase 4)

> Status: TODO
> Depends on: plan 024 (Phases 1-3 DONE). Effort: M. Risk: MEDIUM (network behavior).
> Soak testing REQUIRED before DONE (manual, user-driven).

## Why

Four independently-built `OkHttpClient` instances exist (verified 2026-09-03):

| Call site | Known profile |
|---|---|
| `BatchDownloadWorker.kt:55` | plain-HTTP fallback for the PC pull port (no TLS on 48426), connect 10s / read 30s |
| `QuicClient.kt` (lazy `httpClient`) | OkHttp fallback beside the Cronet QUIC path |
| `WanDownloadWorker.kt:54` | `CONNECT_TIMEOUT_SEC` / `READ_INACTIVITY_SEC` constants (WAN relay data plane) |
| `WebSocketClientService.kt:49` | 30s ping + `sslSocketFactory` + `pinnedTrustManager` (cert pinning via `CertificatePinning.kt`) |

Each carries its own timeouts, and — critically — it is UNVERIFIED from this survey
which of the non-WebSocket clients also install the pinned trust manager. Duplicated
builders have already drifted (different constants, different SSL treatment), which is
exactly how a pinning regression or a timeout regression ships silently.

## Scope

1. **WP0 audit (gates everything)**: for each of the four call sites record EVERY
   builder option (timeouts, SSL/pinning, interceptors, dispatchers, connection specs)
   and whether the traffic is TLS or plain. Output appended to this file.
2. **Introduce one `DeXHttpClients` factory** with named profiles derived from the
   audit (e.g. `plainPull()`, `wanRelay()`, `pinnedWebSocket()`, `quicFallback()`),
   each reproducing its call site's options EXACTLY. Callers swap to the factory;
   no option values change.
3. If the audit reveals a genuine profile BUG (e.g. an un-pinned client that should be
   pinned), STOP and file it as a separate finding — this plan consolidates, it does
   not fix security posture.

## STOP conditions

- Timeout and SSL values byte-identical per call site (the audit table is the contract).
- No Cronet behavior change (`QuicClient` stays Cronet-first, OkHttp fallback).
- No dependency changes (okhttp/cronet versions untouched).
- Soak gates DONE: LAN batch download (plain fallback), WAN relay download, WS
  pairing/transfer handshake (pinning still enforced — verify a mismatched-cert host
  is refused), QUIC fast path still preferred where available.

## Verification

- `:app:assembleDebug`, `:app:testDebugUnitTest` green.
- Manual soak: LAN transfer, batch download, WAN relay both directions, certificate
  pinning refusal check.
