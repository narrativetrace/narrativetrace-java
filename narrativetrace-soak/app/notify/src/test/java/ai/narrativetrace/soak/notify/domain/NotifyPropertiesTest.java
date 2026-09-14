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

class NotifyPropertiesTest {

  @Test
  void bindsMissingValuesToDocumentedDefaults() {
    var properties = new NotifyProperties(0, 0, -1);

    assertThat(properties.minDelayMillis()).isEqualTo(5);
    assertThat(properties.maxDelayMillis()).isEqualTo(50);
    assertThat(properties.failurePercent()).isEqualTo(1);
  }

  @Test
  void rejectsMaxBelowMin() {
    assertThatThrownBy(() -> new NotifyProperties(50, 10, 1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsFailurePercentAbove100() {
    assertThatThrownBy(() -> new NotifyProperties(1, 2, 101))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void honoursExplicitConfiguration() {
    var properties = new NotifyProperties(5, 50, 1);

    assertThat(properties.minDelayMillis()).isEqualTo(5);
    assertThat(properties.maxDelayMillis()).isEqualTo(50);
    assertThat(properties.failurePercent()).isEqualTo(1);
  }
}
