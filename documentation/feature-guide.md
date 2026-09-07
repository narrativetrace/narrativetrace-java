# NarrativeTrace Feature Guide

**Scope: Product.** This guide is the canonical catalog of
NarrativeTrace features for every platform (Java, TypeScript, Python,
.NET).

The catalog has one home so it cannot drift into per-platform copies: a
decision that holds on every platform is recorded once, here; a mechanism
specific to one platform's implementation stays in that platform's own
documentation. Every shipped row cites the Java classes that implement it
(`module: main classes`) so a reader goes straight from a row to the code
in this repository. A row describes *what* the feature is; agreement
between platforms on *how* it behaves belongs to conformance fixtures over
the canonical JSON schema, not to prose here.

Organized by what you want to accomplish, not by module.

**Status labels:**

- **Open** — shipped, Apache 2.0, in `narrativetrace-api`: the annotations, the
  event model, the output-format specification and the SPIs. An open standard,
  so any implementation can target it.
- **Free** — shipped, source-available (BSL 1.1, converts to Apache 2.0 after
  four years), available in this repository. Free to use in production.
- **Free/closed** — shipped free of charge, proprietary, built outside this
  repository.
- **Pro** — shipped in NarrativeTrace Pro (commercial tier).
- **In development** — actively being built; the design is settled.
- **Planned** — specified, not yet started; may change.

Which category each module of this repository ships under is declared in
`licensing.properties`, and the build refuses a dependency graph the licences
cannot support.

Last full audit of this guide against the Java code: **2026-08-18**.

---

## Capture the story of your code (core tracing)

| Feature | Status | Java implementation | Notes |
|---|---|---|---|
| Automatic narrative capture — method, class, parameter names, return values, timing, errors; zero log statements | Free | `proxy: NarrativeTraceProxy` · `agent: NarrativeClassFileTransformer` · `core: NarrativeContext, TraceEvent` | Tier 1: no annotations, no config. Parameter names require `-parameters` (the Gradle plugin adds it) |
| Enrichment annotations — `@Narrated`, `@OnError`/`@OnErrors` with `{param}` templates, `@NarrativeSummary` | Free | `core: Narrated, OnError, NarrativeSummary, TemplateParser` | Unresolved placeholders are reported at test time (`TemplateWarningCollector`); narration renders on every traced call, including leaf calls. [annotations-guide.md](annotations-guide.md) |
| Sensitive data redaction — `@NotTraced` (all outputs, always) + name-based redaction rules | Free | `core: NotTraced, RedactionPolicy` | Case-insensitive substring deny-list on field names; built-in defaults (password, token, ssn, …) + custom sets. Map keys render through the same guarded path (redaction + bounds), never raw `toString()`. Single-payload wrappers (`Optional`, the primitive optionals, `Future`, `AtomicReference`) and the list- and pair-shaped holders (`AtomicReferenceArray`, a standalone `Map.Entry`) are opened and their payload rendered by the same rules, so redaction is not lost one container deep |
| Void-completion contract — void methods carry no rendered value (`null`, never the string "null") | Free | `proxy` + `agent` capture; honored by every renderer/exporter | Markdown/text render nothing, JSON omits `returnValue`, diagrams render ✓, SLF4J logs “← completed”; a rendered `"null"` always means a real null return |
| Output injection hardening — rendered values cannot forge log lines or break Markdown/diagram syntax | Free | `core: MarkdownEscape, ControlEscape` · `diagrams: DiagramText` | Control-character sanitizing, HTML escaping, dynamic code-fence widening |
| Five capture levels (OFF → ERRORS → SUMMARY → NARRATIVE → DETAIL), changeable at runtime | Free | `core: TracingLevel, NarrativeTraceConfig` | OFF costs ~1–2 ns; SUMMARY/NARRATIVE suppress parameter values |
| Two-gate level architecture — capture level and log level are independent | Free | `core: NarrativeTraceConfig` · `slf4j: Slf4jTraceEventListener` | ADR-008. [configuration-guide.md](configuration-guide.md) |
| Configuration resolution — system property → `narrativetrace.properties`, duplicate-config fail-fast | Free | `core: ConfigResolver, DuplicateConfigurationException` | Two config files on the classpath is a hard error, not silent precedence |
| Concurrency capture — fork/join and fire-and-forget groups, cross-thread grafting, virtual threads, wait-time and sequential-async diagnostics | Free | `core: ForkGroup, FireAndForgetGroup, ContextSnapshot` · `render: SequentialAsyncDetector` | jcstress-verified; wait-time and sequential-async analysis are render-time (Markdown output). `captureTrace()` is thread-scoped — capture on the recording thread or graft via `ContextSnapshot.wrap()` |
| Trace identity — traceId, human-readable trace names, storyId/chapterId derivation | Free | `core: TraceId, SpanContext` · `export: CanonicalEntryMapper` | Canonical-schema aligned |
| Trace scaling for high-fan-out loops — sampling, width/depth limits, streaming mode | Planned (Free) | — | Until then: `TracingLevel.OFF` opt-out in hot loops |

## Attach it to your stack (integrations)

| Feature | Status | Java implementation | Notes |
|---|---|---|---|
| JDK dynamic proxy wrapping | Free | `proxy: NarrativeTraceProxy` | `NarrativeTraceProxy.trace(...)`, single- and multi-interface overloads |
| Java agent — bytecode instrumentation, zero code changes, package filtering | Free | `agent: NarrativeTraceAgent, AgentConfig` | Filter via agent args or `narrativetrace.properties`. The `-standalone` classifier jar bundles core + SLF4J bridge for `-javaagent` attach on hosts with no build tool (app servers); `loggingJars=` agent arg injects an SLF4J provider via `appendToSystemClassLoaderSearch` |
| Spring — `@EnableNarrativeTrace`, automatic bean wrapping | Free | `spring: EnableNarrativeTrace, NarrativeTraceBeanPostProcessor` | [spring-integration-guide.md](spring-integration-guide.md) |
| Micronaut — bean wrapping + reactive HTTP filter | Free | `micronaut: NarrativeTraceBeanListener` · `micronaut-http: NarrativeTraceHttpFilter` | Kotlin-first modules. [micronaut-integration-guide.md](micronaut-integration-guide.md) |
| Servlet request lifecycle filter (no Spring required) + Spring Web `@Configuration` wiring | Free | `servlet: NarrativeTraceFilter` · `spring-web: NarrativeTraceWebConfiguration` | Import-based `@Configuration`, not Boot auto-configuration |
| Cross-process trace continuity — W3C `traceparent` read on inbound requests, written on outbound calls | Free | `core: Traceparent, NarrativeContext.adoptTraceparent/outboundTraceparent` · `servlet: NarrativeTraceFilter` · `micronaut-http: NarrativeTraceHttpFilter` | One story across service boundaries. The filters adopt an inbound header; `outboundTraceparent()` gives any HTTP client the value to send. ADR-014 rung 1: the adopted span parents the root span only, and a malformed header is ignored rather than failing the request |
| JUnit 5 extension / JUnit 4 rules — per-test scenarios | Free | `junit5: NarrativeTraceExtension` · `junit4: NarrativeTraceClassRule, NarrativeTraceRule` | ServiceLoader auto-detection is JUnit 5 only; JUnit 4 rules are declared explicitly |
| Gradle plugin — dependency wiring, `-parameters`, clarity tasks | Free | `gradle-plugin: NarrativeTracePlugin, ClarityCheckTask` | [gradle-plugin-guide.md](gradle-plugin-guide.md) |
| Cross-thread context propagation via Micrometer (Spring Boot 3 / Reactor / `@Async`) | Free | `micrometer: NarrativeTraceThreadLocalAccessor` | Single `ThreadLocalAccessor` under key `"narrativetrace"` |
| Runnable reference apps — e-commerce, Minecraft naming comparison, library lending (Kotlin), clarity demo | Free | `examples: ECommerceExample, MinecraftExample, LibraryExample, ClarityDemoExample` | Source-only module with ~100 example tests |
| Jakarta EE / EJB app servers — zero-code tracing of an unmodified WAR via the agent (WildFly-verified) | Free | `agent: NarrativeTraceAgent` · `examples: ejb4` | Dependency-free EJB 4 WAR traced solely by `-javaagent` with the `-standalone` jar; runnable recipe and WildFly gotchas in `narrativetrace-examples/ejb4` |
| EJB `@AroundInvoke` interceptor bridge (agent-free alternative) | Planned (Free) | — | Backlogged, demand-gated — the agent path above is the shipped shape |
| Java 21+ `ScopedValue` context option | Planned (Free) | — | Backlog; `ThreadLocalNarrativeContext` works with virtual threads today |

## Read the story (outputs)

| Feature | Status | Java implementation | Notes |
|---|---|---|---|
| Indented text and Markdown renderers (wired to test output); prose renderer (library API) | Free | `core: IndentedTextRenderer, MarkdownRenderer, ProseRenderer` | Prose is not a `narrativetrace.format` option yet — API + examples only. Markdown renders parent returns inline on the entry line (no closing repeat) |
| Trace value references — content-addressed dedup of repeated captured values with readable labels (`‹Hotel›=full` on first emission, `‹Hotel›` after) | Free | `core: ValueReferenceIndex` (via `MarkdownRenderer`) | Labels from the structured value's identity field (name/id/description/…), never a redacted field; byte equality certifies sameness — any difference renders in full; containment inside other captured values counts and is replaced |
| Intra-trace value deltas — a re-capture of the same entity, changed, renders as a diff against the reference (`‹Dinner›′{amount: 100.0→92.0, currency: "USD"→"EUR"}`) | Free | `core: ValueDelta` (via `ValueReferenceIndex`, `MarkdownRenderer`) | "Same entity" is the same structured type name plus an equal identity field — the same ladder that names the label; different rendered bytes mean it changed. The diff is computed from the two structured values and names changed scalar fields only (string, integer, decimal, boolean, instant, null), never reconstructing a flat render from a structured one. Anything it cannot express — a changed nested object or list, a different field set, a value with no identity field — renders in full exactly as before, with no label stamped on a definition nothing refers back to. A changed variant that itself repeats is defined AS the diff (`‹Dinner·2›=‹Dinner›′{…}`) so it still earns a reusable label, and a folded loop iteration is named by its diff on the `×k more` line. Decimals travel as `double`: a `BigDecimal` captured as `100.00` prints `100.0` in the diff, while the reference line still shows the original text. Unlike cross-run diffs the baseline is inside the same document, so the artifact stays self-contained. Presentation-only and Markdown-only |
| Loop folding — condense repeated same-shape sibling subtrees in Markdown | Free | `core: LoopFold, StructuralTraceRenderer#subtreeKey` (via `MarkdownRenderer`) | A maximal run of ≥2 consecutive structurally identical sequential siblings renders the first iteration in full, then one `×k more: ‹Dinner›, ‹Taxi› — same flow (validate ✓ → record ✓) — 12ms total, 2–5ms each` line. "Same shape" is the value-free structural projection (item 8's oracle reused): equal signatures, child shape, and outcome kinds — a divergent call, a lone throw, or an outcome-kind mismatch renders in full outside the fold (the anomaly is the signal); a run of identically-throwing iterations still folds. Each folded iteration is named by its first distinguishing argument — as a `‹ref›′{…}` diff when the document already defines that entity, otherwise through the identity ladder (`ValueReferenceIndex`, consistent with later `‹ref›` uses) — positional `#n` when no identity field applies; durations aggregate (total + range, never per-iteration); labels cap at 6 and the flow tail at 8 with `…`. Presentation-only and Markdown-only — JSON, `.nt`, diagrams, and translated views are unchanged; concurrency (fork groups, fire-and-forget) never folds |
| Per-test trace files — full-detail Markdown with YAML frontmatter + canonical JSON + Mermaid diagram per scenario | Free | `core: TraceTestSupport, TraceFileWriter, FrontmatterBuilder` | `build/narrativetrace/traces/<Class>/<test>.md` + `.json` + `diagrams/` |
| AI-safe structural trace files — separate value-free artifact per test, plus a live structural stream | Free | `core: StructuralTraceRenderer, StructuralProjection, TraceTestSupport` · `slf4j: StructuralSubscriber` | `structural/<Class>/<scenario>.nt` per scenario — names, hierarchy, outcome kinds only; deterministic (byte-identical for identical behavior); format spec: [structural-trace-format.md](structural-trace-format.md). `narrativetrace.structuralJson=true` additionally emits `<test>.structural.json` — schema-1.2 entries with every runtime-value field elided (ADR-002 Level 1; the 1.2 additions are identity-shaped and survive the projection); `StructuralSubscriber` on the pipeline seam emits the same projection live as JSON lines on the `narrativetrace.ai.structural` logger |
| Canonical JSON export (chapter-tree schema) | Free | `core: JsonExporter, ChapterExporter, CanonicalEntry, CanonicalEntryMapper` | Per-test `.json` is wired and schema-validated against `chapter-tree.schema.json` by a conformance gate over the real writer; `narrativetrace.canonicalJson=true` additionally writes per-test `.canonical.json` (flat canonical entries — the port/conformance-fixture format). `nt.schemaVersion` is global across entries and the chapter envelope — 1.2 everywhere, stamped from `CanonicalEntry.SCHEMA_VERSION` (= 1.1 + capture-width identity fields). Chapter identity is eager: `trace_id`, `nt.storyId` and `nt.chapterId` are always written — the trace id adopted, inherited or generated (never a shared constant), the story derived from the first root-level call and the chapter equal to it — resolved once per tree so a chapter and its own entries always name the same trace. Chapter exporter is library API without a production caller yet |
| Sequence diagrams per scenario — Mermaid + PlantUML | Free | `diagrams: MermaidSequenceDiagramRenderer, PlantUmlSequenceDiagramRenderer` | Mermaid auto-emitted per scenario; PlantUML via `narrativetrace.format=plantuml` (replaces the Markdown trace) |
| Console test summaries with clarity scores + failure narratives | Free | `core: ConsoleSummaryReporter` · `output: TraceTestSupport` | On failure the report localizes change: the structural delta against last green when a baseline exists (summary + readable diff), the full trace otherwise; trace paths print as clickable `file://` links |
| Narrative delta in the test loop — one-line structural delta after every run + approval mode with committed baselines | Free | `core: StructuralDelta, ScenarioDelta, NarrativeApproval` · `junit5/junit4` · `gradle-plugin: approveNarratives` | The on-disk `.nt` is the last-green baseline — failed runs compare against it but never overwrite it. Suite footer ends with `Since last green: 4 scenarios unchanged · 1 new · 1 changed: "…" (+4 calls X.y)`. Approval mode (`narrativetrace.approval=true`; plugin DSL `approval.set(true)`, baselines default `src/test/narratives/<Class>/<scenario>.approved.nt`): a passing test whose structure differs from its committed baseline fails with a readable diff, the current structure lands beside it as `.received.nt`, and `approveNarratives` promotes reviewed files. Mechanism is Free by the 2026-08-23 tier decision; semantic diff, PR-bot review, and drift analytics are Pro |
| Flow summaries — aggregated paths + frequencies per entry point | Pro | Pro repo | Free-core boundary ArchUnit-enforced (`ArchitectureTest`) |
| Migration diffs — behavioral before/after comparison | Pro | Pro repo | |
| Runtime dependency graphs (always-called vs conditional) | Pro | Pro repo | |
| Aggregate sequence diagrams — all observed branches in one diagram, `alt/else` + frequency counts | In development (Pro) | Pro repo | |
| Aggregated activity diagrams | Planned (Pro) | — | |
| Zero-config Pro outputs — add the Pro jars and aggregated reports/diagrams appear on the next test run, no wiring | In development (Pro) | Pro repo | Via core's ServiceLoader seams (ADR-010) |

## Keep your logging stack (logging + observability)

| Feature | Status | Java implementation | Notes |
|---|---|---|---|
| SLF4J bridge — narrative events through your existing appenders, per-event-type log levels, crash-safe synchronous stream | Free | `slf4j: Slf4jTraceEventListener` · `core: DualPathPipeline` | Logger `narrativetrace`; defaults ENTRY/RETURN=TRACE, EXCEPTION=WARN |
| MDC enrichment — three-tier attribute model (resource / trace / span), persistent request-scoped fields | Free | `core: AttributeTier, SpanContext` · `servlet: NarrativeTraceFilter` | ADR-009 |
| Coexistence with hand-written logs | Free | — | Remove them at your own pace |
| Loud shedding — a capture that lost events says so in the narrative it writes | Free | `api: TraceLoss, TraceTree.loss()` · `core: LossFooter, BoundedEventBuffer, BufferedEventConsumer` | The buffered path is best-effort by design, so it is never silent: shed events are counted (ring overwrites, adaptive-drain discards and subscriber drops alike) and every format with a footer slot carries one line naming the count and `narrativetrace.buffer.capacity`. Text, prose, Markdown (blockquote + frontmatter `incomplete: true`), Mermaid and PlantUML (comment syntax). The structural `.nt` artifact deliberately does **not** — it is the approval baseline and must stay byte-identical for identical behaviour. A clean capture says nothing at all |
| OpenTelemetry span export — batch post-capture and live listener, typed attributes, business events on parent spans | Free | `opentelemetry: TraceSpanExporter, OtelTraceEventListener, SpanContextAttributeMapper` | Live listener bounds active spans with TTL |
| PII-aware MDC — identity fields hashed to searchable tokens (`@MdcField(pii = true)`) | Planned (Pro) | — | |
| Infrastructure identity — k8s / cloud / container context auto-detected as resource attributes | Planned (Pro) | — | |
| Semantic OTel keys — `@SpanAttribute("order.total_usd")`, `@SpanEvent("order.shipped")` | Planned (Pro) | — | |
| High-throughput pipeline options — LMAX Disruptor; Chronicle Queue durable/WAL mode | Planned (Pro) | — | See ADR-006 |

## Improve the code (clarity diagnostics)

| Feature | Status | Java implementation | Notes |
|---|---|---|---|
| Clarity scoring — method / class / parameter naming quality from real execution | Free | `clarity: ClarityAnalyzer` + `MethodNameScorer, ClassNameScorer, ParameterNameScorer, CohesionScorer` | [clarity-guide.md](clarity-guide.md); experimental |
| Suite-level clarity report with renaming targets + per-element notes + machine-readable results | Free | `clarity: ClarityReportRenderer, ClarityJsonExporter, ElementNoteComposer` | `clarity-report.md` + `clarity-results.json` (the contract `clarityCheck` and CI tooling consume). An **Elements** table / `elements` array gives one teaching note per element at every score (clarity is a teacher, not a judge), separate from threshold-gated issues. Results schema 1.2 adds per-scenario `elements` (1.1 added `suiteIssues`); consumers must keep accepting 1.0/1.1 files |
| Standalone scanner — score compiled classes without running tests (`clarityScan`, CLI) | Free | `clarity: ClarityScanner, ClarityScannerMain` | CLI `--format markdown\|json\|both` |
| Project vocabulary in scoring — the committed glossary extends the built-in dictionaries | Free | `clarity: DomainVocabulary, ProjectVocabularySource` · `glossary: GlossaryVocabulary, GlossaryAwareClarityScannerMain` · `junit5/junit4/gradle-plugin` | One file, one review workflow: verbs of the committed `glossary.json` score as domain verbs, its nouns as domain tokens, and its root-level `abbreviations` section (schema 2) declares accepted shorthand — a token merely appearing inside a committed phrase does not qualify. A listed abbreviation is accepted *and* spelled out from its declared expansion. Reading is unconditional (unlike harvesting); only the *committed* file counts, so a run cannot expand its own vocabulary. Built-in tiers keep authority — generic verbs, boolean prefixes, meaningless placeholders, deprecated synonyms and `stale` terms are never promoted |
| CI gate (`clarityCheck`) | Free | `gradle-plugin: ClarityCheckTask` | Joins `check` and fails the build by default; set `warnOnly` for advisory mode |
| Historical clarity trending | Planned (Pro) | — | |
| AI-assisted renaming with trace evidence | Planned (Pro) | — | |
| Dead-code and untested-branch detection from traces | Planned (Pro) | — | |
| Log-redundancy analysis — which hand-written logs the narrative makes unnecessary, with token savings | Planned (Pro) | — | |

## Speak the domain language (glossary & translation)

| Feature | Status | Java implementation | Notes |
|---|---|---|---|
| Domain glossary — single per-repo ubiquitous-language file (canonical JSON + Markdown view), additively harvested from test-time traces | Free | `glossary: Glossary, GlossaryJsonWriter, GlossaryJsonReader, GlossaryMarkdownRenderer, GlossaryHarvester, GlossaryMerger, GlossarySuiteHarvest` · `junit5: NarrativeTraceExtension` | Opt-in: `narrativetrace.glossary=true` (`narrativeTrace { glossary.set(true) }`), since it writes outside the build directory |
| Glossary harvesting from compiled classes — `glossaryScan`, the only mode that harvests `@Narrated`/`@OnError` templates | Free | `glossary: GlossaryStaticScanner, GlossaryScannerMain` · `gradle-plugin: NarrativeTracePlugin` | Static-only by design: a captured trace carries narration with runtime values already interpolated |
| Canonical terms + deprecated synonyms — non-canonical usage flagged in run output and suppressed from harvest | Free | `glossary: AliasIndex, VocabularyViolations, RenameSuggester, VocabularySummaryFormatter, NonCanonicalTermIssues, GlossaryUsageReport` | One term per concept. Violations reach the console, `glossary-usage.json`, and `clarity-report.md` ("Suite Issues") / `clarity-results.json` (`suiteIssues`, schema 1.1); the `clarityCheck` gate is advisory by default, hard-fail via `clarity.maxSuiteIssues`. The vocabulary check fires only when a committed `glossary.json` exists before the run |
| Bounded contexts — vocabulary scoped by package-mapped contexts | Free | `glossary: BoundedContext, ContextResolver, TermNormalizer` | Same term may differ per context; delimiter-aware longest-prefix package mapping, `_unassigned` fallback. `TermNormalizer` rules (s-final keep-list, stable-stem, idempotence) are term identity in persisted glossaries — every NarrativeTrace runtime adopts them verbatim |
| Trace translation views — per-trace Markdown files rendered live from the event pipeline + glossary | Free | `glossary: TraceTranslationView, TranslationSubscriber, GlossaryTranslator, GlossaryLoader` | Values never translated; one `<traceId>.md` per trace, footer lists glossary gaps |
| Live translated stream — locale-suffixed SLF4J loggers, appender-routable to same or separate destination | Free | `glossary: TranslationSubscriber` | Best-effort path; canonical stream untouched |
| AI-assisted glossary translation & definition generation | Planned (Pro) | — | Glossary terms only; explicit opt-in |
| Cross-context term-bleed diagnostics | Planned (Pro) | — | |

## Let AI agents see runtime truth (AI integration)

| Feature | Status | Java implementation | Notes |
|---|---|---|---|
| AI-safe structural traces — no runtime values, zero injection surface | Free | `core: StructuralTraceRenderer, StructuralProjection` | Shipped: the distinct `.nt` artifact (ADR-002 complete) plus the flagged `.structural.json` projection, its safety pinned by a property test; SUMMARY/NARRATIVE value suppression additionally exists in the human-facing output |
| LLM-oriented docs (`llms.txt`, `llms-full.md`) | Free | `documentation/llms.txt, llms-full.md` | |
| MCP analysis tools — execution traces, dependency graph, branching, renaming suggestions, clarity report, capture level, trace comparison | Pro | Pro repo | Available as a library today |
| MCP server (stdio transport — connect Claude Code / Cursor directly) | In development (Pro) | Pro repo | |
| Runtime intelligence tools — performance profile, architecture health, test-gap analysis, behavioral contracts, refactoring impact | Planned (Pro) | — | |
| Pseudonymized AI output (Level 2) — synthetic tokens, data-flow preserved, real values destroyed | Planned (Pro) | — | |
| Selective / full-detail AI output (Levels 3–4) with `@UntrustedInput` sanitization | Planned (Pro) | — | |

## Prove what happened (audit & compliance — Pro)

| Feature | Status | Java implementation | Notes |
|---|---|---|---|
| Audit & SecOps annotations — `@AuditEvent`, `@SecurityEvent`, `@AuditActor`, `@AuditEntityId`, `@AuditField` | Pro | Pro repo | |
| Deterministic inference — action, actor, entity, outcome resolution | Pro | Pro repo | |
| Policy engine — `AUDIT_RELAXED` / `AUDIT_STRICT` / `SECOPS_STRICT`, classification filtering | Pro | Pro repo | |
| Field masking — `LAST4`, `REDACT`, `HASH` | Pro | Pro repo | |
| Governance checker — build-time annotation compliance validation | Pro | Pro repo | |
| Structured JSON events with schema version + trace correlation | Pro | Pro repo | |
| Durable delivery — synchronous logging-path sink, audit/secops routing | In development (Pro) | Pro repo | See ADR-005 for why this path |
| Spring interception for audit annotations | In development (Pro) | Pro repo | |
| Governance Gradle task + CI action | In development (Pro) | Pro repo | |
| Control traceability — `controls={"AU-05"}`, control registry, cross-framework mapping (PCI-DSS / SOC 2 / NIST / ISO), coverage reports | In development (Pro) | Pro repo | |
| Compliance artifacts — governance report export, audit event schema, evidence per release | In development (Pro) | Pro repo | |
| Compliance-standard policy factories, PAN masking (`FIRST6_LAST4`), source IP | Planned (Pro) | — | |
| Sink adapters — Pangea, WorkOS, SIEM/HEC | Planned (Pro) | — | |
| Tamper-evidence hash chaining + verifier | Planned (Pro) | — | |
| GDPR actor pseudonymization (right-to-erasure) | Planned (Pro) | — | |
| OCSF export for SIEM interoperability | Planned (Pro) | — | |

---

## Keeping this guide honest

This guide exists so no feature gets lost between code, plans, and
vision documents — and so nothing reads as shipped when it isn't. Rules:

1. Every user-visible feature appears here, exactly once, with a status.
2. A feature moves to **Free**/**Pro** only when it is merged, tested,
   and documented. "In development" means the design is settled and work
   is scheduled; "Planned" means specified only.
3. Changes that add or promote a feature must update this file in the
   same commit.
4. **The code decides.** Every shipped Free row cites the Java classes
   that implement it (`module: main classes`). When the guide and the
   code disagree, the code is right and the guide is the bug — fix the
   row, and record the audit date in the header.
5. **One catalog, never a per-platform copy.** This guide says what the
   product is; each platform records its own mechanisms in its own
   repository. Per-platform shipped/pending status belongs in the
   maintainers' status matrix, a private working record — not in copies
   of this catalog.
