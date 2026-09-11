# NarrativeTrace

**English** | [Español](LEAME.md) | [Português](LEIAME.md) | [简体中文](自述文件.md)

## Start here

[See a trace in 60 seconds](documentation/first-10-minutes.md) — a console app, one run, and the trace is in your terminal.

## Demo

Clone the repository and run `./demo.sh`.

## Examples

See [the examples](narrativetrace-examples/) — NarrativeTrace in realistic applications.

## Code is the log

NarrativeTrace™ turns running Java code into a readable execution narrative,
built from the method, class and parameter names you already wrote. No
`logger.info(...)` lines. If the trace is unreadable, your code needs
refactoring — not more log statements.

## The problem

Half of this method is logging noise:

```java
public OrderResult placeOrder(String customerId, String productId, int quantity) {
    logger.info("Placing order for customer {} product {} quantity {}", customerId, productId, quantity);

    var inventory = inventoryService.reserve(productId, quantity);
    logger.debug("Reserved inventory: {}", inventory);

    var payment = paymentService.charge(customerId, inventory.total());
    logger.info("Payment processed: {}", payment.transactionId());

    var result = new OrderResult(payment.transactionId(), inventory.items());
    logger.info("Order placed successfully: {}", result);
    return result;
}
```

The business logic is three lines. The logging is another four. Every developer
writes these logs differently — different messages, different levels, different
included values. The result is inconsistent, verbose, and tangled with the code
it describes.

NarrativeTrace eliminates this entirely:

```java
public OrderResult placeOrder(String customerId, String productId, int quantity) {
    var inventory = inventoryService.reserve(productId, quantity);
    var payment = paymentService.charge(customerId, inventory.total());
    return new OrderResult(payment.transactionId(), inventory.items());
}
```

Pure business logic. The trace is generated from the method names, parameter
names and return values — the information that was already there.

## Code–log drift

Log statements are the one part of a codebase with no compiler check and, in
practice, no test coverage — so they silently stop being true as the code
changes. A rename leaves the message describing the old name; an added step
is simply never mentioned; a unit change (cents → euros) leaves `total`
describing a different number. Nothing catches it: log text is rarely
asserted, and where it is, the assertion is brittle and gets deleted first. A
stale log is worse than none — in an incident it is read as evidence of what
happened, when it is a sentence someone wrote once about code that has since
changed.

> **Code–log drift, eliminated by construction.** A log line is a claim about
> code, written once and never checked again. A narrative trace is derived
> from the run — so there is nothing to drift.

To be precise: a narration template (`@Narrated`) is still a hand-written
string, and a renamed parameter can break its placeholder — which is exactly
why it is the exception here, not the standard path (see the [Annotations
Guide](documentation/annotations-guide.md)). Everything else in a trace — the
calls, arguments, and outcomes — is derived, never written, so there is
nothing there to go stale. And because a trace is structural, a genuine
behavior change becomes something a reviewer can diff, not a sentence that
silently stopped matching the code — turn on approval mode (below) and that
diff fails the build instead of slipping by.

## What the output looks like

Run your code and get execution traces like this:

```
OrderService.placeOrder(customerId: "C-1234", productId: "SKU-MECHANICAL-KB", quantity: 2)
  CustomerService.findCustomer(customerId: "C-1234") -> Customer[id=C-1234, name=Alice Johnson, tier=GOLD]
  ProductCatalogService.lookupPrice(productId: "SKU-MECHANICAL-KB") -> 89.99
  InventoryService.reserve(productId: "SKU-MECHANICAL-KB", quantity: 2) -> Reservation[productId=SKU-MECHANICAL-KB, quantity=2]
  PaymentService.charge(customerId: "C-1234", amount: 179.98) -> PaymentConfirmation[transactionId=TXN-00001, amount=179.98]
-> OrderResult[orderId=ORD-00001, transactionId=TXN-00001, totalCharged=179.98, itemCount=2]
```

**When something goes wrong**, the trace makes the bug visible:

```
OrderService.placeOrder(customerId: "C-BROKE", productId: "SKU-MOUSE-PAD", quantity: 3)
  CustomerService.findCustomer(customerId: "C-BROKE") -> Customer[id=C-BROKE, name=Charlie Broke, tier=STANDARD]
  ProductCatalogService.lookupPrice(productId: "SKU-MOUSE-PAD") -> 24.99
  InventoryService.reserve(productId: "SKU-MOUSE-PAD", quantity: 3) -> Reservation[productId=SKU-MOUSE-PAD, quantity=3]
  PaymentService.charge(customerId: "C-BROKE", amount: 74.97) !! PaymentDeclinedException: Payment declined for customer C-BROKE
!! PaymentDeclinedException: Payment declined for customer C-BROKE
```

`InventoryService.reserve` was called but `InventoryService.release` is nowhere
in the trace. The bug is visible.

**The trace is only as good as your names.** The same Minecraft "player joins
world" flow, traced twice — once with domain names, once with generic ones.

**Domain names:**

```
WorldServer.playerJoined(playerName: "Steve")
  WorldGenerator.generateChunk(x: 0, z: 0) -> Chunk(x: 0, z: 0, biome: "plains")
  PlayerInventory.addItem(item: OAK_LOG, quantity: 4) -> true
  CraftingTable.craft(recipe: WOODEN_PICKAXE) -> WOODEN_PICKAXE
  CreatureSpawner.spawnHostile(type: ZOMBIE, x: 10, y: 64, z: 20) -> Creature(type: ZOMBIE, ...)
```

**Generic names:**

```
GameManager.handle(input: "Steve")
  DataProcessor.process(a: 0, b: 0) -> DataResult(a: 0, b: 0, tag: "plains")
  StateManager.update(type: 1, count: 4) -> true
  ThingFactory.create(type: 1) -> 1
  EntityHandler.execute(kind: 1, a: 10, b: 64, c: 20) -> Entity(kind: 1, ...)
```

Same call graph. Same return values. Only names differ. If your code can't tell
its own story, it needs refactoring — which is why NarrativeTrace also
[scores your naming](#beyond-the-first-trace).

### Why this matters for AI-assisted development

Every `logger.info(...)` line is a line that AI coding tools — Claude Code,
Copilot, Cursor — have to parse, spend tokens on, and reason around. In a
typical service class, logging is 30–50% of the lines. Remove them and the same
token budget covers more of your actual code, the model sees what the code does
rather than how it logs, and pull requests show business-logic changes instead
of mixed logic-and-logging changes.

There is a second half to it: each test also emits an **AI-safe structural
trace** (`structural/<Class>/<scenario>.nt`) — the call structure with every
runtime value stripped, safe to hand to an AI tool or commit to the repository.
See the [structural trace format](documentation/structural-trace-format.md).

### How it compares

| Instead of | NarrativeTrace |
|---|---|
| **Structured logging** (SLF4J + MDC) — you write the log statements | Generates them from code structure; when request tracing is active it also fills correlation fields such as `traceId` and a readable `traceName` like `bold elk soars`. It *uses* SLF4J rather than replacing it |
| **Distributed tracing** (OpenTelemetry, Jaeger) — spans across services, no parameter values | Method-level call trees with parameter and return values. `narrativetrace-opentelemetry` bridges both: NarrativeTrace trees exported as OTel spans with `narrative.*` attributes |
| **AOP logging** (Spring AOP, AspectJ) — flat, mechanical entry/exit lines | Nested call trees, plus a clarity score on the naming that produced them |

NarrativeTrace does not replace your production alerting or your service
topology maps. It gives you what neither provides: a human-readable execution
narrative that doubles as a code-quality diagnostic.

### Not a replacement for your logging framework

NarrativeTrace doesn't touch your logging framework. It ships no appender, no
encoder, no sink — your Logback or Log4j configuration, formats and
destinations keep working exactly as they do today.

What it replaces is the narration you write by hand: the
`log.info("Placing order {} for customer {}", ...)` lines that describe what
a method is doing. A traced method produces that narrative automatically,
from the method's own signature and return value.

This isn't a metaphor. The event pipeline's durable, synchronous path *is* an
SLF4J listener (`narrativetrace-slf4j`) — the generated narrative reaches
your appenders through the same SLF4J call a hand-written `log.info(...)`
would use. Manual log statements keep working right alongside it: same
logger, same streams, before, inside, or after a traced method. Mix the two
freely while you migrate — see [Coexisting with traditional
logging](documentation/configuration-guide.md#coexisting-with-traditional-logging)
for a worked example.

## Add it to one test

The shortest path from "interesting library" to "I saw a useful trace of my own
code" is the Gradle plugin plus a JUnit 5 test. Java 17+
([full compatibility matrix](documentation/installation-guide.md#compatibility)).

**1. Apply the plugin.** It adds the dependencies, sets the `-parameters`
compiler flag (without it traces show `arg0`, `arg1`), configures the test JVM
and supplies the Jupiter engine:

```kotlin
// build.gradle.kts
plugins {
    id("ai.narrativetrace") version "0.2.1"
}
```

**2. Trace one service in one test:**

```java
@ExtendWith(NarrativeTraceExtension.class)
class OrderServiceTest {
    @Test
    void customerPlacesOrder(NarrativeContext context) {
        OrderService service = NarrativeTraceProxy.trace(
            new DefaultOrderService(), OrderService.class, context);

        service.placeOrder("C-1234", "SKU-KB", 2);
    }
}
```

**3. Run the suite:**

```bash
./gradlew test
```

**4. Open the narrative** — the test method name became the scenario name:

```text
build/narrativetrace/traces/OrderServiceTest/customer_places_order.md
```

Every test in the suite writes its own set of artifacts:

```text
build/narrativetrace/
├── traces/<TestClass>/<scenario>.md        the human narrative
├── traces/<TestClass>/<scenario>.json      the same trace as canonical JSON
├── diagrams/<TestClass>/<scenario>.mmd     Mermaid sequence diagram
├── structural/<TestClass>/<scenario>.nt    value-free behavior shape (AI-safe)
└── clarity-report.md                       naming feedback for the whole suite
```

Without the plugin, the same setup is four lines of Gradle and two
dependencies — see the [Installation Guide](documentation/installation-guide.md).

Want to see this outside a test — a plain `main`, one call, a trace printed
to your terminal? → [See a trace in 60
seconds](documentation/first-10-minutes.md) is that path end to end, run for
real against the published artifacts, with the real output pasted in.

### Which artifact answers which question

One test writes several files. Open the one that answers your question:

| Your question | Read |
|---|---|
| What called what, in what order? | `structural/…/<scenario>.nt` — call structure, no values |
| What were the actual values? | `traces/…/<scenario>.json` — every call, every captured value |
| What happened, for a human? | `traces/…/<scenario>.md` — the narrative |
| Which file holds this scenario? | `manifest.json` — scenario → file, one row per invocation |

The Markdown narrative folds a run of same-shape iterations into the first one
in full plus a `×2 more: #2 sku=…` line, so the repeats are named rather than
shown. The JSON keeps every iteration whatever happens, and
`narrativetrace.unfolded=true` renders them all in Markdown too.

### Gradle or Maven?

The runtime jars are ordinary Maven artifacts. `ai.narrativetrace:narrativetrace-core:0.2.1`
and every module beside it resolve and work exactly the same from a Maven build;
nothing in the library itself is Gradle-specific. What *is* Gradle-specific is
the plugin above — it is a convenience that wires the compiler flag, the
dependencies and the test JVM for you.

So: same jars, either build tool — but the first-class, documented setup
experience is Gradle today. A worked Maven example now backs that claim:
[`narrativetrace-maven-example`](narrativetrace-maven-example) is a standalone
`pom.xml` consuming locally-installed artifacts, with Surefire wired for the
output directory and the JUnit 5 extension registered the Maven way — the
[Maven Guide](documentation/maven-guide.md) walks through it end to end,
including the one difference that matters: no plugin, so no automatic
compiler flag and no `approveNarratives` task, and what to do instead of
each. Both resolve the same released artifacts from Maven Central.

## Choose your integration

Tests are where most people start. This is where you go next:

| You want | Start with |
|---|---|
| Traces in tests, least wiring | Gradle plugin + `narrativetrace-junit5` |
| The same, on JUnit 4 | `narrativetrace-junit4` |
| To choose exactly what is wrapped, in plain Java | `narrativetrace-proxy` (JDK dynamic proxy) |
| Spring beans traced automatically | `narrativetrace-spring` |
| Spring HTTP request lifecycle in production | `narrativetrace-spring-web` (wires `narrativetrace-servlet`) |
| Any servlet app, no Spring | `narrativetrace-servlet` |
| Micronaut beans and requests | `narrativetrace-micronaut` + `narrativetrace-micronaut-http` |
| **Zero code changes** — an app you can't or won't modify | `narrativetrace-agent` (java agent) |
| Cross-thread async visibility (`@Async`, Reactor, executors) | `narrativetrace-micrometer`, or `ContextSnapshot` by hand |
| Traces in your production log stream | `narrativetrace-slf4j` |
| OpenTelemetry spans | `narrativetrace-opentelemetry` |

**Zero code changes** is worth spelling out, because it needs no build change at
all — the java agent rewrites classes as they load:

```bash
java -javaagent:narrativetrace-agent.jar=packages=com.example.myapp.* -jar your-app.jar
```

Every non-private method of every class under the named packages gets enter/exit
capture compiled into it; nothing outside them is touched. Package matching is
include-only and delimiter-aware, so `com.acme` never catches `com.acmeExtra`.
On an app server with no reachable classpath, use the `-standalone` jar and
`loggingJars=`; the agent deliberately brings no SLF4J provider of its own. Recipes
for every path above are in the
[Installation Guide](documentation/installation-guide.md); a decision diagram
for picking one is in
[Choosing an Integration](documentation/choosing-an-integration.md).

## Modules

| Module | You need it when... |
|--------|---------------------|
| `narrativetrace-api` | The compile-only contract: annotations, event model and SPIs. You usually get it transitively with `core` — depend on it alone when you are a library author publishing against the contract. |
| `narrativetrace-core` | Always required. Runtime context, pipeline, renderers, config and export. Zero runtime dependencies. |
| `narrativetrace-proxy` | Using JDK proxy tracing (most common). |
| `narrativetrace-junit5` | Auto-tracing in JUnit 5 tests. |
| `narrativetrace-junit4` | Auto-tracing in JUnit 4 tests. |
| `narrativetrace-spring` | Auto-wrapping Spring beans. |
| `narrativetrace-micronaut` | Auto-wrapping Micronaut beans. |
| `narrativetrace-slf4j` | Routing traces through SLF4J/Logback. |
| `narrativetrace-clarity` | Analyzing method/param naming quality. |
| `narrativetrace-glossary` | Harvesting a domain glossary (ubiquitous language) from traces. |
| `narrativetrace-diagrams` | Generating Mermaid/PlantUML sequence diagrams. |
| `narrativetrace-opentelemetry` | Export trace trees as OpenTelemetry spans, or live span creation via decorator. |
| `narrativetrace-micrometer` | Cross-thread trace propagation via Micrometer context-propagation. |
| `narrativetrace-agent` | Bytecode-level tracing without proxy wiring. |
| `narrativetrace-servlet` | Production request lifecycle in any servlet app (no Spring required). |
| `narrativetrace-spring-web` | Spring `@Configuration` for `narrativetrace-servlet` — auto-wires the filter with `ObjectProvider`. |
| `narrativetrace-micronaut-http` | Micronaut reactive HTTP filter for per-request trace lifecycle. |
| `narrativetrace-examples` | Reference apps (source only): e-commerce, Minecraft naming comparison, library lending, hotel-booking clarity demo, an EJB 4 (Jakarta EE) insurance-claims WAR on WildFly traced zero-code by the java agent (`ejb4`, Docker-based tests), plus shared utilities in `common`. |

**Typical starting point:** the Gradle plugin, which installs `core` + `proxy` +
`junit5` for you.

## Beyond the first trace

Three things the suite gives you once traces are running, each with a guide
behind it:

**Clarity scoring.** If the trace *is* the code, trace quality *is* code
quality. `clarity-report.md` scores every method, class and parameter name that
ran: generic names like `processData` or `handleRequest` score low,
domain-specific ones like `reserveInventory` or `customerId` score high, and
`clarityCheck` can fail the build on a threshold you set. Still experimental.
→ [Clarity Guide](documentation/clarity-guide.md)

**Narrative approval testing.** Every run compares each scenario's *structure*
against the last green run and ends the suite with one line:

```
NarrativeTrace — Suite complete
  5 scenarios recorded
  Clarity: 100% high | 0% moderate | 0% low
  Reports: build/narrativetrace
  Since last green: 4 scenarios unchanged · 1 changed: "Customer places order" (+1 call InventoryService.release)
```

Turn on approval mode (`narrativetrace.approval=true`, or `approval.set(true)`
in the plugin DSL) and that structure becomes a committed contract: a passing
test whose shape differs from its `src/test/narratives/<Class>/<scenario>.approved.nt`
baseline fails with a readable diff, the new shape lands beside it as
`.received.nt`, and `./gradlew approveNarratives` promotes what you reviewed. A
behavior change — including one an AI agent slipped into a refactoring — has to
be reviewed and approved, not merely compile. Baselines are value-free, so they
are stable across runs and safe to commit.
→ [Structural Trace Format](documentation/structural-trace-format.md)

**Concurrency.** Fork-join parallelism (`ForkGroup`), fire-and-forget work
(`FireAndForgetGroup`) and `CompletableFuture` returns are first-class in the
trace tree, rendered with `⑂ fork` / `⑃ join` markers, thread names and
wait-time analysis. `captureTrace()` is deliberately thread-scoped — capture on
the recording thread, or graft the work into the parent trace with
`ContextSnapshot.wrap()`.
→ [Complete reference: Concurrency](documentation/llms-full.md#concurrency)

## Privacy and safety

This library runs inside your process and writes files your team will share.
What that means, on one screen:

| Guarantee | How it holds |
|---|---|
| **Redaction is unconditional** | `@NotTraced` and the name-based deny-list (`password`, `token`, `cvv`, `ssn`, …) apply to every shipped output path — test traces, CI artifacts, container logs, agent narration. No stage, flag or property turns them off (owner decision, 2026-08-16). It survives every wrapper at any depth (`Optional`, `Future`, `AtomicReference`, `Map.Entry`) and a composite `Map` key; a type's own `toString()` is never trusted while the type has fields, so a curated one cannot print past a redaction; and a `{param.path}` template naming a redacted member resolves to `[REDACTED]`. |
| **The AI-safe artifact holds no values at all** | The structural `.nt` file contains names, hierarchy and outcome kinds only. Nothing to redact, zero prompt-injection surface — and that is a property test, not a policy. |
| **Tracing failures cannot fail your application** | Recording is exception-isolated on every path, and both pipeline consumers swallow their own errors. A throwing `toString()`, a full buffer or a broken appender never changes what your method returns or throws. |
| **Resource use is bounded** | The buffered analysis path is a fixed-size ring (65,536 slots by default, `narrativetrace.buffer.capacity`) that sheds rather than blocks — and says so: a capture that lost events prints the count in its own footer. Value rendering is capped in string length, collection size, object width and nesting depth. |
| **Output cannot be forged** | Rendered values are escaped so they cannot inject log lines, break Markdown, or corrupt diagram syntax. |
| **Stacking with other wrappers is safe** | AOP proxies, contract libraries, container interceptors and other agents can wrap the same method NarrativeTrace does. Nesting order can change how the trace *reads* — never what it *returns or throws*: recording is exception-isolated on every path and never replaces a result or exception. The goal is one trace frame per business-boundary crossing; machinery (bridge methods, container view stubs, another library's generated code) is not the target. |

Two honest limits. First, the only way values escape redaction is application
code that constructs its own `ValueRenderer` with `RedactionPolicy.DISABLED` — a
deliberate, reviewable act in your own source, never a configuration state.
Second, capture reads *fields* reflectively and never calls your getters, but it
does invoke a `@NarrativeSummary` method, the `toString()` of a type with no
instance fields, record component accessors, and property paths named in
`@Narrated`/`@OnError` templates; keep those pure, as you would for a debugger. Scoping today is
include-only — `packages=` for the agent, base packages for Spring and Micronaut
— with no exclude list yet.

→ [Privacy and Redaction](documentation/privacy-and-redaction.md) for the
row-by-row contract verified against the code,
[Lifecycle Guide](documentation/lifecycle-guide.md) for the privacy posture at
each stage, [Annotations Guide](documentation/annotations-guide.md) for the full
purity contract, and [Choosing an Integration § Stacking with other
wrappers](documentation/choosing-an-integration.md#stacking-with-other-wrappers)
for the per-mechanism detail on coexisting with AOP proxies, contract
libraries and other agents.

## Performance

We put real effort into the hot paths, and we will not claim "zero overhead" —
tracing does work and work costs something. What we measure (JMH, JDK 17,
`-prof gc`, one benchmark operation = one call):

- **Tracing OFF or inactive context:** the JDK proxy adds ~12–26 ns per call
  over a direct invocation (which measures ~9–12 ns in this harness) and
  24 B/op — one `Object[]` for the arguments. An `isActive()` gate skips all
  capture, rendering and reflection when tracing is disabled.
- **The bytecode agent's inactive path allocates nothing.** `agent_OFF`
  measures **56 B/op** — the same as a direct call — at ~37–44 ns per
  operation, which includes the context swap the benchmark performs inside its
  own measurement. An instrumented method reads a static `isActive()` before it
  marshals anything: with tracing off, no argument is boxed, no array is built,
  no call is made.
- **Active tracing:** a traced proxy call with parameter capture and value
  rendering measures **0.6–1.7 µs** and allocates **~1.0–1.5 kB/op**, depending
  on annotations. Capturing a one-node trace and resetting the context costs
  1.3–2.3 µs and 2.8–3.1 kB.

These figures come from [JMH benchmarks](narrativetrace-benchmarks/) run on a
shared container, where allocation reproduces to the byte and the nanosecond
numbers move with machine load — so both are recorded as *ceilings* in
[`baseline.txt`](narrativetrace-benchmarks/baseline.txt) and
[`allocation-baseline.txt`](narrativetrace-benchmarks/allocation-baseline.txt),
and regressions stay visible across commits. They are higher than the numbers
this README carried until 2026-08-31, which were measured against a pipeline
that did less: every enter and exit now publishes an event through a ring buffer
into the retained store, and every span carries W3C trace identity. Performance
is an ongoing concern, not a solved problem. For extremely hot loops, use
`TracingLevel.OFF` or narrow the traced scope.

## What is free and what is Pro

**Free** is everything in this repository — source-available under BSL 1.1,
free in production, converting to Apache 2.0 four years after each release: the
whole runtime, per-test traces in every format, the structural `.nt` artifact
with delta reporting and approval mode, clarity scoring, the domain glossary and
translated trace views, and every integration in the table above. The
`narrativetrace-api` contract jar is Apache 2.0 outright.

**Pro** is intelligence *across* runs and repositories: aggregated flow
summaries and runtime dependency graphs, migration and semantic diffs, aggregate
sequence diagrams, PR-review bots and drift analytics, historical clarity
trending, MCP tools and server for AI agents, and the audit & compliance suite
(`@AuditEvent`, policy engine, field masking, control traceability). Not all of
it ships today. The [Feature Guide](documentation/feature-guide.md) is the
authoritative status table: it labels every single feature Open, Free, Pro, In
development or Planned, and cites the code behind each shipped row.

## Documentation

**[documentation/](documentation/README.md)** indexes every document in this
repository, English guides and their Spanish/Chinese translations alike. The
complete published documentation — including pages with no counterpart here —
is at **[narrativetrace.ai/docs](https://narrativetrace.ai/docs.html)**.
For AI agents: [`documentation/llms.txt`](documentation/llms.txt) is the machine-readable
index, [`documentation/llms-full.md`](documentation/llms-full.md) the complete
single-file reference, and every published module ships a Javadoc-rich source jar
([javadoc.io](https://javadoc.io/doc/ai.narrativetrace)).

Start here:

- [See a trace in 60 seconds](documentation/first-10-minutes.md) — the smallest path to a real trace: one file, one run, real output pasted in
- [Installation Guide](documentation/installation-guide.md) — dependencies, every integration path, how capture works, trace output setup
- [Choosing an Integration](documentation/choosing-an-integration.md) — which module you need, as a decision diagram
- [Configuration Guide](documentation/configuration-guide.md) — tracing levels, JUnit/Gradle/Spring/Micronaut/SLF4J config
- [Gradle Plugin Guide](documentation/gradle-plugin-guide.md) — DSL reference, quality gates, recipes
- [Annotations Guide](documentation/annotations-guide.md) — `@Narrated`, `@OnError`, `@NotTraced`, `@NarrativeSummary`, `@EnableNarrativeTrace`

Going deeper:

- [Lifecycle Guide](documentation/lifecycle-guide.md) — where NarrativeTrace lives in your process: development, CI/acceptance, production
- [Privacy and Redaction](documentation/privacy-and-redaction.md) — the row-by-row redaction contract, verified against the code
- [What to Commit](documentation/what-to-commit.md) — which generated files are CI artifacts and which are reviewed baselines
- [Troubleshooting](documentation/troubleshooting.md) — symptom → cause → fix for the failure modes people actually hit
- [Spring Integration Guide](documentation/spring-integration-guide.md) — bean tracing, servlet filter, `@Async` propagation
- [Micronaut Integration Guide](documentation/micronaut-integration-guide.md) — bean tracing, HTTP filter, configuration properties
- [Clarity Guide](documentation/clarity-guide.md) — scoring model, NLP components, JUnit integration
- [Feature Guide](documentation/feature-guide.md) — canonical catalog of every feature across all platforms, with tier and status
- [Structural Trace Format](documentation/structural-trace-format.md) — the value-free `.nt` artifact behind delta reporting and approval testing
- [Complete Reference](documentation/llms-full.md) — API, capture internals, configuration, integration recipes, troubleshooting, in one file
- [Domain Glossary](https://narrativetrace.ai/doc.html?p=docs/glossary.md) — ubiquitous-language glossary harvested from traces, and translated trace views

## Building from source

```bash
./gradlew test                                     # run all tests
./gradlew check                                    # tests + PMD + JaCoCo coverage + the other gates
./gradlew verifyAll                                # every verification category this repo has — see below
./gradlew :narrativetrace-examples:runExamples     # every example in sequence
./gradlew :narrativetrace-examples:traceExamples   # run tests → Markdown trace files
./gradlew :narrativetrace-examples:ejb4:dockerTest # EJB 4 WAR on WildFly traced by the agent alone (needs Docker)
```

`./gradlew verifyAll` runs every verification this repo has, end to end, in one
command: unit tests, coverage, mutation testing, property-based tests, both
fuzzing tiers, concurrency stress, benchmarks and allocation, architecture
rules, secrets/security/dependency scanners, formatting, linting and
translation checks. It is **long-running by design** — mutation testing alone
typically takes well over an hour — and that is deliberate: the point is one
command anyone who clones the repo can run and trust, not a fast one. A
failing category never stops the run; every category executes regardless, and
`verifyAll` exits non-zero only at the very end, if anything failed. It writes
one row per category to a JSON report — the concrete tool, a status
(`passed`/`failed`/`skipped`/`not-implemented` — the last one is a real,
first-class answer for a category this project genuinely has no tool for, not
a failure), and real numbers parsed from that tool's own output, never
estimated — plus a Markdown table rendered from that same JSON, so the two can
never disagree.

## FAQ

### How much overhead does this add, and what happens under high concurrency?

We will not claim "zero overhead" — see [Performance](#performance) above for the dated numbers this answer summarizes (JMH, JDK 17, `-prof gc`, committed to [`narrativetrace-benchmarks/baseline.txt`](narrativetrace-benchmarks/baseline.txt) and [`allocation-baseline.txt`](narrativetrace-benchmarks/allocation-baseline.txt), most recently refreshed 2026-08-31/2026-09-01): a direct, untraced call costs ~9–12 ns in this harness; the JDK dynamic proxy with tracing off adds only ~12–26 ns and 24 B/op (one `Object[]` for the arguments) behind an `isActive()` gate that skips all capture, rendering and reflection. The bytecode agent's inactive path is cheaper still — ~37–44 ns at **56 B/op, the same allocation as the direct call itself** — because an instrumented method reads a static `isActive()` before it marshals anything. With tracing fully on (parameter capture and value rendering), a traced call costs **0.6–1.7 µs** and allocates **~1.0–1.5 kB/op**; capturing and resetting a one-node trace costs 1.3–2.3 µs and 2.8–3.1 kB.

What NarrativeTrace itself adds is that capture — intercepting the call, reading arguments, building the trace tree. Everything downstream of capture (the SLF4J write, the collector, the disk or network) is the same cost your logging stack already pays; NarrativeTrace does not add a second sink. For a team replacing hand-written log statements, the sink side is close to a wash: N log calls per method become one trace write, and those statements stop being written, reviewed, and kept in sync with the code.

Under concurrency, the default `DualPathPipeline`'s two paths carry different guarantees. The synchronous path — typically an `Slf4jTraceEventListener` — runs inline on the caller's thread: the write completes before the method returns, so it is exactly as durable, and costs exactly what, a log call already does. The buffered, best-effort path — the one that backs `captureTrace()` and analysis — is a bounded ring buffer (65,536 slots by default, `narrativetrace.buffer.capacity`) that never grows. It load-sheds above 70% fill rather than blocking the caller, and every dropped event is **counted** — ring overwrites, adaptive-drain discards and subscriber drops alike, via `BufferedEventConsumer.droppedCount()` — and surfaced in the trace's own footer, omitted only when nothing was lost: a short trace is never silently indistinguishable from a quiet one.

**The honest gap:** there is no sampling in this runtime, or in any NarrativeTrace runtime, today — every traced call is captured in full at its configured `TracingLevel`. A percentage- or rate-based sampler is on the roadmap, not shipped. If you need to cap capture volume now, use `TracingLevel.OFF` or narrow the traced scope to the boundary that matters.

### How do I know a parameter with PII or credentials won't leak into a trace?

Four independent layers, not one blanket promise — the row-by-row contract, verified against the code, is [Privacy and Redaction](documentation/privacy-and-redaction.md):

1. **`@NotTraced` on a parameter, field, or record component** — explicit redaction you control, unconditional: no stage, flag, or property turns it off. Nothing outranks it, and nothing routes around it: a type's own `toString()` is never trusted while the type has fields, so a curated one is not called at all rather than being allowed to print past the annotation.
2. **An always-on, multilingual name deny-list** (`RedactionPolicy.DEFAULT`) — matches field and parameter names against `password`, `secret`, `token`, `ssn`, `cvv`, `apikey`, `cardnumber`, `jwt`, `cookie`, `sessionid`, `accountnumber`, `routingnumber`, plus Spanish (`contraseña`, `tarjeta`, `cédula`, `clave de acceso`), Portuguese (`cartão`), French (`mot de passe`, `carte bancaire`), German (`Passwort`, `Kennwort`) and Chinese (`密码`, `身份证`) equivalents. It is on by default, not opt-in, and the patterns most prone to false positives (`pan`, `iban`, `rut`, `cuit`, `dni`, `senha`, `cpf`, `cnpj`, `nir`, `mima`) match only on identifier-token boundaries, so `panelId` and `circuitBreaker` stay visible.
3. **Value-shape matching, independent of the field name** — a JWT-shaped string, a Luhn-valid card number, a `Set-Cookie`-shaped value, a national-ID checksum (Chilean RUT, Brazilian CPF/CNPJ, Spanish DNI/NIE, French NIR, Chinese resident ID), or a dashed US Social Security number is redacted even when it arrives under an innocuous name like `data` or `value`. The US SSN is the one shape here without a checksum to lean on, so only the dashed `AAA-GG-SSSS` form counts: nine bare digits are indistinguishable from an order number, and blanking those would cost more than it protects.
4. **The value-free `.nt` structural mode (ADR-002) — the categorical guarantee.** A `.nt` artifact carries only class, method and parameter *names*, the call hierarchy, and outcome *kinds* — zero runtime values, zero prompt-injection surface, and that is a property test, not a policy someone could forget to apply. Committed as an `.approved.nt` baseline, it is what to hand an external AI tool when no value may leave the process at all. See the [Structural Trace Format](documentation/structural-trace-format.md).

Be precise about the boundary: layers 1–3 are heuristic and extensible — patterns get added as gaps are found, and can always miss one nobody has named yet. Layer 4 is the only *categorical* one. If your threat model requires "no value can possibly leave the process," reach for the `.nt` structural artifact, not the redaction layers alone.

### Can trace IDs correlate with a standard correlation ID across services, or is tracing local only?

Yes — through W3C `traceparent`, the same mechanism OpenTelemetry itself uses. An inbound `traceparent` header is adopted via `NarrativeContext.adoptTraceparent(...)` (wired automatically by the servlet filter, the Micronaut HTTP filter, and Spring's web filter), and NarrativeTrace's own trace id **becomes** that header's trace id directly — not a separate identifier merely shaped to match. `outboundTraceparent()` gives any HTTP client the value to attach on the way out (the ecommerce example wires this into a real `HttpRequest.Builder`). Where no header is present, a fresh id is generated in the same W3C 32-lowercase-hex-character shape. The `narrativetrace-opentelemetry` module additionally exports NarrativeTrace spans (`TraceSpanExporter`, batch; `OtelTraceEventListener`, live) with typed attributes, so your existing OTel collector, Jaeger, or correlation-id middleware understands the id with nothing to reconcile.

What stays local: the narrative tree itself — the nested method calls, arguments, narration — is captured per process and is never shipped to another service; only the trace id crosses the boundary. A downstream service produces its own narrative tree correlated to that same id, not one merged cross-service tree.

## License

NarrativeTrace's API and output format are open standards (Apache 2.0). Its
runtime is free and source-available (BSL 1.1, converting to Apache 2.0 four
years after each release). Pro is commercial.

| Artifact | Licence | What that means |
|---|---|---|
| `narrativetrace-api` | [Apache 2.0](LICENSE-APACHE) | The annotations, the event model and the SPIs — everything your code compiles against. An open standard, so any implementation can target it. |
| every other `ai.narrativetrace` artifact | [BSL 1.1](LICENSE) | The runtime. Free to use in production, including in products and services you provide to your own customers. The one exclusion is offering NarrativeTrace itself — or a product or service whose value derives substantially from it — to third parties as a logging, tracing or code-narrative product or service. Each released version becomes Apache 2.0 four years after it is published. |
| the documentation (prose guides) | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) | The guides and reference documentation. The output-format specification and the JSON schemas are Apache 2.0 — they are the open standard. |

Which category each module ships under is declared in
[`licensing.properties`](licensing.properties), and the build refuses a
dependency graph the licences cannot support.

The runtime is **not** open source, and this README will not call it that. It
is source-available and free, with a dated promise to become open source.

<!-- legal:trademark:begin -->
Neither licence grants any trademark right: NarrativeTrace is a trademark of
Empower Agile, and permission to use, copy or modify the code is not permission
to use the name for your own distribution or service.
<!-- legal:trademark:end -->

### The licence, in plain words

The `narrativetrace-api` jar, the output-format specification and the JSON
schemas are Apache 2.0 — open source outright, no restrictions beyond Apache's
own.

<!-- legal:plain-words:begin -->
**Free to run.** The runtime is source-available under the Business Source
License 1.1: read it, audit it, patch it, and use it in production at no cost —
including inside the products and services you sell to your own customers.

**One exclusion.** You may not offer NarrativeTrace itself — or a product or
service whose value derives substantially from it — to third parties as a
logging, tracing or code-narrative product or service.

**It opens on a date.** Every release converts to Apache 2.0 four years after it
is published; the exact date is printed in that release's LICENSE.

*This summary is a courtesy, not a licence. The LICENSE file is the only binding
text; where the two differ, the LICENSE governs.*
<!-- legal:plain-words:end -->
