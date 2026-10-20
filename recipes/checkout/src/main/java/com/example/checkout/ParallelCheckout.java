/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
// src/main/java/com/example/checkout/ParallelCheckout.java
package com.example.checkout;

import ai.narrativetrace.core.context.ForkGroup;
import ai.narrativetrace.core.context.NarrativeContext;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * The same checkout with the two slow steps off the caller's thread: reserve the stock and run the
 * fraud check on an executor, wait for both, then charge and save.
 *
 * <p>INTENT: Shows the handover for documentation/recipes/follow-a-flow-across-threads.md. A task
 * handed to an executor runs on a thread that knows nothing about the caller's trace, so the trace
 * has to be handed over with it: {@link ForkGroup#wrap} takes a snapshot at submission time, the
 * worker activates it, and {@link ForkGroup#merge()} grafts what the worker recorded back under the
 * span that launched it.
 *
 * <p><b>@llmNote</b> Creating a group is not enough — every forked task must be wrapped and {@code
 * merge()} must run after they finish, or the children stay detached from the caller's trace. The
 * same applies to work that outlives the call, with {@code FireAndForgetGroup} instead.
 *
 * <p><b>@sideEffects</b> Submits to the executor it is given and blocks until both tasks are done;
 * a refused order releases the reservation before it throws, like {@link DefaultOrderService}.
 */
public final class ParallelCheckout implements OrderService {

  private static final int UNIT_PRICE_CENTS = 3999;

  private final NarrativeContext context;
  private final InventoryService inventory;
  private final FraudCheckService fraud;
  private final PaymentService payments;
  private final OrderRepository orders;
  private final ExecutorService workers;

  public ParallelCheckout(
      NarrativeContext context,
      InventoryService inventory,
      FraudCheckService fraud,
      PaymentService payments,
      OrderRepository orders,
      ExecutorService workers) {
    this.context = context;
    this.inventory = inventory;
    this.fraud = fraud;
    this.payments = payments;
    this.orders = orders;
    this.workers = workers;
  }

  @Override
  public String placeOrder(String customerId, String sku, int quantity, String cardNumber) {
    var amountCents = UNIT_PRICE_CENTS * quantity;
    Runnable reserving = () -> inventory.reserve(sku, quantity);
    Callable<Boolean> scoring = () -> fraud.approve(customerId, amountCents);

    var group = ForkGroup.create(context);
    var reserved = workers.submit(group.wrap(reserving));
    var approved = workers.submit(group.wrap(scoring));
    var allowed = awaitBoth(reserved, approved);
    group.merge();

    if (!allowed) {
      inventory.release(sku, quantity);
      throw new PaymentDeclinedException("the fraud check refused the order");
    }
    payments.charge(customerId, amountCents, cardNumber);
    var orderId = "ORD-" + customerId + "-" + sku + "-" + quantity;
    orders.save(new Order(orderId, customerId, sku, quantity, amountCents));
    return orderId;
  }

  /** Waits for the reservation, then for the decision — the merge may only run after both. */
  private static boolean awaitBoth(Future<?> reserved, Future<Boolean> approved) {
    try {
      reserved.get();
      return approved.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while waiting for the checkout's tasks", e);
    } catch (ExecutionException e) {
      throw asRuntime(e);
    }
  }

  /** A task's own failure belongs to the caller, not wrapped in an executor's exception. */
  private static RuntimeException asRuntime(ExecutionException failure) {
    var cause = failure.getCause();
    if (cause instanceof RuntimeException runtime) {
      return runtime;
    }
    return new IllegalStateException("a checkout task failed", cause);
  }
}
