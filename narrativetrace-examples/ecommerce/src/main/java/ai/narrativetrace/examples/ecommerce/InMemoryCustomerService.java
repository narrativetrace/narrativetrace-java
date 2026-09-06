/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ecommerce;

import java.util.Map;

public class InMemoryCustomerService implements CustomerService {

  private final Map<String, Customer> customers =
      Map.of(
          "C-1234", new Customer("C-1234", "Alice Johnson", CustomerTier.GOLD),
          "C-5678", new Customer("C-5678", "Bob Smith", CustomerTier.STANDARD),
          "C-BROKE", new Customer("C-BROKE", "Charlie Broke", CustomerTier.STANDARD));

  @Override
  public Customer findCustomer(String customerId) {
    var customer = customers.get(customerId);
    if (customer == null) {
      throw new IllegalArgumentException("Customer not found: " + customerId);
    }
    return customer;
  }
}
