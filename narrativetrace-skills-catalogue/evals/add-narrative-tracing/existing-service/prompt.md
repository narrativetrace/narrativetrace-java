# Case: existing-service

**Fixture:** `narrativetrace-skills-catalogue/evals/fixtures/existing-service` (a project that
already has a passing test suite, harness §6), scaffolded into a scratch copy with nothing
installed yet.

**Task prompt** (given to the agent, catalogue loaded):

> This service already has a test suite. Add NarrativeTrace and show me the trace of one of my
> existing tests — I want to find the file afterwards, not just see it scroll by.

**Expected trajectory:** the agent recognizes the trigger, loads `add-narrative-tracing`, adds
`narrativetrace-core` and `narrativetrace-proxy` with Gradle, wraps `InvoiceService` with
`NarrativeTraceProxy.trace` inside one existing test (never a new demo main, since the project
already has tests), adds the JUnit 5 test integration (`narrativetrace-junit5`) with output on,
runs the suite, and points at the rendered `.md` file it wrote under `build/narrativetrace/` —
then runs `narrativetrace-doctor` and reports its findings rather than declaring success
unprompted.

**Grading** (§11.1 split):
- **Gates** (every model): `graders/verify.sh` — `build.gradle.kts` declares
  `narrativetrace-proxy` and the JUnit 5 test integration (`narrativetrace-junit5`); the grader
  itself runs `./gradlew test -q` and asserts it exits 0 (the fixture's own test still passes),
  then asserts at least one rendered `.md` trace AND one structural `.nt` trace exist under
  `build/narrativetrace/`; every `toolchain.*` doctor finding holds.
- **Report-only** (cheapest model), gates (mid model+): did the agent run the final
  `narrativetrace-doctor` seam step and read its report, rather than declaring the project done
  unprompted?

No study is named in this content; the case content here is original to this eval suite.
