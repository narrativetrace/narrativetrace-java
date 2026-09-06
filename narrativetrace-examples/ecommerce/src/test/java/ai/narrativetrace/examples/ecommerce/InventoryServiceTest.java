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

class InventoryServiceTest {

  @Test
  void reserveReducesStockAndReturnsReservation() {
    InventoryService service = new InMemoryInventoryService();

    var reservation = service.reserve("SKU-MECHANICAL-KB", 2);

    assertThat(reservation.productId()).isEqualTo("SKU-MECHANICAL-KB");
    assertThat(reservation.quantity()).isEqualTo(2);
  }

  @Test
  void releaseRestoresStock() {
    InventoryService service = new InMemoryInventoryService();
    service.reserve("SKU-MECHANICAL-KB", 2);

    service.release("SKU-MECHANICAL-KB", 2);

    // can reserve again without error
    var reservation = service.reserve("SKU-MECHANICAL-KB", 150);
    assertThat(reservation.quantity()).isEqualTo(150);
  }

  @Test
  void reserveThrowsWhenInsufficientStock() {
    InventoryService service = new InMemoryInventoryService();

    assertThatThrownBy(() -> service.reserve("SKU-MECHANICAL-KB", 9999))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Insufficient stock");
  }
}
