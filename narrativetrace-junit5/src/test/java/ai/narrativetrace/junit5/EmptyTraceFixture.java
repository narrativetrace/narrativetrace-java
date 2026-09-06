/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.junit5;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.context.NarrativeContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(NarrativeTraceExtension.class)
class EmptyTraceFixture {

  @Test
  void testWithEmptyTrace(NarrativeContext context) {
    // Don't enter any methods — trace stays empty
    assertThat(context).isNotNull();
  }
}
