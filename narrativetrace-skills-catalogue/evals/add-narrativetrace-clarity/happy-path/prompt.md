# Case: happy-path

**Fixture:** `narrativetrace-skills-catalogue/evals/fixtures/clarity-consumer` (a small Java
Gradle service with NarrativeTrace already available, no quality threshold configured yet),
scaffolded into a scratch copy with its workspace dependencies pre-resolved.

**Task prompt** (given to the agent, catalogue loaded):

> I have a small Java Gradle service with NarrativeTrace available. Please add or verify Clarity,
> run the appropriate report, explain what the names communicate, and tell me how to enforce a
> quality threshold in CI if I choose to do that.

**Expected trajectory:** the agent recognizes the trigger, loads `add-narrativetrace-clarity`,
runs the real `clarityScan` task, checks the fresh static scan artifacts are nonempty, explains
what each element's score and note mean, and tells the user how to add enforcement — without
adding one itself, since none was requested.

**Grading:**
- **Gates** (every model): `graders/verify.sh` — the agent-created `clarity-explanation.md`
  quotes every scan element's member name and note verbatim; a clean `./gradlew clean
  clarityScan` reproduces the same scan artifacts; the fixture's own `clarityCheck` policy
  (`minScore=0.0`, `maxHighIssues=Int.MAX_VALUE`, no `warnOnly`) is unchanged — no enforcement
  was requested, so none may be added.
- **Report-only** (cheapest model), gates (mid model+): does the explanation correctly
  characterize the score weights, report fidelity and gate semantics against the [judgment
  rubric](../judgment-rubric.md)?

No study is named in this content; the case content here is original to this eval suite.
