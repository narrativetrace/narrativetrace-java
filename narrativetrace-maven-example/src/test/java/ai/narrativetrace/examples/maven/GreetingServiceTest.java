/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.maven;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.context.NarrativeContext;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.junit5.NarrativeTraceExtension;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Proves the NarrativeTrace runtime jars work exactly the same from a plain {@code mvn test} as
 * they do under Gradle — same extension, same proxy call, same trace. {@code
 * narrativetrace.output=true} is set in {@code pom.xml}'s Surefire configuration, so this test also
 * writes {@code target/narrativetrace/GreetingServiceTest/greetsByName.md} on every run.
 */
@ExtendWith(NarrativeTraceExtension.class)
class GreetingServiceTest {

  @Test
  void greetsByName(NarrativeContext context) {
    var service =
        NarrativeTraceProxy.trace(new DefaultGreetingService(), GreetingService.class, context);

    var greeting = service.greet("Alice");

    assertThat(greeting).isEqualTo("Hello, Alice!");
    var narrative = new IndentedTextRenderer().render(context.captureTrace());
    assertThat(narrative).contains("GreetingService.greet(name: \"Alice\")");
  }
}
