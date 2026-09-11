/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import ai.narrativetrace.core.export.JsonEscape;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The scenario → file index a suite run leaves beside its artifacts.
 *
 * <p>INTENT: Artifact names are derived, not announced, so a reader who knows a scenario had to
 * guess which file holds it — and once a test method runs more than once, guessing stops working
 * (2026-09-08 agent evaluation). {@code manifest.json} answers the question directly: one row per
 * traced scenario, naming the test that produced it, its invocation number when the method ran more
 * than once, and every artifact it owns as a path relative to the output directory.
 *
 * <p><b>@llmNote</b> Rows appear in suite execution order and paths always use {@code /}, so the
 * file diffs cleanly and reads the same on every platform. The listing is what is actually on disk:
 * an artifact a format or a flag did not produce is absent from the row rather than listed and
 * missing.
 */
public final class ScenarioManifest {

  /** The manifest's name inside the output directory. */
  public static final String FILE_NAME = "manifest.json";

  private static final String SCHEMA = "narrativetrace/scenario-manifest/1";

  /** One probe of the {@code traces} tree: the role a file plays, and the suffix that finds it. */
  private record TraceRole(String role, String suffix) {}

  /**
   * The {@code traces} tree probed in listing order. The four rendering formats share the {@code
   * trace} role — a run writes exactly one of them — and the first that exists claims it.
   */
  private static final List<TraceRole> TRACE_ROLES =
      List.of(
          new TraceRole("trace", ".md"),
          new TraceRole("trace", ".txt"),
          new TraceRole("trace", ".mmd"),
          new TraceRole("trace", ".puml"),
          new TraceRole("json", ".json"),
          new TraceRole("canonicalJson", ".canonical.json"),
          new TraceRole("structuralJson", ".structural.json"));

  private ScenarioManifest() {}

  /**
   * One traced scenario's row.
   *
   * @param scenario the humanized scenario name, as the artifacts' own headers spell it
   * @param identity which test invocation produced it
   * @param artifacts role → path relative to the output directory, in listing order
   */
  public record Entry(String scenario, ArtifactIdentity identity, Map<String, String> artifacts) {

    /**
     * Copies the artifact map so a row cannot change after the run that recorded it — through a
     * {@code LinkedHashMap}, because {@code Map.copyOf} does not promise iteration order and the
     * manifest has to render the same bytes every run.
     */
    public Entry {
      artifacts = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(artifacts));
    }
  }

  /**
   * Builds one scenario's row by probing the artifact layout for files that exist.
   *
   * @param outputDir the run's output directory, which every listed path is relative to
   */
  public static Entry entryFor(Path outputDir, ArtifactIdentity identity, String scenario) {
    var resolver = new OutputDirectoryResolver(outputDir);
    var artifacts = new LinkedHashMap<String, String>();
    for (var role : TRACE_ROLES) {
      putIfPresent(
          artifacts, role.role(), outputDir, resolver.traceArtifact(identity, role.suffix()));
    }
    putIfPresent(artifacts, "diagram", outputDir, resolver.diagramFile(identity));
    putIfPresent(artifacts, "structural", outputDir, resolver.structuralFile(identity));
    return new Entry(scenario, identity, artifacts);
  }

  /** Records a file that exists under its role, keeping the first path a role resolves to. */
  private static void putIfPresent(
      Map<String, String> artifacts, String role, Path outputDir, Path file) {
    if (Files.isRegularFile(file)) {
      artifacts.putIfAbsent(role, relative(outputDir, file));
    }
  }

  /** The path as the manifest states it: relative to the output directory, {@code /}-separated. */
  private static String relative(Path outputDir, Path file) {
    return outputDir.relativize(file).toString().replace('\\', '/');
  }

  /** Writes {@code manifest.json}; writes nothing at all when the run traced no scenario. */
  public static void write(List<Entry> entries, Path outputDir) throws IOException {
    if (entries.isEmpty()) {
      return;
    }
    new TraceFileWriter().write(render(entries), outputDir.resolve(FILE_NAME));
  }

  /** The manifest document, rendered. */
  public static String render(List<Entry> entries) {
    var rows = new ArrayList<String>(entries.size());
    for (var entry : entries) {
      rows.add(renderEntry(entry));
    }
    return "{\n  \"schema\": \""
        + SCHEMA
        + "\",\n  \"scenarios\": [\n"
        + String.join(",\n", rows)
        + "\n  ]\n}\n";
  }

  private static String renderEntry(Entry entry) {
    var identity = entry.identity();
    var sb = new StringBuilder("    {\n");
    sb.append("      \"scenario\": \"").append(JsonEscape.escape(entry.scenario())).append("\",\n");
    sb.append("      \"testClass\": \"")
        .append(JsonEscape.escape(identity.testClassName()))
        .append("\",\n");
    sb.append("      \"testMethod\": \"")
        .append(JsonEscape.escape(identity.methodName()))
        .append("\",\n");
    if (identity.isInvocation()) {
      sb.append("      \"invocation\": ").append(identity.invocationIndex()).append(",\n");
    }
    sb.append("      \"artifacts\": {\n").append(renderArtifacts(entry.artifacts()));
    return sb.append("      }\n    }").toString();
  }

  private static String renderArtifacts(Map<String, String> artifacts) {
    var lines = new ArrayList<String>(artifacts.size());
    artifacts.forEach(
        (role, path) ->
            lines.add(
                "        \""
                    + JsonEscape.escape(role)
                    + "\": \""
                    + JsonEscape.escape(path)
                    + "\""));
    return lines.isEmpty() ? "" : String.join(",\n", lines) + "\n";
  }
}
