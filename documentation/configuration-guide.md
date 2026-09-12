# NarrativeTrace Java Configuration Guide

This guide documents runtime and test configuration for NarrativeTrace Java.
For which configuration belongs to which stage of your process —
development, CI/acceptance, production — see the
[Lifecycle Guide](lifecycle-guide.md).

## Configuration Surface

NarrativeTrace provides six configuration paths:

| Path | Mechanism | Best for |
|---|---|---|
| Gradle Plugin | `narrativeTrace { }` DSL in `build.gradle.kts` | Gradle projects (recommended) |
| JUnit 5 | `junit-platform.properties` | Test-time trace output |
| Pure Java / Agent | `narrativetrace.properties` on classpath | Standalone apps, agent |
| Gradle (manual) | `gradle.properties` + build script forwarding | Gradle projects without plugin |
| Spring | `@EnableNarrativeTrace` annotation | Spring apps |
| Micronaut | `application.yml` via `@ConfigurationProperties` | Micronaut apps |

All paths support system property overrides (`-D` flags) as the highest-priority source.

## Gradle Plugin DSL

The Gradle plugin (`ai.narrativetrace`) configures everything automatically. Apply it and optionally customize:

```kotlin
plugins {
    id("ai.narrativetrace") version "0.2.1"
}

// Zero-config works — sensible defaults for everything:
narrativeTrace { }

// Full surface:
narrativeTrace {
    enabled.set(true)                          // default: true
    mode.set("proxy")                          // "proxy" (default) | "agent" | "spring"
    testFramework.set("junit5")               // "junit5" (default) | "junit4"
    scope.set("test")                          // "test" (default) | "production"
    format.set("markdown")                     // "markdown" | "text" | "mermaid" | "plantuml"
    tracingLevel.set("DETAIL")                 // "OFF" | "ERRORS" | "SUMMARY" | "NARRATIVE" | "DETAIL"
    outputDir.set(layout.buildDirectory.dir("narrativetrace"))
    glossary.set(false)                        // default: false — harvest glossary.json at suite end
    approval.set(false)                        // default: false — verify structure against committed baselines
    approvedDir.set(layout.projectDirectory.dir("src/test/narratives"))

    modules {                                  // fine-grained opt-in (all default false)
        slf4j.set(false)
        micrometer.set(false)
        servlet.set(false)
        springWeb.set(false)                   // implies servlet
    }

    agent {                                    // only relevant when mode = "agent"
        packages.set(listOf("com.example.app"))
    }

    clarity {
        minScore.set(0.80)                     // default: 0.0 (no gate)
        maxHighIssues.set(0)                   // default: Integer.MAX_VALUE (no gate)
        maxSuiteIssues.set(0)                  // default: Integer.MAX_VALUE (advisory only)
        warnOnly.set(false)                    // default: false
    }
}
```

### What the plugin does

| Action | Detail |
|---|---|
| Adds `-parameters` compiler flag | On all `JavaCompile` tasks, skipped if already present |
| Adds dependencies | Based on `mode`, `modules`, and `testFramework`; version auto-detected from plugin JAR |
| Sets test JVM properties | `narrativetrace.output=true`, `narrativetrace.outputDir`, and optionally `narrativetrace.format`, `narrativetrace.level`, the glossary pair (`glossary=true`), and the approval pair (`approval=true`) |
| Registers `clarityCheck` task | Reads `clarity-results.json`, enforces thresholds, wired into `check` lifecycle |
| Registers `clarityScan` task | Standalone clarity analysis from compiled classes (no tests required) |
| Registers `glossaryScan` task | Standalone glossary harvest from compiled classes, including annotation templates |
| Registers `approveNarratives` task | Promotes reviewed `*.received.nt` narratives to `*.approved.nt` baselines (approval mode) |
| Configures agent JVM arg | When `mode = "agent"`: resolves agent JAR, adds `-javaagent` to Test tasks |

### Interception modes

| Mode | Dependencies added (besides core + clarity + diagrams + test framework) |
|---|---|
| `proxy` (default) | `narrativetrace-proxy` |
| `agent` | `narrativetrace-agent` (separate configuration for JAR resolution) |
| `spring` | `narrativetrace-spring` + `narrativetrace-proxy` |

### Module opt-in

| Flag | Artifact | Notes |
|---|---|---|
| `modules.slf4j` | `narrativetrace-slf4j` | SLF4J bridge |
| `modules.micrometer` | `narrativetrace-micrometer` | Cross-thread propagation |
| `modules.servlet` | `narrativetrace-servlet` | Servlet filter |
| `modules.springWeb` | `narrativetrace-spring-web` + `narrativetrace-servlet` | Auto-adds servlet |

### Dependency scope

| Scope | Library dependencies | Test framework dependency |
|---|---|---|
| `test` (default) | `testImplementation` | `testImplementation` |
| `production` | `implementation` | `testImplementation` (always) |

### Disabling the plugin

To disable the plugin entirely (e.g., in a subproject), set `enabled.set(false)`. No tasks are registered, no dependencies added, no compiler flags set.

```kotlin
narrativeTrace {
    enabled.set(false)
}
```

### Clarity thresholds

The `clarityCheck` task reads `build/narrativetrace/clarity-results.json` (produced by the JUnit extension during `test`) and enforces configured thresholds. It runs automatically as part of `./gradlew check`.

- **`minScore`** — minimum overall clarity score (0.0–1.0). Any scenario below this threshold fails the build.
- **`maxHighIssues`** — maximum number of HIGH-severity issues per scenario. Exceeding this fails the build.
- **`warnOnly`** — when `true`, threshold violations produce warnings instead of build failures.

If no `clarity-results.json` exists (e.g., no tests ran), the task passes silently.

### Version resolution

The plugin auto-detects its version from the plugin JAR (no DSL property). The same version is used for all managed dependencies. There is no `manageDependencies` property — if `enabled=true`, the plugin manages dependencies based on mode/modules/scope. Users who want full manual control set `enabled.set(false)` and wire everything themselves.

For the complete plugin reference including recipes, Groovy DSL, and validation details, see the [Gradle Plugin Guide](gradle-plugin-guide.md).

## 1. Tracing Levels (`NarrativeTraceConfig`)

`ThreadLocalNarrativeContext` uses `NarrativeTraceConfig`, which defaults to `DETAIL`.

```java
var config = new NarrativeTraceConfig(TracingLevel.NARRATIVE);
var context = new ThreadLocalNarrativeContext(config);
```

Available levels:

| Level | Behavior |
|---|---|
| `OFF` | No tracing captured |
| `ERRORS` | Only exception paths captured |
| `SUMMARY` | Captures root entry, deepest leaf, and full exception chains |
| `NARRATIVE` | Captures full call flow, suppresses parameter values; return values render at every active level (a documented promise — the return is the payload of a narrative) |
| `DETAIL` | Captures full call flow with parameter values and return values |

Runtime level changes are supported:

```java
config.setLevel(TracingLevel.ERRORS);
```

## 2. JUnit 5 Configuration (`junit-platform.properties`)

The JUnit extension uses `ExtensionContext.getConfigurationParameter()`, which resolves values in this order:

1. System properties (highest — CLI `-D` flags still work)
2. `junit-platform.properties` on the test classpath
3. Hardcoded defaults (lowest)

### Properties

| Property | Values | Default |
|---|---|---|
| `narrativetrace.output` | `true` / `false` | `true` *(since 0.2.2, unreleased)* |
| `narrativetrace.outputDir` | Any writable path | `build/narrativetrace` |
| `narrativetrace.format` | `markdown`, `text`, `mermaid`, `plantuml` | `markdown` |
| `narrativetrace.unfolded` | `true` / `false` | `false` |
| `narrativetrace.glossary` | `true` / `false` | `false` |
| `narrativetrace.glossaryDir` | Any writable path | working directory |
| `narrativetrace.canonicalJson` | `true` / `false` | `false` |
| `narrativetrace.structuralJson` | `true` / `false` | `false` |
| `narrativetrace.approval` | `true` / `false` | `false` |
| `narrativetrace.approvedDir` | Directory of committed baselines | `src/test/narratives` |
| `narrativetrace.bufferCapacity` | Slots in a test context's event ring | `8192` |

`narrativetrace.bufferCapacity` sizes the event ring of the context the
extension builds **per test method**. It defaults to 8,192 slots rather
than the runtime's 65,536 because a test traces tens of calls, not tens of
thousands, and the ring is allocated eagerly at construction — the runtime
default would cost 1.75 MB and about 1.5 ms per test method for slots no
test reaches. At 8,192 that is 224 kB.

This is the JUnit 5 spelling of the same knob; the runtime-wide
`narrativetrace.buffer.capacity` (a system property or
`narrativetrace.properties` entry) overrules it, because a deployment's
setting outranks an integration's default. Nothing in the runtime detects
JUnit — the extension passes its choice explicitly, in its own code.

A suite that does exceed 8,192 events in one test sheds the excess, and
says so: every narrative it writes carries a footer line naming the count
and the property to raise. Raise this one for a test-only fix, or the
runtime key to change it everywhere.

Glossary harvesting is off by default because it rewrites `glossary.json`
and `glossary.md` **outside** the build directory — a committed, reviewed
artifact, not a build output. `narrativetrace.glossaryDir` names the
directory holding those two files; the Gradle plugin sets it to the
repository root, which matters in multi-module builds where a test task's
working directory is the subproject and the glossary is one file per
repository.

`narrativetrace.glossaryDir` is read even when harvesting is off: a
committed `glossary.json` is the project vocabulary clarity scores with
(see the [Clarity Guide](clarity-guide.md)). Reading changes nothing on
disk, so it needs no opt-in; a repository without the file scores with
the built-in dictionaries alone.

`narrativetrace.canonicalJson` additionally writes a
`<test>.canonical.json` schema-1.1 entry array beside each trace file —
a machine artifact for canonical-schema consumers such as the other
NarrativeTrace runtimes and conformance fixtures.

`narrativetrace.structuralJson` additionally writes a
`<test>.structural.json` beside each trace file: the same schema-1.1
entry array with every runtime-value field elided (parameters carry
`[ELIDED]`, return values and exception messages are null) — the
AI-safe structural artifact of ADR-002 Level 1. Both flags are
independent and can be combined in one run.

`narrativetrace.unfolded` turns **loop folding off** in the Markdown
narrative. By default a run of consecutive same-shape sibling calls
renders as the first iteration in full plus one summary line —
`×2 more: ‹Taxi›, #3 sku=` `` `"TENT"` `` ` — same flow (validate ✓ →
record ✓)`. That line names every folded iteration: by its identity label
where the distinguishing argument has one, and otherwise by position plus
the argument itself, so the omitted iterations are reachable. When you
need each iteration's *whole* subtree and all of its values, set
`narrativetrace.unfolded=true` and every iteration renders in full.
Markdown only — no other format folds, and the canonical JSON always
carries every iteration. With the Gradle plugin, forward it like any
other property:

```kotlin
tasks.withType<Test> { systemProperty("narrativetrace.unfolded", "true") }
```

`narrativetrace.approval` turns on approval mode: after a **passing**
test, the scenario's value-free structure (the same render as the `.nt`
artifact) is verified against the committed baseline
`<approvedDir>/<TestClassSimpleName>/<artifact_name>.approved.nt` — the
same artifact identity as every other per-test file, so a method that
runs more than once has one baseline per invocation.
A missing baseline or a structural difference fails the test with a
readable diff and writes the current structure beside the baseline as
`*.received.nt`; review it and accept it with the Gradle
`approveNarratives` task (or rename it manually). Failing tests are
never verified — their structure is mid-flight and must not churn the
received files. The Gradle plugin sets both properties from its
`approval` / `approvedDir` DSL.

### File-based configuration (recommended)

Output writes by default — no file needed to turn it on. Drop a file in
`src/test/resources/junit-platform.properties` only to change the format or
opt out:

```properties
narrativetrace.output=false
```

No Gradle `systemProperty()` wiring needed. The file is test-only and never touches production.

### CLI overrides

System properties still work as overrides:

```bash
./gradlew test -Dnarrativetrace.output=false
./gradlew test -Dnarrativetrace.format=text
./gradlew test -Dnarrativetrace.outputDir=out/narrative
```

### Gradle CLI forwarding (optional)

Only needed if you want to pass CLI `-D` flags through Gradle to the forked test JVM:

```kotlin
tasks.withType<Test> {
    System.getProperty("narrativetrace.output")?.let { systemProperty("narrativetrace.output", it) }
    System.getProperty("narrativetrace.outputDir")?.let { systemProperty("narrativetrace.outputDir", it) }
    System.getProperty("narrativetrace.format")?.let { systemProperty("narrativetrace.format", it) }
}
```

### Scenario Names

The extension derives a human-readable scenario name from each test:

- `customerPlacesOrder()` → "Customer places order"
- `customer_places_order()` → "Customer places order"
- `@DisplayName("customer places order")` → "customer places order" (passed through)

JUnit parameter type suffixes (e.g. `(NarrativeContext)`) are stripped automatically.

### File Layout

Base layout:

- `<outputDir>/traces/<TestClassSimpleName>/<artifact_name>.<ext>`

`<artifact_name>` is the **artifact identity** of one test invocation:

- An ordinary test method is its slugged name — `customerPlacesOrder` →
  `customer_places_order`.
- A method that runs more than once (`@ParameterizedTest`,
  `@RepeatedTest`) appends `-<index>-<label>`: the 1-based invocation
  number zero-padded to three digits, then the invocation's display name
  through the same slug rule — `equipment_can_be_found-002-find_tent`.
  The label is dropped when it slugs to nothing, leaving
  `equipment_can_be_found-002`.
- `-` is the separator because the slug alphabet is `[a-z0-9_]` and can
  never produce one: an invocation artifact can never collide with an
  ordinary method's, and the name splits back into method, index and
  label. Two invocations of one method always differ in the index, so
  display names that differ only in characters a path cannot carry
  (`find/TENT` versus `find TENT`) still land on different files.
- Nothing in the name varies per run or per machine, which is what lets
  an approval baseline be committed for one invocation. A name too long
  for the filesystem is shortened on its *method* half and given eight
  hex characters of the Java `String.hashCode` of the full slug; the
  index and label are never the part truncated away.

Every per-test artifact of one invocation shares that name: the trace,
the JSON export, the diagram, the structural artifact, and the committed
`.approved.nt` baseline beside them.

> A `@ParameterizedTest(name = ...)` template interpolates arguments into
> the display name, so the value-free `.nt` artifact is not titled with
> it: an invocation's `scenario:` header is `<humanized method name>
> #<index>` (`Equipment can be found #2`), and a method that runs once
> keeps the display name it always had *(since 0.2.2, unreleased)*. The artifact *filename* and
> `manifest.json` do carry the display name — the filename is what tells
> two invocations apart on disk, and the manifest indexes the
> value-carrying artifacts as well. Keep secrets out of display-name
> templates.

When `format=markdown`, the extension also writes per test:

- Mermaid diagram: `<outputDir>/diagrams/<TestClassSimpleName>/<artifact_name>.mmd`
- JSON export: `<outputDir>/traces/<TestClassSimpleName>/<artifact_name>.json`
- Structural artifact: `<outputDir>/structural/<TestClassSimpleName>/<artifact_name>.nt`
  — the value-free call structure (format spec:
  [structural-trace-format.md](structural-trace-format.md)). The file on
  disk is the **last-green baseline**: a green run advances it, a
  non-green run compares against it but never overwrites it, so every
  delta reads "what changed since the last time this scenario passed".
  "Green" is the whole verdict, not just the assertions — a test that
  passed but whose structure approval **rejected** ends red, and its
  structure is not written. Rejecting a change therefore leaves the
  baseline where it was, and reverting the change reports no delta

After all tests in a class complete, the extension writes:

- Run manifest: `<outputDir>/manifest.json` — one row per traced
  scenario, in execution order, naming the test that produced it, its
  invocation number when the method ran more than once, and every
  artifact it owns as a path relative to `<outputDir>`. This is the
  index to read when you know the scenario and want the file:
  ```json
  {
    "scenario": "find TENT",
    "testClass": "traildepot.CatalogTest",
    "testMethod": "equipmentCanBeFound",
    "invocation": 2,
    "artifacts": {
      "trace": "traces/CatalogTest/equipment_can_be_found-002-find_tent.md",
      "structural": "structural/CatalogTest/equipment_can_be_found-002-find_tent.nt"
    }
  }
  ```
  Only artifacts actually on disk are listed, so the row reflects the
  format and flags the run used.
- Clarity report: `<outputDir>/clarity-report.md`
- Console summary (printed to stdout), ending with the one-line
  structural delta against the last green run:
  ```
  NarrativeTrace — Suite complete
    2 scenarios recorded
    Clarity: 100% high | 0% moderate | 0% low
    Reports: build/narrativetrace
    Since last green: 1 scenario unchanged · 1 changed: "Customer places order" (+1 call InventoryService.release)
  ```

A failing test's console report prints the structural delta against the
last green artifact — summary plus readable diff — instead of the full
trace, and links the trace file as a clickable `file://` URI.

## 3. Pure Java / Agent Configuration (`narrativetrace.properties`)

For standalone Java apps and the bytecode agent, `ConfigResolver` loads configuration from the classpath.

Resolution order:

1. System properties (highest)
2. `narrativetrace.properties` on classpath
3. Hardcoded defaults (lowest)

### Properties

| Property | Values | Default |
|---|---|---|
| `narrativetrace.level` | `OFF`, `ERRORS`, `SUMMARY`, `NARRATIVE`, `DETAIL` | `DETAIL` |
| `narrativetrace.packages` | Semicolon-separated package prefixes | (empty) |
| `narrativetrace.loggerName` | SLF4J logger name | `narrativetrace` |
| `narrativetrace.loggingJars` | Semicolon-separated jar files or directories | (empty) |
| `narrativetrace.capture.resource` | `true` / `false` | `true` |
| `narrativetrace.capture.sourceLocation` | `true` / `false` | `false` |
| `narrativetrace.capture.instanceIds` | `true` / `false` | `false` |
| `narrativetrace.narration` | `off` to suppress the SLF4J listener | (on) |
| `narrativetrace.pipeline` | Name of a registered pipeline topology | (dual-path) |
| `narrativetrace.pipeline.<name>.*` | Settings for the named topology | (per topology) |
| `narrativetrace.buffer.capacity` | Slots in the default topology's ring, rounded up to a power of two | `65536` |
| `narrativetrace.discovery` | `off` to disable extension discovery | (on) |
| `narrativetrace.discovery.disabled` | Comma-separated provider class names | (empty) |

### Capture flags

The `narrativetrace.capture.*` flags widen what identity is captured per
event. They gate **capture only, never schema shape**: every gated field
stays nullable in the canonical schema and is simply absent when the
flag is off, so downstream consumers and cross-platform fixtures never
branch on configuration.

- **`narrativetrace.capture.resource`** (default on) — stamps
  auto-detected process identity onto every span: `host.name` (from
  `HOSTNAME`/`COMPUTERNAME`, falling back to a reverse lookup),
  `process.pid`, and `process.runtime.version`. Detection runs once per
  process. Hostname-sensitive deployments opt out. These fields are
  never re-emitted onto OpenTelemetry spans — the OTel SDK's resource
  detectors own that path; this covers the library's own outputs
  (canonical JSON, MDC).
- **`narrativetrace.capture.sourceLocation`** (default off) — records
  `code.filepath`/`code.lineno` on enter entries. The two capture paths
  are deliberately asymmetric: the agent bakes the instrumented
  method's own source file and first line number at instrumentation
  time (free), while the proxy pays a stack walk per call and records
  the *caller's* frame (proxied interfaces carry no line info) — which
  is why the flag defaults off.
- **`narrativetrace.capture.instanceIds`** (default off) — records the
  receiver object's identity hash (lowercase hex) as `nt.instanceId` on
  enter entries; useful to tell instances of one class apart.

Automatic collection stays identity-shaped by design. Content-shaped
context (order totals, feature flags, business state) never enters
automatic capture — the three-tier attribute/MDC enrichment is the
client's channel for unforeseen context, and it flows through redaction
and elision like every other value.

`narrativetrace.loggingJars` supports standalone agent attach on hosts
whose class loaders cannot see an SLF4J provider (application servers):
each listed jar — directories are expanded to the jars they contain —
is appended to the system class loader search before tracing starts. A
path that does not exist fails fast at attach time. Also available as
the `loggingJars=` agent argument.

### Sizing the event buffer

The best-effort path retains events in a **fixed-size ring buffer**. It
never grows: the capacity is chosen once, the whole ring is allocated at
construction, and a producer that outruns the drain overwrites the oldest
slot instead of expanding. There is no initial capacity, no growth factor
and no resize — what a traced process spends on retention is decided at
startup and stays decided.

`narrativetrace.buffer.capacity` sets that size for the default dual-path
topology, rounded up to the next power of two (the ring masks rather than
divides). The default is **65,536 slots**. A value the ring cannot honour —
not a number, zero, negative, or above 2^30 — falls back to the default
instead of failing startup: a mis-sized buffer costs analysis history,
which this path is allowed to lose, while a refused startup costs the
application.

**The sizing rule:**

```
capacity  ≈  peak events/s  ×  worst tolerable drain stall
memory at saturation  ≈  capacity  ×  ~300 B/event
```

Worked example. 1,000 req/s × 50 traced calls per request × 2 events per
call (enter and exit) is 100,000 events/s. Budget a 500 ms worst-case
drain stall — a long GC pause, a starved thread — and 50,000 events are
outstanding at the peak. That fits the 65,536-slot default, and costs
roughly 20 MB at saturation. Twice the traffic, or a longer stall you are
willing to survive, and you raise it deliberately:

```properties
narrativetrace.buffer.capacity=131072
```

Above 70% fill the buffer sheds rather than queues, and every shed event is
counted (`EventPipeline.droppedEventCount()`) — a ring that is too small
for its traffic shows up as a number, not as silence. All three loss modes
count: the ring overwriting a slot the consumer had not reached, the
adaptive drain discarding a batch above the shedding threshold, and a
subscriber that could not keep up.

The count is not only available, it is **announced**. A capture that lost
events carries it on the tree (`TraceTree.loss()`), and every rendered
format with a footer slot prints one line — the count and the property to
raise:

```
⚠ Incomplete narrative: 1204 events shed under load (buffer full) — raise narrativetrace.buffer.capacity.
```

Text, prose and Markdown carry it as a footer (Markdown as a blockquote,
plus `incomplete: true` and `dropped_events:` in the document
frontmatter); Mermaid and PlantUML carry it as a diagram comment, so the
drawing itself is unchanged. The structural `.nt` artifact deliberately
does not: it is the approval baseline and the conformance fixture format,
and must stay byte-identical for identical behaviour. A capture that lost
nothing prints nothing.

**Why there is a draining thread, and when there is one.** The default
context starts none: it builds its consumer with `startConsumer=false` and
drains on demand, so `captureTrace()` flushes the ring before it reads. A
consumer you start yourself owns a thread because of the tail — the last
events published before traffic stops are already in the ring, and nothing
new is coming to carry them out. Draining only as a side effect of
publishing would strand them there until a next publish that may never
happen, so the thread parks and re-checks instead of exiting, and it keeps
draining until the ring is empty. `close()` drains what remains, which is
why closing such a consumer is mandatory — see the
[lifecycle guide](lifecycle-guide.md).

### File-based configuration

Drop a file on the classpath (e.g. `src/main/resources/narrativetrace.properties`):

```properties
narrativetrace.level=DETAIL
narrativetrace.packages=com.example.app.*;com.example.shared.*
narrativetrace.loggerName=myapp.traces
```

### Programmatic usage

```java
var resolver = new ConfigResolver();
var level = resolver.resolve("narrativetrace.level", "DETAIL");
```

### Duplicate file detection

If multiple `narrativetrace.properties` files are found on the classpath (e.g., one in the app JAR and one in a dependency), `ConfigResolver` throws `DuplicateConfigurationException` listing all locations. This prevents silent shadowing bugs.

### Agent fallback

When the agent receives no CLI arguments, it falls back to `ConfigResolver`:

```bash
# Explicit CLI args (highest priority)
java -javaagent:narrativetrace-agent.jar=packages=com.example.app -jar app.jar

# With custom logger name
java -javaagent:narrativetrace-agent.jar=packages=com.example.app,loggerName=myapp.traces -jar app.jar

# Falls back to narrativetrace.properties on classpath
java -javaagent:narrativetrace-agent.jar -jar app.jar
```

## 4. Gradle Configuration (`gradle.properties`)

For Gradle projects, `gradle.properties` provides a single place to define NarrativeTrace test output settings. Properties defined here are available as Gradle project properties and can be forwarded to the forked test JVM.

### Define properties

Output writes by default; `gradle.properties` is where you'd change the
format or opt out. Add to `gradle.properties` in the project root:

```properties
narrativetrace.output=false
narrativetrace.format=markdown
```

### Forward to test JVM

Gradle project properties don't automatically flow into forked test JVMs. Add forwarding in `build.gradle.kts`:

```kotlin
tasks.withType<Test> {
    useJUnitPlatform()
    listOf("narrativetrace.output", "narrativetrace.outputDir", "narrativetrace.format")
        .forEach { key ->
            (findProperty(key) as? String)?.let { systemProperty(key, it) }
        }
}
```

This reads each property from `gradle.properties` (or CLI `-P` flags) and passes it as a system property to the test JVM. System properties take the highest priority in JUnit's `getConfigurationParameter()` resolution.

### CLI overrides with `-P`

Gradle project properties can be overridden from the command line with `-P`:

```bash
./gradlew test -Pnarrativetrace.format=text
./gradlew test -Pnarrativetrace.output=false
```

### JUnit-specific DSL

Gradle also offers a JUnit-specific way to pass configuration parameters directly:

```kotlin
tasks.withType<Test> {
    useJUnitPlatform {
        configurationParameter("narrativetrace.output", "false") // opt out; on by default
        configurationParameter("narrativetrace.format", "markdown")
    }
}
```

This only feeds into JUnit's `getConfigurationParameter()` — it does not affect `ConfigResolver` or the agent. Use `gradle.properties` with forwarding when you need a single config source for all integrations.

## 5. Spring Configuration

Use package filters to control which beans are considered for proxy wrapping:

```java
@Configuration
@EnableNarrativeTrace
public class AppConfig { }
```

When `basePackages` is omitted, it defaults to the annotated class's package — just like `@ComponentScan`. To narrow scope explicitly:

```java
@EnableNarrativeTrace(basePackages = {"com.example.order", "com.example.payment"})
```

### Logger name

When `narrativetrace-slf4j` is on the classpath, the auto-created `NarrativeContext` bean narrates through SLF4J automatically (`Slf4jTraceEventListener` on the pipeline's synchronous path). Configure the SLF4J logger name via the annotation:

```java
@EnableNarrativeTrace(loggerName = "myapp.traces")
```

The default logger name is `"narrativetrace"`. Set to empty string to disable automatic SLF4J wrapping:

```java
@EnableNarrativeTrace(loggerName = "")
```

The logger name also propagates to `Slf4jTraceExporter` in the spring-web module, which derives the export logger as `<loggerName>.export`.

Spring apps use their own configuration conventions. The `@EnableNarrativeTrace` annotation is the recommended approach — no properties files needed.

Behavior:

- Beans outside `basePackages` (or the default package) are skipped.
- Beans with no interfaces are skipped (JDK dynamic proxy limitation).
- Only interfaces in the configured packages are traced; Spring framework interfaces are ignored.
- A `NarrativeContext` bean is provided automatically. When `narrativetrace-slf4j` is on the classpath and `loggerName` is non-empty, it narrates through SLF4J under that logger.
- Defining your own `narrativeContext` bean overrides the auto-created one.

For `@Async` cross-thread propagation, servlet filter setup, and production deployment patterns, see the [Spring Integration Guide](spring-integration-guide.md).

## 6. Micronaut Configuration

Micronaut integration is auto-discovered on the classpath — no enable annotation needed. Configuration uses `application.yml`:

```yaml
# src/main/resources/application.yml
narrativetrace:
  base-packages:
    - com.example.order
    - com.example.payment
  logger-name: myapp.traces
  service-name: order-service
  service-version: "2.0"
  environment: production
```

### Properties

| Property | Type | Default | Purpose |
|---|---|---|---|
| `narrativetrace.base-packages` | `List<String>` | `[]` (empty — wraps nothing) | Package prefixes for bean wrapping |
| `narrativetrace.logger-name` | `String` | `"narrativetrace"` | SLF4J logger name for trace events |
| `narrativetrace.service-name` | `String` | `""` | Service identity metadata |
| `narrativetrace.service-version` | `String` | `""` | Service identity metadata |
| `narrativetrace.environment` | `String` | `""` | Service identity metadata |

### What happens

A `BeanCreatedEventListener<Any>` wraps eligible beans in JDK dynamic proxies. The same eligibility rules as Spring apply:
- Bean's class must be in a configured base package
- Bean must implement at least one interface in a configured package
- Beans without matching interfaces are left untouched

A `NarrativeContext` bean is provided automatically (marked `@Secondary`). When `narrativetrace-slf4j` is on the classpath and `loggerName` is non-empty, the context is wired with SLF4J event logging. Define your own `@Bean NarrativeContext` to override the default.

### HTTP filter

Add `narrativetrace-micronaut-http` for per-request trace lifecycle:

```kotlin
implementation("ai.narrativetrace:narrativetrace-micronaut-http:0.2.1")
```

The reactive HTTP filter (`HttpServerFilter`) is auto-registered on the classpath. Lifecycle: reset → stamp HTTP metadata → proceed → capture → export → reset.

A default `Slf4jTraceExporter` is provided (marked `@Secondary`). Provide your own `@Bean TraceExporter` to override.

For the complete integration guide, see [Micronaut Integration Guide](micronaut-integration-guide.md).

## 7. SLF4J Configuration

**NarrativeTrace is not a logging framework.** Everything below wires *into* your existing SLF4J/Logback/Log4j setup — it changes what gets narrated (generated instead of hand-written), never how, where, or through what your logs are shipped. See [Not a replacement for your logging framework](../README.md#not-a-replacement-for-your-logging-framework) for the short version.

Trace narration through your existing logging framework is automatic: when `narrativetrace-slf4j` is on the classpath, `PipelineBootstrap` (the composition root behind every default-constructed context) attaches `Slf4jTraceEventListener` to the synchronous path of the event pipeline. No wrapper class, no wiring:

```java
var context = new ThreadLocalNarrativeContext(); // narrates via SLF4J when the module is present
```

Veto narration without removing the module via `narrativetrace.narration=off`.

### Logger name

By default, trace events are logged under the `narrativetrace` SLF4J logger. Route events to a different logger with the `narrativetrace.loggerName` property (ConfigResolver chain: system property or `narrativetrace.properties`), or programmatically:

```java
var context = new ThreadLocalNarrativeContext(
    new NarrativeTraceConfig(), PipelineBootstrap.createDefault("myapp.traces"));
```

This is useful when different applications or modules need separate log routing. The logger name propagates to other components:

- **Spring web exporter** — when the context uses a custom logger name, `Slf4jTraceExporter` automatically derives `<loggerName>.export` (e.g., `myapp.traces.export`)
- **Spring annotation** — `@EnableNarrativeTrace(loggerName = "myapp.traces")` configures the name for the auto-created context
- **Agent** — `loggerName=myapp.traces` in agent args or `narrativetrace.loggerName=myapp.traces` in properties

### Log levels

Events are logged under the configured logger at these default levels:

| Event type | Default level |
|---|---|
| Method entry | `TRACE` |
| Method return | `TRACE` |
| Method exception | `WARN` |

### Custom log levels

Override the defaults by constructing the listener yourself and handing the context a pipeline built around it:

```java
var listener = new Slf4jTraceEventListener("myapp.traces", Map.of(
    Slf4jTraceEventListener.EventType.ENTRY, Level.DEBUG,
    Slf4jTraceEventListener.EventType.RETURN, Level.DEBUG,
    Slf4jTraceEventListener.EventType.EXCEPTION, Level.ERROR
));
var context = new ThreadLocalNarrativeContext(
    new NarrativeTraceConfig(), new DualPathPipeline(listener));
```

### MDC fields

`Slf4jTraceEventListener` sets MDC fields on each trace event. Request filters in the servlet and
Micronaut HTTP modules also populate persistent request-scoped MDC fields before traced methods run.

| MDC key | Value |
|---|---|
| `traceId` | Raw 32-char W3C trace ID |
| `traceName` | Deterministic three-word human-readable name derived from `traceId` |
| `spanId` | Current span ID |
| `parentSpanId` | Parent span ID when present |
| `service.name` | Configured service name when present |
| `service.version` | Configured service version when present |
| `service.environment` | Configured environment when present |
| `host.name` | Auto-detected host name (`narrativetrace.capture.resource`, on by default) |
| `process.pid` | Process id (`narrativetrace.capture.resource`) |
| `process.runtime.version` | Java runtime version (`narrativetrace.capture.resource`) |
| `nt.class` | Class name of the traced service |
| `nt.method` | Method name |
| `nt.package` | Declaring package of the traced service, when captured |
| `nt.depth` | Call depth (1 for top-level enter events) |
| `nt.threadVirtual` | Whether the entry ran on a virtual thread (thread name/id are `%thread` built-ins) |

`traceName` is deterministic but not guaranteed unique. Use `traceId` for exact correlation and
`traceName` for readability.

Use these in logback patterns for structured log output.

**Appearance is logging configuration; capture is NarrativeTrace
configuration.** The narrative message text stays clean by design —
everything beyond it (trace identity, service identity, class, method,
depth) is published as MDC keys, and your logging pattern decides what
appears: a pattern without `%X{...}` shows the bare narrative,
`%X{nt.class}` surfaces one key, a JSON encoder emits them all for log
aggregators. NarrativeTrace settings control only what is *captured* —
and therefore *can* appear — never how a log line is laid out.

### Coexisting with traditional logging

Well-structured code — small methods with clear names, computed values returned rather than logged — needs no SLF4J calls at all. NarrativeTrace captures everything from the method signatures and return values.

In code that isn't fully structured that way yet, you can mix in traditional SLF4J calls for things like intermediate computations or decision points that don't surface in method boundaries. The two interleave naturally:

```java
public class DefaultOrderService implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(DefaultOrderService.class);

    @Override
    public OrderResult placeOrder(String customerId, String productId, int quantity) {
        log.info("Placing order: customer={}, product={}, qty={}", customerId, productId, quantity);

        var customer = customers.findCustomer(customerId);
        log.debug("Resolved customer {} (tier: {})", customer.name(), customer.tier());

        double unitPrice = catalog.lookupPrice(productId);
        double total = unitPrice * quantity;
        log.debug("Calculated total: {} x {} = {}", unitPrice, quantity, total);

        inventory.reserve(productId, quantity);
        var payment = payments.charge(customerId, total, "tok_" + customer.id());
        log.info("Payment {} confirmed for ${}", payment.transactionId(), payment.amount());

        var orderId = "ORD-%05d".formatted(orderCounter.getAndIncrement());
        return new OrderResult(orderId, payment.transactionId(), total, quantity);
    }
}
```

```
TRACE [narrativetrace]        → OrderService.placeOrder(customerId: C-1234, productId: SKU-MECHANICAL-KB, quantity: 2)
INFO  [DefaultOrderService]   Placing order: customer=C-1234, product=SKU-MECHANICAL-KB, qty=2
TRACE [narrativetrace]        → CustomerService.findCustomer(customerId: C-1234)
TRACE [narrativetrace]        ← returned: Customer[id=C-1234, name=Alice Johnson, tier=GOLD]
DEBUG [DefaultOrderService]   Resolved customer Alice Johnson (tier: GOLD)
TRACE [narrativetrace]        → ProductCatalogService.lookupPrice(productId: SKU-MECHANICAL-KB)
TRACE [narrativetrace]        ← returned: 89.99
DEBUG [DefaultOrderService]   Calculated total: 89.99 x 2 = 179.98
...
INFO  [DefaultOrderService]   Payment TXN-00001 confirmed for $179.98
TRACE [narrativetrace]        ← returned: OrderResult[orderId=ORD-00001, ...]
```

This makes NarrativeTrace easy to adopt incrementally — add it alongside existing logging, then remove the manual log calls as you refactor toward cleaner method boundaries.

### Legacy logging frameworks

Apps using `java.util.logging` or Log4j 1.x: add the appropriate SLF4J bridge ([jul-to-slf4j](https://www.slf4j.org/legacy.html#jul-to-slf4j) or [log4j-over-slf4j](https://www.slf4j.org/legacy.html#log4j-over-slf4j)) and NarrativeTrace output flows into your existing logging infrastructure unchanged.

## 8. TracingLevel vs SLF4J Interplay

TracingLevel (section 1) and SLF4J log levels (section 7) are two independent filtering layers. Both must allow an event for it to appear in log output.

### Data flow

```
method call → TracingLevel filter → event pipeline
                                      ↓
                              Slf4jTraceEventListener
                                      ↓
                              SLF4J logger level filter → log output
```

**TracingLevel** controls what gets **recorded** into the trace tree. If a call is filtered out here, it never reaches the context, renderers, or SLF4J — it simply doesn't exist.

**SLF4J log level** controls what gets **printed** to logs. The events are already captured; this only affects whether `Slf4jTraceEventListener` log statements pass through logback/log4j.

### Combination examples

| TracingLevel | Logback level on `narrativetrace` | Result |
|---|---|---|
| `DETAIL` | `INFO` | Full trace tree (with param values) in files and renderers, but entry/return log lines suppressed (they log at TRACE). Only exception paths (WARN) appear in logs. |
| `ERRORS` | `TRACE` | Only exception paths recorded in the trace tree. Those exceptions are logged (WARN passes TRACE threshold). Normal calls produce nothing anywhere. |
| `NARRATIVE` | `TRACE` | Full call flow recorded and logged, but parameter values show as empty strings (NARRATIVE suppresses values). |
| `DETAIL` | `TRACE` | Everything recorded and everything logged — maximum verbosity. |
| `OFF` | `TRACE` | Nothing recorded, nothing logged. TracingLevel gate blocks all events before they reach SLF4J. |

### Which knob for which goal

| Goal | Adjust | Why |
|---|---|---|
| Reduce log noise | Raise SLF4J level on `narrativetrace` logger | Trace tree is still captured for file output and renderers; only console/log-file volume decreases. |
| Reduce trace file size | Lower TracingLevel (e.g. `NARRATIVE` → `SUMMARY`) | Fewer events enter the trace tree, producing smaller rendered output. |
| Reduce CPU/memory overhead | Lower TracingLevel | SLF4J level has no effect on capture overhead — the proxy still intercepts, serializes, and records every allowed call. Only TracingLevel prevents that work. |

## 9. Recommended Defaults by Environment

| Environment | Suggested level | Suggested output |
|---|---|---|
| Local feature work | `DETAIL` | on by default, `format=markdown` |
| CI test runs | `NARRATIVE` or `SUMMARY` | on by default, `format=markdown` |
| Performance-sensitive prod | `ERRORS` (or `OFF`) | no test file output |

## 10. OpenTelemetry Configuration

The `narrativetrace-opentelemetry` module provides two integration modes. Both require `opentelemetry-api` on the classpath (it's `compileOnly` in the module — you provide it).

### Batch export (post-hoc)

Export a captured trace tree to OTel spans:

```java
var tracer = openTelemetry.getTracer("narrativetrace");
var exporter = new TraceSpanExporter(tracer);
exporter.export(context.captureTrace().roots());
```

Use this for test output or post-request export. Low overhead during execution.

### Live span decorator (real-time)

Create OTel spans from events in the pipeline:

```java
var tracer = openTelemetry.getTracer("narrativetrace");
var listener = new OtelTraceEventListener(tracer);
// Plug into DualPathPipeline as a synchronous listener
```

`OtelTraceEventListener` is a `Consumer<TraceEvent>` that creates spans from `EnterEvent`/`ExitEvent` pairs with explicit timestamps and explicit `parentSpanId` linking.

### Span attributes

Both modes set the same attribute schema:

| Attribute | Source |
|---|---|
| `narrative.class` | `MethodSignature.className()` |
| `narrative.method` | `MethodSignature.methodName()` |
| `narrative.trace_id` | `SpanContext.traceId()` |
| `narrative.trace_name` | `TraceNamer.name(SpanContext.traceId().value())` |
| `narrative.param.<name>` | Each parameter's rendered value |
| `narrative.outcome` | Rendered return value |
| `narrative.duration_ms` | Node duration (batch only) |
| `narrative.concurrency.groupId` | Fork-join or fire-and-forget group ID |
| `narrative.concurrency.kind` | `FORK_JOIN` or `FIRE_AND_FORGET` |
| `narrative.concurrency.threadId` | Thread ID |
| `narrative.concurrency.threadName` | Thread name |
| `narrative.concurrency.virtual` | Whether the thread is virtual |

## 11. Extension Points

Modules on the classpath can extend NarrativeTrace through `java.util.ServiceLoader`,
declared in `META-INF/services`. Two kinds exist, and they activate differently.

**Additive extensions** are observers. Many can coexist, order between them is
undefined, and presence on the classpath is what activates them — dropping the jar
in is the whole installation step.

| Extension point | Receives | Called on |
|---|---|---|
| `ai.narrativetrace.api.spi.TraceEventListener` | every published event | the publishing thread (topology-dependent) |
| `ai.narrativetrace.api.spi.ReportContributor` | every trace accumulated in a run | once at the end of a test run |

A `TraceEventListener` must be thread-safe; where it is attached is the topology's
decision, so the same listener works unchanged if the pipeline is reconfigured. Both
kinds are isolated: one that throws is reported once and skipped, and neither the
other extensions nor the application are affected.

Two escape hatches control discovery, for deployments that want the classpath to
stop deciding:

```properties
# Disable extension discovery entirely
narrativetrace.discovery=off

# Or disable named providers only
narrativetrace.discovery.disabled=com.example.NoisyListener,com.example.SlowContributor
```

**Replacement extensions** supply a different pipeline topology through
`ai.narrativetrace.core.pipeline.EventPipelineFactory`. These are *never* activated
by classpath presence — a topology decides durability, so it changes only when
configuration names it:

```properties
narrativetrace.pipeline=<name>
narrativetrace.pipeline.<name>.<setting>=<value>
```

Without `narrativetrace.pipeline`, the default dual-path topology is built and no
factory is ever looked up. With it, a factory answering to that name must be on the
classpath: if none is found, initialization fails rather than quietly falling back to
a topology with different durability guarantees.

By default the SLF4J listener is composed onto the durable path automatically
whenever the `narrativetrace-slf4j` module is present. Set `narrativetrace.narration=off`
to keep capture without narration.

## 12. Redaction

Reflective introspection is sensitive-data-by-default: a DTO reaches the
renderer as a bag of field values bound for traces, logs and exports — and a
hand-written `toString()` does not exempt it, because a type's own
stringification is never trusted while the type has fields.
NarrativeTrace hides values on two independent axes, both on by default.

### Axis 1 — the field name

A case- and accent-insensitive match against a built-in vocabulary. The
vocabulary is **multilingual and always on** — there is no locale to select
and nothing to opt into, because an English-only deny-list does not give a
weaker guarantee, it gives a differently-distributed one: it protects whoever
happens to name fields in the language the list was written in.

| Language | Words |
|---|---|
| English | `password`, `passwd`, `secret`, `token`, `apikey`, `api_key`, `cvv`, `ssn`, `authorization`, `credential`, `privatekey`, `private_key`, `cardnumber`, `card_number`, `jwt`, `cookie`, `setcookie`, `set_cookie`, `sessionid`, `session_id`, `accountnumber`, `account_number`, `routingnumber`, `routing_number`, `pan`, `iban` |
| Spanish | `contraseña`, `tarjeta`, `cédula`, `rut`, `cuit`, `dni`, `claveAcceso`, `claveSecreta` |
| Portuguese | `senha`, `cpf`, `cnpj`, `cartão` |
| French | `motDePasse`, `mot_de_passe`, `nir`, `carteBancaire`, `numeroCarte` |
| Chinese | `密码`, `身份证`, and the pinyin `mima`, `shenfenzheng` |

Accents are folded on both sides, so `contraseña`, `contrasena` and
`CONTRASEÑA` are one pattern rather than three — including the decomposed
spelling a macOS filesystem hands back.

Most words match as **substrings**, so `userPassword` and `numeroTarjeta` are
caught. The short ones match on **identifier-token boundaries** instead:

`pan` · `iban` · `rut` · `cuit` · `dni` · `senha` · `cpf` · `cnpj` · `nir` ·
`mima`

Each of those is inside an ordinary business word — `cuit` in
`circuitBreaker`, `rut` in `truthValue`, `dni` in `midnightCutoff`, `senha`
across the seam of `chosenHash` — and a default that blanks those is one
teams switch off entirely, which leaks every field rather than one.
`rutCliente`, `cuit_empresa` and `DNI` still match; `circuitBreaker` does not.

**Narrow whenever in doubt.** Bare `clave` and `carte` used to be
token-matched words on that list, on the same reasoning as `rut`/`cuit`
above — until a native-reader review found that a token match only protects
a short word from *someone else's* compound, never from the codebase's own:
`clavePrimaria`/`claveForanea` (Spanish, "primary key"/"foreign key" — Spanish
database code also writes `llavePrimaria`) and `carteGraphique`/
`carteRoutiere` (French, "graphics card"/"road map") are themselves a whole
identifier token, so the old rule blanked them too. Both words were removed
and replaced by the specific compounds that are actually credentials —
`claveAcceso`/`clave_acceso`, `claveSecreta`/`clave_secreta`,
`carteBancaire`/`carte_bancaire`, `numeroCarte`/`numero_carte` — which are
long enough to be safe as plain substrings, matched with both the camelCase
and snake_case spelling the same way `motDePasse`/`mot_de_passe` already are.

### Axis 2 — the value's own shape

A bearer token arrives as `value`, `header`, `data`, or the third element of a
list with no name at all, so the second axis asks what the bytes say. Every
matcher is structural — there is no entropy heuristic and no length rule.

| Shape | Recognised by |
|---|---|
| JWT | the `eyJ` prefix and three base64url segments |
| Card number (PAN) | 13–19 digits, Luhn-valid |
| `Set-Cookie` | `name=value` plus an RFC 6265 attribute |
| Chilean RUT | mod-11 verifier; dots optional, **the `-` verifier separator is required** |
| Brazilian CPF | 11 digits, both check digits |
| Brazilian CNPJ | 14 digits, both check digits |
| Spanish DNI / NIE | the mod-23 check letter |
| French NIR | the mod-97 key, Corsica's `2A`/`2B` included |
| Chinese resident id | the ISO 7064 check character *and* a plausible birth date |

National-id shapes are language-neutral: a CPF is a CPF whatever the field
holding it is called, which is exactly why the value axis is the right one for
a document whose field name is usually in a language the deny-list is read in
but not written in.

A lookalike that fails its checksum stays **visible** — an order number, an
invoice number, a date. A bare nine-digit run is *not* treated as a RUT for
that reason: mod-11 alone would hide roughly one in eleven of every nine-digit
identifier in your system.

### Widening the defaults

Two knobs, and they do different things.

```properties
# APPEND to the built-in vocabulary (system property, or the environment
# variable NARRATIVETRACE_REDACTION_ADDITIONALPATTERNS)
narrativetrace.redaction.additionalPatterns=betalingskort,kontonummer
```

```java
// REPLACE the built-in vocabulary entirely
new ValueRenderer(RedactionPolicy.ofPatterns(Set.of("ssn", "internalRef")));
```

`additionalPatterns` is for whoever *deploys* the artifact — it needs no
rebuild, and it is read from the property first, then the environment
variable, not from `narrativetrace.properties`. `ofPatterns` is for whoever
*writes* the application. They compose: the additions still apply to a policy
built by `ofPatterns`, because replacing the vocabulary is an application's
opinion about which of its own fields are sensitive, not permission to undo a
deployment's widening.

> **Additions are substring patterns**, matched exactly as the built-in
> substring vocabulary is. A short one carries the same trap the token-matched
> words avoid: adding `id` blanks every identifier in the trace.

### Turning it off

```java
new ValueRenderer(RedactionPolicy.DISABLED);
```

`DISABLED` turns off both axes and the additions. `@NotTraced` is still
honoured — it is an explicit instruction, not a default. Redacted values render
as the literal `[REDACTED]`, never as silence, so a reader can tell "hidden"
from "never captured".

## See also

- [Gradle Plugin Guide](gradle-plugin-guide.md) — full DSL reference, interception modes, recipes, Groovy DSL
- [Installation Guide](installation-guide.md) — dependencies, integration paths, Java agent setup
- [Spring Integration Guide](spring-integration-guide.md) — bean tracing, servlet filter, `@Async` propagation, testing
- [Micronaut Integration Guide](micronaut-integration-guide.md) — bean tracing, HTTP filter, configuration properties
- [Annotations Guide](annotations-guide.md) — `@Narrated`, `@OnError`, `@NotTraced`, `@NarrativeSummary`, `@EnableNarrativeTrace`
- [Clarity Guide](clarity-guide.md) — scoring model, NLP components, JUnit integration
