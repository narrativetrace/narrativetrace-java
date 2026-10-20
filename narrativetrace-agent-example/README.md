# NarrativeTrace Agent-Mode Example

Minimal proof that agent mode captures traces with **zero NarrativeTrace code in the test
body**. `GreetingService` (`src/main/java`) has no NarrativeTrace annotation, import, or
interface — it is instrumented at class-load time by the `-javaagent` the `agentTest` /
`agentTestJunit4` tasks attach (see `build.gradle.kts`). `GreetingServiceAgentFixture` (JUnit 5)
and `GreetingServiceAgentJunit4Fixture` (JUnit 4) instantiate and call it exactly as they would
without NarrativeTrace on the classpath at all, beyond the ordinary `@ExtendWith
(NarrativeTraceExtension.class)` / linked `NarrativeTraceClassRule`+`NarrativeTraceRule` wiring
any proxy-traced test already carries. Since §176, that is sufficient: both integrations route
the agent's shared capture into their own lifecycle-owned context, so these fixtures write the
same per-test Markdown/structural/JSON/diagram files (plus, for JUnit 5, the run's
`manifest.json`) that a proxy-traced test does — `GreetingServiceAgentTest` /
`GreetingServiceAgentJunit4Test` run each fixture through a nested JUnit Platform launch and
assert on the files it wrote.

This module is **not published** — like `narrativetrace-junit4-example`, it exists to be read
and run, not depended on.

## Three ways to attach NarrativeTrace

| Path | How it works | What it costs the target code |
|---|---|---|
| **Proxy** (`narrativetrace-junit4-example`) | `NarrativeTraceProxy.trace(impl, SomeInterface.class, context)` wraps a JDK dynamic proxy around an interface-implementing instance. | An interface, and one explicit `trace(...)` call per traced instance — nothing hidden, every traced call site is visible in the test. |
| **Agent** (this module) | A `-javaagent` rewrites method bytecode for every class under a configured package filter as the JVM loads it. | Nothing. The target class needs no interface and no NarrativeTrace reference of any kind — only the build (or the deployed JVM's command line) knows tracing is on. |
| **Container integrations** (`narrativetrace-spring`, `narrativetrace-micronaut`, `narrativetrace-servlet`, …) | A framework hook — a Spring `BeanPostProcessor`, a Micronaut interceptor, a servlet `Filter` — wraps beans or requests automatically as the container wires them. | Nothing in the traced class; one line of framework configuration (`@EnableNarrativeTrace`, a filter registration) at the composition root. |

Proxy is explicit and requires an interface; agent is automatic for whatever packages you
name, with no source-level footprint at all; container integrations sit in between, automatic
within a framework's own wiring rather than the whole JVM. `narrativetrace-examples/ejb4`
carries the same agent story into a real deployed app server (WildFly, over HTTP, read back
from the container log) if you want to see it outside a Gradle test JVM.

## Where the logger is configured

`AgentRuntime` builds its pipeline through the same `PipelineBootstrap` as
every other integration (proxy, Spring, JUnit 5) — agent mode is not a
separate narration path. `build.gradle.kts` adds `narrativetrace-slf4j` and
`logback-classic` as `testRuntimeOnly` so `Slf4jTraceEventListener`
auto-attaches reflectively (see
[Configuration Guide §7](../documentation/configuration-guide.md#7-slf4j-configuration)),
and [`src/test/resources/logback-test.xml`](src/test/resources/logback-test.xml)
sets the `narrativetrace` logger to `TRACE`. This is a separate, durable
narration path from the per-test files the JUnit extension/rule write via
`captureTrace()` — both fire from the same `agentTest`/`agentTestJunit4` run.

This module's `-javaagent` weaves the whole `ai.narrativetrace.examples.agent`
package (see `packages=` in `build.gradle.kts`), so the SLF4J trace also
narrates the fixture classes' own methods, not only `GreetingService.greet` —
expected, since the filter is a package, not a class.

## Running it

```bash
./gradlew :narrativetrace-agent-example:agentTest        # JUnit 5 fixture
./gradlew :narrativetrace-agent-example:agentTestJunit4   # JUnit 4 fixture
```

Each task's own test (`GreetingServiceAgentTest` / `GreetingServiceAgentJunit4Test`) asserts
directly on the files its fixture wrote under a temporary `narrativetrace.outputDir` — read
those assertions, not the console, to see what agent mode produced. Neither fixture runs on the
default `test` task: JaCoCo's coverage agent and the narrativetrace agent both instrument
`GreetingService`'s bytecode when they share a JVM, and JaCoCo cannot then match its execution
data back to the class file — it silently zeroes that class's coverage rather than failing
loudly. `GreetingServiceTest` (a plain, untraced unit test) covers the same class for the
coverage gate; both agent-mode tasks still run on every `./gradlew check`, just outside
coverage.
