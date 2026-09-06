/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.spring.test;

import ai.narrativetrace.spring.EnableNarrativeTrace;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableNarrativeTrace
public class DefaultPackageConfig {
  @Bean
  GreetingService greetingService() {
    return new DefaultGreetingService();
  }
}
