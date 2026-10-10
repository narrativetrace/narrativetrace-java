#!/bin/sh
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
# Grades the published init prompt's step 6 — "Run the program … paste the trace" — for a case whose
# program is a SERVER (a Spring Boot application): the project the agent left behind starts, answers
# one request to its own endpoint, and the output it prints BECAUSE of that request carries a
# rendered trace naming $1. Run with cwd set to the scaffolded fixture copy, from grade_the_prompt.sh.
#
#   run_the_server.sh <TracedServiceName> <request-path>
#
# Request-scoped, operationally: the server's output is cut at the moment the request is sent, and
# only what it printed after that cut is graded. A trace printed at startup — a CommandLineRunner
# demo, a @PostConstruct call — is not a trace of a request and does not pass, however correct it
# is. The cut is taken once the port is open AND the startup output has been quiet for two seconds,
# because Spring runs its runners after the web server is already listening.
#
# What counts as a trace is find_the_trace.sh's decision, shared with run_the_program.sh: the
# Spring Web filter's per-request canonical JSON (`"className": "AccountService"` beside
# `"methodName"`) and a text renderer's `AccountService.describeAccount(` both qualify.
#
# Before anything runs, the boot jar has to start the project's own @SpringBootApplication class —
# see the Start-Class check below.
#
# The server is started from its boot jar on a free port, never `./gradlew bootRun`: a forked JVM
# under a daemon is not a process this script can stop, and an agent's own server may still hold
# 8080. Requests go through python3, not curl — the trial's curl is a recording stand-in.
set -e

service="$1"
path="$2"
here=$(dirname "$0")
if [ -z "$service" ] || [ -z "$path" ]; then
  echo "usage: run_the_server.sh <TracedServiceName> <request-path>" >&2
  exit 1
fi
sh "$here/find_the_trace.sh" "$service"
case "$path" in
  /*) ;;
  *)
    echo "the request path must start with /, got \"$path\"" >&2
    exit 1
    ;;
esac

work=$(mktemp -d)
log="$work/server-output.txt"
server=
stop_the_server() {
  if [ -n "$server" ]; then
    kill "$server" 2>/dev/null || true
    wait "$server" 2>/dev/null || true
  fi
  rm -rf "$work"
}
trap stop_the_server EXIT

# Bounded, like every command here: a cold build resolves dependencies over the network.
if ! timeout 900 ./gradlew bootJar --console=plain -q >"$work/build.txt" 2>&1; then
  echo "\`./gradlew bootJar\` failed — the prompt says to run the program, and it does not build" >&2
  cat "$work/build.txt" >&2
  exit 1
fi
jar=$(ls build/libs/*.jar 2>/dev/null | grep -v -- '-plain\.jar$' | head -1)
if [ -z "$jar" ]; then
  echo "\`./gradlew bootJar\` left no runnable jar under build/libs" >&2
  exit 1
fi

# The application is the project's own @SpringBootApplication class, and the boot jar must still start
# it. A console demo given to the `application` plugin as its mainClass becomes the boot jar's
# Start-Class too — the project's deliverable then runs the demo, not the service. That is a broken
# existing project however good the demo's trace is (three trials on 2026-10-09 did exactly this,
# following the skill's console first-trace step), so it fails here, by name, before anything runs.
app_source=$(grep -rl "@SpringBootApplication" src/main/java 2>/dev/null | head -1)
if [ -z "$app_source" ]; then
  echo "no @SpringBootApplication class is left under src/main/java — the application is gone" >&2
  exit 1
fi
app_class=$(printf '%s' "${app_source#src/main/java/}" | sed 's/\.java$//; s|/|.|g')
start_class=$(python3 - "$jar" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1]) as jar:
    manifest = jar.read("META-INF/MANIFEST.MF").decode("utf-8", "replace")
for line in manifest.splitlines():
    if line.startswith("Start-Class:"):
        print(line.split(":", 1)[1].strip())
PY
)
if [ "$start_class" != "$app_class" ]; then
  echo "the boot jar now starts ${start_class:-nothing}, not the application $app_class — the" >&2
  echo "project's own deliverable was changed (an \`application { mainClass }\` for a demo becomes" >&2
  echo "the boot jar's Start-Class), so running it no longer runs the service" >&2
  exit 1
fi

port=$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')
SERVER_PORT="$port" timeout 300 java -jar "$jar" --server.port="$port" >"$log" 2>&1 &
server=$!

# Up, then quiet: the port accepts a connection and the log has not grown for two seconds.
if ! python3 - "$port" "$log" "$server" <<'PY'
import os, socket, sys, time
port, log, pid = int(sys.argv[1]), sys.argv[2], int(sys.argv[3])
deadline = time.time() + 180
def alive():
    try:
        os.kill(pid, 0)
        return True
    except OSError:
        return False
while time.time() < deadline and alive():
    try:
        socket.create_connection(("127.0.0.1", port), timeout=1).close()
        break
    except OSError:
        time.sleep(0.5)
else:
    sys.exit(1)
size, quiet_since = -1, time.time()
while time.time() < deadline:
    now = os.path.getsize(log)
    if now != size:
        size, quiet_since = now, time.time()
    elif time.time() - quiet_since >= 2:
        sys.exit(0)
    time.sleep(0.25)
sys.exit(1)
PY
then
  echo "the server did not come up on port $port within three minutes" >&2
  cat "$log" >&2
  exit 1
fi

cut=$(wc -c <"$log")
status=$(python3 - "http://127.0.0.1:$port$path" <<'PY'
import sys, urllib.error, urllib.request
try:
    print(urllib.request.urlopen(sys.argv[1], timeout=30).status)
except urllib.error.HTTPError as e:
    print(e.code)
except Exception as e:
    print("no-answer:", e)
PY
)
case "$status" in
  2??) ;;
  *)
    echo "GET $path answered \"$status\" — the fixture's endpoint has to keep working" >&2
    cat "$log" >&2
    exit 1
    ;;
esac

# The trace is written after the response is committed, on the request thread; give it ten seconds.
after="$work/after-the-request.txt"
tries=0
while :; do
  tail -c +"$((cut + 1))" "$log" >"$after"
  if sh "$here/find_the_trace.sh" "$service" "$after" run_the_server.sh >/dev/null 2>&1; then
    break
  fi
  tries=$((tries + 1))
  if [ "$tries" -ge 20 ]; then
    if sh "$here/find_the_trace.sh" "$service" "$log" >/dev/null 2>&1; then
      echo "a trace naming $service was printed, but only BEFORE the request — a startup trace is" >&2
      echo "not a request-scoped one" >&2
    fi
    echo "--- the server's output after GET $path ---" >&2
    sh "$here/find_the_trace.sh" "$service" "$after" run_the_server.sh
    exit 1
  fi
  sleep 0.5
done
sh "$here/find_the_trace.sh" "$service" "$after" run_the_server.sh
echo "run_the_server.sh: GET $path answered $status and the trace was printed for that request"
