/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.gradle;

import java.io.File;

/**
 * The console line {@code glossaryScan} prints to announce where it writes.
 *
 * <p>INTENT: Unlike the test-time glossary harvest (opt-in via {@code narrativetrace.glossary}),
 * {@code glossaryScan} writes {@code glossary.json}/{@code glossary.md} straight to the project
 * root — surfacing them there unannounced surprised a dogfood user. Building the message here
 * (rather than inline in the task) keeps it pure and unit-testable; the task action only logs it.
 */
final class GlossaryScanAnnouncement {

  private GlossaryScanAnnouncement() {}

  /**
   * @param rootDir the Gradle project root the glossary is written to
   * @return a multi-line announcement naming both glossary files by absolute path
   */
  static String forRoot(File rootDir) {
    return "glossaryScan writes the domain glossary to the project root:\n"
        + "  "
        + new File(rootDir, "glossary.json").getAbsolutePath()
        + "\n  "
        + new File(rootDir, "glossary.md").getAbsolutePath();
  }
}
