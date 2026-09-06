/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.narrativetrace.api.config.TracingLevel;
import ai.narrativetrace.core.config.NarrativeTraceConfig;
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

/**
 * What the proxy does when reflection itself refuses to cooperate.
 *
 * <p>INTENT: These are the two failure modes a JDK proxy cannot dispatch on its own — a method the
 * runtime will not open, and an invocation that fails before the target's body is entered. Both are
 * reached directly rather than through the factories, because a {@link java.lang.reflect.Proxy}
 * only ever dispatches interface methods of a target it was handed.
 */
class ProxyMethodDegradationTest {

  interface Greeter {
    String greet(String name);
  }

  @Test
  void aMethodTheRuntimeWillNotOpenYieldsNoMetadataRatherThanAFailure() throws Exception {
    var closedToUs = ArrayList.class.getDeclaredMethod("grow", int.class);

    assertThat(NarrativeTraceProxy.metadataFor(closedToUs))
        .as("java.base does not open java.util, so setAccessible refuses")
        .isNull();
  }

  @Test
  void anInvocationThatCannotReachATargetFailsWithItsOwnError() {
    var context = new ThreadLocalNarrativeContext(new NarrativeTraceConfig(TracingLevel.DETAIL));
    var proxy = NarrativeTraceProxy.trace((Greeter) null, Greeter.class, context);

    assertThatThrownBy(() -> proxy.greet("Ada"))
        .as("the reflective failure reaches the caller, not a tracing error")
        .isInstanceOf(NullPointerException.class);
  }
}
