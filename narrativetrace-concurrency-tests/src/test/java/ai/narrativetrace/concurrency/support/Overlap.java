/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.concurrency.support;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The barrier that makes N requests overlap: each request waits here, INSIDE the request, until all
 * N are in flight — so their hand-overs genuinely interleave on the shared pool.
 */
public final class Overlap {

  private volatile CyclicBarrier barrier = new CyclicBarrier(1);

  /** Expect {@code requests} requests to meet before any proceeds. */
  public void expect(int requests) {
    barrier = new CyclicBarrier(requests);
  }

  public void meet() {
    try {
      barrier.await(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (BrokenBarrierException | TimeoutException e) {
      throw new IllegalStateException("the concurrent requests never all arrived", e);
    }
  }
}
