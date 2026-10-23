/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.core.output;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The one-line pointers from a failing test to its trace file.
 *
 * <p>INTENT: an agent that runs a failing test reads the console, not the output directory — in
 * every benchmark row it wrote hundreds of trace files and opened none. A single line under the
 * failure, naming the file by its absolute path (the agent's working directory is not the
 * module's), is where it already looks. The same line shape is repeated for every failing test in
 * the run footer, failing tests first, so the end of the log carries them too.
 *
 * <p><b>@llmNote</b> The per-test pointer goes to standard error, the footer's list to the stream
 * the footer already uses. Gradle hides both unless {@code testLogging.showStandardStreams} is on
 * (the report XML still holds them); Maven surefire prints both. The named file is the row's {@code
 * trace} artifact — the text, Markdown or diagram trace — never the JSON companions, which {@link
 * RunIndex} lists.
 */
public final class RunPointers {

  /** The prefix every pointer line starts with, so a log can be searched for it. */
  public static final String PREFIX = "narrativetrace: ";

  private RunPointers() {}

  /**
   * {@code Class.method}, with the invocation number appended for a repeated or parameterized one.
   */
  public static String label(ArtifactIdentity identity) {
    var className = identity.testClassName();
    var simple = className.substring(className.lastIndexOf('.') + 1);
    var label = simple + "." + identity.methodName();
    return identity.isInvocation() ? label + "[#" + identity.invocationIndex() + "]" : label;
  }

  /** The absolute path of a row's trace file, when it has one. */
  public static Optional<Path> traceFile(ScenarioManifest.Entry entry, Path outputDir) {
    return Optional.ofNullable(entry.artifacts().get("trace"))
        .map(relative -> outputDir.toAbsolutePath().normalize().resolve(relative));
  }

  /**
   * {@code narrativetrace: trace of <Class.method> → <absolute path>}, or empty when the row wrote
   * no trace file.
   */
  public static Optional<String> pointerLine(ScenarioManifest.Entry entry, Path outputDir) {
    return traceFile(entry, outputDir)
        .map(file -> PREFIX + "trace of " + label(entry.identity()) + " → " + file);
  }

  /** The line naming the run index. */
  public static String indexLine(Path outputDir) {
    return PREFIX
        + "index of this run → "
        + outputDir.toAbsolutePath().normalize().resolve(RunIndex.FILE_NAME);
  }

  /**
   * The footer block: one pointer per failing test, in execution order, then the index line. Prints
   * nothing when no test failed.
   */
  public static void printFailingFirst(
      List<ScenarioManifest.Entry> rows, Path outputDir, PrintStream out) {
    var lines =
        rows.stream()
            .filter(ScenarioManifest.Entry::failed)
            .flatMap(row -> pointerLine(row, outputDir).stream())
            .toList();
    if (lines.isEmpty()) {
      return;
    }
    lines.forEach(out::println);
    out.println(indexLine(outputDir));
  }
}
