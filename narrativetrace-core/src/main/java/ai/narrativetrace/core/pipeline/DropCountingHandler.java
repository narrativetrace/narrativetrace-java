/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import ai.narrativetrace.api.event.TraceEvent;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;

/**
 * SubmissionPublisher drop handler that counts rejected events.
 *
 * <p>INTENT: Buffered consumers use this to observe backpressure loss without retrying dropped
 * events.
 */
public final class DropCountingHandler
    implements BiPredicate<Flow.Subscriber<? super TraceEvent>, TraceEvent> {

  private final AtomicLong droppedCount = new AtomicLong();

  @Override
  public boolean test(Flow.Subscriber<? super TraceEvent> subscriber, TraceEvent event) {
    droppedCount.incrementAndGet();
    return false;
  }

  public long droppedCount() {
    return droppedCount.get();
  }
}
