#!/usr/bin/env bash
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
#
# Post-release marker settle: rewrites `*(since X.Y.Z, unreleased)*` -> `*(since X.Y.Z)*` for a
# version the package registry now actually serves.
#
# The release-publish tooling's own tag-time step performs the identical rewrite, but only inside
# the STAGED snapshot it is about to publish — never in this working tree. So once a release's
# binaries are confirmed live, the private tree still reads "unreleased" for a version that no
# longer is, and the next untagged snapshot would ship that stale word. This script applies the
# same mechanical rewrite here, once, right after a release's registry publish is confirmed —
# never before (a settle must never run ahead of the publish it settles). Mechanical, never
# editorial: it only ever touches markers whose own cited version is the one just confirmed live.
#
# Usage: scripts/settle-markers.sh <version>
#
# What it does, in order:
#   1. refuses unless <version> is a plain X.Y.Z release version and the working tree is clean;
#   2. refuses unless ai.narrativetrace:narrativetrace-core:<version> is present on the registry
#      (offline, or not yet synced, both refuse with a clear message — never a guess);
#   3. rewrites every `(since <version>, unreleased)*` marker to `(since <version>)*` across
#      documentation/**/*.md, documentation/**/llms.txt, and the root README.md (translated
#      mirrors of both live under documentation/ and fall out of that same walk) — the same scope
#      and the same perl -0777 slurp pattern (tolerating both Markdown hard-wrap shapes a marker
#      can land on) the tag-time rewrite above uses. Source and test files are never touched: a
#      marker-shaped string there is a fixture, not a doc;
#   4. regenerates documentation/llms.txt's "N behaviour(s) marked unreleased" banner clause from
#      the new (lower) marker count, via the repo's own snippet-sync build task — the single place
#      that count is computed, so this script never re-derives it by hand;
#   5. AFTER that task runs (never before — it can itself rewrite an English page that carries no
#      marker at all, e.g. re-embedding timing digits): restamps the blob-hash portion (never the
#      translated/reviewed dates) of every translated mirror whose English source this whole run
#      actually changed — the marker rewrite's own edits and anything the snippet-sync task just
#      changed, both;
#   6. prints the count of markers settled and the files touched; exits non-zero when nothing was
#      settled (a no-op settle run is a mistake, not a success, never a quiet no-op).
set -euo pipefail
# Portability guard: this script targets bash 3.2+ (macOS stock bash included), matching every
# other script here.
[ -n "${BASH_VERSION:-}" ] || { echo "ERROR: run with bash, not another shell." >&2; exit 1; }

# BASH_SOURCE[0], not $0: this file is sourced (not executed) by SettleMarkersScriptTest to unit-
# test its rewrite/restamp mechanics without a release, and inside a sourced file $0 is the
# CALLER's path, not this file's — only BASH_SOURCE[0] is reliable in both cases.
cd "$(dirname "${BASH_SOURCE[0]}")/.."
# Overridable for tests, the same idiom every script here uses: a test points this at a throwaway
# fixture tree instead of the real checkout.
REPO_ROOT="${REPO_ROOT:-$(pwd)}"
# Overridable for tests: a loopback HTTP fixture instead of the real registry.
MAVEN_CENTRAL_BASE="${MAVEN_CENTRAL_BASE:-https://repo1.maven.org/maven2}"
CURL_MAX_TIME_SECONDS=30

usage() {
    echo "Usage: scripts/settle-markers.sh <version>" >&2
}

# True (exit 0) iff $1 is a plain release version (no qualifier, no snapshot marker) — a settle
# only ever cites a real published version, never a moving target.
is_release_version() {
    case "$1" in
        '') return 1 ;;
    esac
    [[ "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]
}

# HEAD-request presence check, the same idiom the registry-verification script's own head_status
# uses — against the bellwether module's version directory rather than its maven-metadata.xml, so
# a release whose freshly-published <latest>/<release> tags have not caught up yet (they can lag
# the artifact files themselves by a beat during the registry's own sync) is not mistaken for one
# that has not published at all.
registry_has_version() {  # version
    local url status
    url="$MAVEN_CENTRAL_BASE/ai/narrativetrace/narrativetrace-core/$1/"
    status="$(curl -s -o /dev/null -w '%{http_code}' --head -L --max-time "$CURL_MAX_TIME_SECONDS" "$url" 2>/dev/null || echo 000)"
    [ "$status" = "200" ]
}

# ---------------------------------------------------------------------------------------
# Rewrite + restamp: pure(ish) tree mutation, no network, no git. Sourced directly by
# SettleMarkersScriptTest so this logic is unit-tested against a synthetic fixture tree without a
# real release, the same technique the sibling publish/verify script tests use for their own real
# script text.
# ---------------------------------------------------------------------------------------

# Rewrites every `(since $2, unreleased)*` marker to `(since $2)*` under $1 (documentation/**/*.md,
# documentation/**/llms.txt, and README.md at the repo root). Sets the globals SETTLED (total
# marker count rewritten) and MARKER_TOUCHED (one touched file path per line) for the caller.
# Exits non-zero, printing why, when SETTLED comes out at zero — nothing settled is a mistake, not
# a success.
rewrite_markers() {  # repo_root version
    local root="$1" version="$2" f before hits
    SETTLED=0
    MARKER_TOUCHED=""
    while IFS= read -r -d '' f; do
        before="$(cat "$f")"
        # perl -0777 (slurp mode), not grep/sed: a marker can survive a Markdown hard-wrap either
        # right after 'since' or after the version comma -- a real newline sitting inside the
        # pattern that neither line-based tool can see once the match spans two lines. \s+/\s*
        # tolerate both wrap points, same tolerance the tag-time rewrite this mirrors uses, so the
        # two agree on what counts as a marker.
        hits="$(printf '%s' "$before" | perl -0777 -ne "my \$c = () = /\\(since\\s+\\Q${version}\\E,\\s*unreleased\\)\\*/g; print \$c" 2>/dev/null)"
        hits="${hits:-0}"
        [ "$hits" -gt 0 ] || continue
        perl -0777 -pi -e "s/\\(since\\s+\\Q${version}\\E,\\s*unreleased\\)\\*/(since ${version})*/g" "$f"
        SETTLED=$((SETTLED + hits))
        MARKER_TOUCHED="$MARKER_TOUCHED$f
"
    done < <(
        find "$root/documentation" -type f \( -name '*.md' -o -name 'llms.txt' \) -print0
        [ -f "$root/README.md" ] && printf '%s\0' "$root/README.md"
    )
    if [ "$SETTLED" -eq 0 ]; then
        echo "ERROR: no unreleased since-marker for $version found anywhere in scope — nothing to settle." >&2
        return 1
    fi
    return 0
}

# Every file under $1 that now differs from HEAD in the working tree, one absolute path per line —
# `git diff --name-only`, resolved to absolute paths. The working tree is verified clean before a
# settle run starts (main's own precondition), so calling this once at the very end — after BOTH
# the marker rewrite above AND the snippetSync banner regeneration below — captures every English
# source the WHOLE run actually touched, not only the ones the marker rewrite itself changed:
# snippetSync can mutate a page carrying no since-marker at all (the incident this fixes: it
# re-embedded timing digits into sixty-seconds.md, which no marker rewrite had ever touched), and
# that page's translated mirrors still need their hash restamped.
changed_english_sources() {  # repo_root
    local root="$1" rel
    git -C "$root" diff --name-only 2>/dev/null | while IFS= read -r rel; do
        [ -n "$rel" ] && printf '%s\n' "$root/$rel"
    done
}

# Restamps the blob-hash portion (only) of every translated mirror under $1/documentation whose
# line-1 `<!-- source: PATH blob HASH ... -->` header names a file $2 (newline-separated absolute
# paths) lists — i.e. every mirror whose English source actually changed. The caller decides what
# "changed" means: `changed_english_sources` above (a full settle run, so snippetSync's own
# changes are covered too) or, in a unit test exercising this function alone, MARKER_TOUCHED
# directly. Sets the global REMARK_RESTAMPED to the count restamped.
restamp_translated_mirrors() {  # repo_root marker_touched
    local root="$1" touched="$2" md line1 old_hash src_rel new_hash
    REMARK_RESTAMPED=0
    [ -n "$touched" ] || return 0
    while IFS= read -r -d '' md; do
        line1="$(head -1 "$md")"
        old_hash="$(printf '%s\n' "$line1" | sed -n 's/^<!-- source: [^ ]* blob \([0-9a-f]\{12\}\) .*-->$/\1/p')"
        [ -n "$old_hash" ] || continue
        src_rel="$(printf '%s\n' "$line1" | sed -n 's/^<!-- source: \([^ ]*\) blob [0-9a-f]\{12\} .*-->$/\1/p')"
        [ -f "$root/$src_rel" ] || continue
        printf '%s\n' "$touched" | grep -qF "$root/$src_rel" || continue
        new_hash="$(git -C "$root" hash-object "$root/$src_rel" | cut -c1-12)"
        [ "$new_hash" = "$old_hash" ] && continue
        { printf '%s\n' "${line1/blob $old_hash/blob $new_hash}"; tail -n +2 "$md"; } > "$md.stamp"
        mv "$md.stamp" "$md"
        REMARK_RESTAMPED=$((REMARK_RESTAMPED + 1))
    done < <(find "$root/documentation" -type f -name '*.md' -print0)
    return 0
}

main() {
    [ $# -eq 1 ] || { usage; exit 1; }
    local version="$1"
    is_release_version "$version" || { echo "ERROR: '$version' is not a plain X.Y.Z release version." >&2; exit 1; }

    [ -z "$(git -C "$REPO_ROOT" status --porcelain)" ] || { echo "ERROR: working tree not clean." >&2; exit 1; }

    echo ">> Registry precondition (ai.narrativetrace:narrativetrace-core:$version) ..."
    if ! registry_has_version "$version"; then
        echo "ERROR: ai.narrativetrace:narrativetrace-core:$version is not on the registry yet" >&2
        echo "       ($MAVEN_CENTRAL_BASE/ai/narrativetrace/narrativetrace-core/$version/ did not answer 200)." >&2
        echo "       A settle must never run ahead of the publish it settles — wait for the release" >&2
        echo "       to land and re-run." >&2
        exit 1
    fi
    echo ">>   present."

    echo ">> Rewriting unreleased since-markers for $version ..."
    rewrite_markers "$REPO_ROOT" "$version"
    local touched_count
    touched_count="$(printf '%s' "$MARKER_TOUCHED" | grep -c . || true)"
    echo ">>   markers settled: $SETTLED (across $touched_count file(s))"

    echo ">> Regenerating the llms.txt unreleased-marker banner (./gradlew snippetSync) ..."
    # Deliberately not --offline: this task's own network fetch (confirming the registry state
    # for the banner line) is the whole point of running it here, right after the registry
    # precondition above already proved a network path exists.
    if ! "$REPO_ROOT/gradlew" -p "$REPO_ROOT" --no-daemon snippetSync; then
        echo "ERROR: ./gradlew snippetSync failed." >&2
        exit 1
    fi

    # After snippetSync, never before: restamping only MARKER_TOUCHED here would miss any page
    # snippetSync itself just changed but that carried no marker of its own — see
    # changed_english_sources's own comment.
    echo ">> Restamping translated mirrors' hash after the marker rewrite and snippetSync ..."
    restamp_translated_mirrors "$REPO_ROOT" "$(changed_english_sources "$REPO_ROOT")"
    echo ">>   headers restamped: $REMARK_RESTAMPED"

    echo ">> Settle complete: $SETTLED marker(s) settled for $version across $touched_count file(s)" \
         "($REMARK_RESTAMPED translated header(s) restamped)."
    printf '%s' "$MARKER_TOUCHED"
}

# Guard so sourcing this file (SettleMarkersScriptTest sources it to unit-test rewrite_markers /
# restamp_translated_mirrors / registry_has_version without a release) does not also run main.
if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
    main "$@"
fi
