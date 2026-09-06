/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import ai.narrativetrace.core.config.ConfigResolver;
import ai.narrativetrace.core.spi.ExtensionRegistry;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * An optional narration listener that will not load never fails the application that was starting.
 *
 * <p>INTENT: The 2026-09-01 bug hunt put a fake {@code Slf4jTraceEventListener} whose static
 * initialiser throws earlier on the classpath and watched {@code PipelineBootstrap.createDefault}
 * propagate the {@code AssertionError}. Context construction and agent initialisation both go
 * through this method, so that was an application startup failure caused by a logging jar.
 */
class NarrationBootstrapTotalityTest {

  private static PipelineBootstrap bootstrapNarratingThrough(String listenerClass) {
    return new PipelineBootstrap(new ConfigResolver(), new ExtensionRegistry(), listenerClass);
  }

  @Test
  void aNarrationListenerWhoseInitializerThrowsLeavesTheApplicationStarting() {
    var bootstrap = bootstrapNarratingThrough(ExplodingNarrationListener.class.getName());

    assertThatNoException().isThrownBy(() -> bootstrap.build("probe").close());
  }

  @Test
  void aNarrationListenerThatCannotLoadStillProducesAWorkingPipeline() {
    var bootstrap = bootstrapNarratingThrough(ExplodingNarrationListener.class.getName());

    try (var pipeline = (DualPathPipeline) bootstrap.build("probe")) {
      assertThat(pipeline.retainsEvents()).isTrue();
    }
  }

  @Test
  void aNarrationListenerThatCannotLoadSaysSoOnceOnStderr() {
    var captured = new ByteArrayOutputStream();
    var original = System.err;
    try {
      System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
      bootstrapNarratingThrough(ExplodingNarrationListener.class.getName()).build("probe").close();
    } finally {
      System.setErr(original);
    }

    assertThat(captured.toString(StandardCharsets.UTF_8))
        .contains("narrative-trace: narration listener")
        .contains(ExplodingNarrationListener.class.getName())
        .contains("continuing without narration");
  }

  @Test
  void aNarrationListenerWithoutTheExpectedConstructorIsAMisconfigurationNotAFailure() {
    var bootstrap = bootstrapNarratingThrough(NoStringConstructorListener.class.getName());

    assertThatNoException().isThrownBy(() -> bootstrap.build("probe").close());
  }

  @Test
  void anAbsentNarrationListenerStaysSilent() {
    var captured = new ByteArrayOutputStream();
    var original = System.err;
    try {
      System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
      bootstrapNarratingThrough("ai.narrativetrace.slf4j.NotOnThisClasspath")
          .build("probe")
          .close();
    } finally {
      System.setErr(original);
    }

    assertThat(captured.toString(StandardCharsets.UTF_8))
        .as("the module simply not being present is an ordinary outcome")
        .isEmpty();
  }

  /** Loads fine, but has no {@code (String)} constructor for the logger name. */
  public static final class NoStringConstructorListener {
    public NoStringConstructorListener() {
      // no-arg only, on purpose
    }
  }
}
