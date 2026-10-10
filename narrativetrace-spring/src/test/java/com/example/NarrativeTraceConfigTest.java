/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.context.NarrativeContext;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * The Spring row's wiring snippet, run: the exact configuration class the doctor's fix line and the
 * docs show traces a bean under its base package. Lives in {@code com.example}, outside the
 * integration's own package, because that is where a reader's configuration lives too.
 */
class NarrativeTraceConfigTest {

  @Test
  void tracesABeanUnderTheBasePackage() {
    try (var ctx =
        new AnnotationConfigApplicationContext(NarrativeTraceConfig.class, DefaultGreeter.class)) {
      Greeter greeter = ctx.getBean(Greeter.class);
      NarrativeContext context = ctx.getBean(NarrativeContext.class);
      context.reset();

      assertThat(Proxy.isProxyClass(greeter.getClass())).isTrue();
      assertThat(greeter.greet("Alice")).isEqualTo("Hello, Alice!");
      assertThat(context.captureTrace().roots()).hasSize(1);
    }
  }
}
