/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import ai.narrativetrace.api.event.TraceEvent;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.II_Result;

/**
 * Verifies that the drain never decides to sleep while a completed publication is outstanding.
 *
 * <p>INTENT: The drain loop's last act in each pass is to ask {@link
 * BufferedEventConsumer#drained()} and park if the answer is yes. That question is the whole
 * wake-up race in one call: if a ring that has just been published into can answer "empty", the
 * event waits for whatever wakes the thread next, and in a design with an untimed park it would
 * wait forever.
 *
 * <p>Making the question answerable needs an ordering the harness does not give. The producer sets
 * a volatile flag <em>after</em> {@code accept} returns, and the drain reads that flag
 * <em>before</em> it asks whether the ring is drained. A true flag therefore means the publication
 * completed before the emptiness check began — so the check is reading a producer index that has
 * already moved, and "empty" is only honest if this same pass consumed the event.
 *
 * <p>{@code r1} is that violation, {@code r2} is what the store holds once the arbiter has flushed.
 *
 * <p>Expected contract:
 *
 * <ul>
 *   <li>{@code 0, 1}: the only acceptable outcome. Either the drain consumed the event, or it saw
 *       the ring as non-empty, or the publication had not happened yet — and the event is stored.
 *   <li>{@code 1, ...}: forbidden. The drain would have parked with a published event unconsumed
 *       and unseen, which is a stranded event on any implementation whose park is not timed.
 *   <li>{@code 0, 0}: forbidden — the event was lost outright.
 * </ul>
 *
 * <p><b>@edgeCase</b> The park itself is deliberately not exercised. {@code drainCycle()} is the
 * loop body without the sleep, so the scenario tests the <em>decision</em> rather than the timer; a
 * state that started a real drain thread would leak a thread per iteration and jcstress builds
 * millions of them.
 *
 * <p><b>@llmNote</b> The 1 ms timed park is what makes a wrong decision survivable today. This
 * scenario is what keeps the decision itself correct, so the timer stays a latency choice rather
 * than a correctness crutch.
 *
 * <p><b>@pattern</b> Park-decision correctness against a concurrent publish
 */
@JCStressTest
@Outcome(id = "0, 1", expect = Expect.ACCEPTABLE, desc = "No stranded event; the store has it")
@Outcome(
    id = "1, [01]",
    expect = Expect.FORBIDDEN,
    desc = "The drain would park with a completed publication outstanding")
@Outcome(id = "0, 0", expect = Expect.FORBIDDEN, desc = "The event was lost, not merely delayed")
@State
public class ConsumerParkWakeupTest {

  private final BufferedEventConsumer consumer = new BufferedEventConsumer(4, false);
  private final TraceEvent event = StressEvents.tagged(0);

  /** Volatile, and the whole point: it is the only ordering edge the scenario has. */
  private volatile boolean published;

  private boolean stranded;

  @Actor
  public void producer() {
    consumer.accept(event);
    published = true;
  }

  @Actor
  public void drain() {
    consumer.drainCycle();
    boolean publishedBefore = published;
    boolean empty = consumer.drained();
    stranded = publishedBefore && empty && consumer.events().isEmpty();
  }

  @Arbiter
  public void arbiter(II_Result r) {
    consumer.flush();
    r.r1 = stranded ? 1 : 0;
    r.r2 = consumer.events().size();
  }
}
