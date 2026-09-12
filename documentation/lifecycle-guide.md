# NarrativeTrace Across the Development Cycle

[English](lifecycle-guide.md) | [Español](es/guia-del-ciclo-de-vida.md) | [简体中文](zh-CN/生命周期指南.md)

NarrativeTrace is not a test-time tool with a production mode bolted on,
nor a production tracer that happens to work in tests. It is **one capture
mechanism whose configuration and consumers change as code moves through
the cycle**: the same trace that documents a unit test during development
gates the build in CI, narrates an acceptance run against a deployed
environment, and streams through your log stack in production. This guide
walks the three stages, then condenses them into a configuration matrix
and a privacy posture.

The mechanism-level guides ([installation](installation-guide.md),
[configuration](configuration-guide.md),
[annotations](annotations-guide.md), the integration guides) explain each
piece; this guide explains **when in your process each piece earns its
keep**. For a hands-on feel before reading further, run `./demo.sh` at
the repository root.

---

## 1. Development time — the inner loop

At development time the consumer is **you, reading**. Tracing rides on
the tests you already write; nothing extra is instrumented and no
long-lived infrastructure exists.

- **Zero-config test integration.** With the JUnit 5 extension on the
  classpath (ServiceLoader-discovered; JUnit 4 rules are declared
  explicitly), every test writes its trace as a reviewable artifact by
  default — no configuration needed, `narrativetrace.output=false` opts out
  *(since 0.2.2, unreleased)*:
  `build/narrativetrace/traces/<TestClass>/<test>.md` plus a canonical
  `.json` and a per-scenario Mermaid diagram. The [Gradle
  plugin](gradle-plugin-guide.md) wires the dependencies, the
  `-parameters` compiler flag, and the test JVM properties in one
  `narrativeTrace { }` block.
- **Failing tests tell their story.** On a red test the full captured
  trace prints as the failure narrative — what was called, with which
  values, where it stopped — before you reach for the debugger.
- **Naming feedback while it is cheap.** Clarity scoring reads the
  captured traces and reports method/class/parameter naming quality per
  scenario (`clarity-report.md`, console summary). At this stage it is
  advisory: a mirror, not a gate.
- **Vocabulary grows from the code.** With `narrativetrace.glossary=true`
  the suite harvests domain terms into the committed `glossary.json` —
  curation happens in code review, like any other artifact.

The habit this stage builds is the point: **the trace is the first thing
you read**, before log statements, before the debugger. Everything later
in the cycle reuses that same readable capture.

## 2. Test time — CI, integration, acceptance, smoke

At test time the consumers are **machines and reviewers**: the build
gate, artifact archives, other platforms, AI tools, and testers watching
a deployed environment.

- **Traces as build artifacts.** The per-test files from stage 1 are CI
  artifacts: archive `build/narrativetrace/` and a failed pipeline
  carries its own narrative. Reviewers read what the code did, not what
  the committer says it does.
- **Quality gates.** `clarityCheck` runs inside `./gradlew check`:
  thresholds (`clarity.minScore`, `clarity.maxHighIssues`,
  `clarity.maxSuiteIssues`) turn the stage-1 mirror into a build gate,
  with `warnOnly` as the soft-launch mode. `clarityScan` scores compiled
  classes without running tests, for pipelines that split the two.
- **Machine-readable exports.** Two opt-in flags widen the audience:
  `narrativetrace.canonicalJson=true` writes per-test schema-1.2 entry
  arrays (the contract the other runtimes and conformance fixtures
  consume);
  `narrativetrace.structuralJson=true` writes the value-free structural
  artifact (ADR-002 Level 1) that can be handed to AI review tooling
  with zero data exposure.
- **Acceptance and smoke tests watch a deployed system.** Here tracing
  moves from the test JVM into the application under test, using the
  production integrations early: the servlet filter, Spring or Micronaut
  wiring, or — for a system you cannot modify — the Java agent
  (`-javaagent:narrativetrace-agent-<version>-standalone.jar`) attached
  to the deployment. A smoke test then asserts on *behavior you can
  read*: the narrated request flow in the container logs, correlated by
  `traceId` in MDC. The `narrativetrace-examples:ejb4` WildFly example
  is exactly this shape.
- **Same schema everywhere.** Because acceptance environments emit the
  same canonical stream as unit tests, tooling written against one stage
  works against the other — that is the parity the canonical schema
  exists to protect.

## 3. Production

In production the consumers are **operators, log tooling, and — behind
explicit routing — AI systems**. The design constraints change: cost,
loss-tolerance, and privacy dominate.

- **Scope and cost.** `narrativeTrace { scope.set("production") }` moves
  the library onto the runtime classpath. The capture level is the cost
  dial — `OFF` (~1–2 ns per call) → `ERRORS` → `SUMMARY` → `NARRATIVE` →
  `DETAIL` — and is switchable at runtime
  (`config.setLevel(...)`), so "turn narration up on the incident, back
  down after" is an operation, not a deploy.
- **Two independent gates (ADR-008).** The capture level decides what is
  recorded; your logging configuration decides what is emitted where.
  Narrative events flow through the SLF4J bridge under the
  `narrativetrace` logger (ENTRY/RETURN at TRACE, EXCEPTION at WARN by
  default), so ordinary appender configuration — not library
  configuration — routes, filters, and ships them.
- **Identity travels in MDC, not in message text.** Trace identity
  (`traceId`, `nt.class`, `nt.method`, `nt.package`), service identity
  (`service.*`, host/pid/runtime when `capture.resource` is on), and the
  three-tier attribute model (ADR-009) surface as MDC keys; your log
  pattern or JSON encoder decides visibility.
- **OpenTelemetry.** Captured traces export as OTel spans (batch or live
  listener) beside your existing tracing; NarrativeTrace never re-emits
  resource attributes the client SDK owns.
- **Live derived views are subscribers.** The best-effort pipeline
  (`BufferedEventConsumer`) exposes a publisher seam; everything derived
  attaches there, off the caller thread: the translated narration stream
  (`narrativetrace.i18n.<locale>` loggers or per-trace Markdown files,
  glossary-driven) and the AI-safe structural stream
  (`narrativetrace.ai.structural`, one value-free JSON line per event).
  This path is **lossy under load by design** — it sheds rather than
  blocks the application. The synchronous listener path is the durable
  record; derived views are conveniences layered on top.
- **Whoever starts a drain thread owns `close()`.** Every integration
  here — Spring, the servlet filter, the agent, both JUnit extensions —
  retains events through a consumer created with `startConsumer=false`:
  no background thread, no JVM shutdown hook, nothing rooting it. A
  context that goes out of scope is collected like any other object, and
  an un-closed one leaks nothing. Construct a `BufferedEventConsumer`
  with its own drain thread instead (`new BufferedEventConsumer()`, or
  the `(int capacity)` form) and the shutdown hook it registers roots the
  consumer, its ring and everything the ring retains until the JVM exits.
  That is the one shape where `close()` is mandatory —
  `try`-with-resources is the way to spell it.
- **Capture flags stay conservative.** `capture.resource` is on by
  default (opt out for hostname-sensitive deployments);
  `capture.sourceLocation` and `capture.instanceIds` are off by default.
  Flags gate capture, never schema shape — absent fields stay absent,
  and consumers never branch on configuration.

## The stage × configuration matrix

| | Development | Test / CI / acceptance | Production |
|---|---|---|---|
| **Primary consumer** | The developer, reading | Gates, artifacts, machines, testers | Operators, log stack, routed AI streams |
| **Integration** | JUnit 5/4 via the Gradle plugin | Same, plus filters/agent in deployed environments | Proxy / Spring / Micronaut / servlet / agent; `scope = "production"` |
| **Capture level** | `DETAIL` | `DETAIL` | `SUMMARY` or `NARRATIVE` baseline; `DETAIL` on demand; `OFF`/`ERRORS` on hot paths |
| **Outputs** | Per-test `.md` + `.json` + diagrams, failure narratives | The same as CI artifacts; `canonicalJson` / `structuralJson`; container logs in acceptance | SLF4J stream + MDC; OTel spans; subscriber streams (i18n, structural) |
| **Gates** | None — clarity is advisory | `clarityCheck` thresholds, your own assertions on traces | None — observability, not enforcement |
| **Loss model** | Complete (synchronous capture in-test) | Complete in-test; deployed = production model | Sync path durable; subscriber path best-effort, sheds under load |
| **Redaction** | **Always on** | **Always on** | **Always on** |

## Privacy posture across the cycle

The last row of the matrix is deliberate and worth stating as a rule:

**Redaction is unconditional. There is no stage, flag, or property that
turns `@NotTraced` or the name-based redaction rules off — by decision,
not omission** (owner decision 2026-08-16). The reasoning:

- The value of "outputs are safe" is that it is an *invariant*, not a
  configuration state. Every artifact this library writes — test traces,
  CI archives, container logs, AI streams — can be shared without first
  auditing which flags were set when it was produced.
- "Test data is synthetic" is exactly wrong in the environments where an
  off-switch would be most tempting: acceptance and staging systems are
  routinely seeded with production-shaped data.
- Test artifacts travel — into version control, tickets, and AI tools.
  Those are the outputs redaction exists to protect.

When a redacted value blocks debugging, the supported answers are, in
order: assert on behavior rather than the secret value; use the captured
**declared types** (schema 1.2) to diagnose shape without disclosure;
and craft obviously-fake fixtures whose *names* carry the information
(`"card-token-for-decline-path"` redacts, but the parameter name and
type still narrate). A **per-test, explicitly annotated escape hatch**
(visible in code review, wired only through the JUnit extension — never
through the production configuration chain — and stamping a loud banner
into any file it touches) is a recorded design direction, demand-gated:
it will be built when a real user hits the wall, and a global switch
will not be built at all.

Note what redaction does *not* have to carry alone: `SUMMARY` and
`NARRATIVE` levels suppress all parameter values, the structural
artifact elides every value field architecturally, and rendered values
cannot forge log lines or break Markdown/diagram syntax (injection
hardening). Redaction is one layer of a posture, and each stage of the
cycle picks the layers it needs.

## Where to go next

- [Installation Guide](installation-guide.md) — get stage 1 running in minutes
- [Configuration Guide](configuration-guide.md) — every key referenced above
- [Gradle Plugin Guide](gradle-plugin-guide.md) — the `narrativeTrace { }` DSL and gates
- [Clarity Guide](clarity-guide.md) — the scoring model behind the gate
- [Feature Guide](feature-guide.md) — the full catalog, with tier and status per feature
