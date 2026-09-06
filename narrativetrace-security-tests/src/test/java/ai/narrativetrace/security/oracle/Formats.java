/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.oracle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Well-formedness oracles: each format read back by a real parser for that format.
 *
 * <p>INTENT: "The output looks fine" is not an oracle. A JSON document is well-formed when Jackson
 * parses it and the canonical schema accepts it; a Mermaid diagram is well-formed when every line
 * is a statement the grammar allows; frontmatter is well-formed when SnakeYAML reads it as a
 * mapping. Reading back with the consumer's own parser is the only check that catches an escaper
 * that is merely plausible.
 *
 * <p><b>@llmNote</b> The canonical schema is read from where it lives — {@code
 * narrativetrace-core/src/test/resources/schema/} — rather than copied here. A copy would drift,
 * and the whole point of the schema is that one document defines the artifact for every port.
 * {@code projectDir} is supplied by the build, the same way {@code narrativetrace-build-tests}
 * reaches the source tree.
 */
public final class Formats {

  /** The canonical chapter-tree schema, relative to the repository root. */
  public static final String CHAPTER_TREE_SCHEMA =
      "narrativetrace-core/src/test/resources/schema/chapter-tree.schema.json";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * Statements the Mermaid sequence-diagram renderer emits. Anything else on a line means a value
   * broke out of the message text it was interpolated into.
   */
  private static final Pattern MERMAID_STATEMENT =
      Pattern.compile("^(participant .*|.*->>.*: .*|.*-->>.*: .*|.*-x.*: .*|Note over .*: .*)$");

  private Formats() {}

  /** Parses {@code json}, failing the test with the emitter's name when it does not parse. */
  public static JsonNode parseJson(String emitter, String json) {
    try {
      return MAPPER.readTree(json);
    } catch (IOException e) {
      throw new AssertionError(emitter + " produced JSON no parser accepts: " + e.getMessage(), e);
    }
  }

  /** Validates a chapter-tree document against the canonical schema. */
  public static void validatesAgainstChapterTreeSchema(String emitter, String json) {
    var schema =
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(readSchema());
    var violations = schema.validate(json, InputFormat.JSON);

    assertThat(violations).as("%s violates the canonical chapter-tree schema", emitter).isEmpty();
  }

  /**
   * A Mermaid sequence diagram is well formed when it opens with its keyword and every other line
   * is one statement the grammar allows — no raw line break inside a label, no injected directive.
   */
  public static void isWellFormedMermaid(String emitter, String diagram) {
    assertThat(diagram).as("%s must open the diagram", emitter).startsWith("sequenceDiagram");
    for (var line : statementsOf(diagram)) {
      assertThat(line)
          .as("%s emitted a line the Mermaid grammar does not allow", emitter)
          .matches(MERMAID_STATEMENT);
      assertThat(line.chars().anyMatch(Character::isISOControl))
          .as("%s left a control character inside a diagram line", emitter)
          .isFalse();
    }
  }

  /** The statement lines of a diagram: no blanks, no {@code %%} comments, indentation stripped. */
  public static List<String> statementsOf(String diagram) {
    return diagram
        .lines()
        .skip(1)
        .map(String::strip)
        .filter(line -> !line.isEmpty() && !line.startsWith("%%"))
        .toList();
  }

  /**
   * A PlantUML statement: a participant declaration, an arrow with a message, or an in-flight note.
   *
   * <p><b>@llmNote</b> Added for an adversarial-review finding. Bracketing alone said nothing about
   * what was <em>between</em> the markers, so a forged {@code note over} or an injected {@code
   * !include} preprocessor directive was well-formed by this oracle's standard.
   */
  private static final Pattern PLANTUML_STATEMENT =
      Pattern.compile(
          "^(participant .*|.* -> .*: .*|.* --> .*: .*|.* -\\[#red\\]-> .*: .*"
              + "|hnote over .* : .*)$");

  /**
   * A PlantUML diagram is well formed when it is bracketed by its own markers <em>and</em> every
   * line between them is a statement the renderer meant to write, on one physical line.
   */
  public static void isWellFormedPlantUml(String emitter, String diagram) {
    assertThat(diagram.stripTrailing())
        .as("%s must bracket the diagram", emitter)
        .startsWith("@startuml")
        .endsWith("@enduml");
    for (var line : plantUmlStatementsOf(diagram)) {
      assertThat(line)
          .as("%s emitted a line the PlantUML grammar does not allow", emitter)
          .matches(PLANTUML_STATEMENT);
      assertThat(line.chars().anyMatch(Character::isISOControl))
          .as("%s left a control character inside a diagram line", emitter)
          .isFalse();
    }
  }

  /** The statement lines of a PlantUML diagram: no blanks, no markers, no {@code '} comments. */
  public static List<String> plantUmlStatementsOf(String diagram) {
    return diagram
        .lines()
        .map(String::strip)
        .filter(
            line ->
                !line.isEmpty()
                    && !line.startsWith("@startuml")
                    && !line.startsWith("@enduml")
                    && !line.startsWith("'"))
        .toList();
  }

  /**
   * A sentence the prose renderer can produce: it ends the way that renderer ends a line — a full
   * stop for a statement, a colon for a node that opens a block, a closing parenthesis for the
   * sequential-async hint.
   */
  private static final Pattern PROSE_STATEMENT = Pattern.compile("^.*[.:)]$");

  /**
   * A prose narrative is well formed when every line is one statement the renderer meant to write,
   * on one physical line, with no raw control character in it.
   *
   * <p>INTENT: The formats with a parser had an oracle and the line-oriented ones did not, which is
   * exactly why {@code ProseRenderer} interpolating an exception message with no escaping at all
   * survived a property named "every format", per an adversarial review's threat model. Prose is a
   * shipped format whose entire structure is lines, so a raw {@code \n} in any interpolated field
   * forges a sentence and a raw ESC injects an ANSI sequence into the console it is printed to.
   * Both are checkable without a parser: the sentence terminator is the grammar, and a control
   * character is never legitimate inside a line.
   *
   * <p><b>@llmNote</b> Blank lines are skipped rather than rejected: {@code LossFooter} separates
   * its sentence from the body with one, and that separation is layout, not a statement.
   */
  public static void isWellFormedProse(String emitter, String prose) {
    for (var line : prose.lines().toList()) {
      if (line.isBlank()) {
        continue;
      }
      assertThat(line.chars().anyMatch(Character::isISOControl))
          .as("%s left a control character inside a prose line", emitter)
          .isFalse();
      assertThat(line.strip())
          .as("%s emitted a line the prose renderer does not end", emitter)
          .matches(PROSE_STATEMENT);
    }
  }

  /**
   * A line-oriented text artifact carries no raw control character inside any of its lines.
   *
   * <p>The weaker half of {@link #isWellFormedProse}, for the formats whose line shapes are
   * drawings rather than sentences — the indented tree and the console summary. An ESC or a NUL
   * that reached one of those lines came from an unescaped field, whatever the line looks like.
   */
  public static void carriesNoControlCharacterInAnyLine(String emitter, String text) {
    for (var line : text.lines().toList()) {
      assertThat(line.chars().anyMatch(Character::isISOControl))
          .as("%s left a control character inside a line", emitter)
          .isFalse();
    }
  }

  /**
   * Reads the YAML frontmatter block of a Markdown document.
   *
   * @return the parsed mapping; the test asserts on its keys
   */
  public static Map<String, Object> frontmatterOf(String emitter, String markdown) {
    var block = frontmatterBlock(emitter, markdown);
    var yaml = new Yaml(new LoaderOptions());
    Object parsed;
    try {
      parsed = yaml.load(block);
    } catch (RuntimeException e) {
      throw new AssertionError(
          emitter + " produced frontmatter no YAML parser accepts: " + e.getMessage(), e);
    }
    assertThat(parsed).as("%s frontmatter must be a mapping", emitter).isInstanceOf(Map.class);
    @SuppressWarnings("unchecked")
    var mapping = (Map<String, Object>) parsed;
    return mapping;
  }

  /** The text between the opening and closing {@code ---} fences. */
  private static String frontmatterBlock(String emitter, String markdown) {
    assertThat(markdown).as("%s must open with a frontmatter fence", emitter).startsWith("---\n");
    var end = markdown.indexOf("\n---", "---\n".length());
    assertThat(end).as("%s left the frontmatter block unterminated", emitter).isNotNegative();
    return markdown.substring("---\n".length(), end);
  }

  /** Every artifact whose name ends in {@code .json} parses, whichever writer produced it. */
  public static void everyJsonArtifactParses(Map<String, String> outputs) {
    outputs.forEach(
        (emitter, output) -> {
          if (emitter.endsWith(".json") || "renderer:json".equals(emitter)) {
            assertThatCode(() -> parseJson(emitter, output)).doesNotThrowAnyException();
          }
        });
  }

  /**
   * The document's shape with every scalar's <em>content</em> erased: field names, array lengths
   * and node kinds only.
   *
   * <p>INTENT: This is the AI-consumer oracle's comparison. A captured value that stayed one value
   * produces the same shape whatever it contained; a value that broke out of its string produces a
   * different one — an extra field, an extra element, a different node kind. Comparing shapes says
   * "exactly one value" without the test having to guess where in the document that value sits.
   */
  public static String jsonShape(JsonNode node) {
    var sb = new StringBuilder();
    appendShape(node, sb);
    return sb.toString();
  }

  private static void appendShape(JsonNode node, StringBuilder sb) {
    if (node.isObject()) {
      sb.append('{');
      node.fieldNames()
          .forEachRemaining(
              name -> {
                sb.append(name).append(':');
                appendShape(node.get(name), sb);
                sb.append(',');
              });
      sb.append('}');
    } else if (node.isArray()) {
      sb.append('[');
      node.forEach(element -> appendShape(element, sb));
      sb.append(']');
    } else {
      sb.append(node.getNodeType());
    }
  }

  /**
   * Every string node under {@code node} whose field is {@code fieldName}, in document order.
   *
   * <p>Used to read a captured value back out of the document a parser produced, which is the
   * strongest form of "it came back as exactly one value": the bytes match, and they are one node.
   */
  public static List<String> stringsNamed(JsonNode node, String fieldName) {
    var found = new java.util.ArrayList<String>();
    collectStrings(node, fieldName, found);
    return List.copyOf(found);
  }

  private static void collectStrings(JsonNode node, String fieldName, List<String> found) {
    if (node.isObject()) {
      node.fields()
          .forEachRemaining(
              entry -> {
                if (fieldName.equals(entry.getKey()) && entry.getValue().isTextual()) {
                  found.add(entry.getValue().asText());
                }
                collectStrings(entry.getValue(), fieldName, found);
              });
    } else if (node.isArray()) {
      node.forEach(element -> collectStrings(element, fieldName, found));
    }
  }

  /** How many fenced-code delimiters a Markdown document carries. */
  public static long fenceCount(String markdown) {
    return markdown.lines().filter(line -> line.strip().startsWith("```")).count();
  }

  /** How many frontmatter fences a Markdown document carries: exactly two, or it is broken. */
  public static long frontmatterFenceCount(String markdown) {
    return markdown.lines().filter(line -> "---".equals(line.strip())).count();
  }

  private static String readSchema() {
    var root = System.getProperty("projectDir");
    assertThat(root).as("the build must supply projectDir").isNotNull();
    var path = Path.of(root, CHAPTER_TREE_SCHEMA);
    assertThat(path).as("the canonical schema has moved; update Formats").exists();
    try {
      return Files.readString(path);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
