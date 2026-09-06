/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Per-scenario delta status against the last green {@code .nt} artifact: NEW (no baseline yet),
 * UNCHANGED (byte-identical), or CHANGED (with the compact summary and readable diff). This is what
 * the trace writer reports upward for the suite delta line and the failure surface.
 */
class ScenarioDeltaTest {

  private static final String BASELINE =
      """
      scenario: Weekend trip settles with three transfers

      - TripSettlementService.settleTrip(tripName) → value
      """;

  @Test
  void withoutBaselineTheScenarioIsNew() {
    var delta = ScenarioDelta.of("Weekend trip settles with three transfers", null, BASELINE);

    assertThat(delta.kind()).isEqualTo(ScenarioDelta.Kind.NEW);
    assertThat(delta.scenario()).isEqualTo("Weekend trip settles with three transfers");
    assertThat(delta.summary()).isEmpty();
    assertThat(delta.diff()).isEmpty();
  }

  @Test
  void byteIdenticalArtifactIsUnchanged() {
    var delta = ScenarioDelta.of("Weekend trip settles with three transfers", BASELINE, BASELINE);

    assertThat(delta.kind()).isEqualTo(ScenarioDelta.Kind.UNCHANGED);
    assertThat(delta.summary()).isEmpty();
    assertThat(delta.diff()).isEmpty();
  }

  @Test
  void changedArtifactCarriesSummaryAndDiff() {
    var current = BASELINE + "  - CurrencyConverter.toBaseCurrency(amount, currency) → value\n";

    var delta = ScenarioDelta.of("Weekend trip settles with three transfers", BASELINE, current);

    assertThat(delta.kind()).isEqualTo(ScenarioDelta.Kind.CHANGED);
    assertThat(delta.summary()).isEqualTo("+1 call CurrencyConverter.toBaseCurrency");
    assertThat(delta.diff())
        .isEqualTo(StructuralDelta.between(BASELINE, current).diff())
        .contains("+  - CurrencyConverter.toBaseCurrency(amount, currency) → value");
  }

  @Test
  void nullScenarioAndNullCurrentAreRejected() {
    assertThatThrownBy(() -> ScenarioDelta.of(null, BASELINE, BASELINE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("scenario must not be null");
    assertThatThrownBy(() -> ScenarioDelta.of("Weekend trip", BASELINE, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("current must not be null");
  }
}
