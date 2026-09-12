# Privacy and redaction

This library runs inside your process and writes files your team will
share — CI artifacts, committed baselines, production log lines. This page
is the row-by-row version of that contract: what redacts, where it does and
does not reach, and what NarrativeTrace guarantees versus what it does not
claim at all.

**Test-time trace output writes by default** — the JUnit 5 extension and the
JUnit 4 integration write each test's `.md`/`.json`/`.mmd` artifacts to the
ephemeral `build/narrativetrace` (gitignored, regenerated every run) with no
configuration needed; `narrativetrace.output=false` opts out *(since 0.2.2, unreleased)*. That default
does not change what gets redacted or how — every artifact still goes
through the same `ValueRenderer` and the same deny-list described below,
whether writing was on by default or turned on explicitly. See the
[Configuration Guide](configuration-guide.md) for every key, and
[What to Commit](what-to-commit.md) for why none of it belongs in source
control.

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
2. **The name-based deny-list** (`RedactionPolicy.DEFAULT`) — matches
   **parameter names, field names and record-component names** against a
   built-in, multilingual set (`password`, `token`, `cvv`, `ssn`, `secret`,
   `authorization`, `cardNumber`, and their Spanish, Portuguese, French,
   German and Chinese equivalents, among others), plus a second independent
   check on the *shape* of the value itself (a JWT, a Luhn-valid card
   number, a `Set-Cookie` string, a national identity number that passes its
   own checksum, a dashed US Social Security number) so a bearer token
   passed under an unrecognized name is still caught.

   Parameter names were added on 2026-09-10. Until then this axis reached
   fields and record components only, so a method taking `String password`
   printed it in full unless the parameter carried `@NotTraced` — while the
   README stated the opposite. The decision now happens at **capture**, in
   the per-method metadata both the proxy and the agent already cache, which
   has two consequences worth knowing: the lookup costs nothing per traced
   call, and a denied value never enters the `TraceEvent` at all, so it
   cannot reach the audit path, the buffered consumer or a listener attached
   through the pipeline SPI. It is never rendered and then replaced — a
   secret formatted and discarded still existed as a string.

### A type's own `toString()` is never trusted while the type has state

This is the invariant the rest of the page rests on, and it is worth stating
plainly:

> **A class or record that declares instance fields is walked field by field,
> at every depth, with both redaction mechanisms consulted per field — whatever
> its own `toString()` would have printed.**

Exactly two kinds of value keep their own text. One is a class with **no
instance fields at all**: there is nothing to hide and nothing to walk. The
other is a class **the platform defines** — `LocalDate`, `Duration`, `UUID`,
`URI` and their kind — whose `toString()` is the JDK's format rather than
application code, and which cannot declare one of your fields in the first
place. A class *your* code declares is application code whatever it extends.

The single opt-in back to curated rendering is **`@NarrativeSummary`**: a
zero-argument method you wrote *for* the trace, so its output is your choice.
Even that is not trusted verbatim — its text passes the value-shape check, the
control-character escape and the length cap, exactly as a `String` parameter
would, so a summary that interpolates a bearer token still renders
`[REDACTED]`.

Until 2026-09-11 the rule ran the opposite way: any `toString()` was preferred
over introspection unless the class declared a `@NotTraced` field. That check
consulted neither the name deny-list nor the types of the fields, which left
two channels open. A plain `Login { username, password }` with a hand-written
`toString()` printed the password **at depth zero** — no nesting, no wrapper,
no annotation involved. And a curated `toString()` on any outer class printed
nested `@NotTraced` values straight through ordinary Java stringification,
because the outer class itself declared nothing sensitive. Walking instead also
puts the depth cap and the cycle guard back in front of every value: your
`toString()` used to run outside both.

**The cost is real and was accepted.** A value class with a pleasant
`toString()` and no `@NarrativeSummary` now renders as a field dump —
`Amount{currency: "EUR", units: 10}` rather than `EUR 10.00`. Uglier, and
correct. Add `@NarrativeSummary` to the types where the reading matters.

A **parameter** is settled earlier still, and the difference follows from where
the decision is made. A parameter whose *name* the deny-list denies is decided
at capture, before the argument is handed to any renderer — so nothing on the
value is ever called. The distinction is not inconsistency: a field name is
discovered *by* introspection, while a parameter name is known from the method
signature before the value is touched at all.

Redaction survives **every wrapper, at any depth** — `Optional`, `Future`,
`AtomicReference`, `AtomicReferenceArray` and a standalone `Map.Entry` are
opened rather than rendered via their own `toString()`, and what they hold is
rendered under exactly these rules, which then applies again to whatever *that*
holds. A `Map` **key** is walked the same way, so a composite used as a key
cannot carry a field out through the one place stringification is hardest to
avoid. And redaction **wins over a narration template that names it**:
`{param.property}` in `@Narrated`/`@OnError` resolves a path to a redacted
member as `[REDACTED]`, at every depth along the path, never the literal value.

### When rendering a part fails

A value whose `toString()`, `@NarrativeSummary`, getter or accessor throws
costs only its own slot: that part renders as `<error: IllegalStateException>`
— the exception's **type name and nothing else** — and the rest of the value
renders whole. The message is deliberately excluded. An exception message
routinely interpolates the very value that failed to format
(`"cannot render " + password`), so a marker carrying it would turn the
renderer's own failure path into a leak.

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
  to apply. Its `scenario:` header is covered by that: one invocation of a
  `@ParameterizedTest` is titled `<method> #<index>`, never the display
  name a `name = "…"` template interpolated its arguments into *(since 0.2.2, unreleased)*. What the
  artifact is *called* is a different question — see the non-guarantee
  below.
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
- **No redaction of test *names*.** A test's display name is
  developer-authored text, and a `@ParameterizedTest(name = "find {0}")`
  template interpolates its arguments into it. That name reaches the
  artifact *filename* (`equipment_can_be_found-002-find_tent.md` — the
  slugged label is what tells two invocations apart on disk), the run's
  `manifest.json`, and the heading of the value-carrying artifacts. No
  deny-list is consulted for any of them: a name is an identifier here,
  not a captured value. Keep secrets out of display-name templates —
  the value-free `.nt` header is the one place this is handled for you,
  by not using the display name at all.
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
