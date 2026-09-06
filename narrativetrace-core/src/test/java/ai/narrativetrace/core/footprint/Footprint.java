/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.footprint;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Assumptions;

/**
 * Heap, thread and reachability probes shared by the Tier 0 footprint tests.
 *
 * <p>INTENT: A 7 MiB-per-pipeline footprint shipped for months because every JMH benchmark builds
 * its context once in {@code @Setup(Level.Trial)}, where per-instance cost is invisible, and
 * nothing asserted memory at all. These probes are the cheap layer that catches that class on every
 * commit: ordinary JUnit, milliseconds, no harness.
 *
 * <p><b>@llmNote</b> Measuring retained heap from inside the JVM is inherently approximate. Two
 * things make it trustworthy enough to gate on: the quantity measured is megabytes while the noise
 * is kilobytes, and {@link #retainedBytesPerInstance} takes the <em>minimum</em> over several
 * rounds, because every source of noise adds bytes and none removes them.
 *
 * <p><b>@sideEffects</b> Calls {@link System#gc()}. That is the point — these are the only tests in
 * the suite allowed to.
 */
public final class Footprint {

  /**
   * Retained-heap budget for one default topology ({@code new DualPathPipeline()} or a default
   * {@code ThreadLocalNarrativeContext}), in bytes.
   *
   * <p>The arithmetic, written down so the next cap change updates this number deliberately rather
   * than by loosening it:
   *
   * <ul>
   *   <li>{@code BufferedEventConsumer.DEFAULT_CAPACITY} is 65,536 slots, and the ring is filled
   *       eagerly at construction — every slot holds a real {@code Slot} object so the per-slot
   *       sequence protocol has somewhere to write.
   *   <li>65,536 × 24 B per {@code Slot} (12 B header + 8 B {@code long} sequence + 4 B compressed
   *       reference) = 1,572,864 B, plus a 65,536 × 4 B + 16 B reference array = 262,160 B, giving
   *       <b>1,835,024 B of ring</b>.
   *   <li>Measured on JDK 17.0.20 with this probe: 1,835,888 B for {@code new DualPathPipeline()}
   *       and 1,836,187 B for {@code new ThreadLocalNarrativeContext()} — so the store, the
   *       publisher, the drop handler and the objects themselves are together under 1.2 kB.
   *   <li>Inside the Gradle test JVM this probe reads 1,838,880 B and 1,848,028 B for the same two
   *       — a few kilobytes of ambient noise on top, which is why the budget carries a margin at
   *       all.
   *   <li>Budget = 1,835,888 × 1.2 ≈ <b>2,200,000 B</b>. The 20 % absorbs that noise and a future
   *       kilobyte of topology, and nothing more: doubling the ring costs 1.8 MB and fails here,
   *       which is the regression this exists to catch.
   * </ul>
   *
   * <p><b>@edgeCase</b> Assumes compressed oops, which every heap this gate runs under (well under
   * 32 GB) gets by default. Without them a {@code Slot} is 32 B and the array is 8 B per entry, so
   * the ring alone is ~2.6 MB and this budget fails. That is a signal to re-measure on the JVM in
   * question, not to raise the number.
   */
  public static final long DEFAULT_TOPOLOGY_BUDGET_BYTES = 2_200_000L;

  /** Prefix every thread this library starts carries — the drain thread, watchdog and hook. */
  public static final String THREAD_PREFIX = "narrative-trace-";

  private static final int ROUNDS = 3;
  private static final int ALLOCATION_ROUNDS = 4;
  private static final int GC_PASSES = 2;
  private static final long GC_SETTLE_MILLIS = 40;
  private static final long COLLECT_POLL_MILLIS = 20;

  private Footprint() {}

  /**
   * Retained bytes for one instance built by {@code factory}, measured the way the cost audit
   * measured it: hold {@code instances} of them strongly and divide the settled heap delta.
   *
   * @param factory builds one instance per call
   * @param instances how many to hold; more divides the fixed noise further
   * @return the smallest per-instance figure any round produced
   * @throws IllegalStateException when no round saw the objects it had just allocated, which means
   *     the measurement is broken rather than the footprint small
   */
  public static long retainedBytesPerInstance(Supplier<?> factory, int instances) {
    long best = Long.MAX_VALUE;
    for (int round = 0; round < ROUNDS; round++) {
      best = Math.min(best, measureOnce(factory, instances));
    }
    if (best <= 0) {
      throw new IllegalStateException(
          "Retained-heap measurement saw no growth from " + instances + " live instances");
    }
    return best;
  }

  private static long measureOnce(Supplier<?> factory, int instances) {
    var warmup = new Object[4];
    fill(warmup, factory);
    Arrays.fill(warmup, null);
    long before = usedHeapAfterGc();
    var held = new Object[instances];
    fill(held, factory);
    long after = usedHeapAfterGc();
    long perInstance = (after - before) / instances;
    // Reaches `held` after the second reading, so it cannot be collected during it.
    Arrays.fill(held, null);
    return perInstance;
  }

  private static void fill(Object[] target, Supplier<?> factory) {
    for (int i = 0; i < target.length; i++) {
      target[i] = factory.get();
    }
  }

  /**
   * Bytes this thread allocates per call of {@code work}, measured with the JVM's own per-thread
   * allocation counter.
   *
   * <p>INTENT: The companion to {@link #retainedBytesPerInstance} for the paths whose cost is
   * garbage rather than footprint. {@code reset()} on a thread that never traced built a whole
   * {@code TraceStack} and dropped it — invisible to every functional test, and paid on every
   * request of a filter ordered before the traced beans. This is the probe the 2026-08-31
   * attribution run used, brought into the gate.
   *
   * <p><b>@llmNote</b> Takes the <em>minimum</em> over several rounds and subtracts an empty-loop
   * reading, for the same reason the heap probe takes a minimum: every source of noise (JIT
   * compilation, a lambda capture, the counter's own read) adds bytes and none removes them. The
   * first round is discarded as warm-up, so a path that allocates only on its first call — a class
   * initialiser, a {@code ClassValue} miss — is not charged for it.
   *
   * @param work the call under measurement, run {@code iterations} times per round
   * @param iterations calls per round; more divides the fixed noise further
   * @return the smallest per-call figure any round produced, never below zero
   * @throws org.opentest4j.TestAbortedException when the JVM cannot report thread allocation
   */
  public static long allocatedBytesPerCall(Runnable work, int iterations) {
    var bean = allocationBean();
    long best = Long.MAX_VALUE;
    for (int round = 0; round < ALLOCATION_ROUNDS; round++) {
      long measured = allocatedBytes(bean, work, iterations);
      long empty = allocatedBytes(bean, () -> {}, iterations);
      if (round > 0) {
        best = Math.min(best, Math.max(0, (measured - empty) / iterations));
      }
    }
    return best;
  }

  private static long allocatedBytes(com.sun.management.ThreadMXBean bean, Runnable work, int n) {
    long id = Thread.currentThread().getId();
    long before = bean.getThreadAllocatedBytes(id);
    for (int i = 0; i < n; i++) {
      work.run();
    }
    return bean.getThreadAllocatedBytes(id) - before;
  }

  /** The per-thread allocation counter, or an aborted test where the JVM has none. */
  private static com.sun.management.ThreadMXBean allocationBean() {
    var bean = java.lang.management.ManagementFactory.getThreadMXBean();
    Assumptions.assumeTrue(
        bean instanceof com.sun.management.ThreadMXBean sun
            && sun.isThreadAllocatedMemorySupported(),
        "JVM does not report per-thread allocation");
    var sun = (com.sun.management.ThreadMXBean) bean;
    sun.setThreadAllocatedMemoryEnabled(true);
    return sun;
  }

  /** Live threads this library started, by name — zero on every default construction path. */
  public static long liveLibraryThreads() {
    return Thread.getAllStackTraces().keySet().stream()
        .filter(thread -> thread.getName().startsWith(THREAD_PREFIX))
        .count();
  }

  /**
   * Waits, bounded, for every reference to clear.
   *
   * <p>INTENT: Collection is the observable consequence of "nothing roots this" — a registered
   * shutdown hook or a running thread holding the instance would keep it alive forever. Retrying
   * against a deadline rather than sleeping a fixed time keeps the test honest under a slow or
   * loaded GC without making it slow when the GC is prompt.
   *
   * @param references weak references to objects that must all be unreachable
   * @param timeoutMillis how long to keep trying before answering
   * @return whether every reference cleared within the timeout
   */
  public static boolean awaitCleared(List<WeakReference<?>> references, long timeoutMillis) {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    while (true) {
      collectGarbage();
      if (allCleared(references)) {
        return true;
      }
      if (System.nanoTime() >= deadline) {
        return false;
      }
      sleep(COLLECT_POLL_MILLIS);
    }
  }

  private static boolean allCleared(List<WeakReference<?>> references) {
    return references.stream().allMatch(reference -> reference.get() == null);
  }

  private static long usedHeapAfterGc() {
    for (int pass = 0; pass < GC_PASSES; pass++) {
      collectGarbage();
      sleep(GC_SETTLE_MILLIS);
    }
    var runtime = Runtime.getRuntime();
    return runtime.totalMemory() - runtime.freeMemory();
  }

  @SuppressWarnings("PMD.DoNotCallGarbageCollectionExplicitly") // the whole point of this class
  private static void collectGarbage() {
    System.gc();
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Footprint measurement interrupted", e);
    }
  }
}
