/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.concurrency.shop;

import ai.narrativetrace.concurrency.support.Await;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Hand-over by Spring's {@code @Async}: calls an async bean and waits for its futures. */
public class AsyncCheckout implements Checkout {

  private final AsyncInventory inventory;

  public AsyncCheckout(AsyncInventory inventory) {
    this.inventory = inventory;
  }

  @Override
  public String place(String requestId, List<String> skus) {
    List<CompletableFuture<String>> pending = new ArrayList<>();
    for (var sku : skus) {
      pending.add(inventory.reserveAsync(requestId + ":" + sku));
    }
    Await.all(pending);
    return requestId;
  }
}
