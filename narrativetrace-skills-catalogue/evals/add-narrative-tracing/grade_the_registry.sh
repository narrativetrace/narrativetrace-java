#!/bin/sh
# SPDX-License-Identifier: BUSL-1.1
# Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four years from publication; Change License: Apache-2.0
# Copyright (c) 2026 Empower Agile
# World-state verifier for a REGISTRY case: what the registry left in the project, and what the
# installer then did with it. Shared by both registry cases; run with cwd set to the scaffolded
# fixture copy, from a case's graders/verify.sh, with $1 naming the registry that delivered the
# pages (claude-marketplace | npx-skills) and $2 the service boundary that fixture defines.
#
# The published prompt's own promises are NOT graded here — grade_the_prompt.sh is the whole of
# that, and it runs last. What is graded here is the three things only a registry case can be
# asked, in the order that fails cheapest first:
#
#   1. No vendor page was written THROUGH a symbolic link. `npx skills add` makes
#      .claude/skills/<name> a link to .agents/skills/<name>, so writing the vendor flavour through
#      it would destroy the open-standard page it had just adopted. The invariant is keyed on the
#      one line the two flavours differ by: `allowed-tools` is in the vendor page and in no other.
#   2. The installer refused nothing, so no `--force` was needed. A registry's pages are our own
#      rendered pages, so an identical one is adopted rather than refused — and the refusal a
#      pre-adoption installer produced is exactly what a reader would have reached for --force over.
#   3. The registry's own files are still the registry's: its lock file, and the pages it wrote
#      where our installer never looks.
#
# $NARRATIVETRACE_CLI_JAR is the built, zero-dependency narrativetrace-cli jar the runner points at.
set -e

registry="$1"
service="$2"
if [ -z "$registry" ] || [ -z "$service" ]; then
  echo "usage: grade_the_registry.sh <claude-marketplace|npx-skills> <TracedServiceName>" >&2
  exit 1
fi
here=$(dirname "$0")

# ------------------------------------------------------------------------------------------------
# 1. Nothing was written through a link.
# ------------------------------------------------------------------------------------------------
for page in .agents/skills/*/SKILL.md; do
  [ -f "$page" ] || continue
  if grep -q "^allowed-tools:" "$page"; then
    echo "$page carries the VENDOR flavour's allowed-tools line — the open-standard page was" >&2
    echo "overwritten, which is what writing through .claude/skills's symbolic link does" >&2
    exit 1
  fi
done

for entry in .claude/skills/*; do
  [ -e "$entry" ] || [ -L "$entry" ] || continue
  if [ -L "$entry" ]; then
    target=$(readlink "$entry")
    case "$target" in
      *.agents/skills/*)
        echo "no write-through: $entry is still the registry's link to $target" ;;
      *)
        echo "$entry is a symbolic link to $target, which is not the open-standard page the" >&2
        echo "registry pointed it at — something replaced a link the installer must refuse" >&2
        exit 1 ;;
    esac
  elif ! grep -q "^allowed-tools:" "$entry/SKILL.md"; then
    echo "$entry/SKILL.md is a real page in the vendor's own directory but carries no" >&2
    echo "allowed-tools line — the wrong flavour was installed there" >&2
    exit 1
  else
    echo "no write-through: $entry holds the vendor flavour, as a real directory of its own"
  fi
done

# ------------------------------------------------------------------------------------------------
# 2. The installer refuses nothing on the tree the registry made, and needs no --force.
#    A dry run writes nothing and always exits 0, so the plan itself is what is read.
# ------------------------------------------------------------------------------------------------
plan=$(java -jar "$NARRATIVETRACE_CLI_JAR" init --dry-run --json)

echo "$plan" | python3 -c '
import json, sys
plan = json.load(sys.stdin)
actions = plan.get("actions", [])
refused = [a for a in actions if a.get("status") == "refused"]
if refused:
    print("the installer refuses actions on the registry-installed tree, so a reader would reach",
          "for --force:", refused, file=sys.stderr)
    sys.exit(1)
print("no refusals: the plan is", ", ".join(sorted({a["kind"] for a in actions})) or "empty")
'

# ------------------------------------------------------------------------------------------------
# 3. What this particular registry leaves behind, and what the installer may never touch of it.
# ------------------------------------------------------------------------------------------------
case "$registry" in
  npx-skills)
    test -f skills-lock.json || {
      echo "the registry tool's own lock file is gone — the installer may remove only what it" >&2
      echo "wrote itself" >&2
      exit 1
    }
    python3 - <<'PY'
import json, pathlib, sys
lock = json.loads(pathlib.Path("skills-lock.json").read_text())
names = sorted(lock.get("skills", {}))
if not names:
    print("skills-lock.json names no skill at all: the registry installed nothing", file=sys.stderr)
    sys.exit(1)
missing = [n for n in names if not pathlib.Path(".agents/skills", n, "SKILL.md").is_file()]
if missing:
    print("the lock file names skills with no page left on disk:", missing, file=sys.stderr)
    sys.exit(1)
# The vendor side the tool symlinked has to be there too — as the link it made, or as the real
# directory the installer replaced it with. Gone means somebody removed what the registry left.
vendorless = [n for n in names if not pathlib.Path(".claude/skills", n).exists()
              and not pathlib.Path(".claude/skills", n).is_symlink()]
if vendorless:
    print("the registry pointed .claude/skills at these and now nothing is there:", vendorless,
          file=sys.stderr)
    sys.exit(1)
print("the registry's", len(names), "pages and its lock file are intact:", ", ".join(names))
PY
    # The third place this tool writes: real pages under agent/skills, reflowed by the tool itself.
    # Our installer never looks there, so it must never have stamped one.
    for page in agent/skills/*/SKILL.md; do
      [ -f "$page" ] || continue
      if grep -q "installed by narrativetrace init from" "$page"; then
        echo "$page carries our provenance line, but it is the registry tool's own copy in a" >&2
        echo "directory the installer does not own" >&2
        exit 1
      fi
    done
    ;;
  claude-marketplace)
    test ! -f skills-lock.json || {
      echo "a user-scope plugin install writes no lock file into the project, so this tree was" >&2
      echo "not made by the registry this case names" >&2
      exit 1
    }
    # User scope: the plugin's pages live in the agent's own configuration, never in the project.
    # So every page that IS in the project has to be one the installer wrote and stamped.
    for page in .agents/skills/*/SKILL.md .claude/skills/*/SKILL.md; do
      [ -f "$page" ] || continue
      grep -q "installed by narrativetrace init from" "$page" || {
        echo "$page is in the project without the installer's provenance line, and a user-scope" >&2
        echo "plugin install puts nothing in the project — where did it come from?" >&2
        exit 1
      }
    done
    echo "user scope: the project carries no page the installer did not write"
    ;;
  *)
    echo "unknown registry \"$registry\" — the graders know claude-marketplace and npx-skills" >&2
    exit 1
    ;;
esac

# ------------------------------------------------------------------------------------------------
# 4. Everything the published prompt itself promises, graded exactly as the other cases grade it.
# ------------------------------------------------------------------------------------------------
sh "$here/grade_the_prompt.sh" "$service"
