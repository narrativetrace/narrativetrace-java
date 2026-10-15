# Sporadic eval quota

The cheaper Tier B lanes (Codex, Antigravity, Cursor) run under the sporadic policy in `evals/README.md`:
never scheduled, promotion points only, quota-guarded. `EvalRunner` reads this file before every
codex/antigravity/cursor trial and refuses to start once the current ISO week's spend reaches the platform's
weekly allowance below — **there is no override flag**; raise the number here instead. The Claude
lane is not sporadic (it runs on the harness's own regular cadence) and carries no quota row.

## Allowance

Owner-ruled 2026-09-13 (skill-evals-multi-platform ruling): Codex is available now on the basic
plan at the default sporadic-lane allowance. Antigravity replaced the Gemini CLI lane on 2026-10-10
(owner: "use Antigravity instead of Gemini CLI"); its `agy` CLI is installed and signed in on the
owner's workstation, so it carries the same default allowance as Codex. Cursor (owner item, Phase 8)
stays at 0 — skipping every trial with one line — until its `agent` CLI is installed and signed in.

| platform | plan tier | weekly allowance |
|---|---|---|
| codex | basic | 4 |
| antigravity | subscription | 4 |
| cursor | not installed | 0 |

## Spend log

Appended by the runner (`EvalRunner`), one row per sporadic-lane (codex/antigravity/cursor) trial —
Claude-lane trials never append here. `week` is the ISO 8601 week (`QuotaMarkdown.isoWeek()`) the
trial's own date falls in, so a week boundary crossing mid-run is judged the same way twice.

| date | platform | skill | case | week |
|---|---|---|---|---|
| 2026-10-10 | antigravity | narrativetrace-doctor | happy-path | 2026-W41 |
| 2026-10-10 | antigravity | add-narrative-tracing | init-prompt-empty-project | 2026-W41 |
| 2026-10-10 | antigravity | add-narrative-tracing | init-prompt-existing-project | 2026-W41 |
| 2026-10-10 | antigravity | add-narrative-tracing | init-prompt-empty-project | 2026-W41 |
