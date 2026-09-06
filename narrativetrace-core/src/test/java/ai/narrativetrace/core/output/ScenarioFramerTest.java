/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScenarioFramerTest {

  @Test
  void humanizesCamelCaseMethodName() {
    assertThat(ScenarioFramer.humanize("customerPlacesOrder()")).isEqualTo("Customer places order");
  }

  @Test
  void humanizesUnderscoreSeparatedName() {
    assertThat(ScenarioFramer.humanize("customer_places_order_with_expired_card()"))
        .isEqualTo("Customer places order with expired card");
  }

  @Test
  void preservesDisplayNameWithSpaces() {
    assertThat(ScenarioFramer.humanize("Customer places order with loyalty discount"))
        .isEqualTo("Customer places order with loyalty discount");
  }

  @Test
  void stripsParameterSuffix() {
    assertThat(ScenarioFramer.humanize("customerPlacesOrder(NarrativeContext)"))
        .isEqualTo("Customer places order");
  }

  @Test
  void stripsParameterSuffixFromDisplayName() {
    assertThat(ScenarioFramer.humanize("customer places order(narrative context)"))
        .isEqualTo("customer places order");
  }

  @Test
  void framePrependsScenarioPrefix() {
    assertThat(ScenarioFramer.frame("customerPlacesOrder()"))
        .isEqualTo("Scenario: Customer places order");
  }

  @Test
  void framePreservesDisplayName() {
    assertThat(ScenarioFramer.frame("Customer places order with loyalty discount"))
        .isEqualTo("Scenario: Customer places order with loyalty discount");
  }

  @Test
  void emptyDisplayNameDoesNotThrow() {
    assertThat(ScenarioFramer.humanize("")).isEqualTo("");
  }

  @Test
  void parenthesesOnlyDisplayNameDoesNotThrow() {
    assertThat(ScenarioFramer.humanize("()")).isEqualTo("");
  }
}
