/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.conformance;

import static org.assertj.core.api.Assertions.assertThat;

import ai.narrativetrace.api.annotation.NotTraced;
import ai.narrativetrace.api.event.MethodSignature;
import ai.narrativetrace.api.event.ParameterCapture;
import ai.narrativetrace.api.event.TraceNode;
import ai.narrativetrace.api.event.TraceOutcome;
import ai.narrativetrace.api.tree.TraceTree;
import ai.narrativetrace.core.output.TraceTestSupport;
import ai.narrativetrace.core.render.ValueRenderer;
import ai.narrativetrace.core.template.TemplateParser;
import ai.narrativetrace.core.tree.DefaultTraceTree;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the containment claim end to end: a redacted record component wrapped in an {@link
 * Optional} reaches <em>no</em> artifact NarrativeTrace writes.
 *
 * <p>INTENT: {@code ValueRendererWrapperTest} pins the renderer; this pins the product. Capture is
 * eager (values become strings at {@link ValueRenderer}, never later), so every downstream emitter
 * — the markdown narrative, the console report, the chapter JSON, the structural {@code .nt}, the
 * canonical entries — receives whatever that one call produced. Running all of them over a real
 * temp directory and asserting the secret appears in no byte of it is the only assertion that
 * covers the emitters nobody thought to name.
 *
 * <p><b>@llmNote</b> Deliberately placed outside {@code core.render}: the leak was found by a user
 * reading output, not by a renderer unit test, and a same-package test would have been free to
 * reach for package-private detail instead of the shipped path.
 */
class RedactedValueContainmentTest {

  private static final String SECRET = "cvv-4242-not-for-logs";

  /**
   * The dogfood shape: a repository returns {@code Optional<Card>}, and {@code cvv} is redacted.
   */
  record Card(String number, @NotTraced String cvv) {}

  @Test
  void aRedactedComponentInsideAnOptionalReachesNoWrittenArtifact(@TempDir Path dir)
      throws IOException {
    var renderer = new ValueRenderer();
    var returned = renderer.render(Optional.of(new Card("4111", SECRET)));
    var argument = renderer.render(Optional.of(new Card("4000", SECRET)));

    writeEveryArtifact(treeReturning(returned, argument), dir);

    assertThat(everyWrittenByte(dir))
        .as("eager capture means one render() call feeds every emitter — none may hold the secret")
        .isNotEmpty()
        .noneMatch(content -> content.contains(SECRET));
  }

  @Test
  void theRedactionMarkerIsWhatTheNarrativeShowsInstead(@TempDir Path dir) throws IOException {
    var returned = new ValueRenderer().render(Optional.of(new Card("4111", SECRET)));

    writeEveryArtifact(treeReturning(returned, "\"none\""), dir);

    assertThat(everyWrittenByte(dir))
        .as("silence would also pass the containment assertion; the reader must see the redaction")
        .anyMatch(content -> content.contains("[REDACTED]"));
  }

  @Test
  void aRedactedComponentInsideAnAtomicReferenceArrayReachesNoWrittenArtifact(@TempDir Path dir)
      throws IOException {
    var renderer = new ValueRenderer();
    var returned =
        renderer.render(new AtomicReferenceArray<>(new Object[] {new Card("4111", SECRET)}));
    var argument =
        renderer.render(new AtomicReferenceArray<>(new Object[] {new Card("4000", SECRET)}));

    writeEveryArtifact(treeReturning(returned, argument), dir);

    assertThat(everyWrittenByte(dir))
        .as("an AtomicReferenceArray prints Arrays.toString over raw elements if left unopened")
        .isNotEmpty()
        .noneMatch(content -> content.contains(SECRET));
  }

  @Test
  void aRedactedComponentInsideAStandaloneEntryReachesNoWrittenArtifact(@TempDir Path dir)
      throws IOException {
    var renderer = new ValueRenderer();
    var returned = renderer.render(Map.entry("issued", new Card("4111", SECRET)));
    var argument = renderer.render(Map.entry(new Card("4000", SECRET), "requested"));

    writeEveryArtifact(treeReturning(returned, argument), dir);

    assertThat(everyWrittenByte(dir))
        .as("a lone Map.Entry prints key=value over raw payloads if left unopened")
        .isNotEmpty()
        .noneMatch(content -> content.contains(SECRET));
  }

  /**
   * A narration is prose the author wrote, so it travels into every artifact without passing
   * through {@link ValueRenderer} at all. Naming a redacted path is the one way that prose can
   * carry a value redaction removed everywhere else.
   */
  @Test
  void aNarrationTemplateNamingARedactedPathReachesNoWrittenArtifact(@TempDir Path dir)
      throws IOException {
    var narration =
        TemplateParser.resolve(
            "charging {card.cvv} for {card.number}", Map.of("card", new Card("4111", SECRET)));

    writeEveryArtifact(treeNarrating(narration), dir);

    assertThat(everyWrittenByte(dir))
        .as("a template path is not a licence to print what redaction hides")
        .isNotEmpty()
        .noneMatch(content -> content.contains(SECRET));
    assertThat(narration).isEqualTo("charging [REDACTED] for 4111");
  }

  /**
   * The other spelling of the template case, and the more common one: {@code {card}} names the
   * object, not a path into it, so the object's own {@code toString()} decided what got printed and
   * knew nothing about {@code @NotTraced}.
   */
  @Test
  void aNarrationTemplateNamingTheWholeObjectReachesNoWrittenArtifact(@TempDir Path dir)
      throws IOException {
    var narration =
        TemplateParser.resolve("charging {card}", Map.of("card", new Card("4111", SECRET)));

    writeEveryArtifact(treeNarrating(narration), dir);

    assertThat(everyWrittenByte(dir))
        .as("naming the object is not a licence to print what redaction hides")
        .isNotEmpty()
        .noneMatch(content -> content.contains(SECRET));
    assertThat(narration).contains("[REDACTED]").contains("4111");
  }

  /**
   * A class that prints its own secret. The sibling TypeScript port leaked exactly here: {@code
   * toString()} short-circuits the introspection that honors {@code @NotTraced}, so the annotation
   * silently did nothing for any class that had one.
   */
  static class ChattyCard {
    final String number;
    @NotTraced final String cvv;

    ChattyCard(String number, String cvv) {
      this.number = number;
      this.cvv = cvv;
    }

    @Override
    public String toString() {
      return "ChattyCard{number=" + number + ", cvv=" + cvv + "}";
    }
  }

  @Test
  void aRedactedFieldOfAClassThatPrintsItselfReachesNoWrittenArtifact(@TempDir Path dir)
      throws IOException {
    var renderer = new ValueRenderer();
    var returned = renderer.render(new ChattyCard("4111", SECRET));
    var argument = renderer.render(Optional.of(new ChattyCard("4000", SECRET)));

    writeEveryArtifact(treeReturning(returned, argument), dir);

    assertThat(everyWrittenByte(dir))
        .as("a curated toString() is a better rendering, never a licence to print a redaction")
        .isNotEmpty()
        .noneMatch(content -> content.contains(SECRET));
    assertThat(returned).contains("[REDACTED]").contains("4111");
  }

  @Test
  void aNarrationTemplateNamingAnObjectThatPrintsItselfReachesNoWrittenArtifact(@TempDir Path dir)
      throws IOException {
    var narration =
        TemplateParser.resolve("charging {card}", Map.of("card", new ChattyCard("4111", SECRET)));

    writeEveryArtifact(treeNarrating(narration), dir);

    assertThat(everyWrittenByte(dir))
        .as("templates render through the same renderer, so the seam must be closed for them too")
        .isNotEmpty()
        .noneMatch(content -> content.contains(SECRET));
    assertThat(narration).contains("[REDACTED]").contains("4111");
  }

  @Test
  void anEmptyOptionalReachesTheArtifactsAsTheAbsentMarker(@TempDir Path dir) throws IOException {
    var returned = new ValueRenderer().render(Optional.empty());

    writeEveryArtifact(treeReturning(returned, "\"none\""), dir);

    assertThat(everyWrittenByte(dir)).anyMatch(content -> content.contains("<empty>"));
  }

  private static TraceTree treeNarrating(String narration) {
    var node =
        new TraceNode(
            new MethodSignature(
                "PaymentService", "charge", List.of(), narration, null, "charging {card.cvv}"),
            List.of(),
            new TraceOutcome.Returned("true"),
            42_000_000L);
    return new DefaultTraceTree(List.of(node));
  }

  private static TraceTree treeReturning(String renderedReturn, String renderedArgument) {
    var node =
        new TraceNode(
            new MethodSignature(
                "CardRepository",
                "findByNumber",
                List.of(new ParameterCapture("probe", renderedArgument, false))),
            List.of(),
            new TraceOutcome.Returned(renderedReturn),
            42_000_000L);
    return new DefaultTraceTree(List.of(node));
  }

  /**
   * Every writer the product ships, over one temp directory: markdown, chapter, structural,
   * canonical.
   */
  private static void writeEveryArtifact(TraceTree trace, Path dir) throws IOException {
    var console = new ByteArrayOutputStream();
    TraceTestSupport.writeTraceFile(
        "com.example.CardRepositoryTest",
        "findsACard",
        "finds a card",
        trace,
        false,
        dir,
        new PrintStream(console, true, StandardCharsets.UTF_8),
        "markdown",
        tree -> "graph TD",
        tree -> "@startuml\n@enduml");
    TraceTestSupport.writeCanonicalTraceFile(
        "com.example.CardRepositoryTest", "findsACard", trace, dir);
    TraceTestSupport.writeStructuralTraceFile(
        "com.example.CardRepositoryTest", "findsACard", trace, dir);
    Files.writeString(dir.resolve("console.txt"), console.toString(StandardCharsets.UTF_8));
  }

  private static List<String> everyWrittenByte(Path dir) throws IOException {
    try (Stream<Path> files = Files.walk(dir)) {
      return files.filter(Files::isRegularFile).map(RedactedValueContainmentTest::read).toList();
    }
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new IllegalStateException("unreadable artifact: " + file, e);
    }
  }
}
