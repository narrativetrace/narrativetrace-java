/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ecommerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CustomerServiceTest {

  @Test
  void findCustomerReturnsCustomerById() {
    CustomerService service = new InMemoryCustomerService();

    var customer = service.findCustomer("C-1234");

    assertThat(customer.id()).isEqualTo("C-1234");
    assertThat(customer.name()).isNotBlank();
    assertThat(customer.tier()).isNotNull();
  }

  @Test
  void findCustomerThrowsForUnknownId() {
    CustomerService service = new InMemoryCustomerService();

    assertThatThrownBy(() -> service.findCustomer("UNKNOWN"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("UNKNOWN");
  }
}
