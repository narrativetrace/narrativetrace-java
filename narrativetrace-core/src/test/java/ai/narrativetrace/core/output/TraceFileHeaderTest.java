/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.render.NarrativeRenderer;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Every written trace file opens by saying what it is, how to read it, where its failing call is
 * and where the run's index and manifest are — in at most ten lines, in the format's own comment
 * syntax, then the trace exactly as the renderer produced it.
 */
class TraceFileHeaderTest {

  private static final ArtifactIdentity IDENTITY =
      ArtifactIdentity.ofMethod("com.example.OrderServiceTest", "declinedPayment");
  private static final NarrativeRenderer DIAGRAM = tree -> "sequenceDiagram";

  private static TraceTree failingTrace() {
    var failing =
        new TraceNode(
            new MethodSignature("PaymentGateway", "charge", List.of()),
            List.of(),
            new TraceOutcome.Threw(new IllegalStateException("card declined")));
    var root =
        new TraceNode(
            new MethodSignature("OrderService", "placeOrder", List.of()),
            List.of(failing),
            new TraceOutcome.Threw(new IllegalStateException("card declined")));
    return new DefaultTraceTree(List.of(root));
  }

  private static TraceTree passingTrace() {
    return new DefaultTraceTree(
        List.of(
            new TraceNode(
                new MethodSignature("OrderService", "placeOrder", List.of()),
                List.of(),
                new TraceOutcome.Returned("\"order-1\""))));
  }

  private static List<String> write(String format, TraceTree trace, boolean failed, Path dir)
      throws Exception {
    TraceTestSupport.writeTraceFile(
        IDENTITY,
        "declined payment",
        trace,
        failed,
        dir,
        new PrintStream(new java.io.ByteArrayOutputStream()),
        format,
        DIAGRAM,
        DIAGRAM);
    var suffix = TraceTestSupport.extensionForFormat(format);
    return Files.readAllLines(dir.resolve("traces/OrderServiceTest/declined_payment" + suffix));
  }

  /** The 1-based number of the first line of the file containing {@code marker}. */
  private static int lineOf(List<String> lines, String marker) {
    for (var i = 0; i < lines.size(); i++) {
      if (lines.get(i).contains(marker)) {
        return i + 1;
      }
    }
    throw new AssertionError("no line contains " + marker);
  }

  private static int statedLine(String header) {
    var matcher = Pattern.compile("line (\\d+) of this file").matcher(header);
    assertThat(matcher.find()).as(header).isTrue();
    return Integer.parseInt(matcher.group(1));
  }

  @Test
  void aTextTraceOpensWithACommentBlockOfAtMostTenLines(@TempDir Path dir) throws Exception {
    var lines = write("text", failingTrace(), true, dir);

    var header = lines.stream().takeWhile(line -> line.startsWith("# ")).toList();
    assertThat(header).hasSizeBetween(4, 10);
    var text = String.join("\n", header);
    assertThat(text)
        .contains("recorded call tree of one test run: OrderServiceTest.declinedPayment, failed,")
        .contains("indentation is call nesting")
        .contains("[REDACTED]")
        .contains("../../INDEX.md")
        .contains("../../manifest.json");
    assertThat(lines.get(header.size())).doesNotStartWith("# ");
  }

  @Test
  void theFailingCallLineNumberPointsAtTheFirstThrowInTheFileAsWritten(@TempDir Path dir)
      throws Exception {
    var text = write("text", failingTrace(), true, dir);
    var markdown = write("markdown", failingTrace(), true, dir);

    assertThat(statedLine(String.join("\n", text))).isEqualTo(lineOf(text, "!!"));
    assertThat(statedLine(String.join("\n", markdown))).isEqualTo(lineOf(markdown, "❌"));
  }

  @Test
  void aPassingRunSaysNoCallThrew(@TempDir Path dir) throws Exception {
    var lines = write("text", passingTrace(), false, dir);

    assertThat(String.join("\n", lines))
        .contains("OrderServiceTest.declinedPayment, passed,")
        .contains("Failing call: none, no call threw in this run.");
  }

  @Test
  void markdownKeepsItsFrontmatterOnLineOneAndPutsTheHeaderAfterIt(@TempDir Path dir)
      throws Exception {
    var lines = write("markdown", failingTrace(), true, dir);

    assertThat(lines.get(0)).isEqualTo("---");
    var close = lines.subList(1, lines.size()).indexOf("---") + 1;
    assertThat(lines.get(close + 1)).isEqualTo("<!--");
    var end = lines.indexOf("-->");
    assertThat(end - close).isLessThanOrEqualTo(10);
    assertThat(lines.subList(close + 1, end + 1))
        .anyMatch(line -> line.contains("recorded call tree of one test run"));
  }

  @Test
  void theJsonCompanionIsLeftToTheSchemaThatGovernsIt(@TempDir Path dir) throws Exception {
    write("markdown", failingTrace(), true, dir);
    var lines = Files.readAllLines(dir.resolve("traces/OrderServiceTest/declined_payment.json"));

    assertThat(lines.get(0)).isEqualTo("{");
    assertThat(lines.get(1)).isEqualTo("  \"version\": \"1.0\",");
    assertThat(String.join("\n", lines)).doesNotContain("_about");
  }

  @Test
  void diagramFormatsKeepTheirOwnFirstLine(@TempDir Path dir) throws Exception {
    var mermaid = write("mermaid", failingTrace(), true, dir);
    var plantuml = write("plantuml", failingTrace(), true, dir);

    assertThat(mermaid).containsExactly("sequenceDiagram");
    assertThat(plantuml).containsExactly("sequenceDiagram");
  }

  @Test
  void theTraceFollowsTheHeaderUnchanged(@TempDir Path dir) throws Exception {
    var lines = write("text", passingTrace(), false, dir);

    var body = lines.stream().dropWhile(line -> line.startsWith("# ")).toList();
    assertThat(lines).hasSizeGreaterThan(body.size());
    assertThat(body.get(0)).isEqualTo("Scenario: declined payment");
    assertThat(String.join("\n", body)).contains("OrderService.placeOrder() → \"order-1\"");
  }
}
