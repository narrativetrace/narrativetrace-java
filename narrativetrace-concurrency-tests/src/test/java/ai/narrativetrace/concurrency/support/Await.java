/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.concurrency.support;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Bounded waits for worker results; a hang becomes a failure, never a stuck build. */
public final class Await {

  private static final long LIMIT_SECONDS = 30;

  private Await() {}

  public static void all(List<? extends Future<?>> futures) {
    for (var future : futures) {
      one(future);
    }
  }

  public static <T> T one(Future<T> future) {
    try {
      return future.get(LIMIT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (ExecutionException | TimeoutException e) {
      throw new IllegalStateException(e);
    }
  }
}
