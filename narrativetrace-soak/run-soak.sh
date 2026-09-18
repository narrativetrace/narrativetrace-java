#!/usr/bin/env bash
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
# Soak harness runner — see README.md. Usage: ./run-soak.sh [smoke|one-hour|two-hour]
#
# Precondition (deliberately NOT run by this script, so it stays build-tool-agnostic — see
# README.md "How to run"): the shop/notify bootJars and the agent's standalone jar must already be
# built:
#   ./gradlew :narrativetrace-soak:shop:bootJar :narrativetrace-soak:notify:bootJar \
#             :narrativetrace-agent:standaloneJar
set -euo pipefail

PROFILE="${1:-smoke}"
case "$PROFILE" in
  smoke) TOTAL_SECONDS=600 ;;
  one-hour) TOTAL_SECONDS=3600 ;;
  two-hour) TOTAL_SECONDS=7200 ;;
  *)
    echo "Unknown profile '$PROFILE' (expected smoke|one-hour|two-hour)" >&2
    exit 1
    ;;
esac

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"
REPO_ROOT="$(cd .. && pwd)"

# --- Guards ---------------------------------------------------------------

LOCK_FILE="$SCRIPT_DIR/.soak.lock"
if [ -e "$LOCK_FILE" ]; then
  echo "Refusing to start: lock file $LOCK_FILE present (another run in progress, or stale from a crashed one — remove it if you are sure)." >&2
  exit 1
fi
touch "$LOCK_FILE"
cleanup() {
  rm -f "$LOCK_FILE"
  rm -rf "$SCRIPT_DIR/.agent-jar"
  docker compose -f compose.yaml down --remove-orphans >/dev/null 2>&1 || true
}
trap cleanup EXIT

if [ -n "$(cd "$REPO_ROOT" && git status --porcelain)" ]; then
  echo "Refusing to start: $REPO_ROOT has a dirty working tree." >&2
  exit 1
fi

CORES="$(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo 4)"
LOAD1="$(uptime | sed -E 's/.*load averages?: *([0-9]+)[.,]([0-9]+).*/\1.\2/')"
CEILING="$(awk -v c="$CORES" 'BEGIN{printf "%.2f", c*0.5}')"
if awk -v l="$LOAD1" -v c="$CEILING" 'BEGIN{exit !(l>c)}'; then
  echo "Refusing to start: 1-min load average $LOAD1 exceeds the ceiling $CEILING (cores*0.5, cores=$CORES)." >&2
  exit 1
fi

for jar_glob in \
  "$REPO_ROOT/narrativetrace-soak/app/shop/build/libs/shop-"*.jar \
  "$REPO_ROOT/narrativetrace-soak/app/notify/build/libs/notify-"*.jar; do
  if ! compgen -G "$jar_glob" >/dev/null; then
    echo "Missing build artifact matching: $jar_glob" >&2
    echo "Build first: ./gradlew :narrativetrace-soak:shop:bootJar :narrativetrace-soak:notify:bootJar :narrativetrace-agent:standaloneJar" >&2
    exit 1
  fi
done

# The agent module's build/libs/ accumulates standalone jars from every version this machine has
# ever built (SNAPSHOT/-local/prior releases) — mounting that whole directory would let the
# container pick a stale one by an arbitrary sort order. Stage exactly the current version's
# standalone jar instead, keyed off gradle.properties (the single source for narrativetraceVersion).
AGENT_VERSION="$(sed -n 's/^narrativetraceVersion=//p' "$REPO_ROOT/gradle.properties")"
AGENT_JAR_SRC="$REPO_ROOT/narrativetrace-agent/build/libs/narrativetrace-agent-${AGENT_VERSION}-standalone.jar"
if [ ! -f "$AGENT_JAR_SRC" ]; then
  echo "Missing build artifact: $AGENT_JAR_SRC" >&2
  echo "Build first: ./gradlew :narrativetrace-agent:standaloneJar" >&2
  exit 1
fi
AGENT_JAR_DIR="$SCRIPT_DIR/.agent-jar"
rm -rf "$AGENT_JAR_DIR"
mkdir -p "$AGENT_JAR_DIR"
cp "$AGENT_JAR_SRC" "$AGENT_JAR_DIR/"

# --- Setup ------------------------------------------------------------------

RESULTS_DIR="$SCRIPT_DIR/results"
LOGS_DIR="$SCRIPT_DIR/logs"
rm -rf "$RESULTS_DIR" "$LOGS_DIR"
mkdir -p "$RESULTS_DIR" "$LOGS_DIR/shop" "$LOGS_DIR/notify"
# k6 (uid 12345) and the sampler (uid 100) share neither uid nor group with the host user that
# creates this directory — world-writable so both containers can write their evidence into it (D2).
chmod a+rwx "$RESULTS_DIR"

PHASE1_SECONDS=$(( TOTAL_SECONDS / 10 ))
PHASE2_SECONDS=$TOTAL_SECONDS

wait_for_shop() {
  # Checked from INSIDE the shop container, not against localhost:8080 on the runner's own host:
  # under a remote/sibling Docker daemon (DOCKER_HOST pointing at a docker:dind sidecar, this
  # repo's own dev-container setup), a published port binds on the DAEMON's host, not on whatever
  # host `docker compose` itself runs from — "localhost:8080" there never reaches it.
  for _ in $(seq 1 90); do
    if docker compose -f compose.yaml exec -T shop wget -q -O /dev/null http://localhost:8080/soak/stats 2>/dev/null; then
      return 0
    fi
    sleep 2
  done
  echo "shop never answered /soak/stats" >&2
  return 1
}

start_sampler() {
  # $1: phase-specific stats file — see compose.yaml's sampler service comment for why this
  # can't be a single shared stats.jsonl across both phases.
  # --no-deps: this is a `run`, not `up` — without it, compose re-resolves shop/notify's config
  # (TRACING_ENABLED) and can recreate them (D1).
  STATS_FILE="$1" docker compose -f compose.yaml run --rm --no-deps -d sampler >/dev/null
}

stop_sampler() {
  # `docker compose run --rm -d` starts a one-off container that plain `down` does not stop (D3) —
  # it must be stopped explicitly before the next phase starts, or it keeps sampling (and, once the
  # next phase's containers exist, appending samples from the WRONG phase into this phase's file).
  docker compose -f compose.yaml stop sampler >/dev/null 2>&1 || true
  docker compose -f compose.yaml rm -f sampler >/dev/null 2>&1 || true
}

# --- Phase 1: baseline (tracing off — no -javaagent at all, the truest zero-overhead baseline;
# narrativetrace.level=OFF would still pay weaving/dispatch-check cost for an attached-but-quiet
# agent) for 10% of the profile length. TRACING_ENABLED is exported for the whole phase (not just
# the `up` line) — otherwise the sampler/k6 `docker compose run` invocations resolve compose.yaml's
# ${TRACING_ENABLED:-true} default, see config drift on shop/notify, and recreate them traced (D1).
# ---
echo "=== Phase 1: baseline (tracing off), ${PHASE1_SECONDS}s ==="
export TRACING_ENABLED=false
docker compose -f compose.yaml up -d --build shop notify
wait_for_shop
start_sampler /results/baseline-stats.jsonl
# The k6 service's compose-level entrypoint is already ["sh", "-c"] — the override below is that
# `-c` script's single argument, not a second `sh -c` (docker compose run appends its command
# argument(s) after the service's entrypoint, it does not replace it).
# --no-deps: same D1 reasoning as start_sampler above — a plain `run` must not recreate shop/notify.
docker compose -f compose.yaml run --rm --no-deps k6 \
  "k6 run --vus 5 --duration ${PHASE1_SECONDS}s -e SOAK_SUMMARY_PATH=/results/baseline-scenario-summary.json scenario.js & \
   k6 run -e SOAK_POISON_DURATION=${PHASE1_SECONDS}s -e SOAK_POISON_SUMMARY_PATH=/results/baseline-poison-summary.json poison.js & \
   wait"
stop_sampler
docker compose -f compose.yaml down
cp -r "$LOGS_DIR" "$RESULTS_DIR/baseline-logs"

# --- Phase 2: traced, full profile length. ---
echo "=== Phase 2: traced, ${PHASE2_SECONDS}s (profile=$PROFILE) ==="
rm -rf "$LOGS_DIR" && mkdir -p "$LOGS_DIR/shop" "$LOGS_DIR/notify"
export TRACING_ENABLED=true
docker compose -f compose.yaml up -d --build shop notify
wait_for_shop
start_sampler /results/traced-stats.jsonl
K6_EXIT=0
docker compose -f compose.yaml run --rm \
  -e SOAK_PROFILE="$PROFILE" \
  -e SOAK_SUMMARY_PATH=/results/traced-scenario-summary.json \
  -e SOAK_POISON_DURATION="${PHASE2_SECONDS}s" \
  -e SOAK_POISON_SUMMARY_PATH=/results/traced-poison-summary.json \
  k6 "k6 run scenario.js & k6 run poison.js & wait" || K6_EXIT=$?
docker compose -f compose.yaml down

# --- Evidence -----------------------------------------------------------

echo "=== Building SUMMARY.md ==="
SUMMARY_EXIT=0
python3 "$SCRIPT_DIR/summarize.py" "$RESULTS_DIR" "$LOGS_DIR" "$PHASE2_SECONDS" \
  > "$RESULTS_DIR/SUMMARY.md" || SUMMARY_EXIT=$?
cat "$RESULTS_DIR/SUMMARY.md"

if [ "$K6_EXIT" -ne 0 ]; then
  echo "Phase 2 k6 run exited non-zero ($K6_EXIT) — see $RESULTS_DIR/traced-scenario-summary.json" >&2
fi
if [ "$SUMMARY_EXIT" -ne 0 ]; then
  echo "summarize.py reported a threshold or poison-count failure (see SUMMARY.md)." >&2
fi

exit $(( K6_EXIT != 0 || SUMMARY_EXIT != 0 ? 1 : 0 ))
