# NarrativeTrace Maven Example

Minimal proof that the NarrativeTrace runtime jars work from a plain Maven
build, not just Gradle: one traced service (`GreetingService`,
`DefaultGreetingService`), one JUnit 5 test
(`GreetingServiceTest`), wired entirely through `pom.xml` — no Gradle
involved anywhere in the loop. See
[`documentation/maven-guide.md`](../documentation/maven-guide.md) at the
repository root for the walkthrough this module exists to back: what each
part of the `pom.xml` does and why, and what differs from the Gradle plugin
path.

This module is **not published** — like `narrativetrace-junit4-example` and
`narrativetrace-agent-example`, it exists to be read and run, not depended
on. It is also deliberately **not** a Gradle subproject (it has no entry in
the repository's `settings.gradle.kts`): the whole point is a build that
Gradle never touches.

## Running it

The artifacts this `pom.xml` depends on are published to Maven Central, so
`mvn test` resolves them on its own. To run against a version that is not
released yet, install the modules locally first — from the **repository
root**:

```bash
./gradlew publishToMavenLocal
```

Then, from this directory:

```bash
mvn test
```

Expect to see the trace print during the run, and these files afterward:

```
target/narrativetrace/traces/GreetingServiceTest/greets_by_name.md
target/narrativetrace/clarity-report.md
target/narrativetrace/clarity-results.json
```

## What to look at

- **`pom.xml`** — every dependency is `test`-scoped (the traced code itself
  never references NarrativeTrace), the `-parameters` compiler flag is set
  explicitly (Gradle's plugin does this automatically; Maven has no plugin
  to do it for you), and Surefire's `systemPropertyVariables` point trace
  output at `target/narrativetrace` rather than the extension's Gradle-ism
  default of `build/narrativetrace`.
- **`GreetingServiceTest`** — identical to the JUnit 5 "Auto Context + Trace
  Output" recipe in the [Installation Guide](../documentation/installation-guide.md):
  `@ExtendWith(NarrativeTraceExtension.class)`, a `NarrativeContext`
  parameter, `NarrativeTraceProxy.trace(...)`. The extension does not know or
  care which build tool invoked it.
