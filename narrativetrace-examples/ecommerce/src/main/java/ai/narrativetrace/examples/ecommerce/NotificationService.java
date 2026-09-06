/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ecommerce;

import ai.narrativetrace.api.annotation.OnError;
import java.util.concurrent.CompletableFuture;
import org.springframework.scheduling.annotation.Async;

public interface NotificationService {
  @Async
  @OnError(
      value = "Failed to notify customer {customerId} about order {orderId}",
      exception = ExternalServiceException.class)
  CompletableFuture<Boolean> notifyOrderPlaced(String customerId, String orderId);
}
