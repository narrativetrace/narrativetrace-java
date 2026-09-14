/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.notify.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** The notify latency/failure configuration (see README.md "app/notify"). */
class NotificationProcessorTest {

  @Test
  void appliesTheConfiguredDelayAndSucceedsBelowTheFailureRoll() {
    var properties = new NotifyProperties(10, 10, 1);
    var processor = new NotificationProcessor(properties, () -> 10L, () -> 50);

    var outcome = processor.process("C-1234", "ORD-00001");

    assertThat(outcome.customerId()).isEqualTo("C-1234");
    assertThat(outcome.orderId()).isEqualTo("ORD-00001");
    assertThat(outcome.simulatedDelayMillis()).isEqualTo(10);
  }

  @Test
  void aRollBelowTheFailurePercentFails() {
    var properties = new NotifyProperties(1, 1, 100);
    var processor = new NotificationProcessor(properties, () -> 1L, () -> 0);

    assertThatThrownBy(() -> processor.process("C-1234", "ORD-00001"))
        .isInstanceOf(NotificationFailedException.class)
        .hasMessageContaining("ORD-00001");
  }

  @Test
  void aRollEqualToTheFailurePercentSucceeds() {
    // failIfUnlucky fails when roll < failurePercent — a roll exactly at the boundary succeeds.
    var properties = new NotifyProperties(1, 1, 50);
    var processor = new NotificationProcessor(properties, () -> 1L, () -> 50);

    assertThat(processor.process("C-1234", "ORD-00001").simulatedDelayMillis()).isEqualTo(1);
  }

  @Test
  void zeroFailurePercentNeverFails() {
    var properties = new NotifyProperties(1, 1, 0);
    var processor = new NotificationProcessor(properties, () -> 1L, () -> 0);

    assertThat(processor.process("C-1234", "ORD-00001")).isNotNull();
  }
}
