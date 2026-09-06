/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ecommerce;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryPaymentService implements PaymentService {

  private final AtomicInteger txCounter = new AtomicInteger(1);
  private final Set<String> declinedCustomers = Set.of("C-BROKE");

  @Override
  public PaymentConfirmation charge(String customerId, double amount, String cardToken) {
    if (declinedCustomers.contains(customerId)) {
      throw new PaymentDeclinedException("Payment declined for customer " + customerId);
    }
    var txId = "TXN-%05d".formatted(txCounter.getAndIncrement());
    return new PaymentConfirmation(txId, amount);
  }
}
