# API surface — the decision record


`narrativetrace-api` is the jar third parties compile against: the annotations
they write, the event model they read, and the SPIs they implement. It is
published under the Apache License 2.0 and it carries **zero dependencies**, so
that a consumer of the contract never inherits a dependency from it.

This file is that contract, written down. Every public top-level type in the jar
has a row here; nothing else may be in the jar. A test in the module
(`ApiSurfaceTest`) compares the two and fails when they disagree — a type added
to the jar without a row is a type nobody decided to support.

## The admission rule

A type belongs in `narrativetrace-api` when **code outside this repository has
to name it** in order to use NarrativeTrace:

- an annotation a user writes in their own source;
- a type that appears in the signature of an SPI a third party implements;
- a type such an SPI hands them, transitively, so they cannot avoid naming it.

Everything else stays in the runtime module, where it can change. A borderline
type stays out: admitting it later costs one commit, and withdrawing it costs a
major version.

## Deferred, deliberately

| Type | Date | Why it stayed in core |
|---|---|---|
| `NarrativeContext` | 2026-08-30 | It is the runtime contract, not the compile-time one: it imports `SpanIdGenerator` and `TraceLoss`, and customer code reaches it through the proxy, Spring and agent modules rather than by implementing it. Programmatic callers compile against the runtime jar, which is a permitted use of it. |
| `EventPipeline`, `EventPipelineFactory` | 2026-08-30 | Pipeline topology is a runtime decision. The alternative strategies that implement it ship in the same tree as the default one, so nothing outside this repository implements it today. Admitting it later is one commit; withdrawing it is a major version. |
| `ExtensionRegistry` | 2026-08-30 | *How* extensions are discovered is a runtime mechanism; *what* they implement is the contract, and only the latter is admitted. It also reads `ConfigResolver`, which is core. |
| `ai.narrativetrace.micronaut.http.RequestContextProvider` | 2026-08-30 | Kept as its own Kotlin type. It is typed on Micronaut's `HttpRequest`, and its `UserContext` is a Kotlin data class; folding it into the generic API type would change the Micronaut integration's public shape for no gain to a third party. Revisit if a second reactive integration appears. |

## Admitted types

One row per public top-level type in the jar. Nested types (`TraceEvent`'s
variants, `SpanContext.Builder`, `RequestContextProvider.UserContext`, …) are
admitted with their enclosing type and are not listed separately.

| Type | Admitted | Reason |
|---|---|---|
| `ai.narrativetrace.api.annotation.Narrated` | 2026-08-30 | Users write it on their own methods; it is the primary authoring surface. |
| `ai.narrativetrace.api.annotation.NarrativeSummary` | 2026-08-30 | Same: authored in user code, read by the proxy and the agent. |
| `ai.narrativetrace.api.annotation.NotTraced` | 2026-08-30 | Same, and the redaction contract depends on it being namable everywhere. |
| `ai.narrativetrace.api.annotation.OnError` | 2026-08-30 | Same: authored in user code. |
| `ai.narrativetrace.api.annotation.OnErrors` | 2026-08-30 | The repeatable container for OnError; inseparable from it. |
| `ai.narrativetrace.api.config.TracingLevel` | 2026-08-30 | The vocabulary a caller names when it sets a level. Resolution stays in core. |
| `ai.narrativetrace.api.event.AttributeTier` | 2026-08-30 | Names the resource/trace/span tiers an exporter must respect. |
| `ai.narrativetrace.api.event.ClientIp` | 2026-08-30 | Micro-typed span field; an exporter reads it. |
| `ai.narrativetrace.api.event.ConcurrencyInfo` | 2026-08-30 | Carried on every node an exporter or renderer walks. |
| `ai.narrativetrace.api.event.ConcurrencyKind` | 2026-08-30 | The enum ConcurrencyInfo is typed on. |
| `ai.narrativetrace.api.event.EnduserId` | 2026-08-30 | Micro-typed span field; a RequestContextProvider produces the value behind it. |
| `ai.narrativetrace.api.event.HttpRoute` | 2026-08-30 | Micro-typed span field, set by every HTTP integration. |
| `ai.narrativetrace.api.event.MethodSignature` | 2026-08-30 | The unit of capture; every listener and renderer reads it. |
| `ai.narrativetrace.api.event.ParameterCapture` | 2026-08-30 | Part of MethodSignature; cannot be admitted separately. |
| `ai.narrativetrace.api.event.RenderedValue` | 2026-08-30 | The structured value channel a typed exporter consumes. |
| `ai.narrativetrace.api.event.ResourceIdentity` | 2026-08-30 | Resource-tier span fields; OTel export maps them directly. |
| `ai.narrativetrace.api.event.ServiceIdentity` | 2026-08-30 | Trace-scoped identity an exporter groups on. |
| `ai.narrativetrace.api.event.SessionId` | 2026-08-30 | Micro-typed span field. |
| `ai.narrativetrace.api.event.SourceLocation` | 2026-08-30 | Capture-width identity field carried on nodes. |
| `ai.narrativetrace.api.event.SpanContext` | 2026-08-30 | The interoperability boundary with OpenTelemetry and every other exporter. |
| `ai.narrativetrace.api.event.SpanContextFields` | 2026-08-30 | Declares which tier each SpanContext field belongs to; exporters branch on it. |
| `ai.narrativetrace.api.event.SpanId` | 2026-08-30 | Returned by enterMethod and passed back on exit; unavoidable in any capture caller. |
| `ai.narrativetrace.api.event.SpanIdGenerator` | 2026-08-30 | A third-party context implementation needs W3C-valid ids, and reimplementing them invites an all-zero id. |
| `ai.narrativetrace.api.event.TenantId` | 2026-08-30 | Micro-typed span field. |
| `ai.narrativetrace.api.event.ThreadInfo` | 2026-08-30 | Thread-tier fields on every event. |
| `ai.narrativetrace.api.event.TraceAnchor` | 2026-08-30 | Wall-clock anchor an exporter needs to map timestamps. |
| `ai.narrativetrace.api.event.TraceEvent` | 2026-08-30 | The sealed event model a TraceEventListener switches over. |
| `ai.narrativetrace.api.event.TraceId` | 2026-08-30 | Trace identity; MDC bridges and exporters both read it. |
| `ai.narrativetrace.api.event.TraceLoss` | 2026-08-30 | What a trace is missing; a caller reads it around a scenario. |
| `ai.narrativetrace.api.event.TraceNode` | 2026-08-30 | The node type of every tree a renderer or exporter walks. |
| `ai.narrativetrace.api.event.TraceOutcome` | 2026-08-30 | Sealed Returned/Threw on every node. |
| `ai.narrativetrace.api.event.Traceparent` | 2026-08-30 | The cross-process wire format; an HTTP client on either side needs it. |
| `ai.narrativetrace.api.export.RequestContext` | 2026-08-30 | Passed to every TraceExporter call. |
| `ai.narrativetrace.api.export.RequestContextProvider` | 2026-08-30 | Third-party identity resolvers are expected. Generic over the request type so the jar stays dependency-free. |
| `ai.narrativetrace.api.export.TraceExporter` | 2026-08-30 | The sink contract a third party implements. |
| `ai.narrativetrace.api.render.NarrativeRenderer` | 2026-08-30 | The rendering contract a third party implements. |
| `ai.narrativetrace.api.spi.NamedTrace` | 2026-08-30 | Passed to ReportContributor; part of that contract. |
| `ai.narrativetrace.api.spi.ReportContributor` | 2026-08-30 | Discovered by ServiceLoader from outside this repository. |
| `ai.narrativetrace.api.spi.TraceEventListener` | 2026-08-30 | Discovered by ServiceLoader from outside this repository. |
| `ai.narrativetrace.api.tree.TraceTree` | 2026-08-30 | What every renderer, exporter and analysis consumes. Gained `traceId()` on 2026-08-30 — a `default` method returning `null`, so third-party implementations keep compiling — because an exporter has to be able to name the trace a capture belongs to even when no node in it kept a span context. |

## Not admitted, and why it matters

`HexValidator` lives in `ai.narrativetrace.api.event` but is package-private: it
is how an admitted type keeps its promise, not a promise itself. Package-private
types are not part of the surface and have no row here.

`TraceNamer` sat beside it until 2026-09-04, reached through a
`TraceId.humanName()` that no longer exists. Three word tables and a bit layout
are implementation, and this jar is irreversibly Apache-licensed — whatever is
left in it is given away for good — so it moved to
`ai.narrativetrace.core.render`, where every one of its callers already lived.
The type was package-private, so nothing outside this repository could name it;
`humanName()` was not, and the callers that used it now ask
`TraceNamer.name(traceId.value())` directly. The words a trace id maps to are
unchanged, which matters because every port derives the same three from the same
id.
