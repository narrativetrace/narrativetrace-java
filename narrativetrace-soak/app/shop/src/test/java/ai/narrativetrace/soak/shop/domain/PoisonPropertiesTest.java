/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.soak.shop.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PoisonPropertiesTest {

  @Test
  void bindsMissingValuesToDocumentedDefaults() {
    var properties = new PoisonProperties(null, 0);

    assertThat(properties.field()).isEqualTo("quantity");
    assertThat(properties.value()).isEqualTo(2_000_000_000);
  }

  @Test
  void honoursAnExplicitlyConfiguredSentinel() {
    var properties = new PoisonProperties("quantity", 999);

    assertThat(properties.value()).isEqualTo(999);
  }
}
