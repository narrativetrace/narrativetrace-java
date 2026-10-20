# Case: deviation-output-disabled

**Fixture:** `narrativetrace-skills-catalogue/evals/fixtures/clarity-output-disabled` (a project
with a real traced JUnit test, output disabled at execution time, and an existing `minScore` of
0.50), scaffolded into a scratch copy with its workspace dependencies pre-resolved.

**Task prompt** (given to the agent, catalogue loaded):

> Use the add-narrativetrace-clarity skill. I tried to add a Clarity report to this Java project,
> but the report is missing after the build. Please investigate the configuration and get me to a
> trustworthy first report without hiding an existing quality threshold.

**Expected trajectory:** the agent recognizes the trigger, loads `add-narrativetrace-clarity`,
diagnoses why output is disabled, repairs it without touching the existing `minScore=0.50`
policy, and produces a trustworthy test report.

**Grading:**
- **Gates** (every model): `graders/verify.sh` — the agent-created test reports exist; the
  resolved `clarityCheck` policy still reads `minScore=0.50`, enforcing (not `warnOnly`), with
  Clarity enabled; a clean `./gradlew clean clarityCheck` reproduces the same reports; a
  temporary stricter threshold (`1.01`) still fails with a Clarity diagnostic naming it, proving
  enforcement was never made advisory; the original policy passes again afterward.
- **Report-only** (cheapest model), gates (mid model+): does the explanation correctly
  characterize the score weights, report fidelity and gate semantics against the [judgment
  rubric](../judgment-rubric.md)?

No study is named in this content; the case content here is original to this eval suite.
