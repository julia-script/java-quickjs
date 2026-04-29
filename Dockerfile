# syntax=docker/dockerfile:1
#
# Linux CI parity (Temurin 22 + Zig) with cache-friendly layers.
#
# BuildKit caches Gradle between builds (wrapper + dependency caches):
#   DOCKER_BUILDKIT=1 docker build --platform linux/amd64 -t javaquickjs-linux-tools --target linux-tools .
#
# Optional image that also warms compileJava (slower build, faster first test in container):
#   DOCKER_BUILDKIT=1 docker build --platform linux/amd64 -t javaquickjs-linux-ci --target linux-ci .
#
# Typical local run (reuses ~/.gradle in a named volume, sources from bind mount):
#   docker compose run --rm linux-test
#
FROM eclipse-temurin:22-jdk AS linux-tools

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates xz-utils \
    && rm -rf /var/lib/apt/lists/*

ARG ZIG_VERSION=0.13.0
ENV ZIG_INSTALL=/opt/zig
ENV PATH="${ZIG_INSTALL}:${PATH}"

RUN curl -fsSL "https://ziglang.org/download/${ZIG_VERSION}/zig-linux-x86_64-${ZIG_VERSION}.tar.xz" \
        | tar -xJ -C /opt \
    && mv "/opt/zig-linux-x86_64-${ZIG_VERSION}" "${ZIG_INSTALL}"

WORKDIR /workspace

# --- Optional CI image: bake project and warm Gradle caches (no bind mount needed) ---
FROM linux-tools AS linux-ci

COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties ./

RUN chmod +x gradlew \
    && ./gradlew --version --no-daemon

COPY . .

RUN --mount=type=cache,target=/root/.gradle/wrapper \
    --mount=type=cache,target=/root/.gradle/caches \
    ./gradlew --no-daemon compileJava -PhostNativeTarget=linux-x86_64

CMD ["./gradlew", "--no-daemon", "test", "-PhostNativeTarget=linux-x86_64"]
