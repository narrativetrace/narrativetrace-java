# Agent skills

*(since 0.2.2)*

NarrativeTrace ships **skills**: agent-loadable procedures that run tested commands and gate
completion on a `verify` step, rather than docs an agent might or might not read. A skill is thin
by design — the checking, diagnosis, or generation logic lives in tested library code; the skill's
own job is knowing when to act, invoking that tested code, and interpreting the result in context.

## Two skills: setup and diagnosis

- **`add-narrative-tracing`** — installs NarrativeTrace into a project and gets it to a first
  trace: install with the real toolchain, wrap a class with `NarrativeTraceProxy.trace`, render
  and run the first trace, then wire a real logger (`narrativetrace-slf4j` plus Logback). Ends by
  running `narrativetrace-doctor` and handing off — the seam between the two skills.
- **`narrativetrace-doctor`** — diagnosis only, and **read-only**: it never edits, generates, or
  deletes a file. Runs the tested CLI, reads its report, and walks through the parts a plain CLI
  output can't cover on its own: proving redaction in a test, reading a rendered trace before
  asserting against it, and the approval-trace flow (flagged unstudied — its own eval cell is
  still pending).

They compose: a brand-new project starts with `add-narrative-tracing`; a project that already has
NarrativeTrace installed, where something isn't working, starts with `narrativetrace-doctor`.
Either path ends at the doctor — it owns diagnosis from there. A later skill will own generation
(writing the redaction-proof test the doctor can only ask you to add today).

## Installing them

- **Claude Code**: rendered `SKILL.md` files live at
  [`.claude/skills/narrativetrace-doctor/`](../.claude/skills/narrativetrace-doctor/SKILL.md) and
  [`.claude/skills/add-narrative-tracing/`](../.claude/skills/add-narrative-tracing/SKILL.md) in
  this repository. Copy either directory into your own project's `.claude/skills/<name>/` and
  Claude picks it up on its own, invokable by name (`narrativetrace-doctor` /
  `add-narrative-tracing`) directly.
- **Codex**: the same catalogue also renders
  [`.agents/skills/narrativetrace-doctor/`](../.agents/skills/narrativetrace-doctor/SKILL.md) and
  [`.agents/skills/add-narrative-tracing/`](../.agents/skills/add-narrative-tracing/SKILL.md) — the
  repo-checked-in layout Codex CLI documents for a project's own skills. Its frontmatter carries
  only `name` and `description` (Codex documents no `when_to_use` or `allowed-tools` keys); the
  page body is otherwise identical. Copy either directory into your own project's
  `.agents/skills/<name>/` and Codex discovers it the same way.
- **Any agent, any platform**: every agent that reads `AGENTS.md` sees the always-on pointer this
  repository's own `AGENTS.md` carries between its `<!-- narrativetrace:skills:start -->` markers
  — both skills' names and descriptions, so an agent that never thought to look still knows they
  exist.
- **Gemini** runs against the same catalogue on a sporadic, quota-guarded schedule (see
  [Tier B — LLM trials](../narrativetrace-skills/evals/README.md)) rather than every commit; it has
  no rendered layout of its own yet — copying the Claude or Codex files above is the closest path
  today.

## How they're built

Neither skill is ever hand-edited. `narrativetrace-skills/src/main/java/ai/narrativetrace/skills/catalogue/AddNarrativeTracingSkill.java`
and `.../NarrativeTraceDoctorSkill.java` are the two sources of truth; their typed steps render
`.claude/skills/add-narrative-tracing/SKILL.md`, `.claude/skills/narrativetrace-doctor/SKILL.md`,
the Codex mirrors at `.agents/skills/add-narrative-tracing/SKILL.md` and
`.agents/skills/narrativetrace-doctor/SKILL.md`, and this repository's own `AGENTS.md` section — a
drift test (`RenderDriftTest`, wired into `./gradlew check`) fails the build the moment any of the
five drifts from the typed source. A skill's rendered `name:` is always its canonical name, never a
shortened "claude segment" — a repo-checked-in `.claude/skills/` or `.agents/skills/` directory is a
flat namespace with no plugin prefix to hide behind, so the name must be globally self-identifying
on its own. A second suite (`SkillReplayer`, Tier A2) mechanically replays every command and
machine-checkable `verify` a step names against the `sixty-seconds` fixture, today, deterministic,
no LLM — green means the instructions are literally executable right now, not merely plausible
prose. A Tier A lint keeps private planning-note citations, this repository's own Pro sibling, CI
config filenames, and git SHAs out of both pages: rationale sentences ship, the citation naming the
source does not.

## See also

- [`narrativetrace-cli`](../narrativetrace-cli/) — the `doctor` verb `narrativetrace-doctor` runs
- [Sixty Seconds](sixty-seconds.md) — the install-and-first-trace walkthrough `add-narrative-tracing`'s steps are drawn from
- [What to Commit](what-to-commit.md) — the approval-trace state the doctor's fourth step checks
- [Tier B — LLM trials](../narrativetrace-skills/evals/README.md) — the engine-neutral case layout, the sporadic Codex/Gemini policy, and the promotion matrix
