/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;

class BoundedContextTest {

  @Test
  void createsContextWithPackagesAndDescription() {
    var context =
        new BoundedContext("billing", List.of("com.acme.billing"), "Charging, invoicing, funds");

    assertThat(context.name()).isEqualTo("billing");
    assertThat(context.packages()).containsExactly("com.acme.billing");
    assertThat(context.description()).isEqualTo("Charging, invoicing, funds");
  }

  @Test
  void rejectsBlankName() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new BoundedContext(" ", List.of(), "desc"))
        .withMessageContaining("name");
  }

  @Test
  void rejectsNullPackages() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new BoundedContext("billing", null, "desc"))
        .withMessageContaining("packages");
  }
}
