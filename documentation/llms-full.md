# NarrativeTrace Java — Complete Reference

> Code is the log. Method names, parameter names, and return values already describe what code does. NarrativeTrace generates human-readable execution traces automatically — no log statements needed.

## Overview

NarrativeTrace is a Java library that auto-generates execution traces from method/parameter names and return values. It wraps services with tracing proxies (or uses a Java agent) to capture every method call, then renders the captured trace as Markdown, prose, Mermaid diagrams, PlantUML, or JSON.

The core insight: if your method is called `placeOrder(customerId, quantity)` and returns an `OrderResult`, you don't need `logger.info("Placing order...")`. The method signature already says it. NarrativeTrace captures that information and renders it as a readable execution trace.

**Key features:**
- Zero-config trace capture via JDK proxy or Java agent
- Multiple output formats: Markdown, prose, indented text, Mermaid, PlantUML, JSON
- JUnit 5 and JUnit 4 extensions with automatic per-test output
- AI-safe structural trace artifact per test (`structural/<Class>/<scenario>.nt` — call structure with every runtime value stripped)
- Narrative delta loop: suite footer prints a one-line structural delta since the last green run; approval mode fails passing tests whose structure drifts from committed `.approved.nt` baselines
- Trace value references: repeated captured values dedup to readable labels (`‹Hotel›=full` first, `‹Hotel›` after)
- Intra-trace value deltas: the same entity re-captured with a scalar field changed renders as a diff against the in-document reference (`‹Dinner›′{amount: 100.0→92.0}`); anything the diff cannot express renders in full
- Loop folding: consecutive same-shape sibling subtrees condense in Markdown — first iteration in full, then one `×k more: ‹Dinner›, ‹Taxi› — same flow (validate ✓ → record ✓)` line; a divergent call or outcome renders in full outside the fold (Markdown only). Each folded iteration is named on that line: by its identity label where the distinguishing argument has one, otherwise by position and the argument itself (`#2 sku="TENT"`). `narrativetrace.unfolded=true` turns folding off and renders every iteration in full
- Naming clarity analysis that scores code readability (method, class, parameter names) with a per-element teaching note at every score
- Spring integration with automatic bean post-processing
- Servlet filter for production request lifecycle tracing
- Automatic SLF4J narration with MDC integration (when `narrativetrace-slf4j` is on the classpath)
- Micrometer context-propagation for cross-thread tracing
- Custom annotations: `@Narrated`, `@OnError`, `@NotTraced`, `@NarrativeSummary`

**Requirements:** Java 17+, `-parameters` compiler flag

**Group ID:** `ai.narrativetrace`

---

## How Capture Works

```text
instrumented method call
        |
        v
   TraceEvent published (enter/exit, immutable, append-only)
        |
        v
   EventPipeline (single publish seam — DualPathPipeline by default)
        |
        +--> synchronous path -----> SLF4J listener --> durable log line
        |    (never buffered)                            (crash-safe,
        |                                                  never lost)
        |
        +--> buffered async path --> bounded ring buffer --> EventStore
             (best-effort; sheds        (65,536 slots,          |
              above 70% fill, never      never grows)           v
              blocks the caller)                          TraceTreeBuilder
                                                          (pure function over
                                                           the event trail)
                                                                 |
                                                  +--------------+--------------+--------------+
                                                  |              |              |              |
                                              Markdown        canonical      Mermaid /      structural
                                              (humans)          JSON        PlantUML          .nt
                                                              (tooling)     (diagrams)     (value-free,
                                                                                             AI-safe)
```

One capture, four sibling projections computed from the same trace tree —
none derived from another. Two capture paths reach that seam.

**JDK dynamic proxy** (`narrativetrace-proxy`) — `NarrativeTraceProxy.trace(...)` hands
back a `java.lang.reflect.Proxy` implementing your interface. Its `InvocationHandler`
records the call, delegates to your implementation, then records the return value or
exception and the elapsed time. Interface methods only; per-`Method` metadata (parameter
names, annotations) is computed once and cached. The Spring, Micronaut and JUnit modules
are wiring conveniences around this path — they decide *what* gets wrapped, not *how*.

**Bytecode agent** (`narrativetrace-agent`) — `-javaagent` installs an ASM
`ClassFileTransformer`. Classes in the packages you name are rewritten as they load: every
non-private, non-abstract method gets the same enter/exit calls compiled into its body. No
interfaces, no wrapping, nothing to wire, and nothing outside the matched packages is
touched.

What happens to a captured call is identical on both paths:

- **Names come from bytecode, not guesswork.** The `-parameters` compiler flag keeps real
  parameter names in the class file; they are read reflectively once per method and cached.
- **Values are rendered eagerly** to strings at the moment of capture (see
  [Eager serialization](#eager-serialization)). Reflection over your objects is confined to
  *reading fields* — NarrativeTrace does not call getters to obtain values.
- **Nesting comes from a per-thread stack** in the narrative context, so a traced call made
  inside another becomes a child node. Cross-thread work is handled explicitly — see
  [Concurrency](#concurrency).
- **Every call publishes once, to two consumers.** An inline SLF4J listener writes the
  durable log line before your method continues; a bounded in-memory buffer keeps the tree
  that `captureTrace()`, the reports, and the `.nt` artifact are built from. Failures in
  either consumer are swallowed — observability must never break the application.

### Private methods are not traced

The proxy traces interface methods; the agent instruments every non-private method of the
classes it matches. Either way, private methods stay visible through the service calls they
make:

```java
private void fulfillOrder(Order order) {
    if (order.isDigital()) {
        deliveryService.sendDownloadLink(order.customerId(), order.productId());
    } else {
        warehouseService.shipPhysical(order.customerId(), order.shippingAddress());
    }
    notificationService.confirmOrder(order.customerId(), order.orderId());
}
```

The trace shows which branch ran:

```
OrderService.placeOrder(customerId: "C-1234", productId: "SKU-EBOOK")
  DeliveryService.sendDownloadLink(customerId: "C-1234", productId: "SKU-EBOOK") -> "https://..."
  NotificationService.confirmOrder(customerId: "C-1234", orderId: "ORD-001") -> true
```

There is no need to trace the `if` — the presence of `sendDownloadLink` and the absence of
`shipPhysical` tell the story. For logic that calls nothing, add a targeted
`logger.debug()`: hand-written SLF4J logs flow into the same output.

### Stacking with other interceptors

NarrativeTrace and other libraries that wrap the same methods (AOP proxies, contract
libraries, container interceptors, other agents) stack, and they do not know about each
other. Nesting order can change how a trace reads; it never changes what a method returns
or throws, because recording is exception-isolated on every path and never replaces an
outcome. Scoping is include-only today — `packages=` for the agent, base packages for
Spring and Micronaut, all matched on a proper delimiter boundary so `com.acme` never
catches `com.acmeExtra` — with no exclude list yet, and `@NotTraced` applies to parameters,
fields and record components rather than to methods. See [Choosing an
Integration § Stacking with other
wrappers](choosing-an-integration.md#stacking-with-other-wrappers) for the
per-mechanism detail.

---

## Quick Start

### 1. Add the Gradle plugin (recommended)

```kotlin
plugins {
    id("ai.narrativetrace") version "0.2.1"
}
```

This adds all dependencies, the `-parameters` compiler flag, and test JVM configuration automatically — including the JUnit Platform engine (`testRuntimeOnly org.junit.jupiter:junit-jupiter-engine`), so no separate JUnit declaration is needed for the first `gradle test` to run.

### 2. Write a test

```java
@ExtendWith(NarrativeTraceExtension.class)
class OrderServiceTest {
    @Test
    void customerPlacesOrder(NarrativeContext context) {
        OrderService service = NarrativeTraceProxy.trace(
            new DefaultOrderService(), OrderService.class, context);
        service.placeOrder("C-1234", 2);
    }
}
```

### 3. Run tests

```bash
./gradlew test
```

Output appears in `build/narrativetrace/`:
```
build/narrativetrace/
├── traces/
│   └── OrderServiceTest/
│       ├── customer_places_order.md
│       └── customer_places_order.json
├── diagrams/
│   └── OrderServiceTest/
│       └── customer_places_order.mmd
├── structural/
│   └── OrderServiceTest/
│       └── customer_places_order.nt
├── clarity-report.md
└── clarity-results.json
```

The `.nt` file is the value-free structural artifact (ADR-002): the call structure with every runtime value stripped. The copy on disk is the last-green baseline — the suite footer's `Since last green: …` delta line and failure reports compare against it.

### 4. Without the plugin (manual setup)

```kotlin
// build.gradle.kts
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

dependencies {
    implementation("ai.narrativetrace:narrativetrace-core:0.2.1")
    implementation("ai.narrativetrace:narrativetrace-proxy:0.2.1")
    testImplementation("ai.narrativetrace:narrativetrace-junit5:0.2.1")
}
```

---

## Module Map

NarrativeTrace consists of 18 modules. Most projects need only 2-3.

### Core modules (always needed)

| Module | Artifact | Purpose |
|--------|----------|---------|
| `narrativetrace-api` | `ai.narrativetrace:narrativetrace-api` | The contract you compile against: annotations, the event model, `TraceTree`, and the SPIs (`TraceEventListener`, `ReportContributor`, `TraceExporter`, `NarrativeRenderer`, `RequestContextProvider`). **Zero dependencies**, by rule — see [api-surface.md](api-surface.md). Arrives transitively with core; name it directly only when you ship a library that implements an SPI without needing the runtime. |
| `narrativetrace-core` | `ai.narrativetrace:narrativetrace-core` | The runtime: context, pipeline, renderers, config resolution, export. Zero *third-party* dependencies; depends on `narrativetrace-api` and exposes it to consumers. |
| `narrativetrace-proxy` | `ai.narrativetrace:narrativetrace-proxy` | JDK dynamic proxy for interface-based tracing. Depends on core. |

### Test framework integrations

| Module | Artifact | Purpose |
|--------|----------|---------|
| `narrativetrace-junit5` | `ai.narrativetrace:narrativetrace-junit5` | JUnit 5 extension: auto context, output files, clarity reports, console summaries. |
| `narrativetrace-junit4` | `ai.narrativetrace:narrativetrace-junit4` | JUnit 4 rules: `NarrativeTraceRule` + `NarrativeTraceClassRule`. |

### Output format modules

| Module | Artifact | Purpose |
|--------|----------|---------|
| `narrativetrace-diagrams` | `ai.narrativetrace:narrativetrace-diagrams` | Mermaid and PlantUML sequence diagram renderers. |
| `narrativetrace-clarity` | `ai.narrativetrace:narrativetrace-clarity` | NLP-based naming clarity analysis and scoring. |
| `narrativetrace-glossary` | `ai.narrativetrace:narrativetrace-glossary` | Domain glossary harvested from traces (canonical JSON + Markdown); deprecated-synonym violations feed the clarity report as suite-level issues, and the committed glossary is the project vocabulary clarity scores with. |

### Integration modules

| Module | Artifact | Purpose |
|--------|----------|---------|
| `narrativetrace-spring` | `ai.narrativetrace:narrativetrace-spring` | `@EnableNarrativeTrace`, BeanPostProcessor for auto-wrapping. Requires Spring Context 6.2+. |
| `narrativetrace-servlet` | `ai.narrativetrace:narrativetrace-servlet` | Servlet filter for per-request trace lifecycle. Zero Spring dependencies. |
| `narrativetrace-spring-web` | `ai.narrativetrace:narrativetrace-spring-web` | Spring auto-configuration for the servlet filter with pluggable exporter. |
| `narrativetrace-slf4j` | `ai.narrativetrace:narrativetrace-slf4j` | SLF4J bridge with MDC integration. |
| `narrativetrace-opentelemetry` | `ai.narrativetrace:narrativetrace-opentelemetry` | Batch span export (`TraceSpanExporter`) and pipeline consumer (`OtelTraceEventListener`). |
| `narrativetrace-micrometer` | `ai.narrativetrace:narrativetrace-micrometer` | Micrometer `ThreadLocalAccessor` for cross-thread trace propagation. |
| `narrativetrace-agent` | `ai.narrativetrace:narrativetrace-agent` | Java agent for bytecode-level instrumentation via ASM. |
| `narrativetrace-micronaut` | `ai.narrativetrace:narrativetrace-micronaut` | Micronaut bean auto-wrapping via `BeanCreatedEventListener`. Auto-discovered on classpath. |
| `narrativetrace-micronaut-http` | `ai.narrativetrace:narrativetrace-micronaut-http` | Micronaut reactive HTTP filter for per-request trace lifecycle. |

### Build tooling

| Module | Artifact | Purpose |
|--------|----------|---------|
| `narrativetrace-gradle-plugin` | `ai.narrativetrace:narrativetrace-gradle-plugin` | Gradle plugin: auto deps, `-parameters`, `clarityCheck` task, `clarityScan` task. |

### Dependency graph

```
narrativetrace-api (zero deps — the contract)
└── narrativetrace-core → api (the runtime; exposes api to its consumers)
├── narrativetrace-proxy → core
├── narrativetrace-diagrams → core
├── narrativetrace-clarity → core
├── narrativetrace-glossary → core, clarity
├── narrativetrace-slf4j → core, slf4j-api
├── narrativetrace-micrometer → core, micrometer context-propagation
├── narrativetrace-agent → core (ASM bundled)
├── narrativetrace-servlet → core, servlet-api (compileOnly)
├── narrativetrace-spring → core, proxy, spring-context
├── narrativetrace-spring-web → servlet, spring, spring-context
├── narrativetrace-micronaut → core, proxy, micronaut-context
├── narrativetrace-micronaut-http → core, micronaut, servlet, micronaut-http-server, reactor-core
├── narrativetrace-opentelemetry → core, opentelemetry-api (compileOnly)
├── narrativetrace-junit5 → core, proxy, diagrams, clarity, glossary, junit-jupiter
└── narrativetrace-junit4 → core, proxy, diagrams, clarity, glossary, junit:junit
```

---

## Core API Reference

### NarrativeContext (interface)

The central interface for recording trace events. All tracing flows through this.

```java
package ai.narrativetrace.core.context;

public interface NarrativeContext {
    boolean isActive();
    SpanId enterMethod(MethodSignature signature);
    void detachFrame(SpanId spanId);
    void exitMethodWithReturn(String renderedReturnValue);
    void exitMethodWithReturn(String renderedReturnValue, SpanId spanId);
    void exitMethodWithException(Throwable exception, String errorContext);
    void exitMethodWithException(Throwable exception, String errorContext, SpanId spanId);
    void emitTraceNode(TraceNode node, SpanId parentSpanId);
    SpanId currentSpanId();
    TraceTree captureTrace();
    TraceTree captureLocalTrace();
    void discardLocalTrace();
    void reset();
    ContextSnapshot snapshot();
    void adoptTraceparent(Traceparent traceparent);
    Traceparent outboundTraceparent();
    void onForkCreated(String groupId);
    void onMerge(String groupId, List<TraceNode> members);
    void onFireAndForgetLaunched(String groupId);
}
```

**Key methods:**
- `enterMethod(signature)` — push a frame, returns the frame's `SpanId` (or `null` if tracing disabled). Must have a matching exit call.
- `detachFrame(spanId)` — detach a frame from the active stack without completing it. Used by the proxy for `CompletableFuture` returns.
- `exitMethodWithReturn(rendered)` — pop the top frame (LIFO), record success. The value is pre-rendered to String.
- `exitMethodWithReturn(rendered, spanId)` — pop a specific frame by span id, record success. Supports out-of-order (deferred) completion.
- `exitMethodWithException(ex, errorContext)` — pop frame, record failure.
- `exitMethodWithException(ex, errorContext, spanId)` — pop a specific frame by span id, record failure.
- `emitTraceNode(node, parentSpanId)` — replay an externally-produced `TraceNode` (and its children) into the event trail under the given parent, or as a root when `null`. Used by `ForkGroup`/`FireAndForgetGroup` for cross-thread merging.
- `currentSpanId()` — the innermost open frame's span id, or `null` when none is open.
- `captureTrace()` — return the immutable trace tree. **Thread-scoped:** returns only the calling thread's trace (see "Thread scoping" under Concurrency).
- `captureLocalTrace()` — the trace of the current execution scope; the same as `captureTrace()` unless an implementation distinguishes local from shared state. What `ForkGroup`/`FireAndForgetGroup` collect from a worker.
- `discardLocalTrace()` — forget everything the current scope recorded, after the caller has taken its copy. The other half of `captureLocalTrace()`, used by the same helpers; not loss, because the copy is the record.
- `reset()` — clear the calling thread's state at the end of a request or test. It removes everything that thread could **report** — the spans it created and the ones its async workers handed over — and their retained events (see "End of life" under Concurrency).
- `snapshot()` — create a `ContextSnapshot` for cross-thread propagation.
- `adoptTraceparent(tp)` / `outboundTraceparent()` — cross-*process* propagation over the W3C header (see "Traceparent" below).
- `onForkCreated(groupId)` / `onMerge(groupId, members)` / `onFireAndForgetLaunched(groupId)` — lifecycle callbacks for concurrency events (default no-ops).

**Implementations / decorators:**
- `ThreadLocalNarrativeContext` — default, zero-dependency, ThreadLocal-based
- `Slf4jTraceEventListener` — pipeline consumer that routes events through SLF4J with MDC
- `OtelTraceEventListener` — pipeline consumer that creates OpenTelemetry spans from events

### ThreadLocalNarrativeContext

Default implementation using a ThreadLocal Deque-based call stack.

```java
// Default (DETAIL level)
var context = new ThreadLocalNarrativeContext();

// With explicit level
var config = new NarrativeTraceConfig(TracingLevel.NARRATIVE);
var context = new ThreadLocalNarrativeContext(config);

// Change level at runtime (thread-safe, volatile)
config.setLevel(TracingLevel.ERRORS);
```

### TracingLevel (enum)

Controls trace capture verbosity. Ordered by increasing detail:

| Level | Captures | Parameter Values |
|-------|----------|-----------------|
| `OFF` | Nothing | N/A |
| `ERRORS` | Exception paths only | No |
| `SUMMARY` | Root + leaf calls only | No |
| `NARRATIVE` | All calls | No |
| `DETAIL` | All calls | Yes |

Default: `DETAIL` for tests, `ERRORS` or `OFF` recommended for production.

### NarrativeTraceProxy

Creates JDK dynamic proxies that automatically capture trace events.

```java
// Single interface
OrderService traced = NarrativeTraceProxy.trace(realService, OrderService.class, context);

// Multiple interfaces
Object traced = NarrativeTraceProxy.trace(target, new Class<?>[]{ServiceA.class, ServiceB.class}, context);
```

**Requirements:**
- Target must implement at least one interface
- `-parameters` compiler flag for meaningful parameter names
- Works with lambdas and non-public implementations

**Proxy vs Agent:** Use the proxy when you control instantiation and the target implements interfaces. Use the agent for concrete classes or third-party code.

### TraceTree, TraceNode, and data model

```java
// TraceTree — immutable result of trace capture
public interface TraceTree {
    List<TraceNode> roots();
    boolean isEmpty();
    long durationNanos();  // wall-clock span: first entry to last exit
    TraceId traceId();     // the trace this tree belongs to; null only for an
                           // implementation that carries none (default)
}

// TraceNode — single method invocation
public record TraceNode(
    MethodSignature signature,
    List<TraceNode> children,
    TraceOutcome outcome,
    long durationNanos,
    long startTimeNanos,
    ConcurrencyInfo concurrency   // nullable — non-null for fork-join/fire-and-forget members
) {}

// MethodSignature — identifies the method
public record MethodSignature(
    String className,
    String methodName,
    List<ParameterCapture> parameters,
    String narration,        // resolved @Narrated template, or null
    String errorContext       // resolved @OnError template, or null
) {}

// ParameterCapture — captured parameter
public record ParameterCapture(
    String name,             // from -parameters flag
    String renderedValue,    // pre-rendered String
    boolean redacted         // true if @NotTraced
) {}

// TraceOutcome — how the method completed
public sealed interface TraceOutcome {
    record Returned(String renderedValue) implements TraceOutcome {}
    record Threw(Throwable exception) implements TraceOutcome {}
}
```

```java
// ConcurrencyInfo — thread and grouping metadata for concurrent children
public record ConcurrencyInfo(
    String groupId,              // shared across fork-join/fire-and-forget siblings
    String threadName,
    long threadId,
    boolean virtual,             // true for Java 21+ virtual threads
    ConcurrencyKind kind
) {}

// ConcurrencyKind — how the concurrent task was dispatched
public enum ConcurrencyKind {
    FORK_JOIN,          // parallel tasks merged back into parent
    FIRE_AND_FORGET     // independent task, no join
}
```

**Important:** Values are eagerly serialized at capture time. `renderedValue` fields hold pre-rendered Strings. String values include quotes (`"\"order-42\""`), numbers/booleans are plain (`"42"`, `"true"`). Empty string `""` means suppressed (non-DETAIL level).

### ContextSnapshot (cross-thread propagation)

```java
var snapshot = context.snapshot();

// Pass to another thread
executor.submit(snapshot.wrap(() -> {
    // Trace events captured in parent context
    service.processOrder(orderId);
}));

// Or manual activation
try (var scope = snapshot.activate()) {
    service.processOrder(orderId);
}
```

Convenience wrappers: `wrap(Runnable)`, `wrap(Callable)`, `wrap(Supplier)`.

Work traced inside an active scope joins the snapshotting thread's captured trace as soon as it is published — the originating thread does not have to wait for the scope to close, and the rule is transitive, so a chain of async hops arrives whole. `activateWithoutAdoption()` opts out entirely, for helpers that publish their children themselves.

### Traceparent (cross-process propagation)

`ContextSnapshot` carries a trace across threads; the W3C `traceparent` header
carries it across processes. `Traceparent` is the zero-dependency value type for
that header, and `NarrativeContext` has one method for each direction.

```java
// Inbound — the HTTP filters already do this for you.
context.adoptTraceparent(Traceparent.parse(request.getHeader(Traceparent.HEADER_NAME)));

// Outbound — attach to any HTTP client.
var traceparent = context.outboundTraceparent();
if (traceparent != null) {
    builder.header(Traceparent.HEADER_NAME, traceparent.format());
}
```

| Member | Behavior |
|---|---|
| `Traceparent.parse(String)` | Returns `null` for an absent or malformed header — never throws. Strict per the W3C ABNF: lowercase hex only, no surrounding whitespace, no all-zero trace id or parent id, the forbidden `ff` version rejected, and version `00` must be exactly four fields |
| `Traceparent.format()` | Always emits a version-`00`, 55-character header. `parse(format())` is the identity |
| `Traceparent.HEADER_NAME` | `"traceparent"` |
| `Traceparent.sampled()` | Bit 0 of `traceFlags` |
| `NarrativeContext.adoptTraceparent(Traceparent)` | Continues the caller's trace. `null` is the documented no-op, so a filter can call it unconditionally |
| `NarrativeContext.outboundTraceparent()` | The header to send, or `null` when there is no open span and no adopted parent — a `traceparent` naming a span that never existed is worse than none |

Two rules keep the resulting tree honest (ADR-014):

- **The adopted span parents the root span only.** A nested call's parent is its
  local caller, never the remote span.
- **A bad header degrades, it never fails the request.** An absent, malformed or
  forbidden header leaves the request with a freshly generated trace id.

Sampling is inherited, not re-decided: a locally started trace is sampled
(NarrativeTrace recorded it), and an adopted header's flags are forwarded
verbatim to the next hop.

Inbound adoption is wired for you by `NarrativeTraceFilter` (servlet),
`NarrativeTraceWebConfiguration` (Spring Web) and `NarrativeTraceHttpFilter`
(Micronaut). Outbound is one header on your own client — see
`narrativetrace-examples/ecommerce`'s `JsonPlaceholderNotificationService`.

### NarrativeRenderer (interface)

```java
public interface NarrativeRenderer {
    String render(TraceTree tree);
}
```

**Built-in implementations:**
- `MarkdownRenderer` — Markdown with YAML frontmatter
- `ProseRenderer` — natural-language sentences
- `IndentedTextRenderer` — plain text with arrow notation

### ValueRenderer

Serializes objects to strings. Handles:
- Primitives, strings, enums
- Arrays and collections
- Records (component-based)
- POJOs via reflective *field* introspection — getters are never called
- Single-payload wrappers, opened rather than printed: `Optional`, `OptionalInt`/`OptionalLong`/`OptionalDouble`, `Future`, `AtomicReference`
- List- and pair-shaped holders, opened the same way: `AtomicReferenceArray` (rendered exactly as the `Object[]` holding the same elements) and a standalone `Map.Entry` (rendered `key=value`, exactly as inside a `Map`)
- `@NarrativeSummary`-annotated methods
- Cycle detection (identity-based)
- Depth limiting (32 levels of nested complex values)
- toString failure protection
- Escaping of characters no sink can carry: control characters (CWE-117 log forging, ANSI terminal sequences) and unpaired surrogates, both rendered as inert `\uXXXX` text

**Wrappers are transparent.** A wrapper's own `toString()` prints its payload's `toString()`, which
would bypass redaction, truncation, cycle detection and `@NarrativeSummary`. `ValueRenderer`
therefore renders what the wrapper holds, under exactly the rules that apply to that value anywhere
else: `Optional.of(card)` renders as `Card(number: "4111", cvv: [REDACTED])`, not as
`Optional[Card[number=4111, cvv=123]]`. An empty wrapper renders as `<empty>` — marker-shaped like
`<pending>`, so an absent `Optional` stays distinguishable from a null field. A holder that holds
itself (`AtomicReference`, `AtomicReferenceArray`, and a mutable `Map.Entry` via `setValue`)
collapses to the identity marker instead of recursing.

**Depth is capped as well as cycles.** A cycle guard answers "have I been here before?", which a
chain never trips — a linked list, a parent-child tree or a JSON document mapped to nested maps is
arbitrarily deep without ever repeating an object. Following one recursively is a
`StackOverflowError` raised inside instrumentation, so `ValueRenderer` stops after 32 levels of
nested complex values and renders `<max-depth>` there. It is the fourth cap beside the 200-character
string limit, the 5-element collection limit and the 5-field object limit, and it behaves like them:
the output says it truncated rather than falling silent. The cap is per path, so a shallow sibling
after a deep one still renders in full.

---

## Annotations Reference

### @Narrated

Customizes the narrative template for a traced method.

```java
@Narrated("place order for {item} with quantity {quantity}")
OrderResult placeOrder(String item, int quantity);

@Narrated("transfer {amount} from {source.accountId} to {target.accountId}")
TransferResult transfer(Account source, Account target, Money amount);
```

- `{paramName}` — renders the parameter value. A scalar renders as its own `toString()`; an object whose members are redacted renders through `ValueRenderer` instead, so `{card}` gives `Card(number: "4111", cvv: [REDACTED])` rather than the object's `toString()`. An object with nothing hidden keeps its own `toString()`
- `{param.property}` — calls the getter on the raw object (before serialization)
- Templates are resolved at capture time in the proxy/agent
- A path reaching a redacted member resolves to `[REDACTED]`, at every depth of the path: `@NotTraced` and the name-based deny-list both apply, and naming a path — or naming the object it belongs to — never weakens the rules that apply to the value directly. To narrate the value, remove `@NotTraced` from the component
- A path naming no member at all is still preserved literally, so the unresolved-placeholder warning survives

### @OnError / @OnErrors

Exception-specific narrative templates. Repeatable.

```java
@OnError(value = "order {orderId} rejected: insufficient stock",
         exception = InsufficientStockException.class)
@OnError(value = "order {orderId} failed: payment declined",
         exception = PaymentDeclinedException.class)
OrderResult placeOrder(String orderId, int quantity);
```

- Exception matching uses `isAssignableFrom` — most specific wins
- Default `exception = Throwable.class` matches all
- Same `{paramName}` placeholder syntax as `@Narrated`

### @NotTraced

Redacts a parameter value in all output channels.

```java
void authenticate(String username, @NotTraced String password);
// Output: authenticate username="admin" password=***
```

Two redaction rules run beside it, both on by default:

- **By name** — `RedactionPolicy`'s case-insensitive substring deny-list over field, record-component and `Map`-key names: `password`, `cvv`, `ssn`, `token`, `secret`, `authorization`, `cardNumber`, `accountNumber`, `routingNumber`, `sessionId`, `jwt`, `cookie`, and more. `pan` and `iban` are matched on identifier-token boundaries instead of as substrings, so `companyName` and `panelId` stay visible.
- **By value shape** — three structural signatures that identify a credential whatever the field is called: a JWT (three base64url segments, the first beginning `eyJ`), a card number (13–19 digits passing Luhn), and a `Set-Cookie` string. Not an entropy heuristic; a card-length identifier failing Luhn stays visible.

`RedactionPolicy.ofPatterns(...)` replaces the names only. `RedactionPolicy.DISABLED` turns both rules off.

### @NarrativeSummary

Marks a no-arg method as the custom renderer for its type.

```java
public class Order {
    @NarrativeSummary
    public String narrativeSummary() {
        return "Order " + id + " ($" + total + ")";
    }
}
```

The annotated method must be public, no-arg, and return String.

---

## Configuration Reference

### System properties (highest priority)

| Property | Values | Default |
|----------|--------|---------|
| `narrativetrace.output` | `true`/`false` | `false` |
| `narrativetrace.outputDir` | path | `build/narrativetrace` |
| `narrativetrace.format` | `markdown`, `text`, `mermaid`, `plantuml` | `markdown` |
| `narrativetrace.level` | `OFF`, `ERRORS`, `SUMMARY`, `NARRATIVE`, `DETAIL` | `DETAIL` |
| `narrativetrace.packages` | semicolon-separated prefixes | (empty) |

### JUnit 5: junit-platform.properties

```properties
# src/test/resources/junit-platform.properties
narrativetrace.output=true
narrativetrace.format=markdown
```

### Gradle plugin DSL

```kotlin
plugins {
    id("ai.narrativetrace") version "0.2.1"
}

narrativeTrace {
    enabled.set(true)
    mode.set("proxy")                  // "proxy" | "agent" | "spring"
    testFramework.set("junit5")        // "junit5" | "junit4"
    scope.set("test")                  // "test" | "production"
    outputDir.set(layout.buildDirectory.dir("narrativetrace"))

    clarity {
        minScore.set(0.80)
        maxHighIssues.set(0)
        warnOnly.set(false)
    }
}
```

The plugin:
- Adds `-parameters` to all JavaCompile tasks
- Adds test dependencies (api, core, proxy, clarity, diagrams, junit5/junit4)
- Adds `testRuntimeOnly org.junit.jupiter:junit-jupiter-engine:5.11.4` for `testFramework = "junit5"` — `useJUnitPlatform()` cannot start without an engine. JUnit 4 gets none
- Sets test JVM properties
- Registers `clarityCheck` task (wired into `check`)
- Registers `clarityScan` task (standalone classpath analysis)

### Spring configuration

```java
@Configuration
@EnableNarrativeTrace(basePackages = "com.example.service")
public class AppConfig { }
```

- `basePackages` limits which beans are proxy-wrapped
- Empty = all interface-implementing beans
- BPP runs at `HIGHEST_PRECEDENCE` (inner proxy with `@Async`)
- For Micronaut: use `application.yml` with `narrativetrace.base-packages` instead (auto-discovered, no annotation)

Override the context bean:

```java
@Bean
public static NarrativeContext narrativeContext() {
    // SLF4J narration attaches automatically when narrativetrace-slf4j is present
    return new ThreadLocalNarrativeContext();
}
```

### Agent configuration

```bash
java -javaagent:narrativetrace-agent.jar=packages=com.example.app.*;com.example.shared.* -jar app.jar
```

- Package separators: semicolons (`;`)
- Wildcards: `com.example.*` matches all subpackages
- Without CLI args, falls back to `narrativetrace.properties` on classpath

### Extension points

Modules on the classpath extend NarrativeTrace through `java.util.ServiceLoader`
(`META-INF/services`). Additive extensions activate by presence; a replacement
topology activates only when configuration names it.

| Extension point | Kind | Activation |
|---|---|---|
| `ai.narrativetrace.api.spi.TraceEventListener` | additive — every published event | classpath presence |
| `ai.narrativetrace.api.spi.ReportContributor` | additive — all traces at end of run | classpath presence |
| `ai.narrativetrace.core.pipeline.EventPipelineFactory` | replacement — pipeline topology | `narrativetrace.pipeline=<name>` |

```properties
narrativetrace.discovery=off                    # disable discovery entirely
narrativetrace.discovery.disabled=com.example.X # disable named providers
narrativetrace.pipeline=<name>                  # select a registered topology
narrativetrace.pipeline.<name>.<setting>=<value>
narrativetrace.narration=off                    # capture without SLF4J narration
narrativetrace.buffer.capacity=65536            # slots in the default topology's ring
```

`PipelineBootstrap` composes all of this in one place: it attaches the SLF4J
listener automatically when `narrativetrace-slf4j` is present, places discovered
listeners beside retention, and builds either the default dual-path topology or a
named one. A named topology with no matching factory fails initialization rather
than falling back — durability is not something to change silently. Listeners must
be thread-safe; one that throws is disabled after a single diagnostic and never
reaches application code.

The default topology's best-effort path retains events in a **fixed-size ring
buffer that never grows** (`BoundedEventBuffer` behind `BufferedEventConsumer`):
the capacity is chosen once, the whole ring is allocated at construction, and an
overrun overwrites the oldest slot rather than expanding. `DEFAULT_CAPACITY` is
`1 << 16` — 65,536 slots — and `narrativetrace.buffer.capacity` overrides it,
rounded up to the next power of two. Size it as `peak events/s × worst tolerable
drain stall`, budgeting roughly 300 B per retained event; a value the ring cannot
honour degrades to the default rather than failing startup. Above 70% fill the
buffer sheds and counts what it shed (`EventPipeline.droppedEventCount()`).
Retention on the default path drains on demand — `captureTrace()` flushes first —
so no thread is started; a `BufferedEventConsumer` you start yourself owns a drain
thread for the tail case (events already buffered with nothing new arriving to
carry them out) and must be `close()`d.

---

## Integration Guides

### JDK Proxy (any Java app)

```java
var context = new ThreadLocalNarrativeContext();
var traced = NarrativeTraceProxy.trace(orderService, OrderService.class, context);

traced.placeOrder("C-1234", 2);

// Render
String output = new IndentedTextRenderer().render(context.captureTrace());
System.out.println(output);
context.reset();
```

### JUnit 5

```java
@ExtendWith(NarrativeTraceExtension.class)
class OrderServiceTest {
    @Test
    void placesOrder(NarrativeContext context) {
        var service = NarrativeTraceProxy.trace(impl, OrderService.class, context);
        service.placeOrder("C-1234", 2);
    }
}
```

Features:
- Per-test `NarrativeContext` via parameter injection
- Automatic failure reporting: prints the structural delta against the last green run (summary + readable diff) when a baseline exists, the full trace otherwise; trace paths print as `file://` links
- Scenario names derived from test method names
- With `narrativetrace.output=true` (markdown format): writes `.md`, `.json`, `.mmd`, and the value-free structural artifact `structural/<Class>/<scenario>.nt` per test — the on-disk `.nt` is the last-green baseline (non-green runs compare against it, never overwrite it; a rejected approval counts as non-green, so a rejected structure never poisons the baseline)
- Per-invocation artifact identity: a method that runs more than once (`@ParameterizedTest`, `@RepeatedTest`) names each invocation `<method_slug>-<index>-<label>` (`equipment_can_be_found-002-find_tent`), so invocations never overwrite one another and each carries its own `.approved.nt` baseline
- Suite-level clarity report, run manifest (`manifest.json` — scenario → file index over every artifact written, with the invocation number) and console summary after all tests; the summary ends with `Since last green: …`, the one-line structural delta
- Approval mode (`narrativetrace.approval=true`): a passing test whose structure differs from its committed `src/test/narratives/<Class>/<scenario>.approved.nt` baseline fails with a readable diff; the Gradle `approveNarratives` task promotes reviewed `.received.nt` files

### JUnit 4

```java
public class OrderServiceTest {
    @ClassRule public static NarrativeTraceClassRule classRule = new NarrativeTraceClassRule();
    @Rule public NarrativeTraceRule rule = classRule.testRule();

    @Test
    public void placesOrder() {
        var service = NarrativeTraceProxy.trace(impl, OrderService.class, rule.context());
        service.placeOrder("C-1234", 2);
    }
}
```

- `rule.context()` provides the per-test context
- Configuration via system properties (`-Dnarrativetrace.output=true`)
- `NarrativeTraceClassRule` accumulates traces for combined clarity reports

### Spring

```java
@Configuration
@EnableNarrativeTrace(basePackages = "com.example")
@EnableAsync
public class AppConfig {
    @Bean
    public static NarrativeContext narrativeContext() {
        // SLF4J narration attaches automatically when narrativetrace-slf4j is present
        return new ThreadLocalNarrativeContext();
    }
}
```

Spring beans implementing interfaces within `basePackages` are automatically wrapped. The BPP ordering ensures tracing runs on the async thread when combined with `@EnableAsync`.

### Servlet filter (production)

```java
// Zero Spring dependencies
var context = new ThreadLocalNarrativeContext();
var filter = new NarrativeTraceFilter(context, new Slf4jTraceExporter());
// Register filter with your servlet container
```

Filter lifecycle: reset → adopt inbound `traceparent` → chain → capture → export → reset.

`Slf4jTraceExporter` logs to `narrativetrace.export` at INFO:
```
GET /api/orders [200] 42ms — {"nodes":[...]}
```

### Spring Web (auto-configured servlet filter)

```java
@Configuration
@EnableNarrativeTrace(basePackages = "com.example")
@Import(NarrativeTraceWebConfiguration.class)
public class WebConfig { }
```

Registers `NarrativeTraceFilter` as a Spring bean with the context and exporter from the application context.

### Micronaut (auto-discovered)

```yaml
# src/main/resources/application.yml
narrativetrace:
  base-packages:
    - com.example.order
    - com.example.payment
  logger-name: myapp.traces
```

No enable annotation needed — auto-discovered on classpath. A `BeanCreatedEventListener<Any>` wraps eligible beans. `@Secondary` default `NarrativeContext` bean is overridable. Add `narrativetrace-micronaut-http` for per-request HTTP filter tracing (reactive `HttpServerFilter`).

### SLF4J narration

Automatic: with `narrativetrace-slf4j` on the classpath, every default-constructed context narrates through SLF4J — `PipelineBootstrap` attaches `Slf4jTraceEventListener` to the pipeline's synchronous (durable) path. Veto with `narrativetrace.narration=off`; change the logger with `narrativetrace.loggerName`. For custom per-event levels, construct the listener yourself:

```java
var listener = new Slf4jTraceEventListener("myapp.traces", Map.of(
    Slf4jTraceEventListener.EventType.ENTRY, Level.DEBUG,
    Slf4jTraceEventListener.EventType.RETURN, Level.DEBUG,
    Slf4jTraceEventListener.EventType.EXCEPTION, Level.ERROR
));
var context = new ThreadLocalNarrativeContext(
    new NarrativeTraceConfig(), new DualPathPipeline(listener));
```

MDC keys set during logging include `traceId`, `traceName`, `spanId`, `parentSpanId`,
`service.name`, `service.version`, `service.environment`, `host.name`, `process.pid`,
`process.runtime.version`, plus `nt.class`, `nt.method`, `nt.depth`, `nt.package`,
and `nt.threadVirtual` (when captured).

Default levels: ENTRY=TRACE, RETURN=TRACE, EXCEPTION=WARN.

Narration is ordinary SLF4J output, so it renders in whatever format the logging backend is
configured for — patterns, MDC correlation, JSON encoders, file appenders, log aggregators.
Nothing about the format is special; a trace line in a production log looks like any other:

```
2026-08-13 22:02:18.871 TRACE [main] [trace-004] [narrativetrace] - → OrderService.placeOrder(customerId: "C-UNKNOWN", productId: "SKU-MECHANICAL-KB", quantity: 1)
```

To see that live, run `./demo.sh --example ecommerce --classic`, which replays an example
through a conventional logback config (the styled demo also shows one scenario in classic
form mid-run). Add `--lang es` or `--lang zh-CN` to re-render the same run through the
example's committed domain glossary: identifiers translated with the originals kept
greppable, values byte-identical.

### Micrometer cross-thread propagation

```java
// Register once at startup
ContextRegistry.getInstance().registerThreadLocalAccessor(
    new NarrativeTraceThreadLocalAccessor(context));
```

Enables automatic trace propagation with Spring Boot 3, Reactor, and `@Async`.

### Java agent

```bash
java -javaagent:narrativetrace-agent.jar=packages=com.example.* -jar app.jar
```

- Instruments every non-private, non-abstract method of matching classes
- Uses ASM AdviceAdapter with try-catch-rethrow
- No source code changes required

---

## Clarity Scoring

The clarity module scores naming quality across five dimensions:

| Component | Weight | Measures |
|-----------|--------|----------|
| Method names | 30% | Verb quality, domain vocabulary, specificity |
| Parameter names | 25% | Domain specificity vs generic tokens |
| Class names | 20% | Role suffix quality, domain prefix |
| Structural | 15% | Parameter count, call depth |
| Cohesion | 10% | Vocabulary consistency within classes |

### Score interpretation

- **0.80+** — good, no action needed
- **0.60-0.79** — acceptable, minor improvements
- **below 0.60** — poor, significant refactoring recommended

### Verb scoring examples

| Category | Examples | Base Score |
|----------|----------|-----------|
| Domain | `calculate`, `validate`, `reserve`, `dispatch` | 0.60 |
| Standard | `create`, `find`, `delete`, `update` | 0.45 |
| Boolean | `is`, `has`, `can`, `contains` | 1.00 |
| Generic | `get`, `set`, `process`, `handle`, `execute` | 0.10 |

### Per-element teaching notes

Clarity is a teacher, not a judge: every element gets a one-line note explaining its score — at every score, not only below thresholds. Reports render an Elements table per scenario:

```
| Element | Score | Note |
|---|---|---|
| OrderService.placeOrder | 0.90 | Standard verb 'place' + domain noun 'order' |
| OrderService.processData | 0.24 | Generic verb 'process' + vague noun 'data' |
```

Notes name the offending or praised tokens, append concrete rename hints from the collocation dictionary ("consider: cancelOrder, fulfillOrder"), and spell out abbreviations ("chk → check"). In `clarity-results.json` (schema 1.2) each scenario carries an additive `elements` array (`kind`, `element`, `score`, `note`); `issues[]` stays threshold-gated. Record accessors are scored on the noun rubric — a component named `payer` scores well, `data` still scores poorly — so records are not punished for being records.

### Project vocabulary from the committed glossary

The built-in dictionaries know general software English, not your domain. The
committed `glossary.json` (ADR-012) extends them — there is no second dictionary
file:

- a `verb-phrase` term contributes its leading verb as a **domain verb** and the
  rest as **domain nouns** (`settle trade` → verb `settle`, noun `trade`);
- `word` and `noun-phrase` terms contribute every token as a **domain noun**;
- the root-level `abbreviations` section (schema 2) declares **accepted
  shorthand**: `"abbreviations": { "fx": "foreign exchange" }` makes `fx`
  neither penalized nor asked to be spelled out, and the note channel teaches it
  as `; project shorthand: fx → foreign exchange`.

Accepted shorthand is a **declared decision, not an inference**. A token that
merely appears inside a committed phrase does not qualify — committing
`calc total` says nothing about whether `calcTotal` is an acceptable method
name, so `calc` keeps its built-in `calc → calculate` hint. `DomainVocabulary`
therefore carries three sets: `isDomainVerb`, `isDomainNoun` and
`isAcceptedAbbreviation`/`expansionOf`.

The section is human-owned: harvesting never writes it and a merge carries it
through untouched. `schemaVersion` is derived from the content — 2 when the
section is non-empty, 1 otherwise — so a repository that declares no shorthand
sees a byte-identical file. Readers accept the key at any version ≥ 1.

Multi-word terms teach one token at a time, because identifiers are scored one
token at a time; all bounded contexts contribute, since an identifier carries no
package.

The built-in tiers keep their authority. Generic verbs (`process`, `handle`),
boolean prefixes (`is`, `has`) and meaningless placeholders (`temp`, `foo`) are
never promoted; deprecated synonyms and `stale` terms are never vocabulary. Only
the *committed* file counts — a run cannot expand its own vocabulary, which
would make scores non-deterministic and self-certifying.

Reading the glossary is unconditional, unlike harvesting (which is opt-in
because it writes outside the build directory). The JUnit 5 extension and JUnit 4
rule read `narrativetrace.glossaryDir` (default: working directory; the Gradle
plugin sets it to the repository root); `clarityScan` takes `--glossary-dir`. A
repository with no `glossary.json` scores exactly as before; a glossary that
cannot be read degrades to the built-in dictionaries with a warning rather than
failing the suite.

```java
var vocabulary = GlossaryVocabulary.from(Path.of("."));   // glossary module
var analyzer = new ClarityAnalyzer(vocabulary);           // clarity module
```

### Standalone scanning (no tests needed)

```bash
./gradlew clarityScan
```

Analyzes compiled classes via reflection. Depends on `classes` task, not `test`. Writes `clarity-scan-report.md` and `clarity-scan-results.json` — deliberately distinct from the test-run artifacts so a scan never overwrites what the `clarityCheck` gate reads. Private nested, anonymous, local, and lambda classes are skipped as implementation details. The plugin runs `ai.narrativetrace.glossary.GlossaryAwareClarityScannerMain`, which resolves `--glossary-dir` (the repository root) into the project vocabulary before delegating to `ClarityScannerMain`; the clarity module's own entry point always scores with the built-in dictionaries, since reading `glossary.json` needs the glossary module and that dependency runs the other way.

### Programmatic usage

```java
var analyzer = new ClarityAnalyzer();
ClarityResult result = analyzer.analyze(traceTree);
System.out.println("Score: " + result.overallScore());
result.issues().forEach(System.out::println);      // threshold-gated actionables
result.elementNotes().forEach(System.out::println); // one teaching note per element

// Standalone scanning
var scanner = new ClarityScanner();
Map<String, ClarityResult> results = scanner.scan(Path.of("build/classes/java/main"));
```

---

## Architecture Decisions

### Eager serialization

All parameter values and return values are serialized to Strings at capture time (in the proxy or agent), not at render time. This means:

- The context and renderers only see Strings — never raw objects
- Template resolution (`@Narrated`, `@OnError`) happens before serialization, preserving `{param.property}` access on live objects
- No risk of objects changing state between capture and render
- `ParameterCapture.renderedValue()` and `TraceOutcome.Returned.renderedValue()` are pre-rendered Strings

Why it is worth the cost:

- **Correctness** — objects are captured as they were at call time. A mutable object modified after the traced call returns still shows its original value in the trace.
- **No object retention** — the trace holds strings, not references to your domain objects. Nothing NarrativeTrace keeps prevents an object from being garbage collected.
- **Safe rendering** — `ValueRenderer` handles nulls, strings, numbers, enums, records, collections, arrays and plain objects; detects cycles by identity; catches rogue `toString()` implementations; and truncates large values. POJOs without a curated `toString()` are rendered by reflecting over their fields.

The trade-off is that serialization happens on every traced call, whether or not anyone ever reads the trace. That cost is inside the active-path benchmark numbers. For extremely hot loops, exclude them with `TracingLevel.OFF` or `@NotTraced`, or narrow the traced scope.

### ThreadLocal-based context

The context uses `ThreadLocal<TraceStack>` rather than a shared concurrent data structure. This gives:
- Zero synchronization overhead
- Natural thread isolation — `captureTrace()`/`events()` return only the calling thread's trace, so concurrent requests sharing one context never see each other's spans
- Cross-thread propagation via explicit `ContextSnapshot`

### Sealed types and records

`TraceOutcome` is a sealed interface with `Returned` and `Threw` variants — enabling exhaustive pattern matching. All data types (`TraceNode`, `MethodSignature`, `ParameterCapture`) are records for immutability.

### Proxy at HIGHEST_PRECEDENCE in Spring

The `NarrativeTraceBeanPostProcessor` runs at `Ordered.HIGHEST_PRECEDENCE` so the tracing proxy is created first (innermost). When combined with `@EnableAsync`, the async proxy wraps outside, ensuring trace capture runs on the async thread — not the calling thread.

### Tracing is total on the application thread

Every trace boundary that runs on the caller's thread is *total*: it catches
`Throwable`, not `Exception`, and the application's own result or exception
always wins. This is a contract, not a style — a tracing failure that reaches
host code turns observability into an outage.

What it covers:

- **Value rendering.** `ValueRenderer.render` and `renderStructured` never throw.
  A value whose `toString()`, `iterator()`, `size()`, `entrySet()` or `isDone()`
  raises anything renders as its type marker (`<OrderId>`); one unreadable
  element, entry, field or record component renders as `<error>` and the rest of
  the value survives.
- **The proxy.** Metadata lookup, `isActive()`, signature building, parameter
  rendering, trace entry, return rendering and both exits are guarded
  individually. If entry fails the target is invoked raw and no exit is recorded.
  `equals`, `hashCode` and `toString` are answered before tracing and never
  traced.
- **The agent.** Every hook injected into user bytecode returns as if it had done
  nothing. `enterMethod` is emitted before the synthetic try range opens, so a
  throw there would skip the instrumented method body entirely.
- **The pipeline.** `DualPathPipeline.publish`, listener fan-out and the consumer
  watchdog all call through one internal boundary. A listener that throws is
  reported once and disabled for the JVM.
- **Bootstrap.** An optional narration listener that will not load — a shaded
  slf4j, a failing static initialiser — costs one stderr line, not startup.
- **Request filters.** Servlet and Micronaut capture, export, MDC cleanup and
  context reset are four independent best-effort steps, so cleanup can neither
  fail the request nor skip itself.

Where the guarantee stops, stated exactly: it covers every tracing hook this
library invokes and the contexts it ships (`ThreadLocalNarrativeContext`,
`NoopNarrativeContext`). It does not cover a *custom* `NarrativeContext.runScoped`
implementation. That method is the only seam on the synchronous path that wraps
the business call rather than being invoked beside it, so an override of it runs
inside the call — host code, like the target itself. If it throws, the proxy
cannot know whether the target ran, so it does not re-invoke it (duplicate side
effects) and does not invent a return value: the span is closed best-effort and
the throwable propagates, even when the target had already returned successfully
and the throw came from the override's own `finally`. The target's own throwable
is carried out of `runScoped` in a private marker so the two are never confused.

An override must therefore be total itself — scope in a `try`/`finally` whose
`finally` cannot throw, and let the supplier's outcome through unchanged. This
is a decision (2026-09-01) with a regression test behind it, not an accident;
a narrower scope API whose begin/end failures are contained is queued work.

### Zero runtime dependencies in core

`narrativetrace-core` has zero external dependencies. All types — context, events, renderers, config — use only JDK 17 APIs. This ensures NarrativeTrace can be added to any project without dependency conflicts.

### Span-identified frame management

`enterMethod()` returns a typed `SpanId` that uniquely identifies each frame (0.2.0 changed this from the earlier `int` handle). This enables:
- **Out-of-order completion** — frames can be completed in any order, not just LIFO
- **Deferred exit** — `detachFrame(spanId)` removes a frame from the active stack without completing it; the proxy calls this for `CompletableFuture<T>` returns, then completes the frame via `whenComplete` when the future resolves
- **Cross-thread completion** — a detached frame can be completed from any thread via the span-identified exit methods

The proxy automatically detects `CompletableFuture<T>` return types and applies deferred exit. The trace captures the resolved value (not `<pending>`).

---

## Concurrency

NarrativeTrace supports two concurrency patterns as first-class trace tree citizens.

### Thread scoping — read this first

`captureTrace()` and `events()` are **thread-scoped by design**: they return only the calling thread's trace. This isolates concurrent requests sharing one context instance — but it means capturing from a thread other than the one that recorded the events yields an empty tree, which is easy to misread as data loss (it isn't). The rule for cross-thread flows:

- **Capture on the recording thread** — call `captureTrace()` inside the task that did the work, before the thread finishes; or
- **Graft into the observer's trace** — wrap the task with `ContextSnapshot.wrap()` (what `ForkGroup`/`FireAndForgetGroup` do internally), so the forked work is merged into the parent thread's tree and becomes visible to its `captureTrace()`.

Grafted work is visible to the observer **from the moment its spans are published**, not from the moment the worker's scope closes. That distinction matters wherever a framework hands control back to the caller mid-scope — Spring completes an `@Async` method's `CompletableFuture` inside the decorated task, so `future.get()` returns while the `TaskDecorator`'s scope is still open. The activated scope registers itself with the originating stack, so a capture taken in that window already reports the worker's calls; scope close then adopts the same spans, which is why they are never reported twice.

This holds **transitively**: a worker that itself grafts work — a service dispatching to a client — hands over, and contributes while live, its own spans *plus* everything it has adopted or can currently see from its own live children. A chain of async hops therefore reaches the originating thread whole, instead of stopping at the first worker. The ceiling is unchanged and attributed to the receiver: a batch arriving from a child is measured against the receiving stack's own 10,000-span cap and refused whole if it does not fit. A scope activated with `activateWithoutAdoption()` contributes nothing in either direction, its own grandchildren included.

This applies equally to virtual threads: a body running on a virtual thread records into that thread's scope, not the test thread's.

### End of life — what `reset()` takes with it

A request ends when its thread calls `reset()`, and everything that request could report ends with it: the spans it created, the spans its workers handed over, the spans its live children can still answer for, and every retained event behind them. Reporting and clearing read the same set, so nothing is ever visible to a request and able to outlive it. A thread that never traced holds nothing and `reset()` does nothing for it.

`reset()` also **closes** the thread's stack, which is what makes async end-of-life deterministic rather than a question about garbage collection:

- **A worker that finishes after its request reset** finds a closed origin. Its spans and events are discarded on the spot and counted as `TraceLoss.discardedSpans`. They are not adopted into a stack nobody will ever capture from, and they are not left in memory.
- **A worker whose batch the 10,000-span adoption cap refuses** is discarded the same way, but counted where it already was — as a refused scope, the number that says a subtree is missing from the narrative.
- **A helper-owned worker** (`ForkGroup`, `FireAndForgetGroup`, anything using `activateWithoutAdoption()`) is discarded at collection, through `collectLocalTrace()` — capture and discard as one operation: the copied `TraceNode`s are the surviving record, and `merge()` re-emits them as the spans a reader sees. Because that capture is the last one the scope ever gets, the collect counts, as refused loss, any of the worker's own spans whose events it could not see — a child is allowed to be lost under pressure, never silently.

A capture is also a **bounded wait**, not a snapshot: the buffered path's drain stops at a slot another thread has claimed but not finished writing, and the thread inside that window can be descheduled for a whole scheduling quantum — so a flush that returned around the stall would leave the calling thread's *own* events outstanding, and a capture taken then would omit them silently. `flush()` is therefore a barrier for everything published before it was called, waiting out stalled claims under an explicit deadline (spin, then yield, 10 ms); `captureTrace()` still retries the flush while the pipeline reports itself undrained (up to 64 spin-flushes, for pipelines that drain incrementally). A pipeline that is already drained, which is every single-threaded capture, pays one comparison.

`discardedSpans` is deliberately **not** part of `TraceLoss.any()`, so it never triggers a renderer's incomplete-narrative footer: the discard happens after its request's tree was captured, so no rendered narrative is missing anything it could have contained. Read it when you want to know that async work is outliving its request; read `droppedEvents` and `refusedScopes` when you want to know whether the trace in your hand is complete.

### ForkGroup (fork-join)

Coordinates parallel tasks that are all awaited before continuing:

```java
var fork = ForkGroup.create(context);
Supplier<Price> priceTask = fork.wrap(() -> pricing.calculate(order));
Supplier<Stock> stockTask = fork.wrap(() -> inventory.check(order));
// submit to executor...
fork.merge(); // grafts results into parent with FORK_JOIN metadata
```

`wrap()` accepts `Supplier<T>`, `Runnable`, and `Callable<T>`. Each wrapped task runs in its own `ContextSnapshot`, collects trace roots, and records thread metadata. `merge()` grafts all collected children into the parent frame tagged with `ConcurrencyInfo(groupId, threadName, threadId, virtual, FORK_JOIN)`.

### FireAndForgetGroup (fire-and-forget)

Launches a task that runs independently — the parent continues without waiting:

```java
var group = FireAndForgetGroup.create(context, "NotificationService");
executor.submit(group.wrap(() -> notifier.send(orderId)));
```

A launcher node (`fire-and-forget`) is inserted into the parent tree at creation time. The child tree runs separately, linked by the shared `groupId`. Renderers stitch them together visually.

### Rendered output

Fork-join groups render with `⑂ fork [N tasks]` / `⑃ join — Xms` markers. Members are prefixed with `↦` and sorted by `className.methodName` for deterministic output. Thread names appear as `[thread: name]`. Wait-time analysis identifies the slowest/fastest members.

### Concurrency in trace trees

Concurrent children are linked by a shared `groupId` on their `ConcurrencyInfo`. Non-concurrent children have `concurrency: null`. Renderers use `ChildSegment` to partition consecutive children by groupId for visual grouping.

---

## OpenTelemetry Integration

The `narrativetrace-opentelemetry` module bridges NarrativeTrace with OpenTelemetry. `opentelemetry-api` is a `compileOnly` dependency — no runtime cost if not used.

### Batch export (TraceSpanExporter)

Maps a captured `TraceNode` tree to OTel spans after the fact:

```java
var tracer = openTelemetry.getTracer("narrativetrace");
var exporter = new TraceSpanExporter(tracer);
exporter.export(context.captureTrace().roots());
```

Each node becomes one span with attributes: `narrative.class`, `narrative.method`, `narrative.param.*`, `narrative.outcome`, `narrative.duration_ms`. Concurrent nodes add `narrative.concurrency.groupId`, `.kind`, `.threadId`, `.threadName`, `.virtual`. Parent-child hierarchy is preserved via `makeCurrent()`.

### Pipeline consumer (OtelTraceEventListener)

Creates OTel spans in real-time from `TraceEvent`s in the event pipeline:

```java
var tracer = openTelemetry.getTracer("narrativetrace");
var listener = new OtelTraceEventListener(tracer);
// Plug into DualPathPipeline as a synchronous listener, composable via Consumer.andThen()
```

`OtelTraceEventListener` is a `Consumer<TraceEvent>` that creates spans from `EnterEvent`/`ExitEvent` pairs. Spans carry `narrative.class`, `narrative.method`, `narrative.param.*`, and `narrative.outcome` attributes. Parent-child relationships use explicit handle-based linking (not OTel's implicit thread-local context). Explicit timestamps from event fields ensure accurate timing.

### When to use which

- **Batch export** (`TraceSpanExporter`) — post-hoc analysis, test output, low overhead during execution
- **Pipeline consumer** (`OtelTraceEventListener`) — real-time observability in production, integrates with Jaeger/Zipkin/OTLP collectors

---

## Troubleshooting

Moved to [`troubleshooting.md`](troubleshooting.md) — the dedicated page
indexes every failure mode listed above (and a few this file never carried:
the annotation-jar compile error, the Micronaut context-provider mismatch,
the agent's missing SLF4J provider, and why `./gradlew test` can print
nothing to your terminal) as symptom → cause → fix, with the exact commands
and messages verified against a live run.

## FAQ

Short, skeptical questions with a direct answer and a pointer to the fuller
treatment — nothing here duplicates the section it points to.

**Why not just use structured logging (SLF4J + MDC)?** Because someone still
has to write and maintain every log statement, at a level and with a message
someone chooses by hand. NarrativeTrace generates the trace from the method
signature that already exists and *uses* SLF4J for the durable path rather
than replacing it — see the README's
[How it compares](../README.md#how-it-compares) table.

**Why not OpenTelemetry?** Different granularity, not a competitor: spans
describe cross-service calls, generally without parameter values.
NarrativeTrace trees are method-level, with parameter and return values, and
`narrativetrace-opentelemetry` exports them *as* OTel spans — see
[OpenTelemetry Integration](#opentelemetry-integration).

**Why not Spring AOP logging?** AOP interceptors produce flat, mechanical
entry/exit lines with no opinion on whether the names are any good.
NarrativeTrace nests the call tree and scores the naming that produced it —
see [Clarity Scoring](#clarity-scoring).

**Will this leak secrets?** Every shipped integration path redacts by
default with no way to turn it off from configuration; the row-by-row
contract, including the two mechanisms that provide it and where each one
does and does not reach, is [Privacy and Redaction](privacy-and-redaction.md).

**Will this break production?** Recording is exception-isolated on every
path, and both pipeline consumers swallow their own errors — a throwing
`toString()` or a full buffer changes what gets traced, never what your
method returns or throws. See
[Privacy and Redaction § Guarantees](privacy-and-redaction.md#guarantees).

**What happens under async load?** The buffered analysis path sheds events
above 70% fill rather than blocking the caller or growing without bound, and
a run that lost events says so in its own footer — see
[Privacy and Redaction § The production loss model](privacy-and-redaction.md#the-production-loss-model-visually).

**What is free versus paid?** Everything in this repository — the runtime,
every format, the structural artifact with approval mode, clarity scoring,
and every integration — is free. Intelligence *across* runs and
repositories is Pro, and not all of it ships yet. The
[Feature Guide](feature-guide.md) is the authoritative status table.

**What happens when names are bad?** The trace still generates — it just
reads badly, and the clarity score says so with a per-element note at every
score, not only when something is wrong. The Minecraft naming comparison in
the [README](../README.md#what-the-output-looks-like) shows the same call
graph traced twice, once well-named and once generically, side by side.

---

## API Quick Reference

### Essential imports

```java
// Core
import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.api.config.TracingLevel;

// Proxy
import ai.narrativetrace.proxy.NarrativeTraceProxy;

// Annotations
import ai.narrativetrace.api.annotation.Narrated;
import ai.narrativetrace.api.annotation.OnError;
import ai.narrativetrace.api.annotation.NotTraced;
import ai.narrativetrace.api.annotation.NarrativeSummary;

// Renderers
import ai.narrativetrace.core.render.MarkdownRenderer;
import ai.narrativetrace.core.render.ProseRenderer;
import ai.narrativetrace.core.render.IndentedTextRenderer;

// JUnit 5
import ai.narrativetrace.junit5.NarrativeTraceExtension;

// JUnit 4
import ai.narrativetrace.junit4.NarrativeTraceRule;
import ai.narrativetrace.junit4.NarrativeTraceClassRule;

// Spring
import ai.narrativetrace.spring.EnableNarrativeTrace;

// Micronaut
import ai.narrativetrace.micronaut.NarrativeTraceProperties;
import ai.narrativetrace.micronaut.NarrativeTraceFactory;
import ai.narrativetrace.micronaut.NarrativeTraceBeanListener;
import ai.narrativetrace.micronaut.http.NarrativeTraceHttpFilter;

// Clarity
import ai.narrativetrace.clarity.ClarityAnalyzer;
import ai.narrativetrace.clarity.ClarityResult;
import ai.narrativetrace.clarity.ClarityScanner;

// Servlet
import ai.narrativetrace.servlet.NarrativeTraceFilter;
import ai.narrativetrace.servlet.Slf4jTraceExporter;

// SLF4J
import ai.narrativetrace.slf4j.Slf4jTraceEventListener;

// Diagrams
import ai.narrativetrace.diagrams.MermaidSequenceDiagramRenderer;
import ai.narrativetrace.diagrams.PlantUmlSequenceDiagramRenderer;

// OpenTelemetry
import ai.narrativetrace.opentelemetry.TraceSpanExporter;
import ai.narrativetrace.opentelemetry.OtelTraceEventListener;

// Concurrency
import ai.narrativetrace.core.context.ForkGroup;
import ai.narrativetrace.core.context.FireAndForgetGroup;
import ai.narrativetrace.api.event.ConcurrencyInfo;
import ai.narrativetrace.api.event.ConcurrencyKind;
```

### Minimal working example

```java
var context = new ThreadLocalNarrativeContext();
OrderService traced = NarrativeTraceProxy.trace(new OrderServiceImpl(), OrderService.class, context);

traced.placeOrder("C-1234", 2);

System.out.println(new IndentedTextRenderer().render(context.captureTrace()));
context.reset();
```

Output:
```
OrderService.placeOrder(customerId: "C-1234", quantity: 2)
  InventoryService.reserve(productId: "SKU-KB", quantity: 2) -> Reservation[...]
  PaymentService.charge(customerId: "C-1234", amount: 179.98) -> PaymentConfirmation[...]
-> OrderResult[orderId=ORD-001, totalCharged=179.98]
```
