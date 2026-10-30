# NarrativeTrace Java Installation Guide

This guide covers installing and wiring NarrativeTrace Java in a JVM project.

## Prerequisites

- Java 17+
- Gradle build

## Compatibility

These are the versions this release builds and tests against.
"Brought" means the module depends on it and Gradle
resolves it for you; "yours" means the module compiles against it but does not
depend on it — you already have it, and NarrativeTrace uses whatever version
you bring.

| Requirement | Version | Who supplies it |
|---|---|---|
| Java | **17+** — compiled and tested on 17; JDK 21 exercised by a scheduled CI job (the virtual-thread tests only run there) | yours |
| Gradle (for the plugin) | **8.0+** — developed and tested against 8.14.2 | yours |
| JUnit 5 | 5.11.4 (`narrativetrace-junit5` exposes `junit-jupiter-api` as `api`) | brought |
| JUnit 4 | 4.13.2 | brought |
| TestNG | 7.10.2 | brought |
| Spring Framework | 6.2.3 — i.e. Spring Boot 3.x | brought |
| Micronaut | 4.7.6 | brought |
| Jakarta Servlet | 6.0 (`narrativetrace-servlet`) | yours |
| SLF4J | 2.0.16 | brought |
| Micrometer context-propagation | 1.1.2 (`narrativetrace-micrometer`; also needed by `ContextPropagatingTaskDecorator`) | brought by the micrometer module, yours if you use the decorator without it |
| OpenTelemetry API | 1.46.0 (`narrativetrace-opentelemetry`) | yours |
| ASM | 9.7.1 — shaded into `narrativetrace-agent`, never on your classpath | brought |

### Runtime platforms

The table above is about versions; this is about *where the library runs*.

| Platform | Supported | Notes |
|---|---|---|
| Server and desktop JVMs (HotSpot, OpenJ9, GraalVM on the JVM) | **Yes** | The tested target |
| Android | **No** | Not tested, and not merely untested — see below |
| iOS and other Apple platforms | **N/A** | Use the Swift edition |
| GraalVM native image | **Untested** | The proxy and agent paths rely on reflection; no reachability metadata ships today |

**Why Android is a "no" rather than a "not yet".** Three mechanisms degrade
*silently* under R8/ProGuard minification: SPI discovery loses its
`META-INF/services` entries, so extensions never load; the pipeline bootstrap
resolves the SLF4J listener by name, so synchronous narration disappears; and
value rendering reflects over fields and getters, so a narrative renders as
`→ a.b(c: "x")` instead of readable names. None of these fail loudly, which
makes "it seemed to work in a debug build" the worst possible outcome. Honest
Android support needs shipped consumer keep rules and a real Android CI, and
neither exists yet. `narrativetrace-agent` can never work there at all —
Android has no `java.lang.instrument`.

The library no longer *crashes* on a runtime without `java.lang.ProcessHandle`
(the process id is simply reported as absent, which the schema permits), but
not crashing is not the same as being supported.

The plugin enforces the first two rows at apply time: an unsupported Gradle or a
build targeting Java below 17 fails immediately, naming the requirement, instead
of failing later inside the library.

Two failure modes worth naming, because neither error message points at the
cause:

- **`arg0`, `arg1` instead of parameter names** — the `-parameters` compiler
  flag is missing. It is a hard requirement, not a nicety; see step 1 below.
- **`NoClassDefFoundError` at runtime** — a "yours" row is missing from the
  runtime classpath. The modules compile against those APIs deliberately, so
  that a servlet-free or OTel-free application carries no extra dependency.

## Quick Start with Gradle Plugin

The Gradle plugin handles all wiring automatically — dependencies, compiler flags, and test JVM configuration.

Give Maven Central the first look in `pluginManagement` — the plugin and its marker publish to
Maven Central with every release, while the Gradle Plugin Portal listing follows Gradle's
approval of a new plugin id, so Central is what makes a fresh release resolvable the day it
ships:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
```

```kotlin
plugins {
    id("ai.narrativetrace") version "0.5.5"
}
```

That's it — no JUnit dependency needed either: with `testFramework = "junit5"` the plugin puts the Jupiter engine on `testRuntimeOnly`, because the JUnit Platform it configures refuses to start without one. Run `./gradlew test` and trace output appears in `build/narrativetrace/`.

To enforce naming quality thresholds:

```kotlin
narrativeTrace {
    clarity {
        minScore.set(0.80)
        maxHighIssues.set(0)
    }
}
```

Now `./gradlew check` fails if any scenario's clarity score drops below 0.80 or has any HIGH-severity issues.

See the [Gradle Plugin Guide](gradle-plugin-guide.md) for the full DSL reference, interception modes, module opt-in, and recipes.

## Manual Setup

The sections below cover manual installation for projects that don't use the Gradle plugin.

### Prerequisites

- Compiler parameter metadata enabled (`-parameters`)

## 1. Enable Parameter Name Retention

NarrativeTrace uses method parameter names in trace output. Without `-parameters`, traces show `arg0`, `arg1`, etc.

```kotlin
// build.gradle.kts
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}
```

## 2. Add Dependencies

Start with the minimum stack and then add only the integrations you need.

```kotlin
dependencies {
    // Minimum
    implementation("ai.narrativetrace:narrativetrace-core:0.5.5")
    implementation("ai.narrativetrace:narrativetrace-proxy:0.5.5")

    // Optional integrations
    testImplementation("ai.narrativetrace:narrativetrace-junit5:0.5.5")
    testImplementation("ai.narrativetrace:narrativetrace-junit4:0.5.5")  // for JUnit 4
    testImplementation("ai.narrativetrace:narrativetrace-testng:0.5.5")  // for TestNG
    implementation("ai.narrativetrace:narrativetrace-spring:0.5.5")
    implementation("ai.narrativetrace:narrativetrace-slf4j:0.5.5")
    implementation("ai.narrativetrace:narrativetrace-diagrams:0.5.5")
    implementation("ai.narrativetrace:narrativetrace-clarity:0.5.5")
    implementation("ai.narrativetrace:narrativetrace-opentelemetry:0.5.5")
    implementation("ai.narrativetrace:narrativetrace-micrometer:0.5.5")
    implementation("ai.narrativetrace:narrativetrace-agent:0.5.5")
    implementation("ai.narrativetrace:narrativetrace-servlet:0.5.5")
    implementation("ai.narrativetrace:narrativetrace-spring-web:0.5.5")
    implementation("ai.narrativetrace:narrativetrace-micronaut:0.5.5")
    implementation("ai.narrativetrace:narrativetrace-micronaut-http:0.5.5")
}
```

## 3. Choose an Integration Path

### Option A: JDK Proxy (works in any Java app)

```java
var context = new ThreadLocalNarrativeContext();
var tracedOrderService = NarrativeTraceProxy.trace(orderService, OrderService.class, context);

tracedOrderService.placeOrder("C-1234", "SKU-KB", 2);
System.out.println(new IndentedTextRenderer().render(context.captureTrace()));
context.reset();
```

Use this when services are interface-based.

### Option B: Spring Auto-Wrapping

```java
@Configuration
@EnableNarrativeTrace(basePackages = {"com.example.myapp"})
public class AppConfig {}
```

Use this when you want bean post-processing to wrap eligible beans automatically.

### Option B2: Micronaut Auto-Wrapping

```yaml
# src/main/resources/application.yml
narrativetrace:
  base-packages:
    - com.example.myapp
```

No enable annotation needed — the `narrativetrace-micronaut` module is auto-discovered on the classpath. All beans whose class and interfaces match the configured packages are wrapped in tracing proxies.

See the [Micronaut Integration Guide](micronaut-integration-guide.md) for HTTP filter setup and configuration details.

### Option C: JUnit 5 Auto Context + Trace Output

```java
@ExtendWith(NarrativeTraceExtension.class)
class OrderServiceTest {
    @Test
    void customerPlacesOrder(NarrativeContext context) {
        var orderService = NarrativeTraceProxy.trace(new DefaultOrderService(), OrderService.class, context);
        orderService.placeOrder("C-1234", "SKU-KB", 2);
    }
}
```

Features:
- Per-test `NarrativeContext` via parameter injection
- Automatic failure trace printing to console
- Scenario name derived from test method name (`customerPlacesOrder` → "Customer places order")
- Writes `.md`, `.json`, `.mmd` per test by default, plus a suite-level `clarity-report.md` — set `narrativetrace.output=false` to opt out

### Option D: JUnit 4 Auto Context + Trace Output

```java
public class OrderServiceTest {
    @Rule
    public NarrativeTraceRule narrativeTrace = new NarrativeTraceRule();

    @Test
    public void customerPlacesOrder() {
        NarrativeContext context = narrativeTrace.context();
        var orderService = NarrativeTraceProxy.trace(new DefaultOrderService(), OrderService.class, context);
        orderService.placeOrder("C-1234", "SKU-KB", 2);
    }
}
```

Features:
- Per-test `NarrativeContext` via `narrativeTrace.context()`
- Automatic failure trace printing to console
- Scenario name derived from test method name (`customerPlacesOrder` → "Customer places order")
- Writes `.md`, `.json`, `.mmd` per test by default — set `-Dnarrativetrace.output=false` to opt out
- Add `@ClassRule` with `NarrativeTraceClassRule` for suite-level `clarity-report.md` and console summary

> **Seeing the failure trace in your terminal:** the "failure trace printing to console" above is written to the test process's standard output, which Gradle captures into the XML/HTML report — a vanilla terminal shows nothing. To surface it live in the console, enable standard-stream logging on the `test` task:
>
> ```kotlin
> tasks.test {
>     testLogging.showStandardStreams = true
> }
> ```
>
> The trace files under `build/narrativetrace/` are written regardless; this only affects what the console shows.

Configuration uses system properties (JUnit 4 has no `junit-platform.properties`):
- `narrativetrace.output` — `true`/`false` (default: `true`)
- `narrativetrace.outputDir` — path (default: `build/narrativetrace`)
- `narrativetrace.format` — `markdown`/`text`/`mermaid`/`plantuml` (default: `markdown`)

### Option D2: TestNG Auto Context + Trace Output

With the Gradle plugin, `narrativeTrace { testFramework.set("testng") }` is the whole setup: it adds
`narrativetrace-testng` on `testImplementation`, switches the `test` task to `useTestNG()` and passes the
`narrativetrace.*` system properties; the listener attaches itself. Declare `org.testng:testng` yourself, as
you would JUnit 4 (the module's `api` dependency brings TestNG 7.10.2 if you do not). Without the plugin:

Add `narrativetrace-testng` on `testImplementation` and nothing else: TestNG's service loader
registers its listener (`NarrativeTraceListener`) for every run — Gradle's `useTestNG()`, Maven
surefire, the IDE. `@Listeners(NarrativeTraceListener.class)` on a test class registers it
explicitly too; both at once still trace each test once.

```java
public class OrderServiceTest {
    private final OrderService orderService = NarrativeTraceProxy.trace(
        new DefaultOrderService(), OrderService.class, NarrativeTraceListener.context());

    @Test
    public void customerPlacesOrder() {
        orderService.placeOrder("C-1234", "SKU-KB", 2);
    }
}
```

What gets written — the same files as the JUnit integrations:
- One trace per test method under `build/narrativetrace/traces/<TestClass>/`: `.md`, `.json`, a sequence diagram and the structural `.nt`
- A failing test prints its failure report and, on standard error, `narrativetrace: trace of <Class.method> → <absolute path>`
- When the run ends: `manifest.json`, `INDEX.md` (failing tests first), `clarity-report.md` and the console footer, which repeats the failing tests' pointers
- A data-provider row, an `invocationCount` repetition or a `@Factory` instance is one invocation with its own files — `<method>-001.md`, labelled `Class.method[#1]`
- Approval traces (`narrativetrace.approval=true`) compare each test against its committed `.approved.nt`, as under JUnit

Configuration uses the same system properties as JUnit 4 (`narrativetrace.output`, `narrativetrace.outputDir`,
`narrativetrace.format`, `narrativetrace.level`, `narrativetrace.approval`, `narrativetrace.approvedDir`).

Limits:
- `NarrativeTraceListener.context()` is how a test reaches the context — TestNG cannot inject it as a test-method parameter
- The run's files are written when the TestNG execution ends; TestNG has no per-class end, so nothing is written per class
- One pipeline serves the whole test JVM; each test sees only the calls made on its own thread while it runs, `parallel="methods"` included
- A test with `timeOut` runs its body on a separate TestNG thread, so its calls are not in its trace
- A data-provider row is labelled by its number, never by its arguments: rendering never runs your objects' `toString`
- No glossary harvest (`narrativetrace.glossary`) — that step belongs to the JUnit 5 extension

### Option E: Java Agent (no proxy wiring)

```bash
java -javaagent:narrativetrace-agent.jar=packages=com.example.myapp.* -jar your-app.jar
```

Use this when you want bytecode instrumentation for classes under selected package prefixes. When no CLI args are provided, the agent falls back to `narrativetrace.properties` on the classpath. Attached to a test JVM that also carries `narrativetrace-junit5`'s extension or `narrativetrace-junit4`'s rules, the agent's captures land in the same per-test files a proxy-traced test produces — no proxy, no annotation needed in the instrumented class (see [Configuration Guide §3](configuration-guide.md#3-pure-java--agent-configuration-narrativetraceproperties)).

Agent argument format: `packages=<pkg1>;<pkg2>;...`

Package patterns support wildcards:

| Pattern | Matches |
|---|---|
| `com.example.*` | All classes under `com.example` and subpackages |
| `com.example.**` | Same as `.*` (both match all subpackages) |
| `com.example` | Same as `com.example.*` (bare prefix with boundary enforcement) |

Multiple packages:

```bash
java -javaagent:narrativetrace-agent.jar=packages=com.example.app.*;com.example.shared.* -jar app.jar
```

Package separators are semicolons (`;`), not commas. Unknown keys are ignored; duplicate keys are rejected.

#### Narration needs an SLF4J provider — the agent never brings one

The `-standalone` agent jar bundles everything it needs *except* a logging
backend. That is deliberate: your host's logging stack is yours, and an agent
that smuggled in a second provider would fight the one you already have.

So a minimal host with no provider on its classpath sees SLF4J say so, once, at
startup:

```text
SLF4J(W): No SLF4J providers were found.
SLF4J(W): Defaulting to no-operation (NOP) logger implementation
```

Nothing is broken — traces are still captured, and `captureTrace()` still
returns them — but nothing is written to a log. Two one-line fixes, depending on
what you want:

| You want | Do this |
|---|---|
| Narration in your logs | Put a provider on the classpath (`logback-classic`, `slf4j-simple`, …), or pass `loggingJars=/path/to/provider.jar` when the host has no reachable classpath |
| No narration at all | Attach with `loggerName=` (empty), or set `-Dnarrativetrace.narration=off` |

With narration switched off the agent never touches SLF4J at all — it starts
silent. **Without an SLF4J provider on the classpath, the agent is silent
too** — it still captures, but narration goes to a NOP logger.

#### Classes the agent's runtime cannot see load uninstrumented

Matching the package filter is necessary but not sufficient. Instrumented
bytecode calls `ai.narrativetrace.agent.AgentRuntime` by name, so a class is
woven only when the class loader that defined it can resolve that type. Two
kinds of class cannot:

- **bootstrap-injected classes** — mockito's inline mock maker, for one, injects
  its method dispatcher into the bootstrap class loader
- **isolated loaders** that do not delegate to the loader the agent arrived on

Those load uninstrumented, and the agent says so once per loader:

```text
narrativetrace-agent: cannot instrument org/example/Injected (ai.narrativetrace.agent.AgentRuntime is not visible from the bootstrap class loader); classes from that loader load uninstrumented
```

Once per loader, never once per class — a hot loader must not flood your error
stream. Everything else in the same JVM is still traced normally; if the classes
you care about are among the uninstrumented ones, narrow `packages=` so the
filter stops matching them, or trace them through the proxy instead.

#### Generated and compiler-made classes are never instrumented

Some classes under a matched package are never a business boundary — they are
bytecode a generator or the compiler made, not code you wrote. These are
excluded unconditionally, with no switch to re-enable them:

- a JDK dynamic proxy (`$Proxy0`, `$Proxy17`, …)
- a class whose name carries a generator's own marker: `$$` (CGLIB's
  `$$EnhancerByCGLIB$$`, Spring's `$$SpringCGLIB$$`, a lambda's own hidden
  class), `$ByteBuddy$` (Byte Buddy's default naming), or `$MockitoMock$`
  (Mockito's inline mock maker)
- a hidden class, by its JVM-assigned `/0x…` suffix
- a bridge method or a synthetic method — a compiler-generated duplicate of a
  real method, or an accessor nobody wrote

This is silent — no `stderr` line — because it is the expected, structural
case; membership never depends on a vendor's package name, so adding a new
mocking or proxy library to your dependencies needs no configuration change
here.

#### Exclude packages: `exclude=<pkg1>;<pkg2>`

Library and framework internals — never your own business boundary, but named
by package rather than by shape — are excluded beside `packages=`:

```bash
java -javaagent:narrativetrace-agent.jar=packages=com.example.myapp.*,exclude=org.mockito -jar your-app.jar
```

`exclude=` always wins: a class matching both `packages=` and `exclude=` is
not instrumented, even when the excluded package sits inside a matched one
(`packages=com.example`, `exclude=com.example.generated` — a class under
`com.example.generated` is not instrumented; everything else under
`com.example` still is). Shipped default, applied whenever `exclude=` (and
`narrativetrace.exclude` on the property fallback path) is absent entirely:

| Prefix | Reason |
|---|---|
| `org.mockito` | Mocking library's dispatcher and generated mock subclasses |
| `net.bytebuddy` | The code generator underneath Mockito, Hibernate and many proxy tools |
| `org.junit` | Test runner's own helpers |
| `org.testng` | The other mainstream JVM test runner's helpers |
| `net.sf.cglib` | Classic CGLIB's own internals |
| `org.springframework.cglib` | Spring's repackaged CGLIB |
| `org.aspectj` | Another weaver's runtime |
| `datadog` | Another APM agent's runtime — avoids recursive instrumentation when co-deployed |
| `io.opentelemetry.javaagent` | The other APM agent most likely to be co-deployed |

The key's presence decides: `exclude=` absent means this default applies;
`exclude=` present — even with nothing after it — states the whole list
explicitly, replacing the default (empty disables list 2 altogether). To
extend the default rather than replace it, copy these nine
prefixes into your own `exclude=` value and add yours alongside them.
Delimiter-aware like `packages=` (`org.acme` never matches `org.acmeExtra`);
a duplicate `exclude=` key is rejected like a duplicate `packages=` key. A
matched, excluded class is reported once per excluded prefix, never once per
class:

```text
narrativetrace-agent: cannot instrument org/mockito/internal/Foo (excluded package org/mockito/); classes under that package load uninstrumented
```

#### Deep traces (opt-in)

Deep traces require the Java agent (`mode = "agent"` in the plugin, or the `-javaagent` option): the proxy route cannot see private methods or self-calls, so with `mode = "proxy"` the setting has no effect.

By default the agent records each class's observable boundary. It does **not**
instrument private methods, constructors, class initializers, abstract methods,
or bridge/synthetic methods (which includes every lambda body); package-private,
protected, public and static methods are all instrumented, and a call from one
instrumented method to another on `this` is already recorded, because the
weaving is in the method bodies. What a default trace therefore cannot show is
a private helper, and the wrong value it returns.

Deep traces record those private methods too, for the classes `packages=` already
matches (never a JDK or library class, and `exclude=` still wins). It is a
verbose, test-time setting and **never a production setting**: it is off by
default in every integration, and reachable only through two explicit opt-ins that
must both be given.

1. Start the test JVM's agent with `depth=internal` (default `public`; the property
   fallback, used when the agent is given no arguments, is
   `narrativetrace.agent.depth`). This weaves the private methods when their class
   loads; it records nothing by itself. `maxInternalSpans=` bounds the deep spans of
   one test (default 5000); beyond it deep spans are dropped, counted, and named in
   the trace file's header, and the public calls are all kept.
2. Ask for it per test with `@DeepTrace` (on a test, a class or an enclosing class),
   or for a whole run with `narrativetrace.depth=internal`. The JUnit 5, JUnit 4 and TestNG
   integrations open the agent's deep scope for exactly that test. A request without
   the agent option runs at the default depth and says so once on standard error.

```bash
java -javaagent:narrativetrace-agent.jar=packages=com.example.myapp.*,depth=internal -jar your-app-tests.jar
```

```java
@Test
@DeepTrace
void pricesAreCalculated() { ... }
```

With the Gradle plugin in `agent` mode, the agent option is one line in the DSL
(`maxInternalSpans.set(...)` is optional):

```kotlin
narrativeTrace {
    mode.set("agent")
    agent {
        depth.set("internal")   // appends depth=internal to the -javaagent argument
    }
}
```

An internal call nests under the public call that made it, like any other, and is
marked with a leading `·` in the text, Markdown and structural (`.nt`) renderings;
the canonical and wire form do not carry the mark.

```text
#1 - Pricing.total() → value
  #1.1 - · Pricing.withTax(net) → value
```

Approval baselines follow the same opt-in. A `*.approved.nt` cut with deep traces on
declares `depth: internal` under its `scenario:` line (no line means `public`, so
every earlier baseline stays valid). A baseline and a run of different depths are
never compared line by line, and never pass silently: the run fails with `Depth
mismatch`, naming both depths, and writes its own `*.received.nt` for review.
Approving that file re-baselines at the new depth.

Limits: private methods are recorded on the test's own thread; a private method that
runs on a worker thread the test did not start is not. Lambda bodies stay out.


## 4. Configure Trace Output

Trace output writes to `build/narrativetrace` by default — nothing to turn
on. The sections below are for changing the format or opting out.

### JUnit 5 (recommended): `junit-platform.properties`

Add `src/test/resources/junit-platform.properties`:

```properties
narrativetrace.format=markdown
```

To opt out entirely, set `narrativetrace.output=false` in the same file.
No Gradle wiring needed either way. This file is test-only and never touches
production.

### Gradle: `gradle.properties` (alternative)

Define trace output settings in one place:

```properties
# gradle.properties
narrativetrace.format=markdown
```

Then forward to the test JVM in `build.gradle.kts`:

```kotlin
tasks.withType<Test> {
    useJUnitPlatform()
    listOf("narrativetrace.output", "narrativetrace.outputDir", "narrativetrace.format")
        .forEach { key ->
            (findProperty(key) as? String)?.let { systemProperty(key, it) }
        }
}
```

### CLI override

System properties override all other sources:

```bash
./gradlew test -Dnarrativetrace.output=false
./gradlew test -Pnarrativetrace.format=text
```

### Pure Java / Agent: `narrativetrace.properties`

Add a file to the classpath (e.g. `src/main/resources/narrativetrace.properties`):

```properties
narrativetrace.packages=com.example.app.*;com.example.shared.*
```

## 5. Validate Installation

Run tests:

```bash
./gradlew test
```

Trace files are written automatically, unless `junit-platform.properties` sets `narrativetrace.output=false`.

Expected output structure:

```text
build/narrativetrace/
├── traces/
│   └── OrderServiceTest/
│       ├── customer_places_order.md
│       ├── customer_places_order.json
│       └── ...
├── diagrams/
│   └── OrderServiceTest/
│       ├── customer_places_order.mmd
│       └── ...
└── clarity-report.md
```

### What appears in generated output

When a trace has span context, generated files include both the raw trace ID and a deterministic
human-readable trace name derived from it.

Markdown frontmatter includes:

```yaml
trace_id: 4bf92f3577b34da6a3ce929d0e0e4736
trace_name: bold elk soars
```

JSON export includes:

```json
{
  "trace": {
    "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
    "traceName": "bold elk soars"
  }
}
```

The hex `traceId` remains the authoritative identifier. `traceName` is a readable alias for logs,
dashboards, and team discussion.

## Module Selection Reference

| Module | When to add it |
|---|---|
| `narrativetrace-api` | Never — arrives transitively with `narrativetrace-core`; depend on it directly only when you ship a library that implements an SPI without needing the runtime |
| `narrativetrace-core` | Always required |
| `narrativetrace-proxy` | Interface-based tracing via JDK proxies |
| `narrativetrace-junit5` | JUnit 5 extension and trace file emission |
| `narrativetrace-junit4` | JUnit 4 rule and class rule for trace output |
| `narrativetrace-testng` | TestNG listener for trace output, registered by TestNG's service loader |
| `narrativetrace-spring` | Spring bean auto-wrapping via `@EnableNarrativeTrace` |
| `narrativetrace-slf4j` | Emit narrative events to SLF4J logger |
| `narrativetrace-diagrams` | Mermaid / PlantUML renderers |
| `narrativetrace-clarity` | Naming clarity analysis and reporting |
| `narrativetrace-opentelemetry` | Export trace trees as OpenTelemetry spans, or live span creation via decorator |
| `narrativetrace-micrometer` | Cross-thread trace propagation via Micrometer context-propagation |
| `narrativetrace-agent` | Java agent instrumentation |
| `narrativetrace-servlet` | Production servlet filter — per-request trace lifecycle and export (no Spring) |
| `narrativetrace-spring-web` | Spring `@Configuration` auto-wiring the servlet filter with pluggable exporter |
| `narrativetrace-micronaut` | Micronaut bean auto-wrapping via `BeanCreatedEventListener` |
| `narrativetrace-micronaut-http` | Micronaut reactive HTTP filter for per-request trace lifecycle |

## See also

- [Gradle Plugin Guide](gradle-plugin-guide.md) — full DSL reference, interception modes, recipes, Groovy DSL
- [Configuration Guide](configuration-guide.md) — tracing levels, JUnit/Gradle/Spring/SLF4J configuration
- [Spring Integration Guide](spring-integration-guide.md) — bean tracing, servlet filter, `@Async` propagation, testing
- [Micronaut Integration Guide](micronaut-integration-guide.md) — bean tracing, HTTP filter, configuration properties
- [Annotations Guide](annotations-guide.md) — `@Narrated`, `@OnError`, `@NotTraced`, `@NarrativeSummary`, `@EnableNarrativeTrace`
- [Clarity Guide](clarity-guide.md) — scoring model, NLP components, JUnit integration
