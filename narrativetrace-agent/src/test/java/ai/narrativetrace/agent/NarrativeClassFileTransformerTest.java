/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NarrativeClassFileTransformerTest {

  @Test
  void returnsNullForNullClassName() {
    var config = AgentConfig.parse("packages=ai.narrativetrace.test");
    var transformer = new NarrativeClassFileTransformer(config);

    var result = transformer.transform(null, null, null, null, new byte[0]);

    assertThat(result).isNull();
  }

  @Test
  void returnsNullForClassOutsideConfiguredPackages() {
    var config = AgentConfig.parse("packages=ai.narrativetrace.test");
    var transformer = new NarrativeClassFileTransformer(config);

    var result = transformer.transform(null, "com/other/SomeClass", null, null, new byte[0]);

    assertThat(result).isNull();
  }

  @Test
  void neverThrowsWhenInstrumentationFails() {
    var config = AgentConfig.parse("packages=ai.narrativetrace.test");
    var transformer = new NarrativeClassFileTransformer(config);
    var notAClassFile = new byte[] {(byte) 0xCA, (byte) 0xFE, 0x00};
    var originalErr = System.err;
    var captured = new java.io.ByteArrayOutputStream();
    System.setErr(new java.io.PrintStream(captured, true, java.nio.charset.StandardCharsets.UTF_8));

    try {
      // The JVM discards transformer exceptions silently; the contract is: warn, never throw,
      // let the class load uninstrumented.
      var result =
          transformer.transform(null, "ai/narrativetrace/test/Broken", null, null, notAClassFile);

      assertThat(result).isNull();
      assertThat(captured.toString(java.nio.charset.StandardCharsets.UTF_8))
          .contains("cannot instrument ai/narrativetrace/test/Broken")
          .contains("loads uninstrumented");
    } finally {
      System.setErr(originalErr);
    }
  }

  @Test
  void returnsTransformedBytesForClassInsideConfiguredPackages() throws Exception {
    var config = AgentConfig.parse("packages=ai.narrativetrace.agent.sample");
    var transformer = new NarrativeClassFileTransformer(config);
    var originalBytes =
        getClass()
            .getClassLoader()
            .getResourceAsStream("ai/narrativetrace/agent/sample/Calculator.class")
            .readAllBytes();

    var result =
        transformer.transform(
            null, "ai/narrativetrace/agent/sample/Calculator", null, null, originalBytes);

    assertThat(result).isNotNull();
    assertThat(result).isNotEqualTo(originalBytes);
  }
}
