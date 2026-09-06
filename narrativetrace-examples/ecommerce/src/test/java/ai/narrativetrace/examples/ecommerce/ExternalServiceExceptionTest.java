/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ecommerce;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExternalServiceExceptionTest {

  @Test
  void twoArgConstructorPreservesMessageAndCause() {
    var cause = new RuntimeException("network error");
    var exception = new ExternalServiceException("failed", cause);

    assertThat(exception.getMessage()).isEqualTo("failed");
    assertThat(exception.getCause()).isSameAs(cause);
  }
}
