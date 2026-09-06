/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.diagrams;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Diagram metadata is attacker-reachable, and the diagram grammars are line-oriented.
 *
 * <p>INTENT: An adversarial review built a {@link MethodSignature} whose class name carried a quote
 * and a newline, and watched both renderers emit a forged participant, a Mermaid {@code click}
 * interaction pointing at an attacker URL, and a forged PlantUML note. The quoting helper only
 * wrapped names containing {@code . - :} or a space, and escaped nothing, so every name was a
 * breakout.
 *
 * <p><b>@llmNote</b> {@code MethodSignature} is a public record with no validation, and trace trees
 * are built by public API, deserialized from JSON, and post-processed by integrations. "The proxy
 * only ever produces Java identifiers" is true and irrelevant — the public trace model is wider
 * than the proxy path.
 */
class DiagramMetadataInjectionTest {

  private static final String QUOTE_AND_BREAK =
      "Victim\"\nparticipant InjectedActor\nclick InjectedActor href \"https://attacker.example\"";
  private static final String NOTE_FORGERY = "Victim\nnote over Victim: forged\n";
  private static final String CARRIAGE = "Victim\rparticipant InjectedActor";
  private static final String NUL = "Victim\u0000participant InjectedActor";

  private final MermaidSequenceDiagramRenderer mermaid = new MermaidSequenceDiagramRenderer();
  private final PlantUmlSequenceDiagramRenderer plantUml = new PlantUmlSequenceDiagramRenderer();

  private static DefaultTraceTree treeWith(String className, String methodName, String paramName) {
    var node =
        new TraceNode(
            new MethodSignature(
                className, methodName, List.of(new ParameterCapture(paramName, "\"v\"", false))),
            List.of(),
            new TraceOutcome.Returned("\"ok\"", null),
            1_000_000L);
    return new DefaultTraceTree(List.of(node));
  }

  static Stream<Arguments> hostileFields() {
    return Stream.of(
        Arguments.of("className quote+break", QUOTE_AND_BREAK, "m", "p"),
        Arguments.of("className note forgery", NOTE_FORGERY, "m", "p"),
        Arguments.of("className carriage return", CARRIAGE, "m", "p"),
        Arguments.of("className NUL", NUL, "m", "p"),
        Arguments.of("methodName quote+break", "C", QUOTE_AND_BREAK, "p"),
        Arguments.of("methodName note forgery", "C", NOTE_FORGERY, "p"),
        Arguments.of("parameterName quote+break", "C", "m", QUOTE_AND_BREAK),
        Arguments.of("parameterName note forgery", "C", "m", NOTE_FORGERY),
        Arguments.of("every field at once", QUOTE_AND_BREAK, NOTE_FORGERY, CARRIAGE));
  }

  @ParameterizedTest(name = "mermaid: {0}")
  @MethodSource("hostileFields")
  void mermaidNeverEmitsAForgedLineFromMetadata(
      String field, String className, String methodName, String paramName) {
    var tree = treeWith(className, methodName, paramName);

    assertNoInjection(mermaid.render(tree), "sequenceDiagram", MERMAID_LINES);
    assertNoInjection(mermaid.renderWithAliases(tree), "sequenceDiagram", MERMAID_LINES);
  }

  @ParameterizedTest(name = "plantuml: {0}")
  @MethodSource("hostileFields")
  void plantUmlNeverEmitsAForgedLineFromMetadata(
      String field, String className, String methodName, String paramName) {
    assertNoInjection(
        plantUml.render(treeWith(className, methodName, paramName)), "@startuml", PLANTUML_LINES);
  }

  /** One participant, one call arrow, one return arrow — plus the format's own framing lines. */
  private static final int MERMAID_LINES = 4;

  private static final int PLANTUML_LINES = 5;

  /**
   * The invariant that actually matters: hostile metadata may appear as inert text inside a name,
   * but it may not become <em>structure</em>.
   *
   * <p><b>@llmNote</b> Asserting that the output does not <em>contain</em> the payload would be the
   * wrong test — the payload is the class name, so of course it is printed. Injection means an
   * extra line, an unbalanced quote, or a directive the renderer never wrote. Those are what is
   * asserted here, and a line count is the sharpest of the three: every breakout the audit
   * demonstrated added lines.
   */
  private void assertNoInjection(String diagram, String header, int expectedLines) {
    seenParticipant.set(false);
    assertThat(diagram).startsWith(header);

    var lines = diagram.split("\n", -1);
    assertThat(lines)
        .as("hostile metadata must not add a diagram line: %s", diagram)
        .hasSize(expectedLines);

    for (var line : lines) {
      var trimmed = line.stripLeading();
      assertThat(trimmed).as("a forged Mermaid interaction").doesNotStartWith("click ");
      assertThat(trimmed).as("a forged note").doesNotStartWith("note over");
      assertThat(trimmed).as("a PlantUML preprocessor directive").doesNotStartWith("!");
      assertThat(trimmed)
          .as("a forged participant declaration")
          .satisfies(
              t -> assertThat(t.startsWith("participant ")).isEqualTo(isIntendedParticipant(t)));
      assertThat(line.chars().anyMatch(Character::isISOControl))
          .as("a raw control character inside the line: %s", line)
          .isFalse();
    }

    var quotes = diagram.chars().filter(c -> c == '"').count();
    assertThat(quotes % 2).as("every quote must be part of a pair").isZero();
  }

  /** The renderers emit exactly one participant line per diagram in these fixtures. */
  private boolean isIntendedParticipant(String trimmedLine) {
    return trimmedLine.startsWith("participant ") && !seenParticipant.getAndSet(true);
  }

  private final java.util.concurrent.atomic.AtomicBoolean seenParticipant =
      new java.util.concurrent.atomic.AtomicBoolean();

  @Test
  @DisplayName("ordinary Java identifiers still render exactly as before")
  void ordinaryIdentifiersRenderUnchanged() {
    var tree = treeWith("OrderService", "placeOrder", "orderId");

    assertThat(mermaid.render(tree))
        .contains("participant OrderService")
        .contains("OrderService->>OrderService: placeOrder(orderId)");
    assertThat(plantUml.render(tree))
        .contains("participant OrderService")
        .contains("OrderService -> OrderService: placeOrder(orderId)");
  }

  @Test
  @DisplayName("a dotted class name is still quoted, as it always was")
  void aDottedClassNameIsStillQuoted() {
    var tree = treeWith("com.acme.OrderService", "placeOrder", "orderId");

    assertThat(mermaid.render(tree)).contains("participant \"com.acme.OrderService\"");
    assertThat(plantUml.render(tree)).contains("participant \"com.acme.OrderService\"");
  }
}
