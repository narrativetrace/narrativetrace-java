/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.clarity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI entry point for standalone clarity scanning.
 *
 * <p>INTENT: Use this from Gradle tasks or manual scripts when you want clarity reports from
 * compiled classes without executing tests.
 *
 * <p>Output files are {@code clarity-scan-report.md} and {@code clarity-scan-results.json} —
 * deliberately distinct from the test-run artifacts ({@code clarity-report.md} / {@code
 * clarity-results.json}) so a scan can never overwrite what the {@code clarityCheck} gate reads;
 * scan results and test-derived results answer different questions and must coexist.
 *
 * <p>{@code --glossary-dir} names the directory holding the project's committed glossary, so the
 * scan scores in the project's own vocabulary. Resolving it needs the glossary module, which
 * depends on this one — so the resolution arrives as a {@link ProjectVocabularySource}, and this
 * entry point alone always scores with the built-in dictionaries.
 */
public final class ClarityScannerMain {

  private static final int EXIT_OK = 0;
  private static final int EXIT_FAILED = 1;

  private ClarityScannerMain() {}

  public static void main(String[] args) {
    int status = run(args, ProjectVocabularySource.none());
    if (status != EXIT_OK) {
      System.exit(status);
    }
  }

  /**
   * Runs the scan, resolving the project vocabulary through the given source.
   *
   * <p>Returns a status instead of terminating, so a caller that owns the process — the glossary
   * module's entry point, or a test — decides what to do with a failure.
   *
   * @param args command-line arguments; {@code --classes-dir} is required
   * @param vocabularySource resolves {@code --glossary-dir} into a vocabulary; must not be {@code
   *     null}
   * @return {@code 0} on success, {@code 1} when a required argument is missing or the scan fails
   *     to write
   */
  public static int run(String[] args, ProjectVocabularySource vocabularySource) {
    if (vocabularySource == null) {
      throw new IllegalArgumentException("vocabularySource must not be null");
    }
    var classesDir = findArg(args, "--classes-dir");
    if (classesDir == null) {
      System.err.println("--classes-dir is required");
      return EXIT_FAILED;
    }

    var outputDirArg = findArg(args, "--output-dir");
    var formatArg = findArg(args, "--format");
    var glossaryDirArg = findArg(args, "--glossary-dir");
    var outputDir = outputDirArg != null ? Path.of(outputDirArg) : Path.of("build/narrativetrace");
    var format = formatArg != null ? formatArg : "both";
    var glossaryDir = glossaryDirArg != null ? Path.of(glossaryDirArg) : null;

    try {
      run(Path.of(classesDir), outputDir, format, vocabularySource.resolve(glossaryDir));
      return EXIT_OK;
    } catch (IOException e) {
      System.err.println("Error: " + e.getMessage());
      return EXIT_FAILED;
    }
  }

  private static String findArg(String[] args, String name) {
    for (int i = 0; i < args.length - 1; i++) {
      if (name.equals(args[i])) {
        return args[i + 1];
      }
    }
    return null;
  }

  static void run(Path classesDir, Path outputDir, String format) throws IOException {
    run(classesDir, outputDir, format, DomainVocabulary.empty());
  }

  static void run(Path classesDir, Path outputDir, String format, DomainVocabulary vocabulary)
      throws IOException {
    if (!"both".equals(format) && !"md".equals(format) && !"json".equals(format)) {
      throw new IllegalArgumentException(
          "Unknown format: " + format + " (expected: both, md, or json)");
    }

    var scanner = new ClarityScanner(vocabulary);
    var results = scanner.scan(classesDir);

    if (results.isEmpty()) {
      System.out.println("No classes found in " + classesDir);
      return;
    }

    Files.createDirectories(outputDir);

    var writeMd = "both".equals(format) || "md".equals(format);
    var writeJson = "both".equals(format) || "json".equals(format);

    if (writeMd) {
      var report = new ClarityReportRenderer().renderSuiteReport(results);
      Files.writeString(outputDir.resolve("clarity-scan-report.md"), report);
    }

    if (writeJson) {
      var json = new ClarityJsonExporter().export(results);
      Files.writeString(outputDir.resolve("clarity-scan-results.json"), json);
    }

    System.out.println("Clarity analysis complete: " + results.size() + " classes scanned");
    System.out.println("Output: " + outputDir);
  }
}
