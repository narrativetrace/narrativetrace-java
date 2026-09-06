/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.core.footprint.Footprint;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tier 0: what a default {@link ThreadLocalNarrativeContext} costs, and what an un-closed one
 * leaves behind.
 *
 * <p>INTENT: The JUnit 5 extension and the JUnit 4 rule build one of these per <em>test method</em>
 * and never close it, so "per-instance cost" and "collectable when dropped" are the two properties
 * that decide whether the library is cheap to test with. Both are pinned here rather than left to a
 * one-off audit that nothing repeats.
 *
 * <p><b>@llmNote</b> Collection is how this test asserts "registers no shutdown hook" without
 * reaching into {@code java.lang.ApplicationShutdownHooks} (which needs {@code --add-opens} and so
 * cannot be a gate test): a registered hook is a strong GC root, so a context that clears from a
 * weak reference demonstrably has none. {@code PipelineFootprintTest} pins the hook field itself,
 * one layer down, where the package allows it.
 */
class ContextFootprintTest {

  /**
   * Dropped contexts per run. The brief's floor is 1,000; the cost is roughly 1.5 ms each — almost
   * all of it {@code PipelineBootstrap}'s classpath scan and two {@code ServiceLoader} passes, not
   * the ring — so this is a ~2 s test, the most expensive Tier 0 assertion by an order of magnitude
   * and still cheaper than one JMH warm-up iteration.
   */
  private static final int DROPPED_CONTEXTS = 1_000;

  private static final long COLLECT_TIMEOUT_MILLIS = 5_000;

  @Test
  void aDefaultContextStaysInsideTheRetainedHeapBudget() {
    var retained = Footprint.retainedBytesPerInstance(ThreadLocalNarrativeContext::new, 16);

    assertThat(retained).isLessThanOrEqualTo(Footprint.DEFAULT_TOPOLOGY_BUDGET_BYTES);
  }

  @Test
  void constructingADefaultContextStartsNoThread() {
    long before = Footprint.liveLibraryThreads();

    var context = new ThreadLocalNarrativeContext();

    assertThat(Footprint.liveLibraryThreads()).isEqualTo(before);
    assertThat(context.isActive()).isTrue();
  }

  /**
   * One scenario, both of its consequences: a thousand contexts built and dropped without {@code
   * close()} must leave no thread running and must all be collectable. They are asserted together
   * because building them is the expensive part and doing it twice buys nothing.
   */
  @Test
  void aThousandDroppedContextsLeaveNoThreadRunningAndAreAllCollectable() {
    long threadsBefore = Footprint.liveLibraryThreads();

    var references = constructAndDrop(DROPPED_CONTEXTS);

    assertThat(Footprint.liveLibraryThreads()).isEqualTo(threadsBefore);
    assertThat(Footprint.awaitCleared(references, COLLECT_TIMEOUT_MILLIS))
        .as("an un-closed default context must be rooted by nothing — no thread, no shutdown hook")
        .isTrue();
  }

  /**
   * Calls per round in the allocation probe. Large enough that the counter's own cost divides away,
   * small enough that the test stays in the milliseconds Tier 0 is allowed.
   */
  private static final int RESET_CALLS = 1_000;

  /**
   * Tier 0 pin for the {@code reset()} half of the 2026-08-31 allocation regression: a thread that
   * never entered a traced method holds no stack, so there is nothing to clear and clearing it must
   * cost nothing. It used to construct a {@code TraceStack} — an {@code ArrayList}, a {@code
   * ConcurrentHashMap} key set and two {@code AtomicLong}s, ~344 B — purely to ask the empty thing
   * for its empty span set and drop it, on every call. A servlet filter resetting in a {@code
   * finally} paid that for every request that never traced.
   *
   * <p><b>@llmNote</b> This is an allocation assertion, not a footprint one, because the defect
   * produced garbage rather than retained heap: no functional test could see it, and every one of
   * the five {@code TraceStack} fields added between February and August was silently charged to
   * it.
   */
  @Test
  void resetOnAThreadThatNeverTracedAllocatesNothing() {
    var context = new ThreadLocalNarrativeContext();

    long perCall = Footprint.allocatedBytesPerCall(context::reset, RESET_CALLS);

    assertThat(perCall)
        .as("reset() on a thread with no stack must not build one to remove it")
        .isZero();
  }

  /** The no-op must be a no-op only when there is nothing to clear — a traced thread still is. */
  @Test
  void resetStillClearsTheStateOfAThreadThatDidTrace() {
    var context = new ThreadLocalNarrativeContext();
    context.enterMethod(new MethodSignature("Service", "call", List.of(), null, null));

    context.reset();

    assertThat(context.currentSpanId()).isNull();
    assertThat(context.captureTrace().roots()).isEmpty();
  }

  /**
   * Builds {@code count} default contexts, keeping only weak references. The instance is never held
   * in a local, so nothing but the weak reference can reach it once the constructor returns.
   */
  private static List<WeakReference<?>> constructAndDrop(int count) {
    var references = new ArrayList<WeakReference<?>>(count);
    for (int i = 0; i < count; i++) {
      references.add(new WeakReference<>(new ThreadLocalNarrativeContext()));
    }
    return references;
  }
}
