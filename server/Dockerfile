# DeX relay/sync server (plan 032) — Multi-stage reproducible build.
#
# Usage:
#   docker build -t dex-server -f server/Dockerfile .

# Stage 1: Build the fat JAR from source in an isolated JDK 21 container
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace
COPY . .

RUN chmod +x gradlew && ./gradlew :server:shadowJar --no-daemon

# Stage 2: Minimal JRE runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root runtime user; the server needs no filesystem writes (streaming relay law:
# content NEVER touches disk).
RUN addgroup -S dex && adduser -S dex -G dex
USER dex

WORKDIR /app
COPY --from=builder /workspace/server/build/libs/dex-server-all.jar /app/dex-server.jar

# Config is env-only (never secrets in git): DEX_PORT, DEX_GOOGLE_CLIENT_ID.
ENV DEX_PORT=8443

EXPOSE 8443

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- "http://127.0.0.1:${DEX_PORT}/healthz" || exit 1

ENTRYPOINT ["java", "-jar", "/app/dex-server.jar"]
