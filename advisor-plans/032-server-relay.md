# Plan 032 — `server/` Module: Streaming E2EE Relay + Sync Host (Phase 0-C)

> Status: IN PROGRESS — WP1 DONE (surfaces + deploy) · WP2 DONE (E2EE + WAN client) · WP3 DONE (hardening pass) · WP4 DONE (cross-platform desktop & android WAN relay integration)
> Depends on: 025 (DONE), 031 (DONE — client loop)
> Effort: L (2–3 weeks solo)

## What shipped (pre-deploy hardening, 2026-09-03)

- **Caddyfile correctness fix**: removed `buffer_requests false` / `buffer_responses
  false` — these subdirectives no longer exist in current Caddy 2.x (verified against
  the live reverse_proxy docs; current streaming surface is `flush_interval`,
  `request_buffers`, `response_buffers`, `stream_timeout`, `stream_close_delay`) and
  would have failed config parse on first `docker compose up`. The zero-buffer law is
  preserved: Caddy does not buffer proxy bodies by default, and `flush_interval -1`
  (valid, verified) flushes every read immediately. Deploy runbook added at
  `docs/SERVER_DEPLOY.md`; ledger rows for 032/024 trued up.
- **CI deploy path resolved (Option A, user decision 2026-09-03)**:
  `.github/workflows/server-deploy.yml` reworked to the single canonical path — CI
  builds + tests the fat JAR, ships JAR + `server/` source bundle over SSH, and the
  VPS runs `server/scripts/deploy.sh` (compose + Caddy, auto-HTTPS). Server env
  (`DEX_GOOGLE_CLIENT_ID`, `DEX_DOMAIN`) lives only in `/opt/dex/server/.env` on the
  VPS, excluded from the bundle and never overwritten by CI; SSH credentials remain
  the only GitHub secrets. Runbook updated.

## What shipped (Work Package 4, 2026-09-03 — Cross-Platform WAN Cloud Relay Transfer Integration)

- **`IPlatformEngine` Multiplatform Expansion**: Added `downloadWanRelay(sessionId, streamToken, relayUrl, fileName, totalBytes, fingerprint, sourceAlias)` to the shared cross-platform engine contract.
- **`MessageHandler` Wire Routing**: Added `MessageTypes.RELAY_OFFER` handling, extracting wire fields and routing offers directly to `engine.downloadWanRelay(...)`.
- **Android Platform Engine**: Implemented `AndroidPlatformEngine.downloadWanRelay(...)` verifying paired token trust before dispatching to `TcpDownloadService.downloadWanRelay(...)` and `WanDownloadWorker`.
- **Desktop Platform Engine**: Implemented `DesktopPlatformEngine.downloadWanRelay(...)` verifying paired token trust, allocating unique destination files via `ReceiveStorage.uniqueDest(...)`, and streaming/decrypting frame-by-frame via `WanRelayClient.download(...)` while publishing live updates to `TransferStateMonitor` and `TransferHistoryRecorder`.
- **Centralized Path Sanitization**: Centralized collision-free destination resolution (`uniqueDest`) in `ReceiveStorage`, eliminating code duplication between `DesktopPullService` and `DesktopPlatformEngine`.
- **Desktop Outbound WAN Relay Transfer**: Integrated `sendViaWanRelay` into `DesktopFileSendService` when `deviceConfig.syncHostUrl` is configured, opening relay sessions via `WanRelayClient`, prompting remote peers with `MessageTypes.RELAY_OFFER`, and streaming AES-256-GCM sealed chunks concurrently with progress telemetry, falling back seamlessly to LAN relay if unset or unreachable.
- **Contract & Regression Tests**: Added unit contract tests for `relay-offer` parsing in `MessageHandlerTest` and verified full multiplatform test suite (125 tests passed in `:core:network`, 57 tasks in root, 100 tasks in Android).

## What shipped (Work Package 3, 2026-09-02 — edge-case hardening pass)

- **SECURITY**: punch routes now bearer-auth + tenant-scoped (cross-account resolve →
  indistinguishable 404), bounded inputs, 64-entry per-tenant cap, sweep-on-request.
- **Memory**: WAN download streams frame-by-frame (no whole-file buffering — 10 GiB
  transfers cost KiB); hostile frame-length parsing fails closed.
- **Failure semantics**: upload completes ONLY fully-pushed streams and hard-closes on
  failure (truncation can never masquerade as completion); 120s relay / 90s sync
  request timeouts (wedged hosts fail requests, never stall loops).
- **Concurrency**: DeviceRegistry / TransferUseCase / RelaySessionRegistry.openSession
  read-modify-write sequences serialized (concurrent beacons/telemetry/sweeps/quota
  checks could lose updates or double-pass caps).
- **SyncEngine**: failure re-queue no longer regresses newer deltas queued mid-exchange
  (HLC comparison before restore; regression test added).
- **DoS bounds**: exchange body + batch caps pre-parse; SyncHostStore per-tenant record
  cap (20k) that never wedges existing-key updates.
- **Ops**: unauthenticated `/healthz`; Dockerfile HEALTHCHECK now probes it (the old
  probe hit an auth-gated route and would restart-loop a healthy container).
- Remaining: first deploy (user decisions: Hetzner, domain, DNS, client-id secret),
  production load test, phone-side WAN receive (plan 030).

## What shipped (Work Package 2, 2026-09-02 — E2EE + WAN client)

- **`RelayCrypto` (core/data, KMP expect/actual)**: the E2EE key schedule — HKDF-SHA256
  (extract-then-expand, RFC 5869) over IKM=paired-token, salt=sessionId, info="dex-relay-v1"
  -> AES-256-GCM per-chunk AEAD (random 96-bit nonces, 128-bit tags). Both peers derive
  identically; NO key ever crosses the wire. JVM actuals via javax.crypto only (no
  hand-rolled primitives). 10-test suite: deterministic derivation, per-session key
  uniqueness, nonce randomness, round-trips (0B..1MiB+), wrong-key/tamper/truncation
  fail-closed, multi-chunk independence.
- **Server relay data-plane fix (real defect found by orchestration tests)**: the
  SharedFlow pass-through had NO completion semantics — the receiver's HTTP response
  never terminated (hang), and subscriber-less emissions were silently dropped.
  Rewritten as a capacity-bounded Channel (64 frames, ~16MiB at the client's chunk
  size): an outrunning sender SUSPENDS at capacity (the bounded-memory law, enforced),
  the sender's explicit `/complete` closes the channel and the receiver's drain always
  terminates. Added `/close` (idempotent hard teardown).
- **`WanRelayClient` (core/network)**: desktop WAN orchestration — authenticated session
  open, sender streams 256KiB chunks sealed under the session key as [4-byte
  length][sealed] frames (framing is INSIDE the opaque stream — the relay learns only
  chunk sizes it already quota-accounts), signals completion, receiver drains and opens
  every frame with the same derived key; any AEAD failure aborts with zero plaintext
  leaked. 5 orchestration tests run against the REAL server module's routes (embedded),
  covering full multi-chunk round-trips, wrong-token fail-closed (no partial plaintext),
  mid-stream rejection surfacing, auth gate, 37-chunk framing integrity, and per-test
  session hygiene (quota release).
- Remaining for this plan: first deploy (user decisions: Hetzner provisioning, domain,
  DNS, secrets), production load test against the live host, phone-side WAN receive
  wiring (Android, plan 030 territory).

## What shipped (Work Package 1, 2026-09-01)

- **`server/` Gradle module** (JVM): Ktor 3.x Netty; consumes ONLY `core/protocol` +
  `core/sync` (the two wire laws) — no desktop modules, enforced by the dependency list.
  Shadow fat JAR (`dex-server-all.jar`) + Dockerfile (non-root, streaming law = no disk
  writes) + GitHub Actions deploy workflow (secrets-only credentials).
- **Auth**: `GoogleIdTokenVerifier` (current documented approach, research-verified) —
  signature/iss/aud/exp with cached public keys; tenant = verified `sub` (googleSub —
  the trust model's namespace). `FixtureIdTokenVerifier` for tests, DOUBLE-GATED in
  Main (refuses to boot without real verification in prod — no silent open relay).
- **Sync host**: `/sync/v1/exchange` (the core/sync wire law verbatim) over an
  in-memory tenant store; same `supersedes` HLC-LWW law as every peer; illegal
  collections dropped at the door. 6 tests: auth gates, tenant isolation, merge law,
  illegal-collection rejection.
- **Punch rendezvous**: `/punch/register` + `/punch/resolve`, 5-min TTL (desktop
  parity), self-contained JSON (no plugin coupling).
- **Streaming relay**: `RelaySessionRegistry` — quota BEFORE first byte (2 concurrent
  sessions/tenant, 2 GiB/session, 10-min idle + 1-h hard TTL), bounded SharedFlow
  pass-through (never disk), opaque E2EE bytes (the relay structurally cannot read
  content), unguessable per-session stream token for the data plane. 11 tests: auth
  gates, token gates, quota, opaque byte pass-through, session lifecycle, punch
  round-trips.
- **Root build fix**: the `allprojects` test-router now registers ONLY for KMP/Android
  projects — the previous unconditional registration collided with the Java plugin's
  own `test` task and broke any JVM module's plugin application.
- Remaining for this plan: desktop/WAN client wiring against the deployed host
  (relay-session orchestration through the registry), E2EE session-key plumbing from
  the pairing exchange, load test (concurrent multi-GB within bounded memory),
  on-device convergence test, VPS provisioning + first deploy (user decision: domain +
  DNS records).

## Why

WAN transfers between devices on different networks currently relay THROUGH the
desktop (it must be online and reachable). A cloud relay on the Hetzner VPS removes the
desktop from the critical path and hosts the sync surface (031). The existing
`RelayService` (desktop) STAGES files on disk — unusable on a CX22 (40GB disk; one
10GB relay fills it). The server must be a bounded-memory streaming postman.

## Scope

1. **`server/` Gradle module** (JVM, Kotlin, Ktor 3.x):
   - Depends on `:core:protocol` (wire law) AND `:core:sync` (sync wire law — the
     `SyncExchangeRequest`/`SyncExchangeResponse` DTOs + `SyncEndpoints` paths + the
     `SyncRecord`/HLC model are DEFINED THERE and consumed by the server verbatim, so
     the HTTP exchange contract can never drift between client and host). No desktop
     modules.
   - Deployment: fat JAR in Docker; GitHub Actions build+deploy to Hetzner.
2. **Streaming relay**:
   - Chunked pass-through with bounded in-flight buffers per session — never stage to
     disk. Per-account concurrent-session cap + hard size cap + idle timeout.
   - **E2EE by construction**: per-session keys derived from the pairing identity
     exchange; the relay forwards opaque bytes + routing headers. The relay CANNOT read
     content — this is what makes a $4 VPS architecturally sufficient forever.
   - Pull-token model preserved (hosted files become hosted streams with TTLs).
3. **Punch rendezvous**: move `/punch/endpoint` semantics off the desktop
   (`/punch/register`, `/punch/resolve` REST) — the desktop stays a peer, not a
   rendezvous point. Trusted-caller-only rules migrate verbatim.
4. **Sync host**: the 031 collections + auth (Google ID Token verification; account
   subtree tenancy).
5. **Ops**: structured logging, health endpoint, backup snapshots (Hetzner), uptime
   monitoring. No secrets in the repo — env-file injection.

## STOP conditions

- **NEVER stage file content to server disk** — if streaming cannot be made bounded,
   STOP and consult the user; do not ship disk staging.
- Relay must enforce per-account quotas BEFORE first byte (fail fast, not mid-stream).
- Google ID Token verification must use a current documented library flow — run the
  stale-knowledge protocol before implementation (auth is a proactive-research trigger).
- Desktop `RelayService` is NOT deleted in this phase — it remains the LAN/P2P
  fallback path; the server complements it. Any removal is a separate user decision.
- Docker/deploy secrets never enter git (use GitHub Actions secrets + env files).

## Verification

```
.\gradlew :server:test                  # streaming/quota/auth suites
.\gradlew :core:network:desktopTest     # desktop punch path still green
```
Load test: concurrent multi-GB streams within bounded memory; quota rejection tests.
