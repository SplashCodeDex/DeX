# DeX relay/sync server (plan 032) — fat JAR in a slim JDK runtime.
# Build context must contain server/build/libs/dex-server-all.jar (built by Gradle first).

FROM eclipse-temurin:21-jre-alpine

# Non-root runtime user; the server needs no filesystem writes (streaming relay law:
# content NEVER touches disk).
RUN addgroup -S dex && adduser -S dex -G dex
USER dex

WORKDIR /app
COPY server/build/libs/dex-server-all.jar /app/dex-server.jar

# Config is env-only (never secrets in git): DEX_PORT, DEX_GOOGLE_CLIENT_ID.
ENV DEX_PORT=8443

EXPOSE 8443

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- "http://127.0.0.1:${DEX_PORT}/healthz" || exit 1

ENTRYPOINT ["java", "-jar", "/app/dex-server.jar"]
