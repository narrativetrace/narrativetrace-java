# NarrativeTrace Soak Harness

A **soak**, not a benchmark: a long-running Spring Boot application under open-model HTTP load,
with NarrativeTrace writing through a Logback `RollingFileAppender`, judged relative to a
tracing-off baseline taken in the same session. This is P1 — it builds the application, the
compose topology, a 10-minute k6 smoke profile, and the runner script. The **oracles** (the
automated pass/fail judgments over the collected evidence) are P2 — this harness already exposes
what they will need (`/soak/stats`, the rolled logs, the appender status log).

This module is **not published** as a Maven artifact — like `narrativetrace-examples`, it ships in
the public source-available snapshot (proven by being executed, not by a Maven coordinate).

## Module tree

```
narrativetrace-soak/
├── app/
│   ├── shop/     Gradle project :narrativetrace-soak:shop   — the traced application
│   └── notify/   Gradle project :narrativetrace-soak:notify — the downstream dependency
├── k6/           load-generation scripts (smoke + two-hour profiles, the poison probe)
├── compose.yaml  shop + notify + k6 + sampler topology
├── run-soak.sh   two-phase (baseline, then traced) runner — see below
└── summarize.py  builds results/SUMMARY.md from the run's artifacts
```

## Endpoints

**shop** (`:narrativetrace-soak:shop`, port 8080):

| Method | Path | Notes |
|---|---|---|
| `GET` | `/catalog` | Lists the seeded H2 catalog (`JdbcProductCatalogService.listAll()`) |
| `POST` | `/orders` | Places a multi-line order — see "the open-gate design" below |
| `GET` | `/orders/{id}` | Reads a placed order back from H2 |
| `POST` | `/orders/{id}/cancel` | Releases inventory and marks the order cancelled |
| `GET` | `/soak/stats` | Process health — see "What `/soak/stats` reports" |

**notify** (`:narrativetrace-soak:notify`, port 8081):

| Method | Path | Notes |
|---|---|---|
| `POST` | `/notifications` | Replaces the ecommerce example's third-party call — sleeps a configurable 5-50ms, fails a configurable 1% with a 503 |

## Reuse, never copy

The shop process reuses the ecommerce example's domain interfaces and, where suitable, its
implementations — `CustomerService`/`InMemoryCustomerService`, `PaymentService`/
`InMemoryPaymentService`, `DiscountService`/`InMemoryDiscountService`,
`ShippingEstimateService`/`InMemoryShippingEstimateService`, `InventoryService` (wrapped, see
below), `ProductCatalogService` (a new JDBC implementation of the same interface), and
`NotificationService` (a new HTTP implementation of the same interface, see "notify" above) — all
by project dependency on `:narrativetrace-examples:ecommerce`. **Zero changes were made to the
example.**

Three decisions the design note didn't anticipate, all driven by the code itself:

- **`DefaultOrderService` is not reused.** Its `placeOrder(customerId, productId, quantity)`
  signature is single-line, with no discount/shipping/notification step and no persistence — it
  cannot serve the multi-line-cart web API the brief calls for. `OrderPlacementService`
  (`domain/OrderPlacementService.java`) composes the same six reused interfaces directly instead;
  `DefaultOrderService` itself stays untouched, still exercised by the example's own tests.
- **`InMemoryInventoryService` is wrapped, not modified.** Its backing `HashMap` is not
  thread-safe, and a soak deliberately runs under concurrent load the example's own single-threaded
  scenarios never exercise. `ThreadSafeInventoryService` (a new class, `domain/`) synchronizes calls
  to an unmodified `InMemoryInventoryService` instance — composition, not a change to the example.
- **Stock is restocked at startup, not left at the example's seeded 150/500/75.** Found running
  the P1 smoke test: a 10-minute run at 20 req/s exhausted the demo-sized seed within seconds, and
  nearly every `POST /orders` after that failed as (correctly, but unintentionally) out-of-stock —
  k6's "order placed" check went from passing to 16 passes / 1593 fails. `ShopConfig` restocks
  every SKU to 1,000,000 via the reused `InventoryService#release` — composition again, not a
  change to `InMemoryInventoryService` — comfortably above realistic sustained demand and three
  orders of magnitude below the poison sentinel.

## The open-gate design, and why

Every field in `POST /orders` is validated at the edge (Bean Validation) **except each cart
line's `quantity`**, which is accepted unchecked and passed straight into the domain. A quantity
equal to the configured poison sentinel (`soak.poison.value`, default `2_000_000_000` — chosen to
exceed every seeded stock level, restocked to 1,000,000 per SKU at startup, see ShopConfig)
reaches `InventoryService.reserve` unmodified
and throws `IllegalStateException("Insufficient stock...")` — the same exception type an ordinary
large-but-plausible out-of-stock request throws. The two are told apart not by exception type but
by a marker: `OrderPlacementService` compares the raw quantity to the poison sentinel *before*
calling into the domain and, on a match, sets `MDC["soak.poison"]=true` and logs `!! poison
quantity=... reached the domain unchecked`. `ShopExceptionHandler` reads that marker to classify
the resulting `IllegalStateException` as a **poison exception** (500, counted in
`/soak/stats.poisonExceptionCount`) rather than a **business failure** (409, counted in
`businessFailureCount`).

`soak.poison.field` (default `quantity`) is **documentary in P1** — it names which field is open,
but does not itself rewire Bean Validation. Rotating which field is open is a code change
(annotate a different field, leave `quantity` alone) plus updating this property to match; fully
dynamic gate rotation (validator groups selected at runtime by property) was out of scope for P1.

Three failure kinds are distinguishable in both the rolled logs and `/soak/stats`:

1. **Edge rejections** — `MethodArgumentNotValidException`/`HttpMessageNotReadableException`, 400,
   never reach the domain. Counted in `edgeRejectionCount`.
2. **Business failures** — reported domain outcomes: unknown customer (404), out-of-stock (409,
   *not* the poison sentinel), payment declined (402, customer `C-BROKE`), order not found (404),
   already cancelled (409). Counted in `businessFailureCount`.
3. **Poison exceptions** — the open gate, above. Counted in `poisonExceptionCount`, response body
   carries `"status":"POISON"` and the log line carries the `!!` marker.

## Persistence and tracing

- **Persistence**: H2 in memory through `JdbcTemplate` for the catalog (`JdbcProductCatalogService`)
  and orders (`JdbcOrderRepository`) — real leaf-span JDBC query latency in the trace, not an
  in-memory map lookup.
- **Tracing**: the java agent (`-javaagent`), package filter
  `ai.narrativetrace.examples.ecommerce;ai.narrativetrace.soak.shop.domain` for shop,
  `ai.narrativetrace.soak.notify.domain` for notify — no NarrativeTrace import anywhere under
  `ai.narrativetrace.soak.shop.web`/the app root, or in the example. Proxies (`NarrativeTraceProxy`,
  wrapping the reused interfaces explicitly) are a documented second configuration, not wired here.
- **Baseline (phase 1 of `run-soak.sh`) runs with no `-javaagent` at all**, not
  `narrativetrace.level=OFF` — an attached-but-quiet agent still pays the bytecode-weaving and
  per-call dispatch-check cost; the truest zero-overhead baseline has no agent attached.
- **Request-boundary plumbing** (`TraceContextFilter` in each process) is written directly against
  the public `NarrativeContext`/`Traceparent` API (`AgentRuntime.getContext()`, `context.reset()`,
  `context.adoptTraceparent(...)`) rather than `narrativetrace-servlet`/`narrativetrace-spring-web`:
  those pin their own Spring Framework version, and this module wants a current,
  independently-managed Spring Boot BOM instead of reconciling two. See `build.gradle.kts` in each
  app for the full reasoning.
- **Rolling-appender configuration** (`logback-spring.xml` in each app) is the recommended
  production logging setup: synchronous `RollingFileAppender`,
  `SizeAndTimeBasedRollingPolicy` (50MB/file, 20-file cap, 1GB total size cap), no console appender,
  pattern carrying `%X{traceId}`, `%X{spanId}`, and `%X{nt.depth}` (the SLF4J listener's call-depth
  MDC key). A `StatusListener` (`SoakStatusListener`, one per app — Joran instantiates it directly
  from XML, so it cannot see Spring's resolved properties, only `SOAK_LOG_DIR` from the environment)
  writes appender status (rollovers, open/close, errors) to its own file, for P2's oracles.

## What `/soak/stats` reports

| Field | Source |
|---|---|
| `droppedEventCount` | `NarrativeContext#traceLoss().droppedEvents()` — the public API surface for `DualPathPipeline`'s process-wide drop count; P1 does not reach into pipeline internals |
| `bufferFill` | always `null` — not exposed by any public API; reported honestly rather than guessed |
| `heapUsedAfterLastGcBytes` | summed `MemoryPoolMXBean#getCollectionUsage()` across every pool that reports one — the JDK's own "as of the most recent collection" figure |
| `liveThreadCount` | `ThreadMXBean#getThreadCount()` |
| `openFileDescriptorCount` | `com.sun.management.UnixOperatingSystemMXBean#getOpenFileDescriptorCount()`, `-1` on a non-Unix JVM |
| `uptimeMillis` | `RuntimeMXBean#getUptime()` |
| `requestCount`, `exceptionCount`, `edgeRejectionCount`, `businessFailureCount`, `poisonExceptionCount` | `SoakMetrics`, incremented by `TraceContextFilter` and `ShopExceptionHandler` |

## Seeded PII

`k6/pii-seed.json` carries ten synthetic identities (well-known payment-sandbox test card numbers
— `4111111111111111` and the like, never real PII), each with an email, card number, session
cookie, and JWT. Every `POST /orders` request carries one, chosen at random. `email`/`cardNumber`/
`sessionCookie`/`jwt` flow as plain method parameters into the agent-woven
`OrderPlacementService.placeOrder`, deliberately *not* marked `@NotTraced` — the point is to give
NarrativeTrace's default value redaction real values to mask; `cardNumber` additionally reaches
`PaymentService#charge`'s `@NotTraced` `cardToken` parameter, exercising the example's own
redaction pattern too. Redaction stays at its defaults throughout — nothing here is configured.

## `k6/`

- **`scenario.js`** — the main traffic. Open-model executors (`ramping-arrival-rate`). Operation
  mix (`mix.js`'s `OPERATION_MIX`, picked by `pickOperation`): `GET /catalog` 60%,
  `GET /orders/{id}` 20%, `POST /orders` 15% (carts of 1-20 lines, quantities that vary — some
  large enough to exercise value-rendering caps), `POST /orders/{id}/cancel` 3%, business-failure
  requests (unknown customer / payment decline / out-of-stock, evenly split) 2%. Exports two
  profiles, selected by `-e SOAK_PROFILE=smoke|two-hour`:
  - **`smoke`** (P1, the one `run-soak.sh` runs): 1 min ramp to 20 req/s, 8 min steady, 1 min down.
  - **`two-hour`** — exported, **not run in P1**: 10 min ramp, 90 min steady (with two 5-min 3×
    spikes to 60 req/s inside it), 10 min recovery; 120 minutes total.
- **`poison.js`** — a **separate script**, run alongside `scenario.js` (not merged into its
  `options.scenarios` — the brief calls for a separate file, and `run-soak.sh` launches both as
  concurrent `k6 run` processes inside the same container). `constant-arrival-rate`, one poison
  request every 30s for the whole run.
- **`mix.js`** — the operation-mix percentages and the picker function, as a pure,
  framework-free ES module importable both by `scenario.js` (inside k6) and `mix.test.js` (inside
  Node) — k6's own JS runtime does not run under plain Node, so keeping this file free of k6 APIs
  is what makes it independently testable at all.
- **`mix.test.js`** — `node --test k6/mix.test.js` (Node ships in the dev container; if a
  contributor's environment lacks Node, the table above and `mix.js`'s literal `OPERATION_MIX`
  object are the fallback source of truth). Not wired into `./gradlew check` — introducing a Node
  toolchain to Gradle for one small pure-function test was judged disproportionate; run it
  manually, or as part of `run-soak.sh`'s own preflight if that need arises later.
- **`pii-seed.json`** — see "Seeded PII" above.
- Thresholds: `http_req_failed{expected_failure:false}` (unexpected HTTP errors) must be `rate==0`
  — every deliberate failure (business failures, the poison probe) is tagged
  `expected_failure:true` at the point it's fired, so this threshold only watches the rest. The
  run's JSON summary is written to `results/`.

## `compose.yaml` + `run-soak.sh`

Services: `shop`, `notify`, `k6` (`grafana/k6` image, running `scenario.js` and `poison.js`
concurrently), `sampler` (a shell loop curling `/soak/stats` into `results/stats.jsonl` every
30s). The agent's standalone jar (`narrativetrace-agent/build/libs/*-standalone.jar`) is
mounted read-only into `shop`/`notify` rather than baked into each image, so a rebuild of the
agent doesn't require rebuilding the application images. The rolling log directory (`logs/`) and
the results directory (`results/`) are bind-mounted to the host.

`./run-soak.sh <smoke|two-hour>`:

1. **Guards**: refuses to start if a lock file is present (another run in progress, or a stale one
   from a crash), if the repo's working tree is dirty, or if the host's 1-minute load average
   exceeds `cores × 0.5`.
2. **Precondition, not built by the script itself** (kept build-tool-agnostic — see the comment at
   the top of `run-soak.sh`): the shop/notify bootJars and the agent's standalone jar must already
   exist under their respective `build/libs/`.
3. **Phase 1 — baseline**, 10% of the profile length, tracing off (no `-javaagent`).
4. **Phase 2 — traced**, the full profile length.
5. Collects both phases' k6 summaries, `stats.jsonl`, the rolled logs, and the appender status log
   under `results/`, then runs `summarize.py` to build `results/SUMMARY.md`.

`results/SUMMARY.md` contains: latency percentiles (p50/p90/p95/p99 of `http_req_duration`) for
tracing-off vs tracing-on, throughput (request count and rate) for both phases, the pipeline's
drop count first-vs-last sample, heap/thread/fd first-vs-last sample, the poison probe's expected
vs observed count (`"!! poison"` log-line markers, matched against one-per-30s over the traced
phase's duration), the rolling appender's roll-event count and total bytes written.

**Exit code**: `run-soak.sh` exits non-zero if phase 2's k6 thresholds failed, or if the poison
count is off by more than 5% from expected.

## How to run

```bash
# Build the artifacts run-soak.sh needs (never run by the script itself):
./gradlew :narrativetrace-soak:shop:bootJar :narrativetrace-soak:notify:bootJar \
          :narrativetrace-agent:standaloneJar

# The 10-minute smoke profile (P1):
cd narrativetrace-soak
./run-soak.sh smoke

# The two-hour profile (exported, not exercised in P1):
./run-soak.sh two-hour
```

Docker is required (the compose file, and `grafana/k6`'s image) — building the jars never uses
host Gradle (`./gradlew` runs inside the dev container in this repo's own workflow); running the
containers is fine from the host.

## Also see

The ecommerce example this module reuses:
[`../narrativetrace-examples/README.md`](../narrativetrace-examples/README.md).
