/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import ai.narrativetrace.api.event.TraceEvent;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.Consumer;

/*
 * Cache-line padding hierarchy — JCTools-inspired.
 *
 * JVM may reorder fields within a single class, but never across class boundaries.
 * By placing padding longs in separate abstract superclasses, we guarantee that
 * producerIndex and consumerIndex land on different cache lines (≥ 64 bytes apart),
 * eliminating false sharing between producer and consumer threads.
 */

/** Padding before producer index. Pushes producerIndex away from the object header. */
@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod")
abstract class BoundedEventBufferPad0 {
  long p00, p01, p02, p03, p04, p05, p06, p07;
}

/** Producer index — isolated on its own cache line between Pad0 and Pad1. */
@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod")
abstract class BoundedEventBufferProducerField extends BoundedEventBufferPad0 {
  volatile long producerIndex;
}

/** Padding between producer index and consumer fields. */
@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod")
abstract class BoundedEventBufferPad1 extends BoundedEventBufferProducerField {
  long p10, p11, p12, p13, p14, p15, p16, p17;
}

/**
 * Lock-free bounded ring buffer for trace events. Multiple producers, single consumer (MPSC).
 *
 * <p>Key design decisions inspired by JCTools:
 *
 * <ul>
 *   <li>Cache-line padding via inheritance — producer and consumer indices on separate cache lines
 *   <li>Power-of-two capacity with bitmask — branchless index wrapping
 *   <li>VarHandle atomic increment — lock-free producer coordination
 *   <li>Per-slot sequence numbers — safe publication protocol: volatile stores on the producer,
 *       acquire loads on the consumer
 * </ul>
 *
 * <p>Key difference from JCTools: overwrites oldest events on overflow instead of rejecting newest.
 * Under backpressure you see the most recent activity, not stale history.
 *
 * <p>Per-slot sequence numbers separate reservation from publication. The consumer only advances
 * past a slot whose sequence confirms the producer has finished writing, eliminating the
 * claim-before-store race present in simpler index-only designs.
 */
final class BoundedEventBuffer extends BoundedEventBufferPad1 {

  private static final VarHandle PRODUCER_INDEX;
  private static final VarHandle SLOT_SEQUENCE;

  static {
    try {
      var lookup = MethodHandles.lookup();
      PRODUCER_INDEX =
          lookup.findVarHandle(BoundedEventBufferProducerField.class, "producerIndex", long.class);
      SLOT_SEQUENCE = lookup.findVarHandle(Slot.class, "sequence", long.class);
    } catch (ReflectiveOperationException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  /**
   * One ring position: the generation it holds and that generation's event.
   *
   * <p><b>@llmNote</b> Both fields are {@code volatile} so that {@link #put} can order its three
   * stores with plain field writes — no {@code VarHandle} call, no fence call — see there for why
   * that matters. Do not demote either to a plain field.
   */
  static final class Slot {
    volatile long sequence;
    volatile TraceEvent event;
  }

  /**
   * Largest capacity this buffer can represent: {@link #nextPowerOfTwo} overflows to a negative
   * length above it, so a bigger request is a mistake rather than a choice.
   */
  static final int MAX_CAPACITY = 1 << 30;

  private final Slot[] slots;
  private final int mask;

  /**
   * The producer seam: run after a claim, before its publication — {@code null} in production, so
   * that {@link #put} makes no call in that window (a null check of a final field is not a call).
   */
  private final Runnable afterClaim;

  private final Runnable afterSequenceCheck;
  private long consumerIndex;

  /**
   * Events overwritten before the consumer reached them — this buffer's own shedding.
   *
   * <p>Counted where it is free: the consumer already computes how far it has to skip forward when
   * producers have lapped it, and that skip <em>is</em> the number of events lost. Nothing on the
   * producer path pays for this.
   *
   * <p><b>@llmNote</b> Written only by the single consumer, so the non-atomic {@code +=} is safe;
   * {@code volatile} is for the readers on other threads that report the count.
   */
  private volatile long overwritten;

  BoundedEventBuffer(int capacity) {
    this(capacity, null);
  }

  /**
   * Package-private constructor with the producer seam: fires after claim, before publish.
   *
   * @param afterClaim the seam, or {@code null} for none — what every production buffer passes
   */
  BoundedEventBuffer(int capacity, Runnable afterClaim) {
    this(capacity, afterClaim, () -> {});
  }

  /**
   * Package-private constructor with both seams: the producer's, and the consumer's — which fires
   * after a slot's sequence has been accepted and before its event is read, the window a lapping
   * producer can overwrite the slot in.
   *
   * @param afterClaim the producer seam, or {@code null} for none
   */
  BoundedEventBuffer(int capacity, Runnable afterClaim, Runnable afterSequenceCheck) {
    if (capacity < 1 || capacity > MAX_CAPACITY) {
      throw new IllegalArgumentException(
          "capacity must be between 1 and " + MAX_CAPACITY + ", but was " + capacity);
    }
    int rounded = nextPowerOfTwo(capacity);
    this.slots = new Slot[rounded];
    this.mask = rounded - 1;
    this.afterClaim = afterClaim;
    this.afterSequenceCheck = afterSequenceCheck;
    for (int i = 0; i < rounded; i++) {
      var slot = new Slot();
      slot.sequence = i;
      slots[i] = slot;
    }
  }

  /**
   * Enqueue an event. Always succeeds — overwrites oldest on overflow.
   *
   * <p>Publication is three steps, not two. The slot's sequence is moved to this generation's
   * <em>claim</em> value before the event is written and to its <em>published</em> value after, so
   * the interval in which the slot holds a new event under an old sequence does not exist. Without
   * the claim, a consumer that had already accepted the previous generation's sequence could read
   * the new event out of the slot and deliver it under the wrong index — see {@link
   * #steppedOverLappedSlot}, which is the check the claim makes effective.
   *
   * <p><b>Nothing between the claim and the publication is a call</b> (B-342). Once {@code
   * getAndAdd} has returned, the index is taken; if anything after it throws, the slot is never
   * published, every later drain stops in front of it, and every event published after it is
   * invisible until producers lap the whole ring. The one thing that can throw there without being
   * a bug in this class is {@link StackOverflowError}, raised where a frame is pushed — and a
   * traced call at the bottom of a recursion that exhausts the stack publishes exactly there. So
   * after the claim, production code pushes no frame: the three steps are plain stores to {@code
   * volatile} fields, and the test seam is skipped by a null check of a final field. The claim
   * itself is the last call, and it does not strand a slot: a {@code StackOverflowError} inside it
   * is raised while its frames are being pushed, before the atomic add, and after the add the JDK's
   * implementation only returns (compiled, it is a single atomic instruction). {@code
   * AbandonedClaimTest} pins the bytecode of this method to that shape.
   *
   * <p>The ordering is the volatile stores' own. Volatile writes are synchronization actions, and
   * synchronization order is total and consistent with program order, so neither the compiler nor
   * the CPU may let the event store rise above the claim store or sink below the publication store
   * — the two guarantees a {@code storeStoreFence} and a trailing release store gave the previous
   * shape of this method, both of which were calls. The consumer side is unchanged: it reads the
   * sequence with acquire, then the event (now a volatile read), then the sequence again behind
   * {@code loadLoadFence}.
   *
   * <p><b>@llmNote</b> Keep this method call-free after the claim: no helper, no {@code VarHandle}
   * access mode, no fence, no logging, no assertion that calls. The price is three volatile stores
   * per event instead of two release stores and a fence — on a weakly ordered CPU (AArch64) these
   * are store-release instructions as before; on x86 a volatile store carries a StoreLoad barrier
   * the release store did not. The overhead benchmark ({@code PipelineOverheadBenchmark}) measures
   * it.
   */
  void put(TraceEvent event) {
    long idx = (long) PRODUCER_INDEX.getAndAdd(this, 1L);
    if (afterClaim != null) {
      afterClaim.run();
    }
    Slot slot = slots[(int) (idx & mask)];
    slot.sequence = idx;
    slot.event = event;
    slot.sequence = idx + 1;
  }

  /**
   * Events this buffer shed by overwriting them before they were consumed.
   *
   * <p>INTENT: Overflow is silent by design on the producer side — that is the whole point of a
   * never-blocking ring — so it has to be observable somewhere, or a short trace is
   * indistinguishable from a quiet one.
   */
  long overwrittenCount() {
    return overwritten;
  }

  /**
   * Advances past the events producers overwrote before this consumer reached them, counting them.
   *
   * <p>INTENT: One definition of "how far behind am I, and what did that cost", because {@link
   * #poll} and {@link #drain} have to agree exactly. A skip counted by one and not the other would
   * make the number a reader sees as {@code TraceLoss.droppedEvents} depend on which drain mode the
   * topology happened to use.
   *
   * @param pIdx the producer index this consumer pass snapshotted; re-reading it here would let the
   *     skip and the delivery loop work from two different horizons
   */
  private void skipOverwritten(long pIdx) {
    if (pIdx - consumerIndex > slots.length) {
      overwritten += pIdx - consumerIndex - slots.length;
      consumerIndex = pIdx - slots.length;
    }
  }

  /** Dequeue the oldest event, or {@code null} if empty. Single-consumer only. */
  TraceEvent poll() {
    long pIdx = producerIndex;
    if (consumerIndex >= pIdx) {
      return null;
    }
    skipOverwritten(pIdx);
    Slot slot = slots[(int) (consumerIndex & mask)];
    long seq = (long) SLOT_SEQUENCE.getAcquire(slot);
    if (seq != consumerIndex + 1) {
      return null;
    }
    afterSequenceCheck.run();
    TraceEvent event = slot.event;
    if (steppedOverLappedSlot(slot)) {
      return null;
    }
    consumerIndex++;
    return event;
  }

  /**
   * Whether a producer replaced this slot's event between the sequence check and the read, and if
   * so, counts it as shed and steps over it.
   *
   * <p>INTENT: The sequence check proves the producer <em>had</em> finished writing the slot, not
   * that it still holds that event. A producer that laps the ring in the two instructions between
   * the check and the read replaces the event underneath the consumer, which would then deliver a
   * later generation's event under this index — and deliver it a second time when the index catches
   * up, while the event that belonged here vanished without being counted. Overflow may lose
   * events; it may not invent them, duplicate them, or lose them silently.
   *
   * <p>Re-reading the sequence is what makes the read a claim rather than a hope, and it works only
   * because {@link #put} marks the slot as claimed <em>before</em> it writes the event: the
   * sequence therefore changes no later than the event does, never after it. When it changed, this
   * index's event is definitively gone — counting one overwrite and advancing keeps the accounting
   * exact, and the generation that took the slot is delivered when the consumer reaches its own
   * index.
   *
   * <p>The {@code loadLoadFence} is not decoration. Acquire semantics order what comes
   * <em>after</em> a load, not what comes before, so nothing otherwise stops the compiler from
   * issuing this second sequence read ahead of the event read it is supposed to validate — which
   * puts both sequence reads on one side of the data read and restores exactly the race this method
   * exists to close. This is a seqlock, and it needs the reader's fence for the same reason every
   * other seqlock does.
   *
   * <p><b>@edgeCase</b> The window is a two-instruction one and needs a producer to publish a whole
   * ring's worth inside it, so it is vanishingly rare on the 65,536-slot default and reachable on a
   * deliberately small one. jcstress measured 123 samples in 2.2 million on a four-slot ring
   * ({@code DrainRacingPublishTest}, 2026-09-01).
   */
  private boolean steppedOverLappedSlot(Slot slot) {
    VarHandle.loadLoadFence();
    if ((long) SLOT_SEQUENCE.getAcquire(slot) == consumerIndex + 1) {
      return false;
    }
    overwritten++;
    consumerIndex++;
    return true;
  }

  /**
   * Drain all available events to the given action. Single-consumer only.
   *
   * @return how many events reached {@code action} — what a shedding caller discarded, so it can
   *     count what it threw away without a second pass over the ring
   */
  int drain(Consumer<TraceEvent> action) {
    long pIdx = producerIndex;
    skipOverwritten(pIdx);
    int delivered = 0;
    while (consumerIndex < pIdx) {
      Slot slot = slots[(int) (consumerIndex & mask)];
      long seq = (long) SLOT_SEQUENCE.getAcquire(slot);
      if (seq != consumerIndex + 1) {
        break;
      }
      afterSequenceCheck.run();
      TraceEvent event = slot.event;
      if (steppedOverLappedSlot(slot)) {
        continue;
      }
      action.accept(event);
      consumerIndex++;
      delivered++;
    }
    return delivered;
  }

  /**
   * The claim horizon: every publication begun before this call claimed an index below it.
   *
   * <p>INTENT: One half of the flush barrier. A caller that has finished its own {@link #put}s
   * reads the horizon, drains, and asks {@link #consumedPast} — its own events all sit below the
   * horizon, so "consumed past it" is exactly "everything I published has been delivered or
   * counted".
   */
  long cursor() {
    return producerIndex;
  }

  /**
   * Whether the consumer has accounted for every index below the given horizon — delivered, or
   * counted as overwritten. False while a drain is stopped at a slot some producer has claimed and
   * not yet written.
   *
   * <p><b>@threadSafety</b> Reads {@code consumerIndex}, which only the single consumer writes —
   * ask this where draining itself is legal: under the consumer's serialization, or on the consumer
   * thread.
   */
  boolean consumedPast(long cursor) {
    return consumerIndex >= cursor;
  }

  /** Approximate number of unconsumed events (may briefly exceed capacity during races). */
  int size() {
    return (int) Math.min(Math.max(0, producerIndex - consumerIndex), slots.length);
  }

  int capacity() {
    return slots.length;
  }

  boolean isEmpty() {
    return consumerIndex >= producerIndex;
  }

  private static int nextPowerOfTwo(int value) {
    return Integer.highestOneBit(Math.max(1, value - 1)) << 1;
  }
}
