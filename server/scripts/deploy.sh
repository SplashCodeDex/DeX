#!/usr/bin/env bash
# DeX Cloud Relay Deployment Script (Plan 032)
# Target: Ubuntu 24.04 LTS (Hetzner VPS or equivalent)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

cd "${SERVER_DIR}"

echo "=== DeX Cloud Relay & Sync Host Deployment ==="

# 1. Check prerequisites
if ! command -v docker &>/dev/null; then
    echo "ERROR: docker is not installed. Please install Docker before deploying."
    exit 1
fi

if ! docker compose version &>/dev/null; then
    echo "ERROR: docker compose is not available. Please install the Docker Compose plugin."
    exit 1
fi

# 2. Check environment configuration
if [ ! -f ".env" ]; then
    if [ -f ".env.example" ]; then
        echo "WARNING: .env not found. Copying .env.example to .env..."
        cp .env.example .env
        echo "Please edit server/.env with your real DEX_GOOGLE_CLIENT_ID and DEX_DOMAIN before proceeding."
        exit 1
    else
        echo "ERROR: Neither .env nor .env.example found in ${SERVER_DIR}."
        exit 1
    fi
fi

# Source .env
set -a
# shellcheck disable=SC1091
source .env
set +a

if [ -z "${DEX_GOOGLE_CLIENT_ID:-}" ] && [ "${DEX_FIXTURE_AUTH:-0}" != "1" ]; then
    echo "ERROR: DEX_GOOGLE_CLIENT_ID is not configured in .env."
    echo "The server strictly refuses to boot as an unauthenticated open relay."
    exit 1
fi

echo "Deploying for domain: ${DEX_DOMAIN:-localhost}"

# 3. Build fat JAR if gradle is present locally, or rely on Docker multi-stage
if [ -f "../gradlew" ] && [ ! -f "build/libs/dex-server-all.jar" ]; then
    echo "Building dex-server-all.jar via Gradle..."
    (cd .. && ./gradlew :server:shadowJar --no-daemon)
fi

# 4. Bring up containers
echo "Starting containers via Docker Compose..."
docker compose up -d --build --remove-orphans

# 5. Wait and probe healthz
echo "Verifying service health..."
MAX_ATTEMPTS=12
ATTEMPT=0
HEALTHY=0

while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
    ATTEMPT=$((ATTEMPT + 1))
    STATUS=$(docker inspect --format='{{.State.Health.Status}}' dex-server 2>/dev/null || echo "starting")
    if [ "$STATUS" = "healthy" ]; then
        HEALTHY=1
        break
    fi
    echo "Waiting for dex-server container to become healthy (attempt $ATTEMPT/$MAX_ATTEMPTS)..."
    sleep 3
done

if [ $HEALTHY -eq 1 ]; then
    echo "=== Deployment Successful! ==="
    echo "dex-server is HEALTHY and listening behind Caddy edge proxy."
    docker compose ps
else
    echo "ERROR: dex-server failed to report healthy status within timeout."
    docker compose logs dex-server
    exit 1
fi
