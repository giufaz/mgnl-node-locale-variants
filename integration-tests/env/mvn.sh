#!/usr/bin/env bash
# Maven in Docker, for run.sh when Maven is not installed: runs "mvn <arguments>" in the project root.
# Env: MVN_IMAGE (default maven:3.9-eclipse-temurin-17), M2_DIR (local Maven repository, default ~/.m2).
set -euo pipefail
P="$(cd "$(dirname "$0")/../.." && pwd)"
IMAGE="${MVN_IMAGE:-maven:3.9-eclipse-temurin-17}"
M2="${M2_DIR:-$HOME/.m2}"; mkdir -p "$M2"
exec docker run --rm -u "$(id -u):$(id -g)" \
  -e MAVEN_CONFIG=/var/maven/.m2 -e HOME=/var/maven \
  -v "$M2:/var/maven/.m2" -v "$P:/w" -w /w \
  "$IMAGE" mvn -Duser.home=/var/maven -B "$@"
