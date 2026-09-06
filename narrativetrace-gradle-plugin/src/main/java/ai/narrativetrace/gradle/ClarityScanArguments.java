/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import java.io.File;
import java.util.List;

/**
 * Builds the command line the {@code clarityScan} task passes to its entry point.
 *
 * <p>INTENT: Keep the argument contract pure and unit-testable, the way {@link AgentJvmArgFactory}
 * does for the agent. The task itself only resolves lazy Gradle properties and hands the resolved
 * files here.
 *
 * <p>{@code --glossary-dir} is always the repository root, never the subproject directory: one
 * repository has one committed glossary, and a multi-project build's subprojects all score against
 * it. A root without {@code glossary.json} costs nothing — the scan then scores with the built-in
 * dictionaries alone.
 */
final class ClarityScanArguments {

  private ClarityScanArguments() {}

  /**
   * @param classesDir compiled classes to scan
   * @param outputDir directory receiving {@code clarity-scan-*} artifacts
   * @param repositoryRoot root directory holding the committed {@code glossary.json}
   * @return the argument list, in order
   */
  static List<String> forScan(File classesDir, File outputDir, File repositoryRoot) {
    return List.of(
        "--classes-dir",
        classesDir.getAbsolutePath(),
        "--output-dir",
        outputDir.getAbsolutePath(),
        "--glossary-dir",
        repositoryRoot.getAbsolutePath());
  }
}
