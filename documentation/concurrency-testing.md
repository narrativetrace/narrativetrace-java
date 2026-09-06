# Concurrency stress testing

The dual-path event pipeline is the part of NarrativeTrace that runs inside
someone else's threads. Every traced call publishes once, synchronously to the
durable log path and into a bounded ring for the analysis path; a drain empties
that ring into the store that `captureTrace()` reads; and worker threads hand
their spans to the thread that will do the capturing. All of it is lock-free or
nearly so, all of it runs under whatever contention the application happens to
have, and none of its failure modes announce themselves.

That last point is why this suite exists. A concurrency defect here does not
throw. It produces a trace that is quietly one call short, or one call too many,
or a loss counter that disagrees with the loss. A reader cannot tell any of
those from a run where nothing happened.

This document describes the suite that attacks those paths on purpose:
`narrativetrace-jcstress`, built on [OpenJDK
jcstress](https://openjdk.org/projects/code-tools/jcstress/).

## What jcstress does

A jcstress scenario is a small state object, two to four *actors* that run
concurrently against it, and an *arbiter* that runs afterwards and records what
happened into a result. The harness runs each scenario billions of times across
every JVM configuration it can derive — biased locking on and off, the C2
stress-scheduling flags, split per-actor compilation — collects the frequency of
every observed result, and grades each one against a matrix the scenario
declares:

| Verdict | Meaning |
|---|---|
| `ACCEPTABLE` | A correct outcome. The scenario expects to see it. |
| `ACCEPTABLE_INTERESTING` | A correct but surprising outcome — a documented weak guarantee. It is reported separately every run, so it cannot quietly become normal. |
| `FORBIDDEN` | A defect. Observing it once fails the run. |

An outcome that matches no declared row is forbidden by default. Enumerating
what is *allowed* is the point: a scenario is a written contract, and the
suite's real output is not "pass" but the frequency table underneath it.

## Running it

```bash
./gradlew :narrativetrace-jcstress:jcstress -PjcstressMode=sanity   # seconds
./gradlew :narrativetrace-jcstress:jcstress -PjcstressMode=quick    # minutes
./gradlew :narrativetrace-jcstress:jcstress                         # hours (full)
```

Add `-PjcstressFilter=<regex>` to select scenarios — the filter matches the
fully qualified class name, so `-PjcstressFilter=LossAccounting` runs one and
`-PjcstressFilter=ai.narrativetrace.core.context` runs a package.

Results land in `narrativetrace-jcstress/build/reports/jcstress/` as HTML, one
page per scenario with the full frequency table.

**Where it runs.** The weekly scheduled JDK-21 CI job runs the suite in `quick`
mode — about twenty minutes for the current scenarios on an eight-core machine,
a thousand-odd results across every JVM configuration — and publishes the report
as an artifact whether the job passed or failed, because a forbidden outcome is
only diagnosable from the run that observed it. The full sweep is deliberately
manual: it takes hours, and it belongs to a session someone is watching. It is
not part of `./gradlew check`; the per-commit gate carries deterministic race
tests instead, so every defect this suite finds also gets a regression test that
runs everywhere.

## The invariants

Every scenario is one question. Grouped by the part of the pipeline it holds:

### The ring (`BoundedEventBuffer`)

| Invariant | Scenario |
|---|---|
| **Loss accounting.** Every published event is either delivered exactly once or counted as shed exactly once — never both, never neither. The count a reader sees as `TraceLoss.droppedEvents` may not disagree with what actually happened. | `LossAccountingTest` (two producers), `SaturatedRingAccountingTest` (four producers, sixteen publications into four slots) |
| **Exclusive claim.** Two producers never take the same slot. With capacity to spare, no publication may be lost at all. | `ClaimUniquenessTest` |
| **Prefix consistency, and slot integrity.** A drain running against a live producer sees a prefix of the publication order — never a torn slot, a duplicate, or an event delivered under an index that was not its own. The scenario reports integrity and prefix-order separately, because one failing result cannot say which broke. | `DrainRacingPublishTest` |
| **Safe publication.** A buffer handed to another thread with no happens-before edge is never observable half-built. | `UnsafePublicationTest` |
| **Overwrite integrity.** Overflow may drop data; it may never corrupt what survives. | `OverwriteWindowTest` |
| **No permanent loss on a single publication.** An early poll may miss; the event may not vanish. | `PollBeforePublishTest`, `ProducerPublishVisibilityTest`, `ConcurrentProducersTest` |

### The buffered consumer (`BufferedEventConsumer`)

| Invariant | Scenario |
|---|---|
| **The park decision.** The drain never concludes "nothing to do" while a completed publication is unconsumed and unseen. | `ConsumerParkWakeupTest` |
| **The flush post-condition, exactly as strong as it is.** A single flush does *not* guarantee that everything published before it is in the store — a producer's claimed, unwritten slot stops the drain in front of your event. Two flushes leave exactly what was published. | `FlushRacingPublishTest` |
| **Publication racing shutdown.** An event published while the consumer closes is retained or counted, never dropped in silence, and the closing path never throws into the application. | `CloseRacingPublishTest` |
| **Single-consumer discipline.** Only one thread walks the ring at a time, shutdown included. | `CloseRacingFlushTest` |
| **Idempotent close.** Two threads closing at once close once, and the last drain still happens. | `CloseIdempotenceTest` |

### The store (`EventStore`)

| Invariant | Scenario |
|---|---|
| **Prefix-consistent snapshots.** A snapshot taken while events are being appended is a prefix of the appends — never partially visible, never out of order. | `EventStoreSnapshotTest` |

### The async seams (`TraceStack`, `ThreadLocalNarrativeContext`)

| Invariant | Scenario |
|---|---|
| **Two-route visibility.** A worker's spans are reportable through the live-child registry while its scope is open and through adoption after it closes. A capture racing the hand-over sees them through one route or the other — never twice, never neither. | `LiveChildHandOverTest` |
| **All-or-nothing adoption.** A batch that would cross the adoption ceiling is refused whole and counted. A partially adopted batch would strand children whose parent stayed out, and the rendered tree would assert a call graph that never happened. | `AdoptionCeilingTest` |
| **Request teardown.** Two requests sharing one context each end cleanly and alone: no retained events, no retained span contexts, and neither request seeing the other's calls. | `ResetRacingRequestsTest` |

## Four defects this suite found

All four on 2026-09-01, all in code that had passed every deterministic test:

1. **A flush after close threw into the caller.** Draining a ring after
   `close()` handed the event to a closed `Flow` publisher, whose `offer`
   answers with `IllegalStateException` — and the flush path, unlike the publish
   path, had no boundary to swallow it. A request capturing during shutdown got
   an exception from the observability layer, which is the one thing this
   pipeline's contract forbids.
2. **Two threads walked the ring at once.** `close()` drained without the
   monitor every other drain holds, so a request flushing during shutdown and
   the closing thread both advanced the consumer index and delivered the same
   slot: a call that happened once appeared twice in the captured trace.
   Observed in 14% of samples on the first run.
3. **A capture could miss an async subtree entirely.** Handing a worker's spans
   to the thread that captures them is two steps — adopt, then unregister the
   live child — and the reader unioned its two sources in the *same* direction:
   it could read the adopted set before the hand-over added to it and the live
   registry after the hand-over removed the child, and come back with neither.
   Reading in the opposite order makes it impossible. Observed at 0.12% of
   samples; the visible symptom is a whole async subtree absent from a trace
   captured at that instant.
4. **The ring delivered an event under the wrong index.** The per-slot sequence
   protocol is a seqlock, and it was missing the writer's claim marker, the
   writer's store-store fence and the reader's re-read with a load-load fence. A
   producer lapping the ring in the two instructions between the consumer's
   sequence check and its slot read replaced the event underneath it, so one
   call was delivered twice and another vanished — with the loss counter
   reporting nothing. Measured across the fix on a four-slot ring: 1,732
   forbidden samples in 62M with none of the three pieces, 42 in 46M with the
   claim marker alone, 0 in 45M with the fences. The remaining failures at the
   middle step were the compiler's reordering, not the processor's.

Each fix landed with a deterministic regression test in the per-commit gate as
well as the scenario that found it, because the scenarios themselves do not run
on every commit.

## Porting these invariants

jcstress is a JVM tool, and the other NarrativeTrace implementations cannot use
it. **What ports mirror is the invariant table above, not the tool.** Each
platform expresses the same contracts with whatever it has — a .NET buffer
stress harness, `asyncio` race tests in Python, actor stress in Swift — and the
rows are the specification. A port that cannot yet run a scenario still owes the
invariant.

## What this suite does not do

- It does not replace the deterministic concurrency tests. Those pin behaviour a
  specific interleaving must produce; these enumerate which interleavings are
  allowed to exist.
- It does not measure throughput or latency. A scenario that got faster and
  wrong looks the same as one that got faster.
- It does not cover the framework integrations. Its subject is the core
  pipeline: the ring, the consumer, the store, and the stacks that feed them.
