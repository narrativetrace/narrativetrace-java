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
import java.util.concurrent.Executor;

/**
 * Hand-over by an executor whose tasks are decorated (Spring's {@code
 * ContextPropagatingTaskDecorator}): the checkout submits plain tasks and the executor carries the
 * trace.
 */
public class ExecutorCheckout implements Checkout {

  private final Executor executor;
  private final Inventory inventory;

  public ExecutorCheckout(Executor executor, Inventory inventory) {
    this.executor = executor;
    this.inventory = inventory;
  }

  @Override
  public String place(String requestId, List<String> skus) {
    List<CompletableFuture<String>> pending = new ArrayList<>();
    for (var sku : skus) {
      pending.add(
          CompletableFuture.supplyAsync(() -> inventory.reserve(requestId + ":" + sku), executor));
    }
    Await.all(pending);
    return requestId;
  }
}
