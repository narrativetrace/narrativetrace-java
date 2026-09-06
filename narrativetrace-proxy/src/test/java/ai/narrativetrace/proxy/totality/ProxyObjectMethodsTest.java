/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.proxy.totality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import ai.narrativetrace.api.config.TracingLevel;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.proxy.NarrativeTraceProxy;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

/**
 * A tracing proxy is still an ordinary object.
 *
 * <p>INTENT: The 2026-09-01 bug hunt found {@code proxy.equals(proxy)} returning {@code false} and
 * five trace roots produced by nothing but {@code Object} method calls. A non-reflexive object
 * breaks every set, map and cache it is put into, and framework identity checks with them.
 */
class ProxyObjectMethodsTest {

  interface Greeter {
    String greet(String name);
  }

  static final class RealGreeter implements Greeter {
    @Override
    public String greet(String name) {
      return "hello " + name;
    }

    @Override
    public String toString() {
      return "RealGreeter[live]";
    }
  }

  /** A target whose toString() throws — printing a wrapped bean must still work. */
  static final class RudeGreeter implements Greeter {
    @Override
    public String greet(String name) {
      return "hi " + name;
    }

    @Override
    public String toString() {
      throw new AssertionError("toString must not fail the log line");
    }
  }

  /** A target whose toString() answers null — legal, and no use to a log line. */
  static final class SilentGreeter implements Greeter {
    @Override
    public String greet(String name) {
      return "hey " + name;
    }

    @Override
    public String toString() {
      return null;
    }
  }

  private static Greeter proxyOver(Greeter target, ThreadLocalNarrativeContext context) {
    return NarrativeTraceProxy.trace(target, Greeter.class, context);
  }

  private static ThreadLocalNarrativeContext tracingContext() {
    return new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.DETAIL));
  }

  @Test
  void aProxyEqualsItself() {
    var proxy = proxyOver(new RealGreeter(), tracingContext());

    assertThat(proxy.equals(proxy)).isTrue();
  }

  @Test
  void twoProxiesOverTheSameTargetAreNotTheSameObject() {
    var target = new RealGreeter();
    var context = tracingContext();

    assertThat(proxyOver(target, context).equals(proxyOver(target, context))).isFalse();
  }

  @Test
  void aProxyIsNotEqualToItsTargetOrToNull() {
    var target = new RealGreeter();
    var proxy = proxyOver(target, tracingContext());
    Object nothing = null;

    assertThat(proxy.equals(target)).isFalse();
    assertThat(proxy.equals(nothing)).isFalse();
  }

  @Test
  void aProxyHashesTheSameEveryTime() {
    var proxy = proxyOver(new RealGreeter(), tracingContext());

    assertThat(proxy.hashCode()).isEqualTo(proxy.hashCode()).isEqualTo(proxy.hashCode());
  }

  @Test
  void aProxyBehavesInASet() {
    var proxy = proxyOver(new RealGreeter(), tracingContext());
    var set = new HashSet<Greeter>();

    set.add(proxy);
    set.add(proxy);

    assertThat(set).hasSize(1).contains(proxy);
  }

  @Test
  void aProxyStillPrintsAsItsTarget() {
    var proxy = proxyOver(new RealGreeter(), tracingContext());

    assertThat(proxy.toString()).isEqualTo("RealGreeter[live]");
  }

  @Test
  void aTargetThatWillNotPrintItselfGetsAStableDescriptionInstead() {
    var proxy = proxyOver(new RudeGreeter(), tracingContext());

    assertThatNoException().isThrownBy(proxy::toString);
    assertThat(proxy.toString())
        .startsWith("NarrativeTraceProxy@")
        .contains(RudeGreeter.class.getName())
        .isEqualTo(proxy.toString());
  }

  @Test
  void objectMethodsProduceNoTraceRoots() {
    var context = tracingContext();
    var proxy = proxyOver(new RealGreeter(), context);

    proxy.equals(proxy);
    proxy.hashCode();
    proxy.toString();

    assertThat(context.captureTrace().roots()).isEmpty();
  }

  @Test
  void aTargetWhosePrintingAnswersNullGetsTheStableDescription() {
    var proxy = proxyOver(new SilentGreeter(), tracingContext());

    assertThat(proxy.toString())
        .startsWith("NarrativeTraceProxy@")
        .contains(SilentGreeter.class.getName());
  }

  @Test
  void aRealCallIsStillTracedBesideThem() {
    var context = tracingContext();
    var proxy = proxyOver(new RealGreeter(), context);

    proxy.toString();
    assertThat(proxy.greet("Ada")).isEqualTo("hello Ada");
    proxy.hashCode();

    assertThat(context.captureTrace().roots()).singleElement();
  }
}
