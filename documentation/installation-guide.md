# NarrativeTrace Java Installation Guide

This guide covers installing and wiring NarrativeTrace Java in a JVM project.

## Prerequisites

- Java 17+
- Gradle build

## Compatibility

Versions as of 0.2.0. "Brought" means the module depends on it and Gradle
resolves it for you; "yours" means the module compiles against it but does not
depend on it — you already have it, and NarrativeTrace uses whatever version
you bring.

| Requirement | Version | Who supplies it |
|---|---|---|
| Java | **17+** — compiled and tested on 17; JDK 21 exercised by a scheduled CI job (the virtual-thread tests only run there) | yours |
| Gradle (for the plugin) | **8.0+** — developed and tested against 8.14.2 | yours |
| JUnit 5 | 5.11.4 (`narrativetrace-junit5` exposes `junit-jupiter-api` as `api`) | brought |
| JUnit 4 | 4.13.2 | brought |
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

The Gradle plugin handles all wiring automatically — dependencies, compiler flags, and test JVM configuration:

```kotlin
plugins {
    id("ai.narrativetrace") version "0.2.0"
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
    implementation("ai.narrativetrace:narrativetrace-core:0.2.0")
    implementation("ai.narrativetrace:narrativetrace-proxy:0.2.0")

    // Optional integrations
    testImplementation("ai.narrativetrace:narrativetrace-junit5:0.2.0")
    testImplementation("ai.narrativetrace:narrativetrace-junit4:0.2.0")  // for JUnit 4
    implementation("ai.narrativetrace:narrativetrace-spring:0.2.0")
    implementation("ai.narrativetrace:narrativetrace-slf4j:0.2.0")
    implementation("ai.narrativetrace:narrativetrace-diagrams:0.2.0")
    implementation("ai.narrativetrace:narrativetrace-clarity:0.2.0")
    implementation("ai.narrativetrace:narrativetrace-opentelemetry:0.2.0")
    implementation("ai.narrativetrace:narrativetrace-micrometer:0.2.0")
    implementation("ai.narrativetrace:narrativetrace-agent:0.2.0")
    implementation("ai.narrativetrace:narrativetrace-servlet:0.2.0")
    implementation("ai.narrativetrace:narrativetrace-spring-web:0.2.0")
    implementation("ai.narrativetrace:narrativetrace-micronaut:0.2.0")
    implementation("ai.narrativetrace:narrativetrace-micronaut-http:0.2.0")
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
- With `narrativetrace.output=true`: writes `.md`, `.json`, `.mmd` per test, plus a suite-level `clarity-report.md`

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
- With `-Dnarrativetrace.output=true`: writes `.md`, `.json`, `.mmd` per test
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
- `narrativetrace.output` — `true`/`false` (default: `false`)
- `narrativetrace.outputDir` — path (default: `build/narrativetrace`)
- `narrativetrace.format` — `markdown`/`text`/`mermaid`/`plantuml` (default: `markdown`)

### Option E: Java Agent (no proxy wiring)

```bash
java -javaagent:narrativetrace-agent.jar=packages=com.example.myapp.* -jar your-app.jar
```

Use this when you want bytecode instrumentation for classes under selected package prefixes. When no CLI args are provided, the agent falls back to `narrativetrace.properties` on the classpath.

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
silent.


## 4. Configure Trace Output

### JUnit 5 (recommended): `junit-platform.properties`

Add `src/test/resources/junit-platform.properties`:

```properties
narrativetrace.output=true
narrativetrace.format=markdown
```

No Gradle wiring needed. This file is test-only and never touches production.

### Gradle: `gradle.properties` (alternative)

Define trace output settings in one place:

```properties
# gradle.properties
narrativetrace.output=true
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
./gradlew test -Dnarrativetrace.output=true
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

If `junit-platform.properties` has `narrativetrace.output=true`, trace files are written automatically.

Expected output structure:

```
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
| `narrativetrace-core` | Always required |
| `narrativetrace-proxy` | Interface-based tracing via JDK proxies |
| `narrativetrace-junit5` | JUnit 5 extension and trace file emission |
| `narrativetrace-junit4` | JUnit 4 rule and class rule for trace output |
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
