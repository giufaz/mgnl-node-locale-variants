#!/usr/bin/env bash
# Integration (regression) tests: builds the module and the demo, starts Magnolia CE author + public on a fresh repository,
# seeds test content, runs the Karate features in features/, then stops Magnolia and removes its data.
#
# Usage: integration-tests/run.sh [--keep] [Karate options, e.g. --tags=@delivery (with "=": Karate reads a separate "@x" as an argument file)]
#   --keep   leave Magnolia running afterwards (stop it with: docker compose -f integration-tests/env/docker-compose.yml down)
# Env: MGNL_PORT (8064), MGNL_VERSION (6.4.10), MGNL_SUPERUSER_PASSWORD (superuser: password of the fresh test instance),
#      MVN (Maven command; default: mvn if installed, else Maven in Docker: env/mvn.sh), KARATE_VERSION (2.1.4),
#      WARMUP_TIMEOUT (240: seconds the AdminCentral gets to open its first app before the scenarios)
# Needs: Docker. Both images are built on first use: Magnolia from Magnolia's public Nexus (env/image), Karate with
# Chromium for the UI features (env/karate).
set -euo pipefail
IT="$(cd "$(dirname "$0")" && pwd)"; P="$(cd "$IT/.." && pwd)"
KEEP=false; ARGS=()
for a in "$@"; do if [ "$a" = "--keep" ]; then KEEP=true; else ARGS+=("$a"); fi; done
PORT="${MGNL_PORT:-8064}"; KV="${KARATE_VERSION:-2.1.4}"; MV="${MGNL_VERSION:-6.4.10}"
AUTH="superuser:${MGNL_SUPERUSER_PASSWORD:-superuser}"; WARMUP="${WARMUP_TIMEOUT:-240}"
W="$IT/.work"
export IT_ENV_DIR="$W/env" IT_DATA_DIR="$W/data" IT_UID="$(id -u)" IT_GID="$(id -g)" MGNL_PORT="$PORT" MGNL_VERSION="$MV"
DC=(docker compose -f "$IT/env/docker-compose.yml")

# Only one Magnolia at a time on the machine (same port, memory)
if docker ps --format '{{.Names}}' | grep -qi mgnl; then echo "A Magnolia container is already running:"; docker ps; exit 1; fi

echo "== Building the module"
if [ -n "${MVN:-}" ]; then read -r -a M <<< "$MVN"; elif command -v mvn >/dev/null; then M=(mvn); else M=("$IT/env/mvn.sh"); fi
(cd "$P" && "${M[@]}" -q -B -Dmaven.test.skip=true clean package)
JAR="$(ls "$P"/module/target/mgnl-node-locale-variants-*.jar | grep -v -e '-sources' -e '-javadoc' | head -1)"
VERSION="$(basename "$JAR" .jar)"; VERSION="${VERSION#mgnl-node-locale-variants-}"
DEMO_JAR="$(ls "$P"/demo/target/mgnl-node-locale-variants-demo-*.jar | grep -v -e '-sources' -e '-javadoc' | head -1)"

echo "== Preparing a fresh Magnolia $MV"
rm -rf "$W"; mkdir -p "$IT_ENV_DIR/jars" "$IT_DATA_DIR" "$IT/reports"
cp "$IT/env/entrypoint.sh" "$IT_ENV_DIR/"; cp "$JAR" "$DEMO_JAR" "$IT_ENV_DIR/jars/"
"${DC[@]}" build -q
cleanup() {
  if [ "$KEEP" = false ]; then
    echo "== Stopping Magnolia and removing its data"
    "${DC[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
    rm -rf "$W"
  else
    echo "== Magnolia left running on http://localhost:$PORT (data: $W)"
  fi
}
trap cleanup EXIT

echo "== Starting Magnolia (author + public, port $PORT)"
"${DC[@]}" up -d
for ctx in magnoliaAuthor magnoliaPublic; do
  c=000
  for _ in $(seq 1 120); do
    c="$(curl -s -o /dev/null -w '%{http_code}' -u "$AUTH" -X POST -H 'Content-Type: text/plain' --data-binary 'null' \
      "http://localhost:$PORT/$ctx/.rest/lv-demo/groovy" || true)"
    [ "$c" = 200 ] && break; sleep 5
  done
  [ "$c" = 200 ] || { echo "$ctx did not start (HTTP $c)"; "${DC[@]}" logs --tail 80; exit 1; }
done

RUN_ID="$(date +%Y%m%d_%H%M%S)"
docker build -q -t "lv-it/karate:$KV" --build-arg KARATE_VERSION="$KV" "$IT/env/karate" >/dev/null
# Karate in its image, in features/ (where karate-config.js is): arguments = Karate options and what to run
karate() {
  docker run --rm -u "$IT_UID:$IT_GID" -e HOME=/tmp --add-host=host.docker.internal:host-gateway \
    -e JAVA_TOOL_OPTIONS="-Dmgnl.base=http://host.docker.internal:$PORT -Dproject.version=$VERSION -Dwarmup.seconds=$WARMUP -Dmgnl.authToken=$(printf %s "$AUTH" | base64 | tr -d '\n') -Dmgnl.user=${AUTH%%:*} -Dmgnl.password=${AUTH#*:}" \
    -v "$IT:/it" -w /it/features "lv-it/karate:$KV" "$@"
}

# A Magnolia that has just started opens its first app slowly: wait for it here, not in a scenario
echo "== Warming up the AdminCentral (up to ${WARMUP}s)"
if ! karate --output "/it/reports/$RUN_ID/warmup" ../warmup/warmup.feature > "$W/warmup.log" 2>&1; then
  echo "== Warm-up failed: the AdminCentral did not open an app within ${WARMUP}s on the Magnolia that has just started."
  echo "   The scenarios were not run. On a slow machine give it more time, e.g.: WARMUP_TIMEOUT=600 integration-tests/run.sh"
  echo "   Details: $W/warmup.log (kept with --keep) and the Magnolia log: ${DC[*]} logs"
  tail -15 "$W/warmup.log"
  exit 1
fi

echo "== Running Karate $KV (report: reports/$RUN_ID)"
set +e
karate --output "/it/reports/$RUN_ID" "${ARGS[@]}" .
RC=$?
set -e

# Like every manual checkpoint: no WARN/ERROR from the module in the author and public logs
LOGS="$(grep -h -E '(^| )(WARN|ERROR) .*(giufaz|localevariants|c\.g\.m\.l\.|LocaleVariant|PagesSupport)' "$IT_DATA_DIR"/logs/*.log "$IT_DATA_DIR"/public/logs/*.log 2>/dev/null | sort -u || true)"
if [ -n "$LOGS" ]; then echo "== Module WARN/ERROR in the Magnolia logs:"; echo "$LOGS" | head -20; RC=1; else echo "== Magnolia logs: no WARN/ERROR from the module"; fi
ln -sfn "$RUN_ID" "$IT/reports/latest"
echo "== Karate exit code $RC. Report: $IT/reports/$RUN_ID/karate-summary.html (also reports/latest)"
exit $RC
