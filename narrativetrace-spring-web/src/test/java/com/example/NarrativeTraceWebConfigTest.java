/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package com.example;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.servlet.NarrativeTraceFilter;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * The Spring Web row's wiring snippet, run: the exact configuration class the doctor's fix line and
 * the docs show registers the request filter Spring Boot then installs in the servlet container.
 */
class NarrativeTraceWebConfigTest {

  @Test
  void registersTheRequestFilterAsABean() {
    try (var ctx = new AnnotationConfigApplicationContext(NarrativeTraceWebConfig.class)) {
      assertThat(ctx.getBean(NarrativeTraceFilter.class)).isNotNull();
    }
  }
}
