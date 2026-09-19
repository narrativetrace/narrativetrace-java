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
import ai.narrativetrace.api.annotation.NotTraced;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.core.render.ValueRenderer;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ParameterNameResolverTest {

  private final ValueRenderer valueRenderer = new ValueRenderer();

  interface Greeter {
    String greet(String name, int times);
  }

  interface AuthService {
    boolean login(String username, @NotTraced String password);
  }

  @Test
  void extractsParamNamesFromInterfaceMethod() throws Exception {
    var method = Greeter.class.getMethod("greet", String.class, int.class);
    var args = new Object[] {"Alice", 3};

    List<ParameterCapture> captures = ParameterNameResolver.resolve(method, args, valueRenderer);

    assertThat(captures).hasSize(2);
    assertThat(captures.get(0).name()).isEqualTo("name");
    assertThat(captures.get(0).renderedValue()).isEqualTo("\"Alice\"");
    assertThat(captures.get(0).redacted()).isFalse();
    assertThat(captures.get(1).name()).isEqualTo("times");
    assertThat(captures.get(1).renderedValue()).isEqualTo("3");
  }

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

  @Test
  void skipsValueRenderingWhenRenderValuesFalse() {
    var probe = new RenderProbe();
    var paramNames = new String[] {"arg"};
    var redacted = new boolean[] {false};

    var captures =
        ParameterNameResolver.resolve(
            paramNames, redacted, new Object[] {probe}, valueRenderer, false);

    assertThat(RenderProbe.RENDER_COUNT.get())
        .as("value must not be rendered when suppressed")
        .isZero();
    assertThat(captures.get(0).name()).isEqualTo("arg");
    assertThat(captures.get(0).renderedValue()).isEmpty();
    assertThat(captures.get(0).structuredValue()).isNull();
  }

  @Test
  void rendersValueWhenRenderValuesTrue() {
    var probe = new RenderProbe();
    var paramNames = new String[] {"arg"};
    var redacted = new boolean[] {false};

    ParameterNameResolver.resolve(paramNames, redacted, new Object[] {probe}, valueRenderer, true);

    assertThat(RenderProbe.RENDER_COUNT.get()).isPositive();
  }

  @Test
  void resolvesFromParamNamesAndRedactedArrays() {
    var paramNames = new String[] {"name", "times"};
    var redacted = new boolean[] {false, false};
    var args = new Object[] {"Alice", 3};

    var captures = ParameterNameResolver.resolve(paramNames, redacted, args, valueRenderer);

    assertThat(captures).hasSize(2);
    assertThat(captures.get(0).name()).isEqualTo("name");
    assertThat(captures.get(0).renderedValue()).isEqualTo("\"Alice\"");
    assertThat(captures.get(0).redacted()).isFalse();
    assertThat(captures.get(1).name()).isEqualTo("times");
    assertThat(captures.get(1).renderedValue()).isEqualTo("3");
  }

  @Test
  void resolvesRedactedFromArrays() {
    var paramNames = new String[] {"username", "password"};
    var redacted = new boolean[] {false, true};
    var args = new Object[] {"admin", "secret"};

    var captures = ParameterNameResolver.resolve(paramNames, redacted, args, valueRenderer);

    assertThat(captures.get(0).redacted()).isFalse();
    assertThat(captures.get(1).redacted()).isTrue();
    assertThat(captures.get(1).renderedValue()).isEqualTo("[REDACTED]");
  }

  @Test
  void detectsNotTracedAnnotationAsRedacted() throws Exception {
    var method = AuthService.class.getMethod("login", String.class, String.class);
    var args = new Object[] {"admin", "secret"};

    var captures = ParameterNameResolver.resolve(method, args, valueRenderer);

    assertThat(captures.get(0).redacted()).isFalse();
    assertThat(captures.get(1).redacted()).isTrue();
    assertThat(captures.get(1).name()).isEqualTo("password");
  }

  @Test
  void notTracedParameterRendersAsRedactedViaMethodOverload() throws Exception {
    var method = AuthService.class.getMethod("login", String.class, String.class);
    var args = new Object[] {"admin", "secret"};

    var captures = ParameterNameResolver.resolve(method, args, valueRenderer);

    assertThat(captures.get(1).renderedValue()).isEqualTo("[REDACTED]");
  }
}
