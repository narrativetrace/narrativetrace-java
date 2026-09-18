/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import org.junit.jupiter.api.Test;

/**
 * Proxy-side parity for the render-reentrancy defect pinned by {@code RenderReentrancyGuardTest} in
 * {@code narrativetrace-agent}: a returned or parameter value that is a record must not cause
 * value-rendering to produce spans of its own.
 *
 * <p><b>@llmNote</b> {@code narrativetrace-proxy} has no dependency on {@code narrativetrace-agent}
 * — the two are peer engine modules, neither weaves the other's fixtures — so this module cannot
 * itself weave a class and therefore cannot reproduce the reentrancy here: a plain, un-woven
 * record's accessor, invoked reflectively by {@code ValueRenderer}, is ordinary, uninstrumented
 * code and opens no span regardless of any guard. Reproducing the reentrancy on the proxy path
 * needs the agent attached in the same JVM (as the finding that motivated this item observed, with
 * both the agent and the proxy tracing the same request) or a module that depends on both — neither
 * exists today, and adding one is an architecture decision left to the owner, not a test-only
 * change. This test is therefore the positive assertion the item's brief allows for this case: it
 * is GREEN before and after the guard lands. It pins the invariant; it does not reproduce the
 * defect.
 */
class RenderReentrancyParityTest {

  record CartLine(String productId, int quantity) {}

  interface LineGateway {
    CartLine fetch();
  }

  @Test
  void renderingAReturnedRecordThroughTheProxyEmitsExactlyOneSpan() {
    var context = new ThreadLocalNarrativeContext();
    LineGateway target = () -> new CartLine("SKU-1", 2);
    var proxy = NarrativeTraceProxy.trace(target, LineGateway.class, context);

    proxy.fetch();

    var tree = context.captureTrace();
    assertThat(tree.roots()).hasSize(1);
    assertThat(tree.roots().get(0).signature().methodName()).isEqualTo("fetch");
    assertThat(tree.roots().get(0).children()).isEmpty();
  }
}
