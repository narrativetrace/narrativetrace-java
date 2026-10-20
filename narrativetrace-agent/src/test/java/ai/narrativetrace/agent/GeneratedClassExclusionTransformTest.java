/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * List 1's name-shape rule, exercised through the transformer's public entry point: a class whose
 * binary name carries a generator's own marker is not instrumented, even when it matches {@code
 * packages=} — the same precondition ordering {@link LoaderVisibilityTransformTest} proves for the
 * loader-visibility rule, one gate earlier (cheapest filters first).
 *
 * <p>Every case below hands the transformer an empty {@code byte[]} rather than real class bytes:
 * if the name-shape gate did not exist, ASM would throw trying to parse it, and the transformer's
 * generic catch-all would also return {@code null}, but noisily (one "cannot instrument" line on
 * {@code stderr}). Asserting {@code stderr} stayed empty is what proves the *new*, silent gate
 * fired instead of that fallback — the actual red/green signal for this class.
 */
class GeneratedClassExclusionTransformTest {

  private NarrativeClassFileTransformer transformer() {
    return new NarrativeClassFileTransformer(
        AgentConfig.parse("packages=ai.narrativetrace.agent.sample"));
  }

  private String withCapturedStderr(Runnable action) {
    var captured = new ByteArrayOutputStream();
    var originalErr = System.err;
    System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
    try {
      action.run();
    } finally {
      System.setErr(originalErr);
    }
    return captured.toString(StandardCharsets.UTF_8);
  }

  private void assertSilentlyExcluded(String internalClassName) {
    byte[][] resultHolder = new byte[1][];
    var stderr =
        withCapturedStderr(
            () ->
                resultHolder[0] =
                    transformer()
                        .transform(
                            getClass().getClassLoader(),
                            internalClassName,
                            null,
                            null,
                            new byte[0]));

    assertThat(resultHolder[0]).as("generated-shape class is not instrumented").isNull();
    assertThat(stderr)
        .as("list 1 exclusions are silent, unlike the generic failure path")
        .isEmpty();
  }

  @Test
  void aByteBuddyGeneratedSubclassIsSilentlyExcluded() {
    assertSilentlyExcluded("ai/narrativetrace/agent/sample/Service$ByteBuddy$1a2b3c4d");
  }

  @Test
  void aJdkDynamicProxyIsSilentlyExcluded() {
    assertSilentlyExcluded("ai/narrativetrace/agent/sample/$Proxy0");
  }

  @Test
  void aLambdaHiddenClassNameIsSilentlyExcluded() {
    assertSilentlyExcluded("ai/narrativetrace/agent/sample/Service$$Lambda$14/0x0000006801003c00");
  }

  @Test
  void aBareHiddenClassSuffixIsSilentlyExcluded() {
    assertSilentlyExcluded("ai/narrativetrace/agent/sample/Service$Outer/0x0000006801006400");
  }

  @Test
  void aMockitoInlineMockSubclassIsSilentlyExcluded() {
    assertSilentlyExcluded("ai/narrativetrace/agent/sample/Service$MockitoMock$12345678");
  }

  @Test
  void aCglibEnhancerSubclassIsSilentlyExcluded() {
    assertSilentlyExcluded("ai/narrativetrace/agent/sample/Service$$EnhancerByCGLIB$$abcdef12");
  }

  /** The regression guard: an ordinary matched class, no generator shape, is still instrumented. */
  @Test
  void anOrdinaryMatchedClassIsStillInstrumented() throws Exception {
    var originalBytes =
        getClass()
            .getClassLoader()
            .getResourceAsStream("ai/narrativetrace/agent/sample/Calculator.class")
            .readAllBytes();

    var result =
        transformer()
            .transform(
                getClass().getClassLoader(),
                "ai/narrativetrace/agent/sample/Calculator",
                null,
                null,
                originalBytes);

    assertThat(result).isNotNull().isNotEqualTo(originalBytes);
  }
}
