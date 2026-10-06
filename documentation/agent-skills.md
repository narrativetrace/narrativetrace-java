# Agent skills

NarrativeTrace ships **skills**: agent-loadable procedures that run tested commands and gate
completion on a `verify` step, rather than docs an agent might or might not read. A skill is thin
by design — the checking, diagnosis, or generation logic lives in tested library code; the skill's
own job is knowing when to act, invoking that tested code, and interpreting the result in context.

## Four skills: setup, diagnosis, clarity, and reporting

- **`add-narrative-tracing`** — installs NarrativeTrace into a project and gets it to a first
  trace: install with the real toolchain, wrap a class with `NarrativeTraceProxy.trace`, render
  and run the first trace, then wire a real logger (`narrativetrace-slf4j` plus Logback). Runs
  `narrativetrace-doctor` and hands off — the seam between the two skills — and closes by previewing
  `narrativetraceInit`, so the next session finds these skills already installed. It previews and
  never applies: a skill that wrote into `AGENTS.md` on its own initiative would be the postinstall
  hook the installer exists to avoid.
- **`narrativetrace-doctor`** — diagnosis only, and **read-only**: it never edits, generates, or
  deletes a file. Runs the tested CLI, reads its report, and walks through the parts a plain CLI
  output can't cover on its own: proving redaction in a test, reading a rendered trace before
  asserting against it, and the approval-trace flow (flagged unstudied — its own eval cell is
  still pending). Every finding it reads also names the skill that fixes that class of problem
  (`skill` in the JSON report, `skill:` under the fix in the text form), so an agent holding a
  report knows which of these procedures to follow next; a finding no skill fixes says so with a
  `null`.
- **`add-narrativetrace-clarity`** — runs a first static naming scan, checks fresh scan artifacts,
  explains ranked issues and per-element notes, and adds an explicit clarity gate only when asked.
- **`narrativetrace-feedback`** — reports a defect in NarrativeTrace itself: a doctor check that is
  wrong or whose fix does not work, a skill step that cannot be followed, wording in the install
  prompt that led somewhere wrong, or the library misbehaving on a correctly configured project.
  The tested verb behind it drafts the report from the project (the install coordinates, the
  doctor's own JSON report, and at most one structural trace), and **refuses to write a report that
  carries a value from your traces** — naming the rule that refused it, so there is something
  specific to fix rather than a warning to ignore. The skill then shows the whole draft and asks
  once whether to file it publicly. It sends nothing anywhere and files nothing without an answer
  given in a turn of its own.

They compose: a brand-new project starts with `add-narrative-tracing`; a project that already has
NarrativeTrace installed, where something isn't working, starts with `narrativetrace-doctor`.
Either path ends at the doctor — it owns diagnosis from there. `add-narrativetrace-clarity` owns
first static naming reports and optional clarity enforcement; it does not install tracing or
harvest a glossary. `narrativetrace-feedback` is where a path ends when the problem turns out to be
ours rather than the project's — the doctor's own closing rule points at it.

## Installing them

One command, in the project you want them in:

```bash
./gradlew narrativetraceInit --diff
```

That PREVIEWS the install and writes nothing. It reports the plan — with `--json`, the same plan as
an envelope a script can gate on:

<!-- snippet: narrativetrace-cli/build/narrativetrace/init-plan.json -->
```json
{
  "carrier": "ai.narrativetrace:narrativetrace-cli:0.2.5",
  "actions": [
    {"kind": "create", "path": ".agents/skills/narrativetrace-doctor/SKILL.md", "status": "planned"},
    {"kind": "create", "path": ".agents/skills/add-narrative-tracing/SKILL.md", "status": "planned"},
    {"kind": "create", "path": ".agents/skills/add-narrativetrace-clarity/SKILL.md", "status": "planned"},
    {"kind": "create", "path": ".agents/skills/narrativetrace-feedback/SKILL.md", "status": "planned"},
    {"kind": "create", "path": "AGENTS.md", "status": "planned"}
  ],
  "exitCode": 0
}
```
<!-- /snippet -->

Read it, then run the same command without the flag to apply it. Nothing is written until you do;
there is no postinstall hook and no build step that installs skills behind you. What it writes:

- `.agents/skills/<name>/SKILL.md` for each catalogue skill — always, whatever agent you use.
- `.claude/skills/<name>/SKILL.md` as well, when the project has a `.claude/` directory or a
  `CLAUDE.md` (or with `--vendor claude`; `--vendor none` turns it off).
- a marked section in `AGENTS.md`, created if the file is absent, replaced between its own markers
  if a previous run wrote one. An `AGENTS.md` that exists without our markers needs
  `--write-existing`, so a first run can never append to a file you did not expect it to touch.
- one `@AGENTS.md` import line in an existing `CLAUDE.md`, and never a `CLAUDE.md` of its own.

Re-running is safe: an action that would write what is already there is dropped, so an up-to-date
project plans nothing. `./gradlew narrativetraceUninstall` removes exactly what was installed —
a page only when it carries the installer's own stamp, a file only when the installer created it and
nothing of yours is left in it. The full task and flag reference is in the
[Gradle Plugin Guide](gradle-plugin-guide.md#narrativetraceinit).

Without the Gradle plugin, the same installer is a verb on the launcher:
`narrativetrace init --dry-run` previews and `narrativetrace init` applies (the CLI keeps
`--dry-run`, where nothing shadows it; the Gradle task cannot, because Gradle's own `--dry-run`
skips every task in the graph). Run through the plugin, the plan above names
`ai.narrativetrace:narrativetrace-skills` as its carrier instead of the launcher — the same pages
out of whichever archive the project already fetched.

### By hand, the fallback

Copying works too, and is the only path for an agent with no build of its own:

- **Claude Code**: rendered `SKILL.md` files live at
  [`.claude/skills/narrativetrace-doctor/`](../.claude/skills/narrativetrace-doctor/SKILL.md) and
  [`.claude/skills/add-narrative-tracing/`](../.claude/skills/add-narrative-tracing/SKILL.md) and
  [`.claude/skills/add-narrativetrace-clarity/`](../.claude/skills/add-narrativetrace-clarity/SKILL.md)
  in this repository. Copy the relevant directory into your own project's `.claude/skills/<name>/` and
  Claude picks it up on its own, invokable by name directly.
- **Codex**: the same catalogue also renders
  [`.agents/skills/narrativetrace-doctor/`](../.agents/skills/narrativetrace-doctor/SKILL.md) and
  [`.agents/skills/add-narrative-tracing/`](../.agents/skills/add-narrative-tracing/SKILL.md) and
  [`.agents/skills/add-narrativetrace-clarity/`](../.agents/skills/add-narrativetrace-clarity/SKILL.md) — the
  repo-checked-in layout Codex CLI documents for a project's own skills. Its frontmatter carries
  only `name` and `description` (Codex documents no `when_to_use` or `allowed-tools` keys); the
  page body is otherwise identical. Copy the relevant directory into your own project's
  `.agents/skills/<name>/` and Codex discovers it the same way.
- **Any agent, any platform**: every agent that reads `AGENTS.md` sees the always-on pointer this
  repository's own `AGENTS.md` carries between its `<!-- narrativetrace:skills:start -->` markers
  — all four skills' names and descriptions, so an agent that never thought to look still knows they
  exist.
- **Gemini** runs against the same catalogue on a sporadic, quota-guarded schedule (see
  [Tier B — LLM trials](../narrativetrace-skills-catalogue/evals/README.md)) rather than every commit; it has
  no rendered layout of its own yet — copying the Claude or Codex files above is the closest path
  today.

### The doctor reports on this

One of its checks, `config.skills-installed`, reads each installed
`SKILL.md` and the stamp the installer left in it, and fails when the skills are absent, when one
of them is missing, or when their stamp names a different release than the project resolves —
so a stale copy shows up as a finding instead of as an agent quietly following last release's
instructions. A directory sitting at a skill's path without that stamp is reported as somebody
else's and never counted as installed. When the carrier cannot be resolved at all — an offline
build, or one with no repository that provides it — the check says it cannot tell and passes:
being offline is not a defect.

## From a registry

A project can carry these skills without anyone here ever running the installer, in one of three
states:

1. **Installed by `init`** — committed, the team's. The only state `config.skills-installed`
   passes: the pages carry the provenance line and match the release this project resolves.
2. **A personal install from a registry** (a Claude Code plugin cache) — yours only. Invisible to
   the doctor by design: it diagnoses the project, and a personal install reaches no teammate and
   no other agent.
3. **A registry install into the project** (`npx skills add`, `gemini skills install --scope
   workspace`) — this repository's own rendered pages, landed by a registry rather than by `init`,
   so they carry no provenance line yet.

Trying the skills yourself, without touching the project:

```text
/plugin marketplace add narrativetrace/narrativetrace-java
/plugin install narrativetrace-java@narrativetrace-java
```

then run `./gradlew narrativetraceInit --diff`, read the diff, and run it without the flag so
`AGENTS.md` points at them.

Installing into the project from the open-standard registry:

```text
npx skills add narrativetrace/narrativetrace-java
```

then run `./gradlew narrativetraceInit --diff`, read the diff, and run it without the flag so
`AGENTS.md` points at them.

A page a registry left behind is never refused just for being there. `init` compares it, byte for
byte but for the provenance line, against what it would have rendered itself. One identical to
this release's own page is **adopted** — the plan says so, rather than "replaced", because a
person reading it has to know that nothing of theirs was overwritten. This is the plan's own text,
quoted, never retyped here:

<!-- snippet: narrativetrace-tooling/src/main/java/ai/narrativetrace/tooling/init/PlanRenderer.java region=adoptedNote -->
```java
  private static final String ADOPTED =
      "adopted: identical to this carrier's page, so only the provenance line is added";

```
<!-- /snippet -->

A page that differs — another release, or hand-edited — keeps the ordinary refusal `--force` is
for. `npx skills add` also leaves `.claude/skills/<name>` a symbolic link to the open-standard
page; `init` never writes through a link like that one. A link whose target it would adopt or
already owns is replaced with a real directory holding the right flavour; every other link is
refused, because `--force` covers content, never a link.

And this is the doctor's own fix, quoted the same way, for a project where the pages are there but
carry none of this:

<!-- snippet: narrativetrace-tooling/src/main/java/ai/narrativetrace/tooling/doctor/checks/SkillsInstalledCheck.java region=registryMessages -->
```java
  /** Both spellings of the same command: Gradle owns {@code --dry-run}, so the task says diff. */
  private static final String INIT_COMMANDS =
      "./gradlew narrativetraceInit --diff (or narrativetrace init --dry-run)";

  /** What a skill directory that is present but not ours is called in a message. */
  private static final String NOT_OURS = " (there, but not ours)";

  /**
   * What a page with no provenance line most often IS: a registry install (D5 state 3) — `npx
   * skills add`, or a workspace skills install — of this repository's own rendered pages. Naming
   * the case matters because the obvious reading of "not ours" is "somebody else's work", which
   * invites a `--force` nobody needs: `init` ADOPTS a page identical to this release's.
   */
  private static final String FROM_A_REGISTRY =
      " Pages that are there without our line usually came from a registry (npx skills add, a"
          + " plugin or workspace install). Run "
          + INIT_COMMANDS
          + ", read the diff, then run it without the flag — a page identical to this release's is"
          + " adopted, and no --force is needed.";

```
<!-- /snippet -->

## How they're built

No skill is ever hand-edited. `narrativetrace-skills-catalogue/src/main/java/ai/narrativetrace/skills/catalogue/`
contains the four sources of truth; their typed steps render four Claude pages, four Codex pages,
the carrier's own copy of both flavours plus its `catalogue.json` index — what the published jar
hands `narrativetraceInit` — this repository's own `AGENTS.md` section, and the
`.claude-plugin/marketplace.json` listing that makes this repository a Claude Code plugin
marketplace: a drift test (`RenderDriftTest`, wired into `./gradlew check`) fails the build the
moment any of these nineteen outputs drifts from the typed source, and a second one fails it if
anything else appears in the carrier at all. A skill's rendered `name:` is always its canonical
name, never a
shortened "claude segment" — a repo-checked-in `.claude/skills/` or `.agents/skills/` directory is a
flat namespace with no plugin prefix to hide behind, so the name must be globally self-identifying
on its own. A second suite (`SkillReplayer`, Tier A2) mechanically replays every command and
machine-checkable `verify` a step names against the appropriate real fixture, including the
standalone Clarity consumer, today, deterministic,
no LLM — green means the instructions are literally executable right now, not merely plausible
prose. A Tier A lint keeps private planning-note citations, this repository's own Pro sibling, CI
config filenames, and git SHAs out of the rendered pages: rationale sentences ship, the citation naming the
source does not. A second lint guards the one piece of frontmatter whose absence is a feature: a
skill whose steps can make something public — today, `narrativetrace-feedback` — must declare no
`allowed-tools`, because that field pre-approves its listed tools for the turn that loads the
skill, and a reporting skill that pre-approved its own reporting command would stop the harness
asking exactly where asking is the point.

## See also

- [`narrativetrace-cli`](../narrativetrace-cli/) — the `doctor` verb `narrativetrace-doctor` runs
- [Sixty Seconds](sixty-seconds.md) — the install-and-first-trace walkthrough `add-narrative-tracing`'s steps are drawn from
- [What to Commit](what-to-commit.md) — the approval-trace state the doctor's fourth step checks
- [Tier B — LLM trials](../narrativetrace-skills-catalogue/evals/README.md) — the engine-neutral case layout, the sporadic Codex/Gemini policy, and the promotion matrix
- [Privacy and redaction](privacy-and-redaction.md) — the deny-list and value shapes the problem report's own value-free rules are measured against
