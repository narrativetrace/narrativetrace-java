/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.context;

import ai.narrativetrace.api.event.SpanId;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext.TraceStack;
import java.util.Set;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.Expect;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.III_Result;

/**
 * Verifies that two workers arriving at a full adoption ceiling cannot half-adopt a batch.
 *
 * <p>INTENT: Adoption is all-or-nothing on purpose. A batch that would cross the ceiling is refused
 * whole and counted, because adopting a prefix of it strands children whose parent stayed out — and
 * {@code TraceTreeBuilder} promotes a parentless node to a root, so the artifact would assert a
 * call graph that never happened, differently on every run. An incomplete trace is honest; a
 * wrong-shaped one is not.
 *
 * <p>Two workers hand over three spans each into a stack whose ceiling is four. Exactly one batch
 * fits. The check and the insert are two steps, so without the lock both could read a size of zero,
 * both could pass the ceiling test, and the stack would end up holding six spans — or, worse, one
 * batch and part of another.
 *
 * <p>{@code r1} is how many spans were adopted, {@code r2} how many scopes were refused, {@code r3}
 * how many spans those refusals cost.
 *
 * <p>Expected contract:
 *
 * <ul>
 *   <li>{@code 3, 1, 3}: the only acceptable outcome, and it is order-independent — whichever
 *       worker takes the monitor first adopts, the other is refused whole and counted.
 *   <li>{@code 6, 0, 0}: forbidden — both passed the ceiling check, which is the lost-update the
 *       lock exists to prevent.
 *   <li>{@code [45], ...}: forbidden — a batch was partially adopted, the one outcome that makes a
 *       rendered tree lie about structure.
 *   <li>{@code 0, 2, 6}: forbidden — both refused. One batch always fits an empty ceiling, so this
 *       would mean a refusal that never looked at the state it refused on.
 * </ul>
 *
 * <p><b>@pattern</b> All-or-nothing admission under a shared ceiling
 *
 * <p><b>@llmNote</b> The refusal counts are asserted, not just the set size: {@code TraceLoss}
 * reports refused scopes and refused spans to the reader as "this subtree is missing", and a
 * ceiling that refused silently would be worse than one that overflowed.
 */
@JCStressTest
@Outcome(id = "3, 1, 3", expect = Expect.ACCEPTABLE, desc = "One batch adopted, one refused whole")
@Outcome(
    id = "6, [0-9]+, [0-9]+",
    expect = Expect.FORBIDDEN,
    desc = "Both batches adopted: the ceiling was crossed")
@Outcome(
    id = "[45], [0-9]+, [0-9]+",
    expect = Expect.FORBIDDEN,
    desc = "A batch was partially adopted: the tree would assert a call graph that never happened")
@Outcome(
    id = "0, [0-9]+, [0-9]+",
    expect = Expect.FORBIDDEN,
    desc = "Both batches refused although one fits")
@Outcome(
    id = "3, [02-9][0-9]*, [0-9]+",
    expect = Expect.FORBIDDEN,
    desc = "The refusal was not counted exactly once")
@State
public class AdoptionCeilingTest {

  private static final int CEILING = 4;
  private static final int BATCH = 3;

  private final TraceStack origin = new TraceStack(CEILING);
  private final Set<SpanId> firstBatch = batch();
  private final Set<SpanId> secondBatch = batch();

  private static Set<SpanId> batch() {
    var ids = new java.util.HashSet<SpanId>();
    for (int i = 0; i < BATCH; i++) {
      ids.add(SpanId.generate());
    }
    return Set.copyOf(ids);
  }

  @Actor
  public void workerOne() {
    origin.adopt(firstBatch);
  }

  @Actor
  public void workerTwo() {
    origin.adopt(secondBatch);
  }

  @Arbiter
  public void arbiter(III_Result r) {
    r.r1 = origin.adoptedSpanIds().size();
    r.r2 = (int) origin.refusedScopeCount();
    r.r3 = (int) origin.refusedSpanCount();
  }
}
