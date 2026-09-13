# Case: happy-path

**Fixture:** `sixty-seconds` (the canonical fixture, this repository's own quickstart module),
scaffolded into a scratch copy with its workspace dependencies pre-resolved.

**Task prompt** (given to the agent, catalogue loaded):

> This project already has NarrativeTrace installed. Something feels off — I'm not sure the setup
> is actually correct. Can you check it and tell me what, if anything, needs fixing?

**Expected trajectory:** the agent recognizes the trigger, loads `narrativetrace-doctor`, runs
`./gradlew narrativetraceDoctor` (never edits a file — doctor is read-only), and reports back using
the tool's own findings rather than re-deriving them by hand.

**Grading** (§11.1 split):
- **Gates** (every model): `graders/verify.sh` — the report is well-formed JSON with all eleven
  finding ids present.
- **Report-only** (cheapest model), gates (mid model+): did the agent's summary correctly
  characterize the one real finding this fixture has today (`toolchain.launcher` fails — the
  fixture declares `junit-jupiter` without `junit-platform-launcher` on `testRuntimeOnly`) rather
  than claiming the project is fully clean?

No study is named in this content; the case content here is original to this eval suite.
