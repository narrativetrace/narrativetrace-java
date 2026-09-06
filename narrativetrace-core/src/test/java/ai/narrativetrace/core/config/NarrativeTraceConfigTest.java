/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.config.TracingLevel;
import org.junit.jupiter.api.Test;

class NarrativeTraceConfigTest {

  @Test
  void defaultsToDetailLevel() {
    var config = new NarrativeTraceConfig();
    assertThat(config.level()).isEqualTo(TracingLevel.DETAIL);
  }

  @Test
  void acceptsExplicitLevel() {
    var config = new NarrativeTraceConfig(TracingLevel.ERRORS);
    assertThat(config.level()).isEqualTo(TracingLevel.ERRORS);
  }

  @Test
  void levelIsChangeableAtRuntime() {
    var config = new NarrativeTraceConfig();
    assertThat(config.level()).isEqualTo(TracingLevel.DETAIL);

    config.setLevel(TracingLevel.OFF);
    assertThat(config.level()).isEqualTo(TracingLevel.OFF);

    config.setLevel(TracingLevel.ERRORS);
    assertThat(config.level()).isEqualTo(TracingLevel.ERRORS);
  }

  @Test
  void captureFlagDefaultsAreResourceOnEverythingElseOff() {
    var config = new NarrativeTraceConfig();

    assertThat(config.captureResource()).isTrue();
    assertThat(config.captureSourceLocation()).isFalse();
    assertThat(config.captureInstanceIds()).isFalse();
  }

  @Test
  void captureFlagsAreChangeableAtRuntime() {
    var config = new NarrativeTraceConfig();

    config.setCaptureResource(false);
    config.setCaptureSourceLocation(true);
    config.setCaptureInstanceIds(true);

    assertThat(config.captureResource()).isFalse();
    assertThat(config.captureSourceLocation()).isTrue();
    assertThat(config.captureInstanceIds()).isTrue();
  }

  @Test
  void resolveCaptureFlagsReadsTheConfigResolverChain() {
    System.setProperty("narrativetrace.capture.resource", "false");
    System.setProperty("narrativetrace.capture.sourceLocation", "true");
    System.setProperty("narrativetrace.capture.instanceIds", "true");
    try {
      var config = new NarrativeTraceConfig().resolveCaptureFlags(new ConfigResolver());

      assertThat(config.captureResource()).isFalse();
      assertThat(config.captureSourceLocation()).isTrue();
      assertThat(config.captureInstanceIds()).isTrue();
    } finally {
      System.clearProperty("narrativetrace.capture.resource");
      System.clearProperty("narrativetrace.capture.sourceLocation");
      System.clearProperty("narrativetrace.capture.instanceIds");
    }
  }

  @Test
  void resolveCaptureFlagsFallsBackToDefaultsWithoutConfiguration() {
    var config = new NarrativeTraceConfig().resolveCaptureFlags(new ConfigResolver());

    assertThat(config.captureResource()).isTrue();
    assertThat(config.captureSourceLocation()).isFalse();
    assertThat(config.captureInstanceIds()).isFalse();
  }
}
