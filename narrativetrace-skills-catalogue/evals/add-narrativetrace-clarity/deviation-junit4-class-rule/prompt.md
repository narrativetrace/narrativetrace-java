# Case: deviation-junit4-class-rule

**Fixture:** `narrativetrace-skills-catalogue/evals/fixtures/clarity-junit4-class-rule` (a JUnit 4
service with a working per-test rule, an existing `minScore` of 0.50, but no linked class rule,
so the aggregate report is missing and `clarityCheck` skips), scaffolded into a scratch copy with
its workspace dependencies pre-resolved.

**Task prompt** (given to the agent, catalogue loaded):

> Use the add-narrativetrace-clarity skill. This service uses JUnit 4. Tests pass and
> per-test narratives appear, but the aggregate Clarity report is missing and
> clarityCheck skips. Repair the setup, produce a trustworthy test report, and verify
> the existing quality gate. Keep JUnit 4 and our existing threshold.

**Expected trajectory:** the agent recognizes the trigger, loads `add-narrativetrace-clarity`,
links the missing `@ClassRule`, keeps the JUnit 4 framework and the original test execution
(never a static report, never a migration to Jupiter), and produces a trustworthy aggregate
report under the existing `minScore=0.50` threshold.

**Grading:**
- **Gates** (every model): `graders/verify.sh` — the agent-created test reports exist; the
  resolved `narrativeTrace.testFramework` is still `junit4` and the `test` task still runs
  through JUnit 4's options; the original JUnit 4 test still runs with no failures or errors;
  then the output-disabled case's own clean-reproduction, policy and strict-failure checks are
  reused and must all hold.
- **Report-only** (cheapest model), gates (mid model+): does the explanation correctly
  characterize the score weights, report fidelity and gate semantics against the [judgment
  rubric](../judgment-rubric.md)?

No study is named in this content; the case content here is original to this eval suite.
