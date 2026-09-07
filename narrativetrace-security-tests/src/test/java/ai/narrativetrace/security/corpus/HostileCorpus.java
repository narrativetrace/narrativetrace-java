/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.security.corpus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Reader for the shared hostile corpus in {@code src/test/resources/hostile-corpus/}.
 *
 * <p>INTENT: One loader, so every property and every Jazzer seed writer reads the same fixtures the
 * same way. The corpus is the cross-runtime artifact — every runtime copies the JSON verbatim and
 * reimplements only this reader and {@link HostileGraphs} — so nothing Java-specific may leak into
 * the files.
 *
 * <p><b>@llmNote</b> The fixtures are ASCII: every hostile character is a {@code \\uXXXX} escape
 * that the JSON parser turns back into the real code point here. That is deliberate — a corpus
 * carrying raw C0 bytes is unreadable in a diff and gets silently normalised by editors, which is
 * exactly how a case stops testing what it was written for.
 *
 * <p><b>@edgeCase</b> A case may declare {@code repeat} instead of {@code value}, which is how a 1
 * MiB input lives in a 6 kB fixture. {@code prefix} and {@code suffix} bracket the repetition.
 */
public final class HostileCorpus {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String DIRECTORY = "hostile-corpus/";

  private static final java.util.Map<String, JsonNode> PARSED =
      new java.util.concurrent.ConcurrentHashMap<>();
  private static final java.util.Map<String, List<?>> CASES =
      new java.util.concurrent.ConcurrentHashMap<>();

  private HostileCorpus() {}

  /** Hostile scalar values, for the renderer and every output format. */
  @SuppressWarnings("unchecked")
  public static List<CorpusCase> strings() {
    return (List<CorpusCase>)
        CASES.computeIfAbsent("strings", key -> cases(read("strings.json").get("cases")));
  }

  /** Test class and method names, for the artifact writers that turn one into a path. */
  @SuppressWarnings("unchecked")
  public static List<CorpusCase> names() {
    return (List<CorpusCase>)
        CASES.computeIfAbsent("names", key -> cases(read("names.json").get("cases")));
  }

  /** Prompt-injection payloads, for the AI-consumer containment oracle. */
  @SuppressWarnings("unchecked")
  public static List<CorpusCase> injections() {
    return (List<CorpusCase>)
        CASES.computeIfAbsent("injections", key -> cases(read("injection.json").get("cases")));
  }

  /** W3C {@code traceparent} header values, each saying whether the parser must accept it. */
  @SuppressWarnings("unchecked")
  public static List<HeaderCase> traceparents() {
    return (List<HeaderCase>)
        CASES.computeIfAbsent(
            "traceparent", key -> headers(read("headers.json").get("traceparent")));
  }

  /** W3C {@code tracestate} header values. */
  @SuppressWarnings("unchecked")
  public static List<HeaderCase> tracestates() {
    return (List<HeaderCase>)
        CASES.computeIfAbsent("tracestate", key -> headers(read("headers.json").get("tracestate")));
  }

  /** {@code @Narrated}/{@code @OnError} template strings. */
  @SuppressWarnings("unchecked")
  public static List<TemplateCase> templates() {
    return (List<TemplateCase>) CASES.computeIfAbsent("templates", key -> parseTemplates());
  }

  private static List<TemplateCase> parseTemplates() {
    var result = new ArrayList<TemplateCase>();
    for (var node : read("templates.json").get("cases")) {
      result.add(
          new TemplateCase(
              text(node, "id"),
              text(node, "description"),
              materialize(node, "template"),
              text(node, "values"),
              text(node, "expect")));
    }
    return List.copyOf(result);
  }

  /** Sensitive field names and national-id value shapes, each declaring which way it must go. */
  @SuppressWarnings("unchecked")
  public static List<RedactionCase> redactions() {
    return (List<RedactionCase>) CASES.computeIfAbsent("redaction", key -> parseRedactions());
  }

  private static List<RedactionCase> parseRedactions() {
    var result = new ArrayList<RedactionCase>();
    for (var node : read("redaction.json").get("cases")) {
      result.add(
          new RedactionCase(
              text(node, "id"),
              text(node, "description"),
              text(node, "name"),
              text(node, "value"),
              text(node, "canary"),
              text(node, "expect")));
    }
    return List.copyOf(result);
  }

  /** Declarative object-graph shapes; {@link HostileGraphs} turns one into a live graph. */
  @SuppressWarnings("unchecked")
  public static List<GraphCase> graphs() {
    return (List<GraphCase>) CASES.computeIfAbsent("graphs", key -> parseGraphs());
  }

  private static List<GraphCase> parseGraphs() {
    var result = new ArrayList<GraphCase>();
    for (var node : read("graphs.json").get("cases")) {
      result.add(graphCase(node));
    }
    return List.copyOf(result);
  }

  /** Declarative {@code TraceNode} call-tree shapes; {@link TraceShapes} turns one into a tree. */
  @SuppressWarnings("unchecked")
  public static List<TraceShapeCase> traceShapes() {
    return (List<TraceShapeCase>) CASES.computeIfAbsent("trace-shapes", key -> parseTraceShapes());
  }

  private static List<TraceShapeCase> parseTraceShapes() {
    var result = new ArrayList<TraceShapeCase>();
    for (var node : read("trace-shapes.json").get("cases")) {
      result.add(
          new TraceShapeCase(
              text(node, "id"),
              text(node, "description"),
              text(node, "kind"),
              node.path("n").asInt(0)));
    }
    return List.copyOf(result);
  }

  private static GraphCase graphCase(JsonNode node) {
    var layers = new ArrayList<String>();
    if (node.has("layers")) {
      node.get("layers").forEach(layer -> layers.add(layer.asText()));
    }
    return new GraphCase(
        text(node, "id"),
        text(node, "description"),
        text(node, "kind"),
        List.copyOf(layers),
        text(node, "layer"),
        text(node, "container"),
        text(node, "member"),
        text(node, "state"),
        text(node, "payload"),
        node.path("n").asInt(0));
  }

  private static List<CorpusCase> cases(JsonNode array) {
    var result = new ArrayList<CorpusCase>();
    for (var node : array) {
      result.add(new CorpusCase(text(node, "id"), text(node, "description"), materialize(node)));
    }
    return List.copyOf(result);
  }

  private static List<HeaderCase> headers(JsonNode array) {
    var result = new ArrayList<HeaderCase>();
    for (var node : array) {
      result.add(
          new HeaderCase(
              text(node, "id"),
              text(node, "description"),
              materialize(node),
              node.path("accepted").asBoolean(false)));
    }
    return List.copyOf(result);
  }

  /**
   * {@code prefix} + {@code unit} x {@code count} + {@code suffix}, or the literal {@code value}.
   */
  private static String materialize(JsonNode node) {
    return materialize(node, "value");
  }

  private static String materialize(JsonNode node, String literalField) {
    if (!node.has("repeat")) {
      return node.path(literalField).asText("");
    }
    var repeat = node.get("repeat");
    return node.path("prefix").asText("")
        + repeat.get("unit").asText().repeat(repeat.get("count").asInt())
        + node.path("suffix").asText("");
  }

  private static String text(JsonNode node, String field) {
    return node.has(field) ? node.get(field).asText() : null;
  }

  private static JsonNode read(String fileName) {
    return PARSED.computeIfAbsent(fileName, HostileCorpus::parse);
  }

  private static JsonNode parse(String fileName) {
    var resource = DIRECTORY + fileName;
    try (InputStream stream = HostileCorpus.class.getClassLoader().getResourceAsStream(resource)) {
      if (stream == null) {
        throw new IllegalStateException("hostile corpus not on the classpath: " + resource);
      }
      return MAPPER.readTree(stream);
    } catch (IOException e) {
      throw new IllegalStateException("hostile corpus unreadable: " + resource, e);
    }
  }
}
