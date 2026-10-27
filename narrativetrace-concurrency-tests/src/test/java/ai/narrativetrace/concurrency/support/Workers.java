/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.concurrency.support;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Names the pool threads so a trace can show which work ran on a worker and not the request thread.
 */
public final class Workers {

  public static final String PREFIX = "ntmt-worker-";

  private Workers() {}

  public static ThreadFactory factory() {
    var count = new AtomicInteger();
    return runnable -> {
      var thread = new Thread(runnable, PREFIX + count.incrementAndGet());
      thread.setDaemon(true);
      return thread;
    };
  }
}
