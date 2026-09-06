/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

/**
 * CLI entry point for standalone glossary harvesting.
 *
 * <p>INTENT: Backs the {@code glossaryScan} Gradle task — harvest the domain vocabulary from
 * compiled classes without running tests, which is also the only mode that harvests
 * {@code @Narrated} / {@code @OnError} templates (ADR-012).
 *
 * @see GlossaryStaticScanner
 * @see GlossarySuiteHarvest#runStatic(List)
 */
public final class GlossaryScannerMain {

  private GlossaryScannerMain() {}

  public static void main(String[] args) {
    var exitCode = execute(args);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }

  /**
   * Runs the scan and reports an exit code instead of terminating the JVM.
   *
   * <p>{@link #main} is the only caller that turns a non-zero result into {@link System#exit},
   * which keeps every failure path reachable from a test.
   *
   * @param args command-line arguments
   * @return {@code 0} on success, {@code 1} on a missing argument or an unreadable/unwritable path
   */
  static int execute(String[] args) {
    var classesDir = findArg(args, "--classes-dir");
    if (classesDir == null) {
      System.err.println("--classes-dir is required");
      return 1;
    }
    try {
      run(
          Path.of(classesDir),
          pathArg(args, "--glossary-dir", Path.of(".")),
          pathArg(args, "--output-dir", Path.of("build/narrativetrace")));
      return 0;
    } catch (IOException e) {
      System.err.println("Error: " + e.getMessage());
      return 1;
    }
  }

  /** Reads a path argument, falling back when it was not supplied. */
  static Path pathArg(String[] args, String name, Path fallback) {
    var value = findArg(args, name);
    return value == null ? fallback : Path.of(value);
  }

  private static String findArg(String[] args, String name) {
    for (int i = 0; i < args.length - 1; i++) {
      if (name.equals(args[i])) {
        return args[i + 1];
      }
    }
    return null;
  }

  /**
   * Scans {@code classesDir} and merges the harvest into the glossary under {@code glossaryDir}.
   *
   * @param classesDir compiled classes to scan
   * @param glossaryDir directory holding {@code glossary.json} / {@code glossary.md}
   * @param outputDir build directory receiving the volatile usage report
   * @throws IOException if classes cannot be read or the glossary cannot be written
   */
  static void run(Path classesDir, Path glossaryDir, Path outputDir) throws IOException {
    var trees = new GlossaryStaticScanner().scan(classesDir);
    if (trees.isEmpty()) {
      System.out.println("No classes found in " + classesDir);
      return;
    }
    var harvest =
        new GlossarySuiteHarvest(
            glossaryDir,
            outputDir.resolve("glossary-usage.json"),
            ClassPackageIndex.fromDirectories(List.of(classesDir)),
            Clock.systemUTC());
    var result = harvest.runStatic(trees);

    System.out.println(result.summary());
    System.out.println("Glossary: " + glossaryDir.resolve("glossary.json"));
  }
}
