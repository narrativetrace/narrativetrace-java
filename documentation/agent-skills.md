# Agent skills

NarrativeTrace ships **skills**: agent-loadable procedures that run tested commands and gate
completion on a `verify` step, rather than docs an agent might or might not read. A skill is thin
by design — the checking, diagnosis, or generation logic lives in tested library code; the skill's
own job is knowing when to act, invoking that tested code, and interpreting the result in context.

## Seven skills: setup, diagnosis, clarity, reporting, verification, debugging, and explanation

- **`add-narrative-tracing`** — installs NarrativeTrace into a project and gets it to a first
  trace: install with the real toolchain, wrap a class with `NarrativeTraceProxy.trace`, render
  and run the first trace, then wire a real logger (`narrativetrace-slf4j` plus Logback). Right
  after the install it also runs the doctor and applies, in order, every `config.<framework>-*` fix
  it prints — the page names no framework; the installed doctor's own framework table decides
  which ones the project needs. Runs `narrativetrace-doctor` and hands off — the seam between the two skills — and closes by previewing
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
- **`narrativetrace-verify`** — reads what a change actually did before the agent says it is done.
  It runs after the tests are green and first decides whether the change is worth tracing at all —
  a pure function or a one-class edit is not, and the skill says so and stops. Otherwise it writes
  the intent down before anything runs (which collaborators, in which order, under which branch,
  how many times), runs the smallest real path with tracing on, reads the value-free structural
  trace against that intent, opens values only on the span that looks wrong, fixes and re-reads,
  then pins the flow as an `.approved.nt` baseline — turning approval mode on if it is off, showing
  the whole `.received.nt`, and promoting it only after your yes, in a turn of its own. Its report
  cites span ids (`#2.1`), the position every flavour prints for the same call, so a claim about the
  trace can be checked against the trace.
- **`narrativetrace-debug`** — finds the cause of a wrong result by reading what the code did with
  the values, not by stepping through it. It starts from a symptom rather than a change: reproduce
  it with the smallest input and tracing on, read the sequence diagram first when the path crosses
  threads, then name — by span id, before touching any code — the first span whose inputs are
  right and whose result is wrong. It narrows by span, never by file: it reads the sub-tree under
  that id and, when the work inside the span is not traced, wraps one more collaborator rather than
  redacting anything (`@NotTraced` hides a value; it does not scope a trace). It fixes the defect in
  that span, re-runs the same input, and checks that nothing else moved — a red run writes no `.nt`,
  so the shape before the fix is the reproduction's Markdown call lines without their values. It
  keeps the reproduction as a regression test, pins its structural trace through the same approval
  gate as `narrativetrace-verify`, and reports the root cause by span id. When the trace and the code
  disagree, or the defect is NarrativeTrace's own, it hands off to `narrativetrace-feedback` instead
  of patching around it.
- **`narrativetrace-explain`** — explains what an application actually does at runtime, for someone
  who has not thought of tracing at all: "show me what my application actually does", "walk me
  through what happens when a user checks out", "what does this endpoint call". It is the only one
  of these that triggers when nothing is broken and no change needs reading. Its first step is a
  cost decision rather than a run: a pure function, or a question ten lines of source answer, is
  answered from the source with no install and no run, and the skill says so and stops. Otherwise it
  picks ONE scenario that already exercises the flow — an existing test or entry point, never a new
  harness where one exists — routes through `add-narrative-tracing` when the project has no tracing
  yet, runs that scenario as it found it, reads the value-free structural trace in the order it ran,
  opens values only where the question is about a value or about where the time went, and explains
  the run by span id. Two things make the explanation checkable: every sentence that goes beyond
  what the trace shows is marked as an inference, and the explanation names what was NOT observed —
  the code no span covers. It fixes nothing: a defect the run exposes goes to
  `narrativetrace-debug`, a change of this session's own to `narrativetrace-verify`.

They compose: a brand-new project starts with `add-narrative-tracing`; a project that already has
NarrativeTrace installed, where something isn't working, starts with `narrativetrace-doctor`.
Either path ends at the doctor — it owns diagnosis from there. `add-narrativetrace-clarity` owns
first static naming reports and optional clarity enforcement; it does not install tracing or
harvest a glossary. `narrativetrace-feedback` is where a path ends when the problem turns out to be
ours rather than the project's — the doctor's own closing rule points at it. `narrativetrace-verify`
is what a session with NarrativeTrace installed does after every change worth tracing — the install
skill's last step points the next session at it, and the doctor's `config.approval-mode` finding
(baselines that nothing compares) is fixed by its pin step. `narrativetrace-debug` is where a
reported symptom starts; it shares the verify skill's reading reference (which flavour answers which
question, and the shapes that mean something went wrong) and its pin, and ends at
`narrativetrace-feedback` when the defect is ours. `narrativetrace-explain` is the entry point for
the request that names nothing of ours — a question about what the code does — and it reads the
trace with the same shared reference; where the other two end at a pin, it ends at an explanation,
and hands anything that needs fixing or verifying to them.

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
  "carrier": "ai.narrativetrace:narrativetrace-cli:0.5.1",
  "actions": [
    {"kind": "create", "path": ".agents/skills/narrativetrace-doctor/SKILL.md", "status": "planned"},
    {"kind": "create", "path": ".claude/skills/narrativetrace-doctor/SKILL.md", "status": "planned"},
    {"kind": "create", "path": ".agents/skills/add-narrative-tracing/SKILL.md", "status": "planned"},
    {"kind": "create", "path": ".claude/skills/add-narrative-tracing/SKILL.md", "status": "planned"},
    {"kind": "create", "path": ".agents/skills/add-narrativetrace-clarity/SKILL.md", "status": "planned"},
    {"kind": "create", "path": ".claude/skills/add-narrativetrace-clarity/SKILL.md", "status": "planned"},
    {"kind": "create", "path": ".agents/skills/narrativetrace-feedback/SKILL.md", "status": "planned"},
    {"kind": "create", "path": ".claude/skills/narrativetrace-feedback/SKILL.md", "status": "planned"},
    {"kind": "create", "path": ".agents/skills/narrativetrace-verify/SKILL.md", "status": "planned"},
    {"kind": "create", "path": ".claude/skills/narrativetrace-verify/SKILL.md", "status": "planned"},
    {"kind": "create", "path": ".agents/skills/narrativetrace-debug/SKILL.md", "status": "planned"},
    {"kind": "create", "path": ".claude/skills/narrativetrace-debug/SKILL.md", "status": "planned"},
    {"kind": "create", "path": ".agents/skills/narrativetrace-explain/SKILL.md", "status": "planned"},
    {"kind": "create", "path": ".claude/skills/narrativetrace-explain/SKILL.md", "status": "planned"},
    {"kind": "create", "path": "AGENTS.md", "status": "planned"},
    {"kind": "create", "path": "CLAUDE.md", "status": "planned"}
  ],
  "exitCode": 0
}
```
<!-- /snippet -->

Read it, then run the same command without the flag to apply it. Nothing is written until you do;
there is no postinstall hook and no build step that installs skills behind you. What it writes:

- `.agents/skills/<name>/SKILL.md` for each catalogue skill — always, whatever agent you use.
- `.claude/skills/<name>/SKILL.md` as well, always: Claude Code loads project skills from that
  directory only, never from `.agents/skills`, and Cursor and GitHub Copilot read it too
  (`--vendor none` turns it off).
- a marked section in `AGENTS.md`, created if the file is absent, replaced between its own markers
  if a previous run wrote one. An `AGENTS.md` that exists without our markers needs
  `--write-existing`, so a first run can never append to a file you did not expect it to touch.
- one `@AGENTS.md` import line in `CLAUDE.md`: appended to an existing `CLAUDE.md` (with
  `--write-existing`), and when the project has none, a new `CLAUDE.md` holding that line and
  nothing else. Claude Code loads `CLAUDE.md` and never `AGENTS.md`, so without the import the
  section is not read at all. Re-running changes nothing; `narrativetraceUninstall` deletes a
  `CLAUDE.md` it created.
- a pointer file for each platform the project shows it uses — a marked section in `replit.md` when
  the project has `replit.md` or `.replit`, and `.cursor/rules/narrativetrace.mdc` when it has a
  `.cursor/` directory; nothing for a platform the project does not use. See
  [Use NarrativeTrace with your agent](use-with-your-agent.md).

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
- **Any agent, any platform**: every agent that reads `AGENTS.md` sees the always-on pointer this
  repository's own `AGENTS.md` carries between its `<!-- narrativetrace:skills:start -->` markers
  — all seven skills' names and descriptions, so an agent that never thought to look still knows they
  exist.
- **Codex, Antigravity, Cursor, Replit, GitHub Copilot**: all of them read the open-standard
  `.agents/skills/` layout the installer writes, and `init` adds a small pointer file where the
  platform has one. Each platform's install path, what its agent finds, and the copy-by-hand notes
  are in [Use NarrativeTrace with your agent](use-with-your-agent.md).

### The doctor reports on this

One of its checks, `config.skills-installed`, reads each installed `SKILL.md` and the stamp the
installer left in it, and fails when the skills are absent, when one of them is missing, or when
their stamp names a different release than the project resolves — so a stale copy shows up as a
finding instead of as an agent quietly following last release's instructions. For each detected
platform that has a pointer file, it also reports the pointer as present and current, or names what
is missing, stale or not ours. A directory sitting at a skill's path without that stamp is reported
as somebody else's and never counted as installed. When the carrier cannot be resolved at all — an
offline build, or one with no repository that provides it — the check says it cannot tell and
passes: being offline is not a defect.

## From a registry

A project can carry these skills without anyone here ever running the installer, in one of three
states:

1. **Installed by `init`** — committed, the team's. The only state `config.skills-installed`
   passes: the pages carry the provenance line and match the release this project resolves.
2. **A personal install from a registry** (a Claude Code plugin cache) — yours only. Invisible to
   the doctor by design: it diagnoses the project, and a personal install reaches no teammate and
   no other agent.
3. **A registry install into the project** (`npx skills add`) — this repository's own rendered
   pages, landed by a registry rather than by `init`, so they carry no provenance line yet.

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
contains the seven sources of truth; their typed steps render seven Claude pages, seven Codex pages,
the carrier's own copy of both flavours plus its `catalogue.json` index — what the published jar
hands `narrativetraceInit` — this repository's own `AGENTS.md` section, and the
`.claude-plugin/marketplace.json` listing that makes this repository a Claude Code plugin
marketplace: a drift test (`RenderDriftTest`, wired into `./gradlew check`) fails the build the
moment any of these thirty-one outputs drifts from the typed source, and a second one fails it if
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
asking exactly where asking is the point. A third lint holds a skill that promotes an approval
baseline — today, `narrativetrace-verify` and `narrativetrace-debug` — to no allowed tool beyond read-only `find`, for the same
reason: the promotion runs through `./gradlew`, and the yes it waits for is yours.

## Context7

Context7 (context7.com) indexes this repository's `documentation/` tree and the root
`README.md` — minus the translated mirrors and a short exclude list — and serves their code
blocks to coding agents that resolve this library before answering. The root `context7.json`
controls what it sees, validated every build against the schema copy committed at
`config/context7/schema.json`, which is refreshed by hand from the live schema, not fetched at
build time.

## See also

- [`narrativetrace-cli`](../narrativetrace-cli/) — the `doctor` verb `narrativetrace-doctor` runs
- [Sixty Seconds](sixty-seconds.md) — the install-and-first-trace walkthrough `add-narrative-tracing`'s steps are drawn from
- [What to Commit](what-to-commit.md) — the approval-trace state the doctor's fourth step checks
- [Tier B — LLM trials](../narrativetrace-skills-catalogue/evals/README.md) — the engine-neutral case layout, the sporadic Codex/Antigravity/Cursor policy, and the promotion matrix
- [Privacy and redaction](privacy-and-redaction.md) — the deny-list and value shapes the problem report's own value-free rules are measured against
