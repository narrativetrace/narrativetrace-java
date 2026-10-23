/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.annotation.NarrativeSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Every {@link ValueRenderer} entry point hands the rendering guard back on the way out, whatever
 * the value did on the way in.
 *
 * <p>INTENT: {@code RenderingGuard.leave()} is a one-line {@code finally} body in both public entry
 * points and PIT's {@code VoidMethodCallMutator} survived on both, so nothing proved either ran. A
 * guard that is not handed back is not a flag that stays set for one call — it is a live
 * thread-local on an application thread, and {@code AgentRuntime.isActive()} reads it before
 * everything, so the thread stops being traced for the rest of its life. On a pooled thread that is
 * every request the pool hands it afterwards.
 *
 * <p><b>@llmNote</b> The hostile cases matter more than the plain one: the guard is released on the
 * way out of a value whose own hook threw, and on the way out of a value nothing could be read
 * from, because those are the paths that return through a {@code catch} rather than off the end.
 */
class ValueRendererGuardReleaseTest {

  private final ValueRenderer renderer = new ValueRenderer();

  /** No test may inherit a guard another test left up. */
  @BeforeEach
  void clearGuard() {
    RenderingGuard.leave();
  }

  @Test
  void theFlatEntryPointReleasesTheGuard() {
    renderer.render(new Leaf("ordinary"));

    assertThat(RenderingGuard.isActive()).isFalse();
  }

  @Test
  void theStructuredEntryPointReleasesTheGuard() {
    renderer.renderStructured(new Leaf("ordinary"));

    assertThat(RenderingGuard.isActive()).isFalse();
  }

  @Test
  void theFlatEntryPointReleasesTheGuardWhenTheValuesHookThrows() {
    renderer.render(new ThrowingSummary());

    assertThat(RenderingGuard.isActive()).isFalse();
  }

  @Test
  void theStructuredEntryPointReleasesTheGuardWhenTheValuesHookThrows() {
    renderer.renderStructured(new ThrowingSummary());

    assertThat(RenderingGuard.isActive()).isFalse();
  }

  @Test
  void theCaptureEntryPointReleasesTheGuardAfterDrivingBothPaths() {
    renderer.renderForCapture(new ThrowingSummary());

    assertThat(RenderingGuard.isActive()).isFalse();
  }

  record Leaf(String name) {}

  /** A sanctioned hook that refuses: the entry point returns through its {@code catch}. */
  static final class ThrowingSummary {
    @NarrativeSummary
    public String toNarrativeSummary() {
      throw new IllegalStateException("hook refuses");
    }
  }
}
