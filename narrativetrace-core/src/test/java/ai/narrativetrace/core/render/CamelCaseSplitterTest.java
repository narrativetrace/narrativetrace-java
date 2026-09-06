/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CamelCaseSplitterTest {

  @Test
  void split_singleWord() {
    assertThat(CamelCaseSplitter.split("order")).containsExactly("order");
  }

  @Test
  void split_camelCase() {
    assertThat(CamelCaseSplitter.split("calculateTotal")).containsExactly("calculate", "Total");
  }

  @Test
  void toPhrase_camelCase() {
    assertThat(CamelCaseSplitter.toPhrase("calculateTotal")).isEqualTo("calculate total");
  }
}
