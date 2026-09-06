/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;

class AsciiSequenceDiagramTest {

  @Test
  void rendersSequenceDiagramMarkupAsUnicodeText() {
    var markup =
        """
        @startuml
        participant OrderService
        participant PaymentService
        OrderService -> PaymentService: charge(amount)
        PaymentService --> OrderService: PaymentConfirmation
        @enduml
        """;

    var ascii = AsciiSequenceDiagram.render(markup);

    assertThat(ascii)
        .contains("OrderService")
        .contains("PaymentService")
        .contains("charge(amount)")
        .contains("PaymentConfirmation");
    assertThat(ascii).as("participants are drawn as boxes").contains("─");
  }

  @Test
  void rejectsBlankMarkup() {
    assertThatThrownBy(() -> AsciiSequenceDiagram.render("  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("plantUmlMarkup");
  }

  @Test
  void reportsAFailingSinkAsAnUncheckedIoException() {
    var failingSink =
        new OutputStream() {
          @Override
          public void write(int b) throws IOException {
            throw new IOException("sink refused the diagram");
          }
        };

    assertThatThrownBy(
            () -> AsciiSequenceDiagram.renderTo("@startuml\nA -> B: x\n@enduml", failingSink))
        .isInstanceOf(UncheckedIOException.class)
        .hasMessageContaining("PlantUML text rendering failed");
  }
}
