/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.shop.domain;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Process-wide counters behind {@code GET /soak/stats}, distinguishing the three failure kinds the
 * design calls for: edge rejections, business failures, and poison exceptions.
 */
@Component
public class SoakMetrics {

  private final AtomicLong requestCount = new AtomicLong();
  private final AtomicLong edgeRejectionCount = new AtomicLong();
  private final AtomicLong businessFailureCount = new AtomicLong();
  private final AtomicLong poisonExceptionCount = new AtomicLong();

  public void incrementRequest() {
    requestCount.incrementAndGet();
  }

  public void incrementEdgeRejection() {
    edgeRejectionCount.incrementAndGet();
  }

  public void incrementBusinessFailure() {
    businessFailureCount.incrementAndGet();
  }

  public void incrementPoisonException() {
    poisonExceptionCount.incrementAndGet();
  }

  public long requestCount() {
    return requestCount.get();
  }

  public long edgeRejectionCount() {
    return edgeRejectionCount.get();
  }

  public long businessFailureCount() {
    return businessFailureCount.get();
  }

  public long poisonExceptionCount() {
    return poisonExceptionCount.get();
  }

  public long exceptionCount() {
    return edgeRejectionCount.get() + businessFailureCount.get() + poisonExceptionCount.get();
  }
}
