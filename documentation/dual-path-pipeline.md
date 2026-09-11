# The Dual-Path Event Pipeline

[English](dual-path-pipeline.md) | [Español](es/canalizacion-de-doble-ruta.md) | [Português](pt-BR/pipeline-de-caminho-duplo.md) | [简体中文](zh-CN/双路径事件管道.md)

Every traced call publishes its events exactly once, through one
pipeline. The default pipeline — `DualPathPipeline`, in every
distribution — delivers each event twice, along two paths with
deliberately opposite guarantees. This page is the contract for both:
what each path promises, what each one costs, and what happens under
load and after a crash.

## The shape

```
 traced method (caller thread)
      │
      ▼
 capture: values rendered and REDACTED here, once   ← before anything else sees them
      │
      ▼
 publish (one TraceEvent)
      ├──────────────► synchronous path: SLF4J narration, inline,
      │                write completes before the method returns
      └──────────────► buffered path: bounded ring → event store,
                       trace tree, exporters, SPI listeners
```

One event, two deliveries. Each path is isolated from the other and
from you: an exception thrown by either consumer is contained and never
becomes an application failure.

## The synchronous path — narration that survives a crash

The SLF4J listener runs inline on the calling thread. The log write
completes before the traced method returns to its caller, so the
narration is exactly as durable — and costs exactly what — a log call
you would have written by hand. If the process dies on the next
instruction, everything narrated up to that point is already in your
log stream.

Per-event-type levels are configurable; the defaults are
`ENTRY`/`RETURN` at TRACE and `EXCEPTION` at WARN, on the
`narrativetrace` logger. Formatting, appenders, and any locking belong
to your logging backend — the listener itself keeps no shared mutable
state beyond per-thread context.

## The buffered path — capture that never blocks

The buffered path backs `captureTrace()` and everything built on it:
the trace tree, exporters, and listeners attached through the SPI. Its
carrier is a bounded ring buffer — 65,536 slots by default
(`narrativetrace.buffer.capacity`), roughly 1.75 MB, budgeted at about
300 bytes per event at saturation — drained by a single consumer
thread. It never grows, and it never blocks the calling thread.

Under pressure it sheds load instead of applying backpressure:

| Fill | Behavior |
|---|---|
| up to 70% | full processing — store, tree, subscribers |
| 70–90% | shed: events are drained and discarded in batches |
| above 90% | emergency: everything is discarded until pressure clears |

The ring itself overwrites its oldest slot rather than growing, so a
producer can always write. Losing events under saturation is the
intended behavior on this path — the alternative is your request
threads waiting on observability.

## Loss is counted, never silent

Every dropped event is counted — ring overwrites, shed batches, and
subscriber backpressure drops alike — and summed in
`BufferedEventConsumer.droppedCount()`. The trace's own footer reports
the loss, and is omitted only when nothing was lost: a short trace is
never silently indistinguishable from a quiet one.

## What a crash costs

The two paths answer the crash question differently, on purpose:

- **Synchronous path:** nothing already narrated is lost. The write
  happened before the method continued.
- **Buffered path:** the in-flight trace tree is best-effort. Events
  still in the ring at the moment of a crash are gone, and that is the
  documented trade for never blocking a caller.

## Redaction happens before the fork

Parameter values are rendered — and redacted — once, at capture, before
the event is published. A value denied by name, annotation, or its own
shape never enters the event at all, so neither path, no exporter, and
no SPI listener can ever see it. The full contract, surface by surface,
is in [Privacy and Redaction](privacy-and-redaction.md).

## Where consumers attach

Two seams, both public API:

- **`TraceEventListener`** — per-event, discovered via `ServiceLoader`,
  delivered on the buffered path's consumer thread. A listener that
  throws is reported once and disabled for the rest of the JVM's life;
  a misbehaving consumer never takes the pipeline down with it.
- **`TraceExporter`** — per completed trace, at a request boundary
  (the servlet filter calls it with the finished tree).

## Configuration

| Key | Effect |
|---|---|
| `narrativetrace.pipeline` | Selects a registered pipeline topology by name; unset builds the default dual-path topology described here |
| `narrativetrace.buffer.capacity` | Ring size for the buffered path |
| `narrativetrace.narration` | `off` vetoes the SLF4J narration listener |

The pipeline is a component behind one interface: this page documents
the default topology, and everything above — capture-time redaction,
counted loss, consumer isolation — holds regardless of which topology a
deployment selects. All keys are read once at startup; see the
[Configuration Guide](configuration-guide.md) for every configuration
surface.
