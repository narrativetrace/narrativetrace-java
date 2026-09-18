# FAQ

Short answers to the questions that come up once someone has NarrativeTrace running.
See the [Configuration Guide](configuration-guide.md) for the full reference behind
each answer.

## I set the tracing level to Detail but nothing shows up in my logs. Or: I set the logger to WARN and the trace still appears in my test output. Which setting wins?

Both, because they answer different questions. NarrativeTrace has two dials, and its
pipeline has two paths.

**Dial 1, `TracingLevel`, decides what is captured.** `OFF`, `ERRORS`, `SUMMARY`,
`NARRATIVE`, `DETAIL` — increasing verbosity. It is NarrativeTrace's own setting and
it sits at the front: a call `TracingLevel` filters out never becomes an event, for
any consumer, and no other setting can bring it back.

This dial is also the only one that changes the cost of tracing, but not as a clean
on/off below `DETAIL`: only `OFF` skips interception entirely — the JDK proxy's
`isActive()` gate returns before any serialization, event creation, or pipeline
publish happens. `ERRORS`, `SUMMARY` and `NARRATIVE` still intercept every call and
publish an entry/exit event for it; they cost less than `DETAIL` only because
parameter values are not rendered (`ParameterNameResolver` captures them as empty
strings instead of calling the value renderer), not because the call goes
unintercepted. The pruning that gives `ERRORS` and `SUMMARY` their smaller trees —
dropping non-error paths, dropping intermediate frames — happens afterward, in
`TraceTreeBuilder`, when a tree is built from the events already captured. So: `OFF`
skips capture entirely; the lower levels record less, and cost less than `DETAIL`,
but they do not skip interception the way `OFF` does.

**Dial 2, your SLF4J logger's level, decides what is printed.** NarrativeTrace writes
every captured event to the `narrativetrace` logger (or whatever name
`narrativetrace.loggerName` routes to) as a log line, at a level per kind of line:
an entry and a return at `TRACE`, an exception at `WARN`, by default. Your logger's
threshold then does what it always does — raising it silences lines. It never
captures more, and it never captures less: an event the synchronous path can't print
because the logger is too quiet was still captured, and still reaches the buffered
path (`captureTrace()`, trace files, exporters) whole.

**Now the two paths, which is where the confusion comes from.** Every event
`TracingLevel` admits travels down both paths of the default `DualPathPipeline` at
once:

- The **synchronous path** runs `Slf4jTraceEventListener` inline, before the traced
  method continues. The log line is written before the call returns, so it survives
  a crash. This is the only path your logger's level affects — and it logs one line
  per event it receives, whether or not that event later survives tree pruning.
- The **buffered path** feeds everything else: the trace files written after a test,
  `captureTrace()`, approval traces, the clarity report, the OpenTelemetry export.
  This path never consults your logger. It sees every event `TracingLevel` admitted,
  whatever the logger threshold says, and it is where `ERRORS`/`SUMMARY` pruning
  actually happens — after capture, not instead of it.

So a logger at `WARN` and a `TracingLevel` at `DETAIL` gives you quiet logs and a
complete trace file. A `TracingLevel` at `SUMMARY` and a logger at `TRACE` gives you
a loud log of every call, but a trace file pruned to roots and leaves. A
`TracingLevel` at `OFF` gives you nothing anywhere, because nothing was captured.

**Where each dial lives.**

| Dial | Lives in |
|---|---|
| Tracing level | The `narrativetrace.level` system property or `narrativetrace.properties` entry, or `new NarrativeTraceConfig(TracingLevel...)` / `config.setLevel(...)` in code |
| Logger threshold | Your Logback/Log4j configuration for the `narrativetrace` logger (or the name `narrativetrace.loggerName` routes to) |
| Level per kind of line | `Slf4jTraceEventListener`'s `Map<EventType, Level>` constructor argument — entry/return default `TRACE`, exception defaults `WARN` |

**Rules of thumb.** To reduce log volume, raise the logger threshold; the trace file
is untouched. To reduce the size of the trace, lower the tracing level. To reduce
overhead, lower the tracing level to `OFF`; the logger threshold changes nothing
about capture cost. To keep tracing on in production but out of the logs, leave the
tracing level at `SUMMARY` and the logger at `WARN`: the synchronous path stays quiet
and the buffered path still feeds your exports.

See [Two dials, two paths](configuration-guide.md#8-two-dials-two-paths) in the
Configuration Guide for the worked combination table, and
[The Dual-Path Event Pipeline](dual-path-pipeline.md) for the pipeline's own contract.
