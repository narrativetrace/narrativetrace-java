# Sixty Seconds

This module **is** [`documentation/first-10-minutes.md`](../documentation/first-10-minutes.md),
"See a trace in 60 seconds" — not an illustration of it. Every file under `src/main/java` is
embedded verbatim into that page (`snippetCheck`/`snippetSync` in the root `build.gradle.kts`, the
same mechanism `translationCheck` already uses for translated code blocks), and `SixtySecondsTest`
runs the page's own call through the real `NarrativeTraceProxy`, proving the narrative the page
shows is what the code actually produces (rule 8, docs as tests).

This module is **not published** — it exists to be read and run, not depended on.

## Two run modes, one project

- **`./gradlew :sixty-seconds:run`** — the page's "3. Run it" step: `Main` prints the one trace
  line, nothing else on the classpath.
- **`./gradlew :sixty-seconds:runWithLogger`** — the page's "Send it to your logger" postscript:
  the *same* `Main.java`, run against a classpath that also carries `narrativetrace-slf4j` and
  `logback-classic` (added only to this task's own classpath — see `build.gradle.kts` for why the
  base `run` above never carries them). `src/main/resources/logback.xml` is the page's config file;
  it sits on every classpath, including the base `run`'s, but is inert without those two jars.

## What the test proves, and what it doesn't

`SixtySecondsTest` writes two artifacts:

- The runtime's own JUnit 5 test integration (`@ExtendWith(NarrativeTraceExtension.class)`) writes
  its usual `build/narrativetrace/traces/SixtySecondsTest/` artifacts, output on by default — this
  is what "the tutorial call runs through the runtime's own test integration" means.
- A second, byte-stable file (`build/narrativetrace/sixty-seconds/see-a-trace.txt`) holding exactly
  the line `Main.java` prints, since the extension's own artifact frames the scenario name above it
  and the page shows the bare console line. `snippetCheck`/`snippetSync` embed *this* file into the
  page's output block, masking the duration.

The page's `build.gradle.kts` block and the SLF4J dependency diff are **not** embedded snippets:
they show Maven Central coordinates a standalone consumer would use, and this module deliberately
depends on the in-tree modules by source (`project(...)`) instead — see the comment at the top of
`build.gradle.kts`. Proving those exact coordinates resolve and run is the cold walk's job (layer 2,
not yet built), not this module's.
