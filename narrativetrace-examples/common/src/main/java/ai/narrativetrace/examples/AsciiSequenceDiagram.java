/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.examples;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

/**
 * Renders PlantUML sequence-diagram markup as Unicode text ("ASCII art") for terminal display.
 *
 * <p>INTENT: The demo launcher shows the same captured trace in several renderings; this utility
 * turns the PlantUML markup produced by {@code PlantUmlSequenceDiagramRenderer} into a diagram
 * viewable directly in the console — no image viewer, browser, or terminal-graphics protocol
 * required. Mermaid markup has no equivalent text renderer, which is why the terminal diagram path
 * goes through PlantUML.
 */
public final class AsciiSequenceDiagram {

  private AsciiSequenceDiagram() {}

  /**
   * Renders the given PlantUML markup ({@code @startuml…@enduml}) as Unicode text.
   *
   * @param plantUmlMarkup complete PlantUML source, as produced by the diagrams module
   * @return the diagram drawn with box-drawing characters, one terminal-printable string
   * @throws IllegalArgumentException if the markup is null or blank
   */
  public static String render(String plantUmlMarkup) {
    var out = new ByteArrayOutputStream();
    renderTo(plantUmlMarkup, out);
    return out.toString(StandardCharsets.UTF_8);
  }

  /**
   * Draws the diagram, then hands the finished bytes to the given sink.
   *
   * <p>The diagram is buffered rather than drawn straight into the sink on purpose: PlantUML's
   * {@code generateImage} declares {@code IOException} but swallows failures of the stream it is
   * handed (verified against plantuml-lgpl 1.2024.8), so writing here is what makes a rejected
   * write observable at all instead of silently producing a truncated diagram.
   *
   * @param plantUmlMarkup complete PlantUML source, as produced by the diagrams module
   * @param sink destination for the drawn diagram, written as UTF-8
   * @throws IllegalArgumentException if the markup is null or blank
   * @throws UncheckedIOException if the sink rejects the diagram
   */
  static void renderTo(String plantUmlMarkup, OutputStream sink) {
    if (plantUmlMarkup == null || plantUmlMarkup.isBlank()) {
      throw new IllegalArgumentException("plantUmlMarkup must not be null or blank");
    }
    var reader = new SourceStringReader(plantUmlMarkup);
    var drawn = new ByteArrayOutputStream();
    try {
      reader.generateImage(drawn, new FileFormatOption(FileFormat.UTXT));
      sink.write(drawn.toByteArray());
    } catch (IOException e) {
      throw new UncheckedIOException("PlantUML text rendering failed", e);
    }
  }
}
