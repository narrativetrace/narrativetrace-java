# NarrativeTrace JUnit 4 Example

Minimal example for legacy suites still on JUnit 4 (via the vintage engine —
`org.junit.vintage:junit-vintage-engine` runs `org.junit:junit` `@Test`s on the JUnit
Platform, which is what every quality gate in this repository already runs against). Both
`GreetingServiceTest` and `GreetingServiceNarrativeTestCaseTest` trace the same
`DefaultGreetingService.greet` call through `NarrativeTraceProxy`; only the rule wiring
differs.

- **`GreetingServiceTest`** — the two-rule form, `@ClassRule NarrativeTraceClassRule` +
  `@Rule NarrativeTraceRule`, wired explicitly. This is the primary API: it is a single
  `@Rule`/`@ClassRule` pair, so it composes with whatever base class a legacy suite already
  extends.
- **`GreetingServiceNarrativeTestCaseTest`** — the same scenario via `extends
  NarrativeTestCase`, the low-ceremony form for suites free to spend their one superclass
  slot on it. Both stay side by side on purpose — some teams do not want inheritance.

This module is **not published** — it exists to be read and run, not depended on.

If a suite can drop rule wiring of any kind, `narrativetrace-agent-example` shows the other
end of the spectrum: a `-javaagent` traces a plain class with no NarrativeTrace code
anywhere in it, no rule and no `NarrativeTraceProxy.trace(...)` call either.

## Running it

```bash
./gradlew :narrativetrace-junit4-example:test
```
