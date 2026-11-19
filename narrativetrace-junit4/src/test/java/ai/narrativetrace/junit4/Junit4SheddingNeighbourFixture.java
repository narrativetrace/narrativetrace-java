/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit4;

import ai.narrativetrace.api.event.MethodSignature;
import java.util.List;
import org.junit.ClassRule;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Two ordered tests sharing the JVM's one suite context: the first publishes more events than the
 * suite's ring holds, the second makes one call — read back by {@link Junit4TraceLossPerTestTest}.
 *
 * <p>The suite context is built once per JVM at the runtime's default ring size, so the first test
 * overflows it by publishing more than that many events (two per call) between two drains.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class Junit4SheddingNeighbourFixture {

  /** Calls enough to overflow the runtime's default ring: two events each, more than it holds. */
  private static final int CALLS_THAT_OVERFLOW_THE_RING = 40_000;

  @ClassRule public static NarrativeTraceClassRule classRule = new NarrativeTraceClassRule();

  @Rule public NarrativeTraceRule narrativeTrace = classRule.testRule();

  @Test
  public void firstShedsUnderLoad() {
    for (int i = 0; i < CALLS_THAT_OVERFLOW_THE_RING; i++) {
      narrativeTrace
          .context()
          .enterMethod(new MethodSignature("BulkImporter", "importRow", List.of()));
      narrativeTrace.context().exitMethodWithReturn("ok");
    }
  }

  @Test
  public void secondMakesOneCall() {
    narrativeTrace.context().enterMethod(new MethodSignature("Pricing", "total", List.of()));
    narrativeTrace.context().exitMethodWithReturn("200");
  }
}
