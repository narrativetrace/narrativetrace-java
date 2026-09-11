# NarrativeTrace Examples

**English** | [Español](LEAME.md) | [简体中文](自述文件.md)

Runnable tutorials that show NarrativeTrace in practice. This module is **not published** —
it exists so a developer (or an AI agent) can run a realistic scenario, read the resulting
trace, and connect it back to the code that produced it.

Treat the subprojects as tutorials with a suggested order, not as a reusable API. Each
example package carries a `package-info.java` with a guided reading order; start there
when drilling into one.

## Subproject map

| Subproject | Language | Entry point | What it teaches |
|---|---|---|---|
| `ecommerce` | Java | `ECommerceExample` | The flagship: tracing a realistic Spring service graph — bean wiring, auto-proxying, SLF4J event logging, Micrometer-backed async context propagation, success and failure scenarios. |
| `clarity` | Java | `ClarityDemoExample` | How the clarity subsystem scores naming quality, using a hotel-reservation domain with deliberately excellent, adequate, and poor names. |
| `minecraft` | Java | `MinecraftExample` | How much naming alone changes trace quality: the same behavior traced twice, once with domain-rich names, once with generic ones. |
| `library` | Kotlin | `LibraryExample` | Using NarrativeTrace from Kotlin: tracing Kotlin services through dynamic proxies in a small book-lending domain. |
| `ejb4` | Java | `WildFlyAgentNarrationTest` *(Docker)* | An unmodified EJB 4 (Jakarta EE 10) WAR — servlet → EJB → services — traced inside WildFly by the java agent alone, written in deliberately EJB-2.x-era *style*; plus the clarity "rename these" report over its period names. |
| `common` | Java | *(utility, no `run` task)* | Shared example tooling: `PlantUmlImageRenderer` turns generated `.puml` trace diagrams into SVG images; `AsciiSequenceDiagram` draws them as Unicode text for the console. |

## Running the examples

Each example subproject registers a `run` task with its own logback configuration:

```bash
./gradlew :narrativetrace-examples:ecommerce:run
./gradlew :narrativetrace-examples:clarity:run
./gradlew :narrativetrace-examples:minecraft:run
./gradlew :narrativetrace-examples:library:run

./gradlew :narrativetrace-examples:runExamples   # all four in sequence
```

`ejb4` has no `run` task — it runs as a WAR inside a WildFly container, driven by its
Docker-tagged test (see its section below):

```bash
./gradlew :narrativetrace-examples:ejb4:dockerTest   # needs Docker
```

## Demo launcher

The repository root ships `./demo.sh` — the fastest way to watch the examples: one
command, no Gradle noise, the live narration colorized and indented by call depth,
and every rendering announced as its own section. `./demo.sh` opens an interactive
picker; `--example <name>` runs non-interactively; `--list` enumerates the examples.

**It walks, it does not scroll.** On a terminal the demo stops after every scenario —
`[Enter]` moves on, `q` quits — and each scenario opens with a note on *how that
scenario's trace is configured*: Spring AOP via `@EnableNarrativeTrace` here, a plain
`NarrativeTraceProxy` there, which of `@Narrated` / `@OnError` / `@NotTraced` produced
what you are about to read. The notes live in `demo/wiring.awk`, keyed by scenario
title, and the `demoWiringCheck` build task fails if a scenario ever loses its note or
a note outlives its scenario. Paced runs are recorded first and then walked, so a stop
point can never inflate the durations the trace tree reports; `--no-pause` plays the
run straight through, live, and is what pipes, CI, and the VHS recording get.

**Where the renderings come from** is answered once per run, at the first rendering
section, because it is the question every demo viewer asks next. There is no default
renderer and nothing to configure: capture produces a `TraceTree` and you call the
renderer you want (`new IndentedTextRenderer().render(trace)`), `NarrativeRenderer`
being a single method so your own is a lambda. The live `→ ← !!` lines are not a
renderer at all — that is `Slf4jTraceEventListener` in the `DualPathPipeline`, the only
view that costs no rendering code. Configuration selects a renderer in exactly one
place, trace files written from tests: `narrativetrace.output=true` plus
`narrativetrace.format=markdown|text|mermaid|plantuml`, where `markdown` is the default
and the Gradle plugin's `narrativeTrace { format = … }` sets the same property. Each
section marker names the renderer that produced it.

**Classic log output is a first-class mode.** The narration is ordinary SLF4J through
ordinary logback, so it renders in the traditional format every log tool ingests —
full `yyyy-MM-dd HH:mm:ss.SSS` timestamps, level, thread, and logger name (ecommerce
also carries the MDC `traceId`). Three places to see it:

- `./demo.sh --example <name> --classic` — the whole run through the example's own
  bundled logback config, verbatim.
- The ecommerce demo shows one scenario ("Unknown Customer") in classic form inline,
  mid-run — same events as the styled scenarios, presentation apart.
- Plain `./gradlew :narrativetrace-examples:<name>:run` uses the same traditional
  format via the bundled `logback-<name>.xml` configs.

**Translated traces are a first-class mode too.** Every example commits a domain
glossary (`<example>/glossary.json` — a bounded context with curated Spanish and
Chinese translations), and `./demo.sh --example <name> --lang es` (or `zh-CN`)
re-renders the same run through it: identifiers and `@Narrated` templates appear in
the chosen language with the original names kept in parentheses, while parameter
values, return values, and exception messages stay byte-identical. The interactive
picker offers exactly the locales the example's glossary carries. Untranslated
phrases collect into a "glossary gaps" footer — the curation work queue — and the
poorly-named scenarios (minecraft's unrefactored world, clarity's legacy processing)
stay untranslated on purpose: names that tell no story cannot be translated into one.

## The examples in detail

### ecommerce — production-style Spring application

The most complete example. `ECommerceExample` runs six scenarios:

1. **Successful order + async notification** — the happy path fanning out into async work,
   rendered as indented text, prose, and a Mermaid sequence diagram.
2. **Payment failure — inventory leak bug** — the trace exposes that
   `InventoryService.reserve` was called but `release` never was: a demonstration of
   traces surfacing real bugs.
3. **Flaky external service** — a decorator (`FlakyNotificationService`) wrapping a real
   HTTP client (`JsonPlaceholderNotificationService`) succeeds once, then fails.
4. **Unknown customer** — input-validation failure branch.
5. **Out of stock** — business-rule failure branch, additionally drawn as an ASCII
   sequence diagram in the console via PlantUML.
6. **Explicit async capture** — separate main-thread and worker-thread trace captures via
   `CompletableFuture` on a Spring `ThreadPoolTaskExecutor`.

Key supporting classes: `ECommerceConfig` (Spring + NarrativeTrace + Micrometer wiring),
`DefaultOrderService` (the orchestration that produces the interesting traces),
`ContextPropagatingTaskDecorator` (carries the trace context across thread boundaries),
plus simple in-memory adapters for catalog, inventory, payment, customer, discount, and
shipping so the trace stays easy to follow.

The test suite doubles as documentation: `SpringIntegrationTest` (auto-proxy wiring),
`ConcurrencyScenarioTest` (parallel capture), `MarkdownDocumentTest` (rendering a trace as
a full Markdown document with frontmatter), and per-service unit tests using the
`NarrativeTraceExtension` JUnit 5 extension. `NotificationServiceTest` is tagged
`network` because it performs real HTTP calls — the tag is excluded from all test tasks by
default.

### clarity — what the clarity analyzer rewards and penalizes

Four scenarios over a hotel-reservation domain, each at a different naming-quality tier:

1. **Guest books a room** — excellent, domain-specific naming (`DefaultReservationService`).
2. **Booking via manager** — adequate but less expressive naming (`DefaultBookingManager`).
3. **Legacy data processing** — intentionally weak naming (`DefaultDataProcessor`) that
   the analyzer should penalize.
4. **Guest repository operations** — a cohesion-focused scenario.

After running all scenarios it feeds the captured traces to `ClarityAnalyzer` and prints
`ClarityReportRenderer` output, so you can connect each score back to the naming choices
that caused it.

### minecraft — naming quality, contrasted directly

Two packages perform comparable "player joins world" work:

- `refactored/` — domain-rich names: `WorldGenerator`, `PlayerInventory`,
  `CraftingTable`, `CreatureSpawner`, `WorldServer`.
- `unrefactored/` — the same intent hidden behind generic labels: `DataProcessor`,
  `StateManager`, `ThingFactory`, `EntityHandler`, `GameManager`.

`MinecraftExample` runs both back to back so the traces can be compared side by side. It
is a teaching aid about naming and observability, not a gameplay sample. The
`unrefactored/**` tests are excluded from the trace-output tasks below so generated trace
documents only showcase the good vocabulary.

### library — Kotlin consumer

A small book-lending domain (`CatalogService`, `MemberService`, `LendingService`) traced
through `NarrativeTraceProxy` from Kotlin, with a successful borrow and a
`BookUnavailableException` failure scenario rendered as text, prose, Mermaid, and an
ASCII sequence diagram. The
build sets `javaParameters.set(true)` so real parameter names survive into the trace —
required for readable Kotlin traces.

### ejb4 — unmodified EJB 4 WAR under the java agent

An EJB 4 insurance-claims application (servlet → stateless EJB → services) written in
deliberate EJB-2.x-era *style* — `ClaimsProcessorBean`, `PolicyLookupEJB`,
`FraudChkMgr`, `CoverageCalcEJB` — compiled against container-provided APIs only and
packaged as a WAR with an empty `WEB-INF/lib`: it has **no NarrativeTrace dependency of
any kind**. `WildFlyAgentNarrationTest` (tagged `docker`, excluded from the default test
task) deploys that WAR to WildFly in Docker with only
`-javaagent:narrativetrace-agent-<version>-standalone.jar` attached, files a claim over
HTTP, and asserts the full narrated call chain — servlet, EJB container proxy, beans, and
services with parameters and return values — from the container log.

**Style vs technology.** The technology is current — Jakarta
Enterprise Beans 4.0 (`jakarta.*`, Jakarta EE 10) on WildFly, annotation-driven EJB 3.x
programming model (`@Stateless`, `@Singleton @Startup`, no-interface views, `@EJB`
injection, no `ejb-jar.xml`, no home/remote interfaces). Only the *architecture and
naming* deliberately imitate EJB-2.x-era codebases; that period style is what the
clarity report feeds on. There is no `javax.*`-namespace or EJB 2.x support here, and Java 8 /
EAP 6–7 servers are out of scope (the core library is Java 17-idiomatic; the plan's
decision record sketches a separate minimal recorder tier if such customers ever
materialize). What the example proves: a real app-server container, real container
proxies, real JBoss-modules classloading — narrated by one `-javaagent` flag.

The deliberately bad names double as the clarity tie-in: `Ejb4NamingClarityTest` (plain
`test` task, no Docker) runs `ClarityScanner` over the compiled classes and writes
`build/narrativetrace/ejb4-clarity-report.md`, the "rename these" report ("Spell out:
chk → check, mgr → manager"). The WildFly gotchas are pinned by the Docker harness
itself (full `JAVA_OPTS`, exact `jboss.modules.system.pkgs`, waiting on Undertow context
registration rather than the `Deployed` line) — read `WildFlyAgentNarrationTest` for the exact
settings.

### common — diagram rendering utility

`PlantUmlImageRenderer` converts `.puml` files produced by the trace tasks into `.svg`
images so diagram output can be viewed outside text-only tooling. Its test resources also
show property-file-driven trace output (`junit-platform.properties` with
`narrativetrace.output=true`).

## Trace-output tasks

Every example subproject gets these tasks (defined in this directory's
`build.gradle.kts`). They run the example's test suite with trace output enabled and
write one file per test into `<subproject>/build/narrativetrace/`:

```bash
./gradlew :narrativetrace-examples:ecommerce:traceTests      # Markdown documents
./gradlew :narrativetrace-examples:ecommerce:traceTexts      # indented text
./gradlew :narrativetrace-examples:ecommerce:traceMermaid    # Mermaid sequence diagrams
./gradlew :narrativetrace-examples:ecommerce:tracePlantUml   # PlantUML sequence diagrams
./gradlew :narrativetrace-examples:ecommerce:renderDiagrams  # tracePlantUml + .puml → .svg

./gradlew :narrativetrace-examples:traceExamples             # Markdown traces for all four
```

Exclusions that apply to the trace tasks: `network`-tagged tests,
`MarkdownDocumentTest` (it manages its own rendering), and `minecraft`'s
`unrefactored/**` tests.

## Quality gates

The examples are excluded from publishing and from the JDepend coupling gates, but they
are **not** exempt from code quality: PMD, Spotless (google-java-format for Java, ktlint
for Kotlin), the 20-NCSS method-length gate, and JaCoCo coverage all apply. Only the
un-unit-testable entry points (`*Example`, `PlantUmlImageRenderer`,
`ContextPropagatingTaskDecorator`) are excluded from the coverage denominator.

## Housekeeping notes

- `common/?/` (a literal question-mark directory) is a fontconfig cache leaked by
  containerized JVM runs with an unset `$HOME`. It is gitignored and safe to delete.
- Each example ships its own logback config (`logback-<name>.xml`) wired via the `run`
  task's JVM args, so console output stays readable per example.
- **Where the logger is configured.** Each `run` module depends on `narrativetrace-slf4j`
  (`build.gradle.kts`) so `PipelineBootstrap` auto-attaches `Slf4jTraceEventListener`
  with no wiring code (Configuration Guide, [§7](../documentation/configuration-guide.md#7-slf4j-configuration));
  each `logback-<name>.xml` sets the `narrativetrace` logger to `TRACE`, which is
  what makes the `→ ← !!` lines you see in the demo real SLF4J events, not
  demo-only formatting. `ecommerce/src/main/resources/logback-ecommerce.xml`,
  `clarity/src/main/resources/logback-clarity.xml`,
  `minecraft/src/main/resources/logback-minecraft.xml`, and
  `library/src/main/resources/logback-library.xml` are the four files.
