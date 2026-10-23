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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADV-2026-10-23-3 — a hook that reaches traced code produces no span, on the proxy path too: the
 * trace of a call describes the calls the application made, never a call rendering made.
 *
 * <p>INTENT: {@code AgentRuntime.isActive()} reads {@code RenderingGuard.isActive()} before
 * anything else, so a woven method reached from inside rendering opens no span. {@code
 * NarrativeTraceProxy}'s own gate ({@code tracingActive}) asks only {@code context.isActive()} and
 * never the guard, so the proxy engine has no such protection.
 *
 * <p><b>@llmNote</b> {@code RenderReentrancyParityTest} records that the proxy half of this defect
 * cannot be reproduced without the agent attached, because a plain record's accessor invoked
 * reflectively is uninstrumented code. That is true of the accessor and misses the other door: the
 * narrative-summary hook is user code the renderer invokes on purpose, and a JDK dynamic proxy
 * needs no weaving at all. A hook that calls a proxied collaborator therefore reaches the proxy's
 * gate with the guard up and is traced — and because parameters are rendered before {@code
 * beginScope}, the phantom call is attributed as a second ROOT, beside the call the application
 * actually made.
 */
class RenderHookReachesProxiedCodeTest {

  interface AuditGateway {
    String stamp();
  }

  interface Checkout {
    String pay(Receipt receipt);
  }

  /** A value whose sanctioned summary hook talks to a collaborator, as application code does. */
  static final class Receipt {

    private final AuditGateway gateway;

    Receipt(AuditGateway gateway) {
      this.gateway = gateway;
    }

    @NarrativeSummary
    public String toNarrativeSummary() {
      return "Receipt(" + gateway.stamp() + ")";
    }
  }

  @Test
  @DisplayName("ADV-2026-10-23-3: a summary hook calling a proxied collaborator emits no span")
  void aSummaryHookCallingAProxiedCollaboratorEmitsNoSpan() {
    var context = new ThreadLocalNarrativeContext();
    var gateway =
        NarrativeTraceProxy.trace((AuditGateway) () -> "stamped", AuditGateway.class, context);
    Checkout checkout = NarrativeTraceProxy.trace(receipt -> "paid", Checkout.class, context);

    checkout.pay(new Receipt(gateway));

    var tree = context.captureTrace();
    var methodNames = tree.roots().stream().map(node -> node.signature().methodName()).toList();
    assertThat(methodNames)
        .as("rendering the parameter must not add a call the application never made")
        .containsExactly("pay");
  }
}
