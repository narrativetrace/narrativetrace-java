/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLClassLoader;
import org.junit.jupiter.api.Test;

/** Same-package test for the one branch a normal run never takes: SLF4J missing. */
class MdcAvailabilityTest {

  @Test
  void reportsMdcPresentOnTheOrdinaryClasspath() {
    assertThat(
            ContextPropagatingTaskDecorator.isMdcOnClasspath(
                ContextPropagatingTaskDecorator.class.getClassLoader()))
        .isTrue();
  }

  @Test
  void reportsMdcAbsentWhenTheLoaderCannotSeeIt() {
    var empty = new URLClassLoader(new java.net.URL[0], null);

    assertThat(ContextPropagatingTaskDecorator.isMdcOnClasspath(empty)).isFalse();
  }

  @Test
  void theInvariantHoldsForBothConstructions() {
    assertThat(new ContextPropagatingTaskDecorator().invariant()).isTrue();
    assertThat(ContextPropagatingTaskDecorator.withoutMdc().invariant()).isTrue();
  }
}
