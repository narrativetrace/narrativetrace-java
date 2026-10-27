/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.concurrency.shop;

import ai.narrativetrace.concurrency.support.Await;
import ai.narrativetrace.core.context.ForkGroup;
import ai.narrativetrace.core.context.NarrativeContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/** Hand-over by {@link ForkGroup}: wrap each task, join, merge under the submitting span. */
public class ForkCheckout implements Checkout {

  private final NarrativeContext context;
  private final ExecutorService pool;
  private final Inventory inventory;

  public ForkCheckout(NarrativeContext context, ExecutorService pool, Inventory inventory) {
    this.context = context;
    this.pool = pool;
    this.inventory = inventory;
  }

  @Override
  public String place(String requestId, List<String> skus) {
    var group = ForkGroup.create(context);
    List<Future<String>> pending = new ArrayList<>();
    for (var sku : skus) {
      Callable<String> task = () -> inventory.reserve(requestId + ":" + sku);
      pending.add(pool.submit(group.wrap(task)));
    }
    Await.all(pending);
    group.merge();
    return requestId;
  }
}
