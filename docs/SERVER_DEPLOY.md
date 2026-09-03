# DeX Server — Deployment & Operations Runbook (Plan 032)

> Source of truth for the first production deploy of the `server/` relay + sync host.
> Every artifact referenced here already exists in the repo and was verified 2026-09-03.
> Secrets law (plan 032 STOP condition): credentials NEVER enter git — GitHub Actions
> secrets + VPS-local env files only.

## Topology (what runs where)

```
Internet ── 80/443 (Caddy: auto-HTTPS, HTTP/3) ── dex-server:8443 (Ktor/Netty, Docker)
```

- `server/Dockerfile` — slim `eclipse-temurin:21-jre-alpine`, non-root `dex` user,
  `dex-server-all.jar` (Shadow fat JAR), unauthenticated `/healthz` probe.
- `server/docker-compose.yml` — two services on `dex-net`: `dex-server` (memory cap
  1536M) + `caddy:2-alpine` (80/443/443-udp), Caddy waits for server health.
- `server/Caddyfile` — TLS termination, zero-buffer streaming law (`flush_interval -1`;
  Caddy does not buffer proxy bodies by default), HSTS + hardening headers, JSON logs.
- `server/scripts/deploy.sh` — CANONICAL deploy path: env validation → Gradle fat JAR →
  `docker compose up -d --build` → health-probe loop (12 × 3s) → `docker compose ps`.
- `.github/workflows/server-deploy.yml` — CI path (see "Known discrepancy" below).

## User decision gate (before anything below can run)

Per the plan ledger, plan 032 cannot complete without these user-provided inputs:

1. **Hetzner VPS** provisioned (plan assumption: CX22, Ubuntu 24.04 LTS).
2. **Domain** (e.g. `relay.dexstudios.com`) with **A/AAAA records** pointing at the VPS
   IP — Caddy auto-issues Let's Encrypt certificates on first start; ports 80 + 443 must
   be reachable from the internet, and the `caddy_data` volume (already in compose)
   persists certificates across restarts.
3. **Google OAuth 2.0 Web Client ID** — must match the Android Credential Manager
   `serverClientId`. The server refuses to boot as an unauthenticated open relay
   (`deploy.sh` enforces `DEX_GOOGLE_CLIENT_ID` unless `DEX_FIXTURE_AUTH=1`, which is
   forbidden in production).
4. **GitHub Actions secrets** (only if the CI path is used): `DEX_SSH_KEY`,
   `DEX_VPS_HOST`, `DEX_VPS_USER`.

## First-deploy procedure (canonical path — `deploy.sh`)

1. Provision VPS: install Docker Engine + the Docker Compose plugin; open firewall for
   `22`, `80`, `443` (443/udp for HTTP/3 if the firewall is protocol-aware).
2. Get the code onto the VPS (`git clone` is enough — the script builds locally).
3. `cd server && cp .env.example .env` and edit:
   - `DEX_DOMAIN=<your domain>` (drives Caddy's site address + auto-HTTPS),
   - `DEX_GOOGLE_CLIENT_ID=<real client id>`,
   - `DEX_FIXTURE_AUTH=0` and `DEX_DEV=0` (never `1` in production).
4. `./scripts/deploy.sh` — validates env, builds `:server:shadowJar`, brings up the
   compose stack, and fails loudly if the container never reports `healthy`.
5. Verify from outside:
   - `curl -f https://<domain>/healthz` → HTTP 200 (proves Caddy TLS + proxy + server),
   - `docker compose ps` → both services `running`, `dex-server` `healthy`,
   - `docker compose logs caddy -n=100` → certificate issuance succeeded.

## CI deploy path (resolved — Option A, user decision 2026-09-03)

`.github/workflows/server-deploy.yml` no longer runs a bare `docker run` — it now:

1. Builds `dex-server-all.jar` and runs the `:server:test` contract suite (gate).
2. Ships only the JAR + a `server/` source bundle over SSH (`DEX_SSH_KEY`,
   `DEX_VPS_HOST`, `DEX_VPS_USER` GitHub secrets).
3. On the VPS, extracts to `/opt/dex`, places the JAR, and invokes
   `server/scripts/deploy.sh` — the SAME compose + Caddy stack as the manual path
   above. TLS, health probing and rollback semantics can never diverge between
   CI and manual deploys.

One-time VPS provisioning for the CI path (in addition to the procedure above):

- Install Docker Engine + Compose plugin and `tar`; the deploy user needs write
  access to `/opt/dex` and Docker permissions (docker group or root).
- Server config lives ONLY in `/opt/dex/server/.env` on the VPS — the bundle
  excludes `.env`, so CI can never overwrite it. On the very first CI deploy, the
  workflow copies `.env.example` to `.env` and fails with provisioning instructions;
  edit `DEX_DOMAIN` + `DEX_GOOGLE_CLIENT_ID` and re-run the workflow.
  `DEX_GOOGLE_CLIENT_ID` therefore never needs to be a GitHub secret at all.

## Remaining plan-032 verification after first deploy

- **Production load test:** concurrent multi-GB relay streams; confirm bounded memory
  (`docker stats` — KiB-scale deltas expected, the streaming law), no disk growth in
  `/var/lib/docker` beyond images, quota rejection (per-account caps) before first byte.
- **On-device convergence test:** desktop + Android against the deployed host —
  pairing, LAN transfer fallback, WAN relay transfer both directions, sync exchange.
- Then mark plan 032 DONE in `advisor-plans/README.md` (status row MUST be updated).

## Rollback

The compose path rebuilds from the checked-out commit, so rollback = `git checkout
<last-good-tag> && ./scripts/deploy.sh`. The CI path overwrites `dex-server:latest`
without keeping the previous image — another reason to prefer path A above.

## Change protocol

Any change to these artifacts (Dockerfile, compose, Caddyfile, deploy.sh, workflow)
follows repo rules: handwritten CHANGELOG entry, `[fix]`/`[minor]`/`[major]` commit tag,
push, and a plan-032 note when it affects remaining scope.
