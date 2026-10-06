#!/usr/bin/env bash
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
#
# Nightly contract gate — what this repository's documentation CLAIMS, checked against what a
# consumer actually downloads. Resolves the published version the same way
# scripts/verify-publication.sh does (newest v* tag reachable from HEAD, else Maven Central's
# maven-metadata.xml <latest> for narrativetrace-core), installs it into a FRESH temp dir (an
# isolated Gradle user home — never this checkout's own, never mavenLocal) and runs
# contract-probe/'s `runContract` against it. A future layer 2 (the sixty-seconds cold walk,
# documentation/sixty-seconds.md, "not yet built") reuses the same resolve-then-fresh-install shape
# rather than inventing a second one.
#
# THE CONTRACT IS THE WORKING TREE'S. Development is trunk-based and publishing is one step: the
# public snapshot and the artifacts go out together from the same commit, so main IS the published
# code. There is no "main ahead of published" gap for a tag-time read to close, and so no tag
# lookup here — documentation/contract.yaml as it stands describes the code the release published.
#
# Usage:
#   scripts/contract-check.sh [<version>] [options]
#
# <version> is optional, exactly like verify-publication.sh: omit it (as the nightly schedule
# does) and the LAST PUBLISHED version is verified. Pass it explicitly to check exactly that
# version instead (a manual rehearsal, or a specific past release).
#
# Options:
#   --dry-run   Print the resolved version and the command that would run; no network calls beyond
#               version resolution itself, no Gradle build.
#   -h, --help  Print this usage text and exit 0.
#
# Exit status: 0 only if every contract.yaml entry HOLDS (contract-probe's own exit code,
# propagated through Gradle's runContract task failing on any FAILS verdict).
set -euo pipefail
[ -n "${BASH_VERSION:-}" ] || { echo "ERROR: run with bash, not another shell." >&2; exit 1; }

cd "$(dirname "${BASH_SOURCE[0]}")/.."
REPO_ROOT="${REPO_ROOT:-$(pwd)}"

usage() {
    cat <<'USAGE'
Usage: scripts/contract-check.sh [<version>] [options]

<version> is optional: omitted, the LAST PUBLISHED version is checked (newest v* tag reachable
from HEAD, else Maven Central's maven-metadata.xml <latest> for narrativetrace-core) — never this
checkout's own gradle.properties version.

The contract checked is documentation/contract.yaml as it stands in the working tree: main is the
published code, so an entry describes the code the release published.

Options:
  --dry-run   print the resolved version and the command that would run; no Gradle build.
  -h, --help  print this text and exit 0.
USAGE
}

DRY_RUN=0
VERSION=""
while [ $# -gt 0 ]; do
    case "$1" in
        --dry-run) DRY_RUN=1 ;;
        -h|--help) usage; exit 0 ;;
        -*) echo "ERROR: unknown argument '$1'" >&2; exit 1 ;;
        *)
            [ -z "$VERSION" ] || { echo "ERROR: unexpected extra argument '$1'" >&2; exit 1; }
            VERSION="$1"
            ;;
    esac
    shift
done

# Reuses verify-publication.sh's own version-resolution functions (resolve_version,
# latest_tag_version, latest_metadata_version) rather than a second copy of the same logic — the
# sourcing guard at that script's own bottom keeps its main() from also running here. Sourced from
# THIS script's directory, not from $REPO_ROOT: the sibling script always lives next to this one,
# while REPO_ROOT names the repository whose tags and contract are being read (a test points it at
# a throwaway fixture).
# shellcheck source=scripts/verify-publication.sh
source "$(dirname "${BASH_SOURCE[0]}")/verify-publication.sh"

if [ -z "$VERSION" ]; then
    resolve_version || exit 1
    echo ">> no <version> given — checking the last published version: $VERSION ($VERSION_SOURCE)" >&2
fi

# A fresh temp dir for BOTH the Gradle user home (so a stale local cache entry, or a previous
# mavenLocal publish of this exact version, can never stand in for the real registry answer) and
# the JSON result file — never this checkout's own build/ or ~/.gradle.
WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT
GRADLE_HOME="$WORKDIR/gradle-home"
RESULT_JSON="$WORKDIR/contract-result.json"
CONTRACT_YAML="$REPO_ROOT/documentation/contract.yaml"

if [ "$DRY_RUN" = 1 ]; then
    echo "Dry run — would install $VERSION into a fresh temp dir and run:"
    echo "  contract-probe/gradlew -g $GRADLE_HOME runContract -PcontractVersion=$VERSION" \
        "-PcontractYaml=$CONTRACT_YAML -Pout=$RESULT_JSON"
    exit 0
fi

echo ">> checking documentation/contract.yaml (the working tree's) against the published artifacts" >&2
echo ">> installing ai.narrativetrace:*:$VERSION into a fresh Gradle user home and running contract-probe" >&2

set +e
"$REPO_ROOT/contract-probe/gradlew" -g "$GRADLE_HOME" --console=plain --no-daemon \
    -p "$REPO_ROOT/contract-probe" runContract \
    -PcontractVersion="$VERSION" \
    -PcontractYaml="$CONTRACT_YAML" \
    -Pout="$RESULT_JSON"
STATUS=$?
set -e

if [ -f "$RESULT_JSON" ]; then
    echo ">> result JSON: $RESULT_JSON" >&2
    cat "$RESULT_JSON"
    echo
fi

exit "$STATUS"
