# NarrativeTrace Maven Guide

The NarrativeTrace runtime jars are ordinary Maven artifacts — nothing in the
library itself is Gradle-specific. What *is* Gradle-specific is the
[Gradle Plugin](gradle-plugin-guide.md): it wires the compiler flag, the
dependencies, the test JVM properties, and three extra build tasks for you.
None of that is available under Maven because there is no NarrativeTrace
Maven plugin — this guide is the manual recipe, and it names each thing the
Gradle plugin would otherwise have done and what to do about it instead.

The working recipe below is [`narrativetrace-maven-example`](../narrativetrace-maven-example)
at the repository root: a complete, runnable `pom.xml`, one traced service,
one JUnit 5 test. Run it yourself with `mvn test` from that directory once
you've completed [Before you start](#before-you-start) below — everything
this guide describes is exercised there for real, not just written down.

## Table of Contents

- [Before you start: where the artifacts come from](#before-you-start-where-the-artifacts-come-from)
- [Dependencies](#dependencies)
- [The `-parameters` compiler flag](#the--parameters-compiler-flag)
- [Surefire: wiring trace output](#surefire-wiring-trace-output)
- [Surefire: the JUnit 5 provider](#surefire-the-junit-5-provider)
- [The test itself](#the-test-itself)
- [What the Gradle plugin does that Maven has to do by hand](#what-the-gradle-plugin-does-that-maven-has-to-do-by-hand)
- [See Also](#see-also)

## Before you start: where the artifacts come from

`ai.narrativetrace:narrativetrace-core` and its siblings are published to
Maven Central, so a `pom.xml` that names a released `<version>` resolves them
like any other dependency — no repository configuration needed.

To build against a version that is not released yet — a local change, or a
`narrativetraceVersion` newer than the last release — install the modules into
your own `~/.m2/repository` first. From the repository root:

```bash
./gradlew publishToMavenLocal
```

This installs every publishable module under the version declared in
`gradle.properties` (`narrativetraceVersion`); `mavenLocal()`-published
artifacts land in Maven's own default local repository, so a `pom.xml` naming
that same `<version>` picks them up. Re-run the command after pulling changes
that bump the version, or your `pom.xml` will ask for a version that was never
installed.

## Dependencies

Only test code needs to know NarrativeTrace exists — a traced service can be
a plain POJO with no NarrativeTrace reference of any kind; tracing is added
at the test boundary via `NarrativeTraceProxy.trace(...)`. So every
NarrativeTrace dependency below is `test` scope:

```xml
<dependency>
  <groupId>ai.narrativetrace</groupId>
  <artifactId>narrativetrace-junit5</artifactId>
  <version>${narrativetrace.version}</version>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>ai.narrativetrace</groupId>
  <artifactId>narrativetrace-proxy</artifactId>
  <version>${narrativetrace.version}</version>
  <scope>test</scope>
</dependency>
```

`narrativetrace-junit5` brings `narrativetrace-core`, `narrativetrace-proxy`,
`narrativetrace-diagrams`, `narrativetrace-clarity`, `narrativetrace-glossary`
and `junit-jupiter-api` transitively — the same way it does under Gradle.
`narrativetrace-proxy` is still declared directly: a class a test imports by
name should be a direct dependency, not an implicit one riding along on
someone else's transitive graph.

Add `narrativetrace-slf4j` (also `test` scope) to route trace events through
your SLF4J provider — `PipelineBootstrap` discovers it reflectively by
classname when it is on the classpath, so nothing else changes:

```xml
<dependency>
  <groupId>ai.narrativetrace</groupId>
  <artifactId>narrativetrace-slf4j</artifactId>
  <version>${narrativetrace.version}</version>
  <scope>test</scope>
</dependency>
```

Round out the test classpath with JUnit 5, AssertJ, and an SLF4J provider —
see [Surefire: the JUnit 5 provider](#surefire-the-junit-5-provider) for why
`junit-platform-launcher` is not optional.

## The `-parameters` compiler flag

Without it, NarrativeTrace renders parameter names as `arg0`, `arg1`, … —
see [Troubleshooting](troubleshooting.md#parameters-show-as-arg0-arg1). The
Gradle plugin adds this flag to every `JavaCompile` task automatically; a
Maven build has to set it itself, on `maven-compiler-plugin`:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <version>3.13.0</version>
  <configuration>
    <parameters>true</parameters>
  </configuration>
</plugin>
```

This is the single most consequential line in the whole `pom.xml` — leave it
out and every trace still captures, it just captures `arg0` instead of
`name`.

## Surefire: wiring trace output

The JUnit 5 extension writes trace files when `narrativetrace.output=true` is
set as a system property or JUnit configuration parameter — under Gradle this
usually happens via `src/test/resources/junit-platform.properties` (see
[Installation Guide § JUnit 5](installation-guide.md#4-configure-trace-output),
"No Gradle wiring needed" — that file works identically under Maven, since
JUnit Platform configuration parameters are not a build-tool concept). This
guide instead wires it on Surefire directly, since the point of a Maven-native
recipe is showing the Maven-native mechanism:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <version>3.5.2</version>
  <configuration>
    <systemPropertyVariables>
      <narrativetrace.output>true</narrativetrace.output>
      <narrativetrace.outputDir>${project.build.directory}/narrativetrace</narrativetrace.outputDir>
      <narrativetrace.format>markdown</narrativetrace.format>
    </systemPropertyVariables>
  </configuration>
</plugin>
```

The one Maven-specific detail: the extension's default `narrativetrace.outputDir`
is `build/narrativetrace`, a Gradle-ism. A Maven build's directory is
`target/`, so this guide points it at `${project.build.directory}/narrativetrace`
explicitly — otherwise trace files land in a `build/` directory nobody else
in the project uses or `.gitignore`s.

Every other extension property (`narrativetrace.level`,
`narrativetrace.bufferCapacity`, `narrativetrace.approval`,
`narrativetrace.approvedDir`, …) is set the same way, as another
`systemPropertyVariables` entry — the full list is on
[`NarrativeTraceExtension`'s Javadoc](../narrativetrace-junit5/src/main/java/ai/narrativetrace/junit5/NarrativeTraceExtension.java)
and in the [Installation Guide](installation-guide.md#4-configure-trace-output).

## Surefire: the JUnit 5 provider

Surefire auto-detects the JUnit Platform provider from `junit-jupiter` on the
test classpath, but since Surefire 3.x it additionally needs
`junit-platform-launcher` there explicitly — omit it and `mvn test` fails
before it reaches a single `@Test`:

```xml
<dependency>
  <groupId>org.junit.platform</groupId>
  <artifactId>junit-platform-launcher</artifactId>
  <version>1.11.4</version>
  <scope>test</scope>
</dependency>
```

## The test itself

Identical to the Gradle path — the extension and the proxy call do not know
or care which build tool invoked them:

```java
@ExtendWith(NarrativeTraceExtension.class)
class GreetingServiceTest {

  @Test
  void greetsByName(NarrativeContext context) {
    var service =
        NarrativeTraceProxy.trace(new DefaultGreetingService(), GreetingService.class, context);

    var greeting = service.greet("Alice");

    assertThat(greeting).isEqualTo("Hello, Alice!");
  }
}
```

`mvn test` writes `target/narrativetrace/traces/GreetingServiceTest/greets_by_name.md`,
alongside a suite-level `clarity-report.md` and `clarity-results.json` — both
written by the JUnit 5 extension itself, not by any Gradle-only machinery, so
they appear exactly the same way under Maven.

## What the Gradle plugin does that Maven has to do by hand

Three things the plugin registers as Gradle tasks have no Maven equivalent,
because they are tasks, not library behavior. Here is what to do instead of
each:

| Gradle plugin feature | What it does | Maven equivalent |
|---|---|---|
| `clarityCheck` | Reads `clarity-results.json` after `test` and fails the build below a configured threshold | No build-time gate ships. `clarity-results.json` is written regardless (previous section) — read it in CI with a one-line script, or call `ClarityAnalyzer` directly inside a test and assert on `.overallScore()` yourself if you want a hard per-scenario failure |
| `approveNarratives` | Promotes every reviewed `*.received.nt` file to `*.approved.nt` | `NarrativeApproval.promoteReceived(Path)` — the exact method the Gradle task calls, documented to "work standalone for non-Gradle builds" — call it from a one-off `main`, or simply rename each `*.received.nt` to `*.approved.nt` by hand; that rename is the whole of what promotion does |
| `mode = "agent"` (`-javaagent` wiring) | Resolves the agent jar and adds `-javaagent:...` to every `Test` task | Add `narrativetrace-agent` with `<classifier>standalone</classifier>` as a `test`-scope dependency, resolve its path with `maven-dependency-plugin`'s `properties` goal, then reference the resulting property on Surefire's `<argLine>`: `-javaagent:${ai.narrativetrace:narrativetrace-agent:jar:standalone}=packages=com.example.*` |

`clarityScan` and `glossaryScan` (standalone analysis without running tests)
have no Maven equivalent either — both need compiled classes on a classpath
the Gradle plugin already has assembled; under Maven, run the suite (which
already produces the clarity report) rather than reaching for a
tests-not-required scan.

## See Also

- [`narrativetrace-maven-example`](../narrativetrace-maven-example) — the
  complete, runnable `pom.xml` this guide describes
- [Installation Guide](installation-guide.md) — the five integration paths,
  and JUnit 5 extension configuration in full
- [Gradle Plugin Guide](gradle-plugin-guide.md) — the automated equivalent of
  everything on this page
- [Troubleshooting](troubleshooting.md) — symptom → cause → fix, including
  the `-parameters` flag
- [Clarity Guide](clarity-guide.md) — the scoring model behind
  `clarity-results.json` and `ClarityAnalyzer`
