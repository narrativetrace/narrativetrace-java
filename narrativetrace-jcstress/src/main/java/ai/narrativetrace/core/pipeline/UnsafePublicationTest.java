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
 * Verifies that a thread which sees a freshly built buffer sees a complete one.
 *
 * <p>INTENT: The ring is allocated once, whole, in the constructor — there is no lazy slot
 * allocation and no growth, so "does the first publish race the allocation?" cannot be asked of
 * this design. What can be asked is the question the design replaced it with: <b>a buffer handed to
 * another thread without synchronisation must never be observable half-built.</b> That guarantee is
 * the Java memory model's final-field freeze, and it holds only while {@code slots} and {@code
 * mask} stay final and the instance does not escape its own constructor — two properties a
 * refactoring can quietly remove.
 *
 * <p>One actor constructs a buffer, stores it into a plain (non-volatile, non-final) field, and
 * publishes into it. The other reads that field with no synchronisation whatsoever; when it sees a
 * reference, it checks the ring is fully formed and publishes into it too.
 *
 * <p>{@code r1} counts the events the arbiter drains; {@code r2} says whether the racing reader
 * found a usable ring.
 *
 * <p>Expected contract:
 *
 * <ul>
 *   <li>{@code 1, 1}: acceptable and common — the reader saw {@code null} and published nothing.
 *       The race did not happen this time.
 *   <li>{@code 2, 1}: acceptable — the reader saw the reference through a data race and found the
 *       ring complete, which is what the final-field freeze promises.
 *   <li>{@code r2 = 0}: forbidden. A reader that saw the reference but not the slots behind it
 *       means the freeze no longer covers the ring, and every unsynchronised hand-off of a pipeline
 *       in the wild is then a null-pointer waiting to happen.
 *   <li>{@code 0, ...}: forbidden — the constructing thread's own publication was lost.
 * </ul>
 *
 * <p><b>@pattern</b> Unsafe publication of a final-field-frozen object
 *
 * <p><b>@edgeCase</b> On a strongly ordered CPU this scenario reports {@code 1, 1} and {@code 2, 1}
 * only, and would keep doing so even if the fields stopped being final. It earns its place on
 * weakly ordered hardware, where an unfrozen ring is observable — which is exactly where nobody
 * runs the unit tests.
 *
 * <p><b>@llmNote</b> The reader catches {@link Throwable} rather than letting it escape: a
 * half-built ring surfaces as a {@code NullPointerException} from the slot array, and turning that
 * into a jcstress error would lose the outcome that names what happened.
 */
@JCStressTest
@Outcome(id = "1, 1", expect = Expect.ACCEPTABLE, desc = "Reader never saw the buffer")
@Outcome(id = "2, 1", expect = Expect.ACCEPTABLE, desc = "Reader saw a fully built ring")
@Outcome(id = "0, [01]", expect = Expect.FORBIDDEN, desc = "The builder's own event was lost")
@Outcome(
    id = "[12], 0",
    expect = Expect.FORBIDDEN,
    desc = "A half-built ring was observable through the race")
@State
public class UnsafePublicationTest {

  private static final int CAPACITY = 4;
  private static final int PUBLISHED = 2;

  private final TraceEvent[] events = StressEvents.sequence(PUBLISHED);
  private final DeliveryTally tally = new DeliveryTally(PUBLISHED);

  /** Deliberately plain: the point of the scenario is the absence of a happens-before edge. */
  private BoundedEventBuffer buffer;

  private boolean ringIntact = true;

  @Actor
  public void builder() {
    var created = new BoundedEventBuffer(CAPACITY);
    buffer = created;
    created.put(events[0]);
  }

  @Actor
  @SuppressWarnings("PMD.AvoidCatchingThrowable") // a half-built ring is an outcome, not an error
  public void reader() {
    var observed = buffer;
    if (observed == null) {
      return;
    }
    try {
      ringIntact = observed.capacity() == CAPACITY;
      observed.put(events[1]);
    } catch (Throwable half) { // NOPMD — the forbidden outcome, recorded rather than thrown
      ringIntact = false;
    }
  }

  @Arbiter
  public void arbiter(II_Result r) {
    buffer.drain(tally);
    r.r1 = tally.delivered();
    r.r2 = ringIntact && tally.intact() ? 1 : 0;
  }
}
