/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.render;

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
 * Trace <em>metadata</em> reaches the text renderers unescaped, where rendered values do not.
 *
 * <p>INTENT: The 2026-09-02 adversarial audit (finding 4) showed {@code MARKDOWN_HAS_RAW_METADATA_
 * NEWLINE} and {@code INDENTED_HAS_RAW_METADATA_NEWLINE}. Exception <em>messages</em> were already
 * routed through {@link MarkdownEscape} and {@link ControlEscape} — because those were known to
 * echo user input — but the class name, method name and parameter names beside them were not, and
 * {@code MethodSignature} is a public record with no validation on any of them.
 *
 * <p><b>@llmNote</b> All three renderers are line-oriented: one node is one line, one sentence or
 * one heading, so a raw newline in a name forges an entry that a reader, a log parser or an AI
 * agent consuming the narrative cannot tell from a real one.
 *
 * <p><b>@edgeCase</b> {@link ProseRenderer} was outside this test until 2026-09-04 and escaped
 * <em>nothing</em> — not the names, not the narration, not even the exception message its two
 * siblings had folded since the audit. It is here now, held to the same standard, which is what
 * lets the security suite's well-formedness oracle be pointed at prose at all.
 */
class RendererMetadataEscapingTest {

  private static final String FORGED_ENTRY = "Svc\ncall OtherService.transferFunds() ok";
  private static final String ANSI = "Svc\u001b[31mred";
  private static final String HTML = "Svc<img src=x onerror=alert(1)>";
  private static final String MARKDOWN = "Svc\n## Forged heading\n";

  private final MarkdownRenderer markdown = new MarkdownRenderer();
  private final IndentedTextRenderer indented = new IndentedTextRenderer();
  private final ProseRenderer prose = new ProseRenderer();

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

  static Stream<Arguments> hostileMetadata() {
    return Stream.of(
        Arguments.of("className forges an entry", FORGED_ENTRY, "m", "p"),
        Arguments.of("className carries ANSI", ANSI, "m", "p"),
        Arguments.of("className carries HTML", HTML, "m", "p"),
        Arguments.of("className forges a heading", MARKDOWN, "m", "p"),
        Arguments.of("methodName forges an entry", "C", FORGED_ENTRY, "p"),
        Arguments.of("methodName carries ANSI", "C", ANSI, "p"),
        Arguments.of("parameterName forges an entry", "C", "m", FORGED_ENTRY),
        Arguments.of("parameterName carries ANSI", "C", "m", ANSI),
        Arguments.of("every field at once", FORGED_ENTRY, MARKDOWN, ANSI));
  }

  @ParameterizedTest(name = "indented text: {0}")
  @MethodSource("hostileMetadata")
  void indentedTextNeverEmitsARawControlCharacterFromMetadata(
      String field, String className, String methodName, String paramName) {
    var rendered = indented.render(treeWith(className, methodName, paramName));

    assertNoRawControlPerLine(rendered);
    assertThat(rendered.split("\n", -1)).as("one node is one line: %s", rendered).hasSize(1);
  }

  @ParameterizedTest(name = "markdown: {0}")
  @MethodSource("hostileMetadata")
  void markdownNeverEmitsARawControlCharacterFromMetadata(
      String field, String className, String methodName, String paramName) {
    var rendered = markdown.render(treeWith(className, methodName, paramName));

    assertNoRawControlPerLine(rendered);
    for (var line : rendered.split("\n", -1)) {
      assertThat(line.stripLeading())
          .as("a forged Markdown heading")
          .doesNotStartWith("## Forged heading");
    }
  }

  @ParameterizedTest(name = "prose: {0}")
  @MethodSource("hostileMetadata")
  void proseNeverEmitsARawControlCharacterFromMetadata(
      String field, String className, String methodName, String paramName) {
    var rendered = prose.render(treeWith(className, methodName, paramName));

    assertNoRawControlPerLine(rendered);
    assertThat(rendered.split("\n", -1)).as("one node is one sentence: %s", rendered).hasSize(1);
  }

  @Test
  @DisplayName("prose escapes the narration an author wrote around user data")
  void proseNeverEmitsARawControlCharacterFromNarration() {
    var node =
        new TraceNode(
            new MethodSignature(
                "Svc", "call", List.of(), "charging\nforged: ok", null, "charging {card}"),
            List.of(),
            new TraceOutcome.Returned("\"ok\"", null),
            1_000_000L);

    var rendered = prose.render(new DefaultTraceTree(List.of(node)));

    assertNoRawControlPerLine(rendered);
    assertThat(rendered.split("\n", -1)).hasSize(1);
  }

  @Test
  @DisplayName("markdown neutralises active HTML in a class name, as it does in a message")
  void markdownNeutralisesActiveHtmlInAClassName() {
    var rendered = markdown.render(treeWith(HTML, "m", "p"));

    assertThat(rendered).doesNotContain("<img src=x onerror=alert(1)>");
    assertThat(rendered).contains("&lt;img src=x onerror=alert(1)&gt;");
  }

  @Test
  @DisplayName("a hostile exception type name cannot forge a line in either renderer")
  void aHostileExceptionTypeNameCannotForgeALine() {
    var node =
        new TraceNode(
            new MethodSignature("Svc", "call", List.of()),
            List.of(),
            new TraceOutcome.Threw(new RuntimeException("boom\nforged: ok")),
            1_000_000L);
    var tree = new DefaultTraceTree(List.of(node));

    assertNoRawControlPerLine(indented.render(tree));
    assertNoRawControlPerLine(markdown.render(tree));
    assertNoRawControlPerLine(prose.render(tree));
  }

  @Test
  @DisplayName("ordinary identifiers render exactly as before")
  void ordinaryIdentifiersRenderUnchanged() {
    var tree = treeWith("OrderService", "placeOrder", "orderId");

    assertThat(indented.render(tree)).contains("OrderService.placeOrder(orderId: ");
    assertThat(markdown.render(tree)).contains("OrderService").contains("placeOrder");
    assertThat(prose.render(tree)).contains("The order service place order for orderId: ");
  }

  private void assertNoRawControlPerLine(String rendered) {
    for (var line : rendered.split("\n", -1)) {
      assertThat(line.chars().anyMatch(Character::isISOControl))
          .as("a raw control character inside the line: %s", line)
          .isFalse();
    }
  }
}
