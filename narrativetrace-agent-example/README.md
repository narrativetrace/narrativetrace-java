# NarrativeTrace Agent-Mode Example

Minimal proof that agent mode captures traces with **zero NarrativeTrace code in the test
body**. `GreetingService` (`src/main/java`) has no NarrativeTrace annotation, import, or
interface — it is instrumented at class-load time by the `-javaagent` this module's `test`
task attaches (see `build.gradle.kts`). `GreetingServiceAgentTest` instantiates and calls it
exactly as it would without NarrativeTrace on the classpath at all; `AgentNarrationExtension`
only resets and prints what the agent already recorded, through `AgentRuntime`'s shared
context — it does no tracing of its own.

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

## Running it

```bash
./gradlew :narrativetrace-agent-example:agentTest
```

The printed trace comes from `AgentNarrationExtension`, not from the test's own assertions —
watch the console output, not just the pass/fail line. `GreetingServiceAgentTest` runs on its
own `agentTest` task, not the default `test` task: JaCoCo's coverage agent and the
narrativetrace agent both instrument `GreetingService`'s bytecode when they share a JVM, and
JaCoCo cannot then match its execution data back to the class file — it silently zeroes that
class's coverage rather than failing loudly. `GreetingServiceTest` (a plain, untraced unit
test) covers the same class for the coverage gate; `agentTest` still runs on every
`./gradlew check`, just outside coverage.
