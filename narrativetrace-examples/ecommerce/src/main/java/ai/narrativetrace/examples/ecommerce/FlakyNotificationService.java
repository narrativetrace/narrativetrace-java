/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ecommerce;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class FlakyNotificationService implements NotificationService {

  private final NotificationService delegate;
  private final int failOnCall;
  private final AtomicInteger callCount = new AtomicInteger(0);

  public FlakyNotificationService(NotificationService delegate, int failOnCall) {
    this.delegate = delegate;
    this.failOnCall = failOnCall;
  }

  @Override
  public CompletableFuture<Boolean> notifyOrderPlaced(String customerId, String orderId) {
    int call = callCount.incrementAndGet();
    if (call >= failOnCall) {
      throw new ExternalServiceException(
          "External notification service unavailable (call #" + call + ")");
    }
    return delegate.notifyOrderPlaced(customerId, orderId);
  }
}
