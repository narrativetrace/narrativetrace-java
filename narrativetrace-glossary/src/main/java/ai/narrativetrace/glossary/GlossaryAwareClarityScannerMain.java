/*
 * Copyright (c) 2026 Empower Agile
 *
 * SPDX-License-Identifier: BUSL-1.1
 * Licensed under the Business Source License 1.1 (see LICENSE); Change Date: four
 * years from publication; Change License: Apache-2.0
 */
package ai.narrativetrace.glossary;

import ai.narrativetrace.clarity.ClarityScannerMain;

/**
 * CLI entry point for a clarity scan that scores in the project's own vocabulary.
 *
 * <p>INTENT: The one place the two modules meet at the command line. Reading {@code glossary.json}
 * needs this module, which depends on the clarity module — so the {@code clarityScan} Gradle task
 * runs this class, which hands {@link ClarityScannerMain} the vocabulary resolver it cannot import.
 * Everything else — arguments, output files, exit status — stays owned by the scanner.
 *
 * <p>Accepts every argument {@link ClarityScannerMain} does, plus {@code --glossary-dir}; without
 * that argument, or without a committed glossary in it, the scan scores exactly as the plain entry
 * point would.
 */
public final class GlossaryAwareClarityScannerMain {

  private GlossaryAwareClarityScannerMain() {}

  public static void main(String[] args) {
    int status = ClarityScannerMain.run(args, GlossaryVocabulary::from);
    if (status != 0) {
      System.exit(status);
    }
  }
}
