# Case: happy-path

**Fixture:** `narrativetrace-skills/evals/fixtures/empty-project` (a cold install from an empty
project, harness §6 Q5), scaffolded into a scratch copy with nothing installed yet.

**Task prompt** (given to the agent, catalogue loaded):

> This is a brand new Java project. I want to add NarrativeTrace and see a trace from a real
> method call within the next few minutes.

**Expected trajectory:** the agent recognizes the trigger, loads `add-narrative-tracing`, adds
`narrativetrace-core` and `narrativetrace-proxy` with Gradle, wraps a class with
`NarrativeTraceProxy.trace`, runs it, and shows the rendered trace — then runs
`narrativetrace-doctor` and reports its findings rather than declaring success unprompted.

**Grading** (§11.1 split):
- **Gates** (every model): `graders/verify.sh` — `build.gradle.kts` declares
  `narrativetrace-proxy`, at least one rendered `.md` trace exists under `build/narrativetrace`,
  and every `toolchain.*` doctor finding holds.
- **Report-only** (cheapest model), gates (mid model+): did the agent run the final
  `narrativetrace-doctor` seam step and read its report, rather than declaring the project done
  unprompted?

No study is named in this content; the case content here is original to this eval suite.
