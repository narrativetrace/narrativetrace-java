#!/usr/bin/env bash
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
#
# Answers one question the release pipeline needs before it decides whether to publish the
# Gradle plugin again: is <version> already live on the Gradle Plugin Portal — asked of the
# Portal itself, never its `/m2` proxy. `/m2` mirrors Maven Central: the instant the libraries
# step publishes the marker POM there, `/m2` answers with a 303 redirect to Central's own copy,
# whether or not the Portal has ever run `publishPlugins` for this coordinate. `curl -f` does not
# treat a 3xx as failure, so a guard built on `/m2` reads that redirect as "already published"
# and skips forever — the Portal publish step then never runs, and the credentials that gate it
# are never exercised (release rule 2: a graceful skip must prove it has ever run).
#
# The real signal is the plugin's own page for that exact version:
#   https://plugins.gradle.org/plugin/<id>/<version>
# The Portal serves this only for a version it actually holds, and the page carries two hidden
# fields naming what it served: `id="pluginIdValue"` (the plugin id) and `id="plugin-id-version"`
# (the version). A 200 whose body names both is PUBLISHED. Anything else the Portal answers —
# 400 (unknown plugin or unknown version there), or a redirect (blocked from following by
# `--max-redirs 0`, so a 303 can never be mistaken for success the way `/m2`'s was) — is
# NOT_PUBLISHED. A request that never got a real answer at all (DNS failure, connection refused,
# timeout — curl's own exit code is nonzero) is kept apart from both: the question was never
# actually asked, so the caller must fail loudly rather than guess "not published" and republish
# over a live release, or guess "published" and skip a release that never went out.
#
# Usage: scripts/plugin-portal-published.sh <version>
#
# Overridable for tests, the same idiom scripts/verify-publication.sh uses:
#   PLUGIN_ID                      (default: ai.narrativetrace)
#   GRADLE_PLUGIN_PORTAL_BASE      (default: https://plugins.gradle.org)
#   CURL_MAX_TIME_SECONDS          (default: 30)
#
# Exit status:
#   0  PUBLISHED     — <version> is live on the Portal; the caller should skip publishPlugins.
#   1  NOT_PUBLISHED — the Portal has no such release; the caller should run publishPlugins.
#   2  ERROR         — the question could not be answered; never treat this as either of the above.
set -euo pipefail
# Portability guard: bash 3.2+ (macOS stock bash), matching every other script here.
[ -n "${BASH_VERSION:-}" ] || { echo "ERROR: run with bash, not another shell." >&2; exit 2; }

PLUGIN_ID="${PLUGIN_ID:-ai.narrativetrace}"
GRADLE_PLUGIN_PORTAL_BASE="${GRADLE_PLUGIN_PORTAL_BASE:-https://plugins.gradle.org}"
CURL_MAX_TIME_SECONDS="${CURL_MAX_TIME_SECONDS:-30}"

usage() {
    cat <<'USAGE'
Usage: scripts/plugin-portal-published.sh <version>

Asks the Gradle Plugin Portal itself (never its /m2 proxy) whether <version> of the plugin is
published. Exit 0 PUBLISHED, exit 1 NOT_PUBLISHED, exit 2 ERROR (the question could not be
answered — a network failure, never read as either verdict).
USAGE
}

# ---------------------------------------------------------------------------------------
# Pure functions: no network, no filesystem. Sourced directly by
# PluginPortalPublishedScriptTest (narrativetrace-build-tests), the same way
# VerifyPublicationScriptTest sources scripts/verify-publication.sh.
# ---------------------------------------------------------------------------------------

portal_check_url() {  # base id version
    printf '%s/plugin/%s/%s\n' "$1" "$2" "$3"
}

# Pulls the text content of one `<p id="...">...</p>` hidden field out of the Portal's plugin
# page. Both fields this script reads (pluginIdValue, plugin-id-version) are emitted with no
# markup inside them, so a non-greedy match to the next `<` is exact, not a best-effort scrape.
extract_field() {  # body field_id
    printf '%s' "$1" | sed -n 's:.*<p id="'"$2"'"[^>]*>\([^<]*\)</p>.*:\1:p' | head -n1
}

# curl's own exit code decides ERROR vs a real verdict FIRST: a nonzero exit means curl never
# got a full response (DNS failure, connection refused, timeout), so status/body below are not
# evidence of anything the Portal actually said. Only once a real response came back does the
# status/body pair decide PUBLISHED vs NOT_PUBLISHED — a 200 whose body names both the plugin id
# and the version is the only PUBLISHED case; a redirect status (curl was run with
# --max-redirs 0, so a 3xx is reported here rather than followed) or a 400 both fall through to
# NOT_PUBLISHED, same as any other non-200.
classify_response() {  # curl_exit status body plugin_id version
    local curl_exit="$1" status="$2" body="$3" plugin_id="$4" version="$5"
    [ "$curl_exit" -eq 0 ] || { echo "ERROR"; return; }
    if [ "$status" = "200" ] \
        && [ "$(extract_field "$body" pluginIdValue)" = "$plugin_id" ] \
        && [ "$(extract_field "$body" plugin-id-version)" = "$version" ]; then
        echo "PUBLISHED"
    else
        echo "NOT_PUBLISHED"
    fi
}

# ---------------------------------------------------------------------------------------
# Network call — isolated so the pure classification above can be tested without it.
# ---------------------------------------------------------------------------------------

# Sets RESPONSE_STATUS / RESPONSE_BODY / RESPONSE_CURL_EXIT. --max-redirs 0: a redirect is
# reported as its own status code, never silently followed and misread as whatever it points at
# (the exact shape that made the /m2 proxy look like a "yes" for a plugin the Portal had never
# heard of). A real `if`, not a bare statement, so a curl failure under `set -e` is captured
# here rather than aborting the script — the whole point is to turn that failure into ERROR,
# not crash before classify_response ever runs.
fetch_portal_page() {  # url
    local url="$1" tmp
    tmp="$(mktemp)"
    if RESPONSE_STATUS="$(curl -s -o "$tmp" -w '%{http_code}' --max-redirs 0 \
        --max-time "$CURL_MAX_TIME_SECONDS" "$url")"; then
        RESPONSE_CURL_EXIT=0
    else
        RESPONSE_CURL_EXIT=$?
        RESPONSE_STATUS="000"
    fi
    RESPONSE_BODY="$(cat "$tmp")"
    rm -f "$tmp"
}

# ---------------------------------------------------------------------------------------
main() {
    [ $# -eq 1 ] || { usage >&2; exit 2; }
    case "$1" in -h|--help) usage; exit 0 ;; esac
    local version="$1" url verdict
    [ -n "$version" ] || { echo "ERROR: empty version." >&2; exit 2; }

    url="$(portal_check_url "$GRADLE_PLUGIN_PORTAL_BASE" "$PLUGIN_ID" "$version")"
    fetch_portal_page "$url"
    verdict="$(classify_response "$RESPONSE_CURL_EXIT" "$RESPONSE_STATUS" "$RESPONSE_BODY" "$PLUGIN_ID" "$version")"

    case "$verdict" in
        PUBLISHED)
            echo "$PLUGIN_ID $version is published on the Gradle Plugin Portal ($url)."
            exit 0
            ;;
        NOT_PUBLISHED)
            echo "$PLUGIN_ID $version is NOT on the Gradle Plugin Portal yet (status $RESPONSE_STATUS from $url)."
            exit 1
            ;;
        *)
            echo "ERROR: could not reach the Gradle Plugin Portal to check $PLUGIN_ID $version ($url)." >&2
            exit 2
            ;;
    esac
}

# Guard so sourcing this file (PluginPortalPublishedScriptTest sources it to unit-test the pure
# functions above) does not also run main — same idiom as scripts/verify-publication.sh.
if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
    main "$@"
fi
