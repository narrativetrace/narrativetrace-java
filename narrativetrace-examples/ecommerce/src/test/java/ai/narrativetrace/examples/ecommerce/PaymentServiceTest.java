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

class PaymentServiceTest {

  @Test
  void chargeReturnsConfirmation() {
    PaymentService service = new InMemoryPaymentService();

    var confirmation = service.charge("C-1234", 179.98, "tok_C-1234");

    assertThat(confirmation.transactionId()).isNotBlank();
    assertThat(confirmation.amount()).isEqualTo(179.98);
  }

  @Test
  void chargeThrowsForBrokeCustomer() {
    PaymentService service = new InMemoryPaymentService();

    assertThatThrownBy(() -> service.charge("C-BROKE", 100.00, "tok_C-BROKE"))
        .isInstanceOf(PaymentDeclinedException.class)
        .hasMessageContaining("declined");
  }
}
