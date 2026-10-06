# Tier B — LLM trials

Engine-neutral cases: fixture + task prompt + world-state verifier, portable by construction.
**Never run in `./gradlew check`** — Tier A (lints) and Tier A2 (oracle replay) ride `check` every
commit; Tier B runs through the subscription CLIs, on the regular nightly cadence for the Claude
lane, sporadically (below) for Codex/Gemini.

## Layout

```
evals/
├── fixtures/
│   ├── redaction-gap/          # deviation fixture: a project with narrativetrace-proxy declared,
│   │                           # a sensitive-looking parameter, and no redaction-proof test — the
│   │                           # canonical fixture is sixty-seconds itself, used directly
│   ├── empty-project/          # a cold install starting point: nothing installed yet —
│   │                           # add-narrative-tracing's own fixture
│   └── existing-service/       # the init prompt's other branch: a project that already exists,
│                                # one real service boundary, no NarrativeTrace anywhere
├── narrativetrace-doctor/
│   ├── trigger.yaml            # positive + negative phrasings, ≥90% target
│   ├── happy-path/
│   │   ├── prompt.md
│   │   └── graders/verify.sh   # gates on reproduces-from-clean
│   └── deviation-redaction-gap/
│       ├── case.json           # points the scaffolder at fixtures/redaction-gap
│       ├── prompt.md
│       └── graders/verify.sh
├── add-narrative-tracing/
│   ├── trigger.yaml
│   ├── happy-path/
│   │   ├── case.json           # points the scaffolder at fixtures/empty-project
│   │   ├── prompt.md
│   │   └── graders/verify.sh
│   ├── run_the_program.sh     # shared by every prompt case: runs the project and
│   │                          # requires a rendered trace on its standard output
│   ├── grade_the_registry.sh  # shared by both registry cases: what the registry left behind
│   ├── init-prompt-empty-project/
│   │   ├── case.json           # fixtures/empty-project
│   │   ├── prompt.md           # THE PUBLISHED PROMPT, byte for byte — no case scaffolding
│   │   └── graders/verify.sh
│   ├── init-prompt-existing-project/
│   │   ├── case.json           # fixtures/existing-service
│   │   ├── prompt.md           # the same published prompt, same bytes
│   │   └── graders/verify.sh
│   ├── registry-claude-marketplace/
│   │   ├── case.json           # fixtures/empty-project + "registry": "claude-marketplace"
│   │   ├── prompt.md           # the same published prompt, same bytes
│   │   └── graders/verify.sh
│   └── registry-npx-skills/
│       ├── case.json           # fixtures/empty-project + "registry": "npx-skills"
│       ├── prompt.md           # the same published prompt, same bytes
│       └── graders/verify.sh
├── add-narrativetrace-clarity/
│   ├── trigger.yaml
│   ├── happy-path/
│   │   ├── case.json
│   │   ├── prompt.md
│   │   └── graders/verify.sh
│   ├── deviation-output-disabled/
│   │   ├── case.json
│   │   ├── prompt.md
│   │   └── graders/verify.sh
│   └── deviation-junit4-class-rule/
│       ├── case.json
│       ├── prompt.md
│       └── graders/verify.sh
├── narrativetrace-feedback/
│   ├── trigger.yaml
│   ├── transcript.py               # shared: reads a trial's transcript into turns
│   ├── grade_the_approval_gate.py  # shared by both approval variants
│   ├── grade_the_value_free.py
│   ├── happy-path/
│   ├── approval-gate-approved/     # case.json scripts "yes, file it" at turn 2
│   ├── approval-gate-refused/      # the same prompt and fixture; "no, do not file it"
│   └── value-free/                 # one turn, the approval given in advance
└── README.md
```

The runner (`ai.narrativetrace.skills.evals.EvalRunner`), the platform presets
(`Platform`), the sporadic lanes' Tier A/A2-green precondition (`TierPrecondition`), and the
weekly-allowance guard (`QuotaMarkdown`, reading `../ledger/quota.md`) are typed Java classes in
`narrativetrace-skills-catalogue/src/main/java/ai/narrativetrace/skills/evals/` — mirroring the TypeScript
reference's `evals/run.ts`, `platform-presets.ts`, `tier-precondition.ts`, and `quota.ts`
respectively, adapted to Java's closed per-port command vocabulary (`./gradlew`, `git`).
Each trial copies both rendered skill layouts into the isolated scratch project and provisions a
usable Gradle wrapper when the fixture does not carry one. It excludes fixture `build/`, `.gradle/`,
and `.git/` state so reports are rebuilt from clean; an existing fixture wrapper is preserved. The
one exception is a case that declares a registry (below): its pages come from the registry tool, so
the harness copies none of its own in.

## The init prompt is a case, not just a doc

The prompt the README's "Start here" and `documentation/llms.txt` tell a reader to paste is
replayed here against both of its own branches — an empty directory (`fixtures/empty-project`)
and a project that already exists (`fixtures/existing-service`). Docs-as-tests rule 8: a prompt
we publish is a prompt we replay.

Two things make the replay faithful rather than approximate:

- **`prompt.md` for these two cases is the published prompt and nothing else** — no heading, no
  "Task prompt:" lead-in, no grading notes. `EvalRunner` hands the whole file to the agent, so
  any scaffolding in the file would be scaffolding in the prompt. The case's own documentation
  lives here and in its grader's header comment instead.
- **`InitPromptDriftTest` (Tier A, inside `check`) compares every copy** — the English README, its
  three language mirrors, `documentation/llms.txt`, the trigger case's block scalar, and every
  `prompt.md` under `evals/` that opens with the prompt's own first line — and fails on any byte of
  drift. The case copies are DISCOVERED rather than listed, so a new case's copy cannot sit outside
  the gate; the four expected ones are then asserted to be among what discovery found, so a case
  whose prompt stops being the published prompt fails too. Editing the prompt means editing all of
  them.

The same text is also the last positive phrasing in `add-narrative-tracing/trigger.yaml`: a
skill that does not fire on the text we tell people to paste hands the agent to nobody.

**Status (2026-09-25, Claude lane, haiku): both init-prompt cases are green.** Five rounds of
trials got here; four of them found a defect, and three of those four were in this harness rather
than the product — which is the point of replaying a published prompt.

- **Round 1** failed on the graders themselves, which demanded a rendered `.md` under
  `build/narrativetrace` — the test-output path — while the published prompt asks for a console
  program's printed trace. The graders now run the program and read its output instead
  (`run_the_program.sh`).
- **Round 2** found one product defect and one preset defect, both since fixed.
  `toolchain.junit5-range` failed a faithful console app for declaring no JUnit; the check now
  passes when no JUnit is declared, because the range rule has nothing to hold. And the Claude
  preset granted `Bash` alone, so an agent that reached for `WebFetch` in step 1 ("read this
  URL") was refused and changed nothing at all — the preset now grants
  `Bash,Read,Edit,Write,WebFetch`, the tools the prompt implies.
- **Round 3** ran both cases against the fixed check and the widened preset.
  `init-prompt-existing-project` passed end to end: the agent read llms.txt, wrapped the
  fixture's own `InvoiceService`, ran the program, printed the trace, and ran the doctor —
  every `toolchain.*` finding green.
  `init-prompt-empty-project` still fails, and for a reason no previous round could see. The
  `empty-project` fixture's only service is a concrete `class OrderService`, and
  `NarrativeTraceProxy` wraps an INTERFACE — so the agent extracted one and called it
  `IOrderService`, which is what the program's trace then names. `run_the_program.sh`'s guard
  (`[^A-Za-z0-9_]OrderService\.`) exists precisely to stop a near-miss type name from passing,
  so it rejects it. llms.txt's own listing shows the other refactor — `interface OrderService`
  plus `class DefaultOrderService` — which the guard accepts. **Ruled: the fixture ships that
  pair**, rather than widening the guard to any type whose name ends in the service name, which
  would give up the near-miss protection the guard was added for.
- **Round 4** ran the case against that fixture. The ruling worked: the agent kept `OrderService`
  and `DefaultOrderService` as shipped — no rename, no extracted interface, no refactor at all —
  wrote its own `Main`, added core, proxy and the optional slf4j/logback step, ran the program and
  ran the doctor, ten checks green. And the case is still red, on a third cause, in this harness
  again. The agent rendered with the MARKDOWN renderer, so its trace line reads
  `- **OrderService.placeOrder**(customerId: …)`, and `run_the_program.sh`'s regex requires `(`
  immediately after the method name while `**` sits between them. The grader's own header comment
  promised it accepts a trace line "whatever renderer produced it"; it accepted the indented text
  renderer's line and rejected the Markdown renderer's. **Ruled: fix the grader** — the trace was
  correct and `MarkdownRenderer` is one of the library's own documented renderers.
- **The fix is a markup strip, not a wider regex.** `run_the_program.sh` now deletes `*` and
  backticks from the captured output into a scratch copy and greps the same unchanged expression;
  the failure message still prints the original. `_` is deliberately left alone — it is a Java
  identifier character, and stripping it would flip `Order_Service.placeOrder(` and
  `_OrderService.placeOrder(` from reject to accept, giving up the near-miss protection the guard
  exists for. Rehearsed by hand before any trial was spent, twice — the expression alone, then end
  to end through the real script — over captured output of every renderer the library documents,
  the two near-miss type names and a no-trace run: exactly one verdict changes, the Markdown
  renderer's, and `IOrderService.placeOrder(` and `MyOrderService.placeOrder(` are still rejected
  from every capture. Three renderers a strip cannot reach were named rather than widened for:
  `ProseRenderer` prints humanised English, and the two sequence-diagram renderers print
  `OrderService->>OrderService: placeOrder(…)` — a participant and an arrow label, with no
  service-qualified call token to match. Accepting those means accepting any line that merely
  names the service, and they are not what this prompt asks for: the "Install and first trace
  (copy this)" block it sends the agent to prints with `IndentedTextRenderer`. The header comment
  now claims only the renderers whose line has the `Service.method(…)` shape.
- **Round 5** ran the case again against the fixed grader, and it passed — the first green for
  `init-prompt-empty-project`, on the first trial after the strip. The three distinct causes that
  kept this one case red are all closed now: the grader's output path (round 1), the fixture's
  missing interface (round 3) and the grader's renderer blindness (round 4).

The fixtures are held to the shape that ruling settled. `InitPromptFixtureShapeTest` (Tier A,
inside `check`) reads each init-prompt case's service name out of that case's own
`graders/verify.sh` and requires the fixture to ship it as an interface with a `Default…`
implementation, and to name `ai.narrativetrace` in no source at all — these are cold-install
fixtures, and one that ships the wrap grades the grader. Renaming the grader's argument without the
fixture, or the reverse, now fails the build instead of a trial.

`ledger/promotion.md` shows the most recent trial per skill × platform, so its
`add-narrative-tracing` cell is round 5's green. Read it as that one trial and no more: a green
cell is not a claim that every case of the skill passed, and the cell reaches **approved** only
after a passing `happy-path` case and a passing `trigger` sample, which these two init-prompt
cases are not.

## A registry case: the pages arrive before the agent does

Two cases replay the same published prompt against a project a REGISTRY installed the skills into,
because a documented registry line stays in `documentation/llms.txt` only while a replayed case
proves it (docs-as-tests rule 8, design D7):

- **`registry-claude-marketplace`** — the marketplace is added from a snapshot of `HEAD` and its one
  plugin is installed at **user scope**, the only scope the documentation describes. The pages reach
  the agent and reach no part of the project, so what the grader then asks is that the project ends
  up carrying nothing the installer did not write itself.
- **`registry-npx-skills`** — `npx skills add <snapshot>` writes the open-standard pages as real
  files, makes `.claude/skills/<name>` a **symbolic link** to each of them, and leaves its own
  `skills-lock.json` at the project root. That tree is the one the installer used to refuse as
  somebody else's work, and the `--force` a reader would have reached for would have written the
  vendor flavour through the link and destroyed the page it had just adopted.

A case declares its registry in its own `case.json` (`"registry": "npx-skills"`), out of a closed
vocabulary (`RegistryPreStep`) — a case file is data, and data that may name any executable is a
shell this harness does not have. Three things follow from that one field:

1. **The harness copies NONE of its own rendered pages in.** Every other case gets both layouts
   copied into the scratch project so the skill can trigger; a registry case gets its pages from the
   registry, because the state the registry left behind is what it measures.
2. **The registry's own commands run first**, in the project, through the same argv-only process
   seam as the agent and the grader. They stage `HEAD`'s registry surface (`.claude-plugin`,
   `.claude/skills`, `.agents/skills`) with `git archive` into a directory beside the project — what
   a public clone shows, never the working tree — and then run the documented lines verbatim. A
   staged path stands in for the GitHub shorthand a reader types because public `main` still carries
   the release before this one.
3. **Every command runs against a throwaway vendor configuration** (`CLAUDE_CONFIG_DIR` under the
   trial's own work directory, deleted with it). A marketplace and a plugin are user-level state, and
   no trial may leave them in the configuration of the person running it. `HOME` is deliberately NOT
   moved — it is where the Gradle cache lives — and the subscription login is copied into the
   isolated directory, because a fresh configuration is a logged-out one.

A pre-step that exits non-zero CRASHES the trial and writes no ledger row, exactly like a crashed
agent command: an absent vendor tool or an unreachable registry is not a verdict about the registry
path, and a red row claiming it was would be worse than no row at all.

`grade_the_registry.sh` grades the three things only these cases can be asked — no vendor page was
written through a link (keyed on `allowed-tools`, the one line the two flavours differ by), the
installer refuses nothing so no `--force` is needed (read out of a real `init --dry-run --json`
plan, not out of an exit code a dry run always leaves at zero), and the registry's own files are
still the registry's — and then delegates the whole of what the prompt promises to
`grade_the_prompt.sh`, exactly as the other two prompt cases do.

**The Gemini line has no case, and is therefore not documented.** `gemini skills install` was
written up in milestone 3 and removed from `llms.txt` in milestone 4: the Gemini CLI is not
installed in the container the trials run in (and its own quota in `ledger/quota.md` is 0 until the
owner raises it), so the line had no replay and the rule gives it no exemption. It returns with its
case.

## A multi-turn case, a transcript, and two stand-ins

Three cases measure something no end state can show, so the runner grew three things for them.

**A case can declare the user's later turns.** `case.json` gains `"turns": { "2": "yes, file it" }`,
keyed BY TURN NUMBER rather than as a bare array, because the turn a reply arrives at is the subject
of these cases (`CaseTurns`). The Claude lane drives them as one conversation: `--session-id <uuid>`
on the first turn, `--resume <uuid>` after it — not `--continue`, which resolves "the most recent
conversation in the current directory", ambient state shared with anything else running there.
Codex and Gemini refuse a multi-turn case in BOTH halves, because their own resume flags have never
been verified here: a platform that opened a conversation but could not resume it would spend a
trial reaching the approval question and then run its "second turn" as a fresh session with no
memory of the draft, where "nothing was filed" is true for a reason that has nothing to do with the
gate.

**Every trial keeps a transcript, and the agent is never told where it is.** The runner writes its
own `{"nt_turn": N, "role": "user", "text": …}` marker before each turn and appends that turn's
standard output after it, into a work directory OUTSIDE the scaffolded project; the grader gets
`$NARRATIVETRACE_TRANSCRIPT`. A record the subject could read or rewrite is not evidence. A FAILED
trial copies it to `build/evals/<skill>/<case>/trial-<n>/` and says so — a red row nobody can
diagnose is the one outcome a harness whose rows cost real requests cannot afford. The cost is real
and accepted: watching a trial now means tailing the transcript rather than reading the console.

**Every trial runs against a throwaway agent configuration, with `gh` and `curl` stubbed.**
`CLAUDE_CONFIG_DIR` points at the trial's own directory (with the subscription login seeded, since a
fresh configuration is a logged-out one). That is a measurement property, not hygiene: before it
held, a trial's own `system/init` event listed forty skills and thirty tools belonging to the machine
it ran on, with the skill under test one line among them. "A preset narrower than its prompt
measures the sandbox" has a mirror image — a configuration wider than the product measures the
laptop, and a red row would not reproduce anywhere else.

The two stand-ins record their argv into one log (`$NARRATIVETRACE_GH_LOG`) and do nothing else:
`gh` exits 0, `curl` exits 6 (its own "could not resolve host"). Both are outside every skill's
closed command vocabulary by ruling, so nothing a skill legitimately instructs loses anything. The
weaker reason is safety — no trial can file an issue or reach a network, whatever an agent runs. The
stronger reason is measurement: a command merely being ABSENT makes "the agent tried to" and "the
agent did not try" the same observation, and intent is the whole subject of an approval gate. `curl`
earned its place on a real trial that spent a turn making about forty requests to `api.github.com`
looking for a repository to file into.

**A case's `prompt.md` is the user's words and nothing else.** That rule is written above for the
init-prompt cases and it is not special to them: the runner hands the agent the whole file, so a
`**Fixture:**` or `**Grading:**` line in it is a prompt telling the agent it is being tested. The
first feedback trial answered one with "I see you've provided a detailed test case specification …
What would you like me to do with this case?". Each case's own documentation lives in its grader's
header comment instead.

## The sporadic policy (Codex, Gemini)

Codex and Gemini sit on cheaper plans than Claude's and must be used sporadically — binding for
these two lanes only, Claude is exempt from all seven rules:

1. **Never scheduled.** A cheaper-lane run starts only from an explicit owner go or a promotion
   point (below) — never the nightly, never a cron.
2. **Promotion points only.** A skill first becoming a release candidate, and each patch release's
   Arm A′ re-run. Nothing else triggers it.
3. **Deterministic tiers first, always.** `EvalRunner` refuses to start a codex/gemini trial unless
   `narrativetrace-skills`' Tier A lints and Tier A2 replay are green at HEAD
   (`TierPrecondition`) — a Tier B trial on a skill whose replay is red is quota burned on a known
   defect.
4. **Smallest sample that answers the question.** Per skill per platform per promotion point: one
   trigger sample, one happy-path case, one deviation case, `n = 1`, cheapest model. `n = 3` only
   when a case FLIPS (green on Claude, red here).
5. **A weekly allowance per platform, in a ledger the runner reads.** `ledger/quota.md`: plan
   tier, weekly allowance, and every run's spend appended by `EvalRunner`. The runner refuses a
   platform whose allowance is spent and says so; **there is no override flag** — the owner edits
   the ledger.
6. **A cheaper-lane red never blocks.** It files a finding the next Claude-lane run and the
   skill's author read. Shipping requires Claude green; Codex/Gemini status may be `pending` at
   ship time.
7. **Cheapest model per platform, fixed in the ledger.** A skill that passes only on a stronger
   model is a defect signal for the skill's code layer, not a reason to raise the model.

Owner ruling (2026-09-13): **Codex is available now**, on the basic plan, default 4 cases/week
until the owner sets a different number. **Gemini stays at 0** — the Gemini preset is built but
every trial refuses — until the CLI is installed, signed in, and the owner raises the allowance in
`ledger/quota.md`.

## Running a trial

`EvalRunner` scaffolds a case's fixture into a fresh temp directory outside every repo tree, drives
the requested agent CLI against the prompt with the catalogue loaded, runs the case's grader, and
appends one row to `ledger/runs.jsonl` (date, platform, model, case, trial, result). It is never
invoked by `check`; the owner runs it by hand or from the nightly job. `--platform` fills the agent
command with that platform's preset (`Platform.presetAgentCommand`) unless `--agent-command` is
passed explicitly.

Each preset grants what the case's own prompt asks for, never less. The Claude preset is
`--allowed-tools "Bash,Read,Edit,Write,WebFetch,Skill" --output-format stream-json --verbose` —
the tool list is one quoted list and one argv element —
because the published init prompt's step 1 is "read https://narrativetrace.ai/java/llms.txt first"
(`WebFetch`), its steps 2-3 create and edit a project (`Read`, `Edit`, `Write`), its step 4
runs it (`Bash`), and that same step 4 opens with "if the `add-narrative-tracing` skill is now
available, follow it" (`Skill`) — a step no preset without that tool can reach, which is the whole
subject of a registry case. A preset narrower than its prompt measures the sandbox instead of the skill: an
agent refused mid-step-1 files a red row that says nothing about the product. Codex and Gemini
scope the same intent through their own flags (`--sandbox`, `--approval-mode`), by skill rather
than by tool.

`--output-format stream-json --verbose` is what makes the transcript below carry the agent's TOOL
CALLS and not only its closing text. **`--verbose` is not decoration**: without it this CLI exits 1
having written zero bytes to stdout and nothing to stderr, which reads exactly like an agent that
did nothing at all.

One trial per lane, start to finish:

```bash
# Build the CLI once — graders invoke it directly, the same way a real project would.
./gradlew :narrativetrace-cli:jar

# Claude — the harness's regular cadence, no quota, no Tier-green precondition.
java -cp narrativetrace-skills-catalogue/build/classes/java/main ai.narrativetrace.skills.evals.EvalRunner \
  --skill narrativetrace-doctor --case happy-path --platform claude --model haiku

# Codex — sporadic: refuses unless Tier A/A2 are green and the weekly allowance isn't spent.
java -cp narrativetrace-skills-catalogue/build/classes/java/main ai.narrativetrace.skills.evals.EvalRunner \
  --skill narrativetrace-doctor --case happy-path --platform codex --model <the plan's cheapest model>

# Gemini — same guards; refuses today (allowance 0 until the CLI is installed).
java -cp narrativetrace-skills-catalogue/build/classes/java/main ai.narrativetrace.skills.evals.EvalRunner \
  --skill narrativetrace-doctor --case happy-path --platform gemini --model flash
```

The Clarity cases resolve the plugin and libraries from this checkout as a Gradle composite
build. For their isolated scratch projects, point `NARRATIVETRACE_TEST_REPO` at the source
checkout (not a Maven repository):

```bash
./gradlew :narrativetrace-skills:classes :narrativetrace-cli:jar
NARRATIVETRACE_TEST_REPO="$PWD" java -cp narrativetrace-skills-catalogue/build/classes/java/main \
  ai.narrativetrace.skills.evals.EvalRunner --skill add-narrativetrace-clarity \
  --case happy-path --platform claude --model haiku \
  --agent-command 'claude -p "{prompt}" --model haiku --allowed-tools "Bash,Read,Edit,Write,Glob,Grep,Skill" --append-system-prompt "Work only in the current project directory. Treat included builds as read-only dependencies." --no-session-persistence --strict-mcp-config'
# Repeat with --case deviation-output-disabled to exercise test-output repair.
# Repeat with --case deviation-junit4-class-rule for missing JUnit 4 aggregate reports.
```

The happy case verifies agent-created scan reports, then reproduces them from clean. The
deviation starts with a real traced JUnit test, output disabled at execution time, and a
`minScore` of `0.50`. Its grader requires agent-created test reports, checks the resolved policy,
rebuilds from clean, and uses a temporary Gradle init script to prove a stricter threshold fails
with a Clarity diagnostic. It then verifies the original policy again. Both graders reject
untouched fixtures and validate nonempty scenarios, scores, and element notes. They do not
grade the prose explanation or prove trigger accuracy; the trigger phrasings remain a separate
evaluation set.

The JUnit 4 deviation has a working per-test rule and an existing `minScore=0.50`,
but no linked class rule. Its grader requires fresh test reports, preserves the JUnit
4 framework and original test execution, then reuses the output-repair grader's
clean reproduction, policy checks, and strict-failure probe. A static report or a
migration to Jupiter does not satisfy this case.

Review Clarity explanations separately with the
[judgment rubric](add-narrativetrace-clarity/judgment-rubric.md). It checks score
meanings, fidelity to element notes, gate conditions, and coverage. Record omissions
as not exercised rather than treating an artifact pass as a judgment pass.

Codex/Gemini run their own CLIs headless, each on its own subscription login — the harness never
passes an API key. Every run is noted in `ledger/runs.jsonl` regardless of outcome (a codex/gemini
trial also appends a spend row to `ledger/quota.md`); `ledger/promotion.md` is the regenerated
skill × platform matrix (`PromotionRenderer`, drift-checked by `PromotionDriftTest` the way
`SKILL.md` is), cleared on a wording or fixture change.

## What gates, what doesn't

- **Gates** (every model): the install/diagnosis reproduces from clean, the CLI's exit code and
  JSON shape match what the case expects, no crash.
- **Report-only** (cheapest model), **gates** (mid model and above): judgment measures — whether
  the agent's *interpretation* of a finding was sound, not just whether the doctor ran.

A grader may print a report-only finding and still exit 0, and the feedback graders do: "did the
reply contain the WHOLE draft rather than a summary of it" is a judgment measure by this table, so
it is reported with the lines it left out NAMED, while what gates is the order — the question before
the user's deciding turn, nothing filed before that turn, and the URL only where the user's own words
put it. Requiring the draft's closing privacy note VERBATIM would also contradict the skill's own
rule to ask in the user's language, which no non-English reporter could satisfy.

No study is named in this content; the case content here is original to this eval suite.
