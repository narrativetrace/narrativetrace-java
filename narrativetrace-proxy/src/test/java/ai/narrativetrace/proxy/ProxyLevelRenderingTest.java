/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.annotation.NarrativeSummary;
import ai.narrativetrace.api.config.TracingLevel;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Proves the proxy does not pay the parameter-rendering cost when the active level would suppress
 * the values anyway (perf: no render-then-discard below DETAIL).
 */
class ProxyLevelRenderingTest {

  /**
   * Counts the one member rendering calls on a user type: the {@code @NarrativeSummary} hook.
   *
   * <p><b>@llmNote</b> The counter is {@code static} deliberately — a class that declares instance
   * fields is walked field by field, so an instance counter would read zero in every case, a green
   * test measuring nothing. The probe counted its own {@code toString()} until 2026-09-19, when the
   * stateless-leaf hook became an explicit list of platform leaf types and a user class's own text
   * stopped being read at all; the summary hook is the invocation that still observes "the renderer
   * reached this value".
   */
  static final class RenderProbe {
    static final AtomicInteger RENDER_COUNT = new AtomicInteger();

    @NarrativeSummary
    public String describe() {
      RENDER_COUNT.incrementAndGet();
      return "probe";
    }
  }

  @BeforeEach
  void resetProbe() {
    RenderProbe.RENDER_COUNT.set(0);
  }

  public interface Service {
    String handle(RenderProbe probe);
  }

  static final class ServiceImpl implements Service {
    @Override
    public String handle(RenderProbe probe) {
      return "ok";
    }
  }

  @Test
  void doesNotRenderParameterValuesBelowDetailLevel() {
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.NARRATIVE));
    var service = NarrativeTraceProxy.trace(new ServiceImpl(), Service.class, context);
    var probe = new RenderProbe();

    service.handle(probe);

    assertThat(RenderProbe.RENDER_COUNT.get())
        .as("parameter must not be rendered when the level suppresses values")
        .isZero();
    var root = context.captureTrace().roots().get(0);
    assertThat(root.signature().parameters().get(0).renderedValue()).isEmpty();
  }

  @Test
  void rendersParameterValuesAtDetailLevel() {
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.DETAIL));
    var service = NarrativeTraceProxy.trace(new ServiceImpl(), Service.class, context);
    var probe = new RenderProbe();

    service.handle(probe);

    assertThat(RenderProbe.RENDER_COUNT.get()).isPositive();
  }
}
