# DeX relay/sync server (plan 032).
# Source checkout: docker build --target runtime -t dex-server -f server/Dockerfile .
# CI-tested JAR: docker build --target prebuilt -t dex-server -f server/Dockerfile .

FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
COPY . .
RUN sh ./gradlew :server:shadowJar --no-daemon

# Both build paths use the same runtime configuration.
FROM eclipse-temurin:21-jre-alpine AS runtime-base
RUN addgroup -S dex && adduser -S dex -G dex
USER dex
WORKDIR /app
ENV DEX_PORT=8443
EXPOSE 8443
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -qO- "http://127.0.0.1:${DEX_PORT}/healthz" || exit 1
ENTRYPOINT ["java", "-jar", "/app/dex-server.jar"]

# BuildKit skips the unrelated builder stage when deploying the tested artifact.
FROM runtime-base AS prebuilt
COPY server/build/libs/dex-server-all.jar /app/dex-server.jar

# Keep a full source build as the default target for existing manual workflows.
FROM runtime-base AS runtime
COPY --from=builder /workspace/server/build/libs/dex-server-all.jar /app/dex-server.jar
