#!/usr/bin/env bash
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
#
# Post-publish verification: proves a Maven Central + Gradle Plugin Portal release actually
# landed. Two things are checked, both from OUTSIDE the pipeline that built the release:
#
#   1. Artifact presence — every published module's jar+pom on repo1.maven.org, and the
#      Gradle Plugin Portal marker for `id("ai.narrativetrace")`. The module list comes from
#      `./gradlew -q printPublishedCoordinates` (see build.gradle.kts) rather than being
#      copy-pasted here, so an 18th published artifact can never go unpolled by accident.
#      Central's own sync can take minutes to hours, so this polls every not-yet-present
#      artifact together, backing off between rounds, up to one overall --timeout — not a
#      separate timeout per artifact (18 artifacts times a two-hour timeout each would be a
#      day and a half of polling for what is, in practice, one release event).
#   2. A consumer smoke test — a throwaway project applying `id("ai.narrativetrace") version
#      <version>` from the real Gradle Plugin Portal, with the exact one-service-one-test
#      recipe `documentation/first-10-minutes.md` documents and this repository's own
#      functional tests exercise. Built with an isolated Gradle user home and no mavenLocal:
#      resolution must come from the real remote repositories, or the test is theater.
#
# This is a manual publish-checklist step (planning/PUBLISHING.md), never a per-commit or
# nightly gate: it makes real network calls, and a passing run this minute can legitimately
# fail the next if Central has not synced yet. See documentation/security-tooling.md's
# THIN-CI rule for the same reasoning applied to the scanners that also stay out of gates.
#
# Usage:
#   scripts/verify-publication.sh <version> [options]
#
# Options:
#   --dry-run           Print the coordinates and URLs that would be checked; make no network
#                        calls, run no smoke test. Exits 0.
#   --local-rehearsal   Rehearse the recipe before any release exists anywhere public: check
#                        the local Maven repository (~/.m2/repository, populated by
#                        `./gradlew publishToMavenLocal`) instead of the real remotes, and
#                        resolve the smoke test's plugin + libraries from mavenLocal() instead
#                        of the Plugin Portal + Maven Central. Never the default — a rehearsal
#                        that silently became the real check would prove nothing.
#   --timeout=SECONDS   Give up polling after this long overall (default: 7200 — two hours;
#                        Central sync is documented as "minutes to hours").
#   --interval=SECONDS  Steady-state wait between polling rounds once backoff has ramped up
#                        (default: 60).
#   -h, --help           Print this usage text and exit 0.
#
# Exit status: 0 only if every artifact is PRESENT and the smoke test PASSED (or --dry-run).
set -euo pipefail
# Portability guard: bash 3.2+ (macOS stock bash), matching every other script here.
[ -n "${BASH_VERSION:-}" ] || { echo "ERROR: run with bash, not another shell." >&2; exit 1; }

# BASH_SOURCE[0], not $0: this file is sourced (not executed) by
# VerifyPublicationScriptTest to unit-test the pure functions below without a release, and
# inside a sourced file $0 is the CALLER's path, not this file's — only BASH_SOURCE[0] is
# reliable in both cases.
cd "$(dirname "${BASH_SOURCE[0]}")/.."
REPO_ROOT="$(pwd)"

MAVEN_CENTRAL_BASE="https://repo1.maven.org/maven2"
GRADLE_PLUGIN_PORTAL_BASE="https://plugins.gradle.org/m2"
LOCAL_MAVEN_REPO="${LOCAL_MAVEN_REPO:-$HOME/.m2/repository}"
DEFAULT_TIMEOUT_SECONDS=7200
DEFAULT_INTERVAL_SECONDS=60
INITIAL_BACKOFF_SECONDS=15
CURL_MAX_TIME_SECONDS=30

usage() {
    cat <<'USAGE'
Usage: scripts/verify-publication.sh <version> [options]

Options:
  --dry-run           print the coordinates/URLs that would be checked; no network calls,
                       no smoke test.
  --local-rehearsal   check ~/.m2/repository and resolve the smoke test from mavenLocal()
                       instead of the real remotes. Rehearsal only, never the default.
  --timeout=SECONDS   overall polling deadline (default: 7200).
  --interval=SECONDS  steady-state polling interval (default: 60).
  -h, --help          print this text and exit 0.
USAGE
}

# ---------------------------------------------------------------------------------------
# Pure functions: no network, no filesystem beyond what is passed in as arguments. Sourced
# directly by narrativetrace-build-tests' VerifyPublicationScriptTest, which is how this
# logic is unit-tested without a release — see the task's own comment on
# `printPublishedCoordinates` in build.gradle.kts for the matching "testable without a
# release" idea applied to the coordinate list this script consumes.
# ---------------------------------------------------------------------------------------

# "ai.narrativetrace" -> "ai/narrativetrace"
group_path() {
    printf '%s' "${1//./\/}"
}

maven_artifact_url() {  # base group artifact version extension
    printf '%s/%s/%s/%s/%s-%s.%s\n' "$1" "$(group_path "$2")" "$3" "$4" "$3" "$4" "$5"
}

maven_jar_url() { maven_artifact_url "$1" "$2" "$3" "$4" jar; }  # base group artifact version
maven_pom_url() { maven_artifact_url "$1" "$2" "$3" "$4" pom; }  # base group artifact version

local_artifact_path() {  # group artifact version extension
    printf '%s/%s/%s/%s/%s-%s.%s' "$LOCAL_MAVEN_REPO" "$(group_path "$1")" "$2" "$3" "$2" "$3" "$4"
}

# HTTP status -> verdict. 200 is PRESENT. 404 is LAGGING: a clean "not found yet" answer, and
# Central's own documented propagation delay is minutes to hours, so this is expected, not an
# error. Anything else (5xx, or curl's own "000" for a connection that never got a response)
# is MISSING — not explained by ordinary sync lag, and worth a human's attention.
classify_http_status() {
    case "$1" in
        200) echo "PRESENT" ;;
        404) echo "LAGGING" ;;
        *) echo "MISSING" ;;
    esac
}

# ---------------------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------------------

parse_args() {
    DRY_RUN=0
    LOCAL_REHEARSAL=0
    TIMEOUT_SECONDS="$DEFAULT_TIMEOUT_SECONDS"
    INTERVAL_SECONDS="$DEFAULT_INTERVAL_SECONDS"
    VERSION=""
    while [ $# -gt 0 ]; do
        case "$1" in
            --dry-run) DRY_RUN=1 ;;
            --local-rehearsal) LOCAL_REHEARSAL=1 ;;
            --timeout=*) TIMEOUT_SECONDS="${1#--timeout=}" ;;
            --interval=*) INTERVAL_SECONDS="${1#--interval=}" ;;
            -h|--help) usage; exit 0 ;;
            -*) echo "ERROR: unknown argument '$1'" >&2; exit 1 ;;
            *)
                [ -z "$VERSION" ] || { echo "ERROR: unexpected extra argument '$1'" >&2; exit 1; }
                VERSION="$1"
                ;;
        esac
        shift
    done
    [ -n "$VERSION" ] || { usage >&2; echo "ERROR: missing <version>." >&2; exit 1; }
    case "$TIMEOUT_SECONDS" in ''|*[!0-9]*) echo "ERROR: --timeout must be a whole number of seconds." >&2; exit 1 ;; esac
    case "$INTERVAL_SECONDS" in ''|*[!0-9]*) echo "ERROR: --interval must be a whole number of seconds." >&2; exit 1 ;; esac
}

# ---------------------------------------------------------------------------------------
# Coordinate derivation — asks the build, never hardcodes the module list.
# ---------------------------------------------------------------------------------------

load_coordinates() {
    "$REPO_ROOT/gradlew" -q printPublishedCoordinates
}

# ---------------------------------------------------------------------------------------
# Artifact presence poll. Parallel arrays (bash 3.2 has no associative arrays) indexed the
# same way: CHECK_KIND[i] / CHECK_GROUP[i] / CHECK_ARTIFACT[i] / CHECK_STATUS[i]. One HTTP
# round checks every not-yet-PRESENT artifact once; the whole batch shares one deadline.
# ---------------------------------------------------------------------------------------

CHECK_KIND=()
CHECK_GROUP=()
CHECK_ARTIFACT=()
CHECK_STATUS=()

load_checks() {
    CHECK_KIND=()
    CHECK_GROUP=()
    CHECK_ARTIFACT=()
    CHECK_STATUS=()
    local kind group artifact
    while read -r kind group artifact; do
        [ -n "$kind" ] || continue
        CHECK_KIND+=("$kind")
        CHECK_GROUP+=("$group")
        CHECK_ARTIFACT+=("$artifact")
        CHECK_STATUS+=("PENDING")
    done <<COORDINATES
$(load_coordinates)
COORDINATES
}

# Remote resource URLs to check for one artifact: LIBRARY checks jar+pom, PLUGIN checks the
# marker POM only (a plugin marker artifact has no jar — its only content is a <dependencies>
# entry pointing at the real implementation coordinate, which the LIBRARY-shaped
# narrativetrace-gradle-plugin coordinate covers if it is ever published separately).
resource_urls_for() {  # kind group artifact version
    local kind="$1" group="$2" artifact="$3" version="$4" base
    base="$MAVEN_CENTRAL_BASE"
    if [ "$kind" = "PLUGIN" ]; then
        base="$GRADLE_PLUGIN_PORTAL_BASE"
    fi
    maven_pom_url "$base" "$group" "$artifact" "$version"
    # A real `if`, not a bare `[ ... ] &&` guard: under `set -e`, a guard whose condition is
    # false and which is the last statement of a function returns non-zero from the function
    # itself — silently, since nothing here treats "no jar for a marker" as an error — and
    # every caller of this function (a bare statement or the LHS of a `pipefail`-checked pipe)
    # would then abort the whole script. An `if` block's own exit status is always 0 when its
    # condition is false and there is no `else`.
    if [ "$kind" = "LIBRARY" ]; then
        maven_jar_url "$base" "$group" "$artifact" "$version"
    fi
}

head_status() {  # url
    curl -s -o /dev/null -w '%{http_code}' --head -L --max-time "$CURL_MAX_TIME_SECONDS" "$1" || echo 000
}

# Every required resource for one artifact must be 200 for the artifact itself to read
# PRESENT; the worst single-resource verdict otherwise wins (MISSING over LAGGING).
remote_check_one() {  # kind group artifact version
    local url status worst="PRESENT" verdict
    while read -r url; do
        [ -n "$url" ] || continue
        status="$(head_status "$url")"
        verdict="$(classify_http_status "$status")"
        case "$verdict" in
            MISSING) worst="MISSING" ;;
            LAGGING) [ "$worst" = "PRESENT" ] && worst="LAGGING" ;;
        esac
    done <<URLS
$(resource_urls_for "$1" "$2" "$3" "$4")
URLS
    echo "$worst"
}

local_check_one() {  # kind group artifact version
    local pom_path jar_path
    pom_path="$(local_artifact_path "$2" "$3" "$4" pom)"
    [ -f "$pom_path" ] || { echo "MISSING"; return; }
    if [ "$1" = "LIBRARY" ]; then
        jar_path="$(local_artifact_path "$2" "$3" "$4" jar)"
        [ -f "$jar_path" ] || { echo "MISSING"; return; }
    fi
    echo "PRESENT"
}

poll_artifacts() {  # version
    local version="$1" deadline now wait i pending_names pending_count check
    deadline=$(( $(date +%s) + TIMEOUT_SECONDS ))
    wait="$INITIAL_BACKOFF_SECONDS"
    while :; do
        pending_names=""
        pending_count=0
        for i in "${!CHECK_KIND[@]}"; do
            [ "${CHECK_STATUS[$i]}" = "PRESENT" ] && continue
            if [ "$LOCAL_REHEARSAL" = 1 ]; then
                check="$(local_check_one "${CHECK_KIND[$i]}" "${CHECK_GROUP[$i]}" "${CHECK_ARTIFACT[$i]}" "$version")"
            else
                check="$(remote_check_one "${CHECK_KIND[$i]}" "${CHECK_GROUP[$i]}" "${CHECK_ARTIFACT[$i]}" "$version")"
            fi
            CHECK_STATUS[$i]="$check"
            if [ "$check" != "PRESENT" ]; then
                pending_count=$(( pending_count + 1 ))
                pending_names="$pending_names ${CHECK_GROUP[$i]}:${CHECK_ARTIFACT[$i]}($check)"
            fi
        done
        [ "$pending_count" -eq 0 ] && return 0
        [ "$LOCAL_REHEARSAL" = 1 ] && return 0
        now="$(date +%s)"
        [ "$now" -ge "$deadline" ] && return 0
        echo ">> not yet propagated, $pending_count pending, retrying in ${wait}s:$pending_names" >&2
        sleep "$wait"
        wait=$(( wait * 2 ))
        [ "$wait" -gt "$INTERVAL_SECONDS" ] && wait="$INTERVAL_SECONDS"
    done
}

# ---------------------------------------------------------------------------------------
# Consumer smoke test — the exact recipe documentation/first-10-minutes.md documents and
# proves works: one service interface, one implementation, one JUnit 5 test wrapping it with
# NarrativeTraceProxy. Real mode resolves the plugin from the Gradle Plugin Portal and the
# libraries from Maven Central; --local-rehearsal resolves both from mavenLocal() instead.
# Either way the nested build runs with its own isolated Gradle user home (`-g`), never this
# repository's own — a leftover cache entry must never let a stale artifact pass for a fresh
# one.
# ---------------------------------------------------------------------------------------

SMOKE_VERDICT="SKIPPED"
SMOKE_DETAIL=""

write_smoke_project() {  # dir version plugin_repo library_repo
    local dir="$1" version="$2" plugin_repo="$3" library_repo="$4"
    mkdir -p "$dir/src/main/java/com/example/orders" "$dir/src/test/java/com/example/orders"
    cat >"$dir/settings.gradle.kts" <<SETTINGS
pluginManagement {
    repositories {
        $plugin_repo
    }
}
rootProject.name = "nt-verify-publication-smoke"
SETTINGS
    cat >"$dir/build.gradle.kts" <<BUILD
plugins {
    java
    id("ai.narrativetrace") version "$version"
}
repositories {
    $library_repo
}
BUILD
    cat >"$dir/src/main/java/com/example/orders/OrderService.java" <<'JAVA'
package com.example.orders;

public interface OrderService {
    String placeOrder(String customerId, String productId, int quantity);
}
JAVA
    cat >"$dir/src/main/java/com/example/orders/DefaultOrderService.java" <<'JAVA'
package com.example.orders;

public class DefaultOrderService implements OrderService {
    @Override
    public String placeOrder(String customerId, String productId, int quantity) {
        return "ORD-" + customerId + "-" + productId + "-" + quantity;
    }
}
JAVA
    cat >"$dir/src/test/java/com/example/orders/OrderServiceTest.java" <<'JAVA'
package com.example.orders;

import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(NarrativeTraceExtension.class)
class OrderServiceTest {
    @Test
    void customerPlacesOrder(NarrativeContext context) {
        OrderService service = NarrativeTraceProxy.trace(
            new DefaultOrderService(), OrderService.class, context);

        service.placeOrder("C-1234", "SKU-KB", 2);
    }
}
JAVA
}

run_smoke_test() {  # version
    local version="$1" work_dir gradle_home plugin_repo library_repo exit_code trace_file
    work_dir="$(mktemp -d "${TMPDIR:-/tmp}/nt-verify-publication.XXXXXX")"
    gradle_home="$(mktemp -d "${TMPDIR:-/tmp}/nt-verify-publication-home.XXXXXX")"
    if [ "$LOCAL_REHEARSAL" = 1 ]; then
        plugin_repo="mavenLocal()"
        library_repo="mavenLocal()"
    else
        plugin_repo="gradlePluginPortal()"
        library_repo="mavenCentral()"
    fi
    write_smoke_project "$work_dir" "$version" "$plugin_repo" "$library_repo"

    exit_code=0
    "$REPO_ROOT/gradlew" --project-dir "$work_dir" --gradle-user-home "$gradle_home" test \
        >"$work_dir/gradle-output.log" 2>&1 || exit_code=$?

    trace_file="$work_dir/build/narrativetrace/traces/OrderServiceTest/customer_places_order.md"
    if [ "$exit_code" -eq 0 ] && [ -f "$trace_file" ]; then
        SMOKE_VERDICT="PASSED"
        SMOKE_DETAIL="build passed, trace file present: $trace_file"
    elif [ "$exit_code" -ne 0 ]; then
        # Left on disk on purpose: SMOKE_DETAIL points at gradle-output.log, and deleting the
        # only evidence of why a smoke test failed would make this branch worse than useless.
        # The operator cleans these up; a script polling a release is not the place for that.
        SMOKE_VERDICT="FAILED"
        SMOKE_DETAIL="build failed (exit $exit_code) — see $work_dir/gradle-output.log"
        return
    else
        SMOKE_VERDICT="FAILED"
        SMOKE_DETAIL="build passed but no trace file at $trace_file"
        return
    fi
    rm -rf "$work_dir" "$gradle_home"
}

# ---------------------------------------------------------------------------------------
# Report
# ---------------------------------------------------------------------------------------

ALL_PRESENT=1

# Sets the global ALL_PRESENT rather than returning a boolean: this function's job is to print
# the table, and folding the overall verdict into its exit status would make that exit status
# significant under `set -e` for whoever calls it — a footgun this script's own `resource_urls_for`
# fix above exists to avoid. `main` reads ALL_PRESENT explicitly instead.
print_report() {  # version
    local i status
    ALL_PRESENT=1
    echo
    printf '%-8s %-45s %s\n' "KIND" "COORDINATE" "STATUS"
    for i in "${!CHECK_KIND[@]}"; do
        status="${CHECK_STATUS[$i]}"
        if [ "$status" != "PRESENT" ]; then
            ALL_PRESENT=0
        fi
        printf '%-8s %-45s %s\n' "${CHECK_KIND[$i]}" "${CHECK_GROUP[$i]}:${CHECK_ARTIFACT[$i]}:$1" "$status"
    done
    echo
    echo "Smoke test: $SMOKE_VERDICT${SMOKE_DETAIL:+ ($SMOKE_DETAIL)}"
}

dry_run_report() {  # version
    local i version="$1"
    echo "Dry run — no network calls, no smoke test. Would check:"
    echo
    for i in "${!CHECK_KIND[@]}"; do
        resource_urls_for "${CHECK_KIND[$i]}" "${CHECK_GROUP[$i]}" "${CHECK_ARTIFACT[$i]}" "$version" \
            | while read -r url; do echo "  ${CHECK_KIND[$i]} $url"; done
    done
    echo
    echo "Smoke test would apply id(\"ai.narrativetrace\") version \"$version\" from" \
        "$([ "$LOCAL_REHEARSAL" = 1 ] && echo mavenLocal || echo "the Gradle Plugin Portal")."
}

# ---------------------------------------------------------------------------------------
main() {
    parse_args "$@"
    load_checks
    [ "${#CHECK_KIND[@]}" -gt 0 ] || { echo "ERROR: printPublishedCoordinates listed no modules." >&2; exit 1; }

    if [ "$DRY_RUN" = 1 ]; then
        dry_run_report "$VERSION"
        exit 0
    fi

    poll_artifacts "$VERSION"
    run_smoke_test "$VERSION"
    print_report "$VERSION"

    if [ "$ALL_PRESENT" -eq 1 ] && [ "$SMOKE_VERDICT" = "PASSED" ]; then
        exit 0
    fi
    exit 1
}

# Guard so sourcing this file (VerifyPublicationScriptTest sources it to unit-test the pure
# functions above) does not also run main: executed directly, BASH_SOURCE[0] and $0 are the
# same path; sourced, $0 is still the caller's.
if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
    main "$@"
fi
