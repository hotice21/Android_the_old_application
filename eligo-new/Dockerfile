# syntax=docker/dockerfile:1

# Stage 1: Build
FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace

# First copy only build scripts to maximize dependency-cache reuse
COPY gradlew gradlew.bat gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle

RUN chmod +x ./gradlew

# Resolve dependencies with a BuildKit cache mount
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon --console=plain > /dev/null

COPY src ./src

# Build the boot jar
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon --console=plain -x test \
    && cp build/libs/*.jar /workspace/app.jar

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-jammy

# OCI labels
LABEL org.opencontainers.image.title="eligo-server" \
      org.opencontainers.image.description="Eligo backend API (Spring Boot)" \
      org.opencontainers.image.source="https://github.com/eligo/eligo-server"

# Run as a non-root user
RUN groupadd --system --gid 10001 eligo \
    && useradd --system --uid 10001 --gid eligo --home-dir /app eligo

WORKDIR /app

# Install curl for healthcheck
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Prepare the file-storage directory so the named volume inherits ownership
RUN mkdir -p /data/eligo-files \
    && chown -R eligo:eligo /data/eligo-files

COPY --from=build /workspace/app.jar ./app.jar

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
ENV SPRING_PROFILES_ACTIVE=docker

EXPOSE 8080

USER eligo

HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=10 \
    CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
