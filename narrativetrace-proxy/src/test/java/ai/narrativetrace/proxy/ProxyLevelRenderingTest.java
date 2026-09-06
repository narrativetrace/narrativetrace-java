/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.config.TracingLevel;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import org.junit.jupiter.api.Test;

/**
 * Proves the proxy does not pay the parameter-rendering cost when the active level would suppress
 * the values anyway (perf: no render-then-discard below DETAIL).
 */
class ProxyLevelRenderingTest {

  static final class RenderProbe {
    int renderCount;

    @Override
    public String toString() {
      renderCount++;
      return "probe";
    }
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

    assertThat(probe.renderCount)
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

    assertThat(probe.renderCount).isPositive();
  }
}
