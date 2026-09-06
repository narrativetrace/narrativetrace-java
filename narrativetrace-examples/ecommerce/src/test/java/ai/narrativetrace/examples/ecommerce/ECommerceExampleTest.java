/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples.ecommerce;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;

class ECommerceExampleTest {

  @Test
  void mainRunsAllScenariosWithoutError() {
    var out = new ByteArrayOutputStream();
    var original = System.out;
    System.setOut(new PrintStream(out));
    try {
      ECommerceExample.main(new String[] {});
    } finally {
      System.setOut(original);
    }

    var output = out.toString();
    assertThat(output).contains("Scenario 1: Successful Order");
    assertThat(output).contains("Scenario 2: Payment Failure");
    assertThat(output).contains("Scenario 3: Flaky External Service");
    assertThat(output).contains("Scenario 4: Unknown Customer");
    assertThat(output).contains("Scenario 5: Out of Stock");
    assertThat(output).contains("InventoryService.reserve");
    assertThat(output).contains("sequenceDiagram");
    assertThat(output)
        .as("every rendering of the captured trace is announced as its own section")
        .contains("--- Trace tree ---");
    assertThat(output)
        .as("the ASCII sequence diagram section renders in the console via PlantUML")
        .contains("--- Sequence diagram (ASCII) ---");
    assertThat(output)
        .as("scenario 6 explains why it prints two separate trees")
        .contains("Traces are thread-scoped");
  }

  @Test
  void classCanBeInstantiated() {
    var example = new ECommerceExample();
    assertThat(example).isNotNull();
  }
}
