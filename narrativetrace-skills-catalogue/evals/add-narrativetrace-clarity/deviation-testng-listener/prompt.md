# Case: deviation-testng-listener

**Fixture:** `narrativetrace-skills-catalogue/evals/fixtures/clarity-testng-listener` (a TestNG
service whose build already applies the plugin with `testFramework.set("testng")`, with one test
that reaches the context through `NarrativeTraceListener.context()` and no `@Listeners`),
scaffolded into a scratch copy with its workspace dependencies pre-resolved.

**Task prompt** (given to the agent, catalogue loaded):

> Use the add-narrativetrace-clarity skill. This service uses TestNG. Check that
> NarrativeTrace is set up for it and that the test run writes its trace files. Fix what is
> missing, and keep TestNG as the runner.

**Expected trajectory:** the agent recognizes the trigger, loads `add-narrativetrace-clarity`,
takes the TestNG branch (keeps `testFramework.set("testng")` and the `useTestNG()` runner, adds no
Jupiter or `useJUnitPlatform()`, adds no `@Listeners` because the listener attaches itself through
TestNG's service loader), runs the tests and confirms the trace files under
`build/narrativetrace/traces/`.

**Grading:**
- **Gates** (every model): `graders/verify.sh` — the resolved `narrativeTrace.testFramework` is
  still `testng`, the `test` task still runs through TestNG's options, no Jupiter artifact is on
  the test classpath, and a clean `test` run passes and writes a trace file for the fixture's test.

No study is named in this content; the case content here is original to this eval suite.
