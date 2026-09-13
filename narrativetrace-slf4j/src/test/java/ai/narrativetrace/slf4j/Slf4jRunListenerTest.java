/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.slf4j;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.spi.RunListener;
import ai.narrativetrace.core.spi.ExtensionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * The SLF4J half of the {@code RunListener} SPI seam (2026-09-13 ruling, item 2): the enclosing
 * test-suite run's phrase reaches MDC as {@code runName}, without any JUnit integration module
 * depending on SLF4J directly — see {@link Slf4jRunListener}'s own INTENT.
 */
class Slf4jRunListenerTest {

  private final Slf4jRunListener listener = new Slf4jRunListener();

  @BeforeEach
  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void runStartedSetsTheRunNameMdcKey() {
    listener.runStarted("a".repeat(32), "bold elk soars");

    assertThat(MDC.get("runName")).isEqualTo("bold elk soars");
  }

  @Test
  void runStartedIsIdempotentForTheSameRun() {
    listener.runStarted("a".repeat(32), "bold elk soars");
    listener.runStarted("a".repeat(32), "bold elk soars");

    assertThat(MDC.get("runName")).isEqualTo("bold elk soars");
  }

  @Test
  void runEndedRemovesTheRunNameMdcKey() {
    listener.runStarted("a".repeat(32), "bold elk soars");

    listener.runEnded();

    assertThat(MDC.get("runName")).isNull();
  }

  @Test
  void runEndedWithNothingSetIsANoOp() {
    listener.runEnded();

    assertThat(MDC.get("runName")).isNull();
  }

  @Test
  void aSecondRunOverwritesTheFirstsName() {
    listener.runStarted("a".repeat(32), "bold elk soars");
    listener.runStarted("b".repeat(32), "shy owl waits");

    assertThat(MDC.get("runName")).isEqualTo("shy owl waits");
  }

  /**
   * Proves the composition seam itself, matching {@code PipelineBootstrapNarrationTest}'s reason
   * for living here: a JUnit integration discovers {@link RunListener} through {@link
   * ExtensionRegistry}, and only this module — the one declaring {@code
   * META-INF/services/ai.narrativetrace.api.spi.RunListener} — can prove that discovery actually
   * finds {@link Slf4jRunListener} rather than nothing.
   */
  @Test
  void isDiscoveredAsARunListenerByServiceLoader() {
    var discovered = new ExtensionRegistry().load(RunListener.class);

    assertThat(discovered).hasAtLeastOneElementOfType(Slf4jRunListener.class);
  }
}
