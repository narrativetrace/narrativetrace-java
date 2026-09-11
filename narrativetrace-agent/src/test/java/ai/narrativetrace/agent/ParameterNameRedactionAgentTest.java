/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.core.context.ThreadLocalNarrativeContext;
import ai.narrativetrace.core.render.IndentedTextRenderer;
import ai.narrativetrace.core.render.RedactionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The name deny-list must reach a parameter on the bytecode path too.
 *
 * <p>INTENT: This is the path that actually leaked. An agent building against the published 0.2.0
 * artifacts used the Gradle plugin — so the agent, not the proxy — and its payment token was
 * written in cleartext into twelve trace files. The proxy has its own twin of this test; both are
 * needed, because the two paths compute their redaction flags in different files and only one of
 * them being fixed is exactly how this class of defect survives.
 */
class ParameterNameRedactionAgentTest {

  private ThreadLocalNarrativeContext context;

  @BeforeEach
  void setUp() {
    context = new ThreadLocalNarrativeContext();
    context.reset();
    AgentRuntime.setContext(context);
  }

  private Object transformAndLoad() throws Exception {
    var internalName = "ai/narrativetrace/agent/sample/UnannotatedCredentialService";
    var qualifiedName = "ai.narrativetrace.agent.sample.UnannotatedCredentialService";
    var originalBytes =
        getClass().getClassLoader().getResourceAsStream(internalName + ".class").readAllBytes();
    var transformed = ClassTransformer.transform(originalBytes, internalName);
    var loader = new ByteArrayClassLoader(getClass().getClassLoader(), transformed, qualifiedName);
    var clazz = loader.loadClass(qualifiedName);
    return clazz.getDeclaredConstructor().newInstance();
  }

  private String traceOf(String method, Class<?>[] types, Object... args) throws Exception {
    var instance = transformAndLoad();
    instance.getClass().getMethod(method, types).invoke(instance, args);
    return new IndentedTextRenderer().render(context.captureTrace());
  }

  @Test
  @DisplayName("an unannotated password parameter is redacted on the bytecode path")
  void redactsPasswordByNameAlone() throws Exception {
    var text = traceOf("login", new Class<?>[] {String.class, String.class}, "jsmith", "hunter2");

    assertThat(text).contains(RedactionPolicy.MARKER).doesNotContain("hunter2");
    assertThat(text).as("the username is not a credential").contains("jsmith");
  }

  @Test
  @DisplayName("the exact leak found in the wild: paymentToken on the agent path")
  void redactsThePaymentTokenThatLeaked() throws Exception {
    var text =
        traceOf(
            "pay",
            new Class<?>[] {String.class, long.class, String.class},
            "POL-001",
            22500L,
            "secret_payment_token");

    assertThat(text).doesNotContain("secret_payment_token").contains(RedactionPolicy.MARKER);
    assertThat(text).as("the amount is business data, not a secret").contains("22500");
    assertThat(text).as("the policy id is business data too").contains("POL-001");
  }

  @Test
  @DisplayName("ordinary parameters are untouched — redaction must not blank the trace")
  void leavesOrdinaryParametersVisible() throws Exception {
    var text = traceOf("describe", new Class<?>[] {String.class, int.class}, "ORD-991", 3);

    assertThat(text).contains("ORD-991").contains("3").doesNotContain(RedactionPolicy.MARKER);
  }
}
