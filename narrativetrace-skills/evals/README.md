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
│   └── empty-project/          # a cold install starting point: nothing installed yet —
│                                # add-narrative-tracing's own fixture
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
│   └── happy-path/
│       ├── case.json           # points the scaffolder at fixtures/empty-project
│       ├── prompt.md
│       └── graders/verify.sh
└── README.md
```

The runner (`ai.narrativetrace.skills.evals.EvalRunner`), the platform presets
(`Platform`), the sporadic lanes' Tier A/A2-green precondition (`TierPrecondition`), and the
weekly-allowance guard (`QuotaMarkdown`, reading `../ledger/quota.md`) are typed Java classes in
`narrativetrace-skills/src/main/java/ai/narrativetrace/skills/evals/` — mirroring the TypeScript
reference's `evals/run.ts`, `platform-presets.ts`, `tier-precondition.ts`, and `quota.ts`
respectively, adapted to Java's closed per-port command vocabulary (`./gradlew`, `git`).

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
passed explicitly:

```bash
# Build the CLI once — graders invoke it directly, the same way a real project would.
./gradlew :narrativetrace-cli:jar

# Claude — the harness's regular cadence, no quota, no Tier-green precondition.
java -cp narrativetrace-skills/build/classes/java/main ai.narrativetrace.skills.evals.EvalRunner \
  --skill narrativetrace-doctor --case happy-path --platform claude --model haiku

# Codex — sporadic: refuses unless Tier A/A2 are green and the weekly allowance isn't spent.
java -cp narrativetrace-skills/build/classes/java/main ai.narrativetrace.skills.evals.EvalRunner \
  --skill narrativetrace-doctor --case happy-path --platform codex --model <the plan's cheapest model>

# Gemini — same guards; refuses today (allowance 0 until the CLI is installed).
java -cp narrativetrace-skills/build/classes/java/main ai.narrativetrace.skills.evals.EvalRunner \
  --skill narrativetrace-doctor --case happy-path --platform gemini --model flash
```

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

No study is named in this content; the case content here is original to this eval suite.
