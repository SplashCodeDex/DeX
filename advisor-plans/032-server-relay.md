# Plan 032 — `server/` Module: Streaming E2EE Relay + Sync Host (Phase 0-C)

> Status: TODO
> Depends on: 025 (DONE), 031 (client contract)
> Effort: L (2–3 weeks solo)

## Why

WAN transfers between devices on different networks currently relay THROUGH the
desktop (it must be online and reachable). A cloud relay on the Hetzner VPS removes the
desktop from the critical path and hosts the sync surface (031). The existing
`RelayService` (desktop) STAGES files on disk — unusable on a CX22 (40GB disk; one
10GB relay fills it). The server must be a bounded-memory streaming postman.

## Scope

1. **`server/` Gradle module** (JVM, Kotlin, Ktor 3.x):
   - Depends ONLY on `:core:protocol` (wire law) — no desktop modules.
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
