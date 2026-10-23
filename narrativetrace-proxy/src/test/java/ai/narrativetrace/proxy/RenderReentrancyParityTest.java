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
import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import org.junit.jupiter.api.Test;

/**
 * Proxy-side parity for the render-reentrancy defect pinned by {@code RenderReentrancyGuardTest} in
 * {@code narrativetrace-agent}: rendering a parameter or return value must not cause spans of its
 * own, on the proxy engine exactly as on the agent.
 *
 * <p>INTENT: the agent reaches traced code from rendering through a woven accessor; the proxy
 * engine reaches it through the sanctioned narrative-summary hook, which is user code the renderer
 * invokes on purpose and which may call a proxied collaborator. No weaving is needed: a JDK dynamic
 * proxy is not instrumentation. The proxy's gate must therefore read {@code
 * RenderingGuard.isActive()} first, as {@code AgentRuntime.isActive()} does. This class pins the
 * return-value door; {@code RenderHookReachesProxiedCodeTest} pins the parameter door.
 */
class RenderReentrancyParityTest {

  interface StockGateway {
    String reserve();
  }

  /** A returned value whose summary hook talks to a proxied collaborator. */
  record CartLine(String productId, int quantity, StockGateway stock) {
    @NarrativeSummary
    public String toNarrativeSummary() {
      return productId + " x" + quantity + " (" + stock.reserve() + ")";
    }
  }

  interface LineGateway {
    CartLine fetch();
  }

  @Test
  void renderingAReturnedValueWhoseHookCallsAProxiedCollaboratorEmitsExactlyOneSpan() {
    var context = new ThreadLocalNarrativeContext();
    var stock = NarrativeTraceProxy.trace((StockGateway) () -> "held", StockGateway.class, context);
    LineGateway target = () -> new CartLine("SKU-1", 2, stock);
    var proxy = NarrativeTraceProxy.trace(target, LineGateway.class, context);

    proxy.fetch();

    var tree = context.captureTrace();
    assertThat(tree.roots())
        .as("rendering the return value must not add a call the application never made")
        .hasSize(1);
    assertThat(tree.roots().get(0).signature().methodName()).isEqualTo("fetch");
    assertThat(tree.roots().get(0).children()).isEmpty();
  }

  /** A returned value whose summary hook counts its own invocations. */
  static final class Tally {
    int calls;

    @NarrativeSummary
    public String toNarrativeSummary() {
      calls++;
      return "tally";
    }
  }

  interface TallyGateway {
    Tally fetch();
  }

  @Test
  void aReturnedValuesSummaryHookRunsOncePerCapture() {
    var context = new ThreadLocalNarrativeContext();
    var tally = new Tally();
    var proxy = NarrativeTraceProxy.trace((TallyGateway) () -> tally, TallyGateway.class, context);

    proxy.fetch();

    assertThat(tally.calls).as("one return value, two render paths, one hook run").isEqualTo(1);
  }
}
