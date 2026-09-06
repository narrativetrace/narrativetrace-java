/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.minecraft;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;

class MinecraftExampleTest {

  @Test
  void mainRunsBothRefactoredAndUnrefactoredScenarios() {
    var out = new ByteArrayOutputStream();
    var original = System.out;
    System.setOut(new PrintStream(out));
    try {
      MinecraftExample.main(new String[] {});
    } finally {
      System.setOut(original);
    }

    var output = out.toString();
    assertThat(output).contains("Refactored: Player Joins World");
    assertThat(output).contains("Unrefactored: Player Joins World");
    assertThat(output).contains("WorldServer.playerJoined");
    assertThat(output).contains("GameManager.handle");
    assertThat(output).contains("sequenceDiagram");
    assertThat(output)
        .as("tree renderings are announced as their own section")
        .contains("--- Trace tree ---");
    assertThat(output)
        .as("the refactored trace is also drawn as an ASCII sequence diagram")
        .contains("--- Sequence diagram (ASCII) ---");
  }

  @Test
  void classCanBeInstantiated() {
    var example = new MinecraftExample();
    assertThat(example).isNotNull();
  }
}
