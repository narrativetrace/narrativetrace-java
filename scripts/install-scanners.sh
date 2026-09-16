#!/usr/bin/env bash
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
#
# Installs the pinned, checksummed security scanners CI (and any developer machine) runs:
# Semgrep by an exact `pip install semgrep==<version>`, and OSV-Scanner by an exact GitHub
# release tag with its binary verified against that release's own published SHA256SUMS file
# before it is trusted anywhere.
#
# Before 2026-09-16 CI installed an unversioned `pip install semgrep` (whatever PyPI resolved
# that run) and downloaded OSV-Scanner's `latest` release with no integrity check at all — two
# different scanners, two different ways a compromised or simply different-behaving binary could
# land unnoticed (build-automation assessment 2026-09-14, Priority 1/4).
#
# The versions live in ONE place — scripts/scanner-versions.env — read by this script and by
# nothing else; the CI pipeline calls this script and carries no scanner version, flag or
# threshold of its own (thin-CI rule; see documentation/security-tooling.md, which also names the
# update procedure). Bump a version there; this script and CI both pick it up with no other
# change.
#
# Usage:
#   scripts/install-scanners.sh [semgrep|osv-scanner|all]   (default: all)
#
# Overridable — for tests to point at a fixture, never for a real run:
#   REPO_ROOT                 defaults to this script's own parent directory
#   SCANNER_VERSIONS_FILE     defaults to <REPO_ROOT>/scripts/scanner-versions.env
#   OSV_SCANNER_RELEASE_BASE  defaults to the real GitHub releases download base
#   OSV_SCANNER_INSTALL_PATH  defaults to /usr/local/bin/osv-scanner
set -euo pipefail

REPO_ROOT="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
SCANNER_VERSIONS_FILE="${SCANNER_VERSIONS_FILE:-$REPO_ROOT/scripts/scanner-versions.env}"
OSV_SCANNER_RELEASE_BASE="${OSV_SCANNER_RELEASE_BASE:-https://github.com/google/osv-scanner/releases/download}"
OSV_SCANNER_INSTALL_PATH="${OSV_SCANNER_INSTALL_PATH:-/usr/local/bin/osv-scanner}"

load_versions() {
    if [ ! -f "$SCANNER_VERSIONS_FILE" ]; then
        echo "install-scanners: no such versions file: $SCANNER_VERSIONS_FILE" >&2
        exit 2
    fi
    # shellcheck disable=SC1090
    source "$SCANNER_VERSIONS_FILE"
    : "${SEMGREP_VERSION:?SEMGREP_VERSION not set in $SCANNER_VERSIONS_FILE}"
    : "${OSV_SCANNER_VERSION:?OSV_SCANNER_VERSION not set in $SCANNER_VERSIONS_FILE}"
}

install_semgrep() {
    load_versions
    echo "install-scanners: pip install semgrep==${SEMGREP_VERSION}"
    pip install --break-system-packages --quiet "semgrep==${SEMGREP_VERSION}"
}

# Downloads the linux_amd64 binary and the release's SHA256SUMS file into $1, verifies the binary
# against the line naming it. Never against a hash typed into this script — that would only move
# the trust problem from "which URL" to "which hash to trust", not remove it; the checksum comes
# from the same immutable, tag-pinned release as the binary itself. A mismatch fails loudly (`set
# -e` plus `sha256sum -c`'s own non-zero exit) and leaves no binary at the install path.
download_and_verify_osv_scanner() {
    local dest_dir="$1"
    load_versions
    local base="$OSV_SCANNER_RELEASE_BASE/$OSV_SCANNER_VERSION"
    echo "install-scanners: downloading osv-scanner ${OSV_SCANNER_VERSION} from $base"
    curl -fsSL "$base/osv-scanner_linux_amd64" -o "$dest_dir/osv-scanner_linux_amd64"
    curl -fsSL "$base/osv-scanner_SHA256SUMS" -o "$dest_dir/osv-scanner_SHA256SUMS"
    (
        cd "$dest_dir"
        grep 'osv-scanner_linux_amd64$' osv-scanner_SHA256SUMS > osv-scanner_linux_amd64.sha256sum
        sha256sum -c osv-scanner_linux_amd64.sha256sum
    )
}

install_osv_scanner() {
    local tmp
    tmp="$(mktemp -d)"
    trap 'rm -rf "$tmp"' RETURN
    download_and_verify_osv_scanner "$tmp"
    install -m 0755 "$tmp/osv-scanner_linux_amd64" "$OSV_SCANNER_INSTALL_PATH"
    echo "install-scanners: installed osv-scanner to $OSV_SCANNER_INSTALL_PATH"
}

main() {
    local target="${1:-all}"
    case "$target" in
        semgrep) install_semgrep ;;
        osv-scanner) install_osv_scanner ;;
        all)
            install_semgrep
            install_osv_scanner
            ;;
        *)
            echo "install-scanners: unknown target '$target' (expected semgrep, osv-scanner, or all)" >&2
            exit 2
            ;;
    esac
}

# Guard so sourcing this file (InstallScannersScriptTest sources it to unit-test the pure
# functions above) does not also run main: executed directly, BASH_SOURCE[0] and $0 are the same
# path; sourced, $0 is still the caller's.
if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
    main "$@"
fi
