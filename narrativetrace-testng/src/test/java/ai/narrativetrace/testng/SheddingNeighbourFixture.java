/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.testng;

import org.testng.annotations.Test;

/**
 * Two ordered tests sharing the JVM's one pipeline: the first publishes more events than the
 * test-sized ring holds, the second makes one call — read back by {@link TraceLossPerTestTest}.
 *
 * <p>The shared ring is built once per JVM at {@link SharedPipeline#DEFAULT_TEST_BUFFER_CAPACITY}
 * slots, so the first test overflows it by publishing more than that many events (two per call)
 * between two drains.
 */
public class SheddingNeighbourFixture {

  /** Calls enough to overflow the default shared ring: two events each, more than it holds. */
  static final int CALLS_THAT_OVERFLOW_THE_RING = SharedPipeline.DEFAULT_TEST_BUFFER_CAPACITY;

  @Test(priority = 1)
  public void shedsUnderLoad() {
    for (int i = 0; i < CALLS_THAT_OVERFLOW_THE_RING; i++) {
      Traced.call("BulkImporter", "importRow", "ok");
    }
  }

  @Test(priority = 2)
  public void makesOneCall() {
    Traced.call("Pricing", "total", "200");
  }
}
