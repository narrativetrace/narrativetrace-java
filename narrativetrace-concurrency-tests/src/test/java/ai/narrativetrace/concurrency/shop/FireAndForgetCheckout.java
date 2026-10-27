/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.concurrency.shop;

import ai.narrativetrace.concurrency.support.Await;
import ai.narrativetrace.core.context.FireAndForgetGroup;
import ai.narrativetrace.core.context.NarrativeContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * Hand-over by {@link FireAndForgetGroup}: the launcher node stays in the request's trace, the
 * background work is read from the group afterwards. The checkout waits for the worker only so a
 * test can read the group deterministically; the export never depends on it.
 */
public class FireAndForgetCheckout implements Checkout {

  private final NarrativeContext context;
  private final ExecutorService pool;
  private final Inventory inventory;
  private final Map<String, FireAndForgetGroup> groupsByRequest = new ConcurrentHashMap<>();

  public FireAndForgetCheckout(
      NarrativeContext context, ExecutorService pool, Inventory inventory) {
    this.context = context;
    this.pool = pool;
    this.inventory = inventory;
  }

  @Override
  public String place(String requestId, List<String> skus) {
    var group = FireAndForgetGroup.create(context, "Checkout");
    groupsByRequest.put(requestId, group);
    List<Future<String>> pending = new java.util.ArrayList<>();
    for (var sku : skus) {
      Supplier<String> task = () -> inventory.reserve(requestId + ":" + sku);
      var wrapped =
          group.wrap(task); // wrapped HERE, on the request thread: the snapshot is taken now
      pending.add(pool.submit(wrapped::get));
    }
    Await.all(pending);
    return requestId;
  }

  /** The group a request launched, once its {@code place} returned. */
  public FireAndForgetGroup groupOf(String requestId) {
    return groupsByRequest.get(requestId);
  }
}
