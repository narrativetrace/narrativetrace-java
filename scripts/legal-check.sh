#!/usr/bin/env bash
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
#
# Golden-side legal-marker self-check.
#
# This repository is the CANONICAL copy of every legally load-bearing sentence
# in the NarrativeTrace family (the free Java repo is the golden copy). Those sentences live in
# README.md and its three translations, wrapped in machine-anchor markers:
#
#   <!-- legal:<name>:begin -->
#   ...canonical (or, in a translation, translated) text...
#   <!-- legal:<name>:end -->
#
# The marker LINES are always in English (they are anchors, not content); the
# text between them is English in README.md and translated in LEAME.md /
# LEIAME.md / 自述文件.md. Every NarrativeTrace runtime mirrors these regions
# byte-exact per language; this script never talks to another repository, it
# only proves this repo's own copy is internally consistent, and it prints one
# named region's content on request so a sibling repository's own check can
# pull the text it must mirror out of a checkout of this one.
#
# Checks (default mode, no arguments):
#   (a) every `legal:<name>:begin` has a matching `legal:<name>:end`, in
#       order, never nested, never empty, in each of the four files below
#   (b) the same SET of region names appears in all four files
#   (c) LICENSE still contains exactly one `{{VERSION}}` and one
#       `{{CHANGE_DATE}}` placeholder (the per-release fill point
#       scripts/publish-public.sh uses)
#   (d) LICENSE-APACHE is present
#
# Usage:
#   scripts/legal-check.sh                                 # run all checks
#   scripts/legal-check.sh --emit <name>                   # region <name> from README.md (en)
#   scripts/legal-check.sh --emit <name> --lang es|pt|zh   # ... from the matching translation
set -euo pipefail
# Portability guard: bash 3.2+ (macOS stock bash included), same convention as
# scripts/publish-public.sh.
[ -n "${BASH_VERSION:-}" ] || { echo "ERROR: run with bash, not another shell."; exit 1; }
cd "$(dirname "$0")/.."

FILE_en="README.md"
FILE_es="LEAME.md"
FILE_pt="LEIAME.md"
FILE_zh="自述文件.md"

lang_file() {
    case "$1" in
        en) printf '%s' "$FILE_en" ;;
        es) printf '%s' "$FILE_es" ;;
        pt) printf '%s' "$FILE_pt" ;;
        zh) printf '%s' "$FILE_zh" ;;
        *) echo "ERROR: unknown --lang '$1' (want en|es|pt|zh)" >&2; exit 1 ;;
    esac
}

MARKER_RE='^<!-- legal:([A-Za-z0-9_-]+):(begin|end) -->$'

# --- --emit mode -------------------------------------------------------------
# Prints the content between a named region's begin/end markers (exclusive) in
# the requested language's file (default: en / README.md). Exits 1 if the
# region does not exist in that file.
if [ "${1:-}" = "--emit" ]; then
    EMIT_NAME="${2:?--emit requires a region name}"
    EMIT_LANG="en"
    if [ "${3:-}" = "--lang" ]; then
        EMIT_LANG="${4:?--lang requires en|es|pt|zh}"
    elif [ -n "${3:-}" ]; then
        echo "ERROR: unknown argument '$3' (expected --lang)" >&2
        exit 1
    fi
    EMIT_FILE="$(lang_file "$EMIT_LANG")"
    if ! grep -qF "<!-- legal:${EMIT_NAME}:begin -->" "$EMIT_FILE"; then
        echo "ERROR: region '$EMIT_NAME' not found in $EMIT_FILE" >&2
        exit 1
    fi
    awk -v name="<!-- legal:${EMIT_NAME}:begin -->" -v endmark="<!-- legal:${EMIT_NAME}:end -->" '
        $0 == name { on=1; next }
        $0 == endmark { on=0 }
        on { print }
    ' "$EMIT_FILE"
    exit 0
fi

[ $# -eq 0 ] || { echo "ERROR: unknown argument '$1' (expected no arguments, or --emit)" >&2; exit 1; }

# --- (a) marker well-formedness, per file ------------------------------------
# Walks a file line by line tracking at most one open region; writes every
# region name it closes cleanly to $2 (one per line), and reports every
# malformed marker as ERROR: <file>:<line>: ... . Returns 1 iff a malformed
# marker was found.
check_markers() {
    local file="$1" names_out="$2"
    local open_name="" open_line=0 lineno=0 status=0
    : > "$names_out"
    while IFS= read -r line || [ -n "$line" ]; do
        lineno=$((lineno + 1))
        if [[ "$line" =~ $MARKER_RE ]]; then
            local name="${BASH_REMATCH[1]}" kind="${BASH_REMATCH[2]}"
            if [ "$kind" = "begin" ]; then
                if [ -n "$open_name" ]; then
                    echo "ERROR: $file:$lineno: region '$name' begins while '$open_name' (opened at line $open_line) is still open — nesting is not allowed"
                    status=1
                fi
                open_name="$name"
                open_line="$lineno"
            else
                if [ -z "$open_name" ]; then
                    echo "ERROR: $file:$lineno: end marker for '$name' with no open region"
                    status=1
                elif [ "$open_name" != "$name" ]; then
                    echo "ERROR: $file:$lineno: end marker for '$name' does not match open region '$open_name' (opened at line $open_line)"
                    status=1
                    open_name=""
                    open_line=0
                else
                    if [ "$lineno" -le "$((open_line + 1))" ]; then
                        echo "ERROR: $file:$lineno: region '$name' is empty (begin at line $open_line, end at line $lineno)"
                        status=1
                    else
                        echo "$name" >> "$names_out"
                    fi
                    open_name=""
                    open_line=0
                fi
            fi
        fi
    done < "$file"
    if [ -n "$open_name" ]; then
        echo "ERROR: $file: region '$open_name' (opened at line $open_line) is never closed"
        status=1
    fi
    return $status
}

STATUS=0
NAMES_DIR="$(mktemp -d)"
trap 'rm -rf "$NAMES_DIR"' EXIT

i=0
for f in "$FILE_en" "$FILE_es" "$FILE_pt" "$FILE_zh"; do
    i=$((i + 1))
    if [ ! -f "$f" ]; then
        echo "ERROR: $f is missing"
        STATUS=1
        continue
    fi
    check_markers "$f" "$NAMES_DIR/names.$i" || STATUS=1
done
echo ">> Marker well-formedness checked across README.md and its 3 translations."

# --- (b) same region-name set in all four files ------------------------------
REF_NAMES="$(sort -u "$NAMES_DIR/names.1" 2>/dev/null || true)"
i=0
for f in "$FILE_en" "$FILE_es" "$FILE_pt" "$FILE_zh"; do
    i=$((i + 1))
    [ -f "$NAMES_DIR/names.$i" ] || continue
    THIS_NAMES="$(sort -u "$NAMES_DIR/names.$i")"
    if [ "$THIS_NAMES" != "$REF_NAMES" ]; then
        echo "ERROR: region set mismatch — $f has {$(echo "$THIS_NAMES" | tr '\n' ' ' | sed 's/ *$//')} but $FILE_en has {$(echo "$REF_NAMES" | tr '\n' ' ' | sed 's/ *$//')}"
        STATUS=1
    fi
done
echo ">> Region-name set compared across all 4 files: {$(echo "$REF_NAMES" | tr '\n' ' ' | sed 's/ *$//')}"

# --- (c) LICENSE placeholders --------------------------------------------------
# Fill-aware, because this script ships in the public snapshot: the private
# (golden) tree carries the template with exactly one {{VERSION}} and one
# {{CHANGE_DATE}}; a published tree carries them FILLED — a concrete version
# and a concrete "(YYYY-MM-DD for this version)" date, and zero placeholders.
# Either state is correct; a mixture is not. (`grep -c ... || true` — grep
# exits 1 on zero matches, and under `set -euo pipefail` the unguarded pipe
# aborted the whole script exactly when it had something to report.)
if [ -f LICENSE ]; then
    v_count="$(grep -c -F '{{VERSION}}' LICENSE || true)"
    d_count="$(grep -c -F '{{CHANGE_DATE}}' LICENSE || true)"
    if [ "$v_count" = "1" ] && [ "$d_count" = "1" ]; then
        echo ">> LICENSE is the template: exactly one {{VERSION}} and one {{CHANGE_DATE}}."
    elif [ "$v_count" = "0" ] && [ "$d_count" = "0" ] \
        && grep -qE '\([0-9]{4}-[0-9]{2}-[0-9]{2} for this version\)' LICENSE \
        && grep -qE 'version [0-9]+\.[0-9]+\.[0-9]+' LICENSE; then
        echo ">> LICENSE is filled: concrete version and Change Date, no placeholders."
    else
        echo "ERROR: LICENSE is neither a clean template (1+1 placeholders) nor cleanly filled (0+0 with concrete version and date): VERSION=$v_count CHANGE_DATE=$d_count"
        STATUS=1
    fi
else
    echo "ERROR: LICENSE is missing"
    STATUS=1
fi

# --- (d) LICENSE-APACHE present ------------------------------------------------
if [ -f LICENSE-APACHE ]; then
    echo ">> LICENSE-APACHE present."
else
    echo "ERROR: LICENSE-APACHE is missing"
    STATUS=1
fi

if [ "$STATUS" -eq 0 ]; then
    echo "legal-check: OK — markers well-formed and consistent, both licence files intact."
else
    echo "legal-check: FAILED — see ERROR lines above."
fi
exit $STATUS
