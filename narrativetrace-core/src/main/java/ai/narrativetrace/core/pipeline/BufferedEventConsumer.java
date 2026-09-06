/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.api.event.TraceEvent;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

/**
 * Best-effort asynchronous consumer backed by a bounded buffer and drain thread.
 *
 * <p>INTENT: Use this when you want to keep an event history without blocking the caller on every
 * publish.
 *
 * <p>It uses adaptive draining with three modes based on queue fill level:
 *
 * <ul>
 *   <li><b>Normal (&lt; 70%)</b> — full processing: append each event to the store
 *   <li><b>Shedding (70–90%)</b> — batch-drain and discard to relieve buffer pressure
 *   <li><b>Emergency (&gt; 90%)</b> — drain and discard everything
 * </ul>
 *
 * <p>The buffer is a <b>fixed-size ring. It never grows.</b> Capacity is chosen once, at
 * construction, and the whole ring is allocated there; over-capacity publishing overwrites the
 * oldest slot rather than expanding. There is no initial capacity, no growth factor, and no resize.
 *
 * <p><b>@llmNote</b> Publishing and lifecycle methods ({@code flush}, {@code clear}, {@code close})
 * are thread-safe. The {@link #events()} snapshot reflects internal mutable state, so treat it as
 * an approximate read rather than a linearizable view.
 *
 * <p><b>@threadSafety</b> The ring is multi-producer/<em>single</em>-consumer, and this class is
 * what enforces the single half: every path that drains it — {@link #flush()}, {@link
 * #drainCycle()}, and the last drain inside {@link #close()} — holds this instance's monitor.
 * Producers hold nothing; {@link #accept} is the never-blocking side by design.
 *
 * <p><b>@sideEffects</b> The two thread-starting constructors ({@link #BufferedEventConsumer()} and
 * {@link #BufferedEventConsumer(int)}) start a drain thread, start a watchdog, and register a JVM
 * shutdown hook. The hook is a strong GC root, so an instance built that way and then dropped
 * without {@link #close()} lives until the JVM exits — <b>calling {@code close()} is mandatory</b>.
 * The {@code startConsumer=false} form, which every default topology here uses, starts nothing and
 * is collectable the moment it goes out of scope.
 *
 * <p>Plug this into {@link DualPathPipeline} as the best-effort consumer, or use it standalone.
 */
public final class BufferedEventConsumer implements RetainingConsumer, AutoCloseable {

  /**
   * Default buffer capacity: 65,536 slots — roughly 1.75 MB, all of it allocated up front.
   *
   * <p>Size it as {@code peak events/s × worst tolerable drain stall}, and budget the retained
   * memory at saturation as {@code capacity × ~300 B/event}. Worked example: 1,000 req/s × 50
   * traced calls × 2 events (enter + exit) is 100,000 events/s; a 500 ms stall leaves 50,000 events
   * outstanding, which fits under this default. Beyond that, raise it deliberately with {@code
   * narrativetrace.buffer.capacity} — the excess is shed and counted, never queued.
   */
  public static final int DEFAULT_CAPACITY = 1 << 16;

  static final double SHEDDING_THRESHOLD = 0.70;
  static final double EMERGENCY_THRESHOLD = 0.90;

  private final BoundedEventBuffer queue;
  private final EventStore store = new EventStore();
  private final SubmissionPublisher<TraceEvent> publisher = new SubmissionPublisher<>();
  private final DropCountingHandler dropHandler = new DropCountingHandler();

  /** Events the adaptive drain threw away in shedding or emergency mode. */
  private final AtomicLong shedByDrain = new AtomicLong();

  /**
   * Spans whose events were removed while the ring still held unconsumed slots.
   *
   * <p>INTENT: {@link #removeSpans} can only reach what has already been drained, and a drain stops
   * at the first slot a producer has claimed but not yet written — so under concurrent publishing
   * some of a finished request's events are still in the ring when its spans are removed, and a
   * later drain would put them into the store where nothing will ever clear them again. Remembering
   * the span ids for exactly as long as the ring is non-empty makes the removal cover events that
   * had not arrived yet, which is what "this request is over" has to mean.
   *
   * <p><b>@llmNote</b> Emptied the moment a drain observes an empty ring: everything published
   * before that point is in the store and has already been filtered, so no tombstone can outlive
   * its purpose. Bounded by the ring's own capacity for the pathological case of a ring that never
   * empties — at most one tombstone per outstanding event can ever be useful.
   */
  private final Set<SpanId> discardedSpanIds = ConcurrentHashMap.newKeySet();

  private static final long WATCHDOG_STALE_THRESHOLD_MILLIS = 5000;
  private static final long WATCHDOG_CHECK_INTERVAL_MILLIS = 1000;

  private final Thread consumer;
  private final ConsumerWatchdog watchdog;
  private final Thread shutdownHook;
  private final AtomicBoolean closed = new AtomicBoolean();
  private volatile boolean running = true;
  private volatile long lastActivityNanos;

  public BufferedEventConsumer() {
    this(DEFAULT_CAPACITY, true);
  }

  public BufferedEventConsumer(int bufferCapacity) {
    this(bufferCapacity, true);
  }

  @SuppressWarnings("PMD.DoNotUseThreads")
  public BufferedEventConsumer(int bufferCapacity, boolean startConsumer) {
    this.queue = new BoundedEventBuffer(bufferCapacity);
    this.consumer = new Thread(this::drain, "narrative-trace-consumer");
    this.consumer.setDaemon(true);
    if (startConsumer) {
      this.consumer.start();
      this.watchdog = createWatchdog();
      this.shutdownHook = new Thread(this::shutdownHookAction, "narrative-trace-shutdown");
      Runtime.getRuntime().addShutdownHook(shutdownHook);
    } else {
      this.watchdog = null;
      this.shutdownHook = null;
    }
  }

  public void subscribe(Flow.Subscriber<? super TraceEvent> subscriber) {
    publisher.subscribe(subscriber);
  }

  @Override
  public void accept(TraceEvent event) {
    queue.put(event);
  }

  @Override
  public synchronized List<TraceEvent> events() {
    return store.events();
  }

  /** Drains all buffered events into the store so query methods observe them. */
  @Override
  public synchronized void flush() {
    drainRemaining();
  }

  /**
   * Whether the ring holds nothing — no unconsumed event, and no slot claimed but not yet written.
   *
   * <p><b>@edgeCase</b> The two ring indices are read separately, so a racing read can report "not
   * drained" when the last event has just been consumed. That direction is harmless: the caller
   * flushes once more. The opposite direction cannot be produced by a producer that published
   * before this call, because claiming its slot moves the producer index first.
   */
  @Override
  public boolean drained() {
    return queue.isEmpty();
  }

  /** Clears stored events accumulated so far. */
  @Override
  public synchronized void clear() {
    store.clear();
  }

  /**
   * Removes the stored events belonging to the given span ids, and refuses the ones still in
   * flight; other traces are untouched.
   *
   * <p><b>@edgeCase</b> The refusal half is what makes this a request-lifecycle operation rather
   * than a snapshot edit — see {@link #discardedSpanIds}.
   */
  @Override
  public synchronized void removeSpans(Set<SpanId> spanIds) {
    store.removeSpans(spanIds);
    if (!queue.isEmpty() && discardedSpanIds.size() < queue.capacity()) {
      discardedSpanIds.addAll(spanIds);
    }
  }

  /**
   * Every event this consumer lost, from all three of its loss modes.
   *
   * <p>INTENT: One number, because a reader asking "is my trace complete?" does not care which
   * mechanism dropped what. The three are: the ring overwriting unconsumed slots (the only one that
   * can happen with {@code startConsumer=false}, i.e. in every default topology here), the adaptive
   * drain discarding batches above the shedding threshold, and a {@link java.util.concurrent.Flow}
   * subscriber that could not keep up.
   *
   * <p><b>@llmNote</b> Ring overwrites went uncounted until 2026-08-31, so this number used to read
   * zero for the one loss mode the default wiring can actually produce.
   */
  public long droppedCount() {
    return dropHandler.droppedCount() + queue.overwrittenCount() + shedByDrain.get();
  }

  public long lastActivityNanos() {
    return lastActivityNanos;
  }

  public long staleSinceMillis() {
    long last = lastActivityNanos;
    if (last == 0) {
      return 0;
    }
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - last);
  }

  /**
   * Ends this consumer: stops the drain thread, drains what is left, and closes the fan-out.
   *
   * <p>INTENT: Idempotent by compare-and-set, because a shutdown hook, a framework lifecycle
   * callback and a test teardown can all reach it, and two of them can arrive together.
   *
   * <p><b>@edgeCase</b> The last drain goes through {@link #flush()} rather than straight to the
   * ring. Shutdown is exactly when a request is most likely to be capturing, and a bare drain here
   * put a second consumer on a single-consumer ring: both read the same {@code consumerIndex} and
   * delivered the same slot, so a call that happened once appeared twice in the captured trace.
   * {@code CloseRacingFlushTest} observed it in 14% of samples before this went through the
   * monitor.
   *
   * <p><b>@sideEffects</b> The thread is interrupted and joined <em>before</em> the monitor is
   * taken, so a drain cycle in flight finishes on its own; nothing here waits for a lock while
   * holding one.
   */
  @Override
  @SuppressWarnings("PMD.DoNotUseThreads")
  public void close() {
    if (!closed.compareAndSet(false, true)) {
      return;
    }
    running = false;
    consumer.interrupt();
    try {
      consumer.join(2000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    if (watchdog != null) {
      watchdog.close();
    }
    removeShutdownHook();
    flush();
    publisher.close();
  }

  void shutdownHookAction() {
    close();
  }

  private void removeShutdownHook() {
    if (shutdownHook == null) {
      return;
    }
    try {
      Runtime.getRuntime().removeShutdownHook(shutdownHook);
    } catch (IllegalStateException ignored) { // NOPMD — JVM shutting down
    }
  }

  boolean consumerAlive() {
    return consumer.isAlive();
  }

  /**
   * The JVM shutdown hook this consumer registered, or {@code null} when it started nothing.
   *
   * <p>INTENT: Lets the footprint tests ask the <em>JVM</em> whether the hook is still registered —
   * {@link Runtime#removeShutdownHook} answers that and nothing else does — instead of trusting a
   * flag this class sets. The hook is the one strong GC root a dropped consumer can have, so
   * "registered?" is exactly the invariant the default {@code startConsumer=false} path must keep
   * at "no". Package-private: the hook is lifecycle machinery, never something a caller may hold.
   */
  Thread shutdownHook() {
    return shutdownHook;
  }

  /**
   * Slots in the ring, rounded up to the next power of two from the requested capacity.
   *
   * <p>INTENT: Lets composition tests assert the <em>size</em> a topology chose, so the default cap
   * and the {@code narrativetrace.buffer.capacity} knob are pinned by something other than reading
   * the constant back. Package-private: the number is a sizing decision the deployment makes, not
   * state a caller is meant to branch on at runtime.
   */
  int bufferCapacity() {
    return queue.capacity();
  }

  /**
   * One pass of the drain loop, and the only one running at a time.
   *
   * <p>INTENT: The ring is multi-producer/<em>single</em>-consumer — {@code consumerIndex} is a
   * plain field advanced with no atomicity — so every drain is serialised on this monitor. That is
   * what makes the drain thread and a request thread's {@link #flush()} take turns instead of both
   * walking the ring, reading the same index and delivering the same slot twice.
   *
   * <p><b>@edgeCase</b> The lock is held across {@code processNormal}, whose subscriber offer can
   * wait up to a millisecond for a lagging subscriber. A flusher can therefore wait that long. That
   * is the price of the single-consumer rule, and it is bounded; a duplicated call in a captured
   * trace is not.
   */
  synchronized void drainCycle() {
    lastActivityNanos = System.nanoTime();
    double fill = (double) queue.size() / queue.capacity();
    if (fill > EMERGENCY_THRESHOLD) {
      shedByDrain.addAndGet(queue.drain(e -> {}));
    } else if (fill > SHEDDING_THRESHOLD) {
      // Shedding: batch-drain and discard to relieve buffer pressure without the per-event
      // store/publish work of normal mode. Counted, because discarding quietly is the one thing a
      // best-effort path may never do.
      shedByDrain.addAndGet(queue.drain(e -> {}));
    } else {
      TraceEvent event = queue.poll();
      if (event != null) {
        processNormal(event);
      }
    }
  }

  private void processNormal(TraceEvent event) {
    if (!discardedSpanIds.isEmpty() && belongsToDiscardedSpan(event)) {
      return;
    }
    store.add(event);
    offerToSubscribers(event);
  }

  /**
   * Hands the event to {@link java.util.concurrent.Flow} subscribers, and lets a closed publisher
   * end the fan-out quietly.
   *
   * <p>INTENT: A drain can run after {@link #close()}. Nothing gates {@link #flush()} on the
   * lifecycle, and it should not be: a request that captures or resets after the pipeline was
   * closed still wants the events it published. {@link
   * java.util.concurrent.SubmissionPublisher#offer} answers a closed publisher with {@link
   * IllegalStateException}, and {@code EventPipeline.flush()} is reached directly from {@code
   * captureTrace()} — no {@link TraceBoundary} on that path — so letting it out turned an ended
   * subscription into the caller's exception.
   *
   * <p><b>@edgeCase</b> The check is the catch rather than a flag, so {@code close()}'s own final
   * drain still reaches subscribers: it runs before {@code publisher.close()}, sees an open
   * publisher, and delivers. A flag set at the start of {@code close()} would silence exactly the
   * last batch anyone is waiting for.
   *
   * <p><b>@llmNote</b> The event is already in the store by the time this runs, so a swallowed
   * offer costs a subscriber notification nobody is left to receive — never a retained event.
   */
  private void offerToSubscribers(TraceEvent event) {
    try {
      publisher.offer(event, 1, TimeUnit.MILLISECONDS, dropHandler);
    } catch (IllegalStateException subscriptionOver) { // NOPMD — the publisher is closed
      // Nothing left to notify. Retention above is unaffected.
    }
  }

  /** Whether this event belongs to a span a finished request already removed. */
  private boolean belongsToDiscardedSpan(TraceEvent event) {
    var spanId = TraceEvent.spanIdOf(event);
    return spanId != null && discardedSpanIds.contains(spanId);
  }

  private void drainRemaining() {
    queue.drain(this::processNormal);
    forgetDiscardedSpansIfDrained();
  }

  /**
   * Drops the tombstones once the ring is empty, because every event they could match has arrived.
   *
   * <p><b>@edgeCase</b> A stale read of the two ring indices can only report empty when it is not
   * in the direction that matters here — and if it ever did, the cost is the residue this whole
   * mechanism removes, never a wrong trace.
   */
  private void forgetDiscardedSpansIfDrained() {
    if (!discardedSpanIds.isEmpty() && queue.isEmpty()) {
      discardedSpanIds.clear();
    }
  }

  private void drain() {
    while (running && !Thread.currentThread().isInterrupted()) {
      drainCycle();
      if (queue.isEmpty()) {
        forgetDiscardedSpansIfDrained();
        LockSupport.parkNanos(1_000_000);
      }
    }
  }

  void onWatchdogStale() {
    System.err.println( // NOPMD
        "narrative-trace-consumer stale for " + staleSinceMillis() + "ms");
  }

  private ConsumerWatchdog createWatchdog() {
    return new ConsumerWatchdog(
        this::lastActivityNanos,
        WATCHDOG_STALE_THRESHOLD_MILLIS,
        WATCHDOG_CHECK_INTERVAL_MILLIS,
        this::onWatchdogStale);
  }
}
