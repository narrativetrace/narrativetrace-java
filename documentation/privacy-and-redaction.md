# Privacy and redaction

This library runs inside your process and writes files your team will
share — CI artifacts, committed baselines, production log lines. This page
is the row-by-row version of that contract: what redacts, where it does and
does not reach, and what NarrativeTrace guarantees versus what it does not
claim at all.

## Redaction, surface by surface

| Surface | Can disable built-in redaction? |
|---|---|
| Standard JUnit output (proxy, agent, Spring, Micronaut, servlet, SLF4J) | No |
| Agent narration | No |
| Custom `ValueRenderer` your own code constructs | Yes — only by passing `RedactionPolicy.DISABLED` to the constructor explicitly |
| `@NotTraced` | Not applicable — it is the thing doing the redacting, and it always wins |
| Structural `.nt` | Not applicable — it carries no values to redact in the first place |

Verified against the code, not inferred from the docs: every shipped
integration — `NarrativeTraceProxy` and `AgentRuntime` (the two places
capture actually happens), and every framework module built on top of
them — constructs its `ValueRenderer` the same way, as a `private static
final` field with no injection point:

```java
private static final ValueRenderer VALUE_RENDERER = new ValueRenderer();
```

`new ValueRenderer()` defaults to `RedactionPolicy.DEFAULT`. There is no
configuration flag, system property, or plugin DSL knob that reaches that
constant — the only way to get `RedactionPolicy.DISABLED` is application
code that builds its own `ValueRenderer` instance directly, bypassing every
shipped integration path. That is a deliberate, reviewable act in your own
source, not a configuration state a deploy can silently flip.

## What the deny-list catches, and what outranks it

Two independent redaction mechanisms apply to every reflectively-rendered
value:

1. **`@NotTraced`** on a parameter, field, or record component — always
   redacts, unconditionally, everywhere.
2. **The name-based deny-list** (`RedactionPolicy.DEFAULT`) — matches field
   names against a built-in, multilingual set (`password`, `token`, `cvv`,
   `ssn`, `secret`, `authorization`, `cardNumber`, and their Spanish,
   Portuguese, French and Chinese equivalents, among others), plus a second
   independent check on the *shape* of the value itself (a JWT, a
   Luhn-valid card number, a `Set-Cookie` string) so a bearer token passed
   under an unrecognized name is still caught.

A **curated `toString()`** is normally preferred over reflective
introspection — but a class that declares a `@NotTraced` field is
introspected anyway, so the annotation is honored instead of whatever that
`toString()` would have printed. The name-based deny-list does *not* get the
same override power: it only applies when NarrativeTrace is already
introspecting fields, so a class with its own `toString()` and no
`@NotTraced` member is trusted as written. Only the explicit annotation
outranks a curated `toString()`.

Redaction also **survives one container deep** — `Optional`, `Future`,
`AtomicReference`, `AtomicReferenceArray` and a standalone `Map.Entry` are
opened rather than rendered via their own `toString()`, so a redacted value
inside one of them stays redacted instead of leaking through the wrapper.
And it **wins over a narration template that names it**: `{param.property}`
in `@Narrated`/`@OnError` resolves a path to a redacted member as
`[REDACTED]`, at every depth along the path, never the literal value.

Full detail and worked examples: [Annotations Guide](annotations-guide.md).

## Guarantees

- **Tracing failures are isolated from host execution.** Recording is
  exception-isolated on every path; both pipeline consumers swallow their
  own errors. A throwing `toString()`, a full buffer, or a broken appender
  never changes what your method returns or throws.
- **Standard outputs honor redaction.** See the table above — no shipped
  integration exposes a way around it.
- **The structural `.nt` artifact has no runtime values at all.** Names,
  call hierarchy and outcome kinds only — zero prompt-injection surface,
  and that is a property test (ADR-002), not a policy someone could forget
  to apply.
- **The buffered analysis path may shed events, but it always reports
  loss.** It never blocks the caller and never grows past its bound; a
  capture that lost events prints the count in its own footer rather than
  silently under-reporting.

## Non-guarantees

- **No "zero overhead" claim.** Tracing does work, and work costs
  something — see the [README's Performance section](../README.md#performance)
  for the measured numbers.
- **No private-method tracing.** Both capture paths see only interface
  methods (proxy) or non-private methods (agent); a private method's
  branch is inferred from which of *its* calls show up in the trace.
- **No automatic tracing of a bean with no interface, on any proxy-based
  path.** Proxy, Spring and Micronaut all wrap through a JDK dynamic
  proxy, and a class with no interface is left untouched — silently, not
  as an error. The Java agent is the path that does not have this limit.
- **No agent exclude list yet.** `AgentConfig` parses exactly `packages`,
  `loggerName`, `level` and `loggingJars` — inclusion only, nothing to
  carve an exception out of an included package.
- **No Android or GraalVM native-image support today.** Full detail,
  including *why* Android is a "no" rather than a "not yet," in
  [Installation Guide § Compatibility](installation-guide.md#compatibility).

## The production loss model, visually

Every event is written synchronously to the durable log stream and
*additionally* published to a best-effort, bounded in-memory buffer — the
two halves of the dual-path pipeline carry opposite obligations by design:

```text
synchronous log path (SLF4J, inline on the caller thread)
   must not fail the host application
   must not be starved by the analysis path failing
   durable — this is the record

buffered analysis path (bounded ring, drained into the retained store)
   may shed events under load, and always says so
   must never block the caller
   must never grow past its bound (65,536 slots by default)
   best-effort — this is analysis, not audit
```

Losing the buffer loses analysis fidelity for that run. Losing the log
stream loses the record. That asymmetry is why they are two paths with two
different failure behaviors, rather than one path with a single trade-off.

## What this page does not cover

Where NarrativeTrace sits at each stage of your pipeline — development,
CI/acceptance, production — is the [Lifecycle Guide](lifecycle-guide.md).
What happens when it stacks with AOP proxies, contract libraries, or another
agent wrapping the same classes — nesting order can change how a trace
*reads*, never what a method returns or throws, because recording never
replaces an outcome — is [Choosing an Integration § Stacking with other
wrappers](choosing-an-integration.md#stacking-with-other-wrappers).
