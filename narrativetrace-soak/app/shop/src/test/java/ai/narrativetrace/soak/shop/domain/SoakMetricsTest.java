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

class SoakMetricsTest {

  @Test
  void exceptionCountSumsTheThreeFailureKinds() {
    var metrics = new SoakMetrics();

    metrics.incrementEdgeRejection();
    metrics.incrementEdgeRejection();
    metrics.incrementBusinessFailure();
    metrics.incrementPoisonException();

    assertThat(metrics.edgeRejectionCount()).isEqualTo(2);
    assertThat(metrics.businessFailureCount()).isEqualTo(1);
    assertThat(metrics.poisonExceptionCount()).isEqualTo(1);
    assertThat(metrics.exceptionCount()).isEqualTo(4);
  }

  @Test
  void startsAtZero() {
    var metrics = new SoakMetrics();

    assertThat(metrics.requestCount()).isZero();
    assertThat(metrics.exceptionCount()).isZero();
  }
}
